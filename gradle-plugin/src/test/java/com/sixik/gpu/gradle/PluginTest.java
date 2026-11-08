package com.sixik.gpu.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TestKit-проверка плагина на тестовом проекте: настоящая вложенная сборка Gradle. Проект подключает только плагин и
 * flatDir с jar'ами библиотек; зависимости, annotation processor, weave-шаг и аргументы JVM ставит плагин. Ядро
 * вызывается обычным вызовом Java-метода в задаче {@code runDemo}: на машине с OpenCL оно идёт на устройство, без него -
 * на JVM-fallback; результат одинаков.
 */
class PluginTest {

    private static final String KERNELS = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.Global;
            import com.sixik.gpu.api.annotations.Kernel;

            public class Kernels {

                @Kernel
                public static void scale(@Global float[] in, @Global float[] out, float k) {
                    int g = Gpu.getGlobalId(0);
                    out[g] = in[g] * k + 1.0f;
                }

                public static int plain(int a) {
                    return a * 3;
                }
            }
            """;

    private static final String MAIN = """
            package demo;

            public class Main {
                public static void main(String[] args) {
                    float[] in = {1, 2, 3, 4};
                    float[] out = new float[4];
                    Kernels.scale(in, out, 2f);
                    StringBuilder sb = new StringBuilder("RESULT");
                    for (float v : out) {
                        sb.append(' ').append(v);
                    }
                    System.out.println(sb);
                    System.out.println("PLAIN " + Kernels.plain(5));
                    System.out.println("JVMARGS " + java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
                    System.out.println("DEVICE " + com.sixik.gpu.runtime.JavaToGpu.defaultContext().isPresent());
                }
            }
            """;

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static void project(Path dir, String gpuBlock) throws IOException {
        write(dir.resolve("settings.gradle"), "rootProject.name = 'demo'\n");
        write(dir.resolve("build.gradle"), """
                plugins {
                    id 'io.github.deussixik.javatogpu'
                }

                repositories {
                    flatDir { dirs 'libs' }
                }

                gpu {
                    version = '%s'
                    %s
                }

                tasks.register('runDemo', JavaExec) {
                    classpath = sourceSets.main.runtimeClasspath
                    mainClass = 'demo.Main'
                }
                """.formatted(System.getProperty("gpu.version"), gpuBlock));
        write(dir.resolve("src/main/java/demo/Kernels.java"), KERNELS);
        write(dir.resolve("src/main/java/demo/Main.java"), MAIN);
        Files.createDirectories(dir.resolve("libs"));
        for (String jar : System.getProperty("gpu.libs").split(File.pathSeparator)) {
            Path src = Path.of(jar);
            Files.copy(src, dir.resolve("libs").resolve(src.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static GradleRunner runner(Path dir, String... args) {
        return GradleRunner.create().withProjectDir(dir.toFile()).withPluginClasspath().withArguments(args)
                .forwardOutput();
    }

    private static String classText(Path dir, String name) throws IOException {
        return new String(Files.readAllBytes(dir.resolve("build/classes/java/main/demo/" + name + ".class")),
                StandardCharsets.ISO_8859_1);
    }

    @Test
    void pluginBuildsWeavesAndRunsAnOrdinaryCall(@TempDir Path dir) throws IOException {
        project(dir, "");
        BuildResult r = runner(dir, "runDemo", "--stacktrace").build();

        assertEquals(TaskOutcome.SUCCESS, r.task(":compileJava").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, r.task(":runDemo").getOutcome());
        assertTrue(r.getOutput().contains("RESULT 3.0 5.0 7.0 9.0"), r.getOutput());
        assertTrue(r.getOutput().contains("PLAIN 15"), "обычные методы не тронуты");
        // именно строка JVMARGS: при отсутствии флага JDK сам печатает предупреждение с упоминанием этого флага
        assertTrue(r.getOutput().lines().anyMatch(l -> l.startsWith("JVMARGS ")
                        && l.contains("--enable-native-access=ALL-UNNAMED")),
                "плагин выдал --enable-native-access задаче JavaExec: " + r.getOutput());

        String kernels = classText(dir, "Kernels");
        assertTrue(kernels.contains("scale$jvm"), "оригинал сохранён как scale$jvm");
        assertFalse(kernels.contains("plain$jvm"));
        assertTrue(Files.isRegularFile(dir.resolve("build/classes/java/main/META-INF/gpu/kernels.idx")));
        try (var irs = Files.list(dir.resolve("build/classes/java/main/META-INF/gpu/ir"))) {
            assertEquals(1, irs.count(), "IR-ресурс записан processor'ом");
        }
    }

    @Test
    void secondBuildIsUpToDateAndStillWoven(@TempDir Path dir) throws IOException {
        project(dir, "");
        runner(dir, "classes").build();
        BuildResult second = runner(dir, "runDemo").build();
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":compileJava").getOutcome());
        assertTrue(second.getOutput().contains("RESULT 3.0 5.0 7.0 9.0"), second.getOutput());
    }

    @Test
    void recompilationWeavesAgain(@TempDir Path dir) throws IOException {
        project(dir, "");
        runner(dir, "classes").build();
        write(dir.resolve("src/main/java/demo/Kernels.java"), KERNELS.replace("* k + 1.0f", "* k + 10.0f"));
        BuildResult r = runner(dir, "runDemo").build();
        assertEquals(TaskOutcome.SUCCESS, r.task(":compileJava").getOutcome());
        assertTrue(r.getOutput().contains("RESULT 12.0 14.0 16.0 18.0"), r.getOutput());
        assertTrue(classText(dir, "Kernels").contains("scale$jvm"));
    }

    @Test
    void strictBarriersMakeDivergentBarrierACompileError(@TempDir Path dir) throws IOException {
        String divergent = """
                package demo;

