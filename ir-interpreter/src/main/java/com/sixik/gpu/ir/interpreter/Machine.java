package com.sixik.gpu.ir.interpreter;

import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.HostObjects;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

import java.util.Arrays;

/**
 * Исполнитель IR: обходит таблицу узлов одной функции-ядра для каждого work-item виртуального NDRange.
 *
 * <p><b>Модель данных (плоская).</b> Всё состояние - примитивные массивы:</p>
 * <ul>
 *   <li>{@code stack} - стек значений: кадр функции = параметры, затем локали; значение - {@code long} в каноническом
 *       виде ({@link Alu});</li>
 *   <li>{@code bufs} - таблица буферов памяти, буфер = {@code long[]} канонических значений элементов. Хостовые
 *       массивы занимают первые {@code hostCount} строк, private-массивы work-item'а - следующие (освобождаются на
 *       выходе из функции). Значение типа {@code Array} = номер буфера, значение типа {@code Ptr} =
 *       {@code (буфер << 32) | индекс элемента}.</li>
 * </ul>
 *
 * <p><b>Ошибки.</b> Нарушение времени выполнения (выход за границы, деление на ноль, исчерпание бюджета шагов) -
 * не исключение: первое нарушение запоминается, исполнение немедленно прекращается через код возврата
 * {@link #FAULT} стейтментов, а значения выражений после нарушения не используются (все обращения к памяти проверяются
 * сами, поэтому «мусорное» значение не может привести к падению JVM).</p>
 *
 * <p><b>Предусловие:</b> модуль прошёл {@code Validator} без ошибок и {@link Interpreter} проверил, что в достижимых
 * из ядра функциях нет узлов, которые интерпретатор пока не умеет исполнять.</p>
 */
final class Machine {

    // ---- коды завершения стейтмента -------------------------------------------------------------
    private static final int NORMAL = 0;
    private static final int BREAK = 1;
    private static final int CONTINUE = 2;
    private static final int RETURN = 3;
    private static final int FAULT = 4;

    /** Максимум элементов private-массива: защита от «мусорной» длины. */
    private static final int MAX_PRIVATE_ARRAY = 1 << 22;

    // ---- коды builtins, исполняемых интерпретатором ---------------------------------------------
    private static final int B_NONE = 0;
    private static final int B_GLOBAL_ID = 1;
    private static final int B_LOCAL_ID = 2;
    private static final int B_GROUP_ID = 3;
    private static final int B_GLOBAL_SIZE = 4;
    private static final int B_LOCAL_SIZE = 5;
    private static final int B_NUM_GROUPS = 6;
    private static final int B_WORK_DIM = 7;
    private static final int B_SIN = 8;
    private static final int B_COS = 9;
    private static final int B_SQRT = 10;
    private static final int B_FMA = 11;
    private static final int B_MAX = 12;
    private static final int B_MIN = 13;
    private static final int B_ASSUME_UNIFORM = 14;
    private static final int B_ATOMIC_ADD = 15;

    /** Соответствие «id builtin -> код исполнения»; {@link #B_NONE}, если интерпретатор builtin не знает. */
    private static final int[] BUILTIN_CODE;

    static {
        BUILTIN_CODE = new int[Builtins.count()];
        for (int id = 0; id < BUILTIN_CODE.length; id++) {
            if (!Builtins.isValid(id)) {
                continue;
            }
            BUILTIN_CODE[id] = switch (Builtins.name(id)) {
                case "getGlobalId" -> B_GLOBAL_ID;
                case "getLocalId" -> B_LOCAL_ID;
                case "getGroupId" -> B_GROUP_ID;
                case "getGlobalSize" -> B_GLOBAL_SIZE;
                case "getLocalSize" -> B_LOCAL_SIZE;
                case "getNumGroups" -> B_NUM_GROUPS;
                case "getWorkDim" -> B_WORK_DIM;
                case "sin" -> B_SIN;
                case "cos" -> B_COS;
                case "sqrt" -> B_SQRT;
                case "fma" -> B_FMA;
                case "max" -> B_MAX;
                case "min" -> B_MIN;
                case "assumeUniform" -> B_ASSUME_UNIFORM;
                case "atomicAdd" -> B_ATOMIC_ADD;
                default -> B_NONE;
            };
        }
    }

    /**
     * @param builtinId id builtin из таблицы {@link Builtins}
     * @return {@code true}, если интерпретатор умеет исполнять этот builtin
     */
    static boolean supportsBuiltin(int builtinId) {
        return builtinId >= 0 && builtinId < BUILTIN_CODE.length && BUILTIN_CODE[builtinId] != B_NONE;
    }

