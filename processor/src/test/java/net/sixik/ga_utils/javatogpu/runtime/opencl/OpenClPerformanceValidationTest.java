package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenClPerformanceValidationTest {

    @Test
    void capturesColdAndWarmCachePathWithinSingleBackend() {
        OpenClGpuRuntimeBackend backend = fakeBackend(OpenClGpuRuntimeBackend.CacheMode.INSTANCE);
        int[] output = new int[]{0};

        backend.invoke(new GpuKernelInvocation(sampleDescriptor(), new Object[]{output}));
        backend.invoke(new GpuKernelInvocation(sampleDescriptor(), new Object[]{output}));

        OpenClRuntimeStatistics statistics = backend.statistics();
        assertEquals(2L, statistics.invocationCount());
        assertEquals(1L, statistics.compileCount());
        assertEquals(1L, statistics.compileCacheHitCount());
        assertEquals(0L, statistics.sessionCreationCount());
        assertEquals(1L, statistics.deviceBufferCreationCount());
    }

    @Test
    void sharedCacheTurnsSecondBackendIntoWarmCompilePath() {
        OpenClGpuRuntimeBackend.shutdownSharedCache();
        try {
            OpenClGpuRuntimeBackend firstBackend = fakeBackend(OpenClGpuRuntimeBackend.CacheMode.SHARED);
            firstBackend.invoke(new GpuKernelInvocation(sampleDescriptor(), new Object[]{new int[]{0}}));

            OpenClRuntimeStatistics firstStatistics = firstBackend.statistics();
            assertEquals(1L, firstStatistics.invocationCount());
            assertEquals(1L, firstStatistics.compileCount());
            assertEquals(0L, firstStatistics.compileCacheHitCount());

            OpenClGpuRuntimeBackend secondBackend = fakeBackend(OpenClGpuRuntimeBackend.CacheMode.SHARED);
            secondBackend.invoke(new GpuKernelInvocation(sampleDescriptor(), new Object[]{new int[]{0}}));

            OpenClRuntimeStatistics secondStatistics = secondBackend.statistics();
            assertEquals(1L, secondStatistics.invocationCount());
            assertEquals(0L, secondStatistics.compileCount());
            assertEquals(1L, secondStatistics.compileCacheHitCount());
        } finally {
            OpenClGpuRuntimeBackend.shutdownSharedCache();
        }
    }

    @Test
    void resetStatisticsStartsNewMeasurementWindowWithoutClearingCaches() {
        OpenClGpuRuntimeBackend backend = fakeBackend(OpenClGpuRuntimeBackend.CacheMode.INSTANCE);
        int[] output = new int[]{0};

        backend.invoke(new GpuKernelInvocation(sampleDescriptor(), new Object[]{output}));
        backend.invoke(new GpuKernelInvocation(sampleDescriptor(), new Object[]{output}));
        backend.resetStatistics();

        OpenClRuntimeStatistics reset = backend.statistics();
        assertEquals(0L, reset.invocationCount());
        assertEquals(0L, reset.compileCount());
        assertEquals(0L, reset.compileCacheHitCount());
        assertEquals(0L, reset.sessionCreationCount());
        assertEquals(0L, reset.deviceBufferCreationCount());

        backend.invoke(new GpuKernelInvocation(sampleDescriptor(), new Object[]{output}));

        OpenClRuntimeStatistics afterWarmInvoke = backend.statistics();
        assertEquals(1L, afterWarmInvoke.invocationCount());
        assertEquals(0L, afterWarmInvoke.compileCount());
        assertEquals(1L, afterWarmInvoke.compileCacheHitCount());
        assertEquals(0L, afterWarmInvoke.deviceBufferCreationCount());
    }

    @Test
    void repeatedWarmInvocationsReuseCompileAndBufferCaches() {
        OpenClGpuRuntimeBackend backend = fakeBackend(OpenClGpuRuntimeBackend.CacheMode.INSTANCE);
        int[] output = new int[]{0};

        for (int iteration = 0; iteration < 100; iteration++) {
            backend.invoke(new GpuKernelInvocation(sampleDescriptor(), new Object[]{output}));
        }

        OpenClRuntimeStatistics statistics = backend.statistics();
        assertEquals(100L, statistics.invocationCount());
        assertEquals(1L, statistics.compileCount());
        assertEquals(99L, statistics.compileCacheHitCount());
        assertEquals(1L, statistics.deviceBufferCreationCount());
    }

    @Test
    void repeatedLargeArrayInvocationsStillReuseCompileAndBufferCaches() {
        OpenClGpuRuntimeBackend backend = fakeBackend(OpenClGpuRuntimeBackend.CacheMode.INSTANCE);
        int[] output = new int[4096];

        for (int iteration = 0; iteration < 250; iteration++) {
            backend.invoke(new GpuKernelInvocation(sampleDescriptor(), new Object[]{output}));
        }

        OpenClRuntimeStatistics statistics = backend.statistics();
        assertEquals(250L, statistics.invocationCount());
        assertEquals(1L, statistics.compileCount());
        assertEquals(249L, statistics.compileCacheHitCount());
        assertEquals(1L, statistics.deviceBufferCreationCount());
    }

    @Test
    void multipleKernelDescriptorsTrackColdAndWarmPathsIndependently() {
        OpenClGpuRuntimeBackend backend = fakeBackend(OpenClGpuRuntimeBackend.CacheMode.INSTANCE);
        int[] firstOutput = new int[]{0};
        int[] secondOutput = new int[]{0};

        backend.invoke(new GpuKernelInvocation(sampleDescriptor(), new Object[]{firstOutput}));
        backend.invoke(new GpuKernelInvocation(alternativeDescriptor(), new Object[]{secondOutput}));

        for (int iteration = 0; iteration < 50; iteration++) {
            backend.invoke(new GpuKernelInvocation(sampleDescriptor(), new Object[]{firstOutput}));
            backend.invoke(new GpuKernelInvocation(alternativeDescriptor(), new Object[]{secondOutput}));
        }

        OpenClRuntimeStatistics statistics = backend.statistics();
        assertEquals(102L, statistics.invocationCount());
        assertEquals(2L, statistics.compileCount());
        assertEquals(100L, statistics.compileCacheHitCount());
        assertEquals(2L, statistics.deviceBufferCreationCount());
    }

    private static GpuKernelDescriptor sampleDescriptor() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/performance/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    private static GpuKernelDescriptor alternativeDescriptor() {
        return new GpuKernelDescriptor(
                "kernel_alt",
                "javatogpu/performance/Demo/kernel-alt.cl",
                "__kernel void kernel_alt(__global int* output) { output[0] = 2; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    private static OpenClGpuRuntimeBackend fakeBackend(OpenClGpuRuntimeBackend.CacheMode cacheMode) {
        return new OpenClGpuRuntimeBackend(cacheMode) {
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
                // no-op for performance validation tests
            }

            @Override
            protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
                // no-op for performance validation tests
            }

            @Override
            protected void bindScalarArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClScalarBinding binding) {
                // no-op for performance validation tests
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, long globalWorkSize) {
                // no-op for performance validation tests
            }

            @Override
            protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                // no-op for performance validation tests
            }
        };
    }
}
