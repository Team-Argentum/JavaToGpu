package net.sixik.ga_utils.javatogpu.runtime;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Built-in policy that applies user-facing soft device preferences and hard exclusions.
 */
public final class GpuRuntimeDevicePreferencePolicy implements GpuRuntimeDevicePolicy {

    public static final String POLICY_ID = "javatogpu.device.preference";

    @Override
    public GpuRuntimeDevicePolicyDecision evaluate(GpuRuntimeDevicePolicyContext context) {
        GpuRuntimeDevicePreference preference = context.compileOptions().devicePreference();
        if (!preference.active()) {
            return GpuRuntimeDevicePolicyDecision.noChange(this);
        }

        LinkedHashMap<String, Integer> scoreAdjustments = new LinkedHashMap<>();
        LinkedHashSet<String> rejected = new LinkedHashSet<>();
        LinkedHashMap<String, String> facts = new LinkedHashMap<>();
        int rejectedCount = 0;
        int preferredCount = 0;
        for (GpuRuntimeDeviceProfile candidate : context.candidates()) {
            String key = GpuRuntimeDevicePolicyContext.deviceKey(candidate);
            String rejectionReason = preference.rejectionReason(candidate);
            int adjustment = preference.scoreAdjustment(candidate);
            boolean rejectedCandidate = !rejectionReason.isBlank();
            boolean preferredCandidate = adjustment > 0;
            facts.put(key + ".preferenceRejected", Boolean.toString(rejectedCandidate));
            facts.put(key + ".preferenceRejectionReason", rejectedCandidate ? rejectionReason : "none");
            facts.put(key + ".preferenceScoreAdjustment", Integer.toString(adjustment));
            facts.put(key + ".preferenceMatched", Boolean.toString(preferredCandidate));
            if (rejectedCandidate) {
                rejected.add(key);
                rejectedCount++;
            }
            if (preferredCandidate) {
                scoreAdjustments.put(key, adjustment);
                preferredCount++;
            }
        }
        facts.put("preference.active", "true");
        facts.put("preference.description", preference.describe());
        facts.put("preference.rejected.count", Integer.toString(rejectedCount));
        facts.put("preference.preferred.count", Integer.toString(preferredCount));

        return new GpuRuntimeDevicePolicyDecision(
                policyId(),
                policyVersion(),
                scoreAdjustments,
                rejected,
                facts,
                List.of(),
                true,
                List.of(),
                List.of("device preference applied: " + preference.describe())
        );
    }

    @Override
    public String policyId() {
        return POLICY_ID;
    }
}
