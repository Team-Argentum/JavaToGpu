package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilationHook;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilationResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;

import java.util.Set;

/**
 * Example read-only compilation hook. It is safe to install because it returns the original receipt.
 */
public final class ExampleBackendCompilationHook implements GpuBackendCompilationHook {

    @Override
    public String extensionId() {
        return "examples.backend-hook.compilation";
    }

    @Override
    public String extensionVersion() {
        return "1";
    }

    @Override
    public int extensionOrder() {
        return 30_300;
    }

    @Override
    public Set<GpuBackendTarget> backendTargets() {
        return Set.of(GpuBackendTarget.OPENCL);
    }

    @Override
    public GpuBackendCompilationResult afterCompilation(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendCompilationResult compilationResult
    ) {
        return compilationResult;
    }
}
