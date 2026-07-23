package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.List;

/**
 * SPI boundary for backend-native compilation.
 *
 * <p>Implementations consume a portable compile request plus a backend module artifact and return a backend-specific
 * compiled handle. They should throw clear runtime exceptions for hard failures; callers that need typed receipts can
 * run through {@link GpuBackendExecutionPipeline#executeSafely}.</p>
 */
public interface GpuBackendKernelCompiler<C extends GpuBackendCompiledKernel> {

    /**
     * Backend family compiled by this stage.
     */
    GpuBackendTarget backendTarget();

    /**
     * Compiles or loads the backend module and returns a closeable compiled handle.
     */
    C compile(GpuRuntimeCompileRequest compileRequest, GpuBackendModuleArtifact moduleArtifact);

    /**
     * Builds the portable compile receipt for a returned compiled handle.
     */
    default GpuBackendCompilationResult compilationResult(
            C compiledKernel,
            GpuBackendLoweringResult loweringResult
    ) {
        GpuRuntimeCompileArtifactSnapshot artifactSnapshot = compiledKernel == null
                ? null
                : compiledKernel.artifactSnapshot();
        GpuBackendModuleArtifact moduleArtifact = compiledKernel == null
                ? GpuBackendModuleArtifact.unknown()
                : compiledKernel.moduleArtifact();
        return GpuBackendCompilationResult.succeeded(
                loweringResult,
                GpuRuntimeBackendCompilationSummary.from(
                        moduleArtifact,
                        artifactSnapshot,
                        compiledKernel == null ? "" : compiledKernel.cacheKey()
                ),
                compiledKernel == null ? "" : compiledKernel.cacheKey(),
                List.of("backend compiler returned a compiled kernel handle")
        );
    }
}
