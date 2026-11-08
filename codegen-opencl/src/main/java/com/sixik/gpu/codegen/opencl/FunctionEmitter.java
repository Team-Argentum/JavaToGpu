package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Operands;
import com.sixik.gpu.ir.Types;

import java.util.ArrayList;
import java.util.List;

/**
 * Печать одной функции IR как функции OpenCL C (REWORK_ARCHITECTURE.md, 8.A).
 *
 * <h2>Как устроен текст</h2>
 * <p>IR - дерево стейтментов с выражениями-деревьями (операнды имеют меньший id, эффекты подняты на уровень
 * стейтментов, IR-E01/E02), поэтому выражение печатается рекурсивно «как есть», а общий закрытый подузел
 * просто печатается в каждом месте использования: он чистый, значит повтор не меняет результата.
 * Порядок вычисления операндов в C не определён, но здесь это безразлично: эффектов внутри выражений нет.</p>
 *
 * <h2>Java-семантика поверх C</h2>
 * <ul>
 *   <li><b>Целые знаковые</b> {@code + - * -x << }: переполнение знакового целого в C - неопределённое
 *       поведение, у Java - оборот по модулю 2^N. Поэтому считаем в беззнаковом типе той же ширины:
 *       {@code (int)((uint)a + (uint)b)}. Для беззнаковых типов оператор C уже оборачивается;</li>
 *   <li><b>Сдвиги</b>: счётчик маскируется шириной ({@code n & 31} / {@code n & 63}), как в Java; для
 *       константного счётчика маска применяется при генерации;</li>
 *   <li><b>Деление и остаток знаковых</b> - через {@link Helpers} ({@code MIN / -1});</li>
 *   <li><b>float -> int</b> - насыщающее, {@code NaN -> 0}; к 8/16-битным типам - как Java
 *       {@code (byte)(int)f}: насыщение до {@code int}, затем сужение с оборотом (так же, как в эталоне
 *       {@code ir-interpreter});</li>
 *   <li><b>{@code %} для float</b> - {@code fmod}: знак остатка совпадает со знаком делимого, как в Java;</li>
 *   <li><b>{@code max/min} для float</b> - через {@link Helpers} (NaN и {@code +-0} как у {@code Math.max/min}).</li>
 * </ul>
 *
 * <h2>Приоритеты</h2>
 * <p>Каждое выражение несёт уровень приоритета; скобки ставятся только там, где они нужны, а операнды
 * побитовых операций, сдвигов, сравнений и тернарного оператора всегда явно разделены скобками (читаемость
 * и отсутствие предупреждений компилятора о смешении операторов).</p>
 */
final class FunctionEmitter {

    private static final int P_ATOM = 15;
    private static final int P_UNARY = 14;
    private static final int P_MUL = 13;
    private static final int P_ADD = 12;
    private static final int P_SHIFT = 11;
    private static final int P_REL = 10;
    private static final int P_EQ = 9;
    private static final int P_AND = 8;
    private static final int P_XOR = 7;
    private static final int P_OR = 6;
    private static final int P_LAND = 5;
    private static final int P_LOR = 4;
    private static final int P_TERNARY = 3;

    /** Выражение C и его приоритет. */
    private record Ex(String s, int prec) {
    }

    private final IrModule m;
    private final int f;
    private final EmitOptions opts;
    private final Helpers helpers;
    private final String[] funcNames;
    private final String[] paramNames;
    private final String[] lenNames;
    private final String[] localNames;
    private final int[] arrayLen;
    private final StringBuilder out = new StringBuilder();
    private final List<String> loops = new ArrayList<>();
    private int indent;
    private int labelCounter;
    private final java.util.Set<String> usedLabels = new java.util.HashSet<>();

