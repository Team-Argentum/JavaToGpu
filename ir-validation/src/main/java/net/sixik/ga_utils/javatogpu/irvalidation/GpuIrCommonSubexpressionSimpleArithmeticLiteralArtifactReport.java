package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Combined export artifact for explicit literal canonicalization validation runs.
 *
 * <p>This type packages the read-only preview, typed numeric proof, runtime-equivalence evidence,
 * and promotion gate into one stable CI-friendly artifact. It does not execute rewrites and does
 * not enable production fingerprint promotion.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport(
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalenceReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate canonicalizationGate,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport fingerprintDecisionReport,
        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport fingerprintParityReport
) {
    public GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport {
        canonicalizationReport = Objects.requireNonNull(canonicalizationReport, "canonicalizationReport");
        numericSemanticsProofReport = Objects.requireNonNull(numericSemanticsProofReport, "numericSemanticsProofReport");
        runtimeEquivalenceReport = Objects.requireNonNull(runtimeEquivalenceReport, "runtimeEquivalenceReport");
        canonicalizationGate = Objects.requireNonNull(canonicalizationGate, "canonicalizationGate");
        fingerprintDecisionReport = Objects.requireNonNull(fingerprintDecisionReport, "fingerprintDecisionReport");
        fingerprintParityReport = Objects.requireNonNull(fingerprintParityReport, "fingerprintParityReport");
    }

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalenceReport
    ) {
        this(defaultComponents(runtimeEquivalenceReport));
    }

    private GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport(DefaultComponents components) {
        this(
                components.canonicalizationReport(),
                components.numericSemanticsProofReport(),
                components.runtimeEquivalenceReport(),
                components.canonicalizationGate(),
                components.fingerprintDecisionReport(),
                components.fingerprintParityReport()
        );
    }

    public boolean successful() {
        return runtimeEquivalenceReport.successful();
    }

    public int previewCandidateCount() {
        return canonicalizationReport.candidateCount();
    }

    public int uniqueCanonicalKeyCount() {
        return canonicalizationReport.uniqueCanonicalKeyCount();
    }

    public int diagnosticCount() {
        return runtimeEquivalenceReport.diagnosticCount();
    }

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactSummary artifactSummary() {
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactSummary(
                successful(),
                previewCandidateCount(),
                uniqueCanonicalKeyCount(),
                numericSemanticsProofReport.fullyProven(),
                runtimeEquivalenceReport.successful(),
                runtimeEquivalenceReport.inputCaseCount(),
                runtimeEquivalenceReport.comparedOutputCount(),
                runtimeEquivalenceReport.diagnosticCount(),
                runtimeEquivalenceReport.firstDiagnostic().orElse(""),
                canonicalizationGate.readiness(),
                blockingReasonSummary()
        );
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Successful", Boolean.toString(successful()));
        values.put(prefix + "PreviewCandidates", Integer.toString(previewCandidateCount()));
        values.put(prefix + "UniqueCanonicalKeys", Integer.toString(uniqueCanonicalKeyCount()));
        values.put(prefix + "NumericSemanticsFullyProven", Boolean.toString(numericSemanticsProofReport.fullyProven()));
        values.put(prefix + "RuntimeEquivalenceSuccessful", Boolean.toString(runtimeEquivalenceReport.successful()));
        values.put(prefix + "RuntimeEquivalenceDiagnostics", Integer.toString(runtimeEquivalenceReport.diagnosticCount()));
        GpuIrRuntimeEquivalenceDiagnosticFamilies.putArtifactFields(
                values,
                prefix + "RuntimeEquivalence",
                runtimeEquivalenceReport.diagnostics()
        );
        values.put(prefix + "GateCanPromoteToFingerprint", Boolean.toString(canonicalizationGate.canPromoteToFingerprint()));
        values.put(prefix + "GateReadiness", canonicalizationGate.readiness());
        values.put(prefix + "GateBlockingReasons", blockingReasonSummary());
        values.put(prefix + "FingerprintDecisionReadiness", fingerprintDecisionReport.readiness());
        values.put(prefix + "FingerprintDecisionReadyForProduction", Boolean.toString(fingerprintDecisionReport.readyForProductionFingerprintIntegration()));
        values.put(prefix + "FingerprintParityReadiness", fingerprintParityReport.readiness());
        values.put(prefix + "FingerprintParityPreviewOnlyKeys", Integer.toString(fingerprintParityReport.previewOnlyKeyCount()));
        values.put(prefix + "Summary", artifactSummary().summaryLine());
        values.putAll(GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactConsistencyReport.from(this)
                .artifactFields(prefix + "Consistency."));
        values.putAll(canonicalizationReport.artifactFields(prefix + "Canonicalization."));
        values.putAll(numericSemanticsProofReport.artifactFields(prefix + "NumericSemanticsProof."));
        values.putAll(runtimeEquivalenceReport.artifactFields(prefix + "RuntimeEquivalence."));
        values.putAll(canonicalizationGate.artifactFields(prefix + "Gate."));
        values.putAll(fingerprintDecisionReport.artifactFields(prefix + "FingerprintDecision."));
        values.putAll(fingerprintParityReport.artifactFields(prefix + "FingerprintParity."));
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseSimpleArithmeticLiteralArtifact");
    }

    public String summary() {
        return artifactSummary().summaryLine()
                + " canonicalization={" + canonicalizationReport.summary() + "}"
                + " numericSemantics={" + numericSemanticsProofReport.summary() + "}"
                + " runtimeEquivalence={" + runtimeEquivalenceReport.summary() + "}"
                + " gate={" + canonicalizationGate.summary() + "}"
                + " fingerprintDecision={" + fingerprintDecisionReport.summary() + "}"
                + " fingerprintParity={" + fingerprintParityReport.summary() + "}"
                + " consistency={" + GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactConsistencyReport.from(this).summary() + "}";
    }

    private String blockingReasonSummary() {
        return canonicalizationGate.blockingReasons().stream()
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static DefaultComponents defaultComponents(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalenceReport
    ) {
        Objects.requireNonNull(runtimeEquivalenceReport, "runtimeEquivalenceReport");
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport =
                runtimeEquivalenceReport.canonicalizationReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport =
                runtimeEquivalenceReport.numericSemanticsProofReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate canonicalizationGate =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate.from(
                        canonicalizationReport,
                        numericSemanticsProofReport,
                        runtimeEquivalenceReport
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport fingerprintDecisionReport =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport.from(
                        canonicalizationReport,
                        numericSemanticsProofReport,
                        runtimeEquivalenceReport,
                        canonicalizationGate
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport fingerprintParityReport =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport.from(
                        emptyCseSnapshot(canonicalizationReport.methodName()),
                        canonicalizationReport,
                        fingerprintDecisionReport
                );
        return new DefaultComponents(
                canonicalizationReport,
                numericSemanticsProofReport,
                runtimeEquivalenceReport,
                canonicalizationGate,
                fingerprintDecisionReport,
                fingerprintParityReport
        );
    }

    private static GpuIrCommonSubexpressionArtifactSnapshot emptyCseSnapshot(String methodName) {
        return new GpuIrCommonSubexpressionArtifactSnapshot(
                methodName,
                new GpuIrCommonSubexpressionRewritePreview(java.util.List.of(), java.util.List.of(), java.util.List.of())
        );
    }

    private record DefaultComponents(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalenceReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate canonicalizationGate,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport fingerprintDecisionReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport fingerprintParityReport
    ) {
    }
}
