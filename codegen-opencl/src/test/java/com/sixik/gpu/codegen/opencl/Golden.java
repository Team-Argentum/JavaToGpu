package com.sixik.gpu.codegen.opencl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Сравнение с golden-файлами OpenCL C. Файлы лежат в {@code src/test/resources/golden/<target>/<имя>.cl};
 * при запуске с {@code -PgoldenUpdate} файлы перезаписываются (и это нужно просмотреть глазами, а не принять
 * вслепую). Окончания строк - {@code \n}, кодировка UTF-8.
 */
final class Golden {

    private static final Path DIR = Path.of(System.getProperty("golden.dir", "src/test/resources/golden"));
    private static final boolean UPDATE = Boolean.parseBoolean(System.getProperty("golden.update", "false"));

    private Golden() {
    }

    /**
     * Сравнивает (или обновляет) golden-файл.
     *
     * @param target цель (подкаталог)
     * @param name   имя файла без каталога
     * @param actual сгенерированный текст
     */
    static void check(Target target, String name, String actual) {
        Path file = DIR.resolve(target.id()).resolve(name);
        try {
            if (UPDATE) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, actual, StandardCharsets.UTF_8);
                return;
            }
            if (!Files.exists(file)) {
                fail("нет golden-файла " + file + " (создать: -PgoldenUpdate)");
            }
            assertEquals(Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n"), actual,
                    "расхождение с golden " + file);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
