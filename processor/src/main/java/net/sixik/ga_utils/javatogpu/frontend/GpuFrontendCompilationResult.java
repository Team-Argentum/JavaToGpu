package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

public record GpuFrontendCompilationResult(
        String openClSource,
        IrGpuArtifact irGpuArtifact
) {
}
