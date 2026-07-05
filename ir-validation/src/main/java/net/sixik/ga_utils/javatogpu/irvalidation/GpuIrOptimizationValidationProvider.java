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
        values.putAll(report.optimizerGateSnapshot().artifactFields("optimizerGate"));
        values.putAll(report.optimizerGatePolicyDecision(mode(request.mode())).artifactFields("optimizerGatePolicy"));
        values.put("optimizerDiagnostics", Integer.toString(report.optimizerDiagnosticCount()));
        values.put("hasOptimizerDiagnostics", Boolean.toString(report.hasOptimizerDiagnostics()));
        values.putAll(report.commonSubexpressionArtifactSnapshot().artifactFields("cse"));
        values.putAll(report.commonSubexpressionNumericBoundaryReport().artifactFields("cseSimpleArithmeticNumericBoundary"));
        values.putAll(report.commonSubexpressionLiteralProofReport().artifactFields("cseSimpleArithmeticLiteralProof"));
        values.putAll(report.autoVectorizationArtifactSnapshot().artifactFields("autoVectorization"));
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
