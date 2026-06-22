package net.sixik.ga_utils.javatogpu.runtime.opencl;

import dev.denismasterherobrine.packager.opencl.core.OpenClBuffer;
import dev.denismasterherobrine.packager.opencl.core.OpenClCommandQueue;
import dev.denismasterherobrine.packager.opencl.core.OpenClEvents;
import dev.denismasterherobrine.packager.opencl.core.OpenClException;
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
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackend;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import org.lwjgl.opencl.CL10;

public class OpenClGpuRuntimeBackend implements GpuRuntimeBackend, AutoCloseable {

    private final Map<GpuKernelDescriptor, OpenClCompiledKernel> compiledKernels = new ConcurrentHashMap<>();
    private final OpenClDeviceBufferRegistry bufferRegistry = new OpenClDeviceBufferRegistry();
    private final OpenClExecutionPreparer executionPreparer = new OpenClExecutionPreparer(bufferRegistry);
    private final Map<String, Object> nativeBuffers = new ConcurrentHashMap<>();
    private volatile OpenClRuntimeSession session;

    @Override
    public final void invoke(GpuKernelInvocation invocation) {
        if (OpenClAbiDebug.enabled()) {
            System.err.println(OpenClAbiDebug.describeInvocation(invocation.descriptor(), invocation.arguments()));
        }
        OpenClCompiledKernel compiledKernel = compiledKernels.computeIfAbsent(
                invocation.descriptor(),
                this::compileKernel
        );
        OpenClKernelArguments arguments = OpenClArgumentMarshaller.marshall(invocation.descriptor(), invocation.arguments());
        OpenClExecutionPlan plan = OpenClExecutionPlanner.plan(arguments);
        executeKernel(executionPreparer.prepare(compiledKernel, plan));
    }

    public final Image2DReadOnly createReadOnlyRgbaFloatImage(int width, int height, float[] rgba) {
        return createReadOnlyRgbaFloatImageInternal(width, height, rgba);
    }

