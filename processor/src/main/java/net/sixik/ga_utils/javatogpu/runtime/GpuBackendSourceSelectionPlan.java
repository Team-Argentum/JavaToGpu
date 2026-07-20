package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.backend.sourceSelection"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".backendTarget", backendTarget.name());
        fields.put(normalizedPrefix + ".irGpuSourceSelected", Boolean.toString(irGpuSourceSelected));
        fields.put(normalizedPrefix + ".selectedSource", selectedSource);
        fields.put(normalizedPrefix + ".payloadFormat", payloadFormat);
        fields.put(normalizedPrefix + ".runtimeLoadMode", runtimeLoadMode);
        fields.put(normalizedPrefix + ".blocker.count", Integer.toString(blockers.size()));
        for (int index = 0; index < blockers.size(); index++) {
            fields.put(normalizedPrefix + ".blocker." + index, blockers.get(index));
        }
        fields.put(normalizedPrefix + ".diagnostic.count", Integer.toString(diagnostics.size()));
        for (int index = 0; index < diagnostics.size(); index++) {
            fields.put(normalizedPrefix + ".diagnostic." + index, diagnostics.get(index));
        }
        fields.put("runtime.backend.sourceSelection.present", "true");
        fields.put("runtime.backend.sourceSelection.backendTarget", backendTarget.name());
        fields.put("runtime.backend.sourceSelection.irGpuSourceSelected", Boolean.toString(irGpuSourceSelected));
        fields.put("runtime.backend.sourceSelection.selectedSource", selectedSource);
        fields.put("runtime.backend.sourceSelection.payloadFormat", payloadFormat);
        fields.put("runtime.backend.sourceSelection.runtimeLoadMode", runtimeLoadMode);
        fields.put("runtime.backend.sourceSelection.blocker.count", Integer.toString(blockers.size()));
        fields.put("runtime.backend.target", backendTarget.name());
        return Collections.unmodifiableMap(fields);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
