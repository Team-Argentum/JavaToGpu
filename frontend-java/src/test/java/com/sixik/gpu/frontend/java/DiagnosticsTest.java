package com.sixik.gpu.frontend.java;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Негативные тесты: для каждого кода {@code GPU####} из таблицы диагностик - образец, который его вызывает; проверяется
 * код и строка (она помечена комментарием {@code // <-}). Отдельно проверяется, что таблица и код согласованы.
 */
class DiagnosticsTest {

    private record Case(String code, String members) {
    }

    private static String source(String members) {
        return """
                package demo;

                import com.sixik.gpu.api.Gpu;
                import com.sixik.gpu.api.annotations.*;
                import com.sixik.gpu.api.types.*;

                public final class T {
                """ + members + "\n}\n";
    }

    /** Образцы: поле {@code members} вставляется в тело класса {@code T}. */
    private static final List<Case> CASES = List.of(
            new Case("GPU0001", """
                    @DeviceFunction int f(int x) { return x; } // <-
                    """),
            new Case("GPU0002", """
                    @Kernel public static int k(@Global int[] a) { return 1; } // <-
                    """),
            new Case("GPU0003", """
                    @Kernel public static void k(String s) { } // <-
                    """),
            new Case("GPU0004", """
                    @Kernel public static void k(boolean flag) { } // <-
                    """),
            new Case("GPU0005", """
                    @Kernel public static void k(int[][] m) { } // <-
                    """),
            new Case("GPU0006", """
                    @DeviceFunction static String f() { return "x"; } // <-
                    """),
            new Case("GPU0007", """
                    @DeviceFunction static <X> int f(int x) { return x; } // <-
                    """),
            new Case("GPU0008", """
                    @Kernel(workgroup = 4) @WorkGroup(x = 8) public static void k(@Global int[] a) { } // <-
                    """),
            new Case("GPU0009", """
                    @Kernel(required = {"SIMD"}) public static void k(@Global int[] a) { } // <-
                    """),
            new Case("GPU0010", """
                    @Kernel(fallback = "nope") public static void k(@Global int[] a) { } // <-
                    """),
            new Case("GPU0011", """
                    @Kernel(fallback = "other") public static void k(@Global int[] a) { } // <-
                    @Kernel public static void other(@Global float[] a) { }
                    """),
            new Case("GPU0012", """
                    @Kernel(optLevel = 9) public static void k(@Global int[] a) { } // <-
                    """),
            new Case("GPU0013", """
                    @Kernel @WorkGroup(x = 0) public static void k(@Global int[] a) { } // <-
                    """),
            new Case("GPU0014", """
                    @Kernel(fallback = "b") public static void a(@Global int[] x) { } // <-
                    @Kernel(fallback = "a") public static void b(@Global int[] x) { }
                    """),
            new Case("GPU0015", """
                    @Constant static int notFinal = 3; // <-
                    """),
            new Case("GPU0016", """
                    @Reduction public static void r(int[] a) { } // <-
                    """),
            new Case("GPU0017", """
                    @Kernel @Reduction public static int r(int[] a) { return 0; } // <-
                    """),
            new Case("GPU0018", """
                    @Reduction public static int r(int[] a) { int s = 0; for (int i = 0; i < a.length; i++) { if (a[i] < 0) break; s += a[i]; } return s; } // <-
                    """),
            new Case("GPU0101", """
                    @Kernel public static void k(@Global int[] a) {
                        try { a[0] = 1; } catch (RuntimeException e) { a[0] = 2; } // <-
                    }
                    """),
            new Case("GPU0102", """
                    @Kernel public static void k(@Global int[] a) {
                        synchronized (a) { a[0] = 1; } // <-
                    }
                    """),
            new Case("GPU0103", """
                    @Kernel public static void k(@Global int[] a) {
                        assert a[0] > 0; // <-
                    }
                    """),
            new Case("GPU0104", """
                    @Kernel public static void k(@Global int[] a) {
                        outer: for (int i = 0; i < 3; i++) { // <-
                            for (int j = 0; j < 3; j++) { break outer; }
                        }
                    }
                    """),
            new Case("GPU0105", """
                    @DeviceFunction static int f(int n) { return f(n - 1); } // <-
                    @Kernel public static void k(@Global int[] a) { a[0] = f(3); }
                    """),
            new Case("GPU0106", """
                    @Kernel public static void k(@Global int[] a) {
                        a[0] = "text".length(); // <-
                    }
                    """),
            new Case("GPU0107", """
                    @Kernel public static void k(@Global int[] a) {
                        a[0] = new StringBuilder().length(); // <-
                    }
                    """),
            new Case("GPU0108", """
                    @Kernel public static void k(@Global int[] a) {
                        Runnable r = () -> { }; // <-
                    }
                    """),
            new Case("GPU0109", """
                    @Kernel public static void k(@Global int[] a) {
                        Integer boxed = (Integer) a[0]; // <-
                    }
                    """),
            new Case("GPU0110", """
                    @Kernel public static void k(@Global int[] a) {
                        a[0] = switch (a[1]) { case 1 -> 5; default -> 6; }; // <-
                    }
                    """),
            new Case("GPU0111", """
                    @Kernel public static void k(@Global int[] a) {
                        for (int v : a) { a[0] = v; } // <-
                    }
                    """),
            new Case("GPU0112", """
                    @Kernel public static void k(@Global float[] a) {
                        a[0] = (float) Math.sqrt(a[1]); // <-
                    }
                    """),
            new Case("GPU0113", """
                    static int helper(int x) { return x; }
                    @Kernel public static void k(@Global int[] a) {
                        a[0] = helper(2); // <-
                    }
                    """),
            new Case("GPU0114", """
                    @Kernel public static void k(@Global int[] a) {
                        a[0] = com.sixik.gpu.frontend.java.fixture.LibDevice.libAdd(1, 2); // <-
                    }
                    """),
            new Case("GPU0115", """
                    static int counter;
                    @Kernel public static void k(@Global int[] a) {
                        counter = a[0]; // <-
                    }
                    """),
            new Case("GPU0116", """
                    @Kernel public static void k(@Global int[] a) {
                        int[] alias = a; // <-
                    }
                    """),
            new Case("GPU0117", """
                    @Kernel public static void k(@Global int[] a) {
                        int[] t = new int[a[0]]; // <-
                    }
                    """),
            new Case("GPU0118", """
                    @Kernel public static void k(@Global int[] a, @Global(constant = true) int c) {
                        c = 5; // <-
                    }
                    """),
            new Case("GPU0119", """
                    @Kernel public static void k(@Global int[] a) {
                        Integer boxed = 5; // <-
                    }
                    """),
            new Case("GPU0120", """
                    @DeviceFunction static void f() {
                        @Local float[] t = new float[8]; // <-
                    }
                    """),
            new Case("GPU0121", """
                    @DeviceFunction static void f(@Global float[] x) { x[0] = 1f; }
                    @Kernel public static void k(@Global float[] a) {
                        @Local float[] t = new float[8];
                        f(t); // <-
                    }
                    """),
            new Case("GPU0122", """
                    @Kernel public static void k(@Global(constant = true) float[] c) {
                        c[0] = 1f; // <-
                    }
                    """),
            new Case("GPU0191", """
                    @Struct public static final class Bad { public final int x = 1; public Bad() { } }
                    @Kernel public static void k(@Global Bad[] a) { } // <-
                    """),
            new Case("GPU0192", """
                    @Struct public static final class P { public int x; public P() { } public P(int x) { this.x = x; } }
                    @Kernel public static void k(@Global int[] a) {
                        P p = new P(); // <-
                    }
                    """),
            new Case("GPU0193", """
                    @Struct public static final class P { public int x; public P() { } public P(int x) { this.x = x; } }
                    @Kernel public static void k(@Global int[] a) {
                        P p = new P(1);
                        P q = p; // <-
                    }
                    """),
            new Case("GPU0194", """
                    @Struct public static final class P { public int x; public P() { } public P(int x) { this.x = x; } }
                    @DeviceFunction static void f(P p) {
                        p.x = 2; // <-
                    }
                    """),
            new Case("GPU0195", """
                    @Kernel public static void k(@Global float[] a, int i) {
                        Float4 v = new Float4(1, 2, 3, 4);
                        Float2 w = v.shuffle(i, 0); // <-
                    }
                    """),
            new Case("GPU0196", """
                    @Kernel public static void k(@Global int[] a) {
                        Float4 v = new Float4(1, 2, 3, 4);
                        boolean same = v == v; // <-
                    }
                    """),
            new Case("GPU0197", """
                    @Kernel public static void k(FloatPtr p) { } // <-
                    """),
            new Case("GPU0198", """
                    @DeviceFunction static void f(FloatPtr p) {
                        p = new FloatPtr(); // <-
                    }
                    """),
            new Case("GPU0199", """
                    @Kernel public static void k(@Global int[] a) {
                        ConstantIntPtr p = ConstantIntPtr.of(a, 0); // <-
                    }
                    """)
    );

    /** Коды, проверяемые отдельными тестами окружения и внутренних ошибок. */
    private static final Set<String> ENVIRONMENT_CODES = Set.of("GPU0130", "GPU0131", "GPU0201", "GPU0901", "GPU0902", "GPU0903");

    @Test
    void everyCaseReportsItsCodeOnTheMarkedLine() {
        for (Case c : CASES) {
            String src = source(c.members());
            FrontendHarness.Outcome r = FrontendHarness.compile(src);
            assertFalse(r.success(), c.code() + ": compilation must fail with an error\n" + r.describe());
            FrontendHarness.Diag d = r.gpu().stream().filter(x -> x.code().equals(c.code())).findFirst().orElse(null);
            assertTrue(d != null, c.code() + " was not reported; reported: " + r.errorCodes() + "\n" + r.describe());
            long expectedLine = markerLine(src);
            assertEquals(expectedLine, d.line(), c.code() + ": diagnostic line\n" + r.describe());
            assertTrue(d.message().startsWith(c.code() + ":"), d.message());
            assertTrue(d.message().contains("Hint:"), c.code() + ": the error has a hint: " + d.message());
        }
    }

    @Test
    void failingKernelProducesNoIr() {
        for (Case c : CASES) {
            if (c.code().equals("GPU0105") || c.code().equals("GPU0011") || c.code().equals("GPU0014")
                    || c.code().equals("GPU0015") || c.code().equals("GPU0001") || c.code().equals("GPU0006")
                    || c.code().equals("GPU0007") || c.code().equals("GPU0120") || c.code().equals("GPU0121")) {
                continue;
            }
            FrontendHarness.Outcome r = FrontendHarness.compile(source(c.members()));
            assertTrue(r.resources().isEmpty(), c.code() + ": on errors neither IR nor index is written: " + r.resources().keySet());
        }
    }

    @Test
    void allErrorsAreReportedNotJustTheFirst() {
        FrontendHarness.Outcome r = FrontendHarness.compile(source("""
                @Kernel public static void k(@Global int[] a) {
                    try { a[0] = 1; } catch (RuntimeException e) { }
                    a[1] = "x".length();
                    int[] alias = a;
                    for (int v : a) { }
                }
                """));
        assertTrue(r.errorCodes().containsAll(List.of("GPU0101", "GPU0106", "GPU0116", "GPU0111")), r.describe());
    }

    @Test
    void tableCoversEveryCaseAndNothingElse() {
        Set<String> covered = new TreeSet<>();
        CASES.forEach(c -> covered.add(c.code()));
        covered.addAll(ENVIRONMENT_CODES);
        assertEquals(new TreeSet<>(Diag.codes()), covered,
                "every code in the diagnostics.def table must have a negative test, and vice versa");
    }

    @Test
    void everyCodeUsedInSourcesIsInTheTable() throws IOException {
        Pattern p = Pattern.compile("\"(GPU\\d{4})\"");
        Set<String> used = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of(System.getProperty("frontend.src")))) {
            for (Path f : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                Matcher m = p.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find()) {
                    used.add(m.group(1));
                }
            }
        }
        assertFalse(used.isEmpty());
        List<String> unknown = new ArrayList<>(used);
        unknown.removeAll(Diag.codes());
        assertTrue(unknown.isEmpty(), "codes in sources without an entry in diagnostics.def: " + unknown);
        List<String> unused = new ArrayList<>(Diag.codes());
        unused.removeAll(used);
        assertTrue(unused.isEmpty(), "table codes that the code never reports: " + unused);
    }

    @Test
    void tableFormatting() {
        assertEquals("GPU0106: Strings are not supported in GPU code. Hint: Use numeric codes",
                Diag.format("GPU0106"));
        assertEquals("GPU0903: IR of kernel k: msg", Diag.format("GPU0903", "k", "msg"), "empty hint is not appended");
        assertTrue(Diag.entry("GPU0901").error());
        assertFalse(Diag.entry("GPU0903").error());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> Diag.entry("GPU9999"));
    }

    private static long markerLine(String src) {
        String[] lines = src.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("// <-")) {
                return i + 1;
            }
        }
        throw new AssertionError("no // <- marker in the sample");
    }
}
