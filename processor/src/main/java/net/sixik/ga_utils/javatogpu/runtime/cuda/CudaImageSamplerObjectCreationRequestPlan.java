package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Java-only request plan for future CUDA texture/surface object creation.
 *
 * <p>The plan records which future {@code cuTexObjectCreate} / {@code cuSurfObjectCreate} requests would be needed
 * after descriptor encoding. It deliberately does not call Driver API object-creation functions and does not create or
 * own any CUDA texture/surface objects.</p>
 */
public record CudaImageSamplerObjectCreationRequestPlan(
        List<Entry> entries,
        String nativeDescriptorEncodingPlanStatus,
        boolean nativeDescriptorEncodingPlanReady,
        boolean javaObjectCreationRequestPlanEnabled,
        boolean objectCreationCallEnabled,
        boolean objectOwnershipEnabled,
        boolean runtimeBindingEnabled,
        int activeObjectCount
) {

    public record Entry(
            int parameterIndex,
            String parameterName,
            String javaType,
            String abiKey,
            String cudaAbiRole,
            boolean objectCreationRequestRequired,
            String objectKind,
            String parameterCarrier,
            boolean resourceDescriptorRequired,
            boolean resourceDescriptorReady,
            boolean textureDescriptorRequired,
            boolean textureDescriptorReady,
            String createFunctionSymbol,
            String destroyFunctionSymbol,
            String objectCreationRequestStatus,
            String objectCreationCallStatus,
            String objectOwnershipStatus,
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
            objectKind = normalize(objectKind, objectCreationRequestRequired ? "unknown" : "not-required");
            parameterCarrier = normalize(parameterCarrier, objectCreationRequestRequired ? "unknown" : "not-required");
            createFunctionSymbol = normalize(createFunctionSymbol, objectCreationRequestRequired ? "unknown" : "not-required");
            destroyFunctionSymbol = normalize(destroyFunctionSymbol, objectCreationRequestRequired ? "unknown" : "not-required");
            objectCreationRequestStatus = normalize(objectCreationRequestStatus, "blocked");
            objectCreationCallStatus = normalize(objectCreationCallStatus, "disabled");
            objectOwnershipStatus = normalize(objectOwnershipStatus, "prepared");
            runtimeBindingStatus = normalize(runtimeBindingStatus, "fail-closed");
            firstBlocker = normalize(firstBlocker, "none");
        }

        public boolean textureRequest() {
            return objectCreationRequestRequired && "texture".equals(objectKind);
        }

        public boolean surfaceRequest() {
            return objectCreationRequestRequired && "surface".equals(objectKind);
        }

        public boolean samplerFolded() {
            return ready() && "texture-descriptor-state".equals(cudaAbiRole);
        }

        public boolean ready() {
            return "object-creation-request-planned".equals(objectCreationRequestStatus)
                    && "disabled".equals(objectCreationCallStatus)
                    && "prepared".equals(objectOwnershipStatus)
                    && "fail-closed".equals(runtimeBindingStatus)
                    && !productionSupportEnabled
                    && "none".equals(firstBlocker)
                    && (!objectCreationRequestRequired || (!"unknown".equals(objectKind)
                    && !"unknown".equals(parameterCarrier)
                    && !"unknown".equals(createFunctionSymbol)
                    && !"unknown".equals(destroyFunctionSymbol)))
                    && (!resourceDescriptorRequired || resourceDescriptorReady)
                    && (!textureDescriptorRequired || textureDescriptorReady);
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerObjectCreationRequestPlan.entry"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".parameter.index", Integer.toString(parameterIndex));
            fields.put(normalizedPrefix + ".parameter.name", parameterName);
            fields.put(normalizedPrefix + ".parameter.javaType", javaType);
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".abi.key", abiKey);
            fields.put(normalizedPrefix + ".abi.role", cudaAbiRole);
            fields.put(normalizedPrefix + ".request.required", Boolean.toString(objectCreationRequestRequired));
            fields.put(normalizedPrefix + ".object.kind", objectKind);
            fields.put(normalizedPrefix + ".parameter.carrier", parameterCarrier);
            fields.put(normalizedPrefix + ".resourceDescriptor.required", Boolean.toString(resourceDescriptorRequired));
            fields.put(normalizedPrefix + ".resourceDescriptor.ready", Boolean.toString(resourceDescriptorReady));
            fields.put(normalizedPrefix + ".textureDescriptor.required", Boolean.toString(textureDescriptorRequired));
            fields.put(normalizedPrefix + ".textureDescriptor.ready", Boolean.toString(textureDescriptorReady));
            fields.put(normalizedPrefix + ".createFunction.symbol", createFunctionSymbol);
            fields.put(normalizedPrefix + ".destroyFunction.symbol", destroyFunctionSymbol);
            fields.put(normalizedPrefix + ".objectCreationRequest.status", objectCreationRequestStatus);
            fields.put(normalizedPrefix + ".objectCreationCall.status", objectCreationCallStatus);
            fields.put(normalizedPrefix + ".objectOwnership.status", objectOwnershipStatus);
            fields.put(normalizedPrefix + ".runtimeBinding.status", runtimeBindingStatus);
            fields.put(normalizedPrefix + ".productionSupport.enabled", Boolean.toString(productionSupportEnabled));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerObjectCreationRequestPlan {
        entries = entries == null ? List.of() : List.copyOf(entries);
        nativeDescriptorEncodingPlanStatus = normalize(nativeDescriptorEncodingPlanStatus, "not-present");
        activeObjectCount = Math.max(0, activeObjectCount);
    }

    static CudaImageSamplerObjectCreationRequestPlan empty() {
        return new CudaImageSamplerObjectCreationRequestPlan(
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

    static CudaImageSamplerObjectCreationRequestPlan from(CudaImageSamplerNativeDescriptorEncodingPlan encodingPlan) {
        if (encodingPlan == null || !encodingPlan.present()) {
            return empty();
        }
        return new CudaImageSamplerObjectCreationRequestPlan(
                encodingPlan.entries().stream()
                        .map(CudaImageSamplerObjectCreationRequestPlan::entry)
                        .collect(Collectors.toUnmodifiableList()),
                encodingPlan.status(),
                encodingPlan.ready(),
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
                && "ready".equals(nativeDescriptorEncodingPlanStatus)
                && nativeDescriptorEncodingPlanReady
                && javaObjectCreationRequestPlanEnabled
                && !objectCreationCallEnabled
                && !objectOwnershipEnabled
                && !runtimeBindingEnabled
                && activeObjectCount == 0;
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

    public long objectCreationRequestCount() {
        return entries.stream().filter(Entry::objectCreationRequestRequired).count();
    }

    public long textureObjectRequestCount() {
        return entries.stream().filter(Entry::textureRequest).count();
    }

    public long surfaceObjectRequestCount() {
        return entries.stream().filter(Entry::surfaceRequest).count();
    }

    public long foldedSamplerCount() {
        return entries.stream().filter(Entry::samplerFolded).count();
    }

    public long objectCreationCallEnabledCount() {
        return entries.stream().filter(entry -> !"disabled".equals(entry.objectCreationCallStatus())).count();
    }

    public String firstBlocker() {
        if (!present()) {
            return "none";
        }
        return entries.stream()
                .map(Entry::firstBlocker)
                .filter(blocker -> !"none".equals(blocker))
                .findFirst()
                .orElseGet(() -> ready() ? "none" : "cuda-image-sampler-object-creation-request-plan-not-ready");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerObjectCreationRequestPlan"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerObjectCreationRequestPlan");
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", Boolean.toString(present()));
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".ready", Boolean.toString(ready()));
        fields.put(prefix + ".nativeDescriptorEncodingPlan.status", nativeDescriptorEncodingPlanStatus);
        fields.put(prefix + ".nativeDescriptorEncodingPlan.ready", Boolean.toString(nativeDescriptorEncodingPlanReady));
        fields.put(prefix + ".javaObjectCreationRequestPlan.enabled", Boolean.toString(javaObjectCreationRequestPlanEnabled));
        fields.put(prefix + ".objectCreationCall.enabled", Boolean.toString(objectCreationCallEnabled));
        fields.put(prefix + ".objectOwnership.enabled", Boolean.toString(objectOwnershipEnabled));
        fields.put(prefix + ".runtimeBinding.enabled", Boolean.toString(runtimeBindingEnabled));
        fields.put(prefix + ".activeObject.count", Integer.toString(activeObjectCount));
        fields.put(prefix + ".entry.count", Integer.toString(entries.size()));
        fields.put(prefix + ".entry.ready.count", Long.toString(entryReadyCount()));
        fields.put(prefix + ".entry.blocked.count", Long.toString(entryBlockedCount()));
        fields.put(prefix + ".request.count", Long.toString(objectCreationRequestCount()));
        fields.put(prefix + ".textureObjectRequest.count", Long.toString(textureObjectRequestCount()));
        fields.put(prefix + ".surfaceObjectRequest.count", Long.toString(surfaceObjectRequestCount()));
        fields.put(prefix + ".foldedSampler.count", Long.toString(foldedSamplerCount()));
        fields.put(prefix + ".objectCreationCall.enabled.count", Long.toString(objectCreationCallEnabledCount()));
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < entries.size(); index++) {
            fields.putAll(entries.get(index).artifactFields(prefix + ".entry." + index));
        }
    }

    private static Entry entry(CudaImageSamplerNativeDescriptorEncodingPlan.Entry encodingEntry) {
        if (encodingEntry == null) {
            return blockedEntry("cuda-image-sampler-native-descriptor-encoding-plan-entry-missing");
        }
        boolean ready = encodingEntry.ready();
        boolean texture = ready && "read-texture-object".equals(encodingEntry.cudaAbiRole());
        boolean surface = ready && "write-surface-object".equals(encodingEntry.cudaAbiRole());
        boolean requestRequired = texture || surface;
        return new Entry(
                encodingEntry.parameterIndex(),
                encodingEntry.parameterName(),
                encodingEntry.javaType(),
                encodingEntry.abiKey(),
                encodingEntry.cudaAbiRole(),
                requestRequired,
                objectKind(texture, surface),
                parameterCarrier(texture, surface),
                requestRequired && encodingEntry.resourceEncodingRequired(),
                ready && encodingEntry.resourceEncodingRequired() && !encodingEntry.resourceFieldWrites().isEmpty(),
                texture && encodingEntry.textureEncodingRequired(),
                ready && texture && !encodingEntry.textureFieldWrites().isEmpty(),
                createSymbol(texture, surface),
                destroySymbol(texture, surface),
                ready ? "object-creation-request-planned" : "blocked",
                "disabled",
                "prepared",
                encodingEntry.runtimeBindingStatus(),
                encodingEntry.productionSupportEnabled(),
                ready ? "none" : encodingEntry.firstBlocker()
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
                "not-required",
                false,
                false,
                false,
                false,
                "not-required",
                "not-required",
                "blocked",
                "disabled",
                "prepared",
                "fail-closed",
                false,
                firstBlocker
        );
    }

    private static String objectKind(boolean texture, boolean surface) {
        if (texture) {
            return "texture";
        }
        if (surface) {
            return "surface";
        }
        return "not-required";
    }

    private static String parameterCarrier(boolean texture, boolean surface) {
        if (texture) {
            return "CUtexObject";
        }
        if (surface) {
            return "CUsurfObject";
        }
        return "not-required";
    }

    private static String createSymbol(boolean texture, boolean surface) {
        if (texture) {
            return "cuTexObjectCreate";
        }
        if (surface) {
            return "cuSurfObjectCreate";
        }
        return "not-required";
    }

    private static String destroySymbol(boolean texture, boolean surface) {
        if (texture) {
            return "cuTexObjectDestroy";
        }
        if (surface) {
            return "cuSurfObjectDestroy";
        }
        return "not-required";
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
