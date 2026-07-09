package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable typed method-body context passed to one peephole rule.
 */
public record GpuRuntimeIrPeepholeRuleContext(
        GpuRuntimeIrOptimizationRequest request,
        IrGpuArtifact artifact,
        IrGpuMethodBody methodBody,
        Map<Integer, IrGpuTypedNode> nodesById
) {

    public GpuRuntimeIrPeepholeRuleContext {
        request = Objects.requireNonNull(request, "request");
        artifact = Objects.requireNonNull(artifact, "artifact");
        methodBody = Objects.requireNonNull(methodBody, "methodBody");
        if (!methodBody.typedBody().available()) {
            throw new IllegalArgumentException("Peephole rule context requires an available typed IrGpu body");
        }
        nodesById = nodesById == null
                ? indexNodes(methodBody.typedBody())
                : Map.copyOf(nodesById);
    }

    public GpuRuntimeIrPeepholeRuleContext(
            GpuRuntimeIrOptimizationRequest request,
            IrGpuArtifact artifact,
            IrGpuMethodBody methodBody
    ) {
        this(request, artifact, methodBody, null);
    }

    public IrGpuTypedBody typedBody() {
        return methodBody.typedBody();
    }

    public IrGpuTypedNode node(int id) {
        return nodesById.get(id);
    }

    private static Map<Integer, IrGpuTypedNode> indexNodes(IrGpuTypedBody body) {
        return body.nodes().stream().collect(Collectors.toUnmodifiableMap(IrGpuTypedNode::id, node -> node));
    }
}
