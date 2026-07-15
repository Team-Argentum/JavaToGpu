package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;

/**
 * Result of a capability-based backend selection attempt.
 *
 * <p>This is the non-throwing companion to {@link GpuRuntimeBackendSelection}. It is useful for production flows that
 * want to perform a capability precheck and then decide whether to install a backend, skip GPU execution, or fall back
 * to another path without treating the miss as an exceptional control-flow event.
 */
public record GpuRuntimeSelectionResult(
        GpuRuntimeBackendSelection selection,
        List<String> failureReasons,
        List<GpuRuntimeBackendCandidateDecision> candidateDecisions
) {

    public GpuRuntimeSelectionResult(GpuRuntimeBackendSelection selection, List<String> failureReasons) {
        this(selection, failureReasons, List.of());
    }

    public GpuRuntimeSelectionResult {
        failureReasons = failureReasons == null ? List.of() : List.copyOf(failureReasons);
        candidateDecisions = candidateDecisions == null ? List.of() : List.copyOf(candidateDecisions);
    }

    /**
     * Returns {@code true} when a backend matched all requested requirements.
     */
    public boolean matched() {
        return selection != null;
    }

    /**
     * Returns the selected backend selection or throws a descriptive exception when none matched.
     */
    public GpuRuntimeBackendSelection requireSelection() {
        if (selection != null) {
            return selection;
        }
        throw new UnsupportedOperationException(
                "No GPU runtime backend satisfies the requested requirements: " + failureSummary()
        );
    }

    /**
     * Installs the selected backend using its recorded ownership semantics or throws when no backend matched.
     */
    public GpuRuntimeScope install() {
        return requireSelection().install();
    }

    /**
     * Returns all recorded rejection reasons as one human-readable summary.
     */
    public String failureSummary() {
        if (failureReasons.isEmpty()) {
            return "no backend candidates were provided";
        }
        return String.join(" | ", failureReasons);
    }

    /**
     * Returns a compact selection explanation suitable for logs and user-facing diagnostics.
     */
    public String explanationSummary() {
        return explanation().summary();
    }

    /**
     * Returns a structured backend-selection explanation for logs, CLIs, and runtime artifacts.
     */
    public GpuRuntimeBackendSelectionExplanation explanation() {
        return GpuRuntimeBackendSelectionExplanation.from(this);
    }

    /**
     * Returns a combined backend/device explanation when native device discovery evidence is available.
     */
    public GpuRuntimeBackendDeviceSelectionExplanation explainWithDeviceDiscovery(
            GpuRuntimeDeviceDiscoveryResult deviceDiscovery
    ) {
        return GpuRuntimeBackendDeviceSelectionExplanation.from(this, deviceDiscovery);
    }

    /**
     * Returns a combined backend/device explanation for a multi-backend discovery catalog.
     */
    public GpuRuntimeBackendDeviceSelectionExplanation explainWithDeviceDiscovery(
            GpuRuntimeDeviceDiscoveryCatalog deviceDiscoveryCatalog
    ) {
        return GpuRuntimeBackendDeviceSelectionExplanation.from(this, deviceDiscoveryCatalog);
    }

    /**
     * Returns deterministic key/value fields for logs, reports, or future lifecycle-journal events.
     */
    public Map<String, String> artifactFields(String prefix) {
        return explanation().artifactFields(prefix);
    }
}
