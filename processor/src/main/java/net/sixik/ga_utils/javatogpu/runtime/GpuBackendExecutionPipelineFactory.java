package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Factory for backend-neutral execution pipelines bound to a concrete runtime backend instance.
 */
public interface GpuBackendExecutionPipelineFactory<
        C extends GpuBackendCompiledKernel,
        P extends GpuPreparedKernel,
        PLAN> {

    GpuBackendTarget backendTarget();

    String factoryId();

    String factoryVersion();

    Class<? extends GpuRuntimeBackend> backendType();

    GpuBackendExecutionPipeline<C, P, PLAN> createPipeline(GpuRuntimeBackend backend);

    default boolean supportsBackend(GpuRuntimeBackend backend) {
        Class<? extends GpuRuntimeBackend> type = backendType();
        return backend != null
                && backend.backendTarget() == backendTarget()
                && (type == null || type.isInstance(backend));
    }

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
