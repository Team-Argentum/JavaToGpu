package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.NdRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Coalesce}: соседние обращения ядра к буферу склеиваются в {@code vloadN/vstoreN} только там, где это не меняет
 * семантику Java; каждый случай сверяется с интерпретатором, в том числе когда один массив передан в два параметра.
 */
class CoalesceTest {

    private static final int VLOAD2 = Builtins.idOf("vload2");
    private static final int VLOAD4 = Builtins.idOf("vload4");
    private static final int VSTORE2 = Builtins.idOf("vstore2");
    private static final int VSTORE4 = Builtins.idOf("vstore4");
    private static final int N = 8;

    /** Форма индекса {@code S*i + c}. */
    enum Index { MUL, SHL, LOCAL }

    /**
     * {@code od[S*i+c] = od[S*i+c] + (a[S*i+c] - od[S*i+c]) * t} для {@code c = 0..S-1} - по стейтменту на полосу
     * (как в ядре GA2).
     */
    static IrModule lerp(int elem, int stride, Index form) {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, elem);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, arr, elem);
        int i = b.declareLocal(Types.I32);
        int base = b.declareLocal(Types.I32);
        List<Integer> st = new ArrayList<>();
        st.add(b.localDecl(i, b.intrinsic(OptTest.GET_GLOBAL_ID, b.constI32(0))));
        st.add(b.localDecl(base, b.binary(Op.MUL, b.localGet(i), b.constI32(stride))));
        for (int c = 0; c < stride; c++) {
            int[] idx = new int[3];
            for (int k = 0; k < 3; k++) {
                int s = switch (form) {
                    case MUL -> b.binary(Op.MUL, b.constI32(stride), b.localGet(i));
                    case SHL -> b.binary(Op.SHL, b.localGet(i), b.constI32(Integer.numberOfTrailingZeros(stride)));
                    case LOCAL -> b.localGet(base);
                };
                idx[k] = c == 0 ? s : b.binary(Op.ADD, s, b.constI32(c));
            }
            int o = b.load(b.elemPtr(b.param(0), idx[0]));
            int x = b.load(b.elemPtr(b.param(1), idx[1]));
            int o2 = b.load(b.elemPtr(b.param(0), idx[2]));
            int v = b.binary(Op.ADD, o, b.binary(Op.MUL, b.binary(Op.SUB, x, o2), b.param(2)));
            int si = c == 0 ? (form == Index.LOCAL ? b.localGet(base) : b.binary(Op.MUL, b.localGet(i),
                    b.constI32(stride))) : b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(i), b.constI32(stride)),
                    b.constI32(c));
            st.add(b.store(b.elemPtr(b.param(0), si), v));
        }
        b.endFunction(b.block(st.stream().mapToInt(Integer::intValue).toArray()));
        return b.build();
    }

    private static Object data(int elem, int len, long seed) {
        Random r = new Random(seed);
        return switch (elem) {
            case Types.F32 -> {
                float[] f = new float[len];
                for (int k = 0; k < len; k++) {
                    f[k] = r.nextFloat() * 10f - 5f;
                }
                yield f;
            }
            case Types.F64 -> {
                double[] d = new double[len];
                for (int k = 0; k < len; k++) {
                    d[k] = r.nextDouble() * 10 - 5;
                }
                yield d;
            }
            default -> {
                int[] v = new int[len];
                for (int k = 0; k < len; k++) {
                    v[k] = r.nextInt(1000) - 500;
                }
                yield v;
            }
        };
    }

    private static Object scalar(int elem) {
        return switch (elem) {
            case Types.F32 -> 0.25f;
            case Types.F64 -> 0.25;
            default -> 3;
        };
    }

    /** Сверка с исходным модулем: раздельные массивы и один массив в обоих параметрах. */
    private static void sameBehavior(String label, IrModule m, IrModule opt, int elem, int len) {
        OptTest.assertSameBehavior(label + " separate", m, opt, NdRange.of1D(N),
                () -> new Object[]{data(elem, len, 1), data(elem, len, 2), scalar(elem)});
        OptTest.assertSameBehavior(label + " aliased", m, opt, NdRange.of1D(N), () -> {
            Object x = data(elem, len, 3);
            return new Object[]{x, x, scalar(elem)};
        });
    }

    @Test
    void lerpOverFloat4LanesBecomesTwoLoadsAndOneStore() {
        for (Index form : Index.values()) {
            IrModule m = lerp(Types.F32, 4, form);
            IrModule opt = OptTest.opt(m);
            assertEquals(2, OptTest.countIntrinsic(opt, VLOAD4), form + ": od and a are loaded as float4");
            assertEquals(1, OptTest.countIntrinsic(opt, VSTORE4), form + ": od is stored as float4");
            assertEquals(0, OptTest.count(opt, Op.LOAD), form + ": no scalar loads left");
            assertEquals(0, OptTest.count(opt, Op.STORE), form + ": no scalar stores left");
            sameBehavior("float4 " + form, m, opt, Types.F32, 4 * N);
        }
    }

    @Test
    void doublesAndIntsUseSixteenByteVectors() {
        IrModule d = lerp(Types.F64, 4, Index.MUL);
        IrModule od = OptTest.opt(d);
        assertEquals(4, OptTest.countIntrinsic(od, VLOAD2), "double: lanes 0-1 and 2-3 of od and a");
        assertEquals(2, OptTest.countIntrinsic(od, VSTORE2));
        assertEquals(0, OptTest.countIntrinsic(od, VLOAD4), "double4 would be two transactions anyway");
        sameBehavior("double", d, od, Types.F64, 4 * N);

        IrModule i = lerp(Types.I32, 2, Index.MUL);
        IrModule oi = OptTest.opt(i);
        assertEquals(2, OptTest.countIntrinsic(oi, VLOAD2));
        assertEquals(1, OptTest.countIntrinsic(oi, VSTORE2));
        sameBehavior("int2", i, oi, Types.I32, 2 * N);
    }

    @Test
    void levelOneKeepsScalarAccesses() {
        IrModule opt = OptTest.opt(lerp(Types.F32, 4, Index.MUL), OptimizeOptions.DEFAULT.withLevel(1));
        assertEquals(0, OptTest.countIntrinsic(opt, VLOAD4));
    }

    /**
     * {@code x = a[4i]; <между>; y = a[4i+1] + a[4i+2] + a[4i+3]; out[i] = x + y} - что стоит между загрузками,
     * решает, можно ли склеить полосы 0-1 (полосы 2-3 стоят в одном стейтменте и склеиваются всегда).
     */
    static IrModule between(int what) {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        int ints = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, arr, ints);
        int i = b.declareLocal(Types.I32);
        int x = b.declareLocal(Types.F32);
        int y = b.declareLocal(Types.F32);
        List<Integer> st = new ArrayList<>();
        st.add(b.localDecl(i, b.intrinsic(OptTest.GET_GLOBAL_ID, b.constI32(0))));
        st.add(b.localDecl(x, b.load(b.elemPtr(b.param(1), b.binary(Op.MUL, b.localGet(i), b.constI32(4))))));
        switch (what) {
            case 0 -> { // запись в ту же полосу 1 того же массива
                st.add(b.store(b.elemPtr(b.param(1), b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(i),
                        b.constI32(4)), b.constI32(1))), b.constF32(7f)));
            }
            case 1 -> { // запись в float-массив по другому индексу: может перекрыться
                st.add(b.store(b.elemPtr(b.param(0), b.binary(Op.ADD, b.localGet(i), b.constI32(1))),
                        b.constF32(9f)));
            }
            case 2 -> { // запись в int-массив: массивы Java разного типа не перекрываются
                st.add(b.store(b.elemPtr(b.param(2), b.localGet(i)), b.constI32(5)));
            }
            case 3 -> { // запись в полосу 0: другая полоса той же группы
                st.add(b.store(b.elemPtr(b.param(1), b.binary(Op.MUL, b.localGet(i), b.constI32(4))),
                        b.constF32(1f)));
            }
            default -> { // ранний выход
                st.add(b.ifStmt(b.binary(Op.GT, b.localGet(x), b.constF32(100f)), b.block(b.returnStmt(-1)), -1));
            }
        }
        int sum = -1;
        for (int c = 1; c < 4; c++) {
            int idx = b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(i), b.constI32(4)), b.constI32(c));
            sum = c == 1 ? b.load(b.elemPtr(b.param(1), idx))
                    : b.binary(Op.ADD, sum, b.load(b.elemPtr(b.param(1), idx)));
        }
        st.add(b.localDecl(y, sum));
        st.add(b.store(b.elemPtr(b.param(0), b.localGet(i)), b.binary(Op.ADD, b.localGet(x), b.localGet(y))));
        b.endFunction(b.block(st.stream().mapToInt(Integer::intValue).toArray()));
        return b.build();
    }

    @Test
    void writesAndExitsBetweenLoadsDecideWhatIsMerged() {
        String[] names = {"same lane", "float array", "int array", "other lane", "early return"};
        int[] float4 = {0, 0, 1, 1, 0};
        int[] float2 = {1, 1, 0, 0, 1};
        for (int w = 0; w < names.length; w++) {
            IrModule m = between(w);
            IrModule opt = OptTest.opt(m);
            assertEquals(float4[w], OptTest.countIntrinsic(opt, VLOAD4), names[w]);
            assertEquals(float2[w], OptTest.countIntrinsic(opt, VLOAD2), names[w]);
            String label = names[w];
            OptTest.assertSameBehavior(label, m, opt, NdRange.of1D(N),
                    () -> new Object[]{data(Types.F32, 4 * N + 1, 4), data(Types.F32, 4 * N, 5), new int[N]});
            OptTest.assertSameBehavior(label + " aliased", m, opt, NdRange.of1D(N), () -> {
                Object a = data(Types.F32, 4 * N + 1, 6);
                return new Object[]{a, a, new int[N]};
            });
        }
    }

    /** Не склеиваются: неполный блок полос, загрузки в ветке, обращения в device-функции. */
    @Test
    void incompleteConditionalAndDeviceAccessesStayScalar() {
        // полосы 0..2 из 4: блок 0-1 склеивается в float2, полоса 2 остаётся
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, arr);
        int i = b.declareLocal(Types.I32);
        int sum = -1;
        for (int c = 0; c < 3; c++) {
            int l = b.load(b.elemPtr(b.param(1), b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(i), b.constI32(4)),
                    b.constI32(c))));
            sum = sum < 0 ? l : b.binary(Op.ADD, sum, l);
        }
        b.endFunction(b.block(b.localDecl(i, b.intrinsic(OptTest.GET_GLOBAL_ID, b.constI32(0))),
                b.store(b.elemPtr(b.param(0), b.localGet(i)), sum)));
        IrModule partial = OptTest.opt(b.build());
        assertEquals(0, OptTest.countIntrinsic(partial, VLOAD4));
        assertEquals(1, OptTest.countIntrinsic(partial, VLOAD2));

        // все четыре полосы, но под условием
        b = new IrBuilder();
        arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, arr);
        i = b.declareLocal(Types.I32);
        sum = -1;
        for (int c = 0; c < 4; c++) {
            int l = b.load(b.elemPtr(b.param(1), b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(i), b.constI32(4)),
                    b.constI32(c))));
            sum = sum < 0 ? l : b.binary(Op.ADD, sum, l);
        }
        b.endFunction(b.block(b.localDecl(i, b.intrinsic(OptTest.GET_GLOBAL_ID, b.constI32(0))),
                b.ifStmt(b.binary(Op.LT, b.localGet(i), b.constI32(4)),
                        b.block(b.store(b.elemPtr(b.param(0), b.localGet(i)), sum)), -1)));
        IrModule guardedIn = b.build();
        IrModule guarded = OptTest.opt(guardedIn);
        assertEquals(1, OptTest.countIntrinsic(guarded, VLOAD4), "the branch is its own block: merged inside it");
        assertEquals(1, OptTest.inside(guarded, ifNode(guarded), Op.INTRINSIC) ? 1 : 0,
                "the vector load stays under the condition");

        // device-функция: выравнивание параметра неизвестно
        b = new IrBuilder();
        arr = b.array(AddressSpace.GLOBAL, Types.F32);
        int dev = b.beginFunction(IrModule.DEVICE, Types.F32, arr, Types.I32);
        sum = -1;
        for (int c = 0; c < 4; c++) {
            int l = b.load(b.elemPtr(b.param(0), b.binary(Op.ADD, b.binary(Op.MUL, b.param(1), b.constI32(4)),
                    b.constI32(c))));
            sum = sum < 0 ? l : b.binary(Op.ADD, sum, l);
        }
        b.endFunction(b.block(b.returnStmt(sum)));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, arr);
        i = b.declareLocal(Types.I32);
        b.endFunction(b.block(b.localDecl(i, b.intrinsic(OptTest.GET_GLOBAL_ID, b.constI32(0))),
                b.store(b.elemPtr(b.param(0), b.localGet(i)), b.call(dev, b.param(1), b.localGet(i)))));
        IrModule device = OptTest.opt(b.build(), OptimizeOptions.DEFAULT);
        assertEquals(0, OptTest.countIntrinsic(device, VLOAD4) - inlinedKernelLoads(device));
    }

    private static int ifNode(IrModule m) {
        for (int n = 0; n < m.nodeCount(); n++) {
            if (m.op(n) == Op.IF) {
                return n;
            }
        }
        throw new AssertionError("no if");
    }

    /** vload4 в ядре (если оптимизатор встроил функцию, ядро вправе склеить уже свои обращения). */
    private static int inlinedKernelLoads(IrModule m) {
        int kernel = OptTest.kernelIndex(m);
        int c = 0;
        for (int n = 0; n < m.nodeCount(); n++) {
            if (m.op(n) == Op.INTRINSIC && m.a(n) == VLOAD4 && OptTest.inside(m, m.fBody(kernel), Op.INTRINSIC)
                    && reachable(m, m.fBody(kernel), n)) {
                c++;
            }
        }
        return c;
    }

    private static boolean reachable(IrModule m, int root, int target) {
        if (root == target) {
            return true;
        }
        int cnt = com.sixik.gpu.ir.Operands.count(m, root);
        for (int k = 0; k < cnt; k++) {
            if (reachable(m, com.sixik.gpu.ir.Operands.get(m, root, k), target)) {
                return true;
            }
        }
        return false;
    }
}