    /**
     * @param m         модуль
     * @param f         индекс печатаемой функции
     * @param opts      параметры генерации
     * @param helpers   реестр вспомогательных функций (общий на модуль)
     * @param funcNames имена функций по индексу
     * @param global    имена, уже занятые на уровне модуля (имена функций); копируется
     */
    FunctionEmitter(IrModule m, int f, EmitOptions opts, Helpers helpers, String[] funcNames, Names global) {
        this.m = m;
        this.f = f;
        this.opts = opts;
        this.helpers = helpers;
        this.funcNames = funcNames;
        Names names = global.copy();
        int pc = m.fParamCount(f);
        paramNames = new String[pc];
        lenNames = new String[pc];
        for (int i = 0; i < pc; i++) {
            String wanted = opts.debug() ? m.debug().paramName(f, i) : null;
            paramNames[i] = names.unique(wanted, "p" + i);
            if (m.typeKind(m.fParamType(f, i)) == Types.KIND_ARRAY) {
                lenNames[i] = names.unique(paramNames[i] + "_len", "p" + i + "_len");
            }
        }
        int lc = m.fLocalCount(f);
        localNames = new String[lc];
        arrayLen = new int[lc];
        for (int l = 0; l < lc; l++) {
            String wanted = opts.debug() ? m.debug().localName(f, l) : null;
            localNames[l] = names.unique(wanted, "v" + l);
            arrayLen[l] = -1;
        }
    }

    /**
     * @param i индекс параметра
     * @return имя параметра в тексте C
     */
    String paramName(int i) {
        return paramNames[i];
    }

    /**
     * @param i индекс параметра-массива
     * @return имя аргумента длины
     */
    String lengthName(int i) {
        return lenNames[i];
    }

    /**
     * Печатает функцию целиком.
     *
     * @param kernel {@code true} для точки входа
     * @return текст функции, оканчивающийся переводом строки
     */
    String emit(boolean kernel) {
        out.setLength(0);
        indent = 0;
        header(kernel);
        line("{");
        indent++;
        innerStatements(m.fBody(f));
        indent--;
        line("}");
        return out.toString();
    }

    // ---- заголовок ---------------------------------------------------------------------------

    private void header(boolean kernel) {
        if (kernel) {
            int x = m.fReqWorkGroup(f, 0);
            int y = m.fReqWorkGroup(f, 1);
            int z = m.fReqWorkGroup(f, 2);
            if (x > 0 || y > 0 || z > 0) {
                line("__attribute__((reqd_work_group_size(" + Math.max(1, x) + ", " + Math.max(1, y) + ", "
                        + Math.max(1, z) + ")))");
            }
        }
        List<String> params = new ArrayList<>();
        for (int i = 0; i < m.fParamCount(f); i++) {
            int t = m.fParamType(f, i);
            if (m.typeKind(t) == Types.KIND_ARRAY) {
                params.add(CTypes.space(Types.space(m, t)) + " " + CTypes.scalar(Types.elem(m, t)) + "* "
                        + paramNames[i]);
                params.add("int " + lenNames[i]);
            } else {
                params.add(CTypes.scalar(t) + " " + paramNames[i]);
            }
        }
        int ret = m.fRet(f);
        String head = (kernel ? "__kernel void " : (ret == Types.VOID ? "void " : CTypes.scalar(ret) + " "))
                + funcNames[f] + "(";
        String joined = String.join(", ", params);
        if (head.length() + joined.length() + 1 <= 100) {
            line(head + joined + ")");
        } else {
            line(head);
            for (int i = 0; i < params.size(); i++) {
                line("        " + params.get(i) + (i + 1 < params.size() ? "," : ")"));
            }
        }
    }

    // ---- стейтменты --------------------------------------------------------------------------

    private void innerStatements(int n) {
        if (m.op(n) == Op.BLOCK) {
            int cnt = Operands.count(m, n);
            for (int k = 0; k < cnt; k++) {
                statement(Operands.get(m, n, k));
            }
        } else {
            statement(n);
        }
    }

    private void directive(int n) {
        if (!opts.debug()) {
            return;
        }
        int ln = m.debug().line(n);
        if (ln > 0) {
            String name = opts.sourceName();
            raw("#line " + ln + (name == null ? "" : " \"" + name + "\""));
        }
    }

