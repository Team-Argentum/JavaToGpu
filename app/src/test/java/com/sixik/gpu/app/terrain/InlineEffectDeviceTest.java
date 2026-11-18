package com.sixik.gpu.app.terrain;

import com.sixik.gpu.codegen.opencl.Artifact;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.JavaToGpu;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Замер перед встраиванием device-функций и развёрткой циклов в кодгене (REWORK_ARCHITECTURE.md, 7.1): тот же
 * OpenCL-текст ядра {@code TerrainNoise.generate}, изменённый вручную - функции {@code always_inline}, число октав
 * константой, {@code #pragma unroll}. Варианты собираются и запускаются напрямую через {@link DeviceApi}; результат
 * обязан совпасть побитово с исходным, печатаются медиана ядра и регистры (лог {@code -cl-nv-verbose}).
 */
@Tag("device")
class InlineEffectDeviceTest {

    private static final int RUNS = 30;
    private static final int CL_DEVICE_TYPE = 0x1000;
    private static final int CL_DEVICE_TYPE_GPU = 4;
    private static final Pattern REGISTERS = Pattern.compile("Used (\\d+) registers");

    @Test
    void inliningAndUnrollingVariants() {
        Result<GpuContext> rc = GpuContext.builder().ignoreEnvironment().noDiskCache()
                .fallback(GpuContext.Fallback.NEVER).tryBuild();
        Assumptions.assumeTrue(rc.isOk(), "no OpenCL device");
        Artifact art;
        try (GpuContext ctx = rc.value()) {
            art = ctx.compile(JavaToGpu.loadModule(TerrainNoise.class, "generate")).value().artifact();
        }
        String src = art.source();
        String loop = "v6 < p4;";
        Assumptions.assumeTrue(src.contains(loop) && src.contains("\nfloat f"), "unexpected kernel text");
        String inlined = src.replace("\nfloat f", "\n__attribute__((always_inline)) static inline float f");
        String constant = inlined.replace(loop, "v6 < 6;");
        Map<String, String> variants = new LinkedHashMap<>();
        variants.put("as emitted", src);
        variants.put("octaves = 6", src.replace(loop, "v6 < 6;"));
        variants.put("always_inline", inlined);
        variants.put("inline + octaves = 6", constant);
        variants.put("inline + 6 + unroll", constant.replace("        for (; v6 < 6;",
                "        #pragma unroll\n        for (; v6 < 6;"));
        variants.put("noinline", src.replace("\nfloat f", "\n__attribute__((noinline)) float f"));

        DeviceApi api = OpenClApi.load();
        long device = gpu(api);
        Assumptions.assumeTrue(device != 0, "no OpenCL GPU");
        long context = api.createContext(device);
        long queue = api.createQueue(context, device);
        try (Arena arena = Arena.ofConfined()) {
            for (int chunks : new int[]{8, 64}) {
                TerrainBatch batch = new TerrainBatch(chunks, TerrainBatch.Sampling.DENSE);
                TerrainBatch.Sampling g = batch.sampling;
                int n = batch.output.length;
                long out = api.createBuffer(context, n * 4L);
                long xs = upload(api, context, queue, arena, batch.chunkX);
                long zs = upload(api, context, queue, arena, batch.chunkZ);
                assertEquals(6, batch.octaves, "the constant variants assume 6 octaves");
                String[] names = variants.keySet().toArray(String[]::new);
                long[] programs = new long[names.length];
                long[] kernels = new long[names.length];
                String[] regs = new String[names.length];
                for (int v = 0; v < names.length; v++) {
                    programs[v] = api.createProgram(context, variants.get(names[v]));
                    DeviceApi.BuildOutcome b = api.build(programs[v], device, art.buildOptions() + " -cl-nv-verbose");
                    assertTrue(b.ok(), names[v] + ": " + b.log());
                    kernels[v] = api.createKernel(programs[v], art.entry());
                    long[] bufs = {out, xs, zs};
                    int[] lens = {n, batch.chunkX.length, batch.chunkZ.length};
                    int[] scalars = {g.horizontal, g.vertical, g.stepXZ, g.stepY, batch.seed, batch.octaves};
                    for (int i = 0; i < 3; i++) {
                        api.setArgBuffer(kernels[v], 2 * i, bufs[i]);
                        api.setArgScalar(kernels[v], 2 * i + 1, 4, lens[i]);
                    }
                    for (int i = 0; i < scalars.length; i++) {
                        api.setArgScalar(kernels[v], 6 + i, 4, scalars[i]);
                    }
                    Matcher m = REGISTERS.matcher(b.log());
                    regs[v] = "?";
                    while (m.find()) {
                        regs[v] = m.group(1); // последняя запись лога - сам kernel
                    }
                }
                // раунды: в каждом все варианты по разу, чтобы разгон и нагрев карты влияли на всех одинаково
                long[][] times = new long[names.length][RUNS];
                float[] reference = null;
                MemorySegment host = arena.allocate(n * 4L, 4);
                for (int r = 0; r < RUNS + 5; r++) {
                    for (int v = 0; v < names.length; v++) {
                        long t = System.nanoTime();
                        api.enqueue(queue, kernels[v], 1, new long[]{n, 1, 1}, null);
                        api.finish(queue);
                        if (r >= 5) {
                            times[v][r - 5] = System.nanoTime() - t;
                        }
                        if (r == 0) {
                            api.readBuffer(queue, out, 0, n * 4L, FfmMemory.wrap(host));
                            float[] result = host.toArray(ValueLayout.JAVA_FLOAT);
                            if (reference == null) {
                                reference = result;
                            } else {
                                assertTrue(Arrays.equals(reference, result), names[v] + " changes the result");
                            }
                        }
                    }
                }
                System.out.printf(Locale.ROOT, "DENSE, %d chunks (%,d samples):%n", chunks, n);
                for (int v = 0; v < names.length; v++) {
                    Arrays.sort(times[v]);
                    System.out.printf(Locale.ROOT, "  %-22s %.3f ms (p25 %.3f, p75 %.3f), %s registers%n", names[v],
                            times[v][RUNS / 2] / 1e6, times[v][RUNS / 4] / 1e6, times[v][RUNS * 3 / 4] / 1e6, regs[v]);
                    api.release(DeviceApi.HandleKind.KERNEL, kernels[v]);
                    api.release(DeviceApi.HandleKind.PROGRAM, programs[v]);
                }
                api.release(DeviceApi.HandleKind.BUFFER, out);
                api.release(DeviceApi.HandleKind.BUFFER, xs);
                api.release(DeviceApi.HandleKind.BUFFER, zs);
            }
        } finally {
            api.release(DeviceApi.HandleKind.QUEUE, queue);
            api.release(DeviceApi.HandleKind.CONTEXT, context);
        }
    }

    private static long upload(DeviceApi api, long context, long queue, Arena arena, int[] data) {
        long b = api.createBuffer(context, Math.max(4L, data.length * 4L));
        api.writeBuffer(queue, b, 0, data.length * 4L, FfmMemory.wrap(arena.allocateFrom(ValueLayout.JAVA_INT, data)));
        return b;
    }

    private static long gpu(DeviceApi api) {
        for (long p : api.platforms()) {
            for (long d : api.devices(p)) {
                if ((api.deviceLong(d, CL_DEVICE_TYPE) & CL_DEVICE_TYPE_GPU) != 0) {
                    return d;
                }
            }
        }
        return 0;
    }
}
