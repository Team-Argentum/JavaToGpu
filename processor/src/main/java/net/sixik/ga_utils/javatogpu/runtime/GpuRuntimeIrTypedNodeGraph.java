package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable read-only graph view over one flattened typed IrGpu method body.
 */
public final class GpuRuntimeIrTypedNodeGraph {

    private final IrGpuTypedBody body;
    private final Map<Integer, IrGpuTypedNode> nodesById;

    private GpuRuntimeIrTypedNodeGraph(IrGpuTypedBody body, Map<Integer, IrGpuTypedNode> nodesById) {
        this.body = Objects.requireNonNull(body, "body");
        this.nodesById = Map.copyOf(nodesById == null ? indexNodes(body) : nodesById);
    }

    public static GpuRuntimeIrTypedNodeGraph from(IrGpuTypedBody body) {
        return new GpuRuntimeIrTypedNodeGraph(body, null);
    }

    public static GpuRuntimeIrTypedNodeGraph from(
            IrGpuTypedBody body,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        return new GpuRuntimeIrTypedNodeGraph(body, nodesById);
    }

    public IrGpuTypedBody body() {
        return body;
    }

    public List<Integer> rootNodeIds() {
        return body.rootNodeIds();
    }

    public List<IrGpuTypedNode> nodes() {
        return body.nodes();
    }

    public Map<Integer, IrGpuTypedNode> nodesById() {
        return nodesById;
    }

    public int maxNodeId() {
        return nodesById.keySet().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }

    public IrGpuTypedNode node(int id) {
        return nodesById.get(id);
    }

    public boolean containsNode(int id) {
        return nodesById.containsKey(id);
    }

    public List<Integer> childIds(IrGpuTypedNode node, String... names) {
        if (node == null || names == null || names.length == 0) {
            return List.of();
        }
        ArrayList<Integer> ids = new ArrayList<>();
        for (String name : names) {
            ids.addAll(node.children().getOrDefault(name, List.of()));
        }
        return List.copyOf(ids);
    }

    public Integer singleChild(IrGpuTypedNode node, String... names) {
        if (node == null || names == null) {
            return null;
        }
        for (String name : names) {
            List<Integer> ids = node.children().getOrDefault(name, List.of());
            if (ids.size() == 1) {
                return ids.get(0);
            }
        }
        return null;
    }

    public boolean isBinary(IrGpuTypedNode node, String operator) {
        return node != null
                && "GpuIrBinary".equals(node.kind())
                && Objects.equals(operator, node.attributes().get("operator"));
    }

    public boolean isCall(IrGpuTypedNode node, String name) {
        if (node == null) {
            return false;
        }
        return ("GpuIrCall".equals(node.kind())
                || "GpuIrIntrinsicCall".equals(node.kind())
                || "GpuIrFunctionCall".equals(node.kind()))
                && Objects.equals(name, callName(node));
    }

    public String callName(IrGpuTypedNode node) {
        return attribute(node, "name", "function", "intrinsic", "backendName");
    }

    public List<Integer> callArguments(IrGpuTypedNode node) {
        List<Integer> args = childIds(node, "args");
        return args.isEmpty() ? childIds(node, "arguments") : args;
    }

    public boolean isConditional(IrGpuTypedNode node) {
        return node != null && ("GpuIrConditional".equals(node.kind())
                || "GpuIrTernary".equals(node.kind())
                || "GpuIrSelect".equals(node.kind()));
    }

    public String literalText(IrGpuTypedNode node) {
        if (node == null || !"GpuIrLiteral".equals(node.kind())) {
            return "";
        }
        return attribute(node, "sourceText", "value", "literal");
    }

    public String attribute(IrGpuTypedNode node, String... names) {
        if (node == null || names == null) {
            return "";
        }
        for (String name : names) {
            String value = node.attributes().get(name);
            if (value != null) {
                return value;
            }
        }
        return "";
    }

    public List<Integer> missingNodeIds(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        ArrayList<Integer> missing = new ArrayList<>();
        for (Integer id : ids) {
            if (id != null && !nodesById.containsKey(id)) {
                missing.add(id);
            }
        }
        return List.copyOf(missing);
    }

    public static List<Integer> presentIds(Integer... ids) {
        if (ids == null || ids.length == 0) {
            return List.of();
        }
        ArrayList<Integer> present = new ArrayList<>();
        for (Integer id : ids) {
            if (id != null) {
                present.add(id);
            }
        }
        return List.copyOf(present);
    }

    private static Map<Integer, IrGpuTypedNode> indexNodes(IrGpuTypedBody body) {
        LinkedHashMap<Integer, IrGpuTypedNode> indexed = new LinkedHashMap<>();
        for (IrGpuTypedNode node : body.nodes()) {
            IrGpuTypedNode previous = indexed.putIfAbsent(node.id(), node);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate typed IrGpu node id '" + node.id() + "'");
            }
        }
        return indexed;
    }
}
