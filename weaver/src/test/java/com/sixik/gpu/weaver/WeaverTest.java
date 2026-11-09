package com.sixik.gpu.weaver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Байткод-тесты weaver'а на настоящих class-файлах: исходник компилируется javac c процессором {@code frontend-java},
 * затем подменяется и разбирается ASM'ом. Проверяется, что оригинал сохранён как {@code <имя>$jvm}, а делегат
 * вызывает хост-фасад с правильными константами и упаковкой аргументов.
 */
class WeaverTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.DeviceFunction;
            import com.sixik.gpu.api.annotations.Global;
            import com.sixik.gpu.api.annotations.Kernel;

            public class Kernels {

                @DeviceFunction
                static float twice(float x) {
                    return x * 2f;
                }

                @Kernel
                public static void scale(@Global float[] in, @Global float[] out, float k, int n, long big, double d) {
                    int g = Gpu.getGlobalId(0);
                    if (g < n) {
                        out[g] = twice(in[g]) * k + (float) (big + d);
                    }
                }

                @Kernel
                public static void fill(@Global int[] out) {
                    int g = Gpu.getGlobalId(0);
                    out[g] = g;
                }

                public static int plain(int a) {
                    return a + 1;
                }
            }
            """;

    private static byte[] read(Path file) throws IOException {
        return Files.readAllBytes(file);
    }

    private static ClassNode parse(byte[] bytes) {
        ClassNode n = new ClassNode();
        new ClassReader(bytes).accept(n, 0);
        return n;
    }

    private static MethodNode method(ClassNode c, String name) {
        return c.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElse(null);
    }

    private static Path compiled(Path dir) {
        KernelCompiler.Compiled r = KernelCompiler.compile(dir, SOURCE);
        assertTrue(r.success(), r.diagnostics());
        return dir;
    }

    // ---- KernelIndex ----------------------------------------------------------------------------

    @Test
    void indexParsesAndLooksUp() {
        KernelIndex idx = KernelIndex.parse("a.B\tf\t([F)V\tid1\r\na.B\tg\t()V\tid2\n\na.C$D\th\t(I)V\tid3\n");
        assertEquals(3, idx.size());
        assertEquals("id1", idx.kernelId("a.B", "f", "([F)V"));
        assertEquals("id2", idx.kernelId("a.B", "g", "()V"));
        assertEquals("id3", idx.kernelId("a.C$D", "h", "(I)V"));
        assertNull(idx.kernelId("a.B", "f", "()V"), "descriptor distinguishes overloads");
        assertNull(idx.kernelId("a.X", "f", "([F)V"));
        List<String> classes = new ArrayList<>();
        idx.classes().forEach(classes::add);
        assertEquals(2, classes.size());
    }

    @Test
    void emptyIndexIsValid() {
        assertEquals(0, KernelIndex.parse("").size());
        assertEquals(0, KernelIndex.parse("\n\n").size());
    }

    @Test
    void malformedIndexLineIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> KernelIndex.parse("a.B\tf\t()V\n"));
        assertThrows(IllegalArgumentException.class, () -> KernelIndex.parse("a.B\tf\t()V\tid\textra\n"));
        assertThrows(IllegalArgumentException.class, () -> KernelIndex.parse("a.B\t\t()V\tid\n"));
    }

    // ---- подмена --------------------------------------------------------------------------------

    @Test
    void originalBodyMovesToJvmMethodAndDelegateCallsTheFacade(@TempDir Path dir) throws IOException {
        compiled(dir);
        KernelIndex idx = KernelIndex.parse(Files.readString(dir.resolve(KernelIndex.PATH), StandardCharsets.UTF_8));
        byte[] original = read(dir.resolve("demo/Kernels.class"));
        ClassNode before = parse(original);
        int originalInstructions = method(before, "scale").instructions.size();

        Weaver.Outcome o = Weaver.weave(original, idx);
        assertEquals(2, o.woven());
        assertEquals(0, o.skipped());
        assertTrue(o.problems().isEmpty(), o.problems().toString());
        ClassNode after = parse(o.bytes());

        MethodNode jvm = method(after, "scale$jvm");
        assertNotNull(jvm, "original preserved");
        assertEquals(method(before, "scale").desc, jvm.desc);
        assertEquals(method(before, "scale").access, jvm.access);
        assertEquals(originalInstructions, jvm.instructions.size(), "body moved unchanged");
        assertTrue(jvm.invisibleAnnotations == null || jvm.invisibleAnnotations.isEmpty(), "$jvm has no @Kernel");
        assertNotNull(method(after, "fill$jvm"));

        MethodNode delegate = method(after, "scale");
        assertTrue(delegate.invisibleAnnotations.stream().anyMatch(a -> Weaver.KERNEL_ANNOTATION.equals(a.desc)),
                "@Kernel stays on the delegate");
        assertNotNull(delegate.invisibleParameterAnnotations, "parameter annotations stay on the delegate");
        MethodInsnNode call = null;
        LdcInsnNode ownerConst = null;
        List<String> strings = new ArrayList<>();
        for (AbstractInsnNode in : delegate.instructions) {
            if (in instanceof MethodInsnNode m && m.owner.equals(Weaver.FACADE)) {
                call = m;
            }
            if (in instanceof LdcInsnNode l) {
                if (l.cst instanceof Type) {
                    ownerConst = l;
                } else {
                    strings.add((String) l.cst);
                }
            }
        }
        assertNotNull(call);
        assertEquals(Weaver.FACADE_METHOD, call.name);
        assertEquals(Weaver.FACADE_DESCRIPTOR, call.desc);
        assertEquals("Ldemo/Kernels;", ((Type) ownerConst.cst).getDescriptor());
        assertEquals("scale", strings.get(0));
        assertEquals(idx.kernelId("demo.Kernels", "scale", delegate.desc), strings.get(1));
        long boxings = 0;
        for (AbstractInsnNode in : delegate.instructions) {
            if (in instanceof MethodInsnNode m && m.name.equals("valueOf")) {
                boxings++;
            }
        }
        assertEquals(4, boxings, "float, int, long and double are boxed; arrays are passed as is");
        assertEquals(Opcodes.RETURN, delegate.instructions.getLast().getOpcode());
        assertEquals(0, delegate.tryCatchBlocks.size());
        assertTrue(delegate.localVariables == null || delegate.localVariables.isEmpty(), "debug locals of the original body are removed");
    }

    @Test
    void plainAndDeviceMethodsAreUntouched(@TempDir Path dir) throws IOException {
        compiled(dir);
        KernelIndex idx = KernelIndex.parse(Files.readString(dir.resolve(KernelIndex.PATH)));
        ClassNode after = parse(Weaver.weave(read(dir.resolve("demo/Kernels.class")), idx).bytes());
        assertNull(method(after, "plain$jvm"));
        assertNull(method(after, "twice$jvm"));
        assertTrue(method(after, "twice").instructions.size() > 3, "the device function body is in place");
        assertTrue(method(after, "plain").instructions.size() > 2);
    }

    @Test
    void weavingIsIdempotent(@TempDir Path dir) throws IOException {
        compiled(dir);
        KernelIndex idx = KernelIndex.parse(Files.readString(dir.resolve(KernelIndex.PATH)));
        Weaver.Outcome first = Weaver.weave(read(dir.resolve("demo/Kernels.class")), idx);
        Weaver.Outcome second = Weaver.weave(first.bytes(), idx);
        assertEquals(0, second.woven());
        assertEquals(2, second.skipped());
        assertSame(first.bytes(), second.bytes(), "nothing to change - the same bytes");
    }

    @Test
    void classWithoutKernelsIsReturnedAsIs(@TempDir Path dir) throws IOException {
        KernelCompiler.Compiled r = KernelCompiler.compile(dir, "package demo; public class Plain { public static int f(int a) { return a; } }");
        assertTrue(r.success(), r.diagnostics());
        byte[] bytes = read(dir.resolve("demo/Plain.class"));
        Weaver.Outcome o = Weaver.weave(bytes, KernelIndex.parse(""));
        assertSame(bytes, o.bytes());
        assertEquals(0, o.woven());
    }

    @Test
    void kernelMissingFromTheIndexIsReported(@TempDir Path dir) throws IOException {
        compiled(dir);
        Weaver.Outcome o = Weaver.weave(read(dir.resolve("demo/Kernels.class")), KernelIndex.parse(""));
        assertEquals(0, o.woven());
        assertEquals(2, o.problems().size());
        assertTrue(o.problems().get(0).contains("not in kernels.idx"), o.problems().toString());
    }

    @Test
    void weavedClassLoadsAndKeepsBothMethods(@TempDir Path dir) throws Exception {
        compiled(dir);
        Weaver.Report report = Weaver.weaveTree(dir);
        assertEquals(1, report.classes());
        assertEquals(2, report.kernels());
        assertTrue(report.problems().isEmpty(), report.problems().toString());
        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, WeaverTest.class.getClassLoader())) {
            Class<?> c = loader.loadClass("demo.Kernels");
            Method fillJvm = c.getDeclaredMethod("fill$jvm", int[].class);
            assertNotNull(c.getDeclaredMethod("fill", int[].class));
            assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, fillJvm.getModifiers() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC));
            assertEquals(2, c.getDeclaredMethod("plain", int.class).invoke(null, 1));
        }
    }

    // ---- дерево class-файлов --------------------------------------------------------------------

    @Test
    void weaveTreeWithoutIndexDoesNothing(@TempDir Path dir) {
        Weaver.Report r = Weaver.weaveTree(dir);
        assertEquals(0, r.classes() + r.kernels() + r.skipped());
        assertTrue(r.problems().isEmpty());
    }

    @Test
    void weaveTreeReportsMissingClassFile(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("META-INF/gpu"));
        Files.writeString(dir.resolve(KernelIndex.PATH), "demo.Ghost\tf\t()V\tid\n");
        Weaver.Report r = Weaver.weaveTree(dir);
        assertEquals(1, r.problems().size());
        assertTrue(r.problems().get(0).contains("demo.Ghost"));
    }

    @Test
    void weaveTreeIsRepeatable(@TempDir Path dir) throws IOException {
        compiled(dir);
        Weaver.Report first = Weaver.weaveTree(dir);
        byte[] afterFirst = read(dir.resolve("demo/Kernels.class"));
        Weaver.Report second = Weaver.weaveTree(dir);
        assertEquals(2, first.kernels());
        assertEquals(0, second.kernels());
        assertEquals(2, second.skipped());
        assertEquals(java.util.Arrays.hashCode(afterFirst), java.util.Arrays.hashCode(read(dir.resolve("demo/Kernels.class"))));
        try (Stream<Path> files = Files.walk(dir)) {
            assertTrue(files.noneMatch(p -> p.toString().endsWith(".weave")), "temporary files removed");
        }
    }

    // ---- нельзя подменить -----------------------------------------------------------------------

    @Test
    void instanceKernelIsReportedNotWoven(@TempDir Path dir) throws IOException {
        // процессор отвергает такой исходник, поэтому индекс составляем сами, а класс компилируем без процессора
        KernelCompiler.Compiled r = KernelCompiler.compileWithoutProcessing(dir, """
                package demo;
                import com.sixik.gpu.api.annotations.Kernel;
                public class Bad {
                    @Kernel public void inst(int a) { }
                    @Kernel public static int returns(int a) { return a; }
                    @Kernel public static void notIndexed(int a) { }
                }
                """);
        assertTrue(r.success(), r.diagnostics());
        KernelIndex idx = KernelIndex.parse("demo.Bad\tinst\t(I)V\ti1\ndemo.Bad\treturns\t(I)I\ti2\n");
        Weaver.Outcome o = Weaver.weave(read(dir.resolve("demo/Bad.class")), idx);
        assertEquals(0, o.woven());
        assertEquals(3, o.problems().size(), o.problems().toString());
        assertTrue(o.problems().get(0).contains("static") || o.problems().get(1).contains("static"));
        assertTrue(o.problems().stream().anyMatch(p -> p.contains("void")));
        assertTrue(o.problems().stream().anyMatch(p -> p.contains("not in kernels.idx")));
    }
}
