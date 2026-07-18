package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyContext;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestMode;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEventBus;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestGpuProbeOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestProbeEvidenceSelection;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestProbeEvidenceSelectionPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestProbeEvidenceWarmupCandidates;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestProbeEvidenceWarmupPlan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Runs the {@code @GPUTest} evidence warm-up + cache-only selection flow against discovered OpenCL devices.
 */
public final class OpenClMethodTestProbeEvidenceSelectionExample {

    public static final String WARMUP_LIMIT_PROPERTY = "javatogpu.methodTestProbeOpenClWarmupLimit";

    private static final String LIFECYCLE_JOURNAL_FILE_PROPERTY = "javatogpu.runtime.lifecycleJournalFile";
    private static final String LIFECYCLE_JOURNAL_FORMAT_PROPERTY = "javatogpu.runtime.lifecycleJournalFormat";
    private static final String EXAMPLE_LIFECYCLE_TRACE_FILE_PROPERTY = ExampleLifecycleTraceService.TRACE_FILE_PROPERTY;

    private OpenClMethodTestProbeEvidenceSelectionExample() {
    }

    public static void main(String[] args) {
        Path cacheDirectory = args.length == 0
                ? Path.of("build", "method-test-probe-opencl-evidence")
                : Path.of(args[0]);
        System.out.println(renderRealOpenClEvidenceSelection(cacheDirectory));
    }

    static String renderRealOpenClEvidenceSelection(Path cacheDirectory) {
        Path resolvedCacheDirectory = normalizeCacheDirectory(cacheDirectory);
        String previousLifecycleJournalFile = System.getProperty(LIFECYCLE_JOURNAL_FILE_PROPERTY);
        String previousLifecycleJournalFormat = System.getProperty(LIFECYCLE_JOURNAL_FORMAT_PROPERTY);
        String previousExampleLifecycleTraceFile = System.getProperty(EXAMPLE_LIFECYCLE_TRACE_FILE_PROPERTY);
        boolean ownsLifecycleJournalFile = isBlank(previousLifecycleJournalFile);
        boolean ownsLifecycleJournalFormat = isBlank(previousLifecycleJournalFormat);
        boolean ownsExampleLifecycleTraceFile = isBlank(previousExampleLifecycleTraceFile);
        if (ownsLifecycleJournalFile) {
            Path lifecycleJournalFile = resolveLifecycleJournalFile(resolvedCacheDirectory);
            clearGeneratedFile(lifecycleJournalFile);
            System.setProperty(LIFECYCLE_JOURNAL_FILE_PROPERTY, lifecycleJournalFile.toString());
        }
        if (ownsLifecycleJournalFormat) {
            System.setProperty(LIFECYCLE_JOURNAL_FORMAT_PROPERTY, "jsonl");
        }
        if (ownsExampleLifecycleTraceFile) {
            Path exampleLifecycleTraceFile = resolveExampleLifecycleTraceFile(resolvedCacheDirectory);
            clearGeneratedFile(exampleLifecycleTraceFile);
            System.setProperty(EXAMPLE_LIFECYCLE_TRACE_FILE_PROPERTY, exampleLifecycleTraceFile.toString());
        }

        GpuRuntimeCompileOptions baseOptions = baseOpenClOptions();
        try {
            return renderOpenClEvidenceSelectionOneCall(
                    resolvedCacheDirectory,
                    MethodTestProbeExample.descriptor(),
                    OpenClMethodTestProbeEvidenceSelectionExample.class.getClassLoader(),
                    baseOptions
            );
        } finally {
            restoreProperty(LIFECYCLE_JOURNAL_FILE_PROPERTY, previousLifecycleJournalFile);
            restoreProperty(LIFECYCLE_JOURNAL_FORMAT_PROPERTY, previousLifecycleJournalFormat);
            restoreProperty(EXAMPLE_LIFECYCLE_TRACE_FILE_PROPERTY, previousExampleLifecycleTraceFile);
        }
    }

