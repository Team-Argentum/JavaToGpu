package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendInvocationHook;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendInvocationResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;

import java.util.Set;

/**
 * Example read-only invocation hook. It observes launch/readback receipts without changing execution.
 */
public final class ExampleBackendInvocationHook implements GpuBackendInvocationHook {

    @Override
    public String extensionId() {
        return "examples.backend-hook.invocation";
    }

    @Override
    public String extensionVersion() {
        return "1";
    }

    @Override
    public int extensionOrder() {
        return 30_400;
    }

    @Override
    public Set<GpuBackendTarget> backendTargets() {
        return Set.of(GpuBackendTarget.OPENCL);
    }

    @Override
    public GpuBackendInvocationResult afterInvocation(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendInvocationResult invocationResult
    ) {
        return invocationResult;
    }
}
