package net.sixik.ga_utils.javatogpu.frontend.ir.validation;

/**
 * Optional ServiceLoader extension point for read-only IR validation modules.
 */
public interface GpuIrValidationProvider {
    void validate(GpuIrValidationRequest request);
}
