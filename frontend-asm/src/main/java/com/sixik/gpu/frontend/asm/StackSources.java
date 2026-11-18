package com.sixik.gpu.frontend.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Источники значений стека операндов (шаг B7, FRONTEND_ASM_RUNTIME.md, S9; замена ASM {@code Analyzer} с
 * {@code SourceInterpreter} в {@link LvtSynth}). Для каждой инструкции - стек перед ней, у каждого значения - множество
 * инструкций, которые могли его положить, с той же семантикой, что у {@code SourceInterpreter}: загрузка и копия
 * ({@code dup*}, {@code swap}) - сами себе источник, на стыке - объединение.
 *
 * <p>Отличия, ради которых класс написан:</p>
 * <ul>
 *   <li>локали не отслеживаются (тип локали {@code LvtSynth} знает по вебам), поэтому память - инструкции × глубина
 *       стека, а не инструкции × ({@code maxLocals} + стек): у роутера с тысячами локалей кадры {@code Analyzer}
 *       занимали квадратичную память;</li>
 *   <li>стык не копирует множества: значение позиции стека на стыке - узел {@code phi} со ссылками на входящие, а
 *       множество источников собирается по запросу. Вложенный {@code ?:} глубины {@code d} у {@code SourceInterpreter}
 *       строил {@code d²/2} элементов, а ASM 9.11 отвечал {@code LimitExceededException};</li>
 *   <li>поведение не зависит от версии ASM загрузчика.</li>
 * </ul>
 */
final class StackSources {

    /** Источники одного значения стека. */
    static final class Src {
        /** Инструкции, которые могли положить значение (пусто у исключения в обработчике). */
        final Set<AbstractInsnNode> insns;

        Src(Set<AbstractInsnNode> insns) {
            this.insns = insns;
        }
    }

    /** Стек перед инструкцией: значения от дна к вершине. */
    final class Frame {
        private final int[] stack;

        private Frame(int[] stack) {
            this.stack = stack;
        }

        /** @return число значений на стеке ({@code long/double} - одно значение) */
        int getStackSize() {
            return stack.length;
        }

        /**
         * @param i индекс от дна
         * @return источники значения
         */
        Src getStack(int i) {
            return src(stack[i]);
        }
    }

    /** Ошибка разбора байткода (несогласованная высота стека, {@code jsr/ret}). */
    static final class Failure extends Exception {
        private static final long serialVersionUID = 1L;

        Failure(String message) {
            super(message);
        }
    }

    // значения: производитель (инструкция) либо phi; размер 1 или 2
    private final List<Integer> producer = new ArrayList<>();
    private final List<Integer> size = new ArrayList<>();
    /** Входящие phi ({@code null} у производителя). */
    private final List<Set<Integer>> phi = new ArrayList<>();
    private final Map<Integer, Src> flat = new HashMap<>();
    /** phi по (инструкция стыка, позиция): заводится один раз. */
    private final Map<Long, Integer> phiAt = new HashMap<>();

    private final InsnList insns;
    private int[][] before;

    private StackSources(InsnList insns) {
        this.insns = insns;
    }

    /**
     * Анализирует метод.
     *
     * @param m метод с кодом
     * @return кадры по индексам инструкций ({@code null} - недостижимая)
     * @throws Failure если байткод не разбирается
     */
    static Frame[] analyze(MethodNode m) throws Failure {
        StackSources s = new StackSources(m.instructions);
        return s.run(m);
    }

    private int value(int insn, int sz) {
        producer.add(insn);
        size.add(sz);
        phi.add(null);
        return producer.size() - 1;
    }

    private Src src(int v) {
        Src r = flat.get(v);
        if (r != null) {
            return r;
        }
        Set<AbstractInsnNode> out = new LinkedHashSet<>();
        // обход phi без рекурсии: цепочка стыков вложенного ?: может быть длинной
        ArrayDeque<Integer> work = new ArrayDeque<>();
        Set<Integer> seen = new java.util.HashSet<>();
        work.push(v);
        while (!work.isEmpty()) {
            int x = work.pop();
            if (!seen.add(x)) {
                continue;
            }
            Set<Integer> in = phi.get(x);
            if (in == null) {
                if (producer.get(x) >= 0) {
                    out.add(insns.get(producer.get(x)));
                }
            } else {
                for (int y : in) {
                    work.push(y);
                }
            }
        }
        r = new Src(Collections.unmodifiableSet(out));
        flat.put(v, r);
        return r;
    }

