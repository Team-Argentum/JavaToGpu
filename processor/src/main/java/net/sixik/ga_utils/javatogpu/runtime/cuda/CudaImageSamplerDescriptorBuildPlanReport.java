package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.images.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.images.Sampler;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardware-free contract for Java-side CUDA image/sampler descriptor build planning.
 */
public record CudaImageSamplerDescriptorBuildPlanReport(
        List<Case> cases,
        String nativeDescriptorLayoutStatus,
        boolean nativeDescriptorLayoutReady
) {

    public record Case(
            String key,
            String expectedPlanStatus,
            String expectedFirstBlocker,
            CudaImageSamplerDescriptorBuildPlan plan
    ) {
        public Case {
            key = normalize(key, "unknown");
            expectedPlanStatus = normalize(expectedPlanStatus, "ready");
            expectedFirstBlocker = normalize(expectedFirstBlocker, "none");
            plan = plan == null ? CudaImageSamplerDescriptorBuildPlan.empty() : plan;
        }

        public boolean ready() {
            return expectedPlanStatus.equals(plan.status())
                    && expectedFirstBlocker.equals(plan.firstBlocker());
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public String firstBlocker() {
            if (ready()) {
                return "none";
            }
            if (!expectedPlanStatus.equals(plan.status())) {
                return "cuda-image-sampler-descriptor-build-plan-status-mismatch:" + key + ':' + plan.status();
            }
            if (!expectedFirstBlocker.equals(plan.firstBlocker())) {
                return "cuda-image-sampler-descriptor-build-plan-blocker-mismatch:" + key + ':' + plan.firstBlocker();
            }
            return "cuda-image-sampler-descriptor-build-plan-case-not-ready:" + key;
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerDescriptorBuildPlanReport.case"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".key", key);
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".expected.plan.status", expectedPlanStatus);
            fields.put(normalizedPrefix + ".expected.firstBlocker", expectedFirstBlocker);
            fields.put(normalizedPrefix + ".actual.plan.status", plan.status());
            fields.put(normalizedPrefix + ".actual.firstBlocker", plan.firstBlocker());
            fields.putAll(plan.artifactFields(normalizedPrefix + ".plan"));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerDescriptorBuildPlanReport {
        cases = cases == null ? List.of() : List.copyOf(cases);
        nativeDescriptorLayoutStatus = normalize(nativeDescriptorLayoutStatus, "ready");
    }

    public static CudaImageSamplerDescriptorBuildPlanReport inspectBuiltIns() {
        ArrayList<Case> cases = new ArrayList<>();
        cases.add(runCase(
                "image2d-read-only",
                List.of(new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY)),
                new Object[]{Image2DReadOnly.borrowed(0xCAFE_5001L, 8, 4)},
                "ready",
                "none"
        ));
        cases.add(runCase(
                "image2d-write-only",
                List.of(new GpuKernelParameterDescriptor("outputImage", Image2DWriteOnly.class.getName(), GpuKernelParameterAccess.READ_WRITE)),
                new Object[]{Image2DWriteOnly.borrowed(0xCAFE_5002L, 8, 4)},
                "ready",
                "none"
        ));
        cases.add(runCase(
                "sampler-value",
                List.of(new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE)),
                new Object[]{Sampler.borrowed(0xCAFE_5003L)},
                "ready",
                "none"
        ));
        cases.add(runCase(
                "image-sampler-mixed",
                List.of(
                        new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", Image2DWriteOnly.class.getName(), GpuKernelParameterAccess.READ_WRITE)
                ),
                new Object[]{
                        Image2DReadOnly.borrowed(0xCAFE_5004L, 8, 4),
                        Sampler.borrowed(0xCAFE_5005L),
                        Image2DWriteOnly.borrowed(0xCAFE_5006L, 8, 4)
                },
                "ready",
                "none"
        ));
        cases.add(runCase(
                "image2d-handle-missing",
                List.of(new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY)),
                new Object[]{new Image2DReadOnly()},
                "blocked",
                "cuda-image-descriptor-handle-missing:0:" + Image2DReadOnly.class.getName()
        ));
        Sampler closedSampler = Sampler.borrowed(0xCAFE_5007L);
        closedSampler.close();
        cases.add(runCase(
                "sampler-closed",
                List.of(new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE)),
                new Object[]{closedSampler},
                "blocked",
                "cuda-sampler-descriptor-handle-closed:0:" + Sampler.class.getName()
        ));
        cases.add(runCase(
                "image2d-height-missing",
                List.of(new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY)),
                new Object[]{Image2DReadOnly.borrowed(0xCAFE_5008L, 8, 0)},
                "blocked",
                "cuda-image-descriptor-height-missing:0:" + Image2DReadOnly.class.getName()
        ));
        return new CudaImageSamplerDescriptorBuildPlanReport(
                cases,
                "ready",
                true
        );
    }

    public boolean ready() {
        return !cases.isEmpty()
                && cases.stream().allMatch(Case::ready)
                && nativeDescriptorLayoutReady;
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

    public long resourceDescriptorPayloadPlannedCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().resourceDescriptorPayloadPlannedCount()).sum();
    }

    public long textureDescriptorPayloadPlannedCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().textureDescriptorPayloadPlannedCount()).sum();
    }

    public long activeDescriptorPayloadCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().activeDescriptorPayloadCount()).sum();
    }

    public long activeNativeDescriptorCount() {
        return cases.stream().mapToLong(testCase -> testCase.plan().activeNativeDescriptorCount()).sum();
    }

    public String firstBlocker() {
        if (cases.isEmpty()) {
            return "cuda-image-sampler-descriptor-build-plan-cases-missing";
        }
        return cases.stream()
                .filter(testCase -> !testCase.ready())
                .map(Case::firstBlocker)
                .findFirst()
                .orElseGet(() -> nativeDescriptorLayoutReady
                        ? "none"
                        : "cuda-image-sampler-native-descriptor-layout-not-ready:" + nativeDescriptorLayoutStatus);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerDescriptorBuildPlanReport"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerDescriptorBuildPlanReport");
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler descriptor build plan: ").append(status()).append('\n');
        builder.append("Cases: ").append(caseReadyCount()).append('/').append(cases.size()).append(" ready").append('\n');
        builder.append("Plans: ready=").append(planReadyCount()).append(", blocked=").append(planBlockedCount()).append('\n');
        builder.append("Entries: ").append(entryCount()).append('\n');
        builder.append("Descriptor payloads: resource=").append(resourceDescriptorPayloadPlannedCount())
                .append(", texture=").append(textureDescriptorPayloadPlannedCount())
                .append(", active=").append(activeDescriptorPayloadCount()).append('\n');
        builder.append("Native descriptors active: ").append(activeNativeDescriptorCount()).append('\n');
        builder.append("Native layout: ").append(nativeDescriptorLayoutStatus).append('\n');
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
        fields.put(prefix + ".resourceDescriptorPayload.planned.count", Long.toString(resourceDescriptorPayloadPlannedCount()));
        fields.put(prefix + ".textureDescriptorPayload.planned.count", Long.toString(textureDescriptorPayloadPlannedCount()));
        fields.put(prefix + ".activeDescriptorPayload.count", Long.toString(activeDescriptorPayloadCount()));
        fields.put(prefix + ".activeNativeDescriptor.count", Long.toString(activeNativeDescriptorCount()));
        fields.put(prefix + ".nativeDescriptorLayout.status", nativeDescriptorLayoutStatus);
        fields.put(prefix + ".nativeDescriptorLayout.ready", Boolean.toString(nativeDescriptorLayoutReady));
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < cases.size(); index++) {
            fields.putAll(cases.get(index).artifactFields(prefix + ".case." + index));
        }
    }

    private static Case runCase(
            String key,
            List<GpuKernelParameterDescriptor> parameters,
            Object[] arguments,
            String expectedPlanStatus,
            String expectedFirstBlocker
    ) {
        return new Case(
                key,
                expectedPlanStatus,
                expectedFirstBlocker,
                CudaImageSamplerDescriptorBuildPlan.from(parameters, arguments)
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
