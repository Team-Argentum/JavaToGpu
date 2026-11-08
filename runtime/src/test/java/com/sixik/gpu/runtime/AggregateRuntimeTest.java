package com.sixik.gpu.runtime;

import com.sixik.gpu.api.types.Float3;
import com.sixik.gpu.api.types.Float4;
import com.sixik.gpu.ir.AbiLayout;
import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Массивы векторов и struct, векторы и struct по значению: упаковка по раскладке ABI, возврат результата в объекты
 * хоста и ошибки аргументов. Устройство - {@link FakeDeviceApi}.
 */
class AggregateRuntimeTest {

    /** Struct хоста: {@code float x; float y; float4 v; int id} - 48 байт, {@code v} со смещения 16. */
    public static final class Particle {
        public float x;
        public float y;
        public Float4 v;
        public int id;

        public Particle() {
            v = new Float4(0, 0, 0, 0);
        }

        public Particle(float x, float y, Float4 v, int id) {
            this.x = x;
            this.y = y;
            this.v = v;
            this.id = id;
        }
    }

    public static final class WrongShape {
        public float x;
    }

    private static final int PARTICLE_SIZE = 48;

    /** Ядро {@code items[0] = items[0]} с параметрами: массив struct и struct по значению: нужны режимы READ и WRITE. */
    private static IrModule structKernel() {
        IrBuilder b = new IrBuilder();
        int v4 = b.vec(Types.F32, 4);
        int s = b.struct(new int[]{Types.F32, Types.F32, v4, Types.I32}, 0);
        int arr = b.array(AddressSpace.GLOBAL, s);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, s, v4);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.load(b.elemPtr(b.param(0), b.constI32(0))))));
        return b.build();
    }

    private static IrModule vec3Kernel() {
        IrBuilder b = new IrBuilder();
        int v3 = b.vec(Types.F32, 3);
        int arr = b.array(AddressSpace.GLOBAL, v3);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.load(b.elemPtr(b.param(0), b.constI32(0))))));
        return b.build();
    }

    private static GpuContext open(FakeDeviceApi fake) {
        return GpuContext.builder().api(fake).ignoreEnvironment().build();
    }

    @Test
    void structLayoutMatchesTheAbiAndResultsComeBackIntoTheSameObjects() {
        IrModule m = structKernel();
        int s = m.fParamType(m.functionCount() - 1, 1);
        assertEquals(PARTICLE_SIZE, AbiLayout.sizeOf(m, s));
        assertEquals(16, AbiLayout.fieldOffset(m, s, 2));
        assertEquals(32, AbiLayout.fieldOffset(m, s, 3));

        List<byte[]> seen = new ArrayList<>();
        FakeDeviceApi fake = FakeDeviceApi.withGpu().onKernel("k0", l -> {
            ByteBuffer buf = l.buffer(0);
            byte[] copy = new byte[buf.capacity()];
            buf.get(0, copy);
            seen.add(copy);
            // ядро «двигает» частицы: x += 1, id *= 2, v.w = 42
            for (int i = 0; i < l.intArg(1); i++) {
                int at = i * PARTICLE_SIZE;
                buf.putFloat(at, buf.getFloat(at) + 1f);
                buf.putInt(at + 32, buf.getInt(at + 32) * 2);
                buf.putFloat(at + 28, 42f);
            }
            ByteBuffer scalar = l.bytesArg(2);
            seen.add(scalar.array().clone());
            ByteBuffer vec = l.bytesArg(3);
            seen.add(vec.array().clone());
        });
        Particle a = new Particle(1.5f, -2f, new Float4(10, 20, 30, 40), 7);
        Particle b = new Particle(3f, 4f, new Float4(1, 2, 3, 4), -5);
        Particle[] items = {a, b};
        Particle init = new Particle(9, 8, new Float4(5, 6, 7, 8), 3);
        try (GpuContext ctx = open(fake)) {
            LaunchStats stats = ctx.run(m, LaunchConfig.of1D(1), items, init, new Float4(0.5f, 1.5f, 2.5f, 3.5f));
            assertEquals(2 * PARTICLE_SIZE, stats.bytesUp());
            assertEquals(2 * PARTICLE_SIZE, stats.bytesDown());
        }
        // что увидело устройство: x, y, пробел до 16, float4, id, хвост выравнивания
        ByteBuffer up = ByteBuffer.wrap(seen.get(0)).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(1.5f, up.getFloat(0));
        assertEquals(-2f, up.getFloat(4));
        assertEquals(0, up.getLong(8), "выравнивающий промежуток нулевой");
        assertEquals(10f, up.getFloat(16));
        assertEquals(40f, up.getFloat(28));
        assertEquals(7, up.getInt(32));
        assertEquals(3f, up.getFloat(PARTICLE_SIZE));
        ByteBuffer byValue = ByteBuffer.wrap(seen.get(1)).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(PARTICLE_SIZE, byValue.capacity(), "struct по значению - sizeof структуры");
        assertEquals(9f, byValue.getFloat(0));
        assertEquals(3, byValue.getInt(32));
        assertEquals(16, ByteBuffer.wrap(seen.get(2)).capacity());
        // результат: struct обновлены на месте, идентичность объектов сохранена
        assertSame(a, items[0]);
        assertSame(b, items[1]);
        assertEquals(2.5f, a.x);
        assertEquals(-2f, a.y);
        assertEquals(14, a.id);
        assertEquals(new Float4(10, 20, 30, 42), a.v, "вектор-поле заменён новым объектом");
        assertEquals(4f, b.x);
        assertEquals(-10, b.id);
        assertEquals(9f, init.x, "аргумент по значению не изменяется");
    }

    @Test
    void float3ElementsOccupySixteenBytes() {
        IrModule m = vec3Kernel();
        FakeDeviceApi fake = FakeDeviceApi.withGpu().onKernel("k0", l -> {
            ByteBuffer buf = l.buffer(0);
            assertEquals(2 * 16, buf.capacity());
            assertEquals(1f, buf.getFloat(0));
            assertEquals(3f, buf.getFloat(8));
            assertEquals(0f, buf.getFloat(12), "четвёртая «компонента» float3 - пустое место");
            assertEquals(4f, buf.getFloat(16));
            buf.putFloat(16, 40f);
        });
        Float3[] data = {new Float3(1, 2, 3), new Float3(4, 5, 6)};
        try (GpuContext ctx = open(fake)) {
            ctx.run(m, LaunchConfig.of1D(1), (Object) data);
        }
        assertEquals(new Float3(1, 2, 3), data[0]);
        assertEquals(new Float3(40, 5, 6), data[1]);
    }

    @Test
    void emptyObjectArraysAreAllowed() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu().onKernel("k0", l -> {
        });
        try (GpuContext ctx = open(fake)) {
            ctx.run(vec3Kernel(), LaunchConfig.of1D(1), (Object) new Float3[0]);
        }
        assertEquals(0, fake.liveBuffers());
    }

    @Test
    void nullElementsWrongClassesAndBuffersAreArgumentErrors() {
        IrModule m = structKernel();
        try (GpuContext ctx = open(FakeDeviceApi.withGpu().onKernel("k0", l -> {
        }))) {
            CompiledKernel k = ctx.compile(m).value();
            Particle ok = new Particle();
            Float4 scale = new Float4(1, 1, 1, 1);
            assertMessage(ctx.launch(k, LaunchConfig.of1D(1), new Particle[]{ok, null}, ok, scale), "элемент 1 равен null");
            assertMessage(ctx.launch(k, LaunchConfig.of1D(1), new WrongShape[]{new WrongShape()}, ok, scale), "WrongShape");
            assertMessage(ctx.launch(k, LaunchConfig.of1D(1), new float[4], ok, scale), "массив объектов");
            assertMessage(ctx.launch(k, LaunchConfig.of1D(1), new Particle[]{ok}, null, scale), "null");
            assertMessage(ctx.launch(k, LaunchConfig.of1D(1), new Particle[]{ok}, new WrongShape(), scale), "WrongShape");
            assertMessage(ctx.launch(k, LaunchConfig.of1D(1), new Particle[]{ok}, ok, new Float3(1, 2, 3)), "Float3");
            try (GpuBuffer buf = ctx.allocate(Types.F32, 4)) {
                assertMessage(ctx.launch(k, LaunchConfig.of1D(1), buf, ok, scale), "GpuBuffer");
            }
        }
    }

    private static void assertMessage(Result<?> r, String part) {
        assertFalse(r.isOk());
        assertEquals("RT-A02", r.diagnostics().get(0).code());
        assertTrue(r.diagnostics().get(0).message().contains(part), r.diagnostics().get(0).message());
    }

    @Test
    void readOnlyObjectArraysAreNotReadBack() {
        IrBuilder b = new IrBuilder();
        int v4 = b.vec(Types.F32, 4);
        int arr = b.array(AddressSpace.GLOBAL, v4);
        int out = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, out);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(1), b.constI32(0)),
                b.extract(b.load(b.elemPtr(b.param(0), b.constI32(0))), 2))));
        FakeDeviceApi fake = FakeDeviceApi.withGpu().onKernel("k0", l -> l.buffer(2).putFloat(0, l.buffer(0).getFloat(8)));
        Float4[] in = {new Float4(1, 2, 3, 4)};
        Float4 before = in[0];
        float[] result = new float[1];
        try (GpuContext ctx = open(fake)) {
            LaunchStats stats = ctx.run(b.build(), LaunchConfig.of1D(1), in, result);
            assertEquals(4, stats.bytesDown(), "возвращается только записываемый массив");
        }
        assertEquals(3f, result[0]);
        assertSame(before, in[0], "читаемый массив объектов не пересоздаётся");
        assertNotSame(null, fake);
    }
}
