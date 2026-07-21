package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.Image1DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DBufferReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DBufferWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DMipmappedReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DMipmappedWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DMsaaReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DMsaaWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardware-free contract for Java-side CUDA image/sampler descriptor payload construction.
 */
public record CudaImageSamplerDescriptorPayloadModelReport(List<Case> cases) {

    public record Case(
            String key,
            String expectedModelStatus,
            String expectedFirstBlocker,
            int expectedResourcePayloads,
            int expectedTexturePayloads,
            CudaImageSamplerDescriptorPayloadModel model
    ) {
        public Case {
            key = normalize(key, "unknown");
            expectedModelStatus = normalize(expectedModelStatus, "ready");
            expectedFirstBlocker = normalize(expectedFirstBlocker, "none");
            expectedResourcePayloads = Math.max(0, expectedResourcePayloads);
            expectedTexturePayloads = Math.max(0, expectedTexturePayloads);
            model = model == null ? CudaImageSamplerDescriptorPayloadModel.empty() : model;
        }

        public boolean ready() {
            return expectedModelStatus.equals(model.status())
                    && expectedFirstBlocker.equals(model.firstBlocker())
                    && expectedResourcePayloads == model.resourcePayloadBuiltCount()
                    && expectedTexturePayloads == model.texturePayloadBuiltCount();
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public String firstBlocker() {
            if (ready()) {
                return "none";
            }
            if (!expectedModelStatus.equals(model.status())) {
                return "cuda-image-sampler-descriptor-payload-model-status-mismatch:" + key + ':' + model.status();
            }
            if (!expectedFirstBlocker.equals(model.firstBlocker())) {
                return "cuda-image-sampler-descriptor-payload-model-blocker-mismatch:" + key + ':' + model.firstBlocker();
            }
            if (expectedResourcePayloads != model.resourcePayloadBuiltCount()) {
                return "cuda-image-sampler-descriptor-payload-model-resource-count-mismatch:" + key + ':' + model.resourcePayloadBuiltCount();
            }
            if (expectedTexturePayloads != model.texturePayloadBuiltCount()) {
                return "cuda-image-sampler-descriptor-payload-model-texture-count-mismatch:" + key + ':' + model.texturePayloadBuiltCount();
            }
            return "cuda-image-sampler-descriptor-payload-model-case-not-ready:" + key;
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerDescriptorPayloadModelReport.case"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".key", key);
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".expected.model.status", expectedModelStatus);
            fields.put(normalizedPrefix + ".expected.firstBlocker", expectedFirstBlocker);
            fields.put(normalizedPrefix + ".expected.resourcePayload.count", Integer.toString(expectedResourcePayloads));
            fields.put(normalizedPrefix + ".expected.texturePayload.count", Integer.toString(expectedTexturePayloads));
            fields.put(normalizedPrefix + ".actual.model.status", model.status());
            fields.put(normalizedPrefix + ".actual.firstBlocker", model.firstBlocker());
            fields.putAll(model.artifactFields(normalizedPrefix + ".model"));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerDescriptorPayloadModelReport {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public static CudaImageSamplerDescriptorPayloadModelReport inspectBuiltIns() {
        ArrayList<Case> cases = new ArrayList<>();
        cases.add(runCase(
                "all-builtins-valid",
                allBuiltInParameters(),
                allBuiltInArguments(),
                "ready",
                "none",
                16,
                9
        ));
        cases.add(runCase(
                "image2d-handle-missing",
                List.of(new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY)),
                new Object[]{new Image2DReadOnly()},
                "blocked",
                "cuda-image-descriptor-handle-missing:0:" + Image2DReadOnly.class.getName(),
                0,
                0
        ));
        Sampler closedSampler = Sampler.borrowed(0xCAFE_7001L);
        closedSampler.close();
        cases.add(runCase(
                "sampler-closed",
                List.of(new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE)),
                new Object[]{closedSampler},
                "blocked",
                "cuda-sampler-descriptor-handle-closed:0:" + Sampler.class.getName(),
                0,
                0
        ));
        return new CudaImageSamplerDescriptorPayloadModelReport(cases);
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

    public long modelReadyCount() {
        return cases.stream().filter(testCase -> "ready".equals(testCase.model().status())).count();
    }

    public long modelBlockedCount() {
        return cases.stream().filter(testCase -> "blocked".equals(testCase.model().status())).count();
    }

    public long entryCount() {
        return cases.stream().mapToLong(testCase -> testCase.model().entries().size()).sum();
    }

    public long resourcePayloadBuiltCount() {
        return cases.stream().mapToLong(testCase -> testCase.model().resourcePayloadBuiltCount()).sum();
    }

    public long texturePayloadBuiltCount() {
        return cases.stream().mapToLong(testCase -> testCase.model().texturePayloadBuiltCount()).sum();
    }

    public long samplerPayloadBuiltCount() {
        return cases.stream().mapToLong(testCase -> testCase.model().samplerPayloadCount()).sum();
    }

    public long activeNativeDescriptorCount() {
        return cases.stream().mapToLong(testCase -> testCase.model().activeNativeDescriptorCount()).sum();
    }

    public String firstBlocker() {
        if (cases.isEmpty()) {
            return "cuda-image-sampler-descriptor-payload-model-cases-missing";
        }
        return cases.stream()
                .filter(testCase -> !testCase.ready())
                .map(Case::firstBlocker)
                .findFirst()
                .orElse("none");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerDescriptorPayloadModelReport"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerDescriptorPayloadModelReport");
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler descriptor payload model: ").append(status()).append('\n');
        builder.append("Cases: ").append(caseReadyCount()).append('/').append(cases.size()).append(" ready").append('\n');
        builder.append("Models: ready=").append(modelReadyCount()).append(", blocked=").append(modelBlockedCount()).append('\n');
        builder.append("Entries: ").append(entryCount()).append('\n');
        builder.append("Java payloads: resource=").append(resourcePayloadBuiltCount())
                .append(", texture=").append(texturePayloadBuiltCount())
                .append(", sampler=").append(samplerPayloadBuiltCount()).append('\n');
        builder.append("Native descriptors active: ").append(activeNativeDescriptorCount()).append('\n');
        builder.append("First blocker: ").append(firstBlocker()).append('\n');
        builder.append('\n').append("Cases:").append('\n');
        for (Case testCase : cases) {
            builder.append("- ")
                    .append(testCase.key())
                    .append(": case=")
                    .append(testCase.status())
                    .append(", model=")
                    .append(testCase.model().status())
                    .append(", firstBlocker=")
                    .append(testCase.model().firstBlocker())
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
        fields.put(prefix + ".model.ready.count", Long.toString(modelReadyCount()));
        fields.put(prefix + ".model.blocked.count", Long.toString(modelBlockedCount()));
        fields.put(prefix + ".entry.count", Long.toString(entryCount()));
        fields.put(prefix + ".resourcePayload.built.count", Long.toString(resourcePayloadBuiltCount()));
        fields.put(prefix + ".texturePayload.built.count", Long.toString(texturePayloadBuiltCount()));
        fields.put(prefix + ".samplerPayload.built.count", Long.toString(samplerPayloadBuiltCount()));
        fields.put(prefix + ".activeNativeDescriptor.count", Long.toString(activeNativeDescriptorCount()));
        fields.put(prefix + ".nativeDescriptorAllocation.enabled", "false");
        fields.put(prefix + ".objectCreation.enabled", "false");
        fields.put(prefix + ".runtimeBinding.enabled", "false");
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < cases.size(); index++) {
            fields.putAll(cases.get(index).artifactFields(prefix + ".case." + index));
        }
    }

    private static Case runCase(
            String key,
            List<GpuKernelParameterDescriptor> parameters,
            Object[] arguments,
            String expectedModelStatus,
            String expectedFirstBlocker,
            int expectedResourcePayloads,
            int expectedTexturePayloads
    ) {
        CudaImageSamplerDescriptorBuildPlan buildPlan = CudaImageSamplerDescriptorBuildPlan.from(parameters, arguments);
        return new Case(
                key,
                expectedModelStatus,
                expectedFirstBlocker,
                expectedResourcePayloads,
                expectedTexturePayloads,
                CudaImageSamplerDescriptorPayloadModel.from(buildPlan)
        );
    }

    private static List<GpuKernelParameterDescriptor> allBuiltInParameters() {
        return CudaImageSamplerAbi.descriptors().stream()
                .map(descriptor -> new GpuKernelParameterDescriptor(
                        descriptor.key().replace('-', '_'),
                        descriptor.javaQualifiedName(),
                        access(descriptor)
                ))
                .toList();
    }

    private static Object[] allBuiltInArguments() {
        return CudaImageSamplerAbi.descriptors().stream()
                .map(CudaImageSamplerDescriptorPayloadModelReport::syntheticArgument)
                .toArray(Object[]::new);
    }

    private static GpuKernelParameterAccess access(CudaImageSamplerAbi.Descriptor descriptor) {
        if (descriptor.sampler()) {
            return GpuKernelParameterAccess.VALUE;
        }
        return descriptor.readTextureObject() ? GpuKernelParameterAccess.READ_ONLY : GpuKernelParameterAccess.READ_WRITE;
    }

    private static Object syntheticArgument(CudaImageSamplerAbi.Descriptor descriptor) {
        long base = 0xCAFE_6000L;
        return switch (descriptor.key()) {
            case "image1d-read-only" -> Image1DReadOnly.borrowed(base + 1, 8);
            case "image1d-write-only" -> Image1DWriteOnly.borrowed(base + 2, 8);
            case "image1d-array-read-only" -> Image1DArrayReadOnly.borrowed(base + 3, 8, 2);
            case "image1d-array-write-only" -> Image1DArrayWriteOnly.borrowed(base + 4, 8, 2);
            case "image1d-buffer-read-only" -> new Image1DBufferReadOnly(base + 5, 8, base + 1005);
            case "image1d-buffer-write-only" -> new Image1DBufferWriteOnly(base + 6, 8, base + 1006);
            case "image2d-read-only" -> Image2DReadOnly.borrowed(base + 7, 8, 4);
            case "image2d-write-only" -> Image2DWriteOnly.borrowed(base + 8, 8, 4);
            case "image2d-mipmapped-read-only" -> Image2DMipmappedReadOnly.borrowed(base + 9, 8, 4, 3);
            case "image2d-mipmapped-write-only" -> Image2DMipmappedWriteOnly.borrowed(base + 10, 8, 4, 3);
            case "image2d-msaa-read-only" -> Image2DMsaaReadOnly.borrowed(base + 11, 8, 4, 4);
            case "image2d-msaa-write-only" -> Image2DMsaaWriteOnly.borrowed(base + 12, 8, 4, 4);
            case "image2d-array-read-only" -> Image2DArrayReadOnly.borrowed(base + 13, 8, 4, 2);
            case "image2d-array-write-only" -> Image2DArrayWriteOnly.borrowed(base + 14, 8, 4, 2);
            case "image3d-read-only" -> Image3DReadOnly.borrowed(base + 15, 8, 4, 2);
            case "image3d-write-only" -> Image3DWriteOnly.borrowed(base + 16, 8, 4, 2);
            case "sampler" -> Sampler.borrowed(base + 17);
            default -> throw new IllegalArgumentException("Unknown CUDA image/sampler descriptor key: " + descriptor.key());
        };
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
