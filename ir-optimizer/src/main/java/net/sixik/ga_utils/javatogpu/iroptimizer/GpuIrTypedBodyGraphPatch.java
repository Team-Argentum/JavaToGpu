package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GpuIrTypedBodyGraphPatch {

    private static final String BODY_TEXT_PATTERN_MISSING = "body-text-pattern-missing";
    private static final String TYPED_BODY_MISSING = "typed-body-missing";
    private static final String TYPED_REPLACEMENT_MISSING = "typed-replacement-missing";
    private static final String TYPED_ROOT_MISSING = "typed-root-missing";

    private GpuIrTypedBodyGraphPatch() {
    }

    static Map<Integer, IrGpuTypedNode> nodesById(IrGpuTypedBody typedBody) {
        LinkedHashMap<Integer, IrGpuTypedNode> nodes = new LinkedHashMap<>();
        if (typedBody == null) {
            return Map.of();
        }
        for (IrGpuTypedNode node : typedBody.nodes()) {
            nodes.put(node.id(), node);
        }
        return Map.copyOf(nodes);
    }

    static Set<Integer> reachableNodeIds(IrGpuTypedBody typedBody, Map<Integer, IrGpuTypedNode> nodesById) {
        return reachability(typedBody, nodesById).reachableNodeIds();
    }

    static Reachability reachability(IrGpuTypedBody typedBody, Map<Integer, IrGpuTypedNode> nodesById) {
        LinkedHashSet<Integer> reachable = new LinkedHashSet<>();
        if (typedBody == null) {
            return new Reachability(Set.of(), 0, 0);
        }
        Map<Integer, IrGpuTypedNode> safeNodesById = nodesById == null ? Map.of() : nodesById;
        ArrayList<Integer> pending = new ArrayList<>();
        int rootMissingCount = 0;
        int missingChildReferenceCount = 0;
        for (Integer rootNodeId : typedBody.rootNodeIds()) {
            if (rootNodeId == null || !safeNodesById.containsKey(rootNodeId)) {
                rootMissingCount++;
                continue;
            }
            pending.add(rootNodeId);
        }
        while (!pending.isEmpty()) {
            int nodeId = pending.remove(pending.size() - 1);
            if (!reachable.add(nodeId)) {
                continue;
            }
            IrGpuTypedNode node = safeNodesById.get(nodeId);
            if (node == null) {
                continue;
            }
            for (List<Integer> childIds : node.children().values()) {
                for (Integer childId : childIds) {
                    if (childId == null || !safeNodesById.containsKey(childId)) {
                        missingChildReferenceCount++;
                        continue;
                    }
                    if (!reachable.contains(childId)) {
                        pending.add(childId);
                    }
                }
            }
        }
        return new Reachability(Set.copyOf(reachable), rootMissingCount, missingChildReferenceCount);
    }

    record Reachability(Set<Integer> reachableNodeIds, int rootMissingCount, int missingChildReferenceCount) {
    }

    static int nextNodeId(IrGpuTypedBody typedBody) {
        if (typedBody == null || typedBody.nodes().isEmpty()) {
            return 0;
        }
        return typedBody.nodes().stream().mapToInt(IrGpuTypedNode::id).max().orElse(-1) + 1;
    }

    static IrGpuTypedBody replaceNode(IrGpuTypedBody typedBody, IrGpuTypedNode replacementNode) {
        return replaceAndAppend(typedBody, replacementNode, List.of());
    }

    static IrGpuTypedBody appendNode(IrGpuTypedBody typedBody, IrGpuTypedNode node) {
        if (typedBody == null) {
            return IrGpuTypedBody.none();
        }
        ArrayList<IrGpuTypedNode> nodes = new ArrayList<>(typedBody.nodes());
        nodes.add(node);
        return new IrGpuTypedBody(typedBody.format(), typedBody.rootNodeIds(), nodes);
    }

    static IrGpuTypedBody replaceAndAppend(
            IrGpuTypedBody typedBody,
            IrGpuTypedNode replacementNode,
            List<IrGpuTypedNode> appendedNodes
    ) {
        if (typedBody == null) {
            return IrGpuTypedBody.none();
        }
        ArrayList<IrGpuTypedNode> nodes = new ArrayList<>();
        boolean replaced = false;
        for (IrGpuTypedNode node : typedBody.nodes()) {
            if (node.id() == replacementNode.id()) {
                nodes.add(replacementNode);
                replaced = true;
            } else {
                nodes.add(node);
            }
        }
        if (!replaced) {
            return typedBody;
        }
        if (appendedNodes != null) {
            nodes.addAll(appendedNodes);
        }
        return new IrGpuTypedBody(typedBody.format(), typedBody.rootNodeIds(), nodes);
    }

    static Plan plan(
            String sourceText,
            String replacementText,
            IrGpuTypedNode replacementNode
    ) {
        return new Plan(sourceText, replacementText, replacementNode, List.of(), 0);
    }

    static Plan plan(
            String sourceText,
            String replacementText,
            IrGpuTypedNode replacementNode,
            int searchStartIndex
    ) {
        return new Plan(sourceText, replacementText, replacementNode, List.of(), searchStartIndex);
    }

    static Plan plan(
            String sourceText,
            String replacementText,
            IrGpuTypedNode replacementNode,
            List<IrGpuTypedNode> appendedNodes
    ) {
        return new Plan(sourceText, replacementText, replacementNode, appendedNodes, 0);
    }

    static Plan plan(
            String sourceText,
            String replacementText,
            IrGpuTypedNode replacementNode,
            List<IrGpuTypedNode> appendedNodes,
            int searchStartIndex
    ) {
        return new Plan(sourceText, replacementText, replacementNode, appendedNodes, searchStartIndex);
    }

    static PatchBlockerKind blockerKind(String blocker) {
        return switch (blocker == null ? "" : blocker) {
            case BODY_TEXT_PATTERN_MISSING -> PatchBlockerKind.BODY_TEXT_PATTERN_MISSING;
            case TYPED_BODY_MISSING -> PatchBlockerKind.TYPED_BODY_MISSING;
            case TYPED_ROOT_MISSING, TYPED_REPLACEMENT_MISSING -> PatchBlockerKind.TYPED_GRAPH_MISSING;
            default -> PatchBlockerKind.OTHER;
        };
    }

    enum PatchBlockerKind {
        BODY_TEXT_PATTERN_MISSING,
        TYPED_BODY_MISSING,
        TYPED_GRAPH_MISSING,
        OTHER
    }

    record Plan(
            String sourceText,
            String replacementText,
            IrGpuTypedNode replacementNode,
            List<IrGpuTypedNode> appendedNodes,
            int searchStartIndex
    ) {

        Plan {
            sourceText = sourceText == null ? "" : sourceText;
            replacementText = replacementText == null ? "" : replacementText;
            appendedNodes = appendedNodes == null ? List.of() : List.copyOf(appendedNodes);
            searchStartIndex = Math.max(0, searchStartIndex);
        }

        Applied apply(IrGpuTypedBody typedBody, String body) {
            if (typedBody == null || !typedBody.available()) {
                return Applied.blocked(typedBody, body, TYPED_BODY_MISSING);
            }
            if (replacementNode == null) {
                return Applied.blocked(typedBody, body, TYPED_REPLACEMENT_MISSING);
            }
            if (!nodesById(typedBody).containsKey(replacementNode.id())) {
                return Applied.blocked(typedBody, body, TYPED_ROOT_MISSING);
            }
            if (body == null || sourceText.isBlank()) {
                return Applied.blocked(typedBody, body, BODY_TEXT_PATTERN_MISSING);
            }
            int index = body.indexOf(sourceText, searchStartIndex);
            if (index < 0) {
                return Applied.blocked(typedBody, body, BODY_TEXT_PATTERN_MISSING);
            }
            String patchedBody = body.substring(0, index)
                    + replacementText
                    + body.substring(index + sourceText.length());
            IrGpuTypedBody patchedTypedBody = replaceAndAppend(typedBody, replacementNode, appendedNodes);
            return new Applied(patchedTypedBody, patchedBody, true, "none");
        }
    }

    record Applied(
            IrGpuTypedBody typedBody,
            String body,
            boolean applied,
            String blocker
    ) {

        private static Applied blocked(IrGpuTypedBody typedBody, String body, String blocker) {
            return new Applied(typedBody, body, false, blocker);
        }
    }

    static IrGpuTypedNode intrinsicCall(
            int nodeId,
            String intrinsicName,
            String resultType,
            List<Integer> argumentNodeIds,
            String argumentType
    ) {
        ArrayList<String> argumentTypes = new ArrayList<>();
        for (int index = 0; index < safeArguments(argumentNodeIds).size(); index++) {
            argumentTypes.add(argumentType);
        }
        return intrinsicCall(nodeId, intrinsicName, resultType, argumentNodeIds, argumentTypes);
    }

    static IrGpuTypedNode intrinsicCall(
            int nodeId,
            String intrinsicName,
            String resultType,
            List<Integer> argumentNodeIds,
            List<String> argumentTypes
    ) {
        List<Integer> arguments = safeArguments(argumentNodeIds);
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("name", intrinsicName);
        attributes.put("backendName", intrinsicName);
        attributes.put("codeTemplate", "");
        attributes.put("receiver.null", "true");
        attributes.put("resultType", resultType);
        attributes.put("argumentTypes.count", Integer.toString(arguments.size()));
        for (int index = 0; index < arguments.size(); index++) {
            attributes.put("argumentTypes." + index, argumentTypeAt(argumentTypes, index));
        }
        return new IrGpuTypedNode(
                nodeId,
                "GpuIrIntrinsicCall",
                attributes,
                Map.of("arguments", arguments)
        );
    }

    private static List<Integer> safeArguments(List<Integer> argumentNodeIds) {
        return argumentNodeIds == null ? List.of() : List.copyOf(argumentNodeIds);
    }

    private static String argumentTypeAt(List<String> argumentTypes, int index) {
        if (argumentTypes == null || index >= argumentTypes.size()) {
            return "unknown-review";
        }
        String argumentType = argumentTypes.get(index);
        return argumentType == null || argumentType.isBlank() ? "unknown-review" : argumentType;
    }
}
