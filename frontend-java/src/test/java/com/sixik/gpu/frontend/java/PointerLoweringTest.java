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
 * Pointer-типы во фронтенде (M9): {@code FloatPtr} как локаль, адрес которой отдаётся функциям, и явные указатели
 * {@code Global/Local/Constant*Ptr}. Исполнение (JVM против интерпретатора и устройства) проверяют дифференциальные
 * тесты образцов; здесь - какие узлы IR получаются и какие использования отвергаются.
 */
class PointerLoweringTest {

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

    private static boolean hasPtr(IrModule m, int space, int pointee) {
        for (int t = 0; t < m.typeCount(); t++) {
            if (m.typeKind(t) == Types.KIND_PTR && m.typeA(t) == space && m.typeB(t) == pointee) {
                return true;
            }
        }
        return false;
    }

    @Test
    void pointerSamplesLowerToExpectedIr() {
        Golden.assertLowers("pointers.ir.txt", """
                package demo;

                import com.sixik.gpu.api.Gpu;
                import com.sixik.gpu.api.annotations.*;
                import com.sixik.gpu.api.types.*;

                public final class Pointers {
                    @DeviceFunction
                    static void addTo(FloatPtr acc, float v) {
                        acc.value += v;
                    }

                    @DeviceFunction
                    static float twice(FloatPtr x) {
                        x.value = x.value * 2f;
                        return x.value;
                    }

                    @DeviceFunction
                    static float sum(ConstantFloatPtr t, int n) {
                        float s = 0f;
                        for (int i = 0; i < n; i++) {
                            s += t.get(i);
                        }
                        return s;
                    }

                    @DeviceFunction
                    static void scale(GlobalFloatPtr p, int n, float k) {
                        for (int i = 0; i < n; i++) {
                            p.set(i, p.get(i) * k);
                        }
                    }

                    @Kernel
                    public static void refs(@Global float[] in, @Global float[] out) {
                        int g = Gpu.getGlobalId(0);
                        FloatPtr acc = new FloatPtr();
                        addTo(acc, in[g]);
                        addTo(acc, 2f);
                        out[g] = acc.value + twice(acc);
                    }

                    @Kernel
                    public static void spaces(@Global float[] data, @Global(constant = true) float[] table,
                                              @Global float[] out) {
                        GlobalFloatPtr row = GlobalFloatPtr.of(data, 4);
                        scale(row.plus(1), 2, 3f);
                        row = row.plus(2);
                        out[0] = row.get(0) + sum(ConstantFloatPtr.of(table, 1), 3);
                    }
                }
                """);
    }

    @Test
    void referenceIsAScalarLocalWhoseAddressIsPassed() {
        FrontendHarness.Outcome r = compile("""
                @DeviceFunction static void inc(IntPtr p) { p.value++; }
                @Kernel public static void k(@Global int[] out) {
                    IntPtr c = new IntPtr(5);
                    inc(c);
                    inc(c);
                    out[0] = c.value;
                }
                """);
        assertTrue(r.success(), r.describe());
        IrModule m = r.module();
        assertEquals(3, count(m, Op.LOCAL_ADDR), "the address is taken on every use of the name: two calls and c.value");
        assertTrue(hasPtr(m, AddressSpace.PRIVATE, Types.I32));
        assertEquals(0, count(m, Op.LOCAL_ARRAY_DECL));
    }

    @Test
    void readOfValueIsOrderedBeforeTheCallThatChangesIt() {
        // Java: левый операнд читается раньше вызова; значение до вызова обязано быть сохранено
        FrontendHarness.Outcome r = compile("""
                @DeviceFunction static int bump(IntPtr p) { p.value = p.value + 10; return p.value; }
                @Kernel public static void k(@Global int[] out) {
                    IntPtr c = new IntPtr(1);
                    out[0] = c.value + bump(c);
                }
                """);
        assertTrue(r.success(), r.describe());
        assertTrue(count(r.module(), Op.LOCAL_DECL) >= 3, "c, a temporary copy of c.value and the call result");
    }

