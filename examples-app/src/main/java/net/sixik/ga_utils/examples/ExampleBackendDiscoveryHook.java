package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendDiscoveryContributor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Example read-only backend discovery hook loaded through ServiceLoader.
 */
public final class ExampleBackendDiscoveryHook implements GpuBackendDiscoveryContributor {

    @Override
    public String extensionId() {
        return "examples.backend-hook.discovery";
    }

    @Override
    public String extensionVersion() {
        return "1";
    }

    @Override
    public int extensionOrder() {
        return 30_100;
    }

    @Override
    public Set<GpuBackendTarget> backendTargets() {
        return Set.of(GpuBackendTarget.OPENCL, GpuBackendTarget.CUDA);
    }

    @Override
    public GpuRuntimeDeviceDiscoveryResult afterDiscovery(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDeviceDiscoveryResult discoveryResult
    ) {
        return discoveryResult;
    }

    @Override
    public Map<String, String> discoveryFacts(GpuRuntimeDeviceDiscoveryResult discoveryResult) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("examples.backendHook.discovery.backendTarget", discoveryResult.backendTarget().name());
        fields.put("examples.backendHook.discovery.available", Boolean.toString(discoveryResult.discoveryAvailable()));
        fields.put("examples.backendHook.discovery.device.count", Integer.toString(discoveryResult.discoveredDevices().size()));
        fields.put("examples.backendHook.discovery.selectedDeviceKey", discoveryResult.selectedDevice()
                .map(GpuRuntimeDevicePolicyContext::deviceKey)
                .orElse("none"));
        return Collections.unmodifiableMap(fields);
    }
}
