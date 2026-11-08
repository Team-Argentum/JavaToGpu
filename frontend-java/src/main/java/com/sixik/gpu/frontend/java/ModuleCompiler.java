package com.sixik.gpu.frontend.java;

import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.frontend.java.FunctionLowerer.FnInfo;
import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Validator;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сборка одного IR-модуля ядра: проверка подмножества, транзитивное замыкание вызовов, порядок функций,
 * построение IR и его проверка валидатором {@code ir}.
 *
 * <p><b>Состав и порядок модуля.</b> Модуль содержит ядро, все достижимые device-функции и функции-варианты
 * ({@code fallback}) вместе с их замыканиями. Функции идут от листьев вызовов к корню (builder требует, чтобы
 * вызываемая функция была создана раньше вызывающей), а <b>ядро - всегда последняя функция</b> модуля: так рантайм
 * находит точку входа. Варианты {@code fallback} - тоже функции вида {@code KERNEL}, потому что их запускает рантайм.</p>
 *
 * <p>Результаты проверки методов кэшируются: каждый метод проверяется и получает диагностики один раз, сколько бы
 * ядер его ни использовало.</p>
 */
final class ModuleCompiler {

    /**
     * Готовое ядро.
     *
     * @param module   модуль IR
     * @param kernelId хэш модуля без отладочной секции (64 символа)
     * @param bytes    бинарное представление модуля
     */
    record Compiled(IrModule module, String kernelId, byte[] bytes) {
    }

    private record Checked(boolean valid, TreePath path, List<SubsetChecker.CallSite> calls, KernelAttrs attrs) {
    }

    private final Trees trees;
    private final Reporter rep;
    private final SubsetChecker checker;
    private final Map<ExecutableElement, Checked> cache = new HashMap<>();
    private final Set<Tree> reportedCycles = new HashSet<>();

    ModuleCompiler(Trees trees, Reporter rep) {
        this.trees = trees;
        this.rep = rep;
        this.checker = new SubsetChecker(trees, rep);
    }

    /**
     * Проверяет метод на соответствие подмножеству (с кэшем) без построения IR.
     *
     * @param e метод с {@code @Kernel} или {@code @DeviceFunction}
     * @return {@code true}, если метод допустим
     */
    boolean check(ExecutableElement e) {
        return checked(e).valid();
    }

    private Checked checked(ExecutableElement e) {
        Checked c = cache.get(e);
        if (c == null) {
            TreePath path = trees.getPath(e);
            if (path == null) {
                c = new Checked(false, null, List.of(), null);
            } else {
                boolean ok = checker.check(path, e);
                List<SubsetChecker.CallSite> calls = checker.callSites();
                KernelAttrs attrs = null;
                if (e.getAnnotation(Kernel.class) != null) {
                    attrs = KernelAttrs.parse(e, path, rep);
                    ok &= attrs != null;
                }
                c = new Checked(ok, path, calls, attrs);
            }
            cache.put(e, c);
        }
        return c;
    }

    /**
     * Строит модуль ядра.
     *
     * @param kernel метод с {@code @Kernel}
     * @return готовое ядро либо {@code null}, если найдены ошибки (диагностики уже выданы)
     */
    Compiled compile(ExecutableElement kernel) {
        List<ExecutableElement> order = new ArrayList<>();
        Map<ExecutableElement, ExecutableElement> fallbacks = new HashMap<>();
        if (!collect(kernel, new ArrayDeque<>(), order, fallbacks)) {
            return null;
        }
        IrBuilder b = new IrBuilder();
        Map<ExecutableElement, FnInfo> fns = new HashMap<>();
        int errorsBefore = rep.errorCount();
        for (ExecutableElement e : order) {
            Checked c = cache.get(e);
            ExecutableElement fb = fallbacks.get(e);
            int fbIndex = fb == null ? -1 : fns.get(fb).index;
            FunctionLowerer.lower(trees, rep, b, fns, c.path(), e, c.attrs(), fbIndex);
        }
        if (rep.errorCount() > errorsBefore) {
            return null;
        }
        IrModule m = b.build();
        if (!validate(m, cache.get(kernel).path(), kernel.getSimpleName().toString())) {
            return null;
        }
        return new Compiled(m, IrBinary.kernelId(m), IrBinary.write(m));
    }

