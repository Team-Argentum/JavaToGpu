package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscovery;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyContext;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestMode;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEventBus;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestGpuProbeOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestProbeEvidenceSelection;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestProbeEvidenceSelectionPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestProbeEvidenceWarmupCandidate;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Runs the {@code @GPUTest} evidence warm-up + cache-only selection flow against discovered OpenCL devices.
 */
public final class OpenClMethodTestProbeEvidenceSelectionExample {

    public static final String WARMUP_LIMIT_PROPERTY = "javatogpu.methodTestProbeOpenClWarmupLimit";
    private static final int DEFAULT_WARMUP_LIMIT = 4;

    private OpenClMethodTestProbeEvidenceSelectionExample() {
    }

    public static void main(String[] args) {
        Path cacheDirectory = args.length == 0
                ? Path.of("build", "method-test-probe-opencl-evidence")
                : Path.of(args[0]);
        System.out.println(renderRealOpenClEvidenceSelection(cacheDirectory));
    }

    static String renderRealOpenClEvidenceSelection(Path cacheDirectory) {
        GpuRuntimeCompileOptions baseOptions = baseOpenClOptions();
        return renderOpenClEvidenceSelection(
                cacheDirectory,
                MethodTestProbeExample.descriptor(),
                OpenClMethodTestProbeEvidenceSelectionExample.class.getClassLoader(),
                GpuRuntimeDeviceDiscovery.discoverOpenCl(baseOptions),
                baseOptions
        );
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
        List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> warmupCandidates = openClWarmupCandidates(
                resolvedDiscovery,
                warmupLimit()
        );
        GpuRuntimeMethodTestProbeEvidenceSelectionPlan selectionPlan = GpuRuntimeMethodTestProbeEvidenceSelection.warmAndSelect(
                descriptor,
                classLoader,
                warmupCandidates,
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
        builder.append("Warm-up candidate count: ").append(warmupCandidates.size()).append(System.lineSeparator());
        for (GpuRuntimeMethodTestProbeEvidenceWarmupCandidate candidate : warmupCandidates) {
            builder.append("- ")
                    .append(candidate.deviceProfile().deviceLabel())
                    .append(" (`")
                    .append(GpuRuntimeDevicePolicyContext.deviceKey(candidate.deviceProfile()))
                    .append("`)")
                    .append(System.lineSeparator());
        }
        builder.append(System.lineSeparator()).append("Discovery").append(System.lineSeparator());
        builder.append(resolvedDiscovery.toMarkdown());
        builder.append(System.lineSeparator()).append("Selection").append(System.lineSeparator());
        builder.append(selectionPlan.toMarkdown());
        builder.append(System.lineSeparator())
                .append("Rule: warm-up may execute tiny @GPUTest probes only for listed candidates; selection remains cache-only.")
                .append(System.lineSeparator());
        return builder.toString();
    }

    static List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> openClWarmupCandidates(
            GpuRuntimeDeviceDiscoveryResult discovery,
            int limit
    ) {
        if (discovery == null || !discovery.discoveryAvailable()) {
            return List.of();
        }
        int resolvedLimit = limit <= 0 ? DEFAULT_WARMUP_LIMIT : limit;
        List<GpuRuntimeDeviceProfile> gpuDevices = discovery.discoveredDevices().stream()
                .filter(profile -> profile.backendTarget() == GpuBackendTarget.OPENCL)
                .filter(profile -> profile.deviceClass() != GpuDeviceClassTarget.CPU)
                .limit(resolvedLimit)
                .toList();
        List<GpuRuntimeDeviceProfile> selectedDevices = gpuDevices.isEmpty()
                ? discovery.discoveredDevices().stream()
                .filter(profile -> profile.backendTarget() == GpuBackendTarget.OPENCL)
                .limit(resolvedLimit)
                .toList()
                : gpuDevices;
        return selectedDevices.stream()
                .map(profile -> GpuRuntimeMethodTestProbeEvidenceWarmupCandidate.owned(
                        profile,
                        OpenClGpuRuntimeBackend::new
                ))
                .toList();
    }

    private static GpuRuntimeCompileOptions baseOpenClOptions() {
        return GpuRuntimeCompileOptions
                .defaults(GpuBackendTarget.OPENCL)
                .excludeCpuDevices()
                .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.DISABLED);
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
            return DEFAULT_WARMUP_LIMIT;
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return DEFAULT_WARMUP_LIMIT;
        }
    }
}
