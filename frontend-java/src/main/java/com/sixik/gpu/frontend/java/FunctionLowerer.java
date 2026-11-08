package com.sixik.gpu.frontend.java;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Local;
import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ConstantCaseLabelTree;
import com.sun.source.tree.DefaultCaseLabelTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;

/**
 * Построение функции IR из метода Java (REWORK_ARCHITECTURE.md, 5.4, 5.8, 5.12): один экземпляр на функцию.
 *
 * <p>Метод должен был пройти {@link SubsetChecker}. Здесь выполняется <b>нормализация эффектов</b>: IR допускает
 * эффекты только на уровне стейтмента (правило IR-E01) и не позволяет делить узлы, читающие локали и память,
 * между несколькими родителями (IR-E02), поэтому:</p>
 * <ul>
 *   <li>{@code x++}, присваивания внутри выражений, вызовы функций с эффектами и ветки {@code ?:}/{@code &&}/{@code ||}
 *   с такими эффектами «поднимаются» в стейтменты перед использующим их стейтментом, а результат подставляется
 *   временной локалью;</li>
 *   <li>порядок вычисления Java (слева направо) сохраняется: если у более позднего операнда появились поднятые
 *   стейтменты, ранее вычисленные «открытые» операнды (читающие локали и память) сохраняются во временные
 *   локали <i>до</i> этих стейтментов;</li>
 *   <li>составные присваивания и {@code ++/--} разворачиваются в {@code local_set}/{@code store}; для {@code a[i] += x}
 *   создаются два узла {@code elem_ptr} (индекс пересчитывается, если он чистый, иначе сохраняется во временную
 *   локаль).</li>
 * </ul>
 *
 * <p>Арифметика повторяет Java: операнды {@code byte/short/char} повышаются до {@code int}, общий тип бинарной
 * операции выбирается правилами числового повышения, обратное сужение - явный {@code convert}. Вызовы переводятся в
 * {@code intrinsic} (методы {@link Gpu}) либо {@code call}.</p>
 */
final class FunctionLowerer {

    /**
     * Итог построения функции.
     */
    static final class FnInfo {
        /** Индекс функции в модуле. */
        final int index;
        /** Сигнатура. */
        final FnSig sig;
        /** Функция (или её вызываемые) пишет память, использует барьер или эффектный builtin. */
        boolean effects;

        FnInfo(int index, FnSig sig) {
            this.index = index;
            this.sig = sig;
        }
    }

    /**
     * Значение выражения.
     *
     * @param node     id узла IR
     * @param kind     вид Java ({@link JavaTypes})
     * @param elemKind вид элемента для массива, иначе -1
     * @param space    адресное пространство для массива, иначе -1
     * @param open     значение может измениться последующими эффектами (читает изменяемую локаль или память), поэтому
     *                 его надо сохранить во временную локаль до таких эффектов; временные локали пишутся один раз и
     *                 «открытыми» не считаются
     */
    private record V(int node, int kind, int elemKind, int space, boolean open) {
    }

    /** Переменная Java в функции IR: параметр или локаль. */
    private record Var(boolean param, int index, int kind, int elemKind, int space) {
    }

    private static final int MODE_STMT = 0;
    private static final int MODE_NEW = 1;
    private static final int MODE_OLD = 2;

    private final Trees trees;
    private final Reporter rep;
    private final IrBuilder b;
    private final Map<ExecutableElement, FnInfo> fns;
    private final TreePath methodPath;
    private final ExecutableElement method;
    private final KernelAttrs attrs;
    private final int fallbackIndex;
    private final SourcePositions positions;
    private final CompilationUnitTree unit;
    private final LineMap lines;
    private final Map<Element, Var> vars = new HashMap<>();
    private List<Integer> out = new ArrayList<>();
    private boolean effects;
    private FnSig sig;

    private FunctionLowerer(Trees trees, Reporter rep, IrBuilder b, Map<ExecutableElement, FnInfo> fns, TreePath methodPath,
                            ExecutableElement method, KernelAttrs attrs, int fallbackIndex) {
        this.trees = trees;
        this.rep = rep;
        this.b = b;
        this.fns = fns;
        this.methodPath = methodPath;
        this.method = method;
        this.attrs = attrs;
        this.fallbackIndex = fallbackIndex;
        this.positions = trees.getSourcePositions();
        this.unit = methodPath.getCompilationUnit();
        this.lines = unit.getLineMap();
    }

    /**
     * Строит функцию в {@code b} и регистрирует её в {@code fns}.
     *
     * @param trees         API деревьев
     * @param rep           приёмник диагностик (используется для проверки адресных пространств аргументов)
     * @param b             builder модуля
     * @param fns           уже построенные функции модуля (вызываемые должны быть в ней)
     * @param methodPath    путь к {@link MethodTree}
     * @param method        элемент метода
     * @param attrs         атрибуты ядра либо {@code null} для device-функции
     * @param fallbackIndex индекс функции-варианта ядра либо -1
     * @return сведения о построенной функции
     */
    static FnInfo lower(Trees trees, Reporter rep, IrBuilder b, Map<ExecutableElement, FnInfo> fns, TreePath methodPath,
                        ExecutableElement method, KernelAttrs attrs, int fallbackIndex) {
        return new FunctionLowerer(trees, rep, b, fns, methodPath, method, attrs, fallbackIndex).run();
    }

    // =============================================================================================
    // Функция
    // =============================================================================================

