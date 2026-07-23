package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeDeviceDiscoveryResultTest {

    @Test
    void rendersDeviceSelectionMarkdownAndArtifactFields() {
        GpuRuntimeDeviceProfile integrated = device(
                "opencl-0",
                "Intel Integrated",
                "Intel",
                "Intel OpenCL",
                "OpenCL 3.0 Intel",
                GpuDeviceClassTarget.IGPU,
                8
        );
        GpuRuntimeDeviceProfile discrete = device(
                "opencl-1",
                "NVIDIA RTX",
                "NVIDIA",
                "NVIDIA CUDA",
                "OpenCL 3.0 CUDA",
                GpuDeviceClassTarget.DGPU,
                48
        );

        List<GpuRuntimeDeviceProfile> profiles = List.of(integrated, discrete);
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                .preferDeviceClass(GpuDeviceClassTarget.DGPU)
                .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.DISABLED);
        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                GpuRuntimeDevicePolicyContext.forBackendDiscovery(options, profiles)
        );
        GpuRuntimeDeviceDiscoveryResult result = GpuRuntimeDeviceDiscoveryResult.available(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                profiles,
                selection
        );

        assertEquals("NVIDIA RTX", result.selectedDevice().orElseThrow().deviceLabel());
        String markdown = result.toMarkdown();
        assertTrue(markdown.contains("Device discovery: available"));
        assertTrue(markdown.contains("Backend: OpenCL (`OPENCL`)"));
        assertTrue(markdown.contains("Selected device: NVIDIA RTX (`OPENCL:opencl-1`)"));
        assertTrue(markdown.contains("Platforms:"));
        assertTrue(markdown.contains("NVIDIA CUDA (`OpenCL 3.0 CUDA`), devices=1"));
        assertTrue(markdown.contains("Self-tests: mode=disabled, disabled=2"));
        assertTrue(markdown.contains("class=dgpu"));
        assertTrue(markdown.contains("platform=NVIDIA CUDA (`OpenCL 3.0 CUDA`)"));
        assertTrue(markdown.contains("score="));
        Map<String, String> fields = result.artifactFields("openclDiscovery");
        assertEquals("OPENCL", fields.get("runtime.backend.target"));
        assertEquals("OpenCL", fields.get("runtime.backend.name"));
        assertEquals("true", fields.get("runtime.device.discovery.present"));
        assertEquals("true", fields.get("runtime.device.discovery.available"));
        assertEquals("2", fields.get("runtime.device.discovery.device.count"));
        assertEquals("OPENCL:opencl-1", fields.get("runtime.device.discovery.selectedDeviceKey"));
        assertEquals("true", fields.get("runtime.device.selected"));
        assertEquals("opencl-1", fields.get("runtime.device.id"));
        assertEquals("NVIDIA RTX", fields.get("runtime.device.label"));
        assertEquals("NVIDIA", fields.get("runtime.device.vendor"));
        assertEquals("DGPU", fields.get("runtime.device.class"));
        assertEquals("OPENCL", fields.get("openclDiscovery.backendTarget"));
        assertEquals("true", fields.get("openclDiscovery.available"));
        assertEquals("2", fields.get("openclDiscovery.device.count"));
        assertEquals("2", fields.get("openclDiscovery.platform.count"));
        assertEquals("NVIDIA CUDA", fields.get("openclDiscovery.platform.1.name"));
        assertEquals("OpenCL 3.0 CUDA", fields.get("openclDiscovery.platform.1.version"));
        assertEquals("disabled", fields.get("openclDiscovery.selfTest.mode"));
        assertEquals("disabled", fields.get("openclDiscovery.selfTest.status.0.name"));
        assertEquals("2", fields.get("openclDiscovery.selfTest.status.0.count"));
        assertEquals("OPENCL:opencl-1", fields.get("openclDiscovery.selectedDeviceKey"));
        assertEquals("true", fields.get("openclDiscovery.selection.present"));
        assertEquals("NVIDIA CUDA", fields.get("openclDiscovery.selection.selected.platformName"));
        assertEquals("OpenCL 3.0 CUDA", fields.get("openclDiscovery.selection.selected.platformVersion"));
        assertEquals("NVIDIA CUDA", fields.get("openclDiscovery.selection.candidate.0.platformName"));
        assertTrue(fields.containsValue("javatogpu.device.preference"));
    }

    @Test
    void rendersUnavailableDiscoveryWithoutThrowing() {
        GpuRuntimeDeviceDiscoveryResult result = GpuRuntimeDeviceDiscoveryResult.unavailable(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "opencl-device-discovery-failed",
                new IllegalStateException("No OpenCL device found")
        );

        assertTrue(result.selectedDevice().isEmpty());
        assertTrue(result.toMarkdown().contains("Device discovery: unavailable"));
        assertTrue(result.toMarkdown().contains("First blocker: opencl-device-discovery-failed"));
        assertTrue(result.toMarkdown().contains("IllegalStateException: No OpenCL device found"));
    }

    @Test
    void rendersMultiBackendDiscoveryCatalogWithPlannedBackendStates() {
        GpuRuntimeDeviceProfile device = device(
                "opencl-0",
                "NVIDIA RTX",
                "NVIDIA",
                "NVIDIA CUDA",
                "OpenCL 3.0 CUDA",
                GpuDeviceClassTarget.DGPU,
                48
        );
        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                GpuRuntimeDevicePolicyContext.forBackendDiscovery(
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                                .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.DISABLED),
                        List.of(device)
                )
        );
        GpuRuntimeDeviceDiscoveryCatalog catalog = GpuRuntimeDeviceDiscoveryCatalog.of(List.of(
                GpuRuntimeDeviceDiscoveryResult.available(GpuBackendTarget.OPENCL, "OpenCL", List.of(device), selection),
                GpuRuntimeDeviceDiscovery.plannedUnavailable(GpuBackendTarget.CUDA)
        ));

        String markdown = catalog.toMarkdown();
        Map<String, String> fields = catalog.artifactFields("catalog");

        assertTrue(catalog.forBackend(GpuBackendTarget.OPENCL).orElseThrow().selectedDevice().isPresent());
        assertTrue(catalog.forBackend(GpuBackendTarget.CUDA).orElseThrow().selectedDevice().isEmpty());
        assertTrue(markdown.contains("Device discovery catalog: 2 backend(s)"));
        assertTrue(markdown.contains("Backend device discovery: CUDA (`CUDA`)"));
        assertTrue(markdown.contains("backend-device-discovery-not-implemented"));
        assertEquals("2", fields.get("catalog.backend.count"));
        assertEquals("OPENCL", fields.get("runtime.backend.target"));
        assertEquals("OpenCL", fields.get("runtime.backend.name"));
        assertEquals("NVIDIA RTX", fields.get("runtime.device.label"));
        assertEquals("OPENCL", fields.get("catalog.backend.0.backendTarget"));
        assertEquals("CUDA", fields.get("catalog.backend.1.backendTarget"));
        assertEquals("false", fields.get("catalog.backend.1.available"));
    }

    private static GpuRuntimeDeviceProfile device(
            String deviceId,
            String label,
            String vendor,
            String platformName,
            String platformVersion,
            GpuDeviceClassTarget deviceClass,
            long computeUnits
    ) {
        return GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                deviceId,
                label,
                vendor,
                "test-driver",
                "OpenCL 3.0 Test",
                platformName,
                platformVersion,
                deviceClass,
                computeUnits,
                8L * 1024L * 1024L * 1024L,
                64L * 1024L,
                1024L,
                1L,
                deviceClass == GpuDeviceClassTarget.IGPU,
                true,
                true,
                false
        );
    }
}
