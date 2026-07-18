package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.Float2;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuGeneratedLauncherInvoker;
import net.sixik.ga_utils.javatogpu.runtime.GpuGeneratedLauncherReturnValueConvenienceReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeFeature;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClImageWorkflow;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Curated OpenCL walkthrough for the common alpha-user flow.
 */
public final class OpenClPracticalReleaseExample {

    private static final Path DEFAULT_CACHE_DIRECTORY = Path.of(
            "build",
            "practical-opencl-release",
            "method-test-evidence"
    );

    private OpenClPracticalReleaseExample() {
    }

    public static void main(String[] args) {
        Path cacheDirectory = resolveCacheDirectory(args);
        System.out.println(renderPracticalOpenClWalkthrough(cacheDirectory));
    }

    static String renderPracticalOpenClWalkthrough(Path cacheDirectory) {
        Path resolvedCacheDirectory = cacheDirectory == null ? DEFAULT_CACHE_DIRECTORY : cacheDirectory;
        return renderPracticalOpenClWalkthrough(
                resolvedCacheDirectory,
                BackendSelectionExample.renderStandardBackendDeviceSelectionAttempt(),
                MethodTestProbeExample.renderProbeReadiness(),
                OpenClMethodTestProbeEvidenceSelectionExample.renderRealOpenClEvidenceSelection(resolvedCacheDirectory),
                renderPracticalWorkloadSmoke()
        );
    }

    static String renderPracticalOpenClWalkthrough(
            Path cacheDirectory,
            String backendDeviceSelection,
            String methodTestPreflight,
            String openClEvidenceSelection,
            String practicalWorkloadSmoke
    ) {
        Path resolvedCacheDirectory = cacheDirectory == null ? DEFAULT_CACHE_DIRECTORY : cacheDirectory;
        StringBuilder builder = new StringBuilder();
        builder.append("Practical OpenCL release walkthrough").append(System.lineSeparator());
        builder.append("This example ties together backend/device explanation, @GPUTest preflight, real OpenCL ")
                .append("probe warm-up, cache-only placement, launch-shape guidance, practical workload smoke, ")
                .append("packed/root-blob smoke, generated return-first launcher convenience, image-helper guidance, ")
                .append("optimizer artifact review guidance, ")
                .append("and lifecycle trace output.")
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        appendSection(builder, "1. Backend/device selection", backendDeviceSelection);
        appendSection(builder, "2. @GPUTest CPU-reference preflight", methodTestPreflight);
        appendSection(builder, "3. Real OpenCL evidence warm-up and cache-only placement", openClEvidenceSelection);
        appendSection(builder, "4. Launch configuration shapes", renderLaunchConfigurationGuide());
        appendSection(builder, "5. Practical workload smoke", practicalWorkloadSmoke);
        appendSection(builder, "6. Image helper workflow", renderImageHelperGuide());
        appendSection(builder, "7. Optimizer artifact review", renderOptimizerArtifactReviewGuide());

        builder.append("Review files").append(System.lineSeparator());
        builder.append("- Evidence cache: ")
                .append(resolvedCacheDirectory.toAbsolutePath().normalize())
                .append(System.lineSeparator());
        builder.append("- Lifecycle JSONL: ")
                .append(OpenClMethodTestProbeEvidenceSelectionExample.resolveLifecycleJournalFile(resolvedCacheDirectory)
                        .toAbsolutePath()
                        .normalize())
                .append(System.lineSeparator());
        builder.append("- Human trace: ")
                .append(OpenClMethodTestProbeEvidenceSelectionExample.resolveExampleLifecycleTraceFile(resolvedCacheDirectory)
                        .toAbsolutePath()
                        .normalize())
                .append(System.lineSeparator());
        builder.append("- Optimizer journal root: ")
                .append(OptimizationJournalExample.resolveJournalRoot(new String[0]).toAbsolutePath().normalize())
                .append(System.lineSeparator());
        return builder.toString();
    }

    static String renderLaunchConfigurationGuide() {
        GpuExecutionConfig oneDimensional = GpuExecutionConfig.oneDimensional(4L);
        GpuExecutionConfig twoDimensional = GpuExecutionConfig.twoDimensional(16L, 8L, 4L, 2L);
        GpuExecutionConfig threeDimensional = GpuExecutionConfig.threeDimensional(16L, 8L, 4L);
        String invalidLocalShape = invalidTwoDimensionalLocalShapeMessage();

        return "Launch configuration guide" + System.lineSeparator()
                + "- 1D array kernel: " + oneDimensional.summary()
                + ", globalItems=" + oneDimensional.globalItemCount() + System.lineSeparator()
                + "- 2D tiled kernel: " + twoDimensional.summary()
                + ", localItems=" + twoDimensional.localItemCount() + System.lineSeparator()
                + "- 3D volume kernel: " + threeDimensional.summary()
                + ", globalItems=" + threeDimensional.globalItemCount() + System.lineSeparator()
                + "- invalid local-shape example: " + invalidLocalShape + System.lineSeparator();
    }

