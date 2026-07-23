package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardware-free contract for future CUDA native descriptor field-write transactions.
 */
public record CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport(List<Case> cases) {

    public record Case(
            String key,
            String expectedTransactionStatus,
            String expectedFirstBlocker,
            int expectedResourceDescriptorWrites,
            int expectedTextureDescriptorWrites,
            int expectedResourceFieldWrites,
            int expectedTextureFieldWrites,
            CudaImageSamplerNativeDescriptorEncodingTransactionPlan plan
    ) {
        public Case {
            key = normalize(key, "unknown");
            expectedTransactionStatus = normalize(expectedTransactionStatus, "blocked");
            expectedFirstBlocker = normalize(expectedFirstBlocker, "none");
            expectedResourceDescriptorWrites = Math.max(0, expectedResourceDescriptorWrites);
            expectedTextureDescriptorWrites = Math.max(0, expectedTextureDescriptorWrites);
            expectedResourceFieldWrites = Math.max(0, expectedResourceFieldWrites);
            expectedTextureFieldWrites = Math.max(0, expectedTextureFieldWrites);
            plan = plan == null ? CudaImageSamplerNativeDescriptorEncodingTransactionPlan.empty() : plan;
        }

        public boolean ready() {
            int expectedDescriptorWrites = expectedResourceDescriptorWrites + expectedTextureDescriptorWrites;
            int expectedFieldWrites = expectedResourceFieldWrites + expectedTextureFieldWrites;
            return expectedTransactionStatus.equals(plan.status())
                    && expectedFirstBlocker.equals(plan.firstBlocker())
                    && expectedResourceDescriptorWrites == plan.resourceDescriptorWriteCount()
                    && expectedTextureDescriptorWrites == plan.textureDescriptorWriteCount()
                    && expectedDescriptorWrites == plan.descriptorWriteCount()
                    && expectedResourceFieldWrites == plan.resourceFieldWriteCount()
                    && expectedTextureFieldWrites == plan.textureFieldWriteCount()
                    && expectedFieldWrites == plan.fieldWriteCount()
                    && expectedDescriptorWrites == plan.ownerPresentCount()
                    && plan.ownerActiveCount() == 0
                    && plan.nativeAddressPresentCount() == 0
                    && plan.nativeWriteEnabledCount() == 0
                    && plan.sdkStructByteEncodingEnabledCount() == 0
                    && plan.activeNativeDescriptorCount() == 0;
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public String firstBlocker() {
            if (ready()) {
                return "none";
            }
            if (!expectedTransactionStatus.equals(plan.status())) {
                return "cuda-image-sampler-native-descriptor-encoding-transaction-status-mismatch:" + key + ':' + plan.status();
            }
            if (!expectedFirstBlocker.equals(plan.firstBlocker())) {
                return "cuda-image-sampler-native-descriptor-encoding-transaction-blocker-mismatch:" + key + ':' + plan.firstBlocker();
            }
            if (expectedResourceDescriptorWrites != plan.resourceDescriptorWriteCount()) {
                return "cuda-image-sampler-native-resource-descriptor-write-count-mismatch:" + key + ':' + plan.resourceDescriptorWriteCount();
            }
            if (expectedTextureDescriptorWrites != plan.textureDescriptorWriteCount()) {
                return "cuda-image-sampler-native-texture-descriptor-write-count-mismatch:" + key + ':' + plan.textureDescriptorWriteCount();
            }
            int expectedDescriptorWrites = expectedResourceDescriptorWrites + expectedTextureDescriptorWrites;
            if (expectedDescriptorWrites != plan.descriptorWriteCount()) {
                return "cuda-image-sampler-native-descriptor-write-count-mismatch:" + key + ':' + plan.descriptorWriteCount();
            }
            if (expectedResourceFieldWrites != plan.resourceFieldWriteCount()) {
                return "cuda-image-sampler-native-resource-field-write-count-mismatch:" + key + ':' + plan.resourceFieldWriteCount();
            }
            if (expectedTextureFieldWrites != plan.textureFieldWriteCount()) {
                return "cuda-image-sampler-native-texture-field-write-count-mismatch:" + key + ':' + plan.textureFieldWriteCount();
            }
            int expectedFieldWrites = expectedResourceFieldWrites + expectedTextureFieldWrites;
            if (expectedFieldWrites != plan.fieldWriteCount()) {
                return "cuda-image-sampler-native-field-write-count-mismatch:" + key + ':' + plan.fieldWriteCount();
            }
            if (expectedDescriptorWrites != plan.ownerPresentCount()) {
                return "cuda-image-sampler-native-descriptor-write-owner-count-mismatch:" + key + ':' + plan.ownerPresentCount();
            }
            if (plan.ownerActiveCount() != 0) {
                return "cuda-image-sampler-native-descriptor-write-owner-active-unexpected:" + key + ':' + plan.ownerActiveCount();
            }
            if (plan.nativeAddressPresentCount() != 0) {
                return "cuda-image-sampler-native-descriptor-write-address-present-unexpected:" + key + ':' + plan.nativeAddressPresentCount();
            }
            if (plan.nativeWriteEnabledCount() != 0) {
                return "cuda-image-sampler-native-descriptor-write-native-write-enabled:" + key + ':' + plan.nativeWriteEnabledCount();
            }
            if (plan.sdkStructByteEncodingEnabledCount() != 0) {
                return "cuda-image-sampler-native-descriptor-write-sdk-byte-encoding-enabled:" + key + ':' + plan.sdkStructByteEncodingEnabledCount();
            }
            if (plan.activeNativeDescriptorCount() != 0) {
                return "cuda-image-sampler-active-native-descriptors-unexpected:" + key + ':' + plan.activeNativeDescriptorCount();
            }
            return "cuda-image-sampler-native-descriptor-encoding-transaction-case-not-ready:" + key;
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport.case"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".key", key);
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".expected.transaction.status", expectedTransactionStatus);
            fields.put(normalizedPrefix + ".expected.firstBlocker", expectedFirstBlocker);
            fields.put(normalizedPrefix + ".expected.resourceDescriptorWrite.count", Integer.toString(expectedResourceDescriptorWrites));
            fields.put(normalizedPrefix + ".expected.textureDescriptorWrite.count", Integer.toString(expectedTextureDescriptorWrites));
            fields.put(normalizedPrefix + ".expected.descriptorWrite.count", Integer.toString(expectedResourceDescriptorWrites + expectedTextureDescriptorWrites));
            fields.put(normalizedPrefix + ".expected.resourceFieldWrite.count", Integer.toString(expectedResourceFieldWrites));
            fields.put(normalizedPrefix + ".expected.textureFieldWrite.count", Integer.toString(expectedTextureFieldWrites));
            fields.put(normalizedPrefix + ".expected.fieldWrite.count", Integer.toString(expectedResourceFieldWrites + expectedTextureFieldWrites));
            fields.put(normalizedPrefix + ".actual.transaction.status", plan.status());
            fields.put(normalizedPrefix + ".actual.firstBlocker", plan.firstBlocker());
            fields.putAll(plan.artifactFields(normalizedPrefix + ".plan"));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public static CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport inspectBuiltIns() {
        return new CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport(
                CudaImageSamplerNativeDescriptorEncodingPlanReport.inspectBuiltIns().cases().stream()
                        .map(CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport::runCase)
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

    public long encodingPlanReadyCount() {
        return cases.stream().filter(testCase -> "ready".equals(testCase.plan().nativeDescriptorEncodingPlanStatus())).count();
    }

    public long encodingPlanBlockedCount() {
        return cases.stream().filter(testCase -> "blocked".equals(testCase.plan().nativeDescriptorEncodingPlanStatus())).count();
    }

    public long allocationTransactionReadyCount() {
        return cases.stream().filter(testCase -> "ready".equals(testCase.plan().nativeDescriptorAllocationTransactionPlanStatus())).count();
    }

    public long allocationTransactionBlockedCount() {
        return cases.stream().filter(testCase -> "blocked".equals(testCase.plan().nativeDescriptorAllocationTransactionPlanStatus())).count();
    }

    public long transactionReadyCount() {
        return cases.stream().filter(testCase -> "ready".equals(testCase.plan().status())).count();
    }

    public long transactionBlockedCount() {
        return cases.stream().filter(testCase -> "blocked".equals(testCase.plan().status())).count();
    }

    public long entryCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().entries().size()).sum();
    }

    public long descriptorWriteCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().descriptorWriteCount()).sum();
    }

