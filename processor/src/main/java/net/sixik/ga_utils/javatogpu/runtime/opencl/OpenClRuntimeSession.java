package net.sixik.ga_utils.javatogpu.runtime.opencl;

import dev.denismasterherobrine.packager.opencl.core.OpenClBuffer;
import dev.denismasterherobrine.packager.opencl.core.OpenClCommandQueue;
import dev.denismasterherobrine.packager.opencl.core.OpenClContext;
import dev.denismasterherobrine.packager.opencl.core.OpenClDevice;
import dev.denismasterherobrine.packager.opencl.core.OpenClDevices;
import dev.denismasterherobrine.packager.opencl.core.OpenClException;
import dev.denismasterherobrine.packager.opencl.core.OpenClKernel;
import dev.denismasterherobrine.packager.opencl.core.OpenClProgram;
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
import net.sixik.ga_utils.javatogpu.api.Image3DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CL12;
import org.lwjgl.opencl.CLImageDesc;
import org.lwjgl.opencl.CLImageFormat;
import org.lwjgl.system.MemoryStack;

public final class OpenClRuntimeSession implements AutoCloseable {

    private static final Pattern OPENCL_VERSION_PATTERN = Pattern.compile("OpenCL\\s+(\\d+)\\.(\\d+)");
    private static final int CL_DEPTH = 0x10BD;

    private final OpenClDevice device;
    private final OpenClContext context;
    private final OpenClCommandQueue queue;

    private OpenClRuntimeSession(OpenClDevice device, OpenClContext context, OpenClCommandQueue queue) {
        this.device = device;
        this.context = context;
        this.queue = queue;
    }

    public static OpenClRuntimeSession createDefault() {
        OpenClDevice device = OpenClDevices.selectBest();
        if (device == null) {
            throw new IllegalStateException("No OpenCL device found");
        }

        OpenClContext context = OpenClContext.create(device);
        OpenClCommandQueue queue = context.createQueue(true);
        return new OpenClRuntimeSession(device, context, queue);
    }

    public OpenClCompiledKernel compileKernel(GpuKernelDescriptor descriptor) {
        OpenClProgram program = context.buildProgram(descriptor.kernelSource());
        OpenClKernel kernel = program.createKernel(descriptor.kernelName());
        return new OpenClCompiledKernel(descriptor, descriptor.kernelResource(), program, kernel);
    }

    public OpenClRuntimeCapabilities capabilities() {
        OpenClValidationDeviceInfo deviceInfo = validationDeviceInfo();
        return new OpenClRuntimeCapabilities(
                deviceInfo.deviceLabel(),
                deviceInfo.deviceVersion(),
                deviceInfo.supportsDoublePrecision(),
                deviceInfo.supportsImages(),
                deviceInfo.supportsImage3dWrites(),
                deviceInfo.localMemoryBytes(),
                deviceInfo.maxWorkGroupSize()
        );
    }

    public OpenClValidationDeviceInfo validationDeviceInfo() {
        long deviceHandle = device.device();
        String extensions = queryStringDeviceInfo(deviceHandle, CL10.CL_DEVICE_EXTENSIONS);
        String deviceVersion = queryStringDeviceInfo(deviceHandle, CL10.CL_DEVICE_VERSION);
        long platformHandle = queryLongDeviceInfo(deviceHandle, CL10.CL_DEVICE_PLATFORM);
        return new OpenClValidationDeviceInfo(
                device.label(),
                queryStringDeviceInfo(deviceHandle, CL10.CL_DEVICE_VENDOR),
                queryStringDeviceInfo(deviceHandle, CL10.CL_DRIVER_VERSION),
                deviceVersion,
                platformHandle == 0L ? "unknown" : queryStringPlatformInfo(platformHandle, CL10.CL_PLATFORM_NAME),
                platformHandle == 0L ? "unknown" : queryStringPlatformInfo(platformHandle, CL10.CL_PLATFORM_VERSION),
                supportsDoublePrecision(extensions),
                queryIntDeviceInfo(deviceHandle, CL10.CL_DEVICE_IMAGE_SUPPORT) != 0,
                supportsImage3dWrites(extensions, deviceVersion),
                device.localMemoryBytes(),
                device.maxWorkGroupSize()
        );
    }

    public OpenClBuffer createReadWriteBuffer(long sizeBytes) {
        return context.createReadWriteBuffer(sizeBytes);
    }

    public Image2DReadOnly createReadOnlyRgbaFloatImage(int width, int height, float[] rgba) {
        return createReadOnlyFloatImage(width, height, CL10.CL_RGBA, 4, rgba, "RGBA");
    }

    public Image1DReadOnly createReadOnlyRgbaFloatImage1D(int width, float[] rgba) {
        return createReadOnlyFloatImage1D(width, CL10.CL_RGBA, 4, rgba, "RGBA");
    }

    public Image2DWriteOnly createWriteOnlyRgbaFloatImage(int width, int height) {
        return createWriteOnlyFloatImage(width, height, CL10.CL_RGBA);
    }

    public Image1DWriteOnly createWriteOnlyRgbaFloatImage1D(int width) {
        return createWriteOnlyFloatImage1D(width, CL10.CL_RGBA);
    }

    public Image2DReadOnly createReadOnlyRFloatImage(int width, int height, float[] values) {
        return createReadOnlyFloatImage(width, height, CL10.CL_R, 1, values, "R");
    }

    public Image2DWriteOnly createWriteOnlyRFloatImage(int width, int height) {
        return createWriteOnlyFloatImage(width, height, CL10.CL_R);
    }

    public Image2DReadOnly createReadOnlyRgFloatImage(int width, int height, float[] values) {
        return createReadOnlyFloatImage(width, height, CL10.CL_RG, 2, values, "RG");
    }

    public Image2DWriteOnly createWriteOnlyRgFloatImage(int width, int height) {
        return createWriteOnlyFloatImage(width, height, CL10.CL_RG);
    }

    public Image2DReadOnly createReadOnlyDepthImage(int width, int height, float[] values) {
        return createReadOnlyFloatImage(width, height, CL_DEPTH, 1, values, "depth");
    }

    public Image2DWriteOnly createWriteOnlyDepthImage(int width, int height) {
        return createWriteOnlyFloatImage(width, height, CL_DEPTH);
    }

    public Image2DReadOnly createReadOnlyRIntImage(int width, int height, int[] values) {
        return createReadOnlyIntImage(width, height, CL10.CL_R, 1, CL10.CL_SIGNED_INT32, values, "R int");
    }

    public Image2DWriteOnly createWriteOnlyRIntImage(int width, int height) {
        return createWriteOnlyIntImage(width, height, CL10.CL_R, CL10.CL_SIGNED_INT32);
    }

    public Image2DReadOnly createReadOnlyRgIntImage(int width, int height, int[] values) {
        return createReadOnlyIntImage(width, height, CL10.CL_RG, 2, CL10.CL_SIGNED_INT32, values, "RG int");
    }

    public Image2DWriteOnly createWriteOnlyRgIntImage(int width, int height) {
        return createWriteOnlyIntImage(width, height, CL10.CL_RG, CL10.CL_SIGNED_INT32);
    }

    public Image2DReadOnly createReadOnlyRgbaIntImage(int width, int height, int[] rgba) {
        validateImageDimensions(width, height);
        validatePixelArrayLength(width, height, 4, rgba.length, "RGBA int");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(CL10.CL_RGBA)
                    .image_channel_data_type(CL10.CL_SIGNED_INT32);
            int[] errorCode = new int[1];
            long handle = CL10.clCreateImage2D(
                    context.handle(),
                    CL10.CL_MEM_READ_ONLY | CL10.CL_MEM_COPY_HOST_PTR,
                    format,
                    width,
                    height,
                    0L,
                    rgba,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage2D");
            return Image2DReadOnly.owned(handle, width, height);
        }
    }

    public Image2DWriteOnly createWriteOnlyRgbaIntImage(int width, int height) {
        validateImageDimensions(width, height);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(CL10.CL_RGBA)
                    .image_channel_data_type(CL10.CL_SIGNED_INT32);
            int[] errorCode = new int[1];
            long handle = CL10.clCreateImage2D(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    width,
                    height,
                    0L,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage2D");
            return Image2DWriteOnly.owned(handle, width, height);
        }
    }

    public Image2DReadOnly createReadOnlyRgbaUIntImage(int width, int height, int[] rgba) {
        validateImageDimensions(width, height);
        validatePixelArrayLength(width, height, 4, rgba.length, "RGBA uint");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(CL10.CL_RGBA)
                    .image_channel_data_type(CL10.CL_UNSIGNED_INT32);
            int[] errorCode = new int[1];
            long handle = CL10.clCreateImage2D(
                    context.handle(),
                    CL10.CL_MEM_READ_ONLY | CL10.CL_MEM_COPY_HOST_PTR,
                    format,
                    width,
                    height,
                    0L,
                    rgba,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage2D");
            return Image2DReadOnly.owned(handle, width, height);
        }
    }

    public Image1DReadOnly createReadOnlyRgbaUIntImage1D(int width, int[] rgba) {
        return createReadOnlyIntImage1D(width, CL10.CL_RGBA, 4, CL10.CL_UNSIGNED_INT32, rgba, "RGBA uint");
    }

