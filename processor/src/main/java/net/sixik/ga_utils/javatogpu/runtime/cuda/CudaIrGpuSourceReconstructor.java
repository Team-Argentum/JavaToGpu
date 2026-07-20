package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModuleMethod;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceReconstructionResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceReconstructor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrTextBodyParseResult;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrTextBodyParser;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Preview CUDA-C source reconstruction from backend-neutral IrGpu text.
 */
public final class CudaIrGpuSourceReconstructor implements GpuBackendSourceReconstructor {

    public static final CudaIrGpuSourceReconstructor INSTANCE = new CudaIrGpuSourceReconstructor();
    public static final String VERSION = "cuda-irgpu-source-reconstructor-preview-v1";

    private CudaIrGpuSourceReconstructor() {
    }

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.CUDA;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public GpuBackendSourceReconstructionResult reconstruct(GpuRuntimeCompileRequest compileRequest) {
        if (compileRequest == null || compileRequest.irGpuArtifact().isEmpty()) {
            return GpuBackendSourceReconstructionResult.blocked(
                    GpuBackendTarget.CUDA,
                    "cuda-irgpu-source-unavailable",
                    "irgpu-missing",
                    "cuda-source-preview-unavailable",
                    List.of("cuda-irgpu-artifact-missing"),
                    List.of("CUDA lowering preview requires a loaded IrGpu artifact; descriptor OpenCL source is not translated")
            );
        }
        return reconstruct(compileRequest.irGpuArtifact().orElseThrow());
    }

    public GpuBackendSourceReconstructionResult reconstruct(IrGpuArtifact artifact) {
        ArrayList<String> blockers = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        if (artifact == null) {
            blockers.add("cuda-irgpu-artifact-missing");
            return blocked(blockers, diagnostics);
        }
        Optional<IrGpuMethodBody> entryBody = entryBody(artifact);
        if (entryBody.isEmpty()) {
            blockers.add("cuda-entry-body-missing");
        }
        if (!artifact.structMetadata().isEmpty() || !artifact.module().structs().isEmpty()) {
            blockers.add("cuda-struct-lowering-not-supported");
        }
        if (!artifact.constants().isEmpty() || !artifact.constantData().isEmpty()) {
            blockers.add("cuda-constant-metadata-lowering-not-supported");
        }
        entryBody.ifPresent(body -> validateEntryDependencies(artifact, body, blockers));
        Map<String, String> helperBodies = emitHelperBodies(artifact, blockers, diagnostics);
        for (IrGpuEntryParameter parameter : artifact.entryParameters()) {
            if (!supportedParameter(parameter)) {
                blockers.add("cuda-parameter-type-not-supported:" + parameter.name() + ':' + parameter.javaType());
            }
        }
        if (!blockers.isEmpty()) {
            diagnostics.add("CUDA IrGpu source reconstruction is limited to simple entry/helper kernels for this slice");
            return blocked(blockers, diagnostics);
        }

        IrGpuMethodBody body = entryBody.orElseThrow();
        if (!"ir-text-v1".equals(body.format())) {
            blockers.add("cuda-method-body-format-not-supported:" + body.format());
            return blocked(blockers, diagnostics);
        }
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse(body.body());
        CudaIrTextBodyEmitter.Emission emission = CudaIrTextBodyEmitter.INSTANCE.emit(parseResult);
        if (!emission.sourceGenerated()) {
            blockers.addAll(emission.blockers());
            diagnostics.addAll(emission.diagnostics());
            return blocked(blockers, diagnostics);
        }

        String source = assembleKernel(artifact, body, emission.body(), helperBodies, blockers, diagnostics);
        if (!blockers.isEmpty()) {
            return blocked(blockers, diagnostics);
        }
        diagnostics.add("CUDA source reconstructed from IrGpu ir-text-v1 entry body");
        diagnostics.addAll(parseResult.diagnostics());
        diagnostics.addAll(emission.diagnostics());
        return GpuBackendSourceReconstructionResult.reconstructedSource(
                GpuBackendTarget.CUDA,
                source,
                "irgpu-cuda-source",
                "ir-text-v1",
                "cuda-c-source-preview",
                diagnostics
        );
    }