    private void statement(int n) {
        int op = m.op(n);
        if (op != Op.BLOCK) {
            directive(n);
        }
        switch (op) {
            case Op.BLOCK -> {
                line("{");
                indent++;
                innerStatements(n);
                indent--;
                line("}");
            }
            case Op.LOCAL_DECL -> {
                int l = m.a(n);
                line(CTypes.scalar(m.fLocalType(f, l)) + " " + localNames[l] + " = " + expr(m.b(n)).s() + ";");
            }
            case Op.LOCAL_SET -> line(localNames[m.a(n)] + " = " + expr(m.b(n)).s() + ";");
            case Op.STORE -> line(lvalue(m.a(n)) + " = " + expr(m.b(n)).s() + ";");
            case Op.EXPR_STMT -> exprStatement(m.a(n));
            case Op.PRIVATE_ARRAY_DECL -> {
                int l = m.a(n);
                int len = m.a(m.b(n));
                arrayLen[l] = len;
                int elem = Types.elem(m, m.fLocalType(f, l));
                line(CTypes.scalar(elem) + " " + localNames[l] + "[" + len + "] = {0};");
            }
            case Op.IF -> ifStatement(n);
            case Op.LOOP -> loopStatement(n);
            case Op.DO_WHILE -> {
                loops.add(null);
                line("do {");
                indent++;
                innerStatements(m.a(n));
                indent--;
                loops.remove(loops.size() - 1);
                line("} while (" + expr(m.b(n)).s() + ");");
            }
            case Op.SWITCH -> switchStatement(n);
            case Op.BREAK -> line("break;");
            case Op.CONTINUE -> {
                String label = loops.isEmpty() ? null : loops.get(loops.size() - 1);
                if (label == null) {
                    line("continue;");
                } else {
                    line("goto " + label + ";");
                    usedLabels.add(label);
                }
            }
            case Op.RETURN -> line(m.a(n) >= 0 ? "return " + expr(m.a(n)).s() + ";" : "return;");
            default -> throw new IllegalStateException("стейтмент " + Op.nameOf(op) + " не поддержан эмиттером");
        }
    }

    private void exprStatement(int e) {
        int op = m.op(e);
        boolean effectful = op == Op.CALL
                || (op == Op.INTRINSIC && Builtins.has(m.a(e), Builtins.EFFECT));
        String text = expr(e).s();
        line(effectful ? text + ";" : "(void)(" + text + ");");
    }

    private void ifStatement(int n) {
        line("if (" + expr(m.a(n)).s() + ") {");
        indent++;
        innerStatements(m.b(n));
        indent--;
        int otherwise = m.c(n);
        while (otherwise >= 0) {
            int chained = chainedIf(otherwise);
            if (chained >= 0 && !opts.debug()) {
                line("} else if (" + expr(m.a(chained)).s() + ") {");
                indent++;
                innerStatements(m.b(chained));
                indent--;
                otherwise = m.c(chained);
            } else {
                line("} else {");
                indent++;
                innerStatements(otherwise);
                indent--;
                otherwise = -1;
            }
        }
        line("}");
    }

    /** Если ветка else - это блок из одного {@code if}, возвращает его (для цепочки {@code else if}), иначе -1. */
    private int chainedIf(int otherwise) {
        int x = otherwise;
        if (m.op(x) == Op.BLOCK && Operands.count(m, x) == 1) {
            x = Operands.get(m, x, 0);
        }
        return m.op(x) == Op.IF ? x : -1;
    }

    private void loopStatement(int n) {
        String cond = expr(m.a(n)).s();
        int body = m.b(n);
        int update = m.c(n);
        if (update < 0) {
            loops.add(null);
            line("while (" + cond + ") {");
            indent++;
            innerStatements(body);
            indent--;
            loops.remove(loops.size() - 1);
            line("}");
        } else if (isSimpleUpdate(update)) {
            loops.add(null);
            line("for (; " + cond + "; " + simpleUpdate(update) + ") {");
            indent++;
            innerStatements(body);
            indent--;
            loops.remove(loops.size() - 1);
            line("}");
        } else {
            // сложный шаг (объявления, ветвления): continue заменяется переходом на метку перед шагом
            String label = "cont_" + (++labelCounter);
            loops.add(label);
            line("while (" + cond + ") {");
            indent++;
            line("{");
            indent++;
            innerStatements(body);
            indent--;
            line("}");
            loops.remove(loops.size() - 1);
            if (usedLabels.contains(label)) {
                raw(label + ":;");
            }
            innerStatements(update);
            indent--;
            line("}");
        }
    }

