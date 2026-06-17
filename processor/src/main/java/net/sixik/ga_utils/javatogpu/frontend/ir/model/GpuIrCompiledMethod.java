package net.sixik.ga_utils.javatogpu.frontend.ir.model;

import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;

public record GpuIrCompiledMethod(
        ParsedGpuMethod parsedMethod,
        GpuIrMethod irMethod
) {
}