    private FnInfo run() {
        MethodTree mt = (MethodTree) methodPath.getLeaf();
        sig = FnSig.of(method);
        int n = sig.paramKinds().length;
        int[] ptypes = new int[n];
        String[] pnames = new String[n];
        for (int i = 0; i < n; i++) {
            ptypes[i] = sig.paramKinds()[i] == JavaTypes.K_ARRAY
                    ? b.array(sig.spaces()[i], JavaTypes.storageType(sig.elemKinds()[i]))
                    : JavaTypes.irType(sig.paramKinds()[i]);
            pnames[i] = method.getParameters().get(i).getSimpleName().toString();
        }
        int ret = sig.retKind() == JavaTypes.K_VOID ? Types.VOID : JavaTypes.irType(sig.retKind());
        boolean kernel = attrs != null;
        int f = b.beginFunction(kernel ? IrModule.KERNEL : IrModule.DEVICE, ret, ptypes);
        FnInfo info = new FnInfo(f, sig);
        fns.put(method, info);
        if (kernel) {
            attrs.apply(b, fallbackIndex);
        }
        b.nameFunction(method.getSimpleName().toString());
        b.nameParams(pnames);

        Set<Element> assigned = assignedParams(new TreePath(methodPath, mt.getBody()));
        for (int i = 0; i < n; i++) {
            VariableElement pe = method.getParameters().get(i);
            int k = sig.paramKinds()[i];
            if (k != JavaTypes.K_ARRAY && assigned.contains(pe)) {
                // параметры в IR неизменяемы: изменяемый параметр Java становится локалью с начальным значением
                int l = b.declareLocal(JavaTypes.irType(k));
                b.nameLocal(l, pnames[i]);
                out.add(b.localDecl(l, b.param(i)));
                vars.put(pe, new Var(false, l, k, -1, -1));
            } else {
                vars.put(pe, new Var(true, i, k, sig.elemKinds()[i], sig.spaces()[i]));
            }
        }
        TreePath bodyPath = new TreePath(methodPath, mt.getBody());
        for (StatementTree s : mt.getBody().getStatements()) {
            stmt(new TreePath(bodyPath, s));
        }
        int root = b.block(toArray(out));
        b.endFunction(root);
        info.effects = effects;
        return info;
    }

    private Set<Element> assignedParams(TreePath body) {
        Set<Element> result = new HashSet<>();
        new TreePathScanner<Void, Void>() {
            private void target(ExpressionTree t) {
                ExpressionTree x = t;
                while (x instanceof ParenthesizedTree pt) {
                    x = pt.getExpression();
                }
                if (x instanceof IdentifierTree) {
                    Element e = trees.getElement(unparen(new TreePath(getCurrentPath(), t)));
                    if (e != null && e.getKind() == javax.lang.model.element.ElementKind.PARAMETER) {
                        result.add(e);
                    }
                }
            }

            @Override
            public Void visitAssignment(AssignmentTree t, Void v) {
                target(t.getVariable());
                return super.visitAssignment(t, v);
            }

            @Override
            public Void visitCompoundAssignment(CompoundAssignmentTree t, Void v) {
                target(t.getVariable());
                return super.visitCompoundAssignment(t, v);
            }

            @Override
            public Void visitUnary(UnaryTree t, Void v) {
                switch (t.getKind()) {
                    case PREFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_INCREMENT, POSTFIX_DECREMENT ->
                            target(t.getExpression());
                    default -> {
                    }
                }
                return super.visitUnary(t, v);
            }
        }.scan(body, null);
        return result;
    }

    // =============================================================================================
    // Вспомогательное
    // =============================================================================================

    private static int[] toArray(List<Integer> list) {
        int[] a = new int[list.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = list.get(i);
        }
        return a;
    }

    private static TreePath child(TreePath parent, Tree t) {
        return new TreePath(parent, t);
    }

    private static TreePath unparen(TreePath p) {
        TreePath x = p;
        while (x.getLeaf() instanceof ParenthesizedTree pt) {
            x = new TreePath(x, pt.getExpression());
        }
        return x;
    }

    private int at(Tree t, int node) {
        mark(t, node);
        return node;
    }

    private void mark(Tree t, int node) {
        long pos = positions.getStartPosition(unit, t);
        if (pos >= 0) {
            b.span(node, (int) lines.getLineNumber(pos), (int) lines.getColumnNumber(pos));
        }
    }

    /** Выполняет {@code r}, собирая все стейтменты, которые она добавила бы в {@link #out}, в отдельный список. */
    private List<Integer> collect(Runnable r) {
        List<Integer> saved = out;
        List<Integer> mine = new ArrayList<>();
        out = mine;
        try {
            r.run();
        } finally {
            out = saved;
        }
        return mine;
    }

    private static V sv(int node, int kind, boolean open) {
        return new V(node, kind, -1, -1, open);
    }

    private int zero(int kind) {
        return switch (kind) {
            case JavaTypes.K_BOOLEAN -> b.constBool(false);
            case JavaTypes.K_INT -> b.constI32(0);
            case JavaTypes.K_LONG -> b.constI64(0);
            case JavaTypes.K_FLOAT -> b.constF32(0f);
            case JavaTypes.K_DOUBLE -> b.constF64(0.0);
            default -> b.constInt(JavaTypes.irType(kind), 0);
        };
    }

    private int one(int kind) {
        return switch (kind) {
            case JavaTypes.K_INT -> b.constI32(1);
            case JavaTypes.K_LONG -> b.constI64(1);
            case JavaTypes.K_FLOAT -> b.constF32(1f);
            case JavaTypes.K_DOUBLE -> b.constF64(1.0);
            default -> throw new IllegalStateException("единица вида " + kind);
        };
    }

    /** Узел {@code v}, приведённый к виду {@code kind} (если виды различны - явный {@code convert}). */
    private int conv(V v, int kind) {
        return v.kind() == kind ? v.node() : b.convert(JavaTypes.irType(kind), v.node());
    }

    private V coerce(V v, int kind) {
        return v.kind() == kind ? v : sv(conv(v, kind), kind, v.open());
    }

    /** Значение после унарного числового повышения (byte/short/char -> int). */
    private V promote(V v) {
        return coerce(v, JavaTypes.unaryPromoted(v.kind()));
    }

