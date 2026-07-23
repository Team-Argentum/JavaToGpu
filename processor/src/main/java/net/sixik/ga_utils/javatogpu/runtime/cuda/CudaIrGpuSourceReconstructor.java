package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModuleMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuStructFieldMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuStructMetadata;
import net.sixik.ga_utils.javatogpu.frontend.opencl.OpenClAttributeProjection;
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
        if (!artifact.module().structs().isEmpty()) {
            blockers.add("cuda-legacy-module-struct-lowering-not-supported");
        }
        if (!artifact.constants().isEmpty() || !artifact.constantData().isEmpty()) {
            blockers.add("cuda-constant-metadata-lowering-not-supported");
        }
        entryBody.ifPresent(body -> {
            validateEntryDependencies(artifact, body, blockers);
            validateImageSamplerIntrinsicCoverage(body.body(), blockers);
        });
        Map<String, String> helperBodies = emitHelperBodies(artifact, blockers, diagnostics);
        for (IrGpuEntryParameter parameter : artifact.entryParameters()) {
            if (!supportedParameter(parameter, artifact.structMetadata())) {
                blockers.add("cuda-parameter-type-not-supported:" + parameter.name() + ':' + parameter.javaType());
            }
            validateEntryImageSamplerSourceLowering(parameter, blockers);
        }
        validateEntryLocalParameters(artifact.entryParameters(), artifact.structMetadata(), blockers);
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
            validateHelperMetadata(helperMethod.orElseThrow(), artifact.structMetadata(), blockers);
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

    private static void validateHelperMetadata(
            IrGpuModuleMethod helperMethod,
            List<IrGpuStructMetadata> structMetadata,
            List<String> blockers
    ) {
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
            if (!supportedParameter(parameter, structMetadata)) {
                blockers.add("cuda-helper-" + sanitize(emittedName)
                        + "-parameter-type-not-supported:" + parameter.name() + ':' + parameter.javaType());
            }
            validateHelperImageSamplerSourceLowering(parameter, blockers);
        }
    }

    private static void validateEntryImageSamplerSourceLowering(IrGpuEntryParameter parameter, List<String> blockers) {
        Optional<CudaImageSamplerAbi.Descriptor> descriptor = parameter == null
                ? Optional.empty()
                : CudaImageSamplerAbi.descriptorFor(parameter.javaType());
        if (descriptor.isEmpty()) {
            return;
        }
        CudaImageSamplerAbi.Descriptor imageSampler = descriptor.orElseThrow();
        if (((imageSampler.readTextureObject() || imageSampler.writeSurfaceObject()) && imageSampler.cudaArray2D())
                || imageSampler.textureDescriptorState()) {
            return;
        }
        blockers.add("cuda-image-sampler-source-lowering-pending:" + parameter.name() + ':' + parameter.javaType());
    }

    private static void validateHelperImageSamplerSourceLowering(IrGpuEntryParameter parameter, List<String> blockers) {
        if (parameter == null || CudaImageSamplerAbi.descriptorFor(parameter.javaType()).isEmpty()) {
            return;
        }
        blockers.add("cuda-image-sampler-helper-source-lowering-pending:" + parameter.name() + ':' + parameter.javaType());
    }

    private static void validateImageSamplerIntrinsicCoverage(String body, List<String> blockers) {
        String source = body == null ? "" : body;
        for (String intrinsic : imageWriteIntrinsics(source)) {
            if (!"write_imagef".equals(intrinsic) && !"write_imagei".equals(intrinsic) && !"write_imageui".equals(intrinsic)) {
                blockers.add("cuda-image-write-source-lowering-pending:" + intrinsic);
            }
        }
        for (String intrinsic : imageMetadataIntrinsics(source)) {
            if (!"get_image_width".equals(intrinsic) && !"get_image_height".equals(intrinsic)) {
                blockers.add("cuda-image-metadata-source-lowering-pending:" + intrinsic);
            }
        }
    }

    private static List<String> imageMetadataIntrinsics(String body) {
        ArrayList<String> names = new ArrayList<>();
        String source = body == null ? "" : body;
        int index = 0;
        while (index >= 0 && index < source.length()) {
            int start = source.indexOf("intrinsic(get_image_", index);
            if (start < 0) {
                break;
            }
            int nameStart = start + "intrinsic(".length();
            int nameEnd = nameStart;
            while (nameEnd < source.length()) {
                char ch = source.charAt(nameEnd);
                if (!Character.isLetterOrDigit(ch) && ch != '_') {
                    break;
                }
                nameEnd++;
            }
            String name = source.substring(nameStart, nameEnd);
            if (!names.contains(name)) {
                names.add(name);
            }
            index = nameEnd;
        }
        return names;
    }

    private static List<String> imageWriteIntrinsics(String body) {
        return imageIntrinsicNames(body, "intrinsic(write_image");
    }

    private static List<String> imageIntrinsicNames(String body, String prefix) {
        ArrayList<String> names = new ArrayList<>();
        String source = body == null ? "" : body;
        int index = 0;
        while (index >= 0 && index < source.length()) {
            int start = source.indexOf(prefix, index);
            if (start < 0) {
                break;
            }
            int nameStart = start + "intrinsic(".length();
            int nameEnd = nameStart;
            while (nameEnd < source.length()) {
                char ch = source.charAt(nameEnd);
                if (!Character.isLetterOrDigit(ch) && ch != '_') {
                    break;
                }
                nameEnd++;
            }
            String name = source.substring(nameStart, nameEnd);
            if (!names.contains(name)) {
                names.add(name);
            }
            index = nameEnd;
        }
        return names;
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

    private static boolean supportedParameter(IrGpuEntryParameter parameter, List<IrGpuStructMetadata> structMetadata) {
        String type = parameter.javaType();
        return GpuTypeSupport.isSupportedArrayType(type)
                || isSupportedVectorArrayType(type)
                || isSupportedStructArrayType(type, structMetadata)
                || isSupportedStructValueType(type, structMetadata)
                || GpuTypeSupport.isSupportedKernelParameterType(type)
                || GpuTypeSupport.isSupportedVectorType(type)
                || GpuTypeSupport.isSupportedPointerType(type);
    }

    private static boolean isSupportedVectorArrayType(String javaType) {
        String declaredType = GpuTypeSupport.declaredType(javaType);
        return declaredType != null
                && declaredType.endsWith("[]")
                && GpuTypeSupport.isSupportedVectorType(GpuTypeSupport.componentType(declaredType));
    }

    private static boolean isSupportedStructArrayType(String javaType, List<IrGpuStructMetadata> structMetadata) {
        String declaredType = GpuTypeSupport.declaredType(javaType);
        if (declaredType == null || !declaredType.endsWith("[]")) {
            return false;
        }
        String componentType = GpuTypeSupport.componentType(declaredType);
        for (IrGpuStructMetadata struct : structMetadata) {
            if (componentType.equals(struct.ownerQualifiedName())
                    || componentType.equals(struct.ownerSimpleName())
                    || componentType.equals(GpuTypeSupport.simpleTypeName(struct.ownerSimpleName()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSupportedStructValueType(String javaType, List<IrGpuStructMetadata> structMetadata) {
        String declaredType = GpuTypeSupport.declaredType(javaType);
        if (declaredType == null || declaredType.endsWith("[]")) {
            return false;
        }
        for (IrGpuStructMetadata struct : structMetadata) {
            if (declaredType.equals(struct.ownerQualifiedName())
                    || declaredType.equals(struct.ownerSimpleName())
                    || declaredType.equals(GpuTypeSupport.simpleTypeName(struct.ownerSimpleName()))) {
                return true;
            }
        }
        return false;
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
        if (hasImageSamplerParameters(artifact.entryParameters())) {
            builder.append("#include <cuda_runtime.h>\n\n");
        }
        for (IrGpuStructMetadata struct : artifact.structMetadata()) {
            builder.append(emitStruct(struct));
        }
        if (!artifact.structMetadata().isEmpty()) {
            builder.append('\n');
        }
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
                .append(localSharedMemoryDeclarations(artifact.entryParameters()))
                .append(body)
                .append("}\n");
        diagnostics.add("CUDA source assembler emitted " + emittedHelperBodies.size() + " helper function(s)");
        diagnostics.add("CUDA source assembler emitted " + artifact.structMetadata().size() + " struct typedef(s)");
        return builder.toString();
    }

    private static String emitStruct(IrGpuStructMetadata struct) {
        StringBuilder builder = new StringBuilder();
        builder.append("typedef struct");
        List<String> structAttributes = OpenClAttributeProjection.projectIr(
                struct.openClAttributes(),
                struct.attributeMetadata()
        );
        if (!structAttributes.isEmpty()) {
            builder.append(' ');
            emitAttributes(builder, structAttributes, true);
        }
        builder.append("{\n");
        for (IrGpuStructFieldMetadata field : struct.fields()) {
            builder.append("    ")
                    .append(CudaIrTextBodyEmitter.emitType(field.javaType()))
                    .append(' ')
                    .append(field.name());
            emitAttributes(builder, OpenClAttributeProjection.projectIr(field.openClAttributes(), field.attributeMetadata()), false);
            builder.append(";\n");
        }
        builder.append("} ")
                .append(GpuTypeSupport.simpleTypeName(struct.ownerSimpleName()))
                .append(";\n");
        return builder.toString();
    }

    private static void emitAttributes(StringBuilder builder, List<String> attributes, boolean structPrefix) {
        if (attributes.isEmpty()) {
            return;
        }
        if (structPrefix) {
            builder.append("__attribute__((")
                    .append(String.join(", ", attributes))
                    .append(")) ");
            return;
        }
        builder.append(" __attribute__((")
                .append(String.join(", ", attributes))
                .append("))");
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
        ArrayList<IrGpuEntryParameter> localParameters = new ArrayList<>();
        for (IrGpuEntryParameter parameter : parameters) {
            if ("LOCAL".equals(parameter.addressSpace())) {
                localParameters.add(parameter);
                continue;
            }
            boolean constant = parameter.constant()
                    || "CONSTANT".equals(parameter.addressSpace())
                    || parameter.openClQualifiers().contains("const");
            Optional<CudaImageSamplerAbi.Descriptor> imageSampler = CudaImageSamplerAbi.descriptorFor(parameter.javaType());
            if (imageSampler.isPresent() && imageSampler.orElseThrow().textureDescriptorState()) {
                continue;
            }
            emitted.add(CudaIrTextBodyEmitter.emitParameterType(parameter.javaType(), constant) + ' ' + parameter.name());
            if (imageSampler.isPresent()
                    && (imageSampler.orElseThrow().readTextureObject() || imageSampler.orElseThrow().writeSurfaceObject())
                    && imageSampler.orElseThrow().cudaArray2D()) {
                emitted.add("int " + CudaIrTextBodyEmitter.imageMetadataParameter(parameter.name(), "width"));
                emitted.add("int " + CudaIrTextBodyEmitter.imageMetadataParameter(parameter.name(), "height"));
            }
        }
        if (CudaLocalSharedMemoryLayout.hiddenOffsetsRequired(localParameters.size())) {
            for (IrGpuEntryParameter parameter : localParameters) {
                emitted.add("unsigned int " + CudaLocalSharedMemoryLayout.hiddenOffsetParameterName(parameter.name()));
            }
        }
        return String.join(", ", emitted);
    }

    private static boolean hasImageSamplerParameters(List<IrGpuEntryParameter> parameters) {
        for (IrGpuEntryParameter parameter : parameters == null ? List.<IrGpuEntryParameter>of() : parameters) {
            if (parameter != null && CudaImageSamplerAbi.descriptorFor(parameter.javaType()).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static void validateEntryLocalParameters(
            List<IrGpuEntryParameter> parameters,
            List<IrGpuStructMetadata> structMetadata,
            List<String> blockers
    ) {
        int localCount = 0;
        for (IrGpuEntryParameter parameter : parameters) {
            if (!"LOCAL".equals(parameter.addressSpace())) {
                continue;
            }
            localCount++;
            String type = GpuTypeSupport.declaredType(parameter.javaType());
            if (type == null || !type.endsWith("[]")) {
                blockers.add("cuda-local-shared-memory-type-not-supported:" + parameter.name() + ':' + parameter.javaType());
                continue;
            }
            String componentType = GpuTypeSupport.componentType(type);
            if (!isSupportedLocalSharedMemoryComponent(componentType)
                    && !isSupportedStructArrayType(type, structMetadata)) {
                blockers.add("cuda-local-shared-memory-type-not-supported:" + parameter.name() + ':' + parameter.javaType());
            }
        }
    }

    private static boolean isSupportedLocalSharedMemoryComponent(String componentType) {
        return switch (componentType) {
            case "byte", "short", "char", "int", "long", "float", "double" -> true;
            default -> false;
        };
    }

    private static String localSharedMemoryDeclarations(List<IrGpuEntryParameter> parameters) {
        ArrayList<IrGpuEntryParameter> localParameters = new ArrayList<>();
        for (IrGpuEntryParameter parameter : parameters) {
            if ("LOCAL".equals(parameter.addressSpace())) {
                localParameters.add(parameter);
            }
        }
        if (localParameters.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (localParameters.size() == 1) {
            IrGpuEntryParameter parameter = localParameters.get(0);
            String componentType = GpuTypeSupport.componentType(GpuTypeSupport.declaredType(parameter.javaType()));
            builder.append("    extern __shared__ ")
                    .append(CudaIrTextBodyEmitter.emitType(componentType))
                    .append(' ')
                    .append(parameter.name())
                    .append("[];\n");
            return builder.toString();
        }
        builder.append("    extern __shared__ __align__(16) unsigned char __jtg_cuda_dynamic_shared[];\n");
        for (IrGpuEntryParameter parameter : localParameters) {
            String componentType = GpuTypeSupport.componentType(GpuTypeSupport.declaredType(parameter.javaType()));
            String cudaType = CudaIrTextBodyEmitter.emitType(componentType);
            builder.append("    ")
                    .append(cudaType)
                    .append("* ")
                    .append(parameter.name())
                    .append(" = (")
                    .append(cudaType)
                    .append("*)(__jtg_cuda_dynamic_shared + ")
                    .append(CudaLocalSharedMemoryLayout.hiddenOffsetParameterName(parameter.name()))
                    .append(");\n");
        }
        return builder.toString();
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
