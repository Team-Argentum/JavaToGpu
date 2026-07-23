package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLoweringHook;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLoweringResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;

import java.util.Set;

/**
 * Example read-only lowerer hook. It observes OpenCL lowering receipts and never replaces them.
 */
public final class ExampleBackendLoweringHook implements GpuBackendLoweringHook {

    @Override
    public String extensionId() {
        return "examples.backend-hook.lowering";
    }

    @Override
    public String extensionVersion() {
        return "1";
    }

    @Override
    public int extensionOrder() {
        return 30_200;
    }

    @Override
    public Set<GpuBackendTarget> backendTargets() {
        return Set.of(GpuBackendTarget.OPENCL);
    }

    @Override
    public GpuBackendLoweringResult afterLowering(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendLoweringResult loweringResult
    ) {
        return loweringResult;
    }
}
