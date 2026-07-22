package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of warming {@code @GPUTest} probe evidence for one backend/device candidate.
 */
public record GpuRuntimeMethodTestProbeEvidenceWarmupCandidateResult(
        String deviceKey,
        GpuRuntimeDeviceProfile deviceProfile,
        GpuRuntimeMethodTestGpuProbePlan gpuProbePlan,
        List<String> blockers,
        List<String> diagnostics
) {

    public GpuRuntimeMethodTestProbeEvidenceWarmupCandidateResult {
        deviceKey = normalize(deviceKey, deviceProfile == null ? "unknown" : GpuRuntimeDevicePolicyContext.deviceKey(deviceProfile));
        blockers = normalizeList(blockers);
        diagnostics = normalizeList(diagnostics);
    }

    public static GpuRuntimeMethodTestProbeEvidenceWarmupCandidateResult blocked(
            GpuRuntimeDeviceProfile deviceProfile,
            String blocker,
            String diagnostic
    ) {
        return new GpuRuntimeMethodTestProbeEvidenceWarmupCandidateResult(
                deviceProfile == null ? "unknown" : GpuRuntimeDevicePolicyContext.deviceKey(deviceProfile),
                deviceProfile,
                null,
                List.of(normalize(blocker, "method-test-probe-evidence-warmup-blocked")),
                List.of(normalize(diagnostic, "method-test probe evidence warm-up was blocked"))
        );
    }

    public boolean warmupReady() {
        return blockers.isEmpty() && gpuProbePlan != null && gpuProbePlan.gpuProbeReady();
    }

    public boolean warmupPassed() {
        return warmupReady() && gpuProbePlan.gpuProbePassed();
    }

    public String status() {
        if (!blockers.isEmpty() || gpuProbePlan == null || !gpuProbePlan.gpuProbeReady()) {
            return "blocked";
        }
        return gpuProbePlan.gpuProbePassed() ? "passed" : "failed";
    }

    public String firstBlocker() {
        if (!blockers.isEmpty()) {
            return blockers.get(0);
        }
        return gpuProbePlan == null ? "method-test-probe-evidence-warmup-not-run" : gpuProbePlan.firstBlocker();
    }

    public String firstFailure() {
        return gpuProbePlan == null ? "none" : gpuProbePlan.firstFailure();
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = normalize(prefix, "methodTestProbeEvidenceWarmupCandidate");
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".deviceKey", deviceKey);
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".warmupReady", Boolean.toString(warmupReady()));
        fields.put(normalizedPrefix + ".warmupPassed", Boolean.toString(warmupPassed()));
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        fields.put(normalizedPrefix + ".firstFailure", firstFailure());
        if (deviceProfile != null) {
            fields.put(normalizedPrefix + ".deviceId", deviceProfile.deviceId());
            fields.put(normalizedPrefix + ".deviceLabel", deviceProfile.deviceLabel());
            fields.put(normalizedPrefix + ".vendor", deviceProfile.vendor());
            fields.put(normalizedPrefix + ".backendTarget", deviceProfile.backendTarget().name());
            fields.put(normalizedPrefix + ".deviceClass", deviceProfile.deviceClass().name().toLowerCase(java.util.Locale.ROOT));
        }
        if (gpuProbePlan != null) {
            fields.putAll(gpuProbePlan.artifactFields(normalizedPrefix + ".gpuProbe"));
        }
        writeList(fields, normalizedPrefix + ".blocker", blockers);
        writeList(fields, normalizedPrefix + ".diagnostic", diagnostics);
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Method test probe evidence warm-up candidate: ")
                .append(status())
                .append('\n');
        builder.append("Device: ").append(deviceKey).append('\n');
        if (deviceProfile != null) {
            builder.append("Label: ").append(deviceProfile.deviceLabel()).append('\n');
        }
        if (!"none".equals(firstBlocker())) {
            builder.append("First blocker: ").append(firstBlocker()).append('\n');
        }
        if (!"none".equals(firstFailure())) {
            builder.append("First failure: ").append(firstFailure()).append('\n');
        }
        if (gpuProbePlan != null) {
            builder.append('\n').append(gpuProbePlan.toMarkdown());
        }
        if (!diagnostics.isEmpty()) {
            builder.append('\n').append("Diagnostics:").append('\n');
            for (String diagnostic : diagnostics) {
                builder.append("- ").append(diagnostic).append('\n');
            }
        }
        return builder.toString();
    }

    private static void writeList(LinkedHashMap<String, String> fields, String prefix, List<String> values) {
        fields.put(prefix + ".count", Integer.toString(values.size()));
        for (int index = 0; index < values.size(); index++) {
            fields.put(prefix + "." + index, values.get(index));
        }
    }

    private static List<String> normalizeList(List<String> values) {
        return values == null
                ? List.of()
                : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
