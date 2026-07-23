package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Objects;
import java.util.Optional;

/**
 * Inspectable runtime backend catalog entry.
 *
 * <p>The entry carries lightweight metadata for discovery/explanation plus the factory used by selection when the
 * entry is appended to a {@link GpuRuntimeBackendPolicy}. Creating the backend is intentionally lazy, so applications
 * can list available or planned backend families without touching native runtime state.</p>
 */
public record GpuRuntimeBackendCatalogEntry(
        GpuBackendTarget backendTarget,
        String backendName,
        GpuRuntimeBackendFactory factory,
        GpuRuntimeBackendOwnership ownership,
        boolean productionAdapter,
        Optional<GpuRuntimeBackendExecutionSupport> executionSupport,
        String diagnostic
) {

    public GpuRuntimeBackendCatalogEntry(
            GpuBackendTarget backendTarget,
            String backendName,
            GpuRuntimeBackendFactory factory,
            GpuRuntimeBackendOwnership ownership,
            boolean productionAdapter,
            String diagnostic
    ) {
        this(backendTarget, backendName, factory, ownership, productionAdapter, Optional.empty(), diagnostic);
    }

    public GpuRuntimeBackendCatalogEntry {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        backendName = backendName == null || backendName.isBlank() ? backendTarget.name() : backendName;
        factory = Objects.requireNonNull(factory, "factory");
        ownership = ownership == null ? GpuRuntimeBackendOwnership.OWNED : ownership;
        executionSupport = executionSupport == null ? Optional.empty() : executionSupport;
        diagnostic = diagnostic == null || diagnostic.isBlank() ? "none" : diagnostic;
    }

    /**
     * Creates an owned catalog entry backed by a lazy backend factory.
     */
    public static GpuRuntimeBackendCatalogEntry owned(
            GpuBackendTarget backendTarget,
            String backendName,
            GpuRuntimeBackendFactory factory,
            boolean productionAdapter,
            String diagnostic
    ) {
        return new GpuRuntimeBackendCatalogEntry(
                backendTarget,
                backendName,
                factory,
                GpuRuntimeBackendOwnership.OWNED,
                productionAdapter,
                Optional.empty(),
                diagnostic
        );
    }

    public static GpuRuntimeBackendCatalogEntry owned(
            GpuBackendTarget backendTarget,
            String backendName,
            GpuRuntimeBackendFactory factory,
            boolean productionAdapter,
            GpuRuntimeBackendExecutionSupport executionSupport,
            String diagnostic
    ) {
        return new GpuRuntimeBackendCatalogEntry(
                backendTarget,
                backendName,
                factory,
                GpuRuntimeBackendOwnership.OWNED,
                productionAdapter,
                Optional.ofNullable(executionSupport),
                diagnostic
        );
    }

    /**
     * Creates a borrowed catalog entry around an existing caller-managed backend instance.
     */
    public static GpuRuntimeBackendCatalogEntry borrowed(
            String backendName,
            GpuRuntimeBackend backend,
            boolean productionAdapter,
            String diagnostic
    ) {
        Objects.requireNonNull(backend, "backend");
        return new GpuRuntimeBackendCatalogEntry(
                backend.backendTarget(),
                backendName,
                () -> backend,
                GpuRuntimeBackendOwnership.BORROWED,
                productionAdapter,
                Optional.empty(),
                diagnostic
        );
    }

    public GpuRuntimeBackendCatalogEntry withExecutionSupport(GpuRuntimeBackendExecutionSupport support) {
        return new GpuRuntimeBackendCatalogEntry(
                backendTarget,
                backendName,
                factory,
                ownership,
                productionAdapter,
                Optional.ofNullable(support),
                diagnostic
        );
    }
}
