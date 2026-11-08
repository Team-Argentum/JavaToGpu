package com.sixik.gpu.codegen.opencl;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Минимальная обвязка над {@code libOpenCL} через FFM - <b>только для тестов кодогенератора</b>: компилирует
 * сгенерированный C реальным драйвером и запускает ядро.
 *
 * <p>Это не {@code DeviceApi} из M6 (там будут контекст, кэш, события, разбор build log, потокобезопасность):
 * здесь одно устройство, синхронный запуск, ошибка - исключение. Нужна, чтобы критерий M5 «сгенерированный C
 * компилируется драйвером» и сверка результатов с {@code ir-interpreter} проверялись уже сейчас.</p>
 */
@SuppressWarnings("restricted") // FFM-вызовы нативного кода - суть этого тестового класса (доступ включён в build.gradle)
final class ClDevice implements AutoCloseable {

    private static final int CL_DEVICE_TYPE_GPU = 4;
    private static final int CL_DEVICE_TYPE_ALL = 0xFFFFFFFF;
    private static final int CL_DEVICE_NAME = 0x102B;
    private static final int CL_PROGRAM_BUILD_LOG = 0x1183;
    private static final int CL_MEM_READ_WRITE = 1;

    /** Ошибка компиляции или запуска. */
    static final class ClException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ClException(String message) {
            super(message);
        }
    }

    private final Arena arena = Arena.ofShared();
    private final Linker linker = Linker.nativeLinker();
    private final SymbolLookup lib;
    private MemorySegment device;
    private MemorySegment context;
    private MemorySegment queue;
    private String name;

    private ClDevice(SymbolLookup lib) {
        this.lib = lib;
    }

    /**
     * @return открытое устройство (предпочитается GPU) либо {@code null}, если драйвера или устройств нет
     */
    static ClDevice open() {
        try {
            SymbolLookup lookup = SymbolLookup.libraryLookup(
                    System.getProperty("os.name").toLowerCase().contains("win") ? "OpenCL.dll" : "libOpenCL.so.1",
                    Arena.global());
            ClDevice d = new ClDevice(lookup);
            return d.init() ? d : null;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return null;
        }
    }

    /** @return имя устройства */
    String name() {
        return name;
    }

    private MethodHandle fn(String symbol, FunctionDescriptor fd) {
        return linker.downcallHandle(lib.find(symbol).orElseThrow(() -> new ClException("нет " + symbol)), fd);
    }

    private static final ValueLayout.OfInt I = ValueLayout.JAVA_INT;
    private static final ValueLayout.OfLong L = ValueLayout.JAVA_LONG;
    private static final AddressLayout A = ValueLayout.ADDRESS;

    private boolean init() {
        try {
            MemorySegment num = arena.allocate(4);
            MemorySegment platforms = arena.allocate(8 * 16);
            int st = (int) fn("clGetPlatformIDs", FunctionDescriptor.of(I, I, A, A)).invoke(16, platforms, num);
            int np = num.get(I, 0);
            if (st != 0 || np == 0) {
                return false;
            }
            MethodHandle getDevices = fn("clGetDeviceIDs", FunctionDescriptor.of(I, A, L, I, A, A));
            for (int type : new int[]{CL_DEVICE_TYPE_GPU, CL_DEVICE_TYPE_ALL}) {
                for (int p = 0; p < np && device == null; p++) {
                    MemorySegment devs = arena.allocate(8 * 16);
                    MemorySegment platform = platforms.getAtIndex(A, p);
                    int r = (int) getDevices.invoke(platform, (long) type & 0xFFFFFFFFL, 16, devs, num);
                    if (r == 0 && num.get(I, 0) > 0) {
                        device = devs.getAtIndex(A, 0);
                    }
                }
                if (device != null) {
                    break;
                }
            }
            if (device == null) {
                return false;
            }
            MemorySegment buf = arena.allocate(256);
            MemorySegment ret = arena.allocate(8);
            fn("clGetDeviceInfo", FunctionDescriptor.of(I, A, I, L, A, A)).invoke(device, CL_DEVICE_NAME, 256L, buf, ret);
            name = buf.getString(0, StandardCharsets.UTF_8);
            MemorySegment err = arena.allocate(4);
            MemorySegment devices = arena.allocate(8);
            devices.set(A, 0, device);
            context = (MemorySegment) fn("clCreateContext", FunctionDescriptor.of(A, A, I, A, A, A, A))
                    .invoke(MemorySegment.NULL, 1, devices, MemorySegment.NULL, MemorySegment.NULL, err);
            check(err.get(I, 0), "clCreateContext");
            queue = (MemorySegment) fn("clCreateCommandQueue", FunctionDescriptor.of(A, A, A, L, A))
                    .invoke(context, device, 0L, err);
            check(err.get(I, 0), "clCreateCommandQueue");
            return true;
        } catch (ClException e) {
            return false;
        } catch (Throwable t) {
            throw new ClException("init: " + t);
        }
    }

    private static void check(int status, String what) {
        if (status != 0) {
            throw new ClException(what + " вернул " + status);
        }
    }

    /** Скомпилированная программа. */
    final class Program implements AutoCloseable {
        private final MemorySegment program;

        private Program(MemorySegment program) {
            this.program = program;
        }

        @Override
        public void close() {
            try {
                fn("clReleaseProgram", FunctionDescriptor.of(I, A)).invoke(program);
            } catch (Throwable ignored) {
                // освобождение в тесте не критично
            }
        }
    }

    /**
     * Компилирует исходник.
     *
     * @param source  OpenCL C
     * @param options опции {@code clBuildProgram}
     * @return программа
     * @throws ClException если драйвер отверг исходник (в сообщении - build log)
     */
    Program build(String source, String options) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment err = a.allocate(4);
            MemorySegment text = a.allocateFrom(source, StandardCharsets.UTF_8);
            MemorySegment strings = a.allocate(8);
            strings.set(A, 0, text);
            MemorySegment lengths = a.allocate(8);
            lengths.set(L, 0, source.getBytes(StandardCharsets.UTF_8).length);
            MemorySegment program = (MemorySegment) fn("clCreateProgramWithSource", FunctionDescriptor.of(A, A, I, A, A, A))
                    .invoke(context, 1, strings, lengths, err);
            check(err.get(I, 0), "clCreateProgramWithSource");
            MemorySegment devices = a.allocate(8);
            devices.set(A, 0, device);
            int st = (int) fn("clBuildProgram", FunctionDescriptor.of(I, A, I, A, A, A, A))
                    .invoke(program, 1, devices, a.allocateFrom(options), MemorySegment.NULL, MemorySegment.NULL);
            if (st != 0) {
                MemorySegment log = a.allocate(1 << 16);
                MemorySegment size = a.allocate(8);
                fn("clGetProgramBuildInfo", FunctionDescriptor.of(I, A, A, I, L, A, A))
                        .invoke(program, device, CL_PROGRAM_BUILD_LOG, (long) (1 << 16), log, size);
                throw new ClException("clBuildProgram вернул " + st + ":\n" + log.getString(0, StandardCharsets.UTF_8));
            }
            return new Program(program);
        } catch (ClException e) {
            throw e;
        } catch (Throwable t) {
            throw new ClException("build: " + t);
        }
    }

    /**
     * Запускает ядро: массивы копируются на устройство, после выполнения читаются обратно в те же Java-массивы.
     *
     * @param program программа
     * @param artifact результат кодогенерации (имя точки входа и раскрытая сигнатура)
     * @param host     значения параметров ядра по параметрам IR (Java-массивы и {@code Integer/Long/Float/Double})
     * @param global   размеры NDRange (1-3 измерения)
     * @param local    размеры work-group либо {@code null}
     */
    void run(Program program, Artifact artifact, Object[] host, long[] global, long[] local) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment err = a.allocate(4);
            MemorySegment kernel = (MemorySegment) fn("clCreateKernel", FunctionDescriptor.of(A, A, A, A))
                    .invoke(program.program, a.allocateFrom(artifact.entry()), err);
            check(err.get(I, 0), "clCreateKernel " + artifact.entry());
            MethodHandle setArg = fn("clSetKernelArg", FunctionDescriptor.of(I, A, I, L, A));
            MethodHandle createBuffer = fn("clCreateBuffer", FunctionDescriptor.of(A, A, L, L, A, A));
            MethodHandle write = fn("clEnqueueWriteBuffer", FunctionDescriptor.of(I, A, A, I, L, L, A, I, A, A));
            MemorySegment[] buffers = new MemorySegment[host.length];
            List<KernelArg> args = artifact.args();
            for (int i = 0; i < args.size(); i++) {
                KernelArg ka = args.get(i);
                Object value = host[ka.param()];
                MemorySegment slot = a.allocate(8);
                int size;
                switch (ka.kind()) {
                    case KernelArg.ARRAY_PTR -> {
                        MemorySegment bytes = toNative(a, value);
                        long len = Math.max(bytes.byteSize(), 4);
                        MemorySegment buf = (MemorySegment) createBuffer.invoke(context, (long) CL_MEM_READ_WRITE, len,
                                MemorySegment.NULL, err);
                        check(err.get(I, 0), "clCreateBuffer");
                        check((int) write.invoke(queue, buf, 1, 0L, bytes.byteSize(), bytes, 0, MemorySegment.NULL,
                                MemorySegment.NULL), "clEnqueueWriteBuffer");
                        buffers[ka.param()] = buf;
                        slot.set(A, 0, buf);
                        size = 8;
                    }
                    case KernelArg.ARRAY_LEN -> {
                        slot.set(I, 0, length(value));
                        size = 4;
                    }
                    default -> {
                        size = scalar(slot, value);
                    }
                }
                check((int) setArg.invoke(kernel, i, (long) size, slot), "clSetKernelArg " + ka.name());
            }
            MemorySegment gs = a.allocate(8L * global.length);
            MemorySegment ls = local == null ? MemorySegment.NULL : a.allocate(8L * local.length);
            for (int d = 0; d < global.length; d++) {
                gs.setAtIndex(L, d, global[d]);
                if (local != null) {
                    ls.setAtIndex(L, d, local[d]);
                }
            }
            check((int) fn("clEnqueueNDRangeKernel", FunctionDescriptor.of(I, A, A, I, A, A, A, I, A, A))
                    .invoke(queue, kernel, global.length, MemorySegment.NULL, gs, ls, 0, MemorySegment.NULL,
                            MemorySegment.NULL), "clEnqueueNDRangeKernel");
            check((int) fn("clFinish", FunctionDescriptor.of(I, A)).invoke(queue), "clFinish");
            MethodHandle read = fn("clEnqueueReadBuffer", FunctionDescriptor.of(I, A, A, I, L, L, A, I, A, A));
            for (int p = 0; p < host.length; p++) {
                if (buffers[p] != null) {
                    long bytes = byteSize(host[p]);
                    MemorySegment out = a.allocate(Math.max(bytes, 4));
                    check((int) read.invoke(queue, buffers[p], 1, 0L, bytes, out, 0, MemorySegment.NULL,
                            MemorySegment.NULL), "clEnqueueReadBuffer");
                    fromNative(out, host[p]);
                    fn("clReleaseMemObject", FunctionDescriptor.of(I, A)).invoke(buffers[p]);
                }
            }
            fn("clReleaseKernel", FunctionDescriptor.of(I, A)).invoke(kernel);
        } catch (ClException e) {
            throw e;
        } catch (Throwable t) {
            throw new ClException("run: " + t);
        }
    }

    // ---- упаковка Java-массивов ---------------------------------------------------------------

    private static int length(Object arr) {
        return java.lang.reflect.Array.getLength(arr);
    }

    private static long byteSize(Object arr) {
        return switch (arr) {
            case byte[] x -> x.length;
            case boolean[] x -> x.length;
            case short[] x -> 2L * x.length;
            case char[] x -> 2L * x.length;
            case int[] x -> 4L * x.length;
            case float[] x -> 4L * x.length;
            case long[] x -> 8L * x.length;
            case double[] x -> 8L * x.length;
            default -> throw new IllegalArgumentException("неподдержанный массив " + arr.getClass());
        };
    }

    private static MemorySegment toNative(Arena a, Object arr) {
        MemorySegment seg = a.allocate(Math.max(byteSize(arr), 4));
        switch (arr) {
            case byte[] x -> MemorySegment.copy(x, 0, seg, ValueLayout.JAVA_BYTE, 0, x.length);
            case boolean[] x -> {
                for (int i = 0; i < x.length; i++) {
                    seg.set(ValueLayout.JAVA_BYTE, i, (byte) (x[i] ? 1 : 0));
                }
            }
            case short[] x -> MemorySegment.copy(x, 0, seg, ValueLayout.JAVA_SHORT, 0, x.length);
            case char[] x -> MemorySegment.copy(x, 0, seg, ValueLayout.JAVA_CHAR, 0, x.length);
            case int[] x -> MemorySegment.copy(x, 0, seg, ValueLayout.JAVA_INT, 0, x.length);
            case float[] x -> MemorySegment.copy(x, 0, seg, ValueLayout.JAVA_FLOAT, 0, x.length);
            case long[] x -> MemorySegment.copy(x, 0, seg, ValueLayout.JAVA_LONG, 0, x.length);
            case double[] x -> MemorySegment.copy(x, 0, seg, ValueLayout.JAVA_DOUBLE, 0, x.length);
            default -> throw new IllegalArgumentException("неподдержанный массив " + arr.getClass());
        }
        return seg.asSlice(0, byteSize(arr));
    }

    private static void fromNative(MemorySegment seg, Object arr) {
        switch (arr) {
            case byte[] x -> MemorySegment.copy(seg, ValueLayout.JAVA_BYTE, 0, x, 0, x.length);
            case boolean[] x -> {
                for (int i = 0; i < x.length; i++) {
                    x[i] = seg.get(ValueLayout.JAVA_BYTE, i) != 0;
                }
            }
            case short[] x -> MemorySegment.copy(seg, ValueLayout.JAVA_SHORT, 0, x, 0, x.length);
            case char[] x -> MemorySegment.copy(seg, ValueLayout.JAVA_CHAR, 0, x, 0, x.length);
            case int[] x -> MemorySegment.copy(seg, ValueLayout.JAVA_INT, 0, x, 0, x.length);
            case float[] x -> MemorySegment.copy(seg, ValueLayout.JAVA_FLOAT, 0, x, 0, x.length);
            case long[] x -> MemorySegment.copy(seg, ValueLayout.JAVA_LONG, 0, x, 0, x.length);
            case double[] x -> MemorySegment.copy(seg, ValueLayout.JAVA_DOUBLE, 0, x, 0, x.length);
            default -> throw new IllegalArgumentException("неподдержанный массив " + arr.getClass());
        }
    }

    private static int scalar(MemorySegment slot, Object v) {
        switch (v) {
            case Integer x -> {
                slot.set(I, 0, x);
                return 4;
            }
            case Long x -> {
                slot.set(L, 0, x);
                return 8;
            }
            case Float x -> {
                slot.set(ValueLayout.JAVA_FLOAT, 0, x);
                return 4;
            }
            case Double x -> {
                slot.set(ValueLayout.JAVA_DOUBLE, 0, x);
                return 8;
            }
            default -> throw new IllegalArgumentException("неподдержанный скаляр " + v.getClass());
        }
    }

    @Override
    public void close() {
        try {
            fn("clReleaseCommandQueue", FunctionDescriptor.of(I, A)).invoke(queue);
            fn("clReleaseContext", FunctionDescriptor.of(I, A)).invoke(context);
        } catch (Throwable ignored) {
            // освобождение в тесте не критично
        }
        arena.close();
    }
}