    @Test
    void pointerMethodsBecomeElemPtrLoadAndStore() {
        FrontendHarness.Outcome r = compile("""
                @Kernel public static void k(@Global int[] a) {
                    GlobalIntPtr p = GlobalIntPtr.of(a, 2);
                    p.set(1, p.get(0) + 1);
                    GlobalIntPtr q = p.plus(-1);
                    a[0] = q.get(0);
                }
                """);
        assertTrue(r.success(), r.describe());
        IrModule m = r.module();
        assertEquals(2, count(m, Op.LOAD), "p.get(0) and q.get(0)");
        assertEquals(2, count(m, Op.STORE), "p.set(...) and a[0] = ...");
        assertTrue(count(m, Op.ELEM_PTR) >= 5);
        assertTrue(hasPtr(m, AddressSpace.GLOBAL, Types.I32));
    }

    @Test
    void localPointerCanBeReassigned() {
        FrontendHarness.Outcome r = compile("""
                @Kernel public static void k(@Global long[] a) {
                    GlobalLongPtr p = GlobalLongPtr.of(a, 0);
                    for (int i = 0; i < 3; i++) {
                        p.set(0, p.get(0) + 1L);
                        p = p.plus(1);
                    }
                }
                """);
        assertTrue(r.success(), r.describe());
    }

    @Test
    void constantAndLocalPointersCarryTheirAddressSpace() {
        FrontendHarness.Outcome r = compile("""
                @DeviceFunction static double first(ConstantDoublePtr t) { return t.get(0); }
                @DeviceFunction static void put(LocalIntPtr t, int v) { t.set(0, v); }
                @Kernel public static void k(@Global(constant = true) double[] c, @Global double[] out) {
                    @Local int[] tile = new int[8];
                    put(LocalIntPtr.of(tile, 1), 7);
                    out[0] = first(ConstantDoublePtr.of(c, 0));
                }
                """);
        assertTrue(r.success(), r.describe());
        IrModule m = r.module();
        assertTrue(hasPtr(m, AddressSpace.CONSTANT, Types.F64));
        assertTrue(hasPtr(m, AddressSpace.LOCAL, Types.I32));
    }

    @Test
    void parameterPointerCanBeAdvancedAndReferenceParameterWrittenThrough() {
        FrontendHarness.Outcome r = compile("""
                @DeviceFunction static void fill(GlobalIntPtr p, int n) {
                    for (int i = 0; i < n; i++) {
                        p.set(0, i);
                        p = p.plus(1);
                    }
                }
                @DeviceFunction static void setTo(LongPtr r, long v) { r.value = v; }
                @Kernel public static void k(@Global int[] a, @Global long[] out) {
                    fill(GlobalIntPtr.of(a, 0), 4);
                    LongPtr r = new LongPtr();
                    setTo(r, 9L);
                    out[0] = r.value;
                }
                """);
        assertTrue(r.success(), r.describe());
    }

    // ---- отвергаемые использования ---------------------------------------------------------------

    @Test
    void pointerCannotBeAKernelParameter() {
        rejects("GPU0197", "@Kernel public static void k(FloatPtr p) { }");
        rejects("GPU0197", "@Kernel public static void k(GlobalFloatPtr p) { }");
    }

    @Test
    void pointerCannotBeReturnedOrStoredInArrays() {
        rejects("GPU0006", "@DeviceFunction static FloatPtr f() { return new FloatPtr(); }");
        rejects("GPU0003", "@Kernel public static void k(FloatPtr[] a) { }");
        rejects("GPU0119", "@Kernel public static void k(@Global float[] a) { GlobalFloatPtr[] ps = new GlobalFloatPtr[2]; }");
    }

