package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.ir.Op;
import org.objectweb.asm.Opcodes;

/**
 * Условия переходов байткода → булевы выражения (REWORK_ARCHITECTURE.md, 17.9-1).
 *
 * <p>javac компилирует условие {@code if (c)} как переход <b>мимо</b> ветки при {@code !c}, поэтому структуризатору
 * постоянно нужно отрицание. {@link #neg} отрицает так, как это записал бы человек: целочисленное сравнение
 * переворачивается ({@code ge} → {@code lt}), снимается внешний {@code not}, а вещественное сравнение (кроме
 * {@code eq/ne}) оборачивается в {@code not}: из-за NaN {@code !(x < y)} не равно {@code x >= y}. Расхождение с
 * исходником вида {@code if (!(a >= b))} у целых - пункт (б) закрытого списка 17.14-3.</p>
 *
 * <p>{@code fcmpl/fcmpg} отличаются результатом на NaN ({@code -1}/{@code 1}): условие перехода строится точно -
 * либо прямое сравнение, если на NaN оно ложно так же, как переход не выполняется, либо {@code not} обратного.</p>
 */
final class Conds {

    /** {@code lcmp}: NaN не бывает. */
    static final int CMP_EXACT = 0;
    /** {@code fcmpl/dcmpl}: на NaN результат {@code -1}. */
    static final int CMP_NAN_LESS = 1;
    /** {@code fcmpg/dcmpg}: на NaN результат {@code 1}. */
    static final int CMP_NAN_GREATER = 2;

    private Conds() {
    }

    /**
     * @param opcode {@code if*}/{@code if_icmp*}
     * @return операция сравнения IR, которую проверяет переход
     */
    static int relation(int opcode) {
        return switch (opcode) {
            case Opcodes.IFEQ, Opcodes.IF_ICMPEQ, Opcodes.IF_ACMPEQ -> Op.EQ;
            case Opcodes.IFNE, Opcodes.IF_ICMPNE, Opcodes.IF_ACMPNE -> Op.NE;
            case Opcodes.IFLT, Opcodes.IF_ICMPLT -> Op.LT;
            case Opcodes.IFGE, Opcodes.IF_ICMPGE -> Op.GE;
            case Opcodes.IFGT, Opcodes.IF_ICMPGT -> Op.GT;
            case Opcodes.IFLE, Opcodes.IF_ICMPLE -> Op.LE;
            default -> throw new IllegalStateException("not a conditional jump: " + Insns.name(opcode));
        };
    }

    static boolean isCompare(int irOp) {
        return irOp >= Op.EQ && irOp <= Op.GE;
    }

    private static int flip(int irOp) {
        return switch (irOp) {
            case Op.EQ -> Op.NE;
            case Op.NE -> Op.EQ;
            case Op.LT -> Op.GE;
            case Op.GE -> Op.LT;
            case Op.GT -> Op.LE;
            default -> Op.GT;
        };
    }

    private static boolean floating(int kind) {
        return kind == Kinds.K_FLOAT || kind == Kinds.K_DOUBLE;
    }

    /**
     * Отрицание условия (см. описание класса).
     *
     * @param e булево выражение
     * @return {@code !e}
     */
    static Ex neg(Ex e) {
        if (e.op == Ex.NOT && e.kind == Kinds.K_BOOLEAN) {
            return e.kids[0];
        }
        if (e.op == Ex.CONST && e.kind == Kinds.K_BOOLEAN) {
            return Ex.constant(Kinds.K_BOOLEAN, e.bits == 0 ? 1 : 0);
        }
        if (e.op == Ex.BINARY && isCompare(e.aux)) {
            if (!floating(e.kids[0].kind) || e.aux == Op.EQ || e.aux == Op.NE) {
                return Ex.binary(flip(e.aux), Kinds.K_BOOLEAN, e.kids[0], e.kids[1]);
            }
        }
        if (e.op == Ex.LAND) {
            return Ex.logical(Ex.LOR, neg(e.kids[0]), neg(e.kids[1]));
        }
        if (e.op == Ex.LOR) {
            return Ex.logical(Ex.LAND, neg(e.kids[0]), neg(e.kids[1]));
        }
        return Ex.unary(Ex.NOT, Kinds.K_BOOLEAN, e);
    }

    /**
     * Булево значение выражения: само выражение вида {@code boolean} либо условие «ромба» {@code c ? 1 : 0}
     * ({@code c ? 0 : 1} - его отрицание), который javac строит для материализации {@code boolean}.
     *
     * @param v выражение
     * @return булево выражение либо {@code null}
     */
    static Ex boolValue(Ex v) {
        if (v.kind == Kinds.K_BOOLEAN) {
            return v;
        }
        if (v.op == Ex.SELECT && v.kind == Kinds.K_INT) {
            Ex a = v.kids[1];
            Ex b = v.kids[2];
            if (a.isIntConstant(1) && b.isIntConstant(0)) {
                return v.kids[0];
            }
            if (a.isIntConstant(0) && b.isIntConstant(1)) {
                return neg(v.kids[0]);
            }
        }
        return null;
    }

    /**
     * Сравнение с числовым повышением операндов (как {@code applyBinary} {@code frontend-java}).
     *
     * @param irOp операция сравнения
     * @param l    левый операнд
     * @param r    правый операнд
     * @return булево выражение
     */
    static Ex compare(int irOp, Ex l, Ex r) {
        Ex bl = boolValue(l);
        Ex br = boolValue(r);
        if (bl != null && br != null && (irOp == Op.EQ || irOp == Op.NE)) {
            return Ex.binary(irOp, Kinds.K_BOOLEAN, bl, br);
        }
        int pk = Kinds.binaryPromoted(l.kind, r.kind);
        return Ex.binary(irOp, Kinds.K_BOOLEAN, MethodLowerer.promoteOperand(l, r, pk),
                MethodLowerer.promoteOperand(r, l, pk));
    }

    /**
     * Условие перехода {@code if<rel>} над результатом трёхзначного сравнения.
     *
     * @param cmp  выражение {@link Ex#CMP3}
     * @param rel  отношение перехода к нулю
     * @return булево выражение «переход выполняется»
     */
    static Ex fromCmp3(Ex cmp, int rel) {
        Ex l = cmp.kids[0];
        Ex r = cmp.kids[1];
        if (cmp.aux == CMP_EXACT || rel == Op.EQ || rel == Op.NE) {
            return compare(rel, l, r);
        }
        // результат на NaN: -1 или 1; выполняется ли переход «результат rel 0» на NaN
        int nan = cmp.aux == CMP_NAN_LESS ? -1 : 1;
        boolean jumpsOnNan = switch (rel) {
            case Op.LT -> nan < 0;
            case Op.LE -> nan <= 0;
            case Op.GT -> nan > 0;
            default -> nan >= 0;
        };
        if (!jumpsOnNan) {
            return compare(rel, l, r); // упорядоченное сравнение IR на NaN ложно - как и переход
        }
        return Ex.unary(Ex.NOT, Kinds.K_BOOLEAN, compare(flip(rel), l, r));
    }
}
