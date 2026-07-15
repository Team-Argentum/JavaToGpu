package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUOptimize;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuGeneratedLauncherInvoker;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Small runnable example for enabling the optional IR optimizer bridge and writing inspection artifacts.
 */
public final class OptimizationJournalExample {

    private static final String ARTIFACT_DIRECTORY_PROPERTY = "javatogpu.opencl.runtimeCompileArtifactDirectory";
    private static final String LIFECYCLE_JOURNAL_FILE_PROPERTY = "javatogpu.runtime.lifecycleJournalFile";
    private static final String LIFECYCLE_JOURNAL_FORMAT_PROPERTY = "javatogpu.runtime.lifecycleJournalFormat";
    private static final String EXAMPLE_LIFECYCLE_TRACE_FILE_PROPERTY = ExampleLifecycleTraceService.TRACE_FILE_PROPERTY;
    private static final Path DEFAULT_JOURNAL_ROOT = Path.of(
            "build",
            "reports",
            "ir-optimizer-journal"
    );
    private static final Set<String> INTERESTING_ARTIFACTS = Set.of(
            "backend.opencl-c",
            "original.backend.opencl-c",
            "optimized.backend.opencl-c",
            "original.irgpu.properties",
            "optimized.irgpu.properties",
            "runtime-ir-handoff.properties",
            "optimizer-report.txt",
            "runtime-ir-optimizer-evidence.properties",
            "runtime-lifecycle.jsonl",
            "runtime-lifecycle.properties",
            "example-lifecycle-service.trace"
    );
    private static final List<String> EXPECTED_OPTIMIZED_SOURCE_MARKERS = List.of(
            "mad(",
            "clamp(",
            "step(",
            "mix(",
            "vload4("
    );
    private static final List<String> EXPECTED_EVIDENCE_MARKERS = List.of(
            "madFmaMaterialization.status",
            "clampMaterialization.status",
            "stepMaterialization.status",
            "mixMaterialization.status",
            "loopVectorizationMaterialization.status",
            "runtimeEquivalenceReview.familySummary"
    );

    private OptimizationJournalExample() {
    }

