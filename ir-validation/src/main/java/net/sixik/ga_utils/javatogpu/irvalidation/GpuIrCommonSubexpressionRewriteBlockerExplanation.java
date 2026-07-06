package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Explains why the conservative CSE rewrite policy is still blocked.
 *
 * <p>This artifact is intentionally read-only. It turns skipped CSE candidates into stable
 * blocker families and next-work keys without enabling mutation.</p>
 */
public record GpuIrCommonSubexpressionRewriteBlockerExplanation(
        String methodName,
        GpuIrCommonSubexpressionRewriteReadiness readiness,
        int blockingCandidateCount,
        Optional<GpuIrCommonSubexpressionSkippedDiagnostic> firstBlockingDiagnostic,
        List<String> blockerFamilies,
        List<String> remainingWork
) {
    public GpuIrCommonSubexpressionRewriteBlockerExplanation {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        readiness = Objects.requireNonNull(readiness, "readiness");
        firstBlockingDiagnostic = Objects.requireNonNull(firstBlockingDiagnostic, "firstBlockingDiagnostic");
        if (blockingCandidateCount < 0) {
            throw new IllegalArgumentException("blockingCandidateCount must be non-negative");
        }
        blockerFamilies = copyNonBlankList(blockerFamilies, "blockerFamilies");
        remainingWork = copyNonBlankList(remainingWork, "remainingWork");
        if (blockingCandidateCount == 0 && firstBlockingDiagnostic.isPresent()) {
            throw new IllegalArgumentException("first blocker cannot be present without blocked candidates");
        }
    }

    public static GpuIrCommonSubexpressionRewriteBlockerExplanation from(
            GpuIrCommonSubexpressionRewritePolicy policy
    ) {
        Objects.requireNonNull(policy, "policy");
        Optional<GpuIrCommonSubexpressionSkippedDiagnostic> firstDiagnostic =
                policy.firstBlockingSkippedCandidate().map(GpuIrCommonSubexpressionSkippedCandidate::diagnostic);
        return new GpuIrCommonSubexpressionRewriteBlockerExplanation(
                policy.methodName(),
                policy.readiness(),
                policy.blockingSkippedCandidateCount(),
                firstDiagnostic,
                blockerFamilies(firstDiagnostic),
                remainingWork(firstDiagnostic)
        );
    }

    public boolean blocked() {
        return blockingCandidateCount > 0;
    }

    public String verdict() {
        return blocked() ? "blocked" : readiness.artifactValue();
    }

    public String firstBlockerFamily() {
        return blockerFamilies.stream().findFirst().orElse("none");
    }

    public String firstRemainingWork() {
        return remainingWork.stream().findFirst().orElse("none");
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseRewriteBlocker");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName);
        values.put(prefix + "Verdict", verdict());
        values.put(prefix + "Blocked", Boolean.toString(blocked()));
        values.put(prefix + "Readiness", readiness.artifactValue());
        values.put(prefix + "BlockingCandidates", Integer.toString(blockingCandidateCount));
        values.put(prefix + "Families", String.join(",", blockerFamilies));
        values.put(prefix + "FirstFamily", firstBlockerFamily());
        values.put(prefix + "RemainingWork", String.join(",", remainingWork));
        values.put(prefix + "FirstRemainingWork", firstRemainingWork());
        firstBlockingDiagnostic.ifPresent(diagnostic -> {
            values.put(prefix + "FirstReason", diagnostic.reason().name());
            values.put(prefix + "FirstKind", diagnostic.kind().name());
            values.put(prefix + "FirstScope", diagnostic.scope().name());
            values.put(prefix + "FirstDominanceStatus", diagnostic.dominanceStatus().artifactValue());
            values.put(prefix + "FirstFingerprint", diagnostic.fingerprint());
            values.put(prefix + "FirstLocations", String.join(",", diagnostic.locations()));
            values.put(prefix + "FirstSummary", diagnostic.summary());
            values.put(prefix + "FirstHint", hintFor(diagnostic));
        });
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "CSE rewrite blocker method=" + methodName
                + " verdict=" + verdict()
                + " blocked=" + blocked()
                + " families=" + blockerFamilies
                + " firstFamily=" + firstBlockerFamily()
                + " firstRemainingWork=" + firstRemainingWork();
    }

    public String summary() {
        return ciSummaryLine()
                + firstBlockingDiagnostic
                .map(diagnostic -> " firstBlocker={" + diagnostic.summary() + "} hint=" + hintFor(diagnostic))
                .orElse("");
    }

    private static List<String> blockerFamilies(Optional<GpuIrCommonSubexpressionSkippedDiagnostic> diagnostic) {
        return diagnostic
                .map(value -> List.of(
                        "skipReason." + value.reason().name(),
                        "dominance." + value.dominanceStatus().artifactValue(),
                        "kind." + value.kind().name(),
                        "scope." + value.scope().name()
                ))
                .orElseGet(List::of);
    }

    private static List<String> remainingWork(Optional<GpuIrCommonSubexpressionSkippedDiagnostic> diagnostic) {
        return diagnostic
                .map(value -> List.of(
                        workForReason(value.reason()),
                        workForDominance(value.dominanceStatus()),
                        "addRuntimeEquivalenceEvidenceBeforeMutation"
                ))
                .orElseGet(List::of);
    }

    private static String workForReason(GpuIrCommonSubexpressionSkipReason reason) {
        return switch (reason) {
            case NOT_LOCAL_REUSE -> "proveLocalReuseOrKeepExpressionInline";
            case CONTROL_FLOW_BOUNDARY -> "splitControlFlowRegionsBeforeCse";
            case MUTATED_BETWEEN_OCCURRENCES -> "proveOperandStabilityBetweenOccurrences";
            case NO_DOMINATING_FIRST_OCCURRENCE -> "proveDominatingAnchorForAllReplacements";
            case COVERED_BY_PARENT_REWRITE -> "preferParentRewriteOrSuppressNestedCandidate";
        };
    }

    private static String workForDominance(GpuIrCommonSubexpressionDominanceStatus status) {
        return switch (status) {
            case TOP_LEVEL_DOWNSTREAM_REPLACEMENTS -> "verifyTopLevelRewriteAnchor";
            case LOCAL_EXPRESSION_DOWNSTREAM_REPLACEMENTS -> "verifyLocalExpressionRewriteAnchor";
            case MISSING_TOP_LEVEL_ANCHOR -> "recoverTopLevelStatementAnchor";
            case LOCATIONS_MOVE_BACKWARDS -> "preserveOriginalEvaluationOrder";
            case REQUIRES_LOCAL_EXPRESSION_DOMINANCE -> "addLocalExpressionDominanceProof";
        };
    }

    private static String hintFor(GpuIrCommonSubexpressionSkippedDiagnostic diagnostic) {
        return "CSE saw repeated " + diagnostic.kind()
                + " expression but needs " + workForReason(diagnostic.reason())
                + " and " + workForDominance(diagnostic.dominanceStatus())
                + " before a production rewrite can be enabled.";
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
