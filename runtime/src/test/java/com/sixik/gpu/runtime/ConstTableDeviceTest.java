package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Таблицы констант на устройстве (решение 18.6-5): {@code __constant}-массивы области программы собираются драйвером и
 * читаются так же, как в интерпретаторе, включая NaN, бесконечности и {@code -0.0f}.
 */
@Tag("device")
class ConstTableDeviceTest {

    private static final float[] VALUES = {1.5f, -0f, Float.NaN, Float.POSITIVE_INFINITY, -3.25f, 1e-30f, 7f, 0.1f,
            Float.NEGATIVE_INFINITY, 42f, -1f, 2f, 3f, 4f, 5f, 6f, 9f};

    /** {@code out[i] = F[i % 17] * 2 + (float) I[i & 3]} над {@code float[] out}. */
    static IrModule module() {
        long[] f = new long[VALUES.length];
        for (int i = 0; i < f.length; i++) {
            f[i] = Float.floatToRawIntBits(VALUES[i]);
        }
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int id = b.declareLocal(Types.I32);
        int decl = b.localDecl(id, b.intrinsic(Fixtures.GET_GLOBAL_ID, b.constI32(0)));
        int fv = b.load(b.elemPtr(b.constTable(Types.F32, f), b.binary(Op.REM, b.localGet(id), b.constI32(f.length))));
        int iv = b.load(b.elemPtr(b.constTable(Types.I16, new long[]{-30000, 2, 30000, -1}),
                b.binary(Op.AND, b.localGet(id), b.constI32(3))));
        int v = b.binary(Op.ADD, b.binary(Op.MUL, fv, b.constF32(2f)), b.convert(Types.F32, iv));
        b.endFunction(b.block(decl, b.store(b.elemPtr(b.param(0), b.localGet(id)), v)));
        return b.build();
    }

    @Test
    void tablesRunOnTheDevice() {
        Result<GpuContext> opened = GpuContext.builder().ignoreEnvironment().tryBuild();
        Assumptions.assumeTrue(opened.isOk(), "no OpenCL device");
        int n = 256;
        float[] expected = new float[n];
        IrModule m = module();
        assertTrue(Interpreter.run(m, NdRange.of1D(n), expected).isOk());
        float[] out = new float[n];
        try (GpuContext ctx = opened.value()) {
            Result<CompiledKernel> k = ctx.compile(m);
            assertTrue(k.isOk(), k.diagnostics().toString());
            String src = k.value().artifact().source();
            assertTrue(src.contains("__constant float tbl"), src);
            assertTrue(src.contains("__constant short tbl"), src);
            assertTrue(ctx.launch(k.value(), LaunchConfig.of1D(n), (Object) out).isOk());
        }
        for (int i = 0; i < n; i++) {
            assertTrue(Float.floatToIntBits(expected[i]) == Float.floatToIntBits(out[i])
                    || (Float.isNaN(expected[i]) && Float.isNaN(out[i])), "i=" + i + ": " + expected[i] + " vs " + out[i]);
        }
    }
}
