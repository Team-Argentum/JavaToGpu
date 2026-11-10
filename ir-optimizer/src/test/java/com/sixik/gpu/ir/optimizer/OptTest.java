package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Operands;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.interpreter.samples.SampleArgs;

import java.util.Arrays;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Общие помощники тестов оптимизатора: оптимизация с проверкой, подсчёт узлов, дифференциальный запуск
 * исходного и оптимизированного модулей в интерпретаторе.
 */
final class OptTest {

    static final int SIN = Builtins.idOf("sin");
    static final int SQRT = Builtins.idOf("sqrt");
    static final int GET_GLOBAL_ID = Builtins.idOf("getGlobalId");

    private OptTest() {
    }

    // ---- оптимизация ---------------------------------------------------------------------------

    static IrModule opt(IrModule m) {
        return opt(m, OptimizeOptions.DEFAULT);
    }

    static IrModule opt(IrModule m, OptimizeOptions o) {
        OptimizeResult r = Optimizer.optimize(m, o);
        assertTrue(r.ok(), () -> "optimizer rejected passes: " + r.failures());
        Validator.Report v = Validator.validate(r.module());
        assertFalse(v.hasErrors(), () -> "optimized module is invalid: " + v.diagnostics());
        return r.module();
    }

    // ---- подсчёт узлов -------------------------------------------------------------------------

    static int count(IrModule m, int op) {
        int c = 0;
        for (int n = 0; n < m.nodeCount(); n++) {
            if (m.op(n) == op) {
                c++;
            }
        }
        return c;
    }

    static int countIntrinsic(IrModule m, int builtin) {
        int c = 0;
        for (int n = 0; n < m.nodeCount(); n++) {
            if (m.op(n) == Op.INTRINSIC && m.a(n) == builtin) {
                c++;
            }
        }
        return c;
    }

    /** Есть ли в поддереве узла {@code root} узел с опкодом {@code op} (в том числе в стейтментах-детях). */
    static boolean inside(IrModule m, int root, int op) {
        if (m.op(root) == op) {
            return true;
        }
        int cnt = Operands.count(m, root);
        for (int k = 0; k < cnt; k++) {
            if (inside(m, Operands.get(m, root, k), op)) {
                return true;
            }
        }
        return false;
    }

