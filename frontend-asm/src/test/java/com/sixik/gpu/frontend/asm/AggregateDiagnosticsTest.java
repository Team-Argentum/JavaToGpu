package com.sixik.gpu.frontend.asm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Шаг A5 (17.15): диагностики векторов, {@code @Struct} и указателей на байткоде - те же коды, что у
 * {@code frontend-java} на тех же исходниках (случаи - из его тестов агрегатов и указателей).
 */
class AggregateDiagnosticsTest {

    private static final String HEAD = """
            package d;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.*;
            import com.sixik.gpu.api.types.*;

            public final class D {
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

    private static TreeSet<String> javaCodes(String src) {
        return new TreeSet<>(JavacHarness.compile(true, null, src).gpuErrors());
    }

    private static TreeSet<String> asmCodes(String src) {
        JavacHarness.Outcome j = JavacHarness.compile(false, null, src);
        assertTrue(j.success(), j.log());
        AsmFrontend.Result r = AsmFrontend.compile(ClassSource.of(j.classes()), List.of("d.D"), AsmFrontend.Options.DEFAULT);
        TreeSet<String> codes = new TreeSet<>();
        r.diagnostics().stream().filter(AsmDiagnostic::error).forEach(d -> codes.add(d.code()));
        return codes;
    }

    /** Каждый случай: коды обоих фронтендов совпадают и содержат ожидаемый (у корректного - пусто). */
    private static void same(String expected, String... members) {
        List<String> failures = new ArrayList<>();
        for (String m : members) {
            String src = HEAD + m + "\n}\n";
            TreeSet<String> java = javaCodes(src);
            TreeSet<String> asm = asmCodes(src);
            boolean ok = expected == null ? java.isEmpty() : java.contains(expected);
            if (!ok || !java.equals(asm)) {
                failures.add(m + "\n   frontend-java " + java + ", frontend-asm " + asm);
            }
        }
        assertEquals(List.of(), failures);
    }

    @Test
    void validAggregateKernelsHaveNoErrors() {
        same(null,
                "@Kernel public static void k(@Global P[] ps, @Global Q[] qs) { P p = ps[0].copy(); p.x += 1; ps[1] = p.copy();"
                        + " Q q = new Q(p.copy(), 2.0); q.p.id = 3; qs[0] = q.copy(); }",
                "@DeviceFunction static P make(float x) { return new P(x, Float4.splat(x), 1); }\n"
                        + "@Kernel public static void k(@Global P[] ps, int c) { P a = c > 0 ? ps[0].copy() : make(2f);"
                        + " ps[1] = make(1f); ps[2] = a.copy(); }",
                "@DeviceFunction static float f(P p) { P q = p.copy(); q.x = 1f; return q.x + p.x; }\n"
                        + "@Kernel public static void k(@Global P[] ps, @Global float[] o) { o[0] = f(ps[0]); }",
                "static final int LAST = 3;\n"
                        + "@Kernel public static void k(@Global Float4[] a) { Float2 w = a[0].shuffle(LAST, 0); a[1] = new Float4(w.x, w.y, 0, 0); }");
    }

    @Test
    void structValuesMustBeFresh() {
        same("GPU0193",
                "@Kernel public static void k(@Global P[] ps) { P a = new P(1, new Float4(1, 2, 3, 4), 1); P b = a; }",
                "@Kernel public static void k(@Global P[] ps) { P a = ps[0]; }",
                "@Kernel public static void k(@Global P[] ps) { ps[1] = ps[0]; }",
                "@DeviceFunction static P f(P p) { return p; }\n@Kernel public static void k(@Global P[] ps) { ps[0] = f(ps[1].copy()); }",
                "@Kernel public static void k(@Global Q[] qs, P p) { qs[0] = new Q(p, 1.0); }",
                "@Kernel public static void k(@Global P[] ps, int c) { P a = c > 0 ? ps[0] : new P(1, new Float4(1, 2, 3, 4), 1); }",
                "@DeviceFunction static P make(float x) { return new P(x, Float4.splat(x), 1); }\n"
                        + "@Kernel public static void k(@Global P[] ps, int c) { P a = c > 0 ? ps[0].copy() : make(2f); ps[2] = a; }");
    }

    @Test
    void structParametersAreReadOnly() {
        same("GPU0194",
                "@DeviceFunction static void f(P p) { p.x = 1f; }\n@Kernel public static void k(@Global P[] ps) { f(ps[0]); }",
                "@DeviceFunction static void f(Q q) { q.p.id = 1; }\n@Kernel public static void k(@Global Q[] qs) { f(qs[0]); }",
                "@DeviceFunction static void f(P p) { p.id++; }\n@Kernel public static void k(@Global P[] ps) { f(ps[0]); }");
    }

    @Test
    void objectCreationAndArrays() {
        same("GPU0192", "@Kernel public static void k(@Global float[] a) { P p = new P(); }");
        same("GPU0117", "@Kernel public static void k(@Global float[] a) { Float4[] t = new Float4[4]; }",
                "@Kernel public static void k(@Global float[] a) { P[] t = new P[2]; }");
        same("GPU0196", "@Kernel public static void k(@Global P[] ps) { boolean b = ps[0] == ps[1]; }",
                "@Kernel public static void k(@Global Float4[] a) { boolean b = a[0] != a[1]; }");
        same("GPU0195", "@Kernel public static void k(@Global Float4[] a, int i) { Float2 w = a[0].shuffle(i, 1); }",
                "@Kernel public static void k(@Global Float4[] a) { Float2 w = a[0].shuffle(1, 4); }");
        same("GPU0112", "@Kernel public static void k(@Global Float4[] a) { int h = a[0].hashCode(); }",
                "@Kernel public static void k(@Global Float4[] a) { boolean e = a[0].equals(a[1]); }");
    }

    @Test
    void structDeclarationProblems() {
        String use = "@Kernel public static void k(@Global B[] a) { }";
        same("GPU0191",
                "@Struct public static final class B { public final int x = 1; public B() { } }\n" + use,
                "@Struct public static final class B { public int x; public B(int x) { this.x = x; } }\n" + use,
                "@Struct public static final class B { public int x; public String s; public B() { } }\n" + use,
                "@Struct public static final class B { public P p; public B() { p = new P(); } public B(P p) { this.p = p; } "
                        + "public B copy() { return new B(p); } }\n" + use,
                "@Struct public static final class B { public int x; public B() { } public B(int x) { this.x = x + 1; } }\n" + use,
                "@Struct public static final class B { public B() { } }\n" + use);
    }

    @Test
    void signatures() {
        same("GPU0197", "@Kernel public static void k(FloatPtr p) { }", "@Kernel public static void k(GlobalFloatPtr p) { }");
        same("GPU0006", "@DeviceFunction static FloatPtr f() { return new FloatPtr(); }\n"
                + "@Kernel public static void k(@Global float[] a) { f(); }");
        same("GPU0003", "@Kernel public static void k(FloatPtr[] a) { }");
        same("GPU0004", "@Kernel public static void k(@Global float[] a, boolean b) { }");
        same("GPU0005", "@Kernel public static void k(int[][] a) { }");
        same("GPU0119", "@Kernel public static void k(@Global float[] a) { GlobalFloatPtr[] ps = new GlobalFloatPtr[2]; }");
    }

    @Test
    void pointerMisuse() {
        same("GPU0198",
                "@DeviceFunction static void f(FloatPtr p) { FloatPtr q = p; }\n@Kernel public static void k(@Global float[] a) { f(new FloatPtr()); }",
                "@DeviceFunction static void g(FloatPtr p) { }\n@Kernel public static void k(@Global float[] a) { g(new FloatPtr()); }",
                "@Kernel public static void k(@Global float[] a) { FloatPtr q = new FloatPtr(); q = new FloatPtr(); }",
                "@DeviceFunction static void f(FloatPtr p) { p = new FloatPtr(); }\n@Kernel public static void k(@Global float[] a) { FloatPtr q = new FloatPtr(); f(q); }",
                "@DeviceFunction static boolean g(FloatPtr p, FloatPtr q) { return p == q; }\n"
                        + "@Kernel public static void k(@Global float[] a) { FloatPtr q = new FloatPtr(); boolean b = g(q, q); }",
                "@DeviceFunction static float f(int c, FloatPtr a, FloatPtr b) { return (c > 0 ? a : b).value; }\n"
                        + "@Kernel public static void k(@Global float[] a) { FloatPtr q = new FloatPtr(); a[0] = f(1, q, q); }",
                "@Kernel public static void k(@Global int[] a) { GlobalIntPtr p = GlobalIntPtr.of(a, 0); boolean same = p == p.plus(1); }",
                "@Kernel public static void k(@Global int[] a) { GlobalIntPtr p = GlobalIntPtr.of(a, 0);"
                        + " GlobalIntPtr q = GlobalIntPtr.of(a, 1); q = (p = q); }");
        same("GPU0199",
                "@Kernel public static void k(@Global int[] a) { ConstantIntPtr p = ConstantIntPtr.of(a, 0); }",
                "@Kernel public static void k(@Global(constant = true) int[] a) { GlobalIntPtr p = GlobalIntPtr.of(a, 0); }",
                "@Kernel public static void k(@Global int[] a) { int[] t = new int[4]; GlobalIntPtr p = GlobalIntPtr.of(t, 0); }");
        same("GPU0112", "@Kernel public static void k(@Global int[] a) { GlobalIntPtr p = GlobalIntPtr.of(a, 0); String s = p.toString(); }");
    }
}
