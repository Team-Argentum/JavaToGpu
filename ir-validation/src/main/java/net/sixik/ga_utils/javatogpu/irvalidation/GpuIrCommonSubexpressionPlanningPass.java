package net.sixik.ga_utils.javatogpu.irvalidation;

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
    private final GpuIrCommonSubexpressionPlanningMode mode;

    public GpuIrCommonSubexpressionPlanningPass() {
        this(
                GpuIrCommonSubexpressionScanner.optimizerFocused(),
                new GpuIrCommonSubexpressionRewritePlanner(),
                GpuIrCommonSubexpressionPlanningMode.DIAGNOSTIC_ONLY
        );
    }

    public GpuIrCommonSubexpressionPlanningPass(
            GpuIrCommonSubexpressionScanner scanner,
            GpuIrCommonSubexpressionRewritePlanner planner,
            GpuIrCommonSubexpressionPlanningMode mode
    ) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    @Override
    public void run(GpuIrPassContext context) {
        GpuIrCommonSubexpressionRewritePlanReport report = plan(context);
        if (mode == GpuIrCommonSubexpressionPlanningMode.STRICT_FAIL_ON_SKIPPED_CANDIDATES
                && !report.skippedCandidates().isEmpty()) {
            GpuIrCommonSubexpressionSkippedCandidate skipped = report.skippedCandidates().getFirst();
            throw new GpuIrPassException("IR CSE planning failed for " + context.method().irMethod().name()
                    + ": skipped candidate " + skipped.reason() + " for " + skipped.candidate().fingerprint());
        }
    }

    public GpuIrCommonSubexpressionRewritePlanReport plan(GpuIrPassContext context) {
        GpuIrCommonSubexpressionReport report = scanner.scan(context.method().irMethod());
        return planner.planReport(context.method().irMethod(), report);
    }
}
