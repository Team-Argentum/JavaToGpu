package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
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
 * @net.sixik.ga_utils.javatogpu.api.annotations.GPU
 * static void kernel(
 *         @net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal float[] input,
 *         @net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal float[] output
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
     * OpenCL image channel order constant for {@code CL_DEPTH}.
     */
    public static final int CL_DEPTH = 0x10BD;

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
    private static final double UINT_MAX_AS_DOUBLE = 4294967295.0;
    private static final double ULONG_SIGN_THRESHOLD_AS_DOUBLE = 9223372036854775808.0;
    private static final double ULONG_MAX_AS_DOUBLE = 18446744073709551615.0;
    private static final double ULONG_WRAP_AS_DOUBLE = 18446744073709551616.0;

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

    @GPUIntrinsic(code = "({0})")
    public static GlobalBytePtr global(byte[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static GlobalCharPtr global(char[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static GlobalShortPtr global(short[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static GlobalIntPtr global(int[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static GlobalLongPtr global(long[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static GlobalFloatPtr global(float[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static GlobalDoublePtr global(double[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static ConstantBytePtr constant(byte[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static ConstantCharPtr constant(char[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static ConstantShortPtr constant(short[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static ConstantIntPtr constant(int[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static ConstantLongPtr constant(long[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static ConstantFloatPtr constant(float[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static ConstantDoublePtr constant(double[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static LocalBytePtr local(byte[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static LocalCharPtr local(char[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static LocalShortPtr local(short[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static LocalIntPtr local(int[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static LocalLongPtr local(long[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static LocalFloatPtr local(float[] values) {
        return null;
    }

    @GPUIntrinsic(code = "({0})")
    public static LocalDoublePtr local(double[] values) {
        return null;
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
    public static int get_image_width(Image2DMipmappedReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_width")
    public static int get_image_width(Image2DMipmappedWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_width")
    public static int get_image_width(Image2DMsaaReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_width")
    public static int get_image_width(Image2DMsaaWriteOnly image) {
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
    public static int get_image_height(Image2DMipmappedReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_height")
    public static int get_image_height(Image2DMipmappedWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_height")
    public static int get_image_height(Image2DMsaaReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_height")
    public static int get_image_height(Image2DMsaaWriteOnly image) {
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
    public static int get_image_channel_order(Image2DMipmappedReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_order")
    public static int get_image_channel_order(Image2DMipmappedWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_order")
    public static int get_image_channel_order(Image2DMsaaReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_order")
    public static int get_image_channel_order(Image2DMsaaWriteOnly image) {
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
    public static int get_image_channel_data_type(Image2DMipmappedReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_data_type")
    public static int get_image_channel_data_type(Image2DMipmappedWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_data_type")
    public static int get_image_channel_data_type(Image2DMsaaReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_channel_data_type")
    public static int get_image_channel_data_type(Image2DMsaaWriteOnly image) {
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

    @GPUIntrinsic(name = "get_image_num_mip_levels")
    public static int get_image_num_mip_levels(Image1DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_mip_levels")
    public static int get_image_num_mip_levels(Image1DWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_mip_levels")
    public static int get_image_num_mip_levels(Image1DArrayReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_mip_levels")
    public static int get_image_num_mip_levels(Image1DArrayWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_mip_levels")
    public static int get_image_num_mip_levels(Image1DBufferReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_mip_levels")
    public static int get_image_num_mip_levels(Image1DBufferWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_mip_levels")
    public static int get_image_num_mip_levels(Image2DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_mip_levels")
    public static int get_image_num_mip_levels(Image2DWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_mip_levels")
    public static int get_image_num_mip_levels(Image2DMipmappedReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_mip_levels")
    public static int get_image_num_mip_levels(Image2DMipmappedWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_mip_levels")
    public static int get_image_num_mip_levels(Image2DArrayReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_mip_levels")
    public static int get_image_num_mip_levels(Image2DArrayWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_mip_levels")
    public static int get_image_num_mip_levels(Image3DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_mip_levels")
    public static int get_image_num_mip_levels(Image3DWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_samples")
    public static int get_image_num_samples(Image1DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_samples")
    public static int get_image_num_samples(Image1DWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_samples")
    public static int get_image_num_samples(Image1DArrayReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_samples")
    public static int get_image_num_samples(Image1DArrayWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_samples")
    public static int get_image_num_samples(Image1DBufferReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_samples")
    public static int get_image_num_samples(Image1DBufferWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_samples")
    public static int get_image_num_samples(Image2DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_samples")
    public static int get_image_num_samples(Image2DWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_samples")
    public static int get_image_num_samples(Image2DMipmappedReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_samples")
    public static int get_image_num_samples(Image2DMipmappedWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_samples")
    public static int get_image_num_samples(Image2DMsaaReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_samples")
    public static int get_image_num_samples(Image2DMsaaWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_samples")
    public static int get_image_num_samples(Image2DArrayReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_samples")
    public static int get_image_num_samples(Image2DArrayWriteOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_samples")
    public static int get_image_num_samples(Image3DReadOnly image) {
        return 0;
    }

    @GPUIntrinsic(name = "get_image_num_samples")
    public static int get_image_num_samples(Image3DWriteOnly image) {
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
    public static Float4 read_imagef(Image2DMipmappedReadOnly image, Sampler sampler, Int2 coordinates) {
        return new Float4();
    }

    @GPUIntrinsic(name = "read_imagef")
    public static Float4 read_imagef(Image2DMipmappedReadOnly image, Int2 coordinates) {
        return new Float4();
    }

    @GPUIntrinsic(code = "read_imagef({0}, {1}, {2})")
    public static Float4 read_imagef(Image2DMsaaReadOnly image, Int2 coordinates, int sampleIndex) {
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
    public static Int4 read_imagei(Image2DMipmappedReadOnly image, Sampler sampler, Int2 coordinates) {
        return new Int4();
    }

    @GPUIntrinsic(name = "read_imagei")
    public static Int4 read_imagei(Image2DMipmappedReadOnly image, Int2 coordinates) {
        return new Int4();
    }

    @GPUIntrinsic(code = "read_imagei({0}, {1}, {2})")
    public static Int4 read_imagei(Image2DMsaaReadOnly image, Int2 coordinates, int sampleIndex) {
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
    public static UInt4 read_imageui(Image2DMipmappedReadOnly image, Sampler sampler, Int2 coordinates) {
        return new UInt4();
    }

    @GPUIntrinsic(name = "read_imageui")
    public static UInt4 read_imageui(Image2DMipmappedReadOnly image, Int2 coordinates) {
        return new UInt4();
    }

    @GPUIntrinsic(code = "read_imageui({0}, {1}, {2})")
    public static UInt4 read_imageui(Image2DMsaaReadOnly image, Int2 coordinates, int sampleIndex) {
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
    public static void write_imagef(Image2DMipmappedWriteOnly image, Int2 coordinates, Float4 value) {
    }

    @GPUIntrinsic(code = "write_imagef({0}, {1}, {2}, {3})")
    public static void write_imagef(Image2DMsaaWriteOnly image, Int2 coordinates, int sampleIndex, Float4 value) {
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
    public static void write_imagei(Image2DMipmappedWriteOnly image, Int2 coordinates, Int4 value) {
    }

    @GPUIntrinsic(code = "write_imagei({0}, {1}, {2}, {3})")
    public static void write_imagei(Image2DMsaaWriteOnly image, Int2 coordinates, int sampleIndex, Int4 value) {
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
    public static void write_imageui(Image2DMipmappedWriteOnly image, Int2 coordinates, UInt4 value) {
    }

    @GPUIntrinsic(code = "write_imageui({0}, {1}, {2}, {3})")
    public static void write_imageui(Image2DMsaaWriteOnly image, Int2 coordinates, int sampleIndex, UInt4 value) {
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

    @GPUIntrinsic(code = "sin({0})")
    public static Float2 sin(Float2 value) {
        return new Float2(sin(value.x), sin(value.y));
    }

    @GPUIntrinsic(code = "sin({0})")
    public static Float3 sin(Float3 value) {
        return new Float3(sin(value.x), sin(value.y), sin(value.z));
    }

    @GPUIntrinsic(code = "sin({0})")
    public static Float4 sin(Float4 value) {
        return new Float4(sin(value.x), sin(value.y), sin(value.z), sin(value.w));
    }

    @GPUIntrinsic(name = "sin")
    public static double sin(double value) {
        return Math.sin(value);
    }

    @GPUIntrinsic(code = "sin({0})")
    public static Double2 sin(Double2 value) {
        return new Double2(sin(value.x), sin(value.y));
    }

    @GPUIntrinsic(code = "sin({0})")
    public static Double3 sin(Double3 value) {
        return new Double3(sin(value.x), sin(value.y), sin(value.z));
    }

    @GPUIntrinsic(code = "sin({0})")
    public static Double4 sin(Double4 value) {
        return new Double4(sin(value.x), sin(value.y), sin(value.z), sin(value.w));
    }

    @GPUIntrinsic(name = "cos")
    public static float cos(float value) {
        return (float) Math.cos(value);
    }

    @GPUIntrinsic(code = "cos({0})")
    public static Float2 cos(Float2 value) {
        return new Float2(cos(value.x), cos(value.y));
    }

    @GPUIntrinsic(code = "cos({0})")
    public static Float3 cos(Float3 value) {
        return new Float3(cos(value.x), cos(value.y), cos(value.z));
    }

    @GPUIntrinsic(code = "cos({0})")
    public static Float4 cos(Float4 value) {
        return new Float4(cos(value.x), cos(value.y), cos(value.z), cos(value.w));
    }

    @GPUIntrinsic(name = "cos")
    public static double cos(double value) {
        return Math.cos(value);
    }

    @GPUIntrinsic(code = "cos({0})")
    public static Double2 cos(Double2 value) {
        return new Double2(cos(value.x), cos(value.y));
    }

    @GPUIntrinsic(code = "cos({0})")
    public static Double3 cos(Double3 value) {
        return new Double3(cos(value.x), cos(value.y), cos(value.z));
    }

    @GPUIntrinsic(code = "cos({0})")
    public static Double4 cos(Double4 value) {
        return new Double4(cos(value.x), cos(value.y), cos(value.z), cos(value.w));
    }

    @GPUIntrinsic(name = "tan")
    public static float tan(float value) {
        return (float) Math.tan(value);
    }

    @GPUIntrinsic(code = "tan({0})")
    public static Float2 tan(Float2 value) {
        return new Float2(tan(value.x), tan(value.y));
    }

    @GPUIntrinsic(code = "tan({0})")
    public static Float3 tan(Float3 value) {
        return new Float3(tan(value.x), tan(value.y), tan(value.z));
    }

    @GPUIntrinsic(code = "tan({0})")
    public static Float4 tan(Float4 value) {
        return new Float4(tan(value.x), tan(value.y), tan(value.z), tan(value.w));
    }

    @GPUIntrinsic(name = "tan")
    public static double tan(double value) {
        return Math.tan(value);
    }

    @GPUIntrinsic(code = "tan({0})")
    public static Double2 tan(Double2 value) {
        return new Double2(tan(value.x), tan(value.y));
    }

    @GPUIntrinsic(code = "tan({0})")
    public static Double3 tan(Double3 value) {
        return new Double3(tan(value.x), tan(value.y), tan(value.z));
    }

    @GPUIntrinsic(code = "tan({0})")
    public static Double4 tan(Double4 value) {
        return new Double4(tan(value.x), tan(value.y), tan(value.z), tan(value.w));
    }

    @GPUIntrinsic(name = "sinh")
    public static float sinh(float value) {
        return (float) Math.sinh(value);
    }

    @GPUIntrinsic(code = "sinh({0})")
    public static Float2 sinh(Float2 value) {
        return new Float2(sinh(value.x), sinh(value.y));
    }

    @GPUIntrinsic(code = "sinh({0})")
    public static Float3 sinh(Float3 value) {
        return new Float3(sinh(value.x), sinh(value.y), sinh(value.z));
    }

    @GPUIntrinsic(code = "sinh({0})")
    public static Float4 sinh(Float4 value) {
        return new Float4(sinh(value.x), sinh(value.y), sinh(value.z), sinh(value.w));
    }

    @GPUIntrinsic(name = "sinh")
    public static double sinh(double value) {
        return Math.sinh(value);
    }

    @GPUIntrinsic(code = "sinh({0})")
    public static Double2 sinh(Double2 value) {
        return new Double2(sinh(value.x), sinh(value.y));
    }

    @GPUIntrinsic(code = "sinh({0})")
    public static Double3 sinh(Double3 value) {
        return new Double3(sinh(value.x), sinh(value.y), sinh(value.z));
    }

    @GPUIntrinsic(code = "sinh({0})")
    public static Double4 sinh(Double4 value) {
        return new Double4(sinh(value.x), sinh(value.y), sinh(value.z), sinh(value.w));
    }

    @GPUIntrinsic(name = "cosh")
    public static float cosh(float value) {
        return (float) Math.cosh(value);
    }

    @GPUIntrinsic(code = "cosh({0})")
    public static Float2 cosh(Float2 value) {
        return new Float2(cosh(value.x), cosh(value.y));
    }

    @GPUIntrinsic(code = "cosh({0})")
    public static Float3 cosh(Float3 value) {
        return new Float3(cosh(value.x), cosh(value.y), cosh(value.z));
    }

    @GPUIntrinsic(code = "cosh({0})")
    public static Float4 cosh(Float4 value) {
        return new Float4(cosh(value.x), cosh(value.y), cosh(value.z), cosh(value.w));
    }

    @GPUIntrinsic(name = "cosh")
    public static double cosh(double value) {
        return Math.cosh(value);
    }

    @GPUIntrinsic(code = "cosh({0})")
    public static Double2 cosh(Double2 value) {
        return new Double2(cosh(value.x), cosh(value.y));
    }

    @GPUIntrinsic(code = "cosh({0})")
    public static Double3 cosh(Double3 value) {
        return new Double3(cosh(value.x), cosh(value.y), cosh(value.z));
    }

    @GPUIntrinsic(code = "cosh({0})")
    public static Double4 cosh(Double4 value) {
        return new Double4(cosh(value.x), cosh(value.y), cosh(value.z), cosh(value.w));
    }

    @GPUIntrinsic(name = "tanh")
    public static float tanh(float value) {
        return (float) Math.tanh(value);
    }

    @GPUIntrinsic(code = "tanh({0})")
    public static Float2 tanh(Float2 value) {
        return new Float2(tanh(value.x), tanh(value.y));
    }

    @GPUIntrinsic(code = "tanh({0})")
    public static Float3 tanh(Float3 value) {
        return new Float3(tanh(value.x), tanh(value.y), tanh(value.z));
    }

    @GPUIntrinsic(code = "tanh({0})")
    public static Float4 tanh(Float4 value) {
        return new Float4(tanh(value.x), tanh(value.y), tanh(value.z), tanh(value.w));
    }

    @GPUIntrinsic(name = "tanh")
    public static double tanh(double value) {
        return Math.tanh(value);
    }

    @GPUIntrinsic(code = "tanh({0})")
    public static Double2 tanh(Double2 value) {
        return new Double2(tanh(value.x), tanh(value.y));
    }

    @GPUIntrinsic(code = "tanh({0})")
    public static Double3 tanh(Double3 value) {
        return new Double3(tanh(value.x), tanh(value.y), tanh(value.z));
    }

    @GPUIntrinsic(code = "tanh({0})")
    public static Double4 tanh(Double4 value) {
        return new Double4(tanh(value.x), tanh(value.y), tanh(value.z), tanh(value.w));
    }

    @GPUIntrinsic(name = "asin")
    public static float asin(float value) {
        return (float) Math.asin(value);
    }

    @GPUIntrinsic(code = "asin({0})")
    public static Float2 asin(Float2 value) {
        return new Float2(asin(value.x), asin(value.y));
    }

    @GPUIntrinsic(code = "asin({0})")
    public static Float3 asin(Float3 value) {
        return new Float3(asin(value.x), asin(value.y), asin(value.z));
    }

    @GPUIntrinsic(code = "asin({0})")
    public static Float4 asin(Float4 value) {
        return new Float4(asin(value.x), asin(value.y), asin(value.z), asin(value.w));
    }

    @GPUIntrinsic(name = "asin")
    public static double asin(double value) {
        return Math.asin(value);
    }

    @GPUIntrinsic(code = "asin({0})")
    public static Double2 asin(Double2 value) {
        return new Double2(asin(value.x), asin(value.y));
    }

    @GPUIntrinsic(code = "asin({0})")
    public static Double3 asin(Double3 value) {
        return new Double3(asin(value.x), asin(value.y), asin(value.z));
    }

    @GPUIntrinsic(code = "asin({0})")
    public static Double4 asin(Double4 value) {
        return new Double4(asin(value.x), asin(value.y), asin(value.z), asin(value.w));
    }

    @GPUIntrinsic(name = "acos")
    public static float acos(float value) {
        return (float) Math.acos(value);
    }

    @GPUIntrinsic(code = "acos({0})")
    public static Float2 acos(Float2 value) {
        return new Float2(acos(value.x), acos(value.y));
    }

    @GPUIntrinsic(code = "acos({0})")
    public static Float3 acos(Float3 value) {
        return new Float3(acos(value.x), acos(value.y), acos(value.z));
    }

    @GPUIntrinsic(code = "acos({0})")
    public static Float4 acos(Float4 value) {
        return new Float4(acos(value.x), acos(value.y), acos(value.z), acos(value.w));
    }

    @GPUIntrinsic(name = "acos")
    public static double acos(double value) {
        return Math.acos(value);
    }

    @GPUIntrinsic(code = "acos({0})")
    public static Double2 acos(Double2 value) {
        return new Double2(acos(value.x), acos(value.y));
    }

    @GPUIntrinsic(code = "acos({0})")
    public static Double3 acos(Double3 value) {
        return new Double3(acos(value.x), acos(value.y), acos(value.z));
    }

    @GPUIntrinsic(code = "acos({0})")
    public static Double4 acos(Double4 value) {
        return new Double4(acos(value.x), acos(value.y), acos(value.z), acos(value.w));
    }

    @GPUIntrinsic(name = "atan")
    public static float atan(float value) {
        return (float) Math.atan(value);
    }

    @GPUIntrinsic(code = "atan({0})")
    public static Float2 atan(Float2 value) {
        return new Float2(atan(value.x), atan(value.y));
    }

    @GPUIntrinsic(code = "atan({0})")
    public static Float3 atan(Float3 value) {
        return new Float3(atan(value.x), atan(value.y), atan(value.z));
    }

    @GPUIntrinsic(code = "atan({0})")
    public static Float4 atan(Float4 value) {
        return new Float4(atan(value.x), atan(value.y), atan(value.z), atan(value.w));
    }

    @GPUIntrinsic(name = "atan")
    public static double atan(double value) {
        return Math.atan(value);
    }

    @GPUIntrinsic(code = "atan({0})")
    public static Double2 atan(Double2 value) {
        return new Double2(atan(value.x), atan(value.y));
    }

    @GPUIntrinsic(code = "atan({0})")
    public static Double3 atan(Double3 value) {
        return new Double3(atan(value.x), atan(value.y), atan(value.z));
    }

    @GPUIntrinsic(code = "atan({0})")
    public static Double4 atan(Double4 value) {
        return new Double4(atan(value.x), atan(value.y), atan(value.z), atan(value.w));
    }

    @GPUIntrinsic(name = "atan2")
    public static float atan2(float y, float x) {
        return (float) Math.atan2(y, x);
    }

    @GPUIntrinsic(code = "atan2({0}, {1})")
    public static Float2 atan2(Float2 y, Float2 x) {
        return new Float2(atan2(y.x, x.x), atan2(y.y, x.y));
    }

    @GPUIntrinsic(code = "atan2({0}, {1})")
    public static Float3 atan2(Float3 y, Float3 x) {
        return new Float3(atan2(y.x, x.x), atan2(y.y, x.y), atan2(y.z, x.z));
    }

    @GPUIntrinsic(code = "atan2({0}, {1})")
    public static Float4 atan2(Float4 y, Float4 x) {
        return new Float4(atan2(y.x, x.x), atan2(y.y, x.y), atan2(y.z, x.z), atan2(y.w, x.w));
    }

    @GPUIntrinsic(name = "atan2")
    public static double atan2(double y, double x) {
        return Math.atan2(y, x);
    }

    @GPUIntrinsic(code = "atan2({0}, {1})")
    public static Double2 atan2(Double2 y, Double2 x) {
        return new Double2(atan2(y.x, x.x), atan2(y.y, x.y));
    }

    @GPUIntrinsic(code = "atan2({0}, {1})")
    public static Double3 atan2(Double3 y, Double3 x) {
        return new Double3(atan2(y.x, x.x), atan2(y.y, x.y), atan2(y.z, x.z));
    }

    @GPUIntrinsic(code = "atan2({0}, {1})")
    public static Double4 atan2(Double4 y, Double4 x) {
        return new Double4(atan2(y.x, x.x), atan2(y.y, x.y), atan2(y.z, x.z), atan2(y.w, x.w));
    }

    @GPUIntrinsic(name = "sqrt")
    public static float sqrt(float value) {
        return (float) Math.sqrt(value);
    }

    @GPUIntrinsic(name = "cbrt")
    public static float cbrt(float value) {
        return (float) Math.cbrt(value);
    }

    @GPUIntrinsic(code = "cbrt({0})")
    public static Float2 cbrt(Float2 value) {
        return new Float2(cbrt(value.x), cbrt(value.y));
    }

    @GPUIntrinsic(code = "cbrt({0})")
    public static Float3 cbrt(Float3 value) {
        return new Float3(cbrt(value.x), cbrt(value.y), cbrt(value.z));
    }

    @GPUIntrinsic(code = "cbrt({0})")
    public static Float4 cbrt(Float4 value) {
        return new Float4(cbrt(value.x), cbrt(value.y), cbrt(value.z), cbrt(value.w));
    }

    @GPUIntrinsic(code = "sqrt({0})")
    public static Float2 sqrt(Float2 value) {
        return new Float2(sqrt(value.x), sqrt(value.y));
    }

    @GPUIntrinsic(code = "sqrt({0})")
    public static Float3 sqrt(Float3 value) {
        return new Float3(sqrt(value.x), sqrt(value.y), sqrt(value.z));
    }

    @GPUIntrinsic(code = "sqrt({0})")
    public static Float4 sqrt(Float4 value) {
        return new Float4(sqrt(value.x), sqrt(value.y), sqrt(value.z), sqrt(value.w));
    }

    @GPUIntrinsic(name = "sqrt")
    public static double sqrt(double value) {
        return Math.sqrt(value);
    }

    @GPUIntrinsic(name = "cbrt")
    public static double cbrt(double value) {
        return Math.cbrt(value);
    }

    @GPUIntrinsic(code = "cbrt({0})")
    public static Double2 cbrt(Double2 value) {
        return new Double2(cbrt(value.x), cbrt(value.y));
    }

    @GPUIntrinsic(code = "cbrt({0})")
    public static Double3 cbrt(Double3 value) {
        return new Double3(cbrt(value.x), cbrt(value.y), cbrt(value.z));
    }

    @GPUIntrinsic(code = "cbrt({0})")
    public static Double4 cbrt(Double4 value) {
        return new Double4(cbrt(value.x), cbrt(value.y), cbrt(value.z), cbrt(value.w));
    }

    @GPUIntrinsic(code = "sqrt({0})")
    public static Double2 sqrt(Double2 value) {
        return new Double2(sqrt(value.x), sqrt(value.y));
    }

    @GPUIntrinsic(code = "sqrt({0})")
    public static Double3 sqrt(Double3 value) {
        return new Double3(sqrt(value.x), sqrt(value.y), sqrt(value.z));
    }

    @GPUIntrinsic(code = "sqrt({0})")
    public static Double4 sqrt(Double4 value) {
        return new Double4(sqrt(value.x), sqrt(value.y), sqrt(value.z), sqrt(value.w));
    }

    @GPUIntrinsic(name = "exp")
    public static float exp(float value) {
        return (float) Math.exp(value);
    }

    @GPUIntrinsic(name = "exp2")
    public static float exp2(float value) {
        return (float) Math.pow(2.0, value);
    }

    @GPUIntrinsic(code = "exp2({0})")
    public static Float2 exp2(Float2 value) {
        return new Float2(exp2(value.x), exp2(value.y));
    }

    @GPUIntrinsic(code = "exp2({0})")
    public static Float3 exp2(Float3 value) {
        return new Float3(exp2(value.x), exp2(value.y), exp2(value.z));
    }

    @GPUIntrinsic(code = "exp2({0})")
    public static Float4 exp2(Float4 value) {
        return new Float4(exp2(value.x), exp2(value.y), exp2(value.z), exp2(value.w));
    }

    @GPUIntrinsic(code = "exp({0})")
    public static Float2 exp(Float2 value) {
        return new Float2(exp(value.x), exp(value.y));
    }

    @GPUIntrinsic(code = "exp({0})")
    public static Float3 exp(Float3 value) {
        return new Float3(exp(value.x), exp(value.y), exp(value.z));
    }

    @GPUIntrinsic(code = "exp({0})")
    public static Float4 exp(Float4 value) {
        return new Float4(exp(value.x), exp(value.y), exp(value.z), exp(value.w));
    }

    @GPUIntrinsic(name = "exp")
    public static double exp(double value) {
        return Math.exp(value);
    }

    @GPUIntrinsic(name = "exp2")
    public static double exp2(double value) {
        return Math.pow(2.0, value);
    }

    @GPUIntrinsic(code = "exp2({0})")
    public static Double2 exp2(Double2 value) {
        return new Double2(exp2(value.x), exp2(value.y));
    }

    @GPUIntrinsic(code = "exp2({0})")
    public static Double3 exp2(Double3 value) {
        return new Double3(exp2(value.x), exp2(value.y), exp2(value.z));
    }

    @GPUIntrinsic(code = "exp2({0})")
    public static Double4 exp2(Double4 value) {
        return new Double4(exp2(value.x), exp2(value.y), exp2(value.z), exp2(value.w));
    }

    @GPUIntrinsic(code = "exp({0})")
    public static Double2 exp(Double2 value) {
        return new Double2(exp(value.x), exp(value.y));
    }

    @GPUIntrinsic(code = "exp({0})")
    public static Double3 exp(Double3 value) {
        return new Double3(exp(value.x), exp(value.y), exp(value.z));
    }

    @GPUIntrinsic(code = "exp({0})")
    public static Double4 exp(Double4 value) {
        return new Double4(exp(value.x), exp(value.y), exp(value.z), exp(value.w));
    }

    @GPUIntrinsic(name = "log")
    public static float log(float value) {
        return (float) Math.log(value);
    }

    @GPUIntrinsic(name = "log10")
    public static float log10(float value) {
        return (float) Math.log10(value);
    }

    @GPUIntrinsic(code = "log10({0})")
    public static Float2 log10(Float2 value) {
        return new Float2(log10(value.x), log10(value.y));
    }

    @GPUIntrinsic(code = "log10({0})")
    public static Float3 log10(Float3 value) {
        return new Float3(log10(value.x), log10(value.y), log10(value.z));
    }

    @GPUIntrinsic(code = "log10({0})")
    public static Float4 log10(Float4 value) {
        return new Float4(log10(value.x), log10(value.y), log10(value.z), log10(value.w));
    }

    @GPUIntrinsic(code = "log({0})")
    public static Float2 log(Float2 value) {
        return new Float2(log(value.x), log(value.y));
    }

    @GPUIntrinsic(code = "log({0})")
    public static Float3 log(Float3 value) {
        return new Float3(log(value.x), log(value.y), log(value.z));
    }

    @GPUIntrinsic(code = "log({0})")
    public static Float4 log(Float4 value) {
        return new Float4(log(value.x), log(value.y), log(value.z), log(value.w));
    }

    @GPUIntrinsic(name = "log")
    public static double log(double value) {
        return Math.log(value);
    }

    @GPUIntrinsic(name = "log10")
    public static double log10(double value) {
        return Math.log10(value);
    }

    @GPUIntrinsic(code = "log10({0})")
    public static Double2 log10(Double2 value) {
        return new Double2(log10(value.x), log10(value.y));
    }

    @GPUIntrinsic(code = "log10({0})")
    public static Double3 log10(Double3 value) {
        return new Double3(log10(value.x), log10(value.y), log10(value.z));
    }

    @GPUIntrinsic(code = "log10({0})")
    public static Double4 log10(Double4 value) {
        return new Double4(log10(value.x), log10(value.y), log10(value.z), log10(value.w));
    }

    @GPUIntrinsic(code = "log({0})")
    public static Double2 log(Double2 value) {
        return new Double2(log(value.x), log(value.y));
    }

    @GPUIntrinsic(code = "log({0})")
    public static Double3 log(Double3 value) {
        return new Double3(log(value.x), log(value.y), log(value.z));
    }

    @GPUIntrinsic(code = "log({0})")
    public static Double4 log(Double4 value) {
        return new Double4(log(value.x), log(value.y), log(value.z), log(value.w));
    }

    @GPUIntrinsic(name = "log2")
    public static float log2(float value) {
        return (float) (Math.log(value) / LOG_2);
    }

    @GPUIntrinsic(code = "log2({0})")
    public static Float2 log2(Float2 value) {
        return new Float2(log2(value.x), log2(value.y));
    }

    @GPUIntrinsic(code = "log2({0})")
    public static Float3 log2(Float3 value) {
        return new Float3(log2(value.x), log2(value.y), log2(value.z));
    }

    @GPUIntrinsic(code = "log2({0})")
    public static Float4 log2(Float4 value) {
        return new Float4(log2(value.x), log2(value.y), log2(value.z), log2(value.w));
    }

    @GPUIntrinsic(name = "log2")
    public static double log2(double value) {
        return Math.log(value) / LOG_2;
    }

    @GPUIntrinsic(code = "log2({0})")
    public static Double2 log2(Double2 value) {
        return new Double2(log2(value.x), log2(value.y));
    }

    @GPUIntrinsic(code = "log2({0})")
    public static Double3 log2(Double3 value) {
        return new Double3(log2(value.x), log2(value.y), log2(value.z));
    }

    @GPUIntrinsic(code = "log2({0})")
    public static Double4 log2(Double4 value) {
        return new Double4(log2(value.x), log2(value.y), log2(value.z), log2(value.w));
    }

    @GPUIntrinsic(name = "fabs")
    public static float fabs(float value) {
        return Math.abs(value);
    }

    @GPUIntrinsic(code = "fabs({0})")
    public static Float2 fabs(Float2 value) {
        return new Float2(fabs(value.x), fabs(value.y));
    }

    @GPUIntrinsic(code = "fabs({0})")
    public static Float3 fabs(Float3 value) {
        return new Float3(fabs(value.x), fabs(value.y), fabs(value.z));
    }

    @GPUIntrinsic(code = "fabs({0})")
    public static Float4 fabs(Float4 value) {
        return new Float4(fabs(value.x), fabs(value.y), fabs(value.z), fabs(value.w));
    }

    @GPUIntrinsic(name = "fabs")
    public static double fabs(double value) {
        return Math.abs(value);
    }

    @GPUIntrinsic(code = "fabs({0})")
    public static Double2 fabs(Double2 value) {
        return new Double2(fabs(value.x), fabs(value.y));
    }

    @GPUIntrinsic(code = "fabs({0})")
    public static Double3 fabs(Double3 value) {
        return new Double3(fabs(value.x), fabs(value.y), fabs(value.z));
    }

    @GPUIntrinsic(code = "fabs({0})")
    public static Double4 fabs(Double4 value) {
        return new Double4(fabs(value.x), fabs(value.y), fabs(value.z), fabs(value.w));
    }

    @GPUIntrinsic(name = "fabs")
    public static float abs(float value) {
        return Math.abs(value);
    }

    @GPUIntrinsic(code = "fabs({0})")
    public static Float2 abs(Float2 value) {
        return fabs(value);
    }

    @GPUIntrinsic(code = "fabs({0})")
    public static Float3 abs(Float3 value) {
        return fabs(value);
    }

    @GPUIntrinsic(code = "fabs({0})")
    public static Float4 abs(Float4 value) {
        return fabs(value);
    }

    @GPUIntrinsic(name = "fabs")
    public static double abs(double value) {
        return Math.abs(value);
    }

    @GPUIntrinsic(code = "fabs({0})")
    public static Double2 abs(Double2 value) {
        return fabs(value);
    }

    @GPUIntrinsic(code = "fabs({0})")
    public static Double3 abs(Double3 value) {
        return fabs(value);
    }

    @GPUIntrinsic(code = "fabs({0})")
    public static Double4 abs(Double4 value) {
        return fabs(value);
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

    @GPUIntrinsic(code = "floor({0})")
    public static Float2 floor(Float2 value) {
        return new Float2(floor(value.x), floor(value.y));
    }

    @GPUIntrinsic(code = "floor({0})")
    public static Float3 floor(Float3 value) {
        return new Float3(floor(value.x), floor(value.y), floor(value.z));
    }

    @GPUIntrinsic(code = "floor({0})")
    public static Float4 floor(Float4 value) {
        return new Float4(floor(value.x), floor(value.y), floor(value.z), floor(value.w));
    }

    @GPUIntrinsic(name = "floor")
    public static double floor(double value) {
        return Math.floor(value);
    }

    @GPUIntrinsic(code = "floor({0})")
    public static Double2 floor(Double2 value) {
        return new Double2(floor(value.x), floor(value.y));
    }

    @GPUIntrinsic(code = "floor({0})")
    public static Double3 floor(Double3 value) {
        return new Double3(floor(value.x), floor(value.y), floor(value.z));
    }

    @GPUIntrinsic(code = "floor({0})")
    public static Double4 floor(Double4 value) {
        return new Double4(floor(value.x), floor(value.y), floor(value.z), floor(value.w));
    }

    @GPUIntrinsic(name = "ceil")
    public static float ceil(float value) {
        return (float) Math.ceil(value);
    }

    @GPUIntrinsic(code = "ceil({0})")
    public static Float2 ceil(Float2 value) {
        return new Float2(ceil(value.x), ceil(value.y));
    }

    @GPUIntrinsic(code = "ceil({0})")
    public static Float3 ceil(Float3 value) {
        return new Float3(ceil(value.x), ceil(value.y), ceil(value.z));
    }

    @GPUIntrinsic(code = "ceil({0})")
    public static Float4 ceil(Float4 value) {
        return new Float4(ceil(value.x), ceil(value.y), ceil(value.z), ceil(value.w));
    }

    @GPUIntrinsic(name = "ceil")
    public static double ceil(double value) {
        return Math.ceil(value);
    }

    @GPUIntrinsic(code = "ceil({0})")
    public static Double2 ceil(Double2 value) {
        return new Double2(ceil(value.x), ceil(value.y));
    }

    @GPUIntrinsic(code = "ceil({0})")
    public static Double3 ceil(Double3 value) {
        return new Double3(ceil(value.x), ceil(value.y), ceil(value.z));
    }

    @GPUIntrinsic(code = "ceil({0})")
    public static Double4 ceil(Double4 value) {
        return new Double4(ceil(value.x), ceil(value.y), ceil(value.z), ceil(value.w));
    }

    @GPUIntrinsic(name = "trunc")
    public static float trunc(float value) {
        return truncFloat(value);
    }

    @GPUIntrinsic(code = "trunc({0})")
    public static Float2 trunc(Float2 value) {
        return new Float2(trunc(value.x), trunc(value.y));
    }

    @GPUIntrinsic(code = "trunc({0})")
    public static Float3 trunc(Float3 value) {
        return new Float3(trunc(value.x), trunc(value.y), trunc(value.z));
    }

    @GPUIntrinsic(code = "trunc({0})")
    public static Float4 trunc(Float4 value) {
        return new Float4(trunc(value.x), trunc(value.y), trunc(value.z), trunc(value.w));
    }

    @GPUIntrinsic(name = "trunc")
    public static double trunc(double value) {
        return truncDouble(value);
    }

    @GPUIntrinsic(code = "trunc({0})")
    public static Double2 trunc(Double2 value) {
        return new Double2(trunc(value.x), trunc(value.y));
    }

    @GPUIntrinsic(code = "trunc({0})")
    public static Double3 trunc(Double3 value) {
        return new Double3(trunc(value.x), trunc(value.y), trunc(value.z));
    }

    @GPUIntrinsic(code = "trunc({0})")
    public static Double4 trunc(Double4 value) {
        return new Double4(trunc(value.x), trunc(value.y), trunc(value.z), trunc(value.w));
    }

    @GPUIntrinsic(name = "round")
    public static float round(float value) {
        return roundFloat(value);
    }

    @GPUIntrinsic(code = "round({0})")
    public static Float2 round(Float2 value) {
        return new Float2(round(value.x), round(value.y));
    }

    @GPUIntrinsic(code = "round({0})")
    public static Float3 round(Float3 value) {
        return new Float3(round(value.x), round(value.y), round(value.z));
    }

    @GPUIntrinsic(code = "round({0})")
    public static Float4 round(Float4 value) {
        return new Float4(round(value.x), round(value.y), round(value.z), round(value.w));
    }

    @GPUIntrinsic(name = "round")
    public static double round(double value) {
        return roundDouble(value);
    }

    @GPUIntrinsic(code = "round({0})")
    public static Double2 round(Double2 value) {
        return new Double2(round(value.x), round(value.y));
    }

    @GPUIntrinsic(code = "round({0})")
    public static Double3 round(Double3 value) {
        return new Double3(round(value.x), round(value.y), round(value.z));
    }

    @GPUIntrinsic(code = "round({0})")
    public static Double4 round(Double4 value) {
        return new Double4(round(value.x), round(value.y), round(value.z), round(value.w));
    }

    @GPUIntrinsic(name = "pow")
    public static float pow(float left, float right) {
        return (float) Math.pow(left, right);
    }

    @GPUIntrinsic(code = "pow({0}, {1})")
    public static Float2 pow(Float2 left, Float2 right) {
        return new Float2(pow(left.x, right.x), pow(left.y, right.y));
    }

    @GPUIntrinsic(code = "pow({0}, {1})")
    public static Float3 pow(Float3 left, Float3 right) {
        return new Float3(pow(left.x, right.x), pow(left.y, right.y), pow(left.z, right.z));
    }

    @GPUIntrinsic(code = "pow({0}, {1})")
    public static Float4 pow(Float4 left, Float4 right) {
        return new Float4(pow(left.x, right.x), pow(left.y, right.y), pow(left.z, right.z), pow(left.w, right.w));
    }

    @GPUIntrinsic(name = "pow")
    public static double pow(double left, double right) {
        return Math.pow(left, right);
    }

    @GPUIntrinsic(code = "pow({0}, {1})")
    public static Double2 pow(Double2 left, Double2 right) {
        return new Double2(pow(left.x, right.x), pow(left.y, right.y));
    }

    @GPUIntrinsic(code = "pow({0}, {1})")
    public static Double3 pow(Double3 left, Double3 right) {
        return new Double3(pow(left.x, right.x), pow(left.y, right.y), pow(left.z, right.z));
    }

    @GPUIntrinsic(code = "pow({0}, {1})")
    public static Double4 pow(Double4 left, Double4 right) {
        return new Double4(pow(left.x, right.x), pow(left.y, right.y), pow(left.z, right.z), pow(left.w, right.w));
    }

    @GPUIntrinsic(name = "pown")
    public static float pown(float value, int exponent) {
        return (float) Math.pow(value, exponent);
    }

    @GPUIntrinsic(code = "pown({0}, {1})")
    public static Float2 pown(Float2 value, Int2 exponent) {
        return new Float2(pown(value.x, exponent.x), pown(value.y, exponent.y));
    }

    @GPUIntrinsic(code = "pown({0}, {1})")
    public static Float3 pown(Float3 value, Int3 exponent) {
        return new Float3(pown(value.x, exponent.x), pown(value.y, exponent.y), pown(value.z, exponent.z));
    }

    @GPUIntrinsic(code = "pown({0}, {1})")
    public static Float4 pown(Float4 value, Int4 exponent) {
        return new Float4(pown(value.x, exponent.x), pown(value.y, exponent.y), pown(value.z, exponent.z), pown(value.w, exponent.w));
    }

    @GPUIntrinsic(name = "pown")
    public static double pown(double value, int exponent) {
        return Math.pow(value, exponent);
    }

    @GPUIntrinsic(code = "pown({0}, {1})")
    public static Double2 pown(Double2 value, Int2 exponent) {
        return new Double2(pown(value.x, exponent.x), pown(value.y, exponent.y));
    }

    @GPUIntrinsic(code = "pown({0}, {1})")
    public static Double3 pown(Double3 value, Int3 exponent) {
        return new Double3(pown(value.x, exponent.x), pown(value.y, exponent.y), pown(value.z, exponent.z));
    }

    @GPUIntrinsic(code = "pown({0}, {1})")
    public static Double4 pown(Double4 value, Int4 exponent) {
        return new Double4(pown(value.x, exponent.x), pown(value.y, exponent.y), pown(value.z, exponent.z), pown(value.w, exponent.w));
    }

    @GPUIntrinsic(name = "rootn")
    public static float rootn(float value, int exponent) {
        return (float) Math.pow(value, 1.0f / exponent);
    }

    @GPUIntrinsic(code = "rootn({0}, {1})")
    public static Float2 rootn(Float2 value, Int2 exponent) {
        return new Float2(rootn(value.x, exponent.x), rootn(value.y, exponent.y));
    }

    @GPUIntrinsic(code = "rootn({0}, {1})")
    public static Float3 rootn(Float3 value, Int3 exponent) {
        return new Float3(rootn(value.x, exponent.x), rootn(value.y, exponent.y), rootn(value.z, exponent.z));
    }

    @GPUIntrinsic(code = "rootn({0}, {1})")
    public static Float4 rootn(Float4 value, Int4 exponent) {
        return new Float4(rootn(value.x, exponent.x), rootn(value.y, exponent.y), rootn(value.z, exponent.z), rootn(value.w, exponent.w));
    }

    @GPUIntrinsic(name = "rootn")
    public static double rootn(double value, int exponent) {
        return Math.pow(value, 1.0 / exponent);
    }

    @GPUIntrinsic(code = "rootn({0}, {1})")
    public static Double2 rootn(Double2 value, Int2 exponent) {
        return new Double2(rootn(value.x, exponent.x), rootn(value.y, exponent.y));
    }

    @GPUIntrinsic(code = "rootn({0}, {1})")
    public static Double3 rootn(Double3 value, Int3 exponent) {
        return new Double3(rootn(value.x, exponent.x), rootn(value.y, exponent.y), rootn(value.z, exponent.z));
    }

    @GPUIntrinsic(code = "rootn({0}, {1})")
    public static Double4 rootn(Double4 value, Int4 exponent) {
        return new Double4(rootn(value.x, exponent.x), rootn(value.y, exponent.y), rootn(value.z, exponent.z), rootn(value.w, exponent.w));
    }

    @GPUIntrinsic(name = "powr")
    public static float powr(float value, float exponent) {
        return (float) Math.pow(value, exponent);
    }

    @GPUIntrinsic(code = "powr({0}, {1})")
    public static Float2 powr(Float2 value, Float2 exponent) {
        return new Float2(powr(value.x, exponent.x), powr(value.y, exponent.y));
    }

    @GPUIntrinsic(code = "powr({0}, {1})")
    public static Float3 powr(Float3 value, Float3 exponent) {
        return new Float3(powr(value.x, exponent.x), powr(value.y, exponent.y), powr(value.z, exponent.z));
    }

    @GPUIntrinsic(code = "powr({0}, {1})")
    public static Float4 powr(Float4 value, Float4 exponent) {
        return new Float4(powr(value.x, exponent.x), powr(value.y, exponent.y), powr(value.z, exponent.z), powr(value.w, exponent.w));
    }

    @GPUIntrinsic(name = "powr")
    public static double powr(double value, double exponent) {
        return Math.pow(value, exponent);
    }

    @GPUIntrinsic(code = "powr({0}, {1})")
    public static Double2 powr(Double2 value, Double2 exponent) {
        return new Double2(powr(value.x, exponent.x), powr(value.y, exponent.y));
    }

    @GPUIntrinsic(code = "powr({0}, {1})")
    public static Double3 powr(Double3 value, Double3 exponent) {
        return new Double3(powr(value.x, exponent.x), powr(value.y, exponent.y), powr(value.z, exponent.z));
    }

    @GPUIntrinsic(code = "powr({0}, {1})")
    public static Double4 powr(Double4 value, Double4 exponent) {
        return new Double4(powr(value.x, exponent.x), powr(value.y, exponent.y), powr(value.z, exponent.z), powr(value.w, exponent.w));
    }

    @GPUIntrinsic(name = "fmod")
    public static float fmod(float left, float right) {
        return left % right;
    }

    @GPUIntrinsic(code = "fmod({0}, {1})")
    public static Float2 fmod(Float2 left, Float2 right) {
        return new Float2(fmod(left.x, right.x), fmod(left.y, right.y));
    }

    @GPUIntrinsic(code = "fmod({0}, {1})")
    public static Float3 fmod(Float3 left, Float3 right) {
        return new Float3(fmod(left.x, right.x), fmod(left.y, right.y), fmod(left.z, right.z));
    }

    @GPUIntrinsic(code = "fmod({0}, {1})")
    public static Float4 fmod(Float4 left, Float4 right) {
        return new Float4(fmod(left.x, right.x), fmod(left.y, right.y), fmod(left.z, right.z), fmod(left.w, right.w));
    }

    @GPUIntrinsic(name = "fmod")
    public static double fmod(double left, double right) {
        return left % right;
    }

    @GPUIntrinsic(code = "fmod({0}, {1})")
    public static Double2 fmod(Double2 left, Double2 right) {
        return new Double2(fmod(left.x, right.x), fmod(left.y, right.y));
    }

    @GPUIntrinsic(code = "fmod({0}, {1})")
    public static Double3 fmod(Double3 left, Double3 right) {
        return new Double3(fmod(left.x, right.x), fmod(left.y, right.y), fmod(left.z, right.z));
    }

    @GPUIntrinsic(code = "fmod({0}, {1})")
    public static Double4 fmod(Double4 left, Double4 right) {
        return new Double4(fmod(left.x, right.x), fmod(left.y, right.y), fmod(left.z, right.z), fmod(left.w, right.w));
    }

    @GPUIntrinsic(name = "remainder")
    public static float remainder(float left, float right) {
        return (float) Math.IEEEremainder(left, right);
    }

    @GPUIntrinsic(code = "remainder({0}, {1})")
    public static Float2 remainder(Float2 left, Float2 right) {
        return new Float2(remainder(left.x, right.x), remainder(left.y, right.y));
    }

    @GPUIntrinsic(code = "remainder({0}, {1})")
    public static Float3 remainder(Float3 left, Float3 right) {
        return new Float3(remainder(left.x, right.x), remainder(left.y, right.y), remainder(left.z, right.z));
    }

    @GPUIntrinsic(code = "remainder({0}, {1})")
    public static Float4 remainder(Float4 left, Float4 right) {
        return new Float4(remainder(left.x, right.x), remainder(left.y, right.y), remainder(left.z, right.z), remainder(left.w, right.w));
    }

    @GPUIntrinsic(name = "remainder")
    public static double remainder(double left, double right) {
        return Math.IEEEremainder(left, right);
    }

    @GPUIntrinsic(code = "remainder({0}, {1})")
    public static Double2 remainder(Double2 left, Double2 right) {
        return new Double2(remainder(left.x, right.x), remainder(left.y, right.y));
    }

    @GPUIntrinsic(code = "remainder({0}, {1})")
    public static Double3 remainder(Double3 left, Double3 right) {
        return new Double3(remainder(left.x, right.x), remainder(left.y, right.y), remainder(left.z, right.z));
    }

    @GPUIntrinsic(code = "remainder({0}, {1})")
    public static Double4 remainder(Double4 left, Double4 right) {
        return new Double4(remainder(left.x, right.x), remainder(left.y, right.y), remainder(left.z, right.z), remainder(left.w, right.w));
    }

    @GPUIntrinsic(name = "nextafter")
    public static float nextafter(float start, float direction) {
        return Math.nextAfter(start, direction);
    }

    @GPUIntrinsic(code = "nextafter({0}, {1})")
    public static Float2 nextafter(Float2 start, Float2 direction) {
        return new Float2(nextafter(start.x, direction.x), nextafter(start.y, direction.y));
    }

    @GPUIntrinsic(code = "nextafter({0}, {1})")
    public static Float3 nextafter(Float3 start, Float3 direction) {
        return new Float3(nextafter(start.x, direction.x), nextafter(start.y, direction.y), nextafter(start.z, direction.z));
    }

    @GPUIntrinsic(code = "nextafter({0}, {1})")
    public static Float4 nextafter(Float4 start, Float4 direction) {
        return new Float4(nextafter(start.x, direction.x), nextafter(start.y, direction.y), nextafter(start.z, direction.z), nextafter(start.w, direction.w));
    }

    @GPUIntrinsic(name = "nextafter")
    public static double nextafter(double start, double direction) {
        return Math.nextAfter(start, direction);
    }

    @GPUIntrinsic(code = "nextafter({0}, {1})")
    public static Double2 nextafter(Double2 start, Double2 direction) {
        return new Double2(nextafter(start.x, direction.x), nextafter(start.y, direction.y));
    }

    @GPUIntrinsic(code = "nextafter({0}, {1})")
    public static Double3 nextafter(Double3 start, Double3 direction) {
        return new Double3(nextafter(start.x, direction.x), nextafter(start.y, direction.y), nextafter(start.z, direction.z));
    }

    @GPUIntrinsic(code = "nextafter({0}, {1})")
    public static Double4 nextafter(Double4 start, Double4 direction) {
        return new Double4(nextafter(start.x, direction.x), nextafter(start.y, direction.y), nextafter(start.z, direction.z), nextafter(start.w, direction.w));
    }

    @GPUIntrinsic(name = "ldexp")
    public static float ldexp(float value, int exponent) {
        return Math.scalb(value, exponent);
    }

    @GPUIntrinsic(code = "ldexp({0}, {1})")
    public static Float2 ldexp(Float2 value, Int2 exponent) {
        return new Float2(ldexp(value.x, exponent.x), ldexp(value.y, exponent.y));
    }

    @GPUIntrinsic(code = "ldexp({0}, {1})")
    public static Float3 ldexp(Float3 value, Int3 exponent) {
        return new Float3(ldexp(value.x, exponent.x), ldexp(value.y, exponent.y), ldexp(value.z, exponent.z));
    }

    @GPUIntrinsic(code = "ldexp({0}, {1})")
    public static Float4 ldexp(Float4 value, Int4 exponent) {
        return new Float4(ldexp(value.x, exponent.x), ldexp(value.y, exponent.y), ldexp(value.z, exponent.z), ldexp(value.w, exponent.w));
    }

    @GPUIntrinsic(name = "ldexp")
    public static double ldexp(double value, int exponent) {
        return Math.scalb(value, exponent);
    }

    @GPUIntrinsic(code = "ldexp({0}, {1})")
    public static Double2 ldexp(Double2 value, Int2 exponent) {
        return new Double2(ldexp(value.x, exponent.x), ldexp(value.y, exponent.y));
    }

    @GPUIntrinsic(code = "ldexp({0}, {1})")
    public static Double3 ldexp(Double3 value, Int3 exponent) {
        return new Double3(ldexp(value.x, exponent.x), ldexp(value.y, exponent.y), ldexp(value.z, exponent.z));
    }

    @GPUIntrinsic(code = "ldexp({0}, {1})")
    public static Double4 ldexp(Double4 value, Int4 exponent) {
        return new Double4(ldexp(value.x, exponent.x), ldexp(value.y, exponent.y), ldexp(value.z, exponent.z), ldexp(value.w, exponent.w));
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

    @GPUIntrinsic(code = "rsqrt({0})")
    public static Float2 rsqrt(Float2 value) {
        return new Float2(rsqrt(value.x), rsqrt(value.y));
    }

    @GPUIntrinsic(code = "rsqrt({0})")
    public static Float3 rsqrt(Float3 value) {
        return new Float3(rsqrt(value.x), rsqrt(value.y), rsqrt(value.z));
    }

    @GPUIntrinsic(code = "rsqrt({0})")
    public static Float4 rsqrt(Float4 value) {
        return new Float4(rsqrt(value.x), rsqrt(value.y), rsqrt(value.z), rsqrt(value.w));
    }

    @GPUIntrinsic(name = "rsqrt")
    public static double rsqrt(double value) {
        return 1.0 / Math.sqrt(value);
    }

    @GPUIntrinsic(code = "rsqrt({0})")
    public static Double2 rsqrt(Double2 value) {
        return new Double2(rsqrt(value.x), rsqrt(value.y));
    }

    @GPUIntrinsic(code = "rsqrt({0})")
    public static Double3 rsqrt(Double3 value) {
        return new Double3(rsqrt(value.x), rsqrt(value.y), rsqrt(value.z));
    }

    @GPUIntrinsic(code = "rsqrt({0})")
    public static Double4 rsqrt(Double4 value) {
        return new Double4(rsqrt(value.x), rsqrt(value.y), rsqrt(value.z), rsqrt(value.w));
    }

    @GPUIntrinsic(name = "fmin")
    public static float fmin(float left, float right) {
        return Math.min(left, right);
    }

    @GPUIntrinsic(code = "fmin({0}, {1})")
    public static Float2 fmin(Float2 left, Float2 right) {
        return new Float2(fmin(left.x, right.x), fmin(left.y, right.y));
    }

    @GPUIntrinsic(code = "fmin({0}, {1})")
    public static Float2 fmin(Float2 left, float right) {
        return new Float2(fmin(left.x, right), fmin(left.y, right));
    }

    @GPUIntrinsic(code = "fmin({0}, {1})")
    public static Float3 fmin(Float3 left, Float3 right) {
        return new Float3(fmin(left.x, right.x), fmin(left.y, right.y), fmin(left.z, right.z));
    }

    @GPUIntrinsic(code = "fmin({0}, {1})")
    public static Float3 fmin(Float3 left, float right) {
        return new Float3(fmin(left.x, right), fmin(left.y, right), fmin(left.z, right));
    }

    @GPUIntrinsic(code = "fmin({0}, {1})")
    public static Float4 fmin(Float4 left, Float4 right) {
        return new Float4(fmin(left.x, right.x), fmin(left.y, right.y), fmin(left.z, right.z), fmin(left.w, right.w));
    }

    @GPUIntrinsic(code = "fmin({0}, {1})")
    public static Float4 fmin(Float4 left, float right) {
        return new Float4(fmin(left.x, right), fmin(left.y, right), fmin(left.z, right), fmin(left.w, right));
    }

    @GPUIntrinsic(name = "fmin")
    public static double fmin(double left, double right) {
        return Math.min(left, right);
    }

    @GPUIntrinsic(code = "fmin({0}, {1})")
    public static Double2 fmin(Double2 left, Double2 right) {
        return new Double2(fmin(left.x, right.x), fmin(left.y, right.y));
    }

    @GPUIntrinsic(code = "fmin({0}, {1})")
    public static Double2 fmin(Double2 left, double right) {
        return new Double2(fmin(left.x, right), fmin(left.y, right));
    }

    @GPUIntrinsic(code = "fmin({0}, {1})")
    public static Double3 fmin(Double3 left, Double3 right) {
        return new Double3(fmin(left.x, right.x), fmin(left.y, right.y), fmin(left.z, right.z));
    }

    @GPUIntrinsic(code = "fmin({0}, {1})")
    public static Double3 fmin(Double3 left, double right) {
        return new Double3(fmin(left.x, right), fmin(left.y, right), fmin(left.z, right));
    }

    @GPUIntrinsic(code = "fmin({0}, {1})")
    public static Double4 fmin(Double4 left, Double4 right) {
        return new Double4(fmin(left.x, right.x), fmin(left.y, right.y), fmin(left.z, right.z), fmin(left.w, right.w));
    }

    @GPUIntrinsic(code = "fmin({0}, {1})")
    public static Double4 fmin(Double4 left, double right) {
        return new Double4(fmin(left.x, right), fmin(left.y, right), fmin(left.z, right), fmin(left.w, right));
    }

    @GPUIntrinsic(name = "fmax")
    public static float fmax(float left, float right) {
        return Math.max(left, right);
    }

    @GPUIntrinsic(code = "fmax({0}, {1})")
    public static Float2 fmax(Float2 left, Float2 right) {
        return new Float2(fmax(left.x, right.x), fmax(left.y, right.y));
    }

    @GPUIntrinsic(code = "fmax({0}, {1})")
    public static Float2 fmax(Float2 left, float right) {
        return new Float2(fmax(left.x, right), fmax(left.y, right));
    }

    @GPUIntrinsic(code = "fmax({0}, {1})")
    public static Float3 fmax(Float3 left, Float3 right) {
        return new Float3(fmax(left.x, right.x), fmax(left.y, right.y), fmax(left.z, right.z));
    }

    @GPUIntrinsic(code = "fmax({0}, {1})")
    public static Float3 fmax(Float3 left, float right) {
        return new Float3(fmax(left.x, right), fmax(left.y, right), fmax(left.z, right));
    }

    @GPUIntrinsic(code = "fmax({0}, {1})")
    public static Float4 fmax(Float4 left, Float4 right) {
        return new Float4(fmax(left.x, right.x), fmax(left.y, right.y), fmax(left.z, right.z), fmax(left.w, right.w));
    }

    @GPUIntrinsic(code = "fmax({0}, {1})")
    public static Float4 fmax(Float4 left, float right) {
        return new Float4(fmax(left.x, right), fmax(left.y, right), fmax(left.z, right), fmax(left.w, right));
    }

    @GPUIntrinsic(name = "fmax")
    public static double fmax(double left, double right) {
        return Math.max(left, right);
    }

    @GPUIntrinsic(code = "fmax({0}, {1})")
    public static Double2 fmax(Double2 left, Double2 right) {
        return new Double2(fmax(left.x, right.x), fmax(left.y, right.y));
    }

    @GPUIntrinsic(code = "fmax({0}, {1})")
    public static Double2 fmax(Double2 left, double right) {
        return new Double2(fmax(left.x, right), fmax(left.y, right));
    }

    @GPUIntrinsic(code = "fmax({0}, {1})")
    public static Double3 fmax(Double3 left, Double3 right) {
        return new Double3(fmax(left.x, right.x), fmax(left.y, right.y), fmax(left.z, right.z));
    }

    @GPUIntrinsic(code = "fmax({0}, {1})")
    public static Double3 fmax(Double3 left, double right) {
        return new Double3(fmax(left.x, right), fmax(left.y, right), fmax(left.z, right));
    }

    @GPUIntrinsic(code = "fmax({0}, {1})")
    public static Double4 fmax(Double4 left, Double4 right) {
        return new Double4(fmax(left.x, right.x), fmax(left.y, right.y), fmax(left.z, right.z), fmax(left.w, right.w));
    }

    @GPUIntrinsic(code = "fmax({0}, {1})")
    public static Double4 fmax(Double4 left, double right) {
        return new Double4(fmax(left.x, right), fmax(left.y, right), fmax(left.z, right), fmax(left.w, right));
    }

    @GPUIntrinsic(code = "minmag({0}, {1})")
    public static float minmag(float left, float right) {
        float absLeft = Math.abs(left);
        float absRight = Math.abs(right);
        return absLeft < absRight ? left : (absLeft > absRight ? right : min(left, right));
    }

    @GPUIntrinsic(code = "minmag({0}, {1})")
    public static Float2 minmag(Float2 left, Float2 right) {
        return new Float2(minmag(left.x, right.x), minmag(left.y, right.y));
    }

    @GPUIntrinsic(code = "minmag({0}, {1})")
    public static Float3 minmag(Float3 left, Float3 right) {
        return new Float3(minmag(left.x, right.x), minmag(left.y, right.y), minmag(left.z, right.z));
    }

    @GPUIntrinsic(code = "minmag({0}, {1})")
    public static Float4 minmag(Float4 left, Float4 right) {
        return new Float4(minmag(left.x, right.x), minmag(left.y, right.y), minmag(left.z, right.z), minmag(left.w, right.w));
    }

    @GPUIntrinsic(code = "minmag({0}, {1})")
    public static double minmag(double left, double right) {
        double absLeft = Math.abs(left);
        double absRight = Math.abs(right);
        return absLeft < absRight ? left : (absLeft > absRight ? right : min(left, right));
    }

    @GPUIntrinsic(code = "minmag({0}, {1})")
    public static Double2 minmag(Double2 left, Double2 right) {
        return new Double2(minmag(left.x, right.x), minmag(left.y, right.y));
    }

    @GPUIntrinsic(code = "minmag({0}, {1})")
    public static Double3 minmag(Double3 left, Double3 right) {
        return new Double3(minmag(left.x, right.x), minmag(left.y, right.y), minmag(left.z, right.z));
    }

    @GPUIntrinsic(code = "minmag({0}, {1})")
    public static Double4 minmag(Double4 left, Double4 right) {
        return new Double4(minmag(left.x, right.x), minmag(left.y, right.y), minmag(left.z, right.z), minmag(left.w, right.w));
    }

    @GPUIntrinsic(code = "maxmag({0}, {1})")
    public static float maxmag(float left, float right) {
        float absLeft = Math.abs(left);
        float absRight = Math.abs(right);
        return absLeft > absRight ? left : (absLeft < absRight ? right : max(left, right));
    }

    @GPUIntrinsic(code = "maxmag({0}, {1})")
    public static Float2 maxmag(Float2 left, Float2 right) {
        return new Float2(maxmag(left.x, right.x), maxmag(left.y, right.y));
    }

    @GPUIntrinsic(code = "maxmag({0}, {1})")
    public static Float3 maxmag(Float3 left, Float3 right) {
        return new Float3(maxmag(left.x, right.x), maxmag(left.y, right.y), maxmag(left.z, right.z));
    }

    @GPUIntrinsic(code = "maxmag({0}, {1})")
    public static Float4 maxmag(Float4 left, Float4 right) {
        return new Float4(maxmag(left.x, right.x), maxmag(left.y, right.y), maxmag(left.z, right.z), maxmag(left.w, right.w));
    }

    @GPUIntrinsic(code = "maxmag({0}, {1})")
    public static double maxmag(double left, double right) {
        double absLeft = Math.abs(left);
        double absRight = Math.abs(right);
        return absLeft > absRight ? left : (absLeft < absRight ? right : max(left, right));
    }

    @GPUIntrinsic(code = "maxmag({0}, {1})")
    public static Double2 maxmag(Double2 left, Double2 right) {
        return new Double2(maxmag(left.x, right.x), maxmag(left.y, right.y));
    }

    @GPUIntrinsic(code = "maxmag({0}, {1})")
    public static Double3 maxmag(Double3 left, Double3 right) {
        return new Double3(maxmag(left.x, right.x), maxmag(left.y, right.y), maxmag(left.z, right.z));
    }

    @GPUIntrinsic(code = "maxmag({0}, {1})")
    public static Double4 maxmag(Double4 left, Double4 right) {
        return new Double4(maxmag(left.x, right.x), maxmag(left.y, right.y), maxmag(left.z, right.z), maxmag(left.w, right.w));
    }

    @GPUIntrinsic(name = "mad")
    public static float mad(float a, float b, float c) {
        return a * b + c;
    }

    @GPUIntrinsic(name = "mad")
    public static double mad(double a, double b, double c) {
        return a * b + c;
    }

    @GPUIntrinsic(code = "mad({0}, {1}, {2})")
    public static Float2 mad(Float2 a, Float2 b, Float2 c) {
        return new Float2(mad(a.x, b.x, c.x), mad(a.y, b.y, c.y));
    }

    @GPUIntrinsic(code = "mad({0}, {1}, {2})")
    public static Float3 mad(Float3 a, Float3 b, Float3 c) {
        return new Float3(mad(a.x, b.x, c.x), mad(a.y, b.y, c.y), mad(a.z, b.z, c.z));
    }

    @GPUIntrinsic(code = "mad({0}, {1}, {2})")
    public static Float4 mad(Float4 a, Float4 b, Float4 c) {
        return new Float4(mad(a.x, b.x, c.x), mad(a.y, b.y, c.y), mad(a.z, b.z, c.z), mad(a.w, b.w, c.w));
    }

    @GPUIntrinsic(code = "mad({0}, {1}, {2})")
    public static Double2 mad(Double2 a, Double2 b, Double2 c) {
        return new Double2(mad(a.x, b.x, c.x), mad(a.y, b.y, c.y));
    }

    @GPUIntrinsic(code = "mad({0}, {1}, {2})")
    public static Double3 mad(Double3 a, Double3 b, Double3 c) {
        return new Double3(mad(a.x, b.x, c.x), mad(a.y, b.y, c.y), mad(a.z, b.z, c.z));
    }

    @GPUIntrinsic(code = "mad({0}, {1}, {2})")
    public static Double4 mad(Double4 a, Double4 b, Double4 c) {
        return new Double4(mad(a.x, b.x, c.x), mad(a.y, b.y, c.y), mad(a.z, b.z, c.z), mad(a.w, b.w, c.w));
    }

    @GPUIntrinsic(name = "fma")
    public static float fma(float a, float b, float c) {
        return Math.fma(a, b, c);
    }

    @GPUIntrinsic(name = "fma")
    public static double fma(double a, double b, double c) {
        return Math.fma(a, b, c);
    }

    @GPUIntrinsic(code = "fma({0}, {1}, {2})")
    public static Float2 fma(Float2 a, Float2 b, Float2 c) {
        return new Float2(fma(a.x, b.x, c.x), fma(a.y, b.y, c.y));
    }

    @GPUIntrinsic(code = "fma({0}, {1}, {2})")
    public static Float3 fma(Float3 a, Float3 b, Float3 c) {
        return new Float3(fma(a.x, b.x, c.x), fma(a.y, b.y, c.y), fma(a.z, b.z, c.z));
    }

    @GPUIntrinsic(code = "fma({0}, {1}, {2})")
    public static Float4 fma(Float4 a, Float4 b, Float4 c) {
        return new Float4(fma(a.x, b.x, c.x), fma(a.y, b.y, c.y), fma(a.z, b.z, c.z), fma(a.w, b.w, c.w));
    }

    @GPUIntrinsic(code = "fma({0}, {1}, {2})")
    public static Double2 fma(Double2 a, Double2 b, Double2 c) {
        return new Double2(fma(a.x, b.x, c.x), fma(a.y, b.y, c.y));
    }

    @GPUIntrinsic(code = "fma({0}, {1}, {2})")
    public static Double3 fma(Double3 a, Double3 b, Double3 c) {
        return new Double3(fma(a.x, b.x, c.x), fma(a.y, b.y, c.y), fma(a.z, b.z, c.z));
    }

    @GPUIntrinsic(code = "fma({0}, {1}, {2})")
    public static Double4 fma(Double4 a, Double4 b, Double4 c) {
        return new Double4(fma(a.x, b.x, c.x), fma(a.y, b.y, c.y), fma(a.z, b.z, c.z), fma(a.w, b.w, c.w));
    }

    @GPUIntrinsic(name = "mul24")
    public static int mul24(int left, int right) {
        return left * right;
    }

    @GPUIntrinsic(code = "mul24({0}, {1})")
    public static Int2 mul24(Int2 left, Int2 right) {
        return new Int2(mul24(left.x, right.x), mul24(left.y, right.y));
    }

    @GPUIntrinsic(code = "mul24({0}, {1})")
    public static Int3 mul24(Int3 left, Int3 right) {
        return new Int3(mul24(left.x, right.x), mul24(left.y, right.y), mul24(left.z, right.z));
    }

    @GPUIntrinsic(code = "mul24({0}, {1})")
    public static Int4 mul24(Int4 left, Int4 right) {
        return new Int4(mul24(left.x, right.x), mul24(left.y, right.y), mul24(left.z, right.z), mul24(left.w, right.w));
    }

    @GPUIntrinsic(name = "mad24")
    public static int mad24(int left, int right, int addend) {
        return left * right + addend;
    }

    @GPUIntrinsic(code = "mad24({0}, {1}, {2})")
    public static Int2 mad24(Int2 left, Int2 right, Int2 addend) {
        return new Int2(mad24(left.x, right.x, addend.x), mad24(left.y, right.y, addend.y));
    }

    @GPUIntrinsic(code = "mad24({0}, {1}, {2})")
    public static Int3 mad24(Int3 left, Int3 right, Int3 addend) {
        return new Int3(mad24(left.x, right.x, addend.x), mad24(left.y, right.y, addend.y), mad24(left.z, right.z, addend.z));
    }

    @GPUIntrinsic(code = "mad24({0}, {1}, {2})")
    public static Int4 mad24(Int4 left, Int4 right, Int4 addend) {
        return new Int4(mad24(left.x, right.x, addend.x), mad24(left.y, right.y, addend.y), mad24(left.z, right.z, addend.z), mad24(left.w, right.w, addend.w));
    }

    @GPUIntrinsic(name = "add_sat")
    public static int add_sat(int left, int right) {
        return saturatingAddInt(left, right);
    }

    @GPUIntrinsic(code = "add_sat({0}, {1})")
    public static Int2 add_sat(Int2 left, Int2 right) {
        return new Int2(add_sat(left.x, right.x), add_sat(left.y, right.y));
    }

    @GPUIntrinsic(code = "add_sat({0}, {1})")
    public static Int3 add_sat(Int3 left, Int3 right) {
        return new Int3(add_sat(left.x, right.x), add_sat(left.y, right.y), add_sat(left.z, right.z));
    }

    @GPUIntrinsic(code = "add_sat({0}, {1})")
    public static Int4 add_sat(Int4 left, Int4 right) {
        return new Int4(add_sat(left.x, right.x), add_sat(left.y, right.y), add_sat(left.z, right.z), add_sat(left.w, right.w));
    }

    @GPUIntrinsic(name = "add_sat")
    public static long add_sat(long left, long right) {
        return saturatingAddLong(left, right);
    }

    @GPUIntrinsic(code = "add_sat({0}, {1})")
    public static Long2 add_sat(Long2 left, Long2 right) {
        return new Long2(add_sat(left.x, right.x), add_sat(left.y, right.y));
    }

    @GPUIntrinsic(code = "add_sat({0}, {1})")
    public static Long3 add_sat(Long3 left, Long3 right) {
        return new Long3(add_sat(left.x, right.x), add_sat(left.y, right.y), add_sat(left.z, right.z));
    }

    @GPUIntrinsic(code = "add_sat({0}, {1})")
    public static Long4 add_sat(Long4 left, Long4 right) {
        return new Long4(add_sat(left.x, right.x), add_sat(left.y, right.y), add_sat(left.z, right.z), add_sat(left.w, right.w));
    }

    @GPUIntrinsic(code = "add_sat({0}, {1})")
    public static UByte add_sat(UByte left, UByte right) {
        return new UByte(saturatingAddUnsignedByte(left.value, right.value));
    }

    @GPUIntrinsic(code = "add_sat({0}, {1})")
    public static UShort add_sat(UShort left, UShort right) {
        return new UShort(saturatingAddUnsignedShort(left.value, right.value));
    }

    @GPUIntrinsic(code = "add_sat({0}, {1})")
    public static UInt add_sat(UInt left, UInt right) {
        return new UInt(saturatingAddUnsignedInt(left.value, right.value));
    }

    @GPUIntrinsic(code = "add_sat({0}, {1})")
    public static ULong add_sat(ULong left, ULong right) {
        return new ULong(saturatingAddUnsignedLong(left.value, right.value));
    }

    @GPUIntrinsic(name = "sub_sat")
    public static int sub_sat(int left, int right) {
        return saturatingSubInt(left, right);
    }

    @GPUIntrinsic(code = "sub_sat({0}, {1})")
    public static Int2 sub_sat(Int2 left, Int2 right) {
        return new Int2(sub_sat(left.x, right.x), sub_sat(left.y, right.y));
    }

    @GPUIntrinsic(code = "sub_sat({0}, {1})")
    public static Int3 sub_sat(Int3 left, Int3 right) {
        return new Int3(sub_sat(left.x, right.x), sub_sat(left.y, right.y), sub_sat(left.z, right.z));
    }

    @GPUIntrinsic(code = "sub_sat({0}, {1})")
    public static Int4 sub_sat(Int4 left, Int4 right) {
        return new Int4(sub_sat(left.x, right.x), sub_sat(left.y, right.y), sub_sat(left.z, right.z), sub_sat(left.w, right.w));
    }

    @GPUIntrinsic(name = "sub_sat")
    public static long sub_sat(long left, long right) {
        return saturatingSubLong(left, right);
    }

    @GPUIntrinsic(code = "sub_sat({0}, {1})")
    public static Long2 sub_sat(Long2 left, Long2 right) {
        return new Long2(sub_sat(left.x, right.x), sub_sat(left.y, right.y));
    }

    @GPUIntrinsic(code = "sub_sat({0}, {1})")
    public static Long3 sub_sat(Long3 left, Long3 right) {
        return new Long3(sub_sat(left.x, right.x), sub_sat(left.y, right.y), sub_sat(left.z, right.z));
    }

    @GPUIntrinsic(code = "sub_sat({0}, {1})")
    public static Long4 sub_sat(Long4 left, Long4 right) {
        return new Long4(sub_sat(left.x, right.x), sub_sat(left.y, right.y), sub_sat(left.z, right.z), sub_sat(left.w, right.w));
    }

    @GPUIntrinsic(code = "sub_sat({0}, {1})")
    public static UByte sub_sat(UByte left, UByte right) {
        return new UByte(saturatingSubUnsignedByte(left.value, right.value));
    }

    @GPUIntrinsic(code = "sub_sat({0}, {1})")
    public static UShort sub_sat(UShort left, UShort right) {
        return new UShort(saturatingSubUnsignedShort(left.value, right.value));
    }

    @GPUIntrinsic(code = "sub_sat({0}, {1})")
    public static UInt sub_sat(UInt left, UInt right) {
        return new UInt(saturatingSubUnsignedInt(left.value, right.value));
    }

    @GPUIntrinsic(code = "sub_sat({0}, {1})")
    public static ULong sub_sat(ULong left, ULong right) {
        return new ULong(saturatingSubUnsignedLong(left.value, right.value));
    }

    @GPUIntrinsic(name = "mul_sat")
    public static int mul_sat(int left, int right) {
        return saturatingMulInt(left, right);
    }

    @GPUIntrinsic(code = "mul_sat({0}, {1})")
    public static Int2 mul_sat(Int2 left, Int2 right) {
        return new Int2(mul_sat(left.x, right.x), mul_sat(left.y, right.y));
    }

    @GPUIntrinsic(code = "mul_sat({0}, {1})")
    public static Int3 mul_sat(Int3 left, Int3 right) {
        return new Int3(mul_sat(left.x, right.x), mul_sat(left.y, right.y), mul_sat(left.z, right.z));
    }

    @GPUIntrinsic(code = "mul_sat({0}, {1})")
    public static Int4 mul_sat(Int4 left, Int4 right) {
        return new Int4(mul_sat(left.x, right.x), mul_sat(left.y, right.y), mul_sat(left.z, right.z), mul_sat(left.w, right.w));
    }

    @GPUIntrinsic(name = "mul_sat")
    public static long mul_sat(long left, long right) {
        return saturatingMulLong(left, right);
    }

    @GPUIntrinsic(code = "mul_sat({0}, {1})")
    public static Long2 mul_sat(Long2 left, Long2 right) {
        return new Long2(mul_sat(left.x, right.x), mul_sat(left.y, right.y));
    }

    @GPUIntrinsic(code = "mul_sat({0}, {1})")
    public static Long3 mul_sat(Long3 left, Long3 right) {
        return new Long3(mul_sat(left.x, right.x), mul_sat(left.y, right.y), mul_sat(left.z, right.z));
    }

    @GPUIntrinsic(code = "mul_sat({0}, {1})")
    public static Long4 mul_sat(Long4 left, Long4 right) {
        return new Long4(mul_sat(left.x, right.x), mul_sat(left.y, right.y), mul_sat(left.z, right.z), mul_sat(left.w, right.w));
    }

    @GPUIntrinsic(code = "mul_sat({0}, {1})")
    public static UByte mul_sat(UByte left, UByte right) {
        return new UByte(saturatingMulUnsignedByte(left.value, right.value));
    }

    @GPUIntrinsic(code = "mul_sat({0}, {1})")
    public static UShort mul_sat(UShort left, UShort right) {
        return new UShort(saturatingMulUnsignedShort(left.value, right.value));
    }

    @GPUIntrinsic(code = "mul_sat({0}, {1})")
    public static UInt mul_sat(UInt left, UInt right) {
        return new UInt(saturatingMulUnsignedInt(left.value, right.value));
    }

    @GPUIntrinsic(code = "mul_sat({0}, {1})")
    public static ULong mul_sat(ULong left, ULong right) {
        return new ULong(saturatingMulUnsignedLong(left.value, right.value));
    }

    @GPUIntrinsic(name = "mad_sat")
    public static int mad_sat(int left, int right, int addend) {
        return saturatingMadInt(left, right, addend);
    }

    @GPUIntrinsic(code = "mad_sat({0}, {1}, {2})")
    public static Int2 mad_sat(Int2 left, Int2 right, Int2 addend) {
        return new Int2(mad_sat(left.x, right.x, addend.x), mad_sat(left.y, right.y, addend.y));
    }

    @GPUIntrinsic(code = "mad_sat({0}, {1}, {2})")
    public static Int3 mad_sat(Int3 left, Int3 right, Int3 addend) {
        return new Int3(mad_sat(left.x, right.x, addend.x), mad_sat(left.y, right.y, addend.y), mad_sat(left.z, right.z, addend.z));
    }

    @GPUIntrinsic(code = "mad_sat({0}, {1}, {2})")
    public static Int4 mad_sat(Int4 left, Int4 right, Int4 addend) {
        return new Int4(mad_sat(left.x, right.x, addend.x), mad_sat(left.y, right.y, addend.y), mad_sat(left.z, right.z, addend.z), mad_sat(left.w, right.w, addend.w));
    }

    @GPUIntrinsic(name = "mad_sat")
    public static long mad_sat(long left, long right, long addend) {
        return saturatingMadLong(left, right, addend);
    }

    @GPUIntrinsic(code = "mad_sat({0}, {1}, {2})")
    public static Long2 mad_sat(Long2 left, Long2 right, Long2 addend) {
        return new Long2(mad_sat(left.x, right.x, addend.x), mad_sat(left.y, right.y, addend.y));
    }

    @GPUIntrinsic(code = "mad_sat({0}, {1}, {2})")
    public static Long3 mad_sat(Long3 left, Long3 right, Long3 addend) {
        return new Long3(mad_sat(left.x, right.x, addend.x), mad_sat(left.y, right.y, addend.y), mad_sat(left.z, right.z, addend.z));
    }

    @GPUIntrinsic(code = "mad_sat({0}, {1}, {2})")
    public static Long4 mad_sat(Long4 left, Long4 right, Long4 addend) {
        return new Long4(mad_sat(left.x, right.x, addend.x), mad_sat(left.y, right.y, addend.y), mad_sat(left.z, right.z, addend.z), mad_sat(left.w, right.w, addend.w));
    }

    @GPUIntrinsic(code = "mad_sat({0}, {1}, {2})")
    public static UByte mad_sat(UByte left, UByte right, UByte addend) {
        return new UByte(saturatingMadUnsignedByte(left.value, right.value, addend.value));
    }

    @GPUIntrinsic(code = "mad_sat({0}, {1}, {2})")
    public static UShort mad_sat(UShort left, UShort right, UShort addend) {
        return new UShort(saturatingMadUnsignedShort(left.value, right.value, addend.value));
    }

    @GPUIntrinsic(code = "mad_sat({0}, {1}, {2})")
    public static UInt mad_sat(UInt left, UInt right, UInt addend) {
        return new UInt(saturatingMadUnsignedInt(left.value, right.value, addend.value));
    }

    @GPUIntrinsic(code = "mad_sat({0}, {1}, {2})")
    public static ULong mad_sat(ULong left, ULong right, ULong addend) {
        return new ULong(saturatingMadUnsignedLong(left.value, right.value, addend.value));
    }

    @GPUIntrinsic(name = "hadd")
    public static int hadd(int left, int right) {
        return (left >> 1) + (right >> 1) + ((left & 1) & (right & 1));
    }

    @GPUIntrinsic(code = "hadd({0}, {1})")
    public static Int2 hadd(Int2 left, Int2 right) {
        return new Int2(hadd(left.x, right.x), hadd(left.y, right.y));
    }

    @GPUIntrinsic(code = "hadd({0}, {1})")
    public static Int3 hadd(Int3 left, Int3 right) {
        return new Int3(hadd(left.x, right.x), hadd(left.y, right.y), hadd(left.z, right.z));
    }

    @GPUIntrinsic(code = "hadd({0}, {1})")
    public static Int4 hadd(Int4 left, Int4 right) {
        return new Int4(hadd(left.x, right.x), hadd(left.y, right.y), hadd(left.z, right.z), hadd(left.w, right.w));
    }

    @GPUIntrinsic(name = "hadd")
    public static long hadd(long left, long right) {
        return (left >> 1) + (right >> 1) + ((left & 1L) & (right & 1L));
    }

    @GPUIntrinsic(code = "hadd({0}, {1})")
    public static Long2 hadd(Long2 left, Long2 right) {
        return new Long2(hadd(left.x, right.x), hadd(left.y, right.y));
    }

    @GPUIntrinsic(code = "hadd({0}, {1})")
    public static Long3 hadd(Long3 left, Long3 right) {
        return new Long3(hadd(left.x, right.x), hadd(left.y, right.y), hadd(left.z, right.z));
    }

    @GPUIntrinsic(code = "hadd({0}, {1})")
    public static Long4 hadd(Long4 left, Long4 right) {
        return new Long4(hadd(left.x, right.x), hadd(left.y, right.y), hadd(left.z, right.z), hadd(left.w, right.w));
    }

    @GPUIntrinsic(name = "rhadd")
    public static int rhadd(int left, int right) {
        return (left >> 1) + (right >> 1) + ((left & 1) | (right & 1));
    }

    @GPUIntrinsic(code = "rhadd({0}, {1})")
    public static Int2 rhadd(Int2 left, Int2 right) {
        return new Int2(rhadd(left.x, right.x), rhadd(left.y, right.y));
    }

    @GPUIntrinsic(code = "rhadd({0}, {1})")
    public static Int3 rhadd(Int3 left, Int3 right) {
        return new Int3(rhadd(left.x, right.x), rhadd(left.y, right.y), rhadd(left.z, right.z));
    }

    @GPUIntrinsic(code = "rhadd({0}, {1})")
    public static Int4 rhadd(Int4 left, Int4 right) {
        return new Int4(rhadd(left.x, right.x), rhadd(left.y, right.y), rhadd(left.z, right.z), rhadd(left.w, right.w));
    }

    @GPUIntrinsic(name = "rhadd")
    public static long rhadd(long left, long right) {
        return (left >> 1) + (right >> 1) + ((left & 1L) | (right & 1L));
    }

    @GPUIntrinsic(code = "rhadd({0}, {1})")
    public static Long2 rhadd(Long2 left, Long2 right) {
        return new Long2(rhadd(left.x, right.x), rhadd(left.y, right.y));
    }

    @GPUIntrinsic(code = "rhadd({0}, {1})")
    public static Long3 rhadd(Long3 left, Long3 right) {
        return new Long3(rhadd(left.x, right.x), rhadd(left.y, right.y), rhadd(left.z, right.z));
    }

    @GPUIntrinsic(code = "rhadd({0}, {1})")
    public static Long4 rhadd(Long4 left, Long4 right) {
        return new Long4(rhadd(left.x, right.x), rhadd(left.y, right.y), rhadd(left.z, right.z), rhadd(left.w, right.w));
    }

    @GPUIntrinsic(name = "mul_hi")
    public static int mul_hi(int left, int right) {
        return (int) ((((long) left) * ((long) right)) >> 32);
    }

    @GPUIntrinsic(code = "mul_hi({0}, {1})")
    public static Int2 mul_hi(Int2 left, Int2 right) {
        return new Int2(mul_hi(left.x, right.x), mul_hi(left.y, right.y));
    }

    @GPUIntrinsic(code = "mul_hi({0}, {1})")
    public static Int3 mul_hi(Int3 left, Int3 right) {
        return new Int3(mul_hi(left.x, right.x), mul_hi(left.y, right.y), mul_hi(left.z, right.z));
    }

    @GPUIntrinsic(code = "mul_hi({0}, {1})")
    public static Int4 mul_hi(Int4 left, Int4 right) {
        return new Int4(mul_hi(left.x, right.x), mul_hi(left.y, right.y), mul_hi(left.z, right.z), mul_hi(left.w, right.w));
    }

    @GPUIntrinsic(name = "mul_hi")
    public static long mul_hi(long left, long right) {
        return signedMulHiLong(left, right);
    }

    @GPUIntrinsic(code = "mul_hi({0}, {1})")
    public static Long2 mul_hi(Long2 left, Long2 right) {
        return new Long2(mul_hi(left.x, right.x), mul_hi(left.y, right.y));
    }

    @GPUIntrinsic(code = "mul_hi({0}, {1})")
    public static Long3 mul_hi(Long3 left, Long3 right) {
        return new Long3(mul_hi(left.x, right.x), mul_hi(left.y, right.y), mul_hi(left.z, right.z));
    }

    @GPUIntrinsic(code = "mul_hi({0}, {1})")
    public static Long4 mul_hi(Long4 left, Long4 right) {
        return new Long4(mul_hi(left.x, right.x), mul_hi(left.y, right.y), mul_hi(left.z, right.z), mul_hi(left.w, right.w));
    }

    @GPUIntrinsic(name = "mad_hi")
    public static int mad_hi(int left, int right, int addend) {
        return mul_hi(left, right) + addend;
    }

    @GPUIntrinsic(code = "mad_hi({0}, {1}, {2})")
    public static Int2 mad_hi(Int2 left, Int2 right, Int2 addend) {
        return new Int2(mad_hi(left.x, right.x, addend.x), mad_hi(left.y, right.y, addend.y));
    }

    @GPUIntrinsic(code = "mad_hi({0}, {1}, {2})")
    public static Int3 mad_hi(Int3 left, Int3 right, Int3 addend) {
        return new Int3(mad_hi(left.x, right.x, addend.x), mad_hi(left.y, right.y, addend.y), mad_hi(left.z, right.z, addend.z));
    }

    @GPUIntrinsic(code = "mad_hi({0}, {1}, {2})")
    public static Int4 mad_hi(Int4 left, Int4 right, Int4 addend) {
        return new Int4(mad_hi(left.x, right.x, addend.x), mad_hi(left.y, right.y, addend.y), mad_hi(left.z, right.z, addend.z), mad_hi(left.w, right.w, addend.w));
    }

    @GPUIntrinsic(name = "mad_hi")
    public static long mad_hi(long left, long right, long addend) {
        return mul_hi(left, right) + addend;
    }

    @GPUIntrinsic(code = "mad_hi({0}, {1}, {2})")
    public static Long2 mad_hi(Long2 left, Long2 right, Long2 addend) {
        return new Long2(mad_hi(left.x, right.x, addend.x), mad_hi(left.y, right.y, addend.y));
    }

    @GPUIntrinsic(code = "mad_hi({0}, {1}, {2})")
    public static Long3 mad_hi(Long3 left, Long3 right, Long3 addend) {
        return new Long3(mad_hi(left.x, right.x, addend.x), mad_hi(left.y, right.y, addend.y), mad_hi(left.z, right.z, addend.z));
    }

    @GPUIntrinsic(code = "mad_hi({0}, {1}, {2})")
    public static Long4 mad_hi(Long4 left, Long4 right, Long4 addend) {
        return new Long4(mad_hi(left.x, right.x, addend.x), mad_hi(left.y, right.y, addend.y), mad_hi(left.z, right.z, addend.z), mad_hi(left.w, right.w, addend.w));
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

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Float2 clamp(Float2 value, Float2 minValue, Float2 maxValue) {
        return new Float2(clamp(value.x, minValue.x, maxValue.x), clamp(value.y, minValue.y, maxValue.y));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Float2 clamp(Float2 value, float minValue, float maxValue) {
        return new Float2(clamp(value.x, minValue, maxValue), clamp(value.y, minValue, maxValue));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Int2 clamp(Int2 value, Int2 minValue, Int2 maxValue) {
        return new Int2(clamp(value.x, minValue.x, maxValue.x), clamp(value.y, minValue.y, maxValue.y));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Byte2 clamp(Byte2 value, Byte2 minValue, Byte2 maxValue) {
        return new Byte2((byte) clamp(value.x, minValue.x, maxValue.x), (byte) clamp(value.y, minValue.y, maxValue.y));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Byte3 clamp(Byte3 value, Byte3 minValue, Byte3 maxValue) {
        return new Byte3((byte) clamp(value.x, minValue.x, maxValue.x), (byte) clamp(value.y, minValue.y, maxValue.y), (byte) clamp(value.z, minValue.z, maxValue.z));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Byte4 clamp(Byte4 value, Byte4 minValue, Byte4 maxValue) {
        return new Byte4((byte) clamp(value.x, minValue.x, maxValue.x), (byte) clamp(value.y, minValue.y, maxValue.y), (byte) clamp(value.z, minValue.z, maxValue.z), (byte) clamp(value.w, minValue.w, maxValue.w));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Short2 clamp(Short2 value, Short2 minValue, Short2 maxValue) {
        return new Short2((short) clamp(value.x, minValue.x, maxValue.x), (short) clamp(value.y, minValue.y, maxValue.y));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Short3 clamp(Short3 value, Short3 minValue, Short3 maxValue) {
        return new Short3((short) clamp(value.x, minValue.x, maxValue.x), (short) clamp(value.y, minValue.y, maxValue.y), (short) clamp(value.z, minValue.z, maxValue.z));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Short4 clamp(Short4 value, Short4 minValue, Short4 maxValue) {
        return new Short4((short) clamp(value.x, minValue.x, maxValue.x), (short) clamp(value.y, minValue.y, maxValue.y), (short) clamp(value.z, minValue.z, maxValue.z), (short) clamp(value.w, minValue.w, maxValue.w));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Long2 clamp(Long2 value, Long2 minValue, Long2 maxValue) {
        return new Long2(clamp(value.x, minValue.x, maxValue.x), clamp(value.y, minValue.y, maxValue.y));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static UInt2 clamp(UInt2 value, UInt2 minValue, UInt2 maxValue) {
        return new UInt2(clampUnsignedInt(value.x, minValue.x, maxValue.x), clampUnsignedInt(value.y, minValue.y, maxValue.y));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static UByte2 clamp(UByte2 value, UByte2 minValue, UByte2 maxValue) {
        return new UByte2(clampUnsignedByte(value.x, minValue.x, maxValue.x), clampUnsignedByte(value.y, minValue.y, maxValue.y));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static UShort2 clamp(UShort2 value, UShort2 minValue, UShort2 maxValue) {
        return new UShort2(clampUnsignedShort(value.x, minValue.x, maxValue.x), clampUnsignedShort(value.y, minValue.y, maxValue.y));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static ULong2 clamp(ULong2 value, ULong2 minValue, ULong2 maxValue) {
        return new ULong2(clampUnsignedLong(value.x, minValue.x, maxValue.x), clampUnsignedLong(value.y, minValue.y, maxValue.y));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Float3 clamp(Float3 value, Float3 minValue, Float3 maxValue) {
        return new Float3(clamp(value.x, minValue.x, maxValue.x), clamp(value.y, minValue.y, maxValue.y), clamp(value.z, minValue.z, maxValue.z));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Float3 clamp(Float3 value, float minValue, float maxValue) {
        return new Float3(clamp(value.x, minValue, maxValue), clamp(value.y, minValue, maxValue), clamp(value.z, minValue, maxValue));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Int3 clamp(Int3 value, Int3 minValue, Int3 maxValue) {
        return new Int3(clamp(value.x, minValue.x, maxValue.x), clamp(value.y, minValue.y, maxValue.y), clamp(value.z, minValue.z, maxValue.z));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Long3 clamp(Long3 value, Long3 minValue, Long3 maxValue) {
        return new Long3(clamp(value.x, minValue.x, maxValue.x), clamp(value.y, minValue.y, maxValue.y), clamp(value.z, minValue.z, maxValue.z));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static UInt3 clamp(UInt3 value, UInt3 minValue, UInt3 maxValue) {
        return new UInt3(clampUnsignedInt(value.x, minValue.x, maxValue.x), clampUnsignedInt(value.y, minValue.y, maxValue.y), clampUnsignedInt(value.z, minValue.z, maxValue.z));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static UByte3 clamp(UByte3 value, UByte3 minValue, UByte3 maxValue) {
        return new UByte3(clampUnsignedByte(value.x, minValue.x, maxValue.x), clampUnsignedByte(value.y, minValue.y, maxValue.y), clampUnsignedByte(value.z, minValue.z, maxValue.z));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static UShort3 clamp(UShort3 value, UShort3 minValue, UShort3 maxValue) {
        return new UShort3(clampUnsignedShort(value.x, minValue.x, maxValue.x), clampUnsignedShort(value.y, minValue.y, maxValue.y), clampUnsignedShort(value.z, minValue.z, maxValue.z));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static ULong3 clamp(ULong3 value, ULong3 minValue, ULong3 maxValue) {
        return new ULong3(clampUnsignedLong(value.x, minValue.x, maxValue.x), clampUnsignedLong(value.y, minValue.y, maxValue.y), clampUnsignedLong(value.z, minValue.z, maxValue.z));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Float4 clamp(Float4 value, Float4 minValue, Float4 maxValue) {
        return new Float4(clamp(value.x, minValue.x, maxValue.x), clamp(value.y, minValue.y, maxValue.y), clamp(value.z, minValue.z, maxValue.z), clamp(value.w, minValue.w, maxValue.w));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Float4 clamp(Float4 value, float minValue, float maxValue) {
        return new Float4(clamp(value.x, minValue, maxValue), clamp(value.y, minValue, maxValue), clamp(value.z, minValue, maxValue), clamp(value.w, minValue, maxValue));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Int4 clamp(Int4 value, Int4 minValue, Int4 maxValue) {
        return new Int4(clamp(value.x, minValue.x, maxValue.x), clamp(value.y, minValue.y, maxValue.y), clamp(value.z, minValue.z, maxValue.z), clamp(value.w, minValue.w, maxValue.w));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Long4 clamp(Long4 value, Long4 minValue, Long4 maxValue) {
        return new Long4(clamp(value.x, minValue.x, maxValue.x), clamp(value.y, minValue.y, maxValue.y), clamp(value.z, minValue.z, maxValue.z), clamp(value.w, minValue.w, maxValue.w));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static UInt4 clamp(UInt4 value, UInt4 minValue, UInt4 maxValue) {
        return new UInt4(clampUnsignedInt(value.x, minValue.x, maxValue.x), clampUnsignedInt(value.y, minValue.y, maxValue.y), clampUnsignedInt(value.z, minValue.z, maxValue.z), clampUnsignedInt(value.w, minValue.w, maxValue.w));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static UByte4 clamp(UByte4 value, UByte4 minValue, UByte4 maxValue) {
        return new UByte4(clampUnsignedByte(value.x, minValue.x, maxValue.x), clampUnsignedByte(value.y, minValue.y, maxValue.y), clampUnsignedByte(value.z, minValue.z, maxValue.z), clampUnsignedByte(value.w, minValue.w, maxValue.w));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static UShort4 clamp(UShort4 value, UShort4 minValue, UShort4 maxValue) {
        return new UShort4(clampUnsignedShort(value.x, minValue.x, maxValue.x), clampUnsignedShort(value.y, minValue.y, maxValue.y), clampUnsignedShort(value.z, minValue.z, maxValue.z), clampUnsignedShort(value.w, minValue.w, maxValue.w));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static ULong4 clamp(ULong4 value, ULong4 minValue, ULong4 maxValue) {
        return new ULong4(clampUnsignedLong(value.x, minValue.x, maxValue.x), clampUnsignedLong(value.y, minValue.y, maxValue.y), clampUnsignedLong(value.z, minValue.z, maxValue.z), clampUnsignedLong(value.w, minValue.w, maxValue.w));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static UByte8 clamp(UByte8 value, UByte8 minValue, UByte8 maxValue) {
        return new UByte8(
                clampUnsignedByte(value.s0, minValue.s0, maxValue.s0),
                clampUnsignedByte(value.s1, minValue.s1, maxValue.s1),
                clampUnsignedByte(value.s2, minValue.s2, maxValue.s2),
                clampUnsignedByte(value.s3, minValue.s3, maxValue.s3),
                clampUnsignedByte(value.s4, minValue.s4, maxValue.s4),
                clampUnsignedByte(value.s5, minValue.s5, maxValue.s5),
                clampUnsignedByte(value.s6, minValue.s6, maxValue.s6),
                clampUnsignedByte(value.s7, minValue.s7, maxValue.s7)
        );
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static UShort8 clamp(UShort8 value, UShort8 minValue, UShort8 maxValue) {
        return new UShort8(
                clampUnsignedShort(value.s0, minValue.s0, maxValue.s0),
                clampUnsignedShort(value.s1, minValue.s1, maxValue.s1),
                clampUnsignedShort(value.s2, minValue.s2, maxValue.s2),
                clampUnsignedShort(value.s3, minValue.s3, maxValue.s3),
                clampUnsignedShort(value.s4, minValue.s4, maxValue.s4),
                clampUnsignedShort(value.s5, minValue.s5, maxValue.s5),
                clampUnsignedShort(value.s6, minValue.s6, maxValue.s6),
                clampUnsignedShort(value.s7, minValue.s7, maxValue.s7)
        );
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static UInt8 clamp(UInt8 value, UInt8 minValue, UInt8 maxValue) {
        return new UInt8(
                clampUnsignedInt(value.s0, minValue.s0, maxValue.s0),
                clampUnsignedInt(value.s1, minValue.s1, maxValue.s1),
                clampUnsignedInt(value.s2, minValue.s2, maxValue.s2),
                clampUnsignedInt(value.s3, minValue.s3, maxValue.s3),
                clampUnsignedInt(value.s4, minValue.s4, maxValue.s4),
                clampUnsignedInt(value.s5, minValue.s5, maxValue.s5),
                clampUnsignedInt(value.s6, minValue.s6, maxValue.s6),
                clampUnsignedInt(value.s7, minValue.s7, maxValue.s7)
        );
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static ULong8 clamp(ULong8 value, ULong8 minValue, ULong8 maxValue) {
        return new ULong8(
                clampUnsignedLong(value.s0, minValue.s0, maxValue.s0),
                clampUnsignedLong(value.s1, minValue.s1, maxValue.s1),
                clampUnsignedLong(value.s2, minValue.s2, maxValue.s2),
                clampUnsignedLong(value.s3, minValue.s3, maxValue.s3),
                clampUnsignedLong(value.s4, minValue.s4, maxValue.s4),
                clampUnsignedLong(value.s5, minValue.s5, maxValue.s5),
                clampUnsignedLong(value.s6, minValue.s6, maxValue.s6),
                clampUnsignedLong(value.s7, minValue.s7, maxValue.s7)
        );
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static UByte16 clamp(UByte16 value, UByte16 minValue, UByte16 maxValue) {
        return new UByte16(
                clampUnsignedByte(value.s0, minValue.s0, maxValue.s0), clampUnsignedByte(value.s1, minValue.s1, maxValue.s1),
                clampUnsignedByte(value.s2, minValue.s2, maxValue.s2), clampUnsignedByte(value.s3, minValue.s3, maxValue.s3),
                clampUnsignedByte(value.s4, minValue.s4, maxValue.s4), clampUnsignedByte(value.s5, minValue.s5, maxValue.s5),
                clampUnsignedByte(value.s6, minValue.s6, maxValue.s6), clampUnsignedByte(value.s7, minValue.s7, maxValue.s7),
                clampUnsignedByte(value.s8, minValue.s8, maxValue.s8), clampUnsignedByte(value.s9, minValue.s9, maxValue.s9),
                clampUnsignedByte(value.sa, minValue.sa, maxValue.sa), clampUnsignedByte(value.sb, minValue.sb, maxValue.sb),
                clampUnsignedByte(value.sc, minValue.sc, maxValue.sc), clampUnsignedByte(value.sd, minValue.sd, maxValue.sd),
                clampUnsignedByte(value.se, minValue.se, maxValue.se), clampUnsignedByte(value.sf, minValue.sf, maxValue.sf)
        );
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static UShort16 clamp(UShort16 value, UShort16 minValue, UShort16 maxValue) {
        return new UShort16(
                clampUnsignedShort(value.s0, minValue.s0, maxValue.s0), clampUnsignedShort(value.s1, minValue.s1, maxValue.s1),
                clampUnsignedShort(value.s2, minValue.s2, maxValue.s2), clampUnsignedShort(value.s3, minValue.s3, maxValue.s3),
                clampUnsignedShort(value.s4, minValue.s4, maxValue.s4), clampUnsignedShort(value.s5, minValue.s5, maxValue.s5),
                clampUnsignedShort(value.s6, minValue.s6, maxValue.s6), clampUnsignedShort(value.s7, minValue.s7, maxValue.s7),
                clampUnsignedShort(value.s8, minValue.s8, maxValue.s8), clampUnsignedShort(value.s9, minValue.s9, maxValue.s9),
                clampUnsignedShort(value.sa, minValue.sa, maxValue.sa), clampUnsignedShort(value.sb, minValue.sb, maxValue.sb),
                clampUnsignedShort(value.sc, minValue.sc, maxValue.sc), clampUnsignedShort(value.sd, minValue.sd, maxValue.sd),
                clampUnsignedShort(value.se, minValue.se, maxValue.se), clampUnsignedShort(value.sf, minValue.sf, maxValue.sf)
        );
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static UInt16 clamp(UInt16 value, UInt16 minValue, UInt16 maxValue) {
        return new UInt16(
                clampUnsignedInt(value.s0, minValue.s0, maxValue.s0), clampUnsignedInt(value.s1, minValue.s1, maxValue.s1),
                clampUnsignedInt(value.s2, minValue.s2, maxValue.s2), clampUnsignedInt(value.s3, minValue.s3, maxValue.s3),
                clampUnsignedInt(value.s4, minValue.s4, maxValue.s4), clampUnsignedInt(value.s5, minValue.s5, maxValue.s5),
                clampUnsignedInt(value.s6, minValue.s6, maxValue.s6), clampUnsignedInt(value.s7, minValue.s7, maxValue.s7),
                clampUnsignedInt(value.s8, minValue.s8, maxValue.s8), clampUnsignedInt(value.s9, minValue.s9, maxValue.s9),
                clampUnsignedInt(value.sa, minValue.sa, maxValue.sa), clampUnsignedInt(value.sb, minValue.sb, maxValue.sb),
                clampUnsignedInt(value.sc, minValue.sc, maxValue.sc), clampUnsignedInt(value.sd, minValue.sd, maxValue.sd),
                clampUnsignedInt(value.se, minValue.se, maxValue.se), clampUnsignedInt(value.sf, minValue.sf, maxValue.sf)
        );
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static ULong16 clamp(ULong16 value, ULong16 minValue, ULong16 maxValue) {
        return new ULong16(
                clampUnsignedLong(value.s0, minValue.s0, maxValue.s0), clampUnsignedLong(value.s1, minValue.s1, maxValue.s1),
                clampUnsignedLong(value.s2, minValue.s2, maxValue.s2), clampUnsignedLong(value.s3, minValue.s3, maxValue.s3),
                clampUnsignedLong(value.s4, minValue.s4, maxValue.s4), clampUnsignedLong(value.s5, minValue.s5, maxValue.s5),
                clampUnsignedLong(value.s6, minValue.s6, maxValue.s6), clampUnsignedLong(value.s7, minValue.s7, maxValue.s7),
                clampUnsignedLong(value.s8, minValue.s8, maxValue.s8), clampUnsignedLong(value.s9, minValue.s9, maxValue.s9),
                clampUnsignedLong(value.sa, minValue.sa, maxValue.sa), clampUnsignedLong(value.sb, minValue.sb, maxValue.sb),
                clampUnsignedLong(value.sc, minValue.sc, maxValue.sc), clampUnsignedLong(value.sd, minValue.sd, maxValue.sd),
                clampUnsignedLong(value.se, minValue.se, maxValue.se), clampUnsignedLong(value.sf, minValue.sf, maxValue.sf)
        );
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Double2 clamp(Double2 value, Double2 minValue, Double2 maxValue) {
        return new Double2(clamp(value.x, minValue.x, maxValue.x), clamp(value.y, minValue.y, maxValue.y));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Double2 clamp(Double2 value, double minValue, double maxValue) {
        return new Double2(clamp(value.x, minValue, maxValue), clamp(value.y, minValue, maxValue));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Double3 clamp(Double3 value, Double3 minValue, Double3 maxValue) {
        return new Double3(clamp(value.x, minValue.x, maxValue.x), clamp(value.y, minValue.y, maxValue.y), clamp(value.z, minValue.z, maxValue.z));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Double3 clamp(Double3 value, double minValue, double maxValue) {
        return new Double3(clamp(value.x, minValue, maxValue), clamp(value.y, minValue, maxValue), clamp(value.z, minValue, maxValue));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Double4 clamp(Double4 value, Double4 minValue, Double4 maxValue) {
        return new Double4(clamp(value.x, minValue.x, maxValue.x), clamp(value.y, minValue.y, maxValue.y), clamp(value.z, minValue.z, maxValue.z), clamp(value.w, minValue.w, maxValue.w));
    }

    @GPUIntrinsic(code = "clamp({0}, {1}, {2})")
    public static Double4 clamp(Double4 value, double minValue, double maxValue) {
        return new Double4(clamp(value.x, minValue, maxValue), clamp(value.y, minValue, maxValue), clamp(value.z, minValue, maxValue), clamp(value.w, minValue, maxValue));
    }

    @GPUIntrinsic(name = "mix")
    public static float mix(float left, float right, float amount) {
        return left + (right - left) * amount;
    }

    @GPUIntrinsic(name = "mix")
    public static double mix(double left, double right, double amount) {
        return left + (right - left) * amount;
    }

    @GPUIntrinsic(code = "saturate({0})")
    public static float saturate(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    @GPUIntrinsic(code = "saturate({0})")
    public static double saturate(double value) {
        return clamp(value, 0.0, 1.0);
    }

    @GPUIntrinsic(code = "saturate({0})")
    public static Float2 saturate(Float2 value) {
        return clamp(value, new Float2(0.0f), new Float2(1.0f));
    }

    @GPUIntrinsic(code = "saturate({0})")
    public static Float3 saturate(Float3 value) {
        return clamp(value, new Float3(0.0f), new Float3(1.0f));
    }

    @GPUIntrinsic(code = "saturate({0})")
    public static Float4 saturate(Float4 value) {
        return clamp(value, new Float4(0.0f), new Float4(1.0f));
    }

    @GPUIntrinsic(code = "saturate({0})")
    public static Double2 saturate(Double2 value) {
        return clamp(value, new Double2(0.0), new Double2(1.0));
    }

    @GPUIntrinsic(code = "saturate({0})")
    public static Double3 saturate(Double3 value) {
        return clamp(value, new Double3(0.0), new Double3(1.0));
    }

    @GPUIntrinsic(code = "saturate({0})")
    public static Double4 saturate(Double4 value) {
        return clamp(value, new Double4(0.0), new Double4(1.0));
    }

    @GPUIntrinsic(code = "mix({0}, {1}, {2})")
    public static Float2 mix(Float2 left, Float2 right, float amount) {
        return new Float2(mix(left.x, right.x, amount), mix(left.y, right.y, amount));
    }

    @GPUIntrinsic(code = "mix({0}, {1}, {2})")
    public static Float2 mix(Float2 left, Float2 right, Float2 amount) {
        return new Float2(mix(left.x, right.x, amount.x), mix(left.y, right.y, amount.y));
    }

    @GPUIntrinsic(code = "mix({0}, {1}, {2})")
    public static Float3 mix(Float3 left, Float3 right, float amount) {
        return new Float3(mix(left.x, right.x, amount), mix(left.y, right.y, amount), mix(left.z, right.z, amount));
    }

    @GPUIntrinsic(code = "mix({0}, {1}, {2})")
    public static Float3 mix(Float3 left, Float3 right, Float3 amount) {
        return new Float3(mix(left.x, right.x, amount.x), mix(left.y, right.y, amount.y), mix(left.z, right.z, amount.z));
    }

    @GPUIntrinsic(code = "mix({0}, {1}, {2})")
    public static Float4 mix(Float4 left, Float4 right, float amount) {
        return new Float4(mix(left.x, right.x, amount), mix(left.y, right.y, amount), mix(left.z, right.z, amount), mix(left.w, right.w, amount));
    }

    @GPUIntrinsic(code = "mix({0}, {1}, {2})")
    public static Float4 mix(Float4 left, Float4 right, Float4 amount) {
        return new Float4(mix(left.x, right.x, amount.x), mix(left.y, right.y, amount.y), mix(left.z, right.z, amount.z), mix(left.w, right.w, amount.w));
    }

    @GPUIntrinsic(code = "mix({0}, {1}, {2})")
    public static Double2 mix(Double2 left, Double2 right, double amount) {
        return new Double2(mix(left.x, right.x, amount), mix(left.y, right.y, amount));
    }

    @GPUIntrinsic(code = "mix({0}, {1}, {2})")
    public static Double2 mix(Double2 left, Double2 right, Double2 amount) {
        return new Double2(mix(left.x, right.x, amount.x), mix(left.y, right.y, amount.y));
    }

    @GPUIntrinsic(code = "mix({0}, {1}, {2})")
    public static Double3 mix(Double3 left, Double3 right, double amount) {
        return new Double3(mix(left.x, right.x, amount), mix(left.y, right.y, amount), mix(left.z, right.z, amount));
    }

    @GPUIntrinsic(code = "mix({0}, {1}, {2})")
    public static Double3 mix(Double3 left, Double3 right, Double3 amount) {
        return new Double3(mix(left.x, right.x, amount.x), mix(left.y, right.y, amount.y), mix(left.z, right.z, amount.z));
    }

    @GPUIntrinsic(code = "mix({0}, {1}, {2})")
    public static Double4 mix(Double4 left, Double4 right, double amount) {
        return new Double4(mix(left.x, right.x, amount), mix(left.y, right.y, amount), mix(left.z, right.z, amount), mix(left.w, right.w, amount));
    }

    @GPUIntrinsic(code = "mix({0}, {1}, {2})")
    public static Double4 mix(Double4 left, Double4 right, Double4 amount) {
        return new Double4(mix(left.x, right.x, amount.x), mix(left.y, right.y, amount.y), mix(left.z, right.z, amount.z), mix(left.w, right.w, amount.w));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static Int2 min(Int2 left, Int2 right) {
        return new Int2(min(left.x, right.x), min(left.y, right.y));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static Byte2 min(Byte2 left, Byte2 right) {
        return new Byte2((byte) min(left.x, right.x), (byte) min(left.y, right.y));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static Byte3 min(Byte3 left, Byte3 right) {
        return new Byte3((byte) min(left.x, right.x), (byte) min(left.y, right.y), (byte) min(left.z, right.z));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static Byte4 min(Byte4 left, Byte4 right) {
        return new Byte4((byte) min(left.x, right.x), (byte) min(left.y, right.y), (byte) min(left.z, right.z), (byte) min(left.w, right.w));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static Short2 min(Short2 left, Short2 right) {
        return new Short2((short) min(left.x, right.x), (short) min(left.y, right.y));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static Short3 min(Short3 left, Short3 right) {
        return new Short3((short) min(left.x, right.x), (short) min(left.y, right.y), (short) min(left.z, right.z));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static Short4 min(Short4 left, Short4 right) {
        return new Short4((short) min(left.x, right.x), (short) min(left.y, right.y), (short) min(left.z, right.z), (short) min(left.w, right.w));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static Int3 min(Int3 left, Int3 right) {
        return new Int3(min(left.x, right.x), min(left.y, right.y), min(left.z, right.z));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static Int4 min(Int4 left, Int4 right) {
        return new Int4(min(left.x, right.x), min(left.y, right.y), min(left.z, right.z), min(left.w, right.w));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static Long2 min(Long2 left, Long2 right) {
        return new Long2(min(left.x, right.x), min(left.y, right.y));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static Long3 min(Long3 left, Long3 right) {
        return new Long3(min(left.x, right.x), min(left.y, right.y), min(left.z, right.z));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static Long4 min(Long4 left, Long4 right) {
        return new Long4(min(left.x, right.x), min(left.y, right.y), min(left.z, right.z), min(left.w, right.w));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static UInt2 min(UInt2 left, UInt2 right) {
        return new UInt2(minUnsignedInt(left.x, right.x), minUnsignedInt(left.y, right.y));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static UByte2 min(UByte2 left, UByte2 right) {
        return new UByte2(minUnsignedByte(left.x, right.x), minUnsignedByte(left.y, right.y));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static UShort2 min(UShort2 left, UShort2 right) {
        return new UShort2(minUnsignedShort(left.x, right.x), minUnsignedShort(left.y, right.y));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static UInt3 min(UInt3 left, UInt3 right) {
        return new UInt3(minUnsignedInt(left.x, right.x), minUnsignedInt(left.y, right.y), minUnsignedInt(left.z, right.z));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static UByte3 min(UByte3 left, UByte3 right) {
        return new UByte3(minUnsignedByte(left.x, right.x), minUnsignedByte(left.y, right.y), minUnsignedByte(left.z, right.z));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static UShort3 min(UShort3 left, UShort3 right) {
        return new UShort3(minUnsignedShort(left.x, right.x), minUnsignedShort(left.y, right.y), minUnsignedShort(left.z, right.z));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static UInt4 min(UInt4 left, UInt4 right) {
        return new UInt4(minUnsignedInt(left.x, right.x), minUnsignedInt(left.y, right.y), minUnsignedInt(left.z, right.z), minUnsignedInt(left.w, right.w));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static UByte4 min(UByte4 left, UByte4 right) {
        return new UByte4(minUnsignedByte(left.x, right.x), minUnsignedByte(left.y, right.y), minUnsignedByte(left.z, right.z), minUnsignedByte(left.w, right.w));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static UShort4 min(UShort4 left, UShort4 right) {
        return new UShort4(minUnsignedShort(left.x, right.x), minUnsignedShort(left.y, right.y), minUnsignedShort(left.z, right.z), minUnsignedShort(left.w, right.w));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static ULong2 min(ULong2 left, ULong2 right) {
        return new ULong2(minUnsignedLong(left.x, right.x), minUnsignedLong(left.y, right.y));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static ULong3 min(ULong3 left, ULong3 right) {
        return new ULong3(minUnsignedLong(left.x, right.x), minUnsignedLong(left.y, right.y), minUnsignedLong(left.z, right.z));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static ULong4 min(ULong4 left, ULong4 right) {
        return new ULong4(minUnsignedLong(left.x, right.x), minUnsignedLong(left.y, right.y), minUnsignedLong(left.z, right.z), minUnsignedLong(left.w, right.w));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static UByte8 min(UByte8 left, UByte8 right) {
        return new UByte8(minUnsignedByte(left.s0, right.s0), minUnsignedByte(left.s1, right.s1), minUnsignedByte(left.s2, right.s2), minUnsignedByte(left.s3, right.s3), minUnsignedByte(left.s4, right.s4), minUnsignedByte(left.s5, right.s5), minUnsignedByte(left.s6, right.s6), minUnsignedByte(left.s7, right.s7));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static UShort8 min(UShort8 left, UShort8 right) {
        return new UShort8(minUnsignedShort(left.s0, right.s0), minUnsignedShort(left.s1, right.s1), minUnsignedShort(left.s2, right.s2), minUnsignedShort(left.s3, right.s3), minUnsignedShort(left.s4, right.s4), minUnsignedShort(left.s5, right.s5), minUnsignedShort(left.s6, right.s6), minUnsignedShort(left.s7, right.s7));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static UInt8 min(UInt8 left, UInt8 right) {
        return new UInt8(minUnsignedInt(left.s0, right.s0), minUnsignedInt(left.s1, right.s1), minUnsignedInt(left.s2, right.s2), minUnsignedInt(left.s3, right.s3), minUnsignedInt(left.s4, right.s4), minUnsignedInt(left.s5, right.s5), minUnsignedInt(left.s6, right.s6), minUnsignedInt(left.s7, right.s7));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static ULong8 min(ULong8 left, ULong8 right) {
        return new ULong8(minUnsignedLong(left.s0, right.s0), minUnsignedLong(left.s1, right.s1), minUnsignedLong(left.s2, right.s2), minUnsignedLong(left.s3, right.s3), minUnsignedLong(left.s4, right.s4), minUnsignedLong(left.s5, right.s5), minUnsignedLong(left.s6, right.s6), minUnsignedLong(left.s7, right.s7));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static UByte16 min(UByte16 left, UByte16 right) {
        return new UByte16(minUnsignedByte(left.s0, right.s0), minUnsignedByte(left.s1, right.s1), minUnsignedByte(left.s2, right.s2), minUnsignedByte(left.s3, right.s3), minUnsignedByte(left.s4, right.s4), minUnsignedByte(left.s5, right.s5), minUnsignedByte(left.s6, right.s6), minUnsignedByte(left.s7, right.s7), minUnsignedByte(left.s8, right.s8), minUnsignedByte(left.s9, right.s9), minUnsignedByte(left.sa, right.sa), minUnsignedByte(left.sb, right.sb), minUnsignedByte(left.sc, right.sc), minUnsignedByte(left.sd, right.sd), minUnsignedByte(left.se, right.se), minUnsignedByte(left.sf, right.sf));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static UShort16 min(UShort16 left, UShort16 right) {
        return new UShort16(minUnsignedShort(left.s0, right.s0), minUnsignedShort(left.s1, right.s1), minUnsignedShort(left.s2, right.s2), minUnsignedShort(left.s3, right.s3), minUnsignedShort(left.s4, right.s4), minUnsignedShort(left.s5, right.s5), minUnsignedShort(left.s6, right.s6), minUnsignedShort(left.s7, right.s7), minUnsignedShort(left.s8, right.s8), minUnsignedShort(left.s9, right.s9), minUnsignedShort(left.sa, right.sa), minUnsignedShort(left.sb, right.sb), minUnsignedShort(left.sc, right.sc), minUnsignedShort(left.sd, right.sd), minUnsignedShort(left.se, right.se), minUnsignedShort(left.sf, right.sf));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static UInt16 min(UInt16 left, UInt16 right) {
        return new UInt16(minUnsignedInt(left.s0, right.s0), minUnsignedInt(left.s1, right.s1), minUnsignedInt(left.s2, right.s2), minUnsignedInt(left.s3, right.s3), minUnsignedInt(left.s4, right.s4), minUnsignedInt(left.s5, right.s5), minUnsignedInt(left.s6, right.s6), minUnsignedInt(left.s7, right.s7), minUnsignedInt(left.s8, right.s8), minUnsignedInt(left.s9, right.s9), minUnsignedInt(left.sa, right.sa), minUnsignedInt(left.sb, right.sb), minUnsignedInt(left.sc, right.sc), minUnsignedInt(left.sd, right.sd), minUnsignedInt(left.se, right.se), minUnsignedInt(left.sf, right.sf));
    }

    @GPUIntrinsic(code = "min({0}, {1})")
    public static ULong16 min(ULong16 left, ULong16 right) {
        return new ULong16(minUnsignedLong(left.s0, right.s0), minUnsignedLong(left.s1, right.s1), minUnsignedLong(left.s2, right.s2), minUnsignedLong(left.s3, right.s3), minUnsignedLong(left.s4, right.s4), minUnsignedLong(left.s5, right.s5), minUnsignedLong(left.s6, right.s6), minUnsignedLong(left.s7, right.s7), minUnsignedLong(left.s8, right.s8), minUnsignedLong(left.s9, right.s9), minUnsignedLong(left.sa, right.sa), minUnsignedLong(left.sb, right.sb), minUnsignedLong(left.sc, right.sc), minUnsignedLong(left.sd, right.sd), minUnsignedLong(left.se, right.se), minUnsignedLong(left.sf, right.sf));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static Int2 max(Int2 left, Int2 right) {
        return new Int2(max(left.x, right.x), max(left.y, right.y));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static Byte2 max(Byte2 left, Byte2 right) {
        return new Byte2((byte) max(left.x, right.x), (byte) max(left.y, right.y));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static Byte3 max(Byte3 left, Byte3 right) {
        return new Byte3((byte) max(left.x, right.x), (byte) max(left.y, right.y), (byte) max(left.z, right.z));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static Byte4 max(Byte4 left, Byte4 right) {
        return new Byte4((byte) max(left.x, right.x), (byte) max(left.y, right.y), (byte) max(left.z, right.z), (byte) max(left.w, right.w));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static Short2 max(Short2 left, Short2 right) {
        return new Short2((short) max(left.x, right.x), (short) max(left.y, right.y));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static Short3 max(Short3 left, Short3 right) {
        return new Short3((short) max(left.x, right.x), (short) max(left.y, right.y), (short) max(left.z, right.z));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static Short4 max(Short4 left, Short4 right) {
        return new Short4((short) max(left.x, right.x), (short) max(left.y, right.y), (short) max(left.z, right.z), (short) max(left.w, right.w));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static Int3 max(Int3 left, Int3 right) {
        return new Int3(max(left.x, right.x), max(left.y, right.y), max(left.z, right.z));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static Int4 max(Int4 left, Int4 right) {
        return new Int4(max(left.x, right.x), max(left.y, right.y), max(left.z, right.z), max(left.w, right.w));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static Long2 max(Long2 left, Long2 right) {
        return new Long2(max(left.x, right.x), max(left.y, right.y));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static Long3 max(Long3 left, Long3 right) {
        return new Long3(max(left.x, right.x), max(left.y, right.y), max(left.z, right.z));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static Long4 max(Long4 left, Long4 right) {
        return new Long4(max(left.x, right.x), max(left.y, right.y), max(left.z, right.z), max(left.w, right.w));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static UInt2 max(UInt2 left, UInt2 right) {
        return new UInt2(maxUnsignedInt(left.x, right.x), maxUnsignedInt(left.y, right.y));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static UByte2 max(UByte2 left, UByte2 right) {
        return new UByte2(maxUnsignedByte(left.x, right.x), maxUnsignedByte(left.y, right.y));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static UShort2 max(UShort2 left, UShort2 right) {
        return new UShort2(maxUnsignedShort(left.x, right.x), maxUnsignedShort(left.y, right.y));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static UInt3 max(UInt3 left, UInt3 right) {
        return new UInt3(maxUnsignedInt(left.x, right.x), maxUnsignedInt(left.y, right.y), maxUnsignedInt(left.z, right.z));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static UByte3 max(UByte3 left, UByte3 right) {
        return new UByte3(maxUnsignedByte(left.x, right.x), maxUnsignedByte(left.y, right.y), maxUnsignedByte(left.z, right.z));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static UShort3 max(UShort3 left, UShort3 right) {
        return new UShort3(maxUnsignedShort(left.x, right.x), maxUnsignedShort(left.y, right.y), maxUnsignedShort(left.z, right.z));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static UInt4 max(UInt4 left, UInt4 right) {
        return new UInt4(maxUnsignedInt(left.x, right.x), maxUnsignedInt(left.y, right.y), maxUnsignedInt(left.z, right.z), maxUnsignedInt(left.w, right.w));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static UByte4 max(UByte4 left, UByte4 right) {
        return new UByte4(maxUnsignedByte(left.x, right.x), maxUnsignedByte(left.y, right.y), maxUnsignedByte(left.z, right.z), maxUnsignedByte(left.w, right.w));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static UShort4 max(UShort4 left, UShort4 right) {
        return new UShort4(maxUnsignedShort(left.x, right.x), maxUnsignedShort(left.y, right.y), maxUnsignedShort(left.z, right.z), maxUnsignedShort(left.w, right.w));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static ULong2 max(ULong2 left, ULong2 right) {
        return new ULong2(maxUnsignedLong(left.x, right.x), maxUnsignedLong(left.y, right.y));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static ULong3 max(ULong3 left, ULong3 right) {
        return new ULong3(maxUnsignedLong(left.x, right.x), maxUnsignedLong(left.y, right.y), maxUnsignedLong(left.z, right.z));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static ULong4 max(ULong4 left, ULong4 right) {
        return new ULong4(maxUnsignedLong(left.x, right.x), maxUnsignedLong(left.y, right.y), maxUnsignedLong(left.z, right.z), maxUnsignedLong(left.w, right.w));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static UByte8 max(UByte8 left, UByte8 right) {
        return new UByte8(maxUnsignedByte(left.s0, right.s0), maxUnsignedByte(left.s1, right.s1), maxUnsignedByte(left.s2, right.s2), maxUnsignedByte(left.s3, right.s3), maxUnsignedByte(left.s4, right.s4), maxUnsignedByte(left.s5, right.s5), maxUnsignedByte(left.s6, right.s6), maxUnsignedByte(left.s7, right.s7));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static UShort8 max(UShort8 left, UShort8 right) {
        return new UShort8(maxUnsignedShort(left.s0, right.s0), maxUnsignedShort(left.s1, right.s1), maxUnsignedShort(left.s2, right.s2), maxUnsignedShort(left.s3, right.s3), maxUnsignedShort(left.s4, right.s4), maxUnsignedShort(left.s5, right.s5), maxUnsignedShort(left.s6, right.s6), maxUnsignedShort(left.s7, right.s7));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static UInt8 max(UInt8 left, UInt8 right) {
        return new UInt8(maxUnsignedInt(left.s0, right.s0), maxUnsignedInt(left.s1, right.s1), maxUnsignedInt(left.s2, right.s2), maxUnsignedInt(left.s3, right.s3), maxUnsignedInt(left.s4, right.s4), maxUnsignedInt(left.s5, right.s5), maxUnsignedInt(left.s6, right.s6), maxUnsignedInt(left.s7, right.s7));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static ULong8 max(ULong8 left, ULong8 right) {
        return new ULong8(maxUnsignedLong(left.s0, right.s0), maxUnsignedLong(left.s1, right.s1), maxUnsignedLong(left.s2, right.s2), maxUnsignedLong(left.s3, right.s3), maxUnsignedLong(left.s4, right.s4), maxUnsignedLong(left.s5, right.s5), maxUnsignedLong(left.s6, right.s6), maxUnsignedLong(left.s7, right.s7));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static UByte16 max(UByte16 left, UByte16 right) {
        return new UByte16(maxUnsignedByte(left.s0, right.s0), maxUnsignedByte(left.s1, right.s1), maxUnsignedByte(left.s2, right.s2), maxUnsignedByte(left.s3, right.s3), maxUnsignedByte(left.s4, right.s4), maxUnsignedByte(left.s5, right.s5), maxUnsignedByte(left.s6, right.s6), maxUnsignedByte(left.s7, right.s7), maxUnsignedByte(left.s8, right.s8), maxUnsignedByte(left.s9, right.s9), maxUnsignedByte(left.sa, right.sa), maxUnsignedByte(left.sb, right.sb), maxUnsignedByte(left.sc, right.sc), maxUnsignedByte(left.sd, right.sd), maxUnsignedByte(left.se, right.se), maxUnsignedByte(left.sf, right.sf));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static UShort16 max(UShort16 left, UShort16 right) {
        return new UShort16(maxUnsignedShort(left.s0, right.s0), maxUnsignedShort(left.s1, right.s1), maxUnsignedShort(left.s2, right.s2), maxUnsignedShort(left.s3, right.s3), maxUnsignedShort(left.s4, right.s4), maxUnsignedShort(left.s5, right.s5), maxUnsignedShort(left.s6, right.s6), maxUnsignedShort(left.s7, right.s7), maxUnsignedShort(left.s8, right.s8), maxUnsignedShort(left.s9, right.s9), maxUnsignedShort(left.sa, right.sa), maxUnsignedShort(left.sb, right.sb), maxUnsignedShort(left.sc, right.sc), maxUnsignedShort(left.sd, right.sd), maxUnsignedShort(left.se, right.se), maxUnsignedShort(left.sf, right.sf));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static UInt16 max(UInt16 left, UInt16 right) {
        return new UInt16(maxUnsignedInt(left.s0, right.s0), maxUnsignedInt(left.s1, right.s1), maxUnsignedInt(left.s2, right.s2), maxUnsignedInt(left.s3, right.s3), maxUnsignedInt(left.s4, right.s4), maxUnsignedInt(left.s5, right.s5), maxUnsignedInt(left.s6, right.s6), maxUnsignedInt(left.s7, right.s7), maxUnsignedInt(left.s8, right.s8), maxUnsignedInt(left.s9, right.s9), maxUnsignedInt(left.sa, right.sa), maxUnsignedInt(left.sb, right.sb), maxUnsignedInt(left.sc, right.sc), maxUnsignedInt(left.sd, right.sd), maxUnsignedInt(left.se, right.se), maxUnsignedInt(left.sf, right.sf));
    }

    @GPUIntrinsic(code = "max({0}, {1})")
    public static ULong16 max(ULong16 left, ULong16 right) {
        return new ULong16(maxUnsignedLong(left.s0, right.s0), maxUnsignedLong(left.s1, right.s1), maxUnsignedLong(left.s2, right.s2), maxUnsignedLong(left.s3, right.s3), maxUnsignedLong(left.s4, right.s4), maxUnsignedLong(left.s5, right.s5), maxUnsignedLong(left.s6, right.s6), maxUnsignedLong(left.s7, right.s7), maxUnsignedLong(left.s8, right.s8), maxUnsignedLong(left.s9, right.s9), maxUnsignedLong(left.sa, right.sa), maxUnsignedLong(left.sb, right.sb), maxUnsignedLong(left.sc, right.sc), maxUnsignedLong(left.sd, right.sd), maxUnsignedLong(left.se, right.se), maxUnsignedLong(left.sf, right.sf));
    }

    @GPUIntrinsic(name = "degrees")
    public static float degrees(float radians) {
        return (float) Math.toDegrees(radians);
    }

    @GPUIntrinsic(code = "degrees({0})")
    public static Float2 degrees(Float2 radians) {
        return new Float2(degrees(radians.x), degrees(radians.y));
    }

    @GPUIntrinsic(code = "degrees({0})")
    public static Float3 degrees(Float3 radians) {
        return new Float3(degrees(radians.x), degrees(radians.y), degrees(radians.z));
    }

    @GPUIntrinsic(code = "degrees({0})")
    public static Float4 degrees(Float4 radians) {
        return new Float4(degrees(radians.x), degrees(radians.y), degrees(radians.z), degrees(radians.w));
    }

    @GPUIntrinsic(name = "degrees")
    public static double degrees(double radians) {
        return Math.toDegrees(radians);
    }

    @GPUIntrinsic(code = "degrees({0})")
    public static Double2 degrees(Double2 radians) {
        return new Double2(degrees(radians.x), degrees(radians.y));
    }

    @GPUIntrinsic(code = "degrees({0})")
    public static Double3 degrees(Double3 radians) {
        return new Double3(degrees(radians.x), degrees(radians.y), degrees(radians.z));
    }

    @GPUIntrinsic(code = "degrees({0})")
    public static Double4 degrees(Double4 radians) {
        return new Double4(degrees(radians.x), degrees(radians.y), degrees(radians.z), degrees(radians.w));
    }

    @GPUIntrinsic(name = "radians")
    public static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    @GPUIntrinsic(code = "radians({0})")
    public static Float2 radians(Float2 degrees) {
        return new Float2(radians(degrees.x), radians(degrees.y));
    }

    @GPUIntrinsic(code = "radians({0})")
    public static Float3 radians(Float3 degrees) {
        return new Float3(radians(degrees.x), radians(degrees.y), radians(degrees.z));
    }

    @GPUIntrinsic(code = "radians({0})")
    public static Float4 radians(Float4 degrees) {
        return new Float4(radians(degrees.x), radians(degrees.y), radians(degrees.z), radians(degrees.w));
    }

    @GPUIntrinsic(name = "radians")
    public static double radians(double degrees) {
        return Math.toRadians(degrees);
    }

    @GPUIntrinsic(code = "radians({0})")
    public static Double2 radians(Double2 degrees) {
        return new Double2(radians(degrees.x), radians(degrees.y));
    }

    @GPUIntrinsic(code = "radians({0})")
    public static Double3 radians(Double3 degrees) {
        return new Double3(radians(degrees.x), radians(degrees.y), radians(degrees.z));
    }

    @GPUIntrinsic(code = "radians({0})")
    public static Double4 radians(Double4 degrees) {
        return new Double4(radians(degrees.x), radians(degrees.y), radians(degrees.z), radians(degrees.w));
    }

    @GPUIntrinsic(name = "copysign")
    public static float copysign(float magnitude, float sign) {
        return Math.copySign(magnitude, sign);
    }

    @GPUIntrinsic(code = "copysign({0}, {1})")
    public static Float2 copysign(Float2 magnitude, Float2 sign) {
        return new Float2(copysign(magnitude.x, sign.x), copysign(magnitude.y, sign.y));
    }

    @GPUIntrinsic(code = "copysign({0}, {1})")
    public static Float3 copysign(Float3 magnitude, Float3 sign) {
        return new Float3(copysign(magnitude.x, sign.x), copysign(magnitude.y, sign.y), copysign(magnitude.z, sign.z));
    }

    @GPUIntrinsic(code = "copysign({0}, {1})")
    public static Float4 copysign(Float4 magnitude, Float4 sign) {
        return new Float4(copysign(magnitude.x, sign.x), copysign(magnitude.y, sign.y), copysign(magnitude.z, sign.z), copysign(magnitude.w, sign.w));
    }

    @GPUIntrinsic(name = "copysign")
    public static double copysign(double magnitude, double sign) {
        return Math.copySign(magnitude, sign);
    }

    @GPUIntrinsic(code = "copysign({0}, {1})")
    public static Double2 copysign(Double2 magnitude, Double2 sign) {
        return new Double2(copysign(magnitude.x, sign.x), copysign(magnitude.y, sign.y));
    }

    @GPUIntrinsic(code = "copysign({0}, {1})")
    public static Double3 copysign(Double3 magnitude, Double3 sign) {
        return new Double3(copysign(magnitude.x, sign.x), copysign(magnitude.y, sign.y), copysign(magnitude.z, sign.z));
    }

    @GPUIntrinsic(code = "copysign({0}, {1})")
    public static Double4 copysign(Double4 magnitude, Double4 sign) {
        return new Double4(copysign(magnitude.x, sign.x), copysign(magnitude.y, sign.y), copysign(magnitude.z, sign.z), copysign(magnitude.w, sign.w));
    }

    @GPUIntrinsic(name = "step")
    public static float step(float edge, float value) {
        return value < edge ? 0.0f : 1.0f;
    }

    @GPUIntrinsic(name = "step")
    public static double step(double edge, double value) {
        return value < edge ? 0.0 : 1.0;
    }

    @GPUIntrinsic(code = "step({0}, {1})")
    public static Float2 step(Float2 edge, Float2 value) {
        return new Float2(step(edge.x, value.x), step(edge.y, value.y));
    }

    @GPUIntrinsic(code = "step({0}, {1})")
    public static Float2 step(float edge, Float2 value) {
        return new Float2(step(edge, value.x), step(edge, value.y));
    }

    @GPUIntrinsic(code = "step({0}, {1})")
    public static Float3 step(Float3 edge, Float3 value) {
        return new Float3(step(edge.x, value.x), step(edge.y, value.y), step(edge.z, value.z));
    }

    @GPUIntrinsic(code = "step({0}, {1})")
    public static Float3 step(float edge, Float3 value) {
        return new Float3(step(edge, value.x), step(edge, value.y), step(edge, value.z));
    }

    @GPUIntrinsic(code = "step({0}, {1})")
    public static Float4 step(Float4 edge, Float4 value) {
        return new Float4(step(edge.x, value.x), step(edge.y, value.y), step(edge.z, value.z), step(edge.w, value.w));
    }

    @GPUIntrinsic(code = "step({0}, {1})")
    public static Float4 step(float edge, Float4 value) {
        return new Float4(step(edge, value.x), step(edge, value.y), step(edge, value.z), step(edge, value.w));
    }

    @GPUIntrinsic(code = "step({0}, {1})")
    public static Double2 step(Double2 edge, Double2 value) {
        return new Double2(step(edge.x, value.x), step(edge.y, value.y));
    }

    @GPUIntrinsic(code = "step({0}, {1})")
    public static Double2 step(double edge, Double2 value) {
        return new Double2(step(edge, value.x), step(edge, value.y));
    }

    @GPUIntrinsic(code = "step({0}, {1})")
    public static Double3 step(Double3 edge, Double3 value) {
        return new Double3(step(edge.x, value.x), step(edge.y, value.y), step(edge.z, value.z));
    }

    @GPUIntrinsic(code = "step({0}, {1})")
    public static Double3 step(double edge, Double3 value) {
        return new Double3(step(edge, value.x), step(edge, value.y), step(edge, value.z));
    }

    @GPUIntrinsic(code = "step({0}, {1})")
    public static Double4 step(Double4 edge, Double4 value) {
        return new Double4(step(edge.x, value.x), step(edge.y, value.y), step(edge.z, value.z), step(edge.w, value.w));
    }

    @GPUIntrinsic(code = "step({0}, {1})")
    public static Double4 step(double edge, Double4 value) {
        return new Double4(step(edge, value.x), step(edge, value.y), step(edge, value.z), step(edge, value.w));
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

    @GPUIntrinsic(code = "smoothstep({0}, {1}, {2})")
    public static Float2 smoothstep(Float2 edge0, Float2 edge1, Float2 value) {
        return new Float2(smoothstep(edge0.x, edge1.x, value.x), smoothstep(edge0.y, edge1.y, value.y));
    }

    @GPUIntrinsic(code = "smoothstep({0}, {1}, {2})")
    public static Float2 smoothstep(float edge0, float edge1, Float2 value) {
        return new Float2(smoothstep(edge0, edge1, value.x), smoothstep(edge0, edge1, value.y));
    }

    @GPUIntrinsic(code = "smoothstep({0}, {1}, {2})")
    public static Float3 smoothstep(Float3 edge0, Float3 edge1, Float3 value) {
        return new Float3(smoothstep(edge0.x, edge1.x, value.x), smoothstep(edge0.y, edge1.y, value.y), smoothstep(edge0.z, edge1.z, value.z));
    }

    @GPUIntrinsic(code = "smoothstep({0}, {1}, {2})")
    public static Float3 smoothstep(float edge0, float edge1, Float3 value) {
        return new Float3(smoothstep(edge0, edge1, value.x), smoothstep(edge0, edge1, value.y), smoothstep(edge0, edge1, value.z));
    }

    @GPUIntrinsic(code = "smoothstep({0}, {1}, {2})")
    public static Float4 smoothstep(Float4 edge0, Float4 edge1, Float4 value) {
        return new Float4(smoothstep(edge0.x, edge1.x, value.x), smoothstep(edge0.y, edge1.y, value.y), smoothstep(edge0.z, edge1.z, value.z), smoothstep(edge0.w, edge1.w, value.w));
    }

    @GPUIntrinsic(code = "smoothstep({0}, {1}, {2})")
    public static Float4 smoothstep(float edge0, float edge1, Float4 value) {
        return new Float4(smoothstep(edge0, edge1, value.x), smoothstep(edge0, edge1, value.y), smoothstep(edge0, edge1, value.z), smoothstep(edge0, edge1, value.w));
    }

    @GPUIntrinsic(code = "smoothstep({0}, {1}, {2})")
    public static Double2 smoothstep(Double2 edge0, Double2 edge1, Double2 value) {
        return new Double2(smoothstep(edge0.x, edge1.x, value.x), smoothstep(edge0.y, edge1.y, value.y));
    }

    @GPUIntrinsic(code = "smoothstep({0}, {1}, {2})")
    public static Double2 smoothstep(double edge0, double edge1, Double2 value) {
        return new Double2(smoothstep(edge0, edge1, value.x), smoothstep(edge0, edge1, value.y));
    }

    @GPUIntrinsic(code = "smoothstep({0}, {1}, {2})")
    public static Double3 smoothstep(Double3 edge0, Double3 edge1, Double3 value) {
        return new Double3(smoothstep(edge0.x, edge1.x, value.x), smoothstep(edge0.y, edge1.y, value.y), smoothstep(edge0.z, edge1.z, value.z));
    }

    @GPUIntrinsic(code = "smoothstep({0}, {1}, {2})")
    public static Double3 smoothstep(double edge0, double edge1, Double3 value) {
        return new Double3(smoothstep(edge0, edge1, value.x), smoothstep(edge0, edge1, value.y), smoothstep(edge0, edge1, value.z));
    }

    @GPUIntrinsic(code = "smoothstep({0}, {1}, {2})")
    public static Double4 smoothstep(Double4 edge0, Double4 edge1, Double4 value) {
        return new Double4(smoothstep(edge0.x, edge1.x, value.x), smoothstep(edge0.y, edge1.y, value.y), smoothstep(edge0.z, edge1.z, value.z), smoothstep(edge0.w, edge1.w, value.w));
    }

    @GPUIntrinsic(code = "smoothstep({0}, {1}, {2})")
    public static Double4 smoothstep(double edge0, double edge1, Double4 value) {
        return new Double4(smoothstep(edge0, edge1, value.x), smoothstep(edge0, edge1, value.y), smoothstep(edge0, edge1, value.z), smoothstep(edge0, edge1, value.w));
    }

    @GPUIntrinsic(name = "hypot")
    public static float length(float x, float y) {
        return (float) Math.hypot(x, y);
    }

    @GPUIntrinsic(name = "hypot")
    public static float hypot(float x, float y) {
        return (float) Math.hypot(x, y);
    }

    @GPUIntrinsic(name = "hypot")
    public static double length(double x, double y) {
        return Math.hypot(x, y);
    }

    @GPUIntrinsic(name = "hypot")
    public static double hypot(double x, double y) {
        return Math.hypot(x, y);
    }

    @GPUIntrinsic(code = "hypot({0}, {1})")
    public static Float2 hypot(Float2 x, Float2 y) {
        return new Float2(hypot(x.x, y.x), hypot(x.y, y.y));
    }

    @GPUIntrinsic(code = "hypot({0}, {1})")
    public static Float3 hypot(Float3 x, Float3 y) {
        return new Float3(hypot(x.x, y.x), hypot(x.y, y.y), hypot(x.z, y.z));
    }

    @GPUIntrinsic(code = "hypot({0}, {1})")
    public static Float4 hypot(Float4 x, Float4 y) {
        return new Float4(hypot(x.x, y.x), hypot(x.y, y.y), hypot(x.z, y.z), hypot(x.w, y.w));
    }

    @GPUIntrinsic(code = "hypot({0}, {1})")
    public static Double2 hypot(Double2 x, Double2 y) {
        return new Double2(hypot(x.x, y.x), hypot(x.y, y.y));
    }

    @GPUIntrinsic(code = "hypot({0}, {1})")
    public static Double3 hypot(Double3 x, Double3 y) {
        return new Double3(hypot(x.x, y.x), hypot(x.y, y.y), hypot(x.z, y.z));
    }

    @GPUIntrinsic(code = "hypot({0}, {1})")
    public static Double4 hypot(Double4 x, Double4 y) {
        return new Double4(hypot(x.x, y.x), hypot(x.y, y.y), hypot(x.z, y.z), hypot(x.w, y.w));
    }

    @GPUIntrinsic(code = "sqrt(dot({0}, {0}))")
    public static float length(Float2 value) {
        return (float) Math.sqrt(value.x * value.x + value.y * value.y);
    }

    @GPUIntrinsic(code = "sqrt(dot({0}, {0}))")
    public static float length(Float3 value) {
        return (float) Math.sqrt(value.x * value.x + value.y * value.y + value.z * value.z);
    }

    @GPUIntrinsic(code = "sqrt(dot({0}, {0}))")
    public static float length(Float4 value) {
        return (float) Math.sqrt(value.x * value.x + value.y * value.y + value.z * value.z + value.w * value.w);
    }

    @GPUIntrinsic(code = "sqrt(dot({0}, {0}))")
    public static double length(Double2 value) {
        return Math.sqrt(value.x * value.x + value.y * value.y);
    }

    @GPUIntrinsic(code = "sqrt(dot({0}, {0}))")
    public static double length(Double3 value) {
        return Math.sqrt(value.x * value.x + value.y * value.y + value.z * value.z);
    }

    @GPUIntrinsic(code = "sqrt(dot({0}, {0}))")
    public static double length(Double4 value) {
        return Math.sqrt(value.x * value.x + value.y * value.y + value.z * value.z + value.w * value.w);
    }

    @GPUIntrinsic(name = "dot")
    public static float dot(Float2 left, Float2 right) {
        return left.x * right.x + left.y * right.y;
    }

    @GPUIntrinsic(name = "dot")
    public static float dot(Float3 left, Float3 right) {
        return left.x * right.x + left.y * right.y + left.z * right.z;
    }

    @GPUIntrinsic(name = "dot")
    public static float dot(Float4 left, Float4 right) {
        return left.x * right.x + left.y * right.y + left.z * right.z + left.w * right.w;
    }

    @GPUIntrinsic(name = "dot")
    public static double dot(Double2 left, Double2 right) {
        return left.x * right.x + left.y * right.y;
    }

    @GPUIntrinsic(name = "dot")
    public static double dot(Double3 left, Double3 right) {
        return left.x * right.x + left.y * right.y + left.z * right.z;
    }

    @GPUIntrinsic(name = "dot")
    public static double dot(Double4 left, Double4 right) {
        return left.x * right.x + left.y * right.y + left.z * right.z + left.w * right.w;
    }

    @GPUIntrinsic(code = "length(({0}) - ({1}))")
    public static float distance(Float2 left, Float2 right) {
        return length(new Float2(left.x - right.x, left.y - right.y));
    }

    @GPUIntrinsic(code = "length(({0}) - ({1}))")
    public static float distance(Float3 left, Float3 right) {
        return length(new Float3(left.x - right.x, left.y - right.y, left.z - right.z));
    }

    @GPUIntrinsic(code = "length(({0}) - ({1}))")
    public static float distance(Float4 left, Float4 right) {
        return length(new Float4(left.x - right.x, left.y - right.y, left.z - right.z, left.w - right.w));
    }

    @GPUIntrinsic(code = "length(({0}) - ({1}))")
    public static double distance(Double2 left, Double2 right) {
        return length(new Double2(left.x - right.x, left.y - right.y));
    }

    @GPUIntrinsic(code = "length(({0}) - ({1}))")
    public static double distance(Double3 left, Double3 right) {
        return length(new Double3(left.x - right.x, left.y - right.y, left.z - right.z));
    }

    @GPUIntrinsic(code = "length(({0}) - ({1}))")
    public static double distance(Double4 left, Double4 right) {
        return length(new Double4(left.x - right.x, left.y - right.y, left.z - right.z, left.w - right.w));
    }

    @GPUIntrinsic(code = "normalize({0})")
    public static Float2 normalize(Float2 value) {
        float length = length(value);
        return new Float2(value.x / length, value.y / length);
    }

    @GPUIntrinsic(code = "normalize({0})")
    public static Float3 normalize(Float3 value) {
        float length = length(value);
        return new Float3(value.x / length, value.y / length, value.z / length);
    }

    @GPUIntrinsic(code = "normalize({0})")
    public static Float4 normalize(Float4 value) {
        float length = length(value);
        return new Float4(value.x / length, value.y / length, value.z / length, value.w / length);
    }

    @GPUIntrinsic(code = "normalize({0})")
    public static Double2 normalize(Double2 value) {
        double length = length(value);
        return new Double2(value.x / length, value.y / length);
    }

    @GPUIntrinsic(code = "normalize({0})")
    public static Double3 normalize(Double3 value) {
        double length = length(value);
        return new Double3(value.x / length, value.y / length, value.z / length);
    }

    @GPUIntrinsic(code = "normalize({0})")
    public static Double4 normalize(Double4 value) {
        double length = length(value);
        return new Double4(value.x / length, value.y / length, value.z / length, value.w / length);
    }

    @GPUIntrinsic(name = "cross")
    public static Float3 cross(Float3 left, Float3 right) {
        return new Float3(
                left.y * right.z - left.z * right.y,
                left.z * right.x - left.x * right.z,
                left.x * right.y - left.y * right.x
        );
    }

    @GPUIntrinsic(name = "cross")
    public static Double3 cross(Double3 left, Double3 right) {
        return new Double3(
                left.y * right.z - left.z * right.y,
                left.z * right.x - left.x * right.z,
                left.x * right.y - left.y * right.x
        );
    }

    @GPUIntrinsic(code = "(({0}) - floor({0}))")
    public static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    @GPUIntrinsic(code = "(({0}) - floor({0}))")
    public static double fract(double value) {
        return value - Math.floor(value);
    }

    @GPUIntrinsic(code = "fract({0})")
    public static Float2 fract(Float2 value) {
        return new Float2(fract(value.x), fract(value.y));
    }

    @GPUIntrinsic(code = "fract({0})")
    public static Float3 fract(Float3 value) {
        return new Float3(fract(value.x), fract(value.y), fract(value.z));
    }

    @GPUIntrinsic(code = "fract({0})")
    public static Float4 fract(Float4 value) {
        return new Float4(fract(value.x), fract(value.y), fract(value.z), fract(value.w));
    }

    @GPUIntrinsic(code = "fract({0})")
    public static Double2 fract(Double2 value) {
        return new Double2(fract(value.x), fract(value.y));
    }

    @GPUIntrinsic(code = "fract({0})")
    public static Double3 fract(Double3 value) {
        return new Double3(fract(value.x), fract(value.y), fract(value.z));
    }

    @GPUIntrinsic(code = "fract({0})")
    public static Double4 fract(Double4 value) {
        return new Double4(fract(value.x), fract(value.y), fract(value.z), fract(value.w));
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

    @GPUIntrinsic(code = "sign({0})")
    public static Float2 sign(Float2 value) {
        return new Float2(sign(value.x), sign(value.y));
    }

    @GPUIntrinsic(code = "sign({0})")
    public static Float3 sign(Float3 value) {
        return new Float3(sign(value.x), sign(value.y), sign(value.z));
    }

    @GPUIntrinsic(code = "sign({0})")
    public static Float4 sign(Float4 value) {
        return new Float4(sign(value.x), sign(value.y), sign(value.z), sign(value.w));
    }

    @GPUIntrinsic(code = "sign({0})")
    public static Double2 sign(Double2 value) {
        return new Double2(sign(value.x), sign(value.y));
    }

    @GPUIntrinsic(code = "sign({0})")
    public static Double3 sign(Double3 value) {
        return new Double3(sign(value.x), sign(value.y), sign(value.z));
    }

    @GPUIntrinsic(code = "sign({0})")
    public static Double4 sign(Double4 value) {
        return new Double4(sign(value.x), sign(value.y), sign(value.z), sign(value.w));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static int abs_diff(int left, int right) {
        return Math.abs(left - right);
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static Int2 abs_diff(Int2 left, Int2 right) {
        return new Int2(abs_diff(left.x, right.x), abs_diff(left.y, right.y));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static Int3 abs_diff(Int3 left, Int3 right) {
        return new Int3(abs_diff(left.x, right.x), abs_diff(left.y, right.y), abs_diff(left.z, right.z));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static Int4 abs_diff(Int4 left, Int4 right) {
        return new Int4(abs_diff(left.x, right.x), abs_diff(left.y, right.y), abs_diff(left.z, right.z), abs_diff(left.w, right.w));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static long abs_diff(long left, long right) {
        return Math.abs(left - right);
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static Long2 abs_diff(Long2 left, Long2 right) {
        return new Long2(abs_diff(left.x, right.x), abs_diff(left.y, right.y));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static Long3 abs_diff(Long3 left, Long3 right) {
        return new Long3(abs_diff(left.x, right.x), abs_diff(left.y, right.y), abs_diff(left.z, right.z));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static Long4 abs_diff(Long4 left, Long4 right) {
        return new Long4(abs_diff(left.x, right.x), abs_diff(left.y, right.y), abs_diff(left.z, right.z), abs_diff(left.w, right.w));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static UByte abs_diff(UByte left, UByte right) {
        return new UByte(absDiffUnsignedByte(left.value, right.value));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static UByte2 abs_diff(UByte2 left, UByte2 right) {
        return new UByte2(absDiffUnsignedByte(left.x, right.x), absDiffUnsignedByte(left.y, right.y));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static UByte3 abs_diff(UByte3 left, UByte3 right) {
        return new UByte3(absDiffUnsignedByte(left.x, right.x), absDiffUnsignedByte(left.y, right.y), absDiffUnsignedByte(left.z, right.z));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static UByte4 abs_diff(UByte4 left, UByte4 right) {
        return new UByte4(absDiffUnsignedByte(left.x, right.x), absDiffUnsignedByte(left.y, right.y), absDiffUnsignedByte(left.z, right.z), absDiffUnsignedByte(left.w, right.w));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static UByte8 abs_diff(UByte8 left, UByte8 right) {
        return new UByte8(
                absDiffUnsignedByte(left.s0, right.s0), absDiffUnsignedByte(left.s1, right.s1),
                absDiffUnsignedByte(left.s2, right.s2), absDiffUnsignedByte(left.s3, right.s3),
                absDiffUnsignedByte(left.s4, right.s4), absDiffUnsignedByte(left.s5, right.s5),
                absDiffUnsignedByte(left.s6, right.s6), absDiffUnsignedByte(left.s7, right.s7)
        );
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static UShort abs_diff(UShort left, UShort right) {
        return new UShort(absDiffUnsignedShort(left.value, right.value));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static UShort2 abs_diff(UShort2 left, UShort2 right) {
        return new UShort2(absDiffUnsignedShort(left.x, right.x), absDiffUnsignedShort(left.y, right.y));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static UShort3 abs_diff(UShort3 left, UShort3 right) {
        return new UShort3(absDiffUnsignedShort(left.x, right.x), absDiffUnsignedShort(left.y, right.y), absDiffUnsignedShort(left.z, right.z));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static UShort4 abs_diff(UShort4 left, UShort4 right) {
        return new UShort4(absDiffUnsignedShort(left.x, right.x), absDiffUnsignedShort(left.y, right.y), absDiffUnsignedShort(left.z, right.z), absDiffUnsignedShort(left.w, right.w));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static UShort8 abs_diff(UShort8 left, UShort8 right) {
        return new UShort8(
                absDiffUnsignedShort(left.s0, right.s0), absDiffUnsignedShort(left.s1, right.s1),
                absDiffUnsignedShort(left.s2, right.s2), absDiffUnsignedShort(left.s3, right.s3),
                absDiffUnsignedShort(left.s4, right.s4), absDiffUnsignedShort(left.s5, right.s5),
                absDiffUnsignedShort(left.s6, right.s6), absDiffUnsignedShort(left.s7, right.s7)
        );
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static UInt abs_diff(UInt left, UInt right) {
        return new UInt(absDiffUnsignedInt(left.value, right.value));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static UInt2 abs_diff(UInt2 left, UInt2 right) {
        return new UInt2(absDiffUnsignedInt(left.x, right.x), absDiffUnsignedInt(left.y, right.y));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static UInt3 abs_diff(UInt3 left, UInt3 right) {
        return new UInt3(absDiffUnsignedInt(left.x, right.x), absDiffUnsignedInt(left.y, right.y), absDiffUnsignedInt(left.z, right.z));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static UInt4 abs_diff(UInt4 left, UInt4 right) {
        return new UInt4(absDiffUnsignedInt(left.x, right.x), absDiffUnsignedInt(left.y, right.y), absDiffUnsignedInt(left.z, right.z), absDiffUnsignedInt(left.w, right.w));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static UInt8 abs_diff(UInt8 left, UInt8 right) {
        return new UInt8(
                absDiffUnsignedInt(left.s0, right.s0), absDiffUnsignedInt(left.s1, right.s1),
                absDiffUnsignedInt(left.s2, right.s2), absDiffUnsignedInt(left.s3, right.s3),
                absDiffUnsignedInt(left.s4, right.s4), absDiffUnsignedInt(left.s5, right.s5),
                absDiffUnsignedInt(left.s6, right.s6), absDiffUnsignedInt(left.s7, right.s7)
        );
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static ULong abs_diff(ULong left, ULong right) {
        return new ULong(absDiffUnsignedLong(left.value, right.value));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static ULong2 abs_diff(ULong2 left, ULong2 right) {
        return new ULong2(absDiffUnsignedLong(left.x, right.x), absDiffUnsignedLong(left.y, right.y));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static ULong3 abs_diff(ULong3 left, ULong3 right) {
        return new ULong3(absDiffUnsignedLong(left.x, right.x), absDiffUnsignedLong(left.y, right.y), absDiffUnsignedLong(left.z, right.z));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static ULong4 abs_diff(ULong4 left, ULong4 right) {
        return new ULong4(absDiffUnsignedLong(left.x, right.x), absDiffUnsignedLong(left.y, right.y), absDiffUnsignedLong(left.z, right.z), absDiffUnsignedLong(left.w, right.w));
    }

    @GPUIntrinsic(code = "abs_diff({0}, {1})")
    public static ULong8 abs_diff(ULong8 left, ULong8 right) {
        return new ULong8(
                absDiffUnsignedLong(left.s0, right.s0), absDiffUnsignedLong(left.s1, right.s1),
                absDiffUnsignedLong(left.s2, right.s2), absDiffUnsignedLong(left.s3, right.s3),
                absDiffUnsignedLong(left.s4, right.s4), absDiffUnsignedLong(left.s5, right.s5),
                absDiffUnsignedLong(left.s6, right.s6), absDiffUnsignedLong(left.s7, right.s7)
        );
    }

    @GPUIntrinsic(name = "upsample")
    public static int upsample(short high, short low) {
        return ((high & 0xFFFF) << 16) | (low & 0xFFFF);
    }

    @GPUIntrinsic(code = "upsample({0}, {1})")
    public static int upsample(short high, UShort low) {
        return ((high & 0xFFFF) << 16) | Short.toUnsignedInt(low.value);
    }

    @GPUIntrinsic(code = "upsample({0}, {1})")
    public static UInt upsample(UShort high, UShort low) {
        return new UInt((Short.toUnsignedInt(high.value) << 16) | Short.toUnsignedInt(low.value));
    }

    @GPUIntrinsic(name = "upsample")
    public static long upsample(int high, int low) {
        return ((high & 0xFFFFFFFFL) << 32) | (low & 0xFFFFFFFFL);
    }

    @GPUIntrinsic(code = "upsample({0}, {1})")
    public static long upsample(int high, UInt low) {
        return ((high & 0xFFFFFFFFL) << 32) | Integer.toUnsignedLong(low.value);
    }

    @GPUIntrinsic(code = "upsample({0}, {1})")
    public static ULong upsample(UInt high, UInt low) {
        return new ULong((Integer.toUnsignedLong(high.value) << 32) | Integer.toUnsignedLong(low.value));
    }

    @GPUIntrinsic(code = "(({2}) ? ({1}) : ({0}))")
    public static int select(int left, int right, boolean condition) {
        return condition ? right : left;
    }

    @GPUIntrinsic(code = "(({2}) ? ({1}) : ({0}))")
    public static long select(long left, long right, boolean condition) {
        return condition ? right : left;
    }

    @GPUIntrinsic(code = "(({2}) ? ({1}) : ({0}))")
    public static float select(float left, float right, boolean condition) {
        return condition ? right : left;
    }

    @GPUIntrinsic(code = "(({2}) ? ({1}) : ({0}))")
    public static double select(double left, double right, boolean condition) {
        return condition ? right : left;
    }

    @GPUIntrinsic(name = "select")
    public static int select(int left, int right, int mask) {
        return mask < 0 ? right : left;
    }

    @GPUIntrinsic(name = "select")
    public static long select(long left, long right, long mask) {
        return mask < 0L ? right : left;
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static UInt select(UInt left, UInt right, UInt mask) {
        return Integer.compareUnsigned(mask.value, 0x80000000) >= 0 ? right : left;
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static ULong select(ULong left, ULong right, ULong mask) {
        return Long.compareUnsigned(mask.value, 0x8000000000000000L) >= 0 ? right : left;
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static Int2 select(Int2 left, Int2 right, Int2 mask) {
        return new Int2(select(left.x, right.x, mask.x), select(left.y, right.y, mask.y));
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static Int3 select(Int3 left, Int3 right, Int3 mask) {
        return new Int3(select(left.x, right.x, mask.x), select(left.y, right.y, mask.y), select(left.z, right.z, mask.z));
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static Int4 select(Int4 left, Int4 right, Int4 mask) {
        return new Int4(select(left.x, right.x, mask.x), select(left.y, right.y, mask.y), select(left.z, right.z, mask.z), select(left.w, right.w, mask.w));
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static Long2 select(Long2 left, Long2 right, Long2 mask) {
        return new Long2(select(left.x, right.x, mask.x), select(left.y, right.y, mask.y));
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static Long3 select(Long3 left, Long3 right, Long3 mask) {
        return new Long3(select(left.x, right.x, mask.x), select(left.y, right.y, mask.y), select(left.z, right.z, mask.z));
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static Long4 select(Long4 left, Long4 right, Long4 mask) {
        return new Long4(select(left.x, right.x, mask.x), select(left.y, right.y, mask.y), select(left.z, right.z, mask.z), select(left.w, right.w, mask.w));
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static UInt2 select(UInt2 left, UInt2 right, UInt2 mask) {
        return new UInt2(select(new UInt(left.x), new UInt(right.x), new UInt(mask.x)).value,
                select(new UInt(left.y), new UInt(right.y), new UInt(mask.y)).value);
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static UInt3 select(UInt3 left, UInt3 right, UInt3 mask) {
        return new UInt3(select(new UInt(left.x), new UInt(right.x), new UInt(mask.x)).value,
                select(new UInt(left.y), new UInt(right.y), new UInt(mask.y)).value,
                select(new UInt(left.z), new UInt(right.z), new UInt(mask.z)).value);
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static UInt4 select(UInt4 left, UInt4 right, UInt4 mask) {
        return new UInt4(select(new UInt(left.x), new UInt(right.x), new UInt(mask.x)).value,
                select(new UInt(left.y), new UInt(right.y), new UInt(mask.y)).value,
                select(new UInt(left.z), new UInt(right.z), new UInt(mask.z)).value,
                select(new UInt(left.w), new UInt(right.w), new UInt(mask.w)).value);
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static ULong2 select(ULong2 left, ULong2 right, ULong2 mask) {
        return new ULong2(select(new ULong(left.x), new ULong(right.x), new ULong(mask.x)).value,
                select(new ULong(left.y), new ULong(right.y), new ULong(mask.y)).value);
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static ULong3 select(ULong3 left, ULong3 right, ULong3 mask) {
        return new ULong3(select(new ULong(left.x), new ULong(right.x), new ULong(mask.x)).value,
                select(new ULong(left.y), new ULong(right.y), new ULong(mask.y)).value,
                select(new ULong(left.z), new ULong(right.z), new ULong(mask.z)).value);
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static ULong4 select(ULong4 left, ULong4 right, ULong4 mask) {
        return new ULong4(select(new ULong(left.x), new ULong(right.x), new ULong(mask.x)).value,
                select(new ULong(left.y), new ULong(right.y), new ULong(mask.y)).value,
                select(new ULong(left.z), new ULong(right.z), new ULong(mask.z)).value,
                select(new ULong(left.w), new ULong(right.w), new ULong(mask.w)).value);
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static UByte2 select(UByte2 left, UByte2 right, UByte2 mask) {
        return new UByte2((byte) select(Byte.toUnsignedInt(left.x), Byte.toUnsignedInt(right.x), Byte.toUnsignedInt(mask.x)),
                (byte) select(Byte.toUnsignedInt(left.y), Byte.toUnsignedInt(right.y), Byte.toUnsignedInt(mask.y)));
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static UByte3 select(UByte3 left, UByte3 right, UByte3 mask) {
        return new UByte3((byte) select(Byte.toUnsignedInt(left.x), Byte.toUnsignedInt(right.x), Byte.toUnsignedInt(mask.x)),
                (byte) select(Byte.toUnsignedInt(left.y), Byte.toUnsignedInt(right.y), Byte.toUnsignedInt(mask.y)),
                (byte) select(Byte.toUnsignedInt(left.z), Byte.toUnsignedInt(right.z), Byte.toUnsignedInt(mask.z)));
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static UByte4 select(UByte4 left, UByte4 right, UByte4 mask) {
        return new UByte4((byte) select(Byte.toUnsignedInt(left.x), Byte.toUnsignedInt(right.x), Byte.toUnsignedInt(mask.x)),
                (byte) select(Byte.toUnsignedInt(left.y), Byte.toUnsignedInt(right.y), Byte.toUnsignedInt(mask.y)),
                (byte) select(Byte.toUnsignedInt(left.z), Byte.toUnsignedInt(right.z), Byte.toUnsignedInt(mask.z)),
                (byte) select(Byte.toUnsignedInt(left.w), Byte.toUnsignedInt(right.w), Byte.toUnsignedInt(mask.w)));
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static UShort2 select(UShort2 left, UShort2 right, UShort2 mask) {
        return new UShort2((short) select(Short.toUnsignedInt(left.x), Short.toUnsignedInt(right.x), Short.toUnsignedInt(mask.x)),
                (short) select(Short.toUnsignedInt(left.y), Short.toUnsignedInt(right.y), Short.toUnsignedInt(mask.y)));
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static UShort3 select(UShort3 left, UShort3 right, UShort3 mask) {
        return new UShort3((short) select(Short.toUnsignedInt(left.x), Short.toUnsignedInt(right.x), Short.toUnsignedInt(mask.x)),
                (short) select(Short.toUnsignedInt(left.y), Short.toUnsignedInt(right.y), Short.toUnsignedInt(mask.y)),
                (short) select(Short.toUnsignedInt(left.z), Short.toUnsignedInt(right.z), Short.toUnsignedInt(mask.z)));
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static UShort4 select(UShort4 left, UShort4 right, UShort4 mask) {
        return new UShort4((short) select(Short.toUnsignedInt(left.x), Short.toUnsignedInt(right.x), Short.toUnsignedInt(mask.x)),
                (short) select(Short.toUnsignedInt(left.y), Short.toUnsignedInt(right.y), Short.toUnsignedInt(mask.y)),
                (short) select(Short.toUnsignedInt(left.z), Short.toUnsignedInt(right.z), Short.toUnsignedInt(mask.z)),
                (short) select(Short.toUnsignedInt(left.w), Short.toUnsignedInt(right.w), Short.toUnsignedInt(mask.w)));
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static UInt8 select(UInt8 left, UInt8 right, UInt8 mask) {
        return new UInt8(
                select(new UInt(left.s0), new UInt(right.s0), new UInt(mask.s0)).value,
                select(new UInt(left.s1), new UInt(right.s1), new UInt(mask.s1)).value,
                select(new UInt(left.s2), new UInt(right.s2), new UInt(mask.s2)).value,
                select(new UInt(left.s3), new UInt(right.s3), new UInt(mask.s3)).value,
                select(new UInt(left.s4), new UInt(right.s4), new UInt(mask.s4)).value,
                select(new UInt(left.s5), new UInt(right.s5), new UInt(mask.s5)).value,
                select(new UInt(left.s6), new UInt(right.s6), new UInt(mask.s6)).value,
                select(new UInt(left.s7), new UInt(right.s7), new UInt(mask.s7)).value
        );
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static UInt16 select(UInt16 left, UInt16 right, UInt16 mask) {
        return new UInt16(
                select(new UInt(left.s0), new UInt(right.s0), new UInt(mask.s0)).value,
                select(new UInt(left.s1), new UInt(right.s1), new UInt(mask.s1)).value,
                select(new UInt(left.s2), new UInt(right.s2), new UInt(mask.s2)).value,
                select(new UInt(left.s3), new UInt(right.s3), new UInt(mask.s3)).value,
                select(new UInt(left.s4), new UInt(right.s4), new UInt(mask.s4)).value,
                select(new UInt(left.s5), new UInt(right.s5), new UInt(mask.s5)).value,
                select(new UInt(left.s6), new UInt(right.s6), new UInt(mask.s6)).value,
                select(new UInt(left.s7), new UInt(right.s7), new UInt(mask.s7)).value,
                select(new UInt(left.s8), new UInt(right.s8), new UInt(mask.s8)).value,
                select(new UInt(left.s9), new UInt(right.s9), new UInt(mask.s9)).value,
                select(new UInt(left.sa), new UInt(right.sa), new UInt(mask.sa)).value,
                select(new UInt(left.sb), new UInt(right.sb), new UInt(mask.sb)).value,
                select(new UInt(left.sc), new UInt(right.sc), new UInt(mask.sc)).value,
                select(new UInt(left.sd), new UInt(right.sd), new UInt(mask.sd)).value,
                select(new UInt(left.se), new UInt(right.se), new UInt(mask.se)).value,
                select(new UInt(left.sf), new UInt(right.sf), new UInt(mask.sf)).value
        );
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static ULong8 select(ULong8 left, ULong8 right, ULong8 mask) {
        return new ULong8(
                select(new ULong(left.s0), new ULong(right.s0), new ULong(mask.s0)).value,
                select(new ULong(left.s1), new ULong(right.s1), new ULong(mask.s1)).value,
                select(new ULong(left.s2), new ULong(right.s2), new ULong(mask.s2)).value,
                select(new ULong(left.s3), new ULong(right.s3), new ULong(mask.s3)).value,
                select(new ULong(left.s4), new ULong(right.s4), new ULong(mask.s4)).value,
                select(new ULong(left.s5), new ULong(right.s5), new ULong(mask.s5)).value,
                select(new ULong(left.s6), new ULong(right.s6), new ULong(mask.s6)).value,
                select(new ULong(left.s7), new ULong(right.s7), new ULong(mask.s7)).value
        );
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static ULong16 select(ULong16 left, ULong16 right, ULong16 mask) {
        return new ULong16(
                select(new ULong(left.s0), new ULong(right.s0), new ULong(mask.s0)).value,
                select(new ULong(left.s1), new ULong(right.s1), new ULong(mask.s1)).value,
                select(new ULong(left.s2), new ULong(right.s2), new ULong(mask.s2)).value,
                select(new ULong(left.s3), new ULong(right.s3), new ULong(mask.s3)).value,
                select(new ULong(left.s4), new ULong(right.s4), new ULong(mask.s4)).value,
                select(new ULong(left.s5), new ULong(right.s5), new ULong(mask.s5)).value,
                select(new ULong(left.s6), new ULong(right.s6), new ULong(mask.s6)).value,
                select(new ULong(left.s7), new ULong(right.s7), new ULong(mask.s7)).value,
                select(new ULong(left.s8), new ULong(right.s8), new ULong(mask.s8)).value,
                select(new ULong(left.s9), new ULong(right.s9), new ULong(mask.s9)).value,
                select(new ULong(left.sa), new ULong(right.sa), new ULong(mask.sa)).value,
                select(new ULong(left.sb), new ULong(right.sb), new ULong(mask.sb)).value,
                select(new ULong(left.sc), new ULong(right.sc), new ULong(mask.sc)).value,
                select(new ULong(left.sd), new ULong(right.sd), new ULong(mask.sd)).value,
                select(new ULong(left.se), new ULong(right.se), new ULong(mask.se)).value,
                select(new ULong(left.sf), new ULong(right.sf), new ULong(mask.sf)).value
        );
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static UByte8 select(UByte8 left, UByte8 right, UByte8 mask) {
        return new UByte8(
                (byte) select(Byte.toUnsignedInt(left.s0), Byte.toUnsignedInt(right.s0), Byte.toUnsignedInt(mask.s0)),
                (byte) select(Byte.toUnsignedInt(left.s1), Byte.toUnsignedInt(right.s1), Byte.toUnsignedInt(mask.s1)),
                (byte) select(Byte.toUnsignedInt(left.s2), Byte.toUnsignedInt(right.s2), Byte.toUnsignedInt(mask.s2)),
                (byte) select(Byte.toUnsignedInt(left.s3), Byte.toUnsignedInt(right.s3), Byte.toUnsignedInt(mask.s3)),
                (byte) select(Byte.toUnsignedInt(left.s4), Byte.toUnsignedInt(right.s4), Byte.toUnsignedInt(mask.s4)),
                (byte) select(Byte.toUnsignedInt(left.s5), Byte.toUnsignedInt(right.s5), Byte.toUnsignedInt(mask.s5)),
                (byte) select(Byte.toUnsignedInt(left.s6), Byte.toUnsignedInt(right.s6), Byte.toUnsignedInt(mask.s6)),
                (byte) select(Byte.toUnsignedInt(left.s7), Byte.toUnsignedInt(right.s7), Byte.toUnsignedInt(mask.s7))
        );
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static UByte16 select(UByte16 left, UByte16 right, UByte16 mask) {
        return new UByte16(
                (byte) select(Byte.toUnsignedInt(left.s0), Byte.toUnsignedInt(right.s0), Byte.toUnsignedInt(mask.s0)),
                (byte) select(Byte.toUnsignedInt(left.s1), Byte.toUnsignedInt(right.s1), Byte.toUnsignedInt(mask.s1)),
                (byte) select(Byte.toUnsignedInt(left.s2), Byte.toUnsignedInt(right.s2), Byte.toUnsignedInt(mask.s2)),
                (byte) select(Byte.toUnsignedInt(left.s3), Byte.toUnsignedInt(right.s3), Byte.toUnsignedInt(mask.s3)),
                (byte) select(Byte.toUnsignedInt(left.s4), Byte.toUnsignedInt(right.s4), Byte.toUnsignedInt(mask.s4)),
                (byte) select(Byte.toUnsignedInt(left.s5), Byte.toUnsignedInt(right.s5), Byte.toUnsignedInt(mask.s5)),
                (byte) select(Byte.toUnsignedInt(left.s6), Byte.toUnsignedInt(right.s6), Byte.toUnsignedInt(mask.s6)),
                (byte) select(Byte.toUnsignedInt(left.s7), Byte.toUnsignedInt(right.s7), Byte.toUnsignedInt(mask.s7)),
                (byte) select(Byte.toUnsignedInt(left.s8), Byte.toUnsignedInt(right.s8), Byte.toUnsignedInt(mask.s8)),
                (byte) select(Byte.toUnsignedInt(left.s9), Byte.toUnsignedInt(right.s9), Byte.toUnsignedInt(mask.s9)),
                (byte) select(Byte.toUnsignedInt(left.sa), Byte.toUnsignedInt(right.sa), Byte.toUnsignedInt(mask.sa)),
                (byte) select(Byte.toUnsignedInt(left.sb), Byte.toUnsignedInt(right.sb), Byte.toUnsignedInt(mask.sb)),
                (byte) select(Byte.toUnsignedInt(left.sc), Byte.toUnsignedInt(right.sc), Byte.toUnsignedInt(mask.sc)),
                (byte) select(Byte.toUnsignedInt(left.sd), Byte.toUnsignedInt(right.sd), Byte.toUnsignedInt(mask.sd)),
                (byte) select(Byte.toUnsignedInt(left.se), Byte.toUnsignedInt(right.se), Byte.toUnsignedInt(mask.se)),
                (byte) select(Byte.toUnsignedInt(left.sf), Byte.toUnsignedInt(right.sf), Byte.toUnsignedInt(mask.sf))
        );
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static UShort8 select(UShort8 left, UShort8 right, UShort8 mask) {
        return new UShort8(
                (short) select(Short.toUnsignedInt(left.s0), Short.toUnsignedInt(right.s0), Short.toUnsignedInt(mask.s0)),
                (short) select(Short.toUnsignedInt(left.s1), Short.toUnsignedInt(right.s1), Short.toUnsignedInt(mask.s1)),
                (short) select(Short.toUnsignedInt(left.s2), Short.toUnsignedInt(right.s2), Short.toUnsignedInt(mask.s2)),
                (short) select(Short.toUnsignedInt(left.s3), Short.toUnsignedInt(right.s3), Short.toUnsignedInt(mask.s3)),
                (short) select(Short.toUnsignedInt(left.s4), Short.toUnsignedInt(right.s4), Short.toUnsignedInt(mask.s4)),
                (short) select(Short.toUnsignedInt(left.s5), Short.toUnsignedInt(right.s5), Short.toUnsignedInt(mask.s5)),
                (short) select(Short.toUnsignedInt(left.s6), Short.toUnsignedInt(right.s6), Short.toUnsignedInt(mask.s6)),
                (short) select(Short.toUnsignedInt(left.s7), Short.toUnsignedInt(right.s7), Short.toUnsignedInt(mask.s7))
        );
    }

    @GPUIntrinsic(code = "select({0}, {1}, {2})")
    public static UShort16 select(UShort16 left, UShort16 right, UShort16 mask) {
        return new UShort16(
                (short) select(Short.toUnsignedInt(left.s0), Short.toUnsignedInt(right.s0), Short.toUnsignedInt(mask.s0)),
                (short) select(Short.toUnsignedInt(left.s1), Short.toUnsignedInt(right.s1), Short.toUnsignedInt(mask.s1)),
                (short) select(Short.toUnsignedInt(left.s2), Short.toUnsignedInt(right.s2), Short.toUnsignedInt(mask.s2)),
                (short) select(Short.toUnsignedInt(left.s3), Short.toUnsignedInt(right.s3), Short.toUnsignedInt(mask.s3)),
                (short) select(Short.toUnsignedInt(left.s4), Short.toUnsignedInt(right.s4), Short.toUnsignedInt(mask.s4)),
                (short) select(Short.toUnsignedInt(left.s5), Short.toUnsignedInt(right.s5), Short.toUnsignedInt(mask.s5)),
                (short) select(Short.toUnsignedInt(left.s6), Short.toUnsignedInt(right.s6), Short.toUnsignedInt(mask.s6)),
                (short) select(Short.toUnsignedInt(left.s7), Short.toUnsignedInt(right.s7), Short.toUnsignedInt(mask.s7)),
                (short) select(Short.toUnsignedInt(left.s8), Short.toUnsignedInt(right.s8), Short.toUnsignedInt(mask.s8)),
                (short) select(Short.toUnsignedInt(left.s9), Short.toUnsignedInt(right.s9), Short.toUnsignedInt(mask.s9)),
                (short) select(Short.toUnsignedInt(left.sa), Short.toUnsignedInt(right.sa), Short.toUnsignedInt(mask.sa)),
                (short) select(Short.toUnsignedInt(left.sb), Short.toUnsignedInt(right.sb), Short.toUnsignedInt(mask.sb)),
                (short) select(Short.toUnsignedInt(left.sc), Short.toUnsignedInt(right.sc), Short.toUnsignedInt(mask.sc)),
                (short) select(Short.toUnsignedInt(left.sd), Short.toUnsignedInt(right.sd), Short.toUnsignedInt(mask.sd)),
                (short) select(Short.toUnsignedInt(left.se), Short.toUnsignedInt(right.se), Short.toUnsignedInt(mask.se)),
                (short) select(Short.toUnsignedInt(left.sf), Short.toUnsignedInt(right.sf), Short.toUnsignedInt(mask.sf))
        );
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static int bitselect(int left, int right, int mask) {
        return (left & ~mask) | (right & mask);
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static long bitselect(long left, long right, long mask) {
        return (left & ~mask) | (right & mask);
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static UInt bitselect(UInt left, UInt right, UInt mask) {
        return new UInt(bitselect(left.value, right.value, mask.value));
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static ULong bitselect(ULong left, ULong right, ULong mask) {
        return new ULong(bitselect(left.value, right.value, mask.value));
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static UInt2 bitselect(UInt2 left, UInt2 right, UInt2 mask) {
        return new UInt2(bitselect(new UInt(left.x), new UInt(right.x), new UInt(mask.x)).value,
                bitselect(new UInt(left.y), new UInt(right.y), new UInt(mask.y)).value);
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static UInt3 bitselect(UInt3 left, UInt3 right, UInt3 mask) {
        return new UInt3(bitselect(new UInt(left.x), new UInt(right.x), new UInt(mask.x)).value,
                bitselect(new UInt(left.y), new UInt(right.y), new UInt(mask.y)).value,
                bitselect(new UInt(left.z), new UInt(right.z), new UInt(mask.z)).value);
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static UInt4 bitselect(UInt4 left, UInt4 right, UInt4 mask) {
        return new UInt4(bitselect(new UInt(left.x), new UInt(right.x), new UInt(mask.x)).value,
                bitselect(new UInt(left.y), new UInt(right.y), new UInt(mask.y)).value,
                bitselect(new UInt(left.z), new UInt(right.z), new UInt(mask.z)).value,
                bitselect(new UInt(left.w), new UInt(right.w), new UInt(mask.w)).value);
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static ULong2 bitselect(ULong2 left, ULong2 right, ULong2 mask) {
        return new ULong2(bitselect(new ULong(left.x), new ULong(right.x), new ULong(mask.x)).value,
                bitselect(new ULong(left.y), new ULong(right.y), new ULong(mask.y)).value);
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static ULong3 bitselect(ULong3 left, ULong3 right, ULong3 mask) {
        return new ULong3(bitselect(new ULong(left.x), new ULong(right.x), new ULong(mask.x)).value,
                bitselect(new ULong(left.y), new ULong(right.y), new ULong(mask.y)).value,
                bitselect(new ULong(left.z), new ULong(right.z), new ULong(mask.z)).value);
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static ULong4 bitselect(ULong4 left, ULong4 right, ULong4 mask) {
        return new ULong4(bitselect(new ULong(left.x), new ULong(right.x), new ULong(mask.x)).value,
                bitselect(new ULong(left.y), new ULong(right.y), new ULong(mask.y)).value,
                bitselect(new ULong(left.z), new ULong(right.z), new ULong(mask.z)).value,
                bitselect(new ULong(left.w), new ULong(right.w), new ULong(mask.w)).value);
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static UByte2 bitselect(UByte2 left, UByte2 right, UByte2 mask) {
        return new UByte2((byte) bitselect(Byte.toUnsignedInt(left.x), Byte.toUnsignedInt(right.x), Byte.toUnsignedInt(mask.x)),
                (byte) bitselect(Byte.toUnsignedInt(left.y), Byte.toUnsignedInt(right.y), Byte.toUnsignedInt(mask.y)));
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static UByte3 bitselect(UByte3 left, UByte3 right, UByte3 mask) {
        return new UByte3((byte) bitselect(Byte.toUnsignedInt(left.x), Byte.toUnsignedInt(right.x), Byte.toUnsignedInt(mask.x)),
                (byte) bitselect(Byte.toUnsignedInt(left.y), Byte.toUnsignedInt(right.y), Byte.toUnsignedInt(mask.y)),
                (byte) bitselect(Byte.toUnsignedInt(left.z), Byte.toUnsignedInt(right.z), Byte.toUnsignedInt(mask.z)));
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static UByte4 bitselect(UByte4 left, UByte4 right, UByte4 mask) {
        return new UByte4((byte) bitselect(Byte.toUnsignedInt(left.x), Byte.toUnsignedInt(right.x), Byte.toUnsignedInt(mask.x)),
                (byte) bitselect(Byte.toUnsignedInt(left.y), Byte.toUnsignedInt(right.y), Byte.toUnsignedInt(mask.y)),
                (byte) bitselect(Byte.toUnsignedInt(left.z), Byte.toUnsignedInt(right.z), Byte.toUnsignedInt(mask.z)),
                (byte) bitselect(Byte.toUnsignedInt(left.w), Byte.toUnsignedInt(right.w), Byte.toUnsignedInt(mask.w)));
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static UShort2 bitselect(UShort2 left, UShort2 right, UShort2 mask) {
        return new UShort2((short) bitselect(Short.toUnsignedInt(left.x), Short.toUnsignedInt(right.x), Short.toUnsignedInt(mask.x)),
                (short) bitselect(Short.toUnsignedInt(left.y), Short.toUnsignedInt(right.y), Short.toUnsignedInt(mask.y)));
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static UShort3 bitselect(UShort3 left, UShort3 right, UShort3 mask) {
        return new UShort3((short) bitselect(Short.toUnsignedInt(left.x), Short.toUnsignedInt(right.x), Short.toUnsignedInt(mask.x)),
                (short) bitselect(Short.toUnsignedInt(left.y), Short.toUnsignedInt(right.y), Short.toUnsignedInt(mask.y)),
                (short) bitselect(Short.toUnsignedInt(left.z), Short.toUnsignedInt(right.z), Short.toUnsignedInt(mask.z)));
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static UShort4 bitselect(UShort4 left, UShort4 right, UShort4 mask) {
        return new UShort4((short) bitselect(Short.toUnsignedInt(left.x), Short.toUnsignedInt(right.x), Short.toUnsignedInt(mask.x)),
                (short) bitselect(Short.toUnsignedInt(left.y), Short.toUnsignedInt(right.y), Short.toUnsignedInt(mask.y)),
                (short) bitselect(Short.toUnsignedInt(left.z), Short.toUnsignedInt(right.z), Short.toUnsignedInt(mask.z)),
                (short) bitselect(Short.toUnsignedInt(left.w), Short.toUnsignedInt(right.w), Short.toUnsignedInt(mask.w)));
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static UInt8 bitselect(UInt8 left, UInt8 right, UInt8 mask) {
        return new UInt8(
                bitselect(new UInt(left.s0), new UInt(right.s0), new UInt(mask.s0)).value,
                bitselect(new UInt(left.s1), new UInt(right.s1), new UInt(mask.s1)).value,
                bitselect(new UInt(left.s2), new UInt(right.s2), new UInt(mask.s2)).value,
                bitselect(new UInt(left.s3), new UInt(right.s3), new UInt(mask.s3)).value,
                bitselect(new UInt(left.s4), new UInt(right.s4), new UInt(mask.s4)).value,
                bitselect(new UInt(left.s5), new UInt(right.s5), new UInt(mask.s5)).value,
                bitselect(new UInt(left.s6), new UInt(right.s6), new UInt(mask.s6)).value,
                bitselect(new UInt(left.s7), new UInt(right.s7), new UInt(mask.s7)).value
        );
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static UInt16 bitselect(UInt16 left, UInt16 right, UInt16 mask) {
        return new UInt16(
                bitselect(new UInt(left.s0), new UInt(right.s0), new UInt(mask.s0)).value,
                bitselect(new UInt(left.s1), new UInt(right.s1), new UInt(mask.s1)).value,
                bitselect(new UInt(left.s2), new UInt(right.s2), new UInt(mask.s2)).value,
                bitselect(new UInt(left.s3), new UInt(right.s3), new UInt(mask.s3)).value,
                bitselect(new UInt(left.s4), new UInt(right.s4), new UInt(mask.s4)).value,
                bitselect(new UInt(left.s5), new UInt(right.s5), new UInt(mask.s5)).value,
                bitselect(new UInt(left.s6), new UInt(right.s6), new UInt(mask.s6)).value,
                bitselect(new UInt(left.s7), new UInt(right.s7), new UInt(mask.s7)).value,
                bitselect(new UInt(left.s8), new UInt(right.s8), new UInt(mask.s8)).value,
                bitselect(new UInt(left.s9), new UInt(right.s9), new UInt(mask.s9)).value,
                bitselect(new UInt(left.sa), new UInt(right.sa), new UInt(mask.sa)).value,
                bitselect(new UInt(left.sb), new UInt(right.sb), new UInt(mask.sb)).value,
                bitselect(new UInt(left.sc), new UInt(right.sc), new UInt(mask.sc)).value,
                bitselect(new UInt(left.sd), new UInt(right.sd), new UInt(mask.sd)).value,
                bitselect(new UInt(left.se), new UInt(right.se), new UInt(mask.se)).value,
                bitselect(new UInt(left.sf), new UInt(right.sf), new UInt(mask.sf)).value
        );
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static ULong8 bitselect(ULong8 left, ULong8 right, ULong8 mask) {
        return new ULong8(
                bitselect(new ULong(left.s0), new ULong(right.s0), new ULong(mask.s0)).value,
                bitselect(new ULong(left.s1), new ULong(right.s1), new ULong(mask.s1)).value,
                bitselect(new ULong(left.s2), new ULong(right.s2), new ULong(mask.s2)).value,
                bitselect(new ULong(left.s3), new ULong(right.s3), new ULong(mask.s3)).value,
                bitselect(new ULong(left.s4), new ULong(right.s4), new ULong(mask.s4)).value,
                bitselect(new ULong(left.s5), new ULong(right.s5), new ULong(mask.s5)).value,
                bitselect(new ULong(left.s6), new ULong(right.s6), new ULong(mask.s6)).value,
                bitselect(new ULong(left.s7), new ULong(right.s7), new ULong(mask.s7)).value
        );
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static ULong16 bitselect(ULong16 left, ULong16 right, ULong16 mask) {
        return new ULong16(
                bitselect(new ULong(left.s0), new ULong(right.s0), new ULong(mask.s0)).value,
                bitselect(new ULong(left.s1), new ULong(right.s1), new ULong(mask.s1)).value,
                bitselect(new ULong(left.s2), new ULong(right.s2), new ULong(mask.s2)).value,
                bitselect(new ULong(left.s3), new ULong(right.s3), new ULong(mask.s3)).value,
                bitselect(new ULong(left.s4), new ULong(right.s4), new ULong(mask.s4)).value,
                bitselect(new ULong(left.s5), new ULong(right.s5), new ULong(mask.s5)).value,
                bitselect(new ULong(left.s6), new ULong(right.s6), new ULong(mask.s6)).value,
                bitselect(new ULong(left.s7), new ULong(right.s7), new ULong(mask.s7)).value,
                bitselect(new ULong(left.s8), new ULong(right.s8), new ULong(mask.s8)).value,
                bitselect(new ULong(left.s9), new ULong(right.s9), new ULong(mask.s9)).value,
                bitselect(new ULong(left.sa), new ULong(right.sa), new ULong(mask.sa)).value,
                bitselect(new ULong(left.sb), new ULong(right.sb), new ULong(mask.sb)).value,
                bitselect(new ULong(left.sc), new ULong(right.sc), new ULong(mask.sc)).value,
                bitselect(new ULong(left.sd), new ULong(right.sd), new ULong(mask.sd)).value,
                bitselect(new ULong(left.se), new ULong(right.se), new ULong(mask.se)).value,
                bitselect(new ULong(left.sf), new ULong(right.sf), new ULong(mask.sf)).value
        );
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static UByte8 bitselect(UByte8 left, UByte8 right, UByte8 mask) {
        return new UByte8(
                (byte) bitselect(Byte.toUnsignedInt(left.s0), Byte.toUnsignedInt(right.s0), Byte.toUnsignedInt(mask.s0)),
                (byte) bitselect(Byte.toUnsignedInt(left.s1), Byte.toUnsignedInt(right.s1), Byte.toUnsignedInt(mask.s1)),
                (byte) bitselect(Byte.toUnsignedInt(left.s2), Byte.toUnsignedInt(right.s2), Byte.toUnsignedInt(mask.s2)),
                (byte) bitselect(Byte.toUnsignedInt(left.s3), Byte.toUnsignedInt(right.s3), Byte.toUnsignedInt(mask.s3)),
                (byte) bitselect(Byte.toUnsignedInt(left.s4), Byte.toUnsignedInt(right.s4), Byte.toUnsignedInt(mask.s4)),
                (byte) bitselect(Byte.toUnsignedInt(left.s5), Byte.toUnsignedInt(right.s5), Byte.toUnsignedInt(mask.s5)),
                (byte) bitselect(Byte.toUnsignedInt(left.s6), Byte.toUnsignedInt(right.s6), Byte.toUnsignedInt(mask.s6)),
                (byte) bitselect(Byte.toUnsignedInt(left.s7), Byte.toUnsignedInt(right.s7), Byte.toUnsignedInt(mask.s7))
        );
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static UByte16 bitselect(UByte16 left, UByte16 right, UByte16 mask) {
        return new UByte16(
                (byte) bitselect(Byte.toUnsignedInt(left.s0), Byte.toUnsignedInt(right.s0), Byte.toUnsignedInt(mask.s0)),
                (byte) bitselect(Byte.toUnsignedInt(left.s1), Byte.toUnsignedInt(right.s1), Byte.toUnsignedInt(mask.s1)),
                (byte) bitselect(Byte.toUnsignedInt(left.s2), Byte.toUnsignedInt(right.s2), Byte.toUnsignedInt(mask.s2)),
                (byte) bitselect(Byte.toUnsignedInt(left.s3), Byte.toUnsignedInt(right.s3), Byte.toUnsignedInt(mask.s3)),
                (byte) bitselect(Byte.toUnsignedInt(left.s4), Byte.toUnsignedInt(right.s4), Byte.toUnsignedInt(mask.s4)),
                (byte) bitselect(Byte.toUnsignedInt(left.s5), Byte.toUnsignedInt(right.s5), Byte.toUnsignedInt(mask.s5)),
                (byte) bitselect(Byte.toUnsignedInt(left.s6), Byte.toUnsignedInt(right.s6), Byte.toUnsignedInt(mask.s6)),
                (byte) bitselect(Byte.toUnsignedInt(left.s7), Byte.toUnsignedInt(right.s7), Byte.toUnsignedInt(mask.s7)),
                (byte) bitselect(Byte.toUnsignedInt(left.s8), Byte.toUnsignedInt(right.s8), Byte.toUnsignedInt(mask.s8)),
                (byte) bitselect(Byte.toUnsignedInt(left.s9), Byte.toUnsignedInt(right.s9), Byte.toUnsignedInt(mask.s9)),
                (byte) bitselect(Byte.toUnsignedInt(left.sa), Byte.toUnsignedInt(right.sa), Byte.toUnsignedInt(mask.sa)),
                (byte) bitselect(Byte.toUnsignedInt(left.sb), Byte.toUnsignedInt(right.sb), Byte.toUnsignedInt(mask.sb)),
                (byte) bitselect(Byte.toUnsignedInt(left.sc), Byte.toUnsignedInt(right.sc), Byte.toUnsignedInt(mask.sc)),
                (byte) bitselect(Byte.toUnsignedInt(left.sd), Byte.toUnsignedInt(right.sd), Byte.toUnsignedInt(mask.sd)),
                (byte) bitselect(Byte.toUnsignedInt(left.se), Byte.toUnsignedInt(right.se), Byte.toUnsignedInt(mask.se)),
                (byte) bitselect(Byte.toUnsignedInt(left.sf), Byte.toUnsignedInt(right.sf), Byte.toUnsignedInt(mask.sf))
        );
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static UShort8 bitselect(UShort8 left, UShort8 right, UShort8 mask) {
        return new UShort8(
                (short) bitselect(Short.toUnsignedInt(left.s0), Short.toUnsignedInt(right.s0), Short.toUnsignedInt(mask.s0)),
                (short) bitselect(Short.toUnsignedInt(left.s1), Short.toUnsignedInt(right.s1), Short.toUnsignedInt(mask.s1)),
                (short) bitselect(Short.toUnsignedInt(left.s2), Short.toUnsignedInt(right.s2), Short.toUnsignedInt(mask.s2)),
                (short) bitselect(Short.toUnsignedInt(left.s3), Short.toUnsignedInt(right.s3), Short.toUnsignedInt(mask.s3)),
                (short) bitselect(Short.toUnsignedInt(left.s4), Short.toUnsignedInt(right.s4), Short.toUnsignedInt(mask.s4)),
                (short) bitselect(Short.toUnsignedInt(left.s5), Short.toUnsignedInt(right.s5), Short.toUnsignedInt(mask.s5)),
                (short) bitselect(Short.toUnsignedInt(left.s6), Short.toUnsignedInt(right.s6), Short.toUnsignedInt(mask.s6)),
                (short) bitselect(Short.toUnsignedInt(left.s7), Short.toUnsignedInt(right.s7), Short.toUnsignedInt(mask.s7))
        );
    }

    @GPUIntrinsic(code = "bitselect({0}, {1}, {2})")
    public static UShort16 bitselect(UShort16 left, UShort16 right, UShort16 mask) {
        return new UShort16(
                (short) bitselect(Short.toUnsignedInt(left.s0), Short.toUnsignedInt(right.s0), Short.toUnsignedInt(mask.s0)),
                (short) bitselect(Short.toUnsignedInt(left.s1), Short.toUnsignedInt(right.s1), Short.toUnsignedInt(mask.s1)),
                (short) bitselect(Short.toUnsignedInt(left.s2), Short.toUnsignedInt(right.s2), Short.toUnsignedInt(mask.s2)),
                (short) bitselect(Short.toUnsignedInt(left.s3), Short.toUnsignedInt(right.s3), Short.toUnsignedInt(mask.s3)),
                (short) bitselect(Short.toUnsignedInt(left.s4), Short.toUnsignedInt(right.s4), Short.toUnsignedInt(mask.s4)),
                (short) bitselect(Short.toUnsignedInt(left.s5), Short.toUnsignedInt(right.s5), Short.toUnsignedInt(mask.s5)),
                (short) bitselect(Short.toUnsignedInt(left.s6), Short.toUnsignedInt(right.s6), Short.toUnsignedInt(mask.s6)),
                (short) bitselect(Short.toUnsignedInt(left.s7), Short.toUnsignedInt(right.s7), Short.toUnsignedInt(mask.s7)),
                (short) bitselect(Short.toUnsignedInt(left.s8), Short.toUnsignedInt(right.s8), Short.toUnsignedInt(mask.s8)),
                (short) bitselect(Short.toUnsignedInt(left.s9), Short.toUnsignedInt(right.s9), Short.toUnsignedInt(mask.s9)),
                (short) bitselect(Short.toUnsignedInt(left.sa), Short.toUnsignedInt(right.sa), Short.toUnsignedInt(mask.sa)),
                (short) bitselect(Short.toUnsignedInt(left.sb), Short.toUnsignedInt(right.sb), Short.toUnsignedInt(mask.sb)),
                (short) bitselect(Short.toUnsignedInt(left.sc), Short.toUnsignedInt(right.sc), Short.toUnsignedInt(mask.sc)),
                (short) bitselect(Short.toUnsignedInt(left.sd), Short.toUnsignedInt(right.sd), Short.toUnsignedInt(mask.sd)),
                (short) bitselect(Short.toUnsignedInt(left.se), Short.toUnsignedInt(right.se), Short.toUnsignedInt(mask.se)),
                (short) bitselect(Short.toUnsignedInt(left.sf), Short.toUnsignedInt(right.sf), Short.toUnsignedInt(mask.sf))
        );
    }

    @GPUIntrinsic(code = "nan(((uint) ({0})))")
    public static float nan(int nancode) {
        return Float.intBitsToFloat(0x7fc00000 | (nancode & 0x003fffff));
    }

    @GPUIntrinsic(code = "nan(((ulong) ({0})))")
    public static double nan(long nancode) {
        return Double.longBitsToDouble(0x7ff8000000000000L | (nancode & 0x0007ffffffffffffL));
    }

    @GPUIntrinsic(name = "convert_int")
    public static int convert_int(byte value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_int")
    public static int convert_int(short value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_int")
    public static int convert_int(char value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_int")
    public static int convert_int(int value) {
        return value;
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

    @GPUIntrinsic(name = "convert_int")
    public static int convert_int(UByte value) {
        return Byte.toUnsignedInt(value.value);
    }

    @GPUIntrinsic(name = "convert_int")
    public static int convert_int(UShort value) {
        return Short.toUnsignedInt(value.value);
    }

    @GPUIntrinsic(name = "convert_int")
    public static int convert_int(UInt value) {
        return value.value;
    }

    @GPUIntrinsic(name = "convert_int")
    public static int convert_int(ULong value) {
        return (int) value.value;
    }

    @GPUIntrinsic(name = "convert_long")
    public static long convert_long(byte value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_long")
    public static long convert_long(short value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_long")
    public static long convert_long(char value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_long")
    public static long convert_long(long value) {
        return value;
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

    @GPUIntrinsic(name = "convert_long")
    public static long convert_long(UByte value) {
        return Byte.toUnsignedLong(value.value);
    }

    @GPUIntrinsic(name = "convert_long")
    public static long convert_long(UShort value) {
        return Short.toUnsignedLong(value.value);
    }

    @GPUIntrinsic(name = "convert_long")
    public static long convert_long(UInt value) {
        return Integer.toUnsignedLong(value.value);
    }

    @GPUIntrinsic(name = "convert_long")
    public static long convert_long(ULong value) {
        return value.value;
    }

    @GPUIntrinsic(name = "convert_float")
    public static float convert_float(byte value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_float")
    public static float convert_float(short value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_float")
    public static float convert_float(char value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_float")
    public static float convert_float(float value) {
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

    @GPUIntrinsic(name = "convert_float")
    public static float convert_float(UByte value) {
        return Byte.toUnsignedInt(value.value);
    }

    @GPUIntrinsic(name = "convert_float")
    public static float convert_float(UShort value) {
        return Short.toUnsignedInt(value.value);
    }

    @GPUIntrinsic(name = "convert_float")
    public static float convert_float(UInt value) {
        return Integer.toUnsignedLong(value.value);
    }

    @GPUIntrinsic(name = "convert_float")
    public static float convert_float(ULong value) {
        return (float) value.value;
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float2 convert_float(Byte2 value) {
        return new Float2(convert_float(value.x), convert_float(value.y));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float3 convert_float(Byte3 value) {
        return new Float3(convert_float(value.x), convert_float(value.y), convert_float(value.z));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float4 convert_float(Byte4 value) {
        return new Float4(convert_float(value.x), convert_float(value.y), convert_float(value.z), convert_float(value.w));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float2 convert_float(Short2 value) {
        return new Float2(convert_float(value.x), convert_float(value.y));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float3 convert_float(Short3 value) {
        return new Float3(convert_float(value.x), convert_float(value.y), convert_float(value.z));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float4 convert_float(Short4 value) {
        return new Float4(convert_float(value.x), convert_float(value.y), convert_float(value.z), convert_float(value.w));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float2 convert_float(Int2 value) {
        return new Float2(convert_float(value.x), convert_float(value.y));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float3 convert_float(Int3 value) {
        return new Float3(convert_float(value.x), convert_float(value.y), convert_float(value.z));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float4 convert_float(Int4 value) {
        return new Float4(convert_float(value.x), convert_float(value.y), convert_float(value.z), convert_float(value.w));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float2 convert_float(Long2 value) {
        return new Float2(convert_float(value.x), convert_float(value.y));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float3 convert_float(Long3 value) {
        return new Float3(convert_float(value.x), convert_float(value.y), convert_float(value.z));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float4 convert_float(Long4 value) {
        return new Float4(convert_float(value.x), convert_float(value.y), convert_float(value.z), convert_float(value.w));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float2 convert_float(UByte2 value) {
        return new Float2(convert_float(new UByte(value.x)), convert_float(new UByte(value.y)));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float3 convert_float(UByte3 value) {
        return new Float3(convert_float(new UByte(value.x)), convert_float(new UByte(value.y)), convert_float(new UByte(value.z)));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float4 convert_float(UByte4 value) {
        return new Float4(convert_float(new UByte(value.x)), convert_float(new UByte(value.y)), convert_float(new UByte(value.z)), convert_float(new UByte(value.w)));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float2 convert_float(UShort2 value) {
        return new Float2(convert_float(new UShort(value.x)), convert_float(new UShort(value.y)));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float3 convert_float(UShort3 value) {
        return new Float3(convert_float(new UShort(value.x)), convert_float(new UShort(value.y)), convert_float(new UShort(value.z)));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float4 convert_float(UShort4 value) {
        return new Float4(convert_float(new UShort(value.x)), convert_float(new UShort(value.y)), convert_float(new UShort(value.z)), convert_float(new UShort(value.w)));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float2 convert_float(UInt2 value) {
        return new Float2(convert_float(new UInt(value.x)), convert_float(new UInt(value.y)));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float3 convert_float(UInt3 value) {
        return new Float3(convert_float(new UInt(value.x)), convert_float(new UInt(value.y)), convert_float(new UInt(value.z)));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float4 convert_float(UInt4 value) {
        return new Float4(convert_float(new UInt(value.x)), convert_float(new UInt(value.y)), convert_float(new UInt(value.z)), convert_float(new UInt(value.w)));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float2 convert_float(ULong2 value) {
        return new Float2(convert_float(new ULong(value.x)), convert_float(new ULong(value.y)));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float3 convert_float(ULong3 value) {
        return new Float3(convert_float(new ULong(value.x)), convert_float(new ULong(value.y)), convert_float(new ULong(value.z)));
    }

    @GPUIntrinsic(name = "convert_float")
    public static Float4 convert_float(ULong4 value) {
        return new Float4(convert_float(new ULong(value.x)), convert_float(new ULong(value.y)), convert_float(new ULong(value.z)), convert_float(new ULong(value.w)));
    }

    @GPUIntrinsic(name = "convert_double")
    public static double convert_double(byte value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_double")
    public static double convert_double(short value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_double")
    public static double convert_double(char value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_double")
    public static double convert_double(double value) {
        return value;
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

    @GPUIntrinsic(name = "convert_double")
    public static double convert_double(UByte value) {
        return Byte.toUnsignedInt(value.value);
    }

    @GPUIntrinsic(name = "convert_double")
    public static double convert_double(UShort value) {
        return Short.toUnsignedInt(value.value);
    }

    @GPUIntrinsic(name = "convert_double")
    public static double convert_double(UInt value) {
        return Integer.toUnsignedLong(value.value);
    }

    @GPUIntrinsic(name = "convert_double")
    public static double convert_double(ULong value) {
        return value.value;
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double2 convert_double(Byte2 value) {
        return new Double2(convert_double(value.x), convert_double(value.y));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double3 convert_double(Byte3 value) {
        return new Double3(convert_double(value.x), convert_double(value.y), convert_double(value.z));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double4 convert_double(Byte4 value) {
        return new Double4(convert_double(value.x), convert_double(value.y), convert_double(value.z), convert_double(value.w));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double2 convert_double(Short2 value) {
        return new Double2(convert_double(value.x), convert_double(value.y));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double3 convert_double(Short3 value) {
        return new Double3(convert_double(value.x), convert_double(value.y), convert_double(value.z));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double4 convert_double(Short4 value) {
        return new Double4(convert_double(value.x), convert_double(value.y), convert_double(value.z), convert_double(value.w));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double2 convert_double(Int2 value) {
        return new Double2(convert_double(value.x), convert_double(value.y));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double3 convert_double(Int3 value) {
        return new Double3(convert_double(value.x), convert_double(value.y), convert_double(value.z));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double4 convert_double(Int4 value) {
        return new Double4(convert_double(value.x), convert_double(value.y), convert_double(value.z), convert_double(value.w));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double2 convert_double(Long2 value) {
        return new Double2(convert_double(value.x), convert_double(value.y));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double3 convert_double(Long3 value) {
        return new Double3(convert_double(value.x), convert_double(value.y), convert_double(value.z));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double4 convert_double(Long4 value) {
        return new Double4(convert_double(value.x), convert_double(value.y), convert_double(value.z), convert_double(value.w));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double2 convert_double(UByte2 value) {
        return new Double2(convert_double(new UByte(value.x)), convert_double(new UByte(value.y)));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double3 convert_double(UByte3 value) {
        return new Double3(convert_double(new UByte(value.x)), convert_double(new UByte(value.y)), convert_double(new UByte(value.z)));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double4 convert_double(UByte4 value) {
        return new Double4(convert_double(new UByte(value.x)), convert_double(new UByte(value.y)), convert_double(new UByte(value.z)), convert_double(new UByte(value.w)));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double2 convert_double(UShort2 value) {
        return new Double2(convert_double(new UShort(value.x)), convert_double(new UShort(value.y)));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double3 convert_double(UShort3 value) {
        return new Double3(convert_double(new UShort(value.x)), convert_double(new UShort(value.y)), convert_double(new UShort(value.z)));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double4 convert_double(UShort4 value) {
        return new Double4(convert_double(new UShort(value.x)), convert_double(new UShort(value.y)), convert_double(new UShort(value.z)), convert_double(new UShort(value.w)));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double2 convert_double(UInt2 value) {
        return new Double2(convert_double(new UInt(value.x)), convert_double(new UInt(value.y)));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double3 convert_double(UInt3 value) {
        return new Double3(convert_double(new UInt(value.x)), convert_double(new UInt(value.y)), convert_double(new UInt(value.z)));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double4 convert_double(UInt4 value) {
        return new Double4(convert_double(new UInt(value.x)), convert_double(new UInt(value.y)), convert_double(new UInt(value.z)), convert_double(new UInt(value.w)));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double2 convert_double(ULong2 value) {
        return new Double2(convert_double(new ULong(value.x)), convert_double(new ULong(value.y)));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double3 convert_double(ULong3 value) {
        return new Double3(convert_double(new ULong(value.x)), convert_double(new ULong(value.y)), convert_double(new ULong(value.z)));
    }

    @GPUIntrinsic(name = "convert_double")
    public static Double4 convert_double(ULong4 value) {
        return new Double4(convert_double(new ULong(value.x)), convert_double(new ULong(value.y)), convert_double(new ULong(value.z)), convert_double(new ULong(value.w)));
    }

    @GPUIntrinsic(name = "convert_int")
    public static Int2 convert_int(Float2 value) {
        return new Int2(convert_int(value.x), convert_int(value.y));
    }

    @GPUIntrinsic(name = "convert_int")
    public static Int3 convert_int(Float3 value) {
        return new Int3(convert_int(value.x), convert_int(value.y), convert_int(value.z));
    }

    @GPUIntrinsic(name = "convert_int")
    public static Int4 convert_int(Float4 value) {
        return new Int4(convert_int(value.x), convert_int(value.y), convert_int(value.z), convert_int(value.w));
    }

    @GPUIntrinsic(name = "convert_int")
    public static Int2 convert_int(Double2 value) {
        return new Int2(convert_int(value.x), convert_int(value.y));
    }

    @GPUIntrinsic(name = "convert_int")
    public static Int3 convert_int(Double3 value) {
        return new Int3(convert_int(value.x), convert_int(value.y), convert_int(value.z));
    }

    @GPUIntrinsic(name = "convert_int")
    public static Int4 convert_int(Double4 value) {
        return new Int4(convert_int(value.x), convert_int(value.y), convert_int(value.z), convert_int(value.w));
    }

    @GPUIntrinsic(name = "convert_int")
    public static Int8 convert_int(UInt8 value) {
        return new Int8(convert_int(new UInt(value.s0)), convert_int(new UInt(value.s1)), convert_int(new UInt(value.s2)), convert_int(new UInt(value.s3)),
                convert_int(new UInt(value.s4)), convert_int(new UInt(value.s5)), convert_int(new UInt(value.s6)), convert_int(new UInt(value.s7)));
    }

    @GPUIntrinsic(name = "convert_int")
    public static Int16 convert_int(UInt16 value) {
        return new Int16(convert_int(new UInt(value.s0)), convert_int(new UInt(value.s1)), convert_int(new UInt(value.s2)), convert_int(new UInt(value.s3)),
                convert_int(new UInt(value.s4)), convert_int(new UInt(value.s5)), convert_int(new UInt(value.s6)), convert_int(new UInt(value.s7)),
                convert_int(new UInt(value.s8)), convert_int(new UInt(value.s9)), convert_int(new UInt(value.sa)), convert_int(new UInt(value.sb)),
                convert_int(new UInt(value.sc)), convert_int(new UInt(value.sd)), convert_int(new UInt(value.se)), convert_int(new UInt(value.sf)));
    }

    @GPUIntrinsic(name = "convert_int")
    public static Int8 convert_int(ULong8 value) {
        return new Int8(convert_int(new ULong(value.s0)), convert_int(new ULong(value.s1)), convert_int(new ULong(value.s2)), convert_int(new ULong(value.s3)),
                convert_int(new ULong(value.s4)), convert_int(new ULong(value.s5)), convert_int(new ULong(value.s6)), convert_int(new ULong(value.s7)));
    }

    @GPUIntrinsic(name = "convert_int")
    public static Int16 convert_int(ULong16 value) {
        return new Int16(convert_int(new ULong(value.s0)), convert_int(new ULong(value.s1)), convert_int(new ULong(value.s2)), convert_int(new ULong(value.s3)),
                convert_int(new ULong(value.s4)), convert_int(new ULong(value.s5)), convert_int(new ULong(value.s6)), convert_int(new ULong(value.s7)),
                convert_int(new ULong(value.s8)), convert_int(new ULong(value.s9)), convert_int(new ULong(value.sa)), convert_int(new ULong(value.sb)),
                convert_int(new ULong(value.sc)), convert_int(new ULong(value.sd)), convert_int(new ULong(value.se)), convert_int(new ULong(value.sf)));
    }

    @GPUIntrinsic(name = "convert_long")
    public static Long2 convert_long(Float2 value) {
        return new Long2(convert_long(value.x), convert_long(value.y));
    }

    @GPUIntrinsic(name = "convert_long")
    public static Long3 convert_long(Float3 value) {
        return new Long3(convert_long(value.x), convert_long(value.y), convert_long(value.z));
    }

    @GPUIntrinsic(name = "convert_long")
    public static Long4 convert_long(Float4 value) {
        return new Long4(convert_long(value.x), convert_long(value.y), convert_long(value.z), convert_long(value.w));
    }

    @GPUIntrinsic(name = "convert_long")
    public static Long2 convert_long(Double2 value) {
        return new Long2(convert_long(value.x), convert_long(value.y));
    }

    @GPUIntrinsic(name = "convert_long")
    public static Long3 convert_long(Double3 value) {
        return new Long3(convert_long(value.x), convert_long(value.y), convert_long(value.z));
    }

    @GPUIntrinsic(name = "convert_long")
    public static Long4 convert_long(Double4 value) {
        return new Long4(convert_long(value.x), convert_long(value.y), convert_long(value.z), convert_long(value.w));
    }

    @GPUIntrinsic(code = "convert_uint({0})")
    public static UInt2 convert_uint(Float2 value) {
        return new UInt2(convert_uint(value.x).value, convert_uint(value.y).value);
    }

    @GPUIntrinsic(code = "convert_uint({0})")
    public static UInt3 convert_uint(Float3 value) {
        return new UInt3(convert_uint(value.x).value, convert_uint(value.y).value, convert_uint(value.z).value);
    }

    @GPUIntrinsic(code = "convert_uint({0})")
    public static UInt4 convert_uint(Float4 value) {
        return new UInt4(convert_uint(value.x).value, convert_uint(value.y).value, convert_uint(value.z).value, convert_uint(value.w).value);
    }

    @GPUIntrinsic(code = "convert_uint({0})")
    public static UInt2 convert_uint(Double2 value) {
        return new UInt2(convert_uint(value.x).value, convert_uint(value.y).value);
    }

    @GPUIntrinsic(code = "convert_uint({0})")
    public static UInt3 convert_uint(Double3 value) {
        return new UInt3(convert_uint(value.x).value, convert_uint(value.y).value, convert_uint(value.z).value);
    }

    @GPUIntrinsic(code = "convert_uint({0})")
    public static UInt4 convert_uint(Double4 value) {
        return new UInt4(convert_uint(value.x).value, convert_uint(value.y).value, convert_uint(value.z).value, convert_uint(value.w).value);
    }

    @GPUIntrinsic(code = "convert_uint({0})")
    public static UInt8 convert_uint(Int8 value) {
        return new UInt8(convert_uint(value.s0).value, convert_uint(value.s1).value, convert_uint(value.s2).value, convert_uint(value.s3).value,
                convert_uint(value.s4).value, convert_uint(value.s5).value, convert_uint(value.s6).value, convert_uint(value.s7).value);
    }

    @GPUIntrinsic(code = "convert_uint({0})")
    public static UInt16 convert_uint(Int16 value) {
        return new UInt16(convert_uint(value.s0).value, convert_uint(value.s1).value, convert_uint(value.s2).value, convert_uint(value.s3).value,
                convert_uint(value.s4).value, convert_uint(value.s5).value, convert_uint(value.s6).value, convert_uint(value.s7).value,
                convert_uint(value.s8).value, convert_uint(value.s9).value, convert_uint(value.sa).value, convert_uint(value.sb).value,
                convert_uint(value.sc).value, convert_uint(value.sd).value, convert_uint(value.se).value, convert_uint(value.sf).value);
    }

    @GPUIntrinsic(code = "convert_ulong({0})")
    public static ULong2 convert_ulong(Float2 value) {
        return new ULong2(convert_ulong(value.x).value, convert_ulong(value.y).value);
    }

    @GPUIntrinsic(code = "convert_ulong({0})")
    public static ULong3 convert_ulong(Float3 value) {
        return new ULong3(convert_ulong(value.x).value, convert_ulong(value.y).value, convert_ulong(value.z).value);
    }

    @GPUIntrinsic(code = "convert_ulong({0})")
    public static ULong4 convert_ulong(Float4 value) {
        return new ULong4(convert_ulong(value.x).value, convert_ulong(value.y).value, convert_ulong(value.z).value, convert_ulong(value.w).value);
    }

    @GPUIntrinsic(code = "convert_ulong({0})")
    public static ULong2 convert_ulong(Double2 value) {
        return new ULong2(convert_ulong(value.x).value, convert_ulong(value.y).value);
    }

    @GPUIntrinsic(code = "convert_ulong({0})")
    public static ULong3 convert_ulong(Double3 value) {
        return new ULong3(convert_ulong(value.x).value, convert_ulong(value.y).value, convert_ulong(value.z).value);
    }

    @GPUIntrinsic(code = "convert_ulong({0})")
    public static ULong4 convert_ulong(Double4 value) {
        return new ULong4(convert_ulong(value.x).value, convert_ulong(value.y).value, convert_ulong(value.z).value, convert_ulong(value.w).value);
    }

    @GPUIntrinsic(name = "convert_char")
    public static byte convert_char(float value) {
        return (byte) value;
    }

    @GPUIntrinsic(name = "convert_char")
    public static byte convert_char(double value) {
        return (byte) value;
    }

    @GPUIntrinsic(name = "convert_char")
    public static byte convert_char(int value) {
        return (byte) value;
    }

    @GPUIntrinsic(name = "convert_char")
    public static byte convert_char(long value) {
        return (byte) value;
    }

    @GPUIntrinsic(name = "convert_char")
    public static byte convert_char(byte value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_char")
    public static byte convert_char(short value) {
        return (byte) value;
    }

    @GPUIntrinsic(name = "convert_char")
    public static byte convert_char(char value) {
        return (byte) value;
    }

    @GPUIntrinsic(name = "convert_char")
    public static byte convert_char(UByte value) {
        return value.value;
    }

    @GPUIntrinsic(name = "convert_char")
    public static byte convert_char(UShort value) {
        return (byte) value.value;
    }

    @GPUIntrinsic(name = "convert_char")
    public static byte convert_char(UInt value) {
        return (byte) value.value;
    }

    @GPUIntrinsic(name = "convert_char")
    public static byte convert_char(ULong value) {
        return (byte) value.value;
    }

    @GPUIntrinsic(name = "convert_char")
    public static Byte2 convert_char(Float2 value) {
        return new Byte2(convert_char(value.x), convert_char(value.y));
    }

    @GPUIntrinsic(name = "convert_char")
    public static Byte3 convert_char(Float3 value) {
        return new Byte3(convert_char(value.x), convert_char(value.y), convert_char(value.z));
    }

    @GPUIntrinsic(name = "convert_char")
    public static Byte4 convert_char(Float4 value) {
        return new Byte4(convert_char(value.x), convert_char(value.y), convert_char(value.z), convert_char(value.w));
    }

    @GPUIntrinsic(name = "convert_char")
    public static Byte2 convert_char(Double2 value) {
        return new Byte2(convert_char(value.x), convert_char(value.y));
    }

    @GPUIntrinsic(name = "convert_char")
    public static Byte3 convert_char(Double3 value) {
        return new Byte3(convert_char(value.x), convert_char(value.y), convert_char(value.z));
    }

    @GPUIntrinsic(name = "convert_char")
    public static Byte4 convert_char(Double4 value) {
        return new Byte4(convert_char(value.x), convert_char(value.y), convert_char(value.z), convert_char(value.w));
    }

    @GPUIntrinsic(code = "convert_uchar({0})")
    public static UByte convert_uchar(float value) {
        return new UByte((byte) value);
    }

    @GPUIntrinsic(code = "convert_uchar({0})")
    public static UByte convert_uchar(double value) {
        return new UByte((byte) value);
    }

    @GPUIntrinsic(code = "convert_uchar({0})")
    public static UByte convert_uchar(int value) {
        return new UByte((byte) value);
    }

    @GPUIntrinsic(code = "convert_uchar({0})")
    public static UByte convert_uchar(long value) {
        return new UByte((byte) value);
    }

    @GPUIntrinsic(code = "convert_uchar({0})")
    public static UByte convert_uchar(byte value) {
        return new UByte(value);
    }

    @GPUIntrinsic(code = "convert_uchar({0})")
    public static UByte convert_uchar(short value) {
        return new UByte((byte) value);
    }

    @GPUIntrinsic(code = "convert_uchar({0})")
    public static UByte convert_uchar(char value) {
        return new UByte((byte) value);
    }

    @GPUIntrinsic(code = "convert_uchar({0})")
    public static UByte convert_uchar(UByte value) {
        return value;
    }

    @GPUIntrinsic(code = "convert_uchar({0})")
    public static UByte convert_uchar(UShort value) {
        return new UByte((byte) value.value);
    }

    @GPUIntrinsic(code = "convert_uchar({0})")
    public static UByte convert_uchar(UInt value) {
        return new UByte((byte) value.value);
    }

    @GPUIntrinsic(code = "convert_uchar({0})")
    public static UByte convert_uchar(ULong value) {
        return new UByte((byte) value.value);
    }

    @GPUIntrinsic(name = "convert_short")
    public static short convert_short(float value) {
        return (short) value;
    }

    @GPUIntrinsic(name = "convert_short")
    public static short convert_short(double value) {
        return (short) value;
    }

    @GPUIntrinsic(name = "convert_short")
    public static short convert_short(int value) {
        return (short) value;
    }

    @GPUIntrinsic(name = "convert_short")
    public static short convert_short(long value) {
        return (short) value;
    }

    @GPUIntrinsic(name = "convert_short")
    public static short convert_short(byte value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_short")
    public static short convert_short(short value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_short")
    public static short convert_short(char value) {
        return (short) value;
    }

    @GPUIntrinsic(name = "convert_short")
    public static short convert_short(UByte value) {
        return (short) Byte.toUnsignedInt(value.value);
    }

    @GPUIntrinsic(name = "convert_short")
    public static short convert_short(UShort value) {
        return value.value;
    }

    @GPUIntrinsic(name = "convert_short")
    public static short convert_short(UInt value) {
        return (short) value.value;
    }

    @GPUIntrinsic(name = "convert_short")
    public static short convert_short(ULong value) {
        return (short) value.value;
    }

    @GPUIntrinsic(name = "convert_short")
    public static Short2 convert_short(Float2 value) {
        return new Short2(convert_short(value.x), convert_short(value.y));
    }

    @GPUIntrinsic(name = "convert_short")
    public static Short3 convert_short(Float3 value) {
        return new Short3(convert_short(value.x), convert_short(value.y), convert_short(value.z));
    }

    @GPUIntrinsic(name = "convert_short")
    public static Short4 convert_short(Float4 value) {
        return new Short4(convert_short(value.x), convert_short(value.y), convert_short(value.z), convert_short(value.w));
    }

    @GPUIntrinsic(name = "convert_short")
    public static Short2 convert_short(Double2 value) {
        return new Short2(convert_short(value.x), convert_short(value.y));
    }

    @GPUIntrinsic(name = "convert_short")
    public static Short3 convert_short(Double3 value) {
        return new Short3(convert_short(value.x), convert_short(value.y), convert_short(value.z));
    }

    @GPUIntrinsic(name = "convert_short")
    public static Short4 convert_short(Double4 value) {
        return new Short4(convert_short(value.x), convert_short(value.y), convert_short(value.z), convert_short(value.w));
    }

    @GPUIntrinsic(code = "convert_ushort({0})")
    public static UShort convert_ushort(float value) {
        return new UShort((short) value);
    }

    @GPUIntrinsic(code = "convert_ushort({0})")
    public static UShort convert_ushort(double value) {
        return new UShort((short) value);
    }

    @GPUIntrinsic(code = "convert_ushort({0})")
    public static UShort convert_ushort(int value) {
        return new UShort((short) value);
    }

    @GPUIntrinsic(code = "convert_ushort({0})")
    public static UShort convert_ushort(long value) {
        return new UShort((short) value);
    }

    @GPUIntrinsic(code = "convert_ushort({0})")
    public static UShort convert_ushort(byte value) {
        return new UShort((short) value);
    }

    @GPUIntrinsic(code = "convert_ushort({0})")
    public static UShort convert_ushort(short value) {
        return new UShort(value);
    }

    @GPUIntrinsic(code = "convert_ushort({0})")
    public static UShort convert_ushort(char value) {
        return new UShort((short) value);
    }

    @GPUIntrinsic(code = "convert_ushort({0})")
    public static UShort convert_ushort(UByte value) {
        return new UShort((short) Byte.toUnsignedInt(value.value));
    }

    @GPUIntrinsic(code = "convert_ushort({0})")
    public static UShort convert_ushort(UShort value) {
        return value;
    }

    @GPUIntrinsic(code = "convert_ushort({0})")
    public static UShort convert_ushort(UInt value) {
        return new UShort((short) value.value);
    }

    @GPUIntrinsic(code = "convert_ushort({0})")
    public static UShort convert_ushort(ULong value) {
        return new UShort((short) value.value);
    }

    @GPUIntrinsic(code = "convert_uchar({0})")
    public static UByte2 convert_uchar(Float2 value) {
        return new UByte2(convert_uchar(value.x).value, convert_uchar(value.y).value);
    }

    @GPUIntrinsic(code = "convert_uchar({0})")
    public static UByte3 convert_uchar(Float3 value) {
        return new UByte3(convert_uchar(value.x).value, convert_uchar(value.y).value, convert_uchar(value.z).value);
    }

    @GPUIntrinsic(code = "convert_uchar({0})")
    public static UByte4 convert_uchar(Float4 value) {
        return new UByte4(convert_uchar(value.x).value, convert_uchar(value.y).value, convert_uchar(value.z).value, convert_uchar(value.w).value);
    }

    @GPUIntrinsic(code = "convert_uchar({0})")
    public static UByte2 convert_uchar(Double2 value) {
        return new UByte2(convert_uchar(value.x).value, convert_uchar(value.y).value);
    }

    @GPUIntrinsic(code = "convert_uchar({0})")
    public static UByte3 convert_uchar(Double3 value) {
        return new UByte3(convert_uchar(value.x).value, convert_uchar(value.y).value, convert_uchar(value.z).value);
    }

    @GPUIntrinsic(code = "convert_uchar({0})")
    public static UByte4 convert_uchar(Double4 value) {
        return new UByte4(convert_uchar(value.x).value, convert_uchar(value.y).value, convert_uchar(value.z).value, convert_uchar(value.w).value);
    }

    @GPUIntrinsic(code = "convert_ushort({0})")
    public static UShort2 convert_ushort(Float2 value) {
        return new UShort2(convert_ushort(value.x).value, convert_ushort(value.y).value);
    }

    @GPUIntrinsic(code = "convert_ushort({0})")
    public static UShort3 convert_ushort(Float3 value) {
        return new UShort3(convert_ushort(value.x).value, convert_ushort(value.y).value, convert_ushort(value.z).value);
    }

    @GPUIntrinsic(code = "convert_ushort({0})")
    public static UShort4 convert_ushort(Float4 value) {
        return new UShort4(convert_ushort(value.x).value, convert_ushort(value.y).value, convert_ushort(value.z).value, convert_ushort(value.w).value);
    }

    @GPUIntrinsic(code = "convert_ushort({0})")
    public static UShort2 convert_ushort(Double2 value) {
        return new UShort2(convert_ushort(value.x).value, convert_ushort(value.y).value);
    }

    @GPUIntrinsic(code = "convert_ushort({0})")
    public static UShort3 convert_ushort(Double3 value) {
        return new UShort3(convert_ushort(value.x).value, convert_ushort(value.y).value, convert_ushort(value.z).value);
    }

    @GPUIntrinsic(code = "convert_ushort({0})")
    public static UShort4 convert_ushort(Double4 value) {
        return new UShort4(convert_ushort(value.x).value, convert_ushort(value.y).value, convert_ushort(value.z).value, convert_ushort(value.w).value);
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static int convert_int_sat(byte value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static int convert_int_sat(short value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static int convert_int_sat(char value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static int convert_int_sat(float value) {
        return saturateToSignedInt(value);
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static int convert_int_sat(double value) {
        return saturateToSignedInt(value);
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static int convert_int_sat(long value) {
        return saturateToSignedInt(value);
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static int convert_int_sat(int value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static int convert_int_sat(UByte value) {
        return Byte.toUnsignedInt(value.value);
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static int convert_int_sat(UShort value) {
        return Short.toUnsignedInt(value.value);
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static int convert_int_sat(UInt value) {
        return value.value < 0 ? Integer.MAX_VALUE : value.value;
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static int convert_int_sat(ULong value) {
        return value.value < 0L ? Integer.MAX_VALUE : saturateToSignedInt(value.value);
    }

    @GPUIntrinsic(name = "convert_long_sat")
    public static long convert_long_sat(byte value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_long_sat")
    public static long convert_long_sat(short value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_long_sat")
    public static long convert_long_sat(char value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_long_sat")
    public static long convert_long_sat(float value) {
        return saturateToSignedLong(value);
    }

    @GPUIntrinsic(name = "convert_long_sat")
    public static long convert_long_sat(double value) {
        return saturateToSignedLong(value);
    }

    @GPUIntrinsic(name = "convert_long_sat")
    public static long convert_long_sat(int value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_long_sat")
    public static long convert_long_sat(long value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_long_sat")
    public static long convert_long_sat(UByte value) {
        return Byte.toUnsignedInt(value.value);
    }

    @GPUIntrinsic(name = "convert_long_sat")
    public static long convert_long_sat(UShort value) {
        return Short.toUnsignedInt(value.value);
    }

    @GPUIntrinsic(name = "convert_long_sat")
    public static long convert_long_sat(UInt value) {
        return Integer.toUnsignedLong(value.value);
    }

    @GPUIntrinsic(name = "convert_long_sat")
    public static long convert_long_sat(ULong value) {
        return value.value < 0L ? Long.MAX_VALUE : value.value;
    }

    @GPUIntrinsic(name = "convert_char_sat")
    public static byte convert_char_sat(byte value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_char_sat")
    public static byte convert_char_sat(short value) {
        return saturateToSignedByte((int) value);
    }

    @GPUIntrinsic(name = "convert_char_sat")
    public static byte convert_char_sat(char value) {
        return saturateToSignedByte((int) value);
    }

    @GPUIntrinsic(name = "convert_char_sat")
    public static byte convert_char_sat(float value) {
        return saturateToSignedByte(value);
    }

    @GPUIntrinsic(name = "convert_char_sat")
    public static byte convert_char_sat(double value) {
        return saturateToSignedByte(value);
    }

    @GPUIntrinsic(name = "convert_char_sat")
    public static byte convert_char_sat(int value) {
        return saturateToSignedByte(value);
    }

    @GPUIntrinsic(name = "convert_char_sat")
    public static byte convert_char_sat(long value) {
        return saturateToSignedByte(value);
    }

    @GPUIntrinsic(name = "convert_char_sat")
    public static byte convert_char_sat(UByte value) {
        return saturateToSignedByte(Byte.toUnsignedInt(value.value));
    }

    @GPUIntrinsic(name = "convert_char_sat")
    public static byte convert_char_sat(UShort value) {
        return saturateToSignedByte(Short.toUnsignedInt(value.value));
    }

    @GPUIntrinsic(name = "convert_char_sat")
    public static byte convert_char_sat(UInt value) {
        return saturateToSignedByte(Integer.toUnsignedLong(value.value));
    }

    @GPUIntrinsic(name = "convert_char_sat")
    public static byte convert_char_sat(ULong value) {
        return value.value < 0L ? Byte.MAX_VALUE : saturateToSignedByte(value.value);
    }

    @GPUIntrinsic(name = "convert_char_sat")
    public static Byte2 convert_char_sat(Float2 value) {
        return new Byte2(convert_char_sat(value.x), convert_char_sat(value.y));
    }

    @GPUIntrinsic(name = "convert_char_sat")
    public static Byte3 convert_char_sat(Float3 value) {
        return new Byte3(convert_char_sat(value.x), convert_char_sat(value.y), convert_char_sat(value.z));
    }

    @GPUIntrinsic(name = "convert_char_sat")
    public static Byte4 convert_char_sat(Float4 value) {
        return new Byte4(convert_char_sat(value.x), convert_char_sat(value.y), convert_char_sat(value.z), convert_char_sat(value.w));
    }

    @GPUIntrinsic(name = "convert_char_sat")
    public static Byte2 convert_char_sat(Double2 value) {
        return new Byte2(convert_char_sat(value.x), convert_char_sat(value.y));
    }

    @GPUIntrinsic(name = "convert_char_sat")
    public static Byte3 convert_char_sat(Double3 value) {
        return new Byte3(convert_char_sat(value.x), convert_char_sat(value.y), convert_char_sat(value.z));
    }

    @GPUIntrinsic(name = "convert_char_sat")
    public static Byte4 convert_char_sat(Double4 value) {
        return new Byte4(convert_char_sat(value.x), convert_char_sat(value.y), convert_char_sat(value.z), convert_char_sat(value.w));
    }

    @GPUIntrinsic(code = "convert_uchar_sat({0})")
    public static UByte convert_uchar_sat(byte value) {
        return new UByte(saturateToUnsignedByte((int) value));
    }

    @GPUIntrinsic(code = "convert_uchar_sat({0})")
    public static UByte convert_uchar_sat(short value) {
        return new UByte(saturateToUnsignedByte((int) value));
    }

    @GPUIntrinsic(code = "convert_uchar_sat({0})")
    public static UByte convert_uchar_sat(char value) {
        return new UByte(saturateToUnsignedByte((int) value));
    }

    @GPUIntrinsic(code = "convert_uchar_sat({0})")
    public static UByte convert_uchar_sat(float value) {
        return new UByte(saturateToUnsignedByte(value));
    }

    @GPUIntrinsic(code = "convert_uchar_sat({0})")
    public static UByte convert_uchar_sat(double value) {
        return new UByte(saturateToUnsignedByte(value));
    }

    @GPUIntrinsic(code = "convert_uchar_sat({0})")
    public static UByte convert_uchar_sat(int value) {
        return new UByte(saturateToUnsignedByte(value));
    }

    @GPUIntrinsic(code = "convert_uchar_sat({0})")
    public static UByte convert_uchar_sat(long value) {
        return new UByte(saturateToUnsignedByte(value));
    }

    @GPUIntrinsic(code = "convert_uchar_sat({0})")
    public static UByte convert_uchar_sat(UByte value) {
        return value;
    }

    @GPUIntrinsic(code = "convert_uchar_sat({0})")
    public static UByte convert_uchar_sat(UShort value) {
        return new UByte(saturateToUnsignedByte(Short.toUnsignedInt(value.value)));
    }

    @GPUIntrinsic(code = "convert_uchar_sat({0})")
    public static UByte convert_uchar_sat(UInt value) {
        return new UByte(saturateToUnsignedByte(Integer.toUnsignedLong(value.value)));
    }

    @GPUIntrinsic(code = "convert_uchar_sat({0})")
    public static UByte convert_uchar_sat(ULong value) {
        return new UByte(value.value < 0L ? (byte) 0xFF : saturateToUnsignedByte(value.value));
    }

    @GPUIntrinsic(name = "convert_short_sat")
    public static short convert_short_sat(byte value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_short_sat")
    public static short convert_short_sat(short value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_short_sat")
    public static short convert_short_sat(char value) {
        return saturateToSignedShort((int) value);
    }

    @GPUIntrinsic(name = "convert_short_sat")
    public static short convert_short_sat(float value) {
        return saturateToSignedShort(value);
    }

    @GPUIntrinsic(name = "convert_short_sat")
    public static short convert_short_sat(double value) {
        return saturateToSignedShort(value);
    }

    @GPUIntrinsic(name = "convert_short_sat")
    public static short convert_short_sat(int value) {
        return saturateToSignedShort(value);
    }

    @GPUIntrinsic(name = "convert_short_sat")
    public static short convert_short_sat(long value) {
        return saturateToSignedShort(value);
    }

    @GPUIntrinsic(name = "convert_short_sat")
    public static short convert_short_sat(UByte value) {
        return (short) Byte.toUnsignedInt(value.value);
    }

    @GPUIntrinsic(name = "convert_short_sat")
    public static short convert_short_sat(UShort value) {
        return saturateToSignedShort(Short.toUnsignedInt(value.value));
    }

    @GPUIntrinsic(name = "convert_short_sat")
    public static short convert_short_sat(UInt value) {
        return saturateToSignedShort(Integer.toUnsignedLong(value.value));
    }

    @GPUIntrinsic(name = "convert_short_sat")
    public static short convert_short_sat(ULong value) {
        return value.value < 0L ? Short.MAX_VALUE : saturateToSignedShort(value.value);
    }

    @GPUIntrinsic(name = "convert_short_sat")
    public static Short2 convert_short_sat(Float2 value) {
        return new Short2(convert_short_sat(value.x), convert_short_sat(value.y));
    }

    @GPUIntrinsic(name = "convert_short_sat")
    public static Short3 convert_short_sat(Float3 value) {
        return new Short3(convert_short_sat(value.x), convert_short_sat(value.y), convert_short_sat(value.z));
    }

    @GPUIntrinsic(name = "convert_short_sat")
    public static Short4 convert_short_sat(Float4 value) {
        return new Short4(convert_short_sat(value.x), convert_short_sat(value.y), convert_short_sat(value.z), convert_short_sat(value.w));
    }

    @GPUIntrinsic(name = "convert_short_sat")
    public static Short2 convert_short_sat(Double2 value) {
        return new Short2(convert_short_sat(value.x), convert_short_sat(value.y));
    }

    @GPUIntrinsic(name = "convert_short_sat")
    public static Short3 convert_short_sat(Double3 value) {
        return new Short3(convert_short_sat(value.x), convert_short_sat(value.y), convert_short_sat(value.z));
    }

    @GPUIntrinsic(name = "convert_short_sat")
    public static Short4 convert_short_sat(Double4 value) {
        return new Short4(convert_short_sat(value.x), convert_short_sat(value.y), convert_short_sat(value.z), convert_short_sat(value.w));
    }

    @GPUIntrinsic(code = "convert_ushort_sat({0})")
    public static UShort convert_ushort_sat(byte value) {
        return new UShort(saturateToUnsignedShort((int) value));
    }

    @GPUIntrinsic(code = "convert_ushort_sat({0})")
    public static UShort convert_ushort_sat(short value) {
        return new UShort(saturateToUnsignedShort((int) value));
    }

    @GPUIntrinsic(code = "convert_ushort_sat({0})")
    public static UShort convert_ushort_sat(char value) {
        return new UShort(saturateToUnsignedShort((int) value));
    }

    @GPUIntrinsic(code = "convert_ushort_sat({0})")
    public static UShort convert_ushort_sat(float value) {
        return new UShort(saturateToUnsignedShort(value));
    }

    @GPUIntrinsic(code = "convert_ushort_sat({0})")
    public static UShort convert_ushort_sat(double value) {
        return new UShort(saturateToUnsignedShort(value));
    }

    @GPUIntrinsic(code = "convert_ushort_sat({0})")
    public static UShort convert_ushort_sat(int value) {
        return new UShort(saturateToUnsignedShort(value));
    }

    @GPUIntrinsic(code = "convert_ushort_sat({0})")
    public static UShort convert_ushort_sat(long value) {
        return new UShort(saturateToUnsignedShort(value));
    }

    @GPUIntrinsic(code = "convert_ushort_sat({0})")
    public static UShort convert_ushort_sat(UByte value) {
        return new UShort((short) Byte.toUnsignedInt(value.value));
    }

    @GPUIntrinsic(code = "convert_ushort_sat({0})")
    public static UShort convert_ushort_sat(UShort value) {
        return value;
    }

    @GPUIntrinsic(code = "convert_ushort_sat({0})")
    public static UShort convert_ushort_sat(UInt value) {
        return new UShort(saturateToUnsignedShort(Integer.toUnsignedLong(value.value)));
    }

    @GPUIntrinsic(code = "convert_ushort_sat({0})")
    public static UShort convert_ushort_sat(ULong value) {
        return new UShort(value.value < 0L ? (short) 0xFFFF : saturateToUnsignedShort(value.value));
    }

    @GPUIntrinsic(code = "convert_uchar_sat({0})")
    public static UByte2 convert_uchar_sat(Float2 value) {
        return new UByte2(convert_uchar_sat(value.x).value, convert_uchar_sat(value.y).value);
    }

    @GPUIntrinsic(code = "convert_uchar_sat({0})")
    public static UByte3 convert_uchar_sat(Float3 value) {
        return new UByte3(convert_uchar_sat(value.x).value, convert_uchar_sat(value.y).value, convert_uchar_sat(value.z).value);
    }

    @GPUIntrinsic(code = "convert_uchar_sat({0})")
    public static UByte4 convert_uchar_sat(Float4 value) {
        return new UByte4(convert_uchar_sat(value.x).value, convert_uchar_sat(value.y).value, convert_uchar_sat(value.z).value, convert_uchar_sat(value.w).value);
    }

    @GPUIntrinsic(code = "convert_uchar_sat({0})")
    public static UByte2 convert_uchar_sat(Double2 value) {
        return new UByte2(convert_uchar_sat(value.x).value, convert_uchar_sat(value.y).value);
    }

    @GPUIntrinsic(code = "convert_uchar_sat({0})")
    public static UByte3 convert_uchar_sat(Double3 value) {
        return new UByte3(convert_uchar_sat(value.x).value, convert_uchar_sat(value.y).value, convert_uchar_sat(value.z).value);
    }

    @GPUIntrinsic(code = "convert_uchar_sat({0})")
    public static UByte4 convert_uchar_sat(Double4 value) {
        return new UByte4(convert_uchar_sat(value.x).value, convert_uchar_sat(value.y).value, convert_uchar_sat(value.z).value, convert_uchar_sat(value.w).value);
    }

    @GPUIntrinsic(code = "convert_ushort_sat({0})")
    public static UShort2 convert_ushort_sat(Float2 value) {
        return new UShort2(convert_ushort_sat(value.x).value, convert_ushort_sat(value.y).value);
    }

    @GPUIntrinsic(code = "convert_ushort_sat({0})")
    public static UShort3 convert_ushort_sat(Float3 value) {
        return new UShort3(convert_ushort_sat(value.x).value, convert_ushort_sat(value.y).value, convert_ushort_sat(value.z).value);
    }

    @GPUIntrinsic(code = "convert_ushort_sat({0})")
    public static UShort4 convert_ushort_sat(Float4 value) {
        return new UShort4(convert_ushort_sat(value.x).value, convert_ushort_sat(value.y).value, convert_ushort_sat(value.z).value, convert_ushort_sat(value.w).value);
    }

    @GPUIntrinsic(code = "convert_ushort_sat({0})")
    public static UShort2 convert_ushort_sat(Double2 value) {
        return new UShort2(convert_ushort_sat(value.x).value, convert_ushort_sat(value.y).value);
    }

    @GPUIntrinsic(code = "convert_ushort_sat({0})")
    public static UShort3 convert_ushort_sat(Double3 value) {
        return new UShort3(convert_ushort_sat(value.x).value, convert_ushort_sat(value.y).value, convert_ushort_sat(value.z).value);
    }

    @GPUIntrinsic(code = "convert_ushort_sat({0})")
    public static UShort4 convert_ushort_sat(Double4 value) {
        return new UShort4(convert_ushort_sat(value.x).value, convert_ushort_sat(value.y).value, convert_ushort_sat(value.z).value, convert_ushort_sat(value.w).value);
    }

    @GPUIntrinsic(code = "convert_uint_sat({0})")
    public static UInt convert_uint_sat(byte value) {
        return new UInt(saturateToUnsignedInt((int) value));
    }

    @GPUIntrinsic(code = "convert_uint_sat({0})")
    public static UInt convert_uint_sat(short value) {
        return new UInt(saturateToUnsignedInt((int) value));
    }

    @GPUIntrinsic(code = "convert_uint_sat({0})")
    public static UInt convert_uint_sat(char value) {
        return new UInt(value);
    }

    @GPUIntrinsic(code = "convert_uint_sat({0})")
    public static UInt convert_uint_sat(float value) {
        return new UInt(saturateToUnsignedInt(value));
    }

    @GPUIntrinsic(code = "convert_uint_sat({0})")
    public static UInt convert_uint_sat(double value) {
        return new UInt(saturateToUnsignedInt(value));
    }

    @GPUIntrinsic(code = "convert_uint_sat({0})")
    public static UInt convert_uint_sat(int value) {
        return new UInt(saturateToUnsignedInt(value));
    }

    @GPUIntrinsic(code = "convert_uint_sat({0})")
    public static UInt convert_uint_sat(long value) {
        return new UInt(saturateToUnsignedInt(value));
    }

    @GPUIntrinsic(code = "convert_uint_sat({0})")
    public static UInt convert_uint_sat(UByte value) {
        return new UInt(Byte.toUnsignedInt(value.value));
    }

    @GPUIntrinsic(code = "convert_uint_sat({0})")
    public static UInt convert_uint_sat(UShort value) {
        return new UInt(Short.toUnsignedInt(value.value));
    }

    @GPUIntrinsic(code = "convert_uint_sat({0})")
    public static UInt convert_uint_sat(UInt value) {
        return value;
    }

    @GPUIntrinsic(code = "convert_uint_sat({0})")
    public static UInt convert_uint_sat(ULong value) {
        return new UInt(value.value < 0L ? -1 : (int) value.value);
    }

    @GPUIntrinsic(code = "convert_ulong_sat({0})")
    public static ULong convert_ulong_sat(byte value) {
        return new ULong(saturateToUnsignedLong((int) value));
    }

    @GPUIntrinsic(code = "convert_ulong_sat({0})")
    public static ULong convert_ulong_sat(short value) {
        return new ULong(saturateToUnsignedLong((int) value));
    }

    @GPUIntrinsic(code = "convert_ulong_sat({0})")
    public static ULong convert_ulong_sat(char value) {
        return new ULong(value);
    }

    @GPUIntrinsic(code = "convert_ulong_sat({0})")
    public static ULong convert_ulong_sat(float value) {
        return new ULong(saturateToUnsignedLong(value));
    }

    @GPUIntrinsic(code = "convert_ulong_sat({0})")
    public static ULong convert_ulong_sat(double value) {
        return new ULong(saturateToUnsignedLong(value));
    }

    @GPUIntrinsic(code = "convert_ulong_sat({0})")
    public static ULong convert_ulong_sat(int value) {
        return new ULong(saturateToUnsignedLong(value));
    }

    @GPUIntrinsic(code = "convert_ulong_sat({0})")
    public static ULong convert_ulong_sat(long value) {
        return new ULong(saturateToUnsignedLong(value));
    }

    @GPUIntrinsic(code = "convert_ulong_sat({0})")
    public static ULong convert_ulong_sat(UByte value) {
        return new ULong(Byte.toUnsignedInt(value.value));
    }

    @GPUIntrinsic(code = "convert_ulong_sat({0})")
    public static ULong convert_ulong_sat(UShort value) {
        return new ULong(Short.toUnsignedInt(value.value));
    }

    @GPUIntrinsic(code = "convert_ulong_sat({0})")
    public static ULong convert_ulong_sat(UInt value) {
        return new ULong(Integer.toUnsignedLong(value.value));
    }

    @GPUIntrinsic(code = "convert_ulong_sat({0})")
    public static ULong convert_ulong_sat(ULong value) {
        return value;
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static Int2 convert_int_sat(Float2 value) {
        return new Int2(convert_int_sat(value.x), convert_int_sat(value.y));
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static Int3 convert_int_sat(Float3 value) {
        return new Int3(convert_int_sat(value.x), convert_int_sat(value.y), convert_int_sat(value.z));
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static Int4 convert_int_sat(Float4 value) {
        return new Int4(convert_int_sat(value.x), convert_int_sat(value.y), convert_int_sat(value.z), convert_int_sat(value.w));
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static Int2 convert_int_sat(Double2 value) {
        return new Int2(convert_int_sat(value.x), convert_int_sat(value.y));
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static Int3 convert_int_sat(Double3 value) {
        return new Int3(convert_int_sat(value.x), convert_int_sat(value.y), convert_int_sat(value.z));
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static Int4 convert_int_sat(Double4 value) {
        return new Int4(convert_int_sat(value.x), convert_int_sat(value.y), convert_int_sat(value.z), convert_int_sat(value.w));
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static Int8 convert_int_sat(UInt8 value) {
        return new Int8(convert_int_sat(new UInt(value.s0)), convert_int_sat(new UInt(value.s1)), convert_int_sat(new UInt(value.s2)), convert_int_sat(new UInt(value.s3)),
                convert_int_sat(new UInt(value.s4)), convert_int_sat(new UInt(value.s5)), convert_int_sat(new UInt(value.s6)), convert_int_sat(new UInt(value.s7)));
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static Int16 convert_int_sat(UInt16 value) {
        return new Int16(convert_int_sat(new UInt(value.s0)), convert_int_sat(new UInt(value.s1)), convert_int_sat(new UInt(value.s2)), convert_int_sat(new UInt(value.s3)),
                convert_int_sat(new UInt(value.s4)), convert_int_sat(new UInt(value.s5)), convert_int_sat(new UInt(value.s6)), convert_int_sat(new UInt(value.s7)),
                convert_int_sat(new UInt(value.s8)), convert_int_sat(new UInt(value.s9)), convert_int_sat(new UInt(value.sa)), convert_int_sat(new UInt(value.sb)),
                convert_int_sat(new UInt(value.sc)), convert_int_sat(new UInt(value.sd)), convert_int_sat(new UInt(value.se)), convert_int_sat(new UInt(value.sf)));
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static Int8 convert_int_sat(ULong8 value) {
        return new Int8(convert_int_sat(new ULong(value.s0)), convert_int_sat(new ULong(value.s1)), convert_int_sat(new ULong(value.s2)), convert_int_sat(new ULong(value.s3)),
                convert_int_sat(new ULong(value.s4)), convert_int_sat(new ULong(value.s5)), convert_int_sat(new ULong(value.s6)), convert_int_sat(new ULong(value.s7)));
    }

    @GPUIntrinsic(name = "convert_int_sat")
    public static Int16 convert_int_sat(ULong16 value) {
        return new Int16(convert_int_sat(new ULong(value.s0)), convert_int_sat(new ULong(value.s1)), convert_int_sat(new ULong(value.s2)), convert_int_sat(new ULong(value.s3)),
                convert_int_sat(new ULong(value.s4)), convert_int_sat(new ULong(value.s5)), convert_int_sat(new ULong(value.s6)), convert_int_sat(new ULong(value.s7)),
                convert_int_sat(new ULong(value.s8)), convert_int_sat(new ULong(value.s9)), convert_int_sat(new ULong(value.sa)), convert_int_sat(new ULong(value.sb)),
                convert_int_sat(new ULong(value.sc)), convert_int_sat(new ULong(value.sd)), convert_int_sat(new ULong(value.se)), convert_int_sat(new ULong(value.sf)));
    }

    @GPUIntrinsic(name = "convert_long_sat")
    public static Long2 convert_long_sat(Float2 value) {
        return new Long2(convert_long_sat(value.x), convert_long_sat(value.y));
    }

    @GPUIntrinsic(name = "convert_long_sat")
    public static Long3 convert_long_sat(Float3 value) {
        return new Long3(convert_long_sat(value.x), convert_long_sat(value.y), convert_long_sat(value.z));
    }

    @GPUIntrinsic(name = "convert_long_sat")
    public static Long4 convert_long_sat(Float4 value) {
        return new Long4(convert_long_sat(value.x), convert_long_sat(value.y), convert_long_sat(value.z), convert_long_sat(value.w));
    }

    @GPUIntrinsic(name = "convert_long_sat")
    public static Long2 convert_long_sat(Double2 value) {
        return new Long2(convert_long_sat(value.x), convert_long_sat(value.y));
    }

    @GPUIntrinsic(name = "convert_long_sat")
    public static Long3 convert_long_sat(Double3 value) {
        return new Long3(convert_long_sat(value.x), convert_long_sat(value.y), convert_long_sat(value.z));
    }

    @GPUIntrinsic(name = "convert_long_sat")
    public static Long4 convert_long_sat(Double4 value) {
        return new Long4(convert_long_sat(value.x), convert_long_sat(value.y), convert_long_sat(value.z), convert_long_sat(value.w));
    }

    @GPUIntrinsic(code = "convert_uint_sat({0})")
    public static UInt2 convert_uint_sat(Float2 value) {
        return new UInt2(convert_uint_sat(value.x).value, convert_uint_sat(value.y).value);
    }

    @GPUIntrinsic(code = "convert_uint_sat({0})")
    public static UInt3 convert_uint_sat(Float3 value) {
        return new UInt3(convert_uint_sat(value.x).value, convert_uint_sat(value.y).value, convert_uint_sat(value.z).value);
    }

    @GPUIntrinsic(code = "convert_uint_sat({0})")
    public static UInt4 convert_uint_sat(Float4 value) {
        return new UInt4(convert_uint_sat(value.x).value, convert_uint_sat(value.y).value, convert_uint_sat(value.z).value, convert_uint_sat(value.w).value);
    }

    @GPUIntrinsic(code = "convert_uint_sat({0})")
    public static UInt2 convert_uint_sat(Double2 value) {
        return new UInt2(convert_uint_sat(value.x).value, convert_uint_sat(value.y).value);
    }

    @GPUIntrinsic(code = "convert_uint_sat({0})")
    public static UInt3 convert_uint_sat(Double3 value) {
        return new UInt3(convert_uint_sat(value.x).value, convert_uint_sat(value.y).value, convert_uint_sat(value.z).value);
    }

    @GPUIntrinsic(code = "convert_uint_sat({0})")
    public static UInt4 convert_uint_sat(Double4 value) {
        return new UInt4(convert_uint_sat(value.x).value, convert_uint_sat(value.y).value, convert_uint_sat(value.z).value, convert_uint_sat(value.w).value);
    }

    @GPUIntrinsic(code = "convert_ulong_sat({0})")
    public static ULong2 convert_ulong_sat(Float2 value) {
        return new ULong2(convert_ulong_sat(value.x).value, convert_ulong_sat(value.y).value);
    }

    @GPUIntrinsic(code = "convert_ulong_sat({0})")
    public static ULong3 convert_ulong_sat(Float3 value) {
        return new ULong3(convert_ulong_sat(value.x).value, convert_ulong_sat(value.y).value, convert_ulong_sat(value.z).value);
    }

    @GPUIntrinsic(code = "convert_ulong_sat({0})")
    public static ULong4 convert_ulong_sat(Float4 value) {
        return new ULong4(convert_ulong_sat(value.x).value, convert_ulong_sat(value.y).value, convert_ulong_sat(value.z).value, convert_ulong_sat(value.w).value);
    }

    @GPUIntrinsic(code = "convert_ulong_sat({0})")
    public static ULong2 convert_ulong_sat(Double2 value) {
        return new ULong2(convert_ulong_sat(value.x).value, convert_ulong_sat(value.y).value);
    }

    @GPUIntrinsic(code = "convert_ulong_sat({0})")
    public static ULong3 convert_ulong_sat(Double3 value) {
        return new ULong3(convert_ulong_sat(value.x).value, convert_ulong_sat(value.y).value, convert_ulong_sat(value.z).value);
    }

    @GPUIntrinsic(code = "convert_ulong_sat({0})")
    public static ULong4 convert_ulong_sat(Double4 value) {
        return new ULong4(convert_ulong_sat(value.x).value, convert_ulong_sat(value.y).value, convert_ulong_sat(value.z).value, convert_ulong_sat(value.w).value);
    }

    @GPUIntrinsic(code = "((uint) ({0}))")
    public static UInt convert_uint(byte value) {
        return new UInt(value);
    }

    @GPUIntrinsic(code = "((uint) ({0}))")
    public static UInt convert_uint(short value) {
        return new UInt(value);
    }

    @GPUIntrinsic(code = "((uint) ({0}))")
    public static UInt convert_uint(char value) {
        return new UInt(value);
    }

    @GPUIntrinsic(code = "((uint) ({0}))")
    public static UInt convert_uint(int value) {
        return new UInt(value);
    }

    @GPUIntrinsic(code = "((uint) ({0}))")
    public static UInt convert_uint(UByte value) {
        return new UInt(Byte.toUnsignedInt(value.value));
    }

    @GPUIntrinsic(code = "((uint) ({0}))")
    public static UInt convert_uint(UShort value) {
        return new UInt(Short.toUnsignedInt(value.value));
    }

    @GPUIntrinsic(code = "((uint) ({0}))")
    public static UInt convert_uint(UInt value) {
        return new UInt(value.value);
    }

    @GPUIntrinsic(code = "((uint) ({0}))")
    public static UInt convert_uint(ULong value) {
        return new UInt((int) value.value);
    }

    @GPUIntrinsic(code = "((uint) ({0}))")
    public static UInt convert_uint(long value) {
        return new UInt((int) value);
    }

    @GPUIntrinsic(code = "((uint) ({0}))")
    public static UInt convert_uint(float value) {
        return new UInt((int) value);
    }

    @GPUIntrinsic(code = "((uint) ({0}))")
    public static UInt convert_uint(double value) {
        return new UInt((int) value);
    }

    @GPUIntrinsic(code = "((ulong) ({0}))")
    public static ULong convert_ulong(byte value) {
        return new ULong(value);
    }

    @GPUIntrinsic(code = "((ulong) ({0}))")
    public static ULong convert_ulong(short value) {
        return new ULong(value);
    }

    @GPUIntrinsic(code = "((ulong) ({0}))")
    public static ULong convert_ulong(char value) {
        return new ULong(value);
    }

    @GPUIntrinsic(code = "((ulong) ({0}))")
    public static ULong convert_ulong(int value) {
        return new ULong(value);
    }

    @GPUIntrinsic(code = "((ulong) ({0}))")
    public static ULong convert_ulong(UByte value) {
        return new ULong(Byte.toUnsignedLong(value.value));
    }

    @GPUIntrinsic(code = "((ulong) ({0}))")
    public static ULong convert_ulong(UShort value) {
        return new ULong(Short.toUnsignedLong(value.value));
    }

    @GPUIntrinsic(code = "((ulong) ({0}))")
    public static ULong convert_ulong(UInt value) {
        return new ULong(Integer.toUnsignedLong(value.value));
    }

    @GPUIntrinsic(code = "((ulong) ({0}))")
    public static ULong convert_ulong(ULong value) {
        return new ULong(value.value);
    }

    @GPUIntrinsic(code = "((ulong) ({0}))")
    public static ULong convert_ulong(long value) {
        return new ULong(value);
    }

    @GPUIntrinsic(code = "((ulong) ({0}))")
    public static ULong convert_ulong(float value) {
        return new ULong((long) value);
    }

    @GPUIntrinsic(code = "((ulong) ({0}))")
    public static ULong convert_ulong(double value) {
        return new ULong((long) value);
    }

    @GPUIntrinsic(name = "as_int")
    public static int as_int(float value) {
        return Float.floatToRawIntBits(value);
    }

    @GPUIntrinsic(code = "as_uint({0})")
    public static UInt as_uint(float value) {
        return new UInt(Float.floatToRawIntBits(value));
    }

    @GPUIntrinsic(name = "as_float")
    public static float as_float(int value) {
        return Float.intBitsToFloat(value);
    }

    @GPUIntrinsic(code = "as_float({0}.value)")
    public static float as_float(UInt value) {
        return Float.intBitsToFloat(value.value);
    }

    @GPUIntrinsic(name = "as_long")
    public static long as_long(double value) {
        return Double.doubleToRawLongBits(value);
    }

    @GPUIntrinsic(code = "as_ulong({0})")
    public static ULong as_ulong(double value) {
        return new ULong(Double.doubleToRawLongBits(value));
    }

    @GPUIntrinsic(name = "as_double")
    public static double as_double(long value) {
        return Double.longBitsToDouble(value);
    }

    @GPUIntrinsic(code = "as_double({0}.value)")
    public static double as_double(ULong value) {
        return Double.longBitsToDouble(value.value);
    }

    @GPUIntrinsic(name = "clz")
    public static int clz(int value) {
        return Integer.numberOfLeadingZeros(value);
    }

    @GPUIntrinsic(name = "clz")
    public static int clz(long value) {
        return Long.numberOfLeadingZeros(value);
    }

    @GPUIntrinsic(code = "clz({0})")
    public static int clz(UInt value) {
        return Integer.numberOfLeadingZeros(value.value);
    }

    @GPUIntrinsic(code = "clz({0})")
    public static int clz(ULong value) {
        return Long.numberOfLeadingZeros(value.value);
    }

    @GPUIntrinsic(code = "clz({0})")
    public static Int2 clz(UInt2 value) {
        return new Int2(clz(new UInt(value.x)), clz(new UInt(value.y)));
    }

    @GPUIntrinsic(code = "clz({0})")
    public static Int3 clz(UInt3 value) {
        return new Int3(clz(new UInt(value.x)), clz(new UInt(value.y)), clz(new UInt(value.z)));
    }

    @GPUIntrinsic(code = "clz({0})")
    public static Int4 clz(UInt4 value) {
        return new Int4(clz(new UInt(value.x)), clz(new UInt(value.y)), clz(new UInt(value.z)), clz(new UInt(value.w)));
    }

    @GPUIntrinsic(code = "clz({0})")
    public static Int2 clz(ULong2 value) {
        return new Int2(clz(new ULong(value.x)), clz(new ULong(value.y)));
    }

    @GPUIntrinsic(code = "clz({0})")
    public static Int3 clz(ULong3 value) {
        return new Int3(clz(new ULong(value.x)), clz(new ULong(value.y)), clz(new ULong(value.z)));
    }

    @GPUIntrinsic(code = "clz({0})")
    public static Int4 clz(ULong4 value) {
        return new Int4(clz(new ULong(value.x)), clz(new ULong(value.y)), clz(new ULong(value.z)), clz(new ULong(value.w)));
    }

    @GPUIntrinsic(code = "clz({0})")
    public static Int8 clz(UInt8 value) {
        return new Int8(clz(new UInt(value.s0)), clz(new UInt(value.s1)), clz(new UInt(value.s2)), clz(new UInt(value.s3)),
                clz(new UInt(value.s4)), clz(new UInt(value.s5)), clz(new UInt(value.s6)), clz(new UInt(value.s7)));
    }

    @GPUIntrinsic(code = "clz({0})")
    public static Int16 clz(UInt16 value) {
        return new Int16(clz(new UInt(value.s0)), clz(new UInt(value.s1)), clz(new UInt(value.s2)), clz(new UInt(value.s3)),
                clz(new UInt(value.s4)), clz(new UInt(value.s5)), clz(new UInt(value.s6)), clz(new UInt(value.s7)),
                clz(new UInt(value.s8)), clz(new UInt(value.s9)), clz(new UInt(value.sa)), clz(new UInt(value.sb)),
                clz(new UInt(value.sc)), clz(new UInt(value.sd)), clz(new UInt(value.se)), clz(new UInt(value.sf)));
    }

    @GPUIntrinsic(code = "clz({0})")
    public static Int8 clz(ULong8 value) {
        return new Int8(clz(new ULong(value.s0)), clz(new ULong(value.s1)), clz(new ULong(value.s2)), clz(new ULong(value.s3)),
                clz(new ULong(value.s4)), clz(new ULong(value.s5)), clz(new ULong(value.s6)), clz(new ULong(value.s7)));
    }

    @GPUIntrinsic(code = "clz({0})")
    public static Int16 clz(ULong16 value) {
        return new Int16(clz(new ULong(value.s0)), clz(new ULong(value.s1)), clz(new ULong(value.s2)), clz(new ULong(value.s3)),
                clz(new ULong(value.s4)), clz(new ULong(value.s5)), clz(new ULong(value.s6)), clz(new ULong(value.s7)),
                clz(new ULong(value.s8)), clz(new ULong(value.s9)), clz(new ULong(value.sa)), clz(new ULong(value.sb)),
                clz(new ULong(value.sc)), clz(new ULong(value.sd)), clz(new ULong(value.se)), clz(new ULong(value.sf)));
    }

    @GPUIntrinsic(name = "popcount")
    public static int popcount(int value) {
        return Integer.bitCount(value);
    }

    @GPUIntrinsic(name = "popcount")
    public static int popcount(long value) {
        return Long.bitCount(value);
    }

    @GPUIntrinsic(code = "popcount({0})")
    public static int popcount(UInt value) {
        return Integer.bitCount(value.value);
    }

    @GPUIntrinsic(code = "popcount({0})")
    public static int popcount(ULong value) {
        return Long.bitCount(value.value);
    }

    @GPUIntrinsic(code = "popcount({0})")
    public static Int2 popcount(UInt2 value) {
        return new Int2(popcount(new UInt(value.x)), popcount(new UInt(value.y)));
    }

    @GPUIntrinsic(code = "popcount({0})")
    public static Int3 popcount(UInt3 value) {
        return new Int3(popcount(new UInt(value.x)), popcount(new UInt(value.y)), popcount(new UInt(value.z)));
    }

    @GPUIntrinsic(code = "popcount({0})")
    public static Int4 popcount(UInt4 value) {
        return new Int4(popcount(new UInt(value.x)), popcount(new UInt(value.y)), popcount(new UInt(value.z)), popcount(new UInt(value.w)));
    }

    @GPUIntrinsic(code = "popcount({0})")
    public static Int2 popcount(ULong2 value) {
        return new Int2(popcount(new ULong(value.x)), popcount(new ULong(value.y)));
    }

    @GPUIntrinsic(code = "popcount({0})")
    public static Int3 popcount(ULong3 value) {
        return new Int3(popcount(new ULong(value.x)), popcount(new ULong(value.y)), popcount(new ULong(value.z)));
    }

    @GPUIntrinsic(code = "popcount({0})")
    public static Int4 popcount(ULong4 value) {
        return new Int4(popcount(new ULong(value.x)), popcount(new ULong(value.y)), popcount(new ULong(value.z)), popcount(new ULong(value.w)));
    }

    @GPUIntrinsic(code = "popcount({0})")
    public static Int8 popcount(UInt8 value) {
        return new Int8(popcount(new UInt(value.s0)), popcount(new UInt(value.s1)), popcount(new UInt(value.s2)), popcount(new UInt(value.s3)),
                popcount(new UInt(value.s4)), popcount(new UInt(value.s5)), popcount(new UInt(value.s6)), popcount(new UInt(value.s7)));
    }

    @GPUIntrinsic(code = "popcount({0})")
    public static Int16 popcount(UInt16 value) {
        return new Int16(popcount(new UInt(value.s0)), popcount(new UInt(value.s1)), popcount(new UInt(value.s2)), popcount(new UInt(value.s3)),
                popcount(new UInt(value.s4)), popcount(new UInt(value.s5)), popcount(new UInt(value.s6)), popcount(new UInt(value.s7)),
                popcount(new UInt(value.s8)), popcount(new UInt(value.s9)), popcount(new UInt(value.sa)), popcount(new UInt(value.sb)),
                popcount(new UInt(value.sc)), popcount(new UInt(value.sd)), popcount(new UInt(value.se)), popcount(new UInt(value.sf)));
    }

    @GPUIntrinsic(code = "popcount({0})")
    public static Int8 popcount(ULong8 value) {
        return new Int8(popcount(new ULong(value.s0)), popcount(new ULong(value.s1)), popcount(new ULong(value.s2)), popcount(new ULong(value.s3)),
                popcount(new ULong(value.s4)), popcount(new ULong(value.s5)), popcount(new ULong(value.s6)), popcount(new ULong(value.s7)));
    }

    @GPUIntrinsic(code = "popcount({0})")
    public static Int16 popcount(ULong16 value) {
        return new Int16(popcount(new ULong(value.s0)), popcount(new ULong(value.s1)), popcount(new ULong(value.s2)), popcount(new ULong(value.s3)),
                popcount(new ULong(value.s4)), popcount(new ULong(value.s5)), popcount(new ULong(value.s6)), popcount(new ULong(value.s7)),
                popcount(new ULong(value.s8)), popcount(new ULong(value.s9)), popcount(new ULong(value.sa)), popcount(new ULong(value.sb)),
                popcount(new ULong(value.sc)), popcount(new ULong(value.sd)), popcount(new ULong(value.se)), popcount(new ULong(value.sf)));
    }

    @GPUIntrinsic(name = "rotate")
    public static int rotate(int value, int amount) {
        return Integer.rotateLeft(value, amount);
    }

    @GPUIntrinsic(name = "rotate")
    public static long rotate(long value, long amount) {
        return Long.rotateLeft(value, (int) amount);
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static UInt rotate(UInt value, UInt amount) {
        return new UInt(Integer.rotateLeft(value.value, amount.value));
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static ULong rotate(ULong value, ULong amount) {
        return new ULong(Long.rotateLeft(value.value, (int) amount.value));
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static UInt2 rotate(UInt2 value, UInt2 amount) {
        return new UInt2(rotate(new UInt(value.x), new UInt(amount.x)).value,
                rotate(new UInt(value.y), new UInt(amount.y)).value);
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static UInt3 rotate(UInt3 value, UInt3 amount) {
        return new UInt3(rotate(new UInt(value.x), new UInt(amount.x)).value,
                rotate(new UInt(value.y), new UInt(amount.y)).value,
                rotate(new UInt(value.z), new UInt(amount.z)).value);
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static UInt4 rotate(UInt4 value, UInt4 amount) {
        return new UInt4(rotate(new UInt(value.x), new UInt(amount.x)).value,
                rotate(new UInt(value.y), new UInt(amount.y)).value,
                rotate(new UInt(value.z), new UInt(amount.z)).value,
                rotate(new UInt(value.w), new UInt(amount.w)).value);
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static ULong2 rotate(ULong2 value, ULong2 amount) {
        return new ULong2(rotate(new ULong(value.x), new ULong(amount.x)).value,
                rotate(new ULong(value.y), new ULong(amount.y)).value);
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static ULong3 rotate(ULong3 value, ULong3 amount) {
        return new ULong3(rotate(new ULong(value.x), new ULong(amount.x)).value,
                rotate(new ULong(value.y), new ULong(amount.y)).value,
                rotate(new ULong(value.z), new ULong(amount.z)).value);
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static ULong4 rotate(ULong4 value, ULong4 amount) {
        return new ULong4(rotate(new ULong(value.x), new ULong(amount.x)).value,
                rotate(new ULong(value.y), new ULong(amount.y)).value,
                rotate(new ULong(value.z), new ULong(amount.z)).value,
                rotate(new ULong(value.w), new ULong(amount.w)).value);
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static UByte2 rotate(UByte2 value, UByte2 amount) {
        return new UByte2((byte) rotate(Byte.toUnsignedInt(value.x), Byte.toUnsignedInt(amount.x)),
                (byte) rotate(Byte.toUnsignedInt(value.y), Byte.toUnsignedInt(amount.y)));
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static UByte3 rotate(UByte3 value, UByte3 amount) {
        return new UByte3((byte) rotate(Byte.toUnsignedInt(value.x), Byte.toUnsignedInt(amount.x)),
                (byte) rotate(Byte.toUnsignedInt(value.y), Byte.toUnsignedInt(amount.y)),
                (byte) rotate(Byte.toUnsignedInt(value.z), Byte.toUnsignedInt(amount.z)));
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static UByte4 rotate(UByte4 value, UByte4 amount) {
        return new UByte4((byte) rotate(Byte.toUnsignedInt(value.x), Byte.toUnsignedInt(amount.x)),
                (byte) rotate(Byte.toUnsignedInt(value.y), Byte.toUnsignedInt(amount.y)),
                (byte) rotate(Byte.toUnsignedInt(value.z), Byte.toUnsignedInt(amount.z)),
                (byte) rotate(Byte.toUnsignedInt(value.w), Byte.toUnsignedInt(amount.w)));
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static UShort2 rotate(UShort2 value, UShort2 amount) {
        return new UShort2((short) rotate(Short.toUnsignedInt(value.x), Short.toUnsignedInt(amount.x)),
                (short) rotate(Short.toUnsignedInt(value.y), Short.toUnsignedInt(amount.y)));
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static UShort3 rotate(UShort3 value, UShort3 amount) {
        return new UShort3((short) rotate(Short.toUnsignedInt(value.x), Short.toUnsignedInt(amount.x)),
                (short) rotate(Short.toUnsignedInt(value.y), Short.toUnsignedInt(amount.y)),
                (short) rotate(Short.toUnsignedInt(value.z), Short.toUnsignedInt(amount.z)));
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static UShort4 rotate(UShort4 value, UShort4 amount) {
        return new UShort4((short) rotate(Short.toUnsignedInt(value.x), Short.toUnsignedInt(amount.x)),
                (short) rotate(Short.toUnsignedInt(value.y), Short.toUnsignedInt(amount.y)),
                (short) rotate(Short.toUnsignedInt(value.z), Short.toUnsignedInt(amount.z)),
                (short) rotate(Short.toUnsignedInt(value.w), Short.toUnsignedInt(amount.w)));
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static UInt8 rotate(UInt8 value, UInt8 amount) {
        return new UInt8(
                rotate(new UInt(value.s0), new UInt(amount.s0)).value,
                rotate(new UInt(value.s1), new UInt(amount.s1)).value,
                rotate(new UInt(value.s2), new UInt(amount.s2)).value,
                rotate(new UInt(value.s3), new UInt(amount.s3)).value,
                rotate(new UInt(value.s4), new UInt(amount.s4)).value,
                rotate(new UInt(value.s5), new UInt(amount.s5)).value,
                rotate(new UInt(value.s6), new UInt(amount.s6)).value,
                rotate(new UInt(value.s7), new UInt(amount.s7)).value
        );
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static UInt16 rotate(UInt16 value, UInt16 amount) {
        return new UInt16(
                rotate(new UInt(value.s0), new UInt(amount.s0)).value,
                rotate(new UInt(value.s1), new UInt(amount.s1)).value,
                rotate(new UInt(value.s2), new UInt(amount.s2)).value,
                rotate(new UInt(value.s3), new UInt(amount.s3)).value,
                rotate(new UInt(value.s4), new UInt(amount.s4)).value,
                rotate(new UInt(value.s5), new UInt(amount.s5)).value,
                rotate(new UInt(value.s6), new UInt(amount.s6)).value,
                rotate(new UInt(value.s7), new UInt(amount.s7)).value,
                rotate(new UInt(value.s8), new UInt(amount.s8)).value,
                rotate(new UInt(value.s9), new UInt(amount.s9)).value,
                rotate(new UInt(value.sa), new UInt(amount.sa)).value,
                rotate(new UInt(value.sb), new UInt(amount.sb)).value,
                rotate(new UInt(value.sc), new UInt(amount.sc)).value,
                rotate(new UInt(value.sd), new UInt(amount.sd)).value,
                rotate(new UInt(value.se), new UInt(amount.se)).value,
                rotate(new UInt(value.sf), new UInt(amount.sf)).value
        );
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static ULong8 rotate(ULong8 value, ULong8 amount) {
        return new ULong8(
                rotate(new ULong(value.s0), new ULong(amount.s0)).value,
                rotate(new ULong(value.s1), new ULong(amount.s1)).value,
                rotate(new ULong(value.s2), new ULong(amount.s2)).value,
                rotate(new ULong(value.s3), new ULong(amount.s3)).value,
                rotate(new ULong(value.s4), new ULong(amount.s4)).value,
                rotate(new ULong(value.s5), new ULong(amount.s5)).value,
                rotate(new ULong(value.s6), new ULong(amount.s6)).value,
                rotate(new ULong(value.s7), new ULong(amount.s7)).value
        );
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static ULong16 rotate(ULong16 value, ULong16 amount) {
        return new ULong16(
                rotate(new ULong(value.s0), new ULong(amount.s0)).value,
                rotate(new ULong(value.s1), new ULong(amount.s1)).value,
                rotate(new ULong(value.s2), new ULong(amount.s2)).value,
                rotate(new ULong(value.s3), new ULong(amount.s3)).value,
                rotate(new ULong(value.s4), new ULong(amount.s4)).value,
                rotate(new ULong(value.s5), new ULong(amount.s5)).value,
                rotate(new ULong(value.s6), new ULong(amount.s6)).value,
                rotate(new ULong(value.s7), new ULong(amount.s7)).value,
                rotate(new ULong(value.s8), new ULong(amount.s8)).value,
                rotate(new ULong(value.s9), new ULong(amount.s9)).value,
                rotate(new ULong(value.sa), new ULong(amount.sa)).value,
                rotate(new ULong(value.sb), new ULong(amount.sb)).value,
                rotate(new ULong(value.sc), new ULong(amount.sc)).value,
                rotate(new ULong(value.sd), new ULong(amount.sd)).value,
                rotate(new ULong(value.se), new ULong(amount.se)).value,
                rotate(new ULong(value.sf), new ULong(amount.sf)).value
        );
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static UByte8 rotate(UByte8 value, UByte8 amount) {
        return new UByte8(
                (byte) rotate(Byte.toUnsignedInt(value.s0), Byte.toUnsignedInt(amount.s0)),
                (byte) rotate(Byte.toUnsignedInt(value.s1), Byte.toUnsignedInt(amount.s1)),
                (byte) rotate(Byte.toUnsignedInt(value.s2), Byte.toUnsignedInt(amount.s2)),
                (byte) rotate(Byte.toUnsignedInt(value.s3), Byte.toUnsignedInt(amount.s3)),
                (byte) rotate(Byte.toUnsignedInt(value.s4), Byte.toUnsignedInt(amount.s4)),
                (byte) rotate(Byte.toUnsignedInt(value.s5), Byte.toUnsignedInt(amount.s5)),
                (byte) rotate(Byte.toUnsignedInt(value.s6), Byte.toUnsignedInt(amount.s6)),
                (byte) rotate(Byte.toUnsignedInt(value.s7), Byte.toUnsignedInt(amount.s7))
        );
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static UByte16 rotate(UByte16 value, UByte16 amount) {
        return new UByte16(
                (byte) rotate(Byte.toUnsignedInt(value.s0), Byte.toUnsignedInt(amount.s0)),
                (byte) rotate(Byte.toUnsignedInt(value.s1), Byte.toUnsignedInt(amount.s1)),
                (byte) rotate(Byte.toUnsignedInt(value.s2), Byte.toUnsignedInt(amount.s2)),
                (byte) rotate(Byte.toUnsignedInt(value.s3), Byte.toUnsignedInt(amount.s3)),
                (byte) rotate(Byte.toUnsignedInt(value.s4), Byte.toUnsignedInt(amount.s4)),
                (byte) rotate(Byte.toUnsignedInt(value.s5), Byte.toUnsignedInt(amount.s5)),
                (byte) rotate(Byte.toUnsignedInt(value.s6), Byte.toUnsignedInt(amount.s6)),
                (byte) rotate(Byte.toUnsignedInt(value.s7), Byte.toUnsignedInt(amount.s7)),
                (byte) rotate(Byte.toUnsignedInt(value.s8), Byte.toUnsignedInt(amount.s8)),
                (byte) rotate(Byte.toUnsignedInt(value.s9), Byte.toUnsignedInt(amount.s9)),
                (byte) rotate(Byte.toUnsignedInt(value.sa), Byte.toUnsignedInt(amount.sa)),
                (byte) rotate(Byte.toUnsignedInt(value.sb), Byte.toUnsignedInt(amount.sb)),
                (byte) rotate(Byte.toUnsignedInt(value.sc), Byte.toUnsignedInt(amount.sc)),
                (byte) rotate(Byte.toUnsignedInt(value.sd), Byte.toUnsignedInt(amount.sd)),
                (byte) rotate(Byte.toUnsignedInt(value.se), Byte.toUnsignedInt(amount.se)),
                (byte) rotate(Byte.toUnsignedInt(value.sf), Byte.toUnsignedInt(amount.sf))
        );
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static UShort8 rotate(UShort8 value, UShort8 amount) {
        return new UShort8(
                (short) rotate(Short.toUnsignedInt(value.s0), Short.toUnsignedInt(amount.s0)),
                (short) rotate(Short.toUnsignedInt(value.s1), Short.toUnsignedInt(amount.s1)),
                (short) rotate(Short.toUnsignedInt(value.s2), Short.toUnsignedInt(amount.s2)),
                (short) rotate(Short.toUnsignedInt(value.s3), Short.toUnsignedInt(amount.s3)),
                (short) rotate(Short.toUnsignedInt(value.s4), Short.toUnsignedInt(amount.s4)),
                (short) rotate(Short.toUnsignedInt(value.s5), Short.toUnsignedInt(amount.s5)),
                (short) rotate(Short.toUnsignedInt(value.s6), Short.toUnsignedInt(amount.s6)),
                (short) rotate(Short.toUnsignedInt(value.s7), Short.toUnsignedInt(amount.s7))
        );
    }

    @GPUIntrinsic(code = "rotate({0}, {1})")
    public static UShort16 rotate(UShort16 value, UShort16 amount) {
        return new UShort16(
                (short) rotate(Short.toUnsignedInt(value.s0), Short.toUnsignedInt(amount.s0)),
                (short) rotate(Short.toUnsignedInt(value.s1), Short.toUnsignedInt(amount.s1)),
                (short) rotate(Short.toUnsignedInt(value.s2), Short.toUnsignedInt(amount.s2)),
                (short) rotate(Short.toUnsignedInt(value.s3), Short.toUnsignedInt(amount.s3)),
                (short) rotate(Short.toUnsignedInt(value.s4), Short.toUnsignedInt(amount.s4)),
                (short) rotate(Short.toUnsignedInt(value.s5), Short.toUnsignedInt(amount.s5)),
                (short) rotate(Short.toUnsignedInt(value.s6), Short.toUnsignedInt(amount.s6)),
                (short) rotate(Short.toUnsignedInt(value.s7), Short.toUnsignedInt(amount.s7)),
                (short) rotate(Short.toUnsignedInt(value.s8), Short.toUnsignedInt(amount.s8)),
                (short) rotate(Short.toUnsignedInt(value.s9), Short.toUnsignedInt(amount.s9)),
                (short) rotate(Short.toUnsignedInt(value.sa), Short.toUnsignedInt(amount.sa)),
                (short) rotate(Short.toUnsignedInt(value.sb), Short.toUnsignedInt(amount.sb)),
                (short) rotate(Short.toUnsignedInt(value.sc), Short.toUnsignedInt(amount.sc)),
                (short) rotate(Short.toUnsignedInt(value.sd), Short.toUnsignedInt(amount.sd)),
                (short) rotate(Short.toUnsignedInt(value.se), Short.toUnsignedInt(amount.se)),
                (short) rotate(Short.toUnsignedInt(value.sf), Short.toUnsignedInt(amount.sf))
        );
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

    /**
     * Emits an OpenCL/C trap for source-level fail-fast paths.
     *
     * <p>This is the supported Java-side replacement for low-level kernels that use {@code __builtin_trap()} behind
     * debug or configuration guards. Preprocessor feature gates remain a build/configuration concern rather than a Java
     * frontend construct.
     */
    @GPUIntrinsic(code = "__builtin_trap()")
    public static void trap() {
    }

    /**
     * Emits an OpenCL/C unreachable marker after a trap or equivalent terminating path.
     */
    @GPUIntrinsic(code = "__builtin_unreachable()")
    public static void unreachable() {
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

    private static byte saturateToSignedByte(float value) {
        return saturateToSignedByte((double) truncFloat(value));
    }

    private static int saturateToSignedInt(float value) {
        return saturateToSignedInt((double) truncFloat(value));
    }

    private static int saturateToSignedInt(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private static int saturateToSignedInt(long value) {
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private static long saturateToSignedLong(float value) {
        return saturateToSignedLong((double) truncFloat(value));
    }

    private static long saturateToSignedLong(double value) {
        if (Double.isNaN(value)) {
            return 0L;
        }
        if (value < Long.MIN_VALUE) {
            return Long.MIN_VALUE;
        }
        if (value > Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) value;
    }

    private static byte saturateToSignedByte(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        if (value < Byte.MIN_VALUE) {
            return Byte.MIN_VALUE;
        }
        if (value > Byte.MAX_VALUE) {
            return Byte.MAX_VALUE;
        }
        return (byte) value;
    }

    private static byte saturateToSignedByte(int value) {
        if (value < Byte.MIN_VALUE) {
            return Byte.MIN_VALUE;
        }
        if (value > Byte.MAX_VALUE) {
            return Byte.MAX_VALUE;
        }
        return (byte) value;
    }

    private static byte saturateToSignedByte(long value) {
        if (value < Byte.MIN_VALUE) {
            return Byte.MIN_VALUE;
        }
        if (value > Byte.MAX_VALUE) {
            return Byte.MAX_VALUE;
        }
        return (byte) value;
    }

    private static short saturateToSignedShort(float value) {
        return saturateToSignedShort((double) truncFloat(value));
    }

    private static short saturateToSignedShort(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        if (value < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        if (value > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        return (short) value;
    }

    private static short saturateToSignedShort(int value) {
        if (value < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        if (value > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        return (short) value;
    }

    private static short saturateToSignedShort(long value) {
        if (value < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        if (value > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        return (short) value;
    }

    private static byte saturateToUnsignedByte(float value) {
        return saturateToUnsignedByte((double) truncFloat(value));
    }

    private static byte saturateToUnsignedByte(double value) {
        if (Double.isNaN(value) || value <= 0.0) {
            return 0;
        }
        if (value >= 255.0) {
            return (byte) 0xFF;
        }
        return (byte) (((int) value) & 0xFF);
    }

    private static byte saturateToUnsignedByte(int value) {
        if (value <= 0) {
            return 0;
        }
        if (value >= 255) {
            return (byte) 0xFF;
        }
        return (byte) (value & 0xFF);
    }

    private static byte saturateToUnsignedByte(long value) {
        if (value <= 0L) {
            return 0;
        }
        if (value >= 255L) {
            return (byte) 0xFF;
        }
        return (byte) (((int) value) & 0xFF);
    }

    private static short saturateToUnsignedShort(float value) {
        return saturateToUnsignedShort((double) truncFloat(value));
    }

    private static int saturateToUnsignedInt(float value) {
        return saturateToUnsignedInt((double) truncFloat(value));
    }

    private static int saturateToUnsignedInt(double value) {
        if (Double.isNaN(value) || value <= 0.0) {
            return 0;
        }
        if (value >= UINT_MAX_AS_DOUBLE) {
            return -1;
        }
        if (value < 2147483648.0) {
            return (int) value;
        }
        return (int) (value - 4294967296.0);
    }

    private static int saturateToUnsignedInt(int value) {
        return value <= 0 ? 0 : value;
    }

    private static int saturateToUnsignedInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        if (value >= UINT_MAX_AS_DOUBLE) {
            return -1;
        }
        return (int) value;
    }

    private static long saturateToUnsignedLong(float value) {
        return saturateToUnsignedLong((double) truncFloat(value));
    }

    private static long saturateToUnsignedLong(double value) {
        if (Double.isNaN(value) || value <= 0.0) {
            return 0L;
        }
        if (value >= ULONG_MAX_AS_DOUBLE) {
            return -1L;
        }
        if (value < ULONG_SIGN_THRESHOLD_AS_DOUBLE) {
            return (long) value;
        }
        return (long) (value - ULONG_WRAP_AS_DOUBLE);
    }

    private static long saturateToUnsignedLong(int value) {
        return value <= 0 ? 0L : value;
    }

    private static long saturateToUnsignedLong(long value) {
        return value <= 0L ? 0L : value;
    }

    private static short saturateToUnsignedShort(double value) {
        if (Double.isNaN(value) || value <= 0.0) {
            return 0;
        }
        if (value >= 65535.0) {
            return (short) 0xFFFF;
        }
        return (short) (((int) value) & 0xFFFF);
    }

    private static short saturateToUnsignedShort(int value) {
        if (value <= 0) {
            return 0;
        }
        if (value >= 65535) {
            return (short) 0xFFFF;
        }
        return (short) (value & 0xFFFF);
    }

    private static short saturateToUnsignedShort(long value) {
        if (value <= 0L) {
            return 0;
        }
        if (value >= 65535L) {
            return (short) 0xFFFF;
        }
        return (short) (((int) value) & 0xFFFF);
    }

    private static byte minUnsignedByte(byte left, byte right) {
        return Integer.compare(Byte.toUnsignedInt(left), Byte.toUnsignedInt(right)) <= 0 ? left : right;
    }

    private static byte absDiffUnsignedByte(byte left, byte right) {
        int leftValue = Byte.toUnsignedInt(left);
        int rightValue = Byte.toUnsignedInt(right);
        return (byte) Math.abs(leftValue - rightValue);
    }

    private static short absDiffUnsignedShort(short left, short right) {
        int leftValue = Short.toUnsignedInt(left);
        int rightValue = Short.toUnsignedInt(right);
        return (short) Math.abs(leftValue - rightValue);
    }

    private static int absDiffUnsignedInt(int left, int right) {
        long leftValue = Integer.toUnsignedLong(left);
        long rightValue = Integer.toUnsignedLong(right);
        return (int) Math.abs(leftValue - rightValue);
    }

    private static long absDiffUnsignedLong(long left, long right) {
        java.math.BigInteger leftValue = unsignedBigInteger(left);
        java.math.BigInteger rightValue = unsignedBigInteger(right);
        return leftValue.subtract(rightValue).abs().longValue();
    }

    private static long signedMulHiLong(long left, long right) {
        java.math.BigInteger product = java.math.BigInteger.valueOf(left).multiply(java.math.BigInteger.valueOf(right));
        return product.shiftRight(64).longValue();
    }

    private static byte saturatingAddUnsignedByte(byte left, byte right) {
        int result = Byte.toUnsignedInt(left) + Byte.toUnsignedInt(right);
        return (byte) Math.min(0xFF, result);
    }

    private static short saturatingAddUnsignedShort(short left, short right) {
        int result = Short.toUnsignedInt(left) + Short.toUnsignedInt(right);
        return (short) Math.min(0xFFFF, result);
    }

    private static int saturatingAddUnsignedInt(int left, int right) {
        long result = Integer.toUnsignedLong(left) + Integer.toUnsignedLong(right);
        return result >= 0xFFFF_FFFFL ? -1 : (int) result;
    }

    private static long saturatingAddUnsignedLong(long left, long right) {
        return Long.compareUnsigned(left, -1L - right) > 0 ? -1L : left + right;
    }

    private static byte saturatingSubUnsignedByte(byte left, byte right) {
        int result = Byte.toUnsignedInt(left) - Byte.toUnsignedInt(right);
        return (byte) Math.max(0, result);
    }

    private static short saturatingSubUnsignedShort(short left, short right) {
        int result = Short.toUnsignedInt(left) - Short.toUnsignedInt(right);
        return (short) Math.max(0, result);
    }

    private static int saturatingSubUnsignedInt(int left, int right) {
        return Integer.compareUnsigned(left, right) < 0 ? 0 : left - right;
    }

    private static long saturatingSubUnsignedLong(long left, long right) {
        return Long.compareUnsigned(left, right) < 0 ? 0L : left - right;
    }

    private static byte saturatingMadUnsignedByte(byte left, byte right, byte addend) {
        int result = (Byte.toUnsignedInt(left) * Byte.toUnsignedInt(right)) + Byte.toUnsignedInt(addend);
        return (byte) Math.min(0xFF, result);
    }

    private static byte saturatingMulUnsignedByte(byte left, byte right) {
        int result = Byte.toUnsignedInt(left) * Byte.toUnsignedInt(right);
        return (byte) Math.min(0xFF, result);
    }

    private static short saturatingMadUnsignedShort(short left, short right, short addend) {
        int result = (Short.toUnsignedInt(left) * Short.toUnsignedInt(right)) + Short.toUnsignedInt(addend);
        return (short) Math.min(0xFFFF, result);
    }

    private static short saturatingMulUnsignedShort(short left, short right) {
        int result = Short.toUnsignedInt(left) * Short.toUnsignedInt(right);
        return (short) Math.min(0xFFFF, result);
    }

    private static int saturatingMadUnsignedInt(int left, int right, int addend) {
        long result = (Integer.toUnsignedLong(left) * Integer.toUnsignedLong(right)) + Integer.toUnsignedLong(addend);
        return result >= 0xFFFF_FFFFL ? -1 : (int) result;
    }

    private static int saturatingMulUnsignedInt(int left, int right) {
        long result = Integer.toUnsignedLong(left) * Integer.toUnsignedLong(right);
        return result >= 0xFFFF_FFFFL ? -1 : (int) result;
    }

    private static long saturatingMadUnsignedLong(long left, long right, long addend) {
        java.math.BigInteger result = unsignedBigInteger(left)
                .multiply(unsignedBigInteger(right))
                .add(unsignedBigInteger(addend));
        java.math.BigInteger max = unsignedBigInteger(-1L);
        return result.compareTo(max) > 0 ? -1L : result.longValue();
    }

    private static long saturatingMulUnsignedLong(long left, long right) {
        java.math.BigInteger result = unsignedBigInteger(left).multiply(unsignedBigInteger(right));
        java.math.BigInteger max = unsignedBigInteger(-1L);
        return result.compareTo(max) > 0 ? -1L : result.longValue();
    }

    private static int saturatingAddInt(int left, int right) {
        long result = (long) left + right;
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (result < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) result;
    }

    private static long saturatingAddLong(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        if (right < 0 && left < Long.MIN_VALUE - right) {
            return Long.MIN_VALUE;
        }
        return left + right;
    }

    private static int saturatingSubInt(int left, int right) {
        long result = (long) left - right;
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (result < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) result;
    }

    private static long saturatingSubLong(long left, long right) {
        if (right < 0 && left > Long.MAX_VALUE + right) {
            return Long.MAX_VALUE;
        }
        if (right > 0 && left < Long.MIN_VALUE + right) {
            return Long.MIN_VALUE;
        }
        return left - right;
    }

    private static int saturatingMadInt(int left, int right, int addend) {
        long result = ((long) left * right) + addend;
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (result < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) result;
    }

    private static int saturatingMulInt(int left, int right) {
        long result = (long) left * right;
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (result < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) result;
    }

    private static long saturatingMadLong(long left, long right, long addend) {
        java.math.BigInteger result = java.math.BigInteger.valueOf(left)
                .multiply(java.math.BigInteger.valueOf(right))
                .add(java.math.BigInteger.valueOf(addend));
        if (result.compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            return Long.MAX_VALUE;
        }
        if (result.compareTo(java.math.BigInteger.valueOf(Long.MIN_VALUE)) < 0) {
            return Long.MIN_VALUE;
        }
        return result.longValue();
    }

    private static long saturatingMulLong(long left, long right) {
        java.math.BigInteger result = java.math.BigInteger.valueOf(left)
                .multiply(java.math.BigInteger.valueOf(right));
        if (result.compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            return Long.MAX_VALUE;
        }
        if (result.compareTo(java.math.BigInteger.valueOf(Long.MIN_VALUE)) < 0) {
            return Long.MIN_VALUE;
        }
        return result.longValue();
    }

    private static short minUnsignedShort(short left, short right) {
        return Integer.compare(Short.toUnsignedInt(left), Short.toUnsignedInt(right)) <= 0 ? left : right;
    }

    private static java.math.BigInteger unsignedBigInteger(long value) {
        java.math.BigInteger base = java.math.BigInteger.valueOf(value & Long.MAX_VALUE);
        if (value < 0) {
            base = base.setBit(63);
        }
        return base;
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