    private Frame[] run(MethodNode m) throws Failure {
        int n = insns.size();
        before = new int[n][];
        boolean[] queued = new boolean[n];
        ArrayDeque<Integer> work = new ArrayDeque<>();
        Map<Integer, List<Integer>> handlers = new HashMap<>();
        if (m.tryCatchBlocks != null) {
            for (TryCatchBlockNode t : m.tryCatchBlocks) {
                int h = insns.indexOf(t.handler);
                for (int k = insns.indexOf(t.start); k < insns.indexOf(t.end); k++) {
                    handlers.computeIfAbsent(k, z -> new ArrayList<>()).add(h);
                }
            }
        }
        if (n == 0) {
            return new Frame[0];
        }
        before[0] = new int[0];
        work.add(0);
        queued[0] = true;
        while (!work.isEmpty()) {
            int k = work.poll();
            queued[k] = false;
            AbstractInsnNode in = insns.get(k);
            int[] after = execute(k, in, before[k]);
            List<Integer> hs = handlers.get(k);
            if (hs != null) {
                for (int h : hs) {
                    flow(h, new int[]{value(-1, 1)}, work, queued);
                }
            }
            for (int t : successors(k, in)) {
                flow(t, after, work, queued);
            }
        }
        Frame[] r = new Frame[n];
        for (int k = 0; k < n; k++) {
            r[k] = before[k] == null ? null : new Frame(before[k]);
        }
        return r;
    }

    /** Сливает стек в инструкцию {@code t}; ставит её в работу, если её стек изменился. */
    private void flow(int t, int[] incoming, ArrayDeque<Integer> work, boolean[] queued) throws Failure {
        int[] cur = before[t];
        if (cur == null) {
            before[t] = incoming;
            enqueue(t, work, queued);
            return;
        }
        if (cur.length != incoming.length) {
            throw new Failure("inconsistent stack height at instruction " + t + ": " + cur.length + " and "
                    + incoming.length);
        }
        int[] merged = null;
        for (int i = 0; i < cur.length; i++) {
            int a = cur[i];
            int b = incoming[i];
            if (a == b) {
                continue;
            }
            long key = ((long) t << 16) | i;
            Integer p = phiAt.get(key);
            if (p != null && a == p) {
                // позиция уже phi: новый вход дополняет её, стек (идентификаторы) не меняется
                phi.get(p).add(b);
                continue;
            }
            if (p == null) {
                p = value(-1, size.get(a));
                phi.set(p, new LinkedHashSet<>());
                phiAt.put(key, p);
            }
            phi.get(p).add(a);
            phi.get(p).add(b);
            if (merged == null) {
                merged = cur.clone();
            }
            merged[i] = p;
        }
        if (merged != null) {
            before[t] = merged;
            enqueue(t, work, queued);
        }
    }

    private static void enqueue(int t, ArrayDeque<Integer> work, boolean[] queued) {
        if (!queued[t]) {
            queued[t] = true;
            work.add(t);
        }
    }

