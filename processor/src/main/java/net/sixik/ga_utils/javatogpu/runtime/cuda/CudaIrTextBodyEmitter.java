package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrTextBodyParseResult;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrTextStatement;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrTextSwitchCase;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emits the first conservative CUDA-C subset from parsed ir-text-v1 statements.
 */
final class CudaIrTextBodyEmitter {

    static final CudaIrTextBodyEmitter INSTANCE = new CudaIrTextBodyEmitter();

    private static final String INTRINSIC_PREFIX = "intrinsic(";
    private static final String HELPER_PREFIX = "helper(";
    private static final String CAST_PREFIX = "cast<";
    private static final String INIT_PREFIX = "init<";
    private static final Pattern VARIABLE = Pattern.compile("^var\\s+(\\S+)\\s+(\\S+)\\s+=\\s+(.+)$");
    private static final Pattern PRIVATE_ARRAY = Pattern.compile("^private-array\\s+(\\S+)\\s+(\\S+)\\[(.+)]$");
    private static final Pattern ASSIGNMENT = Pattern.compile("^set\\s+(.+?)\\s+=\\s+(.+)$");

    private CudaIrTextBodyEmitter() {
    }

    Emission emit(OpenClIrTextBodyParseResult parseResult) {
        ArrayList<String> blockers = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        if (parseResult == null || !parseResult.parsed()) {
            blockers.add("cuda-ir-text-body-not-parsed");
            diagnostics.add("CUDA body emission skipped because ir-text-v1 parsing did not succeed");
            if (parseResult != null) {
                blockers.addAll(parseResult.blockers());
                diagnostics.addAll(parseResult.diagnostics());
            }
            return new Emission(false, "", blockers, diagnostics);
        }

        StringBuilder builder = new StringBuilder();
        emitStatements(builder, parseResult.statements(), 1);
        diagnostics.add("CUDA body emitter generated " + parseResult.statements().size() + " simple statement(s)");
        return new Emission(true, builder.toString(), blockers, diagnostics);
    }

    private static void emitStatements(StringBuilder builder, List<OpenClIrTextStatement> statements, int indent) {
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
                case EXPRESSION -> builder.append(prefix).append(emitExpression(statement.expression())).append(";\n");
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
        Matcher variable = VARIABLE.matcher(trimmed);
        if (variable.matches()) {
            return emitLocalVariableType(variable.group(1))
                    + " "
                    + variable.group(2)
                    + " = "
                    + emitExpression(variable.group(3));
        }
        Matcher privateArray = PRIVATE_ARRAY.matcher(trimmed);
        if (privateArray.matches()) {
            return emitType(privateArray.group(1))
                    + " "
                    + privateArray.group(2)
                    + "["
                    + emitExpression(privateArray.group(3))
                    + "]";
        }
        Matcher assignment = ASSIGNMENT.matcher(trimmed);
        if (assignment.matches()) {
            return assignment.group(1) + " = " + emitExpression(assignment.group(2));
        }
        return trimmed;
    }

    static String emitParameterType(String javaType, boolean constant) {
        String declaredType = GpuTypeSupport.declaredType(javaType);
        java.util.Optional<CudaImageSamplerAbi.Descriptor> imageSampler = CudaImageSamplerAbi.descriptorFor(declaredType);
        if (imageSampler.isPresent()) {
            CudaImageSamplerAbi.Descriptor descriptor = imageSampler.orElseThrow();
            if (descriptor.readTextureObject()) {
                return "cudaTextureObject_t";
            }
            if (descriptor.writeSurfaceObject()) {
                return "cudaSurfaceObject_t";
            }
        }
        if (declaredType != null && GpuTypeSupport.isSupportedArrayType(declaredType)) {
            return (constant ? "const " : "") + emitType(GpuTypeSupport.componentType(declaredType)) + "*";
        }
        if (declaredType != null && isSupportedVectorArrayType(declaredType)) {
            return (constant ? "const " : "") + emitType(GpuTypeSupport.componentType(declaredType)) + "*";
        }
        if (declaredType != null && declaredType.endsWith("[]")) {
            return (constant ? "const " : "") + emitType(GpuTypeSupport.componentType(declaredType)) + "*";
        }
        if (GpuTypeSupport.isSupportedPointerType(declaredType)) {
            return (constant ? "const " : "") + emitType(GpuTypeSupport.pointerValueType(declaredType)) + "*";
        }
        return emitType(declaredType);
    }

    private static boolean isSupportedVectorArrayType(String javaType) {
        return javaType.endsWith("[]") && GpuTypeSupport.isSupportedVectorType(GpuTypeSupport.componentType(javaType));
    }

    private static String emitLocalVariableType(String javaType) {
        if (GpuTypeSupport.isSupportedPointerType(javaType)
                && "PRIVATE".equals(GpuTypeSupport.pointerAddressSpace(javaType))) {
            return emitType(GpuTypeSupport.pointerValueType(javaType));
        }
        return emitType(javaType);
    }