    private boolean isSimpleUpdate(int u) {
        switch (m.op(u)) {
            case Op.LOCAL_SET, Op.STORE, Op.EXPR_STMT:
                return true;
            case Op.BLOCK: {
                int cnt = Operands.count(m, u);
                if (cnt == 0) {
                    return false;
                }
                for (int k = 0; k < cnt; k++) {
                    if (!isSimpleUpdate(Operands.get(m, u, k)) || m.op(Operands.get(m, u, k)) == Op.BLOCK) {
                        return false;
                    }
                }
                return true;
            }
            default:
                return false;
        }
    }

    private String simpleUpdate(int u) {
        switch (m.op(u)) {
            case Op.LOCAL_SET:
                return localNames[m.a(u)] + " = " + expr(m.b(u)).s();
            case Op.STORE:
                return lvalue(m.a(u)) + " = " + expr(m.b(u)).s();
            case Op.EXPR_STMT:
                return expr(m.a(u)).s();
            default: {
                StringBuilder sb = new StringBuilder();
                int cnt = Operands.count(m, u);
                for (int k = 0; k < cnt; k++) {
                    if (k > 0) {
                        sb.append(", ");
                    }
                    sb.append(simpleUpdate(Operands.get(m, u, k)));
                }
                return sb.toString();
            }
        }
    }

    private void switchStatement(int n) {
        int keyNode = m.a(n);
        int keyType = m.type(keyNode);
        line("switch (" + expr(keyNode).s() + ") {");
        indent++;
        int off = m.b(n);
        int cases = m.c(n);
        for (int i = 0; i < cases; i++) {
            boolean isDefault = m.list(off + 3 * i) != 0;
            if (isDefault) {
                line("default: {");
            } else {
                line("case " + CTypes.integer(keyType, m.list(off + 3 * i + 1)) + ": {");
            }
            indent++;
            innerStatements(m.list(off + 3 * i + 2));
            indent--;
            line("}");
        }
        indent--;
        line("}");
    }

    // ---- выражения ---------------------------------------------------------------------------

    private Ex expr(int n) {
        int op = m.op(n);
        int t = m.type(n);
        switch (op) {
            case Op.CONST_INT:
                return new Ex(CTypes.integer(t, intConst(n)), P_ATOM);
            case Op.CONST_FLOAT:
                return new Ex(t == Types.F64 ? CTypes.float64(m.constant(m.a(n))) : CTypes.float32(m.a(n)), P_ATOM);
            case Op.CONST_BOOL:
                return new Ex(m.a(n) != 0 ? "true" : "false", P_ATOM);
            case Op.PARAM:
                return new Ex(paramNames[m.a(n)], P_ATOM);
            case Op.LOCAL_GET:
                return new Ex(localNames[m.a(n)], P_ATOM);
            case Op.ADD, Op.SUB, Op.MUL, Op.DIV, Op.REM:
                return arith(n);
            case Op.NEG:
                return negate(n);
            case Op.AND:
                return binary(n, "&", P_AND);
            case Op.OR:
                return binary(n, "|", P_OR);
            case Op.XOR:
                return binary(n, "^", P_XOR);
            case Op.NOT: {
                Ex a = expr(Operands.get(m, n, 0));
                return new Ex((t == Types.BOOL ? "!" : "~") + wrap(a, P_UNARY), P_UNARY);
            }
            case Op.SHL, Op.SHR, Op.USHR:
                return shift(n);
            case Op.EQ:
                return binary(n, "==", P_EQ);
            case Op.NE:
                return binary(n, "!=", P_EQ);
            case Op.LT:
                return binary(n, "<", P_REL);
            case Op.LE:
                return binary(n, "<=", P_REL);
            case Op.GT:
                return binary(n, ">", P_REL);
            case Op.GE:
                return binary(n, ">=", P_REL);
            case Op.SELECT:
                return select(n);
            case Op.CONVERT:
                return convert(n);
            case Op.BITCAST: {
                Ex a = expr(m.a(n));
                return m.type(m.a(n)) == t ? a : new Ex(CTypes.asName(t) + "(" + a.s() + ")", P_ATOM);
            }
            case Op.ELEM_PTR:
                return pointer(n);
            case Op.LOAD:
                return load(n);
            case Op.ARRAY_LEN:
                return new Ex(lengthOf(m.a(n)), P_ATOM);
            case Op.CALL:
                return call(n);
            case Op.INTRINSIC:
                return intrinsic(n);
            default:
                throw new IllegalStateException("выражение " + Op.nameOf(op) + " не поддержано эмиттером");
        }
    }

