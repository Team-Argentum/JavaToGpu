package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Маска {@code fp}, действующая в функциях: ядро - своя, device-функция - пересечение масок вызывающих ядер. */
class FpModesTest {

    private static final int FMA = Builtins.idOf("fma");

    /**
     * f0: device {@code mad(x) = x*x + 1}; f1: device без вызовов; f2, f3: ядра с масками {@code m2}, {@code m3},
     * оба вызывают f0 ({@code a[0] = mad(a[0]) * 5 + 3}).
     */
    private static IrModule module(int m2, int m3) {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.DEVICE, Types.F32, Types.F32);
        b.endFunction(b.block(b.returnStmt(b.binary(Op.ADD, b.binary(Op.MUL, b.param(0), b.param(0)), b.constF32(1f)))));
        b.beginFunction(IrModule.DEVICE, Types.F32, Types.F32);
        b.endFunction(b.block(b.returnStmt(b.binary(Op.ADD, b.binary(Op.MUL, b.param(0), b.param(0)), b.constF32(2f)))));
        for (int mask : new int[]{m2, m3}) {
            b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
            b.fpMode(mask);
            int p = b.elemPtr(b.param(0), b.constI32(0));
            int call = b.call(0, b.load(p));
            b.endFunction(b.block(b.store(p, b.binary(Op.ADD, b.binary(Op.MUL, call, b.constF32(5f)), b.constF32(3f)))));
        }
        return b.build();
    }

    @Test
    void deviceFunctionGetsTheIntersectionOfItsCallers() {
        int both = IrModule.FP_CONTRACT | IrModule.FP_FINITE;
        Codec.ModuleTree mt = Codec.lift(module(both | IrModule.FP_NATIVE, both | IrModule.FP_RECIPROCAL));
        assertArrayEquals(new int[]{both, 0, both | IrModule.FP_NATIVE, both | IrModule.FP_RECIPROCAL},
                FpModes.effective(mt), "unreachable f1 is strict, kernels keep their own masks");
    }

    @Test
    void aStrictCallerKeepsASharedFunctionStrict() {
        Codec.ModuleTree mt = Codec.lift(module(IrModule.FP_ALL, 0));
        assertEquals(0, FpModes.effective(mt)[0]);
    }

    @Test
    void contractionFollowsTheEffectiveMask() {
        // f2 разрешает слияние, f3 - нет: сливается только выражение в теле f2; общая f0 строгая из-за f3
        assertEquals(1, OptTest.countIntrinsic(OptTest.opt(module(IrModule.FP_CONTRACT, 0)), FMA));
        assertEquals(3, OptTest.countIntrinsic(OptTest.opt(module(IrModule.FP_CONTRACT, IrModule.FP_CONTRACT)), FMA),
                "f0, f2, f3 (unreachable f1 is dropped)");
    }
}
