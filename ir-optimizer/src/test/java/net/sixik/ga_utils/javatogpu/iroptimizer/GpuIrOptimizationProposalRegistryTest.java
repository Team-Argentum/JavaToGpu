package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationProposalRegistryTest {

    @Test
    void registryOrdersProvidersAndRejectsDuplicateIds() {
        GpuIrOptimizationProposalRegistry registry = GpuIrOptimizationProposalRegistry.of(List.of(
                new Provider("z-provider", 20),
                new Provider("a-provider", 10),
                new Provider("a-provider", 30)
        ));

        assertEquals(2, registry.providers().size());
        assertEquals("a-provider", registry.providers().get(0).extensionId());
        assertEquals("z-provider", registry.providers().get(1).extensionId());
        assertEquals("duplicate extension id", registry.rejectionReasons().get("a-provider"));
    }

    @Test
    void serviceLoaderRegistryLoadsBackendNeutralProvidersOnly() {
        List<String> providerIds = GpuIrOptimizationProposalRegistry.loadFromServiceLoader()
                .providers()
                .stream()
                .map(GpuIrOptimizationProposalProvider::extensionId)
                .toList();

        assertTrue(providerIds.contains(GpuIrNoOpOptimizationPass.PASS_ID));
        assertTrue(providerIds.contains(GpuIrTextCanonicalizationProposalProvider.PROVIDER_ID));
        assertTrue(providerIds.contains(GpuIrHelperDependencyDeduplicationProposalProvider.PROVIDER_ID));
        assertTrue(providerIds.contains(GpuIrConstantFoldingPreviewProposalProvider.PROVIDER_ID));
        assertTrue(providerIds.contains(GpuIrSafeLocalCsePreviewProposalProvider.PROVIDER_ID));
        assertTrue(providerIds.contains(GpuIrTypedDeadCodePreviewProposalProvider.PROVIDER_ID));
        assertTrue(providerIds.stream().noneMatch(id -> id.contains("vendor")));
    }

    private record Provider(String extensionId, int extensionOrder) implements GpuIrOptimizationProposalProvider {

        @Override
        public GpuIrOptimizationProposal propose(GpuIrOptimizationProposalRequest request) {
            IrGpuArtifact original = request.originalArtifact();
            return GpuIrOptimizationProposal.noChange(
                    extensionId(),
                    extensionVersion(),
                    original,
                    "test provider"
            );
        }

        @Override
        public String extensionId() {
            return extensionId;
        }

        @Override
        public String extensionVersion() {
            return extensionId + ":1";
        }

        @Override
        public int extensionOrder() {
            return extensionOrder;
        }
    }
}
