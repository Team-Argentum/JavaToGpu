package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.List;

/**
 * Backend-neutral result of attempting to reconstruct backend source from an IrGpu payload.
 */
public record GpuBackendSourceReconstructionResult(
        GpuBackendTarget backendTarget,
        boolean attempted,
        boolean ready,
        boolean reconstructed,
        String selectedSource,
        String payloadFormat,
        String source,
        String sourceOrigin,
        String runtimeLoadMode,
        List<String> blockers,
        List<String> diagnostics
) {

    public GpuBackendSourceReconstructionResult {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        selectedSource = normalize(selectedSource, "descriptor-source");
        payloadFormat = normalize(payloadFormat, "unknown");
        source = source == null ? "" : source;
        sourceOrigin = normalize(sourceOrigin, selectedSource);
        runtimeLoadMode = normalize(runtimeLoadMode, "source-compile");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static GpuBackendSourceReconstructionResult notAttempted(
            GpuBackendTarget backendTarget,
            String selectedSource,
            String payloadFormat,
            String runtimeLoadMode,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new GpuBackendSourceReconstructionResult(
                backendTarget,
                false,
                false,
                false,
                selectedSource,
                payloadFormat,
                "",
                selectedSource,
                runtimeLoadMode,
                blockers,
                diagnostics
        );
    }

    public static GpuBackendSourceReconstructionResult blocked(
            GpuBackendTarget backendTarget,
            String selectedSource,
            String payloadFormat,
            String runtimeLoadMode,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new GpuBackendSourceReconstructionResult(
                backendTarget,
                true,
                false,
                false,
                selectedSource,
                payloadFormat,
                "",
                selectedSource,
                runtimeLoadMode,
                blockers,
                diagnostics
        );
    }

    public static GpuBackendSourceReconstructionResult ready(
            GpuBackendTarget backendTarget,
            String selectedSource,
            String payloadFormat,
            String runtimeLoadMode,
            List<String> diagnostics
    ) {
        return new GpuBackendSourceReconstructionResult(
                backendTarget,
                true,
                true,
                false,
                selectedSource,
                payloadFormat,
                "",
                selectedSource,
                runtimeLoadMode,
                List.of(),
                diagnostics
        );
    }

    public static GpuBackendSourceReconstructionResult reconstructedSource(
            GpuBackendTarget backendTarget,
            String source,
            String selectedSource,
            String payloadFormat,
            String runtimeLoadMode,
            List<String> diagnostics
    ) {
        return new GpuBackendSourceReconstructionResult(
                backendTarget,
                true,
                true,
                true,
                selectedSource,
                payloadFormat,
                source,
                selectedSource,
                runtimeLoadMode,
                List.of(),
                diagnostics
        );
    }

    public boolean sourceAvailable() {
        return !source.isBlank();
    }

    public String toLine() {
        return "backendTarget="
                + backendTarget
                + " attempted="
                + attempted
                + " ready="
                + ready
                + " reconstructed="
                + reconstructed
                + " selectedSource="
                + selectedSource
                + " payloadFormat="
                + payloadFormat
                + " sourceAvailable="
                + sourceAvailable()
                + " sourceOrigin="
                + sourceOrigin
                + " runtimeLoadMode="
                + runtimeLoadMode
                + " blockers="
                + (blockers.isEmpty() ? "-" : String.join(",", blockers))
                + " diagnostics="
                + (diagnostics.isEmpty() ? "-" : String.join(" | ", diagnostics));
    }

    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("backendTarget=").append(backendTarget).append('\n');
        builder.append("attempted=").append(attempted).append('\n');
        builder.append("ready=").append(ready).append('\n');
        builder.append("reconstructed=").append(reconstructed).append('\n');
        builder.append("selectedSource=").append(selectedSource).append('\n');
        builder.append("payloadFormat=").append(payloadFormat).append('\n');
        builder.append("sourceAvailable=").append(sourceAvailable()).append('\n');
        builder.append("sourceOrigin=").append(sourceOrigin).append('\n');
        builder.append("runtimeLoadMode=").append(runtimeLoadMode).append('\n');
        builder.append("blocker.count=").append(blockers.size()).append('\n');
        for (int index = 0; index < blockers.size(); index++) {
            builder.append("blocker.").append(index).append('=').append(blockers.get(index)).append('\n');
        }
        builder.append("diagnostic.count=").append(diagnostics.size()).append('\n');
        for (int index = 0; index < diagnostics.size(); index++) {
            builder.append("diagnostic.").append(index).append('=').append(diagnostics.get(index)).append('\n');
        }
        return builder.toString();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
