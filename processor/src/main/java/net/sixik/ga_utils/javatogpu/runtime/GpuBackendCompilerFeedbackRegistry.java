package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionRegistry;

import java.util.List;

/**
 * Compatibility facade for deterministic backend compiler feedback inspection.
 */
public final class GpuBackendCompilerFeedbackRegistry {

    private final net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuBackendCompilerFeedbackRegistry delegate;

    private GpuBackendCompilerFeedbackRegistry(
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuBackendCompilerFeedbackRegistry delegate
    ) {
        this.delegate = delegate;
    }

    public static GpuBackendCompilerFeedbackRegistry of(List<GpuBackendCompilerFeedbackProvider> providers) {
        return new GpuBackendCompilerFeedbackRegistry(
                net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuBackendCompilerFeedbackRegistry.of(providers)
        );
    }

    public static GpuBackendCompilerFeedbackRegistry loadWithBuiltIns() {
        return new GpuBackendCompilerFeedbackRegistry(
                net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuBackendCompilerFeedbackRegistry.loadWithBuiltIns()
        );
    }

    public GpuBackendCompilerFeedbackReport inspect(GpuRuntimeCompileArtifactSnapshot snapshot) {
        return delegate.inspect(snapshot);
    }

    public GpuBackendCompilerFeedbackReport inspect(GpuBackendCompilerFeedbackRequest request) {
        return delegate.inspect(request);
    }

    public List<GpuBackendCompilerFeedbackProvider> providers() {
        return delegate.providers();
    }

    public GpuExtensionRegistry extensionRegistry() {
        return delegate.extensionRegistry();
    }

    net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuBackendCompilerFeedbackRegistry unwrap() {
        return delegate;
    }
}
