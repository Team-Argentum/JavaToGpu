package com.sixik.gpu.app.terrain;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.optimizer.OptimizeOptions;
import com.sixik.gpu.runtime.CompiledKernel;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.runtime.LaunchConfig;
import com.sixik.gpu.runtime.LaunchStats;
import com.sixik.gpu.runtime.PreparedLauncher;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Влияние оптимизатора IR на реальное ядро ({@link TerrainNoise#generate}): чистое время ядра на устройстве, передачи,
 * регистры по данным драйвера и размер OpenCL C - без оптимизатора и на каждом уровне. Результаты печатаются, исходники
 * сохраняются в {@code build/reports/optimizer-effect/}. Результат ядра обязан совпадать побитово (точные преобразования).
 * Запуск: {@code ./gradlew :app:deviceTest --tests '*OptimizerEffectDeviceTest'}.
 */
@Tag("device")
class OptimizerEffectDeviceTest {

    private static final int RUNS = 30;

    record Measure(String mode, long kernelMedianNanos, long uploadNanos, long readbackNanos, int registers,
                   int estimate, int irNodes, int sourceChars, float[] output) {
    }

    private static Measure measure(String mode, OptimizeOptions o, TerrainBatch.Sampling sampling) throws IOException {
        Result<GpuContext> rc = GpuContext.builder().ignoreEnvironment().noDiskCache().optimize(o)
                .fallback(GpuContext.Fallback.NEVER).tryBuild();
        Assumptions.assumeTrue(rc.isOk(), "no OpenCL device");
        try (GpuContext ctx = rc.value()) {
            IrModule m = JavaToGpu.loadModule(TerrainNoise.class, "generate");
            Result<CompiledKernel> ck = ctx.compile(m);
            assertTrue(ck.isOk(), ck.diagnostics().toString());
            CompiledKernel k = ck.value();
            TerrainBatch batch = new TerrainBatch(64, sampling);
            TerrainBatch.Sampling g = batch.sampling;
            Object[] args = {batch.output, batch.chunkX, batch.chunkZ, g.horizontal, g.vertical, g.stepXZ, g.stepY,
                    batch.seed, batch.octaves};
            Result<PreparedLauncher> pr = ctx.prepare(k, args);
            assertTrue(pr.isOk(), pr.diagnostics().toString());
            PreparedLauncher p = pr.value();
            LaunchConfig cfg = LaunchConfig.of1D(batch.output.length);
            for (int i = 0; i < 5; i++) {
                p.run(cfg);
            }
            long[] kernel = new long[RUNS];
            long up = 0;
            long down = 0;
            for (int i = 0; i < RUNS; i++) {
                LaunchStats s = p.run(cfg);
                kernel[i] = s.kernelNanos();
                up += s.uploadNanos();
                down += s.readbackNanos();
            }
            Arrays.sort(kernel);
            Path dir = Path.of("build", "reports", "optimizer-effect");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("terrain-" + mode + ".cl"), k.artifact().source(), StandardCharsets.UTF_8);
            return new Measure(mode, kernel[RUNS / 2], up / RUNS, down / RUNS, k.driverStats().registers(),
                    k.estimatedRegisters(), k.module().nodeCount(), k.artifact().source().length(),
                    batch.output.clone());
        }
    }

    /** Выходной массив ядро перезаписывает целиком ({@code WriteCoverage}): ни один путь запуска его не загружает. */
    @Test
    void outputArrayIsNotUploaded() {
        Result<GpuContext> rc = GpuContext.builder().ignoreEnvironment().noDiskCache()
                .fallback(GpuContext.Fallback.NEVER).tryBuild();
        Assumptions.assumeTrue(rc.isOk(), "no OpenCL device");
        try (GpuContext ctx = rc.value()) {
            CompiledKernel k = ctx.compile(JavaToGpu.loadModule(TerrainNoise.class, "generate")).value();
            assertTrue(k.fullyWritten(0), "output[getGlobalId(0)] = ... covers the array");
            TerrainBatch batch = new TerrainBatch(8, TerrainBatch.Sampling.DENSE);
            TerrainBatch.Sampling g = batch.sampling;
            Object[] args = {batch.output, batch.chunkX, batch.chunkZ, g.horizontal, g.vertical, g.stepXZ, g.stepY,
                    batch.seed, batch.octaves};
            LaunchConfig cfg = LaunchConfig.of1D(batch.output.length);
            long inputs = 4L * (batch.chunkX.length + batch.chunkZ.length);
            for (int i = 0; i < 3; i++) {
                LaunchStats plain = ctx.launch(k, cfg, args).value();
                System.out.printf(Locale.ROOT, "launch:   up %d B in %.1f us, kernel %.1f us, down %d B in %.1f us%n",
                        plain.bytesUp(), plain.uploadNanos() / 1e3, plain.kernelNanos() / 1e3, plain.bytesDown(),
                        plain.readbackNanos() / 1e3);
                assertTrue(plain.bytesUp() <= inputs, "launch uploaded " + plain.bytesUp() + " bytes");
            }
            try (PreparedLauncher p = ctx.prepare(k, args).value()) {
                LaunchStats s = p.run(cfg);
                System.out.printf(Locale.ROOT, "prepared: up %d B in %.1f us, kernel %.1f us, down %d B in %.1f us%n",
                        s.bytesUp(), s.uploadNanos() / 1e3, s.kernelNanos() / 1e3, s.bytesDown(), s.readbackNanos() / 1e3);
                assertTrue(s.bytesUp() <= inputs, "prepared launch uploaded " + s.bytesUp() + " bytes");
            }
        }
    }

    /**
     * Результат в {@code GpuBuffer}: чтение в {@code float[]} против вида без копирования ({@code readView()}).
     * Содержимое обязано совпадать; печатается медианное время чтения.
     */
    @Test
    void resultViewAvoidsTheArrayCopy() {
        Result<GpuContext> rc = GpuContext.builder().ignoreEnvironment().noDiskCache()
                .fallback(GpuContext.Fallback.NEVER).tryBuild();
        Assumptions.assumeTrue(rc.isOk(), "no OpenCL device");
        try (GpuContext ctx = rc.value()) {
            CompiledKernel k = ctx.compile(JavaToGpu.loadModule(TerrainNoise.class, "generate")).value();
            TerrainBatch batch = new TerrainBatch(64, TerrainBatch.Sampling.DENSE);
            TerrainBatch.Sampling g = batch.sampling;
            try (com.sixik.gpu.runtime.GpuBuffer out = ctx.allocate(com.sixik.gpu.ir.Types.F32, batch.output.length)) {
                Object[] args = {out, batch.chunkX, batch.chunkZ, g.horizontal, g.vertical, g.stepXZ, g.stepY,
                        batch.seed, batch.octaves};
                LaunchConfig cfg = LaunchConfig.of1D(batch.output.length);
                try (PreparedLauncher p = ctx.prepare(k, args).value()) {
                    long[] copy = new long[RUNS];
                    long[] view = new long[RUNS];
                    float[] host = new float[batch.output.length];
                    java.lang.foreign.MemorySegment v = null;
                    for (int i = 0; i < RUNS + 3; i++) {
                        p.run(cfg);
                        long t = System.nanoTime();
                        out.read(host);
                        long tc = System.nanoTime() - t;
                        t = System.nanoTime();
                        v = out.readView();
                        long tv = System.nanoTime() - t;
                        if (i >= 3) {
                            copy[i - 3] = tc;
                            view[i - 3] = tv;
                        }
                    }
                    for (int i = 0; i < host.length; i += 997) {
                        assertTrue(host[i] == v.getAtIndex(java.lang.foreign.ValueLayout.JAVA_FLOAT, i), "i=" + i);
                    }
                    Arrays.sort(copy);
                    Arrays.sort(view);
                    System.out.printf(Locale.ROOT, "result %.1f MB: read(float[]) %.2f ms, readView() %.2f ms%n",
                            host.length * 4 / 1e6, copy[RUNS / 2] / 1e6, view[RUNS / 2] / 1e6);
                }
            }
        }
    }

    /**
     * Вход в {@code GpuBuffer} того же размера (23.6 МБ): запись из {@code float[]} против заполнения вида и
     * {@code commitView()}: сама передача и передача вместе с заполнением (пользователь пишет значения в вид вместо
     * массива - честное сравнение включает заполнение у обоих путей). Содержимое буфера обязано совпадать; печатаются
     * медианы.
     */
    @Test
    void inputViewAvoidsTheArrayCopy() {
        Result<GpuContext> rc = GpuContext.builder().ignoreEnvironment().noDiskCache()
                .fallback(GpuContext.Fallback.NEVER).tryBuild();
        Assumptions.assumeTrue(rc.isOk(), "no OpenCL device");
        int n = new TerrainBatch(64, TerrainBatch.Sampling.DENSE).output.length;
        float[] host = new float[n];
        for (int i = 0; i < n; i++) {
            host[i] = i * 0.5f;
        }
        try (GpuContext ctx = rc.value();
             com.sixik.gpu.runtime.GpuBuffer in = ctx.allocate(com.sixik.gpu.ir.Types.F32, n)) {
            long[] copy = new long[RUNS];
            long[] commit = new long[RUNS];
            long[] fillWrite = new long[RUNS];
            long[] fillCommit = new long[RUNS];
            java.lang.foreign.MemorySegment w = in.writeView();
            for (int i = 0; i < RUNS + 3; i++) {
                long t = System.nanoTime();
                in.write(host);
                long tc = System.nanoTime() - t;
                t = System.nanoTime();
                in.commitView();
                long tv = System.nanoTime() - t;
                t = System.nanoTime();
                for (int j = 0; j < n; j++) {
                    host[j] = j * 0.5f;
                }
                in.write(host);
                long tfw = System.nanoTime() - t;
                t = System.nanoTime();
                for (int j = 0; j < n; j++) {
                    w.setAtIndex(java.lang.foreign.ValueLayout.JAVA_FLOAT, j, j * 0.5f);
                }
                in.commitView();
                long tf = System.nanoTime() - t;
                if (i >= 3) {
                    copy[i - 3] = tc;
                    commit[i - 3] = tv;
                    fillWrite[i - 3] = tfw;
                    fillCommit[i - 3] = tf;
                }
            }
            float[] back = new float[n];
            in.read(back);
            assertTrue(Arrays.equals(host, back), "the committed view must equal the array");
            Arrays.sort(copy);
            Arrays.sort(commit);
            Arrays.sort(fillWrite);
            Arrays.sort(fillCommit);
            System.out.printf(Locale.ROOT, "input %.1f MB: write(float[]) %.2f ms, commitView() %.2f ms;"
                            + " fill array + write %.2f ms, fill view + commitView() %.2f ms%n",
                    n * 4 / 1e6, copy[RUNS / 2] / 1e6, commit[RUNS / 2] / 1e6, fillWrite[RUNS / 2] / 1e6,
                    fillCommit[RUNS / 2] / 1e6);
        }
    }

    /**
     * Обычный вызов {@code TerrainNoise.generate(...)} через фасад (как пишет пользователь) против явного {@code ctx.launch}:
     * фасад переиспользует подготовленный запуск для тех же массивов. Печатает медианы.
     */
    @Test
    void plainJavaCallReusesThePreparedLaunch() {
        Result<GpuContext> rc = GpuContext.builder().ignoreEnvironment().noDiskCache()
                .fallback(GpuContext.Fallback.NEVER).tryBuild();
        Assumptions.assumeTrue(rc.isOk(), "no OpenCL device");
        try (GpuContext ctx = rc.value(); JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
            CompiledKernel k = ctx.compile(JavaToGpu.loadModule(TerrainNoise.class, "generate")).value();
            for (int chunks : new int[]{1, 8, 64}) {
                TerrainBatch batch = new TerrainBatch(chunks, TerrainBatch.Sampling.DENSE);
                TerrainBatch.Sampling g = batch.sampling;
                Object[] args = {batch.output, batch.chunkX, batch.chunkZ, g.horizontal, g.vertical, g.stepXZ, g.stepY,
                        batch.seed, batch.octaves};
                LaunchConfig cfg = LaunchConfig.of1D(batch.output.length);
                long[] plain = new long[RUNS];
                long[] call = new long[RUNS];
                for (int i = 0; i < RUNS + 3; i++) {
                    long t = System.nanoTime();
                    ctx.launch(k, cfg, args).value();
                    long tp = System.nanoTime() - t;
                    float[] viaLaunch = batch.output.clone();
                    t = System.nanoTime();
                    TerrainNoise.generate(batch.output, batch.chunkX, batch.chunkZ, g.horizontal, g.vertical,
                            g.stepXZ, g.stepY, batch.seed, batch.octaves);
                    long tc = System.nanoTime() - t;
                    assertArrayEquals(viaLaunch, batch.output, "the facade call must produce the same result");
                    if (i >= 3) {
                        plain[i - 3] = tp;
                        call[i - 3] = tc;
                    }
                }
                Arrays.sort(plain);
                Arrays.sort(call);
                System.out.printf(Locale.ROOT, "%2d chunks: ctx.launch %.3f ms, TerrainNoise.generate(...) %.3f ms%n",
                        chunks, plain[RUNS / 2] / 1e6, call[RUNS / 2] / 1e6);
            }
        }
    }

    /** Разбивка одного прохода GPU_RESIDENT по шагам (медианы), чтобы видеть, где тратится время. */
    @Test
    void residentPassBreakdown() {
        Result<GpuContext> rc = GpuContext.builder().ignoreEnvironment().noDiskCache()
                .fallback(GpuContext.Fallback.NEVER).tryBuild();
        Assumptions.assumeTrue(rc.isOk(), "no OpenCL device");
        try (GpuContext ctx = rc.value(); JavaToGpu.Scope s = JavaToGpu.use(ctx)) {
            for (TerrainBatch.Sampling sampling : TerrainBatch.Sampling.values()) {
                TerrainBatch batch = new TerrainBatch(64, sampling);
                TerrainBatch.Sampling g = batch.sampling;
                int n = batch.output.length;
                com.sixik.gpu.api.types.GpuFloatArray density = new com.sixik.gpu.api.types.GpuFloatArray(n);
                com.sixik.gpu.api.types.GpuIntArray cx = new com.sixik.gpu.api.types.GpuIntArray(batch.chunkX.length);
                com.sixik.gpu.api.types.GpuIntArray cz = new com.sixik.gpu.api.types.GpuIntArray(batch.chunkZ.length);
                com.sixik.gpu.api.types.GpuIntArray partial = new com.sixik.gpu.api.types.GpuIntArray(
                        TerrainExecutor.partials(n));
                long[][] t = new long[5][RUNS];
                for (int i = 0; i < RUNS + 3; i++) {
                    long t0 = System.nanoTime();
                    cx.copyFrom(batch.chunkX);
                    cz.copyFrom(batch.chunkZ);
                    long t1 = System.nanoTime();
                    TerrainNoise.generateResident(density, cx, cz, g.horizontal, g.vertical, g.stepXZ, g.stepY,
                            batch.seed, batch.octaves);
                    long t2 = System.nanoTime();
                    TerrainNoise.countSolid(partial, density);
                    long t3 = System.nanoTime();
                    int solid = 0;
                    for (int k = 0; k < partial.length(); k++) {
                        solid += partial.get(k);
                    }
                    long t4 = System.nanoTime();
                    assertTrue(solid >= 0);
                    if (i >= 3) {
                        t[0][i - 3] = t1 - t0;
                        t[1][i - 3] = t2 - t1;
                        t[2][i - 3] = t3 - t2;
                        t[3][i - 3] = t4 - t3;
                        t[4][i - 3] = t4 - t0;
                    }
                }
                String[] names = {"copyFrom", "generate call", "countSolid call", "read sums", "total"};
                StringBuilder sb = new StringBuilder();
                for (int p = 0; p < names.length; p++) {
                    Arrays.sort(t[p]);
                    sb.append(String.format(Locale.ROOT, "  %s %.3f", names[p], t[p][RUNS / 2] / 1e6));
                }
                System.out.println("resident " + sampling + ":" + sb);
                // время самих ядер (с ожиданием): запуск через GpuBuffer не откладывается
                CompiledKernel gen = ctx.compile(JavaToGpu.loadModule(TerrainNoise.class, "generateResident")).value();
                CompiledKernel cnt = ctx.compile(JavaToGpu.loadModule(TerrainNoise.class, "countSolid")).value();
                try (com.sixik.gpu.runtime.GpuBuffer bd = ctx.allocate(com.sixik.gpu.ir.Types.F32, n);
                     com.sixik.gpu.runtime.GpuBuffer bx = ctx.upload(batch.chunkX);
                     com.sixik.gpu.runtime.GpuBuffer bz = ctx.upload(batch.chunkZ);
                     com.sixik.gpu.runtime.GpuBuffer bp = ctx.allocate(com.sixik.gpu.ir.Types.I32, partial.length())) {
                    long[] kg = new long[RUNS];
                    long[] kc = new long[RUNS];
                    for (int i = 0; i < RUNS + 3; i++) {
                        LaunchStats a1 = ctx.launch(gen, LaunchConfig.of1D(n), bd, bx, bz, g.horizontal, g.vertical,
                                g.stepXZ, g.stepY, batch.seed, batch.octaves).value();
                        LaunchStats a2 = ctx.launch(cnt, LaunchConfig.of1D(partial.length()), bp, bd).value();
                        if (i >= 3) {
                            kg[i - 3] = a1.kernelNanos();
                            kc[i - 3] = a2.kernelNanos();
                        }
                    }
                    CompiledKernel old = ctx.compile(JavaToGpu.loadModule(TerrainNoise.class, "generate")).value();
                    long[] ko = new long[RUNS];
                    float[] host = new float[n];
                    for (int i = 0; i < RUNS + 3; i++) {
                        LaunchStats a0 = ctx.launch(old, LaunchConfig.of1D(n), bd, batch.chunkX, batch.chunkZ,
                                g.horizontal, g.vertical, g.stepXZ, g.stepY, batch.seed, batch.octaves).value();
                        if (i >= 3) {
                            ko[i - 3] = a0.kernelNanos();
                        }
                    }
                    Arrays.sort(ko);
                    System.out.printf(Locale.ROOT, "  generate (float[]) %.3f ms, regs %d / generateResident regs %d%n",
                            ko[RUNS / 2] / 1e6, old.driverStats().registers(), gen.driverStats().registers());
                    Arrays.sort(kg);
                    Arrays.sort(kc);
                    System.out.printf(Locale.ROOT, "  kernels %s: generateResident %.3f ms, countSolid %.3f ms%n",
                            sampling, kg[RUNS / 2] / 1e6, kc[RUNS / 2] / 1e6);
                }
            }
        }
    }

    @Test
    void terrainKernelWithAndWithoutOptimizer() throws IOException {
        for (TerrainBatch.Sampling sampling : TerrainBatch.Sampling.values()) {
            Measure base = measure("off-" + sampling, OptimizeOptions.NONE, sampling);
            Measure[] runs = {base,
                    measure("l1-" + sampling, OptimizeOptions.DEFAULT.withLevel(1), sampling),
                    measure("l2-" + sampling, OptimizeOptions.DEFAULT, sampling),
                    measure("l3-" + sampling, OptimizeOptions.DEFAULT.withLevel(3), sampling)};
            System.out.printf(Locale.ROOT, "%-14s %10s %10s %10s %5s %5s %7s %7s%n", "mode", "kernel_us", "upload_us",
                    "readbk_us", "regs", "est", "irNodes", "clChars");
            for (Measure r : runs) {
                System.out.printf(Locale.ROOT, "%-14s %10.1f %10.1f %10.1f %5d %5d %7d %7d%n", r.mode(),
                        r.kernelMedianNanos() / 1e3, r.uploadNanos() / 1e3, r.readbackNanos() / 1e3, r.registers(),
                        r.estimate(), r.irNodes(), r.sourceChars());
                assertArrayEquals(base.output(), r.output(), r.mode() + ": exact optimizations must not change results");
            }
        }
    }
}
