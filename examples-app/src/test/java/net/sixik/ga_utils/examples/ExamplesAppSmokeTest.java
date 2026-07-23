package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.types.floats.Float2;
import net.sixik.ga_utils.javatogpu.api.types.integers.UInt;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeFeature;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamplesAppSmokeTest {

    @Test
    void runsCoreGpuShowcaseExamplesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        float[] floatInput = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
        float[] basicOutput = new float[floatInput.length];
        float[] vectorOutput = new float[floatInput.length];
        float[] nativeOutput = new float[floatInput.length];
        float[] libraryOutput = new float[floatInput.length];
        float[] attributeOutput = new float[floatInput.length];

        int[] controlInput = new int[]{1, 2, 3, 4};
        int[] controlOutput = new int[controlInput.length];
        int[] doWhileOutput = new int[controlInput.length];
        int[] unsignedScalarOutput = new int[floatInput.length];

        double[] doubleInput = new double[]{1.0, 2.0, 3.0, 4.0};
        double[] structOutput = new double[doubleInput.length];

        Vec2[] structBufferInput = new Vec2[]{
                new Vec2(1.0, 2.0),
                new Vec2(3.0, 4.0),
                new Vec2(5.0, 6.0),
                new Vec2(7.0, 8.0)
        };
        Vec2[] structBufferOutput = new Vec2[]{new Vec2(), new Vec2(), new Vec2(), new Vec2()};

        try (GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {
            GpuShowcase.basicMath(floatInput, basicOutput);
            GpuShowcase.controlFlowExample(controlInput, controlOutput);
            GpuShowcase.doWhileExample(controlInput, doWhileOutput);
            GpuShowcase.vectorExample(new Float2(1.0f, 0.5f), floatInput, vectorOutput);
            GpuShowcase.nativeHelperExample(floatInput, nativeOutput);
            GpuShowcase.libraryHelperExample(floatInput, libraryOutput);
            GpuShowcase.attributeExample(floatInput, attributeOutput);
            GpuShowcase.structExample(new SampleData(0.75, 3), doubleInput, structOutput);
            GpuShowcase.structBufferExample(structBufferInput, structBufferOutput);
            GpuShowcase.unsignedScalarExample(new UInt(41), unsignedScalarOutput);
        }

        assertArrayEquals(new float[]{0.96036774f, 1.7273244f, 2.2852800f, 2.8107994f}, basicOutput, 1.0e-5f);
        assertArrayEquals(new int[]{5, 7, 9, 11}, controlOutput);
        assertArrayEquals(new int[]{1, 2, 3, 4}, doWhileOutput);
        assertArrayEquals(new float[]{4.5f, 7.5f, 10.5f, 13.5f}, vectorOutput, 1.0e-5f);
        assertArrayEquals(new float[]{6.0f, 7.0f, 8.0f, 9.0f}, nativeOutput, 1.0e-5f);
        assertArrayEquals(new float[]{1.5f, 3.0f, 5.5f, 9.0f}, libraryOutput, 1.0e-5f);
        assertArrayEquals(new float[]{2.0f, 3.0f, 4.0f, 5.0f}, attributeOutput, 1.0e-5f);
        assertArrayEquals(new double[]{7.25, 11.25, 15.25, 19.25}, structOutput, 1.0e-9);
        assertArrayEquals(new double[]{2.0, 4.0, 6.0, 8.0}, new double[]{
                structBufferOutput[0].x,
                structBufferOutput[1].x,
                structBufferOutput[2].x,
                structBufferOutput[3].x
        }, 1.0e-9);
        assertArrayEquals(new double[]{4.0, 6.0, 8.0, 10.0}, new double[]{
                structBufferOutput[0].y,
                structBufferOutput[1].y,
                structBufferOutput[2].y,
                structBufferOutput[3].y
        }, 1.0e-9);
        assertArrayEquals(new int[]{17, 17, 17, 17}, unsignedScalarOutput);
    }

    @Test
    void runsExamplesMainWhenFullShowcaseCapabilitiesAreAvailable() {
        assumeFullExamplesMainCapabilitySet();

        PrintStream originalOut = System.out;
        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(outputBuffer, true, StandardCharsets.UTF_8));
            ExamplesMain.main(new String[0]);
        } finally {
            System.setOut(originalOut);
        }

        String output = outputBuffer.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Running basic example..."));
        assertTrue(output.contains("Running image example..."));
        assertTrue(output.contains("Running 3D unsigned image example..."));
        assertTrue(output.contains("Running ASM compiler example..."));
        assertTrue(output.contains("Generated OpenCL from ASM:"));
        assertTrue(!output.contains("GPU execution failed:"), output);
    }

    private static void assumeOpenClAvailable() {
        GpuRuntimeBackendReport report;
        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            report = backend.describeCapabilities();
        } catch (RuntimeException exception) {
            Assumptions.assumeTrue(false, "Skipping examples-app smoke test: " + exception.getMessage());
            return;
        }

        Assumptions.assumeTrue(report.available(), "Skipping examples-app smoke test: " + report.detail());
    }

    private static void assumeFullExamplesMainCapabilitySet() {
        GpuRuntimeBackendReport report;
        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            report = backend.describeCapabilities();
        } catch (RuntimeException exception) {
            Assumptions.assumeTrue(false, "Skipping full examples-app smoke test: " + exception.getMessage());
            return;
        }

        Assumptions.assumeTrue(report.available(), "Skipping full examples-app smoke test: " + report.detail());
        Assumptions.assumeTrue(report.supports(GpuRuntimeFeature.DOUBLE_PRECISION), "Skipping full examples-app smoke test: no fp64 support");
        Assumptions.assumeTrue(report.supports(GpuRuntimeFeature.IMAGES), "Skipping full examples-app smoke test: no image support");
        Assumptions.assumeTrue(report.supports(GpuRuntimeFeature.IMAGE3D_WRITES), "Skipping full examples-app smoke test: no 3D image write support");
    }
}
