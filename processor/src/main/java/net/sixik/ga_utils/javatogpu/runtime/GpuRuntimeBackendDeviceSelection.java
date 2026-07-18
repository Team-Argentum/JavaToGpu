package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Combined backend + device selection snapshot.
 *
 * <p>The backend selector owns runtime adapter choice. Device discovery owns native device inventory and policy ranking.
 * This record keeps both decisions together so callers can inspect one stable object before installing a backend or
 * deciding to fall back.</p>
 */
public record GpuRuntimeBackendDeviceSelection(
        GpuRuntimeSelectionResult backendSelection,
        GpuRuntimeDeviceDiscoveryCatalog deviceDiscoveryCatalog
) {

    public GpuRuntimeBackendDeviceSelection {
        backendSelection = Objects.requireNonNull(backendSelection, "backendSelection");
        deviceDiscoveryCatalog = deviceDiscoveryCatalog == null
                ? GpuRuntimeDeviceDiscoveryCatalog.empty()
                : deviceDiscoveryCatalog;
    }

    /**
     * Returns whether a runtime backend matched the backend policy.
     */
    public boolean backendMatched() {
        return backendSelection.matched();
    }

    /**
     * Returns whether native device discovery selected a device for the selected backend.
     */
    public boolean deviceMatched() {
        return selectedDevice().isPresent();
    }

    /**
     * Returns whether both the backend and native device parts selected a concrete target.
     */
    public boolean matched() {
        return backendMatched() && deviceMatched();
    }

    public Optional<GpuRuntimeBackendSelection> selectedBackend() {
        return Optional.ofNullable(backendSelection.selection());
    }

    public Optional<GpuRuntimeDeviceDiscoveryResult> selectedDiscovery() {
        return deviceDiscoveryCatalog.forBackendSelection(backendSelection.explanation());
    }

    public Optional<GpuRuntimeDeviceProfile> selectedDevice() {
        return selectedDiscovery().flatMap(GpuRuntimeDeviceDiscoveryResult::selectedDevice);
    }

    public GpuRuntimeBackendSelection requireBackendSelection() {
        return backendSelection.requireSelection();
    }

    public GpuRuntimeDeviceProfile requireSelectedDevice() {
        return selectedDevice().orElseThrow(() -> new UnsupportedOperationException(
                "No GPU runtime device was selected for the selected backend: " + summary()
        ));
    }

    public GpuRuntimeDeviceDiscoveryResult requireSelectedDiscovery() {
        return selectedDiscovery().orElseThrow(() -> new UnsupportedOperationException(
                "No GPU runtime device discovery result matches the selected backend: " + summary()
        ));
    }

    /**
     * Installs the selected backend using its recorded ownership semantics.
     *
     * <p>This method intentionally requires a selected device first so callers do not confuse a backend-only match with
     * a full backend+device preflight. The backend itself still performs final native validation at compile/invoke time.</p>
     */
    public GpuRuntimeScope installSelectedBackend() {
        requireSelectedDevice();
        GpuRuntimeBackendSelection selected = requireBackendSelection();
        GpuRuntimeDeviceDiscoveryResult discovery = requireSelectedDiscovery();
        if (selected.backend() instanceof GpuRuntimeBackendDevicePreselector preselector) {
            preselector.preselectDevice(discovery);
        }
        return selected.install();
    }

    public String status() {
        return explanation().status();
    }

    public String summary() {
        return explanation().summary();
    }

    public GpuRuntimeBackendDeviceSelectionExplanation explanation() {
        return backendSelection.explainWithDeviceDiscovery(deviceDiscoveryCatalog);
    }

    public Map<String, String> artifactFields(String prefix) {
        return explanation().artifactFields(prefix);
    }

    public String toMarkdown() {
        return explanation().toMarkdown();
    }
}
