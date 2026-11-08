package com.sixik.gpu.apigen;

import com.sixik.gpu.apigen.BuiltinsDef.Builtin;
import com.sixik.gpu.apigen.BuiltinsDef.Def;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Генератор Java-API из разобранной таблицы builtins (REWORK_ARCHITECTURE.md, 5.7): класс {@code Gpu}, векторные
 * типы {@code Float2..Long16} и классы-ссылки {@code FloatPtr..LongPtr}.
 *
 * <p>Генератор - чистая функция «таблица -> тексты файлов»: нет ввода-вывода, порядок вывода детерминирован
 * (по id builtin, затем скаляры {@code f32,f64,i32,i64}, затем векторы по возрастанию числа компонент), поэтому
 * одинаковый {@code builtins.def} всегда даёт побайтово одинаковые файлы.</p>
 */
public final class ApiGenerator {

    private static final String PKG_API = "com.sixik.gpu.api";
    private static final String PKG_TYPES = "com.sixik.gpu.api.types";
    private static final String HEADER = "// СГЕНЕРИРОВАНО из builtins/builtins.def (задача generateApi). Не править вручную.\n";

    /** Порядок скаляров в выводе. */
    private static final List<String> SCALAR_ORDER = List.of("f32", "f64", "i32", "i64");

    /**
     * Результат генерации.
     *
     * @param files   путь относительно корня исходников -> содержимое файла (в порядке генерации)
     * @param skipped имена builtins, для которых Java-перегрузки не созданы (нет Java-представления типа)
     */
    public record Output(Map<String, String> files, List<String> skipped) {
    }

    /** Java-представление скаляра. */
    private record Scalar(String id, String java, String cls, String bitsEq, String box) {
        /** @return выражение «поле {@code l} равно полю {@code r}» с учётом NaN и знака нуля */
        String eq(String l, String r) {
            return bitsEq.isEmpty() ? l + " == " + r : bitsEq.formatted(l, r);
        }

        String hash(String field) {
            return box + ".hashCode(" + field + ")";
        }
    }

    private static final Map<String, Scalar> SCALARS = Map.of(
            "f32", new Scalar("f32", "float", "Float", "Float.floatToIntBits(%s) == Float.floatToIntBits(%s)", "Float"),
            "f64", new Scalar("f64", "double", "Double", "Double.doubleToLongBits(%s) == Double.doubleToLongBits(%s)", "Double"),
            "i32", new Scalar("i32", "int", "Int", "", "Integer"),
            "i64", new Scalar("i64", "long", "Long", "", "Long"));

    private static final Map<String, String> FIXED_JAVA = Map.of(
            "void", "void", "bool", "boolean", "i32", "int", "i64", "long", "f32", "float", "f64", "double");

    private ApiGenerator() {
    }

    /**
     * Генерирует все файлы API.
     *
     * @param def разобранная таблица
     * @return тексты файлов и список пропущенных builtins
     * @throws IllegalArgumentException если для обобщённого builtin нет JVM-реализации какого-либо скаляра либо
     *                                  указана реализация для скаляра вне класса
     */
    public static Output generate(Def def) {
        Map<String, String> files = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        String gpu = genGpu(def, skipped);
        files.put(PKG_API.replace('.', '/') + "/Gpu.java", gpu);
        for (String s : sorted(def.vectorScalars())) {
            for (int lanes : def.vectorLanes()) {
                files.put(PKG_TYPES.replace('.', '/') + "/" + vectorName(s, lanes) + ".java", genVector(s, lanes));
            }
        }
        for (String s : sorted(def.pointerScalars())) {
            files.put(PKG_TYPES.replace('.', '/') + "/" + SCALARS.get(s).cls() + "Ptr.java", genPointer(s));
            for (Space space : Space.values()) {
                files.put(PKG_TYPES.replace('.', '/') + "/" + space.cls + SCALARS.get(s).cls() + "Ptr.java",
                        genSpacePointer(s, space));
            }
        }
        return new Output(files, List.copyOf(skipped));
    }

