package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.Image1DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DBufferReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DBufferWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DMipmappedReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DMipmappedWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Sampler;
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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClGpuRuntimeBackendTest {

    @Test
    void cachesCompiledKernelAcrossInvocations() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
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

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

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

        assertTrue(exception.getMessage().contains(
                "OpenCL execution requires at least one buffer argument to derive global work size for kernel kernel"
        ));
        assertTrue(exception.getMessage().contains("explicit work-size configuration is not exposed here yet"));
        assertEquals(0, backend.cacheSize());
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

        assertTrue(exception.getMessage().contains(
                "Mismatched GPU array lengths for kernel kernel: expected 2 but found 1"
        ));
        assertTrue(exception.getMessage().contains("must share the same logical length"));
        assertEquals(0, executeCalls.get());
        assertEquals(0, backend.cacheSize());
    }

    @Test
    void rejectsDoubleKernelWhenDeviceLacksFp64BeforeCompile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Double/kernel.cl",
                "__kernel void kernel(__global double* input, __global double* output) { output[0] = input[0]; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "double[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "double[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", false, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                compileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{new double[]{1.0d}, new double[]{0.0d}}
                ))
        );

        assertTrue(exception.getMessage().contains(
                "OpenCL capability precheck failed for kernel kernel: device Fake GPU does not advertise fp64 support, but the kernel uses double precision"
        ));
        assertTrue(exception.getMessage().contains("float/fallback path"));
        assertEquals(0, compileCalls.get());
        assertEquals(0, backend.cacheSize());
    }

    @Test
    void rejectsImageKernelWhenDeviceLacksImageSupportBeforeCompile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Image/kernel.cl",
                "__kernel void kernel(read_only image2d_t inputImage, sampler_t sampler, __global int* output) { }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, false, false, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                compileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{Image2DReadOnly.borrowed(1L, 1, 1), Sampler.borrowed(2L), new int[]{0}}
                ))
        );

        assertTrue(exception.getMessage().contains(
                "OpenCL capability precheck failed for kernel kernel: device Fake GPU does not support OpenCL images, but the kernel requires image/sampler parameters"
        ));
        assertTrue(exception.getMessage().contains("buffer-backed kernels"));
        assertEquals(0, compileCalls.get());
    }

    @Test
    void rejectsImage3dWriteKernelWhenDeviceLacks3dWriteSupportBeforeCompile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Image3d/kernel.cl",
                "__kernel void kernel(read_only image3d_t inputImage, write_only image3d_t outputImage, sampler_t sampler, __global int* output) { }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image3DReadOnly", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("outputImage", "Image3DWriteOnly", GpuKernelParameterAccess.READ_WRITE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, false, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                compileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{Image3DReadOnly.borrowed(1L, 1, 1, 1), Image3DWriteOnly.borrowed(2L, 1, 1, 1), Sampler.borrowed(3L), new int[]{0}}
                ))
        );

        assertTrue(exception.getMessage().contains(
                "OpenCL capability precheck failed for kernel kernel: device Fake GPU does not support 3D image writes required by the kernel"
        ));
        assertTrue(exception.getMessage().contains("2D/buffer workflows"));
        assertEquals(0, compileCalls.get());
    }

    @Test
    void rejectsLocalMemoryRequestThatExceedsDeviceBudgetBeforeCompile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Local/kernel.cl",
                "__kernel void kernel(__local float* scratch, __global float* output) { output[0] = scratch[0]; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("scratch", "float[]", GpuKernelParameterAccess.LOCAL),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 8L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                compileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{new float[]{1.0f, 2.0f, 3.0f}, new float[]{0.0f}}
                ))
        );

        assertTrue(exception.getMessage().contains(
                "OpenCL capability precheck failed for kernel kernel: requested 12 bytes of local memory, but device Fake GPU exposes only 8 bytes"
        ));
        assertTrue(exception.getMessage().contains("reduce the local scratch size"));
        assertEquals(0, compileCalls.get());
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

        assertTrue(exception.getMessage().contains("OpenCL runtime is unavailable: LWJGL OpenCL bindings are missing"));
        assertTrue(exception.getMessage().contains("GpuRuntime.trySelect(...)"));
    }

    @Test
    void formatsKernelBuildFailuresWithKernelAndDeviceContext() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global float* output) { output[0] = 1.0f; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                throw new RuntimeException("driver build log: unknown type name 'half16'");
            }
        };

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new float[]{0.0f}}))
        );

        assertTrue(exception.getMessage().contains(
                "OpenCL kernel build failed for kernel kernel on device Fake GPU [javatogpu/sample/Demo/kernel.cl]: driver build log: unknown type name 'half16'"
        ));
        assertTrue(exception.getMessage().contains("enable ABI debug"));
        assertTrue(exception.getMessage().contains("opencl-known-device-quirks.md"));
    }

    @Test
    void formatsKernelExecutionFailuresWithKernelAndDeviceContext() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global float* output) { output[0] = 1.0f; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                throw new RuntimeException("clEnqueueNDRangeKernel failed: CL_OUT_OF_RESOURCES");
            }
        };

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new float[]{0.0f}}))
        );

        assertTrue(exception.getMessage().contains(
                "OpenCL kernel execution failed for kernel kernel on device Fake GPU: clEnqueueNDRangeKernel failed: CL_OUT_OF_RESOURCES"
        ));
        assertTrue(exception.getMessage().contains("fallback backend"));
    }

    @Test
    void closeClearsBufferRegistryClosesTrackedBuffersAndAllowsReuse() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        AtomicInteger trackedBufferCloseCalls = new AtomicInteger();
        AtomicInteger deviceBufferAllocations = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                compileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:" + compileCalls.get());
            }

            @Override
            protected Object createDeviceBuffer(OpenClBufferBinding binding) {
                deviceBufferAllocations.incrementAndGet();
                return new AutoCloseable() {
                    private boolean closed;

                    @Override
                    public void close() {
                        if (!closed) {
                            closed = true;
                            trackedBufferCloseCalls.incrementAndGet();
                        }
                    }
                };
            }

            @Override
            protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                // no-op
            }

            @Override
            protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
                // no-op
            }

            @Override
            protected void bindScalarArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClScalarBinding binding) {
                // no-op
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, long globalWorkSize) {
                // no-op
            }

            @Override
            protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                // no-op
            }
        };

        int[] output = new int[]{0};
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{output}));

        assertEquals(1, compileCalls.get());
        assertEquals(1, deviceBufferAllocations.get());
        assertEquals(1, backend.cacheSize());
        assertEquals(1, backend.bufferCacheSize());

        backend.close();

        assertEquals(1, trackedBufferCloseCalls.get());
        assertEquals(0, backend.cacheSize());
        assertEquals(0, backend.bufferCacheSize());

        backend.close();

        assertEquals(1, trackedBufferCloseCalls.get());

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{output}));

        assertEquals(2, compileCalls.get());
        assertEquals(2, deviceBufferAllocations.get());
        assertEquals(1, backend.cacheSize());
        assertEquals(1, backend.bufferCacheSize());
    }

    @Test
    void repeatedCreateInvokeCloseCyclesRemainStable() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        AtomicInteger deviceBufferAllocations = new AtomicInteger();
        AtomicInteger trackedBufferCloseCalls = new AtomicInteger();

        for (int iteration = 0; iteration < 25; iteration++) {
            OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
                @Override
                protected OpenClRuntimeCapabilities runtimeCapabilities() {
                    return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
                }

                @Override
                protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                    compileCalls.incrementAndGet();
                    return new OpenClCompiledKernel(kernelDescriptor, "compiled:" + compileCalls.get());
                }

                @Override
                protected Object createDeviceBuffer(OpenClBufferBinding binding) {
                    deviceBufferAllocations.incrementAndGet();
                    return new AutoCloseable() {
                        private boolean closed;

                        @Override
                        public void close() {
                            if (!closed) {
                                closed = true;
                                trackedBufferCloseCalls.incrementAndGet();
                            }
                        }
                    };
                }

                @Override
                protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                    // no-op
                }

                @Override
                protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
                    // no-op
                }

                @Override
                protected void bindScalarArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClScalarBinding binding) {
                    // no-op
                }

                @Override
                protected void enqueueKernel(OpenClCompiledKernel compiledKernel, long globalWorkSize) {
                    // no-op
                }

                @Override
                protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                    // no-op
                }
            };

            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{iteration}}));

            assertEquals(1, backend.cacheSize());
            assertEquals(1, backend.bufferCacheSize());

            backend.close();

            assertEquals(0, backend.cacheSize());
            assertEquals(0, backend.bufferCacheSize());
        }

        assertEquals(25, compileCalls.get());
        assertEquals(25, deviceBufferAllocations.get());
        assertEquals(25, trackedBufferCloseCalls.get());
    }

    @Test
    void sharedCacheRetainsCompiledKernelAcrossBackendInstancesUntilExplicitShutdown() {
        OpenClGpuRuntimeBackend.shutdownSharedCache();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();

        OpenClGpuRuntimeBackend firstBackend = new OpenClGpuRuntimeBackend(OpenClGpuRuntimeBackend.CacheMode.SHARED) {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:" + compileCalls.incrementAndGet());
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        firstBackend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        assertEquals(1, compileCalls.get());
        assertEquals(1, firstBackend.cacheSize());
        firstBackend.close();
        assertEquals(1, firstBackend.cacheSize());

        OpenClGpuRuntimeBackend secondBackend = new OpenClGpuRuntimeBackend(OpenClGpuRuntimeBackend.CacheMode.SHARED) {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:" + compileCalls.incrementAndGet());
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        secondBackend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        assertEquals(1, compileCalls.get());
        assertEquals(1, secondBackend.cacheSize());
        secondBackend.close();

        OpenClGpuRuntimeBackend.shutdownSharedCache();

        OpenClGpuRuntimeBackend thirdBackend = new OpenClGpuRuntimeBackend(OpenClGpuRuntimeBackend.CacheMode.SHARED) {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:" + compileCalls.incrementAndGet());
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        thirdBackend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        assertEquals(2, compileCalls.get());
        thirdBackend.close();
        OpenClGpuRuntimeBackend.shutdownSharedCache();
    }

    @Test
    void createsHighLevelFloatImageThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgbaFloatImageInternal(int width, int height, float[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(rgba);
                return Image2DReadOnly.borrowed(777L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgbaFloatImage(2, 1, new float[]{1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f});

        assertEquals(777L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRFloatImageThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRFloatImageInternal(int width, int height, float[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(778L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRFloatImage(2, 1, new float[]{1.0f, 2.0f});

        assertEquals(778L, image.handle());
        assertEquals(2, captured.get().length);
    }

    @Test
    void createsHighLevelRgFloatImageThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgFloatImageInternal(int width, int height, float[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(779L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgFloatImage(2, 1, new float[]{1.0f, 2.0f, 3.0f, 4.0f});

        assertEquals(779L, image.handle());
        assertEquals(4, captured.get().length);
    }

    @Test
    void createsHighLevelDepthImageThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyDepthImageInternal(int width, int height, float[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(7791L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyDepthImage(2, 1, new float[]{0.25f, 0.75f});

        assertEquals(7791L, image.handle());
        assertEquals(2, captured.get().length);
    }

    @Test
    void createsHighLevelRIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRIntImageInternal(int width, int height, int[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(780L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRIntImage(2, 1, new int[]{1, 2});

        assertEquals(780L, image.handle());
        assertEquals(2, captured.get().length);
    }

    @Test
    void createsHighLevelRgIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgIntImageInternal(int width, int height, int[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(781L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgIntImage(2, 1, new int[]{1, 2, 3, 4});

        assertEquals(781L, image.handle());
        assertEquals(4, captured.get().length);
    }

    @Test
    void createsHighLevelSamplerThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Sampler createSamplerInternal(boolean normalizedCoordinates, int addressingMode, int filterMode) {
                assertEquals(true, normalizedCoordinates);
                assertEquals(5, addressingMode);
                assertEquals(6, filterMode);
                return Sampler.borrowed(888L);
            }
        };

        Sampler sampler = backend.createSampler(true, 5, 6);

        assertEquals(888L, sampler.handle());
    }

    @Test
    void createsHighLevelRgba8ImageThroughProtectedHook() {
        AtomicReference<byte[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgba8ImageInternal(int width, int height, byte[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(rgba);
                return Image2DReadOnly.borrowed(999L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgba8Image(
                2,
                1,
                new byte[]{1, 2, 3, 4, 5, 6, 7, 8}
        );

        assertEquals(999L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaUIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgbaUIntImageInternal(int width, int height, int[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(rgba);
                return Image2DReadOnly.borrowed(1001L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgbaUIntImage(
                2,
                1,
                new int[]{1, 2, 3, 4, 5, 6, 7, 8}
        );

        assertEquals(1001L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRUIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRUIntImageInternal(int width, int height, int[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(1002L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRUIntImage(2, 1, new int[]{1, 2});

        assertEquals(1002L, image.handle());
        assertEquals(2, captured.get().length);
    }

    @Test
    void createsHighLevelRgUIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgUIntImageInternal(int width, int height, int[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(1003L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgUIntImage(2, 1, new int[]{1, 2, 3, 4});

        assertEquals(1003L, image.handle());
        assertEquals(4, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaFloatImage3dThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image3DReadOnly createReadOnlyRgbaFloatImage3DInternal(int width, int height, int depth, float[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                assertEquals(2, depth);
                captured.set(rgba);
                return Image3DReadOnly.borrowed(1004L, width, height, depth);
            }
        };

        Image3DReadOnly image = backend.createReadOnlyRgbaFloatImage3D(
                2,
                1,
                2,
                new float[]{
                        1.0f, 0.0f, 0.0f, 1.0f,
                        0.0f, 1.0f, 0.0f, 1.0f,
                        0.0f, 0.0f, 1.0f, 1.0f,
                        1.0f, 1.0f, 1.0f, 1.0f
                }
        );

        assertEquals(1004L, image.handle());
        assertEquals(16, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaIntImage3dThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image3DReadOnly createReadOnlyRgbaIntImage3DInternal(int width, int height, int depth, int[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                assertEquals(2, depth);
                captured.set(rgba);
                return Image3DReadOnly.borrowed(1005L, width, height, depth);
            }
        };

        Image3DReadOnly image = backend.createReadOnlyRgbaIntImage3D(
                2,
                1,
                2,
                new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}
        );

        assertEquals(1005L, image.handle());
        assertEquals(16, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaUIntImage3dThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image3DReadOnly createReadOnlyRgbaUIntImage3DInternal(int width, int height, int depth, int[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                assertEquals(2, depth);
                captured.set(rgba);
                return Image3DReadOnly.borrowed(1006L, width, height, depth);
            }
        };

        Image3DReadOnly image = backend.createReadOnlyRgbaUIntImage3D(
                2,
                1,
                2,
                new int[]{101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116}
        );

        assertEquals(1006L, image.handle());
        assertEquals(16, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaFloatImage1dThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image1DReadOnly createReadOnlyRgbaFloatImage1DInternal(int width, float[] rgba) {
                assertEquals(2, width);
                captured.set(rgba);
                return Image1DReadOnly.borrowed(1007L, width);
            }
        };

        Image1DReadOnly image = backend.createReadOnlyRgbaFloatImage1D(2, new float[]{1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f});

        assertEquals(1007L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaUIntImage1dThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image1DReadOnly createReadOnlyRgbaUIntImage1DInternal(int width, int[] rgba) {
                assertEquals(2, width);
                captured.set(rgba);
                return Image1DReadOnly.borrowed(1008L, width);
            }
        };

        Image1DReadOnly image = backend.createReadOnlyRgbaUIntImage1D(2, new int[]{1, 2, 3, 4, 5, 6, 7, 8});

        assertEquals(1008L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaUIntImage1dArrayThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image1DArrayReadOnly createReadOnlyRgbaUIntImage1DArrayInternal(int width, int layers, int[] rgba) {
                assertEquals(2, width);
                assertEquals(2, layers);
                captured.set(rgba);
                return Image1DArrayReadOnly.borrowed(1010L, width, layers);
            }
        };

        Image1DArrayReadOnly image = backend.createReadOnlyRgbaUIntImage1DArray(2, 2, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});

        assertEquals(1010L, image.handle());
        assertEquals(16, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaUIntImage1dBufferThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image1DBufferReadOnly createReadOnlyRgbaUIntImage1DBufferInternal(int width, int[] rgba) {
                assertEquals(2, width);
                captured.set(rgba);
                return Image1DBufferReadOnly.borrowed(1011L, width);
            }
        };

        Image1DBufferReadOnly image = backend.createReadOnlyRgbaUIntImage1DBuffer(2, new int[]{1, 2, 3, 4, 5, 6, 7, 8});

        assertEquals(1011L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaFloatImage2dArrayThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DArrayReadOnly createReadOnlyRgbaFloatImage2DArrayInternal(int width, int height, int layers, float[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                assertEquals(2, layers);
                captured.set(rgba);
                return Image2DArrayReadOnly.borrowed(1012L, width, height, layers);
            }
        };

        Image2DArrayReadOnly image = backend.createReadOnlyRgbaFloatImage2DArray(2, 1, 2, new float[]{1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1});

        assertEquals(1012L, image.handle());
        assertEquals(16, captured.get().length);
    }

    @Test
    void createsHighLevelMipmappedFloatImageThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DMipmappedReadOnly createReadOnlyRgbaFloatImageMipmappedInternal(int width, int height, int mipLevels, float[] rgba) {
                assertEquals(4, width);
                assertEquals(2, height);
                assertEquals(2, mipLevels);
                captured.set(rgba);
                return Image2DMipmappedReadOnly.borrowed(10121L, width, height, mipLevels);
            }
        };

        Image2DMipmappedReadOnly image = backend.createReadOnlyRgbaFloatImageMipmapped(4, 2, 2, new float[]{
                1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1,
                1, 0, 1, 1, 0, 1, 1, 1, 1, 1, 0, 1, 0, 0, 0, 1,
                0.5f, 0.5f, 0.5f, 1.0f, 0.25f, 0.25f, 0.25f, 1.0f
        });

        assertEquals(10121L, image.handle());
        assertEquals(40, captured.get().length);
    }

    @Test
    void createsHighLevelMipmappedUIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DMipmappedReadOnly createReadOnlyRgbaUIntImageMipmappedInternal(int width, int height, int mipLevels, int[] rgba) {
                assertEquals(4, width);
                assertEquals(2, height);
                assertEquals(2, mipLevels);
                captured.set(rgba);
                return Image2DMipmappedReadOnly.borrowed(10122L, width, height, mipLevels);
            }
        };

        Image2DMipmappedReadOnly image = backend.createReadOnlyRgbaUIntImageMipmapped(4, 2, 2, new int[]{
                1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23, 24,
                25, 26, 27, 28, 29, 30, 31, 32,
                33, 34, 35, 36, 37, 38, 39, 40
        });

        assertEquals(10122L, image.handle());
        assertEquals(40, captured.get().length);
    }

    @Test
    void readsHighLevelFloatImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgbaFloatImageInternal(Image2DWriteOnly image) {
                assertEquals(123L, image.handle());
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f};
            }
        };

        float[] rgba = backend.readRgbaFloatImage(Image2DWriteOnly.borrowed(123L, 1, 1));

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f}, rgba);
    }

    @Test
    void readsHighLevelRFloatImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRFloatImageInternal(Image2DReadOnly image) {
                assertEquals(124L, image.handle());
                return new float[]{1.0f, 2.0f};
            }
        };

        float[] values = backend.readRFloatImage(Image2DReadOnly.borrowed(124L, 2, 1));

        assertArrayEquals(new float[]{1.0f, 2.0f}, values);
    }

    @Test
    void readsHighLevelRgFloatImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgFloatImageInternal(Image2DWriteOnly image) {
                assertEquals(125L, image.handle());
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f};
            }
        };

        float[] values = backend.readRgFloatImage(Image2DWriteOnly.borrowed(125L, 2, 1));

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f}, values);
    }

    @Test
    void readsHighLevelDepthImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readDepthImageInternal(Image2DWriteOnly image) {
                assertEquals(1251L, image.handle());
                return new float[]{0.25f, 0.75f};
            }
        };

        float[] values = backend.readDepthImage(Image2DWriteOnly.borrowed(1251L, 2, 1));

        assertArrayEquals(new float[]{0.25f, 0.75f}, values);
    }

    @Test
    void readsHighLevelRIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRIntImageInternal(Image2DReadOnly image) {
                assertEquals(126L, image.handle());
                return new int[]{7, 8};
            }
        };

        int[] values = backend.readRIntImage(Image2DReadOnly.borrowed(126L, 2, 1));

        assertArrayEquals(new int[]{7, 8}, values);
    }

    @Test
    void readsHighLevelRgIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgIntImageInternal(Image2DWriteOnly image) {
                assertEquals(127L, image.handle());
                return new int[]{7, 8, 9, 10};
            }
        };

        int[] values = backend.readRgIntImage(Image2DWriteOnly.borrowed(127L, 2, 1));

        assertArrayEquals(new int[]{7, 8, 9, 10}, values);
    }

    @Test
    void readsHighLevelIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaIntImageInternal(Image2DReadOnly image) {
                assertEquals(321L, image.handle());
                return new int[]{5, 6, 7, 8};
            }
        };

        int[] rgba = backend.readRgbaIntImage(Image2DReadOnly.borrowed(321L, 1, 1));

        assertArrayEquals(new int[]{5, 6, 7, 8}, rgba);
    }

    @Test
    void readsHighLevelRgbaUIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaUIntImageInternal(Image2DReadOnly image) {
                assertEquals(741L, image.handle());
                return new int[]{11, 12, 13, 14};
            }
        };

        int[] rgba = backend.readRgbaUIntImage(Image2DReadOnly.borrowed(741L, 1, 1));

        assertArrayEquals(new int[]{11, 12, 13, 14}, rgba);
    }

    @Test
    void readsHighLevelRUIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRUIntImageInternal(Image2DReadOnly image) {
                assertEquals(742L, image.handle());
                return new int[]{15, 16};
            }
        };

        int[] values = backend.readRUIntImage(Image2DReadOnly.borrowed(742L, 2, 1));

        assertArrayEquals(new int[]{15, 16}, values);
    }

    @Test
    void readsHighLevelRgUIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgUIntImageInternal(Image2DWriteOnly image) {
                assertEquals(743L, image.handle());
                return new int[]{15, 16, 17, 18};
            }
        };

        int[] values = backend.readRgUIntImage(Image2DWriteOnly.borrowed(743L, 2, 1));

        assertArrayEquals(new int[]{15, 16, 17, 18}, values);
    }

    @Test
    void readsHighLevelRgba8ImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected byte[] readRgba8ImageInternal(Image2DWriteOnly image) {
                assertEquals(654L, image.handle());
                return new byte[]{9, 10, 11, 12};
            }
        };

        byte[] rgba = backend.readRgba8Image(Image2DWriteOnly.borrowed(654L, 1, 1));

        assertArrayEquals(new byte[]{9, 10, 11, 12}, rgba);
    }

    @Test
    void readsHighLevelRgbaFloatImage3dThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgbaFloatImage3DInternal(Image3DWriteOnly image) {
                assertEquals(905L, image.handle());
                assertEquals(2, image.depth());
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f};
            }
        };

        float[] rgba = backend.readRgbaFloatImage3D(Image3DWriteOnly.borrowed(905L, 1, 1, 2));

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f}, rgba);
    }

    @Test
    void readsHighLevelRgbaIntImage3dThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaIntImage3DInternal(Image3DWriteOnly image) {
                assertEquals(906L, image.handle());
                assertEquals(2, image.depth());
                return new int[]{1, 2, 3, 4, 5, 6, 7, 8};
            }
        };

        int[] rgba = backend.readRgbaIntImage3D(Image3DWriteOnly.borrowed(906L, 1, 1, 2));

        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7, 8}, rgba);
    }

    @Test
    void readsHighLevelRgbaUIntImage3dThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaUIntImage3DInternal(Image3DWriteOnly image) {
                assertEquals(907L, image.handle());
                assertEquals(2, image.depth());
                return new int[]{11, 12, 13, 14, 15, 16, 17, 18};
            }
        };

        int[] rgba = backend.readRgbaUIntImage3D(Image3DWriteOnly.borrowed(907L, 1, 1, 2));

        assertArrayEquals(new int[]{11, 12, 13, 14, 15, 16, 17, 18}, rgba);
    }

    @Test
    void readsHighLevelRgbaFloatImage1dThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgbaFloatImage1DInternal(Image1DWriteOnly image) {
                assertEquals(908L, image.handle());
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f};
            }
        };

        float[] rgba = backend.readRgbaFloatImage1D(Image1DWriteOnly.borrowed(908L, 1));

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f}, rgba);
    }

    @Test
    void readsHighLevelRgbaUIntImage1dThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaUIntImage1DInternal(Image1DWriteOnly image) {
                assertEquals(909L, image.handle());
                return new int[]{9, 10, 11, 12};
            }
        };

        int[] rgba = backend.readRgbaUIntImage1D(Image1DWriteOnly.borrowed(909L, 1));

        assertArrayEquals(new int[]{9, 10, 11, 12}, rgba);
    }

    @Test
    void readsHighLevelRgbaUIntImage1dArrayThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaUIntImage1DArrayInternal(Image1DArrayWriteOnly image) {
                assertEquals(910L, image.handle());
                assertEquals(2, image.layers());
                return new int[]{9, 10, 11, 12, 13, 14, 15, 16};
            }
        };

        int[] rgba = backend.readRgbaUIntImage1DArray(Image1DArrayWriteOnly.borrowed(910L, 1, 2));

        assertArrayEquals(new int[]{9, 10, 11, 12, 13, 14, 15, 16}, rgba);
    }

    @Test
    void readsHighLevelRgbaIntImage1dBufferThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaIntImage1DBufferInternal(Image1DBufferWriteOnly image) {
                assertEquals(911L, image.handle());
                return new int[]{9, 10, 11, 12};
            }
        };

        int[] rgba = backend.readRgbaIntImage1DBuffer(Image1DBufferWriteOnly.borrowed(911L, 1));

        assertArrayEquals(new int[]{9, 10, 11, 12}, rgba);
    }

    @Test
    void readsHighLevelRgbaFloatImage2dArrayThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgbaFloatImage2DArrayInternal(Image2DArrayWriteOnly image) {
                assertEquals(912L, image.handle());
                assertEquals(2, image.layers());
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f};
            }
        };

        float[] rgba = backend.readRgbaFloatImage2DArray(Image2DArrayWriteOnly.borrowed(912L, 1, 1, 2));

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f}, rgba);
    }

    @Test
    void readsHighLevelMipmappedFloatImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgbaFloatImageMipmappedInternal(Image2DMipmappedWriteOnly image, int mipLevel) {
                assertEquals(10123L, image.handle());
                assertEquals(2, image.mipLevels());
                assertEquals(1, mipLevel);
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f};
            }
        };

        float[] rgba = backend.readRgbaFloatImageMipmapped(Image2DMipmappedWriteOnly.borrowed(10123L, 4, 2, 2), 1);

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f}, rgba);
    }

    @Test
    void readsHighLevelMipmappedUIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaUIntImageMipmappedInternal(Image2DMipmappedWriteOnly image, int mipLevel) {
                assertEquals(10124L, image.handle());
                assertEquals(2, image.mipLevels());
                assertEquals(1, mipLevel);
                return new int[]{9, 10, 11, 12};
            }
        };

        int[] rgba = backend.readRgbaUIntImageMipmapped(Image2DMipmappedWriteOnly.borrowed(10124L, 4, 2, 2), 1);

        assertArrayEquals(new int[]{9, 10, 11, 12}, rgba);
    }
}
