package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Java-side CUDA image/sampler descriptor build plan.
 *
 * <p>The plan validates invocation arguments and records the logical descriptor payloads a future CUDA binder needs.
 * It deliberately does not allocate native descriptor memory and does not create texture/surface objects.</p>
 */
public record CudaImageSamplerDescriptorBuildPlan(
        List<Entry> entries,
        String nativeDescriptorLayoutStatus,
        boolean nativeDescriptorLayoutReady,
        boolean javaDescriptorPlanEnabled,
        boolean descriptorPayloadBuildEnabled,
        boolean nativeDescriptorAllocationEnabled,
        boolean objectCreationEnabled,
        int activeDescriptorPayloadCount,
        int activeNativeDescriptorCount
) {

    public record Entry(
            int parameterIndex,
            String parameterName,
            String javaType,
            String abiKey,
            String cudaAbiRole,
            String cudaResourceKind,
            String resourceDescriptorKind,
            String resourceDescriptorDimension,
            boolean resourceLayoutRequired,
            String resourceLayoutStruct,
            int resourceLayoutFieldCount,
            boolean textureLayoutRequired,
            String textureLayoutStruct,
            int textureLayoutFieldCount,
            boolean argumentPresent,
            String argumentType,
            boolean argumentCompatible,
            boolean handlePresent,
            boolean handleValid,
            int metadataWidth,
            int metadataHeight,
            int metadataDepth,
            int metadataLayers,
            int metadataMipLevels,
            int metadataSampleCount,
            long backingBufferHandle,
            boolean metadataAvailable,
            String descriptorPayloadStatus,
            String nativeDescriptorAllocationStatus,
            String objectCreationStatus,
            String runtimeBindingStatus,
            boolean productionSupportEnabled,
            String firstBlocker
    ) {
        public Entry {
            parameterIndex = Math.max(0, parameterIndex);
            parameterName = normalize(parameterName, "unknown");
            javaType = normalize(javaType, "unknown");
            abiKey = normalize(abiKey, "unknown");
            cudaAbiRole = normalize(cudaAbiRole, "unknown");
            cudaResourceKind = normalize(cudaResourceKind, "unknown");
            resourceDescriptorKind = normalize(resourceDescriptorKind, "not-required");
            resourceDescriptorDimension = normalize(resourceDescriptorDimension, "none");
            resourceLayoutStruct = normalize(resourceLayoutStruct, "not-required");
            resourceLayoutFieldCount = Math.max(0, resourceLayoutFieldCount);
            textureLayoutStruct = normalize(textureLayoutStruct, "not-required");
            textureLayoutFieldCount = Math.max(0, textureLayoutFieldCount);
            argumentType = normalize(argumentType, argumentPresent ? "unknown" : "missing");
            metadataWidth = Math.max(0, metadataWidth);
            metadataHeight = Math.max(0, metadataHeight);
            metadataDepth = Math.max(0, metadataDepth);
            metadataLayers = Math.max(0, metadataLayers);
            metadataMipLevels = Math.max(0, metadataMipLevels);
            metadataSampleCount = Math.max(0, metadataSampleCount);
            backingBufferHandle = Math.max(0L, backingBufferHandle);
            descriptorPayloadStatus = normalize(descriptorPayloadStatus, "planned");
            nativeDescriptorAllocationStatus = normalize(nativeDescriptorAllocationStatus, "disabled");
            objectCreationStatus = normalize(objectCreationStatus, "disabled");
            runtimeBindingStatus = normalize(runtimeBindingStatus, "fail-closed");
            firstBlocker = normalize(firstBlocker, "none");
        }

        public boolean image() {
            return !sampler();
        }

        public boolean sampler() {
            return "texture-descriptor-state".equals(cudaAbiRole);
        }

        public boolean ready() {
            return argumentPresent
                    && argumentCompatible
                    && handlePresent
                    && handleValid
                    && metadataAvailable
                    && "planned".equals(descriptorPayloadStatus)
                    && "disabled".equals(nativeDescriptorAllocationStatus)
                    && "disabled".equals(objectCreationStatus)
                    && "fail-closed".equals(runtimeBindingStatus)
                    && !productionSupportEnabled
                    && "none".equals(firstBlocker)
                    && (!resourceLayoutRequired || ("CUDA_RESOURCE_DESC".equals(resourceLayoutStruct) && resourceLayoutFieldCount > 0))
                    && (!textureLayoutRequired || ("CUDA_TEXTURE_DESC".equals(textureLayoutStruct) && textureLayoutFieldCount > 0));
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerDescriptorBuildPlan.entry"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".parameter.index", Integer.toString(parameterIndex));
            fields.put(normalizedPrefix + ".parameter.name", parameterName);
            fields.put(normalizedPrefix + ".parameter.javaType", javaType);
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".abi.key", abiKey);
            fields.put(normalizedPrefix + ".abi.role", cudaAbiRole);
            fields.put(normalizedPrefix + ".resource.kind", cudaResourceKind);
            fields.put(normalizedPrefix + ".resourceDescriptor.kind", resourceDescriptorKind);
            fields.put(normalizedPrefix + ".resourceDescriptor.dimension", resourceDescriptorDimension);
            fields.put(normalizedPrefix + ".resourceLayout.required", Boolean.toString(resourceLayoutRequired));
            fields.put(normalizedPrefix + ".resourceLayout.struct", resourceLayoutStruct);
            fields.put(normalizedPrefix + ".resourceLayout.field.count", Integer.toString(resourceLayoutFieldCount));
            fields.put(normalizedPrefix + ".textureLayout.required", Boolean.toString(textureLayoutRequired));
            fields.put(normalizedPrefix + ".textureLayout.struct", textureLayoutStruct);
            fields.put(normalizedPrefix + ".textureLayout.field.count", Integer.toString(textureLayoutFieldCount));
            fields.put(normalizedPrefix + ".argument.present", Boolean.toString(argumentPresent));
            fields.put(normalizedPrefix + ".argument.type", argumentType);
            fields.put(normalizedPrefix + ".argument.compatible", Boolean.toString(argumentCompatible));
            fields.put(normalizedPrefix + ".argument.handle.present", Boolean.toString(handlePresent));
            fields.put(normalizedPrefix + ".argument.handle.valid", Boolean.toString(handleValid));
            fields.put(normalizedPrefix + ".argument.metadata.width", Integer.toString(metadataWidth));
            fields.put(normalizedPrefix + ".argument.metadata.height", Integer.toString(metadataHeight));
            fields.put(normalizedPrefix + ".argument.metadata.depth", Integer.toString(metadataDepth));
            fields.put(normalizedPrefix + ".argument.metadata.layers", Integer.toString(metadataLayers));
            fields.put(normalizedPrefix + ".argument.metadata.mipLevels", Integer.toString(metadataMipLevels));
            fields.put(normalizedPrefix + ".argument.metadata.sampleCount", Integer.toString(metadataSampleCount));
            fields.put(normalizedPrefix + ".argument.backingBuffer.handle", Long.toString(backingBufferHandle));
            fields.put(normalizedPrefix + ".argument.metadata.available", Boolean.toString(metadataAvailable));
            fields.put(normalizedPrefix + ".descriptorPayload.status", descriptorPayloadStatus);
            fields.put(normalizedPrefix + ".nativeDescriptorAllocation.status", nativeDescriptorAllocationStatus);
            fields.put(normalizedPrefix + ".objectCreation.status", objectCreationStatus);
            fields.put(normalizedPrefix + ".runtimeBinding.status", runtimeBindingStatus);
            fields.put(normalizedPrefix + ".productionSupport.enabled", Boolean.toString(productionSupportEnabled));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
            return Collections.unmodifiableMap(fields);
        }
    }

    private record NativeLayoutEntry(
            String key,
            String resourceDescriptorKind,
            String resourceDescriptorDimension,
            boolean resourceLayoutRequired,
            String resourceLayoutStruct,
            int resourceLayoutFieldCount,
            boolean textureLayoutRequired,
            String textureLayoutStruct,
            int textureLayoutFieldCount
    ) {
    }

    public CudaImageSamplerDescriptorBuildPlan {
        entries = entries == null ? List.of() : List.copyOf(entries);
        nativeDescriptorLayoutStatus = normalize(nativeDescriptorLayoutStatus, "ready");
        activeDescriptorPayloadCount = Math.max(0, activeDescriptorPayloadCount);
        activeNativeDescriptorCount = Math.max(0, activeNativeDescriptorCount);
    }

    static CudaImageSamplerDescriptorBuildPlan empty() {
        return new CudaImageSamplerDescriptorBuildPlan(
                List.of(),
                "not-present",
                false,
                false,
                false,
                false,
                false,
                0,
                0
        );
    }

    static CudaImageSamplerDescriptorBuildPlan from(
            List<GpuKernelParameterDescriptor> parameters,
            Object[] arguments
    ) {
        Map<String, NativeLayoutEntry> layoutEntries = CudaImageSamplerAbi.descriptors().stream()
                .map(CudaImageSamplerDescriptorBuildPlan::layoutEntry)
                .collect(Collectors.toMap(NativeLayoutEntry::key, Function.identity()));
        ArrayList<Entry> entries = new ArrayList<>();
        List<GpuKernelParameterDescriptor> safeParameters = parameters == null ? List.of() : parameters;
        for (int index = 0; index < safeParameters.size(); index++) {
            GpuKernelParameterDescriptor parameter = safeParameters.get(index);
            if (parameter == null) {
                continue;
            }
            int parameterIndex = index;
            CudaImageSamplerAbi.descriptorFor(parameter.javaType())
                    .map(descriptor -> entry(
                            parameterIndex,
                            parameter,
                            descriptor,
                            layoutEntries.get(descriptor.key()),
                            argumentAt(arguments, parameterIndex)
                    ))
                    .ifPresent(entries::add);
        }
        return new CudaImageSamplerDescriptorBuildPlan(
                entries,
                "ready",
                true,
                !entries.isEmpty(),
                false,
                false,
                false,
                0,
                0
        );
    }

    public boolean present() {
        return !entries.isEmpty();
    }

    public boolean ready() {
        return present()
                && entries.stream().allMatch(Entry::ready)
                && nativeDescriptorLayoutReady
                && javaDescriptorPlanEnabled
                && !descriptorPayloadBuildEnabled
                && !nativeDescriptorAllocationEnabled
                && !objectCreationEnabled
                && activeDescriptorPayloadCount == 0
                && activeNativeDescriptorCount == 0;
    }

    public String status() {
        if (!present()) {
            return "not-present";
        }
        return ready() ? "ready" : "blocked";
    }

    public long entryReadyCount() {
        return entries.stream().filter(Entry::ready).count();
    }

    public long entryBlockedCount() {
        return entries.stream().filter(entry -> !entry.ready()).count();
    }

    public long imageEntryCount() {
        return entries.stream().filter(Entry::image).count();
    }

    public long samplerEntryCount() {
        return entries.stream().filter(Entry::sampler).count();
    }

    public long resourceDescriptorPayloadPlannedCount() {
        return entries.stream().filter(Entry::resourceLayoutRequired).count();
    }

    public long textureDescriptorPayloadPlannedCount() {
        return entries.stream().filter(Entry::textureLayoutRequired).count();
    }

    public long compatibleArgumentCount() {
        return entries.stream().filter(Entry::argumentCompatible).count();
    }

    public long validHandleCount() {
        return entries.stream().filter(Entry::handleValid).count();
    }

    public long metadataAvailableCount() {
        return entries.stream().filter(Entry::metadataAvailable).count();
    }

    public String firstBlocker() {
        if (!present()) {
            return "none";
        }
        return entries.stream()
                .map(Entry::firstBlocker)
                .filter(blocker -> !"none".equals(blocker))
                .findFirst()
                .orElseGet(() -> nativeDescriptorLayoutReady
                        ? "none"
                        : "cuda-image-sampler-native-descriptor-layout-not-ready:" + nativeDescriptorLayoutStatus);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerDescriptorBuildPlan"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerDescriptorBuildPlan");
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", Boolean.toString(present()));
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".ready", Boolean.toString(ready()));
        fields.put(prefix + ".javaDescriptorPlan.enabled", Boolean.toString(javaDescriptorPlanEnabled));
        fields.put(prefix + ".descriptorPayloadBuild.enabled", Boolean.toString(descriptorPayloadBuildEnabled));
        fields.put(prefix + ".nativeDescriptorAllocation.enabled", Boolean.toString(nativeDescriptorAllocationEnabled));
        fields.put(prefix + ".objectCreation.enabled", Boolean.toString(objectCreationEnabled));
        fields.put(prefix + ".activeDescriptorPayload.count", Integer.toString(activeDescriptorPayloadCount));
        fields.put(prefix + ".activeNativeDescriptor.count", Integer.toString(activeNativeDescriptorCount));
        fields.put(prefix + ".entry.count", Integer.toString(entries.size()));
        fields.put(prefix + ".entry.ready.count", Long.toString(entryReadyCount()));
        fields.put(prefix + ".entry.blocked.count", Long.toString(entryBlockedCount()));
        fields.put(prefix + ".entry.image.count", Long.toString(imageEntryCount()));
        fields.put(prefix + ".entry.sampler.count", Long.toString(samplerEntryCount()));
        fields.put(prefix + ".argument.compatible.count", Long.toString(compatibleArgumentCount()));
        fields.put(prefix + ".argument.handle.valid.count", Long.toString(validHandleCount()));
        fields.put(prefix + ".argument.metadata.available.count", Long.toString(metadataAvailableCount()));
        fields.put(prefix + ".resourceDescriptorPayload.planned.count", Long.toString(resourceDescriptorPayloadPlannedCount()));
        fields.put(prefix + ".textureDescriptorPayload.planned.count", Long.toString(textureDescriptorPayloadPlannedCount()));
        fields.put(prefix + ".nativeDescriptorLayout.status", nativeDescriptorLayoutStatus);
        fields.put(prefix + ".nativeDescriptorLayout.ready", Boolean.toString(nativeDescriptorLayoutReady));
        fields.put(prefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < entries.size(); index++) {
            fields.putAll(entries.get(index).artifactFields(prefix + ".entry." + index));
        }
    }

    private static Entry entry(
            int parameterIndex,
            GpuKernelParameterDescriptor parameter,
            CudaImageSamplerAbi.Descriptor descriptor,
            NativeLayoutEntry layout,
            Object argument
    ) {
        boolean argumentPresent = argument != null;
        String argumentType = argumentPresent ? argument.getClass().getName() : "missing";
        boolean argumentCompatible = argumentPresent && argumentCompatible(parameter.javaType(), argument);
        long handle = argumentPresent ? longMethod(argument, "handle") : 0L;
        boolean closed = argumentPresent && booleanMethod(argument, "closed");
        boolean handlePresent = argumentPresent && handle != 0L;
        boolean handleValid = handlePresent && !closed;
        int width = argumentPresent ? intMethod(argument, "width") : 0;
        int height = argumentPresent ? intMethod(argument, "height") : 0;
        int depth = argumentPresent ? intMethod(argument, "depth") : 0;
        int layers = argumentPresent ? intMethod(argument, "layers") : 0;
        int mipLevels = argumentPresent ? intMethod(argument, "mipLevels") : 0;
        int sampleCount = argumentPresent ? intMethod(argument, "sampleCount") : 0;
        long backingBufferHandle = argumentPresent ? longMethod(argument, "backingBufferHandle") : 0L;
        boolean metadataAvailable = descriptor.sampler() || metadataAvailable(descriptor, width, height, depth, layers, mipLevels, sampleCount);
        String blocker = firstBlocker(
                parameterIndex,
                parameter,
                descriptor,
                argumentPresent,
                argumentCompatible,
                handlePresent,
                handleValid,
                width,
                height,
                depth,
                layers,
                mipLevels,
                sampleCount
        );
        return new Entry(
                parameterIndex,
                parameter.name(),
                parameter.javaType(),
                descriptor.key(),
                descriptor.cudaAbiRole(),
                descriptor.cudaResourceKind(),
                layout == null ? "unknown" : layout.resourceDescriptorKind(),
                layout == null ? "unknown" : layout.resourceDescriptorDimension(),
                layout != null && layout.resourceLayoutRequired(),
                layout == null ? "unknown" : layout.resourceLayoutStruct(),
                layout == null ? 0 : layout.resourceLayoutFieldCount(),
                layout != null && layout.textureLayoutRequired(),
                layout == null ? "unknown" : layout.textureLayoutStruct(),
                layout == null ? 0 : layout.textureLayoutFieldCount(),
                argumentPresent,
                argumentType,
                argumentCompatible,
                handlePresent,
                handleValid,
                width,
                height,
                depth,
                layers,
                mipLevels,
                sampleCount,
                backingBufferHandle,
                metadataAvailable,
                "none".equals(blocker) ? "planned" : "blocked",
                "disabled",
                "disabled",
                descriptor.runtimeBindingStatus(),
                descriptor.productionSupportEnabled(),
                blocker
        );
    }

    private static NativeLayoutEntry layoutEntry(CudaImageSamplerAbi.Descriptor descriptor) {
        boolean sampler = descriptor.sampler();
        boolean resourceRequired = !sampler;
        boolean textureRequired = descriptor.readTextureObject() || sampler;
        String resourceDescriptorKind = resourceDescriptorKind(descriptor.cudaResourceKind(), sampler);
        return new NativeLayoutEntry(
                descriptor.key(),
                resourceDescriptorKind,
                resourceDescriptorDimension(descriptor.cudaResourceKind(), sampler),
                resourceRequired,
                resourceRequired ? "CUDA_RESOURCE_DESC" : "not-required",
                resourceLayoutFieldCount(resourceDescriptorKind),
                textureRequired,
                textureRequired ? "CUDA_TEXTURE_DESC" : "not-required",
                textureRequired ? 6 : 0
        );
    }

    private static int resourceLayoutFieldCount(String resourceDescriptorKind) {
        return switch (resourceDescriptorKind) {
            case "CUDA_RESOURCE_TYPE_LINEAR" -> 5;
            case "not-required" -> 0;
            default -> 2;
        };
    }

    private static String resourceDescriptorKind(String resourceKind, boolean sampler) {
        if (sampler) {
            return "not-required";
        }
        if (resourceKind.contains("mipmapped")) {
            return "CUDA_RESOURCE_TYPE_MIPMAPPED_ARRAY";
        }
        if (resourceKind.contains("linear-memory-or-staged-array")) {
            return "CUDA_RESOURCE_TYPE_ARRAY_STAGING_PENDING";
        }
        if (resourceKind.contains("linear")) {
            return "CUDA_RESOURCE_TYPE_LINEAR";
        }
        return "CUDA_RESOURCE_TYPE_ARRAY";
    }

    private static String resourceDescriptorDimension(String resourceKind, boolean sampler) {
        if (sampler) {
            return "none";
        }
        if (resourceKind.contains("3d")) {
            return "3d";
        }
        if (resourceKind.contains("2d")) {
            return "2d";
        }
        if (resourceKind.contains("1d") || resourceKind.contains("linear")) {
            return "1d";
        }
        return "unknown";
    }

    private static String firstBlocker(
            int parameterIndex,
            GpuKernelParameterDescriptor parameter,
            CudaImageSamplerAbi.Descriptor descriptor,
            boolean argumentPresent,
            boolean argumentCompatible,
            boolean handlePresent,
            boolean handleValid,
            int width,
            int height,
            int depth,
            int layers,
            int mipLevels,
            int sampleCount
    ) {
        String kind = descriptor.sampler() ? "sampler" : "image";
        if (!argumentPresent) {
            return "cuda-" + kind + "-descriptor-argument-missing:" + parameterIndex + ':' + parameter.javaType();
        }
        if (!argumentCompatible) {
            return "cuda-" + kind + "-descriptor-argument-type-mismatch:" + parameterIndex + ':' + parameter.javaType();
        }
        if (!handlePresent) {
            return "cuda-" + kind + "-descriptor-handle-missing:" + parameterIndex + ':' + parameter.javaType();
        }
        if (!handleValid) {
            return "cuda-" + kind + "-descriptor-handle-closed:" + parameterIndex + ':' + parameter.javaType();
        }
        String resourceKind = descriptor.cudaResourceKind();
        if (requiresWidth(resourceKind) && width <= 0) {
            return "cuda-image-descriptor-width-missing:" + parameterIndex + ':' + parameter.javaType();
        }
        if (requiresHeight(resourceKind) && height <= 0) {
            return "cuda-image-descriptor-height-missing:" + parameterIndex + ':' + parameter.javaType();
        }
        if (requiresDepth(resourceKind) && depth <= 0) {
            return "cuda-image-descriptor-depth-missing:" + parameterIndex + ':' + parameter.javaType();
        }
        if (requiresLayers(resourceKind) && layers <= 0) {
            return "cuda-image-descriptor-layers-missing:" + parameterIndex + ':' + parameter.javaType();
        }
        if (requiresMipLevels(resourceKind) && mipLevels <= 0) {
            return "cuda-image-descriptor-mip-levels-missing:" + parameterIndex + ':' + parameter.javaType();
        }
        if (requiresSampleCount(resourceKind) && sampleCount <= 0) {
            return "cuda-image-descriptor-sample-count-missing:" + parameterIndex + ':' + parameter.javaType();
        }
        return "none";
    }

    private static boolean metadataAvailable(
            CudaImageSamplerAbi.Descriptor descriptor,
            int width,
            int height,
            int depth,
            int layers,
            int mipLevels,
            int sampleCount
    ) {
        String resourceKind = descriptor.cudaResourceKind();
        return (!requiresWidth(resourceKind) || width > 0)
                && (!requiresHeight(resourceKind) || height > 0)
                && (!requiresDepth(resourceKind) || depth > 0)
                && (!requiresLayers(resourceKind) || layers > 0)
                && (!requiresMipLevels(resourceKind) || mipLevels > 0)
                && (!requiresSampleCount(resourceKind) || sampleCount > 0);
    }

    private static boolean requiresWidth(String resourceKind) {
        return resourceKind != null && !resourceKind.contains("sampler");
    }

    private static boolean requiresHeight(String resourceKind) {
        return resourceKind != null && (resourceKind.contains("2d") || resourceKind.contains("3d"));
    }

    private static boolean requiresDepth(String resourceKind) {
        return resourceKind != null && resourceKind.contains("3d");
    }

    private static boolean requiresLayers(String resourceKind) {
        return resourceKind != null && resourceKind.contains("layered");
    }

    private static boolean requiresMipLevels(String resourceKind) {
        return resourceKind != null && resourceKind.contains("mipmapped");
    }

    private static boolean requiresSampleCount(String resourceKind) {
        return resourceKind != null && resourceKind.contains("msaa");
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
