package com.sixik.gpu.test;

import com.sixik.gpu.api.types.Float4;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Разбор JSON, {@link KernelSample}: построитель, диапазоны, независимые копии и загрузчик JSON. */
class JsonAndSampleTest {

    // ---- Json ------------------------------------------------------------------------------------

    @Test
    void parsesAllValueKinds() {
        Object v = Json.parse("""
                {"a": [1, -2, 3.5, 1e2, 12345678901234567890], "s": "x\\ny\\u0041\\"", "t": true, "f": false, "n": null, "o": {}}
                """);
        Map<?, ?> m = (Map<?, ?>) v;
        List<?> a = (List<?>) m.get("a");
        assertEquals(1L, a.get(0));
        assertEquals(-2L, a.get(1));
        assertEquals(3.5, a.get(2));
        assertEquals(100.0, a.get(3));
        assertTrue(a.get(4) instanceof Double, "число вне long - double");
        assertEquals("x\nyA\"", m.get("s"));
        assertEquals(true, m.get("t"));
        assertEquals(false, m.get("f"));
        assertNull(m.get("n"));
        assertTrue(m.containsKey("n"));
        assertTrue(((Map<?, ?>) m.get("o")).isEmpty());
        assertEquals(List.of("a", "s", "t", "f", "n", "o"), List.copyOf(m.keySet()), "порядок ключей сохранён");
    }

