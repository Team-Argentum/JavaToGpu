package com.sixik.gpu.app.buildtools;

import com.sixik.gpu.weaver.Weaver;

import java.nio.file.Path;

/** Запускает подмену ядер в отдельной JVM с версией Java из toolchain приложения. */
public final class Weave {

    /** Запрещает создание экземпляров утилиты сборки. */
    private Weave() {
    }

    /**
     * Подменяет тела ядер в каталоге результатов javac.
     *
     * @param args единственный аргумент — путь к каталогу class-файлов
     * @throws IllegalArgumentException если передано неверное количество аргументов
     * @throws IllegalStateException если weaver обнаружил неподдерживаемое ядро
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one classes directory");
        }
        Weaver.Report report = Weaver.weaveTree(Path.of(args[0]));
        if (!report.problems().isEmpty()) {
            throw new IllegalStateException(String.join(System.lineSeparator(), report.problems()));
        }
        System.out.printf("JavaToGpu: woven %d kernels in %d classes%n", report.kernels(), report.classes());
    }
}
