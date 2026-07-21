package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardware-free contract for planning CUDA native descriptor field encoding from Java descriptor payloads.
 */
public record CudaImageSamplerNativeDescriptorEncodingPlanReport(List<Case> cases) {

    public record Case(
            String key,
            String expectedPlanStatus,
            String expectedFirstBlocker,
            int expectedResourceFieldWrites,
            int expectedTextureFieldWrites,
            CudaImageSamplerNativeDescriptorEncodingPlan plan
    ) {
        public Case {
            key = normalize(key, "unknown");
            expectedPlanStatus = normalize(expectedPlanStatus, "ready");
            expectedFirstBlocker = normalize(expectedFirstBlocker, "none");
            expectedResourceFieldWrites = Math.max(0, expectedResourceFieldWrites);
            expectedTextureFieldWrites = Math.max(0, expectedTextureFieldWrites);
            plan = plan == null ? CudaImageSamplerNativeDescriptorEncodingPlan.empty() : plan;
        }

        public boolean ready() {
            return expectedPlanStatus.equals(plan.status())
                    && expectedFirstBlocker.equals(plan.firstBlocker())
                    && expectedResourceFieldWrites == plan.resourceFieldWriteCount()
                    && expectedTextureFieldWrites == plan.textureFieldWriteCount()
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
            if (!expectedPlanStatus.equals(plan.status())) {
                return "cuda-image-sampler-native-descriptor-encoding-plan-status-mismatch:" + key + ':' + plan.status();
            }
            if (!expectedFirstBlocker.equals(plan.firstBlocker())) {
                return "cuda-image-sampler-native-descriptor-encoding-plan-blocker-mismatch:" + key + ':' + plan.firstBlocker();
            }
            if (expectedResourceFieldWrites != plan.resourceFieldWriteCount()) {
                return "cuda-image-sampler-native-descriptor-resource-field-write-count-mismatch:" + key + ':' + plan.resourceFieldWriteCount();
            }
            if (expectedTextureFieldWrites != plan.textureFieldWriteCount()) {
                return "cuda-image-sampler-native-descriptor-texture-field-write-count-mismatch:" + key + ':' + plan.textureFieldWriteCount();
            }
            if (plan.nativeWriteEnabledCount() != 0) {
                return "cuda-image-sampler-native-descriptor-field-native-write-enabled:" + key;
            }
            if (plan.sdkStructByteEncodingEnabledCount() != 0) {
                return "cuda-image-sampler-native-descriptor-sdk-byte-encoding-enabled:" + key;
            }
            if (plan.activeNativeDescriptorCount() != 0) {
                return "cuda-image-sampler-active-native-descriptors-unexpected:" + key + ':' + plan.activeNativeDescriptorCount();
            }
            return "cuda-image-sampler-native-descriptor-encoding-plan-case-not-ready:" + key;
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport.case"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".key", key);
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".expected.plan.status", expectedPlanStatus);
            fields.put(normalizedPrefix + ".expected.firstBlocker", expectedFirstBlocker);
            fields.put(normalizedPrefix + ".expected.resourceFieldWrite.count", Integer.toString(expectedResourceFieldWrites));
            fields.put(normalizedPrefix + ".expected.textureFieldWrite.count", Integer.toString(expectedTextureFieldWrites));
            fields.put(normalizedPrefix + ".actual.plan.status", plan.status());
            fields.put(normalizedPrefix + ".actual.firstBlocker", plan.firstBlocker());
            fields.putAll(plan.artifactFields(normalizedPrefix + ".plan"));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerNativeDescriptorEncodingPlanReport {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public static CudaImageSamplerNativeDescriptorEncodingPlanReport inspectBuiltIns() {
        return new CudaImageSamplerNativeDescriptorEncodingPlanReport(
                CudaImageSamplerDescriptorPayloadModelReport.inspectBuiltIns().cases().stream()
                        .map(CudaImageSamplerNativeDescriptorEncodingPlanReport::runCase)
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
        return cases.stream().filter(testCase -> "ready".equals(testCase.plan().status())).count();
    }

    public long planBlockedCount() {
        return cases.stream().filter(testCase -> "blocked".equals(testCase.plan().status())).count();
    }

    public long entryCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().entries().size()).sum();
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

    public long samplerTextureFieldWriteCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().samplerTextureFieldWriteCount()).sum();
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
            return "cuda-image-sampler-native-descriptor-encoding-plan-cases-missing";
        }
        return cases.stream()
                .filter(testCase -> !testCase.ready())
                .map(Case::firstBlocker)
                .findFirst()
                .orElse("none");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerNativeDescriptorEncodingPlanReport");
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler native descriptor encoding plan: ").append(status()).append('\n');
        builder.append("Cases: ").append(caseReadyCount()).append('/').append(cases.size()).append(" ready").append('\n');
        builder.append("Plans: ready=").append(planReadyCount()).append(", blocked=").append(planBlockedCount()).append('\n');
        builder.append("Entries: ").append(entryCount()).append('\n');
        builder.append("Field writes: resource=").append(resourceFieldWriteCount())
                .append(", texture=").append(textureFieldWriteCount())
                .append(", total=").append(fieldWriteCount()).append('\n');
        builder.append("Native writes enabled: ").append(nativeWriteEnabledCount()).append('\n');
        builder.append("SDK byte encoding enabled: ").append(sdkStructByteEncodingEnabledCount()).append('\n');
        builder.append("Active native descriptors: ").append(activeNativeDescriptorCount()).append('\n');
        builder.append("First blocker: ").append(firstBlocker()).append('\n');
        builder.append('\n').append("Cases:").append('\n');
        for (Case testCase : cases) {
            builder.append("- ")
                    .append(testCase.key())
                    .append(": case=")
                    .append(testCase.status())
                    .append(", plan=")
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
        fields.put(prefix + ".plan.ready.count", Long.toString(planReadyCount()));
        fields.put(prefix + ".plan.blocked.count", Long.toString(planBlockedCount()));
        fields.put(prefix + ".entry.count", Long.toString(entryCount()));
        fields.put(prefix + ".resourceFieldWrite.count", Long.toString(resourceFieldWriteCount()));
        fields.put(prefix + ".textureFieldWrite.count", Long.toString(textureFieldWriteCount()));
        fields.put(prefix + ".fieldWrite.count", Long.toString(fieldWriteCount()));
        fields.put(prefix + ".samplerTextureFieldWrite.count", Long.toString(samplerTextureFieldWriteCount()));
        fields.put(prefix + ".nativeWrite.enabled.count", Long.toString(nativeWriteEnabledCount()));
        fields.put(prefix + ".sdkStructByteEncoding.enabled.count", Long.toString(sdkStructByteEncodingEnabledCount()));
        fields.put(prefix + ".activeNativeDescriptor.count", Long.toString(activeNativeDescriptorCount()));
        fields.put(prefix + ".nativeDescriptorMemoryAllocation.enabled", "false");
        fields.put(prefix + ".objectCreation.enabled", "false");
        fields.put(prefix + ".runtimeBinding.enabled", "false");
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < cases.size(); index++) {
            fields.putAll(cases.get(index).artifactFields(prefix + ".case." + index));
        }
    }

    private static Case runCase(CudaImageSamplerDescriptorPayloadModelReport.Case payloadCase) {
        CudaImageSamplerNativeDescriptorEncodingPlan plan = CudaImageSamplerNativeDescriptorEncodingPlan.from(payloadCase.model());
        boolean readyModel = "ready".equals(payloadCase.model().status());
        return new Case(
                payloadCase.key(),
                readyModel ? "ready" : "blocked",
                payloadCase.model().firstBlocker(),
                readyModel ? 35 : 0,
                readyModel ? 54 : 0,
                plan
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
