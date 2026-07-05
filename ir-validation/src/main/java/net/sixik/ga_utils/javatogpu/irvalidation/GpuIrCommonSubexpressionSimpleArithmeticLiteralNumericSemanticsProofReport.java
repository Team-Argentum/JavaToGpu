package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only proof that literal canonicalization preview candidates stay inside Java int arithmetic.
 *
 * <p>This proof is intentionally narrow: it only accepts preview candidates that already came from
 * safe integer-literal proof candidates and still have all-{@code int} operand types for {@code +}
 * or {@code *}. It does not prove runtime equivalence or enable fingerprint promotion.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport(
        String methodName,
        List<ProvenCandidate> provenCandidates,
        List<BlockedCandidate> blockedCandidates
) {
    public GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        provenCandidates = List.copyOf(Objects.requireNonNull(provenCandidates, "provenCandidates"));
        blockedCandidates = List.copyOf(Objects.requireNonNull(blockedCandidates, "blockedCandidates"));
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport empty(String methodName) {
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport(methodName, List.of(), List.of());
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport from(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport report
    ) {
        Objects.requireNonNull(report, "report");
        List<ProvenCandidate> provenCandidates = report.candidates().stream()
                .filter(GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport::hasIntArithmeticSemantics)
                .map(ProvenCandidate::from)
                .toList();
        List<BlockedCandidate> blockedCandidates = report.candidates().stream()
                .filter(candidate -> !hasIntArithmeticSemantics(candidate))
                .map(BlockedCandidate::from)
                .toList();
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport(
                report.methodName(),
                provenCandidates,
                blockedCandidates
        );
    }

    public int candidateCount() {
        return provenCandidateCount() + blockedCandidateCount();
    }

    public int provenCandidateCount() {
        return provenCandidates.size();
    }

    public int blockedCandidateCount() {
        return blockedCandidates.size();
    }

    public boolean fullyProven() {
        return candidateCount() > 0 && blockedCandidates.isEmpty();
    }

    public String readiness() {
        if (candidateCount() == 0) {
            return "none";
        }
        return fullyProven() ? "proven" : "blocked";
    }

    public String proofBoundary() {
        return "javaIntLiteralPlusTimesSemantics";
    }

    public String blockedBoundary() {
        return "nonIntOrUnsupportedLiteralOperator";
    }

    public Optional<ProvenCandidate> firstProvenCandidate() {
        if (provenCandidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(provenCandidates.get(0));
    }

    public Optional<BlockedCandidate> firstBlockedCandidate() {
        if (blockedCandidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(blockedCandidates.get(0));
    }

    public Map<String, Long> provenOperatorTypeCounts() {
        return provenCandidates.stream()
                .collect(Collectors.groupingBy(
                        ProvenCandidate::operatorTypeKey,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, Long> blockedReasonCounts() {
        return blockedCandidates.stream()
                .collect(Collectors.groupingBy(
                        BlockedCandidate::reason,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Candidates", Integer.toString(candidateCount()));
        values.put(prefix + "ProvenCandidates", Integer.toString(provenCandidateCount()));
        values.put(prefix + "BlockedCandidates", Integer.toString(blockedCandidateCount()));
        values.put(prefix + "FullyProven", Boolean.toString(fullyProven()));
        values.put(prefix + "Readiness", readiness());
        values.put(prefix + "ProofBoundary", proofBoundary());
        values.put(prefix + "BlockedBoundary", blockedBoundary());
        values.put(prefix + "ProvenOperatorTypeCounts", mapSummary(provenOperatorTypeCounts()));
        values.put(prefix + "BlockedReasonCounts", mapSummary(blockedReasonCounts()));
        firstProvenCandidate().ifPresent(candidate -> {
            values.put(prefix + "FirstProvenLocation", candidate.location());
            values.put(prefix + "FirstProvenOperator", candidate.operator());
            values.put(prefix + "FirstProvenOperatorTypeKey", candidate.operatorTypeKey());
            values.put(prefix + "FirstProvenCanonicalKey", candidate.canonicalKey());
            values.put(prefix + "FirstProvenLiteralSources", listSummary(candidate.literalSources()));
            values.put(prefix + "FirstProvenSummary", candidate.summary());
        });
        firstBlockedCandidate().ifPresent(candidate -> {
            values.put(prefix + "FirstBlockedLocation", candidate.location());
            values.put(prefix + "FirstBlockedOperator", candidate.operator());
            values.put(prefix + "FirstBlockedReason", candidate.reason());
            values.put(prefix + "FirstBlockedExplanation", blockedReasonExplanation(candidate.reason()));
            values.put(prefix + "FirstBlockedOperatorTypeKey", candidate.operatorTypeKey());
            values.put(prefix + "FirstBlockedCanonicalKey", candidate.canonicalKey());
            values.put(prefix + "FirstBlockedSummary", candidate.summary());
        });
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseSimpleArithmeticLiteralNumericSemanticsProof");
    }

    public String summary() {
        return "CSE simple arithmetic literal numeric semantics proof method=" + methodName
                + " candidates=" + candidateCount()
                + " provenCandidates=" + provenCandidateCount()
                + " blockedCandidates=" + blockedCandidateCount()
                + " fullyProven=" + fullyProven()
                + " readiness=" + readiness()
                + " proofBoundary=" + proofBoundary()
                + " blockedBoundary=" + blockedBoundary()
                + " provenOperatorTypes=" + provenOperatorTypeCounts()
                + " blockedReasons=" + blockedReasonCounts()
                + firstProvenCandidate()
                .map(candidate -> " firstProven={" + candidate.summary() + "}")
                .orElse("")
                + firstBlockedCandidate()
                .map(candidate -> " firstBlocked={" + candidate.summary() + "}")
                .orElse("");
    }

    public record ProvenCandidate(
            String location,
            String operator,
            String operatorTypeKey,
            String canonicalKey,
            List<String> operandTypes,
            List<String> literalSources
    ) {
        public ProvenCandidate {
            if (location == null || location.isBlank()) {
                throw new IllegalArgumentException("location must not be blank");
            }
            if (operator == null || operator.isBlank()) {
                throw new IllegalArgumentException("operator must not be blank");
            }
            if (operatorTypeKey == null || operatorTypeKey.isBlank()) {
                throw new IllegalArgumentException("operatorTypeKey must not be blank");
            }
            if (canonicalKey == null || canonicalKey.isBlank()) {
                throw new IllegalArgumentException("canonicalKey must not be blank");
            }
            operandTypes = List.copyOf(Objects.requireNonNull(operandTypes, "operandTypes"));
            literalSources = List.copyOf(Objects.requireNonNull(literalSources, "literalSources"));
        }

        private static ProvenCandidate from(GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.Candidate candidate) {
            return new ProvenCandidate(
                    candidate.location(),
                    candidate.operator(),
                    candidate.operatorTypeKey(),
                    candidate.canonicalKey(),
                    candidate.operandTypes(),
                    candidate.literalSources()
            );
        }

        public String summary() {
            return "location=" + location
                    + " operator=" + operator
                    + " operatorTypeKey=" + operatorTypeKey
                    + " canonicalKey=" + canonicalKey
                    + " operandTypes=" + listSummary(operandTypes)
                    + " literalSources=" + listSummary(literalSources);
        }
    }

    public record BlockedCandidate(
            String location,
            String operator,
            String reason,
            String operatorTypeKey,
            String canonicalKey,
            List<String> operandTypes,
            List<String> literalSources
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
            if (operatorTypeKey == null || operatorTypeKey.isBlank()) {
                throw new IllegalArgumentException("operatorTypeKey must not be blank");
            }
            if (canonicalKey == null || canonicalKey.isBlank()) {
                throw new IllegalArgumentException("canonicalKey must not be blank");
            }
            operandTypes = List.copyOf(Objects.requireNonNull(operandTypes, "operandTypes"));
            literalSources = List.copyOf(Objects.requireNonNull(literalSources, "literalSources"));
        }

        private static BlockedCandidate from(GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.Candidate candidate) {
            return new BlockedCandidate(
                    candidate.location(),
                    candidate.operator(),
                    blockedReason(candidate),
                    candidate.operatorTypeKey(),
                    candidate.canonicalKey(),
                    candidate.operandTypes(),
                    candidate.literalSources()
            );
        }

        public String summary() {
            return "location=" + location
                    + " operator=" + operator
                    + " reason=" + reason
                    + " operatorTypeKey=" + operatorTypeKey
                    + " canonicalKey=" + canonicalKey
                    + " operandTypes=" + listSummary(operandTypes)
                    + " literalSources=" + listSummary(literalSources);
        }
    }

    private static boolean hasIntArithmeticSemantics(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.Candidate candidate
    ) {
        return ("+".equals(candidate.operator()) || "*".equals(candidate.operator()))
                && !candidate.literalSources().isEmpty()
                && candidate.operandTypes().stream().allMatch("int"::equals);
    }

    private static String blockedReason(GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.Candidate candidate) {
        if (!("+".equals(candidate.operator()) || "*".equals(candidate.operator()))) {
            return "unsupportedOperator";
        }
        if (candidate.operandTypes().stream().anyMatch(type -> !"int".equals(type))) {
            if (candidate.operandTypes().stream().anyMatch("long"::equals)) {
                return "longOperandOverflowSemanticsRequireProof";
            }
            if (candidate.operandTypes().stream().anyMatch(type -> "float".equals(type) || "double".equals(type))) {
                return "floatingOperandSemanticsRequireProof";
            }
            return "nonIntOperandSemanticsRequireProof";
        }
        if (candidate.literalSources().isEmpty()) {
            return "missingLiteralSource";
        }
        return "unsupportedIntLiteralSemantics";
    }

    private static String blockedReasonExplanation(String reason) {
        return switch (reason) {
            case "unsupportedOperator" -> "only associative int literal + and * are inside the current numeric proof boundary";
            case "longOperandOverflowSemanticsRequireProof" -> "long operands require explicit overflow and backend-equivalence proof before canonicalization";
            case "floatingOperandSemanticsRequireProof" -> "floating operands require precision, NaN, signed-zero, and backend-equivalence proof before canonicalization";
            case "nonIntOperandSemanticsRequireProof" -> "non-int operands are outside the current Java int literal plus/times proof boundary";
            case "missingLiteralSource" -> "literal canonicalization proof requires at least one literal source";
            case "unsupportedIntLiteralSemantics" -> "candidate is not covered by the current int literal numeric semantics proof";
            default -> "literal numeric semantics blocked until explicitly proven: " + reason;
        };
    }

    private static String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String listSummary(List<String> values) {
        return String.join(",", values);
    }
}