    static String renderOpenClEvidenceSelection(
            Path cacheDirectory,
            GpuKernelDescriptor descriptor,
            ClassLoader classLoader,
            GpuRuntimeDeviceDiscoveryResult discovery,
            GpuRuntimeCompileOptions baseOptions
    ) {
        Path resolvedCacheDirectory = normalizeCacheDirectory(cacheDirectory);
        GpuRuntimeCompileOptions resolvedBaseOptions = baseOptions == null ? baseOpenClOptions() : baseOptions;
        GpuRuntimeDeviceDiscoveryResult resolvedDiscovery = discovery == null
                ? GpuRuntimeDeviceDiscoveryResult.unavailable(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "opencl-device-discovery-result-missing",
                new IllegalStateException("OpenCL device discovery result is missing")
        )
                : discovery;
        GpuRuntimeMethodTestProbeEvidenceSelectionPlan selectionPlan = GpuRuntimeMethodTestProbeEvidenceSelection.warmAndSelect(
                descriptor,
                classLoader,
                GpuRuntimeMethodTestProbeEvidenceWarmupCandidates.openClGpuDevices(resolvedDiscovery, warmupLimit()),
                resolvedDiscovery,
                GpuRuntimeMethodTestGpuProbeOptions
                        .persistentCached(resolvedCacheDirectory)
                        .withCompileOptions(resolvedBaseOptions),
                resolvedBaseOptions,
                Optional.empty(),
                GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns(),
                GpuRuntimeLifecycleEventBus.loadFromServiceLoader()
        );

        StringBuilder builder = new StringBuilder();
        builder.append("Real OpenCL method-test probe evidence selection example").append(System.lineSeparator());
        builder.append("Cache directory: ").append(resolvedCacheDirectory.toAbsolutePath().normalize())
                .append(System.lineSeparator());
        appendWarmupCandidates(builder, selectionPlan.warmupPlan());
        builder.append(System.lineSeparator()).append("Discovery").append(System.lineSeparator());
        builder.append(resolvedDiscovery.toMarkdown());
        builder.append(System.lineSeparator()).append("Selection").append(System.lineSeparator());
        builder.append(selectionPlan.toMarkdown());
        builder.append(System.lineSeparator())
                .append("Rule: warm-up may execute tiny @GPUTest probes only for listed candidates; selection remains cache-only.")
                .append(System.lineSeparator());
        return builder.toString();
    }

    static String renderOpenClEvidenceSelectionOneCall(
            Path cacheDirectory,
            GpuKernelDescriptor descriptor,
            ClassLoader classLoader,
            GpuRuntimeCompileOptions baseOptions
    ) {
        Path resolvedCacheDirectory = normalizeCacheDirectory(cacheDirectory);
        GpuRuntimeCompileOptions resolvedBaseOptions = baseOptions == null ? baseOpenClOptions() : baseOptions;
        GpuRuntimeMethodTestProbeEvidenceSelectionPlan selectionPlan = GpuRuntimeMethodTestProbeEvidenceSelection.warmAndSelectOpenCl(
                descriptor,
                classLoader,
                GpuRuntimeMethodTestGpuProbeOptions
                        .persistentCached(resolvedCacheDirectory)
                        .withCompileOptions(resolvedBaseOptions),
                resolvedBaseOptions,
                warmupLimit()
        );

        StringBuilder builder = new StringBuilder();
        builder.append("Real OpenCL method-test probe evidence selection example").append(System.lineSeparator());
        builder.append("Cache directory: ").append(resolvedCacheDirectory.toAbsolutePath().normalize())
                .append(System.lineSeparator());
        appendWarmupCandidates(builder, selectionPlan.warmupPlan());
        builder.append(System.lineSeparator()).append("Selection").append(System.lineSeparator());
        builder.append(selectionPlan.toMarkdown());
        appendLifecycleOutputs(builder);
        builder.append(System.lineSeparator())
                .append("Rule: warmAndSelectOpenCl discovers OpenCL first, warms explicit candidates, then selects cache-only.")
                .append(System.lineSeparator());
        return builder.toString();
    }

    private static void appendWarmupCandidates(
            StringBuilder builder,
            GpuRuntimeMethodTestProbeEvidenceWarmupPlan warmupPlan
    ) {
        if (warmupPlan == null) {
            builder.append("Warm-up candidate count: 0").append(System.lineSeparator());
            return;
        }
        builder.append("Warm-up candidate count: ").append(warmupPlan.candidateResults().size()).append(System.lineSeparator());
        warmupPlan.candidateResults().forEach(candidate -> builder.append("- ")
                .append(candidate.deviceProfile().deviceLabel())
                .append(" (`")
                .append(GpuRuntimeDevicePolicyContext.deviceKey(candidate.deviceProfile()))
                .append("`)")
                .append(System.lineSeparator()));
    }