    private List<Integer> successors(int k, AbstractInsnNode in) {
        List<Integer> r = new ArrayList<>(2);
        int op = in.getOpcode();
        if (in instanceof JumpInsnNode j) {
            r.add(insns.indexOf(j.label));
            if (op != Opcodes.GOTO) {
                r.add(k + 1);
            }
            return r;
        }
        if (in instanceof TableSwitchInsnNode ts) {
            r.add(insns.indexOf(ts.dflt));
            for (LabelNode l : ts.labels) {
                r.add(insns.indexOf(l));
            }
            return r;
        }
        if (in instanceof LookupSwitchInsnNode ls) {
            r.add(insns.indexOf(ls.dflt));
            for (LabelNode l : ls.labels) {
                r.add(insns.indexOf(l));
            }
            return r;
        }
        if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN || op == Opcodes.ATHROW) {
            return r;
        }
        if (k + 1 < insns.size()) {
            r.add(k + 1);
        }
        return r;
    }

    // ---- действие инструкции на стек ---------------------------------------------------------

    private int[] execute(int k, AbstractInsnNode in, int[] st) throws Failure {
        int op = in.getOpcode();
        if (op < 0) {
            return st;
        }
        switch (op) {
            case Opcodes.NOP, Opcodes.GOTO, Opcodes.RETURN:
                return st;
            case Opcodes.JSR, Opcodes.RET:
                throw new Failure("jsr/ret subroutines are not supported");
            case Opcodes.POP:
                return pop(st, 1);
            case Opcodes.POP2:
                return pop(st, sizeAt(st, 0) == 2 ? 1 : 2);
            case Opcodes.DUP:
                return dup(k, st, 1);
            case Opcodes.DUP_X1:
                return copies(k, st, 2, new int[]{0, 1, 0});
            case Opcodes.DUP_X2:
                if (sizeAt(st, 1) == 2) {
                    return copies(k, st, 2, new int[]{0, 1, 0});
                }
                return copies(k, st, 3, new int[]{0, 2, 1, 0});
            case Opcodes.DUP2:
                return dup(k, st, sizeAt(st, 0) == 2 ? 1 : 2);
            case Opcodes.DUP2_X1:
                if (sizeAt(st, 0) == 2) {
                    return copies(k, st, 2, new int[]{0, 1, 0});
                }
                return copies(k, st, 3, new int[]{1, 0, 2, 1, 0});
            case Opcodes.DUP2_X2:
                return dup2x2(k, st);
            case Opcodes.SWAP:
                return copies(k, st, 2, new int[]{0, 1});
            default:
                break;
        }
        int pops = pops(in);
        int push = pushSize(in);
        int[] r = pop(st, pops);
        if (push == 0) {
            return r;
        }
        int[] out = Arrays.copyOf(r, r.length + 1);
        out[r.length] = value(k, push);
        return out;
    }

    /** Размер значения на {@code fromTop} от вершины. */
    private int sizeAt(int[] st, int fromTop) throws Failure {
        if (st.length <= fromTop) {
            throw new Failure("stack underflow");
        }
        return size.get(st[st.length - 1 - fromTop]);
    }

    private static int[] pop(int[] st, int n) throws Failure {
        if (st.length < n) {
            throw new Failure("stack underflow");
        }
        return Arrays.copyOf(st, st.length - n);
    }

    /**
     * Снимает {@code take} значений и кладёт копии по {@code layout} (от дна к вершине; номер - позиция от вершины
     * снятых); каждая копия - новое значение с источником {@code k}, как у {@code SourceInterpreter.copyOperation}.
     */
    private int[] copies(int k, int[] st, int take, int[] layout) throws Failure {
        if (st.length < take) {
            throw new Failure("stack underflow");
        }
        int[] r = Arrays.copyOf(st, st.length - take + layout.length);
        int base = st.length - take;
        for (int i = 0; i < layout.length; i++) {
            int orig = st[st.length - 1 - layout[i]];
            r[base + i] = value(k, size.get(orig));
        }
        return r;
    }

    /** {@code dup/dup2}: как у ASM {@code Frame}, оригиналы остаются внизу, копии ({@code k}) - сверху. */
    private int[] dup(int k, int[] st, int take) throws Failure {
        if (st.length < take) {
            throw new Failure("stack underflow");
        }
        int[] r = Arrays.copyOf(st, st.length + take);
        for (int i = 0; i < take; i++) {
            int orig = st[st.length - take + i];
            r[st.length + i] = value(k, size.get(orig));
        }
        return r;
    }

    private int[] dup2x2(int k, int[] st) throws Failure {
        boolean v1Wide = sizeAt(st, 0) == 2;
        if (v1Wide) {
            boolean v2Wide = sizeAt(st, 1) == 2;
            // форма 4: ..., v2(2), v1(2) -> v1, v2, v1; форма 2: ..., v3, v2, v1(2) -> v1, v3, v2, v1
            return v2Wide ? copies(k, st, 2, new int[]{0, 1, 0}) : copies(k, st, 3, new int[]{0, 2, 1, 0});
        }
        boolean v3Wide = sizeAt(st, 2) == 2;
        // форма 3: ..., v3(2), v2, v1 -> v2, v1, v3, v2, v1; форма 1: ..., v4, v3, v2, v1 -> v2, v1, v4, v3, v2, v1
        return v3Wide ? copies(k, st, 3, new int[]{1, 0, 2, 1, 0}) : copies(k, st, 4, new int[]{1, 0, 3, 2, 1, 0});
    }

    private static int argCount(String desc) {
        return Type.getArgumentTypes(desc).length;
    }

    /** Число снимаемых значений (не слотов). */
    private static int pops(AbstractInsnNode in) {
        int op = in.getOpcode();
        if (op >= Opcodes.IALOAD && op <= Opcodes.SALOAD) {
            return 2;
        }
        if (op >= Opcodes.ISTORE && op <= Opcodes.ASTORE) {
            return 1;
        }
        if (op >= Opcodes.IASTORE && op <= Opcodes.SASTORE) {
            return 3;
        }
        if (op >= Opcodes.IADD && op <= Opcodes.DREM || op >= Opcodes.ISHL && op <= Opcodes.LXOR
                || op >= Opcodes.LCMP && op <= Opcodes.DCMPG) {
            return 2;
        }
        if (op >= Opcodes.INEG && op <= Opcodes.DNEG || op >= Opcodes.I2L && op <= Opcodes.I2S) {
            return 1;
        }
        if (op >= Opcodes.IFEQ && op <= Opcodes.IFLE || op == Opcodes.IFNULL || op == Opcodes.IFNONNULL) {
            return 1;
        }
        if (op >= Opcodes.IF_ICMPEQ && op <= Opcodes.IF_ACMPNE) {
            return 2;
        }
        switch (op) {
            case Opcodes.TABLESWITCH, Opcodes.LOOKUPSWITCH, Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN,
                 Opcodes.DRETURN, Opcodes.ARETURN, Opcodes.PUTSTATIC, Opcodes.GETFIELD, Opcodes.NEWARRAY,
                 Opcodes.ANEWARRAY, Opcodes.ARRAYLENGTH, Opcodes.ATHROW, Opcodes.CHECKCAST, Opcodes.INSTANCEOF,
                 Opcodes.MONITORENTER, Opcodes.MONITOREXIT:
                return 1;
            case Opcodes.PUTFIELD:
                return 2;
            case Opcodes.INVOKESTATIC:
                return argCount(((MethodInsnNode) in).desc);
            case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKEINTERFACE:
                return argCount(((MethodInsnNode) in).desc) + 1;
            case Opcodes.INVOKEDYNAMIC:
                return argCount(((InvokeDynamicInsnNode) in).desc);
            case Opcodes.MULTIANEWARRAY:
                return ((MultiANewArrayInsnNode) in).dims;
            default:
                return 0;
        }
    }

    /** Размер кладущегося значения; {@code 0} - ничего не кладёт. */
    private static int pushSize(AbstractInsnNode in) {
        int op = in.getOpcode();
        switch (op) {
            case Opcodes.LCONST_0, Opcodes.LCONST_1, Opcodes.DCONST_0, Opcodes.DCONST_1, Opcodes.LLOAD, Opcodes.DLOAD,
                 Opcodes.LALOAD, Opcodes.DALOAD, Opcodes.LADD, Opcodes.DADD, Opcodes.LSUB, Opcodes.DSUB, Opcodes.LMUL,
                 Opcodes.DMUL, Opcodes.LDIV, Opcodes.DDIV, Opcodes.LREM, Opcodes.DREM, Opcodes.LNEG, Opcodes.DNEG,
                 Opcodes.LSHL, Opcodes.LSHR, Opcodes.LUSHR, Opcodes.LAND, Opcodes.LOR, Opcodes.LXOR, Opcodes.I2L,
                 Opcodes.I2D, Opcodes.L2D, Opcodes.F2L, Opcodes.F2D, Opcodes.D2L:
                return 2;
            case Opcodes.LDC: {
                Object c = ((LdcInsnNode) in).cst;
                return c instanceof Long || c instanceof Double ? 2 : 1;
            }
            case Opcodes.GETSTATIC, Opcodes.GETFIELD:
                return Type.getType(((FieldInsnNode) in).desc).getSize();
            case Opcodes.INVOKESTATIC, Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKEINTERFACE:
                return Type.getReturnType(((MethodInsnNode) in).desc).getSize();
            case Opcodes.INVOKEDYNAMIC:
                return Type.getReturnType(((InvokeDynamicInsnNode) in).desc).getSize();
            default:
                break;
        }
        if (op >= Opcodes.ISTORE && op <= Opcodes.ASTORE || op >= Opcodes.IASTORE && op <= Opcodes.SASTORE
                || op >= Opcodes.IFEQ && op <= Opcodes.IF_ACMPNE || op == Opcodes.IFNULL || op == Opcodes.IFNONNULL
                || op >= Opcodes.IRETURN && op <= Opcodes.RETURN) {
            return 0;
        }
        switch (op) {
            case Opcodes.NOP, Opcodes.IINC, Opcodes.GOTO, Opcodes.TABLESWITCH, Opcodes.LOOKUPSWITCH,
                 Opcodes.PUTSTATIC, Opcodes.PUTFIELD, Opcodes.ATHROW, Opcodes.MONITORENTER, Opcodes.MONITOREXIT:
                return 0;
            default:
                return 1;
        }
    }
}