    /** {@code c ? x : false} это {@code c && x}, {@code c ? true : x} это {@code c || x}; иначе тернарный оператор. */
    private Ex select(int n) {
        int c = m.a(n);
        int x = m.b(n);
        int y = m.c(n);
        if (m.type(n) == Types.BOOL) {
            if (m.op(y) == Op.CONST_BOOL && m.a(y) == 0) {
                return new Ex(wrap(expr(c), P_UNARY) + " && " + wrap(expr(x), P_UNARY), P_LAND);
            }
            if (m.op(x) == Op.CONST_BOOL && m.a(x) != 0) {
                return new Ex(wrap(expr(c), P_UNARY) + " || " + wrap(expr(y), P_UNARY), P_LOR);
            }
        }
        return new Ex(wrap(expr(c), P_UNARY) + " ? " + wrap(expr(x), P_UNARY) + " : " + wrap(expr(y), P_UNARY),
                P_TERNARY);
    }

    private long intConst(int n) {
        return Types.bits(m.type(n)) == 64 ? m.constant(m.a(n)) : m.a(n);
    }

    private static String wrap(Ex e, int need) {
        return e.prec() >= need ? e.s() : "(" + e.s() + ")";
    }

    private Ex binary(int n, String op, int prec) {
        Ex a = expr(Operands.get(m, n, 0));
        Ex b = expr(Operands.get(m, n, 1));
        return new Ex(wrap(a, P_UNARY) + " " + op + " " + wrap(b, P_UNARY), prec);
    }

    /**
     * Каноническое значение целой константы типа {@code t}: знаковые типы расширены знаком, беззнаковые - нулями.
     */
    private static long canonical(int t, long v) {
        return switch (t) {
            case Types.I8 -> (byte) v;
            case Types.U8 -> v & 0xFFL;
            case Types.I16 -> (short) v;
            case Types.U16 -> v & 0xFFFFL;
            case Types.I32 -> (int) v;
            case Types.U32 -> v & 0xFFFFFFFFL;
            default -> v;
        };
    }

    /**
     * Значение узла, если оно известно при генерации: целая константа либо {@code convert} целой константы в
     * целый тип (фронтенд пишет {@code (long) 1} именно так).
     *
     * @return каноническое значение либо {@code null}
     */
    private Long foldInt(int node) {
        int op = m.op(node);
        if (op == Op.CONST_INT) {
            return canonical(m.type(node), intConst(node));
        }
        if (op == Op.CONVERT && Types.isInteger(m.type(node)) && Types.isInteger(m.type(m.a(node)))) {
            Long inner = foldInt(m.a(node));
            return inner == null ? null : canonical(m.type(node), inner);
        }
        return null;
    }

    /** Знаковая целая арифметика, которую считаем в беззнаковом типе (оборот без UB). */
    private boolean wrapsViaUnsigned(int node) {
        int t = m.type(node);
        if (!Types.isInt32or64(t) || !Types.isSigned(t)) {
            return false;
        }
        int op = m.op(node);
        return op == Op.ADD || op == Op.SUB || op == Op.MUL || op == Op.NEG || op == Op.SHL;
    }

