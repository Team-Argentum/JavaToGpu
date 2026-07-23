package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtension;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Set;

/**
 * Read-only policy hook for device ranking, capability facts, quirks, and compile-option validation.
 *
 * <p>Implement this service when backend/device selection needs application- or vendor-specific evidence without
 * forking the runtime. Policies are discovered through ServiceLoader and should return explainable decisions rather
 * than throwing for ordinary rejection cases.</p>
 */
@FunctionalInterface
public interface GpuRuntimeDevicePolicy extends GpuExtension {

    /**
     * Evaluates one device candidate and returns ranking/rejection evidence.
     */
    GpuRuntimeDevicePolicyDecision evaluate(GpuRuntimeDevicePolicyContext context);

    /**
     * Stable id used in diagnostics and extension catalogs.
     */
    default String policyId() {
        return getClass().getName();
    }

    /**
     * Policy contract version emitted in diagnostics and artifact fields.
     */
    default String policyVersion() {
        return "1";
    }

    @Override
    default String extensionId() {
        return policyId();
    }

    @Override
    default String extensionVersion() {
        return policyVersion();
    }

    @Override
    default Set<GpuExtensionCapability> extensionCapabilities() {
        return Set.of(GpuExtensionCapability.DEVICE_SELECTION_POLICY);
    }

    @Override
    default GpuExtensionPhase extensionPhase() {
        return GpuExtensionPhase.DEVICE_SELECTION;
    }

    @Override
    default GpuExtensionPermission extensionPermission() {
        return GpuExtensionPermission.READ_ONLY;
    }
}