    static String emitType(String javaType) {
        if (javaType == null || javaType.isBlank()) {
            return "void";
        }
        if (GpuTypeSupport.isSupportedScalarAliasType(javaType)) {
            return cudaScalarAliasTypeName(GpuTypeSupport.openClScalarAliasTypeName(javaType));
        }
        if (GpuTypeSupport.isSupportedVectorType(javaType)) {
            return GpuTypeSupport.openClVectorTypeName(javaType);
        }
        return switch (GpuTypeSupport.simpleTypeName(javaType)) {
            case "byte" -> "signed char";
            case "char" -> "unsigned short";
            case "boolean" -> "bool";
            default -> GpuTypeSupport.simpleTypeName(javaType);
        };
    }

    private static String cudaScalarAliasTypeName(String openClTypeName) {
        return switch (openClTypeName) {
            case "uchar" -> "unsigned char";
            case "ushort" -> "unsigned short";
            case "uint" -> "unsigned int";
            case "ulong" -> "unsigned long";
            default -> openClTypeName;
        };
    }

    private static String emitExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            return "";
        }
        return replaceRawGlobalIds(emitCastExpressions(emitInitExpressions(emitHelperExpressions(emitIntrinsicExpressions(expression)))));
    }

    private static String replaceRawGlobalIds(String expression) {
        return expression
                .replace("get_global_id(0)", cudaGlobalId("0"))
                .replace("get_global_id(1)", cudaGlobalId("1"))
                .replace("get_global_id(2)", cudaGlobalId("2"));
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
            ArrayList<String> emittedArgs = new ArrayList<>();
            for (String arg : splitTopLevel(expression.substring(openParen + 1, initEnd))) {
                emittedArgs.add(emitExpression(arg.trim()));
            }
            String emittedType = emitType(typeName);
            if (isCudaVectorType(emittedType)) {
                builder.append("make_").append(emittedType).append('(').append(String.join(", ", emittedArgs)).append(')');
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

    private static boolean isCudaVectorType(String emittedType) {
        return emittedType.matches("^(?:char|uchar|short|ushort|int|uint|long|ulong|float|double)(?:2|3|4)$");
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
            builder.append("((").append(emitType(targetType)).append(") ").append(emitExpression(value)).append(')');
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
            builder.append(emitHelper(expression.substring(helperStart + HELPER_PREFIX.length(), helperEnd)));
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
        ArrayList<String> emittedArgs = new ArrayList<>();
        for (String arg : splitTopLevel(helperArgs(payload, argsStart))) {
            emittedArgs.add(emitExpression(arg.trim()));
        }
        return helperName + "(" + String.join(", ", emittedArgs) + ")";
    }

    private static String helperArgs(String payload, int argsStart) {
        int start = argsStart + "args=[".length();
        int end = matchingCloseBracket(payload, start - 1);
        return end < 0 ? "" : payload.substring(start, end);
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
            builder.append(emitIntrinsic(expression.substring(intrinsicStart + INTRINSIC_PREFIX.length(), intrinsicEnd)));
            index = intrinsicEnd + 1;
        }
        return builder.toString();
    }

    private static String emitIntrinsic(String payload) {
        String name = intrinsicName(payload);
        String template = intrinsicField(payload, "template=");
        List<String> args = splitTopLevel(intrinsicArgs(payload));
        if ("get_global_id".equals(name)) {
            String dimension = args.isEmpty() ? "0" : emitExpression(args.get(0).trim());
            return cudaGlobalId(dimension);
        }
        ArrayList<String> emittedArgs = new ArrayList<>();
        for (String arg : args) {
            emittedArgs.add(emitExpression(arg.trim()));
        }
        if (isReadImageIntrinsic(name)) {
            return emitTextureRead(name, emittedArgs);
        }
        if (isWriteImageIntrinsic(name)) {
            return emitSurfaceWrite(name, emittedArgs);
        }
        if ("get_image_width".equals(name) && emittedArgs.size() == 1) {
            return imageMetadataParameter(emittedArgs.get(0), "width");
        }
        if ("get_image_height".equals(name) && emittedArgs.size() == 1) {
            return imageMetadataParameter(emittedArgs.get(0), "height");
        }
        if (template.isBlank()) {
            return name + "(" + String.join(", ", emittedArgs) + ")";
        }
        String emitted = template;
        String receiver = intrinsicField(payload, "recv=");
        if (!receiver.isBlank()) {
            emitted = emitted.replace("{this}", emitExpression(receiver));
        }
        for (int index = 0; index < emittedArgs.size(); index++) {
            emitted = emitted.replace("{" + index + "}", emittedArgs.get(index));
        }
        return emitted;
    }

    private static boolean isReadImageIntrinsic(String name) {
        return "read_imagef".equals(name) || "read_imagei".equals(name) || "read_imageui".equals(name);
    }

    private static boolean isWriteImageIntrinsic(String name) {
        return "write_imagef".equals(name) || "write_imagei".equals(name) || "write_imageui".equals(name);
    }

    private static String emitTextureRead(String name, List<String> emittedArgs) {
        if (emittedArgs.size() != 2 && emittedArgs.size() != 3) {
            return name + "(" + String.join(", ", emittedArgs) + ")";
        }
        String image = emittedArgs.get(0);
        String coordinates = emittedArgs.get(emittedArgs.size() - 1);
        return "tex2D<" + cudaTextureReadType(name) + ">(" + image
                + ", (float)(" + coordinateComponent(coordinates, "x") + ")"
                + ", (float)(" + coordinateComponent(coordinates, "y") + "))";
    }

    private static String cudaTextureReadType(String name) {
        return switch (name) {
            case "read_imagef" -> "float4";
            case "read_imageui" -> "uint4";
            default -> "int4";
        };
    }

    private static String emitSurfaceWrite(String name, List<String> emittedArgs) {
        if (emittedArgs.size() != 3) {
            return name + "(" + String.join(", ", emittedArgs) + ")";
        }
        String surface = emittedArgs.get(0);
        String coordinates = emittedArgs.get(1);
        String value = emittedArgs.get(2);
        String valueType = cudaSurfaceWriteType(name);
        return "surf2Dwrite(" + value
                + ", " + surface
                + ", (int)((" + coordinateComponent(coordinates, "x") + ") * sizeof(" + valueType + "))"
                + ", " + coordinateComponent(coordinates, "y") + ")";
    }

    private static String cudaSurfaceWriteType(String name) {
        return switch (name) {
            case "write_imagef" -> "float4";
            case "write_imageui" -> "uint4";
            default -> "int4";
        };
    }

    private static String coordinateComponent(String coordinates, String component) {
        String value = coordinates == null || coordinates.isBlank() ? "0" : coordinates.trim();
        return "(" + value + ")." + component;
    }

    static String imageMetadataParameter(String imageParameterName, String metadataName) {
        String image = sanitizeIdentifier(imageParameterName);
        String metadata = sanitizeIdentifier(metadataName);
        return "__jtg_cuda_image_" + image + '_' + metadata;
    }

    private static String sanitizeIdentifier(String value) {
        String raw = value == null ? "unknown" : value.trim();
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < raw.length(); index++) {
            char ch = raw.charAt(index);
            if (Character.isLetterOrDigit(ch) || ch == '_') {
                builder.append(ch);
            }
        }
        String sanitized = builder.toString();
        if (sanitized.isBlank()) {
            return "unknown";
        }
        if (Character.isDigit(sanitized.charAt(0))) {
            return '_' + sanitized;
        }
        return sanitized;
    }

    private static String cudaGlobalId(String dimension) {
        return switch (dimension.trim()) {
            case "1" -> "((int)(blockIdx.y * blockDim.y + threadIdx.y))";
            case "2" -> "((int)(blockIdx.z * blockDim.z + threadIdx.z))";
            default -> "((int)(blockIdx.x * blockDim.x + threadIdx.x))";
        };
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

    private static String intrinsicArgs(String payload) {
        int start = topLevelFieldStart(payload, "args=[");
        if (start < 0) {
            return "";
        }
        start += "args=[".length();
        int end = matchingCloseBracket(payload, start - 1);
        return end < 0 ? "" : payload.substring(start, end);
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
            } else if (ch == ')') {
                parenDepth--;
            } else if (ch == '[') {
                bracketDepth++;
            } else if (ch == ']') {
                bracketDepth--;
            } else if (parenDepth == 0 && bracketDepth == 0 && value.startsWith(fieldPrefix, index)) {
                return index;
            }
        }
        return -1;
    }

    private static int nextTopLevelField(String value, int start) {
        int parenDepth = 0;
        int bracketDepth = 0;
        boolean quoted = false;
        for (int index = start; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '"' && value.charAt(index - 1) != '\\') {
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
                return index;
            }
        }
        return value.length();
    }

    private static int firstTopLevelSpace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static List<String> splitTopLevel(String value) {
        ArrayList<String> parts = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return parts;
        }
        int parenDepth = 0;
        int bracketDepth = 0;
        boolean quoted = false;
        int start = 0;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '"' && (index == 0 || value.charAt(index - 1) != '\\')) {
                quoted = !quoted;
            } else if (!quoted) {
                if (ch == '(') {
                    parenDepth++;
                } else if (ch == ')') {
                    parenDepth--;
                } else if (ch == '[') {
                    bracketDepth++;
                } else if (ch == ']') {
                    bracketDepth--;
                } else if (ch == ',' && parenDepth == 0 && bracketDepth == 0) {
                    parts.add(value.substring(start, index).trim());
                    start = index + 1;
                }
            }
        }
        parts.add(value.substring(start).trim());
        return parts;
    }

    private static int matchingCloseParen(String value, int openParen) {
        return matchingClose(value, openParen, '(', ')');
    }

    private static int matchingCloseBracket(String value, int openBracket) {
        return matchingClose(value, openBracket, '[', ']');
    }

    private static int matchingClose(String value, int openIndex, char open, char close) {
        if (openIndex < 0 || openIndex >= value.length() || value.charAt(openIndex) != open) {
            return -1;
        }
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

    record Emission(boolean sourceGenerated, String body, List<String> blockers, List<String> diagnostics) {
        Emission {
            body = body == null ? "" : body;
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
    }
}
