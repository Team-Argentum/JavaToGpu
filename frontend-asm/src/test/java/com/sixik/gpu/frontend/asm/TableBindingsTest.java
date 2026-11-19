package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.optimizer.OptimizeOptions;
import com.sixik.gpu.ir.optimizer.OptimizeResult;
import com.sixik.gpu.ir.optimizer.Optimizer;
import com.sixik.gpu.tools.IrDump;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Таблицы констант ({@link Bindings#table}, FRONTEND_ASM_RUNTIME.md, 18.2-S3, решение 18.6-5): {@code getstatic}
 * привязанного поля-массива - {@code __constant}-таблица, которую ядро индексирует и у которой берёт длину; запись и
 * псевдоним в локали - {@code GPU0116}, массив не того типа - {@code GPU0308}.
 */
class TableBindingsTest {

    private static final String DATA = """
            package ga2;

            public class Data {
                public static int[] PERM = {0};
                public static float[] W = {0f};
                public static char[] CODES = {'a'};
            }
            """;

    private static final String KERNELS = """
            package gen;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.Global;
            import ga2.Data;

            public class Tab {
                public static void k(@Global int[] a) {
                    int i = Gpu.getGlobalId(0);
                    a[i] = Data.PERM[a[i] & 7] + Data.PERM.length + (int) (Data.W[1] * 4f) + Data.CODES[0];
                }

                public static void write(@Global int[] a) {
                    Data.PERM[0] = a[0];
                }

                public static void alias(@Global int[] a) {
                    int[] t = Data.PERM;
                    a[0] = t[1];
                }
            }
            """;

    private static final int[] PERM = {5, 3, 7, 1, 0, 6, 2, 4};

    private static Map<String, byte[]> library;
    private static ClassNode kernels;

    private static synchronized void init() {
        if (library != null) {
            return;
        }
        JavacHarness.Outcome o = JavacHarness.compile(false, null, DATA, KERNELS);
        assertTrue(o.success(), o.log());
        library = new HashMap<>(o.classes());
        kernels = new ClassNode();
        new ClassReader(library.remove("gen/Tab")).accept(kernels, 0);
    }

    private static Bindings tables() {
        return Bindings.none().table("ga2/Data", "PERM", PERM).table("ga2/Data", "W", new float[]{0.5f, 2.25f})
                .table("ga2/Data", "CODES", new char[]{'￿'});
    }

    private static AsmJit.Result jit(Bindings b, String method) {
        init();
        ClassSource src = ClassSource.chain(ClassSource.of(library), ClassSource.of(TableBindingsTest.class.getClassLoader()));
        return AsmJit.compile(new AsmJit.Unit(src, Map.of("gen/Tab", kernels),
                List.of(AsmJit.Entry.of("gen/Tab", method, "([I)V")), b, Links.none()), AsmJit.Options.DEFAULT);
    }

    private static List<String> codes(AsmJit.Result r) {
        return r.diagnostics().stream().map(AsmDiagnostic::code).toList();
    }

    private static int[] run(IrModule m) {
        int[] a = new int[16];
        for (int i = 0; i < a.length; i++) {
            a[i] = i * 5;
        }
        assertEquals("ok", CorpusTest.outcome(m, NdRange.of1D(a.length), new Object[]{a}));
        return a;
    }

    @Test
    void boundTableIsReadByTheKernel() {
        AsmJit.Result r = jit(tables(), "k");
        assertTrue(r.ok(), r.diagnostics().toString());
        IrModule m = r.kernels().get(0).module();
        int[] expected = new int[16];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = PERM[(i * 5) & 7] + PERM.length + 9 + 0xffff;
        }
        assertArrayEquals(expected, run(m));
        assertTrue(IrDump.dump(m, false).contains("table"), IrDump.dump(m, false));
        OptimizeResult o = Optimizer.optimize(m, OptimizeOptions.DEFAULT);
        assertTrue(o.ok(), o.failures().toString());
        assertArrayEquals(expected, run(o.module()));
    }

    @Test
    void tableIsCopiedWhenBound() {
        int[] perm = PERM.clone();
        Bindings b = Bindings.none().table("ga2/Data", "PERM", perm).table("ga2/Data", "W", new float[]{0f, 0f})
                .table("ga2/Data", "CODES", new char[]{0});
        perm[1] = 1000;
        IrModule m = jit(b, "k").kernels().get(0).module();
        assertEquals(PERM[5] + 8, run(m)[1], "the kernel sees the array as it was bound");
    }

    @Test
    void writeAndAliasAreRejected() {
        AsmJit.Result w = jit(tables(), "write");
        assertEquals(List.of("GPU0116"), codes(w), w.diagnostics().toString());
        assertTrue(w.diagnostics().get(0).message().contains("read-only"), w.diagnostics().toString());
        AsmJit.Result a = jit(tables(), "alias");
        assertEquals(List.of("GPU0116"), codes(a), a.diagnostics().toString());
    }

    @Test
    void tableOfAnotherTypeIsRejected() {
        AsmJit.Result r = jit(tables().table("ga2/Data", "PERM", new long[]{1}), "k");
        assertEquals(List.of("GPU0308", "GPU0308"), codes(r), "both reads of PERM: " + r.diagnostics());
        assertTrue(r.diagnostics().get(0).message().contains("the value is long[]"), r.diagnostics().toString());
        assertEquals("a table is not empty", Bindings.problem("[I", new int[0]));
        assertTrue(Bindings.problem("[Z", new boolean[]{true}).startsWith("a table is"));
        assertNull(Bindings.problem("[C", new char[]{'x'}));
        assertThrows(IllegalArgumentException.class, () -> Bindings.none().table("a", "b", new Integer[]{1}));
    }
}
