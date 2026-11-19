package com.sixik.gpu.runtime;

import com.sixik.gpu.codegen.opencl.Aliasing;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Вариант ядра без {@code restrict} ({@link Aliasing}) на фейковом драйвере: собирается только тогда, когда записанный
 * буфер стоит в двух параметрах, и запуски переключаются между вариантами.
 */
class AliasingTest {

    /** {@code out[g] = in[g] * 2 + 1}: параметры (in, out). */
    private static IrModule scaled() {
        IrBuilder b = new IrBuilder();
        int a = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, a, a);
        int g = b.declareLocal(Types.I32);
        b.endFunction(b.block(
                b.localDecl(g, b.intrinsic(0, b.constI32(0))),
                b.store(b.elemPtr(b.param(1), b.localGet(g)),
                        b.binary(Op.ADD, b.binary(Op.MUL, b.load(b.elemPtr(b.param(0), b.localGet(g))),
                                b.constF32(2f)), b.constF32(1f)))));
        return b.build();
    }

    /** {@code out[g] = x[g] + y[g]}: параметры (out, x, y), {@code x} и {@code y} только читаются. */
    private static IrModule sum() {
        IrBuilder b = new IrBuilder();
        int a = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, a, a, a);
        int g = b.declareLocal(Types.I32);
        b.endFunction(b.block(
                b.localDecl(g, b.intrinsic(0, b.constI32(0))),
                b.store(b.elemPtr(b.param(0), b.localGet(g)),
                        b.binary(Op.ADD, b.load(b.elemPtr(b.param(1), b.localGet(g))),
                                b.load(b.elemPtr(b.param(2), b.localGet(g)))))));
        return b.build();
    }

    private static FakeDeviceApi device() {
        return FakeDeviceApi.withGpu().onKernel("k0", l -> {
            if (l.argCount() == 4) { // scaled: in, in_len, out, out_len
                ByteBuffer in = l.buffer(0);
                ByteBuffer out = l.buffer(2);
                for (int i = 0; i < l.intArg(1); i++) {
                    out.putFloat(4 * i, in.getFloat(4 * i) * 2f + 1f);
                }
            } else { // sum: out, out_len, x, x_len, y, y_len
                ByteBuffer out = l.buffer(0);
                ByteBuffer x = l.buffer(2);
                ByteBuffer y = l.buffer(4);
                for (int i = 0; i < l.intArg(1); i++) {
                    out.putFloat(4 * i, x.getFloat(4 * i) + y.getFloat(4 * i));
                }
            }
        });
    }

    private static long aliasedBuilds(FakeDeviceApi fake) {
        return fake.buildOptions().stream().filter(o -> o != null && o.contains(Aliasing.ALIASED_OPTION)).count();
    }

    @Test
    void distinctBuffersUseTheRestrictedKernel() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().build()) {
            CompiledKernel k = ctx.compile(scaled()).value();
            assertTrue(k.artifact().source().contains("__global float* " + Aliasing.MACRO + " "),
                    k.artifact().source());
            float[] in = {1, 2, 3};
            float[] out = new float[3];
            assertTrue(ctx.launch(k, LaunchConfig.of1D(3), in, out).isOk());
            assertArrayEquals(new float[]{3, 5, 7}, out);
            assertFalse(k.hasAliasedVariant());
            assertEquals(0, aliasedBuilds(fake));
        }
    }

    @Test
    void aWrittenArrayInTwoParametersRunsTheVariantWithoutRestrict() {
        FakeDeviceApi fake = device();
        List<String> warnings = new ArrayList<>();
        EventListener listener = e -> {
            if (e.kind() == GpuEvent.Kind.WARNING) {
                warnings.add(e.detail());
            }
        };
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().listener(listener).build()) {
            CompiledKernel k = ctx.compile(scaled()).value();
            float[] a = {1, 2, 3};
            Result<LaunchStats> r = ctx.launch(k, LaunchConfig.of1D(3), a, a);
            assertTrue(r.isOk(), r.diagnostics().toString());
            assertArrayEquals(new float[]{3, 5, 7}, a);
            assertTrue(k.hasAliasedVariant());
            assertEquals(1, aliasedBuilds(fake));
            assertTrue(warnings.stream().anyMatch(w -> w.contains("without restrict")), warnings.toString());

            // снова раздельные массивы, затем снова один: вариант не пересобирается
            float[] out = new float[3];
            assertTrue(ctx.launch(k, LaunchConfig.of1D(3), new float[]{0, 1, 2}, out).isOk());
            assertArrayEquals(new float[]{1, 3, 5}, out);
            assertTrue(ctx.launch(k, LaunchConfig.of1D(3), a, a).isOk());
            assertArrayEquals(new float[]{7, 11, 15}, a);
            assertEquals(1, aliasedBuilds(fake));
        }
    }

    @Test
    void anArrayReadThroughTwoParametersKeepsRestrict() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().build()) {
            CompiledKernel k = ctx.compile(sum()).value();
            float[] x = {1, 2};
            float[] out = new float[2];
            assertTrue(ctx.launch(k, LaunchConfig.of1D(2), out, x, x).isOk());
            assertArrayEquals(new float[]{2, 4}, out);
            assertFalse(k.hasAliasedVariant(), "reading one buffer twice does not break restrict");
        }
    }

    @Test
    void preparedLauncherSwitchesToTheVariant() {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().build()) {
            CompiledKernel k = ctx.compile(scaled()).value();
            float[] a = {1, 2};
            try (PreparedLauncher p = ctx.prepare(k, a, a).value()) {
                p.run(LaunchConfig.of1D(2));
                p.run(LaunchConfig.of1D(2));
            }
            assertArrayEquals(new float[]{7, 11}, a);
            assertTrue(k.hasAliasedVariant());
            assertEquals(1, aliasedBuilds(fake));
        }
    }
}
