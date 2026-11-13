package com.sixik.gpu.frontend.asm;

/**
 * Стейтмент фронтенда до эмиссии в IR (REWORK_ARCHITECTURE.md, 17.7).
 *
 * @param kind   вид: {@link #DECL}, {@link #SET}, {@link #STORE}, {@link #EXPR}, {@link #BARRIER}, {@link #RETURN},
 *               {@link #ARRAY_DECL}
 * @param var    переменная ({@code DECL}, {@code SET}, {@code ARRAY_DECL})
 * @param a      значение ({@code DECL}, {@code SET}, {@code EXPR}, {@code RETURN} - может быть {@code null}); массив
 *               ({@code STORE})
 * @param b      индекс ({@code STORE})
 * @param c      записываемое значение ({@code STORE})
 * @param aux    флаги барьера; длина массива ({@code ARRAY_DECL})
 * @param ordered {@code STORE}: операнды вычисляются в порядке Java (индекс, значение, массив), потому что значение или
 *               индекс содержали эффекты; иначе значение первым, как у {@code frontend-java} для чистых операндов
 * @param line   строка исходника либо 0
 * @param group  номер исходного стейтмента: стейтменты одного выражения Java (спиллы, эффекты) имеют один номер
 */
record St(int kind, Var var, Ex a, Ex b, Ex c, int aux, boolean ordered, int line, int group) {

    static final int DECL = 0;
    static final int SET = 1;
    static final int STORE = 2;
    static final int EXPR = 3;
    static final int BARRIER = 4;
    static final int RETURN = 5;
    static final int ARRAY_DECL = 6;
}
