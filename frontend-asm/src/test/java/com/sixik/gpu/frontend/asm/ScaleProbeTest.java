package com.sixik.gpu.frontend.asm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Замер масштаба фронтенда (FRONTEND_ASM_RUNTIME.md, шаг B0 и S9): время {@link AsmFrontend#compile} и глубина, на
 * которой рекурсивные проходы ({@code Cfg}, {@code Structurer}, {@code Scopes}, понижение выражений) исчерпывают стек.
 *
 * <p>Ядра генерируются исходником и компилируются javac с {@code -g} (LVT и формы javac, как у библиотеки GA2):
 * вложенные {@code if}, вложенные {@code ?:}, вложенные циклы, длинная цепочка {@code &&} и длинный прямой код. Обычный
 * прогон проверяет размеры, которые обязаны проходить; {@code -Dprobe.scale=true} прогоняет всю шкалу и печатает таблицу
 * (на переполнении стека шкала формы останавливается).</p>
 */
class ScaleProbeTest {

    /** Размеры, которые фронтенд обязан проходить на стеке потока по умолчанию. */
    private static final int[] REQUIRED = {64};
    /** Полная шкала замера. */
    private static final int[] SCALE = {64, 128, 256, 512, 1024, 2048, 4096, 8192};

    private record Shape(String name, IntFunction<String> body) {
    }

    private static final List<Shape> SHAPES = List.of(
            new Shape("nested if", ScaleProbeTest::nestedIf),
            new Shape("nested ?:", ScaleProbeTest::nestedTernary),
            new Shape("nested for", ScaleProbeTest::nestedLoops),
            new Shape("&& chain", ScaleProbeTest::andChain),
            new Shape("straight line", n -> straight(n * 8)));

    @Test
    void frontendHandlesDeepAndLongMethods() {
        boolean full = Boolean.getBoolean("probe.scale");
        int[] sizes = full ? SCALE : REQUIRED;
        String custom = System.getProperty("probe.sizes");
        if (custom != null) {
            full = true;
            sizes = java.util.Arrays.stream(custom.split(",")).mapToInt(v -> Integer.parseInt(v.trim())).toArray();
        }
        List<String> failures = new ArrayList<>();
        StringBuilder table = new StringBuilder(String.format(Locale.ROOT, "%-14s %6s %10s %10s  %s%n",
                "shape", "size", "javac ms", "asm ms", "result"));
        for (Shape s : SHAPES) {
            for (int n : sizes) {
                String verdict = probe(s, n, table);
                if (!verdict.equals("ok")) {
                    if (!full) {
                        failures.add(s.name() + " " + n + ": " + verdict);
                    }
                    break;
                }
            }
        }
        System.out.print(table);
        assertTrue(failures.isEmpty(), failures.toString());
    }

    /** Один замер: строка таблицы и итог ({@code ok}, {@code javac}, {@code stack overflow} или диагностики). */
    private static String probe(Shape s, int n, StringBuilder table) {
        String src = "package probe;\n"
                + "import com.sixik.gpu.api.Gpu;\n"
                + "import com.sixik.gpu.api.annotations.Global;\n"
                + "import com.sixik.gpu.api.annotations.Kernel;\n"
                + "public class K {\n"
                + "    @Kernel\n"
                + "    public static void k(@Global float[] out, @Global float[] in) {\n"
                + "        int i = Gpu.getGlobalId(0);\n"
                + "        float x = in[i];\n"
                + s.body().apply(n)
                + "        out[i] = x;\n"
                + "    }\n"
                + "}\n";
        long t0 = System.nanoTime();
        JavacHarness.Outcome javac;
        try {
            javac = JavacHarness.compile(false, null, src);
        } catch (StackOverflowError e) {
            return row(table, s, n, System.nanoTime() - t0, -1, "javac stack overflow");
        }
        long tJavac = System.nanoTime() - t0;
        if (!javac.success()) {
            return row(table, s, n, tJavac, -1, "javac failed");
        }
        long t1 = System.nanoTime();
        AsmFrontend.Result r;
        try {
            r = AsmFrontend.compile(ClassSource.of(javac.classes()), List.of("probe.K"), AsmFrontend.Options.DEFAULT);
        } catch (StackOverflowError e) {
            return row(table, s, n, tJavac, System.nanoTime() - t1, "stack overflow");
        }
        long tAsm = System.nanoTime() - t1;
        if (!r.ok() || r.kernels().size() != 1) {
            return row(table, s, n, tJavac, tAsm, "diagnostics " + r.diagnostics());
        }
        return row(table, s, n, tJavac, tAsm, "ok");
    }

    private static String row(StringBuilder table, Shape s, int n, long javacNs, long asmNs, String verdict) {
        table.append(String.format(Locale.ROOT, "%-14s %6d %10.1f %10s  %s%n", s.name(), n, javacNs / 1e6,
                asmNs < 0 ? "-" : String.format(Locale.ROOT, "%.1f", asmNs / 1e6),
                verdict.length() > 160 ? verdict.substring(0, 160) + "..." : verdict));
        return verdict;
    }

    /** {@code if (x > c) { x = ...; if (...) { ... } } else { x = ...; }} глубиной {@code n}. */
    private static String nestedIf(int n) {
        StringBuilder b = new StringBuilder();
        for (int d = 0; d < n; d++) {
            b.append("if (x > ").append(d).append(".5f) { x = x * 0.5f + 1f;\n");
        }
        for (int d = 0; d < n; d++) {
            b.append("} else { x = x - ").append(d).append("f; }\n");
        }
        return b.toString();
    }

    /** {@code x = x > c0 ? (x > c1 ? (...) : x - 1f) : x + 1f} глубиной {@code n}. */
    private static String nestedTernary(int n) {
        StringBuilder b = new StringBuilder("x = ");
        for (int d = 0; d < n; d++) {
            b.append("x > ").append(d).append(".5f ? (");
        }
        b.append("x * 0.5f");
        for (int d = 0; d < n; d++) {
            b.append(") : x + ").append(d).append('f');
        }
        return b.append(";\n").toString();
    }

    /** {@code n} вложенных циклов по одной итерации. */
    private static String nestedLoops(int n) {
        StringBuilder b = new StringBuilder();
        for (int d = 0; d < n; d++) {
            b.append("for (int j").append(d).append(" = 0; j").append(d).append(" < 1; j").append(d).append("++) {\n");
        }
        b.append("x = x + 1f;\n");
        b.append("}\n".repeat(n));
        return b.toString();
    }

    /** {@code if (x > c0 && x > c1 && ... ) x = -x;} из {@code n} условий. */
    private static String andChain(int n) {
        StringBuilder b = new StringBuilder("if (");
        for (int d = 0; d < n; d++) {
            if (d > 0) {
                b.append(" && ");
            }
            b.append("x > ").append(d).append(".5f");
        }
        return b.append(") { x = -x; }\n").toString();
    }

    /** {@code n} операторов присваивания без ветвлений. */
    private static String straight(int n) {
        StringBuilder b = new StringBuilder();
        for (int d = 0; d < n; d++) {
            b.append("x = x * 0.999f + ").append(d % 7).append(".25f;\n");
        }
        return b.toString();
    }
}
