package com.sixik.gpu.runtime;

import com.sixik.gpu.api.internal.GpuArrayAccess;
import com.sixik.gpu.api.types.GpuArray;

import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Резидентность массивов {@link GpuArray} ({@code GpuFloatArray} и др.) на устройстве.
 *
 * <p>Перед запуском ядра каждый такой аргумент заменяется буфером устройства ({@link GpuBuffer}), закреплённым за
 * массивом в этом контексте. Массив загружается, только если копия на устройстве устарела ({@code set/fill/copyFrom} на
 * хосте) - и даже тогда не загружается, если ядро его не читает, а перезаписывает целиком ({@code WriteCoverage}).
 * После успешного запуска массив, который ядро записало, помечается «свежий на устройстве»: на хост он читается
 * лениво, при первом обращении хоста ({@link #RESIDENCY}).</p>
 *
 * <p>Время жизни буфера: он освобождается, когда массив собирает GC ({@link Cleaner}), когда массив переходит в другой
 * контекст и при закрытии контекста - в последнем случае свежие данные сначала возвращаются на хост
 * ({@link #syncAll}), поэтому закрытие контекста данных не теряет.</p>
 */
final class ResidentArrays {

    private static final Cleaner CLEANER = Cleaner.create();
    /** Служебный протокол синхронизации {@link GpuArray}. */
    static final GpuArrayAccess ACCESS = GpuArrayAccess.get();

    private ResidentArrays() {
    }

    /** Копия массива на устройстве. */
    static final class Resident {
        final GpuContext ctx;
        final GpuBuffer buffer;

        Resident(GpuContext ctx, GpuBuffer buffer) {
            this.ctx = ctx;
            this.buffer = buffer;
        }
    }

    /** Чтение свежих данных с устройства на хост при обращении хоста к массиву. */
    static final GpuArrayAccess.Residency RESIDENCY = array -> {
        Resident r = (Resident) ACCESS.residence(array);
        r.buffer.read(ACCESS.hostArray(array));
        ACCESS.markInSync(array);
    };

    /**
     * Аргументы запуска после привязки: {@link GpuArray} заменены буферами устройства.
     *
     * @param args    аргументы для обычного пути запуска
     * @param arrays  привязанные массивы
     * @param params  по массиву - номера параметров, которым он передан
     * @param skipped по массиву - загрузка пропущена (ядро перезаписывает массив целиком)
     */
    record Bound(Object[] args, List<GpuArray> arrays, List<int[]> params, boolean[] skipped) {
        /** @return {@code true}, если среди аргументов не было {@link GpuArray} */
        boolean none() {
            return arrays.isEmpty();
        }
    }

    /**
     * Заменяет {@link GpuArray} буферами устройства и загружает устаревшие копии. Вызывается под замком контекста.
     *
     * @param ctx    контекст запуска
     * @param kernel ядро
     * @param config размеры запуска (для проверки полного покрытия записью)
     * @param args   исходные аргументы
     * @return привязка (аргументы те же объекты, если {@link GpuArray} нет)
     */
    static Bound bind(GpuContext ctx, CompiledKernel kernel, LaunchConfig config, Object[] args) {
        Map<GpuArray, List<Integer>> byArray = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof GpuArray a) {
                if (byArray == null) {
                    byArray = new IdentityHashMap<>();
                }
                byArray.computeIfAbsent(a, x -> new ArrayList<>()).add(i);
            }
        }
        if (byArray == null) {
            return new Bound(args, List.of(), List.of(), new boolean[0]);
        }
        Object[] out = args.clone();
        List<GpuArray> arrays = new ArrayList<>(byArray.keySet());
        List<int[]> params = new ArrayList<>();
        boolean[] skipped = new boolean[arrays.size()];
        for (int j = 0; j < arrays.size(); j++) {
            GpuArray a = arrays.get(j);
            int[] ps = byArray.get(a).stream().mapToInt(Integer::intValue).toArray();
            params.add(ps);
            Resident r = residentIn(ctx, a);
            if (ACCESS.deviceStale(a)) {
                if (kernel.uploadSkippable(ps, ps.length, a.length(), config, args)) {
                    skipped[j] = true;
                } else {
                    // без ожидания: in-order очередь выполнит загрузку раньше ядра
                    ctx.uploadAsync(r.buffer, ACCESS.hostArray(a));
                    ACCESS.markInSync(a);
                }
            }
            for (int p : ps) {
                out[p] = r.buffer;
            }
        }
        return new Bound(out, arrays, params, skipped);
    }

    /**
     * После успешного запуска: массивы, которые ядро записало, свежие на устройстве.
     *
     * @param kernel ядро
     * @param bound  привязка запуска
     */
    static void afterSuccess(CompiledKernel kernel, Bound bound) {
        for (int j = 0; j < bound.arrays().size(); j++) {
            boolean written = false;
            for (int p : bound.params().get(j)) {
                written |= (kernel.access(p) & com.sixik.gpu.ir.Validator.ACCESS_WRITE) != 0;
            }
            if (written || bound.skipped()[j]) {
                ACCESS.markWrittenOnDevice(bound.arrays().get(j));
            }
        }
    }

    /**
     * Буфер устройства, закреплённый за массивом в контексте (создаётся при необходимости, без загрузки данных). Под
     * замком контекста.
     *
     * @param ctx контекст
     * @param a   массив
     * @return буфер массива в этом контексте
     */
    static GpuBuffer bufferOf(GpuContext ctx, GpuArray a) {
        return residentIn(ctx, a).buffer;
    }

    /** Копия массива в контексте {@code ctx}: существующая либо новая (с переносом из другого контекста). */
    private static Resident residentIn(GpuContext ctx, GpuArray a) {
        if (ACCESS.residence(a) instanceof Resident r) {
            if (r.ctx == ctx && !r.buffer.isClosed()) {
                return r;
            }
            // массив жил в другом контексте: сначала свежие данные на хост, затем освободить старую копию
            if (ACCESS.hostStale(a) && !r.ctx.isClosed() && !r.buffer.isClosed()) {
                RESIDENCY.toHost(a);
            }
            r.buffer.close();
            ACCESS.detach(a);
        }
        int type = Marshal.elementTypeOf(ACCESS.hostArray(a));
        GpuBuffer buffer = ctx.allocate(type, a.length());
        Resident r = new Resident(ctx, buffer);
        ACCESS.attach(a, RESIDENCY, r);
        ctx.registerResident(a);
        // массив собран GC - освободить буфер; действие не должно ссылаться на сам массив
        CLEANER.register(a, buffer::close);
        return r;
    }

    /**
     * Перед закрытием контекста: вернуть на хост свежие данные всех его массивов и отвязать их. Вызывается под замком
     * контекста, пока он ещё открыт.
     *
     * @param ctx       закрываемый контекст
     * @param residents массивы, привязанные к контексту
     */
    static void syncAll(GpuContext ctx, Iterable<GpuArray> residents) {
        for (GpuArray a : residents) {
            if (ACCESS.residence(a) instanceof Resident r && r.ctx == ctx) {
                if (ACCESS.hostStale(a) && !r.buffer.isClosed()) {
                    try {
                        RESIDENCY.toHost(a);
                    } catch (RuntimeException e) {
                        // устройство отказало при закрытии: данные массива остаются прежними на хосте
                    }
                }
                ACCESS.detach(a);
            }
        }
    }
}
