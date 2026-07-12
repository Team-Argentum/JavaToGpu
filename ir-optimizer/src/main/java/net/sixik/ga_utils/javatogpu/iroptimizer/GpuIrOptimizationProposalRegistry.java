package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.extension.GpuExtension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Backend-neutral registry for optional immutable IR optimization proposal providers.
 */
public final class GpuIrOptimizationProposalRegistry {

    private final List<GpuIrOptimizationProposalProvider> providers;
    private final Map<String, String> rejectionReasons;

    private GpuIrOptimizationProposalRegistry(
            List<GpuIrOptimizationProposalProvider> providers,
            Map<String, String> rejectionReasons
    ) {
        this.providers = List.copyOf(providers);
        this.rejectionReasons = Map.copyOf(rejectionReasons);
    }

    public static GpuIrOptimizationProposalRegistry empty() {
        return new GpuIrOptimizationProposalRegistry(List.of(), Map.of());
    }

    public static GpuIrOptimizationProposalRegistry of(
            List<GpuIrOptimizationProposalProvider> providers
    ) {
        return build(providers == null ? List.of() : providers);
    }

    public static GpuIrOptimizationProposalRegistry loadFromServiceLoader() {
        ArrayList<GpuIrOptimizationProposalProvider> loaded = new ArrayList<>();
        ServiceLoader.load(
                GpuIrOptimizationProposalProvider.class,
                GpuIrOptimizationProposalProvider.class.getClassLoader()
        ).forEach(loaded::add);
        return build(loaded);
    }

    public List<GpuIrOptimizationProposalProvider> providers() {
        return providers;
    }

    public Map<String, String> rejectionReasons() {
        return rejectionReasons;
    }

    private static GpuIrOptimizationProposalRegistry build(
            List<GpuIrOptimizationProposalProvider> candidates
    ) {
        ArrayList<GpuIrOptimizationProposalProvider> accepted = new ArrayList<>();
        LinkedHashMap<String, String> rejected = new LinkedHashMap<>();
        LinkedHashMap<String, GpuIrOptimizationProposalProvider> byId = new LinkedHashMap<>();
        for (GpuIrOptimizationProposalProvider provider : candidates) {
            if (provider == null) {
                continue;
            }
            String extensionId = provider.extensionId();
            if (extensionId == null || extensionId.isBlank()) {
                rejected.put(provider.getClass().getName(), "blank extension id");
                continue;
            }
            if (byId.containsKey(extensionId)) {
                rejected.put(extensionId, "duplicate extension id");
                continue;
            }
            byId.put(extensionId, provider);
        }
        accepted.addAll(byId.values());
        accepted.sort(Comparator
                .comparingInt(GpuExtension::extensionOrder)
                .thenComparing(GpuExtension::extensionId)
                .thenComparing(GpuExtension::extensionVersion));
        return new GpuIrOptimizationProposalRegistry(accepted, rejected);
    }
}
