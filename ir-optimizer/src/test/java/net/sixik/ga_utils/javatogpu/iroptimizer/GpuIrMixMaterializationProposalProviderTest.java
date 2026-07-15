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

class GpuIrMixMaterializationProposalProviderTest {

    @Test
    void materializesCanonicalInterpolationAsOpenClMixReviewCandidateWithoutFastMath() {
        IrGpuArtifact original = canonicalArtifact();

        GpuIrOptimizationProposal proposal = new GpuIrMixMaterializationProposalProvider()
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
                "body\n  set output[0] = intrinsic(mix template=\"\" args=[a, b, t])\n",
                optimized.module().methodBodies().get(0).body()
        );
        IrGpuTypedNode replacement = optimized.module().methodBodies().get(0).typedBody().nodes().get(2);
        assertEquals("GpuIrIntrinsicCall", replacement.kind());
        assertEquals("mix", replacement.attributes().get("name"));
        assertEquals("mix", replacement.attributes().get("backendName"));
        assertEquals(List.of(3, 7, 5), replacement.children().get("arguments"));
        assertTrue(optimized.regenerationMetadata().backendNeutralSourceReady());

        Map<String, String> fields = proposal.proofArtifact().fields();
        assertEquals("mix-materialization", fields.get("optimizerFamily"));
        assertEquals("mix", fields.get("targetIntrinsic"));
        assertEquals("1", fields.get("candidate.count"));
        assertEquals("1", fields.get("transformedNode.count"));
        assertEquals("1", fields.get("canonicalMix.count"));
        assertEquals("0", fields.get("expandedMix.count"));
        assertEquals("0", fields.get("madExpandedMix.count"));
        assertEquals("false", fields.get("safety.fastMathRequired"));
        assertEquals("true", fields.get("safety.strictFloatPreserved"));
        assertEquals("false", fields.get("safety.algebraicReassociationRequired"));
        assertEquals("linear-interpolation-to-opencl-mix", fields.get("runtimeEquivalencePayload.Case.0.RewriteKind"));
        assertEquals("a", fields.get("runtimeEquivalencePayload.Case.0.Input.1.Value"));
        assertEquals("b", fields.get("runtimeEquivalencePayload.Case.0.Input.2.Value"));
        assertEquals("t", fields.get("runtimeEquivalencePayload.Case.0.Input.3.Value"));
        assertEquals("true", fields.get("openClReview.sourceReady"));
        assertEquals(
                "kernel#2=(a + (t * (b - a)))->intrinsic(mix template=\"\" args=[a, b, t])",
                fields.get("firstTransformedNode")
        );
    }

    @Test
    void materializesMadExpandedInterpolationAsFastMathOpenClMixReviewCandidate() {
        IrGpuArtifact original = madExpandedArtifact(true);

        GpuIrOptimizationProposal proposal = new GpuIrMixMaterializationProposalProvider()
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
                "body\n  set output[0] = intrinsic(mix template=\"\" args=[(clampedWave * 0.1F), blend, isSolid])\n",
                optimized.module().methodBodies().get(0).body()
        );
        IrGpuTypedNode replacement = optimized.module().methodBodies().get(0).typedBody().nodes().get(2);
        assertEquals("GpuIrIntrinsicCall", replacement.kind());
        assertEquals("mix", replacement.attributes().get("backendName"));
        assertEquals(List.of(7, 3, 4), replacement.children().get("arguments"));

        Map<String, String> fields = proposal.proofArtifact().fields();
        assertEquals("mix-materialization", fields.get("optimizerFamily"));
        assertEquals("0", fields.get("canonicalMix.count"));
        assertEquals("0", fields.get("expandedMix.count"));
        assertEquals("1", fields.get("madExpandedMix.count"));
        assertEquals("true", fields.get("policy.fastMathAllowed"));
        assertEquals("true", fields.get("safety.fastMathRequired"));
        assertEquals("false", fields.get("safety.strictFloatPreserved"));
        assertEquals("true", fields.get("safety.algebraicReassociationRequired"));
        assertEquals("mad-expanded-linear-interpolation-to-opencl-mix", fields.get("runtimeEquivalencePayload.Case.0.RewriteKind"));
        assertEquals("(clampedWave * 0.1F)", fields.get("runtimeEquivalencePayload.Case.0.Input.1.Value"));
        assertEquals("blend", fields.get("runtimeEquivalencePayload.Case.0.Input.2.Value"));
        assertEquals("isSolid", fields.get("runtimeEquivalencePayload.Case.0.Input.3.Value"));
        assertEquals(
                "kernel#2=intrinsic(mad template=\"\" args=[blend, isSolid, ((1.0F - isSolid) * (clampedWave * 0.1F))])->intrinsic(mix template=\"\" args=[(clampedWave * 0.1F), blend, isSolid])",
                fields.get("firstTransformedNode")
        );
    }

    @Test
    void blocksMadExpandedInterpolationWhenFastMathIsDisabled() {
        IrGpuArtifact original = madExpandedArtifact(false);

        GpuIrOptimizationProposal proposal = new GpuIrMixMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "OPENCL")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertSame(original, proposal.originalArtifact());
        Map<String, String> fields = proposal.proofArtifact().fields();
        assertEquals("mix-materialization", fields.get("optimizerFamily"));
        assertEquals("1", fields.get("candidate.count"));
        assertEquals("0", fields.get("transformedNode.count"));
        assertEquals("1", fields.get("skipped.fastMathPolicy.count"));
        assertEquals("fast-math-policy-disabled", fields.get("firstBlocker"));
    }

    @Test
    void materializationProviderIsServiceLoaded() {
        List<GpuIrOptimizationProposalProvider> providers = ServiceLoader
                .load(GpuIrOptimizationProposalProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(GpuIrMixMaterializationProposalProvider.class::isInstance)
                .toList();

        assertEquals(1, providers.size());
    }

    private static IrGpuArtifact canonicalArtifact() {
        IrGpuMethodBody methodBody = new IrGpuMethodBody(
                "entry",
                "kernel",
                "kernel",
                "ir-text-v1",
                "body\n  set output[0] = (a + (t * (b - a)))\n",
                canonicalTypedBody(),
                IrGpuBodyIndex.empty(),
                List.of(),
                IrGpuSourceLocation.unknown("kernel")
        );
        return artifact(methodBody, IrGpuOptimizerPolicyMetadata.defaultStrict());
    }

    private static IrGpuTypedBody canonicalTypedBody() {
        return new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0),
                List.of(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of(
                                "target", List.of(1),
                                "value", List.of(2)
                        )),
                        new IrGpuTypedNode(1, "GpuIrVariableRef", Map.of("name", "output[0]"), Map.of()),
                        new IrGpuTypedNode(2, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(3),
                                "right", List.of(4)
                        )),
                        new IrGpuTypedNode(3, "GpuIrVariableRef", Map.of("name", "a"), Map.of()),
                        new IrGpuTypedNode(4, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                                "left", List.of(5),
                                "right", List.of(6)
                        )),
                        new IrGpuTypedNode(5, "GpuIrVariableRef", Map.of("name", "t"), Map.of()),
                        new IrGpuTypedNode(6, "GpuIrBinary", Map.of("operator", "-"), Map.of(
                                "left", List.of(7),
                                "right", List.of(3)
                        )),
                        new IrGpuTypedNode(7, "GpuIrVariableRef", Map.of("name", "b"), Map.of())
                )
        );
    }

    private static IrGpuArtifact madExpandedArtifact(boolean fastMath) {
        IrGpuMethodBody methodBody = new IrGpuMethodBody(
                "entry",
                "kernel",
                "kernel",
                "ir-text-v1",
                "body\n  set output[0] = intrinsic(mad template=\"\" args=[blend, isSolid, ((1.0F - isSolid) * (clampedWave * 0.1F))])\n",
                madExpandedTypedBody(),
                IrGpuBodyIndex.empty(),
                List.of(),
                IrGpuSourceLocation.unknown("kernel")
        );
        return artifact(methodBody, fastMath
                ? IrGpuOptimizerPolicyMetadata.fromGpuOptimize(true)
                : IrGpuOptimizerPolicyMetadata.defaultStrict());
    }

    private static IrGpuTypedBody madExpandedTypedBody() {
        return new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0),
                List.of(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of(
                                "target", List.of(1),
                                "value", List.of(2)
                        )),
                        new IrGpuTypedNode(1, "GpuIrVariableRef", Map.of("name", "output[0]"), Map.of()),
                        new IrGpuTypedNode(2, "GpuIrIntrinsicCall", Map.of(
                                "name", "mad",
                                "backendName", "mad",
                                "codeTemplate", ""
                        ), Map.of("arguments", List.of(3, 4, 5))),
                        new IrGpuTypedNode(3, "GpuIrVariableRef", Map.of("name", "blend"), Map.of()),
                        new IrGpuTypedNode(4, "GpuIrVariableRef", Map.of("name", "isSolid"), Map.of()),
                        new IrGpuTypedNode(5, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                                "left", List.of(6),
                                "right", List.of(7)
                        )),
                        new IrGpuTypedNode(6, "GpuIrBinary", Map.of("operator", "-"), Map.of(
                                "left", List.of(8),
                                "right", List.of(4)
                        )),
                        new IrGpuTypedNode(7, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                                "left", List.of(9),
                                "right", List.of(10)
                        )),
                        new IrGpuTypedNode(8, "GpuIrLiteral", Map.of("sourceText", "1.0F"), Map.of()),
                        new IrGpuTypedNode(9, "GpuIrVariableRef", Map.of("name", "clampedWave"), Map.of()),
                        new IrGpuTypedNode(10, "GpuIrLiteral", Map.of("sourceText", "0.1F"), Map.of())
                )
        );
    }

    private static IrGpuArtifact artifact(
            IrGpuMethodBody methodBody,
            IrGpuOptimizerPolicyMetadata optimizerPolicyMetadata
    ) {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule("kernel", "kernel", List.of(), List.of(), List.of(methodBody)),
                List.of(
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of()),
                        new IrGpuEntryParameter("a", "float", "PRIVATE", false, List.of()),
                        new IrGpuEntryParameter("b", "float", "PRIVATE", false, List.of()),
                        new IrGpuEntryParameter("t", "float", "PRIVATE", false, List.of()),
                        new IrGpuEntryParameter("blend", "float", "PRIVATE", false, List.of()),
                        new IrGpuEntryParameter("isSolid", "float", "PRIVATE", false, List.of()),
                        new IrGpuEntryParameter("clampedWave", "float", "PRIVATE", false, List.of())
                ),
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                optimizerPolicyMetadata,
                IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/test/Kernel/kernel.cl")),
                "opencl",
                "off"
        );
    }
}
