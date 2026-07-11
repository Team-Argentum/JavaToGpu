package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.ArrayList;

/**
 * Emits the first flat subset of parsed ir-text-v1 statements as OpenCL-C body text.
 *
 * <p>The emitter deliberately keeps expressions opaque because the current ir-text-v1 renderer already serializes
 * OpenCL-like expression text. Later slices can replace this with a typed expression emitter without changing the
 * source reconstruction diagnostics contract.
 */
public final class OpenClIrTextBodyEmitter {

    public static final OpenClIrTextBodyEmitter INSTANCE = new OpenClIrTextBodyEmitter();
    private static final String INTRINSIC_PREFIX = "intrinsic(";
    private static final String HELPER_PREFIX = "helper(";
    private static final String CAST_PREFIX = "cast<";
    private static final String INIT_PREFIX = "init<";
    private static final java.util.regex.Pattern OPENCL_VECTOR_TYPE = java.util.regex.Pattern.compile(
            "^(?:char|uchar|short|ushort|int|uint|long|ulong|float|double)(?:2|3|4|8|16)$"
    );
    private static final java.util.regex.Pattern VARIABLE = java.util.regex.Pattern.compile("^var\\s+(\\S+)\\s+(\\S+)\\s+=\\s+(.+)$");
    private static final java.util.regex.Pattern PRIVATE_ARRAY = java.util.regex.Pattern.compile("^private-array\\s+(\\S+)\\s+(\\S+)\\[(.+)]$");
    private static final java.util.regex.Pattern ASSIGNMENT = java.util.regex.Pattern.compile("^set\\s+(.+?)\\s+=\\s+(.+)$");

    private OpenClIrTextBodyEmitter() {
    }

    public OpenClIrTextBodyEmissionResult emit(OpenClIrTextBodyParseResult parseResult) {
        ArrayList<String> blockers = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        if (parseResult == null || !parseResult.parsed()) {
            blockers.add("ir-text-body-not-parsed");
            diagnostics.add("OpenCL body emission skipped because ir-text-v1 parsing did not succeed");
            return new OpenClIrTextBodyEmissionResult(false, "", blockers, diagnostics);
        }

        StringBuilder builder = new StringBuilder();
        emitStatements(builder, parseResult.statements(), 1);

        diagnostics.add("OpenCL body emitter generated " + parseResult.statements().size() + " simple statement(s)");
        return new OpenClIrTextBodyEmissionResult(true, builder.toString(), blockers, diagnostics);
    }

    private static void emitStatements(StringBuilder builder, java.util.List<OpenClIrTextStatement> statements, int indent) {
        for (OpenClIrTextStatement statement : statements) {
            String prefix = "    ".repeat(indent);
            switch (statement.kind()) {
                case VARIABLE -> builder.append(prefix)
                        .append(emitLocalVariableType(statement.typeName()))
                        .append(' ')
                        .append(statement.target())
                        .append(" = ")
                        .append(emitExpression(statement.expression()))
                        .append(";\n");
                case PRIVATE_ARRAY -> builder.append(prefix)
                        .append(emitType(statement.typeName()))
                        .append(' ')
                        .append(statement.target())
                        .append('[')
                        .append(emitExpression(statement.expression()))
                        .append("];\n");
                case ASSIGNMENT -> builder.append(prefix)
                        .append(statement.target())
                        .append(" = ")
                        .append(emitExpression(statement.expression()))
                        .append(";\n");
                case EXPRESSION -> builder.append(prefix)
                        .append(emitExpression(statement.expression()))
                        .append(";\n");
                case RETURN -> emitReturn(builder, statement, prefix);
                case IF -> emitIf(builder, statement, prefix, indent);
                case FOR -> emitFor(builder, statement, prefix, indent);
                case WHILE -> emitWhile(builder, statement, prefix, indent);
                case DO_WHILE -> emitDoWhile(builder, statement, prefix, indent);
                case SWITCH -> emitSwitch(builder, statement, prefix, indent);
                case BREAK -> builder.append(prefix).append("break;\n");
                case CONTINUE -> builder.append(prefix).append("continue;\n");
            }
        }
    }

