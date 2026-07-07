package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.List;

/**
 * Backend-neutral explanation of which source or binary path a lowerer intends to use.
 */
public record GpuBackendSourceSelectionPlan(
        GpuBackendTarget backendTarget,
        boolean irGpuSourceSelected,
        String selectedSource,
        String payloadFormat,
        String runtimeLoadMode,
        List<String> blockers,
        List<String> diagnostics
) {

    public GpuBackendSourceSelectionPlan {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        selectedSource = normalize(selectedSource, "descriptor-source");
        payloadFormat = normalize(payloadFormat, "unknown");
        runtimeLoadMode = normalize(runtimeLoadMode, "source-compile");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static GpuBackendSourceSelectionPlan descriptorSource(
            GpuBackendTarget backendTarget,
            String payloadFormat,
            String diagnostic
    ) {
        return new GpuBackendSourceSelectionPlan(
                backendTarget,
                false,
                "descriptor-source",
                payloadFormat,
                "source-compile",
                List.of(),
                diagnostic == null || diagnostic.isBlank() ? List.of() : List.of(diagnostic)
        );
    }

    public String toLine() {
        return "backendTarget="
                + backendTarget
                + " irGpuSourceSelected="
                + irGpuSourceSelected
                + " selectedSource="
                + selectedSource
                + " payloadFormat="
                + payloadFormat
                + " runtimeLoadMode="
                + runtimeLoadMode
                + " blockers="
                + (blockers.isEmpty() ? "-" : String.join(",", blockers))
                + " diagnostics="
                + (diagnostics.isEmpty() ? "-" : String.join(" | ", diagnostics));
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
