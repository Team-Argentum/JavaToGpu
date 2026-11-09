package com.sixik.gpu.test;

import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.runtime.LaunchConfig;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Тестовый образец ядра: откуда взять аргументы, на каком NDRange запускать, что ожидать и как сравнивать
 * (REWORK_ARCHITECTURE.md, 9.6, 9A.2). Один и тот же образец служит {@link GpuAssert#sameResult} сегодня и AutoTune позже.
 *
 * <p>Образец <b>неизменяем</b>; {@link #args()} каждый раз создаёт новые независимые аргументы, потому что ядро
 * изменяет массивы на месте, а ветви (JVM, интерпретатор, устройство) должны получить равные, но не общие данные.</p>
 *
 * <h2>Параметры</h2>
 * <ul>
 *   <li><b>Аргументы</b> - генератор в коде ({@link Builder#args(Supplier)}) либо JSON ({@link #fromJson});</li>
 *   <li><b>Range</b> - размеры запуска; без него - 1D по длине первого массива (как у вызова ядра через фасад);</li>
 *   <li><b>Ожидаемое</b> (необязательно) - значения аргументов-массивов после запуска; {@code null} на месте - не проверять;</li>
 *   <li><b>ULP</b> - допуск вещественных между ветвями; по умолчанию его выбирает {@link GpuAssert} из таблицы builtins;</li>
 *   <li><b>Ветви</b> - какие из {@link Leg} запускать; <b>ignored</b> - аргументы, которые не сравниваются (результат
 *       зависит от порядка work-item-ов, например прежние значения атомика).</li>
 * </ul>
 *
 * <h2>Формат JSON</h2>
 * <pre>{@code
 * {
 *   "name": "scale",
 *   "range": {"global": [1024], "local": [64]},      // local необязателен
 *   "ulps": 2,                                       // необязательно
 *   "legs": ["JVM", "INTERPRETER", "DEVICE"],        // необязательно: по умолчанию все
 *   "ignore": [2],                                   // номера аргументов, которые не сравниваются
 *   "args": [
 *     {"type": "float[]", "values": [1, 2, 3]},
 *     {"type": "float[]", "size": 1024, "fill": "random", "seed": 7},    // fill: zeros | iota | random
 *     {"type": "int", "value": 3}
 *   ],
 *   "expected": [null, {"type": "float[]", "values": [2, 4, 6]}]
 * }
 * }</pre>
 * <p>Типы: скаляры {@code int, long, float, double}; массивы {@code boolean[], byte[], short[], char[], int[], long[],
 * float[], double[]}. Вещественные значения - числа либо строки {@code "NaN"}, {@code "Infinity"}, {@code "-Infinity"}.
 * {@code random} детерминирован по {@code seed} ({@link java.util.Random}): целые - {@code nextInt()}, вещественные -
 * {@code nextFloat() * 2 - 1}.</p>
 */
public final class KernelSample {

    /** Ветви сравнения. */
    public enum Leg {
        /** Оригинальный Java-метод ({@code <имя>$jvm} либо сам метод), work-item-ы по очереди. */
        JVM,
        /** Эталонный интерпретатор IR. */
        INTERPRETER,
        /** Реальное устройство через {@code GpuContext}. */
        DEVICE
    }

    /** Размеры запуска; {@code local = 0} - размер группы не задан. */
    public static final class Range {
        private final int dim;
        private final long[] global;
        private final long[] local;

        private Range(int dim, long[] global, long[] local) {
            this.dim = dim;
            this.global = global;
            this.local = local;
        }

        /** @param x размер @return 1D без размера группы */
        public static Range of1D(long x) {
            return new Range(1, new long[]{x, 1, 1}, new long[3]);
        }

        /** @param x размер @param lx группа @return 1D с размером группы */
        public static Range of1D(long x, long lx) {
            return new Range(1, new long[]{x, 1, 1}, new long[]{lx, 0, 0});
        }

        /** @param x размер по x @param y размер по y @return 2D без размера группы */
        public static Range of2D(long x, long y) {
            return new Range(2, new long[]{x, y, 1}, new long[3]);
        }

        /** @return 2D с размером группы */
        public static Range of2D(long x, long y, long lx, long ly) {
            return new Range(2, new long[]{x, y, 1}, new long[]{lx, ly, 0});
        }

        /** @return 3D без размера группы */
        public static Range of3D(long x, long y, long z) {
            return new Range(3, new long[]{x, y, z}, new long[3]);
        }

        /** @return 3D с размером группы */
        public static Range of3D(long x, long y, long z, long lx, long ly, long lz) {
            return new Range(3, new long[]{x, y, z}, new long[]{lx, ly, lz});
        }

        /** @return размерность 1..3 */
        public int dim() {
            return dim;
        }

        /** @param axis ось @return размер NDRange по оси */
        public long global(int axis) {
            return global[axis];
        }

        /** @param axis ось @return размер группы по оси либо 0 */
        public long local(int axis) {
            return local[axis];
        }

        /** @return то же для интерпретатора (размеры обязаны помещаться в int) */
        NdRange toNdRange() {
            int[] g = new int[3];
            int[] l = new int[3];
            for (int d = 0; d < 3; d++) {
                g[d] = Math.toIntExact(global[d]);
                l[d] = Math.toIntExact(local[d]);
            }
            return switch (dim) {
                case 1 -> l[0] != 0 ? NdRange.of1D(g[0], l[0]) : NdRange.of1D(g[0]);
                case 2 -> l[0] != 0 ? NdRange.of2D(g[0], g[1], l[0], l[1]) : NdRange.of2D(g[0], g[1]);
                default -> l[0] != 0 ? NdRange.of3D(g[0], g[1], g[2], l[0], l[1], l[2]) : NdRange.of3D(g[0], g[1], g[2]);
            };
        }

        /** @return то же для рантайма */
        LaunchConfig toLaunchConfig() {
            return switch (dim) {
                case 1 -> local[0] != 0 ? LaunchConfig.of1D(global[0], local[0]) : LaunchConfig.of1D(global[0]);
                case 2 -> local[0] != 0 ? LaunchConfig.of2D(global[0], global[1], local[0], local[1])
                        : LaunchConfig.of2D(global[0], global[1]);
                default -> local[0] != 0 ? LaunchConfig.of3D(global[0], global[1], global[2], local[0], local[1], local[2])
                        : LaunchConfig.of3D(global[0], global[1], global[2]);
            };
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(dim + "D global=(");
            for (int d = 0; d < dim; d++) {
                sb.append(d == 0 ? "" : ",").append(global[d]);
            }
            sb.append(')');
            if (local[0] != 0) {
                sb.append(" local=(");
                for (int d = 0; d < dim; d++) {
                    sb.append(d == 0 ? "" : ",").append(local[d]);
                }
                sb.append(')');
            }
            return sb.toString();
        }
    }

    private final String name;
    private final Supplier<Object[]> args;
    private final Range range;
    private final Supplier<Object[]> expected;
    private final int ulps;
    private final Set<Leg> legs;
    private final Set<Integer> ignored;

    private KernelSample(Builder b) {
        this.name = b.name;
        this.args = b.args;
        this.range = b.range;
        this.expected = b.expected;
        this.ulps = b.ulps;
        this.legs = Set.copyOf(b.legs);
        this.ignored = Set.copyOf(b.ignored);
    }

    // ---- построение ------------------------------------------------------------------------------

    /**
     * @param name имя образца для сообщений
     * @return построитель
     */
    public static Builder named(String name) {
        return new Builder(name);
    }

    /**
     * Образец из готовых аргументов: массивы копируются при каждом {@link #args()}, поэтому ветви не делят данные.
     *
     * @param name имя образца
     * @param args начальные значения аргументов (массивы примитивов, массивы векторов и {@code @Struct}, скаляры)
     * @return образец с размером запуска по умолчанию
     */
    public static KernelSample of(String name, Object... args) {
        Object[] snapshot = Copies.deepCopy(args);
        return named(name).args(() -> Copies.deepCopy(snapshot)).build();
    }

    /** Построитель {@link KernelSample}. */
    public static final class Builder {
        private final String name;
        private Supplier<Object[]> args;
        private Range range;
        private Supplier<Object[]> expected;
        private int ulps = -1;
        private final Set<Leg> legs = EnumSet.allOf(Leg.class);
        private final Set<Integer> ignored = new TreeSet<>();

        private Builder(String name) {
            this.name = name;
        }

        /**
         * @param args генератор аргументов; вызывается для каждой ветви и обязан возвращать равные, но независимые данные
         * @return этот построитель
         */
        public Builder args(Supplier<Object[]> args) {
            this.args = args;
            return this;
        }

        /**
         * @param range размеры запуска; по умолчанию 1D по длине первого массива-аргумента
         * @return этот построитель
         */
        public Builder range(Range range) {
            this.range = range;
            return this;
        }

        /**
         * Ожидаемые значения аргументов после запуска (позиция - номер аргумента, {@code null} - не проверять).
         * Для групповых ядер, которые нельзя исполнить на JVM, это единственный независимый эталон.
         *
         * @param expected значения; массивы хоста должны быть того же вида, что аргументы
         * @return этот построитель
         */
        public Builder expected(Object... expected) {
            Object[] snapshot = Copies.deepCopy(expected);
            this.expected = () -> Copies.deepCopy(snapshot);
            return this;
        }

        /**
         * @param ulps допустимое расстояние вещественных между ветвями, не меньше 0; без вызова допуск выбирается из таблицы builtins
         * @return этот построитель
         */
        public Builder ulps(int ulps) {
            if (ulps < 0) {
                throw new IllegalArgumentException("ulps must not be negative");
            }
            this.ulps = ulps;
            return this;
        }

        /**
         * @param legs единственные ветви, которые надо запускать
         * @return этот построитель
         */
        public Builder only(Leg... legs) {
            this.legs.clear();
            this.legs.addAll(Arrays.asList(legs));
            return this;
        }

        /**
         * @param legs ветви, которые запускать не надо
         * @return этот построитель
         */
        public Builder without(Leg... legs) {
            this.legs.removeAll(Arrays.asList(legs));
            return this;
        }

        /**
         * @param indexes номера аргументов, исключаемых из сравнения
         * @return этот построитель
         */
        public Builder ignoring(int... indexes) {
            for (int i : indexes) {
                this.ignored.add(i);
            }
            return this;
        }

        /**
         * @return образец
         * @throws IllegalStateException если генератор аргументов не задан
         */
        public KernelSample build() {
            if (args == null) {
                throw new IllegalStateException("sample " + name + ": no argument generator is set");
            }
            return new KernelSample(this);
        }
    }

    // ---- доступ ----------------------------------------------------------------------------------

    /** @return имя образца */
    public String name() {
        return name;
    }

    /** @return новые независимые аргументы */
    public Object[] args() {
        return args.get();
    }

    /**
     * @param first аргументы образца (для выбора размера по умолчанию)
     * @return заданный размер запуска либо 1D по длине первого массива
     */
    Range rangeFor(Object[] first) {
        if (range != null) {
            return range;
        }
        for (Object a : first) {
            if (a != null && a.getClass().isArray()) {
                return Range.of1D(Math.max(1, Array.getLength(a)));
            }
        }
        throw new IllegalStateException("sample " + name + ": launch size is not set and there are no arrays in the arguments");
    }

    /** @return ожидаемые значения либо {@code null} */
    Object[] expected() {
        return expected == null ? null : expected.get();
    }

    /** @return заданный допуск в ULP либо -1 */
    int ulps() {
        return ulps;
    }

    /** @return запускаемые ветви */
    Set<Leg> legs() {
        return legs;
    }

    /** @return номера аргументов, исключённых из сравнения */
    Set<Integer> ignored() {
        return ignored;
    }

    @Override
    public String toString() {
        return "KernelSample[" + name + (range == null ? "" : ", " + range) + "]";
    }

    // ---- JSON ------------------------------------------------------------------------------------

    /**
     * Загружает образец из JSON (формат в описании класса).
     *
     * @param json текст
     * @return образец
     * @throws IllegalArgumentException если JSON некорректен или не соответствует формату
     */
    public static KernelSample fromJson(String json) {
        Object root = Json.parse(json);
        if (!(root instanceof Map<?, ?> top)) {
            throw new IllegalArgumentException("sample: a JSON object was expected");
        }
        String name = top.get("name") instanceof String s ? s : "json";
        List<?> argSpecs = list(top.get("args"), "args");
        // разбор выполняется сразу: ошибки формата видны при загрузке, а не при первом запуске
        for (int i = 0; i < argSpecs.size(); i++) {
            JsonValues.build(argSpecs.get(i), "args[" + i + "]");
        }
        Builder b = named(name).args(() -> {
            Object[] out = new Object[argSpecs.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = JsonValues.build(argSpecs.get(i), "args[" + i + "]");
            }
            return out;
        });
        if (top.get("range") instanceof Map<?, ?> r) {
            b.range(range(r));
        }
        if (top.get("ulps") instanceof Number n) {
            b.ulps(n.intValue());
        }
        if (top.get("legs") != null) {
            Leg[] only = list(top.get("legs"), "legs").stream().map(x -> Leg.valueOf(String.valueOf(x))).toArray(Leg[]::new);
            b.only(only);
        }
        if (top.get("ignore") != null) {
            b.ignoring(list(top.get("ignore"), "ignore").stream().mapToInt(x -> ((Number) x).intValue()).toArray());
        }
        if (top.get("expected") != null) {
            List<?> specs = list(top.get("expected"), "expected");
            Object[] built = new Object[specs.size()];
            for (int i = 0; i < built.length; i++) {
                built[i] = specs.get(i) == null ? null : JsonValues.build(specs.get(i), "expected[" + i + "]");
            }
            b.expected(built);
        }
        return b.build();
    }

    /**
     * Загружает образец из файла JSON (UTF-8).
     *
     * @param file файл
     * @return образец
     * @throws IOException если файл не читается
     */
    public static KernelSample loadJson(Path file) throws IOException {
        return fromJson(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static List<?> list(Object o, String what) {
        if (o instanceof List<?> l) {
            return l;
        }
        throw new IllegalArgumentException("sample: '" + what + "' must be an array");
    }

    private static Range range(Map<?, ?> r) {
        List<?> g = list(r.get("global"), "range.global");
        List<?> l = r.get("local") == null ? List.of() : list(r.get("local"), "range.local");
        if (g.isEmpty() || g.size() > 3 || (!l.isEmpty() && l.size() != g.size())) {
            throw new IllegalArgumentException("range: global of 1-3 numbers, local of the same length or empty");
        }
        long[] gl = new long[3];
        long[] lo = new long[3];
        Arrays.fill(gl, 1);
        for (int i = 0; i < g.size(); i++) {
            gl[i] = ((Number) g.get(i)).longValue();
            lo[i] = l.isEmpty() ? 0 : ((Number) l.get(i)).longValue();
        }
        return switch (g.size()) {
            case 1 -> lo[0] == 0 ? Range.of1D(gl[0]) : Range.of1D(gl[0], lo[0]);
            case 2 -> lo[0] == 0 ? Range.of2D(gl[0], gl[1]) : Range.of2D(gl[0], gl[1], lo[0], lo[1]);
            default -> lo[0] == 0 ? Range.of3D(gl[0], gl[1], gl[2]) : Range.of3D(gl[0], gl[1], gl[2], lo[0], lo[1], lo[2]);
        };
    }

    /** Построение значений аргументов из описаний JSON. */
    private static final class JsonValues {

        private JsonValues() {
        }

        static Object build(Object spec, String where) {
            if (!(spec instanceof Map<?, ?> m) || !(m.get("type") instanceof String type)) {
                throw new IllegalArgumentException(where + ": an object with a type field was expected");
            }
            if (!type.endsWith("[]")) {
                return scalar(type, m.get("value"), where);
            }
            String elem = type.substring(0, type.length() - 2);
            if (m.get("values") instanceof List<?> values) {
                return array(elem, values.size(), i -> values.get(i), where);
            }
            if (!(m.get("size") instanceof Number size) || size.longValue() < 0 || size.longValue() > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(where + ": values or size is required");
            }
            String fill = m.get("fill") instanceof String f ? f : "zeros";
            Random rnd = new Random(m.get("seed") instanceof Number s ? s.longValue() : 0L);
            return array(elem, size.intValue(), i -> switch (fill) {
                case "zeros" -> 0L;
                case "iota" -> (long) i;
                case "random" -> isFloating(elem) ? (Object) (rnd.nextDouble() * 2 - 1)
                        : (Object) (elem.equals("long") ? rnd.nextLong() : (long) rnd.nextInt());
                default -> throw new IllegalArgumentException(where + ": unknown fill '" + fill + "'");
            }, where);
        }

        private static boolean isFloating(String elem) {
            return elem.equals("float") || elem.equals("double");
        }

        private static Object scalar(String type, Object v, String where) {
            return switch (type) {
                case "int" -> (int) integral(v, where);
                case "long" -> integral(v, where);
                case "float" -> (float) floating(v, where);
                case "double" -> floating(v, where);
                default -> throw new IllegalArgumentException(where + ": unknown scalar type '" + type + "'");
            };
        }

        private static long integral(Object v, String where) {
            if (v instanceof Number n && !(v instanceof Double)) {
                return n.longValue();
            }
            if (v instanceof Double d && d == Math.rint(d)) {
                return d.longValue();
            }
            throw new IllegalArgumentException(where + ": expected an integer, got " + v);
        }

        private static double floating(Object v, String where) {
            if (v instanceof Number n) {
                return n.doubleValue();
            }
            if (v instanceof String s) {
                return switch (s) {
                    case "NaN" -> Double.NaN;
                    case "Infinity" -> Double.POSITIVE_INFINITY;
                    case "-Infinity" -> Double.NEGATIVE_INFINITY;
                    default -> throw new IllegalArgumentException(where + ": unknown floating value '" + s + "'");
                };
            }
            throw new IllegalArgumentException(where + ": expected a number, got " + v);
        }

        private static Object array(String elem, int n, java.util.function.IntFunction<Object> at, String where) {
            switch (elem) {
                case "int": {
                    int[] a = new int[n];
                    for (int i = 0; i < n; i++) {
                        a[i] = (int) integral(at.apply(i), where + "[" + i + "]");
                    }
                    return a;
                }
                case "long": {
                    long[] a = new long[n];
                    for (int i = 0; i < n; i++) {
                        a[i] = integral(at.apply(i), where + "[" + i + "]");
                    }
                    return a;
                }
                case "float": {
                    float[] a = new float[n];
                    for (int i = 0; i < n; i++) {
                        a[i] = (float) floating(at.apply(i), where + "[" + i + "]");
                    }
                    return a;
                }
                case "double": {
                    double[] a = new double[n];
                    for (int i = 0; i < n; i++) {
                        a[i] = floating(at.apply(i), where + "[" + i + "]");
                    }
                    return a;
                }
                case "byte": {
                    byte[] a = new byte[n];
                    for (int i = 0; i < n; i++) {
                        a[i] = (byte) integral(at.apply(i), where + "[" + i + "]");
                    }
                    return a;
                }
                case "short": {
                    short[] a = new short[n];
                    for (int i = 0; i < n; i++) {
                        a[i] = (short) integral(at.apply(i), where + "[" + i + "]");
                    }
                    return a;
                }
                case "char": {
                    char[] a = new char[n];
                    for (int i = 0; i < n; i++) {
                        a[i] = (char) integral(at.apply(i), where + "[" + i + "]");
                    }
                    return a;
                }
                case "boolean": {
                    boolean[] a = new boolean[n];
                    for (int i = 0; i < n; i++) {
                        Object v = at.apply(i);
                        a[i] = v instanceof Boolean bool ? bool : integral(v, where + "[" + i + "]") != 0;
                    }
                    return a;
                }
                default:
                    throw new IllegalArgumentException(where + ": unknown array type '" + elem + "[]'");
            }
        }
    }
}
