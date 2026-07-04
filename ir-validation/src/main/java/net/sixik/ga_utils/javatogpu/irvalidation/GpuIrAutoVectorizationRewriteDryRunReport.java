package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Objects;

/**
 * Structured no-mutation validation result for future auto-vectorization rewrites.
 */
public record GpuIrAutoVectorizationRewriteDryRunReport(
        String methodName,
        GpuIrAutoVectorizationRewriteDryRunReadiness readiness,
        int candidateCount,
        int insertionOperationCount,
        int replacementOperationCount,
        List<String> diagnostics
) {
    public GpuIrAutoVectorizationRewriteDryRunReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        readiness = Objects.requireNonNull(readiness, "readiness");
        if (candidateCount < 0) {
            throw new IllegalArgumentException("candidateCount must be non-negative");
        }
        if (insertionOperationCount < 0) {
            throw new IllegalArgumentException("insertionOperationCount must be non-negative");
        }
        if (replacementOperationCount < 0) {
            throw new IllegalArgumentException("replacementOperationCount must be non-negative");
        }
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (diagnostics.stream().anyMatch(diagnostic -> diagnostic == null || diagnostic.isBlank())) {
            throw new IllegalArgumentException("diagnostics must not contain blank entries");
        }
        if (readiness == GpuIrAutoVectorizationRewriteDryRunReadiness.READY && !diagnostics.isEmpty()) {
            throw new IllegalArgumentException("ready dry-runs must not have diagnostics");
        }
        if (readiness != GpuIrAutoVectorizationRewriteDryRunReadiness.READY && diagnostics.isEmpty()) {
            throw new IllegalArgumentException("non-ready dry-runs must include diagnostics");
        }
    }

    public static GpuIrAutoVectorizationRewriteDryRunReport ready(
            String methodName,
            int candidateCount,
            int insertionOperationCount,
            int replacementOperationCount
    ) {
        return new GpuIrAutoVectorizationRewriteDryRunReport(
                methodName,
                GpuIrAutoVectorizationRewriteDryRunReadiness.READY,
                candidateCount,
                insertionOperationCount,
                replacementOperationCount,
                List.of()
        );
    }

    public static GpuIrAutoVectorizationRewriteDryRunReport failed(
            String methodName,
            int candidateCount,
            int insertionOperationCount,
            int replacementOperationCount,
            List<String> diagnostics
    ) {
        return new GpuIrAutoVectorizationRewriteDryRunReport(
                methodName,
                GpuIrAutoVectorizationRewriteDryRunReadiness.FAILED,
                candidateCount,
                insertionOperationCount,
                replacementOperationCount,
                diagnostics
        );
    }

    public static GpuIrAutoVectorizationRewriteDryRunReport skipped(
            String methodName,
            int candidateCount,
            int insertionOperationCount,
            int replacementOperationCount,
            List<String> diagnostics
    ) {
        return new GpuIrAutoVectorizationRewriteDryRunReport(
                methodName,
                GpuIrAutoVectorizationRewriteDryRunReadiness.SKIPPED,
                candidateCount,
                insertionOperationCount,
                replacementOperationCount,
                diagnostics
        );
    }

    public boolean successful() {
        return readiness == GpuIrAutoVectorizationRewriteDryRunReadiness.READY;
    }

    public boolean hasFailures() {
        return !successful();
    }

    public int operationCount() {
        return insertionOperationCount + replacementOperationCount;
    }

    public String firstDiagnostic() {
        return diagnostics.isEmpty() ? "" : diagnostics.get(0);
    }

    public String summary() {
        return "auto-vectorization rewrite dry-run method=" + methodName
                + " readiness=" + readiness.artifactValue()
                + " successful=" + successful()
                + " candidates=" + candidateCount
                + " insertionOperations=" + insertionOperationCount
                + " replacementOperations=" + replacementOperationCount
                + " operations=" + operationCount()
                + " diagnostics=" + diagnostics.size()
                + (hasFailures() ? " firstDiagnostic=" + firstDiagnostic() : "");
    }
}