    private V constant(Object val) {
        if (val instanceof Boolean x) {
            return sv(b.constBool(x), JavaTypes.K_BOOLEAN, false);
        }
        if (val instanceof Long x) {
            return sv(b.constI64(x), JavaTypes.K_LONG, false);
        }
        if (val instanceof Float x) {
            return sv(b.constF32(x), JavaTypes.K_FLOAT, false);
        }
        if (val instanceof Double x) {
            return sv(b.constF64(x), JavaTypes.K_DOUBLE, false);
        }
        if (val instanceof Character x) {
            return sv(b.constI32(x), JavaTypes.K_INT, false);
        }
        if (val instanceof Number x) {
            return sv(b.constI32(x.intValue()), JavaTypes.K_INT, false);
        }
        throw new IllegalStateException("константа неподдерживаемого типа: " + val);
    }

    // =============================================================================================
    // Стейтменты
    // =============================================================================================

    private void stmt(TreePath p) {
        Tree t = p.getLeaf();
        switch (t.getKind()) {
            case BLOCK -> out.add(blockOf(p));
            case VARIABLE -> localVar(p);
            case EXPRESSION_STATEMENT -> exprStatement(child(p, ((ExpressionStatementTree) t).getExpression()));
            case IF -> ifStmt(p);
            case FOR_LOOP -> forStmt(p);
            case WHILE_LOOP -> whileStmt(p);
            case DO_WHILE_LOOP -> doWhileStmt(p);
            case SWITCH -> switchStmt(p);
            case BREAK -> out.add(at(t, b.breakStmt()));
            case CONTINUE -> out.add(at(t, b.continueStmt()));
            case RETURN -> returnStmt(p);
            case EMPTY_STATEMENT -> {
            }
            default -> throw new IllegalStateException("неожиданный стейтмент " + t.getKind());
        }
    }

    private int blockOf(TreePath p) {
        BlockTree bt = (BlockTree) p.getLeaf();
        List<Integer> list = collect(() -> {
            for (StatementTree s : bt.getStatements()) {
                stmt(child(p, s));
            }
        });
        return at(bt, b.block(toArray(list)));
    }

    /** Тело цикла/ветки: блок IR (сам блок Java переводится в блок, одиночный стейтмент оборачивается). */
    private int bodyBlock(TreePath p) {
        if (p.getLeaf() instanceof BlockTree) {
            return blockOf(p);
        }
        List<Integer> list = collect(() -> stmt(p));
        return at(p.getLeaf(), b.block(toArray(list)));
    }

    private void localVar(TreePath p) {
        VariableTree vt = (VariableTree) p.getLeaf();
        VariableElement el = (VariableElement) trees.getElement(p);
        int kind = JavaTypes.kindOf(el.asType());
        if (kind == JavaTypes.K_ARRAY) {
            localArray(p, vt, el);
            return;
        }
        int l = b.declareLocal(JavaTypes.irType(kind));
        b.nameLocal(l, vt.getName().toString());
        vars.put(el, new Var(false, l, kind, -1, -1));
        int init;
        if (vt.getInitializer() != null) {
            init = coerce(expr(child(p, vt.getInitializer())), kind).node();
        } else {
            init = zero(kind);
        }
        out.add(at(vt, b.localDecl(l, init)));
    }

    private void localArray(TreePath p, VariableTree vt, VariableElement el) {
        NewArrayTree na = (NewArrayTree) vt.getInitializer();
        TreePath np = child(p, na);
        int elemKind = JavaTypes.kindOf(((ArrayType) el.asType()).getComponentType());
        int space = Ann.onVariable(trees, p, Local.class.getName()) ? AddressSpace.LOCAL : AddressSpace.PRIVATE;
        int l = b.declareLocal(b.array(space, JavaTypes.storageType(elemKind)));
        b.nameLocal(l, vt.getName().toString());
        vars.put(el, new Var(false, l, JavaTypes.K_ARRAY, elemKind, space));
        Long len = ConstEval.evalIntegral(trees, child(np, na.getDimensions().get(0)));
        if (len == null) {
            throw new IllegalStateException("размер массива не константа");
        }
        int lenNode = b.constI32(len.intValue());
        out.add(at(vt, space == AddressSpace.LOCAL ? b.localArrayDecl(l, lenNode) : b.privateArrayDecl(l, lenNode)));
    }

    private void exprStatement(TreePath p) {
        Tree t = p.getLeaf();
        switch (t.getKind()) {
            case ASSIGNMENT -> assign(p, false);
            case MULTIPLY_ASSIGNMENT, DIVIDE_ASSIGNMENT, REMAINDER_ASSIGNMENT, PLUS_ASSIGNMENT, MINUS_ASSIGNMENT,
                 LEFT_SHIFT_ASSIGNMENT, RIGHT_SHIFT_ASSIGNMENT, UNSIGNED_RIGHT_SHIFT_ASSIGNMENT, AND_ASSIGNMENT,
                 XOR_ASSIGNMENT, OR_ASSIGNMENT -> compound(p, false);
            case PREFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_INCREMENT, POSTFIX_DECREMENT -> incDec(p, false);
            case METHOD_INVOCATION -> call(p, true);
            default -> throw new IllegalStateException("неожиданное выражение-стейтмент " + t.getKind());
        }
    }

    private void returnStmt(TreePath p) {
        ReturnTree rt = (ReturnTree) p.getLeaf();
        if (rt.getExpression() == null) {
            out.add(at(rt, b.returnStmt(-1)));
        } else {
            V v = coerce(expr(child(p, rt.getExpression())), sig.retKind());
            out.add(at(rt, b.returnStmt(v.node())));
        }
    }

    /** Условие вместе со стейтментами, поднятыми при его вычислении. */
    private record Cond(List<Integer> pre, int node) {
    }

    private Cond cond(TreePath p) {
        int[] node = new int[1];
        List<Integer> pre = collect(() -> node[0] = expr(p).node());
        return new Cond(pre, node[0]);
    }

    private void ifStmt(TreePath p) {
        IfTree it = (IfTree) p.getLeaf();
        Cond c = cond(child(p, it.getCondition()));
        out.addAll(c.pre());
        int thenB = bodyBlock(child(p, it.getThenStatement()));
        int elseB = it.getElseStatement() == null ? -1 : bodyBlock(child(p, it.getElseStatement()));
        out.add(at(it, b.ifStmt(c.node(), thenB, elseB)));
    }