    // ---- модуль и предвычисленные таблицы ---------------------------------------------------------
    private final IrModule m;
    private final int[][] paramSlot;   // по функции: смещение параметра в кадре (агрегат занимает несколько ячеек)
    private final int[][] localSlot;   // по функции: смещение локали в кадре
    private final int[] frameSize;     // по функции: параметры + локали
    private final int[][] arrayLocals; // по функции: индексы локалей типа Array
    private final int[] typeSlots;     // по типу: число ячеек значения (скаляр 1, вектор - лейны, struct - сумма листьев)
    private final boolean[] typeAgg;   // по типу: вектор или struct
    private final int[][] fieldSlot;   // по struct-типу: смещение поля в ячейках

    // ---- состояние исполнения ---------------------------------------------------------------------
    private long[] stack = new long[256];
    private long[][] bufs;
    private int[] bufElem;            // по буферу: ячеек на элемент (1 для скаляров)
    private int bufCount;
    private final int hostCount;
    private int top;
    private int base;
    private int cur;
    private long steps;
    private final long limit;
    private long ret;
    private long[] retAgg;

    // ---- геометрия запуска ------------------------------------------------------------------------
    private final int workDim;
    private final int[] gsz = new int[3];
    private final int[] lsz = new int[3];
    private final int[] ngr = new int[3];
    private final int[] gid = new int[3];
    private final int[] lid = new int[3];
    private final int[] grp = new int[3];

    // ---- первое нарушение -------------------------------------------------------------------------
    private String faultCode;
    private String faultMessage;
    private int faultFunction = -1;
    private int faultNode = -1;

    /**
     * @param m         проверенный модуль
     * @param hostBufs  буферы хостовых массивов (каноническое представление элементов); массивы не копируются
     * @param workDim   размерность запуска
     * @param global    размеры NDRange по трём осям
     * @param local     размеры work-group по трём осям (уже разрешённые, делят {@code global})
     * @param maxSteps  бюджет шагов на весь запуск либо {@code 0} - без ограничения
     */
    Machine(IrModule m, long[][] hostBufs, int[] hostElem, int workDim, int[] global, int[] local, long maxSteps) {
        this.m = m;
        int tc = m.typeCount();
        this.typeSlots = new int[tc];
        this.typeAgg = new boolean[tc];
        this.fieldSlot = new int[tc][];
        for (int t = 0; t < tc; t++) {
            int kind = m.typeKind(t);
            if (kind == Types.KIND_ARRAY || kind == Types.KIND_PTR) {
                typeSlots[t] = 1;
            } else {
                typeSlots[t] = HostObjects.slots(m, t);
            }
            typeAgg[t] = HostObjects.isAggregate(m, t);
            if (kind == Types.KIND_STRUCT) {
                int[] off = new int[m.structFieldCount(t)];
                for (int i = 0; i < off.length; i++) {
                    off[i] = HostObjects.slotOffset(m, t, i);
                }
                fieldSlot[t] = off;
            }
        }
        int fc = m.functionCount();
        this.paramSlot = new int[fc][];
        this.localSlot = new int[fc][];
        this.frameSize = new int[fc];
        this.arrayLocals = new int[fc][];
        for (int f = 0; f < fc; f++) {
            int[] ps = paramOffsets(m, f);
            paramSlot[f] = ps;
            int at = frameSlots(m, f, ps);
            int[] ls = new int[m.fLocalCount(f)];
            int cnt = 0;
            for (int l = 0; l < ls.length; l++) {
                ls[l] = at;
                at += Math.max(1, typeSlots[m.fLocalType(f, l)]);
                if (m.typeKind(m.fLocalType(f, l)) == Types.KIND_ARRAY) {
                    cnt++;
                }
            }
            localSlot[f] = ls;
            frameSize[f] = at;
            int[] arrs = new int[cnt];
            int k = 0;
            for (int l = 0; l < ls.length; l++) {
                if (m.typeKind(m.fLocalType(f, l)) == Types.KIND_ARRAY) {
                    arrs[k++] = l;
                }
            }
            arrayLocals[f] = arrs;
        }
        this.hostCount = hostBufs.length;
        this.bufs = Arrays.copyOf(hostBufs, Math.max(16, hostBufs.length + 8));
        this.bufElem = new int[bufs.length];
        Arrays.fill(bufElem, 1);
        System.arraycopy(hostElem, 0, bufElem, 0, hostElem.length);
        this.bufCount = hostCount;
        this.workDim = workDim;
        for (int d = 0; d < 3; d++) {
            gsz[d] = global[d];
            lsz[d] = local[d];
            ngr[d] = global[d] / local[d];
        }
        this.limit = maxSteps > 0 ? maxSteps : Long.MAX_VALUE;
    }