    public Image2DReadOnly createReadOnlyRUIntImage(int width, int height, int[] values) {
        return createReadOnlyIntImage(width, height, CL10.CL_R, 1, CL10.CL_UNSIGNED_INT32, values, "R uint");
    }

    public Image2DWriteOnly createWriteOnlyRUIntImage(int width, int height) {
        return createWriteOnlyIntImage(width, height, CL10.CL_R, CL10.CL_UNSIGNED_INT32);
    }

    public Image2DReadOnly createReadOnlyRgUIntImage(int width, int height, int[] values) {
        return createReadOnlyIntImage(width, height, CL10.CL_RG, 2, CL10.CL_UNSIGNED_INT32, values, "RG uint");
    }

    public Image2DWriteOnly createWriteOnlyRgUIntImage(int width, int height) {
        return createWriteOnlyIntImage(width, height, CL10.CL_RG, CL10.CL_UNSIGNED_INT32);
    }

    public Image2DWriteOnly createWriteOnlyRgbaUIntImage(int width, int height) {
        validateImageDimensions(width, height);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(CL10.CL_RGBA)
                    .image_channel_data_type(CL10.CL_UNSIGNED_INT32);
            int[] errorCode = new int[1];
            long handle = CL10.clCreateImage2D(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    width,
                    height,
                    0L,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage2D");
            return Image2DWriteOnly.owned(handle, width, height);
        }
    }

