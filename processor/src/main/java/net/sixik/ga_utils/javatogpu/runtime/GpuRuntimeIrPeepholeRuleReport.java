package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;

/**
 * Rule-level candidate and proof evidence for one typed method body.
 */
public record GpuRuntimeIrPeepholeRuleReport(
        String ruleId,
        String ruleVersion,
        String methodName,
        int candidateCount,
        int proposalCount,
        boolean mutationProposed,
        String proofStatus,
        Map<String, String> fields,
        List<String> diagnostics
) {

    public GpuRuntimeIrPeepholeRuleReport {
        ruleId = normalize(ruleId, "rule:unknown");
        ruleVersion = normalize(ruleVersion, "unknown");
        methodName = normalize(methodName, "unknown");
        candidateCount = Math.max(0, candidateCount);
        proposalCount = Math.max(0, proposalCount);
        proofStatus = normalize(proofStatus, "not-proven");
        fields = fields == null ? Map.of() : Map.copyOf(fields);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static GpuRuntimeIrPeepholeRuleReport diagnosticCandidates(
            GpuRuntimeIrPeepholeRule rule,
            String methodName,
            int candidateCount,
            Map<String, String> fields
    ) {
        return new GpuRuntimeIrPeepholeRuleReport(
                rule.ruleId(),
                rule.ruleVersion(),
                methodName,
                candidateCount,
                0,
                false,
                candidateCount > 0 ? "candidate-detected" : "no-candidate",
                fields,
                List.of("rule is diagnostic-only; no mutation proposal was emitted")
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
