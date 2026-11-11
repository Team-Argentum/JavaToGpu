package com.sixik.gpu.runtime.device;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Фейковая реализация {@link DeviceApi} для тестов без железа: память - массивы байт, «ядро» - лямбда, которую тест
 * регистрирует под именем точки входа. Ведёт журнал вызовов, считает живые объекты (чтобы тесты ловили утечки) и умеет
 * имитировать ошибки драйвера и отказ компиляции.
 *
 * <p>Не потокобезопасна (как и настоящий слой): сериализацию обеспечивает {@code GpuContext}. Счётчики и журнал
 * читаются тестом после завершения вызовов.</p>
 */
public final class FakeDeviceApi implements DeviceApi {

    /**
     * Описание устройства фейка.
     *
     * @param platform   имя платформы
     * @param name       имя устройства
     * @param vendor     производитель
     * @param type       маска {@code TYPE_*}
     * @param memory     объём глобальной памяти
     * @param extensions строка расширений
     * @param maxGroup   наибольший размер work-group
     * @param maxConstant наибольший размер буфера {@code __constant}, байт
     */
    public record Spec(String platform, String name, String vendor, long type, long memory, String extensions,
                       long maxGroup, long maxConstant) {
        /** @return GPU с fp64 и 64-битными атомиками, 8 ГиБ, группа до 1024 */
        public static Spec gpu() {
            return new Spec("Fake Platform", "Fake GPU", "Fake Vendor", TYPE_GPU, 8L << 30,
                    "cl_khr_fp64 cl_khr_int64_base_atomics cl_khr_byte_addressable_store", 1024, 64 * 1024);
        }

        /** @return CPU без fp64, 4 ГиБ, группа до 256 */
        public static Spec cpu() {
            return new Spec("Fake Platform", "Fake CPU", "Other Vendor", TYPE_CPU, 4L << 30, "", 256, 64 * 1024);
        }

        /** @param ext расширения @return копия с другими расширениями */
        public Spec withExtensions(String ext) {
            return new Spec(platform, name, vendor, type, memory, ext, maxGroup, maxConstant);
        }

        /** @param n имя @return копия с другим именем */
        public Spec withName(String n) {
            return new Spec(platform, n, vendor, type, memory, extensions, maxGroup, maxConstant);
        }

        /** @param bytes размер @return копия с другим пределом буфера {@code __constant} */
        public Spec withMaxConstant(long bytes) {
            return new Spec(platform, name, vendor, type, memory, extensions, maxGroup, bytes);
        }

        /** @param v производитель @return копия с другим производителем */
        public Spec withVendor(String v) {
            return new Spec(platform, name, v, type, memory, extensions, maxGroup, maxConstant);
        }
    }

    /** Запуск, видимый поведению «ядра». */
    public static final class Launch {
        private final Object[] args;
        private final int dim;
        private final long[] global;
        private final long[] local;
        private final FakeDeviceApi owner;

        private Launch(FakeDeviceApi owner, Object[] args, int dim, long[] global, long[] local) {
            this.owner = owner;
            this.args = args;
            this.dim = dim;
            this.global = global;
            this.local = local;
        }

        /** @return сколько аргументов ядра выставлено (номера подряд с нуля) */
        public int argCount() {
            int n = 0;
            while (n < args.length && args[n] != null) {
                n++;
            }
            return n;
        }

        /** @return размерность */
        public int dim() {
            return dim;
        }

        /** @param axis ось @return размер NDRange */
        public long global(int axis) {
            return global[axis];
        }

        /** @return размеры work-group либо {@code null}, если выбирает драйвер */
        public long[] local() {
            return local;
        }

        /** @param i номер аргумента @return данные буфера (little-endian), изменения видны устройству */
        public ByteBuffer buffer(int i) {
            return ByteBuffer.wrap(owner.bufferData.get((Long) args[i])).order(ByteOrder.LITTLE_ENDIAN);
        }

        /** @param i номер аргумента @return байты аргумента-значения (вектор или struct), little-endian */
        public ByteBuffer bytesArg(int i) {
            return ByteBuffer.wrap((byte[]) args[i]).order(ByteOrder.LITTLE_ENDIAN);
        }

        /** @param i номер аргумента @return скаляр как {@code int} */
        public int intArg(int i) {
            return (int) (long) (Long) args[i];
        }

        /** @param i номер аргумента @return скаляр как {@code long} */
        public long longArg(int i) {
            return (Long) args[i];
        }

        /** @param i номер аргумента @return скаляр как {@code float} */
        public float floatArg(int i) {
            return Float.intBitsToFloat(intArg(i));
        }

