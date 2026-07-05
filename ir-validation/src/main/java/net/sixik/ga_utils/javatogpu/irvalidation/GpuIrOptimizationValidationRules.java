package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Built-in read-only validation rules that can be registered explicitly by tests or tooling.
 */
public final class GpuIrOptimizationValidationRules {
    private static final String SAFETY_CLEAN_ID = "safety.clean";
    private static final String OPTIMIZER_NO_BLOCKING_DIAGNOSTICS_ID = "optimizer.noBlockingDiagnostics";
    private static final String OPTIMIZER_ADVISORY_DIAGNOSTICS_ID = "optimizer.advisoryDiagnostics";
    private static final String CSE_LITERAL_RUNTIME_EQUIVALENCE_EVIDENCE_ID =
            "cse.literalRuntimeEquivalenceEvidence";
    private static final String CSE_LITERAL_PROMOTION_RUNTIME_EQUIVALENCE_GATE_ID =
            "cse.literalPromotionRuntimeEquivalenceGate";
    private static final String AUTO_VECTORIZATION_PROTOTYPE_PRE_POST_RUNTIME_EQUIVALENCE_EVIDENCE_ID =
            "autoVectorization.prototypePrePostRuntimeEquivalenceEvidence";
    private static final String AUTO_VECTORIZATION_PROTOTYPE_PRE_POST_RUNTIME_EQUIVALENCE_GATE_ID =
            "autoVectorization.prototypePrePostRuntimeEquivalenceGate";

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

    public static GpuIrOptimizationValidationRule cseLiteralRuntimeEquivalenceEvidence() {
        return new GpuIrOptimizationValidationRule() {
            @Override
            public String id() {
                return CSE_LITERAL_RUNTIME_EQUIVALENCE_EVIDENCE_ID;
            }

            @Override
            public GpuIrOptimizationValidationRuleResult evaluate(GpuIrOptimizationValidationRuleContext context) {
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalence =
                        context.report().commonSubexpressionLiteralRuntimeEquivalenceReport();
                Map<String, String> metadata = literalRuntimeEquivalenceMetadata(context);
                if (!runtimeEquivalence.canonicalizationReport().hasCandidates()) {
                    return GpuIrOptimizationValidationRuleResult.passed(
                            CSE_LITERAL_RUNTIME_EQUIVALENCE_EVIDENCE_ID,
                            "method has no literal CSE runtime-equivalence candidates",
                            metadata
                    );
                }
                if (runtimeEquivalence.successful()) {
                    return GpuIrOptimizationValidationRuleResult.passed(
                            CSE_LITERAL_RUNTIME_EQUIVALENCE_EVIDENCE_ID,
                            "method has successful literal CSE runtime-equivalence evidence",
                            metadata
                    );
                }
                return GpuIrOptimizationValidationRuleResult.warned(
                        CSE_LITERAL_RUNTIME_EQUIVALENCE_EVIDENCE_ID,
                        "method has literal CSE candidates without proven runtime-equivalence evidence",
                        metadata
                );
            }
        };
    }

    public static GpuIrOptimizationValidationRule cseLiteralPromotionRuntimeEquivalenceGate() {
        return new GpuIrOptimizationValidationRule() {
            @Override
            public String id() {
                return CSE_LITERAL_PROMOTION_RUNTIME_EQUIVALENCE_GATE_ID;
            }

            @Override
            public GpuIrOptimizationValidationRuleResult evaluate(GpuIrOptimizationValidationRuleContext context) {
                GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport promotionReadiness =
                        context.report().commonSubexpressionLiteralPromotionReadinessSummaryReport();
                Map<String, String> metadata = literalPromotionRuntimeEquivalenceMetadata(context);
                if (promotionReadiness.previewCandidateCount() == 0) {
                    return GpuIrOptimizationValidationRuleResult.passed(
                            CSE_LITERAL_PROMOTION_RUNTIME_EQUIVALENCE_GATE_ID,
                            "method has no literal CSE promotion candidates",
                            metadata
                    );
                }
                if (promotionReadiness.runtimeEquivalenceSuccessful()) {
                    return GpuIrOptimizationValidationRuleResult.passed(
                            CSE_LITERAL_PROMOTION_RUNTIME_EQUIVALENCE_GATE_ID,
                            "method has runtime-equivalence evidence for literal CSE promotion review",
                            metadata
                    );
                }
                return GpuIrOptimizationValidationRuleResult.failed(
                        CSE_LITERAL_PROMOTION_RUNTIME_EQUIVALENCE_GATE_ID,
                        "method cannot pass literal CSE promotion gate without runtime-equivalence evidence",
                        metadata
                );
            }
        };
    }