    private static void emitReturn(StringBuilder builder, OpenClIrTextStatement statement, String prefix) {
        builder.append(prefix).append("return");
        if (!statement.expression().isBlank()) {
            builder.append(' ').append(emitExpression(statement.expression()));
        }
        builder.append(";\n");
    }

    private static void emitIf(StringBuilder builder, OpenClIrTextStatement statement, String prefix, int indent) {
        builder.append(prefix).append("if (").append(emitExpression(statement.expression())).append(") {\n");
        emitStatements(builder, statement.thenStatements(), indent + 1);
        if (statement.elseStatements().isEmpty()) {
            builder.append(prefix).append("}\n");
            return;
        }
        builder.append(prefix).append("} else {\n");
        emitStatements(builder, statement.elseStatements(), indent + 1);
        builder.append(prefix).append("}\n");
    }

    private static void emitFor(StringBuilder builder, OpenClIrTextStatement statement, String prefix, int indent) {
        builder.append(prefix)
                .append("for (")
                .append(emitHeaderStatement(statement.typeName()))
                .append("; ")
                .append(emitExpression(statement.expression()))
                .append("; ")
                .append(emitHeaderStatement(statement.target()))
                .append(") {\n");
        emitStatements(builder, statement.thenStatements(), indent + 1);
        builder.append(prefix).append("}\n");
    }

    private static void emitWhile(StringBuilder builder, OpenClIrTextStatement statement, String prefix, int indent) {
        builder.append(prefix).append("while (").append(emitExpression(statement.expression())).append(") {\n");
        emitStatements(builder, statement.thenStatements(), indent + 1);
        builder.append(prefix).append("}\n");
    }

    private static void emitDoWhile(StringBuilder builder, OpenClIrTextStatement statement, String prefix, int indent) {
        builder.append(prefix).append("do {\n");
        emitStatements(builder, statement.thenStatements(), indent + 1);
        builder.append(prefix).append("} while (").append(emitExpression(statement.expression())).append(");\n");
    }

    private static void emitSwitch(StringBuilder builder, OpenClIrTextStatement statement, String prefix, int indent) {
        builder.append(prefix).append("switch (").append(emitExpression(statement.expression())).append(") {\n");
        String casePrefix = "    ".repeat(indent + 1);
        for (OpenClIrTextSwitchCase switchCase : statement.switchCases()) {
            if (switchCase.defaultCase()) {
                builder.append(casePrefix).append("default:\n");
            } else {
                for (String label : switchCase.labels()) {
                    builder.append(casePrefix).append("case ").append(emitExpression(label)).append(":\n");
                }
            }
            emitStatements(builder, switchCase.statements(), indent + 2);
        }
        builder.append(prefix).append("}\n");
    }

    private static String emitHeaderStatement(String statement) {
        String trimmed = statement == null ? "" : statement.trim();
        java.util.regex.Matcher variable = VARIABLE.matcher(trimmed);
        if (variable.matches()) {
            return emitLocalVariableType(variable.group(1))
                    + " "
                    + variable.group(2)
                    + " = "
                    + emitExpression(variable.group(3));
        }
        java.util.regex.Matcher privateArray = PRIVATE_ARRAY.matcher(trimmed);
        if (privateArray.matches()) {
            return emitType(privateArray.group(1))
                    + " "
                    + privateArray.group(2)
                    + "["
                    + emitExpression(privateArray.group(3))
                    + "]";
        }
        java.util.regex.Matcher assignment = ASSIGNMENT.matcher(trimmed);
        if (assignment.matches()) {
            return assignment.group(1) + " = " + emitExpression(assignment.group(2));
        }
        return trimmed;
    }

