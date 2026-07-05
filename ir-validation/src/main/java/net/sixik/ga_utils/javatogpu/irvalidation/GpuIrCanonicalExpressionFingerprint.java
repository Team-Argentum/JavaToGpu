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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Normalizes safe pure expression shapes while still refusing side-effecting expressions.
 */
public final class GpuIrCanonicalExpressionFingerprint {
    // Keep this list narrow until value-type/overflow semantics are explicitly validated.
    private static final Set<String> COMMUTATIVE_OPERATORS = Set.of("+", "*", "&", "|", "^", "==", "!=");
    private static final Set<String> ASSOCIATIVE_BITWISE_OPERATORS = Set.of("&", "|", "^");
    private static final Set<String> SIMPLE_ASSOCIATIVE_ARITHMETIC_OPERATORS = Set.of("+", "*");

    private final GpuIrExpressionClassifier classifier;

    public GpuIrCanonicalExpressionFingerprint() {
        this(new GpuIrExpressionClassifier());
    }

    public GpuIrCanonicalExpressionFingerprint(GpuIrExpressionClassifier classifier) {
        this.classifier = classifier;
    }

    public Optional<String> fingerprint(GpuIrExpression expression) {
        if (expression == null || classifier.effectOf(expression) != GpuIrExpressionEffect.PURE) {
            // Canonicalization is only a matching aid; it must never bless unsafe rewrites.
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
            return binaryFingerprint(binary);
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
            return "struct(" + escape(structInit.structType()) + "," + fingerprintList(structInit.arguments()) + ")";
        }
        if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
            return "intrinsic(" + escape(intrinsicCall.backendName()) + "," + escape(intrinsicCall.codeTemplate()) + ","
                    + escape(intrinsicCall.resultType()) + "," + fingerprintTypes(intrinsicCall.argumentTypes()) + "," + receiverFingerprint(intrinsicCall) + ","
                    + fingerprintList(intrinsicCall.arguments()) + ")";
        }
        if (expression instanceof GpuIrHelperCall helperCall) {
            return "helper(" + escape(helperCall.helperName()) + "," + escape(helperCall.resultType()) + ","
                    + fingerprintList(helperCall.arguments()) + ")";
        }
        throw new IllegalArgumentException("Unsupported pure IR expression type: " + expression.getClass().getName());
    }

    private String binaryFingerprint(GpuIrBinary binary) {
        if (ASSOCIATIVE_BITWISE_OPERATORS.contains(binary.operator())) {
            return associativeBitwiseFingerprint(binary.operator(), binary);
        }
        if (SIMPLE_ASSOCIATIVE_ARITHMETIC_OPERATORS.contains(binary.operator())
                && hasNestedSameOperator(binary.operator(), binary)
                && isSimpleAssociativeArithmetic(binary.operator(), binary)) {
            return associativeArithmeticFingerprint(binary.operator(), binary);
        }
        String left = fingerprintPure(binary.left());
        String right = fingerprintPure(binary.right());
        if (COMMUTATIVE_OPERATORS.contains(binary.operator()) && left.compareTo(right) > 0) {
            // Sort by child fingerprint so `a + b` and `b + a` become the same candidate.
            String temporary = left;
            left = right;
            right = temporary;
        }
        return "binary(" + escape(binary.operator()) + "," + left + "," + right + ")";
    }

    private String associativeBitwiseFingerprint(String operator, GpuIrBinary binary) {
        List<String> operands = associativeOperands(operator, binary).stream()
                .sorted()
                .toList();
        return "binary_assoc(" + escape(operator) + "," + String.join(",", operands) + ")";
    }

    private String associativeArithmeticFingerprint(String operator, GpuIrBinary binary) {
        List<String> operands = associativeArithmeticOperands(operator, binary).stream()
                .sorted()
                .toList();
        return "binary_assoc_simple(" + escape(operator) + "," + String.join(",", operands) + ")";
    }

    private List<String> associativeOperands(String operator, GpuIrExpression expression) {
        if (expression instanceof GpuIrBinary binary && operator.equals(binary.operator())) {
            // Flatten only the exact same bitwise operator; mixed operators keep their nested shape.
            return java.util.stream.Stream.concat(
                    associativeOperands(operator, binary.left()).stream(),
                    associativeOperands(operator, binary.right()).stream()
            ).toList();
        }
        return List.of(fingerprintPure(expression));
    }

    private List<String> associativeArithmeticOperands(String operator, GpuIrExpression expression) {
        if (expression instanceof GpuIrBinary binary && operator.equals(binary.operator())) {
            // Arithmetic associativity is only used for simple reference-only trees until numeric semantics are proven.
            return java.util.stream.Stream.concat(
                    associativeArithmeticOperands(operator, binary.left()).stream(),
                    associativeArithmeticOperands(operator, binary.right()).stream()
            ).toList();
        }
        return List.of(fingerprintPure(expression));
    }

    private boolean isSimpleAssociativeArithmetic(String operator, GpuIrExpression expression) {
        if (expression instanceof GpuIrBinary binary && operator.equals(binary.operator())) {
            return isSimpleAssociativeArithmetic(operator, binary.left())
                    && isSimpleAssociativeArithmetic(operator, binary.right());
        }
        return isReferenceOnlyExpression(expression);
    }

    private boolean hasNestedSameOperator(String operator, GpuIrBinary binary) {
        return isSameOperatorBinary(operator, binary.left()) || isSameOperatorBinary(operator, binary.right());
    }

    private boolean isSameOperatorBinary(String operator, GpuIrExpression expression) {
        return expression instanceof GpuIrBinary binary && operator.equals(binary.operator());
    }

    private boolean isReferenceOnlyExpression(GpuIrExpression expression) {
        if (expression instanceof GpuIrVariableRef) {
            return true;
        }
        if (expression instanceof GpuIrArrayAccess arrayAccess) {
            return isReferenceOnlyExpression(arrayAccess.index());
        }
        if (expression instanceof GpuIrFieldAccess fieldAccess) {
            return isReferenceOnlyExpression(fieldAccess.target());
        }
        return false;
    }

    private String receiverFingerprint(GpuIrIntrinsicCall intrinsicCall) {
        return intrinsicCall.receiver() == null ? "receiver(null)" : fingerprintPure(intrinsicCall.receiver());
    }

    private String fingerprintTypes(List<String> types) {
        return "types(" + types.stream().map(this::escape).collect(Collectors.joining(",")) + ")";
    }

    private String fingerprintList(List<GpuIrExpression> expressions) {
        return expressions.stream().map(this::fingerprintPure).collect(Collectors.joining(","));
    }

    private String escape(String value) {
        if (value == null) {
            return "<null>";
        }
        return value.replace("\\", "\\\\").replace(")", "\\)").replace(",", "\\,");
    }
}
