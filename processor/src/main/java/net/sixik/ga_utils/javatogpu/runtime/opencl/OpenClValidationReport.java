package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuPromotionArtifactSupport;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Snapshot-style report for cross-device OpenCL validation runs.
 *
 * <p>The report is intentionally lightweight: it captures the selected device/runtime identity, supported features, and
 * the backend counters that are already used by stress validation. This makes it suitable both for CI artifact capture
 * and for local vendor-stack verification.
 */
public record OpenClValidationReport(
        Instant generatedAtUtc,
        String backendName,
        String cacheMode,
        String deviceLabel,
        String vendor,
        String driverVersion,
        String deviceVersion,
        String platformName,
        String platformVersion,
        boolean supportsDoublePrecision,
        boolean supportsImages,
        boolean supportsImage3dWrites,
        GpuPromotionArtifactSupport promotionArtifactSupport,
        long localMemoryBytes,
        long maxWorkGroupSize,
        OpenClRuntimeStatistics statistics
) {

    public OpenClValidationReport(
            Instant generatedAtUtc,
            String backendName,
            String cacheMode,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String deviceVersion,
            String platformName,
            String platformVersion,
            boolean supportsDoublePrecision,
            boolean supportsImages,
            boolean supportsImage3dWrites,
            long localMemoryBytes,
            long maxWorkGroupSize,
            OpenClRuntimeStatistics statistics
    ) {
        this(
                generatedAtUtc,
                backendName,
                cacheMode,
                deviceLabel,
                vendor,
                driverVersion,
                deviceVersion,
                platformName,
                platformVersion,
                supportsDoublePrecision,
                supportsImages,
                supportsImage3dWrites,
                GpuPromotionArtifactSupport.complete(GpuBackendTarget.OPENCL),
                localMemoryBytes,
                maxWorkGroupSize,
                statistics
        );
    }

    public OpenClValidationReport {
        generatedAtUtc = Objects.requireNonNull(generatedAtUtc, "generatedAtUtc");
        backendName = Objects.requireNonNull(backendName, "backendName");
        cacheMode = Objects.requireNonNull(cacheMode, "cacheMode");
        deviceLabel = Objects.requireNonNull(deviceLabel, "deviceLabel");
        vendor = safe(vendor);
        driverVersion = safe(driverVersion);
        deviceVersion = safe(deviceVersion);
        platformName = safe(platformName);
        platformVersion = safe(platformVersion);
        promotionArtifactSupport = promotionArtifactSupport == null
                ? GpuPromotionArtifactSupport.none(GpuBackendTarget.OPENCL)
                : promotionArtifactSupport;
        statistics = Objects.requireNonNull(statistics, "statistics");
    }

    /**
     * Formats the report as stable Markdown so CI or local runs can archive a human-readable validation snapshot.
     */
    public String toMarkdown() {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# OpenCL Validation Report\n\n");
        markdown.append("Generated at (UTC): ").append(generatedAtUtc).append("\n\n");

        markdown.append("## Runtime\n\n");
        markdown.append("- Backend: `").append(backendName).append("`\n");
        markdown.append("- Cache mode: `").append(cacheMode).append("`\n");
        markdown.append("- Device label: `").append(deviceLabel).append("`\n");
        markdown.append("- Vendor: `").append(vendor).append("`\n");
        markdown.append("- Driver version: `").append(driverVersion).append("`\n");
        markdown.append("- Device version: `").append(deviceVersion).append("`\n");
        markdown.append("- Platform: `").append(platformName).append("`\n");
        markdown.append("- Platform version: `").append(platformVersion).append("`\n");
        markdown.append("- Local memory bytes: `").append(localMemoryBytes).append("`\n");
        markdown.append("- Max work-group size: `").append(maxWorkGroupSize).append("`\n\n");

        markdown.append("## Features\n\n");
        markdown.append("- Double precision: `").append(yesNo(supportsDoublePrecision)).append("`\n");
        markdown.append("- Images: `").append(yesNo(supportsImages)).append("`\n");
        markdown.append("- 3D image writes: `").append(yesNo(supportsImage3dWrites)).append("`\n\n");

        markdown.append("## Production Promotion Artifacts\n\n");
        markdown.append("- Backend target: `").append(promotionArtifactSupport.backendTarget()).append("`\n");
        markdown.append("- Complete support: `").append(yesNo(promotionArtifactSupport.complete())).append("`\n");
        markdown.append("- Supported artifact count: `").append(promotionArtifactSupport.supportedArtifacts().size()).append("`\n");
        markdown.append("- Missing artifact count: `").append(promotionArtifactSupport.missingArtifacts().size()).append("`\n");
        markdown.append("- Supported artifacts: `")
                .append(joinArtifacts(promotionArtifactSupport.supportedArtifacts()))
                .append("`\n");
        markdown.append("- Missing artifacts: `")
                .append(joinArtifacts(promotionArtifactSupport.missingArtifacts()))
                .append("`\n\n");

        markdown.append("## Counters\n\n");
        markdown.append("- Invocation count: `").append(statistics.invocationCount()).append("`\n");
        markdown.append("- Compile count: `").append(statistics.compileCount()).append("`\n");
        markdown.append("- Compile cache hits: `").append(statistics.compileCacheHitCount()).append("`\n");
        markdown.append("- Session creation count: `").append(statistics.sessionCreationCount()).append("`\n");
        markdown.append("- Device buffer creation count: `").append(statistics.deviceBufferCreationCount()).append("`\n");
        return markdown.toString();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String joinArtifacts(List<String> artifacts) {
        return artifacts == null || artifacts.isEmpty() ? "none" : String.join(", ", artifacts);
    }
}
