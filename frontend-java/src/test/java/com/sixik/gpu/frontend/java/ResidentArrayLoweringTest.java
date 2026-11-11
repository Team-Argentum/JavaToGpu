package com.sixik.gpu.frontend.java;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code GpuFloatArray} и другие массивы с резидентностью на устройстве: в IR это обычный массив {@code global}, а
 * {@code get/set/length} - те же узлы, что {@code a[i]}, {@code a[i] = v} и {@code a.length}.
 */
class ResidentArrayLoweringTest {

    private static final String HEAD = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.*;
            import com.sixik.gpu.api.types.*;

            public final class T {
            """;

    private static FrontendHarness.Outcome compile(String members) {
        return FrontendHarness.compile(HEAD + members + "\n}\n");
    }

    private static void rejects(String code, String members) {
        FrontendHarness.Outcome r = compile(members);
        assertFalse(r.success(), "expected error " + code + "\n" + r.describe());
        assertTrue(r.errorCodes().contains(code), code + " was not reported; reported: " + r.errorCodes() + "\n" + r.describe());
    }

    private static int count(IrModule m, int op) {
        int n = 0;
        for (int i = 0; i < m.nodeCount(); i++) {
            if (m.op(i) == op) {
                n++;
            }
        }
        return n;
    }

    @Test
    void residentArraysLowerLikeGlobalArrays() {
        FrontendHarness.Outcome r = compile("""
                @Kernel
                public static void scale(GpuFloatArray in, GpuFloatArray out, GpuIntArray hits, float k) {
                    int i = Gpu.getGlobalId(0);
                    if (i < in.length()) {
                        out.set(i, in.get(i) * k);
                        hits.set(i, hits.get(i) + 1);
                    }
                }
                """);
        assertTrue(r.success(), r.describe());
        IrModule m = r.module();
        int f = m.functionCount() - 1;
        assertEquals(4, m.fParamCount(f));
        int[] elems = {Types.F32, Types.F32, Types.I32};
        for (int p = 0; p < 3; p++) {
            int t = m.fParamType(f, p);
            assertEquals(Types.KIND_ARRAY, m.typeKind(t), "param " + p);
            assertEquals(AddressSpace.GLOBAL, m.typeA(t));
            assertEquals(elems[p], m.typeB(t));
        }
        assertEquals(Types.F32, m.fParamType(f, 3));
        assertEquals(1, count(m, Op.ARRAY_LEN));
        assertEquals(2, count(m, Op.LOAD));
        assertEquals(2, count(m, Op.STORE));
    }

    @Test
    void sameIrAsPlainJavaArrays() {
        FrontendHarness.Outcome resident = compile("""
                @Kernel
                public static void add(GpuDoubleArray a, GpuDoubleArray out) {
                    int i = Gpu.getGlobalId(0);
                    out.set(i, a.get(i) + a.length());
                }
                """);
        FrontendHarness.Outcome plain = compile("""
                @Kernel
                public static void add(@Global double[] a, @Global double[] out) {
                    int i = Gpu.getGlobalId(0);
                    out[i] = a[i] + a.length;
                }
                """);
        assertTrue(resident.success(), resident.describe());
        assertTrue(plain.success(), plain.describe());
        IrModule x = resident.module();
        IrModule y = plain.module();
        // порядок создания узлов может отличаться (у set приёмник вычисляется раньше значения), состав - тот же
        assertEquals(y.nodeCount(), x.nodeCount());
        for (int op = 1; op < Op.LIMIT; op++) {
            assertEquals(count(y, op), count(x, op), Op.nameOf(op));
        }
        for (int p = 0; p < 2; p++) {
            assertEquals(y.fParamType(0, p), x.fParamType(0, p));
        }
    }

    @Test
    void residentArraysPassToDeviceFunctions() {
        FrontendHarness.Outcome r = compile("""
                @DeviceFunction
                static float at(GpuLongArray a, int i) {
                    return a.get(i) * 0.5f;
                }

                @Kernel
                public static void k(GpuLongArray a, GpuFloatArray out) {
                    int i = Gpu.getGlobalId(0);
                    out.set(i, at(a, i));
                }
                """);
        assertTrue(r.success(), r.describe());
        assertEquals(2, r.module().functionCount());
    }

    @Test
    void hostOnlyMethodsAndLocalsAreRejected() {
        rejects("GPU0112", """
                @Kernel
                public static void k(GpuFloatArray a) {
                    a.fill(1f);
                }
                """);
        rejects("GPU0116", """
                @Kernel
                public static void k(GpuFloatArray a) {
                    GpuFloatArray b = a;
                    b.set(0, 1f);
                }
                """);
    }
}
