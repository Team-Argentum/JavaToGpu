package com.sixik.gpu.frontend.java;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Local-память, барьеры и атомики во фронтенде (M10): какие узлы IR получаются, как работает {@code IR-B01}
 * ({@code GPU0130} предупреждение, {@code GPU0131} при {@code strictBarriers}) и какие использования отвергаются.
 */
class LocalMemoryLoweringTest {

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

    private static FrontendHarness.Outcome compileStrict(String members) {
        return FrontendHarness.compile(new GpuProcessor(), List.of("-proc:only", "-A" + GpuProcessor.STRICT_BARRIERS + "=true"),
                HEAD + members + "\n}\n");
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

    private static void rejects(String code, String members) {
        FrontendHarness.Outcome r = compile(members);
        assertFalse(r.success(), "ожидалась ошибка " + code + "\n" + r.describe());
        assertTrue(r.errorCodes().contains(code), code + " не выдан; выданы: " + r.errorCodes() + "\n" + r.describe());
    }

    @Test
    void localArrayBarrierAndAtomicsLowerToExpectedIr() {
        Golden.assertLowers("local.ir.txt", """
                package demo;

                import com.sixik.gpu.api.Gpu;
                import com.sixik.gpu.api.annotations.*;
                import com.sixik.gpu.api.types.*;

                public final class Groups {
                    @Kernel
                    @WorkGroup(x = 8)
                    public static void exchange(@Global int[] in, @Global int[] out, @Global int[] total) {
                        @Local int[] tile = new int[8];
                        int lid = Gpu.getLocalId(0);
                        tile[lid] = in[Gpu.getGlobalId(0)];
                        Gpu.barrier();
                        out[Gpu.getGlobalId(0)] = tile[7 - lid];
                        Gpu.barrierGlobal();
                        int old = Gpu.atomicAdd(GlobalIntPtr.of(total, 0), tile[lid]);
                        Gpu.atomicMax(LocalIntPtr.of(tile, 0), old);
                    }
                }
                """);
    }

    @Test
    void barrierBecomesABarrierNodeNotAnIntrinsic() {
        FrontendHarness.Outcome r = compile("""
                @Kernel @WorkGroup(x = 4)
                public static void k(@Global int[] a) {
                    @Local int[] t = new int[4];
                    t[Gpu.getLocalId(0)] = 1;
                    Gpu.barrier();
                    Gpu.barrierGlobal();
                    a[Gpu.getGlobalId(0)] = t[0];
                }
                """);
        assertTrue(r.success(), r.describe());
        IrModule m = r.module();
        assertEquals(2, count(m, Op.BARRIER));
        assertEquals(1, count(m, Op.LOCAL_ARRAY_DECL));
        for (int i = 0; i < m.nodeCount(); i++) {
            if (m.op(i) == Op.BARRIER) {
                assertTrue(m.a(i) == 1 || m.a(i) == 3, "флаги барьера: local и local+global");
            }
            if (m.op(i) == Op.INTRINSIC) {
                assertFalse(Builtins.has(m.a(i), Builtins.SYNC));
            }
        }
        assertTrue(Validator.validate(m).functions()[0].usesGroupSync());
    }

    @Test
    void atomicTakesPointersOfTheTwoSpacesAndReturnsTheOldValue() {
        FrontendHarness.Outcome r = compile("""
                @Kernel
                public static void k(@Global int[] counters, @Global long[] wide) {
                    int old = Gpu.atomicAdd(GlobalIntPtr.of(counters, 0), 1);
                    counters[1] = old;
                    Gpu.atomicXchg(GlobalLongPtr.of(wide, 0), 5L);
                    GlobalIntPtr p = GlobalIntPtr.of(counters, 2);
                    Gpu.atomicMin(p, old);
                }
                """);
        assertTrue(r.success(), r.describe());
        IrModule m = r.module();
        assertEquals(3, count(m, Op.INTRINSIC), "atomicAdd, atomicXchg, atomicMin");
    }

    @Test
    void referenceCannotBeGivenToAnAtomic() {
        rejects("GPU0198", """
                @Kernel public static void k(@Global int[] a) {
                    IntPtr r = new IntPtr();
                    Gpu.atomicAdd(r, 1);
                }
                """);
    }

    @Test
    void atomicOnConstantPointerDoesNotCompile() {
        FrontendHarness.Outcome r = compile("""
                @Kernel public static void k(@Global(constant = true) int[] c) {
                    Gpu.atomicAdd(ConstantIntPtr.of(c, 0), 1);
                }
                """);
        assertFalse(r.success(), "в constant атомиков нет: перегрузка отсутствует, javac отвергает вызов");
    }

    // ---- IR-B01 --------------------------------------------------------------------------------

    private static final String DIVERGENT = """
            @Kernel @WorkGroup(x = 4)
            public static void k(@Global int[] a) {
                if (Gpu.getLocalId(0) == 0) {
                    Gpu.barrier();
                }
                a[0] = 1;
            }
            """;

    @Test
    void barrierUnderDivergentControlIsAWarning() {
        FrontendHarness.Outcome r = compile(DIVERGENT);
        assertTrue(r.success(), "по умолчанию IR-B01 - предупреждение: " + r.describe());
        assertTrue(r.gpu().stream().anyMatch(d -> d.code().equals("GPU0130")), r.describe());
        assertFalse(r.modules().isEmpty(), "IR записан");
    }

    @Test
    void strictBarriersTurnsTheWarningIntoAnError() {
        FrontendHarness.Outcome r = compileStrict(DIVERGENT);
        assertFalse(r.success());
        assertTrue(r.errorCodes().contains("GPU0131"), r.describe());
        assertTrue(r.resources().isEmpty(), "ядро с ошибкой IR не записывается");
    }

    @Test
    void assumeUniformSuppressesTheDiagnostic() {
        String uniform = DIVERGENT.replace("Gpu.getLocalId(0) == 0", "Gpu.assumeUniform(Gpu.getLocalId(0)) == 0");
        FrontendHarness.Outcome r = compileStrict(uniform);
        assertTrue(r.success(), r.describe());
        assertTrue(r.gpu().isEmpty(), r.describe());
    }

    @Test
    void barrierInsideADeviceFunctionCalledUnderDivergenceIsReported() {
        FrontendHarness.Outcome r = compile("""
                @DeviceFunction static void sync() { Gpu.barrier(); }
                @Kernel @WorkGroup(x = 4)
                public static void k(@Global int[] a) {
                    if (Gpu.getGlobalId(0) < 2) {
                        sync();
                    }
                }
                """);
        assertTrue(r.gpu().stream().anyMatch(d -> d.code().equals("GPU0130")), r.describe());
    }

    @Test
    void uniformLoopWithBarrierProducesNoDiagnostic() {
        FrontendHarness.Outcome r = compileStrict("""
                @Kernel @WorkGroup(x = 8)
                public static void k(@Global int[] a) {
                    @Local int[] t = new int[8];
                    int lid = Gpu.getLocalId(0);
                    t[lid] = lid;
                    for (int s = 4; s > 0; s >>= 1) {
                        Gpu.barrier();
                        if (lid < s) {
                            t[lid] += t[lid + s];
                        }
                    }
                    a[Gpu.getGlobalId(0)] = t[0];
                }
                """);
        assertTrue(r.success(), r.describe());
        assertTrue(r.gpu().isEmpty(), r.describe());
    }

    @Test
    void localArrayRulesStillApply() {
        rejects("GPU0120", """
                @DeviceFunction static void f() { @Local int[] t = new int[4]; }
                """);
        rejects("GPU0121", """
                @DeviceFunction static void put(@Local int[] t) { t[0] = 1; }
                @Kernel public static void k(@Global int[] a) { put(a); }
                """);
    }

    @Test
    void localPointerFromLocalArrayHasLocalSpace() {
        FrontendHarness.Outcome r = compile("""
                @DeviceFunction static void put(LocalIntPtr p, int v) { p.set(0, v); }
                @Kernel @WorkGroup(x = 2)
                public static void k(@Global int[] a) {
                    @Local int[] t = new int[2];
                    put(LocalIntPtr.of(t, Gpu.getLocalId(0)), 7);
                    Gpu.barrier();
                    a[Gpu.getGlobalId(0)] = t[1 - Gpu.getLocalId(0)];
                }
                """);
        assertTrue(r.success(), r.describe());
        boolean found = false;
        IrModule m = r.module();
        for (int t = 0; t < m.typeCount(); t++) {
            found |= m.typeKind(t) == com.sixik.gpu.ir.Types.KIND_PTR && m.typeA(t) == AddressSpace.LOCAL;
        }
        assertTrue(found, "Ptr<local, i32> в таблице типов");
    }
}
