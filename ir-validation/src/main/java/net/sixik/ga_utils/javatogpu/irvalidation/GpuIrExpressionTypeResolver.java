package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrHelperCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrStructInit;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrTernary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Conservative local type resolver shared by validation and future optimizer rewrites.
 */
public final class GpuIrExpressionTypeResolver {
    public Optional<String> typeOf(GpuIrMethod method, GpuIrExpression expression) {
        Objects.requireNonNull(method, "method");
        return Optional.ofNullable(typeOf(expression, declaredTypes(method)));
    }

    public Optional<String> typeOf(GpuIrCompiledMethod method, GpuIrExpression expression) {
        Objects.requireNonNull(method, "method");
        Map<String, String> types = declaredParameterTypes(method);
        types.putAll(declaredTypes(method.irMethod()));
        return Optional.ofNullable(typeOf(expression, types));
    }

    private Map<String, String> declaredParameterTypes(GpuIrCompiledMethod method) {
        Map<String, String> types = new HashMap<>();
        for (ParsedGpuParameter parameter : method.parsedMethod().parameters()) {
            types.put(parameter.name(), parameter.javaType());
        }
        return types;
    }

    private Map<String, String> declaredTypes(GpuIrMethod method) {
        Map<String, String> types = new HashMap<>();
        for (GpuIrStatement statement : method.statements()) {
            if (statement instanceof GpuIrVariableDeclaration declaration) {
                types.put(declaration.name(), declaration.typeName());
            }
        }
        return types;
    }

    private String typeOf(GpuIrExpression expression, Map<String, String> declaredTypes) {
        if (expression instanceof GpuIrVariableRef variableRef) {
            return declaredTypes.get(variableRef.name());
        }
        if (expression instanceof GpuIrArrayAccess arrayAccess) {
            String arrayType = declaredTypes.get(arrayAccess.arrayName());
            return GpuTypeSupport.isArrayType(arrayType) ? GpuTypeSupport.componentType(arrayType) : null;
        }
        if (expression instanceof GpuIrBinary binary) {
            return binaryType(binary, declaredTypes);
        }
        if (expression instanceof GpuIrUnary unary) {
            return unaryType(unary, declaredTypes);
        }
        if (expression instanceof GpuIrTernary ternary) {
            return ternaryType(ternary, declaredTypes);
        }
        if (expression instanceof GpuIrCast cast) {
            return cast.targetType();
        }
        if (expression instanceof GpuIrHelperCall helperCall) {
            return helperCall.resultType();
        }
        if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
            return intrinsicCall.resultType();
        }
        if (expression instanceof GpuIrStructInit structInit) {
            return structInit.structType();
        }
        return null;
    }

    private String binaryType(GpuIrBinary binary, Map<String, String> declaredTypes) {
        String operator = binary.operator();
        if (Objects.equals("&&", operator) || Objects.equals("||", operator)
                || Objects.equals("<", operator) || Objects.equals("<=", operator)
                || Objects.equals(">", operator) || Objects.equals(">=", operator)
                || Objects.equals("==", operator) || Objects.equals("!=", operator)) {
            return "boolean";
        }
        String leftType = typeOf(binary.left(), declaredTypes);
        String rightType = typeOf(binary.right(), declaredTypes);
        if (Objects.equals("&", operator) || Objects.equals("|", operator) || Objects.equals("^", operator)) {
            return inferIntegralResultType(leftType, rightType);
        }
        if (Objects.equals("<<", operator) || Objects.equals(">>", operator)) {
            if (!GpuTypeSupport.isIntegralScalarType(leftType) || !GpuTypeSupport.isIntegralScalarType(rightType)) {
                return null;
            }
            return Objects.equals("long", GpuTypeSupport.declaredType(leftType)) ? "long" : "int";
        }
        return inferNumericResultType(leftType, rightType);
    }

    private String unaryType(GpuIrUnary unary, Map<String, String> declaredTypes) {
        String operandType = typeOf(unary.operand(), declaredTypes);
        if (Objects.equals("!", unary.operator())) {
            return "boolean";
        }
        if (Objects.equals("-", unary.operator()) || Objects.equals("+", unary.operator())) {
            return normalizeUnaryNumericType(operandType);
        }
        if (Objects.equals("~", unary.operator())) {
            return normalizeUnaryIntegralType(operandType);
        }
        return null;
    }

    private String ternaryType(GpuIrTernary ternary, Map<String, String> declaredTypes) {
        String trueType = typeOf(ternary.whenTrue(), declaredTypes);
        String falseType = typeOf(ternary.whenFalse(), declaredTypes);
        if (trueType == null || falseType == null) {
            return null;
        }
        String normalizedTrue = GpuTypeSupport.declaredType(trueType);
        String normalizedFalse = GpuTypeSupport.declaredType(falseType);
        if (Objects.equals(normalizedTrue, normalizedFalse)) {
            return normalizedTrue;
        }
        if (GpuTypeSupport.isSupportedVectorType(normalizedTrue) || GpuTypeSupport.isSupportedVectorType(normalizedFalse)) {
            return GpuTypeSupport.isHelperArgumentCompatible(normalizedTrue, normalizedFalse) ? normalizedTrue : null;
        }
        return inferNumericResultType(normalizedTrue, normalizedFalse);
    }

    private String inferNumericResultType(String leftType, String rightType) {
        leftType = GpuTypeSupport.declaredType(leftType);
        rightType = GpuTypeSupport.declaredType(rightType);
        if (leftType == null || rightType == null) {
            return null;
        }
        if (!GpuTypeSupport.isSupportedScalarType(leftType) || !GpuTypeSupport.isSupportedScalarType(rightType)) {
            return null;
        }
        if (Objects.equals("double", leftType) || Objects.equals("double", rightType)) {
            return "double";
        }
        if (Objects.equals("float", leftType) || Objects.equals("float", rightType)) {
            return "float";
        }
        if (Objects.equals("long", leftType) || Objects.equals("long", rightType)) {
            return "long";
        }
        return "int";
    }

    private String inferIntegralResultType(String leftType, String rightType) {
        leftType = GpuTypeSupport.declaredType(leftType);
        rightType = GpuTypeSupport.declaredType(rightType);
        if (!GpuTypeSupport.isIntegralScalarType(leftType) || !GpuTypeSupport.isIntegralScalarType(rightType)) {
            return null;
        }
        return Objects.equals("long", leftType) || Objects.equals("long", rightType) ? "long" : "int";
    }

    private String normalizeUnaryNumericType(String operandType) {
        operandType = GpuTypeSupport.declaredType(operandType);
        if (!GpuTypeSupport.isSupportedScalarType(operandType)) {
            return null;
        }
        if (Objects.equals("double", operandType) || Objects.equals("float", operandType) || Objects.equals("long", operandType)) {
            return operandType;
        }
        return "int";
    }

    private String normalizeUnaryIntegralType(String operandType) {
        operandType = GpuTypeSupport.declaredType(operandType);
        if (!GpuTypeSupport.isIntegralScalarType(operandType)) {
            return null;
        }
        return Objects.equals("long", operandType) ? "long" : "int";
    }
}
