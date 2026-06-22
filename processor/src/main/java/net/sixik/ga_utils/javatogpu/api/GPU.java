package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;
import org.lwjgl.opencl.CL10;

/**
 * Java facade for GPU built-ins available inside {@code @GPU} kernels.
 *
 * <p>The methods in this class intentionally look like ordinary Java methods so user code remains valid Java, but the
 * annotation processor maps them directly to backend intrinsics such as {@code get_global_id}, {@code sin} or
 * {@code barrier}.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @net.sixik.ga_utils.javatogpu.api.anotations.GPU
 * static void kernel(
 *         @net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal float[] input,
 *         @net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal float[] output
 * ) {
 *     int id = GPU.get_global_id(0);
 *     output[id] = GPU.sin(input[id]) + GPU.cos(input[id]);
 * }
 * }</pre>
 *
 * <p>Outside translated GPU code these methods behave like light Java stubs or JVM fallbacks. Their real purpose is to
 * provide a stable source-level API for code generation.
 */
public final class GPU {

    /**
     * OpenCL flag for synchronizing accesses to {@code __local} memory.
     */
    public static final int CLK_LOCAL_MEM_FENCE = 1;

    /**
     * OpenCL flag for synchronizing accesses to {@code __global} memory.
     */
    public static final int CLK_GLOBAL_MEM_FENCE = 2;

    /**
     * OpenCL image channel order constant for {@code CL_R}.
     */
    public static final int CL_R = CL10.CL_R;

    /**
     * OpenCL image channel order constant for {@code CL_RG}.
     */
    public static final int CL_RG = CL10.CL_RG;

    /**
     * OpenCL image channel order constant for {@code CL_RGBA}.
     */
    public static final int CL_RGBA = CL10.CL_RGBA;

    /**
     * OpenCL image channel data type constant for {@code CL_FLOAT}.
     */
    public static final int CL_FLOAT = CL10.CL_FLOAT;

    /**
     * OpenCL image channel data type constant for {@code CL_SIGNED_INT32}.
     */
    public static final int CL_SIGNED_INT32 = CL10.CL_SIGNED_INT32;

    /**
     * OpenCL image channel data type constant for {@code CL_UNSIGNED_INT32}.
     */
    public static final int CL_UNSIGNED_INT32 = CL10.CL_UNSIGNED_INT32;

    /**
     * OpenCL image channel data type constant for {@code CL_UNORM_INT8}.
     */
    public static final int CL_UNORM_INT8 = CL10.CL_UNORM_INT8;

    private static final double LOG_2 = Math.log(2.0);

    private GPU() {
    }

    @GPUIntrinsic(name = "get_work_dim")
    public static int get_work_dim() {
        return 1;
    }

    @GPUIntrinsic(name = "get_global_size")
    public static int get_global_size(int dimension) {
        return 1;
    }