        /** @param i номер аргумента @return скаляр как {@code double} */
        public double doubleArg(int i) {
            return Double.longBitsToDouble(longArg(i));
        }
    }

    /** Поведение «ядра». */
    @FunctionalInterface
    public interface KernelBody {
        /** @param launch параметры запуска */
        void run(Launch launch);
    }

    private final List<Spec> specs;
    private final Map<Long, byte[]> bufferData = new HashMap<>();
    private final Map<Long, String> programs = new HashMap<>();
    private final Map<Long, String[]> kernels = new HashMap<>(); // {имя, программа}
    private final Map<Long, Object[]> kernelArgs = new HashMap<>();
    private final Map<String, KernelBody> bodies = new HashMap<>();
    private final Map<String, Integer> failures = new HashMap<>();
    private final List<String> journal = new ArrayList<>();
    private final List<String> sources = new ArrayList<>();
    private final List<String> buildOptions = new ArrayList<>();
    private Predicate<String> rejectWhen = s -> false;
    private String rejectLog = "";
    private String successLog = "";
    private long next = 0x1000;
    private int liveContexts;
    private int liveQueues;
    private int livePrograms;
    private int liveKernels;
    private boolean closed;
    private boolean journaling = true;
    private boolean binariesSupported = true;
    private boolean rejectBinaries;
    private int binaryLoads;
    private int builds;
    private int enqueues;
    private int writes;
    private int reads;

    /** @param specs устройства; каждое - на своей платформе, порядок сохраняется */
    public FakeDeviceApi(Spec... specs) {
        this.specs = List.of(specs);
    }

    /** @return фейк с одним GPU */
    public static FakeDeviceApi withGpu() {
        return new FakeDeviceApi(Spec.gpu());
    }

    // ---- настройка теста ----------------------------------------------------------------------

    /**
     * @param entry имя точки входа
     * @param body  что делает «ядро»
     * @return этот фейк
     */
    public FakeDeviceApi onKernel(String entry, KernelBody body) {
        bodies.put(entry, body);
        return this;
    }

    /**
     * Сборка завершается ошибкой, если исходник удовлетворяет условию.
     *
     * @param when условие по тексту исходника
     * @param log  build log драйвера
     * @return этот фейк
     */
    public FakeDeviceApi rejectBuildWhen(Predicate<String> when, String log) {
        this.rejectWhen = when;
        this.rejectLog = log;
        return this;
    }

    /**
     * @param log build log, возвращаемый при успешной сборке (предупреждения)
     * @return этот фейк
     */
    public FakeDeviceApi successLog(String log) {
        this.successLog = log;
        return this;
    }

    /**
     * Следующий вызов функции завершится ошибкой драйвера.
     *
     * @param function имя функции {@link DeviceApi} (например {@code "enqueue"})
     * @param status   код ошибки
     * @return этот фейк
     */
    public FakeDeviceApi failNext(String function, int status) {
        failures.put(function, status);
        return this;
    }

    /**
     * Выключает журнал вызовов (счётчики остаются): в бенчмарках миллионы вызовов, и журнал занял бы всю память.
     *
     * @return этот фейк
     */
    public FakeDeviceApi withoutJournal() {
        journaling = false;
        return this;
    }

    /**
     * Драйвер не отдаёт бинарные образы программ (так ведёт себя часть реальных драйверов).
     *
     * @return этот фейк
     */
    public FakeDeviceApi withoutBinaries() {
        binariesSupported = false;
        return this;
    }

    /**
     * Драйвер отвергает любой бинарный образ (устаревший или повреждённый кэш).
     *
     * @return этот фейк
     */
    public FakeDeviceApi rejectingBinaries() {
        rejectBinaries = true;
        return this;
    }

    // ---- наблюдение ---------------------------------------------------------------------------

    /** @return число попыток создать программу из образа */
    public int binaryLoads() {
        return binaryLoads;
    }

    /** @return журнал вызовов в порядке выполнения: имя функции */
    public List<String> journal() {
        return journal;
    }

    /** @return исходники, переданные в {@code createProgram} */
    public List<String> sources() {
        return sources;
    }

    /** @return опции сборки */
    public List<String> buildOptions() {
        return buildOptions;
    }

    /** @return число вызовов {@code build} */
    public int builds() {
        return builds;
    }

    /** @return число постановок в очередь */
    public int enqueues() {
        return enqueues;
    }

    /** @return число записей в буферы */
    public int writes() {
        return writes;
    }

    /** @return число чтений из буферов */
    public int reads() {
        return reads;
    }

