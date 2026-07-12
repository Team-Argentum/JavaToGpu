package net.sixik.ga_utils.javatogpu.irvendoroptimizer;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.iroptimizer.GpuIrOptimizationProposal;
import net.sixik.ga_utils.javatogpu.iroptimizer.GpuIrVendorOptimizationProposalProvider;
import net.sixik.ga_utils.javatogpu.iroptimizer.GpuIrVendorOptimizationProposalRequest;

/**
 * Vendor-provider skeleton that records opt-in discovery without proposing a rewrite.
 */
public final class GpuIrNoOpVendorOptimizationProposalProvider implements GpuIrVendorOptimizationProposalProvider {

    public static final String PROVIDER_ID = GpuIrVendorOptimizerModule.MODULE_ID + ".noop";
    public static final String PROVIDER_VERSION = PROVIDER_ID + ":1";

    @Override
    public GpuIrOptimizationProposal proposeForVendor(GpuIrVendorOptimizationProposalRequest request) {
        return GpuIrOptimizationProposal.noChange(
                extensionId(),
                extensionVersion(),
                request.originalArtifact(),
                "vendor optimizer skeleton observed backend="
                        + request.backendTarget()
                        + ", vendor="
                        + request.vendor()
                        + " and kept original IR selected"
        );
    }

    @Override
    public GpuBackendTarget supportedBackendTarget() {
        return GpuBackendTarget.UNKNOWN;
    }

    @Override
    public String supportedVendor() {
        return "";
    }

    @Override
    public String extensionId() {
        return PROVIDER_ID;
    }

    @Override
    public String extensionVersion() {
        return PROVIDER_VERSION;
    }

    @Override
    public int extensionOrder() {
        return 100;
    }
}
