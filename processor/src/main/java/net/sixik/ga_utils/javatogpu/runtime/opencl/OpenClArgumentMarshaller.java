package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

public final class OpenClArgumentMarshaller {

    private OpenClArgumentMarshaller() {
    }

    public static OpenClKernelArguments marshall(GpuKernelDescriptor descriptor, Object[] arguments) {
        if (descriptor.parameterDescriptors().size() != arguments.length) {
            throw new IllegalArgumentException(
                    "Kernel argument count mismatch: expected " + descriptor.parameterDescriptors().size() + " but got " + arguments.length
            );
        }

        return new OpenClKernelArguments(
                java.util.stream.IntStream.range(0, arguments.length)
                        .mapToObj(index -> marshallArgument(descriptor.parameterDescriptors().get(index), arguments[index]))
                        .toList()
        );
    }

    private static OpenClKernelArgument marshallArgument(GpuKernelParameterDescriptor parameterDescriptor, Object argument) {
        if (argument == null) {
            throw new IllegalArgumentException("Unsupported OpenCL argument type: null for parameter " + parameterDescriptor.name());
        }

        try {
            GpuKernelParameterAccess access = parameterDescriptor.access();
            if (GpuTypeSupport.isSupportedVectorType(parameterDescriptor.javaType())) {
                return new OpenClScalarArgument(
                        OpenClArgumentKind.PACKED_VALUE,
                        access,
                        OpenClValuePacker.packVector(parameterDescriptor.javaType(), argument)
                );
            }
            if (OpenClValuePacker.isVectorArrayInstance(argument)) {
                return new OpenClArrayArgument(
                        OpenClArgumentKind.VECTOR_ARRAY,
                        access,
                        argument,
                        java.lang.reflect.Array.getLength(argument)
                );
            }
            if (OpenClValuePacker.isStructArrayInstance(argument)) {
                return new OpenClArrayArgument(
                        OpenClArgumentKind.STRUCT_ARRAY,
                        access,
                        argument,
                        java.lang.reflect.Array.getLength(argument)
                );
            }
            if (OpenClValuePacker.isStructInstance(argument)) {
                return new OpenClScalarArgument(
                        OpenClArgumentKind.PACKED_VALUE,
                        access,
                        OpenClValuePacker.packStruct(argument)
                );
            }
            if (argument instanceof byte[] values) {
                return new OpenClArrayArgument(
                        OpenClArgumentKind.BYTE_ARRAY,
                        access,
                        values,
                        values.length
                );
            }
            if (argument instanceof short[] values) {
                return new OpenClArrayArgument(
                        OpenClArgumentKind.SHORT_ARRAY,
                        access,
                        values,
                        values.length
                );
            }
            if (argument instanceof int[] values) {
                return new OpenClArrayArgument(
                        OpenClArgumentKind.INT_ARRAY,
                        access,
                        values,
                        values.length
                );
            }
            if (argument instanceof long[] values) {
                return new OpenClArrayArgument(
                        OpenClArgumentKind.LONG_ARRAY,
                        access,
                        values,
                        values.length
                );
            }
            if (argument instanceof float[] values) {
                return new OpenClArrayArgument(
                        OpenClArgumentKind.FLOAT_ARRAY,
                        access,
                        values,
                        values.length
                );
            }
            if (argument instanceof double[] values) {
                return new OpenClArrayArgument(
                        OpenClArgumentKind.DOUBLE_ARRAY,
                        access,
                        values,
                        values.length
                );
            }
            if (argument instanceof Byte value) {
                return new OpenClScalarArgument(OpenClArgumentKind.INT8, parameterDescriptor.access(), value);
            }
            if (argument instanceof Short value) {
                return new OpenClScalarArgument(OpenClArgumentKind.INT16, parameterDescriptor.access(), value);
            }
            if (argument instanceof Integer value) {
                return new OpenClScalarArgument(OpenClArgumentKind.INT32, parameterDescriptor.access(), value);
            }
            if (argument instanceof Long value) {
                return new OpenClScalarArgument(OpenClArgumentKind.INT64, parameterDescriptor.access(), value);
            }
            if (argument instanceof Float value) {
                return new OpenClScalarArgument(OpenClArgumentKind.FLOAT32, parameterDescriptor.access(), value);
            }
            if (argument instanceof Double value) {
                return new OpenClScalarArgument(OpenClArgumentKind.FLOAT64, parameterDescriptor.access(), value);
            }

            throw new IllegalArgumentException("Unsupported OpenCL argument type: " + argument.getClass().getName());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Failed to marshall parameter '" + parameterDescriptor.name() + "': "
                            + exception.getMessage()
                            + (OpenClAbiDebug.enabled()
                            ? "\n" + OpenClAbiDebug.describeParameterFailure(parameterDescriptor, argument)
                            : ""),
                    exception
            );
        }
    }
}
