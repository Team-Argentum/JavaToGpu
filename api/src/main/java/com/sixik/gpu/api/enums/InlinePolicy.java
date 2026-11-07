package com.sixik.gpu.api.enums;

/**
 * Политика встраивания функции (атрибут функции в IR; пока информативна - оптимизатора в v1 нет,
 * REWORK_ARCHITECTURE.md, 3.B).
 */
public enum InlinePolicy {
    /** Решает оптимизатор / кодген. */
    DEFAULT,
    /** Всегда встраивать. */
    ALWAYS,
    /** Никогда не встраивать. */
    NEVER
}
