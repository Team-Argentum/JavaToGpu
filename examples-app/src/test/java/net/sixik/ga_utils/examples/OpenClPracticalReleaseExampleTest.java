package net.sixik.ga_utils.examples;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClPracticalReleaseExampleTest {

    @Test
    void rendersCuratedOpenClWalkthroughSectionsAndReviewFiles() {
        Path cacheDirectory = Path.of("build", "test-practical-opencl-cache");

        String output = OpenClPracticalReleaseExample.renderPracticalOpenClWalkthrough(
                cacheDirectory,
                "Combined backend/device selection:\nselected backend OPENCL",
                "Method test probe example\nreference comparison passed",
                "Real OpenCL method-test probe evidence selection example\nMethod test probe evidence selection: selected",
                "Practical workload smoke: completed\n- vectorExample: passed\n- return-value convenience: passed\n- structBufferExample: passed\n"
                        + "- packedBlobViewExample: passed\n- imageExample: passed via OpenClImageWorkflow"
        );

        assertTrue(output.contains("Practical OpenCL release walkthrough"), output);
        assertTrue(output.contains("1. Backend/device selection"), output);
        assertTrue(output.contains("2. @GPUTest CPU-reference preflight"), output);
        assertTrue(output.contains("3. Real OpenCL evidence warm-up and cache-only placement"), output);
        assertTrue(output.contains("4. Launch configuration shapes"), output);
        assertTrue(output.contains("5. Practical workload smoke"), output);
        assertTrue(output.contains("6. Image helper workflow"), output);
        assertTrue(output.contains("7. Optimizer artifact review"), output);
        assertTrue(output.contains("selected backend OPENCL"), output);
        assertTrue(output.contains("reference comparison passed"), output);
        assertTrue(output.contains("Method test probe evidence selection: selected"), output);
        assertTrue(output.contains("2D global=16x8, local=4x2"), output);
        assertTrue(output.contains("vectorExample: passed"), output);
        assertTrue(output.contains("return-value convenience: passed"), output);
        assertTrue(output.contains("structBufferExample: passed"), output);
        assertTrue(output.contains("packedBlobViewExample: passed"), output);
        assertTrue(output.contains("imageExample: passed"), output);
        assertTrue(output.contains("OpenClImageWorkflow.rgbaIntToFloat2D"), output);
        assertTrue(output.contains("readOutputRgbaFloat"), output);
        assertTrue(output.contains("packed/root-blob smoke"), output);
        assertTrue(output.contains("generated return-first launcher convenience"), output);
        assertTrue(output.contains("runOptimizationJournalExample"), output);
        assertTrue(output.contains("original.backend.opencl-c -> optimized.backend.opencl-c"), output);
        assertTrue(output.contains("backend.opencl-c (review-only defaults keep the original selected)"), output);
        assertTrue(output.contains("runtime-ir-optimizer-evidence.properties"), output);
        assertTrue(output.contains("mad("), output);
        assertTrue(output.contains("clampMaterialization.status"), output);
        assertTrue(output.contains("runtime-lifecycle.jsonl"), output);
        assertTrue(output.contains("opencl-evidence-selection.trace"), output);
    }

    @Test
    void rendersLaunchConfigurationGuide() {
        String guide = OpenClPracticalReleaseExample.renderLaunchConfigurationGuide();

        assertTrue(guide.contains("1D array kernel: 1D global=4, local=auto"), guide);
        assertTrue(guide.contains("2D tiled kernel: 2D global=16x8, local=4x2"), guide);
        assertTrue(guide.contains("3D volume kernel: 3D global=16x8x4, local=auto"), guide);
        assertTrue(guide.contains("localX/localY must both be zero or both be > 0"), guide);
    }

    @Test
    void rendersImageHelperGuide() {
        String guide = OpenClPracticalReleaseExample.renderImageHelperGuide();

        assertTrue(guide.contains("GpuRuntimeFeature.IMAGES"), guide);
        assertTrue(guide.contains("OpenClImageWorkflow.rgbaIntToFloat2D"), guide);
        assertTrue(guide.contains("images.input()"), guide);
        assertTrue(guide.contains("images.output()"), guide);
        assertTrue(guide.contains("images.sampler()"), guide);
        assertTrue(guide.contains("images.readOutputRgbaFloat()"), guide);
        assertTrue(guide.contains("try-with-resources"), guide);
    }

    @Test
    void rendersOptimizerArtifactReviewGuide() {
        String guide = OpenClPracticalReleaseExample.renderOptimizerArtifactReviewGuide();

        assertTrue(guide.contains("runOptimizationJournalExample"), guide);
        assertTrue(guide.contains("original.backend.opencl-c -> optimized.backend.opencl-c"), guide);
        assertTrue(guide.contains("backend.opencl-c"), guide);
        assertTrue(guide.contains("runtime-ir-handoff.properties"), guide);
        assertTrue(guide.contains("runtime-ir-optimizer-evidence.properties"), guide);
        assertTrue(guide.contains("mad("), guide);
        assertTrue(guide.contains("clamp("), guide);
        assertTrue(guide.contains("step("), guide);
        assertTrue(guide.contains("mix("), guide);
        assertTrue(guide.contains("vload4("), guide);
        assertTrue(guide.contains("runtimeEquivalenceReview.familySummary"), guide);
    }

    @Test
    void resolvesOptionalCacheDirectoryArgument() {
        assertEquals(
                Path.of("custom-cache"),
                OpenClPracticalReleaseExample.resolveCacheDirectory(new String[]{"custom-cache"})
        );
        assertTrue(OpenClPracticalReleaseExample.resolveCacheDirectory(new String[0]).endsWith("method-test-evidence"));
    }
}
