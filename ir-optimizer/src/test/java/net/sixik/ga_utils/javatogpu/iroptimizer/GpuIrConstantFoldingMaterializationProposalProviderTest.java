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

class GpuIrConstantFoldingMaterializationProposalProviderTest {

    @Test
    void materializesIntegerLiteralBinaryFoldIntoTypedBodyAndTextBody() {
        IrGpuArtifact original = artifact(
                "body\n  set output[0] = (2 + 3)\n",
                typedBody(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(2),
                                "right", List.of(3)
                        )),
                        new IrGpuTypedNode(2, "GpuIrLiteral", Map.of("sourceText", "2"), Map.of()),
                        new IrGpuTypedNode(3, "GpuIrLiteral", Map.of("sourceText", "3"), Map.of())
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
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
        assertEquals("body\n  set output[0] = 5\n", optimized.module().methodBodies().get(0).body());
        IrGpuTypedNode rewrittenNode = optimized.module().methodBodies().get(0).typedBody().nodes().get(1);
        assertEquals("GpuIrLiteral", rewrittenNode.kind());
        assertEquals("5", rewrittenNode.attributes().get("sourceText"));
        assertTrue(rewrittenNode.children().isEmpty());
        assertTrue(optimized.regenerationMetadata().backendNeutralSourceReady());
        assertEquals("1", proposal.proofArtifact().fields().get("candidate.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("transformedNode.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("literalRewrite.count"));
        assertEquals("0", proposal.proofArtifact().fields().get("identityRewrite.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("bodyTextReplacement.count"));
        assertEquals("true", proposal.proofArtifact().fields().get("rewrite.materialized"));
        assertEquals("constant-folding-materialization", proposal.proofArtifact().fields().get("optimizerFamily"));
        assertEquals("false", proposal.proofArtifact().fields().get("provider.mutatesOriginal"));
        assertEquals("true", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.required"));
        assertEquals("recorded", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.status"));
        assertEquals("true", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.present"));
        assertEquals("true", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.passed"));
        assertEquals("none", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.firstBlocker"));
        assertEquals("true", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.cpuReference.present"));
        assertEquals("true", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.preOptimizationOutput.present"));
        assertEquals("true", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.postOptimizationOutput.present"));
        assertEquals("true", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.tolerance.present"));
        assertEquals("true", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.failureFixture.present"));
        assertEquals("static-exact-integer-fold", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.ReferenceMode"));
        assertEquals("1", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.Count"));
        assertEquals("kernel#1=2+3->5", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Name"));
        assertEquals("2", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Input.0.Value"));
        assertEquals("3", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Input.1.Value"));
        assertEquals("5", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Output.0.CpuReference"));
        assertEquals("(2 + 3)", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Output.0.PreOptimization"));
        assertEquals("5", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Output.0.PostOptimization"));
        assertEquals("true", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Output.0.Equivalent"));
        assertEquals("true", proposal.proofArtifact().fields().get("openClReview.sourceReady"));
        assertEquals("kernel#1=2+3->5", proposal.proofArtifact().fields().get("firstTransformedNode"));
        assertEquals("(2 + 3)", proposal.proofArtifact().fields().get("firstFoldedExpression"));
        assertEquals("5", proposal.proofArtifact().fields().get("firstFoldedValue"));
    }

    @Test
    void materializesUnaryMinusIntegerLiteralIntoTypedBodyAndTextBody() {
        IrGpuArtifact original = artifact(
                "body\n  set output[0] = (-7)\n",
                typedBody(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrUnary", Map.of("operator", "-"), Map.of("operand", List.of(2))),
                        new IrGpuTypedNode(2, "GpuIrLiteral", Map.of("sourceText", "7"), Map.of())
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "OPENCL")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertEquals("body\n  set output[0] = -7\n", optimized.module().methodBodies().get(0).body());
        IrGpuTypedNode rewrittenNode = optimized.module().methodBodies().get(0).typedBody().nodes().get(1);
        assertEquals("GpuIrLiteral", rewrittenNode.kind());
        assertEquals("-7", rewrittenNode.attributes().get("sourceText"));
        assertTrue(rewrittenNode.children().isEmpty());
        assertEquals("1", proposal.proofArtifact().fields().get("candidate.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("transformedNode.count"));
        assertEquals("kernel#1=-7->-7", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Name"));
        assertEquals("1", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Input.Count"));
        assertEquals("operand", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Input.0.Name"));
        assertEquals("7", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Input.0.Value"));
        assertEquals("(-7)", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Output.0.PreOptimization"));
        assertEquals("-7", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Output.0.PostOptimization"));
    }

    @Test
    void materializesNestedUnaryAndBinaryIntegerLiteralFoldsToFixedPoint() {
        IrGpuArtifact original = artifact(
                "body\n  set output[0] = ((-2) * 4)\n",
                typedBody(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                                "left", List.of(2),
                                "right", List.of(4)
                        )),
                        new IrGpuTypedNode(2, "GpuIrUnary", Map.of("operator", "-"), Map.of("operand", List.of(3))),
                        new IrGpuTypedNode(3, "GpuIrLiteral", Map.of("sourceText", "2"), Map.of()),
                        new IrGpuTypedNode(4, "GpuIrLiteral", Map.of("sourceText", "4"), Map.of())
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "OPENCL")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertEquals("body\n  set output[0] = -8\n", optimized.module().methodBodies().get(0).body());
        IrGpuTypedNode rewrittenRoot = optimized.module().methodBodies().get(0).typedBody().nodes().get(1);
        IrGpuTypedNode rewrittenUnary = optimized.module().methodBodies().get(0).typedBody().nodes().get(2);
        assertEquals("GpuIrLiteral", rewrittenRoot.kind());
        assertEquals("-8", rewrittenRoot.attributes().get("sourceText"));
        assertEquals("GpuIrLiteral", rewrittenUnary.kind());
        assertEquals("-2", rewrittenUnary.attributes().get("sourceText"));
        assertEquals("2", proposal.proofArtifact().fields().get("candidate.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("transformedNode.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("fixedPoint.pass.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.Count"));
        assertEquals("kernel#2=-2->-2", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Name"));
        assertEquals("kernel#1=-2*4->-8", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.1.Name"));
        assertEquals("(-2 * 4)", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.1.Output.0.PreOptimization"));
        assertEquals("-8", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.1.Output.0.PostOptimization"));
    }

    @Test
    void materializesNestedIntegerLiteralFoldsToFixedPoint() {
        IrGpuArtifact original = artifact(
                "body\n  set output[0] = ((2 + 3) * 4)\n",
                typedBody(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                                "left", List.of(2),
                                "right", List.of(4)
                        )),
                        new IrGpuTypedNode(2, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(3),
                                "right", List.of(5)
                        )),
                        new IrGpuTypedNode(3, "GpuIrLiteral", Map.of("sourceText", "2"), Map.of()),
                        new IrGpuTypedNode(4, "GpuIrLiteral", Map.of("sourceText", "4"), Map.of()),
                        new IrGpuTypedNode(5, "GpuIrLiteral", Map.of("sourceText", "3"), Map.of())
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "OPENCL")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertEquals("body\n  set output[0] = 20\n", optimized.module().methodBodies().get(0).body());
        IrGpuTypedNode rewrittenRoot = optimized.module().methodBodies().get(0).typedBody().nodes().get(1);
        IrGpuTypedNode rewrittenNested = optimized.module().methodBodies().get(0).typedBody().nodes().get(2);
        assertEquals("GpuIrLiteral", rewrittenRoot.kind());
        assertEquals("20", rewrittenRoot.attributes().get("sourceText"));
        assertEquals("GpuIrLiteral", rewrittenNested.kind());
        assertEquals("5", rewrittenNested.attributes().get("sourceText"));
        assertEquals("2", proposal.proofArtifact().fields().get("candidate.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("transformedNode.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("bodyTextReplacement.count"));
        assertEquals("true", proposal.proofArtifact().fields().get("fixedPoint.enabled"));
        assertEquals("2", proposal.proofArtifact().fields().get("fixedPoint.pass.count"));
        assertEquals("0", proposal.proofArtifact().fields().get("skipped.nonLiteralOperand.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.Count"));
        assertEquals("kernel#2=2+3->5", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Name"));
        assertEquals("kernel#1=5*4->20", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.1.Name"));
        assertEquals("(5 * 4)", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.1.Output.0.PreOptimization"));
        assertEquals("20", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.1.Output.0.PostOptimization"));
    }

    @Test
    void materializesExactIntegerLiteralDivisionIntoTypedBodyAndTextBody() {
        IrGpuArtifact original = binaryArtifact("body\n  set output[0] = (8 / 2)\n", "/", "8", "2");

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "OPENCL")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertEquals("body\n  set output[0] = 4\n", optimized.module().methodBodies().get(0).body());
        IrGpuTypedNode rewrittenNode = optimized.module().methodBodies().get(0).typedBody().nodes().get(1);
        assertEquals("GpuIrLiteral", rewrittenNode.kind());
        assertEquals("4", rewrittenNode.attributes().get("sourceText"));
        assertTrue(rewrittenNode.children().isEmpty());
        assertTrue(optimized.regenerationMetadata().backendNeutralSourceReady());
        assertEquals("1", proposal.proofArtifact().fields().get("candidate.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("transformedNode.count"));
        assertEquals("0", proposal.proofArtifact().fields().get("skipped.divideByZero.count"));
        assertEquals("0", proposal.proofArtifact().fields().get("skipped.nonEvenDivision.count"));
        assertEquals("kernel#1=8/2->4", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Name"));
        assertEquals("(8 / 2)", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Output.0.PreOptimization"));
        assertEquals("4", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Output.0.PostOptimization"));
        assertEquals("plain-32bit-integer-literals-and-pure-symbolic-identities-unary-minus-plus-minus-multiply-exact-divide",
                proposal.proofArtifact().fields().get("safety.scope"));
    }

    @Test
    void materializesAdditionIdentityWithRightZero() {
        assertIdentityMaterializes("+", variable(2, "x"), literal(3, "0"), "(x + 0)", "x+0", "x");
    }

    @Test
    void materializesAdditionIdentityWithLeftZero() {
        assertIdentityMaterializes("+", literal(2, "0"), variable(3, "x"), "(0 + x)", "0+x", "x");
    }

    @Test
    void materializesSubtractionIdentityWithRightZero() {
        assertIdentityMaterializes("-", variable(2, "x"), literal(3, "0"), "(x - 0)", "x-0", "x");
    }

    @Test
    void materializesMultiplicationIdentityWithRightOne() {
        assertIdentityMaterializes("*", variable(2, "x"), literal(3, "1"), "(x * 1)", "x*1", "x");
    }

    @Test
    void materializesMultiplicationIdentityWithLeftOne() {
        assertIdentityMaterializes("*", literal(2, "1"), variable(3, "x"), "(1 * x)", "1*x", "x");
    }

    @Test
    void materializesDivisionIdentityWithRightOne() {
        assertIdentityMaterializes("/", variable(2, "x"), literal(3, "1"), "(x / 1)", "x/1", "x");
    }

    @Test
    void materializesAdditionIdentityWithPureBinaryRetainedExpression() {
        IrGpuArtifact original = valueArtifact(
                "body\n  set output[0] = ((a + b) + 0)\n",
                typedBody(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(2),
                                "right", List.of(5)
                        )),
                        new IrGpuTypedNode(2, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(3),
                                "right", List.of(4)
                        )),
                        variable(3, "a"),
                        variable(4, "b"),
                        literal(5, "0")
                ),
                "a",
                "b"
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "OPENCL")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertEquals("body\n  set output[0] = (a + b)\n", optimized.module().methodBodies().get(0).body());
        IrGpuTypedNode rewrittenNode = optimized.module().methodBodies().get(0).typedBody().nodes().get(1);
        assertEquals("GpuIrBinary", rewrittenNode.kind());
        assertEquals("+", rewrittenNode.attributes().get("operator"));
        assertEquals(List.of(3), rewrittenNode.children().get("left"));
        assertEquals(List.of(4), rewrittenNode.children().get("right"));
        assertEquals("0", proposal.proofArtifact().fields().get("literalRewrite.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("identityRewrite.count"));
        assertEquals("kernel#1=(a + b)+0->(a + b)",
                proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Name"));
        assertEquals("((a + b) + 0)",
                proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Output.0.PreOptimization"));
        assertEquals("(a + b)",
                proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Output.0.PostOptimization"));
        assertEquals("GpuIrPureExpression", proposal.proofArtifact().fields().get("safety.identityOperandKind"));
        assertEquals("true", proposal.proofArtifact().fields().get("openClReview.sourceReady"));
    }

    @Test
    void materializesDivisionIdentityWithPureBinaryRetainedExpression() {
        IrGpuArtifact original = valueArtifact(
                "body\n  set output[0] = ((a + b) / 1)\n",
                typedBody(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "/"), Map.of(
                                "left", List.of(2),
                                "right", List.of(5)
                        )),
                        new IrGpuTypedNode(2, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(3),
                                "right", List.of(4)
                        )),
                        variable(3, "a"),
                        variable(4, "b"),
                        literal(5, "1")
                ),
                "a",
                "b"
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "OPENCL")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertEquals("body\n  set output[0] = (a + b)\n", optimized.module().methodBodies().get(0).body());
        IrGpuTypedNode rewrittenNode = optimized.module().methodBodies().get(0).typedBody().nodes().get(1);
        assertEquals("GpuIrBinary", rewrittenNode.kind());
        assertEquals("+", rewrittenNode.attributes().get("operator"));
        assertEquals(List.of(3), rewrittenNode.children().get("left"));
        assertEquals(List.of(4), rewrittenNode.children().get("right"));
        assertEquals("0", proposal.proofArtifact().fields().get("literalRewrite.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("identityRewrite.count"));
        assertEquals("kernel#1=(a + b)/1->(a + b)",
                proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Name"));
        assertEquals("((a + b) / 1)",
                proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Output.0.PreOptimization"));
        assertEquals("(a + b)",
                proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Output.0.PostOptimization"));
        assertEquals("GpuIrPureExpression", proposal.proofArtifact().fields().get("safety.identityOperandKind"));
        assertEquals("true", proposal.proofArtifact().fields().get("openClReview.sourceReady"));
    }

    @Test
    void materializesMixedIdentityChainToFixedPoint() {
        IrGpuArtifact original = valueArtifact(
                "body\n  set output[0] = ((x / 1) + 0)\n",
                typedBody(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(2),
                                "right", List.of(5)
                        )),
                        new IrGpuTypedNode(2, "GpuIrBinary", Map.of("operator", "/"), Map.of(
                                "left", List.of(3),
                                "right", List.of(4)
                        )),
                        variable(3, "x"),
                        literal(4, "1"),
                        literal(5, "0")
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "OPENCL")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertEquals("body\n  set output[0] = x\n", optimized.module().methodBodies().get(0).body());
        IrGpuTypedNode rewrittenRoot = optimized.module().methodBodies().get(0).typedBody().nodes().get(1);
        assertEquals("GpuIrVariableRef", rewrittenRoot.kind());
        assertEquals("x", rewrittenRoot.attributes().get("name"));
        assertTrue(rewrittenRoot.children().isEmpty());
        assertEquals("2", proposal.proofArtifact().fields().get("candidate.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("identityRewrite.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("fixedPoint.pass.count"));
        assertEquals("0", proposal.proofArtifact().fields().get("skipped.bodyTextPatternMissing.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.Count"));
        assertEquals("kernel#1=(x / 1)+0->(x / 1)",
                proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Name"));
        assertEquals("kernel#1=x/1->x",
                proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.1.Name"));
        assertEquals("x", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.1.Output.0.PostOptimization"));
        assertEquals("true", proposal.proofArtifact().fields().get("openClReview.sourceReady"));
    }

    @Test
    void materializesRepeatedIdentityExpressionsAcrossMultipleRootsWithoutCrossingTextReplacements() {
        IrGpuArtifact original = valueArtifact(
                "body\n  set output[0] = (x + 0)\n  set output[1] = (x + 0)\n",
                typedBody(
                        List.of(0, 4),
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(2),
                                "right", List.of(3)
                        )),
                        variable(2, "x"),
                        literal(3, "0"),
                        new IrGpuTypedNode(4, "GpuIrAssignment", Map.of(), Map.of("value", List.of(5))),
                        new IrGpuTypedNode(5, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(6),
                                "right", List.of(7)
                        )),
                        variable(6, "x"),
                        literal(7, "0")
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "OPENCL")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertEquals("body\n  set output[0] = x\n  set output[1] = x\n",
                optimized.module().methodBodies().get(0).body());
        IrGpuTypedNode firstRewrittenValue = optimized.module().methodBodies().get(0).typedBody().nodes().get(1);
        IrGpuTypedNode secondRewrittenValue = optimized.module().methodBodies().get(0).typedBody().nodes().get(5);
        assertEquals("GpuIrVariableRef", firstRewrittenValue.kind());
        assertEquals("x", firstRewrittenValue.attributes().get("name"));
        assertEquals("GpuIrVariableRef", secondRewrittenValue.kind());
        assertEquals("x", secondRewrittenValue.attributes().get("name"));
        assertEquals("2", proposal.proofArtifact().fields().get("candidate.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("transformedNode.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("identityRewrite.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("bodyTextReplacement.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("fixedPoint.pass.count"));
        assertEquals("true", proposal.proofArtifact().fields().get("fixedPoint.reachableNodeScan"));
        assertEquals("single-reachable-candidate-per-pass",
                proposal.proofArtifact().fields().get("fixedPoint.rewriteGranularity"));
        assertEquals("reachable-typed-nodes", proposal.proofArtifact().fields().get("bodyTextReplacement.scope"));
        assertEquals("0", proposal.proofArtifact().fields().get("skipped.bodyTextPatternMissing.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.Count"));
        assertEquals("kernel#1=x+0->x", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Name"));
        assertEquals("kernel#5=x+0->x", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.1.Name"));
        assertEquals("true", proposal.proofArtifact().fields().get("openClReview.sourceReady"));
    }

    @Test
    void materializesMultipleMethodBodiesAndKeepsRuntimeEquivalenceCasesScoped() {
        IrGpuArtifact original = artifact(
                List.of(
                        methodBody(
                                "entry",
                                "kernel",
                                "kernel",
                                "body\n  set output[0] = (2 + 3)\n",
                                typedBody(
                                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                                "left", List.of(2),
                                                "right", List.of(3)
                                        )),
                                        literal(2, "2"),
                                        literal(3, "3")
                                )
                        ),
                        methodBody(
                                "helper",
                                "helperScale",
                                "helperScale",
                                "body\n  set output[0] = (x * 1)\n",
                                typedBody(
                                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                                                "left", List.of(2),
                                                "right", List.of(3)
                                        )),
                                        variable(2, "x"),
                                        literal(3, "1")
                                )
                        )
                ),
                List.of(
                        new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of()),
                        new IrGpuEntryParameter("x", "int", "PRIVATE", false, List.of())
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertEquals("body\n  set output[0] = 5\n", optimized.module().methodBodies().get(0).body());
        assertEquals("body\n  set output[0] = x\n", optimized.module().methodBodies().get(1).body());
        assertEquals("GpuIrLiteral", optimized.module().methodBodies().get(0).typedBody().nodes().get(1).kind());
        assertEquals("5", optimized.module().methodBodies().get(0).typedBody().nodes().get(1).attributes().get("sourceText"));
        assertEquals("GpuIrVariableRef", optimized.module().methodBodies().get(1).typedBody().nodes().get(1).kind());
        assertEquals("x", optimized.module().methodBodies().get(1).typedBody().nodes().get(1).attributes().get("name"));
        assertEquals("2", proposal.proofArtifact().fields().get("methodBody.count"));
        assertEquals("all-method-bodies", proposal.proofArtifact().fields().get("methodBody.rewriteScope"));
        assertEquals("2", proposal.proofArtifact().fields().get("typedBody.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("changedMethodBody.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("candidate.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("literalRewrite.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("identityRewrite.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.Count"));
        assertEquals("method-name-and-node-id", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.CaseIdentity"));
        assertEquals("kernel", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.MethodName"));
        assertEquals("1", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.NodeId"));
        assertEquals("literal", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.RewriteKind"));
        assertEquals("kernel#1=2+3->5", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Name"));
        assertEquals("helperScale", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.1.MethodName"));
        assertEquals("1", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.1.NodeId"));
        assertEquals("identity", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.1.RewriteKind"));
        assertEquals("helperScale#1=x*1->x", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.1.Name"));
        assertEquals("false", proposal.proofArtifact().fields().get("openClReview.sourceReady"));
        assertEquals("not-requested", proposal.proofArtifact().fields().get("openClReview.sourceLength"));
    }

    @Test
    void materializesSubtractionIdentityWithPureUnaryRetainedExpression() {
        IrGpuArtifact original = valueArtifact(
                "body\n  set output[0] = ((-a) - 0)\n",
                typedBody(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "-"), Map.of(
                                "left", List.of(2),
                                "right", List.of(4)
                        )),
                        new IrGpuTypedNode(2, "GpuIrUnary", Map.of("operator", "-"), Map.of("operand", List.of(3))),
                        variable(3, "a"),
                        literal(4, "0")
                ),
                "a"
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "OPENCL")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertEquals("body\n  set output[0] = (-a)\n", optimized.module().methodBodies().get(0).body());
        IrGpuTypedNode rewrittenNode = optimized.module().methodBodies().get(0).typedBody().nodes().get(1);
        assertEquals("GpuIrUnary", rewrittenNode.kind());
        assertEquals("-", rewrittenNode.attributes().get("operator"));
        assertEquals(List.of(3), rewrittenNode.children().get("operand"));
        assertEquals("kernel#1=(-a)-0->(-a)",
                proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Name"));
    }

    @Test
    void doesNotMaterializeIdentityWhenRetainedExpressionIsImpure() {
        IrGpuArtifact original = valueArtifact(
                "body\n  set output[0] = (helper(sideEffect args=[]) + 0)\n",
                typedBody(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(2),
                                "right", List.of(3)
                        )),
                        new IrGpuTypedNode(2, "GpuIrHelperCall", Map.of("helper", "sideEffect"), Map.of()),
                        literal(3, "0")
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertTrue(proposal.diagnostics().get(0).contains("non-literal-operand"));
    }

    @Test
    void doesNotMaterializeIdentityWhenRetainedLiteralIsNotPlainInt32() {
        IrGpuArtifact original = valueArtifact(
                "body\n  set output[0] = (0 + 1.5)\n",
                typedBody(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(2),
                                "right", List.of(3)
                        )),
                        literal(2, "0"),
                        literal(3, "1.5")
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertTrue(proposal.diagnostics().get(0).contains("non-literal-operand"));
    }

    @Test
    void doesNotMaterializeDivisionIdentityWithLeftOne() {
        IrGpuArtifact original = valueArtifact(
                "body\n  set output[0] = (1 / x)\n",
                typedBody(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "/"), Map.of(
                                "left", List.of(2),
                                "right", List.of(3)
                        )),
                        literal(2, "1"),
                        variable(3, "x")
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertTrue(proposal.diagnostics().get(0).contains("non-literal-operand"));
    }

    @Test
    void doesNotMaterializeMultiplicationByZeroWithoutSideEffectProof() {
        IrGpuArtifact original = valueArtifact(
                "body\n  set output[0] = (x * 0)\n",
                typedBody(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                                "left", List.of(2),
                                "right", List.of(3)
                        )),
                        variable(2, "x"),
                        literal(3, "0")
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertTrue(proposal.diagnostics().get(0).contains("non-literal-operand"));
    }

    @Test
    void skipsIntegerLiteralDivisionByZero() {
        IrGpuArtifact original = binaryArtifact("body\n  set output[0] = (8 / 0)\n", "/", "8", "0");

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertTrue(proposal.diagnostics().get(0).contains("division-by-zero"));
    }

    @Test
    void skipsIntegerLiteralDivisionWhenResultIsNotEven() {
        IrGpuArtifact original = binaryArtifact("body\n  set output[0] = (5 / 2)\n", "/", "5", "2");

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertTrue(proposal.diagnostics().get(0).contains("non-even-division"));
    }

    @Test
    void skipsIntegerLiteralDivisionWhenFoldedValueOverflowsInt32() {
        IrGpuArtifact original = binaryArtifact(
                "body\n  set output[0] = (-2147483648 / -1)\n",
                "/",
                "-2147483648",
                "-1"
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertTrue(proposal.diagnostics().get(0).contains("integer-overflow-risk"));
    }

    @Test
    void skipsWhenTextBodyCannotBeRewrittenConsistently() {
        IrGpuArtifact original = artifact(
                "body\n  return value\n",
                typedBody(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                                "left", List.of(2),
                                "right", List.of(3)
                        )),
                        new IrGpuTypedNode(2, "GpuIrLiteral", Map.of("sourceText", "4"), Map.of()),
                        new IrGpuTypedNode(3, "GpuIrLiteral", Map.of("sourceText", "5"), Map.of())
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertTrue(proposal.diagnostics().get(0).contains("body-text-pattern-missing"));
    }

    @Test
    void skipsDecimalLiteralsUntilFloatingProofExists() {
        IrGpuArtifact original = artifact(
                "body\n  set output[0] = (1.5 + 2)\n",
                typedBody(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(2),
                                "right", List.of(3)
                        )),
                        new IrGpuTypedNode(2, "GpuIrLiteral", Map.of("sourceText", "1.5"), Map.of()),
                        new IrGpuTypedNode(3, "GpuIrLiteral", Map.of("sourceText", "2"), Map.of())
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertTrue(proposal.diagnostics().get(0).contains("non-integer-literal"));
    }

    @Test
    void validationSandwichCanSelectMaterializedFoldWhenMutationIsExplicitlyAllowed() {
        IrGpuArtifact original = artifact(
                "body\n  set output[0] = (4 * 5)\n",
                typedBody(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                                "left", List.of(2),
                                "right", List.of(3)
                        )),
                        new IrGpuTypedNode(2, "GpuIrLiteral", Map.of("sourceText", "4"), Map.of()),
                        new IrGpuTypedNode(3, "GpuIrLiteral", Map.of("sourceText", "5"), Map.of())
                )
        );

        GpuIrOptimizationSandwichReport report = GpuIrOptimizationSandwichRunner.alwaysValid()
                .run(new GpuIrOptimizationProposalRequest(
                                original,
                                "diagnostic",
                                true,
                                Map.of("backendTarget", "OPENCL")
                        ),
                        new GpuIrConstantFoldingMaterializationProposalProvider());

        assertEquals(GpuIrOptimizationSandwichStatus.OPTIMIZED_SELECTED, report.status());
        assertEquals("body\n  set output[0] = 20\n", report.selectedArtifact().module().methodBodies().get(0).body());
        assertTrue(report.selectedOptimized());
        assertFalse(report.selectedOriginal());
    }

    @Test
    void materializationProviderIsServiceLoaded() {
        List<GpuIrOptimizationProposalProvider> providers = ServiceLoader
                .load(GpuIrOptimizationProposalProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(GpuIrConstantFoldingMaterializationProposalProvider.class::isInstance)
                .toList();

        assertEquals(1, providers.size());
    }

    private static IrGpuTypedBody typedBody(IrGpuTypedNode... nodes) {
        return new IrGpuTypedBody(IrGpuTypedBody.FORMAT, List.of(nodes[0].id()), List.of(nodes));
    }

    private static IrGpuTypedBody typedBody(List<Integer> rootNodeIds, IrGpuTypedNode... nodes) {
        return new IrGpuTypedBody(IrGpuTypedBody.FORMAT, rootNodeIds, List.of(nodes));
    }

    private static IrGpuArtifact binaryArtifact(String body, String operator, String left, String right) {
        return artifact(
                body,
                typedBody(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", operator), Map.of(
                                "left", List.of(2),
                                "right", List.of(3)
                        )),
                        new IrGpuTypedNode(2, "GpuIrLiteral", Map.of("sourceText", left), Map.of()),
                        new IrGpuTypedNode(3, "GpuIrLiteral", Map.of("sourceText", right), Map.of())
                )
        );
    }

    private static void assertIdentityMaterializes(
            String operator,
            IrGpuTypedNode left,
            IrGpuTypedNode right,
            String expression,
            String expressionSummary,
            String foldedValue
    ) {
        IrGpuArtifact original = valueArtifact(
                "body\n  set output[0] = " + expression + "\n",
                typedBody(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", operator), Map.of(
                                "left", List.of(left.id()),
                                "right", List.of(right.id())
                        )),
                        left,
                        right
                )
        );

        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "OPENCL")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertEquals("body\n  set output[0] = " + foldedValue + "\n", optimized.module().methodBodies().get(0).body());
        IrGpuTypedNode rewrittenNode = optimized.module().methodBodies().get(0).typedBody().nodes().get(1);
        assertEquals("GpuIrVariableRef", rewrittenNode.kind());
        assertEquals("x", rewrittenNode.attributes().get("name"));
        assertTrue(rewrittenNode.children().isEmpty());
        assertTrue(optimized.regenerationMetadata().backendNeutralSourceReady());
        assertEquals("1", proposal.proofArtifact().fields().get("candidate.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("transformedNode.count"));
        assertEquals("0", proposal.proofArtifact().fields().get("literalRewrite.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("identityRewrite.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("bodyTextReplacement.count"));
        assertEquals("static-exact-integer-symbolic-identity-fold",
                proposal.proofArtifact().fields().get("runtimeEquivalencePayload.ReferenceMode"));
        assertEquals("kernel#1=" + expressionSummary + "->" + foldedValue,
                proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Name"));
        assertEquals(expression, proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Output.0.PreOptimization"));
        assertEquals(foldedValue, proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.Output.0.PostOptimization"));
        assertEquals("GpuIrPureExpression", proposal.proofArtifact().fields().get("safety.identityOperandKind"));
        assertEquals("GpuIrVariableRef,GpuIrInt32Literal,GpuIrBinary,GpuIrUnary",
                proposal.proofArtifact().fields().get("safety.identityRetainedExpressionKinds"));
        assertEquals("true", proposal.proofArtifact().fields().get("openClReview.sourceReady"));
    }

    private static IrGpuTypedNode literal(int id, String sourceText) {
        return new IrGpuTypedNode(id, "GpuIrLiteral", Map.of("sourceText", sourceText), Map.of());
    }

    private static IrGpuTypedNode variable(int id, String name) {
        return new IrGpuTypedNode(id, "GpuIrVariableRef", Map.of("name", name), Map.of());
    }

    private static IrGpuArtifact valueArtifact(String body, IrGpuTypedBody typedBody) {
        return valueArtifact(body, typedBody, "x");
    }

    private static IrGpuArtifact valueArtifact(String body, IrGpuTypedBody typedBody, String... privateScalarParameters) {
        java.util.ArrayList<IrGpuEntryParameter> entryParameters = new java.util.ArrayList<>();
        entryParameters.add(new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of()));
        for (String parameter : privateScalarParameters) {
            entryParameters.add(new IrGpuEntryParameter(parameter, "int", "PRIVATE", false, List.of()));
        }
        return artifact(
                body,
                typedBody,
                entryParameters
        );
    }

    private static IrGpuArtifact artifact(String body, IrGpuTypedBody typedBody) {
        return artifact(
                body,
                typedBody,
                List.of(new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of()))
        );
    }

    private static IrGpuArtifact artifact(String body, IrGpuTypedBody typedBody, List<IrGpuEntryParameter> entryParameters) {
        IrGpuMethodBody methodBody = methodBody(
                "entry",
                "kernel",
                "kernel",
                body,
                typedBody
        );
        return artifact(List.of(methodBody), entryParameters);
    }

    private static IrGpuArtifact artifact(List<IrGpuMethodBody> methodBodies, List<IrGpuEntryParameter> entryParameters) {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule("kernel", "kernel", List.of(), List.of(), methodBodies),
                entryParameters,
                List.of(IrGpuBackendOutput.openClSource("javatogpu/test/Kernel/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuMethodBody methodBody(
            String role,
            String name,
            String emittedName,
            String body,
            IrGpuTypedBody typedBody
    ) {
        return new IrGpuMethodBody(
                role,
                name,
                emittedName,
                "ir-text-v1",
                body,
                typedBody,
                IrGpuBodyIndex.empty(),
                List.of(),
                IrGpuSourceLocation.unknown(name)
        );
    }
}
