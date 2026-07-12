package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrTextCanonicalizationProposalProviderTest {

    @Test
    void canonicalizeNormalizesLineEndingsAndTrailingWhitespace() {
        assertEquals(
                "body\n  return value\n",
                GpuIrTextCanonicalizationProposalProvider.canonicalize("body  \r\n  return value\t\r\n")
        );
    }

    @Test
    void alreadyCanonicalBodyReturnsNoChangeProposal() {
        IrGpuArtifact original = artifact("body\n  return original\n");

        GpuIrOptimizationProposal proposal = new GpuIrTextCanonicalizationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertSame(original, proposal.originalArtifact());
        assertTrue(proposal.diagnostics().get(0).contains("already canonical"));
    }

    @Test
    void nonCanonicalBodyProducesDistinctOptimizedArtifact() {
        IrGpuArtifact original = artifact("body  \r\n  return original\t\r\n");

        GpuIrOptimizationProposal proposal = new GpuIrTextCanonicalizationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertNotSame(original, optimized);
        assertSame(original.module().methodBodies().get(0).typedBody(), optimized.module().methodBodies().get(0).typedBody());
        assertEquals("body\n  return original\n", optimized.module().methodBodies().get(0).body());
        assertEquals("body  \r\n  return original\t\r\n", original.module().methodBodies().get(0).body());
        assertEquals("1", proposal.proofArtifact().fields().get("changedMethodBodies"));
        assertEquals("false", proposal.proofArtifact().fields().get("mutationRequired"));
    }

    @Test
    void canonicalizationStaysProposalOnlyByDefault() {
        IrGpuArtifact original = artifact("body  \r\n  return original\t\r\n");
        GpuIrTextCanonicalizationProposalProvider provider = new GpuIrTextCanonicalizationProposalProvider();

        GpuIrOptimizationSandwichReport report = GpuIrOptimizationSandwichRunner.alwaysValid()
                .run(new GpuIrOptimizationProposalRequest(original), provider);

        assertEquals(GpuIrOptimizationSandwichStatus.PROPOSAL_ONLY, report.status());
        assertSame(original, report.selectedArtifact());
        assertTrue(report.selectedOriginal());
        assertFalse(report.selectedOptimized());
    }

    @Test
    void canonicalizationCanBeSelectedWhenMutationAllowed() {
        IrGpuArtifact original = artifact("body  \r\n  return original\t\r\n");
        GpuIrTextCanonicalizationProposalProvider provider = new GpuIrTextCanonicalizationProposalProvider();

        GpuIrOptimizationSandwichReport report = GpuIrOptimizationSandwichRunner.alwaysValid()
                .run(new GpuIrOptimizationProposalRequest(original, "diagnostic", true, Map.of()), provider);

        assertEquals(GpuIrOptimizationSandwichStatus.OPTIMIZED_SELECTED, report.status());
        assertEquals("body\n  return original\n", report.selectedArtifact().module().methodBodies().get(0).body());
        assertTrue(report.selectedOptimized());
    }

    @Test
    void canonicalizationProviderIsServiceLoaded() {
        List<GpuIrOptimizationProposalProvider> providers = ServiceLoader
                .load(GpuIrOptimizationProposalProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(GpuIrTextCanonicalizationProposalProvider.class::isInstance)
                .toList();

        assertEquals(1, providers.size());
    }

    private static IrGpuArtifact artifact(String body) {
        IrGpuMethodBody methodBody = IrGpuMethodBody.entry(
                "test.Kernel.run",
                "run",
                body,
                List.of()
        );
        IrGpuModule module = new IrGpuModule("test.Kernel.run", "run", List.of(), List.of(), List.of(methodBody));
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                module,
                List.of(IrGpuBackendOutput.openClSource("test.cl")),
                "opencl",
                "off"
        );
    }
}
