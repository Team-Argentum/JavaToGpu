package com.sixik.gpu.apigen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Точка входа задачи Gradle {@code generateApi}: читает {@code builtins.def} и пишет сгенерированные исходники
 * в каталог, который целиком принадлежит генератору (перед записью он очищается, чтобы не оставалось файлов
 * от удалённых типов).
 *
 * <p>Аргументы: {@code <путь к builtins.def> <каталог вывода>}.</p>
 */
public final class GenerateApi {

    private GenerateApi() {
    }

    /**
     * @param args путь к {@code builtins.def} и каталог вывода
     * @throws IOException при ошибке чтения или записи
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: GenerateApi <builtins.def> <output directory>");
        }
        Path def = Path.of(args[0]);
        Path out = Path.of(args[1]);
        ApiGenerator.Output result = ApiGenerator.generate(BuiltinsDef.parse(Files.readString(def, StandardCharsets.UTF_8)));

        if (Files.exists(out)) {
            try (Stream<Path> walk = Files.walk(out)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
        for (Map.Entry<String, String> e : result.files().entrySet()) {
            Path target = out.resolve(e.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, e.getValue(), StandardCharsets.UTF_8);
        }
        System.out.println("generateApi: " + result.files().size() + " files, skipped builtins: " + result.skipped());
    }
}
