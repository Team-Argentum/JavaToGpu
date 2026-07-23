package net.sixik.ga_utils.javatogpu.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * User-facing explanation that links backend selection with native device discovery.
 *
 * <p>The backend selector answers "which runtime adapter won?" while device discovery answers "which physical device
 * did that adapter prefer?". This record keeps those answers together without making discovery mandatory or forcing a
 * kernel compile/invocation.</p>
 */
public record GpuRuntimeBackendDeviceSelectionExplanation(
        GpuRuntimeBackendSelectionExplanation backendSelection,
        GpuRuntimeDeviceDiscoveryCatalog deviceDiscoveryCatalog,
        String status,
        List<String> diagnostics
) {

    public GpuRuntimeBackendDeviceSelectionExplanation {
        backendSelection = backendSelection == null
                ? new GpuRuntimeBackendSelectionExplanation(false, null, "none", "none", List.of(), List.of())
                : backendSelection;
        deviceDiscoveryCatalog = deviceDiscoveryCatalog == null
                ? GpuRuntimeDeviceDiscoveryCatalog.empty()
                : deviceDiscoveryCatalog;
        status = status == null || status.isBlank()
                ? resolveStatus(backendSelection, deviceDiscoveryCatalog)
                : status;
        diagnostics = diagnostics == null
                ? diagnostics(backendSelection, deviceDiscoveryCatalog)
                : List.copyOf(diagnostics);
    }

    public static GpuRuntimeBackendDeviceSelectionExplanation from(GpuRuntimeSelectionResult backendSelection) {
        return from(backendSelection, GpuRuntimeDeviceDiscoveryCatalog.empty());
    }

    public static GpuRuntimeBackendDeviceSelectionExplanation from(
            GpuRuntimeSelectionResult backendSelection,
            GpuRuntimeDeviceDiscoveryResult deviceDiscovery
    ) {
        GpuRuntimeDeviceDiscoveryCatalog catalog = deviceDiscovery == null
                ? GpuRuntimeDeviceDiscoveryCatalog.empty()
                : GpuRuntimeDeviceDiscoveryCatalog.of(List.of(deviceDiscovery));
        return from(backendSelection, catalog);
    }

    public static GpuRuntimeBackendDeviceSelectionExplanation from(
            GpuRuntimeSelectionResult backendSelection,
            GpuRuntimeDeviceDiscoveryCatalog deviceDiscoveryCatalog
    ) {
        Objects.requireNonNull(backendSelection, "backendSelection");
        GpuRuntimeBackendSelectionExplanation backendExplanation = backendSelection.explanation();
        GpuRuntimeDeviceDiscoveryCatalog resolvedCatalog = deviceDiscoveryCatalog == null
                ? GpuRuntimeDeviceDiscoveryCatalog.empty()
                : deviceDiscoveryCatalog;
        return new GpuRuntimeBackendDeviceSelectionExplanation(
                backendExplanation,
                resolvedCatalog,
                resolveStatus(backendExplanation, resolvedCatalog),
                diagnostics(backendExplanation, resolvedCatalog)
        );
    }

    public Optional<GpuRuntimeDeviceProfile> selectedDevice() {
        return selectedDiscovery().flatMap(GpuRuntimeDeviceDiscoveryResult::selectedDevice);
    }

    public String summary() {
        if (!backendSelection.matched()) {
            return "no backend selected: " + backendSelection.failureSummary();
        }
        if (selectedDevice().isPresent()) {
            GpuRuntimeDeviceProfile profile = selectedDevice().orElseThrow();
            return "selected "
                    + backendSelection.selectedBackendName()
                    + " on "
                    + profile.deviceLabel()
                    + " ("
                    + GpuRuntimeDevicePolicyContext.deviceKey(profile)
                    + ")";
        }
        Optional<GpuRuntimeDeviceDiscoveryResult> selectedDiscovery = selectedDiscovery();
        if (selectedDiscovery.isEmpty()) {
            return "selected " + backendSelection.selectedBackendName() + "; device discovery not run";
        }
        return "selected "
                + backendSelection.selectedBackendName()
                + "; device discovery "
                + selectedDiscovery.orElseThrow().firstBlocker();
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtimeSelection" : prefix.trim();
        LinkedHashMap<String, String> fields = GpuRuntimeLifecycleFields.backendDeviceSelectionFields(this);
        fields.put(normalizedPrefix + ".status", status);
        fields.put(normalizedPrefix + ".summary", summary());
        fields.put(normalizedPrefix + ".backend.matched", Boolean.toString(backendSelection.matched()));
        fields.put(normalizedPrefix + ".backend.selected.backendTarget", backendSelection.selectedBackendTarget().name());
        fields.put(normalizedPrefix + ".backend.selected.backendName", backendSelection.selectedBackendName());
        fields.put(normalizedPrefix + ".backend.selected.deviceLabel", backendSelection.selectedDeviceLabel());
        fields.putAll(backendSelection.artifactFields(normalizedPrefix + ".backendSelection"));
        Optional<GpuRuntimeDeviceDiscoveryResult> selectedDiscovery = selectedDiscovery();
        fields.put(normalizedPrefix + ".deviceDiscovery.present", Boolean.toString(selectedDiscovery.isPresent()));
        fields.put(normalizedPrefix + ".deviceDiscoveryCatalog.present", Boolean.toString(!deviceDiscoveryCatalog.emptyCatalog()));
        selectedDevice().ifPresent(profile -> {
            fields.put(normalizedPrefix + ".device.selected.deviceKey", GpuRuntimeDevicePolicyContext.deviceKey(profile));
            fields.put(normalizedPrefix + ".device.selected.deviceLabel", profile.deviceLabel());
            fields.put(normalizedPrefix + ".device.selected.vendor", profile.vendor());
            fields.put(normalizedPrefix + ".device.selected.platformName", profile.platformName());
            fields.put(normalizedPrefix + ".device.selected.platformVersion", profile.platformVersion());
        });
        selectedDiscovery.ifPresent(discovery -> fields.putAll(discovery.artifactFields(
                normalizedPrefix + ".deviceDiscovery.selected"
        )));
        fields.putAll(deviceDiscoveryCatalog.artifactFields(normalizedPrefix + ".deviceDiscoveryCatalog"));
        fields.put(normalizedPrefix + ".diagnostic.count", Integer.toString(diagnostics.size()));
        for (int index = 0; index < diagnostics.size(); index++) {
            fields.put(normalizedPrefix + ".diagnostic." + index, diagnostics.get(index));
        }
        fields.putAll(GpuRuntimeLifecycleFields.backendDeviceSelectionFields(this));
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Runtime selection: ").append(status).append('\n');
        builder.append("Summary: ").append(summary()).append('\n');
        builder.append('\n').append("Backend:").append('\n');
        builder.append(backendSelection.toMarkdown());
        if (!deviceDiscoveryCatalog.emptyCatalog()) {
            builder.append('\n').append("Device discoveries:").append('\n');
            builder.append(deviceDiscoveryCatalog.toMarkdown());
        }
        if (!diagnostics.isEmpty()) {
            builder.append('\n').append("Combined diagnostics:").append('\n');
            for (String diagnostic : diagnostics) {
                builder.append("- ").append(diagnostic).append('\n');
            }
        }
        return builder.toString();
    }

    private static String resolveStatus(
            GpuRuntimeBackendSelectionExplanation backendSelection,
            GpuRuntimeDeviceDiscoveryCatalog deviceDiscoveryCatalog
    ) {
        if (!backendSelection.matched()) {
            return "backend-not-selected";
        }
        Optional<GpuRuntimeDeviceDiscoveryResult> deviceDiscovery = deviceDiscoveryCatalog.forBackendSelection(backendSelection);
        if (deviceDiscovery.isEmpty()) {
            return "backend-selected-device-discovery-not-run";
        }
        GpuRuntimeDeviceDiscoveryResult discovery = deviceDiscovery.orElseThrow();
        if (!discovery.discoveryAvailable()) {
            return "device-discovery-unavailable";
        }
        if (discovery.selectedDevice().isEmpty()) {
            return "device-not-selected";
        }
        if (discovery.backendTarget() != backendSelection.selectedBackendTarget()) {
            return "backend-device-target-mismatch";
        }
        return "backend-and-device-selected";
    }

    private static List<String> diagnostics(
            GpuRuntimeBackendSelectionExplanation backendSelection,
            GpuRuntimeDeviceDiscoveryCatalog deviceDiscoveryCatalog
    ) {
        ArrayList<String> diagnostics = new ArrayList<>();
        if (!backendSelection.matched()) {
            diagnostics.add("backend selection did not match: " + backendSelection.failureSummary());
        }
        Optional<GpuRuntimeDeviceDiscoveryResult> deviceDiscovery = deviceDiscoveryCatalog.forBackendSelection(backendSelection);
        if (deviceDiscovery.isEmpty()) {
            if (backendSelection.matched()) {
                diagnostics.add("device discovery was not run for the selected backend");
            }
            return List.copyOf(diagnostics);
        }
        GpuRuntimeDeviceDiscoveryResult discovery = deviceDiscovery.orElseThrow();
        if (!discovery.discoveryAvailable()) {
            diagnostics.add("device discovery unavailable: " + discovery.firstBlocker());
        }
        if (discovery.selectedDevice().isEmpty()) {
            diagnostics.add("device selection did not choose a device: " + discovery.firstBlocker());
        }
        if (backendSelection.matched() && discovery.backendTarget() != backendSelection.selectedBackendTarget()) {
            diagnostics.add("backend target "
                    + backendSelection.selectedBackendTarget()
                    + " differs from device discovery target "
                    + discovery.backendTarget());
        }
        return List.copyOf(diagnostics);
    }

    private Optional<GpuRuntimeDeviceDiscoveryResult> selectedDiscovery() {
        return deviceDiscoveryCatalog.forBackendSelection(backendSelection);
    }
}