    private static Map<String, String> emitHelperBodies(
            IrGpuArtifact artifact,
            List<String> blockers,
            List<String> diagnostics
    ) {
        LinkedHashMap<String, String> emittedBodies = new LinkedHashMap<>();
        if (artifact == null) {
            return emittedBodies;
        }
        List<IrGpuMethodBody> helperBodies = artifact.module().methodBodies().stream()
                .filter(methodBody -> "helper".equals(methodBody.role()))
                .toList();
        for (IrGpuModuleMethod helperMethod : artifact.module().helperMethods()) {
            if (findHelperBody(artifact, emittedName(helperMethod)).isEmpty()) {
                blockers.add("cuda-helper-" + sanitize(emittedName(helperMethod)) + "-body-missing");
            }
        }
        for (IrGpuMethodBody helperBody : helperBodies) {
            String emittedName = emittedName(helperBody);
            Optional<IrGpuModuleMethod> helperMethod = findHelperMethod(artifact, emittedName);
            if (helperMethod.isEmpty()) {
                blockers.add("cuda-helper-" + sanitize(emittedName) + "-metadata-missing");
                continue;
            }
            if (!"ir-text-v1".equals(helperBody.format())) {
                blockers.add("cuda-helper-" + sanitize(emittedName) + "-body-format-not-supported:" + helperBody.format());
                continue;
            }
            validateHelperMetadata(helperMethod.orElseThrow(), blockers);
            validateHelperDependencies(artifact, helperBody, blockers);
            OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse(helperBody.body());
            CudaIrTextBodyEmitter.Emission emission = CudaIrTextBodyEmitter.INSTANCE.emit(parseResult);
            if (!emission.sourceGenerated()) {
                blockers.add("cuda-helper-" + sanitize(emittedName) + "-body-emission-failed");
                blockers.addAll(emission.blockers());
                diagnostics.addAll(emission.diagnostics());
                continue;
            }
            emittedBodies.put(emittedName, emission.body());
            diagnostics.addAll(parseResult.diagnostics());
            diagnostics.addAll(emission.diagnostics());
        }
        return emittedBodies;
    }

    private static void validateHelperMetadata(IrGpuModuleMethod helperMethod, List<String> blockers) {
        String emittedName = emittedName(helperMethod);
        if (helperMethod.returnType().isBlank() || "unknown".equals(helperMethod.returnType())) {
            blockers.add("cuda-helper-" + sanitize(emittedName) + "-return-type-missing");
        }
        if (!helperMethod.openClAttributes().isEmpty() || !helperMethod.attributeMetadata().isEmpty()) {
            blockers.add("cuda-helper-" + sanitize(emittedName) + "-attributes-not-supported");
        }
        for (IrGpuEntryParameter parameter : helperMethod.parameters()) {
            if (parameter.name().isBlank()) {
                blockers.add("cuda-helper-" + sanitize(emittedName) + "-parameter-name-missing");
            }
            if (!supportedParameter(parameter)) {
                blockers.add("cuda-helper-" + sanitize(emittedName)
                        + "-parameter-type-not-supported:" + parameter.name() + ':' + parameter.javaType());
            }
        }
    }

    private static void validateHelperDependencies(
            IrGpuArtifact artifact,
            IrGpuMethodBody helperBody,
            List<String> blockers
    ) {
        for (String dependency : helperBody.helperDependencies()) {
            if (findHelperMethod(artifact, dependency).isEmpty()) {
                blockers.add("cuda-helper-" + sanitize(emittedName(helperBody))
                        + "-dependency-metadata-missing:" + dependency);
            }
        }
    }

    private static void validateEntryDependencies(
            IrGpuArtifact artifact,
            IrGpuMethodBody entryBody,
            List<String> blockers
    ) {
        for (String dependency : entryBody.helperDependencies()) {
            if (findHelperMethod(artifact, dependency).isEmpty()) {
                blockers.add("cuda-entry-helper-dependency-metadata-missing:" + dependency);
            }
            if (findHelperBody(artifact, dependency).isEmpty()) {
                blockers.add("cuda-entry-helper-dependency-body-missing:" + dependency);
            }
        }
    }

    private static Optional<IrGpuMethodBody> entryBody(IrGpuArtifact artifact) {
        return artifact.module().methodBodies().stream()
                .filter(body -> "entry".equals(body.role()))
                .filter(body -> body.name().equals(artifact.module().entryMethod())
                        || body.emittedName().equals(artifact.module().entryEmittedName()))
                .findFirst()
                .or(() -> artifact.module().methodBodies().stream().filter(body -> "entry".equals(body.role())).findFirst());
    }

    private static boolean supportedParameter(IrGpuEntryParameter parameter) {
        String type = parameter.javaType();
        return GpuTypeSupport.isSupportedArrayType(type)
                || GpuTypeSupport.isSupportedKernelParameterType(type)
                || GpuTypeSupport.isSupportedVectorType(type)
                || GpuTypeSupport.isSupportedPointerType(type);
    }

