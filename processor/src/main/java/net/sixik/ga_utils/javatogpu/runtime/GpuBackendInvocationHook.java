package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Set;

/**
 * Hook around backend invocation/readback results.
 */
public interface GpuBackendInvocationHook extends GpuBackendHook {

    default GpuBackendInvocationResult afterInvocation(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendInvocationResult invocationResult
    ) {
        return invocationResult;
    }

    @Override
    default Set<GpuExtensionCapability> extensionCapabilities() {
        return Set.of(GpuExtensionCapability.BACKEND_INVOCATION_HOOK);
    }

    @Override
    default GpuExtensionPhase extensionPhase() {
        return GpuExtensionPhase.BACKEND_INVOCATION;
    }

    @Override
    default GpuExtensionPermission extensionPermission() {
        return GpuExtensionPermission.READ_ONLY;
    }
}
