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
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;

import java.util.HashSet;
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
            state.declare(parameter.name(), "parameter");
        }
        validateHelperDependencyMetadata(context.method(), state);
        validateStatements(context.method().irMethod().statements(), state, 0, 0);
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

    private void validateStatements(List<GpuIrStatement> statements, ValidationState state, int loopDepth, int switchDepth) {
        for (GpuIrStatement statement : statements) {
            validateStatement(statement, state, loopDepth, switchDepth);
        }
    }

    private void validateStatement(GpuIrStatement statement, ValidationState state, int loopDepth, int switchDepth) {
        if (statement instanceof GpuIrVariableDeclaration declaration) {
            state.requireNonBlank(declaration.typeName(), "local declaration type for " + declaration.name());
            validateExpression(declaration.initializer(), state);
            state.declare(declaration.name(), "local");
        } else if (statement instanceof GpuIrPrivateArrayDeclaration declaration) {
            state.requireNonBlank(declaration.elementType(), "private array element type for " + declaration.name());
            validateExpression(declaration.size(), state);
            validatePrivateArraySize(declaration.size(), state);
            state.declare(declaration.name(), "private array");
        } else if (statement instanceof GpuIrAssignment assignment) {
            validateAssignmentTarget(assignment.target(), state);
            validateExpression(assignment.value(), state);
        } else if (statement instanceof GpuIrExpressionStatement expressionStatement) {
            validateExpressionStatementEffect(expressionStatement.expression(), state);
            validateExpression(expressionStatement.expression(), state);
        } else if (statement instanceof GpuIrReturn gpuReturn) {
            validateReturnShape(gpuReturn, state);
            validateExpression(gpuReturn.value(), state);
        } else if (statement instanceof GpuIrIf gpuIf) {
            validateExpression(gpuIf.condition(), state);
            // Branch-local declarations must not leak into sibling or parent scopes.
            validateStatements(gpuIf.thenBranch(), state.copy(), loopDepth, switchDepth);
            validateStatements(gpuIf.elseBranch(), state.copy(), loopDepth, switchDepth);
        } else if (statement instanceof GpuIrForLoop loop) {
            ValidationState loopState = state.copy();
            validateOptionalStatement(loop.initializer(), loopState, loopDepth, switchDepth);
            validateExpression(loop.condition(), loopState);
            validateStatements(loop.body(), loopState.copy(), loopDepth + 1, switchDepth);
            validateOptionalStatement(loop.update(), loopState, loopDepth + 1, switchDepth);
        } else if (statement instanceof GpuIrWhileLoop loop) {
            validateExpression(loop.condition(), state);
            validateStatements(loop.body(), state.copy(), loopDepth + 1, switchDepth);
        } else if (statement instanceof GpuIrDoWhileLoop loop) {
            validateStatements(loop.body(), state.copy(), loopDepth + 1, switchDepth);
            validateExpression(loop.condition(), state);
        } else if (statement instanceof GpuIrSwitch gpuSwitch) {
            validateExpression(gpuSwitch.selector(), state);
            int defaultCount = 0;
            for (GpuIrSwitchCase switchCase : gpuSwitch.cases()) {
                if (switchCase.defaultCase()) {
                    defaultCount++;
                }
                for (GpuIrExpression label : switchCase.labels()) {
                    validateExpression(label, state);
                }
                validateStatements(switchCase.statements(), state.copy(), loopDepth, switchDepth + 1);
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
            validateExpression(arrayAccess.index(), state);
        } else if (target instanceof GpuIrFieldAccess fieldAccess) {
            validateExpression(fieldAccess.target(), state);
        } else {
            state.fail("assignment target must be a variable, array element, or field access but got " + target.getClass().getSimpleName());
        }
    }

    private void validateExpression(GpuIrExpression expression, ValidationState state) {
        if (expression == null) {
            return;
        }
        if (expression instanceof GpuIrVariableRef variableRef) {
            state.requireDeclared(variableRef.name());
        } else if (expression instanceof GpuIrArrayAccess arrayAccess) {
            state.requireDeclared(arrayAccess.arrayName());
            validateExpression(arrayAccess.index(), state);
        } else if (expression instanceof GpuIrBinary binary) {
            state.requireNonBlank(binary.operator(), "binary operator");
            validateExpression(binary.left(), state);
            validateExpression(binary.right(), state);
        } else if (expression instanceof GpuIrUnary unary) {
            state.requireNonBlank(unary.operator(), "unary operator");
            validateExpression(unary.operand(), state);
        } else if (expression instanceof GpuIrTernary ternary) {
            validateExpression(ternary.condition(), state);
            validateExpression(ternary.whenTrue(), state);
            validateExpression(ternary.whenFalse(), state);
        } else if (expression instanceof GpuIrCast cast) {
            state.requireNonBlank(cast.targetType(), "cast target type");
            validateExpression(cast.expression(), state);
        } else if (expression instanceof GpuIrFieldAccess fieldAccess) {
            state.requireNonBlank(fieldAccess.fieldName(), "field access name");
            validateExpression(fieldAccess.target(), state);
        } else if (expression instanceof GpuIrHelperCall helperCall) {
            state.requireKnownHelper(helperCall.helperName());
            state.requireNonBlank(helperCall.resultType(), "helper call result type for " + helperCall.helperName());
            validateHelperPurityMetadata(helperCall, state);
            state.recordUsedHelper(helperCall.helperName());
            helperCall.arguments().forEach(argument -> validateExpression(argument, state));
        } else if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
            state.requireNonBlank(intrinsicCall.backendName(), "intrinsic backend name");
            state.requireNonBlank(intrinsicCall.codeTemplate(), "intrinsic code template");
            state.requireNonBlank(intrinsicCall.resultType(), "intrinsic result type");
            validateIntrinsicTemplate(intrinsicCall, state);
            validateExpression(intrinsicCall.receiver(), state);
            intrinsicCall.arguments().forEach(argument -> validateExpression(argument, state));
        } else if (expression instanceof GpuIrStructInit structInit) {
            state.requireNonBlank(structInit.structType(), "struct initializer type");
            structInit.arguments().forEach(argument -> validateExpression(argument, state));
        } else if (expression instanceof GpuIrLiteral) {
            return;
        } else {
            state.fail("unknown IR expression type: " + expression.getClass().getName());
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
        private final Set<String> usedHelpers;

        private ValidationState(GpuIrCompiledMethod method, Map<String, GpuIrCompiledMethod> helperMethodsByName) {
            this(method, helperMethodsByName, new HashSet<>(), new HashSet<>());
        }

        private ValidationState(
                GpuIrCompiledMethod method,
                Map<String, GpuIrCompiledMethod> helperMethodsByName,
                Set<String> declaredNames,
                Set<String> usedHelpers
        ) {
            this.method = method;
            this.helperMethodsByName = helperMethodsByName;
            this.declaredNames = declaredNames;
            this.usedHelpers = usedHelpers;
        }

        private ValidationState copy() {
            // Declarations are scoped, but helper usage is method-wide metadata.
            return new ValidationState(method, helperMethodsByName, new HashSet<>(declaredNames), usedHelpers);
        }

        private void declare(String name, String kind) {
            if (name == null || name.isBlank()) {
                fail("blank " + kind + " name");
            }
            if (!declaredNames.add(name)) {
                fail("duplicate " + kind + " declaration: " + name);
            }
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
