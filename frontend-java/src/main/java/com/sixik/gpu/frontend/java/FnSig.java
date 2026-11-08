package com.sixik.gpu.frontend.java;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;
import java.util.Arrays;
import java.util.List;

/**
 * Сигнатура GPU-функции в терминах видов Java ({@link JavaTypes}) и адресных пространств массивов.
 *
 * @param retKind    вид результата
 * @param paramKinds вид каждого параметра ({@link JavaTypes#K_ARRAY} для массивов)
 * @param elemKinds  вид элемента для массивов, иначе {@code -1}
 * @param spaces     адресное пространство для массивов, иначе {@code -1}
 * @param retAgg     тип Java результата, если он вектор или struct ({@link JavaTypes#K_AGG}), иначе {@code null}
 * @param aggs       по параметру: тип Java значения-агрегата (параметр-вектор/struct либо элемент массива векторов/struct),
 *                   иначе {@code null}
 */
record FnSig(int retKind, int[] paramKinds, int[] elemKinds, int[] spaces, TypeMirror retAgg, TypeMirror[] aggs) {

    /**
     * Строит сигнатуру по элементу метода. Метод должен был пройти проверку сигнатуры
     * ({@link SubsetChecker}): типы параметров - примитивы, векторы, struct либо одномерные массивы таких типов.
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
        TypeMirror[] aggs = new TypeMirror[n];
        for (int i = 0; i < n; i++) {
            TypeMirror t = ps.get(i).asType();
            int k = JavaTypes.kindOf(t);
            kinds[i] = k;
            if (k == JavaTypes.K_ARRAY) {
                TypeMirror comp = ((ArrayType) t).getComponentType();
                elems[i] = JavaTypes.kindOf(comp);
                spaces[i] = Ann.paramSpace(ps.get(i));
                if (elems[i] == JavaTypes.K_AGG) {
                    aggs[i] = comp;
                }
            } else if (k == JavaTypes.K_PTR) {
                Aggregates.Ptr ptr = Aggregates.pointer(t);
                elems[i] = ptr.elemKind();
                spaces[i] = ptr.space();
            } else {
                elems[i] = -1;
                spaces[i] = -1;
                if (k == JavaTypes.K_AGG) {
                    aggs[i] = t;
                }
            }
        }
        int ret = JavaTypes.kindOf(e.getReturnType());
        return new FnSig(ret, kinds, elems, spaces, ret == JavaTypes.K_AGG ? e.getReturnType() : null, aggs);
    }

    /**
     * @param other другая сигнатура
     * @return {@code true}, если результат, виды параметров, виды элементов, типы агрегатов и адресные пространства совпадают
     */
    boolean sameAs(FnSig other) {
        if (retKind != other.retKind || !Arrays.equals(paramKinds, other.paramKinds)
                || !Arrays.equals(elemKinds, other.elemKinds) || !Arrays.equals(spaces, other.spaces)
                || !String.valueOf(retAgg).equals(String.valueOf(other.retAgg))) {
            return false;
        }
        for (int i = 0; i < aggs.length; i++) {
            if (!String.valueOf(aggs[i]).equals(String.valueOf(other.aggs[i]))) {
                return false;
            }
        }
        return true;
    }
}
