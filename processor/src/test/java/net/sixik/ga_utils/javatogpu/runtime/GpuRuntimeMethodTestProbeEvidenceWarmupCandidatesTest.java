package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeMethodTestProbeEvidenceWarmupCandidatesTest {

    @Test
    void openClGpuDevicesUseRankedNonCpuDevicesAndRespectLimit() {
        GpuRuntimeDeviceProfile cpu = device("opencl-cpu", "CPU", GpuDeviceClassTarget.CPU);
        GpuRuntimeDeviceProfile integrated = device("opencl-igpu", "Integrated GPU", GpuDeviceClassTarget.IGPU);
        GpuRuntimeDeviceProfile discrete = device("opencl-dgpu", "Discrete GPU", GpuDeviceClassTarget.DGPU);
        GpuRuntimeDeviceSelection selection = new GpuRuntimeDeviceSelection(
                Optional.of(discrete),
                List.of(ranking(discrete, false), ranking(integrated, false), ranking(cpu, false)),
                List.of(),
                List.of(),
                true,
                false,
                "none",
                List.of()
        );
        GpuRuntimeDeviceDiscoveryResult discovery = GpuRuntimeDeviceDiscoveryResult.available(
                GpuBackendTarget.OPENCL,
                "OpenCL synthetic",
                List.of(cpu, integrated, discrete),
                selection
        );

        List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> candidates =
                GpuRuntimeMethodTestProbeEvidenceWarmupCandidates.openClGpuDevices(discovery, 2);

        assertEquals(2, candidates.size());
        assertEquals("opencl-dgpu", candidates.get(0).deviceProfile().deviceId());
        assertEquals("opencl-igpu", candidates.get(1).deviceProfile().deviceId());
        assertEquals(GpuRuntimeBackendOwnership.OWNED, candidates.get(0).ownership());
    }

    @Test
    void openClSelectedGpuDeviceUsesSelectedGpuAndFallsBackToFirstRankedGpu() {
        GpuRuntimeDeviceProfile cpu = device("opencl-cpu", "CPU", GpuDeviceClassTarget.CPU);
        GpuRuntimeDeviceProfile integrated = device("opencl-igpu", "Integrated GPU", GpuDeviceClassTarget.IGPU);
        GpuRuntimeDeviceProfile discrete = device("opencl-dgpu", "Discrete GPU", GpuDeviceClassTarget.DGPU);
        GpuRuntimeDeviceDiscoveryResult selectedGpuDiscovery = GpuRuntimeDeviceDiscoveryResult.available(
                GpuBackendTarget.OPENCL,
                "OpenCL synthetic",
                List.of(cpu, integrated, discrete),
                new GpuRuntimeDeviceSelection(
                        Optional.of(discrete),
                        List.of(ranking(integrated, false), ranking(discrete, false)),
                        List.of(),
                        List.of(),
                        true,
                        false,
                        "none",
                        List.of()
                )
        );
        GpuRuntimeDeviceDiscoveryResult selectedCpuDiscovery = GpuRuntimeDeviceDiscoveryResult.available(
                GpuBackendTarget.OPENCL,
                "OpenCL synthetic",
                List.of(cpu, integrated, discrete),
                new GpuRuntimeDeviceSelection(
                        Optional.of(cpu),
                        List.of(ranking(integrated, false), ranking(discrete, false), ranking(cpu, false)),
                        List.of(),
                        List.of(),
                        true,
                        false,
                        "none",
                        List.of()
                )
        );

        assertEquals("opencl-dgpu", GpuRuntimeMethodTestProbeEvidenceWarmupCandidates
                .openClSelectedGpuDevice(selectedGpuDiscovery)
                .get(0)
                .deviceProfile()
                .deviceId());
        assertEquals("opencl-igpu", GpuRuntimeMethodTestProbeEvidenceWarmupCandidates
                .openClSelectedGpuDevice(selectedCpuDiscovery)
                .get(0)
                .deviceProfile()
                .deviceId());
    }

    @Test
    void openClGpuDevicesReturnEmptyWhenUnavailableOrLimitIsZero() {
        GpuRuntimeDeviceDiscoveryResult unavailable = GpuRuntimeDeviceDiscoveryResult.unavailable(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "opencl-device-discovery-failed",
                new IllegalStateException("No OpenCL device found")
        );
        GpuRuntimeDeviceDiscoveryResult available = GpuRuntimeDeviceDiscoveryResult.available(
                GpuBackendTarget.OPENCL,
                "OpenCL synthetic",
                List.of(device("opencl-igpu", "Integrated GPU", GpuDeviceClassTarget.IGPU)),
                null
        );

        assertTrue(GpuRuntimeMethodTestProbeEvidenceWarmupCandidates.openClGpuDevices(unavailable).isEmpty());
        assertTrue(GpuRuntimeMethodTestProbeEvidenceWarmupCandidates.openClGpuDevices(available, 0).isEmpty());
    }

    private static GpuRuntimeDeviceCandidateRanking ranking(GpuRuntimeDeviceProfile profile, boolean rejected) {
        return new GpuRuntimeDeviceCandidateRanking(
                GpuRuntimeDevicePolicyContext.deviceKey(profile),
                profile,
                100,
                0,
                100,
                rejected,
                List.of()
        );
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
