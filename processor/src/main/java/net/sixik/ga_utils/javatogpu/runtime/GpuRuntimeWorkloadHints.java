package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Backend-neutral workload intent exposed to advisory backend score contributors.
 *
 * <p>Hints are not hard requirements. Use {@link GpuRuntimeBackendPolicy.Builder#requireBackend} or the typed
 * {@code require...} helpers when a backend must be rejected. Workload hints are meant to explain preference: expected
 * parallelism, memory/arithmetic shape, required portable capabilities, and preferred module formats.</p>
 */
public record GpuRuntimeWorkloadHints(
        long expectedItemCount,
        int preferredWorkGroupSize,
        GpuRuntimeWorkloadIntensity memoryIntensity,
        GpuRuntimeWorkloadIntensity arithmeticIntensity,
        boolean latencySensitive,
        Set<GpuRuntimeCapability> requiredCapabilities,
        Set<GpuBackendModuleFormat> preferredModuleFormats
) {

    private static final GpuRuntimeWorkloadHints EMPTY = new Builder().build();

    public GpuRuntimeWorkloadHints {
        expectedItemCount = expectedItemCount <= 0L ? -1L : expectedItemCount;
        preferredWorkGroupSize = preferredWorkGroupSize <= 0 ? -1 : preferredWorkGroupSize;
        memoryIntensity = memoryIntensity == null ? GpuRuntimeWorkloadIntensity.UNKNOWN : memoryIntensity;
        arithmeticIntensity = arithmeticIntensity == null ? GpuRuntimeWorkloadIntensity.UNKNOWN : arithmeticIntensity;
        requiredCapabilities = immutableCapabilitySet(requiredCapabilities);
        preferredModuleFormats = immutableFormatSet(preferredModuleFormats);
    }

    public static GpuRuntimeWorkloadHints none() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean empty() {
        return expectedItemCount <= 0L
                && preferredWorkGroupSize <= 0
                && memoryIntensity == GpuRuntimeWorkloadIntensity.UNKNOWN
                && arithmeticIntensity == GpuRuntimeWorkloadIntensity.UNKNOWN
                && !latencySensitive
                && requiredCapabilities.isEmpty()
                && preferredModuleFormats.isEmpty();
    }

    public boolean requiresCapability(GpuRuntimeCapability capability) {
        return capability != null && requiredCapabilities.contains(capability);
    }

    public Builder toBuilder() {
        return new Builder()
                .expectedItemCount(expectedItemCount)
                .preferredWorkGroupSize(preferredWorkGroupSize)
                .memoryIntensity(memoryIntensity)
                .arithmeticIntensity(arithmeticIntensity)
                .latencySensitive(latencySensitive)
                .requiredCapabilities(requiredCapabilities)
                .preferredModuleFormats(preferredModuleFormats);
    }

    private static Set<GpuRuntimeCapability> immutableCapabilitySet(Set<GpuRuntimeCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return Set.of();
        }
        EnumSet<GpuRuntimeCapability> normalized = EnumSet.noneOf(GpuRuntimeCapability.class);
        for (GpuRuntimeCapability capability : capabilities) {
            if (capability != null) {
                normalized.add(capability);
            }
        }
        return normalized.isEmpty() ? Set.of() : Collections.unmodifiableSet(normalized);
    }

    private static Set<GpuBackendModuleFormat> immutableFormatSet(Set<GpuBackendModuleFormat> formats) {
        if (formats == null || formats.isEmpty()) {
            return Set.of();
        }
        EnumSet<GpuBackendModuleFormat> normalized = EnumSet.noneOf(GpuBackendModuleFormat.class);
        for (GpuBackendModuleFormat format : formats) {
            if (format != null && format != GpuBackendModuleFormat.UNKNOWN) {
                normalized.add(format);
            }
        }
        return normalized.isEmpty() ? Set.of() : Collections.unmodifiableSet(normalized);
    }

    public static final class Builder {
        private long expectedItemCount = -1L;
        private int preferredWorkGroupSize = -1;
        private GpuRuntimeWorkloadIntensity memoryIntensity = GpuRuntimeWorkloadIntensity.UNKNOWN;
        private GpuRuntimeWorkloadIntensity arithmeticIntensity = GpuRuntimeWorkloadIntensity.UNKNOWN;
        private boolean latencySensitive;
        private Set<GpuRuntimeCapability> requiredCapabilities = Set.of();
        private Set<GpuBackendModuleFormat> preferredModuleFormats = Set.of();

        private Builder() {
        }

        public Builder expectedItemCount(long value) {
            expectedItemCount = value;
            return this;
        }

        public Builder preferredWorkGroupSize(int value) {
            preferredWorkGroupSize = value;
            return this;
        }

        public Builder memoryIntensity(GpuRuntimeWorkloadIntensity value) {
            memoryIntensity = value == null ? GpuRuntimeWorkloadIntensity.UNKNOWN : value;
            return this;
        }

        public Builder arithmeticIntensity(GpuRuntimeWorkloadIntensity value) {
            arithmeticIntensity = value == null ? GpuRuntimeWorkloadIntensity.UNKNOWN : value;
            return this;
        }

        public Builder latencySensitive(boolean value) {
            latencySensitive = value;
            return this;
        }

        public Builder requireCapability(GpuRuntimeCapability capability) {
            if (capability == null) {
                return this;
            }
            EnumSet<GpuRuntimeCapability> values = requiredCapabilities.isEmpty()
                    ? EnumSet.noneOf(GpuRuntimeCapability.class)
                    : EnumSet.copyOf(requiredCapabilities);
            values.add(capability);
            requiredCapabilities = values;
            return this;
        }

        public Builder requiredCapabilities(Set<GpuRuntimeCapability> capabilities) {
            requiredCapabilities = immutableCapabilitySet(capabilities);
            return this;
        }

        public Builder preferModuleFormat(GpuBackendModuleFormat format) {
            if (format == null || format == GpuBackendModuleFormat.UNKNOWN) {
                return this;
            }
            EnumSet<GpuBackendModuleFormat> values = preferredModuleFormats.isEmpty()
                    ? EnumSet.noneOf(GpuBackendModuleFormat.class)
                    : EnumSet.copyOf(preferredModuleFormats);
            values.add(format);
            preferredModuleFormats = values;
            return this;
        }

        public Builder preferredModuleFormats(Set<GpuBackendModuleFormat> formats) {
            preferredModuleFormats = immutableFormatSet(formats);
            return this;
        }

        public GpuRuntimeWorkloadHints build() {
            return new GpuRuntimeWorkloadHints(
                    expectedItemCount,
                    preferredWorkGroupSize,
                    memoryIntensity,
                    arithmeticIntensity,
                    latencySensitive,
                    requiredCapabilities,
                    preferredModuleFormats
            );
        }
    }
}
