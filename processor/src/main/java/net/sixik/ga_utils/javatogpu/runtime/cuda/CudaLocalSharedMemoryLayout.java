package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuMemorySlice;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Host-side layout for CUDA dynamic shared memory slices derived from LOCAL parameters.
 */
final class CudaLocalSharedMemoryLayout {

    private static final CudaLocalSharedMemoryLayout EMPTY = new CudaLocalSharedMemoryLayout(List.of(), 0L);

    private final List<Slice> slices;
    private final long totalByteSize;

    private CudaLocalSharedMemoryLayout(List<Slice> slices, long totalByteSize) {
        this.slices = slices == null ? List.of() : List.copyOf(slices);
        this.totalByteSize = Math.max(0L, totalByteSize);
    }

    static CudaLocalSharedMemoryLayout empty() {
        return EMPTY;
    }

    static CudaLocalSharedMemoryLayout from(
            List<GpuKernelParameterDescriptor> parameters,
            Object[] arguments
    ) {
        if (parameters == null || parameters.isEmpty()) {
            return empty();
        }
        ArrayList<LocalParameter> locals = new ArrayList<>();
        for (int index = 0; index < parameters.size(); index++) {
            GpuKernelParameterDescriptor parameter = parameters.get(index);
            if (parameter == null || parameter.access() != GpuKernelParameterAccess.LOCAL) {
                continue;
            }
            Object argument = arguments[index];
            String declaredType = GpuTypeSupport.declaredType(parameter.javaType());
            String componentType = GpuTypeSupport.componentType(declaredType);
            Object hostArray = hostArray(argument);
            int elementCount = localArrayLength(argument);
            long componentByteSize = localComponentByteSize(declaredType, hostArray, componentType);
            long componentAlignment = localComponentAlignment(declaredType, hostArray, componentType);
            locals.add(new LocalParameter(
                    index,
                    parameter.name(),
                    parameter.javaType(),
                    componentType,
                    elementCount,
                    componentByteSize,
                    componentAlignment
            ));
        }
        if (locals.isEmpty()) {
            return empty();
        }
        boolean hiddenOffsetsRequired = locals.size() > 1;
        ArrayList<Slice> slices = new ArrayList<>();
        long offset = 0L;
        for (LocalParameter local : locals) {
            long alignment = Math.max(1L, local.componentAlignment());
            long alignedOffset = alignTo(offset, alignment);
            long byteSize = local.componentByteSize() * local.elementCount();
            slices.add(new Slice(
                    local.parameterIndex(),
                    local.parameterName(),
                    local.javaType(),
                    local.componentType(),
                    local.elementCount(),
                    byteSize,
                    alignment,
                    alignedOffset,
                    hiddenOffsetsRequired ? hiddenOffsetParameterName(local.parameterName()) : ""
            ));
            offset = alignedOffset + byteSize;
        }
        return new CudaLocalSharedMemoryLayout(slices, offset);
    }

    static String hiddenOffsetParameterName(String parameterName) {
        return "__jtg_local_" + cIdentifier(parameterName) + "_byte_offset";
    }

    static int localParameterCount(List<GpuKernelParameterDescriptor> parameters) {
        int count = 0;
        for (GpuKernelParameterDescriptor parameter : parameters == null ? List.<GpuKernelParameterDescriptor>of() : parameters) {
            if (parameter != null && parameter.access() == GpuKernelParameterAccess.LOCAL) {
                count++;
            }
        }
        return count;
    }

    static boolean hiddenOffsetsRequired(int localParameterCount) {
        return localParameterCount > 1;
    }

    List<Slice> slices() {
        return slices;
    }

    int sliceCount() {
        return slices.size();
    }

    int hiddenOffsetParameterCount() {
        return slices.size() > 1 ? slices.size() : 0;
    }

    boolean present() {
        return totalByteSize > 0L;
    }

    long totalByteSize() {
        return totalByteSize;
    }

    Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.localSharedMemoryLayout"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", Boolean.toString(present()));
        fields.put(normalizedPrefix + ".byteSize", Long.toString(totalByteSize));
        fields.put(normalizedPrefix + ".slice.count", Integer.toString(sliceCount()));
        fields.put(normalizedPrefix + ".hiddenOffsetParameter.count", Integer.toString(hiddenOffsetParameterCount()));
        for (int index = 0; index < slices.size(); index++) {
            fields.putAll(slices.get(index).artifactFields(normalizedPrefix + ".slice." + index));
        }
        return Collections.unmodifiableMap(fields);
    }

    private static long alignTo(long value, long alignment) {
        if (alignment <= 1L) {
            return value;
        }
        long remainder = value % alignment;
        return remainder == 0L ? value : value + alignment - remainder;
    }

    private static int localArrayLength(Object argument) {
        if (argument instanceof GpuMemorySlice<?> slice) {
            return slice.length();
        }
        if (CudaValuePacker.isStructArrayInstance(argument)) {
            return CudaValuePacker.structArrayLength(argument);
        }
        if (argument instanceof byte[] values) {
            return values.length;
        }
        if (argument instanceof short[] values) {
            return values.length;
        }
        if (argument instanceof char[] values) {
            return values.length;
        }
        if (argument instanceof int[] values) {
            return values.length;
        }
        if (argument instanceof long[] values) {
            return values.length;
        }
        if (argument instanceof float[] values) {
            return values.length;
        }
        if (argument instanceof double[] values) {
            return values.length;
        }
        return 0;
    }

    private static Object hostArray(Object argument) {
        return argument instanceof GpuMemorySlice<?> slice ? slice.array() : argument;
    }

    private static long localComponentByteSize(String declaredType, Object hostArray, String componentType) {
        if (CudaValuePacker.structArrayCompatible(declaredType, hostArray)) {
            return CudaValuePacker.structArrayElementByteSize(hostArray);
        }
        return GpuTypeSupport.scalarByteSize(componentType);
    }

    private static long localComponentAlignment(String declaredType, Object hostArray, String componentType) {
        if (CudaValuePacker.structArrayCompatible(declaredType, hostArray)) {
            return CudaValuePacker.structArrayElementAlignment(hostArray);
        }
        return GpuTypeSupport.scalarByteSize(componentType);
    }

    private static String cIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "arg";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            builder.append(Character.isLetterOrDigit(ch) || ch == '_' ? ch : '_');
        }
        if (builder.length() == 0 || Character.isDigit(builder.charAt(0))) {
            builder.insert(0, '_');
        }
        return builder.toString();
    }

    private record LocalParameter(
            int parameterIndex,
            String parameterName,
            String javaType,
            String componentType,
            int elementCount,
            long componentByteSize,
            long componentAlignment
    ) {
    }

    record Slice(
            int parameterIndex,
            String parameterName,
            String javaType,
            String componentType,
            int elementCount,
            long byteSize,
            long alignment,
            long byteOffset,
            String hiddenOffsetParameterName
    ) {

        private Map<String, String> artifactFields(String prefix) {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(prefix + ".parameter.index", Integer.toString(parameterIndex));
            fields.put(prefix + ".parameter.name", parameterName);
            fields.put(prefix + ".parameter.javaType", javaType);
            fields.put(prefix + ".componentType", componentType);
            fields.put(prefix + ".element.count", Integer.toString(elementCount));
            fields.put(prefix + ".byteSize", Long.toString(byteSize));
            fields.put(prefix + ".alignment", Long.toString(alignment));
            fields.put(prefix + ".byteOffset", Long.toString(byteOffset));
            fields.put(prefix + ".hiddenOffsetParameter.name", hiddenOffsetParameterName);
            return fields;
        }
    }
}
