package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

import java.util.ArrayList;
import java.util.List;

/**
 * Свёртка констант, алгебраические упрощения и упрощение структуры control flow.
 *
 * <p><b>Выражения</b> переписываются снизу вверх до неподвижной точки. Правила делятся на «точные» (результат
 * побитово совпадает с исходным, включая NaN, знак нуля и переполнение целых) и «быстрые»
 * ({@link OptimizeOptions#fastMath()}): допускают изменение результата в особых случаях (NaN, inf, -0) или округления.
 * Точные правила включены всегда; быстрые - только по явному согласию (REWORK_ARCHITECTURE.md, 8.3).</p>
 *
 * <p>Точные правила (для целых {@code I32/U32/I64/U64}): {@code x+0, x-0, x*1, x/1, x|0, x^0, x&-1, x<<0 -> x};
 * {@code x*0, x&0, x%1, x-x, x^x -> 0}; {@code x*2^k -> x<<k}; для беззнаковых {@code x/2^k -> x>>k},
 * {@code x%2^k -> x&(2^k-1)}; {@code (x+c1)+c2}, {@code (x*c1)*c2} - свёртка констант (кольцо по модулю 2^n).
 * Для вещественных: {@code x*1, x/1, x-(+0), x+(-0) -> x}, {@code x*(-1) -> -x}, {@code x/2^k -> x*2^-k}
 * (обратная величина точна, значит результат тот же). Для логики и сравнений: де Морган для {@code not(cmp)}
 * (для вещественных только {@code eq/ne}, из-за NaN), {@code select} с константным условием и т.д.</p>
 *
 * <p><b>Стейтменты</b>: {@code if} с константным условием, пустые ветки, {@code while(false)}, {@code do{}while(false)},
 * недостижимый код после {@code return/break/continue}, вложенные блоки без объявлений, самоприсваивание.</p>
 */
final class Simplify {

    /** Пустой блок - «ничего». */
    static final Tree EMPTY = Tree.make(Op.BLOCK, Types.VOID, 0, 0, Tree.NONE, Tree.NO_INTS);

    private static final int FMA = Builtins.idOf("fma");
    private static final int MIN = Builtins.idOf("min");
    private static final int MAX = Builtins.idOf("max");

    private final Effects fx;
    private final OptimizeOptions opts;
    private final IrModule src;

    Simplify(Effects fx, OptimizeOptions opts, IrModule src) {
        this.fx = fx;
        this.opts = opts;
        this.src = src;
    }

    // =============================================================================================
    // выражения
    // =============================================================================================

    /**
     * Упрощает выражение.
     *
     * @param t выражение
     * @return упрощённое выражение (то же, если упрощать нечего)
     */
    Tree expr(Tree t) {
        if (t.kids.length == 0) {
            return t;
        }
        Tree[] nk = null;
        for (int i = 0; i < t.kids.length; i++) {
            Tree c = t.kids[i];
            Tree r = expr(c);
            if (r != c) {
                if (nk == null) {
                    nk = t.kids.clone();
                }
                nk[i] = r;
            }
        }
        Tree cur = nk == null ? t : t.withKids(nk);
        for (int guard = 0; guard < 16; guard++) {
            Tree next = rule(cur);
            if (next == cur) {
                break;
            }
            cur = next;
        }
        return cur;
    }

    private boolean pure(Tree t) {
        return fx.isRemovable(t);
    }

    private static boolean isIntConst(Tree t, long v) {
        return t.op == Op.CONST_INT && t.k == v;
    }

    private static boolean isBoolConst(Tree t, boolean v) {
        return t.op == Op.CONST_BOOL && (t.k != 0) == v;
    }

    private static boolean isFloatConst(Tree t, double v) {
        return t.op == Op.CONST_FLOAT && t.k == Eval.fromDouble(t.type, v);
    }

    /** Для знаковых - положительная степень двойки больше 1; для беззнаковых - любая степень двойки больше 1. */
    private static int log2Pow2(Tree c) {
        if (c.op != Op.CONST_INT) {
            return -1;
        }
        long v = c.k;
        boolean unsigned = c.type == Types.U32 || c.type == Types.U64;
        if (c.type == Types.U32) {
            v &= 0xFFFFFFFFL;
        }
        if (!unsigned && v <= 0) {
            return -1;
        }
        if (v == 0 || v == 1 || (v & (v - 1)) != 0) {
            return -1;
        }
        return Long.numberOfTrailingZeros(v);
    }

