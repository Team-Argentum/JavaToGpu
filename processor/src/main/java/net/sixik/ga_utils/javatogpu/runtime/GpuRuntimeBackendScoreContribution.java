package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

/**
 * Read-only score adjustment contributed by a backend scoring hook.
 */
public record GpuRuntimeBackendScoreContribution(
        int adjustment,
        List<String> diagnostics
) {

    public GpuRuntimeBackendScoreContribution {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static GpuRuntimeBackendScoreContribution none() {
        return new GpuRuntimeBackendScoreContribution(0, List.of());
    }

    public static GpuRuntimeBackendScoreContribution of(int adjustment) {
        return new GpuRuntimeBackendScoreContribution(adjustment, List.of());
    }

    public static GpuRuntimeBackendScoreContribution of(int adjustment, String diagnostic) {
        return new GpuRuntimeBackendScoreContribution(
                adjustment,
                diagnostic == null || diagnostic.isBlank() ? List.of() : List.of(diagnostic)
        );
    }

    public static GpuRuntimeBackendScoreContribution of(int adjustment, List<String> diagnostics) {
        return new GpuRuntimeBackendScoreContribution(adjustment, diagnostics);
    }
}
