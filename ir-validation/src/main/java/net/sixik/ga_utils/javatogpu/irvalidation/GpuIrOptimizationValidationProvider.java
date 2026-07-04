package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassContext;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationDiagnosticPolicy;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationMode;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationProvider;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationReportEntry;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ServiceLoader bridge that exposes the unified validation pipeline to the compiler frontend.
 */
public final class GpuIrOptimizationValidationProvider implements GpuIrValidationProvider {
    @Override
    public void validate(GpuIrValidationRequest request) {
        if (request.mode() == GpuIrValidationMode.OFF) {
            return;
        }
        GpuIrOptimizationValidationReport report = new GpuIrOptimizationValidationPipeline(mode(request.mode())).validate(new GpuIrPassContext(
                request.method(),
                request.helperMethods(),
                request.structs(),
                request.entryPoint()
        ));
        if (request.mode() == GpuIrValidationMode.DIAGNOSTIC) {
            reportDiagnostic(request, report);
        }
        request.reportEntry(reportEntry(request, report));
    }

    private void reportDiagnostic(GpuIrValidationRequest request, GpuIrOptimizationValidationReport report) {
        if (request.diagnosticPolicy() == GpuIrValidationDiagnosticPolicy.QUIET) {
            return;
        }
        if (request.diagnosticPolicy() == GpuIrValidationDiagnosticPolicy.DETAILED) {
            request.reportDiagnostic(report.detailedSummary());
            return;
        }
        request.reportDiagnostic(report.compactSummary());
    }

