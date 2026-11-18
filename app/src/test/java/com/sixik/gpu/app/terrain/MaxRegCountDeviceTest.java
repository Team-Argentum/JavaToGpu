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
 * Замер опции NVIDIA {@code -cl-nv-maxrregcount=N}: предел регистров на поток повышает заполняемость SM ценой
 * проливания. Ядро с {@code LIVE} одновременно живыми значениями собирается без предела и с пределами; варианты
 * чередуются по раундам, результат обязан совпасть побитово, печатаются медиана, регистры и проливание.
 */
@Tag("device")
class MaxRegCountDeviceTest {

    private static final int RUNS = 30;
    private static final int N = 1 << 20;
    private static final Pattern REGISTERS = Pattern.compile("Used (\\d+) registers");
    private static final Pattern SPILL = Pattern.compile("(\\d+) bytes spill stores, (\\d+) bytes spill loads");
    private static final int[] CAPS = {0, 128, 96, 64, 48, 32};

    /** Ядро с {@code live} значениями, которые все нужны до конца (перемешивание по кругу). */
    private static String kernel(int live) {
        StringBuilder sb = new StringBuilder("#pragma OPENCL FP_CONTRACT OFF\n");
        sb.append("__kernel void k(__global float* out, __global const float* in, int rounds) {\n");
        sb.append("    int i = (int)get_global_id(0);\n    float x = in[i];\n");
        for (int j = 0; j < live; j++) {
            sb.append("    float a").append(j).append(" = x * ").append(1.0f + j * 0.01f).append("f + ")
                    .append(j * 0.5f).append("f;\n");
        }
        sb.append("    for (int r = 0; r < rounds; r++) {\n");
        for (int j = 0; j < live; j++) {
            int n = (j + 1) % live;
            int m = (j + 7) % live;
            sb.append("        a").append(j).append(" = a").append(j).append(" * 0.999f + a").append(n)
                    .append(" * 0.001f - a").append(m).append(" * 0.0005f;\n");
        }
        sb.append("    }\n    float s = 0.0f;\n");
        for (int j = 0; j < live; j++) {
            sb.append("    s += a").append(j).append(" * ").append(1 + j % 3).append(".0f;\n");
        }
        sb.append("    out[i] = s;\n}\n");
        return sb.toString();
    }

    @Test
    void registerCapVariants() {
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
                if (device == 0 && (api.deviceLong(d, 0x1000) & 4) != 0
                        && api.deviceString(d, 0x102C).toUpperCase(Locale.ROOT).contains("NVIDIA")) {
                    device = d;
                }
            }
        }
        Assumptions.assumeTrue(device != 0, "no NVIDIA OpenCL GPU");
        long context = api.createContext(device);
        long queue = api.createQueue(context, device);
        try (Arena arena = Arena.ofConfined()) {
            float[] input = new float[N];
            for (int i = 0; i < N; i++) {
                input[i] = (i % 997) * 0.001f;
            }
            long in = api.createBuffer(context, N * 4L);
            long out = api.createBuffer(context, N * 4L);
            api.writeBuffer(queue, in, 0, N * 4L, FfmMemory.wrap(arena.allocateFrom(ValueLayout.JAVA_FLOAT, input)));
            for (int live : new int[]{48, 96, 160}) {
                String src = kernel(live);
                long[] programs = new long[CAPS.length];
                long[] kernels = new long[CAPS.length];
                String[] info = new String[CAPS.length];
                for (int v = 0; v < CAPS.length; v++) {
                    programs[v] = api.createProgram(context, src);
                    String opts = "-cl-nv-verbose" + (CAPS[v] > 0 ? " -cl-nv-maxrregcount=" + CAPS[v] : "");
                    DeviceApi.BuildOutcome b = api.build(programs[v], device, opts);
                    assertTrue(b.ok(), b.log());
                    kernels[v] = api.createKernel(programs[v], "k");
                    api.setArgBuffer(kernels[v], 0, out);
                    api.setArgBuffer(kernels[v], 1, in);
                    api.setArgScalar(kernels[v], 2, 4, 8);
                    Matcher r = REGISTERS.matcher(b.log());
                    Matcher s = SPILL.matcher(b.log());
                    info[v] = (r.find() ? r.group(1) : "?") + " regs, spill "
                            + (s.find() ? s.group(1) + "/" + s.group(2) : "?");
                }
                long[][] times = new long[CAPS.length][RUNS];
                float[] reference = null;
                MemorySegment host = arena.allocate(N * 4L, 4);
                for (int r = 0; r < RUNS + 5; r++) {
                    for (int v = 0; v < CAPS.length; v++) {
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
                                assertTrue(Arrays.equals(reference, result), "cap " + CAPS[v] + " changes the result");
                            }
                        }
                    }
                }
                System.out.printf(Locale.ROOT, "live %d:%n", live);
                for (int v = 0; v < CAPS.length; v++) {
                    Arrays.sort(times[v]);
                    System.out.printf(Locale.ROOT, "  cap %-4s %.3f ms (p25 %.3f), %s%n",
                            CAPS[v] == 0 ? "none" : String.valueOf(CAPS[v]), times[v][RUNS / 2] / 1e6,
                            times[v][RUNS / 4] / 1e6, info[v]);
                    api.release(DeviceApi.HandleKind.KERNEL, kernels[v]);
                    api.release(DeviceApi.HandleKind.PROGRAM, programs[v]);
                }
            }
            api.release(DeviceApi.HandleKind.BUFFER, in);
            api.release(DeviceApi.HandleKind.BUFFER, out);
        } finally {
            api.release(DeviceApi.HandleKind.QUEUE, queue);
            api.release(DeviceApi.HandleKind.CONTEXT, context);
        }
    }
}
