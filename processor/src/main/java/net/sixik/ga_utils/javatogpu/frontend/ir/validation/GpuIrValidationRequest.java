package net.sixik.ga_utils.javatogpu.frontend.ir.validation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;

import java.util.List;
import java.util.Objects;

/**
 * Immutable validation input passed from the compiler frontend to optional providers.
 */
public record GpuIrValidationRequest(
        GpuIrCompiledMethod method,
        List<GpuIrCompiledMethod> helperMethods,
        List<ParsedGpuStruct> structs,
        boolean entryPoint,
        GpuIrValidationMode mode
) {
    public GpuIrValidationRequest {
        method = Objects.requireNonNull(method, "method");
        helperMethods = List.copyOf(helperMethods);
        structs = List.copyOf(structs);
        mode = Objects.requireNonNull(mode, "mode");
    }
}