    private static String emitType(String javaType) {
        if (GpuTypeSupport.isSupportedPointerType(javaType)) {
            return addressSpacePrefix(GpuTypeSupport.pointerAddressSpace(javaType))
                    + emitType(GpuTypeSupport.pointerValueType(javaType))
                    + "*";
        }
        if (GpuTypeSupport.isSupportedScalarAliasType(javaType)) {
            return GpuTypeSupport.openClScalarAliasTypeName(javaType);
        }
        if (GpuTypeSupport.isSupportedImageOrSamplerType(javaType)) {
            return GpuTypeSupport.openClImageOrSamplerTypeName(javaType);
        }
        if (GpuTypeSupport.isSupportedVectorType(javaType)) {
            return GpuTypeSupport.openClVectorTypeName(javaType);
        }
        return switch (javaType) {
            case "byte" -> "char";
            case "char" -> "ushort";
            case "boolean" -> "bool";
            default -> GpuTypeSupport.simpleTypeName(javaType);
        };
    }

    private static String emitLocalVariableType(String javaType) {
        if (GpuTypeSupport.isSupportedPointerType(javaType)
                && "PRIVATE".equals(GpuTypeSupport.pointerAddressSpace(javaType))) {
            return emitType(GpuTypeSupport.pointerValueType(javaType));
        }
        return emitType(javaType);
    }

    private static String addressSpacePrefix(String addressSpace) {
        return switch (addressSpace) {
            case "GLOBAL" -> "__global ";
            case "CONSTANT" -> "__constant ";
            case "LOCAL" -> "__local ";
            default -> "";
        };
    }

