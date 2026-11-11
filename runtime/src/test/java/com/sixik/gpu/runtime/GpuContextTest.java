package com.sixik.gpu.runtime;

import com.sixik.gpu.codegen.opencl.EmitOptions;
import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link GpuContext} на фейковом {@link DeviceApi}: всё, что можно проверить без железа. */
class GpuContextTest {

    // ---- вспомогательное ------------------------------------------------------------------------

    private static FakeDeviceApi sineDevice() {
        return FakeDeviceApi.withGpu().onKernel("k0", l -> {
            ByteBuffer in = l.buffer(0);
            ByteBuffer out = l.buffer(2);
            int n = l.intArg(1);
            for (int i = 0; i < n; i++) {
                out.putFloat(4 * i, (float) Math.sin(in.getFloat(4 * i)) + 2f);
            }
        });
    }

    private static GpuContext open(FakeDeviceApi fake, EventListener listener) {
        return GpuContext.builder().api(fake).ignoreEnvironment().listener(listener).build();
    }

    private static GpuContext open(FakeDeviceApi fake) {
        return open(fake, EventListener.NONE);
    }

    private static String code(Result<?> r) {
        assertFalse(r.isOk());
        return r.diagnostics().get(0).code();
    }

    /** Ядро {@code out[i] = in[i] * k + n} с параметрами: массив, массив, int, float. */
    private static IrModule scalarsKernel() {
        IrBuilder b = new IrBuilder();
        int a = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, a, a, Types.I32, Types.F32);
        b.nameFunction("scaled");
        int g = b.declareLocal(Types.I32);
        b.endFunction(b.block(
                b.localDecl(g, b.intrinsic(Fixtures.GET_GLOBAL_ID, b.constI32(0))),
                b.store(b.elemPtr(b.param(1), b.localGet(g)),
                        b.binary(Op.ADD, b.binary(Op.MUL, b.load(b.elemPtr(b.param(0), b.localGet(g))), b.param(3)),
                                b.convert(Types.F32, b.param(2))))));
        return b.build();
    }

    // ---- создание и выбор устройства ------------------------------------------------------------

    @Test
    void selectsTheDeviceAndReportsIt() {
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = open(FakeDeviceApi.withGpu(), events::add)) {
            assertEquals("Fake GPU", ctx.device().name());
            assertFalse(ctx.isClosed());
        }
        assertEquals(GpuEvent.Kind.DEVICE_SELECTED, events.get(0).kind());
        assertTrue(events.get(0).subject().contains("Fake GPU"));
        assertEquals(GpuEvent.Kind.CLOSED, events.get(events.size() - 1).kind());
    }

    @Test
    void noDriverIsAValueNotAnException() {
        Result<GpuContext> r = GpuContext.builder().api(new FakeDeviceApi()).ignoreEnvironment().tryBuild();
        assertEquals("RT-D01", code(r));
        assertTrue(r.diagnostics().get(0).message().contains("no OpenCL devices"));
        GpuException e = assertThrows(GpuException.class,
                () -> GpuContext.builder().api(new FakeDeviceApi()).ignoreEnvironment().build());
        assertEquals("RT-D01", e.diagnostics().get(0).code());
        assertTrue(e.getMessage().contains("RT-D01"));
    }

    @Test
    void policyThatMatchesNothingExplainsWhy() {
        FakeDeviceApi fake = new FakeDeviceApi(FakeDeviceApi.Spec.cpu());
        Result<GpuContext> r = GpuContext.builder().api(fake).ignoreEnvironment()
                .policy(com.sixik.gpu.runtime.device.DevicePolicy.DEFAULT
                        .withKind(com.sixik.gpu.runtime.device.DevicePolicy.Kind.GPU)).tryBuild();
        assertEquals("RT-D01", code(r));
        assertTrue(r.diagnostics().get(0).message().contains("Fake CPU"), r.diagnostics().get(0).message());
        assertEquals(0, fake.liveContexts(), "the driver got no extra context");
    }

    @Test
    void driverFailureDuringSelectionIsReported() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu().failNext("createQueue", -6);
        Result<GpuContext> r = GpuContext.builder().api(fake).ignoreEnvironment().tryBuild();
        assertEquals("RT-D03", code(r));
        assertEquals(0, fake.liveContexts(), "the driver context was released on failure");
    }

    @Test
    void policyPicksGpuBeforeCpuAndOverrideWins() {
        FakeDeviceApi fake = new FakeDeviceApi(FakeDeviceApi.Spec.cpu(), FakeDeviceApi.Spec.gpu());
        try (GpuContext ctx = open(fake)) {
            assertEquals("Fake GPU", ctx.device().name());
        }
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment()
                .policy(com.sixik.gpu.runtime.device.DevicePolicy.DEFAULT.withOverride("0")).build()) {
            assertEquals("Fake CPU", ctx.device().name());
        }
    }

    @Test
    void contextDoesNotCloseAnApiItDidNotCreate() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        open(fake).close();
        assertFalse(fake.isClosed());
        assertEquals(0, fake.liveContexts());
        assertEquals(0, fake.liveQueues());
    }

    // ---- компиляция и кэш -----------------------------------------------------------------------

    @Test
    void compilesOnceAndServesFromCache() {
        FakeDeviceApi fake = sineDevice();
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = open(fake, events::add)) {
            Result<CompiledKernel> a = ctx.compile(Fixtures.computeSine());
            Result<CompiledKernel> b = ctx.compile(Fixtures.computeSine());
            assertTrue(a.isOk(), a.diagnostics().toString());
            assertSame(a.value(), b.value());
            assertEquals(1, fake.builds());
            assertEquals(1, fake.sources().size());
            assertEquals(new CacheStats(1, 1, 1), ctx.cacheStats());
            assertEquals("k0", a.value().artifact().entry());
            assertEquals(2, a.value().paramCount());
            long compileEvents = events.stream().filter(e -> e.kind() == GpuEvent.Kind.COMPILE).count();
            assertEquals(2, compileEvents);
            assertEquals("cache-hit", events.stream().filter(e -> e.kind() == GpuEvent.Kind.COMPILE)
                    .reduce((x, y) -> y).orElseThrow().detail());
        }
        assertEquals(0, fake.livePrograms());
        assertEquals(0, fake.liveKernels());
    }

    @Test
    void differentOptionsAreDifferentCacheEntries() {
        FakeDeviceApi fake = sineDevice();
        try (GpuContext ctx = open(fake)) {
            IrModule m = Fixtures.computeSine();
            int k = m.functionCount() - 1;
            assertTrue(ctx.compile(m, k, EmitOptions.RELEASE).isOk());
            assertTrue(ctx.compile(m, k, EmitOptions.DEBUG).isOk());
            assertTrue(ctx.compile(m, k, EmitOptions.RELEASE.withFastMath(true)).isOk());
            assertEquals(3, fake.builds());
            assertEquals(List.of("", "", "-cl-fast-relaxed-math"), fake.buildOptions());
        }
    }

    @Test
    void sameIrWithDifferentDebugInfoSharesTheRelease() {
        FakeDeviceApi fake = sineDevice();
        try (GpuContext ctx = open(fake)) {
            assertTrue(ctx.compile(Fixtures.computeSine()).isOk());
            assertTrue(ctx.compile(Fixtures.computeSineWithoutDebug()).isOk());
            assertEquals(1, fake.builds());
        }
    }

    @Test
    void concurrentCompilesOfOneKernelBuildOnce() throws Exception {
        FakeDeviceApi fake = sineDevice();
        try (GpuContext ctx = open(fake); ExecutorService pool = Executors.newFixedThreadPool(8)) {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Result<CompiledKernel>>> all = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                all.add(pool.submit(() -> {
                    go.await();
                    return ctx.compile(Fixtures.computeSine());
                }));
            }
            go.countDown();
            CompiledKernel first = all.get(0).get().value();
            for (Future<Result<CompiledKernel>> f : all) {
                assertSame(first, f.get().value());
            }
            assertEquals(1, fake.builds());
            assertEquals(7, ctx.cacheStats().hits());
        }
    }

    @Test
    void deviceWithoutFp64RejectsDoubleKernelBeforeCompiling() {
        FakeDeviceApi fake = new FakeDeviceApi(FakeDeviceApi.Spec.gpu().withExtensions(""));
        IrBuilder b = new IrBuilder();
        int a = b.array(AddressSpace.GLOBAL, Types.F64);
        b.beginFunction(IrModule.KERNEL, Types.VOID, a);
        b.nameFunction("dbl");
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constF64(1.5))));
        try (GpuContext ctx = open(fake)) {
            Result<CompiledKernel> r = ctx.compile(b.build());
            assertEquals("RT-D02", code(r));
            assertTrue(r.diagnostics().get(0).message().contains("FP64"), r.diagnostics().get(0).message());
            assertTrue(r.diagnostics().get(0).message().contains("Fake GPU"));
            assertEquals(0, fake.builds(), "compilation did not even start");
            assertEquals(0, fake.sources().size());
        }
    }

    @Test
    void driverBuildErrorBecomesParsedDiagnostics() {
        String log = "<kernel>:12:5: error: use of undeclared identifier 'foo'\n    foo(1);\n    ^\n"
                + "<kernel>:3:1: warning: unused function\n";
        FakeDeviceApi fake = sineDevice().rejectBuildWhen(s -> true, log);
        try (GpuContext ctx = open(fake)) {
            Result<CompiledKernel> r = ctx.compile(Fixtures.computeSine());
            assertFalse(r.isOk());
            assertEquals(2, r.diagnostics().size());
            Diagnostic err = r.diagnostics().get(0);
            assertEquals("RT-C01", err.code());
            assertTrue(err.isError());
            assertTrue(err.message().startsWith("line 12:5: use of undeclared identifier"), err.message());
            assertTrue(err.message().contains("foo(1);"), "the code quote is attached");
            assertEquals("RT-C02", r.diagnostics().get(1).code());
            assertFalse(r.diagnostics().get(1).isError());
            assertEquals(0, fake.livePrograms(), "the program was released after the failure");
            // неудача тоже кэшируется: повторять заведомо провальную сборку бессмысленно
            assertEquals("RT-C01", code(ctx.compile(Fixtures.computeSine())));
            assertEquals(1, fake.builds());
        }
    }

    @Test
    void buildWarningsAreEventsAndDiagnosticsOfASuccessfulResult() {
        FakeDeviceApi fake = sineDevice().successLog("<kernel>:7:3: warning: unused variable 'x'");
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = open(fake, events::add)) {
            Result<CompiledKernel> r = ctx.compile(Fixtures.computeSine());
            assertTrue(r.isOk());
            assertEquals(1, r.diagnostics().size());
            assertEquals("RT-C02", r.diagnostics().get(0).code());
        }
        assertTrue(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.WARNING && e.detail().contains("unused variable")));
    }

    @Test
    void codegenErrorsPassThrough() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.specConst(0, Types.I32)))); // spec_const вне v1: CG-U01
        try (GpuContext ctx = open(FakeDeviceApi.withGpu())) {
            assertEquals("CG-U01", code(ctx.compile(b.build())));
        }
    }

    @Test
    void driverFailureWhileCompilingIsReported() {
        FakeDeviceApi fake = sineDevice().failNext("createKernel", -46);
        try (GpuContext ctx = open(fake)) {
            assertEquals("RT-X01", code(ctx.compile(Fixtures.computeSine())));
            assertEquals(0, fake.livePrograms());
        }
    }

    @Test
    void nullsAreProgrammingErrors() {
        try (GpuContext ctx = open(sineDevice())) {
            assertThrows(IllegalArgumentException.class, () -> ctx.compile(null, 0, EmitOptions.RELEASE));
            assertThrows(IllegalArgumentException.class, () -> ctx.compile(Fixtures.computeSine(), 0, null));
            assertThrows(IllegalArgumentException.class, () -> ctx.launch(null, LaunchConfig.of1D(1)));
            CompiledKernel k = ctx.compile(Fixtures.computeSine()).value();
            assertThrows(IllegalArgumentException.class, () -> ctx.launch(k, null));
        }
    }

    // ---- запуск ---------------------------------------------------------------------------------

    @Test
    void computeSineEndToEnd() {
        FakeDeviceApi fake = sineDevice();
        List<GpuEvent> events = new ArrayList<>();
        float[] in = new float[256];
        float[] out = new float[256];
        for (int i = 0; i < in.length; i++) {
            in[i] = i * 0.1f;
        }
        float[] inCopy = in.clone();
        try (GpuContext ctx = open(fake, events::add)) {
            LaunchStats stats = ctx.run(Fixtures.computeSine(), LaunchConfig.of1D(256), in, out);
            assertEquals(1024, stats.bytesUp(), "only in is uploaded: out[getGlobalId(0)] with global == length "
                    + "overwrites every element, so its host content is not needed (WriteCoverage)");
            assertEquals(1024, stats.bytesDown(), "only the writable array is returned");
            assertTrue(stats.kernelNanos() >= 0);
        }
        for (int i = 0; i < in.length; i++) {
            assertEquals((float) Math.sin(inCopy[i]) + 2f, out[i], 0f, "i=" + i);
        }
        assertArrayEquals(inCopy, in, "the input array is untouched");
        assertEquals(1, fake.writes(), "out is overwritten entirely and not uploaded");
        assertEquals(1, fake.reads());
        assertEquals(1, fake.enqueues());
        assertEquals(0, fake.liveBuffers(), "temporary buffers were released");
        List<GpuEvent.Kind> kinds = events.stream().map(GpuEvent::kind).toList();
        assertEquals(List.of(GpuEvent.Kind.DEVICE_SELECTED, GpuEvent.Kind.COMPILE, GpuEvent.Kind.UPLOAD,
                GpuEvent.Kind.KERNEL, GpuEvent.Kind.READBACK, GpuEvent.Kind.CLOSED), kinds);
    }

    @Test
    void kernelArgumentsAreBoundInTheOrderOfTheArtifact() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu().onKernel("k0", l -> {
            // p0 = in (буфер, длина), p1 = out (буфер, длина), p2 = int, p3 = float
            assertEquals(4, l.intArg(1));
            assertEquals(4, l.intArg(3));
            assertEquals(3, l.intArg(4));
            assertEquals(0.5f, l.floatArg(5));
            for (int i = 0; i < 4; i++) {
                l.buffer(2).putFloat(4 * i, l.buffer(0).getFloat(4 * i) * l.floatArg(5) + l.intArg(4));
            }
        });
        float[] in = {1, 2, 3, 4};
        float[] out = new float[4];
        try (GpuContext ctx = open(fake)) {
            ctx.run(scalarsKernel(), LaunchConfig.of1D(4), in, out, 3, 0.5f);
        }
        assertArrayEquals(new float[]{3.5f, 4f, 4.5f, 5f}, out);
    }

    @Test
    void sameArrayPassedTwiceIsOneBuffer() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu().onKernel("k0", l -> {
            assertEquals(l.buffer(0).array(), l.buffer(2).array(), "the same buffer");
            for (int i = 0; i < 4; i++) {
                l.buffer(2).putFloat(4 * i, l.buffer(0).getFloat(4 * i) * 2f + 3);
            }
        });
        float[] data = {1, 2, 3, 4};
        try (GpuContext ctx = open(fake)) {
            ctx.run(scalarsKernel(), LaunchConfig.of1D(4), data, data, 3, 2f);
        }
        assertEquals(1, fake.writes(), "aliasing is preserved: one array - one buffer");
        assertArrayEquals(new float[]{5, 7, 9, 11}, data);
    }

    @Test
    void failedLaunchLeavesHostArraysUntouched() {
        FakeDeviceApi fake = sineDevice().failNext("enqueue", -5);
        float[] in = {1, 2};
        float[] out = {7, 7};
        try (GpuContext ctx = open(fake)) {
            Result<CompiledKernel> k = ctx.compile(Fixtures.computeSine());
            Result<LaunchStats> r = ctx.launch(k.value(), LaunchConfig.of1D(256, 256), in, out);
            assertEquals("RT-X01", code(r));
        }
        assertArrayEquals(new float[]{7, 7}, out);
        assertEquals(0, fake.liveBuffers(), "temporary buffers were released after the error too");
    }

    @Test
    void emptyArraysAreAllowed() {
        FakeDeviceApi fake = sineDevice();
        try (GpuContext ctx = open(fake)) {
            ctx.run(Fixtures.computeSine(), LaunchConfig.of1D(256), new float[0], new float[0]);
        }
        assertEquals(0, fake.liveBuffers());
    }

    /** Ядро {@code a[g] = 5; (b читается, но не пишется)}: первый параметр только пишется, второй - только читается. */
    private static IrModule writeThenReadKernel() {
        IrBuilder b = new IrBuilder();
        int a = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, a, a);
        b.nameFunction("wr");
        int g = b.declareLocal(Types.I32);
        int tmp = b.declareLocal(Types.F32);
        b.endFunction(b.block(
                b.localDecl(g, b.intrinsic(Fixtures.GET_GLOBAL_ID, b.constI32(0))),
                b.localDecl(tmp, b.load(b.elemPtr(b.param(1), b.localGet(g)))),
                b.store(b.elemPtr(b.param(0), b.localGet(g)), b.binary(Op.ADD, b.localGet(tmp), b.constF32(5f)))));
        return b.build();
    }

    @Test
    void aliasedArrayIsReadBackIfAnyParameterWritesIt() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu().onKernel("k0", l -> {
            for (int i = 0; i < 4; i++) {
                l.buffer(0).putFloat(4 * i, l.buffer(2).getFloat(4 * i) + 5f);
            }
        });
        float[] data = {1, 2, 3, 4};
        try (GpuContext ctx = open(fake)) {
            ctx.run(writeThenReadKernel(), LaunchConfig.of1D(4), data, data);
            assertArrayEquals(new float[]{6, 7, 8, 9}, data, "the write through the first parameter returned to the host");
            float[] written = new float[4];
            float[] source = {1, 1, 1, 1};
            ctx.run(writeThenReadKernel(), LaunchConfig.of1D(4), written, source);
            assertArrayEquals(new float[]{6, 6, 6, 6}, written);
            assertArrayEquals(new float[]{1, 1, 1, 1}, source, "a read-only array is not returned");
        }
        assertEquals(2, fake.reads(), "one read per launch");
    }

    // ---- проверка аргументов и размеров ---------------------------------------------------------

    @Test
    void wrongArgumentCount() {
        try (GpuContext ctx = open(sineDevice())) {
            CompiledKernel k = ctx.compile(Fixtures.computeSine()).value();
            assertEquals("RT-A01", code(ctx.launch(k, LaunchConfig.of1D(256), new float[1])));
            assertEquals("RT-A01", code(ctx.launch(k, LaunchConfig.of1D(256))));
            assertEquals("RT-A01", code(ctx.launch(k, LaunchConfig.of1D(256), (Object[]) null)));
        }
    }

    @Test
    void argumentsOfTheWrongKindAreReportedTogether() {
        try (GpuContext ctx = open(sineDevice())) {
            CompiledKernel k = ctx.compile(scalarsKernel()).value();
            Result<LaunchStats> r = ctx.launch(k, LaunchConfig.of1D(4), new int[4], null, 3L, 2);
            assertFalse(r.isOk());
            assertEquals(4, r.diagnostics().size());
            assertTrue(r.diagnostics().stream().allMatch(d -> d.code().equals("RT-A02")));
            assertTrue(r.diagnostics().get(0).message().contains("argument 0"));
            assertTrue(r.diagnostics().get(1).message().contains("null"));
            assertTrue(r.diagnostics().get(2).message().contains("Long"));
        }
    }

    @Test
    void launchSizesAreValidated() {
        try (GpuContext ctx = open(sineDevice())) {
            CompiledKernel plain = ctx.compile(scalarsKernel()).value();
            float[] a = new float[8];
            // не делится на размер группы
            assertEquals("RT-L01", code(ctx.launch(plain, LaunchConfig.of1D(10, 4), a, a, 1, 1f)));
            // группа больше предела устройства (1024)
            assertEquals("RT-L01", code(ctx.launch(plain, LaunchConfig.of1D(4096, 2048), a, a, 1, 1f)));
            // reqd_work_group_size(256,1,1) у computeSine
            CompiledKernel sine = ctx.compile(Fixtures.computeSine()).value();
            assertEquals("RT-L01", code(ctx.launch(sine, LaunchConfig.of1D(256, 128), a, a)));
            assertEquals("RT-L01", code(ctx.launch(sine, LaunchConfig.of1D(100), a, a)),
                    "the kernel's 256 does not divide 100");
        }
    }

    @Test
    void requiredWorkGroupIsPassedToTheDriver() {
        List<long[]> locals = new ArrayList<>();
        FakeDeviceApi fake = FakeDeviceApi.withGpu().onKernel("k0", l -> locals.add(l.local()));
        try (GpuContext ctx = open(fake)) {
            ctx.run(Fixtures.computeSine(), LaunchConfig.of1D(512), new float[512], new float[512]);
            ctx.run(Fixtures.computeSine(), LaunchConfig.of1D(512, 256), new float[512], new float[512]);
        }
        assertArrayEquals(new long[]{256}, locals.get(0));
        assertArrayEquals(new long[]{256}, locals.get(1));
    }

    @Test
    void unspecifiedLocalSizeIsLeftToTheDriver() {
        List<long[]> locals = new ArrayList<>();
        FakeDeviceApi fake = FakeDeviceApi.withGpu().onKernel("k0", l -> locals.add(l.local()));
        float[] a = new float[8];
        try (GpuContext ctx = open(fake)) {
            ctx.run(scalarsKernel(), LaunchConfig.of1D(8), a, a, 1, 1f);
            ctx.run(scalarsKernel(), LaunchConfig.of1D(8, 4), a, a, 1, 1f);
        }
        assertEquals(null, locals.get(0));
        assertArrayEquals(new long[]{4}, locals.get(1));
    }

    @Test
    void kernelOfAnotherContextIsRejected() {
        try (GpuContext one = open(sineDevice()); GpuContext two = open(sineDevice())) {
            CompiledKernel k = one.compile(Fixtures.computeSine()).value();
            assertEquals("RT-A02", code(two.launch(k, LaunchConfig.of1D(256), new float[1], new float[1])));
        }
    }

    // ---- буферы ---------------------------------------------------------------------------------

    @Test
    void bufferKeepsDataBetweenLaunches() {
        FakeDeviceApi fake = sineDevice();
        float[] in = {0f, 1f, 2f, 3f};
        try (GpuContext ctx = open(fake); GpuBuffer input = ctx.upload(in); GpuBuffer output = ctx.allocate(Types.F32, 4)) {
            assertEquals(4, input.length());
            assertEquals(16, input.byteSize());
            assertEquals(Types.F32, input.elementType());
            CompiledKernel k = ctx.compile(Fixtures.computeSine()).value();
            Result<LaunchStats> r = ctx.launch(k, LaunchConfig.of1D(256, 256), input, output);
            // длина 4 < 256, но фейк обходит только n = длина массива: ядро проверяется не здесь
            assertTrue(r.isOk(), r.diagnostics().toString());
            assertEquals(0, r.value().bytesUp(), "the buffers are already on the device");
            assertEquals(0, r.value().bytesDown(), "and stay there");
            float[] host = new float[4];
            output.read(host);
            assertEquals((float) Math.sin(2.0) + 2f, host[2], 0f);
        }
        assertEquals(0, fake.liveBuffers());
    }

    @Test
    void bufferTransfersAreChecked() {
        try (GpuContext ctx = open(sineDevice()); GpuContext other = open(sineDevice())) {
            GpuBuffer b = ctx.allocate(Types.F32, 4);
            assertThrows(GpuException.class, () -> b.write(new int[4]), "int[] does not fit a float buffer");
            assertThrows(GpuException.class, () -> b.write(new float[5]), "longer than the buffer");
            assertThrows(GpuException.class, () -> b.read(new double[4]));
            b.write(new float[2]);
            GpuBuffer foreign = other.allocate(Types.F32, 4);
            CompiledKernel k = ctx.compile(Fixtures.computeSine()).value();
            assertEquals("RT-A02", code(ctx.launch(k, LaunchConfig.of1D(256), foreign, b)));
            b.close();
            assertTrue(b.isClosed());
            assertThrows(GpuException.class, () -> b.write(new float[1]));
            assertEquals("RT-A02", code(ctx.launch(k, LaunchConfig.of1D(256), b, foreign)));
            b.close(); // повторное закрытие безопасно
            foreign.close();
        }
    }

    @Test
    void bufferElementKindMustMatchTheParameter() {
        try (GpuContext ctx = open(sineDevice())) {
            CompiledKernel k = ctx.compile(Fixtures.computeSine()).value();
            try (GpuBuffer ints = ctx.allocate(Types.I32, 4); GpuBuffer floats = ctx.allocate(Types.F32, 4)) {
                assertEquals("RT-A02", code(ctx.launch(k, LaunchConfig.of1D(256), ints, floats)));
            }
        }
    }

    @Test
    void allocateValidatesTypeAndSize() {
        try (GpuContext ctx = open(sineDevice())) {
            assertThrows(GpuException.class, () -> ctx.allocate(Types.BOOL, 4));
            assertThrows(GpuException.class, () -> ctx.allocate(Types.F32, -1));
            assertThrows(GpuException.class, () -> ctx.allocate(Types.F32, 1L << 40), "exceeds the maximum allocation");
            assertThrows(GpuException.class, () -> ctx.upload("not an array"));
            GpuBuffer zero = ctx.allocate(Types.F32, 0);
            assertEquals(0, zero.length());
            zero.close();
        }
    }

    @Test
    void uploadMapsEveryHostArrayKind() {
        try (GpuContext ctx = open(sineDevice())) {
            Object[] arrays = {new byte[3], new boolean[3], new short[3], new char[3], new int[3], new long[3],
                    new float[3], new double[3]};
            int[] expected = {Types.I8, Types.U8, Types.I16, Types.U16, Types.I32, Types.I64, Types.F32, Types.F64};
            for (int i = 0; i < arrays.length; i++) {
                try (GpuBuffer b = ctx.upload(arrays[i])) {
                    assertEquals(expected[i], b.elementType());
                    assertEquals(3, b.length());
                }
            }
        }
    }

    @Test
    void driverFailureOnBufferOperationsBecomesAnException() {
        FakeDeviceApi fake = sineDevice();
        try (GpuContext ctx = open(fake)) {
            fake.failNext("createBuffer", -4);
            assertThrows(GpuException.class, () -> ctx.allocate(Types.F32, 4));
            GpuBuffer b = ctx.allocate(Types.F32, 4);
            fake.failNext("writeBuffer", -5);
            assertThrows(GpuException.class, () -> b.write(new float[4]));
            fake.failNext("readBuffer", -5);
            assertThrows(GpuException.class, () -> b.read(new float[4]));
        }
        assertEquals(0, fake.liveBuffers());
    }

    // ---- закрытие -------------------------------------------------------------------------------

    @Test
    void closeReleasesEverythingAndIsIdempotent() {
        FakeDeviceApi fake = sineDevice();
        GpuContext ctx = open(fake);
        GpuBuffer leaked = ctx.allocate(Types.F32, 4);
        assertTrue(ctx.compile(Fixtures.computeSine()).isOk());
        assertEquals(1, fake.liveKernels());
        ctx.close();
        ctx.close();
        assertTrue(ctx.isClosed());
        assertTrue(leaked.isClosed(), "unclosed buffers are closed together with the context");
        assertEquals(0, fake.liveBuffers());
        assertEquals(0, fake.livePrograms());
        assertEquals(0, fake.liveKernels());
        assertEquals(0, fake.liveQueues());
        assertEquals(0, fake.liveContexts());
    }

    @Test
    void closedContextRefusesWork() {
        GpuContext ctx = open(sineDevice());
        CompiledKernel k = ctx.compile(Fixtures.computeSine()).value();
        ctx.close();
        assertEquals("RT-X02", code(ctx.compile(Fixtures.computeSine())));
        assertEquals("RT-X02", code(ctx.launch(k, LaunchConfig.of1D(256), new float[1], new float[1])));
        assertThrows(GpuException.class, () -> ctx.allocate(Types.F32, 1));
        assertThrows(GpuException.class, () -> ctx.run(Fixtures.computeSine(), LaunchConfig.of1D(256), new float[1], new float[1]));
    }

    @Test
    void listenerExceptionsDoNotBreakComputation() {
        float[] out = new float[4];
        try (GpuContext ctx = open(sineDevice(), e -> {
            throw new IllegalStateException("the observer broke");
        })) {
            ctx.run(Fixtures.computeSine(), LaunchConfig.of1D(256, 256), new float[]{0, 0, 0, 0}, out);
        }
        assertEquals(2f, out[0], 0f);
    }

    @Test
    void concurrentLaunchesAreSerialisedAndCorrect() throws Exception {
        FakeDeviceApi fake = sineDevice();
        try (GpuContext ctx = open(fake); ExecutorService pool = Executors.newFixedThreadPool(6)) {
            List<Future<Boolean>> all = Collections.synchronizedList(new ArrayList<>());
            for (int t = 0; t < 12; t++) {
                final int seed = t;
                all.add(pool.submit(() -> {
                    float[] in = new float[16];
                    float[] out = new float[16];
                    for (int i = 0; i < 16; i++) {
                        in[i] = seed + i * 0.25f;
                    }
                    ctx.run(Fixtures.computeSine(), LaunchConfig.of1D(256, 256), in, out);
                    for (int i = 0; i < 16; i++) {
                        if (out[i] != (float) Math.sin(in[i]) + 2f) {
                            return false;
                        }
                    }
                    return true;
                }));
            }
            for (Future<Boolean> f : all) {
                assertTrue(f.get());
            }
            assertEquals(1, fake.builds());
            assertEquals(12, fake.enqueues());
        }
        assertEquals(0, fake.liveBuffers());
    }
}