    static String renderOptimizerArtifactReviewGuide() {
        Path journalRoot = OptimizationJournalExample.resolveJournalRoot(new String[0]);
        List<String> sourceMarkers = OptimizationJournalExample.expectedOptimizedSourceMarkers();
        List<String> evidenceMarkers = OptimizationJournalExample.expectedEvidenceMarkers();

        return "Optimizer artifact review guide" + System.lineSeparator()
                + "- run: .\\gradlew.bat :examples-app:runOptimizationJournalExample --console=plain"
                + System.lineSeparator()
                + "- default journal root: " + journalRoot.toAbsolutePath().normalize() + System.lineSeparator()
                + "- compare generated source: original.backend.opencl-c -> optimized.backend.opencl-c"
                + System.lineSeparator()
                + "- selected compiled source: backend.opencl-c (review-only defaults keep the original selected)"
                + System.lineSeparator()
                + "- handoff/evidence files: runtime-ir-handoff.properties, runtime-ir-optimizer-evidence.properties"
                + System.lineSeparator()
                + "- optimized-source markers to look for: " + String.join(", ", sourceMarkers)
                + System.lineSeparator()
                + "- evidence markers to look for: " + String.join(", ", evidenceMarkers)
                + System.lineSeparator();
    }

    static String renderPracticalWorkloadSmoke() {
        StringBuilder builder = new StringBuilder();
        builder.append("Practical workload smoke: running").append(System.lineSeparator());
        try (GpuRuntimeScope runtimeScope = GpuRuntime.useOpenClSharedCache()) {
            OpenClGpuRuntimeBackend backend = (OpenClGpuRuntimeBackend) runtimeScope.installedBackend();
            GpuRuntimeBackendReport report = backend.describeCapabilities();
            builder.append("OpenCL capability report: ")
                    .append(report.available() ? "available" : "unavailable")
                    .append(capabilityDetail(report))
                    .append(System.lineSeparator());
            appendVectorSmoke(builder);
            appendReturnValueConvenienceSmoke(builder);
            appendStructSmoke(builder, report);
            appendPackedBlobSmoke(builder);
            appendImageSmoke(builder, report, backend);
            builder.append("Practical workload smoke: completed").append(System.lineSeparator());
        } catch (RuntimeException exception) {
            builder.append("Practical workload smoke: blocked").append(System.lineSeparator());
            builder.append("- OpenCL execution failed before workload smoke completed: ")
                    .append(exception.getMessage())
                    .append(System.lineSeparator());
        } finally {
            GpuRuntime.shutdownOpenClSharedCache();
        }
        return builder.toString();
    }

    private static void appendVectorSmoke(StringBuilder builder) {
        float[] input = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
        float[] output = new float[input.length];
        GpuShowcase.vectorExample(new Float2(1.0f, 0.5f), input, output);
        builder.append("- vectorExample: passed output[0]=")
                .append(formatFloat(output[0]))
                .append(", output[3]=")
                .append(formatFloat(output[3]))
                .append(System.lineSeparator());
    }

    private static void appendReturnValueConvenienceSmoke(StringBuilder builder) {
        float[] input = new float[]{2.25f, 3.0f, 4.0f, 5.0f};
        GpuGeneratedLauncherInvoker.GeneratedLauncher qualifierLauncher = GpuGeneratedLauncherInvoker.launcher(
                GpuShowcase.class,
                "qualifierExample"
        );
        GpuGeneratedLauncherReturnValueConvenienceReport report = qualifierLauncher.returnValueConvenience();
        if (!report.available()) {
            builder.append("- return-value convenience: skipped ")
                    .append(report.summary())
                    .append(System.lineSeparator());
            return;
        }
        float first = qualifierLauncher.invokeReturningFirstWithGlobalWorkSizeAs(
                Float.class,
                input.length,
                input
        );
        builder.append("- return-value convenience: passed ")
                .append(report.summary())
                .append(", qualifierExample output[0]=")
                .append(formatFloat(first))
                .append(" using generated output allocation")
                .append(System.lineSeparator());
    }

