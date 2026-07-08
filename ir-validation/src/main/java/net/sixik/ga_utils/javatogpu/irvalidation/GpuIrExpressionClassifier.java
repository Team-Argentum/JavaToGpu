package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrFieldAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrHelperCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrStructInit;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrTernary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;

import java.util.List;
import java.util.Locale;

/**
 * Conservative side-effect classifier shared by validation and future optimizer passes.
 */
public final class GpuIrExpressionClassifier {
    public GpuIrExpressionEffect effectOf(GpuIrExpression expression) {
        return mayHaveSideEffects(expression) ? GpuIrExpressionEffect.SIDE_EFFECTING : GpuIrExpressionEffect.PURE;
    }

    public boolean mayHaveSideEffects(GpuIrExpression expression) {
        if (expression == null) {
            return false;
        }
        if (expression instanceof GpuIrHelperCall) {
            // Helper bodies are not inspected here yet, so calls stay unsafe by default.
            return true;
        }
        if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
            if (intrinsicCall.arguments() == null) {
                return true;
            }
            return intrinsicMayHaveSideEffects(intrinsicCall);
        }
        if (expression instanceof GpuIrBinary binary) {
            return mayHaveSideEffects(binary.left()) || mayHaveSideEffects(binary.right());
        }
        if (expression instanceof GpuIrUnary unary) {
            return mayHaveSideEffects(unary.operand());
        }
        if (expression instanceof GpuIrTernary ternary) {
            return mayHaveSideEffects(ternary.condition())
                    || mayHaveSideEffects(ternary.whenTrue())
                    || mayHaveSideEffects(ternary.whenFalse());
        }
        if (expression instanceof GpuIrCast cast) {
            return mayHaveSideEffects(cast.expression());
        }
        if (expression instanceof GpuIrFieldAccess fieldAccess) {
            return mayHaveSideEffects(fieldAccess.target());
        }
        if (expression instanceof GpuIrArrayAccess arrayAccess) {
            return mayHaveSideEffects(arrayAccess.index());
        }
        if (expression instanceof GpuIrStructInit structInit) {
            return expressionListMayHaveSideEffects(structInit.arguments());
        }
        return false;
    }

    private boolean expressionListMayHaveSideEffects(List<GpuIrExpression> expressions) {
        // Missing expression lists are malformed IR; keep optimization consumers conservative.
        return expressions == null || expressions.stream().anyMatch(this::mayHaveSideEffects);
    }

    private boolean intrinsicMayHaveSideEffects(GpuIrIntrinsicCall intrinsicCall) {
        // Intrinsic metadata is currently textual, so keep the heuristic intentionally broad.
        String backendName = intrinsicCall.backendName() == null ? "" : intrinsicCall.backendName().toLowerCase(Locale.ROOT);
        String template = intrinsicCall.codeTemplate() == null ? "" : intrinsicCall.codeTemplate().toLowerCase(Locale.ROOT);
        return backendName.contains("barrier")
                || backendName.contains("mem_fence")
                || backendName.contains("atomic")
                || backendName.contains("write_image")
                || backendName.contains("trap")
                || backendName.contains("unreachable")
                || template.contains("barrier(")
                || template.contains("mem_fence(")
                || template.contains("atomic_")
                || template.contains("write_image")
                || template.contains("__builtin_trap")
                || template.contains("__builtin_unreachable");
    }
}