    private static boolean isArithInt(int t) {
        return t == Types.I32 || t == Types.U32 || t == Types.I64 || t == Types.U64;
    }

    private static boolean isUnsigned(int t) {
        return t == Types.U32 || t == Types.U64;
    }

    private Tree zero(Tree like, int type) {
        return Tree.constLike(like, type, 0);
    }

    private Tree rule(Tree t) {
        switch (t.op) {
            case Op.ADD, Op.SUB, Op.MUL, Op.DIV, Op.REM -> {
                return arith(t);
            }
            case Op.AND, Op.OR, Op.XOR -> {
                return bitwise(t);
            }
            case Op.SHL, Op.SHR, Op.USHR -> {
                return shift(t);
            }
            case Op.NEG -> {
                return neg(t);
            }
            case Op.NOT -> {
                return not(t);
            }
            case Op.EQ, Op.NE, Op.LT, Op.LE, Op.GT, Op.GE -> {
                return compare(t);
            }
            case Op.SELECT -> {
                return select(t);
            }
            case Op.CONVERT -> {
                return convert(t);
            }
            case Op.BITCAST -> {
                return bitcast(t);
            }
            case Op.EXTRACT -> {
                return extract(t);
            }
            case Op.FIELD_GET -> {
                return fieldGet(t);
            }
            case Op.BUILD -> {
                return build(t);
            }
            case Op.SHUFFLE -> {
                return shuffle(t);
            }
            case Op.INTRINSIC -> {
                return intrinsic(t);
            }
            default -> {
                return t;
            }
        }
    }

    // ---- арифметика ----------------------------------------------------------------------------

    private static boolean commutative(int op) {
        return op == Op.ADD || op == Op.MUL || op == Op.AND || op == Op.OR || op == Op.XOR || op == Op.EQ
                || op == Op.NE;
    }

    /** Константу - вправо, чтобы правила имели один вид. */
    private Tree canonical(Tree t) {
        Tree a = t.kids[0];
        Tree b = t.kids[1];
        if (a.isConst() && !b.isConst()) {
            if (commutative(t.op)) {
                return t.retarget(t.op, b, a);
            }
            int mirrored = switch (t.op) {
                case Op.LT -> Op.GT;
                case Op.GT -> Op.LT;
                case Op.LE -> Op.GE;
                case Op.GE -> Op.LE;
                default -> -1;
            };
            if (mirrored > 0) {
                return t.retarget(mirrored, b, a);
            }
        }
        return t;
    }

    private Tree arith(Tree t) {
        Tree c = canonical(t);
        if (c != t) {
            return c;
        }
        Tree a = t.kids[0];
        Tree b = t.kids[1];
        int ty = t.type;
        if (a.isConst() && b.isConst() && Eval.isArith(ty) && !Eval.divByZero(t.op, ty, b.k)) {
            return Tree.constLike(t, ty, Eval.arith(t.op, ty, a.k, b.k));
        }
        if (isArithInt(ty)) {
            return arithInt(t, a, b, ty);
        }
        if (Types.isFloat(ty)) {
            return arithFloat(t, a, b, ty);
        }
        if (opts.fastMath()) {
            return contract(t, a, b);
        }
        return t;
    }

    private Tree arithInt(Tree t, Tree a, Tree b, int ty) {
        switch (t.op) {
            case Op.ADD -> {
                if (isIntConst(b, 0)) {
                    return a;
                }
                if (b.op == Op.CONST_INT && a.op == Op.ADD && a.kids[1].op == Op.CONST_INT) {
                    return t.retarget(Op.ADD, a.kids[0], Tree.constLike(t, ty,
                            Eval.arith(Op.ADD, ty, a.kids[1].k, b.k)));
                }
            }
            case Op.SUB -> {
                if (isIntConst(b, 0)) {
                    return a;
                }
                if (Tree.same(a, b) && pure(a)) {
                    return zero(t, ty);
                }
                if (isIntConst(a, 0)) {
                    return t.retarget(Op.NEG, b);
                }
            }
            case Op.MUL -> {
                if (isIntConst(b, 1)) {
                    return a;
                }
                if (isIntConst(b, 0) && pure(a)) {
                    return b;
                }
                int sh = log2Pow2(b);
                if (sh > 0) {
                    return t.retarget(Op.SHL, a, Tree.constLike(t, ty, sh));
                }
                if (b.op == Op.CONST_INT && a.op == Op.MUL && a.kids[1].op == Op.CONST_INT) {
                    return t.retarget(Op.MUL, a.kids[0], Tree.constLike(t, ty,
                            Eval.arith(Op.MUL, ty, a.kids[1].k, b.k)));
                }
            }
            case Op.DIV -> {
                if (isIntConst(b, 1)) {
                    return a;
                }
                int sh = log2Pow2(b);
                if (sh > 0 && isUnsigned(ty)) {
                    return t.retarget(Op.SHR, a, Tree.constLike(t, ty, sh));
                }
            }
            default -> {
                // REM
                if (isIntConst(b, 1) && pure(a)) {
                    return zero(t, ty);
                }
                int sh = log2Pow2(b);
                if (sh > 0 && isUnsigned(ty)) {
                    return t.retarget(Op.AND, a, Tree.constLike(t, ty, Eval.arith(Op.SUB, ty, b.k, 1)));
                }
            }
        }
        return t;
    }

