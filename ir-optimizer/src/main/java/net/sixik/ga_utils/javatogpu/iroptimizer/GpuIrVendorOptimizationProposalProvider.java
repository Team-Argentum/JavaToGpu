package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Set;

/**
 * Optional vendor/device-specific proposal provider.
 *
 * <p>Vendor providers are isolated from the default backend-neutral proposal ServiceLoader path. Projects may
 * load them explicitly through {@link GpuIrVendorOptimizationProposalRegistry} and adapt them into the common
 * immutable proposal contract when they want vendor-aware proposal evidence.</p>
 */
@FunctionalInterface
public interface GpuIrVendorOptimizationProposalProvider extends GpuIrOptimizationProposalProvider {

    GpuIrOptimizationProposal proposeForVendor(GpuIrVendorOptimizationProposalRequest request);

    @Override
    default GpuIrOptimizationProposal propose(GpuIrOptimizationProposalRequest request) {
        return proposeForVendor(GpuIrVendorOptimizationProposalRequest.from(request));
    }

    @Override
    default Set<GpuExtensionCapability> extensionCapabilities() {
        return Set.of(GpuExtensionCapability.IR_OPTIMIZATION_PROPOSAL);
    }

    @Override
    default GpuExtensionPhase extensionPhase() {
        return GpuExtensionPhase.RUNTIME_IR_OPTIMIZATION;
    }

    @Override
    default GpuExtensionPermission extensionPermission() {
        return GpuExtensionPermission.MUTATION_PROPOSAL;
    }

    default GpuBackendTarget supportedBackendTarget() {
        return GpuBackendTarget.UNKNOWN;
    }

    default String supportedVendor() {
        return "unknown";
    }

    default boolean supports(GpuIrVendorOptimizationProposalRequest request) {
        return request.matches(supportedBackendTarget(), supportedVendor());
    }
}
