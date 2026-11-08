package com.sixik.gpu.frontend.java;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Вычисление целочисленных константных выражений Java (размеры массивов, метки {@code case}).
 *
 * <p>Поддержаны литералы, скобки, унарные {@code - + ~}, бинарные арифметические, побитовые и сдвиги, приведения к
 * целым типам и поля/переменные с константным значением ({@code getConstantValue}). Результат обёртывается по
 * правилам Java для типа выражения ({@code int} - 32 бита, {@code long} - 64, {@code short/byte/char} - свои
 * диапазоны), поэтому {@code Integer.MAX_VALUE + 1} даёт {@code Integer.MIN_VALUE}, как в javac.</p>
 */
final class ConstEval {

    private ConstEval() {
    }

    /**
     * @param trees API деревьев
     * @param p     путь к выражению
     * @return значение выражения либо {@code null}, если оно не является целочисленной константой
     */
    static Long evalIntegral(Trees trees, TreePath p) {
        Long raw = eval(trees, p);
        if (raw == null) {
            return null;
        }
        TypeMirror t = trees.getTypeMirror(p);
        return t == null ? raw : wrap(t.getKind(), raw);
    }

    private static Long wrap(TypeKind kind, long v) {
        return switch (kind) {
            case INT -> (long) (int) v;
            case SHORT -> (long) (short) v;
            case BYTE -> (long) (byte) v;
            case CHAR -> (long) (char) v;
            default -> v;
        };
    }

    private static Long eval(Trees trees, TreePath p) {
        switch (p.getLeaf().getKind()) {
            case INT_LITERAL, LONG_LITERAL, CHAR_LITERAL -> {
                Object v = ((LiteralTree) p.getLeaf()).getValue();
                return v instanceof Character c ? (long) c : ((Number) v).longValue();
            }
            case PARENTHESIZED -> {
                return evalIntegral(trees, child(p, ((ParenthesizedTree) p.getLeaf()).getExpression()));
            }
            case UNARY_MINUS, UNARY_PLUS, BITWISE_COMPLEMENT -> {
                UnaryTree u = (UnaryTree) p.getLeaf();
                Long x = evalIntegral(trees, child(p, u.getExpression()));
                if (x == null) {
                    return null;
                }
                return switch (u.getKind()) {
                    case UNARY_MINUS -> -x;
                    case UNARY_PLUS -> x;
                    default -> ~x;
                };
            }
            case TYPE_CAST -> {
                TypeCastTree c = (TypeCastTree) p.getLeaf();
                TypeMirror target = trees.getTypeMirror(child(p, c.getType()));
                Long x = evalIntegral(trees, child(p, c.getExpression()));
                if (x == null || target == null || !isIntegralKind(target.getKind())) {
                    return null;
                }
                return wrap(target.getKind(), x);
            }
            case IDENTIFIER, MEMBER_SELECT -> {
                Element e = trees.getElement(p);
                if (e instanceof VariableElement ve && ve.getConstantValue() != null) {
                    Object v = ve.getConstantValue();
                    if (v instanceof Character c) {
                        return (long) c;
                    }
                    if (v instanceof Number n && !(v instanceof Float) && !(v instanceof Double)) {
                        return n.longValue();
                    }
                }
                return null;
            }
            case PLUS, MINUS, MULTIPLY, DIVIDE, REMAINDER, LEFT_SHIFT, RIGHT_SHIFT, UNSIGNED_RIGHT_SHIFT, AND, OR,
                 XOR -> {
                BinaryTree bt = (BinaryTree) p.getLeaf();
                Long l = evalIntegral(trees, child(p, bt.getLeftOperand()));
                Long r = evalIntegral(trees, child(p, bt.getRightOperand()));
                if (l == null || r == null) {
                    return null;
                }
                return binary(trees, p, bt.getKind(), l, r);
            }
            default -> {
                return null;
            }
        }
    }

    private static Long binary(Trees trees, TreePath p, Tree.Kind kind, long l, long r) {
        TypeMirror lt = trees.getTypeMirror(child(p, ((BinaryTree) p.getLeaf()).getLeftOperand()));
        boolean leftLong = lt != null && lt.getKind() == TypeKind.LONG;
        return switch (kind) {
            case PLUS -> l + r;
            case MINUS -> l - r;
            case MULTIPLY -> l * r;
            case DIVIDE -> r == 0 ? null : (Long) (l / r);
            case REMAINDER -> r == 0 ? null : (Long) (l % r);
            case AND -> l & r;
            case OR -> l | r;
            case XOR -> l ^ r;
            case LEFT_SHIFT -> leftLong ? l << (r & 63) : (long) ((int) l << (int) (r & 31));
            case RIGHT_SHIFT -> leftLong ? l >> (r & 63) : (long) ((int) l >> (int) (r & 31));
            case UNSIGNED_RIGHT_SHIFT -> leftLong ? l >>> (r & 63) : (long) ((int) l >>> (int) (r & 31));
            default -> null;
        };
    }

    private static boolean isIntegralKind(TypeKind k) {
        return k == TypeKind.INT || k == TypeKind.LONG || k == TypeKind.SHORT || k == TypeKind.BYTE
                || k == TypeKind.CHAR;
    }

    private static TreePath child(TreePath parent, Tree t) {
        return new TreePath(parent, t);
    }
}