    /** Есть ли builtin {@code builtin} внутри цикла (любого). */
    static boolean intrinsicInsideLoop(IrModule m, int builtin) {
        for (int n = 0; n < m.nodeCount(); n++) {
            if (m.op(n) == Op.LOOP || m.op(n) == Op.DO_WHILE) {
                if (insideIntrinsic(m, n, builtin)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean insideIntrinsic(IrModule m, int root, int builtin) {
        if (m.op(root) == Op.INTRINSIC && m.a(root) == builtin) {
            return true;
        }
        int cnt = Operands.count(m, root);
        for (int k = 0; k < cnt; k++) {
            if (insideIntrinsic(m, Operands.get(m, root, k), builtin)) {
                return true;
            }
        }
        return false;
    }

    static int kernelIndex(IrModule m) {
        for (int f = m.functionCount() - 1; f >= 0; f--) {
            if (m.fKind(f) == IrModule.KERNEL) {
                return f;
            }
        }
        throw new AssertionError("no kernel");
    }

    // ---- исполнение ----------------------------------------------------------------------------

    /** Исполняет оба модуля с одинаковыми аргументами и сверяет результаты побитово. */
    static void assertSameBehavior(String label, IrModule expected, IrModule actual, NdRange range,
                                   Supplier<Object[]> args) {
        Object[] a = args.get();
        Object[] b = args.get();
        Result<Interpreter.Execution> ra = Interpreter.run(expected, kernelIndex(expected), range,
                new Interpreter.Options(50_000_000L), a);
        Result<Interpreter.Execution> rb = Interpreter.run(actual, kernelIndex(actual), range,
                new Interpreter.Options(50_000_000L), b);
        assertEquals(ra.isOk(), rb.isOk(), () -> label + ": the original " + (ra.isOk() ? "ran" : "failed with "
                + ra.diagnostics()) + ", the optimized " + (rb.isOk() ? "ran" : "failed with " + rb.diagnostics()));
        if (!ra.isOk()) {
            return;
        }
        for (int i = 0; i < a.length; i++) {
            compare(label, i, a[i], b[i]);
        }
    }

    static void compare(String label, int arg, Object x, Object y) {
        if (x instanceof int[] p) {
            assertTrue(Arrays.equals(p, (int[]) y), () -> label + " arg" + arg + ": " + Arrays.toString(p) + " vs "
                    + Arrays.toString((int[]) y));
        } else if (x instanceof long[] p) {
            assertTrue(Arrays.equals(p, (long[]) y), () -> label + " arg" + arg + ": " + Arrays.toString(p) + " vs "
                    + Arrays.toString((long[]) y));
        } else if (x instanceof float[] p) {
            float[] q = (float[]) y;
            for (int i = 0; i < p.length; i++) {
                if (Float.floatToIntBits(p[i]) != Float.floatToIntBits(q[i])) {
                    fail(label + " arg" + arg + "[" + i + "]: " + p[i] + " vs " + q[i]);
                }
            }
        } else if (x instanceof double[] p) {
            double[] q = (double[]) y;
            for (int i = 0; i < p.length; i++) {
                if (Double.doubleToLongBits(p[i]) != Double.doubleToLongBits(q[i])) {
                    fail(label + " arg" + arg + "[" + i + "]: " + p[i] + " vs " + q[i]);
                }
            }
        } else if (x instanceof short[] p) {
            assertTrue(Arrays.equals(p, (short[]) y), label + " arg" + arg);
        } else if (x instanceof byte[] p) {
            assertTrue(Arrays.equals(p, (byte[]) y), label + " arg" + arg);
        } else if (x instanceof char[] p) {
            assertTrue(Arrays.equals(p, (char[]) y), label + " arg" + arg);
        } else if (x instanceof Object[] p) {
            Object[] q = (Object[]) y;
            for (int i = 0; i < p.length; i++) {
                String d = SampleArgs.objectDifference(p[i], q[i], 0);
                if (d != null) {
                    fail(label + " arg" + arg + "[" + i + "]: " + d);
                }
            }
        } else if (x != null && !x.getClass().isPrimitive() && !(x instanceof Number) && !(x instanceof Boolean)) {
            String d = SampleArgs.objectDifference(x, y, 0);
            if (d != null) {
                fail(label + " arg" + arg + ": " + d);
            }
        }
    }

    // ---- построение модулей --------------------------------------------------------------------

    /** Содержимое тела ядра. */
    interface Body {
        int[] statements(IrBuilder b, int[] locals);
    }

    /** Ядро с параметрами-массивами указанных типов элементов и {@code locals} локалями типа {@code localType}. */
    static IrModule kernel(int[] elemTypes, int[] localTypes, Body body) {
        IrBuilder b = new IrBuilder();
        int[] params = new int[elemTypes.length];
        for (int i = 0; i < params.length; i++) {
            params[i] = b.array(AddressSpace.GLOBAL, elemTypes[i]);
        }
        b.beginFunction(IrModule.KERNEL, Types.VOID, params);
        int[] locals = new int[localTypes.length];
        for (int i = 0; i < locals.length; i++) {
            locals[i] = b.declareLocal(localTypes[i]);
        }
        b.endFunction(b.block(body.statements(b, locals)));
        return b.build();
    }

    /** {@code array[index] = value}. */
    static int storeAt(IrBuilder b, int param, int index, int value) {
        return b.store(b.elemPtr(b.param(param), index), value);
    }

    /** {@code array[index]}. */
    static int loadAt(IrBuilder b, int param, int index) {
        return b.load(b.elemPtr(b.param(param), index));
    }

    static int gid(IrBuilder b) {
        return b.intrinsic(GET_GLOBAL_ID, b.constI32(0));
    }

    static Object newArray(int elem, int n) {
        return switch (elem) {
            case Types.I32, Types.U32 -> new int[n];
            case Types.I64, Types.U64 -> new long[n];
            case Types.F32 -> new float[n];
            default -> new double[n];
        };
    }
}