    /**
     * Значение знакового целого узла как беззнаковое той же ширины. Вложенные {@code + - * -x <<} остаются в
     * беззнаковом типе целиком, без промежуточных {@code (int)} и обратных приведений: по модулю 2^N результат
     * тот же, а текст короче.
     */
    private Ex unsignedValue(int node) {
        Long k = foldInt(node);
        int t = m.type(node);
        if (k != null) {
            return new Ex(CTypes.unsignedBits(t, k), P_ATOM);
        }
        if (wrapsViaUnsigned(node)) {
            return unsignedArith(node);
        }
        return new Ex("(" + CTypes.unsignedOf(t) + ")" + wrap(expr(node), P_UNARY), P_UNARY);
    }

    private Ex unsignedArith(int n) {
        int op = m.op(n);
        int t = m.type(n);
        if (op == Op.NEG) {
            Ex a = unsignedValue(Operands.get(m, n, 0));
            return new Ex("0" + (Types.bits(t) == 64 ? "UL" : "u") + " - " + wrap(a, P_UNARY), P_ADD);
        }
        if (op == Op.SHL) {
            return new Ex(wrap(unsignedValue(Operands.get(m, n, 0)), P_UNARY) + " << " + shiftCount(n), P_SHIFT);
        }
        return arithText(op, unsignedValue(Operands.get(m, n, 0)), unsignedValue(Operands.get(m, n, 1)));
    }

    private Ex arith(int n) {
        int t = m.type(n);
        int an = Operands.get(m, n, 0);
        int bn = Operands.get(m, n, 1);
        int op = m.op(n);
        if (Types.isFloat(t)) {
            if (op == Op.REM) {
                return new Ex("fmod(" + expr(an).s() + ", " + expr(bn).s() + ")", P_ATOM);
            }
            return arithText(op, expr(an), expr(bn));
        }
        if (!Types.isSigned(t)) {
            return arithText(op, expr(an), expr(bn));
        }
        if (op == Op.DIV || op == Op.REM) {
            boolean wide = Types.bits(t) == 64;
            int h = op == Op.DIV ? (wide ? Helpers.DIV_I64 : Helpers.DIV_I32) : (wide ? Helpers.REM_I64 : Helpers.REM_I32);
            return new Ex(helpers.use(h) + "(" + expr(an).s() + ", " + expr(bn).s() + ")", P_ATOM);
        }
        return signedFromUnsigned(n);
    }

    private Ex signedFromUnsigned(int n) {
        return new Ex("(" + CTypes.scalar(m.type(n)) + ")" + wrap(unsignedArith(n), P_UNARY), P_UNARY);
    }

    private static Ex arithText(int op, Ex a, Ex b) {
        String sym;
        int prec;
        switch (op) {
            case Op.ADD -> {
                sym = "+";
                prec = P_ADD;
            }
            case Op.SUB -> {
                sym = "-";
                prec = P_ADD;
            }
            case Op.MUL -> {
                sym = "*";
                prec = P_MUL;
            }
            case Op.DIV -> {
                sym = "/";
                prec = P_MUL;
            }
            default -> {
                sym = "%";
                prec = P_MUL;
            }
        }
        return new Ex(wrap(a, prec) + " " + sym + " " + wrap(b, prec + 1), prec);
    }

    private Ex negate(int n) {
        int t = m.type(n);
        int an = Operands.get(m, n, 0);
        if (Types.isFloat(t)) {
            String s = wrap(expr(an), P_UNARY);
            return new Ex("-" + (s.startsWith("-") ? "(" + s + ")" : s), P_UNARY);
        }
        if (Types.isSigned(t)) {
            return signedFromUnsigned(n);
        }
        return new Ex("0" + (Types.bits(t) == 64 ? "UL" : "u") + " - " + wrap(expr(an), P_UNARY), P_ADD);
    }

    /** Счётчик сдвига, маскированный шириной значения (как в Java); константный счётчик маскируется при генерации. */
    private String shiftCount(int n) {
        int mask = Types.bits(m.type(n)) - 1;
        int cn = Operands.get(m, n, 1);
        Long k = foldInt(cn);
        if (k != null) {
            return Long.toString(k & mask);
        }
        return "(" + wrap(expr(cn), P_UNARY) + " & " + mask + ")";
    }

