package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.Float2;
import net.sixik.ga_utils.javatogpu.api.Float4;
import net.sixik.ga_utils.javatogpu.api.FloatPtr;
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
import net.sixik.ga_utils.javatogpu.api.Int2;
import net.sixik.ga_utils.javatogpu.api.Int4;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import net.sixik.ga_utils.javatogpu.api.UInt;
import net.sixik.ga_utils.javatogpu.api.UInt4;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
import net.sixik.ga_utils.javatogpu.api.annotations.GPULocal;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUConstant;
import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLQualifiers;
import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLAttributes;

public final class GpuShowcase {

    private GpuShowcase() {
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void basicMath(
            @GPUGlobal float[] input,
            @GPUGlobal float[] output
    ) {
        int id = GPU.get_global_id(0);
        FloatPtr ptr = new FloatPtr(input[id]);

        GpuSupport.clamp(ptr);
        output[id] = GpuSupport.lerp(ptr.value, GPU.sin(input[id]), 0.25f);
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void controlFlowExample(
            @GPUGlobal int[] input,
            @GPUGlobal int[] output
    ) {
        int id = GPU.get_global_id(0);
        int value = input[id];
        int step = 0;

        while (step < 3) {
            switch (step) {
                case 0:
                    value += 2;
                    break;
                case 1:
                    value *= 2;
                    break;
                default:
                    value -= 1;
                    break;
            }
            step++;
        }

        output[id] = value;
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void doWhileExample(
            @GPUGlobal int[] input,
            @GPUGlobal int[] output
    ) {
        int id = GPU.get_global_id(0);
        int value = input[id];
        int step = 0;

        do {
            value = GPU.max(value, step);
            step++;
        } while (step < 2);

        output[id] = value;
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void vectorExample(
            Float2 bias,
            @GPUGlobal float[] input,
            @GPUGlobal float[] output
    ) {
        int id = GPU.get_global_id(0);

        Float2 left = new Float2(input[id], input[id] * 2.0f);
        Float2 sum = GpuSupport.add(left, bias);

        output[id] = sum.x + sum.y;
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void nativeHelperExample(
            @GPUGlobal float[] input,
            @GPUGlobal float[] output
    ) {
        int id = GPU.get_global_id(0);
        output[id] = GpuSupport.rawBlend(input[id], 0.1f);
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void libraryHelperExample(
            @GPUGlobal float[] input,
            @GPUGlobal float[] output
    ) {
        int id = GPU.get_global_id(0);
        output[id] = ReusableMathLibrary.norm(input[id]);
    }

    @OpenCLAttributes({"work_group_size_hint(4, 1, 1)"})
    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void attributeExample(
            @GPUGlobal float[] input,
            @GPUGlobal float[] output
    ) {
        int id = GPU.get_global_id(0);
        output[id] = GPU.max(input[id], 1.0f) + 1.0f;
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void structExample(
            SampleData sample,
            @GPUGlobal double[] input,
            @GPUGlobal double[] output
    ) {
        int id = GPU.get_global_id(0);

        Vec2 point = new Vec2(input[id], input[id] * 2.0);
        SampleData localSample = new SampleData(0.5, id);

        output[id] = point.x + point.y + sample.bias + sample.index
                + localSample.bias + localSample.index;
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void structBufferExample(
            @GPUGlobal Vec2[] input,
            @GPUGlobal Vec2[] output
    ) {
        int id = GPU.get_global_id(0);
        output[id].x = input[id].x + 1.0;
        output[id].y = input[id].y + 2.0;
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void atomicExample(
            @GPUGlobal int[] state,
            @GPUGlobal int[] output
    ) {
        int id = GPU.get_global_id(0);
        int previous = GPU.atomic_add(state, id, 2);
        output[id] = previous + GPU.atomic_xor(state, id, 31);
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void localMemoryExample(
            @GPUConstant float[] lookup,
            @GPULocal float[] scratch,
            @GPUGlobal float[] output
    ) {
        int gid = GPU.get_global_id(0);
        int lid = GPU.get_local_id(0);

        scratch[lid] = lookup[lid];
        GPU.local_mem_fence();
        GPU.local_barrier();
        output[gid] = scratch[lid];
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void qualifierExample(
            @OpenCLQualifiers({"const", "restrict"}) @GPUGlobal(constant = true) float[] input,
            @OpenCLQualifiers({"restrict"}) @GPUGlobal float[] output
    ) {
        int id = GPU.get_global_id(0);
        output[id] = input[id] * 2.0f;
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void pointerQualifierExample(
            @GPUGlobal float[] input,
            @GPUGlobal float[] output
    ) {
        int id = GPU.get_global_id(0);
        FloatPtr ptr = new FloatPtr(input[id]);
        output[id] = PointerQualifierExample.read(ptr);
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void unsignedScalarExample(
            UInt bias,
            @GPUGlobal int[] output
    ) {
        int id = GPU.get_global_id(0);
        UInt limited = GPU.clamp(GPU.max(bias, new UInt(4)), new UInt(4), new UInt(32));
        UInt result = GPU.min(limited, new UInt(17));
        output[id] = result.value;
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void imageExample(
            Image2DReadOnly inputImage,
            Image2DWriteOnly outputImage,
            Sampler sampler,
            @GPUGlobal int[] output
    ) {
        int id = GPU.get_global_id(0);
        Int2 coords = new Int2(id, 0);
        Int4 pixel = GPU.read_imagei(inputImage, sampler, coords);
        output[id] = pixel.x + pixel.y + pixel.z + pixel.w;
        GPU.write_imagef(outputImage, coords, new Float4(1.0f, 0.5f, 0.25f, 1.0f));
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void samplerlessImageExample(
            Image2DReadOnly inputImage,
            @GPUGlobal int[] output
    ) {
        int id = GPU.get_global_id(0);
        Int2 coords = new Int2(id, 0);
        UInt4 pixel = GPU.read_imageui(inputImage, coords);
        output[id] = pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_width(inputImage);
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void imageMetadataExample(
            Image2DReadOnly inputImage,
            @GPUGlobal int[] output
    ) {
        int id = GPU.get_global_id(0);
        int channelOrder = GPU.get_image_channel_order(inputImage);
        int channelType = GPU.get_image_channel_data_type(inputImage);
        output[id] = channelOrder == GPU.CL_RGBA && channelType == GPU.CL_UNSIGNED_INT32 ? 1 : 0;
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void image1dExample(
            Image1DReadOnly inputImage,
            Image1DWriteOnly outputImage,
            Sampler sampler,
            @GPUGlobal int[] output
    ) {
        int id = GPU.get_global_id(0);
        UInt4 pixel = GPU.read_imageui(inputImage, sampler, id);
        output[id] = pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_width(inputImage);
        GPU.write_imageui(outputImage, id, new UInt4(9, 10, 11, 12));
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void image1dArrayExample(
            Image1DArrayReadOnly inputImage,
            Image1DArrayWriteOnly outputImage,
            @GPUGlobal int[] output
    ) {
        int id = GPU.get_global_id(0);
        Int2 coords = new Int2(id, 0);
        UInt4 pixel = GPU.read_imageui(inputImage, coords);
        output[id] = pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_array_size(inputImage);
        GPU.write_imageui(outputImage, coords, new UInt4(9, 10, 11, 12));
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void image1dBufferExample(
            Image1DBufferReadOnly inputImage,
            Image1DBufferWriteOnly outputImage,
            @GPUGlobal int[] output
    ) {
        int id = GPU.get_global_id(0);
        Int4 pixel = GPU.read_imagei(inputImage, id);
        output[id] = pixel.x + GPU.get_image_width(inputImage);
        GPU.write_imagei(outputImage, id, new Int4(9, 10, 11, 12));
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void image2dArrayExample(
            Image2DArrayReadOnly inputImage,
            Image2DArrayWriteOnly outputImage,
            @GPUGlobal int[] output
    ) {
        int id = GPU.get_global_id(0);
        Int4 coords = new Int4(id, 0, 0, 0);
        UInt4 pixel = GPU.read_imageui(inputImage, coords);
        output[id] = pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_height(inputImage) + GPU.get_image_array_size(inputImage);
        GPU.write_imageui(outputImage, coords, new UInt4(9, 10, 11, 12));
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void unsignedImageExample(
            Image2DReadOnly inputImage,
            Image2DWriteOnly outputImage,
            Sampler sampler,
            @GPUGlobal int[] output
    ) {
        int id = GPU.get_global_id(0);
        Int2 coords = new Int2(id, 0);
        UInt4 pixel = GPU.read_imageui(inputImage, sampler, coords);
        output[id] = pixel.x + pixel.y + pixel.z + pixel.w;
        GPU.write_imageui(outputImage, coords, new UInt4(9, 10, 11, 12));
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void image3dExample(
            Image3DReadOnly inputImage,
            Image3DWriteOnly outputImage,
            Sampler sampler,
            @GPUGlobal float[] output
    ) {
        int id = GPU.get_global_id(0);
        Int4 coords = new Int4(id, 0, 0, 0);
        Float4 pixel = GPU.read_imagef(inputImage, sampler, coords);
        output[id] = pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_depth(inputImage);
        GPU.write_imagef(outputImage, coords, new Float4(0.25f, 0.5f, 0.75f, 1.0f));
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void samplerlessImage3dExample(
            Image3DReadOnly inputImage,
            @GPUGlobal float[] output
    ) {
        int id = GPU.get_global_id(0);
        Int4 coords = new Int4(id, 0, 0, 0);
        Float4 pixel = GPU.read_imagef(inputImage, coords);
        output[id] = pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_depth(inputImage);
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void image3dMetadataExample(
            Image3DReadOnly inputImage,
            @GPUGlobal int[] output
    ) {
        int id = GPU.get_global_id(0);
        int channelOrder = GPU.get_image_channel_order(inputImage);
        int channelType = GPU.get_image_channel_data_type(inputImage);
        output[id] = channelOrder == GPU.CL_RGBA && channelType == GPU.CL_FLOAT ? GPU.get_image_depth(inputImage) : 0;
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void unsignedImage3dExample(
            Image3DReadOnly inputImage,
            Image3DWriteOnly outputImage,
            Sampler sampler,
            @GPUGlobal int[] output
    ) {
        int id = GPU.get_global_id(0);
        Int4 coords = new Int4(id, 0, 0, 0);
        UInt4 pixel = GPU.read_imageui(inputImage, sampler, coords);
        output[id] = pixel.x + pixel.y + pixel.z + pixel.w;
        GPU.write_imageui(outputImage, coords, new UInt4(9, 10, 11, 12));
    }
}
