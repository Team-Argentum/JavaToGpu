package net.sixik.ga_utils;

import net.sixik.ga_utils.javatogpu.api.Float2;
import net.sixik.ga_utils.javatogpu.api.FloatPtr;
import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        float[] floatInput = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
        float[] basicOutput = new float[floatInput.length];
        float[] vectorOutput = new float[floatInput.length];
        SamplePoint[] structBufferInput = new SamplePoint[]{
                new SamplePoint(1.0, 2.0),
                new SamplePoint(3.0, 4.0),
                new SamplePoint(5.0, 6.0),
                new SamplePoint(7.0, 8.0)
        };
        SamplePoint[] structBufferOutput = new SamplePoint[structBufferInput.length];

        double[] doubleInput = new double[]{1.0, 2.0, 3.0, 4.0};
        double[] structOutput = new double[doubleInput.length];

        try (GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {

            System.out.println("Running basic @GPU example...");
            Examples.basicMath(floatInput, basicOutput);
            System.out.println("basicOutput[0] = " + basicOutput[0]);

            System.out.println("Running @GPUStruct example...");
            Examples.structExample(
                    new SampleData(new SamplePoint(1.5, 2.5), 0.75, 3),
                    doubleInput,
                    structOutput
            );
            System.out.println("structOutput[0] = " + structOutput[0]);

            System.out.println("Running vector example...");
            Examples.vectorExample(new Float2(1.0f, 0.5f), floatInput, vectorOutput);
            System.out.println("vectorOutput[0] = " + vectorOutput[0]);

            System.out.println("Running struct buffer example...");
            Examples.structBufferExample(structBufferInput, structBufferOutput);
            System.out.println("structBufferOutput[0] = (" + structBufferOutput[0].x + ", " + structBufferOutput[0].y + ")");
        } catch (RuntimeException exception) {
            System.out.println("GPU execution failed: " + exception.getMessage());
        }
    }

    public static final class Examples {

        private Examples() {
        }

        @net.sixik.ga_utils.javatogpu.api.annotations.GPU
        public static void basicMath(
                @GPUGlobal float[] input,
                @GPUGlobal float[] output
        ) {
            int id = GPU.get_global_id(0);
            FloatPtr ptr = new FloatPtr(input[id]);

            KernelMath.clamp(ptr);
            output[id] = KernelMath.lerp(ptr.value, GPU.sin(input[id]), 0.25f);
        }

        @net.sixik.ga_utils.javatogpu.api.annotations.GPU
        public static void structExample(
                SampleData sample,
                @GPUGlobal double[] input,
                @GPUGlobal double[] output
        ) {
            int id = GPU.get_global_id(0);

            SamplePoint point = new SamplePoint(input[id], input[id] * 2.0);
            SampleData localSample = new SampleData(point, 0.5, id);

            output[id] = sample.point.x + sample.point.y + sample.bias + sample.index
                    + localSample.point.x + localSample.point.y + localSample.bias + localSample.index;
        }

        @net.sixik.ga_utils.javatogpu.api.annotations.GPU
        public static void vectorExample(
                Float2 bias,
                @GPUGlobal float[] input,
                @GPUGlobal float[] output
        ) {
            int id = GPU.get_global_id(0);

            Float2 left = new Float2(input[id], input[id] * 2.0f);
            Float2 sum = VectorMath.add(left, bias);

            output[id] = sum.x + sum.y;
        }

        @net.sixik.ga_utils.javatogpu.api.annotations.GPU
        public static void structBufferExample(
                @GPUGlobal SamplePoint[] input,
                @GPUGlobal SamplePoint[] output
        ) {
            int id = GPU.get_global_id(0);
            output[id].x = input[id].x + 1.0;
            output[id].y = input[id].y + 2.0;
        }
    }

    public static final class KernelMath {

        private static final float LIMIT = 32.0f;

        private KernelMath() {
        }

        @CCode
        public static void clamp(FloatPtr ptr) {
            if (ptr.value > LIMIT) {
                ptr.value = LIMIT;
            }
        }

        @CCode(inline = true)
        public static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }
    }

    public static final class VectorMath {

        private VectorMath() {
        }

        @CCode
        public static Float2 add(Float2 left, Float2 right) {
            return new Float2(left.x + right.x, left.y + right.y);
        }
    }

    @GPUStruct
    public static final class SamplePoint {

        public double x;
        public double y;

        public SamplePoint() {
        }

        public SamplePoint(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    @GPUStruct
    public static final class SampleData {

        public SamplePoint point;
        public double bias;
        public int index;

        public SampleData() {
        }

        public SampleData(SamplePoint point, double bias, int index) {
            this.point = point;
            this.bias = bias;
            this.index = index;
        }
    }
}
