package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.frontend.common.KernelResources;
import com.sixik.gpu.weaver.Weaver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Шаг A6 (17.3): файловая обёртка {@link AsmFrontend#compileTree} - ресурсы в каталоге классов те же, что пишет
 * процессор {@code frontend-java}; device-функция из jar; повторный проход по сплетённым классам; удаление
 * устаревшего {@code .gir}; позиция диагностик {@code pkg/Имя.java:строка}.
 */
class CompileTreeTest {

    private static final String LIB = """
            package lib;

            import com.sixik.gpu.api.annotations.DeviceFunction;

            public final class MathLib {
                @DeviceFunction
                public static float twice(float x) { return x * 2f; }
            }
            """;

    private static final String KERNELS = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.*;

            public final class Kernels {
                @Kernel
                public static void scale(@Global float[] a, float k) {
                    int g = Gpu.getGlobalId(0);
                    a[g] = lib.MathLib.twice(a[g]) * k;
                }

                @Kernel
                public static void fill(@Global int[] a, int v) {
                    a[Gpu.getGlobalId(0)] = v;
                }
            }
            """;

    private static final String MORE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.*;

            public final class More {
                @Kernel
                public static void inc(@Global int[] a) {
                    a[Gpu.getGlobalId(0)]++;
                }
            }
            """;

    private static List<Path> testClasspath() {
        List<Path> out = new ArrayList<>();
        for (String p : System.getProperty("gpu.test.classpath").split(File.pathSeparator)) {
            out.add(Path.of(p));
        }
        return out;
    }

    /** Классы {@code demo/**} - в каталог, {@code lib/**} - в jar (байткод без исходников). */
    private static Path layout(JavacHarness.Outcome j, Path classes, Path jar) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, byte[]> e : j.classes().entrySet()) {
                if (e.getKey().startsWith("lib/")) {
                    out.putNextEntry(new ZipEntry(e.getKey() + ".class"));
                    out.write(e.getValue());
                    out.closeEntry();
                } else {
                    write(classes.resolve(e.getKey() + ".class"), e.getValue());
                }
            }
        }
        return jar;
    }

    private static void write(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream os = Files.newOutputStream(file)) {
            os.write(bytes);
        }
    }

    private static Set<String> irFiles(Path classes) throws IOException {
        Path dir = classes.resolve(KernelResources.IR_DIR);
        if (!Files.isDirectory(dir)) {
            return Set.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private static Set<String> index(Path classes) throws IOException {
        return KernelResources.parseIndex(Files.readString(classes.resolve(KernelResources.INDEX), StandardCharsets.UTF_8))
                .stream().map(KernelResources.IndexEntry::toString).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> javaIndex(JavacHarness.Outcome j) {
        return KernelResources.parseIndex(new String(j.resources().get(KernelResources.INDEX), StandardCharsets.UTF_8))
                .stream().map(KernelResources.IndexEntry::toString).collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void writesTheSameResourcesAsTheProcessorAndSurvivesWeaving(@TempDir Path dir) throws IOException {
        JavacHarness.Outcome java = JavacHarness.compile(true, null, LIB, KERNELS, MORE);
        assertTrue(java.success(), java.log());
        Path classes = dir.resolve("classes");
        List<Path> cp = new ArrayList<>(testClasspath());
        cp.add(0, layout(java, classes, dir.resolve("mathlib.jar")));

        AsmFrontend.Report first = AsmFrontend.compileTree(classes, cp, AsmFrontend.Options.DEFAULT);
        assertTrue(first.ok(), first.messages().toString());
        assertEquals(3, first.kernels());
        assertEquals(javaIndex(java), index(classes), "kernels.idx: the same entries and kernelId as frontend-java");
        Set<String> expectedIr = new TreeSet<>();
        for (String path : java.resources().keySet()) {
            if (path.startsWith(KernelResources.IR_DIR)) {
                expectedIr.add(path.substring(KernelResources.IR_DIR.length()));
            }
        }
        assertEquals(expectedIr, irFiles(classes));

        // weaver подменяет тела; второй проход берёт их из <имя>$jvm и даёт тот же индекс
        Weaver.Report woven = Weaver.weaveTree(classes);
        assertEquals(3, woven.kernels(), woven.problems().toString());
        AsmFrontend.Report again = AsmFrontend.compileTree(classes, cp, AsmFrontend.Options.DEFAULT);
        assertTrue(again.ok(), again.messages().toString());
        assertEquals(javaIndex(java), index(classes));
        assertEquals(0, again.removed());

        // инкрементальная сборка: перекомпилирован один класс, ядро fill исчезло - его .gir удаляется
        JavacHarness.Outcome edited = JavacHarness.compile(false, null, LIB,
                KERNELS.replace("@Kernel\n    public static void fill", "public static void fill"), MORE);
        assertTrue(edited.success(), edited.log());
        write(classes.resolve("demo/Kernels.class"), edited.classes().get("demo/Kernels"));
        AsmFrontend.Report third = AsmFrontend.compileTree(classes, cp, AsmFrontend.Options.DEFAULT);
        assertTrue(third.ok(), third.messages().toString());
        assertEquals(2, third.kernels());
        assertEquals(1, third.removed());
        assertEquals(2, irFiles(classes).size());
        assertFalse(index(classes).stream().anyMatch(e -> e.contains("fill")));
    }

    @Test
    void diagnosticsCarrySourcePositionsAndNoKernelsMeanNoIndex(@TempDir Path dir) throws IOException {
        String bad = """
                package demo;

                import com.sixik.gpu.api.annotations.*;

                public final class Bad {
                    @Kernel
                    public static void k(@Global float[] a) {
                        String s = "x";
                    }
                }
                """;
        JavacHarness.Outcome j = JavacHarness.compile(false, null, bad);
        assertTrue(j.success(), j.log());
        Path classes = dir.resolve("classes");
        write(classes.resolve("demo/Bad.class"), j.classes().get("demo/Bad"));
        write(classes.resolve(KernelResources.INDEX), "stale".getBytes(StandardCharsets.UTF_8));
        write(classes.resolve(KernelResources.irPath("deadbeef")), new byte[]{1});

        AsmFrontend.Report r = AsmFrontend.compileTree(classes, testClasspath(), AsmFrontend.Options.DEFAULT);
        assertFalse(r.ok());
        assertTrue(r.messages().stream().anyMatch(m -> m.startsWith("demo/Bad.java:8: GPU0106") && m.endsWith("(Bad.k)")),
                r.messages().toString());
        assertEquals(0, r.kernels());
        assertFalse(Files.exists(classes.resolve(KernelResources.INDEX)), "no kernels - no index");
        assertEquals(1, r.removed());
    }

    @Test
    void missingClassesDirectoryIsAnEmptyBuild(@TempDir Path dir) {
        AsmFrontend.Report r = AsmFrontend.compileTree(dir.resolve("absent"), List.of(), AsmFrontend.Options.DEFAULT);
        assertTrue(r.ok());
        assertEquals(0, r.kernels());
    }
}
