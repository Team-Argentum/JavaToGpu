package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hardware-free CUDA image/sampler descriptor contract.
 *
 * <p>This report fixes the planned CUDA resource/texture descriptor shape before the runtime starts allocating native
 * descriptor structs or calling {@code cuTexObjectCreate}/{@code cuSurfObjectCreate}. It is metadata only and must remain
 * fail-closed until descriptor memory layout, sampler metadata, and real-device evidence exist.</p>
 */
public record CudaImageSamplerDescriptorContractReport(
        List<Entry> entries,
        CudaImageSamplerObjectCreationContractReport objectCreationContract,
        boolean descriptorBuildEnabled,
        boolean nativeDescriptorAllocationEnabled,
        int activeDescriptorCount
) {

    public record Entry(
            String key,
            String javaType,
            String cudaAbiRole,
            String cudaResourceKind,
            String parameterCarrier,
            String resourceDescriptorKind,
            String resourceDescriptorDimension,
            boolean resourceDescriptorRequired,
            boolean textureDescriptorRequired,
            String textureDescriptorAddressMode,
            String textureDescriptorFilterMode,
            String textureDescriptorReadMode,
            boolean normalizedCoordinates,
            String samplerStateSource,
            String descriptorBuildStatus,
            String nativeLayoutStatus,
            boolean productionSupportEnabled
    ) {
        public Entry {
            key = normalize(key, "unknown");
            javaType = normalize(javaType, "unknown");
            cudaAbiRole = normalize(cudaAbiRole, "unknown");
            cudaResourceKind = normalize(cudaResourceKind, "unknown");
            parameterCarrier = normalize(parameterCarrier, "unknown");
            resourceDescriptorKind = normalize(resourceDescriptorKind, "not-required");
            resourceDescriptorDimension = normalize(resourceDescriptorDimension, "none");
            textureDescriptorAddressMode = normalize(textureDescriptorAddressMode, "not-required");
            textureDescriptorFilterMode = normalize(textureDescriptorFilterMode, "not-required");
            textureDescriptorReadMode = normalize(textureDescriptorReadMode, "not-required");
            samplerStateSource = normalize(samplerStateSource, "not-required");
            descriptorBuildStatus = normalize(descriptorBuildStatus, "fail-closed");
            nativeLayoutStatus = normalize(nativeLayoutStatus, "native-layout-pending");
        }

        public boolean textureEntry() {
            return "read-texture-object".equals(cudaAbiRole);
        }

        public boolean surfaceEntry() {
            return "write-surface-object".equals(cudaAbiRole);
        }

        public boolean samplerEntry() {
            return "texture-descriptor-state".equals(cudaAbiRole);
        }

        public boolean ready() {
            return !"unknown".equals(javaType)
                    && !"unknown".equals(cudaAbiRole)
                    && !"unknown".equals(cudaResourceKind)
                    && !"unknown".equals(parameterCarrier)
                    && "fail-closed".equals(descriptorBuildStatus)
                    && "native-layout-pending".equals(nativeLayoutStatus)
                    && !productionSupportEnabled
                    && (!resourceDescriptorRequired || !"not-required".equals(resourceDescriptorKind))
                    && (!textureDescriptorRequired || !"not-required".equals(textureDescriptorAddressMode));
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerDescriptor.entry"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".key", key);
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".javaType", javaType);
            fields.put(normalizedPrefix + ".cudaAbi.role", cudaAbiRole);
            fields.put(normalizedPrefix + ".cudaResource.kind", cudaResourceKind);
            fields.put(normalizedPrefix + ".parameter.carrier", parameterCarrier);
            fields.put(normalizedPrefix + ".resourceDescriptor.required", Boolean.toString(resourceDescriptorRequired));
            fields.put(normalizedPrefix + ".resourceDescriptor.kind", resourceDescriptorKind);
            fields.put(normalizedPrefix + ".resourceDescriptor.dimension", resourceDescriptorDimension);
            fields.put(normalizedPrefix + ".textureDescriptor.required", Boolean.toString(textureDescriptorRequired));
            fields.put(normalizedPrefix + ".textureDescriptor.addressMode", textureDescriptorAddressMode);
            fields.put(normalizedPrefix + ".textureDescriptor.filterMode", textureDescriptorFilterMode);
            fields.put(normalizedPrefix + ".textureDescriptor.readMode", textureDescriptorReadMode);
            fields.put(normalizedPrefix + ".textureDescriptor.normalizedCoordinates", Boolean.toString(normalizedCoordinates));
            fields.put(normalizedPrefix + ".textureDescriptor.samplerStateSource", samplerStateSource);
            fields.put(normalizedPrefix + ".descriptorBuild.status", descriptorBuildStatus);
            fields.put(normalizedPrefix + ".nativeLayout.status", nativeLayoutStatus);
            fields.put(normalizedPrefix + ".productionSupport.enabled", Boolean.toString(productionSupportEnabled));
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerDescriptorContractReport {
        entries = entries == null ? List.of() : List.copyOf(entries);
        objectCreationContract = objectCreationContract == null
                ? CudaImageSamplerObjectCreationContractReport.inspectBuiltIns()
                : objectCreationContract;
        activeDescriptorCount = Math.max(0, activeDescriptorCount);
    }

    public static CudaImageSamplerDescriptorContractReport inspectBuiltIns() {
        return new CudaImageSamplerDescriptorContractReport(
                CudaImageSamplerAbi.descriptors().stream()
                        .map(CudaImageSamplerDescriptorContractReport::entry)
                        .collect(Collectors.toUnmodifiableList()),
                CudaImageSamplerObjectCreationContractReport.inspectBuiltIns(),
                false,
                false,
                0
        );
    }

    public boolean ready() {
        return !entries.isEmpty()
                && entries.stream().allMatch(Entry::ready)
                && objectCreationContract.ready()
                && !descriptorBuildEnabled
                && !nativeDescriptorAllocationEnabled
                && activeDescriptorCount == 0;
    }

    public String status() {
        return ready() ? "ready" : "blocked";
    }

    public long entryReadyCount() {
        return entries.stream().filter(Entry::ready).count();
    }

    public long entryBlockedCount() {
        return entries.stream().filter(entry -> !entry.ready()).count();
    }

    public long textureEntryCount() {
        return entries.stream().filter(Entry::textureEntry).count();
    }

    public long surfaceEntryCount() {
        return entries.stream().filter(Entry::surfaceEntry).count();
    }

    public long samplerEntryCount() {
        return entries.stream().filter(Entry::samplerEntry).count();
    }

    public long resourceDescriptorRequiredCount() {
        return entries.stream().filter(Entry::resourceDescriptorRequired).count();
    }

    public long textureDescriptorRequiredCount() {
        return entries.stream().filter(Entry::textureDescriptorRequired).count();
    }

    public long nativeLayoutPendingCount() {
        return entries.stream().filter(entry -> "native-layout-pending".equals(entry.nativeLayoutStatus())).count();
    }

    public String firstBlocker() {
        if (entries.isEmpty()) {
            return "cuda-image-sampler-descriptor-entries-missing";
        }
        for (Entry entry : entries) {
            if (!entry.ready()) {
                return "cuda-image-sampler-descriptor-entry-not-ready:" + entry.key();
            }
        }
        if (!objectCreationContract.ready()) {
            return "cuda-image-sampler-object-creation-contract-not-ready:" + objectCreationContract.firstBlocker();
        }
        if (descriptorBuildEnabled) {
            return "cuda-image-sampler-descriptor-build-enabled-without-runtime-implementation";
        }
        if (nativeDescriptorAllocationEnabled) {
            return "cuda-image-sampler-native-descriptor-allocation-enabled-without-layout";
        }
        if (activeDescriptorCount != 0) {
            return "cuda-image-sampler-active-descriptors-unexpected:" + activeDescriptorCount;
        }
        return "none";
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerDescriptor"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        for (int index = 0; index < entries.size(); index++) {
            fields.putAll(entries.get(index).artifactFields(normalizedPrefix + ".entry." + index));
        }
        putFields(fields, "runtime.cuda.imageSamplerDescriptor");
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler descriptor contract: ").append(status()).append('\n');
        builder.append("Entries: ").append(entryReadyCount()).append('/').append(entries.size()).append(" ready").append('\n');
        builder.append("Texture entries: ").append(textureEntryCount()).append(", surface entries: ").append(surfaceEntryCount())
                .append(", sampler entries: ").append(samplerEntryCount()).append('\n');
        builder.append("Descriptors: resource=").append(resourceDescriptorRequiredCount())
                .append(", texture=").append(textureDescriptorRequiredCount())
                .append(", active=").append(activeDescriptorCount).append('\n');
        builder.append("Descriptor build enabled: ").append(descriptorBuildEnabled).append('\n');
        builder.append("Native descriptor allocation enabled: ").append(nativeDescriptorAllocationEnabled).append('\n');
        builder.append("Native layout pending entries: ").append(nativeLayoutPendingCount()).append('\n');
        builder.append("Object creation contract: ").append(objectCreationContract.status()).append('\n');
        builder.append("First blocker: ").append(firstBlocker()).append('\n');
        builder.append('\n').append("Planned descriptors:").append('\n');
        for (Entry entry : entries) {
            builder.append("- ")
                    .append(entry.key())
                    .append(": resource=")
                    .append(entry.resourceDescriptorKind())
                    .append('/')
                    .append(entry.resourceDescriptorDimension())
                    .append(", texture=")
                    .append(entry.textureDescriptorRequired() ? entry.textureDescriptorAddressMode() : "not-required")
                    .append(", status=")
                    .append(entry.descriptorBuildStatus())
                    .append('\n');
        }
        return builder.toString();
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", "true");
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".ready", Boolean.toString(ready()));
        fields.put(prefix + ".descriptorBuild.enabled", Boolean.toString(descriptorBuildEnabled));
        fields.put(prefix + ".nativeDescriptorAllocation.enabled", Boolean.toString(nativeDescriptorAllocationEnabled));
        fields.put(prefix + ".activeDescriptor.count", Integer.toString(activeDescriptorCount));
        fields.put(prefix + ".productionSupport.enabled", "false");
        fields.put(prefix + ".entry.count", Integer.toString(entries.size()));
        fields.put(prefix + ".entry.ready.count", Long.toString(entryReadyCount()));
        fields.put(prefix + ".entry.blocked.count", Long.toString(entryBlockedCount()));
        fields.put(prefix + ".entry.texture.count", Long.toString(textureEntryCount()));
        fields.put(prefix + ".entry.surface.count", Long.toString(surfaceEntryCount()));
        fields.put(prefix + ".entry.sampler.count", Long.toString(samplerEntryCount()));
        fields.put(prefix + ".resourceDescriptor.required.count", Long.toString(resourceDescriptorRequiredCount()));
        fields.put(prefix + ".textureDescriptor.required.count", Long.toString(textureDescriptorRequiredCount()));
        fields.put(prefix + ".nativeLayout.pending.count", Long.toString(nativeLayoutPendingCount()));
        fields.put(prefix + ".objectCreationContract.status", objectCreationContract.status());
        fields.put(prefix + ".objectCreationContract.ready", Boolean.toString(objectCreationContract.ready()));
        fields.put(prefix + ".firstBlocker", firstBlocker());
    }

    private static Entry entry(CudaImageSamplerAbi.Descriptor descriptor) {
        boolean sampler = descriptor.sampler();
        boolean texture = descriptor.readTextureObject();
        boolean surface = descriptor.writeSurfaceObject();
        boolean textureDescriptorRequired = texture || sampler;
        return new Entry(
                descriptor.key(),
                descriptor.javaQualifiedName(),
                descriptor.cudaAbiRole(),
                descriptor.cudaResourceKind(),
                descriptor.parameterCarrier(),
                resourceDescriptorKind(descriptor.cudaResourceKind(), sampler),
                resourceDescriptorDimension(descriptor.cudaResourceKind(), sampler),
                !sampler,
                textureDescriptorRequired,
                textureDescriptorRequired ? "clamp-to-edge" : "not-required",
                textureDescriptorRequired ? "point" : "not-required",
                textureDescriptorRequired ? "element-type" : "not-required",
                false,
                samplerStateSource(sampler, texture, surface),
                "fail-closed",
                "native-layout-pending",
                descriptor.productionSupportEnabled()
        );
    }

    private static String resourceDescriptorKind(String resourceKind, boolean sampler) {
        if (sampler) {
            return "not-required";
        }
        if (resourceKind.contains("mipmapped")) {
            return "CUDA_RESOURCE_TYPE_MIPMAPPED_ARRAY";
        }
        if (resourceKind.contains("linear-memory-or-staged-array")) {
            return "CUDA_RESOURCE_TYPE_ARRAY_STAGING_PENDING";
        }
        if (resourceKind.contains("linear")) {
            return "CUDA_RESOURCE_TYPE_LINEAR";
        }
        return "CUDA_RESOURCE_TYPE_ARRAY";
    }

    private static String resourceDescriptorDimension(String resourceKind, boolean sampler) {
        if (sampler) {
            return "none";
        }
        if (resourceKind.contains("3d")) {
            return "3d";
        }
        if (resourceKind.contains("2d")) {
            return "2d";
        }
        if (resourceKind.contains("1d") || resourceKind.contains("linear")) {
            return "1d";
        }
        return "unknown";
    }

    private static String samplerStateSource(boolean sampler, boolean texture, boolean surface) {
        if (sampler) {
            return "folded-sampler-parameter-default";
        }
        if (texture) {
            return "nearest-clamp-to-edge-default-until-sampler-metadata-exists";
        }
        if (surface) {
            return "not-required-for-surface-object";
        }
        return "not-required";
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
