package com.sixik.gpu.frontend.java;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.optimizer.WriteCoverage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WriteCoverage} на IR, который фронтенд строит из обычного Java-кода: типичные 2D/3D-индексы распознаются, а
 * индекс со смещением - нет.
 */
class WriteCoverageFrontendTest {

    private static WriteCoverage.Coverage coverageOfFirstParam(String kernel) {
        FrontendHarness.Outcome r = FrontendHarness.compile("""
                package demo;

                import com.sixik.gpu.api.Gpu;
                import com.sixik.gpu.api.annotations.*;

                public final class K {
                """ + kernel + "\n}\n");
        assertTrue(r.success(), r.describe());
        IrModule m = r.module();
        return WriteCoverage.coverage(m, m.functionCount() - 1)[0];
    }

    @Test
    void rowMajorJavaIndexIsCovered() {
        WriteCoverage.Coverage c = coverageOfFirstParam("""
                    @Kernel
                    public static void shade(float[] out, int width, float scale) {
                        int x = Gpu.getGlobalId(0);
                        int y = Gpu.getGlobalId(1);
                        out[y * width + x] = (x + y) * scale;
                    }
                """);
        assertNotNull(c);
        assertEquals(2, c.axes());
        assertTrue(c.covers(640 * 480, new long[]{640, 480, 1}, p -> p == 1 ? 640 : Long.MIN_VALUE));
    }

    @Test
    void volumeJavaIndexIsCovered() {
        WriteCoverage.Coverage c = coverageOfFirstParam("""
                    @Kernel
                    public static void fill(int[] out, int nx, int ny) {
                        int x = Gpu.getGlobalId(0);
                        int y = Gpu.getGlobalId(1);
                        int z = Gpu.getGlobalId(2);
                        int i = (z * ny + y) * nx + x;
                        out[i] = x ^ y ^ z;
                    }
                """);
        assertNotNull(c);
        assertEquals(3, c.axes());
        assertTrue(c.covers(8 * 6 * 4, new long[]{8, 6, 4}, p -> p == 1 ? 8 : p == 2 ? 6 : Long.MIN_VALUE));
    }

    @Test
    void offsetIndexIsNotCovered() {
        assertNull(coverageOfFirstParam("""
                    @Kernel
                    public static void shifted(float[] out, int width) {
                        int x = Gpu.getGlobalId(0);
                        int y = Gpu.getGlobalId(1);
                        out[y * width + x + 1] = 1f;
                    }
                """));
    }
}
