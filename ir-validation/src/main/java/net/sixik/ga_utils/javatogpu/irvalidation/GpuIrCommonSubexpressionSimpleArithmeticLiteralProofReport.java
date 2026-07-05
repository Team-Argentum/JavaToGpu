package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only typed proof surface for nested simple arithmetic that contains integer literals.
 *
 * <p>This report deliberately does not enable CSE rewrites. It only separates integer-literal
 * arithmetic shapes that are ready for a future typed proof from shapes that still contain casts,
 * non-int operands, or non-int literals.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport(
        String methodName,
        List<SafeCandidate> safeCandidates,
        List<BlockedCandidate> blockedCandidates
) {
    public GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        safeCandidates = List.copyOf(Objects.requireNonNull(safeCandidates, "safeCandidates"));
        blockedCandidates = List.copyOf(Objects.requireNonNull(blockedCandidates, "blockedCandidates"));
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport empty(String methodName) {
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport(methodName, List.of(), List.of());
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport from(
            GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport boundaryReport
    ) {
        Objects.requireNonNull(boundaryReport, "boundaryReport");
        List<SafeCandidate> safeCandidates = new ArrayList<>();
        List<BlockedCandidate> blockedCandidates = new ArrayList<>();
        for (GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport.BlockedCandidate candidate : boundaryReport.blockedCandidates()) {
            if (isSafeIntegerLiteralCandidate(candidate)) {
                safeCandidates.add(new SafeCandidate(
                        candidate.location(),
                        candidate.operator(),
                        candidate.operandTypes(),
                        candidate.literalSources()
                ));
            } else {
                blockedCandidates.add(new BlockedCandidate(
                        candidate.location(),
                        candidate.operator(),
                        blockedReason(candidate),
                        candidate.operandTypes(),
                        candidate.literalSources(),
                        candidate.castTargets(),
                        candidate.castSourceTypes()
                ));
            }
        }
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport(
                boundaryReport.methodName(),
                safeCandidates,
                blockedCandidates
        );
    }

    public int candidateCount() {
        return safeCandidateCount() + blockedCandidateCount();
    }

    public int safeCandidateCount() {
        return safeCandidates.size();
    }

    public int blockedCandidateCount() {
        return blockedCandidates.size();
    }

    public boolean hasSafeCandidates() {
        return !safeCandidates.isEmpty();
    }

    public Optional<SafeCandidate> firstSafeCandidate() {
        return safeCandidates.stream().findFirst();
    }

    public Optional<BlockedCandidate> firstBlockedCandidate() {
        return blockedCandidates.stream().findFirst();
    }

    public Map<String, Long> blockedReasonCounts() {
        return blockedCandidates.stream()
                .collect(Collectors.groupingBy(
                        BlockedCandidate::reason,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, Long> safeOperatorTypeCounts() {
        return safeCandidates.stream()
                .collect(Collectors.groupingBy(
                        SafeCandidate::operatorTypeKey,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, Long> blockedOperatorTypeCounts() {
        return blockedCandidates.stream()
                .collect(Collectors.groupingBy(
                        BlockedCandidate::operatorTypeKey,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public String proofBoundary() {
        return "safeIntLiteralNestedArithmetic";
    }

    public String blockedBoundary() {
        return "nonIntOrCastLiteralArithmetic";
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Candidates", Integer.toString(candidateCount()));
        values.put(prefix + "SafeCandidates", Integer.toString(safeCandidateCount()));
        values.put(prefix + "BlockedCandidates", Integer.toString(blockedCandidateCount()));
        values.put(prefix + "HasSafeCandidates", Boolean.toString(hasSafeCandidates()));
        values.put(prefix + "ProofBoundary", proofBoundary());
        values.put(prefix + "BlockedBoundary", blockedBoundary());
        values.put(prefix + "BlockedReasonCounts", mapSummary(blockedReasonCounts()));
        values.put(prefix + "SafeOperatorTypeCounts", mapSummary(safeOperatorTypeCounts()));
        values.put(prefix + "BlockedOperatorTypeCounts", mapSummary(blockedOperatorTypeCounts()));
        blockedReasonCounts().forEach((reason, count) ->
                values.put(prefix + "BlockedReason." + reason, Long.toString(count)));
        firstSafeCandidate().ifPresent(candidate -> {
            values.put(prefix + "FirstSafeLocation", candidate.location());
            values.put(prefix + "FirstSafeOperator", candidate.operator());
            values.put(prefix + "FirstSafeOperatorTypeKey", candidate.operatorTypeKey());
            values.put(prefix + "FirstSafeOperandTypes", listSummary(candidate.operandTypes()));
            values.put(prefix + "FirstSafeLiteralSources", listSummary(candidate.literalSources()));
            values.put(prefix + "FirstSafeSummary", candidate.summary());
        });
        firstBlockedCandidate().ifPresent(candidate -> {
            values.put(prefix + "FirstBlockedLocation", candidate.location());
            values.put(prefix + "FirstBlockedOperator", candidate.operator());
            values.put(prefix + "FirstBlockedReason", candidate.reason());
            values.put(prefix + "FirstBlockedExplanation", blockedReasonExplanation(candidate.reason()));
            values.put(prefix + "FirstBlockedOperatorTypeKey", candidate.operatorTypeKey());
            values.put(prefix + "FirstBlockedOperandTypes", listSummary(candidate.operandTypes()));
            values.put(prefix + "FirstBlockedLiteralSources", listSummary(candidate.literalSources()));
            values.put(prefix + "FirstBlockedCastTargets", listSummary(candidate.castTargets()));
            values.put(prefix + "FirstBlockedCastSourceTypes", listSummary(candidate.castSourceTypes()));
            values.put(prefix + "FirstBlockedSummary", candidate.summary());
        });
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseSimpleArithmeticLiteralProof");
    }

    public String summary() {
        return "CSE simple arithmetic literal proof method=" + methodName
                + " candidates=" + candidateCount()
                + " safeCandidates=" + safeCandidateCount()
                + " blockedCandidates=" + blockedCandidateCount()
                + " hasSafeCandidates=" + hasSafeCandidates()
                + " proofBoundary=" + proofBoundary()
                + " blockedBoundary=" + blockedBoundary()
                + " blockedReasons=" + blockedReasonCounts()
                + " safeOperatorTypes=" + safeOperatorTypeCounts()
                + " blockedOperatorTypes=" + blockedOperatorTypeCounts()
                + firstSafeCandidate()
                .map(candidate -> " firstSafe={" + candidate.summary() + "}")
                .orElse("")
                + firstBlockedCandidate()
                .map(candidate -> " firstBlocked={" + candidate.summary() + "}")
                .orElse("");
    }

    public record SafeCandidate(
            String location,
            String operator,
            List<String> operandTypes,
            List<String> literalSources
    ) {
        public SafeCandidate {
            if (location == null || location.isBlank()) {
                throw new IllegalArgumentException("location must not be blank");
            }
            if (operator == null || operator.isBlank()) {
                throw new IllegalArgumentException("operator must not be blank");
            }
            operandTypes = List.copyOf(Objects.requireNonNull(operandTypes, "operandTypes"));
            literalSources = List.copyOf(Objects.requireNonNull(literalSources, "literalSources"));
        }

        public String summary() {
            return "location=" + location
                    + " operator=" + operator
                    + " operatorTypeKey=" + operatorTypeKey()
                    + " operandTypes=" + listSummary(operandTypes)
                    + " literalSources=" + listSummary(literalSources);
        }

        public String operatorTypeKey() {
            return operatorArtifactValue(operator) + ":" + listSummary(operandTypes);
        }
    }

    public record BlockedCandidate(
            String location,
            String operator,
            String reason,
            List<String> operandTypes,
            List<String> literalSources,
            List<String> castTargets,
            List<String> castSourceTypes
    ) {
        public BlockedCandidate {
            if (location == null || location.isBlank()) {
                throw new IllegalArgumentException("location must not be blank");
            }
            if (operator == null || operator.isBlank()) {
                throw new IllegalArgumentException("operator must not be blank");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
            operandTypes = List.copyOf(Objects.requireNonNull(operandTypes, "operandTypes"));
            literalSources = List.copyOf(Objects.requireNonNull(literalSources, "literalSources"));
            castTargets = List.copyOf(Objects.requireNonNull(castTargets, "castTargets"));
            castSourceTypes = List.copyOf(Objects.requireNonNull(castSourceTypes, "castSourceTypes"));
        }

        public String summary() {
            return "location=" + location
                    + " operator=" + operator
                    + " reason=" + reason
                    + " operatorTypeKey=" + operatorTypeKey()
                    + " operandTypes=" + listSummary(operandTypes)
                    + " literalSources=" + listSummary(literalSources)
                    + " castTargets=" + listSummary(castTargets)
                    + " castSourceTypes=" + listSummary(castSourceTypes);
        }

        public String operatorTypeKey() {
            return operatorArtifactValue(operator) + ":" + listSummary(operandTypes);
        }
    }

    private static boolean isSafeIntegerLiteralCandidate(
            GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport.BlockedCandidate candidate
    ) {
        return "literalOperand".equals(candidate.reason())
                && !candidate.literalSources().isEmpty()
                && candidate.castTargets().isEmpty()
                && candidate.operandTypes().stream().allMatch("int"::equals)
                && candidate.literalSources().stream().allMatch(GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport::isIntegerLiteral);
    }

    private static String blockedReason(
            GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport.BlockedCandidate candidate
    ) {
        if (!candidate.castTargets().isEmpty() || candidate.reason().contains("Cast")) {
            return "castRequiresExplicitNumericProof";
        }
        if (candidate.literalSources().stream().anyMatch(GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport::isLongLiteral)) {
            return "longLiteralOverflowSemanticsRequireProof";
        }
        if (candidate.literalSources().stream().anyMatch(GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport::isFloatingLiteral)) {
            return "floatingLiteralSemanticsRequireProof";
        }
        if (candidate.operandTypes().stream().anyMatch(type -> !"int".equals(type))) {
            return "nonIntOperandSemanticsRequireProof";
        }
        if (candidate.literalSources().stream().anyMatch(source -> !isIntegerLiteral(source))) {
            return "unknownLiteralSemanticsRequireProof";
        }
        return "unsupportedLiteralArithmetic";
    }

    private static String blockedReasonExplanation(String reason) {
        return switch (reason) {
            case "castRequiresExplicitNumericProof" -> "casts can change narrowing, widening, sign, or precision semantics and require explicit proof before canonicalization";
            case "longLiteralOverflowSemanticsRequireProof" -> "long literal arithmetic has wider overflow semantics than the current int-only proof boundary";
            case "floatingLiteralSemanticsRequireProof" -> "floating-point literals require backend and precision proof before canonicalization";
            case "nonIntOperandSemanticsRequireProof" -> "non-int operands are outside the current Java int literal plus/times proof boundary";
            case "unknownLiteralSemanticsRequireProof" -> "literal spelling is outside the currently classified int/long/floating proof boundary";
            case "unsupportedLiteralArithmetic" -> "literal arithmetic shape is not covered by the current proof boundary";
            default -> "literal arithmetic blocked until typed numeric semantics are explicitly proven: " + reason;
        };
    }

    private static boolean isLongLiteral(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.replace("_", "").trim().toLowerCase(Locale.ROOT).endsWith("l");
    }

    private static boolean isFloatingLiteral(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        String normalized = sourceText.replace("_", "").trim().toLowerCase(Locale.ROOT);
        return normalized.endsWith("f")
                || normalized.endsWith("d")
                || normalized.contains(".")
                || normalized.contains("e")
                || normalized.contains("p");
    }

    private static boolean isIntegerLiteral(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        String normalized = sourceText.replace("_", "").trim().toLowerCase(Locale.ROOT);
        return !normalized.isBlank()
                && !normalized.endsWith("l")
                && !normalized.endsWith("f")
                && !normalized.endsWith("d")
                && !normalized.contains(".")
                && !normalized.contains("e")
                && !normalized.contains("p");
    }

    private static String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String listSummary(List<String> values) {
        return String.join(",", values);
    }

    private static String operatorArtifactValue(String operator) {
        return switch (operator) {
            case "+" -> "plus";
            case "*" -> "times";
            default -> operator == null || operator.isBlank() ? "unknown" : operator;
        };
    }
}
