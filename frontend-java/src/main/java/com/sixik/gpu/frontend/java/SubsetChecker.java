package com.sixik.gpu.frontend.java;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.DeviceFunction;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.annotations.Local;
import com.sixik.gpu.api.annotations.Struct;
import com.sixik.gpu.ir.Builtins;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.CaseLabelTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.PatternCaseLabelTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.YieldTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

/**
 * Валидатор подмножества Java для GPU-кода (REWORK_ARCHITECTURE.md, 3.3): проверяет сигнатуру и тело одного метода
 * и выдаёт <b>все</b> найденные нарушения с кодами {@code GPU####} и точной позицией, не останавливаясь на первом.
 *
 * <p>Проверка предшествует построению IR: {@link FunctionLowerer} исходит из того, что метод прошёл проверку, и
 * не дублирует диагностики. Заодно проверка собирает вызываемые device-функции ({@link #callSites()}) в порядке
 * появления - из них строится транзитивное замыкание.</p>
 */
final class SubsetChecker extends TreePathScanner<Void, Void> {

    private static final String GPU_CLASS = Gpu.class.getName();
    private static final String VECTOR_PACKAGE = "com.sixik.gpu.api.types.";

    /**
     * Вызов device-функции в проверенном методе.
     *
     * @param callee вызываемый метод
     * @param at     путь к вызову (для диагностик о рекурсии)
     */
    record CallSite(ExecutableElement callee, TreePath at) {
    }

    private final Trees trees;
    private final Reporter rep;
    private final List<CallSite> callSites = new ArrayList<>();
    private TreePath methodPath;
    private boolean kernel;

    SubsetChecker(Trees trees, Reporter rep) {
        this.trees = trees;
        this.rep = rep;
    }

    /**
     * Проверяет метод целиком.
     *
     * @param path путь к {@link MethodTree}
     * @param e    элемент метода
     * @return {@code true}, если нарушений не найдено
     */
    boolean check(TreePath path, ExecutableElement e) {
        callSites.clear();
        methodPath = path;
        kernel = e.getAnnotation(Kernel.class) != null;
        int before = rep.errorCount();
        MethodTree mt = (MethodTree) path.getLeaf();
        checkSignature(path, mt, e);
        if (mt.getBody() != null) {
            scan(new TreePath(path, mt.getBody()), null);
        }
        return rep.errorCount() == before;
    }

    /**
     * @return вызовы device-функций в последнем проверенном методе в порядке появления (с повторами - по одному на
     * вызов)
     */
    List<CallSite> callSites() {
        return new ArrayList<>(callSites);
    }

    // =============================================================================================
    // Сигнатура
    // =============================================================================================

    private void checkSignature(TreePath path, MethodTree mt, ExecutableElement e) {
        String name = e.getSimpleName().toString();
        if (!e.getModifiers().contains(Modifier.STATIC)) {
            rep.report(path, "GPU0001", name, kernel ? "Kernel" : "DeviceFunction");
        }
        if (!e.getTypeParameters().isEmpty() || e.isVarArgs() || !e.getThrownTypes().isEmpty()) {
            rep.report(path, "GPU0007", name);
        }
        int ret = JavaTypes.kindOf(e.getReturnType());
        if (kernel) {
            if (ret != JavaTypes.K_VOID) {
                rep.report(path, "GPU0002", name);
            }
        } else if (!(ret == JavaTypes.K_VOID || ret == JavaTypes.K_BOOLEAN || ret == JavaTypes.K_INT
                || ret == JavaTypes.K_LONG || ret == JavaTypes.K_FLOAT || ret == JavaTypes.K_DOUBLE)) {
            if (isFutureType(e.getReturnType())) {
                rep.report(path, "GPU0190", "Тип результата " + e.getReturnType());
            } else {
                rep.report(path, "GPU0006", e.getReturnType(), name);
            }
        }
        List<? extends VariableTree> ptrees = mt.getParameters();
        for (int i = 0; i < ptrees.size(); i++) {
            VariableElement pe = e.getParameters().get(i);
            TreePath pp = new TreePath(path, ptrees.get(i));
            checkParam(pp, pe, name);
        }
    }

