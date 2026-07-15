package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Audit record for one backend candidate considered by the runtime backend selection orchestrator.
 */
public record GpuRuntimeBackendCandidateDecision(
        int candidateIndex,
        GpuBackendTarget backendTarget,
        String backendName,
        Optional<GpuRuntimeBackendReport> report,
        GpuRuntimeBackendOwnership ownership,
        boolean created,
        boolean selected,
        boolean closed,
        String firstBlocker,
        List<String> diagnostics
) {

    public GpuRuntimeBackendCandidateDecision {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        backendName = backendName == null || backendName.isBlank() ? "candidate." + candidateIndex : backendName;
        report = report == null ? Optional.empty() : report;
        ownership = ownership == null ? GpuRuntimeBackendOwnership.BORROWED : ownership;
        firstBlocker = firstBlocker == null || firstBlocker.isBlank() ? "none" : firstBlocker;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static GpuRuntimeBackendCandidateDecision creationFailed(
            int candidateIndex,
            GpuRuntimeBackendOwnership ownership,
            RuntimeException exception
    ) {
        String message = exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                ? "backend-candidate-creation-failed"
                : exception.getMessage();
        return new GpuRuntimeBackendCandidateDecision(
                candidateIndex,
                GpuBackendTarget.UNKNOWN,
                "candidate." + candidateIndex,
                Optional.empty(),
                ownership,
                false,
                false,
                false,
                "creation-failed",
                List.of("Failed to create backend candidate: " + message)
        );
    }

    public static GpuRuntimeBackendCandidateDecision rejected(
            int candidateIndex,
            GpuRuntimeBackendReport report,
            GpuRuntimeBackendOwnership ownership,
            List<String> reasons,
            boolean closed
    ) {
        List<String> diagnostics = reasons == null ? List.of() : List.copyOf(reasons);
        return new GpuRuntimeBackendCandidateDecision(
                candidateIndex,
                report == null ? GpuBackendTarget.UNKNOWN : report.backendTarget(),
                report == null ? "candidate." + candidateIndex : report.backendName(),
                Optional.ofNullable(report),
                ownership,
                true,
                false,
                closed,
                diagnostics.isEmpty() ? "rejected" : diagnostics.get(0),
                diagnostics
        );
    }

    public static GpuRuntimeBackendCandidateDecision selected(
            int candidateIndex,
            GpuRuntimeBackendReport report,
            GpuRuntimeBackendOwnership ownership
    ) {
        return new GpuRuntimeBackendCandidateDecision(
                candidateIndex,
                report == null ? GpuBackendTarget.UNKNOWN : report.backendTarget(),
                report == null ? "candidate." + candidateIndex : report.backendName(),
                Optional.ofNullable(report),
                ownership,
                true,
                true,
                false,
                "none",
                List.of("selected")
        );
    }

    /**
     * Returns a compact human-readable candidate summary for logs and diagnostics.
     */
    public String summary() {
        if (selected) {
            return backendName + " selected";
        }
        if (!created) {
            return backendName + ": " + String.join("; ", diagnostics);
        }
        if (diagnostics.isEmpty()) {
            return backendName + ": " + firstBlocker;
        }
        return backendName + ": " + String.join("; ", diagnostics);
    }

    /**
     * Returns deterministic artifact fields for future selection explanation and lifecycle journals.
     */
    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "backendCandidate" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".index", Integer.toString(candidateIndex));
        fields.put(normalizedPrefix + ".backendTarget", backendTarget.name());
        fields.put(normalizedPrefix + ".backendName", backendName);
        fields.put(normalizedPrefix + ".ownership", ownership.name().toLowerCase(java.util.Locale.ROOT));
        fields.put(normalizedPrefix + ".created", Boolean.toString(created));
        fields.put(normalizedPrefix + ".selected", Boolean.toString(selected));
        fields.put(normalizedPrefix + ".closed", Boolean.toString(closed));
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
        report.ifPresent(value -> {
            fields.put(normalizedPrefix + ".report.available", Boolean.toString(value.available()));
            fields.put(normalizedPrefix + ".report.deviceLabel", value.deviceLabel() == null ? "unknown" : value.deviceLabel());
            fields.put(normalizedPrefix + ".report.apiVersionText", value.apiVersionText() == null ? "unknown" : value.apiVersionText());
        });
        fields.put(normalizedPrefix + ".diagnostic.count", Integer.toString(diagnostics.size()));
        for (int index = 0; index < diagnostics.size(); index++) {
            fields.put(normalizedPrefix + ".diagnostic." + index, diagnostics.get(index));
        }
        return java.util.Collections.unmodifiableMap(fields);
    }
}
