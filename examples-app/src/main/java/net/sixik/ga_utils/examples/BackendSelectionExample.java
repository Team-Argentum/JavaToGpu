package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCatalogEntry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendPolicy;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendDeviceSelectionExplanation;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscovery;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestMode;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeSelectionResult;

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
        System.out.println(renderPlannedBackendDiagnostics(GpuBackendTarget.CUDA));
        System.out.println(renderStandardBackendDeviceSelectionAttempt());
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
        }
        return builder.toString();
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
            GpuRuntimeSelectionResult result = GpuRuntime.trySelectStandardBackends();
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
            GpuRuntimeSelectionResult backendSelection = GpuRuntime.trySelectStandardBackends();
            GpuRuntimeDeviceDiscoveryCatalog deviceDiscoveryCatalog = GpuRuntimeDeviceDiscovery.discoverStandardBackends(
                    openClDiscoveryOptions()
            );
            return renderBackendDeviceSelection(backendSelection, deviceDiscoveryCatalog);
        } catch (RuntimeException exception) {
            return "Combined backend/device selection:" + System.lineSeparator()
                    + "Backend or device probing failed before a selection result was produced: "
                    + exception.getMessage()
                    + System.lineSeparator();
        }
    }

    static String renderOpenClDeviceDiscovery() {
        return renderDeviceDiscovery(GpuRuntimeDeviceDiscovery.discoverOpenCl(openClDiscoveryOptions()));
    }

    static String renderDeviceDiscovery(GpuRuntimeDeviceDiscoveryResult result) {
        return "OpenCL device discovery:" + System.lineSeparator()
                + result.toMarkdown();
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