    /**
     * Смещения параметров функции в кадре: скаляр и массив занимают ячейку, вектор и struct - по числу листьев.
     *
     * @param m модуль
     * @param f функция
     * @return смещение каждого параметра
     */
    static int[] paramOffsets(IrModule m, int f) {
        int[] ps = new int[m.fParamCount(f)];
        int at = 0;
        for (int i = 0; i < ps.length; i++) {
            ps[i] = at;
            at += paramWidth(m, m.fParamType(f, i));
        }
        return ps;
    }

    /**
     * @param m  модуль
     * @param f  функция
     * @param ps результат {@link #paramOffsets}
     * @return число ячеек, занятых параметрами
     */
    static int frameSlots(IrModule m, int f, int[] ps) {
        int n = m.fParamCount(f);
        return n == 0 ? 0 : ps[n - 1] + paramWidth(m, m.fParamType(f, n - 1));
    }

    private static int paramWidth(IrModule m, int t) {
        return HostObjects.isAggregate(m, t) ? HostObjects.slots(m, t) : 1;
    }

    /** @return число выполненных стейтментов */
    long steps() {
        return steps;
    }

    private boolean faulted() {
        return faultCode != null;
    }

    private long fault(String code, int node, String message) {
        if (faultCode == null) {
            faultCode = code;
            faultMessage = message;
            faultFunction = cur;
            faultNode = node;
        }
        return 0;
    }

    // =============================================================================================
    // запуск
    // =============================================================================================

    /**
     * Исполняет ядро для всех work-item'ов NDRange по порядку {@code z, y, x} (x - самая быстрая ось).
     *
     * @param entry индекс функции-ядра
     * @param argv  значения параметров в каноническом виде (скаляры) либо номера буферов (массивы)
     * @return {@code null}, если все work-item'ы завершились; иначе первое нарушение
     */
    Diagnostic run(int entry, long[] argv) {
        int body = m.fBody(entry);
        for (int z = 0; z < gsz[2]; z++) {
            for (int y = 0; y < gsz[1]; y++) {
                for (int x = 0; x < gsz[0]; x++) {
                    gid[0] = x;
                    gid[1] = y;
                    gid[2] = z;
                    for (int d = 0; d < 3; d++) {
                        lid[d] = gid[d] % lsz[d];
                        grp[d] = gid[d] / lsz[d];
                    }
                    bufCount = hostCount;
                    cur = entry;
                    base = 0;
                    top = frameSize[entry];
                    ensureStack(top);
                    System.arraycopy(argv, 0, stack, 0, argv.length);
                    initArrayLocals(entry, 0);
                    if (exec(body) == FAULT) {
                        return diagnostic();
                    }
                }
            }
        }
        return null;
    }

    private Diagnostic diagnostic() {
        String where = " [work-item global=(" + gid[0] + "," + gid[1] + "," + gid[2] + ")]";
        return new Diagnostic(faultCode, Diagnostic.Severity.ERROR, faultFunction, faultNode, faultMessage + where);
    }

    private void ensureStack(int need) {
        if (need > stack.length) {
            stack = Arrays.copyOf(stack, Math.max(need, stack.length * 2));
        }
    }

    private void initArrayLocals(int f, int frameBase) {
        int[] arrs = arrayLocals[f];
        for (int l : arrs) {
            stack[frameBase + localSlot[f][l]] = -1;
        }
    }

    private int slot(int local) {
        return base + localSlot[cur][local];
    }

    // =============================================================================================
    // стейтменты
    // =============================================================================================

