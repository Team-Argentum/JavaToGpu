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
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPass;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassContext;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassException;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrContinue;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrDoWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrExpressionStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrLoopBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrPrivateArrayDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitch;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitchCase;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Strict opt-in IR pass that rejects unsafe lowered trees before backend emission.
 */
public final class GpuIrSafetyValidator implements GpuIrPass {
    private final GpuIrExpressionClassifier expressionClassifier;

    public GpuIrSafetyValidator() {
        this(new GpuIrExpressionClassifier());
    }

    public GpuIrSafetyValidator(GpuIrExpressionClassifier expressionClassifier) {
        this.expressionClassifier = Objects.requireNonNull(expressionClassifier, "expressionClassifier");
    }

    @Override
    public void run(GpuIrPassContext context) {
        ValidationState state = new ValidationState(context.method(), helperMethodsByName(context));
        validateMethodMetadata(context.method(), state);
        for (ParsedGpuParameter parameter : context.method().parsedMethod().parameters()) {
            state.requireNonBlank(parameter.javaType(), "parameter type for " + parameter.name());
            state.declare(parameter.name(), "parameter", parameter.javaType(), storageAccess(parameter));
        }
        validateHelperDependencyMetadata(context.method(), state);
        validateStatements(context.method().irMethod().statements(), state, 0, 0, "method body");
        state.validateUsedHelpersAreDeclared();
    }

    private void validateMethodMetadata(GpuIrCompiledMethod method, ValidationState state) {
        state.requireNonBlank(method.irMethod().name(), "IR method name");
        state.requireNonBlank(method.emittedName(), "emitted method name");
        state.requireNonBlank(method.parsedMethod().returnType(), "method return type");
    }

    private Map<String, GpuIrCompiledMethod> helperMethodsByName(GpuIrPassContext context) {
        return context.helperMethods().stream()
                .collect(Collectors.toMap(
                        GpuIrCompiledMethod::emittedName,
                        helper -> helper,
                        (left, right) -> left
                ));
    }

    private StorageAccess storageAccess(ParsedGpuParameter parameter) {
        if (parameter.addressSpace() == GpuAddressSpace.CONSTANT || parameter.constant()) {
            return StorageAccess.READ_ONLY;
        }
        return StorageAccess.WRITABLE;
    }

    private enum StorageAccess {
        WRITABLE,
        READ_ONLY
    }

    private void validateHelperDependencyMetadata(GpuIrCompiledMethod method, ValidationState state) {
        Set<String> seenDependencies = new HashSet<>();
        for (String helperDependency : method.helperDependencies()) {
            state.requireNonBlank(helperDependency, "helper dependency name");
            if (!seenDependencies.add(helperDependency)) {
                state.fail("duplicate helper dependency metadata: " + helperDependency);
            }
            state.requireKnownHelper(helperDependency);
        }
    }

    private void validateStatements(
            List<GpuIrStatement> statements,
            ValidationState state,
            int loopDepth,
            int switchDepth,
            String location
    ) {
        if (statements == null) {
            state.fail("missing statement list: " + location);
        }
        for (GpuIrStatement statement : statements) {
            validateStatement(statement, state, loopDepth, switchDepth);
        }
    }

