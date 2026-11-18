package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.frontend.common.KernelAttributes;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Types;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Сборка модуля ядра (REWORK_ARCHITECTURE.md, 17.11; логика {@code ModuleCompiler} из {@code frontend-java} на
 * индексах ASM): проверка метода, разрешение вызовов {@code @DeviceFunction} через {@link ClassSource} (тот же
 * каталог, другие классы, jar), рекурсия ({@code GPU0105}), варианты {@code fallback} ({@code GPU0010/0011/0014}),
 * порядок функций и эмиссия.
 *
 * <p><b>Состав и порядок модуля</b> - как у {@code frontend-java}: функции от листьев вызовов к корню, ядро последним,
 * варианты {@code fallback} - функции вида {@code KERNEL}. Порядок обхода вызовов - порядок исходника (pre-order
 * дерева: внешний вызов раньше вызовов в своих аргументах, шаг {@code for} раньше тела), а не порядок инструкций
 * байткода: от него зависят номера функций и {@code kernelId}.</p>
 *
 * <p>Проверка метода и его диагностики - один раз на экземпляр сборщика (на один вызов {@link AsmFrontend#compile}),
 * сколько бы ядер его ни звали. Стейтменты строятся заново для каждого модуля: эмиттер назначает переменным номера
 * локалей модуля.</p>
 */
final class ModuleBuilder {

    /** Метод - ядро или device-функция модуля. */
    private static final class Unit {
        final String key;
        final String cls;
        final ClassNode cn;
        final MethodNode decl;
        /** Тело; при синтезе LVT (18.2-S7) - копия с синтезированной таблицей. */
        MethodNode body;
        final boolean kernel;
        final boolean reduction;
        /** Проверочный исполнитель: сигнатура, найденные вызовы. */
        MethodLowerer ml;
        int line;
        KernelAttributes attrs;
        /** Имя варианта {@code fallback} либо {@code null}. */
        String fallbackName;
        boolean checked;
        boolean valid;
        /** Конструкция следующего шага (17.15), найденная проверкой: повторяется для каждого ядра. */
        RuntimeException notYet;
        /** Вызов → вызываемая функция (разрешённые вызовы). */
        final Map<MethodInsnNode, Unit> resolved = new IdentityHashMap<>();
        /** Вызываемые в порядке байткода (без повторов). */
        final List<Unit> callees = new ArrayList<>();
        Unit fallback;
        boolean prepared;
        boolean prepareOk;
        boolean effects;
        /** Стейтменты уже строились (повторные построения не выдают диагностик). */
        boolean lowered;
        /**
         * Явные элементы {@code @Kernel}/{@code @WorkGroup} точки входа {@link AsmJit} без аннотации на методе
         * (18.4 {@code Entry}); {@code null} - атрибуты из аннотаций.
         */
        Map<String, Object> entryKernel;
        Map<String, Object> entryWorkGroup;

        Unit(String key, ClassNode cn, MethodNode decl, MethodNode body, boolean kernel, boolean reduction) {
            this.key = key;
            this.cls = cn.name.replace('/', '.');
            this.cn = cn;
            this.decl = decl;
            this.body = body;
            this.kernel = kernel;
            this.reduction = reduction;
        }

        String name() {
            return decl.name;
        }
    }

    /**
     * Построенный модуль.
     *
     * @param module    модуль IR (ещё не проверенный валидатором)
     * @param line      первая строка ядра (позиция диагностик модуля)
     * @param functions индексы функций-ядер в модуле, в порядке запрошенных ядер
     */
    record Built(IrModule module, int line, int[] functions) {
    }

    private static final String JVM_SUFFIX = "$jvm";

    private final ClassSource source;
    private final List<AsmDiagnostic> diags;
    private final Map<String, ClassNode> classes = new HashMap<>();
    /** Агрегаты и указатели API по class-файлам (шаг A5). */
    private final Aggs aggs = new Aggs(this::classNode);
    private final Set<String> missing = new HashSet<>();
    private final Map<String, Unit> units = new HashMap<>();
    private final Set<String> reportedCycles = new HashSet<>();
    /**
     * Классы, сгенерированные программой (18.2-S1, {@link AsmJit}): это целиком GPU-код, поэтому их статические методы
     * вызываются как device-функции и без {@code @DeviceFunction} (контракт 18.3).
     */
    private final Set<String> generated = new HashSet<>();
    /** Методам без LVT она синтезируется ({@link LvtSynth}, режим {@code SYNTHESIZE} у {@link AsmJit}). */
    private boolean synthesizeLvt;
    private Bindings bindings = Bindings.none();
    private Links links = Links.none();

    ModuleBuilder(ClassSource source, List<AsmDiagnostic> diags) {
        this.source = source;
        this.diags = diags;
    }

    /** Класс, уже прочитанный вызывающим ({@code compile} перечисляет ядра его методов). */
    void add(ClassNode cn) {
        classes.put(cn.name, cn);
    }

    /**
     * Класс, сгенерированный программой: деревом, без {@code ClassReader} (18.2-S1).
     *
     * @param cn узел класса
     */
    void addGenerated(ClassNode cn) {
        classes.put(cn.name, cn);
        generated.add(cn.name);
    }

    /**
     * Привязки полей и таблица связей {@link AsmJit} (18.2-S2, S3). Задаются до первой сборки.
     *
     * @param bindings привязки
     * @param links    связи
     */
    void link(Bindings bindings, Links links) {
        this.bindings = bindings;
        this.links = links;
    }

    /** Включает синтез LVT для методов без неё (18.2-S7); иначе такие методы дают {@code GPU0301}. */
    void synthesizeLvt(boolean on) {
        synthesizeLvt = on;
    }

    /**
     * @param internal внутреннее имя класса
     * @return узел класса (сгенерированный, прочитанный ранее или из {@link ClassSource}) либо {@code null}
     */
    ClassNode find(String internal) {
        return classNode(internal);
    }

    /**
     * Объявляет метод без {@code @Kernel} точкой входа с явными атрибутами (18.4 {@code Entry}). Вызывается до
     * {@link #build} этого метода.
     *
     * @param cn        класс
     * @param mn        метод
     * @param kernel    явные элементы {@code @Kernel}
     * @param workGroup явные элементы {@code @WorkGroup} либо {@code null}
     */
    void declareEntry(ClassNode cn, MethodNode mn, Map<String, Object> kernel, Map<String, Object> workGroup) {
        String key = MethodLowerer.key(cn.name, mn.name, mn.desc);
        Unit u = units.get(key);
        if (u == null) {
            u = new Unit(key, cn, mn, bodyOf(cn, mn), true, false);
            units.put(key, u);
        }
        u.entryKernel = kernel;
        u.entryWorkGroup = workGroup;
    }

    /**
     * Строит модуль ядра.
     *
     * @param cn класс ядра
     * @param mn метод с {@code @Kernel} или {@code @Reduction}
     * @return модуль либо {@code null}, если найдены ошибки (диагностики уже выданы)
     */
    Built build(ClassNode cn, MethodNode mn) {
        return buildAll(List.of(unit(cn, mn)));
    }

    /**
     * Строит один модуль с несколькими ядрами (18.2-S4, шаг B4): функции замыканий - по одному разу, вызываемые раньше
     * вызывающих; ядра - в порядке списка, каждое после своих функций. Для одного ядра - тот же модуль, что
     * {@link #build}.
     *
     * @param kernels пары класс → метод ядра (каждое уже построено {@link #build} без ошибок)
     * @return модуль либо {@code null}, если найдены ошибки
     */
    Built buildProgram(List<Map.Entry<ClassNode, MethodNode>> kernels) {
        List<Unit> ks = new ArrayList<>();
        for (Map.Entry<ClassNode, MethodNode> e : kernels) {
            ks.add(unit(e.getKey(), e.getValue()));
        }
        return buildAll(ks);
    }

    private Built buildAll(List<Unit> ks) {
        for (Unit k : ks) {
            if (!prepare(k, new ArrayDeque<>())) {
                return null;
            }
        }
        Set<Unit> all = new LinkedHashSet<>();
        for (Unit k : ks) {
            all.addAll(closure(k));
        }
        // стейтменты всех функций замыкания (порядок вызовов исходника берётся из них)
        Map<Unit, List<St>> bodies = new HashMap<>();
        Map<Unit, MethodLowerer> lowerers = new HashMap<>();
        int before = errorCount();
        for (Unit u : all) {
            MethodLowerer ml = new MethodLowerer(u.cls, u.body, u.name(), u.lowered ? new ArrayList<>() : diags, aggs);
            ml.bindings = bindings;
            ml.callees = mi -> callee(u, mi);
            bodies.put(u, ml.execute());
            lowerers.put(u, ml);
            u.lowered = true;
        }
        if (errorCount() != before) {
            return null;
        }
        List<Unit> order = new ArrayList<>();
        for (Unit k : ks) {
            order(k, bodies, order);
        }

        IrBuilder b = new IrBuilder();
        Aggs.IrTypes types = aggs.new IrTypes(b);
        Map<String, Integer> fns = new HashMap<>();
        for (Unit u : order) {
            MethodLowerer ml = lowerers.get(u);
            int n = ml.paramKinds.length;
            int[] ptypes = new int[n];
            for (int i = 0; i < n; i++) {
                ptypes[i] = types.value(ml.paramKinds[i], ml.paramElems[i], ml.paramSpaces[i], ml.paramCls[i]);
            }
            int ret = types.value(ml.retKind, -1, -1, ml.retCls);
            boolean kernelFn = u.kernel;
            int f = b.beginFunction(kernelFn ? IrModule.KERNEL : IrModule.DEVICE, ret, ptypes);
            if (u.attrs != null) {
                u.attrs.apply(b, u.fallback == null ? -1 : fns.get(u.fallback.key));
            }
            if (!u.kernel && !u.reduction) {
                b.inlinePolicy(inlinePolicy(u.decl));
            }
            b.nameFunction(u.name());
            b.nameParams(ml.paramNames);
            List<Integer> top = new Emitter(b, fns, types, aggs).body(ml.paramVars, ptypes, bodies.get(u));
            int[] root = new int[top.size()];
            for (int i = 0; i < root.length; i++) {
                root[i] = top.get(i);
            }
            b.endFunction(b.block(root));
            fns.put(u.key, f);
        }
        int[] functions = new int[ks.size()];
        for (int i = 0; i < functions.length; i++) {
            functions[i] = fns.get(ks.get(i).key);
        }
        return new Built(b.build(), ks.get(0).line, functions);
    }

    // =============================================================================================
    // Методы
    // =============================================================================================

    private Unit unit(ClassNode cn, MethodNode mn) {
        String key = MethodLowerer.key(cn.name, mn.name, mn.desc);
        Unit u = units.get(key);
        if (u == null) {
            boolean kernel = Annotations.find(mn.invisibleAnnotations, Annotations.KERNEL) != null;
            boolean reduction = Annotations.find(mn.invisibleAnnotations, Annotations.REDUCTION) != null;
            u = new Unit(key, cn, mn, bodyOf(cn, mn), kernel, reduction);
            units.put(key, u);
        }
        return u;
    }

    /**
     * Типы сигнатуры (как {@code SubsetChecker.checkParam} в frontend-java): параметры ({@code GPU0003/0004/0005},
     * указатель у ядра - {@code GPU0197}), возврат device-функции ({@code GPU0006}) и объявления struct, которые
     * встречаются в сигнатуре, локалях и {@code new} ({@code GPU0191}).
     */
    private void signature(Unit u, MethodLowerer ml) {
        MethodNode mn = u.decl;
        Type[] args = Type.getArgumentTypes(mn.desc);
        for (int i = 0; i < args.length; i++) {
            String pname = ml.paramNames[i];
            String shown = args[i].getClassName();
            switch (ml.paramKinds[i]) {
                case Kinds.K_INT, Kinds.K_LONG, Kinds.K_FLOAT, Kinds.K_DOUBLE -> {
                }
                case Kinds.K_AGG -> checkStruct(u, ml.paramCls[i], u.line);
                case Kinds.K_PTR -> {
                    if (u.kernel) {
                        report(u, "GPU0197", u.line, mn.name, shown);
                    }
                }
                case Kinds.K_BOOLEAN -> report(u, "GPU0004", u.line, pname);
                case Kinds.K_ARRAY -> {
                    if (args[i].getSort() != Type.ARRAY) {
                        break; // GpuFloatArray и др.: элемент - всегда допустимый скаляр
                    }
                    int ek = ml.paramElems[i];
                    if (ek == Kinds.K_ARRAY) {
                        report(u, "GPU0005", u.line, pname);
                    } else if (ek == Kinds.K_AGG) {
                        checkStruct(u, ml.paramCls[i], u.line);
                    } else if (ek < Kinds.K_BOOLEAN || ek > Kinds.K_DOUBLE) {
                        report(u, "GPU0003", u.line, args[i].getElementType().getClassName(), pname);
                    }
                }
                default -> report(u, "GPU0003", u.line, shown, pname);
            }
        }
        if (!u.kernel && !u.reduction) {
            int r = ml.retKind;
            if (r == Kinds.K_AGG) {
                checkStruct(u, ml.retCls, u.line);
            } else if (!(r == Kinds.K_VOID || r == Kinds.K_BOOLEAN || r == Kinds.K_INT || r == Kinds.K_LONG
                    || r == Kinds.K_FLOAT || r == Kinds.K_DOUBLE)) {
                report(u, "GPU0006", u.line, Type.getReturnType(mn.desc).getClassName(), mn.name);
            }
        } else if (u.reduction && ml.retKind == Kinds.K_AGG) {
            checkStruct(u, ml.retCls, u.line);
        }
        MethodNode body = u.body;
        if (body.localVariables != null) {
            for (org.objectweb.asm.tree.LocalVariableNode lv : body.localVariables) {
                Aggs.Shape sh = aggs.shape(lv.desc);
                if (sh.kind() == Kinds.K_AGG || sh.kind() == Kinds.K_ARRAY && sh.elemKind() == Kinds.K_AGG) {
                    checkStruct(u, sh.cls(), u.line);
                } else if (sh.kind() == Kinds.K_OTHER && !lv.name.equals("this")) {
                    // локаль неподдерживаемого типа (String, Object, ...): GPU0119, как в frontend-java
                    report(u, "GPU0119", u.line, Type.getType(lv.desc).getClassName());
                }
            }
        }
        int ln = u.line;
        for (org.objectweb.asm.tree.AbstractInsnNode in = body.instructions.getFirst(); in != null; in = in.getNext()) {
            if (in instanceof org.objectweb.asm.tree.LineNumberNode l) {
                ln = l.line;
            } else if (in.getOpcode() == Opcodes.NEW || in.getOpcode() == Opcodes.ANEWARRAY) {
                String c = ((org.objectweb.asm.tree.TypeInsnNode) in).desc;
                if (aggs.isAggregate(c)) {
                    checkStruct(u, c, ln);
                }
            }
        }
    }

    /** Классы struct, чьё объявление уже проверено (один раз на класс, вместе с вложенными). */
    private final Set<String> checkedStructs = new HashSet<>();

    private void checkStruct(Unit u, String cls, int line) {
        if (cls == null || !aggs.isStruct(cls) || !checkedStructs.add(cls)) {
            return;
        }
        String simple = simpleName(cls);
        for (String problem : aggs.problems(cls)) {
            report(u, "GPU0191", line, simple, problem);
        }
        for (org.objectweb.asm.tree.FieldNode f : aggs.fields(cls)) {
            Aggs.Shape sh = aggs.shape(f.desc);
            if (sh.kind() == Kinds.K_AGG) {
                checkStruct(u, sh.cls(), line);
            }
        }
    }

    /** Тело ядра: у сплетённого класса - {@code <имя>$jvm} с тем же дескриптором, иначе сам метод. */
    private static MethodNode bodyOf(ClassNode cn, MethodNode mn) {
        for (MethodNode other : cn.methods) {
            if (other.name.equals(mn.name + JVM_SUFFIX) && other.desc.equals(mn.desc)) {
                return other;
            }
        }
        return mn;
    }

    /** Проверка метода (один раз): сигнатура, атрибуты ядра, подмножество, разрешение вызовов. */
    private void check(Unit u) {
        if (u.checked) {
            if (u.notYet != null) {
                throw u.notYet;
            }
            return;
        }
        u.checked = true;
        int before = errorCount();
        boolean synthFailed = false;
        if (synthesizeLvt && LvtSynth.needed(u.body)) {
            MethodNode b = u.body;
            u.body = LvtSynth.synthesize(u.cn.name, b, what -> report(u, "GPU0307", firstLine(b), u.name(), what));
            synthFailed = errorCount() != before;
        }
        MethodLowerer ml = new MethodLowerer(u.cls, u.body, u.name(), diags, aggs);
        ml.bindings = bindings;
        u.ml = ml;
        u.line = ml.firstLine();
        if (synthFailed) {
            return; // GPU0307 уже выдан; неполная LVT дала бы ещё и GPU0301
        }
        MethodNode mn = u.decl;
        String what = u.kernel ? "Kernel" : u.reduction ? "Reduction" : "DeviceFunction";
        if (u.kernel && u.reduction) {
            report(u, "GPU0017", u.line, mn.name);
            return;
        }
        if ((mn.access & Opcodes.ACC_STATIC) == 0) {
            report(u, "GPU0001", u.line, mn.name, what);
        }
        if ((mn.access & Opcodes.ACC_VARARGS) != 0 || mn.exceptions != null && !mn.exceptions.isEmpty()
                || mn.signature != null && mn.signature.startsWith("<")) {
            report(u, "GPU0007", u.line, mn.name);
        }
        if (u.kernel && ml.retKind != Kinds.K_VOID) {
            report(u, "GPU0002", u.line, mn.name);
        }
        // вектор или @Struct: компоненты - аккумуляторы, их типы проверяет Reductions.analyze (GPU0018)
        if (u.reduction && ml.retKind != Kinds.K_AGG && (ml.retKind < Kinds.K_INT || ml.retKind > Kinds.K_DOUBLE)) {
            report(u, "GPU0016", u.line, mn.name);
        }
        signature(u, ml);
        if (u.kernel) {
            AnnotationNode k = Annotations.find(mn.invisibleAnnotations, Annotations.KERNEL);
            AnnotationNode wg = Annotations.find(mn.invisibleAnnotations, Annotations.WORK_GROUP);
            // точка входа AsmJit без аннотации: элементы заданы явно в Entry (18.4)
            Map<String, Object> explicit = k != null ? Annotations.explicitValues(k)
                    : u.entryKernel != null ? u.entryKernel : Map.of();
            Map<String, Object> group = wg != null ? Annotations.explicitValues(wg) : u.entryWorkGroup;
            if (explicit.get("fallback") instanceof String fb && !fb.isEmpty()) {
                u.fallbackName = fb;
            }
            u.attrs = KernelAttributes.parse(mn.name, explicit, group,
                    (code, args) -> report(u, code, u.line, args));
        }
        boolean ok;
        try {
            ok = ml.check();
        } catch (MethodLowerer.NotYet e) {
            u.notYet = e;
            throw e;
        }
        for (MethodLowerer.DeviceCall c : ml.deviceCalls) {
            Unit callee = resolve(u, c);
            if (callee != null) {
                u.resolved.put(c.insn(), callee);
                if (!u.callees.contains(callee)) {
                    u.callees.add(callee);
                }
            }
        }
        u.valid = ok && errorCount() == before && (!u.kernel || u.attrs != null);
    }

    /**
     * Вызываемый метод: класс через {@link ClassSource} ({@code GPU0303}, если его нет), метод в нём или в
     * суперклассах, {@code @DeviceFunction} на нём ({@code GPU0113}).
     */
    private Unit resolve(Unit caller, MethodLowerer.DeviceCall c) {
        MethodInsnNode mi = c.insn();
        String shown = simpleName(mi.owner) + "." + mi.name;
        String owner = mi.owner;
        String name = mi.name;
        Links.Target link = links.target(mi.owner, mi.name, mi.desc);
        if (link != null) {
            String to = simpleName(link.owner()) + "." + link.name();
            if (!link.desc().equals(mi.desc)) {
                report(caller, "GPU0309", c.line(), shown, "the link target " + to + link.desc()
                        + " has another descriptor than " + mi.desc);
                return null;
            }
            owner = link.owner();
            name = link.name();
            shown = to;
        }
        while (owner != null) {
            ClassNode cn = classNode(owner);
            if (cn == null) {
                report(caller, "GPU0303", c.line(), owner.replace('/', '.'), shown);
                return null;
            }
            for (MethodNode m : cn.methods) {
                if (m.name.equals(name) && m.desc.equals(mi.desc)) {
                    if (Annotations.find(m.invisibleAnnotations, Annotations.EXTERN) != null) {
                        report(caller, "GPU0309", c.line(), shown, link == null
                                ? "the @Extern stub has no link (AsmJit Links)" : "the link target is an @Extern stub");
                        return null;
                    }
                    // у сгенерированного класса device-функция - любой статический метод (контракт 18.3)
                    boolean device = Annotations.find(m.invisibleAnnotations, Annotations.DEVICE_FUNCTION) != null
                            || generated.contains(cn.name) && (m.access & Opcodes.ACC_STATIC) != 0;
                    boolean kernel = Annotations.find(m.invisibleAnnotations, Annotations.KERNEL) != null
                            || Annotations.find(m.invisibleAnnotations, Annotations.REDUCTION) != null
                            || isDeclaredEntry(cn, m);
                    if (!device || kernel) {
                        report(caller, "GPU0113", c.line(), shown);
                        return null;
                    }
                    return unit(cn, m);
                }
            }
            owner = cn.superName == null || cn.superName.equals("java/lang/Object") ? null : cn.superName;
        }
        report(caller, "GPU0113", c.line(), shown);
        return null;
    }

    /** Метод объявлен точкой входа {@link #declareEntry} (вызов ядра из ядра - {@code GPU0113}, как с аннотацией). */
    private boolean isDeclaredEntry(ClassNode cn, MethodNode m) {
        Unit u = units.get(MethodLowerer.key(cn.name, m.name, m.desc));
        return u != null && u.kernel && u.entryKernel != null;
    }

    private ClassNode classNode(String internal) {
        ClassNode cn = classes.get(internal);
        if (cn != null || missing.contains(internal)) {
            return cn;
        }
        byte[] bytes;
        try {
            bytes = source.bytes(internal);
        } catch (RuntimeException e) {
            bytes = null;
        }
        if (bytes == null) {
            missing.add(internal);
            return null;
        }
        try {
            cn = new ClassNode();
            new ClassReader(bytes).accept(cn, 0);
        } catch (RuntimeException e) {
            missing.add(internal);
            return null;
        }
        classes.put(internal, cn);
        return cn;
    }

    private static String simpleName(String internal) {
        String s = internal.substring(internal.lastIndexOf('/') + 1);
        return s.substring(s.lastIndexOf('$') + 1);
    }

    // =============================================================================================
    // Замыкание
    // =============================================================================================

    /**
     * Проверка замыкания (порядок байткода): допустимость всех функций, рекурсия, цепочка {@code fallback} и эффекты
     * функций (нужны исполнителю вызывающих: результат эффектной функции - временная локаль).
     */
    private boolean prepare(Unit u, Deque<Unit> stack) {
        if (u.prepared) {
            return u.prepareOk;
        }
        check(u);
        boolean ok = u.valid;
        boolean effects = u.ml != null && u.ml.ownEffects();
        stack.push(u);
        for (MethodLowerer.DeviceCall c : u.ml == null ? List.<MethodLowerer.DeviceCall>of() : u.ml.deviceCalls) {
            Unit callee = u.resolved.get(c.insn());
            if (callee == null) {
                ok = false;
            } else if (stack.contains(callee)) {
                if (reportedCycles.add(u.key + "@" + u.body.instructions.indexOf(c.insn()))) {
                    report(u, "GPU0105", c.line(), chain(stack, callee));
                }
                ok = false;
            } else {
                ok &= prepare(callee, stack);
                effects |= callee.effects;
            }
        }
        if (u.attrs != null && u.fallbackName != null) {
            Unit fb = findFallback(u, u.fallbackName);
            if (fb == null) {
                ok = false;
            } else if (stack.contains(fb)) {
                check(fb);
                if (reportedCycles.add(fb.key)) {
                    report(fb, "GPU0014", fb.line, chain(stack, fb));
                }
                ok = false;
            } else {
                u.fallback = fb;
                ok &= prepare(fb, stack);
            }
        }
        stack.pop();
        u.effects = effects;
        // функция в цикле вызовов проверяется заново из другого входа: итог кэшируется только вне стека
        if (stack.isEmpty() || ok) {
            u.prepared = true;
            u.prepareOk = ok;
        }
        return ok;
    }

    private static String chain(Deque<Unit> stack, Unit closing) {
        List<String> names = new ArrayList<>();
        for (Unit s : stack) {
            names.add(s.name());
        }
        Collections.reverse(names);
        names.add(closing.name());
        return String.join(" -> ", names);
    }

    /** Вариант {@code fallback}: ядро класса с тем же именем и той же сигнатурой ({@code FnSig.sameAs}). */
    private Unit findFallback(Unit k, String name) {
        boolean sameName = false;
        for (MethodNode m : k.cn.methods) {
            if (m.name.equals(name) && Annotations.find(m.invisibleAnnotations, Annotations.KERNEL) != null) {
                sameName = true;
                Unit fb = unit(k.cn, m);
                if (m.desc.equals(k.decl.desc) && sameSpaces(k, fb)) {
                    return fb;
                }
            }
        }
        report(k, sameName ? "GPU0011" : "GPU0010", k.line, name, k.name());
        return null;
    }

    private boolean sameSpaces(Unit a, Unit b) {
        int[] x = a.ml.paramSpaces;
        int[] y = new MethodLowerer(b.cls, b.body, b.name(), new ArrayList<>(), aggs).paramSpaces;
        return java.util.Arrays.equals(x, y);
    }

    /** Все функции замыкания (с вариантами {@code fallback}). */
    private static Set<Unit> closure(Unit k) {
        Set<Unit> r = new LinkedHashSet<>();
        Deque<Unit> work = new ArrayDeque<>();
        work.add(k);
        while (!work.isEmpty()) {
            Unit u = work.poll();
            if (r.add(u)) {
                work.addAll(u.callees);
                if (u.fallback != null) {
                    work.add(u.fallback);
                }
            }
        }
        return r;
    }

    private MethodLowerer.Callee callee(Unit caller, MethodInsnNode mi) {
        Unit u = caller.resolved.get(mi);
        if (u == null) {
            return null;
        }
        MethodLowerer ml = u.ml;
        return new MethodLowerer.Callee(u.key, ml.paramKinds, ml.paramSpaces, ml.paramNames, ml.retKind, ml.retCls, u.effects);
    }

    // =============================================================================================
    // Порядок функций
    // =============================================================================================

    /** Обход {@code ModuleCompiler.collect}: функция попадает в {@code order} после всех, от кого зависит. */
    private static void order(Unit u, Map<Unit, List<St>> bodies, List<Unit> order) {
        if (order.contains(u)) {
            return;
        }
        List<String> keys = new ArrayList<>(callOrder(bodies.get(u)));
        // вызовы в недостижимом коде проверка тоже видит (как SubsetChecker): после достижимых, в порядке байткода
        for (Unit c : u.callees) {
            keys.add(c.key);
        }
        for (String key : keys) {
            for (Unit c : u.callees) {
                if (c.key.equals(key)) {
                    order(c, bodies, order);
                }
            }
        }
        if (u.fallback != null) {
            order(u.fallback, bodies, order);
        }
        if (!order.contains(u)) {
            order.add(u);
        }
    }

    private record CallAt(String key, int group, int start, int at) {
    }

    /**
     * Вызовы функции в порядке исходника, как их собирает {@code SubsetChecker} (обход дерева javac): стейтменты по
     * порядку; у {@code for} - условие, шаг, тело; у {@code do-while} - тело, условие; в выражении внешний вызов
     * раньше своих аргументов. Внутри одного исходного стейтмента ({@link St#group}) порядок восстанавливается по
     * началу вычисления аргументов - так вызов, вынесенный во временную локаль (эффектная функция), встаёт на своё
     * место в дереве.
     */
    static List<String> callOrder(List<St> body) {
        List<CallAt> calls = new ArrayList<>();
        walk(body, calls);
        List<String> r = new ArrayList<>();
        int i = 0;
        while (i < calls.size()) {
            int j = i;
            while (j < calls.size() && calls.get(j).group() == calls.get(i).group()) {
                j++;
            }
            List<CallAt> seg = new ArrayList<>(calls.subList(i, j));
            seg.sort((a, b) -> a.start() != b.start() ? Integer.compare(a.start(), b.start())
                    : Integer.compare(b.at(), a.at()));
            for (CallAt c : seg) {
                r.add(c.key());
            }
            i = j;
        }
        return r;
    }

    private static void walk(List<St> list, List<CallAt> out) {
        if (list == null) {
            return;
        }
        for (St s : list) {
            switch (s.kind) {
                case St.LOOP -> {
                    walk(s.pre, out);
                    walk(s.a, s.group, out);
                    walk(s.update, out);
                    walk(s.body, out);
                }
                case St.DO_WHILE -> {
                    walk(s.body, out);
                    walk(s.a, s.group, out);
                }
                case St.IF -> {
                    walk(s.a, s.group, out);
                    walk(s.body, out);
                    walk(s.alt, out);
                }
                case St.SWITCH -> {
                    walk(s.a, s.group, out);
                    for (List<St> c : s.cases) {
                        walk(c, out);
                    }
                }
                case St.BLOCK -> walk(s.body, out);
                case St.SHORT -> {
                    walk(s.a, s.group, out);
                    walk(s.body, out);
                    walk(s.b, s.group, out);
                }
                default -> {
                    walk(s.a, s.group, out);
                    walk(s.b, s.group, out);
                    walk(s.c, s.group, out);
                }
            }
        }
    }

    private static void walk(Ex e, int group, List<CallAt> out) {
        if (e == null) {
            return;
        }
        if (e.op == Ex.CALL) {
            out.add(new CallAt(e.target, group, (int) e.bits, e.aux));
        }
        for (Ex k : e.kids) {
            walk(k, group, out);
        }
    }

    // =============================================================================================

    /** Политика встраивания device-функции: {@code @DeviceFunction(inline = ...)} (18.2-S4). */
    private static int inlinePolicy(MethodNode m) {
        AnnotationNode a = Annotations.find(m.invisibleAnnotations, Annotations.DEVICE_FUNCTION);
        Object v = a == null ? null : Annotations.explicitValues(a).get("inline");
        // значение перечисления в AnnotationNode - пара {дескриптор, имя}
        String name = v instanceof String[] e ? e[1] : v == null ? "DEFAULT" : v.toString();
        return switch (name) {
            case "ALWAYS" -> IrModule.INLINE_ALWAYS;
            case "NEVER" -> IrModule.INLINE_NEVER;
            default -> IrModule.INLINE_DEFAULT;
        };
    }

    /** @return первая строка метода ({@code LineNumberTable}) либо 0 */
    private static int firstLine(MethodNode m) {
        for (org.objectweb.asm.tree.AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
            if (in instanceof org.objectweb.asm.tree.LineNumberNode l) {
                return l.line;
            }
        }
        return 0;
    }

    private void report(Unit u, String code, int line, Object... args) {
        diags.add(AsmDiagnostic.of(code, u.cls, u.name(), line, args));
    }

    private int errorCount() {
        int n = 0;
        for (AsmDiagnostic d : diags) {
            if (d.error()) {
                n++;
            }
        }
        return n;
    }
}
