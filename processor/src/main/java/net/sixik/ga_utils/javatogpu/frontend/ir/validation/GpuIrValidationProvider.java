package net.sixik.ga_utils.javatogpu.frontend.ir.validation;

import net.sixik.ga_utils.javatogpu.extension.GpuExtension;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Set;

/**
 * Optional ServiceLoader extension point for read-only IR validation modules.
 */
public interface GpuIrValidationProvider extends GpuExtension {
    void validate(GpuIrValidationRequest request);

    @Override
    default Set<GpuExtensionCapability> extensionCapabilities() {
        return Set.of(GpuExtensionCapability.IR_VALIDATION);
    }

    @Override
    default GpuExtensionPhase extensionPhase() {
        return GpuExtensionPhase.IR_VALIDATION;
    }

    @Override
    default GpuExtensionPermission extensionPermission() {
        return GpuExtensionPermission.READ_ONLY;
    }
}
