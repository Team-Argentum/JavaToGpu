package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Combined export artifact for explicit CSE rewrite validation runs.
 *
 * <p>This type packages read-only planning metadata and runtime-equivalence evidence produced by
 * opt-in callers. It does not execute rewrites and does not enable CSE mutation in normal
 * compiler validation.</p>
 */
public record GpuIrCommonSubexpressionArtifactReport(
        GpuIrCommonSubexpressionArtifactSnapshot snapshot,
        GpuIrCommonSubexpressionRuntimeEquivalenceReport runtimeEquivalenceReport
) {
    public GpuIrCommonSubexpressionArtifactReport {
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        runtimeEquivalenceReport = Objects.requireNonNull(runtimeEquivalenceReport, "runtimeEquivalenceReport");
    }

    public GpuIrCommonSubexpressionArtifactReport(
            GpuIrCommonSubexpressionRuntimeEquivalenceReport runtimeEquivalenceReport
    ) {
        this(
                new GpuIrCommonSubexpressionArtifactSnapshot(runtimeEquivalenceReport.rewritePlanReport().preview()),
                runtimeEquivalenceReport
        );
    }

    public boolean successful() {
        return runtimeEquivalenceReport.successful();
    }

    public int insertionCount() {
        return snapshot.insertionCount();
    }

    public int replacementCount() {
        return snapshot.replacementCount();
    }

    public int skippedCount() {
        return snapshot.skippedCount();
    }

    public int diagnosticCount() {
        return runtimeEquivalenceReport.diagnosticCount();
    }

    public GpuIrCommonSubexpressionArtifactSummary artifactSummary() {
        return new GpuIrCommonSubexpressionArtifactSummary(
                successful(),
                insertionCount(),
                replacementCount(),
                skippedCount(),
                runtimeEquivalenceReport.successful(),
                runtimeEquivalenceReport.inputCaseCount(),
                runtimeEquivalenceReport.comparedOutputCount(),
                runtimeEquivalenceReport.diagnosticCount(),
                runtimeEquivalenceReport.firstDiagnostic(),
                snapshot.skippedDominanceStatusCountsSummary()
        );
    }

    /**
     * Exposes one stable field map for explicit CSE artifact writers.
     */
    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(prefix + "Successful", Boolean.toString(successful()));
        values.put(prefix + "Insertions", Integer.toString(insertionCount()));
        values.put(prefix + "Replacements", Integer.toString(replacementCount()));
        values.put(prefix + "Skipped", Integer.toString(skippedCount()));
        values.put(prefix + "RuntimeEquivalenceSuccessful", Boolean.toString(runtimeEquivalenceReport.successful()));
        values.put(prefix + "RuntimeEquivalenceDiagnostics", Integer.toString(runtimeEquivalenceReport.diagnosticCount()));
        values.put(prefix + "Summary", artifactSummary().summaryLine());
        values.putAll(snapshot.artifactFields(prefix + "Snapshot."));
        values.putAll(runtimeEquivalenceReport.artifactFields(prefix + "RuntimeEquivalence."));
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseArtifact");
    }

    public String summary() {
        return artifactSummary().summaryLine()
                + " snapshot={" + snapshot.summary() + "}"
                + " runtimeEquivalence={" + runtimeEquivalenceReport.summary() + "}";
    }
}
