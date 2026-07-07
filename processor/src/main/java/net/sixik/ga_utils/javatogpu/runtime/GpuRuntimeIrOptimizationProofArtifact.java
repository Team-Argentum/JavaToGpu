package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Map;
import java.util.Objects;

/**
 * Backend-neutral proof evidence attached to a runtime optimizer pass.
 *
 * <p>The production runtime stays decoupled from the optional {@code ir-validation} module by accepting
 * stable artifact fields instead of concrete validator classes. I2 validation reports can export their
 * properties map and feed it here as proof input for future mutation decisions.</p>
 */
public record GpuRuntimeIrOptimizationProofArtifact(
        String source,
        String verdict,
        Map<String, String> fields
) {

    public GpuRuntimeIrOptimizationProofArtifact {
        source = normalize(source, "unknown");
        verdict = normalize(verdict, "unknown");
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    public static GpuRuntimeIrOptimizationProofArtifact fromFields(
            String source,
            String verdict,
            Map<String, String> fields
    ) {
        return new GpuRuntimeIrOptimizationProofArtifact(source, verdict, fields);
    }

    public String toLine() {
        return "proofArtifact source=" + source
                + " verdict=" + verdict
                + " fields=" + fields.size()
                + firstBlockingFieldSummary();
    }

    private String firstBlockingFieldSummary() {
        String firstBlocking = firstNonBlank(
                "optimizerProductionPreflightFirstBlocker",
                "optimizerEnablementGateFirstBlocker",
                "validationRulesFirstBlockingRuleId",
                "optimizerReadinessHandoffFirstBlocker"
        );
        return firstBlocking.isBlank() ? "" : " firstBlocking=" + firstBlocking;
    }

    private String firstNonBlank(String... keys) {
        for (String key : keys) {
            String value = fields.getOrDefault(key, "");
            if (!value.isBlank()) {
                return key + ":" + value;
            }
        }
        return "";
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNull(fallback, "fallback") : value;
    }
}