    private void checkParam(TreePath pp, VariableElement pe, String method) {
        TypeMirror t = pe.asType();
        int k = JavaTypes.kindOf(t);
        String pname = pe.getSimpleName().toString();
        switch (k) {
            case JavaTypes.K_INT, JavaTypes.K_LONG, JavaTypes.K_FLOAT, JavaTypes.K_DOUBLE -> {
            }
            case JavaTypes.K_BOOLEAN -> rep.report(pp, "GPU0004", pname);
            case JavaTypes.K_ARRAY -> {
                TypeMirror comp = ((ArrayType) t).getComponentType();
                int ck = JavaTypes.kindOf(comp);
                if (ck == JavaTypes.K_ARRAY) {
                    rep.report(pp, "GPU0005", pname);
                } else if (!JavaTypes.isPrimitive(ck)) {
                    unsupportedType(pp, comp, pname);
                }
            }
            default -> unsupportedType(pp, t, pname);
        }
    }

    private void unsupportedType(TreePath at, TypeMirror t, String paramName) {
        if (isFutureType(t)) {
            rep.report(at, "GPU0190", "Тип " + t);
        } else {
            rep.report(at, "GPU0003", t, paramName);
        }
    }

    /** Типы, которые языком задуманы, но фронтендом пока не поддержаны: векторы, ссылки, {@code @Struct}. */
    private static boolean isFutureType(TypeMirror t) {
        TypeMirror base = t;
        while (base.getKind() == TypeKind.ARRAY) {
            base = ((ArrayType) base).getComponentType();
        }
        if (base instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
            return te.getQualifiedName().toString().startsWith(VECTOR_PACKAGE) || te.getAnnotation(Struct.class) != null;
        }
        return false;
    }

    // =============================================================================================
    // Конструкции, которых нет в подмножестве
    // =============================================================================================

    @Override
    public Void visitTry(TryTree t, Void v) {
        rep.report(getCurrentPath(), "GPU0101", "try");
        return null;
    }

    @Override
    public Void visitThrow(ThrowTree t, Void v) {
        rep.report(getCurrentPath(), "GPU0101", "throw");
        return null;
    }

    @Override
    public Void visitSynchronized(SynchronizedTree t, Void v) {
        rep.report(getCurrentPath(), "GPU0102");
        return null;
    }

    @Override
    public Void visitAssert(AssertTree t, Void v) {
        rep.report(getCurrentPath(), "GPU0103");
        return null;
    }

    @Override
    public Void visitLabeledStatement(LabeledStatementTree t, Void v) {
        rep.report(getCurrentPath(), "GPU0104");
        return null;
    }

    @Override
    public Void visitBreak(BreakTree t, Void v) {
        if (t.getLabel() != null) {
            rep.report(getCurrentPath(), "GPU0104");
        }
        return null;
    }

    @Override
    public Void visitContinue(ContinueTree t, Void v) {
        if (t.getLabel() != null) {
            rep.report(getCurrentPath(), "GPU0104");
        }
        return null;
    }

    @Override
    public Void visitLambdaExpression(LambdaExpressionTree t, Void v) {
        rep.report(getCurrentPath(), "GPU0108");
        return null;
    }

    @Override
    public Void visitMemberReference(MemberReferenceTree t, Void v) {
        rep.report(getCurrentPath(), "GPU0108");
        return null;
    }

    @Override
    public Void visitClass(ClassTree t, Void v) {
        rep.report(getCurrentPath(), "GPU0108");
        return null;
    }

    @Override
    public Void visitNewClass(NewClassTree t, Void v) {
        rep.report(getCurrentPath(), "GPU0107", "оператор new создаёт объект");
        return null;
    }

    @Override
    public Void visitInstanceOf(InstanceOfTree t, Void v) {
        rep.report(getCurrentPath(), "GPU0109", "instanceof");
        return null;
    }

    @Override
    public Void visitEnhancedForLoop(EnhancedForLoopTree t, Void v) {
        rep.report(getCurrentPath(), "GPU0111");
        return null;
    }

    @Override
    public Void visitSwitchExpression(SwitchExpressionTree t, Void v) {
        rep.report(getCurrentPath(), "GPU0110", "switch как выражение");
        return null;
    }

    @Override
    public Void visitYield(YieldTree t, Void v) {
        rep.report(getCurrentPath(), "GPU0110", "yield");
        return null;
    }

    @Override
    public Void visitLiteral(LiteralTree t, Void v) {
        switch (t.getKind()) {
            case STRING_LITERAL -> rep.report(getCurrentPath(), "GPU0106");
            case NULL_LITERAL -> rep.report(getCurrentPath(), "GPU0107", "null");
            default -> {
            }
        }
        return null;
    }

