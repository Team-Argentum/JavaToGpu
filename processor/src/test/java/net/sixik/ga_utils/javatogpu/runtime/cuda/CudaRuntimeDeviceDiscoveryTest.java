package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyContext;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelection;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaRuntimeDeviceDiscoveryTest {

    @Test
    void parsesRichNvidiaSmiCsvIntoCudaProfiles() {
        String csv = String.join(System.lineSeparator(),
                "0, GPU-aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee, NVIDIA GeForce RTX 3060, 12288, 551.86, 8.6",
                "1, GPU-ffffffff-1111-2222-3333-444444444444, NVIDIA GeForce RTX 5070, 12227, 576.52, 12.0"
        );

        List<GpuRuntimeDeviceProfile> profiles = CudaRuntimeDeviceDiscovery.parseNvidiaSmiCsv(
                csv,
                true,
                "12.4"
        );

        assertEquals(2, profiles.size());
        GpuRuntimeDeviceProfile first = profiles.get(0);
        assertEquals(GpuBackendTarget.CUDA, first.backendTarget());
        assertEquals("CUDA", first.backendName());
        assertEquals("GPU-aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", first.deviceId());
        assertEquals("NVIDIA GeForce RTX 3060", first.deviceLabel());
        assertEquals("NVIDIA", first.vendor());
        assertEquals("551.86", first.driverVersion());
        assertEquals("CUDA 12.4, compute capability 8.6", first.apiVersionText());
        assertEquals("12.4", first.cudaRuntimeVersion());
        assertEquals("8.6", first.cudaComputeCapability());
        assertEquals(GpuDeviceClassTarget.DGPU, first.deviceClass());
        assertEquals(12_884_901_888L, first.globalMemoryBytes());
        assertEquals("NVIDIA CUDA", first.platformName());
        assertEquals("driver 551.86, CUDA 12.4", first.platformVersion());

        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                GpuRuntimeDevicePolicyContext.forBackendDiscovery(
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA)
                                .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.DISABLED),
                        List.of(first)
                )
        );
        Map<String, String> fields = GpuRuntimeDeviceDiscoveryResult.available(
                GpuBackendTarget.CUDA,
                "CUDA",
                List.of(first),
                selection
        ).artifactFields("cudaDiscovery");

        assertEquals("12.4", fields.get("runtime.device.cuda.runtimeVersion"));
        assertEquals("8.6", fields.get("runtime.device.cuda.computeCapability"));
        assertEquals("12.4", fields.get("cudaDiscovery.device.0.cuda.runtimeVersion"));
        assertEquals("8.6", fields.get("cudaDiscovery.device.0.cuda.computeCapability"));
    }

    @Test
    void parsesBasicNvidiaSmiCsvAndKeepsDevicePolicySelectionBackendNeutral() {
        List<GpuRuntimeDeviceProfile> profiles = CudaRuntimeDeviceDiscovery.parseNvidiaSmiCsv(
                "0, unknown, NVIDIA GeForce RTX 3060, 12288 MiB, 551.86",
                false,
                "unknown"
        );

        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                GpuRuntimeDevicePolicyContext.forBackendDiscovery(
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA)
                                .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.DISABLED),
                        profiles
                )
        );

        assertEquals(1, profiles.size());
        assertEquals("cuda-0", profiles.get(0).deviceId());
        assertEquals("CUDA unknown", profiles.get(0).apiVersionText());
        assertEquals("unknown", profiles.get(0).cudaRuntimeVersion());
        assertEquals("unknown", profiles.get(0).cudaComputeCapability());
        assertEquals("driver 551.86", profiles.get(0).platformVersion());
        assertTrue(selection.selectedDevice().isPresent());
        assertEquals("CUDA:cuda-0", selection.selectedDevice()
                .map(net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyContext::deviceKey)
                .orElseThrow());
    }

    @Test
    void skipsMalformedNvidiaSmiRowsAndParsesOverviewCudaVersion() {
        List<GpuRuntimeDeviceProfile> profiles = CudaRuntimeDeviceDiscovery.parseNvidiaSmiCsv(
                String.join(System.lineSeparator(),
                        "index, uuid, name, memory.total, driver_version, compute_cap",
                        "not enough columns",
                        "0, GPU-valid, NVIDIA RTX, 8192, 550.54, 8.9"
                ),
                true,
                "12.5"
        );

        assertEquals(1, profiles.size());
        assertEquals("GPU-valid", profiles.get(0).deviceId());
        assertEquals("12.5", CudaRuntimeDeviceDiscovery.parseCudaVersion(
                "| NVIDIA-SMI 550.54 Driver Version: 550.54 CUDA Version: 12.5 |"
        ).orElseThrow());
        assertTrue(CudaRuntimeDeviceDiscovery.parseCudaVersion("no cuda header").isEmpty());
    }
}
