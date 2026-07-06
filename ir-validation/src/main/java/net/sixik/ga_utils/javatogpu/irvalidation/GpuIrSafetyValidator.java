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
    private static final Set<String> PARAMETER_QUALIFIERS = Set.of("const", "restrict", "volatile");

    private final GpuIrExpressionClassifier expressionClassifier;

    public GpuIrSafetyValidator() {
        this(new GpuIrExpressionClassifier());
    }

    public GpuIrSafetyValidator(GpuIrExpressionClassifier expressionClassifier) {
        this.expressionClassifier = Objects.requireNonNull(expressionClassifier, "expressionClassifier");
    }

    @Override
    public void run(GpuIrPassContext context) {
        if (context == null) {
            throw new GpuIrPassException("IR safety validation failed: missing pass context");
        }
        if (context.method() == null) {
            throw new GpuIrPassException("IR safety validation failed: missing compiled method metadata");
        }
        validateCompiledMethodEnvelope(context.method(), "compiled method");
        validateHelperMethodMetadata(context);
        ValidationState state = new ValidationState(context.method(), helperMethodsByName(context));
        validateMethodMetadata(context.method(), state);
        List<ParsedGpuParameter> parameters = context.method().parsedMethod().parameters();
        if (parameters == null) {
            state.fail("missing parameter metadata list");
        }
        for (ParsedGpuParameter parameter : parameters) {
            if (parameter == null) {
                state.fail("null parameter metadata");
            }
            state.requireNonBlank(parameter.javaType(), "parameter type for " + parameter.name());
            validateParameterMetadata(parameter, context.entryPoint(), state);
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
        validateSupportedMethodReturnType(method.parsedMethod().returnType(), state);
    }

    private void validateCompiledMethodEnvelope(GpuIrCompiledMethod method, String label) {
        if (method.irMethod() == null) {
            throw new GpuIrPassException("IR safety validation failed: missing IR method metadata for " + label);
        }
        if (method.parsedMethod() == null) {
            throw new GpuIrPassException("IR safety validation failed for "
                    + method.irMethod().name() + ": missing parsed method metadata for " + label);
        }
    }

    private void validateSupportedMethodReturnType(String returnType, ValidationState state) {
        String normalizedType = GpuTypeSupport.declaredType(returnType);
        if (normalizedType == null || Objects.equals("void", normalizedType)) {
            return;
        }
        if (!GpuTypeSupport.isSupportedLocalType(normalizedType)
                && !GpuTypeSupport.isSupportedVectorType(normalizedType)) {
            state.fail("unsupported method return type: " + normalizedType);
        }
    }

    private Map<String, GpuIrCompiledMethod> helperMethodsByName(GpuIrPassContext context) {
        return context.helperMethods().stream()
                .collect(Collectors.toMap(
                        GpuIrCompiledMethod::emittedName,
                        helper -> helper,
                        (left, right) -> left
                ));
    }

    private void validateHelperMethodMetadata(GpuIrPassContext context) {
        Set<String> seenEmittedNames = new HashSet<>();
        for (GpuIrCompiledMethod helper : context.helperMethods()) {
            if (helper == null) {
                throw new GpuIrPassException("IR safety validation failed for "
                        + context.method().irMethod().name() + ": null helper method metadata");
            }
            validateCompiledMethodEnvelope(helper, "helper " + helper.emittedName());
            if (helper.emittedName() == null || helper.emittedName().isBlank()) {
                throw new GpuIrPassException("IR safety validation failed for "
                        + context.method().irMethod().name() + ": blank helper emitted method name");
            }
            if (!seenEmittedNames.add(helper.emittedName())) {
                throw new GpuIrPassException("IR safety validation failed for "
                        + context.method().irMethod().name() + ": duplicate helper emitted method name: " + helper.emittedName());
            }
            validateHelperSignatureMetadata(context.method(), helper);
        }
    }

    private void validateHelperSignatureMetadata(GpuIrCompiledMethod method, GpuIrCompiledMethod helper) {
        String returnType = GpuTypeSupport.declaredType(helper.parsedMethod().returnType());
        if (returnType == null || returnType.isBlank()) {
            throw new GpuIrPassException("IR safety validation failed for "
                    + method.irMethod().name() + ": blank helper return type for " + helper.emittedName());
        }
        if (!Objects.equals("void", returnType)
                && !GpuTypeSupport.isSupportedLocalType(returnType)
                && !GpuTypeSupport.isSupportedVectorType(returnType)) {
            throw new GpuIrPassException("IR safety validation failed for "
                    + method.irMethod().name() + ": unsupported helper return type for " + helper.emittedName() + ": " + returnType);
        }
        List<ParsedGpuParameter> parameters = helper.parsedMethod().parameters();
        if (parameters == null) {
            throw new GpuIrPassException("IR safety validation failed for "
                    + method.irMethod().name() + ": missing helper parameter metadata list for " + helper.emittedName());
        }
        Set<String> seenParameterNames = new HashSet<>();
        for (ParsedGpuParameter parameter : parameters) {
            if (parameter == null) {
                throw new GpuIrPassException("IR safety validation failed for "
                        + method.irMethod().name() + ": null helper parameter metadata for " + helper.emittedName());
            }
            String parameterName = parameter.name();
            if (parameterName == null || parameterName.isBlank()) {
                throw new GpuIrPassException("IR safety validation failed for "
                        + method.irMethod().name() + ": blank helper parameter name for " + helper.emittedName());
            }
            if (!seenParameterNames.add(parameterName)) {
                throw new GpuIrPassException("IR safety validation failed for "
                        + method.irMethod().name() + ": duplicate helper parameter name for "
                        + helper.emittedName() + ": " + parameterName);
            }
            if (parameter.addressSpace() == null) {
                throw new GpuIrPassException("IR safety validation failed for "
                        + method.irMethod().name() + ": missing helper parameter address space for "
                        + helper.emittedName() + ": " + parameterName);
            }
            String parameterType = GpuTypeSupport.declaredType(parameter.javaType());
            if (parameterType == null || parameterType.isBlank()) {
                throw new GpuIrPassException("IR safety validation failed for "
                        + method.irMethod().name() + ": blank helper parameter type for "
                        + helper.emittedName() + ": " + parameterName);
            }
            if (!GpuTypeSupport.isSupportedHelperParameterType(parameterType)
                    && !GpuTypeSupport.isSupportedLocalType(parameterType)) {
                throw new GpuIrPassException("IR safety validation failed for "
                        + method.irMethod().name() + ": unsupported helper parameter type for "
                        + helper.emittedName() + ": " + parameterName + " is " + parameterType);
            }
            validateHelperParameterQualifiers(method, helper, parameter, parameterName, parameterType);
        }
    }

    private void validateHelperParameterQualifiers(
            GpuIrCompiledMethod method,
            GpuIrCompiledMethod helper,
            ParsedGpuParameter parameter,
            String parameterName,
            String parameterType
    ) {
        List<String> qualifiers = parameter.openClQualifiers();
        if (qualifiers == null || qualifiers.isEmpty()) {
            return;
        }
        if (!GpuTypeSupport.isSupportedPointerType(parameterType) && !GpuTypeSupport.isArrayType(parameterType)) {
            throw new GpuIrPassException("IR safety validation failed for "
                    + method.irMethod().name() + ": OpenCL qualifiers require a pointer-like helper parameter for "
                    + helper.emittedName() + ": " + parameterName + " is " + parameterType);
        }
        Set<String> seenQualifiers = new HashSet<>();
        for (String qualifier : qualifiers) {
            if (qualifier == null || qualifier.isBlank()) {
                throw new GpuIrPassException("IR safety validation failed for "
                        + method.irMethod().name() + ": blank OpenCL helper parameter qualifier for "
                        + helper.emittedName() + ": " + parameterName);
            }
            if (!PARAMETER_QUALIFIERS.contains(qualifier)) {
                throw new GpuIrPassException("IR safety validation failed for "
                        + method.irMethod().name() + ": unsupported OpenCL helper parameter qualifier for "
                        + helper.emittedName() + ": " + parameterName + " is " + qualifier);
            }
            if (!seenQualifiers.add(qualifier)) {
                throw new GpuIrPassException("IR safety validation failed for "
                        + method.irMethod().name() + ": duplicate OpenCL helper parameter qualifier for "
                        + helper.emittedName() + ": " + parameterName + " is " + qualifier);
            }
        }
    }

    private StorageAccess storageAccess(ParsedGpuParameter parameter) {
        if (parameter.addressSpace() == GpuAddressSpace.CONSTANT || parameter.constant()) {
            return StorageAccess.READ_ONLY;
        }
        return StorageAccess.WRITABLE;
    }

    private void validateParameterMetadata(ParsedGpuParameter parameter, boolean entryPoint, ValidationState state) {
        String type = GpuTypeSupport.declaredType(parameter.javaType());
        if (type == null) {
            return;
        }
        if (parameter.addressSpace() == null) {
            state.fail("missing " + (entryPoint ? "entry-point" : "helper") + " parameter address space for "
                    + parameter.name());
        }
        validateParameterQualifiers(parameter, type, state);
        boolean supported = entryPoint
                ? isSupportedEntryPointParameterType(type)
                : GpuTypeSupport.isSupportedHelperParameterType(type);
        if (!supported) {
            state.fail("unsupported " + (entryPoint ? "entry-point" : "helper") + " parameter type for "
                    + parameter.name() + ": " + type);
        }
        if ((parameter.addressSpace() == GpuAddressSpace.GLOBAL
                || parameter.addressSpace() == GpuAddressSpace.CONSTANT
                || parameter.addressSpace() == GpuAddressSpace.LOCAL)
                && !GpuTypeSupport.isArrayType(type)) {
            state.fail(parameter.addressSpace() + " parameter must be an array type: " + parameter.name() + " is " + type);
        }
        if (parameter.addressSpace() == GpuAddressSpace.PRIVATE && GpuTypeSupport.isArrayType(type)) {
            state.fail("array parameter must use an explicit GPU address space: " + parameter.name() + " is " + type);
        }
    }

    private void validateParameterQualifiers(ParsedGpuParameter parameter, String type, ValidationState state) {
        List<String> qualifiers = parameter.openClQualifiers();
        if (qualifiers == null || qualifiers.isEmpty()) {
            return;
        }
        if (!GpuTypeSupport.isSupportedPointerType(type) && !GpuTypeSupport.isArrayType(type)) {
            state.fail("OpenCL qualifiers require a pointer-like parameter: " + parameter.name() + " is " + type);
        }
        Set<String> seenQualifiers = new HashSet<>();
        for (String qualifier : qualifiers) {
            if (qualifier == null || qualifier.isBlank()) {
                state.fail("blank OpenCL parameter qualifier for " + parameter.name());
            }
            if (!PARAMETER_QUALIFIERS.contains(qualifier)) {
                state.fail("unsupported OpenCL parameter qualifier for " + parameter.name() + ": " + qualifier);
            }
            if (!seenQualifiers.add(qualifier)) {
                state.fail("duplicate OpenCL parameter qualifier for " + parameter.name() + ": " + qualifier);
            }
        }
    }

    private boolean isSupportedEntryPointParameterType(String type) {
        if (GpuTypeSupport.isSupportedKernelParameterType(type) || GpuTypeSupport.isSupportedVectorType(type)) {
            return true;
        }
        if (!GpuTypeSupport.isArrayType(type)) {
            return false;
        }
        String componentType = GpuTypeSupport.componentType(type);
        return GpuTypeSupport.isSupportedVectorType(componentType);
    }

    private enum StorageAccess {
        WRITABLE,
        READ_ONLY
    }

    private void validateHelperDependencyMetadata(GpuIrCompiledMethod method, ValidationState state) {
        if (method.helperDependencies() == null) {
            state.fail("missing helper dependency metadata list");
        }
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
            validateSupportedLocalDeclarationType(declaration.typeName(), "local declaration type for " + declaration.name(), state);
            state.declare(declaration.name(), "local", declaration.typeName(), StorageAccess.WRITABLE);
        } else if (statement instanceof GpuIrPrivateArrayDeclaration declaration) {
            state.requireNonBlank(declaration.elementType(), "private array element type for " + declaration.name());
            validateSupportedPrivateArrayElementType(declaration.elementType(), declaration.name(), state);
            state.requireExpression(declaration.size(), "private array size");
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
            validateBooleanCondition(gpuIf.condition(), "if condition", state);
            // Branch-local declarations must not leak into sibling or parent scopes.
            validateStatements(gpuIf.thenBranch(), state.copy(), loopDepth, switchDepth, "if then branch");
            validateStatements(gpuIf.elseBranch(), state.copy(), loopDepth, switchDepth, "if else branch");
        } else if (statement instanceof GpuIrForLoop loop) {
            ValidationState loopState = state.copy();
            validateOptionalStatement(loop.initializer(), loopState, loopDepth, switchDepth);
            state.requireExpression(loop.condition(), "for loop condition");
            validateExpression(loop.condition(), loopState);
            validateBooleanCondition(loop.condition(), "for loop condition", loopState);
            validateStatements(loop.body(), loopState.copy(), loopDepth + 1, switchDepth, "for loop body");
            validateOptionalStatement(loop.update(), loopState, loopDepth + 1, switchDepth);
        } else if (statement instanceof GpuIrWhileLoop loop) {
            state.requireExpression(loop.condition(), "while loop condition");
            validateExpression(loop.condition(), state);
            validateBooleanCondition(loop.condition(), "while loop condition", state);
            validateStatements(loop.body(), state.copy(), loopDepth + 1, switchDepth, "while loop body");
        } else if (statement instanceof GpuIrDoWhileLoop loop) {
            validateStatements(loop.body(), state.copy(), loopDepth + 1, switchDepth, "do-while loop body");
            state.requireExpression(loop.condition(), "do-while loop condition");
            validateExpression(loop.condition(), state);
            validateBooleanCondition(loop.condition(), "do-while loop condition", state);
        } else if (statement instanceof GpuIrSwitch gpuSwitch) {
            state.requireExpression(gpuSwitch.selector(), "switch selector");
            validateExpression(gpuSwitch.selector(), state);
            validateIntegralSwitchSelector(gpuSwitch.selector(), state);
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
                    validateSwitchCaseLabel(gpuSwitch.selector(), label, state);
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

    private void validateIntegralSwitchSelector(GpuIrExpression selector, ValidationState state) {
        String selectorType = expressionType(selector, state);
        if (selectorType == null) {
            return;
        }
        String normalizedType = GpuTypeSupport.declaredType(selectorType);
        if (normalizedType != null && !GpuTypeSupport.isIntegralScalarType(normalizedType)) {
            state.fail("switch selector must be an integral scalar type but got " + normalizedType);
        }
    }

    private void validateSwitchCaseLabel(GpuIrExpression selector, GpuIrExpression label, ValidationState state) {
        String selectorType = expressionType(selector, state);
        String labelType = expressionType(label, state);
        if (selectorType == null || labelType == null) {
            return;
        }
        String normalizedSelector = GpuTypeSupport.declaredType(selectorType);
        String normalizedLabel = GpuTypeSupport.declaredType(labelType);
        if (normalizedSelector == null || normalizedLabel == null) {
            return;
        }
        if (!GpuTypeSupport.isIntegralScalarType(normalizedLabel)) {
            state.fail("switch case label must be an integral scalar type but got " + normalizedLabel);
        }
        if (GpuTypeSupport.isIntegralScalarType(normalizedSelector)
                && !GpuTypeSupport.isHelperArgumentCompatible(normalizedLabel, normalizedSelector)) {
            state.fail("switch case label type mismatch: selector is " + normalizedSelector + " but label is " + normalizedLabel);
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

    private void validateSupportedLocalDeclarationType(String typeName, String location, ValidationState state) {
        String normalizedType = GpuTypeSupport.declaredType(typeName);
        if (normalizedType == null) {
            return;
        }
        if (!GpuTypeSupport.isSupportedLocalType(normalizedType)
                && !GpuTypeSupport.isSupportedVectorType(normalizedType)) {
            state.fail("unsupported " + location + ": " + normalizedType);
        }
    }

    private void validateSupportedPrivateArrayElementType(String elementType, String arrayName, ValidationState state) {
        String normalizedType = GpuTypeSupport.declaredType(elementType);
        if (normalizedType == null) {
            return;
        }
        if (!GpuTypeSupport.isSupportedScalarType(normalizedType) && !GpuTypeSupport.isSupportedVectorType(normalizedType)) {
            state.fail("unsupported private array element type for " + arrayName + ": " + normalizedType);
        }
    }

    private void validateAssignmentTarget(GpuIrExpression target, ValidationState state) {
        if (target instanceof GpuIrVariableRef variableRef) {
            state.requireDeclared(variableRef.name());
        } else if (target instanceof GpuIrArrayAccess arrayAccess) {
            state.requireDeclared(arrayAccess.arrayName());
            state.requireWritable(arrayAccess.arrayName(), "array assignment target");
            validateArrayAccessTarget(arrayAccess, state);
            state.requireExpression(arrayAccess.index(), "array access index");
            validateExpression(arrayAccess.index(), state);
            validateIntegralIndex(arrayAccess.index(), "array access index for " + arrayAccess.arrayName(), state);
        } else if (target instanceof GpuIrFieldAccess fieldAccess) {
            state.requireNonBlank(fieldAccess.fieldName(), "field access name");
            state.requireExpression(fieldAccess.target(), "field access target");
            validateExpression(fieldAccess.target(), state);
            validateKnownVectorFieldAccess(fieldAccess, state);
        } else {
            state.fail("assignment target must be a variable, array element, or field access but got " + target.getClass().getSimpleName());
        }
    }

    private void validateBooleanCondition(GpuIrExpression condition, String location, ValidationState state) {
        String conditionType = expressionType(condition, state);
        if (conditionType == null) {
            return;
        }
        String normalizedType = GpuTypeSupport.declaredType(conditionType);
        if (normalizedType != null && !Objects.equals("boolean", normalizedType)) {
            state.fail("condition type mismatch in " + location + ": expected boolean but got " + normalizedType);
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
            validateArrayAccessTarget(arrayAccess, state);
            state.requireExpression(arrayAccess.index(), "array access index");
            validateExpression(arrayAccess.index(), state);
            validateIntegralIndex(arrayAccess.index(), "array access index for " + arrayAccess.arrayName(), state);
        } else if (expression instanceof GpuIrBinary binary) {
            state.requireNonBlank(binary.operator(), "binary operator");
            state.requireExpression(binary.left(), "binary left operand");
            state.requireExpression(binary.right(), "binary right operand");
            validateExpression(binary.left(), state);
            validateExpression(binary.right(), state);
            validateBinaryOperatorTypes(binary, state);
        } else if (expression instanceof GpuIrUnary unary) {
            state.requireNonBlank(unary.operator(), "unary operator");
            state.requireExpression(unary.operand(), "unary operand");
            validateExpression(unary.operand(), state);
            validateUnaryOperatorType(unary, state);
        } else if (expression instanceof GpuIrTernary ternary) {
            state.requireExpression(ternary.condition(), "ternary condition");
            state.requireExpression(ternary.whenTrue(), "ternary true branch");
            state.requireExpression(ternary.whenFalse(), "ternary false branch");
            validateExpression(ternary.condition(), state);
            validateBooleanCondition(ternary.condition(), "ternary condition", state);
            validateExpression(ternary.whenTrue(), state);
            validateExpression(ternary.whenFalse(), state);
            validateTernaryBranchTypes(ternary, state);
        } else if (expression instanceof GpuIrCast cast) {
            state.requireNonBlank(cast.targetType(), "cast target type");
            state.requireExpression(cast.expression(), "cast expression");
            validateExpression(cast.expression(), state);
            validateScalarCast(cast, state);
        } else if (expression instanceof GpuIrFieldAccess fieldAccess) {
            state.requireNonBlank(fieldAccess.fieldName(), "field access name");
            state.requireExpression(fieldAccess.target(), "field access target");
            validateExpression(fieldAccess.target(), state);
            validateKnownVectorFieldAccess(fieldAccess, state);
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
            validateSupportedIntrinsicResultType(intrinsicCall, state);
            validateExpressionList(intrinsicCall.arguments(), state, "intrinsic argument");
            validateIntrinsicArgumentMetadata(intrinsicCall, state);
            validateIntrinsicTemplate(intrinsicCall, state);
            validateExpression(intrinsicCall.receiver(), state);
        } else if (expression instanceof GpuIrStructInit structInit) {
            state.requireNonBlank(structInit.structType(), "struct initializer type");
            validateExpressionList(structInit.arguments(), state, "struct initializer argument");
            validateKnownVectorInitializer(structInit, state);
        } else if (expression instanceof GpuIrLiteral) {
            return;
        } else {
            state.fail("unknown IR expression type: " + expression.getClass().getName());
        }
    }

    private void validateArrayAccessTarget(GpuIrArrayAccess arrayAccess, ValidationState state) {
        String arrayType = state.declaredType(arrayAccess.arrayName());
        String normalizedType = GpuTypeSupport.declaredType(arrayType);
        if (normalizedType != null && !GpuTypeSupport.isArrayType(normalizedType)) {
            state.fail("array access target must be an array type: " + arrayAccess.arrayName() + " is " + normalizedType);
        }
    }

    private void validateKnownVectorInitializer(GpuIrStructInit structInit, ValidationState state) {
        String vectorType = GpuTypeSupport.declaredType(structInit.structType());
        if (!GpuTypeSupport.isSupportedVectorType(vectorType)) {
            return;
        }
        int argumentCount = structInit.arguments().size();
        int width = GpuTypeSupport.vectorWidth(vectorType);
        if (argumentCount != 0 && argumentCount != 1 && argumentCount != width) {
            state.fail("vector initializer argument count mismatch for " + vectorType
                    + ": expected 0, 1 or " + width + " but got " + argumentCount);
        }
        if (argumentCount == 0) {
            return;
        }
        String componentType = GpuTypeSupport.vectorComponentType(vectorType);
        if (argumentCount == 1) {
            String argumentType = expressionType(structInit.arguments().get(0), state);
            if (argumentType == null) {
                return;
            }
            if (GpuTypeSupport.isSupportedVectorType(argumentType)) {
                if (!GpuTypeSupport.isHelperArgumentCompatible(argumentType, vectorType)) {
                    state.fail("vector initializer argument type mismatch for " + vectorType
                            + ": expected " + vectorType + " but got " + GpuTypeSupport.declaredType(argumentType));
                }
                return;
            }
            validateAssignableType(componentType, argumentType, "vector initializer argument for " + vectorType, state);
            return;
        }
        for (GpuIrExpression argument : structInit.arguments()) {
            validateAssignableType(componentType, expressionType(argument, state), "vector initializer argument for " + vectorType, state);
        }
    }

    private void validateBinaryOperatorTypes(GpuIrBinary binary, ValidationState state) {
        String leftType = expressionType(binary.left(), state);
        String rightType = expressionType(binary.right(), state);
        if (leftType == null || rightType == null) {
            return;
        }
        String operator = binary.operator();
        if (Objects.equals("&&", operator) || Objects.equals("||", operator)) {
            validateBooleanOperand(leftType, "left", operator, state);
            validateBooleanOperand(rightType, "right", operator, state);
            return;
        }
        if (Objects.equals("&", operator) || Objects.equals("|", operator) || Objects.equals("^", operator)
                || Objects.equals("<<", operator) || Objects.equals(">>", operator)) {
            validateIntegralOperand(leftType, "left", operator, state);
            validateIntegralOperand(rightType, "right", operator, state);
            return;
        }
        if (Objects.equals("<", operator) || Objects.equals("<=", operator)
                || Objects.equals(">", operator) || Objects.equals(">=", operator)) {
            validateNumericOperand(leftType, "left", operator, state);
            validateNumericOperand(rightType, "right", operator, state);
            return;
        }
        if (Objects.equals("==", operator) || Objects.equals("!=", operator)) {
            validateComparableOperandPair(leftType, rightType, operator, state);
            return;
        }
        if (Objects.equals("+", operator) || Objects.equals("-", operator) || Objects.equals("*", operator)
                || Objects.equals("/", operator) || Objects.equals("%", operator)) {
            validateNumericOperand(leftType, "left", operator, state);
            validateNumericOperand(rightType, "right", operator, state);
            return;
        }
        state.fail("unsupported binary operator: " + operator);
    }

    private void validateTernaryBranchTypes(GpuIrTernary ternary, ValidationState state) {
        String trueType = expressionType(ternary.whenTrue(), state);
        String falseType = expressionType(ternary.whenFalse(), state);
        if (trueType == null || falseType == null) {
            return;
        }
        String normalizedTrue = GpuTypeSupport.declaredType(trueType);
        String normalizedFalse = GpuTypeSupport.declaredType(falseType);
        if (normalizedTrue == null || normalizedFalse == null || Objects.equals(normalizedTrue, normalizedFalse)) {
            return;
        }
        if (GpuTypeSupport.isSupportedVectorType(normalizedTrue) || GpuTypeSupport.isSupportedVectorType(normalizedFalse)) {
            if (!GpuTypeSupport.isHelperArgumentCompatible(normalizedTrue, normalizedFalse)
                    && !GpuTypeSupport.isHelperArgumentCompatible(normalizedFalse, normalizedTrue)) {
                state.fail("ternary branch type mismatch: true branch is " + normalizedTrue
                        + " but false branch is " + normalizedFalse);
            }
            return;
        }
        boolean trueBoolean = Objects.equals("boolean", normalizedTrue);
        boolean falseBoolean = Objects.equals("boolean", normalizedFalse);
        if (trueBoolean != falseBoolean) {
            state.fail("ternary branch type mismatch: cannot mix boolean and numeric branches: "
                    + normalizedTrue + " and " + normalizedFalse);
        }
        if (!GpuTypeSupport.isSupportedScalarType(normalizedTrue) || !GpuTypeSupport.isSupportedScalarType(normalizedFalse)) {
            state.fail("ternary branch type mismatch: branches must be supported scalar or compatible vector types but got "
                    + normalizedTrue + " and " + normalizedFalse);
        }
    }

    private void validateUnaryOperatorType(GpuIrUnary unary, ValidationState state) {
        String operandType = expressionType(unary.operand(), state);
        if (operandType == null) {
            return;
        }
        String operator = unary.operator();
        if (Objects.equals("!", operator)) {
            validateBooleanOperand(operandType, "", operator, state);
        } else if (Objects.equals("~", operator)) {
            validateIntegralOperand(operandType, "", operator, state);
        } else if (Objects.equals("-", operator) || Objects.equals("+", operator)) {
            validateNumericOperand(operandType, "", operator, state);
        } else {
            state.fail("unsupported unary operator: " + operator);
        }
    }

    private void validateBooleanOperand(String operandType, String side, String operator, ValidationState state) {
        String normalizedType = GpuTypeSupport.declaredType(operandType);
        if (normalizedType != null && !Objects.equals("boolean", normalizedType)) {
            state.fail("operator " + operator + " requires boolean " + operandLabel(side) + "operand but got " + normalizedType);
        }
    }

    private void validateIntegralOperand(String operandType, String side, String operator, ValidationState state) {
        String normalizedType = GpuTypeSupport.declaredType(operandType);
        if (normalizedType != null && !GpuTypeSupport.isIntegralScalarType(normalizedType)) {
            state.fail("operator " + operator + " requires integral " + operandLabel(side) + "operand but got " + normalizedType);
        }
    }

    private void validateNumericOperand(String operandType, String side, String operator, ValidationState state) {
        String normalizedType = GpuTypeSupport.declaredType(operandType);
        if (normalizedType != null && (!GpuTypeSupport.isSupportedScalarType(normalizedType) || Objects.equals("boolean", normalizedType))) {
            state.fail("operator " + operator + " requires numeric " + operandLabel(side) + "operand but got " + normalizedType);
        }
    }

    private void validateComparableOperandPair(String leftType, String rightType, String operator, ValidationState state) {
        String normalizedLeft = GpuTypeSupport.declaredType(leftType);
        String normalizedRight = GpuTypeSupport.declaredType(rightType);
        if (normalizedLeft == null || normalizedRight == null) {
            return;
        }
        if (!GpuTypeSupport.isSupportedScalarType(normalizedLeft) || !GpuTypeSupport.isSupportedScalarType(normalizedRight)) {
            state.fail("operator " + operator + " requires comparable scalar operands but got "
                    + normalizedLeft + " and " + normalizedRight);
        }
        boolean leftBoolean = Objects.equals("boolean", normalizedLeft);
        boolean rightBoolean = Objects.equals("boolean", normalizedRight);
        if (leftBoolean != rightBoolean) {
            state.fail("operator " + operator + " cannot compare boolean and numeric operands: "
                    + normalizedLeft + " and " + normalizedRight);
        }
    }

    private String operandLabel(String side) {
        return side == null || side.isBlank() ? "" : side + " ";
    }

    private void validateScalarCast(GpuIrCast cast, ValidationState state) {
        if (!GpuTypeSupport.isSupportedScalarType(cast.targetType())) {
            state.fail("unsupported cast target type: " + cast.targetType());
        }
        String sourceType = expressionType(cast.expression(), state);
        if (sourceType != null && !GpuTypeSupport.isSupportedScalarType(sourceType)) {
            state.fail("cast source must be a supported scalar type but got " + GpuTypeSupport.declaredType(sourceType));
        }
    }

    private void validateIntegralIndex(GpuIrExpression index, String location, ValidationState state) {
        String indexType = expressionType(index, state);
        if (indexType == null) {
            return;
        }
        String normalizedType = GpuTypeSupport.declaredType(indexType);
        if (normalizedType != null && !GpuTypeSupport.isIntegralScalarType(normalizedType)) {
            state.fail(location + " must be an integral scalar type but got " + normalizedType);
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
        if (expression instanceof GpuIrFieldAccess fieldAccess) {
            String targetType = expressionType(fieldAccess.target(), state);
            if (GpuTypeSupport.isSupportedVectorType(targetType)) {
                return GpuTypeSupport.vectorComponentType(targetType, fieldAccess.fieldName());
            }
        }
        return null;
    }

    private void validateKnownVectorFieldAccess(GpuIrFieldAccess fieldAccess, ValidationState state) {
        String targetType = expressionType(fieldAccess.target(), state);
        if (!GpuTypeSupport.isSupportedVectorType(targetType)) {
            return;
        }
        String componentType = GpuTypeSupport.vectorComponentType(targetType, fieldAccess.fieldName());
        if (componentType == null) {
            state.fail("unknown vector field " + fieldAccess.fieldName() + " for type " + GpuTypeSupport.declaredType(targetType));
        }
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
        String helperReturnType = GpuTypeSupport.declaredType(helper.parsedMethod().returnType());
        String callResultType = GpuTypeSupport.declaredType(helperCall.resultType());
        boolean helperReturnsVoid = Objects.equals("void", helper.parsedMethod().returnType());
        if (helperReturnsVoid && !Objects.equals("void", helperCall.resultType())) {
            state.fail("void helper call must use void result metadata: " + helperCall.helperName());
        }
        if (helperReturnType != null && callResultType != null && !sameDeclaredResultType(helperReturnType, callResultType)) {
            state.fail("helper call result type mismatch for " + helperCall.helperName()
                    + ": expected " + helperReturnType + " but got " + callResultType);
        }
    }

    private boolean sameDeclaredResultType(String expectedType, String actualType) {
        if (Objects.equals(expectedType, actualType)) {
            return true;
        }
        if (GpuTypeSupport.isSupportedVectorType(expectedType) && GpuTypeSupport.isSupportedVectorType(actualType)) {
            return Objects.equals(GpuTypeSupport.openClVectorTypeName(expectedType), GpuTypeSupport.openClVectorTypeName(actualType));
        }
        return false;
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
            String location = "mutable helper argument " + parameter.name() + " for " + helperCall.helperName();
            if (storageName == null) {
                // Mutating helper parameters must be tied to concrete storage so read-only and alias guards can reason about them.
                state.fail(location + " must reference declared storage directly");
            } else {
                state.requireWritable(storageName, location);
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
        validateIntegralIndex(size, "private array size", state);
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
            if (method.helperDependencies() == null) {
                fail("missing helper dependency metadata list");
            }
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
                if (argumentIndex < 0) {
                    state.fail("intrinsic template references negative argument index {" + argumentIndex + "}: " + intrinsicCall.backendName());
                }
                if (argumentIndex >= argumentCount) {
                    state.fail("intrinsic template references missing argument {" + argumentIndex + "}: " + intrinsicCall.backendName());
                }
            } catch (NumberFormatException exception) {
                state.fail("intrinsic template contains unsupported placeholder {" + placeholder + "}: " + intrinsicCall.backendName());
            }
        }
    }

    private void validateIntrinsicArgumentMetadata(GpuIrIntrinsicCall intrinsicCall, ValidationState state) {
        if (intrinsicCall.argumentTypes().isEmpty()) {
            return;
        }
        if (intrinsicCall.argumentTypes().size() != intrinsicCall.arguments().size()) {
            state.fail("intrinsic argument metadata count mismatch for " + intrinsicCall.backendName()
                    + ": expected " + intrinsicCall.argumentTypes().size() + " but got " + intrinsicCall.arguments().size());
        }
        for (int index = 0; index < intrinsicCall.argumentTypes().size(); index++) {
            String expectedType = intrinsicCall.argumentTypes().get(index);
            if (expectedType == null) {
                state.fail("null intrinsic argument " + index + " metadata type for " + intrinsicCall.backendName());
            }
            state.requireNonBlank(expectedType, "intrinsic argument " + index + " type for " + intrinsicCall.backendName());
            validateSupportedIntrinsicArgumentType(expectedType, index, intrinsicCall.backendName(), state);
            validateAssignableType(
                    expectedType,
                    expressionType(intrinsicCall.arguments().get(index), state),
                    "intrinsic argument " + index + " for " + intrinsicCall.backendName(),
                    state
            );
        }
    }

    private void validateSupportedIntrinsicArgumentType(String expectedType, int index, String backendName, ValidationState state) {
        String normalizedType = GpuTypeSupport.declaredType(expectedType);
        if (normalizedType == null) {
            return;
        }
        if (!GpuTypeSupport.isSupportedScalarType(normalizedType)
                && !GpuTypeSupport.isSupportedVectorType(normalizedType)
                && !GpuTypeSupport.isSupportedPointerType(normalizedType)
                && !GpuTypeSupport.isArrayType(normalizedType)) {
            state.fail("unsupported intrinsic argument " + index + " metadata type for " + backendName + ": " + normalizedType);
        }
    }

    private void validateSupportedIntrinsicResultType(GpuIrIntrinsicCall intrinsicCall, ValidationState state) {
        String normalizedType = GpuTypeSupport.declaredType(intrinsicCall.resultType());
        if (normalizedType == null || Objects.equals("void", normalizedType)) {
            return;
        }
        if (!GpuTypeSupport.isSupportedScalarType(normalizedType)
                && !GpuTypeSupport.isSupportedVectorType(normalizedType)
                && !GpuTypeSupport.isSupportedPointerType(normalizedType)
                && !GpuTypeSupport.isArrayType(normalizedType)) {
            state.fail("unsupported intrinsic result metadata type for " + intrinsicCall.backendName() + ": " + normalizedType);
        }
    }
}
