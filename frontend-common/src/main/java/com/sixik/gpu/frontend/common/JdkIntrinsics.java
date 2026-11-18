package com.sixik.gpu.frontend.common;

import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.Types;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Таблица JDK-интринсиков (FRONTEND_ASM_RUNTIME.md, 18.2-S6, п. 2): методы JDK, которые оба фронтенда переводят в
 * одну операцию IR. Ключ - владелец во внутренней форме, имя и дескриптор метода, как в байткоде; {@code frontend-java}
 * строит тот же ключ по элементу метода, поэтому IR двух фронтендов совпадает.
 *
 * <p>В таблице только <b>побитово точные</b> операции: результат на устройстве и в {@code ir-interpreter} равен
 * результату JVM для любых аргументов. {@code Math.sin}, {@code Math.exp} и подобные в неё не входят: их точность на
 * устройстве - 4 ULP и хуже, а молча подменять семантику Java нельзя (вызов остаётся ошибкой {@code GPU0112}; для
 * приближённых функций есть {@code Gpu.sin} и др.). По той же причине не входят {@code Math.round} (другое правило
 * округления, чем у {@code rint}), {@code Double.doubleToLongBits} (нормализует NaN) и
 * {@code Math.nextAfter(float, double)} (направление в {@code double}).</p>
 *
 * <p>Каждая запись - вызов builtin с флагом {@code jdk} (или прежнего точного builtin: {@code sqrt}, {@code fma},
 * {@code min}, {@code max}), возможно с дописанным константным аргументом ({@code nextUp(x) = nextAfter(x, +inf)}),
 * либо {@code bitcast}. {@code StrictMath} даёт для этих методов тот же результат, что {@code Math}, и входит в
 * таблицу наравне.</p>
 */
public final class JdkIntrinsics {

    /** Дописанного аргумента нет. */
    public static final int EXTRA_NONE = 0;
    /** Дописать константу {@code +∞} типа первого аргумента. */
    public static final int EXTRA_POSITIVE_INFINITY = 1;
    /** Дописать константу {@code -∞} типа первого аргумента. */
    public static final int EXTRA_NEGATIVE_INFINITY = 2;

    /**
     * Запись таблицы.
     *
     * @param owner   владелец во внутренней форме ({@code java/lang/Math})
     * @param name    имя метода
     * @param desc    дескриптор метода
     * @param builtin id builtin либо {@code -1}, если это {@code bitcast}
     * @param bitcast тип результата {@code bitcast} ({@link Types#I64}, ...) либо {@code -1}
     * @param extra   дописанный аргумент: {@link #EXTRA_NONE}, {@link #EXTRA_POSITIVE_INFINITY},
     *                {@link #EXTRA_NEGATIVE_INFINITY}
     */
    public record Intrinsic(String owner, String name, String desc, int builtin, int bitcast, int extra) {

        /** @return {@code true}, если запись - {@code bitcast}, а не вызов builtin */
        public boolean isBitcast() {
            return builtin < 0;
        }

        /** @return показываемое имя: {@code Math.floor} */
        public String shown() {
            return owner.substring(owner.lastIndexOf('/') + 1) + "." + name;
        }
    }

    private static final Map<String, Intrinsic> TABLE = new LinkedHashMap<>();

    static {
        for (String math : new String[]{"java/lang/Math", "java/lang/StrictMath"}) {
            builtin(math, "floor", "floor", "(D)D");
            builtin(math, "ceil", "ceil", "(D)D");
            builtin(math, "rint", "rint", "(D)D");
            builtin(math, "abs", "abs", "(I)I", "(J)J", "(F)F", "(D)D");
            builtin(math, "sqrt", "sqrt", "(D)D");
            builtin(math, "min", "min", "(II)I", "(JJ)J", "(FF)F", "(DD)D");
            builtin(math, "max", "max", "(II)I", "(JJ)J", "(FF)F", "(DD)D");
            builtin(math, "fma", "fma", "(FFF)F", "(DDD)D");
            builtin(math, "copySign", "copySign", "(FF)F", "(DD)D");
            builtin(math, "nextAfter", "nextAfter", "(DD)D");
            add(math, "nextUp", "nextAfter", EXTRA_POSITIVE_INFINITY, "(F)F", "(D)D");
            add(math, "nextDown", "nextAfter", EXTRA_NEGATIVE_INFINITY, "(F)F", "(D)D");
            builtin(math, "signum", "signum", "(F)F", "(D)D");
            builtin(math, "floorDiv", "floorDiv", "(II)I", "(JJ)J");
            builtin(math, "floorMod", "floorMod", "(II)I", "(JJ)J");
            builtin(math, "multiplyHigh", "multiplyHigh", "(JJ)J");
        }
        bitcast("java/lang/Double", "doubleToRawLongBits", "(D)J", Types.I64);
        bitcast("java/lang/Double", "longBitsToDouble", "(J)D", Types.F64);
        bitcast("java/lang/Float", "floatToRawIntBits", "(F)I", Types.I32);
        bitcast("java/lang/Float", "intBitsToFloat", "(I)F", Types.F32);
        for (String[] w : new String[][]{{"java/lang/Integer", "I"}, {"java/lang/Long", "J"}}) {
            String t = w[1];
            builtin(w[0], "rotateLeft", "rotateLeft", "(" + t + "I)" + t);
            builtin(w[0], "rotateRight", "rotateRight", "(" + t + "I)" + t);
            builtin(w[0], "numberOfLeadingZeros", "numberOfLeadingZeros", "(" + t + ")I");
            builtin(w[0], "numberOfTrailingZeros", "numberOfTrailingZeros", "(" + t + ")I");
            builtin(w[0], "bitCount", "bitCount", "(" + t + ")I");
            builtin(w[0], "compareUnsigned", "compareUnsigned", "(" + t + t + ")I");
        }
    }

    private JdkIntrinsics() {
    }

    private static void builtin(String owner, String name, String builtin, String... descs) {
        add(owner, name, builtin, EXTRA_NONE, descs);
    }

    private static void add(String owner, String name, String builtin, int extra, String... descs) {
        int id = Builtins.idOf(builtin);
        if (id < 0) {
            throw new IllegalStateException("builtins.def has no builtin " + builtin);
        }
        for (String d : descs) {
            put(new Intrinsic(owner, name, d, id, -1, extra));
        }
    }

    private static void bitcast(String owner, String name, String desc, int to) {
        put(new Intrinsic(owner, name, desc, -1, to, EXTRA_NONE));
    }

    private static void put(Intrinsic in) {
        if (TABLE.put(key(in.owner(), in.name(), in.desc()), in) != null) {
            throw new IllegalStateException("duplicate JDK intrinsic " + in);
        }
    }

    private static String key(String owner, String name, String desc) {
        return owner + "." + name + desc;
    }

    /**
     * Ищет метод в таблице.
     *
     * @param owner владелец во внутренней форме ({@code java/lang/Math})
     * @param name  имя
     * @param desc  дескриптор
     * @return запись либо {@code null}, если метод не интринсик
     */
    public static Intrinsic find(String owner, String name, String desc) {
        return TABLE.get(key(owner, name, desc));
    }

    /** @return все записи в порядке таблицы (для тестов и документации) */
    public static Map<String, Intrinsic> all() {
        return Collections.unmodifiableMap(TABLE);
    }
}
