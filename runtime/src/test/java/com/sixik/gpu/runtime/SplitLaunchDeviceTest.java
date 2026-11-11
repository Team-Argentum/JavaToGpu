package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Деление ядра на стадии на настоящем драйвере: для синтетических ядер с {@code k} долгоживущими значениями сравниваются
 * целое ядро ({@link KernelSplitMode#OFF}) и разделённое ({@link KernelSplitMode#ALWAYS}) - регистры и проливание по
 * данным драйвера, время ядра и результат. Печатает таблицу и что выбрал {@link KernelSplitMode#AUTO}. Проверяет
 * только совпадение результатов (замер - исследование). Запуск: {@code ./gradlew :runtime:deviceTest}.
 */
@Tag("device")
class SplitLaunchDeviceTest {

    private static final int STRIDE = 512;
    private static final int THREADS = 1 << 14;
    private static final int REPEATS = 30;

    private record Run(CompiledKernel kernel, float[] out, double millis) {
    }

    @Test
    void splitVersusWholeKernel() {
        Result<GpuContext> probe = GpuContext.builder().ignoreEnvironment().noDiskCache().tryBuild();
        Assumptions.assumeTrue(probe.isOk(), "no OpenCL device");
        probe.value().close();
        float[][] in = SplitLaunchTest.inputs(THREADS, STRIDE);
        System.out.println("k  h   | whole: regs spill  ms    | split: stages regs spill  ms    | auto");
        String only = System.getProperty("split.modes", "OFF,ALWAYS,AUTO");
        for (int[] kh : cases()) {
            IrModule m = SplitLaunchTest.phased(kh[0], kh[1], STRIDE);
            Run whole = only.contains("OFF") ? run(m, in, KernelSplitMode.OFF) : null;
            Run split = only.contains("ALWAYS") ? run(m, in, KernelSplitMode.ALWAYS) : null;
            Run auto = only.contains("AUTO") ? run(m, in, KernelSplitMode.AUTO) : null;
            if (whole == null || split == null || auto == null) {
                continue;
            }
            assertEquals(1, whole.kernel().stageCount());
            for (int i = 0; i < THREADS; i++) {
                float a = whole.out()[i];
                float b = split.out()[i];
                assertTrue(Math.abs(a - b) <= 1e-5f * Math.max(1f, Math.abs(a)),
                        "k=" + kh[0] + " item " + i + ": whole " + a + ", split " + b);
            }
            DriverStats ws = whole.kernel().driverStats();
            StringBuilder stageRegs = new StringBuilder();
            long stageSpill = 0;
            for (DriverStats s : split.kernel().stageDriverStats()) {
                stageRegs.append(stageRegs.isEmpty() ? "" : "/").append(s.registers());
                stageSpill += Math.max(0, s.spillStoreBytes()) + Math.max(0, s.spillLoadBytes());
            }
            System.out.printf(Locale.ROOT, "%-3d%-4d | %4d %6d %7.3f | %d %-12s %6d %7.3f | %s (%.3f ms)%n",
                    kh[0], kh[1], ws.registers(), Math.max(0, ws.spillStoreBytes()) + Math.max(0, ws.spillLoadBytes()),
                    whole.millis(), split.kernel().stageCount(), stageRegs, stageSpill, split.millis(),
                    auto.kernel().stageCount() > 1 ? "split" : "whole", auto.millis());
        }
    }

    /** Случаи {@code k,h}; переопределяются свойством {@code split.cases} ({@code "40x24;300x40"}). */
    private static int[][] cases() {
        String v = System.getProperty("split.cases", "40x24;160x32;300x40;480x48");
        return Arrays.stream(v.split(";")).map(c -> c.split("x"))
                .map(c -> new int[]{Integer.parseInt(c[0].strip()), Integer.parseInt(c[1].strip())}).toArray(int[][]::new);
    }

    /** Собирает ядро в новом контексте с заданным режимом и замеряет медиану времени ядра (входы уже на устройстве). */
    private static Run run(IrModule m, float[][] in, KernelSplitMode mode) {
        try (GpuContext ctx = GpuContext.builder().ignoreEnvironment().noDiskCache().splitKernels(mode).build()) {
            Result<CompiledKernel> rk = ctx.compile(m);
            assertTrue(rk.isOk(), rk.diagnostics().toString());
            CompiledKernel k = rk.value();
            GpuBuffer in0 = ctx.allocate(Types.F32, in[0].length);
            GpuBuffer in1 = ctx.allocate(Types.F32, in[1].length);
            GpuBuffer out = ctx.allocate(Types.F32, THREADS);
            in0.write(in[0]);
            in1.write(in[1]);
            double[] ms = new double[REPEATS];
            try (PreparedLauncher p = ctx.prepare(k, in0, in1, out).value()) {
                for (int i = 0; i < 3; i++) {
                    p.run(LaunchConfig.of1D(THREADS));
                }
                for (int i = 0; i < REPEATS; i++) {
                    ms[i] = p.run(LaunchConfig.of1D(THREADS)).kernelNanos() / 1e6;
                }
            }
            Arrays.sort(ms);
            float[] result = new float[THREADS];
            out.read(result);
            return new Run(k, result, ms[REPEATS / 2]);
        }
    }
}