    private void validateStatement(GpuIrStatement statement, ValidationState state, int loopDepth, int switchDepth) {
        if (statement == null) {
            state.fail("null IR statement");
        }
        if (statement instanceof GpuIrVariableDeclaration declaration) {
            state.requireNonBlank(declaration.typeName(), "local declaration type for " + declaration.name());
            validateExpression(declaration.initializer(), state);
            validateAssignableType(declaration.typeName(), expressionType(declaration.initializer(), state), "local initializer for " + declaration.name(), state);
            state.declare(declaration.name(), "local", declaration.typeName(), StorageAccess.WRITABLE);
        } else if (statement instanceof GpuIrPrivateArrayDeclaration declaration) {
            state.requireNonBlank(declaration.elementType(), "private array element type for " + declaration.name());
            validateExpression(declaration.size(), state);
            validatePrivateArraySize(declaration.size(), state);
            state.declare(declaration.name(), "private array", declaration.elementType() + "[]", StorageAccess.WRITABLE);
        } else if (statement instanceof GpuIrAssignment assignment) {
            state.requireExpression(assignment.target(), "assignment target");
            state.requireExpression(assignment.value(), "assignment value");
            validateAssignmentTarget(assignment.target(), state);
            validateExpression(assignment.value(), state);
            validateAssignableType(expressionType(assignment.target(), state), expressionType(assignment.value(), state), "assignment", state);
        } else if (statement instanceof GpuIrExpressionStatement expressionStatement) {
            validateExpressionStatementEffect(expressionStatement.expression(), state);
            validateExpression(expressionStatement.expression(), state, false);
        } else if (statement instanceof GpuIrReturn gpuReturn) {
            validateReturnShape(gpuReturn, state);
            validateExpression(gpuReturn.value(), state);
            if (gpuReturn.value() != null) {
                validateAssignableType(state.methodReturnType(), expressionType(gpuReturn.value(), state), "return value", state);
            }
        } else if (statement instanceof GpuIrIf gpuIf) {
            state.requireExpression(gpuIf.condition(), "if condition");
            validateExpression(gpuIf.condition(), state);
            // Branch-local declarations must not leak into sibling or parent scopes.
            validateStatements(gpuIf.thenBranch(), state.copy(), loopDepth, switchDepth, "if then branch");
            validateStatements(gpuIf.elseBranch(), state.copy(), loopDepth, switchDepth, "if else branch");
        } else if (statement instanceof GpuIrForLoop loop) {
            ValidationState loopState = state.copy();
            validateOptionalStatement(loop.initializer(), loopState, loopDepth, switchDepth);
            state.requireExpression(loop.condition(), "for loop condition");
            validateExpression(loop.condition(), loopState);
            validateStatements(loop.body(), loopState.copy(), loopDepth + 1, switchDepth, "for loop body");
            validateOptionalStatement(loop.update(), loopState, loopDepth + 1, switchDepth);
        } else if (statement instanceof GpuIrWhileLoop loop) {
            state.requireExpression(loop.condition(), "while loop condition");
            validateExpression(loop.condition(), state);
            validateStatements(loop.body(), state.copy(), loopDepth + 1, switchDepth, "while loop body");
        } else if (statement instanceof GpuIrDoWhileLoop loop) {
            validateStatements(loop.body(), state.copy(), loopDepth + 1, switchDepth, "do-while loop body");
            state.requireExpression(loop.condition(), "do-while loop condition");
            validateExpression(loop.condition(), state);
        } else if (statement instanceof GpuIrSwitch gpuSwitch) {
            state.requireExpression(gpuSwitch.selector(), "switch selector");
            validateExpression(gpuSwitch.selector(), state);
            int defaultCount = 0;
            if (gpuSwitch.cases() == null) {
                state.fail("missing switch cases");
            }
            for (GpuIrSwitchCase switchCase : gpuSwitch.cases()) {
                if (switchCase == null) {
                    state.fail("null switch case");
                }
                if (switchCase.defaultCase()) {
                    defaultCount++;
                }
                if (switchCase.labels() == null) {
                    state.fail("missing switch case labels");
                }
                for (GpuIrExpression label : switchCase.labels()) {
                    state.requireExpression(label, "switch case label");
                    validateExpression(label, state);
                }
                validateStatements(switchCase.statements(), state.copy(), loopDepth, switchDepth + 1, "switch case body");
            }
            if (defaultCount > 1) {
                state.fail("switch contains more than one default case");
            }
        } else if (statement instanceof GpuIrBreak) {
            if (loopDepth == 0 && switchDepth == 0) {
                state.fail("break used outside loop or switch");
            }
        } else if (statement instanceof GpuIrLoopBreak) {
            if (loopDepth == 0) {
                state.fail("loop break used outside loop");
            }
        } else if (statement instanceof GpuIrContinue) {
            if (loopDepth == 0) {
                state.fail("continue used outside loop");
            }
        } else {
            state.fail("unknown IR statement type: " + statement.getClass().getName());
        }
    }

    private void validateExpressionStatementEffect(GpuIrExpression expression, ValidationState state) {
        if (expression == null) {
            state.fail("empty expression statement");
        }
        if (expressionClassifier.effectOf(expression) == GpuIrExpressionEffect.PURE) {
            // A pure expression statement usually means the frontend dropped an assignment/use.
            state.fail("expression statement has no observable side effect: " + expression.getClass().getSimpleName());
        }
    }

