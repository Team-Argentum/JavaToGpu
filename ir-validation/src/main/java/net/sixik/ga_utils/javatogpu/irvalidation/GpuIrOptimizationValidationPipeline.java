package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassContext;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassException;

import java.util.List;
import java.util.Optional;

/**
 * Read-only optimizer validation entrypoint that combines safety and planning diagnostics.
 */
public final class GpuIrOptimizationValidationPipeline {
    private final GpuIrSafetyValidator safetyValidator;
    private final GpuIrCommonSubexpressionPlanningPass commonSubexpressionPlanningPass;
    private final GpuIrAutoVectorizationPlanningPass autoVectorizationPlanningPass;
    private final GpuIrOptimizationValidationMode mode;

    public GpuIrOptimizationValidationPipeline() {
        this(
                new GpuIrSafetyValidator(),
                new GpuIrCommonSubexpressionPlanningPass(),
                new GpuIrAutoVectorizationPlanningPass(),
                GpuIrOptimizationValidationMode.DIAGNOSTIC_ONLY
        );
    }

    public GpuIrOptimizationValidationPipeline(GpuIrOptimizationValidationMode mode) {
        this(
                new GpuIrSafetyValidator(),
                new GpuIrCommonSubexpressionPlanningPass(),
                new GpuIrAutoVectorizationPlanningPass(),
                mode
        );
    }

    public GpuIrOptimizationValidationPipeline(
            GpuIrSafetyValidator safetyValidator,
            GpuIrCommonSubexpressionPlanningPass commonSubexpressionPlanningPass,
            GpuIrAutoVectorizationPlanningPass autoVectorizationPlanningPass,
            GpuIrOptimizationValidationMode mode
    ) {
        this.safetyValidator = java.util.Objects.requireNonNull(safetyValidator, "safetyValidator");
        this.commonSubexpressionPlanningPass = java.util.Objects.requireNonNull(commonSubexpressionPlanningPass, "commonSubexpressionPlanningPass");
        this.autoVectorizationPlanningPass = java.util.Objects.requireNonNull(autoVectorizationPlanningPass, "autoVectorizationPlanningPass");
        this.mode = java.util.Objects.requireNonNull(mode, "mode");
    }

    public GpuIrOptimizationValidationReport validate(GpuIrPassContext context) {
        String methodName = methodName(context);
        Optional<String> safetyError = safetyError(context);
        GpuIrCommonSubexpressionRewritePreview commonSubexpressionPreview = commonSubexpressionPreview(context, safetyError);
        GpuIrAutoVectorizationPreview autoVectorizationPreview = autoVectorizationPlanningPass.preview(context);
        GpuIrOptimizationValidationReport report = new GpuIrOptimizationValidationReport(
                methodName,
                safetyError,
                commonSubexpressionPreview,
                autoVectorizationPreview
        );
        enforceMode(report);
        return report;
    }

    private Optional<String> safetyError(GpuIrPassContext context) {
        try {
            safetyValidator.run(context);
            return Optional.empty();
        } catch (GpuIrPassException exception) {
            return Optional.of(exception.getMessage());
        }
    }

    private GpuIrCommonSubexpressionRewritePreview commonSubexpressionPreview(
            GpuIrPassContext context,
            Optional<String> safetyError
    ) {
        if (safetyError.isPresent() || context == null || context.method() == null || context.method().irMethod() == null) {
            return emptyCommonSubexpressionPreview();
        }
        return commonSubexpressionPlanningPass.plan(context).preview();
    }

    private GpuIrCommonSubexpressionRewritePreview emptyCommonSubexpressionPreview() {
        return new GpuIrCommonSubexpressionRewritePreview(List.of(), List.of(), List.of());
    }

    private void enforceMode(GpuIrOptimizationValidationReport report) {
        if (mode == GpuIrOptimizationValidationMode.STRICT_FAIL_ON_SAFETY_ERROR && report.hasSafetyError()) {
            throw new GpuIrPassException("IR optimization validation failed for "
                    + report.methodName() + ": " + report.safetyError().orElseThrow());
        }
        if (mode == GpuIrOptimizationValidationMode.STRICT_FAIL_ON_OPTIMIZER_DIAGNOSTICS
                && report.hasBlockingDiagnostics()) {
            throw new GpuIrPassException("IR optimization validation failed for "
                    + report.methodName() + ": " + report.summary());
        }
    }

    private String methodName(GpuIrPassContext context) {
        if (context == null || context.method() == null) {
            return "<missing>";
        }
        GpuIrCompiledMethod compiledMethod = context.method();
        GpuIrMethod irMethod = compiledMethod.irMethod();
        if (irMethod != null && irMethod.name() != null && !irMethod.name().isBlank()) {
            return irMethod.name();
        }
        if (compiledMethod.emittedName() != null && !compiledMethod.emittedName().isBlank()) {
            return compiledMethod.emittedName();
        }
        return "<missing>";
    }
}