    private static GpuRuntimeCompileOptions baseOpenClOptions() {
        return GpuRuntimeCompileOptions
                .defaults(GpuBackendTarget.OPENCL)
                .excludeCpuDevices()
                .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.DISABLED);
    }

    static Path resolveLifecycleJournalFile(Path cacheDirectory) {
        Path root = cacheDirectory == null
                ? Path.of("build", "method-test-probe-opencl-evidence")
                : cacheDirectory;
        return root.resolve("runtime-lifecycle.jsonl");
    }

    static Path resolveExampleLifecycleTraceFile(Path cacheDirectory) {
        Path root = cacheDirectory == null
                ? Path.of("build", "method-test-probe-opencl-evidence")
                : cacheDirectory;
        return root.resolve("opencl-evidence-selection.trace");
    }

    static List<String> openClSelectionTraceLines(Path traceFile) throws IOException {
        if (traceFile == null || !Files.isRegularFile(traceFile)) {
            return List.of();
        }
        return Files.readAllLines(traceFile, StandardCharsets.UTF_8)
                .stream()
                .filter(line -> line.startsWith("METHOD_TEST_GPU_PROBE_EVIDENCE_SELECTION_"))
                .toList();
    }

    private static void appendLifecycleOutputs(StringBuilder builder) {
        Path lifecycleJournalFile = configuredPath(LIFECYCLE_JOURNAL_FILE_PROPERTY);
        Path exampleLifecycleTraceFile = configuredPath(EXAMPLE_LIFECYCLE_TRACE_FILE_PROPERTY);
        if (lifecycleJournalFile == null && exampleLifecycleTraceFile == null) {
            return;
        }
        builder.append(System.lineSeparator()).append("Lifecycle journal").append(System.lineSeparator());
        if (lifecycleJournalFile != null) {
            builder.append("JSONL journal: ")
                    .append(lifecycleJournalFile.toAbsolutePath().normalize())
                    .append(System.lineSeparator());
        }
        if (exampleLifecycleTraceFile == null) {
            return;
        }
        builder.append("Service trace: ")
                .append(exampleLifecycleTraceFile.toAbsolutePath().normalize())
                .append(System.lineSeparator());
        try {
            List<String> traceLines = openClSelectionTraceLines(exampleLifecycleTraceFile);
            if (traceLines.isEmpty()) {
                builder.append("Lifecycle trace preview: no warmAndSelectOpenCl events were written yet")
                        .append(System.lineSeparator());
                return;
            }
            builder.append("Lifecycle trace preview:").append(System.lineSeparator());
            traceLines.forEach(line -> builder.append("- ").append(line).append(System.lineSeparator()));
        } catch (IOException exception) {
            builder.append("Lifecycle trace preview failed: ").append(exception.getMessage()).append(System.lineSeparator());
        }
    }

    private static Path configuredPath(String property) {
        String value = System.getProperty(property);
        return isBlank(value) ? null : Path.of(value.trim());
    }

    private static void clearGeneratedFile(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().normalize().getParent());
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not reset generated lifecycle file: " + file, exception);
        }
    }

    private static void restoreProperty(String property, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, previousValue);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Path normalizeCacheDirectory(Path cacheDirectory) {
        Path resolved = cacheDirectory == null
                ? Path.of("build", "method-test-probe-opencl-evidence")
                : cacheDirectory;
        try {
            Files.createDirectories(resolved);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create method-test probe evidence cache directory: " + resolved, exception);
        }
        return resolved;
    }

    private static int warmupLimit() {
        String value = System.getProperty(WARMUP_LIMIT_PROPERTY);
        if (value == null || value.isBlank()) {
            return GpuRuntimeMethodTestProbeEvidenceWarmupCandidates.DEFAULT_OPENCL_GPU_WARMUP_LIMIT;
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return GpuRuntimeMethodTestProbeEvidenceWarmupCandidates.DEFAULT_OPENCL_GPU_WARMUP_LIMIT;
        }
    }
}
