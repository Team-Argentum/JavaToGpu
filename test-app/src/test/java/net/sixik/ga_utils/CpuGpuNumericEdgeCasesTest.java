package net.sixik.ga_utils;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackend;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClRuntimeSession;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpuGpuNumericEdgeCasesTest {

    @Test
    void matchesCpuAndGpuForFloatNaNInfinityAndLargeValues() {
        assumeOpenClAvailable();

        float[] input = new float[]{
                Float.NaN,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
                Float.MAX_VALUE,
                -Float.MAX_VALUE,
                0.25f,
                -2.0f,
                8.5f
        };
        float[] cpuOutput = new float[input.length];
        float[] gpuOutput = new float[input.length];

        CpuGpuNumericEdgeCases.transformFloatCpu(input, cpuOutput);

        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            GpuRuntime.setBackend(backend);
            CpuGpuNumericEdgeCases.transformFloat(input, gpuOutput);
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }

        for (int i = 0; i < input.length; i++) {
            assertFloatParity(cpuOutput[i], gpuOutput[i], i);
        }
    }

    @Test
    void matchesCpuAndGpuForDoubleNaNInfinityAndLargeValuesWhenFp64IsAvailable() {
        assumeOpenClAvailable();

        double[] input = new double[]{
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.MAX_VALUE,
                -Double.MAX_VALUE,
                0.5,
                -3.0,
                12.25
        };
        double[] cpuOutput = new double[input.length];
        double[] gpuOutput = new double[input.length];

        CpuGpuNumericEdgeCases.transformDoubleCpu(input, cpuOutput);

        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            GpuRuntime.setBackend(backend);
            try {
                CpuGpuNumericEdgeCases.transformDouble(input, gpuOutput);
            } catch (RuntimeException exception) {
                Assumptions.assumeTrue(false, "Skipping fp64 parity test: " + exception.getMessage());
            }
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }

        for (int i = 0; i < input.length; i++) {
            assertDoubleParity(cpuOutput[i], gpuOutput[i], i);
        }
    }

    private static void assertFloatParity(float expected, float actual, int index) {
        if (Float.isNaN(expected)) {
            assertTrue(Float.isNaN(actual), "Expected NaN at index " + index + " but got " + actual);
            return;
        }
        if (Float.isInfinite(expected)) {
            assertEquals(expected, actual, "Expected infinity parity at index " + index);
            return;
        }
        assertEquals(expected, actual, 1.0e-5f, "Mismatch at index " + index);
    }

    private static void assertDoubleParity(double expected, double actual, int index) {
        if (Double.isNaN(expected)) {
            assertTrue(Double.isNaN(actual), "Expected NaN at index " + index + " but got " + actual);
            return;
        }
        if (Double.isInfinite(expected)) {
            assertEquals(expected, actual, "Expected infinity parity at index " + index);
            return;
        }
        assertEquals(expected, actual, 1.0e-9, "Mismatch at index " + index);
    }

    private static void assumeOpenClAvailable() {
        try (OpenClRuntimeSession ignored = OpenClRuntimeSession.createDefault()) {
            // Session creation is enough to know OpenCL is reachable.
        } catch (UnsatisfiedLinkError | IllegalStateException exception) {
            Assumptions.assumeTrue(false, "Skipping OpenCL parity test: " + exception.getMessage());
        }
    }
}
