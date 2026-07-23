package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCatalogEntry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendPolicy;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProviderCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProviders;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendDeviceSelectionExplanation;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestMode;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeSelectionResult;
import net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeSelection;

import java.util.List;

/**
 * Small runnable example for backend catalog discovery and runtime-selection explanation output.
 */
public final class BackendSelectionExample {

    private BackendSelectionExample() {
    }

    public static void main(String[] args) {
        System.out.println("Backend selection example");
        System.out.println();
        System.out.println(renderCatalog(GpuRuntimeBackendCatalog.standardWithPlannedBackends()));
        System.out.println(renderBackendExecutionAvailability());
        System.out.println(renderPlannedBackendDiagnostics(GpuBackendTarget.CUDA));
        System.out.println(renderStandardBackendDeviceSelectionAttempt());
        System.out.println(renderScoreBasedRankingGuide());
        System.out.println(renderAutomaticBackendDevicePreflightGuide());
    }

    static String renderCatalog(List<GpuRuntimeBackendCatalogEntry> entries) {
        StringBuilder builder = new StringBuilder();
        builder.append("Backend catalog:").append('\n');
        for (GpuRuntimeBackendCatalogEntry entry : entries) {
            builder.append("- ")
                    .append(entry.backendName())
                    .append(" (`")
                    .append(entry.backendTarget())
                    .append("`), productionAdapter=")
                    .append(entry.productionAdapter())
                    .append(", ownership=")
                    .append(entry.ownership())
                    .append('\n');
            builder.append("  diagnostic: ").append(entry.diagnostic()).append('\n');
            entry.executionSupport().ifPresent(support -> {
                builder.append("  moduleFormats: ")
                        .append(support.moduleFormatKeys())
                        .append('\n');
                builder.append("  capabilityVocabulary: ")
                        .append(support.capabilityKeys())
                        .append('\n');
            });
        }
        return builder.toString();
    }

    static String renderBackendExecutionAvailability() {
        return renderBackendExecutionAvailability(GpuRuntimeBackendProviders.standardWithPlannedBackends());
    }

    static String renderBackendExecutionAvailability(List<GpuRuntimeBackendProvider> providers) {
        return GpuRuntimeBackendProviderCatalog.of(providers).toMarkdown();
    }

    static String renderPlannedBackendDiagnostics(GpuBackendTarget backendTarget) {
        GpuRuntimeSelectionResult result = GpuRuntimeBackendPolicy.builder()
                .preferCatalogEntry(GpuRuntimeBackendCatalog.plannedUnsupported(backendTarget))
                .build()
                .trySelect();

        return "Planned backend diagnostic:" + System.lineSeparator()
                + result.explanation().toMarkdown();
    }

    static String renderStandardSelectionAttempt() {
        try {
            GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
                    .preferStandardBackends()
                    .build();
            GpuRuntimeSelectionResult result = GpuRuntimeSelection.trySelect(policy);
            return "Standard backend selection:" + System.lineSeparator()
                    + result.explanation().toMarkdown();
        } catch (RuntimeException exception) {
            return "Standard backend selection:" + System.lineSeparator()
                    + "Backend probing failed before a selection result was produced: "
                    + exception.getMessage()
                    + System.lineSeparator();
        }
    }

    static String renderStandardBackendDeviceSelectionAttempt() {
        try {
            GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
                    .preferStandardBackends()
                    .build();
            GpuRuntimeSelectionResult backendSelection = GpuRuntimeSelection.trySelect(policy);
            GpuRuntimeDeviceDiscoveryCatalog deviceDiscoveryCatalog = GpuRuntimeSelection.discoverStandardBackends(
                    openClDiscoveryOptions()
            );
            return renderBackendDeviceSelection(backendSelection, deviceDiscoveryCatalog)
                    + System.lineSeparator()
                    + renderBackendExecutionAvailability()
                    + System.lineSeparator()
                    + renderCudaInventoryFacts(deviceDiscoveryCatalog);
        } catch (RuntimeException exception) {
            return "Combined backend/device selection:" + System.lineSeparator()
                    + "Backend or device probing failed before a selection result was produced: "
                    + exception.getMessage()
                    + System.lineSeparator();
        }
    }

    static String renderOpenClDeviceDiscovery() {
        return renderDeviceDiscovery(GpuRuntimeSelection.discoverOpenCl(openClDiscoveryOptions()));
    }

