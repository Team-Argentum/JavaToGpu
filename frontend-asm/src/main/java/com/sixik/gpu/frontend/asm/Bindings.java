package com.sixik.gpu.frontend.asm;

import org.objectweb.asm.Type;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Привязки полей - параметры времени компиляции (FRONTEND_ASM_RUNTIME.md, 18.2-S3, шаг B3): {@code getstatic}
 * привязанного {@code static} поля примитивного типа (не обязательно {@code final}) становится константой IR, и
 * оптимизатор сворачивает условия и удаляет мёртвые ветки. На JVM поле читается как обычно. Непривязанный
 * {@code getstatic} - {@code GPU0115}; значение не того типа - {@code GPU0308}. Неизменяемый объект.
 */
public final class Bindings {

    private static final Bindings NONE = new Bindings(Map.of());

    private final Map<String, Object> constants;

    private Bindings(Map<String, Object> constants) {
        this.constants = constants;
    }

    /** @return пустые привязки */
    public static Bindings none() {
        return NONE;
    }

    /**
     * Привязывает поле к значению.
     *
     * @param owner внутреннее имя класса поля ({@code ga2/Flags})
     * @param name  имя поля
     * @param value значение: {@link Integer} для {@code int/short/byte/char} (а также {@link Short}, {@link Byte},
     *              {@link Character}), {@link Boolean} для {@code boolean}, {@link Long}, {@link Float}, {@link Double}
     * @return новые привязки (прежнее значение поля заменяется)
     */
    public Bindings constant(String owner, String name, Object value) {
        Objects.requireNonNull(value, "value");
        Map<String, Object> m = new LinkedHashMap<>(constants);
        m.put(key(Objects.requireNonNull(owner, "owner"), Objects.requireNonNull(name, "name")), value);
        return new Bindings(Collections.unmodifiableMap(m));
    }

    /** @return {@code true}, если привязок нет */
    public boolean isEmpty() {
        return constants.isEmpty();
    }

    /**
     * @param owner внутреннее имя класса
     * @param name  имя поля
     * @return привязанное значение либо {@code null}
     */
    Object constant(String owner, String name) {
        return constants.get(key(owner, name));
    }

    private static String key(String owner, String name) {
        return owner + "." + name;
    }

    /**
     * Проверяет значение по дескриптору поля.
     *
     * @param desc  дескриптор поля
     * @param value значение
     * @return описание несовпадения для {@code GPU0308} либо {@code null}
     */
    static String problem(String desc, Object value) {
        String type = value.getClass().getSimpleName();
        switch (desc) {
            case "I":
                return asInt(value) != null ? null : "the field is int, the value is " + type;
            case "S":
            case "B":
            case "C": {
                Long v = asInt(value);
                if (v == null) {
                    return "the field is " + shown(desc) + ", the value is " + type;
                }
                long lo = desc.equals("S") ? Short.MIN_VALUE : desc.equals("B") ? Byte.MIN_VALUE : 0;
                long hi = desc.equals("S") ? Short.MAX_VALUE : desc.equals("B") ? Byte.MAX_VALUE : Character.MAX_VALUE;
                return v < lo || v > hi ? "the value " + v + " is out of range of " + shown(desc) : null;
            }
            case "Z":
                return value instanceof Boolean ? null : "the field is boolean, the value is " + type;
            case "J":
                return value instanceof Long ? null : "the field is long, the value is " + type;
            case "F":
                return value instanceof Float ? null : "the field is float, the value is " + type;
            case "D":
                return value instanceof Double ? null : "the field is double, the value is " + type;
            default:
                return "only fields of primitive types are bound, the field is " + shown(desc);
        }
    }

    private static String shown(String desc) {
        return Type.getType(desc).getClassName();
    }

    private static Long asInt(Object v) {
        if (v instanceof Integer || v instanceof Short || v instanceof Byte) {
            return ((Number) v).longValue();
        }
        return v instanceof Character c ? Long.valueOf(c) : null;
    }

    /**
     * Константа IR для проверенного значения (на стеке JVM {@code boolean/byte/short/char} - это {@code int}).
     *
     * @param desc  дескриптор поля
     * @param value значение
     * @return константа
     */
    static Ex constantOf(String desc, Object value) {
        switch (desc) {
            case "J":
                return Ex.constant(Kinds.K_LONG, (Long) value);
            case "F":
                return Ex.constant(Kinds.K_FLOAT, Float.floatToRawIntBits((Float) value));
            case "D":
                return Ex.constant(Kinds.K_DOUBLE, Double.doubleToRawLongBits((Double) value));
            case "Z":
                return Ex.constant(Kinds.K_INT, (Boolean) value ? 1 : 0);
            default:
                return Ex.constant(Kinds.K_INT, asInt(value));
        }
    }
}