    private int exec(int n) {
        if (++steps > limit) {
            fault("IN-R03", n, "исчерпан бюджет шагов (" + limit + "): возможно, бесконечный цикл");
            return FAULT;
        }
        switch (m.op(n)) {
            case Op.BLOCK: {
                int off = m.a(n);
                int len = m.b(n);
                for (int i = 0; i < len; i++) {
                    int st = exec(m.list(off + i));
                    if (st != NORMAL) {
                        return st;
                    }
                }
                return NORMAL;
            }
            case Op.LOCAL_DECL:
            case Op.LOCAL_SET: {
                if (typeAgg[m.fLocalType(cur, m.a(n))]) {
                    long[] v = evalAgg(m.b(n));
                    if (faulted()) {
                        return FAULT;
                    }
                    System.arraycopy(v, 0, stack, slot(m.a(n)), v.length);
                    return NORMAL;
                }
                long v = eval(m.b(n));
                if (faulted()) {
                    return FAULT;
                }
                stack[slot(m.a(n))] = v;
                return NORMAL;
            }
            case Op.STORE: {
                long p = eval(m.a(n));
                if (typeAgg[m.type(m.b(n))]) {
                    long[] v = evalAgg(m.b(n));
                    if (faulted()) {
                        return FAULT;
                    }
                    return storeAgg(n, p, v) ? NORMAL : FAULT;
                }
                long v = eval(m.b(n));
                if (faulted()) {
                    return FAULT;
                }
                return store(n, p, v) ? NORMAL : FAULT;
            }
            case Op.EXPR_STMT:
                eval(m.a(n));
                return faulted() ? FAULT : NORMAL;
            case Op.IF: {
                long c = eval(m.a(n));
                if (faulted()) {
                    return FAULT;
                }
                if (c != 0) {
                    return exec(m.b(n));
                }
                int otherwise = m.c(n);
                return otherwise >= 0 ? exec(otherwise) : NORMAL;
            }
            case Op.LOOP:
                return execLoop(n);
            case Op.DO_WHILE:
                return execDoWhile(n);
            case Op.SWITCH:
                return execSwitch(n);
            case Op.BREAK:
                return BREAK;
            case Op.CONTINUE:
                return CONTINUE;
            case Op.RETURN: {
                int value = m.a(n);
                if (value >= 0 && typeAgg[m.type(value)]) {
                    retAgg = evalAgg(value);
                } else {
                    ret = value >= 0 ? eval(value) : 0;
                }
                return faulted() ? FAULT : RETURN;
            }
            case Op.PRIVATE_ARRAY_DECL:
                return execPrivateArray(n) ? NORMAL : FAULT;
            default:
                fault("IN-U01", n, "узел " + Op.nameOf(m.op(n)) + " не поддерживается интерпретатором");
                return FAULT;
        }
    }

    private int execLoop(int n) {
        int cond = m.a(n);
        int body = m.b(n);
        int update = m.c(n);
        while (true) {
            long c = eval(cond);
            if (faulted()) {
                return FAULT;
            }
            if (c == 0) {
                return NORMAL;
            }
            int st = exec(body);
            if (st == BREAK) {
                return NORMAL;
            }
            if (st == RETURN || st == FAULT) {
                return st;
            }
            // NORMAL либо CONTINUE: continue ведёт в update
            if (update >= 0) {
                st = exec(update);
                if (st == RETURN || st == FAULT) {
                    return st;
                }
            }
        }
    }

    private int execDoWhile(int n) {
        int body = m.a(n);
        int cond = m.b(n);
        while (true) {
            int st = exec(body);
            if (st == BREAK) {
                return NORMAL;
            }
            if (st == RETURN || st == FAULT) {
                return st;
            }
            // NORMAL либо CONTINUE: continue ведёт в проверку условия
            long c = eval(cond);
            if (faulted()) {
                return FAULT;
            }
            if (c == 0) {
                return NORMAL;
            }
        }
    }

    /** {@code switch} как в C: вход в первую подходящую ветку (иначе в default), проваливание, {@code break} выходит. */
    private int execSwitch(int n) {
        long key = eval(m.a(n));
        if (faulted()) {
            return FAULT;
        }
        int off = m.b(n);
        int cases = m.c(n);
        int start = -1;
        int def = -1;
        for (int i = 0; i < cases; i++) {
            if (m.list(off + 3 * i) != 0) {
                def = i;
            } else if (key == (long) m.list(off + 3 * i + 1)) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            start = def;
        }
        if (start < 0) {
            return NORMAL;
        }
        for (int i = start; i < cases; i++) {
            int st = exec(m.list(off + 3 * i + 2));
            if (st == BREAK) {
                return NORMAL;
            }
            if (st != NORMAL) {
                return st;
            }
        }
        return NORMAL;
    }

    /**
     * {@code private_array_decl}: создаёт нулевой буфер длины {@code N}; повторное исполнение того же объявления
     * (в теле цикла) заново обнуляет прежний буфер, как свежий {@code new T[N]} в Java.
     */
    private boolean execPrivateArray(int n) {
        long len = eval(m.b(n));
        if (faulted()) {
            return false;
        }
        if (len <= 0 || len > MAX_PRIVATE_ARRAY) {
            fault("IN-R04", n, "недопустимая длина private-массива: " + len);
            return false;
        }
        int s = slot(m.a(n));
        int k = Math.max(1, typeSlots[m.typeB(m.fLocalType(cur, m.a(n)))]);
        long id = stack[s];
        if (id >= 0 && bufs[(int) id].length == len * k) {
            Arrays.fill(bufs[(int) id], 0);
        } else {
            if (bufCount == bufs.length) {
                bufs = Arrays.copyOf(bufs, bufs.length * 2);
                bufElem = Arrays.copyOf(bufElem, bufs.length);
            }
            bufs[bufCount] = new long[(int) len * k];
            bufElem[bufCount] = k;
            stack[s] = bufCount++;
        }
        return true;
    }

    // =============================================================================================
    // память
    // =============================================================================================

