package com.sixik.gpu.apigen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Разбор декларативного файла {@code builtins/builtins.def} для генератора Java-API
 * (REWORK_ARCHITECTURE.md, 5.7; формат описан в заголовке самого файла).
 *
 * <p>Это вторая сторона одного и того же файла: модуль {@code ir} читает из него сигнатуры и флаги для
 * валидатора, а генератор - Javadoc, JVM-реализации и состав Java-типов. Парсер строгий: любое нарушение формата
 * даёт {@link IllegalArgumentException} с номером строки, чтобы ошибка в таблице ломала сборку, а не порождала
 * тихо неверный {@code Gpu.java}.</p>
 */
public final class BuiltinsDef {

    /** Скалярные типы, у которых есть Java-представление в API. */
    public static final Set<String> API_SCALARS = Set.of("f32", "f64", "i32", "i64");
    /** Допустимое число компонент вектора (совпадает с {@code Types} в модуле {@code ir}, правило IR-T07). */
    public static final Set<Integer> API_LANES = Set.of(2, 3, 4, 8, 16);

    private static final Set<String> FLAG_NAMES = Set.of("pure", "effect", "reads", "div", "uniform", "atomic", "ext", "sync");

    /**
     * Один builtin.
     *
     * @param id    стабильный числовой id (на него ссылается бинарный IR)
     * @param name  имя в Java-API
     * @param flags флаги без {@code ulp}/{@code ulp64}
     * @param ulp   допуск {@code f32} в ULP либо {@code -1} (сравнивать точно)
     * @param ulp64 допуск {@code f64} в ULP; без {@code ulp64=} равен {@code ulp}
     * @param ret   токен типа результата
     * @param args  токены типов аргументов
     * @param doc   Javadoc-описание
     * @param jvm   JVM-реализации: ключ - скаляр ({@code f32}...) либо пустая строка для необобщённых builtin
     */
    public record Builtin(int id, String name, Set<String> flags, int ulp, int ulp64, String ret,
                          List<String> args, String doc, Map<String, String> jvm) {
    }

    /**
     * Разобранный файл.
     *
     * @param version        версия таблицы
     * @param vectorScalars  скаляры, для которых создаются векторные типы
     * @param vectorLanes    допустимые числа компонент (по возрастанию)
     * @param pointerScalars скаляры, для которых создаются классы-ссылки
     * @param builtins       builtins в порядке id
     */
    public record Def(int version, List<String> vectorScalars, List<Integer> vectorLanes,
                      List<String> pointerScalars, List<Builtin> builtins) {
    }

    /** Изменяемая заготовка builtin на время разбора. */
    private static final class Draft {
        int id;
        String name;
        Set<String> flags = new LinkedHashSet<>();
        int ulp = -1;
        int ulp64 = -2;
        String ret;
        List<String> args;
        String doc;
        Map<String, String> jvm = new LinkedHashMap<>();
        int line;

        Builtin freeze() {
            return new Builtin(id, name, Set.copyOf(flags), ulp, ulp64 == -2 ? ulp : ulp64, ret,
                    List.copyOf(args), doc, Map.copyOf(jvm));
        }
    }

    private BuiltinsDef() {
    }

