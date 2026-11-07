package com.sixik.gpu.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Сравнение с golden-файлами из {@code src/test/resources/golden}.
 *
 * <p>При расхождении фактический вывод записывается в {@code build/golden-actual/<имя>}: после осознанной
 * проверки его копируют на место эталона. Эталоны обновляются только вручную - это защита от случайных
 * изменений формата (REWORK_ARCHITECTURE.md, разд. 13).</p>
 */
final class Golden {

    private Golden() {
    }

    static void assertMatches(String name, String actual) {
        String expected;
        try (InputStream in = Golden.class.getResourceAsStream("/golden/" + name)) {
            if (in == null) {
                writeActual(name, actual);
                fail("нет golden-файла " + name + "; фактический вывод записан в build/golden-actual/" + name);
                return;
            }
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        if (!expected.equals(actual)) {
            writeActual(name, actual);
            assertEquals(expected, actual, "golden " + name + " не совпал; фактический вывод - build/golden-actual/" + name);
        }
    }

    private static void writeActual(String name, String actual) {
        try {
            Path dir = Path.of("build", "golden-actual");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name), actual, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
