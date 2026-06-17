package net.sixik.ga_utils.javatogpu.frontend.opencl;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrHelperCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrTernary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrContinue;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrDoWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrExpressionStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitch;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitchCase;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.List;
import java.util.stream.Collectors;

public final class OpenClKernelEmitter {

    public String emit(ParsedGpuMethod parsedMethod, GpuIrMethod irMethod) {
        StringBuilder builder = new StringBuilder();
        emitFunction(builder, parsedMethod, irMethod, OpenClKernelNaming.toEntryPointName(irMethod.name()), true);
        return builder.toString();
    }

    public String emitProgram(GpuIrCompiledMethod kernelMethod, List<GpuIrCompiledMethod> helperMethods) {
        StringBuilder builder = new StringBuilder();

        for (GpuIrCompiledMethod helperMethod : helperMethods) {
            emitFunctionPrototype(builder, helperMethod.parsedMethod(), helperMethod.emittedName());
        }

        if (!helperMethods.isEmpty()) {
            builder.append("\n");
        }

        for (GpuIrCompiledMethod helperMethod : helperMethods) {
            emitFunction(builder, helperMethod.parsedMethod(), helperMethod.irMethod(), helperMethod.emittedName(), false);
            builder.append("\n");
        }

        emitFunction(
                builder,
                kernelMethod.parsedMethod(),
                kernelMethod.irMethod(),
                kernelMethod.emittedName(),
                true
        );

        return builder.toString();
    }

    private String emitParameter(ParsedGpuParameter parameter) {
        String type = parameter.javaType();
        if (GpuTypeSupport.isSupportedPointerType(type)) {
            return emitType(GpuTypeSupport.pointerValueType(type)) + "* " + parameter.name();
        }
        if (type.endsWith("[]")) {
            String elementType = type.substring(0, type.length() - 2);
            if (parameter.addressSpace() == GpuAddressSpace.GLOBAL) {
                return "__global " + (parameter.constant() ? "const " : "") + emitType(elementType) + "* " + parameter.name();
            }
            return emitType(elementType) + "* " + parameter.name();
        }

        if (parameter.addressSpace() == GpuAddressSpace.GLOBAL) {
            return "__global " + emitType(type) + "* " + parameter.name();
        }
        return emitType(type) + " " + parameter.name();
    }

    private void emitStatement(StringBuilder builder, GpuIrStatement statement, int indent) {
        String prefix = "    ".repeat(indent);

        if (statement instanceof GpuIrVariableDeclaration declaration) {
            builder.append(prefix)
                    .append(emitType(declaration.typeName()))
                    .append(" ")
                    .append(declaration.name())
                    .append(" = ")
                    .append(emitExpression(declaration.initializer()))
                    .append(";\n");
            return;
        }

        if (statement instanceof GpuIrAssignment assignment) {
            builder.append(prefix)
                    .append(emitExpression(assignment.target()))
                    .append(" = ")
                    .append(emitExpression(assignment.value()))
                    .append(";\n");
            return;
        }

        if (statement instanceof GpuIrExpressionStatement expressionStatement) {
            builder.append(prefix)
                    .append(emitExpression(expressionStatement.expression()))
                    .append(";\n");
            return;
        }

        if (statement instanceof GpuIrForLoop loop) {
            builder.append(prefix)
                    .append("for (")
                    .append(emitForHeaderStatement(loop.initializer()))
                    .append("; ")
                    .append(emitExpression(loop.condition()))
                    .append("; ")
                    .append(emitForHeaderStatement(loop.update()))
                    .append(") {\n");

            for (GpuIrStatement bodyStatement : loop.body()) {
                emitStatement(builder, bodyStatement, indent + 1);
            }

            builder.append(prefix).append("}\n");
            return;
        }

        if (statement instanceof GpuIrIf ifStatement) {
            builder.append(prefix)
                    .append("if (")
                    .append(emitExpression(ifStatement.condition()))
                    .append(") {\n");

            for (GpuIrStatement thenStatement : ifStatement.thenBranch()) {
                emitStatement(builder, thenStatement, indent + 1);
            }

            if (!ifStatement.elseBranch().isEmpty()) {
                builder.append(prefix).append("} else {\n");
                for (GpuIrStatement elseStatement : ifStatement.elseBranch()) {
                    emitStatement(builder, elseStatement, indent + 1);
                }
            }

            builder.append(prefix).append("}\n");
            return;
        }

        if (statement instanceof GpuIrWhileLoop loop) {
            builder.append(prefix)
                    .append("while (")
                    .append(emitExpression(loop.condition()))
                    .append(") {\n");
            for (GpuIrStatement bodyStatement : loop.body()) {
                emitStatement(builder, bodyStatement, indent + 1);
            }
            builder.append(prefix).append("}\n");
            return;
        }

        if (statement instanceof GpuIrDoWhileLoop loop) {
            builder.append(prefix).append("do {\n");
            for (GpuIrStatement bodyStatement : loop.body()) {
                emitStatement(builder, bodyStatement, indent + 1);
            }
            builder.append(prefix)
                    .append("} while (")
                    .append(emitExpression(loop.condition()))
                    .append(");\n");
            return;
        }

        if (statement instanceof GpuIrSwitch switchStatement) {
            builder.append(prefix)
                    .append("switch (")
                    .append(emitExpression(switchStatement.selector()))
                    .append(") {\n");
            for (GpuIrSwitchCase switchCase : switchStatement.cases()) {
                emitSwitchCase(builder, switchCase, indent + 1);
            }
            builder.append(prefix).append("}\n");
            return;
        }

        if (statement instanceof GpuIrBreak) {
            builder.append(prefix).append("break;\n");
            return;
        }

        if (statement instanceof GpuIrContinue) {
            builder.append(prefix).append("continue;\n");
            return;
        }

        if (statement instanceof GpuIrReturn gpuIrReturn) {
            builder.append(prefix)
                    .append("return");
            if (gpuIrReturn.value() != null) {
                builder.append(" ").append(emitExpression(gpuIrReturn.value()));
            }
            builder.append(";\n");
            return;
        }

        throw new IllegalArgumentException("Unsupported IR statement: " + statement);
    }

