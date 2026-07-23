package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fail-closed preflight for the future CUDA texture/surface object kernel-argument binding transaction.
 *
 * <p>The preflight records which planned object bindings would become real kernel parameter writes after native
 * descriptors, object creation, object ownership, and runtime binding are enabled. It deliberately reports those
 * prerequisites as unavailable today and does not write kernel parameter slots.</p>
 */
public record CudaImageSamplerRuntimeObjectBindingTransactionPreflight(
        List<Entry> entries,
        String runtimeObjectBindingPlanStatus,
        boolean runtimeObjectBindingPlanReady,
        String nativeObjectPreparationPreflightStatus,
        boolean nativeObjectPreparationPreflightPresent,
        boolean javaTransactionPreflightEnabled,
        boolean nativeDescriptorAllocationEnabled,
        boolean objectCreationCallEnabled,
        boolean objectOwnershipEnabled,
        boolean transactionApplyEnabled,
        boolean kernelParameterWriteEnabled,
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
            boolean foldedSamplerBinding,
            int plannedObjectKernelParameterSlotCount,
            int plannedMetadataKernelParameterSlotCount,
            int plannedKernelParameterSlotCount,
            boolean nativeDescriptorRequired,
            boolean nativeDescriptorAvailable,
            boolean resourceDescriptorRequired,
            boolean resourceDescriptorOwnerPresent,
            boolean resourceDescriptorNativeAddressPresent,
            boolean resourceDescriptorWritePlanned,
            boolean resourceDescriptorNativeWriteEnabled,
            boolean textureDescriptorRequired,
            boolean textureDescriptorOwnerPresent,
            boolean textureDescriptorNativeAddressPresent,
            boolean textureDescriptorWritePlanned,
            boolean textureDescriptorNativeWriteEnabled,
            boolean objectHandleRequired,
            boolean objectHandleAvailable,
            boolean objectOwnershipRequired,
            boolean objectOwnershipAvailable,
            boolean kernelParameterWriteRequired,
            boolean kernelParameterWriteEnabled,
            String transactionStatus,
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
            transactionStatus = normalize(transactionStatus, "blocked");
            firstBlocker = normalize(firstBlocker, "none");
        }

        public boolean textureObjectTransaction() {
            return objectBindingRequired && "texture".equals(objectKind);
        }

        public boolean surfaceObjectTransaction() {
            return objectBindingRequired && "surface".equals(objectKind);
        }

        public boolean ready() {
            return "ready".equals(transactionStatus)
                    && "none".equals(firstBlocker)
                    && (!objectBindingRequired || (nativeDescriptorRequired
                    && nativeDescriptorAvailable
                    && (!resourceDescriptorRequired || (resourceDescriptorOwnerPresent
                    && resourceDescriptorNativeAddressPresent
                    && resourceDescriptorWritePlanned
                    && resourceDescriptorNativeWriteEnabled))
                    && (!textureDescriptorRequired || (textureDescriptorOwnerPresent
                    && textureDescriptorNativeAddressPresent
                    && textureDescriptorWritePlanned
                    && textureDescriptorNativeWriteEnabled))
                    && objectHandleRequired
                    && objectHandleAvailable
                    && objectOwnershipRequired
                    && objectOwnershipAvailable
                    && kernelParameterWriteRequired
                    && kernelParameterWriteEnabled));
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry"
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
            fields.put(normalizedPrefix + ".foldedSamplerBinding", Boolean.toString(foldedSamplerBinding));
            fields.put(normalizedPrefix + ".plannedObjectKernelParameterSlot.count", Integer.toString(plannedObjectKernelParameterSlotCount));
            fields.put(normalizedPrefix + ".plannedMetadataKernelParameterSlot.count", Integer.toString(plannedMetadataKernelParameterSlotCount));
            fields.put(normalizedPrefix + ".plannedKernelParameterSlot.count", Integer.toString(plannedKernelParameterSlotCount));
            fields.put(normalizedPrefix + ".nativeDescriptor.required", Boolean.toString(nativeDescriptorRequired));
            fields.put(normalizedPrefix + ".nativeDescriptor.available", Boolean.toString(nativeDescriptorAvailable));
            fields.put(normalizedPrefix + ".resourceDescriptor.required", Boolean.toString(resourceDescriptorRequired));
            fields.put(normalizedPrefix + ".resourceDescriptorOwner.present", Boolean.toString(resourceDescriptorOwnerPresent));
            fields.put(normalizedPrefix + ".resourceDescriptor.nativeAddress.present", Boolean.toString(resourceDescriptorNativeAddressPresent));
            fields.put(normalizedPrefix + ".resourceDescriptorWrite.planned", Boolean.toString(resourceDescriptorWritePlanned));
            fields.put(normalizedPrefix + ".resourceDescriptorNativeWrite.enabled", Boolean.toString(resourceDescriptorNativeWriteEnabled));
            fields.put(normalizedPrefix + ".textureDescriptor.required", Boolean.toString(textureDescriptorRequired));
            fields.put(normalizedPrefix + ".textureDescriptorOwner.present", Boolean.toString(textureDescriptorOwnerPresent));
            fields.put(normalizedPrefix + ".textureDescriptor.nativeAddress.present", Boolean.toString(textureDescriptorNativeAddressPresent));
            fields.put(normalizedPrefix + ".textureDescriptorWrite.planned", Boolean.toString(textureDescriptorWritePlanned));
            fields.put(normalizedPrefix + ".textureDescriptorNativeWrite.enabled", Boolean.toString(textureDescriptorNativeWriteEnabled));
            fields.put(normalizedPrefix + ".objectHandle.required", Boolean.toString(objectHandleRequired));
            fields.put(normalizedPrefix + ".objectHandle.available", Boolean.toString(objectHandleAvailable));
            fields.put(normalizedPrefix + ".objectOwnership.required", Boolean.toString(objectOwnershipRequired));
            fields.put(normalizedPrefix + ".objectOwnership.available", Boolean.toString(objectOwnershipAvailable));
            fields.put(normalizedPrefix + ".kernelParameterWrite.required", Boolean.toString(kernelParameterWriteRequired));
            fields.put(normalizedPrefix + ".kernelParameterWrite.enabled", Boolean.toString(kernelParameterWriteEnabled));
            fields.put(normalizedPrefix + ".transaction.status", transactionStatus);
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerRuntimeObjectBindingTransactionPreflight {
        entries = entries == null ? List.of() : List.copyOf(entries);
        runtimeObjectBindingPlanStatus = normalize(runtimeObjectBindingPlanStatus, "not-present");
        nativeObjectPreparationPreflightStatus = normalize(nativeObjectPreparationPreflightStatus, "not-present");
        activeObjectCount = Math.max(0, activeObjectCount);
    }

    static CudaImageSamplerRuntimeObjectBindingTransactionPreflight empty() {
        return new CudaImageSamplerRuntimeObjectBindingTransactionPreflight(
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

    static CudaImageSamplerRuntimeObjectBindingTransactionPreflight from(CudaImageSamplerRuntimeObjectBindingPlan plan) {
        return from(plan, CudaImageSamplerNativeObjectPreparationPreflight.empty());
    }

    static CudaImageSamplerRuntimeObjectBindingTransactionPreflight from(
            CudaImageSamplerRuntimeObjectBindingPlan plan,
            CudaImageSamplerNativeObjectPreparationPreflight nativeObjectPreparationPreflight
    ) {
        if (plan == null || !plan.present()) {
            return empty();
        }
        CudaImageSamplerNativeObjectPreparationPreflight objectPreflight = nativeObjectPreparationPreflight == null
                ? CudaImageSamplerNativeObjectPreparationPreflight.empty()
                : nativeObjectPreparationPreflight;
        return new CudaImageSamplerRuntimeObjectBindingTransactionPreflight(
                plan.entries().stream()
                        .map(entry -> CudaImageSamplerRuntimeObjectBindingTransactionPreflight.entry(entry, objectPreflight.entries()))
                        .toList(),
                plan.status(),
                plan.ready(),
                objectPreflight.status(),
                objectPreflight.present(),
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
                && "ready".equals(runtimeObjectBindingPlanStatus)
                && runtimeObjectBindingPlanReady
                && "ready".equals(nativeObjectPreparationPreflightStatus)
                && nativeObjectPreparationPreflightPresent
                && javaTransactionPreflightEnabled
                && nativeDescriptorAllocationEnabled
                && objectCreationCallEnabled
                && objectOwnershipEnabled
                && transactionApplyEnabled
                && kernelParameterWriteEnabled
                && activeObjectCount > 0;
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

    public long objectBindingTransactionCount() {
        return entries.stream().filter(Entry::objectBindingRequired).count();
    }

    public long textureObjectTransactionCount() {
        return entries.stream().filter(Entry::textureObjectTransaction).count();
    }

    public long surfaceObjectTransactionCount() {
        return entries.stream().filter(Entry::surfaceObjectTransaction).count();
    }

    public long foldedSamplerTransactionCount() {
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

    public long objectHandleRequiredCount() {
        return entries.stream().filter(Entry::objectHandleRequired).count();
    }

    public long objectHandleAvailableCount() {
        return entries.stream().filter(Entry::objectHandleAvailable).count();
    }

    public long nativeDescriptorAvailableCount() {
        return entries.stream().filter(Entry::nativeDescriptorAvailable).count();
    }

    public long resourceDescriptorRequiredCount() {
        return entries.stream().filter(Entry::resourceDescriptorRequired).count();
    }

    public long resourceDescriptorOwnerPresentCount() {
        return entries.stream().filter(Entry::resourceDescriptorOwnerPresent).count();
    }

    public long resourceDescriptorNativeAddressPresentCount() {
        return entries.stream().filter(Entry::resourceDescriptorNativeAddressPresent).count();
    }

    public long resourceDescriptorWritePlannedCount() {
        return entries.stream().filter(Entry::resourceDescriptorWritePlanned).count();
    }

    public long resourceDescriptorNativeWriteEnabledCount() {
        return entries.stream().filter(Entry::resourceDescriptorNativeWriteEnabled).count();
    }

    public long textureDescriptorRequiredCount() {
        return entries.stream().filter(Entry::textureDescriptorRequired).count();
    }

    public long textureDescriptorOwnerPresentCount() {
        return entries.stream().filter(Entry::textureDescriptorOwnerPresent).count();
    }

    public long textureDescriptorNativeAddressPresentCount() {
        return entries.stream().filter(Entry::textureDescriptorNativeAddressPresent).count();
    }

    public long textureDescriptorWritePlannedCount() {
        return entries.stream().filter(Entry::textureDescriptorWritePlanned).count();
    }

    public long textureDescriptorNativeWriteEnabledCount() {
        return entries.stream().filter(Entry::textureDescriptorNativeWriteEnabled).count();
    }

    public long transactionApplyEnabledCount() {
        return transactionApplyEnabled ? entries.stream().filter(Entry::ready).count() : 0;
    }

    public long kernelParameterWriteEnabledCount() {
        return entries.stream().filter(Entry::kernelParameterWriteEnabled).count();
    }

    public String firstBlocker() {
        if (!present()) {
            return "none";
        }
        return entries.stream()
                .map(Entry::firstBlocker)
                .filter(blocker -> !"none".equals(blocker))
                .findFirst()
                .orElseGet(() -> ready() ? "none" : "cuda-image-sampler-runtime-object-binding-transaction-not-ready");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight");
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", Boolean.toString(present()));
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".ready", Boolean.toString(ready()));
        fields.put(prefix + ".runtimeObjectBindingPlan.status", runtimeObjectBindingPlanStatus);
        fields.put(prefix + ".runtimeObjectBindingPlan.ready", Boolean.toString(runtimeObjectBindingPlanReady));
        fields.put(prefix + ".nativeObjectPreparationPreflight.status", nativeObjectPreparationPreflightStatus);
        fields.put(prefix + ".nativeObjectPreparationPreflight.present", Boolean.toString(nativeObjectPreparationPreflightPresent));
        fields.put(prefix + ".javaTransactionPreflight.enabled", Boolean.toString(javaTransactionPreflightEnabled));
        fields.put(prefix + ".nativeDescriptorAllocation.enabled", Boolean.toString(nativeDescriptorAllocationEnabled));
        fields.put(prefix + ".objectCreationCall.enabled", Boolean.toString(objectCreationCallEnabled));
        fields.put(prefix + ".objectOwnership.enabled", Boolean.toString(objectOwnershipEnabled));
        fields.put(prefix + ".transactionApply.enabled", Boolean.toString(transactionApplyEnabled));
        fields.put(prefix + ".kernelParameterWrite.enabled", Boolean.toString(kernelParameterWriteEnabled));
        fields.put(prefix + ".activeObject.count", Integer.toString(activeObjectCount));
        fields.put(prefix + ".entry.count", Integer.toString(entries.size()));
        fields.put(prefix + ".entry.ready.count", Long.toString(entryReadyCount()));
        fields.put(prefix + ".entry.blocked.count", Long.toString(entryBlockedCount()));
        fields.put(prefix + ".objectBindingTransaction.count", Long.toString(objectBindingTransactionCount()));
        fields.put(prefix + ".textureObjectTransaction.count", Long.toString(textureObjectTransactionCount()));
        fields.put(prefix + ".surfaceObjectTransaction.count", Long.toString(surfaceObjectTransactionCount()));
        fields.put(prefix + ".foldedSamplerTransaction.count", Long.toString(foldedSamplerTransactionCount()));
        fields.put(prefix + ".plannedObjectKernelParameterSlot.count", Long.toString(plannedObjectKernelParameterSlotCount()));
        fields.put(prefix + ".plannedMetadataKernelParameterSlot.count", Long.toString(plannedMetadataKernelParameterSlotCount()));
        fields.put(prefix + ".plannedKernelParameterSlot.count", Long.toString(plannedKernelParameterSlotCount()));
        fields.put(prefix + ".objectHandle.required.count", Long.toString(objectHandleRequiredCount()));
        fields.put(prefix + ".objectHandle.available.count", Long.toString(objectHandleAvailableCount()));
        fields.put(prefix + ".nativeDescriptor.available.count", Long.toString(nativeDescriptorAvailableCount()));
        fields.put(prefix + ".resourceDescriptor.required.count", Long.toString(resourceDescriptorRequiredCount()));
        fields.put(prefix + ".resourceDescriptorOwner.present.count", Long.toString(resourceDescriptorOwnerPresentCount()));
        fields.put(prefix + ".resourceDescriptor.nativeAddress.present.count", Long.toString(resourceDescriptorNativeAddressPresentCount()));
        fields.put(prefix + ".resourceDescriptorWrite.planned.count", Long.toString(resourceDescriptorWritePlannedCount()));
        fields.put(prefix + ".resourceDescriptorNativeWrite.enabled.count", Long.toString(resourceDescriptorNativeWriteEnabledCount()));
        fields.put(prefix + ".textureDescriptor.required.count", Long.toString(textureDescriptorRequiredCount()));
        fields.put(prefix + ".textureDescriptorOwner.present.count", Long.toString(textureDescriptorOwnerPresentCount()));
        fields.put(prefix + ".textureDescriptor.nativeAddress.present.count", Long.toString(textureDescriptorNativeAddressPresentCount()));
        fields.put(prefix + ".textureDescriptorWrite.planned.count", Long.toString(textureDescriptorWritePlannedCount()));
        fields.put(prefix + ".textureDescriptorNativeWrite.enabled.count", Long.toString(textureDescriptorNativeWriteEnabledCount()));
        fields.put(prefix + ".transactionApply.enabled.count", Long.toString(transactionApplyEnabledCount()));
        fields.put(prefix + ".kernelParameterWrite.enabled.count", Long.toString(kernelParameterWriteEnabledCount()));
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < entries.size(); index++) {
            fields.putAll(entries.get(index).artifactFields(prefix + ".entry." + index));
        }
    }

    private static Entry entry(
            CudaImageSamplerRuntimeObjectBindingPlan.Entry planEntry,
            List<CudaImageSamplerNativeObjectPreparationPreflight.Entry> nativeObjectPreparationEntries
    ) {
        if (planEntry == null) {
            return blockedEntry("cuda-image-sampler-runtime-object-binding-plan-entry-missing");
        }
        if (!planEntry.ready()) {
            return new Entry(
                    planEntry.parameterIndex(),
                    planEntry.parameterName(),
                    planEntry.javaType(),
                    planEntry.abiKey(),
                    planEntry.cudaAbiRole(),
                    false,
                    "not-required",
                    "not-required",
                    false,
                    0,
                    0,
                    0,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "blocked",
                    planEntry.firstBlocker()
            );
        }
        boolean objectBinding = planEntry.objectBindingRequired();
        boolean foldedSampler = planEntry.foldedSamplerBinding();
        CudaImageSamplerNativeObjectPreparationPreflight.Entry preparationEntry = preparationEntryFor(
                nativeObjectPreparationEntries,
                planEntry
        );
        boolean resourceRequired = objectBinding && preparationEntry != null && preparationEntry.resourceDescriptorRequired();
        boolean resourceOwnerPresent = resourceRequired && preparationEntry.resourceDescriptorOwnerPresent();
        boolean resourceNativeAddressPresent = resourceRequired && preparationEntry.resourceDescriptorNativeAddressPresent();
        boolean resourceWritePlanned = resourceRequired && preparationEntry.resourceDescriptorWritePlanned();
        boolean resourceNativeWriteEnabled = resourceRequired && preparationEntry.resourceDescriptorNativeWriteEnabled();
        boolean textureRequired = objectBinding && preparationEntry != null && preparationEntry.textureDescriptorRequired();
        boolean textureOwnerPresent = textureRequired && preparationEntry.textureDescriptorOwnerPresent();
        boolean textureNativeAddressPresent = textureRequired && preparationEntry.textureDescriptorNativeAddressPresent();
        boolean textureWritePlanned = textureRequired && preparationEntry.textureDescriptorWritePlanned();
        boolean textureNativeWriteEnabled = textureRequired && preparationEntry.textureDescriptorNativeWriteEnabled();
        boolean nativeDescriptorAvailable = objectBinding
                && (!resourceRequired || (resourceOwnerPresent
                && resourceNativeAddressPresent
                && resourceWritePlanned
                && resourceNativeWriteEnabled))
                && (!textureRequired || (textureOwnerPresent
                && textureNativeAddressPresent
                && textureWritePlanned
                && textureNativeWriteEnabled));
        return new Entry(
                planEntry.parameterIndex(),
                planEntry.parameterName(),
                planEntry.javaType(),
                planEntry.abiKey(),
                planEntry.cudaAbiRole(),
                objectBinding,
                planEntry.objectKind(),
                planEntry.parameterCarrier(),
                foldedSampler,
                planEntry.plannedObjectKernelParameterSlotCount(),
                planEntry.plannedMetadataKernelParameterSlotCount(),
                planEntry.plannedKernelParameterSlotCount(),
                objectBinding,
                nativeDescriptorAvailable,
                resourceRequired,
                resourceOwnerPresent,
                resourceNativeAddressPresent,
                resourceWritePlanned,
                resourceNativeWriteEnabled,
                textureRequired,
                textureOwnerPresent,
                textureNativeAddressPresent,
                textureWritePlanned,
                textureNativeWriteEnabled,
                objectBinding,
                false,
                objectBinding,
                false,
                objectBinding,
                false,
                objectBinding ? "blocked" : "ready",
                objectBinding
                        ? firstRuntimeBindingBlocker(planEntry, resourceRequired, resourceOwnerPresent, resourceNativeAddressPresent, resourceWritePlanned, resourceNativeWriteEnabled, textureRequired, textureOwnerPresent, textureNativeAddressPresent, textureWritePlanned, textureNativeWriteEnabled)
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
                "not-required",
                "not-required",
                false,
                0,
                0,
                0,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                "blocked",
                firstBlocker
        );
    }

    private static CudaImageSamplerNativeObjectPreparationPreflight.Entry preparationEntryFor(
            List<CudaImageSamplerNativeObjectPreparationPreflight.Entry> entries,
            CudaImageSamplerRuntimeObjectBindingPlan.Entry planEntry
    ) {
        if (entries == null || planEntry == null) {
            return null;
        }
        return entries.stream()
                .filter(entry -> entry.parameterIndex() == planEntry.parameterIndex())
                .filter(entry -> entry.javaType().equals(planEntry.javaType()))
                .filter(entry -> entry.abiKey().equals(planEntry.abiKey()))
                .findFirst()
                .orElse(null);
    }

    private static String firstRuntimeBindingBlocker(
            CudaImageSamplerRuntimeObjectBindingPlan.Entry planEntry,
            boolean resourceRequired,
            boolean resourceOwnerPresent,
            boolean resourceNativeAddressPresent,
            boolean resourceWritePlanned,
            boolean resourceNativeWriteEnabled,
            boolean textureRequired,
            boolean textureOwnerPresent,
            boolean textureNativeAddressPresent,
            boolean textureWritePlanned,
            boolean textureNativeWriteEnabled
    ) {
        if (resourceRequired) {
            if (!resourceOwnerPresent) {
                return "cuda-image-sampler-runtime-native-resource-descriptor-owner-missing:" + planEntry.parameterIndex() + ':' + planEntry.javaType();
            }
            if (!resourceNativeAddressPresent) {
                return "cuda-image-sampler-runtime-native-resource-descriptor-address-unavailable:" + planEntry.parameterIndex() + ':' + planEntry.javaType();
            }
            if (!resourceWritePlanned) {
                return "cuda-image-sampler-runtime-native-resource-descriptor-write-plan-missing:" + planEntry.parameterIndex() + ':' + planEntry.javaType();
            }
            if (!resourceNativeWriteEnabled) {
                return "cuda-image-sampler-runtime-native-resource-descriptor-write-disabled:" + planEntry.parameterIndex() + ':' + planEntry.javaType();
            }
        }
        if (textureRequired) {
            if (!textureOwnerPresent) {
                return "cuda-image-sampler-runtime-native-texture-descriptor-owner-missing:" + planEntry.parameterIndex() + ':' + planEntry.javaType();
            }
            if (!textureNativeAddressPresent) {
                return "cuda-image-sampler-runtime-native-texture-descriptor-address-unavailable:" + planEntry.parameterIndex() + ':' + planEntry.javaType();
            }
            if (!textureWritePlanned) {
                return "cuda-image-sampler-runtime-native-texture-descriptor-write-plan-missing:" + planEntry.parameterIndex() + ':' + planEntry.javaType();
            }
            if (!textureNativeWriteEnabled) {
                return "cuda-image-sampler-runtime-native-texture-descriptor-write-disabled:" + planEntry.parameterIndex() + ':' + planEntry.javaType();
            }
        }
        return "cuda-image-sampler-runtime-object-handle-unavailable:" + planEntry.parameterIndex() + ':' + planEntry.javaType();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
