package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.Op;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableAnnotationNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Метод байткода → стейтменты фронтенда (REWORK_ARCHITECTURE.md, 17.7, шаги 1 и 3): проверка подмножества (все
 * нарушения метода за один проход, 17.6) и символьное исполнение тела на типизированном стеке (17.8).
 *
 * <p><b>Шаг A2</b> (17.15): прямолинейные тела - скаляры, массивы, builtins {@code Gpu.*}, приведения, правило
 * эффектов и {@code dup}-семейство. Управление потоком (A3), вызовы {@code @DeviceFunction} (A4), векторы, struct,
 * указатели и атомики (A5) дают {@link NotYet}: вызывающий превращает это в {@code GPU0902}, а не строит неверный IR.</p>
 *
 * <p><b>Правило эффектов.</b> Перед любым стейтментом, который появляется, пока на стеке лежат ожидающие значения,
 * «открытые» из них (читают изменяемую локаль или память) сохраняются во временные локали - то же правило, что
 * {@code operands} в {@code frontend-java}: порядок эффектов Java сохраняется, а дерево не дублирует открытых чтений.</p>
 */
final class MethodLowerer {

    static final String GPU = "com/sixik/gpu/api/Gpu";
    static final String LOCAL_ANNOTATION = "Lcom/sixik/gpu/api/annotations/Local;";
    static final String GLOBAL_ANNOTATION = "Lcom/sixik/gpu/api/annotations/Global;";

    /** Конструкция, которую фронтенд байткода ещё не поддерживает (шаги 17.15): становится {@code GPU0902}. */
    static final class NotYet extends RuntimeException {
        private static final long serialVersionUID = 1L;

        NotYet(String what) {
            super(what + " is not supported by frontend-asm yet");
        }
    }

    /** Элемент стека: выражение и число стейтментов на момент, когда оно легло на стек. */
    private record Slot(Ex ex, int mark) {
        int words() {
            return Kinds.wide(ex.kind) ? 2 : 1;
        }
    }

    private final String className;
    private final MethodNode m;
    private final String name;
    private final List<AsmDiagnostic> diags;
    private final InsnList insns;

    /** Параметры: вид, вид элемента, пространство, слот. */
    final int[] paramKinds;
    final int[] paramElems;
    final int[] paramSpaces;
    final String[] paramNames;
    private final int[] paramSlots;
    /** Изменяемый параметр - локаль с начальным значением {@code param(i)}; иначе {@code null}. */
    final Var[] paramVars;
    final int retKind;

    private final Map<String, Var> lvtVars = new HashMap<>();
    private final Set<Var> declared = new HashSet<>();
    private final List<Slot> stack = new ArrayList<>();
    private final List<St> out = new ArrayList<>();
    private int line;
    private int group;

