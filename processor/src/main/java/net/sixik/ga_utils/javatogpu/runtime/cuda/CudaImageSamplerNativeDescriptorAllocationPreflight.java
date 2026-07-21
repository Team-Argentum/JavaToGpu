package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fail-closed preflight for future CUDA native descriptor allocation and ownership.
 *
 * <p>This layer sits between logical native descriptor field encoding and texture/surface object creation. It records
 * which {@code CUDA_RESOURCE_DESC} / {@code CUDA_TEXTURE_DESC} native allocations, owners, cleanup steps, and rollback
 * steps would be required. It deliberately allocates no native memory, writes no SDK struct bytes, and calls no CUDA
 * Driver API object-creation functions.</p>
 */
public record CudaImageSamplerNativeDescriptorAllocationPreflight(
        List<Entry> entries,
        String nativeDescriptorEncodingPlanStatus,
        boolean nativeDescriptorEncodingPlanReady,
        boolean javaNativeDescriptorAllocationPreflightEnabled,
        boolean nativeDescriptorAllocationEnabled,
        boolean sdkStructByteEncodingEnabled,
        boolean objectCreationEnabled,
        boolean runtimeBindingEnabled,
        int activeNativeDescriptorCount
) {

    public record Entry(
            int parameterIndex,
            String parameterName,
            String javaType,
            String abiKey,
            String cudaAbiRole,
            boolean resourceDescriptorAllocationRequired,
            boolean resourceDescriptorAllocationPlanned,
            boolean resourceDescriptorAllocated,
            String resourceDescriptorStruct,
            boolean textureDescriptorAllocationRequired,
            boolean textureDescriptorAllocationPlanned,
            boolean textureDescriptorAllocated,
            String textureDescriptorStruct,
            boolean nativeDescriptorAllocationEnabled,
            boolean sdkStructByteEncodingEnabled,
            String nativeDescriptorAllocationStatus,
            String nativeDescriptorOwnershipStatus,
            String cleanupStatus,
            String rollbackStatus,
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
            resourceDescriptorStruct = normalize(resourceDescriptorStruct, resourceDescriptorAllocationRequired ? "CUDA_RESOURCE_DESC" : "not-required");
            textureDescriptorStruct = normalize(textureDescriptorStruct, textureDescriptorAllocationRequired ? "CUDA_TEXTURE_DESC" : "not-required");
            nativeDescriptorAllocationStatus = normalize(nativeDescriptorAllocationStatus, "blocked");
            nativeDescriptorOwnershipStatus = normalize(nativeDescriptorOwnershipStatus, "planned-inactive");
            cleanupStatus = normalize(cleanupStatus, "planned-inactive");
            rollbackStatus = normalize(rollbackStatus, "planned-inactive");
            sdkStructByteEncodingStatus = normalize(sdkStructByteEncodingStatus, "disabled");
            objectCreationStatus = normalize(objectCreationStatus, "disabled");
            runtimeBindingStatus = normalize(runtimeBindingStatus, "fail-closed");
            firstBlocker = normalize(firstBlocker, "none");
        }

        public boolean descriptorAllocationRequired() {
            return resourceDescriptorAllocationRequired || textureDescriptorAllocationRequired;
        }

        public long resourceDescriptorAllocationCount() {
            return resourceDescriptorAllocationRequired ? 1 : 0;
        }

        public long textureDescriptorAllocationCount() {
            return textureDescriptorAllocationRequired ? 1 : 0;
        }

        public long plannedNativeDescriptorCount() {
            long count = 0;
            if (resourceDescriptorAllocationPlanned) {
                count++;
            }
            if (textureDescriptorAllocationPlanned) {
                count++;
            }
            return count;
        }

        public long allocatedNativeDescriptorCount() {
            long count = 0;
            if (resourceDescriptorAllocated) {
                count++;
            }
            if (textureDescriptorAllocated) {
                count++;
            }
            return count;
        }

        public long nativeDescriptorOwnershipRequiredCount() {
            return plannedNativeDescriptorCount();
        }

        public long nativeDescriptorOwnershipPlannedCount() {
            return "planned-inactive".equals(nativeDescriptorOwnershipStatus) ? plannedNativeDescriptorCount() : 0;
        }

        public long nativeDescriptorOwnershipActiveCount() {
            return "active".equals(nativeDescriptorOwnershipStatus) ? allocatedNativeDescriptorCount() : 0;
        }

        public long cleanupRequiredCount() {
            return plannedNativeDescriptorCount();
        }

        public long cleanupPlannedCount() {
            return "planned-inactive".equals(cleanupStatus) ? plannedNativeDescriptorCount() : 0;
        }

        public long cleanupActiveCount() {
            return "active".equals(cleanupStatus) ? allocatedNativeDescriptorCount() : 0;
        }

        public long rollbackRequiredCount() {
            return plannedNativeDescriptorCount();
        }

        public long rollbackPlannedCount() {
            return "planned-inactive".equals(rollbackStatus) ? plannedNativeDescriptorCount() : 0;
        }

        public long rollbackActiveCount() {
            return "active".equals(rollbackStatus) ? allocatedNativeDescriptorCount() : 0;
        }

        public boolean ready() {
            if (!descriptorAllocationRequired()) {
                return "not-required".equals(nativeDescriptorAllocationStatus)
                        && "disabled".equals(sdkStructByteEncodingStatus)
                        && "disabled".equals(objectCreationStatus)
                        && "fail-closed".equals(runtimeBindingStatus)
                        && !nativeDescriptorAllocationEnabled
                        && !sdkStructByteEncodingEnabled
                        && !productionSupportEnabled
                        && "none".equals(firstBlocker);
            }
            return "native-descriptor-allocation-ready".equals(nativeDescriptorAllocationStatus)
                    && "active".equals(nativeDescriptorOwnershipStatus)
                    && "active".equals(cleanupStatus)
                    && "active".equals(rollbackStatus)
                    && "disabled".equals(sdkStructByteEncodingStatus)
                    && "disabled".equals(objectCreationStatus)
                    && "fail-closed".equals(runtimeBindingStatus)
                    && nativeDescriptorAllocationEnabled
                    && !sdkStructByteEncodingEnabled
                    && !productionSupportEnabled
                    && "none".equals(firstBlocker)
                    && (!resourceDescriptorAllocationRequired || (resourceDescriptorAllocationPlanned
                    && resourceDescriptorAllocated
                    && "CUDA_RESOURCE_DESC".equals(resourceDescriptorStruct)))
                    && (!textureDescriptorAllocationRequired || (textureDescriptorAllocationPlanned
                    && textureDescriptorAllocated
                    && "CUDA_TEXTURE_DESC".equals(textureDescriptorStruct)));
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.entry"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".parameter.index", Integer.toString(parameterIndex));
            fields.put(normalizedPrefix + ".parameter.name", parameterName);
            fields.put(normalizedPrefix + ".parameter.javaType", javaType);
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".abi.key", abiKey);
            fields.put(normalizedPrefix + ".abi.role", cudaAbiRole);
            fields.put(normalizedPrefix + ".resourceDescriptorAllocation.required", Boolean.toString(resourceDescriptorAllocationRequired));
            fields.put(normalizedPrefix + ".resourceDescriptorAllocation.planned", Boolean.toString(resourceDescriptorAllocationPlanned));
            fields.put(normalizedPrefix + ".resourceDescriptor.allocated", Boolean.toString(resourceDescriptorAllocated));
            fields.put(normalizedPrefix + ".resourceDescriptor.struct", resourceDescriptorStruct);
            fields.put(normalizedPrefix + ".textureDescriptorAllocation.required", Boolean.toString(textureDescriptorAllocationRequired));
            fields.put(normalizedPrefix + ".textureDescriptorAllocation.planned", Boolean.toString(textureDescriptorAllocationPlanned));
            fields.put(normalizedPrefix + ".textureDescriptor.allocated", Boolean.toString(textureDescriptorAllocated));
            fields.put(normalizedPrefix + ".textureDescriptor.struct", textureDescriptorStruct);
            fields.put(normalizedPrefix + ".plannedNativeDescriptor.count", Long.toString(plannedNativeDescriptorCount()));
            fields.put(normalizedPrefix + ".allocatedNativeDescriptor.count", Long.toString(allocatedNativeDescriptorCount()));
            fields.put(normalizedPrefix + ".nativeDescriptorOwnership.required.count", Long.toString(nativeDescriptorOwnershipRequiredCount()));
            fields.put(normalizedPrefix + ".nativeDescriptorOwnership.planned.count", Long.toString(nativeDescriptorOwnershipPlannedCount()));
            fields.put(normalizedPrefix + ".nativeDescriptorOwnership.active.count", Long.toString(nativeDescriptorOwnershipActiveCount()));
            fields.put(normalizedPrefix + ".cleanup.required.count", Long.toString(cleanupRequiredCount()));
            fields.put(normalizedPrefix + ".cleanup.planned.count", Long.toString(cleanupPlannedCount()));
            fields.put(normalizedPrefix + ".cleanup.active.count", Long.toString(cleanupActiveCount()));
            fields.put(normalizedPrefix + ".rollback.required.count", Long.toString(rollbackRequiredCount()));
            fields.put(normalizedPrefix + ".rollback.planned.count", Long.toString(rollbackPlannedCount()));
            fields.put(normalizedPrefix + ".rollback.active.count", Long.toString(rollbackActiveCount()));
            fields.put(normalizedPrefix + ".nativeDescriptorAllocation.enabled", Boolean.toString(nativeDescriptorAllocationEnabled));
            fields.put(normalizedPrefix + ".sdkStructByteEncoding.enabled", Boolean.toString(sdkStructByteEncodingEnabled));
            fields.put(normalizedPrefix + ".nativeDescriptorAllocation.status", nativeDescriptorAllocationStatus);
            fields.put(normalizedPrefix + ".nativeDescriptorOwnership.status", nativeDescriptorOwnershipStatus);
            fields.put(normalizedPrefix + ".cleanup.status", cleanupStatus);
            fields.put(normalizedPrefix + ".rollback.status", rollbackStatus);
            fields.put(normalizedPrefix + ".sdkStructByteEncoding.status", sdkStructByteEncodingStatus);
            fields.put(normalizedPrefix + ".objectCreation.status", objectCreationStatus);
            fields.put(normalizedPrefix + ".runtimeBinding.status", runtimeBindingStatus);
            fields.put(normalizedPrefix + ".productionSupport.enabled", Boolean.toString(productionSupportEnabled));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerNativeDescriptorAllocationPreflight {
        entries = entries == null ? List.of() : List.copyOf(entries);
        nativeDescriptorEncodingPlanStatus = normalize(nativeDescriptorEncodingPlanStatus, "not-present");
        activeNativeDescriptorCount = Math.max(0, activeNativeDescriptorCount);
    }

    static CudaImageSamplerNativeDescriptorAllocationPreflight empty() {
        return new CudaImageSamplerNativeDescriptorAllocationPreflight(
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

    static CudaImageSamplerNativeDescriptorAllocationPreflight from(CudaImageSamplerNativeDescriptorEncodingPlan encodingPlan) {
        if (encodingPlan == null || !encodingPlan.present()) {
            return empty();
        }
        return new CudaImageSamplerNativeDescriptorAllocationPreflight(
                encodingPlan.entries().stream()
                        .map(CudaImageSamplerNativeDescriptorAllocationPreflight::entry)
                        .toList(),
                encodingPlan.status(),
                encodingPlan.ready(),
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
                && "ready".equals(nativeDescriptorEncodingPlanStatus)
                && nativeDescriptorEncodingPlanReady
                && javaNativeDescriptorAllocationPreflightEnabled
                && nativeDescriptorAllocationEnabled
                && !sdkStructByteEncodingEnabled
                && !objectCreationEnabled
                && !runtimeBindingEnabled
                && activeNativeDescriptorCount == allocatedNativeDescriptorCount()
                && activeNativeDescriptorCount > 0;
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

    public long resourceDescriptorAllocationCount() {
        return entries.stream().mapToLong(Entry::resourceDescriptorAllocationCount).sum();
    }

    public long resourceDescriptorAllocatedCount() {
        return entries.stream().filter(Entry::resourceDescriptorAllocated).count();
    }

    public long textureDescriptorAllocationCount() {
        return entries.stream().mapToLong(Entry::textureDescriptorAllocationCount).sum();
    }

    public long textureDescriptorAllocatedCount() {
        return entries.stream().filter(Entry::textureDescriptorAllocated).count();
    }

    public long plannedNativeDescriptorCount() {
        return entries.stream().mapToLong(Entry::plannedNativeDescriptorCount).sum();
    }

    public long allocatedNativeDescriptorCount() {
        return entries.stream().mapToLong(Entry::allocatedNativeDescriptorCount).sum();
    }

    public long nativeDescriptorOwnershipRequiredCount() {
        return entries.stream().mapToLong(Entry::nativeDescriptorOwnershipRequiredCount).sum();
    }

    public long nativeDescriptorOwnershipPlannedCount() {
        return entries.stream().mapToLong(Entry::nativeDescriptorOwnershipPlannedCount).sum();
    }

    public long nativeDescriptorOwnershipActiveCount() {
        return entries.stream().mapToLong(Entry::nativeDescriptorOwnershipActiveCount).sum();
    }

    public long cleanupRequiredCount() {
        return entries.stream().mapToLong(Entry::cleanupRequiredCount).sum();
    }

    public long cleanupPlannedCount() {
        return entries.stream().mapToLong(Entry::cleanupPlannedCount).sum();
    }

    public long cleanupActiveCount() {
        return entries.stream().mapToLong(Entry::cleanupActiveCount).sum();
    }

    public long rollbackRequiredCount() {
        return entries.stream().mapToLong(Entry::rollbackRequiredCount).sum();
    }

    public long rollbackPlannedCount() {
        return entries.stream().mapToLong(Entry::rollbackPlannedCount).sum();
    }

    public long rollbackActiveCount() {
        return entries.stream().mapToLong(Entry::rollbackActiveCount).sum();
    }

    public long allocationEnabledCount() {
        return entries.stream().filter(Entry::nativeDescriptorAllocationEnabled).count();
    }

    public long sdkStructByteEncodingEnabledCount() {
        return entries.stream().filter(Entry::sdkStructByteEncodingEnabled).count();
    }

    public String firstBlocker() {
        if (!present()) {
            return "none";
        }
        return entries.stream()
                .map(Entry::firstBlocker)
                .filter(blocker -> !"none".equals(blocker))
                .findFirst()
                .orElseGet(() -> ready() ? "none" : "cuda-image-sampler-native-descriptor-allocation-preflight-not-ready");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight");
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", Boolean.toString(present()));
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".ready", Boolean.toString(ready()));
        fields.put(prefix + ".nativeDescriptorEncodingPlan.status", nativeDescriptorEncodingPlanStatus);
        fields.put(prefix + ".nativeDescriptorEncodingPlan.ready", Boolean.toString(nativeDescriptorEncodingPlanReady));
        fields.put(prefix + ".javaNativeDescriptorAllocationPreflight.enabled", Boolean.toString(javaNativeDescriptorAllocationPreflightEnabled));
        fields.put(prefix + ".nativeDescriptorAllocation.enabled", Boolean.toString(nativeDescriptorAllocationEnabled));
        fields.put(prefix + ".sdkStructByteEncoding.enabled", Boolean.toString(sdkStructByteEncodingEnabled));
        fields.put(prefix + ".objectCreation.enabled", Boolean.toString(objectCreationEnabled));
        fields.put(prefix + ".runtimeBinding.enabled", Boolean.toString(runtimeBindingEnabled));
        fields.put(prefix + ".activeNativeDescriptor.count", Integer.toString(activeNativeDescriptorCount));
        fields.put(prefix + ".entry.count", Integer.toString(entries.size()));
        fields.put(prefix + ".entry.ready.count", Long.toString(entryReadyCount()));
        fields.put(prefix + ".entry.blocked.count", Long.toString(entryBlockedCount()));
        fields.put(prefix + ".resourceDescriptorAllocation.count", Long.toString(resourceDescriptorAllocationCount()));
        fields.put(prefix + ".resourceDescriptor.allocated.count", Long.toString(resourceDescriptorAllocatedCount()));
        fields.put(prefix + ".textureDescriptorAllocation.count", Long.toString(textureDescriptorAllocationCount()));
        fields.put(prefix + ".textureDescriptor.allocated.count", Long.toString(textureDescriptorAllocatedCount()));
        fields.put(prefix + ".plannedNativeDescriptor.count", Long.toString(plannedNativeDescriptorCount()));
        fields.put(prefix + ".allocatedNativeDescriptor.count", Long.toString(allocatedNativeDescriptorCount()));
        fields.put(prefix + ".nativeDescriptorOwnership.required.count", Long.toString(nativeDescriptorOwnershipRequiredCount()));
        fields.put(prefix + ".nativeDescriptorOwnership.planned.count", Long.toString(nativeDescriptorOwnershipPlannedCount()));
        fields.put(prefix + ".nativeDescriptorOwnership.active.count", Long.toString(nativeDescriptorOwnershipActiveCount()));
        fields.put(prefix + ".cleanup.required.count", Long.toString(cleanupRequiredCount()));
        fields.put(prefix + ".cleanup.planned.count", Long.toString(cleanupPlannedCount()));
        fields.put(prefix + ".cleanup.active.count", Long.toString(cleanupActiveCount()));
        fields.put(prefix + ".rollback.required.count", Long.toString(rollbackRequiredCount()));
        fields.put(prefix + ".rollback.planned.count", Long.toString(rollbackPlannedCount()));
        fields.put(prefix + ".rollback.active.count", Long.toString(rollbackActiveCount()));
        fields.put(prefix + ".allocation.enabled.count", Long.toString(allocationEnabledCount()));
        fields.put(prefix + ".sdkStructByteEncoding.enabled.count", Long.toString(sdkStructByteEncodingEnabledCount()));
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < entries.size(); index++) {
            fields.putAll(entries.get(index).artifactFields(prefix + ".entry." + index));
        }
    }

    private static Entry entry(CudaImageSamplerNativeDescriptorEncodingPlan.Entry encodingEntry) {
        if (encodingEntry == null) {
            return blockedEntry("cuda-image-sampler-native-descriptor-encoding-plan-entry-missing");
        }
        if (!encodingEntry.ready()) {
            return new Entry(
                    encodingEntry.parameterIndex(),
                    encodingEntry.parameterName(),
                    encodingEntry.javaType(),
                    encodingEntry.abiKey(),
                    encodingEntry.cudaAbiRole(),
                    false,
                    false,
                    false,
                    "not-required",
                    false,
                    false,
                    false,
                    "not-required",
                    false,
                    false,
                    "blocked",
                    "planned-inactive",
                    "planned-inactive",
                    "planned-inactive",
                    "disabled",
                    "disabled",
                    "fail-closed",
                    encodingEntry.productionSupportEnabled(),
                    encodingEntry.firstBlocker()
            );
        }
        boolean resourceRequired = encodingEntry.resourceEncodingRequired();
        boolean textureRequired = encodingEntry.textureEncodingRequired();
        boolean allocationRequired = resourceRequired || textureRequired;
        return new Entry(
                encodingEntry.parameterIndex(),
                encodingEntry.parameterName(),
                encodingEntry.javaType(),
                encodingEntry.abiKey(),
                encodingEntry.cudaAbiRole(),
                resourceRequired,
                resourceRequired,
                false,
                resourceRequired ? "CUDA_RESOURCE_DESC" : "not-required",
                textureRequired,
                textureRequired,
                false,
                textureRequired ? "CUDA_TEXTURE_DESC" : "not-required",
                false,
                false,
                allocationRequired ? "blocked" : "not-required",
                allocationRequired ? "planned-inactive" : "not-required",
                allocationRequired ? "planned-inactive" : "not-required",
                allocationRequired ? "planned-inactive" : "not-required",
                encodingEntry.sdkStructByteEncodingStatus(),
                encodingEntry.objectCreationStatus(),
                encodingEntry.runtimeBindingStatus(),
                encodingEntry.productionSupportEnabled(),
                allocationRequired
                        ? "cuda-image-sampler-native-descriptor-allocation-disabled:" + encodingEntry.parameterIndex() + ':' + encodingEntry.javaType()
                        : "none"
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
                false,
                false,
                "not-required",
                false,
                false,
                false,
                "not-required",
                false,
                false,
                "blocked",
                "planned-inactive",
                "planned-inactive",
                "planned-inactive",
                "disabled",
                "disabled",
                "fail-closed",
                false,
                firstBlocker
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
