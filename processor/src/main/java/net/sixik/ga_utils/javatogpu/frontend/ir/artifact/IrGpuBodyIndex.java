package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.List;

/**
 * Compact typed summary of an IrGpu method body.
 *
 * <p>This is not a full IR tree serializer yet. It records enough stable structure for runtime/debug tooling to reason
 * about body shape while the full reconstructable body format evolves.</p>
 */
public record IrGpuBodyIndex(
        int statementCount,
        List<String> statementKinds,
        List<String> expressionKinds,
        List<String> intrinsicCalls,
        List<String> helperCalls,
        boolean writesMemory,
        boolean hasControlFlow
) {

    public IrGpuBodyIndex {
        statementCount = Math.max(0, statementCount);
        statementKinds = statementKinds == null ? List.of() : List.copyOf(statementKinds);
        expressionKinds = expressionKinds == null ? List.of() : List.copyOf(expressionKinds);
        intrinsicCalls = intrinsicCalls == null ? List.of() : List.copyOf(intrinsicCalls);
        helperCalls = helperCalls == null ? List.of() : List.copyOf(helperCalls);
    }

    public static IrGpuBodyIndex empty() {
        return new IrGpuBodyIndex(0, List.of(), List.of(), List.of(), List.of(), false, false);
    }
}
