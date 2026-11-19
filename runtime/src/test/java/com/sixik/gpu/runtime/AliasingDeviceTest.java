package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code restrict} на настоящем устройстве ({@link com.sixik.gpu.codegen.opencl.Aliasing}): ядро, чей результат
 * зависит от того, перекрываются ли буферы, даёт семантику Java в обоих случаях. Запуск: {@code ./gradlew
 * :runtime:deviceTest}.
 */
@Tag("device")
class AliasingDeviceTest {

    private static GpuContext ctx;

    @BeforeAll
    static void open() {
        Result<GpuContext> r = GpuContext.builder().tryBuild();
        Assumptions.assumeTrue(r.isOk(), "no OpenCL device: " + r.diagnostics());
        ctx = r.value();
    }

    @AfterAll
    static void close() {
        if (ctx != null) {
            ctx.close();
        }
    }

    /**
     * {@code out[g] = in[g] + 1; out[g] = out[g] + in[g];} - с {@code restrict} компилятор вправе прочитать
     * {@code in[g]} один раз; при {@code out == in} это дало бы {@code 2a+1} вместо {@code 2a+2}.
     */
    private static IrModule twice() {
        IrBuilder b = new IrBuilder();
        int a = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, a, a);
        int g = b.declareLocal(Types.I32);
        b.endFunction(b.block(
                b.localDecl(g, b.intrinsic(0, b.constI32(0))),
                b.store(b.elemPtr(b.param(0), b.localGet(g)),
                        b.binary(Op.ADD, b.load(b.elemPtr(b.param(1), b.localGet(g))), b.constF32(1f))),
                b.store(b.elemPtr(b.param(0), b.localGet(g)),
                        b.binary(Op.ADD, b.load(b.elemPtr(b.param(0), b.localGet(g))),
                                b.load(b.elemPtr(b.param(1), b.localGet(g)))))));
        return b.build();
    }

    @Test
    void inPlaceAndSeparateLaunchesKeepJavaSemantics() {
        CompiledKernel k = ctx.compile(twice()).value();
        int n = 4096;
        float[] a = new float[n];
        float[] in = new float[n];
        float[] out = new float[n];
        float[] inPlace = new float[n];
        float[] separate = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = i;
            in[i] = i;
            inPlace[i] = 2 * i + 2;
            separate[i] = 2 * i + 1;
        }
        assertTrue(ctx.launch(k, LaunchConfig.of1D(n), out, in).isOk());
        assertArrayEquals(separate, out);

        Result<LaunchStats> r = ctx.launch(k, LaunchConfig.of1D(n), a, a);
        assertTrue(r.isOk(), r.diagnostics().toString());
        assertArrayEquals(inPlace, a, "one array in both parameters: the variant without restrict");
        assertTrue(k.hasAliasedVariant());

        // обратно на раздельные массивы - снова вариант с restrict
        java.util.Arrays.fill(out, 0f);
        assertTrue(ctx.launch(k, LaunchConfig.of1D(n), out, in).isOk());
        assertArrayEquals(separate, out);
    }

    /** Тот же буфер устройства ({@link GpuBuffer}) в двух параметрах - тоже перекрытие. */
    @Test
    void oneDeviceBufferInTwoParameters() {
        CompiledKernel k = ctx.compile(twice()).value();
        int n = 1024;
        float[] host = new float[n];
        float[] expected = new float[n];
        for (int i = 0; i < n; i++) {
            host[i] = i;
            expected[i] = 2 * i + 2;
        }
        try (GpuBuffer buf = ctx.upload(host)) {
            assertTrue(ctx.launch(k, LaunchConfig.of1D(n), buf, buf).isOk());
            float[] back = new float[n];
            buf.read(back);
            assertArrayEquals(expected, back);
        }
    }
}
