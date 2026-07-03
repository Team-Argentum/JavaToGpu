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
import java.util.List;
import java.util.Set;

public final class GpuIrSafetyValidator implements GpuIrPass {
    @Override
    public void run(GpuIrPassContext context) {
        ValidationState state = new ValidationState(context.method());
        for (ParsedGpuParameter parameter : context.method().parsedMethod().parameters()) {
            state.declare(parameter.name(), "parameter");
        }
        validateStatements(context.method().irMethod().statements(), state, 0, 0);
    }

    private void validateStatements(List<GpuIrStatement> statements, ValidationState state, int loopDepth, int switchDepth) {
        for (GpuIrStatement statement : statements) {
            validateStatement(statement, state, loopDepth, switchDepth);
        }
    }

    private void validateStatement(GpuIrStatement statement, ValidationState state, int loopDepth, int switchDepth) {
        if (statement instanceof GpuIrVariableDeclaration declaration) {
            validateExpression(declaration.initializer(), state);
            state.declare(declaration.name(), "local");
        } else if (statement instanceof GpuIrPrivateArrayDeclaration declaration) {
            validateExpression(declaration.size(), state);
            state.declare(declaration.name(), "private array");
        } else if (statement instanceof GpuIrAssignment assignment) {
            validateAssignmentTarget(assignment.target(), state);
            validateExpression(assignment.value(), state);
        } else if (statement instanceof GpuIrExpressionStatement expressionStatement) {
            validateExpression(expressionStatement.expression(), state);
        } else if (statement instanceof GpuIrReturn gpuReturn) {
            validateExpression(gpuReturn.value(), state);
        } else if (statement instanceof GpuIrIf gpuIf) {
            validateExpression(gpuIf.condition(), state);
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
            validateExpression(binary.left(), state);
            validateExpression(binary.right(), state);
        } else if (expression instanceof GpuIrUnary unary) {
            validateExpression(unary.operand(), state);
        } else if (expression instanceof GpuIrTernary ternary) {
            validateExpression(ternary.condition(), state);
            validateExpression(ternary.whenTrue(), state);
            validateExpression(ternary.whenFalse(), state);
        } else if (expression instanceof GpuIrCast cast) {
            validateExpression(cast.expression(), state);
        } else if (expression instanceof GpuIrFieldAccess fieldAccess) {
            validateExpression(fieldAccess.target(), state);
        } else if (expression instanceof GpuIrHelperCall helperCall) {
            helperCall.arguments().forEach(argument -> validateExpression(argument, state));
        } else if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
            validateExpression(intrinsicCall.receiver(), state);
            intrinsicCall.arguments().forEach(argument -> validateExpression(argument, state));
        } else if (expression instanceof GpuIrStructInit structInit) {
            structInit.arguments().forEach(argument -> validateExpression(argument, state));
        } else if (expression instanceof GpuIrLiteral) {
            return;
        } else {
            state.fail("unknown IR expression type: " + expression.getClass().getName());
        }
    }

    private static final class ValidationState {
        private final GpuIrCompiledMethod method;
        private final Set<String> declaredNames;

        private ValidationState(GpuIrCompiledMethod method) {
            this(method, new HashSet<>());
        }

        private ValidationState(GpuIrCompiledMethod method, Set<String> declaredNames) {
            this.method = method;
            this.declaredNames = declaredNames;
        }

        private ValidationState copy() {
            return new ValidationState(method, new HashSet<>(declaredNames));
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

        private void fail(String message) {
            throw new GpuIrPassException("IR safety validation failed for " + method.irMethod().name() + ": " + message);
        }
    }
}
