package net.sixik.ga_utils.javatogpu.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a Java instance method name that should lower to pointer arithmetic.
 *
 * <p>This annotation is used from {@link GPUPointerType#operators()} so pointer wrapper classes can expose common
 * Java-friendly methods such as {@code add} and {@code sub} without repeating {@link GPUIntrinsic} on every pointer
 * wrapper method.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface GPUPointerOperator {

    /**
     * Java instance method name, for example {@code add}.
     */
    String method();

    /**
     * Backend operator spelling, for example {@code +}.
     */
    String operator();
}
