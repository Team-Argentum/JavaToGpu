package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendPolicy;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeSelectionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendSelectionExampleTest {

    @Test
    void rendersCatalogWithoutNativeBackendProbe() {
        String catalog = BackendSelectionExample.renderCatalog(GpuRuntimeBackendCatalog.standardWithPlannedBackends());

        assertTrue(catalog.contains("Backend catalog:"));
        assertTrue(catalog.contains("OpenCL (shared cache) (`OPENCL`)"));
        assertTrue(catalog.contains("CUDA (`CUDA`)"));
        assertTrue(catalog.contains("VULKAN (`VULKAN`)"));
        assertTrue(catalog.contains("METAL (`METAL`)"));
        assertTrue(catalog.contains("Runtime backend adapter is not implemented for CUDA"));
    }

    @Test
    void rendersPlannedBackendDiagnosticWithoutNativeBackendProbe() {
        String diagnostic = BackendSelectionExample.renderPlannedBackendDiagnostics(GpuBackendTarget.CUDA);

        assertTrue(diagnostic.contains("Planned backend diagnostic:"));
        assertTrue(diagnostic.contains("Backend selection: not matched"));
        assertTrue(diagnostic.contains("CUDA: Runtime backend adapter is not implemented for CUDA"));
    }

    @Test
    void rendersDeviceDiscoveryResultWithoutNativeBackendProbe() {
        String discovery = BackendSelectionExample.renderDeviceDiscovery(GpuRuntimeDeviceDiscoveryResult.unavailable(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "opencl-device-discovery-failed",
                new IllegalStateException("No OpenCL device found")
        ));

        assertTrue(discovery.contains("OpenCL device discovery:"));
        assertTrue(discovery.contains("Device discovery: unavailable"));
        assertTrue(discovery.contains("First blocker: opencl-device-discovery-failed"));
        assertTrue(discovery.contains("IllegalStateException: No OpenCL device found"));
    }

    @Test
    void rendersCombinedBackendDeviceExplanationWithoutNativeBackendProbe() {
        GpuRuntimeSelectionResult backendSelection = GpuRuntimeBackendPolicy.builder()
                .preferCatalogEntry(GpuRuntimeBackendCatalog.plannedUnsupported(GpuBackendTarget.CUDA))
                .build()
                .trySelect();
        GpuRuntimeDeviceDiscoveryResult deviceDiscovery = GpuRuntimeDeviceDiscoveryResult.unavailable(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "opencl-device-discovery-failed",
                new IllegalStateException("No OpenCL device found")
        );

        String combined = BackendSelectionExample.renderBackendDeviceSelection(
                backendSelection,
                GpuRuntimeDeviceDiscoveryCatalog.of(List.of(deviceDiscovery))
        );

        assertTrue(combined.contains("Combined backend/device selection:"));
        assertTrue(combined.contains("Runtime selection: backend-not-selected"));
        assertTrue(combined.contains("Backend:"));
        assertTrue(combined.contains("Device discoveries:"));
        assertTrue(combined.contains("CUDA: Runtime backend adapter is not implemented for CUDA"));
        assertTrue(combined.contains("First blocker: opencl-device-discovery-failed"));
    }
}
