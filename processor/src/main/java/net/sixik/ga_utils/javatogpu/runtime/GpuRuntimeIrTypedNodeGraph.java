package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Compatibility facade for the typed-node graph helper used by peephole optimization.
 */
public final class GpuRuntimeIrTypedNodeGraph {

    private final net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrTypedNodeGraphSupport delegate;

    private GpuRuntimeIrTypedNodeGraph(
            net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrTypedNodeGraphSupport delegate
    ) {
        this.delegate = delegate;
    }

    public static GpuRuntimeIrTypedNodeGraph from(IrGpuTypedBody body) {
        return new GpuRuntimeIrTypedNodeGraph(
                net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrTypedNodeGraphSupport.from(body)
        );
    }

    public static GpuRuntimeIrTypedNodeGraph from(
            IrGpuTypedBody body,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        return new GpuRuntimeIrTypedNodeGraph(
                net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrTypedNodeGraphSupport.from(body, nodesById)
        );
    }

    public IrGpuTypedBody body() {
        return delegate.body();
    }

    public List<Integer> rootNodeIds() {
        return delegate.rootNodeIds();
    }

    public List<IrGpuTypedNode> nodes() {
        return delegate.nodes();
    }

    public Map<Integer, IrGpuTypedNode> nodesById() {
        return delegate.nodesById();
    }

    public int maxNodeId() {
        return delegate.maxNodeId();
    }

    public IrGpuTypedNode node(int id) {
        return delegate.node(id);
    }

    public boolean containsNode(int id) {
        return delegate.containsNode(id);
    }

    public List<Integer> childIds(IrGpuTypedNode node, String... names) {
        return delegate.childIds(node, names);
    }

    public Integer singleChild(IrGpuTypedNode node, String... names) {
        return delegate.singleChild(node, names);
    }

    public boolean isBinary(IrGpuTypedNode node, String operator) {
        return delegate.isBinary(node, operator);
    }

    public boolean isCall(IrGpuTypedNode node, String name) {
        return delegate.isCall(node, name);
    }

    public String callName(IrGpuTypedNode node) {
        return delegate.callName(node);
    }

    public List<Integer> callArguments(IrGpuTypedNode node) {
        return delegate.callArguments(node);
    }

    public boolean isConditional(IrGpuTypedNode node) {
        return delegate.isConditional(node);
    }

    public String literalText(IrGpuTypedNode node) {
        return delegate.literalText(node);
    }

    public String attribute(IrGpuTypedNode node, String... names) {
        return delegate.attribute(node, names);
    }

    public List<Integer> missingNodeIds(Collection<Integer> ids) {
        return delegate.missingNodeIds(ids);
    }

    public static List<Integer> presentIds(Integer... ids) {
        return net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrTypedNodeGraphSupport.presentIds(ids);
    }
}
