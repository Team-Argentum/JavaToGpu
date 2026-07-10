package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClKernelLaunchAdvisoryTest {

    @Test
    void reportsAlignedExplicitLocalSize() {
        OpenClKernelLaunchAdvisory advisory = OpenClKernelLaunchAdvisory.evaluate(
                compiledKernel(256L, 32L),
                GpuExecutionConfig.twoDimensional(64L, 64L, 8L, 4L)
        );

        assertEquals("aligned", advisory.status());
        assertEquals(32L, advisory.requestedLocalWorkGroupSize());
        assertEquals("8x4", advisory.requestedLocalWorkGroupShape());
        assertTrue(advisory.comparisonPerformed());
        assertTrue(advisory.preferredMultipleMatched());
    }

    @Test
    void reportsDriverSelectedAndUnavailableComparisons() {
        OpenClKernelLaunchAdvisory driverSelected = OpenClKernelLaunchAdvisory.evaluate(
                compiledKernel(256L, 32L),
                GpuExecutionConfig.oneDimensional(64L)
        );
        OpenClKernelLaunchAdvisory unavailable = OpenClKernelLaunchAdvisory.evaluate(
                compiledKernel(256L, OpenClKernelResourceInfo.UNKNOWN),
                GpuExecutionConfig.oneDimensional(64L, 48L)
        );

        assertEquals("driver-selected", driverSelected.status());
        assertFalse(driverSelected.explicitLocalSize());
        assertFalse(driverSelected.comparisonPerformed());
        assertEquals("unavailable", unavailable.status());
        assertTrue(unavailable.explicitLocalSize());
        assertFalse(unavailable.comparisonPerformed());
        assertFalse(unavailable.preferredMultipleMatched());
    }

    @Test
    void serializesNonPreferredMultipleAsNonBlockingDiagnostic() {
        OpenClKernelLaunchAdvisory advisory = OpenClKernelLaunchAdvisory.evaluate(
                compiledKernel(256L, 32L),
                GpuExecutionConfig.oneDimensional(64L, 48L)
        );

        String properties = advisory.toProperties();
        assertEquals("non-preferred-multiple", advisory.status());
        assertTrue(advisory.comparisonPerformed());
        assertFalse(advisory.preferredMultipleMatched());
        assertTrue(properties.contains("blocking=false"));
        assertTrue(properties.contains("requestedLocalWorkGroupSize=48"));
        assertTrue(properties.contains("preferredWorkGroupSizeMultiple=32"));
        assertTrue(properties.contains("execution remains allowed"));
    }

    private static OpenClCompiledKernel compiledKernel(long maxWorkGroupSize, long preferredMultiple) {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/test/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of()
        );
        return new OpenClCompiledKernel(descriptor, "compiled:launch-advisory")
                .withKernelResourceInfo(new OpenClKernelResourceInfo(
                        maxWorkGroupSize,
                        preferredMultiple,
                        0L,
                        0L
                ));
    }
}
