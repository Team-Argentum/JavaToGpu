package com.sixik.gpu.runtime;

import com.sixik.gpu.api.types.GpuArray;
import com.sixik.gpu.codegen.opencl.KernelArg;
import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.HostObjects;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;
import com.sixik.gpu.runtime.device.DeviceApi;
import com.sixik.gpu.runtime.device.DeviceApiException;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Подготовленный запуск ядра для горячего пути (REWORK_ARCHITECTURE.md, 9, 5.23): аргументы проверены и привязаны один раз,
 * буферы и нативная память созданы один раз, повторный {@link #launch} делает только то, что действительно изменилось.
 * Единственная реализация вместо трёх вложенных слоёв прежней версии.
 *
 * <h2>Владение вместо флагов</h2>
 * <p>Что передаётся между хостом и устройством, определяется <b>тем, кому принадлежит память</b>, а не масками
 * {@code withoutHostUpload/Readback}:</p>
 * <ul>
 *   <li><b>{@link GpuBuffer}</b> - данные живут на устройстве и принадлежат пользователю: запуск не передаёт их ни
 *       туда, ни обратно. Так выражаются static-аргументы (таблица, загруженная один раз) и цепочки ядер без хоста.</li>
 *   <li><b>Массив хоста</b> - буфер на устройстве принадлежит этому запуску ({@link #close} освободит его). Перед каждым
 *       запуском массив целиком записывается в буфер (как и при обычном вызове: нетронутые ядром элементы обязаны
 *       сохраниться, а пользователь мог изменить массив между запусками); после запуска назад в массив возвращается
 *       только то, что ядро <b>записывает</b> (режим доступа считает валидатор IR). Длина массива не должна меняться.</li>
 *   <li><b>{@code GpuArray}</b> ({@code GpuFloatArray} и др.) - буфер закреплён за самим массивом, как при прямом вызове
 *       ядра: перед запуском массив загружается, только если хост его менял (и ядро его читает); записанный ядром
 *       массив остаётся свежим на устройстве и читается на хост лениво. Запуск только с {@code GpuArray},
 *       {@link GpuBuffer} и скалярами не ждёт завершения ядра (асинхронная цепочка).</li>
 *   <li><b>Скаляр или вектор/struct по значению</b> - заменяется {@link #set}, остальные аргументы остаются привязанными.</li>
 * </ul>
 *
 * <h2>Что экономится относительно {@link GpuContext#launch}</h2>
 * <p>Не создаются и не освобождаются буферы устройства, не выделяется нативная память под упаковку (одна область на
 * массив, переиспользуется), аргументы {@code cl_kernel} выставляются только если изменились или если после этого
 * запуска ядро запускал кто-то другой (объект драйвера общий для всех запусков одного ядра), аргументы не
 * проверяются заново.</p>
 *
 * <p>Объект <b>не потокобезопасен</b> (как {@link GpuBuffer}); обращения к драйверу внутри сериализует замок контекста.
 * Закрывается {@link #close()} либо вместе с контекстом.</p>
 */
public final class PreparedLauncher implements AutoCloseable {

    /** Массив хоста, привязанный к собственному буферу на устройстве. */
    private static final class Slot {
        final Object array;
        final int elemType;
        final boolean objects;
        final int length;
        final long bytes;
        final long handle;
        final MemorySegment staging;
        /** Закреплённый буфер, которому принадлежит {@link #staging}, либо {@code null} для обычной памяти. */
        final DeviceApi.Pinned pinned;
        int access;
        /** Параметры ядра, которым передан этот массив (первые {@link #paramCount}). */
        int[] params = new int[4];
        int paramCount;

        Slot(Object array, int elemType, boolean objects, int length, long bytes, long handle, MemorySegment staging,
             DeviceApi.Pinned pinned) {
            this.pinned = pinned;
            this.array = array;
            this.elemType = elemType;
            this.objects = objects;
            this.length = length;
            this.bytes = bytes;
            this.handle = handle;
            this.staging = staging;
        }
    }

    /**
     * Начиная с этого размера массив хоста передаётся через закреплённую память ({@link DeviceApi#createPinned}). Закрепление
     * само стоит времени, поэтому выгодно для крупных буферов, которые подготовленный запуск переиспользует многократно.
     */
    static final long PINNED_BYTES = 1L << 20;

    private final GpuContext ctx;
    private final CompiledKernel kernel;
    private final IrModule module;
    private final Object[] args;
    private final Slot[] slotOf;
    private final Slot[] slots;
    private final long[] handles;
    private final long[] lengths;
    private final boolean[] dirty;
    /** По параметру - массив {@code GpuArray}, переданный в нём, либо {@code null}. */
    private final GpuArray[] residents;
    private final boolean hasValueAggregates;
    private final Arena staging;
    private boolean closed;

    private PreparedLauncher(GpuContext ctx, CompiledKernel kernel, Object[] args, Slot[] slotOf, Slot[] slots,
                             long[] handles, long[] lengths, Arena staging, GpuArray[] residents) {
        this.ctx = ctx;
        this.kernel = kernel;
        this.module = kernel.module();
        this.args = args;
        this.slotOf = slotOf;
        this.slots = slots;
        this.handles = handles;
        this.lengths = lengths;
        this.dirty = new boolean[args.length];
        this.residents = residents;
        this.staging = staging;
        boolean aggregates = false;
        for (int i = 0; i < args.length; i++) {
            int t = module.fParamType(kernel.function(), i);
            aggregates |= module.typeKind(t) != Types.KIND_ARRAY && HostObjects.isAggregate(module, t);
        }
        this.hasValueAggregates = aggregates;
    }

    /**
     * Привязывает аргументы к ядру и создаёт буферы под массивы хоста. Вызывается из {@link GpuContext#prepare}.
     *
     * @return запуск либо диагностики ({@code RT-A01/A02/A04}, {@code RT-X01}, {@code RT-X02})
     */
    static Result<PreparedLauncher> create(GpuContext ctx, CompiledKernel kernel, Object[] given,
                                           GpuArray[] residents) {
        Object[] args = given.clone();
        List<Diagnostic> problems = new ArrayList<>();
        ctx.checkArguments(kernel, args, problems);
        if (problems.isEmpty()) {
            ctx.checkConstantArguments(kernel, args, problems);
        }
        if (!problems.isEmpty()) {
            return Result.failed(problems);
        }
        IrModule m = kernel.module();
        int f = kernel.function();
        int n = args.length;
        Slot[] slotOf = new Slot[n];
        Map<Object, Slot> byArray = new IdentityHashMap<>();
        List<Slot> slots = new ArrayList<>();
        long[] handles = new long[n];
        long[] lengths = new long[n];
        Arena staging = Arena.ofShared();
        ctx.lock.lock();
        try {
            if (ctx.closed) {
                staging.close();
                return GpuContext.failure("RT-X02", "context is closed");
            }
            for (int i = 0; i < n; i++) {
                if (m.typeKind(m.fParamType(f, i)) != Types.KIND_ARRAY) {
                    continue;
                }
                if (args[i] instanceof GpuBuffer b) {
                    handles[i] = b.handle();
                    lengths[i] = b.length();
                    continue;
                }
                Slot slot = byArray.get(args[i]);
                if (slot == null) {
                    int elem = Types.elem(m, m.fParamType(f, i));
                    boolean objects = HostObjects.isAggregate(m, elem);
                    long bytes = objects ? Marshal.byteSize(m, elem, (Object[]) args[i]) : Marshal.byteSize(args[i]);
                    long handle = ctx.api.createBuffer(ctx.contextHandle, Math.max(bytes, 4));
                    // крупные массивы - через закреплённую память: DMA вдвое быстрее, чем из обычной
                    DeviceApi.Pinned pin = bytes >= PINNED_BYTES ? ctx.api.createPinned(ctx.contextHandle, ctx.queue, bytes)
                            : null;
                    slot = new Slot(args[i], elem, objects, Marshal.length(args[i]), bytes, handle,
                            pin != null ? pin.memory() : staging.allocate(Math.max(bytes, 8), 8), pin);
                    byArray.put(args[i], slot);
                    slots.add(slot);
                }
                slot.access |= kernel.access(i);
                if (slot.paramCount == slot.params.length) {
                    slot.params = Arrays.copyOf(slot.params, slot.paramCount * 2);
                }
                slot.params[slot.paramCount++] = i;
                slotOf[i] = slot;
                handles[i] = slot.handle;
                lengths[i] = slot.length;
            }
        } catch (DeviceApiException e) {
            for (Slot s : slots) {
                ctx.api.release(DeviceApi.HandleKind.BUFFER, s.handle);
                if (s.pinned != null) {
                    ctx.api.releasePinned(ctx.queue, s.pinned);
                }
            }
            staging.close();
            return GpuContext.failure("RT-X01", ctx.driverError("preparing the launch", e));
        } finally {
            ctx.lock.unlock();
        }
        PreparedLauncher launcher = new PreparedLauncher(ctx, kernel, args, slotOf, slots.toArray(new Slot[0]), handles,
                lengths, staging, residents.clone());
        Arrays.fill(launcher.dirty, true);
        return Result.ok(launcher);
    }

    /** @return суммарный размер массивов хоста, для которых запуск держит буферы устройства и промежуточную память */
    long hostBytes() {
        long b = 0;
        for (Slot s : slots) {
            b += s.bytes;
        }
        return b;
    }

    /** @return ядро, к которому привязан запуск */
    public CompiledKernel kernel() {
        return kernel;
    }

    /** @return {@code true}, если запуск закрыт (явно или вместе с контекстом) */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Заменяет значение скалярного (или векторного/struct) аргумента либо подставляет другой {@link GpuBuffer} вместо
     * прежнего буфера. Остальные аргументы остаются привязанными; на устройство уйдёт только изменённый.
     *
     * @param param номер параметра ядра
     * @param value новое значение того же вида, что и прежнее
     * @return этот запуск
     * @throws GpuException если запуск закрыт, номер неверен, значение не подходит параметру либо параметр - массив
     *                      хоста (его замена потребовала бы нового буфера: подготовьте новый запуск)
     */
    public PreparedLauncher set(int param, Object value) {
        requireOpen();
        if (param < 0 || param >= args.length) {
            throw new GpuException(List.of(GpuContext.error("RT-A01", "the kernel has " + args.length + " parameters, index " + param)));
        }
        if (slotOf[param] != null) {
            throw new GpuException(List.of(GpuContext.error("RT-A02", "argument " + param
                    + " is a host array with this launch's buffer: it cannot be replaced, prepare a new launch or "
                    + "pass a GpuBuffer")));
        }
        if (value instanceof GpuArray a) {
            setResident(param, a);
            return this;
        }
        List<Diagnostic> problems = new ArrayList<>();
        ctx.checkArgument(kernel, param, value, problems);
        boolean array = module.typeKind(module.fParamType(kernel.function(), param)) == Types.KIND_ARRAY;
        if (problems.isEmpty() && array) {
            Object[] trial = args.clone();
            trial[param] = value;
            ctx.checkConstantArguments(kernel, trial, problems);
        }
        if (!problems.isEmpty()) {
            throw new GpuException(problems);
        }
        args[param] = value;
        residents[param] = null;
        if (array) {
            GpuBuffer b = (GpuBuffer) value;
            handles[param] = b.handle();
            lengths[param] = b.length();
        }
        dirty[param] = true;
        return this;
    }

    /** {@link #set} с массивом {@code GpuArray}: буфер, закреплённый за массивом в этом контексте. */
    private void setResident(int param, GpuArray a) {
        ctx.lock.lock();
        try {
            if (ctx.closed) {
                throw new GpuException(List.of(GpuContext.error("RT-X02", "context is closed")));
            }
            GpuBuffer b;
            try {
                b = ResidentArrays.bufferOf(ctx, a);
            } catch (DeviceApiException e) {
                throw new GpuException(List.of(GpuContext.error("RT-X01", ctx.driverError("allocating a buffer", e))));
            }
            List<Diagnostic> problems = new ArrayList<>();
            ctx.checkArgument(kernel, param, b, problems);
            if (!problems.isEmpty()) {
                throw new GpuException(problems);
            }
            residents[param] = a;
            args[param] = b;
            handles[param] = b.handle();
            lengths[param] = b.length();
            dirty[param] = true;
        } finally {
            ctx.lock.unlock();
        }
    }

    /**
     * Запускает ядро; ошибки - исключением.
     *
     * @param config размеры запуска
     * @return тайминги и объёмы передачи
     * @throws GpuException если запуск закрыт, размеры недопустимы, аргументы изменились или драйвер вернул ошибку
     */
    public LaunchStats run(LaunchConfig config) {
        Result<LaunchStats> r = launch(config);
        if (!r.isOk()) {
            throw new GpuException(r.diagnostics());
        }
        return r.value();
    }

    /**
     * Запускает ядро. Массивы хоста, которые ядро записывает, обновляются только при успехе.
     *
     * @param config размеры запуска
     * @return итог либо диагностики: {@code RT-L01} (размеры), {@code RT-A02} (длина массива хоста изменилась или
     *         {@link GpuBuffer} закрыт), {@code RT-X01} (ошибка драйвера), {@code RT-X02} (запуск или контекст закрыты)
     */
    public Result<LaunchStats> launch(LaunchConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config is required");
        }
        if (closed || ctx.closed) {
            return GpuContext.failure("RT-X02", closed ? "the launch is closed" : "context is closed");
        }
        List<Diagnostic> problems = new ArrayList<>();
        long[] local = ctx.checkLaunch(kernel, config, problems);
        for (Slot s : slots) {
            if (Marshal.length(s.array) != s.length) {
                problems.add(GpuContext.error("RT-A02", "host array length changed from " + s.length + " to "
                        + Marshal.length(s.array) + ": the prepared launch buffer was created for the previous length"));
            }
        }
        for (int i = 0; i < args.length; i++) {
            if (residents[i] == null && args[i] instanceof GpuBuffer b && b.isClosed()) {
                problems.add(GpuContext.error("RT-A02", "argument " + i + ": the buffer is closed"));
            }
        }
        if (!problems.isEmpty()) {
            return Result.failed(problems);
        }
        ctx.lock.lock();
        try {
            if (ctx.closed) {
                return GpuContext.failure("RT-X02", "context is closed");
            }
            return residentCount() == 0 ? execute(config, local) : executeWithResidents(config, local);
        } catch (GpuException e) {
            kernel.boundTo = null;
            return Result.failed(e.diagnostics());
        } catch (DeviceApiException e) {
            kernel.boundTo = null;
            return GpuContext.failure("RT-X01", ctx.driverError("launching a kernel", e));
        } finally {
            ctx.lock.unlock();
        }
    }

    /** @return число параметров, которым передан {@code GpuArray} */
    private int residentCount() {
        int c = 0;
        for (GpuArray a : residents) {
            if (a != null) {
                c++;
            }
        }
        return c;
    }

    /**
     * Запуск с {@code GpuArray} под замком контекста: загрузить устаревшие копии, подставить буферы массивов (буфер
     * меняется, если массив побывал в другом контексте), запустить и отметить записанные массивы свежими на устройстве.
     */
    private Result<LaunchStats> executeWithResidents(LaunchConfig config, long[] local) {
        Object[] template = args.clone();
        for (int i = 0; i < residents.length; i++) {
            if (residents[i] != null) {
                template[i] = residents[i];
            }
        }
        ResidentArrays.Bound bound = ResidentArrays.bind(ctx, kernel, config, template);
        for (int i = 0; i < residents.length; i++) {
            if (residents[i] != null && bound.args()[i] != args[i]) {
                GpuBuffer b = (GpuBuffer) bound.args()[i];
                args[i] = b;
                handles[i] = b.handle();
                lengths[i] = b.length();
                dirty[i] = true;
            }
        }
        boolean previous = ctx.allowDefer(true);
        Result<LaunchStats> r;
        try {
            r = execute(config, local);
        } finally {
            ctx.allowDefer(previous);
        }
        if (r.isOk()) {
            ResidentArrays.afterSuccess(kernel, bound);
        }
        return r;
    }

    /** Один запуск под замком контекста. */
    private Result<LaunchStats> execute(LaunchConfig config, long[] local) {
        DeviceApi api = ctx.api;
        long up = 0;
        long t0 = System.nanoTime();
        for (Slot s : slots) {
            if (s.bytes > 0 && !kernel.uploadSkippable(s.params, s.paramCount, s.length, config)) {
                if (s.objects) {
                    Marshal.copyToNative(module, s.elemType, (Object[]) s.array, s.staging);
                } else {
                    Marshal.copyToNativeFast(s.array, s.staging);
                }
                // staging живёт дольше запуска, а dispatch ждёт очередь: блокировать запись незачем
                api.writeBufferAsync(ctx.queue, s.handle, 0, s.bytes, s.staging);
                up += s.bytes;
            }
        }
        long upNanos = System.nanoTime() - t0;
        ctx.emit(GpuEvent.Kind.UPLOAD, kernel.artifact().entry(), upNanos, up, "");

        // без массивов хоста промежуточная память не переиспользуется и читать нечего: можно не ждать ядро
        boolean defer = slots.length == 0 && ctx.deferAllowedNow();
        long kernelNanos;
        if (ctx.splitFor(kernel, config, local) == null) {
            bindArguments();
            kernelNanos = ctx.dispatch(kernel, config, local, defer);
        } else {
            // по стадиям: у каждой стадии свой cl_kernel, аргументы выставляются заново
            try (Arena temp = Arena.ofConfined()) {
                kernelNanos = ctx.dispatchKernel(kernel, config, local, args, handles, lengths, temp, defer);
            }
        }

        long t2 = System.nanoTime();
        long down = 0;
        for (Slot s : slots) {
            if ((s.access & Validator.ACCESS_WRITE) != 0 && s.bytes > 0) {
                api.readBuffer(ctx.queue, s.handle, 0, s.bytes, s.staging);
                if (s.objects) {
                    Marshal.fromNative(s.staging, module, s.elemType, (Object[]) s.array);
                } else {
                    Marshal.fromNativeFast(s.staging, s.array);
                }
                down += s.bytes;
            }
        }
        long downNanos = System.nanoTime() - t2;
        ctx.emit(GpuEvent.Kind.READBACK, kernel.artifact().entry(), downNanos, down, "");
        return Result.ok(new LaunchStats(upNanos, kernelNanos, downNanos, up, down));
    }

    /** Выставляет аргументы {@code cl_kernel}: все, если ядро запускал кто-то другой, иначе только изменённые. */
    private void bindArguments() {
        boolean all = kernel.boundTo != this;
        kernel.boundTo = null;
        Arena temp = hasValueAggregates ? Arena.ofConfined() : null;
        try {
            List<KernelArg> list = kernel.artifact().args();
            for (int i = 0; i < list.size(); i++) {
                if (all || dirty[list.get(i).param()]) {
                    ctx.setKernelArg(kernel, i, args, handles, lengths, temp);
                }
            }
        } finally {
            if (temp != null) {
                temp.close();
            }
        }
        Arrays.fill(dirty, false);
        kernel.boundTo = this;
    }

    private void requireOpen() {
        if (closed) {
            throw new GpuException(List.of(GpuContext.error("RT-X02", "the launch is closed")));
        }
    }

    /**
     * Освобождает буферы массивов хоста и нативную память упаковки. {@link GpuBuffer}, переданные пользователем,
     * не трогаются. Повторный вызов безопасен.
     */
    @Override
    public void close() {
        ctx.lock.lock();
        try {
            releaseLocked();
            ctx.forget(this);
        } finally {
            ctx.lock.unlock();
        }
    }

    /** Освобождение под замком контекста; вызывается и из {@link GpuContext#close()}. */
    void releaseLocked() {
        if (closed) {
            return;
        }
        closed = true;
        if (kernel.boundTo == this) {
            kernel.boundTo = null;
        }
        if (!ctx.closed) {
            // неблокирующая запись могла ещё читать staging: дождаться очереди, прежде чем освобождать память
            try {
                ctx.api.finish(ctx.queue);
            } catch (DeviceApiException ignored) {
                // очередь сломана - освобождаем как есть
            }
            for (Slot s : slots) {
                ctx.api.release(DeviceApi.HandleKind.BUFFER, s.handle);
                if (s.pinned != null) {
                    ctx.api.releasePinned(ctx.queue, s.pinned);
                }
            }
        }
        staging.close();
    }

    @Override
    public String toString() {
        return "PreparedLauncher[" + kernel.artifact().entry() + (closed ? ", closed" : "") + "]";
    }
}
