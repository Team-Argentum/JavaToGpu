package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.NdRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DeadParams}: неиспользуемые параметры device-функций убираются вместе с аргументами вызовов, в том числе по
 * цепочке переходников; эффект вызова в отдельном стейтменте сохраняется; сигнатура ядра не меняется. Поведение сверяется с
 * интерпретатором.
 */
class DeadParamsTest {

    private static final int N = 16;

    /**
     * <pre>
     * read(a, unused, y)  = a[y]
     * pass(a, b, y)       = read(a, y + 1, y)        // b не читается, y + 1 уходит в мёртвый параметр
     * keep(a, x, y)       = a[y]                     // x мёртв; результат touch(...) приходит через локаль
     * touch(s, i)         = { s[i] = 7; return 1; }
     * kernel(out, in, side): t = touch(side, g); out[g] = pass(in, out, g) + keep(in, t, g)
     * </pre>
     */
    private static IrModule module() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        int read = b.beginFunction(IrModule.DEVICE, Types.I32, arr, Types.I32, Types.I32);
        b.inlinePolicy(IrModule.INLINE_NEVER);
        b.nameFunction("read");
        b.endFunction(b.block(b.returnStmt(b.load(b.elemPtr(b.param(0), b.param(2))))));

        int pass = b.beginFunction(IrModule.DEVICE, Types.I32, arr, arr, Types.I32);
        b.inlinePolicy(IrModule.INLINE_NEVER);
        b.nameFunction("pass");
        b.endFunction(b.block(b.returnStmt(b.call(read, b.param(0),
                b.binary(Op.ADD, b.param(2), b.constI32(1)), b.param(2)))));

        int keep = b.beginFunction(IrModule.DEVICE, Types.I32, arr, Types.I32, Types.I32);
        b.inlinePolicy(IrModule.INLINE_NEVER);
        b.nameFunction("keep");
        b.endFunction(b.block(b.returnStmt(b.load(b.elemPtr(b.param(0), b.param(2))))));

        int touch = b.beginFunction(IrModule.DEVICE, Types.I32, arr, Types.I32);
        b.inlinePolicy(IrModule.INLINE_NEVER);
        b.nameFunction("touch");
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.param(1)), b.constI32(7)),
                b.returnStmt(b.constI32(1))));

        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, arr, arr);
        b.nameFunction("kernel");
        int t = b.declareLocal(Types.I32);
        int decl = b.localDecl(t, b.call(touch, b.param(2), OptTest.gid(b)));
        int g = OptTest.gid(b);
        int g2 = OptTest.gid(b);
        int g4 = OptTest.gid(b);
        b.endFunction(b.block(decl, b.store(b.elemPtr(b.param(0), g),
                b.binary(Op.ADD, b.call(pass, b.param(1), b.param(0), g2),
                        b.call(keep, b.param(1), b.localGet(t), g4)))));
        return b.build();
    }

    private static int params(IrModule m, String name) {
        for (int f = 0; f < m.functionCount(); f++) {
            if (name.equals(m.debug().functionName(f))) {
                return m.fParamCount(f);
            }
        }
        throw new AssertionError("no function " + name);
    }

    @Test
    void unusedParametersAndTheirArgumentsAreRemoved() {
        IrModule src = module();
        IrModule out = OptTest.opt(src);
        assertEquals(2, params(out, "read"), "the unused middle parameter is gone");
        assertEquals(2, params(out, "pass"), "b is never read");
        assertEquals(2, params(out, "keep"), "x is never read; touch still runs in its own statement");
        assertEquals(2, params(out, "touch"));
        assertEquals(3, params(out, "kernel"), "the kernel signature is the runtime ABI");
        OptTest.assertSameBehavior("dead params", src, out, NdRange.of1D(N), () -> {
            int[] in = new int[N];
            for (int i = 0; i < N; i++) {
                in[i] = 3 * i - 5;
            }
            return new Object[]{new int[N], in, new int[N]};
        });
    }

    @Test
    void parametersOnlyForwardedToDeadParametersAreDeadToo() {
        // f(a, x) = g(a, x); g(a, x) = a[0]: x мёртв в обеих
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        int gf = b.beginFunction(IrModule.DEVICE, Types.I32, arr, Types.I32);
        b.nameFunction("g");
        b.endFunction(b.block(b.returnStmt(b.load(b.elemPtr(b.param(0), b.constI32(0))))));
        int ff = b.beginFunction(IrModule.DEVICE, Types.I32, arr, Types.I32);
        b.nameFunction("f");
        b.endFunction(b.block(b.returnStmt(b.call(gf, b.param(0), b.param(1)))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), OptTest.gid(b)),
                b.call(ff, b.param(0), OptTest.gid(b)))));
        IrModule src = b.build();
        IrModule out = OptTest.opt(src);
        assertTrue(params(out, "f") == 1 && params(out, "g") == 1, () -> "f/g keep only the array");
        OptTest.assertSameBehavior("forwarded", src, out, NdRange.of1D(N), () -> {
            int[] a = new int[N];
            a[0] = 42;
            return new Object[]{a};
        });
    }
}
