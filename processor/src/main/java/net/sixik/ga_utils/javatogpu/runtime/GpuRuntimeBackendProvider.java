package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * ServiceLoader-friendly provider boundary for runtime backend adapters.
 *
 * <p>A provider is the stable extension point. It describes one backend family and creates the adapter that joins
 * the shared discovery, selection, lowering, and future compile/invoke pipeline. Keeping this layer separate from
 * {@link GpuRuntimeBackendAdapter} lets built-in and third-party backends register through the same deterministic
 * path without forcing native runtime initialization during catalog inspection.</p>
 */
public interface GpuRuntimeBackendProvider {

    /**
     * Stable backend family represented by this provider.
     */
    GpuBackendTarget backendTarget();

    /**
     * Stable provider id used for ordering diagnostics and duplicate detection.
     */
    String providerId();

    /**
     * Provider contract version, not the native driver/compiler version.
     */
    String providerVersion();

    /**
     * Lower values are considered first when providers are merged into the standard registry.
     */
    int providerOrder();

    /**
     * Creates a lightweight backend adapter. Implementations must not eagerly create native runtime sessions here.
     */
    GpuRuntimeBackendAdapter createAdapter();

    /**
     * Declares which execution stages this provider can supply without creating native runtime state.
     */
    default GpuRuntimeBackendExecutionSupport executionSupport() {
        return GpuRuntimeBackendExecutionSupport.discoveryOnly(
                backendTarget(),
                providerId(),
                "Backend provider has not declared a compile/prepare/invoke execution pipeline"
        );
    }

    /**
     * Optional factory for a backend-neutral compile -> prepare -> invoke runner.
     */
    default Optional<GpuBackendExecutionPipelineFactory<?, ?, ?>> executionPipelineFactory() {
        return Optional.empty();
    }

    /**
     * User-facing execution readiness card for diagnostics, catalogs, and planned-backend bring-up.
     */
    default GpuRuntimeBackendExecutionAvailability executionAvailability() {
        return GpuRuntimeBackendExecutionAvailability.from(this);
    }

    /**
     * Structured non-success pipeline result for providers that cannot execute kernels yet.
     */
    default GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> unsupportedExecutionResult(
            GpuBackendLoweringResult loweringResult
    ) {
        return executionAvailability().unsupportedPipelineResult(loweringResult);
    }

    /**
     * Renders provider-level diagnostics for catalogs, reports, and lifecycle journals.
     */
    default Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtimeBackendProvider" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".backendTarget", backendTarget().name());
        fields.put(normalizedPrefix + ".providerId", providerId());
        fields.put(normalizedPrefix + ".providerVersion", providerVersion());
        fields.put(normalizedPrefix + ".providerOrder", Integer.toString(providerOrder()));
        fields.putAll(executionSupport().artifactFields(normalizedPrefix + ".executionSupport"));
        fields.putAll(executionAvailability().artifactFields(normalizedPrefix + ".executionAvailability"));
        executionPipelineFactory().ifPresent(factory -> fields.putAll(
                factory.artifactFields(normalizedPrefix + ".executionPipelineFactory")
        ));
        fields.put("runtime.backend.provider.present", "true");
        fields.put("runtime.backend.provider.id", providerId());
        fields.put("runtime.backend.provider.version", providerVersion());
        fields.put("runtime.backend.provider.order", Integer.toString(providerOrder()));
        fields.put("runtime.backend.executionPipeline.factory.present", Boolean.toString(executionPipelineFactory().isPresent()));
        fields.put("runtime.backend.target", backendTarget().name());
        return Collections.unmodifiableMap(fields);
    }
}
