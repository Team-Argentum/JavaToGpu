package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBodyIndex;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrSafeLocalCseMaterializationProposalProviderTest {

    @Test
    void materializesRepeatedPureExpressionAsExistingLocalReference() {
        IrGpuArtifact original = artifact(
                "body\n  var int tmp = (a + b)\n  set output[0] = (a + b)\n",
                repeatedExpressionTypedBody()
        );

        GpuIrOptimizationProposal proposal = new GpuIrSafeLocalCseMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "OPENCL")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertSame(original, proposal.originalArtifact());
        assertNotEquals(IrGpuArtifactIdentity.stableIdentity(original), IrGpuArtifactIdentity.stableIdentity(optimized));
        assertEquals("body\n  var int tmp = (a + b)\n  set output[0] = tmp\n",
                optimized.module().methodBodies().get(0).body());
        IrGpuTypedNode rewrittenNode = optimized.module().methodBodies().get(0).typedBody().nodes().get(6);
        assertEquals("GpuIrVariableRef", rewrittenNode.kind());
        assertEquals("tmp", rewrittenNode.attributes().get("name"));
        assertTrue(rewrittenNode.children().isEmpty());
        assertTrue(optimized.regenerationMetadata().backendNeutralSourceReady());

        Map<String, String> fields = proposal.proofArtifact().fields();
        assertEquals("safe-local-cse-materialization", fields.get("optimizerFamily"));
        assertEquals("1", fields.get("localBinding.count"));
        assertEquals("1", fields.get("candidate.count"));
        assertEquals("1", fields.get("transformedNode.count"));
        assertEquals("1", fields.get("bodyTextReplacement.count"));
        assertEquals("1", fields.get("fixedPoint.pass.count"));
        assertEquals("true", fields.get("rewrite.materialized"));
        assertEquals("false", fields.get("provider.mutatesOriginal"));
        assertEquals("true", fields.get("runtimeEquivalencePayload.present"));
        assertEquals("true", fields.get("runtimeEquivalencePayload.passed"));
        assertEquals("static-existing-local-expression-reuse", fields.get("runtimeEquivalencePayload.ReferenceMode"));
        assertEquals("1", fields.get("runtimeEquivalencePayload.Case.Count"));
        assertEquals("kernel#6=(a + b)->tmp", fields.get("runtimeEquivalencePayload.Case.0.Name"));
        assertEquals("safe-local-cse-reuse-existing-local", fields.get("runtimeEquivalencePayload.Case.0.RewriteKind"));
        assertEquals("(a + b)", fields.get("runtimeEquivalencePayload.Case.0.Output.0.PreOptimization"));
        assertEquals("tmp", fields.get("runtimeEquivalencePayload.Case.0.Output.0.PostOptimization"));
        assertEquals("0", fields.get("introducedTemporary.count"));
        assertEquals("true", fields.get("safety.dominanceProven"));
        assertEquals("true", fields.get("safety.sideEffectFreedomProven"));
        assertEquals("false", fields.get("safety.newTemporaryIntroduced"));
        assertEquals("true", fields.get("openClReview.sourceReady"));
        assertEquals("kernel#6=(a + b)->tmp", fields.get("firstTransformedNode"));
        assertEquals("(a + b)", fields.get("firstExpression"));
        assertEquals("tmp", fields.get("firstReplacement"));
    }

    @Test
    void materializesRepeatedPureExpressionsToFixedPoint() {
        IrGpuArtifact original = artifact(
                "body\n  var int tmp = (a + b)\n  set output[0] = (a + b)\n  set output[1] = (a + b)\n",
                typedBody(
                        List.of(0, 4, 9),
                        new IrGpuTypedNode(0, "GpuIrVariableDeclaration", Map.of(
                                "typeName", "int",
                                "name", "tmp"
                        ), Map.of("initializer", List.of(1))),
                        binary(1, "+", 2, 3),
                        variable(2, "a"),
                        variable(3, "b"),
                        assignment(4, 5, 6),
                        variable(5, "output[0]"),
                        binary(6, "+", 7, 8),
                        variable(7, "a"),
                        variable(8, "b"),
                        assignment(9, 10, 11),
                        variable(10, "output[1]"),
                        binary(11, "+", 12, 13),
                        variable(12, "a"),
                        variable(13, "b")
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrSafeLocalCseMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "OPENCL")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertEquals(
                "body\n  var int tmp = (a + b)\n  set output[0] = tmp\n  set output[1] = tmp\n",
                optimized.module().methodBodies().get(0).body()
        );
        Map<String, String> fields = proposal.proofArtifact().fields();
        assertEquals("2", fields.get("candidate.count"));
        assertEquals("2", fields.get("transformedNode.count"));
        assertEquals("2", fields.get("bodyTextReplacement.count"));
        assertEquals("2", fields.get("fixedPoint.pass.count"));
        assertEquals("2", fields.get("runtimeEquivalencePayload.Case.Count"));
        assertEquals("safe-local-cse-reuse-existing-local", fields.get("runtimeEquivalencePayload.Case.1.RewriteKind"));
    }

    @Test
    void introducesLocalTemporaryForRepeatedFloatExpressionWithoutExistingBinding() {
        IrGpuArtifact original = floatArtifact(
                "body\n"
                        + "  var float coordX = ((value * 1.414F) + 12.0F)\n"
                        + "  var float coordY = ((value * 1.414F) - 7.5F)\n"
                        + "  var float coordZ = ((value * 1.414F) * staticMath)\n",
                typedBody(
                        List.of(0, 6, 12),
                        declaration(0, "float", "coordX", 1),
                        binary(1, "+", 2, 5),
                        binary(2, "*", 3, 4),
                        variable(3, "value"),
                        literal(4, "1.414F"),
                        literal(5, "12.0F"),
                        declaration(6, "float", "coordY", 7),
                        binary(7, "-", 8, 11),
                        binary(8, "*", 9, 10),
                        variable(9, "value"),
                        literal(10, "1.414F"),
                        literal(11, "7.5F"),
                        declaration(12, "float", "coordZ", 13),
                        binary(13, "*", 14, 17),
                        binary(14, "*", 15, 16),
                        variable(15, "value"),
                        literal(16, "1.414F"),
                        variable(17, "staticMath")
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrSafeLocalCseMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "OPENCL")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertEquals(
                "body\n"
                        + "  var float jtg_cse0 = (value * 1.414F)\n"
                        + "  var float coordX = (jtg_cse0 + 12.0F)\n"
                        + "  var float coordY = (jtg_cse0 - 7.5F)\n"
                        + "  var float coordZ = (jtg_cse0 * staticMath)\n",
                optimized.module().methodBodies().get(0).body()
        );
        IrGpuTypedBody optimizedTypedBody = optimized.module().methodBodies().get(0).typedBody();
        assertEquals(List.of(18, 0, 6, 12), optimizedTypedBody.rootNodeIds());
        IrGpuTypedNode temporaryDeclaration = optimizedTypedBody.nodes().stream()
                .filter(node -> node.id() == 18)
                .findFirst()
                .orElseThrow();
        assertEquals("GpuIrVariableDeclaration", temporaryDeclaration.kind());
        assertEquals("float", temporaryDeclaration.attributes().get("typeName"));
        assertEquals("jtg_cse0", temporaryDeclaration.attributes().get("name"));
        assertTrue(optimizedTypedBody.nodes().stream().filter(node -> List.of(2, 8, 14).contains(node.id())).allMatch(node ->
                "GpuIrVariableRef".equals(node.kind()) && "jtg_cse0".equals(node.attributes().get("name"))
        ));

        Map<String, String> fields = proposal.proofArtifact().fields();
        assertEquals("3", fields.get("localBinding.count"));
        assertEquals("1", fields.get("introducedTemporary.count"));
        assertEquals("3", fields.get("candidate.count"));
        assertEquals("3", fields.get("transformedNode.count"));
        assertEquals("3", fields.get("bodyTextReplacement.count"));
        assertEquals("1", fields.get("fixedPoint.pass.count"));
        assertEquals("true", fields.get("safety.newTemporaryIntroduced"));
        assertEquals("static-local-expression-reuse-with-introduced-temporary", fields.get("runtimeEquivalencePayload.ReferenceMode"));
        assertEquals("3", fields.get("runtimeEquivalencePayload.Case.Count"));
        assertEquals("safe-local-cse-introduce-local", fields.get("runtimeEquivalencePayload.Case.0.RewriteKind"));
        assertEquals("introducedLocal", fields.get("runtimeEquivalencePayload.Case.0.Input.1.Name"));
        assertEquals("(value * 1.414F)", fields.get("firstExpression"));
        assertEquals("jtg_cse0", fields.get("firstReplacement"));
        assertTrue(optimized.regenerationMetadata().backendNeutralSourceReady());
    }

    @Test
    void doesNotMaterializeWithoutExistingLocalBinding() {
        IrGpuArtifact original = artifact(
                "body\n  set output[0] = (a + b)\n",
                typedBody(
                        List.of(0),
                        assignment(0, 1, 2),
                        variable(1, "output[0]"),
                        binary(2, "+", 3, 4),
                        variable(3, "a"),
                        variable(4, "b")
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrSafeLocalCseMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertTrue(proposal.diagnostics().get(0).contains("no-existing-local-expression-binding"));
        assertEquals("0", proposal.proofArtifact().fields().get("localBinding.count"));
        assertEquals("false", proposal.proofArtifact().fields().get("rewrite.materialized"));
    }

    @Test
    void blocksControlFlowBoundariesUntilDominanceProofIsWider() {
        IrGpuArtifact original = artifact(
                "body\n  if (flag)\n    return a\n",
                typedBody(
                        List.of(0),
                        new IrGpuTypedNode(0, "GpuIrIf", Map.of(), Map.of("condition", List.of(1))),
                        variable(1, "flag")
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrSafeLocalCseMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertTrue(proposal.diagnostics().get(0).contains("control-flow-boundary-present"));
        assertEquals("1", proposal.proofArtifact().fields().get("skipped.controlFlowBoundary.count"));
    }

    @Test
    void skipsWhenRepeatedExpressionIsMissingFromTextBody() {
        IrGpuArtifact original = artifact(
                "body\n  var int tmp = (a + b)\n  set output[0] = tmp\n",
                repeatedExpressionTypedBody()
        );

        GpuIrOptimizationProposal proposal = new GpuIrSafeLocalCseMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertTrue(proposal.diagnostics().get(0).contains("body-text-pattern-missing"));
        assertEquals("1", proposal.proofArtifact().fields().get("localBinding.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("candidate.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("skipped.bodyTextPatternMissing.count"));
    }

    @Test
    void materializationProviderIsServiceLoaded() {
        List<GpuIrOptimizationProposalProvider> providers = ServiceLoader
                .load(GpuIrOptimizationProposalProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(GpuIrSafeLocalCseMaterializationProposalProvider.class::isInstance)
                .toList();

        assertEquals(1, providers.size());
    }

    private static IrGpuTypedBody repeatedExpressionTypedBody() {
        return typedBody(
                List.of(0, 4),
                declaration(0, "int", "tmp", 1),
                binary(1, "+", 2, 3),
                variable(2, "a"),
                variable(3, "b"),
                assignment(4, 5, 6),
                variable(5, "output[0]"),
                binary(6, "+", 7, 8),
                variable(7, "a"),
                variable(8, "b")
        );
    }

    private static IrGpuTypedNode declaration(int id, String typeName, String name, int initializerId) {
        return new IrGpuTypedNode(id, "GpuIrVariableDeclaration", Map.of(
                "typeName", typeName,
                "name", name
        ), Map.of("initializer", List.of(initializerId)));
    }

    private static IrGpuTypedNode assignment(int id, int targetId, int valueId) {
        return new IrGpuTypedNode(id, "GpuIrAssignment", Map.of(), Map.of(
                "target", List.of(targetId),
                "value", List.of(valueId)
        ));
    }

    private static IrGpuTypedNode binary(int id, String operator, int leftId, int rightId) {
        return new IrGpuTypedNode(id, "GpuIrBinary", Map.of("operator", operator), Map.of(
                "left", List.of(leftId),
                "right", List.of(rightId)
        ));
    }

    private static IrGpuTypedNode variable(int id, String name) {
        return new IrGpuTypedNode(id, "GpuIrVariableRef", Map.of("name", name), Map.of());
    }

    private static IrGpuTypedNode literal(int id, String sourceText) {
        return new IrGpuTypedNode(id, "GpuIrLiteral", Map.of("sourceText", sourceText), Map.of());
    }

    private static IrGpuTypedBody typedBody(List<Integer> rootNodeIds, IrGpuTypedNode... nodes) {
        return new IrGpuTypedBody(IrGpuTypedBody.FORMAT, rootNodeIds, List.of(nodes));
    }

    private static IrGpuArtifact artifact(String body, IrGpuTypedBody typedBody) {
        return artifact(body, typedBody, List.of(
                new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of()),
                new IrGpuEntryParameter("a", "int", "PRIVATE", false, List.of()),
                new IrGpuEntryParameter("b", "int", "PRIVATE", false, List.of())
        ));
    }

    private static IrGpuArtifact floatArtifact(String body, IrGpuTypedBody typedBody) {
        return artifact(body, typedBody, List.of(
                new IrGpuEntryParameter("value", "float", "PRIVATE", false, List.of()),
                new IrGpuEntryParameter("staticMath", "float", "PRIVATE", false, List.of())
        ));
    }

    private static IrGpuArtifact artifact(String body, IrGpuTypedBody typedBody, List<IrGpuEntryParameter> parameters) {
        IrGpuMethodBody methodBody = new IrGpuMethodBody(
                "entry",
                "kernel",
                "kernel",
                "ir-text-v1",
                body,
                typedBody,
                IrGpuBodyIndex.empty(),
                List.of(),
                IrGpuSourceLocation.unknown("kernel")
        );
        return new IrGpuArtifact(
            IrGpuArtifactHeader.javaSourceV1(),
            new IrGpuModule("kernel", "kernel", List.of(), List.of(), List.of(methodBody)),
                parameters,
                List.of(IrGpuBackendOutput.openClSource("javatogpu/test/Kernel/kernel.cl")),
                "opencl",
                "off"
        );
    }
}
