package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Explains why auto-vectorization readiness is blocked without enabling rewrites.
 *
 * <p>The readiness summary already computes stable blocker reasons. This wrapper turns those
 * reasons into CI-friendly families, next-work keys, and a short human hint that can be surfaced in
 * generated reports.</p>
 */
public record GpuIrAutoVectorizationBlockerExplanation(
        String methodName,
        String readinessVerdict,
        boolean readyForPrototypeRewrite,
        List<String> blockingReasons,
        List<String> remainingWork,
        String rewriteReadiness,
        String rewritePolicyReadiness,
        String dryRunReadiness
) {
    public GpuIrAutoVectorizationBlockerExplanation {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (readinessVerdict == null || readinessVerdict.isBlank()) {
            throw new IllegalArgumentException("readinessVerdict must not be blank");
        }
        blockingReasons = copyNonBlankList(blockingReasons, "blockingReasons");
        remainingWork = copyNonBlankList(remainingWork, "remainingWork");
        if (rewriteReadiness == null || rewriteReadiness.isBlank()) {
            throw new IllegalArgumentException("rewriteReadiness must not be blank");
        }
        if (rewritePolicyReadiness == null || rewritePolicyReadiness.isBlank()) {
            throw new IllegalArgumentException("rewritePolicyReadiness must not be blank");
        }
        if (dryRunReadiness == null || dryRunReadiness.isBlank()) {
            throw new IllegalArgumentException("dryRunReadiness must not be blank");
        }
    }

    public static GpuIrAutoVectorizationBlockerExplanation from(
            GpuIrAutoVectorizationReadinessSummaryReport readiness
    ) {
        Objects.requireNonNull(readiness, "readiness");
        return new GpuIrAutoVectorizationBlockerExplanation(
                readiness.methodName(),
                readiness.verdict(),
                readiness.readyForPrototypeRewrite(),
                readiness.blockingReasons(),
                readiness.remainingWork(),
                readiness.rewriteReadiness(),
                readiness.rewritePolicyReadiness(),
                readiness.dryRunReadiness()
        );
    }

    public boolean blocked() {
        return !readyForPrototypeRewrite;
    }

    public String verdict() {
        return blocked() ? "blocked" : "ready";
    }

    public String firstBlockerFamily() {
        return firstBlockingReason().map(GpuIrAutoVectorizationBlockerExplanation::familyForReason).orElse("none");
    }

    public String firstRemainingWork() {
        return remainingWork.stream().findFirst().orElse("none");
    }

    public Optional<String> firstBlockingReason() {
        return blockingReasons.stream().findFirst();
    }

    public List<String> blockerFamilies() {
        return blockingReasons.stream()
                .map(GpuIrAutoVectorizationBlockerExplanation::familyForReason)
                .distinct()
                .toList();
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorizationBlocker");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName);
        values.put(prefix + "Verdict", verdict());
        values.put(prefix + "ReadinessVerdict", readinessVerdict);
        values.put(prefix + "Blocked", Boolean.toString(blocked()));
        values.put(prefix + "ReadyForPrototypeRewrite", Boolean.toString(readyForPrototypeRewrite));
        values.put(prefix + "Reasons", String.join(",", blockingReasons));
        values.put(prefix + "ReasonCount", Integer.toString(blockingReasons.size()));
        values.put(prefix + "Families", String.join(",", blockerFamilies()));
        values.put(prefix + "FirstReason", firstBlockingReason().orElse("none"));
        values.put(prefix + "FirstFamily", firstBlockerFamily());
        values.put(prefix + "RemainingWork", String.join(",", remainingWork));
        values.put(prefix + "RemainingWorkCount", Integer.toString(remainingWork.size()));
        values.put(prefix + "FirstRemainingWork", firstRemainingWork());
        values.put(prefix + "RewriteReadiness", rewriteReadiness);
        values.put(prefix + "RewritePolicyReadiness", rewritePolicyReadiness);
        values.put(prefix + "DryRunReadiness", dryRunReadiness);
        values.put(prefix + "FirstHint", firstBlockingReason().map(this::hintFor).orElse("auto-vectorization is ready for prototype rewrite review"));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "auto-vectorization blocker method=" + methodName
                + " verdict=" + verdict()
                + " readinessVerdict=" + readinessVerdict
                + " firstFamily=" + firstBlockerFamily()
                + " firstRemainingWork=" + firstRemainingWork();
    }

    public String summary() {
        return ciSummaryLine()
                + " reasons=" + blockingReasons
                + " remainingWork=" + remainingWork
                + firstBlockingReason().map(reason -> " hint=" + hintFor(reason)).orElse("");
    }

    private String hintFor(String reason) {
        return switch (reason) {
            case "noRewriteCandidates" -> "Auto-vectorization did not find a fixed-width rewrite candidate; add or prove a supported loop/vector shape before rewrite review.";
            case "rejectionsPresent" -> "Auto-vectorization found candidates but rejected at least one; clear rejection diagnostics before rewrite review.";
            case "warningsPresent" -> "Auto-vectorization has warnings; clear warnings so proof layers can be trusted.";
            case "rewritePlanGuardsPresent", "rewriteBlockedCandidatesPresent" -> "Rewrite planning guards are present; resolve guard diagnostics before mutation can be considered.";
            case "proofBundleNotRewriteSafe", "proofDecisionBlocksRewrite" -> "Proof bundle is not rewrite-safe; complete proof layers before rewrite review.";
            case "rewritePolicyBlocksRewrite" -> "Rewrite policy is still blocked; keep production mutation disabled until policy and evidence gates are clear.";
            case "dryRunNotReady" -> "Rewrite dry-run is not ready; fix dry-run diagnostics before resolving operations.";
            case "resolvedRewriteOperationsMissing" -> "Resolved rewrite operations are missing; resolve insertions/replacements before prototype rewrite.";
            default -> "Review auto-vectorization blocker `" + reason + "` before rewrite review.";
        };
    }

    private static String familyForReason(String reason) {
        return switch (reason) {
            case "noRewriteCandidates" -> "candidateDiscovery.noRewriteCandidates";
            case "rejectionsPresent" -> "diagnostic.rejectionsPresent";
            case "warningsPresent" -> "diagnostic.warningsPresent";
            case "rewritePlanGuardsPresent" -> "rewritePlan.guardsPresent";
            case "rewriteBlockedCandidatesPresent" -> "rewritePlan.blockedCandidatesPresent";
            case "proofBundleNotRewriteSafe" -> "proof.proofBundleNotRewriteSafe";
            case "proofDecisionBlocksRewrite" -> "proof.proofDecisionBlocksRewrite";
            case "rewritePolicyBlocksRewrite" -> "rewritePolicy.blocksRewrite";
            case "dryRunNotReady" -> "dryRun.notReady";
            case "resolvedRewriteOperationsMissing" -> "rewriteResolution.operationsMissing";
            default -> "unknown." + reason;
        };
    }

    private static List<String> copyNonBlankList(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(name + " must not contain blank entries");
        }
        return values.stream()
                .distinct()
                .collect(Collectors.toUnmodifiableList());
    }
}
