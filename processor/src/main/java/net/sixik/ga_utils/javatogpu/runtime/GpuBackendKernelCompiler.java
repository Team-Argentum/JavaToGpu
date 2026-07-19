package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.List;

/**
 * SPI boundary for backend-native compilation.
 */
public interface GpuBackendKernelCompiler<C extends GpuBackendCompiledKernel> {

    GpuBackendTarget backendTarget();

    C compile(GpuRuntimeCompileRequest compileRequest, GpuBackendModuleArtifact moduleArtifact);

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
