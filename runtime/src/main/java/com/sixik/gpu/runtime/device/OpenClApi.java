package com.sixik.gpu.runtime.device;

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
 * {@link DeviceApi} поверх {@code libOpenCL} через FFM ({@code java.lang.foreign}); сторонних нативных зависимостей
 * нет (REWORK_ARCHITECTURE.md, 9.2).
 *
 * <p>Библиотека ищется по имени платформы ({@code OpenCL.dll}, {@code libOpenCL.so.1}, фреймворк macOS).
 * Её отсутствие - не ошибка: {@link #load()} возвращает {@code null}, то есть «устройств нет», и старт приложения не
 * падает. Для вызова нативного кода JVM нужно запускать с {@code --enable-native-access=ALL-UNNAMED}.</p>
 *
 * <p>Используется очередь {@code clCreateCommandQueue} (OpenCL 1.x): она экспортируется и драйверами 2.x/3.0, а
 * v1 не нуждается в свойствах очереди из 2.0.</p>
 */
@SuppressWarnings("restricted") // FFM-вызовы нативного кода - суть класса; доступ включает --enable-native-access
public final class OpenClApi implements DeviceApi {

    private static final List<String> LIBRARIES = List.of(
            "OpenCL.dll", "libOpenCL.so.1", "libOpenCL.so", "/System/Library/Frameworks/OpenCL.framework/OpenCL");

    private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT;
    private static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG;
    private static final AddressLayout PTR = ValueLayout.ADDRESS;

    private final SymbolLookup lib;
    private final Linker linker = Linker.nativeLinker();
    private boolean closed;

    private final MethodHandle getPlatformIds;
    private final MethodHandle getPlatformInfo;
    private final MethodHandle getDeviceIds;
    private final MethodHandle getDeviceInfo;
    private final MethodHandle createContext;
    private final MethodHandle createQueue;
    private final MethodHandle createBuffer;
    private final MethodHandle writeBuffer;
    private final MethodHandle readBuffer;
    private final MethodHandle createProgram;
    private final MethodHandle buildProgram;
    private final MethodHandle getBuildInfo;
    private final MethodHandle createKernel;
    private final MethodHandle getKernelWorkGroupInfo;
    private final MethodHandle setKernelArg;
    private final MethodHandle enqueueNdRange;
    private final MethodHandle finish;
    private final MethodHandle releaseContext;
    private final MethodHandle releaseQueue;
    private final MethodHandle releaseBuffer;
    private final MethodHandle releaseProgram;
    private final MethodHandle releaseKernel;

    private OpenClApi(SymbolLookup lib) {
        this.lib = lib;
        getPlatformIds = fn("clGetPlatformIDs", FunctionDescriptor.of(INT, INT, PTR, PTR));
        getPlatformInfo = fn("clGetPlatformInfo", FunctionDescriptor.of(INT, PTR, INT, LONG, PTR, PTR));
        getDeviceIds = fn("clGetDeviceIDs", FunctionDescriptor.of(INT, PTR, LONG, INT, PTR, PTR));
        getDeviceInfo = fn("clGetDeviceInfo", FunctionDescriptor.of(INT, PTR, INT, LONG, PTR, PTR));
        createContext = fn("clCreateContext", FunctionDescriptor.of(PTR, PTR, INT, PTR, PTR, PTR, PTR));
        createQueue = fn("clCreateCommandQueue", FunctionDescriptor.of(PTR, PTR, PTR, LONG, PTR));
        createBuffer = fn("clCreateBuffer", FunctionDescriptor.of(PTR, PTR, LONG, LONG, PTR, PTR));
        writeBuffer = fn("clEnqueueWriteBuffer", FunctionDescriptor.of(INT, PTR, PTR, INT, LONG, LONG, PTR, INT, PTR, PTR));
        readBuffer = fn("clEnqueueReadBuffer", FunctionDescriptor.of(INT, PTR, PTR, INT, LONG, LONG, PTR, INT, PTR, PTR));
        createProgram = fn("clCreateProgramWithSource", FunctionDescriptor.of(PTR, PTR, INT, PTR, PTR, PTR));
        buildProgram = fn("clBuildProgram", FunctionDescriptor.of(INT, PTR, INT, PTR, PTR, PTR, PTR));
        getBuildInfo = fn("clGetProgramBuildInfo", FunctionDescriptor.of(INT, PTR, PTR, INT, LONG, PTR, PTR));
        createKernel = fn("clCreateKernel", FunctionDescriptor.of(PTR, PTR, PTR, PTR));
        getKernelWorkGroupInfo = fn("clGetKernelWorkGroupInfo", FunctionDescriptor.of(INT, PTR, PTR, INT, LONG, PTR, PTR));
        setKernelArg = fn("clSetKernelArg", FunctionDescriptor.of(INT, PTR, INT, LONG, PTR));
        enqueueNdRange = fn("clEnqueueNDRangeKernel", FunctionDescriptor.of(INT, PTR, PTR, INT, PTR, PTR, PTR, INT, PTR, PTR));
        finish = fn("clFinish", FunctionDescriptor.of(INT, PTR));
        releaseContext = fn("clReleaseContext", FunctionDescriptor.of(INT, PTR));
        releaseQueue = fn("clReleaseCommandQueue", FunctionDescriptor.of(INT, PTR));
        releaseBuffer = fn("clReleaseMemObject", FunctionDescriptor.of(INT, PTR));
        releaseProgram = fn("clReleaseProgram", FunctionDescriptor.of(INT, PTR));
        releaseKernel = fn("clReleaseKernel", FunctionDescriptor.of(INT, PTR));
    }

    /**
     * Загружает системную библиотеку OpenCL.
     *
     * @return слой доступа либо {@code null}, если библиотеки нет или она не загружается (драйвера нет)
     */
    public static DeviceApi load() {
        for (String name : LIBRARIES) {
            try {
                return new OpenClApi(SymbolLookup.libraryLookup(name, Arena.global()));
            } catch (RuntimeException | UnsatisfiedLinkError e) {
                // пробуем следующее имя
            }
        }
        return null;
    }

    private MethodHandle fn(String symbol, FunctionDescriptor descriptor) {
        MemorySegment address = lib.find(symbol).orElseThrow(
                () -> new IllegalStateException("в библиотеке OpenCL нет функции " + symbol));
        return linker.downcallHandle(address, descriptor);
    }

    private static MemorySegment handle(long h) {
        return MemorySegment.ofAddress(h);
    }

    private static void check(int status, String function) {
        if (status != 0) {
            throw new DeviceApiException(status, function, "драйвер вернул ошибку");
        }
    }

    private static RuntimeException unexpected(String function, Throwable t) {
        if (t instanceof DeviceApiException e) {
            return e;
        }
        return new DeviceApiException(-1, function, "вызов не удался: " + t);
    }

    // ---- платформы и устройства -----------------------------------------------------------------

    @Override
    public long[] platforms() {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment count = a.allocate(4);
            int st = (int) getPlatformIds.invoke(0, MemorySegment.NULL, count);
            if (st != 0 || count.get(INT, 0) == 0) {
                return new long[0]; // -1001 (нет ICD-драйверов) тоже означает «платформ нет»
            }
            int n = count.get(INT, 0);
            MemorySegment ids = a.allocate(8L * n);
            check((int) getPlatformIds.invoke(n, ids, MemorySegment.NULL), "clGetPlatformIDs");
            return addresses(ids, n);
        } catch (Throwable t) {
            throw unexpected("clGetPlatformIDs", t);
        }
    }

    private static long[] addresses(MemorySegment ids, int n) {
        long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = ids.getAtIndex(PTR, i).address();
        }
        return out;
    }

    @Override
    public String platformInfo(long platform, int param) {
        return infoString(getPlatformInfo, platform, param, "clGetPlatformInfo");
    }

    @Override
    public long[] devices(long platform) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment count = a.allocate(4);
            int st = (int) getDeviceIds.invoke(handle(platform), 0xFFFFFFFFL, 0, MemorySegment.NULL, count);
            if (st != 0 || count.get(INT, 0) == 0) {
                return new long[0]; // -1: нет устройств на этой платформе
            }
            int n = count.get(INT, 0);
            MemorySegment ids = a.allocate(8L * n);
            check((int) getDeviceIds.invoke(handle(platform), 0xFFFFFFFFL, n, ids, MemorySegment.NULL), "clGetDeviceIDs");
            return addresses(ids, n);
        } catch (Throwable t) {
            throw unexpected("clGetDeviceIDs", t);
        }
    }

    @Override
    public String deviceString(long device, int param) {
        return infoString(getDeviceInfo, device, param, "clGetDeviceInfo");
    }

    private String infoString(MethodHandle query, long object, int param, String function) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment size = a.allocate(8);
            check((int) query.invoke(handle(object), param, 0L, MemorySegment.NULL, size), function);
            long n = size.get(LONG, 0);
            MemorySegment buf = a.allocate(Math.max(n, 1));
            check((int) query.invoke(handle(object), param, n, buf, MemorySegment.NULL), function);
            return buf.getString(0, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            throw unexpected(function, t);
        }
    }

    @Override
    public long deviceLong(long device, int param) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment v = a.allocate(8);
            check((int) getDeviceInfo.invoke(handle(device), param, 8L, v, MemorySegment.NULL), "clGetDeviceInfo");
            // cl_uint (4 байта) и cl_ulong/size_t (8 байт) читаются из обнулённой памяти одним чтением
            return isFourByte(param) ? v.get(INT, 0) & 0xFFFFFFFFL : v.get(LONG, 0);
        } catch (Throwable t) {
            throw unexpected("clGetDeviceInfo", t);
        }
    }

    private static boolean isFourByte(int param) {
        return param == DEVICE_MAX_COMPUTE_UNITS || param == DEVICE_MAX_CONSTANT_ARGS;
    }

    // ---- контекст и очередь ----------------------------------------------------------------------

    @Override
    public long createContext(long device) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment err = a.allocate(4);
            MemorySegment devices = a.allocate(8);
            devices.set(PTR, 0, handle(device));
            MemorySegment ctx = (MemorySegment) createContext.invoke(MemorySegment.NULL, 1, devices,
                    MemorySegment.NULL, MemorySegment.NULL, err);
            check(err.get(INT, 0), "clCreateContext");
            return ctx.address();
        } catch (Throwable t) {
            throw unexpected("clCreateContext", t);
        }
    }

    @Override
    public long createQueue(long context, long device) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment err = a.allocate(4);
            MemorySegment q = (MemorySegment) createQueue.invoke(handle(context), handle(device), 0L, err);
            check(err.get(INT, 0), "clCreateCommandQueue");
            return q.address();
        } catch (Throwable t) {
            throw unexpected("clCreateCommandQueue", t);
        }
    }

    // ---- буферы ----------------------------------------------------------------------------------

    @Override
    public long createBuffer(long context, long size) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment err = a.allocate(4);
            MemorySegment b = (MemorySegment) createBuffer.invoke(handle(context), 1L /* CL_MEM_READ_WRITE */, size,
                    MemorySegment.NULL, err);
            check(err.get(INT, 0), "clCreateBuffer");
            return b.address();
        } catch (Throwable t) {
            throw unexpected("clCreateBuffer", t);
        }
    }

    @Override
    public void writeBuffer(long queue, long buffer, long offset, long size, MemorySegment src) {
        try {
            check((int) writeBuffer.invoke(handle(queue), handle(buffer), 1, offset, size, src, 0, MemorySegment.NULL,
                    MemorySegment.NULL), "clEnqueueWriteBuffer");
        } catch (Throwable t) {
            throw unexpected("clEnqueueWriteBuffer", t);
        }
    }

    @Override
    public void readBuffer(long queue, long buffer, long offset, long size, MemorySegment dst) {
        try {
            check((int) readBuffer.invoke(handle(queue), handle(buffer), 1, offset, size, dst, 0, MemorySegment.NULL,
                    MemorySegment.NULL), "clEnqueueReadBuffer");
        } catch (Throwable t) {
            throw unexpected("clEnqueueReadBuffer", t);
        }
    }

    // ---- программа и ядро ------------------------------------------------------------------------

    @Override
    public long createProgram(long context, String source) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment err = a.allocate(4);
            byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
            MemorySegment text = a.allocate(Math.max(bytes.length, 1));
            MemorySegment.copy(bytes, 0, text, ValueLayout.JAVA_BYTE, 0, bytes.length);
            MemorySegment strings = a.allocate(8);
            strings.set(PTR, 0, text);
            MemorySegment lengths = a.allocate(8);
            lengths.set(LONG, 0, bytes.length);
            MemorySegment p = (MemorySegment) createProgram.invoke(handle(context), 1, strings, lengths, err);
            check(err.get(INT, 0), "clCreateProgramWithSource");
            return p.address();
        } catch (Throwable t) {
            throw unexpected("clCreateProgramWithSource", t);
        }
    }

    @Override
    public BuildOutcome build(long program, long device, String options) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment devices = a.allocate(8);
            devices.set(PTR, 0, handle(device));
            int st = (int) buildProgram.invoke(handle(program), 1, devices, a.allocateFrom(options),
                    MemorySegment.NULL, MemorySegment.NULL);
            return new BuildOutcome(st, buildLog(a, program, device));
        } catch (Throwable t) {
            throw unexpected("clBuildProgram", t);
        }
    }

    private String buildLog(Arena a, long program, long device) throws Throwable {
        MemorySegment size = a.allocate(8);
        int st = (int) getBuildInfo.invoke(handle(program), handle(device), 0x1183 /* CL_PROGRAM_BUILD_LOG */, 0L,
                MemorySegment.NULL, size);
        long n = size.get(LONG, 0);
        if (st != 0 || n <= 1) {
            return "";
        }
        MemorySegment buf = a.allocate(n);
        st = (int) getBuildInfo.invoke(handle(program), handle(device), 0x1183, n, buf, MemorySegment.NULL);
        return st == 0 ? buf.getString(0, StandardCharsets.UTF_8) : "";
    }

    @Override
    public long createKernel(long program, String name) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment err = a.allocate(4);
            MemorySegment k = (MemorySegment) createKernel.invoke(handle(program), a.allocateFrom(name), err);
            check(err.get(INT, 0), "clCreateKernel");
            return k.address();
        } catch (Throwable t) {
            throw unexpected("clCreateKernel", t);
        }
    }

    @Override
    public long kernelMaxWorkGroupSize(long kernel, long device) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment v = a.allocate(8);
            check((int) getKernelWorkGroupInfo.invoke(handle(kernel), handle(device), 0x11B0 /* WORK_GROUP_SIZE */, 8L,
                    v, MemorySegment.NULL), "clGetKernelWorkGroupInfo");
            return v.get(LONG, 0);
        } catch (Throwable t) {
            throw unexpected("clGetKernelWorkGroupInfo", t);
        }
    }

    // ---- аргументы и запуск ----------------------------------------------------------------------

    @Override
    public void setArgBuffer(long kernel, int index, long buffer) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment slot = a.allocate(8);
            slot.set(PTR, 0, handle(buffer));
            check((int) setKernelArg.invoke(handle(kernel), index, 8L, slot), "clSetKernelArg");
        } catch (Throwable t) {
            throw unexpected("clSetKernelArg", t);
        }
    }

    @Override
    public void setArgScalar(long kernel, int index, int size, long bits) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment slot = a.allocate(8);
            if (size == 4) {
                slot.set(INT, 0, (int) bits);
            } else {
                slot.set(LONG, 0, bits);
            }
            check((int) setKernelArg.invoke(handle(kernel), index, (long) size, slot), "clSetKernelArg");
        } catch (Throwable t) {
            throw unexpected("clSetKernelArg", t);
        }
    }

    @Override
    public void setArgBytes(long kernel, int index, MemorySegment value, long size) {
        try {
            check((int) setKernelArg.invoke(handle(kernel), index, size, value), "clSetKernelArg");
        } catch (Throwable t) {
            throw unexpected("clSetKernelArg", t);
        }
    }

    @Override
    public void enqueue(long queue, long kernel, int dim, long[] global, long[] local) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment gs = a.allocate(8L * dim);
            MemorySegment ls = local == null ? MemorySegment.NULL : a.allocate(8L * dim);
            for (int d = 0; d < dim; d++) {
                gs.setAtIndex(LONG, d, global[d]);
                if (local != null) {
                    ls.setAtIndex(LONG, d, local[d]);
                }
            }
            check((int) enqueueNdRange.invoke(handle(queue), handle(kernel), dim, MemorySegment.NULL, gs, ls, 0,
                    MemorySegment.NULL, MemorySegment.NULL), "clEnqueueNDRangeKernel");
        } catch (Throwable t) {
            throw unexpected("clEnqueueNDRangeKernel", t);
        }
    }

    @Override
    public void finish(long queue) {
        try {
            check((int) finish.invoke(handle(queue)), "clFinish");
        } catch (Throwable t) {
            throw unexpected("clFinish", t);
        }
    }

    // ---- освобождение ----------------------------------------------------------------------------

    @Override
    public void release(HandleKind kind, long handle) {
        try {
            MethodHandle h = switch (kind) {
                case CONTEXT -> releaseContext;
                case QUEUE -> releaseQueue;
                case BUFFER -> releaseBuffer;
                case PROGRAM -> releaseProgram;
                case KERNEL -> releaseKernel;
            };
            int ignored = (int) h.invoke(handle(handle));
        } catch (Throwable ignored) {
            // при освобождении ошибки не пробрасываются
        }
    }

    @Override
    public void close() {
        closed = true;
    }

    /** @return {@code true} после {@link #close()} */
    public boolean isClosed() {
        return closed;
    }
}