    public long resourceDescriptorWriteCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().resourceDescriptorWriteCount()).sum();
    }

    public long textureDescriptorWriteCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().textureDescriptorWriteCount()).sum();
    }

    public long resourceFieldWriteCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().resourceFieldWriteCount()).sum();
    }

    public long textureFieldWriteCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().textureFieldWriteCount()).sum();
    }

    public long fieldWriteCount() {
        return resourceFieldWriteCount() + textureFieldWriteCount();
    }

    public long ownerPresentCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().ownerPresentCount()).sum();
    }

    public long ownerActiveCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().ownerActiveCount()).sum();
    }

    public long nativeAddressPresentCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().nativeAddressPresentCount()).sum();
    }

    public long nativeWriteEnabledCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().nativeWriteEnabledCount()).sum();
    }

    public long sdkStructByteEncodingEnabledCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().sdkStructByteEncodingEnabledCount()).sum();
    }

    public long activeNativeDescriptorCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().activeNativeDescriptorCount()).sum();
    }

    public String firstBlocker() {
        if (cases.isEmpty()) {
            return "cuda-image-sampler-native-descriptor-encoding-transaction-plan-cases-missing";
        }
        return cases.stream()
                .filter(testCase -> !testCase.ready())
                .map(Case::firstBlocker)
                .findFirst()
                .orElse("none");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlanReport");
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler native descriptor encoding transaction plan: ").append(status()).append('\n');
        builder.append("Cases: ").append(caseReadyCount()).append('/').append(cases.size()).append(" ready").append('\n');
        builder.append("Encoding plans: ready=").append(encodingPlanReadyCount()).append(", blocked=").append(encodingPlanBlockedCount()).append('\n');
        builder.append("Allocation transactions: ready=").append(allocationTransactionReadyCount()).append(", blocked=").append(allocationTransactionBlockedCount()).append('\n');
        builder.append("Transactions: ready=").append(transactionReadyCount()).append(", blocked=").append(transactionBlockedCount()).append('\n');
        builder.append("Descriptor writes: resource=").append(resourceDescriptorWriteCount())
                .append(", texture=").append(textureDescriptorWriteCount())
                .append(", total=").append(descriptorWriteCount()).append('\n');
        builder.append("Field writes: resource=").append(resourceFieldWriteCount())
                .append(", texture=").append(textureFieldWriteCount())
                .append(", total=").append(fieldWriteCount()).append('\n');
        builder.append("Owners present: ").append(ownerPresentCount()).append(", active=").append(ownerActiveCount()).append('\n');
        builder.append("Native addresses present: ").append(nativeAddressPresentCount()).append('\n');
        builder.append("Native writes enabled: ").append(nativeWriteEnabledCount()).append('\n');
        builder.append("SDK byte encoding enabled: ").append(sdkStructByteEncodingEnabledCount()).append('\n');
        builder.append("First blocker: ").append(firstBlocker()).append('\n');
        builder.append('\n').append("Cases:").append('\n');
        for (Case testCase : cases) {
            builder.append("- ")
                    .append(testCase.key())
                    .append(": case=")
                    .append(testCase.status())
                    .append(", transaction=")
                    .append(testCase.plan().status())
                    .append(", firstBlocker=")
                    .append(testCase.plan().firstBlocker())
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
        fields.put(prefix + ".encodingPlan.ready.count", Long.toString(encodingPlanReadyCount()));
        fields.put(prefix + ".encodingPlan.blocked.count", Long.toString(encodingPlanBlockedCount()));
        fields.put(prefix + ".allocationTransaction.ready.count", Long.toString(allocationTransactionReadyCount()));
        fields.put(prefix + ".allocationTransaction.blocked.count", Long.toString(allocationTransactionBlockedCount()));
        fields.put(prefix + ".transaction.ready.count", Long.toString(transactionReadyCount()));
        fields.put(prefix + ".transaction.blocked.count", Long.toString(transactionBlockedCount()));
        fields.put(prefix + ".entry.count", Long.toString(entryCount()));
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
        fields.put(prefix + ".activeNativeDescriptor.count", Long.toString(activeNativeDescriptorCount()));
        fields.put(prefix + ".writeTransactionApply.enabled", "false");
        fields.put(prefix + ".nativeMemoryAllocation.enabled", "false");
        fields.put(prefix + ".sdkStructByteEncoding.enabled", "false");
        fields.put(prefix + ".objectCreation.enabled", "false");
        fields.put(prefix + ".runtimeBinding.enabled", "false");
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < cases.size(); index++) {
            fields.putAll(cases.get(index).artifactFields(prefix + ".case." + index));
        }
    }

    private static Case runCase(CudaImageSamplerNativeDescriptorEncodingPlanReport.Case encodingCase) {
        CudaImageSamplerNativeDescriptorAllocationTransactionPlan allocationTransactionPlan =
                CudaImageSamplerNativeDescriptorAllocationTransactionPlan.from(
                        CudaImageSamplerNativeDescriptorAllocationPreflight.from(encodingCase.plan())
                );
        CudaImageSamplerNativeDescriptorEncodingTransactionPlan plan =
                CudaImageSamplerNativeDescriptorEncodingTransactionPlan.from(encodingCase.plan(), allocationTransactionPlan);
        boolean hasWrites = plan.descriptorWriteCount() > 0;
        return new Case(
                encodingCase.key(),
                "blocked",
                hasWrites ? firstTransactionBlocker(plan) : encodingCase.plan().firstBlocker(),
                hasWrites ? (int) plan.resourceDescriptorWriteCount() : 0,
                hasWrites ? (int) plan.textureDescriptorWriteCount() : 0,
                hasWrites ? (int) plan.resourceFieldWriteCount() : 0,
                hasWrites ? (int) plan.textureFieldWriteCount() : 0,
                plan
        );
    }

    private static String firstTransactionBlocker(CudaImageSamplerNativeDescriptorEncodingTransactionPlan plan) {
        return plan.entries().stream()
                .flatMap(entry -> entry.descriptorWrites().stream())
                .filter(write -> !"none".equals(write.firstBlocker()))
                .findFirst()
                .map(CudaImageSamplerNativeDescriptorEncodingTransactionPlan.DescriptorWrite::firstBlocker)
                .orElse("cuda-image-sampler-native-descriptor-encoding-transaction-disabled");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