    @GPUIntrinsic(name = "get_global_offset")
    public static int get_global_offset(int dimension) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_width")
    public static int get_image_width(Image1DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_width")
    public static int get_image_width(Image1DWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_width")
    public static int get_image_width(Image1DArrayReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_width")
    public static int get_image_width(Image1DArrayWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_width")
    public static int get_image_width(Image1DBufferReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_width")
    public static int get_image_width(Image1DBufferWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_width")
    public static int get_image_width(Image2DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_width")
    public static int get_image_width(Image2DWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_width")
    public static int get_image_width(Image2DArrayReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_width")
    public static int get_image_width(Image2DArrayWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_width")
    public static int get_image_width(Image3DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_width")
    public static int get_image_width(Image3DWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_height")
    public static int get_image_height(Image2DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_height")
    public static int get_image_height(Image2DWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_height")
    public static int get_image_height(Image3DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_height")
    public static int get_image_height(Image3DWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_height")
    public static int get_image_height(Image2DArrayReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_height")
    public static int get_image_height(Image2DArrayWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_depth")
    public static int get_image_depth(Image3DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_depth")
    public static int get_image_depth(Image3DWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_array_size")
    public static int get_image_array_size(Image1DArrayReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_array_size")
    public static int get_image_array_size(Image1DArrayWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_array_size")
    public static int get_image_array_size(Image2DArrayReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_array_size")
    public static int get_image_array_size(Image2DArrayWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_order")
    public static int get_image_channel_order(Image1DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_order")
    public static int get_image_channel_order(Image1DWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_order")
    public static int get_image_channel_order(Image1DArrayReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_order")
    public static int get_image_channel_order(Image1DArrayWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_order")
    public static int get_image_channel_order(Image1DBufferReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_order")
    public static int get_image_channel_order(Image1DBufferWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_order")
    public static int get_image_channel_order(Image2DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_order")
    public static int get_image_channel_order(Image2DWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_order")
    public static int get_image_channel_order(Image2DArrayReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_order")
    public static int get_image_channel_order(Image2DArrayWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_order")
    public static int get_image_channel_order(Image3DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_order")
    public static int get_image_channel_order(Image3DWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_data_type")
    public static int get_image_channel_data_type(Image1DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_data_type")
    public static int get_image_channel_data_type(Image1DWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_data_type")
    public static int get_image_channel_data_type(Image1DArrayReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_data_type")
    public static int get_image_channel_data_type(Image1DArrayWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_data_type")
    public static int get_image_channel_data_type(Image1DBufferReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_data_type")
    public static int get_image_channel_data_type(Image1DBufferWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_data_type")
    public static int get_image_channel_data_type(Image2DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_data_type")
    public static int get_image_channel_data_type(Image2DWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_data_type")
    public static int get_image_channel_data_type(Image2DArrayReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_data_type")
    public static int get_image_channel_data_type(Image2DArrayWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_data_type")
    public static int get_image_channel_data_type(Image3DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_data_type")
    public static int get_image_channel_data_type(Image3DWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "read_imagef")
    public static Float4 read_imagef(Image1DReadOnly image, Sampler sampler, int coordinate) {
        return new Float4();
    }

    @GPUIntrinsic(name = "read_imagef")
    public static Float4 read_imagef(Image1DReadOnly image, int coordinate) {
        return new Float4();
    }

    @GPUIntrinsic(name = "read_imagef")
    public static Float4 read_imagef(Image1DArrayReadOnly image, Sampler sampler, Int2 coordinates) {
        return new Float4();
    }

    @GPUIntrinsic(name = "read_imagef")
    public static Float4 read_imagef(Image1DArrayReadOnly image, Int2 coordinates) {
        return new Float4();
    }

    @GPUIntrinsic(name = "read_imagef")
    public static Float4 read_imagef(Image1DBufferReadOnly image, int coordinate) {
        return new Float4();
    }

    @GPUIntrinsic(name = "read_imagef")
    public static Float4 read_imagef(Image2DReadOnly image, Sampler sampler, Int2 coordinates) {
        return new Float4();
    }

    @GPUIntrinsic(name = "read_imagef")
    public static Float4 read_imagef(Image2DReadOnly image, Int2 coordinates) {
        return new Float4();
    }

    @GPUIntrinsic(name = "read_imagef")
    public static Float4 read_imagef(Image2DArrayReadOnly image, Sampler sampler, Int4 coordinates) {
        return new Float4();
    }

    @GPUIntrinsic(name = "read_imagef")
    public static Float4 read_imagef(Image2DArrayReadOnly image, Int4 coordinates) {
        return new Float4();
    }

    @GPUIntrinsic(name = "read_imagef")
    public static Float4 read_imagef(Image3DReadOnly image, Sampler sampler, Int4 coordinates) {
        return new Float4();
    }

    @GPUIntrinsic(name = "read_imagef")
    public static Float4 read_imagef(Image3DReadOnly image, Int4 coordinates) {
        return new Float4();
    }

    @GPUIntrinsic(name = "read_imagei")
    public static Int4 read_imagei(Image1DReadOnly image, Sampler sampler, int coordinate) {
        return new Int4();
    }

    @GPUIntrinsic(name = "read_imagei")
    public static Int4 read_imagei(Image1DReadOnly image, int coordinate) {
        return new Int4();
    }

    @GPUIntrinsic(name = "read_imagei")
    public static Int4 read_imagei(Image1DArrayReadOnly image, Sampler sampler, Int2 coordinates) {
        return new Int4();
    }

    @GPUIntrinsic(name = "read_imagei")
    public static Int4 read_imagei(Image1DArrayReadOnly image, Int2 coordinates) {
        return new Int4();
    }

    @GPUIntrinsic(name = "read_imagei")
    public static Int4 read_imagei(Image1DBufferReadOnly image, int coordinate) {
        return new Int4();
    }

    @GPUIntrinsic(name = "read_imagei")
    public static Int4 read_imagei(Image2DReadOnly image, Sampler sampler, Int2 coordinates) {
        return new Int4();
    }

    @GPUIntrinsic(name = "read_imagei")
    public static Int4 read_imagei(Image2DReadOnly image, Int2 coordinates) {
        return new Int4();
    }

    @GPUIntrinsic(name = "read_imagei")
    public static Int4 read_imagei(Image2DArrayReadOnly image, Sampler sampler, Int4 coordinates) {
        return new Int4();
    }

    @GPUIntrinsic(name = "read_imagei")
    public static Int4 read_imagei(Image2DArrayReadOnly image, Int4 coordinates) {
        return new Int4();
    }

    @GPUIntrinsic(name = "read_imagei")
    public static Int4 read_imagei(Image3DReadOnly image, Sampler sampler, Int4 coordinates) {
        return new Int4();
    }

    @GPUIntrinsic(name = "read_imagei")
    public static Int4 read_imagei(Image3DReadOnly image, Int4 coordinates) {
        return new Int4();
    }

    @GPUIntrinsic(name = "read_imageui")
    public static UInt4 read_imageui(Image1DReadOnly image, Sampler sampler, int coordinate) {
        return new UInt4();
    }

    @GPUIntrinsic(name = "read_imageui")
    public static UInt4 read_imageui(Image1DReadOnly image, int coordinate) {
        return new UInt4();
    }

    @GPUIntrinsic(name = "read_imageui")
    public static UInt4 read_imageui(Image1DArrayReadOnly image, Sampler sampler, Int2 coordinates) {
        return new UInt4();
    }

    @GPUIntrinsic(name = "read_imageui")
    public static UInt4 read_imageui(Image1DArrayReadOnly image, Int2 coordinates) {
        return new UInt4();
    }

    @GPUIntrinsic(name = "read_imageui")
    public static UInt4 read_imageui(Image1DBufferReadOnly image, int coordinate) {
        return new UInt4();
    }

    @GPUIntrinsic(name = "read_imageui")
    public static UInt4 read_imageui(Image2DReadOnly image, Sampler sampler, Int2 coordinates) {
        return new UInt4();
    }

    @GPUIntrinsic(name = "read_imageui")
    public static UInt4 read_imageui(Image2DReadOnly image, Int2 coordinates) {
        return new UInt4();
    }

    @GPUIntrinsic(name = "read_imageui")
    public static UInt4 read_imageui(Image2DArrayReadOnly image, Sampler sampler, Int4 coordinates) {
        return new UInt4();
    }

    @GPUIntrinsic(name = "read_imageui")
    public static UInt4 read_imageui(Image2DArrayReadOnly image, Int4 coordinates) {
        return new UInt4();
    }

    @GPUIntrinsic(name = "read_imageui")
    public static UInt4 read_imageui(Image3DReadOnly image, Sampler sampler, Int4 coordinates) {
        return new UInt4();
    }

    @GPUIntrinsic(name = "read_imageui")
    public static UInt4 read_imageui(Image3DReadOnly image, Int4 coordinates) {
        return new UInt4();
    }

    @GPUIntrinsic(code = "write_imagef({0}, {1}, {2})")
    public static void write_imagef(Image1DWriteOnly image, int coordinate, Float4 value) {
    }

    @GPUIntrinsic(code = "write_imagef({0}, {1}, {2})")
    public static void write_imagef(Image1DArrayWriteOnly image, Int2 coordinates, Float4 value) {
    }

    @GPUIntrinsic(code = "write_imagef({0}, {1}, {2})")
    public static void write_imagef(Image1DBufferWriteOnly image, int coordinate, Float4 value) {
    }

    @GPUIntrinsic(code = "write_imagef({0}, {1}, {2})")
    public static void write_imagef(Image2DWriteOnly image, Int2 coordinates, Float4 value) {
    }

    @GPUIntrinsic(code = "write_imagef({0}, {1}, {2})")
    public static void write_imagef(Image2DArrayWriteOnly image, Int4 coordinates, Float4 value) {
    }

    @GPUIntrinsic(code = "write_imagef({0}, {1}, {2})")
    public static void write_imagef(Image3DWriteOnly image, Int4 coordinates, Float4 value) {
    }

    @GPUIntrinsic(code = "write_imagei({0}, {1}, {2})")
    public static void write_imagei(Image1DWriteOnly image, int coordinate, Int4 value) {
    }

    @GPUIntrinsic(code = "write_imagei({0}, {1}, {2})")
    public static void write_imagei(Image1DArrayWriteOnly image, Int2 coordinates, Int4 value) {
    }

    @GPUIntrinsic(code = "write_imagei({0}, {1}, {2})")
    public static void write_imagei(Image1DBufferWriteOnly image, int coordinate, Int4 value) {
    }

    @GPUIntrinsic(code = "write_imagei({0}, {1}, {2})")
    public static void write_imagei(Image2DWriteOnly image, Int2 coordinates, Int4 value) {
    }

    @GPUIntrinsic(code = "write_imagei({0}, {1}, {2})")
    public static void write_imagei(Image2DArrayWriteOnly image, Int4 coordinates, Int4 value) {
    }

    @GPUIntrinsic(code = "write_imagei({0}, {1}, {2})")
    public static void write_imagei(Image3DWriteOnly image, Int4 coordinates, Int4 value) {
    }

    @GPUIntrinsic(code = "write_imageui({0}, {1}, {2})")
    public static void write_imageui(Image1DWriteOnly image, int coordinate, UInt4 value) {
    }

    @GPUIntrinsic(code = "write_imageui({0}, {1}, {2})")
    public static void write_imageui(Image1DArrayWriteOnly image, Int2 coordinates, UInt4 value) {
    }

    @GPUIntrinsic(code = "write_imageui({0}, {1}, {2})")
    public static void write_imageui(Image1DBufferWriteOnly image, int coordinate, UInt4 value) {
    }

    @GPUIntrinsic(code = "write_imageui({0}, {1}, {2})")
    public static void write_imageui(Image2DWriteOnly image, Int2 coordinates, UInt4 value) {
    }

    @GPUIntrinsic(code = "write_imageui({0}, {1}, {2})")
    public static void write_imageui(Image2DArrayWriteOnly image, Int4 coordinates, UInt4 value) {
    }

    @GPUIntrinsic(code = "write_imageui({0}, {1}, {2})")
    public static void write_imageui(Image3DWriteOnly image, Int4 coordinates, UInt4 value) {
    }

    @GPUIntrinsic(name = "sin")
    public static float sin(float value) {
        return (float) Math.sin(value);
    }

    @GPUIntrinsic(name = "sin")
    public static double sin(double value) {
        return Math.sin(value);
    }

    @GPUIntrinsic(name = "cos")
    public static float cos(float value) {
        return (float) Math.cos(value);
    }

    @GPUIntrinsic(name = "cos")
    public static double cos(double value) {
        return Math.cos(value);
    }

    @GPUIntrinsic(name = "tan")
    public static float tan(float value) {
        return (float) Math.tan(value);
    }

    @GPUIntrinsic(name = "tan")
    public static double tan(double value) {
        return Math.tan(value);
    }

    @GPUIntrinsic(name = "asin")
    public static float asin(float value) {
        return (float) Math.asin(value);
    }

    @GPUIntrinsic(name = "asin")
    public static double asin(double value) {
        return Math.asin(value);
    }

    @GPUIntrinsic(name = "acos")
    public static float acos(float value) {
        return (float) Math.acos(value);
    }

    @GPUIntrinsic(name = "acos")
    public static double acos(double value) {
        return Math.acos(value);
    }

    @GPUIntrinsic(name = "atan")
    public static float atan(float value) {
        return (float) Math.atan(value);
    }

    @GPUIntrinsic(name = "atan")
    public static double atan(double value) {
        return Math.atan(value);
    }

    @GPUIntrinsic(name = "atan2")
    public static float atan2(float y, float x) {
        return (float) Math.atan2(y, x);
    }

    @GPUIntrinsic(name = "atan2")
    public static double atan2(double y, double x) {
        return Math.atan2(y, x);
    }

    @GPUIntrinsic(name = "sqrt")
    public static float sqrt(float value) {
        return (float) Math.sqrt(value);
    }

    @GPUIntrinsic(name = "sqrt")
    public static double sqrt(double value) {
        return Math.sqrt(value);
    }

    @GPUIntrinsic(name = "exp")
    public static float exp(float value) {
        return (float) Math.exp(value);
    }

    @GPUIntrinsic(name = "exp")
    public static double exp(double value) {
        return Math.exp(value);
    }

    @GPUIntrinsic(name = "log")
    public static float log(float value) {
        return (float) Math.log(value);
    }

    @GPUIntrinsic(name = "log")
    public static double log(double value) {
        return Math.log(value);
    }

    @GPUIntrinsic(name = "log2")
    public static float log2(float value) {
        return (float) (Math.log(value) / LOG_2);
    }

    @GPUIntrinsic(name = "log2")
    public static double log2(double value) {
        return Math.log(value) / LOG_2;
    }

    @GPUIntrinsic(name = "fabs")
    public static float fabs(float value) {
        return Math.abs(value);
    }

    @GPUIntrinsic(name = "fabs")
    public static double fabs(double value) {
        return Math.abs(value);
    }

    @GPUIntrinsic(name = "fabs")
    public static float abs(float value) {
        return Math.abs(value);
    }

    @GPUIntrinsic(name = "fabs")
    public static double abs(double value) {
        return Math.abs(value);
    }

    @GPUIntrinsic(code = "((({0}) < 0) ? -({0}) : ({0}))")
    public static int abs(int value) {
        return Math.abs(value);
    }

    @GPUIntrinsic(code = "((({0}) < 0L) ? -({0}) : ({0}))")
    public static long abs(long value) {
        return Math.abs(value);
    }

    @GPUIntrinsic(name = "floor")
    public static float floor(float value) {
        return (float) Math.floor(value);
    }

    @GPUIntrinsic(name = "floor")
    public static double floor(double value) {
        return Math.floor(value);
    }

    @GPUIntrinsic(name = "ceil")
    public static float ceil(float value) {
        return (float) Math.ceil(value);
    }

    @GPUIntrinsic(name = "ceil")
    public static double ceil(double value) {
        return Math.ceil(value);
    }

    @GPUIntrinsic(name = "trunc")
    public static float trunc(float value) {
        return truncFloat(value);
    }

    @GPUIntrinsic(name = "trunc")
    public static double trunc(double value) {
        return truncDouble(value);
    }

    @GPUIntrinsic(name = "round")
    public static float round(float value) {
        return roundFloat(value);
    }

    @GPUIntrinsic(name = "round")
    public static double round(double value) {
        return roundDouble(value);
    }

    @GPUIntrinsic(name = "pow")
    public static float pow(float left, float right) {
        return (float) Math.pow(left, right);
    }

    @GPUIntrinsic(name = "pow")
    public static double pow(double left, double right) {
        return Math.pow(left, right);
    }

    @GPUIntrinsic(name = "min")
    public static float min(float left, float right) {
        return Math.min(left, right);
    }

    @GPUIntrinsic(name = "min")
    public static double min(double left, double right) {
        return Math.min(left, right);
    }

    @GPUIntrinsic(name = "min")
    public static int min(int left, int right) {
        return Math.min(left, right);
    }

    @GPUIntrinsic(name = "min")
    public static byte min(byte left, byte right) {
        return (byte) Math.min(left, right);
    }

    @GPUIntrinsic(name = "min")
    public static short min(short left, short right) {
        return (short) Math.min(left, right);
    }

    @GPUIntrinsic(name = "min")
    public static char min(char left, char right) {
        return (char) Math.min(left, right);
    }

    @GPUIntrinsic(name = "min")
    public static long min(long left, long right) {
        return Math.min(left, right);
    }

    @GPUIntrinsic(name = "min")
    public static UByte min(UByte left, UByte right) {
        return new UByte(minUnsignedByte(left.value, right.value));
    }

    @GPUIntrinsic(name = "min")
    public static UShort min(UShort left, UShort right) {
        return new UShort(minUnsignedShort(left.value, right.value));
    }

    @GPUIntrinsic(name = "min")
    public static UInt min(UInt left, UInt right) {
        return new UInt(minUnsignedInt(left.value, right.value));
    }

    @GPUIntrinsic(name = "min")
    public static ULong min(ULong left, ULong right) {
        return new ULong(minUnsignedLong(left.value, right.value));
    }

    @GPUIntrinsic(name = "max")
    public static float max(float left, float right) {
        return Math.max(left, right);
    }

    @GPUIntrinsic(name = "max")
    public static double max(double left, double right) {
        return Math.max(left, right);
    }

    @GPUIntrinsic(name = "max")
    public static int max(int left, int right) {
        return Math.max(left, right);
    }

    @GPUIntrinsic(name = "max")
    public static byte max(byte left, byte right) {
        return (byte) Math.max(left, right);
    }

    @GPUIntrinsic(name = "max")
    public static short max(short left, short right) {
        return (short) Math.max(left, right);
    }

    @GPUIntrinsic(name = "max")
    public static char max(char left, char right) {
        return (char) Math.max(left, right);
    }

    @GPUIntrinsic(name = "max")
    public static long max(long left, long right) {
        return Math.max(left, right);
    }

    @GPUIntrinsic(name = "max")
    public static UByte max(UByte left, UByte right) {
        return new UByte(maxUnsignedByte(left.value, right.value));
    }

    @GPUIntrinsic(name = "max")
    public static UShort max(UShort left, UShort right) {
        return new UShort(maxUnsignedShort(left.value, right.value));
    }

    @GPUIntrinsic(name = "max")
    public static UInt max(UInt left, UInt right) {
        return new UInt(maxUnsignedInt(left.value, right.value));
    }

    @GPUIntrinsic(name = "max")
    public static ULong max(ULong left, ULong right) {
        return new ULong(maxUnsignedLong(left.value, right.value));
    }

    @GPUIntrinsic(name = "rsqrt")
    public static float rsqrt(float value) {
        return 1.0f / (float) Math.sqrt(value);
    }

    @GPUIntrinsic(name = "rsqrt")
    public static double rsqrt(double value) {
        return 1.0 / Math.sqrt(value);
    }

    @GPUIntrinsic(name = "fmin")
    public static float fmin(float left, float right) {
        return Math.min(left, right);
    }

    @GPUIntrinsic(name = "fmin")
    public static double fmin(double left, double right) {
        return Math.min(left, right);
    }

    @GPUIntrinsic(name = "fmax")
    public static float fmax(float left, float right) {
        return Math.max(left, right);
    }

    @GPUIntrinsic(name = "fmax")
    public static double fmax(double left, double right) {
        return Math.max(left, right);
    }

    @GPUIntrinsic(name = "mad")
    public static float mad(float a, float b, float c) {
        return a * b + c;
    }

    @GPUIntrinsic(name = "mad")
    public static double mad(double a, double b, double c) {
        return a * b + c;
    }

    @GPUIntrinsic(name = "clamp")
    public static float clamp(float value, float minValue, float maxValue) {
        return Math.max(minValue, Math.min(value, maxValue));
    }

    @GPUIntrinsic(name = "clamp")
    public static double clamp(double value, double minValue, double maxValue) {
        return Math.max(minValue, Math.min(value, maxValue));
    }

    @GPUIntrinsic(name = "clamp")
    public static int clamp(int value, int minValue, int maxValue) {
        return Math.max(minValue, Math.min(value, maxValue));
    }

    @GPUIntrinsic(name = "clamp")
    public static byte clamp(byte value, byte minValue, byte maxValue) {
        return max(minValue, min(value, maxValue));
    }

    @GPUIntrinsic(name = "clamp")
    public static short clamp(short value, short minValue, short maxValue) {
        return max(minValue, min(value, maxValue));
    }

    @GPUIntrinsic(name = "clamp")
    public static char clamp(char value, char minValue, char maxValue) {
        return max(minValue, min(value, maxValue));
    }

    @GPUIntrinsic(name = "clamp")
    public static long clamp(long value, long minValue, long maxValue) {
        return Math.max(minValue, Math.min(value, maxValue));
    }

    @GPUIntrinsic(name = "clamp")
    public static UByte clamp(UByte value, UByte minValue, UByte maxValue) {
        return new UByte(clampUnsignedByte(value.value, minValue.value, maxValue.value));
    }

    @GPUIntrinsic(name = "clamp")
    public static UShort clamp(UShort value, UShort minValue, UShort maxValue) {
        return new UShort(clampUnsignedShort(value.value, minValue.value, maxValue.value));
    }

    @GPUIntrinsic(name = "clamp")
    public static UInt clamp(UInt value, UInt minValue, UInt maxValue) {
        return new UInt(clampUnsignedInt(value.value, minValue.value, maxValue.value));
    }

    @GPUIntrinsic(name = "clamp")
    public static ULong clamp(ULong value, ULong minValue, ULong maxValue) {
        return new ULong(clampUnsignedLong(value.value, minValue.value, maxValue.value));
    }

    @GPUIntrinsic(name = "mix")
    public static float mix(float left, float right, float amount) {
        return left + (right - left) * amount;
    }

    @GPUIntrinsic(name = "mix")
    public static double mix(double left, double right, double amount) {
        return left + (right - left) * amount;
    }

    @GPUIntrinsic(name = "degrees")
    public static float degrees(float radians) {
        return (float) Math.toDegrees(radians);
    }

    @GPUIntrinsic(name = "degrees")
    public static double degrees(double radians) {
        return Math.toDegrees(radians);
    }

    @GPUIntrinsic(name = "radians")
    public static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    @GPUIntrinsic(name = "radians")
    public static double radians(double degrees) {
        return Math.toRadians(degrees);
    }

    @GPUIntrinsic(name = "copysign")
    public static float copysign(float magnitude, float sign) {
        return Math.copySign(magnitude, sign);
    }

    @GPUIntrinsic(name = "copysign")
    public static double copysign(double magnitude, double sign) {
        return Math.copySign(magnitude, sign);
    }

    @GPUIntrinsic(name = "step")
    public static float step(float edge, float value) {
        return value < edge ? 0.0f : 1.0f;
    }

    @GPUIntrinsic(name = "step")
    public static double step(double edge, double value) {
        return value < edge ? 0.0 : 1.0;
    }

    @GPUIntrinsic(name = "smoothstep")
    public static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    @GPUIntrinsic(name = "smoothstep")
    public static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    @GPUIntrinsic(name = "hypot")
    public static float length(float x, float y) {
        return (float) Math.hypot(x, y);
    }

    @GPUIntrinsic(name = "hypot")
    public static double length(double x, double y) {
        return Math.hypot(x, y);
    }

    @GPUIntrinsic(code = "(({0}) - floor({0}))")
    public static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    @GPUIntrinsic(code = "(({0}) - floor({0}))")
    public static double fract(double value) {
        return value - Math.floor(value);
    }

    @GPUIntrinsic(code = "((({0}) > 0.0f) ? 1.0f : ((({0}) < 0.0f) ? -1.0f : 0.0f))")
    public static float sign(float value) {
        return value > 0.0f ? 1.0f : (value < 0.0f ? -1.0f : 0.0f);
    }

    @GPUIntrinsic(code = "((({0}) > 0.0) ? 1.0 : ((({0}) < 0.0) ? -1.0 : 0.0))")
    public static double sign(double value) {
        return value > 0.0 ? 1.0 : (value < 0.0 ? -1.0 : 0.0);
    }

    @GPUIntrinsic(code = "((({0}) > 0) ? 1 : ((({0}) < 0) ? -1 : 0))")
    public static int sign(int value) {
        return Integer.compare(value, 0);
    }

    @GPUIntrinsic(code = "((({0}) > 0L) ? 1L : ((({0}) < 0L) ? -1L : 0L))")
    public static long sign(long value) {
        return Long.compare(value, 0L);
    }

    @GPUIntrinsic(name = "convert_int")
    public static int convert_int(float value) {
        return (int) value;
    }

    @GPUIntrinsic(name = "convert_int")
    public static int convert_int(double value) {
        return (int) value;
    }

    @GPUIntrinsic(name = "convert_int")
    public static int convert_int(long value) {
        return (int) value;
    }

    @GPUIntrinsic(name = "convert_long")
    public static long convert_long(float value) {
        return (long) value;
    }

    @GPUIntrinsic(name = "convert_long")
    public static long convert_long(double value) {
        return (long) value;
    }

    @GPUIntrinsic(name = "convert_long")
    public static long convert_long(int value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_float")
    public static float convert_float(int value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_float")
    public static float convert_float(long value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_float")
    public static float convert_float(double value) {
        return (float) value;
    }

    @GPUIntrinsic(name = "convert_double")
    public static double convert_double(int value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_double")
    public static double convert_double(long value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_double")
    public static double convert_double(float value) {
        return value;
    }

    @GPUIntrinsic(name = "as_int")
    public static int as_int(float value) {
        return Float.floatToRawIntBits(value);
    }

    @GPUIntrinsic(name = "as_float")
    public static float as_float(int value) {
        return Float.intBitsToFloat(value);
    }

    @GPUIntrinsic(name = "as_long")
    public static long as_long(double value) {
        return Double.doubleToRawLongBits(value);
    }

    @GPUIntrinsic(name = "as_double")
    public static double as_double(long value) {
        return Double.longBitsToDouble(value);
    }

    @GPUIntrinsic(name = "clz")
    public static int clz(int value) {
        return Integer.numberOfLeadingZeros(value);
    }

    @GPUIntrinsic(name = "popcount")
    public static int popcount(int value) {
        return Integer.bitCount(value);
    }

    @GPUIntrinsic(name = "rotate")
    public static int rotate(int value, int amount) {
        return Integer.rotateLeft(value, amount);
    }

    @GPUIntrinsic(name = "get_global_id")
    public static int get_global_id(int dimension) {
        return 0;
    }

    @GPUIntrinsic(name = "get_local_size")
    public static int get_local_size(int dimension) {
        return 1;
    }

    @GPUIntrinsic(name = "get_local_id")
    public static int get_local_id(int dimension) {
        return 0;
    }

    @GPUIntrinsic(name = "get_num_groups")
    public static int get_num_groups(int dimension) {
        return 1;
    }

    @GPUIntrinsic(name = "get_group_id")
    public static int get_group_id(int dimension) {
        return 0;
    }

    @GPUIntrinsic(name = "barrier")
    public static void barrier(int flags) {
    }

    @GPUIntrinsic(code = "barrier(1)")
    public static void local_barrier() {
    }

    @GPUIntrinsic(code = "barrier(2)")
    public static void global_barrier() {
    }

    @GPUIntrinsic(code = "barrier((1 | 2))")
    public static void all_barrier() {
    }

    @GPUIntrinsic(name = "mem_fence")
    public static void mem_fence(int flags) {
    }

    @GPUIntrinsic(code = "mem_fence(1)")
    public static void local_mem_fence() {
    }

    @GPUIntrinsic(code = "mem_fence(2)")
    public static void global_mem_fence() {
    }

    @GPUIntrinsic(code = "mem_fence((1 | 2))")
    public static void all_mem_fence() {
    }

    @GPUIntrinsic(code = "atomic_add(&(({0})[{1}]), {2})")
    public static int atomic_add(int[] values, int index, int value) {
        int previous = values[index];
        values[index] += value;
        return previous;
    }

    @GPUIntrinsic(code = "atomic_sub(&(({0})[{1}]), {2})")
    public static int atomic_sub(int[] values, int index, int value) {
        int previous = values[index];
        values[index] -= value;
        return previous;
    }

    @GPUIntrinsic(code = "atomic_xchg(&(({0})[{1}]), {2})")
    public static int atomic_xchg(int[] values, int index, int value) {
        int previous = values[index];
        values[index] = value;
        return previous;
    }

    @GPUIntrinsic(code = "atomic_inc(&(({0})[{1}]))")
    public static int atomic_inc(int[] values, int index) {
        return values[index]++;
    }

    @GPUIntrinsic(code = "atomic_dec(&(({0})[{1}]))")
    public static int atomic_dec(int[] values, int index) {
        return values[index]--;
    }

    @GPUIntrinsic(code = "atomic_cmpxchg(&(({0})[{1}]), {2}, {3})")
    public static int atomic_cmpxchg(int[] values, int index, int expected, int replacement) {
        int previous = values[index];
        if (previous == expected) {
            values[index] = replacement;
        }
        return previous;
    }

    @GPUIntrinsic(code = "atomic_min(&(({0})[{1}]), {2})")
    public static int atomic_min(int[] values, int index, int value) {
        int previous = values[index];
        values[index] = Math.min(previous, value);
        return previous;
    }

    @GPUIntrinsic(code = "atomic_max(&(({0})[{1}]), {2})")
    public static int atomic_max(int[] values, int index, int value) {
        int previous = values[index];
        values[index] = Math.max(previous, value);
        return previous;
    }

    @GPUIntrinsic(code = "atomic_and(&(({0})[{1}]), {2})")
    public static int atomic_and(int[] values, int index, int value) {
        int previous = values[index];
        values[index] &= value;
        return previous;
    }

    @GPUIntrinsic(code = "atomic_or(&(({0})[{1}]), {2})")
    public static int atomic_or(int[] values, int index, int value) {
        int previous = values[index];
        values[index] |= value;
        return previous;
    }

    @GPUIntrinsic(code = "atomic_xor(&(({0})[{1}]), {2})")
    public static int atomic_xor(int[] values, int index, int value) {
        int previous = values[index];
        values[index] ^= value;
        return previous;
    }

    private static float roundFloat(float value) {
        return value >= 0.0f
                ? (float) Math.floor(value + 0.5f)
                : (float) Math.ceil(value - 0.5f);
    }

    private static byte minUnsignedByte(byte left, byte right) {
        return Integer.compare(Byte.toUnsignedInt(left), Byte.toUnsignedInt(right)) <= 0 ? left : right;
    }

    private static short minUnsignedShort(short left, short right) {
        return Integer.compare(Short.toUnsignedInt(left), Short.toUnsignedInt(right)) <= 0 ? left : right;
    }

    private static int minUnsignedInt(int left, int right) {
        return Integer.compareUnsigned(left, right) <= 0 ? left : right;
    }

    private static long minUnsignedLong(long left, long right) {
        return Long.compareUnsigned(left, right) <= 0 ? left : right;
    }

    private static byte maxUnsignedByte(byte left, byte right) {
        return Integer.compare(Byte.toUnsignedInt(left), Byte.toUnsignedInt(right)) >= 0 ? left : right;
    }

    private static short maxUnsignedShort(short left, short right) {
        return Integer.compare(Short.toUnsignedInt(left), Short.toUnsignedInt(right)) >= 0 ? left : right;
    }

    private static int maxUnsignedInt(int left, int right) {
        return Integer.compareUnsigned(left, right) >= 0 ? left : right;
    }

    private static long maxUnsignedLong(long left, long right) {
        return Long.compareUnsigned(left, right) >= 0 ? left : right;
    }

    private static byte clampUnsignedByte(byte value, byte minValue, byte maxValue) {
        return maxUnsignedByte(minValue, minUnsignedByte(value, maxValue));
    }

    private static short clampUnsignedShort(short value, short minValue, short maxValue) {
        return maxUnsignedShort(minValue, minUnsignedShort(value, maxValue));
    }

    private static int clampUnsignedInt(int value, int minValue, int maxValue) {
        return maxUnsignedInt(minValue, minUnsignedInt(value, maxValue));
    }

    private static long clampUnsignedLong(long value, long minValue, long maxValue) {
        return maxUnsignedLong(minValue, minUnsignedLong(value, maxValue));
    }

    private static double roundDouble(double value) {
        return value >= 0.0
                ? Math.floor(value + 0.5)
                : Math.ceil(value - 0.5);
    }

    private static float truncFloat(float value) {
        return value >= 0.0f ? (float) Math.floor(value) : (float) Math.ceil(value);
    }

    private static double truncDouble(double value) {
        return value >= 0.0 ? Math.floor(value) : Math.ceil(value);
    }
}
