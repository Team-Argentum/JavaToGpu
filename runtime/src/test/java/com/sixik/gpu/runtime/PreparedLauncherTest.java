package com.sixik.gpu.runtime;

import com.sixik.gpu.api.types.Float4;
import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link PreparedLauncher} на фейковом {@link com.sixik.gpu.runtime.device.DeviceApi}: владение, привязка аргументов, ошибки. */
class PreparedLauncherTest {

    /** Ядро {@code out[g] = in[g] * k + n}: параметры (in, out, int n, float k); аргументы OpenCL: in, in_len, out, out_len, n, k. */
    private static IrModule scaled() {
        IrBuilder b = new IrBuilder();
        int a = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, a, a, Types.I32, Types.F32);
        int g = b.declareLocal(Types.I32);
        b.endFunction(b.block(
                b.localDecl(g, b.intrinsic(0, b.constI32(0))),
                b.store(b.elemPtr(b.param(1), b.localGet(g)),
                        b.binary(Op.ADD, b.binary(Op.MUL, b.load(b.elemPtr(b.param(0), b.localGet(g))), b.param(3)),
                                b.convert(Types.F32, b.param(2))))));
        return b.build();
    }

    private static FakeDeviceApi device() {
        return FakeDeviceApi.withGpu().onKernel("k0", l -> {
            ByteBuffer in = l.buffer(0);
            ByteBuffer out = l.buffer(2);
            int n = l.intArg(1);
            for (int i = 0; i < n; i++) {
                out.putFloat(4 * i, in.getFloat(4 * i) * l.floatArg(5) + l.intArg(4));
            }
        });
    }

    private static GpuContext open(FakeDeviceApi fake, EventListener listener) {
        return GpuContext.builder().api(fake).ignoreEnvironment().listener(listener).build();
    }

    private static long count(FakeDeviceApi fake, String call) {
        return fake.journal().stream().filter(call::equals).count();
    }

    private static long countSetArgs(FakeDeviceApi fake) {
        return count(fake, "setArgBuffer") + count(fake, "setArgScalar") + count(fake, "setArgBytes");
    }

    private static PreparedLauncher prepare(GpuContext ctx, CompiledKernel k, Object... args) {
        Result<PreparedLauncher> r = ctx.prepare(k, args);
        assertTrue(r.isOk(), r.diagnostics().toString());
        return r.value();
    }

    private static String code(Result<?> r) {
        assertFalse(r.isOk());
        return r.diagnostics().get(0).code();
    }

    // ---- переиспользование ---------------------------------------------------------------------

    @Test
    void repeatedLaunchesReuseBuffersAndKeepArgumentsBound() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake, EventListener.NONE)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            float[] in = {1, 2, 3, 4};
            float[] out = new float[4];
            try (PreparedLauncher p = prepare(ctx, k, in, out, 10, 2f)) {
                assertEquals(2, count(fake, "createBuffer"), "one buffer per array, once");
                p.run(LaunchConfig.of1D(4));
                assertArrayEquals(new float[]{12, 14, 16, 18}, out);
                long bound = countSetArgs(fake);
                assertEquals(6, bound, "the first launch set all 6 OpenCL arguments");

                for (int round = 0; round < 5; round++) {
                    in[0] = round; // пользователь меняет данные между запусками - они уходят на устройство
                    p.run(LaunchConfig.of1D(4));
                    assertEquals(round * 2f + 10, out[0]);
                }
                assertEquals(bound, countSetArgs(fake), "arguments did not change: clSetKernelArg was not called");
                assertEquals(2, count(fake, "createBuffer"), "no new buffers were created");
            }
        }
    }

    @Test
    void setReplacesOnlyTheChangedArgument() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake, EventListener.NONE)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            float[] in = {1, 2};
            float[] out = new float[2];
            try (PreparedLauncher p = prepare(ctx, k, in, out, 0, 1f)) {
                p.run(LaunchConfig.of1D(2));
                long before = countSetArgs(fake);
                p.set(3, 5f).run(LaunchConfig.of1D(2));
                assertEquals(before + 1, countSetArgs(fake), "one argument changed - one set");
                assertArrayEquals(new float[]{5, 10}, out);
                p.set(2, 100).set(3, 0.5f).run(LaunchConfig.of1D(2));
                assertEquals(before + 3, countSetArgs(fake));
                assertArrayEquals(new float[]{100.5f, 101f}, out);
            }
        }
    }

    @Test
    void anotherLaunchOfTheSameKernelForcesRebinding() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake, EventListener.NONE)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            float[] in = {1, 2, 3};
            float[] outA = new float[3];
            float[] outB = new float[3];
            try (PreparedLauncher a = prepare(ctx, k, in, outA, 0, 1f); PreparedLauncher b = prepare(ctx, k, in, outB, 0, 10f)) {
                a.run(LaunchConfig.of1D(3));
                b.run(LaunchConfig.of1D(3));
                a.run(LaunchConfig.of1D(3));
                assertArrayEquals(new float[]{1, 2, 3}, outA, "shared cl_kernel: the launch of b did not spoil the arguments of a");
                assertArrayEquals(new float[]{10, 20, 30}, outB);
                b.run(LaunchConfig.of1D(3)); // последний, кто привязал аргументы, - сам b
                long beforeSugar = countSetArgs(fake);
                float[] outC = new float[3];
                assertTrue(ctx.launch(k, LaunchConfig.of1D(3), in, outC, 0, 3f).isOk());
                assertTrue(countSetArgs(fake) > beforeSugar);
                long afterSugar = countSetArgs(fake);
                b.run(LaunchConfig.of1D(3));
                assertEquals(afterSugar + 6, countSetArgs(fake), "after someone else's launch all arguments are set again");
                assertArrayEquals(new float[]{10, 20, 30}, outB);
            }
        }
    }

    // ---- владение ------------------------------------------------------------------------------

    @Test
    void gpuBufferArgumentsAreStaticNothingIsTransferred() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake, EventListener.NONE)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            try (GpuBuffer in = ctx.upload(new float[]{1, 2, 3, 4}); GpuBuffer out = ctx.allocate(Types.F32, 4);
                 PreparedLauncher p = prepare(ctx, k, in, out, 1, 3f)) {
                int writes = fake.writes();
                int reads = fake.reads();
                LaunchStats s = p.run(LaunchConfig.of1D(4));
                p.run(LaunchConfig.of1D(4));
                assertEquals(writes, fake.writes(), "the data is already on the device: nothing is written");
                assertEquals(reads, fake.reads(), "and nothing is read");
                assertEquals(0, s.bytesUp());
                assertEquals(0, s.bytesDown());
                float[] result = new float[4];
                out.read(result);
                assertArrayEquals(new float[]{4, 7, 10, 13}, result);
            }
        }
    }

    @Test
    void hostArrayTransfersFollowAccessModes() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake, EventListener.NONE)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            float[] in = {1, 2, 3, 4};
            float[] out = new float[4];
            try (PreparedLauncher p = prepare(ctx, k, in, out, 0, 1f)) {
                LaunchStats s = p.run(LaunchConfig.of1D(4));
                assertEquals(16, s.bytesUp(), "to the device - in; out[gid] is overwritten entirely and not uploaded");
                assertEquals(16, s.bytesDown(), "back - only the writable out");
                assertEquals(1, fake.reads());
                assertArrayEquals(new float[]{1, 2, 3, 4}, out);
            }
        }
    }

    @Test
    void largeArraysGoThroughPinnedMemoryAndAreReleased() {
        FakeDeviceApi fake = device();
        int n = 512 * 1024; // 2 МиБ на массив - выше порога PINNED_BYTES
        float[] in = new float[n];
        for (int i = 0; i < n; i++) {
            in[i] = i;
        }
        float[] out = new float[n];
        try (GpuContext ctx = open(fake, EventListener.NONE)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            try (PreparedLauncher p = prepare(ctx, k, in, out, 3, 2f)) {
                assertEquals(2, fake.livePinned(), "both large arrays are staged in pinned memory");
                for (int run = 0; run < 3; run++) {
                    p.run(LaunchConfig.of1D(n));
                    assertEquals(2, fake.livePinned(), "the pinned staging is reused between launches");
                }
                for (int i = 0; i < n; i += 4099) {
                    assertEquals(in[i] * 2f + 3, out[i], "i=" + i);
                }
                assertEquals(in[n - 1] * 2f + 3, out[n - 1]);
            }
            assertEquals(0, fake.livePinned(), "closing the launch releases its pinned buffers");
        }
    }

    @Test
    void smallArraysDoNotPinMemory() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake, EventListener.NONE)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            try (PreparedLauncher p = prepare(ctx, k, new float[16], new float[16], 0, 1f)) {
                p.run(LaunchConfig.of1D(16));
                assertEquals(0, fake.livePinned());
            }
        }
    }

    @Test
    void partiallyWrittenArrayIsStillUploaded() {
        // global(0) = 2 < out.length = 4: элементы 2 и 3 ядро не трогает, их значения хоста должны сохраниться
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake, EventListener.NONE)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            assertTrue(k.fullyWritten(1));
            assertFalse(k.fullyWritten(0));
            float[] in = {1, 2, 3, 4};
            float[] out = {9, 9, 9, 9};
            try (PreparedLauncher p = prepare(ctx, k, in, out, 0, 1f)) {
                LaunchStats s = p.run(LaunchConfig.of1D(2));
                assertEquals(2 * 16, s.bytesUp(), "out is covered only for global == length");
            }
        }
    }

    @Test
    void closeReleasesOwnedBuffersButNotTheUsersAndIsIdempotent() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake, EventListener.NONE)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            GpuBuffer shared = ctx.allocate(Types.F32, 4);
            int baseline = fake.liveBuffers();
            float[] out = new float[4];
            PreparedLauncher p = prepare(ctx, k, shared, out, 0, 1f);
            assertEquals(baseline + 1, fake.liveBuffers(), "the buffer for out belongs to the launch");
            p.close();
            p.close();
            assertTrue(p.isClosed());
            assertEquals(baseline, fake.liveBuffers(), "the user's buffer is in place, its own was released");
            assertFalse(shared.isClosed());
            assertEquals("RT-X02", code(p.launch(LaunchConfig.of1D(4))));
            assertThrows(GpuException.class, () -> p.set(2, 1));
            shared.close();
        }
        assertEquals(0, fake.liveBuffers());
    }

    @Test
    void contextCloseReleasesLaunchersThatWereNotClosed() {
        FakeDeviceApi fake = device();
        PreparedLauncher p;
        try (GpuContext ctx = open(fake, EventListener.NONE)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            p = prepare(ctx, k, new float[2], new float[2], 0, 1f);
            assertEquals(2, fake.liveBuffers());
        }
        assertEquals(0, fake.liveBuffers(), "no leak");
        assertTrue(p.isClosed());
        assertEquals("RT-X02", code(p.launch(LaunchConfig.of1D(2))));
    }

    @Test
    void sameHostArrayTwiceIsOneBufferWithCombinedAccess() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake, EventListener.NONE)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            float[] data = {1, 2, 3};
            try (PreparedLauncher p = prepare(ctx, k, data, data, 5, 2f)) {
                assertEquals(1, count(fake, "createBuffer"), "aliasing as in the JVM: one buffer");
                p.run(LaunchConfig.of1D(3));
                assertArrayEquals(new float[]{7, 9, 11}, data);
            }
        }
    }

    @Test
    void swappingGpuBuffersAllowsPingPong() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake, EventListener.NONE)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            try (GpuBuffer a = ctx.upload(new float[]{1, 2}); GpuBuffer b = ctx.allocate(Types.F32, 2);
                 PreparedLauncher p = prepare(ctx, k, a, b, 1, 2f)) {
                p.run(LaunchConfig.of1D(2));              // b = a * 2 + 1
                p.set(0, b).set(1, a).run(LaunchConfig.of1D(2)); // a = b * 2 + 1
                float[] result = new float[2];
                a.read(result);
                assertArrayEquals(new float[]{7, 11}, result);
            }
        }
    }

    // ---- ошибки --------------------------------------------------------------------------------

    @Test
    void argumentsAreValidatedOnceAtPrepare() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake, EventListener.NONE)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            assertEquals("RT-A01", code(ctx.prepare(k, new float[1])));
            assertEquals("RT-A02", code(ctx.prepare(k, new int[1], new float[1], 0, 1f)));
            assertEquals("RT-A02", code(ctx.prepare(k, new float[1], new float[1], 0, "x")));
            assertEquals(0, fake.liveBuffers(), "no buffers are created on rejection");
            assertThrows(IllegalArgumentException.class, () -> ctx.prepare(null, new Object[0]));
            try (GpuContext other = open(device(), EventListener.NONE)) {
                assertEquals("RT-A02", code(other.prepare(k, new float[1], new float[1], 0, 1f)));
            }
        }
    }

    @Test
    void setValidatesTheNewValueAndRefusesHostArrays() {
        try (GpuContext ctx = open(device(), EventListener.NONE)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            try (PreparedLauncher p = prepare(ctx, k, new float[2], new float[2], 0, 1f)) {
                assertThrows(GpuException.class, () -> p.set(3, "string"));
                assertThrows(GpuException.class, () -> p.set(2, 1.5f), "float does not fit an int parameter");
                assertThrows(GpuException.class, () -> p.set(9, 1));
                GpuException e = assertThrows(GpuException.class, () -> p.set(0, new float[2]));
                assertEquals("RT-A02", e.diagnostics().get(0).code());
            }
        }
    }

    @Test
    void closedUserBufferIsReportedAtLaunch() {
        try (GpuContext ctx = open(device(), EventListener.NONE)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            GpuBuffer in = ctx.upload(new float[]{1, 2});
            try (PreparedLauncher p = prepare(ctx, k, in, new float[2], 0, 1f)) {
                in.close();
                Result<LaunchStats> r = p.launch(LaunchConfig.of1D(2));
                assertEquals("RT-A02", code(r));
                assertTrue(r.diagnostics().get(0).message().contains("closed"));
            }
        }
    }

    @Test
    void launchSizesAreCheckedAtEveryLaunch() {
        try (GpuContext ctx = open(device(), EventListener.NONE)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            try (PreparedLauncher p = prepare(ctx, k, new float[8], new float[8], 0, 1f)) {
                assertEquals("RT-L01", code(p.launch(LaunchConfig.of1D(8, 3))), "8 is not divisible by 3");
                assertTrue(p.launch(LaunchConfig.of1D(8, 4)).isOk());
                assertThrows(IllegalArgumentException.class, () -> p.launch(null));
            }
        }
    }

    @Test
    void driverFailureLeavesHostArraysUntouchedAndTheLauncherUsable() {
        FakeDeviceApi fake = device().failNext("enqueue", -5);
        try (GpuContext ctx = open(fake, EventListener.NONE)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            float[] in = {1, 2};
            float[] out = {-1, -1};
            try (PreparedLauncher p = prepare(ctx, k, in, out, 0, 2f)) {
                Result<LaunchStats> failed = p.launch(LaunchConfig.of1D(2));
                assertEquals("RT-X01", code(failed));
                assertArrayEquals(new float[]{-1, -1}, out, "on a launch error the host arrays are not changed");
                assertTrue(p.launch(LaunchConfig.of1D(2)).isOk(), "the next launch succeeds");
                assertArrayEquals(new float[]{2, 4}, out);
            }
        }
    }

    @Test
    void tooLargeConstantArrayIsRejectedAtPrepare() {
        IrBuilder b = new IrBuilder();
        int table = b.array(AddressSpace.CONSTANT, Types.F32);
        int out = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, table, out);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(1), b.constI32(0)), b.load(b.elemPtr(b.param(0), b.constI32(0))))));
        FakeDeviceApi fake = new FakeDeviceApi(FakeDeviceApi.Spec.gpu().withMaxConstant(64));
        try (GpuContext ctx = open(fake, EventListener.NONE)) {
            CompiledKernel k = ctx.compile(b.build()).value();
            assertEquals("RT-A04", code(ctx.prepare(k, new float[100], new float[1])));
            assertEquals(0, fake.liveBuffers());
        }
    }

    // ---- события и агрегаты --------------------------------------------------------------------

    @Test
    void eventsAreReportedForEveryLaunch() {
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = open(device(), events::add)) {
            CompiledKernel k = ctx.compile(scaled()).value();
            try (PreparedLauncher p = prepare(ctx, k, new float[2], new float[2], 0, 1f)) {
                p.run(LaunchConfig.of1D(2));
                p.run(LaunchConfig.of1D(2));
            }
        }
        for (GpuEvent.Kind kind : new GpuEvent.Kind[]{GpuEvent.Kind.UPLOAD, GpuEvent.Kind.KERNEL, GpuEvent.Kind.READBACK}) {
            assertEquals(2, events.stream().filter(e -> e.kind() == kind).count(), kind.toString());
        }
    }

    @Test
    void vectorArraysAreRepackedByAbiOnEveryLaunch() {
        IrBuilder b = new IrBuilder();
        int v4 = b.vec(Types.F32, 4);
        int arr = b.array(AddressSpace.GLOBAL, v4);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(1)),
                b.binary(Op.ADD, b.load(b.elemPtr(b.param(0), b.constI32(0))), b.splat(v4, b.constF32(1))))));
        FakeDeviceApi fake = FakeDeviceApi.withGpu().onKernel("k0", l -> {
            ByteBuffer data = l.buffer(0);
            for (int i = 0; i < 4; i++) {
                data.putFloat(16 + 4 * i, data.getFloat(4 * i) + 1f);
            }
        });
        try (GpuContext ctx = open(fake, EventListener.NONE)) {
            CompiledKernel k = ctx.compile(b.build()).value();
            Float4[] data = {new Float4(1, 2, 3, 4), new Float4(0, 0, 0, 0)};
            try (PreparedLauncher p = prepare(ctx, k, (Object) data)) {
                p.run(LaunchConfig.of1D(1));
                assertEquals(new Float4(2, 3, 4, 5), data[1]);
                Float4[] before = data.clone();
                data[0] = new Float4(10, 20, 30, 40);
                p.run(LaunchConfig.of1D(1));
                assertEquals(new Float4(11, 21, 31, 41), data[1], "the changed host data went to the device");
                assertNotSame(before[1], data[1], "the vector is immutable and created anew");
            }
        }
    }
}
