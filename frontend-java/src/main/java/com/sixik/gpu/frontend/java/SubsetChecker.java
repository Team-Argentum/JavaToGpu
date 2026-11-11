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
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.CaseLabelTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionStatementTree;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    /** Методы вектора, допустимые в GPU-коде (все остальные - {@code GPU0112}). */
    private static final Set<String> VECTOR_METHODS = Set.of("splat", "add", "sub", "mul", "div", "rem", "neg", "and",
            "or", "xor", "not", "shl", "shr", "ushr", "shuffle");

    private final List<CallSite> callSites = new ArrayList<>();
    private final Aggregates agg;
    private final Set<String> checkedStructs = new HashSet<>();
    private TreePath methodPath;
    private ExecutableElement method;
    private boolean kernel;
    /** Проверяемый метод - {@code @Reduction}. */
    private boolean reduction;

    SubsetChecker(Trees trees, Reporter rep) {
        this.trees = trees;
        this.rep = rep;
        this.agg = new Aggregates(trees, null);
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
        method = e;
        reduction = e.getAnnotation(com.sixik.gpu.api.annotations.Reduction.class) != null;
        // свёртка - точка входа, как ядро: те же правила параметров, но результат - значение аккумулятора
        kernel = e.getAnnotation(Kernel.class) != null || reduction;
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
            rep.report(path, "GPU0001", name, reduction ? "Reduction" : kernel ? "Kernel" : "DeviceFunction");
        }
        if (!e.getTypeParameters().isEmpty() || e.isVarArgs() || !e.getThrownTypes().isEmpty()) {
            rep.report(path, "GPU0007", name);
        }
        int ret = JavaTypes.kindOf(e.getReturnType());
        if (reduction) {
            if (e.getAnnotation(Kernel.class) != null) {
                rep.report(path, "GPU0017", name);
            }
            if (!(ret == JavaTypes.K_INT || ret == JavaTypes.K_LONG || ret == JavaTypes.K_FLOAT
                    || ret == JavaTypes.K_DOUBLE)) {
                rep.report(path, "GPU0016", name);
            }
        } else if (kernel) {
            if (ret != JavaTypes.K_VOID) {
                rep.report(path, "GPU0002", name);
            }
        } else if (ret == JavaTypes.K_AGG) {
            checkAggType(path, e.getReturnType());
        } else if (!(ret == JavaTypes.K_VOID || ret == JavaTypes.K_BOOLEAN || ret == JavaTypes.K_INT
                || ret == JavaTypes.K_LONG || ret == JavaTypes.K_FLOAT || ret == JavaTypes.K_DOUBLE)) {
            rep.report(path, "GPU0006", e.getReturnType(), name);
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
            case JavaTypes.K_AGG -> checkAggType(pp, t);
            case JavaTypes.K_PTR -> {
                if (kernel) {
                    rep.report(pp, "GPU0197", method, t);
                }
            }
            case JavaTypes.K_BOOLEAN -> rep.report(pp, "GPU0004", pname);
            case JavaTypes.K_ARRAY -> {
                if (!JavaTypes.isJavaArray(t)) {
                    return; // GpuFloatArray и др.: элемент - всегда допустимый скаляр
                }
                TypeMirror comp = ((ArrayType) t).getComponentType();
                int ck = JavaTypes.kindOf(comp);
                if (ck == JavaTypes.K_ARRAY) {
                    rep.report(pp, "GPU0005", pname);
                } else if (ck == JavaTypes.K_AGG) {
                    checkAggType(pp, comp);
                } else if (!JavaTypes.isPrimitive(ck)) {
                    unsupportedType(pp, comp, pname);
                }
            }
            default -> unsupportedType(pp, t, pname);
        }
    }

    private void unsupportedType(TreePath at, TypeMirror t, String paramName) {
        rep.report(at, "GPU0003", t, paramName);
    }

    /**
     * Проверяет, что тип-агрегат пригоден: вектор всегда, struct - после проверки объявления (один раз на класс,
     * вместе с вложенными struct). Нарушения - {@code GPU0191} в месте первого использования.
     */
    private void checkAggType(TreePath at, TypeMirror t) {
        if (!Aggregates.isStruct(t) || !(((DeclaredType) t).asElement() instanceof TypeElement te)) {
            return;
        }
        if (!checkedStructs.add(te.getQualifiedName().toString())) {
            return;
        }
        for (String problem : agg.problems(te)) {
            rep.report(at, "GPU0191", te.getSimpleName(), problem);
        }
        for (VariableElement f : Aggregates.fields(te)) {
            if (Aggregates.isStruct(f.asType())) {
                checkAggType(at, f.asType());
            }
        }
    }

    /**
     * Значение struct обязано быть «свежим» (3.2): {@code new P(...)}, {@code x.copy()}, результат device-функции либо
     * условное выражение из таких. Присваивание из переменной, параметра или элемента массива на JVM создало бы
     * общую ссылку, а на GPU - копию.
     */
    private boolean isFresh(ExpressionTree e) {
        ExpressionTree x = e;
        while (x instanceof ParenthesizedTree pt) {
            x = pt.getExpression();
        }
        if (x instanceof NewClassTree) {
            return true;
        }
        if (x instanceof ConditionalExpressionTree ct) {
            return isFresh(ct.getTrueExpression()) && isFresh(ct.getFalseExpression());
        }
        if (x instanceof MethodInvocationTree mit) {
            Element el = trees.getElement(new TreePath(getCurrentPath() == null ? methodPath : getCurrentPath(), mit.getMethodSelect()));
            if (el instanceof ExecutableElement callee) {
                return callee.getSimpleName().contentEquals("copy") && Aggregates.isStruct(callee.getReturnType())
                        || callee.getAnnotation(DeviceFunction.class) != null;
            }
        }
        return false;
    }

    /** Требует свежее значение у выражения типа struct. */
    private void requireFresh(TreePath at, ExpressionTree e, TypeMirror type) {
        if (type != null && Aggregates.isStruct(type) && !isFresh(e)) {
            rep.report(new TreePath(at, e), "GPU0193", type);
        }
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
        TreePath p = getCurrentPath();
        TypeMirror type = Aggregates.typeOf(trees, p);
        if (t.getClassBody() == null && type != null && Aggregates.isAggregate(type)) {
            checkAggType(p, type);
            if (Aggregates.isStruct(type) && t.getArguments().isEmpty()) {
                rep.report(p, "GPU0192", type, "a no-arg constructor would leave object fields null; list all fields");
            }
            List<VariableElement> fields = Aggregates.isStruct(type) ? Aggregates.fields((TypeElement) ((DeclaredType) type).asElement()) : List.of();
            for (int i = 0; i < t.getArguments().size(); i++) {
                ExpressionTree a = t.getArguments().get(i);
                if (i < fields.size()) {
                    // поле-struct хранит ссылку, поэтому и аргумент обязан быть свежим значением
                    requireFresh(p, a, fields.get(i).asType());
                }
                scan(a, v);
            }
            return null;
        }
        if (t.getClassBody() == null && type != null && Aggregates.pointer(type) != null) {
            if (!Aggregates.pointer(type).ref()) {
                rep.report(p, "GPU0198", type, "an explicit pointer is created with of(array, offset), not a constructor");
            } else if (!(p.getParentPath().getLeaf() instanceof VariableTree vt && vt.getInitializer() == t)) {
                rep.report(p, "GPU0198", type, "a reference is created only in a local variable declaration");
            }
            for (ExpressionTree a : t.getArguments()) {
                scan(a, v);
            }
            return null;
        }
        if (t.getClassBody() != null) {
            rep.report(p, "GPU0108");
        } else {
            rep.report(p, "GPU0107", "the new operator creates an object");
        }
        return null;
    }

    @Override
    public Void visitConditionalExpression(ConditionalExpressionTree t, Void v) {
        TreePath p = getCurrentPath();
        TypeMirror type = Aggregates.typeOf(trees, p);
        if (type != null && JavaTypes.kindOf(type) == JavaTypes.K_PTR) {
            rep.report(p, "GPU0198", type, "a pointer cannot be chosen by a conditional expression; use if with assignment");
        }
        return super.visitConditionalExpression(t, v);
    }

    @Override
    public Void visitBinary(BinaryTree t, Void v) {
        TreePath p = getCurrentPath();
        if (t.getKind() == Tree.Kind.EQUAL_TO || t.getKind() == Tree.Kind.NOT_EQUAL_TO) {
            TypeMirror l = Aggregates.typeOf(trees, new TreePath(p, t.getLeftOperand()));
            TypeMirror r = Aggregates.typeOf(trees, new TreePath(p, t.getRightOperand()));
            if ((l != null && Aggregates.isAggregate(l)) || (r != null && Aggregates.isAggregate(r))) {
                rep.report(p, "GPU0196", l);
            } else if ((l != null && Aggregates.pointer(l) != null) || (r != null && Aggregates.pointer(r) != null)) {
                rep.report(p, "GPU0198", l, "pointers are not compared: compare values (p.get(0)) or indices");
            }
        }
        return super.visitBinary(t, v);
    }

    @Override
    public Void visitReturn(ReturnTree t, Void v) {
        TreePath p = getCurrentPath();
        if (t.getExpression() != null && method != null) {
            requireFresh(p, t.getExpression(), method.getReturnType());
        }
        return super.visitReturn(t, v);
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
        rep.report(getCurrentPath(), "GPU0110", "switch as an expression");
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
            rep.report(p, "GPU0109", "cast " + source + " to " + target);
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
                rep.report(p, "GPU0110", "key of type " + key);
            }
        }
        return super.visitSwitch(t, v);
    }

    @Override
    public Void visitCase(CaseTree t, Void v) {
        TreePath p = getCurrentPath();
        for (CaseLabelTree label : t.getLabels()) {
            if (label instanceof PatternCaseLabelTree) {
                rep.report(new TreePath(p, label), "GPU0110", "pattern matching in case");
            }
        }
        if (t.getGuard() != null) {
            rep.report(new TreePath(p, t.getGuard()), "GPU0110", "guard in case");
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
            } else if (k == JavaTypes.K_ARRAY && !JavaTypes.isJavaArray(t)) {
                rep.report(p, "GPU0116", "a " + ((DeclaredType) t).asElement().getSimpleName()
                        + " local variable is not supported in GPU code: use the parameter directly");
            } else if (k == JavaTypes.K_ARRAY) {
                checkLocalArray(p, vt, (ArrayType) t, isLocalAnnotated);
            } else if (k == JavaTypes.K_AGG) {
                checkAggType(p, t);
                if (isLocalAnnotated) {
                    rep.report(p, "GPU0120");
                }
                if (vt.getInitializer() != null) {
                    requireFresh(p, vt.getInitializer(), t);
                }
            } else if (k == JavaTypes.K_PTR) {
                checkPointerLocal(p, vt, t, isLocalAnnotated);
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

    /**
     * Локальная переменная-указатель (M9). Ссылка ({@code FloatPtr ...}) - владелец значения: на GPU это обычная
     * локаль, адрес которой передаётся в вызываемые функции, поэтому создаётся строго как {@code new FloatPtr(...)} и не
     * переприсваивается (иначе ссылка могла бы указывать то на одну, то на другую локаль). Явный указатель -
     * значение: инициализатор обязателен, переприсваивание разрешено.
     */
    private void checkPointerLocal(TreePath p, VariableTree vt, TypeMirror t, boolean isLocalAnnotated) {
        Aggregates.Ptr ptr = Aggregates.pointer(t);
        if (isLocalAnnotated) {
            rep.report(p, "GPU0120");
        }
        ExpressionTree init = vt.getInitializer();
        while (init instanceof ParenthesizedTree pt) {
            init = pt.getExpression();
        }
        if (ptr.ref() && !(init instanceof NewClassTree)) {
            rep.report(p, "GPU0198", vt.getName(), "reference " + t + " is created only by the expression 'new " + t
                    + "(...)' in a variable declaration");
        } else if (!ptr.ref() && init == null) {
            rep.report(p, "GPU0198", vt.getName(), "a pointer must be initialized at declaration");
        }
    }

    private void checkLocalArray(TreePath p, VariableTree vt, ArrayType t, boolean isLocalAnnotated) {
        TypeMirror comp = t.getComponentType();
        int ck = JavaTypes.kindOf(comp);
        if (ck == JavaTypes.K_ARRAY) {
            rep.report(p, "GPU0005", vt.getName());
            return;
        }
        if (ck == JavaTypes.K_AGG) {
            rep.report(p, "GPU0117", "local array of objects " + comp + ": on the JVM its elements are null, and on the GPU zeros");
            return;
        }
        if (!JavaTypes.isPrimitive(ck)) {
            rep.report(p, "GPU0119", t);
            return;
        }
        if (!(vt.getInitializer() instanceof NewArrayTree)) {
            rep.report(p, "GPU0116", "local array " + vt.getName()
                    + " must be created as 'new " + comp + "[N]' (copies of array references are forbidden)");
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
            rep.report(p, "GPU0117", "array creation outside a local variable declaration");
            return null;
        }
        if (na.getInitializers() != null) {
            rep.report(p, "GPU0117", "initializer of the form {...}");
        } else if (na.getDimensions().size() != 1) {
            rep.report(p, "GPU0005", vt.getName());
        } else {
            Long size = ConstEval.evalIntegral(trees, new TreePath(p, na.getDimensions().get(0)));
            if (size == null || size <= 0 || size > Integer.MAX_VALUE) {
                rep.report(p, "GPU0117", "size must be a positive constant");
            }
        }
        return null;
    }

    @Override
    public Void visitArrayAccess(ArrayAccessTree t, Void v) {
        TreePath p = getCurrentPath();
        ExpressionTree base = t.getExpression();
        if (!(base instanceof IdentifierTree)) {
            rep.report(p, "GPU0116", "only an array given by name can be indexed (multidimensional arrays and call results are not supported)");
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
                } else if (Aggregates.residentArray(el.asType()) >= 0) {
                    checkResidentContext(p, id);
                } else if (Aggregates.pointer(el.asType()) != null) {
                    checkPointerContext(p, id, Aggregates.pointer(el.asType()));
                }
            }
            case FIELD -> checkFieldAccess(p, (VariableElement) el);
            case ENUM_CONSTANT -> rep.report(p, "GPU0107", "enum constant " + id.getName());
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
            rep.report(p, "GPU0116", "array " + id.getName() + " cannot be assigned, returned or used as a value");
        }
    }

    /**
     * Где может стоять имя {@code GpuFloatArray} и других массивов с резидентностью: квалификатор {@code get/set/length}
     * либо аргумент вызова (device-функции).
     */
    private void checkResidentContext(TreePath p, IdentifierTree id) {
        Tree parent = p.getParentPath().getLeaf();
        boolean ok = (parent instanceof MemberSelectTree ms && ms.getExpression() == id)
                || (parent instanceof MethodInvocationTree mi && mi.getArguments().contains(id));
        if (!ok) {
            rep.report(p, "GPU0116", "array " + id.getName() + " is used via get/set/length or passed to @DeviceFunction");
        }
    }

    /**
     * Где может стоять имя указателя (M9). Ссылка ({@code FloatPtr ...}): только квалификатор {@code .value} либо
     * аргумент device-функции. Явный указатель: квалификатор метода ({@code p.get(i)}), аргумент device-функции,
     * инициализатор и правая часть присваивания указателю. Всё остальное (возврат, арифметика, условное выражение,
     * элемент массива) для указателя не имеет смысла и на JVM, и на GPU.
     */
    private void checkPointerContext(TreePath p, IdentifierTree id, Aggregates.Ptr ptr) {
        Tree parent = p.getParentPath().getLeaf();
        boolean ok;
        if (parent instanceof MethodInvocationTree mi && mi.getArguments().contains(id)) {
            Element callee = trees.getElement(new TreePath(p.getParentPath(), mi.getMethodSelect()));
            // указатель можно отдать device-функции и атомику Gpu.atomic*; ссылку FloatPtr - только device-функции
            ok = callee != null && (callee.getAnnotation(DeviceFunction.class) != null
                    || (!ptr.ref() && callee.getEnclosingElement() instanceof TypeElement owner
                    && owner.getQualifiedName().contentEquals(GPU_CLASS)));
        } else if (parent instanceof MemberSelectTree ms && ms.getExpression() == id) {
            ok = true; // у ссылки есть только поле value, методы отвергает checkPointerMethod (GPU0112)
        } else {
            ok = !ptr.ref() && ((parent instanceof VariableTree vt && vt.getInitializer() == id)
                    || (parent instanceof AssignmentTree at && at.getExpression() == id));
        }
        if (!ok) {
            rep.report(p, "GPU0198", id.getName(), ptr.ref()
                    ? "a reference can be read and changed via .value and passed to @DeviceFunction"
                    : "a pointer is used via get/set/plus, passed to @DeviceFunction or assigned to a pointer");
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
            rep.report(p, "GPU0107", "class literal");
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
        if (el instanceof VariableElement fe && fe.getKind() == ElementKind.FIELD && !fe.getModifiers().contains(Modifier.STATIC)) {
            TypeMirror qt = Aggregates.typeOf(trees, new TreePath(p, ms.getExpression()));
            if (qt != null && Aggregates.isAggregate(qt)) {
                checkAggType(p, qt);
                return scan(ms.getExpression(), v);
            }
            if (qt != null && Aggregates.pointer(qt) != null) {
                requirePointerName(p, ms);
                return scan(ms.getExpression(), v);
            }
        }
        if (el instanceof VariableElement ve) {
            if (ve.getKind() == ElementKind.ENUM_CONSTANT) {
                rep.report(p, "GPU0107", "enum constant " + name);
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
        requireFresh(p, t.getExpression(), Aggregates.typeOf(trees, new TreePath(p, t.getVariable())));
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
                    rep.report(tp, "GPU0116", "array " + id.getName() + " cannot be reassigned");
                } else if (el.getKind() == ElementKind.PARAMETER && Ann.isConstantParam((VariableElement) el)) {
                    rep.report(tp, "GPU0118", id.getName());
                } else if (Aggregates.pointer(el.asType()) != null) {
                    checkPointerAssignment(parent, tp, id, Aggregates.pointer(el.asType()));
                }
            } else {
                rep.report(tp, "GPU0115", id.getName());
            }
        } else if (t instanceof MemberSelectTree ms) {
            TypeMirror qt = Aggregates.typeOf(trees, new TreePath(stripped, ms.getExpression()));
            if (qt != null && Aggregates.pointer(qt) != null) {
                requirePointerName(tp, ms); // запись через ссылку: *p = v; допустима и у параметра
            } else {
                checkFieldTarget(tp, stripped, ms);
            }
        } else if (t instanceof ArrayAccessTree) {
            checkConstantWrite(tp, stripped);
        } else {
            rep.report(tp, "GPU0115", t);
        }
    }

    /**
     * Присваивание самому указателю. Ссылка {@code FloatPtr} - владелец или переданный адрес, менять её нельзя;
     * явный указатель - значение, ему можно присвоить новый указатель, но только стейтментом (значение
     * присваивания-выражения указателя фронтенд не строит).
     */
    private void checkPointerAssignment(TreePath parent, TreePath at, IdentifierTree id, Aggregates.Ptr ptr) {
        if (ptr.ref()) {
            rep.report(at, "GPU0198", id.getName(), "a reference is not reassigned: change the value via .value");
        } else if (!(parent.getParentPath() != null && parent.getParentPath().getLeaf() instanceof ExpressionStatementTree)) {
            rep.report(at, "GPU0198", id.getName(), "assignment to a pointer is allowed only as a standalone statement");
        }
    }

    /**
     * Запись в элемент массива {@code @Global(constant = true)} недопустима: он лежит в {@code __constant}.
     *
     * @param shown    путь цели для позиции диагностики
     * @param stripped путь к {@link ArrayAccessTree} цели (без скобок)
     */
    private void checkConstantWrite(TreePath shown, TreePath stripped) {
        ArrayAccessTree aa = (ArrayAccessTree) stripped.getLeaf();
        if (aa.getExpression() instanceof IdentifierTree id) {
            Element el = trees.getElement(new TreePath(stripped, id));
            if (el != null && el.getKind() == ElementKind.PARAMETER && Ann.isConstantParam((VariableElement) el)) {
                rep.report(shown, "GPU0122", id.getName());
            }
        }
    }

    /**
     * Запись в поле struct: цепочка {@code a.b.c} должна начинаться с локальной переменной-struct или элемента массива
     * struct. Параметр-struct на GPU передаётся копией, а на JVM - ссылкой, поэтому его поля менять нельзя (3.2).
     */
    private void checkFieldTarget(TreePath shown, TreePath stripped, MemberSelectTree ms) {
        ExpressionTree root = ms;
        while (root instanceof MemberSelectTree m) {
            TypeMirror qt = Aggregates.typeOf(trees, new TreePath(stripped, m.getExpression()));
            if (qt == null || !Aggregates.isStruct(qt)) {
                rep.report(shown, "GPU0194", ms.getIdentifier(), "a field can be changed only on a struct (vectors are immutable)");
                return;
            }
            root = m.getExpression();
            while (root instanceof ParenthesizedTree pt) {
                root = pt.getExpression();
            }
        }
        if (root instanceof IdentifierTree id) {
            Element el = trees.getElement(new TreePath(stripped, id));
            if (el == null || el.getKind() != ElementKind.LOCAL_VARIABLE) {
                rep.report(shown, "GPU0194", ms.getIdentifier(),
                        "fields can be changed on a local struct variable and on a struct array element, but not on a parameter");
            }
        } else if (!(root instanceof ArrayAccessTree)) {
            rep.report(shown, "GPU0194", ms.getIdentifier(), "the write target must be a local variable or an array element");
        }
    }

    /** {@code x.value} у ссылки: {@code x} обязан быть именем переменной или параметра (а не результатом выражения). */
    private void requirePointerName(TreePath shown, MemberSelectTree ms) {
        ExpressionTree q = ms.getExpression();
        while (q instanceof ParenthesizedTree pt) {
            q = pt.getExpression();
        }
        if (!(q instanceof IdentifierTree)) {
            rep.report(shown, "GPU0198", ms.getIdentifier(), "the value field is read and changed only on a variable or parameter");
        }
    }

    /** Цель-массив проверяется общим {@link #visitArrayAccess}; имена переменных-целей отдельно не посещаются. */
    private void scanTarget(ExpressionTree target, Void v) {
        ExpressionTree t = target;
        while (t instanceof ParenthesizedTree pt) {
            t = pt.getExpression();
        }
        if (t instanceof ArrayAccessTree || t instanceof MemberSelectTree) {
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

    /** Методы вектора ({@code add, withX, shuffle ...}) и {@code copy()} struct. */
    private void checkAggregateMethod(TreePath p, ExecutableElement callee, TypeElement owner) {
        String name = callee.getSimpleName().toString();
        String shown = owner.getSimpleName() + "." + name;
        Aggregates.Vec vec = Aggregates.vector(owner.asType());
        if (vec == null) {
            if (!(name.equals("copy") && callee.getParameters().isEmpty())) {
                rep.report(p, "GPU0112", shown);
            } else {
                checkAggType(p, owner.asType());
            }
            return;
        }
        boolean with = name.startsWith("with") && name.length() > 4;
        if (!VECTOR_METHODS.contains(name) && !with) {
            rep.report(p, "GPU0112", shown);
            return;
        }
        if (name.equals("shuffle")) {
            MethodInvocationTree mit = (MethodInvocationTree) p.getLeaf();
            for (ExpressionTree a : mit.getArguments()) {
                Long idx = ConstEval.evalIntegral(trees, new TreePath(p, a));
                if (idx == null || idx < 0 || idx >= vec.lanes()) {
                    rep.report(new TreePath(p, a), "GPU0195", vec.lanes() - 1);
                }
            }
        }
    }

    /** Методы явного указателя: {@code of} (создание из массива), {@code get}, {@code set}, {@code plus}. */
    private void checkPointerMethod(TreePath p, ExecutableElement callee, TypeElement owner) {
        String name = callee.getSimpleName().toString();
        boolean isStatic = callee.getModifiers().contains(Modifier.STATIC);
        boolean known = isStatic ? name.equals("of") : name.equals("get") || name.equals("set") || name.equals("plus");
        if (!known) {
            rep.report(p, "GPU0112", owner.getSimpleName() + "." + name);
            return;
        }
        if (isStatic) {
            MethodInvocationTree mit = (MethodInvocationTree) p.getLeaf();
            if (!(mit.getArguments().get(0) instanceof IdentifierTree)) {
                rep.report(new TreePath(p, mit.getArguments().get(0)), "GPU0116",
                        "in " + owner.getSimpleName() + ".of the array is given by a parameter name or a local variable");
            }
        }
    }

    private void checkCallee(TreePath p, ExecutableElement callee) {
        Element owner = callee.getEnclosingElement();
        if (owner instanceof TypeElement rte && Aggregates.residentArray(rte.asType()) >= 0) {
            String name = callee.getSimpleName().toString();
            boolean known = !callee.getModifiers().contains(Modifier.STATIC)
                    && (name.equals("get") || name.equals("set") || name.equals("length"));
            if (!known) {
                rep.report(p, "GPU0112", rte.getSimpleName() + "." + name);
            }
            return;
        }
        if (owner instanceof TypeElement pte && Aggregates.pointer(pte.asType()) != null) {
            checkPointerMethod(p, callee, pte);
            return;
        }
        if (owner instanceof TypeElement ote && Aggregates.isAggregate(ote.asType())) {
            checkAggregateMethod(p, callee, ote);
            return;
        }
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
                rep.report(p, "GPU0107", "call of instance method " + shown);
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
