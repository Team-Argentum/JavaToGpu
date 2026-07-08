package net.sixik.ga_utils.javatogpu.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fail-closed promotion gate for backend source reconstructed from IrGpu.
 *
 * <p>This gate is diagnostic-only. It makes the future production switch auditable by requiring reconstructed source,
 * source-parity evidence, runtime-equivalence evidence, and clean fallback state before any backend can be considered
 * review-ready for promotion.</p>
 */
public record GpuBackendSourcePromotionGate(
        String status,
        boolean ready,
        boolean reconstructed,
        boolean sourceAvailable,
        boolean sourceParityChecked,
        boolean sourceParityMatched,
        boolean runtimeEquivalenceRequired,
        boolean runtimeEquivalencePassed,
        String runtimeEquivalenceStatus,
        boolean runtimeEquivalenceExecuted,
        boolean runtimeEquivalenceEquivalent,
        int runtimeEquivalenceInputCaseCount,
        int runtimeEquivalenceComparedOutputCount,
        List<String> runtimeEquivalenceDiagnostics,
        boolean fallbackClean,
        String selectedSource,
        String payloadFormat,
        String runtimeLoadMode,
        List<String> reconstructionBlockers,
        List<String> reconstructionDiagnostics,
        List<String> diagnostics
) {

    public GpuBackendSourcePromotionGate {
        status = normalize(status, "blocked");
        selectedSource = normalize(selectedSource, "descriptor-source");
        payloadFormat = normalize(payloadFormat, "unknown");
        runtimeLoadMode = normalize(runtimeLoadMode, "source-compile");
        runtimeEquivalenceStatus = normalize(runtimeEquivalenceStatus, "not-run");
        runtimeEquivalenceInputCaseCount = Math.max(0, runtimeEquivalenceInputCaseCount);
        runtimeEquivalenceComparedOutputCount = Math.max(0, runtimeEquivalenceComparedOutputCount);
        runtimeEquivalenceDiagnostics = runtimeEquivalenceDiagnostics == null
                ? List.of()
                : List.copyOf(runtimeEquivalenceDiagnostics);
        reconstructionBlockers = reconstructionBlockers == null ? List.of() : List.copyOf(reconstructionBlockers);
        reconstructionDiagnostics = reconstructionDiagnostics == null ? List.of() : List.copyOf(reconstructionDiagnostics);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static GpuBackendSourcePromotionGate evaluate(
            GpuBackendSourceReconstructionResult reconstructionResult,
            GpuRuntimeEquivalenceEvidence runtimeEquivalenceEvidence,
            GpuRuntimeFallbackEvidence fallbackEvidence
    ) {
        GpuBackendSourceReconstructionResult reconstruction = reconstructionResult == null
                ? GpuBackendSourceReconstructionResult.notAttempted(
                        null,
                        "descriptor-source",
                        "unknown",
                        "source-compile",
                        List.of("backend-source-reconstruction-missing"),
                        List.of("backend source reconstruction result is not available")
                )
                : reconstructionResult;
        GpuRuntimeEquivalenceEvidence equivalence = runtimeEquivalenceEvidence == null
                ? GpuRuntimeEquivalenceEvidence.notRun(null, "runtime equivalence was not executed")
                : runtimeEquivalenceEvidence;
        GpuRuntimeFallbackEvidence fallback = fallbackEvidence == null ? GpuRuntimeFallbackEvidence.none() : fallbackEvidence;

        boolean sourceParityChecked = diagnosticFlag(reconstruction.diagnostics(), "sourceParity.checked=true");
        boolean sourceParityMatched = diagnosticFlag(reconstruction.diagnostics(), "sourceParity.matched=true");
        boolean runtimeEquivalencePassed = equivalence.executed() && equivalence.equivalent();
        boolean fallbackClean = GpuRuntimeFallbackEvidence.NONE.equals(fallback.decision());
        ArrayList<String> diagnostics = new ArrayList<>();

        if (!reconstruction.reconstructed()) {
            diagnostics.add("backend source must be reconstructed from IrGpu before promotion review");
        }
        if (!reconstruction.sourceAvailable()) {
            diagnostics.add("backend source payload must be available before promotion review");
        }
        if (!sourceParityChecked) {
            diagnostics.add("source parity must be checked before promotion review");
        }
        if (!sourceParityMatched) {
            diagnostics.add("reconstructed source must match descriptor source before promotion review");
        }
        if (!runtimeEquivalencePassed) {
            diagnostics.add("runtime equivalence must execute and pass before backend source promotion");
        }
        if (!fallbackClean) {
            diagnostics.add("fallback evidence must be clean before backend source promotion");
        }

        String status = diagnostics.isEmpty() ? "review-ready" : "blocked";
        if (diagnostics.isEmpty()) {
            diagnostics.add("backend source reconstruction is ready for promotion review");
        }

        return new GpuBackendSourcePromotionGate(
                status,
                reconstruction.ready(),
                reconstruction.reconstructed(),
                reconstruction.sourceAvailable(),
                sourceParityChecked,
                sourceParityMatched,
                true,
                runtimeEquivalencePassed,
                equivalence.status(),
                equivalence.executed(),
                equivalence.equivalent(),
                equivalence.inputCaseCount(),
                equivalence.comparedOutputCount(),
                equivalence.diagnostics(),
                fallbackClean,
                reconstruction.selectedSource(),
                reconstruction.payloadFormat(),
                reconstruction.runtimeLoadMode(),
                reconstruction.blockers(),
                reconstruction.diagnostics(),
                diagnostics
        );
    }

    public boolean reviewReady() {
        return "review-ready".equals(status);
    }

    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("status=").append(status).append('\n');
        builder.append("reviewReady=").append(reviewReady()).append('\n');
        builder.append("ready=").append(ready).append('\n');
        builder.append("reconstructed=").append(reconstructed).append('\n');
        builder.append("sourceAvailable=").append(sourceAvailable).append('\n');
        builder.append("sourceParityChecked=").append(sourceParityChecked).append('\n');
        builder.append("sourceParityMatched=").append(sourceParityMatched).append('\n');
        builder.append("runtimeEquivalenceRequired=").append(runtimeEquivalenceRequired).append('\n');
        builder.append("runtimeEquivalencePassed=").append(runtimeEquivalencePassed).append('\n');
        builder.append("runtimeEquivalence.status=").append(runtimeEquivalenceStatus).append('\n');
        builder.append("runtimeEquivalence.executed=").append(runtimeEquivalenceExecuted).append('\n');
        builder.append("runtimeEquivalence.equivalent=").append(runtimeEquivalenceEquivalent).append('\n');
        builder.append("runtimeEquivalence.inputCase.count=").append(runtimeEquivalenceInputCaseCount).append('\n');
        builder.append("runtimeEquivalence.comparedOutput.count=").append(runtimeEquivalenceComparedOutputCount).append('\n');
        builder.append("runtimeEquivalence.diagnostic.count=").append(runtimeEquivalenceDiagnostics.size()).append('\n');
        for (int index = 0; index < runtimeEquivalenceDiagnostics.size(); index++) {
            builder.append("runtimeEquivalence.diagnostic.").append(index).append('=').append(runtimeEquivalenceDiagnostics.get(index)).append('\n');
        }
        builder.append("fallbackClean=").append(fallbackClean).append('\n');
        builder.append("selectedSource=").append(selectedSource).append('\n');
        builder.append("payloadFormat=").append(payloadFormat).append('\n');
        builder.append("runtimeLoadMode=").append(runtimeLoadMode).append('\n');
        builder.append("reconstruction.blocker.count=").append(reconstructionBlockers.size()).append('\n');
        for (int index = 0; index < reconstructionBlockers.size(); index++) {
            builder.append("reconstruction.blocker.").append(index).append('=').append(reconstructionBlockers.get(index)).append('\n');
        }
        builder.append("reconstruction.diagnostic.count=").append(reconstructionDiagnostics.size()).append('\n');
        for (int index = 0; index < reconstructionDiagnostics.size(); index++) {
            builder.append("reconstruction.diagnostic.").append(index).append('=').append(reconstructionDiagnostics.get(index)).append('\n');
        }
        builder.append("diagnostic.count=").append(diagnostics.size()).append('\n');
        for (int index = 0; index < diagnostics.size(); index++) {
            builder.append("diagnostic.").append(index).append('=').append(diagnostics.get(index)).append('\n');
        }
        return builder.toString();
    }

    private static boolean diagnosticFlag(List<String> diagnostics, String flag) {
        return diagnostics != null && diagnostics.contains(flag);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNull(fallback, "fallback") : value;
    }
}
