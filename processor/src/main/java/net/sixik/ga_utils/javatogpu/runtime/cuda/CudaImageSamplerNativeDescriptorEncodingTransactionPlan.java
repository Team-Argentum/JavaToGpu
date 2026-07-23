package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fail-closed transaction plan for future CUDA native descriptor field writes.
 *
 * <p>The plan connects logical {@code CUDA_RESOURCE_DESC} / {@code CUDA_TEXTURE_DESC} field-write intents to the
 * planned descriptor-owner slots from the allocation transaction plan. It does not write native memory, encode CUDA SDK
 * struct bytes, allocate descriptors, or create texture/surface objects.</p>
 */
public record CudaImageSamplerNativeDescriptorEncodingTransactionPlan(
        List<Entry> entries,
        String nativeDescriptorEncodingPlanStatus,
        boolean nativeDescriptorEncodingPlanPresent,
        String nativeDescriptorAllocationTransactionPlanStatus,
        boolean nativeDescriptorAllocationTransactionPlanPresent,
        boolean javaEncodingTransactionPlanEnabled,
        boolean writeTransactionApplyEnabled,
        boolean nativeMemoryAllocationEnabled,
        boolean sdkStructByteEncodingEnabled,
        boolean objectCreationEnabled,
        boolean runtimeBindingEnabled,
        int activeNativeDescriptorCount
) {

    public record DescriptorWrite(
            String descriptorKind,
            String targetStruct,
            boolean ownerPresent,
            boolean ownerActive,
            boolean nativeAddressPresent,
            boolean allocationEnabled,
            int cleanupOrder,
            int rollbackOrder,
            List<CudaImageSamplerNativeDescriptorEncodingPlan.FieldWrite> fieldWrites,
            String writeTransactionStatus,
            boolean nativeWriteEnabled,
            boolean sdkStructByteEncodingEnabled,
            String firstBlocker
    ) {
        public DescriptorWrite {
            descriptorKind = normalizeDescriptorKind(descriptorKind);
            targetStruct = normalize(targetStruct, structFor(descriptorKind));
            cleanupOrder = Math.max(0, cleanupOrder);
            rollbackOrder = Math.max(0, rollbackOrder);
            fieldWrites = fieldWrites == null ? List.of() : List.copyOf(fieldWrites);
            writeTransactionStatus = normalize(writeTransactionStatus, "blocked");
            firstBlocker = normalize(firstBlocker, "none");
        }

        public int fieldWriteCount() {
            return fieldWrites.size();
        }

        public long nativeWriteEnabledCount() {
            return fieldWrites.stream().filter(CudaImageSamplerNativeDescriptorEncodingPlan.FieldWrite::nativeWriteEnabled).count();
        }

        public long sdkStructByteEncodingEnabledCount() {
            return fieldWrites.stream()
                    .filter(CudaImageSamplerNativeDescriptorEncodingPlan.FieldWrite::sdkStructByteEncodingEnabled)
                    .count();
        }

        public boolean ready() {
            return "ready".equals(writeTransactionStatus)
                    && "none".equals(firstBlocker)
                    && ownerPresent
                    && ownerActive
                    && nativeAddressPresent
                    && allocationEnabled
                    && nativeWriteEnabled
                    && sdkStructByteEncodingEnabled
                    && fieldWriteCount() > 0
                    && nativeWriteEnabledCount() == fieldWriteCount()
                    && sdkStructByteEncodingEnabledCount() == fieldWriteCount();
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.descriptorWrite"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".descriptor.kind", descriptorKind);
            fields.put(normalizedPrefix + ".targetStruct", targetStruct);
            fields.put(normalizedPrefix + ".owner.present", Boolean.toString(ownerPresent));
            fields.put(normalizedPrefix + ".owner.active", Boolean.toString(ownerActive));
            fields.put(normalizedPrefix + ".nativeAddress.present", Boolean.toString(nativeAddressPresent));
            fields.put(normalizedPrefix + ".allocation.enabled", Boolean.toString(allocationEnabled));
            fields.put(normalizedPrefix + ".cleanup.order", Integer.toString(cleanupOrder));
            fields.put(normalizedPrefix + ".rollback.order", Integer.toString(rollbackOrder));
            fields.put(normalizedPrefix + ".fieldWrite.count", Integer.toString(fieldWrites.size()));
            fields.put(normalizedPrefix + ".nativeWrite.enabled", Boolean.toString(nativeWriteEnabled));
            fields.put(normalizedPrefix + ".nativeWrite.enabled.count", Long.toString(nativeWriteEnabledCount()));
            fields.put(normalizedPrefix + ".sdkStructByteEncoding.enabled", Boolean.toString(sdkStructByteEncodingEnabled));
            fields.put(normalizedPrefix + ".sdkStructByteEncoding.enabled.count", Long.toString(sdkStructByteEncodingEnabledCount()));
            fields.put(normalizedPrefix + ".writeTransaction.status", writeTransactionStatus);
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
            for (int index = 0; index < fieldWrites.size(); index++) {
                fields.putAll(fieldWrites.get(index).artifactFields(normalizedPrefix + ".fieldWrite." + index));
            }
            return Collections.unmodifiableMap(fields);
        }
    }

    public record Entry(
            int parameterIndex,
            String parameterName,
            String javaType,
            String abiKey,
            String cudaAbiRole,
            List<DescriptorWrite> descriptorWrites,
            String encodingTransactionStatus,
            String firstBlocker
    ) {
        public Entry {
            parameterIndex = Math.max(0, parameterIndex);
            parameterName = normalize(parameterName, "unknown");
            javaType = normalize(javaType, "unknown");
            abiKey = normalize(abiKey, "unknown");
            cudaAbiRole = normalize(cudaAbiRole, "unknown");
            descriptorWrites = descriptorWrites == null ? List.of() : List.copyOf(descriptorWrites);
            encodingTransactionStatus = normalize(encodingTransactionStatus, "blocked");
            firstBlocker = normalize(firstBlocker, "none");
        }

        public long resourceDescriptorWriteCount() {
            return descriptorWrites.stream().filter(write -> "resource".equals(write.descriptorKind())).count();
        }

        public long textureDescriptorWriteCount() {
            return descriptorWrites.stream().filter(write -> "texture".equals(write.descriptorKind())).count();
        }

        public long resourceFieldWriteCount() {
            return descriptorWrites.stream()
                    .filter(write -> "resource".equals(write.descriptorKind()))
                    .mapToLong(DescriptorWrite::fieldWriteCount)
                    .sum();
        }

        public long textureFieldWriteCount() {
            return descriptorWrites.stream()
                    .filter(write -> "texture".equals(write.descriptorKind()))
                    .mapToLong(DescriptorWrite::fieldWriteCount)
                    .sum();
        }

        public long fieldWriteCount() {
            return descriptorWrites.stream().mapToLong(DescriptorWrite::fieldWriteCount).sum();
        }

        public long ownerPresentCount() {
            return descriptorWrites.stream().filter(DescriptorWrite::ownerPresent).count();
        }

        public long ownerActiveCount() {
            return descriptorWrites.stream().filter(DescriptorWrite::ownerActive).count();
        }

        public long nativeAddressPresentCount() {
            return descriptorWrites.stream().filter(DescriptorWrite::nativeAddressPresent).count();
        }

        public long nativeWriteEnabledCount() {
            return descriptorWrites.stream().mapToLong(DescriptorWrite::nativeWriteEnabledCount).sum();
        }

        public long sdkStructByteEncodingEnabledCount() {
            return descriptorWrites.stream().mapToLong(DescriptorWrite::sdkStructByteEncodingEnabledCount).sum();
        }

        public boolean ready() {
            return "ready".equals(encodingTransactionStatus)
                    && "none".equals(firstBlocker)
                    && !descriptorWrites.isEmpty()
                    && descriptorWrites.stream().allMatch(DescriptorWrite::ready);
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.entry"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".parameter.index", Integer.toString(parameterIndex));
            fields.put(normalizedPrefix + ".parameter.name", parameterName);
            fields.put(normalizedPrefix + ".parameter.javaType", javaType);
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".abi.key", abiKey);
            fields.put(normalizedPrefix + ".abi.role", cudaAbiRole);
            fields.put(normalizedPrefix + ".descriptorWrite.count", Integer.toString(descriptorWrites.size()));
            fields.put(normalizedPrefix + ".resourceDescriptorWrite.count", Long.toString(resourceDescriptorWriteCount()));
            fields.put(normalizedPrefix + ".textureDescriptorWrite.count", Long.toString(textureDescriptorWriteCount()));
            fields.put(normalizedPrefix + ".resourceFieldWrite.count", Long.toString(resourceFieldWriteCount()));
            fields.put(normalizedPrefix + ".textureFieldWrite.count", Long.toString(textureFieldWriteCount()));
            fields.put(normalizedPrefix + ".fieldWrite.count", Long.toString(fieldWriteCount()));
            fields.put(normalizedPrefix + ".owner.present.count", Long.toString(ownerPresentCount()));
            fields.put(normalizedPrefix + ".owner.active.count", Long.toString(ownerActiveCount()));
            fields.put(normalizedPrefix + ".nativeAddress.present.count", Long.toString(nativeAddressPresentCount()));
            fields.put(normalizedPrefix + ".nativeWrite.enabled.count", Long.toString(nativeWriteEnabledCount()));
            fields.put(normalizedPrefix + ".sdkStructByteEncoding.enabled.count", Long.toString(sdkStructByteEncodingEnabledCount()));
            fields.put(normalizedPrefix + ".encodingTransaction.status", encodingTransactionStatus);
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
            for (int index = 0; index < descriptorWrites.size(); index++) {
                fields.putAll(descriptorWrites.get(index).artifactFields(normalizedPrefix + ".descriptorWrite." + index));
            }
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerNativeDescriptorEncodingTransactionPlan {
        entries = entries == null ? List.of() : List.copyOf(entries);
        nativeDescriptorEncodingPlanStatus = normalize(nativeDescriptorEncodingPlanStatus, "not-present");
        nativeDescriptorAllocationTransactionPlanStatus = normalize(nativeDescriptorAllocationTransactionPlanStatus, "not-present");
        activeNativeDescriptorCount = Math.max(0, activeNativeDescriptorCount);
    }

    static CudaImageSamplerNativeDescriptorEncodingTransactionPlan empty() {
        return new CudaImageSamplerNativeDescriptorEncodingTransactionPlan(
                List.of(),
                "not-present",
                false,
                "not-present",
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

    static CudaImageSamplerNativeDescriptorEncodingTransactionPlan from(
            CudaImageSamplerNativeDescriptorEncodingPlan encodingPlan,
            CudaImageSamplerNativeDescriptorAllocationTransactionPlan allocationTransactionPlan
    ) {
        if (encodingPlan == null || !encodingPlan.present()) {
            return empty();
        }
        CudaImageSamplerNativeDescriptorAllocationTransactionPlan allocationPlan = allocationTransactionPlan == null
                ? CudaImageSamplerNativeDescriptorAllocationTransactionPlan.empty()
                : allocationTransactionPlan;
        return new CudaImageSamplerNativeDescriptorEncodingTransactionPlan(
                entries(encodingPlan.entries(), allocationPlan.entries()),
                encodingPlan.status(),
                encodingPlan.present(),
                allocationPlan.status(),
                allocationPlan.present(),
                true,
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
                && "ready".equals(nativeDescriptorEncodingPlanStatus)
                && nativeDescriptorEncodingPlanPresent
                && "ready".equals(nativeDescriptorAllocationTransactionPlanStatus)
                && nativeDescriptorAllocationTransactionPlanPresent
                && javaEncodingTransactionPlanEnabled
                && writeTransactionApplyEnabled
                && nativeMemoryAllocationEnabled
                && sdkStructByteEncodingEnabled
                && !objectCreationEnabled
                && !runtimeBindingEnabled
                && activeNativeDescriptorCount == ownerActiveCount()
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

    public long descriptorWriteCount() {
        return entries.stream().mapToLong(entry -> entry.descriptorWrites().size()).sum();
    }

    public long resourceDescriptorWriteCount() {
        return entries.stream().mapToLong(Entry::resourceDescriptorWriteCount).sum();
    }

    public long textureDescriptorWriteCount() {
        return entries.stream().mapToLong(Entry::textureDescriptorWriteCount).sum();
    }

    public long resourceFieldWriteCount() {
        return entries.stream().mapToLong(Entry::resourceFieldWriteCount).sum();
    }

    public long textureFieldWriteCount() {
        return entries.stream().mapToLong(Entry::textureFieldWriteCount).sum();
    }

    public long fieldWriteCount() {
        return resourceFieldWriteCount() + textureFieldWriteCount();
    }

    public long ownerPresentCount() {
        return entries.stream().mapToLong(Entry::ownerPresentCount).sum();
    }

    public long ownerActiveCount() {
        return entries.stream().mapToLong(Entry::ownerActiveCount).sum();
    }

    public long nativeAddressPresentCount() {
        return entries.stream().mapToLong(Entry::nativeAddressPresentCount).sum();
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
                .orElseGet(() -> ready() ? "none" : "cuda-image-sampler-native-descriptor-encoding-transaction-not-ready");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan");
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", Boolean.toString(present()));
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".ready", Boolean.toString(ready()));
        fields.put(prefix + ".nativeDescriptorEncodingPlan.status", nativeDescriptorEncodingPlanStatus);
        fields.put(prefix + ".nativeDescriptorEncodingPlan.present", Boolean.toString(nativeDescriptorEncodingPlanPresent));
        fields.put(prefix + ".nativeDescriptorAllocationTransactionPlan.status", nativeDescriptorAllocationTransactionPlanStatus);
        fields.put(prefix + ".nativeDescriptorAllocationTransactionPlan.present", Boolean.toString(nativeDescriptorAllocationTransactionPlanPresent));
        fields.put(prefix + ".javaEncodingTransactionPlan.enabled", Boolean.toString(javaEncodingTransactionPlanEnabled));
        fields.put(prefix + ".writeTransactionApply.enabled", Boolean.toString(writeTransactionApplyEnabled));
        fields.put(prefix + ".nativeMemoryAllocation.enabled", Boolean.toString(nativeMemoryAllocationEnabled));
        fields.put(prefix + ".sdkStructByteEncoding.enabled", Boolean.toString(sdkStructByteEncodingEnabled));
        fields.put(prefix + ".objectCreation.enabled", Boolean.toString(objectCreationEnabled));
        fields.put(prefix + ".runtimeBinding.enabled", Boolean.toString(runtimeBindingEnabled));
        fields.put(prefix + ".activeNativeDescriptor.count", Integer.toString(activeNativeDescriptorCount));
        fields.put(prefix + ".entry.count", Integer.toString(entries.size()));
        fields.put(prefix + ".entry.ready.count", Long.toString(entryReadyCount()));
        fields.put(prefix + ".entry.blocked.count", Long.toString(entryBlockedCount()));
        fields.put(prefix + ".descriptorWrite.count", Long.toString(descriptorWriteCount()));
        fields.put(prefix + ".resourceDescriptorWrite.count", Long.toString(resourceDescriptorWriteCount()));
        fields.put(prefix + ".textureDescriptorWrite.count", Long.toString(textureDescriptorWriteCount()));
        fields.put(prefix + ".resourceFieldWrite.count", Long.toString(resourceFieldWriteCount()));
        fields.put(prefix + ".textureFieldWrite.count", Long.toString(textureFieldWriteCount()));
        fields.put(prefix + ".fieldWrite.count", Long.toString(fieldWriteCount()));
        fields.put(prefix + ".owner.present.count", Long.toString(ownerPresentCount()));
        fields.put(prefix + ".owner.active.count", Long.toString(ownerActiveCount()));
        fields.put(prefix + ".nativeAddress.present.count", Long.toString(nativeAddressPresentCount()));
        fields.put(prefix + ".nativeWrite.enabled.count", Long.toString(nativeWriteEnabledCount()));
        fields.put(prefix + ".sdkStructByteEncoding.enabled.count", Long.toString(sdkStructByteEncodingEnabledCount()));
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < entries.size(); index++) {
            fields.putAll(entries.get(index).artifactFields(prefix + ".entry." + index));
        }
    }

    private static List<Entry> entries(
            List<CudaImageSamplerNativeDescriptorEncodingPlan.Entry> encodingEntries,
            List<CudaImageSamplerNativeDescriptorAllocationTransactionPlan.Entry> allocationEntries
    ) {
        ArrayList<Entry> entries = new ArrayList<>();
        for (CudaImageSamplerNativeDescriptorEncodingPlan.Entry encodingEntry : encodingEntries) {
            entries.add(entry(encodingEntry, allocationEntryFor(allocationEntries, encodingEntry)));
        }
        return List.copyOf(entries);
    }

    private static Entry entry(
            CudaImageSamplerNativeDescriptorEncodingPlan.Entry encodingEntry,
            CudaImageSamplerNativeDescriptorAllocationTransactionPlan.Entry allocationEntry
    ) {
        if (encodingEntry == null) {
            return new Entry(
                    0,
                    "unknown",
                    "unknown",
                    "unknown",
                    "unknown",
                    List.of(),
                    "blocked",
                    "cuda-image-sampler-native-descriptor-encoding-plan-entry-missing"
            );
        }
        if (!encodingEntry.ready()) {
            return new Entry(
                    encodingEntry.parameterIndex(),
                    encodingEntry.parameterName(),
                    encodingEntry.javaType(),
                    encodingEntry.abiKey(),
                    encodingEntry.cudaAbiRole(),
                    List.of(),
                    "blocked",
                    encodingEntry.firstBlocker()
            );
        }

        ArrayList<DescriptorWrite> writes = new ArrayList<>();
        if (encodingEntry.resourceEncodingRequired()) {
            writes.add(descriptorWrite(encodingEntry, allocationEntry, "resource", encodingEntry.resourceFieldWrites()));
        }
        if (encodingEntry.textureEncodingRequired()) {
            writes.add(descriptorWrite(encodingEntry, allocationEntry, "texture", encodingEntry.textureFieldWrites()));
        }
        String firstBlocker = writes.stream()
                .map(DescriptorWrite::firstBlocker)
                .filter(blocker -> !"none".equals(blocker))
                .findFirst()
                .orElse("none");
        return new Entry(
                encodingEntry.parameterIndex(),
                encodingEntry.parameterName(),
                encodingEntry.javaType(),
                encodingEntry.abiKey(),
                encodingEntry.cudaAbiRole(),
                writes,
                writes.isEmpty() ? "not-required" : "blocked",
                firstBlocker
        );
    }

    private static DescriptorWrite descriptorWrite(
            CudaImageSamplerNativeDescriptorEncodingPlan.Entry encodingEntry,
            CudaImageSamplerNativeDescriptorAllocationTransactionPlan.Entry allocationEntry,
            String descriptorKind,
            List<CudaImageSamplerNativeDescriptorEncodingPlan.FieldWrite> fieldWrites
    ) {
        CudaDriverImageSamplerNativeDescriptor owner = ownerFor(allocationEntry, descriptorKind);
        boolean ownerPresent = owner != null;
        String firstBlocker = ownerPresent
                ? "cuda-image-sampler-native-descriptor-encoding-transaction-disabled:"
                + encodingEntry.parameterIndex()
                + ':'
                + encodingEntry.javaType()
                : "cuda-image-sampler-native-descriptor-owner-missing:"
                + encodingEntry.parameterIndex()
                + ':'
                + encodingEntry.javaType()
                + ':'
                + descriptorKind;
        return new DescriptorWrite(
                descriptorKind,
                ownerPresent ? owner.structName() : structFor(descriptorKind),
                ownerPresent,
                ownerPresent && owner.active(),
                ownerPresent && owner.nativeAddressPresent(),
                ownerPresent && owner.allocationEnabled(),
                ownerPresent ? owner.cleanupOrder() : 0,
                ownerPresent ? owner.rollbackOrder() : 0,
                fieldWrites,
                "blocked",
                false,
                false,
                firstBlocker
        );
    }

    private static CudaImageSamplerNativeDescriptorAllocationTransactionPlan.Entry allocationEntryFor(
            List<CudaImageSamplerNativeDescriptorAllocationTransactionPlan.Entry> allocationEntries,
            CudaImageSamplerNativeDescriptorEncodingPlan.Entry encodingEntry
    ) {
        if (allocationEntries == null || encodingEntry == null) {
            return null;
        }
        return allocationEntries.stream()
                .filter(entry -> entry.parameterIndex() == encodingEntry.parameterIndex())
                .filter(entry -> entry.javaType().equals(encodingEntry.javaType()))
                .filter(entry -> entry.abiKey().equals(encodingEntry.abiKey()))
                .findFirst()
                .orElse(null);
    }

    private static CudaDriverImageSamplerNativeDescriptor ownerFor(
            CudaImageSamplerNativeDescriptorAllocationTransactionPlan.Entry allocationEntry,
            String descriptorKind
    ) {
        if (allocationEntry == null) {
            return null;
        }
        return allocationEntry.descriptorOwners().stream()
                .filter(owner -> descriptorKind.equals(owner.descriptorKind()))
                .findFirst()
                .orElse(null);
    }

    private static String normalizeDescriptorKind(String descriptorKind) {
        if ("texture".equals(descriptorKind)) {
            return "texture";
        }
        return "resource";
    }

    private static String structFor(String descriptorKind) {
        return "texture".equals(descriptorKind) ? "CUDA_TEXTURE_DESC" : "CUDA_RESOURCE_DESC";
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
