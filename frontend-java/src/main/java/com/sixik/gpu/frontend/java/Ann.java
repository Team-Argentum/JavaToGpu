package com.sixik.gpu.frontend.java;

import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Local;
import com.sixik.gpu.ir.AddressSpace;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/**
 * Чтение аннотаций языка ({@code @Global}, {@code @Local}) с исходных деревьев и элементов.
 *
 * <p>Аннотации параметров доступны через элемент сразу, а аннотации локальных переменных javac разбирает только при
 * атрибуции тела, поэтому для них аннотация ищется по дереву объявления (имя типа аннотации разрешается javac).</p>
 */
final class Ann {

    private Ann() {
    }

    /**
     * Проверяет наличие аннотации на объявлении переменной по дереву.
     *
     * @param trees   API деревьев
     * @param varPath путь к {@code VariableTree}
     * @param fqn     полное имя типа аннотации
     * @return {@code true}, если аннотация указана в модификаторах объявления
     */
    static boolean onVariable(Trees trees, TreePath varPath, String fqn) {
        VariableTree vt = (VariableTree) varPath.getLeaf();
        TreePath mods = new TreePath(varPath, vt.getModifiers());
        for (AnnotationTree a : vt.getModifiers().getAnnotations()) {
            TreePath at = new TreePath(mods, a);
            Element e = trees.getElement(new TreePath(at, a.getAnnotationType()));
            if (e instanceof TypeElement te && te.getQualifiedName().contentEquals(fqn)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Адресное пространство массива-параметра: {@code @Local} - local; {@code @Global(constant = true)} - constant;
     * иначе (в том числе без аннотации) - global.
     *
     * @param param элемент параметра
     * @return {@link AddressSpace}
     */
    static int paramSpace(VariableElement param) {
        if (param.getAnnotation(Local.class) != null) {
            return AddressSpace.LOCAL;
        }
        Global g = param.getAnnotation(Global.class);
        return g != null && g.constant() ? AddressSpace.CONSTANT : AddressSpace.GLOBAL;
    }

    /**
     * @param param элемент параметра
     * @return {@code true} для {@code @Global(constant = true)}
     */
    static boolean isConstantParam(VariableElement param) {
        Global g = param.getAnnotation(Global.class);
        return g != null && g.constant();
    }

    /**
     * @param space {@link AddressSpace}
     * @return имя адресного пространства для сообщений
     */
    static String spaceName(int space) {
        return switch (space) {
            case AddressSpace.GLOBAL -> "global";
            case AddressSpace.LOCAL -> "local";
            case AddressSpace.CONSTANT -> "constant";
            default -> "private";
        };
    }
}
