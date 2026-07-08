package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

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

import java.util.List;
import java.util.stream.Collectors;

public final class IrGpuTextBodyRenderer {

    private IrGpuTextBodyRenderer() {
    }

    public static String render(GpuIrCompiledMethod method) {
        StringBuilder builder = new StringBuilder();
        builder.append("method ")
                .append(method.emittedName())
                .append(" source=")
                .append(method.parsedMethod().name())
                .append('\n');
        builder.append("helpers ")
                .append(method.helperDependencies().isEmpty()
                        ? "-"
                        : String.join(",", method.helperDependencies()))
                .append('\n');
        builder.append("body\n");
        for (GpuIrStatement statement : method.irMethod().statements()) {
            renderStatement(builder, statement, 1);
        }
        return builder.toString();
    }

    private static void renderStatement(StringBuilder builder, GpuIrStatement statement, int indent) {
        String prefix = "  ".repeat(indent);
        if (statement instanceof GpuIrVariableDeclaration declaration) {
            builder.append(prefix).append("var ").append(declaration.typeName()).append(" ").append(declaration.name()).append(" = ").append(renderExpression(declaration.initializer())).append('\n');
            return;
        }
        if (statement instanceof GpuIrPrivateArrayDeclaration declaration) {
            builder.append(prefix).append("private-array ").append(declaration.elementType()).append(" ").append(declaration.name()).append("[").append(renderExpression(declaration.size())).append("]\n");
            return;
        }
        if (statement instanceof GpuIrAssignment assignment) {
            builder.append(prefix).append("set ").append(renderExpression(assignment.target())).append(" = ").append(renderExpression(assignment.value())).append('\n');
            return;
        }
        if (statement instanceof GpuIrExpressionStatement expressionStatement) {
            builder.append(prefix).append("expr ").append(renderExpression(expressionStatement.expression())).append('\n');
            return;
        }
        if (statement instanceof GpuIrForLoop loop) {
            builder.append(prefix).append("for init=(").append(renderHeaderStatement(loop.initializer())).append(") cond=").append(renderExpression(loop.condition())).append(" update=(").append(renderHeaderStatement(loop.update())).append(")\n");
            renderBlock(builder, loop.body(), indent + 1);
            return;
        }
        if (statement instanceof GpuIrIf ifStatement) {
            builder.append(prefix).append("if ").append(renderExpression(ifStatement.condition())).append('\n');
            renderBlock(builder, ifStatement.thenBranch(), indent + 1);
            if (!ifStatement.elseBranch().isEmpty()) {
                builder.append(prefix).append("else\n");
                renderBlock(builder, ifStatement.elseBranch(), indent + 1);
            }
            return;
        }
        if (statement instanceof GpuIrWhileLoop loop) {
            builder.append(prefix).append("while ").append(renderExpression(loop.condition())).append('\n');
            renderBlock(builder, loop.body(), indent + 1);
            return;
        }
        if (statement instanceof GpuIrDoWhileLoop loop) {
            builder.append(prefix).append("do\n");
            renderBlock(builder, loop.body(), indent + 1);
            builder.append(prefix).append("while ").append(renderExpression(loop.condition())).append('\n');
            return;
        }
        if (statement instanceof GpuIrSwitch switchStatement) {
            builder.append(prefix).append("switch ").append(renderExpression(switchStatement.selector())).append('\n');
            for (GpuIrSwitchCase switchCase : switchStatement.cases()) {
                builder.append(prefix).append("  ").append(switchCase.defaultCase() ? "default" : "case " + switchCase.labels().stream().map(IrGpuTextBodyRenderer::renderExpression).collect(Collectors.joining(","))).append('\n');
                renderBlock(builder, switchCase.statements(), indent + 2);
            }
            return;
        }
        if (statement instanceof GpuIrReturn gpuIrReturn) {
            builder.append(prefix).append("return");
            if (gpuIrReturn.value() != null) {
                builder.append(" ").append(renderExpression(gpuIrReturn.value()));
            }
            builder.append('\n');
            return;
        }
        if (statement instanceof GpuIrBreak) {
            builder.append(prefix).append("break\n");
            return;
        }
        if (statement instanceof GpuIrLoopBreak) {
            builder.append(prefix).append("loop-break\n");
            return;
        }
        if (statement instanceof GpuIrContinue) {
            builder.append(prefix).append("continue\n");
            return;
        }
        throw new IllegalArgumentException("Unsupported IR statement: " + statement);
    }

    private static void renderBlock(StringBuilder builder, List<GpuIrStatement> statements, int indent) {
        for (GpuIrStatement statement : statements) {
            renderStatement(builder, statement, indent);
        }
    }

    private static String renderHeaderStatement(GpuIrStatement statement) {
        if (statement instanceof GpuIrVariableDeclaration declaration) {
            return "var " + declaration.typeName() + " " + declaration.name() + " = " + renderExpression(declaration.initializer());
        }
        if (statement instanceof GpuIrPrivateArrayDeclaration declaration) {
            return "private-array " + declaration.elementType() + " " + declaration.name() + "[" + renderExpression(declaration.size()) + "]";
        }
        if (statement instanceof GpuIrAssignment assignment) {
            return "set " + renderExpression(assignment.target()) + " = " + renderExpression(assignment.value());
        }
        throw new IllegalArgumentException("Unsupported for-header statement: " + statement);
    }

    private static String renderExpression(GpuIrExpression expression) {
        if (expression instanceof GpuIrVariableRef variableRef) {
            return variableRef.name();
        }
        if (expression instanceof GpuIrLiteral literal) {
            return literal.sourceText();
        }
        if (expression instanceof GpuIrArrayAccess arrayAccess) {
            return arrayAccess.arrayName() + "[" + renderExpression(arrayAccess.index()) + "]";
        }
        if (expression instanceof GpuIrFieldAccess fieldAccess) {
            return renderExpression(fieldAccess.target()) + "." + fieldAccess.fieldName();
        }
        if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
            String receiver = intrinsicCall.receiver() == null ? "" : " recv=" + renderExpression(intrinsicCall.receiver());
            return "intrinsic(" + intrinsicCall.backendName() + receiver + " template=" + quote(intrinsicCall.codeTemplate()) + " args=[" + renderExpressions(intrinsicCall.arguments()) + "])";
        }
        if (expression instanceof GpuIrHelperCall helperCall) {
            return "helper(" + helperCall.helperName() + " args=[" + renderExpressions(helperCall.arguments()) + "])";
        }
        if (expression instanceof GpuIrCast cast) {
            return "cast<" + cast.targetType() + ">(" + renderExpression(cast.expression()) + ")";
        }
        if (expression instanceof GpuIrStructInit structInit) {
            return "init<" + structInit.structType() + ">(" + renderExpressions(structInit.arguments()) + ")";
        }
        if (expression instanceof GpuIrBinary binary) {
            return "(" + renderExpression(binary.left()) + " " + binary.operator() + " " + renderExpression(binary.right()) + ")";
        }
        if (expression instanceof GpuIrUnary unary) {
            return "(" + unary.operator() + renderExpression(unary.operand()) + ")";
        }
        if (expression instanceof GpuIrTernary ternary) {
            return "(" + renderExpression(ternary.condition()) + " ? " + renderExpression(ternary.whenTrue()) + " : " + renderExpression(ternary.whenFalse()) + ")";
        }
        throw new IllegalArgumentException("Unsupported IR expression: " + expression);
    }

    private static String renderExpressions(List<GpuIrExpression> expressions) {
        return expressions.stream().map(IrGpuTextBodyRenderer::renderExpression).collect(Collectors.joining(", "));
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
