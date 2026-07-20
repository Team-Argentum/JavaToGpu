package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

/**
 * Conservative workload hints inferred from already-available method metadata.
 */
public record GpuRuntimeInferredWorkloadHints(
        GpuRuntimeWorkloadHints hints,
        List<String> diagnostics
) {

    public GpuRuntimeInferredWorkloadHints {
        hints = hints == null ? GpuRuntimeWorkloadHints.none() : hints;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
