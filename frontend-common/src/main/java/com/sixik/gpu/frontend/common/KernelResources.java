package com.sixik.gpu.frontend.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Имена и формат ресурсов, которые фронтенд кладёт рядом с class-файлами (решение по O5, REWORK_ARCHITECTURE.md, 5.15).
 * Формат общий для всех фронтендов (раздел 17.2): рантайм и weaver не знают, какой фронтенд построил IR.
 *
 * <ul>
 *   <li>{@code META-INF/gpu/ir/<kernelId>.gir} - бинарный IR ядра ({@code IrBinary}); по {@code kernelId} его находит
 *   рантайм, не зная, из какого класса пришло ядро;</li>
 *   <li>{@code META-INF/gpu/kernels.idx} - индекс для weaver'а: по строке на ядро, поля через табуляцию:
 *   {@code <двоичное имя класса> <имя метода> <JVM-дескриптор> <kernelId>}; строки отсортированы, разделитель
 *   {@code \n}. Weaver не зависит от {@code ir} и не умеет считать хэш, поэтому {@code kernelId} берёт отсюда.</li>
 * </ul>
 */
public final class KernelResources {

    /** Каталог IR-ресурсов. */
    public static final String IR_DIR = "META-INF/gpu/ir/";
    /** Расширение файла IR. */
    public static final String IR_EXT = ".gir";
    /** Путь индекса ядер. */
    public static final String INDEX = "META-INF/gpu/kernels.idx";

    private KernelResources() {
    }

    /**
     * Строка индекса.
     *
     * @param className  двоичное имя класса ({@code pkg.Outer$Inner})
     * @param method     имя метода
     * @param descriptor JVM-дескриптор метода
     * @param kernelId   идентификатор ядра
     */
    public record IndexEntry(String className, String method, String descriptor, String kernelId) {
    }

    /**
     * @param kernelId идентификатор ядра
     * @return путь IR-ресурса относительно корня class-файлов
     */
    public static String irPath(String kernelId) {
        return IR_DIR + kernelId + IR_EXT;
    }

    /**
     * Текст индекса: строки отсортированы по классу, методу и дескриптору.
     *
     * @param entries записи
     * @return текст в UTF-8, каждая строка оканчивается {@code \n}
     */
    public static String formatIndex(Collection<IndexEntry> entries) {
        List<IndexEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(IndexEntry::className).thenComparing(IndexEntry::method)
                .thenComparing(IndexEntry::descriptor));
        StringBuilder sb = new StringBuilder();
        for (IndexEntry e : sorted) {
            sb.append(e.className()).append('\t').append(e.method()).append('\t').append(e.descriptor()).append('\t')
                    .append(e.kernelId()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Разбор текста индекса (обратная операция к {@link #formatIndex}).
     *
     * @param text содержимое {@code kernels.idx}
     * @return записи в порядке файла
     * @throws IllegalArgumentException если строка не состоит из четырёх полей
     */
    public static List<IndexEntry> parseIndex(String text) {
        List<IndexEntry> r = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            String[] f = line.split("\t", -1);
            if (f.length != 4) {
                throw new IllegalArgumentException("index line must contain 4 fields: " + line);
            }
            r.add(new IndexEntry(f[0], f[1], f[2], f[3]));
        }
        return r;
    }
}