    /**
     * @param scalar скаляр ({@code f32}, ...)
     * @param lanes  число компонент
     * @return имя Java-класса вектора, например {@code Float4}
     */
    public static String vectorName(String scalar, int lanes) {
        return SCALARS.get(scalar).cls() + lanes;
    }

    /**
     * @param lanes число компонент вектора
     * @param index номер компоненты
     * @return имя поля: {@code x,y,z,w} для векторов до 4 компонент, иначе {@code s0..s9,sA..sF}
     */
    public static String laneName(int lanes, int index) {
        if (lanes <= 4) {
            return String.valueOf("xyzw".charAt(index));
        }
        return "s" + Integer.toHexString(index).toUpperCase();
    }

    private static List<String> sorted(List<String> scalars) {
        List<String> r = new ArrayList<>(scalars);
        r.sort((a, b) -> Integer.compare(SCALAR_ORDER.indexOf(a), SCALAR_ORDER.indexOf(b)));
        return r;
    }

    // ------------------------------------------------------------------------------------------------
    // Векторные типы и ссылки
    // ------------------------------------------------------------------------------------------------

    private static String genVector(String scalar, int lanes) {
        Scalar sc = SCALARS.get(scalar);
        String name = vectorName(scalar, lanes);
        StringBuilder sb = new StringBuilder(HEADER);
        sb.append("package ").append(PKG_TYPES).append(";\n\n");
        sb.append("/**\n * Неизменяемый вектор из ").append(lanes).append(" компонент типа {@code ").append(sc.java())
                .append("} (OpenCL {@code ").append(sc.java()).append(lanes).append("}).\n");
        sb.append(" *\n * <p>Значение: «изменение» вектора - это создание нового (REWORK_ARCHITECTURE.md, 3.2), поэтому ")
                .append("результат на JVM совпадает с GPU по конструкции. Равенство побитовое по компонентам (как у {@code ")
                .append(sc.cls().equals("Int") ? "Integer" : sc.cls()).append(".equals}).");
        if (lanes == 3) {
            sb.append(" В памяти устройства занимает как 4 компоненты (16 байт для float3).");
        }
        sb.append("</p>\n */\n");
        sb.append("public final class ").append(name).append(" {\n");
        for (int i = 0; i < lanes; i++) {
            sb.append("\n    /** Компонента {@code ").append(laneName(lanes, i)).append("} (номер ").append(i).append("). */\n");
            sb.append("    public final ").append(sc.java()).append(' ').append(laneName(lanes, i)).append(";\n");
        }
        sb.append("\n    /**\n     * Создаёт вектор из компонент.\n     *\n");
        for (int i = 0; i < lanes; i++) {
            sb.append("     * @param ").append(laneName(lanes, i)).append(" компонента ").append(i).append('\n');
        }
        sb.append("     */\n    public ").append(name).append('(');
        for (int i = 0; i < lanes; i++) {
            sb.append(i == 0 ? "" : ", ").append(sc.java()).append(' ').append(laneName(lanes, i));
        }
        sb.append(") {\n");
        for (int i = 0; i < lanes; i++) {
            String f = laneName(lanes, i);
            sb.append("        this.").append(f).append(" = ").append(f).append(";\n");
        }
        sb.append("    }\n");

        sb.append("\n    @Override\n    public boolean equals(Object o) {\n");
        sb.append("        if (this == o) {\n            return true;\n        }\n");
        sb.append("        if (!(o instanceof ").append(name).append(" v)) {\n            return false;\n        }\n");
        sb.append("        return ");
        for (int i = 0; i < lanes; i++) {
            String f = laneName(lanes, i);
            sb.append(i == 0 ? "" : "\n                && ").append(sc.eq(f, "v." + f));
        }
        sb.append(";\n    }\n");

        sb.append("\n    @Override\n    public int hashCode() {\n        int h = 1;\n");
        for (int i = 0; i < lanes; i++) {
            sb.append("        h = 31 * h + ").append(sc.hash(laneName(lanes, i))).append(";\n");
        }
        sb.append("        return h;\n    }\n");

        sb.append("\n    @Override\n    public String toString() {\n        return \"").append(name).append("(\"");
        for (int i = 0; i < lanes; i++) {
            sb.append(" + ").append(laneName(lanes, i));
            if (i + 1 < lanes) {
                sb.append(" + \", \"");
            }
        }
        sb.append(" + \")\";\n    }\n");
        genVectorOps(sb, sc, scalar, name, lanes);
        sb.append("}\n");
        return sb.toString();
    }