    /**
     * Цикл с предусловием. Если вычисление условия породило стейтменты, цикл строится как
     * {@code loop(true, { стейтменты; if (!cond) break; тело }, update)}: условие вычисляется на каждой итерации,
     * а {@code continue} по-прежнему ведёт в {@code update}.
     */
    private int loopNode(Tree at, Cond c, int body, int update) {
        if (c.pre().isEmpty()) {
            return at(at, b.loop(c.node(), body, update));
        }
        List<Integer> inner = new ArrayList<>(c.pre());
        inner.add(b.ifStmt(b.not(c.node()), b.block(b.breakStmt()), -1));
        inner.add(body);
        return at(at, b.loop(b.constBool(true), b.block(toArray(inner)), update));
    }

    private void whileStmt(TreePath p) {
        WhileLoopTree wt = (WhileLoopTree) p.getLeaf();
        Cond c = cond(child(p, wt.getCondition()));
        int body = bodyBlock(child(p, wt.getStatement()));
        out.add(loopNode(wt, c, body, -1));
    }

    private void forStmt(TreePath p) {
        ForLoopTree ft = (ForLoopTree) p.getLeaf();
        List<Integer> wrapper = collect(() -> {
            for (StatementTree init : ft.getInitializer()) {
                stmt(child(p, init));
            }
            Cond c = ft.getCondition() == null ? new Cond(List.of(), b.constBool(true))
                    : cond(child(p, ft.getCondition()));
            int body = bodyBlock(child(p, ft.getStatement()));
            List<Integer> upd = collect(() -> {
                for (ExpressionStatementTree u : ft.getUpdate()) {
                    exprStatement(child(child(p, u), u.getExpression()));
                }
            });
            int update = upd.isEmpty() ? -1 : (upd.size() == 1 ? upd.get(0) : b.block(toArray(upd)));
            out.add(loopNode(ft, c, body, update));
        });
        out.add(at(ft, b.block(toArray(wrapper))));
    }

    /**
     * Цикл с постусловием. Если условие породило стейтменты, используется флаг первой итерации:
     * {@code first = true; loop(true, { if (!first) { стейтменты; if (!cond) break; } first = false; тело })} -
     * тогда {@code continue} в теле снова проходит через проверку условия.
     */
    private void doWhileStmt(TreePath p) {
        DoWhileLoopTree dt = (DoWhileLoopTree) p.getLeaf();
        int body = bodyBlock(child(p, dt.getStatement()));
        Cond c = cond(child(p, dt.getCondition()));
        if (c.pre().isEmpty()) {
            out.add(at(dt, b.doWhile(body, c.node())));
            return;
        }
        int first = b.declareLocal(Types.BOOL);
        out.add(b.localDecl(first, b.constBool(true)));
        List<Integer> guard = new ArrayList<>(c.pre());
        guard.add(b.ifStmt(b.not(c.node()), b.block(b.breakStmt()), -1));
        int ifNotFirst = b.ifStmt(b.not(b.localGet(first)), b.block(toArray(guard)), -1);
        int setFalse = b.localSet(first, b.constBool(false));
        out.add(at(dt, b.loop(b.constBool(true), b.block(ifNotFirst, setFalse, body), -1)));
    }

    private void switchStmt(TreePath p) {
        SwitchTree st = (SwitchTree) p.getLeaf();
        V key = expr(child(p, st.getExpression()));
        int keyNode = conv(key, JavaTypes.K_INT);
        List<Boolean> defaults = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        List<Integer> bodies = new ArrayList<>();
        for (CaseTree ct : st.getCases()) {
            TreePath cp = child(p, ct);
            List<Integer> stmts = collect(() -> {
                if (ct.getCaseKind() == CaseTree.CaseKind.RULE) {
                    Tree body = ct.getBody();
                    TreePath bp = child(cp, body);
                    if (body instanceof ExpressionTree) {
                        exprStatement(bp);
                    } else {
                        stmt(bp);
                    }
                    out.add(b.breakStmt());
                } else {
                    for (StatementTree s : ct.getStatements()) {
                        stmt(child(cp, s));
                    }
                }
            });
            var labels = ct.getLabels();
            for (int i = 0; i < labels.size(); i++) {
                boolean isDefault = labels.get(i) instanceof DefaultCaseLabelTree;
                int value = 0;
                if (!isDefault) {
                    ConstantCaseLabelTree cl = (ConstantCaseLabelTree) labels.get(i);
                    TreePath lp = child(child(cp, cl), cl.getConstantExpression());
                    Long cv = ConstEval.evalIntegral(trees, lp);
                    if (cv == null) {
                        throw new IllegalStateException("метка case не константа");
                    }
                    value = (int) (long) cv;
                }
                defaults.add(isDefault);
                values.add(value);
                // метки без собственного тела «проваливаются» в тело последней метки группы - как в C
                bodies.add(i == labels.size() - 1 ? b.block(toArray(stmts)) : b.block());
            }
        }
        if (bodies.isEmpty()) {
            return;
        }
        boolean[] isDefault = new boolean[defaults.size()];
        for (int i = 0; i < isDefault.length; i++) {
            isDefault[i] = defaults.get(i);
        }
        out.add(at(st, b.switchStmt(keyNode, isDefault, toArray(values), toArray(bodies))));
    }

    // =============================================================================================
    // Выражения
    // =============================================================================================

    private V expr(TreePath p) {
        V v = exprInner(p);
        mark(p.getLeaf(), v.node());
        return v;
    }