    public static GpuIrOptimizationValidationRule autoVectorizationPrototypePrePostRuntimeEquivalenceEvidence(
            GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceReport prePostRuntimeEquivalenceReport
    ) {
        Objects.requireNonNull(prePostRuntimeEquivalenceReport, "prePostRuntimeEquivalenceReport");
        return new GpuIrOptimizationValidationRule() {
            @Override
            public String id() {
                return AUTO_VECTORIZATION_PROTOTYPE_PRE_POST_RUNTIME_EQUIVALENCE_EVIDENCE_ID;
            }

            @Override
            public GpuIrOptimizationValidationRuleResult evaluate(GpuIrOptimizationValidationRuleContext context) {
                Map<String, String> metadata = autoVectorizationPrototypePrePostMetadata(
                        context,
                        prePostRuntimeEquivalenceReport
                );
                if (prePostRuntimeEquivalenceReport.preOptimizationRewriteCandidates() == 0) {
                    return GpuIrOptimizationValidationRuleResult.passed(
                            AUTO_VECTORIZATION_PROTOTYPE_PRE_POST_RUNTIME_EQUIVALENCE_EVIDENCE_ID,
                            "method has no auto-vectorization prototype pre/post candidates",
                            metadata
                    );
                }
                if (prePostRuntimeEquivalenceReport.successful()) {
                    return GpuIrOptimizationValidationRuleResult.passed(
                            AUTO_VECTORIZATION_PROTOTYPE_PRE_POST_RUNTIME_EQUIVALENCE_EVIDENCE_ID,
                            "method has successful auto-vectorization prototype pre/post runtime-equivalence evidence",
                            metadata
                    );
                }
                return GpuIrOptimizationValidationRuleResult.warned(
                        AUTO_VECTORIZATION_PROTOTYPE_PRE_POST_RUNTIME_EQUIVALENCE_EVIDENCE_ID,
                        "method has auto-vectorization prototype candidates without proven pre/post runtime-equivalence evidence",
                        metadata
                );
            }
        };
    }

