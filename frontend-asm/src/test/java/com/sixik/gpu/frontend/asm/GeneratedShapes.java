package com.sixik.gpu.frontend.asm;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Map;

/**
 * Байткод формы генератора (шаг B7, FRONTEND_ASM_RUNTIME.md, S9 и 18.8a): класс {@code g/Gen} с ядром
 * {@code static void k(float[] out, float[] in)} без LVT, каждая конструкция - на своей строке. javac глубже 512 не
 * собирает, поэтому предел глубины меряется такими классами.
 *
 * <p>Пролог: {@code int i = Gpu.getGlobalId(0); float x = in[i];} (слоты 2 и 3); эпилог: {@code out[i] = x;}.</p>
 */
final class GeneratedShapes {

    static final String OWNER = "g/Gen";
    static final String DESC = "([F[F)V";
    private static final int X = 3;

    private GeneratedShapes() {
    }

    /** Номера строк: у каждой конструкции свой («идентификатор узла» генератора). */
    private static final class Lines {
        int next = 1;

        void mark(MethodVisitor mv) {
            Label l = new Label();
            mv.visitLabel(l);
            mv.visitLineNumber(next++, l);
        }
    }

    private static ClassNode kernel(int maxLocals, java.util.function.BiConsumer<MethodVisitor, Lines> body) {
        ClassNode cn = new ClassNode();
        cn.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, OWNER, null, "java/lang/Object", null);
        cn.visitSource("router/probe", null);
        MethodVisitor mv = cn.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "k", DESC, null, null);
        mv.visitCode();
        Lines lines = new Lines();
        lines.mark(mv);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/sixik/gpu/api/Gpu", "getGlobalId", "(I)I", false);
        mv.visitVarInsn(Opcodes.ISTORE, 2);
        lines.mark(mv);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitInsn(Opcodes.FALOAD);
        mv.visitVarInsn(Opcodes.FSTORE, X);
        body.accept(mv, lines);
        lines.mark(mv);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitVarInsn(Opcodes.FLOAD, X);
        mv.visitInsn(Opcodes.FASTORE);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(8, maxLocals);
        mv.visitEnd();
        cn.visitEnd();
        return cn;
    }

    /** {@code x = x > 0.5 ? (x > 1.5 ? (... x * 0.5 ...) : x + 1) : x + 0} глубиной {@code depth} на стеке операндов. */
    static ClassNode nestedSelect(int depth) {
        return kernel(4, (mv, lines) -> {
            lines.mark(mv);
            selectTree(mv, depth, X, X, 0f);
            mv.visitVarInsn(Opcodes.FSTORE, X);
        });
    }

    /**
     * Оставляет на стеке {@code v > b ? (v > b+1 ? (...) : v + 1) : v + 0} глубиной {@code depth}; самое глубокое -
     * {@code w * 0.5}.
     */
    private static void selectTree(MethodVisitor mv, int depth, int v, int w, float base) {
        Label[] other = new Label[depth];
        Label[] end = new Label[depth];
        for (int d = 0; d < depth; d++) {
            other[d] = new Label();
            end[d] = new Label();
            mv.visitVarInsn(Opcodes.FLOAD, v);
            mv.visitLdcInsn(base + d + 0.5f);
            mv.visitInsn(Opcodes.FCMPL);
            mv.visitJumpInsn(Opcodes.IFLE, other[d]);
        }
        mv.visitVarInsn(Opcodes.FLOAD, w);
        mv.visitLdcInsn(0.5f);
        mv.visitInsn(Opcodes.FMUL);
        for (int d = depth - 1; d >= 0; d--) {
            mv.visitJumpInsn(Opcodes.GOTO, end[d]);
            mv.visitLabel(other[d]);
            mv.visitVarInsn(Opcodes.FLOAD, v);
            mv.visitLdcInsn((float) d);
            mv.visitInsn(Opcodes.FADD);
            mv.visitLabel(end[d]);
        }
    }

    /** {@code if (x > d + 0.5) { x = x * 0.5 + 1; if (...) {...} } else { x = x - d; }} глубиной {@code depth}. */
    static ClassNode nestedIf(int depth) {
        return kernel(4, (mv, lines) -> {
            Label[] other = new Label[depth];
            Label[] end = new Label[depth];
            for (int d = 0; d < depth; d++) {
                other[d] = new Label();
                end[d] = new Label();
                lines.mark(mv);
                mv.visitVarInsn(Opcodes.FLOAD, X);
                mv.visitLdcInsn(d + 0.5f);
                mv.visitInsn(Opcodes.FCMPL);
                mv.visitJumpInsn(Opcodes.IFLE, other[d]);
                lines.mark(mv);
                mv.visitVarInsn(Opcodes.FLOAD, X);
                mv.visitLdcInsn(0.5f);
                mv.visitInsn(Opcodes.FMUL);
                mv.visitInsn(Opcodes.FCONST_1);
                mv.visitInsn(Opcodes.FADD);
                mv.visitVarInsn(Opcodes.FSTORE, X);
            }
            for (int d = depth - 1; d >= 0; d--) {
                mv.visitJumpInsn(Opcodes.GOTO, end[d]);
                mv.visitLabel(other[d]);
                lines.mark(mv);
                mv.visitVarInsn(Opcodes.FLOAD, X);
                mv.visitLdcInsn((float) d);
                mv.visitInsn(Opcodes.FSUB);
                mv.visitVarInsn(Opcodes.FSTORE, X);
                mv.visitLabel(end[d]);
            }
        });
    }

    /** {@code depth} вложенных {@code while (j < 1) { ...; j++; }} со своим счётчиком у каждого. */
    static ClassNode nestedLoops(int depth) {
        return kernel(4 + depth, (mv, lines) -> {
            Label[] head = new Label[depth];
            Label[] exit = new Label[depth];
            for (int d = 0; d < depth; d++) {
                head[d] = new Label();
                exit[d] = new Label();
                lines.mark(mv);
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitVarInsn(Opcodes.ISTORE, 4 + d);
                mv.visitLabel(head[d]);
                lines.mark(mv);
                mv.visitVarInsn(Opcodes.ILOAD, 4 + d);
                mv.visitInsn(Opcodes.ICONST_1);
                mv.visitJumpInsn(Opcodes.IF_ICMPGE, exit[d]);
            }
            lines.mark(mv);
            mv.visitVarInsn(Opcodes.FLOAD, X);
            mv.visitInsn(Opcodes.FCONST_1);
            mv.visitInsn(Opcodes.FADD);
            mv.visitVarInsn(Opcodes.FSTORE, X);
            for (int d = depth - 1; d >= 0; d--) {
                lines.mark(mv);
                mv.visitIincInsn(4 + d, 1);
                mv.visitJumpInsn(Opcodes.GOTO, head[d]);
                mv.visitLabel(exit[d]);
            }
        });
    }

    /**
     * Роутер плотности размером {@code nodes} узлов (форма генератора GA2: узел - локаль {@code float}, своя строка):
     * арифметика над предыдущими узлами, {@code Math.max/min/abs/floor} (точные интринсики), выбор по диапазону
     * ({@code RangeChoice}: {@code v < a ? p : (v < b ? q : r)}) и вложенный {@code Select} глубиной до 16. Итог -
     * {@code x = последний узел}.
     */
    static ClassNode router(int nodes) {
        int base = 4;
        return kernel(base + nodes, (mv, lines) -> {
            for (int k = 0; k < nodes; k++) {
                lines.mark(mv);
                int p1 = k >= 1 ? base + k - 1 : X;
                int p2 = k >= 2 ? base + k - 2 : X;
                int p3 = k >= 3 ? base + k - 3 : X;
                float c = (k % 13) * 0.125f + 0.25f;
                switch (k % 5) {
                    case 0 -> {
                        // v = x * c + p1
                        mv.visitVarInsn(Opcodes.FLOAD, X);
                        mv.visitLdcInsn(c);
                        mv.visitInsn(Opcodes.FMUL);
                        mv.visitVarInsn(Opcodes.FLOAD, p1);
                        mv.visitInsn(Opcodes.FADD);
                    }
                    case 1 -> {
                        // v = max(p1, p2) - min(p3, c)
                        mv.visitVarInsn(Opcodes.FLOAD, p1);
                        mv.visitVarInsn(Opcodes.FLOAD, p2);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(FF)F", false);
                        mv.visitVarInsn(Opcodes.FLOAD, p3);
                        mv.visitLdcInsn(c);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(FF)F", false);
                        mv.visitInsn(Opcodes.FSUB);
                    }
                    case 2 -> {
                        // RangeChoice: p1 < c ? p2 : (p1 < c + 1 ? p3 * 0.5 : p1)
                        Label second = new Label();
                        Label last = new Label();
                        Label end = new Label();
                        mv.visitVarInsn(Opcodes.FLOAD, p1);
                        mv.visitLdcInsn(c);
                        mv.visitInsn(Opcodes.FCMPG);
                        mv.visitJumpInsn(Opcodes.IFGE, second);
                        mv.visitVarInsn(Opcodes.FLOAD, p2);
                        mv.visitJumpInsn(Opcodes.GOTO, end);
                        mv.visitLabel(second);
                        mv.visitVarInsn(Opcodes.FLOAD, p1);
                        mv.visitLdcInsn(c + 1f);
                        mv.visitInsn(Opcodes.FCMPG);
                        mv.visitJumpInsn(Opcodes.IFGE, last);
                        mv.visitVarInsn(Opcodes.FLOAD, p3);
                        mv.visitLdcInsn(0.5f);
                        mv.visitInsn(Opcodes.FMUL);
                        mv.visitJumpInsn(Opcodes.GOTO, end);
                        mv.visitLabel(last);
                        mv.visitVarInsn(Opcodes.FLOAD, p1);
                        mv.visitLabel(end);
                    }
                    case 3 -> {
                        // v = floor(abs(p1 * p3)) * 0.25 + c
                        mv.visitVarInsn(Opcodes.FLOAD, p1);
                        mv.visitVarInsn(Opcodes.FLOAD, p3);
                        mv.visitInsn(Opcodes.FMUL);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(F)F", false);
                        mv.visitInsn(Opcodes.F2D);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "floor", "(D)D", false);
                        mv.visitInsn(Opcodes.D2F);
                        mv.visitLdcInsn(0.25f);
                        mv.visitInsn(Opcodes.FMUL);
                        mv.visitLdcInsn(c);
                        mv.visitInsn(Opcodes.FADD);
                    }
                    default -> selectTree(mv, 1 + (k / 5) % 16, p1, p2, c);
                }
                mv.visitVarInsn(Opcodes.FSTORE, base + k);
            }
            lines.mark(mv);
            mv.visitVarInsn(Opcodes.FLOAD, nodes > 0 ? base + nodes - 1 : X);
            mv.visitVarInsn(Opcodes.FSTORE, X);
        });
    }

    /** Единица с одним сгенерированным классом и его ядром. */
    static AsmJit.Unit unit(ClassNode cn) {
        return new AsmJit.Unit(ClassSource.of(GeneratedShapes.class.getClassLoader()), Map.of(OWNER, cn),
                List.of(AsmJit.Entry.of(OWNER, "k", DESC)));
    }

    /** Синтез LVT - генератор её не пишет. */
    static final AsmJit.Options SYNTH = AsmJit.Options.DEFAULT.synthesizeLvt(true);
}
