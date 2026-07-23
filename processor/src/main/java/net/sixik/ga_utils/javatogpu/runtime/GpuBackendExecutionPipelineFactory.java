package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Factory for backend-neutral execution pipelines bound to a concrete runtime backend instance.
 *
 * <p>Providers expose this through {@link GpuRuntimeBackendProvider#executionPipelineFactory()} when a backend can
 * participate in the shared compile/prepare/invoke runner. Factories must be lightweight: catalog inspection may call
 * metadata methods without opening native devices, contexts, or compiler processes.</p>
 *
 * @param <C> backend-specific compiled kernel/module handle
 * @param <P> backend-specific prepared invocation handle
 * @param <PLAN> backend-specific execution plan consumed by the preparer
 */
public interface GpuBackendExecutionPipelineFactory<
        C extends GpuBackendCompiledKernel,
        P extends GpuPreparedKernel,
        PLAN> {

    /**
     * Backend family this factory supports.
     */
    GpuBackendTarget backendTarget();

    /**
     * Stable id for diagnostics and duplicate/provider ordering reports.
     */
    String factoryId();

    /**
     * Factory contract version, not the native compiler/driver version.
     */
    String factoryVersion();

    /**
     * Optional concrete backend type required by this factory.
     */
    Class<? extends GpuRuntimeBackend> backendType();

    /**
     * Creates a pipeline for the supplied backend instance.
     */
    GpuBackendExecutionPipeline<C, P, PLAN> createPipeline(GpuRuntimeBackend backend);

    /**
     * Returns whether this factory can create a pipeline for the supplied backend instance without mutating it.
     */
    default boolean supportsBackend(GpuRuntimeBackend backend) {
        Class<? extends GpuRuntimeBackend> type = backendType();
        return backend != null
                && backend.backendTarget() == backendTarget()
                && (type == null || type.isInstance(backend));
    }

    /**
     * Stable property map for catalogs, lifecycle journals, and validation reports.
     */
    default Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.backend.executionPipelineFactory"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".backendTarget", backendTarget().name());
        fields.put(normalizedPrefix + ".id", factoryId());
        fields.put(normalizedPrefix + ".version", factoryVersion());
        fields.put(normalizedPrefix + ".backendType", backendType() == null ? "" : backendType().getName());
        fields.put("runtime.backend.executionPipelineFactory.present", "true");
        fields.put("runtime.backend.executionPipelineFactory.id", factoryId());
        fields.put("runtime.backend.executionPipelineFactory.version", factoryVersion());
        fields.put("runtime.backend.target", backendTarget().name());
        return Collections.unmodifiableMap(fields);
    }

    /**
     * Fails fast with a clear message when the backend is not compatible with this factory.
     */
    default void requireSupportedBackend(GpuRuntimeBackend backend) {
        Objects.requireNonNull(backend, "backend");
        if (!supportsBackend(backend)) {
            throw new IllegalArgumentException(
                    "Backend execution pipeline factory "
                            + factoryId()
                            + " cannot create a "
                            + backendTarget()
                            + " pipeline for "
                            + backend.getClass().getName()
                            + " (target="
                            + backend.backendTarget()
                            + ")"
            );
        }
    }
}