    @Override
    public Void visitTypeCast(TypeCastTree t, Void v) {
        TreePath p = getCurrentPath();
        TypeMirror target = trees.getTypeMirror(new TreePath(p, t.getType()));
        TypeMirror source = trees.getTypeMirror(new TreePath(p, t.getExpression()));
        int tk = target == null ? JavaTypes.K_OTHER : JavaTypes.kindOf(target);
        int sk = source == null ? JavaTypes.K_OTHER : JavaTypes.kindOf(source);
        if (target != null && source != null && !(JavaTypes.isNumeric(tk) && JavaTypes.isNumeric(sk))) {
            rep.report(p, "GPU0109", "приведение " + source + " к " + target);
        }
        return scan(t.getExpression(), v);
    }

    @Override
    public Void visitSwitch(SwitchTree t, Void v) {
        TreePath p = getCurrentPath();
        TypeMirror key = trees.getTypeMirror(new TreePath(p, t.getExpression()));
        if (key != null) {
            int k = JavaTypes.kindOf(key);
            if (!(k == JavaTypes.K_INT || k == JavaTypes.K_SHORT || k == JavaTypes.K_BYTE || k == JavaTypes.K_CHAR)) {
                rep.report(p, "GPU0110", "ключ типа " + key);
            }
        }
        return super.visitSwitch(t, v);
    }

    @Override
    public Void visitCase(CaseTree t, Void v) {
        TreePath p = getCurrentPath();
        for (CaseLabelTree label : t.getLabels()) {
            if (label instanceof PatternCaseLabelTree) {
                rep.report(new TreePath(p, label), "GPU0110", "pattern matching в case");
            }
        }
        if (t.getGuard() != null) {
            rep.report(new TreePath(p, t.getGuard()), "GPU0110", "guard в case");
        }
        return super.visitCase(t, v);
    }

    // =============================================================================================
    // Переменные, массивы, поля
    // =============================================================================================

    @Override
    public Void visitVariable(VariableTree vt, Void v) {
        TreePath p = getCurrentPath();
        Element el = trees.getElement(p);
        if (el != null) {
            TypeMirror t = el.asType();
            int k = JavaTypes.kindOf(t);
            boolean isLocalAnnotated = Ann.onVariable(trees, p, Local.class.getName());
            if (JavaTypes.isPrimitive(k)) {
                if (isLocalAnnotated) {
                    rep.report(p, "GPU0120");
                }
            } else if (k == JavaTypes.K_ARRAY) {
                checkLocalArray(p, vt, (ArrayType) t, isLocalAnnotated);
            } else if (isFutureType(t)) {
                rep.report(p, "GPU0190", "Тип " + t);
            } else if (k != JavaTypes.K_VOID) {
                rep.report(p, "GPU0119", t);
            }
        }
        boolean arrayAlias = el != null && el.asType().getKind() == TypeKind.ARRAY
                && vt.getInitializer() != null && !(vt.getInitializer() instanceof NewArrayTree);
        if (vt.getInitializer() != null && !arrayAlias) {
            scan(vt.getInitializer(), v);
        }
        return null;
    }

    private void checkLocalArray(TreePath p, VariableTree vt, ArrayType t, boolean isLocalAnnotated) {
        TypeMirror comp = t.getComponentType();
        int ck = JavaTypes.kindOf(comp);
        if (ck == JavaTypes.K_ARRAY) {
            rep.report(p, "GPU0005", vt.getName());
            return;
        }
        if (!JavaTypes.isPrimitive(ck)) {
            if (isFutureType(t)) {
                rep.report(p, "GPU0190", "Тип " + t);
            } else {
                rep.report(p, "GPU0119", t);
            }
            return;
        }
        if (!(vt.getInitializer() instanceof NewArrayTree)) {
            rep.report(p, "GPU0116", "локальный массив " + vt.getName()
                    + " должен создаваться как 'new " + comp + "[N]' (копии ссылок на массив запрещены)");
        }
        if (isLocalAnnotated) {
            boolean topLevel = kernel && p.getParentPath() != null
                    && p.getParentPath().getLeaf() instanceof BlockTree
                    && p.getParentPath().getParentPath() != null
                    && p.getParentPath().getParentPath().getLeaf() == methodPath.getLeaf();
            if (!topLevel) {
                rep.report(p, "GPU0120");
            }
        }
    }

