package com.sixik.gpu.frontend.common;

import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Атрибуты ядра из аннотаций {@code @Kernel} и {@code @WorkGroup}, превращаемые в атрибуты функции IR
 * (REWORK_ARCHITECTURE.md, 3.B). Настройки генерации ({@code fp}, {@code opt}, {@code inline}) берутся из строки
 * {@code compilationProperties} ({@link CompilationProperties}); устаревшие элементы {@code optLevel} и {@code inline}
 * учитываются, только если заданы явно, и не могут дублировать ключ строки.
 *
 * <p>Код общий для фронтендов (раздел 17.2): вход - карты <b>явно заданных</b> элементов аннотаций, которые каждый
 * фронтенд строит из своего представления ({@code AnnotationMirror} в javac, {@code AnnotationNode} в байткоде).
 * Значения по умолчанию элементов известны здесь и совпадают с объявлением аннотаций в модуле {@code api}. Форма
 * значений в картах:</p>
 * <ul>
 *   <li>{@code int} - {@link Integer};</li>
 *   <li>{@code String} - {@link String};</li>
 *   <li>константа перечисления - её имя ({@link String}, например {@code "ALWAYS"});</li>
 *   <li>{@code String[]} - {@link List} строк.</li>
 * </ul>
 *
 * <p>Имена {@code required} приводятся к канонической форме (без повторов, по алфавиту), чтобы порядок записи в
 * исходнике не менял хэш ядра.</p>
 */
public final class KernelAttributes {

    /** Имена возможностей устройства, допустимые в {@code required} (совпадают с {@code Features} в модуле ir). */
    public static final Set<String> KNOWN_FEATURES = Set.of("FP64", "INT64_ATOMICS", "INT64_EXT_ATOMICS", "FP16", "IMAGES");

    /** Приёмник диагностик: место в исходнике или class-файле знает вызывающий фронтенд. */
    @FunctionalInterface
    public interface Sink {
        /**
         * @param code код из таблицы диагностик ({@link Diag})
         * @param args значения для подстановки в сообщение
         */
        void report(String code, Object... args);
    }

    private final int inline;
    private final int optLevel;
    private final int fp;
    private final int[] workGroup;
    private final String[] required;

    private KernelAttributes(int inline, int optLevel, int fp, int[] workGroup, String[] required) {
        this.inline = inline;
        this.optLevel = optLevel;
        this.fp = fp;
        this.workGroup = workGroup;
        this.required = required;
    }

    /**
     * Читает и проверяет атрибуты ядра.
     *
     * @param name      простое имя метода ядра (для сообщений)
     * @param kernel    явно заданные элементы {@code @Kernel}
     * @param workGroup явно заданные элементы {@code @WorkGroup} либо {@code null}, если аннотации нет
     * @param sink      приёмник диагностик
     * @return атрибуты либо {@code null}, если найдены ошибки (они уже выданы)
     */
    public static KernelAttributes parse(String name, Map<String, Object> kernel, Map<String, Object> workGroup,
                                         Sink sink) {
        int[] errors = {0};
        Sink rep = (code, args) -> {
            if (Diag.entry(code).error()) {
                errors[0]++;
            }
            sink.report(code, args);
        };

        int kernelWorkgroup = intValue(kernel, "workgroup", 0);
        int[] wg = null;
        if (kernelWorkgroup != 0 && workGroup != null) {
            rep.report("GPU0008", name);
        } else if (workGroup != null) {
            wg = new int[]{intValue(workGroup, "x", 1), intValue(workGroup, "y", 1), intValue(workGroup, "z", 1)};
        } else if (kernelWorkgroup != 0) {
            wg = new int[]{kernelWorkgroup, 1, 1};
        }
        if (wg != null && (wg[0] <= 0 || wg[1] <= 0 || wg[2] <= 0)) {
            rep.report("GPU0013", Arrays.toString(wg));
            wg = null;
        }
        CompilationProperties props = CompilationProperties.parse((String) kernel.getOrDefault("compilationProperties", ""));
        for (CompilationProperties.Problem p : props.problems) {
            Object[] args = new Object[p.args().length + 1];
            args[0] = name;
            System.arraycopy(p.args(), 0, args, 1, p.args().length);
            rep.report(p.code(), args);
        }
        // устаревшие элементы учитываются, только если заданы явно
        Object legacyOpt = kernel.get("optLevel");
        Object legacyInline = kernel.get("inline");
        int opt = props.opt;
        if (legacyOpt != null) {
            int level = (Integer) legacyOpt;
            if (level < 0 || level > 3) {
                rep.report("GPU0012", level, name);
            }
            if (props.hasOpt()) {
                rep.report("GPU0023", name, "opt", "optLevel");
            }
            opt = level;
        }
        int inline = props.hasInline() ? props.inline : IrModule.INLINE_DEFAULT;
        if (legacyInline != null) {
            if (props.hasInline()) {
                rep.report("GPU0023", name, "inline", "inline");
            }
            inline = switch (legacyInline.toString()) {
                case "ALWAYS" -> IrModule.INLINE_ALWAYS;
                case "NEVER" -> IrModule.INLINE_NEVER;
                default -> IrModule.INLINE_DEFAULT;
            };
        }
        Set<String> req = new TreeSet<>();
        for (Object o : (List<?>) kernel.getOrDefault("required", List.of())) {
            String r = o.toString();
            if (KNOWN_FEATURES.contains(r)) {
                req.add(r);
            } else {
                rep.report("GPU0009", r, name);
            }
        }
        if (errors[0] != 0) {
            return null;
        }
        return new KernelAttributes(inline, opt, props.fp, wg, req.toArray(new String[0]));
    }

    private static int intValue(Map<String, Object> values, String element, int defaultValue) {
        Object v = values.get(element);
        return v == null ? defaultValue : (Integer) v;
    }

    /**
     * Записывает атрибуты в текущую функцию builder'а.
     *
     * @param b             builder (функция уже начата)
     * @param fallbackIndex индекс функции-варианта либо -1
     */
    public void apply(IrBuilder b, int fallbackIndex) {
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
