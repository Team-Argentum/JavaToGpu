package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.interpreter.samples.SampleArgs;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import com.sixik.gpu.runtime.device.DeviceApi;
import com.sixik.gpu.runtime.device.DeviceInfo;
import com.sixik.gpu.runtime.device.OpenClApi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Рантайм на настоящем OpenCL-устройстве: найти устройство, скомпилировать, залить буферы, запустить, забрать
 * результат. Без драйвера тесты пропускаются. Запуск: {@code ./gradlew :runtime:deviceTest}.
 */
@Tag("device")
class RuntimeDeviceTest {

    private static GpuContext ctx;
    private static final List<GpuEvent> EVENTS = new ArrayList<>();

    @BeforeAll
    static void open() {
        Result<GpuContext> r = GpuContext.builder().listener(EVENTS::add).tryBuild();
        Assumptions.assumeTrue(r.isOk(), "no OpenCL device: " + r.diagnostics());
        ctx = r.value();
        System.out.println("OpenCL device: " + ctx.device().describe() + ", driver " + ctx.device().driverVersion());
    }

    @AfterAll
    static void close() {
        if (ctx != null) {
            ctx.close();
        }
    }

    // ---- критерий M6: computeSine на реальном OpenCL --------------------------------------------

    @Test
    void computeSineRunsOnTheDevice() {
        int n = 1024;
        float[] in = new float[n];
        float[] out = new float[n];
        Random rnd = new Random(1);
        for (int i = 0; i < n; i++) {
            in[i] = (rnd.nextFloat() - 0.5f) * 20f;
        }
        float[] inBefore = in.clone();
        LaunchStats stats = ctx.run(Fixtures.computeSine(), LaunchConfig.of1D(n), in, out);
        for (int i = 0; i < n; i++) {
            float expected = (float) Math.sin(inBefore[i]) + 2f;
            assertTrue(ulps(expected, out[i]) <= 4, "i=" + i + ": expected " + expected + ", got " + out[i]);
        }
        assertEquals(n * 4L * 2, stats.bytesUp());
        assertEquals(n * 4L, stats.bytesDown());
        assertTrue(stats.kernelNanos() > 0);
        org.junit.jupiter.api.Assertions.assertArrayEquals(inBefore, in, "the input array is untouched");
    }

    @Test
    void secondRunComesFromTheCache() {
        IrModule m = Fixtures.computeSine();
        CacheStats before = ctx.cacheStats();
        ctx.run(m, LaunchConfig.of1D(256), new float[256], new float[256]);
        ctx.run(m, LaunchConfig.of1D(256), new float[256], new float[256]);
        CacheStats after = ctx.cacheStats();
        assertTrue(after.hits() - before.hits() >= 1);
        assertTrue(after.misses() - before.misses() <= 1);
    }

    @Test
    void deviceBuffersKeepDataBetweenLaunches() {
        int n = 512;
        float[] in = new float[n];
        for (int i = 0; i < n; i++) {
            in[i] = i * 0.01f;
        }
        try (GpuBuffer a = ctx.upload(in); GpuBuffer b = ctx.allocate(Types.F32, n); GpuBuffer c = ctx.allocate(Types.F32, n)) {
            IrModule m = Fixtures.computeSine();
            CompiledKernel k = ctx.compile(m).value();
            LaunchStats first = ctx.launch(k, LaunchConfig.of1D(n), a, b).value();
            LaunchStats second = ctx.launch(k, LaunchConfig.of1D(n), b, c).value();
            assertEquals(0, first.bytesUp() + first.bytesDown() + second.bytesUp() + second.bytesDown());
            float[] out = new float[n];
            c.read(out);
            for (int i = 0; i < n; i++) {
                double inner = (float) Math.sin(in[i]) + 2f;
                float expected = (float) Math.sin(inner) + 2f;
                assertTrue(ulps(expected, out[i]) <= 8, "i=" + i);
            }
        }
    }

