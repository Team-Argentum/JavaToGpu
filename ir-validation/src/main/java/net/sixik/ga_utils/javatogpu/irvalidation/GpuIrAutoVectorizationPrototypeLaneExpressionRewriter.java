package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;

import java.util.Objects;

/**
 * Rewrites one scalar lane expression into a fixed-lane expression for prototype vector init.
 */
final class GpuIrAutoVectorizationPrototypeLaneExpressionRewriter {
    private final GpuIrAutoVectorizationPrototypeRewriteShapeDetector shapeDetector;

    GpuIrAutoVectorizationPrototypeLaneExpressionRewriter(
            GpuIrAutoVectorizationPrototypeRewriteShapeDetector shapeDetector
    ) {
        this.shapeDetector = Objects.requireNonNull(shapeDetector, "shapeDetector");
    }

    GpuIrExpression rewrite(GpuIrExpression expression, String inductionVariable, int lane) {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(inductionVariable, "inductionVariable");
        if (expression instanceof GpuIrLiteral) {
            return expression;
        }
        if (expression instanceof GpuIrArrayAccess arrayAccess
                && shapeDetector.isInductionIndex(arrayAccess.index(), inductionVariable)) {
            return new GpuIrArrayAccess(arrayAccess.arrayName(), new GpuIrLiteral(Integer.toString(lane)));
        }
        if (expression instanceof GpuIrBinary binary && shapeDetector.isPrototypeBinaryOperator(binary.operator())) {
            return new GpuIrBinary(
                    binary.operator(),
                    rewrite(binary.left(), inductionVariable, lane),
                    rewrite(binary.right(), inductionVariable, lane)
            );
        }
        if (expression instanceof GpuIrUnary unary && shapeDetector.isPrototypeUnaryOperator(unary.operator())) {
            return new GpuIrUnary(
                    unary.operator(),
                    rewrite(unary.operand(), inductionVariable, lane)
            );
        }
        throw new IllegalArgumentException("Unsupported prototype lane expression: " + expression);
    }
}
