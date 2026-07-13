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

class GpuIrClampMaterializationProposalProviderTest {

    @Test
    void materializesGeneratedMinMaxShapeAsOpenClClampReviewCandidate() {
        IrGpuArtifact original = artifact();

        GpuIrOptimizationProposal proposal = new GpuIrClampMaterializationProposalProvider()
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
                "body\n  set output[0] = intrinsic(clamp template=\"\" args=[wave1, (-1.0F), 1.0F])\n",
                optimized.module().methodBodies().get(0).body()
        );
        IrGpuTypedNode replacement = optimized.module().methodBodies().get(0).typedBody().nodes().get(2);
        assertEquals("GpuIrIntrinsicCall", replacement.kind());
        assertEquals("clamp", replacement.attributes().get("name"));
        assertEquals("clamp", replacement.attributes().get("backendName"));
        assertEquals(List.of(4, 5, 6), replacement.children().get("arguments"));
        assertTrue(optimized.regenerationMetadata().backendNeutralSourceReady());

        Map<String, String> fields = proposal.proofArtifact().fields();
        assertEquals("clamp-materialization", fields.get("optimizerFamily"));
        assertEquals("clamp", fields.get("targetIntrinsic"));
        assertEquals("1", fields.get("candidate.count"));
        assertEquals("1", fields.get("transformedNode.count"));
        assertEquals("1", fields.get("bodyTextReplacement.count"));
        assertEquals("1", fields.get("fixedPoint.pass.count"));
        assertEquals("true", fields.get("rewrite.materialized"));
        assertEquals("false", fields.get("provider.mutatesOriginal"));
        assertEquals("false", fields.get("safety.fastMathRequired"));
        assertEquals("true", fields.get("safety.strictFloatPreserved"));
        assertEquals("true", fields.get("safety.argumentOrderPreserved"));
        assertEquals("true", fields.get("runtimeEquivalencePayload.present"));
        assertEquals("true", fields.get("runtimeEquivalencePayload.passed"));
        assertEquals("static-min-max-to-clamp", fields.get("runtimeEquivalencePayload.ReferenceMode"));
        assertEquals("1", fields.get("runtimeEquivalencePayload.Case.Count"));
        assertEquals("min-max-to-opencl-clamp", fields.get("runtimeEquivalencePayload.Case.0.RewriteKind"));
        assertEquals("wave1", fields.get("runtimeEquivalencePayload.Case.0.Input.1.Value"));
        assertEquals("(-1.0F)", fields.get("runtimeEquivalencePayload.Case.0.Input.2.Value"));
        assertEquals("1.0F", fields.get("runtimeEquivalencePayload.Case.0.Input.3.Value"));
        assertEquals("true", fields.get("openClReview.sourceReady"));
        assertEquals(
                "kernel#2=intrinsic(min template=\"\" args=[intrinsic(max template=\"\" args=[wave1, (-1.0F)]), 1.0F])->intrinsic(clamp template=\"\" args=[wave1, (-1.0F), 1.0F])",
                fields.get("firstTransformedNode")
        );
    }

    @Test
    void materializationProviderIsServiceLoaded() {
        List<GpuIrOptimizationProposalProvider> providers = ServiceLoader
                .load(GpuIrOptimizationProposalProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(GpuIrClampMaterializationProposalProvider.class::isInstance)
                .toList();

        assertEquals(1, providers.size());
    }

    private static IrGpuArtifact artifact() {
        IrGpuMethodBody methodBody = new IrGpuMethodBody(
                "entry",
                "kernel",
                "kernel",
                "ir-text-v1",
                "body\n  set output[0] = intrinsic(min template=\"\" args=[intrinsic(max template=\"\" args=[wave1, (-1.0F)]), 1.0F])\n",
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
                        new IrGpuEntryParameter("wave1", "float", "PRIVATE", false, List.of())
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
                        new IrGpuTypedNode(2, "GpuIrIntrinsicCall", Map.of(
                                "name", "min",
                                "backendName", "min",
                                "codeTemplate", ""
                        ), Map.of("arguments", List.of(3, 6))),
                        new IrGpuTypedNode(3, "GpuIrIntrinsicCall", Map.of(
                                "name", "max",
                                "backendName", "max",
                                "codeTemplate", ""
                        ), Map.of("arguments", List.of(4, 5))),
                        new IrGpuTypedNode(4, "GpuIrVariableRef", Map.of("name", "wave1"), Map.of()),
                        new IrGpuTypedNode(5, "GpuIrLiteral", Map.of("sourceText", "(-1.0F)"), Map.of()),
                        new IrGpuTypedNode(6, "GpuIrLiteral", Map.of("sourceText", "1.0F"), Map.of())
                )
        );
    }
}
