package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

public interface GpuBackendLowerer {

    GpuBackendTarget backendTarget();

    String lowererVersion();

    default GpuBackendSourceSelectionPlan sourceSelectionPlan(GpuRuntimeCompileRequest compileRequest) {
        return GpuBackendSourceSelectionPlan.descriptorSource(
                backendTarget(),
                "unknown",
                "Backend lowerer has not exposed a specialized source-selection plan"
        );
    }

    GpuBackendModuleArtifact lower(GpuRuntimeCompileRequest compileRequest);
}
