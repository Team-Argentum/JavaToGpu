package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Built-in policy that rejects backend-incompatible candidates and records core capability facts.
 */
public final class GpuRuntimeBackendCompatibilityDevicePolicy implements GpuRuntimeDevicePolicy {

    public static final String POLICY_ID = "javatogpu.device.backend-compatibility";

    @Override
    public GpuRuntimeDevicePolicyDecision evaluate(GpuRuntimeDevicePolicyContext context) {
        GpuBackendTarget requestedBackend = context.compileOptions().backendTarget();
        LinkedHashSet<String> rejected = new LinkedHashSet<>();
        LinkedHashMap<String, String> facts = new LinkedHashMap<>();
        for (GpuRuntimeDeviceProfile candidate : context.candidates()) {
            String key = GpuRuntimeDevicePolicyContext.deviceKey(candidate);
            if (requestedBackend != GpuBackendTarget.UNKNOWN && candidate.backendTarget() != requestedBackend) {
                rejected.add(key);
            }
            facts.put(key + ".deviceId", candidate.deviceId());
            facts.put(key + ".deviceClass", candidate.deviceClass().name().toLowerCase(java.util.Locale.ROOT));
            facts.put(key + ".computeUnits", Long.toString(candidate.computeUnits()));
            facts.put(key + ".globalMemoryBytes", Long.toString(candidate.globalMemoryBytes()));
            facts.put(key + ".localMemoryBytes", Long.toString(candidate.localMemoryBytes()));
            facts.put(key + ".maxWorkGroupSize", Long.toString(candidate.maxWorkGroupSize()));
            facts.put(key + ".preferredVectorWidthFloat", Long.toString(candidate.preferredVectorWidthFloat()));
            facts.put(key + ".unifiedMemory", Boolean.toString(candidate.unifiedMemory()));
            facts.put(key + ".supportsDoublePrecision", Boolean.toString(candidate.supportsDoublePrecision()));
            facts.put(key + ".supportsImages", Boolean.toString(candidate.supportsImages()));
            facts.put(key + ".supportsSubgroups", Boolean.toString(candidate.supportsSubgroups()));
        }
        return new GpuRuntimeDevicePolicyDecision(
                policyId(),
                policyVersion(),
                java.util.Map.of(),
                rejected,
                facts,
                List.of(),
                true,
                List.of(),
                rejected.isEmpty()
                        ? List.of("all candidates match the requested backend")
                        : List.of("backend-incompatible candidates were rejected")
        );
    }

    @Override
    public String policyId() {
        return POLICY_ID;
    }
}
