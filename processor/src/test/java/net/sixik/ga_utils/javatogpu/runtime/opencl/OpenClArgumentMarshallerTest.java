package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.Float2;
import net.sixik.ga_utils.javatogpu.api.Float3;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUStruct;
import net.sixik.ga_utils.javatogpu.api.anotations.OpenCLAttributes;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClArgumentMarshallerTest {

    @Test
    void marshalsFloatArrayAndPrimitiveScalarArguments() {
        float[] input = new float[]{1.0f, 2.0f, 3.0f};
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("count", "int", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE)
                )
        );
        Object[] arguments = new Object[]{input, 7, 2.5f};

        OpenClKernelArguments marshalled = OpenClArgumentMarshaller.marshall(descriptor, arguments);

        assertEquals(3, marshalled.values().size());

        OpenClArrayArgument arrayArgument = assertInstanceOf(OpenClArrayArgument.class, marshalled.values().get(0));
        assertEquals(OpenClArgumentKind.FLOAT_ARRAY, arrayArgument.kind());
        assertEquals(GpuKernelParameterAccess.READ_ONLY, arrayArgument.access());
        assertSame(input, arrayArgument.sourceArray());
        assertEquals(3, arrayArgument.length());

        OpenClScalarArgument intArgument = assertInstanceOf(OpenClScalarArgument.class, marshalled.values().get(1));
        assertEquals(OpenClArgumentKind.INT32, intArgument.kind());
        assertEquals(GpuKernelParameterAccess.VALUE, intArgument.access());
        assertEquals(7, intArgument.value());

        OpenClScalarArgument floatArgument = assertInstanceOf(OpenClScalarArgument.class, marshalled.values().get(2));
        assertEquals(OpenClArgumentKind.FLOAT32, floatArgument.kind());
        assertEquals(GpuKernelParameterAccess.VALUE, floatArgument.access());
        assertEquals(2.5f, floatArgument.value());
    }

    @Test
    void marshalsAdditionalPrimitiveArraysAndScalars() {
        byte[] bytes = new byte[]{1, 2};
        short[] shorts = new short[]{3, 4};
        int[] ints = new int[]{5, 6};
        long[] longs = new long[]{7L, 8L};
        double[] doubles = new double[]{1.5, 2.5};
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("bytes", "byte[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("shorts", "short[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("ints", "int[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("longs", "long[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("doubles", "double[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("byteValue", "byte", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("shortValue", "short", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("longValue", "long", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("doubleValue", "double", GpuKernelParameterAccess.VALUE)
                )
        );

        OpenClKernelArguments marshalled = OpenClArgumentMarshaller.marshall(
                descriptor,
                new Object[]{bytes, shorts, ints, longs, doubles, (byte) 9, (short) 10, 11L, 12.5d}
        );

        assertEquals(OpenClArgumentKind.BYTE_ARRAY, assertInstanceOf(OpenClArrayArgument.class, marshalled.values().get(0)).kind());
        assertEquals(OpenClArgumentKind.SHORT_ARRAY, assertInstanceOf(OpenClArrayArgument.class, marshalled.values().get(1)).kind());
        assertEquals(OpenClArgumentKind.INT_ARRAY, assertInstanceOf(OpenClArrayArgument.class, marshalled.values().get(2)).kind());
        assertEquals(OpenClArgumentKind.LONG_ARRAY, assertInstanceOf(OpenClArrayArgument.class, marshalled.values().get(3)).kind());
        assertEquals(OpenClArgumentKind.DOUBLE_ARRAY, assertInstanceOf(OpenClArrayArgument.class, marshalled.values().get(4)).kind());
        assertEquals(OpenClArgumentKind.INT8, assertInstanceOf(OpenClScalarArgument.class, marshalled.values().get(5)).kind());
        assertEquals(OpenClArgumentKind.INT16, assertInstanceOf(OpenClScalarArgument.class, marshalled.values().get(6)).kind());
        assertEquals(OpenClArgumentKind.INT64, assertInstanceOf(OpenClScalarArgument.class, marshalled.values().get(7)).kind());
        assertEquals(OpenClArgumentKind.FLOAT64, assertInstanceOf(OpenClScalarArgument.class, marshalled.values().get(8)).kind());
    }

    @Test
    void marshalsLocalArrayArguments() {
        float[] scratch = new float[16];
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("scratch", "float[]", GpuKernelParameterAccess.LOCAL)
                )
        );

        OpenClKernelArguments marshalled = OpenClArgumentMarshaller.marshall(descriptor, new Object[]{scratch});

        OpenClArrayArgument arrayArgument = assertInstanceOf(OpenClArrayArgument.class, marshalled.values().get(0));
        assertEquals(OpenClArgumentKind.FLOAT_ARRAY, arrayArgument.kind());
        assertEquals(GpuKernelParameterAccess.LOCAL, arrayArgument.access());
        assertSame(scratch, arrayArgument.sourceArray());
        assertEquals(16, arrayArgument.length());
    }

    @Test
    void marshalsVectorKernelParametersAsPackedValues() {
        Float2 bias = new Float2(1.0f, 2.0f);
        Float3 normal = new Float3(3.0f, 4.0f, 5.0f);
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("bias", "Float2", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("normal", "Float3", GpuKernelParameterAccess.VALUE)
                )
        );

        OpenClKernelArguments marshalled = OpenClArgumentMarshaller.marshall(descriptor, new Object[]{bias, normal});

        OpenClScalarArgument biasArgument = assertInstanceOf(OpenClScalarArgument.class, marshalled.values().get(0));
        assertEquals(OpenClArgumentKind.PACKED_VALUE, biasArgument.kind());
        ByteBuffer biasBytes = ((ByteBuffer) biasArgument.value()).duplicate().order(ByteOrder.nativeOrder());
        assertEquals(8, biasBytes.remaining());
        assertEquals(1.0f, biasBytes.getFloat(0));
        assertEquals(2.0f, biasBytes.getFloat(4));

        OpenClScalarArgument normalArgument = assertInstanceOf(OpenClScalarArgument.class, marshalled.values().get(1));
        assertEquals(OpenClArgumentKind.PACKED_VALUE, normalArgument.kind());
        ByteBuffer normalBytes = ((ByteBuffer) normalArgument.value()).duplicate().order(ByteOrder.nativeOrder());
        assertEquals(16, normalBytes.remaining());
        assertEquals(3.0f, normalBytes.getFloat(0));
        assertEquals(4.0f, normalBytes.getFloat(4));
        assertEquals(5.0f, normalBytes.getFloat(8));
        assertEquals(0.0f, normalBytes.getFloat(12));
    }

    @Test
    void marshalsStructKernelParametersWithNestedLayoutAndAttributes() {
        PackedSample sample = new PackedSample(new InnerSample(3, 4.5f), 1.25f, 7);
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("sample", "sample.PackedSample", GpuKernelParameterAccess.VALUE)
                )
        );

        OpenClKernelArguments marshalled = OpenClArgumentMarshaller.marshall(descriptor, new Object[]{sample});

        OpenClScalarArgument argument = assertInstanceOf(OpenClScalarArgument.class, marshalled.values().get(0));
        assertEquals(OpenClArgumentKind.PACKED_VALUE, argument.kind());
        ByteBuffer bytes = ((ByteBuffer) argument.value()).duplicate().order(ByteOrder.nativeOrder());
        assertEquals(16, bytes.remaining());
        assertEquals(3, bytes.getInt(0));
        assertEquals(4.5f, bytes.getFloat(4));
        assertEquals(1.25f, bytes.getFloat(8));
        assertEquals(7, bytes.getInt(12));
    }

    @Test
    void marshalsAndUnmarshalsStructArrayKernelParameters() {
        Sample[] samples = new Sample[]{
                new Sample(1.0f, 2.0f),
                null
        };
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("samples", "sample.Sample[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        OpenClKernelArguments marshalled = OpenClArgumentMarshaller.marshall(descriptor, new Object[]{samples});

        OpenClArrayArgument argument = assertInstanceOf(OpenClArrayArgument.class, marshalled.values().get(0));
        assertEquals(OpenClArgumentKind.STRUCT_ARRAY, argument.kind());
        assertEquals(2, argument.length());

        ByteBuffer bytes = OpenClValuePacker.packStructArray(samples).duplicate().order(ByteOrder.nativeOrder());
        assertEquals(16, bytes.remaining());
        assertEquals(1.0f, bytes.getFloat(0));
        assertEquals(2.0f, bytes.getFloat(4));
        assertEquals(0.0f, bytes.getFloat(8));
        assertEquals(0.0f, bytes.getFloat(12));

        bytes.putFloat(0, 3.0f);
        bytes.putFloat(4, 4.0f);
        bytes.putFloat(8, 5.0f);
        bytes.putFloat(12, 6.0f);
        OpenClValuePacker.unpackStructArray(bytes, samples);

        assertEquals(3.0f, samples[0].x);
        assertEquals(4.0f, samples[0].y);
        assertEquals(5.0f, samples[1].x);
        assertEquals(6.0f, samples[1].y);
    }

    @Test
    void marshalsAndUnmarshalsVectorArrayKernelParameters() {
        Float2[] vectors = new Float2[]{
                new Float2(1.0f, 2.0f),
                new Float2(3.0f, 4.0f)
        };
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("vectors", "net.sixik.ga_utils.javatogpu.api.Float2[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        OpenClKernelArguments marshalled = OpenClArgumentMarshaller.marshall(descriptor, new Object[]{vectors});

        OpenClArrayArgument argument = assertInstanceOf(OpenClArrayArgument.class, marshalled.values().get(0));
        assertEquals(OpenClArgumentKind.VECTOR_ARRAY, argument.kind());
        assertEquals(2, argument.length());

        ByteBuffer bytes = OpenClValuePacker.packVectorArray(vectors).duplicate().order(ByteOrder.nativeOrder());
        assertEquals(16, bytes.remaining());
        assertEquals(1.0f, bytes.getFloat(0));
        assertEquals(2.0f, bytes.getFloat(4));
        assertEquals(3.0f, bytes.getFloat(8));
        assertEquals(4.0f, bytes.getFloat(12));

        bytes.putFloat(0, 5.0f);
        bytes.putFloat(4, 6.0f);
        bytes.putFloat(8, 7.0f);
        bytes.putFloat(12, 8.0f);
        OpenClValuePacker.unpackVectorArray(bytes, vectors);

        assertEquals(5.0f, vectors[0].x);
        assertEquals(6.0f, vectors[0].y);
        assertEquals(7.0f, vectors[1].x);
        assertEquals(8.0f, vectors[1].y);
    }

    @Test
    void rejectsKernelArgumentCountMismatch() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY)
                )
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OpenClArgumentMarshaller.marshall(descriptor, new Object[0])
        );

        assertEquals("Kernel argument count mismatch: expected 1 but got 0", exception.getMessage());
    }

    @Test
    void rejectsNullArgumentsWithParameterContext() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY)
                )
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OpenClArgumentMarshaller.marshall(descriptor, new Object[]{null})
        );

        assertEquals(
                "Unsupported OpenCL argument type: null for parameter input",
                exception.getMessage()
        );
    }

    @Test
    void rejectsStructArgumentsWithNullNestedFields() {
        PackedSample sample = new PackedSample(null, 1.25f, 7);
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("sample", PackedSample.class.getName(), GpuKernelParameterAccess.VALUE)
                )
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OpenClArgumentMarshaller.marshall(descriptor, new Object[]{sample})
        );

        assertTrue(exception.getMessage().contains("Failed to marshall parameter 'sample':"));
        assertTrue(exception.getMessage().contains("Null @GPUStruct field is not supported for OpenCL marshalling: inner"));
    }

    @Test
    void appendsAbiDebugDetailsToMarshallingFailures() {
        String previous = System.getProperty(OpenClAbiDebug.PROPERTY);
        System.setProperty(OpenClAbiDebug.PROPERTY, "true");
        try {
            PackedSample sample = new PackedSample(null, 1.25f, 7);
            GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                    "kernel",
                    "javatogpu/sample/Demo/kernel.cl",
                    "__kernel void kernel() {}",
                    java.util.List.of(
                            new GpuKernelParameterDescriptor("sample", PackedSample.class.getName(), GpuKernelParameterAccess.VALUE)
                    )
            );

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> OpenClArgumentMarshaller.marshall(descriptor, new Object[]{sample})
            );

            assertTrue(exception.getMessage().contains("Parameter sample : " + PackedSample.class.getName() + " [VALUE]"));
            assertTrue(exception.getMessage().contains("PackedSample [STRUCT] size=16, align=8"));
            assertTrue(exception.getMessage().contains("inner @0 size=8"));
            assertTrue(exception.getMessage().contains(InnerSample.class.getName() + " [STRUCT] size=8, align=4"));
        } finally {
            if (previous == null) {
                System.clearProperty(OpenClAbiDebug.PROPERTY);
            } else {
                System.setProperty(OpenClAbiDebug.PROPERTY, previous);
            }
        }
    }

    @GPUStruct
    static final class InnerSample {
        int x;
        float y;

        InnerSample(int x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    @GPUStruct
    @OpenCLAttributes({"packed"})
    static final class PackedSample {
        InnerSample inner;
        @OpenCLAttributes({"aligned(8)"})
        float bias;
        int count;

        PackedSample(InnerSample inner, float bias, int count) {
            this.inner = inner;
            this.bias = bias;
            this.count = count;
        }
    }

    @GPUStruct
    static final class Sample {
        float x;
        float y;

        Sample() {
        }

        Sample(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
