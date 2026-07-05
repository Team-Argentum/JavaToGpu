package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in read-only validation rules that can be registered explicitly by tests or tooling.
 */
public final class GpuIrOptimizationValidationRules {
    private static final String SAFETY_CLEAN_ID = "safety.clean";
    private static final String OPTIMIZER_NO_BLOCKING_DIAGNOSTICS_ID = "optimizer.noBlockingDiagnostics";
    private static final String OPTIMIZER_ADVISORY_DIAGNOSTICS_ID = "optimizer.advisoryDiagnostics";

    private GpuIrOptimizationValidationRules() {
    }

    public static GpuIrOptimizationValidationRule safetyClean() {
        return new GpuIrOptimizationValidationRule() {
            @Override
            public String id() {
                return SAFETY_CLEAN_ID;
            }

            @Override
            public GpuIrOptimizationValidationRuleResult evaluate(GpuIrOptimizationValidationRuleContext context) {
                Map<String, String> metadata = safetyMetadata(context);
                if (context.hasSafetyError()) {
                    return GpuIrOptimizationValidationRuleResult.failed(
                            SAFETY_CLEAN_ID,
                            "method has a safety validation error",
                            metadata
                    );
                }
                return GpuIrOptimizationValidationRuleResult.passed(
                        SAFETY_CLEAN_ID,
                        "method has no safety validation errors",
                        metadata
                );
            }
        };
    }

    public static GpuIrOptimizationValidationRule optimizerNoBlockingDiagnostics() {
        return new GpuIrOptimizationValidationRule() {
            @Override
            public String id() {
                return OPTIMIZER_NO_BLOCKING_DIAGNOSTICS_ID;
            }

            @Override
            public GpuIrOptimizationValidationRuleResult evaluate(GpuIrOptimizationValidationRuleContext context) {
                Map<String, String> metadata = optimizerMetadata(context);
                if (context.hasOptimizerDiagnostics()) {
                    return GpuIrOptimizationValidationRuleResult.failed(
                            OPTIMIZER_NO_BLOCKING_DIAGNOSTICS_ID,
                            "method has blocking optimizer diagnostics",
                            metadata
                    );
                }
                return GpuIrOptimizationValidationRuleResult.passed(
                        OPTIMIZER_NO_BLOCKING_DIAGNOSTICS_ID,
                        "method has no blocking optimizer diagnostics",
                        metadata
                );
            }
        };
    }

    public static GpuIrOptimizationValidationRule optimizerAdvisoryDiagnostics() {
        return new GpuIrOptimizationValidationRule() {
            @Override
            public String id() {
                return OPTIMIZER_ADVISORY_DIAGNOSTICS_ID;
            }

            @Override
            public GpuIrOptimizationValidationRuleResult evaluate(GpuIrOptimizationValidationRuleContext context) {
                Map<String, String> metadata = optimizerAdvisoryMetadata(context);
                if (hasAdvisoryOptimizerSignals(context.report())) {
                    return GpuIrOptimizationValidationRuleResult.warned(
                            OPTIMIZER_ADVISORY_DIAGNOSTICS_ID,
                            "method has non-blocking optimizer advisory signals",
                            metadata
                    );
                }
                return GpuIrOptimizationValidationRuleResult.passed(
                        OPTIMIZER_ADVISORY_DIAGNOSTICS_ID,
                        "method has no non-blocking optimizer advisory signals",
                        metadata
                );
            }
        };
    }

    public static GpuIrOptimizationValidationRuleRegistry defaultRegistry() {
        return GpuIrOptimizationValidationRuleRegistry.of(defaultRules());
    }

    public static List<GpuIrOptimizationValidationRule> defaultRules() {
        return List.of(safetyClean(), optimizerNoBlockingDiagnostics(), optimizerAdvisoryDiagnostics());
    }

    private static Map<String, String> safetyMetadata(GpuIrOptimizationValidationRuleContext context) {
        Map<String, String> metadata = baseMetadata(context);
        metadata.put("hasSafetyError", Boolean.toString(context.hasSafetyError()));
        context.report().safetyError().ifPresent(error -> metadata.put("safetyError", error));
        return metadata;
    }

    private static Map<String, String> optimizerMetadata(GpuIrOptimizationValidationRuleContext context) {
        GpuIrOptimizationValidationReport report = context.report();
        Map<String, String> metadata = baseMetadata(context);
        metadata.put("hasOptimizerDiagnostics", Boolean.toString(context.hasOptimizerDiagnostics()));
        metadata.put("optimizerDiagnostics", Integer.toString(report.optimizerDiagnosticCount()));
        metadata.put("optimizerGateBlocked", Boolean.toString(report.optimizerGateExplanation().blocked()));
        metadata.put("optimizerGateSource", report.optimizerGateExplanation().source());
        metadata.put("optimizerGateFamily", report.optimizerGateExplanation().family());
        metadata.put("optimizerGateSourceCounts", report.optimizerGateSourceCountsSummary());
        metadata.put("optimizerGateFamilyCounts", report.optimizerGateFamilyCountsSummary());
        return metadata;
    }

    private static Map<String, String> optimizerAdvisoryMetadata(GpuIrOptimizationValidationRuleContext context) {
        GpuIrOptimizationValidationReport report = context.report();
        Map<String, String> metadata = optimizerMetadata(context);
        metadata.put("hasAdvisoryOptimizerSignals", Boolean.toString(hasAdvisoryOptimizerSignals(report)));
        metadata.put("cseInsertions", Integer.toString(report.commonSubexpressionInsertionCount()));
        metadata.put("cseReplacements", Integer.toString(report.commonSubexpressionReplacementCount()));
        metadata.put("autoVectorizationCandidates", Integer.toString(report.autoVectorizationRewriteCandidateCount()));
        metadata.put("autoVectorizationResolvedRewriteOperations", Integer.toString(report.autoVectorizationResolvedRewriteOperationCount()));
        return metadata;
    }

    private static boolean hasAdvisoryOptimizerSignals(GpuIrOptimizationValidationReport report) {
        return !report.hasOptimizerDiagnostics()
                && (report.commonSubexpressionInsertionCount() > 0
                || report.commonSubexpressionReplacementCount() > 0
                || report.autoVectorizationRewriteCandidateCount() > 0
                || report.autoVectorizationResolvedRewriteOperationCount() > 0);
    }

    private static Map<String, String> baseMetadata(GpuIrOptimizationValidationRuleContext context) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("method", context.methodName());
        return metadata;
    }
}
