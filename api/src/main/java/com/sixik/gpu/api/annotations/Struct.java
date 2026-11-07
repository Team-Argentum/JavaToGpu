package com.sixik.gpu.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Пользовательская структура данных, передаваемая на устройство (OpenCL {@code struct}). Тип с value-семантикой:
 * присваивание одной struct-переменной другой без явного копирования запрещено (REWORK_ARCHITECTURE.md, 3.2),
 * чтобы поведение на JVM совпадало с GPU.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Struct {

    /**
     * Выравнивание структуры.
     *
     * @return выравнивание структуры в байтах (степень двойки) либо 0 - естественное выравнивание по полям
     */
    int alignment() default 0;
}
