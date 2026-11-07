package com.sixik.gpu.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Константа, доступная из GPU-кода: {@code static final} поле примитивного типа со значением, известным при
 * компиляции. Фронтенд подставляет значение в IR как константу.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface Constant {
}
