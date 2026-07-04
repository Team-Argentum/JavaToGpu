package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;

import java.util.Objects;

/**
 * Detects the limited statement shape currently supported by the opt-in prototype rewrite.
 */
final class GpuIrAutoVectorizationPrototypeRewriteShapeDetector {
    GpuIrAutoVectorizationPrototypeRewriteShape detect(
            GpuIrForLoop loop,
            GpuIrAutoVectorizationRewriteCandidatePreview candidate,
            GpuIrAutoVectorizationResolvedInsertionOperation insertion
    ) {
        Objects.requireNonNull(loop, "loop");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(insertion, "insertion");

        if (candidate.assignmentCount() != 1 || candidate.targetArrays().size() != 1) {
            throw new IllegalArgumentException("Auto-vectorization prototype rewrite only supports one lane assignment at "
                    + candidate.loopLocation());
        }

        GpuIrAssignment assignment = laneAssignment(loop, candidate);
        return new GpuIrAutoVectorizationPrototypeRewriteShape(
                candidate.loopLocation(),
                insertion.statementIndex(),
                prototypeVectorType(candidate),
                candidate.startInclusive(),
                candidate.endExclusive(),
                candidate.inductionVariable(),
                candidate.targetArrays(),
                candidate.sourceArrays(),
                assignment
        );
    }

    private GpuIrAssignment laneAssignment(
            GpuIrForLoop loop,
            GpuIrAutoVectorizationRewriteCandidatePreview candidate
    ) {
        if (loop.body() == null || loop.body().size() != 1 || !(loop.body().get(0) instanceof GpuIrAssignment assignment)) {
            throw new IllegalArgumentException("Auto-vectorization prototype rewrite only supports a single assignment body at "
                    + candidate.loopLocation());
        }
        if (!(assignment.target() instanceof GpuIrArrayAccess target)
                || !candidate.targetArrays().get(0).equals(target.arrayName())
                || !isInductionIndex(target.index(), candidate.inductionVariable())) {
            throw new IllegalArgumentException("Auto-vectorization prototype rewrite requires targetArray[induction] at "
                    + candidate.loopLocation());
        }
        if (!isPrototypeLaneValue(assignment.value(), candidate.inductionVariable())) {
            throw new IllegalArgumentException("Auto-vectorization prototype rewrite only supports lane-copy or simple binary lane values at "
                    + candidate.loopLocation());
        }
        return assignment;
    }

    String prototypeVectorType(GpuIrAutoVectorizationRewriteCandidatePreview candidate) {
        return switch (candidate.scalarElementType()) {
            case "byte" -> "Byte" + candidate.laneCount();
            case "short" -> "Short" + candidate.laneCount();
            case "int" -> "Int" + candidate.laneCount();
            case "long" -> "Long" + candidate.laneCount();
            case "float" -> "Float" + candidate.laneCount();
            case "double" -> "Double" + candidate.laneCount();
            default -> throw new IllegalArgumentException("Unsupported prototype vector scalar type: " + candidate.scalarElementType());
        };
    }

    boolean isPrototypeBinaryOperator(String operator) {
        return "+".equals(operator)
                || "-".equals(operator)
                || "*".equals(operator)
                || "&".equals(operator)
                || "|".equals(operator)
                || "^".equals(operator);
    }

    boolean isInductionIndex(GpuIrExpression expression, String inductionVariable) {
        return expression instanceof GpuIrVariableRef variableRef
                && inductionVariable.equals(variableRef.name());
    }

    private boolean isPrototypeLaneValue(GpuIrExpression expression, String inductionVariable) {
        if (isLaneArrayAccess(expression, inductionVariable)) {
            return true;
        }
        if (expression instanceof GpuIrBinary binary && isPrototypeBinaryOperator(binary.operator())) {
            return isLaneArrayAccess(binary.left(), inductionVariable)
                    && isLaneArrayAccess(binary.right(), inductionVariable);
        }
        return false;
    }

    private boolean isLaneArrayAccess(GpuIrExpression expression, String inductionVariable) {
        return expression instanceof GpuIrArrayAccess arrayAccess
                && isInductionIndex(arrayAccess.index(), inductionVariable);
    }
}