    private static void appendStructSmoke(StringBuilder builder, GpuRuntimeBackendReport report) {
        if (!report.supports(GpuRuntimeFeature.DOUBLE_PRECISION)) {
            builder.append("- structBufferExample: skipped, backend does not report DOUBLE_PRECISION")
                    .append(System.lineSeparator());
            return;
        }
        Vec2[] input = new Vec2[]{
                new Vec2(1.0, 2.0),
                new Vec2(3.0, 4.0),
                new Vec2(5.0, 6.0),
                new Vec2(7.0, 8.0)
        };
        Vec2[] output = new Vec2[]{new Vec2(), new Vec2(), new Vec2(), new Vec2()};
        GpuShowcase.structBufferExample(input, output);
        builder.append("- structBufferExample: passed output[0]=(")
                .append(formatDouble(output[0].x))
                .append(", ")
                .append(formatDouble(output[0].y))
                .append("), output[3]=(")
                .append(formatDouble(output[3].x))
                .append(", ")
                .append(formatDouble(output[3].y))
                .append(")")
                .append(System.lineSeparator());
    }

    private static void appendPackedBlobSmoke(StringBuilder builder) {
        int rows = 4;
        int secondaryOffset = rows * Integer.BYTES;
        byte[] blob = new byte[secondaryOffset + rows * Integer.BYTES];
        ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.nativeOrder());
        for (int index = 0; index < rows; index++) {
            buffer.putInt(index * Integer.BYTES, index + 1);
            buffer.putInt(secondaryOffset + index * Integer.BYTES, (index + 1) * 10);
        }
        int[] output = new int[blob.length];
        GpuShowcase.packedBlobViewExample(blob, new PackedBlobView(0, secondaryOffset, 3, rows), output);
        builder.append("- packedBlobViewExample: passed output[0]=")
                .append(output[0])
                .append(", output[3]=")
                .append(output[3])
                .append(" using byte[] root blob offsets, logicalRows=")
                .append(rows)
                .append(System.lineSeparator());
    }

    private static void appendImageSmoke(
            StringBuilder builder,
            GpuRuntimeBackendReport report,
            OpenClGpuRuntimeBackend backend
    ) {
        if (!report.supports(GpuRuntimeFeature.IMAGES)) {
            builder.append("- imageExample: skipped, backend does not report IMAGES").append(System.lineSeparator());
            return;
        }
        int[] output = new int[]{0, 0};
        try (OpenClImageWorkflow.RgbaIntToFloat2D images = OpenClImageWorkflow.rgbaIntToFloat2D(
                backend,
                2,
                1,
                new int[]{1, 2, 3, 4, 5, 6, 7, 8}
        )) {
            GpuShowcase.imageExample(images.input(), images.output(), images.sampler(), output);
            float[] writtenPixels = images.readOutputRgbaFloat();
            builder.append("- imageExample: passed output[0]=")
                    .append(output[0])
                    .append(", ")
                    .append(images.summary())
                    .append(", launch=")
                    .append(images.executionConfig().summary())
                    .append(", writtenPixel[0..3]=")
                    .append(formatFloat(writtenPixels[0]))
                    .append(",")
                    .append(formatFloat(writtenPixels[1]))
                    .append(",")
                    .append(formatFloat(writtenPixels[2]))
                    .append(",")
                    .append(formatFloat(writtenPixels[3]))
                    .append(" via OpenClImageWorkflow")
                    .append(System.lineSeparator());
        }
    }

    static String renderImageHelperGuide() {
        return "Image helper guide" + System.lineSeparator()
                + "- check capability: backend.describeCapabilities().supports(GpuRuntimeFeature.IMAGES)"
                + System.lineSeparator()
                + "- create common 2D resources: OpenClImageWorkflow.rgbaIntToFloat2D(backend, width, height, rgba)"
                + System.lineSeparator()
                + "- pass images.input(), images.output(), and images.sampler() to the generated kernel"
                + System.lineSeparator()
                + "- use images.executionConfig() for one-work-item-per-pixel 2D kernels"
                + System.lineSeparator()
                + "- read output with images.readOutputRgbaFloat(); close the workflow with try-with-resources"
                + System.lineSeparator();
    }

    static Path resolveCacheDirectory(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return Path.of(args[0]);
        }
        return DEFAULT_CACHE_DIRECTORY;
    }

    private static void appendSection(StringBuilder builder, String title, String body) {
        builder.append(title).append(System.lineSeparator());
        builder.append(repeat('-', title.length())).append(System.lineSeparator());
        builder.append(body == null ? "" : body.stripTrailing()).append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static String invalidTwoDimensionalLocalShapeMessage() {
        try {
            GpuExecutionConfig.twoDimensional(16L, 8L, 4L, 0L);
            return "unexpectedly accepted";
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }
    }

    private static String repeat(char value, int count) {
        return String.valueOf(value).repeat(Math.max(1, count));
    }

    private static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String capabilityDetail(GpuRuntimeBackendReport report) {
        String detail = report.detail();
        return detail == null || detail.isBlank() ? "" : " - " + detail;
    }
}