    private boolean store(int n, long p, long v) {
        int buf = (int) (p >>> 32);
        int idx = (int) p;
        if (buf < 0 || buf >= bufCount || idx < 0 || idx >= lengthOf(buf)) {
            fault("IN-R01", n, "запись за границами: индекс " + idx + ", длина " + lengthOf(buf));
            return false;
        }
        bufs[buf][idx * bufElem[buf]] = v;
        return true;
    }

    private long load(int n, long p) {
        int buf = (int) (p >>> 32);
        int idx = (int) p;
        if (buf < 0 || buf >= bufCount || idx < 0 || idx >= lengthOf(buf)) {
            return fault("IN-R01", n, "чтение за границами: индекс " + idx + ", длина " + lengthOf(buf));
        }
        return bufs[buf][idx * bufElem[buf]];
    }

    private boolean storeAgg(int n, long p, long[] v) {
        int buf = (int) (p >>> 32);
        int idx = (int) p;
        if (buf < 0 || buf >= bufCount || idx < 0 || idx >= lengthOf(buf)) {
            fault("IN-R01", n, "запись за границами: индекс " + idx + ", длина " + lengthOf(buf));
            return false;
        }
        System.arraycopy(v, 0, bufs[buf], idx * bufElem[buf], v.length);
        return true;
    }

    private long[] loadAgg(int n, long p, int slots) {
        int buf = (int) (p >>> 32);
        int idx = (int) p;
        if (buf < 0 || buf >= bufCount || idx < 0 || idx >= lengthOf(buf)) {
            fault("IN-R01", n, "чтение за границами: индекс " + idx + ", длина " + lengthOf(buf));
            return new long[slots];
        }
        return Arrays.copyOfRange(bufs[buf], idx * bufElem[buf], idx * bufElem[buf] + slots);
    }

    /** Длина буфера в элементах (а не в ячейках). */
    private int lengthOf(int buf) {
        return buf >= 0 && buf < bufCount ? bufs[buf].length / bufElem[buf] : -1;
    }

    // =============================================================================================
    // выражения
    // =============================================================================================

    private long eval(int n) {
        int op = m.op(n);
        switch (op) {
            case Op.CONST_INT: {
                int t = m.type(n);
                return Alu.is64(t) ? m.constant(m.a(n)) : Alu.canon(t, m.a(n));
            }
            case Op.CONST_FLOAT:
                return m.type(n) == Types.F64 ? m.constant(m.a(n)) : (long) m.a(n);
            case Op.CONST_BOOL:
                return m.a(n);
            case Op.PARAM:
                return stack[base + paramSlot[cur][m.a(n)]];
            case Op.LOCAL_GET:
                return stack[slot(m.a(n))];
            case Op.EXTRACT:
                return evalAgg(m.a(n))[m.b(n)];
            case Op.FIELD_GET:
                return evalAgg(m.a(n))[fieldSlot[m.type(m.a(n))][m.b(n)]];
            case Op.ADD:
            case Op.SUB:
            case Op.MUL:
            case Op.DIV:
            case Op.REM: {
                int t = m.type(n);
                long a = eval(m.a(n));
                long b = eval(m.b(n));
                if ((op == Op.DIV || op == Op.REM) && Alu.divisorIsZero(t, b)) {
                    return fault("IN-R02", n, "целочисленное деление на ноль");
                }
                return Alu.arith(op, t, a, b);
            }
            case Op.NEG:
                return Alu.neg(m.type(n), eval(m.a(n)));
            case Op.AND:
            case Op.OR:
            case Op.XOR:
                return Alu.bitwise(op, eval(m.a(n)), eval(m.b(n)));
            case Op.NOT:
                return Alu.not(m.type(n), eval(m.a(n)));
            case Op.SHL:
            case Op.SHR:
            case Op.USHR: {
                long a = eval(m.a(n));
                long b = eval(m.b(n));
                return Alu.shift(op, m.type(n), a, b);
            }
            case Op.EQ:
            case Op.NE:
            case Op.LT:
            case Op.LE:
            case Op.GT:
            case Op.GE: {
                int t = m.type(m.a(n));
                long a = eval(m.a(n));
                long b = eval(m.b(n));
                return Alu.compare(op, t, a, b);
            }
            case Op.SELECT:
                return eval(m.a(n)) != 0 ? eval(m.b(n)) : eval(m.c(n));
            case Op.CONVERT:
                return Alu.convert(m.type(m.a(n)), m.type(n), eval(m.a(n)));
            case Op.BITCAST:
                return Alu.bitcast(m.type(n), eval(m.a(n)));
            case Op.ELEM_PTR: {
                int baseNode = m.a(n);
                long b = eval(baseNode);
                long idx = eval(m.b(n));
                long p = m.typeKind(m.type(baseNode)) == Types.KIND_ARRAY ? b << 32 : b;
                // индекс - I32: складываем как int, чтобы переполнение/отрицательные значения ловились при обращении
                return (p & 0xFFFFFFFF00000000L) | ((int) p + (int) idx) & 0xFFFFFFFFL;
            }
            case Op.LOAD:
                return load(n, eval(m.a(n)));
            case Op.ARRAY_LEN: {
                long id = eval(m.a(n));
                return id >= 0 && id < bufCount ? lengthOf((int) id) : fault("IN-R01", n, "длина неинициализированного массива");
            }
            case Op.CALL:
                return call(n);
            case Op.INTRINSIC:
                return intrinsic(n);
            default:
                return fault("IN-U01", n, "узел " + Op.nameOf(op) + " не поддерживается интерпретатором");
        }
    }