    private Tree arithFloat(Tree t, Tree a, Tree b, int ty) {
        boolean fast = opts.fastMath();
        switch (t.op) {
            case Op.ADD -> {
                if (isFloatConst(b, -0.0) || (fast && isFloatConst(b, 0.0))) {
                    return a;
                }
            }
            case Op.SUB -> {
                if (isFloatConst(b, 0.0) || (fast && isFloatConst(b, -0.0))) {
                    return a;
                }
                if (fast && Tree.same(a, b) && pure(a)) {
                    return zero(t, ty);
                }
            }
            case Op.MUL -> {
                if (isFloatConst(b, 1.0)) {
                    return a;
                }
                if (isFloatConst(b, -1.0)) {
                    return t.retarget(Op.NEG, a);
                }
                if (fast && (isFloatConst(b, 0.0) || isFloatConst(b, -0.0)) && pure(a)) {
                    return b;
                }
            }
            case Op.DIV -> {
                if (isFloatConst(b, 1.0)) {
                    return a;
                }
                if (isFloatConst(b, -1.0)) {
                    return t.retarget(Op.NEG, a);
                }
                if (b.op == Op.CONST_FLOAT) {
                    Tree recip = reciprocal(t, b, ty, fast);
                    if (recip != null) {
                        return t.retarget(Op.MUL, a, recip);
                    }
                }
            }
            default -> {
            }
        }
        return fast ? contract(t, a, b) : t;
    }

    /** {@code 1/c} как константа, если она точна (степень двойки) либо разрешена {@code fastMath}. */
    private Tree reciprocal(Tree like, Tree c, int ty, boolean fast) {
        double d = Eval.toDouble(ty, c.k);
        if (d == 0 || Double.isNaN(d) || Double.isInfinite(d)) {
            return null;
        }
        double r = 1.0 / d;
        long bits = Eval.fromDouble(ty, r);
        double back = Eval.toDouble(ty, bits);
        boolean normal = ty == Types.F32 ? Math.abs((float) r) >= Float.MIN_NORMAL && !Float.isInfinite((float) r)
                : Math.abs(r) >= Double.MIN_NORMAL && !Double.isInfinite(r);
        boolean exact = back == r && normal && (Double.doubleToRawLongBits(d) & 0x000FFFFFFFFFFFFFL) == 0;
        if (exact || (fast && normal)) {
            return Tree.constLike(like, ty, bits);
        }
        return null;
    }

    /** Слияние {@code a*b + c} в {@code fma(a, b, c)} (меняет округление; только при {@code fastMath}). */
    private Tree contract(Tree t, Tree a, Tree b) {
        if (!opts.contractFma() || FMA < 0 || (t.op != Op.ADD && t.op != Op.SUB)) {
            return t;
        }
        int scalar = Types.isVector(src, t.type) ? Types.scalarOf(src, t.type) : t.type;
        if (!Types.isFloat(scalar)) {
            return t;
        }
        if (a.op == Op.MUL) {
            Tree c = t.op == Op.ADD ? b : Tree.of(Op.NEG, t.type, b);
            return Tree.of(Op.INTRINSIC, t.type, a.kids[0], a.kids[1], c).withImm(FMA);
        }
        if (b.op == Op.MUL) {
            if (t.op == Op.ADD) {
                return Tree.of(Op.INTRINSIC, t.type, b.kids[0], b.kids[1], a).withImm(FMA);
            }
            return Tree.of(Op.INTRINSIC, t.type, Tree.of(Op.NEG, t.type, b.kids[0]), b.kids[1], a).withImm(FMA);
        }
        return t;
    }

