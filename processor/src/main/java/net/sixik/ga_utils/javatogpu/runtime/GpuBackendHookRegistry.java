package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Deterministic catalog for backend SPI hooks.
 *
 * <p>This registry discovers and validates hooks, but intentionally does not invoke them from the production runtime.
 * Stage-specific hook execution should be added explicitly by future A8 work once each stage has a reviewed permission
 * and failure-policy contract.</p>
 */
public final class GpuBackendHookRegistry {

    private static final GpuBackendHookRegistry EMPTY = new GpuBackendHookRegistry(List.of());

    private final List<GpuBackendHook> hooks;
    private final GpuExtensionRegistry extensionRegistry;

    private GpuBackendHookRegistry(List<GpuBackendHook> hooks) {
        this.hooks = List.copyOf(hooks);
        this.extensionRegistry = GpuExtensionRegistry.of(this.hooks);
    }

    public static GpuBackendHookRegistry empty() {
        return EMPTY;
    }

    public static GpuBackendHookRegistry of(Collection<? extends GpuBackendHook> hooks) {
        if (hooks == null || hooks.isEmpty()) {
            return empty();
        }
        ArrayList<GpuBackendHook> sorted = new ArrayList<>();
        for (GpuBackendHook hook : hooks) {
            sorted.add(Objects.requireNonNull(hook, "hook"));
        }
        sorted.sort(Comparator
                .comparingInt(GpuBackendHook::extensionOrder)
                .thenComparing(GpuBackendHook::extensionId)
                .thenComparing(GpuBackendHook::extensionVersion)
                .thenComparing(hook -> hook.getClass().getName()));
        return new GpuBackendHookRegistry(sorted);
    }

    public static GpuBackendHookRegistry loadWithServiceLoader() {
        return loadWithServiceLoader(contextClassLoader());
    }

    public static GpuBackendHookRegistry loadWithServiceLoader(ClassLoader classLoader) {
        ClassLoader loader = classLoader == null ? contextClassLoader() : classLoader;
        LinkedHashMap<String, GpuBackendHook> loaded = new LinkedHashMap<>();
        loadService(loader, GpuBackendHook.class, loaded);
        loadService(loader, GpuBackendPolicyContributor.class, loaded);
        loadService(loader, GpuRuntimeBackendScoreContributor.class, loaded);
        loadService(loader, GpuBackendDiscoveryContributor.class, loaded);
        loadService(loader, GpuBackendLoweringHook.class, loaded);
        loadService(loader, GpuBackendCompilationHook.class, loaded);
        loadService(loader, GpuBackendInvocationHook.class, loaded);
        loadService(loader, GpuBackendArtifactHook.class, loaded);
        return of(loaded.values());
    }

    public List<GpuBackendHook> hooks() {
        return hooks;
    }

    public GpuExtensionRegistry extensionRegistry() {
        return extensionRegistry;
    }

    public int size() {
        return hooks.size();
    }

    public boolean isEmpty() {
        return hooks.isEmpty();
    }

    public List<GpuBackendHook> forBackendTarget(GpuBackendTarget backendTarget) {
        return hooks.stream()
                .filter(hook -> hook.appliesTo(backendTarget))
                .toList();
    }

    public List<GpuBackendHook> forCapability(GpuExtensionCapability capability) {
        if (capability == null) {
            return List.of();
        }
        return hooks.stream()
                .filter(hook -> hook.extensionCapabilities().contains(capability))
                .toList();
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.backend.hookRegistry" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".hook.count", Integer.toString(hooks.size()));
        for (int index = 0; index < hooks.size(); index++) {
            GpuBackendHook hook = hooks.get(index);
            String hookPrefix = normalizedPrefix + ".hook." + index;
            fields.put(hookPrefix + ".implementationClass", hook.getClass().getName());
            hook.artifactFields(hookPrefix).forEach((key, value) -> {
                if (key.startsWith(hookPrefix + ".")) {
                    fields.put(key, value);
                }
            });
        }
        fields.put("runtime.backend.hookRegistry.present", "true");
        fields.put("runtime.backend.hookRegistry.hook.count", Integer.toString(hooks.size()));
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Backend hooks: ").append(hooks.size()).append(System.lineSeparator());
        for (GpuBackendHook hook : hooks) {
            builder.append("- ")
                    .append(hook.extensionId())
                    .append(": phase=")
                    .append(hook.extensionPhase())
                    .append(", permission=")
                    .append(hook.extensionPermission())
                    .append(", failurePolicy=")
                    .append(hook.failurePolicy())
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static <T extends GpuBackendHook> void loadService(
            ClassLoader classLoader,
            Class<T> serviceType,
            LinkedHashMap<String, GpuBackendHook> loaded
    ) {
        ServiceLoader.load(serviceType, classLoader)
                .forEach(hook -> loaded.putIfAbsent(hook.getClass().getName(), hook));
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader == null ? GpuBackendHookRegistry.class.getClassLoader() : classLoader;
    }
}
