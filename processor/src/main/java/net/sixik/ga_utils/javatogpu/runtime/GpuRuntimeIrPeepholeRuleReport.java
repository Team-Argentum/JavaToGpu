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
        List<GpuRuntimeIrPeepholeReplacementPlan> replacementPlans,
        List<GpuRuntimeIrPeepholeReplacementPlanValidation> replacementPlanValidations,
        List<GpuRuntimeIrPeepholeRewriteVisitPreflight> rewriteVisitPreflights,
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
        replacementPlans = replacementPlans == null ? List.of() : List.copyOf(replacementPlans);
        replacementPlanValidations = replacementPlanValidations == null
                ? List.of()
                : List.copyOf(replacementPlanValidations);
        rewriteVisitPreflights = rewriteVisitPreflights == null ? List.of() : List.copyOf(rewriteVisitPreflights);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public GpuRuntimeIrPeepholeRuleReport(
            String ruleId,
            String ruleVersion,
            String methodName,
            int candidateCount,
            int proposalCount,
            boolean mutationProposed,
            String proofStatus,
            Map<String, String> fields,
            List<GpuRuntimeIrPeepholeReplacementPlan> replacementPlans,
            List<String> diagnostics
    ) {
        this(
                ruleId,
                ruleVersion,
                methodName,
                candidateCount,
                proposalCount,
                mutationProposed,
                proofStatus,
                fields,
                replacementPlans,
                List.of(),
                List.of(),
                diagnostics
        );
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
                List.of(),
                List.of(),
                List.of(),
                List.of("rule is diagnostic-only; no mutation proposal was emitted")
        );
    }

    public static GpuRuntimeIrPeepholeRuleReport diagnosticCandidates(
            GpuRuntimeIrPeepholeRule rule,
            String methodName,
            int candidateCount,
            Map<String, String> fields,
            List<GpuRuntimeIrPeepholeReplacementPlan> replacementPlans
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
                replacementPlans,
                List.of(),
                List.of(),
                List.of("rule is diagnostic-only; no mutation proposal was emitted")
        );
    }

    public GpuRuntimeIrPeepholeRuleReport withReplacementPlanAnalysis(
            List<GpuRuntimeIrPeepholeReplacementPlanValidation> validations,
            List<GpuRuntimeIrPeepholeRewriteVisitPreflight> visitPreflights
    ) {
        return new GpuRuntimeIrPeepholeRuleReport(
                ruleId,
                ruleVersion,
                methodName,
                candidateCount,
                proposalCount,
                mutationProposed,
                proofStatus,
                fields,
                replacementPlans,
                validations,
                visitPreflights,
                diagnostics
        );
    }

    public GpuRuntimeIrPeepholeRuleReport withReplacementPlanValidations(
            List<GpuRuntimeIrPeepholeReplacementPlanValidation> validations
    ) {
        return withReplacementPlanAnalysis(validations, rewriteVisitPreflights);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
