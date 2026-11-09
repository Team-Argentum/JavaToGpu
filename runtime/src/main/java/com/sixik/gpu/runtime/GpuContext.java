package com.sixik.gpu.runtime;

import com.sixik.gpu.codegen.opencl.Artifact;
import com.sixik.gpu.codegen.opencl.EmitOptions;
import com.sixik.gpu.codegen.opencl.KernelArg;
import com.sixik.gpu.codegen.opencl.OpenClEmitter;
import com.sixik.gpu.codegen.opencl.Target;
import com.sixik.gpu.ir.AbiLayout;
import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.HostObjects;
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
import java.nio.file.Path;
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

    final DeviceApi api;
    private final boolean ownsApi;
    private final DeviceInfo device;
    final long contextHandle;
    final long queue;
    private final Target target;
    private final EventListener listener;
    private final EmitOptions emitOptions;
    private final Fallback fallback;
    private final Set<String> fallbackReported = ConcurrentHashMap.newKeySet();

    final ReentrantLock lock = new ReentrantLock();
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();
    private final List<CompiledKernel> compiled = new ArrayList<>();
    private final Set<GpuBuffer> buffers = ConcurrentHashMap.newKeySet();
    private final Set<PreparedLauncher> launchers = ConcurrentHashMap.newKeySet();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong diskHits = new AtomicLong();
    private final AtomicLong diskMisses = new AtomicLong();
    private final AtomicLong diskWrites = new AtomicLong();
    private final Map<IrModule, String> kernelIds = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private final DiskCache disk;
    private final Path sourceDump;
    private final String codegenVersion;
    volatile boolean closed;

    private GpuContext(DeviceApi api, boolean ownsApi, DeviceInfo device, long contextHandle, long queue,
                       EventListener listener, EmitOptions emitOptions, Fallback fallback, DiskCache disk,
                       Path sourceDump, String codegenVersion) {
        this.disk = disk;
        this.sourceDump = sourceDump;
        this.codegenVersion = codegenVersion;
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

    /** Системное свойство с каталогом дискового кэша либо {@code off}. */
    public static final String CACHE_PROPERTY = "javatogpu.cache";
    /** Переменная окружения с каталогом дискового кэша либо {@code off}. */
    public static final String CACHE_ENVIRONMENT = "JAVATOGPU_CACHE";
    /** Системное свойство с каталогом выгрузки сгенерированного OpenCL C ({@code off} - не выгружать). */
    public static final String DUMP_PROPERTY = "javatogpu.dump";
    /** Переменная окружения с каталогом выгрузки сгенерированного OpenCL C ({@code off} - не выгружать). */
    public static final String DUMP_ENVIRONMENT = "JAVATOGPU_DUMP";

    /** Построитель {@link GpuContext}. */
    public static final class Builder {
        private DeviceApi api;
        private DevicePolicy policy = DevicePolicy.DEFAULT;
        private boolean environment = true;
        private EventListener listener = EventListener.NONE;
        private EmitOptions emitOptions = EmitOptions.RELEASE;
        private Fallback fallback = Fallback.ALLOWED;
        private Path diskCachePath;
        private boolean diskCacheOff;
        private long diskCacheMaxBytes = DiskCache.DEFAULT_MAX_BYTES;
        private Path sourceDumpPath;
        private String codegenVersion = OpenClEmitter.VERSION;

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
         * Явный выбор устройства (то же, что {@code policy.withOverride}): номер, подстрока имени либо {@code подстрока:номер}.
         * Побеждает фильтры политики; переопределяет и переменную окружения.
         *
         * @param override описание устройства
         * @return этот построитель
         */
        public Builder device(String override) {
            this.policy = policy.withOverride(override);
            return this;
        }

        /**
         * Каталог дискового кэша бинарей драйвера (REWORK_ARCHITECTURE.md, 9.3, 5.24). По умолчанию кэш включён в каталоге
         * кэша пользователя ОС, если слой доступа не подменён через {@link #api}; для подменённого слоя (тесты) он выключен,
         * пока не задан явно. Переопределяется также свойством {@code javatogpu.cache} и переменной {@code JAVATOGPU_CACHE}
         * (путь либо {@code off}), если не вызван {@link #ignoreEnvironment()}.
         *
         * @param directory каталог (создаётся при первой записи)
         * @return этот построитель
         */
        public Builder diskCache(Path directory) {
            this.diskCachePath = directory;
            this.diskCacheOff = false;
            return this;
        }

        /**
         * Выключает дисковый кэш.
         *
         * @return этот построитель
         */
        public Builder noDiskCache() {
            this.diskCacheOff = true;
            this.diskCachePath = null;
            return this;
        }

        /**
         * @param bytes предел суммарного размера файлов кэша (по умолчанию 512 МиБ); старые вытесняются
         * @return этот построитель
         */
        public Builder diskCacheLimit(long bytes) {
            this.diskCacheMaxBytes = bytes;
            return this;
        }

        /**
         * Каталог, в который выгружается сгенерированный OpenCL C каждого собранного ядра: файл
         * {@code <ядро>-<хэш исходника>.cl}. Выгрузка не зависит от дискового кэша и делается и при попадании в него
         * (бинарь кэша читать глазами нельзя, исходник - можно). Ошибка записи не мешает сборке. Если не задан,
         * берётся из свойства {@code javatogpu.dump} / переменной {@code JAVATOGPU_DUMP} (путь либо {@code off}),
         * пока не вызван {@link #ignoreEnvironment()}; по умолчанию выгрузки нет.
         *
         * @param directory каталог (создаётся при первой записи); {@code null} - не выгружать
         * @return этот построитель
         */
        public Builder dumpSources(Path directory) {
            this.sourceDumpPath = directory;
            return this;
        }

        /**
         * Версия кодогенератора для ключа дискового кэша; нужна тестам инвалидации, обычно не вызывается.
         *
         * @param version версия
         * @return этот построитель
         */
        Builder codegenVersion(String version) {
            this.codegenVersion = version;
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
                    return failure("RT-D01", "OpenCL library not found: no devices");
                }
            }
            try {
                List<DeviceInfo> all = DeviceInfo.enumerate(a);
                DevicePolicy p = environment && policy.override() == null ? policy.withEnvironment() : policy;
                Optional<DeviceInfo> chosen = p.select(all);
                if (chosen.isEmpty()) {
                    closeIfOwned(a, owns);
                    return failure("RT-D01", all.isEmpty()
                            ? "no OpenCL devices"
                            : "none of the " + all.size() + " devices matches the policy " + p + ": "
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
                        emitOptions, fallback == null ? Fallback.ALLOWED : fallback, openDiskCache(owns), openSourceDump(), codegenVersion);
                gc.emit(GpuEvent.Kind.DEVICE_SELECTED, d.describe(), 0, 0, d.platformName() + ", driver " + d.driverVersion());
                return Result.ok(gc);
            } catch (DeviceApiException e) {
                closeIfOwned(a, owns);
                return failure("RT-D03", "driver error while selecting a device: " + e.getMessage());
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

        /** Дисковый кэш по настройкам построителя и окружения; {@code null} - выключен. */
        private DiskCache openDiskCache(boolean realDriver) {
            if (diskCacheOff) {
                return null;
            }
            if (diskCachePath != null) {
                return new DiskCache(diskCachePath, diskCacheMaxBytes);
            }
            if (environment) {
                String setting = System.getProperty(CACHE_PROPERTY, System.getenv(CACHE_ENVIRONMENT));
                if (setting != null && !setting.isBlank()) {
                    return switch (setting.strip().toLowerCase(java.util.Locale.ROOT)) {
                        case "off", "false", "0", "no" -> null;
                        default -> new DiskCache(Path.of(setting.strip()), diskCacheMaxBytes);
                    };
                }
            }
            return realDriver ? new DiskCache(DiskCache.defaultDirectory(), diskCacheMaxBytes) : null;
        }

        /** Каталог выгрузки исходников по настройкам построителя и окружения; {@code null} - выключена. */
        private Path openSourceDump() {
            if (sourceDumpPath != null) {
                return sourceDumpPath;
            }
            if (environment) {
                String setting = System.getProperty(DUMP_PROPERTY, System.getenv(DUMP_ENVIRONMENT));
                if (setting != null && !setting.isBlank()) {
                    return switch (setting.strip().toLowerCase(java.util.Locale.ROOT)) {
                        case "off", "false", "0", "no" -> null;
                        default -> Path.of(setting.strip());
                    };
                }
            }
            return null;
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
        return new CacheStats(hits.get(), misses.get(), cache.size(), diskHits.get(), diskMisses.get(), diskWrites.get());
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
     *         от генератора, {@code RT-D02} (устройство не поддерживает нужные возможности), {@code RT-D04} (local-памяти ядра больше, чем у устройства), {@code RT-C01} (драйвер
     *         отверг исходник), {@code RT-X01} (ошибка драйвера), {@code RT-X02} (контекст закрыт)
     */
    public Result<CompiledKernel> compile(IrModule module, int kernel, EmitOptions options) {
        if (module == null || options == null) {
            throw new IllegalArgumentException("module and options are required");
        }
        if (closed) {
            return failure("RT-X02", "context is closed");
        }
        StringBuilder variant = new StringBuilder();
        int actual = variantFor(module, kernel, variant);
        String key = cacheKey(module, actual, options);
        Entry entry = cache.computeIfAbsent(key, k -> new Entry());
        synchronized (entry) {
            if (entry.result != null) {
                hits.incrementAndGet();
                emit(GpuEvent.Kind.COMPILE, key.substring(0, Math.min(16, key.length())), 0, 0, "cache-hit" + variant);
                return entry.result;
            }
            misses.incrementAndGet();
            long t0 = System.nanoTime();
            String[] how = {"built"};
            Result<CompiledKernel> r = build(module, actual, options, how);
            entry.result = r;
            emit(GpuEvent.Kind.COMPILE, r.isOk() ? r.value().artifact().entry() : "-", System.nanoTime() - t0, 0,
                    (r.isOk() ? how[0] : "failed") + variant);
            return r;
        }
    }

    /**
     * Выбирает вариант ядра по {@code required} (REWORK_ARCHITECTURE.md, 3.B, 5.24): если устройство не поддерживает
     * возможности, нужные ядру (заявленные в {@code required} и вычисленные валидатором по телу), берётся его
     * {@code fallback}-вариант - другая Java-реализация с той же сигнатурой, а если не подходит и он - следующий по цепочке.
     * Измерений нет: выбор целиком определяется возможностями устройства и потому детерминирован. Если подходящего
     * варианта нет, возвращается исходное ядро, и {@link #build} сообщит {@code RT-D02} именно о нём.
     *
     * @param m      модуль
     * @param kernel запрошенное ядро
     * @param note   сюда дописывается пояснение для события {@code COMPILE}, если выбран другой вариант
     * @return индекс функции, которую надо компилировать
     */
    int variantFor(IrModule m, int kernel, StringBuilder note) {
        if (kernel < 0 || kernel >= m.functionCount() || m.fFallback(kernel) < 0) {
            return kernel;
        }
        Validator.FunctionInfo[] infos = Validator.validate(m).functions();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        int f = kernel;
        while (f >= 0 && f < m.functionCount() && seen.add(f)) {
            int need = requiredMask(m, f) | (f < infos.length && infos[f] != null ? infos[f].requiredFeatures() : 0);
            if ((need & ~device.features()) == 0) {
                if (f != kernel) {
                    int lacking = (requiredMask(m, kernel) | infos[kernel].requiredFeatures()) & ~device.features();
                    note.append("; variant f").append(f).append(" instead of f").append(kernel)
                            .append(": the device does not support ").append(Features.describe(lacking));
                }
                return f;
            }
            f = m.fFallback(f);
        }
        return kernel;
    }

    /** @return маска возможностей, заявленных в {@code required} функции */
    static int requiredMask(IrModule m, int f) {
        int mask = 0;
        for (int i = 0; i < m.fRequiredCount(f); i++) {
            mask |= Features.fromName(m.string(m.fRequiredName(f, i)));
        }
        return mask;
    }

    /**
     * Идентификатор ядра с запоминанием по экземпляру модуля. {@code IrBinary.kernelId} сериализует модуль и считает
     * SHA-256, а ключ кэша нужен при каждом обращении к ядру (каждый вызов {@code @Kernel}-метода): без запоминания
     * попадание в кэш стоило бы порядка микросекунды. Фасад держит один экземпляр модуля на ядро, поэтому запоминание
     * срабатывает; слабые ключи не удерживают модули в памяти.
     */
    private String kernelIdOf(IrModule m) {
        String id = kernelIds.get(m);
        if (id == null) {
            id = IrBinary.kernelId(m);
            kernelIds.put(m, id);
        }
        return id;
    }

    private String cacheKey(IrModule m, int kernel, EmitOptions o) {
        return kernelIdOf(m) + "#" + kernel + "|" + o.debug() + "," + o.fastMath() + "|" + target.id() + "|"
                + device.name() + "|" + device.driverVersion();
    }

    /**
     * Сколько байт {@code local}-памяти объявляет ядро: сумма {@code длина × размер элемента} по узлам
     * {@code local_array_decl} верхнего уровня тела (правило IR-M02 запрещает их где-либо ещё). Размер элемента - по
     * {@link AbiLayout}, как у массива в C, поэтому {@code float3} считается за 16 байт.
     *
     * @param m      модуль
     * @param kernel индекс ядра
     * @return байты local-памяти на work-group
     */
    static long localMemoryBytes(IrModule m, int kernel) {
        int body = m.fBody(kernel);
        long bytes = 0;
        if (m.op(body) == com.sixik.gpu.ir.Op.BLOCK) {
            for (int i = 0; i < m.b(body); i++) {
                int st = m.list(m.a(body) + i);
                if (m.op(st) == com.sixik.gpu.ir.Op.LOCAL_ARRAY_DECL) {
                    int elem = Types.elem(m, m.fLocalType(kernel, m.a(st)));
                    bytes += (long) m.a(m.b(st)) * AbiLayout.sizeOf(m, elem);
                }
            }
        }
        return bytes;
    }

    private Result<CompiledKernel> build(IrModule m, int kernel, EmitOptions options, String[] how) {
        Result<Artifact> emitted = OpenClEmitter.emit(m, kernel, target, options);
        if (!emitted.isOk()) {
            return Result.failed(emitted.diagnostics());
        }
        Artifact art = emitted.value();
        dumpSource(art);
        int missing = (art.requiredFeatures() | requiredMask(m, kernel)) & ~device.features();
        if (missing != 0) {
            return failure("RT-D02", "device " + device.describe() + " does not support " + Features.describe(missing)
                    + ", but the kernel " + art.entry() + " requires them"
                    + (m.fFallback(kernel) >= 0 ? " (the fallback variant did not fit either)" : ""));
        }
        long localBytes = localMemoryBytes(m, kernel);
        if (localBytes > device.localMemory()) {
            return failure("RT-D04", "kernel " + art.entry() + " declares " + localBytes + " bytes of local memory, but the device "
                    + device.describe() + " provides per work-group " + device.localMemory() + " (CL_DEVICE_LOCAL_MEM_SIZE); "
                    + "reduce the @Local arrays");
        }
        int[] access = Validator.validate(m).functions()[kernel].paramAccess();
        long program = 0;
        lock.lock();
        try {
            String diskKey = disk == null ? null : diskKey(m, kernel, options, art);
            long[] cached = diskKey == null ? null : loadFromDisk(diskKey, art);
            long kernelHandle;
            List<Diagnostic> log = List.of();
            if (cached != null) {
                program = cached[0];
                kernelHandle = cached[1];
                how[0] = "disk-hit";
            } else {
                program = api.createProgram(contextHandle, art.source());
                DeviceApi.BuildOutcome outcome = api.build(program, device.handle(), art.buildOptions());
                log = BuildLog.parse(outcome.log(), kernel, !outcome.ok());
                if (!outcome.ok()) {
                    api.release(DeviceApi.HandleKind.PROGRAM, program);
                    return Result.failed(log);
                }
                kernelHandle = api.createKernel(program, art.entry());
                if (diskKey != null) {
                    saveToDisk(diskKey, program);
                }
            }
            long maxWg = api.kernelMaxWorkGroupSize(kernelHandle, device.handle());
            CompiledKernel ck = new CompiledKernel(this, m, kernel, art, kernelIdOf(m), access, program, kernelHandle, maxWg);
            compiled.add(ck);
            for (Diagnostic d : log) {
                emit(GpuEvent.Kind.WARNING, art.entry(), 0, 0, d.message());
            }
            return new Result<>(ck, log);
        } catch (DeviceApiException e) {
            if (program != 0) {
                api.release(DeviceApi.HandleKind.PROGRAM, program);
            }
            return failure("RT-X01", "driver error during compilation: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /** Пишет сгенерированный OpenCL C в каталог выгрузки, если он задан; любая ошибка записи игнорируется. */
    private void dumpSource(Artifact art) {
        if (sourceDump == null) {
            return;
        }
        try {
            String hash = java.util.HexFormat.of().formatHex(DiskCache.sha256(
                    art.source().getBytes(java.nio.charset.StandardCharsets.UTF_8)), 0, 4);
            String entry = art.entry().replaceAll("[^A-Za-z0-9_.-]", "_");
            java.nio.file.Files.createDirectories(sourceDump);
            java.nio.file.Files.writeString(sourceDump.resolve(entry + "-" + hash + ".cl"), art.source(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException | RuntimeException ignored) {
            // выгрузка - отладочная помощь, не причина падения сборки
        }
    }

    /**
     * Ключ дискового кэша: всё, от чего зависит бинарь. Версия кодгена и хэш IR без отладочной секции - явные причины
     * инвалидации; хэш самого исходника страхует от забытого увеличения версии; устройство и драйвер - потому что
     * образ годится только для того же драйвера; опции - потому что от них зависят и текст, и сборка.
     */
    private String diskKey(IrModule m, int kernel, EmitOptions o, Artifact art) {
        return "gbc1|codegen=" + codegenVersion + "|ir=" + kernelIdOf(m) + "#" + kernel
                + "|emit=" + o.debug() + "," + o.fastMath() + "|build=" + art.buildOptions()
                + "|device=" + device.vendor() + "/" + device.name() + "|driver=" + device.driverVersion()
                + "|opencl=" + device.openClVersion()
                + "|src=" + java.util.HexFormat.of().formatHex(DiskCache.sha256(
                art.source().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    /**
     * Пробует собрать ядро из образа с диска. Любая неудача (нет образа, драйвер образ не принял, сборка не прошла) -
     * промах: образ удаляется, вызывающий собирает из исходника. Вызывается под замком.
     *
     * @return {@code {программа, ядро}} либо {@code null}
     */
    private long[] loadFromDisk(String key, Artifact art) {
        byte[] image = disk.get(key);
        if (image == null) {
            diskMisses.incrementAndGet();
            return null;
        }
        long program = 0;
        try {
            program = api.createProgramWithBinary(contextHandle, device.handle(), image);
            DeviceApi.BuildOutcome outcome = api.build(program, device.handle(), art.buildOptions());
            if (outcome.ok()) {
                long k = api.createKernel(program, art.entry());
                diskHits.incrementAndGet();
                return new long[]{program, k};
            }
        } catch (DeviceApiException ignored) {
            // образ от другого драйвера или повреждён: ниже он удаляется
        }
        if (program != 0) {
            api.release(DeviceApi.HandleKind.PROGRAM, program);
        }
        disk.remove(key);
        diskMisses.incrementAndGet();
        return null;
    }

    /** Кладёт образ собранной программы на диск, если драйвер его отдаёт. Вызывается под замком. */
    private void saveToDisk(String key, long program) {
        byte[] image;
        try {
            image = api.programBinary(program);
        } catch (DeviceApiException e) {
            return;
        }
        if (image != null && disk.put(key, image)) {
            diskWrites.incrementAndGet();
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
            throw new GpuException(List.of(error("RT-A03", "invalid buffer element type t" + elementType)));
        }
        if (length < 0) {
            throw new GpuException(List.of(error("RT-A03", "buffer length cannot be negative: " + length)));
        }
        long bytes = length * Marshal.elementSize(elementType);
        if (device.maxAllocation() > 0 && bytes > device.maxAllocation()) {
            throw new GpuException(List.of(error("RT-A03", "a buffer of " + bytes + " bytes exceeds the device's maximum allocation ("
                    + device.maxAllocation() + ")")));
        }
        lock.lock();
        try {
            requireOpen();
            GpuBuffer b = new GpuBuffer(this, api.createBuffer(contextHandle, Math.max(bytes, 4)), elementType, length);
            buffers.add(b);
            return b;
        } catch (DeviceApiException e) {
            throw new GpuException(List.of(error("RT-X01", "driver error while allocating a buffer: " + e.getMessage())));
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
            throw new GpuException(List.of(error("RT-A03", "unsupported array: " + Marshal.describe(hostArray))));
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
            throw new GpuException(List.of(error("RT-X01", "driver error while writing a buffer: " + e.getMessage())));
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
            throw new GpuException(List.of(error("RT-X01", "driver error while reading a buffer: " + e.getMessage())));
        } finally {
            lock.unlock();
        }
    }

    private void checkTransfer(GpuBuffer b, Object array) {
        if (b.owner() != this) {
            throw new GpuException(List.of(error("RT-A02", "the buffer belongs to another context")));
        }
        if (b.isClosed()) {
            throw new GpuException(List.of(error("RT-A02", "the buffer is already closed")));
        }
        int type = Marshal.elementTypeOf(array);
        if (type < 0 || !Marshal.compatible(b.elementType(), type)) {
            throw new GpuException(List.of(error("RT-A02", "array " + Marshal.describe(array)
                    + " does not fit a buffer with element t" + b.elementType())));
        }
        if (Marshal.length(array) > b.length()) {
            throw new GpuException(List.of(error("RT-A02", "an array of " + Marshal.length(array)
                    + " elements does not fit in a buffer of " + b.length())));
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
     * Готовит повторные запуски ядра с одними и теми же аргументами (горячий путь): проверяет аргументы, создаёт буферы
     * под массивы хоста и нативную память упаковки один раз. Подробности и правила владения - в {@link PreparedLauncher}.
     *
     * @param kernel ядро этого контекста
     * @param args   значения параметров ядра по порядку: {@link GpuBuffer}, Java-массив или скаляр
     * @return запуск либо диагностики {@code RT-A01/RT-A02/RT-A04} (аргументы), {@code RT-X01} (драйвер), {@code RT-X02}
     *         (контекст закрыт или ядро чужого контекста)
     */
    public Result<PreparedLauncher> prepare(CompiledKernel kernel, Object... args) {
        if (kernel == null) {
            throw new IllegalArgumentException("kernel is required");
        }
        if (closed) {
            return failure("RT-X02", "context is closed");
        }
        if (kernel.owner() != this) {
            return failure("RT-A02", "the kernel was compiled in another context");
        }
        Result<PreparedLauncher> r = PreparedLauncher.create(this, kernel, args == null ? new Object[0] : args);
        if (r.isOk()) {
            launchers.add(r.value());
        }
        return r;
    }

    /** Исключает закрытый запуск из учёта контекста. */
    void forget(PreparedLauncher launcher) {
        launchers.remove(launcher);
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
            throw new IllegalArgumentException("kernel and config are required");
        }
        Object[] actual = args == null ? new Object[0] : args;
        if (closed) {
            return failure("RT-X02", "context is closed");
        }
        if (kernel.owner() != this) {
            return failure("RT-A02", "the kernel was compiled in another context");
        }
        List<Diagnostic> problems = new ArrayList<>();
        long[] local = checkLaunch(kernel, config, problems);
        checkArguments(kernel, actual, problems);
        if (problems.isEmpty()) {
            checkConstantArguments(kernel, actual, problems);
        }
        if (!problems.isEmpty()) {
            return Result.failed(problems);
        }
        lock.lock();
        try {
            if (closed) {
                return failure("RT-X02", "context is closed");
            }
            return execute(kernel, config, local, actual);
        } catch (DeviceApiException e) {
            return failure("RT-X01", "driver error during launch: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /** Проверяет размеры запуска; возвращает размеры work-group для драйвера либо {@code null}. */
    long[] checkLaunch(CompiledKernel k, LaunchConfig cfg, List<Diagnostic> out) {
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
                out.add(error("RT-L01", "axis " + d + ": group size given " + given + ", but the kernel requires " + req));
            }
            local[d] = req > 0 ? req : given;
            any |= local[d] != 0;
            if (local[d] != 0) {
                product *= local[d];
                if (cfg.global(d) % local[d] != 0) {
                    out.add(error("RT-L01", "axis " + d + ": NDRange size " + cfg.global(d)
                            + " is not divisible by the group size " + local[d]));
                }
            }
        }
        for (int d = dim; d < 3; d++) {
            if (m.fReqWorkGroup(f, d) > 1) {
                out.add(error("RT-L01", "the kernel requires group size " + m.fReqWorkGroup(f, d) + " on axis " + d
                        + ", but the launch has " + dim + "D"));
            }
        }
        long limit = Math.min(device.maxWorkGroupSize(), k.maxWorkGroupSize());
        if (any && limit > 0 && product > limit) {
            out.add(error("RT-L01", "a work-group of " + product + " items exceeds the kernel's limit (" + limit + ")"));
        }
        return any ? local : null;
    }

    void checkArguments(CompiledKernel k, Object[] args, List<Diagnostic> out) {
        IrModule m = k.module();
        int f = k.function();
        int n = m.fParamCount(f);
        if (args.length != n) {
            out.add(error("RT-A01", "the kernel takes " + n + " arguments, passed " + args.length));
            return;
        }
        for (int i = 0; i < n; i++) {
            checkArgument(k, i, args[i], out);
        }
    }

    /**
     * Проверяет один аргумент против параметра ядра ({@code RT-A02}). Вынесено из {@link #checkArguments}, чтобы
     * {@link PreparedLauncher#set} проверял только заменяемое значение.
     *
     * @param k   ядро
     * @param i   номер параметра
     * @param a   значение
     * @param out сюда добавляются ошибки
     */
    void checkArgument(CompiledKernel k, int i, Object a, List<Diagnostic> out) {
        IrModule m = k.module();
        int f = k.function();
        int t = m.fParamType(f, i);
        if (m.typeKind(t) == Types.KIND_ARRAY) {
            int elem = Types.elem(m, t);
            if (HostObjects.isAggregate(m, elem)) {
                if (a instanceof GpuBuffer) {
                    out.add(error("RT-A02", "argument " + i + ": GpuBuffer for arrays of vectors and structs "
                            + "is not supported yet, pass an array of objects"));
                } else {
                    String why = Marshal.objectArrayProblem(m, elem, a);
                    if (!why.isEmpty()) {
                        out.add(error("RT-A02", "argument " + i + ": " + why));
                    }
                }
            } else if (a instanceof GpuBuffer b) {
                if (b.owner() != this) {
                    out.add(error("RT-A02", "argument " + i + ": the buffer belongs to another context"));
                } else if (b.isClosed()) {
                    out.add(error("RT-A02", "argument " + i + ": the buffer is closed"));
                } else if (!Marshal.compatible(elem, b.elementType())) {
                    out.add(error("RT-A02", "argument " + i + ": buffer with element t" + b.elementType()
                            + " does not fit a parameter with element t" + elem));
                } else if (b.length() > Integer.MAX_VALUE) {
                    out.add(error("RT-A02", "argument " + i + ": the buffer length does not fit in int"));
                }
            } else if (!Marshal.accepts(elem, a)) {
                out.add(error("RT-A02", "argument " + i + ": array parameter t" + elem + " does not fit "
                        + Marshal.describe(a)));
            }
        } else if (HostObjects.isAggregate(m, t)) {
            String why = a == null ? "expected a vector or struct object, passed null"
                    : HostObjects.mismatch(m, t, a.getClass());
            if (!why.isEmpty()) {
                out.add(error("RT-A02", "argument " + i + ": " + why));
            }
        } else if (!Marshal.scalarMatches(t, a)) {
            out.add(error("RT-A02", "argument " + i + ": scalar parameter t" + t + " does not fit "
                    + Marshal.describe(a)));
        }
    }

    /**
     * Массивы в {@code __constant} (параметры {@code @Global(constant = true)}) ограничены устройством: каждый - не больше
     * {@code CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE} (по спецификации не меньше 64 КиБ), а число таких аргументов - не
     * больше {@code CL_DEVICE_MAX_CONSTANT_ARGS}. Драйвер при нарушении отвечает невнятным
     * {@code CL_INVALID_KERNEL_ARGS}/{@code CL_OUT_OF_RESOURCES} уже при постановке в очередь, поэтому нарушение
     * проверяется до загрузки данных. Вызывается после {@link #checkArguments}: типы аргументов уже подходят.
     *
     * @param k    ядро
     * @param args проверенные аргументы
     * @param out  сюда добавляется {@code RT-A04} на каждое нарушение
     */
    void checkConstantArguments(CompiledKernel k, Object[] args, List<Diagnostic> out) {
        IrModule m = k.module();
        int f = k.function();
        int count = 0;
        for (int i = 0; i < m.fParamCount(f); i++) {
            int t = m.fParamType(f, i);
            if (m.typeKind(t) != Types.KIND_ARRAY || Types.space(m, t) != AddressSpace.CONSTANT) {
                continue;
            }
            count++;
            int elem = Types.elem(m, t);
            long bytes = args[i] instanceof GpuBuffer b ? b.byteSize()
                    : HostObjects.isAggregate(m, elem) ? Marshal.byteSize(m, elem, (Object[]) args[i])
                    : Marshal.byteSize(args[i]);
            if (bytes > device.maxConstantBuffer()) {
                out.add(error("RT-A04", "argument " + i + ": the @Global(constant = true) array takes " + bytes
                        + " bytes, but the device allows at most " + device.maxConstantBuffer()
                        + " in __constant (CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE); pass it as an ordinary @Global array"));
            }
        }
        if (count > device.maxConstantArgs()) {
            out.add(error("RT-A04", "the kernel receives " + count + " __constant arguments, but the device allows at most "
                    + device.maxConstantArgs() + " (CL_DEVICE_MAX_CONSTANT_ARGS)"));
        }
    }

    /**
     * Выставляет аргументы {@code cl_kernel} по сигнатуре артефакта. Вызывается под замком; после неё состояние
     * объекта драйвера «чужое» для любого {@link PreparedLauncher}, поэтому {@code boundTo} сбрасывается.
     *
     * @param handles дескрипторы буферов по номеру параметра-массива
     * @param lengths длины массивов по номеру параметра
     * @param arena   владелец временной памяти для значений-агрегатов
     */
    void setKernelArgs(CompiledKernel k, Object[] args, long[] handles, long[] lengths, Arena arena) {
        k.boundTo = null;
        for (int i = 0; i < k.artifact().args().size(); i++) {
            setKernelArg(k, i, args, handles, lengths, arena);
        }
    }

    /**
     * Выставляет один аргумент {@code cl_kernel} (номер - в списке аргументов артефакта). Вызывается под замком.
     * Вызывающий отвечает за {@code boundTo}: после частичной установки он обязан быть {@code null}.
     */
    void setKernelArg(CompiledKernel k, int i, Object[] args, long[] handles, long[] lengths, Arena arena) {
        IrModule m = k.module();
        KernelArg ka = k.artifact().args().get(i);
        switch (ka.kind()) {
            case KernelArg.SCALAR -> {
                if (HostObjects.isAggregate(m, ka.type())) {
                    int size = AbiLayout.sizeOf(m, ka.type());
                    api.setArgBytes(k.kernel, i, Marshal.valueToNative(arena, m, ka.type(), args[ka.param()]), size);
                } else {
                    api.setArgScalar(k.kernel, i, Marshal.scalarSize(ka.type()), Marshal.scalarBits(args[ka.param()]));
                }
            }
            case KernelArg.ARRAY_PTR -> api.setArgBuffer(k.kernel, i, handles[ka.param()]);
            default -> api.setArgScalar(k.kernel, i, 4, lengths[ka.param()]);
        }
    }

    /**
     * Ставит ядро в очередь и ждёт завершения. Вызывается под замком.
     *
     * @return время работы ядра, нс
     */
    long dispatch(CompiledKernel k, LaunchConfig cfg, long[] local) {
        long[] global = new long[cfg.dim()];
        for (int d = 0; d < global.length; d++) {
            global[d] = cfg.global(d);
        }
        long t1 = System.nanoTime();
        api.enqueue(queue, k.kernel, cfg.dim(), global, local);
        api.finish(queue);
        long nanos = System.nanoTime() - t1;
        emit(GpuEvent.Kind.KERNEL, k.artifact().entry(), nanos, 0, cfg.toString());
        return nanos;
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
                int elemType = Types.elem(m, m.fParamType(f, i));
                boolean objects = HostObjects.isAggregate(m, elemType);
                if (known == null) {
                    long bytes = objects ? Marshal.byteSize(m, elemType, (Object[]) a) : Marshal.byteSize(a);
                    long h = api.createBuffer(contextHandle, Math.max(bytes, 4));
                    temps.add(h);
                    if (bytes > 0) {
                        api.writeBuffer(queue, h, 0, bytes, objects ? Marshal.toNative(arena, m, elemType, (Object[]) a)
                                : Marshal.toNative(arena, a));
                    }
                    up += bytes;
                    known = new long[]{h, 0, elemType};
                    hostBuffers.put(a, known);
                }
                known[1] |= k.access(i);
                handles[i] = known[0];
                lengths[i] = Marshal.length(a);
            }
            upNanos = System.nanoTime() - t0;
            emit(GpuEvent.Kind.UPLOAD, k.artifact().entry(), upNanos, up, "");

            setKernelArgs(k, args, handles, lengths, arena);
            kernelNanos = dispatch(k, cfg, local);

            long t2 = System.nanoTime();
            for (Map.Entry<Object, long[]> e : hostBuffers.entrySet()) {
                if ((e.getValue()[1] & Validator.ACCESS_WRITE) == 0) {
                    continue;
                }
                int elemType = (int) e.getValue()[2];
                boolean objects = HostObjects.isAggregate(m, elemType);
                long bytes = objects ? Marshal.byteSize(m, elemType, (Object[]) e.getKey()) : Marshal.byteSize(e.getKey());
                if (bytes > 0) {
                    MemorySegment dst = arena.allocate(Math.max(bytes, 8), 8);
                    api.readBuffer(queue, e.getValue()[0], 0, bytes, dst);
                    if (objects) {
                        Marshal.fromNative(dst, m, elemType, (Object[]) e.getKey());
                    } else {
                        Marshal.fromNative(dst, e.getKey());
                    }
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
            for (PreparedLauncher l : launchers) {
                l.releaseLocked(); // до closed = true: освобождает свои буферы в драйвере
            }
            launchers.clear();
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
            throw new GpuException(List.of(error("RT-X02", "context is closed")));
        }
    }

    void emit(GpuEvent.Kind kind, String subject, long nanos, long bytes, String detail) {
        try {
            listener.onEvent(new GpuEvent(kind, subject, nanos, bytes, detail, device.index()));
        } catch (RuntimeException ignored) {
            // наблюдатель не должен ломать вычисление
        }
    }

    static Diagnostic error(String code, String message) {
        return new Diagnostic(code, Diagnostic.Severity.ERROR, -1, -1, message);
    }

    static <T> Result<T> failure(String code, String message) {
        return Result.failed(List.of(error(code, message)));
    }
}
