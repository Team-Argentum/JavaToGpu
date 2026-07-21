package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardware-free contract for future CUDA native descriptor allocation and ownership planning.
 */
public record CudaImageSamplerNativeDescriptorAllocationPreflightReport(List<Case> cases) {

    public record Case(
            String key,
            String expectedPreflightStatus,
            String expectedFirstBlocker,
            int expectedResourceDescriptorAllocations,
            int expectedTextureDescriptorAllocations,
            CudaImageSamplerNativeDescriptorAllocationPreflight preflight
    ) {
        public Case {
            key = normalize(key, "unknown");
            expectedPreflightStatus = normalize(expectedPreflightStatus, "blocked");
            expectedFirstBlocker = normalize(expectedFirstBlocker, "none");
            expectedResourceDescriptorAllocations = Math.max(0, expectedResourceDescriptorAllocations);
            expectedTextureDescriptorAllocations = Math.max(0, expectedTextureDescriptorAllocations);
            preflight = preflight == null ? CudaImageSamplerNativeDescriptorAllocationPreflight.empty() : preflight;
        }

        public boolean ready() {
            int expectedNativeDescriptors = expectedResourceDescriptorAllocations + expectedTextureDescriptorAllocations;
            return expectedPreflightStatus.equals(preflight.status())
                    && expectedFirstBlocker.equals(preflight.firstBlocker())
                    && expectedResourceDescriptorAllocations == preflight.resourceDescriptorAllocationCount()
                    && expectedTextureDescriptorAllocations == preflight.textureDescriptorAllocationCount()
                    && expectedNativeDescriptors == preflight.plannedNativeDescriptorCount()
                    && expectedNativeDescriptors == preflight.nativeDescriptorOwnershipRequiredCount()
                    && expectedNativeDescriptors == preflight.nativeDescriptorOwnershipPlannedCount()
                    && expectedNativeDescriptors == preflight.cleanupRequiredCount()
                    && expectedNativeDescriptors == preflight.cleanupPlannedCount()
                    && expectedNativeDescriptors == preflight.rollbackRequiredCount()
                    && expectedNativeDescriptors == preflight.rollbackPlannedCount()
                    && preflight.resourceDescriptorAllocatedCount() == 0
                    && preflight.textureDescriptorAllocatedCount() == 0
                    && preflight.allocatedNativeDescriptorCount() == 0
                    && preflight.nativeDescriptorOwnershipActiveCount() == 0
                    && preflight.cleanupActiveCount() == 0
                    && preflight.rollbackActiveCount() == 0
                    && preflight.allocationEnabledCount() == 0
                    && preflight.sdkStructByteEncodingEnabledCount() == 0
                    && preflight.activeNativeDescriptorCount() == 0;
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public String firstBlocker() {
            if (ready()) {
                return "none";
            }
            if (!expectedPreflightStatus.equals(preflight.status())) {
                return "cuda-image-sampler-native-descriptor-allocation-status-mismatch:" + key + ':' + preflight.status();
            }
            if (!expectedFirstBlocker.equals(preflight.firstBlocker())) {
                return "cuda-image-sampler-native-descriptor-allocation-blocker-mismatch:" + key + ':' + preflight.firstBlocker();
            }
            if (expectedResourceDescriptorAllocations != preflight.resourceDescriptorAllocationCount()) {
                return "cuda-image-sampler-native-resource-descriptor-allocation-count-mismatch:" + key + ':' + preflight.resourceDescriptorAllocationCount();
            }
            if (expectedTextureDescriptorAllocations != preflight.textureDescriptorAllocationCount()) {
                return "cuda-image-sampler-native-texture-descriptor-allocation-count-mismatch:" + key + ':' + preflight.textureDescriptorAllocationCount();
            }
            int expectedNativeDescriptors = expectedResourceDescriptorAllocations + expectedTextureDescriptorAllocations;
            if (expectedNativeDescriptors != preflight.plannedNativeDescriptorCount()) {
                return "cuda-image-sampler-native-descriptor-planned-count-mismatch:" + key + ':' + preflight.plannedNativeDescriptorCount();
            }
            if (preflight.allocatedNativeDescriptorCount() != 0) {
                return "cuda-image-sampler-native-descriptors-allocated-unexpected:" + key + ':' + preflight.allocatedNativeDescriptorCount();
            }
            if (preflight.nativeDescriptorOwnershipActiveCount() != 0) {
                return "cuda-image-sampler-native-descriptor-ownership-active-unexpected:" + key + ':' + preflight.nativeDescriptorOwnershipActiveCount();
            }
            if (preflight.cleanupActiveCount() != 0) {
                return "cuda-image-sampler-native-descriptor-cleanup-active-unexpected:" + key + ':' + preflight.cleanupActiveCount();
            }
            if (preflight.rollbackActiveCount() != 0) {
                return "cuda-image-sampler-native-descriptor-rollback-active-unexpected:" + key + ':' + preflight.rollbackActiveCount();
            }
            if (preflight.allocationEnabledCount() != 0) {
                return "cuda-image-sampler-native-descriptor-allocation-enabled:" + key + ':' + preflight.allocationEnabledCount();
            }
            if (preflight.sdkStructByteEncodingEnabledCount() != 0) {
                return "cuda-image-sampler-native-descriptor-sdk-byte-encoding-enabled:" + key + ':' + preflight.sdkStructByteEncodingEnabledCount();
            }
            if (preflight.activeNativeDescriptorCount() != 0) {
                return "cuda-image-sampler-active-native-descriptors-unexpected:" + key + ':' + preflight.activeNativeDescriptorCount();
            }
            return "cuda-image-sampler-native-descriptor-allocation-case-not-ready:" + key;
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport.case"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".key", key);
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".expected.preflight.status", expectedPreflightStatus);
            fields.put(normalizedPrefix + ".expected.firstBlocker", expectedFirstBlocker);
            fields.put(normalizedPrefix + ".expected.resourceDescriptorAllocation.count", Integer.toString(expectedResourceDescriptorAllocations));
            fields.put(normalizedPrefix + ".expected.textureDescriptorAllocation.count", Integer.toString(expectedTextureDescriptorAllocations));
            fields.put(normalizedPrefix + ".expected.plannedNativeDescriptor.count", Integer.toString(expectedResourceDescriptorAllocations + expectedTextureDescriptorAllocations));
            fields.put(normalizedPrefix + ".actual.preflight.status", preflight.status());
            fields.put(normalizedPrefix + ".actual.firstBlocker", preflight.firstBlocker());
            fields.putAll(preflight.artifactFields(normalizedPrefix + ".preflight"));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerNativeDescriptorAllocationPreflightReport {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public static CudaImageSamplerNativeDescriptorAllocationPreflightReport inspectBuiltIns() {
        return new CudaImageSamplerNativeDescriptorAllocationPreflightReport(
                CudaImageSamplerNativeDescriptorEncodingPlanReport.inspectBuiltIns().cases().stream()
                        .map(CudaImageSamplerNativeDescriptorAllocationPreflightReport::runCase)
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
        return cases.stream().filter(testCase -> "ready".equals(testCase.preflight().nativeDescriptorEncodingPlanStatus())).count();
    }

    public long planBlockedCount() {
        return cases.stream().filter(testCase -> "blocked".equals(testCase.preflight().nativeDescriptorEncodingPlanStatus())).count();
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

    public long resourceDescriptorAllocationCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().resourceDescriptorAllocationCount()).sum();
    }

    public long resourceDescriptorAllocatedCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().resourceDescriptorAllocatedCount()).sum();
    }

    public long textureDescriptorAllocationCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().textureDescriptorAllocationCount()).sum();
    }

    public long textureDescriptorAllocatedCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().textureDescriptorAllocatedCount()).sum();
    }

    public long plannedNativeDescriptorCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().plannedNativeDescriptorCount()).sum();
    }

    public long allocatedNativeDescriptorCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().allocatedNativeDescriptorCount()).sum();
    }

    public long nativeDescriptorOwnershipPlannedCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().nativeDescriptorOwnershipPlannedCount()).sum();
    }

    public long nativeDescriptorOwnershipActiveCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().nativeDescriptorOwnershipActiveCount()).sum();
    }

    public long cleanupPlannedCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().cleanupPlannedCount()).sum();
    }

    public long cleanupActiveCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().cleanupActiveCount()).sum();
    }

    public long rollbackPlannedCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().rollbackPlannedCount()).sum();
    }

    public long rollbackActiveCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().rollbackActiveCount()).sum();
    }

    public long allocationEnabledCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().allocationEnabledCount()).sum();
    }

    public long sdkStructByteEncodingEnabledCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().sdkStructByteEncodingEnabledCount()).sum();
    }

    public long activeNativeDescriptorCount() {
        return cases.stream().mapToLong(testCase -> testCase.preflight().activeNativeDescriptorCount()).sum();
    }

    public String firstBlocker() {
        if (cases.isEmpty()) {
            return "cuda-image-sampler-native-descriptor-allocation-preflight-cases-missing";
        }
        return cases.stream()
                .filter(testCase -> !testCase.ready())
                .map(Case::firstBlocker)
                .findFirst()
                .orElse("none");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerNativeDescriptorAllocationPreflightReport");
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler native descriptor allocation preflight: ").append(status()).append('\n');
        builder.append("Cases: ").append(caseReadyCount()).append('/').append(cases.size()).append(" ready").append('\n');
        builder.append("Plans: ready=").append(planReadyCount()).append(", blocked=").append(planBlockedCount()).append('\n');
        builder.append("Preflights: ready=").append(preflightReadyCount()).append(", blocked=").append(preflightBlockedCount()).append('\n');
        builder.append("Entries: ").append(entryCount()).append('\n');
        builder.append("Native descriptor allocations: resource=").append(resourceDescriptorAllocationCount())
                .append(", texture=").append(textureDescriptorAllocationCount())
                .append(", planned=").append(plannedNativeDescriptorCount())
                .append(", allocated=").append(allocatedNativeDescriptorCount()).append('\n');
        builder.append("Ownership planned: ").append(nativeDescriptorOwnershipPlannedCount())
                .append(", active=").append(nativeDescriptorOwnershipActiveCount()).append('\n');
        builder.append("Cleanup planned: ").append(cleanupPlannedCount())
                .append(", active=").append(cleanupActiveCount()).append('\n');
        builder.append("Rollback planned: ").append(rollbackPlannedCount())
                .append(", active=").append(rollbackActiveCount()).append('\n');
        builder.append("Allocation enabled: ").append(allocationEnabledCount()).append('\n');
        builder.append("SDK byte encoding enabled: ").append(sdkStructByteEncodingEnabledCount()).append('\n');
        builder.append("Active native descriptors: ").append(activeNativeDescriptorCount()).append('\n');
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
        fields.put(prefix + ".resourceDescriptorAllocation.count", Long.toString(resourceDescriptorAllocationCount()));
        fields.put(prefix + ".resourceDescriptor.allocated.count", Long.toString(resourceDescriptorAllocatedCount()));
        fields.put(prefix + ".textureDescriptorAllocation.count", Long.toString(textureDescriptorAllocationCount()));
        fields.put(prefix + ".textureDescriptor.allocated.count", Long.toString(textureDescriptorAllocatedCount()));
        fields.put(prefix + ".plannedNativeDescriptor.count", Long.toString(plannedNativeDescriptorCount()));
        fields.put(prefix + ".allocatedNativeDescriptor.count", Long.toString(allocatedNativeDescriptorCount()));
        fields.put(prefix + ".nativeDescriptorOwnership.planned.count", Long.toString(nativeDescriptorOwnershipPlannedCount()));
        fields.put(prefix + ".nativeDescriptorOwnership.active.count", Long.toString(nativeDescriptorOwnershipActiveCount()));
        fields.put(prefix + ".cleanup.planned.count", Long.toString(cleanupPlannedCount()));
        fields.put(prefix + ".cleanup.active.count", Long.toString(cleanupActiveCount()));
        fields.put(prefix + ".rollback.planned.count", Long.toString(rollbackPlannedCount()));
        fields.put(prefix + ".rollback.active.count", Long.toString(rollbackActiveCount()));
        fields.put(prefix + ".allocation.enabled.count", Long.toString(allocationEnabledCount()));
        fields.put(prefix + ".sdkStructByteEncoding.enabled.count", Long.toString(sdkStructByteEncodingEnabledCount()));
        fields.put(prefix + ".activeNativeDescriptor.count", Long.toString(activeNativeDescriptorCount()));
        fields.put(prefix + ".nativeDescriptorAllocation.enabled", "false");
        fields.put(prefix + ".sdkStructByteEncoding.enabled", "false");
        fields.put(prefix + ".objectCreation.enabled", "false");
        fields.put(prefix + ".runtimeBinding.enabled", "false");
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < cases.size(); index++) {
            fields.putAll(cases.get(index).artifactFields(prefix + ".case." + index));
        }
    }

    private static Case runCase(CudaImageSamplerNativeDescriptorEncodingPlanReport.Case encodingCase) {
        CudaImageSamplerNativeDescriptorAllocationPreflight preflight =
                CudaImageSamplerNativeDescriptorAllocationPreflight.from(encodingCase.plan());
        boolean readyPlan = "ready".equals(encodingCase.plan().status());
        return new Case(
                encodingCase.key(),
                "blocked",
                readyPlan ? firstNativeDescriptorAllocationBlocker(encodingCase.plan()) : encodingCase.plan().firstBlocker(),
                readyPlan ? 16 : 0,
                readyPlan ? 9 : 0,
                preflight
        );
    }

    private static String firstNativeDescriptorAllocationBlocker(CudaImageSamplerNativeDescriptorEncodingPlan plan) {
        return plan.entries().stream()
                .filter(entry -> entry.resourceEncodingRequired() || entry.textureEncodingRequired())
                .findFirst()
                .map(entry -> "cuda-image-sampler-native-descriptor-allocation-disabled:"
                        + entry.parameterIndex()
                        + ':'
                        + entry.javaType())
                .orElse("cuda-image-sampler-native-descriptor-allocation-disabled");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
