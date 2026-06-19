package net.sixik.ga_utils.javatogpu.runtime.opencl;

import dev.denismasterherobrine.packager.opencl.core.OpenClException;
import net.sixik.ga_utils.javatogpu.api.Float2;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUStruct;
import net.sixik.ga_utils.javatogpu.api.anotations.OpenCLAttributes;
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