    private V exprInner(TreePath p) {
        Tree t = p.getLeaf();
        switch (t.getKind()) {
            case INT_LITERAL, LONG_LITERAL, FLOAT_LITERAL, DOUBLE_LITERAL, BOOLEAN_LITERAL, CHAR_LITERAL -> {
                return constant(((LiteralTree) t).getValue());
            }
            case PARENTHESIZED -> {
                return expr(child(p, ((ParenthesizedTree) t).getExpression()));
            }
            case IDENTIFIER -> {
                return ident(p);
            }
            case MEMBER_SELECT -> {
                return memberSelect(p);
            }
            case ARRAY_ACCESS -> {
                return arrayAccess(p);
            }
            case METHOD_INVOCATION -> {
                return call(p, false);
            }
            case UNARY_PLUS, UNARY_MINUS, BITWISE_COMPLEMENT, LOGICAL_COMPLEMENT -> {
                return unary(p);
            }
            case PREFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_INCREMENT, POSTFIX_DECREMENT -> {
                return incDec(p, true);
            }
            case ASSIGNMENT -> {
                return assign(p, true);
            }
            case MULTIPLY_ASSIGNMENT, DIVIDE_ASSIGNMENT, REMAINDER_ASSIGNMENT, PLUS_ASSIGNMENT, MINUS_ASSIGNMENT,
                 LEFT_SHIFT_ASSIGNMENT, RIGHT_SHIFT_ASSIGNMENT, UNSIGNED_RIGHT_SHIFT_ASSIGNMENT, AND_ASSIGNMENT,
                 XOR_ASSIGNMENT, OR_ASSIGNMENT -> {
                return compound(p, true);
            }
            case CONDITIONAL_EXPRESSION -> {
                return conditional(p);
            }
            case CONDITIONAL_AND, CONDITIONAL_OR -> {
                return shortCircuit(p);
            }
            case TYPE_CAST -> {
                return cast(p);
            }
            case MULTIPLY, DIVIDE, REMAINDER, PLUS, MINUS, LEFT_SHIFT, RIGHT_SHIFT, UNSIGNED_RIGHT_SHIFT, LESS_THAN,
                 GREATER_THAN, LESS_THAN_EQUAL, GREATER_THAN_EQUAL, EQUAL_TO, NOT_EQUAL_TO, AND, XOR, OR -> {
                BinaryTree bt = (BinaryTree) t;
                List<V> vs = operands(List.of(child(p, bt.getLeftOperand()), child(p, bt.getRightOperand())));
                return applyBinary(t.getKind(), vs.get(0), vs.get(1));
            }
            default -> throw new IllegalStateException("неожиданное выражение " + t.getKind());
        }
    }

    /**
     * Вычисляет операнды слева направо. Если вычисление очередного операнда добавило стейтменты, все ранее
     * вычисленные «открытые» операнды сохраняются во временные локали <b>до</b> этих стейтментов - так порядок
     * побочных эффектов Java не нарушается.
     */
    private List<V> operands(List<TreePath> ps) {
        return operandsFrom(new ArrayList<>(), ps);
    }

    private List<V> operandsFrom(List<V> first, List<TreePath> ps) {
        List<V> vals = new ArrayList<>(first);
        for (TreePath p : ps) {
            int before = out.size();
            V v = expr(p);
            if (out.size() > before) {
                int insertAt = before;
                for (int j = 0; j < vals.size(); j++) {
                    V w = vals.get(j);
                    if (w.open() && w.kind() != JavaTypes.K_ARRAY) {
                        int l = b.declareLocal(b.typeOf(w.node()));
                        out.add(insertAt++, b.localDecl(l, w.node()));
                        vals.set(j, sv(b.localGet(l), w.kind(), false));
                    }
                }
            }
            vals.add(v);
        }
        return vals;
    }

    private V ident(TreePath p) {
        Element el = trees.getElement(p);
        Var var = vars.get(el);
        if (var != null) {
            if (var.kind() == JavaTypes.K_ARRAY) {
                int node = var.param() ? b.param(var.index()) : b.localGet(var.index());
                return new V(node, JavaTypes.K_ARRAY, var.elemKind(), var.space(), false);
            }
            return var.param() ? sv(b.param(var.index()), var.kind(), false)
                    : sv(b.localGet(var.index()), var.kind(), true);
        }
        if (el instanceof VariableElement ve && ve.getConstantValue() != null) {
            return constant(ve.getConstantValue());
        }
        throw new IllegalStateException("неизвестная переменная " + el);
    }

    private V memberSelect(TreePath p) {
        MemberSelectTree ms = (MemberSelectTree) p.getLeaf();
        if (ms.getIdentifier().contentEquals("length")) {
            TypeMirror et = trees.getTypeMirror(child(p, ms.getExpression()));
            if (et != null && et.getKind() == javax.lang.model.type.TypeKind.ARRAY) {
                V arr = expr(child(p, ms.getExpression()));
                return sv(b.arrayLen(arr.node()), JavaTypes.K_INT, false);
            }
        }
        Element el = trees.getElement(p);
        if (el instanceof VariableElement ve && ve.getConstantValue() != null) {
            return constant(ve.getConstantValue());
        }
        throw new IllegalStateException("неподдерживаемое обращение к члену " + ms);
    }

    private int indexNode(V idx) {
        return conv(idx, JavaTypes.K_INT);
    }

    private V arrayAccess(TreePath p) {
        ArrayAccessTree aa = (ArrayAccessTree) p.getLeaf();
        V arr = expr(child(p, aa.getExpression()));
        V idx = expr(child(p, aa.getIndex()));
        int ptr = b.elemPtr(arr.node(), indexNode(idx));
        return loadElem(ptr, arr.elemKind());
    }

    private V loadElem(int ptr, int elemKind) {
        int n = b.load(ptr);
        if (elemKind == JavaTypes.K_BOOLEAN) {
            n = b.convert(Types.BOOL, n);
        }
        return sv(n, elemKind, true);
    }

    private int storeElem(int ptr, V value, int elemKind) {
        int val = value.node();
        if (elemKind == JavaTypes.K_BOOLEAN) {
            val = b.convert(Types.U8, val);
        }
        effects = true;
        return b.store(ptr, val);
    }

