package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic runtime choice of one method implementation and one compatible device.
 */
public record GpuRuntimeMethodVariantSelection(
        String groupId,
        String selectedVariantId,
        GpuKernelDescriptor selectedDescriptor,
        Optional<IrGpuArtifact> selectedArtifact,
        GpuRuntimeDeviceSelection deviceSelection,
        List<GpuRuntimeMethodVariantEvaluation> evaluations,
        List<String> diagnostics
) {

    public GpuRuntimeMethodVariantSelection {
        groupId = normalize(groupId, "none");
        selectedVariantId = normalize(selectedVariantId, "unknown");
        selectedDescriptor = java.util.Objects.requireNonNull(selectedDescriptor, "selectedDescriptor");
        selectedArtifact = selectedArtifact == null ? Optional.empty() : selectedArtifact;
        deviceSelection = java.util.Objects.requireNonNull(deviceSelection, "deviceSelection");
        evaluations = evaluations == null ? List.of() : List.copyOf(evaluations);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = normalize(prefix, "methodVariantSelection");
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".groupId", groupId);
        fields.put(normalizedPrefix + ".selectedVariantId", selectedVariantId);
        fields.put(normalizedPrefix + ".selectedKernelName", selectedDescriptor.kernelName());
        fields.put(normalizedPrefix + ".selectedKernelResource", selectedDescriptor.kernelResource());
        fields.put(normalizedPrefix + ".evaluation.count", Integer.toString(evaluations.size()));
        for (int index = 0; index < evaluations.size(); index++) {
            GpuRuntimeMethodVariantEvaluation evaluation = evaluations.get(index);
            String evaluationPrefix = normalizedPrefix + ".evaluation." + index;
            fields.put(evaluationPrefix + ".groupId", evaluation.groupId());
            fields.put(evaluationPrefix + ".variantId", evaluation.variantId());
            fields.put(evaluationPrefix + ".priority", Integer.toString(evaluation.priority()));
            fields.put(evaluationPrefix + ".kernelName", evaluation.descriptor().kernelName());
            fields.put(evaluationPrefix + ".kernelResource", evaluation.descriptor().kernelResource());
            fields.put(evaluationPrefix + ".accepted", Boolean.toString(evaluation.accepted()));
            fields.put(evaluationPrefix + ".selectedDeviceScore", Integer.toString(evaluation.selectedDeviceScore()));
            fields.put(evaluationPrefix + ".selectedDeviceKey", evaluation.selectedDeviceKey());
            fields.put(evaluationPrefix + ".diagnostic.count", Integer.toString(evaluation.diagnostics().size()));
            for (int diagnosticIndex = 0; diagnosticIndex < evaluation.diagnostics().size(); diagnosticIndex++) {
                fields.put(
                        evaluationPrefix + ".diagnostic." + diagnosticIndex,
                        evaluation.diagnostics().get(diagnosticIndex)
                );
            }
        }
        fields.put(normalizedPrefix + ".diagnostic.count", Integer.toString(diagnostics.size()));
        for (int index = 0; index < diagnostics.size(); index++) {
            fields.put(normalizedPrefix + ".diagnostic." + index, diagnostics.get(index));
        }
        return Collections.unmodifiableMap(fields);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
