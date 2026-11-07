package com.sixik.gpu.ir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Таблица builtins: чтение декларативного файла {@code builtins.def} и проверка сигнатур вызовов
 * (REWORK_ARCHITECTURE.md, 5.7).
 *
 * <p>Таблица неизменяема и строится один раз при загрузке класса из ресурса модуля. Узел
 * {@link Op#INTRINSIC} ссылается на builtin числовым id; версия таблицы пишется в заголовок бинарного IR
 * ({@link #VERSION}), и несовпадение версий отвергается правилом {@code IR-S05}.</p>
 */
public final class Builtins {

    /** Флаг: без побочных эффектов. */
    public static final int PURE = 1;
    /** Флаг: имеет побочный эффект. */
    public static final int EFFECT = 1 << 1;
    /** Флаг: читает память. */
    public static final int READS = 1 << 2;
    /** Флаг: результат различается между work-item'ами одной группы. */
    public static final int DIV = 1 << 3;
    /** Флаг: результат считается одинаковым внутри группы (снимает taint). */
    public static final int UNIFORM = 1 << 4;
    /** Флаг: атомарная операция. */
    public static final int ATOMIC = 1 << 5;

    // Кодирование токенов сигнатуры.
    private static final int TOK_BIND = 100;   // 100 + класс: связывает T
    private static final int TOK_REF = 200;    // ссылка на связанный T
    private static final int TOK_PTR = 300;    // 300 + класс: указатель на скаляр, связывает T

    private static final int CLASS_FLOAT = 0;
    private static final int CLASS_INT = 1;
    private static final int CLASS_NUM = 2;

    /** Версия таблицы builtins, по которой собирается и проверяется IR. */
    public static final int VERSION;

    private static final String[] NAMES;
    private static final int[] FLAGS;
    private static final int[] ULP;
    private static final int[][] RET;
    private static final int[][] ARGS;
    private static final int COUNT;

    static {
        int version = -1;
        List<String[]> rows = new ArrayList<>();
        try (InputStream in = Builtins.class.getResourceAsStream("builtins.def")) {
            if (in == null) {
                throw new IllegalStateException("builtins.def не найден в ресурсах модуля ir");
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String raw : text.split("\\R")) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("version")) {
                    version = Integer.parseInt(line.substring("version".length()).strip());
                    continue;
                }
                rows.add(line.split("\\s+", 4));
            }
        } catch (IOException e) {
            throw new IllegalStateException("не удалось прочитать builtins.def", e);
        }
        if (version < 0) {
            throw new IllegalStateException("в builtins.def нет строки version");
        }
        VERSION = version;

        int max = -1;
        for (String[] r : rows) {
            max = Math.max(max, Integer.parseInt(r[0]));
        }
        COUNT = max + 1;
        NAMES = new String[COUNT];
        FLAGS = new int[COUNT];
        ULP = new int[COUNT];
        RET = new int[COUNT][];
        ARGS = new int[COUNT][];
        for (String[] r : rows) {
            int id = Integer.parseInt(r[0]);
            if (NAMES[id] != null) {
                throw new IllegalStateException("builtins.def: повторный id " + id);
            }
            NAMES[id] = r[1];
            int flags = 0;
            int ulp = -1;
            for (String f : r[2].split(",")) {
                if (f.startsWith("ulp=")) {
                    ulp = Integer.parseInt(f.substring(4));
                } else {
                    flags |= flagBit(f, id);
                }
            }
            FLAGS[id] = flags;
            ULP[id] = ulp;
            String sig = r[3].strip();
            int open = sig.indexOf('(');
            int close = sig.lastIndexOf(')');
            if (open < 0 || close < open) {
                throw new IllegalStateException("builtins.def: плохая сигнатура у id " + id);
            }
            RET[id] = new int[]{token(sig.substring(0, open).strip(), id)};
            String inner = sig.substring(open + 1, close).strip();
            if (inner.isEmpty()) {
                ARGS[id] = new int[0];
            } else {
                String[] parts = inner.split(",");
                int[] toks = new int[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    toks[i] = token(parts[i].strip(), id);
                }
                ARGS[id] = toks;
            }
        }
    }

    private Builtins() {
    }

    private static int flagBit(String f, int id) {
        return switch (f) {
            case "pure" -> PURE;
            case "effect" -> EFFECT;
            case "reads" -> READS;
            case "div" -> DIV;
            case "uniform" -> UNIFORM;
            case "atomic" -> ATOMIC;
            default -> throw new IllegalStateException("builtins.def: неизвестный флаг '" + f + "' у id " + id);
        };
    }

    private static int cls(String name, int id) {
        return switch (name) {
            case "genf" -> CLASS_FLOAT;
            case "geni" -> CLASS_INT;
            case "genn" -> CLASS_NUM;
            default -> throw new IllegalStateException("builtins.def: неизвестный класс '" + name + "' у id " + id);
        };
    }

    private static int token(String s, int id) {
        if (s.equals("T")) {
            return TOK_REF;
        }
        if (s.startsWith("T:")) {
            return TOK_BIND + cls(s.substring(2), id);
        }
        if (s.startsWith("ptr<T:") && s.endsWith(">")) {
            return TOK_PTR + cls(s.substring(6, s.length() - 1), id);
        }
        return switch (s) {
            case "void" -> Types.VOID;
            case "bool" -> Types.BOOL;
            case "i8" -> Types.I8;
            case "u8" -> Types.U8;
            case "i16" -> Types.I16;
            case "u16" -> Types.U16;
            case "i32" -> Types.I32;
            case "u32" -> Types.U32;
            case "i64" -> Types.I64;
            case "u64" -> Types.U64;
            case "f32" -> Types.F32;
            case "f64" -> Types.F64;
            default -> throw new IllegalStateException("builtins.def: неизвестный тип '" + s + "' у id " + id);
        };
    }

    /**
     * @return число id в таблице (максимальный id + 1; некоторые id могут быть не заняты)
     */
    public static int count() {
        return COUNT;
    }

    /**
     * @param id id builtin
     * @return {@code true}, если id занят в таблице
     */
    public static boolean isValid(int id) {
        return id >= 0 && id < COUNT && NAMES[id] != null;
    }

    /**
     * @param id id builtin
     * @return имя builtin в Java-API
     */
    public static String name(int id) {
        return NAMES[id];
    }

    /**
     * @param id id builtin
     * @return битовая маска флагов ({@link #PURE}, {@link #EFFECT}, ...)
     */
    public static int flags(int id) {
        return FLAGS[id];
    }

    /**
     * @param id   id builtin
     * @param flag один из флагов
     * @return {@code true}, если флаг установлен
     */
    public static boolean has(int id, int flag) {
        return (FLAGS[id] & flag) != 0;
    }

    /**
     * Допустимая погрешность результата в ULP для дифференциальных тестов (REWORK_ARCHITECTURE.md, 8.3).
     *
     * @param id id builtin
     * @return число ULP либо {@code -1}, если погрешность не задана (сравнивать точно)
     */
    public static int ulp(int id) {
        return ULP[id];
    }

    /**
     * @param id id builtin
     * @return число аргументов
     */
    public static int argCount(int id) {
        return ARGS[id].length;
    }

    /**
     * Ищет id по имени.
     *
     * @param name имя builtin
     * @return id либо {@code -1}
     */
    public static int idOf(String name) {
        for (int i = 0; i < COUNT; i++) {
            if (name.equals(NAMES[i])) {
                return i;
            }
        }
        return -1;
    }

    private static boolean inClass(int scalar, int cls) {
        return switch (cls) {
            case CLASS_FLOAT -> Types.isFloat(scalar);
            case CLASS_INT -> Types.isInt32or64(scalar);
            default -> Types.isArith(scalar);
        };
    }

    /**
     * Проверяет аргументы вызова builtin по его сигнатуре и вычисляет тип результата.
     *
     * @param m        модуль (нужен для разбора векторных и указательных типов)
     * @param id       id builtin
     * @param argTypes типы аргументов
     * @param n        число аргументов (используются первые {@code n} элементов {@code argTypes})
     * @return id типа результата либо {@code -1}, если число или типы аргументов не соответствуют сигнатуре
     */
    public static int resultType(IrModule m, int id, int[] argTypes, int n) {
        if (!isValid(id)) {
            return -1;
        }
        int[] sig = ARGS[id];
        if (sig.length != n) {
            return -1;
        }
        int bound = -1;
        for (int i = 0; i < n; i++) {
            int tok = sig[i];
            int t = argTypes[i];
            if (tok >= TOK_PTR) {
                if (m.typeKind(t) != Types.KIND_PTR) {
                    return -1;
                }
                int space = m.typeA(t);
                int pointee = m.typeB(t);
                if ((space != AddressSpace.GLOBAL && space != AddressSpace.LOCAL)
                        || !Types.isScalar(pointee) || !inClass(pointee, tok - TOK_PTR)) {
                    return -1;
                }
                if (bound == -1) {
                    bound = pointee;
                } else if (bound != pointee) {
                    return -1;
                }
            } else if (tok >= TOK_REF) {
                if (bound == -1 || t != bound) {
                    return -1;
                }
            } else if (tok >= TOK_BIND) {
                int s = Types.scalarOf(m, t);
                if (s < 0 || !inClass(s, tok - TOK_BIND)) {
                    return -1;
                }
                if (bound == -1) {
                    bound = t;
                } else if (bound != t) {
                    return -1;
                }
            } else if (t != tok) {
                return -1;
            }
        }
        int ret = RET[id][0];
        if (ret >= TOK_REF) {
            return bound;
        }
        return ret;
    }
}
