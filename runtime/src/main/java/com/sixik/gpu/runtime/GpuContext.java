package com.sixik.gpu.runtime;

import com.sixik.gpu.codegen.opencl.Artifact;
import com.sixik.gpu.codegen.opencl.EmitOptions;
import com.sixik.gpu.codegen.opencl.KernelArg;
import com.sixik.gpu.codegen.opencl.OpenClEmitter;
import com.sixik.gpu.codegen.opencl.Target;
import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.Features;
import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;
import com.sixik.gpu.runtime.device.DeviceApi;
import com.sixik.gpu.runtime.device.DeviceApiException;
import com.sixik.gpu.runtime.device.DeviceInfo;
import com.sixik.gpu.runtime.device.DevicePolicy;
import com.sixik.gpu.runtime.device.OpenClApi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Контекст запуска: одно выбранное устройство, очередь, кэш скомпилированных ядер и буферы
 * (REWORK_ARCHITECTURE.md, 9, 9.1, 9.4, 9.5). v1 - ровно одно активное устройство, запуск синхронный.
 *
 * <h2>Поток работы</h2>
 * <ol>
 *   <li>{@link #builder()} {@code ...tryBuild()}: найти устройство по политике; нет драйвера или подходящего
 *       устройства - {@code Result} с {@code RT-D01}, а не исключение при старте;</li>
 *   <li>{@link #compile}: IR &rarr; OpenCL C ({@code codegen-opencl}) &rarr; программа драйвера; результат в кэше
 *       (ключ - хэш IR без отладочных данных, номер ядра, параметры генерации, устройство и драйвер; одна компиляция на
 *       ключ, остальные потоки ждут её); ошибки драйвера разобраны в диагностики {@code RT-C01/RT-C02};</li>
 *   <li>{@link #launch}: проверить аргументы и размеры, передать массивы, выполнить, вернуть записанные массивы.
 *       {@link #run} делает {@code compile + launch} и бросает {@link GpuException}.</li>
 * </ol>
 *
 * <h2>Массивы хоста как сахар</h2>
 * <p>Java-массив в аргументах превращается во временный буфер. Передаются <b>все</b> массивы (даже те, что ядро только
 * пишет: иначе нетронутые элементы вернулись бы мусором, а JVM-семантика требует, чтобы они сохранились), а на хост
 * возвращаются только те, что ядро <b>записывает</b> (режим доступа считает валидатор IR). Один и тот же массив,
 * переданный дважды, - один буфер, как в JVM. Чтобы данные не ходили через хост между запусками, их держат в
 * {@link GpuBuffer}.</p>
 *
 * <h2>Потоки</h2>
 * <p>Контекст потокобезопасен: любые обращения к драйверу и очереди сериализует один замок, поэтому запуски на
 * устройстве идут по очереди; кэш компиляции конкурентный. {@link GpuBuffer} сам по себе не потокобезопасен.</p>
 */
public final class GpuContext implements AutoCloseable {

    private static final class Entry {
        Result<CompiledKernel> result;
    }

    private final DeviceApi api;
    private final boolean ownsApi;
    private final DeviceInfo device;
    private final long contextHandle;
    private final long queue;
    private final Target target;
    private final EventListener listener;
    private final EmitOptions emitOptions;
    private final Fallback fallback;
    private final Set<String> fallbackReported = ConcurrentHashMap.newKeySet();

    private final ReentrantLock lock = new ReentrantLock();
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();
    private final List<CompiledKernel> compiled = new ArrayList<>();
    private final Set<GpuBuffer> buffers = ConcurrentHashMap.newKeySet();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private volatile boolean closed;

    private GpuContext(DeviceApi api, boolean ownsApi, DeviceInfo device, long contextHandle, long queue,
                       EventListener listener, EmitOptions emitOptions, Fallback fallback) {
        this.api = api;
        this.ownsApi = ownsApi;
        this.device = device;
        this.contextHandle = contextHandle;
        this.queue = queue;
        this.listener = listener;
        this.emitOptions = emitOptions;
        this.fallback = fallback;
        this.target = new Target("opencl", device.vendor().toLowerCase(java.util.Locale.ROOT).replaceAll("\\W+", "-"),
                device.isGpu() ? "gpu" : device.isCpu() ? "cpu" : "accelerator", device.features(), 0,
                device.computeUnits());
    }

    // =============================================================================================
    // создание
    // =============================================================================================

    /** @return построитель контекста */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Что делать фасаду {@link JavaToGpu}, когда ядро нельзя выполнить на устройстве (REWORK_ARCHITECTURE.md, 3.8).
     */
    public enum Fallback {
        /** Выполнить ядро на JVM и сообщить об этом одним предупреждением в {@link EventListener}. */
        ALLOWED,
        /** Не уходить на JVM: бросить {@link GpuException}. */
        NEVER
    }

    /** Построитель {@link GpuContext}. */
    public static final class Builder {
        private DeviceApi api;
        private DevicePolicy policy = DevicePolicy.DEFAULT;
        private boolean environment = true;
        private EventListener listener = EventListener.NONE;
        private EmitOptions emitOptions = EmitOptions.RELEASE;
        private Fallback fallback = Fallback.ALLOWED;

        private Builder() {
        }

        /**
         * Слой доступа к драйверу. По умолчанию загружается системный OpenCL. Переданным слоем владеет вызывающий:
         * контекст его не закрывает.
         *
         * @param api слой доступа
         * @return этот построитель
         */
        public Builder api(DeviceApi api) {
            this.api = api;
            return this;
        }

        /**
         * @param policy политика выбора устройства
         * @return этот построитель
         */
        public Builder policy(DevicePolicy policy) {
            this.policy = policy;
            return this;
        }

        /**
         * Не читать явный выбор устройства из {@code javatogpu.device} / {@code JAVATOGPU_DEVICE}.
         *
         * @return этот построитель
         */
        public Builder ignoreEnvironment() {
            this.environment = false;
            return this;
        }

        /**
         * @param listener приёмник событий
         * @return этот построитель
         */
        public Builder listener(EventListener listener) {
            this.listener = listener;
            return this;
        }

        /**
         * @param options параметры генерации OpenCL C по умолчанию (release, строгий float)
         * @return этот построитель
         */
        public Builder emitOptions(EmitOptions options) {
            this.emitOptions = options;
            return this;
        }

        /**
         * @param fallback поведение при невозможности выполнить ядро на устройстве (по умолчанию {@link Fallback#ALLOWED})
         * @return этот построитель
         */
        public Builder fallback(Fallback fallback) {
            this.fallback = fallback;
            return this;
        }

        /**
         * Создаёт контекст; отсутствие драйвера или устройства - значение, а не исключение.
         *
         * @return контекст либо {@code RT-D01} (устройств нет), {@code RT-D03} (ошибка драйвера)
         */
        public Result<GpuContext> tryBuild() {
            DeviceApi a = api;
            boolean owns = false;
            if (a == null) {
                a = OpenClApi.load();
                owns = true;
                if (a == null) {
                    return failure("RT-D01", "библиотека OpenCL не найдена: устройств нет");
                }
            }
            try {
                List<DeviceInfo> all = DeviceInfo.enumerate(a);
                DevicePolicy p = environment && policy.override() == null ? policy.withEnvironment() : policy;
                Optional<DeviceInfo> chosen = p.select(all);
                if (chosen.isEmpty()) {
                    closeIfOwned(a, owns);
                    return failure("RT-D01", all.isEmpty()
                            ? "OpenCL-устройств нет"
                            : "ни одно из " + all.size() + " устройств не подходит под политику " + p + ": "
                            + describeAll(all));
                }
                DeviceInfo d = chosen.get();
                long ctx = a.createContext(d.handle());
                long q;
                try {
                    q = a.createQueue(ctx, d.handle());
                } catch (DeviceApiException e) {
                    a.release(DeviceApi.HandleKind.CONTEXT, ctx);
                    throw e;
                }
                GpuContext gc = new GpuContext(a, owns, d, ctx, q, listener == null ? EventListener.NONE : listener,
                        emitOptions, fallback == null ? Fallback.ALLOWED : fallback);
                gc.emit(GpuEvent.Kind.DEVICE_SELECTED, d.describe(), 0, 0, d.platformName() + ", драйвер " + d.driverVersion());
                return Result.ok(gc);
            } catch (DeviceApiException e) {
                closeIfOwned(a, owns);
                return failure("RT-D03", "ошибка драйвера при выборе устройства: " + e.getMessage());
            }
        }

        /**
         * То же, но с исключением на границе.
         *
         * @return контекст
         * @throws GpuException если устройства нет или драйвер вернул ошибку
         */
        public GpuContext build() {
            Result<GpuContext> r = tryBuild();
            if (!r.isOk()) {
                throw new GpuException(r.diagnostics());
            }
            return r.value();
        }

        private static String describeAll(List<DeviceInfo> all) {
            StringBuilder sb = new StringBuilder();
            for (DeviceInfo d : all) {
                sb.append(sb.isEmpty() ? "" : "; ").append(d.index()).append(" = ").append(d.describe());
            }
            return sb.toString();
        }

        private static void closeIfOwned(DeviceApi a, boolean owns) {
            if (owns) {
                a.close();
            }
        }
    }

    // =============================================================================================
    // состояние
    // =============================================================================================

    /** @return выбранное устройство */
    public DeviceInfo device() {
        return device;
    }

    /** @return {@code true} после {@link #close()} */
    public boolean isClosed() {
        return closed;
    }

    /** @return политика ухода на JVM, которую применяет фасад {@link JavaToGpu} */
    public Fallback fallback() {
        return fallback;
    }

    /**
     * Сообщает, что ядро выполнено на JVM: событие {@link GpuEvent.Kind#FALLBACK} уходит в {@link EventListener}
     * <b>один раз</b> на ядро и контекст (повторные уходы того же ядра не засоряют журнал).
     *
     * @param kernelId идентификатор ядра
     * @param reason   причина (диагностики компиляции, отсутствие устройства)
     * @return {@code true}, если событие отправлено, {@code false}, если об этом ядре уже сообщалось
     */
    public boolean reportFallback(String kernelId, String reason) {
        if (!fallbackReported.add(kernelId)) {
            return false;
        }
        emit(GpuEvent.Kind.FALLBACK, kernelId, 0, 0, reason);
        return true;
    }

    /** @return счётчики кэша скомпилированных ядер */
    public CacheStats cacheStats() {
        return new CacheStats(hits.get(), misses.get(), cache.size());
    }

    // =============================================================================================
    // компиляция
    // =============================================================================================

    /**
     * Компилирует ядро модуля (последнюю функцию) с параметрами генерации контекста.
     *
     * @param module IR-модуль
     * @return скомпилированное ядро либо диагностики
     */
    public Result<CompiledKernel> compile(IrModule module) {
        return compile(module, module.functionCount() - 1, emitOptions);
    }

    /**
     * Компилирует заданное ядро; повторный запрос того же ядра отдаёт кэш.
     *
     * @param module  IR-модуль
     * @param kernel  индекс функции-ядра
     * @param options параметры генерации
     * @return скомпилированное ядро (в диагностиках - предупреждения драйвера) либо ошибки: {@code IR-*}/{@code CG-*}
     *         от генератора, {@code RT-D02} (устройство не поддерживает нужные возможности), {@code RT-C01} (драйвер
     *         отверг исходник), {@code RT-X01} (ошибка драйвера), {@code RT-X02} (контекст закрыт)
     */
    public Result<CompiledKernel> compile(IrModule module, int kernel, EmitOptions options) {
        if (module == null || options == null) {
            throw new IllegalArgumentException("module и options обязательны");
        }
        if (closed) {
            return failure("RT-X02", "контекст закрыт");
        }
        String key = cacheKey(module, kernel, options);
        Entry entry = cache.computeIfAbsent(key, k -> new Entry());
        synchronized (entry) {
            if (entry.result != null) {
                hits.incrementAndGet();
                emit(GpuEvent.Kind.COMPILE, key.substring(0, Math.min(16, key.length())), 0, 0, "cache-hit");
                return entry.result;
            }
            misses.incrementAndGet();
            long t0 = System.nanoTime();
            Result<CompiledKernel> r = build(module, kernel, options);
            entry.result = r;
            emit(GpuEvent.Kind.COMPILE, r.isOk() ? r.value().artifact().entry() : "-", System.nanoTime() - t0, 0,
                    r.isOk() ? "built" : "failed");
            return r;
        }
    }

    private String cacheKey(IrModule m, int kernel, EmitOptions o) {
        return IrBinary.kernelId(m) + "#" + kernel + "|" + o.debug() + "," + o.fastMath() + "|" + target.id() + "|"
                + device.name() + "|" + device.driverVersion();
    }

    private Result<CompiledKernel> build(IrModule m, int kernel, EmitOptions options) {
        Result<Artifact> emitted = OpenClEmitter.emit(m, kernel, target, options);
        if (!emitted.isOk()) {
            return Result.failed(emitted.diagnostics());
        }
        Artifact art = emitted.value();
        int missing = art.requiredFeatures() & ~device.features();
        if (missing != 0) {
            return failure("RT-D02", "устройство " + device.describe() + " не поддерживает " + Features.describe(missing)
                    + ", а ядро " + art.entry() + " их использует");
        }
        int[] access = Validator.validate(m).functions()[kernel].paramAccess();
        long program = 0;
        lock.lock();
        try {
            program = api.createProgram(contextHandle, art.source());
            DeviceApi.BuildOutcome outcome = api.build(program, device.handle(), art.buildOptions());
            List<Diagnostic> log = BuildLog.parse(outcome.log(), kernel, !outcome.ok());
            if (!outcome.ok()) {
                api.release(DeviceApi.HandleKind.PROGRAM, program);
                return Result.failed(log);
            }
            long k = api.createKernel(program, art.entry());
            long maxWg = api.kernelMaxWorkGroupSize(k, device.handle());
            CompiledKernel ck = new CompiledKernel(this, m, kernel, art, IrBinary.kernelId(m), access, program, k, maxWg);
            compiled.add(ck);
            for (Diagnostic d : log) {
                emit(GpuEvent.Kind.WARNING, art.entry(), 0, 0, d.message());
            }
            return new Result<>(ck, log);
        } catch (DeviceApiException e) {
            if (program != 0) {
                api.release(DeviceApi.HandleKind.PROGRAM, program);
            }
            return failure("RT-X01", "ошибка драйвера при компиляции: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    // =============================================================================================
    // буферы
    // =============================================================================================

    /**
     * Выделяет буфер на устройстве. Содержимое не определено.
     *
     * @param elementType числовой тип элемента IR ({@code I8..F64})
     * @param length      число элементов
     * @return буфер
     * @throws GpuException если тип или размер недопустимы, контекст закрыт или драйвер вернул ошибку
     */
    public GpuBuffer allocate(int elementType, long length) {
        boolean numeric = Types.isInteger(elementType) || Types.isFloat(elementType);
        if (!numeric) {
            throw new GpuException(List.of(error("RT-A03", "недопустимый тип элемента буфера t" + elementType)));
        }
        if (length < 0) {
            throw new GpuException(List.of(error("RT-A03", "длина буфера не может быть отрицательной: " + length)));
        }
        long bytes = length * Marshal.elementSize(elementType);
        if (device.maxAllocation() > 0 && bytes > device.maxAllocation()) {
            throw new GpuException(List.of(error("RT-A03", "буфер в " + bytes + " байт больше допустимой аллокации устройства ("
                    + device.maxAllocation() + ")")));
        }
        lock.lock();
        try {
            requireOpen();
            GpuBuffer b = new GpuBuffer(this, api.createBuffer(contextHandle, Math.max(bytes, 4)), elementType, length);
            buffers.add(b);
            return b;
        } catch (DeviceApiException e) {
            throw new GpuException(List.of(error("RT-X01", "ошибка драйвера при выделении буфера: " + e.getMessage())));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Создаёт буфер под массив хоста и копирует в него данные.
     *
     * @param hostArray Java-массив примитивов
     * @return буфер того же вида и длины
     * @throws GpuException если массив не поддержан или драйвер вернул ошибку
     */
    public GpuBuffer upload(Object hostArray) {
        int type = Marshal.elementTypeOf(hostArray);
        if (type < 0) {
            throw new GpuException(List.of(error("RT-A03", "не поддержанный массив: " + Marshal.describe(hostArray))));
        }
        GpuBuffer b = allocate(type, Marshal.length(hostArray));
        try {
            return b.write(hostArray);
        } catch (GpuException e) {
            b.close();
            throw e;
        }
    }

    void write(GpuBuffer b, Object array) {
        checkTransfer(b, array);
        long bytes = Marshal.byteSize(array);
        lock.lock();
        try (Arena a = Arena.ofConfined()) {
            requireOpen();
            long t0 = System.nanoTime();
            if (bytes > 0) {
                api.writeBuffer(queue, b.handle(), 0, bytes, Marshal.toNative(a, array));
            }
            emit(GpuEvent.Kind.UPLOAD, b.toString(), System.nanoTime() - t0, bytes, "");
        } catch (DeviceApiException e) {
            throw new GpuException(List.of(error("RT-X01", "ошибка драйвера при записи буфера: " + e.getMessage())));
        } finally {
            lock.unlock();
        }
    }

    void read(GpuBuffer b, Object array) {
        checkTransfer(b, array);
        long bytes = Marshal.byteSize(array);
        lock.lock();
        try (Arena a = Arena.ofConfined()) {
            requireOpen();
            long t0 = System.nanoTime();
            if (bytes > 0) {
                MemorySegment dst = a.allocate(Math.max(bytes, 8), 8);
                api.readBuffer(queue, b.handle(), 0, bytes, dst);
                Marshal.fromNative(dst, array);
            }
            emit(GpuEvent.Kind.READBACK, b.toString(), System.nanoTime() - t0, bytes, "");
        } catch (DeviceApiException e) {
            throw new GpuException(List.of(error("RT-X01", "ошибка драйвера при чтении буфера: " + e.getMessage())));
        } finally {
            lock.unlock();
        }
    }

    private void checkTransfer(GpuBuffer b, Object array) {
        if (b.owner() != this) {
            throw new GpuException(List.of(error("RT-A02", "буфер принадлежит другому контексту")));
        }
        if (b.isClosed()) {
            throw new GpuException(List.of(error("RT-A02", "буфер уже закрыт")));
        }
        int type = Marshal.elementTypeOf(array);
        if (type < 0 || !Marshal.compatible(b.elementType(), type)) {
            throw new GpuException(List.of(error("RT-A02", "массив " + Marshal.describe(array)
                    + " не подходит буферу с элементом t" + b.elementType())));
        }
        if (Marshal.length(array) > b.length()) {
            throw new GpuException(List.of(error("RT-A02", "массив из " + Marshal.length(array)
                    + " элементов не помещается в буфер из " + b.length())));
        }
    }

    void free(GpuBuffer b) {
        lock.lock();
        try {
            if (b.isClosed()) {
                return;
            }
            b.markClosed();
            buffers.remove(b);
            if (!closed) {
                api.release(DeviceApi.HandleKind.BUFFER, b.handle());
            }
        } finally {
            lock.unlock();
        }
    }

    // =============================================================================================
    // запуск
    // =============================================================================================

    /**
     * Компилирует (с кэшем) и запускает ядро модуля; ошибки - исключением.
     *
     * @param module IR-модуль (ядро - последняя функция)
     * @param config размеры запуска
     * @param args   значения параметров ядра по порядку: {@link GpuBuffer}, Java-массив или скаляр
     *               ({@code Integer/Long/Float/Double})
     * @return тайминги и объёмы передачи
     * @throws GpuException если компиляция, проверка аргументов или запуск не удались
     */
    public LaunchStats run(IrModule module, LaunchConfig config, Object... args) {
        Result<CompiledKernel> k = compile(module);
        if (!k.isOk()) {
            throw new GpuException(k.diagnostics());
        }
        Result<LaunchStats> r = launch(k.value(), config, args);
        if (!r.isOk()) {
            throw new GpuException(r.diagnostics());
        }
        return r.value();
    }

    /**
     * Запускает скомпилированное ядро. Массивы хоста, которые ядро записывает, обновляются только при успехе.
     *
     * @param kernel ядро этого контекста
     * @param config размеры запуска
     * @param args   значения параметров ядра по порядку
     * @return итог либо диагностики: {@code RT-A01} (число аргументов), {@code RT-A02} (аргумент не подходит параметру),
     *         {@code RT-L01} (размеры запуска недопустимы), {@code RT-X01} (ошибка драйвера), {@code RT-X02} (контекст закрыт)
     */
    public Result<LaunchStats> launch(CompiledKernel kernel, LaunchConfig config, Object... args) {
        if (kernel == null || config == null) {
            throw new IllegalArgumentException("kernel и config обязательны");
        }
        Object[] actual = args == null ? new Object[0] : args;
        if (closed) {
            return failure("RT-X02", "контекст закрыт");
        }
        if (kernel.owner() != this) {
            return failure("RT-A02", "ядро скомпилировано в другом контексте");
        }
        List<Diagnostic> problems = new ArrayList<>();
        long[] local = checkLaunch(kernel, config, problems);
        checkArguments(kernel, actual, problems);
        if (!problems.isEmpty()) {
            return Result.failed(problems);
        }
        lock.lock();
        try {
            if (closed) {
                return failure("RT-X02", "контекст закрыт");
            }
            return execute(kernel, config, local, actual);
        } catch (DeviceApiException e) {
            return failure("RT-X01", "ошибка драйвера при запуске: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /** Проверяет размеры запуска; возвращает размеры work-group для драйвера либо {@code null}. */
    private long[] checkLaunch(CompiledKernel k, LaunchConfig cfg, List<Diagnostic> out) {
        IrModule m = k.module();
        int f = k.function();
        int dim = cfg.dim();
        long[] local = new long[dim];
        boolean any = false;
        long product = 1;
        for (int d = 0; d < dim; d++) {
            int req = m.fReqWorkGroup(f, d);
            long given = cfg.local(d);
            if (req > 0 && given != 0 && given != req) {
                out.add(error("RT-L01", "ось " + d + ": задан размер группы " + given + ", а ядро требует " + req));
            }
            local[d] = req > 0 ? req : given;
            any |= local[d] != 0;
            if (local[d] != 0) {
                product *= local[d];
                if (cfg.global(d) % local[d] != 0) {
                    out.add(error("RT-L01", "ось " + d + ": размер NDRange " + cfg.global(d)
                            + " не делится на размер группы " + local[d]));
                }
            }
        }
        for (int d = dim; d < 3; d++) {
            if (m.fReqWorkGroup(f, d) > 1) {
                out.add(error("RT-L01", "ядро требует размер группы " + m.fReqWorkGroup(f, d) + " по оси " + d
                        + ", а запуск " + dim + "D"));
            }
        }
        long limit = Math.min(device.maxWorkGroupSize(), k.maxWorkGroupSize());
        if (any && limit > 0 && product > limit) {
            out.add(error("RT-L01", "work-group из " + product + " элементов больше допустимой для ядра (" + limit + ")"));
        }
        return any ? local : null;
    }

    private void checkArguments(CompiledKernel k, Object[] args, List<Diagnostic> out) {
        IrModule m = k.module();
        int f = k.function();
        int n = m.fParamCount(f);
        if (args.length != n) {
            out.add(error("RT-A01", "ядро принимает " + n + " аргументов, передано " + args.length));
            return;
        }
        for (int i = 0; i < n; i++) {
            int t = m.fParamType(f, i);
            Object a = args[i];
            if (m.typeKind(t) == Types.KIND_ARRAY) {
                int elem = Types.elem(m, t);
                if (a instanceof GpuBuffer b) {
                    if (b.owner() != this) {
                        out.add(error("RT-A02", "аргумент " + i + ": буфер принадлежит другому контексту"));
                    } else if (b.isClosed()) {
                        out.add(error("RT-A02", "аргумент " + i + ": буфер закрыт"));
                    } else if (!Marshal.compatible(elem, b.elementType())) {
                        out.add(error("RT-A02", "аргумент " + i + ": буфер с элементом t" + b.elementType()
                                + " не подходит параметру с элементом t" + elem));
                    } else if (b.length() > Integer.MAX_VALUE) {
                        out.add(error("RT-A02", "аргумент " + i + ": длина буфера не помещается в int"));
                    }
                } else if (!Marshal.accepts(elem, a)) {
                    out.add(error("RT-A02", "аргумент " + i + ": параметру-массиву t" + elem + " не подходит "
                            + Marshal.describe(a)));
                }
            } else if (!Marshal.scalarMatches(t, a)) {
                out.add(error("RT-A02", "аргумент " + i + ": скалярному параметру t" + t + " не подходит "
                        + Marshal.describe(a)));
            }
        }
    }

    /** Выполняет запуск под замком; аргументы уже проверены. */
    private Result<LaunchStats> execute(CompiledKernel k, LaunchConfig cfg, long[] local, Object[] args) {
        IrModule m = k.module();
        int f = k.function();
        int n = m.fParamCount(f);
        List<Long> temps = new ArrayList<>();
        Map<Object, long[]> hostBuffers = new IdentityHashMap<>(); // массив -> {дескриптор буфера, доступ}
        long[] handles = new long[n];
        long[] lengths = new long[n];
        long up = 0;
        long upNanos;
        long kernelNanos;
        long downNanos = 0;
        long down = 0;
        try (Arena arena = Arena.ofConfined()) {
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) {
                if (m.typeKind(m.fParamType(f, i)) != Types.KIND_ARRAY) {
                    continue;
                }
                Object a = args[i];
                if (a instanceof GpuBuffer b) {
                    handles[i] = b.handle();
                    lengths[i] = b.length();
                    continue;
                }
                long[] known = hostBuffers.get(a);
                if (known == null) {
                    long bytes = Marshal.byteSize(a);
                    long h = api.createBuffer(contextHandle, Math.max(bytes, 4));
                    temps.add(h);
                    if (bytes > 0) {
                        api.writeBuffer(queue, h, 0, bytes, Marshal.toNative(arena, a));
                    }
                    up += bytes;
                    known = new long[]{h, 0};
                    hostBuffers.put(a, known);
                }
                known[1] |= k.access(i);
                handles[i] = known[0];
                lengths[i] = Marshal.length(a);
            }
            upNanos = System.nanoTime() - t0;
            emit(GpuEvent.Kind.UPLOAD, k.artifact().entry(), upNanos, up, "");

            for (int i = 0; i < k.artifact().args().size(); i++) {
                KernelArg ka = k.artifact().args().get(i);
                switch (ka.kind()) {
                    case KernelArg.SCALAR -> api.setArgScalar(k.kernel, i, Marshal.scalarSize(ka.type()),
                            Marshal.scalarBits(args[ka.param()]));
                    case KernelArg.ARRAY_PTR -> api.setArgBuffer(k.kernel, i, handles[ka.param()]);
                    default -> api.setArgScalar(k.kernel, i, 4, lengths[ka.param()]);
                }
            }
            long[] global = new long[cfg.dim()];
            for (int d = 0; d < global.length; d++) {
                global[d] = cfg.global(d);
            }
            long t1 = System.nanoTime();
            api.enqueue(queue, k.kernel, cfg.dim(), global, local);
            api.finish(queue);
            kernelNanos = System.nanoTime() - t1;
            emit(GpuEvent.Kind.KERNEL, k.artifact().entry(), kernelNanos, 0, cfg.toString());

            long t2 = System.nanoTime();
            for (Map.Entry<Object, long[]> e : hostBuffers.entrySet()) {
                if ((e.getValue()[1] & Validator.ACCESS_WRITE) == 0) {
                    continue;
                }
                long bytes = Marshal.byteSize(e.getKey());
                if (bytes > 0) {
                    MemorySegment dst = arena.allocate(Math.max(bytes, 8), 8);
                    api.readBuffer(queue, e.getValue()[0], 0, bytes, dst);
                    Marshal.fromNative(dst, e.getKey());
                    down += bytes;
                }
            }
            downNanos = System.nanoTime() - t2;
            emit(GpuEvent.Kind.READBACK, k.artifact().entry(), downNanos, down, "");
        } finally {
            for (long h : temps) {
                api.release(DeviceApi.HandleKind.BUFFER, h);
            }
        }
        return Result.ok(new LaunchStats(upNanos, kernelNanos, downNanos, up, down));
    }

    // =============================================================================================
    // закрытие
    // =============================================================================================

    /**
     * Освобождает ядра, буферы, очередь и контекст драйвера (и слой доступа, если контекст загрузил его сам).
     * Повторный вызов безопасен.
     */
    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            for (CompiledKernel k : compiled) {
                api.release(DeviceApi.HandleKind.KERNEL, k.kernel);
                api.release(DeviceApi.HandleKind.PROGRAM, k.program);
            }
            compiled.clear();
            for (GpuBuffer b : buffers) {
                b.markClosed();
                api.release(DeviceApi.HandleKind.BUFFER, b.handle());
            }
            buffers.clear();
            api.release(DeviceApi.HandleKind.QUEUE, queue);
            api.release(DeviceApi.HandleKind.CONTEXT, contextHandle);
            if (ownsApi) {
                api.close();
            }
            emit(GpuEvent.Kind.CLOSED, device.describe(), 0, 0, "");
        } finally {
            lock.unlock();
        }
    }

    // =============================================================================================
    // вспомогательное
    // =============================================================================================

    private void requireOpen() {
        if (closed) {
            throw new GpuException(List.of(error("RT-X02", "контекст закрыт")));
        }
    }

    private void emit(GpuEvent.Kind kind, String subject, long nanos, long bytes, String detail) {
        try {
            listener.onEvent(new GpuEvent(kind, subject, nanos, bytes, detail));
        } catch (RuntimeException ignored) {
            // наблюдатель не должен ломать вычисление
        }
    }

    private static Diagnostic error(String code, String message) {
        return new Diagnostic(code, Diagnostic.Severity.ERROR, -1, -1, message);
    }

    private static <T> Result<T> failure(String code, String message) {
        return Result.failed(List.of(error(code, message)));
    }
}
