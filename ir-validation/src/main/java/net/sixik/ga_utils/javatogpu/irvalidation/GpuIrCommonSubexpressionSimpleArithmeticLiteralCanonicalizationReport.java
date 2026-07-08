package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only preview of canonical keys for safe integer-literal simple arithmetic candidates.
 *
 * <p>This report intentionally does not affect expression fingerprints or rewrite readiness. It only
 * records the canonical key shape that future typed canonicalization could use once runtime and
 * backend numeric semantics are fully proven.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport(
        String methodName,
        List<Candidate> candidates
) {
    public GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport empty(String methodName) {
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport(methodName, List.of());
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport from(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport literalProofReport
    ) {
        Objects.requireNonNull(literalProofReport, "literalProofReport");
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport(
                literalProofReport.methodName(),
                literalProofReport.safeCandidates().stream()
                        .map(Candidate::from)
                        .toList()
        );
    }

    public int candidateCount() {
        return candidates.size();
    }

    public boolean hasCandidates() {
        return !candidates.isEmpty();
    }

    public Optional<Candidate> firstCandidate() {
        return candidates.stream().findFirst();
    }

    public Map<String, Long> operatorTypeCounts() {
        return candidates.stream()
                .collect(Collectors.groupingBy(
                        Candidate::operatorTypeKey,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, Long> canonicalKeyCounts() {
        return candidates.stream()
                .collect(Collectors.groupingBy(
                        Candidate::canonicalKey,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public int uniqueCanonicalKeyCount() {
        return canonicalKeyCounts().size();
    }

    public String readiness() {
        return hasCandidates() ? "preview" : "none";
    }

    public String proofBoundary() {
        return "safeIntLiteralCanonicalKeyPreview";
    }

    public String blockedBoundary() {
        return "previewOnlyNoFingerprintRewrite";
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Candidates", Integer.toString(candidateCount()));
        values.put(prefix + "HasCandidates", Boolean.toString(hasCandidates()));
        values.put(prefix + "Readiness", readiness());
        values.put(prefix + "ProofBoundary", proofBoundary());
        values.put(prefix + "BlockedBoundary", blockedBoundary());
        values.put(prefix + "OperatorTypeCounts", mapSummary(operatorTypeCounts()));
        values.put(prefix + "CanonicalKeyCounts", mapSummary(canonicalKeyCounts()));
        values.put(prefix + "UniqueCanonicalKeys", Integer.toString(uniqueCanonicalKeyCount()));
        firstCandidate().ifPresent(candidate -> {
            values.put(prefix + "FirstLocation", candidate.location());
            values.put(prefix + "FirstOperator", candidate.operator());
            values.put(prefix + "FirstOperatorTypeKey", candidate.operatorTypeKey());
            values.put(prefix + "FirstCanonicalKey", candidate.canonicalKey());
            values.put(prefix + "FirstLiteralSources", listSummary(candidate.literalSources()));
            values.put(prefix + "FirstSummary", candidate.summary());
        });
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseSimpleArithmeticLiteralCanonicalization");
    }

    public String summary() {
        return "CSE simple arithmetic literal canonicalization method=" + methodName
                + " candidates=" + candidateCount()
                + " hasCandidates=" + hasCandidates()
                + " readiness=" + readiness()
                + " proofBoundary=" + proofBoundary()
                + " blockedBoundary=" + blockedBoundary()
                + " operatorTypes=" + operatorTypeCounts()
                + " uniqueCanonicalKeys=" + uniqueCanonicalKeyCount()
                + " canonicalKeys=" + canonicalKeyCounts()
                + firstCandidate()
                .map(candidate -> " firstCandidate={" + candidate.summary() + "}")
                .orElse("");
    }

    public record Candidate(
            String location,
            String operator,
            String operatorTypeKey,
            String canonicalKey,
            List<String> operandTypes,
            List<String> literalSources
    ) {
        public Candidate {
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

        private static Candidate from(GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.SafeCandidate candidate) {
            return new Candidate(
                    candidate.location(),
                    candidate.operator(),
                    candidate.operatorTypeKey(),
                    previewCanonicalKey(candidate),
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

    private static String previewCanonicalKey(GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.SafeCandidate candidate) {
        return "literal_assoc_preview(" + candidate.operatorTypeKey() + ";literals=" + canonicalKeyLiteralSummary(candidate.literalSources()) + ")";
    }

    private static String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String listSummary(List<String> values) {
        return String.join(",", values);
    }

    private static String canonicalKeyLiteralSummary(List<String> values) {
        return values.stream()
                .map(GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport::escapeCanonicalKeyLiteral)
                .collect(Collectors.joining(","));
    }

    private static String escapeCanonicalKeyLiteral(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\\' || current == ',' || current == ';' || current == ')' || current == '(' || current == '=') {
                escaped.append('\\');
            }
            escaped.append(current);
        }
        return escaped.toString();
    }
}
