package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardware-free contract for future CUDA native descriptor allocation transactions.
 */
public record CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport(List<Case> cases) {

    public record Case(
            String key,
            String expectedTransactionStatus,
            String expectedFirstBlocker,
            int expectedResourceDescriptorOwners,
            int expectedTextureDescriptorOwners,
            CudaImageSamplerNativeDescriptorAllocationTransactionPlan plan
    ) {
        public Case {
            key = normalize(key, "unknown");
            expectedTransactionStatus = normalize(expectedTransactionStatus, "blocked");
            expectedFirstBlocker = normalize(expectedFirstBlocker, "none");
            expectedResourceDescriptorOwners = Math.max(0, expectedResourceDescriptorOwners);
            expectedTextureDescriptorOwners = Math.max(0, expectedTextureDescriptorOwners);
            plan = plan == null ? CudaImageSamplerNativeDescriptorAllocationTransactionPlan.empty() : plan;
        }

        public boolean ready() {
            int expectedOwners = expectedResourceDescriptorOwners + expectedTextureDescriptorOwners;
            return expectedTransactionStatus.equals(plan.status())
                    && expectedFirstBlocker.equals(plan.firstBlocker())
                    && expectedResourceDescriptorOwners == plan.resourceDescriptorOwnerCount()
                    && expectedTextureDescriptorOwners == plan.textureDescriptorOwnerCount()
                    && expectedOwners == plan.descriptorOwnerCount()
                    && expectedOwners == plan.cleanupPlannedCount()
                    && expectedOwners == plan.rollbackPlannedCount()
                    && plan.activeDescriptorOwnerCount() == 0
                    && plan.nativeAddressPresentCount() == 0
                    && plan.allocationEnabledCount() == 0
                    && plan.cleanupActiveCount() == 0
                    && plan.rollbackActiveCount() == 0
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
                return "cuda-image-sampler-native-descriptor-allocation-transaction-status-mismatch:" + key + ':' + plan.status();
            }
            if (!expectedFirstBlocker.equals(plan.firstBlocker())) {
                return "cuda-image-sampler-native-descriptor-allocation-transaction-blocker-mismatch:" + key + ':' + plan.firstBlocker();
            }
            if (expectedResourceDescriptorOwners != plan.resourceDescriptorOwnerCount()) {
                return "cuda-image-sampler-native-resource-descriptor-owner-count-mismatch:" + key + ':' + plan.resourceDescriptorOwnerCount();
            }
            if (expectedTextureDescriptorOwners != plan.textureDescriptorOwnerCount()) {
                return "cuda-image-sampler-native-texture-descriptor-owner-count-mismatch:" + key + ':' + plan.textureDescriptorOwnerCount();
            }
            int expectedOwners = expectedResourceDescriptorOwners + expectedTextureDescriptorOwners;
            if (expectedOwners != plan.descriptorOwnerCount()) {
                return "cuda-image-sampler-native-descriptor-owner-count-mismatch:" + key + ':' + plan.descriptorOwnerCount();
            }
            if (plan.activeDescriptorOwnerCount() != 0) {
                return "cuda-image-sampler-native-descriptor-owner-active-unexpected:" + key + ':' + plan.activeDescriptorOwnerCount();
            }
            if (plan.nativeAddressPresentCount() != 0) {
                return "cuda-image-sampler-native-descriptor-address-present-unexpected:" + key + ':' + plan.nativeAddressPresentCount();
            }
            if (plan.allocationEnabledCount() != 0) {
                return "cuda-image-sampler-native-descriptor-owner-allocation-enabled:" + key + ':' + plan.allocationEnabledCount();
            }
            if (plan.cleanupActiveCount() != 0) {
                return "cuda-image-sampler-native-descriptor-owner-cleanup-active-unexpected:" + key + ':' + plan.cleanupActiveCount();
            }
            if (plan.rollbackActiveCount() != 0) {
                return "cuda-image-sampler-native-descriptor-owner-rollback-active-unexpected:" + key + ':' + plan.rollbackActiveCount();
            }
            if (plan.activeNativeDescriptorCount() != 0) {
                return "cuda-image-sampler-active-native-descriptors-unexpected:" + key + ':' + plan.activeNativeDescriptorCount();
            }
            return "cuda-image-sampler-native-descriptor-allocation-transaction-case-not-ready:" + key;
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport.case"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".key", key);
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".expected.transaction.status", expectedTransactionStatus);
            fields.put(normalizedPrefix + ".expected.firstBlocker", expectedFirstBlocker);
            fields.put(normalizedPrefix + ".expected.resourceDescriptorOwner.count", Integer.toString(expectedResourceDescriptorOwners));
            fields.put(normalizedPrefix + ".expected.textureDescriptorOwner.count", Integer.toString(expectedTextureDescriptorOwners));
            fields.put(normalizedPrefix + ".expected.descriptorOwner.count", Integer.toString(expectedResourceDescriptorOwners + expectedTextureDescriptorOwners));
            fields.put(normalizedPrefix + ".actual.transaction.status", plan.status());
            fields.put(normalizedPrefix + ".actual.firstBlocker", plan.firstBlocker());
            fields.putAll(plan.artifactFields(normalizedPrefix + ".plan"));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public static CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport inspectBuiltIns() {
        return new CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport(
                CudaImageSamplerNativeDescriptorAllocationPreflightReport.inspectBuiltIns().cases().stream()
                        .map(CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport::runCase)
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

    public long preflightReadyCount() {
        return cases.stream().filter(testCase -> "ready".equals(testCase.plan().nativeDescriptorAllocationPreflightStatus())).count();
    }

    public long preflightBlockedCount() {
        return cases.stream().filter(testCase -> "blocked".equals(testCase.plan().nativeDescriptorAllocationPreflightStatus())).count();
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

    public long descriptorOwnerCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().descriptorOwnerCount()).sum();
    }

    public long resourceDescriptorOwnerCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().resourceDescriptorOwnerCount()).sum();
    }

    public long textureDescriptorOwnerCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().textureDescriptorOwnerCount()).sum();
    }

    public long activeDescriptorOwnerCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().activeDescriptorOwnerCount()).sum();
    }

    public long nativeAddressPresentCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().nativeAddressPresentCount()).sum();
    }

    public long allocationEnabledCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().allocationEnabledCount()).sum();
    }

    public long cleanupPlannedCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().cleanupPlannedCount()).sum();
    }

    public long cleanupActiveCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().cleanupActiveCount()).sum();
    }

    public long rollbackPlannedCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().rollbackPlannedCount()).sum();
    }

    public long rollbackActiveCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().rollbackActiveCount()).sum();
    }

    public long activeNativeDescriptorCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().activeNativeDescriptorCount()).sum();
    }

    public String firstBlocker() {
        if (cases.isEmpty()) {
            return "cuda-image-sampler-native-descriptor-allocation-transaction-plan-cases-missing";
        }
        return cases.stream()
                .filter(testCase -> !testCase.ready())
                .map(Case::firstBlocker)
                .findFirst()
                .orElse("none");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlanReport");
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler native descriptor allocation transaction plan: ").append(status()).append('\n');
        builder.append("Cases: ").append(caseReadyCount()).append('/').append(cases.size()).append(" ready").append('\n');
        builder.append("Preflights: ready=").append(preflightReadyCount()).append(", blocked=").append(preflightBlockedCount()).append('\n');
        builder.append("Transactions: ready=").append(transactionReadyCount()).append(", blocked=").append(transactionBlockedCount()).append('\n');
        builder.append("Entries: ").append(entryCount()).append('\n');
        builder.append("Descriptor owners: resource=").append(resourceDescriptorOwnerCount())
                .append(", texture=").append(textureDescriptorOwnerCount())
                .append(", total=").append(descriptorOwnerCount())
                .append(", active=").append(activeDescriptorOwnerCount()).append('\n');
        builder.append("Native addresses present: ").append(nativeAddressPresentCount()).append('\n');
        builder.append("Cleanup planned: ").append(cleanupPlannedCount())
                .append(", active=").append(cleanupActiveCount()).append('\n');
        builder.append("Rollback planned: ").append(rollbackPlannedCount())
                .append(", active=").append(rollbackActiveCount()).append('\n');
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
        fields.put(prefix + ".preflight.ready.count", Long.toString(preflightReadyCount()));
        fields.put(prefix + ".preflight.blocked.count", Long.toString(preflightBlockedCount()));
        fields.put(prefix + ".transaction.ready.count", Long.toString(transactionReadyCount()));
        fields.put(prefix + ".transaction.blocked.count", Long.toString(transactionBlockedCount()));
        fields.put(prefix + ".entry.count", Long.toString(entryCount()));
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
        fields.put(prefix + ".activeNativeDescriptor.count", Long.toString(activeNativeDescriptorCount()));
        fields.put(prefix + ".allocationApply.enabled", "false");
        fields.put(prefix + ".nativeMemoryAllocation.enabled", "false");
        fields.put(prefix + ".sdkStructByteEncoding.enabled", "false");
        fields.put(prefix + ".cleanupApply.enabled", "false");
        fields.put(prefix + ".rollbackApply.enabled", "false");
        fields.put(prefix + ".objectCreation.enabled", "false");
        fields.put(prefix + ".runtimeBinding.enabled", "false");
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < cases.size(); index++) {
            fields.putAll(cases.get(index).artifactFields(prefix + ".case." + index));
        }
    }

    private static Case runCase(CudaImageSamplerNativeDescriptorAllocationPreflightReport.Case preflightCase) {
        CudaImageSamplerNativeDescriptorAllocationTransactionPlan plan =
                CudaImageSamplerNativeDescriptorAllocationTransactionPlan.from(preflightCase.preflight());
        boolean hasOwners = plan.descriptorOwnerCount() > 0;
        return new Case(
                preflightCase.key(),
                "blocked",
                hasOwners ? firstTransactionBlocker(plan) : preflightCase.preflight().firstBlocker(),
                hasOwners ? (int) plan.resourceDescriptorOwnerCount() : 0,
                hasOwners ? (int) plan.textureDescriptorOwnerCount() : 0,
                plan
        );
    }

    private static String firstTransactionBlocker(CudaImageSamplerNativeDescriptorAllocationTransactionPlan plan) {
        return plan.entries().stream()
                .filter(entry -> !entry.descriptorOwners().isEmpty())
                .findFirst()
                .map(entry -> "cuda-image-sampler-native-descriptor-allocation-transaction-disabled:"
                        + entry.parameterIndex()
                        + ':'
                        + entry.javaType())
                .orElse("cuda-image-sampler-native-descriptor-allocation-transaction-disabled");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
