package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java-only plan for future CUDA texture/surface object kernel-argument binding.
 *
 * <p>The plan connects planned {@code CUtexObject}/{@code CUsurfObject} creation requests to the kernel parameter
 * slots they would eventually occupy. It deliberately keeps runtime binding disabled, does not call object creation,
 * and does not expose active CUDA image/sampler objects.</p>
 */
public record CudaImageSamplerRuntimeObjectBindingPlan(
        List<Entry> entries,
        String objectCreationRequestPlanStatus,
        boolean objectCreationRequestPlanReady,
        boolean javaRuntimeObjectBindingPlanEnabled,
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
            boolean objectBindingRequired,
            String objectKind,
            String parameterCarrier,
            boolean objectCreationRequestRequired,
            boolean objectCreationRequestReady,
            int plannedObjectKernelParameterSlotCount,
            int plannedMetadataKernelParameterSlotCount,
            int plannedKernelParameterSlotCount,
            int runtimeBindingKernelParameterSlotCount,
            String objectHandleSource,
            String kernelArgumentBindingStatus,
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
            objectKind = normalize(objectKind, objectBindingRequired ? "unknown" : "not-required");
            parameterCarrier = normalize(parameterCarrier, objectBindingRequired ? "unknown" : "not-required");
            plannedObjectKernelParameterSlotCount = Math.max(0, plannedObjectKernelParameterSlotCount);
            plannedMetadataKernelParameterSlotCount = Math.max(0, plannedMetadataKernelParameterSlotCount);
            plannedKernelParameterSlotCount = Math.max(0, plannedKernelParameterSlotCount);
            runtimeBindingKernelParameterSlotCount = Math.max(0, runtimeBindingKernelParameterSlotCount);
            objectHandleSource = normalize(objectHandleSource, objectBindingRequired ? "unknown" : "not-required");
            kernelArgumentBindingStatus = normalize(kernelArgumentBindingStatus, "blocked");
            objectCreationCallStatus = normalize(objectCreationCallStatus, "disabled");
            objectOwnershipStatus = normalize(objectOwnershipStatus, "prepared");
            runtimeBindingStatus = normalize(runtimeBindingStatus, "fail-closed");
            firstBlocker = normalize(firstBlocker, "none");
        }

        public boolean textureObjectBinding() {
            return objectBindingRequired && "texture".equals(objectKind);
        }

        public boolean surfaceObjectBinding() {
            return objectBindingRequired && "surface".equals(objectKind);
        }

        public boolean foldedSamplerBinding() {
            return ready() && "texture-descriptor-state".equals(cudaAbiRole);
        }

        public boolean ready() {
            return "runtime-object-binding-planned".equals(kernelArgumentBindingStatus)
                    && "disabled".equals(objectCreationCallStatus)
                    && "prepared".equals(objectOwnershipStatus)
                    && "fail-closed".equals(runtimeBindingStatus)
                    && !productionSupportEnabled
                    && "none".equals(firstBlocker)
                    && (!objectBindingRequired || (objectCreationRequestRequired
                    && objectCreationRequestReady
                    && plannedObjectKernelParameterSlotCount == 1
                    && plannedKernelParameterSlotCount == plannedObjectKernelParameterSlotCount
                    + plannedMetadataKernelParameterSlotCount
                    && runtimeBindingKernelParameterSlotCount == 0
                    && !"unknown".equals(objectKind)
                    && !"unknown".equals(parameterCarrier)
                    && !"unknown".equals(objectHandleSource)));
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerRuntimeObjectBindingPlan.entry"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".parameter.index", Integer.toString(parameterIndex));
            fields.put(normalizedPrefix + ".parameter.name", parameterName);
            fields.put(normalizedPrefix + ".parameter.javaType", javaType);
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".abi.key", abiKey);
            fields.put(normalizedPrefix + ".abi.role", cudaAbiRole);
            fields.put(normalizedPrefix + ".objectBinding.required", Boolean.toString(objectBindingRequired));
            fields.put(normalizedPrefix + ".object.kind", objectKind);
            fields.put(normalizedPrefix + ".parameter.carrier", parameterCarrier);
            fields.put(normalizedPrefix + ".objectCreationRequest.required", Boolean.toString(objectCreationRequestRequired));
            fields.put(normalizedPrefix + ".objectCreationRequest.ready", Boolean.toString(objectCreationRequestReady));
            fields.put(normalizedPrefix + ".plannedObjectKernelParameterSlot.count", Integer.toString(plannedObjectKernelParameterSlotCount));
            fields.put(normalizedPrefix + ".plannedMetadataKernelParameterSlot.count", Integer.toString(plannedMetadataKernelParameterSlotCount));
            fields.put(normalizedPrefix + ".plannedKernelParameterSlot.count", Integer.toString(plannedKernelParameterSlotCount));
            fields.put(normalizedPrefix + ".runtimeBinding.kernelParameterSlot.count", Integer.toString(runtimeBindingKernelParameterSlotCount));
            fields.put(normalizedPrefix + ".objectHandle.source", objectHandleSource);
            fields.put(normalizedPrefix + ".kernelArgumentBinding.status", kernelArgumentBindingStatus);
            fields.put(normalizedPrefix + ".objectCreationCall.status", objectCreationCallStatus);
            fields.put(normalizedPrefix + ".objectOwnership.status", objectOwnershipStatus);
            fields.put(normalizedPrefix + ".runtimeBinding.status", runtimeBindingStatus);
            fields.put(normalizedPrefix + ".productionSupport.enabled", Boolean.toString(productionSupportEnabled));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerRuntimeObjectBindingPlan {
        entries = entries == null ? List.of() : List.copyOf(entries);
        objectCreationRequestPlanStatus = normalize(objectCreationRequestPlanStatus, "not-present");
        activeObjectCount = Math.max(0, activeObjectCount);
    }

    static CudaImageSamplerRuntimeObjectBindingPlan empty() {
        return new CudaImageSamplerRuntimeObjectBindingPlan(
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

    static CudaImageSamplerRuntimeObjectBindingPlan from(CudaImageSamplerObjectCreationRequestPlan requestPlan) {
        if (requestPlan == null || !requestPlan.present()) {
            return empty();
        }
        return new CudaImageSamplerRuntimeObjectBindingPlan(
                requestPlan.entries().stream()
                        .map(CudaImageSamplerRuntimeObjectBindingPlan::entry)
                        .toList(),
                requestPlan.status(),
                requestPlan.ready(),
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
                && "ready".equals(objectCreationRequestPlanStatus)
                && objectCreationRequestPlanReady
                && javaRuntimeObjectBindingPlanEnabled
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

    public long objectBindingCount() {
        return entries.stream().filter(Entry::objectBindingRequired).count();
    }

    public long textureObjectBindingCount() {
        return entries.stream().filter(Entry::textureObjectBinding).count();
    }

    public long surfaceObjectBindingCount() {
        return entries.stream().filter(Entry::surfaceObjectBinding).count();
    }

    public long foldedSamplerBindingCount() {
        return entries.stream().filter(Entry::foldedSamplerBinding).count();
    }

    public long plannedObjectKernelParameterSlotCount() {
        return entries.stream().mapToLong(Entry::plannedObjectKernelParameterSlotCount).sum();
    }

    public long plannedMetadataKernelParameterSlotCount() {
        return entries.stream().mapToLong(Entry::plannedMetadataKernelParameterSlotCount).sum();
    }

    public long plannedKernelParameterSlotCount() {
        return entries.stream().mapToLong(Entry::plannedKernelParameterSlotCount).sum();
    }

    public long runtimeBindingKernelParameterSlotCount() {
        return entries.stream().mapToLong(Entry::runtimeBindingKernelParameterSlotCount).sum();
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
                .orElseGet(() -> ready() ? "none" : "cuda-image-sampler-runtime-object-binding-plan-not-ready");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerRuntimeObjectBindingPlan"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerRuntimeObjectBindingPlan");
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", Boolean.toString(present()));
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".ready", Boolean.toString(ready()));
        fields.put(prefix + ".objectCreationRequestPlan.status", objectCreationRequestPlanStatus);
        fields.put(prefix + ".objectCreationRequestPlan.ready", Boolean.toString(objectCreationRequestPlanReady));
        fields.put(prefix + ".javaRuntimeObjectBindingPlan.enabled", Boolean.toString(javaRuntimeObjectBindingPlanEnabled));
        fields.put(prefix + ".objectCreationCall.enabled", Boolean.toString(objectCreationCallEnabled));
        fields.put(prefix + ".objectOwnership.enabled", Boolean.toString(objectOwnershipEnabled));
        fields.put(prefix + ".runtimeBinding.enabled", Boolean.toString(runtimeBindingEnabled));
        fields.put(prefix + ".activeObject.count", Integer.toString(activeObjectCount));
        fields.put(prefix + ".entry.count", Integer.toString(entries.size()));
        fields.put(prefix + ".entry.ready.count", Long.toString(entryReadyCount()));
        fields.put(prefix + ".entry.blocked.count", Long.toString(entryBlockedCount()));
        fields.put(prefix + ".objectBinding.count", Long.toString(objectBindingCount()));
        fields.put(prefix + ".textureObjectBinding.count", Long.toString(textureObjectBindingCount()));
        fields.put(prefix + ".surfaceObjectBinding.count", Long.toString(surfaceObjectBindingCount()));
        fields.put(prefix + ".foldedSamplerBinding.count", Long.toString(foldedSamplerBindingCount()));
        fields.put(prefix + ".plannedObjectKernelParameterSlot.count", Long.toString(plannedObjectKernelParameterSlotCount()));
        fields.put(prefix + ".plannedMetadataKernelParameterSlot.count", Long.toString(plannedMetadataKernelParameterSlotCount()));
        fields.put(prefix + ".plannedKernelParameterSlot.count", Long.toString(plannedKernelParameterSlotCount()));
        fields.put(prefix + ".runtimeBinding.kernelParameterSlot.count", Long.toString(runtimeBindingKernelParameterSlotCount()));
        fields.put(prefix + ".objectCreationCall.enabled.count", Long.toString(objectCreationCallEnabledCount()));
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < entries.size(); index++) {
            fields.putAll(entries.get(index).artifactFields(prefix + ".entry." + index));
        }
    }

    private static Entry entry(CudaImageSamplerObjectCreationRequestPlan.Entry requestEntry) {
        if (requestEntry == null) {
            return blockedEntry("cuda-image-sampler-object-creation-request-plan-entry-missing");
        }
        CudaImageSamplerAbi.Descriptor descriptor = CudaImageSamplerAbi.descriptorFor(requestEntry.javaType())
                .orElse(null);
        boolean ready = requestEntry.ready();
        boolean objectBinding = ready && requestEntry.objectCreationRequestRequired();
        boolean foldedSampler = ready && "texture-descriptor-state".equals(requestEntry.cudaAbiRole());
        int objectSlots = objectBinding ? 1 : 0;
        int metadataSlots = objectBinding && descriptor != null ? descriptor.plannedRuntimeMetadataSlotCount() : 0;
        return new Entry(
                requestEntry.parameterIndex(),
                requestEntry.parameterName(),
                requestEntry.javaType(),
                requestEntry.abiKey(),
                requestEntry.cudaAbiRole(),
                objectBinding,
                requestEntry.objectKind(),
                requestEntry.parameterCarrier(),
                requestEntry.objectCreationRequestRequired(),
                ready && requestEntry.objectCreationRequestRequired(),
                objectSlots,
                metadataSlots,
                objectSlots + metadataSlots,
                0,
                objectHandleSource(objectBinding, foldedSampler, requestEntry.objectKind()),
                ready ? "runtime-object-binding-planned" : "blocked",
                requestEntry.objectCreationCallStatus(),
                requestEntry.objectOwnershipStatus(),
                requestEntry.runtimeBindingStatus(),
                requestEntry.productionSupportEnabled(),
                ready ? "none" : requestEntry.firstBlocker()
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
                0,
                0,
                0,
                0,
                "not-required",
                "blocked",
                "disabled",
                "prepared",
                "fail-closed",
                false,
                firstBlocker
        );
    }

    private static String objectHandleSource(boolean objectBinding, boolean foldedSampler, String objectKind) {
        if (objectBinding && "texture".equals(objectKind)) {
            return "future-cuTexObjectCreate-result";
        }
        if (objectBinding && "surface".equals(objectKind)) {
            return "future-cuSurfObjectCreate-result";
        }
        if (foldedSampler) {
            return "folded-sampler-descriptor-state";
        }
        return "not-required";
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