    // ---- вызовы ----------------------------------------------------------------------------------

    private V call(TreePath p, boolean asStatement) {
        MethodInvocationTree mit = (MethodInvocationTree) p.getLeaf();
        ExecutableElement callee = (ExecutableElement) trees.getElement(child(p, mit.getMethodSelect()));
        FnSig csig = FnSig.of(callee);
        List<TreePath> ps = new ArrayList<>();
        for (ExpressionTree a : mit.getArguments()) {
            ps.add(child(p, a));
        }
        List<V> raw = operands(ps);
        int n = raw.size();
        int[] args = new int[n];
        boolean anyOpen = false;
        for (int i = 0; i < n; i++) {
            V v = raw.get(i);
            if (csig.paramKinds()[i] == JavaTypes.K_ARRAY) {
                if (v.space() != csig.spaces()[i]) {
                    rep.report(ps.get(i), "GPU0121", callee.getParameters().get(i).getSimpleName(),
                            Ann.spaceName(csig.spaces()[i]), Ann.spaceName(v.space()));
                }
                args[i] = v.node();
            } else {
                V c = coerce(v, csig.paramKinds()[i]);
                args[i] = c.node();
                anyOpen |= c.open();
            }
        }
        boolean isBuiltin = callee.getEnclosingElement() instanceof TypeElement te
                && te.getQualifiedName().contentEquals(Gpu.class.getName());
        int node;
        boolean effectful;
        boolean open;
        if (isBuiltin) {
            int id = Builtins.idOf(callee.getSimpleName().toString());
            node = b.intrinsic(id, args);
            effectful = Builtins.has(id, Builtins.EFFECT);
            open = anyOpen || Builtins.has(id, Builtins.READS);
        } else {
            FnInfo info = fns.get(callee);
            if (info == null) {
                throw new IllegalStateException("функция " + callee + " не построена раньше вызывающей");
            }
            node = b.call(info.index, args);
            effectful = info.effects;
            open = true;
        }
        if (effectful) {
            effects = true;
        }
        int retKind = csig.retKind();
        if (asStatement) {
            out.add(at(mit, b.exprStmt(node)));
            return null;
        }
        if (effectful) {
            int l = b.declareLocal(JavaTypes.irType(retKind));
            out.add(at(mit, b.localDecl(l, node)));
            return sv(b.localGet(l), retKind, false);
        }
        return sv(node, retKind, open);
    }

    // ---- унарные, приведения, бинарные ----------------------------------------------------------

    private V unary(TreePath p) {
        UnaryTree ut = (UnaryTree) p.getLeaf();
        V v = expr(child(p, ut.getExpression()));
        switch (ut.getKind()) {
            case LOGICAL_COMPLEMENT -> {
                return sv(b.not(v.node()), JavaTypes.K_BOOLEAN, v.open());
            }
            case UNARY_PLUS -> {
                return promote(v);
            }
            case UNARY_MINUS -> {
                V x = promote(v);
                return sv(b.neg(x.node()), x.kind(), x.open());
            }
            default -> {
                V x = promote(v);
                return sv(b.not(x.node()), x.kind(), x.open());
            }
        }
    }

    private V cast(TreePath p) {
        TypeCastTree ct = (TypeCastTree) p.getLeaf();
        int target = JavaTypes.kindOf(trees.getTypeMirror(child(p, ct.getType())));
        V v = expr(child(p, ct.getExpression()));
        return coerce(v, target);
    }

    /** Бинарная операция Java над готовыми значениями (используется и составными присваиваниями). */
    private V applyBinary(Tree.Kind kind, V l, V r) {
        boolean open = l.open() || r.open();
        switch (kind) {
            case MULTIPLY, DIVIDE, REMAINDER, PLUS, MINUS -> {
                int pk = JavaTypes.binaryPromoted(l.kind(), r.kind());
                int op = switch (kind) {
                    case MULTIPLY -> Op.MUL;
                    case DIVIDE -> Op.DIV;
                    case REMAINDER -> Op.REM;
                    case PLUS -> Op.ADD;
                    default -> Op.SUB;
                };
                return sv(b.binary(op, conv(l, pk), conv(r, pk)), pk, open);
            }
            case LEFT_SHIFT, RIGHT_SHIFT, UNSIGNED_RIGHT_SHIFT -> {
                int lk = JavaTypes.unaryPromoted(l.kind());
                int rk = JavaTypes.unaryPromoted(r.kind());
                int ln = conv(l, lk);
                int rn = conv(r, rk);
                if (JavaTypes.irType(rk) != JavaTypes.irType(lk)) {
                    rn = b.convert(JavaTypes.irType(lk), rn);
                }
                int op = kind == Tree.Kind.LEFT_SHIFT ? Op.SHL : kind == Tree.Kind.RIGHT_SHIFT ? Op.SHR : Op.USHR;
                return sv(b.binary(op, ln, rn), lk, open);
            }
            case LESS_THAN, GREATER_THAN, LESS_THAN_EQUAL, GREATER_THAN_EQUAL -> {
                int pk = JavaTypes.binaryPromoted(l.kind(), r.kind());
                int op = switch (kind) {
                    case LESS_THAN -> Op.LT;
                    case GREATER_THAN -> Op.GT;
                    case LESS_THAN_EQUAL -> Op.LE;
                    default -> Op.GE;
                };
                return sv(b.binary(op, conv(l, pk), conv(r, pk)), JavaTypes.K_BOOLEAN, open);
            }
            case EQUAL_TO, NOT_EQUAL_TO -> {
                int op = kind == Tree.Kind.EQUAL_TO ? Op.EQ : Op.NE;
                if (l.kind() == JavaTypes.K_BOOLEAN) {
                    return sv(b.binary(op, l.node(), r.node()), JavaTypes.K_BOOLEAN, open);
                }
                int pk = JavaTypes.binaryPromoted(l.kind(), r.kind());
                return sv(b.binary(op, conv(l, pk), conv(r, pk)), JavaTypes.K_BOOLEAN, open);
            }
            case AND, OR, XOR -> {
                int op = kind == Tree.Kind.AND ? Op.AND : kind == Tree.Kind.OR ? Op.OR : Op.XOR;
                if (l.kind() == JavaTypes.K_BOOLEAN) {
                    return sv(b.binary(op, l.node(), r.node()), JavaTypes.K_BOOLEAN, open);
                }
                int pk = JavaTypes.binaryPromoted(l.kind(), r.kind());
                return sv(b.binary(op, conv(l, pk), conv(r, pk)), pk, open);
            }
            default -> throw new IllegalStateException("неожиданная бинарная операция " + kind);
        }
    }

