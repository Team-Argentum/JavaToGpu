package com.sixik.gpu.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Минимальный разбор JSON для загрузчика {@link KernelSample#fromJson}: тестовой библиотеке не нужна сторонняя
 * зависимость ради одного формата.
 *
 * <p>Результат: {@code Map<String,Object>} (порядок ключей сохраняется), {@code List<Object>}, {@code String},
 * {@code Long} (число без дробной части и порядка, помещающееся в {@code long}), {@code Double}, {@code Boolean},
 * {@code null}. Ошибка - {@link IllegalArgumentException} с позицией.</p>
 */
final class Json {

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    /**
     * @param text JSON-текст
     * @return значение верхнего уровня
     * @throws IllegalArgumentException если текст не является корректным JSON
     */
    static Object parse(String text) {
        Json p = new Json(text);
        p.skip();
        Object v = p.value();
        p.skip();
        if (p.pos != text.length()) {
            throw p.error("лишний текст после значения");
        }
        return v;
    }

    private IllegalArgumentException error(String what) {
        int line = 1;
        int col = 1;
        for (int i = 0; i < Math.min(pos, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        return new IllegalArgumentException("JSON, строка " + line + ", столбец " + col + ": " + what);
    }

    private void skip() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private Object value() {
        if (pos >= text.length()) {
            throw error("неожиданный конец текста");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return number();
                }
                throw error("неожиданный символ '" + c + "'");
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, pos)) {
            throw error("ожидалось " + word);
        }
        pos += word.length();
        return value;
    }

    private Map<String, Object> object() {
        pos++;
        Map<String, Object> map = new LinkedHashMap<>();
        skip();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skip();
            if (peek() != '"') {
                throw error("ожидалось имя поля");
            }
            String key = string();
            skip();
            expect(':');
            skip();
            if (map.put(key, value()) != null) {
                throw error("повторное поле '" + key + "'");
            }
            skip();
            if (peek() == ',') {
                pos++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> array() {
        pos++;
        List<Object> list = new ArrayList<>();
        skip();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skip();
            list.add(value());
            skip();
            if (peek() == ',') {
                pos++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String string() {
        pos++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw error("строка не закрыта");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw error("обрыв экранирования");
            }
            char e = text.charAt(pos++);
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) {
                        throw error("обрыв \\u");
                    }
                    try {
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("плохой \\u");
                    }
                    pos += 4;
                }
                default -> throw error("неизвестное экранирование \\" + e);
            }
        }
    }

    private Object number() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        boolean integral = true;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c >= '0' && c <= '9') {
                pos++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                integral = false;
                pos++;
            } else {
                break;
            }
        }
        String s = text.substring(start, pos);
        try {
            if (integral) {
                try {
                    return Long.parseLong(s);
                } catch (NumberFormatException tooBig) {
                    return Double.parseDouble(s);
                }
            }
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            pos = start;
            throw error("плохое число '" + s + "'");
        }
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private void expect(char c) {
        if (peek() != c) {
            throw error("ожидалось '" + c + "'");
        }
        pos++;
    }
}
