package com.sixik.gpu.runtime;

import com.sixik.gpu.api.types.GpuArray;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.optimizer.Reductions;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Исполнение свёрток ({@code @Reduction}) контекстом: построение параллельного ядра ({@link Reductions#lower}),
 * выбор размера запуска, буфер сумм групп и свёртка этих сумм на хосте.
 *
 * <p>Размер work-group - {@link Reductions#DEFAULT_WORK_GROUP}, но не больше предела устройства и предела
 * скомпилированного ядра (тяжёлое ядро может допускать меньшую группу - тогда ядро строится заново с меньшей). Число
 * групп - столько, чтобы каждой итерации хватило work-item'а, но не больше {@link #GROUPS_PER_UNIT} на
 * вычислительный блок (дальше work-item'ы проходят по несколько итераций). Если число итераций на хосте неизвестно
 * ({@link Reductions.Bound.Kind#UNKNOWN}), берётся предел.</p>
 *
 * <p>Буфер сумм групп - один на тип результата, растёт по требованию (степенью двойки), освобождается с контекстом.
 * Всё исполнение идёт под замком контекста: буфер общий для свёрток этого типа.</p>
 */
final class DeviceReductions {

    /** Наибольшее число групп на вычислительный блок устройства. */
    static final int GROUPS_PER_UNIT = 16;
    /** Абсолютный предел числа групп. */
    static final int MAX_GROUPS = 1 << 16;

    /** Построенное и скомпилированное ядро свёртки. */
    record Kernel(Reductions.Lowered lowered, CompiledKernel compiled) {
    }

    private final GpuContext ctx;
    /** Ядра по исходному модулю (по идентичности; модули фасада живут в кэше классов). */
    private final Map<IrModule, Result<Kernel>> kernels =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    /** Буферы сумм групп по типу результата. */
    private final Map<Integer, GpuBuffer> partials = new HashMap<>();

    DeviceReductions(GpuContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Строит и компилирует ядро свёртки (с кэшем).
     *
     * @param module модуль фронтенда, последняя функция - {@code @Reduction}
     * @return ядро либо диагностики ({@code OPT-R01/R02}, ошибки компиляции)
     */
    Result<Kernel> kernel(IrModule module) {
        Result<Kernel> cached = kernels.get(module);
        if (cached != null) {
            return cached;
        }
        Result<Kernel> built = build(module);
        kernels.put(module, built);
        return built;
    }

    private Result<Kernel> build(IrModule module) {
        long deviceLimit = ctx.device().maxWorkGroupSize();
        int wg = Reductions.DEFAULT_WORK_GROUP;
        while (wg > 1 && deviceLimit > 0 && wg > deviceLimit) {
            wg >>= 1;
        }
        while (true) {
            Result<Reductions.Lowered> lowered = Reductions.lower(module, wg);
            if (!lowered.isOk()) {
                return Result.failed(lowered.diagnostics());
            }
            Result<CompiledKernel> compiled = ctx.compile(lowered.value().module());
            if (!compiled.isOk()) {
                return Result.failed(compiled.diagnostics());
            }
            long limit = compiled.value().maxWorkGroupSize();
            if (limit <= 0 || limit >= wg || wg == 1) {
                return Result.ok(new Kernel(lowered.value(), compiled.value()));
            }
            // ядро тяжелее, чем позволяет группа: строится заново с наибольшей допустимой степенью двойки
            wg = Integer.highestOneBit((int) Math.min(limit, wg - 1));
        }
    }

    /**
     * Исполняет свёртку на устройстве.
     *
     * @param k    ядро свёртки этого контекста
     * @param args аргументы исходного метода (массивы Java, {@link GpuArray}, {@link GpuBuffer}, упакованные скаляры)
     * @return упакованный результат того же типа, что у метода, либо диагностики запуска
     */
    Result<Object> run(Kernel k, Object[] args) {
        Reductions.Lowered lowered = k.lowered();
        Reductions.Plan plan = lowered.plan();
        int wg = lowered.workGroup();
        long trip = trip(plan, args);
        int maxGroups = (int) Math.min(MAX_GROUPS, Math.max(1L, (long) Math.max(1, ctx.device().computeUnits())
                * GROUPS_PER_UNIT));
        int groups = trip < 0 ? maxGroups : (int) Math.max(1, Math.min(maxGroups, (trip + wg - 1) / wg));
        ctx.lock.lock();
        try {
            GpuBuffer partial = partialBuffer(plan.type(), groups);
            Object[] full = Arrays.copyOf(args, args.length + 1);
            full[args.length] = partial;
            Result<LaunchStats> launched = ctx.launchReusing(k.compiled(), LaunchConfig.of1D((long) groups * wg, wg), full);
            if (!launched.isOk()) {
                return Result.failed(launched.diagnostics());
            }
            Object sums = switch (plan.type()) {
                case Types.I32 -> new int[groups];
                case Types.I64 -> new long[groups];
                case Types.F32 -> new float[groups];
                default -> new double[groups];
            };
            partial.read(sums); // блокирующее чтение - точка синхронизации очереди
            return Result.ok(combine(plan.combine(), sums));
        } catch (GpuException e) {
            return Result.failed(e.diagnostics());
        } finally {
            ctx.lock.unlock();
        }
    }

    /** Число итераций цикла, если границы вычислимы на хосте, иначе {@code -1}. */
    static long trip(Reductions.Plan plan, Object[] args) {
        long start = value(plan.start(), args);
        long end = value(plan.end(), args);
        if (start == Long.MIN_VALUE || end == Long.MIN_VALUE) {
            return -1;
        }
        return Math.max(0, end - start);
    }

    private static long value(Reductions.Bound b, Object[] args) {
        switch (b.kind()) {
            case CONST -> {
                return b.value();
            }
            case PARAM -> {
                Object a = args[(int) b.value()];
                return a instanceof Integer i ? i : Long.MIN_VALUE;
            }
            case LENGTH -> {
                Object a = args[(int) b.value()];
                if (a instanceof GpuArray ga) {
                    return ga.length();
                }
                if (a instanceof GpuBuffer gb) {
                    return gb.length();
                }
                if (a != null && a.getClass().isArray()) {
                    return Marshal.length(a);
                }
                return Long.MIN_VALUE;
            }
            default -> {
                return Long.MIN_VALUE;
            }
        }
    }

    /** Буфер сумм групп не короче {@code groups} (под замком). */
    private GpuBuffer partialBuffer(int type, int groups) {
        GpuBuffer b = partials.get(type);
        if (b != null && !b.isClosed() && b.length() >= groups) {
            return b;
        }
        if (b != null && !b.isClosed()) {
            b.close();
        }
        int capacity = Integer.highestOneBit(Math.max(1, groups - 1)) << 1;
        GpuBuffer fresh = ctx.allocate(type, Math.max(capacity, groups));
        partials.put(type, fresh);
        return fresh;
    }

    /**
     * Сворачивает суммы групп по порядку групп той же операцией.
     *
     * @param c    операция
     * @param sums {@code int[]/long[]/float[]/double[]}
     * @return упакованный результат
     */
    static Object combine(Reductions.Combine c, Object sums) {
        if (sums instanceof int[] a) {
            int r = a[0];
            for (int i = 1; i < a.length; i++) {
                int v = a[i];
                r = switch (c) {
                    case ADD -> r + v;
                    case MIN -> Math.min(r, v);
                    case MAX -> Math.max(r, v);
                    case AND -> r & v;
                    case OR -> r | v;
                    case XOR -> r ^ v;
                };
            }
            return r;
        }
        if (sums instanceof long[] a) {
            long r = a[0];
            for (int i = 1; i < a.length; i++) {
                long v = a[i];
                r = switch (c) {
                    case ADD -> r + v;
                    case MIN -> Math.min(r, v);
                    case MAX -> Math.max(r, v);
                    case AND -> r & v;
                    case OR -> r | v;
                    case XOR -> r ^ v;
                };
            }
            return r;
        }
        if (sums instanceof float[] a) {
            float r = a[0];
            for (int i = 1; i < a.length; i++) {
                r = switch (c) {
                    case ADD -> r + a[i];
                    case MIN -> Math.min(r, a[i]);
                    case MAX -> Math.max(r, a[i]);
                    default -> throw new IllegalStateException("bitwise " + c + " on float");
                };
            }
            return r;
        }
        double[] a = (double[]) sums;
        double r = a[0];
        for (int i = 1; i < a.length; i++) {
            r = switch (c) {
                case ADD -> r + a[i];
                case MIN -> Math.min(r, a[i]);
                case MAX -> Math.max(r, a[i]);
                default -> throw new IllegalStateException("bitwise " + c + " on double");
            };
        }
        return r;
    }

    /** Освобождает буферы (под замком, при закрытии контекста буферы освобождает сам контекст). */
    void clear() {
        partials.clear();
        kernels.clear();
    }
}
