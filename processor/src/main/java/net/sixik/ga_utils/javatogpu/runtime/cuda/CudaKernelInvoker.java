package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendInvocationResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelInvoker;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendPreparationResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuPreparedKernel;

import java.util.List;
import java.util.Objects;

/**
 * CUDA invoke-stage skeleton. Native kernel launch starts after compile and prepare are real.
 */
final class CudaKernelInvoker implements GpuBackendKernelInvoker<GpuPreparedKernel> {

    private final CudaKernelLauncherBridgeRegistry launchers;
    private final CudaKernelReadbackBridgeRegistry readbacks;
    private CudaKernelLaunchResult lastLaunchResult;
    private CudaKernelReadbackResult lastReadbackResult;

    CudaKernelInvoker() {
        this(CudaKernelLauncherBridgeRegistry.loadWithBuiltIns(), CudaKernelReadbackBridgeRegistry.loadWithBuiltIns());
    }

    CudaKernelInvoker(CudaKernelLauncherBridgeRegistry launchers) {
        this(launchers, CudaKernelReadbackBridgeRegistry.loadWithBuiltIns());
    }

    CudaKernelInvoker(
            CudaKernelLauncherBridgeRegistry launchers,
            CudaKernelReadbackBridgeRegistry readbacks
    ) {
        this.launchers = Objects.requireNonNull(launchers, "launchers");
        this.readbacks = Objects.requireNonNull(readbacks, "readbacks");
    }

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.CUDA;
    }

    @Override
    public void invoke(GpuPreparedKernel preparedKernel, GpuExecutionConfig executionConfig) {
        lastLaunchResult = null;
        lastReadbackResult = null;
        if (!(preparedKernel instanceof CudaPreparedKernel cudaPreparedKernel)) {
            return;
        }
        CudaKernelLaunchRequest request = CudaKernelLaunchRequest.from(cudaPreparedKernel, executionConfig);
        if (!request.kernelLauncherRequested()) {
            return;
        }
        lastLaunchResult = launchers.launch(request);
        cudaPreparedKernel.recordKernelLaunchResult(lastLaunchResult);
        CudaKernelReadbackRequest readbackRequest = CudaKernelReadbackRequest.from(
                cudaPreparedKernel,
                lastLaunchResult,
                lastLaunchResult.executionConfig() == null ? executionConfig : lastLaunchResult.executionConfig()
        );
        if (!readbackRequest.readbackRequested()) {
            return;
        }
        lastReadbackResult = readbacks.readBack(readbackRequest);
        cudaPreparedKernel.recordKernelReadbackResult(lastReadbackResult);
    }

    @Override
    public GpuBackendInvocationResult invocationResult(
            GpuPreparedKernel preparedKernel,
            GpuBackendPreparationResult preparationResult,
            GpuExecutionConfig executionConfig,
            int readbackCompletedCount
    ) {
        if (preparationResult == null || !preparationResult.prepared()) {
            return GpuBackendInvocationResult.skipped(
                    GpuBackendTarget.CUDA,
                    preparationResult,
                    List.of("cuda-prepare-stage-not-available"),
                    List.of("CUDA kernel launch is skipped until native argument binding succeeds")
            );
        }
        if (lastLaunchResult != null && lastLaunchResult.succeeded()) {
            if (lastReadbackResult != null && lastReadbackResult.succeeded()) {
                return GpuBackendInvocationResult.invoked(
                        preparationResult,
                        lastLaunchResult.executionConfig() == null ? executionConfig : lastLaunchResult.executionConfig(),
                        lastReadbackResult.readbackRequiredCount(),
                        lastReadbackResult.readbackCompletedCount(),
                        diagnostics(lastLaunchResult, lastReadbackResult)
                );
            }
            if (lastReadbackResult != null) {
                return GpuBackendInvocationResult.unsupported(
                        GpuBackendTarget.CUDA,
                        preparationResult,
                        lastReadbackResult.blockers(),
                        diagnostics(lastLaunchResult, lastReadbackResult)
                );
            }
            return GpuBackendInvocationResult.invoked(
                    preparationResult,
                    lastLaunchResult.executionConfig() == null ? executionConfig : lastLaunchResult.executionConfig(),
                    lastLaunchResult.readbackRequiredCount(),
                    lastLaunchResult.readbackCompletedCount(),
                    lastLaunchResult.diagnostics()
            );
        }
        if (lastLaunchResult != null) {
            return GpuBackendInvocationResult.unsupported(
                    GpuBackendTarget.CUDA,
                    preparationResult,
                    lastLaunchResult.blockers(),
                    lastLaunchResult.diagnostics()
            );
        }
        return GpuBackendInvocationResult.unsupported(
                GpuBackendTarget.CUDA,
                preparationResult,
                List.of("cuda-native-kernel-launch-missing"),
                List.of("CUDA native kernel launch was not requested, unavailable, or blocked before submission")
        );
    }

    private static List<String> diagnostics(
            CudaKernelLaunchResult launchResult,
            CudaKernelReadbackResult readbackResult
    ) {
        java.util.ArrayList<String> diagnostics = new java.util.ArrayList<>();
        if (launchResult != null) {
            diagnostics.addAll(launchResult.diagnostics());
        }
        if (readbackResult != null) {
            diagnostics.addAll(readbackResult.diagnostics());
        }
        return List.copyOf(diagnostics);
    }
}
