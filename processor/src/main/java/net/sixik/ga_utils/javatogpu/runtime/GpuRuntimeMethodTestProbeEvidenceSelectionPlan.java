package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Report produced by the explicit {@code @GPUTest} warm-up plus cache-only device-selection helper.
 */
public record GpuRuntimeMethodTestProbeEvidenceSelectionPlan(
        String kernelName,
        String irGpuResource,
        GpuRuntimeMethodTestProbeEvidenceWarmupPlan warmupPlan,
        GpuRuntimeCompileOptions selectionCompileOptions,
        GpuRuntimeDeviceSelection deviceSelection,
        List<String> blockers,
        List<String> diagnostics
) {

    public GpuRuntimeMethodTestProbeEvidenceSelectionPlan {
        kernelName = normalize(kernelName, "unknown");
        irGpuResource = normalize(irGpuResource, "");
        blockers = normalizeList(blockers);
        diagnostics = normalizeList(diagnostics);
    }

    public Optional<GpuRuntimeDeviceProfile> selectedDevice() {
        return deviceSelection == null ? Optional.empty() : deviceSelection.selectedDevice();
    }

    public boolean selectionReady() {
        return blockers.isEmpty()
                && deviceSelection != null
                && deviceSelection.compileOptionsValid()
                && deviceSelection.selectedDevice().isPresent();
    }

    public String status() {
        if (!selectionReady()) {
            return "blocked";
        }
        if (warmupPlan == null || !warmupPlan.warmupPassed()) {
            return "selected-with-warmup-" + (warmupPlan == null ? "missing" : warmupPlan.status());
        }
        return "selected";
    }

    public String firstBlocker() {
        if (!blockers.isEmpty()) {
            return blockers.get(0);
        }
        if (deviceSelection != null && !"none".equals(deviceSelection.firstBlocker())) {
            return deviceSelection.firstBlocker();
        }
        return warmupPlan == null ? "none" : warmupPlan.firstBlocker();
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = normalize(prefix, "methodTestProbeEvidenceSelection");
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".kernelName", kernelName);
        fields.put(normalizedPrefix + ".irGpuResource", irGpuResource);
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".selectionReady", Boolean.toString(selectionReady()));
        fields.put(normalizedPrefix + ".selectedDeviceKey", selectedDevice()
                .map(GpuRuntimeDevicePolicyContext::deviceKey)
                .orElse("none"));
        fields.put(normalizedPrefix + ".warmup.status", warmupPlan == null ? "not-run" : warmupPlan.status());
        fields.put(normalizedPrefix + ".cacheOnly.mode", selectionCompileOptions == null
                ? "unknown"
                : selectionCompileOptions.backendOptions().methodTestProbeMode().optionValue());
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        if (warmupPlan != null) {
            fields.putAll(warmupPlan.artifactFields(normalizedPrefix + ".warmup"));
        }
        if (deviceSelection != null) {
            fields.putAll(deviceSelection.artifactFields(normalizedPrefix + ".deviceSelection"));
        }
        writeList(fields, normalizedPrefix + ".blocker", blockers);
        writeList(fields, normalizedPrefix + ".diagnostic", diagnostics);
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Method test probe evidence selection: ").append(status()).append('\n');
        builder.append("Selected device: ")
                .append(selectedDevice().map(GpuRuntimeDevicePolicyContext::deviceKey).orElse("none"))
                .append('\n');
        builder.append("Warm-up status: ").append(warmupPlan == null ? "not-run" : warmupPlan.status()).append('\n');
        if (!"none".equals(firstBlocker())) {
            builder.append("First blocker: ").append(firstBlocker()).append('\n');
        }
        if (warmupPlan != null) {
            builder.append('\n').append(warmupPlan.toMarkdown());
        }
        if (deviceSelection != null && !deviceSelection.diagnostics().isEmpty()) {
            builder.append('\n').append("Selection diagnostics:").append('\n');
            for (String diagnostic : deviceSelection.diagnostics()) {
                builder.append("- ").append(diagnostic).append('\n');
            }
        }
        if (!diagnostics.isEmpty()) {
            builder.append('\n').append("Diagnostics:").append('\n');
            for (String diagnostic : diagnostics) {
                builder.append("- ").append(diagnostic).append('\n');
            }
        }
        return builder.toString();
    }

    private static void writeList(LinkedHashMap<String, String> fields, String prefix, List<String> values) {
        fields.put(prefix + ".count", Integer.toString(values.size()));
        for (int index = 0; index < values.size(); index++) {
            fields.put(prefix + "." + index, values.get(index));
        }
    }

    private static List<String> normalizeList(List<String> values) {
        return values == null
                ? List.of()
                : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
