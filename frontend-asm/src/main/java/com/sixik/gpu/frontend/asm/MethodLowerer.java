package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.frontend.common.JdkIntrinsics;
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
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
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
 * Метод байткода → стейтменты фронтенда (REWORK_ARCHITECTURE.md, 17.7, шаги 1-4): проверка подмножества (все
 * нарушения метода за один проход, 17.6), граф ({@link Cfg}), символьное исполнение блоков на типизированном стеке
 * (17.8) под управлением структуризатора ({@link Structurer}, 17.9) и размещение объявлений ({@link Scopes}, 17.10).
 *
 * <p>Шаги 17.15: A2 - прямолинейные тела, A3 - управление потоком, A4 - вызовы {@code @DeviceFunction} (сведения о
 * вызываемых даёт сборщик модуля через {@link #callees}). Векторы, struct, указатели и атомики (A5) дают
 * {@link NotYet}: вызывающий превращает это в {@code GPU0902}, а не строит неверный IR.</p>
 *
 * <p><b>Переменные.</b> Переменная - «паутина» записей LVT с одинаковыми слотом, именем и дескриптором, связанных
 * достигающими определениями: javac дробит диапазон локали без инициализатора, присвоенной в ветках, а соседние
 * области ({@code for (int i ...)} дважды) дают разные переменные в одном слоте.</p>
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

    /** Ошибка структуры уже выдана диагностикой: исполнение метода прекращается. */
    static final class Fail extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Fail(String code) {
            super(code, null, false, false);
        }
    }

    /** Снимок состояния исполнителя (пробное исполнение блока при сборке цепочки условий). */
    record Snap(List<St> out, int outSize, List<Slot> stack, int diags, int group, int line) {
    }

    /**
     * Сведения о вызываемой device-функции.
     *
     * @param key         ключ функции ({@link #key})
     * @param paramKinds  виды параметров
     * @param paramSpaces адресные пространства параметров-массивов (иначе -1)
     * @param paramNames  имена параметров (для диагностик)
     * @param retKind     вид результата
     * @param effects     функция или её вызываемые пишут память, используют барьер или эффектный builtin
     */
    record Callee(String key, int[] paramKinds, int[] paramSpaces, String[] paramNames, int retKind, String retCls,
                  boolean effects) {
    }

    /** Вызов device-функции в теле: инструкция и строка (для диагностик сборщика модуля). */
    record DeviceCall(MethodInsnNode insn, int line) {
    }

    /**
     * Элемент стека: выражение, число стейтментов на момент, когда оно легло на стек, и индекс первой инструкции его
     * вычисления (порядок вызовов исходника, 17.11).
     */
    record Slot(Ex ex, int mark, int start) {
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
    /** Внутреннее имя агрегата параметра (у массива - элемента), иначе {@code null}. */
    final String[] paramCls;
    /** Параметр-указатель - ссылка {@code FloatPtr}. */
    final boolean[] paramRef;
    final String[] paramNames;
    private final int[] paramSlots;
    /** Изменяемый параметр - локаль с начальным значением {@code param(i)}; иначе {@code null}. */
    final Var[] paramVars;
    final int retKind;
    final String retCls;
    final Aggs aggs;

    /** Паутины переменных: запись LVT → представитель; представитель → переменная. */
    private final Map<LocalVariableNode, LocalVariableNode> webParent = new IdentityHashMap<>();
    private final Map<LocalVariableNode, Var> webVars = new IdentityHashMap<>();
    private final List<Slot> stack = new ArrayList<>();
    /** Текущая инструкция и наименьшее начало значений, снятых ею со стека. */
    private int curInsn;
    /** Переменная последней записи {@link #store} ({@code null}, если это не {@code local_set}). */
    private Var lastStored;
    private int popMin = Integer.MAX_VALUE;
    /** Вызовы device-функций, найденные проверкой ({@link #check}), в порядке байткода. */
    final List<DeviceCall> deviceCalls = new ArrayList<>();
    /** Сведения о вызываемых; {@code null} для вызова, который сборщик модуля не разрешил. */
    java.util.function.Function<MethodInsnNode, Callee> callees = mi -> null;
    /** Привязки полей ({@link Bindings}, 18.2-S3): {@code getstatic} привязанного поля - константа. */
    Bindings bindings = Bindings.none();
    private List<St> out = new ArrayList<>();
    private int[] lineAt;
    private AbstractInsnNode lastOp;
    private int line;
    private int group;

    /**
     * @param className двоичное имя класса (для диагностик)
     * @param m         метод с телом (для сплетённого ядра - {@code <имя>$jvm})
     * @param name      имя функции (исходное имя метода)
     * @param diags     приёмник диагностик
     */
    MethodLowerer(String className, MethodNode m, String name, List<AsmDiagnostic> diags, Aggs aggs) {
        this.aggs = aggs;
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
        paramCls = new String[n];
        paramRef = new boolean[n];
        int slot = (m.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
        for (int i = 0; i < n; i++) {
            String d = args[i].getDescriptor();
            Aggs.Shape sh = aggs.shape(d);
            paramKinds[i] = sh.kind();
            paramElems[i] = sh.elemKind();
            paramCls[i] = sh.cls();
            paramRef[i] = sh.ref();
            paramSpaces[i] = paramKinds[i] == Kinds.K_ARRAY ? paramSpace(i) : sh.space();
            paramSlots[i] = slot;
            paramNames[i] = paramName(i, slot);
            slot += args[i].getSize();
        }
        Aggs.Shape rs = aggs.shape(Type.getReturnType(m.desc).getDescriptor());
        retKind = rs.kind();
        retCls = rs.cls();
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
                    Object bound = op == Opcodes.GETSTATIC ? bindings.constant(f.owner, f.name) : null;
                    if (f.name.equals("$assertionsDisabled")) {
                        if (!assertReported) {
                            report("GPU0103", ln);
                            assertReported = true;
                        }
                    } else if (bound != null) {
                        String problem = Bindings.problem(f.desc, bound);
                        if (problem != null) {
                            report("GPU0308", ln, f.owner.replace('/', '.') + "." + f.name, problem);
                        }
                    } else {
                        report("GPU0115", ln, f.owner.replace('/', '.') + "." + f.name);
                    }
                }
                case Opcodes.GETFIELD, Opcodes.PUTFIELD -> {
                    String owner = ((FieldInsnNode) in).owner;
                    if (!api(owner)) {
                        notYet = notYet != null ? notYet : "field of class " + owner.replace('/', '.');
                    }
                }
                case Opcodes.NEW -> {
                    String owner = ((TypeInsnNode) in).desc;
                    Aggs.Ptr ptr = Aggs.pointer(owner);
                    if (!aggs.isAggregate(owner) && ptr == null) {
                        report("GPU0192", ln, Type.getObjectType(owner).getClassName(),
                                "only vectors, @Struct and pointer references are created in a kernel");
                    } else if (ptr != null && ptr.ref() && !storedAfterInit(in, owner)) {
                        report("GPU0198", ln, Type.getObjectType(owner).getClassName(),
                                "a reference is created only in a local variable declaration");
                    }
                }
                case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL -> {
                    String owner = ((MethodInsnNode) in).owner;
                    String mname = ((MethodInsnNode) in).name;
                    // методы Object у типов API (toString, equals, ...) отвергаются сразу; прочие имена - при понижении
                    if (!api(owner) && Aggs.resident(owner) < 0 || OBJECT_METHODS.contains(mname)) {
                        report("GPU0112", ln, owner.replace('/', '.') + "." + mname);
                    }
                }
                case Opcodes.AALOAD, Opcodes.AASTORE, Opcodes.ARETURN -> {
                    // массивы агрегатов и возврат агрегата: тип проверен по дескрипторам
                }
                case Opcodes.INVOKEINTERFACE, Opcodes.INVOKEDYNAMIC -> report("GPU0108", ln);
                case Opcodes.INVOKESTATIC -> {
                    MethodInsnNode mi = (MethodInsnNode) in;
                    if (mi.owner.equals(GPU)) {
                        if (Builtins.idOf(mi.name) < 0) {
                            report("GPU0112", ln, "Gpu." + mi.name);
                        }
                    } else if (Aggs.vector(mi.owner) != null || Aggs.pointer(mi.owner) != null) {
                        // splat, of: API типов
                    } else if (mi.owner.startsWith("java/") || mi.owner.startsWith("javax/")) {
                        if (JdkIntrinsics.find(mi.owner, mi.name, mi.desc) == null) {
                            report("GPU0112", ln, mi.owner.replace('/', '.') + "." + mi.name);
                        }
                    } else {
                        deviceCalls.add(new DeviceCall(mi, ln));
                    }
                }
                case Opcodes.CHECKCAST, Opcodes.INSTANCEOF ->
                        report("GPU0109", ln, Type.getObjectType(((TypeInsnNode) in).desc).getClassName());
                case Opcodes.ATHROW -> report("GPU0101", ln, "throw");
                case Opcodes.MONITORENTER, Opcodes.MONITOREXIT -> report("GPU0102", ln);
                case Opcodes.MULTIANEWARRAY -> report("GPU0005", ln, Type.getType(((MultiANewArrayInsnNode) in).desc).getClassName());
                case Opcodes.ANEWARRAY -> {
                    String comp = ((TypeInsnNode) in).desc;
                    if (comp.startsWith("[")) {
                        report("GPU0005", ln, Type.getType(comp).getClassName() + "[]");
                    } else if (aggs.isAggregate(comp)) {
                        report("GPU0117", ln, "local array of objects " + Type.getObjectType(comp).getClassName()
                                + ": on the JVM its elements are null, and on the GPU zeros");
                    } else {
                        report("GPU0119", ln, Type.getObjectType(comp).getClassName() + "[]");
                    }
                }
                case Opcodes.JSR, Opcodes.RET -> report("GPU0302", ln, Insns.name(op), name);
                case Opcodes.GOTO, Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE,
                     Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT,
                     Opcodes.IF_ICMPLE, Opcodes.TABLESWITCH, Opcodes.LOOKUPSWITCH, Opcodes.LCMP, Opcodes.FCMPL,
                     Opcodes.FCMPG, Opcodes.DCMPL, Opcodes.DCMPG -> {
                    // управление потоком: структуризатор (17.9)
                }
                case Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE -> {
                    // GPU0196 (агрегаты) или GPU0198 (указатели) - по типам операндов при понижении
                }
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

    /**
     * Ссылка {@code new FloatPtr(...)} сразу сохраняется в локаль ({@code astore} после {@code <init>}): иначе это не
     * инициализатор объявления. Аргументы конструктора - скаляры, вложенных {@code new} того же класса нет.
     */
    private static boolean storedAfterInit(AbstractInsnNode neu, String owner) {
        for (AbstractInsnNode in = neu.getNext(); in != null; in = in.getNext()) {
            if (in instanceof MethodInsnNode mi && mi.getOpcode() == Opcodes.INVOKESPECIAL && mi.owner.equals(owner)
                    && mi.name.equals("<init>")) {
                AbstractInsnNode next = nextOpcode(in);
                return next != null && next.getOpcode() == Opcodes.ASTORE;
            }
        }
        return false;
    }

    /** Методы {@code Object}: у векторов, struct и указателей на GPU их нет ({@code GPU0112}). */
    private static final java.util.Set<String> OBJECT_METHODS = java.util.Set.of("toString", "hashCode", "equals",
            "getClass", "clone", "finalize", "notify", "notifyAll", "wait");

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
        List<LocalVariableNode> entries = lvtOfSlot(slot);
        if (store) {
            int next = idx + 1;
            while (next < insns.size() && insns.get(next).getOpcode() < 0) {
                next++;
            }
            for (LocalVariableNode lv : entries) {
                int s = insns.indexOf(lv.start);
                if (s > idx && s <= next) {
                    return lv;
                }
            }
        }
        for (LocalVariableNode lv : entries) {
            if (insns.indexOf(lv.start) <= idx && idx < insns.indexOf(lv.end)) {
                return lv;
            }
        }
        return null;
    }

    /** Записи LVT по слотам в порядке таблицы (первое совпадение - как при обходе всей таблицы); строится один раз. */
    private Map<Integer, List<LocalVariableNode>> lvtBySlot;

    /** @return записи LVT слота: поиск в точке программы не просматривает всю таблицу (роутер - тысячи записей, B7) */
    private List<LocalVariableNode> lvtOfSlot(int slot) {
        if (lvtBySlot == null) {
            lvtBySlot = new HashMap<>();
            for (LocalVariableNode lv : m.localVariables) {
                lvtBySlot.computeIfAbsent(lv.index, z -> new ArrayList<>()).add(lv);
            }
        }
        return lvtBySlot.getOrDefault(slot, List.of());
    }

    /**
     * Запись LVT, которую начинает запись в слот: её диапазон начинается сразу за инструкцией.
     *
     * @return запись либо {@code null} (запись в уже живую переменную)
     */
    private LocalVariableNode declaringEntry(int slot, AbstractInsnNode at) {
        LocalVariableNode lv = lvtAt(slot, at, true);
        return lv != null && insns.indexOf(lv.start) > insns.indexOf(at) ? lv : null;
    }

    private LocalVariableNode root(LocalVariableNode lv) {
        LocalVariableNode r = lv;
        while (webParent.containsKey(r)) {
            r = webParent.get(r);
        }
        return r;
    }

    private void union(LocalVariableNode a, LocalVariableNode b) {
        if (a == null || b == null || a.index != b.index || !a.name.equals(b.name) || !a.desc.equals(b.desc)) {
            return;
        }
        LocalVariableNode ra = root(a);
        LocalVariableNode rb = root(b);
        if (ra != rb) {
            webParent.put(rb, ra);
        }
    }

    private static int storeSlot(AbstractInsnNode in) {
        if (in instanceof VarInsnNode v && v.getOpcode() >= Opcodes.ISTORE && v.getOpcode() <= Opcodes.ASTORE) {
            return v.var;
        }
        return in instanceof IincInsnNode ii ? ii.var : -1;
    }

    private static int loadSlot(AbstractInsnNode in) {
        if (in instanceof VarInsnNode v && v.getOpcode() >= Opcodes.ILOAD && v.getOpcode() <= Opcodes.ALOAD) {
            return v.var;
        }
        return in instanceof IincInsnNode ii ? ii.var : -1;
    }

    /**
     * Паутины переменных (17.10): достигающие определения по каждому слоту; чтение объединяет свою запись LVT с
     * записями LVT всех достигающих его записей в слот.
     *
     * <p>Шаг B7 (FRONTEND_ASM_RUNTIME.md, S9): достигающие записи каждого чтения ищутся обратным обходом графа от
     * чтения до первой записи в слот на каждом пути. Прежний анализ потоков данных держал у каждого блока множество
     * всех записей всех слотов, а у каждого чтения перебирал его целиком; у роутера с тысячами локалей это квадрат, у
     * вложенных циклов - ещё и проход на каждый уровень. Результат тот же: множество достигающих записей не зависит от
     * способа поиска, а объединения идут в прежнем порядке (блоки, инструкции, записи по возрастанию), поэтому корни
     * паутин не меняются.</p>
     */
    private void buildWebs(Cfg g) {
        if (m.localVariables == null) {
            return;
        }
        Set<Integer> slots = new HashSet<>();
        for (LocalVariableNode lv : m.localVariables) {
            if (paramAtSlot(lv.index) < 0) {
                slots.add(lv.index);
            }
        }
        int n = g.size();
        // последняя запись каждого слота в блоке: ключ - (блок, слот)
        Map<Long, Integer> lastStore = new HashMap<>();
        for (int b = 0; b < n; b++) {
            Cfg.Block bl = g.get(b);
            for (int k = bl.start; k < bl.end; k++) {
                int slot = storeSlot(insns.get(k));
                if (slot >= 0 && slots.contains(slot)) {
                    lastStore.put(((long) b << 32) | slot, k);
                }
            }
        }
        int[] seen = new int[n];
        int stamp = 0;
        java.util.ArrayDeque<Integer> work = new java.util.ArrayDeque<>();
        List<Integer> reaching = new ArrayList<>();
        for (int b = 0; b < n; b++) {
            Cfg.Block bl = g.get(b);
            for (int k = bl.start; k < bl.end; k++) {
                AbstractInsnNode in = insns.get(k);
                int slot = loadSlot(in);
                if (slot < 0 || !slots.contains(slot)) {
                    continue;
                }
                reaching.clear();
                int local = -1;
                for (int j = k - 1; j >= bl.start; j--) {
                    if (storeSlot(insns.get(j)) == slot) {
                        local = j;
                        break;
                    }
                }
                if (local >= 0) {
                    reaching.add(local);
                } else {
                    // вверх по предшественникам до записи в слот (сам блок по обратной дуге - его последняя запись)
                    stamp++;
                    work.clear();
                    for (int p : bl.preds) {
                        work.push(p);
                    }
                    while (!work.isEmpty()) {
                        int p = work.pop();
                        if (seen[p] == stamp) {
                            continue;
                        }
                        seen[p] = stamp;
                        Integer def = lastStore.get(((long) p << 32) | slot);
                        if (def != null) {
                            reaching.add(def);
                            continue;
                        }
                        for (int q : g.get(p).preds) {
                            if (seen[q] != stamp) {
                                work.push(q);
                            }
                        }
                    }
                    java.util.Collections.sort(reaching);
                }
                if (!reaching.isEmpty()) {
                    LocalVariableNode use = lvtAt(slot, in, false);
                    for (int d : reaching) {
                        union(use, defEntry(slot, insns.get(d)));
                    }
                }
            }
        }
    }

    private LocalVariableNode defEntry(int slot, AbstractInsnNode def) {
        return def instanceof IincInsnNode ? lvtAt(slot, def, false) : lvtAt(slot, def, true);
    }

    private Var varOf(LocalVariableNode entry) {
        LocalVariableNode lv = root(entry);
        return webVars.computeIfAbsent(lv, k -> {
            int kind = Kinds.ofDescriptor(lv.desc);
            if (kind == Kinds.K_ARRAY) {
                int elem = Kinds.ofDescriptor(lv.desc.substring(1));
                return new Var(lv.name, kind, elem, localArraySpace(lv), -1, -1);
            }
            if (kind == Kinds.K_OTHER) {
                Aggs.Shape sh = aggs.shape(lv.desc);
                if (sh.kind() == Kinds.K_AGG) {
                    Var v = new Var(lv.name, Kinds.K_AGG, -1, -1, -1, -1);
                    v.cls = sh.cls();
                    return v;
                }
                if (sh.kind() == Kinds.K_PTR) {
                    Var v = new Var(lv.name, Kinds.K_PTR, sh.elemKind(), sh.space(),
                            sh.ref() ? Kinds.storageType(sh.elemKind()) : -1, -1);
                    v.owned = sh.ref();
                    return v;
                }
                throw new NotYet("local variable of type " + Type.getType(lv.desc).getClassName());
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
                    : Ex.param(p, paramKinds[p], paramElems[p], paramSpaces[p]).typed(paramCls[p]);
        }
        LocalVariableNode lv = lvtAt(slot, at, false);
        if (lv == null) {
            throw new IllegalStateException("no LocalVariableTable entry for slot " + slot + " at a load");
        }
        Var v = varOf(lv);
        return v.owned ? Ex.localAddr(v) : Ex.localGet(v);
    }

    // =============================================================================================
    // Символьное исполнение (17.8)
    // =============================================================================================

    /**
     * Строит граф, исполняет тело под управлением структуризатора и размещает объявления.
     *
     * @return стейтменты функции (без объявлений изменяемых параметров - их добавляет эмиттер); после ошибки
     * структуры (диагностика уже выдана) - пустой список
     */
    List<St> execute() {
        markAssignedParams();
        lastOp = lastOpcode();
        lineAt = new int[insns.size()];
        int ln = 0;
        for (int k = 0; k < insns.size(); k++) {
            if (insns.get(k) instanceof LineNumberNode l) {
                ln = l.line;
            }
            lineAt[k] = ln;
        }
        Map<Integer, Integer> updates = new HashMap<>();
        Cfg g = Cfg.build(insns, updateSplits(Cfg.build(insns, List.of()), updates));
        Map<Integer, Integer> byBlock = new HashMap<>();
        updates.forEach((h, u) -> byBlock.put(g.blockOfInsn(h), g.blockOfInsn(u)));
        g.computeDominators();
        buildWebs(g);
        try {
            List<St> body = new Structurer(this, g, byBlock).run();
            Scopes.place(body);
            return body;
        } catch (Fail f) {
            return List.of();
        }
    }

    /**
     * Шаг цикла {@code for} (17.9-4): хвост латча {@code goto заголовок}, все инструкции которого стоят на строке
     * заголовка (javac относит {@code i++} из {@code for (...; i++)} к строке {@code for}).
     *
     * @param g0      граф без разбиения
     * @param updates сюда: индекс первой инструкции заголовка → индекс начала шага
     * @return индексы, с которых начинаются блоки шага
     */
    private List<Integer> updateSplits(Cfg g0, Map<Integer, Integer> updates) {
        List<Integer> splits = new ArrayList<>();
        for (int li = 0; li < g0.size(); li++) {
            Cfg.Block l = g0.get(li);
            if (l.term != Cfg.GOTO || l.target > li || g0.get(l.target).term != Cfg.COND) {
                continue;
            }
            Cfg.Block h = g0.get(l.target);
            int headerLine = lineAt[firstOpcodeIndex(h)];
            if (headerLine == 0) {
                // без LineNumberTable (18.2-S7) шаг по строке не найти: цикл - while, а латч, в который ведут
                // continue (больше одного предшественника), - шаг: он исполняется и после тела, и по continue
                if (l.preds.size() > 1) {
                    splits.add(l.start);
                    updates.put(h.start, l.start);
                }
                continue;
            }
            int jump = insns.indexOf(l.last);
            int u = -1;
            int before = -1;
            for (int k = jump - 1; k >= l.start; k--) {
                if (insns.get(k).getOpcode() < 0) {
                    continue;
                }
                if (lineAt[k] == headerLine) {
                    u = k;
                } else {
                    before = k;
                    break;
                }
            }
            if (u < 0) {
                continue;
            }
            int split = before < 0 ? l.start : before + 1;
            splits.add(split);
            updates.put(h.start, split);
        }
        return splits;
    }

    private int firstOpcodeIndex(Cfg.Block b) {
        for (int k = b.start; k < b.end; k++) {
            if (insns.get(k).getOpcode() >= 0) {
                return k;
            }
        }
        return b.start;
    }

    // ---- интерфейс для структуризатора ------------------------------------------------------------

    /** Исполняет инструкции {@code [from, to)} (без завершающего перехода блока). */
    void run(int from, int to) {
        for (int k = from; k < to; k++) {
            AbstractInsnNode in = insns.get(k);
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
            curInsn = k;
            popMin = Integer.MAX_VALUE;
            AbstractInsnNode r = step(in, op, in == lastOp);
            if (r != in) {
                k = insns.indexOf(r);
            }
        }
    }

    Snap snap() {
        return new Snap(out, out.size(), new ArrayList<>(stack), diags.size(), group, line);
    }

    void restore(Snap s) {
        out = s.out();
        while (out.size() > s.outSize()) {
            out.remove(out.size() - 1);
        }
        stack.clear();
        stack.addAll(s.stack());
        while (diags.size() > s.diags()) {
            diags.remove(diags.size() - 1);
        }
        group = s.group();
        line = s.line();
    }

    /** Подменяет список, в который идут стейтменты; возвращает прежний. */
    List<St> swapOut(List<St> list) {
        List<St> old = out;
        out = list;
        return old;
    }

    /** Стейтмент с правилом эффектов (ожидающие открытые значения стека сохраняются раньше него). */
    void emit(St s) {
        add(s);
    }

    void emitRaw(St s) {
        out.add(s);
    }

    /** @return число стейтментов текущего списка */
    int outSize() {
        return out.size();
    }

    /**
     * Стейтменты условного выражения-значения - часть стейтмента, который потребит значение (стек между блоками
     * условия пуст, но исходный стейтмент тот же): они получают его номер, и эмиттер объявляет его переменную раньше
     * их временных локалей, как {@code frontend-java}.
     *
     * @param from     начало стейтментов выражения в текущем списке
     * @param minGroup номер, с которого начинается выражение (более ранние стейтменты блока - другие исходные)
     */
    void regroup(int from, int minGroup) {
        for (int i = from; i < out.size(); i++) {
            St s = out.get(i);
            if (s.group >= minGroup) {
                s.group = group;
            }
        }
    }

    void emitAll(List<St> list) {
        out.addAll(list);
    }

    /** Выдаёт ошибку структуры и прекращает исполнение метода. */
    void fail(String code, Object... args) {
        report(code, line, args);
        throw new Fail(code);
    }

    int stackSize() {
        return stack.size();
    }

    List<Slot> stackCopy() {
        return new ArrayList<>(stack);
    }

    void setStack(List<Slot> s) {
        stack.clear();
        stack.addAll(s);
    }

    /** {@code true}, если стек совпадает с {@code base} поэлементно (те же записи). */
    boolean sameStack(List<Slot> base) {
        if (stack.size() != base.size()) {
            return false;
        }
        for (int i = 0; i < base.size(); i++) {
            if (stack.get(i) != base.get(i)) {
                return false;
            }
        }
        return true;
    }

    boolean hasOpenPending() {
        for (Slot s : stack) {
            if (s.ex().open && s.ex().kind != Kinds.K_ARRAY) {
                return true;
            }
        }
        return false;
    }

    void spillPending() {
        spillOpen(false, (Var) null);
    }

    int line() {
        return line;
    }

    int group() {
        return group;
    }

    String name() {
        return name;
    }

    int lineOf(AbstractInsnNode in) {
        return lineAt[insns.indexOf(in)];
    }

    /**
     * {@code invokestatic} без эффектов: builtin {@code Gpu.*} без флагов {@code EFFECT/SYNC} или device-функция без
     * эффектов.
     */
    boolean pureCall(MethodInsnNode mi) {
        if (JdkIntrinsics.find(mi.owner, mi.name, mi.desc) != null) {
            return true;
        }
        if (!mi.owner.equals(GPU)) {
            Callee c = callees.apply(mi);
            return c != null && !c.effects();
        }
        int id = Builtins.idOf(mi.name);
        return id >= 0 && !Builtins.has(id, Builtins.EFFECT) && !Builtins.has(id, Builtins.SYNC);
    }

    /**
     * Ключ функции в модуле.
     *
     * @param owner внутреннее имя класса
     * @param name  имя метода
     * @param desc  дескриптор
     * @return {@code owner.name desc}
     */
    static String key(String owner, String name, String desc) {
        return owner + "." + name + desc;
    }

    /**
     * Собственные эффекты тела (без вызываемых): запись элемента массива, барьер, эффектный builtin - то, от чего
     * {@code frontend-java} помечает функцию эффектной.
     *
     * @return {@code true}, если тело пишет память или синхронизируется
     */
    boolean ownEffects() {
        for (AbstractInsnNode in = insns.getFirst(); in != null; in = in.getNext()) {
            int op = in.getOpcode();
            if (op >= Opcodes.IASTORE && op <= Opcodes.SASTORE || op == Opcodes.PUTFIELD) {
                return true;
            }
            if (op == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) in).name.equals("set")
                    && (Aggs.pointer(((MethodInsnNode) in).owner) != null || Aggs.resident(((MethodInsnNode) in).owner) >= 0)) {
                return true;
            }
            if (op == Opcodes.INVOKESTATIC && ((MethodInsnNode) in).owner.equals(GPU)) {
                int id = Builtins.idOf(((MethodInsnNode) in).name);
                if (id >= 0 && (Builtins.has(id, Builtins.EFFECT) || Builtins.has(id, Builtins.SYNC))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Условие условного перехода: снимает операнды со стека.
     *
     * @return булево выражение «переход выполняется»
     */
    Ex jumpCond(org.objectweb.asm.tree.JumpInsnNode j) {
        int op = j.getOpcode();
        int rel = Conds.relation(op);
        if (op >= Opcodes.IFEQ && op <= Opcodes.IFLE) {
            Ex v = pop();
            if (v.op == Ex.CMP3) {
                return Conds.fromCmp3(v, rel);
            }
            Ex bv = Conds.boolValue(v);
            if (bv != null && (rel == Op.EQ || rel == Op.NE)) {
                return rel == Op.NE ? bv : Conds.neg(bv);
            }
            return Conds.compare(rel, v, Ex.constant(Kinds.K_INT, 0));
        }
        Ex r = pop();
        Ex l = pop();
        if (op == Opcodes.IF_ACMPEQ || op == Opcodes.IF_ACMPNE) {
            if (l.kind == Kinds.K_PTR || r.kind == Kinds.K_PTR) {
                report("GPU0198", line, simple(l.cls != null ? l.cls : String.valueOf(r.cls)),
                        "pointers are not compared: compare values (p.get(0)) or indices");
            } else {
                report("GPU0196", line, "by reference");
            }
            return Ex.constant(Kinds.K_BOOLEAN, 0);
        }
        return Conds.compare(rel, l, r);
    }

    /**
     * Значение «ромба» {@code c ? a : b}: тип - общий тип плеч (константа {@code int} рядом с {@code byte/short/char}
     * или {@code boolean} принимает их тип, как в Java); {@code c ? true : false} → {@code c}.
     */
    Ex diamond(Ex c, Ex a, Ex b) {
        if (a.kind == Kinds.K_PTR || b.kind == Kinds.K_PTR) {
            report("GPU0198", line, simple(a.cls != null ? a.cls : String.valueOf(b.cls)),
                    "a pointer cannot be chosen by a conditional expression; use if with assignment");
        }
        int k;
        if (a.kind == b.kind) {
            k = a.kind;
        } else if (a.kind == Kinds.K_BOOLEAN && isBit(b) || b.kind == Kinds.K_BOOLEAN && isBit(a)) {
            k = Kinds.K_BOOLEAN;
        } else if (isIntConst(a) && small(b.kind)) {
            k = b.kind;
        } else if (isIntConst(b) && small(a.kind)) {
            k = a.kind;
        } else {
            k = Kinds.binaryPromoted(a.kind, b.kind);
        }
        Ex aa = coerce(a, k);
        Ex bb = coerce(b, k);
        if (k == Kinds.K_BOOLEAN && aa.op == Ex.CONST && bb.op == Ex.CONST && aa.bits != bb.bits) {
            return aa.bits == 1 ? c : Conds.neg(c);
        }
        return Ex.select(k, c, aa, bb);
    }

    private static boolean isBit(Ex e) {
        return e.isIntConstant(0) || e.isIntConstant(1);
    }

    private static boolean isIntConst(Ex e) {
        return e.op == Ex.CONST && e.kind == Kinds.K_INT;
    }

    private static boolean small(int k) {
        return k == Kinds.K_BYTE || k == Kinds.K_SHORT || k == Kinds.K_CHAR;
    }

    /**
     * Инициализаторы {@code for} (17.9-8): хвостовые записи текущего списка, объявляющие локаль, диапазон LVT
     * которой кончается на выходе цикла. Снимаются со списка.
     */
    List<St> pullForInit(Cfg g, int exit) {
        List<St> r = new ArrayList<>();
        while (!out.isEmpty()) {
            St s = out.get(out.size() - 1);
            if (s.kind == St.SET && s.lvt != null && g.blockAt(s.lvt.end) == exit) {
                r.add(0, out.remove(out.size() - 1));
            } else {
                break;
            }
        }
        return r;
    }

    /** Нулевое значение вида (инициализатор {@code local_decl} без записи, как {@code zero} в {@code frontend-java}). */
    static Ex zero(int kind) {
        return switch (kind) {
            case Kinds.K_FLOAT -> Ex.constant(kind, Float.floatToRawIntBits(0f));
            case Kinds.K_DOUBLE -> Ex.constant(kind, Double.doubleToRawLongBits(0.0));
            default -> Ex.constant(kind, 0);
        };
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
                int k = paramKinds[p];
                boolean scalar = k != Kinds.K_AGG && k != Kinds.K_PTR;
                paramVars[p] = new Var(paramNames[p], k, k == Kinds.K_PTR ? paramElems[p] : -1,
                        k == Kinds.K_PTR ? paramSpaces[p] : -1, scalar ? Kinds.irType(k) : -1, p);
                paramVars[p].cls = paramCls[p];
            }
        }
    }

    void push(Ex e) {
        stack.add(new Slot(e, out.size(), Math.min(curInsn, popMin)));
    }

    Ex pop() {
        return popSlot().ex();
    }

    private Slot popSlot() {
        Slot s = stack.remove(stack.size() - 1);
        popMin = Math.min(popMin, s.start());
        return s;
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
        int at = out.size();
        List<Ex> ready = readyValues();
        Map<Ex, Var> spilled = spillOpen(true, (Var) null);
        out.add(s);
        markReady(at, ready);
        return spilled;
    }

    /** Готовые значения стека (их узлы {@code frontend-java} уже создал): всё, кроме массивов. */
    private List<Ex> readyValues() {
        List<Ex> r = new ArrayList<>();
        for (Slot s : stack) {
            Ex e = s.ex();
            if (e.kind != Kinds.K_ARRAY && e.op != Ex.NEW_ARRAY && e.op != Ex.CMP3 && e.op != Ex.NEW_OBJ) {
                r.add(e);
            }
        }
        return r;
    }

    private void markReady(int at, List<Ex> ready) {
        if (!ready.isEmpty() && out.size() > at && out.get(at).ready == null) {
            out.get(at).ready = ready;
        }
    }

    /**
     * @param operand спилл перед стейтментом, который выдало вычисление следующего операнда ({@code operandsFrom} в
     *                {@code frontend-java}): эмиттер создаёт его после этого стейтмента
     * @param old     переменная {@code x++}: её сохранённое значение - старое значение постфиксного шага
     *                ({@code aux = 2}, эмиттер создаёт его чтение сразу за записью {@code x})
     */
    private Map<Ex, Var> spillOpen(boolean operand, Var old) {
        return spillOpen(operand, e -> old != null && e.op == Ex.LOCAL_GET && e.var == old);
    }

    /**
     * Сохраняет открытые значения стека во временные локали.
     *
     * @param operand стейтмент - операнд выражения (временная локаль объявляется после него, {@code aux = 1})
     * @param isOld   значение - старое значение цели постфиксного {@code ++/--} ({@code aux = 2})
     * @return спилленные значения → их временные локали
     */
    private Map<Ex, Var> spillOpen(boolean operand, java.util.function.Predicate<Ex> isOld) {
        Map<Ex, Var> spilled = new IdentityHashMap<>();
        for (int i = 0; i < stack.size(); i++) {
            Slot s = stack.get(i);
            Ex e = s.ex();
            if (!e.open || e.kind == Kinds.K_ARRAY) {
                continue;
            }
            Var t = spilled.get(e);
            if (t == null) {
                t = temp(e);
                // aux = 1: временная локаль спилла (эмиттер создаёт её после стейтмента, который её потребовал)
                int aux = isOld.test(e) ? 2 : operand ? 1 : 0;
                out.add(new St(St.DECL, t, e, null, null, aux, false, line, group));
                spilled.put(e, t);
            }
            stack.set(i, new Slot(Ex.localGet(t), s.mark(), s.start()));
        }
        return spilled;
    }

    static Var temp(int kind) {
        return new Var(null, kind, -1, -1, Kinds.irType(kind), -1);
    }

    /** Временная локаль для значения {@code like} (агрегат, указатель или скаляр). */
    static Var temp(Ex like) {
        if (like.kind != Kinds.K_AGG && like.kind != Kinds.K_PTR) {
            return temp(like.kind);
        }
        Var v = new Var(null, like.kind, like.elemKind, like.space, -1, -1);
        v.cls = like.cls;
        return v;
    }

    /** Нулевое значение переменной (объявление без инициализатора). */
    static Ex zeroOf(Var v) {
        return v.kind == Kinds.K_AGG ? Ex.zeroAgg(v.cls) : zero(v.kind);
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
        Ex bv = kind == Kinds.K_BOOLEAN ? Conds.boolValue(v) : null;
        if (bv != null) {
            return bv; // boolean, материализованный ромбом c ? 1 : 0
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
    static Ex promoteOperand(Ex x, Ex other, int pk) {
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
                 Opcodes.SALOAD, Opcodes.AALOAD -> {
                Ex idx = pop();
                Ex arr = pop();
                push(Ex.load(arr, index(idx)));
            }
            case Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE, Opcodes.BASTORE, Opcodes.CASTORE,
                 Opcodes.SASTORE, Opcodes.AASTORE -> arrayStore();
            case Opcodes.GETFIELD -> getField((FieldInsnNode) in);
            case Opcodes.GETSTATIC -> {
                FieldInsnNode f = (FieldInsnNode) in;
                push(Bindings.constantOf(f.desc, bindings.constant(f.owner, f.name),
                        f.owner.replace('/', '.') + "." + f.name));
            }
            case Opcodes.PUTFIELD -> putField((FieldInsnNode) in);
            case Opcodes.NEW -> push(Ex.newObject(((TypeInsnNode) in).desc));
            case Opcodes.INVOKESPECIAL -> construct((MethodInsnNode) in);
            case Opcodes.INVOKEVIRTUAL -> apiMethod((MethodInsnNode) in, false);
            case Opcodes.ARRAYLENGTH -> push(Ex.arrayLen(pop()));
            case Opcodes.NEWARRAY -> newArray((IntInsnNode) in);
            case Opcodes.POP -> pop();
            case Opcodes.POP2 -> popWords(2);
            case Opcodes.DUP, Opcodes.DUP2 -> {
                AbstractInsnNode nx = nextOpcode(in);
                boolean wide = !stack.isEmpty() && stack.get(stack.size() - 1).words() == 2;
                if (nx instanceof VarInsnNode vs && vs.getOpcode() >= Opcodes.ISTORE && vs.getOpcode() <= Opcodes.DSTORE
                        && wide == (op == Opcodes.DUP2)) {
                    // x = (y = v): значение присваивания - чтение y после записи (assign в frontend-java)
                    store(vs);
                    if (lastStored != null) {
                        push(Ex.localGet(lastStored));
                    }
                    return nx;
                }
                if (op == Opcodes.DUP) {
                    dup(1, 0);
                } else {
                    dup(2, 0);
                }
            }
            case Opcodes.DUP_X1 -> dup(1, 1);
            case Opcodes.DUP_X2 -> dup(1, 2);

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
            case Opcodes.I2B -> push(narrow(Kinds.K_BYTE, pop()));
            case Opcodes.I2S -> push(narrow(Kinds.K_SHORT, pop()));
            case Opcodes.I2C -> push(narrow(Kinds.K_CHAR, pop()));
            case Opcodes.LCMP, Opcodes.FCMPL, Opcodes.FCMPG, Opcodes.DCMPL, Opcodes.DCMPG -> {
                Ex r = pop();
                Ex l = pop();
                int variant = op == Opcodes.LCMP ? Conds.CMP_EXACT
                        : op == Opcodes.FCMPL || op == Opcodes.DCMPL ? Conds.CMP_NAN_LESS : Conds.CMP_NAN_GREATER;
                push(Ex.cmp3(variant, l, r));
            }
            case Opcodes.INVOKESTATIC -> {
                MethodInsnNode mi = (MethodInsnNode) in;
                if (Aggs.vector(mi.owner) != null || Aggs.pointer(mi.owner) != null) {
                    apiMethod(mi, true);
                    return in;
                }
                if (mi.owner.equals(GPU)) {
                    return builtin(mi);
                }
                JdkIntrinsics.Intrinsic jdk = JdkIntrinsics.find(mi.owner, mi.name, mi.desc);
                return jdk != null ? jdkIntrinsic(mi, jdk) : deviceCall(mi);
            }
            case Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN -> {
                Ex r = pop();
                requireFresh(r, retKind, retCls);
                add(st(St.RETURN, null, coerce(r, retKind)));
            }
            case Opcodes.RETURN -> {
                if (!last) {
                    add(st(St.RETURN, null, null));
                }
            }
            default -> throw new IllegalStateException("opcode " + Insns.name(op) + " passed the subset check");
        }
        return in;
    }

    /**
     * Сужение до {@code byte/short/char}. {@code (byte) f} у вещественного javac пишет как {@code f2i; i2b} (JLS 5.1.3
     * так и определяет это приведение), а {@code frontend-java} строит одно {@code convert(i8, f)} с тем же смыслом;
     * так же {@code (short) l} у {@code long} - {@code l2i; i2s} (младшие биты те же).
     */
    private static Ex narrow(int kind, Ex v) {
        if (v.op == Ex.CONVERT && !v.implicit && v.kind == Kinds.K_INT
                && (v.kids[0].kind == Kinds.K_FLOAT || v.kids[0].kind == Kinds.K_DOUBLE
                || v.kids[0].kind == Kinds.K_LONG)) {
            return Ex.convert(kind, v.kids[0], false);
        }
        return Ex.convert(kind, v, false);
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
        lastStored = null;
        Ex v = pop();
        Var var = varForStore(in.var, in);
        if (var.kind == Kinds.K_ARRAY) {
            if (v.op == Ex.CONST_TABLE) {
                report("GPU0116", line, "table " + v.target + " is indexed directly, not through the variable " + var.name);
                return;
            }
            if (v.op != Ex.NEW_ARRAY) {
                report("GPU0116", line, "array " + var.name + " cannot be reassigned");
                return;
            }
            add(new St(St.ARRAY_DECL, var, null, null, null, v.aux, false, line, group));
            return;
        }
        if (var.owned || var.kind == Kinds.K_PTR && var.paramIndex >= 0 && paramRef[var.paramIndex]) {
            if (v.op != Ex.NEW_PTR || var.paramIndex >= 0 || declaringEntry(in.var, in) == null) {
                report("GPU0198", line, var.name, "a reference is not reassigned: change the value via .value");
                return;
            }
            v = v.kids[0];
        } else if (var.kind == Kinds.K_PTR) {
            if (v.op == Ex.NEW_PTR) {
                report("GPU0198", line, var.name, "an explicit pointer is created with of(array, offset), not a constructor");
                return;
            }
            if (stackHolds(v)) {
                report("GPU0198", line, var.name, "assignment to a pointer is allowed only as a standalone statement");
                return;
            }
        }
        requireFresh(v, var.kind, var.cls);
        // объявление ставит Scopes после структурирования (17.10): здесь любая запись - local_set
        St s = st(St.SET, var, var.owned ? v : coerce(v, var.kind));
        s.lvt = var.paramIndex < 0 ? declaringEntry(in.var, in) : null;
        add(s);
        lastStored = var;
    }

    /** Значение ещё лежит на стеке (присваивание-выражение {@code a = (b = v)}: {@code dup} перед записью). */
    private boolean stackHolds(Ex v) {
        for (Slot sl : stack) {
            if (sl.ex() == v) {
                return true;
            }
        }
        return false;
    }

    /** {@code GPU0193}: значение struct, которое сохраняется (в локаль, элемент, поле, возврат), должно быть свежим. */
    private void requireFresh(Ex v, int kind, String cls) {
        if (kind == Kinds.K_AGG && cls != null && aggs.isStruct(cls) && !v.isFresh()) {
            report("GPU0193", line, Type.getObjectType(cls).getClassName());
        }
    }

    private void iinc(IincInsnNode in) {
        int p = paramAtSlot(in.var);
        Var var = p >= 0 ? paramVars[p] : varOf(lvtAt(in.var, in, false));
        int at = out.size();
        List<Ex> ready = readyValues();
        Map<Ex, Var> spilled = spillOpen(true, var);
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
        markReady(at, ready);
    }

    private void arrayStore() {
        Ex v = pop();
        Ex idx = pop();
        Slot arr = popSlot();
        if (arr.ex().op == Ex.CONST_TABLE) {
            report("GPU0116", line, "table " + arr.ex().target + " is read-only (Bindings.table)");
            return;
        }
        requireFresh(v, arr.ex().elemKind, arr.ex().cls);
        Ex value = coerce(v, arr.ex().elemKind);
        // isPure в frontend-java: любой вызов, кроме чистого builtin, делает порядок вычисления наблюдаемым
        boolean ordered = out.size() > arr.mark() || impureCall(v) || impureCall(idx);
        add(new St(St.STORE, null, arr.ex(), index(idx), value, 0, ordered, line, group));
    }

    private static AbstractInsnNode nextOpcode(AbstractInsnNode in) {
        AbstractInsnNode n = in.getNext();
        while (n != null && n.getOpcode() < 0) {
            n = n.getNext();
        }
        return n;
    }

    private static boolean impureCall(Ex e) {
        if (e.op == Ex.CALL || e.op == Ex.INTRINSIC && !Builtins.has(e.aux, Builtins.PURE)) {
            return true;
        }
        for (Ex k : e.kids) {
            if (impureCall(k)) {
                return true;
            }
        }
        return false;
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

    /**
     * Вызов {@code @DeviceFunction} (как {@code call} в {@code frontend-java}): аргументы приводятся к типам параметров,
     * у массива проверяется адресное пространство ({@code GPU0121}); вызов-стейтмент - {@code expr_stmt}, результат
     * эффектной функции - временная локаль, иначе вызов остаётся в дереве выражения.
     */
    private AbstractInsnNode deviceCall(MethodInsnNode mi) {
        Callee c = callees.apply(mi);
        if (c == null) {
            throw new IllegalStateException("call of " + mi.owner + "." + mi.name + " is not resolved");
        }
        int n = c.paramKinds().length;
        Ex[] args = new Ex[n];
        int start = curInsn;
        for (int i = n - 1; i >= 0; i--) {
            Slot s = popSlot();
            args[i] = s.ex();
            start = Math.min(start, s.start());
        }
        for (int i = 0; i < n; i++) {
            if (c.paramKinds()[i] == Kinds.K_ARRAY) {
                if (args[i].space != c.paramSpaces()[i]) {
                    report("GPU0121", line, c.paramNames()[i], spaceName(c.paramSpaces()[i]), spaceName(args[i].space));
                }
            } else {
                args[i] = coerce(args[i], c.paramKinds()[i]);
            }
        }
        Ex call = Ex.call(c.key(), c.retKind(), insns.indexOf(mi), start, args).typed(c.retCls());
        AbstractInsnNode next = mi.getNext();
        while (next != null && next.getOpcode() < 0) {
            next = next.getNext();
        }
        if (c.retKind() == Kinds.K_VOID) {
            add(st(St.EXPR, null, call));
            return mi;
        }
        if (next != null && (next.getOpcode() == Opcodes.POP || next.getOpcode() == Opcodes.POP2)) {
            add(st(St.EXPR, null, call));
            return next;
        }
        if (c.effects()) {
            Var t = temp(call);
            add(st(St.DECL, t, call));
            push(Ex.localGet(t).asFresh());
            return mi;
        }
        push(call);
        return mi;
    }

    /** Имя адресного пространства для сообщений (как {@code Ann.spaceName} в {@code frontend-java}). */
    static String spaceName(int space) {
        return switch (space) {
            case com.sixik.gpu.ir.AddressSpace.GLOBAL -> "global";
            case com.sixik.gpu.ir.AddressSpace.LOCAL -> "local";
            case com.sixik.gpu.ir.AddressSpace.CONSTANT -> "constant";
            default -> "private";
        };
    }

    /**
     * {@code invokestatic} метода JDK из таблицы {@link JdkIntrinsics} (18.2-S6): один builtin с флагом {@code jdk}
     * (с дописанной константой {@code ±∞} у {@code nextUp/nextDown}) либо {@code bitcast}; как в {@code frontend-java}.
     */
    private AbstractInsnNode jdkIntrinsic(MethodInsnNode mi, JdkIntrinsics.Intrinsic jdk) {
        Type[] at = Type.getArgumentTypes(mi.desc);
        Ex[] args = new Ex[at.length + (jdk.extra() != JdkIntrinsics.EXTRA_NONE ? 1 : 0)];
        boolean open = false;
        for (int i = at.length - 1; i >= 0; i--) {
            args[i] = coerce(pop(), Kinds.ofDescriptor(at[i].getDescriptor()));
            open |= args[i].open;
        }
        int rk = Kinds.ofDescriptor(Type.getReturnType(mi.desc).getDescriptor());
        if (jdk.isBitcast()) {
            push(Ex.unary(Ex.BITCAST, rk, args[0]));
            return mi;
        }
        if (jdk.extra() != JdkIntrinsics.EXTRA_NONE) {
            boolean positive = jdk.extra() == JdkIntrinsics.EXTRA_POSITIVE_INFINITY;
            int k = args[0].kind;
            args[at.length] = Ex.constant(k, k == Kinds.K_FLOAT
                    ? Float.floatToRawIntBits(positive ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY)
                    : Double.doubleToRawLongBits(positive ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY));
        }
        push(Ex.intrinsic(jdk.builtin(), rk, open, args));
        return mi;
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
            if (k == Kinds.K_OTHER) {
                anyOpen |= args[i].open;
            } else if (k != Kinds.K_ARRAY) {
                args[i] = coerce(args[i], k);
                anyOpen |= args[i].open;
            }
        }
        if (Builtins.has(id, Builtins.SYNC)) {
            add(new St(St.BARRIER, null, null, null, null, mi.name.equals("barrierGlobal") ? 3 : 1, false, line, group));
            return mi;
        }
        Aggs.Shape rs = aggs.shape(Type.getReturnType(mi.desc).getDescriptor());
        int rk = rs.kind();
        boolean effect = Builtins.has(id, Builtins.EFFECT);
        Ex call = Ex.intrinsic(id, rk, anyOpen || Builtins.has(id, Builtins.READS), args).typed(rs.cls());
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
        Var t = temp(call);
        add(st(St.DECL, t, call));
        push(Ex.localGet(t));
        return mi;
    }

    // =============================================================================================
    // Объекты API: векторы, struct, указатели (шаг A5, 17.8)
    // =============================================================================================

    /** @return класс - вектор, {@code @Struct} или указатель */
    private boolean api(String owner) {
        return aggs.isAggregate(owner) || Aggs.pointer(owner) != null;
    }

    private void getField(FieldInsnNode f) {
        Ex recv = pop();
        Aggs.Ptr p = Aggs.pointer(f.owner);
        if (p != null) {
            push(Ex.loadPtr(recv));
            return;
        }
        Aggs.Vec v = Aggs.vector(f.owner);
        if (v != null) {
            push(Ex.agg(Ex.EXTRACT, v.scalarKind(), null, Aggs.laneIndex(v, f.name), recv));
            return;
        }
        Aggs.Shape s = aggs.shape(f.desc);
        push(Ex.agg(Ex.FIELD_GET, s.kind(), s.cls(), aggs.fieldIndex(f.owner, f.name), recv));
    }

    /**
     * {@code putfield}: запись через указатель ({@code p.value = v}) или в поле struct - локали или элемента массива
     * ({@code fieldRef} в frontend-java: цепочка полей пересобирается {@code field_set} от корня).
     */
    private void putField(FieldInsnNode f) {
        Ex v = pop();
        Ex recv = pop();
        St s;
        Ex reread;
        if (Aggs.pointer(f.owner) != null) {
            s = new St(St.PSTORE, null, recv, null, null, 0, false, line, group);
            reread = Ex.loadPtr(recv);
        } else if (Aggs.vector(f.owner) != null) {
            report("GPU0194", line, f.name, "a field can be changed only on a struct (vectors are immutable)");
            return;
        } else {
            java.util.ArrayDeque<Integer> path = new java.util.ArrayDeque<>();
            path.add(aggs.fieldIndex(f.owner, f.name));
            Ex root = recv;
            while (root.op == Ex.FIELD_GET) {
                path.addFirst(root.aux);
                root = root.kids[0];
            }
            boolean local = root.op == Ex.LOCAL_GET && !root.var.temp() && root.var.paramIndex < 0;
            if (!local && root.op != Ex.LOAD) {
                report("GPU0194", line, f.name, root.op == Ex.PARAM || root.op == Ex.LOCAL_GET
                        ? "a struct parameter is read-only" : "the write target must be a local variable or an array element");
                return;
            }
            s = new St(St.FSTORE, null, root, null, null, 0, false, line, group);
            s.values = path.stream().mapToInt(Integer::intValue).toArray();
            Aggs.Shape fs = aggs.shape(f.desc);
            requireFresh(v, fs.kind(), fs.cls());
            reread = Ex.agg(Ex.FIELD_GET, fs.kind(), fs.cls(), aggs.fieldIndex(f.owner, f.name), recv);
        }
        int fk = Aggs.pointer(f.owner) != null ? Aggs.pointer(f.owner).elemKind() : aggs.shape(f.desc).kind();
        // значение присваивания (++p.value, x = (s.f = v)): чтение цели после записи, как Target.read()
        List<Integer> again = new ArrayList<>();
        for (int i = 0; i < stack.size(); i++) {
            if (stack.get(i).ex() == v) {
                again.add(i);
            }
        }
        for (int i = again.size() - 1; i >= 0; i--) {
            stack.remove((int) again.get(i));
        }
        int at = out.size();
        List<Ex> ready = readyValues();
        Map<Ex, Var> spilled = spillOpen(true, e -> contains(v, e));
        Ex value = coerce(substitute(v, spilled), fk);
        St done = new St(s.kind, null, s.a, value, null, s.aux, false, line, group);
        done.values = s.values;
        out.add(done);
        markReady(at, ready);
        for (int i : again) {
            stack.add(i, new Slot(reread, out.size(), curInsn));
        }
    }

    private static boolean contains(Ex tree, Ex e) {
        if (tree == e) {
            return true;
        }
        for (Ex k : tree.kids) {
            if (contains(k, e)) {
                return true;
            }
        }
        return false;
    }

    /** Подставляет чтения временных локалей вместо спилленных подвыражений. */
    private static Ex substitute(Ex e, Map<Ex, Var> spilled) {
        Var t = spilled.get(e);
        if (t != null) {
            return Ex.localGet(t);
        }
        Ex[] kids = null;
        for (int i = 0; i < e.kids.length; i++) {
            Ex k = substitute(e.kids[i], spilled);
            if (k != e.kids[i]) {
                if (kids == null) {
                    kids = e.kids.clone();
                }
                kids[i] = k;
            }
        }
        return kids == null ? e : e.withKids(kids);
    }

    /** {@code invokespecial <init>}: значение заменяет все копии метки {@code new} на стеке (17.8). */
    private void construct(MethodInsnNode mi) {
        Type[] at = Type.getArgumentTypes(mi.desc);
        Ex[] args = new Ex[at.length];
        for (int i = at.length - 1; i >= 0; i--) {
            args[i] = pop();
        }
        Ex marker = pop();
        String cls = mi.owner;
        Ex value;
        Aggs.Ptr p = Aggs.pointer(cls);
        Aggs.Vec vec = Aggs.vector(cls);
        if (p != null) {
            if (!p.ref()) {
                report("GPU0198", line, simple(cls), "an explicit pointer is created with of(array, offset), not a constructor");
                return;
            }
            value = Ex.newPtr(args.length == 0 ? zero(p.elemKind()) : coerce(args[0], p.elemKind()), p.elemKind());
        } else if (vec != null) {
            if (args.length != vec.lanes()) {
                report("GPU0192", line, simple(cls), "a vector is created with a constructor over all components");
                return;
            }
            for (int i = 0; i < args.length; i++) {
                args[i] = coerce(args[i], vec.scalarKind());
            }
            value = Ex.agg(Ex.BUILD, Kinds.K_AGG, cls, 0, args);
        } else {
            List<org.objectweb.asm.tree.FieldNode> fs = aggs.fields(cls);
            if (args.length == 0 || args.length != fs.size()) {
                report("GPU0192", line, simple(cls), "a no-arg constructor would leave object fields null; list all fields");
                return;
            }
            for (int i = 0; i < args.length; i++) {
                Aggs.Shape fsh = aggs.shape(fs.get(i).desc);
                // поле-struct хранит ссылку, поэтому и аргумент обязан быть свежим значением
                requireFresh(args[i], fsh.kind(), fsh.cls());
                args[i] = coerce(args[i], fsh.kind());
            }
            value = Ex.agg(Ex.BUILD, Kinds.K_AGG, cls, 0, args);
        }
        for (int i = 0; i < stack.size(); i++) {
            Slot sl = stack.get(i);
            if (sl.ex() == marker) {
                stack.set(i, new Slot(value, sl.mark(), sl.start()));
            }
        }
    }

    private static String simple(String internal) {
        String n = internal.substring(internal.lastIndexOf('/') + 1);
        return n.substring(n.lastIndexOf('$') + 1);
    }

    private static final Map<String, Integer> VECTOR_BINARY = Map.ofEntries(
            Map.entry("add", Op.ADD), Map.entry("sub", Op.SUB), Map.entry("mul", Op.MUL), Map.entry("div", Op.DIV),
            Map.entry("rem", Op.REM), Map.entry("and", Op.AND), Map.entry("or", Op.OR), Map.entry("xor", Op.XOR),
            Map.entry("shl", Op.SHL), Map.entry("shr", Op.SHR), Map.entry("ushr", Op.USHR));

    /** Методы векторов, struct ({@code copy}), указателей и резидентных массивов (17.11). */
    private void apiMethod(MethodInsnNode mi, boolean isStatic) {
        Type[] at = Type.getArgumentTypes(mi.desc);
        Ex[] args = new Ex[at.length];
        for (int i = at.length - 1; i >= 0; i--) {
            args[i] = pop();
        }
        Ex recv = isStatic ? null : pop();
        String name = mi.name;
        Aggs.Vec vec = Aggs.vector(mi.owner);
        Aggs.Ptr ptr = Aggs.pointer(mi.owner);
        int res = Aggs.resident(mi.owner);
        String retCls = aggs.shape(Type.getReturnType(mi.desc).getDescriptor()).cls();
        if (vec != null) {
            int lk = vec.scalarKind();
            if (name.equals("splat")) {
                push(Ex.agg(Ex.SPLAT, Kinds.K_AGG, mi.owner, 0, coerce(args[0], lk)));
            } else if (name.startsWith("with") && args.length == 1) {
                String suffix = name.substring(4);
                String lane = Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
                push(Ex.agg(Ex.INSERT, Kinds.K_AGG, mi.owner, Aggs.laneIndex(vec, lane), recv, coerce(args[0], lk)));
            } else if (name.equals("shuffle")) {
                int[] lanes = new int[args.length];
                for (int i = 0; i < lanes.length; i++) {
                    if (args[i].op != Ex.CONST || args[i].bits < 0 || args[i].bits >= vec.lanes()) {
                        report("GPU0195", line, vec.lanes() - 1);
                        lanes[i] = 0;
                    } else {
                        lanes[i] = (int) args[i].bits;
                    }
                }
                Ex e = Ex.agg(Ex.SHUFFLE, Kinds.K_AGG, retCls, 0, recv);
                e.lanes = lanes;
                push(e);
            } else if (name.equals("neg") || name.equals("not")) {
                push(Ex.agg(name.equals("neg") ? Ex.NEG : Ex.NOT, Kinds.K_AGG, mi.owner, 0, recv));
            } else if (VECTOR_BINARY.containsKey(name) && args.length == 1) {
                Ex other = args[0].kind == Kinds.K_AGG ? args[0]
                        : Ex.agg(Ex.SPLAT, Kinds.K_AGG, mi.owner, 0, coerce(args[0], lk));
                Ex e = Ex.binary(VECTOR_BINARY.get(name), Kinds.K_AGG, recv, other);
                push(e.typed(mi.owner));
            } else {
                report("GPU0112", line, simple(mi.owner) + "." + name);
                push(recv);
            }
            return;
        }
        if (ptr != null) {
            switch (name) {
                case "of", "plus" -> {
                    Ex base = isStatic ? args[0] : recv;
                    Ex off = isStatic ? args[1] : args[0];
                    if (isStatic && base.space != ptr.space()) {
                        report("GPU0199", line, simple(mi.owner), spaceName(ptr.space()), spaceName(base.space));
                    }
                    push(Ex.elemPtr(base, index(off), ptr.elemKind(), ptr.space(), true));
                }
                case "get" -> push(Ex.loadPtr(Ex.elemPtr(recv, index(args[0]), ptr.elemKind(), ptr.space(), true)));
                case "set" -> pointerSet(Ex.elemPtr(recv, index(args[0]), ptr.elemKind(), ptr.space(), true),
                        args[1], ptr.elemKind());
                default -> report("GPU0112", line, simple(mi.owner) + "." + name);
            }
            return;
        }
        if (res >= 0) {
            switch (name) {
                case "length" -> push(Ex.arrayLen(recv));
                case "get" -> push(Ex.load(recv, index(args[0])));
                case "set" -> pointerSet(Ex.elemPtr(recv, index(args[0]), res, recv.space, false), args[1], res);
                default -> report("GPU0112", line, simple(mi.owner) + "." + name);
            }
            return;
        }
        // struct: copy() - то же значение (агрегаты передаются по значению)
        if (name.equals("copy") && args.length == 0) {
            push(recv.asFresh());
            return;
        }
        report("GPU0112", line, simple(mi.owner) + "." + name);
        if (Type.getReturnType(mi.desc).getSort() != Type.VOID) {
            push(recv);
        }
    }

    /** {@code p.set(i, v)}: операнды по порядку, затем адрес и запись ({@code pointerCall} в frontend-java). */
    private void pointerSet(Ex ptr, Ex v, int elemKind) {
        add(new St(St.PSTORE, null, ptr, coerce(v, elemKind), null, 1, false, line, group));
    }
}
