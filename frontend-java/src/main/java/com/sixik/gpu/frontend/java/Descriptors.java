package com.sixik.gpu.frontend.java;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * JVM-дескрипторы методов ядер для индекса {@code kernels.idx} (формат индекса -
 * {@link com.sixik.gpu.frontend.common.KernelResources}). javac-специфичная часть: дескриптор строится по элементу
 * javac, а не по class-файлу.
 */
final class Descriptors {

    private Descriptors() {
    }

    /**
     * JVM-дескриптор метода ядра. Параметры ядра - примитивы, векторы, struct и одномерные массивы таких типов, результат - void.
     *
     * @param e метод, прошедший проверку сигнатуры
     * @return дескриптор вида {@code ([F[F)V}
     */
    static String descriptor(ExecutableElement e) {
        StringBuilder sb = new StringBuilder("(");
        for (VariableElement p : e.getParameters()) {
            sb.append(typeDescriptor(p.asType()));
        }
        return sb.append(')').append(typeDescriptor(e.getReturnType())).toString();
    }

    private static String typeDescriptor(TypeMirror t) {
        return switch (t.getKind()) {
            case VOID -> "V";
            case BOOLEAN -> "Z";
            case BYTE -> "B";
            case SHORT -> "S";
            case CHAR -> "C";
            case INT -> "I";
            case LONG -> "J";
            case FLOAT -> "F";
            case DOUBLE -> "D";
            case ARRAY -> "[" + typeDescriptor(((ArrayType) t).getComponentType());
            case DECLARED -> "L" + binaryName((TypeElement) ((DeclaredType) t).asElement()).replace('.', '/') + ";";
            default -> throw new IllegalArgumentException("type " + t + " has no kernel descriptor");
        };
    }

    /**
     * Запись таблицы JDK-интринсиков (18.2-S6) для метода: ключ тот же, что у {@code frontend-asm} в байткоде.
     *
     * @param e вызываемый метод
     * @return запись либо {@code null}, если метод не интринсик (или это не метод JDK)
     */
    static com.sixik.gpu.frontend.common.JdkIntrinsics.Intrinsic jdkIntrinsic(ExecutableElement e) {
        if (!(e.getEnclosingElement() instanceof TypeElement owner)
                || !owner.getQualifiedName().toString().startsWith("java.")) {
            return null;
        }
        String desc;
        try {
            desc = descriptor(e);
        } catch (IllegalArgumentException ex) {
            return null; // обобщённые и прочие сигнатуры без дескриптора ядра: точно не интринсик
        }
        return com.sixik.gpu.frontend.common.JdkIntrinsics.find(binaryName(owner).replace('.', '/'),
                e.getSimpleName().toString(), desc);
    }

    /** Двоичное имя класса: вложенные классы разделяются {@code $}. */
    private static String binaryName(TypeElement te) {
        Element owner = te.getEnclosingElement();
        if (owner instanceof TypeElement outer) {
            return binaryName(outer) + "$" + te.getSimpleName();
        }
        return te.getQualifiedName().toString();
    }
}