    static String renderAutomaticBackendDevicePreflightGuide() {
        GpuRuntimeCompileOptions options = openClDiscoveryOptions().withStandardBackendDevicePreflight();
        return "Automatic backend/device preflight profile:" + System.lineSeparator()
                + "- enable with: GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)"
                + ".preferDeviceClass(GpuDeviceClassTarget.DGPU)"
                + ".excludeCpuDevices()"
                + ".withStandardBackendDevicePreflight()" + System.lineSeparator()
                + "- call through: DemoKernel_GpuLauncher.invokeWithCompileOptions(options, ...)"
                + System.lineSeparator()
                + "- mode: " + options.backendOptions().backendDevicePreflightMode() + System.lineSeparator()
                + "- requested: " + options.backendOptions().requestsStandardBackendDevicePreflight()
                + System.lineSeparator()
                + "- behavior: opens the standard backend+device scope only when no backend is already installed"
                + System.lineSeparator();
    }

    static String renderScoreBasedRankingGuide() {
        GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
                .rankCandidatesByScore()
                .preferStandardBackendsWithPlannedDiagnostics()
                .build();
        return "Score-based backend ranking profile:" + System.lineSeparator()
                + "- enable with: GpuRuntimeBackendPolicy.builder().rankCandidatesByScore()"
                + System.lineSeparator()
                + "- mode: " + policy.candidateOrdering().key() + System.lineSeparator()
                + "- behavior: evaluates all candidates, applies hard requirements first, then selects highest score"
                + System.lineSeparator()
                + "- default: fallback-order remains the safe behavior unless rankCandidatesByScore() is used"
                + System.lineSeparator();
    }

    static String renderDeviceDiscovery(GpuRuntimeDeviceDiscoveryResult result) {
        return "OpenCL device discovery:" + System.lineSeparator()
                + result.toMarkdown();
    }

    static String renderCudaInventoryFacts(GpuRuntimeDeviceDiscoveryCatalog catalog) {
        return catalog.forBackend(GpuBackendTarget.CUDA)
                .map(BackendSelectionExample::renderCudaInventoryFacts)
                .orElse("CUDA inventory facts:" + System.lineSeparator()
                        + "- CUDA discovery was not part of this catalog" + System.lineSeparator());
    }

    static String renderCudaInventoryFacts(GpuRuntimeDeviceDiscoveryResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA inventory facts:").append(System.lineSeparator());
        if (!result.discoveryAvailable()) {
            builder.append("- unavailable: ")
                    .append(result.firstBlocker())
                    .append(System.lineSeparator());
            return builder.toString();
        }
        if (result.discoveredDevices().isEmpty()) {
            builder.append("- no CUDA devices reported").append(System.lineSeparator());
            return builder.toString();
        }
        for (GpuRuntimeDeviceProfile profile : result.discoveredDevices()) {
            builder.append("- ")
                    .append(profile.deviceLabel())
                    .append(": runtime=")
                    .append(profile.cudaRuntimeVersion())
                    .append(", computeCapability=")
                    .append(profile.cudaComputeCapability())
                    .append(", memoryBytes=")
                    .append(profile.globalMemoryBytes())
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    static String renderBackendDeviceSelection(
            GpuRuntimeSelectionResult backendSelection,
            GpuRuntimeDeviceDiscoveryResult deviceDiscovery
    ) {
        GpuRuntimeBackendDeviceSelectionExplanation explanation = backendSelection.explainWithDeviceDiscovery(
                deviceDiscovery
        );
        return "Combined backend/device selection:" + System.lineSeparator()
                + explanation.toMarkdown();
    }

    static String renderBackendDeviceSelection(
            GpuRuntimeSelectionResult backendSelection,
            GpuRuntimeDeviceDiscoveryCatalog deviceDiscoveryCatalog
    ) {
        GpuRuntimeBackendDeviceSelectionExplanation explanation = backendSelection.explainWithDeviceDiscovery(
                deviceDiscoveryCatalog
        );
        return "Combined backend/device selection:" + System.lineSeparator()
                + explanation.toMarkdown();
    }

    private static GpuRuntimeCompileOptions openClDiscoveryOptions() {
        return GpuRuntimeCompileOptions
                .defaults(GpuBackendTarget.OPENCL)
                .preferDeviceClass(GpuDeviceClassTarget.DGPU)
                .excludeCpuDevices()
                .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.DISABLED);
    }
}
