package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardware-free contract for the future CUDA image/sampler runtime object-binding transaction.
 */
public record CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport(List<Case> cases) {

    public record Case(
            String key,
            String expectedPreflightStatus,
            String expectedFirstBlocker,
            int expectedObjectBindingTransactions,
            int expectedTextureObjectTransactions,
            int expectedSurfaceObjectTransactions,
            int expectedFoldedSamplers,
            int expectedObjectSlots,
            int expectedMetadataSlots,
            int expectedKernelSlots,
            CudaImageSamplerRuntimeObjectBindingTransactionPreflight preflight
    ) {
        public Case {
            key = normalize(key, "unknown");
            expectedPreflightStatus = normalize(expectedPreflightStatus, "blocked");
            expectedFirstBlocker = normalize(expectedFirstBlocker, "none");
            expectedObjectBindingTransactions = Math.max(0, expectedObjectBindingTransactions);
            expectedTextureObjectTransactions = Math.max(0, expectedTextureObjectTransactions);
            expectedSurfaceObjectTransactions = Math.max(0, expectedSurfaceObjectTransactions);
            expectedFoldedSamplers = Math.max(0, expectedFoldedSamplers);
            expectedObjectSlots = Math.max(0, expectedObjectSlots);
            expectedMetadataSlots = Math.max(0, expectedMetadataSlots);
            expectedKernelSlots = Math.max(0, expectedKernelSlots);
            preflight = preflight == null
                    ? CudaImageSamplerRuntimeObjectBindingTransactionPreflight.empty()
                    : preflight;
        }

        public boolean ready() {
            return expectedPreflightStatus.equals(preflight.status())
                    && expectedFirstBlocker.equals(preflight.firstBlocker())
                    && expectedObjectBindingTransactions == preflight.objectBindingTransactionCount()
                    && expectedTextureObjectTransactions == preflight.textureObjectTransactionCount()
                    && expectedSurfaceObjectTransactions == preflight.surfaceObjectTransactionCount()
                    && expectedFoldedSamplers == preflight.foldedSamplerTransactionCount()
                    && expectedObjectSlots == preflight.plannedObjectKernelParameterSlotCount()
                    && expectedMetadataSlots == preflight.plannedMetadataKernelParameterSlotCount()
                    && expectedKernelSlots == preflight.plannedKernelParameterSlotCount()
                    && preflight.objectHandleAvailableCount() == 0
                    && preflight.nativeDescriptorAvailableCount() == 0
                    && expectedObjectBindingTransactions == preflight.resourceDescriptorRequiredCount()
                    && expectedObjectBindingTransactions == preflight.resourceDescriptorOwnerPresentCount()
                    && expectedObjectBindingTransactions == preflight.resourceDescriptorWritePlannedCount()
                    && expectedTextureObjectTransactions == preflight.textureDescriptorRequiredCount()
                    && expectedTextureObjectTransactions == preflight.textureDescriptorOwnerPresentCount()
                    && expectedTextureObjectTransactions == preflight.textureDescriptorWritePlannedCount()
                    && preflight.resourceDescriptorNativeAddressPresentCount() == 0
                    && preflight.resourceDescriptorNativeWriteEnabledCount() == 0
                    && preflight.textureDescriptorNativeAddressPresentCount() == 0
                    && preflight.textureDescriptorNativeWriteEnabledCount() == 0
                    && preflight.transactionApplyEnabledCount() == 0
                    && preflight.kernelParameterWriteEnabledCount() == 0
                    && preflight.activeObjectCount() == 0;
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public String firstBlocker() {
            if (ready()) {
                return "none";
            }
            if (!expectedPreflightStatus.equals(preflight.status())) {
                return "cuda-image-sampler-runtime-object-binding-preflight-status-mismatch:" + key + ':' + preflight.status();
            }
            if (!expectedFirstBlocker.equals(preflight.firstBlocker())) {
                return "cuda-image-sampler-runtime-object-binding-preflight-blocker-mismatch:" + key + ':' + preflight.firstBlocker();
            }
            if (expectedObjectBindingTransactions != preflight.objectBindingTransactionCount()) {
                return "cuda-image-sampler-runtime-object-binding-transaction-count-mismatch:" + key + ':' + preflight.objectBindingTransactionCount();
            }
            if (expectedTextureObjectTransactions != preflight.textureObjectTransactionCount()) {
                return "cuda-image-sampler-runtime-texture-transaction-count-mismatch:" + key + ':' + preflight.textureObjectTransactionCount();
            }
            if (expectedSurfaceObjectTransactions != preflight.surfaceObjectTransactionCount()) {
                return "cuda-image-sampler-runtime-surface-transaction-count-mismatch:" + key + ':' + preflight.surfaceObjectTransactionCount();
            }
            if (expectedFoldedSamplers != preflight.foldedSamplerTransactionCount()) {
                return "cuda-image-sampler-runtime-folded-sampler-transaction-count-mismatch:" + key + ':' + preflight.foldedSamplerTransactionCount();
            }
            if (expectedObjectSlots != preflight.plannedObjectKernelParameterSlotCount()) {
                return "cuda-image-sampler-runtime-object-transaction-slot-count-mismatch:" + key + ':' + preflight.plannedObjectKernelParameterSlotCount();
            }
            if (expectedMetadataSlots != preflight.plannedMetadataKernelParameterSlotCount()) {
                return "cuda-image-sampler-runtime-metadata-transaction-slot-count-mismatch:" + key + ':' + preflight.plannedMetadataKernelParameterSlotCount();
            }
            if (expectedKernelSlots != preflight.plannedKernelParameterSlotCount()) {
                return "cuda-image-sampler-runtime-kernel-transaction-slot-count-mismatch:" + key + ':' + preflight.plannedKernelParameterSlotCount();
            }
            if (preflight.objectHandleAvailableCount() != 0) {
                return "cuda-image-sampler-runtime-object-handle-available-unexpected:" + key + ':' + preflight.objectHandleAvailableCount();
            }
            if (preflight.nativeDescriptorAvailableCount() != 0) {
                return "cuda-image-sampler-runtime-native-descriptor-available-unexpected:" + key + ':' + preflight.nativeDescriptorAvailableCount();
            }
            if (expectedObjectBindingTransactions != preflight.resourceDescriptorRequiredCount()) {
                return "cuda-image-sampler-runtime-resource-descriptor-required-count-mismatch:" + key + ':' + preflight.resourceDescriptorRequiredCount();
            }
            if (expectedObjectBindingTransactions != preflight.resourceDescriptorOwnerPresentCount()) {
                return "cuda-image-sampler-runtime-resource-descriptor-owner-count-mismatch:" + key + ':' + preflight.resourceDescriptorOwnerPresentCount();
            }
            if (expectedObjectBindingTransactions != preflight.resourceDescriptorWritePlannedCount()) {
                return "cuda-image-sampler-runtime-resource-descriptor-write-plan-count-mismatch:" + key + ':' + preflight.resourceDescriptorWritePlannedCount();
            }
            if (expectedTextureObjectTransactions != preflight.textureDescriptorRequiredCount()) {
                return "cuda-image-sampler-runtime-texture-descriptor-required-count-mismatch:" + key + ':' + preflight.textureDescriptorRequiredCount();
            }
            if (expectedTextureObjectTransactions != preflight.textureDescriptorOwnerPresentCount()) {
                return "cuda-image-sampler-runtime-texture-descriptor-owner-count-mismatch:" + key + ':' + preflight.textureDescriptorOwnerPresentCount();
            }
            if (expectedTextureObjectTransactions != preflight.textureDescriptorWritePlannedCount()) {
                return "cuda-image-sampler-runtime-texture-descriptor-write-plan-count-mismatch:" + key + ':' + preflight.textureDescriptorWritePlannedCount();
            }
            if (preflight.resourceDescriptorNativeAddressPresentCount() != 0) {
                return "cuda-image-sampler-runtime-resource-descriptor-address-present-unexpected:" + key + ':' + preflight.resourceDescriptorNativeAddressPresentCount();
            }
            if (preflight.resourceDescriptorNativeWriteEnabledCount() != 0) {
                return "cuda-image-sampler-runtime-resource-descriptor-write-enabled:" + key + ':' + preflight.resourceDescriptorNativeWriteEnabledCount();
            }
            if (preflight.textureDescriptorNativeAddressPresentCount() != 0) {
                return "cuda-image-sampler-runtime-texture-descriptor-address-present-unexpected:" + key + ':' + preflight.textureDescriptorNativeAddressPresentCount();
            }
            if (preflight.textureDescriptorNativeWriteEnabledCount() != 0) {
                return "cuda-image-sampler-runtime-texture-descriptor-write-enabled:" + key + ':' + preflight.textureDescriptorNativeWriteEnabledCount();
            }
            if (preflight.transactionApplyEnabledCount() != 0) {
                return "cuda-image-sampler-runtime-transaction-apply-enabled:" + key + ':' + preflight.transactionApplyEnabledCount();
            }
            if (preflight.kernelParameterWriteEnabledCount() != 0) {
                return "cuda-image-sampler-runtime-kernel-parameter-write-enabled:" + key + ':' + preflight.kernelParameterWriteEnabledCount();
            }
            if (preflight.activeObjectCount() != 0) {
                return "cuda-image-sampler-runtime-active-objects-unexpected:" + key + ':' + preflight.activeObjectCount();
            }
            return "cuda-image-sampler-runtime-object-binding-transaction-preflight-case-not-ready:" + key;
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport.case"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".key", key);
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".expected.preflight.status", expectedPreflightStatus);
            fields.put(normalizedPrefix + ".expected.firstBlocker", expectedFirstBlocker);
            fields.put(normalizedPrefix + ".expected.objectBindingTransaction.count", Integer.toString(expectedObjectBindingTransactions));
            fields.put(normalizedPrefix + ".expected.textureObjectTransaction.count", Integer.toString(expectedTextureObjectTransactions));
            fields.put(normalizedPrefix + ".expected.surfaceObjectTransaction.count", Integer.toString(expectedSurfaceObjectTransactions));
            fields.put(normalizedPrefix + ".expected.foldedSamplerTransaction.count", Integer.toString(expectedFoldedSamplers));
            fields.put(normalizedPrefix + ".expected.plannedObjectKernelParameterSlot.count", Integer.toString(expectedObjectSlots));
            fields.put(normalizedPrefix + ".expected.plannedMetadataKernelParameterSlot.count", Integer.toString(expectedMetadataSlots));
            fields.put(normalizedPrefix + ".expected.plannedKernelParameterSlot.count", Integer.toString(expectedKernelSlots));
            fields.put(normalizedPrefix + ".actual.preflight.status", preflight.status());
            fields.put(normalizedPrefix + ".actual.firstBlocker", preflight.firstBlocker());
            fields.putAll(preflight.artifactFields(normalizedPrefix + ".preflight"));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public static CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport inspectBuiltIns() {
        return new CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport(
                CudaImageSamplerRuntimeObjectBindingPlanReport.inspectBuiltIns().cases().stream()
                        .map(CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport::runCase)
                        .toList()
        );
    }

    public boolean ready() {
        return !cases.isEmpty() && cases.stream().allMatch(Case::ready);
    }

    public String status() {
        return ready() ? "ready" : "blocked";
    }

    public long caseReadyCount() {
        return cases.stream().filter(Case::ready).count();
    }

    public long caseBlockedCount() {
        return cases.stream().filter(testCase -> !testCase.ready()).count();
    }

    public long planReadyCount() {
        return cases.stream().filter(testCase -> "ready".equals(testCase.preflight().runtimeObjectBindingPlanStatus())).count();
    }

    public long planBlockedCount() {
        return cases.stream().filter(testCase -> "blocked".equals(testCase.preflight().runtimeObjectBindingPlanStatus())).count();
    }

    public long preflightReadyCount() {
        return cases.stream().filter(testCase -> "ready".equals(testCase.preflight().status())).count();
    }

    public long preflightBlockedCount() {
        return cases.stream().filter(testCase -> "blocked".equals(testCase.preflight().status())).count();
    }

    public long entryCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().entries().size()).sum();
    }