    private GpuIrValidationReportEntry reportEntry(
            GpuIrValidationRequest request,
            GpuIrOptimizationValidationReport report
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("mode", request.mode().name());
        values.put("entryPoint", Boolean.toString(request.entryPoint()));
        values.put("safety", report.hasSafetyError() ? "failed" : "ok");
        values.put("hasSafetyError", Boolean.toString(report.hasSafetyError()));
        values.put("optimizerDiagnostics", Integer.toString(report.optimizerDiagnosticCount()));
        values.put("hasOptimizerDiagnostics", Boolean.toString(report.hasOptimizerDiagnostics()));
        values.put("cseInsertions", Integer.toString(report.commonSubexpressionInsertionCount()));
        values.put("cseReplacements", Integer.toString(report.commonSubexpressionReplacementCount()));
        values.put("cseSkipped", Integer.toString(report.commonSubexpressionSkippedCount()));
        values.put("autoVectorizationCandidates", Integer.toString(report.autoVectorizationRewriteCandidateCount()));
        values.put("autoVectorizationWarnings", Integer.toString(report.autoVectorizationWarningCount()));
        values.put("autoVectorizationRejections", Integer.toString(report.autoVectorizationRejectionCount()));
        values.put("autoVectorizationRewriteReadiness", report.autoVectorizationPreview().rewriteReadiness().artifactValue());
        values.put("autoVectorizationCanApplyRewrite", Boolean.toString(report.autoVectorizationPreview().canApplyRewrite()));
        values.put("autoVectorizationHasPolicyBlockedRewrite", Boolean.toString(report.autoVectorizationPreview().hasPolicyBlockedRewrite()));
        values.put("autoVectorizationRewriteBlockedCandidates", Integer.toString(report.autoVectorizationPreview().rewriteBlockedCandidateCount()));
        values.put("autoVectorizationHasRewriteBlockedCandidates", Boolean.toString(report.autoVectorizationPreview().hasRewriteBlockedCandidates()));
        report.autoVectorizationPreview().firstBlockingDiagnosticSummary()
                .ifPresent(diagnostic -> values.put("autoVectorizationFirstBlockingDiagnostic", diagnostic));
        report.autoVectorizationPreview().firstBlockingDiagnosticFamily()
                .ifPresent(family -> values.put("autoVectorizationFirstBlockingDiagnosticFamily", family));
        GpuIrAutoVectorizationRewritePlan autoVectorizationRewritePlan = report.autoVectorizationPreview().rewritePlan();
        GpuIrAutoVectorizationRewritePolicy autoVectorizationRewritePolicy = report.autoVectorizationPreview().rewritePolicy();
        values.put("autoVectorizationRewritePlanCandidates", Integer.toString(autoVectorizationRewritePlan.candidateCount()));
        values.put("autoVectorizationRewritePlanInsertions", Integer.toString(autoVectorizationRewritePlan.insertionCount()));
        values.put("autoVectorizationRewritePlanReplacements", Integer.toString(autoVectorizationRewritePlan.replacementCount()));
        values.put("autoVectorizationRewritePlanOperations", Integer.toString(autoVectorizationRewritePlan.operationCount()));
        values.put("autoVectorizationRewritePlanGuards", Integer.toString(autoVectorizationRewritePlan.guardDiagnostics().size()));
        values.put("autoVectorizationRewritePolicyCanRewrite", Boolean.toString(autoVectorizationRewritePolicy.canRewrite()));
        values.put("autoVectorizationRewritePolicyReadiness", autoVectorizationRewritePolicy.readiness().artifactValue());
        values.put("autoVectorizationRewritePolicyPlannedOperations", Integer.toString(autoVectorizationRewritePolicy.plannedOperationCount()));
        values.put("autoVectorizationRewritePolicyBlockingGuards", Integer.toString(autoVectorizationRewritePolicy.blockingGuards().size()));
        GpuIrAutoVectorizationRewriteDryRunReport autoVectorizationRewriteDryRunReport = report.autoVectorizationRewriteDryRunReport();
        values.put("autoVectorizationRewriteDryRunReadiness", autoVectorizationRewriteDryRunReport.readiness().artifactValue());
        values.put("autoVectorizationRewriteDryRunSuccessful", Boolean.toString(autoVectorizationRewriteDryRunReport.successful()));
        values.put("autoVectorizationRewriteDryRunDiagnostics", Integer.toString(autoVectorizationRewriteDryRunReport.diagnostics().size()));
        values.put("autoVectorizationRewriteDryRunCandidates", Integer.toString(autoVectorizationRewriteDryRunReport.candidateCount()));
        values.put("autoVectorizationRewriteDryRunOperations", Integer.toString(autoVectorizationRewriteDryRunReport.operationCount()));
        GpuIrAutoVectorizationResolvedRewriteOperations resolvedRewriteOperations = report.autoVectorizationResolvedRewriteOperations();
        values.put("autoVectorizationResolvedRewriteInsertions", Integer.toString(resolvedRewriteOperations.insertions().size()));
        values.put("autoVectorizationResolvedRewriteReplacements", Integer.toString(resolvedRewriteOperations.replacements().size()));
        values.put("autoVectorizationResolvedRewriteOperations", Integer.toString(resolvedRewriteOperations.operationCount()));
        if (resolvedRewriteOperations.hasOperations()) {
            values.put("autoVectorizationResolvedRewriteFirstInsertion", resolvedRewriteOperations.insertions().get(0).summary());
            values.put("autoVectorizationResolvedRewriteFirstReplacement", resolvedRewriteOperations.replacements().get(0).summary());
        }
        if (autoVectorizationRewriteDryRunReport.hasFailures()) {
            values.put("autoVectorizationRewriteDryRunFirstDiagnostic", autoVectorizationRewriteDryRunReport.firstDiagnostic());
        }
        autoVectorizationRewritePolicy.firstBlockingGuard()
                .ifPresent(guard -> values.put("autoVectorizationRewritePolicyFirstBlockingGuardFamily", guard.family().artifactValue()));
        autoVectorizationRewritePlan.guardFamilyTypeCounts().entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().artifactValue()))
                .forEach(entry -> values.put(
                        "autoVectorizationRewritePlanGuardFamily." + entry.getKey().artifactValue(),
                        Long.toString(entry.getValue())
                ));
        values.putAll(report.autoVectorizationPreview()
                .rewritePlanProofSummary()
                .artifactFields("autoVectorizationProofRewritePlan"));
        values.putAll(report.autoVectorizationPreview()
                .proofBundle()
                .artifactFields("autoVectorizationProofBundle"));
        report.autoVectorizationPreview().vectorTypeCounts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> values.put(
                        "autoVectorizationVectorType." + entry.getKey(),
                        Long.toString(entry.getValue())
                ));
        report.autoVectorizationPreview().warningFamilyCounts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> values.put(
                        "autoVectorizationWarningFamily." + entry.getKey(),
                        Long.toString(entry.getValue())
                ));
        report.autoVectorizationPreview().rejectionReasonCounts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> values.put(
                        "autoVectorizationRejectionReason." + entry.getKey().name(),
                        Long.toString(entry.getValue())
                ));
        report.safetyError().ifPresent(error -> values.put("safetyError", error));
        return new GpuIrValidationReportEntry("optimization-validation", report.methodName(), request.entryPoint(), values);
    }

    private GpuIrOptimizationValidationMode mode(GpuIrValidationMode mode) {
        return switch (mode) {
            case OFF, DIAGNOSTIC -> GpuIrOptimizationValidationMode.DIAGNOSTIC_ONLY;
            case STRICT_SAFETY -> GpuIrOptimizationValidationMode.STRICT_FAIL_ON_SAFETY_ERROR;
            case STRICT_OPTIMIZER -> GpuIrOptimizationValidationMode.STRICT_FAIL_ON_OPTIMIZER_DIAGNOSTICS;
        };
    }
}
