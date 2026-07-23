package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Objects;

/**
 * Backend module selected for compilation or direct loading.
 *
 * <p>The artifact may contain source text, point to a generated resource, describe a binary/intermediate payload, or be
 * {@link #unknown()} when a backend stage did not reach module selection. This record intentionally carries both
 * backend-neutral facts and backend-specific format keys so OpenCL, CUDA, SPIR-V, and future adapters can share the same
 * lowering/compile pipeline receipts.</p>
 *
 * @param backendTarget backend family this module belongs to
 * @param kind broad module kind such as {@code source}, {@code intermediate}, {@code binary}, or {@code unknown}
 * @param format canonical module format key, for example {@code opencl-c}, {@code cuda-c}, {@code ptx}, or {@code cubin}
 * @param source in-memory source/intermediate text, or blank for binary/resource-only artifacts
 * @param resource classpath or filesystem resource path for the module payload, when available
 * @param artifactVersion stable artifact contract version emitted by the lowerer/compiler bridge
 * @param lowererVersion version/id of the lowerer that produced this module
 * @param sourceOrigin explanation of where the module came from, such as descriptor source or lowered source
 * @param sourceAvailable whether source text is available for review or source compilation
 * @param binaryAvailable whether binary/intermediate bytes are available for direct loading
 * @param compileLogResource optional resource path for native compiler logs
 * @param sourceMapResource optional resource path for backend source maps
 * @param runtimeLoadMode intended runtime mode, such as source compilation or binary load
 */
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

    /**
     * Creates an OpenCL C source artifact from lowered or descriptor-provided source text.
     */
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

    /**
     * Creates a CUDA C source artifact for staged CUDA compilation.
     */
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

    /**
     * Creates a PTX intermediate artifact for CUDA Driver API loading or later native assembly.
     */
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

    public static GpuBackendModuleArtifact cubin(
            String resource,
            String lowererVersion,
            boolean binaryAvailable
    ) {
        return cudaBinary(GpuBackendModuleFormat.CUBIN.key(), resource, lowererVersion, binaryAvailable);
    }

    public static GpuBackendModuleArtifact fatbin(
            String resource,
            String lowererVersion,
            boolean binaryAvailable
    ) {
        return cudaBinary(GpuBackendModuleFormat.FATBIN.key(), resource, lowererVersion, binaryAvailable);
    }

    /**
     * Creates a CUDA binary artifact receipt for a canonical binary format key.
     */
    public static GpuBackendModuleArtifact cudaBinary(
            String format,
            String resource,
            String lowererVersion,
            boolean binaryAvailable
    ) {
        String normalizedFormat = GpuBackendModuleFormat.normalizeKey(format);
        return new GpuBackendModuleArtifact(
                GpuBackendTarget.CUDA,
                "binary",
                normalizedFormat,
                "",
                resource,
                "cuda:binary:" + normalizedFormat + ":v1",
                lowererVersion,
                "derived-cuda-binary",
                false,
                binaryAvailable,
                "",
                "",
                "binary-load"
        );
    }

    /**
     * Creates a SPIR-V binary artifact receipt for future Vulkan/OpenCL paths.
     */
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

    /**
     * Returns an explicit placeholder for stages that did not produce a module artifact.
     */
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

    /**
     * Returns source text or fails with a clear message when this artifact is not source-backed.
     */
    public String requireSource() {
        if (source.isBlank()) {
            throw new IllegalStateException(
                    "Backend module artifact " + backendTarget + "/" + format + " does not contain source text"
            );
        }
        return source;
    }

    /**
     * Returns the canonical enum value for {@link #format()}.
     */
    public GpuBackendModuleFormat moduleFormat() {
        return GpuBackendModuleFormat.fromKey(format);
    }

    /**
     * Returns whether the module format normally carries textual source/intermediate content.
     */
    public boolean sourceLikeFormat() {
        return moduleFormat().sourceLike();
    }

    /**
     * Returns whether the module format normally carries binary or directly loadable content.
     */
    public boolean binaryLikeFormat() {
        return moduleFormat().binaryLike();
    }

    /**
     * Returns whether the format is normally valid for this backend target.
     */
    public boolean formatMatchesBackendTarget() {
        return moduleFormat().hasDefaultTarget(backendTarget);
    }

    private static String normalize(String value, String fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return value == null || value.isBlank() ? fallback : value;
    }
}
