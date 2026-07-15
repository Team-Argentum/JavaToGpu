package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Backend/vendor-specific view over an immutable IR optimization proposal request.
 */
public record GpuIrVendorOptimizationProposalRequest(
        GpuIrOptimizationProposalRequest proposalRequest,
        GpuBackendTarget backendTarget,
        String vendor,
        String deviceId,
        String deviceLabel,
        String driverVersion,
        String apiVersionText,
        GpuDeviceClassTarget deviceClass,
        Map<String, String> contextFields
) {

    public GpuIrVendorOptimizationProposalRequest {
        proposalRequest = Objects.requireNonNull(proposalRequest, "proposalRequest");
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        vendor = normalize(vendor);
        deviceId = normalize(deviceId);
        deviceLabel = normalize(deviceLabel);
        driverVersion = normalize(driverVersion);
        apiVersionText = normalize(apiVersionText);
        deviceClass = deviceClass == null || deviceClass == GpuDeviceClassTarget.ANY
                ? GpuDeviceClassTarget.UNKNOWN
                : deviceClass;
        contextFields = contextFields == null ? Map.of() : Map.copyOf(contextFields);
    }

    public static GpuIrVendorOptimizationProposalRequest from(GpuIrOptimizationProposalRequest request) {
        Map<String, String> fields = request.contextFields();
        return new GpuIrVendorOptimizationProposalRequest(
                request,
                parseBackend(fields.get("backendTarget")),
                fields.get("deviceProfile.vendor"),
                fields.get("deviceProfile.id"),
                fields.get("deviceProfile.label"),
                fields.get("deviceProfile.driverVersion"),
                fields.get("deviceProfile.apiVersionText"),
                parseDeviceClass(fields.get("deviceProfile.deviceClass")),
                fields
        );
    }

    public IrGpuArtifact originalArtifact() {
        return proposalRequest.originalArtifact();
    }

    public String optimizerProfile() {
        return proposalRequest.optimizerProfile();
    }

    public boolean mutationAllowed() {
        return proposalRequest.mutationAllowed();
    }

    public boolean matches(GpuBackendTarget expectedBackend, String expectedVendor) {
        if (expectedBackend != null && expectedBackend != GpuBackendTarget.UNKNOWN && backendTarget != expectedBackend) {
            return false;
        }
        return expectedVendor == null
                || expectedVendor.isBlank()
                || vendor.equalsIgnoreCase(expectedVendor);
    }

    private static GpuBackendTarget parseBackend(String value) {
        if (value == null || value.isBlank()) {
            return GpuBackendTarget.UNKNOWN;
        }
        try {
            return GpuBackendTarget.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return GpuBackendTarget.UNKNOWN;
        }
    }

    private static GpuDeviceClassTarget parseDeviceClass(String value) {
        if (value == null || value.isBlank()) {
            return GpuDeviceClassTarget.UNKNOWN;
        }
        try {
            return GpuDeviceClassTarget.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return GpuDeviceClassTarget.UNKNOWN;
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
