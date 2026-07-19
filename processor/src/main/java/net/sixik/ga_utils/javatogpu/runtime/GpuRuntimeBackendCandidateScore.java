package net.sixik.ga_utils.javatogpu.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Explainable score evidence for one backend-selection candidate.
 *
 * <p>The current backend selector still preserves explicit fallback order. This score is audit evidence for reports,
 * CI, and future ranking policies rather than an automatic reordering mechanism.</p>
 */
public record GpuRuntimeBackendCandidateScore(
        int preferenceScore,
        int metadataScoreAdjustment,
        int runtimeScoreAdjustment,
        int policyScoreAdjustment,
        int totalScore,
        boolean rejected,
        List<String> diagnostics
) {

    public GpuRuntimeBackendCandidateScore {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static GpuRuntimeBackendCandidateScore estimate(
            int candidateIndex,
            GpuRuntimeBackendReport report,
            GpuRuntimeBackendCandidateMetadata metadata,
            boolean created,
            boolean rejected
    ) {
        return estimate(candidateIndex, report, metadata, created, rejected, List.of());
    }

    public static GpuRuntimeBackendCandidateScore estimate(
            int candidateIndex,
            GpuRuntimeBackendReport report,
            GpuRuntimeBackendCandidateMetadata metadata,
            boolean created,
            boolean rejected,
            List<GpuRuntimeBackendScoreContribution> policyContributions
    ) {
        GpuRuntimeBackendCandidateMetadata candidateMetadata = metadata == null
                ? GpuRuntimeBackendCandidateMetadata.unknown()
                : metadata;
        ArrayList<String> diagnostics = new ArrayList<>();
        int preferenceScore = Math.max(0, 1_000_000 - Math.max(0, candidateIndex) * 1_000);
        diagnostics.add("fallback preference order score +" + preferenceScore);

        int metadataScore = 0;
        int runtimeScore = 0;
        if (!created) {
            diagnostics.add("candidate was not created; runtime facts unavailable +0");
        } else if (report != null && report.available()) {
            RuntimeFactScore factScore = runtimeFactScore(report);
            runtimeScore = saturatingAdd(runtimeScore, factScore.score());
            diagnostics.addAll(factScore.diagnostics());
        } else {
            diagnostics.add("runtime report is unavailable; runtime facts +0");
        }

        if (candidateMetadata.productionAdapter()) {
            metadataScore = saturatingAdd(metadataScore, 5_000);
            diagnostics.add("production adapter +5000");
        } else {
            diagnostics.add("non-production adapter +0");
        }

        if (candidateMetadata.executionSupport().isPresent()) {
            GpuRuntimeBackendExecutionSupport support = candidateMetadata.executionSupport().orElseThrow();
            if (support.executionPipelineAvailable()) {
                metadataScore = saturatingAdd(metadataScore, 5_000);
                diagnostics.add("compile/prepare/invoke pipeline available +5000");
            } else if (support.supportsStage(GpuBackendPipelineStage.LOWER)) {
                metadataScore = saturatingAdd(metadataScore, 1_000);
                diagnostics.add("lowering support without execution pipeline +1000");
            } else if (support.supportsStage(GpuBackendPipelineStage.DISCOVER)) {
                metadataScore = saturatingAdd(metadataScore, 250);
                diagnostics.add("discovery-only support +250");
            } else {
                diagnostics.add("no declared backend pipeline stages +0");
            }
            int moduleFormatScore = support.moduleFormats().size() * 100;
            int capabilityScore = support.capabilityVocabulary().size() * 25;
            metadataScore = saturatingAdd(metadataScore, moduleFormatScore);
            metadataScore = saturatingAdd(metadataScore, capabilityScore);
            diagnostics.add("declared module formats +" + moduleFormatScore);
            diagnostics.add("declared capability vocabulary +" + capabilityScore);
        } else {
            diagnostics.add("execution support metadata unavailable +0");
        }

        PolicyContributionScore policyScore = policyContributionScore(policyContributions);
        diagnostics.addAll(policyScore.diagnostics());

        int totalScore = saturatingAdd(
                saturatingAdd(saturatingAdd(preferenceScore, metadataScore), runtimeScore),
                policyScore.score()
        );
        return new GpuRuntimeBackendCandidateScore(
                preferenceScore,
                metadataScore,
                runtimeScore,
                policyScore.score(),
                totalScore,
                rejected,
                diagnostics
        );
    }

    private static RuntimeFactScore runtimeFactScore(GpuRuntimeBackendReport report) {
        ArrayList<String> diagnostics = new ArrayList<>();
        int score = 10_000;
        diagnostics.add("runtime report is available +10000");

        int apiVersionScore = apiVersionScore(report.apiVersion());
        score = saturatingAdd(score, apiVersionScore);
        diagnostics.add("runtime API version +" + apiVersionScore);

        int runtimeCapabilityScore = report.runtimeCapabilities().size() * 100;
        score = saturatingAdd(score, runtimeCapabilityScore);
        diagnostics.add("runtime capability facts +" + runtimeCapabilityScore);

        int featureScore = report.features().size() * 500;
        score = saturatingAdd(score, featureScore);
        diagnostics.add("runtime feature flags +" + featureScore);

        int localMemoryScore = boundedLongScore(report.localMemoryBytes(), 1024L, 2_048);
        score = saturatingAdd(score, localMemoryScore);
        diagnostics.add("runtime local memory +" + localMemoryScore);

        int workGroupScore = boundedLongScore(report.maxWorkGroupSize(), 1L, 2_048);
        score = saturatingAdd(score, workGroupScore);
        diagnostics.add("runtime max work-group size +" + workGroupScore);
        return new RuntimeFactScore(score, diagnostics);
    }

    private static int apiVersionScore(GpuRuntimeApiVersion apiVersion) {
        if (apiVersion == null) {
            return 0;
        }
        return Math.max(0, apiVersion.major()) * 100 + Math.max(0, apiVersion.minor()) * 10;
    }

    private static int boundedLongScore(Long value, long divisor, int cap) {
        if (value == null || value <= 0L || divisor <= 0L) {
            return 0;
        }
        long score = value / divisor;
        return score > cap ? cap : (int) score;
    }

    private record RuntimeFactScore(int score, List<String> diagnostics) {
    }

    private static PolicyContributionScore policyContributionScore(
            List<GpuRuntimeBackendScoreContribution> policyContributions
    ) {
        if (policyContributions == null || policyContributions.isEmpty()) {
            return new PolicyContributionScore(0, List.of());
        }
        int score = 0;
        ArrayList<String> diagnostics = new ArrayList<>();
        for (GpuRuntimeBackendScoreContribution contribution : policyContributions) {
            if (contribution == null) {
                continue;
            }
            score = saturatingAdd(score, contribution.adjustment());
            diagnostics.addAll(contribution.diagnostics());
        }
        if (diagnostics.isEmpty() && score != 0) {
            diagnostics.add("policy score contributors " + signed(score));
        }
        return new PolicyContributionScore(score, diagnostics);
    }

    private record PolicyContributionScore(int score, List<String> diagnostics) {
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    private static int saturatingAdd(int left, int right) {
        long sum = (long) left + right;
        if (sum > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (sum < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) sum;
    }
}
