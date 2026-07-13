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

class GpuIrLoopVectorizationMaterializationProposalProviderTest {

    @Test
    void materializesFixedWidthContiguousFloatReductionAsVload4ReviewCandidate() {
        IrGpuArtifact original = artifact("""
                body
                  var int id = intrinsic(get_global_id template="" args=[0])
                  var float sum = 0.0F
                  for init=(var int i = 0) cond=(i < 4) update=(set i = (i + 1))
                    set sum = (sum + input[((id * 4) + i)])
                  set output[id] = sum
                """);

        GpuIrOptimizationProposal proposal = new GpuIrLoopVectorizationMaterializationProposalProvider()
                .propose(openClRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        assertSame(original, proposal.originalArtifact());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertEquals("""
                body
                  var int id = intrinsic(get_global_id template="" args=[0])
                  var Float4 sum_vec4 = intrinsic(vload4 template="" args=[0, (&input[(id * 4)])])
                  var float sum = ((((0.0F + sum_vec4.x) + sum_vec4.y) + sum_vec4.z) + sum_vec4.w)
                  set output[id] = sum
                """, optimized.module().methodBodies().get(0).body());
        IrGpuTypedBody optimizedTypedBody = optimized.module().methodBodies().get(0).typedBody();
        assertTrue(optimizedTypedBody.available());
        assertTrue(optimizedTypedBody.nodes().stream().anyMatch(node -> "GpuIrIntrinsicCall".equals(node.kind())
                && "vload4".equals(node.attributes().get("backendName"))
                && "Float4".equals(node.attributes().get("resultType"))));
        assertTrue(optimizedTypedBody.nodes().stream().anyMatch(node -> "GpuIrFieldAccess".equals(node.kind())
                && "w".equals(node.attributes().get("fieldName"))));
        assertTrue(optimized.regenerationMetadata().backendNeutralSourceReady());

        Map<String, String> fields = proposal.proofArtifact().fields();
        assertEquals("loop-vectorization-materialization", fields.get("optimizerFamily"));
        assertEquals("4", fields.get("vectorWidth"));
        assertEquals("Float4", fields.get("vectorType"));
        assertEquals("vload4", fields.get("loadIntrinsic"));
        assertEquals("ordered-scalar-float-sum", fields.get("reductionKind"));
        assertEquals("1", fields.get("candidate.count"));
        assertEquals("1", fields.get("transformedLoop.count"));
        assertEquals("1", fields.get("bodyTextReplacement.count"));
        assertEquals("1", fields.get("typedBody.materialized.count"));
        assertEquals("0", fields.get("typedBody.invalidated.count"));
        assertEquals("true", fields.get("rewrite.materialized"));
        assertEquals("false", fields.get("provider.mutatesOriginal"));
        assertEquals("true", fields.get("runtimeEquivalencePayload.present"));
        assertEquals("true", fields.get("runtimeEquivalencePayload.passed"));
        assertEquals("static-vload4-ordered-scalar-reduction", fields.get("runtimeEquivalencePayload.ReferenceMode"));
        assertEquals("1", fields.get("runtimeEquivalencePayload.Case.Count"));
        assertEquals(
                "fixed-width-contiguous-vload4-ordered-reduction",
                fields.get("runtimeEquivalencePayload.Case.0.RewriteKind")
        );
        assertEquals("true", fields.get("safety.orderedReductionPreserved"));
        assertEquals("false", fields.get("safety.reassociationRequired"));
        assertEquals("false", fields.get("safety.fastMathRequired"));
        assertEquals("true", fields.get("safety.typedBodyMaterializedForReview"));
        assertEquals("false", fields.get("safety.typedBodyInvalidatedForReview"));
        assertEquals("true", fields.get("openClReview.sourceReady"));
        assertTrue(fields.get("firstReplacement").contains("intrinsic(vload4 template=\"\" args=[0, (&input[(id * 4)])])"));
    }

    @Test
    void materializesMadIndexedContiguousFloatReductionAsSameVload4ReviewCandidate() {
        IrGpuArtifact original = artifact("""
                body
                  var int id = intrinsic(get_global_id template="" args=[0])
                  var float sum = 0.0f
                  for init=(var int i = 0) cond=(i < 4) update=(set i = (i + 1))
                    set sum = (sum + input[intrinsic(mad template="" args=[id, 4, i])])
                  set output[id] = sum
                """);

        GpuIrOptimizationProposal proposal = new GpuIrLoopVectorizationMaterializationProposalProvider()
                .propose(openClRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        assertEquals("""
                body
                  var int id = intrinsic(get_global_id template="" args=[0])
                  var Float4 sum_vec4 = intrinsic(vload4 template="" args=[0, (&input[(id * 4)])])
                  var float sum = ((((0.0f + sum_vec4.x) + sum_vec4.y) + sum_vec4.z) + sum_vec4.w)
                  set output[id] = sum
                """, proposal.optimizedArtifact().orElseThrow().module().methodBodies().get(0).body());
        assertEquals("1", proposal.proofArtifact().fields().get("transformedLoop.count"));
        assertEquals("intrinsic(mad template=\"\" args=[id, 4, i])", proposal.proofArtifact().fields()
                .get("runtimeEquivalencePayload.Case.0.Input.3.Value"));
    }

    @Test
    void materializesPrecomputedContiguousBaseTempAsVload4ReviewCandidate() {
        IrGpuArtifact original = artifact("""
                body
                  var int id = intrinsic(get_global_id template="" args=[0])
                  var int base = (id * 4)
                  var float sum = 0.0F
                  for init=(var int i = 0) cond=(i < 4) update=(set i = (i + 1))
                    set sum = (sum + input[(base + i)])
                  set output[id] = sum
                """);

        GpuIrOptimizationProposal proposal = new GpuIrLoopVectorizationMaterializationProposalProvider()
                .propose(openClRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        assertEquals("""
                body
                  var int id = intrinsic(get_global_id template="" args=[0])
                  var int base = (id * 4)
                  var Float4 sum_vec4 = intrinsic(vload4 template="" args=[0, (&input[base])])
                  var float sum = ((((0.0F + sum_vec4.x) + sum_vec4.y) + sum_vec4.z) + sum_vec4.w)
                  set output[id] = sum
                """, proposal.optimizedArtifact().orElseThrow().module().methodBodies().get(0).body());
        assertTrue(proposal.optimizedArtifact().orElseThrow().module().methodBodies().get(0).typedBody().available());
        assertEquals("(base + i)", proposal.proofArtifact().fields()
                .get("runtimeEquivalencePayload.Case.0.Input.3.Value"));
        assertTrue(proposal.proofArtifact().fields().get("firstReplacement").contains("(&input[base])"));
    }

    @Test
    void materializesConstantOffsetContiguousBaseAsVload4ReviewCandidate() {
        IrGpuArtifact original = artifact("""
                body
                  var int id = intrinsic(get_global_id template="" args=[0])
                  var float sum = 0.0F
                  for init=(var int i = 0) cond=(i < 4) update=(set i = (i + 1))
                    set sum = (sum + input[(((id * 4) + 8) + i)])
                  set output[id] = sum
                """);

        GpuIrOptimizationProposal proposal = new GpuIrLoopVectorizationMaterializationProposalProvider()
                .propose(openClRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        assertEquals("""
                body
                  var int id = intrinsic(get_global_id template="" args=[0])
                  var Float4 sum_vec4 = intrinsic(vload4 template="" args=[0, (&input[((id * 4) + 8)])])
                  var float sum = ((((0.0F + sum_vec4.x) + sum_vec4.y) + sum_vec4.z) + sum_vec4.w)
                  set output[id] = sum
                """, proposal.optimizedArtifact().orElseThrow().module().methodBodies().get(0).body());
        assertTrue(proposal.optimizedArtifact().orElseThrow().module().methodBodies().get(0).typedBody().available());
        assertEquals("(((id * 4) + 8) + i)", proposal.proofArtifact().fields()
                .get("runtimeEquivalencePayload.Case.0.Input.3.Value"));
        assertTrue(proposal.proofArtifact().fields().get("firstReplacement").contains("(&input[((id * 4) + 8)])"));
    }

    @Test
    void blocksMaterializationWhenBackendTargetIsNotOpenCl() {
        IrGpuArtifact original = artifact("""
                body
                  var float sum = 0.0F
                  for init=(var int i = 0) cond=(i < 4) update=(set i = (i + 1))
                    set sum = (sum + input[((id * 4) + i)])
                  set output[id] = sum
                """);

        GpuIrOptimizationProposal proposal = new GpuIrLoopVectorizationMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertTrue(proposal.diagnostics().get(0).contains("backend-target-not-opencl"));
        assertEquals("0", proposal.proofArtifact().fields().get("candidate.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("skipped.backendTarget.count"));
    }

    @Test
    void blocksMaterializationWhenLoopTripCountIsNotVectorWidthFour() {
        IrGpuArtifact original = artifact("""
                body
                  var int id = intrinsic(get_global_id template="" args=[0])
                  var float sum = 0.0F
                  for init=(var int i = 0) cond=(i < 3) update=(set i = (i + 1))
                    set sum = (sum + input[((id * 3) + i)])
                  set output[id] = sum
                """);

        GpuIrOptimizationProposal proposal = new GpuIrLoopVectorizationMaterializationProposalProvider()
                .propose(openClRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertEquals("1", proposal.proofArtifact().fields().get("skipped.unsupportedWidth.count"));
        assertEquals("loop-width-unsupported-3", proposal.proofArtifact().fields().get("firstBlocker"));
    }

    @Test
    void materializationProviderIsServiceLoaded() {
        List<GpuIrOptimizationProposalProvider> providers = ServiceLoader
                .load(GpuIrOptimizationProposalProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(GpuIrLoopVectorizationMaterializationProposalProvider.class::isInstance)
                .toList();

        assertEquals(1, providers.size());
    }

    private static GpuIrOptimizationProposalRequest openClRequest(IrGpuArtifact original) {
        return new GpuIrOptimizationProposalRequest(
                original,
                "diagnostic",
                false,
                Map.of("backendTarget", "OPENCL")
        );
    }

    private static IrGpuArtifact artifact(String body) {
        IrGpuMethodBody methodBody = new IrGpuMethodBody(
                "entry",
                "kernel",
                "kernel",
                "ir-text-v1",
                body,
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
                        new IrGpuEntryParameter("input", "float[]", "GLOBAL", false, List.of())
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
                List.of(new IrGpuTypedNode(0, "GpuIrVariableRef", Map.of("name", "sum"), Map.of()))
        );
    }
}
