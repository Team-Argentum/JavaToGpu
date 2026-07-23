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
        GpuRuntimeBackendCandidateMetadata metadata,
        GpuRuntimeBackendCandidateScore score,
        List<String> diagnostics
) {

    public GpuRuntimeBackendCandidateDecision(
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
        this(
                candidateIndex,
                backendTarget,
                backendName,
                report,
                ownership,
                created,
                selected,
                closed,
                firstBlocker,
                GpuRuntimeBackendCandidateMetadata.unknown(),
                null,
                diagnostics
        );
    }

    public GpuRuntimeBackendCandidateDecision {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        backendName = backendName == null || backendName.isBlank() ? "candidate." + candidateIndex : backendName;
        report = report == null ? Optional.empty() : report;
        ownership = ownership == null ? GpuRuntimeBackendOwnership.BORROWED : ownership;
        firstBlocker = firstBlocker == null || firstBlocker.isBlank() ? "none" : firstBlocker;
        metadata = metadata == null ? GpuRuntimeBackendCandidateMetadata.unknown() : metadata;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        score = score == null
                ? GpuRuntimeBackendCandidateScore.estimate(
                        candidateIndex,
                        report.orElse(null),
                        metadata,
                        created,
                        !selected && (!diagnostics.isEmpty() || !created)
                )
                : score;
    }

    public static GpuRuntimeBackendCandidateDecision creationFailed(
            int candidateIndex,
            GpuRuntimeBackendOwnership ownership,
            RuntimeException exception
    ) {
        return creationFailed(candidateIndex, ownership, GpuRuntimeBackendCandidateMetadata.unknown(), exception);
    }

    public static GpuRuntimeBackendCandidateDecision creationFailed(
            int candidateIndex,
            GpuRuntimeBackendOwnership ownership,
            GpuRuntimeBackendCandidateMetadata metadata,
            RuntimeException exception
    ) {
        String message = exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                ? "backend-candidate-creation-failed"
                : exception.getMessage();
        GpuRuntimeBackendCandidateMetadata resolvedMetadata = metadata == null
                ? GpuRuntimeBackendCandidateMetadata.unknown()
                : metadata;
        boolean hasCatalogMetadata = resolvedMetadata.backendTarget() != GpuBackendTarget.UNKNOWN
                || resolvedMetadata.executionSupportPresent();
        return new GpuRuntimeBackendCandidateDecision(
                candidateIndex,
                hasCatalogMetadata ? resolvedMetadata.backendTarget() : GpuBackendTarget.UNKNOWN,
                hasCatalogMetadata ? resolvedMetadata.backendName() : "candidate." + candidateIndex,
                Optional.empty(),
                ownership,
                false,
                false,
                false,
                "creation-failed",
                resolvedMetadata,
                GpuRuntimeBackendCandidateScore.estimate(candidateIndex, null, resolvedMetadata, false, true),
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
        return rejected(candidateIndex, report, ownership, reasons, closed, GpuRuntimeBackendCandidateMetadata.unknown());
    }

    public static GpuRuntimeBackendCandidateDecision rejected(
            int candidateIndex,
            GpuRuntimeBackendReport report,
            GpuRuntimeBackendOwnership ownership,
            List<String> reasons,
            boolean closed,
            GpuRuntimeBackendCandidateMetadata metadata
    ) {
        return rejected(candidateIndex, report, ownership, reasons, closed, metadata, null);
    }

    public static GpuRuntimeBackendCandidateDecision rejected(
            int candidateIndex,
            GpuRuntimeBackendReport report,
            GpuRuntimeBackendOwnership ownership,
            List<String> reasons,
            boolean closed,
            GpuRuntimeBackendCandidateMetadata metadata,
            GpuRuntimeBackendCandidateScore score
    ) {
        List<String> diagnostics = reasons == null ? List.of() : List.copyOf(reasons);
        GpuRuntimeBackendCandidateMetadata resolvedMetadata = metadata == null
                ? GpuRuntimeBackendCandidateMetadata.unknown()
                : metadata;
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
                resolvedMetadata,
                score == null
                        ? GpuRuntimeBackendCandidateScore.estimate(candidateIndex, report, resolvedMetadata, true, true)
                        : score,
                diagnostics
        );
    }

    public static GpuRuntimeBackendCandidateDecision selected(
            int candidateIndex,
            GpuRuntimeBackendReport report,
            GpuRuntimeBackendOwnership ownership
    ) {
        return selected(candidateIndex, report, ownership, GpuRuntimeBackendCandidateMetadata.unknown());
    }

    public static GpuRuntimeBackendCandidateDecision selected(
            int candidateIndex,
            GpuRuntimeBackendReport report,
            GpuRuntimeBackendOwnership ownership,
            GpuRuntimeBackendCandidateMetadata metadata
    ) {
        return selected(candidateIndex, report, ownership, metadata, null);
    }

    public static GpuRuntimeBackendCandidateDecision selected(
            int candidateIndex,
            GpuRuntimeBackendReport report,
            GpuRuntimeBackendOwnership ownership,
            GpuRuntimeBackendCandidateMetadata metadata,
            GpuRuntimeBackendCandidateScore score
    ) {
        GpuRuntimeBackendCandidateMetadata resolvedMetadata = metadata == null
                ? GpuRuntimeBackendCandidateMetadata.unknown()
                : metadata;
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
                resolvedMetadata,
                score == null
                        ? GpuRuntimeBackendCandidateScore.estimate(candidateIndex, report, resolvedMetadata, true, false)
                        : score,
                List.of("selected")
        );
    }

    public static GpuRuntimeBackendCandidateDecision notSelected(
            int candidateIndex,
            GpuRuntimeBackendReport report,
            GpuRuntimeBackendOwnership ownership,
            boolean closed,
            GpuRuntimeBackendCandidateMetadata metadata,
            String reason
    ) {
        return notSelected(candidateIndex, report, ownership, closed, metadata, reason, null);
    }

    public static GpuRuntimeBackendCandidateDecision notSelected(
            int candidateIndex,
            GpuRuntimeBackendReport report,
            GpuRuntimeBackendOwnership ownership,
            boolean closed,
            GpuRuntimeBackendCandidateMetadata metadata,
            String reason,
            GpuRuntimeBackendCandidateScore score
    ) {
        GpuRuntimeBackendCandidateMetadata resolvedMetadata = metadata == null
                ? GpuRuntimeBackendCandidateMetadata.unknown()
                : metadata;
        String diagnostic = reason == null || reason.isBlank()
                ? "not selected: lower score than selected candidate"
                : reason;
        return new GpuRuntimeBackendCandidateDecision(
                candidateIndex,
                report == null ? GpuBackendTarget.UNKNOWN : report.backendTarget(),
                report == null ? "candidate." + candidateIndex : report.backendName(),
                Optional.ofNullable(report),
                ownership,
                true,
                false,
                closed,
                "not-selected",
                resolvedMetadata,
                score == null
                        ? GpuRuntimeBackendCandidateScore.estimate(candidateIndex, report, resolvedMetadata, true, false)
                        : score,
                List.of(diagnostic)
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
        fields.put(normalizedPrefix + ".score.preference", Integer.toString(score.preferenceScore()));
        fields.put(normalizedPrefix + ".score.metadataAdjustment", Integer.toString(score.metadataScoreAdjustment()));
        fields.put(normalizedPrefix + ".score.runtimeAdjustment", Integer.toString(score.runtimeScoreAdjustment()));
        fields.put(normalizedPrefix + ".score.policyAdjustment", Integer.toString(score.policyScoreAdjustment()));
        fields.put(normalizedPrefix + ".score.total", Integer.toString(score.totalScore()));
        fields.put(normalizedPrefix + ".score.rejected", Boolean.toString(score.rejected()));
        fields.put(normalizedPrefix + ".score.diagnostic.count", Integer.toString(score.diagnostics().size()));
        for (int index = 0; index < score.diagnostics().size(); index++) {
            fields.put(normalizedPrefix + ".score.diagnostic." + index, score.diagnostics().get(index));
        }
        fields.putAll(metadata.artifactFields(normalizedPrefix + ".metadata"));
        fields.put(normalizedPrefix + ".executionSupport.present", Boolean.toString(metadata.executionSupportPresent()));
        fields.put(normalizedPrefix + ".executionSupport.moduleFormats", metadata.moduleFormatKeys());
        fields.put(normalizedPrefix + ".executionSupport.capabilities", metadata.capabilityKeys());
        fields.put(
                normalizedPrefix + ".executionSupport.executionPipeline.available",
                metadata.executionSupport()
                        .map(GpuRuntimeBackendExecutionSupport::executionPipelineAvailable)
                        .map(value -> Boolean.toString(value))
                        .orElse("false")
        );
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
