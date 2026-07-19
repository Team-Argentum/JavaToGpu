package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

/**
 * Test-only external backend provider used to verify ServiceLoader registration without touching production built-ins.
 */
public final class TestServiceLoadedRuntimeBackendProvider implements GpuRuntimeBackendProvider {

    public TestServiceLoadedRuntimeBackendProvider() {
    }

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.UNKNOWN;
    }

    @Override
    public String providerId() {
        return "test.backend-provider:service-loaded";
    }

    @Override
    public String providerVersion() {
        return "test-1";
    }

    @Override
    public int providerOrder() {
        return 10_000;
    }

    @Override
    public GpuRuntimeBackendAdapter createAdapter() {
        return new PlannedGpuRuntimeBackendAdapter(backendTarget());
    }

    @Override
    public GpuRuntimeBackendExecutionSupport executionSupport() {
        return GpuRuntimeBackendExecutionSupport.discoveryOnly(
                backendTarget(),
                providerId(),
                "test provider loaded from a temporary ServiceLoader descriptor"
        );
    }
}
