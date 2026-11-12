package com.sixik.gpu.runtime;

import com.sixik.gpu.api.types.GpuArray;
import com.sixik.gpu.api.types.GpuDoubleArray;
import com.sixik.gpu.api.types.GpuFloatArray;
import com.sixik.gpu.api.types.GpuIntArray;
import com.sixik.gpu.api.types.GpuLongArray;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;
import com.sixik.gpu.ir.optimizer.Fusion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.sixik.gpu.runtime.ResidentArrays.ACCESS;

/**
 * Отложенные ядра-производители фасада и слияние «ядро → свёртка» (REWORK_ARCHITECTURE.md, 7.1).
 *
 * <p>Вызов через {@link JavaToGpu} ядра-производителя ({@link Fusion#producer}: {@code out[gid] = value}, без других
 * записей), все массивы которого - {@link GpuArray}, а запуск - ровно {@code out.length}, не исполняется сразу:
 * массив {@code out} помечается «свежий на устройстве», а запуск запоминается ({@link #defer}). Если затем
 * {@code @Reduction} получает {@code out}, свёртка строится слитой ({@link Fusion#fuse}): производитель вычисляется в
 * ней поэлементно, и массив не пишется в память вовсе ({@link #fusedReduce}). Сам {@code out} остаётся отложенным.</p>
 *
 * <p>Отложенный запуск исполняется (материализуется), как только его результат или входы могут понадобиться иначе:</p>
 * <ul>
 *   <li>хост читает или частично пишет {@code out} ({@code Residency.toHost}, {@code settle});</li>
 *   <li>{@code out} передан другому ядру, свёртке без слияния или подготовленному запуску ({@link #beforeLaunch});</li>
 *   <li>хост или ядро меняют вход, который запуск читает со своей копии на устройстве;</li>
 *   <li>контекст закрывается ({@link #materializeAll}).</li>
 * </ul>
 * <p>Маленькие входы ({@value #SNAPSHOT_BYTES} байт и меньше) со свежими данными на хосте при откладывании копируются в
 * собственный массив-снимок: их последующее изменение (координаты следующей порции) не заставляет исполнять старый
 * запуск. Если {@code out} целиком перезаписывает новый отложенный производитель или хост ({@code fill}, полный
 * {@code copyFrom}), старый запуск просто отбрасывается - его результат никто не видел.</p>
 *
 * <p>Всё - под замком контекста. Ошибка исполнения отложенного ядра проявляется при материализации.</p>
 */
final class LazyProducers {

    /** Наибольший вход, который копируется в снимок при откладывании. */
    static final long SNAPSHOT_BYTES = 64 * 1024;

    /** Отложенный запуск. */
    private static final class Pending {
        final CompiledKernel kernel;
        final LaunchConfig config;
        /** Аргументы запуска: снимки вместо скопированных входов. */
        final Object[] args;
        final GpuArray out;
        final int outParam;
        /** Входы, которые запуск читает со своей копии на устройстве (наблюдаются). */
        final List<GpuArray> watched = new ArrayList<>();
        /** Собственные снимки (возвращаются в пул). */
        final List<GpuArray> snapshots = new ArrayList<>();

        Pending(CompiledKernel kernel, LaunchConfig config, Object[] args, GpuArray out, int outParam) {
            this.kernel = kernel;
            this.config = config;
            this.args = args;
            this.out = out;
            this.outParam = outParam;
        }
    }

    /** Ключ слитой свёртки: ядро-производитель, модуль свёртки и параметр, которому передан выход. */
    private record FusedKey(CompiledKernel producer, IrModule reduction, int param) {
        @Override
        public boolean equals(Object o) {
            return o instanceof FusedKey k && k.producer == producer && k.reduction == reduction && k.param == param;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(producer) * 31 + System.identityHashCode(reduction) * 7 + param;
        }
    }

    private final GpuContext ctx;
    private final Map<GpuArray, Pending> byOut = new IdentityHashMap<>();
    private final Map<GpuArray, List<Pending>> byInput = new IdentityHashMap<>();
    private final Map<CompiledKernel, Optional<Fusion.Producer>> producers = new IdentityHashMap<>();
    private final Map<FusedKey, Result<IrModule>> fused = new HashMap<>();
    private final Map<FusedKey, Result<CompiledKernel>> fusedKernels = new HashMap<>();
    /** Свободные снимки по «класс массива, длина». */
    private final Map<String, ArrayDeque<GpuArray>> pool = new HashMap<>();