    public long objectBindingTransactionCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().objectBindingTransactionCount()).sum();
    }

    public long textureObjectTransactionCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().textureObjectTransactionCount()).sum();
    }

    public long surfaceObjectTransactionCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().surfaceObjectTransactionCount()).sum();
    }

    public long foldedSamplerTransactionCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().foldedSamplerTransactionCount()).sum();
    }

    public long plannedObjectKernelParameterSlotCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().plannedObjectKernelParameterSlotCount()).sum();
    }

    public long plannedMetadataKernelParameterSlotCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().plannedMetadataKernelParameterSlotCount()).sum();
    }

    public long plannedKernelParameterSlotCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().plannedKernelParameterSlotCount()).sum();
    }

    public long objectHandleRequiredCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().objectHandleRequiredCount()).sum();
    }

    public long objectHandleAvailableCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().objectHandleAvailableCount()).sum();
    }

    public long nativeDescriptorAvailableCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().nativeDescriptorAvailableCount()).sum();
    }

    public long resourceDescriptorRequiredCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().resourceDescriptorRequiredCount()).sum();
    }

    public long resourceDescriptorOwnerPresentCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().resourceDescriptorOwnerPresentCount()).sum();
    }

    public long resourceDescriptorNativeAddressPresentCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().resourceDescriptorNativeAddressPresentCount()).sum();
    }

    public long resourceDescriptorWritePlannedCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().resourceDescriptorWritePlannedCount()).sum();
    }

    public long resourceDescriptorNativeWriteEnabledCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().resourceDescriptorNativeWriteEnabledCount()).sum();
    }

    public long textureDescriptorRequiredCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().textureDescriptorRequiredCount()).sum();
    }

    public long textureDescriptorOwnerPresentCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().textureDescriptorOwnerPresentCount()).sum();
    }

    public long textureDescriptorNativeAddressPresentCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().textureDescriptorNativeAddressPresentCount()).sum();
    }

    public long textureDescriptorWritePlannedCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().textureDescriptorWritePlannedCount()).sum();
    }

    public long textureDescriptorNativeWriteEnabledCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().textureDescriptorNativeWriteEnabledCount()).sum();
    }

    public long transactionApplyEnabledCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().transactionApplyEnabledCount()).sum();
    }

    public long kernelParameterWriteEnabledCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().kernelParameterWriteEnabledCount()).sum();
    }

    public long activeObjectCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().activeObjectCount()).sum();
    }

    public String firstBlocker() {
        if (cases.isEmpty()) {
            return "cuda-image-sampler-runtime-object-binding-transaction-preflight-cases-missing";
        }
        return cases.stream()
                .filter(testCase -> !testCase.ready())
                .map(Case::firstBlocker)
                .findFirst()
                .orElse("none");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflightReport");
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler runtime object binding transaction preflight: ").append(status()).append('\n');
        builder.append("Cases: ").append(caseReadyCount()).append('/').append(cases.size()).append(" ready").append('\n');
        builder.append("Plans: ready=").append(planReadyCount()).append(", blocked=").append(planBlockedCount()).append('\n');
        builder.append("Preflights: ready=").append(preflightReadyCount()).append(", blocked=").append(preflightBlockedCount()).append('\n');
        builder.append("Entries: ").append(entryCount()).append('\n');
        builder.append("Transactions: texture=").append(textureObjectTransactionCount())
                .append(", surface=").append(surfaceObjectTransactionCount())
                .append(", total=").append(objectBindingTransactionCount()).append('\n');
        builder.append("Kernel slots: object=").append(plannedObjectKernelParameterSlotCount())
                .append(", metadata=").append(plannedMetadataKernelParameterSlotCount())
                .append(", planned=").append(plannedKernelParameterSlotCount()).append('\n');
        builder.append("Object handles: required=").append(objectHandleRequiredCount())
                .append(", available=").append(objectHandleAvailableCount()).append('\n');
        builder.append("Native descriptors: resourceRequired=").append(resourceDescriptorRequiredCount())
                .append(", resourceOwners=").append(resourceDescriptorOwnerPresentCount())
                .append(", resourceAddresses=").append(resourceDescriptorNativeAddressPresentCount())
                .append(", textureRequired=").append(textureDescriptorRequiredCount())
                .append(", textureOwners=").append(textureDescriptorOwnerPresentCount())
                .append(", textureAddresses=").append(textureDescriptorNativeAddressPresentCount()).append('\n');
        builder.append("Kernel parameter writes enabled: ").append(kernelParameterWriteEnabledCount()).append('\n');
        builder.append("Active objects: ").append(activeObjectCount()).append('\n');
        builder.append("First blocker: ").append(firstBlocker()).append('\n');
        builder.append('\n').append("Cases:").append('\n');
        for (Case testCase : cases) {
            builder.append("- ")
                    .append(testCase.key())
                    .append(": case=")
                    .append(testCase.status())
                    .append(", preflight=")
                    .append(testCase.preflight().status())
                    .append(", firstBlocker=")
                    .append(testCase.preflight().firstBlocker())
                    .append('\n');
        }
        return builder.toString();
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", "true");
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".ready", Boolean.toString(ready()));
        fields.put(prefix + ".case.count", Integer.toString(cases.size()));
        fields.put(prefix + ".case.ready.count", Long.toString(caseReadyCount()));
        fields.put(prefix + ".case.blocked.count", Long.toString(caseBlockedCount()));
        fields.put(prefix + ".plan.ready.count", Long.toString(planReadyCount()));
        fields.put(prefix + ".plan.blocked.count", Long.toString(planBlockedCount()));
        fields.put(prefix + ".preflight.ready.count", Long.toString(preflightReadyCount()));
        fields.put(prefix + ".preflight.blocked.count", Long.toString(preflightBlockedCount()));
        fields.put(prefix + ".entry.count", Long.toString(entryCount()));
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
        fields.put(prefix + ".activeObject.count", Long.toString(activeObjectCount()));
        fields.put(prefix + ".nativeDescriptorAllocation.enabled", "false");
        fields.put(prefix + ".objectCreationCall.enabled", "false");
        fields.put(prefix + ".transactionApply.enabled", "false");
        fields.put(prefix + ".kernelParameterWrite.enabled", "false");
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < cases.size(); index++) {
            fields.putAll(cases.get(index).artifactFields(prefix + ".case." + index));
        }
    }

    private static Case runCase(CudaImageSamplerRuntimeObjectBindingPlanReport.Case planCase) {
        CudaImageSamplerNativeDescriptorEncodingPlan encodingPlan = planCase.plan().objectCreationRequestPlanReady()
                ? CudaImageSamplerNativeDescriptorEncodingPlanReport.inspectBuiltIns().cases().stream()
                .filter(testCase -> testCase.key().equals(planCase.key()))
                .map(CudaImageSamplerNativeDescriptorEncodingPlanReport.Case::plan)
                .findFirst()
                .orElse(CudaImageSamplerNativeDescriptorEncodingPlan.empty())
                : CudaImageSamplerNativeDescriptorEncodingPlan.empty();
        CudaImageSamplerObjectCreationRequestPlan requestPlan = CudaImageSamplerObjectCreationRequestPlan.from(encodingPlan);
        CudaImageSamplerNativeObjectPreparationPreflight nativeObjectPreflight =
                CudaImageSamplerNativeObjectPreparationPreflight.from(
                        requestPlan,
                        CudaImageSamplerNativeDescriptorEncodingTransactionPlan.from(
                                encodingPlan,
                                CudaImageSamplerNativeDescriptorAllocationTransactionPlan.from(
                                        CudaImageSamplerNativeDescriptorAllocationPreflight.from(encodingPlan)
                                )
                        )
                );
        CudaImageSamplerRuntimeObjectBindingTransactionPreflight preflight =
                CudaImageSamplerRuntimeObjectBindingTransactionPreflight.from(planCase.plan(), nativeObjectPreflight);
        boolean readyPlan = "ready".equals(planCase.plan().status());
        return new Case(
                planCase.key(),
                "blocked",
                readyPlan ? preflight.firstBlocker() : planCase.plan().firstBlocker(),
                readyPlan ? 16 : 0,
                readyPlan ? 8 : 0,
                readyPlan ? 8 : 0,
                readyPlan ? 1 : 0,
                readyPlan ? 16 : 0,
                readyPlan ? 28 : 0,
                readyPlan ? 44 : 0,
                preflight
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
