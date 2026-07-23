package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardware-free contract for CUDA image/sampler native object-preparation preflight.
 */
public record CudaImageSamplerNativeObjectPreparationPreflightReport(List<Case> cases) {

    public record Case(
            String key,
            String expectedPreflightStatus,
            String expectedFirstBlocker,
            int expectedObjectPreparations,
            int expectedTexturePreparations,
            int expectedSurfacePreparations,
            int expectedFoldedSamplers,
            int expectedResourceDescriptors,
            int expectedTextureDescriptors,
            int expectedCreateFunctions,
            int expectedDestroyFunctions,
            CudaImageSamplerNativeObjectPreparationPreflight preflight
    ) {
        public Case {
            key = normalize(key, "unknown");
            expectedPreflightStatus = normalize(expectedPreflightStatus, "blocked");
            expectedFirstBlocker = normalize(expectedFirstBlocker, "none");
            expectedObjectPreparations = Math.max(0, expectedObjectPreparations);
            expectedTexturePreparations = Math.max(0, expectedTexturePreparations);
            expectedSurfacePreparations = Math.max(0, expectedSurfacePreparations);
            expectedFoldedSamplers = Math.max(0, expectedFoldedSamplers);
            expectedResourceDescriptors = Math.max(0, expectedResourceDescriptors);
            expectedTextureDescriptors = Math.max(0, expectedTextureDescriptors);
            expectedCreateFunctions = Math.max(0, expectedCreateFunctions);
            expectedDestroyFunctions = Math.max(0, expectedDestroyFunctions);
            preflight = preflight == null ? CudaImageSamplerNativeObjectPreparationPreflight.empty() : preflight;
        }

        public boolean ready() {
            return expectedPreflightStatus.equals(preflight.status())
                    && expectedFirstBlocker.equals(preflight.firstBlocker())
                    && expectedObjectPreparations == preflight.objectPreparationCount()
                    && expectedTexturePreparations == preflight.textureObjectPreparationCount()
                    && expectedSurfacePreparations == preflight.surfaceObjectPreparationCount()
                    && expectedFoldedSamplers == preflight.foldedSamplerPreparationCount()
                    && expectedResourceDescriptors == preflight.resourceDescriptorRequiredCount()
                    && expectedTextureDescriptors == preflight.textureDescriptorRequiredCount()
                    && expectedResourceDescriptors == preflight.resourceDescriptorOwnerPresentCount()
                    && expectedTextureDescriptors == preflight.textureDescriptorOwnerPresentCount()
                    && expectedResourceDescriptors == preflight.resourceDescriptorWritePlannedCount()
                    && expectedTextureDescriptors == preflight.textureDescriptorWritePlannedCount()
                    && expectedCreateFunctions == preflight.createFunctionRequiredCount()
                    && expectedCreateFunctions == preflight.createFunctionAvailableCount()
                    && expectedDestroyFunctions == preflight.destroyFunctionRequiredCount()
                    && expectedDestroyFunctions == preflight.destroyFunctionAvailableCount()
                    && preflight.resourceDescriptorAvailableCount() == 0
                    && preflight.resourceDescriptorNativeAddressPresentCount() == 0
                    && preflight.resourceDescriptorNativeWriteEnabledCount() == 0
                    && preflight.textureDescriptorAvailableCount() == 0
                    && preflight.textureDescriptorNativeAddressPresentCount() == 0
                    && preflight.textureDescriptorNativeWriteEnabledCount() == 0
                    && preflight.objectHandleAvailableCount() == 0
                    && preflight.objectOwnershipAvailableCount() == 0
                    && preflight.objectCreationCallEnabledCount() == 0
                    && preflight.activeNativeDescriptorCount() == 0
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
                return "cuda-image-sampler-native-object-preparation-status-mismatch:" + key + ':' + preflight.status();
            }
            if (!expectedFirstBlocker.equals(preflight.firstBlocker())) {
                return "cuda-image-sampler-native-object-preparation-blocker-mismatch:" + key + ':' + preflight.firstBlocker();
            }
            if (expectedObjectPreparations != preflight.objectPreparationCount()) {
                return "cuda-image-sampler-native-object-preparation-count-mismatch:" + key + ':' + preflight.objectPreparationCount();
            }
            if (expectedTexturePreparations != preflight.textureObjectPreparationCount()) {
                return "cuda-image-sampler-native-texture-preparation-count-mismatch:" + key + ':' + preflight.textureObjectPreparationCount();
            }
            if (expectedSurfacePreparations != preflight.surfaceObjectPreparationCount()) {
                return "cuda-image-sampler-native-surface-preparation-count-mismatch:" + key + ':' + preflight.surfaceObjectPreparationCount();
            }
            if (expectedFoldedSamplers != preflight.foldedSamplerPreparationCount()) {
                return "cuda-image-sampler-native-folded-sampler-count-mismatch:" + key + ':' + preflight.foldedSamplerPreparationCount();
            }
            if (expectedResourceDescriptors != preflight.resourceDescriptorRequiredCount()) {
                return "cuda-image-sampler-native-resource-descriptor-count-mismatch:" + key + ':' + preflight.resourceDescriptorRequiredCount();
            }
            if (expectedResourceDescriptors != preflight.resourceDescriptorOwnerPresentCount()) {
                return "cuda-image-sampler-native-resource-descriptor-owner-count-mismatch:" + key + ':' + preflight.resourceDescriptorOwnerPresentCount();
            }
            if (expectedResourceDescriptors != preflight.resourceDescriptorWritePlannedCount()) {
                return "cuda-image-sampler-native-resource-descriptor-write-plan-count-mismatch:" + key + ':' + preflight.resourceDescriptorWritePlannedCount();
            }
            if (expectedTextureDescriptors != preflight.textureDescriptorRequiredCount()) {
                return "cuda-image-sampler-native-texture-descriptor-count-mismatch:" + key + ':' + preflight.textureDescriptorRequiredCount();
            }
            if (expectedTextureDescriptors != preflight.textureDescriptorOwnerPresentCount()) {
                return "cuda-image-sampler-native-texture-descriptor-owner-count-mismatch:" + key + ':' + preflight.textureDescriptorOwnerPresentCount();
            }
            if (expectedTextureDescriptors != preflight.textureDescriptorWritePlannedCount()) {
                return "cuda-image-sampler-native-texture-descriptor-write-plan-count-mismatch:" + key + ':' + preflight.textureDescriptorWritePlannedCount();
            }
            if (expectedCreateFunctions != preflight.createFunctionRequiredCount()
                    || expectedCreateFunctions != preflight.createFunctionAvailableCount()) {
                return "cuda-image-sampler-native-create-function-count-mismatch:" + key + ':'
                        + preflight.createFunctionRequiredCount() + '/' + preflight.createFunctionAvailableCount();
            }
            if (expectedDestroyFunctions != preflight.destroyFunctionRequiredCount()
                    || expectedDestroyFunctions != preflight.destroyFunctionAvailableCount()) {
                return "cuda-image-sampler-native-destroy-function-count-mismatch:" + key + ':'
                        + preflight.destroyFunctionRequiredCount() + '/' + preflight.destroyFunctionAvailableCount();
            }
            if (preflight.resourceDescriptorAvailableCount() != 0) {
                return "cuda-image-sampler-native-resource-descriptor-available-unexpected:" + key + ':' + preflight.resourceDescriptorAvailableCount();
            }
            if (preflight.resourceDescriptorNativeAddressPresentCount() != 0) {
                return "cuda-image-sampler-native-resource-descriptor-address-present-unexpected:" + key + ':' + preflight.resourceDescriptorNativeAddressPresentCount();
            }
            if (preflight.resourceDescriptorNativeWriteEnabledCount() != 0) {
                return "cuda-image-sampler-native-resource-descriptor-write-enabled:" + key + ':' + preflight.resourceDescriptorNativeWriteEnabledCount();
            }
            if (preflight.textureDescriptorAvailableCount() != 0) {
                return "cuda-image-sampler-native-texture-descriptor-available-unexpected:" + key + ':' + preflight.textureDescriptorAvailableCount();
            }
            if (preflight.textureDescriptorNativeAddressPresentCount() != 0) {
                return "cuda-image-sampler-native-texture-descriptor-address-present-unexpected:" + key + ':' + preflight.textureDescriptorNativeAddressPresentCount();
            }
            if (preflight.textureDescriptorNativeWriteEnabledCount() != 0) {
                return "cuda-image-sampler-native-texture-descriptor-write-enabled:" + key + ':' + preflight.textureDescriptorNativeWriteEnabledCount();
            }
            if (preflight.objectHandleAvailableCount() != 0) {
                return "cuda-image-sampler-native-object-handle-available-unexpected:" + key + ':' + preflight.objectHandleAvailableCount();
            }
            if (preflight.objectOwnershipAvailableCount() != 0) {
                return "cuda-image-sampler-native-object-ownership-available-unexpected:" + key + ':' + preflight.objectOwnershipAvailableCount();
            }
            if (preflight.objectCreationCallEnabledCount() != 0) {
                return "cuda-image-sampler-native-object-creation-call-enabled:" + key + ':' + preflight.objectCreationCallEnabledCount();
            }
            if (preflight.activeNativeDescriptorCount() != 0) {
                return "cuda-image-sampler-native-descriptors-active-unexpected:" + key + ':' + preflight.activeNativeDescriptorCount();
            }
            if (preflight.activeObjectCount() != 0) {
                return "cuda-image-sampler-native-objects-active-unexpected:" + key + ':' + preflight.activeObjectCount();
            }
            return "cuda-image-sampler-native-object-preparation-case-not-ready:" + key;
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport.case"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".key", key);
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".expected.preflight.status", expectedPreflightStatus);
            fields.put(normalizedPrefix + ".expected.firstBlocker", expectedFirstBlocker);
            fields.put(normalizedPrefix + ".expected.objectPreparation.count", Integer.toString(expectedObjectPreparations));
            fields.put(normalizedPrefix + ".expected.textureObjectPreparation.count", Integer.toString(expectedTexturePreparations));
            fields.put(normalizedPrefix + ".expected.surfaceObjectPreparation.count", Integer.toString(expectedSurfacePreparations));
            fields.put(normalizedPrefix + ".expected.foldedSamplerPreparation.count", Integer.toString(expectedFoldedSamplers));
            fields.put(normalizedPrefix + ".expected.resourceDescriptor.required.count", Integer.toString(expectedResourceDescriptors));
            fields.put(normalizedPrefix + ".expected.textureDescriptor.required.count", Integer.toString(expectedTextureDescriptors));
            fields.put(normalizedPrefix + ".expected.createFunction.required.count", Integer.toString(expectedCreateFunctions));
            fields.put(normalizedPrefix + ".expected.destroyFunction.required.count", Integer.toString(expectedDestroyFunctions));
            fields.put(normalizedPrefix + ".actual.preflight.status", preflight.status());
            fields.put(normalizedPrefix + ".actual.firstBlocker", preflight.firstBlocker());
            fields.putAll(preflight.artifactFields(normalizedPrefix + ".preflight"));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerNativeObjectPreparationPreflightReport {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public static CudaImageSamplerNativeObjectPreparationPreflightReport inspectBuiltIns() {
        return new CudaImageSamplerNativeObjectPreparationPreflightReport(
                CudaImageSamplerNativeDescriptorEncodingPlanReport.inspectBuiltIns().cases().stream()
                        .map(CudaImageSamplerNativeObjectPreparationPreflightReport::runCase)
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
        return cases.stream().filter(testCase -> "ready".equals(testCase.preflight().objectCreationRequestPlanStatus())).count();
    }

    public long planBlockedCount() {
        return cases.stream().filter(testCase -> "blocked".equals(testCase.preflight().objectCreationRequestPlanStatus())).count();
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

    public long objectPreparationCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().objectPreparationCount()).sum();
    }

    public long textureObjectPreparationCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().textureObjectPreparationCount()).sum();
    }

    public long surfaceObjectPreparationCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().surfaceObjectPreparationCount()).sum();
    }

    public long foldedSamplerPreparationCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().foldedSamplerPreparationCount()).sum();
    }

    public long resourceDescriptorRequiredCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().resourceDescriptorRequiredCount()).sum();
    }

    public long resourceDescriptorAvailableCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().resourceDescriptorAvailableCount()).sum();
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

    public long textureDescriptorAvailableCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().textureDescriptorAvailableCount()).sum();
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

    public long createFunctionRequiredCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().createFunctionRequiredCount()).sum();
    }

    public long createFunctionAvailableCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().createFunctionAvailableCount()).sum();
    }

    public long destroyFunctionRequiredCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().destroyFunctionRequiredCount()).sum();
    }

    public long destroyFunctionAvailableCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().destroyFunctionAvailableCount()).sum();
    }

    public long objectHandleRequiredCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().objectHandleRequiredCount()).sum();
    }

    public long objectHandleAvailableCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().objectHandleAvailableCount()).sum();
    }

    public long objectOwnershipAvailableCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().objectOwnershipAvailableCount()).sum();
    }

    public long objectCreationCallEnabledCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().objectCreationCallEnabledCount()).sum();
    }

    public long activeNativeDescriptorCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().activeNativeDescriptorCount()).sum();
    }

    public long activeObjectCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().activeObjectCount()).sum();
    }

    public String firstBlocker() {
        if (cases.isEmpty()) {
            return "cuda-image-sampler-native-object-preparation-preflight-cases-missing";
        }
        return cases.stream()
                .filter(testCase -> !testCase.ready())
                .map(Case::firstBlocker)
                .findFirst()
                .orElse("none");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerNativeObjectPreparationPreflightReport");
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler native object preparation preflight: ").append(status()).append('\n');
        builder.append("Cases: ").append(caseReadyCount()).append('/').append(cases.size()).append(" ready").append('\n');
        builder.append("Plans: ready=").append(planReadyCount()).append(", blocked=").append(planBlockedCount()).append('\n');
        builder.append("Preflights: ready=").append(preflightReadyCount()).append(", blocked=").append(preflightBlockedCount()).append('\n');
        builder.append("Entries: ").append(entryCount()).append('\n');
        builder.append("Object preparations: texture=").append(textureObjectPreparationCount())
                .append(", surface=").append(surfaceObjectPreparationCount())
                .append(", total=").append(objectPreparationCount()).append('\n');
        builder.append("Native descriptors: resourceRequired=").append(resourceDescriptorRequiredCount())
                .append(", resourceAvailable=").append(resourceDescriptorAvailableCount())
                .append(", resourceOwners=").append(resourceDescriptorOwnerPresentCount())
                .append(", textureRequired=").append(textureDescriptorRequiredCount())
                .append(", textureAvailable=").append(textureDescriptorAvailableCount())
                .append(", textureOwners=").append(textureDescriptorOwnerPresentCount()).append('\n');
        builder.append("Descriptor writes planned: resource=").append(resourceDescriptorWritePlannedCount())
                .append(", texture=").append(textureDescriptorWritePlannedCount())
                .append(", nativeWritesEnabled=")
                .append(resourceDescriptorNativeWriteEnabledCount() + textureDescriptorNativeWriteEnabledCount())
                .append('\n');
        builder.append("Object handles: required=").append(objectHandleRequiredCount())
                .append(", available=").append(objectHandleAvailableCount()).append('\n');
        builder.append("Object creation calls enabled: ").append(objectCreationCallEnabledCount()).append('\n');
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
        fields.put(prefix + ".activeNativeDescriptor.count", Long.toString(activeNativeDescriptorCount()));
        fields.put(prefix + ".activeObject.count", Long.toString(activeObjectCount()));
        fields.put(prefix + ".nativeDescriptorAllocation.enabled", "false");
        fields.put(prefix + ".objectCreationCall.enabled", "false");
        fields.put(prefix + ".objectOwnership.enabled", "false");
        fields.put(prefix + ".runtimeBinding.enabled", "false");
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < cases.size(); index++) {
            fields.putAll(cases.get(index).artifactFields(prefix + ".case." + index));
        }
    }

    private static Case runCase(CudaImageSamplerNativeDescriptorEncodingPlanReport.Case encodingCase) {
        CudaImageSamplerObjectCreationRequestPlan requestPlan = CudaImageSamplerObjectCreationRequestPlan.from(encodingCase.plan());
        CudaImageSamplerNativeDescriptorEncodingTransactionPlan transactionPlan =
                CudaImageSamplerNativeDescriptorEncodingTransactionPlan.from(
                        encodingCase.plan(),
                        CudaImageSamplerNativeDescriptorAllocationTransactionPlan.from(
                                CudaImageSamplerNativeDescriptorAllocationPreflight.from(encodingCase.plan())
                        )
                );
        CudaImageSamplerNativeObjectPreparationPreflight preflight =
                CudaImageSamplerNativeObjectPreparationPreflight.from(requestPlan, transactionPlan);
        boolean readyPlan = "ready".equals(requestPlan.status());
        return new Case(
                encodingCase.key(),
                "blocked",
                readyPlan ? preflight.firstBlocker() : requestPlan.firstBlocker(),
                readyPlan ? 16 : 0,
                readyPlan ? 8 : 0,
                readyPlan ? 8 : 0,
                readyPlan ? 1 : 0,
                readyPlan ? 16 : 0,
                readyPlan ? 8 : 0,
                readyPlan ? 16 : 0,
                readyPlan ? 16 : 0,
                preflight
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
