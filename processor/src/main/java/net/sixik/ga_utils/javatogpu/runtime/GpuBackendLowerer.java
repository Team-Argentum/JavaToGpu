package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

public interface GpuBackendLowerer {

    GpuBackendTarget backendTarget();

    String lowererVersion();

    GpuBackendModuleArtifact lower(GpuRuntimeCompileRequest compileRequest);
}
