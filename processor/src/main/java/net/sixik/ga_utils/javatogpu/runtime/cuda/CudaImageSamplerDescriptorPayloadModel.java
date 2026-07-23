package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Java-only CUDA image/sampler descriptor payload model.
 *
 * <p>This model creates logical Java payloads for future {@code CUDA_RESOURCE_DESC} and {@code CUDA_TEXTURE_DESC}
 * builders. It does not allocate native memory, does not encode CUDA SDK structs, does not bind CUDA handles, and does
 * not create texture/surface objects.</p>
 */
public record CudaImageSamplerDescriptorPayloadModel(
        List<Entry> entries,
        String descriptorBuildPlanStatus,
        boolean descriptorBuildPlanReady,
        boolean javaDescriptorPayloadBuildEnabled,
        boolean nativeDescriptorAllocationEnabled,
        boolean objectCreationEnabled,
        boolean runtimeBindingEnabled,
        int activeNativeDescriptorCount
) {

    public record ResourcePayload(
            String structName,
            String resourceDescriptorKind,
            String resourceDescriptorDimension,
            int fieldCount,
            String fieldPolicy,
            String handleSource,
            String cudaHandleBindingStatus,
            boolean javaHandlePresent,
            int width,
            int height,
            int depth,
            int layers,
            int mipLevels,
            int sampleCount,
            boolean backingBufferHandlePresent
    ) {
        public ResourcePayload {
            structName = normalize(structName, "CUDA_RESOURCE_DESC");
            resourceDescriptorKind = normalize(resourceDescriptorKind, "unknown");
            resourceDescriptorDimension = normalize(resourceDescriptorDimension, "unknown");
            fieldCount = Math.max(0, fieldCount);
            fieldPolicy = normalize(fieldPolicy, "unknown");
            handleSource = normalize(handleSource, "java-wrapper-handle");
            cudaHandleBindingStatus = normalize(cudaHandleBindingStatus, "not-native-bound");
            width = Math.max(0, width);
            height = Math.max(0, height);
            depth = Math.max(0, depth);
            layers = Math.max(0, layers);
            mipLevels = Math.max(0, mipLevels);
            sampleCount = Math.max(0, sampleCount);
        }

        public boolean ready() {
            return "CUDA_RESOURCE_DESC".equals(structName)
                    && !"unknown".equals(resourceDescriptorKind)
                    && fieldCount > 0
                    && javaHandlePresent
                    && !"native-bound".equals(cudaHandleBindingStatus);
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerDescriptorPayloadModel.resourcePayload"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".present", "true");
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".struct", structName);
            fields.put(normalizedPrefix + ".resourceDescriptor.kind", resourceDescriptorKind);
            fields.put(normalizedPrefix + ".resourceDescriptor.dimension", resourceDescriptorDimension);
            fields.put(normalizedPrefix + ".field.count", Integer.toString(fieldCount));
            fields.put(normalizedPrefix + ".field.policy", fieldPolicy);
            fields.put(normalizedPrefix + ".handle.source", handleSource);
            fields.put(normalizedPrefix + ".cudaHandleBinding.status", cudaHandleBindingStatus);
            fields.put(normalizedPrefix + ".javaHandle.present", Boolean.toString(javaHandlePresent));
            fields.put(normalizedPrefix + ".metadata.width", Integer.toString(width));
            fields.put(normalizedPrefix + ".metadata.height", Integer.toString(height));
            fields.put(normalizedPrefix + ".metadata.depth", Integer.toString(depth));
            fields.put(normalizedPrefix + ".metadata.layers", Integer.toString(layers));
            fields.put(normalizedPrefix + ".metadata.mipLevels", Integer.toString(mipLevels));
            fields.put(normalizedPrefix + ".metadata.sampleCount", Integer.toString(sampleCount));
            fields.put(normalizedPrefix + ".backingBufferHandle.present", Boolean.toString(backingBufferHandlePresent));
            return Collections.unmodifiableMap(fields);
        }
    }

    public record TexturePayload(
            String structName,
            int fieldCount,
            String addressModeX,
            String addressModeY,
            String addressModeZ,
            String filterMode,
            String readMode,
            boolean normalizedCoordinates,
            String samplerStateSource,
            String cudaHandleBindingStatus
    ) {
        public TexturePayload {
            structName = normalize(structName, "CUDA_TEXTURE_DESC");
            fieldCount = Math.max(0, fieldCount);
            addressModeX = normalize(addressModeX, "clamp-to-edge");
            addressModeY = normalize(addressModeY, "clamp-to-edge");
            addressModeZ = normalize(addressModeZ, "clamp-to-edge");
            filterMode = normalize(filterMode, "point");
            readMode = normalize(readMode, "element-type");
            samplerStateSource = normalize(samplerStateSource, "nearest-clamp-to-edge-default-until-sampler-metadata-exists");
            cudaHandleBindingStatus = normalize(cudaHandleBindingStatus, "not-native-bound");
        }

        public boolean ready() {
            return "CUDA_TEXTURE_DESC".equals(structName)
                    && fieldCount > 0
                    && !"native-bound".equals(cudaHandleBindingStatus);
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerDescriptorPayloadModel.texturePayload"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".present", "true");
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".struct", structName);
            fields.put(normalizedPrefix + ".field.count", Integer.toString(fieldCount));
            fields.put(normalizedPrefix + ".addressMode.x", addressModeX);
            fields.put(normalizedPrefix + ".addressMode.y", addressModeY);
            fields.put(normalizedPrefix + ".addressMode.z", addressModeZ);
            fields.put(normalizedPrefix + ".filterMode", filterMode);
            fields.put(normalizedPrefix + ".readMode", readMode);
            fields.put(normalizedPrefix + ".normalizedCoordinates", Boolean.toString(normalizedCoordinates));
            fields.put(normalizedPrefix + ".samplerState.source", samplerStateSource);
            fields.put(normalizedPrefix + ".cudaHandleBinding.status", cudaHandleBindingStatus);
            return Collections.unmodifiableMap(fields);
        }
    }

    public record Entry(
            int parameterIndex,
            String parameterName,
            String javaType,
            String abiKey,
            String cudaAbiRole,
            boolean resourcePayloadRequired,
            ResourcePayload resourcePayload,
            boolean texturePayloadRequired,
            TexturePayload texturePayload,
            String descriptorPayloadStatus,
            String nativeDescriptorAllocationStatus,
            String objectCreationStatus,
            String runtimeBindingStatus,
            boolean productionSupportEnabled,
            String firstBlocker
    ) {
        public Entry {
            parameterIndex = Math.max(0, parameterIndex);
            parameterName = normalize(parameterName, "unknown");
            javaType = normalize(javaType, "unknown");
            abiKey = normalize(abiKey, "unknown");
            cudaAbiRole = normalize(cudaAbiRole, "unknown");
            descriptorPayloadStatus = normalize(descriptorPayloadStatus, "blocked");
            nativeDescriptorAllocationStatus = normalize(nativeDescriptorAllocationStatus, "disabled");
            objectCreationStatus = normalize(objectCreationStatus, "disabled");
            runtimeBindingStatus = normalize(runtimeBindingStatus, "fail-closed");
            firstBlocker = normalize(firstBlocker, "none");
        }

        public boolean resourcePayloadPresent() {
            return resourcePayload != null;
        }

        public boolean texturePayloadPresent() {
            return texturePayload != null;
        }

        public boolean sampler() {
            return "texture-descriptor-state".equals(cudaAbiRole);
        }

        public boolean ready() {
            return "java-payload-built".equals(descriptorPayloadStatus)
                    && "disabled".equals(nativeDescriptorAllocationStatus)
                    && "disabled".equals(objectCreationStatus)
                    && "fail-closed".equals(runtimeBindingStatus)
                    && !productionSupportEnabled
                    && "none".equals(firstBlocker)
                    && (!resourcePayloadRequired || (resourcePayload != null && resourcePayload.ready()))
                    && (!texturePayloadRequired || (texturePayload != null && texturePayload.ready()));
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerDescriptorPayloadModel.entry"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".parameter.index", Integer.toString(parameterIndex));
            fields.put(normalizedPrefix + ".parameter.name", parameterName);
            fields.put(normalizedPrefix + ".parameter.javaType", javaType);
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".abi.key", abiKey);
            fields.put(normalizedPrefix + ".abi.role", cudaAbiRole);
            fields.put(normalizedPrefix + ".resourcePayload.required", Boolean.toString(resourcePayloadRequired));
            fields.put(normalizedPrefix + ".resourcePayload.present", Boolean.toString(resourcePayloadPresent()));
            if (resourcePayload != null) {
                fields.putAll(resourcePayload.artifactFields(normalizedPrefix + ".resourcePayload"));
            }
            fields.put(normalizedPrefix + ".texturePayload.required", Boolean.toString(texturePayloadRequired));
            fields.put(normalizedPrefix + ".texturePayload.present", Boolean.toString(texturePayloadPresent()));
            if (texturePayload != null) {
                fields.putAll(texturePayload.artifactFields(normalizedPrefix + ".texturePayload"));
            }
            fields.put(normalizedPrefix + ".descriptorPayload.status", descriptorPayloadStatus);
            fields.put(normalizedPrefix + ".nativeDescriptorAllocation.status", nativeDescriptorAllocationStatus);
            fields.put(normalizedPrefix + ".objectCreation.status", objectCreationStatus);
            fields.put(normalizedPrefix + ".runtimeBinding.status", runtimeBindingStatus);
            fields.put(normalizedPrefix + ".productionSupport.enabled", Boolean.toString(productionSupportEnabled));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerDescriptorPayloadModel {
        entries = entries == null ? List.of() : List.copyOf(entries);
        descriptorBuildPlanStatus = normalize(descriptorBuildPlanStatus, "not-present");
        activeNativeDescriptorCount = Math.max(0, activeNativeDescriptorCount);
    }

    static CudaImageSamplerDescriptorPayloadModel empty() {
        return new CudaImageSamplerDescriptorPayloadModel(
                List.of(),
                "not-present",
                false,
                false,
                false,
                false,
                false,
                0
        );
    }

    static CudaImageSamplerDescriptorPayloadModel from(CudaImageSamplerDescriptorBuildPlan buildPlan) {
        if (buildPlan == null || !buildPlan.present()) {
            return empty();
        }
        return new CudaImageSamplerDescriptorPayloadModel(
                buildPlan.entries().stream()
                        .map(CudaImageSamplerDescriptorPayloadModel::entry)
                        .collect(Collectors.toUnmodifiableList()),
                buildPlan.status(),
                buildPlan.ready(),
                true,
                false,
                false,
                false,
                0
        );
    }

    public boolean present() {
        return !entries.isEmpty();
    }

    public boolean ready() {
        return present()
                && entries.stream().allMatch(Entry::ready)
                && "ready".equals(descriptorBuildPlanStatus)
                && descriptorBuildPlanReady
                && javaDescriptorPayloadBuildEnabled
                && !nativeDescriptorAllocationEnabled
                && !objectCreationEnabled
                && !runtimeBindingEnabled
                && activeNativeDescriptorCount == 0;
    }

    public String status() {
        if (!present()) {
            return "not-present";
        }
        return ready() ? "ready" : "blocked";
    }

    public long entryReadyCount() {
        return entries.stream().filter(Entry::ready).count();
    }

    public long entryBlockedCount() {
        return entries.stream().filter(entry -> !entry.ready()).count();
    }

    public long resourcePayloadBuiltCount() {
        return entries.stream().filter(Entry::resourcePayloadPresent).count();
    }

    public long texturePayloadBuiltCount() {
        return entries.stream().filter(Entry::texturePayloadPresent).count();
    }

    public long samplerPayloadCount() {
        return entries.stream().filter(Entry::sampler).filter(Entry::texturePayloadPresent).count();
    }

    public long blockedPayloadCount() {
        return entries.stream().filter(entry -> !entry.ready()).count();
    }

    public String firstBlocker() {
        if (!present()) {
            return "none";
        }
        return entries.stream()
                .map(Entry::firstBlocker)
                .filter(blocker -> !"none".equals(blocker))
                .findFirst()
                .orElseGet(() -> ready() ? "none" : "cuda-image-sampler-descriptor-payload-model-not-ready");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerDescriptorPayloadModel"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerDescriptorPayloadModel");
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", Boolean.toString(present()));
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".ready", Boolean.toString(ready()));
        fields.put(prefix + ".descriptorBuildPlan.status", descriptorBuildPlanStatus);
        fields.put(prefix + ".descriptorBuildPlan.ready", Boolean.toString(descriptorBuildPlanReady));
        fields.put(prefix + ".javaDescriptorPayloadBuild.enabled", Boolean.toString(javaDescriptorPayloadBuildEnabled));
        fields.put(prefix + ".nativeDescriptorAllocation.enabled", Boolean.toString(nativeDescriptorAllocationEnabled));
        fields.put(prefix + ".objectCreation.enabled", Boolean.toString(objectCreationEnabled));
        fields.put(prefix + ".runtimeBinding.enabled", Boolean.toString(runtimeBindingEnabled));
        fields.put(prefix + ".activeNativeDescriptor.count", Integer.toString(activeNativeDescriptorCount));
        fields.put(prefix + ".entry.count", Integer.toString(entries.size()));
        fields.put(prefix + ".entry.ready.count", Long.toString(entryReadyCount()));
        fields.put(prefix + ".entry.blocked.count", Long.toString(entryBlockedCount()));
        fields.put(prefix + ".resourcePayload.built.count", Long.toString(resourcePayloadBuiltCount()));
        fields.put(prefix + ".texturePayload.built.count", Long.toString(texturePayloadBuiltCount()));
        fields.put(prefix + ".samplerPayload.built.count", Long.toString(samplerPayloadCount()));
        fields.put(prefix + ".payload.blocked.count", Long.toString(blockedPayloadCount()));
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < entries.size(); index++) {
            fields.putAll(entries.get(index).artifactFields(prefix + ".entry." + index));
        }
    }

    private static Entry entry(CudaImageSamplerDescriptorBuildPlan.Entry buildEntry) {
        if (buildEntry == null) {
            return new Entry(
                    0,
                    "unknown",
                    "unknown",
                    "unknown",
                    "unknown",
                    false,
                    null,
                    false,
                    null,
                    "blocked",
                    "disabled",
                    "disabled",
                    "fail-closed",
                    false,
                    "cuda-image-sampler-descriptor-build-plan-entry-missing"
            );
        }
        boolean buildReady = buildEntry.ready();
        return new Entry(
                buildEntry.parameterIndex(),
                buildEntry.parameterName(),
                buildEntry.javaType(),
                buildEntry.abiKey(),
                buildEntry.cudaAbiRole(),
                buildEntry.resourceLayoutRequired(),
                buildReady && buildEntry.resourceLayoutRequired() ? resourcePayload(buildEntry) : null,
                buildEntry.textureLayoutRequired(),
                buildReady && buildEntry.textureLayoutRequired() ? texturePayload(buildEntry) : null,
                buildReady ? "java-payload-built" : "blocked",
                "disabled",
                "disabled",
                buildEntry.runtimeBindingStatus(),
                buildEntry.productionSupportEnabled(),
                buildReady ? "none" : buildEntry.firstBlocker()
        );
    }

    private static ResourcePayload resourcePayload(CudaImageSamplerDescriptorBuildPlan.Entry buildEntry) {
        return new ResourcePayload(
                buildEntry.resourceLayoutStruct(),
                buildEntry.resourceDescriptorKind(),
                buildEntry.resourceDescriptorDimension(),
                buildEntry.resourceLayoutFieldCount(),
                resourceFieldPolicy(buildEntry.resourceDescriptorKind()),
                "java-wrapper-handle",
                cudaHandleBindingStatus(buildEntry.resourceDescriptorKind()),
                buildEntry.handlePresent(),
                buildEntry.metadataWidth(),
                buildEntry.metadataHeight(),
                buildEntry.metadataDepth(),
                buildEntry.metadataLayers(),
                buildEntry.metadataMipLevels(),
                buildEntry.metadataSampleCount(),
                buildEntry.backingBufferHandle() != 0L
        );
    }

    private static TexturePayload texturePayload(CudaImageSamplerDescriptorBuildPlan.Entry buildEntry) {
        return new TexturePayload(
                buildEntry.textureLayoutStruct(),
                buildEntry.textureLayoutFieldCount(),
                "clamp-to-edge",
                "clamp-to-edge",
                "clamp-to-edge",
                "point",
                "element-type",
                false,
                buildEntry.sampler()
                        ? "folded-sampler-parameter-default"
                        : "nearest-clamp-to-edge-default-until-sampler-metadata-exists",
                "not-native-bound"
        );
    }

    private static String resourceFieldPolicy(String resourceDescriptorKind) {
        return switch (resourceDescriptorKind) {
            case "CUDA_RESOURCE_TYPE_ARRAY" -> "resType+res.array.hArray";
            case "CUDA_RESOURCE_TYPE_MIPMAPPED_ARRAY" -> "resType+res.mipmap.hMipmappedArray";
            case "CUDA_RESOURCE_TYPE_LINEAR" -> "resType+res.linear.devPtr+format+numChannels+sizeInBytes";
            case "CUDA_RESOURCE_TYPE_ARRAY_STAGING_PENDING" -> "resType+staged-array-resource-handle-pending";
            default -> "not-required";
        };
    }

    private static String cudaHandleBindingStatus(String resourceDescriptorKind) {
        return "CUDA_RESOURCE_TYPE_ARRAY_STAGING_PENDING".equals(resourceDescriptorKind)
                ? "staged-array-resource-handle-pending"
                : "not-native-bound";
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
