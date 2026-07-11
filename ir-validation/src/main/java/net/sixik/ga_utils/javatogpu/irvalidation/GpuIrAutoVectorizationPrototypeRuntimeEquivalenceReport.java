package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit artifact for opt-in prototype rewrite equivalence checks.
 *
 * <p>The report intentionally stores the result of an external test/integration equivalence
 * runner instead of executing IR itself. This keeps runtime-equivalence evidence close to the
 * prototype rewrite metadata without making the production validation path mutating.</p>
 */
public record GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport(
        GpuIrAutoVectorizationPrototypeRewriteReport rewriteReport,
        boolean equivalent,
        int inputCaseCount,
        List<String> comparedOutputs,
        List<String> diagnostics,
        List<GpuIrRuntimeEquivalenceCaseEvidence> caseEvidence
) {
    public GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport {
        rewriteReport = Objects.requireNonNull(rewriteReport, "rewriteReport");
        if (inputCaseCount < 0) {
            throw new IllegalArgumentException("inputCaseCount must be non-negative");
        }
        Objects.requireNonNull(comparedOutputs, "comparedOutputs");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(caseEvidence, "caseEvidence");
        if (comparedOutputs.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("comparedOutputs must not contain blank entries");
        }
        if (diagnostics.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("diagnostics must not contain null entries");
        }
        if (caseEvidence.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("caseEvidence must not contain null entries");
        }
        if (!caseEvidence.isEmpty() && caseEvidence.size() != inputCaseCount) {
            throw new IllegalArgumentException("caseEvidence size must match inputCaseCount when recorded");
        }
        java.util.Set<String> expectedOutputs = new java.util.LinkedHashSet<>(comparedOutputs);
        if (caseEvidence.stream().anyMatch(evidence -> !evidence.cpuReferenceOutputs().keySet().equals(expectedOutputs))) {
            throw new IllegalArgumentException("caseEvidence output names must match comparedOutputs");
        }
        comparedOutputs = List.copyOf(comparedOutputs);
        diagnostics = List.copyOf(diagnostics);
        caseEvidence = List.copyOf(caseEvidence);
    }

    public GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport(
            GpuIrAutoVectorizationPrototypeRewriteReport rewriteReport,
            boolean equivalent,
            int inputCaseCount,
            List<String> comparedOutputs,
            List<String> diagnostics
    ) {
        this(rewriteReport, equivalent, inputCaseCount, comparedOutputs, diagnostics, List.of());
    }

    public static GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport equivalent(
            GpuIrAutoVectorizationPrototypeRewriteReport rewriteReport,
            int inputCaseCount,
            List<String> comparedOutputs
    ) {
        return new GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport(
                rewriteReport,
                true,
                inputCaseCount,
                comparedOutputs,
                List.of(),
                List.of()
        );
    }

    public static GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport equivalent(
            GpuIrAutoVectorizationPrototypeRewriteReport rewriteReport,
            int inputCaseCount,
            List<String> comparedOutputs,
            List<GpuIrRuntimeEquivalenceCaseEvidence> caseEvidence
    ) {
        return new GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport(
                rewriteReport,
                true,
                inputCaseCount,
                comparedOutputs,
                List.of(),
                caseEvidence
        );
    }

    public static GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport failed(
            GpuIrAutoVectorizationPrototypeRewriteReport rewriteReport,
            int inputCaseCount,
            List<String> comparedOutputs,
            List<String> diagnostics
    ) {
        return new GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport(
                rewriteReport,
                false,
                inputCaseCount,
                comparedOutputs,
                diagnostics,
                List.of()
        );
    }

    public static GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport failed(
            GpuIrAutoVectorizationPrototypeRewriteReport rewriteReport,
            int inputCaseCount,
            List<String> comparedOutputs,
            List<String> diagnostics,
            List<GpuIrRuntimeEquivalenceCaseEvidence> caseEvidence
    ) {
        return new GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport(
                rewriteReport,
                false,
                inputCaseCount,
                comparedOutputs,
                diagnostics,
                caseEvidence
        );
    }

    public boolean successful() {
        return equivalent && diagnostics.isEmpty();
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

    public int caseEvidenceCount() {
        return caseEvidence.size();
    }

    public String firstDiagnostic() {
        return diagnostics.isEmpty() ? "" : diagnostics.get(0);
    }

    /**
     * Exposes stable string fields for explicit prototype/runtime-equivalence artifact writers.
     */
    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(prefix + "Successful", Boolean.toString(successful()));
        values.put(prefix + "Equivalent", Boolean.toString(equivalent));
        values.put(prefix + "InputCases", Integer.toString(inputCaseCount));
        values.put(prefix + "ComparedOutputs", Integer.toString(comparedOutputCount()));
        values.put(prefix + "ComparedOutputNames", String.join(",", comparedOutputs));
        values.put(prefix + "Diagnostics", Integer.toString(diagnosticCount()));
        values.put(prefix + "HasDiagnostics", Boolean.toString(hasDiagnostics()));
        values.put(prefix + "Payload.InputCases", Integer.toString(inputCaseCount));
        values.put(prefix + "Payload.CpuReference", cpuReferencePayload());
        values.put(prefix + "Payload.PreOptimizationOutput", preOptimizationOutputPayload());
        values.put(prefix + "Payload.PostOptimizationOutput", postOptimizationOutputPayload());
        values.put(prefix + "Payload.Tolerance", tolerancePayload());
        values.put(prefix + "Payload.FailureFixture", failureFixturePayload());
        values.put(prefix + "Payload.ReferenceMode", "original-ir-array-interpreter");
        values.put(prefix + "Payload.Case.Count", Integer.toString(caseEvidenceCount()));
        for (int caseIndex = 0; caseIndex < caseEvidence.size(); caseIndex++) {
            values.putAll(caseEvidence.get(caseIndex).artifactFields(prefix + "Payload.Case." + caseIndex + "."));
        }
        GpuIrRuntimeEquivalenceDiagnosticFamilies.putArtifactFields(values, prefix, diagnostics);
        if (hasDiagnostics()) {
            values.put(prefix + "FirstDiagnostic", firstDiagnostic());
            values.put(prefix + "AllDiagnostics", String.join(" | ", diagnostics));
            for (int index = 0; index < diagnostics.size(); index++) {
                values.put(prefix + "Diagnostic." + index, diagnostics.get(index));
            }
        }
        values.put(prefix + "AppliedRewrites", Integer.toString(rewriteReport.appliedRewriteCount()));
        values.put(prefix + "AppliedRewriteFamilies", rewriteReport.appliedRewriteFamilyCountersSummary());
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorizationPrototypeRuntimeEquivalence");
    }

    public String summary() {
        return "auto-vectorization prototype runtime-equivalence method=" + rewriteReport.method().name()
                + " successful=" + successful()
                + " equivalent=" + equivalent
                + " inputCases=" + inputCaseCount
                + " comparedOutputs=" + comparedOutputCount()
                + " diagnostics=" + diagnosticCount()
                + " caseEvidence=" + caseEvidenceCount()
                + " appliedRewrites=" + rewriteReport.appliedRewriteCount()
                + " appliedRewriteFamilies=" + rewriteReport.appliedRewriteFamilyCountersSummary()
                + (hasDiagnostics() ? " firstDiagnostic=" + firstDiagnostic() : "");
    }

    private String cpuReferencePayload() {
        return "inputCases=" + inputCaseCount
                + ", comparedOutputs=" + comparedOutputCount()
                + ", outputNames=" + joinedComparedOutputs();
    }

    private String preOptimizationOutputPayload() {
        return "method=" + rewriteReport.method().name()
                + ", appliedRewrites=" + rewriteReport.appliedRewriteCount()
                + ", families=" + rewriteReport.appliedRewriteFamilyCountersSummary();
    }

    private String postOptimizationOutputPayload() {
        return "equivalent=" + equivalent
                + ", successful=" + successful()
                + ", comparedOutputs=" + comparedOutputCount();
    }

    private String tolerancePayload() {
        return "mode=exact-int-lane, diagnostics=" + diagnosticCount()
                + ", diagnosticFamilies=" + GpuIrRuntimeEquivalenceDiagnosticFamilies.countsSummary(diagnostics);
    }

    private String failureFixturePayload() {
        return hasDiagnostics()
                ? String.join(" | ", diagnostics)
                : "none";
    }

    private String joinedComparedOutputs() {
        return comparedOutputs.isEmpty() ? "none" : String.join(",", comparedOutputs);
    }
}