                import com.sixik.gpu.api.Gpu;
                import com.sixik.gpu.api.annotations.*;

                public class Sync {
                    @Kernel
                    @WorkGroup(x = 4)
                    public static void bad(@Global int[] a) {
                        if (Gpu.getLocalId(0) == 0) {
                            Gpu.barrier();
                        }
                        a[0] = 1;
                    }
                }
                """;
        project(dir, "");
        write(dir.resolve("src/main/java/demo/Sync.java"), divergent);
        BuildResult lenient = runner(dir, "classes").build();
        assertTrue(lenient.getOutput().contains("GPU0130"), "по умолчанию - предупреждение: " + lenient.getOutput());

        project(dir, "strictBarriers = true");
        write(dir.resolve("src/main/java/demo/Sync.java"), divergent);
        BuildResult strict = runner(dir, "clean", "classes").buildAndFail();
        assertTrue(strict.getOutput().contains("GPU0131"), strict.getOutput());
    }

    @Test
    void weavingCanBeSwitchedOff(@TempDir Path dir) throws IOException {
        project(dir, "weave = false");
        runner(dir, "classes").build();
        assertFalse(classText(dir, "Kernels").contains("scale$jvm"), "weave выключен: тело ядра не тронуто");
    }

    @Test
    void autoDependenciesCanBeSwitchedOff(@TempDir Path dir) throws IOException {
        project(dir, "autoDependencies = false");
        BuildResult r = runner(dir, "compileJava").buildAndFail();
        assertTrue(r.getOutput().contains("package com.sixik.gpu.api") || r.getOutput().contains("не существует")
                || r.getOutput().contains("does not exist"), r.getOutput());
    }

    @Test
    void pluginAppliesTheJavaPluginAndRegistersTheExtension(@TempDir Path dir) throws IOException {
        project(dir, "");
        write(dir.resolve("build.gradle"), Files.readString(dir.resolve("build.gradle")) + """

                tasks.register('inspect') {
                    doLast {
                        println 'JAVA ' + plugins.hasPlugin('java')
                        println 'EXT ' + project.extensions.getByName('gpu').autoDependencies.get()
                        println 'DEPS ' + configurations.implementation.dependencies.collect { it.name }.sort()
                    }
                }
                """);
        BuildResult r = runner(dir, "inspect", "-q").build();
        assertTrue(r.getOutput().contains("JAVA true"), r.getOutput());
        assertTrue(r.getOutput().contains("EXT true"));
        assertTrue(r.getOutput().contains("[api, codegen-opencl, ir, runtime]"), r.getOutput());
    }
}
