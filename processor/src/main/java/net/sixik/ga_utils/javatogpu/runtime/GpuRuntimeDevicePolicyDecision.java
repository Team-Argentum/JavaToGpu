package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One policy contribution to deterministic device selection.
 */
public record GpuRuntimeDevicePolicyDecision(
        String policyId,
        String policyVersion,
        Map<String, Integer> scoreAdjustments,
        Set<String> rejectedDeviceKeys,
        Map<String, String> capabilityFacts,
        List<String> vendorQuirks,
        boolean compileOptionsValid,
        List<String> compileOptionDiagnostics,
        List<String> diagnostics
) {

    public GpuRuntimeDevicePolicyDecision {
        policyId = normalize(policyId, "policy:unknown");
        policyVersion = normalize(policyVersion, "unknown");
        scoreAdjustments = scoreAdjustments == null ? Map.of() : Map.copyOf(scoreAdjustments);
        rejectedDeviceKeys = rejectedDeviceKeys == null ? Set.of() : Set.copyOf(rejectedDeviceKeys);
        capabilityFacts = capabilityFacts == null ? Map.of() : Map.copyOf(capabilityFacts);
        vendorQuirks = vendorQuirks == null ? List.of() : List.copyOf(vendorQuirks);
        compileOptionDiagnostics = compileOptionDiagnostics == null ? List.of() : List.copyOf(compileOptionDiagnostics);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static GpuRuntimeDevicePolicyDecision noChange(GpuRuntimeDevicePolicy policy) {
        return new GpuRuntimeDevicePolicyDecision(
                policy.policyId(),
                policy.policyVersion(),
                Map.of(),
                Set.of(),
                Map.of(),
                List.of(),
                true,
                List.of(),
                List.of()
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