    /**
     * @param className двоичное имя класса (для диагностик)
     * @param m         метод с телом (для сплетённого ядра - {@code <имя>$jvm})
     * @param name      имя функции (исходное имя метода)
     * @param diags     приёмник диагностик
     */
    MethodLowerer(String className, MethodNode m, String name, List<AsmDiagnostic> diags) {
        this.className = className;
        this.m = m;
        this.name = name;
        this.diags = diags;
        this.insns = m.instructions;
        Type[] args = Type.getArgumentTypes(m.desc);
        int n = args.length;
        paramKinds = new int[n];
        paramElems = new int[n];
        paramSpaces = new int[n];
        paramSlots = new int[n];
        paramNames = new String[n];
        paramVars = new Var[n];
        int slot = (m.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
        for (int i = 0; i < n; i++) {
            String d = args[i].getDescriptor();
            paramKinds[i] = Kinds.ofDescriptor(d);
            paramElems[i] = paramKinds[i] == Kinds.K_ARRAY ? Kinds.ofDescriptor(d.substring(1)) : -1;
            paramSpaces[i] = paramKinds[i] == Kinds.K_ARRAY ? paramSpace(i) : -1;
            paramSlots[i] = slot;
            paramNames[i] = paramName(i, slot);
            slot += args[i].getSize();
        }
        retKind = Kinds.ofDescriptor(Type.getReturnType(m.desc).getDescriptor());
    }

    // =============================================================================================
    // Проверка подмножества (17.6)
    // =============================================================================================

    /**
     * Проверяет метод и выдаёт все нарушения. Конструкции следующих шагов дают {@link NotYet} после проверки, чтобы
     * пользователь сначала увидел свои ошибки.
     *
     * @return {@code true}, если ошибок нет
     */
    boolean check() {
        int before = errors();
        String notYet = null;
        if (m.tryCatchBlocks != null && !m.tryCatchBlocks.isEmpty()) {
            report("GPU0101", firstLine(), "try/catch");
        }
        if ((m.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            report("GPU0102", firstLine());
        }
        for (int i = 0; i < paramKinds.length; i++) {
            if (paramKinds[i] == Kinds.K_OTHER) {
                notYet = "parameter of type " + Type.getArgumentTypes(m.desc)[i].getClassName() + " (step A5)";
            } else if (paramKinds[i] == Kinds.K_ARRAY && (paramElems[i] == Kinds.K_ARRAY || paramElems[i] == Kinds.K_OTHER)) {
                notYet = "array parameter of non-primitive elements (step A5)";
            }
        }
        boolean needLvt = m.maxLocals > paramSlotCount();
        if (needLvt && (m.localVariables == null || m.localVariables.isEmpty())) {
            report("GPU0301", firstLine(), className);
        }
        boolean assertReported = false;
        int ln = 0;
        for (AbstractInsnNode in = insns.getFirst(); in != null; in = in.getNext()) {
            if (in instanceof LineNumberNode l) {
                ln = l.line;
                continue;
            }
            int op = in.getOpcode();
            if (op < 0) {
                continue;
            }
            switch (op) {
                case Opcodes.ACONST_NULL -> report("GPU0107", ln, "null");
                case Opcodes.LDC -> {
                    Object c = ((LdcInsnNode) in).cst;
                    if (c instanceof String) {
                        report("GPU0106", ln);
                    } else if (!(c instanceof Integer || c instanceof Float || c instanceof Long || c instanceof Double)) {
                        report("GPU0107", ln, c);
                    }
                }
                case Opcodes.GETSTATIC, Opcodes.PUTSTATIC -> {
                    FieldInsnNode f = (FieldInsnNode) in;
                    if (f.name.equals("$assertionsDisabled")) {
                        if (!assertReported) {
                            report("GPU0103", ln);
                            assertReported = true;
                        }
                    } else {
                        report("GPU0115", ln, f.owner.replace('/', '.') + "." + f.name);
                    }
                }
                case Opcodes.GETFIELD, Opcodes.PUTFIELD, Opcodes.NEW, Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL,
                     Opcodes.AALOAD, Opcodes.AASTORE, Opcodes.ARETURN ->
                        notYet = notYet != null ? notYet : "objects: vectors, @Struct, pointers and resident arrays (step A5)";
                case Opcodes.INVOKEINTERFACE, Opcodes.INVOKEDYNAMIC -> report("GPU0108", ln);
                case Opcodes.INVOKESTATIC -> {
                    MethodInsnNode mi = (MethodInsnNode) in;
                    if (mi.owner.equals(GPU)) {
                        if (Builtins.idOf(mi.name) < 0) {
                            report("GPU0112", ln, "Gpu." + mi.name);
                        } else {
                            for (Type t : Type.getArgumentTypes(mi.desc)) {
                                if (t.getSort() == Type.OBJECT) {
                                    notYet = notYet != null ? notYet : "builtins over pointers: atomics (step A5)";
                                }
                            }
                        }
                    } else if (mi.owner.startsWith("java/") || mi.owner.startsWith("javax/")) {
                        report("GPU0112", ln, mi.owner.replace('/', '.') + "." + mi.name);
                    } else {
                        notYet = notYet != null ? notYet : "calls of @DeviceFunction methods (step A4)";
                    }
                }
                case Opcodes.CHECKCAST, Opcodes.INSTANCEOF ->
                        report("GPU0109", ln, Type.getObjectType(((TypeInsnNode) in).desc).getClassName());
                case Opcodes.ATHROW -> report("GPU0101", ln, "throw");
                case Opcodes.MONITORENTER, Opcodes.MONITOREXIT -> report("GPU0102", ln);
                case Opcodes.ANEWARRAY, Opcodes.MULTIANEWARRAY -> report("GPU0117", ln, "an array of arrays or of objects");
                case Opcodes.JSR, Opcodes.RET -> report("GPU0302", ln, Insns.name(op), name);
                case Opcodes.GOTO, Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE,
                     Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT,
                     Opcodes.IF_ICMPLE, Opcodes.TABLESWITCH, Opcodes.LOOKUPSWITCH, Opcodes.LCMP, Opcodes.FCMPL,
                     Opcodes.FCMPG, Opcodes.DCMPL, Opcodes.DCMPG ->
                        notYet = notYet != null ? notYet : "control flow: if, loops, switch, ?:, && and || (step A3)";
                case Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE -> report("GPU0196", ln, "by reference");
                case Opcodes.IFNULL, Opcodes.IFNONNULL -> report("GPU0107", ln, "null");
                default -> {
                    if (!Insns.supported(op)) {
                        report("GPU0302", ln, Insns.name(op), name);
                    }
                }
            }
        }
        if (errors() != before) {
            return false;
        }
        if (notYet != null) {
            throw new NotYet(notYet);
        }
        return true;
    }

    private int errors() {
        int n = 0;
        for (AsmDiagnostic d : diags) {
            if (d.error()) {
                n++;
            }
        }
        return n;
    }

    private void report(String code, int atLine, Object... args) {
        diags.add(AsmDiagnostic.of(code, className, name, atLine, args));
    }

    /** @return первая строка метода из {@code LineNumberTable} либо 0 */
    int firstLine() {
        for (AbstractInsnNode in = insns.getFirst(); in != null; in = in.getNext()) {
            if (in instanceof LineNumberNode l) {
                return l.line;
            }
        }
        return 0;
    }

    private int paramSlotCount() {
        int s = (m.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
        for (Type t : Type.getArgumentTypes(m.desc)) {
            s += t.getSize();
        }
        return s;
    }

    // =============================================================================================
    // Параметры и локали (17.10)
    // =============================================================================================

    private int paramSpace(int i) {
        List<AnnotationNode>[] anns = m.invisibleParameterAnnotations;
        if (anns != null && i < anns.length && anns[i] != null) {
            for (AnnotationNode a : anns[i]) {
                if (a.desc.equals(LOCAL_ANNOTATION)) {
                    return com.sixik.gpu.ir.AddressSpace.LOCAL;
                }
                if (a.desc.equals(GLOBAL_ANNOTATION) && Boolean.TRUE.equals(Annotations.explicitValues(a).get("constant"))) {
                    return com.sixik.gpu.ir.AddressSpace.CONSTANT;
                }
            }
        }
        return com.sixik.gpu.ir.AddressSpace.GLOBAL;
    }

    private String paramName(int i, int slot) {
        if (m.parameters != null && i < m.parameters.size() && m.parameters.get(i).name != null) {
            return m.parameters.get(i).name;
        }
        if (m.localVariables != null) {
            for (LocalVariableNode lv : m.localVariables) {
                if (lv.index == slot && insns.indexOf(lv.start) == 0) {
                    return lv.name;
                }
            }
        }
        return "p" + i;
    }

    private int paramAtSlot(int slot) {
        for (int i = 0; i < paramSlots.length; i++) {
            if (paramSlots[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Запись LVT для слота в точке программы. Диапазон локали начинается <b>после</b> инструкции, которая её
     * инициализирует, поэтому для записи сначала ищется диапазон, начинающийся сразу за ней.
     */
    private LocalVariableNode lvtAt(int slot, AbstractInsnNode at, boolean store) {
        if (m.localVariables == null) {
            return null;
        }
        int idx = insns.indexOf(at);
        if (store) {
            int next = idx + 1;
            while (next < insns.size() && insns.get(next).getOpcode() < 0) {
                next++;
            }
            for (LocalVariableNode lv : m.localVariables) {
                int s = insns.indexOf(lv.start);
                if (lv.index == slot && s > idx && s <= next) {
                    return lv;
                }
            }
        }
        for (LocalVariableNode lv : m.localVariables) {
            if (lv.index == slot && insns.indexOf(lv.start) <= idx && idx < insns.indexOf(lv.end)) {
                return lv;
            }
        }
        return null;
    }

    private Var varOf(LocalVariableNode lv) {
        String key = lv.index + ":" + lv.name + ":" + lv.desc;
        return lvtVars.computeIfAbsent(key, k -> {
            int kind = Kinds.ofDescriptor(lv.desc);
            if (kind == Kinds.K_ARRAY) {
                int elem = Kinds.ofDescriptor(lv.desc.substring(1));
                return new Var(lv.name, kind, elem, localArraySpace(lv), -1, -1);
            }
            if (kind == Kinds.K_OTHER) {
                throw new NotYet("local variable of type " + Type.getType(lv.desc).getClassName() + " (step A5)");
            }
            return new Var(lv.name, kind, -1, -1, Kinds.irType(kind), -1);
        });
    }

    /** {@code @Local} у локального массива - аннотация типа {@code LOCAL_VARIABLE} (цель {@code TYPE_USE}, 17.4). */
    private int localArraySpace(LocalVariableNode lv) {
        if (m.invisibleLocalVariableAnnotations != null) {
            for (LocalVariableAnnotationNode a : m.invisibleLocalVariableAnnotations) {
                if (!a.desc.equals(LOCAL_ANNOTATION)) {
                    continue;
                }
                for (int i = 0; i < a.index.size(); i++) {
                    if (a.index.get(i) == lv.index && a.start.get(i) == lv.start) {
                        return com.sixik.gpu.ir.AddressSpace.LOCAL;
                    }
                }
            }
        }
        return com.sixik.gpu.ir.AddressSpace.PRIVATE;
    }

    private Var varForStore(int slot, AbstractInsnNode at) {
        int p = paramAtSlot(slot);
        if (p >= 0) {
            return paramVars[p];
        }
        LocalVariableNode lv = lvtAt(slot, at, true);
        if (lv == null) {
            throw new IllegalStateException("no LocalVariableTable entry for slot " + slot + " at a store");
        }
        return varOf(lv);
    }

    private Ex read(int slot, AbstractInsnNode at) {
        int p = paramAtSlot(slot);
        if (p >= 0) {
            return paramVars[p] != null ? Ex.localGet(paramVars[p])
                    : Ex.param(p, paramKinds[p], paramElems[p], paramSpaces[p]);
        }
        LocalVariableNode lv = lvtAt(slot, at, false);
        if (lv == null) {
            throw new IllegalStateException("no LocalVariableTable entry for slot " + slot + " at a load");
        }
        return Ex.localGet(varOf(lv));
    }

    // =============================================================================================
    // Символьное исполнение (17.8)
    // =============================================================================================

    /**
     * Исполняет тело на абстрактном стеке.
     *
     * @return стейтменты функции (без объявлений изменяемых параметров - их добавляет эмиттер)
     */
    List<St> execute() {
        markAssignedParams();
        AbstractInsnNode last = lastOpcode();
        for (AbstractInsnNode in = insns.getFirst(); in != null; in = in.getNext()) {
            if (in instanceof LineNumberNode l) {
                line = l.line;
                continue;
            }
            int op = in.getOpcode();
            if (op < 0) {
                continue;
            }
            if (stack.isEmpty()) {
                group++;
            }
            in = step(in, op, in == last);
        }
        return out;
    }

    private AbstractInsnNode lastOpcode() {
        for (AbstractInsnNode in = insns.getLast(); in != null; in = in.getPrevious()) {
            if (in.getOpcode() >= 0) {
                return in;
            }
        }
        return null;
    }

    /** Параметры, в слот которых есть запись, становятся локалями с начальным значением (правило (6) 5.15). */
    private void markAssignedParams() {
        for (AbstractInsnNode in = insns.getFirst(); in != null; in = in.getNext()) {
            int slot = -1;
            if (in instanceof VarInsnNode v && v.getOpcode() >= Opcodes.ISTORE && v.getOpcode() <= Opcodes.ASTORE) {
                slot = v.var;
            } else if (in instanceof IincInsnNode ii) {
                slot = ii.var;
            }
            int p = slot < 0 ? -1 : paramAtSlot(slot);
            if (p >= 0 && paramVars[p] == null && paramKinds[p] != Kinds.K_ARRAY) {
                paramVars[p] = new Var(paramNames[p], paramKinds[p], -1, -1, Kinds.irType(paramKinds[p]), p);
            }
        }
    }

    private void push(Ex e) {
        stack.add(new Slot(e, out.size()));
    }

    private Ex pop() {
        return stack.remove(stack.size() - 1).ex();
    }

    private Slot popSlot() {
        return stack.remove(stack.size() - 1);
    }

    /** Снимает со стека ровно {@code words} слов (элементы возвращаются в порядке стека, снизу вверх). */
    private List<Slot> popWords(int words) {
        List<Slot> r = new ArrayList<>();
        int w = 0;
        while (w < words) {
            Slot s = popSlot();
            r.add(0, s);
            w += s.words();
        }
        if (w != words) {
            throw new IllegalStateException("a dup instruction splits a long/double value");
        }
        return r;
    }

    /** Добавляет стейтмент, предварительно сохранив открытые ожидающие значения стека. */
    private Map<Ex, Var> add(St s) {
        Map<Ex, Var> spilled = spillOpen();
        out.add(s);
        return spilled;
    }

    private Map<Ex, Var> spillOpen() {
        Map<Ex, Var> spilled = new IdentityHashMap<>();
        for (int i = 0; i < stack.size(); i++) {
            Slot s = stack.get(i);
            Ex e = s.ex();
            if (!e.open || e.kind == Kinds.K_ARRAY) {
                continue;
            }
            Var t = spilled.get(e);
            if (t == null) {
                t = temp(e.kind);
                out.add(new St(St.DECL, t, e, null, null, 0, false, line, group));
                spilled.put(e, t);
            }
            stack.set(i, new Slot(Ex.localGet(t), s.mark()));
        }
        return spilled;
    }

    private static Var temp(int kind) {
        return new Var(null, kind, -1, -1, Kinds.irType(kind), -1);
    }

    private St st(int kind, Var v, Ex a) {
        return new St(kind, v, a, null, null, 0, false, line, group);
    }

    /**
     * Приведение значения к виду назначения (локаль, элемент, параметр, результат) - неявное приведение Java.
     * Константа {@code 0/1} в {@code boolean} - константа {@code BOOL} (так javac компилирует {@code true/false}).
     */
    private static Ex coerce(Ex v, int kind) {
        if (v.kind == kind) {
            return isExplicitWidening(v) ? v.withImplicit(true) : v;
        }
        if (kind == Kinds.K_BOOLEAN && v.op == Ex.CONST && (v.bits == 0 || v.bits == 1)) {
            return Ex.constant(Kinds.K_BOOLEAN, v.bits);
        }
        return Ex.convert(kind, v, true);
    }

    private static boolean isExplicitWidening(Ex v) {
        return v.op == Ex.CONVERT && !v.implicit && Kinds.widening(v.kids[0].kind, v.kind);
    }

    /** Вид операнда до явного расширяющего приведения (для решения «приведение неявное»). */
    private static int sourceKind(Ex v) {
        return isExplicitWidening(v) ? v.kids[0].kind : v.kind;
    }

    /** Операнд бинарной операции, повышенный до {@code pk} (числовое повышение Java - неявное). */
    private static Ex promoteOperand(Ex x, Ex other, int pk) {
        if (x.kind == pk) {
            if (isExplicitWidening(x) && Kinds.binaryPromoted(sourceKind(x), sourceKind(other)) == pk) {
                return x.withImplicit(true);
            }
            return x;
        }
        return Ex.convert(pk, x, true);
    }

    private static Ex promoteUnary(Ex x) {
        int k = Kinds.unaryPromoted(x.kind);
        return x.kind == k ? x : Ex.convert(k, x, true);
    }

    private static Ex index(Ex idx) {
        return idx.kind == Kinds.K_INT ? idx : Ex.convert(Kinds.K_INT, idx, true);
    }

    private AbstractInsnNode step(AbstractInsnNode in, int op, boolean last) {
        switch (op) {
            case Opcodes.NOP -> {
            }
            case Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3,
                 Opcodes.ICONST_4, Opcodes.ICONST_5 -> push(Ex.constant(Kinds.K_INT, op - Opcodes.ICONST_0));
            case Opcodes.LCONST_0, Opcodes.LCONST_1 -> push(Ex.constant(Kinds.K_LONG, op - Opcodes.LCONST_0));
            case Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2 ->
                    push(Ex.constant(Kinds.K_FLOAT, Float.floatToRawIntBits(op - Opcodes.FCONST_0)));
            case Opcodes.DCONST_0, Opcodes.DCONST_1 ->
                    push(Ex.constant(Kinds.K_DOUBLE, Double.doubleToRawLongBits(op - Opcodes.DCONST_0)));
            case Opcodes.BIPUSH, Opcodes.SIPUSH -> push(Ex.constant(Kinds.K_INT, ((IntInsnNode) in).operand));
            case Opcodes.LDC -> push(ldc(((LdcInsnNode) in).cst));
            case Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD ->
                    push(read(((VarInsnNode) in).var, in));
            case Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE -> store((VarInsnNode) in);
            case Opcodes.IINC -> iinc((IincInsnNode) in);
            case Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD, Opcodes.BALOAD, Opcodes.CALOAD,
                 Opcodes.SALOAD -> {
                Ex idx = pop();
                Ex arr = pop();
                push(Ex.load(arr, index(idx)));
            }
            case Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE, Opcodes.BASTORE, Opcodes.CASTORE,
                 Opcodes.SASTORE -> arrayStore();
            case Opcodes.ARRAYLENGTH -> push(Ex.arrayLen(pop()));
            case Opcodes.NEWARRAY -> newArray((IntInsnNode) in);
            case Opcodes.POP -> pop();
            case Opcodes.POP2 -> popWords(2);
            case Opcodes.DUP -> dup(1, 0);
            case Opcodes.DUP_X1 -> dup(1, 1);
            case Opcodes.DUP_X2 -> dup(1, 2);
            case Opcodes.DUP2 -> dup(2, 0);
            case Opcodes.DUP2_X1 -> dup(2, 1);
            case Opcodes.DUP2_X2 -> dup(2, 2);
            case Opcodes.SWAP -> {
                Slot a = popSlot();
                Slot b = popSlot();
                stack.add(a);
                stack.add(b);
            }
            case Opcodes.IADD, Opcodes.LADD, Opcodes.FADD, Opcodes.DADD -> arith(Op.ADD);
            case Opcodes.ISUB, Opcodes.LSUB, Opcodes.FSUB, Opcodes.DSUB -> arith(Op.SUB);
            case Opcodes.IMUL, Opcodes.LMUL, Opcodes.FMUL, Opcodes.DMUL -> arith(Op.MUL);
            case Opcodes.IDIV, Opcodes.LDIV, Opcodes.FDIV, Opcodes.DDIV -> arith(Op.DIV);
            case Opcodes.IREM, Opcodes.LREM, Opcodes.FREM, Opcodes.DREM -> arith(Op.REM);
            case Opcodes.IAND, Opcodes.LAND -> arith(Op.AND);
            case Opcodes.IOR, Opcodes.LOR -> arith(Op.OR);
            case Opcodes.IXOR, Opcodes.LXOR -> {
                Ex r = stack.get(stack.size() - 1).ex();
                if (r.isIntConstant(-1) && stack.get(stack.size() - 2).ex().kind != Kinds.K_BOOLEAN) {
                    // ~x javac компилирует как x ^ -1
                    pop();
                    Ex x = promoteUnary(pop());
                    push(Ex.unary(Ex.NOT, x.kind, x));
                } else {
                    arith(Op.XOR);
                }
            }
            case Opcodes.ISHL, Opcodes.LSHL -> shift(Op.SHL);
            case Opcodes.ISHR, Opcodes.LSHR -> shift(Op.SHR);
            case Opcodes.IUSHR, Opcodes.LUSHR -> shift(Op.USHR);
            case Opcodes.INEG, Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG -> {
                Ex x = promoteUnary(pop());
                push(Ex.unary(Ex.NEG, x.kind, x));
            }
            case Opcodes.I2L, Opcodes.F2L, Opcodes.D2L -> push(Ex.convert(Kinds.K_LONG, pop(), false));
            case Opcodes.I2F, Opcodes.L2F, Opcodes.D2F -> push(Ex.convert(Kinds.K_FLOAT, pop(), false));
            case Opcodes.I2D, Opcodes.L2D, Opcodes.F2D -> push(Ex.convert(Kinds.K_DOUBLE, pop(), false));
            case Opcodes.L2I, Opcodes.F2I, Opcodes.D2I -> push(Ex.convert(Kinds.K_INT, pop(), false));
            case Opcodes.I2B -> push(Ex.convert(Kinds.K_BYTE, pop(), false));
            case Opcodes.I2S -> push(Ex.convert(Kinds.K_SHORT, pop(), false));
            case Opcodes.I2C -> push(Ex.convert(Kinds.K_CHAR, pop(), false));
            case Opcodes.INVOKESTATIC -> {
                return builtin((MethodInsnNode) in);
            }
            case Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN ->
                    add(st(St.RETURN, null, coerce(pop(), retKind)));
            case Opcodes.RETURN -> {
                if (!last) {
                    add(st(St.RETURN, null, null));
                }
            }
            default -> throw new IllegalStateException("opcode " + Insns.name(op) + " passed the subset check");
        }
        return in;
    }

    private static Ex ldc(Object c) {
        if (c instanceof Integer i) {
            return Ex.constant(Kinds.K_INT, i);
        }
        if (c instanceof Long l) {
            return Ex.constant(Kinds.K_LONG, l);
        }
        if (c instanceof Float f) {
            return Ex.constant(Kinds.K_FLOAT, Float.floatToRawIntBits(f));
        }
        return Ex.constant(Kinds.K_DOUBLE, Double.doubleToRawLongBits((Double) c));
    }

    private void dup(int words, int skip) {
        List<Slot> top = popWords(words);
        List<Slot> below = popWords(skip);
        stack.addAll(top);
        stack.addAll(below);
        stack.addAll(top);
    }

    private void arith(int irOp) {
        Ex r = pop();
        Ex l = pop();
        if (l.kind == Kinds.K_BOOLEAN && r.kind == Kinds.K_BOOLEAN) {
            // & | ^ над boolean - логические операции BOOL
            push(Ex.binary(irOp, Kinds.K_BOOLEAN, l, r));
            return;
        }
        int pk = Kinds.binaryPromoted(l.kind, r.kind);
        push(Ex.binary(irOp, pk, promoteOperand(l, r, pk), promoteOperand(r, l, pk)));
    }

    /**
     * Сдвиг. Счётчик сдвига в байткоде всегда {@code int}: javac пишет {@code l2i} для счётчика {@code long}. В
     * Java (и в IR {@code frontend-java}) счётчик приводится к типу левого операнда, поэтому {@code l2i} счётчика
     * снимается: для {@code long << long} приведения нет, для {@code int << long} оно неявное.
     */
    private void shift(int irOp) {
        Ex r = pop();
        Ex l = promoteUnary(pop());
        Ex count;
        if (r.op == Ex.CONVERT && !r.implicit && r.kind == Kinds.K_INT && r.kids[0].kind == Kinds.K_LONG) {
            Ex src = r.kids[0];
            count = l.kind == Kinds.K_LONG ? src : Ex.convert(Kinds.K_INT, src, true);
        } else {
            count = promoteUnary(r);
            if (l.kind == Kinds.K_LONG) {
                count = Ex.convert(Kinds.K_LONG, count, true);
            }
        }
        push(Ex.binary(irOp, l.kind, l, count));
    }

    private void store(VarInsnNode in) {
        Ex v = pop();
        Var var = varForStore(in.var, in);
        if (var.kind == Kinds.K_ARRAY) {
            if (v.op != Ex.NEW_ARRAY) {
                report("GPU0116", line, "array " + var.name + " cannot be reassigned");
                return;
            }
            add(new St(St.ARRAY_DECL, var, null, null, null, v.aux, false, line, group));
            return;
        }
        boolean first = var.paramIndex < 0 && declared.add(var);
        add(st(first ? St.DECL : St.SET, var, coerce(v, var.kind)));
    }

    private void iinc(IincInsnNode in) {
        int p = paramAtSlot(in.var);
        Var var = p >= 0 ? paramVars[p] : varOf(lvtAt(in.var, in, false));
        Map<Ex, Var> spilled = spillOpen();
        Ex base = Ex.localGet(var);
        for (Map.Entry<Ex, Var> e : spilled.entrySet()) {
            // постфиксный x++ в выражении: старое значение уже во временной локали - шаг считается от неё, как в
            // frontend-java (old = x; x = old + 1)
            if (e.getKey().op == Ex.LOCAL_GET && e.getKey().var == var) {
                base = Ex.localGet(e.getValue());
            }
        }
        int c = in.incr;
        Ex value = Ex.binary(c < 0 ? Op.SUB : Op.ADD, Kinds.K_INT, base, Ex.constant(Kinds.K_INT, Math.abs((long) c)));
        out.add(st(St.SET, var, value));
    }

    private void arrayStore() {
        Ex v = pop();
        Ex idx = pop();
        Slot arr = popSlot();
        Ex value = coerce(v, arr.ex().elemKind);
        boolean ordered = out.size() > arr.mark();
        add(new St(St.STORE, null, arr.ex(), index(idx), value, 0, ordered, line, group));
    }

    private void newArray(IntInsnNode in) {
        Ex len = pop();
        int elem = switch (in.operand) {
            case Opcodes.T_BOOLEAN -> Kinds.K_BOOLEAN;
            case Opcodes.T_BYTE -> Kinds.K_BYTE;
            case Opcodes.T_SHORT -> Kinds.K_SHORT;
            case Opcodes.T_CHAR -> Kinds.K_CHAR;
            case Opcodes.T_INT -> Kinds.K_INT;
            case Opcodes.T_LONG -> Kinds.K_LONG;
            case Opcodes.T_FLOAT -> Kinds.K_FLOAT;
            default -> Kinds.K_DOUBLE;
        };
        if (len.op != Ex.CONST) {
            report("GPU0117", line, "the size is not a constant");
            push(Ex.newArray(elem, 1));
            return;
        }
        push(Ex.newArray(elem, (int) len.bits));
    }

    /** {@code invokestatic Gpu.*}: builtin, барьер или эффектный builtin (его результат - временная локаль). */
    private AbstractInsnNode builtin(MethodInsnNode mi) {
        int id = Builtins.idOf(mi.name);
        Type[] at = Type.getArgumentTypes(mi.desc);
        Ex[] args = new Ex[at.length];
        for (int i = at.length - 1; i >= 0; i--) {
            args[i] = pop();
        }
        boolean anyOpen = false;
        for (int i = 0; i < args.length; i++) {
            int k = Kinds.ofDescriptor(at[i].getDescriptor());
            if (k != Kinds.K_ARRAY) {
                args[i] = coerce(args[i], k);
                anyOpen |= args[i].open;
            }
        }
        if (Builtins.has(id, Builtins.SYNC)) {
            add(new St(St.BARRIER, null, null, null, null, mi.name.equals("barrierGlobal") ? 3 : 1, false, line, group));
            return mi;
        }
        int rk = Kinds.ofDescriptor(Type.getReturnType(mi.desc).getDescriptor());
        boolean effect = Builtins.has(id, Builtins.EFFECT);
        Ex call = Ex.intrinsic(id, rk, anyOpen || Builtins.has(id, Builtins.READS), args);
        if (!effect) {
            push(call);
            return mi;
        }
        AbstractInsnNode next = mi.getNext();
        while (next != null && next.getOpcode() < 0) {
            next = next.getNext();
        }
        if (rk == Kinds.K_VOID) {
            add(st(St.EXPR, null, call));
            return mi;
        }
        if (next != null && (next.getOpcode() == Opcodes.POP || next.getOpcode() == Opcodes.POP2)) {
            // результат не используется: стейтмент-выражение, как вызов-стейтмент в frontend-java
            add(st(St.EXPR, null, call));
            return next;
        }
        Var t = temp(rk);
        add(st(St.DECL, t, call));
        push(Ex.localGet(t));
        return mi;
    }
}
