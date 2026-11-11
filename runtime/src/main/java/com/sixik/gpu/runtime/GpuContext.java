package com.sixik.gpu.runtime;

import com.sixik.gpu.codegen.opencl.Artifact;
import com.sixik.gpu.codegen.opencl.EmitOptions;
import com.sixik.gpu.codegen.opencl.KernelArg;
import com.sixik.gpu.codegen.opencl.OpenClEmitter;
import com.sixik.gpu.ir.optimizer.OptimizeOptions;
import com.sixik.gpu.ir.optimizer.OptimizeResult;
import com.sixik.gpu.ir.optimizer.Optimizer;
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
    /**
     * Быстрый путь {@link #compile} для повторного запроса того же модуля (по идентичности объекта) с теми же ядром и
     * опциями: без построения строкового ключа кэша - это заметная доля стоимости обычного вызова ядра через фасад.
     */
    private final Map<IrModule, FastEntry> fastCompile = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Запись быстрого пути: ядро и опции запроса, запись кэша и данные события {@code COMPILE}. */
    private record FastEntry(int kernel, EmitOptions options, Entry entry, String shortKey, String variant) {
    }
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
    private final OptimizeOptions optimizeOptions;
    /** Когда исполнять ядра по стадиям {@code KernelSplitter}. */
    private final KernelSplitMode splitMode;
    /**
     * Промежуточные буферы разделённых ядер: по порядковому номеру буфера в плане - дескриптор и размер в байтах. Общие
     * для всех ядер контекста: очередь in-order, поэтому стадии разных запусков не пересекаются. Под {@link #lock}.
     */
    private long[] scratchHandles = new long[0];
    private long[] scratchSizes = new long[0];
    /** Больше стольких байт промежуточный буфер не бывает: запуск идёт целиком. */
    static final long MAX_SCRATCH_BYTES = 1L << 30;
    /**
     * Кэш подготовленных запусков для вызовов через фасад ({@link #launchReusing}): ключ - ядро и идентичность массивов
     * хоста; порядок доступа (LRU). Под {@link #lock}.
     */
    private final java.util.LinkedHashMap<ReuseKey, Reused> reuse = new java.util.LinkedHashMap<>(16, 0.75f, true);
    /** Сколько байт массивов хоста держат запуски кэша. */
    private long reuseBytes;
    /** Больше стольких запусков кэш не держит. */
    static final int REUSE_MAX_ENTRIES = 16;
    /** Больше стольких байт массивов кэш не держит (кроме единственного запуска, который больше лимита сам по себе). */
    static final long REUSE_MAX_BYTES = 512L << 20;

    /** Ключ кэша: ядро и массивы хоста по позициям (сравнение по идентичности, {@code null} - не массив хоста). */
    private record ReuseKey(CompiledKernel kernel, Object[] arrays) {
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ReuseKey k) || k.kernel != kernel || k.arrays.length != arrays.length) {
                return false;
            }
            for (int i = 0; i < arrays.length; i++) {
                if (k.arrays[i] != arrays[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int h = System.identityHashCode(kernel);
            for (Object a : arrays) {
                h = 31 * h + System.identityHashCode(a);
            }
            return h;
        }
    }

    /** Запись кэша: запуск и последние значения не-массивных аргументов. */
    private static final class Reused {
        final PreparedLauncher launcher;
        final Object[] last;
        final long bytes;

        Reused(PreparedLauncher launcher, Object[] last, long bytes) {
            this.launcher = launcher;
            this.last = last;
            this.bytes = bytes;
        }
    }

    /**
     * Отложенная синхронизация (асинхронная цепочка {@code GpuArray}). Очередь драйвера in-order, поэтому любая
     * блокирующая операция ({@code finish}, чтение, запись) дожидается всех команд до неё. Запуск ядра, после которого хосту
     * ничего не нужно читать, не ждёт своего завершения; загрузка устаревшей копии {@code GpuArray} ставится в очередь
     * без ожидания, а её промежуточная память держится здесь до ближайшей точки синхронизации. Всё - под {@link #lock}.
     */
    private final List<MemorySegment> pendingStaging = new ArrayList<>();
    /** Байт промежуточной памяти, ожидающей синхронизации. */
    private long pendingBytes;
    /** Запусков, поставленных без ожидания с последней синхронизации. */
    private int pendingLaunches;
    /**
     * Ядра, поставленные без ожидания с последней синхронизации ({@code "имя размеры"}): ошибка исполнения такого ядра
     * проявится в ближайшей блокирующей операции, и сообщение об ошибке перечисляет их как возможных виновников.
     */
    private final List<String> pendingKernels = new ArrayList<>();
    /** Текущему запуску разрешено не ждать завершения ядра (выставляется на время запуска с {@code GpuArray}). */
    private boolean deferAllowed;
    /** Больше стольких отложенных запусков подряд не накапливается: следующий ждёт завершения. */
    static final int MAX_DEFERRED_LAUNCHES = 64;
    /** Больше стольких байт отложенной промежуточной памяти не держится: дальше - синхронизация. */
    static final long MAX_PENDING_BYTES = 64L << 20;

    /** Массивы {@code GpuArray}, привязанные к контексту (слабые ссылки; под {@link #lock}). */
    private final java.util.Map<com.sixik.gpu.api.types.GpuArray, Boolean> residents = new java.util.WeakHashMap<>();

    /** Общий закреплённый промежуточный буфер передач (под {@link #lock}); растёт до самого крупного массива. */
    private DeviceApi.Pinned transferPool;
    /** Больше этого размера закреплённый буфер не держится: крупные одиночные передачи идут через обычную память. */
    static final long TRANSFER_POOL_MAX_BYTES = 256L << 20;
    volatile boolean closed;

    private GpuContext(DeviceApi api, boolean ownsApi, DeviceInfo device, long contextHandle, long queue,
                       EventListener listener, EmitOptions emitOptions, Fallback fallback, DiskCache disk,
                       Path sourceDump, String codegenVersion, OptimizeOptions optimizeOptions, KernelSplitMode splitMode) {
        this.optimizeOptions = optimizeOptions;
        this.splitMode = splitMode;
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
    /** Системное свойство с уровнем оптимизатора IR ({@code 0..3} либо {@code off}). */
    public static final String OPTIMIZE_PROPERTY = "javatogpu.optimize";
    /** Переменная окружения с уровнем оптимизатора IR ({@code 0..3} либо {@code off}). */
    public static final String OPTIMIZE_ENVIRONMENT = "JAVATOGPU_OPTIMIZE";

    /** Системное свойство с режимом деления ядер на стадии ({@code off}, {@code auto}, {@code always}). */
    public static final String SPLIT_PROPERTY = "javatogpu.split";
    /** Переменная окружения с режимом деления ядер на стадии ({@code off}, {@code auto}, {@code always}). */
    public static final String SPLIT_ENVIRONMENT = "JAVATOGPU_SPLIT";

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
        private OptimizeOptions optimizeOptions = OptimizeOptions.DEFAULT;
        private KernelSplitMode splitMode = KernelSplitMode.OFF;

        private Builder() {
        }

        /**
         * Настройки оптимизатора IR, применяемого перед генерацией OpenCL C (модуль {@code ir-optimizer}). По умолчанию -
         * {@link OptimizeOptions#DEFAULT}: только точные преобразования, результат ядра побитово тот же. В режиме
         * {@link EmitOptions#debug()} оптимизатор не запускается, чтобы исходник соответствовал Java-коду. Если не вызван
         * {@link #ignoreEnvironment()}, уровень переопределяется свойством {@code javatogpu.optimize} либо переменной
         * {@code JAVATOGPU_OPTIMIZE} ({@code 0..3} либо {@code off}).
         *
         * @param options настройки ({@link OptimizeOptions#NONE} выключает оптимизатор)
         * @return этот построитель
         */
        public Builder optimize(OptimizeOptions options) {
            if (options == null) {
                throw new IllegalArgumentException("optimize options are required");
            }
            this.optimizeOptions = options;
            return this;
        }

        /**
         * Когда исполнять ядро по стадиям {@code KernelSplitter} (см. {@link KernelSplitMode}); по умолчанию
         * {@link KernelSplitMode#OFF}. Если не вызван {@link #ignoreEnvironment()}, переопределяется свойством
         * {@code javatogpu.split} либо переменной {@code JAVATOGPU_SPLIT} ({@code off}, {@code auto}, {@code always}).
         *
         * @param mode режим
         * @return этот построитель
         */
        public Builder splitKernels(KernelSplitMode mode) {
            if (mode == null) {
                throw new IllegalArgumentException("split mode is required");
            }
            this.splitMode = mode;
            return this;
        }

        /** Режим деления с учётом свойства и переменной окружения. */
        KernelSplitMode effectiveSplitMode() {
            if (!environment) {
                return splitMode;
            }
            String v = System.getProperty(SPLIT_PROPERTY);
            if (v == null) {
                v = System.getenv(SPLIT_ENVIRONMENT);
            }
            if (v == null || v.isBlank()) {
                return splitMode;
            }
            try {
                return KernelSplitMode.valueOf(v.strip().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return splitMode;
            }
        }

        /** Настройки оптимизатора с учётом свойства и переменной окружения. */
        OptimizeOptions effectiveOptimizeOptions() {
            if (!environment) {
                return optimizeOptions;
            }
            String v = System.getProperty(OPTIMIZE_PROPERTY);
            if (v == null) {
                v = System.getenv(OPTIMIZE_ENVIRONMENT);
            }
            if (v == null || v.isBlank()) {
                return optimizeOptions;
            }
            v = v.strip();
            if (v.equalsIgnoreCase("off")) {
                return optimizeOptions.withLevel(0);
            }
            try {
                return optimizeOptions.withLevel(Math.max(0, Math.min(3, Integer.parseInt(v))));
            } catch (NumberFormatException e) {
                return optimizeOptions;
            }
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
         * Политика фасада до создания устройства: нужна и при неудаче {@link #tryBuild()}.
         *
         * @return настроенная политика fallback
         */
        Fallback fallback() {
            return fallback;
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
                        emitOptions, fallback == null ? Fallback.ALLOWED : fallback, openDiskCache(owns), openSourceDump(), codegenVersion,
                        effectiveOptimizeOptions(), effectiveSplitMode());
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
        FastEntry fast = fastCompile.get(module);
        if (fast != null && fast.kernel() == kernel && fast.options() == options) {
            synchronized (fast.entry()) {
                if (fast.entry().result != null) {
                    hits.incrementAndGet();
                    emit(GpuEvent.Kind.COMPILE, fast.shortKey(), 0, 0, "cache-hit" + fast.variant());
                    return fast.entry().result;
                }
            }
        }
        StringBuilder variant = new StringBuilder();
        int actual = variantFor(module, kernel, variant);
        String key = cacheKey(module, actual, options);
        Entry entry = cache.computeIfAbsent(key, k -> new Entry());
        fastCompile.put(module, new FastEntry(kernel, options, entry, key.substring(0, Math.min(16, key.length())),
                variant.toString()));
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
        return kernelIdOf(m) + "#" + kernel + "|" + o.debug() + "," + o.fastMath() + "|" + optimizeKey() + "|"
                + target.id() + "|"
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

    /**
     * Закреплённая промежуточная память для передачи {@code bytes} байт. Все передачи контекста идут под замком и
     * блокирующие, поэтому одного буфера достаточно: следующая передача начинается, когда предыдущая уже закончила
     * с ним работу. Закрепление дорого, поэтому буфер переиспользуется и только растёт.
     *
     * @param bytes размер передачи
     * @return память не короче {@code bytes} либо {@code null} (мелкая или слишком крупная передача, драйвер не умеет)
     */
    private MemorySegment pinnedStaging(long bytes) {
        if (bytes < PreparedLauncher.PINNED_BYTES || bytes > TRANSFER_POOL_MAX_BYTES) {
            return null;
        }
        if (transferPool != null && transferPool.memory().byteSize() >= bytes) {
            return transferPool.memory();
        }
        try {
            if (transferPool != null) {
                api.releasePinned(queue, transferPool);
                transferPool = null;
            }
            transferPool = api.createPinned(contextHandle, queue, bytes);
        } catch (DeviceApiException e) {
            transferPool = null;
        }
        return transferPool == null ? null : transferPool.memory();
    }

    /** Блокирующая запись примитивного массива через закреплённую память, если она есть. */
    private void upload(long handle, Object array, long bytes, Arena arena) {
        MemorySegment pin = pinnedStaging(bytes);
        if (pin != null) {
            Marshal.copyToNativeFast(array, pin);
            api.writeBuffer(queue, handle, 0, bytes, pin);
        } else {
            api.writeBuffer(queue, handle, 0, bytes, Marshal.toNative(arena, array));
        }
        afterSync(); // блокирующая запись in-order очереди: всё до неё выполнено
    }

    /** Блокирующее чтение в примитивный массив через закреплённую память, если она есть. */
    private void download(long handle, Object array, long bytes, Arena arena) {
        MemorySegment pin = pinnedStaging(bytes);
        MemorySegment dst = pin != null ? pin : arena.allocate(Math.max(bytes, 8), 8);
        api.readBuffer(queue, handle, 0, bytes, dst);
        afterSync(); // блокирующее чтение in-order очереди: всё до него выполнено
        Marshal.fromNativeFast(dst, array);
    }

    /** @return {@code true}, если устройство NVIDIA: его драйвер умеет печатать ресурсы ядра в лог сборки */
    private boolean isNvidia() {
        return device.vendor() != null && device.vendor().toUpperCase(java.util.Locale.ROOT).contains("NVIDIA");
    }

    /**
     * Опции {@code clBuildProgram}: опции кодогена плюс {@link DriverStats#NVIDIA_VERBOSE} на NVIDIA, чтобы получить число
     * регистров (на код и производительность опция не влияет).
     */
    private String buildOptions(Artifact art) {
        String o = art.buildOptions();
        if (!isNvidia()) {
            return o;
        }
        return o == null || o.isBlank() ? DriverStats.NVIDIA_VERBOSE : o + " " + DriverStats.NVIDIA_VERBOSE;
    }

    /** Параметры-массивы, которые ядро перезаписывает целиком; ошибка анализа означает «не доказано». */
    private static boolean[] fullyWritten(IrModule m, int kernel) {
        try {
            return com.sixik.gpu.ir.optimizer.WriteCoverage.fullyWrittenParams(m, kernel);
        } catch (RuntimeException e) {
            return new boolean[m.fParamCount(kernel)];
        }
    }

    /** Оценка регистров по IR; ошибка оценки не мешает компиляции. */
    private static int estimateRegisters(IrModule m, int kernel) {
        try {
            return com.sixik.gpu.ir.optimizer.RegisterPressure.analyze(m).functions()[kernel].registers();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** Часть ключей кэша, зависящая от настроек оптимизатора. */
    private String optimizeKey() {
        OptimizeOptions o = optimizeOptions;
        return "opt" + o.level() + (o.fastMath() ? "f" : "") + (o.contractFma() ? "c" : "") + "/" + o.registerBudget()
                + "/split=" + splitMode.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** @return режим деления ядер на стадии этого контекста */
    public KernelSplitMode splitMode() {
        return splitMode;
    }

    /** @return настройки оптимизатора IR этого контекста */
    public OptimizeOptions optimizeOptions() {
        return optimizeOptions;
    }

    /**
     * Оптимизирует модуль перед генерацией (REWORK_ARCHITECTURE.md, 7.1). Типы и параметры ядра оптимизатор не меняет,
     * поэтому проверка аргументов и раскладка буферов по оптимизированному модулю те же, что по исходному; меняется только
     * индекс функции (недостижимые device-функции выбрасываются). Отвергнутые проходы и превышение бюджета регистров
     * сообщаются предупреждениями.
     *
     * @param m       исходный модуль
     * @param kernel  индекс ядра в нём
     * @param options параметры генерации
     * @return результат оптимизации либо {@code null}, если модуль генерируется как есть
     */
    private OptimizeResult optimizeForEmit(IrModule m, int kernel, EmitOptions options) {
        if (options.debug() || optimizeOptions.level() <= 0) {
            return null;
        }
        long t0 = System.nanoTime();
        OptimizeResult r;
        try {
            r = Optimizer.optimize(m, optimizeOptions);
        } catch (RuntimeException e) {
            emit(GpuEvent.Kind.WARNING, "f" + kernel, 0, 0, "IR optimizer failed, the kernel is compiled unoptimized: " + e);
            return null;
        }
        int nk = r.newIndexOf(kernel);
        if (nk < 0) {
            return null;
        }
        for (String failure : r.failures()) {
            emit(GpuEvent.Kind.WARNING, "f" + kernel, 0, 0, "IR optimizer pass rejected: " + failure);
        }
        if (r.overBudget().contains(nk)) {
            int regs = r.pressureAfter().functions()[nk].registers();
            emit(GpuEvent.Kind.WARNING, "f" + kernel, System.nanoTime() - t0, 0, "estimated register pressure " + regs
                    + " exceeds the budget " + optimizeOptions.registerBudget()
                    + "; the kernel may spill (KernelSplitter can split it into stages)");
        }
        return r;
    }

    private Result<CompiledKernel> build(IrModule original, int originalKernel, EmitOptions options, String[] how) {
        OptimizeResult optimized = optimizeForEmit(original, originalKernel, options);
        IrModule m = optimized == null ? original : optimized.module();
        int kernel = optimized == null ? originalKernel : optimized.newIndexOf(originalKernel);
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
            String[] driverLog = {""};
            long[] cached = diskKey == null ? null : loadFromDisk(diskKey, art, driverLog);
            long kernelHandle;
            List<Diagnostic> log = List.of();
            if (cached != null) {
                program = cached[0];
                kernelHandle = cached[1];
                how[0] = "disk-hit";
            } else {
                program = api.createProgram(contextHandle, art.source());
                DeviceApi.BuildOutcome outcome = buildProgram(program, buildOptions(art));
                driverLog[0] = outcome.log();
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
            DriverStats stats = (isNvidia() ? DriverStats.parseNvidiaLog(driverLog[0], art.entry()) : DriverStats.UNKNOWN)
                    .withPrivateBytes(api.kernelPrivateMemSize(kernelHandle, device.handle()));
            CompiledKernel ck = new CompiledKernel(this, m, kernel, art, kernelIdOf(original), access, program, kernelHandle,
                    maxWg, stats, estimateRegisters(m, kernel), fullyWritten(m, kernel));
            compiled.add(ck);
            attachSplit(ck, m, kernel, options);
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

    /**
     * Решает, исполнять ли ядро по стадиям {@code KernelSplitter} (см. {@link KernelSplitMode}), и если да - собирает
     * стадии. Любая неудача оставляет ядро целым: деление - оптимизация, а не условие работы. Под замком.
     *
     * @param ck      собранное целое ядро
     * @param m       модуль, из которого оно собрано
     * @param kernel  индекс ядра в модуле
     * @param options параметры генерации
     */
    private void attachSplit(CompiledKernel ck, IrModule m, int kernel, EmitOptions options) {
        if (splitMode == KernelSplitMode.OFF || options.debug()) {
            return;
        }
        DriverStats stats = ck.driverStats();
        boolean known = stats.registers() >= 0;
        boolean want = splitMode == KernelSplitMode.ALWAYS
                || (known ? stats.spills() : ck.estimatedRegisters() > optimizeOptions.registerBudget());
        if (!want) {
            return;
        }
        String entry = ck.artifact().entry();
        com.sixik.gpu.ir.optimizer.KernelSplitter.SplitResult sr;
        try {
            // драйвер видит проливание (или деление запрошено всегда), а оценка укладывается в бюджет: цель стадий -
            // заметно ниже оценки целого ядра, иначе KernelSplitter откажется делить
            int estimate = ck.estimatedRegisters();
            int budget = optimizeOptions.registerBudget();
            OptimizeOptions target = estimate > budget || estimate <= 0 ? optimizeOptions
                    : optimizeOptions.withRegisterBudget(Math.max(1, estimate * 2 / 3));
            sr = com.sixik.gpu.ir.optimizer.KernelSplitter.split(m, kernel, target);
        } catch (RuntimeException e) {
            emit(GpuEvent.Kind.WARNING, entry, 0, 0, "kernel splitting failed, the kernel runs whole: " + e);
            return;
        }
        if (!sr.isSplit()) {
            if (splitMode == KernelSplitMode.AUTO) {
                emit(GpuEvent.Kind.WARNING, entry, 0, 0, "the kernel spills registers but cannot be split: " + sr.reason());
            }
            return;
        }
        com.sixik.gpu.ir.optimizer.KernelSplitter.KernelSplit plan = sr.split();
        IrModule sm = sr.module();
        int[] stageFns = plan.stages();
        CompiledKernel[] stages = new CompiledKernel[stageFns.length];
        long t0 = System.nanoTime();
        try {
            long minWg = ck.maxWorkGroupSize();
            for (int j = 0; j < stageFns.length; j++) {
                stages[j] = buildStage(sm, stageFns[j], options, ck.kernelId() + "$s" + j);
                if (stages[j] == null) {
                    releaseStages(stages);
                    return;
                }
                minWg = Math.min(minWg, stages[j].maxWorkGroupSize());
            }
            if (splitMode == KernelSplitMode.AUTO && known) {
                long before = (long) stats.spillStoreBytes() + stats.spillLoadBytes();
                long worst = 0;
                for (CompiledKernel s : stages) {
                    DriverStats st = s.driverStats();
                    worst = Math.max(worst, st.registers() < 0 ? before : (long) st.spillStoreBytes() + st.spillLoadBytes());
                }
                if (worst >= before) {
                    emit(GpuEvent.Kind.COMPILE, entry, System.nanoTime() - t0, 0, "split rejected: the stages still spill "
                            + worst + " bytes (whole kernel: " + before + ")");
                    releaseStages(stages);
                    return;
                }
            }
            int sc = plan.scratch().size();
            int[] param = new int[sc];
            long[] bytes = new long[sc];
            int[] perItem = new int[sc];
            for (int i = 0; i < sc; i++) {
                com.sixik.gpu.ir.optimizer.KernelSplitter.Scratch s = plan.scratch().get(i);
                param[i] = s.paramIndex();
                perItem[i] = s.elementsPerWorkItem();
                bytes[i] = (long) com.sixik.gpu.ir.AbiLayout.sizeOf(sm, s.elementType()) * s.elementsPerWorkItem();
            }
            ck.split = new CompiledKernel.SplitPlan(stages, param, bytes, perItem, minWg);
            // ALWAYS - стадии сразу; AUTO - решит замер на первых запусках (SPLIT_TRIAL_LAUNCHES)
            ck.splitChosen = splitMode == KernelSplitMode.ALWAYS ? Boolean.TRUE : null;
            emit(GpuEvent.Kind.COMPILE, entry, System.nanoTime() - t0, 0, "split into " + stages.length
                    + " stages, estimated registers " + plan.originalRegisters() + " -> "
                    + java.util.Arrays.toString(plan.stageRegisters()));
        } catch (DeviceApiException e) {
            releaseStages(stages);
            emit(GpuEvent.Kind.WARNING, entry, 0, 0, "driver error while building kernel stages, the kernel runs whole: "
                    + e.getMessage());
        }
    }

    /**
     * Собирает одну стадию разделённого ядра из исходника (без дискового кэша). Под замком.
     *
     * @return стадия либо {@code null}, если генератор или драйвер её отверг (сообщается предупреждением)
     */
    private CompiledKernel buildStage(IrModule sm, int fn, EmitOptions options, String id) {
        Result<Artifact> emitted = OpenClEmitter.emit(sm, fn, target, options);
        if (!emitted.isOk()) {
            emit(GpuEvent.Kind.WARNING, id, 0, 0, "kernel stage was not generated, the kernel runs whole: "
                    + emitted.diagnostics());
            return null;
        }
        Artifact art = emitted.value();
        dumpSource(art);
        long program = api.createProgram(contextHandle, art.source());
        DeviceApi.BuildOutcome outcome = buildProgram(program, buildOptions(art));
        if (!outcome.ok()) {
            api.release(DeviceApi.HandleKind.PROGRAM, program);
            emit(GpuEvent.Kind.WARNING, art.entry(), 0, 0, "the driver rejected a kernel stage, the kernel runs whole");
            return null;
        }
        long handle;
        try {
            handle = api.createKernel(program, art.entry());
        } catch (DeviceApiException e) {
            api.release(DeviceApi.HandleKind.PROGRAM, program);
            throw e;
        }
        long maxWg = api.kernelMaxWorkGroupSize(handle, device.handle());
        DriverStats stats = (isNvidia() ? DriverStats.parseNvidiaLog(outcome.log(), art.entry()) : DriverStats.UNKNOWN)
                .withPrivateBytes(api.kernelPrivateMemSize(handle, device.handle()));
        int[] access = Validator.validate(sm).functions()[fn].paramAccess();
        CompiledKernel stage = new CompiledKernel(this, sm, fn, art, id, access, program, handle, maxWg, stats, -1,
                new boolean[access.length]);
        compiled.add(stage);
        return stage;
    }

    /**
     * Стек потока, на котором драйвер собирает программу. Компилятор OpenCL C в драйвере рекурсивен по глубине выражения:
     * на NVIDIA выражение глубиной ~200 скобок переполняет стек обычного потока, и процесс JVM падает без исключения
     * (поток Java не может перехватить переполнение в нативном коде). Память стека выделяется по мере использования.
     */
    static final long BUILD_STACK_BYTES = 512L << 20;

    /**
     * Сборка программы драйвером на отдельном потоке с большим стеком ({@link #BUILD_STACK_BYTES}). Вызывающий ждёт её
     * завершения; замок контекста остаётся за вызывающим (сборка - обычный вызов драйвера под ним).
     *
     * @param program программа
     * @param options параметры сборки
     * @return итог сборки
     */
    private DeviceApi.BuildOutcome buildProgram(long program, String options) {
        DeviceApi.BuildOutcome[] outcome = new DeviceApi.BuildOutcome[1];
        RuntimeException[] failure = new RuntimeException[1];
        Thread t = new Thread(null, () -> {
            try {
                outcome[0] = api.build(program, device.handle(), options);
            } catch (RuntimeException e) {
                failure[0] = e;
            }
        }, "javatogpu-build", BUILD_STACK_BYTES);
        t.setDaemon(true);
        t.start();
        boolean interrupted = false;
        while (true) {
            try {
                t.join();
                break;
            } catch (InterruptedException e) {
                interrupted = true; // сборку прервать нельзя: дождаться и восстановить флаг
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (failure[0] != null) {
            throw failure[0];
        }
        return outcome[0];
    }

    /** Освобождает уже собранные стадии (под замком). */
    private void releaseStages(CompiledKernel[] stages) {
        for (CompiledKernel s : stages) {
            if (s != null && compiled.remove(s)) {
                api.release(DeviceApi.HandleKind.KERNEL, s.kernel);
                api.release(DeviceApi.HandleKind.PROGRAM, s.program);
            }
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
        return "gbc1|codegen=" + codegenVersion + "|" + optimizeKey() + "|ir=" + kernelIdOf(m) + "#" + kernel
                + "|emit=" + o.debug() + "," + o.fastMath() + "|build=" + buildOptions(art)
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
    private long[] loadFromDisk(String key, Artifact art, String[] logOut) {
        byte[] image = disk.get(key);
        if (image == null) {
            diskMisses.incrementAndGet();
            return null;
        }
        long program = 0;
        try {
            program = api.createProgramWithBinary(contextHandle, device.handle(), image);
            DeviceApi.BuildOutcome outcome = buildProgram(program, buildOptions(art));
            logOut[0] = outcome.log();
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
            throw new GpuException(List.of(error("RT-X01", driverError("allocating a buffer", e))));
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
        try (Arena a = Arena.ofConfined()) { // крупные массивы - через общий закреплённый буфер
            requireOpen();
            long t0 = System.nanoTime();
            if (bytes > 0) {
                upload(b.handle(), array, bytes, a);
            }
            emit(GpuEvent.Kind.UPLOAD, b.toString(), System.nanoTime() - t0, bytes, "");
        } catch (DeviceApiException e) {
            throw new GpuException(List.of(error("RT-X01", driverError("writing a buffer", e))));
        } finally {
            lock.unlock();
        }
    }

    void read(GpuBuffer b, Object array) {
        checkTransfer(b, array);
        long bytes = Marshal.byteSize(array);
        lock.lock();
        try (Arena a = Arena.ofConfined()) { // крупные массивы - через общий закреплённый буфер
            requireOpen();
            long t0 = System.nanoTime();
            if (bytes > 0) {
                download(b.handle(), array, bytes, a);
            }
            emit(GpuEvent.Kind.READBACK, b.toString(), System.nanoTime() - t0, bytes, "");
        } catch (DeviceApiException e) {
            throw new GpuException(List.of(error("RT-X01", driverError("reading a buffer", e))));
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

    /**
     * Запуск с переиспользованием подготовленного запуска - путь обычного вызова {@code @Kernel}-метода через фасад
     * {@link JavaToGpu}. Повторный вызов того же ядра с теми же массивами хоста (по идентичности) переиспользует буферы
     * устройства и закреплённую промежуточную память: на мелких вызовах это в разы быстрее {@link #launch}.
     *
     * <p>Результат тот же, что у {@link #launch}: входы загружаются при каждом вызове (изменения массивов между вызовами
     * видны), записанные массивы читаются обратно, скаляры и {@link GpuBuffer} подставляются заново. Кэш держит не более
     * {@value #REUSE_MAX_ENTRIES} запусков и {@link #REUSE_MAX_BYTES} байт массивов (LRU); вытесненный запуск освобождает
     * свою память. Пока запуск в кэше, он удерживает ссылки на свои массивы; {@link #trimLaunchCache()} освобождает всё.
     * Если подготовить запуск нельзя, выполняется обычный {@link #launch} (с его диагностиками).</p>
     *
     * @param kernel ядро этого контекста
     * @param config размеры запуска
     * @param args   аргументы
     * @return итог либо диагностики, как у {@link #launch}
     */
    Result<LaunchStats> launchReusing(CompiledKernel kernel, LaunchConfig config, Object[] args) {
        return withResidentArrays(kernel, config, args, true);
    }

    /** {@link #launchReusing} без привязки {@code GpuArray} (аргументы уже привязаны). */
    private Result<LaunchStats> launchReusingHost(CompiledKernel kernel, LaunchConfig config, Object[] args) {
        lock.lock();
        try {
            if (closed || kernel.owner() != this) {
                return launchHost(kernel, config, args);
            }
            IrModule m = kernel.module();
            int f = kernel.function();
            if (args.length != m.fParamCount(f)) {
                return launchHost(kernel, config, args);
            }
            Object[] arrays = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                if (m.typeKind(m.fParamType(f, i)) == Types.KIND_ARRAY && args[i] != null && !(args[i] instanceof GpuBuffer)) {
                    arrays[i] = args[i];
                }
            }
            // и без массивов хоста (только GpuArray/GpuBuffer и скаляры) подготовленный запуск выгоден: драйверу
            // передаются заново только изменившиеся аргументы
            ReuseKey key = new ReuseKey(kernel, arrays);
            Reused r = reuse.get(key);
            if (r != null && r.launcher.isClosed()) {
                forgetReused(key, r);
                r = null;
            }
            if (r == null) {
                Result<PreparedLauncher> p = prepare(kernel, args);
                if (!p.isOk()) {
                    return launchHost(kernel, config, args);
                }
                r = new Reused(p.value(), args.clone(), p.value().hostBytes());
                reuse.put(key, r);
                reuseBytes += r.bytes;
                evictReused();
            } else {
                for (int i = 0; i < args.length; i++) {
                    if (arrays[i] == null && !sameValue(r.last[i], args[i])) {
                        try {
                            r.launcher.set(i, args[i]);
                        } catch (GpuException e) {
                            return Result.failed(e.diagnostics());
                        }
                        r.last[i] = args[i];
                    }
                }
            }
            return r.launcher.launch(config);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Значение не-массивного аргумента не изменилось: тот же {@link GpuBuffer} либо равный упакованный примитив. Объекты
     * векторов и struct изменяемы, поэтому всегда подставляются заново.
     */
    private static boolean sameValue(Object previous, Object current) {
        if (previous == current) {
            return previous == null || previous instanceof GpuBuffer || previous instanceof Number
                    || previous instanceof Boolean || previous instanceof Character;
        }
        return (current instanceof Number || current instanceof Boolean || current instanceof Character)
                && current.equals(previous);
    }

    private void evictReused() {
        java.util.Iterator<java.util.Map.Entry<ReuseKey, Reused>> it = reuse.entrySet().iterator();
        while (reuse.size() > 1 && (reuse.size() > REUSE_MAX_ENTRIES || reuseBytes > REUSE_MAX_BYTES) && it.hasNext()) {
            java.util.Map.Entry<ReuseKey, Reused> eldest = it.next();
            it.remove();
            reuseBytes -= eldest.getValue().bytes;
            eldest.getValue().launcher.close();
        }
    }

    private void forgetReused(ReuseKey key, Reused r) {
        reuse.remove(key);
        reuseBytes -= r.bytes;
    }

    /**
     * Освобождает все подготовленные запуски кэша обычных вызовов (буферы устройства, закреплённую память и ссылки на
     * массивы хоста). Следующий вызов ядра подготовит запуск заново.
     */
    public void trimLaunchCache() {
        lock.lock();
        try {
            for (Reused r : reuse.values()) {
                r.launcher.close();
            }
            reuse.clear();
            reuseBytes = 0;
        } finally {
            lock.unlock();
        }
    }

    /** @return число подготовленных запусков в кэше обычных вызовов (для тестов и диагностики) */
    int reusedLaunches() {
        lock.lock();
        try {
            return reuse.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Чтение буфера в его закреплённую память без копирования в массив ({@link GpuBuffer#readView()}).
     *
     * @param b буфер
     * @return вид только для чтения
     */
    MemorySegment readView(GpuBuffer b) {
        if (b.owner() != this) {
            throw new GpuException(List.of(error("RT-A02", "the buffer belongs to another context")));
        }
        lock.lock();
        try {
            requireOpen();
            if (b.isClosed()) {
                throw new GpuException(List.of(error("RT-A02", "the buffer is already closed")));
            }
            long bytes = b.byteSize();
            MemorySegment mem = b.viewMemory(api, contextHandle, queue);
            long t0 = System.nanoTime();
            if (bytes > 0) {
                api.readBuffer(queue, b.handle(), 0, bytes, mem);
                afterSync();
            }
            emit(GpuEvent.Kind.READBACK, b.toString(), System.nanoTime() - t0, bytes, "view");
            return mem.asSlice(0, bytes).asReadOnly();
        } catch (DeviceApiException e) {
            throw new GpuException(List.of(error("RT-X01", driverError("reading a buffer", e))));
        } finally {
            lock.unlock();
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
            b.releaseView(api, queue, !closed);
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
     * @param args   значения параметров ядра по порядку: {@link GpuBuffer}, {@code GpuArray}, Java-массив или скаляр
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
        Object[] given = args == null ? new Object[0] : args.clone();
        // GpuArray - буфер, закреплённый за массивом в этом контексте; синхронизация - при каждом запуске
        com.sixik.gpu.api.types.GpuArray[] resident = new com.sixik.gpu.api.types.GpuArray[given.length];
        lock.lock();
        try {
            requireOpen();
            for (int i = 0; i < given.length; i++) {
                if (given[i] instanceof com.sixik.gpu.api.types.GpuArray a) {
                    resident[i] = a;
                    given[i] = ResidentArrays.bufferOf(this, a);
                }
            }
        } catch (GpuException e) {
            return Result.failed(e.diagnostics());
        } catch (DeviceApiException e) {
            return failure("RT-X01", driverError("allocating a buffer", e));
        } finally {
            lock.unlock();
        }
        Result<PreparedLauncher> r = PreparedLauncher.create(this, kernel, given, resident);
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
        return withResidentArrays(kernel, config, actual, false);
    }

    /**
     * Привязывает аргументы {@code GpuArray} к буферам устройства, запускает ядро обычным путём либо через кэш
     * подготовленных запусков и отмечает записанные массивы свежими на устройстве.
     */
    private Result<LaunchStats> withResidentArrays(CompiledKernel kernel, LaunchConfig config, Object[] args,
                                                   boolean reuse) {
        lock.lock();
        try {
            ResidentArrays.Bound bound;
            if (closed || kernel.owner() != this) {
                bound = new ResidentArrays.Bound(args, List.of(), List.of(), new boolean[0]);
            } else {
                try {
                    bound = ResidentArrays.bind(this, kernel, config, args);
                } catch (GpuException e) {
                    return Result.failed(e.diagnostics());
                } catch (DeviceApiException e) {
                    return failure("RT-X01", driverError("uploading a GpuArray", e));
                }
            }
            Result<LaunchStats> r;
            boolean previous = deferAllowed;
            deferAllowed = !bound.none();
            try {
                r = reuse ? launchReusingHost(kernel, config, bound.args()) : launchHost(kernel, config, bound.args());
            } finally {
                deferAllowed = previous;
            }
            if (r.isOk() && !bound.none()) {
                ResidentArrays.afterSuccess(kernel, bound);
            }
            return r;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Неблокирующая загрузка массива хоста в буфер (асинхронная цепочка {@code GpuArray}). Под замком.
     *
     * @param b     буфер
     * @param array примитивный массив хоста
     */
    void uploadAsync(GpuBuffer b, Object array) {
        requireOpen();
        long bytes = Marshal.byteSize(array);
        if (bytes == 0) {
            return;
        }
        long t0 = System.nanoTime();
        // auto-арена: закрывать не нужно (это дорого для shared), память освободит GC, когда ссылка уйдёт после
        // ближайшей синхронизации
        MemorySegment seg = Arena.ofAuto().allocate(Math.max(bytes, 8), 8);
        Marshal.copyToNativeFast(array, seg);
        api.writeBufferAsync(queue, b.handle(), 0, bytes, seg);
        pendingStaging.add(seg);
        pendingBytes += bytes;
        emit(GpuEvent.Kind.UPLOAD, b.toString(), System.nanoTime() - t0, bytes, "async");
        if (pendingBytes > MAX_PENDING_BYTES) {
            synchronizeQueue();
        }
    }

    /** Ждёт все команды очереди и освобождает отложенную промежуточную память (под замком). */
    void synchronizeQueue() {
        api.finish(queue);
        afterSync();
    }

    /** Очередь только что дошла до точки синхронизации: отложенное можно освободить (под замком). */
    void afterSync() {
        pendingStaging.clear(); // память auto-арен освободит GC
        pendingBytes = 0;
        pendingLaunches = 0;
        pendingKernels.clear();
    }

    /**
     * Сообщение об ошибке драйвера в точке синхронизации. Если с прошлой синхронизации ядра ставились без ожидания,
     * ошибка могла произойти в любом из них - они перечисляются. Очередь после ошибки считается дошедшей до точки
     * синхронизации: отложенное сбрасывается, чтобы следующая ошибка не приписывалась тем же ядрам. Под замком.
     *
     * @param what что делалось ({@code "reading a buffer"})
     * @param e    ошибка драйвера
     * @return текст диагностики
     */
    String driverError(String what, DeviceApiException e) {
        StringBuilder sb = new StringBuilder("driver error while ").append(what).append(": ").append(e.getMessage());
        if (!pendingKernels.isEmpty()) {
            sb.append("; the error may come from a kernel launched earlier without waiting for completion: ")
                    .append(String.join(", ", pendingKernels));
        }
        afterSync();
        return sb.toString();
    }

    /**
     * Разрешает либо запрещает текущему запуску не ждать завершения ядра (под замком).
     *
     * @param allowed новое значение
     * @return прежнее значение (восстановить после запуска)
     */
    boolean allowDefer(boolean allowed) {
        boolean previous = deferAllowed;
        deferAllowed = allowed;
        return previous;
    }

    /** @return разрешено ли текущему запуску не ждать завершения ядра (под замком) */
    boolean deferAllowedNow() {
        return deferAllowed;
    }

    /** Регистрирует массив, привязанный к контексту (под замком). */
    void registerResident(com.sixik.gpu.api.types.GpuArray array) {
        residents.put(array, Boolean.TRUE);
    }

    /** Обычный запуск без привязки {@code GpuArray}. */
    private Result<LaunchStats> launchHost(CompiledKernel kernel, LaunchConfig config, Object[] actual) {
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
            return failure("RT-X01", driverError("launching a kernel", e));
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
        return dispatch(k, cfg, local, false);
    }

    /**
     * Ставит ядро в очередь; при {@code defer} не ждёт завершения (если отложенных запусков не слишком много). Тогда
     * возвращаемое время - время постановки в очередь, а ошибка исполнения проявится в ближайшей точке синхронизации.
     *
     * @return время работы ядра (либо постановки в очередь), нс
     */
    long dispatch(CompiledKernel k, LaunchConfig cfg, long[] local, boolean defer) {
        long[] global = new long[cfg.dim()];
        for (int d = 0; d < global.length; d++) {
            global[d] = cfg.global(d);
        }
        long t1 = System.nanoTime();
        api.enqueue(queue, k.kernel, cfg.dim(), global, local);
        afterEnqueue(k.artifact().entry() + " " + cfg, defer);
        long nanos = System.nanoTime() - t1;
        emit(GpuEvent.Kind.KERNEL, k.artifact().entry(), nanos, 0, cfg.toString());
        return nanos;
    }

    /** После постановки запуска в очередь: отложить ожидание (если можно) либо дождаться очереди. Под замком. */
    private void afterEnqueue(String what, boolean defer) {
        if (defer && pendingLaunches < MAX_DEFERRED_LAUNCHES) {
            pendingLaunches++;
            pendingKernels.add(what);
        } else {
            synchronizeQueue();
        }
    }

    /**
     * План деления, если этот запуск исполняется по стадиям: ядро разделено, промежуточные буферы помещаются в память и
     * индексы {@code int}, а заданная work-group допустима для всех стадий. Иначе {@code null} - запуск целиком.
     *
     * @param k     ядро
     * @param cfg   размеры запуска
     * @param local размер work-group либо {@code null}
     * @return план либо {@code null}
     */
    CompiledKernel.SplitPlan splitFor(CompiledKernel k, LaunchConfig cfg, long[] local) {
        CompiledKernel.SplitPlan p = k.split;
        if (p == null || Boolean.FALSE.equals(k.splitChosen)) {
            return null;
        }
        long items = 1;
        for (int d = 0; d < cfg.dim(); d++) {
            items *= cfg.global(d);
        }
        long maxBytes = device.maxAllocation() > 0 ? Math.min(device.maxAllocation(), MAX_SCRATCH_BYTES) : MAX_SCRATCH_BYTES;
        for (int i = 0; i < p.scratchParam().length; i++) {
            if (items * p.scratchPerItem()[i] > Integer.MAX_VALUE || items * p.scratchBytes()[i] > maxBytes) {
                return null;
            }
        }
        if (local != null) {
            long wg = 1;
            for (long l : local) {
                wg *= l;
            }
            if (wg > p.maxWorkGroupSize()) {
                return null;
            }
        }
        return p;
    }

    /**
     * Выставляет аргументы и ставит ядро в очередь: целиком либо по стадиям (если {@link #splitFor} даёт план). Стадии
     * идут подряд без ожидания между ними (очередь in-order); промежуточные буферы выделяет и держит контекст. Под замком.
     *
     * @param k       ядро
     * @param cfg     размеры запуска
     * @param local   размер work-group либо {@code null}
     * @param args    аргументы (по параметрам исходного ядра)
     * @param handles дескрипторы буферов по параметрам
     * @param lengths длины массивов по параметрам
     * @param arena   память для упаковки значений struct/векторов
     * @param defer   можно не ждать завершения
     * @return время работы (либо постановки в очередь), нс
     */
    long dispatchKernel(CompiledKernel k, LaunchConfig cfg, long[] local, Object[] args, long[] handles, long[] lengths,
                        Arena arena, boolean defer) {
        CompiledKernel.SplitPlan p = splitFor(k, cfg, local);
        if (p == null) {
            setKernelArgs(k, args, handles, lengths, arena);
            return dispatch(k, cfg, local, defer);
        }
        if (k.splitChosen == null) {
            return trialDispatch(k, p, cfg, local, args, handles, lengths, arena);
        }
        long t1 = System.nanoTime();
        enqueueStages(k, p, cfg, local, args, handles, lengths, arena);
        afterEnqueue(k.artifact().entry() + " " + cfg + " (" + p.stages().length + " stages)", defer);
        long nanos = System.nanoTime() - t1;
        emit(GpuEvent.Kind.KERNEL, k.artifact().entry(), nanos, 0, cfg + ", " + p.stages().length + " stages");
        return nanos;
    }

    /**
     * Сколько первых запусков ядра-кандидата в режиме {@link KernelSplitMode#AUTO} уходит на замер: поочерёдно целиком и
     * по стадиям, с ожиданием каждого; первые два (по одному каждого варианта) - прогрев и в замер не входят.
     */
    static final int SPLIT_TRIAL_LAUNCHES = 10;

    /**
     * Пробный запуск режима {@link KernelSplitMode#AUTO}: чередует целое ядро и стадии, меряет каждый вариант с
     * ожиданием очереди и после {@link #SPLIT_TRIAL_LAUNCHES} запусков выбирает: стадии остаются, только если они
     * быстрее целого ядра больше чем на 5%. Под замком.
     */
    private long trialDispatch(CompiledKernel k, CompiledKernel.SplitPlan p, LaunchConfig cfg, long[] local,
                               Object[] args, long[] handles, long[] lengths, Arena arena) {
        int n = k.trialLaunches++;
        boolean stagesArm = n % 2 == 1;
        synchronizeQueue(); // в замер не должна попасть чужая работа очереди
        long t1 = System.nanoTime();
        if (stagesArm) {
            enqueueStages(k, p, cfg, local, args, handles, lengths, arena);
        } else {
            setKernelArgs(k, args, handles, lengths, arena);
            long[] global = new long[cfg.dim()];
            for (int d = 0; d < global.length; d++) {
                global[d] = cfg.global(d);
            }
            api.enqueue(queue, k.kernel, cfg.dim(), global, local);
        }
        synchronizeQueue();
        long nanos = System.nanoTime() - t1;
        if (n >= 2) {
            if (stagesArm) {
                k.trialSplitNanos += nanos;
            } else {
                k.trialWholeNanos += nanos;
            }
        }
        String entry = k.artifact().entry();
        emit(GpuEvent.Kind.KERNEL, entry, nanos, 0, cfg + (stagesArm ? ", " + p.stages().length + " stages" : "")
                + ", split trial");
        if (k.trialLaunches >= SPLIT_TRIAL_LAUNCHES) {
            boolean split = k.trialSplitNanos < k.trialWholeNanos * 0.95;
            k.splitChosen = split;
            int runs = (SPLIT_TRIAL_LAUNCHES - 2) / 2;
            emit(GpuEvent.Kind.COMPILE, entry, 0, 0, (split ? "split kept" : "split rejected") + " by measurement: whole "
                    + k.trialWholeNanos / runs / 1000 + " us, " + p.stages().length + " stages "
                    + k.trialSplitNanos / runs / 1000 + " us");
        }
        return nanos;
    }

    /** Выставляет аргументы стадий (с промежуточными буферами) и ставит стадии в очередь подряд. Под замком. */
    private void enqueueStages(CompiledKernel k, CompiledKernel.SplitPlan p, LaunchConfig cfg, long[] local,
                               Object[] args, long[] handles, long[] lengths, Arena arena) {
        long[] global = new long[cfg.dim()];
        long items = 1;
        for (int d = 0; d < global.length; d++) {
            global[d] = cfg.global(d);
            items *= global[d];
        }
        int total = args.length + p.scratchParam().length;
        Object[] xa = java.util.Arrays.copyOf(args, total);
        long[] xh = java.util.Arrays.copyOf(handles, total);
        long[] xl = java.util.Arrays.copyOf(lengths, total);
        for (int i = 0; i < p.scratchParam().length; i++) {
            xh[p.scratchParam()[i]] = scratch(i, Math.max(4, items * p.scratchBytes()[i]));
            xl[p.scratchParam()[i]] = items * p.scratchPerItem()[i];
        }
        for (CompiledKernel s : p.stages()) {
            setKernelArgs(s, xa, xh, xl, arena);
            api.enqueue(queue, s.kernel, cfg.dim(), global, local);
        }
    }

    /**
     * Промежуточный буфер номер {@code i} не меньше {@code bytes} байт (переиспользуется, растёт по требованию). Под
     * замком. Прежний буфер освобождается сразу: драйвер держит память, пока её используют поставленные команды.
     */
    private long scratch(int i, long bytes) {
        if (i >= scratchHandles.length) {
            scratchHandles = java.util.Arrays.copyOf(scratchHandles, i + 1);
            scratchSizes = java.util.Arrays.copyOf(scratchSizes, i + 1);
        }
        if (scratchSizes[i] < bytes) {
            long h = api.createBuffer(contextHandle, bytes);
            if (scratchHandles[i] != 0) {
                api.release(DeviceApi.HandleKind.BUFFER, scratchHandles[i]);
            }
            scratchHandles[i] = h;
            scratchSizes[i] = bytes;
        }
        return scratchHandles[i];
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
        // массивы хоста, которые ядро только пишет и перезаписывает целиком: загружать не нужно
        Map<Object, int[]> paramsOf = new IdentityHashMap<>();
        for (int i = 0; i < n; i++) {
            if (m.typeKind(m.fParamType(f, i)) == Types.KIND_ARRAY && !(args[i] instanceof GpuBuffer)) {
                int[] ps = paramsOf.computeIfAbsent(args[i], x -> new int[n + 1]);
                ps[1 + ps[0]++] = i;
            }
        }
        // confined: закрытие shared-арены дорого (рукопожатие со всеми потоками) и стоило бы на каждом запуске;
        // крупные массивы и так идут через общий закреплённый буфер контекста, доступный потокам копирования
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
                    int[] ps = paramsOf.get(a);
                    boolean skip = k.uploadSkippable(java.util.Arrays.copyOfRange(ps, 1, 1 + ps[0]), ps[0],
                            Marshal.length(a), cfg);
                    if (bytes > 0 && !skip) {
                        if (objects) {
                            api.writeBuffer(queue, h, 0, bytes, Marshal.toNative(arena, m, elemType, (Object[]) a));
                        } else {
                            upload(h, a, bytes, arena);
                        }
                    }
                    up += skip ? 0 : bytes;
                    known = new long[]{h, 0, elemType};
                    hostBuffers.put(a, known);
                }
                known[1] |= k.access(i);
                handles[i] = known[0];
                lengths[i] = Marshal.length(a);
            }
            upNanos = System.nanoTime() - t0;
            emit(GpuEvent.Kind.UPLOAD, k.artifact().entry(), upNanos, up, "");

            boolean readsBack = false;
            for (long[] v : hostBuffers.values()) {
                readsBack |= (v[1] & Validator.ACCESS_WRITE) != 0;
            }
            // читать на хост нечего - ждать завершения ядра не нужно: дождётся первая блокирующая операция очереди
            kernelNanos = dispatchKernel(k, cfg, local, args, handles, lengths, arena, deferAllowed && !readsBack);

            long t2 = System.nanoTime();
            for (Map.Entry<Object, long[]> e : hostBuffers.entrySet()) {
                if ((e.getValue()[1] & Validator.ACCESS_WRITE) == 0) {
                    continue;
                }
                int elemType = (int) e.getValue()[2];
                boolean objects = HostObjects.isAggregate(m, elemType);
                long bytes = objects ? Marshal.byteSize(m, elemType, (Object[]) e.getKey()) : Marshal.byteSize(e.getKey());
                if (bytes > 0) {
                    if (objects) {
                        MemorySegment dst = arena.allocate(Math.max(bytes, 8), 8);
                        api.readBuffer(queue, e.getValue()[0], 0, bytes, dst);
                        Marshal.fromNative(dst, m, elemType, (Object[]) e.getKey());
                    } else {
                        download(e.getValue()[0], e.getKey(), bytes, arena);
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
            // массивы GpuArray со свежими данными на устройстве - на хост, пока контекст открыт
            ResidentArrays.syncAll(this, new ArrayList<>(residents.keySet()));
            try {
                synchronizeQueue();
            } catch (DeviceApiException ignored) {
                afterSync(); // очередь сломана: освободить хотя бы память хоста
            }
            residents.clear();
            for (PreparedLauncher l : launchers) {
                l.releaseLocked(); // до closed = true: освобождает свои буферы в драйвере
            }
            launchers.clear();
            reuse.clear();
            reuseBytes = 0;
            closed = true;
            for (CompiledKernel k : compiled) {
                api.release(DeviceApi.HandleKind.KERNEL, k.kernel);
                api.release(DeviceApi.HandleKind.PROGRAM, k.program);
            }
            compiled.clear();
            for (long h : scratchHandles) {
                if (h != 0) {
                    api.release(DeviceApi.HandleKind.BUFFER, h);
                }
            }
            scratchHandles = new long[0];
            scratchSizes = new long[0];
            for (GpuBuffer b : buffers) {
                b.markClosed();
                try {
                    b.releaseView(api, queue, true);
                } catch (DeviceApiException ignored) {
                    // контекст всё равно освобождается целиком
                }
                api.release(DeviceApi.HandleKind.BUFFER, b.handle());
            }
            buffers.clear();
            if (transferPool != null) {
                try {
                    api.releasePinned(queue, transferPool);
                } catch (DeviceApiException ignored) {
                    // контекст всё равно освобождается целиком
                }
                transferPool = null;
            }
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