    public static GpuIrOptimizationValidationRule autoVectorizationPrototypePrePostRuntimeEquivalenceGate(
            GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceReport prePostRuntimeEquivalenceReport
    ) {
        Objects.requireNonNull(prePostRuntimeEquivalenceReport, "prePostRuntimeEquivalenceReport");
        return new GpuIrOptimizationValidationRule() {
            @Override
            public String id() {
                return AUTO_VECTORIZATION_PROTOTYPE_PRE_POST_RUNTIME_EQUIVALENCE_GATE_ID;
            }

            @Override
            public GpuIrOptimizationValidationRuleResult evaluate(GpuIrOptimizationValidationRuleContext context) {
                Map<String, String> metadata = autoVectorizationPrototypePrePostMetadata(
                        context,
                        prePostRuntimeEquivalenceReport
                );
                if (prePostRuntimeEquivalenceReport.preOptimizationRewriteCandidates() == 0) {
                    return GpuIrOptimizationValidationRuleResult.passed(
                            AUTO_VECTORIZATION_PROTOTYPE_PRE_POST_RUNTIME_EQUIVALENCE_GATE_ID,
                            "method has no auto-vectorization prototype pre/post candidates",
                            metadata
                    );
                }
                if (prePostRuntimeEquivalenceReport.successful()) {
                    return GpuIrOptimizationValidationRuleResult.passed(
                            AUTO_VECTORIZATION_PROTOTYPE_PRE_POST_RUNTIME_EQUIVALENCE_GATE_ID,
                            "method has passing auto-vectorization prototype pre/post runtime-equivalence gate",
                            metadata
                    );
                }
                return GpuIrOptimizationValidationRuleResult.failed(
                        AUTO_VECTORIZATION_PROTOTYPE_PRE_POST_RUNTIME_EQUIVALENCE_GATE_ID,
                        "method cannot pass auto-vectorization prototype pre/post gate without runtime-equivalence evidence",
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

    public static GpuIrOptimizationValidationRuleRegistry runtimeEquivalenceEvidenceRegistry() {
        return GpuIrOptimizationValidationRuleRegistry.of(runtimeEquivalenceEvidenceRules());
    }

    public static GpuIrOptimizationValidationRuleRegistry runtimeEquivalenceEvidenceRegistry(
            GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceReport prePostRuntimeEquivalenceReport
    ) {
        return GpuIrOptimizationValidationRuleRegistry.of(runtimeEquivalenceEvidenceRules(prePostRuntimeEquivalenceReport));
    }

    public static GpuIrOptimizationValidationRuleRegistry autoVectorizationPrototypePrePostRuntimeEquivalenceRegistry(
            GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceReport prePostRuntimeEquivalenceReport
    ) {
        return GpuIrOptimizationValidationRuleRegistry.of(
                autoVectorizationPrototypePrePostRuntimeEquivalenceRules(prePostRuntimeEquivalenceReport)
        );
    }

    public static List<GpuIrOptimizationValidationRule> runtimeEquivalenceEvidenceRules() {
        return List.of(
                cseLiteralRuntimeEquivalenceEvidence(),
                cseLiteralPromotionRuntimeEquivalenceGate()
        );
    }

    public static List<GpuIrOptimizationValidationRule> runtimeEquivalenceEvidenceRules(
            GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceReport prePostRuntimeEquivalenceReport
    ) {
        java.util.ArrayList<GpuIrOptimizationValidationRule> rules = new java.util.ArrayList<>(runtimeEquivalenceEvidenceRules());
        rules.addAll(autoVectorizationPrototypePrePostRuntimeEquivalenceRules(prePostRuntimeEquivalenceReport));
        return List.copyOf(rules);
    }

    public static List<GpuIrOptimizationValidationRule> autoVectorizationPrototypePrePostRuntimeEquivalenceRules(
            GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceReport prePostRuntimeEquivalenceReport
    ) {
        return List.of(
                autoVectorizationPrototypePrePostRuntimeEquivalenceEvidence(prePostRuntimeEquivalenceReport),
                autoVectorizationPrototypePrePostRuntimeEquivalenceGate(prePostRuntimeEquivalenceReport)
        );
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

    private static Map<String, String> literalRuntimeEquivalenceMetadata(
            GpuIrOptimizationValidationRuleContext context
    ) {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalence =
                context.report().commonSubexpressionLiteralRuntimeEquivalenceReport();
        Map<String, String> metadata = baseMetadata(context);
        metadata.put("previewCandidates", Integer.toString(runtimeEquivalence.canonicalizationReport().candidateCount()));
        metadata.put("uniqueCanonicalKeys", Integer.toString(runtimeEquivalence.canonicalizationReport().uniqueCanonicalKeyCount()));
        metadata.put("runtimeEquivalenceSuccessful", Boolean.toString(runtimeEquivalence.successful()));
        metadata.put("runtimeEquivalenceEquivalent", Boolean.toString(runtimeEquivalence.equivalent()));
        metadata.put("runtimeEquivalenceReadiness", runtimeEquivalence.readiness());
        metadata.put("runtimeEquivalenceInputCases", Integer.toString(runtimeEquivalence.inputCaseCount()));
        metadata.put("runtimeEquivalenceComparedOutputs", Integer.toString(runtimeEquivalence.comparedOutputCount()));
        metadata.put("runtimeEquivalenceDiagnostics", Integer.toString(runtimeEquivalence.diagnosticCount()));
        metadata.put("runtimeEquivalenceDiagnosticFamilyCounts", GpuIrRuntimeEquivalenceDiagnosticFamilies.countsSummary(
                runtimeEquivalence.diagnostics()
        ));
        metadata.put("numericSemanticsFullyProven", Boolean.toString(
                runtimeEquivalence.numericSemanticsProofReport().fullyProven()
        ));
        runtimeEquivalence.firstDiagnostic().ifPresent(diagnostic ->
                metadata.put("runtimeEquivalenceFirstDiagnostic", diagnostic)
        );
        return metadata;
    }

    private static Map<String, String> literalPromotionRuntimeEquivalenceMetadata(
            GpuIrOptimizationValidationRuleContext context
    ) {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport promotionReadiness =
                context.report().commonSubexpressionLiteralPromotionReadinessSummaryReport();
        Map<String, String> metadata = literalRuntimeEquivalenceMetadata(context);
        metadata.put("promotionReadinessVerdict", promotionReadiness.verdict());
        metadata.put("promotionReadyForProductionMutation", Boolean.toString(
                promotionReadiness.readyForProductionMutation()
        ));
        metadata.put("promotionRuntimeEquivalenceSuccessful", Boolean.toString(
                promotionReadiness.runtimeEquivalenceSuccessful()
        ));
        metadata.put("promotionRuntimeEquivalenceDiagnostics", Integer.toString(
                promotionReadiness.runtimeEquivalenceDiagnosticCount()
        ));
        metadata.put("promotionBlockingReasons", String.join(",", promotionReadiness.blockingReasons()));
        metadata.put("promotionRemainingWork", String.join(",", promotionReadiness.remainingWork()));
        promotionReadiness.firstBlockingReason().ifPresent(reason ->
                metadata.put("promotionFirstBlockingReason", reason)
        );
        promotionReadiness.firstRemainingWork().ifPresent(work ->
                metadata.put("promotionFirstRemainingWork", work)
        );
        return metadata;
    }

    private static Map<String, String> autoVectorizationPrototypePrePostMetadata(
            GpuIrOptimizationValidationRuleContext context,
            GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceReport prePostRuntimeEquivalenceReport
    ) {
        GpuIrAutoVectorizationReadinessSummaryReport readiness = context.report()
                .autoVectorizationArtifactSnapshot()
                .readinessSummaryReport();
        GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport runtimeEquivalenceReport =
                prePostRuntimeEquivalenceReport.artifactReport().runtimeEquivalenceReport();
        Map<String, String> metadata = baseMetadata(context);
        metadata.put("autoVectorizationReadinessVerdict", readiness.verdict());
        metadata.put("autoVectorizationReadyForPrototypeRewrite", Boolean.toString(readiness.readyForPrototypeRewrite()));
        metadata.put("autoVectorizationReadinessBlockingReasons", String.join(",", readiness.blockingReasons()));
        readiness.firstBlockingReason().ifPresent(reason ->
                metadata.put("autoVectorizationReadinessFirstBlockingReason", reason)
        );
        metadata.put("preOptimizationRewriteCandidates", Integer.toString(
                prePostRuntimeEquivalenceReport.preOptimizationRewriteCandidates()
        ));
        metadata.put("postOptimizationAppliedRewrites", Integer.toString(
                prePostRuntimeEquivalenceReport.postOptimizationAppliedRewrites()
        ));
        metadata.put("prePostSuccessful", Boolean.toString(prePostRuntimeEquivalenceReport.successful()));
        metadata.put("runtimeEquivalenceSuccessful", Boolean.toString(runtimeEquivalenceReport.successful()));
        metadata.put("runtimeEquivalenceEquivalent", Boolean.toString(runtimeEquivalenceReport.equivalent()));
        metadata.put("runtimeEquivalenceInputCases", Integer.toString(runtimeEquivalenceReport.inputCaseCount()));
        metadata.put("runtimeEquivalenceComparedOutputs", Integer.toString(runtimeEquivalenceReport.comparedOutputCount()));
        metadata.put("runtimeEquivalenceDiagnostics", Integer.toString(runtimeEquivalenceReport.diagnosticCount()));
        metadata.put("runtimeEquivalenceDiagnosticFamilyCounts", GpuIrRuntimeEquivalenceDiagnosticFamilies.countsSummary(
                runtimeEquivalenceReport.diagnostics()
        ));
        metadata.put("appliedRewriteFamilies", prePostRuntimeEquivalenceReport.artifactReport()
                .rewriteReport()
                .appliedRewriteFamilyCountersSummary());
        if (!runtimeEquivalenceReport.firstDiagnostic().isBlank()) {
            metadata.put("runtimeEquivalenceFirstDiagnostic", runtimeEquivalenceReport.firstDiagnostic());
        }
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
