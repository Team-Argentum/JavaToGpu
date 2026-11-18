package com.sixik.gpu.app.buildtools;

import com.sixik.gpu.frontend.asm.AsmFrontend;
import com.sixik.gpu.weaver.Weaver;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Шаг сборки примера {@code asmExample}: то же, что делает плагин при {@code gpu { frontend = 'asm' }}
 * (REWORK_ARCHITECTURE.md, 17.3). Сначала {@code frontend-asm} строит IR из готовых class-файлов, потом weaver
 * подменяет тела ядер.
 */
public final class AsmBuild {

    /** Запрещает создание экземпляров утилиты сборки. */
    private AsmBuild() {
    }

    /**
     * Строит ресурсы IR и подменяет ядра в каталоге результатов javac.
     *
     * @param args каталог class-файлов и classpath компиляции (пути через {@link File#pathSeparator}; может быть пустым)
     * @throws IllegalArgumentException если передано неверное количество аргументов
     * @throws IllegalStateException если фронтенд нашёл ошибки в GPU-коде или weaver - неподдерживаемое ядро
     */
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Expected a classes directory and an optional classpath");
        }
        Path classes = Path.of(args[0]);
        List<Path> classpath = new ArrayList<>();
        if (args.length == 2 && !args[1].isEmpty()) {
            for (String p : args[1].split(File.pathSeparator)) {
                classpath.add(Path.of(p));
            }
        }
        AsmFrontend.Report ir = AsmFrontend.compileTree(classes, classpath, AsmFrontend.Options.DEFAULT);
        ir.messages().forEach(System.out::println);
        if (!ir.ok()) {
            throw new IllegalStateException("frontend-asm found errors in GPU code, see above");
        }
        System.out.printf("JavaToGpu (asm): IR of %d kernels, stale IR files removed: %d%n", ir.kernels(), ir.removed());
        Weaver.Report report = Weaver.weaveTree(classes);
        if (!report.problems().isEmpty()) {
            throw new IllegalStateException(String.join(System.lineSeparator(), report.problems()));
        }
        System.out.printf("JavaToGpu (asm): woven %d kernels in %d classes%n", report.kernels(), report.classes());
    }
}
