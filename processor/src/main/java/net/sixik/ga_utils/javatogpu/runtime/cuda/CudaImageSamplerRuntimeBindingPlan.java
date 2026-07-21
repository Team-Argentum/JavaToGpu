package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardware-free CUDA image/sampler runtime binding preflight.
 *
 * <p>The plan records the texture/surface/sampler argument shape that a future CUDA binder must create, while keeping
 * active runtime binding disabled and fail-closed.</p>
 */
public record CudaImageSamplerRuntimeBindingPlan(List<Entry> entries) {

    public record Entry(
            int parameterIndex,
            String parameterName,
            String javaType,
            String abiKey,
            String cudaAbiRole,
            String cudaResourceKind,
            String parameterCarrier,
            String sourcePreviewStatus,
            String sourcePreviewCarrier,
            int sourcePreviewKernelParameterSlotCount,
            int sourcePreviewMetadataSlotCount,
            int plannedRuntimeKernelParameterSlotCount,
            int plannedRuntimeMetadataSlotCount,
            String runtimeBindingStatus,
            int runtimeBindingKernelParameterSlotCount,
            boolean argumentPresent,
            String argumentType,
            boolean argumentCompatible,
            boolean handlePresent,
            boolean handleValid,
            int metadataWidth,
            int metadataHeight,
            boolean metadataAvailable,
            String firstBlocker
    ) {
        public Entry {
            parameterIndex = Math.max(0, parameterIndex);
            parameterName = normalize(parameterName, "unknown");
            javaType = normalize(javaType, "unknown");
            abiKey = normalize(abiKey, "unknown");
            cudaAbiRole = normalize(cudaAbiRole, "unknown");
            cudaResourceKind = normalize(cudaResourceKind, "unknown");
            parameterCarrier = normalize(parameterCarrier, "unknown");
            sourcePreviewStatus = normalize(sourcePreviewStatus, "pending");
            sourcePreviewCarrier = normalize(sourcePreviewCarrier, "pending");
            sourcePreviewKernelParameterSlotCount = Math.max(0, sourcePreviewKernelParameterSlotCount);
            sourcePreviewMetadataSlotCount = Math.max(0, sourcePreviewMetadataSlotCount);
            plannedRuntimeKernelParameterSlotCount = Math.max(0, plannedRuntimeKernelParameterSlotCount);
            plannedRuntimeMetadataSlotCount = Math.max(0, plannedRuntimeMetadataSlotCount);
            runtimeBindingStatus = normalize(runtimeBindingStatus, "fail-closed");
            runtimeBindingKernelParameterSlotCount = Math.max(0, runtimeBindingKernelParameterSlotCount);
            argumentType = normalize(argumentType, argumentPresent ? "unknown" : "missing");
            metadataWidth = Math.max(0, metadataWidth);
            metadataHeight = Math.max(0, metadataHeight);
            firstBlocker = normalize(firstBlocker, "none");
        }

        boolean runtimeBindingEnabled() {
            return !"fail-closed".equals(runtimeBindingStatus);
        }

        boolean image() {
            return !"texture-descriptor-state".equals(cudaAbiRole);
        }

        boolean sampler() {
            return "texture-descriptor-state".equals(cudaAbiRole);
        }

        Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerRuntimeBindingPlan.entry"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".parameter.index", Integer.toString(parameterIndex));
            fields.put(normalizedPrefix + ".parameter.name", parameterName);
            fields.put(normalizedPrefix + ".parameter.javaType", javaType);
            fields.put(normalizedPrefix + ".abi.key", abiKey);
            fields.put(normalizedPrefix + ".abi.role", cudaAbiRole);
            fields.put(normalizedPrefix + ".resource.kind", cudaResourceKind);
            fields.put(normalizedPrefix + ".parameter.carrier", parameterCarrier);
            fields.put(normalizedPrefix + ".sourcePreview.status", sourcePreviewStatus);
            fields.put(normalizedPrefix + ".sourcePreview.carrier", sourcePreviewCarrier);
            fields.put(normalizedPrefix + ".sourcePreview.kernelParameterSlot.count", Integer.toString(sourcePreviewKernelParameterSlotCount));
            fields.put(normalizedPrefix + ".sourcePreview.metadataSlot.count", Integer.toString(sourcePreviewMetadataSlotCount));
            fields.put(normalizedPrefix + ".runtimeBinding.status", runtimeBindingStatus);
            fields.put(normalizedPrefix + ".runtimeBinding.enabled", Boolean.toString(runtimeBindingEnabled()));
            fields.put(normalizedPrefix + ".runtimeBinding.kernelParameterSlot.count", Integer.toString(runtimeBindingKernelParameterSlotCount));
            fields.put(normalizedPrefix + ".runtimeBinding.plannedKernelParameterSlot.count", Integer.toString(plannedRuntimeKernelParameterSlotCount));
            fields.put(normalizedPrefix + ".runtimeBinding.plannedMetadataSlot.count", Integer.toString(plannedRuntimeMetadataSlotCount));
            fields.put(normalizedPrefix + ".argument.present", Boolean.toString(argumentPresent));
            fields.put(normalizedPrefix + ".argument.type", argumentType);
            fields.put(normalizedPrefix + ".argument.compatible", Boolean.toString(argumentCompatible));
            fields.put(normalizedPrefix + ".argument.handle.present", Boolean.toString(handlePresent));
            fields.put(normalizedPrefix + ".argument.handle.valid", Boolean.toString(handleValid));
            fields.put(normalizedPrefix + ".argument.metadata.width", Integer.toString(metadataWidth));
            fields.put(normalizedPrefix + ".argument.metadata.height", Integer.toString(metadataHeight));
            fields.put(normalizedPrefix + ".argument.metadata.available", Boolean.toString(metadataAvailable));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerRuntimeBindingPlan {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    static CudaImageSamplerRuntimeBindingPlan empty() {
        return new CudaImageSamplerRuntimeBindingPlan(List.of());
    }

    static CudaImageSamplerRuntimeBindingPlan from(
            List<GpuKernelParameterDescriptor> parameters,
            Object[] arguments
    ) {
        ArrayList<Entry> entries = new ArrayList<>();
        List<GpuKernelParameterDescriptor> safeParameters = parameters == null ? List.of() : parameters;
        for (int index = 0; index < safeParameters.size(); index++) {
            GpuKernelParameterDescriptor parameter = safeParameters.get(index);
            if (parameter == null) {
                continue;
            }
            int parameterIndex = index;
            Object argument = argumentAt(arguments, parameterIndex);
            CudaImageSamplerAbi.descriptorFor(parameter.javaType())
                    .map(descriptor -> entry(parameterIndex, parameter, descriptor, argument))
                    .ifPresent(entries::add);
        }
        return new CudaImageSamplerRuntimeBindingPlan(entries);
    }

    boolean present() {
        return !entries.isEmpty();
    }

    boolean runtimeBindingEnabled() {
        return entries.stream().anyMatch(Entry::runtimeBindingEnabled);
    }

    long imageEntryCount() {
        return entries.stream().filter(Entry::image).count();
    }

    long samplerEntryCount() {
        return entries.stream().filter(Entry::sampler).count();
    }

    long compatibleArgumentCount() {
        return entries.stream().filter(Entry::argumentCompatible).count();
    }

    long metadataAvailableCount() {
        return entries.stream().filter(Entry::metadataAvailable).count();
    }

    long sourcePreviewKernelParameterSlotCount() {
        return entries.stream().mapToLong(Entry::sourcePreviewKernelParameterSlotCount).sum();
    }

    long sourcePreviewMetadataSlotCount() {
        return entries.stream().mapToLong(Entry::sourcePreviewMetadataSlotCount).sum();
    }

    long plannedRuntimeKernelParameterSlotCount() {
        return entries.stream().mapToLong(Entry::plannedRuntimeKernelParameterSlotCount).sum();
    }

    long plannedRuntimeMetadataSlotCount() {
        return entries.stream().mapToLong(Entry::plannedRuntimeMetadataSlotCount).sum();
    }

    long runtimeBindingKernelParameterSlotCount() {
        return entries.stream().mapToLong(Entry::runtimeBindingKernelParameterSlotCount).sum();
    }

    String status() {
        if (!present()) {
            return "not-present";
        }
        return runtimeBindingEnabled() ? "enabled" : "fail-closed";
    }

    String firstBlocker() {
        if (!present()) {
            return "none";
        }
        return entries.stream()
                .map(Entry::firstBlocker)
                .filter(blocker -> !"none".equals(blocker))
                .findFirst()
                .orElse("none");
    }

    Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerRuntimeBindingPlan"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerRuntimeBindingPlan");
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", Boolean.toString(present()));
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".runtimeBinding.enabled", Boolean.toString(runtimeBindingEnabled()));
        fields.put(prefix + ".entry.count", Integer.toString(entries.size()));
        fields.put(prefix + ".entry.image.count", Long.toString(imageEntryCount()));
        fields.put(prefix + ".entry.sampler.count", Long.toString(samplerEntryCount()));
        fields.put(prefix + ".argument.compatible.count", Long.toString(compatibleArgumentCount()));
        fields.put(prefix + ".argument.metadata.available.count", Long.toString(metadataAvailableCount()));
        fields.put(prefix + ".sourcePreview.kernelParameterSlot.count", Long.toString(sourcePreviewKernelParameterSlotCount()));
        fields.put(prefix + ".sourcePreview.metadataSlot.count", Long.toString(sourcePreviewMetadataSlotCount()));
        fields.put(prefix + ".runtimeBinding.plannedKernelParameterSlot.count", Long.toString(plannedRuntimeKernelParameterSlotCount()));
        fields.put(prefix + ".runtimeBinding.plannedMetadataSlot.count", Long.toString(plannedRuntimeMetadataSlotCount()));
        fields.put(prefix + ".runtimeBinding.kernelParameterSlot.count", Long.toString(runtimeBindingKernelParameterSlotCount()));
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < entries.size(); index++) {
            fields.putAll(entries.get(index).artifactFields(prefix + ".entry." + index));
        }
    }

    private static Entry entry(
            int parameterIndex,
            GpuKernelParameterDescriptor parameter,
            CudaImageSamplerAbi.Descriptor descriptor,
            Object argument
    ) {
        boolean argumentPresent = argument != null;
        String argumentType = argumentPresent ? argument.getClass().getName() : "missing";
        boolean argumentCompatible = argumentPresent && argumentCompatible(parameter.javaType(), argument);
        long handle = argumentPresent ? longMethod(argument, "handle") : 0L;
        int width = argumentPresent ? intMethod(argument, "width") : 0;
        int height = argumentPresent ? intMethod(argument, "height") : 0;
        boolean handlePresent = argumentPresent && handle != 0L;
        boolean handleValid = handlePresent && !booleanMethod(argument, "closed");
        boolean metadataAvailable = descriptor.sampler()
                || descriptor.plannedRuntimeMetadataSlotCount() == 0
                || metadataAvailable(descriptor, width, height);
        return new Entry(
                parameterIndex,
                parameter.name(),
                parameter.javaType(),
                descriptor.key(),
                descriptor.cudaAbiRole(),
                descriptor.cudaResourceKind(),
                descriptor.parameterCarrier(),
                descriptor.sourcePreviewStatus(),
                descriptor.sourcePreviewCarrier(),
                descriptor.sourcePreviewKernelParameterSlotCount(),
                descriptor.sourcePreviewMetadataSlotCount(),
                descriptor.plannedRuntimeKernelParameterSlotCount(),
                descriptor.plannedRuntimeMetadataSlotCount(),
                descriptor.runtimeBindingStatus(),
                descriptor.runtimeBindingKernelParameterSlotCount(),
                argumentPresent,
                argumentType,
                argumentCompatible,
                handlePresent,
                handleValid,
                width,
                height,
                metadataAvailable,
                firstBlocker(parameterIndex, parameter, descriptor, argumentPresent, argumentCompatible, metadataAvailable)
        );
    }

    private static String firstBlocker(
            int parameterIndex,
            GpuKernelParameterDescriptor parameter,
            CudaImageSamplerAbi.Descriptor descriptor,
            boolean argumentPresent,
            boolean argumentCompatible,
            boolean metadataAvailable
    ) {
        String kind = descriptor.sampler() ? "sampler" : "image";
        if (!argumentPresent) {
            return "cuda-" + kind + "-runtime-argument-missing:" + parameterIndex + ':' + parameter.javaType();
        }
        if (!argumentCompatible) {
            return "cuda-" + kind + "-runtime-argument-type-mismatch:" + parameterIndex + ':' + parameter.javaType();
        }
        if (!metadataAvailable) {
            return "cuda-image-runtime-metadata-missing:" + parameterIndex + ':' + parameter.javaType();
        }
        return "cuda-" + kind + "-runtime-binding-disabled:" + parameterIndex + ':' + parameter.javaType();
    }

    private static boolean metadataAvailable(CudaImageSamplerAbi.Descriptor descriptor, int width, int height) {
        if (descriptor.plannedRuntimeMetadataSlotCount() >= 2) {
            return width > 0 && height > 0;
        }
        if (descriptor.plannedRuntimeMetadataSlotCount() == 1) {
            return width > 0 || height > 0;
        }
        return true;
    }

    private static Object argumentAt(Object[] arguments, int index) {
        if (arguments == null || index < 0 || index >= arguments.length) {
            return null;
        }
        return arguments[index];
    }

    private static boolean argumentCompatible(String javaType, Object argument) {
        if (javaType == null || argument == null) {
            return false;
        }
        String expected = javaType.trim();
        Class<?> actualClass = argument.getClass();
        return expected.equals(actualClass.getName()) || expected.equals(actualClass.getSimpleName());
    }

    private static long longMethod(Object target, String name) {
        Object value = invokeNoArg(target, name);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static int intMethod(Object target, String name) {
        Object value = invokeNoArg(target, name);
        return value instanceof Number number ? Math.max(0, number.intValue()) : 0;
    }

    private static boolean booleanMethod(Object target, String name) {
        Object value = invokeNoArg(target, name);
        return value instanceof Boolean bool && bool;
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null || name == null || name.isBlank()) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
