package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.NdRange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Свёртка констант проверяется против эталонного интерпретатора: модуль, где все операнды - константы, исполняется до
 * и после оптимизации на всех парах крайних значений. Расхождение {@link Eval} с интерпретатором - ошибка этого теста.
 */
class FoldTest {

    private static final int[] TYPES = {Types.I32, Types.U32, Types.I64, Types.U64, Types.F32, Types.F64};

    private static final long[] INT_VALUES = {Long.MIN_VALUE, -4294967296L, -2147483649L, -2147483648L, -2, -1, 0, 1, 2,
            7, 31, 32, 33, 63, 64, 65, 2147483647L, 2147483648L, 4294967295L, Long.MAX_VALUE};
    private static final double[] FLOAT_VALUES = {0.0, -0.0, 1.0, -1.0, 0.5, -2.5, 3.0, 1e30, -1e30, 1e-30, 16777216.0,
            4294967296.0, 9.3e18, 1.9e19, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN};

    /** Значения типа: целые усечены до ширины типа, вещественные округлены. */
    private static List<Number> values(int t) {
        List<Number> out = new ArrayList<>();
        if (Types.isFloat(t)) {
            for (double d : FLOAT_VALUES) {
                out.add(t == Types.F32 ? (Number) (float) d : (Number) d);
            }
        } else {
            for (long v : INT_VALUES) {
                out.add(Types.bits(t) == 64 ? v : (long) (int) v);
            }
        }
        return out;
    }

    private static int constant(IrBuilder b, int t, Number v) {
        return switch (t) {
            case Types.F32 -> b.constF32(v.floatValue());
            case Types.F64 -> b.constF64(v.doubleValue());
            default -> b.constInt(t, v.longValue());
        };
    }

    private static boolean isZero(int t, Number v) {
        return !Types.isFloat(t) && v.longValue() == 0;
    }

    // ---- бинарные операции ---------------------------------------------------------------------

    static Stream<Object[]> binary() {
        List<Object[]> out = new ArrayList<>();
        for (int t : TYPES) {
            for (int op : new int[]{Op.ADD, Op.SUB, Op.MUL, Op.DIV, Op.REM}) {
                out.add(new Object[]{t, op});
            }
            for (int op : new int[]{Op.EQ, Op.NE, Op.LT, Op.LE, Op.GT, Op.GE}) {
                out.add(new Object[]{t, op});
            }
            if (!Types.isFloat(t)) {
                for (int op : new int[]{Op.AND, Op.OR, Op.XOR, Op.SHL, Op.SHR, Op.USHR}) {
                    out.add(new Object[]{t, op});
                }
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{1} on t{0}")
    @MethodSource("binary")
    void binaryOperationsFoldLikeTheInterpreter(int t, int op) {
        List<Number> vals = values(t);
        int n = vals.size();
        boolean compare = Op.isCompare(op);
        int outType = compare ? Types.I32 : t;

        IrModule m = OptTest.kernel(new int[]{outType}, new int[0], (b, l) -> {
            List<Integer> stmts = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if ((op == Op.DIV || op == Op.REM) && isZero(t, vals.get(j))) {
                        continue;
                    }
                    int v = b.binary(op, constant(b, t, vals.get(i)), constant(b, t, vals.get(j)));
                    if (compare) {
                        v = b.convert(Types.I32, v);
                    }
                    stmts.add(OptTest.storeAt(b, 0, b.constI32(i * n + j), v));
                }
            }
            return stmts.stream().mapToInt(Integer::intValue).toArray();
        });
        IrModule o = OptTest.opt(m);
        assertEquals(0, OptTest.count(o, op), "all operations on constants must fold");
        OptTest.assertSameBehavior(Op.nameOf(op) + "/t" + t, m, o, NdRange.of1D(1),
                () -> new Object[]{OptTest.newArray(outType, n * n)});
    }

    // ---- унарные операции ----------------------------------------------------------------------

    static Stream<Object[]> unary() {
        List<Object[]> out = new ArrayList<>();
        for (int t : TYPES) {
            out.add(new Object[]{t, Op.NEG});
            if (!Types.isFloat(t)) {
                out.add(new Object[]{t, Op.NOT});
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{1} on t{0}")
    @MethodSource("unary")
    void unaryOperationsFold(int t, int op) {
        List<Number> vals = values(t);
        IrModule m = OptTest.kernel(new int[]{t}, new int[0], (b, l) -> {
            int[] st = new int[vals.size()];
            for (int i = 0; i < st.length; i++) {
                int c = constant(b, t, vals.get(i));
                st[i] = OptTest.storeAt(b, 0, b.constI32(i), op == Op.NEG ? b.neg(c) : b.not(c));
            }
            return st;
        });
        IrModule o = OptTest.opt(m);
        assertEquals(0, OptTest.count(o, op));
        OptTest.assertSameBehavior(Op.nameOf(op) + "/t" + t, m, o, NdRange.of1D(1),
                () -> new Object[]{OptTest.newArray(t, vals.size())});
    }

    // ---- преобразования ------------------------------------------------------------------------

    static Stream<Object[]> conversions() {
        List<Object[]> out = new ArrayList<>();
        for (int from : TYPES) {
            for (int to : TYPES) {
                out.add(new Object[]{from, to});
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "t{0} -> t{1}")
    @MethodSource("conversions")
    void conversionsFold(int from, int to) {
        List<Number> vals = values(from);
        IrModule m = OptTest.kernel(new int[]{to}, new int[0], (b, l) -> {
            int[] st = new int[vals.size()];
            for (int i = 0; i < st.length; i++) {
                st[i] = OptTest.storeAt(b, 0, b.constI32(i), b.convert(to, constant(b, from, vals.get(i))));
            }
            return st;
        });
        IrModule o = OptTest.opt(m);
        assertEquals(0, OptTest.count(o, Op.CONVERT));
        OptTest.assertSameBehavior("convert t" + from + "->t" + to, m, o, NdRange.of1D(1),
                () -> new Object[]{OptTest.newArray(to, vals.size())});
    }
}
