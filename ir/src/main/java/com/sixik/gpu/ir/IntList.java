package com.sixik.gpu.ir;

import java.util.Arrays;

/**
 * Растущий массив {@code int} без боксинга. Используется builder'ом и десериализатором для сборки колонок.
 */
final class IntList {

    private int[] data;
    private int size;

    IntList() {
        this(16);
    }

    IntList(int capacity) {
        data = new int[Math.max(capacity, 4)];
    }

    /**
     * Добавляет значение в конец.
     *
     * @param v значение
     * @return индекс добавленного значения
     */
    int add(int v) {
        if (size == data.length) {
            data = Arrays.copyOf(data, size * 2);
        }
        data[size] = v;
        return size++;
    }

    int get(int i) {
        return data[i];
    }

    void set(int i, int v) {
        data[i] = v;
    }

    int size() {
        return size;
    }

    /** Удаляет последний элемент. */
    void pop() {
        size--;
    }

    /** Обнуляет длину; ёмкость сохраняется. */
    void clear() {
        size = 0;
    }

    /**
     * @return копия содержимого точной длины
     */
    int[] toArray() {
        return Arrays.copyOf(data, size);
    }
}
