package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestProbeEvidenceWarmupCandidate;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClMethodTestProbeEvidenceSelectionExampleTest {

    @Test
    void rendersBlockedReportWhenOpenClDiscoveryIsUnavailable() throws IOException {
        Path cacheDirectory = Files.createTempDirectory("javatogpu-opencl-method-test-selection-example");
        GpuRuntimeCompileOptions baseOptions = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL);
        GpuRuntimeDeviceDiscoveryResult discovery = GpuRuntimeDeviceDiscoveryResult.unavailable(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "opencl-device-discovery-failed",
                new IllegalStateException("No OpenCL device found")
        );

        String output = OpenClMethodTestProbeEvidenceSelectionExample.renderOpenClEvidenceSelection(
                cacheDirectory,
                MethodTestProbeExample.descriptor(),
                OpenClMethodTestProbeEvidenceSelectionExample.class.getClassLoader(),
                discovery,
                baseOptions
        );

        assertTrue(output.contains("Real OpenCL method-test probe evidence selection example"));
        assertTrue(output.contains("Warm-up candidate count: 0"));
        assertTrue(output.contains("Device discovery: unavailable"));
        assertTrue(output.contains("Method test probe evidence selection: blocked"));
        assertTrue(output.contains("First blocker: opencl-device-discovery-failed"));
        assertTrue(output.contains("selection remains cache-only"));
    }

    @Test
    void openClWarmupCandidatesPreferGpuDevicesAndRespectLimit() {
        GpuRuntimeDeviceProfile cpu = device("opencl-cpu", "CPU", GpuDeviceClassTarget.CPU);
        GpuRuntimeDeviceProfile integrated = device("opencl-igpu", "Integrated GPU", GpuDeviceClassTarget.IGPU);
        GpuRuntimeDeviceProfile discrete = device("opencl-dgpu", "Discrete GPU", GpuDeviceClassTarget.DGPU);
        GpuRuntimeDeviceDiscoveryResult discovery = GpuRuntimeDeviceDiscoveryResult.available(
                GpuBackendTarget.OPENCL,
                "OpenCL synthetic",
                List.of(cpu, integrated, discrete),
                null
        );

        List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> candidates =
                OpenClMethodTestProbeEvidenceSelectionExample.openClWarmupCandidates(discovery, 1);

        assertEquals(1, candidates.size());
        assertEquals("opencl-igpu", candidates.get(0).deviceProfile().deviceId());
    }

    private static GpuRuntimeDeviceProfile device(
            String deviceId,
            String deviceLabel,
            GpuDeviceClassTarget deviceClass
    ) {
        return GpuRuntimeDeviceProfile.openCl(
                "OpenCL synthetic",
                deviceId,
                deviceLabel,
                "Example Vendor",
                "example-driver",
                "OpenCL 3.0 Example",
                deviceClass,
                8,
                1024L * 1024L * 1024L,
                64L * 1024L,
                256,
                1,
                false,
                true,
                false,
                false
        );
    }
}
