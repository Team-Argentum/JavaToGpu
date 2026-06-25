package net.sixik.ga_utils.javatogpu.runtime.opencl;

import dev.denismasterherobrine.packager.opencl.core.OpenClException;
import net.sixik.ga_utils.javatogpu.api.Float2;
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
import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import net.sixik.ga_utils.javatogpu.api.UInt;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;
import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLAttributes;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class OpenClGpuRuntimeBackendIntegrationTest {

    @Test
    void runsSimpleKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_entry",
                "inline://integration/kernel.cl",
                """
                        __kernel void gpu_entry(__global const float* input, float scale, __global float* output) {
                            int id = get_global_id(0);
                            output[id] = input[id] + scale;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        float[] input = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
        float[] output = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, 2.5f, output}));
        }

        assertArrayEquals(new float[]{3.5f, 4.5f, 5.5f, 6.5f}, output);
    }

    @Test
    void runsLongKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_long_entry",
                "inline://integration/long-kernel.cl",
                """
                        __kernel void gpu_long_entry(__global const long* input, long offset, __global long* output) {
                            int id = get_global_id(0);
                            output[id] = input[id] + offset;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "long[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("offset", "long", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "long[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        long[] input = new long[]{10L, 20L, 30L, 40L};
        long[] output = new long[]{0L, 0L, 0L, 0L};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, 5L, output}));
        }

        assertArrayEquals(new long[]{15L, 25L, 35L, 45L}, output);
    }

    @Test
    void runsDoubleKernelWhenDeviceSupportsFp64() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_double_entry",
                "inline://integration/double-kernel.cl",
                """
                        #pragma OPENCL EXTENSION cl_khr_fp64 : enable
                        __kernel void gpu_double_entry(__global const double* input, double scale, __global double* output) {
                            int id = get_global_id(0);
                            output[id] = input[id] * scale;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "double[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "double", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "double[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping fp64 integration smoke test");

        double[] input = new double[]{1.5d, 2.5d, 3.5d};
        double[] output = new double[]{0.0d, 0.0d, 0.0d};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, 2.0d, output}));
        }

        assertArrayEquals(new double[]{3.0d, 5.0d, 7.0d}, output);
    }

    @Test
    void runsBitwiseIntKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_bitwise_entry",
                "inline://integration/bitwise-kernel.cl",
                """
                        __kernel void gpu_bitwise_entry(__global const int* input, __global int* output) {
                            int id = get_global_id(0);
                            output[id] = ((~input[id]) << 1) ^ ((input[id] >> 1) | (input[id] & 7));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "int[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        int[] input = new int[]{1, 2, 7, 16};
        int[] output = new int[]{0, 0, 0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, output}));
        }

        assertArrayEquals(
                new int[]{
                        ((~1) << 1) ^ ((1 >> 1) | (1 & 7)),
                        ((~2) << 1) ^ ((2 >> 1) | (2 & 7)),
                        ((~7) << 1) ^ ((7 >> 1) | (7 & 7)),
                        ((~16) << 1) ^ ((16 >> 1) | (16 & 7))
                },
                output
        );
    }

    @Test
    void runsFloat2ParameterKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_vector_entry",
                "inline://integration/vector-kernel.cl",
                """
                        __kernel void gpu_vector_entry(float2 bias, __global float* output) {
                            int id = get_global_id(0);
                            output[id] = bias.x + bias.y + (float) id;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("bias", "Float2", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping vector parameter integration smoke test");

        float[] output = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new Float2(1.5f, 2.0f), output}));
        }

        assertArrayEquals(new float[]{3.5f, 4.5f, 5.5f, 6.5f}, output);
    }

    @Test
    void runsStructParameterKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_struct_entry",
                "inline://integration/struct-kernel.cl",
                """
                        typedef struct __attribute__((packed)) {
                            float x;
                            float y __attribute__((aligned(8)));
                            int count;
                        } Sample;

                        __kernel void gpu_struct_entry(Sample sample, __global float* output) {
                            int id = get_global_id(0);
                            output[id] = sample.x + sample.y + sample.count + (float) id;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("sample", "sample.Sample", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping struct parameter integration smoke test");

        float[] output = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new Sample(1.25f, 2.5f, 3), output}));
        }

        assertArrayEquals(new float[]{6.75f, 7.75f, 8.75f, 9.75f}, output);
    }

    @Test
    void runsStructArrayKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_struct_array_entry",
                "inline://integration/struct-array-kernel.cl",
                """
                        typedef struct{
                            float x;
                            float y;
                        } StructArraySample;

                        __kernel void gpu_struct_array_entry(__global StructArraySample* input, __global StructArraySample* output) {
                            int id = get_global_id(0);
                            output[id].x = input[id].x + 1.0f;
                            output[id].y = input[id].y + 2.0f;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "sample.StructArraySample[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "sample.StructArraySample[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping struct array integration smoke test");

        StructArraySample[] input = new StructArraySample[]{
                new StructArraySample(1.0f, 2.0f),
                new StructArraySample(3.0f, 4.0f)
        };
        StructArraySample[] output = new StructArraySample[]{new StructArraySample(), new StructArraySample()};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, output}));
        }

        assertArrayEquals(new float[]{2.0f, 4.0f}, new float[]{output[0].x, output[1].x});
        assertArrayEquals(new float[]{4.0f, 6.0f}, new float[]{output[0].y, output[1].y});
    }

    @Test
    void runsNestedAlignedStructArrayKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_nested_struct_array_entry",
                "inline://integration/nested-struct-array-kernel.cl",
                """
                        typedef struct{
                            float x;
                            float y;
                        } InnerPoint;

                        typedef struct __attribute__((aligned(16))) {
                            InnerPoint point;
                            float bias __attribute__((aligned(8)));
                            int count;
                        } ComplexStructArraySample;

                        __kernel void gpu_nested_struct_array_entry(__global ComplexStructArraySample* input, __global ComplexStructArraySample* output) {
                            int id = get_global_id(0);
                            output[id].point.x = input[id].point.x + 1.0f;
                            output[id].point.y = input[id].point.y + 2.0f;
                            output[id].bias = input[id].bias + 3.0f;
                            output[id].count = input[id].count + 4;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "sample.ComplexStructArraySample[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "sample.ComplexStructArraySample[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping nested aligned struct array integration smoke test");

        ComplexStructArraySample[] input = new ComplexStructArraySample[]{
                new ComplexStructArraySample(new InnerPoint(1.0f, 2.0f), 3.0f, 4),
                new ComplexStructArraySample(new InnerPoint(5.0f, 6.0f), 7.0f, 8)
        };
        ComplexStructArraySample[] output = new ComplexStructArraySample[]{
                new ComplexStructArraySample(new InnerPoint(), 0.0f, 0),
                new ComplexStructArraySample(new InnerPoint(), 0.0f, 0)
        };

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, output}));
        }

        assertArrayEquals(new float[]{2.0f, 6.0f}, new float[]{output[0].point.x, output[1].point.x});
        assertArrayEquals(new float[]{4.0f, 8.0f}, new float[]{output[0].point.y, output[1].point.y});
        assertArrayEquals(new float[]{6.0f, 10.0f}, new float[]{output[0].bias, output[1].bias});
        assertArrayEquals(new int[]{8, 12}, new int[]{output[0].count, output[1].count});
    }

    @Test
    void runsVectorArrayKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_vector_array_entry",
                "inline://integration/vector-array-kernel.cl",
                """
                        __kernel void gpu_vector_array_entry(__global float2* input, __global float2* output) {
                            int id = get_global_id(0);
                            output[id].x = input[id].x + 1.0f;
                            output[id].y = input[id].y + 2.0f;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "net.sixik.ga_utils.javatogpu.api.Float2[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "net.sixik.ga_utils.javatogpu.api.Float2[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping vector array integration smoke test");

        Float2[] input = new Float2[]{
                new Float2(1.0f, 2.0f),
                new Float2(3.0f, 4.0f)
        };
        Float2[] output = new Float2[]{new Float2(), new Float2()};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, output}));
        }

        assertArrayEquals(new float[]{2.0f, 4.0f}, new float[]{output[0].x, output[1].x});
        assertArrayEquals(new float[]{4.0f, 6.0f}, new float[]{output[0].y, output[1].y});
    }

    @Test
    void runsImageAndSamplerKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_entry",
                "inline://integration/image-kernel.cl",
                """
                        __kernel void gpu_image_entry(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            int4 pixel = read_imagei(inputImage, sampler, coords);
                            output[id] = pixel.x + pixel.y + pixel.z + pixel.w;
                            write_imagef(outputImage, coords, (float4)(1.0f, 0.5f, 0.25f, 1.0f));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping image/sampler integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgbaIntImage(
                     2,
                     1,
                     new int[]{
                             1, 2, 3, 4,
                             5, 6, 7, 8
                     }
             );
             Image2DWriteOnly outputImage = backend.createWriteOnlyRgbaFloatImage(2, 1);
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, sampler, output}));
            float[] written = backend.readRgbaFloatImage(outputImage);

            assertArrayEquals(new int[]{10, 26}, output);
            assertArrayEquals(new float[]{1.0f, 0.5f, 0.25f, 1.0f}, new float[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new float[]{1.0f, 0.5f, 0.25f, 1.0f}, new float[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void roundTripsRgba8ImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        byte[] source = new byte[]{
                0, 127, (byte) 255, 64,
                5, 10, 15, 20
        };

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgba8Image(2, 1, source)) {
            assertArrayEquals(source, backend.readRgba8Image(inputImage));
        }
    }

    @Test
    void roundTripsRFloatImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        float[] source = new float[]{1.25f, 2.5f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRFloatImage(2, 1, source)) {
            assertArrayEquals(source, backend.readRFloatImage(inputImage));
        }
    }

    @Test
    void roundTripsRgFloatImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        float[] source = new float[]{1.0f, 2.0f, 3.0f, 4.0f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgFloatImage(2, 1, source)) {
            assertArrayEquals(source, backend.readRgFloatImage(inputImage));
        }
    }

    @Test
    void roundTripsDepthImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        float[] source = new float[]{0.125f, 0.875f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyDepthImage(2, 1, source)) {
            assertArrayEquals(source, backend.readDepthImage(inputImage));
        }
    }

    @Test
    void roundTripsMipmappedRgbaFloatImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        float[] source = new float[]{
                1.0f, 0.0f, 0.0f, 1.0f,
                0.0f, 1.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 0.0f, 1.0f, 1.0f,
                0.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 0.0f, 1.0f,
                0.5f, 0.5f, 0.5f, 1.0f,
                0.25f, 0.25f, 0.25f, 1.0f
        };

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DMipmappedReadOnly inputImage = backend.createReadOnlyRgbaFloatImageMipmapped(4, 2, 2, source)) {
            assertArrayEquals(java.util.Arrays.copyOfRange(source, 0, 32), backend.readRgbaFloatImageMipmapped(inputImage, 0));
            assertArrayEquals(java.util.Arrays.copyOfRange(source, 32, 40), backend.readRgbaFloatImageMipmapped(inputImage, 1));
        }
    }

    @Test
    void roundTripsMipmappedRgbaUIntImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                13, 14, 15, 16,
                17, 18, 19, 20,
                21, 22, 23, 24,
                25, 26, 27, 28,
                29, 30, 31, 32,
                33, 34, 35, 36,
                37, 38, 39, 40
        };

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DMipmappedReadOnly inputImage = backend.createReadOnlyRgbaUIntImageMipmapped(4, 2, 2, source)) {
            assertArrayEquals(java.util.Arrays.copyOfRange(source, 0, 32), backend.readRgbaUIntImageMipmapped(inputImage, 0));
            assertArrayEquals(java.util.Arrays.copyOfRange(source, 32, 40), backend.readRgbaUIntImageMipmapped(inputImage, 1));
        }
    }

    @Test
    void roundTripsRIntImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{11, 22};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRIntImage(2, 1, source)) {
            assertArrayEquals(source, backend.readRIntImage(inputImage));
        }
    }

    @Test
    void roundTripsRgIntImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{11, 22, 33, 44};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgIntImage(2, 1, source)) {
            assertArrayEquals(source, backend.readRgIntImage(inputImage));
        }
    }

    @Test
    void roundTripsRUIntImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{101, 202};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRUIntImage(2, 1, source)) {
            assertArrayEquals(source, backend.readRUIntImage(inputImage));
        }
    }

    @Test
    void roundTripsRgUIntImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{101, 202, 303, 404};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgUIntImage(2, 1, source)) {
            assertArrayEquals(source, backend.readRgUIntImage(inputImage));
        }
    }

    @Test
    void runsUnsignedImageKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_uint_entry",
                "inline://integration/image-uint-kernel.cl",
                """
                        __kernel void gpu_image_uint_entry(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            uint4 pixel = read_imageui(inputImage, sampler, coords);
                            output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w);
                            write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping unsigned image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage(
                     2,
                     1,
                     new int[]{
                             1, 2, 3, 4,
                             5, 6, 7, 8
                     }
             );
             Image2DWriteOnly outputImage = backend.createWriteOnlyRgbaUIntImage(2, 1);
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, sampler, output}));
            int[] written = backend.readRgbaUIntImage(outputImage);

            assertArrayEquals(new int[]{10, 26}, output);
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void runsDepthImageKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_depth_entry",
                "inline://integration/image-depth-kernel.cl",
                """
                        __kernel void gpu_image_depth_entry(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global float* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            float4 pixel = read_imagef(inputImage, sampler, coords);
                            output[id] = pixel.x + (float) get_image_width(inputImage);
                            write_imagef(outputImage, coords, (float4)(pixel.x * 0.5f, 0.0f, 0.0f, 1.0f));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping depth image integration smoke test");

        float[] output = new float[]{0.0f, 0.0f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyDepthImage(2, 1, new float[]{0.25f, 0.75f});
             Image2DWriteOnly outputImage = backend.createWriteOnlyDepthImage(2, 1);
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, sampler, output}));
            float[] written = backend.readDepthImage(outputImage);

            assertArrayEquals(new float[]{2.25f, 2.75f}, output);
            assertArrayEquals(new float[]{0.125f, 0.375f}, written);
        }
    }

    @Test
    void roundTripsRgbaFloatImage3dOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        float[] source = new float[]{
                1.0f, 0.0f, 0.0f, 1.0f,
                0.0f, 1.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f
        };

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image3DReadOnly inputImage = backend.createReadOnlyRgbaFloatImage3D(2, 1, 2, source)) {
            assertArrayEquals(source, backend.readRgbaFloatImage3D(inputImage));
        }
    }

    @Test
    void roundTripsRgbaIntImage3dOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image3DReadOnly inputImage = backend.createReadOnlyRgbaIntImage3D(2, 1, 2, source)) {
            assertArrayEquals(source, backend.readRgbaIntImage3D(inputImage));
        }
    }

    @Test
    void roundTripsRgbaUIntImage3dOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image3DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage3D(2, 1, 2, source)) {
            assertArrayEquals(source, backend.readRgbaUIntImage3D(inputImage));
        }
    }

    @Test
    void roundTripsRgbaFloatImage1dOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        float[] source = new float[]{1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image1DReadOnly inputImage = backend.createReadOnlyRgbaFloatImage1D(2, source)) {
            assertArrayEquals(source, backend.readRgbaFloatImage1D(inputImage));
        }
    }

    @Test
    void roundTripsRgbaUIntImage1dOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{1, 2, 3, 4, 5, 6, 7, 8};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image1DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage1D(2, source)) {
            assertArrayEquals(source, backend.readRgbaUIntImage1D(inputImage));
        }
    }

    @Test
    void runsUnsignedImage1dKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image1d_uint_entry",
                "inline://integration/image1d-uint-kernel.cl",
                """
                        __kernel void gpu_image1d_uint_entry(read_only image1d_t inputImage, write_only image1d_t outputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            uint4 pixel = read_imageui(inputImage, sampler, id);
                            output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w + get_image_width(inputImage));
                            write_imageui(outputImage, id, (uint4)(9, 10, 11, 12));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image1DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image1DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping unsigned 1D image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image1DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage1D(2, new int[]{1, 2, 3, 4, 5, 6, 7, 8});
             Image1DWriteOnly outputImage = backend.createWriteOnlyRgbaUIntImage1D(2);
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, sampler, output}));
            int[] written = backend.readRgbaUIntImage1D(outputImage);

            assertArrayEquals(new int[]{12, 28}, output);
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void roundTripsRgbaIntImage1dOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{1, 2, 3, 4, 5, 6, 7, 8};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image1DReadOnly inputImage = backend.createReadOnlyRgbaIntImage1D(2, source)) {
            assertArrayEquals(source, backend.readRgbaIntImage1D(inputImage));
        }
    }

    @Test
    void roundTripsRgbaUIntImage1dArrayOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                13, 14, 15, 16
        };

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image1DArrayReadOnly inputImage = backend.createReadOnlyRgbaUIntImage1DArray(2, 2, source)) {
            assertArrayEquals(source, backend.readRgbaUIntImage1DArray(inputImage));
        }
    }

    @Test
    void runsUnsignedImage1dArrayKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image1d_array_uint_entry",
                "inline://integration/image1d-array-uint-kernel.cl",
                """
                        __kernel void gpu_image1d_array_uint_entry(read_only image1d_array_t inputImage, write_only image1d_array_t outputImage, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            uint4 pixel = read_imageui(inputImage, coords);
                            output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w + get_image_array_size(inputImage));
                            write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image1DArrayReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image1DArrayWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping unsigned 1D array image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image1DArrayReadOnly inputImage = backend.createReadOnlyRgbaUIntImage1DArray(2, 2, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
             Image1DArrayWriteOnly outputImage = backend.createWriteOnlyRgbaUIntImage1DArray(2, 2)) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, output}));
            int[] written = backend.readRgbaUIntImage1DArray(outputImage);

            assertArrayEquals(new int[]{12, 28}, output);
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void roundTripsRgbaFloatImage2dArrayOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        float[] source = new float[]{
                1.0f, 0.0f, 0.0f, 1.0f,
                0.0f, 1.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f
        };

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DArrayReadOnly inputImage = backend.createReadOnlyRgbaFloatImage2DArray(2, 1, 2, source)) {
            assertArrayEquals(source, backend.readRgbaFloatImage2DArray(inputImage));
        }
    }

    @Test
    void runsUnsignedImage2dArrayKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image2d_array_uint_entry",
                "inline://integration/image2d-array-uint-kernel.cl",
                """
                        __kernel void gpu_image2d_array_uint_entry(read_only image2d_array_t inputImage, write_only image2d_array_t outputImage, __global int* output) {
                            int id = get_global_id(0);
                            int4 coords = (int4)(id, 0, 0, 0);
                            uint4 pixel = read_imageui(inputImage, coords);
                            output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w + get_image_array_size(inputImage));
                            write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DArrayReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DArrayWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping unsigned 2D array image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DArrayReadOnly inputImage = backend.createReadOnlyRgbaUIntImage2DArray(2, 1, 2, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
             Image2DArrayWriteOnly outputImage = backend.createWriteOnlyRgbaUIntImage2DArray(2, 1, 2)) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, output}));
            int[] written = backend.readRgbaUIntImage2DArray(outputImage);

            assertArrayEquals(new int[]{12, 28}, output);
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void roundTripsRgbaIntImage1dBufferOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{1, 2, 3, 4, 5, 6, 7, 8};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image1DBufferReadOnly inputImage = backend.createReadOnlyRgbaIntImage1DBuffer(2, source)) {
            assertArrayEquals(source, backend.readRgbaIntImage1DBuffer(inputImage));
        }
    }

    @Test
    void runsIntImage1dBufferKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image1d_buffer_int_entry",
                "inline://integration/image1d-buffer-int-kernel.cl",
                """
                        __kernel void gpu_image1d_buffer_int_entry(read_only image1d_buffer_t inputImage, write_only image1d_buffer_t outputImage, __global int* output) {
                            int id = get_global_id(0);
                            int4 pixel = read_imagei(inputImage, id);
                            output[id] = pixel.x + get_image_width(inputImage);
                            write_imagei(outputImage, id, (int4)(9, 10, 11, 12));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image1DBufferReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image1DBufferWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping 1D buffer image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image1DBufferReadOnly inputImage = backend.createReadOnlyRgbaIntImage1DBuffer(2, new int[]{1, 2, 3, 4, 5, 6, 7, 8});
             Image1DBufferWriteOnly outputImage = backend.createWriteOnlyRgbaIntImage1DBuffer(2)) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, output}));
            int[] written = backend.readRgbaIntImage1DBuffer(outputImage);

            assertArrayEquals(new int[]{3, 7}, output);
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void runsUnsignedImage3dKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image3d_uint_entry",
                "inline://integration/image3d-uint-kernel.cl",
                """
                        __kernel void gpu_image3d_uint_entry(read_only image3d_t inputImage, write_only image3d_t outputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            int4 coords = (int4)(id, 0, 0, 0);
                            uint4 pixel = read_imageui(inputImage, sampler, coords);
                            output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w);
                            write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image3DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image3DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping unsigned 3D image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             net.sixik.ga_utils.javatogpu.api.Image3DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage3D(
                     2,
                     1,
                     2,
                     new int[]{
                             1, 2, 3, 4,
                             5, 6, 7, 8,
                             9, 10, 11, 12,
                             13, 14, 15, 16
                     }
             );
             net.sixik.ga_utils.javatogpu.api.Image3DWriteOnly outputImage = backend.createWriteOnlyRgbaUIntImage3D(2, 1, 2);
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, sampler, output}));
            int[] written = backend.readRgbaUIntImage3D(outputImage);

            assertArrayEquals(new int[]{10, 26}, output);
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void runsSamplerlessImageKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_nosampler_entry",
                "inline://integration/image-nosampler-kernel.cl",
                """
                        __kernel void gpu_image_nosampler_entry(read_only image2d_t inputImage, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            uint4 pixel = read_imageui(inputImage, coords);
                            output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w + get_image_width(inputImage));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping samplerless image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage(
                     2,
                     1,
                     new int[]{
                             1, 2, 3, 4,
                             5, 6, 7, 8
                     }
             )) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, output}));

            assertArrayEquals(new int[]{12, 28}, output);
        }
    }

    @Test
    void runsSamplerlessImage3dKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image3d_nosampler_entry",
                "inline://integration/image3d-nosampler-kernel.cl",
                """
                        __kernel void gpu_image3d_nosampler_entry(read_only image3d_t inputImage, __global float* output) {
                            int id = get_global_id(0);
                            int4 coords = (int4)(id, 0, 0, 0);
                            float4 pixel = read_imagef(inputImage, coords);
                            output[id] = pixel.x + pixel.y + pixel.z + pixel.w + get_image_depth(inputImage);
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image3DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping samplerless 3D image integration smoke test");

        float[] output = new float[]{0.0f, 0.0f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image3DReadOnly inputImage = backend.createReadOnlyRgbaFloatImage3D(
                     2,
                     1,
                     2,
                     new float[]{
                             1.0f, 0.0f, 0.0f, 1.0f,
                             0.0f, 1.0f, 0.0f, 1.0f,
                             0.0f, 0.0f, 1.0f, 1.0f,
                             1.0f, 1.0f, 1.0f, 1.0f
                     }
             )) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, output}));

            assertArrayEquals(new float[]{4.0f, 4.0f}, output);
        }
    }

    @Test
    void runsImageMetadataKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_meta_entry",
                "inline://integration/image-meta-kernel.cl",
                """
                        __kernel void gpu_image_meta_entry(read_only image2d_t inputImage, __global int* output) {
                            int id = get_global_id(0);
                            int channelOrder = get_image_channel_order(inputImage);
                            int channelType = get_image_channel_data_type(inputImage);
                            output[id] = ((channelOrder == %d) && (channelType == %d)) ? 1 : 0;
                        }""".formatted(org.lwjgl.opencl.CL10.CL_RGBA, org.lwjgl.opencl.CL10.CL_UNSIGNED_INT32),
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping image metadata integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage(
                     2,
                     1,
                     new int[]{
                             1, 2, 3, 4,
                             5, 6, 7, 8
                     }
             )) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, output}));

            assertArrayEquals(new int[]{1, 1}, output);
        }
    }

    @Test
    void runsImage3dMetadataKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image3d_meta_entry",
                "inline://integration/image3d-meta-kernel.cl",
                """
                        __kernel void gpu_image3d_meta_entry(read_only image3d_t inputImage, __global int* output) {
                            int id = get_global_id(0);
                            int channelOrder = get_image_channel_order(inputImage);
                            int channelType = get_image_channel_data_type(inputImage);
                            output[id] = ((channelOrder == %d) && (channelType == %d)) ? get_image_depth(inputImage) : 0;
                        }""".formatted(org.lwjgl.opencl.CL10.CL_RGBA, org.lwjgl.opencl.CL10.CL_FLOAT),
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image3DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping 3D image metadata integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image3DReadOnly inputImage = backend.createReadOnlyRgbaFloatImage3D(
                     2,
                     1,
                     2,
                     new float[]{
                             1.0f, 0.0f, 0.0f, 1.0f,
                             0.0f, 1.0f, 0.0f, 1.0f,
                             0.0f, 0.0f, 1.0f, 1.0f,
                             1.0f, 1.0f, 1.0f, 1.0f
                     }
             )) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, output}));

            assertArrayEquals(new int[]{2, 2}, output);
        }
    }

    @Test
    void runsExtendedImageMetadataKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_meta_extended_entry",
                "inline://integration/image-meta-extended-kernel.cl",
                """
                        __kernel void gpu_image_meta_extended_entry(read_only image2d_t inputImage, __global int* output) {
                            int id = get_global_id(0);
                            int mipLevels = get_image_num_mip_levels(inputImage);
                            int sampleCount = get_image_num_samples(inputImage);
                            output[id] = mipLevels + sampleCount + get_image_width(inputImage);
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping extended image metadata integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage(
                     2,
                     1,
                     new int[]{
                             1, 2, 3, 4,
                             5, 6, 7, 8
                     }
             )) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, output}));

            assertArrayEquals(new int[]{3, 3}, output);
        }
    }

    @Test
    void runsMipmappedImageMetadataKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_meta_mipmapped_entry",
                "inline://integration/image-meta-mipmapped-kernel.cl",
                """
                        __kernel void gpu_image_meta_mipmapped_entry(read_only image2d_t inputImage, __global int* output) {
                            int id = get_global_id(0);
                            int mipLevels = get_image_num_mip_levels(inputImage);
                            int sampleCount = get_image_num_samples(inputImage);
                            output[id] = mipLevels + sampleCount + get_image_width(inputImage) + get_image_height(inputImage);
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DMipmappedReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping mipmapped image metadata integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DMipmappedReadOnly inputImage = backend.createReadOnlyRgbaUIntImageMipmapped(
                     4,
                     2,
                     2,
                     new int[]{
                             1, 2, 3, 4,
                             5, 6, 7, 8,
                             9, 10, 11, 12,
                             13, 14, 15, 16,
                             17, 18, 19, 20,
                             21, 22, 23, 24,
                             25, 26, 27, 28,
                             29, 30, 31, 32,
                             33, 34, 35, 36,
                             37, 38, 39, 40
                     }
             )) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, output}));

            assertArrayEquals(new int[]{9, 9}, output);
        }
    }

    @Test
    void runsMipmappedFloatImageKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_mipmapped_float_entry",
                "inline://integration/image-mipmapped-float-kernel.cl",
                """
                        __kernel void gpu_image_mipmapped_float_entry(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            float4 pixel = read_imagef(inputImage, sampler, coords);
                            output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w);
                            write_imagef(outputImage, coords, (float4)(1.0f, 0.5f, 0.25f, 1.0f));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DMipmappedReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DMipmappedWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping mipmapped float image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DMipmappedReadOnly inputImage = backend.createReadOnlyRgbaFloatImageMipmapped(
                     2,
                     1,
                     1,
                     new float[]{
                             1.0f, 0.5f, 0.25f, 1.0f,
                             0.25f, 0.25f, 0.25f, 1.0f
                     }
             );
             Image2DMipmappedWriteOnly outputImage = backend.createWriteOnlyRgbaFloatImageMipmapped(2, 1, 1);
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, sampler, output}));
            float[] written = backend.readRgbaFloatImageMipmapped(outputImage, 0);

            assertArrayEquals(new int[]{2, 1}, output);
            assertArrayEquals(new float[]{1.0f, 0.5f, 0.25f, 1.0f}, new float[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new float[]{1.0f, 0.5f, 0.25f, 1.0f}, new float[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void runsMipmappedUIntImageKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_mipmapped_uint_entry",
                "inline://integration/image-mipmapped-uint-kernel.cl",
                """
                        __kernel void gpu_image_mipmapped_uint_entry(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            uint4 pixel = read_imageui(inputImage, sampler, coords);
                            output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w);
                            write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DMipmappedReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DMipmappedWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping mipmapped uint image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DMipmappedReadOnly inputImage = backend.createReadOnlyRgbaUIntImageMipmapped(
                     2,
                     1,
                     1,
                     new int[]{
                             1, 2, 3, 4,
                             5, 6, 7, 8
                     }
             );
             Image2DMipmappedWriteOnly outputImage = backend.createWriteOnlyRgbaUIntImageMipmapped(2, 1, 1);
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, sampler, output}));
            int[] written = backend.readRgbaUIntImageMipmapped(outputImage, 0);

            assertArrayEquals(new int[]{10, 26}, output);
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void runsUnsignedScalarAliasKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_uint_entry",
                "inline://integration/uint-kernel.cl",
                """
                        __kernel void gpu_uint_entry(uint bias, __global int* output) {
                            int id = get_global_id(0);
                            uint limited = clamp(max(bias, 4u), 4u, 32u);
                            uint result = min(limited, 17u);
                            output[id] = (int) result;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("bias", "UInt", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping unsigned scalar alias integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new UInt(41), output}));
            assertArrayEquals(new int[]{17, 17}, output);
        }
    }

    @GPUStruct
    @OpenCLAttributes({"packed"})
    static final class Sample {
        float x;
        @OpenCLAttributes({"aligned(8)"})
        float y;
        int count;

        Sample(float x, float y, int count) {
            this.x = x;
            this.y = y;
            this.count = count;
        }
    }

    @GPUStruct
    static final class StructArraySample {
        float x;
        float y;

        StructArraySample() {
        }

        StructArraySample(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    @GPUStruct
    static final class InnerPoint {
        float x;
        float y;

        InnerPoint() {
        }

        InnerPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    @GPUStruct
    @OpenCLAttributes({"aligned(16)"})
    static final class ComplexStructArraySample {
        InnerPoint point;
        @OpenCLAttributes({"aligned(8)"})
        float bias;
        int count;

        ComplexStructArraySample() {
        }

        ComplexStructArraySample(InnerPoint point, float bias, int count) {
            this.point = point;
            this.bias = bias;
            this.count = count;
        }
    }

    private static void assumeOpenClAvailable() {
        try (OpenClRuntimeSession ignored = OpenClRuntimeSession.createDefault()) {
            // Session creation is enough for this smoke test to know OpenCL is reachable.
        } catch (UnsatisfiedLinkError | IllegalStateException exception) {
            Assumptions.assumeTrue(false, "Skipping OpenCL integration smoke test: " + exception.getMessage());
        }
    }

    private static void assumeKernelCompiles(GpuKernelDescriptor descriptor, String messagePrefix) {
        try (OpenClRuntimeSession session = OpenClRuntimeSession.createDefault();
             OpenClCompiledKernel ignored = session.compileKernel(descriptor)) {
            // Compilation succeeded, so the runtime test can proceed.
        } catch (UnsatisfiedLinkError | IllegalStateException | OpenClException exception) {
            Assumptions.assumeTrue(false, messagePrefix + ": " + exception.getMessage());
        }
    }
}
