package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrTypedBodyGraphPatchTest {

    @Test
    void mapsReachableNodesAndAllocatesDeterministicNextId() {
        IrGpuTypedBody body = typedBody();

        Map<Integer, IrGpuTypedNode> nodesById = GpuIrTypedBodyGraphPatch.nodesById(body);

        assertEquals("GpuIrAssignment", nodesById.get(0).kind());
        assertEquals("GpuIrLiteral", nodesById.get(99).kind());
        assertEquals(100, GpuIrTypedBodyGraphPatch.nextNodeId(body));
        assertEquals(List.of(0, 1, 2, 3, 99), body.nodes().stream().map(IrGpuTypedNode::id).toList());
        assertEquals(
                List.of(0, 1, 2, 3),
                body.nodes().stream()
                        .map(IrGpuTypedNode::id)
                        .filter(id -> GpuIrTypedBodyGraphPatch.reachableNodeIds(body, nodesById).contains(id))
                        .toList()
        );
    }

    @Test
    void replacesRootNodeAndAppendsAuxiliaryNodeWithoutChangingRoots() {
        IrGpuTypedBody body = typedBody();
        IrGpuTypedNode replacement = GpuIrTypedBodyGraphPatch.intrinsicCall(
                2,
                "clamp",
                "clamp-review",
                List.of(1, 3, 99),
                "clamp-review"
        );
        IrGpuTypedNode auxiliary = new IrGpuTypedNode(100, "GpuIrLiteral", Map.of("sourceText", "1.0F"), Map.of());

        IrGpuTypedBody patched = GpuIrTypedBodyGraphPatch.replaceAndAppend(body, replacement, List.of(auxiliary));

        assertEquals(List.of(0), patched.rootNodeIds());
        assertEquals(List.of(0, 1, 2, 3, 99, 100), patched.nodes().stream().map(IrGpuTypedNode::id).toList());
        IrGpuTypedNode replacedNode = GpuIrTypedBodyGraphPatch.nodesById(patched).get(2);
        assertEquals("GpuIrIntrinsicCall", replacedNode.kind());
        assertEquals("clamp", replacedNode.attributes().get("name"));
        assertEquals("clamp-review", replacedNode.attributes().get("argumentTypes.2"));
        assertEquals(List.of(1, 3, 99), replacedNode.children().get("arguments"));
        assertEquals("1.0F", GpuIrTypedBodyGraphPatch.nodesById(patched).get(100).attributes().get("sourceText"));
    }

    @Test
    void reportsMissingRootsAndChildReferencesDuringReachability() {
        IrGpuTypedBody body = new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0, 404),
                List.of(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1, 405))),
                        new IrGpuTypedNode(1, "GpuIrLiteral", Map.of("sourceText", "1"), Map.of())
                )
        );

        GpuIrTypedBodyGraphPatch.Reachability reachability = GpuIrTypedBodyGraphPatch
                .reachability(body, GpuIrTypedBodyGraphPatch.nodesById(body));

        assertEquals(1, reachability.rootMissingCount());
        assertEquals(1, reachability.missingChildReferenceCount());
        assertTrue(reachability.reachableNodeIds().contains(0));
        assertTrue(reachability.reachableNodeIds().contains(1));
        assertFalse(reachability.reachableNodeIds().contains(404));
    }

    @Test
    void keepsOriginalBodyWhenReplacementRootWasNotPresent() {
        IrGpuTypedBody body = typedBody();
        IrGpuTypedNode replacement = GpuIrTypedBodyGraphPatch.intrinsicCall(
                101,
                "mix",
                "mix-review",
                List.of(1, 2, 3),
                List.of("base-review", "target-review", "amount-review")
        );

        IrGpuTypedBody patched = GpuIrTypedBodyGraphPatch.replaceNode(body, replacement);

        assertEquals(body.nodes(), patched.nodes());
        assertEquals(body.rootNodeIds(), patched.rootNodeIds());
        assertTrue(GpuIrTypedBodyGraphPatch.reachableNodeIds(patched, GpuIrTypedBodyGraphPatch.nodesById(patched)).contains(2));
    }

    @Test
    void appliesTextAndTypedBodyPatchAsSinglePlan() {
        IrGpuTypedBody body = typedBody();
        IrGpuTypedNode replacement = GpuIrTypedBodyGraphPatch.intrinsicCall(
                2,
                "mad",
                "fast-math-review",
                List.of(1, 3, 99),
                "fast-math-review"
        );

        GpuIrTypedBodyGraphPatch.Applied applied = GpuIrTypedBodyGraphPatch
                .plan("(out[0] + 2)", "intrinsic(mad template=\"\" args=[out[0], 2, unreachable])", replacement)
                .apply(body, "body\n  set tmp = (out[0] + 2)\n");

        assertTrue(applied.applied());
        assertEquals("none", applied.blocker());
        assertEquals("body\n  set tmp = intrinsic(mad template=\"\" args=[out[0], 2, unreachable])\n", applied.body());
        assertEquals("GpuIrIntrinsicCall", GpuIrTypedBodyGraphPatch.nodesById(applied.typedBody()).get(2).kind());
        assertEquals("mad", GpuIrTypedBodyGraphPatch.nodesById(applied.typedBody()).get(2).attributes().get("name"));
    }

    @Test
    void appliesTextPatchAfterSearchStartIndex() {
        IrGpuTypedBody body = typedBody();
        IrGpuTypedNode replacement = new IrGpuTypedNode(2, "GpuIrVariableRef", Map.of("name", "tmp"), Map.of());
        String text = "body\n  var int tmp = (out[0] + 2)\n  set tmp2 = (out[0] + 2)\n";

        GpuIrTypedBodyGraphPatch.Applied applied = GpuIrTypedBodyGraphPatch
                .plan("(out[0] + 2)", "tmp", replacement, text.indexOf("set tmp2"))
                .apply(body, text);

        assertTrue(applied.applied());
        assertEquals("body\n  var int tmp = (out[0] + 2)\n  set tmp2 = tmp\n", applied.body());
        assertEquals("GpuIrVariableRef", GpuIrTypedBodyGraphPatch.nodesById(applied.typedBody()).get(2).kind());
        assertEquals("tmp", GpuIrTypedBodyGraphPatch.nodesById(applied.typedBody()).get(2).attributes().get("name"));
    }

    @Test
    void blocksPlanWhenTextPatternIsMissingWithoutChangingTypedBody() {
        IrGpuTypedBody body = typedBody();
        IrGpuTypedNode replacement = GpuIrTypedBodyGraphPatch.intrinsicCall(
                2,
                "mix",
                "mix-review",
                List.of(1, 2, 3),
                "mix-review"
        );

        GpuIrTypedBodyGraphPatch.Applied applied = GpuIrTypedBodyGraphPatch
                .plan("missing", "replacement", replacement)
                .apply(body, "body\n  set tmp = (out[0] + 2)\n");

        assertFalse(applied.applied());
        assertEquals("body-text-pattern-missing", applied.blocker());
        assertEquals(body.nodes(), applied.typedBody().nodes());
        assertEquals("body\n  set tmp = (out[0] + 2)\n", applied.body());
    }

    @Test
    void blocksPlanWhenTypedRootIsMissingWithoutChangingTextBody() {
        IrGpuTypedBody body = typedBody();
        IrGpuTypedNode replacement = GpuIrTypedBodyGraphPatch.intrinsicCall(
                101,
                "step",
                "step-review",
                List.of(1, 3),
                "step-review"
        );

        GpuIrTypedBodyGraphPatch.Applied applied = GpuIrTypedBodyGraphPatch
                .plan("(out[0] + 2)", "intrinsic(step template=\"\" args=[out[0], 2])", replacement)
                .apply(body, "body\n  set tmp = (out[0] + 2)\n");

        assertFalse(applied.applied());
        assertEquals("typed-root-missing", applied.blocker());
        assertEquals(body.nodes(), applied.typedBody().nodes());
        assertEquals("body\n  set tmp = (out[0] + 2)\n", applied.body());
    }

    @Test
    void classifiesPatchBlockersForProviderCounters() {
        assertEquals(
                GpuIrTypedBodyGraphPatch.PatchBlockerKind.BODY_TEXT_PATTERN_MISSING,
                GpuIrTypedBodyGraphPatch.blockerKind("body-text-pattern-missing")
        );
        assertEquals(
                GpuIrTypedBodyGraphPatch.PatchBlockerKind.TYPED_BODY_MISSING,
                GpuIrTypedBodyGraphPatch.blockerKind("typed-body-missing")
        );
        assertEquals(
                GpuIrTypedBodyGraphPatch.PatchBlockerKind.TYPED_GRAPH_MISSING,
                GpuIrTypedBodyGraphPatch.blockerKind("typed-root-missing")
        );
        assertEquals(
                GpuIrTypedBodyGraphPatch.PatchBlockerKind.TYPED_GRAPH_MISSING,
                GpuIrTypedBodyGraphPatch.blockerKind("typed-replacement-missing")
        );
        assertEquals(
                GpuIrTypedBodyGraphPatch.PatchBlockerKind.OTHER,
                GpuIrTypedBodyGraphPatch.blockerKind("custom-blocker")
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
                        new IrGpuTypedNode(1, "GpuIrVariableRef", Map.of("name", "out[0]"), Map.of()),
                        new IrGpuTypedNode(2, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(1),
                                "right", List.of(3)
                        )),
                        new IrGpuTypedNode(3, "GpuIrLiteral", Map.of("sourceText", "2"), Map.of()),
                        new IrGpuTypedNode(99, "GpuIrLiteral", Map.of("sourceText", "unreachable"), Map.of())
                )
        );
    }
}