    /**
     * Разбирает текст {@code builtins.def}.
     *
     * @param text содержимое файла
     * @return разобранные данные; builtins отсортированы по id
     * @throws IllegalArgumentException при нарушении формата (сообщение содержит номер строки)
     */
    public static Def parse(String text) {
        int version = -1;
        List<String> vectorScalars = new ArrayList<>();
        List<Integer> vectorLanes = new ArrayList<>();
        List<String> pointerScalars = new ArrayList<>();
        List<Draft> drafts = new ArrayList<>();
        Draft cur = null;

        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            int no = i + 1;
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String head = line.split("\\s+", 2)[0];
            String rest = line.length() > head.length() ? line.substring(head.length()).strip() : "";
            if (Character.isDigit(head.charAt(0))) {
                cur = parseRow(line, no);
                drafts.add(cur);
                continue;
            }
            switch (head) {
                case "version" -> version = parseInt(rest, no, "version");
                case "vectors" -> {
                    int colon = rest.indexOf(':');
                    if (colon < 0) {
                        throw error(no, "vectors: expected '<scalars> : <lanes>'");
                    }
                    for (String s : words(rest.substring(0, colon))) {
                        requireScalar(s, no);
                        vectorScalars.add(s);
                    }
                    for (String l : words(rest.substring(colon + 1))) {
                        int lanes = parseInt(l, no, "lanes");
                        if (!API_LANES.contains(lanes)) {
                            throw error(no, "vectors: invalid lane count " + lanes);
                        }
                        vectorLanes.add(lanes);
                    }
                    vectorLanes.sort(Integer::compare);
                }
                case "pointers" -> {
                    for (String s : words(rest)) {
                        requireScalar(s, no);
                        pointerScalars.add(s);
                    }
                }
                case "doc" -> {
                    requireCurrent(cur, no, "doc");
                    if (cur.doc != null) {
                        throw error(no, "builtin " + cur.name + ": duplicate doc");
                    }
                    if (rest.isEmpty()) {
                        throw error(no, "builtin " + cur.name + ": empty doc");
                    }
                    cur.doc = rest;
                }
                case "jvm" -> {
                    requireCurrent(cur, no, "jvm");
                    int eq = rest.indexOf('=');
                    if (eq < 0) {
                        throw error(no, "jvm: expected '[types] = expression'");
                    }
                    String expr = rest.substring(eq + 1).strip();
                    if (expr.isEmpty()) {
                        throw error(no, "jvm: empty expression");
                    }
                    String keys = rest.substring(0, eq).strip();
                    List<String> keyList = keys.isEmpty() ? List.of("") : List.of(keys.split("\\s*,\\s*"));
                    for (String k : keyList) {
                        if (!k.isEmpty()) {
                            requireScalar(k, no);
                        }
                        if (cur.jvm.put(k, expr) != null) {
                            throw error(no, "builtin " + cur.name + ": duplicate jvm implementation for '" + k + "'");
                        }
                    }
                }
                default -> throw error(no, "unknown directive '" + head + "'");
            }
        }
        if (version < 0) {
            throw error(0, "missing version line");
        }

        List<Builtin> builtins = new ArrayList<>();
        Set<Integer> ids = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        for (Draft d : drafts) {
            if (!ids.add(d.id)) {
                throw error(d.line, "duplicate id " + d.id);
            }
            if (!names.add(d.name)) {
                throw error(d.line, "duplicate name " + d.name);
            }
            if (d.doc == null) {
                throw error(d.line, "builtin " + d.name + ": missing doc");
            }
            builtins.add(d.freeze());
        }
        builtins.sort((a, b) -> Integer.compare(a.id(), b.id()));
        return new Def(version, List.copyOf(vectorScalars), List.copyOf(vectorLanes),
                List.copyOf(pointerScalars), List.copyOf(builtins));
    }

    private static Draft parseRow(String line, int no) {
        String[] r = line.split("\\s+", 4);
        if (r.length < 4) {
            throw error(no, "expected '<id> <name> <flags> <ret> (<args>)'");
        }
        Draft d = new Draft();
        d.line = no;
        d.id = parseInt(r[0], no, "id");
        d.name = r[1];
        for (String f : r[2].split(",")) {
            if (f.startsWith("ulp64=")) {
                d.ulp64 = parseInt(f.substring(6), no, "ulp64");
            } else if (f.startsWith("ulp=")) {
                d.ulp = parseInt(f.substring(4), no, "ulp");
            } else if (FLAG_NAMES.contains(f)) {
                d.flags.add(f);
            } else {
                throw error(no, "unknown flag '" + f + "'");
            }
        }
        String sig = r[3].strip();
        int open = sig.indexOf('(');
        int close = sig.lastIndexOf(')');
        if (open < 0 || close < open) {
            throw error(no, "bad signature '" + sig + "'");
        }
        d.ret = sig.substring(0, open).strip();
        String inner = sig.substring(open + 1, close).strip();
        d.args = inner.isEmpty() ? List.of() : List.of(inner.split("\\s*,\\s*"));
        return d;
    }

    private static List<String> words(String s) {
        String t = s.strip();
        return t.isEmpty() ? List.of() : List.of(t.split("\\s+"));
    }

    private static void requireScalar(String s, int no) {
        if (!API_SCALARS.contains(s)) {
            throw error(no, "type '" + s + "' has no Java representation (allowed: " + API_SCALARS + ")");
        }
    }

    private static void requireCurrent(Draft cur, int no, String directive) {
        if (cur == null) {
            throw error(no, directive + " before the first builtin line");
        }
    }

    private static int parseInt(String s, int no, String what) {
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            throw error(no, what + ": not a number '" + s + "'");
        }
    }

    private static IllegalArgumentException error(int line, String message) {
        return new IllegalArgumentException("builtins.def" + (line > 0 ? ":" + line : "") + ": " + message);
    }
}