    LazyProducers(GpuContext ctx) {
        this.ctx = ctx;
    }

    /** @return число отложенных запусков (для тестов) */
    int pending() {
        return byOut.size();
    }

    /** @return {@code true}, если массив - выход отложенного запуска */
    boolean isPending(GpuArray a) {
        return byOut.containsKey(a);
    }

    /**
     * Откладывает вызов фасада, если ядро - производитель и запуск подходит. Под замком.
     *
     * @param kernel ядро этого контекста
     * @param config размеры запуска
     * @param args   аргументы вызова
     * @return {@code true}, если запуск отложен (исполнять его сейчас не нужно)
     */
    boolean defer(CompiledKernel kernel, LaunchConfig config, Object[] args) {
        if (!ctx.fusion || kernel.split != null) {
            return false;
        }
        Optional<Fusion.Producer> pr = producers.computeIfAbsent(kernel, k -> Fusion.producer(k.module()));
        if (pr.isEmpty()) {
            return false;
        }
        int o = pr.get().outParam();
        IrModule m = kernel.module();
        int f = kernel.function();
        if (args.length != m.fParamCount(f) || !(args[o] instanceof GpuArray out)
                || config.global(0) != out.length() || config.global(1) != 1 || config.global(2) != 1) {
            return false;
        }
        for (int i = 0; i < args.length; i++) {
            boolean array = m.typeKind(m.fParamType(f, i)) == Types.KIND_ARRAY;
            Object a = args[i];
            if (array ? !(a instanceof GpuArray) || (i != o && a == out)
                    : !(a instanceof Integer || a instanceof Long || a instanceof Float || a instanceof Double
                    || a instanceof Short || a instanceof Byte || a instanceof Character || a instanceof Boolean)) {
                return false;
            }
        }
        // out перезаписывается целиком: прежний отложенный запуск в него не нужен, читающие out - исполняются
        Pending old = byOut.get(out);
        if (old != null) {
            drop(old);
        }
        materializeWatching(out);
        Pending p = new Pending(kernel, config, args.clone(), out, o);
        for (int i = 0; i < args.length; i++) {
            if (i == o || !(args[i] instanceof GpuArray in)) {
                continue;
            }
            Object host = ACCESS.hostArray(in);
            if (!ACCESS.hostStale(in) && Marshal.byteSize(host) <= SNAPSHOT_BYTES) {
                GpuArray s = snapshot(host);
                p.args[i] = s;
                p.snapshots.add(s);
            } else {
                ResidentArrays.bufferOf(ctx, in); // связать с рантаймом, чтобы запись хоста была видна
                byInput.computeIfAbsent(in, x -> new ArrayList<>()).add(p);
                ACCESS.watch(in, true);
                p.watched.add(in);
            }
        }
        ResidentArrays.bufferOf(ctx, out);
        ACCESS.markWrittenOnDevice(out); // чтение хоста пойдёт через toHost и исполнит запуск
        ACCESS.watch(out, true);
        byOut.put(out, p);
        ctx.emit(GpuEvent.Kind.KERNEL, kernel.artifact().entry(), 0, 0, "deferred");
        return true;
    }