    /** Размеры векторов результата {@code shuffle}. */
    private static final int[] SHUFFLE_LANES = {2, 3, 4, 8, 16};

    /**
     * Операции вектора (REWORK_ARCHITECTURE.md, 5.20): на JVM это обычные методы, во фронтенде каждый из них
     * соответствует узлу IR ({@code splat, insert, shuffle, add ...}). Java не умеет перегружать операторы, поэтому
     * арифметика - методы; все они покомпонентные и повторяют скалярную семантику Java.
     */
    private static void genVectorOps(StringBuilder sb, Scalar sc, String scalar, String name, int lanes) {
        String t = sc.java();
        boolean integer = scalar.startsWith("i");

        sb.append("\n    /**\n     * Вектор, все компоненты которого равны {@code v} (IR: {@code splat}).\n     *\n")
                .append("     * @param v значение компонент\n     * @return вектор\n     */\n")
                .append("    public static ").append(name).append(" splat(").append(t).append(" v) {\n        return new ")
                .append(name).append('(');
        for (int i = 0; i < lanes; i++) {
            sb.append(i == 0 ? "" : ", ").append('v');
        }
        sb.append(");\n    }\n");

        sb.append("\n    private ").append(t).append(" lane(int i) {\n        switch (i) {\n");
        for (int i = 0; i < lanes; i++) {
            sb.append("            case ").append(i).append(": return ").append(laneName(lanes, i)).append(";\n");
        }
        sb.append("            default: throw new IndexOutOfBoundsException(\"компонента \" + i + \" вне 0..").append(lanes - 1)
                .append("\");\n        }\n    }\n");

        for (int k = 0; k < lanes; k++) {
            String lane = laneName(lanes, k);
            String method = "with" + Character.toUpperCase(lane.charAt(0)) + lane.substring(1);
            sb.append("\n    /**\n     * Копия вектора с изменённой компонентой {@code ").append(lane)
                    .append("} (IR: {@code insert}); сам вектор неизменяем.\n     *\n")
                    .append("     * @param v новое значение компоненты\n     * @return новый вектор\n     */\n")
                    .append("    public ").append(name).append(' ').append(method).append('(').append(t)
                    .append(" v) {\n        return new ").append(name).append('(');
            for (int i = 0; i < lanes; i++) {
                sb.append(i == 0 ? "" : ", ").append(i == k ? "v" : laneName(lanes, i));
            }
            sb.append(");\n    }\n");
        }

        for (int n : SHUFFLE_LANES) {
            String result = vectorName(scalar, n);
            sb.append("\n    /**\n     * Перестановка компонент (IR: {@code shuffle}): компонента позиции {@code k} результата - компонента ")
                    .append("{@code ik} исходного вектора. Номера обязаны быть константами в пределах 0..").append(lanes - 1)
                    .append(".\n     *\n");
            for (int i = 0; i < n; i++) {
                sb.append("     * @param i").append(i).append(" номер компоненты для позиции ").append(i).append('\n');
            }
            sb.append("     * @return вектор из ").append(n).append(" компонент\n     */\n    public ").append(result)
                    .append(" shuffle(");
            for (int i = 0; i < n; i++) {
                sb.append(i == 0 ? "" : ", ").append("int i").append(i);
            }
            sb.append(") {\n        return new ").append(result).append('(');
            for (int i = 0; i < n; i++) {
                sb.append(i == 0 ? "" : ", ").append("lane(i").append(i).append(')');
            }
            sb.append(");\n    }\n");
        }

        String[][] arith = {{"add", "+", "сумма"}, {"sub", "-", "разность"}, {"mul", "*", "произведение"},
                {"div", "/", "частное"}, {"rem", "%", "остаток"}};
        for (String[] op : arith) {
            binary(sb, name, t, lanes, op[0], op[1], op[2]);
        }
        if (integer) {
            String[][] bits = {{"and", "&", "побитовое И"}, {"or", "|", "побитовое ИЛИ"},
                    {"xor", "^", "побитовое исключающее ИЛИ"},
                    {"shl", "<<", "сдвиг влево (счётчик маскируется шириной, как в Java)"},
                    {"shr", ">>", "арифметический сдвиг вправо"}, {"ushr", ">>>", "логический сдвиг вправо"}};
            for (String[] op : bits) {
                binary(sb, name, t, lanes, op[0], op[1], op[2]);
            }
        }
        sb.append("\n    /**\n     * Покомпонентное отрицание (IR: {@code neg}).\n     *\n     * @return новый вектор\n     */\n")
                .append("    public ").append(name).append(" neg() {\n        return new ").append(name).append('(');
        for (int i = 0; i < lanes; i++) {
            sb.append(i == 0 ? "" : ", ").append('-').append(laneName(lanes, i));
        }
        sb.append(");\n    }\n");
        if (integer) {
            sb.append("\n    /**\n     * Покомпонентное побитовое НЕ (IR: {@code not}).\n     *\n     * @return новый вектор\n     */\n")
                    .append("    public ").append(name).append(" not() {\n        return new ").append(name).append('(');
            for (int i = 0; i < lanes; i++) {
                sb.append(i == 0 ? "" : ", ").append('~').append(laneName(lanes, i));
            }
            sb.append(");\n    }\n");
        }
    }