    /** @return живых буферов */
    public int liveBuffers() {
        return bufferData.size();
    }

    /** @return закреплённых промежуточных буферов, которые ещё не освобождены */
    public int livePinned() {
        return livePinned.size();
    }

    /** @return живых программ */
    public int livePrograms() {
        return livePrograms;
    }

    /** @return живых ядер */
    public int liveKernels() {
        return liveKernels;
    }

    /** @return живых очередей */
    public int liveQueues() {
        return liveQueues;
    }

    /** @return живых контекстов */
    public int liveContexts() {
        return liveContexts;
    }

    /** @return {@code true} после {@link #close()} */
    public boolean isClosed() {
        return closed;
    }

    /** @param buffer дескриптор буфера @return его содержимое (то же, что видит «устройство») */
    public ByteBuffer contents(long buffer) {
        return ByteBuffer.wrap(bufferData.get(buffer)).order(ByteOrder.LITTLE_ENDIAN);
    }

    // ---- реализация ---------------------------------------------------------------------------

    private void call(String function) {
        if (journaling) {
            journal.add(function);
        }
        Integer status = failures.isEmpty() ? null : failures.remove(function);
        if (status != null) {
            throw new DeviceApiException(status, function, "simulated failure");
        }
    }

    private long fresh() {
        return next += 0x10;
    }

    @Override
    public long[] platforms() {
        call("platforms");
        long[] out = new long[specs.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = 0x100 + i;
        }
        return out;
    }

    @Override
    public String platformInfo(long platform, int param) {
        call("platformInfo");
        Spec s = specs.get((int) (platform - 0x100));
        return switch (param) {
            case PLATFORM_NAME -> s.platform();
            case PLATFORM_VENDOR -> s.vendor();
            default -> "OpenCL 3.0 fake";
        };
    }

    @Override
    public long[] devices(long platform) {
        call("devices");
        return new long[]{0x200 + (platform - 0x100)};
    }

    private Spec spec(long device) {
        return specs.get((int) (device - 0x200));
    }

    @Override
    public String deviceString(long device, int param) {
        call("deviceString");
        Spec s = spec(device);
        return switch (param) {
            case DEVICE_NAME -> s.name();
            case DEVICE_VENDOR -> s.vendor();
            case DEVICE_EXTENSIONS -> s.extensions();
            case DEVICE_VERSION -> "OpenCL 3.0 fake";
            case DRIVER_VERSION -> "1.0-fake";
            default -> "";
        };
    }

    @Override
    public long deviceLong(long device, int param) {
        call("deviceLong");
        Spec s = spec(device);
        return switch (param) {
            case DEVICE_TYPE -> s.type();
            case DEVICE_GLOBAL_MEM_SIZE -> s.memory();
            case DEVICE_MAX_MEM_ALLOC_SIZE -> s.memory() / 4;
            case DEVICE_MAX_COMPUTE_UNITS -> 8;
            case DEVICE_MAX_WORK_GROUP_SIZE -> s.maxGroup();
            case DEVICE_LOCAL_MEM_SIZE -> 48 * 1024;
            case DEVICE_MAX_CONSTANT_BUFFER_SIZE -> s.maxConstant();
            case DEVICE_MAX_CONSTANT_ARGS -> 8;
            default -> 0;
        };
    }

    @Override
    public long createContext(long device) {
        call("createContext");
        liveContexts++;
        return fresh();
    }

    @Override
    public long createQueue(long context, long device) {
        call("createQueue");
        liveQueues++;
        return fresh();
    }

    private final Map<Long, java.lang.foreign.Arena> livePinned = new java.util.HashMap<>();

    /** Закреплённая память фейка - обычная нативная память; отличается только учётом выделений. */
    @Override
    public Pinned createPinned(long context, long queue, long size) {
        call("createPinned");
        long h = fresh();
        java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofShared();
        livePinned.put(h, arena);
        return new Pinned(h, arena.allocate(Math.max(size, 8), 8));
    }

    @Override
    public void releasePinned(long queue, Pinned pinned) {
        call("releasePinned");
        java.lang.foreign.Arena arena = livePinned.remove(pinned.buffer());
        if (arena == null) {
            throw new IllegalStateException("pinned buffer released twice or never created");
        }
        arena.close();
    }

    @Override
    public long createBuffer(long context, long size) {
        call("createBuffer");
        long h = fresh();
        bufferData.put(h, new byte[(int) size]);
        return h;
    }

