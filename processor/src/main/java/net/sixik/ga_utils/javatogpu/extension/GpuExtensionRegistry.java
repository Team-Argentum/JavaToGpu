package net.sixik.ga_utils.javatogpu.extension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Fail-closed registry for loaded compiler and runtime extensions.
 */
public final class GpuExtensionRegistry {

    private static final GpuExtensionRegistry EMPTY = new GpuExtensionRegistry(List.of());

    private final List<GpuExtensionDescriptor> descriptors;

    private GpuExtensionRegistry(List<GpuExtensionDescriptor> descriptors) {
        this.descriptors = List.copyOf(descriptors);
    }

    public static GpuExtensionRegistry empty() {
        return EMPTY;
    }

    public static GpuExtensionRegistry of(Collection<? extends GpuExtension> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return empty();
        }

        ArrayList<GpuExtensionDescriptor> descriptors = new ArrayList<>();
        LinkedHashMap<String, GpuExtensionDescriptor> descriptorsById = new LinkedHashMap<>();
        for (GpuExtension extension : extensions) {
            GpuExtensionDescriptor descriptor = GpuExtensionDescriptor.from(
                    Objects.requireNonNull(extension, "extension")
            );
            GpuExtensionDescriptor previous = descriptorsById.putIfAbsent(descriptor.id(), descriptor);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate GPU extension id '" + descriptor.id() + "': "
                                + previous.implementationClass() + " and " + descriptor.implementationClass()
                );
            }
            descriptors.add(descriptor);
        }
        descriptors.sort(Comparator
                .comparingInt(GpuExtensionDescriptor::order)
                .thenComparing(GpuExtensionDescriptor::id)
                .thenComparing(GpuExtensionDescriptor::version)
                .thenComparing(GpuExtensionDescriptor::implementationClass));
        return new GpuExtensionRegistry(descriptors);
    }

    public List<GpuExtensionDescriptor> descriptors() {
        return descriptors;
    }

    public int size() {
        return descriptors.size();
    }

    public Optional<GpuExtensionDescriptor> findDescriptor(String extensionId) {
        if (extensionId == null || extensionId.isBlank()) {
            return Optional.empty();
        }
        return descriptors.stream()
                .filter(descriptor -> descriptor.id().equals(extensionId))
                .findFirst();
    }

    public GpuExtensionDescriptor requireDescriptor(String extensionId) {
        return findDescriptor(extensionId).orElseThrow(() -> new IllegalArgumentException(
                "Unknown GPU extension id '" + extensionId + "'"
        ));
    }

    public void requirePipelineContract(
            String pipelineName,
            GpuExtensionPhase requiredPhase,
            GpuExtensionPermission maximumPermission,
            GpuExtensionCapability requiredCapability
    ) {
        String normalizedPipelineName = pipelineName == null || pipelineName.isBlank()
                ? "extension pipeline"
                : pipelineName.trim();
        Objects.requireNonNull(requiredPhase, "requiredPhase");
        Objects.requireNonNull(maximumPermission, "maximumPermission");
        Objects.requireNonNull(requiredCapability, "requiredCapability");
        for (GpuExtensionDescriptor descriptor : descriptors) {
            if (descriptor.phase() != requiredPhase) {
                throw new IllegalArgumentException(
                        "GPU extension '" + descriptor.id() + "' declares phase " + descriptor.phase()
                                + " but " + normalizedPipelineName + " requires " + requiredPhase
                );
            }
            if (!descriptor.permission().isAtMost(maximumPermission)) {
                throw new IllegalArgumentException(
                        "GPU extension '" + descriptor.id() + "' permission " + descriptor.permission()
                                + " exceeds " + normalizedPipelineName + " maximum " + maximumPermission
                );
            }
            if (!descriptor.capabilities().contains(requiredCapability)) {
                throw new IllegalArgumentException(
                        "GPU extension '" + descriptor.id() + "' does not declare required capability "
                                + requiredCapability + " for " + normalizedPipelineName
                );
            }
        }
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "extensions" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".count", Integer.toString(descriptors.size()));
        for (int index = 0; index < descriptors.size(); index++) {
            GpuExtensionDescriptor descriptor = descriptors.get(index);
            String entryPrefix = normalizedPrefix + "." + index;
            fields.put(entryPrefix + ".id", descriptor.id());
            fields.put(entryPrefix + ".version", descriptor.version());
            fields.put(entryPrefix + ".order", Integer.toString(descriptor.order()));
            fields.put(entryPrefix + ".phase", descriptor.phase().name());
            fields.put(entryPrefix + ".permission", descriptor.permission().name());
            fields.put(entryPrefix + ".implementationClass", descriptor.implementationClass());
            fields.put(entryPrefix + ".capability.count", Integer.toString(descriptor.capabilities().size()));
            for (int capabilityIndex = 0; capabilityIndex < descriptor.capabilities().size(); capabilityIndex++) {
                fields.put(
                        entryPrefix + ".capability." + capabilityIndex,
                        descriptor.capabilities().get(capabilityIndex).name()
                );
            }
        }
        return Collections.unmodifiableMap(fields);
    }
}
