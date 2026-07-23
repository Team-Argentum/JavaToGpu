package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Soft and hard device-selection preferences supplied by runtime compile options.
 *
 * <p>Explicit device overrides remain strict selectors. Preferences are lighter: preferred values add ranking score,
 * while excluded values reject matching candidates before native context creation.</p>
 */
public record GpuRuntimeDevicePreference(
        List<String> preferredDeviceIds,
        List<String> preferredVendors,
        List<String> preferredDeviceLabels,
        Set<GpuDeviceClassTarget> preferredDeviceClasses,
        List<String> excludedDeviceIds,
        List<String> excludedVendors,
        List<String> excludedDeviceLabels,
        Set<GpuDeviceClassTarget> excludedDeviceClasses
) {

    private static final int PREFERRED_DEVICE_ID_SCORE = 1_500_000_000;
    private static final int PREFERRED_DEVICE_CLASS_SCORE = 1_200_000_000;
    private static final int PREFERRED_VENDOR_SCORE = 500_000_000;
    private static final int PREFERRED_DEVICE_LABEL_SCORE = 500_000_000;

    public GpuRuntimeDevicePreference {
        preferredDeviceIds = normalizeStrings(preferredDeviceIds);
        preferredVendors = normalizeStrings(preferredVendors);
        preferredDeviceLabels = normalizeStrings(preferredDeviceLabels);
        preferredDeviceClasses = normalizeClasses(preferredDeviceClasses);
        excludedDeviceIds = normalizeStrings(excludedDeviceIds);
        excludedVendors = normalizeStrings(excludedVendors);
        excludedDeviceLabels = normalizeStrings(excludedDeviceLabels);
        excludedDeviceClasses = normalizeClasses(excludedDeviceClasses);
    }

    public static GpuRuntimeDevicePreference automatic() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean active() {
        return !preferredDeviceIds.isEmpty()
                || !preferredVendors.isEmpty()
                || !preferredDeviceLabels.isEmpty()
                || !preferredDeviceClasses.isEmpty()
                || !excludedDeviceIds.isEmpty()
                || !excludedVendors.isEmpty()
                || !excludedDeviceLabels.isEmpty()
                || !excludedDeviceClasses.isEmpty();
    }

    public GpuRuntimeDevicePreference withPreferredDeviceId(String deviceId) {
        return copyBuilder().preferDeviceId(deviceId).build();
    }

    public GpuRuntimeDevicePreference withPreferredVendor(String vendorContains) {
        return copyBuilder().preferVendor(vendorContains).build();
    }

    public GpuRuntimeDevicePreference withPreferredDeviceLabel(String labelContains) {
        return copyBuilder().preferDeviceLabel(labelContains).build();
    }

    public GpuRuntimeDevicePreference withPreferredDeviceClass(GpuDeviceClassTarget deviceClass) {
        return copyBuilder().preferDeviceClass(deviceClass).build();
    }

    public GpuRuntimeDevicePreference withExcludedDeviceId(String deviceId) {
        return copyBuilder().excludeDeviceId(deviceId).build();
    }

    public GpuRuntimeDevicePreference withExcludedVendor(String vendorContains) {
        return copyBuilder().excludeVendor(vendorContains).build();
    }

    public GpuRuntimeDevicePreference withExcludedDeviceLabel(String labelContains) {
        return copyBuilder().excludeDeviceLabel(labelContains).build();
    }

    public GpuRuntimeDevicePreference withExcludedDeviceClass(GpuDeviceClassTarget deviceClass) {
        return copyBuilder().excludeDeviceClass(deviceClass).build();
    }

    public String rejectionReason(GpuRuntimeDeviceProfile profile) {
        if (profile == null) {
            return "device profile is missing";
        }
        if (matchesExact(excludedDeviceIds, profile.deviceId())) {
            return "device id is excluded: " + profile.deviceId();
        }
        if (matchesContains(excludedVendors, profile.vendor())) {
            return "device vendor is excluded: " + profile.vendor();
        }
        if (matchesContains(excludedDeviceLabels, profile.deviceLabel())) {
            return "device label is excluded: " + profile.deviceLabel();
        }
        if (excludedDeviceClasses.contains(profile.deviceClass())) {
            return "device class is excluded: " + profile.deviceClass().name().toLowerCase(Locale.ROOT);
        }
        return "";
    }

    public int scoreAdjustment(GpuRuntimeDeviceProfile profile) {
        if (profile == null) {
            return 0;
        }
        long score = 0L;
        if (matchesExact(preferredDeviceIds, profile.deviceId())) {
            score += PREFERRED_DEVICE_ID_SCORE;
        }
        if (matchesContains(preferredVendors, profile.vendor())) {
            score += PREFERRED_VENDOR_SCORE;
        }
        if (matchesContains(preferredDeviceLabels, profile.deviceLabel())) {
            score += PREFERRED_DEVICE_LABEL_SCORE;
        }
        if (preferredDeviceClasses.contains(profile.deviceClass())) {
            score += PREFERRED_DEVICE_CLASS_SCORE;
        }
        return score > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) score;
    }

    public String describe() {
        if (!active()) {
            return "automatic";
        }
        return "preferDeviceIds=" + listOrAny(preferredDeviceIds)
                + ", preferVendors=" + listOrAny(preferredVendors)
                + ", preferDeviceLabels=" + listOrAny(preferredDeviceLabels)
                + ", preferDeviceClasses=" + classesOrAny(preferredDeviceClasses)
                + ", excludeDeviceIds=" + listOrNone(excludedDeviceIds)
                + ", excludeVendors=" + listOrNone(excludedVendors)
                + ", excludeDeviceLabels=" + listOrNone(excludedDeviceLabels)
                + ", excludeDeviceClasses=" + classesOrNone(excludedDeviceClasses);
    }

    private Builder copyBuilder() {
        return builder()
                .preferDeviceIds(preferredDeviceIds)
                .preferVendors(preferredVendors)
                .preferDeviceLabels(preferredDeviceLabels)
                .preferDeviceClasses(preferredDeviceClasses)
                .excludeDeviceIds(excludedDeviceIds)
                .excludeVendors(excludedVendors)
                .excludeDeviceLabels(excludedDeviceLabels)
                .excludeDeviceClasses(excludedDeviceClasses);
    }

    private static boolean matchesExact(List<String> selectors, String candidate) {
        String normalizedCandidate = candidate == null ? "" : candidate.trim();
        return selectors.stream().anyMatch(selector -> selector.equalsIgnoreCase(normalizedCandidate));
    }

    private static boolean matchesContains(List<String> selectors, String candidate) {
        String normalizedCandidate = candidate == null ? "" : candidate.toLowerCase(Locale.ROOT);
        return selectors.stream().anyMatch(selector -> normalizedCandidate.contains(selector.toLowerCase(Locale.ROOT)));
    }

    private static List<String> normalizeStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim());
            }
        }
        return List.copyOf(normalized);
    }

    private static Set<GpuDeviceClassTarget> normalizeClasses(Set<GpuDeviceClassTarget> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<GpuDeviceClassTarget> normalized = new LinkedHashSet<>();
        for (GpuDeviceClassTarget value : values) {
            if (value != null && value != GpuDeviceClassTarget.ANY) {
                normalized.add(value);
            }
        }
        return Set.copyOf(normalized);
    }

    private static String listOrAny(List<String> values) {
        return values.isEmpty() ? "any" : values.toString();
    }

    private static String listOrNone(List<String> values) {
        return values.isEmpty() ? "none" : values.toString();
    }

    private static String classesOrAny(Set<GpuDeviceClassTarget> values) {
        return values.isEmpty() ? "any" : classNames(values).toString();
    }

    private static String classesOrNone(Set<GpuDeviceClassTarget> values) {
        return values.isEmpty() ? "none" : classNames(values).toString();
    }

    private static List<String> classNames(Set<GpuDeviceClassTarget> values) {
        return values.stream()
                .map(value -> value.name().toLowerCase(Locale.ROOT))
                .sorted()
                .toList();
    }

    public static final class Builder {
        private final LinkedHashSet<String> preferredDeviceIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> preferredVendors = new LinkedHashSet<>();
        private final LinkedHashSet<String> preferredDeviceLabels = new LinkedHashSet<>();
        private final LinkedHashSet<GpuDeviceClassTarget> preferredDeviceClasses = new LinkedHashSet<>();
        private final LinkedHashSet<String> excludedDeviceIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> excludedVendors = new LinkedHashSet<>();
        private final LinkedHashSet<String> excludedDeviceLabels = new LinkedHashSet<>();
        private final LinkedHashSet<GpuDeviceClassTarget> excludedDeviceClasses = new LinkedHashSet<>();

        private Builder() {
        }

        public Builder preferDeviceId(String deviceId) {
            addString(preferredDeviceIds, deviceId);
            return this;
        }

        public Builder preferDeviceIds(List<String> deviceIds) {
            addStrings(preferredDeviceIds, deviceIds);
            return this;
        }

        public Builder preferVendor(String vendorContains) {
            addString(preferredVendors, vendorContains);
            return this;
        }

        public Builder preferVendors(List<String> vendors) {
            addStrings(preferredVendors, vendors);
            return this;
        }

        public Builder preferDeviceLabel(String labelContains) {
            addString(preferredDeviceLabels, labelContains);
            return this;
        }

        public Builder preferDeviceLabels(List<String> labels) {
            addStrings(preferredDeviceLabels, labels);
            return this;
        }

        public Builder preferDeviceClass(GpuDeviceClassTarget deviceClass) {
            addClass(preferredDeviceClasses, deviceClass);
            return this;
        }

        public Builder preferDeviceClasses(Set<GpuDeviceClassTarget> deviceClasses) {
            addClasses(preferredDeviceClasses, deviceClasses);
            return this;
        }

        public Builder excludeDeviceId(String deviceId) {
            addString(excludedDeviceIds, deviceId);
            return this;
        }

        public Builder excludeDeviceIds(List<String> deviceIds) {
            addStrings(excludedDeviceIds, deviceIds);
            return this;
        }

        public Builder excludeVendor(String vendorContains) {
            addString(excludedVendors, vendorContains);
            return this;
        }

        public Builder excludeVendors(List<String> vendors) {
            addStrings(excludedVendors, vendors);
            return this;
        }

        public Builder excludeDeviceLabel(String labelContains) {
            addString(excludedDeviceLabels, labelContains);
            return this;
        }

        public Builder excludeDeviceLabels(List<String> labels) {
            addStrings(excludedDeviceLabels, labels);
            return this;
        }

        public Builder excludeDeviceClass(GpuDeviceClassTarget deviceClass) {
            addClass(excludedDeviceClasses, deviceClass);
            return this;
        }

        public Builder excludeDeviceClasses(Set<GpuDeviceClassTarget> deviceClasses) {
            addClasses(excludedDeviceClasses, deviceClasses);
            return this;
        }

        public GpuRuntimeDevicePreference build() {
            return new GpuRuntimeDevicePreference(
                    List.copyOf(preferredDeviceIds),
                    List.copyOf(preferredVendors),
                    List.copyOf(preferredDeviceLabels),
                    Set.copyOf(preferredDeviceClasses),
                    List.copyOf(excludedDeviceIds),
                    List.copyOf(excludedVendors),
                    List.copyOf(excludedDeviceLabels),
                    Set.copyOf(excludedDeviceClasses)
            );
        }

        private static void addStrings(LinkedHashSet<String> target, List<String> values) {
            if (values != null) {
                values.forEach(value -> addString(target, value));
            }
        }

        private static void addString(LinkedHashSet<String> target, String value) {
            if (value != null && !value.isBlank()) {
                target.add(value.trim());
            }
        }

        private static void addClasses(
                LinkedHashSet<GpuDeviceClassTarget> target,
                Set<GpuDeviceClassTarget> values
        ) {
            if (values != null) {
                values.forEach(value -> addClass(target, value));
            }
        }

        private static void addClass(LinkedHashSet<GpuDeviceClassTarget> target, GpuDeviceClassTarget value) {
            if (value != null && value != GpuDeviceClassTarget.ANY) {
                target.add(value);
            }
        }
    }
}