    /**
     * Проверяет построенный модуль валидатором {@code ir} и выдаёт его диагностики: ошибки - {@code GPU0901}
     * (внутренняя ошибка фронтенда или нарушение контракта IR), предупреждения - {@code GPU0903}.
     *
     * @param m    модуль
     * @param path путь к методу ядра (место диагностик)
     * @param name имя ядра для сообщений
     * @return {@code true}, если ошибок нет
     */
    boolean validate(IrModule m, TreePath path, String name) {
        Validator.Report report = Validator.validate(m);
        for (Diagnostic d : report.diagnostics()) {
            rep.report(path, d.isError() ? "GPU0901" : "GPU0903", name, d);
        }
        return !report.hasErrors();
    }

    /** Обход замыкания: функции попадают в {@code order} после всех, от кого они зависят. */
    private boolean collect(ExecutableElement e, Deque<ExecutableElement> stack, List<ExecutableElement> order,
                            Map<ExecutableElement, ExecutableElement> fallbacks) {
        if (order.contains(e)) {
            return true;
        }
        Checked c = checked(e);
        boolean ok = c.valid();
        stack.push(e);
        for (SubsetChecker.CallSite cs : c.calls()) {
            if (stack.contains(cs.callee())) {
                if (reportedCycles.add(cs.at().getLeaf())) {
                    rep.report(cs.at(), "GPU0105", chain(stack, cs.callee()));
                }
                ok = false;
            } else if (!collect(cs.callee(), stack, order, fallbacks)) {
                ok = false;
            }
        }
        if (c.attrs() != null) {
            Kernel k = e.getAnnotation(Kernel.class);
            if (!k.fallback().isEmpty()) {
                ExecutableElement fb = findFallback(e, k.fallback(), c.path());
                if (fb == null) {
                    ok = false;
                } else if (stack.contains(fb)) {
                    // цикл сообщается один раз - на первом ядре цепочки (оно уже лежит в стеке)
                    if (reportedCycles.add(checked(fb).path().getLeaf())) {
                        rep.report(checked(fb).path(), "GPU0014", chain(stack, fb));
                    }
                    ok = false;
                } else {
                    fallbacks.put(e, fb);
                    if (!collect(fb, stack, order, fallbacks)) {
                        ok = false;
                    }
                }
            }
        }
        stack.pop();
        if (ok && !order.contains(e)) {
            order.add(e);
        }
        return ok;
    }

    private static String chain(Deque<ExecutableElement> stack, ExecutableElement closing) {
        List<String> names = stack.stream().map(x -> x.getSimpleName().toString()).collect(Collectors.toList());
        java.util.Collections.reverse(names);
        names.add(closing.getSimpleName().toString());
        return String.join(" -> ", names);
    }

    private ExecutableElement findFallback(ExecutableElement kernel, String name, TreePath path) {
        FnSig sig = FnSig.of(kernel);
        boolean sameNameFound = false;
        for (Element m : kernel.getEnclosingElement().getEnclosedElements()) {
            if (m.getKind() == ElementKind.METHOD && m.getSimpleName().contentEquals(name)
                    && m.getAnnotation(Kernel.class) != null) {
                ExecutableElement ee = (ExecutableElement) m;
                sameNameFound = true;
                if (FnSig.of(ee).sameAs(sig)) {
                    return ee;
                }
            }
        }
        if (sameNameFound) {
            rep.report(path, "GPU0011", name, kernel.getSimpleName());
        } else {
            rep.report(path, "GPU0010", name, kernel.getSimpleName());
        }
        return null;
    }
}
