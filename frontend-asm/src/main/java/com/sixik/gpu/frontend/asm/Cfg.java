package com.sixik.gpu.frontend.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.TreeSet;

/**
 * Граф потока управления метода (REWORK_ARCHITECTURE.md, 17.7, шаг 2): базовые блоки по лидерам, рёбра, доминаторы.
 *
 * <p>Блоки нумеруются в порядке адресов: javac раскладывает структурный код вложенными интервалами, и структуризатор
 * ({@link Structurer}) опирается на этот порядок. Блок - полуинтервал индексов {@link InsnList} {@code [start, end)};
 * завершающая инструкция ({@link #term}) - последний опкод блока, если это переход, {@code switch} или возврат.</p>
 */
final class Cfg {

    /** Блок кончается без перехода и продолжается следующим. */
    static final int FALL = 0;
    /** Безусловный {@code goto}. */
    static final int GOTO = 1;
    /** Условный переход: {@link Block#target} при выполнении условия, иначе следующий блок. */
    static final int COND = 2;
    /** {@code tableswitch}/{@code lookupswitch}. */
    static final int SWITCH = 3;
    /** {@code *return}. */
    static final int RETURN = 4;

    /** Базовый блок. */
    static final class Block {
        final int index;
        final int start;
        final int end;
        int term = FALL;
        /** Последний опкод блока ({@code null} - в блоке нет опкодов). */
        AbstractInsnNode last;
        /** Цель {@code goto} или условного перехода. */
        int target = -1;
        /** Цели {@code switch}: ключи, блоки, блок по умолчанию. */
        int[] keys;
        int[] targets;
        int deflt = -1;
        final List<Integer> preds = new ArrayList<>();

        Block(int index, int start, int end) {
            this.index = index;
            this.start = start;
            this.end = end;
        }

        /** @return блоки-преемники */
        List<Integer> succs(int count) {
            List<Integer> r = new ArrayList<>();
            switch (term) {
                case FALL -> {
                    if (index + 1 < count) {
                        r.add(index + 1);
                    }
                }
                case GOTO -> r.add(target);
                case COND -> {
                    r.add(target);
                    if (index + 1 < count) {
                        r.add(index + 1);
                    }
                }
                case SWITCH -> {
                    for (int t : targets) {
                        r.add(t);
                    }
                    r.add(deflt);
                }
                default -> {
                }
            }
            return r;
        }

        @Override
        public String toString() {
            return "B" + index + "[" + start + "," + end + ")";
        }
    }

    final InsnList insns;
    final List<Block> blocks = new ArrayList<>();
    private final int[] blockOf;
    /** {@code dom[b]} - множество блоков, доминирующих над {@code b}; {@code null} - блок недостижим. */
    private BitSet[] dom;

    private Cfg(InsnList insns, TreeSet<Integer> leaders) {
        this.insns = insns;
        this.blockOf = new int[insns.size() + 1];
        List<Integer> ls = new ArrayList<>(leaders);
        for (int i = 0; i < ls.size(); i++) {
            int s = ls.get(i);
            int e = i + 1 < ls.size() ? ls.get(i + 1) : insns.size();
            blocks.add(new Block(i, s, e));
            for (int k = s; k < e; k++) {
                blockOf[k] = i;
            }
        }
        blockOf[insns.size()] = blocks.size();
        for (Block b : blocks) {
            for (int k = b.end - 1; k >= b.start; k--) {
                AbstractInsnNode in = insns.get(k);
                if (in.getOpcode() >= 0) {
                    b.last = in;
                    break;
                }
            }
            AbstractInsnNode t = b.last;
            if (t instanceof JumpInsnNode j) {
                b.term = j.getOpcode() == Opcodes.GOTO ? GOTO : COND;
                b.target = blockAt(j.label);
            } else if (t instanceof TableSwitchInsnNode ts) {
                b.term = SWITCH;
                b.keys = new int[ts.labels.size()];
                b.targets = new int[ts.labels.size()];
                for (int i = 0; i < b.keys.length; i++) {
                    b.keys[i] = ts.min + i;
                    b.targets[i] = blockAt(ts.labels.get(i));
                }
                b.deflt = blockAt(ts.dflt);
            } else if (t instanceof LookupSwitchInsnNode ls2) {
                b.term = SWITCH;
                b.keys = new int[ls2.keys.size()];
                b.targets = new int[ls2.keys.size()];
                for (int i = 0; i < b.keys.length; i++) {
                    b.keys[i] = ls2.keys.get(i);
                    b.targets[i] = blockAt(ls2.labels.get(i));
                }
                b.deflt = blockAt(ls2.dflt);
            } else if (t != null && (t.getOpcode() >= Opcodes.IRETURN && t.getOpcode() <= Opcodes.RETURN)) {
                b.term = RETURN;
            }
        }
        for (Block b : blocks) {
            for (int s : b.succs(blocks.size())) {
                blocks.get(s).preds.add(b.index);
            }
        }
    }

