package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only aggregate of typed numeric blockers for literal arithmetic canonicalization.
 *
 * <p>This report gives CI one compact view of the remaining typed numeric proof families before
 * literal arithmetic preview keys can be considered for any future fingerprint promotion work.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport(
        String methodName,
        int literalProofBlockedCandidateCount,
        int numericSemanticsBlockedCandidateCount,
        Map<String, Long> literalProofBlockerCounts,
        Map<String, Long> numericSemanticsBlockerCounts
) {
    public GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (literalProofBlockedCandidateCount < 0) {
            throw new IllegalArgumentException("literalProofBlockedCandidateCount must be non-negative");
        }
        if (numericSemanticsBlockedCandidateCount < 0) {
            throw new IllegalArgumentException("numericSemanticsBlockedCandidateCount must be non-negative");
        }
        literalProofBlockerCounts = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(literalProofBlockerCounts, "literalProofBlockerCounts")));
        numericSemanticsBlockerCounts = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(numericSemanticsBlockerCounts, "numericSemanticsBlockerCounts")));
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport from(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport literalProofReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport
    ) {
        Objects.requireNonNull(literalProofReport, "literalProofReport");
        Objects.requireNonNull(numericSemanticsProofReport, "numericSemanticsProofReport");
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport(
                literalProofReport.methodName(),
                literalProofReport.blockedCandidateCount(),
                numericSemanticsProofReport.blockedCandidateCount(),
                literalProofReport.blockedReasonCounts(),
                numericSemanticsProofReport.blockedReasonCounts()
        );
    }

    public int totalBlockedCandidateCount() {
        return literalProofBlockedCandidateCount + numericSemanticsBlockedCandidateCount;
    }

    public boolean hasBlockers() {
        return totalBlockedCandidateCount() > 0;
    }

    public String readiness() {
        return hasBlockers() ? "blocked" : "clear";
    }

    public Map<String, Long> combinedBlockerCounts() {
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        literalProofBlockerCounts.forEach((reason, count) -> counts.merge(reason, count, Long::sum));
        numericSemanticsBlockerCounts.forEach((reason, count) -> counts.merge(reason, count, Long::sum));
        return Collections.unmodifiableMap(counts);
    }

    public int uniqueBlockerFamilyCount() {
        return combinedBlockerCounts().size();
    }

    public Optional<String> firstBlockerFamily() {
        return combinedBlockerCounts().keySet().stream().findFirst();
    }

    public Optional<String> firstBlockerExplanation() {
        return firstBlockerFamily().map(GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport::blockerExplanation);
    }

    public String ciSummaryLine() {
        if (!hasBlockers()) {
            return "literal typed numeric blockers clear";
        }
        return "literal typed numeric blockers=" + totalBlockedCandidateCount()
                + " families=" + uniqueBlockerFamilyCount()
                + " first=" + firstBlockerFamily().orElse("unknown")
                + "; explanation=" + firstBlockerExplanation().orElse("unknown typed numeric blocker");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Readiness", readiness());
        values.put(prefix + "HasBlockers", Boolean.toString(hasBlockers()));
        values.put(prefix + "TotalBlockedCandidates", Integer.toString(totalBlockedCandidateCount()));
        values.put(prefix + "LiteralProofBlockedCandidates", Integer.toString(literalProofBlockedCandidateCount));
        values.put(prefix + "NumericSemanticsBlockedCandidates", Integer.toString(numericSemanticsBlockedCandidateCount));
        values.put(prefix + "UniqueBlockerFamilies", Integer.toString(uniqueBlockerFamilyCount()));
        values.put(prefix + "CombinedBlockerCounts", mapSummary(combinedBlockerCounts()));
        values.put(prefix + "LiteralProofBlockerCounts", mapSummary(literalProofBlockerCounts));
        values.put(prefix + "NumericSemanticsBlockerCounts", mapSummary(numericSemanticsBlockerCounts));
        combinedBlockerCounts().forEach((reason, count) -> values.put(prefix + "Family." + reason, Long.toString(count)));
        firstBlockerFamily().ifPresent(reason -> values.put(prefix + "FirstBlockerFamily", reason));
        firstBlockerExplanation().ifPresent(explanation -> values.put(prefix + "FirstBlockerExplanation", explanation));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseSimpleArithmeticLiteralTypedNumericBlockers");
    }

    public String summary() {
        return "CSE simple arithmetic literal typed numeric blockers method=" + methodName
                + " readiness=" + readiness()
                + " totalBlockedCandidates=" + totalBlockedCandidateCount()
                + " literalProofBlockedCandidates=" + literalProofBlockedCandidateCount
                + " numericSemanticsBlockedCandidates=" + numericSemanticsBlockedCandidateCount
                + " families=" + mapSummary(combinedBlockerCounts())
                + firstBlockerFamily().map(reason -> " firstBlocker=" + reason).orElse("");
    }

    private static String blockerExplanation(String reason) {
        return switch (reason) {
            case "castRequiresExplicitNumericProof" -> "casts require explicit narrowing, widening, sign, and precision proof before canonicalization";
            case "longLiteralOverflowSemanticsRequireProof", "longOperandOverflowSemanticsRequireProof" -> "long arithmetic requires explicit overflow and backend-equivalence proof";
            case "floatingLiteralSemanticsRequireProof", "floatingOperandSemanticsRequireProof" -> "floating arithmetic requires precision, NaN, signed-zero, and backend-equivalence proof";
            case "nonIntOperandSemanticsRequireProof" -> "non-int operands are outside the current int-only literal proof boundary";
            case "unknownLiteralSemanticsRequireProof" -> "literal spelling is outside the classified int/long/floating proof boundary";
            case "unsupportedOperator" -> "only associative literal + and * are inside the current proof boundary";
            case "missingLiteralSource" -> "literal canonicalization proof requires at least one literal source";
            case "unsupportedIntLiteralSemantics", "unsupportedLiteralArithmetic" -> "literal arithmetic shape is not covered by the current typed proof boundary";
            default -> "typed numeric blocker requires explicit proof before canonicalization: " + reason;
        };
    }

    private static String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }
}
