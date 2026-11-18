package com.sixik.gpu.codegen.opencl;

import java.util.List;

/**
 * Результат кодогенерации модуля с несколькими ядрами (FRONTEND_ASM_RUNTIME.md, 18.2-S4, шаг B4): один исходник, в
 * котором каждая функция напечатана один раз, а все ядра модуля - {@code __kernel}. Драйвер собирает его одной
 * программой.
 *
 * @param source           текст OpenCL C; детерминирован
 * @param kernels          ядра в порядке функций модуля
 * @param requiredFeatures объединение масок {@link com.sixik.gpu.ir.Features} всех ядер
 * @param buildOptions     опции {@code clBuildProgram}: {@link Target#EXACT_DIVIDE_SQRT_OPTION}, если её требует хотя бы
 *                         одно ядро (опция только ужесточает), иначе пусто
 * @param target           цель
 * @param options          параметры генерации
 */
public record Program(String source, List<Kernel> kernels, int requiredFeatures, String buildOptions, Target target,
                      EmitOptions options) {

    /**
     * Ядро программы.
     *
     * @param function индекс функции в модуле
     * @param entry    имя {@code __kernel}-функции в {@link #source()}
     * @param args     раскрытая сигнатура в порядке аргументов OpenCL
     */
    public record Kernel(int function, String entry, List<KernelArg> args) {

        /** Фиксирует список аргументов. */
        public Kernel {
            args = List.copyOf(args);
        }
    }

    /** Фиксирует список ядер. */
    public Program {
        kernels = List.copyOf(kernels);
    }

    /**
     * @param function индекс функции-ядра
     * @return ядро либо {@code null}
     */
    public Kernel kernel(int function) {
        for (Kernel k : kernels) {
            if (k.function() == function) {
                return k;
            }
        }
        return null;
    }
}