    private void emitSwitchCase(StringBuilder builder, GpuIrSwitchCase switchCase, int indent) {
        String prefix = "    ".repeat(indent);
        if (switchCase.defaultCase()) {
            builder.append(prefix).append("default:\n");
        } else {
            for (GpuIrExpression label : switchCase.labels()) {
                builder.append(prefix)
                        .append("case ")
                        .append(emitExpression(label))
                        .append(":\n");
            }
        }

        for (GpuIrStatement statement : switchCase.statements()) {
            emitStatement(builder, statement, indent + 1);
        }
    }

    private String emitForHeaderStatement(GpuIrStatement statement) {
        if (statement instanceof GpuIrVariableDeclaration declaration) {
            return emitType(declaration.typeName()) + " " + declaration.name() + " = " + emitExpression(declaration.initializer());
        }
        if (statement instanceof GpuIrAssignment assignment) {
            return emitExpression(assignment.target()) + " = " + emitExpression(assignment.value());
        }
        throw new IllegalArgumentException("Unsupported for-header statement: " + statement);
    }

    private String emitExpression(GpuIrExpression expression) {
        if (expression instanceof GpuIrVariableRef variableRef) {
            return variableRef.name();
        }

        if (expression instanceof GpuIrLiteral literal) {
            return literal.sourceText();
        }

        if (expression instanceof GpuIrArrayAccess arrayAccess) {
            return arrayAccess.arrayName() + "[" + emitExpression(arrayAccess.index()) + "]";
        }

        if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
            return intrinsicCall.backendName()
                    + "("
                    + intrinsicCall.arguments().stream().map(this::emitExpression).collect(Collectors.joining(", "))
                    + ")";
        }

        if (expression instanceof GpuIrHelperCall helperCall) {
            return helperCall.helperName()
                    + "("
                    + helperCall.arguments().stream().map(this::emitExpression).collect(Collectors.joining(", "))
                    + ")";
        }

        if (expression instanceof GpuIrCast cast) {
            return "((" + emitType(cast.targetType()) + ") " + emitExpression(cast.expression()) + ")";
        }

        if (expression instanceof GpuIrBinary binary) {
            return "(" + emitExpression(binary.left()) + " " + binary.operator() + " " + emitExpression(binary.right()) + ")";
        }

        if (expression instanceof GpuIrUnary unary) {
            return "(" + unary.operator() + emitExpression(unary.operand()) + ")";
        }

        if (expression instanceof GpuIrTernary ternary) {
            return "("
                    + emitExpression(ternary.condition())
                    + " ? "
                    + emitExpression(ternary.whenTrue())
                    + " : "
                    + emitExpression(ternary.whenFalse())
                    + ")";
        }

        throw new IllegalArgumentException("Unsupported IR expression: " + expression);
    }

    private String emitType(String javaType) {
        if (GpuTypeSupport.isSupportedPointerType(javaType)) {
            return emitType(GpuTypeSupport.pointerValueType(javaType));
        }
        return switch (javaType) {
            case "byte" -> "char";
            case "boolean" -> "bool";
            default -> javaType;
        };
    }

    private void emitFunction(StringBuilder builder, ParsedGpuMethod parsedMethod, GpuIrMethod irMethod, String emittedName, boolean kernel) {
        if (!kernel && parsedMethod.inline()) {
            builder.append("inline ");
        }
        builder.append(kernel ? "__kernel void " : emitType(parsedMethod.returnType()) + " ")
                .append(emittedName)
                .append("(")
                .append(parsedMethod.parameters().stream().map(this::emitParameter).collect(Collectors.joining(", ")))
                .append(") {\n");

        for (GpuIrStatement statement : irMethod.statements()) {
            emitStatement(builder, statement, 1);
        }

        builder.append("}");
    }

    private void emitFunctionPrototype(StringBuilder builder, ParsedGpuMethod parsedMethod, String emittedName) {
        if (parsedMethod.inline()) {
            builder.append("inline ");
        }
        builder.append(emitType(parsedMethod.returnType()))
                .append(" ")
                .append(emittedName)
                .append("(")
                .append(parsedMethod.parameters().stream().map(this::emitParameter).collect(Collectors.joining(", ")))
                .append(");\n");
    }
}
