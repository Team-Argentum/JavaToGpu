package com.sixik.gpu.frontend.asm;

/**
 * Переменная функции (REWORK_ARCHITECTURE.md, 17.10): запись LVT (имя, слот, дескриптор), изменяемый параметр или
 * временная локаль фронтенда. Локаль IR назначается при эмиссии ({@link #irLocal}), поэтому порядок номеров локалей
 * задаёт эмиттер, а не порядок появления в байткоде.
 */
final class Var {

    /** Имя из LVT либо {@code null} для временной локали (в секции {@code debug} она без имени). */
    final String name;
    /** Вид значения ({@link Kinds}). */
    final int kind;
    /** Вид элемента для массива, иначе -1. */
    final int elemKind;
    /** Адресное пространство для массива, иначе -1. */
    final int space;
    /** Тип IR локали. */
    final int irType;
    /** Индекс параметра, если это изменяемый параметр (локаль с начальным значением {@code param(i)}), иначе -1. */
    final int paramIndex;
    /** Номер локали IR либо -1, пока не объявлена. */
    int irLocal = -1;
    /** Внутреннее имя агрегата (у массива - элемента), иначе {@code null}. */
    String cls;
    /** Локаль-ссылка {@code FloatPtr}: хранит значение ячейки, чтение даёт её адрес. */
    boolean owned;

    Var(String name, int kind, int elemKind, int space, int irType, int paramIndex) {
        this.name = name;
        this.kind = kind;
        this.elemKind = elemKind;
        this.space = space;
        this.irType = irType;
        this.paramIndex = paramIndex;
    }

    /** @return {@code true} для временной локали фронтенда (пишется один раз, «открытой» не считается) */
    boolean temp() {
        return name == null;
    }

    @Override
    public String toString() {
        return name == null ? "tmp" : name;
    }
}
