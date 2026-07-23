package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level hardware-free CUDA image/sampler fail-closed contract.
 *
 * <p>This report aggregates the staged image/sampler reports and pins the global invariant that all planned native
 * descriptor, object-creation, runtime-binding, and kernel-argument write work remains disabled until the production
 * boundary is deliberately opened.</p>
 */
public record CudaImageSamplerFailClosedContractReport(
        CudaImageSamplerContractReport imageSamplerContract,
        CudaImageSamplerAbiPlanReport abiPlan,
        CudaImageSamplerObjectCreationContractReport objectCreationContract,
        CudaImageSamplerDescriptorContractReport descriptorContract,
        CudaImageSamplerNativeDescriptorLayoutReport nativeDescriptorLayout,
        CudaImageSamplerDescriptorBuildPlanReport descriptorBuildPlan,
        CudaImageSamplerDescriptorPayloadModelReport descriptorPayloadModel,
        CudaImageSamplerNativeDescriptorEncodingPlanReport nativeDescriptorEncodingPlan,
        CudaImageSamplerNativeDescriptorAllocationPreflightReport nativeDescriptorAllocationPreflight,
        CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport nativeDescriptorAllocationTransactionPlan,
        CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport nativeDescriptorEncodingTransactionPlan,
        CudaImageSamplerObjectCreationRequestPlanReport objectCreationRequestPlan,
        CudaImageSamplerNativeObjectPreparationPreflightReport nativeObjectPreparationPreflight,
        CudaImageSamplerRuntimeObjectBindingPlanReport runtimeObjectBindingPlan,
        CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport runtimeObjectBindingTransactionPreflight
) {

    public record Component(
            String key,
            String status,
            boolean ready,
            String firstBlocker
    ) {
        public Component {
            key = normalize(key, "unknown");
            status = normalize(status, ready ? "ready" : "blocked");
            firstBlocker = normalize(firstBlocker, "none");
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerFailClosedContract.component"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".key", key);
            fields.put(normalizedPrefix + ".status", status);
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerFailClosedContractReport {
        imageSamplerContract = imageSamplerContract == null
                ? CudaImageSamplerContractReport.inspectBuiltIns()
                : imageSamplerContract;
        abiPlan = abiPlan == null ? CudaImageSamplerAbiPlanReport.inspectBuiltIns() : abiPlan;
        objectCreationContract = objectCreationContract == null
                ? CudaImageSamplerObjectCreationContractReport.inspectBuiltIns()
                : objectCreationContract;
        descriptorContract = descriptorContract == null
                ? CudaImageSamplerDescriptorContractReport.inspectBuiltIns()
                : descriptorContract;
        nativeDescriptorLayout = nativeDescriptorLayout == null
                ? CudaImageSamplerNativeDescriptorLayoutReport.inspectBuiltIns()
                : nativeDescriptorLayout;
        descriptorBuildPlan = descriptorBuildPlan == null
                ? CudaImageSamplerDescriptorBuildPlanReport.inspectBuiltIns()
                : descriptorBuildPlan;
        descriptorPayloadModel = descriptorPayloadModel == null
                ? CudaImageSamplerDescriptorPayloadModelReport.inspectBuiltIns()
                : descriptorPayloadModel;
        nativeDescriptorEncodingPlan = nativeDescriptorEncodingPlan == null
                ? CudaImageSamplerNativeDescriptorEncodingPlanReport.inspectBuiltIns()
                : nativeDescriptorEncodingPlan;
        nativeDescriptorAllocationPreflight = nativeDescriptorAllocationPreflight == null
                ? CudaImageSamplerNativeDescriptorAllocationPreflightReport.inspectBuiltIns()
                : nativeDescriptorAllocationPreflight;
        nativeDescriptorAllocationTransactionPlan = nativeDescriptorAllocationTransactionPlan == null
                ? CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport.inspectBuiltIns()
                : nativeDescriptorAllocationTransactionPlan;
        nativeDescriptorEncodingTransactionPlan = nativeDescriptorEncodingTransactionPlan == null
                ? CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport.inspectBuiltIns()
                : nativeDescriptorEncodingTransactionPlan;
        objectCreationRequestPlan = objectCreationRequestPlan == null
                ? CudaImageSamplerObjectCreationRequestPlanReport.inspectBuiltIns()
                : objectCreationRequestPlan;
        nativeObjectPreparationPreflight = nativeObjectPreparationPreflight == null
                ? CudaImageSamplerNativeObjectPreparationPreflightReport.inspectBuiltIns()
                : nativeObjectPreparationPreflight;
        runtimeObjectBindingPlan = runtimeObjectBindingPlan == null
                ? CudaImageSamplerRuntimeObjectBindingPlanReport.inspectBuiltIns()
                : runtimeObjectBindingPlan;
        runtimeObjectBindingTransactionPreflight = runtimeObjectBindingTransactionPreflight == null
                ? CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport.inspectBuiltIns()
                : runtimeObjectBindingTransactionPreflight;
    }

    public static CudaImageSamplerFailClosedContractReport inspectBuiltIns() {
        return new CudaImageSamplerFailClosedContractReport(
                CudaImageSamplerContractReport.inspectBuiltIns(),
                CudaImageSamplerAbiPlanReport.inspectBuiltIns(),
                CudaImageSamplerObjectCreationContractReport.inspectBuiltIns(),
                CudaImageSamplerDescriptorContractReport.inspectBuiltIns(),
                CudaImageSamplerNativeDescriptorLayoutReport.inspectBuiltIns(),
                CudaImageSamplerDescriptorBuildPlanReport.inspectBuiltIns(),
                CudaImageSamplerDescriptorPayloadModelReport.inspectBuiltIns(),
                CudaImageSamplerNativeDescriptorEncodingPlanReport.inspectBuiltIns(),
                CudaImageSamplerNativeDescriptorAllocationPreflightReport.inspectBuiltIns(),
                CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport.inspectBuiltIns(),
                CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport.inspectBuiltIns(),
                CudaImageSamplerObjectCreationRequestPlanReport.inspectBuiltIns(),
                CudaImageSamplerNativeObjectPreparationPreflightReport.inspectBuiltIns(),
                CudaImageSamplerRuntimeObjectBindingPlanReport.inspectBuiltIns(),
                CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport.inspectBuiltIns()
        );
    }

    public List<Component> components() {
        return List.of(
                component("argument-contract", imageSamplerContract.status(), imageSamplerContract.ready(), imageSamplerContract.firstBlocker()),
                component("abi-plan", abiPlan.status(), abiPlan.ready(), abiPlan.firstBlocker()),
                component("object-creation-contract", objectCreationContract.status(), objectCreationContract.ready(), objectCreationContract.firstBlocker()),
                component("descriptor-contract", descriptorContract.status(), descriptorContract.ready(), descriptorContract.firstBlocker()),
                component("native-descriptor-layout", nativeDescriptorLayout.status(), nativeDescriptorLayout.ready(), nativeDescriptorLayout.firstBlocker()),
                component("descriptor-build-plan", descriptorBuildPlan.status(), descriptorBuildPlan.ready(), descriptorBuildPlan.firstBlocker()),
                component("descriptor-payload-model", descriptorPayloadModel.status(), descriptorPayloadModel.ready(), descriptorPayloadModel.firstBlocker()),
                component("native-descriptor-encoding-plan", nativeDescriptorEncodingPlan.status(), nativeDescriptorEncodingPlan.ready(), nativeDescriptorEncodingPlan.firstBlocker()),
                component("native-descriptor-allocation-preflight", nativeDescriptorAllocationPreflight.status(), nativeDescriptorAllocationPreflight.ready(), nativeDescriptorAllocationPreflight.firstBlocker()),
                component("native-descriptor-allocation-transaction-plan", nativeDescriptorAllocationTransactionPlan.status(), nativeDescriptorAllocationTransactionPlan.ready(), nativeDescriptorAllocationTransactionPlan.firstBlocker()),
                component("native-descriptor-encoding-transaction-plan", nativeDescriptorEncodingTransactionPlan.status(), nativeDescriptorEncodingTransactionPlan.ready(), nativeDescriptorEncodingTransactionPlan.firstBlocker()),
                component("object-creation-request-plan", objectCreationRequestPlan.status(), objectCreationRequestPlan.ready(), objectCreationRequestPlan.firstBlocker()),
                component("native-object-preparation-preflight", nativeObjectPreparationPreflight.status(), nativeObjectPreparationPreflight.ready(), nativeObjectPreparationPreflight.firstBlocker()),
                component("runtime-object-binding-plan", runtimeObjectBindingPlan.status(), runtimeObjectBindingPlan.ready(), runtimeObjectBindingPlan.firstBlocker()),
                component("runtime-object-binding-transaction-preflight", runtimeObjectBindingTransactionPreflight.status(), runtimeObjectBindingTransactionPreflight.ready(), runtimeObjectBindingTransactionPreflight.firstBlocker())
        );
    }

    public long componentReadyCount() {
        return components().stream().filter(Component::ready).count();
    }

    public long componentBlockedCount() {
        return components().stream().filter(component -> !component.ready()).count();
    }

    public long plannedNativeDescriptorCount() {
        return nativeDescriptorAllocationPreflight.plannedNativeDescriptorCount();
    }

    public long plannedObjectRequestCount() {
        return objectCreationRequestPlan.objectCreationRequestCount();
    }

    public long plannedRuntimeKernelParameterSlotCount() {
        return runtimeObjectBindingTransactionPreflight.plannedKernelParameterSlotCount();
    }

    public long runtimeBindingKernelParameterSlotCount() {
        return imageSamplerContract.runtimeBindingKernelParameterSlotCount()
                + abiPlan.runtimeBindingKernelParameterSlotCount()
                + runtimeObjectBindingPlan.runtimeBindingKernelParameterSlotCount();
    }

    public long objectCreationCallEnabledCount() {
        return objectCreationRequestPlan.objectCreationCallEnabledCount()
                + nativeObjectPreparationPreflight.objectCreationCallEnabledCount()
                + runtimeObjectBindingPlan.objectCreationCallEnabledCount();
    }

    public long nativeDescriptorAvailableCount() {
        return nativeObjectPreparationPreflight.resourceDescriptorAvailableCount()
                + nativeObjectPreparationPreflight.textureDescriptorAvailableCount()
                + runtimeObjectBindingTransactionPreflight.nativeDescriptorAvailableCount();
    }

    public long nativeDescriptorAddressPresentCount() {
        return nativeDescriptorAllocationTransactionPlan.nativeAddressPresentCount()
                + nativeDescriptorEncodingTransactionPlan.nativeAddressPresentCount()
                + nativeObjectPreparationPreflight.resourceDescriptorNativeAddressPresentCount()
                + nativeObjectPreparationPreflight.textureDescriptorNativeAddressPresentCount()
                + runtimeObjectBindingTransactionPreflight.resourceDescriptorNativeAddressPresentCount()
                + runtimeObjectBindingTransactionPreflight.textureDescriptorNativeAddressPresentCount();
    }

    public long nativeDescriptorWriteEnabledCount() {
        return nativeDescriptorEncodingPlan.nativeWriteEnabledCount()
                + nativeDescriptorEncodingTransactionPlan.nativeWriteEnabledCount()
                + nativeObjectPreparationPreflight.resourceDescriptorNativeWriteEnabledCount()
                + nativeObjectPreparationPreflight.textureDescriptorNativeWriteEnabledCount()
                + runtimeObjectBindingTransactionPreflight.resourceDescriptorNativeWriteEnabledCount()
                + runtimeObjectBindingTransactionPreflight.textureDescriptorNativeWriteEnabledCount();
    }

    public long objectHandleAvailableCount() {
        return nativeObjectPreparationPreflight.objectHandleAvailableCount()
                + runtimeObjectBindingTransactionPreflight.objectHandleAvailableCount();
    }

    public long activeNativeDescriptorCount() {
        return nativeDescriptorLayout.activeNativeDescriptorCount()
                + descriptorBuildPlan.activeNativeDescriptorCount()
                + descriptorPayloadModel.activeNativeDescriptorCount()
                + nativeDescriptorEncodingPlan.activeNativeDescriptorCount()
                + nativeDescriptorAllocationPreflight.activeNativeDescriptorCount()
                + nativeDescriptorAllocationTransactionPlan.activeNativeDescriptorCount()
                + nativeDescriptorEncodingTransactionPlan.activeNativeDescriptorCount()
                + nativeObjectPreparationPreflight.activeNativeDescriptorCount();
    }

    public long activeObjectCount() {
        return objectCreationContract.activeObjectCount()
                + objectCreationRequestPlan.activeObjectCount()
                + nativeObjectPreparationPreflight.activeObjectCount()
                + runtimeObjectBindingPlan.activeObjectCount()
                + runtimeObjectBindingTransactionPreflight.activeObjectCount();
    }

    public long nativeMutationCount() {
        return (objectCreationContract.objectCreationEnabled() ? 1 : 0)
                + (descriptorContract.descriptorBuildEnabled() ? 1 : 0)
                + (descriptorContract.nativeDescriptorAllocationEnabled() ? 1 : 0)
                + descriptorContract.activeDescriptorCount()
                + (nativeDescriptorLayout.nativeLayoutBuildEnabled() ? 1 : 0)
                + (nativeDescriptorLayout.nativeDescriptorAllocationEnabled() ? 1 : 0)
                + descriptorBuildPlan.activeDescriptorPayloadCount()
                + nativeDescriptorAllocationPreflight.allocatedNativeDescriptorCount()
                + nativeDescriptorAllocationPreflight.nativeDescriptorOwnershipActiveCount()
                + nativeDescriptorAllocationPreflight.cleanupActiveCount()
                + nativeDescriptorAllocationPreflight.rollbackActiveCount()
                + nativeDescriptorAllocationPreflight.allocationEnabledCount()
                + nativeDescriptorAllocationPreflight.sdkStructByteEncodingEnabledCount()
                + nativeDescriptorAllocationTransactionPlan.activeDescriptorOwnerCount()
                + nativeDescriptorAllocationTransactionPlan.allocationEnabledCount()
                + nativeDescriptorAllocationTransactionPlan.cleanupActiveCount()
                + nativeDescriptorAllocationTransactionPlan.rollbackActiveCount()
                + nativeDescriptorEncodingPlan.sdkStructByteEncodingEnabledCount()
                + nativeDescriptorEncodingTransactionPlan.ownerActiveCount()
                + nativeDescriptorEncodingTransactionPlan.sdkStructByteEncodingEnabledCount()
                + nativeObjectPreparationPreflight.objectOwnershipAvailableCount()
                + runtimeObjectBindingTransactionPreflight.transactionApplyEnabledCount()
                + runtimeObjectBindingTransactionPreflight.kernelParameterWriteEnabledCount()
                + runtimeBindingKernelParameterSlotCount()
                + objectCreationCallEnabledCount()
                + nativeDescriptorAvailableCount()
                + nativeDescriptorAddressPresentCount()
                + nativeDescriptorWriteEnabledCount()
                + objectHandleAvailableCount()
                + activeNativeDescriptorCount()
                + activeObjectCount();
    }

    public boolean ready() {
        return !components().isEmpty()
                && componentBlockedCount() == 0
                && nativeMutationCount() == 0
                && abiPlan.runtimeBindingEnabledCount() == 0;
    }

    public String status() {
        return ready() ? "ready" : "blocked";
    }

    public String firstBlocker() {
        return components().stream()
                .filter(component -> !component.ready())
                .map(component -> "cuda-image-sampler-fail-closed-component-not-ready:" + component.key() + ':' + component.firstBlocker())
                .findFirst()
                .orElseGet(() -> nativeMutationCount() == 0
                        ? "none"
                        : "cuda-image-sampler-fail-closed-native-mutation-enabled:" + nativeMutationCount());
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerFailClosedContract"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerFailClosedContract");
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler fail-closed contract: ").append(status()).append('\n');
        builder.append("Components: ").append(componentReadyCount()).append('/').append(components().size()).append(" ready").append('\n');
        builder.append("Planned native descriptors: ").append(plannedNativeDescriptorCount()).append('\n');
        builder.append("Planned object requests: ").append(plannedObjectRequestCount()).append('\n');
        builder.append("Planned runtime kernel slots: ").append(plannedRuntimeKernelParameterSlotCount()).append('\n');
        builder.append("Native mutation count: ").append(nativeMutationCount()).append('\n');
        builder.append("Runtime binding kernel slots: ").append(runtimeBindingKernelParameterSlotCount()).append('\n');
        builder.append("Object creation calls enabled: ").append(objectCreationCallEnabledCount()).append('\n');
        builder.append("Native descriptors available: ").append(nativeDescriptorAvailableCount()).append('\n');
        builder.append("Native descriptor addresses present: ").append(nativeDescriptorAddressPresentCount()).append('\n');
        builder.append("Native descriptor writes enabled: ").append(nativeDescriptorWriteEnabledCount()).append('\n');
        builder.append("Object handles available: ").append(objectHandleAvailableCount()).append('\n');
        builder.append("Active native descriptors: ").append(activeNativeDescriptorCount()).append('\n');
        builder.append("Active objects: ").append(activeObjectCount()).append('\n');
        builder.append("First blocker: ").append(firstBlocker()).append('\n');
        builder.append('\n').append("Components:").append('\n');
        for (Component component : components()) {
            builder.append("- ")
                    .append(component.key())
                    .append(": status=")
                    .append(component.status())
                    .append(", firstBlocker=")
                    .append(component.firstBlocker())
                    .append('\n');
        }
        return builder.toString();
    }

    private void putFields(Map<String, String> fields, String prefix) {
        List<Component> components = components();
        fields.put(prefix + ".present", "true");
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".ready", Boolean.toString(ready()));
        fields.put(prefix + ".component.count", Integer.toString(components.size()));
        fields.put(prefix + ".component.ready.count", Long.toString(componentReadyCount()));
        fields.put(prefix + ".component.blocked.count", Long.toString(componentBlockedCount()));
        fields.put(prefix + ".plannedNativeDescriptor.count", Long.toString(plannedNativeDescriptorCount()));
        fields.put(prefix + ".plannedObjectRequest.count", Long.toString(plannedObjectRequestCount()));
        fields.put(prefix + ".plannedRuntimeKernelParameterSlot.count", Long.toString(plannedRuntimeKernelParameterSlotCount()));
        fields.put(prefix + ".nativeMutation.count", Long.toString(nativeMutationCount()));
        fields.put(prefix + ".runtimeBinding.kernelParameterSlot.count", Long.toString(runtimeBindingKernelParameterSlotCount()));
        fields.put(prefix + ".objectCreationCall.enabled.count", Long.toString(objectCreationCallEnabledCount()));
        fields.put(prefix + ".nativeDescriptor.available.count", Long.toString(nativeDescriptorAvailableCount()));
        fields.put(prefix + ".nativeDescriptor.address.present.count", Long.toString(nativeDescriptorAddressPresentCount()));
        fields.put(prefix + ".nativeDescriptorWrite.enabled.count", Long.toString(nativeDescriptorWriteEnabledCount()));
        fields.put(prefix + ".objectHandle.available.count", Long.toString(objectHandleAvailableCount()));
        fields.put(prefix + ".activeNativeDescriptor.count", Long.toString(activeNativeDescriptorCount()));
        fields.put(prefix + ".activeObject.count", Long.toString(activeObjectCount()));
        fields.put(prefix + ".nativeDescriptorAllocation.enabled", "false");
        fields.put(prefix + ".nativeDescriptorWrite.enabled", "false");
        fields.put(prefix + ".objectCreation.enabled", "false");
        fields.put(prefix + ".objectBinding.enabled", "false");
        fields.put(prefix + ".kernelParameterWrite.enabled", "false");
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < components.size(); index++) {
            fields.putAll(components.get(index).artifactFields(prefix + ".component." + index));
        }
    }

    private static Component component(String key, String status, boolean ready, String firstBlocker) {
        return new Component(key, status, ready, firstBlocker);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
