package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable typed method-body context passed to one peephole rule.
 */
public record GpuRuntimeIrPeepholeRuleContext(
        GpuRuntimeIrOptimizationRequest request,
        IrGpuArtifact artifact,
        IrGpuMethodBody methodBody,
        GpuRuntimeIrTypedNodeGraph graph
) {

    public GpuRuntimeIrPeepholeRuleContext {
        request = Objects.requireNonNull(request, "request");
        artifact = Objects.requireNonNull(artifact, "artifact");
        methodBody = Objects.requireNonNull(methodBody, "methodBody");
        if (!methodBody.typedBody().available()) {
            throw new IllegalArgumentException("Peephole rule context requires an available typed IrGpu body");
        }
        graph = graph == null ? GpuRuntimeIrTypedNodeGraph.from(methodBody.typedBody()) : graph;
    }

    public GpuRuntimeIrPeepholeRuleContext(
            GpuRuntimeIrOptimizationRequest request,
            IrGpuArtifact artifact,
            IrGpuMethodBody methodBody
    ) {
        this(request, artifact, methodBody, (GpuRuntimeIrTypedNodeGraph) null);
    }

    public GpuRuntimeIrPeepholeRuleContext(
            GpuRuntimeIrOptimizationRequest request,
            IrGpuArtifact artifact,
            IrGpuMethodBody methodBody,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        this(request, artifact, methodBody, GpuRuntimeIrTypedNodeGraph.from(methodBody.typedBody(), nodesById));
    }

    public IrGpuTypedBody typedBody() {
        return methodBody.typedBody();
    }

    public IrGpuTypedNode node(int id) {
        return graph.node(id);
    }

    public Map<Integer, IrGpuTypedNode> nodesById() {
        return graph.nodesById();
    }
}
