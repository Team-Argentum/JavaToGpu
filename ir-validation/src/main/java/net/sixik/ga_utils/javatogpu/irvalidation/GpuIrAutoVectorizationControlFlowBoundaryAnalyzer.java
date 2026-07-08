package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrContinue;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrDoWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrLoopBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitch;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrWhileLoop;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Reusable read-only classifier for sibling statements that block vector rewrites.
 */
public final class GpuIrAutoVectorizationControlFlowBoundaryAnalyzer {
    private final Predicate<GpuIrForLoop> vectorShapedLoopPredicate;

    public GpuIrAutoVectorizationControlFlowBoundaryAnalyzer() {
        this(loop -> false);
    }

    public GpuIrAutoVectorizationControlFlowBoundaryAnalyzer(Predicate<GpuIrForLoop> vectorShapedLoopPredicate) {
        this.vectorShapedLoopPredicate = Objects.requireNonNull(vectorShapedLoopPredicate, "vectorShapedLoopPredicate");
    }

    public GpuIrAutoVectorizationControlFlowBoundaryReport analyze(
            String candidateLocation,
            String siblingLocation,
            String side,
            GpuIrStatement sibling
    ) {
        return new GpuIrAutoVectorizationControlFlowBoundaryReport(
                candidateLocation,
                siblingLocation,
                side,
                kind(sibling)
        );
    }

    public GpuIrAutoVectorizationControlFlowBoundaryKind kind(GpuIrStatement statement) {
        if (isEarlyExitBoundary(statement)) {
            return GpuIrAutoVectorizationControlFlowBoundaryKind.EARLY_EXIT_BOUNDARY;
        }
        if (isControlFlowBoundary(statement)) {
            return GpuIrAutoVectorizationControlFlowBoundaryKind.CONTROL_FLOW_BOUNDARY;
        }
        return GpuIrAutoVectorizationControlFlowBoundaryKind.NONE;
    }

    public boolean isEarlyExitBoundary(GpuIrStatement statement) {
        return statement instanceof GpuIrReturn
                || statement instanceof GpuIrBreak
                || statement instanceof GpuIrContinue
                || statement instanceof GpuIrLoopBreak;
    }

    public boolean isControlFlowBoundary(GpuIrStatement statement) {
        if (statement instanceof GpuIrForLoop loop && vectorShapedLoopPredicate.test(loop)) {
            return false;
        }
        return statement instanceof GpuIrIf
                || statement instanceof GpuIrForLoop
                || statement instanceof GpuIrWhileLoop
                || statement instanceof GpuIrDoWhileLoop
                || statement instanceof GpuIrSwitch;
    }
}
