package net.sixik.ga_utils.javatogpu.runtime.selection;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodDeviceConstraint;
import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Infers advisory backend-placement workload hints from method metadata that is already loaded.
 *
 * <p>The inference is intentionally conservative and read-only. It never compiles, probes, or executes a candidate
 * backend; it only classifies visible descriptor/IrGpu facts into optional scoring hints.</p>
 */
public final class GpuRuntimeWorkloadHintInference {

    private static final Set<String> PRIMITIVE_TYPES = Set.of(
            "byte",
            "short",
            "int",
            "long",
            "float",
            "double",
            "boolean",
            "char"
    );

    private GpuRuntimeWorkloadHintInference() {
    }

    public static GpuRuntimeInferredWorkloadHints infer(GpuKernelDescriptor descriptor) {
        return infer(descriptor, null);
    }

    public static GpuRuntimeInferredWorkloadHints infer(GpuKernelDescriptor descriptor, IrGpuArtifact artifact) {
        InferenceState state = new InferenceState();
        inspectDescriptor(descriptor, state);
        inspectArtifact(artifact, state);

        GpuRuntimeWorkloadHints.Builder hints = GpuRuntimeWorkloadHints.builder();
        for (GpuRuntimeCapability capability : state.requiredCapabilities) {
            hints.requireCapability(capability);
        }
        hints.memoryIntensity(memoryIntensity(state));
        hints.arithmeticIntensity(arithmeticIntensity(state));

        ArrayList<String> diagnostics = new ArrayList<>();
        if (descriptor != null || artifact != null) {
            diagnostics.add("inferred workload sources descriptor=" + (descriptor != null)
                    + " irGpu=" + (artifact != null));
        }
        if (state.parameterCount > 0) {
            diagnostics.add("inferred workload parameters count=" + state.parameterCount
                    + " arrays=" + state.arrayParameterCount
                    + " images=" + state.imageParameterCount
                    + " local=" + state.localParameterCount
                    + " structArrays=" + state.structArrayParameterCount);
        }
        if (!state.requiredCapabilities.isEmpty()) {
            diagnostics.add("inferred workload capabilities=" + capabilityKeys(state.requiredCapabilities));
        }
        if (state.mathFunctionCount > 0 || state.operatorCount > 0) {
            diagnostics.add("inferred workload math functions=" + state.mathFunctionCount
                    + " operators=" + state.operatorCount);
        }
        if (state.launchDimensions > 0) {
            diagnostics.add("inferred workload launch dimensions=" + state.launchDimensions);
        }

        GpuRuntimeWorkloadHints inferred = hints.build();
        if (inferred.empty() && diagnostics.isEmpty()) {
            return new GpuRuntimeInferredWorkloadHints(GpuRuntimeWorkloadHints.none(), List.of());
        }
        return new GpuRuntimeInferredWorkloadHints(inferred, diagnostics);
    }

    private static void inspectDescriptor(GpuKernelDescriptor descriptor, InferenceState state) {
        if (descriptor == null) {
            return;
        }
        if (descriptor.parameterDescriptors() != null) {
            for (GpuKernelParameterDescriptor parameter : descriptor.parameterDescriptors()) {
                if (parameter != null) {
                    inspectParameter(parameter.javaType(), parameter.access(), state);
                }
            }
        }
        inspectBody(descriptor.kernelSource(), state);
    }

