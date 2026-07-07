package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

/**
 * Backend-neutral entrypoint for future IrGpu -> backend source reconstruction.
 */
public interface GpuBackendSourceReconstructor {

    GpuBackendTarget backendTarget();

    String version();

    GpuBackendSourceReconstructionResult reconstruct(GpuRuntimeCompileRequest compileRequest);
}