    /**
     * Строит граф метода. Шаг цикла {@code for} (17.9-4) отделяется в собственный блок: {@code splitAt} - индексы
     * инструкций, с которых начинается новый блок, помимо обычных лидеров.
     *
     * @param insns   инструкции метода
     * @param splitAt дополнительные лидеры
     * @return граф
     */
    static Cfg build(InsnList insns, List<Integer> splitAt) {
        TreeSet<Integer> leaders = new TreeSet<>();
        leaders.add(0);
        leaders.addAll(splitAt);
        for (int i = 0; i < insns.size(); i++) {
            AbstractInsnNode in = insns.get(i);
            if (in instanceof JumpInsnNode j) {
                leaders.add(insns.indexOf(j.label));
                leaders.add(i + 1);
            } else if (in instanceof TableSwitchInsnNode ts) {
                leaders.add(insns.indexOf(ts.dflt));
                ts.labels.forEach(l -> leaders.add(insns.indexOf(l)));
                leaders.add(i + 1);
            } else if (in instanceof LookupSwitchInsnNode ls) {
                leaders.add(insns.indexOf(ls.dflt));
                ls.labels.forEach(l -> leaders.add(insns.indexOf(l)));
                leaders.add(i + 1);
            } else if (in.getOpcode() >= Opcodes.IRETURN && in.getOpcode() <= Opcodes.RETURN) {
                leaders.add(i + 1);
            }
        }
        leaders.removeIf(x -> x >= insns.size());
        return new Cfg(insns, leaders);
    }

    /** @return блок, в котором лежит метка */
    int blockAt(LabelNode label) {
        return blockOf[insns.indexOf(label)];
    }

    /** @return блок инструкции с индексом {@code insn} ({@code size()} для конца метода) */
    int blockOfInsn(int insn) {
        return blockOf[insn];
    }

    int size() {
        return blocks.size();
    }

    Block get(int i) {
        return blocks.get(i);
    }

    /**
     * Номер строки, действующий на первой инструкции блока (последний {@code LineNumberNode} до неё).
     *
     * @param b блок
     * @return строка либо 0
     */
    int lineAt(int insnIndex) {
        for (int k = insnIndex; k >= 0; k--) {
            if (insns.get(k) instanceof LineNumberNode l) {
                return l.line;
            }
        }
        return 0;
    }

    /** Доминаторы (итеративно, на битовых множествах: методы ядер маленькие). */
    void computeDominators() {
        int n = blocks.size();
        dom = new BitSet[n];
        BitSet reach = new BitSet(n);
        List<Integer> work = new ArrayList<>(List.of(0));
        reach.set(0);
        while (!work.isEmpty()) {
            int b = work.remove(work.size() - 1);
            for (int s : blocks.get(b).succs(n)) {
                if (!reach.get(s)) {
                    reach.set(s);
                    work.add(s);
                }
            }
        }
        for (int b = 0; b < n; b++) {
            if (reach.get(b)) {
                dom[b] = new BitSet(n);
                if (b == 0) {
                    dom[b].set(0);
                } else {
                    dom[b].or(reach);
                }
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int b = 1; b < n; b++) {
                if (dom[b] == null) {
                    continue;
                }
                BitSet d = null;
                for (int p : blocks.get(b).preds) {
                    if (dom[p] == null) {
                        continue;
                    }
                    if (d == null) {
                        d = (BitSet) dom[p].clone();
                    } else {
                        d.and(dom[p]);
                    }
                }
                if (d == null) {
                    d = new BitSet(n);
                }
                d.set(b);
                if (!d.equals(dom[b])) {
                    dom[b] = d;
                    changed = true;
                }
            }
        }
    }

    /** @return {@code true}, если {@code a} доминирует над {@code b} (недостижимый {@code b} - доминирует) */
    boolean dominates(int a, int b) {
        return dom[b] == null || dom[b].get(a);
    }

    /** @return {@code true}, если блок достижим из входа */
    boolean reachable(int b) {
        return dom[b] != null;
    }
}