    /**
     * Перед запуском ядра с аргументами {@code args}: исполнить отложенные запуски, чей выход ядро получает, и те, чьи
     * наблюдаемые входы ядро пишет. Под замком.
     *
     * @param kernel ядро
     * @param args   аргументы (до привязки)
     */
    void beforeLaunch(CompiledKernel kernel, Object[] args) {
        if (byOut.isEmpty()) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof GpuArray a) {
                Pending p = byOut.get(a);
                if (p != null) {
                    materialize(p);
                }
                if ((kernel.access(i) & Validator.ACCESS_WRITE) != 0) {
                    materializeWatching(a);
                }
            }
        }
    }

    /**
     * Хост собирается писать в массив ({@code Residency.settle}).
     *
     * @param a         массив
     * @param overwrite хост перезапишет его целиком
     */
    void settle(GpuArray a, boolean overwrite) {
        ctx.lock.lock();
        try {
            Pending p = byOut.get(a);
            if (p != null) {
                if (overwrite) {
                    drop(p);
                } else {
                    materialize(p);
                }
            }
            materializeWatching(a);
        } finally {
            ctx.lock.unlock();
        }
    }

    /**
     * Перед чтением хоста: исполнить запуск, если массив - его выход.
     *
     * @param a массив
     */
    void materializeOut(GpuArray a) {
        ctx.lock.lock();
        try {
            Pending p = byOut.get(a);
            if (p != null) {
                materialize(p);
            }
        } finally {
            ctx.lock.unlock();
        }
    }

    /** Перед закрытием контекста: исполнить все отложенные запуски (под замком). */
    void materializeAll() {
        while (!byOut.isEmpty()) {
            materialize(byOut.values().iterator().next());
        }
        pool.clear();
        fused.clear();
        fusedKernels.clear();
        producers.clear();
    }

    /**
     * Свёртка, один из аргументов которой - выход отложенного запуска: строит и исполняет слитую свёртку. Под замком.
     *
     * @param reduction   модуль свёртки
     * @param args        аргументы свёртки
     * @param resultClass класс результата-агрегата либо {@code null}
     * @return результат либо {@code null}, если слияние неприменимо (свёртка исполняется обычным путём)
     */
    Result<Object> fusedReduce(IrModule reduction, Object[] args, Class<?> resultClass) {
        if (byOut.isEmpty() || !ctx.fusion) {
            return null;
        }
        for (int i = 0; i < args.length; i++) {
            if (!(args[i] instanceof GpuArray a) || !byOut.containsKey(a)) {
                continue;
            }
            Pending p = byOut.get(a);
            int r = i;
            Result<IrModule> fm = fused.computeIfAbsent(new FusedKey(p.kernel, reduction, i),
                    k -> Fusion.fuse(p.kernel.module(), reduction, r));
            if (!fm.isOk()) {
                continue;
            }
            Result<DeviceReductions.Kernel> k = ctx.reductions.kernel(fm.value());
            if (!k.isOk()) {
                continue;
            }
            // слитая свёртка: аргументы свёртки без выхода, затем аргументы производителя без out, затем длина
            Object[] full = new Object[args.length - 1 + p.args.length - 1 + 1];
            int j = 0;
            for (int q = 0; q < args.length; q++) {
                if (q != i) {
                    full[j++] = args[q];
                }
            }
            for (int q = 0; q < p.args.length; q++) {
                if (q != p.outParam) {
                    full[j++] = p.args[q];
                }
            }
            full[j] = p.out.length();
            ctx.emit(GpuEvent.Kind.KERNEL, p.kernel.artifact().entry(), 0, 0, "fused into a reduction");
            return ctx.reductions.run(k.value(), full, resultClass);
        }
        return null;
    }

    /**
     * Ядро со вставленным производителем и его аргументы.
     *
     * @param kernel слитое ядро
     * @param args   аргументы: аргументы ядра без выхода, затем аргументы производителя без {@code out}, затем длина
     */
    record FusedLaunch(CompiledKernel kernel, Object[] args) {
    }

    /**
     * Ядро, которое фасад собирается запустить, читает выход отложенного запуска поэлементно: строит ядро со вставленным
     * производителем ({@link Fusion#fuseKernel}). Отложенный запуск остаётся отложенным. Под замком.
     *
     * @param kernel ядро
     * @param args   аргументы вызова
     * @return слитое ядро и аргументы либо {@code null}, если сливать нечего или нельзя
     */
    FusedLaunch fuseInto(CompiledKernel kernel, Object[] args) {
        if (byOut.isEmpty() || !ctx.fusion || kernel.split != null) {
            return null;
        }
        for (int i = 0; i < args.length; i++) {
            if (!(args[i] instanceof GpuArray a) || !byOut.containsKey(a)
                    || (kernel.access(i) & Validator.ACCESS_WRITE) != 0) {
                continue;
            }
            Pending p = byOut.get(a);
            if (writesAnInputOf(kernel, args, p)) {
                continue; // ядро меняет то, что читает производитель: сначала исполнить производителя
            }
            int r = i;
            Result<CompiledKernel> fk = fusedKernels.computeIfAbsent(new FusedKey(p.kernel, kernel.module(), i), k -> {
                Result<IrModule> fm = Fusion.fuseKernel(p.kernel.module(), kernel.module(), r);
                return fm.isOk() ? ctx.compile(fm.value()) : Result.failed(fm.diagnostics());
            });
            if (!fk.isOk()) {
                continue;
            }
            Object[] full = new Object[args.length - 1 + p.args.length - 1 + 1];
            int j = 0;
            for (int q = 0; q < args.length; q++) {
                if (q != i) {
                    full[j++] = args[q];
                }
            }
            for (int q = 0; q < p.args.length; q++) {
                if (q != p.outParam) {
                    full[j++] = p.args[q];
                }
            }
            full[j] = p.out.length();
            ctx.emit(GpuEvent.Kind.KERNEL, p.kernel.artifact().entry(), 0, 0,
                    "fused into " + kernel.artifact().entry());
            return new FusedLaunch(fk.value(), full);
        }
        return null;
    }

    /** Ядро пишет массив, который отложенный запуск читает со своей копии на устройстве. */
    private static boolean writesAnInputOf(CompiledKernel kernel, Object[] args, Pending p) {
        for (int q = 0; q < args.length; q++) {
            if (args[q] instanceof GpuArray w && (kernel.access(q) & Validator.ACCESS_WRITE) != 0) {
                for (GpuArray in : p.watched) {
                    if (in == w) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---- внутреннее ---------------------------------------------------------------------------

    /** Исполняет отложенный запуск (под замком). */
    private void materialize(Pending p) {
        forget(p);
        Result<LaunchStats> r = ctx.launchReusing(p.kernel, p.config, p.args);
        release(p);
        if (!r.isOk()) {
            throw new GpuException(r.diagnostics());
        }
    }

    /** Исполняет отложенные запуски, читающие массив со своей копии на устройстве. */
    private void materializeWatching(GpuArray a) {
        List<Pending> ws = byInput.get(a);
        if (ws != null) {
            for (Pending p : new ArrayList<>(ws)) {
                materialize(p);
            }
        }
    }

    /** Отбрасывает запуск, результат которого никто не увидит. */
    private void drop(Pending p) {
        forget(p);
        release(p);
    }

    /** Убирает запуск из таблиц и снимает наблюдение с массивов, которые больше ни в чём не участвуют. */
    private void forget(Pending p) {
        byOut.remove(p.out);
        for (GpuArray in : p.watched) {
            List<Pending> ws = byInput.get(in);
            if (ws != null) {
                ws.remove(p);
                if (ws.isEmpty()) {
                    byInput.remove(in);
                }
            }
            ACCESS.watch(in, byInput.containsKey(in) || byOut.containsKey(in));
        }
        ACCESS.watch(p.out, byInput.containsKey(p.out));
    }

    /** Возвращает снимки запуска в пул (их данные уже переданы на устройство либо не нужны). */
    private void release(Pending p) {
        for (GpuArray s : p.snapshots) {
            pool.computeIfAbsent(poolKey(ACCESS.hostArray(s)), x -> new ArrayDeque<>()).push(s);
        }
        p.snapshots.clear();
    }

    /** Снимок данных хоста входа: свободный массив из пула либо новый. */
    private GpuArray snapshot(Object host) {
        ArrayDeque<GpuArray> free = pool.get(poolKey(host));
        GpuArray s = free == null || free.isEmpty() ? newArray(host) : free.pop();
        switch (s) {
            case GpuFloatArray x -> x.copyFrom((float[]) host);
            case GpuIntArray x -> x.copyFrom((int[]) host);
            case GpuLongArray x -> x.copyFrom((long[]) host);
            case GpuDoubleArray x -> x.copyFrom((double[]) host);
        }
        return s;
    }

    private static String poolKey(Object host) {
        return host.getClass().getName() + ":" + java.lang.reflect.Array.getLength(host);
    }

    private static GpuArray newArray(Object host) {
        int n = java.lang.reflect.Array.getLength(host);
        return switch (host) {
            case float[] a -> new GpuFloatArray(n);
            case int[] a -> new GpuIntArray(n);
            case long[] a -> new GpuLongArray(n);
            case double[] a -> new GpuDoubleArray(n);
            default -> throw new IllegalStateException("unexpected host array " + Arrays.toString(new Object[]{host}));
        };
    }
}
