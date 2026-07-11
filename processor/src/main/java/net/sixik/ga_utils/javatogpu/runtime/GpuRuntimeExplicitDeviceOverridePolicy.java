package net.sixik.ga_utils.javatogpu.runtime;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Core policy that applies an explicit user device override as a required selector.
 */
public final class GpuRuntimeExplicitDeviceOverridePolicy implements GpuRuntimeDevicePolicy {

    public static final String POLICY_ID = "javatogpu.device.explicit-override";

    @Override
    public GpuRuntimeDevicePolicyDecision evaluate(GpuRuntimeDevicePolicyContext context) {
        GpuRuntimeDeviceOverride override = context.compileOptions().deviceOverride();
        if (!override.active()) {
            return GpuRuntimeDevicePolicyDecision.noChange(this);
        }

        LinkedHashSet<String> rejected = new LinkedHashSet<>();
        LinkedHashMap<String, String> facts = new LinkedHashMap<>();
        int matched = 0;
        for (GpuRuntimeDeviceProfile candidate : context.candidates()) {
            String key = GpuRuntimeDevicePolicyContext.deviceKey(candidate);
            boolean matches = override.matches(candidate);
            facts.put(key + ".explicitOverrideMatch", Boolean.toString(matches));
            if (matches) {
                matched++;
            } else {
                rejected.add(key);
            }
        }
        facts.put("override.active", "true");
        facts.put("override.selector", override.describe());
        facts.put("override.matchCount", Integer.toString(matched));

        return new GpuRuntimeDevicePolicyDecision(
                policyId(),
                policyVersion(),
                Map.of(),
                rejected,
                facts,
                List.of(),
                true,
                List.of(),
                matched == 0
                        ? List.of("explicit device override matched no discovered candidates: " + override.describe())
                        : List.of("explicit device override matched " + matched + " candidate(s): " + override.describe())
        );
    }

    @Override
    public String policyId() {
        return POLICY_ID;
    }
}
