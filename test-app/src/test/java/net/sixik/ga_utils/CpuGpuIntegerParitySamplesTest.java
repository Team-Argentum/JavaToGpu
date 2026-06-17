package net.sixik.ga_utils;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackend;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClRuntimeSession;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CpuGpuIntegerParitySamplesTest {

    @Test
    void matchesCpuAndGpuForIntCompoundAssignmentsBitwiseAndSwitch() {
        assumeOpenClAvailable();

        int[] input = new int[]{1, 2, 7, 16, -3, 31};
        int[] cpuOutput = new int[input.length];
        int[] gpuOutput = new int[input.length];

        CpuGpuIntegerParitySamples.transformIntCpu(input, cpuOutput);

        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            GpuRuntime.setBackend(backend);
            CpuGpuIntegerParitySamples.transformInt(input, gpuOutput);
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }

        assertArrayEquals(cpuOutput, gpuOutput);
    }

    @Test
    void matchesCpuAndGpuForLongCompoundAssignmentsBitwiseAndSwitch() {
        assumeOpenClAvailable();

        long[] input = new long[]{1L, 2L, 7L, 16L, -3L, 31L, 1024L};
        long[] cpuOutput = new long[input.length];
        long[] gpuOutput = new long[input.length];

        CpuGpuIntegerParitySamples.transformLongCpu(input, cpuOutput);

        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            GpuRuntime.setBackend(backend);
            CpuGpuIntegerParitySamples.transformLong(input, gpuOutput);
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }

        assertArrayEquals(cpuOutput, gpuOutput);
    }

    private static void assumeOpenClAvailable() {
        try (OpenClRuntimeSession ignored = OpenClRuntimeSession.createDefault()) {
            // Session creation is enough to know OpenCL is reachable.
        } catch (UnsatisfiedLinkError | IllegalStateException exception) {
            Assumptions.assumeTrue(false, "Skipping OpenCL parity test: " + exception.getMessage());
        }
    }
}
