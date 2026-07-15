package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * User-facing explanation for one runtime backend selection attempt.
 *
 * <p>The explanation is intentionally backend-neutral. Today OpenCL is the only production adapter, but future CUDA,
 * Vulkan/SPIR-V, Metal, or custom backends can expose the same candidate/rejection/selection surface.</p>
 */
public record GpuRuntimeBackendSelectionExplanation(
        boolean matched,
        GpuBackendTarget selectedBackendTarget,
        String selectedBackendName,
        String selectedDeviceLabel,
        List<String> failureReasons,
        List<GpuRuntimeBackendCandidateDecision> candidateDecisions
) {

    public GpuRuntimeBackendSelectionExplanation {
        selectedBackendTarget = selectedBackendTarget == null ? GpuBackendTarget.UNKNOWN : selectedBackendTarget;
        selectedBackendName = selectedBackendName == null || selectedBackendName.isBlank()
                ? "none"
                : selectedBackendName;
        selectedDeviceLabel = selectedDeviceLabel == null || selectedDeviceLabel.isBlank()
                ? (matched ? "unknown" : "none")
                : selectedDeviceLabel;
        failureReasons = failureReasons == null ? List.of() : List.copyOf(failureReasons);
        candidateDecisions = candidateDecisions == null ? List.of() : List.copyOf(candidateDecisions);
    }

    /**
     * Builds an explanation from a selection result.
     */
    public static GpuRuntimeBackendSelectionExplanation from(GpuRuntimeSelectionResult result) {
        Objects.requireNonNull(result, "result");
        GpuRuntimeBackendSelection selection = result.selection();
        if (selection == null) {
            return new GpuRuntimeBackendSelectionExplanation(
                    false,
                    GpuBackendTarget.UNKNOWN,
                    "none",
                    "none",
                    result.failureReasons(),
                    result.candidateDecisions()
            );
        }

        GpuRuntimeBackendReport report = selection.report();
        return new GpuRuntimeBackendSelectionExplanation(
                true,
                report.backendTarget(),
                report.backendName(),
                report.deviceLabel(),
                result.failureReasons(),
                result.candidateDecisions()
        );
    }

    /**
     * Returns a compact one-line explanation suitable for logs.
     */
    public String summary() {
        if (candidateDecisions.isEmpty()) {
            return matched ? "selected " + selectedBackendName : failureSummary();
        }
        return String.join(
                " | ",
                candidateDecisions.stream()
                        .map(GpuRuntimeBackendCandidateDecision::summary)
                        .toList()
        );
    }

    /**
     * Returns all recorded rejection reasons as one human-readable summary.
     */
    public String failureSummary() {
        if (failureReasons.isEmpty()) {
            return "no backend candidates were provided";
        }
        return String.join(" | ", failureReasons);
    }

    /**
     * Returns deterministic key/value fields for logs, reports, or future lifecycle-journal events.
     */
    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "backendSelection" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".matched", Boolean.toString(matched));
        fields.put(normalizedPrefix + ".selected.backendTarget", selectedBackendTarget.name());
        fields.put(normalizedPrefix + ".selected.backendName", selectedBackendName);
        fields.put(normalizedPrefix + ".selected.deviceLabel", selectedDeviceLabel);
        fields.put(normalizedPrefix + ".failure.count", Integer.toString(failureReasons.size()));
        for (int index = 0; index < failureReasons.size(); index++) {
            fields.put(normalizedPrefix + ".failure." + index, failureReasons.get(index));
        }
        fields.put(normalizedPrefix + ".candidate.count", Integer.toString(candidateDecisions.size()));
        for (int index = 0; index < candidateDecisions.size(); index++) {
            fields.putAll(candidateDecisions.get(index).artifactFields(normalizedPrefix + ".candidate." + index));
        }
        return Collections.unmodifiableMap(fields);
    }

    /**
     * Renders the explanation as compact Markdown for examples, CLIs, and troubleshooting output.
     */
    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Backend selection: ").append(matched ? "matched" : "not matched").append('\n');
        if (matched) {
            builder.append("Selected: ")
                    .append(selectedBackendName)
                    .append(" (`")
                    .append(selectedBackendTarget)
                    .append("`) on ")
                    .append(selectedDeviceLabel)
                    .append('\n');
        }
        if (!failureReasons.isEmpty()) {
            builder.append('\n').append("Failures:").append('\n');
            for (String failureReason : failureReasons) {
                builder.append("- ").append(failureReason).append('\n');
            }
        }
        if (!candidateDecisions.isEmpty()) {
            builder.append('\n').append("Candidates:").append('\n');
            for (GpuRuntimeBackendCandidateDecision decision : candidateDecisions) {
                builder.append("- ").append(decision.summary()).append('\n');
            }
        }
        return builder.toString();
    }
}
