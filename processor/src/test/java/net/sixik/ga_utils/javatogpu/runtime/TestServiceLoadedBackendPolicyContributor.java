package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Set;

/**
 * Test-only backend hook used to verify ServiceLoader registration.
 */
public final class TestServiceLoadedBackendPolicyContributor implements GpuBackendPolicyContributor {

    public TestServiceLoadedBackendPolicyContributor() {
    }

    @Override
    public String extensionId() {
        return "test.backend-hook:service-loaded-policy";
    }

    @Override
    public String extensionVersion() {
        return "test-1";
    }

    @Override
    public int extensionOrder() {
        return 10_000;
    }

    @Override
    public Set<GpuBackendTarget> backendTargets() {
        return Set.of(GpuBackendTarget.CUDA);
    }
}
