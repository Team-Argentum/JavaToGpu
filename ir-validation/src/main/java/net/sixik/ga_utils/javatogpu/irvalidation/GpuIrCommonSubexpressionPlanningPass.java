package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPass;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassContext;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassException;

import java.util.Objects;

/**
 * No-op optimizer bridge: builds CSE plans for validation/debugging but never mutates IR.
 */
public final class GpuIrCommonSubexpressionPlanningPass implements GpuIrPass {
    private final GpuIrCommonSubexpressionScanner scanner;
    private final GpuIrCommonSubexpressionRewritePlanner planner;
    private final GpuIrCommonSubexpressionRewriteApplicator applicator;
    private final GpuIrCommonSubexpressionPlanningMode mode;

    public GpuIrCommonSubexpressionPlanningPass() {
        this(
                GpuIrCommonSubexpressionScanner.optimizerFocused(),
                new GpuIrCommonSubexpressionRewritePlanner(),
                new GpuIrCommonSubexpressionRewriteApplicator(),
                GpuIrCommonSubexpressionPlanningMode.DIAGNOSTIC_ONLY
        );
    }

    public GpuIrCommonSubexpressionPlanningPass(
            GpuIrCommonSubexpressionScanner scanner,
            GpuIrCommonSubexpressionRewritePlanner planner,
            GpuIrCommonSubexpressionPlanningMode mode
    ) {
        this(scanner, planner, new GpuIrCommonSubexpressionRewriteApplicator(), mode);
    }

    public GpuIrCommonSubexpressionPlanningPass(
            GpuIrCommonSubexpressionScanner scanner,
            GpuIrCommonSubexpressionRewritePlanner planner,
            GpuIrCommonSubexpressionRewriteApplicator applicator,
            GpuIrCommonSubexpressionPlanningMode mode
    ) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.applicator = Objects.requireNonNull(applicator, "applicator");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    @Override
    public void run(GpuIrPassContext context) {
        GpuIrCommonSubexpressionRewritePlanReport report = plan(context);
        if (mode == GpuIrCommonSubexpressionPlanningMode.STRICT_FAIL_ON_SKIPPED_CANDIDATES
                && !report.skippedCandidates().isEmpty()) {
            GpuIrCommonSubexpressionRewritePreview preview = report.preview();
            GpuIrCommonSubexpressionSkippedDiagnostic skipped = report.previewSkippedDiagnostics().get(0);
            throw new GpuIrPassException("IR CSE planning failed for " + context.method().irMethod().name()
                    + ": " + preview.summary() + "; first skipped candidate " + skipped.summary());
        }
    }

    public GpuIrCommonSubexpressionRewritePlanReport plan(GpuIrPassContext context) {
        GpuIrCompiledMethod method = requireCompiledMethod(context, "planning");
        GpuIrCommonSubexpressionReport report = scanner.scan(method.irMethod());
        return planner.planReport(method, report);
    }

    public GpuIrCompiledMethod rewrite(GpuIrPassContext context) {
        GpuIrCompiledMethod method = requireCompiledMethod(context, "rewrite");
        GpuIrCommonSubexpressionRewritePlanReport report = plan(context);
        GpuIrMethod rewrittenMethod = applicator.apply(method, report);
        return new GpuIrCompiledMethod(
                method.parsedMethod(),
                rewrittenMethod,
                method.emittedName(),
                method.helperDependencies()
        );
    }

    private GpuIrCompiledMethod requireCompiledMethod(GpuIrPassContext context, String operation) {
        if (context == null) {
            throw new GpuIrPassException("IR CSE " + operation + " failed: missing pass context");
        }
        if (context.method() == null) {
            throw new GpuIrPassException("IR CSE " + operation + " failed: missing compiled method metadata");
        }
        if (context.method().irMethod() == null) {
            throw new GpuIrPassException("IR CSE " + operation + " failed: missing IR method metadata");
        }
        return context.method();
    }
}