    @Test
    void reportsPositionOfSyntaxErrors() {
        for (String bad : new String[]{"", "{", "[1,", "{\"a\" 1}", "{\"a\":1,}", "[1 2]", "tru", "\"abc", "{\"a\":1} x",
                "{\"a\":1,\"a\":2}", "[01x]", "\"\\q\""}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Json.parse(bad), bad);
            assertTrue(e.getMessage().startsWith("JSON, строка "), e.getMessage());
        }
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Json.parse("{\n\n  \"a\": ?}")).getMessage()
                .contains("строка 3"));
    }

    // ---- KernelSample ----------------------------------------------------------------------------

    @Test
    void argumentsAreFreshAndIndependentOnEveryCall() {
        float[] data = {1, 2, 3};
        KernelSample s = KernelSample.of("s", data, new int[2], 7);
        data[0] = 100; // исходные данные после создания образца не влияют
        Object[] a = s.args();
        Object[] b = s.args();
        assertNotSame(a[0], b[0]);
        assertArrayEquals(new float[]{1, 2, 3}, (float[]) a[0]);
        ((float[]) a[0])[1] = -1;
        assertArrayEquals(new float[]{1, 2, 3}, (float[]) b[0], "запись в одну копию не видна в другой");
        assertEquals(7, a[2]);
    }

    @Test
    void defaultRangeIsOneDimensionalByTheFirstArray() {
        KernelSample s = KernelSample.of("s", 5, new float[10], new float[3]);
        KernelSample.Range r = s.rangeFor(s.args());
        assertEquals(1, r.dim());
        assertEquals(10, r.global(0));
        assertEquals(0, r.local(0));
        assertThrows(IllegalStateException.class, () -> KernelSample.of("none", 1, 2).rangeFor(new Object[]{1, 2}));
    }

    @Test
    void explicitRangesConvertToBothExecutors() {
        KernelSample.Range r = KernelSample.Range.of2D(8, 4, 4, 2);
        assertEquals("2D global=(8,4) local=(4,2)", r.toString());
        assertEquals(2, r.toNdRange().dim());
        assertEquals(8, r.toNdRange().global(0));
        assertEquals(2, r.toNdRange().local(1));
        assertEquals(4, r.toLaunchConfig().global(1));
        assertEquals("1D global=(16)", KernelSample.Range.of1D(16).toString());
        assertEquals(3, KernelSample.Range.of3D(2, 2, 2, 1, 1, 1).toLaunchConfig().dim());
    }

    @Test
    void builderValidatesAndRecordsOptions() {
        assertThrows(IllegalStateException.class, () -> KernelSample.named("x").build());
        assertThrows(IllegalArgumentException.class, () -> KernelSample.named("x").ulps(-1));
        KernelSample s = KernelSample.named("x").args(() -> new Object[0]).ulps(3)
                .only(KernelSample.Leg.JVM, KernelSample.Leg.INTERPRETER).ignoring(2, 1).build();
        assertEquals(3, s.ulps());
        assertEquals(java.util.Set.of(KernelSample.Leg.JVM, KernelSample.Leg.INTERPRETER), s.legs());
        assertEquals(java.util.Set.of(1, 2), s.ignored());
        assertEquals(java.util.Set.of(KernelSample.Leg.INTERPRETER),
                KernelSample.named("y").args(() -> new Object[0]).without(KernelSample.Leg.JVM, KernelSample.Leg.DEVICE)
                        .build().legs());
    }

    // ---- JSON-загрузчик --------------------------------------------------------------------------

    private static final String SCALE = """
            {
              "name": "scale",
              "range": {"global": [4], "local": [2]},
              "ulps": 2,
              "legs": ["JVM", "INTERPRETER"],
              "ignore": [3],
              "args": [
                {"type": "float[]", "values": [1, 2, "NaN", "-Infinity"]},
                {"type": "float[]", "size": 4},
                {"type": "float", "value": 2.5},
                {"type": "int[]", "size": 3, "fill": "iota"}
              ],
              "expected": [null, {"type": "float[]", "values": [2.5, 5, "NaN", "-Infinity"]}]
            }
            """;

    @Test
    void loadsASampleFromJson() {
        KernelSample s = KernelSample.fromJson(SCALE);
        assertEquals("scale", s.name());
        assertEquals(2, s.ulps());
        assertEquals(java.util.Set.of(KernelSample.Leg.JVM, KernelSample.Leg.INTERPRETER), s.legs());
        assertEquals(java.util.Set.of(3), s.ignored());
        Object[] args = s.args();
        assertEquals(4, args.length);
        float[] in = (float[]) args[0];
        assertEquals(1f, in[0]);
        assertTrue(Float.isNaN(in[2]));
        assertEquals(Float.NEGATIVE_INFINITY, in[3]);
        assertArrayEquals(new float[4], (float[]) args[1]);
        assertEquals(2.5f, args[2]);
        assertArrayEquals(new int[]{0, 1, 2}, (int[]) args[3]);
        KernelSample.Range r = s.rangeFor(args);
        assertEquals(4, r.global(0));
        assertEquals(2, r.local(0));
        float[] expected = (float[]) s.expected()[1];
        assertEquals(2.5f, expected[0]);
        assertNull(s.expected()[0]);
        assertNotSame(s.args()[0], s.args()[0]);
    }

    @Test
    void randomFillIsDeterministicBySeed() {
        String json = """
                {"args": [{"type": "int[]", "size": 5, "fill": "random", "seed": 7},
                          {"type": "float[]", "size": 5, "fill": "random", "seed": 7},
                          {"type": "long[]", "size": 2, "fill": "random", "seed": 1}]}
                """;
        Object[] a = KernelSample.fromJson(json).args();
        Object[] b = KernelSample.fromJson(json).args();
        assertArrayEquals((int[]) a[0], (int[]) b[0]);
        assertArrayEquals((float[]) a[1], (float[]) b[1]);
        assertArrayEquals((long[]) a[2], (long[]) b[2]);
        java.util.Random rnd = new java.util.Random(7);
        assertEquals(rnd.nextInt(), ((int[]) a[0])[0], "формула описана: nextInt()");
        for (float f : (float[]) a[1]) {
            assertTrue(f >= -1f && f < 1f);
        }
    }

    @Test
    void allElementTypesAreSupported() {
        Object[] args = KernelSample.fromJson("""
                {"args": [
                  {"type": "int", "value": -3}, {"type": "long", "value": 5000000000}, {"type": "double", "value": 0.25},
                  {"type": "boolean[]", "values": [true, false, 1, 0]},
                  {"type": "byte[]", "values": [1, -2]}, {"type": "short[]", "values": [300]}, {"type": "char[]", "values": [65]},
                  {"type": "double[]", "values": [1.5, "Infinity"]}, {"type": "long[]", "values": [1, 2]}]}
                """).args();
        assertEquals(-3, args[0]);
        assertEquals(5_000_000_000L, args[1]);
        assertEquals(0.25, args[2]);
        assertArrayEquals(new boolean[]{true, false, true, false}, (boolean[]) args[3]);
        assertArrayEquals(new byte[]{1, -2}, (byte[]) args[4]);
        assertArrayEquals(new short[]{300}, (short[]) args[5]);
        assertArrayEquals(new char[]{'A'}, (char[]) args[6]);
        assertEquals(Double.POSITIVE_INFINITY, ((double[]) args[7])[1]);
        assertArrayEquals(new long[]{1, 2}, (long[]) args[8]);
    }

    @Test
    void formatErrorsAreReportedAtLoadTimeWithAPlace() {
        String[] bad = {
                "[]",
                "{\"args\": 1}",
                "{\"args\": [{\"value\": 1}]}",
                "{\"args\": [{\"type\": \"string\", \"value\": 1}]}",
                "{\"args\": [{\"type\": \"int\", \"value\": 1.5}]}",
                "{\"args\": [{\"type\": \"float[]\", \"values\": [\"x\"]}]}",
                "{\"args\": [{\"type\": \"float[]\"}]}",
                "{\"args\": [{\"type\": \"int[]\", \"size\": 2, \"fill\": \"sparse\"}]}",
                "{\"args\": [], \"range\": {\"global\": []}}",
                "{\"args\": [], \"range\": {\"global\": [1, 2], \"local\": [1]}}",
                "{\"args\": [], \"legs\": [\"GPU\"]}",
        };
        for (String json : bad) {
            assertThrows(RuntimeException.class, () -> KernelSample.fromJson(json).args(), json);
        }
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> KernelSample.fromJson("{\"args\": [{\"type\": \"int\", \"value\": \"x\"}]}")).getMessage().contains("args[0]"));
    }

    @Test
    void loadsAFileInUtf8(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("sample.json"), "{\"name\": \"образец\", \"args\": [{\"type\": \"int\", \"value\": 1}]}");
        assertEquals("образец", KernelSample.loadJson(file).name());
    }

    // ---- Copies ----------------------------------------------------------------------------------

    /** Struct хоста: открытый конструктор без аргументов и открытые поля. */
    public static final class Cell {
        public float w;
        public Float4 v = new Float4(1, 2, 3, 4);
        public int tag;
    }

    @Test
    void deepCopyIsIndependentForStructsAndSharesImmutableVectors() {
        Cell original = new Cell();
        original.w = 5;
        original.tag = 9;
        Cell[] cells = {original, null};
        Cell[] copy = (Cell[]) Copies.copy(cells);
        assertNotSame(copy, cells);
        assertNotSame(copy[0], original);
        assertNull(copy[1]);
        assertEquals(5f, copy[0].w);
        assertSame(copy[0].v, original.v, "вектор неизменяем и разделяется");
        copy[0].w = -1;
        assertEquals(5f, original.w);
        int[][] nested = {{1, 2}};
        assertNotSame(((int[][]) Copies.copy(nested))[0], nested[0]);
        assertEquals("x", Copies.copy("x"));
        assertNull(Copies.copy(null));
        assertNull(Copies.deepCopy(null));
    }
}
