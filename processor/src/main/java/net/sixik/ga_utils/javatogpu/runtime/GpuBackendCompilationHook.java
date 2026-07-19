package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Set;

/**
 * Hook around backend compilation results.
 */
public interface GpuBackendCompilationHook extends GpuBackendHook {

    default GpuBackendCompilationResult afterCompilation(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendCompilationResult compilationResult
    ) {
        return compilationResult;
    }

    @Override
    default Set<GpuExtensionCapability> extensionCapabilities() {
        return Set.of(GpuExtensionCapability.BACKEND_COMPILATION_HOOK);
    }

    @Override
    default GpuExtensionPhase extensionPhase() {
        return GpuExtensionPhase.BACKEND_COMPILATION;
    }

    @Override
    default GpuExtensionPermission extensionPermission() {
        return GpuExtensionPermission.READ_ONLY;
    }
}