    @Override
    public Void visitNewArray(NewArrayTree na, Void v) {
        TreePath p = getCurrentPath();
        Tree parent = p.getParentPath().getLeaf();
        if (!(parent instanceof VariableTree vt && vt.getInitializer() == na)) {
            rep.report(p, "GPU0117", "создание массива вне объявления локальной переменной");
            return null;
        }
        if (na.getInitializers() != null) {
            rep.report(p, "GPU0117", "инициализатор вида {...}");
        } else if (na.getDimensions().size() != 1) {
            rep.report(p, "GPU0005", vt.getName());
        } else {
            Long size = ConstEval.evalIntegral(trees, new TreePath(p, na.getDimensions().get(0)));
            if (size == null || size <= 0 || size > Integer.MAX_VALUE) {
                rep.report(p, "GPU0117", "размер должен быть положительной константой");
            }
        }
        return null;
    }

    @Override
    public Void visitArrayAccess(ArrayAccessTree t, Void v) {
        TreePath p = getCurrentPath();
        ExpressionTree base = t.getExpression();
        if (!(base instanceof IdentifierTree)) {
            rep.report(p, "GPU0116", "индексировать можно только массив, заданный именем (многомерные массивы и результаты вызовов не поддерживаются)");
        } else {
            scan(base, v);
        }
        return scan(t.getIndex(), v);
    }

    @Override
    public Void visitIdentifier(IdentifierTree id, Void v) {
        TreePath p = getCurrentPath();
        if (id.getName().contentEquals("this") || id.getName().contentEquals("super")) {
            rep.report(p, "GPU0107", id.getName());
            return null;
        }
        Element el = trees.getElement(p);
        if (el == null) {
            return null;
        }
        switch (el.getKind()) {
            case LOCAL_VARIABLE, PARAMETER -> {
                if (el.asType().getKind() == TypeKind.ARRAY) {
                    checkArrayContext(p, id);
                }
            }
            case FIELD -> checkFieldAccess(p, (VariableElement) el);
            case ENUM_CONSTANT -> rep.report(p, "GPU0107", "константа перечисления " + id.getName());
            default -> {
            }
        }
        return null;
    }

    private void checkArrayContext(TreePath p, IdentifierTree id) {
        Tree parent = p.getParentPath().getLeaf();
        boolean ok = (parent instanceof ArrayAccessTree aa && aa.getExpression() == id)
                || (parent instanceof MemberSelectTree ms && ms.getExpression() == id
                && ms.getIdentifier().contentEquals("length"))
                || (parent instanceof MethodInvocationTree mi && mi.getArguments().contains(id));
        if (!ok) {
            rep.report(p, "GPU0116", "массив " + id.getName() + " нельзя присваивать, возвращать или использовать как значение");
        }
    }

    private void checkFieldAccess(TreePath p, VariableElement field) {
        if (field.getConstantValue() == null) {
            rep.report(p, "GPU0115", field.getSimpleName());
        }
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree ms, Void v) {
        TreePath p = getCurrentPath();
        String name = ms.getIdentifier().toString();
        if (name.equals("class")) {
            rep.report(p, "GPU0107", "литерал класса");
            return null;
        }
        if (name.equals("length")) {
            TypeMirror et = trees.getTypeMirror(new TreePath(p, ms.getExpression()));
            if (et != null && et.getKind() == TypeKind.ARRAY) {
                return scan(ms.getExpression(), v);
            }
        }
        Element el = trees.getElement(p);
        if (el instanceof ExecutableElement) {
            // выбор метода: имя метода проверяет visitMethodInvocation, а квалификатор (например, "x" в "x".length()) - здесь
            return scan(ms.getExpression(), v);
        }
        if (el instanceof VariableElement ve) {
            if (ve.getKind() == ElementKind.ENUM_CONSTANT) {
                rep.report(p, "GPU0107", "константа перечисления " + name);
            } else if (ve.getKind() == ElementKind.FIELD) {
                checkFieldAccess(p, ve);
            }
        }
        // остальное - имена типов и пакетов (квалификаторы): в них нечего проверять
        return null;
    }

    // =============================================================================================
    // Присваивания
    // =============================================================================================

