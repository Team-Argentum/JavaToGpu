package net.sixik.ga_utils.javatogpu.frontend.asm;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;

import java.util.List;

public record AsmLiftingResult(
        GpuIrMethod irMethod,
        List<String> helperDependencies
) {
}
