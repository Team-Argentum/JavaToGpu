package net.sixik.ga_utils.javatogpu.runtime;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GpuRuntimeMethodTestFixtureValuePayloadParser {

    private GpuRuntimeMethodTestFixtureValuePayloadParser() {
    }

    static Payload parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Fixture payload is empty");
        }
        return new Parser(new String(bytes, StandardCharsets.UTF_8)).parseDocument();
    }

    record Payload(Map<String, FixtureValue> fields, List<String> unsupportedFields) {

        Payload {
            fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
            unsupportedFields = unsupportedFields == null ? List.of() : List.copyOf(unsupportedFields);
        }

        boolean containsField(String field) {
            return fields.containsKey(field) || unsupportedFields.contains(field);
        }

        FixtureValue field(String field) {
            return fields.get(field);
        }

        NumericValue numericField(String field) {
            FixtureValue value = fields.get(field);
            return value instanceof NumericValue numericValue ? numericValue : null;
        }
    }

    interface FixtureValue {

        String valueKind();

        int itemCount();

        List<String> flattenedNumericValues();
    }

    record NumericValue(boolean array, List<String> numbers) implements FixtureValue {

        NumericValue {
            numbers = numbers == null ? List.of() : List.copyOf(numbers);
        }

        @Override
        public String valueKind() {
            return array ? "numeric-array" : "numeric-scalar";
        }

        @Override
        public int itemCount() {
            return numbers.size();
        }

        @Override
        public List<String> flattenedNumericValues() {
            return numbers;
        }
    }

    record StructValue(Map<String, FixtureValue> fields) implements FixtureValue {

        StructValue {
            fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }

        @Override
        public String valueKind() {
            return "struct-object";
        }

        @Override
        public int itemCount() {
            return 1;
        }

        FixtureValue field(String field) {
            return fields.get(field);
        }

        @Override
        public List<String> flattenedNumericValues() {
            ArrayList<String> values = new ArrayList<>();
            appendFlattenedStructValues(values, "", this);
            return List.copyOf(values);
        }
    }

    record StructArrayValue(List<StructValue> items) implements FixtureValue {

        StructArrayValue {
            items = items == null ? List.of() : List.copyOf(items);
        }

        @Override
        public String valueKind() {
            return "struct-array";
        }

        @Override
        public int itemCount() {
            return items.size();
        }

        @Override
        public List<String> flattenedNumericValues() {
            ArrayList<String> values = new ArrayList<>();
            for (int index = 0; index < items.size(); index++) {
                appendFlattenedStructValues(values, "[" + index + "]", items.get(index));
            }
            return List.copyOf(values);
        }
    }

    private static void appendFlattenedStructValues(ArrayList<String> values, String path, StructValue structValue) {
        for (Map.Entry<String, FixtureValue> entry : structValue.fields().entrySet()) {
            String childPath = path.isBlank() ? entry.getKey() : path + "." + entry.getKey();
            FixtureValue value = entry.getValue();
            if (value instanceof NumericValue numericValue && !numericValue.array()) {
                values.add(childPath + "=" + numericValue.numbers().get(0));
            } else if (value instanceof StructValue nestedStruct) {
                appendFlattenedStructValues(values, childPath, nestedStruct);
            } else {
                values.add(childPath + "=<unsupported>");
            }
        }
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text == null ? "" : text;
        }

        private Payload parseDocument() {
            skipWhitespace();
            Payload payload = parseObjectPayload();
            skipWhitespace();
            if (index != text.length()) {
                throw error("Trailing JSON content");
            }
            return payload;
        }

        private Payload parseObjectPayload() {
            expect('{');
            LinkedHashMap<String, FixtureValue> fields = new LinkedHashMap<>();
            ArrayList<String> unsupportedFields = new ArrayList<>();
            skipWhitespace();
            if (tryConsume('}')) {
                return new Payload(fields, unsupportedFields);
            }

            while (true) {
                String field = parseString();
                skipWhitespace();
                expect(':');
                FixtureValue value = parseMaybeFixtureValue();
                if (value == null) {
                    unsupportedFields.add(field);
                } else {
                    fields.put(field, value);
                }
                skipWhitespace();
                if (tryConsume('}')) {
                    return new Payload(fields, unsupportedFields);
                }
                expect(',');
            }
        }

        private FixtureValue parseMaybeFixtureValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw error("Unexpected end of JSON");
            }
            int start = index;
            char current = text.charAt(index);
            if (current == '-' || Character.isDigit(current)) {
                return new NumericValue(false, List.of(parseNumberString()));
            }
            FixtureValue value = null;
            if (current == '[') {
                value = parseMaybeArrayValue();
            } else if (current == '{') {
                value = parseMaybeStructValue();
            }
            if (value != null) {
                return value;
            }
            index = start;
            skipValue();
            return null;
        }

        private FixtureValue parseMaybeArrayValue() {
            expect('[');
            ArrayList<String> numbers = new ArrayList<>();
            ArrayList<StructValue> structs = new ArrayList<>();
            String arrayKind = "empty";
            skipWhitespace();
            if (tryConsume(']')) {
                return new NumericValue(true, numbers);
            }

            while (true) {
                skipWhitespace();
                if (index < text.length() && (text.charAt(index) == '-' || Character.isDigit(text.charAt(index)))) {
                    if ("struct".equals(arrayKind)) {
                        skipValue();
                        return null;
                    }
                    arrayKind = "numeric";
                    numbers.add(parseNumberString());
                } else if (index < text.length() && text.charAt(index) == '{') {
                    if ("numeric".equals(arrayKind)) {
                        skipValue();
                        return null;
                    }
                    arrayKind = "struct";
                    StructValue structValue = parseMaybeStructValue();
                    if (structValue == null) {
                        return null;
                    }
                    structs.add(structValue);
                } else {
                    skipValue();
                    return null;
                }
                skipWhitespace();
                if (tryConsume(']')) {
                    return switch (arrayKind) {
                        case "numeric", "empty" -> new NumericValue(true, numbers);
                        case "struct" -> new StructArrayValue(structs);
                        default -> null;
                    };
                }
                expect(',');
            }
        }

        private StructValue parseMaybeStructValue() {
            expect('{');
            LinkedHashMap<String, FixtureValue> fields = new LinkedHashMap<>();
            skipWhitespace();
            if (tryConsume('}')) {
                return new StructValue(fields);
            }

            while (true) {
                String field = parseString();
                skipWhitespace();
                expect(':');
                FixtureValue value = parseMaybeFixtureValue();
                if (value == null) {
                    return null;
                }
                fields.put(field, value);
                skipWhitespace();
                if (tryConsume('}')) {
                    return new StructValue(fields);
                }
                expect(',');
            }
        }

        private void skipValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw error("Unexpected end of JSON");
            }
            char current = text.charAt(index);
            switch (current) {
                case '{' -> skipObject();
                case '[' -> skipArray();
                case '"' -> parseString();
                case 't' -> expectLiteral("true");
                case 'f' -> expectLiteral("false");
                case 'n' -> expectLiteral("null");
                default -> {
                    if (current == '-' || Character.isDigit(current)) {
                        parseNumberString();
                    } else {
                        throw error("Unexpected JSON token");
                    }
                }
            }
        }

        private void skipObject() {
            expect('{');
            skipWhitespace();
            if (tryConsume('}')) {
                return;
            }
            while (true) {
                parseString();
                skipWhitespace();
                expect(':');
                skipValue();
                skipWhitespace();
                if (tryConsume('}')) {
                    return;
                }
                expect(',');
            }
        }

        private void skipArray() {
            expect('[');
            skipWhitespace();
            if (tryConsume(']')) {
                return;
            }
            while (true) {
                skipValue();
                skipWhitespace();
                if (tryConsume(']')) {
                    return;
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
                int digit = Character.digit(text.charAt(index++), 16);
                if (digit < 0) {
                    throw error("Invalid JSON unicode escape");
                }
                value = (value << 4) + digit;
            }
            return (char) value;
        }

        private String parseNumberString() {
            skipWhitespace();
            int start = index;
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
            return text.substring(start, index);
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

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + index);
        }
    }
}
