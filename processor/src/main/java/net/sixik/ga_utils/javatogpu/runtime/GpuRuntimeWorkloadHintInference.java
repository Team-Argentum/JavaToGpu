package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

/**
 * Compatibility facade for read-only workload-hint inference.
 */
public final class GpuRuntimeWorkloadHintInference {

    private GpuRuntimeWorkloadHintInference() {
    }

    public static GpuRuntimeInferredWorkloadHints infer(GpuKernelDescriptor descriptor) {
        return net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeWorkloadHintInference.infer(descriptor);
    }

    public static GpuRuntimeInferredWorkloadHints infer(GpuKernelDescriptor descriptor, IrGpuArtifact artifact) {
        return net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeWorkloadHintInference.infer(
                descriptor,
                artifact
        );
    }
}