    private static void inspectArtifact(IrGpuArtifact artifact, InferenceState state) {
        if (artifact == null) {
            return;
        }
        for (IrGpuEntryParameter parameter : artifact.entryParameters()) {
            inspectParameter(parameter.javaType(), accessFromIrParameter(parameter), state);
            inspectAddressSpace(parameter.addressSpace(), state);
        }
        if (!artifact.structMetadata().isEmpty()
                || (artifact.module() != null && artifact.module().structs().size() > 0)) {
            state.requiredCapabilities.add(GpuRuntimeCapability.STRUCT_ABI);
        }
        if (artifact.launchMetadata() != null) {
            state.launchDimensions = artifact.launchMetadata().requiredDimensions();
        }
        if (artifact.featureMetadata() != null) {
            for (String feature : artifact.featureMetadata().requiredFeatures()) {
                inspectFeature(feature, state);
            }
        }
        if (artifact.module() != null) {
            artifact.entryDeviceConstraint()
                    .map(IrGpuMethodDeviceConstraint::requiredFeatures)
                    .ifPresent(features -> features.forEach(feature -> inspectFeature(feature, state)));
            for (IrGpuMethodBody body : artifact.module().methodBodies()) {
                if (body != null) {
                    inspectBody(body.body(), state);
                }
            }
        }
    }

    private static void inspectParameter(
            String javaType,
            GpuKernelParameterAccess access,
            InferenceState state
    ) {
        String type = normalize(javaType);
        if (type.isBlank()) {
            return;
        }
        state.parameterCount++;
        GpuKernelParameterAccess normalizedAccess = access == null ? GpuKernelParameterAccess.VALUE : access;
        if (normalizedAccess == GpuKernelParameterAccess.LOCAL) {
            state.localParameterCount++;
            state.requiredCapabilities.add(GpuRuntimeCapability.LOCAL_MEMORY);
            state.requiredCapabilities.add(GpuRuntimeCapability.ADDRESS_SPACE_LOCAL);
        }

        boolean array = arrayType(type);
        boolean image = imageType(type);
        boolean sampler = samplerType(type);
        if (array) {
            state.arrayParameterCount++;
            if (!primitiveArrayType(type) && !image && !sampler && !vectorType(type)) {
                state.structArrayParameterCount++;
                state.requiredCapabilities.add(GpuRuntimeCapability.STRUCT_ABI);
            }
        }
        if (image) {
            state.imageParameterCount++;
            state.requiredCapabilities.add(GpuRuntimeCapability.IMAGES);
            state.requiredCapabilities.add(GpuRuntimeCapability.IMAGE_ABI);
            if (type.contains("image3d") && type.contains("write")) {
                state.requiredCapabilities.add(GpuRuntimeCapability.IMAGE_3D_WRITES);
            }
        }
        if (sampler) {
            state.samplerParameterCount++;
            state.requiredCapabilities.add(GpuRuntimeCapability.IMAGES);
            state.requiredCapabilities.add(GpuRuntimeCapability.IMAGE_ABI);
        }
        if (type.contains("double")) {
            state.requiredCapabilities.add(GpuRuntimeCapability.FP64);
        }
        if (vectorType(type)) {
            state.requiredCapabilities.add(GpuRuntimeCapability.VECTOR_TYPES);
        }
        if (normalizedAccess == GpuKernelParameterAccess.READ_WRITE) {
            state.readWriteParameterCount++;
        }
    }

    private static GpuKernelParameterAccess accessFromIrParameter(IrGpuEntryParameter parameter) {
        String addressSpace = normalize(parameter.addressSpace());
        if ("local".equals(addressSpace)) {
            return GpuKernelParameterAccess.LOCAL;
        }
        if ("global".equals(addressSpace) || "constant".equals(addressSpace)) {
            return parameter.constant() ? GpuKernelParameterAccess.READ_ONLY : GpuKernelParameterAccess.READ_WRITE;
        }
        return GpuKernelParameterAccess.VALUE;
    }

    private static void inspectAddressSpace(String addressSpace, InferenceState state) {
        String normalized = normalize(addressSpace);
        switch (normalized) {
            case "global" -> state.requiredCapabilities.add(GpuRuntimeCapability.ADDRESS_SPACE_GLOBAL);
            case "local" -> {
                state.requiredCapabilities.add(GpuRuntimeCapability.ADDRESS_SPACE_LOCAL);
                state.requiredCapabilities.add(GpuRuntimeCapability.LOCAL_MEMORY);
            }
            case "constant" -> state.requiredCapabilities.add(GpuRuntimeCapability.ADDRESS_SPACE_CONSTANT);
            default -> {
            }
        }
    }

