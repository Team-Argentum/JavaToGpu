package net.sixik.ga_utils.javatogpu.frontend.opencl;
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
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrContinue;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrDoWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrExpressionStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrLoopBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitch;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitchCase;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStructField;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class OpenClKernelEmitter {

    public String emit(ParsedGpuMethod parsedMethod, GpuIrMethod irMethod) {
        StringBuilder builder = new StringBuilder();
        emitFunction(builder, parsedMethod, irMethod, OpenClKernelNaming.toEntryPointName(irMethod.name()), true);
        return builder.toString();
    }

    public String emitProgram(GpuIrCompiledMethod kernelMethod, List<GpuIrCompiledMethod> helperMethods) {
        return emitProgram(kernelMethod, helperMethods, List.of());
    }

    public String emitProgram(
            GpuIrCompiledMethod kernelMethod,
            List<GpuIrCompiledMethod> helperMethods,
            List<ParsedGpuStruct> structs
    ) {
        StringBuilder builder = new StringBuilder();

        for (ParsedGpuStruct struct : structs) {
            emitStruct(builder, struct);
            builder.append("\n");
        }
        if (!structs.isEmpty()) {
            builder.append("\n");
        }

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
        if (GpuTypeSupport.isSupportedImageOrSamplerType(type)) {
            return emitType(type) + " " + parameter.name();
        }
        if (GpuTypeSupport.isSupportedPointerType(type)) {
            return emitType(GpuTypeSupport.pointerValueType(type)) + "* " + parameter.name();
        }
        if (type.endsWith("[]")) {
            String elementType = type.substring(0, type.length() - 2);
            return switch (parameter.addressSpace()) {
                case GLOBAL -> "__global " + (parameter.constant() ? "const " : "") + emitType(elementType) + "* " + parameter.name();
                case CONSTANT -> "__constant " + emitType(elementType) + "* " + parameter.name();
                case LOCAL -> "__local " + emitType(elementType) + "* " + parameter.name();
                case PRIVATE -> emitType(elementType) + "* " + parameter.name();
            };
        }

        if (parameter.addressSpace() == GpuAddressSpace.GLOBAL) {
            return "__global " + emitType(type) + "* " + parameter.name();
        }
        return emitType(type) + " " + parameter.name();
    }

    private void emitStruct(StringBuilder builder, ParsedGpuStruct struct) {
        builder.append("typedef struct");
        if (!struct.openClAttributes().isEmpty()) {
            builder.append(" ");
            emitAttributes(builder, struct.openClAttributes(), true);
        }
        builder.append("{\n");
        for (ParsedGpuStructField field : struct.fields()) {
            builder.append("    ")
                    .append(emitType(field.javaType()))
                    .append(" ")
                    .append(field.name());
            emitAttributes(builder, field.openClAttributes(), false);
            builder.append(";\n");
        }
        builder.append("} ")
                .append(GpuTypeSupport.simpleTypeName(struct.ownerSimpleName()))
                .append(";");
    }

    private void emitStatement(StringBuilder builder, GpuIrStatement statement, int indent, EmissionContext context) {
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
            String loopBreakLabel = context.nextLoopBreakLabel();
            builder.append(prefix)
                    .append("for (")
                    .append(emitForHeaderStatement(loop.initializer()))
                    .append("; ")
                    .append(emitExpression(loop.condition()))
                    .append("; ")
                    .append(emitForHeaderStatement(loop.update()))
                    .append(") {\n");

            context.pushLoopBreakLabel(loopBreakLabel);
            for (GpuIrStatement bodyStatement : loop.body()) {
                emitStatement(builder, bodyStatement, indent + 1, context);
            }
            context.popLoopBreakLabel();

            builder.append(prefix).append("}\n");
            if (context.isLoopBreakLabelUsed(loopBreakLabel)) {
                builder.append(prefix).append(loopBreakLabel).append(": ;\n");
            }
            return;
        }

        if (statement instanceof GpuIrIf ifStatement) {
            builder.append(prefix)
                    .append("if (")
                    .append(emitExpression(ifStatement.condition()))
                    .append(") {\n");

            for (GpuIrStatement thenStatement : ifStatement.thenBranch()) {
                emitStatement(builder, thenStatement, indent + 1, context);
            }

            if (!ifStatement.elseBranch().isEmpty()) {
                builder.append(prefix).append("} else {\n");
                for (GpuIrStatement elseStatement : ifStatement.elseBranch()) {
                    emitStatement(builder, elseStatement, indent + 1, context);
                }
            }

            builder.append(prefix).append("}\n");
            return;
        }

        if (statement instanceof GpuIrWhileLoop loop) {
            String loopBreakLabel = context.nextLoopBreakLabel();
            builder.append(prefix)
                    .append("while (")
                    .append(emitExpression(loop.condition()))
                    .append(") {\n");
            context.pushLoopBreakLabel(loopBreakLabel);
            for (GpuIrStatement bodyStatement : loop.body()) {
                emitStatement(builder, bodyStatement, indent + 1, context);
            }
            context.popLoopBreakLabel();
            builder.append(prefix).append("}\n");
            if (context.isLoopBreakLabelUsed(loopBreakLabel)) {
                builder.append(prefix).append(loopBreakLabel).append(": ;\n");
            }
            return;
        }

        if (statement instanceof GpuIrDoWhileLoop loop) {
            String loopBreakLabel = context.nextLoopBreakLabel();
            builder.append(prefix).append("do {\n");
            context.pushLoopBreakLabel(loopBreakLabel);
            for (GpuIrStatement bodyStatement : loop.body()) {
                emitStatement(builder, bodyStatement, indent + 1, context);
            }
            context.popLoopBreakLabel();
            builder.append(prefix)
                    .append("} while (")
                    .append(emitExpression(loop.condition()))
                    .append(");\n");
            if (context.isLoopBreakLabelUsed(loopBreakLabel)) {
                builder.append(prefix).append(loopBreakLabel).append(": ;\n");
            }
            return;
        }

        if (statement instanceof GpuIrSwitch switchStatement) {
            context.pushSwitch();
            builder.append(prefix)
                    .append("switch (")
                    .append(emitExpression(switchStatement.selector()))
                    .append(") {\n");
            for (GpuIrSwitchCase switchCase : switchStatement.cases()) {
                emitSwitchCase(builder, switchCase, indent + 1, context);
            }
            builder.append(prefix).append("}\n");
            context.popSwitch();
            return;
        }

        if (statement instanceof GpuIrBreak) {
            builder.append(prefix).append("break;\n");
            return;
        }

        if (statement instanceof GpuIrLoopBreak) {
            if (context.insideSwitch()) {
                context.markCurrentLoopBreakUsed();
                builder.append(prefix)
                        .append("goto ")
                        .append(context.currentLoopBreakLabel())
                        .append(";\n");
            } else {
                builder.append(prefix).append("break;\n");
            }
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

    private void emitSwitchCase(StringBuilder builder, GpuIrSwitchCase switchCase, int indent, EmissionContext context) {
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
            emitStatement(builder, statement, indent + 1, context);
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

        if (expression instanceof GpuIrFieldAccess fieldAccess) {
            return emitExpression(fieldAccess.target()) + "." + fieldAccess.fieldName();
        }

        if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
            if (!intrinsicCall.codeTemplate().isBlank()) {
                return emitIntrinsicTemplate(intrinsicCall);
            }
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

        if (expression instanceof GpuIrStructInit structInit) {
            String args = structInit.arguments().isEmpty()
                    ? "0"
                    : structInit.arguments().stream().map(this::emitExpression).collect(Collectors.joining(", "));
            if (GpuTypeSupport.isSupportedVectorType(structInit.structType())) {
                return "(" + emitType(structInit.structType()) + ")(" + args + ")";
            }
            return "(" + emitType(structInit.structType()) + "){" + args + "}";
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

    private String emitIntrinsicTemplate(GpuIrIntrinsicCall intrinsicCall) {
        String rendered = intrinsicCall.codeTemplate();
        for (int i = 0; i < intrinsicCall.arguments().size(); i++) {
            rendered = rendered.replace("{" + i + "}", emitExpression(intrinsicCall.arguments().get(i)));
        }
        return rendered;
    }

    private String emitType(String javaType) {
        if (GpuTypeSupport.isSupportedPointerType(javaType)) {
            return emitType(GpuTypeSupport.pointerValueType(javaType));
        }
        if (GpuTypeSupport.isSupportedImageOrSamplerType(javaType)) {
            return GpuTypeSupport.openClImageOrSamplerTypeName(javaType);
        }
        if (GpuTypeSupport.isSupportedVectorType(javaType)) {
            return GpuTypeSupport.openClVectorTypeName(javaType);
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
        emitAttributes(builder, parsedMethod.openClAttributes(), true);
        builder.append(kernel ? "__kernel void " : emitType(parsedMethod.returnType()) + " ")
                .append(emittedName)
                .append("(")
                .append(parsedMethod.parameters().stream().map(this::emitParameter).collect(Collectors.joining(", ")))
                .append(") {\n");

        if (!kernel && !parsedMethod.nativeCode().isBlank()) {
            emitNativeCode(builder, parsedMethod.nativeCode(), 1);
        } else {
            EmissionContext context = new EmissionContext();
            for (GpuIrStatement statement : irMethod.statements()) {
                emitStatement(builder, statement, 1, context);
            }
        }

        builder.append("}");
    }

    private static final class EmissionContext {
        private final Deque<String> loopBreakLabels = new ArrayDeque<>();
        private final Set<String> usedLoopBreakLabels = new LinkedHashSet<>();
        private int switchDepth;
        private int nextLoopLabelId;

        private String nextLoopBreakLabel() {
            return "__jtg_loop_break_" + nextLoopLabelId++;
        }

        private void pushLoopBreakLabel(String loopBreakLabel) {
            loopBreakLabels.addLast(loopBreakLabel);
        }

        private void popLoopBreakLabel() {
            loopBreakLabels.removeLast();
        }

        private String currentLoopBreakLabel() {
            if (loopBreakLabels.isEmpty()) {
                throw new IllegalStateException("Loop-break label requested outside of a loop");
            }
            return loopBreakLabels.getLast();
        }

        private void markCurrentLoopBreakUsed() {
            usedLoopBreakLabels.add(currentLoopBreakLabel());
        }

        private boolean isLoopBreakLabelUsed(String loopBreakLabel) {
            return usedLoopBreakLabels.contains(loopBreakLabel);
        }

        private void pushSwitch() {
            switchDepth++;
        }

        private void popSwitch() {
            switchDepth--;
        }

        private boolean insideSwitch() {
            return switchDepth > 0;
        }
    }

    private void emitNativeCode(StringBuilder builder, String nativeCode, int indent) {
        String prefix = "    ".repeat(indent);
        String normalized = nativeCode.strip();
        if (normalized.isEmpty()) {
            return;
        }
        for (String line : normalized.split("\\R", -1)) {
            builder.append(prefix).append(line).append("\n");
        }
    }

    private void emitFunctionPrototype(StringBuilder builder, ParsedGpuMethod parsedMethod, String emittedName) {
        if (parsedMethod.inline()) {
            builder.append("inline ");
        }
        emitAttributes(builder, parsedMethod.openClAttributes(), true);
        builder.append(emitType(parsedMethod.returnType()))
                .append(" ")
                .append(emittedName)
                .append("(")
                .append(parsedMethod.parameters().stream().map(this::emitParameter).collect(Collectors.joining(", ")))
                .append(");\n");
    }

    private void emitAttributes(StringBuilder builder, List<String> attributes, boolean leadingSpace) {
        if (attributes.isEmpty()) {
            return;
        }
        if (leadingSpace) {
            builder.append("__attribute__((")
                    .append(String.join(", ", attributes))
                    .append(")) ");
            return;
        }
        builder.append(" __attribute__((")
                .append(String.join(", ", attributes))
                .append("))");
    }
}
