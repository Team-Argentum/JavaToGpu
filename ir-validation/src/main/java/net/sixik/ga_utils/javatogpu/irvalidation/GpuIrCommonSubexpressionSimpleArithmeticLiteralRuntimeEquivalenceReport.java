package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Explicit runtime-equivalence evidence for literal canonicalization preview candidates.
 *
 * <p>This report is an artifact container for tests and opt-in runners. It does not execute
 * rewrites, does not mutate IR, and does not connect literal canonicalization to production
 * fingerprints.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport(
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport,
        boolean equivalent,
        int inputCaseCount,
        List<String> comparedOutputs,
        List<String> diagnostics
) {
    public GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport {
        canonicalizationReport = Objects.requireNonNull(canonicalizationReport, "canonicalizationReport");
        numericSemanticsProofReport = Objects.requireNonNull(numericSemanticsProofReport, "numericSemanticsProofReport");
        if (inputCaseCount < 0) {
            throw new IllegalArgumentException("inputCaseCount must be non-negative");
        }
        Objects.requireNonNull(comparedOutputs, "comparedOutputs");
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (comparedOutputs.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("comparedOutputs must not contain blank entries");
        }
        if (diagnostics.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("diagnostics must not contain null entries");
        }
        comparedOutputs = List.copyOf(comparedOutputs);
        diagnostics = List.copyOf(diagnostics);
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport notRun(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport
    ) {
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport(
                canonicalizationReport,
                numericSemanticsProofReport,
                false,
                0,
                List.of(),
                List.of("literal canonicalization runtime equivalence not run")
        );
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport equivalent(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport,
            int inputCaseCount,
            List<String> comparedOutputs
    ) {
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport(
                canonicalizationReport,
                numericSemanticsProofReport,
                true,
                inputCaseCount,
                comparedOutputs,
                List.of()
        );
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport failed(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport,
            int inputCaseCount,
            List<String> comparedOutputs,
            List<String> diagnostics
    ) {
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport(
                canonicalizationReport,
                numericSemanticsProofReport,
                false,
                inputCaseCount,
                comparedOutputs,
                diagnostics
        );
    }

    public boolean successful() {
        return equivalent && diagnostics.isEmpty() && numericSemanticsProofReport.fullyProven();
    }

    public boolean hasDiagnostics() {
        return !diagnostics.isEmpty();
    }

    public int comparedOutputCount() {
        return comparedOutputs.size();
    }

    public int diagnosticCount() {
        return diagnostics.size();
    }

    public String readiness() {
        if (!canonicalizationReport.hasCandidates()) {
            return "none";
        }
        return successful() ? "proven" : "notProven";
    }

    public Optional<String> firstDiagnostic() {
        if (diagnostics.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(diagnostics.get(0));
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Successful", Boolean.toString(successful()));
        values.put(prefix + "Equivalent", Boolean.toString(equivalent));
        values.put(prefix + "Readiness", readiness());
        values.put(prefix + "InputCases", Integer.toString(inputCaseCount));
        values.put(prefix + "ComparedOutputs", Integer.toString(comparedOutputCount()));
        values.put(prefix + "ComparedOutputNames", String.join(",", comparedOutputs));
        values.put(prefix + "Diagnostics", Integer.toString(diagnosticCount()));
        values.put(prefix + "HasDiagnostics", Boolean.toString(hasDiagnostics()));
        values.put(prefix + "PreviewCandidates", Integer.toString(canonicalizationReport.candidateCount()));
        values.put(prefix + "UniqueCanonicalKeys", Integer.toString(canonicalizationReport.uniqueCanonicalKeyCount()));
        values.put(prefix + "NumericSemanticsFullyProven", Boolean.toString(numericSemanticsProofReport.fullyProven()));
        values.put(prefix + "NumericSemanticsReadiness", numericSemanticsProofReport.readiness());
        values.put(prefix + "CanonicalKeyCounts", mapSummary(canonicalizationReport.canonicalKeyCounts()));
        firstDiagnostic().ifPresent(diagnostic -> values.put(prefix + "FirstDiagnostic", diagnostic));
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseSimpleArithmeticLiteralRuntimeEquivalence");
    }

    public String summary() {
        return "CSE simple arithmetic literal runtime-equivalence successful=" + successful()
                + " equivalent=" + equivalent
                + " readiness=" + readiness()
                + " inputCases=" + inputCaseCount
                + " comparedOutputs=" + comparedOutputCount()
                + " diagnostics=" + diagnosticCount()
                + " previewCandidates=" + canonicalizationReport.candidateCount()
                + " uniqueCanonicalKeys=" + canonicalizationReport.uniqueCanonicalKeyCount()
                + " numericSemanticsFullyProven=" + numericSemanticsProofReport.fullyProven()
                + " canonicalKeys=" + canonicalizationReport.canonicalKeyCounts()
                + firstDiagnostic()
                .map(diagnostic -> " firstDiagnostic=" + diagnostic)
                .orElse("");
    }

    private static String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }
}