    // ---- побитовые, сдвиги ---------------------------------------------------------------------

    private Tree bitwise(Tree t) {
        Tree c = canonical(t);
        if (c != t) {
            return c;
        }
        Tree a = t.kids[0];
        Tree b = t.kids[1];
        int ty = t.type;
        if (a.isConst() && b.isConst()) {
            return Tree.constLike(t, ty, Eval.bitwise(t.op, ty, a.k, b.k));
        }
        if (ty == Types.BOOL) {
            boolean same = Tree.same(a, b);
            switch (t.op) {
                case Op.AND -> {
                    if (isBoolConst(b, true)) {
                        return a;
                    }
                    if (isBoolConst(b, false) && pure(a)) {
                        return b;
                    }
                    if (same) {
                        return a;
                    }
                }
                case Op.OR -> {
                    if (isBoolConst(b, false)) {
                        return a;
                    }
                    if (isBoolConst(b, true) && pure(a)) {
                        return b;
                    }
                    if (same) {
                        return a;
                    }
                }
                default -> {
                    if (isBoolConst(b, false)) {
                        return a;
                    }
                    if (isBoolConst(b, true)) {
                        return t.retarget(Op.NOT, a);
                    }
                    if (same && pure(a)) {
                        return Tree.constLike(t, Types.BOOL, 0);
                    }
                }
            }
            return t;
        }
        if (!Types.isInteger(ty)) {
            return t;
        }
        long ones = Eval.canon(ty, -1L);
        switch (t.op) {
            case Op.AND -> {
                if (isIntConst(b, 0) && pure(a)) {
                    return b;
                }
                if (isIntConst(b, ones) || Tree.same(a, b)) {
                    return a;
                }
            }
            case Op.OR -> {
                if (isIntConst(b, 0) || Tree.same(a, b)) {
                    return a;
                }
                if (isIntConst(b, ones) && pure(a)) {
                    return b;
                }
            }
            default -> {
                if (isIntConst(b, 0)) {
                    return a;
                }
                if (Tree.same(a, b) && pure(a)) {
                    return zero(t, ty);
                }
            }
        }
        return t;
    }

    private Tree shift(Tree t) {
        Tree a = t.kids[0];
        Tree b = t.kids[1];
        if (a.isConst() && b.isConst()) {
            return Tree.constLike(t, t.type, Eval.canon(t.type, Eval.shift(t.op, t.type, a.k, b.k)));
        }
        if (b.op == Op.CONST_INT) {
            int width = Types.bits(t.type);
            if ((b.k & (width - 1)) == 0) {
                return a;
            }
        }
        return t;
    }

    private Tree neg(Tree t) {
        Tree a = t.kids[0];
        if (a.isConst() && Eval.isArith(t.type)) {
            return Tree.constLike(t, t.type, Eval.neg(t.type, a.k));
        }
        if (a.op == Op.NEG) {
            return a.kids[0];
        }
        return t;
    }

    private Tree not(Tree t) {
        Tree a = t.kids[0];
        if (a.isConst()) {
            return Tree.constLike(t, t.type, Eval.not(t.type, a.k));
        }
        if (a.op == Op.NOT) {
            return a.kids[0];
        }
        if (Op.isCompare(a.op) && t.type == Types.BOOL) {
            int inverse = switch (a.op) {
                case Op.EQ -> Op.NE;
                case Op.NE -> Op.EQ;
                case Op.LT -> Op.GE;
                case Op.GE -> Op.LT;
                case Op.GT -> Op.LE;
                default -> Op.GT;
            };
            boolean floating = Types.isFloat(a.kids[0].type);
            if (!floating || inverse == Op.NE || inverse == Op.EQ) {
                return a.retarget(inverse, a.kids[0], a.kids[1]);
            }
        }
        return t;
    }

