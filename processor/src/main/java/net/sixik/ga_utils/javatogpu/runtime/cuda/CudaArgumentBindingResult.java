package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationBindingSummary;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Result of binding Java invocation arguments to CUDA kernel argument slots.
 */
public record CudaArgumentBindingResult(
        String binderId,
        String status,
        GpuRuntimeInvocationBindingSummary bindingSummary,
        CudaExecutionPlan executionPlan,
        CudaKernelArgumentFrame argumentFrame,
        CudaImageSamplerRuntimeBindingPlan imageSamplerRuntimeBindingPlan,
        CudaImageSamplerDescriptorBuildPlan imageSamplerDescriptorBuildPlan,
        CudaImageSamplerDescriptorPayloadModel imageSamplerDescriptorPayloadModel,
        CudaImageSamplerNativeDescriptorEncodingPlan imageSamplerNativeDescriptorEncodingPlan,
        CudaImageSamplerObjectCreationRequestPlan imageSamplerObjectCreationRequestPlan,
        List<String> blockers,
        List<String> diagnostics
) {

    public CudaArgumentBindingResult {
        binderId = binderId == null || binderId.isBlank() ? "cuda-argument-binder:unknown" : binderId.trim();
        status = status == null || status.isBlank() ? "unknown" : status.trim();
        bindingSummary = bindingSummary == null ? GpuRuntimeInvocationBindingSummary.empty() : bindingSummary;
        imageSamplerRuntimeBindingPlan = imageSamplerRuntimeBindingPlan == null
                ? CudaImageSamplerRuntimeBindingPlan.empty()
                : imageSamplerRuntimeBindingPlan;
        imageSamplerDescriptorBuildPlan = imageSamplerDescriptorBuildPlan == null
                ? CudaImageSamplerDescriptorBuildPlan.empty()
                : imageSamplerDescriptorBuildPlan;
        imageSamplerDescriptorPayloadModel = imageSamplerDescriptorPayloadModel == null
                ? CudaImageSamplerDescriptorPayloadModel.from(imageSamplerDescriptorBuildPlan)
                : imageSamplerDescriptorPayloadModel;
        imageSamplerNativeDescriptorEncodingPlan = imageSamplerNativeDescriptorEncodingPlan == null
                ? CudaImageSamplerNativeDescriptorEncodingPlan.from(imageSamplerDescriptorPayloadModel)
                : imageSamplerNativeDescriptorEncodingPlan;
        imageSamplerObjectCreationRequestPlan = imageSamplerObjectCreationRequestPlan == null
                ? CudaImageSamplerObjectCreationRequestPlan.from(imageSamplerNativeDescriptorEncodingPlan)
                : imageSamplerObjectCreationRequestPlan;
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static CudaArgumentBindingResult disabled(String binderMode) {
        return new CudaArgumentBindingResult(
                binderId(binderMode),
                "disabled",
                GpuRuntimeInvocationBindingSummary.empty(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("cuda-native-argument-binder-disabled"),
                List.of("CUDA native argument binder was not requested")
        );
    }

    public static CudaArgumentBindingResult unsupported(
            String binderMode,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new CudaArgumentBindingResult(
                binderId(binderMode),
                "unsupported",
                GpuRuntimeInvocationBindingSummary.empty(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                blockers,
                diagnostics
        );
    }

    public static CudaArgumentBindingResult unsupportedWithExecutionPlan(
            String binderMode,
            CudaExecutionPlan executionPlan,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return unsupportedWithExecutionPlan(binderMode, executionPlan, null, blockers, diagnostics);
    }

    public static CudaArgumentBindingResult unsupportedWithExecutionPlan(
            String binderMode,
            CudaExecutionPlan executionPlan,
            CudaImageSamplerRuntimeBindingPlan imageSamplerRuntimeBindingPlan,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return unsupportedWithExecutionPlan(
                binderMode,
                executionPlan,
                imageSamplerRuntimeBindingPlan,
                null,
                blockers,
                diagnostics
        );
    }

    public static CudaArgumentBindingResult unsupportedWithExecutionPlan(
            String binderMode,
            CudaExecutionPlan executionPlan,
            CudaImageSamplerRuntimeBindingPlan imageSamplerRuntimeBindingPlan,
            CudaImageSamplerDescriptorBuildPlan imageSamplerDescriptorBuildPlan,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new CudaArgumentBindingResult(
                binderId(binderMode),
                "unsupported",
                GpuRuntimeInvocationBindingSummary.empty(),
                executionPlan,
                null,
                imageSamplerRuntimeBindingPlan,
                imageSamplerDescriptorBuildPlan,
                null,
                null,
                null,
                blockers,
                diagnostics
        );
    }

    public static CudaArgumentBindingResult failed(
            String binderId,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new CudaArgumentBindingResult(
                binderId,
                "failed",
                GpuRuntimeInvocationBindingSummary.empty(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                blockers,
                diagnostics
        );
    }

    public static CudaArgumentBindingResult failedWithExecutionPlan(
            String binderId,
            CudaExecutionPlan executionPlan,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new CudaArgumentBindingResult(
                binderId,
                "failed",
                GpuRuntimeInvocationBindingSummary.empty(),
                executionPlan,
                null,
                null,
                null,
                null,
                null,
                null,
                blockers,
                diagnostics
        );
    }

    public static CudaArgumentBindingResult succeeded(
            String binderId,
            GpuRuntimeInvocationBindingSummary bindingSummary,
            List<String> diagnostics
    ) {
        return succeeded(binderId, bindingSummary, null, diagnostics);
    }

    public static CudaArgumentBindingResult succeeded(
            String binderId,
            GpuRuntimeInvocationBindingSummary bindingSummary,
            CudaKernelArgumentFrame argumentFrame,
            List<String> diagnostics
    ) {
        return succeeded(binderId, bindingSummary, argumentFrame, diagnostics, null);
    }

    public static CudaArgumentBindingResult succeeded(
            String binderId,
            GpuRuntimeInvocationBindingSummary bindingSummary,
            CudaKernelArgumentFrame argumentFrame,
            List<String> diagnostics,
            CudaExecutionPlan executionPlan
    ) {
        return new CudaArgumentBindingResult(
                binderId,
                "succeeded",
                bindingSummary,
                executionPlan,
                argumentFrame,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                diagnostics
        );
    }

    public boolean succeeded() {
        return "succeeded".equals(status);
    }

    public Optional<String> firstBlocker() {
        return blockers.stream().findFirst();
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.cuda.argumentBinding" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.argumentBinding");
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", "true");
        fields.put(prefix + ".binder.id", binderId);
        fields.put(prefix + ".status", status);
        fields.put(prefix + ".succeeded", Boolean.toString(succeeded()));
        fields.put(prefix + ".binding.argument.count", Integer.toString(bindingSummary.argumentBindingCount()));
        fields.put(prefix + ".binding.buffer.count", Integer.toString(bindingSummary.bufferBindingCount()));
        fields.put(prefix + ".binding.local.count", Integer.toString(bindingSummary.localBindingCount()));
        fields.put(prefix + ".binding.scalar.count", Integer.toString(bindingSummary.scalarBindingCount()));
        fields.put(prefix + ".executionPlan.present", Boolean.toString(executionPlan != null));
        if (executionPlan != null) {
            fields.putAll(executionPlan.artifactFields(prefix + ".executionPlan"));
        }
        fields.put(prefix + ".imageSamplerRuntimeBindingPlan.present", Boolean.toString(imageSamplerRuntimeBindingPlan.present()));
        if (imageSamplerRuntimeBindingPlan.present()) {
            fields.putAll(imageSamplerRuntimeBindingPlan.artifactFields(prefix + ".imageSamplerRuntimeBindingPlan"));
        }
        fields.put(prefix + ".imageSamplerDescriptorBuildPlan.present", Boolean.toString(imageSamplerDescriptorBuildPlan.present()));
        if (imageSamplerDescriptorBuildPlan.present()) {
            fields.putAll(imageSamplerDescriptorBuildPlan.artifactFields(prefix + ".imageSamplerDescriptorBuildPlan"));
        }
        fields.put(prefix + ".imageSamplerDescriptorPayloadModel.present", Boolean.toString(imageSamplerDescriptorPayloadModel.present()));
        if (imageSamplerDescriptorPayloadModel.present()) {
            fields.putAll(imageSamplerDescriptorPayloadModel.artifactFields(prefix + ".imageSamplerDescriptorPayloadModel"));
        }
        fields.put(prefix + ".imageSamplerNativeDescriptorEncodingPlan.present", Boolean.toString(imageSamplerNativeDescriptorEncodingPlan.present()));
        if (imageSamplerNativeDescriptorEncodingPlan.present()) {
            fields.putAll(imageSamplerNativeDescriptorEncodingPlan.artifactFields(prefix + ".imageSamplerNativeDescriptorEncodingPlan"));
        }
        fields.put(prefix + ".imageSamplerObjectCreationRequestPlan.present", Boolean.toString(imageSamplerObjectCreationRequestPlan.present()));
        if (imageSamplerObjectCreationRequestPlan.present()) {
            fields.putAll(imageSamplerObjectCreationRequestPlan.artifactFields(prefix + ".imageSamplerObjectCreationRequestPlan"));
        }
        fields.put(prefix + ".argumentFrame.present", Boolean.toString(argumentFrame != null));
        if (argumentFrame != null) {
            fields.putAll(argumentFrame.artifactFields(prefix + ".argumentFrame"));
        }
        fields.put(prefix + ".blocker.count", Integer.toString(blockers.size()));
        for (int index = 0; index < blockers.size(); index++) {
            fields.put(prefix + ".blocker." + index, blockers.get(index));
        }
    }

    private static String binderId(String binderMode) {
        return binderMode == null || binderMode.isBlank()
                ? "cuda-argument-binder:unknown"
                : "cuda-argument-binder:" + binderMode.trim();
    }
}
