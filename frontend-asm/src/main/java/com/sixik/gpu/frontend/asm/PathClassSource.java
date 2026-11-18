package com.sixik.gpu.frontend.asm;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Class-файлы из каталогов и jar сборки (17.3): каталог классов модуля и classpath компиляции. Порядок поиска - порядок
 * корней; классы JDK ({@code java/}, {@code javax/}) - из системного загрузчика, если их нет на classpath.
 */
final class PathClassSource implements ClassSource, Closeable {

    private final List<Object> roots = new ArrayList<>();

    /**
     * @param roots каталоги и jar; несуществующие пути пропускаются (Gradle передаёт и ещё не созданные каталоги)
     * @throws UncheckedIOException если jar не открывается
     */
    PathClassSource(List<Path> roots) {
        try {
            for (Path p : roots) {
                if (Files.isDirectory(p)) {
                    this.roots.add(p);
                } else if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar")) {
                    this.roots.add(new ZipFile(p.toFile()));
                }
            }
        } catch (IOException e) {
            close();
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] bytes(String internalName) {
        String file = internalName + ".class";
        try {
            for (Object r : roots) {
                if (r instanceof Path dir) {
                    Path f = dir.resolve(file);
                    if (Files.isRegularFile(f)) {
                        return Files.readAllBytes(f);
                    }
                } else {
                    ZipFile zip = (ZipFile) r;
                    ZipEntry e = zip.getEntry(file);
                    if (e != null) {
                        try (InputStream in = zip.getInputStream(e)) {
                            return in.readAllBytes();
                        }
                    }
                }
            }
            if (internalName.startsWith("java/") || internalName.startsWith("javax/")) {
                try (InputStream in = ClassLoader.getSystemResourceAsStream(file)) {
                    return in == null ? null : in.readAllBytes();
                }
            }
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        for (Object r : roots) {
            if (r instanceof ZipFile zip) {
                try {
                    zip.close();
                } catch (IOException ignored) {
                    // только чтение: ошибка закрытия ничего не теряет
                }
            }
        }
    }
}
