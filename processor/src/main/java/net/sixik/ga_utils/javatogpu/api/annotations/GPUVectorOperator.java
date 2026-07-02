package net.sixik.ga_utils.javatogpu.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a Java instance method name that should lower to a backend vector operator.
 *
 * <p>This annotation is used from {@link GPUVectorType#operators()} so vector wrapper classes can expose common
 * Java-friendly methods such as {@code add}, {@code sub}, {@code mul}, and {@code div} without repeating
 * {@link GPUIntrinsic} on every method in every vector class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface GPUVectorOperator {

    /**
     * Java instance method name, for example {@code add}.
     */
    String method();

    /**
     * Backend operator spelling, for example {@code +}.
     */
    String operator();
}
