package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackend;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendReport;

/**
 * CUDA runtime backend placeholder for the first execution vertical-slice skeleton.
 */
public final class CudaGpuRuntimeBackend implements GpuRuntimeBackend {

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.CUDA;
    }

    @Override
    public GpuRuntimeBackendReport describeCapabilities() {
        return GpuRuntimeBackendReport.unavailable(
                GpuBackendTarget.CUDA,
                "CUDA",
                "CUDA native execution bridge is not implemented yet"
        );
    }

    @Override
    public void invoke(GpuKernelInvocation invocation) {
        throw new UnsupportedOperationException(
                "CUDA runtime invocation is not implemented yet; use the execution pipeline skeleton for fail-closed receipts"
        );
    }
}
