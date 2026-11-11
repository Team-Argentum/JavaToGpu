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

    /**
     * Исполнение по стадиям {@code KernelSplitter} либо {@code null}, если ядро запускается целиком. Выставляется при
     * сборке, до первого запуска.
     */
    SplitPlan split;
    /**
     * Исполнять ли {@link #split}: {@code null} - ещё не решено (режим {@link KernelSplitMode#AUTO} меряет оба варианта
     * на первых запусках), {@code TRUE}/{@code FALSE} - решено. Под замком контекста.
     */
    Boolean splitChosen;
    /** Пробных запусков (режим {@link KernelSplitMode#AUTO}) и суммарное время целого ядра и стадий, нс. */
    int trialLaunches;
    long trialWholeNanos;
    long trialSplitNanos;

    /**
     * Разделённое ядро: стадии запускаются подряд с тем же NDRange, промежуточные буферы общие для всех стадий.
     *
     * @param stages       скомпилированные стадии в порядке запуска (параметры исходного ядра, затем промежуточные буферы)
     * @param scratchParam по буферу - номер параметра стадии
     * @param scratchBytes по буферу - байт на work-item
     * @param scratchPerItem по буферу - элементов на work-item
     * @param maxWorkGroupSize наименьший из пределов work-group стадий
     */
    record SplitPlan(CompiledKernel[] stages, int[] scratchParam, long[] scratchBytes, int[] scratchPerItem,
                     long maxWorkGroupSize) {
    }

    private final DriverStats driverStats;
    private final int estimatedRegisters;
    private final boolean[] fullyWritten;

    CompiledKernel(GpuContext owner, IrModule module, int function, Artifact artifact, String kernelId, int[] access,
                   long program, long kernel, long maxWorkGroupSize) {
        this(owner, module, function, artifact, kernelId, access, program, kernel, maxWorkGroupSize, DriverStats.UNKNOWN,
                -1, new boolean[access.length]);
    }

    CompiledKernel(GpuContext owner, IrModule module, int function, Artifact artifact, String kernelId, int[] access,
                   long program, long kernel, long maxWorkGroupSize, DriverStats driverStats, int estimatedRegisters,
                   boolean[] fullyWritten) {
        this.fullyWritten = fullyWritten.clone();
        this.driverStats = driverStats;
        this.estimatedRegisters = estimatedRegisters;
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

    /**
     * @return сколько запусков делает один вызов ядра: число стадий {@code KernelSplitter} либо {@code 1}, если ядро не
     *         разделено (см. {@link KernelSplitMode})
     */
    public int stageCount() {
        SplitPlan p = split;
        return p == null || Boolean.FALSE.equals(splitChosen) ? 1 : p.stages().length;
    }

    /**
     * @return {@code true}, если выбор между целым ядром и стадиями окончателен (деления нет, режим
     *         {@link KernelSplitMode#ALWAYS} либо замер {@link KernelSplitMode#AUTO} завершён)
     */
    public boolean splitDecided() {
        return split == null || splitChosen != null;
    }

    /**
     * @return статистика драйвера по стадиям разделённого ядра (пусто, если ядро не разделено)
     */
    public java.util.List<DriverStats> stageDriverStats() {
        SplitPlan p = split;
        if (p == null) {
            return java.util.List.of();
        }
        java.util.List<DriverStats> r = new java.util.ArrayList<>();
        for (CompiledKernel k : p.stages()) {
            r.add(k.driverStats());
        }
        return r;
    }

    /** @return число параметров ядра */
    public int paramCount() {
        return access.length;
    }

    /** @return наибольший размер work-group для этого ядра на устройстве */
    public long maxWorkGroupSize() {
        return maxWorkGroupSize;
    }

    /**
     * Ресурсы, которые реально выделил драйвер. Число регистров известно для NVIDIA (опция {@code -cl-nv-verbose}
     * добавляется автоматически); при загрузке образа с диска оно берётся из лога повторной сборки, если драйвер его
     * печатает.
     *
     * @return статистика драйвера (неизвестные поля - {@code -1})
     */
    public DriverStats driverStats() {
        return driverStats;
    }

    /**
     * @return оценка регистров по IR ({@code ir-optimizer}, {@code RegisterPressure}) для собранного модуля либо {@code -1}
     */
    public int estimatedRegisters() {
        return estimatedRegisters;
    }

    /**
     * Перезаписывает ли ядро каждый элемент массива-параметра при {@code globalSize(0) == length}
     * ({@code ir-optimizer}, {@code WriteCoverage}). Такой массив, если ядро его не читает, не нужно загружать на устройство.
     *
     * @param param индекс параметра
     * @return {@code true}, если покрытие доказано
     */
    public boolean fullyWritten(int param) {
        return param >= 0 && param < fullyWritten.length && fullyWritten[param];
    }

    /**
     * Можно ли не загружать массив хоста перед запуском: все параметры, которым он передан, ядро не читает, хотя бы один
     * перезаписывает целиком, и размер запуска по оси 0 равен длине массива.
     *
     * @param params параметры, которым передан один и тот же массив
     * @param count  сколько первых элементов {@code params} значимо
     * @param length длина массива в элементах
     * @param config размеры запуска
     * @return {@code true}, если загрузка не нужна
     */
    boolean uploadSkippable(int[] params, int count, long length, LaunchConfig config) {
        if (config.global(0) != length) {
            return false;
        }
        boolean covered = false;
        for (int i = 0; i < count; i++) {
            if ((access[params[i]] & com.sixik.gpu.ir.Validator.ACCESS_READ) != 0) {
                return false;
            }
            covered |= fullyWritten(params[i]);
        }
        return covered;
    }
}
