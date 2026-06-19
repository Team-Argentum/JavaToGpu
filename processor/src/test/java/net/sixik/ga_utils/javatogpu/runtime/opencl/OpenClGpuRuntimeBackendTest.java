package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenClGpuRuntimeBackendTest {

    @Test
    void cachesCompiledKernelAcrossInvocations() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("value", "int", GpuKernelParameterAccess.VALUE)
                )
        );
        AtomicInteger compileCalls = new AtomicInteger();
        AtomicInteger executeCalls = new AtomicInteger();
        AtomicReference<OpenClCompiledKernel> firstCompiledKernel = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                OpenClCompiledKernel compiledKernel = new OpenClCompiledKernel(
                        kernelDescriptor,
                        "compiled:" + compileCalls.incrementAndGet()
                );
                firstCompiledKernel.compareAndSet(null, compiledKernel);
                return compiledKernel;
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                executeCalls.incrementAndGet();
                assertSame(firstCompiledKernel.get(), execution.compiledKernel());
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{1}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{2}));

        assertEquals(1, compileCalls.get());
        assertEquals(2, executeCalls.get());
        assertEquals(1, backend.cacheSize());
    }

    @Test
    void requiresBufferArgumentToDeriveGlobalWorkSize() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of()
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        UnsupportedOperationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[0]))
        );

        assertEquals(
                "OpenCL execution requires at least one buffer argument to derive global work size for kernel kernel",
                exception.getMessage()
        );
        assertEquals(1, backend.cacheSize());
    }

    @Test
    void executesPreparedArgumentsInKernelOrderAndReadsBackOutputs() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        float[] input = new float[]{1.0f, 2.0f, 3.0f};
        float[] output = new float[]{0.0f, 0.0f, 0.0f};
        List<String> events = new ArrayList<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            private final HashMap<Object, float[]> nativeArrays = new HashMap<>();
            private final HashMap<Integer, Object> boundBuffers = new HashMap<>();
            private final HashMap<Integer, Object> boundScalars = new HashMap<>();

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected Object createDeviceBuffer(OpenClBufferBinding binding) {
                float[] deviceArray = new float[binding.length()];
                nativeArrays.put(deviceArray, deviceArray);
                events.add("alloc:" + binding.length());
                return deviceArray;
            }

            @Override
            protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                System.arraycopy((float[]) binding.sourceArray(), 0, (float[]) nativeBuffer, 0, binding.length());
                events.add("upload:" + binding.length());
            }

            @Override
            protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
                boundBuffers.put(parameterIndex, nativeBuffer);
                events.add("bind-buffer:" + parameterIndex);
            }

            @Override
            protected void bindScalarArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClScalarBinding binding) {
                boundScalars.put(parameterIndex, binding.value());
                events.add("bind-scalar:" + parameterIndex);
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, long globalWorkSize) {
                float[] in = (float[]) boundBuffers.get(0);
                float scale = (Float) boundScalars.get(1);
                float[] out = (float[]) boundBuffers.get(2);
                for (int i = 0; i < globalWorkSize; i++) {
                    out[i] = in[i] + scale;
                }
                events.add("enqueue:" + globalWorkSize);
            }

            @Override
            protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                System.arraycopy((float[]) nativeBuffer, 0, (float[]) binding.sourceArray(), 0, binding.length());
                events.add("readback:" + binding.length());
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, 2.5f, output}));

        assertEquals(
                Arrays.asList(
                        "alloc:3",
                        "upload:3",
                        "alloc:3",
                        "upload:3",
                        "bind-buffer:0",
                        "bind-scalar:1",
                        "bind-buffer:2",
                        "enqueue:3",
                        "readback:3"
                ),
                events
        );
        assertEquals(3.5f, output[0]);
        assertEquals(4.5f, output[1]);
        assertEquals(5.5f, output[2]);
    }

    @Test
    void rejectsMismatchedBufferLengthsBeforeKernelLaunch() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger executeCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected Object createDeviceBuffer(OpenClBufferBinding binding) {
                return new Object();
            }

            @Override
            protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                // no-op for length validation path
            }

            @Override
            protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
                // no-op for length validation path
            }

            @Override
            protected void bindScalarArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClScalarBinding binding) {
                // no-op for length validation path
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, long globalWorkSize) {
                executeCalls.incrementAndGet();
            }

            @Override
            protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                // no-op for length validation path
            }
        };

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{new float[]{1.0f, 2.0f}, new float[]{0.0f}}
                ))
        );

        assertEquals(
                "Mismatched GPU array lengths for kernel kernel: expected 2 but found 1",
                exception.getMessage()
        );
        assertEquals(0, executeCalls.get());
    }

    @Test
    void reportsUnavailableOpenClRuntimeClearly() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY)
                )
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeSession createSession() {
                throw new UnsatisfiedLinkError("LWJGL OpenCL bindings are missing");
            }
        };

        UnsupportedOperationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new float[]{1.0f}}))
        );

        assertEquals("OpenCL runtime is unavailable: LWJGL OpenCL bindings are missing", exception.getMessage());
    }
}
