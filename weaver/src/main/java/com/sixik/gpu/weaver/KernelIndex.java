package com.sixik.gpu.weaver;

import java.util.HashMap;
import java.util.Map;

/**
 * Индекс ядер, который фронтенд записывает рядом с class-файлами (REWORK_ARCHITECTURE.md, 5.15):
 * {@code META-INF/gpu/kernels.idx}, по строке на ядро, поля через табуляцию:
 * {@code <двоичное имя класса> <имя метода> <JVM-дескриптор> <kernelId>}.
 *
 * <p>Weaver не зависит от {@code ir} и не умеет считать хэш, поэтому {@code kernelId} берёт отсюда. Разбор терпим к
 * пустым строкам и окончаниям {@code \r\n}; строка, в которой не четыре поля, - ошибка формата (молча пропустить
 * ядро значило бы оставить его без подмены).</p>
 */
public final class KernelIndex {

    /** Путь индекса относительно корня class-файлов. */
    public static final String PATH = "META-INF/gpu/kernels.idx";

    private final Map<String, Map<String, String>> byClass = new HashMap<>();
    private int size;

    private KernelIndex() {
    }

    /**
     * @param text содержимое {@code kernels.idx}
     * @return индекс
     * @throws IllegalArgumentException если строка не состоит из четырёх полей
     */
    public static KernelIndex parse(String text) {
        KernelIndex idx = new KernelIndex();
        int lineNo = 0;
        for (String line : text.split("\n", -1)) {
            lineNo++;
            String l = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            if (l.isBlank()) {
                continue;
            }
            String[] f = l.split("\t", -1);
            if (f.length != 4 || f[0].isEmpty() || f[1].isEmpty() || f[2].isEmpty() || f[3].isEmpty()) {
                throw new IllegalArgumentException("kernels.idx, line " + lineNo + ": expected four tab-separated fields");
            }
            idx.byClass.computeIfAbsent(f[0], k -> new HashMap<>()).put(f[1] + f[2], f[3]);
            idx.size++;
        }
        return idx;
    }

    /**
     * @param className  двоичное имя класса ({@code pkg.Outer$Inner}, с точками)
     * @param method     имя метода
     * @param descriptor JVM-дескриптор метода
     * @return {@code kernelId} либо {@code null}, если такого ядра нет в индексе
     */
    public String kernelId(String className, String method, String descriptor) {
        Map<String, String> m = byClass.get(className);
        return m == null ? null : m.get(method + descriptor);
    }

    /**
     * @return двоичные имена классов, у которых есть ядра
     */
    public Iterable<String> classes() {
        return byClass.keySet();
    }

    /** @return число ядер в индексе */
    public int size() {
        return size;
    }
}
