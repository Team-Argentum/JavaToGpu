package com.sixik.gpu.apigen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Генератор Java-API: разбор таблицы (позитивные и негативные случаи) и вид сгенерированного кода. */
class ApiGeneratorTest {

    private static final String HEAD = "version 1\nvectors f32 f64 : 2 4\npointers f32\n";

    private static String generic(String extra) {
        return HEAD + "7 foo pure,ulp=4 T (T:genf)\ndoc Описание foo.\n" + extra;
    }

    private static String gpu(String defText) {
        return ApiGenerator.generate(BuiltinsDef.parse(defText)).files().get("com/sixik/gpu/api/Gpu.java");
    }

    private static String parseError(String text) {
        return assertThrows(IllegalArgumentException.class, () -> BuiltinsDef.parse(text)).getMessage();
    }

    // ---- разбор ------------------------------------------------------------------------------------

    @Test
    void parsesMinimalDefinition() {
        var def = BuiltinsDef.parse(generic("jvm f32 = (float) a\njvm f64 = a\n"));
        assertEquals(1, def.version());
        assertEquals(List.of("f32", "f64"), def.vectorScalars());
        assertEquals(List.of(2, 4), def.vectorLanes());
        assertEquals(List.of("f32"), def.pointerScalars());
        var b = def.builtins().get(0);
        assertEquals("foo", b.name());
        assertEquals(7, b.id());
        assertEquals(4, b.ulp());
        assertEquals(4, b.ulp64(), "without ulp64 it equals ulp");
        assertEquals("T", b.ret());
        assertEquals(List.of("T:genf"), b.args());
        assertEquals("Описание foo.", b.doc());
        assertEquals("(float) a", b.jvm().get("f32"));
    }

    @Test
    void ulp64OverridesUlp() {
        var def = BuiltinsDef.parse(HEAD + "1 bar pure,ulp=3,ulp64=0 T (T:genf)\ndoc d\njvm f32,f64 = a\n");
        assertEquals(3, def.builtins().get(0).ulp());
        assertEquals(0, def.builtins().get(0).ulp64());
        assertEquals("a", def.builtins().get(0).jvm().get("f64"), "comma-separated keys define one expression");
    }

    @Test
    void buildinsAreSortedById() {
        var def = BuiltinsDef.parse(HEAD + "5 b pure i32 ()\ndoc d\njvm = 1\n2 a pure i32 ()\ndoc d\njvm = 2\n");
        assertEquals("a", def.builtins().get(0).name());
        assertEquals("b", def.builtins().get(1).name());
    }

    @Test
    void rejectsMalformedDefinitions() {
        assertTrue(parseError("1 a pure i32 ()\ndoc d\n").contains("version"), "no version");
        assertTrue(parseError(HEAD + "bogus x\n").contains("bogus"), "unknown directive");
        assertTrue(parseError(HEAD + "1 a pure i32 ()\ndoc d\n1 b pure i32 ()\ndoc d\n").contains("duplicate id"));
        assertTrue(parseError(HEAD + "1 a pure i32 ()\ndoc d\n2 a pure i32 ()\ndoc d\n").contains("duplicate name"));
        assertTrue(parseError(HEAD + "1 a weird i32 ()\ndoc d\n").contains("weird"), "unknown flag");
        assertTrue(parseError(HEAD + "1 a pure i32\ndoc d\n").contains("signature"), "no parentheses");
        assertTrue(parseError(HEAD + "1 a pure\n").contains("expected"), "too few fields");
        assertTrue(parseError(HEAD + "1 a pure i32 ()\n").contains("missing doc"), "doc is required");
        assertTrue(parseError(HEAD + "1 a pure i32 ()\ndoc one\ndoc two\n").contains("duplicate doc"));
        assertTrue(parseError(HEAD + "doc orphan\n").contains("before the first builtin line"), "doc before builtin");
        assertTrue(parseError(HEAD + "jvm = 1\n").contains("before the first builtin line"), "jvm before builtin");
        assertTrue(parseError(HEAD + "1 a pure i32 ()\ndoc d\njvm 1\n").contains("jvm"), "jvm without '='");
        assertTrue(parseError(HEAD + "1 a pure i32 ()\ndoc d\njvm =\n").contains("empty"), "empty expression");
        assertTrue(parseError(HEAD + "1 a pure i32 ()\ndoc d\njvm = 1\njvm = 2\n").contains("duplicate"));
        assertTrue(parseError(HEAD + "1 a pure i32 ()\ndoc d\njvm u8 = 1\n").contains("Java representation"),
                "jvm key outside the API set");
        assertTrue(parseError("version 1\nvectors f32 : 5\n").contains("5"), "invalid lane count");
        assertTrue(parseError("version 1\nvectors f32 2\n").contains("vectors"), "no colon");
        assertTrue(parseError("version 1\nvectors u32 : 2\n").contains("u32"), "scalar without a Java representation");
        assertTrue(parseError("version x\n").contains("version"), "version is not a number");
        assertTrue(parseError(HEAD + "x a pure i32 ()\n").contains("x"), "unknown directive instead of id");
    }

