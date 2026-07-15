package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationSandwichRunnerTest {

    @Test
    void noOpProposalKeepsOriginalSelected() {
        IrGpuArtifact original = artifact("body\n  return original\n");

        GpuIrOptimizationSandwichReport report = GpuIrOptimizationSandwichRunner.alwaysValid()
                .run(new GpuIrOptimizationProposalRequest(original), new GpuIrNoOpProposalProvider());

        assertEquals(GpuIrOptimizationSandwichStatus.NO_CHANGE, report.status());
        assertSame(original, report.selectedArtifact());
        assertTrue(report.selectedOriginal());
        assertFalse(report.selectedOptimized());
        assertTrue(report.optimizedArtifactCandidate().isEmpty());
        assertFalse(report.requiresRollback());
        assertTrue(report.proposal().orElseThrow().diagnostics().get(0).contains("keeps original IR"));
    }

    @Test
    void optimizedArtifactStaysProposalOnlyWhenMutationDisabled() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuIrOptimizationProposalProvider provider = request -> proposed(original, optimized);

        GpuIrOptimizationSandwichReport report = GpuIrOptimizationSandwichRunner.alwaysValid()
                .run(new GpuIrOptimizationProposalRequest(original), provider);

        assertEquals(GpuIrOptimizationSandwichStatus.PROPOSAL_ONLY, report.status());
        assertSame(original, report.selectedArtifact());
        assertTrue(report.selectedOriginal());
        assertFalse(report.selectedOptimized());
        GpuIrOptimizedArtifactCandidate candidate = report.optimizedArtifactCandidate().orElseThrow();
        assertSame(original, candidate.originalArtifact());
        assertSame(optimized, candidate.optimizedArtifact());
        assertEquals("candidate-ready", candidate.status());
        assertEquals("none", candidate.firstBlocker());
        assertEquals("mutation-disabled", candidate.selectionFirstBlocker());
        assertFalse(candidate.selectionReady());
        assertFalse(candidate.selectionApplied());
        assertFalse(candidate.selectedIrReplacement());
        assertEquals("false", candidate.fields("candidate").get("candidate.selectionApplied"));
        assertEquals("mutation-disabled", candidate.fields("candidate").get("candidate.selectionFirstBlocker"));
        assertTrue(report.diagnostics().contains(
                "optimized artifact validated but mutation is disabled; original IR remains selected"
        ));
    }

    @Test
    void optimizedArtifactCanBeSelectedWhenMutationAllowedAndValidationPasses() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuIrOptimizationProposalProvider provider = request -> proposed(original, optimized);

        GpuIrOptimizationSandwichReport report = GpuIrOptimizationSandwichRunner.alwaysValid()
                .run(new GpuIrOptimizationProposalRequest(original, "diagnostic", true, Map.of()), provider);

        assertEquals(GpuIrOptimizationSandwichStatus.OPTIMIZED_SELECTED, report.status());
        assertSame(optimized, report.selectedArtifact());
        assertFalse(report.selectedOriginal());
        assertTrue(report.selectedOptimized());
        GpuIrOptimizedArtifactCandidate candidate = report.optimizedArtifactCandidate().orElseThrow();
        assertEquals("candidate-ready", candidate.status());
        assertEquals("selection-gate-not-bound", candidate.selectionFirstBlocker());
        assertTrue(candidate.mutationAllowed());
        assertFalse(candidate.selectionReady());
        assertFalse(candidate.selectionApplied());
        assertFalse(candidate.selectedIrReplacement());
        assertFalse(report.requiresRollback());
    }

    @Test
    void invalidOptimizedArtifactRollsBackToOriginal() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuIrOptimizationValidationGate validationGate = request -> {
            if (request.stage() == GpuIrOptimizationValidationStage.OPTIMIZED_AFTER) {
                return GpuIrOptimizationValidationResult.invalid(
                        request.stage(),
                        "post-validation-failed",
                        "optimized artifact rejected"
                );
            }
            return GpuIrOptimizationValidationResult.valid(request.stage());
        };

        GpuIrOptimizationSandwichReport report = new GpuIrOptimizationSandwichRunner(validationGate)
                .run(new GpuIrOptimizationProposalRequest(original, "diagnostic", true, Map.of()),
                        request -> proposed(original, optimized));

        assertEquals(GpuIrOptimizationSandwichStatus.OPTIMIZED_INVALID_ROLLED_BACK, report.status());
        assertSame(original, report.selectedArtifact());
        assertTrue(report.selectedOriginal());
        assertTrue(report.requiresRollback());
        GpuIrOptimizedArtifactCandidate candidate = report.optimizedArtifactCandidate().orElseThrow();
        assertEquals("blocked", candidate.status());
        assertEquals("post-validation-failed", candidate.firstBlocker());
        assertEquals("optimized-validation-failed", candidate.selectionFirstBlocker());
        assertFalse(candidate.optimizedValidationPassed());
        assertFalse(candidate.selectionApplied());
        assertEquals("post-validation-failed", report.optimizedValidation().orElseThrow().verdict());
        assertTrue(report.diagnostics().contains("optimized artifact rejected"));
    }

    @Test
    void invalidOriginalStopsBeforeProviderRuns() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        GpuIrOptimizationValidationGate validationGate = request -> GpuIrOptimizationValidationResult.invalid(
                request.stage(),
                "original-invalid",
                "original artifact rejected"
        );

        GpuIrOptimizationSandwichReport report = new GpuIrOptimizationSandwichRunner(validationGate)
                .run(new GpuIrOptimizationProposalRequest(original), request -> {
                    throw new AssertionError("provider must not run when original validation fails");
                });

        assertEquals(GpuIrOptimizationSandwichStatus.ORIGINAL_INVALID, report.status());
        assertSame(original, report.selectedArtifact());
        assertTrue(report.proposal().isEmpty());
        assertTrue(report.diagnostics().contains("original artifact rejected"));
    }

    @Test
    void malformedProposedDecisionWithoutArtifactIsRejected() {
        IrGpuArtifact original = artifact("body\n  return original\n");

        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationProposal(
                "optimizer:bad",
                "optimizer:bad:1",
                original,
                java.util.Optional.empty(),
                GpuIrOptimizationProposalDecision.PROPOSED,
                GpuRuntimeIrOptimizationProofArtifact.fromFields("test", "bad", Map.of()),
                "",
                List.of()
        ));
    }

    @Test
    void candidateBuilderRejectsNoChangeProposals() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        GpuIrOptimizationProposal proposal = GpuIrOptimizationProposal.noChange(
                "optimizer:test",
                "optimizer:test:1",
                original,
                "no change"
        );

        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizedArtifactCandidateBuilder.failClosed().build(
                new GpuIrOptimizationProposalRequest(original),
                proposal,
                GpuIrOptimizationValidationResult.valid(GpuIrOptimizationValidationStage.OPTIMIZED_AFTER)
        ));
    }

    @Test
    void candidateBuilderRejectsProposalForDifferentOriginalArtifact() {
        IrGpuArtifact requestOriginal = artifact("body\n  return requestOriginal\n");
        IrGpuArtifact proposalOriginal = artifact("body\n  return proposalOriginal\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");

        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizedArtifactCandidateBuilder.failClosed().build(
                new GpuIrOptimizationProposalRequest(requestOriginal),
                proposed(proposalOriginal, optimized),
                GpuIrOptimizationValidationResult.valid(GpuIrOptimizationValidationStage.OPTIMIZED_AFTER)
        ));
    }

    @Test
    void candidateEnvelopePinsDerivedFailClosedFields() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuIrOptimizationProposal proposal = proposed(original, optimized);
        GpuIrOptimizationValidationResult invalidValidation = GpuIrOptimizationValidationResult.invalid(
                GpuIrOptimizationValidationStage.OPTIMIZED_AFTER,
                "post-validation-failed",
                "optimized artifact rejected"
        );

        GpuIrOptimizedArtifactCandidate candidate = new GpuIrOptimizedArtifactCandidate(
                original,
                optimized,
                proposal,
                invalidValidation,
                "caller-supplied-original-id",
                "caller-supplied-optimized-id",
                "",
                false,
                true,
                false,
                false,
                true,
                true,
                true,
                true,
                "candidate-ready",
                "none",
                "none"
        );

        assertTrue(candidate.candidateBuilt());
        assertFalse(candidate.optimizedValidationPassed());
        assertTrue(candidate.proofPresent());
        assertTrue(candidate.mutationAllowed());
        assertFalse(candidate.selectionReady());
        assertFalse(candidate.selectionApplied());
        assertFalse(candidate.selectedIrReplacement());
        assertEquals("blocked", candidate.status());
        assertEquals("post-validation-failed", candidate.firstBlocker());
        assertEquals("optimized-validation-failed", candidate.selectionFirstBlocker());
        assertEquals(IrGpuArtifactIdentity.stableIdentity(original), candidate.originalIrIdentity());
        assertEquals(IrGpuArtifactIdentity.stableIdentity(optimized), candidate.optimizedIrIdentity());
        assertTrue(candidate.candidateEnvelopeKey().contains("validation=post-validation-failed"));
        assertEquals("false", candidate.fields("candidate").get("candidate.selectionApplied"));
        assertEquals("optimized-validation-failed", candidate.fields("candidate").get("candidate.selectionFirstBlocker"));
    }

    @Test
    void noOpProposalProviderIsServiceLoaded() {
        List<GpuIrOptimizationProposalProvider> providers = ServiceLoader
                .load(GpuIrOptimizationProposalProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(GpuIrNoOpProposalProvider.class::isInstance)
                .toList();

        assertEquals(1, providers.size());
    }

    private static GpuIrOptimizationProposal proposed(IrGpuArtifact original, IrGpuArtifact optimized) {
        return GpuIrOptimizationProposal.proposed(
                "optimizer:test",
                "optimizer:test:1",
                original,
                optimized,
                GpuRuntimeIrOptimizationProofArtifact.fromFields("test", "proof-collected", Map.of()),
                List.of("test proposal")
        );
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
