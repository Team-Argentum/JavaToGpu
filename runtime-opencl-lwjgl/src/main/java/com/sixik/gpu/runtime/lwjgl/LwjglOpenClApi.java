package com.sixik.gpu.runtime.lwjgl;

import com.sixik.gpu.runtime.device.AttachedMemory;
import com.sixik.gpu.runtime.device.DeviceApi;
import com.sixik.gpu.runtime.device.DeviceApiException;
import com.sixik.gpu.runtime.device.HostArena;
import com.sixik.gpu.runtime.device.HostMemory;
import org.lwjgl.opencl.CL;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * {@link DeviceApi} поверх {@code lwjgl-opencl} (Java 17, без FFM). Тот же список вызовов, что у FFM-бэкенда, через
 * «небезопасные» функции LWJGL ({@code ncl*}) с адресами: поведение и коды ошибок совпадают.
 *
 * <p>Ядро LWJGL ({@code org.lwjgl:lwjgl} с нативами) даёт приложение - например, Minecraft. Своих нативов у
 * {@code lwjgl-opencl} нет: функции берутся из системной библиотеки OpenCL (ICD). Если {@code CL} уже создан другим
 * кодом процесса, он переиспользуется и при {@link #close()} не уничтожается.</p>
 */
public final class LwjglOpenClApi implements DeviceApi {

    /**
     * Имена библиотеки на Linux: без пакета разработки там есть только {@code libOpenCL.so.1}, а имя LWJGL по умолчанию -
     * {@code libOpenCL.so}. На Windows и macOS имя по умолчанию верное.
     */
    private static final List<String> LINUX_LIBRARIES = List.of("libOpenCL.so.1", "libOpenCL.so");

    private boolean closed;

    private LwjglOpenClApi() {
    }

    /**
     * Загружает библиотеку OpenCL через LWJGL.
     *
     * @return слой доступа либо {@code null}, если библиотеки нет (драйвера нет)
     */
    public static DeviceApi load() {
        try {
            // до первого обращения к CL: иначе его статический инициализатор сам вызывает create() по имени по умолчанию
            // и при отсутствии библиотеки (на Linux часто есть только libOpenCL.so.1) ломает класс навсегда. Если CL уже
            // инициализирован другим кодом, флаг ни на что не влияет
            Configuration.OPENCL_EXPLICIT_INIT.set(true);
            if (!created()) {
                if (!create()) {
                    return null;
                }
            }
            return new LwjglOpenClApi();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static boolean created() {
        try {
            CL.getFunctionProvider();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static boolean create() {
        if (Platform.get() == Platform.LINUX) {
            for (String name : LINUX_LIBRARIES) {
                try {
                    CL.create(name);
                    return true;
                } catch (RuntimeException | LinkageError e) {
                    // следующее имя
                }
            }
            return false;
        }
        try {
            CL.create();
            return true;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static void check(int status, String function) {
        if (status != 0) {
            throw new DeviceApiException(status, function, "the driver returned an error");
        }
    }

    private static RuntimeException unexpected(String function, Throwable t) {
        if (t instanceof DeviceApiException e) {
            return e;
        }
        return new DeviceApiException(-1, function, "call failed: " + t);
    }

    // ---- память хоста ----------------------------------------------------------------------------

    @Override
    public HostArena arena(HostArena.Kind kind) {
        return new NativeMemory.Arena(kind);
    }

    /** @return нативный адрес памяти либо {@code 0}, если она в куче (тогда её нужно скопировать) */
    private static long nativeAddress(HostMemory m) {
        while (m instanceof AttachedMemory a) {
            m = a.unwrap();
        }
        return m.address();
    }

    /** Копия памяти кучи в новую нативную память; освобождает вызывающий ({@code nmemFree}). */
    private static long copyOf(HostMemory m, long size) {
        long p = MemoryUtil.nmemAllocChecked(Math.max(size, 1));
        byte[] chunk = new byte[(int) Math.min(size, 1 << 16)];
        for (long done = 0; done < size; ) {
            int n = (int) Math.min(chunk.length, size - done);
            m.copyTo(done, chunk, 0, n);
            MemoryUtil.memByteBuffer(p + done, n).put(chunk, 0, n);
            done += n;
        }
        return p;
    }

    // ---- платформы и устройства -----------------------------------------------------------------

    @Override
    public long[] platforms() {
        try (MemoryStack s = MemoryStack.stackPush()) {
            long count = s.ncalloc(4, 1, 4);
            int st = CL10.nclGetPlatformIDs(0, 0L, count);
            int n = MemoryUtil.memGetInt(count);
            if (st != 0 || n == 0) {
                return new long[0]; // -1001 (нет ICD-драйверов) тоже означает «платформ нет»
            }
            long ids = s.ncalloc(8, n, 8);
            check(CL10.nclGetPlatformIDs(n, ids, 0L), "clGetPlatformIDs");
            return addresses(ids, n);
        } catch (Throwable t) {
            throw unexpected("clGetPlatformIDs", t);
        }
    }

    private static long[] addresses(long ids, int n) {
        long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = MemoryUtil.memGetAddress(ids + 8L * i);
        }
        return out;
    }

    @Override
    public String platformInfo(long platform, int param) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            long size = s.ncalloc(8, 1, 8);
            check(CL10.nclGetPlatformInfo(platform, param, 0L, 0L, size), "clGetPlatformInfo");
            long n = MemoryUtil.memGetAddress(size);
            long buf = MemoryUtil.nmemCalloc(1, Math.max(n, 1));
            try {
                check(CL10.nclGetPlatformInfo(platform, param, n, buf, 0L), "clGetPlatformInfo");
                return string(buf, n);
            } finally {
                MemoryUtil.nmemFree(buf);
            }
        } catch (Throwable t) {
            throw unexpected("clGetPlatformInfo", t);
        }
    }

    /** Строка до первого {@code NUL} (или все {@code n} байт). */
    private static String string(long address, long n) {
        int len = 0;
        while (len < n && MemoryUtil.memGetByte(address + len) != 0) {
            len++;
        }
        byte[] b = new byte[len];
        MemoryUtil.memByteBuffer(address, len).get(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    @Override
    public long[] devices(long platform) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            long count = s.ncalloc(4, 1, 4);
            int st = CL10.nclGetDeviceIDs(platform, 0xFFFFFFFFL, 0, 0L, count);
            int n = MemoryUtil.memGetInt(count);
            if (st != 0 || n == 0) {
                return new long[0]; // -1: нет устройств на этой платформе
            }
            long ids = s.ncalloc(8, n, 8);
            check(CL10.nclGetDeviceIDs(platform, 0xFFFFFFFFL, n, ids, 0L), "clGetDeviceIDs");
            return addresses(ids, n);
        } catch (Throwable t) {
            throw unexpected("clGetDeviceIDs", t);
        }
    }

    @Override
    public String deviceString(long device, int param) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            long size = s.ncalloc(8, 1, 8);
            check(CL10.nclGetDeviceInfo(device, param, 0L, 0L, size), "clGetDeviceInfo");
            long n = MemoryUtil.memGetAddress(size);
            long buf = MemoryUtil.nmemCalloc(1, Math.max(n, 1));
            try {
                check(CL10.nclGetDeviceInfo(device, param, n, buf, 0L), "clGetDeviceInfo");
                return string(buf, n);
            } finally {
                MemoryUtil.nmemFree(buf);
            }
        } catch (Throwable t) {
            throw unexpected("clGetDeviceInfo", t);
        }
    }

    @Override
    public long deviceLong(long device, int param) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            long v = s.ncalloc(8, 1, 8);
            check(CL10.nclGetDeviceInfo(device, param, 8L, v, 0L), "clGetDeviceInfo");
            // cl_uint (4 байта) и cl_ulong/size_t (8 байт) читаются из обнулённой памяти одним чтением
            return isFourByte(param) ? MemoryUtil.memGetInt(v) & 0xFFFFFFFFL : MemoryUtil.memGetLong(v);
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
        try (MemoryStack s = MemoryStack.stackPush()) {
            long err = s.ncalloc(4, 1, 4);
            long devices = s.ncalloc(8, 1, 8);
            MemoryUtil.memPutAddress(devices, device);
            long ctx = CL10.nclCreateContext(0L, 1, devices, 0L, 0L, err);
            check(MemoryUtil.memGetInt(err), "clCreateContext");
            return ctx;
        } catch (Throwable t) {
            throw unexpected("clCreateContext", t);
        }
    }

    @Override
    public long createQueue(long context, long device) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            long err = s.ncalloc(4, 1, 4);
            long q = CL10.nclCreateCommandQueue(context, device, 0L, err);
            check(MemoryUtil.memGetInt(err), "clCreateCommandQueue");
            return q;
        } catch (Throwable t) {
            throw unexpected("clCreateCommandQueue", t);
        }
    }

    // ---- буферы ----------------------------------------------------------------------------------

    @Override
    public long createBuffer(long context, long size) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            long err = s.ncalloc(4, 1, 4);
            long b = CL10.nclCreateBuffer(context, 1L /* CL_MEM_READ_WRITE */, size, 0L, err);
            check(MemoryUtil.memGetInt(err), "clCreateBuffer");
            return b;
        } catch (Throwable t) {
            throw unexpected("clCreateBuffer", t);
        }
    }

    @Override
    public void writeBuffer(long queue, long buffer, long offset, long size, HostMemory src) {
        long p = nativeAddress(src);
        long copy = p == 0 ? copyOf(src, size) : 0;
        try {
            check(CL10.nclEnqueueWriteBuffer(queue, buffer, 1, offset, size, p == 0 ? copy : p, 0, 0L, 0L),
                    "clEnqueueWriteBuffer");
        } catch (Throwable t) {
            throw unexpected("clEnqueueWriteBuffer", t);
        } finally {
            if (copy != 0) {
                MemoryUtil.nmemFree(copy);
            }
        }
    }

    @Override
    public void writeBufferAsync(long queue, long buffer, long offset, long size, HostMemory src) {
        long p = nativeAddress(src);
        if (p == 0) {
            // временную копию нельзя освободить, пока запись не выполнена: такая запись - блокирующая
            writeBuffer(queue, buffer, offset, size, src);
            return;
        }
        try {
            check(CL10.nclEnqueueWriteBuffer(queue, buffer, 0 /* CL_FALSE */, offset, size, p, 0, 0L, 0L),
                    "clEnqueueWriteBuffer");
        } catch (Throwable t) {
            throw unexpected("clEnqueueWriteBuffer", t);
        }
    }

    @Override
    public void readBuffer(long queue, long buffer, long offset, long size, HostMemory dst) {
        long p = nativeAddress(dst);
        long tmp = p == 0 ? MemoryUtil.nmemAllocChecked(Math.max(size, 1)) : 0;
        try {
            check(CL10.nclEnqueueReadBuffer(queue, buffer, 1, offset, size, p == 0 ? tmp : p, 0, 0L, 0L),
                    "clEnqueueReadBuffer");
            if (tmp != 0) {
                byte[] chunk = new byte[(int) Math.min(size, 1 << 16)];
                for (long done = 0; done < size; ) {
                    int n = (int) Math.min(chunk.length, size - done);
                    MemoryUtil.memByteBuffer(tmp + done, n).get(chunk, 0, n);
                    dst.copyFrom(chunk, 0, done, n);
                    done += n;
                }
            }
        } catch (Throwable t) {
            throw unexpected("clEnqueueReadBuffer", t);
        } finally {
            if (tmp != 0) {
                MemoryUtil.nmemFree(tmp);
            }
        }
    }

    @Override
    public Pinned createPinned(long context, long queue, long size) {
        long buffer = 0;
        try (MemoryStack s = MemoryStack.stackPush()) {
            long err = s.ncalloc(4, 1, 4);
            buffer = CL10.nclCreateBuffer(context, 1L /* CL_MEM_READ_WRITE */ | 0x10L /* CL_MEM_ALLOC_HOST_PTR */, size,
                    0L, err);
            check(MemoryUtil.memGetInt(err), "clCreateBuffer");
            long p = CL10.nclEnqueueMapBuffer(queue, buffer, 1 /* blocking */, 3L /* CL_MAP_READ | CL_MAP_WRITE */, 0L,
                    size, 0, 0L, 0L, err);
            check(MemoryUtil.memGetInt(err), "clEnqueueMapBuffer");
            return new Pinned(buffer, NativeMemory.wrap(p, size));
        } catch (Throwable t) {
            if (buffer != 0) {
                release(HandleKind.BUFFER, buffer);
            }
            throw unexpected("clEnqueueMapBuffer", t);
        }
    }

    @Override
    public void releasePinned(long queue, Pinned pinned) {
        try {
            check(CL10.nclEnqueueUnmapMemObject(queue, pinned.buffer(), nativeAddress(pinned.memory()), 0, 0L, 0L),
                    "clEnqueueUnmapMemObject");
            check(CL10.clFinish(queue), "clFinish");
        } catch (Throwable t) {
            throw unexpected("clEnqueueUnmapMemObject", t);
        } finally {
            release(HandleKind.BUFFER, pinned.buffer());
        }
    }

    // ---- программа и ядро ------------------------------------------------------------------------

    @Override
    public long createProgram(long context, String source) {
        ByteBuffer text = MemoryUtil.memUTF8(source, false);
        try (MemoryStack s = MemoryStack.stackPush()) {
            long err = s.ncalloc(4, 1, 4);
            long strings = s.ncalloc(8, 1, 8);
            MemoryUtil.memPutAddress(strings, MemoryUtil.memAddress(text));
            long lengths = s.ncalloc(8, 1, 8);
            MemoryUtil.memPutAddress(lengths, text.remaining());
            long p = CL10.nclCreateProgramWithSource(context, 1, strings, lengths, err);
            check(MemoryUtil.memGetInt(err), "clCreateProgramWithSource");
            return p;
        } catch (Throwable t) {
            throw unexpected("clCreateProgramWithSource", t);
        } finally {
            MemoryUtil.memFree(text);
        }
    }

    @Override
    public long createProgramWithBinary(long context, long device, byte[] binary) {
        long image = MemoryUtil.nmemAllocChecked(Math.max(binary.length, 1));
        try (MemoryStack s = MemoryStack.stackPush()) {
            MemoryUtil.memByteBuffer(image, binary.length).put(binary);
            long devices = s.ncalloc(8, 1, 8);
            MemoryUtil.memPutAddress(devices, device);
            long images = s.ncalloc(8, 1, 8);
            MemoryUtil.memPutAddress(images, image);
            long lengths = s.ncalloc(8, 1, 8);
            MemoryUtil.memPutAddress(lengths, binary.length);
            long status = s.ncalloc(4, 1, 4);
            long err = s.ncalloc(4, 1, 4);
            long p = CL10.nclCreateProgramWithBinary(context, 1, devices, lengths, images, status, err);
            check(MemoryUtil.memGetInt(err), "clCreateProgramWithBinary");
            check(MemoryUtil.memGetInt(status), "clCreateProgramWithBinary(status)");
            return p;
        } catch (Throwable t) {
            throw unexpected("clCreateProgramWithBinary", t);
        } finally {
            MemoryUtil.nmemFree(image);
        }
    }

    @Override
    public byte[] programBinary(long program) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            long size = s.ncalloc(8, 1, 8);
            // CL_PROGRAM_BINARY_SIZES (0x1165): по size_t на устройство; контекст у нас с одним устройством
            int st = CL10.nclGetProgramInfo(program, 0x1165, 8L, size, 0L);
            long n = MemoryUtil.memGetAddress(size);
            if (st != 0 || n <= 0 || n > Integer.MAX_VALUE) {
                return null;
            }
            long image = MemoryUtil.nmemAllocChecked(n);
            try {
                long images = s.ncalloc(8, 1, 8);
                MemoryUtil.memPutAddress(images, image);
                // CL_PROGRAM_BINARIES (0x1166): массив указателей на буферы, по одному на устройство
                st = CL10.nclGetProgramInfo(program, 0x1166, 8L, images, 0L);
                if (st != 0) {
                    return null;
                }
                byte[] out = new byte[(int) n];
                MemoryUtil.memByteBuffer(image, out.length).get(out);
                return out;
            } finally {
                MemoryUtil.nmemFree(image);
            }
        } catch (Throwable t) {
            throw unexpected("clGetProgramInfo", t);
        }
    }

    @Override
    public BuildOutcome build(long program, long device, String options) {
        ByteBuffer opts = MemoryUtil.memUTF8(options, true);
        try (MemoryStack s = MemoryStack.stackPush()) {
            long devices = s.ncalloc(8, 1, 8);
            MemoryUtil.memPutAddress(devices, device);
            int st = CL10.nclBuildProgram(program, 1, devices, MemoryUtil.memAddress(opts), 0L, 0L);
            return new BuildOutcome(st, buildLog(program, device));
        } catch (Throwable t) {
            throw unexpected("clBuildProgram", t);
        } finally {
            MemoryUtil.memFree(opts);
        }
    }

    private static String buildLog(long program, long device) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            long size = s.ncalloc(8, 1, 8);
            int st = CL10.nclGetProgramBuildInfo(program, device, 0x1183 /* CL_PROGRAM_BUILD_LOG */, 0L, 0L, size);
            long n = MemoryUtil.memGetAddress(size);
            if (st != 0 || n <= 1) {
                return "";
            }
            long buf = MemoryUtil.nmemCalloc(1, n);
            try {
                st = CL10.nclGetProgramBuildInfo(program, device, 0x1183, n, buf, 0L);
                return st == 0 ? string(buf, n) : "";
            } finally {
                MemoryUtil.nmemFree(buf);
            }
        }
    }

    @Override
    public long createKernel(long program, String name) {
        ByteBuffer n = MemoryUtil.memUTF8(name, true);
        try (MemoryStack s = MemoryStack.stackPush()) {
            long err = s.ncalloc(4, 1, 4);
            long k = CL10.nclCreateKernel(program, MemoryUtil.memAddress(n), err);
            check(MemoryUtil.memGetInt(err), "clCreateKernel");
            return k;
        } catch (Throwable t) {
            throw unexpected("clCreateKernel", t);
        } finally {
            MemoryUtil.memFree(n);
        }
    }

    @Override
    public long kernelMaxWorkGroupSize(long kernel, long device) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            long v = s.ncalloc(8, 1, 8);
            check(CL10.nclGetKernelWorkGroupInfo(kernel, device, 0x11B0 /* WORK_GROUP_SIZE */, 8L, v, 0L),
                    "clGetKernelWorkGroupInfo");
            return MemoryUtil.memGetLong(v);
        } catch (Throwable t) {
            throw unexpected("clGetKernelWorkGroupInfo", t);
        }
    }

    @Override
    public long kernelPrivateMemSize(long kernel, long device) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            long v = s.ncalloc(8, 1, 8);
            int st = CL10.nclGetKernelWorkGroupInfo(kernel, device, 0x11B4 /* CL_KERNEL_PRIVATE_MEM_SIZE */, 8L, v, 0L);
            return st == 0 ? MemoryUtil.memGetLong(v) : -1;
        } catch (Throwable t) {
            throw unexpected("clGetKernelWorkGroupInfo", t);
        }
    }

    // ---- аргументы и запуск ----------------------------------------------------------------------

    @Override
    public void setArgBuffer(long kernel, int index, long buffer) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            long slot = s.ncalloc(8, 1, 8);
            MemoryUtil.memPutAddress(slot, buffer);
            check(CL10.nclSetKernelArg(kernel, index, 8L, slot), "clSetKernelArg");
        } catch (Throwable t) {
            throw unexpected("clSetKernelArg", t);
        }
    }

    @Override
    public void setArgScalar(long kernel, int index, int size, long bits) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            long slot = s.ncalloc(8, 1, 8);
            if (size == 4) {
                MemoryUtil.memPutInt(slot, (int) bits);
            } else {
                MemoryUtil.memPutLong(slot, bits);
            }
            check(CL10.nclSetKernelArg(kernel, index, size, slot), "clSetKernelArg");
        } catch (Throwable t) {
            throw unexpected("clSetKernelArg", t);
        }
    }

    @Override
    public void setArgBytes(long kernel, int index, HostMemory value, long size) {
        long p = nativeAddress(value);
        long copy = p == 0 ? copyOf(value, size) : 0; // драйвер копирует значение аргумента сразу
        try {
            check(CL10.nclSetKernelArg(kernel, index, size, p == 0 ? copy : p), "clSetKernelArg");
        } catch (Throwable t) {
            throw unexpected("clSetKernelArg", t);
        } finally {
            if (copy != 0) {
                MemoryUtil.nmemFree(copy);
            }
        }
    }

    @Override
    public void enqueue(long queue, long kernel, int dim, long[] global, long[] local) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            long gs = s.ncalloc(8, dim, 8);
            long ls = local == null ? 0L : s.ncalloc(8, dim, 8);
            for (int d = 0; d < dim; d++) {
                MemoryUtil.memPutAddress(gs + 8L * d, global[d]);
                if (local != null) {
                    MemoryUtil.memPutAddress(ls + 8L * d, local[d]);
                }
            }
            check(CL10.nclEnqueueNDRangeKernel(queue, kernel, dim, 0L, gs, ls, 0, 0L, 0L), "clEnqueueNDRangeKernel");
        } catch (Throwable t) {
            throw unexpected("clEnqueueNDRangeKernel", t);
        }
    }

    @Override
    public void finish(long queue) {
        try {
            check(CL10.clFinish(queue), "clFinish");
        } catch (Throwable t) {
            throw unexpected("clFinish", t);
        }
    }

    // ---- освобождение ----------------------------------------------------------------------------

    @Override
    public void release(HandleKind kind, long handle) {
        try {
            switch (kind) {
                case CONTEXT -> CL10.clReleaseContext(handle);
                case QUEUE -> CL10.clReleaseCommandQueue(handle);
                case BUFFER -> CL10.clReleaseMemObject(handle);
                case PROGRAM -> CL10.clReleaseProgram(handle);
                case KERNEL -> CL10.clReleaseKernel(handle);
            }
        } catch (Throwable ignored) {
            // при освобождении ошибки не пробрасываются
        }
    }

    /** Закрывает слой; {@code CL} не уничтожается: им может пользоваться другой код процесса. */
    @Override
    public void close() {
        closed = true;
    }

    /** @return {@code true} после {@link #close()} */
    public boolean isClosed() {
        return closed;
    }
}
