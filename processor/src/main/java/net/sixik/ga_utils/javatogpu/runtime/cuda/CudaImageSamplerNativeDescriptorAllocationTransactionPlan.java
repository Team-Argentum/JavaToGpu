package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fail-closed transaction plan for future CUDA native descriptor allocation.
 *
 * <p>The plan materializes Java-side descriptor-owner skeletons and deterministic cleanup/rollback order. It does not
 * allocate native memory, does not encode CUDA SDK struct bytes, and does not apply cleanup or rollback callbacks.</p>
 */
public record CudaImageSamplerNativeDescriptorAllocationTransactionPlan(
        List<Entry> entries,
        String nativeDescriptorAllocationPreflightStatus,
        boolean nativeDescriptorAllocationPreflightPresent,
        boolean javaAllocationTransactionPlanEnabled,
        boolean allocationApplyEnabled,
        boolean nativeMemoryAllocationEnabled,
        boolean sdkStructByteEncodingEnabled,
        boolean cleanupApplyEnabled,
        boolean rollbackApplyEnabled,
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
            List<CudaDriverImageSamplerNativeDescriptor> descriptorOwners,
            String allocationTransactionStatus,
            String firstBlocker
    ) {
        public Entry {
            parameterIndex = Math.max(0, parameterIndex);
            parameterName = normalize(parameterName, "unknown");
            javaType = normalize(javaType, "unknown");
            abiKey = normalize(abiKey, "unknown");
            cudaAbiRole = normalize(cudaAbiRole, "unknown");
            descriptorOwners = descriptorOwners == null ? List.of() : List.copyOf(descriptorOwners);
            allocationTransactionStatus = normalize(allocationTransactionStatus, "blocked");
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

        public long cleanupPlannedCount() {
            return descriptorOwners.stream().filter(owner -> "planned-inactive".equals(owner.cleanupStatus())).count();
        }

        public long rollbackPlannedCount() {
            return descriptorOwners.stream().filter(owner -> "planned-inactive".equals(owner.rollbackStatus())).count();
        }

        public long cleanupActiveCount() {
            return descriptorOwners.stream().filter(owner -> "active".equals(owner.cleanupStatus())).count();
        }

        public long rollbackActiveCount() {
            return descriptorOwners.stream().filter(owner -> "active".equals(owner.rollbackStatus())).count();
        }

        public boolean ready() {
            return "ready".equals(allocationTransactionStatus)
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
                    ? "runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.entry"
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
            fields.put(normalizedPrefix + ".cleanup.planned.count", Long.toString(cleanupPlannedCount()));
            fields.put(normalizedPrefix + ".cleanup.active.count", Long.toString(cleanupActiveCount()));
            fields.put(normalizedPrefix + ".rollback.planned.count", Long.toString(rollbackPlannedCount()));
            fields.put(normalizedPrefix + ".rollback.active.count", Long.toString(rollbackActiveCount()));
            fields.put(normalizedPrefix + ".allocationTransaction.status", allocationTransactionStatus);
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
            for (int index = 0; index < descriptorOwners.size(); index++) {
                fields.putAll(descriptorOwners.get(index).artifactFields(normalizedPrefix + ".descriptorOwner." + index));
            }
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerNativeDescriptorAllocationTransactionPlan {
        entries = entries == null ? List.of() : List.copyOf(entries);
        nativeDescriptorAllocationPreflightStatus = normalize(nativeDescriptorAllocationPreflightStatus, "not-present");
        activeNativeDescriptorCount = Math.max(0, activeNativeDescriptorCount);
    }

    static CudaImageSamplerNativeDescriptorAllocationTransactionPlan empty() {
        return new CudaImageSamplerNativeDescriptorAllocationTransactionPlan(
                List.of(),
                "not-present",
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                0
        );
    }

    static CudaImageSamplerNativeDescriptorAllocationTransactionPlan from(
            CudaImageSamplerNativeDescriptorAllocationPreflight preflight
    ) {
        if (preflight == null || !preflight.present()) {
            return empty();
        }
        return new CudaImageSamplerNativeDescriptorAllocationTransactionPlan(
                entries(preflight.entries()),
                preflight.status(),
                preflight.present(),
                true,
                false,
                false,
                false,
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
                && "ready".equals(nativeDescriptorAllocationPreflightStatus)
                && nativeDescriptorAllocationPreflightPresent
                && javaAllocationTransactionPlanEnabled
                && allocationApplyEnabled
                && nativeMemoryAllocationEnabled
                && !sdkStructByteEncodingEnabled
                && cleanupApplyEnabled
                && rollbackApplyEnabled
                && !objectCreationEnabled
                && !runtimeBindingEnabled
                && activeNativeDescriptorCount == activeDescriptorOwnerCount()
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

    public long nativeAddressPresentCount() {
        return entries.stream().mapToLong(Entry::nativeAddressPresentCount).sum();
    }

    public long allocationEnabledCount() {
        return entries.stream().mapToLong(Entry::allocationEnabledCount).sum();
    }

    public long cleanupPlannedCount() {
        return entries.stream().mapToLong(Entry::cleanupPlannedCount).sum();
    }

    public long cleanupActiveCount() {
        return entries.stream().mapToLong(Entry::cleanupActiveCount).sum();
    }

    public long rollbackPlannedCount() {
        return entries.stream().mapToLong(Entry::rollbackPlannedCount).sum();
    }

    public long rollbackActiveCount() {
        return entries.stream().mapToLong(Entry::rollbackActiveCount).sum();
    }

    public String firstBlocker() {
        if (!present()) {
            return "none";
        }
        return entries.stream()
                .map(Entry::firstBlocker)
                .filter(blocker -> !"none".equals(blocker))
                .findFirst()
                .orElseGet(() -> ready() ? "none" : "cuda-image-sampler-native-descriptor-allocation-transaction-not-ready");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan");
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", Boolean.toString(present()));
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".ready", Boolean.toString(ready()));
        fields.put(prefix + ".nativeDescriptorAllocationPreflight.status", nativeDescriptorAllocationPreflightStatus);
        fields.put(prefix + ".nativeDescriptorAllocationPreflight.present", Boolean.toString(nativeDescriptorAllocationPreflightPresent));
        fields.put(prefix + ".javaAllocationTransactionPlan.enabled", Boolean.toString(javaAllocationTransactionPlanEnabled));
        fields.put(prefix + ".allocationApply.enabled", Boolean.toString(allocationApplyEnabled));
        fields.put(prefix + ".nativeMemoryAllocation.enabled", Boolean.toString(nativeMemoryAllocationEnabled));
        fields.put(prefix + ".sdkStructByteEncoding.enabled", Boolean.toString(sdkStructByteEncodingEnabled));
        fields.put(prefix + ".cleanupApply.enabled", Boolean.toString(cleanupApplyEnabled));
        fields.put(prefix + ".rollbackApply.enabled", Boolean.toString(rollbackApplyEnabled));
        fields.put(prefix + ".objectCreation.enabled", Boolean.toString(objectCreationEnabled));
        fields.put(prefix + ".runtimeBinding.enabled", Boolean.toString(runtimeBindingEnabled));
        fields.put(prefix + ".activeNativeDescriptor.count", Integer.toString(activeNativeDescriptorCount));
        fields.put(prefix + ".entry.count", Integer.toString(entries.size()));
        fields.put(prefix + ".entry.ready.count", Long.toString(entryReadyCount()));
        fields.put(prefix + ".entry.blocked.count", Long.toString(entryBlockedCount()));
        fields.put(prefix + ".descriptorOwner.count", Long.toString(descriptorOwnerCount()));
        fields.put(prefix + ".resourceDescriptorOwner.count", Long.toString(resourceDescriptorOwnerCount()));
        fields.put(prefix + ".textureDescriptorOwner.count", Long.toString(textureDescriptorOwnerCount()));
        fields.put(prefix + ".activeDescriptorOwner.count", Long.toString(activeDescriptorOwnerCount()));
        fields.put(prefix + ".nativeAddress.present.count", Long.toString(nativeAddressPresentCount()));
        fields.put(prefix + ".allocation.enabled.count", Long.toString(allocationEnabledCount()));
        fields.put(prefix + ".cleanup.planned.count", Long.toString(cleanupPlannedCount()));
        fields.put(prefix + ".cleanup.active.count", Long.toString(cleanupActiveCount()));
        fields.put(prefix + ".rollback.planned.count", Long.toString(rollbackPlannedCount()));
        fields.put(prefix + ".rollback.active.count", Long.toString(rollbackActiveCount()));
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < entries.size(); index++) {
            fields.putAll(entries.get(index).artifactFields(prefix + ".entry." + index));
        }
    }

    private static List<Entry> entries(List<CudaImageSamplerNativeDescriptorAllocationPreflight.Entry> preflightEntries) {
        ArrayList<Entry> entries = new ArrayList<>();
        int cleanupOrder = 0;
        int rollbackOrder = 0;
        for (CudaImageSamplerNativeDescriptorAllocationPreflight.Entry preflightEntry : preflightEntries) {
            ArrayList<CudaDriverImageSamplerNativeDescriptor> owners = new ArrayList<>();
            if (preflightEntry != null && preflightEntry.resourceDescriptorAllocationPlanned()) {
                owners.add(CudaDriverImageSamplerNativeDescriptor.plannedResource(
                        preflightEntry.parameterIndex(),
                        preflightEntry.parameterName(),
                        preflightEntry.javaType(),
                        cleanupOrder++,
                        rollbackOrder++
                ));
            }
            if (preflightEntry != null && preflightEntry.textureDescriptorAllocationPlanned()) {
                owners.add(CudaDriverImageSamplerNativeDescriptor.plannedTexture(
                        preflightEntry.parameterIndex(),
                        preflightEntry.parameterName(),
                        preflightEntry.javaType(),
                        cleanupOrder++,
                        rollbackOrder++
                ));
            }
            entries.add(entry(preflightEntry, owners));
        }
        return List.copyOf(entries);
    }

    private static Entry entry(
            CudaImageSamplerNativeDescriptorAllocationPreflight.Entry preflightEntry,
            List<CudaDriverImageSamplerNativeDescriptor> owners
    ) {
        if (preflightEntry == null) {
            return new Entry(
                    0,
                    "unknown",
                    "unknown",
                    "unknown",
                    "unknown",
                    List.of(),
                    "blocked",
                    "cuda-image-sampler-native-descriptor-allocation-preflight-entry-missing"
            );
        }
        boolean planned = owners != null && !owners.isEmpty();
        return new Entry(
                preflightEntry.parameterIndex(),
                preflightEntry.parameterName(),
                preflightEntry.javaType(),
                preflightEntry.abiKey(),
                preflightEntry.cudaAbiRole(),
                owners,
                planned ? "blocked" : preflightEntry.status(),
                planned
                        ? "cuda-image-sampler-native-descriptor-allocation-transaction-disabled:" + preflightEntry.parameterIndex() + ':' + preflightEntry.javaType()
                        : preflightEntry.firstBlocker()
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
