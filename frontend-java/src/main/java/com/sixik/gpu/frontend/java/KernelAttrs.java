package com.sixik.gpu.frontend.java;

import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.annotations.WorkGroup;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sun.source.util.TreePath;

import javax.lang.model.element.ExecutableElement;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/**
 * Атрибуты ядра из аннотаций {@code @Kernel} и {@code @WorkGroup}, превращаемые в атрибуты функции IR
 * (REWORK_ARCHITECTURE.md, 3.B).
 *
 * <p>Имена {@code required} приводятся к канонической форме (без повторов, по алфавиту), чтобы порядок записи в
 * исходнике не менял хэш ядра.</p>
 */
final class KernelAttrs {

    /** Имена возможностей устройства, допустимые в {@code required} (совпадают с {@code Features} в модуле ir). */
    static final Set<String> KNOWN_FEATURES = Set.of("FP64", "INT64_ATOMICS", "FP16", "IMAGES");

    private final int inline;
    private final int optLevel;
    private final int[] workGroup;
    private final String[] required;

    private KernelAttrs(int inline, int optLevel, int[] workGroup, String[] required) {
        this.inline = inline;
        this.optLevel = optLevel;
        this.workGroup = workGroup;
        this.required = required;
    }

    /**
     * Читает и проверяет атрибуты ядра.
     *
     * @param kernel элемент ядра
     * @param path   путь к методу (для диагностик)
     * @param rep    приёмник диагностик
     * @return атрибуты либо {@code null}, если найдены ошибки (они уже выданы)
     */
    static KernelAttrs parse(ExecutableElement kernel, TreePath path, Reporter rep) {
        int before = rep.errorCount();
        Kernel k = kernel.getAnnotation(Kernel.class);
        WorkGroup w = kernel.getAnnotation(WorkGroup.class);
        String name = kernel.getSimpleName().toString();

        int[] wg = null;
        if (k.workgroup() != 0 && w != null) {
            rep.report(path, "GPU0008", name);
        } else if (w != null) {
            wg = new int[]{w.x(), w.y(), w.z()};
        } else if (k.workgroup() != 0) {
            wg = new int[]{k.workgroup(), 1, 1};
        }
        if (wg != null && (wg[0] <= 0 || wg[1] <= 0 || wg[2] <= 0)) {
            rep.report(path, "GPU0013", Arrays.toString(wg));
            wg = null;
        }
        if (k.optLevel() < 0 || k.optLevel() > 3) {
            rep.report(path, "GPU0012", k.optLevel(), name);
        }
        Set<String> req = new TreeSet<>();
        for (String r : k.required()) {
            if (KNOWN_FEATURES.contains(r)) {
                req.add(r);
            } else {
                rep.report(path, "GPU0009", r, name);
            }
        }
        if (rep.errorCount() != before) {
            return null;
        }
        int inline = switch (k.inline()) {
            case DEFAULT -> IrModule.INLINE_DEFAULT;
            case ALWAYS -> IrModule.INLINE_ALWAYS;
            case NEVER -> IrModule.INLINE_NEVER;
        };
        return new KernelAttrs(inline, k.optLevel(), wg, req.toArray(new String[0]));
    }

    /**
     * Записывает атрибуты в текущую функцию builder'а.
     *
     * @param b             builder (функция уже начата)
     * @param fallbackIndex индекс функции-варианта либо -1
     */
    void apply(IrBuilder b, int fallbackIndex) {
        b.inlinePolicy(inline);
        b.optLevel(optLevel);
        if (workGroup != null) {
            b.requireWorkGroup(workGroup[0], workGroup[1], workGroup[2]);
        }
        if (required.length > 0) {
            b.required(required);
        }
        if (fallbackIndex >= 0) {
            b.fallback(fallbackIndex);
        }
    }
}
