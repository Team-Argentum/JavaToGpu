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
            "runtime-ir-optimizer-evidence.properties"
    );

    private OptimizationJournalExample() {
    }

    public static void main(String[] args) {
        Path journalRoot = resolveJournalRoot(args);
        String previousArtifactDirectory = System.getProperty(ARTIFACT_DIRECTORY_PROPERTY);
        System.setProperty(ARTIFACT_DIRECTORY_PROPERTY, journalRoot.toString());

        float[] input = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
        float[] output = new float[input.length];
        GpuRuntimeCompileOptions compileOptions = GpuRuntimeCompileOptions.openCl(
                List.of(),
                "diagnostic"
        );

        System.out.println("IR optimizer example");
        System.out.println("Optimization profile: " + compileOptions.optimizationProfile());
        System.out.println("Artifact journal root: " + journalRoot.toAbsolutePath().normalize());
        System.out.println("Runtime artifact property: -D" + ARTIFACT_DIRECTORY_PROPERTY + "=" + journalRoot);

        try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
            GpuGeneratedLauncherInvoker.invokeWithConfigAndCompileOptions(
                    OptimizationJournalExample.class,
                    "optimizerJournalKernel",
                    GpuExecutionConfig.oneDimensional(input.length),
                    compileOptions,
                    input,
                    output,
                    0f
            );
            System.out.println("output[0] = " + output[0]);
            printJournal(journalRoot);
        } catch (RuntimeException exception) {
            System.out.println("OpenCL execution failed: " + exception.getMessage());
            System.out.println("The example still shows the switches to use; run it on a machine with OpenCL to write artifacts.");
        } finally {
            restoreProperty(previousArtifactDirectory);
            GpuRuntime.shutdownOpenClSharedCache();
        }
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    @GPUOptimize(fastMath = true) // Разрешаем оптимизатору применять fma/mad/mix и менять округление
    public static void optimizerJournalKernel(
            @GPUGlobal float[] input,
            @GPUGlobal float[] output,
            float t
    ) {
        int id = GPU.get_global_id(0);
        float value = input[id];

        // 1. Мишень для CSE (Common Subexpression Elimination)
        // Выражение (value * 3.1415f) повторяется три раза подряд без мутации 'value'.
        float a = value * 3.1415f;
        float b = (value * 3.1415f) + 10.0f;
        float c = (value * 3.1415f) - 5.0f;

        // 2. Мишень для InstCombine (mad / fma)
        // Паттерн a * b + c
        float fmaTarget = a * b + c;

        // 3. Мишень для InstCombine (clamp)
        // Паттерн min(max(x, lo), hi)
        float clampTarget = GPU.min(GPU.max(fmaTarget, 0.0f), 1.0f);

        // 4. Мишень для InstCombine (mix / lerp)
        // Паттерн a + t * (b - a)
        float mixTarget = a + t * (b - a);

        // 5. Мишень для Auto-vectorization
        // Последовательный доступ к памяти внутри цикла с фиксированным размером
        float sum = 0.0f;
        for (int i = 0; i < 4; i++) {
            sum = sum + input[id * 4 + i];
        }

        // 6. Давление на регистры (Register Pressure)
        // Множество промежуточных переменных сходятся в одном тяжелом математическом выражении,
        // что должно отразиться в GpuRuntimeRegisterPressureAnalyzer
        float heavyMath = GPU.sin(clampTarget) + GPU.cos(mixTarget) + sum;

        output[id] = heavyMath;
    }

    static Path resolveJournalRoot(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return Path.of(args[0]);
        }
        return DEFAULT_JOURNAL_ROOT;
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
        } catch (IOException exception) {
            System.out.println("Failed to list journal artifacts: " + exception.getMessage());
        }
    }

    private static void restoreProperty(String previousArtifactDirectory) {
        if (previousArtifactDirectory == null) {
            System.clearProperty(ARTIFACT_DIRECTORY_PROPERTY);
        } else {
            System.setProperty(ARTIFACT_DIRECTORY_PROPERTY, previousArtifactDirectory);
        }
    }
}
