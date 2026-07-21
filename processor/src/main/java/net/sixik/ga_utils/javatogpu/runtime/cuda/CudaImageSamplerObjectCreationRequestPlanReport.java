package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardware-free contract for future CUDA texture/surface object creation requests.
 */
public record CudaImageSamplerObjectCreationRequestPlanReport(List<Case> cases) {

    public record Case(
            String key,
            String expectedPlanStatus,
            String expectedFirstBlocker,
            int expectedObjectRequests,
            int expectedTextureRequests,
            int expectedSurfaceRequests,
            int expectedFoldedSamplers,
            CudaImageSamplerObjectCreationRequestPlan plan
    ) {
        public Case {
            key = normalize(key, "unknown");
            expectedPlanStatus = normalize(expectedPlanStatus, "ready");
            expectedFirstBlocker = normalize(expectedFirstBlocker, "none");
            expectedObjectRequests = Math.max(0, expectedObjectRequests);
            expectedTextureRequests = Math.max(0, expectedTextureRequests);
            expectedSurfaceRequests = Math.max(0, expectedSurfaceRequests);
            expectedFoldedSamplers = Math.max(0, expectedFoldedSamplers);
            plan = plan == null ? CudaImageSamplerObjectCreationRequestPlan.empty() : plan;
        }

        public boolean ready() {
            return expectedPlanStatus.equals(plan.status())
                    && expectedFirstBlocker.equals(plan.firstBlocker())
                    && expectedObjectRequests == plan.objectCreationRequestCount()
                    && expectedTextureRequests == plan.textureObjectRequestCount()
                    && expectedSurfaceRequests == plan.surfaceObjectRequestCount()
                    && expectedFoldedSamplers == plan.foldedSamplerCount()
                    && plan.objectCreationCallEnabledCount() == 0
                    && plan.activeObjectCount() == 0;
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public String firstBlocker() {
            if (ready()) {
                return "none";
            }
            if (!expectedPlanStatus.equals(plan.status())) {
                return "cuda-image-sampler-object-creation-request-plan-status-mismatch:" + key + ':' + plan.status();
            }
            if (!expectedFirstBlocker.equals(plan.firstBlocker())) {
                return "cuda-image-sampler-object-creation-request-plan-blocker-mismatch:" + key + ':' + plan.firstBlocker();
            }
            if (expectedObjectRequests != plan.objectCreationRequestCount()) {
                return "cuda-image-sampler-object-request-count-mismatch:" + key + ':' + plan.objectCreationRequestCount();
            }
            if (expectedTextureRequests != plan.textureObjectRequestCount()) {
                return "cuda-image-sampler-texture-object-request-count-mismatch:" + key + ':' + plan.textureObjectRequestCount();
            }
            if (expectedSurfaceRequests != plan.surfaceObjectRequestCount()) {
                return "cuda-image-sampler-surface-object-request-count-mismatch:" + key + ':' + plan.surfaceObjectRequestCount();
            }
            if (expectedFoldedSamplers != plan.foldedSamplerCount()) {
                return "cuda-image-sampler-folded-sampler-count-mismatch:" + key + ':' + plan.foldedSamplerCount();
            }
            if (plan.objectCreationCallEnabledCount() != 0) {
                return "cuda-image-sampler-object-creation-call-enabled:" + key;
            }
            if (plan.activeObjectCount() != 0) {
                return "cuda-image-sampler-active-objects-unexpected:" + key + ':' + plan.activeObjectCount();
            }
            return "cuda-image-sampler-object-creation-request-plan-case-not-ready:" + key;
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerObjectCreationRequestPlanReport.case"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".key", key);
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".expected.plan.status", expectedPlanStatus);
            fields.put(normalizedPrefix + ".expected.firstBlocker", expectedFirstBlocker);
            fields.put(normalizedPrefix + ".expected.objectRequest.count", Integer.toString(expectedObjectRequests));
            fields.put(normalizedPrefix + ".expected.textureObjectRequest.count", Integer.toString(expectedTextureRequests));
            fields.put(normalizedPrefix + ".expected.surfaceObjectRequest.count", Integer.toString(expectedSurfaceRequests));
            fields.put(normalizedPrefix + ".expected.foldedSampler.count", Integer.toString(expectedFoldedSamplers));
            fields.put(normalizedPrefix + ".actual.plan.status", plan.status());
            fields.put(normalizedPrefix + ".actual.firstBlocker", plan.firstBlocker());
            fields.putAll(plan.artifactFields(normalizedPrefix + ".plan"));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerObjectCreationRequestPlanReport {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public static CudaImageSamplerObjectCreationRequestPlanReport inspectBuiltIns() {
        return new CudaImageSamplerObjectCreationRequestPlanReport(
                CudaImageSamplerNativeDescriptorEncodingPlanReport.inspectBuiltIns().cases().stream()
                        .map(CudaImageSamplerObjectCreationRequestPlanReport::runCase)
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

    public long objectCreationRequestCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().objectCreationRequestCount()).sum();
    }

    public long textureObjectRequestCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().textureObjectRequestCount()).sum();
    }

    public long surfaceObjectRequestCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().surfaceObjectRequestCount()).sum();
    }

    public long foldedSamplerCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().foldedSamplerCount()).sum();
    }

    public long objectCreationCallEnabledCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().objectCreationCallEnabledCount()).sum();
    }

    public long activeObjectCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().activeObjectCount()).sum();
    }

    public String firstBlocker() {
        if (cases.isEmpty()) {
            return "cuda-image-sampler-object-creation-request-plan-cases-missing";
        }
        return cases.stream()
                .filter(testCase -> !testCase.ready())
                .map(Case::firstBlocker)
                .findFirst()
                .orElse("none");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerObjectCreationRequestPlanReport"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerObjectCreationRequestPlanReport");
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler object creation request plan: ").append(status()).append('\n');
        builder.append("Cases: ").append(caseReadyCount()).append('/').append(cases.size()).append(" ready").append('\n');
        builder.append("Plans: ready=").append(planReadyCount()).append(", blocked=").append(planBlockedCount()).append('\n');
        builder.append("Entries: ").append(entryCount()).append('\n');
        builder.append("Object requests: texture=").append(textureObjectRequestCount())
                .append(", surface=").append(surfaceObjectRequestCount())
                .append(", total=").append(objectCreationRequestCount()).append('\n');
        builder.append("Folded samplers: ").append(foldedSamplerCount()).append('\n');
        builder.append("Object creation calls enabled: ").append(objectCreationCallEnabledCount()).append('\n');
        builder.append("Active objects: ").append(activeObjectCount()).append('\n');
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
        fields.put(prefix + ".objectRequest.count", Long.toString(objectCreationRequestCount()));
        fields.put(prefix + ".textureObjectRequest.count", Long.toString(textureObjectRequestCount()));
        fields.put(prefix + ".surfaceObjectRequest.count", Long.toString(surfaceObjectRequestCount()));
        fields.put(prefix + ".foldedSampler.count", Long.toString(foldedSamplerCount()));
        fields.put(prefix + ".objectCreationCall.enabled.count", Long.toString(objectCreationCallEnabledCount()));
        fields.put(prefix + ".activeObject.count", Long.toString(activeObjectCount()));
        fields.put(prefix + ".objectCreationCall.enabled", "false");
        fields.put(prefix + ".runtimeBinding.enabled", "false");
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < cases.size(); index++) {
            fields.putAll(cases.get(index).artifactFields(prefix + ".case." + index));
        }
    }

    private static Case runCase(CudaImageSamplerNativeDescriptorEncodingPlanReport.Case encodingCase) {
        CudaImageSamplerObjectCreationRequestPlan plan = CudaImageSamplerObjectCreationRequestPlan.from(encodingCase.plan());
        boolean readyPlan = "ready".equals(encodingCase.plan().status());
        return new Case(
                encodingCase.key(),
                readyPlan ? "ready" : "blocked",
                encodingCase.plan().firstBlocker(),
                readyPlan ? 16 : 0,
                readyPlan ? 8 : 0,
                readyPlan ? 8 : 0,
                readyPlan ? 1 : 0,
                plan
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
