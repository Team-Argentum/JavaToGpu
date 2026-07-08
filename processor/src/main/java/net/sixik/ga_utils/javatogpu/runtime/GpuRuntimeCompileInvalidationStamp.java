package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

import java.util.Optional;

/**
 * Version stamp used to invalidate compiled runtime artifacts after pipeline upgrades.
 *
 * <p>The stamp intentionally stays small and string-based so future CUDA/Vulkan/Metal backends can add their own
 * versioning without changing the cache-key shape again.</p>
 */
public record GpuRuntimeCompileInvalidationStamp(
        String irFormat,
        int irSchemaVersion,
        String compilerArtifact,
        String sourceFrontend,
        String backendArtifactVersion,
        String backendLowererVersion,
        String optimizerPipelineVersion
) {

    public static final String NO_IR_FORMAT = "irgpu:none";
    public static final String NO_COMPILER_ARTIFACT = "compiler:none";
    public static final String NO_SOURCE_FRONTEND = "frontend:none";
    public static final String NO_OPTIMIZER_PIPELINE = "optimizer:none";

    public GpuRuntimeCompileInvalidationStamp {
        irFormat = normalize(irFormat, NO_IR_FORMAT);
        compilerArtifact = normalize(compilerArtifact, NO_COMPILER_ARTIFACT);
        sourceFrontend = normalize(sourceFrontend, NO_SOURCE_FRONTEND);
        backendArtifactVersion = normalize(backendArtifactVersion, "backend-artifact:unknown");
        backendLowererVersion = normalize(backendLowererVersion, "backend-lowerer:unknown");
        optimizerPipelineVersion = normalize(optimizerPipelineVersion, NO_OPTIMIZER_PIPELINE);
    }

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
                        NO_IR_FORMAT,
                        0,
                        NO_COMPILER_ARTIFACT,
                        NO_SOURCE_FRONTEND,
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

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
