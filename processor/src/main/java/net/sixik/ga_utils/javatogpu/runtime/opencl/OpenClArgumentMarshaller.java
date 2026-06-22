package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.Image1DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DBufferReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DBufferWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import net.sixik.ga_utils.javatogpu.api.UByte;
import net.sixik.ga_utils.javatogpu.api.UInt;
import net.sixik.ga_utils.javatogpu.api.ULong;
import net.sixik.ga_utils.javatogpu.api.UShort;
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
            if (GpuTypeSupport.isSupportedImageOrSamplerType(parameterDescriptor.javaType())) {
                return marshallImageOrSamplerArgument(parameterDescriptor, access, argument);
            }
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
            if (argument instanceof UByte value) {
                return new OpenClScalarArgument(OpenClArgumentKind.INT8, parameterDescriptor.access(), value.value);
            }
            if (argument instanceof UShort value) {
                return new OpenClScalarArgument(OpenClArgumentKind.INT16, parameterDescriptor.access(), value.value);
            }
            if (argument instanceof UInt value) {
                return new OpenClScalarArgument(OpenClArgumentKind.INT32, parameterDescriptor.access(), value.value);
            }
            if (argument instanceof ULong value) {
                return new OpenClScalarArgument(OpenClArgumentKind.INT64, parameterDescriptor.access(), value.value);
            }
            if (argument instanceof Byte value) {
                return new OpenClScalarArgument(OpenClArgumentKind.INT8, parameterDescriptor.access(), value);
            }
            if (argument instanceof Short value) {
                return new OpenClScalarArgument(OpenClArgumentKind.INT16, parameterDescriptor.access(), value);
            }
            if (argument instanceof Character value) {
                return new OpenClScalarArgument(OpenClArgumentKind.INT16, parameterDescriptor.access(), (short) value.charValue());
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

    private static OpenClKernelArgument marshallImageOrSamplerArgument(
            GpuKernelParameterDescriptor parameterDescriptor,
            GpuKernelParameterAccess access,
            Object argument
    ) {
        if (argument instanceof Image1DReadOnly image) {
            requireValidHandle(parameterDescriptor, "Image1DReadOnly", image.isValid());
            return new OpenClScalarArgument(OpenClArgumentKind.IMAGE1D, access, image.handle());
        }
        if (argument instanceof Image1DWriteOnly image) {
            requireValidHandle(parameterDescriptor, "Image1DWriteOnly", image.isValid());
            return new OpenClScalarArgument(OpenClArgumentKind.IMAGE1D, access, image.handle());
        }
        if (argument instanceof Image1DArrayReadOnly image) {
            requireValidHandle(parameterDescriptor, "Image1DArrayReadOnly", image.isValid());
            return new OpenClScalarArgument(OpenClArgumentKind.IMAGE1D_ARRAY, access, image.handle());
        }
        if (argument instanceof Image1DArrayWriteOnly image) {
            requireValidHandle(parameterDescriptor, "Image1DArrayWriteOnly", image.isValid());
            return new OpenClScalarArgument(OpenClArgumentKind.IMAGE1D_ARRAY, access, image.handle());
        }
        if (argument instanceof Image1DBufferReadOnly image) {
            requireValidHandle(parameterDescriptor, "Image1DBufferReadOnly", image.isValid());
            return new OpenClScalarArgument(OpenClArgumentKind.IMAGE1D_BUFFER, access, image.handle());
        }
        if (argument instanceof Image1DBufferWriteOnly image) {
            requireValidHandle(parameterDescriptor, "Image1DBufferWriteOnly", image.isValid());
            return new OpenClScalarArgument(OpenClArgumentKind.IMAGE1D_BUFFER, access, image.handle());
        }
        if (argument instanceof Image2DReadOnly image) {
            requireValidHandle(parameterDescriptor, "Image2DReadOnly", image.isValid());
            return new OpenClScalarArgument(OpenClArgumentKind.IMAGE2D, access, image.handle());
        }
        if (argument instanceof Image2DWriteOnly image) {
            requireValidHandle(parameterDescriptor, "Image2DWriteOnly", image.isValid());
            return new OpenClScalarArgument(OpenClArgumentKind.IMAGE2D, access, image.handle());
        }
        if (argument instanceof Image2DArrayReadOnly image) {
            requireValidHandle(parameterDescriptor, "Image2DArrayReadOnly", image.isValid());
            return new OpenClScalarArgument(OpenClArgumentKind.IMAGE2D_ARRAY, access, image.handle());
        }
        if (argument instanceof Image2DArrayWriteOnly image) {
            requireValidHandle(parameterDescriptor, "Image2DArrayWriteOnly", image.isValid());
            return new OpenClScalarArgument(OpenClArgumentKind.IMAGE2D_ARRAY, access, image.handle());
        }
        if (argument instanceof Image3DReadOnly image) {
            requireValidHandle(parameterDescriptor, "Image3DReadOnly", image.isValid());
            return new OpenClScalarArgument(OpenClArgumentKind.IMAGE3D, access, image.handle());
        }
        if (argument instanceof Image3DWriteOnly image) {
            requireValidHandle(parameterDescriptor, "Image3DWriteOnly", image.isValid());
            return new OpenClScalarArgument(OpenClArgumentKind.IMAGE3D, access, image.handle());
        }
        if (argument instanceof Sampler sampler) {
            requireValidHandle(parameterDescriptor, "Sampler", sampler.isValid());
            return new OpenClScalarArgument(OpenClArgumentKind.SAMPLER, access, sampler.handle());
        }
        throw new IllegalArgumentException(
                "Unsupported OpenCL image/sampler runtime argument type for "
                        + parameterDescriptor.javaType()
                        + ": "
                        + argument.getClass().getName()
        );
    }

    private static void requireValidHandle(GpuKernelParameterDescriptor parameterDescriptor, String typeName, boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException(
                    typeName + " runtime argument for parameter '" + parameterDescriptor.name() + "' does not carry a valid native handle"
            );
        }
    }
}