    public static void main(String[] args) {
        Path journalRoot = resolveJournalRoot(args);
        String previousArtifactDirectory = System.getProperty(ARTIFACT_DIRECTORY_PROPERTY);
        String previousLifecycleJournalFile = System.getProperty(LIFECYCLE_JOURNAL_FILE_PROPERTY);
        String previousLifecycleJournalFormat = System.getProperty(LIFECYCLE_JOURNAL_FORMAT_PROPERTY);
        String previousExampleLifecycleTraceFile = System.getProperty(EXAMPLE_LIFECYCLE_TRACE_FILE_PROPERTY);
        System.setProperty(ARTIFACT_DIRECTORY_PROPERTY, journalRoot.toString());
        if (previousLifecycleJournalFile == null || previousLifecycleJournalFile.isBlank()) {
            System.setProperty(LIFECYCLE_JOURNAL_FILE_PROPERTY, resolveLifecycleJournalFile(journalRoot).toString());
        }
        if (previousLifecycleJournalFormat == null || previousLifecycleJournalFormat.isBlank()) {
            System.setProperty(LIFECYCLE_JOURNAL_FORMAT_PROPERTY, "jsonl");
        }
        if (previousExampleLifecycleTraceFile == null || previousExampleLifecycleTraceFile.isBlank()) {
            System.setProperty(EXAMPLE_LIFECYCLE_TRACE_FILE_PROPERTY, resolveExampleLifecycleTraceFile(journalRoot).toString());
        }

        int vectorRows = 4;
        float[] input = new float[vectorRows * 4];
        for (int index = 0; index < input.length; index++) {
            input[index] = index + 1.0f;
        }
        float[] output = new float[vectorRows];
        GpuRuntimeCompileOptions compileOptions = GpuRuntimeCompileOptions.openCl(
                List.of(),
                "diagnostic"
        );

        System.out.println("IR optimizer example");
        System.out.println("Optimization profile: " + compileOptions.optimizationProfile());
        System.out.println("Artifact journal root: " + journalRoot.toAbsolutePath().normalize());
        System.out.println("Runtime artifact property: -D" + ARTIFACT_DIRECTORY_PROPERTY + "=" + journalRoot);
        System.out.println("Lifecycle journal property: -D" + LIFECYCLE_JOURNAL_FILE_PROPERTY
                + "=" + System.getProperty(LIFECYCLE_JOURNAL_FILE_PROPERTY));
        System.out.println("Example lifecycle service property: -D" + EXAMPLE_LIFECYCLE_TRACE_FILE_PROPERTY
                + "=" + System.getProperty(EXAMPLE_LIFECYCLE_TRACE_FILE_PROPERTY));


        int sizeX = 16;
        int sizeY = 256;
        int sizeZ = 16;

        float chunkOffsetX = 1024.0f;
        float chunkOffsetY = 0.0f;
        float chunkOffsetZ = -512.0f;

        int totalVoxels = sizeX * sizeY * sizeZ;
        float[] densityMap = new float[totalVoxels];

        try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
            GpuGeneratedLauncherInvoker.invokeWithConfigAndCompileOptions(
                    OptimizationJournalExample.class,
                    "optimizerJournalKernel",
                    GpuExecutionConfig.oneDimensional(output.length),
                    compileOptions,
                    input,
                    output,
                    0f
            );
            GpuGeneratedLauncherInvoker.invokeWithConfigAndCompileOptions(
                    OptimizationJournalExample.class,
                    "computeVoxelDensity",
                    GpuExecutionConfig.threeDimensional(sizeX, sizeY, sizeZ),
                    compileOptions,
                    densityMap,
                    sizeX, sizeY,
                    chunkOffsetX, chunkOffsetY, chunkOffsetZ
            );
            System.out.println("output[0] = " + output[0]);
            printJournal(journalRoot);
        } catch (RuntimeException exception) {
            System.out.println("OpenCL execution failed: " + exception.getMessage());
            System.out.println("The example still shows the switches to use; run it on a machine with OpenCL to write artifacts.");
        } finally {
            restoreProperty(ARTIFACT_DIRECTORY_PROPERTY, previousArtifactDirectory);
            restoreProperty(LIFECYCLE_JOURNAL_FILE_PROPERTY, previousLifecycleJournalFile);
            restoreProperty(LIFECYCLE_JOURNAL_FORMAT_PROPERTY, previousLifecycleJournalFormat);
            restoreProperty(EXAMPLE_LIFECYCLE_TRACE_FILE_PROPERTY, previousExampleLifecycleTraceFile);
            GpuRuntime.shutdownOpenClSharedCache();
        }
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    @GPUOptimize(fastMath = true) // Allows diagnostic peephole evidence such as mad/fma/mix planning.
    public static void optimizerJournalKernel(
            @GPUGlobal float[] input,
            @GPUGlobal float[] output,
            float t
    ) {
        int id = GPU.get_global_id(0);
        float value = input[id];

        // Existing-local CSE review candidate: later repeats can reuse scale in optimized.backend.opencl-c.
        float scale = value * 3.1415f;
        float shifted = (value * 3.1415f) + 10.0f;
        float lowered = (value * 3.1415f) - 5.0f;

        // Fast-math mad/fma now materializes into the optimized review artifact only.
        float fmaTarget = scale * shifted + lowered;
        float clampTarget = GPU.min(GPU.max(fmaTarget, 0.0f), 1.0f);
        float mixTarget = scale + t * (shifted - scale);

        // Constant-folding materialization can collapse this in the optimized review artifact.
        int foldedSeed = (2 + 3) * 4;

        float sum = 0.0f;
        for (int i = 0; i < 4; i++) {
            sum = sum + input[id * 4 + i];
        }

        foldedSeed += (int) sum;

        // A small expression chain keeps register-pressure diagnostics visible in the journal.
        float heavyMath = GPU.sin(clampTarget) + GPU.cos(mixTarget) + lowered;
        output[id] = heavyMath + foldedSeed * 0.001f;
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    @GPUOptimize(fastMath = true)
    public static void computeVoxelDensity(
            @GPUGlobal float[] densityMap,
            int sizeX, int sizeY,
            float offsetX, float offsetY, float offsetZ
    ) {
        // (3D NDRange)
        int x = GPU.get_global_id(0);
        int y = GPU.get_global_id(1);
        int z = GPU.get_global_id(2);

        // Target CSE
        int index = x + (y * sizeX) + (z * sizeX * sizeY);

        // Target (CSE + MAD/FMA)
        float worldX = (x * 0.015f) + offsetX;
        float worldY = (y * 0.015f) + offsetY;
        float worldZ = (z * 0.015f) + offsetZ;

        // Target (MAD/FMA)
        float nx = worldX * 2.5f + 10.0f;
        float ny = worldY * 2.5f + 10.0f;
        float nz = worldZ * 2.5f + 10.0f;

        // Target Register
        float wave1 = GPU.sin(nx) * GPU.cos(ny) + GPU.sin(nz);
        float wave2 = GPU.cos(nx) * GPU.sin(ny) + GPU.cos(nz);

        // Target (Clamp (min/max))  = clamp(wave1, -1.0f, 1.0f)?
        float clampedWave = GPU.min(GPU.max(wave1, -1.0f), 1.0f);

        // Target hidden Mix/Lerp = mix(wave2, clampedWave, 0.5f) -> a + t * (b - a)?
        float blend = wave2 + 0.5f * (clampedWave - wave2);

        // Target Step
        float threshold = 0.25f;
        float isSolid = blend > threshold ? 1.0f : 0.0f;

        // Target (Mix/MAD) = mix(clampedWave * 0.1f, blend, isSolid)
        float finalDensity = blend * isSolid + (1.0f - isSolid) * (clampedWave * 0.1f);

        densityMap[index] = finalDensity;
    }

    static Path resolveJournalRoot(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return Path.of(args[0]);
        }
        return DEFAULT_JOURNAL_ROOT;
    }

