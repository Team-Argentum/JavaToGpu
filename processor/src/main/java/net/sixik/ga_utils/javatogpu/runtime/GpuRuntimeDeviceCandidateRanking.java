package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

/**
 * Deterministic final ranking for one runtime device candidate.
 */
public record GpuRuntimeDeviceCandidateRanking(
        String deviceKey,
        GpuRuntimeDeviceProfile profile,
        int baseScore,
        int policyScoreAdjustment,
        int totalScore,
        boolean rejected,
        List<String> diagnostics
) {

    public GpuRuntimeDeviceCandidateRanking {
        deviceKey = deviceKey == null || deviceKey.isBlank() ? "device:unknown" : deviceKey;
        profile = java.util.Objects.requireNonNull(profile, "profile");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
