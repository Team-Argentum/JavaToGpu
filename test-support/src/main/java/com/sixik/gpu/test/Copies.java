package com.sixik.gpu.test;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Глубокое копирование аргументов ядра: ядро изменяет массивы (и объекты {@code @Struct} в них) на месте, а ветви
 * сравнения обязаны получить равные, но не общие данные.
 *
 * <p>Массивы примитивов клонируются; массивы объектов копируются поэлементно. Векторы {@code api.types.*} неизменяемы
 * и разделяются. Объект {@code @Struct} (аннотация не видна во время выполнения - ее хранит только class-файл)
 * распознаётся по открытому конструктору без аргументов: создаётся новый и в него копируются открытые поля.</p>
 */
final class Copies {

    private static final String VECTOR_PACKAGE = "com.sixik.gpu.api.types.";

    private Copies() {
    }

    /**
     * @param values значения (аргументы ядра)
     * @return независимая копия того же вида
     */
    static Object[] deepCopy(Object[] values) {
        if (values == null) {
            return null;
        }
        Object[] out = new Object[values.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = copy(values[i]);
        }
        return out;
    }

    /**
     * @param value значение
     * @return копия: массив - новый массив, struct - новый объект, остальное (скаляры, векторы) - то же значение
     */
    static Object copy(Object value) {
        if (value == null) {
            return null;
        }
        Class<?> c = value.getClass();
        if (c.isArray()) {
            if (c.getComponentType().isPrimitive()) {
                int n = Array.getLength(value);
                Object copy = Array.newInstance(c.getComponentType(), n);
                System.arraycopy(value, 0, copy, 0, n);
                return copy;
            }
            Object[] src = (Object[]) value;
            Object[] copy = (Object[]) Array.newInstance(c.getComponentType(), src.length);
            for (int i = 0; i < src.length; i++) {
                copy[i] = copy(src[i]);
            }
            return copy;
        }
        if (c.getName().startsWith(VECTOR_PACKAGE) || c.isPrimitive() || value instanceof Number
                || value instanceof Boolean || value instanceof Character || value instanceof String) {
            return value;
        }
        return copyStruct(value);
    }

    private static Object copyStruct(Object value) {
        Class<?> c = value.getClass();
        try {
            Constructor<?> ctor = c.getConstructor();
            Object copy = ctor.newInstance();
            for (Field f : c.getFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers())) {
                    f.set(copy, copy(f.get(value)));
                }
            }
            return copy;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("failed to copy argument " + c.getName()
                    + ": a public no-arg constructor and public fields (@Struct) are required", e);
        }
    }
}
