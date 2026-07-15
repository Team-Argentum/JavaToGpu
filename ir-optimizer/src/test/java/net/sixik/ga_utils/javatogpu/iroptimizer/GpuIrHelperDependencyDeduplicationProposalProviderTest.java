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

class GpuIrHelperDependencyDeduplicationProposalProviderTest {

    @Test
    void deduplicateKeepsFirstOccurrenceOrderAndDropsBlankEntries() {
        assertEquals(
                List.of("helperA", "helperB", "helperC"),
                GpuIrHelperDependencyDeduplicationProposalProvider.deduplicate(
                        List.of("helperA", "helperB", "helperA", " ", "helperC", "helperB")
                )
        );
    }

    @Test
    void alreadyDeduplicatedDependenciesReturnNoChangeProposal() {
        IrGpuArtifact original = artifact(List.of("helperA", "helperB"));

        GpuIrOptimizationProposal proposal = new GpuIrHelperDependencyDeduplicationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertSame(original, proposal.originalArtifact());
        assertTrue(proposal.diagnostics().get(0).contains("already deduplicated"));
    }

    @Test
    void duplicateDependenciesProduceDistinctOptimizedArtifact() {
        IrGpuArtifact original = artifact(List.of("helperA", "helperB", "helperA", "helperC", "helperB"));

        GpuIrOptimizationProposal proposal = new GpuIrHelperDependencyDeduplicationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertNotSame(original, optimized);
        assertSame(original.module().methodBodies().get(0).typedBody(), optimized.module().methodBodies().get(0).typedBody());
        assertEquals(original.module().methodBodies().get(0).body(), optimized.module().methodBodies().get(0).body());
        assertEquals(List.of("helperA", "helperB", "helperC"), optimized.module().methodBodies().get(0).helperDependencies());
        assertEquals(List.of("helperA", "helperB", "helperA", "helperC", "helperB"), original.module().methodBodies().get(0).helperDependencies());
        assertEquals("1", proposal.proofArtifact().fields().get("changedMethodBodies"));
        assertEquals("2", proposal.proofArtifact().fields().get("removedDuplicateDependencies"));
        assertEquals("first-occurrence", proposal.proofArtifact().fields().get("preservedOrder"));
    }

    @Test
    void deduplicationStaysProposalOnlyByDefault() {
        IrGpuArtifact original = artifact(List.of("helperA", "helperA"));

        GpuIrOptimizationSandwichReport report = GpuIrOptimizationSandwichRunner.alwaysValid()
                .run(new GpuIrOptimizationProposalRequest(original), new GpuIrHelperDependencyDeduplicationProposalProvider());

        assertEquals(GpuIrOptimizationSandwichStatus.PROPOSAL_ONLY, report.status());
        assertSame(original, report.selectedArtifact());
        assertTrue(report.selectedOriginal());
        assertFalse(report.selectedOptimized());
    }

    @Test
    void deduplicationCanBeSelectedWhenMutationAllowed() {
        IrGpuArtifact original = artifact(List.of("helperA", "helperA"));

        GpuIrOptimizationSandwichReport report = GpuIrOptimizationSandwichRunner.alwaysValid()
                .run(new GpuIrOptimizationProposalRequest(original, "diagnostic", true, Map.of()),
                        new GpuIrHelperDependencyDeduplicationProposalProvider());

        assertEquals(GpuIrOptimizationSandwichStatus.OPTIMIZED_SELECTED, report.status());
        assertEquals(List.of("helperA"), report.selectedArtifact().module().methodBodies().get(0).helperDependencies());
        assertTrue(report.selectedOptimized());
    }

    @Test
    void deduplicationProviderIsServiceLoaded() {
        List<GpuIrOptimizationProposalProvider> providers = ServiceLoader
                .load(GpuIrOptimizationProposalProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(GpuIrHelperDependencyDeduplicationProposalProvider.class::isInstance)
                .toList();

        assertEquals(1, providers.size());
    }

    private static IrGpuArtifact artifact(List<String> helperDependencies) {
        IrGpuMethodBody methodBody = IrGpuMethodBody.entry(
                "test.Kernel.run",
                "run",
                "body\n  return original\n",
                helperDependencies
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