    // =============================================================================================
    // агрегаты: векторы и struct (значение - несколько ячеек подряд)
    // =============================================================================================

    /** Скалярный тип компоненты вектора либо сам тип. */
    private int laneType(int t) {
        return m.typeKind(t) == Types.KIND_VEC ? m.typeA(t) : t;
    }

    /**
     * Вычисляет выражение типа вектор/struct в новый массив листьев. Значения неизменяемы, поэтому результат -
     * всегда свежая копия, которую вызывающий вправе менять.
     */
    private long[] evalAgg(int n) {
        int t = m.type(n);
        int k = typeSlots[t];
        int op = m.op(n);
        switch (op) {
            case Op.PARAM: {
                int at = base + paramSlot[cur][m.a(n)];
                return Arrays.copyOfRange(stack, at, at + k);
            }
            case Op.LOCAL_GET: {
                int at = slot(m.a(n));
                return Arrays.copyOfRange(stack, at, at + k);
            }
            case Op.LOAD:
                return loadAgg(n, eval(m.a(n)), k);
            case Op.SELECT:
                return eval(m.a(n)) != 0 ? evalAgg(m.b(n)) : evalAgg(m.c(n));
            case Op.CALL: {
                call(n);
                return retAgg == null || faulted() ? new long[k] : retAgg;
            }
            case Op.BUILD: {
                long[] out = new long[k];
                for (int i = 0; i < k; i++) {
                    out[i] = eval(m.list(m.a(n) + i));
                }
                return out;
            }
            case Op.SPLAT: {
                long[] out = new long[k];
                Arrays.fill(out, eval(m.a(n)));
                return out;
            }
            case Op.INSERT: {
                long[] out = evalAgg(m.a(n));
                out[m.b(n)] = eval(m.c(n));
                return out;
            }
            case Op.SHUFFLE: {
                long[] src = evalAgg(m.a(n));
                long[] out = new long[k];
                for (int i = 0; i < k; i++) {
                    out[i] = src[m.list(m.b(n) + i)];
                }
                return out;
            }
            case Op.STRUCT_BUILD: {
                long[] out = new long[k];
                int at = 0;
                for (int i = 0; i < m.b(n); i++) {
                    int field = m.list(m.a(n) + i);
                    if (typeAgg[m.type(field)]) {
                        long[] sub = evalAgg(field);
                        System.arraycopy(sub, 0, out, at, sub.length);
                        at += sub.length;
                    } else {
                        out[at++] = eval(field);
                    }
                }
                return out;
            }
            case Op.FIELD_GET: {
                long[] src = evalAgg(m.a(n));
                int at = fieldSlot[m.type(m.a(n))][m.b(n)];
                return Arrays.copyOfRange(src, at, at + k);
            }
            case Op.FIELD_SET: {
                long[] out = evalAgg(m.a(n));
                int at = fieldSlot[t][m.b(n)];
                int value = m.c(n);
                if (typeAgg[m.type(value)]) {
                    long[] sub = evalAgg(value);
                    System.arraycopy(sub, 0, out, at, sub.length);
                } else {
                    out[at] = eval(value);
                }
                return out;
            }
            case Op.ADD:
            case Op.SUB:
            case Op.MUL:
            case Op.DIV:
            case Op.REM:
            case Op.AND:
            case Op.OR:
            case Op.XOR:
            case Op.SHL:
            case Op.SHR:
            case Op.USHR:
                return laneWise2(n, op, laneType(t), k);
            case Op.NEG:
            case Op.NOT: {
                long[] a = evalAgg(m.a(n));
                int st = laneType(t);
                for (int i = 0; i < k; i++) {
                    a[i] = op == Op.NEG ? Alu.neg(st, a[i]) : Alu.not(st, a[i]);
                }
                return a;
            }
            case Op.CONVERT: {
                long[] a = evalAgg(m.a(n));
                int from = laneType(m.type(m.a(n)));
                int to = laneType(t);
                for (int i = 0; i < k; i++) {
                    a[i] = Alu.convert(from, to, a[i]);
                }
                return a;
            }
            case Op.BITCAST: {
                long[] a = evalAgg(m.a(n));
                int to = laneType(t);
                for (int i = 0; i < k; i++) {
                    a[i] = Alu.bitcast(to, a[i]);
                }
                return a;
            }
            case Op.INTRINSIC:
                return vectorIntrinsic(n, laneType(t), k);
            default:
                fault("IN-U01", n, "узел " + Op.nameOf(op) + " с агрегатным результатом не поддерживается интерпретатором");
                return new long[k];
        }
    }