    /** Покомпонентная бинарная операция: с вектором и со скаляром (скаляр размножается, IR: {@code splat}). */
    private static void binary(StringBuilder sb, String name, String t, int lanes, String method, String op, String what) {
        sb.append("\n    /**\n     * Покомпонентно: ").append(what).append(" (IR: {@code ").append(method).append("}).\n     *\n")
                .append("     * @param o второй операнд\n     * @return новый вектор\n     */\n")
                .append("    public ").append(name).append(' ').append(method).append('(').append(name)
                .append(" o) {\n        return new ").append(name).append('(');
        for (int i = 0; i < lanes; i++) {
            String f = laneName(lanes, i);
            sb.append(i == 0 ? "" : ", ").append(f).append(' ').append(op).append(" o.").append(f);
        }
        sb.append(");\n    }\n");
        sb.append("\n    /**\n     * То же со скаляром: он размножается по всем компонентам (IR: {@code splat}).\n     *\n")
                .append("     * @param s второй операнд\n     * @return новый вектор\n     */\n")
                .append("    public ").append(name).append(' ').append(method).append('(').append(t)
                .append(" s) {\n        return new ").append(name).append('(');
        for (int i = 0; i < lanes; i++) {
            sb.append(i == 0 ? "" : ", ").append(laneName(lanes, i)).append(' ').append(op).append(" s");
        }
        sb.append(");\n    }\n");
    }

    private static String genPointer(String scalar) {
        Scalar sc = SCALARS.get(scalar);
        String name = sc.cls() + "Ptr";
        return HEADER + "package " + PKG_TYPES + ";\n\n"
                + "/**\n * Скаляр {@code " + sc.java() + "}, передаваемый по ссылке (OpenCL: указатель на {@code private}-значение).\n"
                + " *\n * <p>Запись в {@link #value} внутри вызываемой device-функции видна вызывающему - и на JVM, и на GPU\n"
                + " * (REWORK_ARCHITECTURE.md, 3.B). Объект изменяемый и сравнивается по ссылке.</p>\n */\n"
                + "public final class " + name + " {\n\n"
                + "    /** Текущее значение. */\n"
                + "    public " + sc.java() + " value;\n\n"
                + "    /** Создаёт ссылку со значением по умолчанию (ноль). */\n"
                + "    public " + name + "() {\n    }\n\n"
                + "    /**\n     * Создаёт ссылку с начальным значением.\n     *\n     * @param value начальное значение\n     */\n"
                + "    public " + name + "(" + sc.java() + " value) {\n        this.value = value;\n    }\n\n"
                + "    @Override\n    public String toString() {\n        return \"" + name + "(\" + value + \")\";\n    }\n}\n";
    }

