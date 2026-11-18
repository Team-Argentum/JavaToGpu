package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.IrModule;

import java.util.List;

/**
 * Все ядра модуля, собранные драйвером одной программой (FRONTEND_ASM_RUNTIME.md, 18.2-S4, шаги B4/B6): общие
 * device-функции печатаются и компилируются один раз, образ на диске - один на модуль. Получается
 * {@link GpuContext#compileProgram(IrModule)}; ядра запускаются как обычные {@link CompiledKernel}
 * ({@link GpuContext#launch}). Программа принадлежит контексту и освобождается при его закрытии.
 */
public final class CompiledProgram {

    private final IrModule module;
    private final String moduleId;
    private final String source;
    private final int[] functions;
    private final List<CompiledKernel> kernels;

    /**
     * @param module    исходный модуль
     * @param moduleId  {@code IrBinary.kernelId(module)}
     * @param source    текст OpenCL C программы
     * @param functions индексы ядер в исходном модуле, по порядку {@code kernels}
     * @param kernels   собранные ядра
     */
    CompiledProgram(IrModule module, String moduleId, String source, int[] functions, List<CompiledKernel> kernels) {
        this.module = module;
        this.moduleId = moduleId;
        this.source = source;
        this.functions = functions.clone();
        this.kernels = List.copyOf(kernels);
    }

    /** @return исходный (неоптимизированный) модуль */
    public IrModule module() {
        return module;
    }

    /** @return идентификатор модуля: {@code IrBinary.kernelId(module)}, как {@code AsmJit.Result.moduleId()} */
    public String moduleId() {
        return moduleId;
    }

    /** @return текст OpenCL C, общий для всех ядер */
    public String source() {
        return source;
    }

    /** @return ядра в порядке модуля */
    public List<CompiledKernel> kernels() {
        return kernels;
    }

    /**
     * @param function индекс функции-ядра в <b>исходном</b> модуле (как {@code AsmJit.Result.Kernel.function()})
     * @return ядро либо {@code null}, если такой функции-ядра нет
     */
    public CompiledKernel kernel(int function) {
        for (int i = 0; i < functions.length; i++) {
            if (functions[i] == function) {
                return kernels.get(i);
            }
        }
        return null;
    }

    /**
     * @param name отладочное имя функции исходного модуля (имя метода у фронтенда) либо имя {@code __kernel} в тексте C
     * @return первое ядро с таким именем либо {@code null}
     */
    public CompiledKernel kernel(String name) {
        for (int i = 0; i < functions.length; i++) {
            if (name.equals(module.debug().functionName(functions[i]))) {
                return kernels.get(i);
            }
        }
        for (CompiledKernel k : kernels) {
            if (k.artifact().entry().equals(name)) {
                return k;
            }
        }
        return null;
    }
}
