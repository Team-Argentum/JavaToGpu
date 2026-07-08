package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Objects;

/**
 * Records why a runtime compile path used or kept the original IR instead of an optimized artifact.
 */
public record GpuRuntimeFallbackEvidence(
        String decision,
        String reason,
        boolean originalIrSelected,
        boolean optimizedIrRejected,
        List<String> diagnostics
) {

    public static final String NONE = "none";

    public GpuRuntimeFallbackEvidence {
        decision = normalize(decision, NONE);
        reason = normalize(reason, "no fallback was required");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static GpuRuntimeFallbackEvidence none() {
        return new GpuRuntimeFallbackEvidence(
                NONE,
                "no fallback was required",
                false,
                false,
                List.of()
        );
    }

    public static GpuRuntimeFallbackEvidence optimizerRollback(List<String> diagnostics) {
        return new GpuRuntimeFallbackEvidence(
                "optimizer-rollback",
                "optimizer requested rollback; original IR remains selected",
                true,
                true,
                diagnostics
        );
    }

    public static GpuRuntimeFallbackEvidence runtimeEquivalenceFailure(List<String> diagnostics) {
        return new GpuRuntimeFallbackEvidence(
                "runtime-equivalence-failed",
                "runtime equivalence failed; original IR must be selected",
                true,
                true,
                diagnostics
        );
    }

    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("decision=").append(decision).append('\n');
        builder.append("reason=").append(reason).append('\n');
        builder.append("originalIrSelected=").append(originalIrSelected).append('\n');
        builder.append("optimizedIrRejected=").append(optimizedIrRejected).append('\n');
        builder.append("diagnostic.count=").append(diagnostics.size()).append('\n');
        for (int index = 0; index < diagnostics.size(); index++) {
            builder.append("diagnostic.").append(index).append('=').append(diagnostics.get(index)).append('\n');
        }
        return builder.toString();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNull(fallback, "fallback") : value;
    }
}
