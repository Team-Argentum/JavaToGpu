package com.sixik.gpu.codegen.opencl;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Детерминированный манглинг идентификаторов C (REWORK_ARCHITECTURE.md, 8.A: 8.1).
 *
 * <p>Имя из debug-секции IR - это произвольная строка, а в C она обязана быть идентификатором, не совпадающим
 * с ключевым словом, типом или функцией, которую вызывает сгенерированный код (иначе локаль {@code sin} сломала бы
 * вызов {@code sin(x)} в той же области видимости). Поэтому:</p>
 * <ol>
 *   <li>недопустимые символы заменяются на {@code _}, ведущая цифра получает префикс {@code _}, имя с ведущими
 *       {@code __} (зарезервированы C) - тоже;</li>
 *   <li>зарезервированное имя получает префикс {@code u_} (суффикс не помог бы именам вроде {@code get_x});</li>
 *   <li>повтор в пределах одного набора получает суффикс {@code _1, _2, ...} в порядке запросов.</li>
 * </ol>
 *
 * <p>Порядок запросов задаёт эмиттер (по возрастанию индексов), поэтому результат воспроизводим.</p>
 */
final class Names {

    private static final Set<String> RESERVED = new HashSet<>(java.util.List.of(
            // ключевые слова C и OpenCL C
            "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else", "enum",
            "extern", "float", "for", "goto", "if", "inline", "int", "long", "register", "restrict", "return",
            "short", "signed", "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void",
            "volatile", "while", "bool", "true", "false", "half", "uchar", "ushort", "uint", "ulong", "size_t",
            "ptrdiff_t", "intptr_t", "uintptr_t", "kernel", "global", "local", "constant", "private", "image2d_t",
            "image3d_t", "sampler_t", "event_t", "NULL",
            // функции и макросы, которые вызывает сгенерированный код
            "sin", "cos", "sqrt", "fma", "fmod", "max", "min", "isnan", "signbit", "barrier",
            "NAN", "INFINITY", "INT_MIN", "INT_MAX", "LONG_MIN", "LONG_MAX"));

    private static final Pattern VECTOR_TYPE = Pattern.compile(
            "(char|uchar|short|ushort|int|uint|long|ulong|float|double|half)(2|3|4|8|16)");

    private final Set<String> used = new HashSet<>();

    /**
     * @return независимая копия набора занятых имён (имена функции не должны попадать в соседние функции)
     */
    Names copy() {
        Names n = new Names();
        n.used.addAll(used);
        return n;
    }

    /**
     * Резервирует имя, не меняя его (для имён, которые уже выбраны, например параметров ядра).
     *
     * @param name имя
     */
    void reserve(String name) {
        used.add(name);
    }

    /**
     * Выдаёт свободное допустимое имя.
     *
     * @param wanted желаемое имя (может быть {@code null} или пустым)
     * @param dflt   имя, если желаемого нет
     * @return идентификатор C, ранее не выданный этим набором
     */
    String unique(String wanted, String dflt) {
        String base = sanitize(wanted == null || wanted.isEmpty() ? dflt : wanted);
        if (isReserved(base)) {
            // суффикс не спасает зарезервированный префикс (get_, convert_ ...), поэтому имя получает префикс
            base = "u_" + base;
        }
        String candidate = base;
        int k = 0;
        while (used.contains(candidate)) {
            k++;
            candidate = base + "_" + k;
        }
        used.add(candidate);
        return candidate;
    }

    /**
     * @param name проверяемая строка
     * @return {@code true}, если имя нельзя использовать как идентификатор пользовательского кода
     */
    static boolean isReserved(String name) {
        return RESERVED.contains(name)
                || VECTOR_TYPE.matcher(name).matches()
                || name.startsWith("as_") || name.startsWith("convert_") || name.startsWith("get_")
                || name.startsWith("atomic_") || name.startsWith("atom_") || name.startsWith("gpu_")
                || name.startsWith("cont_") || name.startsWith("CLK_") || name.startsWith("cl_");
    }

    /**
     * Приводит строку к идентификатору C.
     *
     * @param s произвольная строка
     * @return непустой идентификатор из {@code [A-Za-z0-9_]}, не начинающийся с цифры и с {@code __}
     */
    static String sanitize(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 1);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            sb.append(ok ? c : '_');
        }
        if (sb.isEmpty()) {
            return "_";
        }
        if (Character.isDigit(sb.charAt(0))) {
            sb.insert(0, '_');
        }
        if (sb.length() >= 2 && sb.charAt(0) == '_' && sb.charAt(1) == '_') {
            sb.insert(0, 'n');
        }
        return sb.toString();
    }
}
