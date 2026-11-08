package com.sixik.gpu.frontend.java;

import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;

/**
 * Выдача диагностик {@code GPU####} в javac с точной позицией в исходнике (через {@code Trees.printMessage}) и учёт
 * числа ошибок. Тексты берутся из {@link Diag}.
 *
 * <p>Если у места нет дерева (например, элемент из class-файла), диагностика привязывается к элементу.
 * Отчёты копятся и в списке {@link #reported()}, чтобы тесты и вызывающий код видели, что именно выдано.</p>
 */
final class Reporter {

    private final Trees trees;
    private final javax.annotation.processing.Messager messager;
    private final List<String> reported = new ArrayList<>();
    private int errors;

    Reporter(Trees trees, javax.annotation.processing.Messager messager) {
        this.trees = trees;
        this.messager = messager;
    }

    /**
     * Сообщает о нарушении на конкретном дереве.
     *
     * @param at   путь к дереву, на котором показывается диагностика
     * @param code код из таблицы диагностик
     * @param args значения для подстановки в сообщение
     */
    void report(TreePath at, String code, Object... args) {
        report(at.getLeaf(), at, code, args);
    }

    /**
     * Сообщает о нарушении на указанном поддереве того же пути (например, на параметре метода).
     *
     * @param leaf поддерево, на которое указывает диагностика
     * @param base путь, из которого берётся единица компиляции
     * @param code код из таблицы диагностик
     * @param args значения для подстановки
     */
    void report(Tree leaf, TreePath base, String code, Object... args) {
        Diag.Entry e = Diag.entry(code);
        String text = Diag.format(code, args);
        Diagnostic.Kind kind = e.error() ? Diagnostic.Kind.ERROR : Diagnostic.Kind.WARNING;
        if (e.error()) {
            errors++;
        }
        reported.add(code);
        trees.printMessage(kind, text, leaf, base.getCompilationUnit());
    }

    /**
     * Сообщает о нарушении, привязанном к элементу (когда дерева нет).
     *
     * @param element элемент, к которому относится диагностика
     * @param code    код из таблицы диагностик
     * @param args    значения для подстановки
     */
    void report(Element element, String code, Object... args) {
        Diag.Entry e = Diag.entry(code);
        String text = Diag.format(code, args);
        if (e.error()) {
            errors++;
        }
        reported.add(code);
        messager.printMessage(e.error() ? Diagnostic.Kind.ERROR : Diagnostic.Kind.WARNING, text, element);
    }

    /**
     * @return число выданных ошибок (предупреждения не считаются)
     */
    int errorCount() {
        return errors;
    }

    /**
     * @return коды выданных диагностик по порядку выдачи
     */
    List<String> reported() {
        return reported;
    }
}
