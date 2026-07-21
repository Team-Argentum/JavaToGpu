package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Java-only preflight for future CUDA native descriptor field encoding.
 *
 * <p>The plan records which logical {@code CUDA_RESOURCE_DESC} and {@code CUDA_TEXTURE_DESC} fields a native encoder
 * would write. It does not allocate descriptor memory, does not encode CUDA SDK struct bytes, does not bind native CUDA
 * handles, and does not create texture or surface objects.</p>
 */
public record CudaImageSamplerNativeDescriptorEncodingPlan(
        List<Entry> entries,
        String descriptorPayloadModelStatus,
        boolean descriptorPayloadModelReady,
        boolean javaNativeDescriptorEncodingPlanEnabled,
        boolean nativeDescriptorMemoryAllocationEnabled,
        boolean sdkStructByteEncodingEnabled,
        boolean objectCreationEnabled,
        boolean runtimeBindingEnabled,
        int activeNativeDescriptorCount
) {

    public record FieldWrite(
            String targetStruct,
            String fieldPath,
            String valueSource,
            String valueStatus,
            boolean nativeWriteEnabled,
            boolean sdkStructByteEncodingEnabled
    ) {
        public FieldWrite {
            targetStruct = normalize(targetStruct, "unknown");
            fieldPath = normalize(fieldPath, "unknown");
            valueSource = normalize(valueSource, "unknown");
            valueStatus = normalize(valueStatus, "planned");
        }

        public boolean ready() {
            return !"unknown".equals(targetStruct)
                    && !"unknown".equals(fieldPath)
                    && !nativeWriteEnabled
                    && !sdkStructByteEncodingEnabled;
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.fieldWrite"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".targetStruct", targetStruct);
            fields.put(normalizedPrefix + ".fieldPath", fieldPath);
            fields.put(normalizedPrefix + ".valueSource", valueSource);
            fields.put(normalizedPrefix + ".valueStatus", valueStatus);
            fields.put(normalizedPrefix + ".nativeWrite.enabled", Boolean.toString(nativeWriteEnabled));
            fields.put(normalizedPrefix + ".sdkStructByteEncoding.enabled", Boolean.toString(sdkStructByteEncodingEnabled));
            return Collections.unmodifiableMap(fields);
        }
    }

    public record Entry(
            int parameterIndex,
            String parameterName,
            String javaType,
            String abiKey,
            String cudaAbiRole,
            boolean resourceEncodingRequired,
            String resourceStruct,
            List<FieldWrite> resourceFieldWrites,
            boolean textureEncodingRequired,
            String textureStruct,
            List<FieldWrite> textureFieldWrites,
            String nativeDescriptorEncodingStatus,
            String nativeDescriptorMemoryAllocationStatus,
            String sdkStructByteEncodingStatus,
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
            resourceStruct = normalize(resourceStruct, resourceEncodingRequired ? "CUDA_RESOURCE_DESC" : "not-required");
            resourceFieldWrites = resourceFieldWrites == null ? List.of() : List.copyOf(resourceFieldWrites);
            textureStruct = normalize(textureStruct, textureEncodingRequired ? "CUDA_TEXTURE_DESC" : "not-required");
            textureFieldWrites = textureFieldWrites == null ? List.of() : List.copyOf(textureFieldWrites);
            nativeDescriptorEncodingStatus = normalize(nativeDescriptorEncodingStatus, "blocked");
            nativeDescriptorMemoryAllocationStatus = normalize(nativeDescriptorMemoryAllocationStatus, "disabled");
            sdkStructByteEncodingStatus = normalize(sdkStructByteEncodingStatus, "disabled");
            objectCreationStatus = normalize(objectCreationStatus, "disabled");
            runtimeBindingStatus = normalize(runtimeBindingStatus, "fail-closed");
            firstBlocker = normalize(firstBlocker, "none");
        }

        public boolean sampler() {
            return "texture-descriptor-state".equals(cudaAbiRole);
        }

        public int fieldWriteCount() {
            return resourceFieldWrites.size() + textureFieldWrites.size();
        }

        public long nativeWriteEnabledCount() {
            return allFieldWrites().stream().filter(FieldWrite::nativeWriteEnabled).count();
        }

        public long sdkStructByteEncodingEnabledCount() {
            return allFieldWrites().stream().filter(FieldWrite::sdkStructByteEncodingEnabled).count();
        }

        public boolean ready() {
            return "native-encoding-plan-built".equals(nativeDescriptorEncodingStatus)
                    && "disabled".equals(nativeDescriptorMemoryAllocationStatus)
                    && "disabled".equals(sdkStructByteEncodingStatus)
                    && "disabled".equals(objectCreationStatus)
                    && "fail-closed".equals(runtimeBindingStatus)
                    && !productionSupportEnabled
                    && "none".equals(firstBlocker)
                    && (!resourceEncodingRequired || ("CUDA_RESOURCE_DESC".equals(resourceStruct) && !resourceFieldWrites.isEmpty()))
                    && (!textureEncodingRequired || ("CUDA_TEXTURE_DESC".equals(textureStruct) && !textureFieldWrites.isEmpty()))
                    && allFieldWrites().stream().allMatch(FieldWrite::ready);
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".parameter.index", Integer.toString(parameterIndex));
            fields.put(normalizedPrefix + ".parameter.name", parameterName);
            fields.put(normalizedPrefix + ".parameter.javaType", javaType);
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".abi.key", abiKey);
            fields.put(normalizedPrefix + ".abi.role", cudaAbiRole);
            fields.put(normalizedPrefix + ".resourceEncoding.required", Boolean.toString(resourceEncodingRequired));
            fields.put(normalizedPrefix + ".resourceEncoding.struct", resourceStruct);
            fields.put(normalizedPrefix + ".resourceFieldWrite.count", Integer.toString(resourceFieldWrites.size()));
            for (int index = 0; index < resourceFieldWrites.size(); index++) {
                fields.putAll(resourceFieldWrites.get(index).artifactFields(normalizedPrefix + ".resourceFieldWrite." + index));
            }
            fields.put(normalizedPrefix + ".textureEncoding.required", Boolean.toString(textureEncodingRequired));
            fields.put(normalizedPrefix + ".textureEncoding.struct", textureStruct);
            fields.put(normalizedPrefix + ".textureFieldWrite.count", Integer.toString(textureFieldWrites.size()));
            for (int index = 0; index < textureFieldWrites.size(); index++) {
                fields.putAll(textureFieldWrites.get(index).artifactFields(normalizedPrefix + ".textureFieldWrite." + index));
            }
            fields.put(normalizedPrefix + ".fieldWrite.count", Integer.toString(fieldWriteCount()));
            fields.put(normalizedPrefix + ".nativeWrite.enabled.count", Long.toString(nativeWriteEnabledCount()));
            fields.put(normalizedPrefix + ".sdkStructByteEncoding.enabled.count", Long.toString(sdkStructByteEncodingEnabledCount()));
            fields.put(normalizedPrefix + ".nativeDescriptorEncoding.status", nativeDescriptorEncodingStatus);
            fields.put(normalizedPrefix + ".nativeDescriptorMemoryAllocation.status", nativeDescriptorMemoryAllocationStatus);
            fields.put(normalizedPrefix + ".sdkStructByteEncoding.status", sdkStructByteEncodingStatus);
            fields.put(normalizedPrefix + ".objectCreation.status", objectCreationStatus);
            fields.put(normalizedPrefix + ".runtimeBinding.status", runtimeBindingStatus);
            fields.put(normalizedPrefix + ".productionSupport.enabled", Boolean.toString(productionSupportEnabled));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
            return Collections.unmodifiableMap(fields);
        }

        private List<FieldWrite> allFieldWrites() {
            ArrayList<FieldWrite> writes = new ArrayList<>(resourceFieldWrites.size() + textureFieldWrites.size());
            writes.addAll(resourceFieldWrites);
            writes.addAll(textureFieldWrites);
            return writes;
        }
    }

    public CudaImageSamplerNativeDescriptorEncodingPlan {
        entries = entries == null ? List.of() : List.copyOf(entries);
        descriptorPayloadModelStatus = normalize(descriptorPayloadModelStatus, "not-present");
        activeNativeDescriptorCount = Math.max(0, activeNativeDescriptorCount);
    }

    static CudaImageSamplerNativeDescriptorEncodingPlan empty() {
        return new CudaImageSamplerNativeDescriptorEncodingPlan(
                List.of(),
                "not-present",
                false,
                false,
                false,
                false,
                false,
                false,
                0
        );
    }

    static CudaImageSamplerNativeDescriptorEncodingPlan from(CudaImageSamplerDescriptorPayloadModel payloadModel) {
        if (payloadModel == null || !payloadModel.present()) {
            return empty();
        }
        return new CudaImageSamplerNativeDescriptorEncodingPlan(
                payloadModel.entries().stream()
                        .map(CudaImageSamplerNativeDescriptorEncodingPlan::entry)
                        .collect(Collectors.toUnmodifiableList()),
                payloadModel.status(),
                payloadModel.ready(),
                true,
                false,
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
                && "ready".equals(descriptorPayloadModelStatus)
                && descriptorPayloadModelReady
                && javaNativeDescriptorEncodingPlanEnabled
                && !nativeDescriptorMemoryAllocationEnabled
                && !sdkStructByteEncodingEnabled
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

    public long resourceFieldWriteCount() {
        return entries.stream().mapToLong(entry -> entry.resourceFieldWrites().size()).sum();
    }

    public long textureFieldWriteCount() {
        return entries.stream().mapToLong(entry -> entry.textureFieldWrites().size()).sum();
    }

    public long fieldWriteCount() {
        return resourceFieldWriteCount() + textureFieldWriteCount();
    }

    public long samplerTextureFieldWriteCount() {
        return entries.stream()
                .filter(Entry::sampler)
                .mapToLong(entry -> entry.textureFieldWrites().size())
                .sum();
    }

    public long nativeWriteEnabledCount() {
        return entries.stream().mapToLong(Entry::nativeWriteEnabledCount).sum();
    }

    public long sdkStructByteEncodingEnabledCount() {
        return entries.stream().mapToLong(Entry::sdkStructByteEncodingEnabledCount).sum();
    }

    public String firstBlocker() {
        if (!present()) {
            return "none";
        }
        return entries.stream()
                .map(Entry::firstBlocker)
                .filter(blocker -> !"none".equals(blocker))
                .findFirst()
                .orElseGet(() -> ready() ? "none" : "cuda-image-sampler-native-descriptor-encoding-plan-not-ready");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerNativeDescriptorEncodingPlan"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerNativeDescriptorEncodingPlan");
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", Boolean.toString(present()));
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".ready", Boolean.toString(ready()));
        fields.put(prefix + ".descriptorPayloadModel.status", descriptorPayloadModelStatus);
        fields.put(prefix + ".descriptorPayloadModel.ready", Boolean.toString(descriptorPayloadModelReady));
        fields.put(prefix + ".javaNativeDescriptorEncodingPlan.enabled", Boolean.toString(javaNativeDescriptorEncodingPlanEnabled));
        fields.put(prefix + ".nativeDescriptorMemoryAllocation.enabled", Boolean.toString(nativeDescriptorMemoryAllocationEnabled));
        fields.put(prefix + ".sdkStructByteEncoding.enabled", Boolean.toString(sdkStructByteEncodingEnabled));
        fields.put(prefix + ".objectCreation.enabled", Boolean.toString(objectCreationEnabled));
        fields.put(prefix + ".runtimeBinding.enabled", Boolean.toString(runtimeBindingEnabled));
        fields.put(prefix + ".activeNativeDescriptor.count", Integer.toString(activeNativeDescriptorCount));
        fields.put(prefix + ".entry.count", Integer.toString(entries.size()));
        fields.put(prefix + ".entry.ready.count", Long.toString(entryReadyCount()));
        fields.put(prefix + ".entry.blocked.count", Long.toString(entryBlockedCount()));
        fields.put(prefix + ".resourceFieldWrite.count", Long.toString(resourceFieldWriteCount()));
        fields.put(prefix + ".textureFieldWrite.count", Long.toString(textureFieldWriteCount()));
        fields.put(prefix + ".fieldWrite.count", Long.toString(fieldWriteCount()));
        fields.put(prefix + ".samplerTextureFieldWrite.count", Long.toString(samplerTextureFieldWriteCount()));
        fields.put(prefix + ".nativeWrite.enabled.count", Long.toString(nativeWriteEnabledCount()));
        fields.put(prefix + ".sdkStructByteEncoding.enabled.count", Long.toString(sdkStructByteEncodingEnabledCount()));
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < entries.size(); index++) {
            fields.putAll(entries.get(index).artifactFields(prefix + ".entry." + index));
        }
    }

    private static Entry entry(CudaImageSamplerDescriptorPayloadModel.Entry payloadEntry) {
        if (payloadEntry == null) {
            return blockedEntry("cuda-image-sampler-descriptor-payload-model-entry-missing");
        }
        boolean ready = payloadEntry.ready();
        return new Entry(
                payloadEntry.parameterIndex(),
                payloadEntry.parameterName(),
                payloadEntry.javaType(),
                payloadEntry.abiKey(),
                payloadEntry.cudaAbiRole(),
                payloadEntry.resourcePayloadRequired(),
                payloadEntry.resourcePayloadRequired() ? "CUDA_RESOURCE_DESC" : "not-required",
                ready && payloadEntry.resourcePayload() != null
                        ? resourceFieldWrites(payloadEntry.resourcePayload())
                        : List.of(),
                payloadEntry.texturePayloadRequired(),
                payloadEntry.texturePayloadRequired() ? "CUDA_TEXTURE_DESC" : "not-required",
                ready && payloadEntry.texturePayload() != null
                        ? textureFieldWrites(payloadEntry.texturePayload())
                        : List.of(),
                ready ? "native-encoding-plan-built" : "blocked",
                "disabled",
                "disabled",
                "disabled",
                payloadEntry.runtimeBindingStatus(),
                payloadEntry.productionSupportEnabled(),
                ready ? "none" : payloadEntry.firstBlocker()
        );
    }

    private static Entry blockedEntry(String firstBlocker) {
        return new Entry(
                0,
                "unknown",
                "unknown",
                "unknown",
                "unknown",
                false,
                "not-required",
                List.of(),
                false,
                "not-required",
                List.of(),
                "blocked",
                "disabled",
                "disabled",
                "disabled",
                "fail-closed",
                false,
                firstBlocker
        );
    }

    private static List<FieldWrite> resourceFieldWrites(CudaImageSamplerDescriptorPayloadModel.ResourcePayload payload) {
        ArrayList<FieldWrite> writes = new ArrayList<>();
        writes.add(field("CUDA_RESOURCE_DESC", "resType", "resourceDescriptor.kind", "literal-planned"));
        switch (payload.resourceDescriptorKind()) {
            case "CUDA_RESOURCE_TYPE_ARRAY" -> writes.add(field(
                    "CUDA_RESOURCE_DESC",
                    "res.array.hArray",
                    payload.handleSource(),
                    payload.cudaHandleBindingStatus()
            ));
            case "CUDA_RESOURCE_TYPE_MIPMAPPED_ARRAY" -> writes.add(field(
                    "CUDA_RESOURCE_DESC",
                    "res.mipmap.hMipmappedArray",
                    payload.handleSource(),
                    payload.cudaHandleBindingStatus()
            ));
            case "CUDA_RESOURCE_TYPE_LINEAR" -> {
                writes.add(field("CUDA_RESOURCE_DESC", "res.linear.devPtr", "backingBufferHandle", cudaLinearHandleStatus(payload)));
                writes.add(field("CUDA_RESOURCE_DESC", "res.linear.format", "default-channel-format", "format-default-planned"));
                writes.add(field("CUDA_RESOURCE_DESC", "res.linear.numChannels", "default-channel-count", "channel-count-default-planned"));
                writes.add(field("CUDA_RESOURCE_DESC", "res.linear.sizeInBytes", "metadata.width", "size-derived-from-width"));
            }
            case "CUDA_RESOURCE_TYPE_ARRAY_STAGING_PENDING" -> writes.add(field(
                    "CUDA_RESOURCE_DESC",
                    "res.array.hArray",
                    "staged-array-from-backing-buffer",
                    payload.cudaHandleBindingStatus()
            ));
            default -> writes.add(field("CUDA_RESOURCE_DESC", "resource.kind.unknown", "resourceDescriptor.kind", "blocked"));
        }
        return List.copyOf(writes);
    }

    private static String cudaLinearHandleStatus(CudaImageSamplerDescriptorPayloadModel.ResourcePayload payload) {
        if (payload.backingBufferHandlePresent()) {
            return "backing-buffer-handle-present-not-native-bound";
        }
        return "backing-buffer-handle-missing";
    }

    private static List<FieldWrite> textureFieldWrites(CudaImageSamplerDescriptorPayloadModel.TexturePayload payload) {
        return List.of(
                field("CUDA_TEXTURE_DESC", "addressMode[0]", "texture.addressMode.x", payload.addressModeX()),
                field("CUDA_TEXTURE_DESC", "addressMode[1]", "texture.addressMode.y", payload.addressModeY()),
                field("CUDA_TEXTURE_DESC", "addressMode[2]", "texture.addressMode.z", payload.addressModeZ()),
                field("CUDA_TEXTURE_DESC", "filterMode", "texture.filterMode", payload.filterMode()),
                field("CUDA_TEXTURE_DESC", "flags", "texture.normalizedCoordinates", payload.normalizedCoordinates() ? "normalized-coordinates" : "unnormalized-coordinates"),
                field("CUDA_TEXTURE_DESC", "readMode", "texture.readMode", payload.readMode())
        );
    }

    private static FieldWrite field(String targetStruct, String fieldPath, String valueSource, String valueStatus) {
        return new FieldWrite(targetStruct, fieldPath, valueSource, valueStatus, false, false);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
