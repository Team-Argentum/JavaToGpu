package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * One-line read-only readiness answer for future auto-vectorization rewrites.
 *
 * <p>This report does not apply rewrites. It combines the preview, proof bundle, rewrite policy,
 * dry-run, and resolved-operation layers into one CI-friendly artifact that explains what still
 * blocks a production auto-vectorization rewrite.</p>
 */
public record GpuIrAutoVectorizationReadinessSummaryReport(
        String methodName,
        String verdict,
        int candidateCount,
        int warningCount,
        int rejectionCount,
        int rewriteBlockedCandidateCount,
        int rewritePlanGuardCount,
        int proofBundleDiagnosticCount,
        int unsafeProofCount,
        int dryRunDiagnosticCount,
        int resolvedRewriteOperationCount,
        boolean previewCanApplyRewrite,
        boolean proofBundleRewriteSafe,
        boolean proofDecisionAllowsRewrite,
        boolean rewritePolicyCanRewrite,
        boolean dryRunSuccessful,
        boolean resolvedRewriteOperationsAvailable,
        String rewriteReadiness,
        String proofDecisionStatus,
        String rewritePolicyReadiness,
        String dryRunReadiness,
        List<String> blockingReasons,
        List<String> remainingWork,
        List<String> noCandidateBuckets,
        List<GpuIrAutoVectorizationNoCandidateExample> noCandidateExamples
) {
    private static final String VERDICT_NO_CANDIDATES = "notReady/noCandidates";
    private static final String VERDICT_REJECTED = "notReady/rejections";
    private static final String VERDICT_WARNINGS = "notReady/warnings";
    private static final String VERDICT_GUARDS = "notReady/rewriteGuards";
    private static final String VERDICT_PROOF_BLOCKED = "notReady/proofBlocked";
    private static final String VERDICT_POLICY_BLOCKED = "notReady/rewritePolicyBlocked";
    private static final String VERDICT_DRY_RUN_BLOCKED = "notReady/dryRunBlocked";
    private static final String VERDICT_RESOLUTION_MISSING = "notReady/resolvedOperationsMissing";
    private static final String VERDICT_READY = "readyForPrototypeRewrite";

    public GpuIrAutoVectorizationReadinessSummaryReport(
            String methodName,
            String verdict,
            int candidateCount,
            int warningCount,
            int rejectionCount,
            int rewriteBlockedCandidateCount,
            int rewritePlanGuardCount,
            int proofBundleDiagnosticCount,
            int unsafeProofCount,
            int dryRunDiagnosticCount,
            int resolvedRewriteOperationCount,
            boolean previewCanApplyRewrite,
            boolean proofBundleRewriteSafe,
            boolean proofDecisionAllowsRewrite,
            boolean rewritePolicyCanRewrite,
            boolean dryRunSuccessful,
            boolean resolvedRewriteOperationsAvailable,
            String rewriteReadiness,
            String proofDecisionStatus,
            String rewritePolicyReadiness,
            String dryRunReadiness,
            List<String> blockingReasons,
            List<String> remainingWork
    ) {
        this(
                methodName,
                verdict,
                candidateCount,
                warningCount,
                rejectionCount,
                rewriteBlockedCandidateCount,
                rewritePlanGuardCount,
                proofBundleDiagnosticCount,
                unsafeProofCount,
                dryRunDiagnosticCount,
                resolvedRewriteOperationCount,
                previewCanApplyRewrite,
                proofBundleRewriteSafe,
                proofDecisionAllowsRewrite,
                rewritePolicyCanRewrite,
                dryRunSuccessful,
                resolvedRewriteOperationsAvailable,
                rewriteReadiness,
                proofDecisionStatus,
                rewritePolicyReadiness,
                dryRunReadiness,
                blockingReasons,
                remainingWork,
                List.of(),
                List.of()
        );
    }

    public GpuIrAutoVectorizationReadinessSummaryReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (verdict == null || verdict.isBlank()) {
            throw new IllegalArgumentException("verdict must not be blank");
        }
        if (candidateCount < 0) {
            throw new IllegalArgumentException("candidateCount must be non-negative");
        }
        if (warningCount < 0) {
            throw new IllegalArgumentException("warningCount must be non-negative");
        }
        if (rejectionCount < 0) {
            throw new IllegalArgumentException("rejectionCount must be non-negative");
        }
        if (rewriteBlockedCandidateCount < 0) {
            throw new IllegalArgumentException("rewriteBlockedCandidateCount must be non-negative");
        }
        if (rewritePlanGuardCount < 0) {
            throw new IllegalArgumentException("rewritePlanGuardCount must be non-negative");
        }
        if (proofBundleDiagnosticCount < 0) {
            throw new IllegalArgumentException("proofBundleDiagnosticCount must be non-negative");
        }
        if (unsafeProofCount < 0) {
            throw new IllegalArgumentException("unsafeProofCount must be non-negative");
        }
        if (dryRunDiagnosticCount < 0) {
            throw new IllegalArgumentException("dryRunDiagnosticCount must be non-negative");
        }
        if (resolvedRewriteOperationCount < 0) {
            throw new IllegalArgumentException("resolvedRewriteOperationCount must be non-negative");
        }
        if (rewriteReadiness == null || rewriteReadiness.isBlank()) {
            throw new IllegalArgumentException("rewriteReadiness must not be blank");
        }
        if (proofDecisionStatus == null || proofDecisionStatus.isBlank()) {
            throw new IllegalArgumentException("proofDecisionStatus must not be blank");
        }
        if (rewritePolicyReadiness == null || rewritePolicyReadiness.isBlank()) {
            throw new IllegalArgumentException("rewritePolicyReadiness must not be blank");
        }
        if (dryRunReadiness == null || dryRunReadiness.isBlank()) {
            throw new IllegalArgumentException("dryRunReadiness must not be blank");
        }
        blockingReasons = List.copyOf(Objects.requireNonNull(blockingReasons, "blockingReasons"));
        remainingWork = List.copyOf(Objects.requireNonNull(remainingWork, "remainingWork"));
        noCandidateBuckets = List.copyOf(Objects.requireNonNull(noCandidateBuckets, "noCandidateBuckets"));
        noCandidateExamples = List.copyOf(Objects.requireNonNull(noCandidateExamples, "noCandidateExamples"));
        if (noCandidateBuckets.stream().anyMatch(bucket -> bucket == null || bucket.isBlank())) {
            throw new IllegalArgumentException("noCandidateBuckets must not contain blank entries");
        }
        if (noCandidateExamples.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("noCandidateExamples must not contain null entries");
        }
    }

    public static GpuIrAutoVectorizationReadinessSummaryReport from(
            GpuIrAutoVectorizationPreview preview,
            GpuIrAutoVectorizationRewriteDryRunReport dryRunReport,
            GpuIrAutoVectorizationResolvedRewriteOperations resolvedRewriteOperations
    ) {
        Objects.requireNonNull(preview, "preview");
        Objects.requireNonNull(dryRunReport, "dryRunReport");
        Objects.requireNonNull(resolvedRewriteOperations, "resolvedRewriteOperations");
        GpuIrAutoVectorizationProofBundle proofBundle = preview.proofBundle();
        GpuIrAutoVectorizationProofDecision proofDecision = proofBundle.decision();
        GpuIrAutoVectorizationRewritePolicy rewritePolicy = preview.rewritePolicy();
        boolean resolvedOperationsAvailable = resolvedRewriteOperations.hasOperations();
        List<String> blockingReasons = blockingReasons(
                preview,
                proofBundle,
                proofDecision,
                rewritePolicy,
                dryRunReport,
                resolvedOperationsAvailable
        );
        return new GpuIrAutoVectorizationReadinessSummaryReport(
                preview.methodName(),
                verdict(
                        preview,
                        proofBundle,
                        proofDecision,
                        rewritePolicy,
                        dryRunReport,
                        resolvedOperationsAvailable
                ),
                preview.rewriteCandidateCount(),
                preview.warningCount(),
                preview.rejectionCount(),
                preview.rewriteBlockedCandidateCount(),
                preview.rewritePlanGuardCount(),
                proofBundle.diagnosticCount(),
                proofBundle.unsafeProofSummaries().size(),
                dryRunReport.diagnostics().size(),
                resolvedRewriteOperations.operationCount(),
                preview.canApplyRewrite(),
                proofBundle.rewriteSafe(),
                proofDecision.allowRewrite(),
                rewritePolicy.canRewrite(),
                dryRunReport.successful(),
                resolvedOperationsAvailable,
                preview.rewriteReadiness().artifactValue(),
                proofDecision.status().artifactValue(),
                rewritePolicy.readiness().artifactValue(),
                dryRunReport.readiness().artifactValue(),
                blockingReasons,
                remainingWork(blockingReasons),
                preview.noCandidateBuckets(),
                preview.noCandidateExamples()
        );
    }

    public boolean readyForPrototypeRewrite() {
        return VERDICT_READY.equals(verdict);
    }

    public int blockingReasonCount() {
        return blockingReasons.size();
    }

    public int remainingWorkCount() {
        return remainingWork.size();
    }

    public Optional<String> firstBlockingReason() {
        return blockingReasons.stream().findFirst();
    }

    public Optional<String> firstRemainingWork() {
        return remainingWork.stream().findFirst();
    }

    public Map<String, Long> blockingReasonCounts() {
        return blockingReasons.stream()
                .collect(Collectors.groupingBy(
                        reason -> reason,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public GpuIrAutoVectorizationBlockerExplanation blockerExplanation() {
        return GpuIrAutoVectorizationBlockerExplanation.from(this);
    }

    public GpuIrAutoVectorizationNoCandidateBucketSummaryReport noCandidateBucketSummaryReport() {
        return GpuIrAutoVectorizationNoCandidateBucketSummaryReport.from(this);
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Verdict", verdict);
        values.put(prefix + "ReadyForPrototypeRewrite", Boolean.toString(readyForPrototypeRewrite()));
        values.put(prefix + "Candidates", Integer.toString(candidateCount));
        values.put(prefix + "Warnings", Integer.toString(warningCount));
        values.put(prefix + "Rejections", Integer.toString(rejectionCount));
        values.put(prefix + "RewriteBlockedCandidates", Integer.toString(rewriteBlockedCandidateCount));
        values.put(prefix + "RewritePlanGuards", Integer.toString(rewritePlanGuardCount));
        values.put(prefix + "ProofBundleDiagnostics", Integer.toString(proofBundleDiagnosticCount));
        values.put(prefix + "UnsafeProofs", Integer.toString(unsafeProofCount));
        values.put(prefix + "DryRunDiagnostics", Integer.toString(dryRunDiagnosticCount));
        values.put(prefix + "ResolvedRewriteOperations", Integer.toString(resolvedRewriteOperationCount));
        values.put(prefix + "PreviewCanApplyRewrite", Boolean.toString(previewCanApplyRewrite));
        values.put(prefix + "ProofBundleRewriteSafe", Boolean.toString(proofBundleRewriteSafe));
        values.put(prefix + "ProofDecisionAllowsRewrite", Boolean.toString(proofDecisionAllowsRewrite));
        values.put(prefix + "RewritePolicyCanRewrite", Boolean.toString(rewritePolicyCanRewrite));
        values.put(prefix + "DryRunSuccessful", Boolean.toString(dryRunSuccessful));
        values.put(prefix + "ResolvedRewriteOperationsAvailable", Boolean.toString(resolvedRewriteOperationsAvailable));
        values.put(prefix + "RewriteReadiness", rewriteReadiness);
        values.put(prefix + "ProofDecisionStatus", proofDecisionStatus);
        values.put(prefix + "RewritePolicyReadiness", rewritePolicyReadiness);
        values.put(prefix + "DryRunReadiness", dryRunReadiness);
        values.put(prefix + "BlockingReasons", listSummary(blockingReasons));
        values.put(prefix + "BlockingReasonCount", Integer.toString(blockingReasonCount()));
        values.put(prefix + "BlockingReasonCounts", mapSummary(blockingReasonCounts()));
        values.put(prefix + "RemainingWork", listSummary(remainingWork));
        values.put(prefix + "RemainingWorkCount", Integer.toString(remainingWorkCount()));
        firstBlockingReason().ifPresent(reason -> values.put(prefix + "FirstBlockingReason", reason));
        firstRemainingWork().ifPresent(work -> values.put(prefix + "FirstRemainingWork", work));
        values.putAll(noCandidateBucketSummaryReport().artifactFields(prefix + "NoCandidate"));
        values.putAll(blockerExplanation().artifactFields(prefix + "Blocker"));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorizationReadiness");
    }

    public String ciSummaryLine() {
        if (readyForPrototypeRewrite()) {
            return "auto-vectorization readiness ready";
        }
        return "auto-vectorization readiness " + verdict
                + " blockers=" + blockingReasonCount()
                + firstBlockingReason().map(reason -> " first=" + reason).orElse("");
    }

    public String summary() {
        return "auto-vectorization readiness method=" + methodName
                + " verdict=" + verdict
                + " readyForPrototypeRewrite=" + readyForPrototypeRewrite()
                + " candidates=" + candidateCount
                + " warnings=" + warningCount
                + " rejections=" + rejectionCount
                + " rewriteBlockedCandidates=" + rewriteBlockedCandidateCount
                + " rewritePlanGuards=" + rewritePlanGuardCount
                + " proofBundleDiagnostics=" + proofBundleDiagnosticCount
                + " unsafeProofs=" + unsafeProofCount
                + " dryRunSuccessful=" + dryRunSuccessful
                + " dryRunDiagnostics=" + dryRunDiagnosticCount
                + " resolvedRewriteOperations=" + resolvedRewriteOperationCount
                + " blockingReasons=" + listSummary(blockingReasons)
                + " remainingWork=" + listSummary(remainingWork)
                + " blockerExplanation={" + blockerExplanation().ciSummaryLine() + "}";
    }

    private static String verdict(
            GpuIrAutoVectorizationPreview preview,
            GpuIrAutoVectorizationProofBundle proofBundle,
            GpuIrAutoVectorizationProofDecision proofDecision,
            GpuIrAutoVectorizationRewritePolicy rewritePolicy,
            GpuIrAutoVectorizationRewriteDryRunReport dryRunReport,
            boolean resolvedOperationsAvailable
    ) {
        if (!preview.hasRewriteCandidates()) {
            return VERDICT_NO_CANDIDATES;
        }
        if (preview.hasRejections()) {
            return VERDICT_REJECTED;
        }
        if (preview.hasWarnings()) {
            return VERDICT_WARNINGS;
        }
        if (preview.hasRewritePlanGuardDiagnostics() || preview.hasRewriteBlockedCandidates()) {
            return VERDICT_GUARDS;
        }
        if (!proofBundle.rewriteSafe() || proofDecision.blocksRewrite()) {
            return VERDICT_PROOF_BLOCKED;
        }
        if (!rewritePolicy.canRewrite()) {
            return VERDICT_POLICY_BLOCKED;
        }
        if (!dryRunReport.successful()) {
            return VERDICT_DRY_RUN_BLOCKED;
        }
        if (!resolvedOperationsAvailable) {
            return VERDICT_RESOLUTION_MISSING;
        }
        return VERDICT_READY;
    }

    private static List<String> blockingReasons(
            GpuIrAutoVectorizationPreview preview,
            GpuIrAutoVectorizationProofBundle proofBundle,
            GpuIrAutoVectorizationProofDecision proofDecision,
            GpuIrAutoVectorizationRewritePolicy rewritePolicy,
            GpuIrAutoVectorizationRewriteDryRunReport dryRunReport,
            boolean resolvedOperationsAvailable
    ) {
        java.util.LinkedHashSet<String> reasons = new java.util.LinkedHashSet<>();
        if (!preview.hasRewriteCandidates()) {
            reasons.add("noRewriteCandidates");
        }
        if (preview.hasRejections()) {
            reasons.add("rejectionsPresent");
        }
        if (preview.hasWarnings()) {
            reasons.add("warningsPresent");
        }
        if (preview.hasRewritePlanGuardDiagnostics()) {
            reasons.add("rewritePlanGuardsPresent");
        }
        if (preview.hasRewriteBlockedCandidates()) {
            reasons.add("rewriteBlockedCandidatesPresent");
        }
        if (!proofBundle.rewriteSafe()) {
            reasons.add("proofBundleNotRewriteSafe");
        }
        if (proofDecision.blocksRewrite()) {
            reasons.add("proofDecisionBlocksRewrite");
        }
        if (!rewritePolicy.canRewrite()) {
            reasons.add("rewritePolicyBlocksRewrite");
        }
        if (!dryRunReport.successful()) {
            reasons.add("dryRunNotReady");
        }
        if (preview.hasRewriteCandidates() && dryRunReport.successful() && !resolvedOperationsAvailable) {
            reasons.add("resolvedRewriteOperationsMissing");
        }
        return List.copyOf(reasons);
    }

    private static List<String> remainingWork(List<String> blockingReasons) {
        java.util.LinkedHashSet<String> work = new java.util.LinkedHashSet<>();
        for (String reason : blockingReasons) {
            switch (reason) {
                case "noRewriteCandidates" -> work.add("collectRewriteCandidates");
                case "rejectionsPresent" -> work.add("clearAutoVectorizationRejections");
                case "warningsPresent" -> work.add("clearAutoVectorizationWarnings");
                case "rewritePlanGuardsPresent", "rewriteBlockedCandidatesPresent" -> work.add("clearRewritePlanGuards");
                case "proofBundleNotRewriteSafe", "proofDecisionBlocksRewrite" -> work.add("clearProofBundleBlockers");
                case "rewritePolicyBlocksRewrite" -> work.add("enableRewritePolicy");
                case "dryRunNotReady" -> work.add("fixRewriteDryRun");
                case "resolvedRewriteOperationsMissing" -> work.add("resolveRewriteOperations");
                default -> work.add("reviewAutoVectorizationBlocker:" + reason);
            }
        }
        return List.copyOf(work);
    }

    private static String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String listSummary(List<String> values) {
        return values.stream().collect(Collectors.joining(",", "[", "]"));
    }
}
