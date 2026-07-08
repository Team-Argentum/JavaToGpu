package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Objects;

/**
 * Advisory device/vendor strategy decision for a runtime IR optimization request.
 *
 * <p>The decision is deliberately diagnostic-only today. It may explain which strategy would be preferred for a
 * device family, but it must not enable production IR mutation without proof, post-validation, and rollback gates.</p>
 */
public record GpuOptimizationStrategyDecision(
        String strategyName,
        String deviceFamily,
        String selectedProfile,
        boolean advisoryOnly,
        boolean evidenceBacked,
        String reason,
        GpuOptimizationVendorBaseline vendorBaseline,
        List<String> diagnostics
) {

    public GpuOptimizationStrategyDecision {
        strategyName = normalize(strategyName, "strategy:unknown");
        deviceFamily = normalize(deviceFamily, "unknown");
        selectedProfile = normalize(selectedProfile, "off");
        reason = normalize(reason, "no strategy reason provided");
        vendorBaseline = vendorBaseline == null
                ? GpuOptimizationVendorBaseline.missing(deviceFamily)
                : vendorBaseline;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static GpuOptimizationStrategyDecision advisory(
            String strategyName,
            String deviceFamily,
            String selectedProfile,
            String reason,
            List<String> diagnostics
    ) {
        return advisory(strategyName, deviceFamily, selectedProfile, reason, GpuOptimizationVendorBaseline.missing(deviceFamily), diagnostics);
    }

    public static GpuOptimizationStrategyDecision advisory(
            String strategyName,
            String deviceFamily,
            String selectedProfile,
            String reason,
            GpuOptimizationVendorBaseline vendorBaseline,
            List<String> diagnostics
    ) {
        return new GpuOptimizationStrategyDecision(
                strategyName,
                deviceFamily,
                selectedProfile,
                true,
                false,
                reason,
                vendorBaseline,
                diagnostics
        );
    }

    public static GpuOptimizationStrategyDecision none(GpuRuntimeCompileRequest request) {
        GpuRuntimeDeviceProfile profile = request == null ? null : request.deviceProfile();
        String deviceFamily = profile == null ? "unknown" : profile.vendor();
        String selectedProfile = request == null ? "off" : request.options().optimizationProfile();
        return advisory(
                "strategy:none",
                deviceFamily,
                selectedProfile,
                "no runtime optimization strategy selected",
                GpuOptimizationVendorBaseline.missing(deviceFamily),
                List.of("runtime optimizer remains advisory-only")
        );
    }

    public String toLine() {
        StringBuilder builder = new StringBuilder();
        builder.append("strategy=").append(strategyName)
                .append(" deviceFamily=").append(deviceFamily)
                .append(" selectedProfile=").append(selectedProfile)
                .append(" advisoryOnly=").append(advisoryOnly)
                .append(" evidenceBacked=").append(evidenceBacked)
                .append(" reason=").append(reason)
                .append(' ')
                .append(vendorBaseline.toLine());
        if (!diagnostics.isEmpty()) {
            builder.append(" diagnostics=").append(String.join(" | ", diagnostics));
        }
        return builder.toString();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNull(fallback, "fallback") : value;
    }
}
