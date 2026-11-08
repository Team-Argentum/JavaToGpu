package com.sixik.gpu.runtime;

import com.sixik.gpu.codegen.opencl.Artifact;
import com.sixik.gpu.ir.IrModule;

/**
 * Ядро, скомпилированное драйвером для устройства своего {@link GpuContext}. Хранится в кэше контекста и
 * освобождается вместе с ним; пользователь им не владеет и закрывать его не может.
 *
 * <p>Режимы доступа параметров ({@link #access}) вычисляет валидатор IR (REWORK_ARCHITECTURE.md, 9.5): по ним
 * контекст решает, какие массивы нужно вернуть на хост.</p>
 */
public final class CompiledKernel {

    private final GpuContext owner;
    private final IrModule module;
    private final int function;
    private final Artifact artifact;
    private final String kernelId;
    private final int[] access;
    final long program;
    final long kernel;
    final long maxWorkGroupSize;
    /**
     * Кто последним выставил аргументы {@code cl_kernel}: {@link PreparedLauncher} либо {@code null}. Объект драйвера общий
     * для всех запусков этого ядра, а запуски идут под замком контекста, поэтому поле читается и пишется только под ним.
     * Совпадение с самим запускающим означает «аргументы на месте, {@code clSetKernelArg} можно не вызывать».
     */
    Object boundTo;

    CompiledKernel(GpuContext owner, IrModule module, int function, Artifact artifact, String kernelId, int[] access,
                   long program, long kernel, long maxWorkGroupSize) {
        this.owner = owner;
        this.module = module;
        this.function = function;
        this.artifact = artifact;
        this.kernelId = kernelId;
        this.access = access.clone();
        this.program = program;
        this.kernel = kernel;
        this.maxWorkGroupSize = maxWorkGroupSize;
    }

    /** @return контекст, которому принадлежит ядро */
    GpuContext owner() {
        return owner;
    }

    /** @return IR-модуль, из которого получено ядро */
    public IrModule module() {
        return module;
    }

    /** @return индекс функции-ядра в модуле */
    public int function() {
        return function;
    }

    /** @return результат кодогенерации (текст, entry, сигнатура) */
    public Artifact artifact() {
        return artifact;
    }

    /** @return идентификатор ядра: хэш IR без отладочной информации */
    public String kernelId() {
        return kernelId;
    }

    /**
     * @param param индекс параметра
     * @return комбинация {@link com.sixik.gpu.ir.Validator#ACCESS_READ} и {@link com.sixik.gpu.ir.Validator#ACCESS_WRITE}
     */
    public int access(int param) {
        return access[param];
    }

    /** @return число параметров ядра */
    public int paramCount() {
        return access.length;
    }

    /** @return наибольший размер work-group для этого ядра на устройстве */
    public long maxWorkGroupSize() {
        return maxWorkGroupSize;
    }
}