    private void validateOptionalStatement(GpuIrStatement statement, ValidationState state, int loopDepth, int switchDepth) {
        if (statement != null) {
            validateStatement(statement, state, loopDepth, switchDepth);
        }
    }

    private void validateAssignmentTarget(GpuIrExpression target, ValidationState state) {
        if (target instanceof GpuIrVariableRef variableRef) {
            state.requireDeclared(variableRef.name());
        } else if (target instanceof GpuIrArrayAccess arrayAccess) {
            state.requireDeclared(arrayAccess.arrayName());
            state.requireWritable(arrayAccess.arrayName(), "array assignment target");
            state.requireExpression(arrayAccess.index(), "array access index");
            validateExpression(arrayAccess.index(), state);
        } else if (target instanceof GpuIrFieldAccess fieldAccess) {
            state.requireExpression(fieldAccess.target(), "field access target");
            validateExpression(fieldAccess.target(), state);
        } else {
            state.fail("assignment target must be a variable, array element, or field access but got " + target.getClass().getSimpleName());
        }
    }

    private void validateExpression(GpuIrExpression expression, ValidationState state) {
        validateExpression(expression, state, true);
    }

    private void validateExpression(GpuIrExpression expression, ValidationState state, boolean valueContext) {
        if (expression == null) {
            return;
        }
        if (valueContext) {
            validateExpressionValueMetadata(expression, state);
        }
        if (expression instanceof GpuIrVariableRef variableRef) {
            state.requireDeclared(variableRef.name());
        } else if (expression instanceof GpuIrArrayAccess arrayAccess) {
            state.requireDeclared(arrayAccess.arrayName());
            state.requireExpression(arrayAccess.index(), "array access index");
            validateExpression(arrayAccess.index(), state);
        } else if (expression instanceof GpuIrBinary binary) {
            state.requireNonBlank(binary.operator(), "binary operator");
            state.requireExpression(binary.left(), "binary left operand");
            state.requireExpression(binary.right(), "binary right operand");
            validateExpression(binary.left(), state);
            validateExpression(binary.right(), state);
        } else if (expression instanceof GpuIrUnary unary) {
            state.requireNonBlank(unary.operator(), "unary operator");
            state.requireExpression(unary.operand(), "unary operand");
            validateExpression(unary.operand(), state);
        } else if (expression instanceof GpuIrTernary ternary) {
            state.requireExpression(ternary.condition(), "ternary condition");
            state.requireExpression(ternary.whenTrue(), "ternary true branch");
            state.requireExpression(ternary.whenFalse(), "ternary false branch");
            validateExpression(ternary.condition(), state);
            validateExpression(ternary.whenTrue(), state);
            validateExpression(ternary.whenFalse(), state);
        } else if (expression instanceof GpuIrCast cast) {
            state.requireNonBlank(cast.targetType(), "cast target type");
            state.requireExpression(cast.expression(), "cast expression");
            validateExpression(cast.expression(), state);
        } else if (expression instanceof GpuIrFieldAccess fieldAccess) {
            state.requireNonBlank(fieldAccess.fieldName(), "field access name");
            state.requireExpression(fieldAccess.target(), "field access target");
            validateExpression(fieldAccess.target(), state);
        } else if (expression instanceof GpuIrHelperCall helperCall) {
            state.requireKnownHelper(helperCall.helperName());
            state.requireNonBlank(helperCall.resultType(), "helper call result type for " + helperCall.helperName());
            validateHelperPurityMetadata(helperCall, state);
            state.recordUsedHelper(helperCall.helperName());
            validateExpressionList(helperCall.arguments(), state, "helper call argument");
            validateHelperArgumentCount(helperCall, state);
            validateHelperArgumentTypes(helperCall, state);
            validateHelperArgumentStorage(helperCall, state);
        } else if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
            state.requireNonBlank(intrinsicCall.backendName(), "intrinsic backend name");
            state.requireNonBlank(intrinsicCall.codeTemplate(), "intrinsic code template");
            state.requireNonBlank(intrinsicCall.resultType(), "intrinsic result type");
            validateExpressionList(intrinsicCall.arguments(), state, "intrinsic argument");
            validateIntrinsicTemplate(intrinsicCall, state);
            validateExpression(intrinsicCall.receiver(), state);
        } else if (expression instanceof GpuIrStructInit structInit) {
            state.requireNonBlank(structInit.structType(), "struct initializer type");
            validateExpressionList(structInit.arguments(), state, "struct initializer argument");
        } else if (expression instanceof GpuIrLiteral) {
            return;
        } else {
            state.fail("unknown IR expression type: " + expression.getClass().getName());
        }
    }

    private void validateExpressionValueMetadata(GpuIrExpression expression, ValidationState state) {
        if (expression instanceof GpuIrCast cast && Objects.equals("void", cast.targetType())) {
            state.fail("cast expression cannot target void");
        }
        if (expression instanceof GpuIrHelperCall helperCall && Objects.equals("void", helperCall.resultType())) {
            state.fail("void helper call cannot be used as a value expression: " + helperCall.helperName());
        }
        if (expression instanceof GpuIrIntrinsicCall intrinsicCall && Objects.equals("void", intrinsicCall.resultType())) {
            state.fail("void intrinsic call cannot be used as a value expression: " + intrinsicCall.backendName());
        }
        if (expression instanceof GpuIrStructInit structInit && Objects.equals("void", structInit.structType())) {
            state.fail("struct initializer cannot target void");
        }
    }

    private String expressionType(GpuIrExpression expression, ValidationState state) {
        if (expression instanceof GpuIrVariableRef variableRef) {
            return state.declaredType(variableRef.name());
        }
        if (expression instanceof GpuIrArrayAccess arrayAccess) {
            String arrayType = state.declaredType(arrayAccess.arrayName());
            if (GpuTypeSupport.isArrayType(arrayType)) {
                return GpuTypeSupport.componentType(arrayType);
            }
            return null;
        }
        if (expression instanceof GpuIrBinary binary) {
            return binaryExpressionType(binary, state);
        }
        if (expression instanceof GpuIrUnary unary) {
            return unaryExpressionType(unary, state);
        }
        if (expression instanceof GpuIrTernary ternary) {
            return ternaryExpressionType(ternary, state);
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

    private String binaryExpressionType(GpuIrBinary binary, ValidationState state) {
        String leftType = expressionType(binary.left(), state);
        String rightType = expressionType(binary.right(), state);
        String operator = binary.operator();
        if (Objects.equals("&&", operator) || Objects.equals("||", operator)
                || Objects.equals("<", operator) || Objects.equals("<=", operator)
                || Objects.equals(">", operator) || Objects.equals(">=", operator)
                || Objects.equals("==", operator) || Objects.equals("!=", operator)) {
            return "boolean";
        }
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

    private String unaryExpressionType(GpuIrUnary unary, ValidationState state) {
        String operandType = expressionType(unary.operand(), state);
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

    private String ternaryExpressionType(GpuIrTernary ternary, ValidationState state) {
        String trueType = expressionType(ternary.whenTrue(), state);
        String falseType = expressionType(ternary.whenFalse(), state);
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

    private void validateAssignableType(String expectedType, String actualType, String location, ValidationState state) {
        if (expectedType == null || actualType == null) {
            return;
        }
        String normalizedExpected = GpuTypeSupport.declaredType(expectedType);
        String normalizedActual = GpuTypeSupport.declaredType(actualType);
        if (normalizedExpected == null || normalizedActual == null) {
            return;
        }
        if (!GpuTypeSupport.isHelperArgumentCompatible(normalizedActual, normalizedExpected)) {
            state.fail("type mismatch in " + location + ": expected " + normalizedExpected + " but got " + normalizedActual);
        }
    }

    private void validateExpressionList(List<GpuIrExpression> expressions, ValidationState state, String label) {
        if (expressions == null) {
            state.fail("missing " + label + " list");
        }
        for (GpuIrExpression expression : expressions) {
            state.requireExpression(expression, label);
            validateExpression(expression, state);
        }
    }

    private void validateHelperPurityMetadata(GpuIrHelperCall helperCall, ValidationState state) {
        GpuIrCompiledMethod helper = state.helperMethod(helperCall.helperName());
        if (helper == null) {
            return;
        }
        boolean helperReturnsVoid = Objects.equals("void", helper.parsedMethod().returnType());
        if (helperReturnsVoid && !Objects.equals("void", helperCall.resultType())) {
            state.fail("void helper call must use void result metadata: " + helperCall.helperName());
        }
    }

    private void validateHelperArgumentStorage(GpuIrHelperCall helperCall, ValidationState state) {
        GpuIrCompiledMethod helper = state.helperMethod(helperCall.helperName());
        if (helper == null) {
            return;
        }
        List<ParsedGpuParameter> parameters = helper.parsedMethod().parameters();
        int checkedArgumentCount = Math.min(parameters.size(), helperCall.arguments().size());
        for (int index = 0; index < checkedArgumentCount; index++) {
            ParsedGpuParameter parameter = parameters.get(index);
            if (!isPotentiallyMutatingStorageParameter(parameter)) {
                continue;
            }
            String storageName = referencedStorageName(helperCall.arguments().get(index));
            if (storageName != null) {
                state.requireWritable(storageName, "mutable helper argument " + parameter.name() + " for " + helperCall.helperName());
            }
        }
    }

    private void validateHelperArgumentCount(GpuIrHelperCall helperCall, ValidationState state) {
        GpuIrCompiledMethod helper = state.helperMethod(helperCall.helperName());
        if (helper == null) {
            return;
        }
        int expectedCount = helper.parsedMethod().parameters().size();
        int actualCount = helperCall.arguments().size();
        if (expectedCount != actualCount) {
            state.fail("helper call argument count mismatch for " + helperCall.helperName()
                    + ": expected " + expectedCount + " but got " + actualCount);
        }
    }

    private void validateHelperArgumentTypes(GpuIrHelperCall helperCall, ValidationState state) {
        GpuIrCompiledMethod helper = state.helperMethod(helperCall.helperName());
        if (helper == null) {
            return;
        }
        List<ParsedGpuParameter> parameters = helper.parsedMethod().parameters();
        if (parameters.size() != helperCall.arguments().size()) {
            return;
        }
        for (int index = 0; index < parameters.size(); index++) {
            ParsedGpuParameter parameter = parameters.get(index);
            GpuIrExpression argument = helperCall.arguments().get(index);
            validateAssignableType(
                    parameter.javaType(),
                    expressionType(argument, state),
                    "helper argument " + parameter.name() + " for " + helperCall.helperName(),
                    state
            );
        }
    }

    private boolean isPotentiallyMutatingStorageParameter(ParsedGpuParameter parameter) {
        return GpuTypeSupport.isArrayType(parameter.javaType()) && storageAccess(parameter) == StorageAccess.WRITABLE;
    }

    private String referencedStorageName(GpuIrExpression expression) {
        if (expression instanceof GpuIrVariableRef variableRef) {
            return variableRef.name();
        }
        if (expression instanceof GpuIrArrayAccess arrayAccess) {
            return arrayAccess.arrayName();
        }
        return null;
    }

    private void validateReturnShape(GpuIrReturn gpuReturn, ValidationState state) {
        boolean methodReturnsVoid = Objects.equals("void", state.methodReturnType());
        if (methodReturnsVoid && gpuReturn.value() != null) {
            state.fail("void method returns a value");
        }
        if (!methodReturnsVoid && gpuReturn.value() == null) {
            state.fail("non-void method returns without a value");
        }
    }

    private void validatePrivateArraySize(GpuIrExpression size, ValidationState state) {
        if (size instanceof GpuIrLiteral literal) {
            String sourceText = literal.sourceText();
            try {
                int value = Integer.parseInt(sourceText.strip());
                if (value <= 0) {
                    state.fail("private array literal size must be positive: " + sourceText);
                }
            } catch (NumberFormatException ignored) {
                // Dynamic or constant-backed sizes are validated by the frontend/backend path.
            }
        }
    }

    private static final class ValidationState {
        private final GpuIrCompiledMethod method;
        private final Map<String, GpuIrCompiledMethod> helperMethodsByName;
        private final Set<String> declaredNames;
        private final Map<String, String> declaredTypes;
        private final Map<String, StorageAccess> storageAccessByName;
        private final Set<String> usedHelpers;

        private ValidationState(GpuIrCompiledMethod method, Map<String, GpuIrCompiledMethod> helperMethodsByName) {
            this(method, helperMethodsByName, new HashSet<>(), new HashMap<>(), new HashMap<>(), new HashSet<>());
        }

        private ValidationState(
                GpuIrCompiledMethod method,
                Map<String, GpuIrCompiledMethod> helperMethodsByName,
                Set<String> declaredNames,
                Map<String, String> declaredTypes,
                Map<String, StorageAccess> storageAccessByName,
                Set<String> usedHelpers
        ) {
            this.method = method;
            this.helperMethodsByName = helperMethodsByName;
            this.declaredNames = declaredNames;
            this.declaredTypes = declaredTypes;
            this.storageAccessByName = storageAccessByName;
            this.usedHelpers = usedHelpers;
        }

        private ValidationState copy() {
            // Declarations are scoped, but helper usage is method-wide metadata.
            return new ValidationState(
                    method,
                    helperMethodsByName,
                    new HashSet<>(declaredNames),
                    new HashMap<>(declaredTypes),
                    new HashMap<>(storageAccessByName),
                    usedHelpers
            );
        }

        private void declare(String name, String kind, String type, StorageAccess storageAccess) {
            if (name == null || name.isBlank()) {
                fail("blank " + kind + " name");
            }
            if (!declaredNames.add(name)) {
                fail("duplicate " + kind + " declaration: " + name);
            }
            requireNonBlank(type, kind + " type for " + name);
            declaredTypes.put(name, type);
            storageAccessByName.put(name, Objects.requireNonNull(storageAccess, "storageAccess"));
        }

        private void requireDeclared(String name) {
            if (name == null || name.isBlank()) {
                fail("blank variable reference");
            }
            if (!declaredNames.contains(name)) {
                fail("unknown variable reference: " + name);
            }
        }

        private void requireKnownHelper(String helperName) {
            requireNonBlank(helperName, "helper call target");
            if (!helperMethodsByName.containsKey(helperName)) {
                fail("unknown helper call target: " + helperName);
            }
        }

        private GpuIrCompiledMethod helperMethod(String helperName) {
            return helperMethodsByName.get(helperName);
        }

        private String declaredType(String name) {
            return declaredTypes.get(name);
        }

        private void requireWritable(String name, String location) {
            if (storageAccessByName.get(name) == StorageAccess.READ_ONLY) {
                fail("read-only storage cannot be used as " + location + ": " + name);
            }
        }

        private void recordUsedHelper(String helperName) {
            usedHelpers.add(helperName);
        }

        private void validateUsedHelpersAreDeclared() {
            Set<String> declaredDependencies = new HashSet<>(method.helperDependencies());
            for (String usedHelper : usedHelpers) {
                if (!declaredDependencies.contains(usedHelper)) {
                    fail("helper call target is missing from helperDependencies metadata: " + usedHelper);
                }
            }
        }

        private String methodReturnType() {
            return method.parsedMethod().returnType();
        }

        private void requireNonBlank(String value, String label) {
            if (value == null || value.isBlank()) {
                fail("blank " + label);
            }
        }

        private void requireExpression(GpuIrExpression expression, String label) {
            if (expression == null) {
                fail("missing " + label);
            }
        }

        private void fail(String message) {
            throw new GpuIrPassException("IR safety validation failed for " + method.irMethod().name() + ": " + message);
        }
    }

    private void validateIntrinsicTemplate(GpuIrIntrinsicCall intrinsicCall, ValidationState state) {
        String template = intrinsicCall.codeTemplate();
        int argumentCount = intrinsicCall.arguments().size();
        // Template placeholders are validated here so backend emitters can stay simple.
        if (template.contains("{this}") && intrinsicCall.receiver() == null) {
            state.fail("intrinsic template references {this} but has no receiver: " + intrinsicCall.backendName());
        }
        for (int cursor = template.indexOf('{'); cursor >= 0; cursor = template.indexOf('{', cursor + 1)) {
            int end = template.indexOf('}', cursor + 1);
            if (end < 0) {
                state.fail("intrinsic template contains an unclosed placeholder: " + template);
            }
            String placeholder = template.substring(cursor + 1, end);
            if (Objects.equals(placeholder, "this")) {
                continue;
            }
            try {
                int argumentIndex = Integer.parseInt(placeholder);
                if (argumentIndex < 0 || argumentIndex >= argumentCount) {
                    state.fail("intrinsic template references missing argument {" + argumentIndex + "}: " + intrinsicCall.backendName());
                }
            } catch (NumberFormatException exception) {
                state.fail("intrinsic template contains unsupported placeholder {" + placeholder + "}: " + intrinsicCall.backendName());
            }
        }
    }
}
