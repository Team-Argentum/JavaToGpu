package net.sixik.ga_utils;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackend;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClRuntimeSession;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CpuGpuParitySamplesTest {

    @Test
    void matchesCpuAndGpuForPtrHelperLoopSwitchAndConstants() {
        assumeOpenClAvailable();

        float[] input = new float[]{0.5f, 1.5f, 2.0f, 3.0f, 7.25f, 11.5f};
        float[] cpuOutput = new float[input.length];
        float[] gpuOutput = new float[input.length];

        CpuGpuParitySamples.transformCpu(input, cpuOutput);

        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            GpuRuntime.setBackend(backend);
            CpuGpuParitySamples.transform(input, gpuOutput);
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }

        for (int i = 0; i < input.length; i++) {
            assertEquals(cpuOutput[i], gpuOutput[i], 1.0e-5f, "Mismatch at index " + i);
        }
    }

    private static void assumeOpenClAvailable() {
        try (OpenClRuntimeSession ignored = OpenClRuntimeSession.createDefault()) {
            // Session creation is enough to know OpenCL is reachable.
        } catch (UnsatisfiedLinkError | IllegalStateException exception) {
            Assumptions.assumeTrue(false, "Skipping OpenCL parity test: " + exception.getMessage());
        }
    }
}
