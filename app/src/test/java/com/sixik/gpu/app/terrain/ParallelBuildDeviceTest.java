package com.sixik.gpu.app.terrain;

import com.sixik.gpu.codegen.opencl.Artifact;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.runtime.device.DeviceApi;
import com.sixik.gpu.runtime.device.OpenClApi;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Собирает ли драйвер несколько программ параллельно: OpenCL-тексты ядер ландшафта (каждый со своей «солью», чтобы
 * сборки не совпадали) собираются по очереди и в потоках одновременно, через {@link DeviceApi} напрямую. Кэш драйвера
 * NVIDIA выключен в {@code deviceTest}. Печатает время.
 */
@Tag("device")
class ParallelBuildDeviceTest {

    private static final String[] KERNELS = {"generate", "generateResident", "countSolid", "countSolidFusedStrided"};
    private static final int COPIES = 2;

    @Test
    void sequentialVersusParallelBuilds() throws Exception {
        List<String> sources = new ArrayList<>();
        Result<GpuContext> rc = GpuContext.builder().ignoreEnvironment().noDiskCache().fusion(false)
                .fallback(GpuContext.Fallback.NEVER).tryBuild();
        Assumptions.assumeTrue(rc.isOk(), "no OpenCL device");
        String options;
        try (GpuContext ctx = rc.value()) {
            Artifact first = null;
            for (String k : KERNELS) {
                Artifact a = ctx.compile(JavaToGpu.loadModule(TerrainNoise.class, k)).value().artifact();
                first = first == null ? a : first;
                sources.add(a.source());
            }
            options = first.buildOptions();
        }
        DeviceApi api = OpenClApi.load();
        long device = 0;
        for (long p : api.platforms()) {
            for (long d : api.devices(p)) {
                if (device == 0 && (api.deviceLong(d, 0x1000) & 4) != 0) {
                    device = d;
                }
            }
        }
        long dev = device;
        long context = api.createContext(dev);
        try {
            for (int round = 0; round < 2; round++) {
                List<String> seq = salted(sources, "s" + round);
                long t = System.nanoTime();
                for (String s : seq) {
                    build(api, context, dev, s, options);
                }
                long sequential = System.nanoTime() - t;

                List<String> par = salted(sources, "p" + round);
                ExecutorService pool = Executors.newFixedThreadPool(par.size());
                t = System.nanoTime();
                List<Future<?>> fs = new ArrayList<>();
                for (String s : par) {
                    fs.add(pool.submit(() -> build(api, context, dev, s, options)));
                }
                for (Future<?> f : fs) {
                    f.get();
                }
                long parallel = System.nanoTime() - t;
                pool.shutdown();
                System.out.printf(Locale.ROOT, "%d programs: sequential %.0f ms, parallel (%d threads) %.0f ms%n",
                        par.size(), sequential / 1e6, par.size(), parallel / 1e6);
            }
        } finally {
            api.release(DeviceApi.HandleKind.CONTEXT, context);
        }
    }

    /** Копии текстов с уникальным комментарием: драйвер не узнает уже собранную программу. */
    private static List<String> salted(List<String> sources, String tag) {
        List<String> out = new ArrayList<>();
        for (int c = 0; c < COPIES; c++) {
            for (String s : sources) {
                out.add("// " + tag + "-" + c + "-" + System.nanoTime() + "\n" + s);
            }
        }
        return out;
    }

    private static Void build(DeviceApi api, long context, long device, String source, String options) {
        long p = api.createProgram(context, source);
        DeviceApi.BuildOutcome b = api.build(p, device, options);
        assertTrue(b.ok(), b.log());
        api.release(DeviceApi.HandleKind.PROGRAM, p);
        return null;
    }
}
