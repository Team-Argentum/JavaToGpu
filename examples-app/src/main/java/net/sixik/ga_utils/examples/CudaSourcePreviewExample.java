package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModuleMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLoweringResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLowerers;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDump;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactSnapshot;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileInvalidationStamp;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileProvenance;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationReport;

import java.util.List;
import java.util.Optional;

/**
 * Hardware-free CUDA source preview from a small backend-neutral IrGpu artifact.
 */
public final class CudaSourcePreviewExample {

    private CudaSourcePreviewExample() {
    }

    public static void main(String[] args) {
        System.out.println(renderCudaSourcePreview());
    }

    static String renderCudaSourcePreview() {
        GpuRuntimeCompileRequest request = previewCompileRequest();
        GpuBackendLoweringResult result = GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA)
                .lowerWithStageResult(request);
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA source preview example:").append(System.lineSeparator());
        builder.append("- rule=hardware-free; no CUDA runtime, nvidia-smi, NVRTC, nvcc, or kernel execution is opened")
                .append(System.lineSeparator());
        builder.append("- backend=").append(result.stageResult().backendTarget()).append(System.lineSeparator());
        builder.append("- lowerStage=").append(result.stageResult().status()).append(System.lineSeparator());
        builder.append("- lowered=").append(result.lowered()).append(System.lineSeparator());
        builder.append("- selectedSource=").append(result.sourceSelectionPlan().selectedSource()).append(System.lineSeparator());
        builder.append("- payloadFormat=").append(result.sourceSelectionPlan().payloadFormat()).append(System.lineSeparator());
        builder.append("- runtimeLoadMode=").append(result.sourceSelectionPlan().runtimeLoadMode()).append(System.lineSeparator());
        builder.append("- moduleFormat=").append(result.moduleArtifact().moduleFormat().key()).append(System.lineSeparator());
        builder.append("- helperFunctions=1").append(System.lineSeparator());
        builder.append("- execution=disabled").append(System.lineSeparator());
        if (!result.stageResult().blockers().isEmpty()) {
            builder.append("- blockers=").append(String.join(",", result.stageResult().blockers())).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator()).append("--- cuda-c preview ---").append(System.lineSeparator());
        builder.append(result.moduleArtifact().source());
        builder.append(System.lineSeparator()).append("--- runtime dump preview ---").append(System.lineSeparator());
        builder.append(renderRuntimeDumpPreview());
        return builder.toString();
    }

    static String renderRuntimeDumpPreview() {
        IrGpuArtifact original = previewIrGpuArtifact(false);
        IrGpuArtifact optimized = previewIrGpuArtifact(true);
        GpuKernelDescriptor descriptor = previewDescriptor(
                "inline://examples/cuda-source-preview.cl",
                "__kernel void jtg_kernel(__global const float* input, __global float* output) {\n"
                        + "    int id = get_global_id(0);\n"
                        + "    output[id] = input[id] * input[id];\n"
                        + "}\n"
        );
        GpuRuntimeCompileRequest originalRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(original)
        );
        GpuRuntimeCompileRequest optimizedRequest = originalRequest.withIrGpuArtifact(Optional.of(optimized));
        GpuBackendModuleArtifact selectedOpenCl = GpuBackendModuleArtifact.openClSource(
                descriptor.kernelSource(),
                descriptor.kernelResource(),
                "example-opencl-selected-source",
                "descriptor-opencl-source",
                "opencl-source-compile"
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                originalRequest,
                optimizedRequest,
                selectedOpenCl,
                GpuRuntimeCompileInvalidationStamp.from(optimizedRequest, selectedOpenCl, "example-preview"),
                GpuRuntimeCompileProvenance.from(optimizedRequest),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized))
        );
        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        StringBuilder builder = new StringBuilder();
        builder.append("- dumpArtifact.cudaProperties=")
                .append(dump.hasArtifact("cuda-source-preview.properties"))
                .append(System.lineSeparator());
        builder.append("- dumpArtifact.originalCudaPreview=")
                .append(dump.hasArtifact("original.preview.backend.cuda-c"))
                .append(System.lineSeparator());
        builder.append("- dumpArtifact.optimizedCudaPreview=")
                .append(dump.hasArtifact("optimized.preview.backend.cuda-c"))
                .append(System.lineSeparator());
        builder.append("- dumpArtifact.selectedBackendOpenCl=")
                .append(dump.hasArtifact("backend.opencl-c"))
                .append(System.lineSeparator());
        builder.append("- dumpPreview.selectedStage=optimized").append(System.lineSeparator());
        builder.append("- dumpPreview.execution=disabled").append(System.lineSeparator());
        builder.append("- dumpPreview.optimizedContainsHelper=")
                .append(dump.artifact("optimized.preview.backend.cuda-c").contains("jtg_fn_square_float"))
                .append(System.lineSeparator());
        builder.append("- dumpPreview.selectedBackendStillOpenCl=")
                .append(dump.artifact("backend.opencl-c").contains("__kernel void jtg_kernel"))
                .append(System.lineSeparator());
        return builder.toString();
    }

    private static GpuRuntimeCompileRequest previewCompileRequest() {
        GpuKernelDescriptor descriptor = previewDescriptor("inline://examples/cuda-source-preview.cu", "");
        return new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA"),
                Optional.of(previewIrGpuArtifact(true))
        );
    }

    private static GpuKernelDescriptor previewDescriptor(String resource, String source) {
        return new GpuKernelDescriptor(
                "jtg_kernel",
                resource,
                source,
                List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    private static IrGpuArtifact previewIrGpuArtifact(boolean helperOptimized) {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        helperOptimized ? List.of(new IrGpuModuleMethod(
                                "square",
                                "jtg_fn_square_float",
                                "float",
                                List.of(new IrGpuEntryParameter("value", "float", "PRIVATE", false, List.of()))
                        )) : List.of(),
                        List.of(),
                        helperOptimized ? List.of(
                                IrGpuMethodBody.entry(
                                        "kernel",
                                        "jtg_kernel",
                                        "body\n"
                                                + "  var int id = intrinsic(get_global_id template=\"\" args=[0])\n"
                                                + "  set output[id] = helper(jtg_fn_square_float args=[input[id]])\n"
                                                + "  return\n",
                                        List.of("jtg_fn_square_float")
                                ),
                                IrGpuMethodBody.helper(
                                        "square",
                                        "jtg_fn_square_float",
                                        "body\n  return (value * value)\n",
                                        List.of()
                                )
                        ) : List.of(
                                IrGpuMethodBody.entry(
                                        "kernel",
                                        "jtg_kernel",
                                        "body\n"
                                                + "  var int id = intrinsic(get_global_id template=\"\" args=[0])\n"
                                                + "  set output[id] = (input[id] * input[id])\n"
                                                + "  return\n",
                                        List.of()
                                )
                        )
                ),
                List.of(
                        new IrGpuEntryParameter("input", "float[]", "GLOBAL", true, List.of("const")),
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of())
                ),
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(IrGpuBackendOutput.openClSource("inline://examples/cuda-source-preview.cl")),
                "opencl",
                "off"
        );
    }
}
