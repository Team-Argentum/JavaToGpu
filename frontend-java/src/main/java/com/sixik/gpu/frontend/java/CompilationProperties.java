package com.sixik.gpu.frontend.java;

import com.sixik.gpu.ir.IrModule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Разбор строки {@code @Kernel(compilationProperties = "...")}: пары {@code ключ=значение} через {@code ;}, списки
 * значений через запятую, пробелы вокруг разделителей игнорируются, пустые сегменты ({@code "a=1;"}) допустимы.
 *
 * <p>Разбор не зависит от javac: ошибки возвращаются как список {@link Problem} с кодом {@code GPU####} и
 * аргументами сообщения (кроме имени ядра - его подставляет вызывающий). Ключи и значения регистрозависимы и пишутся
 * строчными буквами.</p>
 */
final class CompilationProperties {

    /** Известные ключи в порядке, в котором они перечисляются в подсказках. */
    static final List<String> KEYS = List.of("fp", "opt", "inline");

    /** Значения {@code fp} и их биты {@code IrModule.FP_*}. */
    private static final Map<String, Integer> FP = new LinkedHashMap<>();

    /** Значения {@code inline}. */
    private static final Map<String, Integer> INLINE = new LinkedHashMap<>();

    static {
        FP.put("contract", IrModule.FP_CONTRACT);
        FP.put("reciprocal", IrModule.FP_RECIPROCAL);
        FP.put("finite", IrModule.FP_FINITE);
        FP.put("nsz", IrModule.FP_NO_SIGNED_ZERO);
        FP.put("native", IrModule.FP_NATIVE);
        FP.put("relaxed", IrModule.FP_ALL);
        INLINE.put("default", IrModule.INLINE_DEFAULT);
        INLINE.put("always", IrModule.INLINE_ALWAYS);
        INLINE.put("never", IrModule.INLINE_NEVER);
    }

    /**
     * Ошибка разбора.
     *
     * @param code код диагностики
     * @param args аргументы сообщения после имени ядра ({@code {1}}, {@code {2}}...)
     */
    record Problem(String code, Object... args) {
    }

    /** Маска {@code fp} (0 - строгий float). */
    final int fp;
    /** Уровень оптимизации либо {@link IrModule#OPT_INHERIT}. */
    final int opt;
    /** Политика inline либо {@code -1}, если ключ не задан. */
    final int inline;
    /** Ошибки разбора; при непустом списке значения полей не используются. */
    final List<Problem> problems;

    private CompilationProperties(int fp, int opt, int inline, List<Problem> problems) {
        this.fp = fp;
        this.opt = opt;
        this.inline = inline;
        this.problems = problems;
    }

    /**
     * @return задан ли ключ {@code opt}
     */
    boolean hasOpt() {
        return opt != IrModule.OPT_INHERIT;
    }

    /**
     * @return задан ли ключ {@code inline}
     */
    boolean hasInline() {
        return inline >= 0;
    }

    /**
     * Разбирает строку свойств.
     *
     * @param text значение элемента аннотации
     * @return результат; ошибки - в {@link #problems}
     */
    static CompilationProperties parse(String text) {
        List<Problem> problems = new ArrayList<>();
        int fp = 0;
        int opt = IrModule.OPT_INHERIT;
        int inline = -1;
        List<String> seen = new ArrayList<>();
        for (String raw : text.split(";", -1)) {
            String segment = raw.strip();
            if (segment.isEmpty()) {
                continue;
            }
            int eq = segment.indexOf('=');
            if (eq <= 0 || eq == segment.length() - 1 || segment.indexOf('=', eq + 1) >= 0) {
                problems.add(new Problem("GPU0019", segment));
                continue;
            }
            String key = segment.substring(0, eq).strip();
            String value = segment.substring(eq + 1).strip();
            if (key.isEmpty() || value.isEmpty() || key.chars().anyMatch(Character::isWhitespace)) {
                problems.add(new Problem("GPU0019", segment));
                continue;
            }
            if (!KEYS.contains(key)) {
                problems.add(new Problem("GPU0020", key, suggestion(key, KEYS)));
                continue;
            }
            if (seen.contains(key)) {
                problems.add(new Problem("GPU0022", key));
                continue;
            }
            seen.add(key);
            switch (key) {
                case "fp" -> {
                    for (String v : value.split(",", -1)) {
                        String item = v.strip();
                        Integer bit = FP.get(item);
                        if (bit == null) {
                            problems.add(new Problem("GPU0021", key, quote(item),
                                    String.join(", ", FP.keySet()) + suggestion(item, FP.keySet())));
                        } else {
                            fp |= bit;
                        }
                    }
                }
                case "opt" -> {
                    if (value.length() == 1 && value.charAt(0) >= '0' && value.charAt(0) <= '3') {
                        opt = value.charAt(0) - '0';
                    } else {
                        problems.add(new Problem("GPU0021", key, quote(value), "0, 1, 2, 3"));
                    }
                }
                case "inline" -> {
                    Integer policy = INLINE.get(value);
                    if (policy == null) {
                        problems.add(new Problem("GPU0021", key, quote(value),
                                String.join(", ", INLINE.keySet()) + suggestion(value, INLINE.keySet())));
                    } else {
                        inline = policy;
                    }
                }
                default -> throw new IllegalStateException("unhandled key " + key);
            }
        }
        return new CompilationProperties(fp, opt, inline, List.copyOf(problems));
    }

    private static String quote(String s) {
        return "'" + s + "'";
    }

    /**
     * Подсказка для опечатки: ближайший известный вариант на расстоянии правки не больше 2.
     *
     * @param word    введённое слово
     * @param options известные варианты
     * @return {@code "; did you mean 'x'?"} либо пустая строка
     */
    static String suggestion(String word, Iterable<String> options) {
        String w = word.toLowerCase(Locale.ROOT);
        String best = null;
        int bestDistance = 3;
        for (String o : options) {
            int d = distance(w, o);
            if (d < bestDistance) {
                bestDistance = d;
                best = o;
            }
        }
        return best == null || w.isEmpty() ? "" : "; did you mean '" + best + "'?";
    }

    /** Расстояние Левенштейна (вставка, удаление, замена - по 1). */
    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int sub = prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                cur[j] = Math.min(sub, Math.min(prev[j], cur[j - 1]) + 1);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
