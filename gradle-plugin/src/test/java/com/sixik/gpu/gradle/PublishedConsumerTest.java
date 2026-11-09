package com.sixik.gpu.gradle;

import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Проверяет опубликованные plugin marker, зависимости и Maven POM без TestKit plugin-classpath и composite build. */
class PublishedConsumerTest {

    /**
     * Копирует самостоятельный пример во внешний проект, включая только исходники и Gradle-скрипты.
     *
     * @param destination каталог проекта-потребителя
     * @param pomOnly true для проверки POM без Gradle module metadata
     * @throws IOException при ошибке копирования
     */
    private static void copyExample(Path destination, boolean pomOnly) throws IOException {
        Path source = Path.of(System.getProperty("gpu.standalone"));
        for (String relative : new String[]{"settings.gradle", "build.gradle", "src"}) {
            Path root = source.resolve(relative);
            try (var paths = Files.walk(root)) {
                for (Path file : paths.filter(Files::isRegularFile).toList()) {
                    Path target = destination.resolve(source.relativize(file));
                    Files.createDirectories(target.getParent());
                    if (pomOnly && file.getFileName().toString().endsWith(".gradle")) {
                        String script = Files.readString(file).replace("maven { url =",
                                "maven { metadataSources { mavenPom(); artifact() }; url =");
                        Files.writeString(target, script);
                    } else {
                        Files.copy(file, target);
                    }
                }
            }
        }
    }

    /**
     * Собирает и запускает приложение только из Maven-артефактов; повторная сборка сохраняет weaving.
     *
     * @param pomOnly проверять только POM или также Gradle metadata
     * @param directory временный независимый Gradle-проект
     * @throws IOException при ошибке чтения примера
     */
    @ParameterizedTest(name = "published consumer, POM only={0}")
    @ValueSource(booleans = {false, true})
    void publishedPlugin_buildsTestsAndRunsWithoutADevice(boolean pomOnly, @TempDir Path directory) throws IOException {
        copyExample(directory, pomOnly);
        String repository = "-PgpuRepository=" + Path.of(System.getProperty("gpu.repository")).toUri();
        var first = GradleRunner.create().withProjectDir(directory.toFile())
                .withArguments("test", "runFallback", "installDist", repository, "--refresh-dependencies", "--stacktrace")
                .forwardOutput().build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":test").getOutcome());
        assertTrue(first.getOutput().contains("RESULT [3.0, 5.0, 7.0, 9.0]"), first.getOutput());
        assertTrue(first.getOutput().contains("BACKEND JVM"), first.getOutput());
        Path launcher = directory.resolve("build/install/gpu-standalone/bin/gpu-standalone.bat");
        assertTrue(Files.readString(launcher).contains("--enable-native-access=ALL-UNNAMED"));

        var second = GradleRunner.create().withProjectDir(directory.toFile())
                .withArguments("test", "runFallback", repository, "--stacktrace").forwardOutput().build();
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":compileJava").getOutcome());
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":compileTestJava").getOutcome());
        assertTrue(second.getOutput().contains("BACKEND JVM"), second.getOutput());
    }
}