    private Tree compare(Tree t) {
        Tree c = canonical(t);
        if (c != t) {
            return c;
        }
        Tree a = t.kids[0];
        Tree b = t.kids[1];
        int ty = a.type;
        if (a.isConst() && b.isConst()) {
            return Tree.constLike(t, Types.BOOL, Eval.compare(t.op, ty, a.k, b.k));
        }
        if (ty == Types.BOOL && b.op == Op.CONST_BOOL) {
            boolean keep = (t.op == Op.EQ) == (b.k != 0);
            if (t.op == Op.EQ || t.op == Op.NE) {
                return keep ? a : Tree.of(Op.NOT, Types.BOOL, a);
            }
        }
        if (!Types.isFloat(ty) && Tree.same(a, b) && pure(a)) {
            boolean truth = t.op == Op.EQ || t.op == Op.LE || t.op == Op.GE;
            return Tree.constLike(t, Types.BOOL, truth ? 1 : 0);
        }
        if (isUnsigned(ty) && isIntConst(b, 0) && pure(a)) {
            if (t.op == Op.LT) {
                return Tree.constLike(t, Types.BOOL, 0);
            }
            if (t.op == Op.GE) {
                return Tree.constLike(t, Types.BOOL, 1);
            }
        }
        return t;
    }

    private Tree select(Tree t) {
        Tree c = t.kids[0];
        Tree x = t.kids[1];
        Tree y = t.kids[2];
        if (c.op == Op.CONST_BOOL) {
            return c.k != 0 ? x : y;
        }
        if (Tree.same(x, y) && pure(c)) {
            return x;
        }
        if (t.type == Types.BOOL) {
            if (isBoolConst(x, true) && isBoolConst(y, false)) {
                return c;
            }
            if (isBoolConst(x, false) && isBoolConst(y, true)) {
                return Tree.of(Op.NOT, Types.BOOL, c);
            }
        }
        if (c.op == Op.NOT) {
            return t.retarget(Op.SELECT, c.kids[0], y, x);
        }
        return t;
    }

    // ---- преобразования ------------------------------------------------------------------------

    private static boolean scalarKind(int t) {
        return Types.isScalar(t);
    }

    private Tree convert(Tree t) {
        Tree a = t.kids[0];
        if (a.type == t.type) {
            return a;
        }
        if (a.isConst() && scalarKind(a.type) && scalarKind(t.type)) {
            return Tree.constLike(t, t.type, Eval.convert(a.type, t.type, a.k));
        }
        if (a.op == Op.CONVERT && a.kids[0].type == t.type) {
            int from = t.type;
            int via = a.type;
            boolean int32 = from == Types.I32 || from == Types.U32;
            boolean lossless = (int32 && (via == Types.I64 || via == Types.U64 || via == Types.F64))
                    || (from == Types.F32 && via == Types.F64);
            if (lossless) {
                return a.kids[0];
            }
        }
        return t;
    }

    private Tree bitcast(Tree t) {
        Tree a = t.kids[0];
        if (a.type == t.type) {
            return a;
        }
        if (a.isConst() && scalarKind(a.type) && scalarKind(t.type)) {
            return Tree.constLike(t, t.type, Eval.bitcast(t.type, a.k));
        }
        if (a.op == Op.BITCAST && a.kids[0].type == t.type) {
            return a.kids[0];
        }
        return t;
    }

    // ---- векторы и struct ----------------------------------------------------------------------

    private Tree extract(Tree t) {
        Tree v = t.kids[0];
        switch (v.op) {
            case Op.BUILD -> {
                return v.kids[t.imm];
            }
            case Op.SPLAT -> {
                return v.kids[0];
            }
            case Op.INSERT -> {
                return v.imm == t.imm ? v.kids[1] : t.withKid(0, v.kids[0]);
            }
            default -> {
                return t;
            }
        }
    }

    private Tree fieldGet(Tree t) {
        Tree s = t.kids[0];
        switch (s.op) {
            case Op.STRUCT_BUILD -> {
                return s.kids[t.imm];
            }
            case Op.FIELD_SET -> {
                return s.imm == t.imm ? s.kids[1] : t.withKid(0, s.kids[0]);
            }
            default -> {
                return t;
            }
        }
    }

    private Tree build(Tree t) {
        Tree first = t.kids[0];
        if (first.op != Op.EXTRACT) {
            return t;
        }
        Tree v = first.kids[0];
        if (v.type != t.type) {
            return t;
        }
        for (int i = 0; i < t.kids.length; i++) {
            Tree e = t.kids[i];
            if (e.op != Op.EXTRACT || e.imm != i || !Tree.same(e.kids[0], v)) {
                return t;
            }
        }
        return v;
    }