    private long[] laneWise2(int n, int op, int st, int k) {
        long[] a = evalAgg(m.a(n));
        long[] b = evalAgg(m.b(n));
        boolean division = op == Op.DIV || op == Op.REM;
        for (int i = 0; i < k; i++) {
            if (division && Alu.divisorIsZero(st, b[i])) {
                fault("IN-R02", n, "целочисленное деление на ноль в компоненте " + i);
                return a;
            }
            switch (op) {
                case Op.AND, Op.OR, Op.XOR -> a[i] = Alu.bitwise(op, a[i], b[i]);
                case Op.SHL, Op.SHR, Op.USHR -> a[i] = Alu.shift(op, st, a[i], b[i]);
                default -> a[i] = Alu.arith(op, st, a[i], b[i]);
            }
        }
        return a;
    }

    /** Математические builtins над векторами: покомпонентно скалярной реализацией. */
    private long[] vectorIntrinsic(int n, int st, int k) {
        int code = BUILTIN_CODE[m.a(n)];
        int off = m.b(n);
        int argc = m.c(n);
        if (code != B_SIN && code != B_COS && code != B_SQRT && code != B_FMA && code != B_MAX && code != B_MIN
                && code != B_ASSUME_UNIFORM) {
            fault("IN-U01", n, "builtin " + Builtins.name(m.a(n)) + " над векторами не поддерживается интерпретатором");
            return new long[k];
        }
        long[][] args = new long[argc][];
        for (int i = 0; i < argc; i++) {
            args[i] = evalAgg(m.list(off + i));
        }
        long[] out = new long[k];
        for (int i = 0; i < k; i++) {
            out[i] = lane(code, st, args[0][i], argc > 1 ? args[1][i] : 0, argc > 2 ? args[2][i] : 0);
        }
        return out;
    }

    private long call(int n) {
        int f = m.a(n);
        int off = m.b(n);
        int argc = m.c(n);
        int nb = top;
        top = nb + frameSize[f];
        ensureStack(top);
        for (int k = 0; k < argc; k++) {
            int arg = m.list(off + k);
            if (typeAgg[m.type(arg)]) {
                long[] v = evalAgg(arg);
                if (!faulted()) {
                    System.arraycopy(v, 0, stack, nb + paramSlot[f][k], v.length);
                }
            } else {
                long v = eval(arg);
                stack[nb + paramSlot[f][k]] = v;
            }
        }
        if (faulted()) {
            top = nb;
            return 0;
        }
        initArrayLocals(f, nb);
        int savedBase = base;
        int savedCur = cur;
        int savedBufs = bufCount;
        base = nb;
        cur = f;
        int st = exec(m.fBody(f));
        long r = ret;
        if (st == FAULT) {
            // base/cur оставляем как есть: диагностика уже зафиксирована
            top = nb;
            return 0;
        }
        base = savedBase;
        cur = savedCur;
        bufCount = savedBufs;
        top = nb;
        return st == RETURN ? r : 0;
    }

    // =============================================================================================
    // builtins
    // =============================================================================================

