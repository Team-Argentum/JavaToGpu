package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.Float2;
import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUStruct;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class OpenClGpuRuntimeBackendMarshallingTest {

    @Test
    void backendPassesMarshalledArgumentsToExecuteHook() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("count", "int", GpuKernelParameterAccess.VALUE)
                )
        );
        AtomicReference<OpenClPreparedExecution> capturedExecution = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                capturedExecution.set(execution);
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new float[]{1.0f, 2.0f}, 4}));

        OpenClPreparedExecution execution = capturedExecution.get();
        assertEquals(1, execution.bufferBindings().size());
        assertEquals(1, execution.scalarBindings().size());
        OpenClPreparedBufferBinding arrayArgument = assertInstanceOf(OpenClPreparedBufferBinding.class, execution.bufferBindings().get(0));
        assertEquals(OpenClArgumentKind.FLOAT_ARRAY, arrayArgument.handle().kind());
        assertEquals(GpuKernelParameterAccess.READ_ONLY, arrayArgument.access());
        OpenClScalarBinding scalarArgument = assertInstanceOf(OpenClScalarBinding.class, execution.scalarBindings().get(0));
        assertEquals(OpenClArgumentKind.INT32, scalarArgument.kind());
        assertEquals(GpuKernelParameterAccess.VALUE, scalarArgument.access());
    }

    @Test
    void backendCarriesAdditionalPrimitiveKindsIntoPreparedExecution() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("bytes", "byte[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("weight", "double", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("ints", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicReference<OpenClPreparedExecution> capturedExecution = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                capturedExecution.set(execution);
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new byte[]{1, 2}, 1.25d, new int[]{3, 4}}));

        OpenClPreparedExecution execution = capturedExecution.get();
        assertEquals(OpenClArgumentKind.BYTE_ARRAY, execution.bufferBindings().get(0).handle().kind());
        assertEquals(OpenClArgumentKind.INT_ARRAY, execution.bufferBindings().get(1).handle().kind());
        assertEquals(OpenClArgumentKind.FLOAT64, execution.scalarBindings().get(0).kind());
    }

    @Test
    void backendCarriesPackedVectorArgumentsIntoPreparedExecution() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("bias", "Float2", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicReference<OpenClPreparedExecution> capturedExecution = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                capturedExecution.set(execution);
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new Float2(1.0f, 2.0f), new float[4]}));

        OpenClPreparedExecution execution = capturedExecution.get();
        assertEquals(1, execution.bufferBindings().size());
        assertEquals(1, execution.scalarBindings().size());
        OpenClScalarBinding vectorArgument = assertInstanceOf(OpenClScalarBinding.class, execution.scalarBindings().get(0));
        assertEquals(OpenClArgumentKind.PACKED_VALUE, vectorArgument.kind());
        assertEquals(8, ((ByteBuffer) vectorArgument.value()).remaining());
    }

    @Test
    void backendCarriesPackedStructArgumentsIntoPreparedExecution() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("sample", "sample.Sample", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicReference<OpenClPreparedExecution> capturedExecution = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                capturedExecution.set(execution);
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new Sample(1.0f, 2.0f), new float[4]}));

        OpenClPreparedExecution execution = capturedExecution.get();
        assertEquals(1, execution.bufferBindings().size());
        assertEquals(1, execution.scalarBindings().size());
        OpenClScalarBinding structArgument = assertInstanceOf(OpenClScalarBinding.class, execution.scalarBindings().get(0));
        assertEquals(OpenClArgumentKind.PACKED_VALUE, structArgument.kind());
        assertEquals(8, ((ByteBuffer) structArgument.value()).remaining());
    }

    @Test
    void backendCarriesImageAndSamplerArgumentsIntoPreparedExecution() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicReference<OpenClPreparedExecution> capturedExecution = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                capturedExecution.set(execution);
            }
        };

        backend.invoke(new GpuKernelInvocation(
                descriptor,
                new Object[]{
                        Image2DReadOnly.borrowed(101L, 64, 32),
                        Image2DWriteOnly.borrowed(202L, 64, 32),
                        Sampler.borrowed(303L),
                        new float[4]
                }
        ));

        OpenClPreparedExecution execution = capturedExecution.get();
        assertEquals(1, execution.bufferBindings().size());
        assertEquals(3, execution.scalarBindings().size());
        assertEquals(OpenClArgumentKind.IMAGE2D, execution.scalarBindings().get(0).kind());
        assertEquals(101L, execution.scalarBindings().get(0).value());
        assertEquals(OpenClArgumentKind.IMAGE2D, execution.scalarBindings().get(1).kind());
        assertEquals(202L, execution.scalarBindings().get(1).value());
        assertEquals(OpenClArgumentKind.SAMPLER, execution.scalarBindings().get(2).kind());
        assertEquals(303L, execution.scalarBindings().get(2).value());
    }

    @Test
    void backendCarriesImage3dArgumentsIntoPreparedExecution() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image3DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image3DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicReference<OpenClPreparedExecution> capturedExecution = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                capturedExecution.set(execution);
            }
        };

        backend.invoke(new GpuKernelInvocation(
                descriptor,
                new Object[]{
                        Image3DReadOnly.borrowed(101L, 8, 4, 2),
                        Image3DWriteOnly.borrowed(202L, 8, 4, 2),
                        new float[4]
                }
        ));

        OpenClPreparedExecution execution = capturedExecution.get();
        assertEquals(1, execution.bufferBindings().size());
        assertEquals(2, execution.scalarBindings().size());
        assertEquals(OpenClArgumentKind.IMAGE3D, execution.scalarBindings().get(0).kind());
        assertEquals(101L, execution.scalarBindings().get(0).value());
        assertEquals(OpenClArgumentKind.IMAGE3D, execution.scalarBindings().get(1).kind());
        assertEquals(202L, execution.scalarBindings().get(1).value());
    }

    @GPUStruct
    static final class Sample {
        float x;
        float y;

        Sample(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
