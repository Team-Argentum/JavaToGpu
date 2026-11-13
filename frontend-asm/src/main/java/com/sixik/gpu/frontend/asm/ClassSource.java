package com.sixik.gpu.frontend.asm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Доступ к class-файлам по имени (REWORK_ARCHITECTURE.md, 17.3): узкий интерфейс, чтобы ядро фронтенда было чистой
 * функцией и тестировалось без файловой системы. Каталоги и jar сборки подключает файловая обёртка (шаг A6).
 */
@FunctionalInterface
public interface ClassSource {

    /**
     * @param internalName внутреннее имя класса ({@code pkg/Outer$Inner})
     * @return содержимое class-файла либо {@code null}, если класса нет
     */
    byte[] bytes(String internalName);

    /**
     * Классы, видимые загрузчику (ресурсы {@code pkg/Name.class}).
     *
     * @param loader загрузчик классов
     * @return источник
     */
    static ClassSource of(ClassLoader loader) {
        return name -> {
            try (InputStream in = loader.getResourceAsStream(name + ".class")) {
                return in == null ? null : in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    /**
     * Классы из памяти (тесты, class-файлы, собранные {@code ClassWriter}).
     *
     * @param classes внутреннее имя → содержимое
     * @return источник
     */
    static ClassSource of(Map<String, byte[]> classes) {
        return classes::get;
    }
}
