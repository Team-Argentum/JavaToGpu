package com.sixik.gpu.frontend.java;

import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Local;
import com.sixik.gpu.ir.AddressSpace;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Чтение аннотаций языка ({@code @Global}, {@code @Local}, атрибуты {@code @Kernel}) с исходных деревьев и элементов.
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
     * Явно заданные элементы аннотации в форме, которую ждёт
     * {@link com.sixik.gpu.frontend.common.KernelAttributes#parse}: числа и строки - как есть, константа перечисления -
     * её имя, массив - {@link List}. Значения по умолчанию в карту не попадают.
     *
     * @param element аннотированный элемент
     * @param fqn     полное имя типа аннотации
     * @return карта «имя элемента → значение» либо {@code null}, если аннотации на элементе нет
     */
    static Map<String, Object> explicitValues(Element element, String fqn) {
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            if (!((TypeElement) am.getAnnotationType().asElement()).getQualifiedName().contentEquals(fqn)) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : am.getElementValues().entrySet()) {
                values.put(e.getKey().getSimpleName().toString(), plain(e.getValue().getValue()));
            }
            return values;
        }
        return null;
    }

    private static Object plain(Object v) {
        if (v instanceof VariableElement ve) {
            return ve.getSimpleName().toString();
        }
        if (v instanceof List<?> list) {
            List<Object> r = new ArrayList<>();
            for (Object o : list) {
                r.add(plain(((AnnotationValue) o).getValue()));
            }
            return r;
        }
        return v;
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
