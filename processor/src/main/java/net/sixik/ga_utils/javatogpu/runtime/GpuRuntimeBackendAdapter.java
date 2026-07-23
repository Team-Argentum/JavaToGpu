package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backend adapter contract that ties runtime selection, device discovery, and lowering together.
 *
 * <p>This is intentionally one level above {@link GpuRuntimeBackend}. A runtime backend executes invocations after it
 * has been selected. A backend adapter describes how that backend family participates in the public runtime pipeline:
 * catalog entry, native device discovery, backend lowerer, and diagnostics.</p>
 */
public interface GpuRuntimeBackendAdapter {

    GpuBackendTarget backendTarget();

    String backendName();

    GpuRuntimeBackendCatalogEntry catalogEntry();

    GpuRuntimeDeviceDiscoveryResult discoverDevices(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry
    );

    GpuBackendLowerer lowerer();

    default boolean productionAdapter() {
        return catalogEntry().productionAdapter();
    }

    default String diagnostic() {
        return catalogEntry().diagnostic();
    }

    default Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtimeBackendAdapter" : prefix.trim();
        LinkedHashMap<String, String> fields = GpuRuntimeLifecycleFields.backendAdapterFields(this);
        GpuRuntimeBackendCatalogEntry entry = catalogEntry();
        GpuBackendLowerer backendLowerer = lowerer();
        fields.put(normalizedPrefix + ".backendTarget", backendTarget().name());
        fields.put(normalizedPrefix + ".backendName", backendName());
        fields.put(normalizedPrefix + ".productionAdapter", Boolean.toString(entry.productionAdapter()));
        fields.put(normalizedPrefix + ".ownership", entry.ownership().name());
        fields.put(normalizedPrefix + ".diagnostic", diagnostic());
        fields.put(normalizedPrefix + ".lowerer.id", backendLowerer.extensionId());
        fields.put(normalizedPrefix + ".lowerer.version", backendLowerer.lowererVersion());
        return Collections.unmodifiableMap(fields);
    }
}
