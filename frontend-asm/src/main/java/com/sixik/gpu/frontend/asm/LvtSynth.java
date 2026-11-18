package com.sixik.gpu.frontend.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Синтез {@code LocalVariableTable} для байткода без отладочной информации (FRONTEND_ASM_RUNTIME.md, 18.2-S7, шаг
 * B2). Работает <b>до</b> понижения: строит записи LVT, и остальной конвейер ({@code MethodLowerer}) не меняется.
 *
 * <ul>
 *   <li><b>Веб переменной</b> - записи и чтения слота, связанные достигающими определениями (одна задача потока данных
 *       на все слоты сразу).</li>
 *   <li><b>Тип</b> - по семейству опкода ({@code istore/iload/iinc} → {@code I}, {@code lstore} → {@code J}, ...);
 *       у {@code astore} - тип источника значения ({@code newarray}, {@code new}, результат вызова, {@code aaload},
 *       параметр, другая локаль; {@link StackSources} - семантика ASM {@code SourceInterpreter}). {@code boolean/byte/short/char} не выводятся - это
 *       {@code int} (контракт 18.3).</li>
 *   <li><b>Диапазон</b> - от первой записи до последнего обращения. Веб, чей диапазон пересекается с другим вебом того
 *       же слота, получает новый слот.</li>
 * </ul>
 *
 * <p>Нарушения контракта (разные типы в одном вебе, чтение до записи, неизвестный тип ссылки, неразбираемый
 * байткод) - {@code GPU0307}.</p>
 */
final class LvtSynth {

    private LvtSynth() {
    }

    /**
     * @param m метод
     * @return {@code true}, если у метода есть локали сверх параметров, а LVT нет
     */
    static boolean needed(MethodNode m) {
        return (m.localVariables == null || m.localVariables.isEmpty()) && maxSlot(m) > paramSlots(m).length;
    }

    /**
     * Копия метода с синтезированной LVT (исходный узел не меняется: сгенерированное дерево принадлежит вызывающему).
     *
     * @param owner    внутреннее имя класса
     * @param m        метод без LVT
     * @param problems приёмник текстов нарушений ({@code GPU0307})
     * @return копия с LVT; при нарушениях - копия, в которой LVT может быть неполной
     */
    static MethodNode synthesize(String owner, MethodNode m, Consumer<String> problems) {
        MethodNode c = new MethodNode(Opcodes.ASM9, m.access, m.name, m.desc, m.signature,
                m.exceptions == null ? null : m.exceptions.toArray(new String[0]));
        m.accept(c);
        c.maxLocals = Math.max(c.maxLocals, maxSlot(c));
        new Run(c, problems).run();
        return c;
    }

