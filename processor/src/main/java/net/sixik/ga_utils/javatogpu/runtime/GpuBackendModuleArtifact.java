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
        String lowererVersion
) {

    public GpuBackendModuleArtifact {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        kind = normalize(kind, "source");
        format = normalize(format, "unknown");
        source = source == null ? "" : source;
        resource = resource == null ? "" : resource;
        artifactVersion = normalize(artifactVersion, backendTarget.name().toLowerCase(java.util.Locale.ROOT) + ":" + kind + ":" + format);
        lowererVersion = normalize(lowererVersion, "unknown");
    }

    public static GpuBackendModuleArtifact openClSource(
            String source,
            String resource,
            String lowererVersion
    ) {
        return new GpuBackendModuleArtifact(
                GpuBackendTarget.OPENCL,
                "source",
                "opencl-c",
                source,
                resource,
                "opencl:source:opencl-c:v1",
                lowererVersion
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

    private static String normalize(String value, String fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return value == null || value.isBlank() ? fallback : value;
    }
}
