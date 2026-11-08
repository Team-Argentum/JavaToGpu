package com.sixik.gpu.test;

import com.sixik.gpu.api.types.Float4;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Правила сравнения результатов: точность целых, ULP вещественных, NaN, знак нуля, объекты хоста. */
class CompareTest {

    private static float next(float f, int steps) {
        return Float.intBitsToFloat(Float.floatToRawIntBits(f) + steps);
    }

    private static double next(double d, long steps) {
        return Double.longBitsToDouble(Double.doubleToRawLongBits(d) + steps);
    }

    @Test
    void integersAreExact() {
        assertNull(Compare.difference(new int[]{1, 2}, new int[]{1, 2}, 5));
        assertTrue(Compare.difference(new int[]{1, 2}, new int[]{1, 3}, 5).contains("[1]"));
        assertNotNull(Compare.difference(new long[]{1L << 40}, new long[]{0}, 0));
        assertNotNull(Compare.difference(new byte[]{1}, new byte[]{2}, 0));
        assertNotNull(Compare.difference(new short[]{1}, new short[]{2}, 0));
        assertNotNull(Compare.difference(new char[]{'a'}, new char[]{'b'}, 0));
        assertNotNull(Compare.difference(new boolean[]{true}, new boolean[]{false}, 0));
        assertNull(Compare.difference(new boolean[]{true}, new boolean[]{true}, 0));
    }

    @Test
    void lengthsAndTypesMustMatch() {
        assertTrue(Compare.difference(new int[2], new int[3], 0).contains("длина 3 вместо 2"));
        assertTrue(Compare.difference(new int[2], new long[2], 0).contains("разные типы"));
        assertNotNull(Compare.difference(new float[1], new float[2], 0));
        assertNotNull(Compare.difference(new double[1], new double[2], 0));
        assertNull(Compare.difference(null, null, 0));
        assertNotNull(Compare.difference(null, new int[1], 0));
        assertNotNull(Compare.difference(new int[1], null, 0));
    }

    @Test
    void scalarsAreNotCompared() {
        assertNull(Compare.difference(1, 2, 0), "скаляры передаются по значению");
    }

    @Test
    void floatsAreComparedInUlps() {
        float x = 1.5f;
        assertTrue(Compare.sameFloat(x, x, 0));
        assertFalse(Compare.sameFloat(x, next(x, 1), 0));
        assertTrue(Compare.sameFloat(x, next(x, 1), 1));
        assertTrue(Compare.sameFloat(x, next(x, -3), 3));
        assertFalse(Compare.sameFloat(x, next(x, 4), 3));
        assertFalse(Compare.sameFloat(x, next(x, -4), 3));
        String why = Compare.difference(new float[]{x}, new float[]{next(x, 4)}, 3);
        assertTrue(why.contains("расстояние 4 ULP, допуск 3"), why);
    }

    @Test
    void ulpDistanceIsContinuousAcrossZero() {
        float tiny = Float.MIN_VALUE;
        assertTrue(Compare.sameFloat(tiny, -tiny, 2), "от +min до -min два шага через ноль");
        assertFalse(Compare.sameFloat(tiny, -tiny, 1));
        assertTrue(Compare.sameFloat(0f, -0f, 1), "знак нуля при ненулевом допуске не различается");
        assertFalse(Compare.sameFloat(0f, -0f, 0), "при допуске 0 - побитовое равенство");
        assertTrue(Compare.sameDouble(Double.MIN_VALUE, -Double.MIN_VALUE, 2));
        assertFalse(Compare.sameDouble(0.0, -0.0, 0));
        assertTrue(Compare.sameDouble(0.0, -0.0, 1));
    }

    @Test
    void nanEqualsAnyNanButNothingElse() {
        float otherNan = Float.intBitsToFloat(0x7fc00123);
        assertTrue(Compare.sameFloat(Float.NaN, otherNan, 0));
        assertTrue(Compare.sameFloat(Float.NaN, otherNan, 100));
        assertFalse(Compare.sameFloat(Float.NaN, 1f, 1_000_000));
        assertFalse(Compare.sameFloat(1f, Float.NaN, 1_000_000));
        assertTrue(Compare.sameDouble(Double.NaN, Double.longBitsToDouble(0x7ff8000000000abcL), 0));
        assertFalse(Compare.sameDouble(Double.NaN, 1.0, 1_000_000));
    }

    @Test
    void infinitiesAreNeighboursOfTheLargestFinite() {
        assertTrue(Compare.sameFloat(Float.MAX_VALUE, Float.POSITIVE_INFINITY, 1));
        assertFalse(Compare.sameFloat(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 1000));
        assertTrue(Compare.sameDouble(Double.MAX_VALUE, Double.POSITIVE_INFINITY, 1));
        assertFalse(Compare.sameDouble(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1000));
    }

    @Test
    void doublesAreComparedInUlps() {
        double x = 3.25;
        assertTrue(Compare.sameDouble(x, next(x, 2L), 2));
        assertFalse(Compare.sameDouble(x, next(x, 3L), 2));
        assertFalse(Compare.sameDouble(x, next(x, -3L), 2));
        assertNotNull(Compare.difference(new double[]{x}, new double[]{next(x, 5L)}, 2));
        assertNull(Compare.difference(new double[]{x}, new double[]{next(x, 2L)}, 2));
        assertFalse(Compare.sameDouble(Double.MAX_VALUE, -Double.MAX_VALUE, 1_000_000), "расстояние через ноль огромно, без переполнения");
    }

    /** Объект хоста с открытыми полями. */
    public static final class Cell {
        public float w;
        public Float4 v = new Float4(1, 2, 3, 4);
        public int tag;
    }

    private static Cell cell(float w, float vx, int tag) {
        Cell c = new Cell();
        c.w = w;
        c.v = new Float4(vx, 2, 3, 4);
        c.tag = tag;
        return c;
    }

    @Test
    void hostObjectsAreComparedFieldByFieldWithUlpsForFloats() {
        assertNull(Compare.difference(new Cell[]{cell(1f, 1f, 7)}, new Cell[]{cell(1f, 1f, 7)}, 0));
        assertNull(Compare.difference(new Cell[]{cell(1f, 1f, 7)}, new Cell[]{cell(next(1f, 2), next(1f, -1), 7)}, 2));
        String w = Compare.difference(new Cell[]{cell(1f, 1f, 7)}, new Cell[]{cell(next(1f, 3), 1f, 7)}, 2);
        assertTrue(w.contains("поле w") && w.contains("[0]"), w);
        String v = Compare.difference(new Cell[]{cell(1f, 1f, 7)}, new Cell[]{cell(1f, 5f, 7)}, 2);
        assertTrue(v.contains("поле v"), v);
        String t = Compare.difference(new Cell[]{cell(1f, 1f, 7)}, new Cell[]{cell(1f, 1f, 8)}, 100);
        assertTrue(t.contains("поле tag"), t);
        assertNotNull(Compare.difference(new Cell[]{null}, new Cell[]{cell(1f, 1f, 7)}, 0));
        assertNull(Compare.difference(new Cell[]{null}, new Cell[]{null}, 0));
    }
}
