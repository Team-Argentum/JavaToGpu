package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.optimizer.OptimizeOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Склеивание обращений {@code od[4*i+0..3]} в {@code float4}/{@code double2} на настоящем устройстве: результат равен
 * интерпретатору на исходном модуле, и при раздельных массивах, и когда один массив передан в оба параметра.
 * Запуск: {@code ./gradlew :runtime:deviceTest}.
 */
@Tag("device")
class CoalesceDeviceTest {

    private static GpuContext ctx;

    @BeforeAll
    static void open() {
        Result<GpuContext> r = GpuContext.builder().optimize(OptimizeOptions.DEFAULT.withLevel(2)).tryBuild();
        Assumptions.assumeTrue(r.isOk(), "no OpenCL device: " + r.diagnostics());
        ctx = r.value();
    }

    @AfterAll
    static void close() {
        if (ctx != null) {
            ctx.close();
        }
    }

    /** {@code od[4i+c] = od[4i+c] + (a[4i+c] - od[4i+c]) * t}, {@code c = 0..3} - по стейтменту на полосу. */
    static IrModule lerp(int elem) {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, elem);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, arr, elem);
        int i = b.declareLocal(Types.I32);
        List<Integer> st = new ArrayList<>();
        st.add(b.localDecl(i, b.intrinsic(0, b.constI32(0))));
        for (int c = 0; c < 4; c++) {
            int[] idx = new int[4];
            for (int k = 0; k < 4; k++) {
                idx[k] = b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(i), b.constI32(4)), b.constI32(c));
            }
            int v = b.binary(Op.ADD, b.load(b.elemPtr(b.param(0), idx[0])),
                    b.binary(Op.MUL, b.binary(Op.SUB, b.load(b.elemPtr(b.param(1), idx[1])),
                            b.load(b.elemPtr(b.param(0), idx[2]))), b.param(2)));
            st.add(b.store(b.elemPtr(b.param(0), idx[3]), v));
        }
        b.endFunction(b.block(st.stream().mapToInt(Integer::intValue).toArray()));
        return b.build();
    }

    private static Object fill(int elem, int len, long seed) {
        Random r = new Random(seed);
        if (elem == Types.F32) {
            float[] f = new float[len];
            for (int k = 0; k < len; k++) {
                f[k] = r.nextFloat() * 10f - 5f;
            }
            return f;
        }
        double[] d = new double[len];
        for (int k = 0; k < len; k++) {
            d[k] = r.nextDouble() * 10 - 5;
        }
        return d;
    }

    private static Object copy(Object a) {
        return a instanceof float[] f ? f.clone() : ((double[]) a).clone();
    }

    private static void check(int elem, boolean aliased) {
        IrModule m = lerp(elem);
        CompiledKernel k = ctx.compile(m).value();
        String src = k.artifact().source();
        String vec = elem == Types.F32 ? "float4*)" : "double2*)";
        assertTrue(src.contains(vec), src);

        int n = 1024;
        Object od = fill(elem, 4 * n, 1);
        Object a = aliased ? od : fill(elem, 4 * n, 2);
        Object t = elem == Types.F32 ? (Object) 0.25f : (Object) 0.25;
        Object eod = copy(od);
        Object ea = aliased ? eod : copy(a);
        Result<Interpreter.Execution> r = Interpreter.run(m, 0, NdRange.of1D(n), Interpreter.Options.DEFAULT,
                new Object[]{eod, ea, t});
        assertTrue(r.isOk(), r.diagnostics().toString());
        Result<LaunchStats> launched = ctx.launch(k, LaunchConfig.of1D(n), od, a, t);
        assertTrue(launched.isOk(), launched.diagnostics().toString());
        if (elem == Types.F32) {
            assertArrayEquals((float[]) eod, (float[]) od);
        } else {
            assertArrayEquals((double[]) eod, (double[]) od);
        }
    }

    @Test
    void float4LanesMatchTheInterpreter() {
        check(Types.F32, false);
        check(Types.F32, true);
    }

    @Test
    void double2LanesMatchTheInterpreter() {
        check(Types.F64, false);
        check(Types.F64, true);
    }
}
