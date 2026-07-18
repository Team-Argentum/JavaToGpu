package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Backend-neutral catalog of device-discovery snapshots.
 *
 * <p>OpenCL can currently produce a real native device inventory. CUDA, Vulkan/SPIR-V, Metal, and future custom
 * backends can still appear in this catalog as explicit unavailable discovery states, which gives tools one stable
 * place to render "what was checked?" without special-casing every backend family.</p>
 */
public record GpuRuntimeDeviceDiscoveryCatalog(List<GpuRuntimeDeviceDiscoveryResult> discoveries) {

    public GpuRuntimeDeviceDiscoveryCatalog {
        discoveries = discoveries == null ? List.of() : List.copyOf(discoveries);
    }

    public static GpuRuntimeDeviceDiscoveryCatalog empty() {
        return new GpuRuntimeDeviceDiscoveryCatalog(List.of());
    }

    public static GpuRuntimeDeviceDiscoveryCatalog of(List<GpuRuntimeDeviceDiscoveryResult> discoveries) {
        return new GpuRuntimeDeviceDiscoveryCatalog(discoveries);
    }

    public Optional<GpuRuntimeDeviceDiscoveryResult> forBackend(GpuBackendTarget backendTarget) {
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        return discoveries.stream()
                .filter(discovery -> discovery.backendTarget() == target)
                .findFirst();
    }

    public Optional<GpuRuntimeDeviceDiscoveryResult> forBackendSelection(
            GpuRuntimeBackendSelectionExplanation backendSelection
    ) {
        if (backendSelection == null || !backendSelection.matched()) {
            return Optional.empty();
        }
        return forBackend(backendSelection.selectedBackendTarget());
    }

    public boolean emptyCatalog() {
        return discoveries.isEmpty();
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "deviceDiscoveryCatalog" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        GpuRuntimeArtifactProperties.putPortable(
                fields,
                "runtime.device.discovery.catalog",
                "present",
                !discoveries.isEmpty()
        );
        GpuRuntimeArtifactProperties.putPortable(
                fields,
                "runtime.device.discovery.catalog",
                "backend.count",
                discoveries.size()
        );
        representativeDiscovery().ifPresent(discovery -> fields.putAll(
                GpuRuntimeLifecycleFields.deviceDiscoveryFields(discovery)
        ));
        fields.put(normalizedPrefix + ".backend.count", Integer.toString(discoveries.size()));
        for (int index = 0; index < discoveries.size(); index++) {
            putNonRuntimeFields(fields, discoveries.get(index).artifactFields(normalizedPrefix + ".backend." + index));
        }
        return Collections.unmodifiableMap(fields);
    }

    private Optional<GpuRuntimeDeviceDiscoveryResult> representativeDiscovery() {
        return discoveries.stream()
                .filter(discovery -> discovery.selectedDevice().isPresent())
                .findFirst()
                .or(() -> discoveries.stream().filter(GpuRuntimeDeviceDiscoveryResult::discoveryAvailable).findFirst())
                .or(() -> discoveries.stream().findFirst());
    }

    private static void putNonRuntimeFields(
            LinkedHashMap<String, String> fields,
            Map<String, String> additions
    ) {
        additions.forEach((key, value) -> {
            if (!key.startsWith("runtime.")) {
                fields.put(key, value);
            }
        });
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Device discovery catalog: ").append(discoveries.size()).append(" backend(s)").append('\n');
        for (GpuRuntimeDeviceDiscoveryResult discovery : discoveries) {
            builder.append('\n')
                    .append("Backend device discovery: ")
                    .append(discovery.backendName())
                    .append(" (`")
                    .append(discovery.backendTarget())
                    .append("`)")
                    .append('\n');
            builder.append(discovery.toMarkdown());
        }
        return builder.toString();
    }
}
