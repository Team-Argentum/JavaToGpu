package com.sixik.gpu.frontend.java;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/**
 * Сигнатура GPU-функции в терминах видов Java ({@link JavaTypes}) и адресных пространств массивов.
 *
 * @param retKind    вид результата
 * @param paramKinds вид каждого параметра ({@link JavaTypes#K_ARRAY} для массивов)
 * @param elemKinds  вид элемента для массивов, иначе {@code -1}
 * @param spaces     адресное пространство для массивов, иначе {@code -1}
 */
record FnSig(int retKind, int[] paramKinds, int[] elemKinds, int[] spaces) {

    /**
     * Строит сигнатуру по элементу метода. Метод должен был пройти проверку сигнатуры
     * ({@link SubsetChecker}): типы параметров - примитивы либо одномерные массивы примитивов.
     *
     * @param e элемент метода
     * @return сигнатура
     */
    static FnSig of(ExecutableElement e) {
        List<? extends VariableElement> ps = e.getParameters();
        int n = ps.size();
        int[] kinds = new int[n];
        int[] elems = new int[n];
        int[] spaces = new int[n];
        for (int i = 0; i < n; i++) {
            TypeMirror t = ps.get(i).asType();
            int k = JavaTypes.kindOf(t);
            kinds[i] = k;
            if (k == JavaTypes.K_ARRAY) {
                elems[i] = JavaTypes.kindOf(((ArrayType) t).getComponentType());
                spaces[i] = Ann.paramSpace(ps.get(i));
            } else {
                elems[i] = -1;
                spaces[i] = -1;
            }
        }
        return new FnSig(JavaTypes.kindOf(e.getReturnType()), kinds, elems, spaces);
    }

    /**
     * @param other другая сигнатура
     * @return {@code true}, если результат, виды параметров, виды элементов и адресные пространства совпадают
     */
    boolean sameAs(FnSig other) {
        return retKind == other.retKind && java.util.Arrays.equals(paramKinds, other.paramKinds)
                && java.util.Arrays.equals(elemKinds, other.elemKinds) && java.util.Arrays.equals(spaces, other.spaces);
    }
}