    /** Пространство адресов явного указателя: часть имени класса, слово OpenCL C и допустимость записи. */
    private enum Space {
        GLOBAL("Global", "global", true, "память устройства, к которой имеют доступ все work-item'ы"),
        LOCAL("Local", "local", true, "локальная память work-group"),
        CONSTANT("Constant", "constant", false, "константная память (только чтение)");

        final String cls;
        final String cl;
        final boolean writable;
        final String what;

        Space(String cls, String cl, boolean writable, String what) {
            this.cls = cls;
            this.cl = cl;
            this.writable = writable;
            this.what = what;
        }
    }

    /**
     * Явный указатель с арифметикой: {@code GlobalFloatPtr}, {@code LocalIntPtr}, {@code ConstantDoublePtr} ... На
     * устройстве это {@code __global float*} и т.п.; на JVM - массив и смещение, поэтому результат совпадает.
     */
    private static String genSpacePointer(String scalar, Space space) {
        Scalar sc = SCALARS.get(scalar);
        String name = space.cls + sc.cls() + "Ptr";
        String t = sc.java();
        StringBuilder sb = new StringBuilder(HEADER);
        sb.append("package ").append(PKG_TYPES).append(";\n\n");
        sb.append("/**\n * Указатель на {@code ").append(t).append("} в пространстве {@code ").append(space.cl)
                .append("}: ").append(space.what).append(".\n *\n");
        sb.append(" * <p>OpenCL: {@code __").append(space.cl).append(' ').append(t).append("*}. Указатель - значение (неизменяемое): ")
                .append("{@link #plus} возвращает новый указатель, исходный не меняется. Длины у указателя нет, ")
                .append("выход за границы массива на устройстве не определён, а на JVM даёт исключение массива ")
                .append("(REWORK_ARCHITECTURE.md, 5.5, 5.21). Создаётся только из массива соответствующего пространства ")
                .append("адресов внутри {@code @Kernel}/{@code @DeviceFunction}.</p>\n */\n");
        sb.append("public final class ").append(name).append(" {\n\n");
        sb.append("    private final ").append(t).append("[] array;\n");
        sb.append("    private final int offset;\n\n");
        sb.append("    private ").append(name).append('(').append(t).append("[] array, int offset) {\n")
                .append("        this.array = array;\n        this.offset = offset;\n    }\n\n");
        sb.append("    /**\n     * Указатель на элемент массива.\n     *\n     * @param array  массив пространства {@code ")
                .append(space.cl).append("}\n     * @param offset индекс элемента, на который указывает результат\n")
                .append("     * @return указатель {@code &array[offset]}\n     */\n");
        sb.append("    public static ").append(name).append(" of(").append(t).append("[] array, int offset) {\n")
                .append("        return new ").append(name).append("(array, offset);\n    }\n\n");
        sb.append("    /**\n     * Читает элемент относительно указателя.\n     *\n     * @param i смещение в элементах\n")
                .append("     * @return {@code p[i]}\n     */\n");
        sb.append("    public ").append(t).append(" get(int i) {\n        return array[offset + i];\n    }\n\n");
        if (space.writable) {
            sb.append("    /**\n     * Записывает элемент относительно указателя.\n     *\n     * @param i     смещение в элементах\n")
                    .append("     * @param value записываемое значение\n     */\n");
            sb.append("    public void set(int i, ").append(t).append(" value) {\n        array[offset + i] = value;\n    }\n\n");
        }
        sb.append("    /**\n     * Сдвигает указатель.\n     *\n     * @param n число элементов\n")
                .append("     * @return указатель {@code p + n}\n     */\n");
        sb.append("    public ").append(name).append(" plus(int n) {\n        return new ").append(name)
                .append("(array, offset + n);\n    }\n\n");
        sb.append("    @Override\n    public String toString() {\n        return \"").append(name)
                .append("(+\" + offset + \")\";\n    }\n}\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------------
    // Gpu
    // ------------------------------------------------------------------------------------------------

    private static String genGpu(Def def, List<String> skipped) {
        StringBuilder methods = new StringBuilder();
        for (Builtin b : def.builtins()) {
            String reason = unsupportedReason(b);
            if (reason != null) {
                skipped.add(b.name());
                continue;
            }
            genBuiltin(def, b, methods);
        }
        StringBuilder sb = new StringBuilder(HEADER);
        sb.append("package ").append(PKG_API).append(";\n\n");
        sb.append("import ").append(PKG_TYPES).append(".*;\n\n");
        sb.append("/**\n * Встроенные функции GPU-кода (builtins). Внутри {@code @Kernel}/{@code @DeviceFunction} методов вызовы ")
                .append("превращаются в узлы IR;\n * вне устройства - это обычные Java-методы, их реализация служит JVM-fallback и ")
                .append("эталоном тестов.\n *\n");
        sb.append(" * <p>Таблица версии ").append(def.version()).append(" генерируется из {@code builtins/builtins.def}; допуски в ULP ")
                .append("для сравнения с GPU заданы там же (REWORK_ARCHITECTURE.md, 8.3).");
        if (!skipped.isEmpty()) {
            sb.append(" Без Java-представления (тип сигнатуры не имеет Java-класса): ").append(String.join(", ", skipped)).append('.');
        }
        sb.append("</p>\n */\n");
        sb.append("public final class Gpu {\n\n    private Gpu() {\n    }\n");
        sb.append(methods);
        sb.append("}\n");
        return sb.toString();
    }

    /** @return причина, по которой builtin не имеет Java-перегрузок, либо {@code null} */
    private static String unsupportedReason(Builtin b) {
        List<String> toks = new ArrayList<>(b.args());
        toks.add(b.ret());
        for (String t : toks) {
            boolean generic = t.equals("T") || t.startsWith("T:") || t.startsWith("ptr<T:");
            if (!generic && !FIXED_JAVA.containsKey(t)) {
                return "тип '" + t + "' без Java-представления";
            }
        }
        return null;
    }

    private static boolean isGeneric(Builtin b) {
        return b.ret().equals("T") || b.args().stream().anyMatch(a -> a.equals("T") || a.startsWith("T:") || a.startsWith("ptr<T:"));
    }

    private static List<String> classScalars(String cls, String builtin) {
        return switch (cls) {
            case "genf" -> List.of("f32", "f64");
            case "geni" -> List.of("i32", "i64");
            case "genn" -> List.of("f32", "f64", "i32", "i64");
            default -> throw new IllegalArgumentException("builtin " + builtin + ": неизвестный класс '" + cls + "'");
        };
    }

    private static void genBuiltin(Def def, Builtin b, StringBuilder out) {
        if (!isGeneric(b)) {
            if (!b.jvm().containsKey("") || b.jvm().size() != 1) {
                throw new IllegalArgumentException("builtin " + b.name() + ": нужна ровно одна jvm-реализация без типов");
            }
            emit(out, b, FIXED_JAVA.get(b.ret()), b.args().stream().map(FIXED_JAVA::get).toList(),
                    body(b.jvm().get("")), false);
            return;
        }
        String cls = null;
        for (String a : b.args()) {
            if (a.startsWith("T:")) {
                cls = a.substring(2);
                break;
            }
            if (a.startsWith("ptr<T:") && a.endsWith(">")) {
                cls = a.substring(6, a.length() - 1);
                break;
            }
        }
        if (cls == null) {
            throw new IllegalArgumentException("builtin " + b.name() + ": обобщённый builtin без привязки 'T:<класс>'");
        }
        List<String> scalars = classScalars(cls, b.name());
        for (String k : b.jvm().keySet()) {
            if (!scalars.contains(k)) {
                throw new IllegalArgumentException("builtin " + b.name() + ": jvm-реализация для '" + k
                        + "' вне класса " + cls);
            }
        }
        for (String s : scalars) {
            if (!b.jvm().containsKey(s)) {
                throw new IllegalArgumentException("builtin " + b.name() + ": нет jvm-реализации для " + s);
            }
        }
        boolean hasPtr = b.args().stream().anyMatch(a -> a.startsWith("ptr<T:"));
        for (String s : scalars) {
            String j = SCALARS.get(s).java();
            // указатель-аргумент: по перегрузке на каждое пространство, в котором атомарные операции допустимы
            for (Space space : hasPtr ? List.of(Space.GLOBAL, Space.LOCAL) : java.util.Arrays.asList((Space) null)) {
                emit(out, b, subst(b.ret(), j, b), b.args().stream().map(a -> a.startsWith("ptr<T:")
                                ? space.cls + SCALARS.get(s).cls() + "Ptr" : subst(a, j, b)).toList(),
                        body(b.jvm().get(s)), false);
            }
        }
        boolean vectorizable = b.ret().equals("T") && !hasPtr;
        if (!vectorizable) {
            return;
        }
        for (String s : scalars) {
            if (!def.vectorScalars().contains(s)) {
                continue;
            }
            for (int lanes : def.vectorLanes()) {
                String v = vectorName(s, lanes);
                List<String> params = b.args().stream().map(a -> subst(a, v, b)).toList();
                StringBuilder body = new StringBuilder("return new " + v + "(");
                for (int i = 0; i < lanes; i++) {
                    body.append(i == 0 ? "" : ",").append(lanes > 4 ? "\n                " : (i == 0 ? "" : " "))
                            .append(b.name()).append('(');
                    for (int k = 0; k < b.args().size(); k++) {
                        boolean typed = b.args().get(k).equals("T") || b.args().get(k).startsWith("T:");
                        body.append(k == 0 ? "" : ", ").append(paramName(k));
                        if (typed) {
                            body.append('.').append(laneName(lanes, i));
                        }
                    }
                    body.append(')');
                }
                body.append(");");
                emit(out, b, v, params, body.toString(), true);
            }
        }
    }

    /** Тело метода: выражение {@code e} превращается в {@code return e;}, текст в фигурных скобках берётся как есть. */
    private static String body(String jvm) {
        String t = jvm.strip();
        if (t.startsWith("{") && t.endsWith("}")) {
            return t.substring(1, t.length() - 1).strip();
        }
        return "return " + t + ";";
    }

    private static String subst(String token, String javaType, Builtin b) {
        if (token.equals("T") || token.startsWith("T:")) {
            return javaType;
        }
        String fixed = FIXED_JAVA.get(token);
        if (fixed == null) {
            throw new IllegalArgumentException("builtin " + b.name() + ": тип '" + token + "' без Java-представления");
        }
        return fixed;
    }

    private static String paramName(int k) {
        return String.valueOf((char) ('a' + k));
    }

    private static final String[] ORDINALS = {"первый", "второй", "третий", "четвёртый"};

    private static void emit(StringBuilder out, Builtin b, String ret, List<String> params, String body, boolean vector) {
        out.append("\n    /**\n     * ").append(b.doc()).append('\n');
        if (!params.isEmpty() || !ret.equals("void")) {
            out.append("     *\n");
        }
        for (int k = 0; k < params.size(); k++) {
            out.append("     * @param ").append(paramName(k)).append(' ').append(ORDINALS[k]).append(" аргумент\n");
        }
        if (!ret.equals("void")) {
            out.append("     * @return результат").append(vector ? " (вектор результатов по компонентам)" : "").append('\n');
        }
        out.append("     */\n    public static ").append(ret).append(' ').append(b.name()).append('(');
        for (int k = 0; k < params.size(); k++) {
            out.append(k == 0 ? "" : ", ").append(params.get(k)).append(' ').append(paramName(k));
        }
        out.append(") {\n        ").append(body).append("\n    }\n");
    }
}
