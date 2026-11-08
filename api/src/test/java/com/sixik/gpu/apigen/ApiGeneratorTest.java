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
        assertEquals(4, b.ulp64(), "без ulp64 равен ulp");
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
        assertEquals("a", def.builtins().get(0).jvm().get("f64"), "ключи через запятую задают одно выражение");
    }

    @Test
    void buildinsAreSortedById() {
        var def = BuiltinsDef.parse(HEAD + "5 b pure i32 ()\ndoc d\njvm = 1\n2 a pure i32 ()\ndoc d\njvm = 2\n");
        assertEquals("a", def.builtins().get(0).name());
        assertEquals("b", def.builtins().get(1).name());
    }

    @Test
    void rejectsMalformedDefinitions() {
        assertTrue(parseError("1 a pure i32 ()\ndoc d\n").contains("version"), "нет version");
        assertTrue(parseError(HEAD + "bogus x\n").contains("bogus"), "неизвестная директива");
        assertTrue(parseError(HEAD + "1 a pure i32 ()\ndoc d\n1 b pure i32 ()\ndoc d\n").contains("повторный id"));
        assertTrue(parseError(HEAD + "1 a pure i32 ()\ndoc d\n2 a pure i32 ()\ndoc d\n").contains("повторное имя"));
        assertTrue(parseError(HEAD + "1 a weird i32 ()\ndoc d\n").contains("weird"), "неизвестный флаг");
        assertTrue(parseError(HEAD + "1 a pure i32\ndoc d\n").contains("сигнатура"), "нет скобок");
        assertTrue(parseError(HEAD + "1 a pure\n").contains("ожидалось"), "мало полей");
        assertTrue(parseError(HEAD + "1 a pure i32 ()\n").contains("нет doc"), "обязателен doc");
        assertTrue(parseError(HEAD + "1 a pure i32 ()\ndoc one\ndoc two\n").contains("повторный doc"));
        assertTrue(parseError(HEAD + "doc orphan\n").contains("до первой строки"), "doc до builtin");
        assertTrue(parseError(HEAD + "jvm = 1\n").contains("до первой строки"), "jvm до builtin");
        assertTrue(parseError(HEAD + "1 a pure i32 ()\ndoc d\njvm 1\n").contains("jvm"), "jvm без '='");
        assertTrue(parseError(HEAD + "1 a pure i32 ()\ndoc d\njvm =\n").contains("пустое"), "пустое выражение");
        assertTrue(parseError(HEAD + "1 a pure i32 ()\ndoc d\njvm = 1\njvm = 2\n").contains("повторная"));
        assertTrue(parseError(HEAD + "1 a pure i32 ()\ndoc d\njvm u8 = 1\n").contains("Java-представления"),
                "ключ jvm вне набора API");
        assertTrue(parseError("version 1\nvectors f32 : 5\n").contains("5"), "недопустимое число компонент");
        assertTrue(parseError("version 1\nvectors f32 2\n").contains("vectors"), "нет двоеточия");
        assertTrue(parseError("version 1\nvectors u32 : 2\n").contains("u32"), "скаляр без Java-представления");
        assertTrue(parseError("version x\n").contains("version"), "версия не число");
        assertTrue(parseError(HEAD + "x a pure i32 ()\n").contains("x"), "неизвестная директива вместо id");
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
        assertFalse(src.contains("Int2"), "vectors не включает i32");
        assertTrue(src.contains("Описание foo."), "doc попадает в Javadoc");
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
        assertEquals(1, src.split("public static ", -1).length - 1, "одна перегрузка");
    }

    @Test
    void classesSelectScalarSets() {
        String src = gpu("version 1\n1 gi pure T (T:geni)\ndoc d\njvm i32,i64 = a\n"
                + "2 gn pure T (T:genn, T)\ndoc d\njvm f32,f64,i32,i64 = a\n");
        assertTrue(src.contains("public static int gi(int a)"));
        assertTrue(src.contains("public static long gi(long a)"));
        assertFalse(src.contains("float gi("), "geni не включает float");
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
    void builtinWithPointerIsSkippedAndReported() {
        var out = ApiGenerator.generate(BuiltinsDef.parse(generic("jvm f32 = a\njvm f64 = a\n")
                + "8 atom effect,atomic T (ptr<T:geni>, T)\ndoc Атомик.\n"));
        assertEquals(List.of("atom"), out.skipped());
        String src = out.files().get("com/sixik/gpu/api/Gpu.java");
        assertFalse(src.contains(" atom("));
        assertTrue(src.contains("Пока без Java-представления"));
        assertTrue(src.contains("atom."), "в Javadoc класса перечислены пропущенные builtins");
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
        assertTrue(typedForPlain.getMessage().contains("без типов"), typedForPlain.getMessage());
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
        assertFalse(files.containsKey("com/sixik/gpu/api/types/Float3.java"), "lanes берутся из директивы vectors");
        assertTrue(files.containsKey("com/sixik/gpu/api/types/FloatPtr.java"));
        assertFalse(files.containsKey("com/sixik/gpu/api/types/DoublePtr.java"), "pointers только f32");
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
        assertFalse(c.contains(" set("), "в constant писать нельзя: метода записи нет");
        assertTrue(files.containsKey("com/sixik/gpu/api/types/LocalFloatPtr.java"));
        assertFalse(files.containsKey("com/sixik/gpu/api/types/GlobalDoublePtr.java"), "pointers только f32");
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
        assertEquals(List.of("atomicAdd"), out.skipped(), "до M10 Java-представления нет только у atomic-builtin");
        // Gpu + 4 скаляра x 5 размеров векторов + 4 класса-ссылки + 4 скаляра x 3 пространства указателей
        assertEquals(1 + 20 + 4 + 12, out.files().size());
        assertEquals(15, def.builtins().size());
        for (var e : out.files().entrySet()) {
            assertTrue(e.getValue().startsWith("// СГЕНЕРИРОВАНО"), e.getKey());
            assertFalse(e.getValue().contains("\r"), "только \\n: вывод не зависит от ОС");
        }
    }
}
