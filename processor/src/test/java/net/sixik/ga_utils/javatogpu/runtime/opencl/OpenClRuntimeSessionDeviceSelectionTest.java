package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCompatibilityDevicePolicy;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicy;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyContext;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyDecision;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class OpenClRuntimeSessionDeviceSelectionTest {

    @Test
    void mapsPolicySelectionBackToTheExactDiscoveredDeviceIndex() {
        GpuRuntimeDeviceProfile first = profile("opencl-0");
        GpuRuntimeDeviceProfile second = profile("opencl-1");
        GpuRuntimeDevicePolicy preferSecond = new GpuRuntimeDevicePolicy() {
            @Override
            public GpuRuntimeDevicePolicyDecision evaluate(GpuRuntimeDevicePolicyContext context) {
                return new GpuRuntimeDevicePolicyDecision(
                        policyId(),
                        policyVersion(),
                        Map.of("OPENCL:opencl-1", 1_000_000),
                        Set.of(),
                        Map.of(),
                        List.of(),
                        true,
                        List.of(),
                        List.of("prefer the second identical adapter")
                );
            }

            @Override
            public String policyId() {
                return "test.prefer-second";
            }
        };
        GpuRuntimeDevicePolicyRegistry registry = GpuRuntimeDevicePolicyRegistry.of(List.of(
                new GpuRuntimeBackendCompatibilityDevicePolicy(),
                preferSecond
        ));

        List<GpuRuntimeDeviceProfile> profiles = List.of(first, second);
        GpuRuntimeDeviceSelection selection = OpenClRuntimeSession.selectDevice(registry, profiles);

        assertSame(second, selection.selectedDevice().orElseThrow());
        assertEquals(1, OpenClRuntimeSession.selectedDeviceIndex(profiles, selection));
    }

    private static GpuRuntimeDeviceProfile profile(String deviceId) {
        return GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                deviceId,
                "Identical GPU",
                "Vendor",
                "driver",
                "OpenCL 3.0",
                GpuDeviceClassTarget.DGPU,
                32L,
                8L * 1024L * 1024L * 1024L,
                64L * 1024L,
                1024L,
                1L,
                false,
                true,
                true,
                false
        );
    }
}