    public Image1DWriteOnly createWriteOnlyRgbaUIntImage1D(int width) {
        validateImageDimensions(width);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(CL10.CL_RGBA)
                    .image_channel_data_type(CL10.CL_UNSIGNED_INT32);
            CLImageDesc desc = CLImageDesc.calloc(stack)
                    .image_type(CL12.CL_MEM_OBJECT_IMAGE1D)
                    .image_width(width)
                    .image_height(1)
                    .image_depth(1)
                    .image_array_size(1)
                    .image_row_pitch(0L)
                    .image_slice_pitch(0L)
                    .num_mip_levels(0)
                    .num_samples(0)
                    .buffer(0L)
                    .mem_object(0L);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    desc,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image1DWriteOnly.owned(handle, width);
        }
    }

    public Image1DReadOnly createReadOnlyRgbaIntImage1D(int width, int[] rgba) {
        return createReadOnlyIntImage1D(width, CL10.CL_RGBA, 4, CL10.CL_SIGNED_INT32, rgba, "RGBA int");
    }

    public Image1DWriteOnly createWriteOnlyRgbaIntImage1D(int width) {
        return createWriteOnlyIntImage1D(width, CL10.CL_RGBA, CL10.CL_SIGNED_INT32);
    }

    public Image1DArrayReadOnly createReadOnlyRgbaFloatImage1DArray(int width, int layers, float[] rgba) {
        return createReadOnlyFloatImage1DArray(width, layers, CL10.CL_RGBA, 4, rgba, "RGBA");
    }

    public Image1DArrayWriteOnly createWriteOnlyRgbaFloatImage1DArray(int width, int layers) {
        return createWriteOnlyFloatImage1DArray(width, layers, CL10.CL_RGBA);
    }

    public Image1DArrayReadOnly createReadOnlyRgbaIntImage1DArray(int width, int layers, int[] rgba) {
        return createReadOnlyIntImage1DArray(width, layers, CL10.CL_RGBA, 4, CL10.CL_SIGNED_INT32, rgba, "RGBA int");
    }

    public Image1DArrayWriteOnly createWriteOnlyRgbaIntImage1DArray(int width, int layers) {
        return createWriteOnlyIntImage1DArray(width, layers, CL10.CL_RGBA, CL10.CL_SIGNED_INT32);
    }

    public Image1DArrayReadOnly createReadOnlyRgbaUIntImage1DArray(int width, int layers, int[] rgba) {
        return createReadOnlyIntImage1DArray(width, layers, CL10.CL_RGBA, 4, CL10.CL_UNSIGNED_INT32, rgba, "RGBA uint");
    }

    public Image1DArrayWriteOnly createWriteOnlyRgbaUIntImage1DArray(int width, int layers) {
        return createWriteOnlyIntImage1DArray(width, layers, CL10.CL_RGBA, CL10.CL_UNSIGNED_INT32);
    }

    public Image1DBufferReadOnly createReadOnlyRgbaFloatImage1DBuffer(int width, float[] rgba) {
        return createReadOnlyFloatImage1DBuffer(width, CL10.CL_RGBA, 4, rgba, "RGBA");
    }

    public Image1DBufferWriteOnly createWriteOnlyRgbaFloatImage1DBuffer(int width) {
        return createWriteOnlyFloatImage1DBuffer(width, CL10.CL_RGBA);
    }

    public Image1DBufferReadOnly createReadOnlyRgbaIntImage1DBuffer(int width, int[] rgba) {
        return createReadOnlyIntImage1DBuffer(width, CL10.CL_RGBA, 4, CL10.CL_SIGNED_INT32, rgba, "RGBA int");
    }

    public Image1DBufferWriteOnly createWriteOnlyRgbaIntImage1DBuffer(int width) {
        return createWriteOnlyIntImage1DBuffer(width, CL10.CL_RGBA, CL10.CL_SIGNED_INT32);
    }

    public Image1DBufferReadOnly createReadOnlyRgbaUIntImage1DBuffer(int width, int[] rgba) {
        return createReadOnlyIntImage1DBuffer(width, CL10.CL_RGBA, 4, CL10.CL_UNSIGNED_INT32, rgba, "RGBA uint");
    }

    public Image1DBufferWriteOnly createWriteOnlyRgbaUIntImage1DBuffer(int width) {
        return createWriteOnlyIntImage1DBuffer(width, CL10.CL_RGBA, CL10.CL_UNSIGNED_INT32);
    }

    public Image2DArrayReadOnly createReadOnlyRgbaFloatImage2DArray(int width, int height, int layers, float[] rgba) {
        return createReadOnlyFloatImage2DArray(width, height, layers, CL10.CL_RGBA, 4, rgba, "RGBA");
    }

    public Image2DArrayWriteOnly createWriteOnlyRgbaFloatImage2DArray(int width, int height, int layers) {
        return createWriteOnlyFloatImage2DArray(width, height, layers, CL10.CL_RGBA);
    }

    public Image2DArrayReadOnly createReadOnlyRgbaIntImage2DArray(int width, int height, int layers, int[] rgba) {
        return createReadOnlyIntImage2DArray(width, height, layers, CL10.CL_RGBA, 4, CL10.CL_SIGNED_INT32, rgba, "RGBA int");
    }

    public Image2DArrayWriteOnly createWriteOnlyRgbaIntImage2DArray(int width, int height, int layers) {
        return createWriteOnlyIntImage2DArray(width, height, layers, CL10.CL_RGBA, CL10.CL_SIGNED_INT32);
    }

    public Image2DArrayReadOnly createReadOnlyRgbaUIntImage2DArray(int width, int height, int layers, int[] rgba) {
        return createReadOnlyIntImage2DArray(width, height, layers, CL10.CL_RGBA, 4, CL10.CL_UNSIGNED_INT32, rgba, "RGBA uint");
    }

    public Image2DArrayWriteOnly createWriteOnlyRgbaUIntImage2DArray(int width, int height, int layers) {
        return createWriteOnlyIntImage2DArray(width, height, layers, CL10.CL_RGBA, CL10.CL_UNSIGNED_INT32);
    }

    public Image2DReadOnly createReadOnlyRgba8Image(int width, int height, byte[] rgba) {
        validateImageDimensions(width, height);
        validatePixelArrayLength(width, height, 4, rgba.length, "RGBA byte");
        ByteBuffer pixels = ByteBuffer.allocateDirect(rgba.length);
        pixels.put(rgba).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(CL10.CL_RGBA)
                    .image_channel_data_type(CL10.CL_UNORM_INT8);
            int[] errorCode = new int[1];
            long handle = CL10.clCreateImage2D(
                    context.handle(),
                    CL10.CL_MEM_READ_ONLY | CL10.CL_MEM_COPY_HOST_PTR,
                    format,
                    width,
                    height,
                    0L,
                    pixels,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage2D");
            return Image2DReadOnly.owned(handle, width, height);
        }
    }

    public Image2DWriteOnly createWriteOnlyRgba8Image(int width, int height) {
        validateImageDimensions(width, height);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(CL10.CL_RGBA)
                    .image_channel_data_type(CL10.CL_UNORM_INT8);
            int[] errorCode = new int[1];
            long handle = CL10.clCreateImage2D(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    width,
                    height,
                    0L,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage2D");
            return Image2DWriteOnly.owned(handle, width, height);
        }
    }

    public Image2DMipmappedReadOnly createReadOnlyRgbaFloatImageMipmapped(int width, int height, int mipLevels, float[] rgba) {
        return createReadOnlyFloatImageMipmapped(width, height, mipLevels, CL10.CL_RGBA, 4, rgba, "RGBA");
    }

    public Image2DMipmappedWriteOnly createWriteOnlyRgbaFloatImageMipmapped(int width, int height, int mipLevels) {
        return createWriteOnlyFloatImageMipmapped(width, height, mipLevels, CL10.CL_RGBA);
    }

    public Image2DMipmappedReadOnly createReadOnlyRgbaUIntImageMipmapped(int width, int height, int mipLevels, int[] rgba) {
        return createReadOnlyIntImageMipmapped(width, height, mipLevels, CL10.CL_RGBA, 4, CL10.CL_UNSIGNED_INT32, rgba, "RGBA uint");
    }

    public Image2DMipmappedWriteOnly createWriteOnlyRgbaUIntImageMipmapped(int width, int height, int mipLevels) {
        return createWriteOnlyIntImageMipmapped(width, height, mipLevels, CL10.CL_RGBA, CL10.CL_UNSIGNED_INT32);
    }

    public Image3DReadOnly createReadOnlyRgbaFloatImage3D(int width, int height, int depth, float[] rgba) {
        return createReadOnlyFloatImage3D(width, height, depth, CL10.CL_RGBA, 4, rgba, "RGBA");
    }

    public Image3DWriteOnly createWriteOnlyRgbaFloatImage3D(int width, int height, int depth) {
        return createWriteOnlyFloatImage3D(width, height, depth, CL10.CL_RGBA);
    }

    public Image3DReadOnly createReadOnlyRgbaIntImage3D(int width, int height, int depth, int[] rgba) {
        return createReadOnlyIntImage3D(width, height, depth, CL10.CL_RGBA, 4, CL10.CL_SIGNED_INT32, rgba, "RGBA int");
    }

    public Image3DWriteOnly createWriteOnlyRgbaIntImage3D(int width, int height, int depth) {
        return createWriteOnlyIntImage3D(width, height, depth, CL10.CL_RGBA, CL10.CL_SIGNED_INT32);
    }

    public Image3DReadOnly createReadOnlyRgbaUIntImage3D(int width, int height, int depth, int[] rgba) {
        return createReadOnlyIntImage3D(width, height, depth, CL10.CL_RGBA, 4, CL10.CL_UNSIGNED_INT32, rgba, "RGBA uint");
    }

    public Image3DWriteOnly createWriteOnlyRgbaUIntImage3D(int width, int height, int depth) {
        return createWriteOnlyIntImage3D(width, height, depth, CL10.CL_RGBA, CL10.CL_UNSIGNED_INT32);
    }

    public float[] readRgbaFloatImage(Image2DReadOnly image) {
        return readFloatImage(image.handle(), image.width(), image.height(), 4);
    }

    public float[] readRgbaFloatImage(Image2DWriteOnly image) {
        return readFloatImage(image.handle(), image.width(), image.height(), 4);
    }

    public float[] readRFloatImage(Image2DReadOnly image) {
        return readFloatImage(image.handle(), image.width(), image.height(), 1);
    }

    public float[] readRFloatImage(Image2DWriteOnly image) {
        return readFloatImage(image.handle(), image.width(), image.height(), 1);
    }

    public float[] readRgFloatImage(Image2DReadOnly image) {
        return readFloatImage(image.handle(), image.width(), image.height(), 2);
    }

    public float[] readRgFloatImage(Image2DWriteOnly image) {
        return readFloatImage(image.handle(), image.width(), image.height(), 2);
    }

    public float[] readDepthImage(Image2DReadOnly image) {
        return readFloatImage(image.handle(), image.width(), image.height(), 1);
    }

    public float[] readDepthImage(Image2DWriteOnly image) {
        return readFloatImage(image.handle(), image.width(), image.height(), 1);
    }

    public int[] readRIntImage(Image2DReadOnly image) {
        return readIntImage(image.handle(), image.width(), image.height(), 1);
    }

    public int[] readRIntImage(Image2DWriteOnly image) {
        return readIntImage(image.handle(), image.width(), image.height(), 1);
    }

    public int[] readRgIntImage(Image2DReadOnly image) {
        return readIntImage(image.handle(), image.width(), image.height(), 2);
    }

    public int[] readRgIntImage(Image2DWriteOnly image) {
        return readIntImage(image.handle(), image.width(), image.height(), 2);
    }

    public int[] readRgbaIntImage(Image2DReadOnly image) {
        return readRgbaIntImage(image.handle(), image.width(), image.height());
    }

    public int[] readRgbaIntImage(Image2DWriteOnly image) {
        return readRgbaIntImage(image.handle(), image.width(), image.height());
    }

    public int[] readRgbaUIntImage(Image2DReadOnly image) {
        return readRgbaUIntImage(image.handle(), image.width(), image.height());
    }

    public int[] readRgbaUIntImage(Image2DWriteOnly image) {
        return readRgbaUIntImage(image.handle(), image.width(), image.height());
    }

    public int[] readRUIntImage(Image2DReadOnly image) {
        return readIntImage(image.handle(), image.width(), image.height(), 1);
    }

    public int[] readRUIntImage(Image2DWriteOnly image) {
        return readIntImage(image.handle(), image.width(), image.height(), 1);
    }

    public int[] readRgUIntImage(Image2DReadOnly image) {
        return readIntImage(image.handle(), image.width(), image.height(), 2);
    }

    public int[] readRgUIntImage(Image2DWriteOnly image) {
        return readIntImage(image.handle(), image.width(), image.height(), 2);
    }

    public byte[] readRgba8Image(Image2DReadOnly image) {
        return readRgba8Image(image.handle(), image.width(), image.height());
    }

    public byte[] readRgba8Image(Image2DWriteOnly image) {
        return readRgba8Image(image.handle(), image.width(), image.height());
    }

    public float[] readRgbaFloatImageMipmapped(Image2DMipmappedReadOnly image, int mipLevel) {
        return readFloatImageMipmapped(image.handle(), image.width(), image.height(), image.mipLevels(), mipLevel, 4);
    }

    public float[] readRgbaFloatImageMipmapped(Image2DMipmappedWriteOnly image, int mipLevel) {
        return readFloatImageMipmapped(image.handle(), image.width(), image.height(), image.mipLevels(), mipLevel, 4);
    }

    public int[] readRgbaUIntImageMipmapped(Image2DMipmappedReadOnly image, int mipLevel) {
        return readIntImageMipmapped(image.handle(), image.width(), image.height(), image.mipLevels(), mipLevel, 4);
    }

    public int[] readRgbaUIntImageMipmapped(Image2DMipmappedWriteOnly image, int mipLevel) {
        return readIntImageMipmapped(image.handle(), image.width(), image.height(), image.mipLevels(), mipLevel, 4);
    }

    public float[] readRgbaFloatImage1D(Image1DReadOnly image) {
        return readFloatImage1D(image.handle(), image.width(), 4);
    }

    public float[] readRgbaFloatImage1D(Image1DWriteOnly image) {
        return readFloatImage1D(image.handle(), image.width(), 4);
    }

    public int[] readRgbaUIntImage1D(Image1DReadOnly image) {
        return readIntImage1D(image.handle(), image.width(), 4);
    }

    public int[] readRgbaUIntImage1D(Image1DWriteOnly image) {
        return readIntImage1D(image.handle(), image.width(), 4);
    }

    public float[] readRgbaFloatImage1DArray(Image1DArrayReadOnly image) {
        return readFloatImage1DArray(image.handle(), image.width(), image.layers(), 4);
    }

    public float[] readRgbaFloatImage1DArray(Image1DArrayWriteOnly image) {
        return readFloatImage1DArray(image.handle(), image.width(), image.layers(), 4);
    }

    public int[] readRgbaIntImage1D(Image1DReadOnly image) {
        return readIntImage1D(image.handle(), image.width(), 4);
    }

    public int[] readRgbaIntImage1D(Image1DWriteOnly image) {
        return readIntImage1D(image.handle(), image.width(), 4);
    }

    public int[] readRgbaIntImage1DArray(Image1DArrayReadOnly image) {
        return readIntImage1DArray(image.handle(), image.width(), image.layers(), 4);
    }

    public int[] readRgbaIntImage1DArray(Image1DArrayWriteOnly image) {
        return readIntImage1DArray(image.handle(), image.width(), image.layers(), 4);
    }

    public int[] readRgbaUIntImage1DArray(Image1DArrayReadOnly image) {
        return readIntImage1DArray(image.handle(), image.width(), image.layers(), 4);
    }

    public int[] readRgbaUIntImage1DArray(Image1DArrayWriteOnly image) {
        return readIntImage1DArray(image.handle(), image.width(), image.layers(), 4);
    }

    public float[] readRgbaFloatImage1DBuffer(Image1DBufferReadOnly image) {
        return readFloatImage1D(image.handle(), image.width(), 4);
    }

    public float[] readRgbaFloatImage1DBuffer(Image1DBufferWriteOnly image) {
        return readFloatImage1D(image.handle(), image.width(), 4);
    }

    public int[] readRgbaIntImage1DBuffer(Image1DBufferReadOnly image) {
        return readIntImage1D(image.handle(), image.width(), 4);
    }

    public int[] readRgbaIntImage1DBuffer(Image1DBufferWriteOnly image) {
        return readIntImage1D(image.handle(), image.width(), 4);
    }

    public int[] readRgbaUIntImage1DBuffer(Image1DBufferReadOnly image) {
        return readIntImage1D(image.handle(), image.width(), 4);
    }

    public int[] readRgbaUIntImage1DBuffer(Image1DBufferWriteOnly image) {
        return readIntImage1D(image.handle(), image.width(), 4);
    }

    public float[] readRgbaFloatImage2DArray(Image2DArrayReadOnly image) {
        return readFloatImage2DArray(image.handle(), image.width(), image.height(), image.layers(), 4);
    }

    public float[] readRgbaFloatImage2DArray(Image2DArrayWriteOnly image) {
        return readFloatImage2DArray(image.handle(), image.width(), image.height(), image.layers(), 4);
    }

    public int[] readRgbaIntImage2DArray(Image2DArrayReadOnly image) {
        return readIntImage2DArray(image.handle(), image.width(), image.height(), image.layers(), 4);
    }

    public int[] readRgbaIntImage2DArray(Image2DArrayWriteOnly image) {
        return readIntImage2DArray(image.handle(), image.width(), image.height(), image.layers(), 4);
    }

    public int[] readRgbaUIntImage2DArray(Image2DArrayReadOnly image) {
        return readIntImage2DArray(image.handle(), image.width(), image.height(), image.layers(), 4);
    }

    public int[] readRgbaUIntImage2DArray(Image2DArrayWriteOnly image) {
        return readIntImage2DArray(image.handle(), image.width(), image.height(), image.layers(), 4);
    }

    public float[] readRgbaFloatImage3D(Image3DReadOnly image) {
        return readFloatImage3D(image.handle(), image.width(), image.height(), image.depth(), 4);
    }

    public float[] readRgbaFloatImage3D(Image3DWriteOnly image) {
        return readFloatImage3D(image.handle(), image.width(), image.height(), image.depth(), 4);
    }

    public int[] readRgbaIntImage3D(Image3DReadOnly image) {
        return readIntImage3D(image.handle(), image.width(), image.height(), image.depth(), 4);
    }

    public int[] readRgbaIntImage3D(Image3DWriteOnly image) {
        return readIntImage3D(image.handle(), image.width(), image.height(), image.depth(), 4);
    }

    public int[] readRgbaUIntImage3D(Image3DReadOnly image) {
        return readIntImage3D(image.handle(), image.width(), image.height(), image.depth(), 4);
    }

    public int[] readRgbaUIntImage3D(Image3DWriteOnly image) {
        return readIntImage3D(image.handle(), image.width(), image.height(), image.depth(), 4);
    }

    public Sampler createSampler(boolean normalizedCoordinates, int addressingMode, int filterMode) {
        int[] errorCode = new int[1];
        long handle = CL10.clCreateSampler(
                context.handle(),
                normalizedCoordinates,
                addressingMode,
                filterMode,
                errorCode
        );
        OpenClException.check(errorCode[0], "clCreateSampler");
        return Sampler.owned(handle);
    }

    public OpenClCommandQueue queue() {
        return queue;
    }

    private int queryIntDeviceInfo(long deviceHandle, int paramName) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer buffer = stack.malloc(Integer.BYTES);
            OpenClException.check(
                    CL10.clGetDeviceInfo(deviceHandle, paramName, buffer, null),
                    "clGetDeviceInfo"
            );
            return buffer.getInt(0);
        }
    }

    private long queryLongDeviceInfo(long deviceHandle, int paramName) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer buffer = stack.mallocPointer(1);
            OpenClException.check(
                    CL10.clGetDeviceInfo(deviceHandle, paramName, buffer, null),
                    "clGetDeviceInfo"
            );
            return buffer.get(0);
        }
    }

    private String queryStringDeviceInfo(long deviceHandle, int paramName) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer sizeBuffer = stack.mallocPointer(1);
            OpenClException.check(
                    CL10.clGetDeviceInfo(deviceHandle, paramName, (ByteBuffer) null, sizeBuffer),
                    "clGetDeviceInfo"
            );
            int size = Math.toIntExact(sizeBuffer.get(0));
            ByteBuffer buffer = stack.malloc(size);
            OpenClException.check(
                    CL10.clGetDeviceInfo(deviceHandle, paramName, buffer, null),
                    "clGetDeviceInfo"
            );

            int length = size;
            while (length > 0 && buffer.get(length - 1) == 0) {
                length--;
            }
            byte[] bytes = new byte[length];
            buffer.get(0, bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private String queryStringPlatformInfo(long platformHandle, int paramName) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer sizeBuffer = stack.mallocPointer(1);
            OpenClException.check(
                    CL10.clGetPlatformInfo(platformHandle, paramName, (ByteBuffer) null, sizeBuffer),
                    "clGetPlatformInfo"
            );
            int size = Math.toIntExact(sizeBuffer.get(0));
            ByteBuffer buffer = stack.malloc(size);
            OpenClException.check(
                    CL10.clGetPlatformInfo(platformHandle, paramName, buffer, null),
                    "clGetPlatformInfo"
            );

            int length = size;
            while (length > 0 && buffer.get(length - 1) == 0) {
                length--;
            }
            byte[] bytes = new byte[length];
            buffer.get(0, bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private boolean supportsDoublePrecision(String extensions) {
        return containsExtension(extensions, "cl_khr_fp64") || containsExtension(extensions, "cl_amd_fp64");
    }

    private boolean supportsImage3dWrites(String extensions, String deviceVersion) {
        if (containsExtension(extensions, "cl_khr_3d_image_writes")) {
            return true;
        }
        Matcher matcher = OPENCL_VERSION_PATTERN.matcher(deviceVersion == null ? "" : deviceVersion);
        if (!matcher.find()) {
            return false;
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        return major > 1 || (major == 1 && minor >= 2);
    }

    private boolean containsExtension(String extensions, String extension) {
        if (extensions == null || extensions.isBlank()) {
            return false;
        }
        for (String token : extensions.split("\\s+")) {
            if (extension.equals(token)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        Throwable failure = null;

        try {
            queue.close();
        } catch (Throwable throwable) {
            failure = throwable;
        }

        try {
            context.close();
        } catch (Throwable throwable) {
            if (failure != null) {
                failure.addSuppressed(throwable);
            } else {
                failure = throwable;
            }
        }

        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure != null) {
            throw new RuntimeException("Failed to close OpenCL runtime session for " + device.label(), failure);
        }
    }

    private Image2DReadOnly createReadOnlyFloatImage(
            int width,
            int height,
            int channelOrder,
            int componentCount,
            float[] values,
            String formatName
    ) {
        validateImageDimensions(width, height);
        validatePixelArrayLength(width, height, componentCount, values.length, formatName + " float");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(CL10.CL_FLOAT);
            int[] errorCode = new int[1];
            long handle = CL10.clCreateImage2D(
                    context.handle(),
                    CL10.CL_MEM_READ_ONLY | CL10.CL_MEM_COPY_HOST_PTR,
                    format,
                    width,
                    height,
                    0L,
                    values,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage2D");
            return Image2DReadOnly.owned(handle, width, height);
        }
    }

    private Image2DWriteOnly createWriteOnlyFloatImage(int width, int height, int channelOrder) {
        validateImageDimensions(width, height);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(CL10.CL_FLOAT);
            int[] errorCode = new int[1];
            long handle = CL10.clCreateImage2D(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    width,
                    height,
                    0L,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage2D");
            return Image2DWriteOnly.owned(handle, width, height);
        }
    }

    private Image2DMipmappedReadOnly createReadOnlyFloatImageMipmapped(
            int width,
            int height,
            int mipLevels,
            int channelOrder,
            int componentCount,
            float[] values,
            String formatName
    ) {
        validateMipmappedImageDimensions(width, height, mipLevels);
        validateMipmappedPixelArrayLength(width, height, mipLevels, componentCount, values.length, formatName + " float mip chain");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(CL10.CL_FLOAT);
            CLImageDesc desc = initMipmappedImage2DDesc(CLImageDesc.calloc(stack), width, height, mipLevels);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_READ_ONLY,
                    format,
                    desc,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            uploadFloatMipmappedImage(handle, width, height, mipLevels, componentCount, values);
            return Image2DMipmappedReadOnly.owned(handle, width, height, mipLevels);
        }
    }

    private Image2DMipmappedWriteOnly createWriteOnlyFloatImageMipmapped(int width, int height, int mipLevels, int channelOrder) {
        validateMipmappedImageDimensions(width, height, mipLevels);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(CL10.CL_FLOAT);
            CLImageDesc desc = initMipmappedImage2DDesc(CLImageDesc.calloc(stack), width, height, mipLevels);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    desc,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image2DMipmappedWriteOnly.owned(handle, width, height, mipLevels);
        }
    }

    private Image1DReadOnly createReadOnlyFloatImage1D(
            int width,
            int channelOrder,
            int componentCount,
            float[] values,
            String formatName
    ) {
        validateImageDimensions(width);
        validatePixelArrayLength(width, componentCount, values.length, formatName + " float");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(CL10.CL_FLOAT);
            CLImageDesc desc = CLImageDesc.calloc(stack)
                    .image_type(CL12.CL_MEM_OBJECT_IMAGE1D)
                    .image_width(width)
                    .image_height(1)
                    .image_depth(1)
                    .image_array_size(1)
                    .image_row_pitch(0L)
                    .image_slice_pitch(0L)
                    .num_mip_levels(0)
                    .num_samples(0)
                    .buffer(0L)
                    .mem_object(0L);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_READ_ONLY | CL10.CL_MEM_COPY_HOST_PTR,
                    format,
                    desc,
                    values,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image1DReadOnly.owned(handle, width);
        }
    }

    private Image1DWriteOnly createWriteOnlyFloatImage1D(int width, int channelOrder) {
        validateImageDimensions(width);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(CL10.CL_FLOAT);
            CLImageDesc desc = CLImageDesc.calloc(stack)
                    .image_type(CL12.CL_MEM_OBJECT_IMAGE1D)
                    .image_width(width)
                    .image_height(1)
                    .image_depth(1)
                    .image_array_size(1)
                    .image_row_pitch(0L)
                    .image_slice_pitch(0L)
                    .num_mip_levels(0)
                    .num_samples(0)
                    .buffer(0L)
                    .mem_object(0L);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    desc,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image1DWriteOnly.owned(handle, width);
        }
    }

    private Image1DArrayReadOnly createReadOnlyFloatImage1DArray(
            int width,
            int layers,
            int channelOrder,
            int componentCount,
            float[] values,
            String formatName
    ) {
        validateImageDimensions(width);
        validateImageDimensions(layers);
        validate1DArrayPixelArrayLength(width, layers, componentCount, values.length, formatName + " float");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(CL10.CL_FLOAT);
            CLImageDesc desc = initImageDesc(CLImageDesc.calloc(stack), CL12.CL_MEM_OBJECT_IMAGE1D_ARRAY, width, 1, 1, layers, 0L);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_READ_ONLY | CL10.CL_MEM_COPY_HOST_PTR,
                    format,
                    desc,
                    values,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image1DArrayReadOnly.owned(handle, width, layers);
        }
    }

    private Image1DArrayWriteOnly createWriteOnlyFloatImage1DArray(int width, int layers, int channelOrder) {
        validateImageDimensions(width);
        validateImageDimensions(layers);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(CL10.CL_FLOAT);
            CLImageDesc desc = initImageDesc(CLImageDesc.calloc(stack), CL12.CL_MEM_OBJECT_IMAGE1D_ARRAY, width, 1, 1, layers, 0L);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    desc,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image1DArrayWriteOnly.owned(handle, width, layers);
        }
    }

    private Image1DBufferReadOnly createReadOnlyFloatImage1DBuffer(
            int width,
            int channelOrder,
            int componentCount,
            float[] values,
            String formatName
    ) {
        validateImageDimensions(width);
        validatePixelArrayLength(width, componentCount, values.length, formatName + " float");
        long backingHandle = createBackingBuffer(values);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(CL10.CL_FLOAT);
            CLImageDesc desc = initImageDesc(CLImageDesc.calloc(stack), CL12.CL_MEM_OBJECT_IMAGE1D_BUFFER, width, 1, 1, 1, backingHandle);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_READ_ONLY,
                    format,
                    desc,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image1DBufferReadOnly.owned(handle, width, backingHandle);
        } catch (RuntimeException exception) {
            CL10.clReleaseMemObject(backingHandle);
            throw exception;
        }
    }

    private Image1DBufferWriteOnly createWriteOnlyFloatImage1DBuffer(int width, int channelOrder) {
        validateImageDimensions(width);
        long backingHandle = createBackingBuffer((long) width * 4L * Float.BYTES);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(CL10.CL_FLOAT);
            CLImageDesc desc = initImageDesc(CLImageDesc.calloc(stack), CL12.CL_MEM_OBJECT_IMAGE1D_BUFFER, width, 1, 1, 1, backingHandle);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    desc,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image1DBufferWriteOnly.owned(handle, width, backingHandle);
        } catch (RuntimeException exception) {
            CL10.clReleaseMemObject(backingHandle);
            throw exception;
        }
    }

    private Image2DArrayReadOnly createReadOnlyFloatImage2DArray(
            int width,
            int height,
            int layers,
            int channelOrder,
            int componentCount,
            float[] values,
            String formatName
    ) {
        validateImageDimensions(width, height);
        validateImageDimensions(layers);
        validate2DArrayPixelArrayLength(width, height, layers, componentCount, values.length, formatName + " float");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(CL10.CL_FLOAT);
            CLImageDesc desc = initImageDesc(CLImageDesc.calloc(stack), CL12.CL_MEM_OBJECT_IMAGE2D_ARRAY, width, height, 1, layers, 0L);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_READ_ONLY | CL10.CL_MEM_COPY_HOST_PTR,
                    format,
                    desc,
                    values,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image2DArrayReadOnly.owned(handle, width, height, layers);
        }
    }

    private Image2DArrayWriteOnly createWriteOnlyFloatImage2DArray(int width, int height, int layers, int channelOrder) {
        validateImageDimensions(width, height);
        validateImageDimensions(layers);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(CL10.CL_FLOAT);
            CLImageDesc desc = initImageDesc(CLImageDesc.calloc(stack), CL12.CL_MEM_OBJECT_IMAGE2D_ARRAY, width, height, 1, layers, 0L);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    desc,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image2DArrayWriteOnly.owned(handle, width, height, layers);
        }
    }

    private Image2DReadOnly createReadOnlyIntImage(
            int width,
            int height,
            int channelOrder,
            int componentCount,
            int channelDataType,
            int[] values,
            String formatName
    ) {
        validateImageDimensions(width, height);
        validatePixelArrayLength(width, height, componentCount, values.length, formatName);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(channelDataType);
            int[] errorCode = new int[1];
            long handle = CL10.clCreateImage2D(
                    context.handle(),
                    CL10.CL_MEM_READ_ONLY | CL10.CL_MEM_COPY_HOST_PTR,
                    format,
                    width,
                    height,
                    0L,
                    values,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage2D");
            return Image2DReadOnly.owned(handle, width, height);
        }
    }

    private Image2DWriteOnly createWriteOnlyIntImage(int width, int height, int channelOrder, int channelDataType) {
        validateImageDimensions(width, height);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(channelDataType);
            int[] errorCode = new int[1];
            long handle = CL10.clCreateImage2D(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    width,
                    height,
                    0L,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage2D");
            return Image2DWriteOnly.owned(handle, width, height);
        }
    }

    private Image2DMipmappedReadOnly createReadOnlyIntImageMipmapped(
            int width,
            int height,
            int mipLevels,
            int channelOrder,
            int componentCount,
            int channelDataType,
            int[] values,
            String formatName
    ) {
        validateMipmappedImageDimensions(width, height, mipLevels);
        validateMipmappedPixelArrayLength(width, height, mipLevels, componentCount, values.length, formatName + " mip chain");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(channelDataType);
            CLImageDesc desc = initMipmappedImage2DDesc(CLImageDesc.calloc(stack), width, height, mipLevels);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_READ_ONLY,
                    format,
                    desc,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            uploadIntMipmappedImage(handle, width, height, mipLevels, componentCount, values);
            return Image2DMipmappedReadOnly.owned(handle, width, height, mipLevels);
        }
    }

    private Image2DMipmappedWriteOnly createWriteOnlyIntImageMipmapped(int width, int height, int mipLevels, int channelOrder, int channelDataType) {
        validateMipmappedImageDimensions(width, height, mipLevels);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(channelDataType);
            CLImageDesc desc = initMipmappedImage2DDesc(CLImageDesc.calloc(stack), width, height, mipLevels);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    desc,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image2DMipmappedWriteOnly.owned(handle, width, height, mipLevels);
        }
    }

    private Image1DReadOnly createReadOnlyIntImage1D(
            int width,
            int channelOrder,
            int componentCount,
            int channelDataType,
            int[] values,
            String formatName
    ) {
        validateImageDimensions(width);
        validatePixelArrayLength(width, componentCount, values.length, formatName);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(channelDataType);
            CLImageDesc desc = CLImageDesc.calloc(stack)
                    .image_type(CL12.CL_MEM_OBJECT_IMAGE1D)
                    .image_width(width)
                    .image_height(1)
                    .image_depth(1)
                    .image_array_size(1)
                    .image_row_pitch(0L)
                    .image_slice_pitch(0L)
                    .num_mip_levels(0)
                    .num_samples(0)
                    .buffer(0L)
                    .mem_object(0L);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_READ_ONLY | CL10.CL_MEM_COPY_HOST_PTR,
                    format,
                    desc,
                    values,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image1DReadOnly.owned(handle, width);
        }
    }

    private Image1DWriteOnly createWriteOnlyIntImage1D(int width, int channelOrder, int channelDataType) {
        validateImageDimensions(width);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(channelDataType);
            CLImageDesc desc = CLImageDesc.calloc(stack)
                    .image_type(CL12.CL_MEM_OBJECT_IMAGE1D)
                    .image_width(width)
                    .image_height(1)
                    .image_depth(1)
                    .image_array_size(1)
                    .image_row_pitch(0L)
                    .image_slice_pitch(0L)
                    .num_mip_levels(0)
                    .num_samples(0)
                    .buffer(0L)
                    .mem_object(0L);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    desc,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image1DWriteOnly.owned(handle, width);
        }
    }

    private Image1DArrayReadOnly createReadOnlyIntImage1DArray(
            int width,
            int layers,
            int channelOrder,
            int componentCount,
            int channelDataType,
            int[] values,
            String formatName
    ) {
        validateImageDimensions(width);
        validateImageDimensions(layers);
        validate1DArrayPixelArrayLength(width, layers, componentCount, values.length, formatName);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(channelDataType);
            CLImageDesc desc = initImageDesc(CLImageDesc.calloc(stack), CL12.CL_MEM_OBJECT_IMAGE1D_ARRAY, width, 1, 1, layers, 0L);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_READ_ONLY | CL10.CL_MEM_COPY_HOST_PTR,
                    format,
                    desc,
                    values,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image1DArrayReadOnly.owned(handle, width, layers);
        }
    }

    private Image1DArrayWriteOnly createWriteOnlyIntImage1DArray(int width, int layers, int channelOrder, int channelDataType) {
        validateImageDimensions(width);
        validateImageDimensions(layers);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(channelDataType);
            CLImageDesc desc = initImageDesc(CLImageDesc.calloc(stack), CL12.CL_MEM_OBJECT_IMAGE1D_ARRAY, width, 1, 1, layers, 0L);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    desc,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image1DArrayWriteOnly.owned(handle, width, layers);
        }
    }

    private Image1DBufferReadOnly createReadOnlyIntImage1DBuffer(
            int width,
            int channelOrder,
            int componentCount,
            int channelDataType,
            int[] values,
            String formatName
    ) {
        validateImageDimensions(width);
        validatePixelArrayLength(width, componentCount, values.length, formatName);
        long backingHandle = createBackingBuffer(values);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(channelDataType);
            CLImageDesc desc = initImageDesc(CLImageDesc.calloc(stack), CL12.CL_MEM_OBJECT_IMAGE1D_BUFFER, width, 1, 1, 1, backingHandle);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_READ_ONLY,
                    format,
                    desc,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image1DBufferReadOnly.owned(handle, width, backingHandle);
        } catch (RuntimeException exception) {
            CL10.clReleaseMemObject(backingHandle);
            throw exception;
        }
    }

    private Image1DBufferWriteOnly createWriteOnlyIntImage1DBuffer(int width, int channelOrder, int channelDataType) {
        validateImageDimensions(width);
        long backingHandle = createBackingBuffer((long) width * 4L * Integer.BYTES);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(channelDataType);
            CLImageDesc desc = initImageDesc(CLImageDesc.calloc(stack), CL12.CL_MEM_OBJECT_IMAGE1D_BUFFER, width, 1, 1, 1, backingHandle);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    desc,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image1DBufferWriteOnly.owned(handle, width, backingHandle);
        } catch (RuntimeException exception) {
            CL10.clReleaseMemObject(backingHandle);
            throw exception;
        }
    }

    private Image2DArrayReadOnly createReadOnlyIntImage2DArray(
            int width,
            int height,
            int layers,
            int channelOrder,
            int componentCount,
            int channelDataType,
            int[] values,
            String formatName
    ) {
        validateImageDimensions(width, height);
        validateImageDimensions(layers);
        validate2DArrayPixelArrayLength(width, height, layers, componentCount, values.length, formatName);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(channelDataType);
            CLImageDesc desc = initImageDesc(CLImageDesc.calloc(stack), CL12.CL_MEM_OBJECT_IMAGE2D_ARRAY, width, height, 1, layers, 0L);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_READ_ONLY | CL10.CL_MEM_COPY_HOST_PTR,
                    format,
                    desc,
                    values,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image2DArrayReadOnly.owned(handle, width, height, layers);
        }
    }

    private Image2DArrayWriteOnly createWriteOnlyIntImage2DArray(int width, int height, int layers, int channelOrder, int channelDataType) {
        validateImageDimensions(width, height);
        validateImageDimensions(layers);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(channelDataType);
            CLImageDesc desc = initImageDesc(CLImageDesc.calloc(stack), CL12.CL_MEM_OBJECT_IMAGE2D_ARRAY, width, height, 1, layers, 0L);
            int[] errorCode = new int[1];
            long handle = CL12.clCreateImage(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    desc,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage");
            return Image2DArrayWriteOnly.owned(handle, width, height, layers);
        }
    }

    private Image3DReadOnly createReadOnlyFloatImage3D(
            int width,
            int height,
            int depth,
            int channelOrder,
            int componentCount,
            float[] values,
            String formatName
    ) {
        validateImageDimensions(width, height, depth);
        validatePixelArrayLength(width, height, depth, componentCount, values.length, formatName + " float");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(CL10.CL_FLOAT);
            int[] errorCode = new int[1];
            long handle = CL10.clCreateImage3D(
                    context.handle(),
                    CL10.CL_MEM_READ_ONLY | CL10.CL_MEM_COPY_HOST_PTR,
                    format,
                    width,
                    height,
                    depth,
                    0L,
                    0L,
                    values,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage3D");
            return Image3DReadOnly.owned(handle, width, height, depth);
        }
    }

    private Image3DWriteOnly createWriteOnlyFloatImage3D(int width, int height, int depth, int channelOrder) {
        validateImageDimensions(width, height, depth);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(CL10.CL_FLOAT);
            int[] errorCode = new int[1];
            long handle = CL10.clCreateImage3D(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    width,
                    height,
                    depth,
                    0L,
                    0L,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage3D");
            return Image3DWriteOnly.owned(handle, width, height, depth);
        }
    }

    private Image3DReadOnly createReadOnlyIntImage3D(
            int width,
            int height,
            int depth,
            int channelOrder,
            int componentCount,
            int channelDataType,
            int[] values,
            String formatName
    ) {
        validateImageDimensions(width, height, depth);
        validatePixelArrayLength(width, height, depth, componentCount, values.length, formatName);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(channelDataType);
            int[] errorCode = new int[1];
            long handle = CL10.clCreateImage3D(
                    context.handle(),
                    CL10.CL_MEM_READ_ONLY | CL10.CL_MEM_COPY_HOST_PTR,
                    format,
                    width,
                    height,
                    depth,
                    0L,
                    0L,
                    values,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage3D");
            return Image3DReadOnly.owned(handle, width, height, depth);
        }
    }

    private Image3DWriteOnly createWriteOnlyIntImage3D(int width, int height, int depth, int channelOrder, int channelDataType) {
        validateImageDimensions(width, height, depth);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLImageFormat format = CLImageFormat.calloc(stack)
                    .image_channel_order(channelOrder)
                    .image_channel_data_type(channelDataType);
            int[] errorCode = new int[1];
            long handle = CL10.clCreateImage3D(
                    context.handle(),
                    CL10.CL_MEM_WRITE_ONLY,
                    format,
                    width,
                    height,
                    depth,
                    0L,
                    0L,
                    (java.nio.ByteBuffer) null,
                    errorCode
            );
            OpenClException.check(errorCode[0], "clCreateImage3D");
            return Image3DWriteOnly.owned(handle, width, height, depth);
        }
    }

    private float[] readFloatImage(long handle, int width, int height, int componentCount) {
        validateImageHandle(handle, width, height);
        float[] rgba = new float[width * height * componentCount];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer origin = stack.mallocPointer(3);
            origin.put(0, 0L).put(1, 0L).put(2, 0L);
            PointerBuffer region = stack.mallocPointer(3);
            region.put(0, width).put(1, height).put(2, 1L);
            OpenClException.check(
                    CL10.clEnqueueReadImage(queue.handle(), handle, true, origin, region, 0L, 0L, rgba, null, null),
                    "clEnqueueReadImage"
            );
            queue.finish();
            return rgba;
        }
    }

    private float[] readFloatImage1D(long handle, int width, int componentCount) {
        validateImageHandle(handle, width);
        float[] rgba = new float[width * componentCount];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer origin = stack.mallocPointer(3);
            origin.put(0, 0L).put(1, 0L).put(2, 0L);
            PointerBuffer region = stack.mallocPointer(3);
            region.put(0, width).put(1, 1L).put(2, 1L);
            OpenClException.check(
                    CL10.clEnqueueReadImage(queue.handle(), handle, true, origin, region, 0L, 0L, rgba, null, null),
                    "clEnqueueReadImage"
            );
            queue.finish();
            return rgba;
        }
    }

    private float[] readFloatImage1DArray(long handle, int width, int layers, int componentCount) {
        validate1DArrayImageHandle(handle, width, layers);
        float[] rgba = new float[width * layers * componentCount];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer origin = stack.mallocPointer(3);
            origin.put(0, 0L).put(1, 0L).put(2, 0L);
            PointerBuffer region = stack.mallocPointer(3);
            region.put(0, width).put(1, layers).put(2, 1L);
            OpenClException.check(
                    CL10.clEnqueueReadImage(queue.handle(), handle, true, origin, region, 0L, 0L, rgba, null, null),
                    "clEnqueueReadImage"
            );
            queue.finish();
            return rgba;
        }
    }

    private float[] readFloatImage2DArray(long handle, int width, int height, int layers, int componentCount) {
        validate2DArrayImageHandle(handle, width, height, layers);
        float[] rgba = new float[width * height * layers * componentCount];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer origin = stack.mallocPointer(3);
            origin.put(0, 0L).put(1, 0L).put(2, 0L);
            PointerBuffer region = stack.mallocPointer(3);
            region.put(0, width).put(1, height).put(2, layers);
            OpenClException.check(
                    CL10.clEnqueueReadImage(queue.handle(), handle, true, origin, region, 0L, 0L, rgba, null, null),
                    "clEnqueueReadImage"
            );
            queue.finish();
            return rgba;
        }
    }

    private float[] readFloatImage3D(long handle, int width, int height, int depth, int componentCount) {
        validateImageHandle(handle, width, height, depth);
        float[] rgba = new float[width * height * depth * componentCount];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer origin = stack.mallocPointer(3);
            origin.put(0, 0L).put(1, 0L).put(2, 0L);
            PointerBuffer region = stack.mallocPointer(3);
            region.put(0, width).put(1, height).put(2, depth);
            OpenClException.check(
                    CL10.clEnqueueReadImage(queue.handle(), handle, true, origin, region, 0L, 0L, rgba, null, null),
                    "clEnqueueReadImage"
            );
            queue.finish();
            return rgba;
        }
    }

    private float[] readFloatImageMipmapped(long handle, int width, int height, int mipLevels, int mipLevel, int componentCount) {
        validateMipmappedImageHandle(handle, width, height, mipLevels, mipLevel);
        int mipWidth = mipExtent(width, mipLevel);
        int mipHeight = mipExtent(height, mipLevel);
        float[] values = new float[mipWidth * mipHeight * componentCount];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer origin = stack.mallocPointer(3);
            origin.put(0, 0L).put(1, 0L).put(2, mipLevel);
            PointerBuffer region = stack.mallocPointer(3);
            region.put(0, mipWidth).put(1, mipHeight).put(2, 1L);
            OpenClException.check(
                    CL10.clEnqueueReadImage(queue.handle(), handle, true, origin, region, 0L, 0L, values, null, null),
                    "clEnqueueReadImage"
            );
            queue.finish();
            return values;
        }
    }

    private int[] readRgbaIntImage(long handle, int width, int height) {
        validateImageHandle(handle, width, height);
        int[] rgba = new int[width * height * 4];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer origin = stack.mallocPointer(3);
            origin.put(0, 0L).put(1, 0L).put(2, 0L);
            PointerBuffer region = stack.mallocPointer(3);
            region.put(0, width).put(1, height).put(2, 1L);
            OpenClException.check(
                    CL10.clEnqueueReadImage(queue.handle(), handle, true, origin, region, 0L, 0L, rgba, null, null),
                    "clEnqueueReadImage"
            );
            queue.finish();
            return rgba;
        }
    }

    private int[] readIntImage(long handle, int width, int height, int componentCount) {
        validateImageHandle(handle, width, height);
        int[] values = new int[width * height * componentCount];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer origin = stack.mallocPointer(3);
            origin.put(0, 0L).put(1, 0L).put(2, 0L);
            PointerBuffer region = stack.mallocPointer(3);
            region.put(0, width).put(1, height).put(2, 1L);
            OpenClException.check(
                    CL10.clEnqueueReadImage(queue.handle(), handle, true, origin, region, 0L, 0L, values, null, null),
                    "clEnqueueReadImage"
            );
            queue.finish();
            return values;
        }
    }

    private int[] readRgbaUIntImage(long handle, int width, int height) {
        validateImageHandle(handle, width, height);
        int[] rgba = new int[width * height * 4];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer origin = stack.mallocPointer(3);
            origin.put(0, 0L).put(1, 0L).put(2, 0L);
            PointerBuffer region = stack.mallocPointer(3);
            region.put(0, width).put(1, height).put(2, 1L);
            OpenClException.check(
                    CL10.clEnqueueReadImage(queue.handle(), handle, true, origin, region, 0L, 0L, rgba, null, null),
                    "clEnqueueReadImage"
            );
            queue.finish();
            return rgba;
        }
    }

    private int[] readIntImage1D(long handle, int width, int componentCount) {
        validateImageHandle(handle, width);
        int[] values = new int[width * componentCount];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer origin = stack.mallocPointer(3);
            origin.put(0, 0L).put(1, 0L).put(2, 0L);
            PointerBuffer region = stack.mallocPointer(3);
            region.put(0, width).put(1, 1L).put(2, 1L);
            OpenClException.check(
                    CL10.clEnqueueReadImage(queue.handle(), handle, true, origin, region, 0L, 0L, values, null, null),
                    "clEnqueueReadImage"
            );
            queue.finish();
            return values;
        }
    }

    private int[] readIntImage1DArray(long handle, int width, int layers, int componentCount) {
        validate1DArrayImageHandle(handle, width, layers);
        int[] values = new int[width * layers * componentCount];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer origin = stack.mallocPointer(3);
            origin.put(0, 0L).put(1, 0L).put(2, 0L);
            PointerBuffer region = stack.mallocPointer(3);
            region.put(0, width).put(1, layers).put(2, 1L);
            OpenClException.check(
                    CL10.clEnqueueReadImage(queue.handle(), handle, true, origin, region, 0L, 0L, values, null, null),
                    "clEnqueueReadImage"
            );
            queue.finish();
            return values;
        }
    }

    private int[] readIntImage2DArray(long handle, int width, int height, int layers, int componentCount) {
        validate2DArrayImageHandle(handle, width, height, layers);
        int[] values = new int[width * height * layers * componentCount];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer origin = stack.mallocPointer(3);
            origin.put(0, 0L).put(1, 0L).put(2, 0L);
            PointerBuffer region = stack.mallocPointer(3);
            region.put(0, width).put(1, height).put(2, layers);
            OpenClException.check(
                    CL10.clEnqueueReadImage(queue.handle(), handle, true, origin, region, 0L, 0L, values, null, null),
                    "clEnqueueReadImage"
            );
            queue.finish();
            return values;
        }
    }

    private int[] readIntImage3D(long handle, int width, int height, int depth, int componentCount) {
        validateImageHandle(handle, width, height, depth);
        int[] values = new int[width * height * depth * componentCount];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer origin = stack.mallocPointer(3);
            origin.put(0, 0L).put(1, 0L).put(2, 0L);
            PointerBuffer region = stack.mallocPointer(3);
            region.put(0, width).put(1, height).put(2, depth);
            OpenClException.check(
                    CL10.clEnqueueReadImage(queue.handle(), handle, true, origin, region, 0L, 0L, values, null, null),
                    "clEnqueueReadImage"
            );
            queue.finish();
            return values;
        }
    }

    private int[] readIntImageMipmapped(long handle, int width, int height, int mipLevels, int mipLevel, int componentCount) {
        validateMipmappedImageHandle(handle, width, height, mipLevels, mipLevel);
        int mipWidth = mipExtent(width, mipLevel);
        int mipHeight = mipExtent(height, mipLevel);
        int[] values = new int[mipWidth * mipHeight * componentCount];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer origin = stack.mallocPointer(3);
            origin.put(0, 0L).put(1, 0L).put(2, mipLevel);
            PointerBuffer region = stack.mallocPointer(3);
            region.put(0, mipWidth).put(1, mipHeight).put(2, 1L);
            OpenClException.check(
                    CL10.clEnqueueReadImage(queue.handle(), handle, true, origin, region, 0L, 0L, values, null, null),
                    "clEnqueueReadImage"
            );
            queue.finish();
            return values;
        }
    }

    private byte[] readRgba8Image(long handle, int width, int height) {
        validateImageHandle(handle, width, height);
        ByteBuffer rgba = ByteBuffer.allocateDirect(width * height * 4);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer origin = stack.mallocPointer(3);
            origin.put(0, 0L).put(1, 0L).put(2, 0L);
            PointerBuffer region = stack.mallocPointer(3);
            region.put(0, width).put(1, height).put(2, 1L);
            OpenClException.check(
                    CL10.clEnqueueReadImage(queue.handle(), handle, true, origin, region, 0L, 0L, rgba, null, null),
                    "clEnqueueReadImage"
            );
            queue.finish();
            byte[] values = new byte[rgba.capacity()];
            rgba.position(0);
            rgba.get(values);
            return values;
        }
    }

    private CLImageDesc initImageDesc(CLImageDesc desc, int imageType, int width, int height, int depth, int arraySize, long memObject) {
        return desc.image_type(imageType)
                .image_width(width)
                .image_height(height)
                .image_depth(depth)
                .image_array_size(arraySize)
                .image_row_pitch(0L)
                .image_slice_pitch(0L)
                .num_mip_levels(0)
                .num_samples(0)
                .buffer(memObject)
                .mem_object(memObject);
    }

    private CLImageDesc initMipmappedImage2DDesc(CLImageDesc desc, int width, int height, int mipLevels) {
        return desc.image_type(CL12.CL_MEM_OBJECT_IMAGE2D)
                .image_width(width)
                .image_height(height)
                .image_depth(1)
                .image_array_size(1)
                .image_row_pitch(0L)
                .image_slice_pitch(0L)
                .num_mip_levels(mipLevels)
                .num_samples(0)
                .buffer(0L)
                .mem_object(0L);
    }

    private void uploadFloatMipmappedImage(long handle, int width, int height, int mipLevels, int componentCount, float[] values) {
        int offset = 0;
        for (int mipLevel = 0; mipLevel < mipLevels; mipLevel++) {
            int mipWidth = mipExtent(width, mipLevel);
            int mipHeight = mipExtent(height, mipLevel);
            int length = mipWidth * mipHeight * componentCount;
            float[] mipValues = java.util.Arrays.copyOfRange(values, offset, offset + length);
            writeFloatMipLevel(handle, mipWidth, mipHeight, mipLevel, mipValues);
            offset += length;
        }
    }

    private void uploadIntMipmappedImage(long handle, int width, int height, int mipLevels, int componentCount, int[] values) {
        int offset = 0;
        for (int mipLevel = 0; mipLevel < mipLevels; mipLevel++) {
            int mipWidth = mipExtent(width, mipLevel);
            int mipHeight = mipExtent(height, mipLevel);
            int length = mipWidth * mipHeight * componentCount;
            int[] mipValues = java.util.Arrays.copyOfRange(values, offset, offset + length);
            writeIntMipLevel(handle, mipWidth, mipHeight, mipLevel, mipValues);
            offset += length;
        }
    }

    private void writeFloatMipLevel(long handle, int width, int height, int mipLevel, float[] values) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer origin = stack.mallocPointer(3);
            origin.put(0, 0L).put(1, 0L).put(2, mipLevel);
            PointerBuffer region = stack.mallocPointer(3);
            region.put(0, width).put(1, height).put(2, 1L);
            OpenClException.check(
                    CL10.clEnqueueWriteImage(queue.handle(), handle, true, origin, region, 0L, 0L, values, null, null),
                    "clEnqueueWriteImage"
            );
            queue.finish();
        }
    }

    private void writeIntMipLevel(long handle, int width, int height, int mipLevel, int[] values) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer origin = stack.mallocPointer(3);
            origin.put(0, 0L).put(1, 0L).put(2, mipLevel);
            PointerBuffer region = stack.mallocPointer(3);
            region.put(0, width).put(1, height).put(2, 1L);
            OpenClException.check(
                    CL10.clEnqueueWriteImage(queue.handle(), handle, true, origin, region, 0L, 0L, values, null, null),
                    "clEnqueueWriteImage"
            );
            queue.finish();
        }
    }

    private long createBackingBuffer(float[] values) {
        int[] errorCode = new int[1];
        long handle = CL10.clCreateBuffer(
                context.handle(),
                CL10.CL_MEM_READ_WRITE | CL10.CL_MEM_COPY_HOST_PTR,
                values,
                errorCode
        );
        OpenClException.check(errorCode[0], "clCreateBuffer");
        return handle;
    }

    private long createBackingBuffer(int[] values) {
        int[] errorCode = new int[1];
        long handle = CL10.clCreateBuffer(
                context.handle(),
                CL10.CL_MEM_READ_WRITE | CL10.CL_MEM_COPY_HOST_PTR,
                values,
                errorCode
        );
        OpenClException.check(errorCode[0], "clCreateBuffer");
        return handle;
    }

    private long createBackingBuffer(long sizeBytes) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer errorCode = stack.mallocInt(1);
            long handle = CL10.clCreateBuffer(
                    context.handle(),
                    CL10.CL_MEM_READ_WRITE,
                    sizeBytes,
                    errorCode
            );
            OpenClException.check(errorCode.get(0), "clCreateBuffer");
            return handle;
        }
    }

    private void validateImageDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("OpenCL image dimensions must be positive: " + width + "x" + height);
        }
    }

    private void validateImageDimensions(int width) {
        if (width <= 0) {
            throw new IllegalArgumentException("OpenCL image dimensions must be positive: " + width);
        }
    }

    private void validateImageDimensions(int width, int height, int depth) {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("OpenCL image dimensions must be positive: " + width + "x" + height + "x" + depth);
        }
    }

    private void validateMipmappedImageDimensions(int width, int height, int mipLevels) {
        validateImageDimensions(width, height);
        if (mipLevels <= 0) {
            throw new IllegalArgumentException("OpenCL mip level count must be positive: " + mipLevels);
        }
        int maxMipLevels = 1 + Math.max(
                Integer.numberOfTrailingZeros(Integer.highestOneBit(width)),
                Integer.numberOfTrailingZeros(Integer.highestOneBit(height))
        );
        if (mipLevels > maxMipLevels) {
            throw new IllegalArgumentException(
                    "OpenCL mip level count " + mipLevels + " exceeds maximum " + maxMipLevels + " for base image " + width + "x" + height
            );
        }
    }

    private void validatePixelArrayLength(int width, int height, int componentCount, int actualLength, String componentType) {
        int expectedLength = width * height * componentCount;
        if (actualLength != expectedLength) {
            throw new IllegalArgumentException(
                    "Expected " + componentType + " image payload length " + expectedLength + " but got " + actualLength
            );
        }
    }

    private void validateMipmappedPixelArrayLength(int width, int height, int mipLevels, int componentCount, int actualLength, String componentType) {
        int expectedLength = 0;
        for (int mipLevel = 0; mipLevel < mipLevels; mipLevel++) {
            expectedLength += mipExtent(width, mipLevel) * mipExtent(height, mipLevel) * componentCount;
        }
        if (actualLength != expectedLength) {
            throw new IllegalArgumentException(
                    "Expected " + componentType + " payload length " + expectedLength + " but got " + actualLength
            );
        }
    }

    private void validateMipmappedImageHandle(long handle, int width, int height, int mipLevels, int mipLevel) {
        validateMipmappedImageDimensions(width, height, mipLevels);
        if (handle == 0L) {
            throw new IllegalArgumentException("OpenCL image handle must be non-zero for mipmapped image readback");
        }
        if (mipLevel < 0 || mipLevel >= mipLevels) {
            throw new IllegalArgumentException("Requested mip level " + mipLevel + " is out of range for image with " + mipLevels + " mip levels");
        }
    }

    private int mipExtent(int baseExtent, int mipLevel) {
        return Math.max(1, baseExtent >> mipLevel);
    }

    private void validatePixelArrayLength(int width, int componentCount, int actualLength, String componentType) {
        int expectedLength = width * componentCount;
        if (actualLength != expectedLength) {
            throw new IllegalArgumentException(
                    "Expected " + componentType + " image payload length " + expectedLength + " but got " + actualLength
            );
        }
    }

    private void validate1DArrayPixelArrayLength(int width, int layers, int componentCount, int actualLength, String componentType) {
        int expectedLength = width * layers * componentCount;
        if (actualLength != expectedLength) {
            throw new IllegalArgumentException(
                    "Expected " + componentType + " image payload length " + expectedLength + " but got " + actualLength
            );
        }
    }

    private void validatePixelArrayLength(int width, int height, int depth, int componentCount, int actualLength, String componentType) {
        int expectedLength = width * height * depth * componentCount;
        if (actualLength != expectedLength) {
            throw new IllegalArgumentException(
                    "Expected " + componentType + " image payload length " + expectedLength + " but got " + actualLength
            );
        }
    }

    private void validateImageHandle(long handle, int width, int height) {
        validateImageDimensions(width, height);
        if (handle == 0L) {
            throw new IllegalArgumentException("OpenCL image handle must be non-zero for readback");
        }
    }

    private void validateImageHandle(long handle, int width) {
        validateImageDimensions(width);
        if (handle == 0L) {
            throw new IllegalArgumentException("OpenCL image handle must be non-zero for readback");
        }
    }

    private void validate1DArrayImageHandle(long handle, int width, int layers) {
        validateImageDimensions(width);
        validateImageDimensions(layers);
        if (handle == 0L) {
            throw new IllegalArgumentException("OpenCL image handle must be non-zero for readback");
        }
    }

    private void validateImageHandle(long handle, int width, int height, int depth) {
        validateImageDimensions(width, height, depth);
        if (handle == 0L) {
            throw new IllegalArgumentException("OpenCL image handle must be non-zero for readback");
        }
    }

    private void validate2DArrayImageHandle(long handle, int width, int height, int layers) {
        validateImageDimensions(width, height);
        validateImageDimensions(layers);
        if (handle == 0L) {
            throw new IllegalArgumentException("OpenCL image handle must be non-zero for readback");
        }
    }

    private void validate2DArrayPixelArrayLength(int width, int height, int layers, int componentCount, int actualLength, String componentType) {
        int expectedLength = width * height * layers * componentCount;
        if (actualLength != expectedLength) {
            throw new IllegalArgumentException(
                    "Expected " + componentType + " image payload length " + expectedLength + " but got " + actualLength
            );
        }
    }
}