    /** @return число слотов, которые занимают обращения к локалям (с учётом ширины {@code J/D}) */
    private static int maxSlot(MethodNode m) {
        int max = 0;
        for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
            if (in instanceof VarInsnNode v) {
                int op = v.getOpcode();
                boolean wide = op == Opcodes.LLOAD || op == Opcodes.DLOAD || op == Opcodes.LSTORE || op == Opcodes.DSTORE;
                max = Math.max(max, v.var + (wide ? 2 : 1));
            } else if (in instanceof IincInsnNode ii) {
                max = Math.max(max, ii.var + 1);
            }
        }
        int[] p = paramSlots(m);
        return Math.max(max, p.length);
    }

    /** @return дескриптор параметра по слоту ({@code null} - не параметр; вторая половина {@code J/D} - {@code ""}) */
    private static String[] paramDescs(MethodNode m) {
        int[] slots = paramSlots(m);
        String[] r = new String[slots.length];
        Type[] args = Type.getArgumentTypes(m.desc);
        int s = (m.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
        if (s == 1) {
            r[0] = "L?;";
        }
        for (Type t : args) {
            r[s] = t.getDescriptor();
            if (t.getSize() == 2) {
                r[s + 1] = "";
            }
            s += t.getSize();
        }
        return r;
    }

    private static int[] paramSlots(MethodNode m) {
        int s = (m.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
        for (Type t : Type.getArgumentTypes(m.desc)) {
            s += t.getSize();
        }
        return new int[s];
    }

    /** Семейство опкода обращения к локали: {@code I J F D A} либо 0. */
    private static char family(AbstractInsnNode in) {
        if (in instanceof IincInsnNode) {
            return 'I';
        }
        return switch (in.getOpcode()) {
            case Opcodes.ILOAD, Opcodes.ISTORE -> 'I';
            case Opcodes.LLOAD, Opcodes.LSTORE -> 'J';
            case Opcodes.FLOAD, Opcodes.FSTORE -> 'F';
            case Opcodes.DLOAD, Opcodes.DSTORE -> 'D';
            case Opcodes.ALOAD, Opcodes.ASTORE -> 'A';
            default -> 0;
        };
    }

    private static boolean isDef(AbstractInsnNode in) {
        return in instanceof IincInsnNode || in.getOpcode() >= Opcodes.ISTORE && in.getOpcode() <= Opcodes.ASTORE;
    }

    private static boolean isUse(AbstractInsnNode in) {
        return in instanceof IincInsnNode || in.getOpcode() >= Opcodes.ILOAD && in.getOpcode() <= Opcodes.ALOAD;
    }

    private static int slotOf(AbstractInsnNode in) {
        return in instanceof IincInsnNode ii ? ii.var : in instanceof VarInsnNode v ? v.var : -1;
    }

    private static String shown(char f) {
        return switch (f) {
            case 'I' -> "int";
            case 'J' -> "long";
            case 'F' -> "float";
            case 'D' -> "double";
            default -> "reference";
        };
    }

    /** Веб переменной. */
    private static final class Web {
        final int slot;
        final List<Integer> insns = new ArrayList<>();
        char family;
        /** Дескриптор (для {@code A} - после вывода типов; {@code null} - пока неизвестен). */
        String desc;
        int min = Integer.MAX_VALUE;
        int max = -1;
        int firstDef = Integer.MAX_VALUE;
        boolean bad;

        Web(int slot) {
            this.slot = slot;
        }
    }

    /** Один проход по методу. */
    private static final class Run {
        private final MethodNode m;
        private final InsnList insns;
        private final Consumer<String> problems;
        private final String[] params;
        /** Веб каждой инструкции обращения к локали (по индексу инструкции). */
        private final Map<Integer, Web> webOf = new HashMap<>();
        private StackSources.Frame[] frames;
        private boolean analysisFailed;
        private String analysisError;

        Run(MethodNode m, Consumer<String> problems) {
            this.m = m;
            this.insns = m.instructions;
            this.problems = problems;
            this.params = paramDescs(m);
        }

        private boolean isParam(int slot) {
            return slot < params.length;
        }

        private void problem(String what) {
            problems.accept(what);
        }

        void run() {
            int n = insns.size();
            // ---- определения: все записи в слоты, кроме параметров
            List<Integer> defs = new ArrayList<>();
            Map<Integer, Integer> defBit = new HashMap<>();
            Map<Integer, BitSet> slotDefs = new HashMap<>();
            for (int k = 0; k < n; k++) {
                AbstractInsnNode in = insns.get(k);
                if (!isDef(in)) {
                    continue;
                }
                int s = slotOf(in);
                if (isParam(s)) {
                    checkParamStore(in, s);
                    continue;
                }
                defBit.put(k, defs.size());
                slotDefs.computeIfAbsent(s, x -> new BitSet()).set(defs.size());
                defs.add(k);
            }
            if (defs.isEmpty() && !hasLocalUse()) {
                return;
            }
            // ---- достигающие определения по блокам
            Cfg g = Cfg.build(insns, List.of());
            int nb = g.size();
            boolean[] reachable = reachable(g);
            BitSet[] out = new BitSet[nb];
            for (int b = 0; b < nb; b++) {
                out[b] = new BitSet();
            }
            boolean changed = true;
            while (changed) {
                changed = false;
                for (int b = 0; b < nb; b++) {
                    BitSet cur = in(g, b, out);
                    Cfg.Block bl = g.get(b);
                    for (int k = bl.start; k < bl.end; k++) {
                        Integer bit = defBit.get(k);
                        if (bit != null) {
                            cur.andNot(slotDefs.get(slotOf(insns.get(k))));
                            cur.set(bit);
                        }
                    }
                    if (!cur.equals(out[b])) {
                        out[b] = cur;
                        changed = true;
                    }
                }
            }
            // ---- union-find определений: чтение объединяет все достигающие его записи слота
            int[] parent = new int[defs.size()];
            for (int i = 0; i < parent.length; i++) {
                parent[i] = i;
            }
            Map<Integer, Integer> useDef = new HashMap<>();
            for (int b = 0; b < nb; b++) {
                BitSet cur = in(g, b, out);
                Cfg.Block bl = g.get(b);
                for (int k = bl.start; k < bl.end; k++) {
                    AbstractInsnNode in = insns.get(k);
                    int s = slotOf(in);
                    if (s < 0 || isParam(s)) {
                        continue;
                    }
                    if (isUse(in)) {
                        BitSet r = (BitSet) cur.clone();
                        BitSet sd = slotDefs.get(s);
                        if (sd != null) {
                            r.and(sd);
                        } else {
                            r.clear();
                        }
                        int first = r.nextSetBit(0);
                        if (first < 0) {
                            if (reachable[b]) {
                                problem("slot " + s + " is read before it is written (instruction " + k + ")");
                            }
                        } else {
                            for (int d = r.nextSetBit(first + 1); d >= 0; d = r.nextSetBit(d + 1)) {
                                union(parent, first, d);
                            }
                            if (in instanceof IincInsnNode) {
                                union(parent, first, defBit.get(k));
                            } else {
                                useDef.put(k, first);
                            }
                        }
                    }
                    Integer bit = defBit.get(k);
                    if (bit != null) {
                        cur.andNot(slotDefs.get(s));
                        cur.set(bit);
                    }
                }
            }
            // ---- веба
            Map<Integer, Web> byRoot = new TreeMap<>();
            for (int i = 0; i < defs.size(); i++) {
                int root = find(parent, i);
                int k = defs.get(i);
                Web w = byRoot.computeIfAbsent(root, r -> new Web(slotOf(insns.get(defs.get(r)))));
                add(w, k);
                w.firstDef = Math.min(w.firstDef, k);
            }
            useDef.forEach((k, d) -> add(byRoot.get(find(parent, d)), k));
            List<Web> webs = new ArrayList<>(byRoot.values());
            webs.sort((a, b) -> Integer.compare(a.min, b.min));
            for (Web w : webs) {
                inferFamily(w);
            }
            refTypes(webs);
            webs = mergeReassigned(webs);
            for (Web w : webs) {
                w.max = loopEnd(w);
            }
            assign(webs);
        }

        /**
         * Конец диапазона, как область видимости у javac. Переменная, объявленная до цикла и последний раз
         * использованная в его латче (шаг {@code for}), живёт до выхода из цикла: так её находит {@code for}-
         * инициализатор (17.9-8, конец LVT - в блоке выхода). Остальные - до конца метода: конец посреди блока выхода
         * из цикла сделал бы любую переменную, объявленную перед циклом, его инициализатором.
         */
        private int loopEnd(Web w) {
            int end = w.max;
            boolean loop = false;
            while (true) {
                AbstractInsnNode next = insns.get(end).getNext();
                while (next != null && next.getOpcode() < 0) {
                    next = next.getNext();
                }
                if (next == null || next.getOpcode() != Opcodes.GOTO) {
                    break;
                }
                int target = insns.indexOf(((org.objectweb.asm.tree.JumpInsnNode) next).label);
                int at = insns.indexOf(next);
                if (target >= at || target <= w.firstDef) {
                    break;
                }
                end = at;
                loop = true;
            }
            return loop ? end : insns.size() - 1;
        }

        /**
         * Переприсваивание {@code x = f(x)} без стыка обрывает веб: запись уже не достигается старым значением. У javac
         * это одна переменная, поэтому соседние веба одного слота и типа склеиваются, если значение первой записи
         * второго веба вычисляется из последнего обращения первого.
         */
        private List<Web> mergeReassigned(List<Web> webs) {
            List<Web> r = new ArrayList<>();
            Map<Integer, Web> lastBySlot = new HashMap<>();
            for (Web w : webs) {
                Web prev = lastBySlot.get(w.slot);
                if (prev != null && !prev.bad && !w.bad && prev.desc != null && prev.desc.equals(w.desc)
                        && prev.max < w.firstDef && w.firstDef == w.min
                        && insns.get(w.firstDef) instanceof VarInsnNode && feeds(prev.max, w.firstDef)) {
                    for (int k : w.insns) {
                        add(prev, k);
                    }
                    continue;
                }
                lastBySlot.put(w.slot, w);
                r.add(w);
            }
            return r;
        }

        /** @return значение, которое записывает {@code store}, вычисляется (в том же выражении) из инструкции {@code from} */
        private boolean feeds(int from, int store) {
            if (!frames()) {
                return false;
            }
            StackSources.Frame f = frames[store];
            return f != null && f.getStackSize() > 0 && dependsOn(f.getStack(f.getStackSize() - 1), from, 0);
        }

        private boolean dependsOn(StackSources.Src v, int target, int depth) {
            for (AbstractInsnNode p : v.insns) {
                int pi = insns.indexOf(p);
                if (pi == target) {
                    return true;
                }
                if (depth > 32 || pi + 1 >= frames.length || frames[pi] == null || frames[pi + 1] == null) {
                    continue;
                }
                StackSources.Frame before = frames[pi];
                // операнды производителя - вершина стека перед ним (снято = было - стало + 1 результат)
                int consumed = Math.max(0, before.getStackSize() - frames[pi + 1].getStackSize() + 1);
                for (int i = 0; i < consumed && i < before.getStackSize(); i++) {
                    if (dependsOn(before.getStack(before.getStackSize() - 1 - i), target, depth + 1)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Источники значений стека (один раз на метод; {@link StackSources} - линейная по памяти замена
         * {@code SourceInterpreter}, шаг B7). @return {@code false}, если байткод не разбирается
         */
        private boolean frames() {
            if (frames == null && !analysisFailed) {
                try {
                    frames = StackSources.analyze(m);
                } catch (StackSources.Failure e) {
                    analysisFailed = true;
                    analysisError = e.getMessage();
                }
            }
            return frames != null;
        }

        private boolean hasLocalUse() {
            for (AbstractInsnNode in = insns.getFirst(); in != null; in = in.getNext()) {
                int s = slotOf(in);
                if (s >= 0 && !isParam(s)) {
                    return true;
                }
            }
            return false;
        }

        private void add(Web w, int k) {
            w.insns.add(k);
            webOf.put(k, w);
            w.min = Math.min(w.min, k);
            w.max = Math.max(w.max, k);
        }

        /** Запись в слот параметра - того же семейства, что параметр (иначе это другая переменная в том же слоте). */
        private void checkParamStore(AbstractInsnNode in, int s) {
            String d = params[s];
            char want = d == null || d.isEmpty() ? 0 : d.charAt(0) == '[' || d.charAt(0) == 'L' ? 'A'
                    : d.charAt(0) == 'Z' || d.charAt(0) == 'B' || d.charAt(0) == 'S' || d.charAt(0) == 'C' ? 'I'
                    : d.charAt(0);
            if (family(in) != want) {
                problem("slot " + s + " of parameter " + Type.getType(d.isEmpty() ? "V" : d).getClassName()
                        + " is written as " + shown(family(in)));
            }
        }

        private void inferFamily(Web w) {
            char f = 0;
            for (int k : w.insns) {
                char x = family(insns.get(k));
                if (f == 0) {
                    f = x;
                } else if (f != x) {
                    problem("slot " + w.slot + " holds " + shown(f) + " and " + shown(x) + " in one variable");
                    w.bad = true;
                    return;
                }
            }
            w.family = f;
            if (f != 'A') {
                w.desc = String.valueOf(f);
            }
        }

        // ---- типы ссылок ---------------------------------------------------------------------

        private void refTypes(List<Web> webs) {
            List<Web> refs = webs.stream().filter(w -> !w.bad && w.family == 'A').toList();
            if (refs.isEmpty()) {
                return;
            }
            if (!frames()) {
                problem("the bytecode cannot be analyzed: " + analysisError);
                refs.forEach(w -> w.bad = true);
                return;
            }
            // тип веба может зависеть от другой локали (astore из aload): до неподвижной точки
            boolean changed = true;
            while (changed) {
                changed = false;
                for (Web w : refs) {
                    if (w.desc != null || w.bad) {
                        continue;
                    }
                    String d = webRefType(w);
                    if (d != null) {
                        w.desc = d;
                        changed = true;
                    }
                }
            }
            for (Web w : refs) {
                if (w.desc == null && !w.bad) {
                    problem("slot " + w.slot + ": the reference type of the variable is unknown");
                    w.bad = true;
                }
            }
        }

        /** @return тип веба по его записям; {@code null} - пока неизвестен; при конфликте веб помечается плохим */
        private String webRefType(Web w) {
            String r = null;
            for (int k : w.insns) {
                AbstractInsnNode in = insns.get(k);
                if (in.getOpcode() != Opcodes.ASTORE || frames[k] == null) {
                    continue;
                }
                StackSources.Frame f = frames[k];
                String d = valueType(f.getStack(f.getStackSize() - 1), 0);
                if (d == null) {
                    continue;
                }
                if (r == null) {
                    r = d;
                } else if (!r.equals(d)) {
                    problem("slot " + w.slot + " holds " + Type.getType(r).getClassName() + " and "
                            + Type.getType(d).getClassName() + " in one variable");
                    w.bad = true;
                    return null;
                }
            }
            return r;
        }

        private String valueType(StackSources.Src v, int depth) {
            String r = null;
            for (AbstractInsnNode p : v.insns) {
                String d = producerType(p, depth);
                if (d == null) {
                    continue;
                }
                if (r == null) {
                    r = d;
                } else if (!r.equals(d)) {
                    return null;
                }
            }
            return r;
        }

        private String top(AbstractInsnNode p, int fromTop, int depth) {
            StackSources.Frame f = frames[insns.indexOf(p)];
            if (f == null || f.getStackSize() <= fromTop) {
                return null;
            }
            return valueType(f.getStack(f.getStackSize() - 1 - fromTop), depth + 1);
        }

        private String producerType(AbstractInsnNode p, int depth) {
            if (depth > 64) {
                return null;
            }
            switch (p.getOpcode()) {
                case Opcodes.NEWARRAY:
                    return "[" + switch (((IntInsnNode) p).operand) {
                        case Opcodes.T_BOOLEAN -> "Z";
                        case Opcodes.T_CHAR -> "C";
                        case Opcodes.T_FLOAT -> "F";
                        case Opcodes.T_DOUBLE -> "D";
                        case Opcodes.T_BYTE -> "B";
                        case Opcodes.T_SHORT -> "S";
                        case Opcodes.T_LONG -> "J";
                        default -> "I";
                    };
                case Opcodes.ANEWARRAY:
                    return "[" + objectDesc(((TypeInsnNode) p).desc);
                case Opcodes.MULTIANEWARRAY:
                    return ((MultiANewArrayInsnNode) p).desc;
                case Opcodes.NEW:
                case Opcodes.CHECKCAST:
                    return objectDesc(((TypeInsnNode) p).desc);
                case Opcodes.GETFIELD:
                case Opcodes.GETSTATIC:
                    return ((FieldInsnNode) p).desc;
                case Opcodes.INVOKESTATIC:
                case Opcodes.INVOKEVIRTUAL:
                case Opcodes.INVOKESPECIAL:
                case Opcodes.INVOKEINTERFACE:
                    return Type.getReturnType(((MethodInsnNode) p).desc).getDescriptor();
                case Opcodes.ALOAD: {
                    int s = ((VarInsnNode) p).var;
                    if (isParam(s)) {
                        return params[s];
                    }
                    Web w = webOf.get(insns.indexOf(p));
                    return w == null ? null : w.desc;
                }
                case Opcodes.AALOAD: {
                    String a = top(p, 1, depth);
                    return a != null && a.startsWith("[") ? a.substring(1) : null;
                }
                case Opcodes.DUP:
                case Opcodes.DUP_X1:
                case Opcodes.DUP_X2:
                    return top(p, 0, depth);
                case Opcodes.DUP2:
                case Opcodes.DUP2_X1:
                case Opcodes.DUP2_X2: {
                    String a = top(p, 0, depth);
                    String b = top(p, 1, depth);
                    return a != null && a.equals(b) ? a : null;
                }
                default:
                    return null;
            }
        }

        private static String objectDesc(String internal) {
            return internal.startsWith("[") ? internal : "L" + internal + ";";
        }

        // ---- слоты и записи LVT --------------------------------------------------------------

        /** Веба одного слота не пересекаются по диапазону; иначе веб получает новый слот. Затем - записи LVT. */
        private void assign(List<Web> webs) {
            Map<Integer, List<int[]>> busy = new HashMap<>();
            int next = m.maxLocals;
            Map<Integer, Integer> perSlot = new HashMap<>();
            List<LocalVariableNode> lvt = new ArrayList<>();
            List<Web> placed = new ArrayList<>();
            for (Web w : webs) {
                if (w.bad || w.desc == null) {
                    continue;
                }
                int size = w.desc.equals("J") || w.desc.equals("D") ? 2 : 1;
                int slot = w.slot;
                if (overlaps(busy, slot, size, w)) {
                    slot = next;
                    next += size;
                    for (int k : w.insns) {
                        AbstractInsnNode in = insns.get(k);
                        if (in instanceof VarInsnNode v) {
                            v.var = slot;
                        } else {
                            ((IincInsnNode) in).var = slot;
                        }
                    }
                }
                for (int i = 0; i < size; i++) {
                    busy.computeIfAbsent(slot + i, x -> new ArrayList<>()).add(new int[]{w.min, w.max});
                }
                int ord = perSlot.merge(slot, 1, Integer::sum) - 1;
                String name = "v" + slot + (ord == 0 ? "" : "_" + ord);
                lvt.add(new LocalVariableNode(name, w.desc, null, null, null, slot));
                placed.add(w);
            }
            m.maxLocals = Math.max(m.maxLocals, next);
            // метки ставятся после всех индексов: вставка сдвигает индексы инструкций
            AbstractInsnNode[] starts = new AbstractInsnNode[placed.size()];
            AbstractInsnNode[] ends = new AbstractInsnNode[placed.size()];
            boolean[] afterDef = new boolean[placed.size()];
            for (int i = 0; i < placed.size(); i++) {
                Web w = placed.get(i);
                afterDef[i] = w.firstDef <= w.min;
                starts[i] = insns.get(afterDef[i] ? w.firstDef : w.min);
                ends[i] = insns.get(w.max);
            }
            for (int i = 0; i < placed.size(); i++) {
                LocalVariableNode lv = lvt.get(i);
                lv.start = afterDef[i] ? labelAfter(starts[i]) : labelBefore(starts[i]);
                lv.end = labelAfter(ends[i]);
            }
            m.localVariables = lvt;
        }

        private static boolean overlaps(Map<Integer, List<int[]>> busy, int slot, int size, Web w) {
            for (int i = 0; i < size; i++) {
                for (int[] r : busy.getOrDefault(slot + i, List.of())) {
                    if (w.min <= r[1] && r[0] <= w.max) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** Метка сразу за инструкцией (до следующего опкода): существующая, если есть (на неё ссылаются аннотации). */
        private LabelNode labelAfter(AbstractInsnNode in) {
            for (AbstractInsnNode x = in.getNext(); x != null && x.getOpcode() < 0; x = x.getNext()) {
                if (x instanceof LabelNode l) {
                    return l;
                }
            }
            LabelNode l = new LabelNode();
            insns.insert(in, l);
            return l;
        }

        private LabelNode labelBefore(AbstractInsnNode in) {
            LabelNode l = new LabelNode();
            insns.insertBefore(in, l);
            return l;
        }

        // ---- поток данных --------------------------------------------------------------------

        private static BitSet in(Cfg g, int b, BitSet[] out) {
            BitSet r = new BitSet();
            for (int p : g.get(b).preds) {
                r.or(out[p]);
            }
            return r;
        }

        private static boolean[] reachable(Cfg g) {
            int n = g.size();
            boolean[] r = new boolean[n];
            if (n == 0) {
                return r;
            }
            r[0] = true;
            boolean changed = true;
            while (changed) {
                changed = false;
                for (int b = 0; b < n; b++) {
                    if (!r[b]) {
                        continue;
                    }
                    for (int s : g.get(b).succs(n)) {
                        if (!r[s]) {
                            r[s] = true;
                            changed = true;
                        }
                    }
                }
            }
            return r;
        }

        private static int find(int[] parent, int x) {
            while (parent[x] != x) {
                parent[x] = parent[parent[x]];
                x = parent[x];
            }
            return x;
        }

        private static void union(int[] parent, int a, int b) {
            int ra = find(parent, a);
            int rb = find(parent, b);
            if (ra != rb) {
                parent[Math.max(ra, rb)] = Math.min(ra, rb);
            }
        }
    }
}
