package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only shape preview for one {@code @GPUTest} fixture payload.
 */
public record GpuRuntimeMethodTestFixturePayloadPreview(
        String format,
        boolean schemaReady,
        String rootKind,
        int topLevelFieldCount,
        String primaryField,
        String primaryValueKind,
        int primaryItemCount,
        String blocker
) {

    public GpuRuntimeMethodTestFixturePayloadPreview {
        format = normalize(format, "none");
        rootKind = normalize(rootKind, "none");
        topLevelFieldCount = Math.max(topLevelFieldCount, 0);
        primaryField = normalize(primaryField, "none");
        primaryValueKind = normalize(primaryValueKind, "none");
        primaryItemCount = schemaReady ? Math.max(primaryItemCount, 0) : -1;
        blocker = normalize(blocker, schemaReady ? "none" : "fixture-payload-schema-not-ready");
    }

    public static GpuRuntimeMethodTestFixturePayloadPreview unavailable(String blocker) {
        return new GpuRuntimeMethodTestFixturePayloadPreview(
                "none",
                false,
                "none",
                0,
                "none",
                "none",
                -1,
                normalize(blocker, "fixture-payload-unavailable")
        );
    }

    public static GpuRuntimeMethodTestFixturePayloadPreview preview(String resourceRef, byte[] bytes) {
        if (!normalize(resourceRef, "").toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
            return blocked("unsupported", "none", 0, "none", "none", -1, "fixture-payload-format-unsupported");
        }
        if (bytes == null || bytes.length == 0) {
            return blocked("json-object-v1", "none", 0, "none", "none", -1, "fixture-payload-empty");
        }

        String text = new String(bytes, StandardCharsets.UTF_8);
        try {
            JsonValueShape root = new JsonShapeParser(text).parseDocument();
            if (!"object".equals(root.kind())) {
                return blocked(
                        "json-object-v1",
                        root.kind(),
                        0,
                        "none",
                        "none",
                        -1,
                        "fixture-payload-root-not-object"
                );
            }
            if (root.objectFieldCount() == 0) {
                return blocked(
                        "json-object-v1",
                        "object",
                        0,
                        "none",
                        "none",
                        -1,
                        "fixture-payload-object-empty"
                );
            }
            return new GpuRuntimeMethodTestFixturePayloadPreview(
                    "json-object-v1",
                    true,
                    "object",
                    root.objectFieldCount(),
                    root.primaryField(),
                    root.primaryValueKind(),
                    root.primaryItemCount(),
                    "none"
            );
        } catch (IllegalArgumentException exception) {
            return blocked("json-object-v1", "invalid", 0, "none", "none", -1, "fixture-payload-json-invalid");
        }
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "payloadPreview" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".format", format);
        fields.put(normalizedPrefix + ".schemaReady", Boolean.toString(schemaReady));
        fields.put(normalizedPrefix + ".rootKind", rootKind);
        fields.put(normalizedPrefix + ".topLevelField.count", Integer.toString(topLevelFieldCount));
        fields.put(normalizedPrefix + ".primaryField", primaryField);
        fields.put(normalizedPrefix + ".primaryValue.kind", primaryValueKind);
        fields.put(normalizedPrefix + ".primaryItem.count", Integer.toString(primaryItemCount));
        fields.put(normalizedPrefix + ".blocker", blocker);
        return Collections.unmodifiableMap(fields);
    }

    private static GpuRuntimeMethodTestFixturePayloadPreview blocked(
            String format,
            String rootKind,
            int fieldCount,
            String primaryField,
            String primaryValueKind,
            int primaryItemCount,
            String blocker
    ) {
        return new GpuRuntimeMethodTestFixturePayloadPreview(
                format,
                false,
                rootKind,
                fieldCount,
                primaryField,
                primaryValueKind,
                primaryItemCount,
                blocker
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record JsonValueShape(
            String kind,
            int objectFieldCount,
            String primaryField,
            String primaryValueKind,
            int primaryItemCount
    ) {

        static JsonValueShape scalar(String kind) {
            return new JsonValueShape(kind, 0, "none", "none", 1);
        }

        static JsonValueShape array(int itemCount) {
            return new JsonValueShape("array", 0, "none", "none", itemCount);
        }

        static JsonValueShape object(
                int fieldCount,
                String primaryField,
                String primaryValueKind,
                int primaryItemCount
        ) {
            return new JsonValueShape("object", fieldCount, primaryField, primaryValueKind, primaryItemCount);
        }
    }

    private static final class JsonShapeParser {
        private final String text;
        private int index;

        private JsonShapeParser(String text) {
            this.text = text == null ? "" : text;
        }

        private JsonValueShape parseDocument() {
            skipWhitespace();
            JsonValueShape value = parseValue();
            skipWhitespace();
            if (index != text.length()) {
                throw error("Trailing JSON content");
            }
            return value;
        }

        private JsonValueShape parseValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw error("Unexpected end of JSON");
            }
            char current = text.charAt(index);
            return switch (current) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> {
                    parseString();
                    yield JsonValueShape.scalar("string");
                }
                case 't' -> {
                    expectLiteral("true");
                    yield JsonValueShape.scalar("boolean");
                }
                case 'f' -> {
                    expectLiteral("false");
                    yield JsonValueShape.scalar("boolean");
                }
                case 'n' -> {
                    expectLiteral("null");
                    yield JsonValueShape.scalar("null");
                }
                default -> {
                    if (current == '-' || Character.isDigit(current)) {
                        parseNumber();
                        yield JsonValueShape.scalar("number");
                    }
                    throw error("Unexpected JSON token");
                }
            };
        }

        private JsonValueShape parseObject() {
            expect('{');
            skipWhitespace();
            if (tryConsume('}')) {
                return JsonValueShape.object(0, "none", "none", -1);
            }

            int fieldCount = 0;
            String primaryField = "none";
            String primaryValueKind = "none";
            int primaryItemCount = -1;
            while (true) {
                skipWhitespace();
                String field = parseString();
                skipWhitespace();
                expect(':');
                JsonValueShape value = parseValue();
                fieldCount++;
                if (fieldCount == 1) {
                    primaryField = field;
                    primaryValueKind = value.kind();
                    primaryItemCount = itemCount(value);
                }
                skipWhitespace();
                if (tryConsume('}')) {
                    return JsonValueShape.object(fieldCount, primaryField, primaryValueKind, primaryItemCount);
                }
                expect(',');
            }
        }

        private JsonValueShape parseArray() {
            expect('[');
            skipWhitespace();
            if (tryConsume(']')) {
                return JsonValueShape.array(0);
            }

            int itemCount = 0;
            while (true) {
                parseValue();
                itemCount++;
                skipWhitespace();
                if (tryConsume(']')) {
                    return JsonValueShape.array(itemCount);
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (index < text.length()) {
                char current = text.charAt(index++);
                if (current == '"') {
                    return builder.toString();
                }
                if (current == '\\') {
                    if (index >= text.length()) {
                        throw error("Unterminated JSON escape");
                    }
                    char escaped = text.charAt(index++);
                    switch (escaped) {
                        case '"', '\\', '/' -> builder.append(escaped);
                        case 'b' -> builder.append('\b');
                        case 'f' -> builder.append('\f');
                        case 'n' -> builder.append('\n');
                        case 'r' -> builder.append('\r');
                        case 't' -> builder.append('\t');
                        case 'u' -> builder.append(parseUnicodeEscape());
                        default -> throw error("Unsupported JSON escape");
                    }
                } else {
                    if (current < 0x20) {
                        throw error("Control character in JSON string");
                    }
                    builder.append(current);
                }
            }
            throw error("Unterminated JSON string");
        }

        private char parseUnicodeEscape() {
            if (index + 4 > text.length()) {
                throw error("Short JSON unicode escape");
            }
            int value = 0;
            for (int offset = 0; offset < 4; offset++) {
                char current = text.charAt(index++);
                int digit = Character.digit(current, 16);
                if (digit < 0) {
                    throw error("Invalid JSON unicode escape");
                }
                value = (value << 4) + digit;
            }
            return (char) value;
        }

        private void parseNumber() {
            if (tryConsume('-') && index >= text.length()) {
                throw error("Invalid JSON number");
            }
            if (tryConsume('0')) {
                // A leading zero is complete unless a fractional/exponent part follows.
            } else {
                consumeDigitRange('1', '9', true);
                consumeDigitRange('0', '9', false);
            }
            if (tryConsume('.')) {
                consumeDigitRange('0', '9', true);
            }
            if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                index++;
                if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                    index++;
                }
                consumeDigitRange('0', '9', true);
            }
        }

        private void consumeDigitRange(char min, char max, boolean requireOne) {
            int start = index;
            while (index < text.length()) {
                char current = text.charAt(index);
                if (current < min || current > max) {
                    break;
                }
                index++;
            }
            if (requireOne && start == index) {
                throw error("Expected JSON digit");
            }
        }

        private void expectLiteral(String literal) {
            if (!text.startsWith(literal, index)) {
                throw error("Expected JSON literal");
            }
            index += literal.length();
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= text.length() || text.charAt(index) != expected) {
                throw error("Expected '" + expected + "'");
            }
            index++;
        }

        private boolean tryConsume(char expected) {
            skipWhitespace();
            if (index < text.length() && text.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (index < text.length()) {
                char current = text.charAt(index);
                if (current != ' ' && current != '\n' && current != '\r' && current != '\t') {
                    return;
                }
                index++;
            }
        }

        private int itemCount(JsonValueShape value) {
            return switch (value.kind()) {
                case "array" -> value.primaryItemCount();
                case "object" -> value.objectFieldCount();
                case "string", "number", "boolean", "null" -> 1;
                default -> -1;
            };
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + index);
        }
    }
}
