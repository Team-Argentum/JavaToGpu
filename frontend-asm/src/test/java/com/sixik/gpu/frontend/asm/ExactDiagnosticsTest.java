package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.ir.Features;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Validator;
import com.sixik.gpu.tools.IrDump;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Шаг B5 (FRONTEND_ASM_RUNTIME.md, 18.2-S6): свойство {@code exact} и JDK-интринсики - оба фронтенда выдают одни и
 * те же коды на одних и тех же исходниках, а IR интринсиков совпадает.
 */
class ExactDiagnosticsTest {

    private static final String HEAD = """
            package x;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.*;

            public final class X {
            """;

    private static TreeSet<String> javaCodes(String src) {
        return new TreeSet<>(JavacHarness.compile(true, null, src).gpuErrors());
    }

    static List<AsmDiagnostic> asm(String src) {
        JavacHarness.Outcome j = JavacHarness.compile(false, null, src);
        assertTrue(j.success(), j.log());
        return AsmFrontend.compile(ClassSource.of(j.classes()), List.of("x.X"), AsmFrontend.Options.DEFAULT)
                .diagnostics();
    }

    private static TreeSet<String> asmCodes(String src) {
        TreeSet<String> codes = new TreeSet<>();
        asm(src).stream().filter(AsmDiagnostic::error).forEach(d -> codes.add(d.code()));
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

    /** Точные операции в ядре {@code exact} допустимы, в том числе {@code float}-деление и {@code sqrt}. */
    @Test
    void exactOperationsAreAccepted() {
        same(null,
                "@Kernel(compilationProperties = \"exact=true\") public static void k(@Global double[] a) {"
                        + " a[0] = Math.floor(a[1]) / a[2] + Math.sqrt(a[3]) + Math.fma(a[4], a[5], a[6]); }",
                "@Kernel(compilationProperties = \"exact=true\") public static void k(@Global float[] a, @Global int[] b) {"
                        + " a[0] = a[1] / a[2] + Gpu.sqrt(a[3]); b[0] = Integer.bitCount(b[1]) + Math.floorMod(b[2], 3); }",
                "@Kernel(compilationProperties = \"exact=false; fp=contract\") public static void k(@Global float[] a) {"
                        + " a[0] = Gpu.sin(a[1]); }");
    }

    /** {@code GPU0310}: приближённый builtin в ядре или в достижимой функции, флаг {@code fp} рядом с {@code exact}. */
    @Test
    void inexactOperationsAreRejected() {
        same("GPU0310",
                "@Kernel(compilationProperties = \"exact=true\") public static void k(@Global float[] a) {"
                        + " a[0] = Gpu.sin(a[1]); }",
                "@DeviceFunction static double wave(double x) { return Gpu.cos(x) * 2; }\n"
                        + "@Kernel(compilationProperties = \"exact=true\") public static void k(@Global double[] a) {"
                        + " a[0] = wave(a[1]); }",
                "@Kernel(compilationProperties = \"exact=true; fp=contract,native\") public static void k(@Global float[] a) {"
                        + " a[0] = a[1] * a[2] + a[3]; }");
    }

    /**
     * Функция с {@code sin}, общая для строгого ядра и ядра {@code exact}: строгое собирается, {@code exact} - нет
     * (проверка идёт по каждому ядру, а не по функции).
     */
    @Test
    void sharedFunctionIsCheckedPerKernel() {
        String src = HEAD + """
                    @DeviceFunction static float wave(float x) { return Gpu.sin(x); }
                    @Kernel public static void strict(@Global float[] a) { a[0] = wave(a[1]); }
                    @Kernel(compilationProperties = "exact=true") public static void exact(@Global float[] a) { a[0] = wave(a[1]); }
                }
                """;
        List<AsmDiagnostic> d = asm(src);
        assertEquals(List.of("GPU0310"), d.stream().map(AsmDiagnostic::code).toList(), d.toString());
        assertEquals("exact", d.get(0).method());
        assertTrue(d.get(0).message().contains("uses sin (tolerance 4 ULP)"), d.get(0).message());
        assertEquals(new TreeSet<>(List.of("GPU0310")), javaCodes(src));
    }

    /** Методы JDK вне таблицы ({@code Math.sin}, {@code Math.round}) - по-прежнему {@code GPU0112}; значение {@code exact} проверяется. */
    @Test
    void methodsOutsideTheTableStayUnsupported() {
        same("GPU0112",
                "@Kernel public static void k(@Global double[] a) { a[0] = Math.sin(a[1]); }",
                "@Kernel(compilationProperties = \"exact=true\") public static void k(@Global double[] a) { a[0] = Math.round(a[1]); }",
                "@Kernel public static void k(@Global long[] a) { a[0] = Double.doubleToLongBits(a[1]); }");
        same("GPU0021", "@Kernel(compilationProperties = \"exact=yes\") public static void k(@Global float[] a) { a[0] = 1; }");
    }

    /**
     * Ядро {@code exact} несёт {@code FP_EXACT}; с {@code float}-делением валидатор требует от устройства
     * {@code EXACT_DIVIDE_SQRT}, без него - нет. IR обоих фронтендов совпадает.
     */
    @Test
    void exactKernelsRequireCorrectlyRoundedDivisionOnlyWhenTheyDivideFloats() {
        String src = HEAD + """
                    @Kernel(compilationProperties = "exact=true") public static void div(@Global float[] a) { a[0] = a[1] / a[2]; }
                    @Kernel(compilationProperties = "exact=true") public static void mul(@Global float[] a) { a[0] = a[1] * a[2]; }
                    @Kernel public static void strict(@Global float[] a) { a[0] = a[1] / a[2]; }
                }
                """;
        JavacHarness.Outcome jv = JavacHarness.compile(true, null, src);
        assertTrue(jv.success(), jv.log());
        AsmFrontend.Result r = AsmFrontend.compile(ClassSource.of(jv.classes()), List.of("x.X"),
                AsmFrontend.Options.DEFAULT);
        assertTrue(r.diagnostics().isEmpty(), r.diagnostics().toString());
        for (AsmFrontend.Kernel k : r.kernels()) {
            String name = k.entry().method();
            IrModule m = k.module();
            int f = m.functionCount() - 1;
            assertEquals(IrDump.dump(jv.module("x.X", name), false), IrDump.dump(m, false), name);
            boolean exact = !name.equals("strict");
            assertEquals(exact ? IrModule.FP_EXACT : 0, m.fFp(f), name);
            int need = Validator.validate(m).functions()[f].requiredFeatures() & Features.EXACT_DIVIDE_SQRT;
            assertEquals(name.equals("div") ? Features.EXACT_DIVIDE_SQRT : 0, need, name);
        }
    }
}
