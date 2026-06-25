package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.Float2;
import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;
import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLAttributes;
import net.sixik.ga_utils.javatogpu.benchmark.BenchmarkHarness;
import net.sixik.ga_utils.javatogpu.benchmark.BenchmarkResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClRuntimeBenchmarkTest {

    @Test
    void benchmarksStructBufferMarshalling() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "inline://benchmark/struct-buffer.cl",
                "__kernel void kernel(__global BenchmarkStruct* input, __global BenchmarkStruct* output) { }",
                List.of(
                        new GpuKernelParameterDescriptor("input", "sample.BenchmarkStruct[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "sample.BenchmarkStruct[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        BenchmarkStruct[] input = new BenchmarkStruct[64];
        BenchmarkStruct[] output = new BenchmarkStruct[64];
        for (int i = 0; i < input.length; i++) {
            input[i] = new BenchmarkStruct(new BenchmarkInner(i, i + 0.25f), i * 0.5f, i);
            output[i] = new BenchmarkStruct(new BenchmarkInner(), 0.0f, 0);
        }

        OpenClExecutionPlan sample = OpenClExecutionPlanner.plan(OpenClArgumentMarshaller.marshall(descriptor, new Object[]{input, output}));
        assertEquals(2, sample.bufferBindings().size());

        BenchmarkResult result = BenchmarkHarness.measure(
                "runtime.struct-buffer.marshalling",
                5,
                25,
                () -> checksum(OpenClExecutionPlanner.plan(OpenClArgumentMarshaller.marshall(descriptor, new Object[]{input, output})))
        );

        assertTrue(result.averageNanos() > 0L);
        assertTrue(result.checksum() > 0L);
    }

    @Test
    void benchmarksLargeStructBufferMarshalling() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "inline://benchmark/large-struct-buffer.cl",
                "__kernel void kernel(__global BenchmarkStruct* input, __global BenchmarkStruct* output) { }",
                List.of(
                        new GpuKernelParameterDescriptor("input", "sample.BenchmarkStruct[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "sample.BenchmarkStruct[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        BenchmarkStruct[] input = new BenchmarkStruct[4096];
        BenchmarkStruct[] output = new BenchmarkStruct[4096];
        for (int i = 0; i < input.length; i++) {
            input[i] = new BenchmarkStruct(new BenchmarkInner(i, i + 0.25f), i * 0.5f, i);
            output[i] = new BenchmarkStruct(new BenchmarkInner(), 0.0f, 0);
        }

        OpenClExecutionPlan sample = OpenClExecutionPlanner.plan(OpenClArgumentMarshaller.marshall(descriptor, new Object[]{input, output}));
        assertEquals(2, sample.bufferBindings().size());

        BenchmarkResult result = BenchmarkHarness.measure(
                "runtime.large-struct-buffer.marshalling",
                3,
                10,
                () -> checksum(OpenClExecutionPlanner.plan(OpenClArgumentMarshaller.marshall(descriptor, new Object[]{input, output})))
        );

        assertTrue(result.averageNanos() > 0L);
        assertTrue(result.checksum() > 0L);
    }

    @Test
    void benchmarksVectorArrayMarshalling() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "inline://benchmark/vector-array.cl",
                "__kernel void kernel(__global float2* input, __global float2* output) { }",
                List.of(
                        new GpuKernelParameterDescriptor("input", "Float2[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "Float2[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        Float2[] input = new Float2[128];
        Float2[] output = new Float2[128];
        for (int i = 0; i < input.length; i++) {
            input[i] = new Float2(i, i + 1.0f);
            output[i] = new Float2();
        }

        OpenClExecutionPlan sample = OpenClExecutionPlanner.plan(OpenClArgumentMarshaller.marshall(descriptor, new Object[]{input, output}));
        assertEquals(2, sample.bufferBindings().size());

        BenchmarkResult result = BenchmarkHarness.measure(
                "runtime.vector-array.marshalling",
                5,
                25,
                () -> checksum(OpenClExecutionPlanner.plan(OpenClArgumentMarshaller.marshall(descriptor, new Object[]{input, output})))
        );

        assertTrue(result.averageNanos() > 0L);
        assertTrue(result.checksum() > 0L);
    }

    @Test
    void benchmarksImageWorkflowMarshalling() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "inline://benchmark/image-workflow.cl",
                "__kernel void kernel(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) { }",
                List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        Image2DReadOnly inputImage = Image2DReadOnly.borrowed(101L, 16, 16);
        Image2DWriteOnly outputImage = Image2DWriteOnly.borrowed(202L, 16, 16);
        Sampler sampler = Sampler.borrowed(303L);
        int[] output = new int[16];

        OpenClExecutionPlan sample = OpenClExecutionPlanner.plan(OpenClArgumentMarshaller.marshall(descriptor, new Object[]{inputImage, outputImage, sampler, output}));
        assertEquals(1, sample.bufferBindings().size());
        assertEquals(3, sample.scalarBindings().size());

        BenchmarkResult result = BenchmarkHarness.measure(
                "runtime.image-workflow.marshalling",
                5,
                25,
                () -> checksum(OpenClExecutionPlanner.plan(OpenClArgumentMarshaller.marshall(descriptor, new Object[]{inputImage, outputImage, sampler, output})))
        );

        assertTrue(result.averageNanos() > 0L);
        assertTrue(result.checksum() > 0L);
    }

    @Test
    void benchmarksWarmInvokePathWithSharedBackendState() {
        OpenClGpuRuntimeBackend backend = fakeBackend();
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "inline://benchmark/warm-invoke.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
        int[] output = new int[]{0};

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{output}));
        backend.resetStatistics();

        BenchmarkResult result = BenchmarkHarness.measure(
                "runtime.warm-invoke.fake-opencl",
                10,
                100,
                () -> {
                    backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{output}));
                    return backend.statistics().compileCacheHitCount() + backend.cacheSize();
                }
        );

        OpenClRuntimeStatistics statistics = backend.statistics();
        assertEquals(110L, statistics.invocationCount());
        assertEquals(0L, statistics.compileCount());
        assertEquals(110L, statistics.compileCacheHitCount());
        assertEquals(0L, statistics.sessionCreationCount());
        assertEquals(0L, statistics.deviceBufferCreationCount());
        assertTrue(result.averageNanos() > 0L);
        assertTrue(result.checksum() > 0L);
    }

    @Test
    void benchmarksColdCompileVersusWarmInvokePath() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "inline://benchmark/cold-vs-warm.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );

        BenchmarkResult coldResult = BenchmarkHarness.measure(
                "runtime.cold-invoke.fake-opencl",
                3,
                20,
                () -> {
                    OpenClGpuRuntimeBackend backend = fakeBackend();
                    int[] output = new int[]{0};
                    try {
                        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{output}));
                        OpenClRuntimeStatistics statistics = backend.statistics();
                        return statistics.compileCount() + statistics.deviceBufferCreationCount();
                    } finally {
                        backend.close();
                    }
                }
        );

        OpenClGpuRuntimeBackend warmBackend = fakeBackend();
        int[] warmOutput = new int[]{0};
        warmBackend.invoke(new GpuKernelInvocation(descriptor, new Object[]{warmOutput}));
        warmBackend.resetStatistics();

        BenchmarkResult warmResult = BenchmarkHarness.measure(
                "runtime.warm-invoke.fake-opencl.after-cold-compile",
                5,
                50,
                () -> {
                    warmBackend.invoke(new GpuKernelInvocation(descriptor, new Object[]{warmOutput}));
                    OpenClRuntimeStatistics statistics = warmBackend.statistics();
                    return statistics.compileCacheHitCount() + warmBackend.cacheSize();
                }
        );

        OpenClRuntimeStatistics warmStatistics = warmBackend.statistics();
        assertEquals(55L, warmStatistics.invocationCount());
        assertEquals(0L, warmStatistics.compileCount());
        assertEquals(55L, warmStatistics.compileCacheHitCount());
        assertTrue(coldResult.averageNanos() > 0L);
        assertTrue(coldResult.checksum() > 0L);
        assertTrue(warmResult.averageNanos() > 0L);
        assertTrue(warmResult.checksum() > 0L);
    }

    private static long checksum(OpenClExecutionPlan plan) {
        return plan.argumentBindings().size()
                + plan.bufferBindings().size()
                + plan.scalarBindings().size()
                + plan.localBindings().size();
    }

    private static OpenClGpuRuntimeBackend fakeBackend() {
        return new OpenClGpuRuntimeBackend(OpenClGpuRuntimeBackend.CacheMode.INSTANCE) {
            private int compileSequence;

            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                compileSequence++;
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:" + compileSequence);
            }

            @Override
            protected Object createDeviceBuffer(OpenClBufferBinding binding) {
                return new Object();
            }

            @Override
            protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                // no-op for warm invoke benchmark
            }

            @Override
            protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
                // no-op for warm invoke benchmark
            }

            @Override
            protected void bindScalarArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClScalarBinding binding) {
                // no-op for warm invoke benchmark
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, long globalWorkSize) {
                // no-op for warm invoke benchmark
            }

            @Override
            protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                // no-op for warm invoke benchmark
            }
        };
    }

    @GPUStruct
    static final class BenchmarkInner {
        int x;
        float y;

        BenchmarkInner() {
        }

        BenchmarkInner(int x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    @GPUStruct
    @OpenCLAttributes({"packed"})
    static final class BenchmarkStruct {
        BenchmarkInner inner;
        @OpenCLAttributes({"aligned(8)"})
        float bias;
        int count;

        BenchmarkStruct(BenchmarkInner inner, float bias, int count) {
            this.inner = inner;
            this.bias = bias;
            this.count = count;
        }
    }
}
