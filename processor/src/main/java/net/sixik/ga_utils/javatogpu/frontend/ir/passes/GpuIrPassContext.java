package net.sixik.ga_utils.javatogpu.frontend.ir.passes;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;

import java.util.List;

public record GpuIrPassContext(
        GpuIrCompiledMethod method,
        List<GpuIrCompiledMethod> helperMethods,
        List<ParsedGpuStruct> structs,
        boolean entryPoint
) {
    public GpuIrPassContext {
        helperMethods = List.copyOf(helperMethods);
        structs = List.copyOf(structs);
    }
}