    @Override
    public Void visitAssignment(AssignmentTree t, Void v) {
        TreePath p = getCurrentPath();
        checkTarget(p, t.getVariable());
        scanTarget(t.getVariable(), v);
        return scan(t.getExpression(), v);
    }

    @Override
    public Void visitCompoundAssignment(CompoundAssignmentTree t, Void v) {
        TreePath p = getCurrentPath();
        checkTarget(p, t.getVariable());
        scanTarget(t.getVariable(), v);
        return scan(t.getExpression(), v);
    }

    @Override
    public Void visitUnary(UnaryTree t, Void v) {
        TreePath p = getCurrentPath();
        switch (t.getKind()) {
            case PREFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_INCREMENT, POSTFIX_DECREMENT -> {
                checkTarget(p, t.getExpression());
                scanTarget(t.getExpression(), v);
                return null;
            }
            default -> {
                return scan(t.getExpression(), v);
            }
        }
    }

    /** Путь к выражению без обрамляющих скобок. */
    private static TreePath unparen(TreePath tp) {
        TreePath x = tp;
        while (x.getLeaf() instanceof ParenthesizedTree pt) {
            x = new TreePath(x, pt.getExpression());
        }
        return x;
    }

    private void checkTarget(TreePath parent, ExpressionTree target) {
        TreePath tp = new TreePath(parent, target);
        TreePath stripped = unparen(tp);
        Tree t = stripped.getLeaf();
        if (t instanceof IdentifierTree id) {
            Element el = trees.getElement(stripped);
            if (el == null) {
                return;
            }
            if (el.getKind() == ElementKind.LOCAL_VARIABLE || el.getKind() == ElementKind.PARAMETER) {
                if (el.asType().getKind() == TypeKind.ARRAY) {
                    rep.report(tp, "GPU0116", "массив " + id.getName() + " нельзя переприсваивать");
                } else if (el.getKind() == ElementKind.PARAMETER && Ann.isConstantParam((VariableElement) el)) {
                    rep.report(tp, "GPU0118", id.getName());
                }
            } else {
                rep.report(tp, "GPU0115", id.getName());
            }
        } else if (!(t instanceof ArrayAccessTree)) {
            rep.report(tp, "GPU0115", t);
        }
    }

    /** Цель-массив проверяется общим {@link #visitArrayAccess}; имена переменных-целей отдельно не посещаются. */
    private void scanTarget(ExpressionTree target, Void v) {
        ExpressionTree t = target;
        while (t instanceof ParenthesizedTree pt) {
            t = pt.getExpression();
        }
        if (t instanceof ArrayAccessTree) {
            scan(target, v);
        }
    }

    // =============================================================================================
    // Вызовы
    // =============================================================================================

    @Override
    public Void visitMethodInvocation(MethodInvocationTree mit, Void v) {
        TreePath p = getCurrentPath();
        Element el = trees.getElement(new TreePath(p, mit.getMethodSelect()));
        if (el instanceof ExecutableElement callee) {
            checkCallee(p, callee);
        }
        scan(mit.getMethodSelect(), v);
        for (ExpressionTree a : mit.getArguments()) {
            scan(a, v);
        }
        return null;
    }

    private void checkCallee(TreePath p, ExecutableElement callee) {
        Element owner = callee.getEnclosingElement();
        String ownerName = owner instanceof TypeElement te ? te.getQualifiedName().toString() : "";
        String shown = ownerName.isEmpty() ? callee.getSimpleName().toString()
                : ((TypeElement) owner).getSimpleName() + "." + callee.getSimpleName();
        boolean isStatic = callee.getModifiers().contains(Modifier.STATIC);
        if (ownerName.equals(GPU_CLASS)) {
            if (!isStatic || Builtins.idOf(callee.getSimpleName().toString()) < 0) {
                rep.report(p, "GPU0112", shown);
            }
            return;
        }
        if (callee.getAnnotation(DeviceFunction.class) != null) {
            if (!isStatic) {
                rep.report(p, "GPU0107", "вызов экземплярного метода " + shown);
            } else if (trees.getPath(callee) == null) {
                rep.report(p, "GPU0114", shown);
            } else {
                callSites.add(new CallSite(callee, p));
            }
            return;
        }
        if (callee.getAnnotation(Kernel.class) != null || trees.getPath(callee) != null) {
            rep.report(p, "GPU0113", shown);
        } else {
            rep.report(p, "GPU0112", shown);
        }
    }
}
