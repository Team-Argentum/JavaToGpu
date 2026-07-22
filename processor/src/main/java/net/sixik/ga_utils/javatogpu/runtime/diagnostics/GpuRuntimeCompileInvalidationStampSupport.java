package net.sixik.ga_utils.javatogpu.runtime.diagnostics;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileInvalidationStamp;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;

import java.util.Optional;

/**
 * Domain implementation support for compile-cache invalidation stamp construction.
 */
public final class GpuRuntimeCompileInvalidationStampSupport {

    private GpuRuntimeCompileInvalidationStampSupport() {
    }

    /**
     * Builds the version stamp used to invalidate compiled runtime artifacts after pipeline upgrades.
     */
    public static GpuRuntimeCompileInvalidationStamp from(
            GpuRuntimeCompileRequest request,
            GpuBackendModuleArtifact moduleArtifact,
            String optimizerPipelineVersion
    ) {
        Optional<IrGpuArtifact> artifact = request == null ? Optional.empty() : request.irGpuArtifact();
        GpuBackendModuleArtifact backendArtifact = moduleArtifact == null
                ? GpuBackendModuleArtifact.unknown()
                : moduleArtifact;
        return artifact
                .map(irGpuArtifact -> from(irGpuArtifact, backendArtifact, optimizerPipelineVersion))
                .orElseGet(() -> new GpuRuntimeCompileInvalidationStamp(
                        GpuRuntimeCompileInvalidationStamp.NO_IR_FORMAT,
                        0,
                        GpuRuntimeCompileInvalidationStamp.NO_COMPILER_ARTIFACT,
                        GpuRuntimeCompileInvalidationStamp.NO_SOURCE_FRONTEND,
                        backendArtifact.artifactVersion(),
                        backendArtifact.lowererVersion(),
                        optimizerPipelineVersion
                ));
    }

    private static GpuRuntimeCompileInvalidationStamp from(
            IrGpuArtifact artifact,
            GpuBackendModuleArtifact moduleArtifact,
            String optimizerPipelineVersion
    ) {
        return new GpuRuntimeCompileInvalidationStamp(
                artifact.header().format(),
                artifact.header().schemaVersion(),
                artifact.header().compilerArtifact(),
                artifact.header().sourceFrontend(),
                moduleArtifact.artifactVersion(),
                moduleArtifact.lowererVersion(),
                optimizerPipelineVersion
        );
    }
}
