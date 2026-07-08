package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrFieldAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrHelperCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrStructInit;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrTernary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Produces order-sensitive fingerprints for pure expressions only.
 */
public final class GpuIrExpressionFingerprint {
    private final GpuIrExpressionClassifier classifier;

    public GpuIrExpressionFingerprint() {
        this(new GpuIrExpressionClassifier());
    }

    public GpuIrExpressionFingerprint(GpuIrExpressionClassifier classifier) {
        this.classifier = classifier;
    }

    public Optional<String> fingerprint(GpuIrExpression expression) {
        if (expression == null || classifier.effectOf(expression) != GpuIrExpressionEffect.PURE) {
            // Refuse unsafe expressions so reports cannot accidentally recommend invalid rewrites.
            return Optional.empty();
        }
        return Optional.of(fingerprintPure(expression));
    }

    private String fingerprintPure(GpuIrExpression expression) {
        if (expression instanceof GpuIrLiteral literal) {
            return "literal(" + escape(literal.sourceText()) + ")";
        }
        if (expression instanceof GpuIrVariableRef variableRef) {
            return "var(" + escape(variableRef.name()) + ")";
        }
        if (expression instanceof GpuIrArrayAccess arrayAccess) {
            return "array(" + escape(arrayAccess.arrayName()) + "," + fingerprintPure(arrayAccess.index()) + ")";
        }
        if (expression instanceof GpuIrFieldAccess fieldAccess) {
            return "field(" + fingerprintPure(fieldAccess.target()) + "," + escape(fieldAccess.fieldName()) + ")";
        }
        if (expression instanceof GpuIrBinary binary) {
            return "binary(" + escape(binary.operator()) + "," + fingerprintPure(binary.left()) + "," + fingerprintPure(binary.right()) + ")";
        }
        if (expression instanceof GpuIrUnary unary) {
            return "unary(" + escape(unary.operator()) + "," + fingerprintPure(unary.operand()) + ")";
        }
        if (expression instanceof GpuIrTernary ternary) {
            return "ternary(" + fingerprintPure(ternary.condition()) + "," + fingerprintPure(ternary.whenTrue()) + "," + fingerprintPure(ternary.whenFalse()) + ")";
        }
        if (expression instanceof GpuIrCast cast) {
            return "cast(" + escape(cast.targetType()) + "," + fingerprintPure(cast.expression()) + ")";
        }
        if (expression instanceof GpuIrStructInit structInit) {
            return "struct(" + escape(structInit.structType()) + "," + structInit.arguments().stream()
                    .map(this::fingerprintPure)
                    .collect(Collectors.joining(",")) + ")";
        }
        if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
            // Include backend/template/result metadata because equal Java shape may lower differently.
            return "intrinsic(" + escape(intrinsicCall.backendName()) + "," + escape(intrinsicCall.codeTemplate()) + ","
                    + escape(intrinsicCall.resultType()) + "," + fingerprintTypes(intrinsicCall.argumentTypes()) + "," + receiverFingerprint(intrinsicCall) + ","
                    + intrinsicCall.arguments().stream().map(this::fingerprintPure).collect(Collectors.joining(",")) + ")";
        }
        if (expression instanceof GpuIrHelperCall helperCall) {
            // Helper identity is part of the expression: two helpers may implement different math.
            return "helper(" + escape(helperCall.helperName()) + "," + escape(helperCall.resultType()) + ","
                    + helperCall.arguments().stream().map(this::fingerprintPure).collect(Collectors.joining(",")) + ")";
        }
        throw new IllegalArgumentException("Unsupported pure IR expression type: " + expression.getClass().getName());
    }

    private String receiverFingerprint(GpuIrIntrinsicCall intrinsicCall) {
        return intrinsicCall.receiver() == null ? "receiver(null)" : fingerprintPure(intrinsicCall.receiver());
    }

    private String fingerprintTypes(java.util.List<String> types) {
        return "types(" + types.stream().map(this::escape).collect(Collectors.joining(",")) + ")";
    }

    private String escape(String value) {
        if (value == null) {
            return "<null>";
        }
        return value.replace("\\", "\\\\").replace(")", "\\)").replace(",", "\\,");
    }
}
