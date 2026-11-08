package com.sixik.gpu.codegen.opencl;

import java.util.List;

/**
 * Результат кодогенерации: всё, что нужно рантайму, чтобы скомпилировать и запустить ядро
 * (REWORK_ARCHITECTURE.md, 8.A).
 *
 * @param source           текст OpenCL C; детерминирован: один и тот же вход даёт те же байты
 * @param entry            имя {@code __kernel}-функции в {@code source}
 * @param args             раскрытая сигнатура ядра в порядке аргументов OpenCL
 * @param requiredFeatures маска {@link com.sixik.gpu.ir.Features}; рантайм сверяет её с устройством до компиляции
 * @param buildOptions     опции {@code clBuildProgram} (пустая строка в строгом режиме)
 * @param target           цель, для которой сгенерирован текст
 * @param options          параметры, с которыми сгенерирован текст
 */
public record Artifact(String source, String entry, List<KernelArg> args, int requiredFeatures,
                       String buildOptions, Target target, EmitOptions options) {

    /**
     * Фиксирует список аргументов от изменений снаружи.
     */
    public Artifact {
        args = List.copyOf(args);
    }
}