    @Test
    void referenceMustBeCreatedByNewInTheDeclaration() {
        rejects("GPU0198", "@DeviceFunction static void f(FloatPtr p) { FloatPtr q = p; }");
        rejects("GPU0198", "@Kernel public static void k(@Global float[] a) { FloatPtr q; }");
        rejects("GPU0198", "@DeviceFunction static void g(FloatPtr p) { }\n"
                + "@Kernel public static void k(@Global float[] a) { g(new FloatPtr()); }");
    }

    @Test
    void referenceCannotBeReassignedOrUsedAsAValue() {
        rejects("GPU0198", "@Kernel public static void k(@Global float[] a) { FloatPtr q = new FloatPtr(); q = new FloatPtr(); }");
        rejects("GPU0198", "@DeviceFunction static void f(FloatPtr p) { p = new FloatPtr(); }");
        rejects("GPU0198", "@DeviceFunction static boolean g(FloatPtr p, FloatPtr q) { return p == q; }");
        rejects("GPU0198", "@DeviceFunction static void g(FloatPtr p, @Global float[] a) { Object o = p; }");
    }

    @Test
    void referenceCannotBeSelectedByConditionalOrPassedToBuiltins() {
        rejects("GPU0198", "@DeviceFunction static float f(boolean c, FloatPtr a, FloatPtr b) { return (c ? a : b).value; }");
    }

    @Test
    void explicitPointersAreComparedByValueNotByAddress() {
        rejects("GPU0198", "@Kernel public static void k(@Global int[] a) { GlobalIntPtr p = GlobalIntPtr.of(a, 0);"
                + " boolean same = p == p.plus(1); }");
    }

    @Test
    void explicitPointerNeedsAnInitializerAndIsNotConstructedDirectly() {
        rejects("GPU0198", "@Kernel public static void k(@Global int[] a) { GlobalIntPtr p; }");
    }

    @Test
    void pointerAssignmentIsAStatementNotAnExpression() {
        rejects("GPU0198", "@Kernel public static void k(@Global int[] a) { GlobalIntPtr p = GlobalIntPtr.of(a, 0);"
                + " GlobalIntPtr q = GlobalIntPtr.of(a, 1); q = (p = q); }");
    }

    @Test
    void pointerOfFromWrongAddressSpaceIsRejected() {
        rejects("GPU0199", "@Kernel public static void k(@Global int[] a) { ConstantIntPtr p = ConstantIntPtr.of(a, 0); }");
        rejects("GPU0199", "@Kernel public static void k(@Global(constant = true) int[] a) { GlobalIntPtr p = GlobalIntPtr.of(a, 0); }");
        rejects("GPU0199", "@Kernel public static void k(@Global int[] a) { int[] t = new int[4]; GlobalIntPtr p = GlobalIntPtr.of(t, 0); }");
    }

    @Test
    void unknownPointerMethodsAreRejected() {
        rejects("GPU0112", "@Kernel public static void k(@Global int[] a) { GlobalIntPtr p = GlobalIntPtr.of(a, 0); String s = p.toString(); }");
    }

    @Test
    void referenceHasOnlyTheValueField() {
        rejects("GPU0112", "@DeviceFunction static void f(FloatPtr p) { String s = p.toString(); }");
    }

    @Test
    void writingToConstantArrayIsRejectedByTheFrontend() {
        rejects("GPU0122", "@Kernel public static void k(@Global(constant = true) float[] c) { c[0] = 1f; }");
        rejects("GPU0122", "@Kernel public static void k(@Global(constant = true) float[] c) { c[0] += 1f; }");
        rejects("GPU0122", "@Kernel public static void k(@Global(constant = true) int[] c) { c[1]++; }");
    }

    @Test
    void readingConstantArrayIsFine() {
        FrontendHarness.Outcome r = compile("""
                @Kernel public static void k(@Global(constant = true) float[] c, @Global float[] out) {
                    out[Gpu.getGlobalId(0)] = c[0] + c[1];
                }
                """);
        assertTrue(r.success(), r.describe());
        assertTrue(hasPtr(r.module(), AddressSpace.CONSTANT, Types.F32));
    }
}
