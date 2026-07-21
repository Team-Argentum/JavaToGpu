package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hardware-free native descriptor layout preview for future CUDA image/sampler binding.
 *
 * <p>This class intentionally records logical field and size/offset policies only. It does not allocate native
 * descriptor memory, does not encode CUDA SDK struct bytes, and does not enable texture/surface object creation.</p>
 */
public record CudaImageSamplerNativeDescriptorLayoutReport(
        List<Entry> entries,
        CudaImageSamplerDescriptorContractReport descriptorContract,
        boolean nativeLayoutBuildEnabled,
        boolean nativeDescriptorAllocationEnabled,
        int activeNativeDescriptorCount
) {

    public record Entry(
            String key,
            String javaType,
            String cudaAbiRole,
            String cudaResourceKind,
            String resourceDescriptorKind,
            String resourceDescriptorDimension,
            boolean resourceLayoutRequired,
            String resourceLayoutStruct,
            int resourceLayoutFieldCount,
            String resourceLayoutSizePolicy,
            String resourceLayoutOffsetPolicy,
            boolean textureLayoutRequired,
            String textureLayoutStruct,
            int textureLayoutFieldCount,
            String textureLayoutSizePolicy,
            String textureLayoutOffsetPolicy,
            String nativeLayoutStatus,
            String nativeAllocationStatus,
            String runtimeBindingStatus,
            boolean productionSupportEnabled
    ) {
        public Entry {
            key = normalize(key, "unknown");
            javaType = normalize(javaType, "unknown");
            cudaAbiRole = normalize(cudaAbiRole, "unknown");
            cudaResourceKind = normalize(cudaResourceKind, "unknown");
            resourceDescriptorKind = normalize(resourceDescriptorKind, "not-required");
            resourceDescriptorDimension = normalize(resourceDescriptorDimension, "none");
            resourceLayoutStruct = normalize(resourceLayoutStruct, "not-required");
            resourceLayoutFieldCount = Math.max(0, resourceLayoutFieldCount);
            resourceLayoutSizePolicy = normalize(resourceLayoutSizePolicy, "not-required");
            resourceLayoutOffsetPolicy = normalize(resourceLayoutOffsetPolicy, "not-required");
            textureLayoutStruct = normalize(textureLayoutStruct, "not-required");
            textureLayoutFieldCount = Math.max(0, textureLayoutFieldCount);
            textureLayoutSizePolicy = normalize(textureLayoutSizePolicy, "not-required");
            textureLayoutOffsetPolicy = normalize(textureLayoutOffsetPolicy, "not-required");
            nativeLayoutStatus = normalize(nativeLayoutStatus, "preview-only");
            nativeAllocationStatus = normalize(nativeAllocationStatus, "disabled");
            runtimeBindingStatus = normalize(runtimeBindingStatus, "fail-closed");
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
                    && "preview-only".equals(nativeLayoutStatus)
                    && "disabled".equals(nativeAllocationStatus)
                    && "fail-closed".equals(runtimeBindingStatus)
                    && !productionSupportEnabled
                    && (!resourceLayoutRequired || ("CUDA_RESOURCE_DESC".equals(resourceLayoutStruct) && resourceLayoutFieldCount > 0))
                    && (!textureLayoutRequired || ("CUDA_TEXTURE_DESC".equals(textureLayoutStruct) && textureLayoutFieldCount > 0));
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerNativeDescriptorLayout.entry"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".key", key);
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".javaType", javaType);
            fields.put(normalizedPrefix + ".cudaAbi.role", cudaAbiRole);
            fields.put(normalizedPrefix + ".cudaResource.kind", cudaResourceKind);
            fields.put(normalizedPrefix + ".resourceDescriptor.kind", resourceDescriptorKind);
            fields.put(normalizedPrefix + ".resourceDescriptor.dimension", resourceDescriptorDimension);
            fields.put(normalizedPrefix + ".resourceLayout.required", Boolean.toString(resourceLayoutRequired));
            fields.put(normalizedPrefix + ".resourceLayout.struct", resourceLayoutStruct);
            fields.put(normalizedPrefix + ".resourceLayout.field.count", Integer.toString(resourceLayoutFieldCount));
            fields.put(normalizedPrefix + ".resourceLayout.sizePolicy", resourceLayoutSizePolicy);
            fields.put(normalizedPrefix + ".resourceLayout.offsetPolicy", resourceLayoutOffsetPolicy);
            fields.put(normalizedPrefix + ".textureLayout.required", Boolean.toString(textureLayoutRequired));
            fields.put(normalizedPrefix + ".textureLayout.struct", textureLayoutStruct);
            fields.put(normalizedPrefix + ".textureLayout.field.count", Integer.toString(textureLayoutFieldCount));
            fields.put(normalizedPrefix + ".textureLayout.sizePolicy", textureLayoutSizePolicy);
            fields.put(normalizedPrefix + ".textureLayout.offsetPolicy", textureLayoutOffsetPolicy);
            fields.put(normalizedPrefix + ".nativeLayout.status", nativeLayoutStatus);
            fields.put(normalizedPrefix + ".nativeAllocation.status", nativeAllocationStatus);
            fields.put(normalizedPrefix + ".runtimeBinding.status", runtimeBindingStatus);
            fields.put(normalizedPrefix + ".productionSupport.enabled", Boolean.toString(productionSupportEnabled));
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerNativeDescriptorLayoutReport {
        entries = entries == null ? List.of() : List.copyOf(entries);
        descriptorContract = descriptorContract == null
                ? CudaImageSamplerDescriptorContractReport.inspectBuiltIns()
                : descriptorContract;
        activeNativeDescriptorCount = Math.max(0, activeNativeDescriptorCount);
    }

    public static CudaImageSamplerNativeDescriptorLayoutReport inspectBuiltIns() {
        CudaImageSamplerDescriptorContractReport descriptorContract = CudaImageSamplerDescriptorContractReport.inspectBuiltIns();
        return new CudaImageSamplerNativeDescriptorLayoutReport(
                descriptorContract.entries().stream()
                        .map(CudaImageSamplerNativeDescriptorLayoutReport::entry)
                        .collect(Collectors.toUnmodifiableList()),
                descriptorContract,
                false,
                false,
                0
        );
    }

    public boolean ready() {
        return !entries.isEmpty()
                && entries.stream().allMatch(Entry::ready)
                && descriptorContract.ready()
                && !nativeLayoutBuildEnabled
                && !nativeDescriptorAllocationEnabled
                && activeNativeDescriptorCount == 0;
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

    public long resourceLayoutRequiredCount() {
        return entries.stream().filter(Entry::resourceLayoutRequired).count();
    }

    public long textureLayoutRequiredCount() {
        return entries.stream().filter(Entry::textureLayoutRequired).count();
    }

    public long resourceLayoutFieldCount() {
        return entries.stream().mapToLong(Entry::resourceLayoutFieldCount).sum();
    }

    public long textureLayoutFieldCount() {
        return entries.stream().mapToLong(Entry::textureLayoutFieldCount).sum();
    }

    public long nativeLayoutPreviewCount() {
        return entries.stream().filter(entry -> "preview-only".equals(entry.nativeLayoutStatus())).count();
    }

    public String firstBlocker() {
        if (entries.isEmpty()) {
            return "cuda-image-sampler-native-descriptor-layout-entries-missing";
        }
        for (Entry entry : entries) {
            if (!entry.ready()) {
                return "cuda-image-sampler-native-descriptor-layout-entry-not-ready:" + entry.key();
            }
        }
        if (!descriptorContract.ready()) {
            return "cuda-image-sampler-descriptor-contract-not-ready:" + descriptorContract.firstBlocker();
        }
        if (nativeLayoutBuildEnabled) {
            return "cuda-image-sampler-native-descriptor-layout-build-enabled-without-native-layout";
        }
        if (nativeDescriptorAllocationEnabled) {
            return "cuda-image-sampler-native-descriptor-allocation-enabled-without-runtime-implementation";
        }
        if (activeNativeDescriptorCount != 0) {
            return "cuda-image-sampler-active-native-descriptors-unexpected:" + activeNativeDescriptorCount;
        }
        return "none";
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerNativeDescriptorLayout"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        for (int index = 0; index < entries.size(); index++) {
            fields.putAll(entries.get(index).artifactFields(normalizedPrefix + ".entry." + index));
        }
        putFields(fields, "runtime.cuda.imageSamplerNativeDescriptorLayout");
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler native descriptor layout: ").append(status()).append('\n');
        builder.append("Entries: ").append(entryReadyCount()).append('/').append(entries.size()).append(" ready").append('\n');
        builder.append("Resource layouts: ").append(resourceLayoutRequiredCount())
                .append(" entries, ").append(resourceLayoutFieldCount()).append(" fields").append('\n');
        builder.append("Texture layouts: ").append(textureLayoutRequiredCount())
                .append(" entries, ").append(textureLayoutFieldCount()).append(" fields").append('\n');
        builder.append("Native layout build enabled: ").append(nativeLayoutBuildEnabled).append('\n');
        builder.append("Native descriptor allocation enabled: ").append(nativeDescriptorAllocationEnabled).append('\n');
        builder.append("Active native descriptors: ").append(activeNativeDescriptorCount).append('\n');
        builder.append("Descriptor contract: ").append(descriptorContract.status()).append('\n');
        builder.append("First blocker: ").append(firstBlocker()).append('\n');
        builder.append('\n').append("Layout preview:").append('\n');
        for (Entry entry : entries) {
            builder.append("- ")
                    .append(entry.key())
                    .append(": resourceFields=")
                    .append(entry.resourceLayoutFieldCount())
                    .append(", textureFields=")
                    .append(entry.textureLayoutFieldCount())
                    .append(", status=")
                    .append(entry.nativeLayoutStatus())
                    .append('\n');
        }
        return builder.toString();
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", "true");
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".ready", Boolean.toString(ready()));
        fields.put(prefix + ".nativeLayoutBuild.enabled", Boolean.toString(nativeLayoutBuildEnabled));
        fields.put(prefix + ".nativeDescriptorAllocation.enabled", Boolean.toString(nativeDescriptorAllocationEnabled));
        fields.put(prefix + ".activeNativeDescriptor.count", Integer.toString(activeNativeDescriptorCount));
        fields.put(prefix + ".productionSupport.enabled", "false");
        fields.put(prefix + ".entry.count", Integer.toString(entries.size()));
        fields.put(prefix + ".entry.ready.count", Long.toString(entryReadyCount()));
        fields.put(prefix + ".entry.blocked.count", Long.toString(entryBlockedCount()));
        fields.put(prefix + ".entry.texture.count", Long.toString(textureEntryCount()));
        fields.put(prefix + ".entry.surface.count", Long.toString(surfaceEntryCount()));
        fields.put(prefix + ".entry.sampler.count", Long.toString(samplerEntryCount()));
        fields.put(prefix + ".resourceLayout.required.count", Long.toString(resourceLayoutRequiredCount()));
        fields.put(prefix + ".resourceLayout.field.count", Long.toString(resourceLayoutFieldCount()));
        fields.put(prefix + ".textureLayout.required.count", Long.toString(textureLayoutRequiredCount()));
        fields.put(prefix + ".textureLayout.field.count", Long.toString(textureLayoutFieldCount()));
        fields.put(prefix + ".nativeLayout.preview.count", Long.toString(nativeLayoutPreviewCount()));
        fields.put(prefix + ".descriptorContract.status", descriptorContract.status());
        fields.put(prefix + ".descriptorContract.ready", Boolean.toString(descriptorContract.ready()));
        fields.put(prefix + ".firstBlocker", firstBlocker());
    }

    private static Entry entry(CudaImageSamplerDescriptorContractReport.Entry descriptor) {
        boolean resourceRequired = descriptor.resourceDescriptorRequired();
        boolean textureRequired = descriptor.textureDescriptorRequired();
        return new Entry(
                descriptor.key(),
                descriptor.javaType(),
                descriptor.cudaAbiRole(),
                descriptor.cudaResourceKind(),
                descriptor.resourceDescriptorKind(),
                descriptor.resourceDescriptorDimension(),
                resourceRequired,
                resourceRequired ? "CUDA_RESOURCE_DESC" : "not-required",
                resourceLayoutFieldCount(descriptor.resourceDescriptorKind()),
                resourceRequired ? "sizeof(CUDA_RESOURCE_DESC)-driver-abi" : "not-required",
                resourceOffsetPolicy(descriptor.resourceDescriptorKind()),
                textureRequired,
                textureRequired ? "CUDA_TEXTURE_DESC" : "not-required",
                textureRequired ? 6 : 0,
                textureRequired ? "sizeof(CUDA_TEXTURE_DESC)-driver-abi" : "not-required",
                textureRequired ? "addressMode[3]+filterMode+flags+readMode" : "not-required",
                "preview-only",
                "disabled",
                "fail-closed",
                descriptor.productionSupportEnabled()
        );
    }

    private static int resourceLayoutFieldCount(String resourceDescriptorKind) {
        return switch (resourceDescriptorKind) {
            case "CUDA_RESOURCE_TYPE_LINEAR" -> 5;
            case "not-required" -> 0;
            default -> 2;
        };
    }

    private static String resourceOffsetPolicy(String resourceDescriptorKind) {
        return switch (resourceDescriptorKind) {
            case "CUDA_RESOURCE_TYPE_ARRAY" -> "resType+res.array.hArray";
            case "CUDA_RESOURCE_TYPE_MIPMAPPED_ARRAY" -> "resType+res.mipmap.hMipmappedArray";
            case "CUDA_RESOURCE_TYPE_LINEAR" -> "resType+res.linear.devPtr+format+numChannels+sizeInBytes";
            case "CUDA_RESOURCE_TYPE_ARRAY_STAGING_PENDING" -> "resType+staged-array-resource-handle-pending";
            default -> "not-required";
        };
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
