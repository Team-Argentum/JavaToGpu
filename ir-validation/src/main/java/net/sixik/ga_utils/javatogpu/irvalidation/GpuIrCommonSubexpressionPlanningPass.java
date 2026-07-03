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
            GpuIrCommonSubexpressionSkippedDiagnostic skipped = report.previewSkippedDiagnostics().getFirst();
            throw new GpuIrPassException("IR CSE planning failed for " + context.method().irMethod().name()
                    + ": skipped candidate " + skipped.summary());
        }
    }

    public GpuIrCommonSubexpressionRewritePlanReport plan(GpuIrPassContext context) {
        GpuIrCommonSubexpressionReport report = scanner.scan(context.method().irMethod());
        return planner.planReport(context.method(), report);
    }

    public GpuIrCompiledMethod rewrite(GpuIrPassContext context) {
        Objects.requireNonNull(context, "context");
        GpuIrCommonSubexpressionRewritePlanReport report = plan(context);
        GpuIrMethod rewrittenMethod = applicator.apply(context.method(), report);
        return new GpuIrCompiledMethod(
                context.method().parsedMethod(),
                rewrittenMethod,
                context.method().emittedName(),
                context.method().helperDependencies()
        );
    }
}
