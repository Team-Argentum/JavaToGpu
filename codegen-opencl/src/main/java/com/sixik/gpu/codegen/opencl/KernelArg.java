package com.sixik.gpu.codegen.opencl;

/**
 * Один аргумент {@code __kernel}-функции после раскрытия (REWORK_ARCHITECTURE.md, 8.2): рантайм упаковывает
 * аргументы {@code clSetKernelArg} строго в порядке {@link Artifact#args()}.
 *
 * <p>Параметр IR-типа {@code Array(T)} раскрывается в два аргумента: указатель {@link #ARRAY_PTR} (буфер
 * устройства) и длину {@link #ARRAY_LEN} ({@code int}, элементов). Скалярный параметр - один аргумент
 * {@link #SCALAR}, передаваемый по значению.</p>
 *
 * @param kind  {@link #SCALAR}, {@link #ARRAY_PTR} или {@link #ARRAY_LEN}
 * @param param индекс параметра ядра в IR
 * @param type  для {@code SCALAR} - тип значения, для {@code ARRAY_PTR} - тип элемента, для {@code ARRAY_LEN} - {@code I32}
 * @param space для {@code ARRAY_PTR} - {@link com.sixik.gpu.ir.AddressSpace}, иначе {@code -1}
 * @param name  имя аргумента в тексте C
 */
public record KernelArg(int kind, int param, int type, int space, String name) {

    /** Скалярный аргумент по значению. */
    public static final int SCALAR = 0;
    /** Указатель на буфер массива. */
    public static final int ARRAY_PTR = 1;
    /** Длина массива в элементах ({@code int}). */
    public static final int ARRAY_LEN = 2;
}
