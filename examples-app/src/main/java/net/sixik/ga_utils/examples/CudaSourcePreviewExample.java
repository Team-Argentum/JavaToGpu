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
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;

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
        return builder.toString();
    }

    private static GpuRuntimeCompileRequest previewCompileRequest() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "jtg_kernel",
                "inline://examples/cuda-source-preview.cu",
                "",
                List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        return new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA"),
                Optional.of(previewIrGpuArtifact())
        );
    }

    private static IrGpuArtifact previewIrGpuArtifact() {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(new IrGpuModuleMethod(
                                "square",
                                "jtg_fn_square_float",
                                "float",
                                List.of(new IrGpuEntryParameter("value", "float", "PRIVATE", false, List.of()))
                        )),
                        List.of(),
                        List.of(
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