    @Test
    void errorMessagesCarryLineNumbers() {
        String msg = parseError("version 1\n\n\nbogus\n");
        assertTrue(msg.contains(":4:"), msg);
    }

    // ---- генерация ---------------------------------------------------------------------------------

    @Test
    void genericBuiltinProducesScalarAndVectorOverloads() {
        String src = gpu(generic("jvm f32 = (float) a\njvm f64 = a\n"));
        assertTrue(src.contains("public static float foo(float a) {\n        return (float) a;"), src);
        assertTrue(src.contains("public static double foo(double a) {\n        return a;"), src);
        assertTrue(src.contains("public static Float2 foo(Float2 a)"));
        assertTrue(src.contains("return new Float2(foo(a.x), foo(a.y));"), src);
        assertTrue(src.contains("return new Float4(foo(a.x), foo(a.y), foo(a.z), foo(a.w));"));
        assertTrue(src.contains("public static Double4 foo(Double4 a)"));
        assertFalse(src.contains("Int2"), "vectors does not include i32");
        assertTrue(src.contains("Описание foo."), "doc goes into Javadoc");
        assertTrue(src.contains("@param a первый аргумент"));
    }

    @Test
    void multiArgBuiltinPassesEachLane() {
        String src = gpu(HEAD + "1 mix pure T (T:genf, T, T)\ndoc d\njvm f32,f64 = a + b + c\n");
        assertTrue(src.contains("public static float mix(float a, float b, float c)"));
        assertTrue(src.contains("return new Float2(mix(a.x, b.x, c.x), mix(a.y, b.y, c.y));"), src);
    }

    @Test
    void nonGenericBuiltinHasSingleOverload() {
        String src = gpu(HEAD + "1 dim pure,uniform i32 ()\ndoc d\njvm = WorkItem.workDim()\n");
        assertTrue(src.contains("public static int dim() {\n        return WorkItem.workDim();"), src);
        assertEquals(1, src.split("public static ", -1).length - 1, "one overload");
    }

    @Test
    void classesSelectScalarSets() {
        String src = gpu("version 1\n1 gi pure T (T:geni)\ndoc d\njvm i32,i64 = a\n"
                + "2 gn pure T (T:genn, T)\ndoc d\njvm f32,f64,i32,i64 = a\n");
        assertTrue(src.contains("public static int gi(int a)"));
        assertTrue(src.contains("public static long gi(long a)"));
        assertFalse(src.contains("float gi("), "geni does not include float");
        assertTrue(src.contains("public static float gn(float a, float b)"));
        assertTrue(src.contains("public static long gn(long a, long b)"));
    }

    @Test
    void mixedFixedArgumentIsPassedUnchangedToLanes() {
        String src = gpu(HEAD + "1 scale pure T (T:genf, i32)\ndoc d\njvm f32,f64 = a * b\n");
        assertTrue(src.contains("public static Float2 scale(Float2 a, int b)"), src);
        assertTrue(src.contains("new Float2(scale(a.x, b), scale(a.y, b))"), src);
    }

    @Test
    void pointerBuiltinGetsOverloadsForGlobalAndLocalPointersWithBlockBody() {
        var out = ApiGenerator.generate(BuiltinsDef.parse(HEAD
                + "8 atom effect,atomic T (ptr<T:geni>, T)\ndoc Атомик.\n"
                + "jvm i32 = { int old = a.get(0); a.set(0, old + b); return old; }\n"
                + "jvm i64 = { long old = a.get(0); a.set(0, old + b); return old; }\n"));
        assertEquals(List.of(), out.skipped());
        String src = out.files().get("com/sixik/gpu/api/Gpu.java");
        for (String sig : new String[]{"int atom(GlobalIntPtr a, int b)", "int atom(LocalIntPtr a, int b)",
                "long atom(GlobalLongPtr a, long b)", "long atom(LocalLongPtr a, long b)"}) {
            assertTrue(src.contains("public static " + sig), sig);
        }
        assertFalse(src.contains("ConstantIntPtr a"), "atomics are not allowed in constant");
        assertFalse(src.contains("Int2 atom("), "atomics have no vector overloads");
        assertTrue(src.contains("int old = a.get(0); a.set(0, old + b); return old;"), "block inserted as is");
        assertFalse(src.contains("return {"), src);
    }

    @Test
    void voidBuiltinWithBlockBody() {
        String src = ApiGenerator.generate(BuiltinsDef.parse(HEAD + "9 sync effect,sync void ()\ndoc Барьер.\n"
                + "jvm = { throw new UnsupportedOperationException(\"x\"); }\n")).files().get("com/sixik/gpu/api/Gpu.java");
        assertTrue(src.contains("public static void sync() {"), src);
        assertTrue(src.contains("throw new UnsupportedOperationException(\"x\");"), src);
    }

    @Test
    void unsignedTypesHaveNoJavaRepresentation() {
        var out = ApiGenerator.generate(BuiltinsDef.parse("version 1\n1 u pure u32 (u32)\ndoc d\n"));
        assertEquals(List.of("u"), out.skipped());
    }