    private Ex shift(int n) {
        int t = m.type(n);
        int op = m.op(n);
        int vn = Operands.get(m, n, 0);
        String count = shiftCount(n);
        boolean signed = Types.isSigned(t);
        if (op == Op.SHL && signed) {
            return signedFromUnsigned(n);
        }
        if (op == Op.SHL) {
            return new Ex(wrap(expr(vn), P_UNARY) + " << " + count, P_SHIFT);
        }
        if (op == Op.USHR && signed) {
            Ex shifted = new Ex(wrap(unsignedValue(vn), P_UNARY) + " >> " + count, P_SHIFT);
            return new Ex("(" + CTypes.scalar(t) + ")" + wrap(shifted, P_UNARY), P_UNARY);
        }
        return new Ex(wrap(expr(vn), P_UNARY) + " >> " + count, P_SHIFT);
    }

    private Ex convert(int n) {
        int to = m.type(n);
        int an = m.a(n);
        int from = m.type(an);
        Long folded = foldInt(n);
        if (folded != null) {
            return new Ex(CTypes.integer(to, folded), P_ATOM);
        }
        Long source = Types.isInteger(from) && from != Types.U64 ? foldInt(an) : null;
        if (source != null && Types.isFloat(to)) {
            return new Ex(to == Types.F64 ? CTypes.float64(Double.doubleToRawLongBits((double) source.longValue()))
                    : CTypes.float32(Float.floatToRawIntBits((float) source.longValue())), P_ATOM);
        }
        if (Types.isInt32or64(from) && Types.isInt32or64(to) && Types.bits(from) == Types.bits(to)
                && !Types.isSigned(to) && wrapsViaUnsigned(an)) {
            // знаковая арифметика уже посчитана в беззнаковом типе той же ширины: приведение к нему бесплатно
            return unsignedValue(an);
        }
        Ex a = expr(an);
        if (from == to) {
            return a;
        }
        if (to == Types.BOOL) {
            return new Ex(wrap(a, P_UNARY) + " != 0", P_EQ);
        }
        if (Types.isFloat(from) && Types.isInteger(to)) {
            int wide = to;
            if (Types.bits(to) < 32) {
                wide = Types.I32;
            }
            Ex call = new Ex(helpers.use(Helpers.floatToInt(from, wide)) + "(" + a.s() + ")", P_ATOM);
            if (wide == to) {
                return call;
            }
            return new Ex("(" + CTypes.scalar(to) + ")" + call.s(), P_UNARY);
        }
        return new Ex("(" + CTypes.scalar(to) + ")" + wrap(a, P_UNARY), P_UNARY);
    }

    // ---- память ------------------------------------------------------------------------------

    /** Имя массива-значения (параметр или локаль-массив). */
    private String arrayName(int node) {
        return switch (m.op(node)) {
            case Op.PARAM -> paramNames[m.a(node)];
            case Op.LOCAL_GET -> localNames[m.a(node)];
            default -> throw new IllegalStateException("массив-значение должно быть параметром или локалью");
        };
    }

    private String lengthOf(int arrayNode) {
        return switch (m.op(arrayNode)) {
            case Op.PARAM -> lenNames[m.a(arrayNode)];
            case Op.LOCAL_GET -> Integer.toString(arrayLen[m.a(arrayNode)]);
            default -> throw new IllegalStateException("массив-значение должно быть параметром или локалью");
        };
    }

    private boolean isArray(int node) {
        return m.typeKind(m.type(node)) == Types.KIND_ARRAY;
    }

    /** Базовое выражение для индексации: имя массива либо указатель. */
    private String indexBase(int base) {
        return isArray(base) ? arrayName(base) : wrap(expr(base), P_ATOM);
    }

    private Ex pointer(int n) {
        int base = m.a(n);
        String idx = expr(m.b(n)).s();
        if (isArray(base)) {
            return new Ex("&" + arrayName(base) + "[" + idx + "]", P_UNARY);
        }
        return new Ex(wrap(expr(base), P_ADD) + " + " + wrap(expr(m.b(n)), P_ADD + 1), P_ADD);
    }

    private Ex load(int n) {
        return new Ex(lvalue(m.a(n)), P_ATOM);
    }