    private long intrinsic(int n) {
        int id = m.a(n);
        int off = m.b(n);
        int argc = m.c(n);
        int code = BUILTIN_CODE[id];
        int t = m.type(n);
        switch (code) {
            case B_GLOBAL_ID:
                return axis(gid, (int) eval(m.list(off)), 0);
            case B_LOCAL_ID:
                return axis(lid, (int) eval(m.list(off)), 0);
            case B_GROUP_ID:
                return axis(grp, (int) eval(m.list(off)), 0);
            case B_GLOBAL_SIZE:
                return axis(gsz, (int) eval(m.list(off)), 1);
            case B_LOCAL_SIZE:
                return axis(lsz, (int) eval(m.list(off)), 1);
            case B_NUM_GROUPS:
                return axis(ngr, (int) eval(m.list(off)), 1);
            case B_WORK_DIM:
                return workDim;
            case B_ASSUME_UNIFORM:
                return eval(m.list(off));
            case B_SIN:
            case B_COS:
            case B_SQRT: {
                long a = eval(m.list(off));
                return t == Types.F32 ? math32(code, a) : math64(code, a);
            }
            case B_FMA: {
                long a = eval(m.list(off));
                long b = eval(m.list(off + 1));
                long c = eval(m.list(off + 2));
                if (t == Types.F32) {
                    return Float.floatToRawIntBits(Math.fma(Float.intBitsToFloat((int) a),
                            Float.intBitsToFloat((int) b), Float.intBitsToFloat((int) c)));
                }
                return Double.doubleToRawLongBits(Math.fma(Double.longBitsToDouble(a),
                        Double.longBitsToDouble(b), Double.longBitsToDouble(c)));
            }
            case B_MAX:
            case B_MIN: {
                long a = eval(m.list(off));
                long b = eval(m.list(off + 1));
                return minMax(code == B_MAX, t, a, b);
            }
            case B_ATOMIC_ADD: {
                long p = eval(m.list(off));
                long v = eval(m.list(off + 1));
                long old = load(n, p);
                if (faulted()) {
                    return 0;
                }
                store(n, p, Alu.arith(Op.ADD, t, old, v));
                return old;
            }
            default:
                return fault("IN-U01", n, "builtin " + Builtins.name(id) + " не поддерживается интерпретатором (аргументов: " + argc + ")");
        }
    }

    /**
     * Покомпонентное применение математического builtin к скалярам типа {@code st}.
     */
    private static long lane(int code, int st, long a, long b, long c) {
        switch (code) {
            case B_SIN:
            case B_COS:
            case B_SQRT:
                return st == Types.F32 ? math32(code, a) : math64(code, a);
            case B_FMA:
                if (st == Types.F32) {
                    return Float.floatToRawIntBits(Math.fma(Float.intBitsToFloat((int) a),
                            Float.intBitsToFloat((int) b), Float.intBitsToFloat((int) c)));
                }
                return Double.doubleToRawLongBits(Math.fma(Double.longBitsToDouble(a),
                        Double.longBitsToDouble(b), Double.longBitsToDouble(c)));
            case B_MAX:
            case B_MIN:
                return minMax(code == B_MAX, st, a, b);
            default:
                return a; // assumeUniform
        }
    }

    /** Индекс по оси; вне {@code 0..2} - значение по умолчанию OpenCL (0 для индексов, 1 для размеров). */
    private static long axis(int[] values, int dim, int outside) {
        return dim >= 0 && dim < 3 ? values[dim] : outside;
    }

    /**
     * {@code sin/cos/sqrt} для {@code F32}. {@code sin/cos} - {@link StrictMath} (fdlibm, одинаково на всех
     * платформах, ровно как JVM-реализация в {@code Gpu}); {@code sqrt} - корректно округлённый
     * {@link Math#sqrt}: результат {@code double} затем округляется к {@code float}, что для квадратного корня
     * даёт корректно округлённый {@code float} (у {@code double} более {@code 2p+2} значащих бит).
     */
    private static long math32(int code, long a) {
        double x = Float.intBitsToFloat((int) a);
        return switch (code) {
            case B_SIN -> Float.floatToRawIntBits((float) StrictMath.sin(x));
            case B_COS -> Float.floatToRawIntBits((float) StrictMath.cos(x));
            default -> Float.floatToRawIntBits((float) Math.sqrt(x));
        };
    }

    private static long math64(int code, long a) {
        double x = Double.longBitsToDouble(a);
        return switch (code) {
            case B_SIN -> Double.doubleToRawLongBits(StrictMath.sin(x));
            case B_COS -> Double.doubleToRawLongBits(StrictMath.cos(x));
            default -> Double.doubleToRawLongBits(Math.sqrt(x));
        };
    }

    /**
     * {@code max/min}. Для вещественных - семантика {@link Math#max}/{@link Math#min} (NaN распространяется,
     * {@code -0 < +0}), как в JVM-реализации {@code Gpu} (REWORK_ARCHITECTURE.md, 5.14); для целых - по знаку типа.
     */
    private static long minMax(boolean max, int t, long a, long b) {
        switch (t) {
            case Types.F32: {
                float x = Float.intBitsToFloat((int) a);
                float y = Float.intBitsToFloat((int) b);
                return Float.floatToRawIntBits(max ? Math.max(x, y) : Math.min(x, y));
            }
            case Types.F64: {
                double x = Double.longBitsToDouble(a);
                double y = Double.longBitsToDouble(b);
                return Double.doubleToRawLongBits(max ? Math.max(x, y) : Math.min(x, y));
            }
            default: {
                boolean aLess = Alu.compare(Op.LT, t, a, b) != 0;
                return max == aLess ? b : a;
            }
        }
    }
}