    private Tree shuffle(Tree t) {
        Tree v = t.kids[0];
        if (v.type == t.type) {
            for (int i = 0; i < t.ints.length; i++) {
                if (t.ints[i] != i) {
                    return t;
                }
            }
            return v;
        }
        return t;
    }

    // ---- builtins ------------------------------------------------------------------------------

    private Tree intrinsic(Tree t) {
        if (t.imm == FMA && t.kids.length == 3 && Types.isFloat(t.type)) {
            Tree a = t.kids[0];
            Tree b = t.kids[1];
            Tree c = t.kids[2];
            if (a.isConst() && b.isConst() && c.isConst()) {
                return Tree.constLike(t, t.type, Eval.fma(t.type, a.k, b.k, c.k));
            }
        }
        if ((t.imm == MIN || t.imm == MAX) && t.kids.length == 2 && isArithInt(t.type)) {
            Tree a = t.kids[0];
            Tree b = t.kids[1];
            if (a.isConst() && b.isConst()) {
                boolean aLess = Eval.compare(Op.LT, t.type, a.k, b.k) != 0;
                return (t.imm == MIN) == aLess ? a : b;
            }
            if (Tree.same(a, b) && pure(a)) {
                return a;
            }
        }
        return t;
    }

    // =============================================================================================
    // стейтменты
    // =============================================================================================

    /**
     * Упрощает стейтмент; «ничего» возвращается как {@link #EMPTY}.
     *
     * @param t стейтмент
     * @return упрощённый стейтмент
     */
    Tree stmt(Tree t) {
        switch (t.op) {
            case Op.BLOCK -> {
                return block(t);
            }
            case Op.IF -> {
                return ifStmt(t);
            }
            case Op.LOOP -> {
                return loop(t);
            }
            case Op.DO_WHILE -> {
                return doWhile(t);
            }
            case Op.SWITCH -> {
                Tree[] nk = t.kids.clone();
                nk[0] = expr(nk[0]);
                for (int i = 1; i < nk.length; i++) {
                    nk[i] = stmt(nk[i]);
                }
                return t.withKids(nk);
            }
            case Op.EXPR_STMT -> {
                Tree e = expr(t.kids[0]);
                return pure(e) ? EMPTY : t.withKid(0, e);
            }
            case Op.LOCAL_SET -> {
                Tree v = expr(t.kids[0]);
                if (v.op == Op.LOCAL_GET && v.imm == t.imm) {
                    return EMPTY;
                }
                return t.withKid(0, v);
            }
            case Op.LOCAL_DECL, Op.STORE, Op.RETURN, Op.PRIVATE_ARRAY_DECL, Op.LOCAL_ARRAY_DECL -> {
                if (t.kids.length == 0) {
                    return t;
                }
                Tree[] nk = t.kids.clone();
                for (int i = 0; i < nk.length; i++) {
                    nk[i] = expr(nk[i]);
                }
                return t.withKids(nk);
            }
            default -> {
                return t;
            }
        }
    }

    static boolean isEmpty(Tree t) {
        return t.op == Op.BLOCK && t.kids.length == 0;
    }

    static boolean isDecl(Tree t) {
        return t.op == Op.LOCAL_DECL || t.op == Op.PRIVATE_ARRAY_DECL || t.op == Op.LOCAL_ARRAY_DECL;
    }

    /**
     * Прерывает ли стейтмент поток управления во всех путях (за ним код недостижим).
     *
     * @param t стейтмент
     * @return {@code true}, если после стейтмента управление никогда не продолжается
     */
    static boolean terminates(Tree t) {
        switch (t.op) {
            case Op.RETURN, Op.BREAK, Op.CONTINUE -> {
                return true;
            }
            case Op.BLOCK -> {
                for (Tree c : t.kids) {
                    if (terminates(c)) {
                        return true;
                    }
                }
                return false;
            }
            case Op.IF -> {
                return t.kids.length == 3 && terminates(t.kids[1]) && terminates(t.kids[2]);
            }
            default -> {
                return false;
            }
        }
    }

