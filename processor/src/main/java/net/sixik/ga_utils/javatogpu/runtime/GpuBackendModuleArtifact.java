package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Objects;

public record GpuBackendModuleArtifact(
        GpuBackendTarget backendTarget,
        String kind,
        String format,
        String source,
        String resource,
        String artifactVersion,
        String lowererVersion,
        String sourceOrigin,
        boolean sourceAvailable,
        boolean binaryAvailable,
        String compileLogResource,
        String sourceMapResource,
        String runtimeLoadMode
) {

    public GpuBackendModuleArtifact(
            GpuBackendTarget backendTarget,
            String kind,
            String format,
            String source,
            String resource,
            String artifactVersion,
            String lowererVersion
    ) {
        this(
                backendTarget,
                kind,
                format,
                source,
                resource,
                artifactVersion,
                lowererVersion,
                "lowered-source",
                source != null && !source.isBlank(),
                false,
                "",
                "",
                "source-compile"
        );
    }

    public GpuBackendModuleArtifact(
            GpuBackendTarget backendTarget,
            String kind,
            String format,
            String source,
            String resource,
            String artifactVersion,
            String lowererVersion,
            String sourceOrigin,
            boolean sourceAvailable,
            boolean binaryAvailable,
            String sourceMapResource,
            String runtimeLoadMode
    ) {
        this(
                backendTarget,
                kind,
                format,
                source,
                resource,
                artifactVersion,
                lowererVersion,
                sourceOrigin,
                sourceAvailable,
                binaryAvailable,
                "",
                sourceMapResource,
                runtimeLoadMode
        );
    }

    public GpuBackendModuleArtifact {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        kind = normalize(kind, "source");
        format = GpuBackendModuleFormat.normalizeKey(format);
        source = source == null ? "" : source;
        resource = resource == null ? "" : resource;
        artifactVersion = normalize(artifactVersion, backendTarget.name().toLowerCase(java.util.Locale.ROOT) + ":" + kind + ":" + format);
        lowererVersion = normalize(lowererVersion, "unknown");
        sourceOrigin = normalize(sourceOrigin, "lowered-source");
        sourceAvailable = sourceAvailable || !source.isBlank();
        compileLogResource = compileLogResource == null ? "" : compileLogResource;
        sourceMapResource = sourceMapResource == null ? "" : sourceMapResource;
        runtimeLoadMode = normalize(runtimeLoadMode, "source-compile");
    }

    public static GpuBackendModuleArtifact openClSource(
            String source,
            String resource,
            String lowererVersion
    ) {
        return openClSource(source, resource, lowererVersion, "derived-opencl-source", "opencl-source-compile");
    }

    public static GpuBackendModuleArtifact openClSource(
            String source,
            String resource,
            String lowererVersion,
            String sourceOrigin,
            String runtimeLoadMode
    ) {
        return new GpuBackendModuleArtifact(
                GpuBackendTarget.OPENCL,
                "source",
                "opencl-c",
                source,
                resource,
                "opencl:source:opencl-c:v1",
                lowererVersion,
                sourceOrigin,
                source != null && !source.isBlank(),
                false,
                "",
                "",
                runtimeLoadMode
        );
    }

    public static GpuBackendModuleArtifact cudaSource(
            String source,
            String resource,
            String lowererVersion
    ) {
        return new GpuBackendModuleArtifact(
                GpuBackendTarget.CUDA,
                "source",
                GpuBackendModuleFormat.CUDA_C.key(),
                source,
                resource,
                "cuda:source:cuda-c:v1",
                lowererVersion,
                "derived-cuda-source",
                source != null && !source.isBlank(),
                false,
                "",
                "",
                "source-compile"
        );
    }

    public static GpuBackendModuleArtifact ptx(
            String source,
            String resource,
            String lowererVersion
    ) {
        return new GpuBackendModuleArtifact(
                GpuBackendTarget.CUDA,
                "intermediate",
                GpuBackendModuleFormat.PTX.key(),
                source,
                resource,
                "cuda:intermediate:ptx:v1",
                lowererVersion,
                "derived-ptx",
                source != null && !source.isBlank(),
                true,
                "",
                "",
                "binary-or-ptx-load"
        );
    }

    public static GpuBackendModuleArtifact spirV(
            String resource,
            String lowererVersion,
            boolean binaryAvailable
    ) {
        return new GpuBackendModuleArtifact(
                GpuBackendTarget.VULKAN,
                "binary",
                GpuBackendModuleFormat.SPIR_V.key(),
                "",
                resource,
                "vulkan:binary:spir-v:v1",
                lowererVersion,
                "derived-spir-v",
                false,
                binaryAvailable,
                "",
                "",
                "binary-load"
        );
    }

    public static GpuBackendModuleArtifact unknown() {
        return new GpuBackendModuleArtifact(
                GpuBackendTarget.UNKNOWN,
                "unknown",
                "unknown",
                "",
                "",
                "unknown:unknown:unknown",
                "unknown",
                "unknown",
                false,
                false,
                "",
                "",
                "unknown"
        );
    }

    public String requireSource() {
        if (source.isBlank()) {
            throw new IllegalStateException(
                    "Backend module artifact " + backendTarget + "/" + format + " does not contain source text"
            );
        }
        return source;
    }

    public GpuBackendModuleFormat moduleFormat() {
        return GpuBackendModuleFormat.fromKey(format);
    }

    public boolean sourceLikeFormat() {
        return moduleFormat().sourceLike();
    }

    public boolean binaryLikeFormat() {
        return moduleFormat().binaryLike();
    }

    public boolean formatMatchesBackendTarget() {
        return moduleFormat().hasDefaultTarget(backendTarget);
    }

    private static String normalize(String value, String fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return value == null || value.isBlank() ? fallback : value;
    }
}
