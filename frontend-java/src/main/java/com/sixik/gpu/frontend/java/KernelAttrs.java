package com.sixik.gpu.frontend.java;

import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.annotations.WorkGroup;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sun.source.util.TreePath;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Атрибуты ядра из аннотаций {@code @Kernel} и {@code @WorkGroup}, превращаемые в атрибуты функции IR
 * (REWORK_ARCHITECTURE.md, 3.B). Настройки генерации ({@code fp}, {@code opt}, {@code inline}) берутся из строки
 * {@code compilationProperties} ({@link CompilationProperties}); устаревшие элементы {@code optLevel} и {@code inline}
 * учитываются, только если заданы явно, и не могут дублировать ключ строки.
 *
 * <p>Имена {@code required} приводятся к канонической форме (без повторов, по алфавиту), чтобы порядок записи в
 * исходнике не менял хэш ядра.</p>
 */
final class KernelAttrs {

    /** Имена возможностей устройства, допустимые в {@code required} (совпадают с {@code Features} в модуле ir). */
    static final Set<String> KNOWN_FEATURES = Set.of("FP64", "INT64_ATOMICS", "INT64_EXT_ATOMICS", "FP16", "IMAGES");

    private final int inline;
    private final int optLevel;
    private final int fp;
    private final int[] workGroup;
    private final String[] required;

    private KernelAttrs(int inline, int optLevel, int fp, int[] workGroup, String[] required) {
        this.inline = inline;
        this.optLevel = optLevel;
        this.fp = fp;
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
        CompilationProperties props = CompilationProperties.parse(k.compilationProperties());
        for (CompilationProperties.Problem p : props.problems) {
            Object[] args = new Object[p.args().length + 1];
            args[0] = name;
            System.arraycopy(p.args(), 0, args, 1, p.args().length);
            rep.report(path, p.code(), args);
        }
        // устаревшие элементы читаются через AnnotationMirror: так видно, задан ли элемент явно
        Object legacyOpt = explicitValue(kernel, "optLevel");
        Object legacyInline = explicitValue(kernel, "inline");
        int opt = props.opt;
        if (legacyOpt != null) {
            int level = (Integer) legacyOpt;
            if (level < 0 || level > 3) {
                rep.report(path, "GPU0012", level, name);
            }
            if (props.hasOpt()) {
                rep.report(path, "GPU0023", name, "opt", "optLevel");
            }
            opt = level;
        }
        int inline = props.hasInline() ? props.inline : IrModule.INLINE_DEFAULT;
        if (legacyInline != null) {
            if (props.hasInline()) {
                rep.report(path, "GPU0023", name, "inline", "inline");
            }
            inline = switch (legacyInline.toString()) {
                case "ALWAYS" -> IrModule.INLINE_ALWAYS;
                case "NEVER" -> IrModule.INLINE_NEVER;
                default -> IrModule.INLINE_DEFAULT;
            };
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
        return new KernelAttrs(inline, opt, props.fp, wg, req.toArray(new String[0]));
    }

    /**
     * Значение элемента {@code @Kernel}, заданное в исходнике явно (значение по умолчанию не считается).
     *
     * @param kernel  элемент ядра
     * @param element имя элемента аннотации
     * @return значение ({@link Integer} для чисел, константа перечисления для enum) либо {@code null}
     */
    private static Object explicitValue(ExecutableElement kernel, String element) {
        for (AnnotationMirror am : kernel.getAnnotationMirrors()) {
            if (!((TypeElement) am.getAnnotationType().asElement()).getQualifiedName()
                    .contentEquals(Kernel.class.getCanonicalName())) {
                continue;
            }
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : am.getElementValues().entrySet()) {
                if (e.getKey().getSimpleName().contentEquals(element)) {
                    Object v = e.getValue().getValue();
                    return v instanceof VariableElement ve ? ve.getSimpleName().toString() : v;
                }
            }
        }
        return null;
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
        b.fpMode(fp);
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