    @Override
    public void writeBuffer(long queue, long buffer, long offset, long size, MemorySegment src) {
        call("writeBuffer");
        writes++;
        MemorySegment.copy(src, ValueLayout.JAVA_BYTE, 0, bufferData.get(buffer), (int) offset, (int) size);
    }

    @Override
    public void readBuffer(long queue, long buffer, long offset, long size, MemorySegment dst) {
        call("readBuffer");
        reads++;
        MemorySegment.copy(bufferData.get(buffer), (int) offset, dst, ValueLayout.JAVA_BYTE, 0, (int) size);
    }

    @Override
    public long createProgram(long context, String source) {
        call("createProgram");
        long h = fresh();
        programs.put(h, source);
        sources.add(source);
        livePrograms++;
        return h;
    }

    /** Образ фейка: заголовок и исходник программы. */
    private static final byte[] BINARY_MAGIC = "FAKEBIN\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Override
    public long createProgramWithBinary(long context, long device, byte[] binary) {
        call("createProgramWithBinary");
        binaryLoads++;
        if (rejectBinaries || binary.length < BINARY_MAGIC.length
                || !java.util.Arrays.equals(java.util.Arrays.copyOf(binary, BINARY_MAGIC.length), BINARY_MAGIC)) {
            throw new DeviceApiException(-42, "clCreateProgramWithBinary", "image not accepted");
        }
        long h = fresh();
        programs.put(h, new String(binary, BINARY_MAGIC.length, binary.length - BINARY_MAGIC.length,
                java.nio.charset.StandardCharsets.UTF_8));
        livePrograms++;
        return h;
    }

    @Override
    public byte[] programBinary(long program) {
        call("programBinary");
        if (!binariesSupported) {
            return null;
        }
        byte[] src = programs.get(program).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] out = java.util.Arrays.copyOf(BINARY_MAGIC, BINARY_MAGIC.length + src.length);
        System.arraycopy(src, 0, out, BINARY_MAGIC.length, src.length);
        return out;
    }

    @Override
    public BuildOutcome build(long program, long device, String options) {
        call("build");
        builds++;
        buildOptions.add(options);
        if (rejectWhen.test(programs.get(program))) {
            return new BuildOutcome(-11, rejectLog);
        }
        return new BuildOutcome(0, successLog);
    }

    @Override
    public long createKernel(long program, String name) {
        call("createKernel");
        long h = fresh();
        kernels.put(h, new String[]{name});
        kernelArgs.put(h, new Object[64]);
        liveKernels++;
        return h;
    }

    @Override
    public long kernelMaxWorkGroupSize(long kernel, long device) {
        call("kernelMaxWorkGroupSize");
        return spec(device).maxGroup();
    }

    @Override
    public void setArgBuffer(long kernel, int index, long buffer) {
        call("setArgBuffer");
        kernelArgs.get(kernel)[index] = buffer;
    }

    @Override
    public void setArgScalar(long kernel, int index, int size, long bits) {
        call("setArgScalar");
        kernelArgs.get(kernel)[index] = size == 4 ? (long) (int) bits : bits;
    }

    @Override
    public void setArgBytes(long kernel, int index, MemorySegment value, long size) {
        call("setArgBytes");
        byte[] copy = new byte[(int) size];
        MemorySegment.copy(value, ValueLayout.JAVA_BYTE, 0, copy, 0, (int) size);
        kernelArgs.get(kernel)[index] = copy;
    }

    @Override
    public void enqueue(long queue, long kernel, int dim, long[] global, long[] local) {
        call("enqueue");
        enqueues++;
        String name = kernels.get(kernel)[0];
        KernelBody body = bodies.get(name);
        if (body == null) {
            throw new DeviceApiException(-48, "enqueue", "the fake has no behavior for kernel " + name);
        }
        body.run(new Launch(this, kernelArgs.get(kernel).clone(), dim, global.clone(), local == null ? null : local.clone()));
    }

    @Override
    public void finish(long queue) {
        call("finish");
        finishes++;
    }

    private int finishes;

    /** @return сколько раз ждали очередь ({@code clFinish}) */
    public int finishes() {
        return finishes;
    }

    @Override
    public void release(HandleKind kind, long handle) {
        journal.add("release:" + kind);
        switch (kind) {
            case CONTEXT -> liveContexts--;
            case QUEUE -> liveQueues--;
            case BUFFER -> bufferData.remove(handle);
            case PROGRAM -> {
                programs.remove(handle);
                livePrograms--;
            }
            case KERNEL -> {
                kernels.remove(handle);
                kernelArgs.remove(handle);
                liveKernels--;
            }
        }
    }

    @Override
    public void close() {
        closed = true;
    }
}
