package com.sixik.gpu.api.types;

import java.util.Arrays;

/**
 * Массив {@code int}, данные которого могут жить на устройстве между вызовами ядер (см. {@link GpuArray}).
 *
 * <p>В ядре: {@code a.get(i)}, {@code a.set(i, v)}, {@code a.length()} - то же, что {@code a[i]} и {@code a.length}
 * для {@code int[]} в пространстве {@code global}. На хосте - те же методы плюс {@link #of}, {@link #toArray},
 * {@link #copyFrom}, {@link #copyTo} и {@link #fill}; они подтягивают данные с устройства только тогда, когда это
 * действительно нужно.</p>
 *
 * <pre>{@code
 * GpuFloatArray heights = new GpuFloatArray(n);
 * Terrain.generate(heights, seed);    // данные остаются на устройстве
 * Terrain.erode(heights, steps);      // следующее ядро берёт их оттуда же
 * float h = heights.get(0);           // здесь - одно чтение на хост
 * }</pre>
 */
public final class GpuIntArray extends GpuArray {

    private final int[] data;

    /**
     * Массив из {@code length} нулей.
     *
     * @param length число элементов
     */
    public GpuIntArray(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must not be negative: " + length);
        }
        this.data = new int[length];
    }

    private GpuIntArray(int[] data) {
        this.data = data;
    }

    /**
     * @param values значения (копируются)
     * @return массив с этими значениями
     */
    public static GpuIntArray of(int... values) {
        return new GpuIntArray(values.clone());
    }

    @Override
    public int length() {
        return data.length;
    }

    /**
     * @param i индекс
     * @return элемент {@code i}
     */
    public int get(int i) {
        syncHost();
        return data[i];
    }

    /**
     * @param i     индекс
     * @param value новое значение
     */
    public void set(int i, int value) {
        syncHost();
        data[i] = value;
        hostWritten();
    }

    /**
     * @param value значение всех элементов
     */
    public void fill(int value) {
        Arrays.fill(data, value);
        // все элементы перезаписаны: старые данные устройства не нужны
        markHostOverwritten();
    }

    /**
     * Копирует значения в начало массива.
     *
     * @param src источник (не длиннее массива)
     */
    public void copyFrom(int[] src) {
        if (src.length != data.length) {
            syncHost();
        }
        System.arraycopy(src, 0, data, 0, src.length);
        if (src.length == data.length) {
            markHostOverwritten();
        } else {
            hostWritten();
        }
    }

    /**
     * Копирует начало массива.
     *
     * @param dst приёмник (не длиннее массива)
     */
    public void copyTo(int[] dst) {
        syncHost();
        System.arraycopy(data, 0, dst, 0, dst.length);
    }

    /** @return копия данных в обычном массиве */
    public int[] toArray() {
        syncHost();
        return data.clone();
    }

    @Override
    Object hostData() {
        return data;
    }

    @Override
    public String toString() {
        return "GpuIntArray[" + data.length + "]";
    }
}
