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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrMadFmaMaterializationProposalProviderTest {

    @Test
    void materializesMultiplyAddAsOpenClMadReviewCandidate() {
        IrGpuArtifact original = artifact(true);

        GpuIrOptimizationProposal proposal = new GpuIrMadFmaMaterializationProposalProvider()
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
                "body\n  set output[0] = intrinsic(mad template=\"\" args=[left, right, addend])\n",
                optimized.module().methodBodies().get(0).body()
        );
        IrGpuTypedNode replacement = optimized.module().methodBodies().get(0).typedBody().nodes().get(2);
        assertEquals("GpuIrIntrinsicCall", replacement.kind());
        assertEquals("mad", replacement.attributes().get("name"));
        assertEquals("mad", replacement.attributes().get("backendName"));
        assertEquals(List.of(4, 5, 6), replacement.children().get("arguments"));
        assertTrue(optimized.regenerationMetadata().backendNeutralSourceReady());

        Map<String, String> fields = proposal.proofArtifact().fields();
        assertEquals("mad-fma-materialization", fields.get("optimizerFamily"));
        assertEquals("mad", fields.get("targetIntrinsic"));
        assertEquals("1", fields.get("candidate.count"));
        assertEquals("1", fields.get("transformedNode.count"));
        assertEquals("1", fields.get("bodyTextReplacement.count"));
        assertEquals("1", fields.get("fixedPoint.pass.count"));
        assertEquals("true", fields.get("rewrite.materialized"));
        assertEquals("false", fields.get("provider.mutatesOriginal"));
        assertEquals("true", fields.get("policy.fastMathAllowed"));
        assertEquals("true", fields.get("safety.fastMathRequired"));
        assertEquals("false", fields.get("safety.strictFloatPreserved"));
        assertEquals("true", fields.get("runtimeEquivalencePayload.present"));
        assertEquals("true", fields.get("runtimeEquivalencePayload.passed"));
        assertEquals("static-fast-math-multiply-add-to-mad", fields.get("runtimeEquivalencePayload.ReferenceMode"));
        assertEquals("1", fields.get("runtimeEquivalencePayload.Case.Count"));
        assertEquals("mad-fma-to-opencl-mad", fields.get("runtimeEquivalencePayload.Case.0.RewriteKind"));
        assertEquals("((left * right) + addend)", fields.get("runtimeEquivalencePayload.Case.0.Output.0.PreOptimization"));
        assertEquals("intrinsic(mad template=\"\" args=[left, right, addend])", fields.get("runtimeEquivalencePayload.Case.0.Output.0.PostOptimization"));
        assertEquals("true", fields.get("openClReview.sourceReady"));
        assertEquals("kernel#2=((left * right) + addend)->intrinsic(mad template=\"\" args=[left, right, addend])", fields.get("firstTransformedNode"));
    }

    @Test
    void blocksMaterializationWhenFastMathPolicyIsStrict() {
        IrGpuArtifact original = artifact(false);

        GpuIrOptimizationProposal proposal = new GpuIrMadFmaMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertTrue(proposal.diagnostics().get(0).contains("fast-math-policy-disabled"));
        assertEquals("1", proposal.proofArtifact().fields().get("candidate.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("skipped.fastMathPolicy.count"));
        assertEquals("false", proposal.proofArtifact().fields().get("rewrite.materialized"));
        assertEquals("false", proposal.proofArtifact().fields().get("policy.fastMathAllowed"));
    }

    @Test
    void materializationProviderIsServiceLoaded() {
        List<GpuIrOptimizationProposalProvider> providers = ServiceLoader
                .load(GpuIrOptimizationProposalProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(GpuIrMadFmaMaterializationProposalProvider.class::isInstance)
                .toList();

        assertEquals(1, providers.size());
    }

    private static IrGpuArtifact artifact(boolean fastMath) {
        IrGpuMethodBody methodBody = new IrGpuMethodBody(
                "entry",
                "kernel",
                "kernel",
                "ir-text-v1",
                "body\n  set output[0] = ((left * right) + addend)\n",
                typedBody(),
                IrGpuBodyIndex.empty(),
                List.of(),
                IrGpuSourceLocation.unknown("kernel")
        );
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule("kernel", "kernel", List.of(), List.of(), List.of(methodBody)),
                List.of(
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of()),
                        new IrGpuEntryParameter("left", "float", "PRIVATE", false, List.of()),
                        new IrGpuEntryParameter("right", "float", "PRIVATE", false, List.of()),
                        new IrGpuEntryParameter("addend", "float", "PRIVATE", false, List.of())
                ),
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                fastMath ? IrGpuOptimizerPolicyMetadata.fromGpuOptimize(true) : IrGpuOptimizerPolicyMetadata.defaultStrict(),
                IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/test/Kernel/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuTypedBody typedBody() {
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
                                "right", List.of(6)
                        )),
                        new IrGpuTypedNode(3, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                                "left", List.of(4),
                                "right", List.of(5)
                        )),
                        new IrGpuTypedNode(4, "GpuIrVariableRef", Map.of("name", "left"), Map.of()),
                        new IrGpuTypedNode(5, "GpuIrVariableRef", Map.of("name", "right"), Map.of()),
                        new IrGpuTypedNode(6, "GpuIrVariableRef", Map.of("name", "addend"), Map.of())
                )
        );
    }
}
