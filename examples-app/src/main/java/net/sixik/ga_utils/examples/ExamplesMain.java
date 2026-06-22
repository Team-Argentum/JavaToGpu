package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.Float2;
import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.Image1DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DBufferReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DBufferWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import net.sixik.ga_utils.javatogpu.api.UInt;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend;

public final class ExamplesMain {

    private ExamplesMain() {
    }

    public static void main(String[] args) {
        float[] floatInput = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
        float[] basicOutput = new float[floatInput.length];
        float[] vectorOutput = new float[floatInput.length];
        float[] nativeOutput = new float[floatInput.length];
        float[] libraryOutput = new float[floatInput.length];
        float[] attributeOutput = new float[floatInput.length];

        int[] controlInput = new int[]{1, 2, 3, 4};
        int[] controlOutput = new int[controlInput.length];
        int[] atomicState = new int[]{5, 6, 7, 8};
        int[] atomicOutput = new int[atomicState.length];
        int[] unsignedScalarOutput = new int[floatInput.length];
        int[] image1dOutput = new int[]{0, 0};
        int[] image1dArrayOutput = new int[]{0, 0};
        int[] image1dBufferOutput = new int[]{0, 0};
        int[] imageOutput = new int[]{0, 0};
        int[] image2dArrayOutput = new int[]{0, 0};
        int[] samplerlessImageOutput = new int[]{0, 0};
        int[] imageMetadataOutput = new int[]{0, 0};
        int[] unsignedImageOutput = new int[]{0, 0};
        int[] unsignedImage3dOutput = new int[]{0, 0};
        int[] image3dMetadataOutput = new int[]{0, 0};
        float[] image3dOutput = new float[]{0.0f, 0.0f};
        float[] samplerlessImage3dOutput = new float[]{0.0f, 0.0f};
        int[] rIntPixels = new int[]{11, 22};
        int[] rgIntPixels = new int[]{11, 22, 33, 44};
        int[] rUIntPixels = new int[]{101, 202};
        int[] rgUIntPixels = new int[]{101, 202, 303, 404};
        int[] rgba1dUIntPixels = new int[]{1, 2, 3, 4, 5, 6, 7, 8};
        float[] rFloatPixels = new float[]{1.25f, 2.5f};
        float[] rgFloatPixels = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
        byte[] rgba8Pixels = new byte[]{
                0, 127, (byte) 255, 64,
                5, 10, 15, 20
        };
        float[] rgba3dPixels = new float[]{
                1.0f, 0.0f, 0.0f, 1.0f,
                0.0f, 1.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f
        };
        int[] rgba3dUIntPixels = new int[]{
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                13, 14, 15, 16
        };

        double[] doubleInput = new double[]{1.0, 2.0, 3.0, 4.0};
        double[] structOutput = new double[doubleInput.length];

        Vec2[] structBufferInput = new Vec2[]{
                new Vec2(1.0, 2.0),
                new Vec2(3.0, 4.0),
                new Vec2(5.0, 6.0),
                new Vec2(7.0, 8.0)
        };
        Vec2[] structBufferOutput = new Vec2[structBufferInput.length];
        for (int i = 0; i < structBufferOutput.length; i++) {
            structBufferOutput[i] = new Vec2();
        }

        float[] lookup = new float[]{10.0f, 20.0f, 30.0f, 40.0f};
        float[] scratch = new float[lookup.length];
        float[] localOutput = new float[lookup.length];
        float[] qualifierOutput = new float[floatInput.length];
        float[] pointerQualifierOutput = new float[floatInput.length];

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            GpuRuntime.setBackend(backend);

            System.out.println("Running basic example...");
            GpuShowcase.basicMath(floatInput, basicOutput);
            System.out.println("basicOutput[0] = " + basicOutput[0]);

            System.out.println("Running control-flow example...");
            GpuShowcase.controlFlowExample(controlInput, controlOutput);
            System.out.println("controlOutput[0] = " + controlOutput[0]);

            System.out.println("Running do-while example...");
            GpuShowcase.doWhileExample(controlInput, controlOutput);
            System.out.println("doWhileOutput[0] = " + controlOutput[0]);

            System.out.println("Running vector example...");
            GpuShowcase.vectorExample(new Float2(1.0f, 0.5f), floatInput, vectorOutput);
            System.out.println("vectorOutput[0] = " + vectorOutput[0]);

            System.out.println("Running native helper example...");
            GpuShowcase.nativeHelperExample(floatInput, nativeOutput);
            System.out.println("nativeOutput[0] = " + nativeOutput[0]);

            System.out.println("Running @CCodeLibrary example...");
            GpuShowcase.libraryHelperExample(floatInput, libraryOutput);
            System.out.println("libraryOutput[0] = " + libraryOutput[0]);

            System.out.println("Running @OpenCLAttributes example...");
            GpuShowcase.attributeExample(floatInput, attributeOutput);
            System.out.println("attributeOutput[0] = " + attributeOutput[0]);

            System.out.println("Running struct example...");
            GpuShowcase.structExample(
                    new SampleData(0.75, 3),
                    doubleInput,
                    structOutput
            );
            System.out.println("structOutput[0] = " + structOutput[0]);

            System.out.println("Running struct buffer example...");
            GpuShowcase.structBufferExample(structBufferInput, structBufferOutput);
            System.out.println("structBufferOutput[0] = (" + structBufferOutput[0].x + ", " + structBufferOutput[0].y + ")");

            System.out.println("Running atomic example...");
            GpuShowcase.atomicExample(atomicState, atomicOutput);
            System.out.println("atomicOutput[0] = " + atomicOutput[0]);

            System.out.println("Running local memory example...");
            GpuShowcase.localMemoryExample(lookup, scratch, localOutput);
            System.out.println("localOutput[0] = " + localOutput[0]);

            System.out.println("Running qualifier example...");
            GpuShowcase.qualifierExample(floatInput, qualifierOutput);
            System.out.println("qualifierOutput[0] = " + qualifierOutput[0]);

            System.out.println("Running pointer qualifier example...");
            GpuShowcase.pointerQualifierExample(floatInput, pointerQualifierOutput);
            System.out.println("pointerQualifierOutput[0] = " + pointerQualifierOutput[0]);

            System.out.println("Running unsigned scalar example...");
            GpuShowcase.unsignedScalarExample(new UInt(41), unsignedScalarOutput);
            System.out.println("unsignedScalarOutput[0] = " + unsignedScalarOutput[0]);

            System.out.println("Running image example...");
            try (
                    Image2DReadOnly inputImage = backend.createReadOnlyRgbaIntImage(
                            2,
                            1,
                            new int[]{
                                    1, 2, 3, 4,
                                    5, 6, 7, 8
                            }
                    );
                    Image2DWriteOnly outputImage = backend.createWriteOnlyRgbaFloatImage(2, 1);
                    Sampler sampler = backend.createNearestClampToEdgeSampler()
            ) {
                GpuShowcase.imageExample(inputImage, outputImage, sampler, imageOutput);
                float[] writtenPixels = backend.readRgbaFloatImage(outputImage);
                System.out.println("imageOutput[0] = " + imageOutput[0]);
                System.out.println("writtenPixel[0..3] = "
                        + writtenPixels[0] + ", "
                        + writtenPixels[1] + ", "
                        + writtenPixels[2] + ", "
                        + writtenPixels[3]);
            }

            System.out.println("Running 1D image example...");
            try (
                    Image1DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage1D(2, rgba1dUIntPixels);
                    Image1DWriteOnly outputImage = backend.createWriteOnlyRgbaUIntImage1D(2);
                    Sampler sampler = backend.createNearestClampToEdgeSampler()
            ) {
                GpuShowcase.image1dExample(inputImage, outputImage, sampler, image1dOutput);
                int[] writtenPixels = backend.readRgbaUIntImage1D(outputImage);
                System.out.println("image1dOutput[0] = " + image1dOutput[0]);
                System.out.println("written1dPixel[0..3] = "
                        + writtenPixels[0] + ", "
                        + writtenPixels[1] + ", "
                        + writtenPixels[2] + ", "
                        + writtenPixels[3]);
            }

            System.out.println("Running 1D image array example...");
            try (
                    Image1DArrayReadOnly inputImage = backend.createReadOnlyRgbaUIntImage1DArray(2, 2, rgba3dUIntPixels);
                    Image1DArrayWriteOnly outputImage = backend.createWriteOnlyRgbaUIntImage1DArray(2, 2)
            ) {
                GpuShowcase.image1dArrayExample(inputImage, outputImage, image1dArrayOutput);
                int[] writtenPixels = backend.readRgbaUIntImage1DArray(outputImage);
                System.out.println("image1dArrayOutput[0] = " + image1dArrayOutput[0]);
                System.out.println("written1dArrayPixel[0..3] = "
                        + writtenPixels[0] + ", "
                        + writtenPixels[1] + ", "
                        + writtenPixels[2] + ", "
                        + writtenPixels[3]);
            }

            System.out.println("Running 1D image buffer example...");
            try (
                    Image1DBufferReadOnly inputImage = backend.createReadOnlyRgbaIntImage1DBuffer(2, new int[]{1, 2, 3, 4, 5, 6, 7, 8});
                    Image1DBufferWriteOnly outputImage = backend.createWriteOnlyRgbaIntImage1DBuffer(2)
            ) {
                GpuShowcase.image1dBufferExample(inputImage, outputImage, image1dBufferOutput);
                int[] writtenPixels = backend.readRgbaIntImage1DBuffer(outputImage);
                System.out.println("image1dBufferOutput[0] = " + image1dBufferOutput[0]);
                System.out.println("written1dBufferPixel[0..3] = "
                        + writtenPixels[0] + ", "
                        + writtenPixels[1] + ", "
                        + writtenPixels[2] + ", "
                        + writtenPixels[3]);
            }

            System.out.println("Running 2D image array example...");
            try (
                    Image2DArrayReadOnly inputImage = backend.createReadOnlyRgbaUIntImage2DArray(2, 1, 2, rgba3dUIntPixels);
                    Image2DArrayWriteOnly outputImage = backend.createWriteOnlyRgbaUIntImage2DArray(2, 1, 2)
            ) {
                GpuShowcase.image2dArrayExample(inputImage, outputImage, image2dArrayOutput);
                int[] writtenPixels = backend.readRgbaUIntImage2DArray(outputImage);
                System.out.println("image2dArrayOutput[0] = " + image2dArrayOutput[0]);
                System.out.println("written2dArrayPixel[0..3] = "
                        + writtenPixels[0] + ", "
                        + writtenPixels[1] + ", "
                        + writtenPixels[2] + ", "
                        + writtenPixels[3]);
            }

            System.out.println("Running samplerless image example...");
            try (Image2DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage(
                    2,
                    1,
                    new int[]{
                            1, 2, 3, 4,
                            5, 6, 7, 8
                    }
            )) {
                GpuShowcase.samplerlessImageExample(inputImage, samplerlessImageOutput);
                System.out.println("samplerlessImageOutput[0] = " + samplerlessImageOutput[0]);
            }

            System.out.println("Running image metadata example...");
            try (Image2DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage(
                    2,
                    1,
                    new int[]{
                            1, 2, 3, 4,
                            5, 6, 7, 8
                    }
            )) {
                GpuShowcase.imageMetadataExample(inputImage, imageMetadataOutput);
                System.out.println("imageMetadataOutput[0] = " + imageMetadataOutput[0]);
            }

            System.out.println("Running RGBA8 image roundtrip example...");
            try (Image2DReadOnly rgba8Image = backend.createReadOnlyRgba8Image(2, 1, rgba8Pixels)) {
                byte[] readBack = backend.readRgba8Image(rgba8Image);
                System.out.println("rgba8Pixel[0..3] = "
                        + Byte.toUnsignedInt(readBack[0]) + ", "
                        + Byte.toUnsignedInt(readBack[1]) + ", "
                        + Byte.toUnsignedInt(readBack[2]) + ", "
                        + Byte.toUnsignedInt(readBack[3]));
            }

            System.out.println("Running R float image roundtrip example...");
            try (Image2DReadOnly rFloatImage = backend.createReadOnlyRFloatImage(2, 1, rFloatPixels)) {
                float[] readBack = backend.readRFloatImage(rFloatImage);
                System.out.println("rFloatPixel[0..1] = " + readBack[0] + ", " + readBack[1]);
            }

            System.out.println("Running RG float image roundtrip example...");
            try (Image2DReadOnly rgFloatImage = backend.createReadOnlyRgFloatImage(2, 1, rgFloatPixels)) {
                float[] readBack = backend.readRgFloatImage(rgFloatImage);
                System.out.println("rgFloatPixel[0..3] = "
                        + readBack[0] + ", "
                        + readBack[1] + ", "
                        + readBack[2] + ", "
                        + readBack[3]);
            }

            System.out.println("Running R int image roundtrip example...");
            try (Image2DReadOnly rIntImage = backend.createReadOnlyRIntImage(2, 1, rIntPixels)) {
                int[] readBack = backend.readRIntImage(rIntImage);
                System.out.println("rIntPixel[0..1] = " + readBack[0] + ", " + readBack[1]);
            }

            System.out.println("Running RG int image roundtrip example...");
            try (Image2DReadOnly rgIntImage = backend.createReadOnlyRgIntImage(2, 1, rgIntPixels)) {
                int[] readBack = backend.readRgIntImage(rgIntImage);
                System.out.println("rgIntPixel[0..3] = "
                        + readBack[0] + ", "
                        + readBack[1] + ", "
                        + readBack[2] + ", "
                        + readBack[3]);
            }

            System.out.println("Running R uint image roundtrip example...");
            try (Image2DReadOnly rUIntImage = backend.createReadOnlyRUIntImage(2, 1, rUIntPixels)) {
                int[] readBack = backend.readRUIntImage(rUIntImage);
                System.out.println("rUIntPixel[0..1] = " + readBack[0] + ", " + readBack[1]);
            }

            System.out.println("Running RG uint image roundtrip example...");
            try (Image2DReadOnly rgUIntImage = backend.createReadOnlyRgUIntImage(2, 1, rgUIntPixels)) {
                int[] readBack = backend.readRgUIntImage(rgUIntImage);
                System.out.println("rgUIntPixel[0..3] = "
                        + readBack[0] + ", "
                        + readBack[1] + ", "
                        + readBack[2] + ", "
                        + readBack[3]);
            }

            System.out.println("Running unsigned image example...");
            try (
                    Image2DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage(
                            2,
                            1,
                            new int[]{
                                    1, 2, 3, 4,
                                    5, 6, 7, 8
                            }
                    );
                    Image2DWriteOnly outputImage = backend.createWriteOnlyRgbaUIntImage(2, 1);
                    Sampler sampler = backend.createNearestClampToEdgeSampler()
            ) {
                GpuShowcase.unsignedImageExample(inputImage, outputImage, sampler, unsignedImageOutput);
                int[] writtenPixels = backend.readRgbaUIntImage(outputImage);
                System.out.println("unsignedImageOutput[0] = " + unsignedImageOutput[0]);
                System.out.println("writtenUIntPixel[0..3] = "
                        + writtenPixels[0] + ", "
                        + writtenPixels[1] + ", "
                        + writtenPixels[2] + ", "
                        + writtenPixels[3]);
            }

            System.out.println("Running 3D image example...");
            try (
                    Image3DReadOnly inputImage = backend.createReadOnlyRgbaFloatImage3D(2, 1, 2, rgba3dPixels);
                    Image3DWriteOnly outputImage = backend.createWriteOnlyRgbaFloatImage3D(2, 1, 2);
                    Sampler sampler = backend.createNearestClampToEdgeSampler()
            ) {
                GpuShowcase.image3dExample(inputImage, outputImage, sampler, image3dOutput);
                float[] writtenPixels = backend.readRgbaFloatImage3D(outputImage);
                System.out.println("image3dOutput[0] = " + image3dOutput[0]);
                System.out.println("written3dPixel[0..3] = "
                        + writtenPixels[0] + ", "
                        + writtenPixels[1] + ", "
                        + writtenPixels[2] + ", "
                        + writtenPixels[3]);
            }

            System.out.println("Running samplerless 3D image example...");
            try (Image3DReadOnly inputImage = backend.createReadOnlyRgbaFloatImage3D(2, 1, 2, rgba3dPixels)) {
                GpuShowcase.samplerlessImage3dExample(inputImage, samplerlessImage3dOutput);
                System.out.println("samplerlessImage3dOutput[0] = " + samplerlessImage3dOutput[0]);
            }

            System.out.println("Running 3D image metadata example...");
            try (Image3DReadOnly inputImage = backend.createReadOnlyRgbaFloatImage3D(2, 1, 2, rgba3dPixels)) {
                GpuShowcase.image3dMetadataExample(inputImage, image3dMetadataOutput);
                System.out.println("image3dMetadataOutput[0] = " + image3dMetadataOutput[0]);
            }

            System.out.println("Running 3D unsigned image example...");
            try (
                    Image3DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage3D(2, 1, 2, rgba3dUIntPixels);
                    Image3DWriteOnly outputImage = backend.createWriteOnlyRgbaUIntImage3D(2, 1, 2);
                    Sampler sampler = backend.createNearestClampToEdgeSampler()
            ) {
                GpuShowcase.unsignedImage3dExample(inputImage, outputImage, sampler, unsignedImage3dOutput);
                int[] writtenPixels = backend.readRgbaUIntImage3D(outputImage);
                System.out.println("unsignedImage3dOutput[0] = " + unsignedImage3dOutput[0]);
                System.out.println("writtenUInt3dPixel[0..3] = "
                        + writtenPixels[0] + ", "
                        + writtenPixels[1] + ", "
                        + writtenPixels[2] + ", "
                        + writtenPixels[3]);
            }
        } catch (RuntimeException exception) {
            System.out.println("GPU execution failed: " + exception.getMessage());
        } finally {
            GpuRuntime.setBackend(GpuRuntime.defaultBackend());
        }

        System.out.println("Running ASM compiler example...");
        System.out.println(AsmExamples.compileStructuredAsmExample());
    }
}
