package net.sixik.ga_utils.javatogpu.frontend.ir.model;

import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;

import java.util.List;

public record GpuIrCompiledMethod(
        ParsedGpuMethod parsedMethod,
        GpuIrMethod irMethod,
        String emittedName,
        List<String> helperDependencies
) {
}