    @Test
    void missingOrExtraJvmImplementationIsAnError() {
        var missing = assertThrows(IllegalArgumentException.class, () -> gpu(generic("jvm f32 = a\n")));
        assertTrue(missing.getMessage().contains("f64"), missing.getMessage());
        var outside = assertThrows(IllegalArgumentException.class,
                () -> gpu(generic("jvm f32,f64,i32 = a\n")));
        assertTrue(outside.getMessage().contains("i32"), outside.getMessage());
        var none = assertThrows(IllegalArgumentException.class,
                () -> gpu(HEAD + "1 ng pure i32 ()\ndoc d\n"));
        assertTrue(none.getMessage().contains("jvm"), none.getMessage());
        var typedForPlain = assertThrows(IllegalArgumentException.class,
                () -> gpu(HEAD + "1 ng pure i32 ()\ndoc d\njvm f32 = 1\n"));
        assertTrue(typedForPlain.getMessage().contains("without types"), typedForPlain.getMessage());
    }

    @Test
    void genericWithoutBindingIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> gpu(HEAD + "1 g pure T (i32)\ndoc d\njvm f32 = 1\n"));
        assertThrows(IllegalArgumentException.class, () -> gpu(HEAD + "1 g pure T (T:genx)\ndoc d\njvm f32 = 1\n"));
    }

    @Test
    void vectorTypeSource() {
        var files = ApiGenerator.generate(BuiltinsDef.parse(HEAD + "1 a pure i32 ()\ndoc d\njvm = 1\n")).files();
        String f2 = files.get("com/sixik/gpu/api/types/Float2.java");
        assertTrue(f2.contains("public final class Float2 {"));
        assertTrue(f2.contains("public final float x;"));
        assertTrue(f2.contains("public Float2(float x, float y)"));
        assertTrue(f2.contains("Float.floatToIntBits(x) == Float.floatToIntBits(v.x)"));
        assertTrue(files.containsKey("com/sixik/gpu/api/types/Double4.java"));
        assertFalse(files.containsKey("com/sixik/gpu/api/types/Float3.java"), "lanes are taken from the vectors directive");
        assertTrue(files.containsKey("com/sixik/gpu/api/types/FloatPtr.java"));
        assertFalse(files.containsKey("com/sixik/gpu/api/types/DoublePtr.java"), "pointers only f32");
        assertEquals("s0", ApiGenerator.laneName(8, 0));
        assertEquals("sF", ApiGenerator.laneName(16, 15));
        assertEquals("w", ApiGenerator.laneName(4, 3));
        assertEquals("Long8", ApiGenerator.vectorName("i64", 8));
    }

    @Test
    void spacePointerSource() {
        var files = ApiGenerator.generate(BuiltinsDef.parse(HEAD + "1 a pure i32 ()\ndoc d\njvm = 1\n")).files();
        String g = files.get("com/sixik/gpu/api/types/GlobalFloatPtr.java");
        assertTrue(g.contains("public final class GlobalFloatPtr {"), g);
        assertTrue(g.contains("public static GlobalFloatPtr of(float[] array, int offset)"), g);
        assertTrue(g.contains("public float get(int i)"), g);
        assertTrue(g.contains("public void set(int i, float value)"), g);
        assertTrue(g.contains("public GlobalFloatPtr plus(int n)"), g);
        String c = files.get("com/sixik/gpu/api/types/ConstantFloatPtr.java");
        assertTrue(c.contains("public float get(int i)"), c);
        assertFalse(c.contains(" set("), "cannot write to constant: there is no write method");
        assertTrue(files.containsKey("com/sixik/gpu/api/types/LocalFloatPtr.java"));
        assertFalse(files.containsKey("com/sixik/gpu/api/types/GlobalDoublePtr.java"), "pointers only f32");
    }

    @Test
    void generationIsDeterministic() {
        String text = generic("jvm f32 = a\njvm f64 = a\n");
        assertEquals(ApiGenerator.generate(BuiltinsDef.parse(text)).files(),
                ApiGenerator.generate(BuiltinsDef.parse(text)).files());
    }

    // ---- реальная таблица --------------------------------------------------------------------------

    @Test
    void realTableGeneratesFullApi() throws IOException {
        var def = BuiltinsDef.parse(Files.readString(Path.of(System.getProperty("builtins.def")), StandardCharsets.UTF_8));
        var out = ApiGenerator.generate(def);
        assertEquals(List.of(), out.skipped(), "since M10 every builtin has a Java representation");
        // Gpu + 4 скаляра x 5 размеров векторов + 4 класса-ссылки + 4 скаляра x 3 пространства указателей
        assertEquals(1 + 20 + 4 + 12, out.files().size());
        assertEquals(24, def.builtins().size());
        for (var e : out.files().entrySet()) {
            assertTrue(e.getValue().startsWith("// СГЕНЕРИРОВАНО"), e.getKey());
            assertFalse(e.getValue().contains("\r"), "only \\n: output does not depend on the OS");
        }
    }
}
