package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.List;
import java.util.Map;

/**
 * One backend-neutral node in a flattened typed IrGpu method-body tree.
 */
public record IrGpuTypedNode(
        int id,
        String kind,
        Map<String, String> attributes,
        Map<String, List<Integer>> children
) {

    public IrGpuTypedNode {
        if (id < 0) {
            throw new IllegalArgumentException("Typed IrGpu node id must be non-negative");
        }
        kind = kind == null || kind.isBlank() ? "unknown" : kind;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        if (children == null || children.isEmpty()) {
            children = Map.of();
        } else {
            java.util.LinkedHashMap<String, List<Integer>> copied = new java.util.LinkedHashMap<>();
            children.forEach((name, ids) -> copied.put(name, ids == null ? List.of() : List.copyOf(ids)));
            children = Map.copyOf(copied);
        }
    }
}
