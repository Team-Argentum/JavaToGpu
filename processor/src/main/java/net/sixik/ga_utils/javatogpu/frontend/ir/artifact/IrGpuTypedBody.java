package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.List;

/**
 * Flattened typed method-body graph stored beside the transitional {@code ir-text-v1} snapshot.
 */
public record IrGpuTypedBody(
        String format,
        List<Integer> rootNodeIds,
        List<IrGpuTypedNode> nodes
) {

    public static final String FORMAT = "ir-tree-v1";

    public IrGpuTypedBody {
        format = format == null || format.isBlank() ? "none" : format;
        rootNodeIds = rootNodeIds == null ? List.of() : List.copyOf(rootNodeIds);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    public static IrGpuTypedBody none() {
        return new IrGpuTypedBody("none", List.of(), List.of());
    }

    public boolean available() {
        return FORMAT.equals(format) && !nodes.isEmpty();
    }
}
