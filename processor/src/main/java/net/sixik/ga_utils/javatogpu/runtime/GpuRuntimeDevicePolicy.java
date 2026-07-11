package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtension;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Set;

/**
 * Read-only policy hook for device ranking, capability facts, quirks, and compile-option validation.
 */
@FunctionalInterface
public interface GpuRuntimeDevicePolicy extends GpuExtension {

    GpuRuntimeDevicePolicyDecision evaluate(GpuRuntimeDevicePolicyContext context);

    default String policyId() {
        return getClass().getName();
    }

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
