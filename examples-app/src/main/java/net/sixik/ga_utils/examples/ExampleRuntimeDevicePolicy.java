package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicy;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyContext;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyDecision;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Example read-only device policy discovered through ServiceLoader.
 */
public final class ExampleRuntimeDevicePolicy implements GpuRuntimeDevicePolicy {

    @Override
    public GpuRuntimeDevicePolicyDecision evaluate(GpuRuntimeDevicePolicyContext context) {
        LinkedHashMap<String, Integer> scoreAdjustments = new LinkedHashMap<>();
        for (GpuRuntimeDeviceProfile candidate : context.candidates()) {
            if (candidate.deviceClass() == GpuDeviceClassTarget.DGPU
                    || candidate.vendor().toLowerCase(java.util.Locale.ROOT).contains("nvidia")) {
                scoreAdjustments.put(GpuRuntimeDevicePolicyContext.deviceKey(candidate), 5_000);
            }
        }
        return new GpuRuntimeDevicePolicyDecision(
                policyId(),
                policyVersion(),
                scoreAdjustments,
                Set.of(),
                Map.of("examples.devicePolicy.scoreBoost", "dgpu-or-nvidia"),
                List.of(),
                true,
                List.of(),
                scoreAdjustments.isEmpty()
                        ? List.of("example device policy found no dGPU/NVIDIA candidate")
                        : List.of("example device policy boosted dGPU/NVIDIA candidates")
        );
    }

    @Override
    public String policyId() {
        return "examples.device-policy.dgpu-score-boost";
    }

    @Override
    public String policyVersion() {
        return "1";
    }

    @Override
    public int extensionOrder() {
        return 30_500;
    }
}
