package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only auto-vectorization artifact snapshot for validation reports and CI exports.
 */
public record GpuIrAutoVectorizationArtifactSnapshot(
        GpuIrAutoVectorizationPreview preview,
        GpuIrAutoVectorizationRewriteDryRunReport dryRunReport,
        GpuIrAutoVectorizationResolvedRewriteOperations resolvedRewriteOperations
) {
    public GpuIrAutoVectorizationArtifactSnapshot {
        preview = Objects.requireNonNull(preview, "preview");
        dryRunReport = Objects.requireNonNull(dryRunReport, "dryRunReport");
        resolvedRewriteOperations = Objects.requireNonNull(resolvedRewriteOperations, "resolvedRewriteOperations");
    }

    public int candidateCount() {
        return preview.rewriteCandidateCount();
    }

    public int warningCount() {
        return preview.warningCount();
    }

    public int rejectionCount() {
        return preview.rejectionCount();
    }

    public int rewriteBlockedCandidateCount() {
        return preview.rewriteBlockedCandidateCount();
    }

    public boolean hasRewriteBlockedCandidates() {
        return preview.hasRewriteBlockedCandidates();
    }

    public GpuIrAutoVectorizationRewritePlan rewritePlan() {
        return preview.rewritePlan();
    }

    public GpuIrAutoVectorizationRewritePolicy rewritePolicy() {
        return preview.rewritePolicy();
    }

    public GpuIrAutoVectorizationReadinessSummaryReport readinessSummaryReport() {
        return GpuIrAutoVectorizationReadinessSummaryReport.from(
                preview,
                dryRunReport,
                resolvedRewriteOperations
        );
    }

    public GpuIrAutoVectorizationBlockerExplanation blockerExplanation() {
        return readinessSummaryReport().blockerExplanation();
    }

    public GpuIrAutoVectorizationNoCandidateBucketSummaryReport noCandidateBucketSummaryReport() {
        return readinessSummaryReport().noCandidateBucketSummaryReport();
    }

    public GpuIrAutoVectorizationProofLayerReadinessSummaryReport proofLayerReadinessSummaryReport() {
        return GpuIrAutoVectorizationProofLayerReadinessSummaryReport.from(preview.proofBundle());
    }

    public Map<String, String> artifactFields(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        Map<String, String> values = new LinkedHashMap<>();
        GpuIrAutoVectorizationRewritePlan plan = rewritePlan();
        GpuIrAutoVectorizationRewritePolicy policy = rewritePolicy();
        GpuIrAutoVectorizationReadinessSummaryReport readinessSummary = readinessSummaryReport();

        values.put(prefix + "Candidates", Integer.toString(candidateCount()));
        values.put(prefix + "Warnings", Integer.toString(warningCount()));
        values.put(prefix + "Rejections", Integer.toString(rejectionCount()));
        values.put(prefix + "RewriteReadiness", preview.rewriteReadiness().artifactValue());
        values.put(prefix + "CanApplyRewrite", Boolean.toString(preview.canApplyRewrite()));
        values.putAll(preview.proofDecision().artifactFields(prefix + "ProofDecision"));
        values.put(prefix + "HasPolicyBlockedRewrite", Boolean.toString(preview.hasPolicyBlockedRewrite()));
        values.putAll(readinessSummary.artifactFields(prefix + "Readiness"));
        values.putAll(noCandidateBucketSummaryReport().artifactFields(prefix + "NoCandidate"));
        values.putAll(blockerExplanation().artifactFields(prefix + "Blocker"));
        values.put(prefix + "RewriteBlockedCandidates", Integer.toString(rewriteBlockedCandidateCount()));
        values.put(prefix + "HasRewriteBlockedCandidates", Boolean.toString(hasRewriteBlockedCandidates()));
        preview.firstBlockingDiagnosticSummary()
                .ifPresent(diagnostic -> values.put(prefix + "FirstBlockingDiagnostic", diagnostic));
        preview.firstBlockingDiagnosticFamily()
                .ifPresent(family -> values.put(prefix + "FirstBlockingDiagnosticFamily", family));

        values.put(prefix + "RewritePlanCandidates", Integer.toString(plan.candidateCount()));
        values.put(prefix + "RewritePlanInsertions", Integer.toString(plan.insertionCount()));
        values.put(prefix + "RewritePlanReplacements", Integer.toString(plan.replacementCount()));
        values.put(prefix + "RewritePlanOperations", Integer.toString(plan.operationCount()));
        values.put(prefix + "RewritePlanGuards", Integer.toString(plan.guardDiagnostics().size()));

        values.put(prefix + "RewritePolicyCanRewrite", Boolean.toString(policy.canRewrite()));
        values.put(prefix + "RewritePolicyReadiness", policy.readiness().artifactValue());
        values.put(prefix + "RewritePolicyPlannedOperations", Integer.toString(policy.plannedOperationCount()));
        values.put(prefix + "RewritePolicyBlockingGuards", Integer.toString(policy.blockingGuards().size()));
        policy.firstBlockingGuard()
                .ifPresent(guard -> values.put(prefix + "RewritePolicyFirstBlockingGuardFamily", guard.family().artifactValue()));

        values.put(prefix + "RewriteDryRunReadiness", dryRunReport.readiness().artifactValue());
        values.put(prefix + "RewriteDryRunSuccessful", Boolean.toString(dryRunReport.successful()));
        values.put(prefix + "RewriteDryRunDiagnostics", Integer.toString(dryRunReport.diagnostics().size()));
        values.put(prefix + "RewriteDryRunCandidates", Integer.toString(dryRunReport.candidateCount()));
        values.put(prefix + "RewriteDryRunOperations", Integer.toString(dryRunReport.operationCount()));
        if (dryRunReport.hasFailures()) {
            values.put(prefix + "RewriteDryRunFirstDiagnostic", dryRunReport.firstDiagnostic());
        }

        values.put(prefix + "ResolvedRewriteInsertions", Integer.toString(resolvedRewriteOperations.insertions().size()));
        values.put(prefix + "ResolvedRewriteReplacements", Integer.toString(resolvedRewriteOperations.replacements().size()));
        values.put(prefix + "ResolvedRewriteOperations", Integer.toString(resolvedRewriteOperations.operationCount()));
        if (!resolvedRewriteOperations.insertions().isEmpty()) {
            values.put(prefix + "ResolvedRewriteFirstInsertion", resolvedRewriteOperations.insertions().get(0).summary());
        }
        if (!resolvedRewriteOperations.replacements().isEmpty()) {
            values.put(prefix + "ResolvedRewriteFirstReplacement", resolvedRewriteOperations.replacements().get(0).summary());
        }

        plan.guardFamilyTypeCounts().entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().artifactValue()))
                .forEach(entry -> values.put(
                        prefix + "RewritePlanGuardFamily." + entry.getKey().artifactValue(),
                        Long.toString(entry.getValue())
                ));
        values.putAll(preview.rewritePlanProofSummary().artifactFields(prefix + "ProofRewritePlan"));
        values.putAll(preview.proofBundle().artifactFields(prefix + "ProofBundle"));
        values.putAll(proofLayerReadinessSummaryReport().artifactFields(prefix + "ProofLayerReadiness"));
        values.put(prefix + "VectorTypeCounts", mapSummary(preview.vectorTypeCounts()));
        values.put(prefix + "UniqueVectorTypes", Integer.toString(preview.vectorTypeCounts().size()));
        preview.vectorTypeCounts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> values.put(
                        prefix + "VectorType." + entry.getKey(),
                        Long.toString(entry.getValue())
                ));
        values.put(prefix + "WarningFamilyCounts", mapSummary(preview.warningFamilyCounts()));
        values.put(prefix + "UniqueWarningFamilies", Integer.toString(preview.warningFamilyCounts().size()));
        preview.warningFamilyCounts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> values.put(
                        prefix + "WarningFamily." + entry.getKey(),
                        Long.toString(entry.getValue())
                ));
        values.put(prefix + "RejectionReasonCounts", rejectionReasonSummary(preview.rejectionReasonCounts()));
        values.put(prefix + "UniqueRejectionReasons", Integer.toString(preview.rejectionReasonCounts().size()));
        preview.rejectionReasonCounts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> values.put(
                        prefix + "RejectionReason." + entry.getKey().name(),
                        Long.toString(entry.getValue())
                ));
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorization");
    }

    public String summary() {
        GpuIrAutoVectorizationReadinessSummaryReport readinessSummary = readinessSummaryReport();
        return "auto-vectorization artifact snapshot candidates=" + candidateCount()
                + " warnings=" + warningCount()
                + " rejections=" + rejectionCount()
                + " rewriteReadiness=" + preview.rewriteReadiness().artifactValue()
                + " readinessVerdict=" + readinessSummary.verdict()
                + " readinessBlockers=" + readinessSummary.blockingReasons()
                + " blockerExplanation={" + blockerExplanation().ciSummaryLine() + "}"
                + " canApplyRewrite=" + preview.canApplyRewrite()
                + " rewritePlanOperations=" + rewritePlan().operationCount()
                + " rewritePlanGuards=" + rewritePlan().guardDiagnostics().size()
                + " uniqueVectorTypes=" + preview.vectorTypeCounts().size()
                + " uniqueWarningFamilies=" + preview.warningFamilyCounts().size()
                + " uniqueRejectionReasons=" + preview.rejectionReasonCounts().size()
                + " dryRunReadiness=" + dryRunReport.readiness().artifactValue()
                + " dryRunSuccessful=" + dryRunReport.successful()
                + " resolvedRewriteOperations=" + resolvedRewriteOperations.operationCount();
    }

    private static String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private static String rejectionReasonSummary(Map<GpuIrAutoVectorizationRejectionReason, Long> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().name() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
}