    static Path resolveLifecycleJournalFile(Path journalRoot) {
        Path root = journalRoot == null ? DEFAULT_JOURNAL_ROOT : journalRoot;
        return root.resolve("runtime-lifecycle.jsonl");
    }

    static Path resolveExampleLifecycleTraceFile(Path journalRoot) {
        Path root = journalRoot == null ? DEFAULT_JOURNAL_ROOT : journalRoot;
        return root.resolve("example-lifecycle-service.trace");
    }

    static List<String> expectedOptimizedSourceMarkers() {
        return EXPECTED_OPTIMIZED_SOURCE_MARKERS;
    }

    static List<String> expectedEvidenceMarkers() {
        return EXPECTED_EVIDENCE_MARKERS;
    }

    static List<Path> interestingJournalFiles(Path journalRoot) throws IOException {
        if (journalRoot == null || !Files.isDirectory(journalRoot)) {
            return List.of();
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(journalRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> INTERESTING_ARTIFACTS.contains(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> journalRoot.relativize(path).toString()))
                    .toList();
        }
    }

    private static void printJournal(Path journalRoot) {
        try {
            List<Path> files = interestingJournalFiles(journalRoot);
            if (files.isEmpty()) {
                System.out.println("No journal artifacts were written yet.");
                return;
            }
            System.out.println("Journal artifacts:");
            for (Path file : files) {
                System.out.println(" - " + journalRoot.relativize(file));
            }
            System.out.println("Expected review-only markers in optimized.backend.opencl-c:");
            for (String marker : expectedOptimizedSourceMarkers()) {
                System.out.println(" - " + marker);
            }
            System.out.println("Expected evidence markers in runtime-ir-optimizer-evidence.properties:");
            for (String marker : expectedEvidenceMarkers()) {
                System.out.println(" - " + marker);
            }
        } catch (IOException exception) {
            System.out.println("Failed to list journal artifacts: " + exception.getMessage());
        }
    }

    private static void restoreProperty(String property, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, previousValue);
        }
    }
}
