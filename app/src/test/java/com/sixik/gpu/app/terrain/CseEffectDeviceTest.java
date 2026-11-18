package com.sixik.gpu.app.terrain;

import com.sixik.gpu.runtime.device.DeviceApi;
import com.sixik.gpu.runtime.ffm.OpenClApi;
import com.sixik.gpu.runtime.ffm.FfmMemory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Замер перед CSE через границы вложенных блоков (REWORK_ARCHITECTURE.md, 7.1): одно и то же ядро с дорогими общими
 * подвыражениями (до {@code if} и в его ветках, в теле цикла и во вложенном {@code if}) - как его пишет кодген без
 * межблочного CSE, и с подвыражениями, вынесенными вручную во временные. Варианты собираются через {@link DeviceApi},
 * чередуются по раундам; результат обязан совпасть побитово, печатаются медиана ядра и регистры.
 */
@Tag("device")
class CseEffectDeviceTest {

    private static final int RUNS = 30;
    private static final int N = 1 << 22;
    private static final Pattern REGISTERS = Pattern.compile("Used (\\d+) registers");

    /** Без межблочного CSE: {@code sin(a)*cos(b)} и {@code sqrt(...)} повторяются в ветках и во вложенном if. */
    private static final String REDUNDANT = """
            #pragma OPENCL FP_CONTRACT OFF
            __kernel void k(__global float* out, __global const float* in, int steps) {
                int i = (int)get_global_id(0);
                float a = in[i];
                float b = a * 0.37f + 1.0f;
                float s = sin(a) * cos(b);
                float acc = 0.0f;
                if (s > 0.0f) {
                    acc = sin(a) * cos(b) + sqrt(fabs(a * b) + 1.0f);
                } else {
                    acc = sin(a) * cos(b) - sqrt(fabs(a * b) + 1.0f);
                }
                for (int j = 0; j < steps; j++) {
                    float t = a + (float)j;
                    float u = exp(t * 0.01f) * log(fabs(t) + 2.0f);
                    if (u > 1.0f) {
                        acc += exp(t * 0.01f) * log(fabs(t) + 2.0f) * 0.5f;
                    } else {
                        acc -= exp(t * 0.01f) * log(fabs(t) + 2.0f);
                    }
                }
                out[i] = acc;
            }
            """;

    /** Те же вычисления с общими подвыражениями во временных. */
    private static final String SHARED = """
            #pragma OPENCL FP_CONTRACT OFF
            __kernel void k(__global float* out, __global const float* in, int steps) {
                int i = (int)get_global_id(0);
                float a = in[i];
                float b = a * 0.37f + 1.0f;
                float s = sin(a) * cos(b);
                float r = sqrt(fabs(a * b) + 1.0f);
                float acc = 0.0f;
                if (s > 0.0f) {
                    acc = s + r;
                } else {
                    acc = s - r;
                }
                for (int j = 0; j < steps; j++) {
                    float t = a + (float)j;
                    float u = exp(t * 0.01f) * log(fabs(t) + 2.0f);
                    if (u > 1.0f) {
                        acc += u * 0.5f;
                    } else {
                        acc -= u;
                    }
                }
                out[i] = acc;
            }
            """;

    @Test
    void crossBlockCommonSubexpressions() {
        DeviceApi api;
        try {
            api = OpenClApi.load();
        } catch (RuntimeException | LinkageError e) {
            Assumptions.abort("no OpenCL: " + e);
            return;
        }
        long device = 0;
        for (long p : api.platforms()) {
            for (long d : api.devices(p)) {
                if (device == 0 && (api.deviceLong(d, 0x1000) & 4) != 0) {
                    device = d;
                }
            }
        }
        Assumptions.assumeTrue(device != 0, "no OpenCL GPU");
        long context = api.createContext(device);
        long queue = api.createQueue(context, device);
        String[] names = {"redundant", "shared"};
        String[] sources = {REDUNDANT, SHARED};
        try (Arena arena = Arena.ofConfined()) {
            float[] input = new float[N];
            for (int i = 0; i < N; i++) {
                input[i] = (i % 1000) * 0.01f - 5f;
            }
            long in = api.createBuffer(context, N * 4L);
            long out = api.createBuffer(context, N * 4L);
            api.writeBuffer(queue, in, 0, N * 4L, FfmMemory.wrap(arena.allocateFrom(ValueLayout.JAVA_FLOAT, input)));
            long[] programs = new long[2];
            long[] kernels = new long[2];
            String[] regs = new String[2];
            for (int v = 0; v < 2; v++) {
                programs[v] = api.createProgram(context, sources[v]);
                DeviceApi.BuildOutcome b = api.build(programs[v], device, "-cl-nv-verbose");
                assertTrue(b.ok(), b.log());
                kernels[v] = api.createKernel(programs[v], "k");
                api.setArgBuffer(kernels[v], 0, out);
                api.setArgBuffer(kernels[v], 1, in);
                api.setArgScalar(kernels[v], 2, 4, 16);
                Matcher m = REGISTERS.matcher(b.log());
                regs[v] = m.find() ? m.group(1) : "?";
            }
            long[][] times = new long[2][RUNS];
            float[] reference = null;
            MemorySegment host = arena.allocate(N * 4L, 4);
            for (int r = 0; r < RUNS + 5; r++) {
                for (int v = 0; v < 2; v++) {
                    long t = System.nanoTime();
                    api.enqueue(queue, kernels[v], 1, new long[]{N, 1, 1}, null);
                    api.finish(queue);
                    if (r >= 5) {
                        times[v][r - 5] = System.nanoTime() - t;
                    }
                    if (r == 0) {
                        api.readBuffer(queue, out, 0, N * 4L, FfmMemory.wrap(host));
                        float[] result = host.toArray(ValueLayout.JAVA_FLOAT);
                        if (reference == null) {
                            reference = result;
                        } else {
                            assertTrue(Arrays.equals(reference, result), names[v] + " changes the result");
                        }
                    }
                }
            }
            for (int v = 0; v < 2; v++) {
                Arrays.sort(times[v]);
                System.out.printf(Locale.ROOT, "%-10s %.3f ms (p25 %.3f), %s registers%n", names[v],
                        times[v][RUNS / 2] / 1e6, times[v][RUNS / 4] / 1e6, regs[v]);
                api.release(DeviceApi.HandleKind.KERNEL, kernels[v]);
                api.release(DeviceApi.HandleKind.PROGRAM, programs[v]);
            }
            api.release(DeviceApi.HandleKind.BUFFER, in);
            api.release(DeviceApi.HandleKind.BUFFER, out);
        } finally {
            api.release(DeviceApi.HandleKind.QUEUE, queue);
            api.release(DeviceApi.HandleKind.CONTEXT, context);
        }
    }
}
