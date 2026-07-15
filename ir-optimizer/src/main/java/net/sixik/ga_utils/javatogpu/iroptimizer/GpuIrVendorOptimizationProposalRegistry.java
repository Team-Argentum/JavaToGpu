package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.extension.GpuExtension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Deterministic registry for optional vendor-specific IR optimizer proposal providers.
 */
public final class GpuIrVendorOptimizationProposalRegistry {

    private final List<GpuIrVendorOptimizationProposalProvider> providers;
    private final Map<String, String> rejectionReasons;

    private GpuIrVendorOptimizationProposalRegistry(
            List<GpuIrVendorOptimizationProposalProvider> providers,
            Map<String, String> rejectionReasons
    ) {
        this.providers = List.copyOf(providers);
        this.rejectionReasons = Map.copyOf(rejectionReasons);
    }

    public static GpuIrVendorOptimizationProposalRegistry empty() {
        return new GpuIrVendorOptimizationProposalRegistry(List.of(), Map.of());
    }

    public static GpuIrVendorOptimizationProposalRegistry of(
            List<GpuIrVendorOptimizationProposalProvider> providers
    ) {
        return build(providers == null ? List.of() : providers);
    }

    public static GpuIrVendorOptimizationProposalRegistry loadFromServiceLoader() {
        ArrayList<GpuIrVendorOptimizationProposalProvider> loaded = new ArrayList<>();
        ServiceLoader.load(
                GpuIrVendorOptimizationProposalProvider.class,
                GpuIrVendorOptimizationProposalProvider.class.getClassLoader()
        ).forEach(loaded::add);
        return build(loaded);
    }

    public List<GpuIrVendorOptimizationProposalProvider> providers() {
        return providers;
    }

    public Map<String, String> rejectionReasons() {
        return rejectionReasons;
    }

    public GpuIrOptimizationProposalProvider asProposalProvider() {
        return request -> {
            GpuIrVendorOptimizationProposalRequest vendorRequest =
                    GpuIrVendorOptimizationProposalRequest.from(request);
            for (GpuIrVendorOptimizationProposalProvider provider : providers) {
                if (provider.supports(vendorRequest)) {
                    return provider.proposeForVendor(vendorRequest);
                }
            }
            return GpuIrOptimizationProposal.noChange(
                    GpuIrOptimizerModule.VENDOR_PROVIDER_ADAPTER_ID,
                    GpuIrOptimizerModule.VENDOR_PROVIDER_ADAPTER_VERSION,
                    request.originalArtifact(),
                    "no vendor IR optimizer proposal provider matched backend="
                            + vendorRequest.backendTarget()
                            + ", vendor="
                            + vendorRequest.vendor()
            );
        };
    }

    private static GpuIrVendorOptimizationProposalRegistry build(
            List<GpuIrVendorOptimizationProposalProvider> candidates
    ) {
        ArrayList<GpuIrVendorOptimizationProposalProvider> accepted = new ArrayList<>();
        LinkedHashMap<String, String> rejected = new LinkedHashMap<>();
        LinkedHashMap<String, GpuIrVendorOptimizationProposalProvider> byId = new LinkedHashMap<>();
        for (GpuIrVendorOptimizationProposalProvider provider : candidates) {
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
        return new GpuIrVendorOptimizationProposalRegistry(accepted, rejected);
    }
}
