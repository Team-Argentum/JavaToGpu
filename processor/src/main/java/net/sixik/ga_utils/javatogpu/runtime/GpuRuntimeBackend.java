package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuPreparedLauncher;

/**
 * Runtime backend contract for executing generated GPU kernel invocations.
 *
 * <p>A backend receives fully generated kernel metadata plus the original Java argument array and is responsible for
 * turning that invocation into an actual execution strategy, for example OpenCL launch, CPU fallback, remote dispatch,
 * or a test double.
 */
@FunctionalInterface
public interface GpuRuntimeBackend {

    /**
     * Returns the coarse backend target family handled by this runtime backend.
     */
    default GpuBackendTarget backendTarget() {
        return GpuBackendTarget.UNKNOWN;
    }

    /**
     * Describes runtime/backend capabilities for selection and fallback logic.
     *
     * <p>Backends that do not implement capability probing may return an unavailable or generic report.
     */
    default GpuRuntimeBackendReport describeCapabilities() {
        return GpuRuntimeBackendReport.unavailable(
                backendTarget(),
                getClass().getName(),
                "Capability report is not implemented for this backend"
        );
    }

    /**
     * Describes which backend-neutral production-promotion artifacts this backend can emit.
     *
     * <p>The default is intentionally fail-closed so newly added backends do not accidentally look production-promotion
     * ready before they wire the required runtime artifact surface.</p>
     */
    default GpuPromotionArtifactSupport promotionArtifactSupport() {
        return GpuPromotionArtifactSupport.none(backendTarget());
    }

    /**
     * Executes one GPU kernel invocation.
     *
     * @param invocation descriptor and launch arguments for the generated kernel call
     */
    void invoke(GpuKernelInvocation invocation);

    /**
     * Prepares a reusable launcher for repeated calls of the same generated kernel.
     *
     * <p>The default implementation is fail-closed. Backends should override this only when they can separate the cold
     * validation/compile path from the hot argument update/enqueue/readback path without weakening normal invocation
     * checks.</p>
     */
    default GpuPreparedLauncher prepare(GpuKernelInvocation invocation) {
        throw new UnsupportedOperationException(
                "Prepared GPU launchers are not implemented for backend " + backendTarget()
                        + " and kernel " + invocation.descriptor().kernelName()
        );
    }
}