    private static void inspectFeature(String feature, InferenceState state) {
        String normalized = normalize(feature).replace('_', '-');
        switch (normalized) {
            case "fp64", "double", "double-precision" -> state.requiredCapabilities.add(GpuRuntimeCapability.FP64);
            case "images", "image", "image-abi" -> {
                state.requiredCapabilities.add(GpuRuntimeCapability.IMAGES);
                state.requiredCapabilities.add(GpuRuntimeCapability.IMAGE_ABI);
            }
            case "image-3d-writes", "image3d-writes", "image3d-write" ->
                    state.requiredCapabilities.add(GpuRuntimeCapability.IMAGE_3D_WRITES);
            case "subgroups", "subgroup" -> state.requiredCapabilities.add(GpuRuntimeCapability.SUBGROUPS);
            case "atomics", "atomic", "int32-atomics", "global-int32-atomics" ->
                    state.requiredCapabilities.add(GpuRuntimeCapability.ATOMICS);
            case "local-memory", "local" -> state.requiredCapabilities.add(GpuRuntimeCapability.LOCAL_MEMORY);
            case "address-space-global", "global-address-space", "global-memory-address-space" ->
                    state.requiredCapabilities.add(GpuRuntimeCapability.ADDRESS_SPACE_GLOBAL);
            case "address-space-local", "local-address-space", "local-memory-address-space" -> {
                state.requiredCapabilities.add(GpuRuntimeCapability.ADDRESS_SPACE_LOCAL);
                state.requiredCapabilities.add(GpuRuntimeCapability.LOCAL_MEMORY);
            }
            case "address-space-constant", "constant-address-space" ->
                    state.requiredCapabilities.add(GpuRuntimeCapability.ADDRESS_SPACE_CONSTANT);
            case "struct-abi", "structs", "struct" -> state.requiredCapabilities.add(GpuRuntimeCapability.STRUCT_ABI);
            case "vector-types", "vectors", "vector" -> state.requiredCapabilities.add(GpuRuntimeCapability.VECTOR_TYPES);
            default -> GpuRuntimeCapability.fromKey(normalized).ifPresent(state.requiredCapabilities::add);
        }
    }

    private static void inspectBody(String body, InferenceState state) {
        if (body == null || body.isBlank()) {
            return;
        }
        String text = body.toLowerCase(Locale.ROOT);
        state.mathFunctionCount += countOccurrences(text, "mad(")
                + countOccurrences(text, "fma(")
                + countOccurrences(text, "sin(")
                + countOccurrences(text, "cos(")
                + countOccurrences(text, "tan(")
                + countOccurrences(text, "sqrt(")
                + countOccurrences(text, "rsqrt(")
                + countOccurrences(text, "pow(")
                + countOccurrences(text, "exp(")
                + countOccurrences(text, "log(")
                + countOccurrences(text, "native_");
        state.operatorCount += countArithmeticOperators(text);
        if (text.contains("double")) {
            state.requiredCapabilities.add(GpuRuntimeCapability.FP64);
        }
        if (text.contains("image") || text.contains("read_image") || text.contains("write_image")) {
            state.requiredCapabilities.add(GpuRuntimeCapability.IMAGES);
            state.requiredCapabilities.add(GpuRuntimeCapability.IMAGE_ABI);
        }
        if (text.contains("write_imagef") && text.contains("image3d")) {
            state.requiredCapabilities.add(GpuRuntimeCapability.IMAGE_3D_WRITES);
        }
        if (text.contains("atomic_")) {
            state.requiredCapabilities.add(GpuRuntimeCapability.ATOMICS);
        }
        if (text.contains("__global") || text.contains(" global ")) {
            state.requiredCapabilities.add(GpuRuntimeCapability.ADDRESS_SPACE_GLOBAL);
        }
        if (text.contains("__local") || text.contains(" local ")) {
            state.requiredCapabilities.add(GpuRuntimeCapability.ADDRESS_SPACE_LOCAL);
            state.requiredCapabilities.add(GpuRuntimeCapability.LOCAL_MEMORY);
        }
        if (text.contains("__constant") || text.contains(" constant ")) {
            state.requiredCapabilities.add(GpuRuntimeCapability.ADDRESS_SPACE_CONSTANT);
        }
    }

