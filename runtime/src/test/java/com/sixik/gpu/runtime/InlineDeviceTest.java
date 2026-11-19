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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code INLINE_ALWAYS} на устройстве: встроенное тело с ранним {@code return} (обёртка {@code do-while(false)})
 * собирается драйвером и считает так же, как интерпретатор по исходному модулю.
 */
@Tag("device")
class InlineDeviceTest {

    /** {@code relu(x) { if (x < 0) return 0; return x; }}, {@code a[i] = relu(a[i]) * 3 + relu(i - 4)}. */
    static IrModule module() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        int relu = b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32);
        b.inlinePolicy(IrModule.INLINE_ALWAYS);
        b.endFunction(b.block(
                b.ifStmt(b.binary(Op.LT, b.param(0), b.constI32(0)), b.block(b.returnStmt(b.constI32(0))), -1),
                b.returnStmt(b.param(0))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int id = b.declareLocal(Types.I32);
        int decl = b.localDecl(id, b.intrinsic(Fixtures.GET_GLOBAL_ID, b.constI32(0)));
        int v = b.binary(Op.ADD, b.binary(Op.MUL, b.call(relu, b.load(b.elemPtr(b.param(0), b.localGet(id)))),
                b.constI32(3)), b.call(relu, b.binary(Op.SUB, b.localGet(id), b.constI32(4))));
        b.endFunction(b.block(decl, b.store(b.elemPtr(b.param(0), b.localGet(id)), v)));
        return b.build();
    }

    @Test
    void inlinedEarlyReturnRunsOnTheDevice() {
        Result<GpuContext> opened = GpuContext.builder().ignoreEnvironment().tryBuild();
        Assumptions.assumeTrue(opened.isOk(), "no OpenCL device");
        int n = 256;
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = (i * 37) % 101 - 50;
        }
        int[] expected = a.clone();
        IrModule m = module();
        assertTrue(Interpreter.run(m, NdRange.of1D(n), expected).isOk());
        try (GpuContext ctx = opened.value()) {
            Result<CompiledKernel> k = ctx.compile(m);
            assertTrue(k.isOk(), k.diagnostics().toString());
            assertFalse(k.value().artifact().source().contains("f0("), k.value().artifact().source());
            assertTrue(ctx.launch(k.value(), LaunchConfig.of1D(n), (Object) a).isOk());
        }
        assertArrayEquals(expected, a);
    }
}
