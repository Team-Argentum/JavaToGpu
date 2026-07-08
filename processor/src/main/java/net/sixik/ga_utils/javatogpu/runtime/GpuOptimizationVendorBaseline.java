package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Objects;

/**
 * Compact vendor baseline state used by advisory runtime optimization strategies.
 */
public record GpuOptimizationVendorBaseline(
        String vendorFamily,
        String status,
        boolean recorded,
        boolean promotionEligible,
        String source,
        List<String> diagnostics
) {

    public GpuOptimizationVendorBaseline {
        vendorFamily = normalize(vendorFamily, "unknown");
        status = normalize(status, "missing-baseline");
        source = normalize(source, "none");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static GpuOptimizationVendorBaseline nvidiaRecorded() {
        return new GpuOptimizationVendorBaseline(
                "nvidia",
                "recorded-nvidia-only",
                true,
                false,
                "docs-project-plan/nvidia-rtx5070-baselines.md",
                List.of("NVIDIA baseline can guide development but is not cross-vendor production proof")
        );
    }

    public static GpuOptimizationVendorBaseline pendingHardware(String vendorFamily) {
        return new GpuOptimizationVendorBaseline(
                vendorFamily,
                "pending-hardware",
                false,
                false,
                "docs/Device-Quirks.md",
                List.of("real hardware validation is required before promotion")
        );
    }

    public static GpuOptimizationVendorBaseline missing(String vendorFamily) {
        return new GpuOptimizationVendorBaseline(
                vendorFamily,
                "missing-baseline",
                false,
                false,
                "none",
                List.of("no vendor baseline is available for this runtime device family")
        );
    }

    public String toLine() {
        StringBuilder builder = new StringBuilder();
        builder.append("baselineVendor=").append(vendorFamily)
                .append(" baselineStatus=").append(status)
                .append(" baselineRecorded=").append(recorded)
                .append(" promotionEligible=").append(promotionEligible)
                .append(" baselineSource=").append(source);
        if (!diagnostics.isEmpty()) {
            builder.append(" baselineDiagnostics=").append(String.join(" | ", diagnostics));
        }
        return builder.toString();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNull(fallback, "fallback") : value;
    }
}
