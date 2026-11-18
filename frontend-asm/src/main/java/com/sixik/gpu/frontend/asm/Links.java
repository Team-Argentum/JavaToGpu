package com.sixik.gpu.frontend.asm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Таблица связей (FRONTEND_ASM_RUNTIME.md, 18.2-S2, шаг B3): вызов {@code invokestatic} метода заменяется вызовом
 * цели. Так библиотека в jar вызывает сгенерированный код через заглушку {@code @Extern}. Дескрипторы должны
 * совпадать; вызов {@code @Extern} без связи - {@code GPU0309}. Неизменяемый объект.
 */
public final class Links {

    /**
     * Цель связи.
     *
     * @param owner внутреннее имя класса
     * @param name  имя метода
     * @param desc  дескриптор
     */
    record Target(String owner, String name, String desc) {
    }

    private static final Links NONE = new Links(Map.of());

    private final Map<String, Target> targets;

    private Links(Map<String, Target> targets) {
        this.targets = targets;
    }

    /** @return пустая таблица */
    public static Links none() {
        return NONE;
    }

    /**
     * Связывает метод с целью.
     *
     * @param owner   внутреннее имя класса вызываемого метода (заглушки)
     * @param name    имя
     * @param desc    дескриптор
     * @param toOwner внутреннее имя класса цели
     * @param toName  имя цели
     * @param toDesc  дескриптор цели (должен совпадать с {@code desc}, иначе {@code GPU0309})
     * @return новая таблица
     */
    public Links redirect(String owner, String name, String desc, String toOwner, String toName, String toDesc) {
        Map<String, Target> m = new LinkedHashMap<>(targets);
        m.put(key(owner, name, desc), new Target(Objects.requireNonNull(toOwner, "toOwner"),
                Objects.requireNonNull(toName, "toName"), Objects.requireNonNull(toDesc, "toDesc")));
        return new Links(Collections.unmodifiableMap(m));
    }

    /** @return {@code true}, если связей нет */
    public boolean isEmpty() {
        return targets.isEmpty();
    }

    /** @return цель либо {@code null} */
    Target target(String owner, String name, String desc) {
        return targets.get(key(owner, name, desc));
    }

    private static String key(String owner, String name, String desc) {
        return Objects.requireNonNull(owner, "owner") + "." + Objects.requireNonNull(name, "name")
                + Objects.requireNonNull(desc, "desc");
    }
}