    // ---- ленивые операции ------------------------------------------------------------------------

    private V conditional(TreePath p) {
        ConditionalExpressionTree ct = (ConditionalExpressionTree) p.getLeaf();
        int rk = JavaTypes.kindOf(trees.getTypeMirror(p));
        V c = expr(child(p, ct.getCondition()));
        V[] a = new V[1];
        V[] e = new V[1];
        List<Integer> la = collect(() -> a[0] = coerce(expr(child(p, ct.getTrueExpression())), rk));
        List<Integer> le = collect(() -> e[0] = coerce(expr(child(p, ct.getFalseExpression())), rk));
        if (la.isEmpty() && le.isEmpty()) {
            return sv(b.select(c.node(), a[0].node(), e[0].node()), rk, c.open() || a[0].open() || e[0].open());
        }
        // в ветках есть эффекты: select ленив, но эффекты в нём запрещены - строим if со временной локалью
        int tmp = b.declareLocal(JavaTypes.irType(rk));
        out.add(b.localDecl(tmp, zero(rk)));
        la.add(b.localSet(tmp, a[0].node()));
        le.add(b.localSet(tmp, e[0].node()));
        out.add(at(ct, b.ifStmt(c.node(), b.block(toArray(la)), b.block(toArray(le)))));
        return sv(b.localGet(tmp), rk, false);
    }

    private V shortCircuit(TreePath p) {
        BinaryTree bt = (BinaryTree) p.getLeaf();
        boolean and = bt.getKind() == Tree.Kind.CONDITIONAL_AND;
        V l = expr(child(p, bt.getLeftOperand()));
        V[] r = new V[1];
        List<Integer> lr = collect(() -> r[0] = expr(child(p, bt.getRightOperand())));
        if (lr.isEmpty()) {
            int node = and ? b.select(l.node(), r[0].node(), b.constBool(false))
                    : b.select(l.node(), b.constBool(true), r[0].node());
            return sv(node, JavaTypes.K_BOOLEAN, l.open() || r[0].open());
        }
        int tmp = b.declareLocal(Types.BOOL);
        out.add(b.localDecl(tmp, l.node()));
        int guard = and ? b.localGet(tmp) : b.not(b.localGet(tmp));
        lr.add(b.localSet(tmp, r[0].node()));
        out.add(at(bt, b.ifStmt(guard, b.block(toArray(lr)), -1)));
        return sv(b.localGet(tmp), JavaTypes.K_BOOLEAN, false);
    }

    // ---- присваивания ----------------------------------------------------------------------------

    /** Цель присваивания: чтение и запись, умеющие строить новые узлы при каждом вызове. */
    private interface Target {
        V read();

        int write(V value);
    }

    private V assign(TreePath p, boolean needValue) {
        AssignmentTree at = (AssignmentTree) p.getLeaf();
        TreePath target = unparen(child(p, at.getVariable()));
        TreePath rhsPath = child(p, at.getExpression());
        if (target.getLeaf() instanceof IdentifierTree) {
            Var var = vars.get(trees.getElement(target));
            V rhs = coerce(expr(rhsPath), var.kind());
            out.add(at(at, b.localSet(var.index(), rhs.node())));
            return needValue ? sv(b.localGet(var.index()), var.kind(), true) : null;
        }
        ArrayAccessTree aa = (ArrayAccessTree) target.getLeaf();
        TreePath arrPath = child(target, aa.getExpression());
        TreePath idxPath = child(target, aa.getIndex());
        V rhs;
        V idx;
        V arr;
        if (isPure(idxPath) && isPure(rhsPath)) {
            // порядок вычисления не наблюдаем: правая часть первой (как в эталонном IR), затем адрес
            rhs = expr(rhsPath);
            arr = expr(arrPath);
            idx = expr(idxPath);
        } else {
            List<V> vs = operands(List.of(idxPath, rhsPath));
            idx = vs.get(0);
            rhs = vs.get(1);
            arr = expr(arrPath);
        }
        int ek = arr.elemKind();
        V value = coerce(rhs, ek);
        if (needValue) {
            int tmp = b.declareLocal(JavaTypes.irType(ek));
            out.add(b.localDecl(tmp, value.node()));
            value = sv(b.localGet(tmp), ek, false);
            out.add(at(at, storeElem(b.elemPtr(arr.node(), indexNode(idx)), value, ek)));
            return sv(b.localGet(tmp), ek, false);
        }
        out.add(at(at, storeElem(b.elemPtr(arr.node(), indexNode(idx)), value, ek)));
        return null;
    }

    private V compound(TreePath p, boolean needValue) {
        CompoundAssignmentTree ct = (CompoundAssignmentTree) p.getLeaf();
        Tree.Kind op = switch (ct.getKind()) {
            case MULTIPLY_ASSIGNMENT -> Tree.Kind.MULTIPLY;
            case DIVIDE_ASSIGNMENT -> Tree.Kind.DIVIDE;
            case REMAINDER_ASSIGNMENT -> Tree.Kind.REMAINDER;
            case PLUS_ASSIGNMENT -> Tree.Kind.PLUS;
            case MINUS_ASSIGNMENT -> Tree.Kind.MINUS;
            case LEFT_SHIFT_ASSIGNMENT -> Tree.Kind.LEFT_SHIFT;
            case RIGHT_SHIFT_ASSIGNMENT -> Tree.Kind.RIGHT_SHIFT;
            case UNSIGNED_RIGHT_SHIFT_ASSIGNMENT -> Tree.Kind.UNSIGNED_RIGHT_SHIFT;
            case AND_ASSIGNMENT -> Tree.Kind.AND;
            case XOR_ASSIGNMENT -> Tree.Kind.XOR;
            default -> Tree.Kind.OR;
        };
        return modify(ct, child(p, ct.getVariable()), op, child(p, ct.getExpression()), needValue ? MODE_NEW : MODE_STMT);
    }

