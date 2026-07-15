package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBodyIndex;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuOptimizerPolicyMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrStepMaterializationProposalProviderTest {

    @Test
    void materializesInclusiveTernaryMaskAsDirectOpenClStepReviewCandidate() {
        IrGpuArtifact original = artifact(">=");

        GpuIrOptimizationProposal proposal = new GpuIrStepMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "OPENCL")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        assertSame(original, proposal.originalArtifact());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertEquals(
                "body\n  set output[0] = intrinsic(step template=\"\" args=[threshold, blend])\n",
                optimized.module().methodBodies().get(0).body()
        );
        IrGpuTypedNode replacement = optimized.module().methodBodies().get(0).typedBody().nodes().get(2);
        assertEquals("GpuIrIntrinsicCall", replacement.kind());
        assertEquals("step", replacement.attributes().get("name"));
        assertEquals("step", replacement.attributes().get("backendName"));
        assertEquals(List.of(5, 4), replacement.children().get("arguments"));
        assertTrue(optimized.regenerationMetadata().backendNeutralSourceReady());

        Map<String, String> fields = proposal.proofArtifact().fields();
        assertEquals("step-materialization", fields.get("optimizerFamily"));
        assertEquals("step", fields.get("targetIntrinsic"));
        assertEquals("1", fields.get("candidate.count"));
        assertEquals("1", fields.get("transformedNode.count"));
        assertEquals("1", fields.get("bodyTextReplacement.count"));
        assertEquals("1", fields.get("fixedPoint.pass.count"));
        assertEquals("1", fields.get("directStep.count"));
        assertEquals("0", fields.get("invertedStep.count"));
        assertEquals("true", fields.get("rewrite.materialized"));
        assertEquals("false", fields.get("provider.mutatesOriginal"));
        assertEquals("false", fields.get("safety.fastMathRequired"));
        assertEquals("true", fields.get("safety.strictComparisonPreserved"));
        assertEquals("true", fields.get("safety.equalityBehaviorPreserved"));
        assertEquals("true", fields.get("safety.nanComparisonPreserved"));
        assertEquals("true", fields.get("runtimeEquivalencePayload.present"));
        assertEquals("true", fields.get("runtimeEquivalencePayload.passed"));
        assertEquals("static-ternary-mask-to-step", fields.get("runtimeEquivalencePayload.ReferenceMode"));
        assertEquals("1", fields.get("runtimeEquivalencePayload.Case.Count"));
        assertEquals("ternary-mask-to-opencl-step", fields.get("runtimeEquivalencePayload.Case.0.RewriteKind"));
        assertEquals("threshold", fields.get("runtimeEquivalencePayload.Case.0.Input.1.Value"));
        assertEquals("blend", fields.get("runtimeEquivalencePayload.Case.0.Input.2.Value"));
        assertEquals(">=", fields.get("runtimeEquivalencePayload.Case.0.Input.3.Value"));
        assertEquals("true", fields.get("openClReview.sourceReady"));
        assertEquals(
                "kernel#2=((blend >= threshold) ? 1.0F : 0.0F)->intrinsic(step template=\"\" args=[threshold, blend])",
                fields.get("firstTransformedNode")
        );
    }

    @Test
    void materializesStrictGreaterThanTernaryMaskAsInvertedOpenClStepReviewCandidate() {
        IrGpuArtifact original = artifact(">");

        GpuIrOptimizationProposal proposal = new GpuIrStepMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "OPENCL")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        assertSame(original, proposal.originalArtifact());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertEquals(
                "body\n  set output[0] = (1.0F - intrinsic(step template=\"\" args=[blend, threshold]))\n",
                optimized.module().methodBodies().get(0).body()
        );

        List<IrGpuTypedNode> nodes = optimized.module().methodBodies().get(0).typedBody().nodes();
        IrGpuTypedNode root = nodes.get(2);
        assertEquals("GpuIrBinary", root.kind());
        assertEquals("-", root.attributes().get("operator"));
        assertEquals(List.of(6), root.children().get("left"));
        assertEquals(List.of(8), root.children().get("right"));
        IrGpuTypedNode step = nodes.stream()
                .filter(node -> node.id() == 8)
                .findFirst()
                .orElseThrow();
        assertEquals("GpuIrIntrinsicCall", step.kind());
        assertEquals("step", step.attributes().get("backendName"));
        assertEquals(List.of(4, 5), step.children().get("arguments"));
        assertTrue(optimized.regenerationMetadata().backendNeutralSourceReady());

        Map<String, String> fields = proposal.proofArtifact().fields();
        assertEquals("step-materialization", fields.get("optimizerFamily"));
        assertEquals("0", fields.get("directStep.count"));
        assertEquals("1", fields.get("invertedStep.count"));
        assertEquals("false", fields.get("safety.fastMathRequired"));
        assertEquals("true", fields.get("safety.strictComparisonPreserved"));
        assertEquals("true", fields.get("safety.equalityBehaviorPreserved"));
        assertEquals("true", fields.get("safety.nanComparisonPreserved"));
        assertEquals("ternary-mask-to-inverted-opencl-step", fields.get("runtimeEquivalencePayload.Case.0.RewriteKind"));
        assertEquals("blend", fields.get("runtimeEquivalencePayload.Case.0.Input.1.Value"));
        assertEquals("threshold", fields.get("runtimeEquivalencePayload.Case.0.Input.2.Value"));
        assertEquals(">", fields.get("runtimeEquivalencePayload.Case.0.Input.3.Value"));
        assertEquals(
                "kernel#2=((blend > threshold) ? 1.0F : 0.0F)->(1.0F - intrinsic(step template=\"\" args=[blend, threshold]))",
                fields.get("firstTransformedNode")
        );
    }

    @Test
    void materializationProviderIsServiceLoaded() {
        List<GpuIrOptimizationProposalProvider> providers = ServiceLoader
                .load(GpuIrOptimizationProposalProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(GpuIrStepMaterializationProposalProvider.class::isInstance)
                .toList();

        assertEquals(1, providers.size());
    }

    private static IrGpuArtifact artifact(String operator) {
        IrGpuMethodBody methodBody = new IrGpuMethodBody(
                "entry",
                "kernel",
                "kernel",
                "ir-text-v1",
                "body\n  set output[0] = ((blend " + operator + " threshold) ? 1.0F : 0.0F)\n",
                typedBody(operator),
                IrGpuBodyIndex.empty(),
                List.of(),
                IrGpuSourceLocation.unknown("kernel")
        );
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule("kernel", "kernel", List.of(), List.of(), List.of(methodBody)),
                List.of(
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of()),
                        new IrGpuEntryParameter("blend", "float", "PRIVATE", false, List.of()),
                        new IrGpuEntryParameter("threshold", "float", "PRIVATE", false, List.of())
                ),
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                IrGpuOptimizerPolicyMetadata.defaultStrict(),
                IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/test/Kernel/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuTypedBody typedBody(String operator) {
        return new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0),
                List.of(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of(
                                "target", List.of(1),
                                "value", List.of(2)
                        )),
                        new IrGpuTypedNode(1, "GpuIrVariableRef", Map.of("name", "output[0]"), Map.of()),
                        new IrGpuTypedNode(2, "GpuIrTernary", Map.of(), Map.of(
                                "condition", List.of(3),
                                "then", List.of(6),
                                "else", List.of(7)
                        )),
                        new IrGpuTypedNode(3, "GpuIrBinary", Map.of("operator", operator), Map.of(
                                "left", List.of(4),
                                "right", List.of(5)
                        )),
                        new IrGpuTypedNode(4, "GpuIrVariableRef", Map.of("name", "blend"), Map.of()),
                        new IrGpuTypedNode(5, "GpuIrVariableRef", Map.of("name", "threshold"), Map.of()),
                        new IrGpuTypedNode(6, "GpuIrLiteral", Map.of("sourceText", "1.0F"), Map.of()),
                        new IrGpuTypedNode(7, "GpuIrLiteral", Map.of("sourceText", "0.0F"), Map.of())
                )
        );
    }
}
