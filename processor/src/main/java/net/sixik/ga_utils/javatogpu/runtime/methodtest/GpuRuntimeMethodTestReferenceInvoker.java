package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

/**
 * Explicit CPU/reference implementation used to validate materialized {@code @GPUTest} fixtures.
 */
@FunctionalInterface
public interface GpuRuntimeMethodTestReferenceInvoker {

    void invoke(Object[] arguments) throws Exception;
}