    private static String emitExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            return "";
        }
        return emitCastExpressions(emitInitExpressions(emitHelperExpressions(emitIntrinsicExpressions(expression))));
    }

    private static String emitInitExpressions(String expression) {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < expression.length()) {
            int initStart = expression.indexOf(INIT_PREFIX, index);
            if (initStart < 0) {
                builder.append(expression, index, expression.length());
                break;
            }
            builder.append(expression, index, initStart);
            int typeEnd = expression.indexOf(">(", initStart + INIT_PREFIX.length());
            if (typeEnd < 0) {
                builder.append(expression, initStart, expression.length());
                break;
            }
            int openParen = typeEnd + 1;
            int initEnd = matchingCloseParen(expression, openParen);
            if (initEnd < 0) {
                builder.append(expression, initStart, expression.length());
                break;
            }
            String typeName = expression.substring(initStart + INIT_PREFIX.length(), typeEnd).trim();
            String argsText = expression.substring(openParen + 1, initEnd);
            java.util.ArrayList<String> emittedArgs = new java.util.ArrayList<>();
            for (String arg : splitTopLevel(argsText)) {
                emittedArgs.add(emitExpression(arg.trim()));
            }
            String emittedType = emitType(typeName);
            if (isVectorInitializerType(typeName, emittedType)) {
                builder.append('(')
                        .append(emittedType)
                        .append(")(")
                        .append(String.join(", ", emittedArgs))
                        .append(')');
            } else {
                builder.append('(')
                        .append(emittedType)
                        .append("){")
                        .append(emittedArgs.isEmpty() ? "0" : String.join(", ", emittedArgs))
                        .append('}');
            }
            index = initEnd + 1;
        }
        return builder.toString();
    }

    private static boolean isVectorInitializerType(String typeName, String emittedType) {
        return GpuTypeSupport.isSupportedVectorType(typeName) || OPENCL_VECTOR_TYPE.matcher(emittedType).matches();
    }

    private static String emitCastExpressions(String expression) {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < expression.length()) {
            int castStart = expression.indexOf(CAST_PREFIX, index);
            if (castStart < 0) {
                builder.append(expression, index, expression.length());
                break;
            }
            builder.append(expression, index, castStart);
            int typeEnd = expression.indexOf(">(", castStart + CAST_PREFIX.length());
            if (typeEnd < 0) {
                builder.append(expression, castStart, expression.length());
                break;
            }
            int openParen = typeEnd + 1;
            int castEnd = matchingCloseParen(expression, openParen);
            if (castEnd < 0) {
                builder.append(expression, castStart, expression.length());
                break;
            }
            String targetType = expression.substring(castStart + CAST_PREFIX.length(), typeEnd).trim();
            String value = expression.substring(openParen + 1, castEnd);
            builder.append("((")
                    .append(emitType(targetType))
                    .append(") ")
                    .append(emitExpression(value))
                    .append(")");
            index = castEnd + 1;
        }
        return builder.toString();
    }

    private static String emitHelperExpressions(String expression) {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < expression.length()) {
            int helperStart = expression.indexOf(HELPER_PREFIX, index);
            if (helperStart < 0) {
                builder.append(expression, index, expression.length());
                break;
            }
            builder.append(expression, index, helperStart);
            int helperEnd = matchingCloseParen(expression, helperStart + HELPER_PREFIX.length() - 1);
            if (helperEnd < 0) {
                builder.append(expression, helperStart, expression.length());
                break;
            }
            String payload = expression.substring(helperStart + HELPER_PREFIX.length(), helperEnd);
            builder.append(emitHelper(payload));
            index = helperEnd + 1;
        }
        return builder.toString();
    }

    private static String emitHelper(String payload) {
        int argsStart = topLevelFieldStart(payload, "args=[");
        if (argsStart < 0) {
            return "helper(" + payload + ")";
        }
        String helperName = payload.substring(0, argsStart).trim();
        String argsText = helperArgs(payload, argsStart);
        java.util.ArrayList<String> emittedArgs = new java.util.ArrayList<>();
        for (String arg : splitTopLevel(argsText)) {
            emittedArgs.add(emitExpression(arg.trim()));
        }
        return helperName + "(" + String.join(", ", emittedArgs) + ")";
    }

    private static String helperArgs(String payload, int argsStart) {
        int start = argsStart + "args=[".length();
        int end = matchingCloseBracket(payload, start - 1);
        if (end < 0) {
            return "";
        }
        return payload.substring(start, end);
    }

    private static String emitIntrinsicExpressions(String expression) {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < expression.length()) {
            int intrinsicStart = expression.indexOf(INTRINSIC_PREFIX, index);
            if (intrinsicStart < 0) {
                builder.append(expression, index, expression.length());
                break;
            }
            builder.append(expression, index, intrinsicStart);
            int intrinsicEnd = matchingCloseParen(expression, intrinsicStart + INTRINSIC_PREFIX.length() - 1);
            if (intrinsicEnd < 0) {
                builder.append(expression, intrinsicStart, expression.length());
                break;
            }
            String payload = expression.substring(intrinsicStart + INTRINSIC_PREFIX.length(), intrinsicEnd);
            builder.append(emitIntrinsic(payload));
            index = intrinsicEnd + 1;
        }
        return builder.toString();
    }

    private static String emitIntrinsic(String payload) {
        String name = intrinsicName(payload);
        String receiver = intrinsicField(payload, "recv=");
        String template = intrinsicField(payload, "template=");
        java.util.List<String> args = splitTopLevel(intrinsicArgs(payload));
        java.util.ArrayList<String> emittedArgs = new java.util.ArrayList<>();
        for (String arg : args) {
            emittedArgs.add(emitExpression(arg.trim()));
        }
        String emittedReceiver = receiver.isBlank() ? "" : emitExpression(receiver);
        if (template.isBlank()) {
            return name + "(" + String.join(", ", emittedArgs) + ")";
        }
        String emitted = template;
        if (!emittedReceiver.isBlank()) {
            emitted = emitted.replace("{this}", emittedReceiver);
        }
        for (int index = 0; index < emittedArgs.size(); index++) {
            emitted = emitted.replace("{" + index + "}", emittedArgs.get(index));
        }
        return emitted;
    }

    private static String intrinsicName(String payload) {
        int firstSpace = firstTopLevelSpace(payload);
        return firstSpace < 0 ? payload.trim() : payload.substring(0, firstSpace).trim();
    }

    private static String intrinsicField(String payload, String fieldPrefix) {
        int start = topLevelFieldStart(payload, fieldPrefix);
        if (start < 0) {
            return "";
        }
        start += fieldPrefix.length();
        if ("template=".equals(fieldPrefix)) {
            return quotedValue(payload, start);
        }
        int end = nextTopLevelField(payload, start);
        return payload.substring(start, end).trim();
    }

    private static String quotedValue(String payload, int quoteStart) {
        if (quoteStart >= payload.length() || payload.charAt(quoteStart) != '"') {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = quoteStart + 1; index < payload.length(); index++) {
            char ch = payload.charAt(index);
            if (ch == '"' && payload.charAt(index - 1) != '\\') {
                return builder.toString().replace("\\\"", "\"");
            }
            builder.append(ch);
        }
        return "";
    }

    private static String intrinsicArgs(String payload) {
        int start = topLevelFieldStart(payload, "args=[");
        if (start < 0) {
            return "";
        }
        start += "args=[".length();
        int end = matchingCloseBracket(payload, start - 1);
        if (end < 0) {
            return "";
        }
        return payload.substring(start, end);
    }

    private static int topLevelFieldStart(String value, String fieldPrefix) {
        int parenDepth = 0;
        int bracketDepth = 0;
        boolean quoted = false;
        for (int index = 0; index <= value.length() - fieldPrefix.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '"' && (index == 0 || value.charAt(index - 1) != '\\')) {
                quoted = !quoted;
                continue;
            }
            if (quoted) {
                continue;
            }
            if (ch == '(') {
                parenDepth++;
                continue;
            }
            if (ch == ')') {
                parenDepth--;
                continue;
            }
            if (ch == '[') {
                bracketDepth++;
                continue;
            }
            if (ch == ']') {
                bracketDepth--;
                continue;
            }
            if (parenDepth == 0 && bracketDepth == 0 && value.startsWith(fieldPrefix, index)) {
                return index;
            }
        }
        return -1;
    }

    private static int firstTopLevelSpace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static int nextTopLevelField(String value, int startIndex) {
        int parenDepth = 0;
        int bracketDepth = 0;
        boolean quoted = false;
        for (int index = startIndex; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '"' && (index == 0 || value.charAt(index - 1) != '\\')) {
                quoted = !quoted;
                continue;
            }
            if (quoted) {
                continue;
            }
            if (ch == '(') {
                parenDepth++;
            } else if (ch == ')') {
                parenDepth--;
            } else if (ch == '[') {
                bracketDepth++;
            } else if (ch == ']') {
                bracketDepth--;
            } else if (Character.isWhitespace(ch) && parenDepth == 0 && bracketDepth == 0) {
                String rest = value.substring(index + 1);
                if (rest.startsWith("template=") || rest.startsWith("args=")) {
                    return index;
                }
            }
        }
        return value.length();
    }

    private static java.util.List<String> splitTopLevel(String argsText) {
        if (argsText == null || argsText.isBlank()) {
            return java.util.List.of();
        }
        java.util.ArrayList<String> args = new java.util.ArrayList<>();
        int parenDepth = 0;
        int bracketDepth = 0;
        boolean quoted = false;
        int start = 0;
        for (int index = 0; index < argsText.length(); index++) {
            char ch = argsText.charAt(index);
            if (ch == '"' && (index == 0 || argsText.charAt(index - 1) != '\\')) {
                quoted = !quoted;
                continue;
            }
            if (quoted) {
                continue;
            }
            if (ch == '(') {
                parenDepth++;
            } else if (ch == ')') {
                parenDepth--;
            } else if (ch == '[') {
                bracketDepth++;
            } else if (ch == ']') {
                bracketDepth--;
            } else if (ch == ',' && parenDepth == 0 && bracketDepth == 0) {
                args.add(argsText.substring(start, index).trim());
                start = index + 1;
            }
        }
        args.add(argsText.substring(start).trim());
        return args.stream().filter(arg -> !arg.isBlank()).toList();
    }

    private static int matchingCloseParen(String value, int openIndex) {
        return matchingClose(value, openIndex, '(', ')');
    }

    private static int matchingCloseBracket(String value, int openIndex) {
        return matchingClose(value, openIndex, '[', ']');
    }

    private static int matchingClose(String value, int openIndex, char open, char close) {
        int depth = 0;
        boolean quoted = false;
        for (int index = openIndex; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '"' && (index == 0 || value.charAt(index - 1) != '\\')) {
                quoted = !quoted;
                continue;
            }
            if (quoted) {
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }
}
