package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleFormat;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaNvccProcessCompilerBridgeTest {

    @Test
    void nvccFailureClassifiesMissingWindowsHostCompiler() {
        String compileLog = "nvcc fatal   : Cannot find compiler 'cl.exe' in PATH";

        assertEquals(
                "cuda-nvcc-host-compiler-missing:cl.exe",
                CudaNvccProcessCompilerBridge.nvccFailureBlockers(1, compileLog).get(0)
        );
        assertTrue(CudaNvccProcessCompilerBridge.nvccFailureBlockers(1, compileLog)
                .contains("cuda-nvcc-process-failed:1"));
        assertTrue(CudaNvccProcessCompilerBridge.nvccFailureDiagnostics(1, compileLog).get(0)
                .contains("Visual Studio Developer Command Prompt"));
    }

    @Test
    void nvccFailureClassifiesUnsupportedHostCompiler() {
        String compileLog = "nvcc fatal   : Unsupported Microsoft Visual Studio version";

        assertEquals(
                "cuda-nvcc-host-compiler-unsupported",
                CudaNvccProcessCompilerBridge.nvccFailureBlockers(1, compileLog).get(0)
        );
        assertTrue(CudaNvccProcessCompilerBridge.nvccFailureBlockers(1, compileLog)
                .contains("cuda-nvcc-process-failed:1"));
    }

    @Test
    void nvccFailureKeepsGenericExitCodeForUnclassifiedFailures() {
        assertEquals(
                java.util.List.of("cuda-nvcc-process-failed:2"),
                CudaNvccProcessCompilerBridge.nvccFailureBlockers(2, "ptxas fatal: synthetic failure")
        );
    }

    @Test
    void nvccFailureClassifiesVirtualArchitectureForBinaryOutput() {
        String compileLog = "nvcc fatal   : Option '--cubin' is not allowed when compiling for a virtual compute architecture";

        assertEquals(
                "cuda-nvcc-virtual-architecture-not-allowed",
                CudaNvccProcessCompilerBridge.nvccFailureBlockers(1, compileLog).get(0)
        );
        assertTrue(CudaNvccProcessCompilerBridge.nvccFailureDiagnostics(1, compileLog).stream()
                .anyMatch(diagnostic -> diagnostic.contains("sm_XX")));
        assertTrue(CudaNvccProcessCompilerBridge.nvccFailureDiagnostics(1, compileLog).stream()
                .anyMatch(diagnostic -> diagnostic.contains("Option '--cubin'")));
    }

    @Test
    void nvccOutputFormatDefaultsToPtx() {
        CudaNativeCompilationRequest request = nativeRequest(GpuBackendCompileOptions.cudaNvcc(List.of(), null));

        assertEquals(GpuBackendModuleFormat.PTX, request.nvccOutputModuleFormat());
        assertTrue(request.nvccOutputFormatBlocker().isEmpty());
    }

    @Test
    void nvccOutputFormatOptionEnablesNvccBridge() {
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA)
                .withCudaNvccOutputFormat("fatbin");

        assertEquals(
                GpuBackendCompileOptions.CUDA_COMPILER_BRIDGE_NVCC,
                options.backendOptions().cudaCompilerBridgeMode()
        );
        assertEquals(GpuBackendModuleFormat.FATBIN, options.backendOptions().cudaNvccOutputModuleFormat());
    }

    @Test
    void nvccCompileAcceptsCubinOutputFormatBeforeStartingProcess() {
        CudaNativeCompilationRequest request = nativeRequest(GpuBackendCompileOptions.cudaNvcc(List.of(), null, "cubin"));

        assertEquals(GpuBackendModuleFormat.CUBIN, request.nvccOutputModuleFormat());
        assertTrue(request.nvccOutputFormatBlocker().isEmpty());
    }

    @Test
    void nvccCompileRejectsUnknownOutputFormatBeforeStartingProcess() {
        CudaNativeCompilationResult result = new CudaNvccProcessCompilerBridge().compile(nativeRequest(
                GpuBackendCompileOptions.cuda(
                        List.of(),
                        Map.of(
                                GpuBackendCompileOptions.CUDA_COMPILER_BRIDGE_PROPERTY,
                                GpuBackendCompileOptions.CUDA_COMPILER_BRIDGE_NVCC,
                                GpuBackendCompileOptions.CUDA_NVCC_OUTPUT_FORMAT_PROPERTY,
                                "sass"
                        )
                )
        ));

        assertEquals("unsupported", result.status());
        assertTrue(result.blockers().contains("cuda-nvcc-output-format-unsupported:sass"));
    }

    private static CudaNativeCompilationRequest nativeRequest(GpuBackendCompileOptions backendOptions) {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "jtg_cuda_nvcc_test",
                "inline://tests/jtg_cuda_nvcc_test.cu",
                "extern \"C\" __global__ void jtg_cuda_nvcc_test() { }",
                List.of()
        );
        return new CudaNativeCompilationRequest(
                new GpuRuntimeCompileRequest(
                        descriptor,
                        new GpuRuntimeCompileOptions(GpuBackendTarget.CUDA, List.of(), "off", backendOptions),
                        GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA")
                ),
                GpuBackendModuleArtifact.cudaSource(
                        descriptor.kernelSource(),
                        descriptor.kernelResource(),
                        "test-cuda-source"
                ),
                backendOptions
        );
    }
}