    public final Image2DWriteOnly createWriteOnlyRgbaFloatImage(int width, int height) {
        return createWriteOnlyRgbaFloatImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRFloatImage(int width, int height, float[] values) {
        return createReadOnlyRFloatImageInternal(width, height, values);
    }

    public final Image2DWriteOnly createWriteOnlyRFloatImage(int width, int height) {
        return createWriteOnlyRFloatImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRgFloatImage(int width, int height, float[] values) {
        return createReadOnlyRgFloatImageInternal(width, height, values);
    }

    public final Image2DWriteOnly createWriteOnlyRgFloatImage(int width, int height) {
        return createWriteOnlyRgFloatImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRIntImage(int width, int height, int[] values) {
        return createReadOnlyRIntImageInternal(width, height, values);
    }

    public final Image2DWriteOnly createWriteOnlyRIntImage(int width, int height) {
        return createWriteOnlyRIntImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRgIntImage(int width, int height, int[] values) {
        return createReadOnlyRgIntImageInternal(width, height, values);
    }

    public final Image2DWriteOnly createWriteOnlyRgIntImage(int width, int height) {
        return createWriteOnlyRgIntImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRgbaIntImage(int width, int height, int[] rgba) {
        return createReadOnlyRgbaIntImageInternal(width, height, rgba);
    }

    public final Image2DWriteOnly createWriteOnlyRgbaIntImage(int width, int height) {
        return createWriteOnlyRgbaIntImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRgbaUIntImage(int width, int height, int[] rgba) {
        return createReadOnlyRgbaUIntImageInternal(width, height, rgba);
    }

    public final Image2DWriteOnly createWriteOnlyRgbaUIntImage(int width, int height) {
        return createWriteOnlyRgbaUIntImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRUIntImage(int width, int height, int[] values) {
        return createReadOnlyRUIntImageInternal(width, height, values);
    }

    public final Image2DWriteOnly createWriteOnlyRUIntImage(int width, int height) {
        return createWriteOnlyRUIntImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRgUIntImage(int width, int height, int[] values) {
        return createReadOnlyRgUIntImageInternal(width, height, values);
    }

    public final Image2DWriteOnly createWriteOnlyRgUIntImage(int width, int height) {
        return createWriteOnlyRgUIntImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRgba8Image(int width, int height, byte[] rgba) {
        return createReadOnlyRgba8ImageInternal(width, height, rgba);
    }

    public final Image2DWriteOnly createWriteOnlyRgba8Image(int width, int height) {
        return createWriteOnlyRgba8ImageInternal(width, height);
    }

    public final Image1DReadOnly createReadOnlyRgbaFloatImage1D(int width, float[] rgba) {
        return createReadOnlyRgbaFloatImage1DInternal(width, rgba);
    }

    public final Image1DWriteOnly createWriteOnlyRgbaFloatImage1D(int width) {
        return createWriteOnlyRgbaFloatImage1DInternal(width);
    }

    public final Image1DReadOnly createReadOnlyRgbaUIntImage1D(int width, int[] rgba) {
        return createReadOnlyRgbaUIntImage1DInternal(width, rgba);
    }

    public final Image1DWriteOnly createWriteOnlyRgbaUIntImage1D(int width) {
        return createWriteOnlyRgbaUIntImage1DInternal(width);
    }

    public final Image1DReadOnly createReadOnlyRgbaIntImage1D(int width, int[] rgba) {
        return createReadOnlyRgbaIntImage1DInternal(width, rgba);
    }

    public final Image1DWriteOnly createWriteOnlyRgbaIntImage1D(int width) {
        return createWriteOnlyRgbaIntImage1DInternal(width);
    }

    public final Image1DArrayReadOnly createReadOnlyRgbaFloatImage1DArray(int width, int layers, float[] rgba) {
        return createReadOnlyRgbaFloatImage1DArrayInternal(width, layers, rgba);
    }

    public final Image1DArrayWriteOnly createWriteOnlyRgbaFloatImage1DArray(int width, int layers) {
        return createWriteOnlyRgbaFloatImage1DArrayInternal(width, layers);
    }

    public final Image1DArrayReadOnly createReadOnlyRgbaIntImage1DArray(int width, int layers, int[] rgba) {
        return createReadOnlyRgbaIntImage1DArrayInternal(width, layers, rgba);
    }

    public final Image1DArrayWriteOnly createWriteOnlyRgbaIntImage1DArray(int width, int layers) {
        return createWriteOnlyRgbaIntImage1DArrayInternal(width, layers);
    }

    public final Image1DArrayReadOnly createReadOnlyRgbaUIntImage1DArray(int width, int layers, int[] rgba) {
        return createReadOnlyRgbaUIntImage1DArrayInternal(width, layers, rgba);
    }

    public final Image1DArrayWriteOnly createWriteOnlyRgbaUIntImage1DArray(int width, int layers) {
        return createWriteOnlyRgbaUIntImage1DArrayInternal(width, layers);
    }

    public final Image1DBufferReadOnly createReadOnlyRgbaFloatImage1DBuffer(int width, float[] rgba) {
        return createReadOnlyRgbaFloatImage1DBufferInternal(width, rgba);
    }

    public final Image1DBufferWriteOnly createWriteOnlyRgbaFloatImage1DBuffer(int width) {
        return createWriteOnlyRgbaFloatImage1DBufferInternal(width);
    }

    public final Image1DBufferReadOnly createReadOnlyRgbaIntImage1DBuffer(int width, int[] rgba) {
        return createReadOnlyRgbaIntImage1DBufferInternal(width, rgba);
    }

    public final Image1DBufferWriteOnly createWriteOnlyRgbaIntImage1DBuffer(int width) {
        return createWriteOnlyRgbaIntImage1DBufferInternal(width);
    }

    public final Image1DBufferReadOnly createReadOnlyRgbaUIntImage1DBuffer(int width, int[] rgba) {
        return createReadOnlyRgbaUIntImage1DBufferInternal(width, rgba);
    }

    public final Image1DBufferWriteOnly createWriteOnlyRgbaUIntImage1DBuffer(int width) {
        return createWriteOnlyRgbaUIntImage1DBufferInternal(width);
    }

    public final Image2DArrayReadOnly createReadOnlyRgbaFloatImage2DArray(int width, int height, int layers, float[] rgba) {
        return createReadOnlyRgbaFloatImage2DArrayInternal(width, height, layers, rgba);
    }

    public final Image2DArrayWriteOnly createWriteOnlyRgbaFloatImage2DArray(int width, int height, int layers) {
        return createWriteOnlyRgbaFloatImage2DArrayInternal(width, height, layers);
    }

    public final Image2DArrayReadOnly createReadOnlyRgbaIntImage2DArray(int width, int height, int layers, int[] rgba) {
        return createReadOnlyRgbaIntImage2DArrayInternal(width, height, layers, rgba);
    }

    public final Image2DArrayWriteOnly createWriteOnlyRgbaIntImage2DArray(int width, int height, int layers) {
        return createWriteOnlyRgbaIntImage2DArrayInternal(width, height, layers);
    }

    public final Image2DArrayReadOnly createReadOnlyRgbaUIntImage2DArray(int width, int height, int layers, int[] rgba) {
        return createReadOnlyRgbaUIntImage2DArrayInternal(width, height, layers, rgba);
    }

    public final Image2DArrayWriteOnly createWriteOnlyRgbaUIntImage2DArray(int width, int height, int layers) {
        return createWriteOnlyRgbaUIntImage2DArrayInternal(width, height, layers);
    }

    public final Image3DReadOnly createReadOnlyRgbaFloatImage3D(int width, int height, int depth, float[] rgba) {
        return createReadOnlyRgbaFloatImage3DInternal(width, height, depth, rgba);
    }

    public final Image3DWriteOnly createWriteOnlyRgbaFloatImage3D(int width, int height, int depth) {
        return createWriteOnlyRgbaFloatImage3DInternal(width, height, depth);
    }

    public final Image3DReadOnly createReadOnlyRgbaIntImage3D(int width, int height, int depth, int[] rgba) {
        return createReadOnlyRgbaIntImage3DInternal(width, height, depth, rgba);
    }

    public final Image3DWriteOnly createWriteOnlyRgbaIntImage3D(int width, int height, int depth) {
        return createWriteOnlyRgbaIntImage3DInternal(width, height, depth);
    }

    public final Image3DReadOnly createReadOnlyRgbaUIntImage3D(int width, int height, int depth, int[] rgba) {
        return createReadOnlyRgbaUIntImage3DInternal(width, height, depth, rgba);
    }

    public final Image3DWriteOnly createWriteOnlyRgbaUIntImage3D(int width, int height, int depth) {
        return createWriteOnlyRgbaUIntImage3DInternal(width, height, depth);
    }

    public final float[] readRgbaFloatImage(Image2DReadOnly image) {
        return readRgbaFloatImageInternal(image);
    }

    public final float[] readRgbaFloatImage(Image2DWriteOnly image) {
        return readRgbaFloatImageInternal(image);
    }

    public final float[] readRFloatImage(Image2DReadOnly image) {
        return readRFloatImageInternal(image);
    }

    public final float[] readRFloatImage(Image2DWriteOnly image) {
        return readRFloatImageInternal(image);
    }

    public final float[] readRgFloatImage(Image2DReadOnly image) {
        return readRgFloatImageInternal(image);
    }

    public final float[] readRgFloatImage(Image2DWriteOnly image) {
        return readRgFloatImageInternal(image);
    }

    public final int[] readRIntImage(Image2DReadOnly image) {
        return readRIntImageInternal(image);
    }

    public final int[] readRIntImage(Image2DWriteOnly image) {
        return readRIntImageInternal(image);
    }

    public final int[] readRgIntImage(Image2DReadOnly image) {
        return readRgIntImageInternal(image);
    }

    public final int[] readRgIntImage(Image2DWriteOnly image) {
        return readRgIntImageInternal(image);
    }

    public final int[] readRgbaIntImage(Image2DReadOnly image) {
        return readRgbaIntImageInternal(image);
    }

    public final int[] readRgbaIntImage(Image2DWriteOnly image) {
        return readRgbaIntImageInternal(image);
    }

    public final int[] readRgbaUIntImage(Image2DReadOnly image) {
        return readRgbaUIntImageInternal(image);
    }

    public final int[] readRgbaUIntImage(Image2DWriteOnly image) {
        return readRgbaUIntImageInternal(image);
    }

    public final int[] readRUIntImage(Image2DReadOnly image) {
        return readRUIntImageInternal(image);
    }

    public final int[] readRUIntImage(Image2DWriteOnly image) {
        return readRUIntImageInternal(image);
    }

    public final int[] readRgUIntImage(Image2DReadOnly image) {
        return readRgUIntImageInternal(image);
    }

    public final int[] readRgUIntImage(Image2DWriteOnly image) {
        return readRgUIntImageInternal(image);
    }

    public final byte[] readRgba8Image(Image2DReadOnly image) {
        return readRgba8ImageInternal(image);
    }

    public final byte[] readRgba8Image(Image2DWriteOnly image) {
        return readRgba8ImageInternal(image);
    }

    public final float[] readRgbaFloatImage1D(Image1DReadOnly image) {
        return readRgbaFloatImage1DInternal(image);
    }

    public final float[] readRgbaFloatImage1D(Image1DWriteOnly image) {
        return readRgbaFloatImage1DInternal(image);
    }

    public final int[] readRgbaUIntImage1D(Image1DReadOnly image) {
        return readRgbaUIntImage1DInternal(image);
    }

    public final int[] readRgbaUIntImage1D(Image1DWriteOnly image) {
        return readRgbaUIntImage1DInternal(image);
    }

    public final int[] readRgbaIntImage1D(Image1DReadOnly image) {
        return readRgbaIntImage1DInternal(image);
    }

    public final int[] readRgbaIntImage1D(Image1DWriteOnly image) {
        return readRgbaIntImage1DInternal(image);
    }

    public final float[] readRgbaFloatImage1DArray(Image1DArrayReadOnly image) {
        return readRgbaFloatImage1DArrayInternal(image);
    }

    public final float[] readRgbaFloatImage1DArray(Image1DArrayWriteOnly image) {
        return readRgbaFloatImage1DArrayInternal(image);
    }

    public final int[] readRgbaIntImage1DArray(Image1DArrayReadOnly image) {
        return readRgbaIntImage1DArrayInternal(image);
    }

    public final int[] readRgbaIntImage1DArray(Image1DArrayWriteOnly image) {
        return readRgbaIntImage1DArrayInternal(image);
    }

    public final int[] readRgbaUIntImage1DArray(Image1DArrayReadOnly image) {
        return readRgbaUIntImage1DArrayInternal(image);
    }

    public final int[] readRgbaUIntImage1DArray(Image1DArrayWriteOnly image) {
        return readRgbaUIntImage1DArrayInternal(image);
    }

    public final float[] readRgbaFloatImage1DBuffer(Image1DBufferReadOnly image) {
        return readRgbaFloatImage1DBufferInternal(image);
    }

    public final float[] readRgbaFloatImage1DBuffer(Image1DBufferWriteOnly image) {
        return readRgbaFloatImage1DBufferInternal(image);
    }

    public final int[] readRgbaIntImage1DBuffer(Image1DBufferReadOnly image) {
        return readRgbaIntImage1DBufferInternal(image);
    }

    public final int[] readRgbaIntImage1DBuffer(Image1DBufferWriteOnly image) {
        return readRgbaIntImage1DBufferInternal(image);
    }

    public final int[] readRgbaUIntImage1DBuffer(Image1DBufferReadOnly image) {
        return readRgbaUIntImage1DBufferInternal(image);
    }

    public final int[] readRgbaUIntImage1DBuffer(Image1DBufferWriteOnly image) {
        return readRgbaUIntImage1DBufferInternal(image);
    }

    public final float[] readRgbaFloatImage2DArray(Image2DArrayReadOnly image) {
        return readRgbaFloatImage2DArrayInternal(image);
    }

    public final float[] readRgbaFloatImage2DArray(Image2DArrayWriteOnly image) {
        return readRgbaFloatImage2DArrayInternal(image);
    }

    public final int[] readRgbaIntImage2DArray(Image2DArrayReadOnly image) {
        return readRgbaIntImage2DArrayInternal(image);
    }

    public final int[] readRgbaIntImage2DArray(Image2DArrayWriteOnly image) {
        return readRgbaIntImage2DArrayInternal(image);
    }

    public final int[] readRgbaUIntImage2DArray(Image2DArrayReadOnly image) {
        return readRgbaUIntImage2DArrayInternal(image);
    }

    public final int[] readRgbaUIntImage2DArray(Image2DArrayWriteOnly image) {
        return readRgbaUIntImage2DArrayInternal(image);
    }

    public final float[] readRgbaFloatImage3D(Image3DReadOnly image) {
        return readRgbaFloatImage3DInternal(image);
    }

    public final float[] readRgbaFloatImage3D(Image3DWriteOnly image) {
        return readRgbaFloatImage3DInternal(image);
    }

    public final int[] readRgbaIntImage3D(Image3DReadOnly image) {
        return readRgbaIntImage3DInternal(image);
    }

    public final int[] readRgbaIntImage3D(Image3DWriteOnly image) {
        return readRgbaIntImage3DInternal(image);
    }

    public final int[] readRgbaUIntImage3D(Image3DReadOnly image) {
        return readRgbaUIntImage3DInternal(image);
    }

    public final int[] readRgbaUIntImage3D(Image3DWriteOnly image) {
        return readRgbaUIntImage3DInternal(image);
    }

    public final Sampler createSampler(boolean normalizedCoordinates, int addressingMode, int filterMode) {
        return createSamplerInternal(normalizedCoordinates, addressingMode, filterMode);
    }

    public final Sampler createNearestClampToEdgeSampler() {
        return createNearestClampToEdgeSampler(false);
    }

    public final Sampler createNearestClampToEdgeSampler(boolean normalizedCoordinates) {
        return createSampler(
                normalizedCoordinates,
                CL10.CL_ADDRESS_CLAMP_TO_EDGE,
                CL10.CL_FILTER_NEAREST
        );
    }

    protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
        return session().compileKernel(kernelDescriptor);
    }

    protected void executeKernel(OpenClPreparedExecution execution) {
        for (OpenClPreparedBufferBinding binding : execution.bufferBindings()) {
            Object nativeBuffer = resolveNativeBuffer(binding);
            if (binding.binding().uploadRequired()) {
                uploadToDeviceBuffer(nativeBuffer, binding.binding());
            }
        }

        for (OpenClPreparedArgumentBinding binding : execution.argumentBindings()) {
            if (binding.bufferBinding() != null) {
                bindBufferArgument(
                        execution.compiledKernel(),
                        binding.parameterIndex(),
                        resolveNativeBuffer(binding.bufferBinding())
                );
                continue;
            }

            if (binding.localBinding() != null) {
                bindLocalArgument(execution.compiledKernel(), binding.parameterIndex(), binding.localBinding());
                continue;
            }

            bindScalarArgument(execution.compiledKernel(), binding.parameterIndex(), binding.scalarBinding());
        }

        enqueueKernel(execution.compiledKernel(), resolveGlobalWorkSize(execution));

        for (OpenClPreparedBufferBinding binding : execution.bufferBindings()) {
            if (binding.binding().readbackRequired()) {
                readBackFromDeviceBuffer(resolveNativeBuffer(binding), binding.binding());
            }
        }
    }

    protected OpenClRuntimeSession createSession() {
        return OpenClRuntimeSession.createDefault();
    }

    protected Image2DReadOnly createReadOnlyRgbaFloatImageInternal(int width, int height, float[] rgba) {
        return session().createReadOnlyRgbaFloatImage(width, height, rgba);
    }

    protected Image2DWriteOnly createWriteOnlyRgbaFloatImageInternal(int width, int height) {
        return session().createWriteOnlyRgbaFloatImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRFloatImageInternal(int width, int height, float[] values) {
        return session().createReadOnlyRFloatImage(width, height, values);
    }

    protected Image2DWriteOnly createWriteOnlyRFloatImageInternal(int width, int height) {
        return session().createWriteOnlyRFloatImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRgFloatImageInternal(int width, int height, float[] values) {
        return session().createReadOnlyRgFloatImage(width, height, values);
    }

    protected Image2DWriteOnly createWriteOnlyRgFloatImageInternal(int width, int height) {
        return session().createWriteOnlyRgFloatImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRIntImageInternal(int width, int height, int[] values) {
        return session().createReadOnlyRIntImage(width, height, values);
    }

    protected Image2DWriteOnly createWriteOnlyRIntImageInternal(int width, int height) {
        return session().createWriteOnlyRIntImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRgIntImageInternal(int width, int height, int[] values) {
        return session().createReadOnlyRgIntImage(width, height, values);
    }

    protected Image2DWriteOnly createWriteOnlyRgIntImageInternal(int width, int height) {
        return session().createWriteOnlyRgIntImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRgbaIntImageInternal(int width, int height, int[] rgba) {
        return session().createReadOnlyRgbaIntImage(width, height, rgba);
    }

    protected Image2DWriteOnly createWriteOnlyRgbaIntImageInternal(int width, int height) {
        return session().createWriteOnlyRgbaIntImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRgbaUIntImageInternal(int width, int height, int[] rgba) {
        return session().createReadOnlyRgbaUIntImage(width, height, rgba);
    }

    protected Image2DWriteOnly createWriteOnlyRgbaUIntImageInternal(int width, int height) {
        return session().createWriteOnlyRgbaUIntImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRUIntImageInternal(int width, int height, int[] values) {
        return session().createReadOnlyRUIntImage(width, height, values);
    }

    protected Image2DWriteOnly createWriteOnlyRUIntImageInternal(int width, int height) {
        return session().createWriteOnlyRUIntImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRgUIntImageInternal(int width, int height, int[] values) {
        return session().createReadOnlyRgUIntImage(width, height, values);
    }

    protected Image2DWriteOnly createWriteOnlyRgUIntImageInternal(int width, int height) {
        return session().createWriteOnlyRgUIntImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRgba8ImageInternal(int width, int height, byte[] rgba) {
        return session().createReadOnlyRgba8Image(width, height, rgba);
    }

    protected Image2DWriteOnly createWriteOnlyRgba8ImageInternal(int width, int height) {
        return session().createWriteOnlyRgba8Image(width, height);
    }

    protected Image1DReadOnly createReadOnlyRgbaFloatImage1DInternal(int width, float[] rgba) {
        return session().createReadOnlyRgbaFloatImage1D(width, rgba);
    }

    protected Image1DWriteOnly createWriteOnlyRgbaFloatImage1DInternal(int width) {
        return session().createWriteOnlyRgbaFloatImage1D(width);
    }

    protected Image1DReadOnly createReadOnlyRgbaUIntImage1DInternal(int width, int[] rgba) {
        return session().createReadOnlyRgbaUIntImage1D(width, rgba);
    }

    protected Image1DWriteOnly createWriteOnlyRgbaUIntImage1DInternal(int width) {
        return session().createWriteOnlyRgbaUIntImage1D(width);
    }

    protected Image1DReadOnly createReadOnlyRgbaIntImage1DInternal(int width, int[] rgba) {
        return session().createReadOnlyRgbaIntImage1D(width, rgba);
    }

    protected Image1DWriteOnly createWriteOnlyRgbaIntImage1DInternal(int width) {
        return session().createWriteOnlyRgbaIntImage1D(width);
    }

    protected Image1DArrayReadOnly createReadOnlyRgbaFloatImage1DArrayInternal(int width, int layers, float[] rgba) {
        return session().createReadOnlyRgbaFloatImage1DArray(width, layers, rgba);
    }

    protected Image1DArrayWriteOnly createWriteOnlyRgbaFloatImage1DArrayInternal(int width, int layers) {
        return session().createWriteOnlyRgbaFloatImage1DArray(width, layers);
    }

    protected Image1DArrayReadOnly createReadOnlyRgbaIntImage1DArrayInternal(int width, int layers, int[] rgba) {
        return session().createReadOnlyRgbaIntImage1DArray(width, layers, rgba);
    }

    protected Image1DArrayWriteOnly createWriteOnlyRgbaIntImage1DArrayInternal(int width, int layers) {
        return session().createWriteOnlyRgbaIntImage1DArray(width, layers);
    }

    protected Image1DArrayReadOnly createReadOnlyRgbaUIntImage1DArrayInternal(int width, int layers, int[] rgba) {
        return session().createReadOnlyRgbaUIntImage1DArray(width, layers, rgba);
    }

    protected Image1DArrayWriteOnly createWriteOnlyRgbaUIntImage1DArrayInternal(int width, int layers) {
        return session().createWriteOnlyRgbaUIntImage1DArray(width, layers);
    }

    protected Image1DBufferReadOnly createReadOnlyRgbaFloatImage1DBufferInternal(int width, float[] rgba) {
        return session().createReadOnlyRgbaFloatImage1DBuffer(width, rgba);
    }

    protected Image1DBufferWriteOnly createWriteOnlyRgbaFloatImage1DBufferInternal(int width) {
        return session().createWriteOnlyRgbaFloatImage1DBuffer(width);
    }

    protected Image1DBufferReadOnly createReadOnlyRgbaIntImage1DBufferInternal(int width, int[] rgba) {
        return session().createReadOnlyRgbaIntImage1DBuffer(width, rgba);
    }

    protected Image1DBufferWriteOnly createWriteOnlyRgbaIntImage1DBufferInternal(int width) {
        return session().createWriteOnlyRgbaIntImage1DBuffer(width);
    }

    protected Image1DBufferReadOnly createReadOnlyRgbaUIntImage1DBufferInternal(int width, int[] rgba) {
        return session().createReadOnlyRgbaUIntImage1DBuffer(width, rgba);
    }

    protected Image1DBufferWriteOnly createWriteOnlyRgbaUIntImage1DBufferInternal(int width) {
        return session().createWriteOnlyRgbaUIntImage1DBuffer(width);
    }

    protected Image2DArrayReadOnly createReadOnlyRgbaFloatImage2DArrayInternal(int width, int height, int layers, float[] rgba) {
        return session().createReadOnlyRgbaFloatImage2DArray(width, height, layers, rgba);
    }

    protected Image2DArrayWriteOnly createWriteOnlyRgbaFloatImage2DArrayInternal(int width, int height, int layers) {
        return session().createWriteOnlyRgbaFloatImage2DArray(width, height, layers);
    }

    protected Image2DArrayReadOnly createReadOnlyRgbaIntImage2DArrayInternal(int width, int height, int layers, int[] rgba) {
        return session().createReadOnlyRgbaIntImage2DArray(width, height, layers, rgba);
    }

    protected Image2DArrayWriteOnly createWriteOnlyRgbaIntImage2DArrayInternal(int width, int height, int layers) {
        return session().createWriteOnlyRgbaIntImage2DArray(width, height, layers);
    }

    protected Image2DArrayReadOnly createReadOnlyRgbaUIntImage2DArrayInternal(int width, int height, int layers, int[] rgba) {
        return session().createReadOnlyRgbaUIntImage2DArray(width, height, layers, rgba);
    }

    protected Image2DArrayWriteOnly createWriteOnlyRgbaUIntImage2DArrayInternal(int width, int height, int layers) {
        return session().createWriteOnlyRgbaUIntImage2DArray(width, height, layers);
    }

    protected Image3DReadOnly createReadOnlyRgbaFloatImage3DInternal(int width, int height, int depth, float[] rgba) {
        return session().createReadOnlyRgbaFloatImage3D(width, height, depth, rgba);
    }

    protected Image3DWriteOnly createWriteOnlyRgbaFloatImage3DInternal(int width, int height, int depth) {
        return session().createWriteOnlyRgbaFloatImage3D(width, height, depth);
    }

    protected Image3DReadOnly createReadOnlyRgbaIntImage3DInternal(int width, int height, int depth, int[] rgba) {
        return session().createReadOnlyRgbaIntImage3D(width, height, depth, rgba);
    }

    protected Image3DWriteOnly createWriteOnlyRgbaIntImage3DInternal(int width, int height, int depth) {
        return session().createWriteOnlyRgbaIntImage3D(width, height, depth);
    }

    protected Image3DReadOnly createReadOnlyRgbaUIntImage3DInternal(int width, int height, int depth, int[] rgba) {
        return session().createReadOnlyRgbaUIntImage3D(width, height, depth, rgba);
    }

    protected Image3DWriteOnly createWriteOnlyRgbaUIntImage3DInternal(int width, int height, int depth) {
        return session().createWriteOnlyRgbaUIntImage3D(width, height, depth);
    }

    protected float[] readRgbaFloatImageInternal(Image2DReadOnly image) {
        return session().readRgbaFloatImage(image);
    }

    protected float[] readRgbaFloatImageInternal(Image2DWriteOnly image) {
        return session().readRgbaFloatImage(image);
    }

    protected float[] readRFloatImageInternal(Image2DReadOnly image) {
        return session().readRFloatImage(image);
    }

    protected float[] readRFloatImageInternal(Image2DWriteOnly image) {
        return session().readRFloatImage(image);
    }

    protected float[] readRgFloatImageInternal(Image2DReadOnly image) {
        return session().readRgFloatImage(image);
    }

    protected float[] readRgFloatImageInternal(Image2DWriteOnly image) {
        return session().readRgFloatImage(image);
    }

    protected int[] readRIntImageInternal(Image2DReadOnly image) {
        return session().readRIntImage(image);
    }

    protected int[] readRIntImageInternal(Image2DWriteOnly image) {
        return session().readRIntImage(image);
    }

    protected int[] readRgIntImageInternal(Image2DReadOnly image) {
        return session().readRgIntImage(image);
    }

    protected int[] readRgIntImageInternal(Image2DWriteOnly image) {
        return session().readRgIntImage(image);
    }

    protected int[] readRgbaIntImageInternal(Image2DReadOnly image) {
        return session().readRgbaIntImage(image);
    }

    protected int[] readRgbaIntImageInternal(Image2DWriteOnly image) {
        return session().readRgbaIntImage(image);
    }

    protected int[] readRgbaUIntImageInternal(Image2DReadOnly image) {
        return session().readRgbaUIntImage(image);
    }

    protected int[] readRgbaUIntImageInternal(Image2DWriteOnly image) {
        return session().readRgbaUIntImage(image);
    }

    protected int[] readRUIntImageInternal(Image2DReadOnly image) {
        return session().readRUIntImage(image);
    }

    protected int[] readRUIntImageInternal(Image2DWriteOnly image) {
        return session().readRUIntImage(image);
    }

    protected int[] readRgUIntImageInternal(Image2DReadOnly image) {
        return session().readRgUIntImage(image);
    }

    protected int[] readRgUIntImageInternal(Image2DWriteOnly image) {
        return session().readRgUIntImage(image);
    }

    protected byte[] readRgba8ImageInternal(Image2DReadOnly image) {
        return session().readRgba8Image(image);
    }

    protected byte[] readRgba8ImageInternal(Image2DWriteOnly image) {
        return session().readRgba8Image(image);
    }

    protected float[] readRgbaFloatImage1DInternal(Image1DReadOnly image) {
        return session().readRgbaFloatImage1D(image);
    }

    protected float[] readRgbaFloatImage1DInternal(Image1DWriteOnly image) {
        return session().readRgbaFloatImage1D(image);
    }

    protected int[] readRgbaUIntImage1DInternal(Image1DReadOnly image) {
        return session().readRgbaUIntImage1D(image);
    }

    protected int[] readRgbaUIntImage1DInternal(Image1DWriteOnly image) {
        return session().readRgbaUIntImage1D(image);
    }

    protected int[] readRgbaIntImage1DInternal(Image1DReadOnly image) {
        return session().readRgbaIntImage1D(image);
    }

    protected int[] readRgbaIntImage1DInternal(Image1DWriteOnly image) {
        return session().readRgbaIntImage1D(image);
    }

    protected float[] readRgbaFloatImage1DArrayInternal(Image1DArrayReadOnly image) {
        return session().readRgbaFloatImage1DArray(image);
    }

    protected float[] readRgbaFloatImage1DArrayInternal(Image1DArrayWriteOnly image) {
        return session().readRgbaFloatImage1DArray(image);
    }

    protected int[] readRgbaIntImage1DArrayInternal(Image1DArrayReadOnly image) {
        return session().readRgbaIntImage1DArray(image);
    }

    protected int[] readRgbaIntImage1DArrayInternal(Image1DArrayWriteOnly image) {
        return session().readRgbaIntImage1DArray(image);
    }

    protected int[] readRgbaUIntImage1DArrayInternal(Image1DArrayReadOnly image) {
        return session().readRgbaUIntImage1DArray(image);
    }

    protected int[] readRgbaUIntImage1DArrayInternal(Image1DArrayWriteOnly image) {
        return session().readRgbaUIntImage1DArray(image);
    }

    protected float[] readRgbaFloatImage1DBufferInternal(Image1DBufferReadOnly image) {
        return session().readRgbaFloatImage1DBuffer(image);
    }

    protected float[] readRgbaFloatImage1DBufferInternal(Image1DBufferWriteOnly image) {
        return session().readRgbaFloatImage1DBuffer(image);
    }

    protected int[] readRgbaIntImage1DBufferInternal(Image1DBufferReadOnly image) {
        return session().readRgbaIntImage1DBuffer(image);
    }

    protected int[] readRgbaIntImage1DBufferInternal(Image1DBufferWriteOnly image) {
        return session().readRgbaIntImage1DBuffer(image);
    }

    protected int[] readRgbaUIntImage1DBufferInternal(Image1DBufferReadOnly image) {
        return session().readRgbaUIntImage1DBuffer(image);
    }

    protected int[] readRgbaUIntImage1DBufferInternal(Image1DBufferWriteOnly image) {
        return session().readRgbaUIntImage1DBuffer(image);
    }

    protected float[] readRgbaFloatImage2DArrayInternal(Image2DArrayReadOnly image) {
        return session().readRgbaFloatImage2DArray(image);
    }

    protected float[] readRgbaFloatImage2DArrayInternal(Image2DArrayWriteOnly image) {
        return session().readRgbaFloatImage2DArray(image);
    }

    protected int[] readRgbaIntImage2DArrayInternal(Image2DArrayReadOnly image) {
        return session().readRgbaIntImage2DArray(image);
    }

    protected int[] readRgbaIntImage2DArrayInternal(Image2DArrayWriteOnly image) {
        return session().readRgbaIntImage2DArray(image);
    }

    protected int[] readRgbaUIntImage2DArrayInternal(Image2DArrayReadOnly image) {
        return session().readRgbaUIntImage2DArray(image);
    }

    protected int[] readRgbaUIntImage2DArrayInternal(Image2DArrayWriteOnly image) {
        return session().readRgbaUIntImage2DArray(image);
    }

    protected float[] readRgbaFloatImage3DInternal(Image3DReadOnly image) {
        return session().readRgbaFloatImage3D(image);
    }

    protected float[] readRgbaFloatImage3DInternal(Image3DWriteOnly image) {
        return session().readRgbaFloatImage3D(image);
    }

    protected int[] readRgbaIntImage3DInternal(Image3DReadOnly image) {
        return session().readRgbaIntImage3D(image);
    }

    protected int[] readRgbaIntImage3DInternal(Image3DWriteOnly image) {
        return session().readRgbaIntImage3D(image);
    }

    protected int[] readRgbaUIntImage3DInternal(Image3DReadOnly image) {
        return session().readRgbaUIntImage3D(image);
    }

    protected int[] readRgbaUIntImage3DInternal(Image3DWriteOnly image) {
        return session().readRgbaUIntImage3D(image);
    }

    protected Sampler createSamplerInternal(boolean normalizedCoordinates, int addressingMode, int filterMode) {
        return session().createSampler(normalizedCoordinates, addressingMode, filterMode);
    }

    protected Object createDeviceBuffer(OpenClBufferBinding binding) {
        return session().createReadWriteBuffer(bytesFor(binding));
    }

    protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
        if (binding.sourceArray() instanceof byte[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length);
            buffer.put(values).flip();
            session().queue().writeBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            return;
        }
        if (binding.sourceArray() instanceof short[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length * Short.BYTES);
            for (short value : values) {
                buffer.putShort(value);
            }
            buffer.flip();
            writeBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            return;
        }
        if (binding.sourceArray() instanceof int[] values) {
            IntBuffer buffer = allocateIntBuffer(values.length);
            buffer.put(values).flip();
            session().queue().writeBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            return;
        }
        if (binding.sourceArray() instanceof long[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length * Long.BYTES);
            for (long value : values) {
                buffer.putLong(value);
            }
            buffer.flip();
            writeBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            return;
        }
        if (binding.sourceArray() instanceof float[] values) {
            FloatBuffer buffer = allocateFloatBuffer(values.length);
            buffer.put(values).flip();
            session().queue().writeBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            return;
        }
        if (binding.sourceArray() instanceof double[] values) {
            DoubleBuffer buffer = allocateDoubleBuffer(values.length);
            buffer.put(values).flip();
            readWriteBufferDirect((OpenClBuffer) nativeBuffer, buffer, true);
            return;
        }
        if (binding.kind() == OpenClArgumentKind.STRUCT_ARRAY) {
            writeBufferDirect((OpenClBuffer) nativeBuffer, OpenClValuePacker.packStructArray(binding.sourceArray()));
            return;
        }
        if (binding.kind() == OpenClArgumentKind.VECTOR_ARRAY) {
            writeBufferDirect((OpenClBuffer) nativeBuffer, OpenClValuePacker.packVectorArray(binding.sourceArray()));
            return;
        }

        throw new IllegalArgumentException("Unsupported OpenCL upload source type: " + binding.sourceArray().getClass().getName());
    }

    protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
        compiledKernel.kernel().setArg(parameterIndex, (OpenClBuffer) nativeBuffer);
    }

    protected void bindLocalArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClLocalBinding binding) {
        checkCl(
                CL10.clSetKernelArg(compiledKernel.kernel().handle(), parameterIndex, binding.byteSize()),
                "clSetKernelArg"
        );
    }

    protected void bindScalarArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClScalarBinding binding) {
        switch (binding.kind()) {
            case INT8 -> checkCl(
                    CL10.clSetKernelArg1b(compiledKernel.kernel().handle(), parameterIndex, (Byte) binding.value()),
                    "clSetKernelArg1b"
            );
            case INT16 -> checkCl(
                    CL10.clSetKernelArg1s(compiledKernel.kernel().handle(), parameterIndex, (Short) binding.value()),
                    "clSetKernelArg1s"
            );
            case INT32 -> compiledKernel.kernel().setArgInt(parameterIndex, (Integer) binding.value());
            case INT64 -> checkCl(
                    CL10.clSetKernelArg1l(compiledKernel.kernel().handle(), parameterIndex, (Long) binding.value()),
                    "clSetKernelArg1l"
            );
            case FLOAT32 -> compiledKernel.kernel().setArgFloat(parameterIndex, (Float) binding.value());
            case FLOAT64 -> checkCl(
                    CL10.clSetKernelArg1d(compiledKernel.kernel().handle(), parameterIndex, (Double) binding.value()),
                    "clSetKernelArg1d"
            );
            case PACKED_VALUE -> {
                ByteBuffer valueBuffer = ((ByteBuffer) binding.value()).duplicate().order(ByteOrder.nativeOrder());
                valueBuffer.clear();
                checkCl(
                        CL10.clSetKernelArg(compiledKernel.kernel().handle(), parameterIndex, valueBuffer),
                        "clSetKernelArg"
                );
            }
            case IMAGE1D, IMAGE1D_ARRAY, IMAGE1D_BUFFER, IMAGE2D, IMAGE2D_ARRAY, IMAGE3D, SAMPLER ->
                    compiledKernel.kernel().setArgPointer(parameterIndex, (Long) binding.value());
            default -> throw new IllegalArgumentException("Unsupported OpenCL scalar binding kind: " + binding.kind());
        }
    }

    protected void enqueueKernel(OpenClCompiledKernel compiledKernel, long globalWorkSize) {
        long event = compiledKernel.kernel().enqueue1D(session().queue(), globalWorkSize, null);
        try {
            OpenClEvents.waitFor(event);
        } finally {
            OpenClEvents.release(event);
        }
        session().queue().finish();
    }

    protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
        if (binding.sourceArray() instanceof byte[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length);
            session().queue().readBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            buffer.position(0);
            buffer.get(values);
            return;
        }
        if (binding.sourceArray() instanceof short[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length * Short.BYTES);
            readBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            buffer.position(0);
            for (int i = 0; i < values.length; i++) {
                values[i] = buffer.getShort();
            }
            return;
        }
        if (binding.sourceArray() instanceof int[] values) {
            IntBuffer buffer = allocateIntBuffer(values.length);
            session().queue().readBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            buffer.position(0);
            buffer.get(values);
            return;
        }
        if (binding.sourceArray() instanceof long[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length * Long.BYTES);
            readBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            buffer.position(0);
            for (int i = 0; i < values.length; i++) {
                values[i] = buffer.getLong();
            }
            return;
        }
        if (binding.sourceArray() instanceof float[] values) {
            FloatBuffer buffer = allocateFloatBuffer(values.length);
            session().queue().readBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            buffer.position(0);
            buffer.get(values);
            return;
        }
        if (binding.sourceArray() instanceof double[] values) {
            DoubleBuffer buffer = allocateDoubleBuffer(values.length);
            readWriteBufferDirect((OpenClBuffer) nativeBuffer, buffer, false);
            buffer.position(0);
            buffer.get(values);
            return;
        }
        if (binding.kind() == OpenClArgumentKind.STRUCT_ARRAY) {
            ByteBuffer buffer = allocateByteBuffer(OpenClValuePacker.structArrayByteSize(binding.sourceArray()));
            readBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            OpenClValuePacker.unpackStructArray(buffer, binding.sourceArray());
            return;
        }
        if (binding.kind() == OpenClArgumentKind.VECTOR_ARRAY) {
            ByteBuffer buffer = allocateByteBuffer(OpenClValuePacker.vectorArrayByteSize(binding.sourceArray()));
            readBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            OpenClValuePacker.unpackVectorArray(buffer, binding.sourceArray());
            return;
        }

        throw new IllegalArgumentException("Unsupported OpenCL readback target type: " + binding.sourceArray().getClass().getName());
    }

    @Override
    public void close() {
        compiledKernels.values().forEach(OpenClCompiledKernel::close);
        compiledKernels.clear();

        nativeBuffers.values().forEach(value -> {
            if (value instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception exception) {
                    throw new RuntimeException("Failed to close OpenCL device buffer", exception);
                }
            }
        });
        nativeBuffers.clear();

        OpenClRuntimeSession currentSession = session;
        session = null;
        if (currentSession != null) {
            currentSession.close();
        }
    }

    int cacheSize() {
        return compiledKernels.size();
    }

    private OpenClRuntimeSession session() {
        OpenClRuntimeSession current = session;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            current = session;
            if (current == null) {
                try {
                    current = createSession();
                } catch (UnsatisfiedLinkError | IllegalStateException exception) {
                    throw new UnsupportedOperationException("OpenCL runtime is unavailable: " + exception.getMessage(), exception);
                }
                session = current;
            }
        }

        return current;
    }

    private Object resolveNativeBuffer(OpenClPreparedBufferBinding binding) {
        return nativeBuffers.computeIfAbsent(
                binding.handle().handleId(),
                ignored -> createDeviceBuffer(binding.binding())
        );
    }

    private long resolveGlobalWorkSize(OpenClPreparedExecution execution) {
        if (execution.bufferBindings().isEmpty()) {
            throw new UnsupportedOperationException(
                    "OpenCL execution requires at least one buffer argument to derive global work size for kernel "
                            + execution.compiledKernel().descriptor().kernelName()
            );
        }

        int expectedLength = execution.bufferBindings().get(0).binding().length();
        for (OpenClPreparedBufferBinding binding : execution.bufferBindings()) {
            if (binding.binding().length() != expectedLength) {
                throw new IllegalArgumentException(
                        "Mismatched GPU array lengths for kernel "
                                + execution.compiledKernel().descriptor().kernelName()
                                + ": expected "
                                + expectedLength
                                + " but found "
                                + binding.binding().length()
                );
            }
        }

        return expectedLength;
    }

    private long bytesFor(OpenClBufferBinding binding) {
        return switch (binding.kind()) {
            case BYTE_ARRAY -> binding.length();
            case SHORT_ARRAY -> (long) binding.length() * Short.BYTES;
            case INT_ARRAY -> (long) binding.length() * Integer.BYTES;
            case LONG_ARRAY -> (long) binding.length() * Long.BYTES;
            case FLOAT_ARRAY -> (long) binding.length() * Float.BYTES;
            case DOUBLE_ARRAY -> (long) binding.length() * Double.BYTES;
            case STRUCT_ARRAY -> OpenClValuePacker.structArrayByteSize(binding.sourceArray());
            case VECTOR_ARRAY -> OpenClValuePacker.vectorArrayByteSize(binding.sourceArray());
            default -> throw new IllegalArgumentException("Unsupported OpenCL buffer kind: " + binding.kind());
        };
    }

    private void writeBufferDirect(OpenClBuffer buffer, ByteBuffer values) {
        checkCl(
                CL10.clEnqueueWriteBuffer(session().queue().handle(), buffer.handle(), true, 0L, values, null, null),
                "clEnqueueWriteBuffer"
        );
        session().queue().finish();
    }

    private void readBufferDirect(OpenClBuffer buffer, ByteBuffer values) {
        checkCl(
                CL10.clEnqueueReadBuffer(session().queue().handle(), buffer.handle(), true, 0L, values, null, null),
                "clEnqueueReadBuffer"
        );
        session().queue().finish();
    }

    private void readWriteBufferDirect(OpenClBuffer buffer, DoubleBuffer values, boolean write) {
        int result = write
                ? CL10.clEnqueueWriteBuffer(session().queue().handle(), buffer.handle(), true, 0L, values, null, null)
                : CL10.clEnqueueReadBuffer(session().queue().handle(), buffer.handle(), true, 0L, values, null, null);
        checkCl(result, write ? "clEnqueueWriteBuffer" : "clEnqueueReadBuffer");
        session().queue().finish();
    }

    private void checkCl(int errorCode, String operation) {
        OpenClException.check(errorCode, operation);
    }

    private ByteBuffer allocateByteBuffer(int sizeBytes) {
        return ByteBuffer.allocateDirect(sizeBytes).order(ByteOrder.nativeOrder());
    }

    private IntBuffer allocateIntBuffer(int length) {
        return allocateByteBuffer(length * Integer.BYTES).asIntBuffer();
    }

    private FloatBuffer allocateFloatBuffer(int length) {
        return allocateByteBuffer(length * Float.BYTES).asFloatBuffer();
    }

    private DoubleBuffer allocateDoubleBuffer(int length) {
        return allocateByteBuffer(length * Double.BYTES).asDoubleBuffer();
    }
}