    private static String assembleKernel(
            IrGpuArtifact artifact,
            IrGpuMethodBody entryBody,
            String body,
            Map<String, String> helperBodies,
            List<String> blockers,
            List<String> diagnostics
    ) {
        StringBuilder builder = new StringBuilder();
        Map<String, String> emittedHelperBodies = helperBodies == null ? Map.of() : helperBodies;
        for (IrGpuModuleMethod helperMethod : artifact.module().helperMethods()) {
            builder.append(emitHelperPrototype(helperMethod, blockers));
        }
        if (!artifact.module().helperMethods().isEmpty()) {
            builder.append('\n');
        }
        for (IrGpuModuleMethod helperMethod : artifact.module().helperMethods()) {
            String emittedName = emittedName(helperMethod);
            String emittedBody = emittedHelperBodies.get(emittedName);
            if (emittedBody == null || emittedBody.isBlank()) {
                blockers.add("cuda-helper-" + sanitize(emittedName) + "-emitted-body-missing");
                continue;
            }
            builder.append(emitHelperSource(helperMethod, emittedBody, blockers));
        }
        if (!emittedHelperBodies.isEmpty()) {
            builder.append('\n');
        }
        builder.append("extern \"C\" __global__ void ")
                .append(entryBody.emittedName().isBlank() ? artifact.module().entryEmittedName() : entryBody.emittedName())
                .append('(')
                .append(parameterList(artifact.entryParameters()))
                .append(") {\n")
                .append(body)
                .append("}\n");
        diagnostics.add("CUDA source assembler emitted " + emittedHelperBodies.size() + " helper function(s)");
        return builder.toString();
    }

    private static String emitHelperPrototype(IrGpuModuleMethod helperMethod, List<String> blockers) {
        String signature = emitHelperSignature(helperMethod, blockers);
        return signature.isBlank() ? "" : signature + ";\n";
    }

    private static String emitHelperSource(
            IrGpuModuleMethod helperMethod,
            String emittedBody,
            List<String> blockers
    ) {
        String signature = emitHelperSignature(helperMethod, blockers);
        return signature.isBlank() ? "" : signature + " {\n" + emittedBody + "}\n";
    }

    private static String emitHelperSignature(IrGpuModuleMethod helperMethod, List<String> blockers) {
        String emittedName = emittedName(helperMethod);
        if (helperMethod.returnType().isBlank() || "unknown".equals(helperMethod.returnType())) {
            blockers.add("cuda-helper-" + sanitize(emittedName) + "-return-type-missing");
            return "";
        }
        ArrayList<String> parameters = new ArrayList<>();
        for (IrGpuEntryParameter parameter : helperMethod.parameters()) {
            if (parameter.name().isBlank()) {
                blockers.add("cuda-helper-" + sanitize(emittedName) + "-parameter-name-missing");
                continue;
            }
            boolean constant = parameter.constant()
                    || "CONSTANT".equals(parameter.addressSpace())
                    || parameter.openClQualifiers().contains("const");
            parameters.add(CudaIrTextBodyEmitter.emitParameterType(parameter.javaType(), constant) + ' ' + parameter.name());
        }
        return "__device__ "
                + (helperMethod.inline() ? "inline " : "")
                + CudaIrTextBodyEmitter.emitType(helperMethod.returnType())
                + ' '
                + emittedName
                + '('
                + String.join(", ", parameters)
                + ')';
    }

    private static String parameterList(List<IrGpuEntryParameter> parameters) {
        ArrayList<String> emitted = new ArrayList<>();
        for (IrGpuEntryParameter parameter : parameters) {
            boolean constant = parameter.constant()
                    || "CONSTANT".equals(parameter.addressSpace())
                    || parameter.openClQualifiers().contains("const");
            emitted.add(CudaIrTextBodyEmitter.emitParameterType(parameter.javaType(), constant) + ' ' + parameter.name());
        }
        return String.join(", ", emitted);
    }

    private static GpuBackendSourceReconstructionResult blocked(List<String> blockers, List<String> diagnostics) {
        return GpuBackendSourceReconstructionResult.blocked(
                GpuBackendTarget.CUDA,
                "cuda-irgpu-source-unavailable",
                "ir-text-v1",
                "cuda-source-preview-unavailable",
                blockers,
                diagnostics
        );
    }

    private static Optional<IrGpuModuleMethod> findHelperMethod(IrGpuArtifact artifact, String emittedName) {
        return artifact.module().helperMethods().stream()
                .filter(helper -> emittedName(helper).equals(emittedName))
                .findFirst();
    }

    private static Optional<IrGpuMethodBody> findHelperBody(IrGpuArtifact artifact, String emittedName) {
        return artifact.module().methodBodies().stream()
                .filter(methodBody -> "helper".equals(methodBody.role()))
                .filter(methodBody -> emittedName(methodBody).equals(emittedName))
                .findFirst();
    }

    private static String emittedName(IrGpuModuleMethod helperMethod) {
        return helperMethod.emittedName().isBlank() ? helperMethod.name() : helperMethod.emittedName();
    }

    private static String emittedName(IrGpuMethodBody methodBody) {
        return methodBody.emittedName().isBlank() ? methodBody.name() : methodBody.emittedName();
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9_-]", "-");
    }
}
