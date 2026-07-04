package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassContext;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationMode;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationProvider;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationRequest;

/**
 * ServiceLoader bridge that exposes the unified validation pipeline to the compiler frontend.
 */
public final class GpuIrOptimizationValidationProvider implements GpuIrValidationProvider {
    @Override
    public void validate(GpuIrValidationRequest request) {
        if (request.mode() == GpuIrValidationMode.OFF) {
            return;
        }
        new GpuIrOptimizationValidationPipeline(mode(request.mode())).validate(new GpuIrPassContext(
                request.method(),
                request.helperMethods(),
                request.structs(),
                request.entryPoint()
        ));
    }

    private GpuIrOptimizationValidationMode mode(GpuIrValidationMode mode) {
        return switch (mode) {
            case OFF, DIAGNOSTIC -> GpuIrOptimizationValidationMode.DIAGNOSTIC_ONLY;
            case STRICT_SAFETY -> GpuIrOptimizationValidationMode.STRICT_FAIL_ON_SAFETY_ERROR;
            case STRICT_OPTIMIZER -> GpuIrOptimizationValidationMode.STRICT_FAIL_ON_OPTIMIZER_DIAGNOSTICS;
        };
    }
}
