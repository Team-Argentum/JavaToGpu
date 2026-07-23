package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Capability report for one runtime backend instance.
 */
public record GpuRuntimeBackendReport(
        GpuBackendTarget backendTarget,
        String backendName,
        boolean available,
        String deviceLabel,
        GpuRuntimeApiVersion apiVersion,
        String apiVersionText,
        Set<GpuRuntimeFeature> features,
        Long localMemoryBytes,
        Long maxWorkGroupSize,
        String detail
) {

    public GpuRuntimeBackendReport {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        backendName = backendName == null || backendName.isBlank() ? "Unknown backend" : backendName;
        features = features == null || features.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(features));
    }

    /**
     * Creates an available capability report.
     */
    public static GpuRuntimeBackendReport available(
            GpuBackendTarget backendTarget,
            String backendName,
            String deviceLabel,
            GpuRuntimeApiVersion apiVersion,
            String apiVersionText,
            Set<GpuRuntimeFeature> features,
            Long localMemoryBytes,
            Long maxWorkGroupSize,
            String detail
    ) {
        return new GpuRuntimeBackendReport(
                backendTarget,
                backendName,
                true,
                deviceLabel,
                apiVersion,
                apiVersionText,
                features,
                localMemoryBytes,
                maxWorkGroupSize,
                detail
        );
    }

    /**
     * Creates an unavailable capability report.
     */
    public static GpuRuntimeBackendReport unavailable(
            GpuBackendTarget backendTarget,
            String backendName,
            String detail
    ) {
        return new GpuRuntimeBackendReport(
                backendTarget,
                backendName,
                false,
                null,
                null,
                null,
                Set.of(),
                null,
                null,
                detail
        );
    }

    /**
     * Returns whether the report advertises the given feature.
     */
    public boolean supports(GpuRuntimeFeature feature) {
        return features.contains(feature);
    }

    public Set<GpuRuntimeCapability> runtimeCapabilities() {
        EnumSet<GpuRuntimeCapability> capabilities = EnumSet.noneOf(GpuRuntimeCapability.class);
        for (GpuRuntimeFeature feature : features) {
            GpuRuntimeCapability.fromFeature(feature).ifPresent(capabilities::add);
        }
        if (apiVersion != null || (apiVersionText != null && !apiVersionText.isBlank())) {
            capabilities.add(GpuRuntimeCapability.RUNTIME_VERSION);
        }
        if (localMemoryBytes != null && localMemoryBytes > 0L) {
            capabilities.add(GpuRuntimeCapability.LOCAL_MEMORY);
        }
        if (maxWorkGroupSize != null && maxWorkGroupSize > 0L) {
            capabilities.add(GpuRuntimeCapability.MAX_WORK_GROUP_SIZE);
        }
        return capabilities.isEmpty() ? Set.of() : Collections.unmodifiableSet(capabilities);
    }

    public boolean supports(GpuRuntimeCapability capability) {
        return capability != null && runtimeCapabilities().contains(capability);
    }
}
