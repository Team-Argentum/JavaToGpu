package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeNativeMemoryServiceRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Explicit opt-in result for native CUDA image/sampler descriptor allocation.
 *
 * <p>This class owns only native host memory for future {@code CUDA_RESOURCE_DESC}/{@code CUDA_TEXTURE_DESC} structs.
 * It does not encode CUDA SDK struct bytes, does not create texture/surface objects, and is not used by the default
 * argument binder. Callers must close the result to release native memory.</p>
 */
public final class CudaImageSamplerNativeDescriptorAllocationResult implements AutoCloseable {

    public record Entry(
            int parameterIndex,
            String parameterName,
            String javaType,
            String abiKey,
            String cudaAbiRole,
            List<CudaDriverImageSamplerNativeDescriptor> descriptorOwners,
            String allocationStatus,
            String firstBlocker
    ) {
        public Entry {
            parameterIndex = Math.max(0, parameterIndex);
            parameterName = normalize(parameterName, "unknown");
            javaType = normalize(javaType, "unknown");
            abiKey = normalize(abiKey, "unknown");
            cudaAbiRole = normalize(cudaAbiRole, "unknown");
            descriptorOwners = descriptorOwners == null ? List.of() : List.copyOf(descriptorOwners);
            allocationStatus = normalize(allocationStatus, "blocked");
            firstBlocker = normalize(firstBlocker, "none");
        }

        public boolean descriptorOwnerRequired() {
            return !descriptorOwners.isEmpty();
        }

        public long resourceDescriptorOwnerCount() {
            return descriptorOwners.stream().filter(owner -> "resource".equals(owner.descriptorKind())).count();
        }

        public long textureDescriptorOwnerCount() {
            return descriptorOwners.stream().filter(owner -> "texture".equals(owner.descriptorKind())).count();
        }

        public long activeDescriptorOwnerCount() {
            return descriptorOwners.stream().filter(CudaDriverImageSamplerNativeDescriptor::active).count();
        }

        public long nativeAddressPresentCount() {
            return descriptorOwners.stream().filter(CudaDriverImageSamplerNativeDescriptor::nativeAddressPresent).count();
        }

        public long allocationEnabledCount() {
            return descriptorOwners.stream().filter(CudaDriverImageSamplerNativeDescriptor::allocationEnabled).count();
        }

        public long cleanupActiveCount() {
            return descriptorOwners.stream().filter(owner -> "active".equals(owner.cleanupStatus()) && !owner.closed()).count();
        }

        public long rollbackActiveCount() {
            return descriptorOwners.stream().filter(owner -> "active".equals(owner.rollbackStatus()) && !owner.closed()).count();
        }

        public long nativeByteSize() {
            return descriptorOwners.stream().mapToLong(CudaDriverImageSamplerNativeDescriptor::nativeByteSize).sum();
        }

        public boolean ready() {
            return "ready".equals(allocationStatus)
                    && "none".equals(firstBlocker)
                    && (!descriptorOwnerRequired() || (activeDescriptorOwnerCount() == descriptorOwners.size()
                    && nativeAddressPresentCount() == descriptorOwners.size()
                    && allocationEnabledCount() == descriptorOwners.size()
                    && cleanupActiveCount() == descriptorOwners.size()
                    && rollbackActiveCount() == descriptorOwners.size()));
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerNativeDescriptorAllocationResult.entry"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".parameter.index", Integer.toString(parameterIndex));
            fields.put(normalizedPrefix + ".parameter.name", parameterName);
            fields.put(normalizedPrefix + ".parameter.javaType", javaType);
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".abi.key", abiKey);
            fields.put(normalizedPrefix + ".abi.role", cudaAbiRole);
            fields.put(normalizedPrefix + ".descriptorOwner.count", Integer.toString(descriptorOwners.size()));
            fields.put(normalizedPrefix + ".resourceDescriptorOwner.count", Long.toString(resourceDescriptorOwnerCount()));
            fields.put(normalizedPrefix + ".textureDescriptorOwner.count", Long.toString(textureDescriptorOwnerCount()));
            fields.put(normalizedPrefix + ".activeDescriptorOwner.count", Long.toString(activeDescriptorOwnerCount()));
            fields.put(normalizedPrefix + ".nativeAddress.present.count", Long.toString(nativeAddressPresentCount()));
            fields.put(normalizedPrefix + ".allocation.enabled.count", Long.toString(allocationEnabledCount()));
            fields.put(normalizedPrefix + ".cleanup.active.count", Long.toString(cleanupActiveCount()));
            fields.put(normalizedPrefix + ".rollback.active.count", Long.toString(rollbackActiveCount()));
            fields.put(normalizedPrefix + ".nativeByteSize", Long.toString(nativeByteSize()));
            fields.put(normalizedPrefix + ".allocation.status", allocationStatus);
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
            for (int index = 0; index < descriptorOwners.size(); index++) {
                fields.putAll(descriptorOwners.get(index).artifactFields(normalizedPrefix + ".descriptorOwner." + index));
            }
            return Collections.unmodifiableMap(fields);
        }
    }

    private final List<Entry> entries;
    private final String allocationTransactionPlanStatus;
    private final boolean allocationTransactionPlanPresent;
    private final boolean allocationApplyEnabled;
    private final boolean nativeMemoryAllocationEnabled;
    private final boolean sdkStructByteEncodingEnabled;
    private final boolean cleanupApplyEnabled;
    private final boolean rollbackApplyEnabled;
    private final boolean objectCreationEnabled;
    private final boolean runtimeBindingEnabled;
    private boolean closed;

    private CudaImageSamplerNativeDescriptorAllocationResult(
            List<Entry> entries,
            String allocationTransactionPlanStatus,
            boolean allocationTransactionPlanPresent,
            boolean allocationApplyEnabled,
            boolean nativeMemoryAllocationEnabled,
            boolean sdkStructByteEncodingEnabled,
            boolean cleanupApplyEnabled,
            boolean rollbackApplyEnabled,
            boolean objectCreationEnabled,
            boolean runtimeBindingEnabled
    ) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
        this.allocationTransactionPlanStatus = normalize(allocationTransactionPlanStatus, "not-present");
        this.allocationTransactionPlanPresent = allocationTransactionPlanPresent;
        this.allocationApplyEnabled = allocationApplyEnabled;
        this.nativeMemoryAllocationEnabled = nativeMemoryAllocationEnabled;
        this.sdkStructByteEncodingEnabled = sdkStructByteEncodingEnabled;
        this.cleanupApplyEnabled = cleanupApplyEnabled;
        this.rollbackApplyEnabled = rollbackApplyEnabled;
        this.objectCreationEnabled = objectCreationEnabled;
        this.runtimeBindingEnabled = runtimeBindingEnabled;
    }

    static CudaImageSamplerNativeDescriptorAllocationResult empty() {
        return new CudaImageSamplerNativeDescriptorAllocationResult(
                List.of(),
                "not-present",
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }

    static CudaImageSamplerNativeDescriptorAllocationResult allocate(
            CudaImageSamplerNativeDescriptorAllocationTransactionPlan plan,
            int resourceDescriptorByteSize,
            int textureDescriptorByteSize
    ) {
        return allocate(
                plan,
                resourceDescriptorByteSize,
                textureDescriptorByteSize,
                GpuRuntimeNativeMemoryServiceRegistry.loadWithBuiltIns()
        );
    }

    static CudaImageSamplerNativeDescriptorAllocationResult allocate(
            CudaImageSamplerNativeDescriptorAllocationTransactionPlan plan,
            int resourceDescriptorByteSize,
            int textureDescriptorByteSize,
            GpuRuntimeNativeMemoryServiceRegistry nativeMemoryServices
    ) {
        if (plan == null || !plan.present()) {
            return empty();
        }
        GpuRuntimeNativeMemoryServiceRegistry services = nativeMemoryServices == null
                ? GpuRuntimeNativeMemoryServiceRegistry.loadWithBuiltIns()
                : nativeMemoryServices;
        ArrayList<CudaDriverImageSamplerNativeDescriptor> allocatedOwners = new ArrayList<>();
        try {
            List<Entry> entries = plan.entries().stream()
                    .map(entry -> allocateEntry(
                            entry,
                            Math.max(1, resourceDescriptorByteSize),
                            Math.max(1, textureDescriptorByteSize),
                            allocatedOwners,
                            services
                    ))
                    .toList();
            return new CudaImageSamplerNativeDescriptorAllocationResult(
                    entries,
                    plan.status(),
                    plan.present(),
                    true,
                    true,
                    false,
                    true,
                    true,
                    false,
                    false
            );
        } catch (RuntimeException exception) {
            closeOwners(allocatedOwners);
            throw exception;
        }
    }

    public boolean present() {
        return !entries.isEmpty();
    }

    public List<Entry> entries() {
        return entries;
    }

    public String allocationTransactionPlanStatus() {
        return allocationTransactionPlanStatus;
    }

    public boolean allocationTransactionPlanPresent() {
        return allocationTransactionPlanPresent;
    }

    public boolean allocationApplyEnabled() {
        return allocationApplyEnabled;
    }

    public boolean nativeMemoryAllocationEnabled() {
        return nativeMemoryAllocationEnabled;
    }

    public boolean sdkStructByteEncodingEnabled() {
        return sdkStructByteEncodingEnabled;
    }

    public boolean cleanupApplyEnabled() {
        return cleanupApplyEnabled;
    }

    public boolean rollbackApplyEnabled() {
        return rollbackApplyEnabled;
    }

    public boolean objectCreationEnabled() {
        return objectCreationEnabled;
    }

    public boolean runtimeBindingEnabled() {
        return runtimeBindingEnabled;
    }

    public boolean closed() {
        return closed;
    }

    public boolean ready() {
        return !closed
                && present()
                && entries.stream().allMatch(Entry::ready)
                && allocationTransactionPlanPresent
                && allocationApplyEnabled
                && nativeMemoryAllocationEnabled
                && !sdkStructByteEncodingEnabled
                && cleanupApplyEnabled
                && rollbackApplyEnabled
                && !objectCreationEnabled
                && !runtimeBindingEnabled
                && activeNativeDescriptorCount() == descriptorOwnerCount()
                && activeNativeDescriptorCount() > 0;
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

    public long descriptorOwnerCount() {
        return entries.stream().mapToLong(entry -> entry.descriptorOwners().size()).sum();
    }

    public long resourceDescriptorOwnerCount() {
        return entries.stream().mapToLong(Entry::resourceDescriptorOwnerCount).sum();
    }

    public long textureDescriptorOwnerCount() {
        return entries.stream().mapToLong(Entry::textureDescriptorOwnerCount).sum();
    }

    public long activeDescriptorOwnerCount() {
        return entries.stream().mapToLong(Entry::activeDescriptorOwnerCount).sum();
    }

    public long activeNativeDescriptorCount() {
        return activeDescriptorOwnerCount();
    }

    public long nativeAddressPresentCount() {
        return entries.stream().mapToLong(Entry::nativeAddressPresentCount).sum();
    }

    public long allocationEnabledCount() {
        return entries.stream().mapToLong(Entry::allocationEnabledCount).sum();
    }

    public long cleanupActiveCount() {
        return entries.stream().mapToLong(Entry::cleanupActiveCount).sum();
    }

    public long rollbackActiveCount() {
        return entries.stream().mapToLong(Entry::rollbackActiveCount).sum();
    }

    public long nativeByteSize() {
        return entries.stream().mapToLong(Entry::nativeByteSize).sum();
    }

    public String nativeMemoryServiceSummary() {
        String summary = entries.stream()
                .flatMap(entry -> entry.descriptorOwners().stream())
                .map(CudaDriverImageSamplerNativeDescriptor::nativeMemoryServiceId)
                .filter(serviceId -> !"none".equals(serviceId))
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
        return summary.isBlank() ? "none" : summary;
    }

    public String firstBlocker() {
        if (!present()) {
            return "none";
        }
        if (closed) {
            return "cuda-image-sampler-native-descriptor-allocation-result-closed";
        }
        return entries.stream()
                .map(Entry::firstBlocker)
                .filter(blocker -> !"none".equals(blocker))
                .findFirst()
                .orElseGet(() -> ready() ? "none" : "cuda-image-sampler-native-descriptor-allocation-result-not-ready");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerNativeDescriptorAllocationResult"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerNativeDescriptorAllocationResult");
        return Collections.unmodifiableMap(fields);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        ArrayList<CudaDriverImageSamplerNativeDescriptor> owners = new ArrayList<>();
        for (Entry entry : entries) {
            owners.addAll(entry.descriptorOwners());
        }
        closeOwners(owners);
        closed = true;
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", Boolean.toString(present()));
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".ready", Boolean.toString(ready()));
        fields.put(prefix + ".allocationTransactionPlan.status", allocationTransactionPlanStatus);
        fields.put(prefix + ".allocationTransactionPlan.present", Boolean.toString(allocationTransactionPlanPresent));
        fields.put(prefix + ".allocationApply.enabled", Boolean.toString(allocationApplyEnabled));
        fields.put(prefix + ".nativeMemoryAllocation.enabled", Boolean.toString(nativeMemoryAllocationEnabled));
        fields.put(prefix + ".sdkStructByteEncoding.enabled", Boolean.toString(sdkStructByteEncodingEnabled));
        fields.put(prefix + ".cleanupApply.enabled", Boolean.toString(cleanupApplyEnabled));
        fields.put(prefix + ".rollbackApply.enabled", Boolean.toString(rollbackApplyEnabled));
        fields.put(prefix + ".objectCreation.enabled", Boolean.toString(objectCreationEnabled));
        fields.put(prefix + ".runtimeBinding.enabled", Boolean.toString(runtimeBindingEnabled));
        fields.put(prefix + ".entry.count", Integer.toString(entries.size()));
        fields.put(prefix + ".entry.ready.count", Long.toString(entryReadyCount()));
        fields.put(prefix + ".entry.blocked.count", Long.toString(entryBlockedCount()));
        fields.put(prefix + ".descriptorOwner.count", Long.toString(descriptorOwnerCount()));
        fields.put(prefix + ".resourceDescriptorOwner.count", Long.toString(resourceDescriptorOwnerCount()));
        fields.put(prefix + ".textureDescriptorOwner.count", Long.toString(textureDescriptorOwnerCount()));
        fields.put(prefix + ".activeDescriptorOwner.count", Long.toString(activeDescriptorOwnerCount()));
        fields.put(prefix + ".nativeAddress.present.count", Long.toString(nativeAddressPresentCount()));
        fields.put(prefix + ".allocation.enabled.count", Long.toString(allocationEnabledCount()));
        fields.put(prefix + ".cleanup.active.count", Long.toString(cleanupActiveCount()));
        fields.put(prefix + ".rollback.active.count", Long.toString(rollbackActiveCount()));
        fields.put(prefix + ".activeNativeDescriptor.count", Long.toString(activeNativeDescriptorCount()));
        fields.put(prefix + ".nativeByteSize", Long.toString(nativeByteSize()));
        fields.put(prefix + ".nativeMemory.service.summary", nativeMemoryServiceSummary());
        fields.put(prefix + ".closed", Boolean.toString(closed));
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < entries.size(); index++) {
            fields.putAll(entries.get(index).artifactFields(prefix + ".entry." + index));
        }
    }

    private static Entry allocateEntry(
            CudaImageSamplerNativeDescriptorAllocationTransactionPlan.Entry planEntry,
            int resourceDescriptorByteSize,
            int textureDescriptorByteSize,
            List<CudaDriverImageSamplerNativeDescriptor> allocatedOwners,
            GpuRuntimeNativeMemoryServiceRegistry nativeMemoryServices
    ) {
        if (planEntry == null) {
            return new Entry(
                    0,
                    "unknown",
                    "unknown",
                    "unknown",
                    "unknown",
                    List.of(),
                    "blocked",
                    "cuda-image-sampler-native-descriptor-allocation-plan-entry-missing"
            );
        }
        if (!planEntry.descriptorOwnerRequired()) {
            return new Entry(
                    planEntry.parameterIndex(),
                    planEntry.parameterName(),
                    planEntry.javaType(),
                    planEntry.abiKey(),
                    planEntry.cudaAbiRole(),
                    List.of(),
                    planEntry.status(),
                    planEntry.firstBlocker()
            );
        }
        ArrayList<CudaDriverImageSamplerNativeDescriptor> owners = new ArrayList<>();
        for (CudaDriverImageSamplerNativeDescriptor owner : planEntry.descriptorOwners()) {
            CudaDriverImageSamplerNativeDescriptor allocatedOwner;
            if ("texture".equals(owner.descriptorKind())) {
                allocatedOwner = CudaDriverImageSamplerNativeDescriptor.allocatedTexture(
                        owner.parameterIndex(),
                        owner.parameterName(),
                        owner.javaType(),
                        owner.cleanupOrder(),
                        owner.rollbackOrder(),
                        textureDescriptorByteSize,
                        nativeMemoryServices
                );
            } else {
                allocatedOwner = CudaDriverImageSamplerNativeDescriptor.allocatedResource(
                        owner.parameterIndex(),
                        owner.parameterName(),
                        owner.javaType(),
                        owner.cleanupOrder(),
                        owner.rollbackOrder(),
                        resourceDescriptorByteSize,
                        nativeMemoryServices
                );
            }
            owners.add(allocatedOwner);
            allocatedOwners.add(allocatedOwner);
        }
        return new Entry(
                planEntry.parameterIndex(),
                planEntry.parameterName(),
                planEntry.javaType(),
                planEntry.abiKey(),
                planEntry.cudaAbiRole(),
                owners,
                "ready",
                "none"
        );
    }

    private static void closeOwners(List<CudaDriverImageSamplerNativeDescriptor> owners) {
        RuntimeException failure = null;
        for (CudaDriverImageSamplerNativeDescriptor owner : owners == null ? List.<CudaDriverImageSamplerNativeDescriptor>of() : owners) {
            try {
                owner.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
