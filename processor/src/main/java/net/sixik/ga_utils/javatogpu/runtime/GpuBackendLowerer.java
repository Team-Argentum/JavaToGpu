package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtension;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Set;

public interface GpuBackendLowerer extends GpuExtension {

    GpuBackendTarget backendTarget();

    String lowererVersion();

    @Override
    default String extensionId() {
        return "backend-lowerer:" + backendTarget().name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    default String extensionVersion() {
        return lowererVersion();
    }

    @Override
    default Set<GpuExtensionCapability> extensionCapabilities() {
        return Set.of(GpuExtensionCapability.BACKEND_LOWERING);
    }

    @Override
    default GpuExtensionPhase extensionPhase() {
        return GpuExtensionPhase.BACKEND_LOWERING;
    }

    @Override
    default GpuExtensionPermission extensionPermission() {
        return GpuExtensionPermission.PRODUCTION_AFFECTING;
    }

    default GpuBackendSourceSelectionPlan sourceSelectionPlan(GpuRuntimeCompileRequest compileRequest) {
        return GpuBackendSourceSelectionPlan.descriptorSource(
                backendTarget(),
                "unknown",
                "Backend lowerer has not exposed a specialized source-selection plan"
        );
    }

    default GpuBackendLoweringResult lowerWithStageResult(GpuRuntimeCompileRequest compileRequest) {
        GpuBackendSourceSelectionPlan plan = sourceSelectionPlan(compileRequest);
        GpuBackendModuleArtifact artifact = lower(compileRequest);
        return GpuBackendLoweringResult.succeeded(
                artifact,
                plan,
                java.util.List.of("Backend lowerer " + extensionId() + " completed the lower stage")
        );
    }

    GpuBackendModuleArtifact lower(GpuRuntimeCompileRequest compileRequest);
}