    @Test
    void concurrentLaunchesFromManyThreads() throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        try {
            List<java.util.concurrent.Future<Boolean>> all = new ArrayList<>();
            for (int t = 0; t < 12; t++) {
                final int seed = t;
                all.add(pool.submit(() -> {
                    float[] in = new float[256];
                    float[] out = new float[256];
                    for (int i = 0; i < in.length; i++) {
                        in[i] = seed + i * 0.01f;
                    }
                    ctx.run(Fixtures.computeSine(), LaunchConfig.of1D(256), in, out);
                    for (int i = 0; i < in.length; i++) {
                        if (ulps((float) Math.sin(in[i]) + 2f, out[i]) > 4) {
                            return false;
                        }
                    }
                    return true;
                }));
            }
            for (java.util.concurrent.Future<Boolean> f : all) {
                assertTrue(f.get());
            }
        } finally {
            pool.shutdown();
        }
    }

    // ---- образцы-ядра против интерпретатора -----------------------------------------------------

    static Stream<String> sampleNames() {
        return SampleKernels.all().stream().map(SampleKernels.Kernel::label)
                .filter(l -> !SampleArgs.notComparableOnDevice(l));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sampleNames")
    void sampleKernelsMatchTheInterpreter(String label) {
        SampleKernels.Kernel k = SampleKernels.all().stream().filter(x -> x.label().equals(label)).findFirst().orElseThrow();
        IrModule m = k.module();
        int f = m.functionCount() - 1;
        boolean grouped = m.fReqWorkGroup(f, 0) > 0;
        NdRange range = grouped ? NdRange.of2D(8, 4) : NdRange.of1D(16);
        LaunchConfig config = grouped ? LaunchConfig.of2D(8, 4) : LaunchConfig.of1D(16, 1);
        Supplier<Object[]> args = SampleArgs.random(k, 99);
        Object[] expected = args.get();
        Object[] actual = args.get();
        Result<Interpreter.Execution> ref = Interpreter.run(m, range, expected);
        assertTrue(ref.isOk(), label + ": " + ref.diagnostics());
        ctx.run(m, config, actual);
        boolean floats = SampleArgs.hasFloatingPoint(k);
        for (int i = 0; i < expected.length; i++) {
            compare(label + " arg" + i, expected[i], actual[i], floats ? 4 : 0);
        }
    }

    // ---- устройство и сборка --------------------------------------------------------------------

    @Test
    void enumerationSeesTheSelectedDevice() {
        DeviceApi api = OpenClApi.load();
        assertFalse(api == null);
        try {
            List<DeviceInfo> all = DeviceInfo.enumerate(api);
            assertFalse(all.isEmpty());
            assertTrue(all.stream().anyMatch(d -> d.name().equals(ctx.device().name())));
            assertTrue(ctx.device().globalMemory() > 0 && ctx.device().maxWorkGroupSize() > 0);
        } finally {
            api.close();
        }
    }

    @Test
    void driverBuildLogBecomesDiagnostics() {
        DeviceApi api = OpenClApi.load();
        long platform = api.platforms()[0];
        DeviceInfo info = DeviceInfo.enumerate(api).get(0);
        long context = api.createContext(info.handle());
        long program = api.createProgram(context, "__kernel void k(__global int* a) {\n    a[0] = undeclared_name;\n}\n");
        DeviceApi.BuildOutcome outcome = api.build(program, info.handle(), "");
        try {
            assertFalse(outcome.ok());
            List<Diagnostic> d = BuildLog.parse(outcome.log(), 0, true);
            assertFalse(d.isEmpty(), outcome.log());
            assertEquals("RT-C01", d.get(0).code());
            assertTrue(platform != 0);
        } finally {
            api.release(DeviceApi.HandleKind.PROGRAM, program);
            api.release(DeviceApi.HandleKind.CONTEXT, context);
            api.close();
        }
    }

    @Test
    void launchSizeErrorsComeBackAsValues() {
        CompiledKernel k = ctx.compile(Fixtures.computeSine()).value();
        Result<LaunchStats> r = ctx.launch(k, LaunchConfig.of1D(100), new float[100], new float[100]);
        assertFalse(r.isOk());
        assertEquals("RT-L01", r.diagnostics().get(0).code());
    }

    // ---- сравнение ------------------------------------------------------------------------------

    private static long ulps(float a, float b) {
        if (Float.isNaN(a) || Float.isNaN(b)) {
            return Float.isNaN(a) && Float.isNaN(b) ? 0 : Long.MAX_VALUE;
        }
        return Math.abs(ordered(Float.floatToRawIntBits(a)) - ordered(Float.floatToRawIntBits(b)));
    }

    private static long ordered(int bits) {
        return bits >= 0 ? bits : Integer.MIN_VALUE - (long) bits;
    }

    private static void compare(String name, Object e, Object a, int ulps) {
        if (!e.getClass().isArray()) {
            return;
        }
        int n = java.lang.reflect.Array.getLength(e);
        for (int i = 0; i < n; i++) {
            Object x = java.lang.reflect.Array.get(e, i);
            Object y = java.lang.reflect.Array.get(a, i);
            if (x instanceof Float fx && y instanceof Float fy) {
                if (Float.floatToRawIntBits(fx) != Float.floatToRawIntBits(fy)
                        && (ulps == 0 || fx == 0f || fy == 0f || ulps(fx, fy) > ulps)
                        && !(Float.isNaN(fx) && Float.isNaN(fy))) {
                    fail(name + "[" + i + "]: expected " + fx + ", got " + fy);
                }
            } else if (x instanceof Double dx && y instanceof Double dy) {
                boolean nan = Double.isNaN(dx) && Double.isNaN(dy);
                long bx = Double.doubleToRawLongBits(dx);
                long by = Double.doubleToRawLongBits(dy);
                long dist = Math.abs((bx >= 0 ? bx : Long.MIN_VALUE - bx) - (by >= 0 ? by : Long.MIN_VALUE - by));
                if (!nan && bx != by && (ulps == 0 || dx == 0d || dy == 0d || dist > ulps)) {
                    fail(name + "[" + i + "]: expected " + dx + ", got " + dy);
                }
            } else if (x != null && !x.getClass().getName().startsWith("java.")) {
                String why = SampleArgs.objectDifference(x, y, ulps);
                if (why != null) {
                    fail(name + "[" + i + "]: " + why);
                }
            } else {
                assertEquals(x, y, name + "[" + i + "]");
            }
        }
    }
}