    private Tree block(Tree t) {
        List<Tree> out = new ArrayList<>(t.kids.length);
        for (Tree c : t.kids) {
            Tree s = stmt(c);
            if (isEmpty(s)) {
                continue;
            }
            if (s.op == Op.BLOCK && !hasDirectDecl(s)) {
                out.addAll(java.util.Arrays.asList(s.kids));
            } else {
                out.add(s);
            }
            if (terminates(s)) {
                // код после прерывающего стейтмента недостижим
                break;
            }
        }
        // withKids вернёт сам блок, если список не изменился
        return t.withKids(out.toArray(Tree.NONE));
    }

    private static boolean hasDirectDecl(Tree block) {
        for (Tree c : block.kids) {
            if (isDecl(c)) {
                return true;
            }
        }
        return false;
    }

    /** Тело ветки/цикла: блок из одного не-объявления заменяется самим стейтментом. */
    private static Tree unwrap(Tree s) {
        if (s.op == Op.BLOCK && s.kids.length == 1 && !isDecl(s.kids[0])) {
            return s.kids[0];
        }
        return s;
    }

    private Tree ifStmt(Tree t) {
        Tree cond = expr(t.kids[0]);
        Tree thenB = unwrap(stmt(t.kids[1]));
        Tree elseB = t.kids.length == 3 ? unwrap(stmt(t.kids[2])) : null;
        if (cond.op == Op.CONST_BOOL) {
            Tree pick = cond.k != 0 ? thenB : elseB;
            return pick == null ? EMPTY : asScope(pick);
        }
        if (elseB != null && isEmpty(elseB)) {
            elseB = null;
        }
        if (isEmpty(thenB)) {
            if (elseB == null) {
                return pure(cond) ? EMPTY : Tree.of(Op.EXPR_STMT, Types.VOID, cond);
            }
            // if (c) {} else B  ->  if (!c) B
            Tree nc = expr(Tree.of(Op.NOT, Types.BOOL, cond));
            return rebuildIf(t, nc, elseB, null);
        }
        if (elseB != null && cond.op == Op.NOT) {
            return rebuildIf(t, cond.kids[0], elseB, thenB);
        }
        return rebuildIf(t, cond, thenB, elseB);
    }

    private static Tree rebuildIf(Tree orig, Tree cond, Tree thenB, Tree elseB) {
        Tree[] nk = elseB == null ? new Tree[]{cond, thenB} : new Tree[]{cond, thenB, elseB};
        return orig.withKids(nk);
    }

    /** Выбранная ветка становится самостоятельным стейтментом; блок сохраняет область видимости объявлений. */
    private static Tree asScope(Tree s) {
        if (isDecl(s)) {
            return Tree.of(Op.BLOCK, Types.VOID, s);
        }
        return s;
    }

    private Tree loop(Tree t) {
        Tree cond = expr(t.kids[0]);
        if (isBoolConst(cond, false)) {
            return EMPTY;
        }
        Tree[] nk = t.kids.clone();
        nk[0] = cond;
        nk[1] = unwrap(stmt(t.kids[1]));
        if (nk.length == 3) {
            Tree upd = unwrap(stmt(t.kids[2]));
            if (isEmpty(upd)) {
                nk = new Tree[]{nk[0], nk[1]};
            } else {
                nk[2] = upd;
            }
        }
        return t.withKids(nk);
    }

    private Tree doWhile(Tree t) {
        Tree body = unwrap(stmt(t.kids[0]));
        Tree cond = expr(t.kids[1]);
        if (isBoolConst(cond, false) && !hasLoopControl(body, false, false)) {
            return body.op == Op.BLOCK ? body : Tree.of(Op.BLOCK, Types.VOID, body);
        }
        return t.withKids(new Tree[]{body, cond});
    }

    /**
     * Есть ли в теле {@code break/continue}, привязанные к охватывающему циклу.
     *
     * @param t        стейтмент
     * @param inLoop   находимся ли внутри вложенного цикла
     * @param inSwitch находимся ли внутри вложенного switch
     * @return {@code true}, если найден привязанный к внешнему циклу переход
     */
    static boolean hasLoopControl(Tree t, boolean inLoop, boolean inSwitch) {
        switch (t.op) {
            case Op.BREAK -> {
                return !inLoop && !inSwitch;
            }
            case Op.CONTINUE -> {
                return !inLoop;
            }
            default -> {
                boolean nl = inLoop || t.op == Op.LOOP || t.op == Op.DO_WHILE;
                boolean ns = inSwitch || t.op == Op.SWITCH;
                for (Tree c : t.kids) {
                    if (c.isStmt() && hasLoopControl(c, nl, ns)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }
}
