package net.sixik.ga_utils.javatogpu.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compact CI-facing summary for detecting runtime optimizer behavior drift across validation runs.
 */
public record GpuRuntimeOptimizerDriftArtifact(
        int passCount,
        int appliedPassCount,
        int skippedPassCount,
        int rolledBackPassCount,
        int failedPassCount,
        String fallbackDecision,
        String selectedRuntimeIrStage,
        String selectedRuntimeIrIdentity,
        boolean optimizedIrRejected,
        String strategyName,
        String selectedProfile,
        String baselineStatus,
        boolean promotionEligible,
        int proofArtifactCount,
        int acceptedProofArtifactCount,
        int blockingProofArtifactCount,
        int replacementPlanCompleteCount,
        int replacementPlanPartialCount,
        String replacementPlanFirstBlocker,
        int replacementPlanValidationCount,
        int replacementPlanValidationValidCount,
        int replacementPlanValidationInvalidCount,
        String replacementPlanValidationFirstBlocker,
        int rewriteSketchCount,
        int rewriteSketchReadyCount,
        int rewriteSketchBlockedCount,
        String rewriteSketchFirstBlocker,
        int rewriteSketchConflictCount,
        String rewriteSketchConflictFirstBlocker,
        String rewriteSelectionStatus,
        String rewriteSelectionFirstBlocker,
        String rewriteProofStatus,
        String rewriteProofFirstBlocker,
        String rewriteReviewPackageStatus,
        String rewriteReviewPackageFirstBlocker,
        int optimizerRuleCount,
        String optimizerRuleSummary,
        String optimizerRuleDetailsProperties,
        int optimizerFamilyCount,
        int optimizerFamilyPromotionReadyCount,
        String optimizerFamilySummary,
        String productionGateStatus,
        boolean productionProfileRequested
) {

    public static GpuRuntimeOptimizerDriftArtifact from(GpuRuntimeCompileArtifactSnapshot snapshot) {
        if (snapshot == null) {
            return new GpuRuntimeOptimizerDriftArtifact(
                    0,
                    0,
                    0,
                    0,
                    0,
                    GpuRuntimeFallbackEvidence.NONE,
                    "missing",
                    "irgpu:missing",
                    false,
                    "strategy:none",
                    "off",
                    "missing",
                    false,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    0,
                    "none",
                    "not-required",
                    "no-rewrite-sketches",
                    "not-required",
                    "no-proof-candidates",
                    "not-required",
                    "no-review-candidates",
                    0,
                    "none",
                    "",
                    0,
                    0,
                    "",
                    "not-requested",
                    false
            );
        }

        GpuRuntimeIrOptimizationReport report = snapshot.optimizationReport();
        GpuOptimizationStrategyDecision strategy = report.strategyDecision();
        GpuOptimizationVendorBaseline baseline = strategy.vendorBaseline();
        GpuRuntimeProductionOptimizerGate gate = snapshot.productionOptimizerGate();
        GpuRuntimeIrSelection selection = snapshot.runtimeIrSelection();
        Map<String, OptimizerFamilyEvidence> optimizerFamilies = optimizerFamilies(report);
        Map<String, OptimizerRuleEvidence> optimizerRules = optimizerRules(report);
        int rewriteSketchCount = rewriteSketchCount(report);
        int rewriteSketchReadyCount = rewriteSketchReadyCount(report);
        int rewriteSketchBlockedCount = rewriteSketchBlockedCount(report);
        String rewriteSketchFirstBlocker = rewriteSketchFirstBlocker(report);
        int rewriteSketchConflictCount = rewriteSketchConflictCount(report);
        String rewriteSketchConflictFirstBlocker = rewriteSketchConflictFirstBlocker(report);
        String rewriteProofStatus = rewriteProofStatus(report, rewriteSketchReadyCount);
        String rewriteProofFirstBlocker = rewriteProofFirstBlocker(report);
        return new GpuRuntimeOptimizerDriftArtifact(
                optimizationPassCount(report),
                count(report, GpuRuntimeIrOptimizationOutcome.APPLIED),
                count(report, GpuRuntimeIrOptimizationOutcome.SKIPPED),
                count(report, GpuRuntimeIrOptimizationOutcome.ROLLED_BACK),
                count(report, GpuRuntimeIrOptimizationOutcome.FAILED),
                selection.fallbackDecision(),
                selection.selectedStage(),
                selection.selectedIdentity(),
                selection.optimizedRejected(),
                strategy.strategyName(),
                strategy.selectedProfile(),
                baseline.status(),
                baseline.promotionEligible(),
                proofArtifactCount(report),
                acceptedProofArtifactCount(report),
                blockingProofArtifactCount(report),
                replacementPlanCompleteCount(report),
                replacementPlanPartialCount(report),
                replacementPlanFirstBlocker(report),
                replacementPlanValidationCount(report),
                replacementPlanValidationValidCount(report),
                replacementPlanValidationInvalidCount(report),
                replacementPlanValidationFirstBlocker(report),
                rewriteSketchCount,
                rewriteSketchReadyCount,
                rewriteSketchBlockedCount,
                rewriteSketchFirstBlocker,
                rewriteSketchConflictCount,
                rewriteSketchConflictFirstBlocker,
                rewriteSelectionStatus(report, rewriteSketchCount),
                rewriteSelectionFirstBlocker(
                        report,
                        rewriteSketchCount,
                        rewriteSketchReadyCount,
                        rewriteSketchBlockedCount,
                        rewriteSketchFirstBlocker,
                        rewriteSketchConflictCount
                ),
                rewriteProofStatus,
                rewriteProofFirstBlocker,
                rewriteReviewPackageStatus(report, rewriteSketchReadyCount),
                rewriteReviewPackageFirstBlocker(
                        report,
                        rewriteSketchReadyCount,
                        rewriteSketchConflictCount,
                        rewriteProofFirstBlocker
                ),
                optimizerRules.size(),
                formatOptimizerRuleSummary(optimizerRules),
                formatOptimizerRuleProperties(optimizerRules),
                optimizerFamilies.size(),
                promotionReadyFamilyCount(optimizerFamilies),
                formatOptimizerFamilySummary(optimizerFamilies),
                gate.status(),
                gate.productionProfileRequested()
        );
    }

    private static int optimizationPassCount(GpuRuntimeIrOptimizationReport report) {
        return (int) report.passReports().stream()
                .filter(passReport -> !passReport.analysisOnly())
                .count();
    }

    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("pass.count=").append(passCount).append('\n');
        builder.append("pass.applied.count=").append(appliedPassCount).append('\n');
        builder.append("pass.skipped.count=").append(skippedPassCount).append('\n');
        builder.append("pass.rolledBack.count=").append(rolledBackPassCount).append('\n');
        builder.append("pass.failed.count=").append(failedPassCount).append('\n');
        builder.append("fallbackDecision=").append(fallbackDecision).append('\n');
        builder.append("selectedRuntimeIrStage=").append(selectedRuntimeIrStage).append('\n');
        builder.append("selectedRuntimeIrIdentity=").append(selectedRuntimeIrIdentity).append('\n');
        builder.append("optimizedIrRejected=").append(optimizedIrRejected).append('\n');
        builder.append("strategyName=").append(strategyName).append('\n');
        builder.append("selectedProfile=").append(selectedProfile).append('\n');
        builder.append("baselineStatus=").append(baselineStatus).append('\n');
        builder.append("promotionEligible=").append(promotionEligible).append('\n');
        builder.append("proofArtifact.count=").append(proofArtifactCount).append('\n');
        builder.append("proofArtifact.accepted.count=").append(acceptedProofArtifactCount).append('\n');
        builder.append("proofArtifact.blocking.count=").append(blockingProofArtifactCount).append('\n');
        builder.append("replacementPlan.complete.count=").append(replacementPlanCompleteCount).append('\n');
        builder.append("replacementPlan.partial.count=").append(replacementPlanPartialCount).append('\n');
        builder.append("replacementPlan.firstBlocker=").append(replacementPlanFirstBlocker).append('\n');
        builder.append("replacementPlan.validation.count=").append(replacementPlanValidationCount).append('\n');
        builder.append("replacementPlan.validation.valid.count=").append(replacementPlanValidationValidCount).append('\n');
        builder.append("replacementPlan.validation.invalid.count=").append(replacementPlanValidationInvalidCount).append('\n');
        builder.append("replacementPlan.validation.firstBlocker=").append(replacementPlanValidationFirstBlocker).append('\n');
        builder.append("rewriteSketch.count=").append(rewriteSketchCount).append('\n');
        builder.append("rewriteSketch.ready.count=").append(rewriteSketchReadyCount).append('\n');
        builder.append("rewriteSketch.blocked.count=").append(rewriteSketchBlockedCount).append('\n');
        builder.append("rewriteSketch.firstBlocker=").append(rewriteSketchFirstBlocker).append('\n');
        builder.append("rewriteSketch.rewriteBuilderImplemented=false\n");
        builder.append("rewriteSketch.mutationAllowed=false\n");
        builder.append("rewriteSketch.selectedIrReplacement=false\n");
        builder.append("rewriteSketch.conflict.count=").append(rewriteSketchConflictCount).append('\n');
        builder.append("rewriteSketch.conflict.firstBlocker=").append(rewriteSketchConflictFirstBlocker).append('\n');
        builder.append("rewriteSketch.conflict.conflictResolutionImplemented=false\n");
        builder.append("rewriteSketch.conflict.selectionApplied=false\n");
        builder.append("rewriteSketch.conflict.mutationAllowed=false\n");
        builder.append("rewriteSketch.conflict.selectedIrReplacement=false\n");
        builder.append("rewriteSelection.sketch.count=").append(rewriteSketchCount).append('\n');
        builder.append("rewriteSelection.sketch.ready.count=").append(rewriteSketchReadyCount).append('\n');
        builder.append("rewriteSelection.sketch.blocked.count=").append(rewriteSketchBlockedCount).append('\n');
        builder.append("rewriteSelection.conflict.count=").append(rewriteSketchConflictCount).append('\n');
        builder.append("rewriteSelection.status=").append(rewriteSelectionStatus).append('\n');
        builder.append("rewriteSelection.firstBlocker=").append(rewriteSelectionFirstBlocker).append('\n');
        builder.append("rewriteSelection.rewriteBuilderImplemented=false\n");
        builder.append("rewriteSelection.conflictResolutionImplemented=false\n");
        builder.append("rewriteSelection.runtimeEquivalenceRequired=").append(rewriteSketchReadyCount > 0).append('\n');
        builder.append("rewriteSelection.runtimeEquivalenceProven=false\n");
        builder.append("rewriteSelection.approvalRequired=").append(rewriteSketchReadyCount > 0).append('\n');
        builder.append("rewriteSelection.approvalAccepted=false\n");
        builder.append("rewriteSelection.mutationAllowed=false\n");
        builder.append("rewriteSelection.selectionApplied=false\n");
        builder.append("rewriteSelection.selectedIrReplacement=false\n");
        builder.append("rewriteProof.status=").append(rewriteProofStatus).append('\n');
        builder.append("rewriteProof.firstBlocker=").append(rewriteProofFirstBlocker).append('\n');
        builder.append("rewriteProof.proofAccepted=false\n");
        builder.append("rewriteProof.runtimeEquivalencePayload.present=false\n");
        builder.append("rewriteProof.runtimeEquivalencePayload.complete=false\n");
        builder.append("rewriteProof.rollbackEvidence.present=false\n");
        builder.append("rewriteProof.rollbackClean=false\n");
        builder.append("rewriteProof.approvalAccepted=false\n");
        builder.append("rewriteProof.mutationAllowed=false\n");
        builder.append("rewriteProof.selectedIrReplacement=false\n");
        builder.append("rewriteReviewPackage.status=").append(rewriteReviewPackageStatus).append('\n');
        builder.append("rewriteReviewPackage.firstBlocker=").append(rewriteReviewPackageFirstBlocker).append('\n');
        builder.append("rewriteReviewPackage.required=").append(rewriteSketchReadyCount > 0).append('\n');
        builder.append("rewriteReviewPackage.complete=false\n");
        builder.append("rewriteReviewPackage.conflict.count=").append(rewriteSketchConflictCount).append('\n');
        builder.append("rewriteReviewPackage.proofAccepted=false\n");
        builder.append("rewriteReviewPackage.runtimeEquivalencePayload.present=false\n");
        builder.append("rewriteReviewPackage.runtimeEquivalencePayload.complete=false\n");
        builder.append("rewriteReviewPackage.rollbackEvidence.present=false\n");
        builder.append("rewriteReviewPackage.rollbackClean=false\n");
        builder.append("rewriteReviewPackage.approvalAccepted=false\n");
        builder.append("rewriteReviewPackage.mutationAllowed=false\n");
        builder.append("rewriteReviewPackage.selectionApplied=false\n");
        builder.append("rewriteReviewPackage.selectedIrReplacement=false\n");
        builder.append("rewriteReviewPackage.manualReviewOnly=true\n");
        builder.append("optimizerRule.count=").append(optimizerRuleCount).append('\n');
        builder.append("optimizerRule.summary=").append(optimizerRuleSummary).append('\n');
        if (optimizerRuleDetailsProperties != null && !optimizerRuleDetailsProperties.isBlank()) {
            builder.append(optimizerRuleDetailsProperties);
        }
        builder.append("optimizerFamily.count=").append(optimizerFamilyCount).append('\n');
        builder.append("optimizerFamily.promotionReady.count=").append(optimizerFamilyPromotionReadyCount).append('\n');
        builder.append("optimizerFamily.summary=").append(optimizerFamilySummary).append('\n');
        builder.append("productionGateStatus=").append(productionGateStatus).append('\n');
        builder.append("productionProfileRequested=").append(productionProfileRequested).append('\n');
        return builder.toString();
    }

    private static int count(GpuRuntimeIrOptimizationReport report, GpuRuntimeIrOptimizationOutcome outcome) {
        return (int) report.passReports().stream()
                .filter(passReport -> !passReport.analysisOnly())
                .filter(passReport -> passReport.outcome() == outcome)
                .count();
    }

    private static int proofArtifactCount(GpuRuntimeIrOptimizationReport report) {
        return (int) report.passReports().stream()
                .filter(passReport -> !passReport.analysisOnly())
                .filter(GpuRuntimeOptimizerDriftArtifact::hasProofArtifact)
                .count();
    }

    private static int acceptedProofArtifactCount(GpuRuntimeIrOptimizationReport report) {
        return (int) report.passReports().stream()
                .filter(passReport -> !passReport.analysisOnly())
                .filter(GpuRuntimeOptimizerDriftArtifact::hasProofArtifact)
                .filter(passReport -> isAcceptedVerdict(passReport.proofArtifact().verdict()))
                .count();
    }

    private static int blockingProofArtifactCount(GpuRuntimeIrOptimizationReport report) {
        return (int) report.passReports().stream()
                .filter(passReport -> !passReport.analysisOnly())
                .filter(GpuRuntimeOptimizerDriftArtifact::hasProofArtifact)
                .filter(passReport -> isBlockingVerdict(passReport.proofArtifact().verdict()))
                .count();
    }

    private static int replacementPlanCompleteCount(GpuRuntimeIrOptimizationReport report) {
        return replacementPlanRuleCount(report, ".replacementPlan.complete.count");
    }

    private static int replacementPlanPartialCount(GpuRuntimeIrOptimizationReport report) {
        int total = 0;
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                Map<String, String> fields = passReport.proofArtifact().fields();
                int passTotal = parseInt(fields.get("replacementPlan.partial.count"));
                total += passTotal > 0 ? passTotal : replacementPlanRuleCount(fields, ".replacementPlan.partial.count");
            }
        }
        return total;
    }

    private static int replacementPlanRuleCount(GpuRuntimeIrOptimizationReport report, String suffix) {
        int total = 0;
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                total += replacementPlanRuleCount(passReport.proofArtifact().fields(), suffix);
            }
        }
        return total;
    }

    private static int replacementPlanRuleCount(Map<String, String> fields, String suffix) {
        return fields.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("rule."))
                .filter(entry -> entry.getKey().endsWith(suffix))
                .mapToInt(entry -> parseInt(entry.getValue()))
                .sum();
    }

    private static String replacementPlanFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                String blocker = passReport.proofArtifact().fields().getOrDefault("replacementPlan.firstBlocker", "none");
                if (!blocker.isBlank() && !"none".equals(blocker)) {
                    return blocker;
                }
            }
        }
        return "none";
    }

    private static int replacementPlanValidationCount(GpuRuntimeIrOptimizationReport report) {
        return replacementPlanRuleCount(report, ".replacementPlan.validation.count");
    }

    private static int replacementPlanValidationValidCount(GpuRuntimeIrOptimizationReport report) {
        return replacementPlanRuleCount(report, ".replacementPlan.validation.valid.count");
    }

    private static int replacementPlanValidationInvalidCount(GpuRuntimeIrOptimizationReport report) {
        int total = 0;
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                Map<String, String> fields = passReport.proofArtifact().fields();
                int passTotal = parseInt(fields.get("replacementPlan.validation.invalid.count"));
                total += passTotal > 0 ? passTotal : replacementPlanRuleCount(fields, ".replacementPlan.validation.invalid.count");
            }
        }
        return total;
    }

    private static String replacementPlanValidationFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                Map<String, String> fields = passReport.proofArtifact().fields();
                String blocker = fields.getOrDefault("replacementPlan.validation.firstBlocker", "none");
                if (!blocker.isBlank() && !"none".equals(blocker)) {
                    return blocker;
                }
                String ruleBlocker = firstRuleBlocker(fields, ".replacementPlan.validation.firstBlocker");
                if (!"none".equals(ruleBlocker)) {
                    return ruleBlocker;
                }
            }
        }
        return "none";
    }

    private static int rewriteSketchCount(GpuRuntimeIrOptimizationReport report) {
        return rewriteSketchRuleCount(report, ".rewriteSketch.count");
    }

    private static int rewriteSketchReadyCount(GpuRuntimeIrOptimizationReport report) {
        return rewriteSketchRuleCount(report, ".rewriteSketch.ready.count");
    }

    private static int rewriteSketchBlockedCount(GpuRuntimeIrOptimizationReport report) {
        int total = 0;
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                Map<String, String> fields = passReport.proofArtifact().fields();
                int passTotal = parseInt(fields.get("rewriteSketch.blocked.count"));
                total += passTotal > 0 ? passTotal : rewriteSketchRuleCount(fields, ".rewriteSketch.blocked.count");
            }
        }
        return total;
    }

    private static int rewriteSketchRuleCount(GpuRuntimeIrOptimizationReport report, String suffix) {
        int total = 0;
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                total += rewriteSketchRuleCount(passReport.proofArtifact().fields(), suffix);
            }
        }
        return total;
    }

    private static int rewriteSketchRuleCount(Map<String, String> fields, String suffix) {
        return fields.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("rule."))
                .filter(entry -> entry.getKey().endsWith(suffix))
                .mapToInt(entry -> parseInt(entry.getValue()))
                .sum();
    }

    private static String rewriteSketchFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                Map<String, String> fields = passReport.proofArtifact().fields();
                String blocker = fields.getOrDefault("rewriteSketch.firstBlocker", "none");
                if (!blocker.isBlank() && !"none".equals(blocker)) {
                    return blocker;
                }
                String ruleBlocker = firstRuleBlocker(fields, ".rewriteSketch.firstBlocker");
                if (!"none".equals(ruleBlocker)) {
                    return ruleBlocker;
                }
            }
        }
        return "none";
    }

    private static int rewriteSketchConflictCount(GpuRuntimeIrOptimizationReport report) {
        int total = 0;
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                total += parseInt(passReport.proofArtifact().fields().get("rewriteSketch.conflict.count"));
            }
        }
        return total;
    }

    private static String rewriteSketchConflictFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                String blocker = passReport.proofArtifact().fields().getOrDefault(
                        "rewriteSketch.conflict.firstBlocker",
                        "none"
                );
                if (!blocker.isBlank() && !"none".equals(blocker)) {
                    return blocker;
                }
            }
        }
        return "none";
    }

    private static String rewriteSelectionStatus(GpuRuntimeIrOptimizationReport report, int rewriteSketchCount) {
        String fallback = rewriteSketchCount == 0 ? "not-required" : "blocked";
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                String status = passReport.proofArtifact().fields().getOrDefault("rewriteSelection.status", "");
                if ("blocked".equals(status)) {
                    return status;
                }
                if (!status.isBlank()) {
                    fallback = status;
                }
            }
        }
        return fallback;
    }

    private static String rewriteSelectionFirstBlocker(
            GpuRuntimeIrOptimizationReport report,
            int rewriteSketchCount,
            int rewriteSketchReadyCount,
            int rewriteSketchBlockedCount,
            String rewriteSketchFirstBlocker,
            int rewriteSketchConflictCount
    ) {
        String fallback = derivedRewriteSelectionFirstBlocker(
                rewriteSketchCount,
                rewriteSketchReadyCount,
                rewriteSketchBlockedCount,
                rewriteSketchFirstBlocker,
                rewriteSketchConflictCount
        );
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                String blocker = passReport.proofArtifact().fields().getOrDefault("rewriteSelection.firstBlocker", "");
                if (!blocker.isBlank() && !"none".equals(blocker) && !"no-rewrite-sketches".equals(blocker)) {
                    return blocker;
                }
                if (!blocker.isBlank()) {
                    fallback = blocker;
                }
            }
        }
        return fallback;
    }

    private static String derivedRewriteSelectionFirstBlocker(
            int rewriteSketchCount,
            int rewriteSketchReadyCount,
            int rewriteSketchBlockedCount,
            String rewriteSketchFirstBlocker,
            int rewriteSketchConflictCount
    ) {
        if (rewriteSketchCount == 0) {
            return "no-rewrite-sketches";
        }
        if (rewriteSketchBlockedCount > 0) {
            return rewriteSketchFirstBlocker == null || rewriteSketchFirstBlocker.isBlank() || "none".equals(rewriteSketchFirstBlocker)
                    ? "rewrite-sketch-blocked"
                    : rewriteSketchFirstBlocker;
        }
        if (rewriteSketchConflictCount > 0) {
            return "rewrite-sketch-conflict-resolution-required";
        }
        return rewriteSketchReadyCount > 0 ? "rewrite-builder-not-implemented" : "no-rewrite-sketches";
    }

    private static String rewriteProofStatus(GpuRuntimeIrOptimizationReport report, int rewriteSketchReadyCount) {
        String fallback = rewriteSketchReadyCount == 0 ? "not-required" : "blocked";
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                String status = passReport.proofArtifact().fields().getOrDefault("rewriteProof.status", "");
                if ("blocked".equals(status)) {
                    return status;
                }
                if (!status.isBlank()) {
                    fallback = status;
                }
            }
        }
        return fallback;
    }

    private static String rewriteProofFirstBlocker(GpuRuntimeIrOptimizationReport report) {
        String fallback = "no-proof-candidates";
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                Map<String, String> fields = passReport.proofArtifact().fields();
                String blocker = fields.getOrDefault("rewriteProof.firstBlocker", "");
                if (!blocker.isBlank() && !"none".equals(blocker) && !"no-proof-candidates".equals(blocker)) {
                    return blocker;
                }
                if (!blocker.isBlank()) {
                    fallback = blocker;
                }
            }
        }
        return fallback;
    }

    private static String rewriteReviewPackageStatus(GpuRuntimeIrOptimizationReport report, int rewriteSketchReadyCount) {
        String fallback = rewriteSketchReadyCount == 0 ? "not-required" : "blocked";
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                String status = passReport.proofArtifact().fields().getOrDefault("rewriteReviewPackage.status", "");
                if ("blocked".equals(status)) {
                    return status;
                }
                if (!status.isBlank()) {
                    fallback = status;
                }
            }
        }
        return fallback;
    }

    private static String rewriteReviewPackageFirstBlocker(
            GpuRuntimeIrOptimizationReport report,
            int rewriteSketchReadyCount,
            int rewriteSketchConflictCount,
            String rewriteProofFirstBlocker
    ) {
        String fallback = derivedRewriteReviewPackageFirstBlocker(
                rewriteSketchReadyCount,
                rewriteSketchConflictCount,
                rewriteProofFirstBlocker
        );
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (!passReport.analysisOnly() && hasProofArtifact(passReport)) {
                String blocker = passReport.proofArtifact().fields().getOrDefault("rewriteReviewPackage.firstBlocker", "");
                if (!blocker.isBlank() && !"none".equals(blocker) && !"no-review-candidates".equals(blocker)) {
                    return blocker;
                }
                if (!blocker.isBlank()) {
                    fallback = blocker;
                }
            }
        }
        return fallback;
    }

    private static String derivedRewriteReviewPackageFirstBlocker(
            int rewriteSketchReadyCount,
            int rewriteSketchConflictCount,
            String rewriteProofFirstBlocker
    ) {
        if (rewriteSketchReadyCount == 0) {
            return "no-review-candidates";
        }
        if (rewriteSketchConflictCount > 0) {
            return "rewrite-sketch-conflict-resolution-required";
        }
        if (rewriteProofFirstBlocker == null
                || rewriteProofFirstBlocker.isBlank()
                || "none".equals(rewriteProofFirstBlocker)
                || "no-proof-candidates".equals(rewriteProofFirstBlocker)) {
            return "runtime-equivalence-payload-missing";
        }
        return rewriteProofFirstBlocker;
    }

    private static String firstRuleBlocker(Map<String, String> fields, String suffix) {
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String blocker = entry.getValue();
            if (entry.getKey().startsWith("rule.")
                    && entry.getKey().endsWith(suffix)
                    && blocker != null
                    && !blocker.isBlank()
                    && !"none".equals(blocker)) {
                return blocker;
            }
        }
        return "none";
    }

    private static Map<String, OptimizerRuleEvidence> optimizerRules(GpuRuntimeIrOptimizationReport report) {
        Map<String, OptimizerRuleEvidence> rules = new LinkedHashMap<>();
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (passReport.analysisOnly() || !hasProofArtifact(passReport)) {
                continue;
            }
            Map<String, String> fields = passReport.proofArtifact().fields();
            int ruleCount = parseInt(fields.get("rule.count"));
            for (int index = 0; index < ruleCount; index++) {
                String prefix = "rule." + index;
                String id = fields.getOrDefault(prefix + ".id", "");
                if (id.isBlank()) {
                    continue;
                }
                OptimizerRuleEvidence existing = rules.getOrDefault(id, OptimizerRuleEvidence.empty(id));
                rules.put(id, existing.add(fields, prefix));
            }
        }
        return rules;
    }

    private static String formatOptimizerRuleSummary(Map<String, OptimizerRuleEvidence> rules) {
        if (rules.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (OptimizerRuleEvidence rule : rules.values()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(rule.id())
                    .append("[candidates=")
                    .append(rule.candidateCount())
                    .append(", proposals=")
                    .append(rule.proposalCount())
                    .append(", applied=")
                    .append(rule.appliedCount())
                    .append(", skipped=")
                    .append(rule.skippedCount())
                    .append(", blocked=")
                    .append(rule.blockedCount())
                    .append(", mutationProposed=")
                    .append(rule.mutationProposed())
                    .append(", replacementPlans=")
                    .append(rule.replacementPlanCount())
                    .append(", completePlans=")
                    .append(rule.replacementPlanCompleteCount())
                    .append(", partialPlans=")
                    .append(rule.replacementPlanPartialCount())
                    .append(", planValidations=")
                    .append(rule.replacementPlanValidationCount())
                    .append(", invalidPlanValidations=")
                    .append(rule.replacementPlanValidationInvalidCount())
                    .append(", planValidationFirstBlocker=")
                    .append(rule.replacementPlanValidationFirstBlocker())
                    .append(", rewriteSketches=")
                    .append(rule.rewriteSketchCount())
                    .append(", readySketches=")
                    .append(rule.rewriteSketchReadyCount())
                    .append(", blockedSketches=")
                    .append(rule.rewriteSketchBlockedCount())
                    .append(", rewriteSketchFirstBlocker=")
                    .append(rule.rewriteSketchFirstBlocker())
                    .append(", rewriteSelectionStatus=")
                    .append(rule.rewriteSelectionStatus())
                    .append(", rewriteSelectionFirstBlocker=")
                    .append(rule.rewriteSelectionFirstBlocker())
                    .append(", rewriteProofStatus=")
                    .append(rule.rewriteProofStatus())
                    .append(", rewriteProofFirstBlocker=")
                    .append(rule.rewriteProofFirstBlocker())
                    .append(", rewriteReviewPackageStatus=")
                    .append(rule.rewriteReviewPackageStatus())
                    .append(", rewriteReviewPackageFirstBlocker=")
                    .append(rule.rewriteReviewPackageFirstBlocker())
                    .append(", firstBlocker=")
                    .append(rule.firstBlocker())
                    .append(']');
        }
        return builder.toString();
    }

    private static String formatOptimizerRuleProperties(Map<String, OptimizerRuleEvidence> rules) {
        if (rules.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (OptimizerRuleEvidence rule : rules.values()) {
            String prefix = "optimizerRule." + index + ".";
            builder.append(prefix).append("id=").append(rule.id()).append('\n');
            builder.append(prefix).append("version=").append(rule.version()).append('\n');
            builder.append(prefix).append("extensionId=").append(rule.extensionId()).append('\n');
            builder.append(prefix).append("extensionVersion=").append(rule.extensionVersion()).append('\n');
            builder.append(prefix).append("proofStatus=").append(rule.proofStatus()).append('\n');
            builder.append(prefix).append("candidate.count=").append(rule.candidateCount()).append('\n');
            builder.append(prefix).append("proposal.count=").append(rule.proposalCount()).append('\n');
            builder.append(prefix).append("applied.count=").append(rule.appliedCount()).append('\n');
            builder.append(prefix).append("skipped.count=").append(rule.skippedCount()).append('\n');
            builder.append(prefix).append("blocked.count=").append(rule.blockedCount()).append('\n');
            builder.append(prefix).append("mutationProposed=").append(rule.mutationProposed()).append('\n');
            builder.append(prefix).append("replacementPlan.count=").append(rule.replacementPlanCount()).append('\n');
            builder.append(prefix).append("replacementPlan.complete.count=").append(rule.replacementPlanCompleteCount()).append('\n');
            builder.append(prefix).append("replacementPlan.partial.count=").append(rule.replacementPlanPartialCount()).append('\n');
            builder.append(prefix).append("replacementPlan.firstBlocker=").append(rule.firstBlocker()).append('\n');
            builder.append(prefix).append("replacementPlan.validation.count=").append(rule.replacementPlanValidationCount()).append('\n');
            builder.append(prefix).append("replacementPlan.validation.valid.count=").append(rule.replacementPlanValidationValidCount()).append('\n');
            builder.append(prefix).append("replacementPlan.validation.invalid.count=").append(rule.replacementPlanValidationInvalidCount()).append('\n');
            builder.append(prefix).append("replacementPlan.validation.firstBlocker=").append(rule.replacementPlanValidationFirstBlocker()).append('\n');
            builder.append(prefix).append("rewriteSketch.count=").append(rule.rewriteSketchCount()).append('\n');
            builder.append(prefix).append("rewriteSketch.ready.count=").append(rule.rewriteSketchReadyCount()).append('\n');
            builder.append(prefix).append("rewriteSketch.blocked.count=").append(rule.rewriteSketchBlockedCount()).append('\n');
            builder.append(prefix).append("rewriteSketch.firstBlocker=").append(rule.rewriteSketchFirstBlocker()).append('\n');
            builder.append(prefix).append("rewriteSketch.rewriteBuilderImplemented=false\n");
            builder.append(prefix).append("rewriteSketch.mutationAllowed=false\n");
            builder.append(prefix).append("rewriteSketch.selectedIrReplacement=false\n");
            builder.append(prefix).append("rewriteSelection.status=").append(rule.rewriteSelectionStatus()).append('\n');
            builder.append(prefix).append("rewriteSelection.firstBlocker=").append(rule.rewriteSelectionFirstBlocker()).append('\n');
            builder.append(prefix).append("rewriteSelection.selectionApplied=false\n");
            builder.append(prefix).append("rewriteSelection.selectedIrReplacement=false\n");
            builder.append(prefix).append("rewriteProof.status=").append(rule.rewriteProofStatus()).append('\n');
            builder.append(prefix).append("rewriteProof.firstBlocker=").append(rule.rewriteProofFirstBlocker()).append('\n');
            builder.append(prefix).append("rewriteProof.proofAccepted=false\n");
            builder.append(prefix).append("rewriteProof.runtimeEquivalencePayload.complete=false\n");
            builder.append(prefix).append("rewriteProof.rollbackClean=false\n");
            builder.append(prefix).append("rewriteProof.selectedIrReplacement=false\n");
            builder.append(prefix).append("rewriteReviewPackage.status=").append(rule.rewriteReviewPackageStatus()).append('\n');
            builder.append(prefix).append("rewriteReviewPackage.firstBlocker=").append(rule.rewriteReviewPackageFirstBlocker()).append('\n');
            builder.append(prefix).append("rewriteReviewPackage.complete=false\n");
            builder.append(prefix).append("rewriteReviewPackage.selectedIrReplacement=false\n");
            builder.append(prefix).append("firstBlocker=").append(rule.firstBlocker()).append('\n');
            index++;
        }
        return builder.toString();
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean hasProofArtifact(GpuRuntimeIrOptimizationPassReport passReport) {
        GpuRuntimeIrOptimizationProofArtifact proofArtifact = passReport.proofArtifact();
        return proofArtifact != null
                && (!"none".equals(proofArtifact.source()) || !proofArtifact.fields().isEmpty());
    }

    private static Map<String, OptimizerFamilyEvidence> optimizerFamilies(GpuRuntimeIrOptimizationReport report) {
        Map<String, OptimizerFamilyEvidence> families = new LinkedHashMap<>();
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (passReport.analysisOnly()) {
                continue;
            }
            String familyName = optimizerFamilyName(passReport);
            OptimizerFamilyEvidence existing = families.getOrDefault(familyName, OptimizerFamilyEvidence.empty(familyName));
            families.put(familyName, existing.add(passReport));
        }
        return families;
    }

    private static String optimizerFamilyName(GpuRuntimeIrOptimizationPassReport passReport) {
        GpuRuntimeIrOptimizationProofArtifact proofArtifact = passReport.proofArtifact();
        String explicitFamily = proofArtifact == null ? "" : proofArtifact.fields().getOrDefault("optimizerFamily", "");
        if (!explicitFamily.isBlank()) {
            return explicitFamily;
        }
        String optimizerVersion = passReport.optimizerVersion();
        int separator = optimizerVersion.indexOf(':');
        return separator >= 0 && separator < optimizerVersion.length() - 1
                ? optimizerVersion.substring(separator + 1)
                : optimizerVersion;
    }

    private static int promotionReadyFamilyCount(Map<String, OptimizerFamilyEvidence> families) {
        int count = 0;
        for (OptimizerFamilyEvidence family : families.values()) {
            if (family.promotionReady()) {
                count++;
            }
        }
        return count;
    }

    private static String formatOptimizerFamilySummary(Map<String, OptimizerFamilyEvidence> families) {
        if (families.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (OptimizerFamilyEvidence family : families.values()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(family.name())
                    .append("[passes=")
                    .append(family.passCount())
                    .append(", acceptedProof=")
                    .append(family.acceptedProofCount())
                    .append(", blockingProof=")
                    .append(family.blockingProofCount())
                    .append(", rolledBack=")
                    .append(family.rolledBackCount())
                    .append(", failed=")
                    .append(family.failedCount())
                    .append(", promotionReady=")
                    .append(family.promotionReady())
                    .append(']');
        }
        return builder.toString();
    }

    private static boolean isAcceptedVerdict(String verdict) {
        String normalized = verdict == null ? "" : verdict.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("accepted") || normalized.contains("passed") || normalized.contains("ready");
    }

    private static boolean isBlockingVerdict(String verdict) {
        String normalized = verdict == null ? "" : verdict.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("reject")
                || normalized.contains("block")
                || normalized.contains("fail")
                || normalized.contains("invalid");
    }

    private record OptimizerFamilyEvidence(
            String name,
            int passCount,
            int acceptedProofCount,
            int blockingProofCount,
            int rolledBackCount,
            int failedCount
    ) {

        private static OptimizerFamilyEvidence empty(String name) {
            return new OptimizerFamilyEvidence(name, 0, 0, 0, 0, 0);
        }

        private OptimizerFamilyEvidence add(GpuRuntimeIrOptimizationPassReport passReport) {
            boolean hasProofArtifact = hasProofArtifact(passReport);
            return new OptimizerFamilyEvidence(
                    name,
                    passCount + 1,
                    acceptedProofCount + (hasProofArtifact && isAcceptedVerdict(passReport.proofArtifact().verdict()) ? 1 : 0),
                    blockingProofCount + (hasProofArtifact && isBlockingVerdict(passReport.proofArtifact().verdict()) ? 1 : 0),
                    rolledBackCount + (passReport.outcome() == GpuRuntimeIrOptimizationOutcome.ROLLED_BACK ? 1 : 0),
                    failedCount + (passReport.outcome() == GpuRuntimeIrOptimizationOutcome.FAILED ? 1 : 0)
            );
        }

        private boolean promotionReady() {
            return acceptedProofCount > 0 && blockingProofCount == 0 && rolledBackCount == 0 && failedCount == 0;
        }
    }

    private record OptimizerRuleEvidence(
            String id,
            String version,
            String extensionId,
            String extensionVersion,
            String proofStatus,
            int candidateCount,
            int proposalCount,
            int appliedCount,
            int skippedCount,
            int blockedCount,
            boolean mutationProposed,
            int replacementPlanCount,
            int replacementPlanCompleteCount,
            int replacementPlanPartialCount,
            int replacementPlanValidationCount,
            int replacementPlanValidationValidCount,
            int replacementPlanValidationInvalidCount,
            String replacementPlanValidationFirstBlocker,
            int rewriteSketchCount,
            int rewriteSketchReadyCount,
            int rewriteSketchBlockedCount,
            String rewriteSketchFirstBlocker,
            String rewriteSelectionStatus,
            String rewriteSelectionFirstBlocker,
            String rewriteProofStatus,
            String rewriteProofFirstBlocker,
            String rewriteReviewPackageStatus,
            String rewriteReviewPackageFirstBlocker,
            String firstBlocker
    ) {

        private static OptimizerRuleEvidence empty(String id) {
            return new OptimizerRuleEvidence(
                    id,
                    "unknown",
                    "unknown",
                    "unknown",
                    "unknown",
                    0,
                    0,
                    0,
                    0,
                    0,
                    false,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "none",
                    0,
                    0,
                    0,
                    "none",
                    "not-required",
                    "no-rewrite-sketches",
                    "not-required",
                    "no-proof-candidates",
                    "not-required",
                    "no-review-candidates",
                    "none"
            );
        }

        private OptimizerRuleEvidence add(Map<String, String> fields, String prefix) {
            String blocker = firstBlocker;
            String validationBlocker = replacementPlanValidationFirstBlocker;
            String nextValidationBlocker = fields.getOrDefault(prefix + ".replacementPlan.validation.firstBlocker", "none");
            if ("none".equals(validationBlocker)
                    && !nextValidationBlocker.isBlank()
                    && !"none".equals(nextValidationBlocker)) {
                validationBlocker = nextValidationBlocker;
            }
            String sketchBlocker = rewriteSketchFirstBlocker;
            String nextSketchBlocker = fields.getOrDefault(prefix + ".rewriteSketch.firstBlocker", "none");
            if ("none".equals(sketchBlocker)
                    && !nextSketchBlocker.isBlank()
                    && !"none".equals(nextSketchBlocker)) {
                sketchBlocker = nextSketchBlocker;
            }
            String selectionStatus = firstNonDefault(
                    rewriteSelectionStatus,
                    fields.getOrDefault(prefix + ".rewriteSelection.status", "not-required"),
                    "not-required"
            );
            String selectionBlocker = firstNonDefault(
                    rewriteSelectionFirstBlocker,
                    fields.getOrDefault(prefix + ".rewriteSelection.firstBlocker", "no-rewrite-sketches"),
                    "no-rewrite-sketches"
            );
            String proofStatus = firstNonDefault(
                    rewriteProofStatus,
                    fields.getOrDefault(prefix + ".rewriteProof.status", "not-required"),
                    "not-required"
            );
            String proofBlocker = firstNonDefault(
                    rewriteProofFirstBlocker,
                    fields.getOrDefault(prefix + ".rewriteProof.firstBlocker", "no-proof-candidates"),
                    "no-proof-candidates"
            );
            String reviewPackageStatus = firstNonDefault(
                    rewriteReviewPackageStatus,
                    fields.getOrDefault(prefix + ".rewriteReviewPackage.status", "not-required"),
                    "not-required"
            );
            String reviewPackageBlocker = firstNonDefault(
                    rewriteReviewPackageFirstBlocker,
                    fields.getOrDefault(prefix + ".rewriteReviewPackage.firstBlocker", "no-review-candidates"),
                    "no-review-candidates"
            );
            String nextBlocker = fields.getOrDefault(
                    prefix + ".firstBlocker",
                    fields.getOrDefault(prefix + ".replacementPlan.firstBlocker", "none")
            );
            if ("none".equals(blocker) && !nextBlocker.isBlank() && !"none".equals(nextBlocker)) {
                blocker = nextBlocker;
            }
            return new OptimizerRuleEvidence(
                    id,
                    firstKnown(version, fields.getOrDefault(prefix + ".version", "unknown")),
                    firstKnown(extensionId, fields.getOrDefault(prefix + ".extensionId", "unknown")),
                    firstKnown(extensionVersion, fields.getOrDefault(prefix + ".extensionVersion", "unknown")),
                    firstKnown(proofStatus, fields.getOrDefault(prefix + ".proofStatus", "unknown")),
                    candidateCount + parseInt(fields.get(prefix + ".candidate.count")),
                    proposalCount + parseInt(fields.get(prefix + ".proposal.count")),
                    appliedCount + parseInt(fields.get(prefix + ".applied.count")),
                    skippedCount + parseInt(fields.get(prefix + ".skipped.count")),
                    blockedCount + parseInt(fields.get(prefix + ".blocked.count")),
                    mutationProposed || Boolean.parseBoolean(fields.getOrDefault(prefix + ".mutationProposed", "false")),
                    replacementPlanCount + parseInt(fields.get(prefix + ".replacementPlan.count")),
                    replacementPlanCompleteCount + parseInt(fields.get(prefix + ".replacementPlan.complete.count")),
                    replacementPlanPartialCount + parseInt(fields.get(prefix + ".replacementPlan.partial.count")),
                    replacementPlanValidationCount + parseInt(fields.get(prefix + ".replacementPlan.validation.count")),
                    replacementPlanValidationValidCount + parseInt(fields.get(prefix + ".replacementPlan.validation.valid.count")),
                    replacementPlanValidationInvalidCount + parseInt(fields.get(prefix + ".replacementPlan.validation.invalid.count")),
                    validationBlocker,
                    rewriteSketchCount + parseInt(fields.get(prefix + ".rewriteSketch.count")),
                    rewriteSketchReadyCount + parseInt(fields.get(prefix + ".rewriteSketch.ready.count")),
                    rewriteSketchBlockedCount + parseInt(fields.get(prefix + ".rewriteSketch.blocked.count")),
                    sketchBlocker,
                    selectionStatus,
                    selectionBlocker,
                    proofStatus,
                    proofBlocker,
                    reviewPackageStatus,
                    reviewPackageBlocker,
                    blocker
            );
        }

        private static String firstNonDefault(String current, String next, String defaultValue) {
            if (current != null && !current.isBlank() && !defaultValue.equals(current) && !"unknown".equals(current)) {
                return current;
            }
            if (next != null && !next.isBlank() && !defaultValue.equals(next) && !"unknown".equals(next)) {
                return next;
            }
            return defaultValue;
        }

        private static String firstKnown(String current, String next) {
            if (current != null && !current.isBlank() && !"unknown".equals(current)) {
                return current;
            }
            return next == null || next.isBlank() ? "unknown" : next;
        }
    }
}