    /** Обращение по указателю как lvalue C: {@code a[i]} для {@code elem_ptr}, иначе {@code *p}. */
    private String lvalue(int ptr) {
        if (m.op(ptr) == Op.ELEM_PTR) {
            return indexBase(m.a(ptr)) + "[" + expr(m.b(ptr)).s() + "]";
        }
        return "*" + wrap(expr(ptr), P_UNARY);
    }

    // ---- вызовы ------------------------------------------------------------------------------

    private Ex call(int n) {
        int callee = m.a(n);
        StringBuilder sb = new StringBuilder(funcNames[callee]).append('(');
        int cnt = Operands.count(m, n);
        boolean first = true;
        for (int k = 0; k < cnt; k++) {
            int arg = Operands.get(m, n, k);
            if (!first) {
                sb.append(", ");
            }
            first = false;
            if (isArray(arg)) {
                sb.append(arrayName(arg)).append(", ").append(lengthOf(arg));
            } else {
                sb.append(expr(arg).s());
            }
        }
        return new Ex(sb.append(')').toString(), P_ATOM);
    }

    private Ex intrinsic(int n) {
        int id = m.a(n);
        String name = Builtins.name(id);
        int cnt = Operands.count(m, n);
        int t = m.type(n);
        switch (name) {
            case "getGlobalId":
                return new Ex("(int)get_global_id(" + expr(Operands.get(m, n, 0)).s() + ")", P_UNARY);
            case "getLocalId":
                return new Ex("(int)get_local_id(" + expr(Operands.get(m, n, 0)).s() + ")", P_UNARY);
            case "getGroupId":
                return new Ex("(int)get_group_id(" + expr(Operands.get(m, n, 0)).s() + ")", P_UNARY);
            case "getGlobalSize":
                return new Ex("(int)get_global_size(" + expr(Operands.get(m, n, 0)).s() + ")", P_UNARY);
            case "getLocalSize":
                return new Ex("(int)get_local_size(" + expr(Operands.get(m, n, 0)).s() + ")", P_UNARY);
            case "getNumGroups":
                return new Ex("(int)get_num_groups(" + expr(Operands.get(m, n, 0)).s() + ")", P_UNARY);
            case "getWorkDim":
                return new Ex("(int)get_work_dim()", P_UNARY);
            case "sin", "cos", "sqrt", "fma": {
                StringBuilder sb = new StringBuilder(name).append('(');
                for (int k = 0; k < cnt; k++) {
                    sb.append(k > 0 ? ", " : "").append(expr(Operands.get(m, n, k)).s());
                }
                return new Ex(sb.append(')').toString(), P_ATOM);
            }
            case "max", "min": {
                String a = expr(Operands.get(m, n, 0)).s();
                String b = expr(Operands.get(m, n, 1)).s();
                if (Types.isFloat(t)) {
                    boolean max = name.equals("max");
                    int h = t == Types.F32 ? (max ? Helpers.MAX_F32 : Helpers.MIN_F32)
                            : (max ? Helpers.MAX_F64 : Helpers.MIN_F64);
                    return new Ex(helpers.use(h) + "(" + a + ", " + b + ")", P_ATOM);
                }
                return new Ex(name + "(" + a + ", " + b + ")", P_ATOM);
            }
            case "assumeUniform":
                return expr(Operands.get(m, n, 0));
            case "atomicAdd": {
                String fn = Types.bits(t) == 64 ? "atom_add" : "atomic_add";
                return new Ex(fn + "(" + expr(Operands.get(m, n, 0)).s() + ", " + expr(Operands.get(m, n, 1)).s() + ")",
                        P_ATOM);
            }
            default:
                throw new IllegalStateException("builtin " + name + " не поддержан эмиттером");
        }
    }

    // ---- вывод -------------------------------------------------------------------------------

    private void line(String s) {
        for (int i = 0; i < indent; i++) {
            out.append("    ");
        }
        out.append(s).append('\n');
    }

    /** Строка без отступа (директивы препроцессора и метки). */
    private void raw(String s) {
        out.append(s).append('\n');
    }
}