    private static GpuRuntimeWorkloadIntensity memoryIntensity(InferenceState state) {
        if (state.imageParameterCount > 0 || state.localParameterCount > 0 || state.arrayParameterCount >= 3) {
            return GpuRuntimeWorkloadIntensity.HIGH;
        }
        if (state.arrayParameterCount > 0 || state.structArrayParameterCount > 0 || state.readWriteParameterCount > 0) {
            return GpuRuntimeWorkloadIntensity.MEDIUM;
        }
        return GpuRuntimeWorkloadIntensity.UNKNOWN;
    }

    private static GpuRuntimeWorkloadIntensity arithmeticIntensity(InferenceState state) {
        int total = state.mathFunctionCount * 2 + state.operatorCount;
        if (state.mathFunctionCount >= 3 || total >= 18) {
            return GpuRuntimeWorkloadIntensity.HIGH;
        }
        if (state.mathFunctionCount > 0 || total >= 6) {
            return GpuRuntimeWorkloadIntensity.MEDIUM;
        }
        return GpuRuntimeWorkloadIntensity.UNKNOWN;
    }

    private static boolean arrayType(String type) {
        return type.endsWith("[]") || type.contains("[");
    }

    private static boolean primitiveArrayType(String type) {
        return arrayType(type) && PRIMITIVE_TYPES.contains(arrayElementType(type));
    }

    private static String arrayElementType(String type) {
        String value = type;
        while (value.endsWith("[]")) {
            value = value.substring(0, value.length() - 2);
        }
        int bracket = value.indexOf('[');
        if (bracket >= 0) {
            value = value.substring(0, bracket);
        }
        int dot = value.lastIndexOf('.');
        return dot >= 0 ? value.substring(dot + 1) : value;
    }

    private static boolean imageType(String type) {
        return type.contains("image1d") || type.contains("image2d") || type.contains("image3d");
    }

    private static boolean samplerType(String type) {
        return type.endsWith("sampler") || type.contains(".sampler");
    }

    private static boolean vectorType(String type) {
        return type.contains("vector")
                || type.matches(".*\\b(float|double|int|uint|short|char)(2|3|4|8|16)(\\[\\])?$");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while (index >= 0 && index < text.length()) {
            index = text.indexOf(needle, index);
            if (index >= 0) {
                count++;
                index += needle.length();
            }
        }
        return count;
    }

    private static int countArithmeticOperators(String text) {
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '+' || value == '*' || value == '/') {
                count++;
            } else if (value == '-' && (index + 1 >= text.length() || text.charAt(index + 1) != '>')) {
                count++;
            }
        }
        return count;
    }

    private static String capabilityKeys(Set<GpuRuntimeCapability> capabilities) {
        return capabilities.stream()
                .map(GpuRuntimeCapability::key)
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class InferenceState {
        private final EnumSet<GpuRuntimeCapability> requiredCapabilities = EnumSet.noneOf(GpuRuntimeCapability.class);
        private int parameterCount;
        private int arrayParameterCount;
        private int readWriteParameterCount;
        private int imageParameterCount;
        private int samplerParameterCount;
        private int localParameterCount;
        private int structArrayParameterCount;
        private int mathFunctionCount;
        private int operatorCount;
        private int launchDimensions = -1;
    }
}
