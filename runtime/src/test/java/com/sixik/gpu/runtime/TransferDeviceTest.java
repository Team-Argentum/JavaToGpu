package com.sixik.gpu.runtime;

import com.sixik.gpu.runtime.device.DeviceApi;
import com.sixik.gpu.runtime.device.DeviceInfo;
import com.sixik.gpu.runtime.device.OpenClApi;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Пропускная способность передач хост-устройство на настоящем драйвере: обычная (pageable) память против закреплённой
 * ({@link DeviceApi#createPinned}), отдельно - копирование между нативной памятью и {@code float[]}. Печатает таблицу;
 * проверяет только корректность данных (тайминги зависят от машины). Запуск: {@code ./gradlew :runtime:deviceTest}.
 */
@Tag("device")
class TransferDeviceTest {

    private static final int RUNS = 15;

    private static double median(long[] nanos) {
        long[] s = nanos.clone();
        Arrays.sort(s);
        return s[s.length / 2];
    }

    private static String gbps(long bytes, double nanos) {
        return String.format(Locale.ROOT, "%7.2f ms %6.1f GB/s", nanos / 1e6, bytes / nanos);
    }

    private static void parallelCopy(MemorySegment src, float[] dst, int parts) {
        int n = dst.length;
        java.util.stream.IntStream.range(0, parts).parallel().forEach(p -> {
            int from = (int) ((long) n * p / parts);
            int to = (int) ((long) n * (p + 1) / parts);
            MemorySegment.copy(src, ValueLayout.JAVA_FLOAT, 4L * from, dst, from, to - from);
        });
    }

    @Test
    void pageableVersusPinned() {
        DeviceApi api = OpenClApi.load();
        Assumptions.assumeTrue(api != null, "no OpenCL library");
        try {
            List<DeviceInfo> all = DeviceInfo.enumerate(api);
            DeviceInfo d = all.stream().filter(DeviceInfo::isGpu).findFirst().orElse(null);
            Assumptions.assumeTrue(d != null, "no GPU");
            long ctx = api.createContext(d.handle());
            long q = api.createQueue(ctx, d.handle());
            int n = 64 * 23_040 * 4; // как выход TerrainBatch (64 чанка DENSE)
            long bytes = 4L * n;
            long buf = api.createBuffer(ctx, bytes);
            float[] host = new float[n];
            for (int i = 0; i < n; i++) {
                host[i] = i * 0.5f;
            }
            float[] back = new float[n];
            try (Arena arena = Arena.ofShared()) {
                MemorySegment pageable = arena.allocate(bytes, 64);
                DeviceApi.Pinned pinned = api.createPinned(ctx, q, bytes);
                Assumptions.assumeTrue(pinned != null, "pinned memory is not supported");
                try {
                    long[] wPage = new long[RUNS];
                    long[] rPage = new long[RUNS];
                    long[] wPin = new long[RUNS];
                    long[] rPin = new long[RUNS];
                    long[] toNative = new long[RUNS];
                    long[] fromNative = new long[RUNS];
                    for (int r = 0; r < RUNS; r++) {
                        long t = System.nanoTime();
                        MemorySegment.copy(host, 0, pageable, ValueLayout.JAVA_FLOAT, 0, n);
                        toNative[r] = System.nanoTime() - t;
                        t = System.nanoTime();
                        api.writeBuffer(q, buf, 0, bytes, pageable);
                        wPage[r] = System.nanoTime() - t;
                        t = System.nanoTime();
                        api.readBuffer(q, buf, 0, bytes, pageable);
                        rPage[r] = System.nanoTime() - t;
                        t = System.nanoTime();
                        MemorySegment.copy(pageable, ValueLayout.JAVA_FLOAT, 0, back, 0, n);
                        fromNative[r] = System.nanoTime() - t;

                        MemorySegment.copy(host, 0, pinned.memory(), ValueLayout.JAVA_FLOAT, 0, n);
                        t = System.nanoTime();
                        api.writeBuffer(q, buf, 0, bytes, pinned.memory());
                        wPin[r] = System.nanoTime() - t;
                        t = System.nanoTime();
                        api.readBuffer(q, buf, 0, bytes, pinned.memory());
                        rPin[r] = System.nanoTime() - t;
                    }
                    // параллельное копирование pinned -> float[]
                    long[] parCopy = new long[RUNS];
                    long[] chunked = new long[RUNS];
                    int parts = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
                    for (int r = 0; r < RUNS; r++) {
                        Arrays.fill(back, 0);
                        long t = System.nanoTime();
                        parallelCopy(pinned.memory(), back, parts);
                        parCopy[r] = System.nanoTime() - t;
                        assertArrayEquals(host, back);
                        Arrays.fill(back, 0);
                        t = System.nanoTime();
                        api.readBuffer(q, buf, 0, bytes, pinned.memory());
                        parallelCopy(pinned.memory(), back, parts);
                        chunked[r] = System.nanoTime() - t;
                        assertArrayEquals(host, back);
                    }
                    System.out.println("  pinned -> float[] x" + parts + "  " + gbps(bytes, median(parCopy)));
                    System.out.println("  read pinned + parallel copy " + gbps(bytes, median(chunked)));
                    // пропускная способность оперативной памяти: копирование нативной памяти в нативную в 8 потоков
                    MemorySegment other = arena.allocate(bytes, 64);
                    long[] ram = new long[RUNS];
                    for (int r = 0; r < RUNS; r++) {
                        long t = System.nanoTime();
                        java.util.stream.IntStream.range(0, parts).parallel().forEach(p -> {
                            long from = bytes * p / parts;
                            long to = bytes * (p + 1) / parts;
                            MemorySegment.copy(pageable, from, other, from, to - from);
                        });
                        ram[r] = System.nanoTime() - t;
                    }
                    System.out.println("  RAM copy x" + parts + " (read+write) " + gbps(2 * bytes, median(ram)) + " of traffic");
                    // задержка одного блокирующего вызова драйвера
                    long[] tiny = new long[RUNS];
                    for (int r = 0; r < RUNS; r++) {
                        long t = System.nanoTime();
                        api.readBuffer(q, buf, 0, 64, pinned.memory());
                        tiny[r] = System.nanoTime() - t;
                    }
                    System.out.printf(Locale.ROOT, "  blocking 64 B read latency %.1f us%n", median(tiny) / 1e3);
                    MemorySegment.copy(pinned.memory(), ValueLayout.JAVA_FLOAT, 0, back, 0, n);
                    assertArrayEquals(host, back);
                    System.out.println("device " + d.describe() + ", " + bytes / 1e6 + " MB");
                    System.out.println("  write pageable " + gbps(bytes, median(wPage)));
                    System.out.println("  write pinned   " + gbps(bytes, median(wPin)));
                    System.out.println("  read  pageable " + gbps(bytes, median(rPage)));
                    System.out.println("  read  pinned   " + gbps(bytes, median(rPin)));
                    System.out.println("  float[] -> native " + gbps(bytes, median(toNative)));
                    System.out.println("  native -> float[] " + gbps(bytes, median(fromNative)));
                } finally {
                    api.releasePinned(q, pinned);
                }
            }
            api.release(DeviceApi.HandleKind.BUFFER, buf);
            api.release(DeviceApi.HandleKind.QUEUE, q);
            api.release(DeviceApi.HandleKind.CONTEXT, ctx);
        } finally {
            try {
                api.close();
            } catch (Exception ignored) {
                // закрытие слоя доступа в тесте
            }
        }
    }
}
