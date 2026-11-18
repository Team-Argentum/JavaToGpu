package com.sixik.gpu.runtime.lwjgl;

import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.interpreter.samples.SampleArgs;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import com.sixik.gpu.runtime.GpuBuffer;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.LaunchConfig;
import com.sixik.gpu.runtime.device.DeviceApi;
import com.sixik.gpu.runtime.device.DeviceInfo;
import com.sixik.gpu.runtime.device.HostMemory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Рантайм на настоящем OpenCL-устройстве через LWJGL-бэкенд: те же проверки, что у FFM-бэкенда в
 * {@code :runtime:deviceTest}, в уменьшенном объёме. Без драйвера пропускаются. Запуск:
 * {@code ./gradlew :runtime-opencl-lwjgl:deviceTest}.
 */
@Tag("device")
class LwjglDeviceTest {

    private static DeviceApi api;
    private static GpuContext ctx;

    @BeforeAll
    static void open() {
        api = LwjglOpenClApi.load();
        Assumptions.assumeTrue(api != null, "no OpenCL library");
        Result<GpuContext> r = GpuContext.builder().api(api).noDiskCache().tryBuild();
        Assumptions.assumeTrue(r.isOk(), "no OpenCL device: " + r.diagnostics());
        ctx = r.value();
        System.out.println("LWJGL OpenCL device: " + ctx.device().describe() + ", driver " + ctx.device().driverVersion());
    }

    @AfterAll
    static void close() {
        if (ctx != null) {
            ctx.close();
        }
        if (api != null) {
            api.close();
        }
    }

    @Test
    void enumerationAndStringsWork() {
        List<DeviceInfo> all = DeviceInfo.enumerate(api);
        assertFalse(all.isEmpty());
        assertTrue(all.stream().anyMatch(d -> d.name().equals(ctx.device().name())));
        assertFalse(ctx.device().name().isEmpty());
        assertTrue(ctx.device().globalMemory() > 0 && ctx.device().maxWorkGroupSize() > 0);
    }

    @Test
    void computeSineRunsOnTheDevice() {
        int n = 1024;
        float[] in = new float[n];
        float[] out = new float[n];
        Random rnd = new Random(1);
        for (int i = 0; i < n; i++) {
            in[i] = (rnd.nextFloat() - 0.5f) * 20f;
        }
        ctx.run(Fixtures.computeSine(), LaunchConfig.of1D(n), in, out);
        for (int i = 0; i < n; i++) {
            float expected = (float) Math.sin(in[i]) + 2f;
            assertTrue(Math.abs(expected - out[i]) <= 4 * Math.ulp(expected), "i=" + i);
        }
    }

    @Test
    void largeTransfersGoThroughPinnedMemoryAndViews() {
        int n = 1 << 20; // больше порога закреплённой памяти
        float[] data = new float[n];
        for (int i = 0; i < n; i++) {
            data[i] = i * 0.5f;
        }
        try (GpuBuffer b = ctx.allocate(Types.F32, n)) {
            b.write(data);
            float[] back = new float[n];
            b.read(back);
            assertArrayEquals(data, back);
            HostMemory v = b.readView();
            assertEquals(data[n - 1], v.getFloat(4L * (n - 1)));
            HostMemory w = b.writeView();
            w.setFloat(0, -1f);
            b.commitView();
            b.read(back);
            assertEquals(-1f, back[0]);
        }
    }

    /**
     * Целочисленные образцы - побитово как интерпретатор. Расхождения собираются списком, чтобы сравнить его со списком
     * FFM-бэкенда на том же драйвере ({@code :runtime:deviceTest}): бэкенд не должен добавлять своих.
     */
    @Test
    void sampleKernelsMatchTheInterpreter() {
        int checked = 0;
        java.util.List<String> mismatches = new java.util.ArrayList<>();
        for (SampleKernels.Kernel k : SampleKernels.all()) {
            if (SampleArgs.notComparableOnDevice(k.label())) {
                continue;
            }
            IrModule m = k.module();
            int f = m.functionCount() - 1;
            boolean grouped = m.fReqWorkGroup(f, 0) > 0;
            NdRange range = grouped ? NdRange.of2D(8, 4) : NdRange.of1D(16);
            LaunchConfig config = grouped ? LaunchConfig.of2D(8, 4) : LaunchConfig.of1D(16, 1);
            Supplier<Object[]> args = SampleArgs.random(k, 99);
            Object[] expected = args.get();
            Object[] actual = args.get();
            Result<Interpreter.Execution> ref = Interpreter.run(m, range, expected);
            assertTrue(ref.isOk(), k.label() + ": " + ref.diagnostics());
            ctx.run(m, config, actual);
            if (!SampleArgs.hasFloatingPoint(k)) {
                for (int i = 0; i < expected.length; i++) {
                    if (expected[i] != null && expected[i].getClass().isArray()) {
                        if (!Arrays.deepEquals(new Object[]{expected[i]}, new Object[]{actual[i]})) {
                            mismatches.add(k.label() + " arg" + i);
                        }
                    }
                }
            }
            checked++;
        }
        assertTrue(checked > 0);
        assertTrue(mismatches.isEmpty(), "differs from the interpreter: " + mismatches);
    }
}