    private V incDec(TreePath p, boolean needValue) {
        UnaryTree ut = (UnaryTree) p.getLeaf();
        boolean inc = ut.getKind() == Tree.Kind.PREFIX_INCREMENT || ut.getKind() == Tree.Kind.POSTFIX_INCREMENT;
        boolean postfix = ut.getKind() == Tree.Kind.POSTFIX_INCREMENT || ut.getKind() == Tree.Kind.POSTFIX_DECREMENT;
        int mode = !needValue ? MODE_STMT : postfix ? MODE_OLD : MODE_NEW;
        return modify(ut, child(p, ut.getExpression()), inc ? Tree.Kind.PLUS : Tree.Kind.MINUS, null, mode);
    }

    /**
     * Общая часть {@code x op= y}, {@code x++}, {@code --x}: {@code x = (T)(x op y)}.
     *
     * @param at      дерево для позиции стейтмента
     * @param target  цель: локаль или элемент массива
     * @param op      бинарная операция Java
     * @param rhsPath правая часть либо {@code null} для {@code ++/--} (тогда правая часть - единица)
     * @param mode    что вернуть: ничего, новое значение или старое (постфиксный {@code ++/--})
     */
    private V modify(Tree at, TreePath target, Tree.Kind op, TreePath rhsPath, int mode) {
        TreePath tp = unparen(target);
        Target t;
        int tk;
        if (tp.getLeaf() instanceof IdentifierTree) {
            Var var = vars.get(trees.getElement(tp));
            tk = var.kind();
            int l = var.index();
            t = new Target() {
                @Override
                public V read() {
                    return sv(b.localGet(l), var.kind(), true);
                }

                @Override
                public int write(V value) {
                    return b.localSet(l, value.node());
                }
            };
        } else {
            ArrayAccessTree aa = (ArrayAccessTree) tp.getLeaf();
            TreePath arrPath = child(tp, aa.getExpression());
            TreePath idxPath = child(tp, aa.getIndex());
            // индекс нужен дважды (чтение и запись): чистый пересчитываем, иначе сохраняем во временную локаль
            IntSupplier idx;
            if (isPure(idxPath) && (rhsPath == null || isPure(rhsPath))) {
                idx = () -> indexNode(expr(idxPath));
            } else {
                int tmp = b.declareLocal(Types.I32);
                out.add(b.localDecl(tmp, indexNode(expr(idxPath))));
                idx = () -> b.localGet(tmp);
            }
            tk = JavaTypes.kindOf(((ArrayType) trees.getTypeMirror(arrPath)).getComponentType());
            t = new Target() {
                @Override
                public V read() {
                    V arr = expr(arrPath);
                    return loadElem(b.elemPtr(arr.node(), idx.getAsInt()), arr.elemKind());
                }

                @Override
                public int write(V value) {
                    V arr = expr(arrPath);
                    return storeElem(b.elemPtr(arr.node(), idx.getAsInt()), value, arr.elemKind());
                }
            };
        }

        V old = t.read();
        int oldLocal = -1;
        if (mode == MODE_OLD) {
            oldLocal = b.declareLocal(JavaTypes.irType(tk));
            out.add(b.localDecl(oldLocal, old.node()));
            old = sv(b.localGet(oldLocal), tk, false);
        }
        V rhs;
        if (rhsPath != null) {
            List<V> vs = operandsFrom(new ArrayList<>(List.of(old)), List.of(rhsPath));
            old = vs.get(0);
            rhs = vs.get(1);
        } else {
            int pk = JavaTypes.binaryPromoted(tk, JavaTypes.K_INT);
            rhs = sv(one(pk), pk, false);
        }
        V result = coerce(applyBinary(op, old, rhs), tk);
        out.add(this.at(at, t.write(result)));
        return switch (mode) {
            case MODE_NEW -> t.read();
            case MODE_OLD -> sv(b.localGet(oldLocal), tk, false);
            default -> null;
        };
    }

    /** Выражение без побочных эффектов: его можно вычислить повторно или в другом порядке. */
    private boolean isPure(TreePath p) {
        boolean[] pure = {true};
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitAssignment(AssignmentTree t, Void v) {
                pure[0] = false;
                return null;
            }

            @Override
            public Void visitCompoundAssignment(CompoundAssignmentTree t, Void v) {
                pure[0] = false;
                return null;
            }

            @Override
            public Void visitUnary(UnaryTree t, Void v) {
                switch (t.getKind()) {
                    case PREFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_INCREMENT, POSTFIX_DECREMENT -> pure[0] = false;
                    default -> {
                    }
                }
                return super.visitUnary(t, v);
            }

            @Override
            public Void visitMethodInvocation(MethodInvocationTree t, Void v) {
                Element el = trees.getElement(new TreePath(getCurrentPath(), t.getMethodSelect()));
                boolean pureBuiltin = false;
                if (el instanceof ExecutableElement ee && ee.getEnclosingElement() instanceof TypeElement te
                        && te.getQualifiedName().contentEquals(Gpu.class.getName())) {
                    int id = Builtins.idOf(ee.getSimpleName().toString());
                    pureBuiltin = id >= 0 && Builtins.has(id, Builtins.PURE);
                }
                if (!pureBuiltin) {
                    pure[0] = false;
                    return null;
                }
                return super.visitMethodInvocation(t, v);
            }
        }.scan(p, null);
        return pure[0];
    }
}
