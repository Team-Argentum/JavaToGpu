package com.sixik.gpu.codegen.opencl;

import java.util.List;

/**
 * Компиляторный выход для чужого рантайма (FRONTEND_ASM_RUNTIME.md, S8, шаг B6): текст программы OpenCL C, опции
 * сборки и ABI каждого ядра - всё, что нужно своему {@code clCreateProgramWithSource}/{@code clSetKernelArg} без
 * {@code runtime} этого проекта. Строится {@link OpenClEmitter#program(com.sixik.gpu.ir.IrModule, Target, EmitOptions)}.
 *
 * <p>Порядок аргументов OpenCL ядра - порядок {@link KernelAbi#params()}: индекс в списке и есть индекс
 * {@code clSetKernelArg}. Массив IR раскрывается в два аргумента: буфер ({@link Param#BUFFER}) и сразу за ним длину
 * в элементах ({@link Param#LENGTH}, {@code int}). Локальная память ядра объявляется внутри ядра, поэтому
 * {@code __local}-аргументов нет.</p>
 *
 * @param source           текст OpenCL C
 * @param buildOptions     опции {@code clBuildProgram} (возможно пустая строка)
 * @param requiredFeatures маска {@link com.sixik.gpu.ir.Features}, которую должно поддерживать устройство
 * @param kernels          ядра программы в порядке модуля
 */
public record ClProgram(String source, String buildOptions, int requiredFeatures, List<KernelAbi> kernels) {

    public ClProgram {
        kernels = List.copyOf(kernels);
    }

    /**
     * @param entry имя {@code __kernel} в тексте C
     * @return ядро либо {@code null}
     */
    public KernelAbi kernel(String entry) {
        for (KernelAbi k : kernels) {
            if (k.name().equals(entry)) {
                return k;
            }
        }
        return null;
    }

    /**
     * Поиск по отладочному имени функции IR (имя метода Java у фронтенда). Имена в тексте C от него не зависят: в
     * release-режиме ядра называются {@code k<индекс>}, поэтому отладочное {@code k0} может быть ядром {@code k1}.
     *
     * @param debugName отладочное имя функции IR
     * @return первое ядро с этим именем либо {@code null}
     */
    public KernelAbi named(String debugName) {
        for (KernelAbi k : kernels) {
            if (debugName.equals(k.debugName())) {
                return k;
            }
        }
        return null;
    }

    /**
     * ABI одного ядра.
     *
     * @param name          имя {@code __kernel} в тексте (для {@code clCreateKernel})
     * @param function      индекс функции в модуле IR
     * @param debugName     отладочное имя функции IR либо {@code null}
     * @param reqdWorkGroup обязательный размер work-group по осям {@code x, y, z} ({@code 0} - не задан)
     * @param params        аргументы OpenCL по порядку
     */
    public record KernelAbi(String name, int function, String debugName, int[] reqdWorkGroup, List<Param> params) {

        public KernelAbi {
            reqdWorkGroup = reqdWorkGroup.clone();
            params = List.copyOf(params);
        }

        @Override
        public int[] reqdWorkGroup() {
            return reqdWorkGroup.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof KernelAbi k && name.equals(k.name) && function == k.function
                    && java.util.Objects.equals(debugName, k.debugName)
                    && java.util.Arrays.equals(reqdWorkGroup, k.reqdWorkGroup) && params.equals(k.params);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, function, debugName, java.util.Arrays.hashCode(reqdWorkGroup), params);
        }

        @Override
        public String toString() {
            return "KernelAbi[" + name + ", function=" + function + ", debugName=" + debugName + ", reqdWorkGroup="
                    + java.util.Arrays.toString(reqdWorkGroup) + ", params=" + params + "]";
        }
    }

    /**
     * Один аргумент OpenCL.
     *
     * @param kind   {@link #BUFFER}, {@link #LENGTH} или {@link #SCALAR}
     * @param param  индекс параметра функции IR, из которого получен аргумент
     * @param name   имя аргумента в тексте C
     * @param type   тип IR: элемента буфера, скаляра/вектора/struct, {@code I32} для длины
     * @param size   размер {@link #type} в байтах по {@link com.sixik.gpu.ir.AbiLayout} ({@code -1} - без раскладки)
     * @param space  {@link com.sixik.gpu.ir.AddressSpace} буфера, {@code -1} для прочих
     * @param access для буфера - {@link com.sixik.gpu.ir.Validator#ACCESS_READ} и/или
     *               {@link com.sixik.gpu.ir.Validator#ACCESS_WRITE} (что ядро делает с ним), для прочих {@code 0}
     */
    public record Param(int kind, int param, String name, int type, int size, int space, int access) {

        /** {@code __global}/{@code __constant} указатель на элементы массива IR: {@code clSetKernelArg(cl_mem)}. */
        public static final int BUFFER = 0;
        /** Длина предыдущего буфера в элементах: {@code int}. */
        public static final int LENGTH = 1;
        /** Значение по значению: скаляр, вектор или struct. */
        public static final int SCALAR = 2;

        /** @return прочитан ли буфер ядром */
        public boolean reads() {
            return (access & com.sixik.gpu.ir.Validator.ACCESS_READ) != 0;
        }

        /** @return записан ли буфер ядром */
        public boolean writes() {
            return (access & com.sixik.gpu.ir.Validator.ACCESS_WRITE) != 0;
        }
    }
}
