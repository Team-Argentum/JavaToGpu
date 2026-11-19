package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.optimizer.OptimizeOptions;
import com.sixik.gpu.ir.optimizer.OptimizeResult;
import com.sixik.gpu.ir.optimizer.Optimizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Код в стиле GA2 ({@code od[4 * i + k]} по стейтменту на полосу, байткод javac) после оптимизатора читает и пишет
 * буферы векторами ({@code ir-optimizer}, проход {@code coalesce}).
 */
class CoalesceFrontendTest {

    private static final String SRC = """
            package d;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.Global;
            import com.sixik.gpu.api.annotations.Kernel;

            public class D {
                @Kernel
                public static void lerp(@Global float[] od, @Global float[] a, float t) {
                    int i = Gpu.getGlobalId(0);
                    od[4 * i] = od[4 * i] + (a[4 * i] - od[4 * i]) * t;
                    od[4 * i + 1] = od[4 * i + 1] + (a[4 * i + 1] - od[4 * i + 1]) * t;
                    od[4 * i + 2] = od[4 * i + 2] + (a[4 * i + 2] - od[4 * i + 2]) * t;
                    od[4 * i + 3] = od[4 * i + 3] + (a[4 * i + 3] - od[4 * i + 3]) * t;
                }

                @Kernel
                public static void lerpd(@Global double[] od, @Global double[] a, double t) {
                    int i = Gpu.getGlobalId(0);
                    int b = i * 4;
                    for (int k = 0; k < 1; k++) {
                        od[b] += (a[b] - od[b]) * t;
                        od[b + 1] += (a[b + 1] - od[b + 1]) * t;
                        od[b + 2] += (a[b + 2] - od[b + 2]) * t;
                        od[b + 3] += (a[b + 3] - od[b + 3]) * t;
                    }
                }
            }
            """;

    private static int intrinsics(IrModule m, String name) {
        int id = Builtins.idOf(name);
        int c = 0;
        for (int n = 0; n < m.nodeCount(); n++) {
            if (m.op(n) == Op.INTRINSIC && m.a(n) == id) {
                c++;
            }
        }
        return c;
    }

    @Test
    void ga2StyleLanesBecomeVectorAccesses() {
        JavacHarness.Outcome java = JavacHarness.compile(true, null, SRC);
        assertTrue(java.success(), java.log());
        AsmFrontend.Result asm = AsmFrontend.compile(ClassSource.of(java.classes()), List.of("d.D"),
                AsmFrontend.Options.DEFAULT);
        assertTrue(asm.diagnostics().isEmpty(), asm.diagnostics().toString());
        for (AsmFrontend.Kernel k : asm.kernels()) {
            OptimizeResult r = Optimizer.optimize(k.module(), OptimizeOptions.DEFAULT);
            assertTrue(r.ok(), r.failures().toString());
            boolean single = k.module().debug().functionName(k.module().functionCount() - 1).equals("lerp");
            String load = single ? "vload4" : "vload2";
            String store = single ? "vstore4" : "vstore2";
            int lanesPerVector = single ? 4 : 2;
            assertEquals(2 * 4 / lanesPerVector, intrinsics(r.module(), load), k.entry() + " " + r.applied());
            assertEquals(4 / lanesPerVector, intrinsics(r.module(), store), k.entry() + " " + r.applied());
        }
    }
}
