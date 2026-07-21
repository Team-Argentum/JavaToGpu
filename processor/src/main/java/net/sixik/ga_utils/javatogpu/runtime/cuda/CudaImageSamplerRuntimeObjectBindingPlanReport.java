package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardware-free contract for future CUDA texture/surface object kernel-argument binding.
 */
public record CudaImageSamplerRuntimeObjectBindingPlanReport(List<Case> cases) {

    public record Case(
            String key,
            String expectedPlanStatus,
            String expectedFirstBlocker,
            int expectedObjectBindings,
            int expectedTextureBindings,
            int expectedSurfaceBindings,
            int expectedFoldedSamplers,
            int expectedObjectSlots,
            int expectedMetadataSlots,
            int expectedKernelSlots,
            CudaImageSamplerRuntimeObjectBindingPlan plan
    ) {
        public Case {
            key = normalize(key, "unknown");
            expectedPlanStatus = normalize(expectedPlanStatus, "ready");
            expectedFirstBlocker = normalize(expectedFirstBlocker, "none");
            expectedObjectBindings = Math.max(0, expectedObjectBindings);
            expectedTextureBindings = Math.max(0, expectedTextureBindings);
            expectedSurfaceBindings = Math.max(0, expectedSurfaceBindings);
            expectedFoldedSamplers = Math.max(0, expectedFoldedSamplers);
            expectedObjectSlots = Math.max(0, expectedObjectSlots);
            expectedMetadataSlots = Math.max(0, expectedMetadataSlots);
            expectedKernelSlots = Math.max(0, expectedKernelSlots);
            plan = plan == null ? CudaImageSamplerRuntimeObjectBindingPlan.empty() : plan;
        }

        public boolean ready() {
            return expectedPlanStatus.equals(plan.status())
                    && expectedFirstBlocker.equals(plan.firstBlocker())
                    && expectedObjectBindings == plan.objectBindingCount()
                    && expectedTextureBindings == plan.textureObjectBindingCount()
                    && expectedSurfaceBindings == plan.surfaceObjectBindingCount()
                    && expectedFoldedSamplers == plan.foldedSamplerBindingCount()
                    && expectedObjectSlots == plan.plannedObjectKernelParameterSlotCount()
                    && expectedMetadataSlots == plan.plannedMetadataKernelParameterSlotCount()
                    && expectedKernelSlots == plan.plannedKernelParameterSlotCount()
                    && plan.runtimeBindingKernelParameterSlotCount() == 0
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
                return "cuda-image-sampler-runtime-object-binding-plan-status-mismatch:" + key + ':' + plan.status();
            }
            if (!expectedFirstBlocker.equals(plan.firstBlocker())) {
                return "cuda-image-sampler-runtime-object-binding-plan-blocker-mismatch:" + key + ':' + plan.firstBlocker();
            }
            if (expectedObjectBindings != plan.objectBindingCount()) {
                return "cuda-image-sampler-runtime-object-binding-count-mismatch:" + key + ':' + plan.objectBindingCount();
            }
            if (expectedTextureBindings != plan.textureObjectBindingCount()) {
                return "cuda-image-sampler-runtime-texture-binding-count-mismatch:" + key + ':' + plan.textureObjectBindingCount();
            }
            if (expectedSurfaceBindings != plan.surfaceObjectBindingCount()) {
                return "cuda-image-sampler-runtime-surface-binding-count-mismatch:" + key + ':' + plan.surfaceObjectBindingCount();
            }
            if (expectedFoldedSamplers != plan.foldedSamplerBindingCount()) {
                return "cuda-image-sampler-runtime-folded-sampler-count-mismatch:" + key + ':' + plan.foldedSamplerBindingCount();
            }
            if (expectedObjectSlots != plan.plannedObjectKernelParameterSlotCount()) {
                return "cuda-image-sampler-runtime-object-slot-count-mismatch:" + key + ':' + plan.plannedObjectKernelParameterSlotCount();
            }
            if (expectedMetadataSlots != plan.plannedMetadataKernelParameterSlotCount()) {
                return "cuda-image-sampler-runtime-metadata-slot-count-mismatch:" + key + ':' + plan.plannedMetadataKernelParameterSlotCount();
            }
            if (expectedKernelSlots != plan.plannedKernelParameterSlotCount()) {
                return "cuda-image-sampler-runtime-kernel-slot-count-mismatch:" + key + ':' + plan.plannedKernelParameterSlotCount();
            }
            if (plan.runtimeBindingKernelParameterSlotCount() != 0) {
                return "cuda-image-sampler-runtime-binding-enabled:" + key + ':' + plan.runtimeBindingKernelParameterSlotCount();
            }
            if (plan.objectCreationCallEnabledCount() != 0) {
                return "cuda-image-sampler-runtime-object-creation-call-enabled:" + key;
            }
            if (plan.activeObjectCount() != 0) {
                return "cuda-image-sampler-runtime-active-objects-unexpected:" + key + ':' + plan.activeObjectCount();
            }
            return "cuda-image-sampler-runtime-object-binding-plan-case-not-ready:" + key;
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport.case"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".key", key);
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".expected.plan.status", expectedPlanStatus);
            fields.put(normalizedPrefix + ".expected.firstBlocker", expectedFirstBlocker);
            fields.put(normalizedPrefix + ".expected.objectBinding.count", Integer.toString(expectedObjectBindings));
            fields.put(normalizedPrefix + ".expected.textureObjectBinding.count", Integer.toString(expectedTextureBindings));
            fields.put(normalizedPrefix + ".expected.surfaceObjectBinding.count", Integer.toString(expectedSurfaceBindings));
            fields.put(normalizedPrefix + ".expected.foldedSamplerBinding.count", Integer.toString(expectedFoldedSamplers));
            fields.put(normalizedPrefix + ".expected.plannedObjectKernelParameterSlot.count", Integer.toString(expectedObjectSlots));
            fields.put(normalizedPrefix + ".expected.plannedMetadataKernelParameterSlot.count", Integer.toString(expectedMetadataSlots));
            fields.put(normalizedPrefix + ".expected.plannedKernelParameterSlot.count", Integer.toString(expectedKernelSlots));
            fields.put(normalizedPrefix + ".actual.plan.status", plan.status());
            fields.put(normalizedPrefix + ".actual.firstBlocker", plan.firstBlocker());
            fields.putAll(plan.artifactFields(normalizedPrefix + ".plan"));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerRuntimeObjectBindingPlanReport {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public static CudaImageSamplerRuntimeObjectBindingPlanReport inspectBuiltIns() {
        return new CudaImageSamplerRuntimeObjectBindingPlanReport(
                CudaImageSamplerObjectCreationRequestPlanReport.inspectBuiltIns().cases().stream()
                        .map(CudaImageSamplerRuntimeObjectBindingPlanReport::runCase)
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

    public long objectBindingCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().objectBindingCount()).sum();
    }

    public long textureObjectBindingCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().textureObjectBindingCount()).sum();
    }

    public long surfaceObjectBindingCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().surfaceObjectBindingCount()).sum();
    }

    public long foldedSamplerBindingCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().foldedSamplerBindingCount()).sum();
    }

    public long plannedObjectKernelParameterSlotCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().plannedObjectKernelParameterSlotCount()).sum();
    }

    public long plannedMetadataKernelParameterSlotCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().plannedMetadataKernelParameterSlotCount()).sum();
    }

    public long plannedKernelParameterSlotCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().plannedKernelParameterSlotCount()).sum();
    }

    public long runtimeBindingKernelParameterSlotCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().runtimeBindingKernelParameterSlotCount()).sum();
    }

    public long objectCreationCallEnabledCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().objectCreationCallEnabledCount()).sum();
    }

    public long activeObjectCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().activeObjectCount()).sum();
    }

    public String firstBlocker() {
        if (cases.isEmpty()) {
            return "cuda-image-sampler-runtime-object-binding-plan-cases-missing";
        }
        return cases.stream()
                .filter(testCase -> !testCase.ready())
                .map(Case::firstBlocker)
                .findFirst()
                .orElse("none");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerRuntimeObjectBindingPlanReport");
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler runtime object binding plan: ").append(status()).append('\n');
        builder.append("Cases: ").append(caseReadyCount()).append('/').append(cases.size()).append(" ready").append('\n');
        builder.append("Plans: ready=").append(planReadyCount()).append(", blocked=").append(planBlockedCount()).append('\n');
        builder.append("Entries: ").append(entryCount()).append('\n');
        builder.append("Object bindings: texture=").append(textureObjectBindingCount())
                .append(", surface=").append(surfaceObjectBindingCount())
                .append(", total=").append(objectBindingCount()).append('\n');
        builder.append("Kernel slots: object=").append(plannedObjectKernelParameterSlotCount())
                .append(", metadata=").append(plannedMetadataKernelParameterSlotCount())
                .append(", planned=").append(plannedKernelParameterSlotCount())
                .append(", active=").append(runtimeBindingKernelParameterSlotCount()).append('\n');
        builder.append("Folded samplers: ").append(foldedSamplerBindingCount()).append('\n');
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
        fields.put(prefix + ".objectBinding.count", Long.toString(objectBindingCount()));
        fields.put(prefix + ".textureObjectBinding.count", Long.toString(textureObjectBindingCount()));
        fields.put(prefix + ".surfaceObjectBinding.count", Long.toString(surfaceObjectBindingCount()));
        fields.put(prefix + ".foldedSamplerBinding.count", Long.toString(foldedSamplerBindingCount()));
        fields.put(prefix + ".plannedObjectKernelParameterSlot.count", Long.toString(plannedObjectKernelParameterSlotCount()));
        fields.put(prefix + ".plannedMetadataKernelParameterSlot.count", Long.toString(plannedMetadataKernelParameterSlotCount()));
        fields.put(prefix + ".plannedKernelParameterSlot.count", Long.toString(plannedKernelParameterSlotCount()));
        fields.put(prefix + ".runtimeBinding.kernelParameterSlot.count", Long.toString(runtimeBindingKernelParameterSlotCount()));
        fields.put(prefix + ".objectCreationCall.enabled.count", Long.toString(objectCreationCallEnabledCount()));
        fields.put(prefix + ".activeObject.count", Long.toString(activeObjectCount()));
        fields.put(prefix + ".objectCreationCall.enabled", "false");
        fields.put(prefix + ".runtimeBinding.enabled", "false");
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < cases.size(); index++) {
            fields.putAll(cases.get(index).artifactFields(prefix + ".case." + index));
        }
    }

    private static Case runCase(CudaImageSamplerObjectCreationRequestPlanReport.Case requestCase) {
        CudaImageSamplerRuntimeObjectBindingPlan plan = CudaImageSamplerRuntimeObjectBindingPlan.from(requestCase.plan());
        boolean readyPlan = "ready".equals(requestCase.plan().status());
        return new Case(
                requestCase.key(),
                readyPlan ? "ready" : "blocked",
                requestCase.plan().firstBlocker(),
                readyPlan ? 16 : 0,
                readyPlan ? 8 : 0,
                readyPlan ? 8 : 0,
                readyPlan ? 1 : 0,
                readyPlan ? 16 : 0,
                readyPlan ? 28 : 0,
                readyPlan ? 44 : 0,
                plan
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
