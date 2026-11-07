package com.sixik.gpu.api;

import com.sixik.gpu.api.types.Double3;
import com.sixik.gpu.api.types.Float16;
import com.sixik.gpu.api.types.Float2;
import com.sixik.gpu.api.types.Float4;
import com.sixik.gpu.api.types.Int2;
import com.sixik.gpu.api.types.Long4;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JVM-реализации математических builtins: крайние значения и семантика (математика) и векторные перегрузки. */
class GpuBuiltinsTest {

    private static boolean sameBits(float a, float b) {
        return Float.floatToRawIntBits(a) == Float.floatToRawIntBits(b);
    }

    private static boolean sameBits(double a, double b) {
        return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b);
    }

    @Test
    void sinCosSpecialValues() {
        assertTrue(sameBits(0f, Gpu.sin(0f)));
        assertTrue(sameBits(-0f, Gpu.sin(-0f)), "sin(-0) = -0");
        assertTrue(Float.isNaN(Gpu.sin(Float.NaN)));
        assertTrue(Float.isNaN(Gpu.sin(Float.POSITIVE_INFINITY)), "sin(inf) = NaN");
        assertTrue(Float.isNaN(Gpu.cos(Float.NEGATIVE_INFINITY)));
        assertEquals(1f, Gpu.cos(0f));
        assertEquals(1f, Gpu.sin((float) (Math.PI / 2)));
        assertEquals(-1f, Gpu.cos((float) Math.PI));
        assertTrue(sameBits(-0.0, Gpu.sin(-0.0)));
        assertEquals(1.0, Gpu.cos(0.0));
        assertTrue(Double.isNaN(Gpu.sin(Double.POSITIVE_INFINITY)));
        // огромный аргумент: редукция по модулю 2π не должна давать NaN или значение вне [-1, 1]
        assertTrue(Math.abs(Gpu.sin(Float.MAX_VALUE)) <= 1f);
        assertTrue(Math.abs(Gpu.cos(Double.MAX_VALUE)) <= 1.0);
    }

    @Test
    void sqrtSpecialValues() {
        assertEquals(2f, Gpu.sqrt(4f));
        assertEquals(3.0, Gpu.sqrt(9.0));
        assertTrue(Float.isNaN(Gpu.sqrt(-1f)));
        assertTrue(Double.isNaN(Gpu.sqrt(-1e-300)));
        assertTrue(sameBits(-0f, Gpu.sqrt(-0f)), "sqrt(-0) = -0");
        assertEquals(Float.POSITIVE_INFINITY, Gpu.sqrt(Float.POSITIVE_INFINITY));
        assertTrue(Float.isNaN(Gpu.sqrt(Float.NaN)));
        // subnormal: корень от минимального положительного float - нормальное число, а не 0
        assertTrue(Gpu.sqrt(Float.MIN_VALUE) > 0f);
        assertEquals((float) Math.sqrt(Float.MIN_VALUE), Gpu.sqrt(Float.MIN_VALUE));
    }

    @Test
    void fmaRoundsOnce() {
        assertEquals(10f, Gpu.fma(2f, 3f, 4f));
        assertEquals(10.0, Gpu.fma(2.0, 3.0, 4.0));
        // a = 1 + 2^-12: a*a = 1 + 2^-11 + 2^-24. Произведение float округляется до 1 + 2^-11 (ровно половина шага, к чётному),
        // поэтому «a*a + c» с c = -(1 + 2^-11) даёт 0, а слитная операция сохраняет 2^-24.
        float a = 1f + 0x1p-12f;
        float c = -(1f + 0x1p-11f);
        assertEquals(0f, a * a + c, "раздельно: произведение уже округлено");
        assertEquals(0x1p-24f, Gpu.fma(a, a, c), "слитно: одно округление");
        double da = 1.0 + 0x1p-27;
        double dc = -(1.0 + 0x1p-26);
        assertEquals(0.0, da * da + dc);
        assertEquals(0x1p-54, Gpu.fma(da, da, dc));
        assertTrue(Float.isNaN(Gpu.fma(Float.POSITIVE_INFINITY, 0f, 1f)), "inf * 0 = NaN");
    }

    @Test
    void integerMaxMinAtBounds() {
        assertEquals(Integer.MAX_VALUE, Gpu.max(Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertEquals(-1, Gpu.max(Integer.MIN_VALUE, -1));
        assertEquals(Integer.MIN_VALUE, Gpu.min(Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, Gpu.max(Long.MIN_VALUE, Long.MAX_VALUE));
        assertEquals(Long.MIN_VALUE, Gpu.min(Long.MIN_VALUE, -1L));
        assertEquals(7, Gpu.max(7, 7));
        // смешанные int/long выбирают long-перегрузку без потери значения
        assertEquals(1L << 40, Gpu.max(5, 1L << 40));
    }

    @Test
    void floatMaxMinFollowJavaSemantics() {
        assertTrue(sameBits(0f, Gpu.max(-0f, 0f)), "max(-0, +0) = +0");
        assertTrue(sameBits(-0f, Gpu.min(-0f, 0f)), "min(-0, +0) = -0");
        assertTrue(Float.isNaN(Gpu.max(Float.NaN, 1f)), "NaN распространяется (в отличие от fmax OpenCL)");
        assertTrue(Double.isNaN(Gpu.min(1.0, Double.NaN)));
        assertEquals(Float.POSITIVE_INFINITY, Gpu.max(Float.POSITIVE_INFINITY, Float.MAX_VALUE));
        assertEquals(Float.NEGATIVE_INFINITY, Gpu.min(Float.NEGATIVE_INFINITY, -Float.MAX_VALUE));
        assertEquals(2.5, Gpu.max(2.5, -3.0));
    }

    @Test
    void assumeUniformIsIdentity() {
        assertEquals(42, Gpu.assumeUniform(42));
        assertEquals(Long.MIN_VALUE, Gpu.assumeUniform(Long.MIN_VALUE));
        assertTrue(sameBits(-0f, Gpu.assumeUniform(-0f)));
        assertTrue(Float.isNaN(Gpu.assumeUniform(Float.NaN)));
        assertEquals(1.5, Gpu.assumeUniform(1.5));
    }

    @Test
    void vectorOverloadsAreComponentWise() {
        Float4 v = Gpu.sin(new Float4(0f, 1f, 2f, 3f));
        assertEquals(new Float4(Gpu.sin(0f), Gpu.sin(1f), Gpu.sin(2f), Gpu.sin(3f)), v);

        assertEquals(new Int2(5, 2), Gpu.max(new Int2(5, -1), new Int2(3, 2)));
        assertEquals(new Long4(1, 2, 3, 4), Gpu.min(new Long4(1, 5, 3, 9), new Long4(2, 2, 7, 4)));
        assertEquals(new Float2(5f, 7f), Gpu.fma(new Float2(1f, 2f), new Float2(2f, 3f), new Float2(3f, 1f)));
        Double3 s = Gpu.sqrt(new Double3(4.0, 9.0, 16.0));
        assertEquals(new Double3(2.0, 3.0, 4.0), s);
        assertEquals(new Float2(1f, 2f), Gpu.assumeUniform(new Float2(1f, 2f)));
    }

    @Test
    void sixteenLaneVectorMapsEveryLane() {
        float[] in = new float[16];
        for (int i = 0; i < 16; i++) {
            in[i] = i * i;
        }
        Float16 r = Gpu.sqrt(new Float16(in[0], in[1], in[2], in[3], in[4], in[5], in[6], in[7],
                in[8], in[9], in[10], in[11], in[12], in[13], in[14], in[15]));
        float[] out = {r.s0, r.s1, r.s2, r.s3, r.s4, r.s5, r.s6, r.s7, r.s8, r.s9, r.sA, r.sB, r.sC, r.sD, r.sE, r.sF};
        for (int i = 0; i < 16; i++) {
            assertEquals((float) i, out[i], "компонента " + i);
        }
    }
}
