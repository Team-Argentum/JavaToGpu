package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBodyIndex;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrConstantFoldingPreviewProposalProviderTest {

    @Test
    void recordsSimpleLiteralBinaryCandidatesWithoutProposingRewrite() {
        IrGpuArtifact original = artifact(typedBody(
                new IrGpuTypedNode(0, "GpuIrReturn", Map.of(), Map.of("value", List.of(1))),
                new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                        "left", List.of(2),
                        "right", List.of(3)
                )),
                new IrGpuTypedNode(2, "GpuIrLiteral", Map.of("sourceText", "2"), Map.of()),
                new IrGpuTypedNode(3, "GpuIrLiteral", Map.of("sourceText", "3"), Map.of())
        ));

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingPreviewProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertSame(original, proposal.originalArtifact());
        assertEquals("preview-candidates-recorded", proposal.proofArtifact().verdict());
        assertEquals("1", proposal.proofArtifact().fields().get("candidate.count"));
        assertEquals("false", proposal.proofArtifact().fields().get("rewrite.proposed"));
        assertEquals("true", proposal.proofArtifact().fields().get("previewOnly"));
        assertEquals("{+=1}", proposal.proofArtifact().fields().get("operator.counts"));
        assertEquals("{integer=1}", proposal.proofArtifact().fields().get("numericKind.counts"));
        assertEquals("+", proposal.proofArtifact().fields().get("firstOperator"));
        assertEquals("integer", proposal.proofArtifact().fields().get("firstNumericKind"));
        assertEquals("5", proposal.proofArtifact().fields().get("firstFoldedValue"));
        assertTrue(proposal.diagnostics().get(0).contains("no rewrite was proposed"));
    }

    @Test
    void recordsOperatorAndNumericKindBreakdownAcrossCandidates() {
        IrGpuArtifact original = artifact(typedBody(
                new IrGpuTypedNode(0, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                        "left", List.of(1),
                        "right", List.of(2)
                )),
                new IrGpuTypedNode(1, "GpuIrLiteral", Map.of("sourceText", "2"), Map.of()),
                new IrGpuTypedNode(2, "GpuIrLiteral", Map.of("sourceText", "3"), Map.of()),
                new IrGpuTypedNode(3, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                        "left", List.of(4),
                        "right", List.of(5)
                )),
                new IrGpuTypedNode(4, "GpuIrLiteral", Map.of("sourceText", "1.5"), Map.of()),
                new IrGpuTypedNode(5, "GpuIrLiteral", Map.of("sourceText", "2"), Map.of()),
                new IrGpuTypedNode(6, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                        "left", List.of(7),
                        "right", List.of(8)
                )),
                new IrGpuTypedNode(7, "GpuIrLiteral", Map.of("sourceText", "4"), Map.of()),
                new IrGpuTypedNode(8, "GpuIrLiteral", Map.of("sourceText", "5"), Map.of())
        ));

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingPreviewProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals("3", proposal.proofArtifact().fields().get("candidate.count"));
        assertEquals("{*=1,+=2}", proposal.proofArtifact().fields().get("operator.counts"));
        assertEquals("{decimal=1,integer=2}", proposal.proofArtifact().fields().get("numericKind.counts"));
        assertEquals("+", proposal.proofArtifact().fields().get("firstOperator"));
        assertEquals("integer", proposal.proofArtifact().fields().get("firstNumericKind"));
        assertEquals("5", proposal.proofArtifact().fields().get("firstFoldedValue"));
    }

    @Test
    void skipsUnsafeOrNonPlainLiteralShapes() {
        IrGpuArtifact original = artifact(typedBody(
                new IrGpuTypedNode(0, "GpuIrBinary", Map.of("operator", "/"), Map.of(
                        "left", List.of(1),
                        "right", List.of(2)
                )),
                new IrGpuTypedNode(1, "GpuIrLiteral", Map.of("sourceText", "1"), Map.of()),
                new IrGpuTypedNode(2, "GpuIrLiteral", Map.of("sourceText", "0"), Map.of()),
                new IrGpuTypedNode(3, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                        "left", List.of(4),
                        "right", List.of(5)
                )),
                new IrGpuTypedNode(4, "GpuIrLiteral", Map.of("sourceText", "1.0f"), Map.of()),
                new IrGpuTypedNode(5, "GpuIrLiteral", Map.of("sourceText", "2"), Map.of())
        ));

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingPreviewProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertEquals("preview-no-candidates", proposal.proofArtifact().verdict());
        assertEquals("0", proposal.proofArtifact().fields().get("candidate.count"));
        assertEquals("{}", proposal.proofArtifact().fields().get("operator.counts"));
        assertEquals("{}", proposal.proofArtifact().fields().get("numericKind.counts"));
        assertEquals("no-simple-literal-binary-candidates", proposal.proofArtifact().fields().get("firstBlocker"));
    }

    @Test
    void previewStaysNoChangeEvenWhenMutationAllowed() {
        IrGpuArtifact original = artifact(typedBody(
                new IrGpuTypedNode(0, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                        "left", List.of(1),
                        "right", List.of(2)
                )),
                new IrGpuTypedNode(1, "GpuIrLiteral", Map.of("sourceText", "4"), Map.of()),
                new IrGpuTypedNode(2, "GpuIrLiteral", Map.of("sourceText", "5"), Map.of())
        ));

        GpuIrOptimizationSandwichReport report = GpuIrOptimizationSandwichRunner.alwaysValid()
                .run(new GpuIrOptimizationProposalRequest(original, "diagnostic", true, Map.of()),
                        new GpuIrConstantFoldingPreviewProposalProvider());

        assertEquals(GpuIrOptimizationSandwichStatus.NO_CHANGE, report.status());
        assertSame(original, report.selectedArtifact());
        assertTrue(report.selectedOriginal());
        assertFalse(report.selectedOptimized());
        assertEquals("20", report.proposal().orElseThrow().proofArtifact().fields().get("firstFoldedValue"));
    }

    @Test
    void previewProviderIsServiceLoaded() {
        List<GpuIrOptimizationProposalProvider> providers = ServiceLoader
                .load(GpuIrOptimizationProposalProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(GpuIrConstantFoldingPreviewProposalProvider.class::isInstance)
                .toList();

        assertEquals(1, providers.size());
    }

    private static IrGpuTypedBody typedBody(IrGpuTypedNode... nodes) {
        return new IrGpuTypedBody(IrGpuTypedBody.FORMAT, List.of(nodes[0].id()), List.of(nodes));
    }

    private static IrGpuArtifact artifact(IrGpuTypedBody typedBody) {
        IrGpuMethodBody methodBody = new IrGpuMethodBody(
                "entry",
                "kernel",
                "jtg_kernel",
                "ir-text-v1",
                "body\n  return 1\n",
                typedBody,
                IrGpuBodyIndex.empty(),
                List.of(),
                IrGpuSourceLocation.unknown("kernel")
        );
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule("kernel", "jtg_kernel", List.of(), List.of(), List.of(methodBody)),
                List.of(IrGpuBackendOutput.openClSource("test.cl")),
                "opencl",
                "off"
        );
    }
}
