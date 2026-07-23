package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fail-closed preflight for future native CUDA texture/surface object preparation.
 *
 * <p>This layer sits above object-creation request planning and below real {@code cuTexObjectCreate} /
 * {@code cuSurfObjectCreate} calls. It records the native descriptor handles and lifecycle prerequisites that must exist
 * before object creation can be attempted. It deliberately allocates no native descriptors and creates no CUDA objects.</p>
 */
public record CudaImageSamplerNativeObjectPreparationPreflight(
        List<Entry> entries,
        String objectCreationRequestPlanStatus,
        boolean objectCreationRequestPlanReady,
        String nativeDescriptorEncodingTransactionPlanStatus,
        boolean nativeDescriptorEncodingTransactionPlanPresent,
        boolean javaNativeObjectPreparationPreflightEnabled,
        boolean nativeDescriptorAllocationEnabled,
        boolean objectCreationCallEnabled,
        boolean objectOwnershipEnabled,
        boolean runtimeBindingEnabled,
        int activeNativeDescriptorCount,
        int activeObjectCount
) {

    public record Entry(
            int parameterIndex,
            String parameterName,
            String javaType,
            String abiKey,
            String cudaAbiRole,
            boolean objectPreparationRequired,
            String objectKind,
            String parameterCarrier,
            boolean foldedSamplerDescriptorState,
            boolean resourceDescriptorRequired,
            boolean resourceDescriptorAvailable,
            boolean resourceDescriptorOwnerPresent,
            boolean resourceDescriptorNativeAddressPresent,
            boolean resourceDescriptorWritePlanned,
            boolean resourceDescriptorNativeWriteEnabled,
            boolean textureDescriptorRequired,
            boolean textureDescriptorAvailable,
            boolean textureDescriptorOwnerPresent,
            boolean textureDescriptorNativeAddressPresent,
            boolean textureDescriptorWritePlanned,
            boolean textureDescriptorNativeWriteEnabled,
            boolean createFunctionRequired,
            boolean createFunctionAvailable,
            boolean destroyFunctionRequired,
            boolean destroyFunctionAvailable,
            boolean objectHandleRequired,
            boolean objectHandleAvailable,
            boolean objectOwnershipRequired,
            boolean objectOwnershipAvailable,
            String nativeObjectPreparationStatus,
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
            objectKind = normalize(objectKind, objectPreparationRequired ? "unknown" : "not-required");
            parameterCarrier = normalize(parameterCarrier, objectPreparationRequired ? "unknown" : "not-required");
            nativeObjectPreparationStatus = normalize(nativeObjectPreparationStatus, "blocked");
            objectCreationCallStatus = normalize(objectCreationCallStatus, "disabled");
            objectOwnershipStatus = normalize(objectOwnershipStatus, "prepared");
            runtimeBindingStatus = normalize(runtimeBindingStatus, "fail-closed");
            firstBlocker = normalize(firstBlocker, "none");
        }

        public boolean textureObjectPreparation() {
            return objectPreparationRequired && "texture".equals(objectKind);
        }

        public boolean surfaceObjectPreparation() {
            return objectPreparationRequired && "surface".equals(objectKind);
        }

        public boolean ready() {
            return "native-object-preparation-ready".equals(nativeObjectPreparationStatus)
                    && "disabled".equals(objectCreationCallStatus)
                    && "prepared".equals(objectOwnershipStatus)
                    && "fail-closed".equals(runtimeBindingStatus)
                    && !productionSupportEnabled
                    && "none".equals(firstBlocker)
                    && (!objectPreparationRequired || (resourceDescriptorRequired
                    && resourceDescriptorAvailable
                    && resourceDescriptorOwnerPresent
                    && resourceDescriptorNativeAddressPresent
                    && resourceDescriptorWritePlanned
                    && resourceDescriptorNativeWriteEnabled
                    && (!textureDescriptorRequired || (textureDescriptorAvailable
                    && textureDescriptorOwnerPresent
                    && textureDescriptorNativeAddressPresent
                    && textureDescriptorWritePlanned
                    && textureDescriptorNativeWriteEnabled))
                    && createFunctionRequired
                    && createFunctionAvailable
                    && destroyFunctionRequired
                    && destroyFunctionAvailable
                    && objectHandleRequired
                    && objectHandleAvailable
                    && objectOwnershipRequired
                    && objectOwnershipAvailable));
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerNativeObjectPreparationPreflight.entry"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".parameter.index", Integer.toString(parameterIndex));
            fields.put(normalizedPrefix + ".parameter.name", parameterName);
            fields.put(normalizedPrefix + ".parameter.javaType", javaType);
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".abi.key", abiKey);
            fields.put(normalizedPrefix + ".abi.role", cudaAbiRole);
            fields.put(normalizedPrefix + ".objectPreparation.required", Boolean.toString(objectPreparationRequired));
            fields.put(normalizedPrefix + ".object.kind", objectKind);
            fields.put(normalizedPrefix + ".parameter.carrier", parameterCarrier);
            fields.put(normalizedPrefix + ".foldedSamplerDescriptorState", Boolean.toString(foldedSamplerDescriptorState));
            fields.put(normalizedPrefix + ".resourceDescriptor.required", Boolean.toString(resourceDescriptorRequired));
            fields.put(normalizedPrefix + ".resourceDescriptor.available", Boolean.toString(resourceDescriptorAvailable));
            fields.put(normalizedPrefix + ".resourceDescriptorOwner.present", Boolean.toString(resourceDescriptorOwnerPresent));
            fields.put(normalizedPrefix + ".resourceDescriptor.nativeAddress.present", Boolean.toString(resourceDescriptorNativeAddressPresent));
            fields.put(normalizedPrefix + ".resourceDescriptorWrite.planned", Boolean.toString(resourceDescriptorWritePlanned));
            fields.put(normalizedPrefix + ".resourceDescriptorNativeWrite.enabled", Boolean.toString(resourceDescriptorNativeWriteEnabled));
            fields.put(normalizedPrefix + ".textureDescriptor.required", Boolean.toString(textureDescriptorRequired));
            fields.put(normalizedPrefix + ".textureDescriptor.available", Boolean.toString(textureDescriptorAvailable));
            fields.put(normalizedPrefix + ".textureDescriptorOwner.present", Boolean.toString(textureDescriptorOwnerPresent));
            fields.put(normalizedPrefix + ".textureDescriptor.nativeAddress.present", Boolean.toString(textureDescriptorNativeAddressPresent));
            fields.put(normalizedPrefix + ".textureDescriptorWrite.planned", Boolean.toString(textureDescriptorWritePlanned));
            fields.put(normalizedPrefix + ".textureDescriptorNativeWrite.enabled", Boolean.toString(textureDescriptorNativeWriteEnabled));
            fields.put(normalizedPrefix + ".createFunction.required", Boolean.toString(createFunctionRequired));
            fields.put(normalizedPrefix + ".createFunction.available", Boolean.toString(createFunctionAvailable));
            fields.put(normalizedPrefix + ".destroyFunction.required", Boolean.toString(destroyFunctionRequired));
            fields.put(normalizedPrefix + ".destroyFunction.available", Boolean.toString(destroyFunctionAvailable));
            fields.put(normalizedPrefix + ".objectHandle.required", Boolean.toString(objectHandleRequired));
            fields.put(normalizedPrefix + ".objectHandle.available", Boolean.toString(objectHandleAvailable));
            fields.put(normalizedPrefix + ".objectOwnership.required", Boolean.toString(objectOwnershipRequired));
            fields.put(normalizedPrefix + ".objectOwnership.available", Boolean.toString(objectOwnershipAvailable));
            fields.put(normalizedPrefix + ".nativeObjectPreparation.status", nativeObjectPreparationStatus);
            fields.put(normalizedPrefix + ".objectCreationCall.status", objectCreationCallStatus);
            fields.put(normalizedPrefix + ".objectOwnership.status", objectOwnershipStatus);
            fields.put(normalizedPrefix + ".runtimeBinding.status", runtimeBindingStatus);
            fields.put(normalizedPrefix + ".productionSupport.enabled", Boolean.toString(productionSupportEnabled));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerNativeObjectPreparationPreflight {
        entries = entries == null ? List.of() : List.copyOf(entries);
        objectCreationRequestPlanStatus = normalize(objectCreationRequestPlanStatus, "not-present");
        activeNativeDescriptorCount = Math.max(0, activeNativeDescriptorCount);
        activeObjectCount = Math.max(0, activeObjectCount);
    }

    static CudaImageSamplerNativeObjectPreparationPreflight empty() {
        return new CudaImageSamplerNativeObjectPreparationPreflight(
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
                0,
                0
        );
    }

    static CudaImageSamplerNativeObjectPreparationPreflight from(CudaImageSamplerObjectCreationRequestPlan requestPlan) {
        return from(requestPlan, CudaImageSamplerNativeDescriptorEncodingTransactionPlan.empty());
    }

    static CudaImageSamplerNativeObjectPreparationPreflight from(
            CudaImageSamplerObjectCreationRequestPlan requestPlan,
            CudaImageSamplerNativeDescriptorEncodingTransactionPlan encodingTransactionPlan
    ) {
        if (requestPlan == null || !requestPlan.present()) {
            return empty();
        }
        CudaImageSamplerNativeDescriptorEncodingTransactionPlan transactionPlan = encodingTransactionPlan == null
                ? CudaImageSamplerNativeDescriptorEncodingTransactionPlan.empty()
                : encodingTransactionPlan;
        return new CudaImageSamplerNativeObjectPreparationPreflight(
                requestPlan.entries().stream()
                        .map(entry -> CudaImageSamplerNativeObjectPreparationPreflight.entry(entry, transactionPlan.entries()))
                        .toList(),
                requestPlan.status(),
                requestPlan.ready(),
                transactionPlan.status(),
                transactionPlan.present(),
                true,
                false,
                false,
                false,
                false,
                0,
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
                && "ready".equals(nativeDescriptorEncodingTransactionPlanStatus)
                && nativeDescriptorEncodingTransactionPlanPresent
                && javaNativeObjectPreparationPreflightEnabled
                && nativeDescriptorAllocationEnabled
                && objectCreationCallEnabled
                && objectOwnershipEnabled
                && !runtimeBindingEnabled
                && activeNativeDescriptorCount > 0
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

    public long objectPreparationCount() {
        return entries.stream().filter(Entry::objectPreparationRequired).count();
    }

    public long textureObjectPreparationCount() {
        return entries.stream().filter(Entry::textureObjectPreparation).count();
    }

    public long surfaceObjectPreparationCount() {
        return entries.stream().filter(Entry::surfaceObjectPreparation).count();
    }

    public long foldedSamplerPreparationCount() {
        return entries.stream().filter(Entry::foldedSamplerDescriptorState).count();
    }

    public long resourceDescriptorRequiredCount() {
        return entries.stream().filter(Entry::resourceDescriptorRequired).count();
    }

    public long resourceDescriptorAvailableCount() {
        return entries.stream().filter(Entry::resourceDescriptorAvailable).count();
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

    public long textureDescriptorAvailableCount() {
        return entries.stream().filter(Entry::textureDescriptorAvailable).count();
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

    public long createFunctionRequiredCount() {
        return entries.stream().filter(Entry::createFunctionRequired).count();
    }

    public long createFunctionAvailableCount() {
        return entries.stream().filter(Entry::createFunctionAvailable).count();
    }

    public long destroyFunctionRequiredCount() {
        return entries.stream().filter(Entry::destroyFunctionRequired).count();
    }

    public long destroyFunctionAvailableCount() {
        return entries.stream().filter(Entry::destroyFunctionAvailable).count();
    }

    public long objectHandleRequiredCount() {
        return entries.stream().filter(Entry::objectHandleRequired).count();
    }

    public long objectHandleAvailableCount() {
        return entries.stream().filter(Entry::objectHandleAvailable).count();
    }

    public long objectOwnershipAvailableCount() {
        return entries.stream().filter(Entry::objectOwnershipAvailable).count();
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
                .orElseGet(() -> ready() ? "none" : "cuda-image-sampler-native-object-preparation-not-ready");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerNativeObjectPreparationPreflight"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerNativeObjectPreparationPreflight");
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", Boolean.toString(present()));
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".ready", Boolean.toString(ready()));
        fields.put(prefix + ".objectCreationRequestPlan.status", objectCreationRequestPlanStatus);
        fields.put(prefix + ".objectCreationRequestPlan.ready", Boolean.toString(objectCreationRequestPlanReady));
        fields.put(prefix + ".nativeDescriptorEncodingTransactionPlan.status", nativeDescriptorEncodingTransactionPlanStatus);
        fields.put(prefix + ".nativeDescriptorEncodingTransactionPlan.present", Boolean.toString(nativeDescriptorEncodingTransactionPlanPresent));
        fields.put(prefix + ".javaNativeObjectPreparationPreflight.enabled", Boolean.toString(javaNativeObjectPreparationPreflightEnabled));
        fields.put(prefix + ".nativeDescriptorAllocation.enabled", Boolean.toString(nativeDescriptorAllocationEnabled));
        fields.put(prefix + ".objectCreationCall.enabled", Boolean.toString(objectCreationCallEnabled));
        fields.put(prefix + ".objectOwnership.enabled", Boolean.toString(objectOwnershipEnabled));
        fields.put(prefix + ".runtimeBinding.enabled", Boolean.toString(runtimeBindingEnabled));
        fields.put(prefix + ".activeNativeDescriptor.count", Integer.toString(activeNativeDescriptorCount));
        fields.put(prefix + ".activeObject.count", Integer.toString(activeObjectCount));
        fields.put(prefix + ".entry.count", Integer.toString(entries.size()));
        fields.put(prefix + ".entry.ready.count", Long.toString(entryReadyCount()));
        fields.put(prefix + ".entry.blocked.count", Long.toString(entryBlockedCount()));
        fields.put(prefix + ".objectPreparation.count", Long.toString(objectPreparationCount()));
        fields.put(prefix + ".textureObjectPreparation.count", Long.toString(textureObjectPreparationCount()));
        fields.put(prefix + ".surfaceObjectPreparation.count", Long.toString(surfaceObjectPreparationCount()));
        fields.put(prefix + ".foldedSamplerPreparation.count", Long.toString(foldedSamplerPreparationCount()));
        fields.put(prefix + ".resourceDescriptor.required.count", Long.toString(resourceDescriptorRequiredCount()));
        fields.put(prefix + ".resourceDescriptor.available.count", Long.toString(resourceDescriptorAvailableCount()));
        fields.put(prefix + ".resourceDescriptorOwner.present.count", Long.toString(resourceDescriptorOwnerPresentCount()));
        fields.put(prefix + ".resourceDescriptor.nativeAddress.present.count", Long.toString(resourceDescriptorNativeAddressPresentCount()));
        fields.put(prefix + ".resourceDescriptorWrite.planned.count", Long.toString(resourceDescriptorWritePlannedCount()));
        fields.put(prefix + ".resourceDescriptorNativeWrite.enabled.count", Long.toString(resourceDescriptorNativeWriteEnabledCount()));
        fields.put(prefix + ".textureDescriptor.required.count", Long.toString(textureDescriptorRequiredCount()));
        fields.put(prefix + ".textureDescriptor.available.count", Long.toString(textureDescriptorAvailableCount()));
        fields.put(prefix + ".textureDescriptorOwner.present.count", Long.toString(textureDescriptorOwnerPresentCount()));
        fields.put(prefix + ".textureDescriptor.nativeAddress.present.count", Long.toString(textureDescriptorNativeAddressPresentCount()));
        fields.put(prefix + ".textureDescriptorWrite.planned.count", Long.toString(textureDescriptorWritePlannedCount()));
        fields.put(prefix + ".textureDescriptorNativeWrite.enabled.count", Long.toString(textureDescriptorNativeWriteEnabledCount()));
        fields.put(prefix + ".createFunction.required.count", Long.toString(createFunctionRequiredCount()));
        fields.put(prefix + ".createFunction.available.count", Long.toString(createFunctionAvailableCount()));
        fields.put(prefix + ".destroyFunction.required.count", Long.toString(destroyFunctionRequiredCount()));
        fields.put(prefix + ".destroyFunction.available.count", Long.toString(destroyFunctionAvailableCount()));
        fields.put(prefix + ".objectHandle.required.count", Long.toString(objectHandleRequiredCount()));
        fields.put(prefix + ".objectHandle.available.count", Long.toString(objectHandleAvailableCount()));
        fields.put(prefix + ".objectOwnership.available.count", Long.toString(objectOwnershipAvailableCount()));
        fields.put(prefix + ".objectCreationCall.enabled.count", Long.toString(objectCreationCallEnabledCount()));
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < entries.size(); index++) {
            fields.putAll(entries.get(index).artifactFields(prefix + ".entry." + index));
        }
    }

    private static Entry entry(
            CudaImageSamplerObjectCreationRequestPlan.Entry requestEntry,
            List<CudaImageSamplerNativeDescriptorEncodingTransactionPlan.Entry> transactionEntries
    ) {
        if (requestEntry == null) {
            return blockedEntry("cuda-image-sampler-object-creation-request-plan-entry-missing");
        }
        if (!requestEntry.ready()) {
            return new Entry(
                    requestEntry.parameterIndex(),
                    requestEntry.parameterName(),
                    requestEntry.javaType(),
                    requestEntry.abiKey(),
                    requestEntry.cudaAbiRole(),
                    false,
                    "not-required",
                    "not-required",
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
                    false,
                    false,
                    false,
                    "blocked",
                    "disabled",
                    "prepared",
                    "fail-closed",
                    requestEntry.productionSupportEnabled(),
                    requestEntry.firstBlocker()
            );
        }
        boolean objectPreparation = requestEntry.objectCreationRequestRequired();
        boolean foldedSampler = requestEntry.samplerFolded();
        boolean resourceRequired = objectPreparation && requestEntry.resourceDescriptorRequired();
        boolean textureRequired = objectPreparation && requestEntry.textureDescriptorRequired();
        CudaImageSamplerNativeDescriptorEncodingTransactionPlan.DescriptorWrite resourceWrite = descriptorWrite(
                requestEntry,
                transactionEntries,
                "resource"
        );
        CudaImageSamplerNativeDescriptorEncodingTransactionPlan.DescriptorWrite textureWrite = descriptorWrite(
                requestEntry,
                transactionEntries,
                "texture"
        );
        boolean resourceOwnerPresent = resourceRequired && resourceWrite != null && resourceWrite.ownerPresent();
        boolean resourceNativeAddressPresent = resourceRequired && resourceWrite != null && resourceWrite.nativeAddressPresent();
        boolean resourceWritePlanned = resourceRequired && resourceWrite != null && resourceWrite.fieldWriteCount() > 0;
        boolean resourceNativeWriteEnabled = resourceRequired && resourceWrite != null && resourceWrite.nativeWriteEnabled();
        boolean textureOwnerPresent = textureRequired && textureWrite != null && textureWrite.ownerPresent();
        boolean textureNativeAddressPresent = textureRequired && textureWrite != null && textureWrite.nativeAddressPresent();
        boolean textureWritePlanned = textureRequired && textureWrite != null && textureWrite.fieldWriteCount() > 0;
        boolean textureNativeWriteEnabled = textureRequired && textureWrite != null && textureWrite.nativeWriteEnabled();
        return new Entry(
                requestEntry.parameterIndex(),
                requestEntry.parameterName(),
                requestEntry.javaType(),
                requestEntry.abiKey(),
                requestEntry.cudaAbiRole(),
                objectPreparation,
                requestEntry.objectKind(),
                requestEntry.parameterCarrier(),
                foldedSampler,
                resourceRequired,
                false,
                resourceOwnerPresent,
                resourceNativeAddressPresent,
                resourceWritePlanned,
                resourceNativeWriteEnabled,
                textureRequired,
                false,
                textureOwnerPresent,
                textureNativeAddressPresent,
                textureWritePlanned,
                textureNativeWriteEnabled,
                objectPreparation,
                objectPreparation && !"not-required".equals(requestEntry.createFunctionSymbol()),
                objectPreparation,
                objectPreparation && !"not-required".equals(requestEntry.destroyFunctionSymbol()),
                objectPreparation,
                false,
                objectPreparation,
                false,
                objectPreparation ? "blocked" : "native-object-preparation-ready",
                requestEntry.objectCreationCallStatus(),
                requestEntry.objectOwnershipStatus(),
                requestEntry.runtimeBindingStatus(),
                requestEntry.productionSupportEnabled(),
                objectPreparation
                        ? firstNativeDescriptorBlocker(requestEntry, resourceRequired, resourceOwnerPresent, resourceNativeAddressPresent, resourceWritePlanned, resourceNativeWriteEnabled, textureRequired, textureOwnerPresent, textureNativeAddressPresent, textureWritePlanned, textureNativeWriteEnabled)
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
                false,
                false,
                "blocked",
                "disabled",
                "prepared",
                "fail-closed",
                false,
                firstBlocker
        );
    }

    private static CudaImageSamplerNativeDescriptorEncodingTransactionPlan.DescriptorWrite descriptorWrite(
            CudaImageSamplerObjectCreationRequestPlan.Entry requestEntry,
            List<CudaImageSamplerNativeDescriptorEncodingTransactionPlan.Entry> transactionEntries,
            String descriptorKind
    ) {
        if (requestEntry == null || transactionEntries == null) {
            return null;
        }
        return transactionEntries.stream()
                .filter(entry -> entry.parameterIndex() == requestEntry.parameterIndex())
                .filter(entry -> entry.javaType().equals(requestEntry.javaType()))
                .filter(entry -> entry.abiKey().equals(requestEntry.abiKey()))
                .flatMap(entry -> entry.descriptorWrites().stream())
                .filter(write -> descriptorKind.equals(write.descriptorKind()))
                .findFirst()
                .orElse(null);
    }

    private static String firstNativeDescriptorBlocker(
            CudaImageSamplerObjectCreationRequestPlan.Entry requestEntry,
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
                return "cuda-image-sampler-native-resource-descriptor-owner-missing:" + requestEntry.parameterIndex() + ':' + requestEntry.javaType();
            }
            if (!resourceNativeAddressPresent) {
                return "cuda-image-sampler-native-resource-descriptor-address-unavailable:" + requestEntry.parameterIndex() + ':' + requestEntry.javaType();
            }
            if (!resourceWritePlanned) {
                return "cuda-image-sampler-native-resource-descriptor-write-plan-missing:" + requestEntry.parameterIndex() + ':' + requestEntry.javaType();
            }
            if (!resourceNativeWriteEnabled) {
                return "cuda-image-sampler-native-resource-descriptor-write-disabled:" + requestEntry.parameterIndex() + ':' + requestEntry.javaType();
            }
        }
        if (textureRequired) {
            if (!textureOwnerPresent) {
                return "cuda-image-sampler-native-texture-descriptor-owner-missing:" + requestEntry.parameterIndex() + ':' + requestEntry.javaType();
            }
            if (!textureNativeAddressPresent) {
                return "cuda-image-sampler-native-texture-descriptor-address-unavailable:" + requestEntry.parameterIndex() + ':' + requestEntry.javaType();
            }
            if (!textureWritePlanned) {
                return "cuda-image-sampler-native-texture-descriptor-write-plan-missing:" + requestEntry.parameterIndex() + ':' + requestEntry.javaType();
            }
            if (!textureNativeWriteEnabled) {
                return "cuda-image-sampler-native-texture-descriptor-write-disabled:" + requestEntry.parameterIndex() + ':' + requestEntry.javaType();
            }
        }
        return "cuda-image-sampler-native-object-preparation-not-ready:" + requestEntry.parameterIndex() + ':' + requestEntry.javaType();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
