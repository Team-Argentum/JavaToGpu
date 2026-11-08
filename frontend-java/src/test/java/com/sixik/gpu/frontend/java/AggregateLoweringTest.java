package com.sixik.gpu.frontend.java;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Векторы и {@code @Struct} во фронтенде: какие узлы и типы IR получаются, какие правила value-семантики (3.2)
 * соблюдаются. Исполнение (JVM против интерпретатора и устройства) проверяют дифференциальные тесты образцов.
 */
class AggregateLoweringTest {

    private static final String HEAD = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.*;
            import com.sixik.gpu.api.types.*;

            public final class T {
                @Struct public static final class P {
                    public float x;
                    public Float4 v;
                    public int id;
                    public P() { this.v = new Float4(0, 0, 0, 0); }
                    public P(float x, Float4 v, int id) { this.x = x; this.v = v; this.id = id; }
                    public P copy() { return new P(x, v, id); }
                }
                @Struct public static final class Q {
                    public P p;
                    public double m;
                    public Q() { this.p = new P(); }
                    public Q(P p, double m) { this.p = p; this.m = m; }
                    public Q copy() { return new Q(p.copy(), m); }
                }
            """;

    private static FrontendHarness.Outcome compile(String members) {
        return FrontendHarness.compile(HEAD + members + "\n}\n");
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

    private static boolean hasType(IrModule m, int kind) {
        for (int t = 0; t < m.typeCount(); t++) {
            if (m.typeKind(t) == kind) {
                return true;
            }
        }
        return false;
    }

    @Test
    void vectorOperationsBecomeVectorNodes() {
        FrontendHarness.Outcome r = compile("""
                @Kernel public static void k(@Global Float4[] a, @Global float[] out) {
                    Float4 v = a[0];
                    Float4 w = v.add(Float4.splat(2f)).mul(0.5f).withY(1f).neg();
                    Float2 s = w.shuffle(3, 0);
                    out[0] = w.x + s.y + Gpu.sin(v).z;
                    a[1] = new Float4(v.x, w.y, 3, 4);
                }
                """);
        assertTrue(r.success(), r.describe());
        IrModule m = r.module();
        assertEquals(1, count(m, Op.SHUFFLE));
        assertEquals(1, count(m, Op.INSERT));
        assertTrue(count(m, Op.SPLAT) >= 2, "Float4.splat и скаляр-операнд mul размножаются");
        assertEquals(1, count(m, Op.BUILD));
        assertTrue(count(m, Op.EXTRACT) >= 4);
        assertEquals(1, count(m, Op.NEG));
        assertTrue(hasType(m, Types.KIND_VEC));
        assertFalse(hasType(m, Types.KIND_STRUCT));
    }

    @Test
    void integerVectorBitOperations() {
        FrontendHarness.Outcome r = compile("""
                @Kernel public static void k(@Global Int4[] a) {
                    Int4 v = a[0];
                    a[1] = v.and(5).or(v.xor(v)).shl(3).shr(1).ushr(2).not().rem(v).div(7);
                }
                """);
        assertTrue(r.success(), r.describe());
        IrModule m = r.module();
        for (int op : new int[]{Op.AND, Op.OR, Op.XOR, Op.SHL, Op.SHR, Op.USHR, Op.NOT, Op.REM, Op.DIV}) {
            assertEquals(1, count(m, op), Op.nameOf(op));
        }
    }

    @Test
    void structFieldsBecomeFieldNodes() {
        FrontendHarness.Outcome r = compile("""
                @Kernel public static void k(@Global P[] ps, @Global Q[] qs, @Global float[] out) {
                    P p = ps[0].copy();
                    p.x = p.x + 1;
                    p.id++;
                    qs[1].p.x = 5f;
                    Q q = new Q(p.copy(), 2.0);
                    q.m += 1;
                    out[0] = q.p.v.w + qs[0].p.x;
                    qs[0] = q.copy();
                }
                """);
        assertTrue(r.success(), r.describe());
        IrModule m = r.module();
        assertTrue(hasType(m, Types.KIND_STRUCT));
        assertTrue(count(m, Op.FIELD_SET) >= 4, "запись в поля - цепочки field_set");
        assertTrue(count(m, Op.FIELD_GET) >= 5);
        assertEquals(1, count(m, Op.STRUCT_BUILD));
        assertEquals(0, count(m, Op.FIELD_PTR), "field_ptr - веха M9, во фронтенде не используется");
        assertEquals(0, count(m, Op.LOCAL_ADDR));
    }

    @Test
    void structsAndVectorsPassThroughDeviceFunctionsAndParameters() {
        FrontendHarness.Outcome r = compile("""
                @DeviceFunction static P bump(P p, Float4 d) { return new P(p.x + d.x, p.v.add(d), p.id); }
                @Kernel public static void k(@Global P[] ps, P init, Float4 delta) {
                    ps[0] = bump(init, delta);
                    P local = bump(ps[1], delta);
                    ps[2] = local.copy();
                }
                """);
        assertTrue(r.success(), r.describe());
        IrModule m = r.module();
        assertEquals(2, m.functionCount());
        assertEquals(2, count(m, Op.CALL), "copy() - не вызов, а пересборка значения");
    }

    @Test
    void releaseKeepsStructFieldNamesOutOfTheHash() {
        String a = """
                @Kernel public static void k(@Global P[] ps) { ps[0].x = 1f; }
                """;
        FrontendHarness.Outcome r = compile(a);
        assertTrue(r.success(), r.describe());
        assertEquals("P", r.module().debug().structName(structType(r.module())));
        assertEquals("x", r.module().debug().fieldName(structType(r.module()), 0));
    }

    private static int structType(IrModule m) {
        for (int t = 0; t < m.typeCount(); t++) {
            if (m.typeKind(t) == Types.KIND_STRUCT) {
                return t;
            }
        }
        throw new AssertionError("нет struct");
    }

    // ---- value-семантика: что отвергается ---------------------------------------------------------

    private static void rejects(String code, String members) {
        FrontendHarness.Outcome r = compile(members);
        assertFalse(r.success(), "ожидалась ошибка " + code + "\n" + r.describe());
        assertTrue(r.errorCodes().contains(code), code + " не выдан; выданы: " + r.errorCodes() + "\n" + r.describe());
    }

    @Test
    void structCannotBeAliasedByAssignment() {
        rejects("GPU0193", "@Kernel public static void k(@Global P[] ps) { P a = new P(1, new Float4(1, 2, 3, 4), 1); P b = a; }");
        rejects("GPU0193", "@Kernel public static void k(@Global P[] ps) { P a = ps[0]; }");
        rejects("GPU0193", "@Kernel public static void k(@Global P[] ps) { ps[1] = ps[0]; }");
        rejects("GPU0193", "@DeviceFunction static P f(P p) { return p; }");
        rejects("GPU0193", "@Kernel public static void k(@Global Q[] qs, P p) { qs[0] = new Q(p, 1.0); }");
        rejects("GPU0193", "@Kernel public static void k(@Global P[] ps, boolean unused) { }".replace(", boolean unused", ", int c")
                .replace("{ }", "{ P a = c > 0 ? ps[0] : new P(1, new Float4(1, 2, 3, 4), 1); }"));
    }

    @Test
    void freshValuesAreAccepted() {
        FrontendHarness.Outcome r = compile("""
                @DeviceFunction static P make(float x) { return new P(x, Float4.splat(x), 1); }
                @Kernel public static void k(@Global P[] ps, int c) {
                    P a = c > 0 ? ps[0].copy() : make(2f);
                    ps[1] = make(1f);
                    ps[2] = a;
                }
                """);
        assertFalse(r.success(), "ps[2] = a - общая ссылка");
        FrontendHarness.Outcome ok = compile("""
                @DeviceFunction static P make(float x) { return new P(x, Float4.splat(x), 1); }
                @Kernel public static void k(@Global P[] ps, int c) {
                    P a = c > 0 ? ps[0].copy() : make(2f);
                    ps[1] = make(1f);
                    ps[2] = a.copy();
                }
                """);
        assertTrue(ok.success(), ok.describe());
    }

    @Test
    void structParametersAreReadOnly() {
        rejects("GPU0194", "@DeviceFunction static void f(P p) { p.x = 1f; }");
        rejects("GPU0194", "@DeviceFunction static void f(Q q) { q.p.id = 1; }");
        rejects("GPU0194", "@DeviceFunction static void f(P p) { p.id++; }");
        FrontendHarness.Outcome ok = compile("@DeviceFunction static float f(P p) { P q = p.copy(); q.x = 1f; return q.x + p.x; }");
        assertTrue(ok.success(), ok.describe());
    }

    @Test
    void newWithoutArgumentsAndLocalObjectArraysAreRejected() {
        rejects("GPU0192", "@Kernel public static void k(@Global float[] a) { P p = new P(); }");
        rejects("GPU0117", "@Kernel public static void k(@Global float[] a) { Float4[] t = new Float4[4]; }");
        rejects("GPU0117", "@Kernel public static void k(@Global float[] a) { P[] t = new P[2]; }");
    }

    @Test
    void referenceComparisonOfObjectsIsRejected() {
        rejects("GPU0196", "@Kernel public static void k(@Global P[] ps) { boolean b = ps[0] == ps[1]; }");
        rejects("GPU0196", "@Kernel public static void k(@Global Float4[] a) { boolean b = a[0] != a[1]; }");
    }

    @Test
    void shuffleIndicesMustBeConstantsInRange() {
        rejects("GPU0195", "@Kernel public static void k(@Global Float4[] a, int i) { Float2 w = a[0].shuffle(i, 1); }");
        rejects("GPU0195", "@Kernel public static void k(@Global Float4[] a) { Float2 w = a[0].shuffle(1, 4); }");
        FrontendHarness.Outcome ok = compile("""
                static final int LAST = 3;
                @Kernel public static void k(@Global Float4[] a) { Float2 w = a[0].shuffle(LAST, 0); }
                """);
        assertTrue(ok.success(), ok.describe());
    }

    @Test
    void vectorMethodsOutsideTheApiAreRejected() {
        rejects("GPU0112", "@Kernel public static void k(@Global Float4[] a) { int h = a[0].hashCode(); }");
        rejects("GPU0112", "@Kernel public static void k(@Global Float4[] a) { boolean e = a[0].equals(a[1]); }");
    }

    @Test
    void structDeclarationProblems() {
        String badFinal = "@Struct public static final class B { public final int x = 1; public B() { } }\n";
        rejects("GPU0191", badFinal + "@Kernel public static void k(@Global B[] a) { }");
        String noDefault = "@Struct public static final class B { public int x; public B(int x) { this.x = x; } }\n";
        rejects("GPU0191", noDefault + "@Kernel public static void k(@Global B[] a) { }");
        String badField = "@Struct public static final class B { public int x; public String s; public B() { } }\n";
        rejects("GPU0191", badField + "@Kernel public static void k(@Global B[] a) { }");
        String shallowCopy = "@Struct public static final class B { public P p; public B() { p = new P(); } public B(P p) { this.p = p; } "
                + "public B copy() { return new B(p); } }\n";
        rejects("GPU0191", shallowCopy + "@Kernel public static void k(@Global B[] a) { }");
        String sneakyConstructor = "@Struct public static final class B { public int x; public B() { } public B(int x) { this.x = x + 1; } }\n";
        rejects("GPU0191", sneakyConstructor + "@Kernel public static void k(@Global B[] a) { }");
        String empty = "@Struct public static final class B { public B() { } }\n";
        rejects("GPU0191", empty + "@Kernel public static void k(@Global B[] a) { }");
    }

    @Test
    void pointerTypesStayForMilestoneM9() {
        rejects("GPU0190", "@Kernel public static void k(FloatPtr p) { }");
    }

    @Test
    void kernelsAreIndexedWithObjectDescriptors() {
        FrontendHarness.Outcome r = compile("@Kernel public static void k(@Global P[] ps, Float4 d, int n) { }");
        assertTrue(r.success(), r.describe());
        String idx = new String(r.resources().get("META-INF/gpu/kernels.idx"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(idx.contains("demo.T\tk\t([Ldemo/T$P;Lcom/sixik/gpu/api/types/Float4;I)V\t"), idx);
    }
}
