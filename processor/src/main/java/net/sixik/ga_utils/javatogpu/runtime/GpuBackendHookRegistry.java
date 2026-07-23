package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Compatibility facade for backend hook discovery and read-only execution.
 */
public final class GpuBackendHookRegistry {

    private static final GpuBackendHookRegistry EMPTY = new GpuBackendHookRegistry(
            net.sixik.ga_utils.javatogpu.runtime.hooks.GpuBackendHookRegistry.empty()
    );

    private final net.sixik.ga_utils.javatogpu.runtime.hooks.GpuBackendHookRegistry delegate;

    private GpuBackendHookRegistry(net.sixik.ga_utils.javatogpu.runtime.hooks.GpuBackendHookRegistry delegate) {
        this.delegate = delegate;
    }

    public static GpuBackendHookRegistry empty() {
        return EMPTY;
    }

    public static GpuBackendHookRegistry of(Collection<? extends GpuBackendHook> hooks) {
        if (hooks == null || hooks.isEmpty()) {
            return empty();
        }
        return new GpuBackendHookRegistry(
                net.sixik.ga_utils.javatogpu.runtime.hooks.GpuBackendHookRegistry.of(hooks)
        );
    }

    public static GpuBackendHookRegistry loadWithServiceLoader() {
        return new GpuBackendHookRegistry(
                net.sixik.ga_utils.javatogpu.runtime.hooks.GpuBackendHookRegistry.loadWithServiceLoader()
        );
    }

    public static GpuBackendHookRegistry loadWithServiceLoader(ClassLoader classLoader) {
        return new GpuBackendHookRegistry(
                net.sixik.ga_utils.javatogpu.runtime.hooks.GpuBackendHookRegistry.loadWithServiceLoader(classLoader)
        );
    }

    public List<GpuBackendHook> hooks() {
        return delegate.hooks();
    }

    public GpuExtensionRegistry extensionRegistry() {
        return delegate.extensionRegistry();
    }

    public int size() {
        return delegate.size();
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public List<GpuBackendHook> forBackendTarget(GpuBackendTarget backendTarget) {
        return delegate.forBackendTarget(backendTarget);
    }

    public List<GpuBackendHook> forCapability(GpuExtensionCapability capability) {
        return delegate.forCapability(capability);
    }

    public Map<String, String> artifactFields(String prefix) {
        return delegate.artifactFields(prefix);
    }

    public Map<String, String> contractDiagnosticFields(String prefix) {
        return delegate.contractDiagnosticFields(prefix);
    }

    public GpuBackendHookAuthorizationReport authorizationReport(
            GpuBackendTarget backendTarget,
            GpuExtensionPhase phase
    ) {
        return delegate.authorizationReport(backendTarget, phase);
    }

    public GpuBackendHookAuthorizationReport authorizationReport(
            GpuBackendTarget backendTarget,
            GpuExtensionPhase phase,
            GpuBackendHookAuthorizationPolicy policy
    ) {
        return delegate.authorizationReport(backendTarget, phase, policy);
    }

    public GpuBackendHookAuthorizationCatalog authorizationCatalog(GpuBackendTarget backendTarget) {
        return delegate.authorizationCatalog(backendTarget);
    }

    public GpuBackendHookAuthorizationCatalog authorizationCatalog(
            GpuBackendTarget backendTarget,
            GpuBackendHookAuthorizationPolicy policy
    ) {
        return delegate.authorizationCatalog(backendTarget, policy);
    }

    public Map<String, String> observeDiscovery(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDeviceDiscoveryResult discoveryResult,
            String prefix
    ) {
        return delegate.observeDiscovery(compileOptions, discoveryResult, prefix);
    }

    public Map<String, String> observeDiscovery(
            GpuBackendTarget backendTarget,
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDeviceDiscoveryResult discoveryResult,
            String prefix
    ) {
        return delegate.observeDiscovery(backendTarget, compileOptions, discoveryResult, prefix);
    }

    public Map<String, String> observeLowering(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendLoweringResult loweringResult,
            String prefix
    ) {
        return delegate.observeLowering(compileRequest, loweringResult, prefix);
    }

    public Map<String, String> observeLowering(
            GpuBackendTarget backendTarget,
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendLoweringResult loweringResult,
            String prefix
    ) {
        return delegate.observeLowering(backendTarget, compileRequest, loweringResult, prefix);
    }

    public Map<String, String> observeCompilation(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendCompilationResult compilationResult,
            String prefix
    ) {
        return delegate.observeCompilation(compileRequest, compilationResult, prefix);
    }

    public Map<String, String> observeCompilation(
            GpuBackendTarget backendTarget,
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendCompilationResult compilationResult,
            String prefix
    ) {
        return delegate.observeCompilation(backendTarget, compileRequest, compilationResult, prefix);
    }

    public Map<String, String> observeInvocation(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendInvocationResult invocationResult,
            String prefix
    ) {
        return delegate.observeInvocation(compileRequest, invocationResult, prefix);
    }

    public Map<String, String> observeInvocation(
            GpuBackendTarget backendTarget,
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendInvocationResult invocationResult,
            String prefix
    ) {
        return delegate.observeInvocation(backendTarget, compileRequest, invocationResult, prefix);
    }

    public Map<String, String> contributeArtifactFields(
            GpuBackendTarget backendTarget,
            GpuRuntimeCompileRequest compileRequest,
            Map<String, String> currentFields,
            String prefix
    ) {
        return delegate.contributeArtifactFields(backendTarget, compileRequest, currentFields, prefix);
    }

    public String toMarkdown() {
        return delegate.toMarkdown();
    }
}
