package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Aggregates optional ir-optimizer evidence artifacts into the OpenCL validation report.
 */
record OpenClRuntimeIrOptimizerEvidenceSummary(String status, List<Entry> entries, String diagnostic) {

    private static final String ARTIFACT_FILE_NAME =
            GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT;

    OpenClRuntimeIrOptimizerEvidenceSummary {
        status = status == null || status.isBlank() ? "unknown" : status;
        entries = entries == null ? List.of() : List.copyOf(entries);
        diagnostic = diagnostic == null ? "" : diagnostic;
    }

    static OpenClRuntimeIrOptimizerEvidenceSummary notRecorded() {
        return new OpenClRuntimeIrOptimizerEvidenceSummary("not-recorded", List.of(), "");
    }

    static OpenClRuntimeIrOptimizerEvidenceSummary failed(Throwable failure) {
        return new OpenClRuntimeIrOptimizerEvidenceSummary(
                "failed",
                List.of(),
                failure == null ? "unknown failure" : failure.toString()
        );
    }

    static OpenClRuntimeIrOptimizerEvidenceSummary read(Path workloadGateFile) throws IOException {
        if (workloadGateFile == null || !Files.isRegularFile(workloadGateFile)) {
            return notRecorded();
        }
        Properties gate = loadProperties(workloadGateFile);
        int kernelCount = parseInt(gate.getProperty("kernel.count"), 0);
        if (kernelCount <= 0) {
            return notRecorded();
        }

        Path reportDirectory = workloadGateFile.getParent();
        Path artifactRoot = reportDirectory == null
                ? null
                : reportDirectory.resolve("runtime-compile-artifacts");
        Map<String, Properties> evidenceByResource = loadEvidenceArtifacts(artifactRoot);
        ArrayList<Entry> entries = new ArrayList<>();
        for (int index = 0; index < kernelCount; index++) {
            String resource = gate.getProperty("kernel." + index + ".sourceKernelResource", "unknown");
            entries.add(Entry.from(resource, evidenceByResource.get(resource)));
        }
        return new OpenClRuntimeIrOptimizerEvidenceSummary("recorded", entries, "");
    }

    int count(String expectedStatus) {
        int count = 0;
        for (Entry entry : entries) {
            if (expectedStatus.equals(entry.status())) {
                count++;
            }
        }
        return count;
    }

    int totalPassCount() {
        return entries.stream().mapToInt(Entry::passCount).sum();
    }

    int totalProposalOnlyCount() {
        return entries.stream().mapToInt(Entry::proposalOnlyCount).sum();
    }

    int totalSelectedOptimizedCount() {
        return entries.stream().mapToInt(Entry::selectedOptimizedCount).sum();
    }

    int totalRolledBackCount() {
        return entries.stream().mapToInt(Entry::rolledBackCount).sum();
    }

    int totalApprovalTemplatePendingCount() {
        return entries.stream().mapToInt(Entry::approvalTemplatePendingCount).sum();
    }

    int totalApprovalTemplateNotApplicableCount() {
        return entries.stream().mapToInt(Entry::approvalTemplateNotApplicableCount).sum();
    }

    int totalApprovalTemplateRuntimeEquivalencePayloadRequiredCount() {
        return entries.stream()
                .mapToInt(Entry::approvalTemplateRuntimeEquivalencePayloadRequiredCount)
                .sum();
    }

    int totalApprovalTemplateRuntimeEquivalencePayloadPresentCount() {
        return entries.stream()
                .mapToInt(Entry::approvalTemplateRuntimeEquivalencePayloadPresentCount)
                .sum();
    }

    int totalApprovalTemplateRuntimeEquivalencePayloadPassedCount() {
        return entries.stream()
                .mapToInt(Entry::approvalTemplateRuntimeEquivalencePayloadPassedCount)
                .sum();
    }

    int totalApprovalTemplateRuntimeEquivalencePayloadCompleteCount() {
        return entries.stream()
                .mapToInt(Entry::approvalTemplateRuntimeEquivalencePayloadCompleteCount)
                .sum();
    }

    int totalPolicyGateSkippedCount() {
        return entries.stream().mapToInt(Entry::policyGateSkippedCount).sum();
    }

    int totalPolicyGateOptimizerPolicyDisabledCount() {
        return entries.stream().mapToInt(Entry::policyGateOptimizerPolicyDisabledCount).sum();
    }

    int totalPolicyGateFamilyDisabledCount() {
        return entries.stream().mapToInt(Entry::policyGateFamilyDisabledCount).sum();
    }

    int totalPolicyGateFamilyNotEnabledCount() {
        return entries.stream().mapToInt(Entry::policyGateFamilyNotEnabledCount).sum();
    }

    int totalPolicyGateProviderInvokedCount() {
        return entries.stream().mapToInt(Entry::policyGateProviderInvokedCount).sum();
    }

    String policyGateFirstBlocker() {
        for (Entry entry : entries) {
            if (entry.policyGateSkippedCount() > 0 && !"none".equals(entry.policyGateFirstBlocker())) {
                return entry.policyGateFirstBlocker();
            }
        }
        return totalPolicyGateSkippedCount() > 0 ? "unknown" : "none";
    }

    String policyGateFamilySummary() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (Entry entry : entries) {
            parseCountSummary(entry.policyGateFamilySummary()).forEach((family, count) ->
                    counts.merge(family, count, Integer::sum));
        }
        return formatProviderCounts(counts);
    }

    int totalOptimizedArtifactCandidateCount() {
        return entries.stream().mapToInt(Entry::optimizedArtifactCandidateCount).sum();
    }

    int totalOptimizedArtifactCandidateReadyCount() {
        return entries.stream().mapToInt(Entry::optimizedArtifactCandidateReadyCount).sum();
    }

    int totalOptimizedArtifactCandidateBlockedCount() {
        return entries.stream().mapToInt(Entry::optimizedArtifactCandidateBlockedCount).sum();
    }

    int totalOptimizedArtifactCandidateSelectionReadyCount() {
        return entries.stream().mapToInt(Entry::optimizedArtifactCandidateSelectionReadyCount).sum();
    }

    int totalOptimizedArtifactCandidateSelectionAppliedCount() {
        return entries.stream().mapToInt(Entry::optimizedArtifactCandidateSelectionAppliedCount).sum();
    }

    int totalOptimizedArtifactCandidateSelectedIrReplacementCount() {
        return entries.stream().mapToInt(Entry::optimizedArtifactCandidateSelectedIrReplacementCount).sum();
    }

    int totalOptimizedArtifactCandidateMutationAllowedCount() {
        return entries.stream().mapToInt(Entry::optimizedArtifactCandidateMutationAllowedCount).sum();
    }

    String optimizedArtifactCandidateStatus() {
        int count = totalOptimizedArtifactCandidateCount();
        if (count <= 0) {
            return "not-recorded";
        }
        int readyCount = totalOptimizedArtifactCandidateReadyCount();
        int blockedCount = totalOptimizedArtifactCandidateBlockedCount();
        if (readyCount > 0 && blockedCount > 0) {
            return "mixed";
        }
        return blockedCount > 0 ? "blocked" : "candidate-ready";
    }

    String optimizedArtifactCandidateFirstBlocker() {
        for (Entry entry : entries) {
            if (entry.optimizedArtifactCandidateCount() > 0
                    && !"none".equals(entry.optimizedArtifactCandidateFirstBlocker())) {
                return entry.optimizedArtifactCandidateFirstBlocker();
            }
        }
        return totalOptimizedArtifactCandidateCount() > 0 ? "none" : "no-candidates";
    }

    String optimizedArtifactCandidateSelectionFirstBlocker() {
        for (Entry entry : entries) {
            if (entry.optimizedArtifactCandidateCount() > 0
                    && !"none".equals(entry.optimizedArtifactCandidateSelectionFirstBlocker())) {
                return entry.optimizedArtifactCandidateSelectionFirstBlocker();
            }
        }
        return totalOptimizedArtifactCandidateCount() > 0 ? "selection-gate-not-bound" : "no-candidates";
    }

    int totalBackendNeutralSourceMaterializationPassCount() {
        return entries.stream().mapToInt(Entry::backendNeutralSourceMaterializationPassCount).sum();
    }

    int totalBackendNeutralSourceMaterializationCandidateCount() {
        return entries.stream().mapToInt(Entry::backendNeutralSourceMaterializationCandidateCount).sum();
    }

    int totalBackendNeutralSourceMaterializationSourceReadyCount() {
        return entries.stream().mapToInt(Entry::backendNeutralSourceMaterializationSourceReadyCount).sum();
    }

    int totalBackendNeutralSourceMaterializationSourceLength() {
        return entries.stream().mapToInt(Entry::backendNeutralSourceMaterializationSourceLengthTotal).sum();
    }

    String backendNeutralSourceMaterializationStatus() {
        if (totalBackendNeutralSourceMaterializationPassCount() <= 0) {
            return "not-recorded";
        }
        if (totalBackendNeutralSourceMaterializationCandidateCount() <= 0) {
            return "no-candidates";
        }
        if (totalBackendNeutralSourceMaterializationSourceReadyCount()
                >= totalBackendNeutralSourceMaterializationCandidateCount()) {
            return "review-ready";
        }
        return totalBackendNeutralSourceMaterializationSourceReadyCount() > 0 ? "mixed" : "blocked";
    }

    String backendNeutralSourceMaterializationFirstBlocker() {
        for (Entry entry : entries) {
            if (entry.backendNeutralSourceMaterializationCandidateCount() > 0
                    && !"none".equals(entry.backendNeutralSourceMaterializationFirstBlocker())) {
                return entry.backendNeutralSourceMaterializationFirstBlocker();
            }
        }
        return switch (backendNeutralSourceMaterializationStatus()) {
            case "not-recorded" -> "not-recorded";
            case "no-candidates" -> "backend-neutral-source-materialization-not-needed";
            case "mixed" -> "backend-neutral-source-partially-materialized";
            case "blocked" -> "backend-neutral-source-not-materialized";
            default -> "none";
        };
    }

    int totalConstantFoldingPreviewPassCount() {
        return entries.stream().mapToInt(Entry::constantFoldingPreviewPassCount).sum();
    }

    int totalConstantFoldingPreviewCandidateCount() {
        return entries.stream().mapToInt(Entry::constantFoldingPreviewCandidateCount).sum();
    }

    int totalConstantFoldingPreviewSkippedCount() {
        return entries.stream().mapToInt(Entry::constantFoldingPreviewSkippedCount).sum();
    }

    int totalConstantFoldingMaterializationPassCount() {
        return entries.stream().mapToInt(Entry::constantFoldingMaterializationPassCount).sum();
    }

    int totalConstantFoldingMaterializationTransformedNodeCount() {
        return entries.stream().mapToInt(Entry::constantFoldingMaterializationTransformedNodeCount).sum();
    }

    int totalConstantFoldingMaterializationLiteralRewriteCount() {
        return entries.stream().mapToInt(Entry::constantFoldingMaterializationLiteralRewriteCount).sum();
    }

    int totalConstantFoldingMaterializationIdentityRewriteCount() {
        return entries.stream().mapToInt(Entry::constantFoldingMaterializationIdentityRewriteCount).sum();
    }

    int totalConstantFoldingMaterializationFixedPointPassCount() {
        return entries.stream().mapToInt(Entry::constantFoldingMaterializationFixedPointPassCount).sum();
    }

    int totalConstantFoldingMaterializationSkippedCount() {
        return entries.stream().mapToInt(Entry::constantFoldingMaterializationSkippedCount).sum();
    }

    int totalConstantFoldingMaterializationRuntimeEquivalencePayloadPresentCount() {
        return entries.stream()
                .mapToInt(Entry::constantFoldingMaterializationRuntimeEquivalencePayloadPresentCount)
                .sum();
    }

    int totalConstantFoldingMaterializationRuntimeEquivalencePassedCount() {
        return entries.stream()
                .mapToInt(Entry::constantFoldingMaterializationRuntimeEquivalencePassedCount)
                .sum();
    }

    String constantFoldingMaterializationStatus() {
        if (totalConstantFoldingMaterializationPassCount() <= 0) {
            return "not-recorded";
        }
        if (totalConstantFoldingMaterializationTransformedNodeCount() <= 0) {
            return "no-candidates";
        }
        if (totalConstantFoldingMaterializationRuntimeEquivalencePayloadPresentCount() <= 0) {
            return "pending-runtime-equivalence";
        }
        return totalConstantFoldingMaterializationRuntimeEquivalencePassedCount()
                < totalConstantFoldingMaterializationRuntimeEquivalencePayloadPresentCount()
                ? "runtime-equivalence-not-passed"
                : "review-ready";
    }

    String constantFoldingMaterializationFirstBlocker() {
        for (Entry entry : entries) {
            if (entry.constantFoldingMaterializationTransformedNodeCount() > 0
                    && !"none".equals(entry.constantFoldingMaterializationFirstBlocker())) {
                return entry.constantFoldingMaterializationFirstBlocker();
            }
        }
        return switch (constantFoldingMaterializationStatus()) {
            case "not-recorded" -> "not-recorded";
            case "no-candidates" -> "no-materialized-candidates";
            case "pending-runtime-equivalence" -> "runtime-equivalence-payload-not-recorded";
            case "runtime-equivalence-not-passed" -> "runtime-equivalence-not-passed";
            default -> "none";
        };
    }

    int totalSafeLocalCseMaterializationPassCount() {
        return entries.stream().mapToInt(Entry::safeLocalCseMaterializationPassCount).sum();
    }

    int totalSafeLocalCseMaterializationLocalBindingCount() {
        return entries.stream().mapToInt(Entry::safeLocalCseMaterializationLocalBindingCount).sum();
    }

    int totalSafeLocalCseMaterializationTransformedNodeCount() {
        return entries.stream().mapToInt(Entry::safeLocalCseMaterializationTransformedNodeCount).sum();
    }

    int totalSafeLocalCseMaterializationBodyTextReplacementCount() {
        return entries.stream().mapToInt(Entry::safeLocalCseMaterializationBodyTextReplacementCount).sum();
    }

    int totalSafeLocalCseMaterializationRuntimeEquivalencePayloadPresentCount() {
        return entries.stream()
                .mapToInt(Entry::safeLocalCseMaterializationRuntimeEquivalencePayloadPresentCount)
                .sum();
    }

    int totalSafeLocalCseMaterializationRuntimeEquivalencePassedCount() {
        return entries.stream()
                .mapToInt(Entry::safeLocalCseMaterializationRuntimeEquivalencePassedCount)
                .sum();
    }

    String safeLocalCseMaterializationStatus() {
        if (totalSafeLocalCseMaterializationPassCount() <= 0) {
            return "not-recorded";
        }
        if (totalSafeLocalCseMaterializationTransformedNodeCount() <= 0) {
            return "no-candidates";
        }
        if (totalSafeLocalCseMaterializationRuntimeEquivalencePayloadPresentCount() <= 0) {
            return "pending-runtime-equivalence";
        }
        return totalSafeLocalCseMaterializationRuntimeEquivalencePassedCount()
                < totalSafeLocalCseMaterializationRuntimeEquivalencePayloadPresentCount()
                ? "runtime-equivalence-not-passed"
                : "review-ready";
    }

    String safeLocalCseMaterializationFirstBlocker() {
        for (Entry entry : entries) {
            if (entry.safeLocalCseMaterializationTransformedNodeCount() > 0
                    && !"none".equals(entry.safeLocalCseMaterializationFirstBlocker())) {
                return entry.safeLocalCseMaterializationFirstBlocker();
            }
        }
        return switch (safeLocalCseMaterializationStatus()) {
            case "not-recorded" -> "not-recorded";
            case "no-candidates" -> "no-materialized-candidates";
            case "pending-runtime-equivalence" -> "runtime-equivalence-payload-not-recorded";
            case "runtime-equivalence-not-passed" -> "runtime-equivalence-not-passed";
            default -> "none";
        };
    }

    int totalMadFmaMaterializationPassCount() {
        return entries.stream().mapToInt(Entry::madFmaMaterializationPassCount).sum();
    }

    int totalMadFmaMaterializationCandidateCount() {
        return entries.stream().mapToInt(Entry::madFmaMaterializationCandidateCount).sum();
    }

    int totalMadFmaMaterializationTransformedNodeCount() {
        return entries.stream().mapToInt(Entry::madFmaMaterializationTransformedNodeCount).sum();
    }

    int totalMadFmaMaterializationBodyTextReplacementCount() {
        return entries.stream().mapToInt(Entry::madFmaMaterializationBodyTextReplacementCount).sum();
    }

    int totalMadFmaMaterializationFixedPointPassCount() {
        return entries.stream().mapToInt(Entry::madFmaMaterializationFixedPointPassCount).sum();
    }

    int totalMadFmaMaterializationSkippedCount() {
        return entries.stream().mapToInt(Entry::madFmaMaterializationSkippedCount).sum();
    }

    int totalMadFmaMaterializationRuntimeEquivalencePayloadPresentCount() {
        return entries.stream()
                .mapToInt(Entry::madFmaMaterializationRuntimeEquivalencePayloadPresentCount)
                .sum();
    }

    int totalMadFmaMaterializationRuntimeEquivalencePassedCount() {
        return entries.stream()
                .mapToInt(Entry::madFmaMaterializationRuntimeEquivalencePassedCount)
                .sum();
    }

    boolean madFmaMaterializationFastMathAllowed() {
        return entries.stream().anyMatch(Entry::madFmaMaterializationFastMathAllowed);
    }

    String madFmaMaterializationStatus() {
        if (totalMadFmaMaterializationPassCount() <= 0) {
            return "not-recorded";
        }
        if (entries.stream().anyMatch(entry -> "blocked".equals(entry.madFmaMaterializationStatus()))) {
            return "blocked";
        }
        if (totalMadFmaMaterializationTransformedNodeCount() <= 0
                && totalMadFmaMaterializationSkippedCount() > 0) {
            return "blocked";
        }
        if (totalMadFmaMaterializationTransformedNodeCount() <= 0) {
            return "no-candidates";
        }
        if (!madFmaMaterializationFastMathAllowed()) {
            return "blocked";
        }
        if (totalMadFmaMaterializationRuntimeEquivalencePayloadPresentCount() <= 0) {
            return "pending-runtime-equivalence";
        }
        return totalMadFmaMaterializationRuntimeEquivalencePassedCount()
                < totalMadFmaMaterializationRuntimeEquivalencePayloadPresentCount()
                ? "runtime-equivalence-not-passed"
                : "review-ready";
    }

    String madFmaMaterializationFirstBlocker() {
        for (Entry entry : entries) {
            if ((entry.madFmaMaterializationTransformedNodeCount() > 0
                    || entry.madFmaMaterializationSkippedCount() > 0)
                    && !"none".equals(entry.madFmaMaterializationFirstBlocker())) {
                return entry.madFmaMaterializationFirstBlocker();
            }
        }
        return switch (madFmaMaterializationStatus()) {
            case "not-recorded" -> "not-recorded";
            case "no-candidates" -> "no-materialized-candidates";
            case "blocked" -> "mad-fma-materialization-blocked";
            case "pending-runtime-equivalence" -> "runtime-equivalence-payload-not-recorded";
            case "runtime-equivalence-not-passed" -> "runtime-equivalence-not-passed";
            default -> "none";
        };
    }

    int totalClampMaterializationPassCount() {
        return totalIntrinsicMaterializationInt("clampMaterialization", "pass.count");
    }

    int totalClampMaterializationCandidateCount() {
        return totalIntrinsicMaterializationInt("clampMaterialization", "candidate.count");
    }

    int totalClampMaterializationTransformedNodeCount() {
        return totalIntrinsicMaterializationInt("clampMaterialization", "transformedNode.count");
    }

    int totalClampMaterializationBodyTextReplacementCount() {
        return totalIntrinsicMaterializationInt("clampMaterialization", "bodyTextReplacement.count");
    }

    int totalClampMaterializationRuntimeEquivalencePayloadPresentCount() {
        return totalIntrinsicMaterializationInt("clampMaterialization", "runtimeEquivalencePayloadPresent.count");
    }

    int totalClampMaterializationRuntimeEquivalencePassedCount() {
        return totalIntrinsicMaterializationInt("clampMaterialization", "runtimeEquivalencePassed.count");
    }

    String clampMaterializationStatus() {
        return intrinsicMaterializationStatus("clampMaterialization");
    }

    String clampMaterializationFirstBlocker() {
        return intrinsicMaterializationFirstBlocker("clampMaterialization", "clamp-materialization-blocked");
    }

    int totalStepMaterializationPassCount() {
        return totalIntrinsicMaterializationInt("stepMaterialization", "pass.count");
    }

    int totalStepMaterializationCandidateCount() {
        return totalIntrinsicMaterializationInt("stepMaterialization", "candidate.count");
    }

    int totalStepMaterializationTransformedNodeCount() {
        return totalIntrinsicMaterializationInt("stepMaterialization", "transformedNode.count");
    }

    int totalStepMaterializationBodyTextReplacementCount() {
        return totalIntrinsicMaterializationInt("stepMaterialization", "bodyTextReplacement.count");
    }

    int totalStepMaterializationDirectStepCount() {
        return totalIntrinsicMaterializationInt("stepMaterialization", "directStep.count");
    }

    int totalStepMaterializationInvertedStepCount() {
        return totalIntrinsicMaterializationInt("stepMaterialization", "invertedStep.count");
    }

    int totalStepMaterializationRuntimeEquivalencePayloadPresentCount() {
        return totalIntrinsicMaterializationInt("stepMaterialization", "runtimeEquivalencePayloadPresent.count");
    }

    int totalStepMaterializationRuntimeEquivalencePassedCount() {
        return totalIntrinsicMaterializationInt("stepMaterialization", "runtimeEquivalencePassed.count");
    }

    String stepMaterializationStatus() {
        return intrinsicMaterializationStatus("stepMaterialization");
    }

    String stepMaterializationFirstBlocker() {
        return intrinsicMaterializationFirstBlocker("stepMaterialization", "step-materialization-blocked");
    }

    int totalMixMaterializationPassCount() {
        return totalIntrinsicMaterializationInt("mixMaterialization", "pass.count");
    }

    int totalMixMaterializationCandidateCount() {
        return totalIntrinsicMaterializationInt("mixMaterialization", "candidate.count");
    }

    int totalMixMaterializationTransformedNodeCount() {
        return totalIntrinsicMaterializationInt("mixMaterialization", "transformedNode.count");
    }

    int totalMixMaterializationBodyTextReplacementCount() {
        return totalIntrinsicMaterializationInt("mixMaterialization", "bodyTextReplacement.count");
    }

    int totalMixMaterializationCanonicalMixCount() {
        return totalIntrinsicMaterializationInt("mixMaterialization", "canonicalMix.count");
    }

    int totalMixMaterializationExpandedMixCount() {
        return totalIntrinsicMaterializationInt("mixMaterialization", "expandedMix.count");
    }

    int totalMixMaterializationMadExpandedMixCount() {
        return totalIntrinsicMaterializationInt("mixMaterialization", "madExpandedMix.count");
    }

    int totalMixMaterializationRuntimeEquivalencePayloadPresentCount() {
        return totalIntrinsicMaterializationInt("mixMaterialization", "runtimeEquivalencePayloadPresent.count");
    }

    int totalMixMaterializationRuntimeEquivalencePassedCount() {
        return totalIntrinsicMaterializationInt("mixMaterialization", "runtimeEquivalencePassed.count");
    }

    boolean mixMaterializationFastMathAllowed() {
        return anyIntrinsicMaterializationBoolean("mixMaterialization", "fastMathAllowed");
    }

    boolean mixMaterializationFastMathRequired() {
        return anyIntrinsicMaterializationBoolean("mixMaterialization", "fastMathRequired");
    }

    boolean mixMaterializationAlgebraicReassociationRequired() {
        return anyIntrinsicMaterializationBoolean("mixMaterialization", "algebraicReassociationRequired");
    }

    String mixMaterializationStatus() {
        return intrinsicMaterializationStatus("mixMaterialization");
    }

    String mixMaterializationFirstBlocker() {
        return intrinsicMaterializationFirstBlocker("mixMaterialization", "mix-materialization-blocked");
    }

    private int totalIntrinsicMaterializationInt(String prefix, String suffix) {
        return entries.stream()
                .mapToInt(entry -> entry.intrinsicMaterializationInt(prefix, suffix))
                .sum();
    }

    private boolean anyIntrinsicMaterializationBoolean(String prefix, String suffix) {
        return entries.stream().anyMatch(entry -> entry.intrinsicMaterializationBoolean(prefix, suffix));
    }

    private int totalIntrinsicMaterializationSkippedCount(String prefix) {
        return entries.stream()
                .mapToInt(entry -> entry.intrinsicMaterializationSkippedCount(prefix))
                .sum();
    }

    private String intrinsicMaterializationStatus(String prefix) {
        if (totalIntrinsicMaterializationInt(prefix, "pass.count") <= 0) {
            return "not-recorded";
        }
        if (entries.stream().anyMatch(entry -> "blocked".equals(entry.intrinsicMaterializationStatus(prefix)))) {
            return "blocked";
        }
        if (totalIntrinsicMaterializationInt(prefix, "transformedNode.count") <= 0
                && totalIntrinsicMaterializationSkippedCount(prefix) > 0) {
            return "blocked";
        }
        if (totalIntrinsicMaterializationInt(prefix, "transformedNode.count") <= 0) {
            return "no-candidates";
        }
        if (totalIntrinsicMaterializationInt(prefix, "runtimeEquivalencePayloadPresent.count") <= 0) {
            return "pending-runtime-equivalence";
        }
        return totalIntrinsicMaterializationInt(prefix, "runtimeEquivalencePassed.count")
                < totalIntrinsicMaterializationInt(prefix, "runtimeEquivalencePayloadPresent.count")
                ? "runtime-equivalence-not-passed"
                : "review-ready";
    }

    private String intrinsicMaterializationFirstBlocker(String prefix, String defaultBlockedFirstBlocker) {
        for (Entry entry : entries) {
            if ((entry.intrinsicMaterializationInt(prefix, "transformedNode.count") > 0
                    || entry.intrinsicMaterializationSkippedCount(prefix) > 0)
                    && !"none".equals(entry.intrinsicMaterializationFirstBlocker(prefix))) {
                return entry.intrinsicMaterializationFirstBlocker(prefix);
            }
        }
        return switch (intrinsicMaterializationStatus(prefix)) {
            case "not-recorded" -> "not-recorded";
            case "no-candidates" -> "no-materialized-candidates";
            case "blocked" -> defaultBlockedFirstBlocker;
            case "pending-runtime-equivalence" -> "runtime-equivalence-payload-not-recorded";
            case "runtime-equivalence-not-passed" -> "runtime-equivalence-not-passed";
            default -> "none";
        };
    }

    int totalLoopVectorizationMaterializationPassCount() {
        return entries.stream().mapToInt(Entry::loopVectorizationMaterializationPassCount).sum();
    }

    int totalLoopVectorizationMaterializationCandidateCount() {
        return entries.stream().mapToInt(Entry::loopVectorizationMaterializationCandidateCount).sum();
    }

    int totalLoopVectorizationMaterializationTransformedLoopCount() {
        return entries.stream().mapToInt(Entry::loopVectorizationMaterializationTransformedLoopCount).sum();
    }

    int totalLoopVectorizationMaterializationBodyTextReplacementCount() {
        return entries.stream().mapToInt(Entry::loopVectorizationMaterializationBodyTextReplacementCount).sum();
    }

    int totalLoopVectorizationMaterializationTypedBodyMaterializedCount() {
        return entries.stream().mapToInt(Entry::loopVectorizationMaterializationTypedBodyMaterializedCount).sum();
    }

    int totalLoopVectorizationMaterializationTypedBodyInvalidatedCount() {
        return entries.stream().mapToInt(Entry::loopVectorizationMaterializationTypedBodyInvalidatedCount).sum();
    }

    int totalLoopVectorizationMaterializationSkippedCount() {
        return entries.stream().mapToInt(Entry::loopVectorizationMaterializationSkippedCount).sum();
    }

    int totalLoopVectorizationMaterializationRuntimeEquivalencePayloadPresentCount() {
        return entries.stream()
                .mapToInt(Entry::loopVectorizationMaterializationRuntimeEquivalencePayloadPresentCount)
                .sum();
    }

    int totalLoopVectorizationMaterializationRuntimeEquivalencePassedCount() {
        return entries.stream()
                .mapToInt(Entry::loopVectorizationMaterializationRuntimeEquivalencePassedCount)
                .sum();
    }

    String loopVectorizationMaterializationStatus() {
        if (totalLoopVectorizationMaterializationPassCount() <= 0) {
            return "not-recorded";
        }
        if (entries.stream().anyMatch(entry -> "blocked".equals(entry.loopVectorizationMaterializationStatus()))) {
            return "blocked";
        }
        if (totalLoopVectorizationMaterializationTransformedLoopCount() <= 0
                && totalLoopVectorizationMaterializationSkippedCount() > 0) {
            return "blocked";
        }
        if (totalLoopVectorizationMaterializationTransformedLoopCount() <= 0) {
            return "no-candidates";
        }
        if (totalLoopVectorizationMaterializationRuntimeEquivalencePayloadPresentCount() <= 0) {
            return "pending-runtime-equivalence";
        }
        return totalLoopVectorizationMaterializationRuntimeEquivalencePassedCount()
                < totalLoopVectorizationMaterializationRuntimeEquivalencePayloadPresentCount()
                ? "runtime-equivalence-not-passed"
                : "review-ready";
    }

    String loopVectorizationMaterializationFirstBlocker() {
        for (Entry entry : entries) {
            if ((entry.loopVectorizationMaterializationTransformedLoopCount() > 0
                    || entry.loopVectorizationMaterializationSkippedCount() > 0)
                    && !"none".equals(entry.loopVectorizationMaterializationFirstBlocker())) {
                return entry.loopVectorizationMaterializationFirstBlocker();
            }
        }
        return switch (loopVectorizationMaterializationStatus()) {
            case "not-recorded" -> "not-recorded";
            case "no-candidates" -> "no-materialized-candidates";
            case "blocked" -> "loop-vectorization-materialization-blocked";
            case "pending-runtime-equivalence" -> "runtime-equivalence-payload-not-recorded";
            case "runtime-equivalence-not-passed" -> "runtime-equivalence-not-passed";
            default -> "none";
        };
    }

    int totalTypedDeadCodeMaterializationPassCount() {
        return entries.stream().mapToInt(Entry::typedDeadCodeMaterializationPassCount).sum();
    }

    int totalTypedDeadCodeMaterializationNodeCount() {
        return entries.stream().mapToInt(Entry::typedDeadCodeMaterializationNodeCount).sum();
    }

    int totalTypedDeadCodeMaterializationUnreachableNodeCount() {
        return entries.stream().mapToInt(Entry::typedDeadCodeMaterializationUnreachableNodeCount).sum();
    }

    int totalTypedDeadCodeMaterializationRemovedNodeCount() {
        return entries.stream().mapToInt(Entry::typedDeadCodeMaterializationRemovedNodeCount).sum();
    }

    int totalTypedDeadCodeMaterializationBlockedCount() {
        return entries.stream().mapToInt(Entry::typedDeadCodeMaterializationBlockedCount).sum();
    }

    int totalTypedDeadCodeMaterializationRuntimeEquivalencePayloadPresentCount() {
        return entries.stream()
                .mapToInt(Entry::typedDeadCodeMaterializationRuntimeEquivalencePayloadPresentCount)
                .sum();
    }

    int totalTypedDeadCodeMaterializationRuntimeEquivalencePassedCount() {
        return entries.stream()
                .mapToInt(Entry::typedDeadCodeMaterializationRuntimeEquivalencePassedCount)
                .sum();
    }

    String typedDeadCodeMaterializationStatus() {
        if (totalTypedDeadCodeMaterializationPassCount() <= 0) {
            return "not-recorded";
        }
        if (totalTypedDeadCodeMaterializationRemovedNodeCount() <= 0
                && totalTypedDeadCodeMaterializationBlockedCount() > 0) {
            return "blocked";
        }
        if (totalTypedDeadCodeMaterializationRemovedNodeCount() <= 0) {
            return "no-candidates";
        }
        if (totalTypedDeadCodeMaterializationRuntimeEquivalencePayloadPresentCount() <= 0) {
            return "pending-runtime-equivalence";
        }
        return totalTypedDeadCodeMaterializationRuntimeEquivalencePassedCount()
                < totalTypedDeadCodeMaterializationRuntimeEquivalencePayloadPresentCount()
                ? "runtime-equivalence-not-passed"
                : "review-ready";
    }

    String typedDeadCodeMaterializationFirstBlocker() {
        for (Entry entry : entries) {
            if ((entry.typedDeadCodeMaterializationRemovedNodeCount() > 0
                    || entry.typedDeadCodeMaterializationBlockedCount() > 0)
                    && !"none".equals(entry.typedDeadCodeMaterializationFirstBlocker())) {
                return entry.typedDeadCodeMaterializationFirstBlocker();
            }
        }
        return switch (typedDeadCodeMaterializationStatus()) {
            case "not-recorded" -> "not-recorded";
            case "no-candidates" -> "no-materialized-candidates";
            case "blocked" -> "typed-dead-code-materialization-blocked";
            case "pending-runtime-equivalence" -> "runtime-equivalence-payload-not-recorded";
            case "runtime-equivalence-not-passed" -> "runtime-equivalence-not-passed";
            default -> "none";
        };
    }

    int totalSafeLocalCsePreviewPassCount() {
        return entries.stream().mapToInt(Entry::safeLocalCsePreviewPassCount).sum();
    }

    int totalSafeLocalCsePreviewCandidateExpressionCount() {
        return entries.stream().mapToInt(Entry::safeLocalCsePreviewCandidateExpressionCount).sum();
    }

    int totalSafeLocalCsePreviewDuplicateExpressionCount() {
        return entries.stream().mapToInt(Entry::safeLocalCsePreviewDuplicateExpressionCount).sum();
    }

    int totalSafeLocalCsePreviewBlockedCount() {
        return entries.stream().mapToInt(Entry::safeLocalCsePreviewBlockedCount).sum();
    }

    int totalTypedDeadCodePreviewPassCount() {
        return entries.stream().mapToInt(Entry::typedDeadCodePreviewPassCount).sum();
    }

    int totalTypedDeadCodePreviewUnreachableNodeCount() {
        return entries.stream().mapToInt(Entry::typedDeadCodePreviewUnreachableNodeCount).sum();
    }

    int totalTypedDeadCodePreviewBlockedCount() {
        return entries.stream().mapToInt(Entry::typedDeadCodePreviewBlockedCount).sum();
    }

    int totalPreviewReadinessFamilyCount() {
        return (int) previewReadinessFamilies().stream()
                .filter(family -> !"not-recorded".equals(family.status()))
                .count();
    }

    int totalPreviewReadinessCandidateFamilyCount() {
        return (int) previewReadinessFamilies().stream()
                .filter(family -> family.candidateCount() > 0)
                .count();
    }

    int totalPreviewReadinessBlockedFamilyCount() {
        return (int) previewReadinessFamilies().stream()
                .filter(family -> "blocked-by-proof".equals(family.status()))
                .count();
    }

    String previewReadinessStatus() {
        List<PreviewFamilyReadiness> families = previewReadinessFamilies();
        if (families.stream().allMatch(family -> "not-recorded".equals(family.status()))) {
            return "not-recorded";
        }
        if (families.stream().anyMatch(family -> "blocked-by-proof".equals(family.status()))) {
            return "blocked-by-proof";
        }
        if (families.stream().anyMatch(family -> "ready-for-runtime-equivalence-review".equals(family.status()))) {
            return "ready-for-runtime-equivalence-review";
        }
        if (families.stream().anyMatch(family -> "candidates-recorded".equals(family.status()))) {
            return "candidates-recorded";
        }
        return "no-candidates";
    }

    String previewReadinessFamilySummary() {
        StringBuilder summary = new StringBuilder();
        for (PreviewFamilyReadiness family : previewReadinessFamilies()) {
            if (!summary.isEmpty()) {
                summary.append(", ");
            }
            summary.append(family.family()).append('=').append(family.status());
        }
        return summary.toString();
    }

    String runtimeEquivalenceReviewStatus() {
        return runtimeEquivalenceReviewEligible() ? "review-ready" : "blocked";
    }

    boolean runtimeEquivalenceReviewEligible() {
        boolean materializationReady = totalConstantFoldingMaterializationTransformedNodeCount() <= 0
                || "review-ready".equals(constantFoldingMaterializationStatus());
        boolean safeLocalCseMaterializationReady = totalSafeLocalCseMaterializationTransformedNodeCount() <= 0
                || "review-ready".equals(safeLocalCseMaterializationStatus());
        boolean madFmaMaterializationReady = totalMadFmaMaterializationTransformedNodeCount() <= 0
                || "review-ready".equals(madFmaMaterializationStatus());
        boolean clampMaterializationReady = totalClampMaterializationTransformedNodeCount() <= 0
                || "review-ready".equals(clampMaterializationStatus());
        boolean stepMaterializationReady = totalStepMaterializationTransformedNodeCount() <= 0
                || "review-ready".equals(stepMaterializationStatus());
        boolean mixMaterializationReady = totalMixMaterializationTransformedNodeCount() <= 0
                || "review-ready".equals(mixMaterializationStatus());
        boolean loopVectorizationMaterializationReady = totalLoopVectorizationMaterializationTransformedLoopCount() <= 0
                || "review-ready".equals(loopVectorizationMaterializationStatus());
        boolean typedMaterializationReady = totalTypedDeadCodeMaterializationRemovedNodeCount() <= 0
                || "review-ready".equals(typedDeadCodeMaterializationStatus());
        boolean previewReady = totalPreviewReadinessCandidateFamilyCount() <= 0
                || "ready-for-runtime-equivalence-review".equals(previewReadinessStatus());
        return runtimeEquivalenceReviewRequired()
                && materializationReady
                && safeLocalCseMaterializationReady
                && madFmaMaterializationReady
                && clampMaterializationReady
                && stepMaterializationReady
                && mixMaterializationReady
                && loopVectorizationMaterializationReady
                && typedMaterializationReady
                && previewReady;
    }

    boolean runtimeEquivalenceReviewRequired() {
        return totalPreviewReadinessCandidateFamilyCount() > 0
                || totalConstantFoldingMaterializationTransformedNodeCount() > 0
                || totalSafeLocalCseMaterializationTransformedNodeCount() > 0
                || totalMadFmaMaterializationTransformedNodeCount() > 0
                || totalClampMaterializationTransformedNodeCount() > 0
                || totalStepMaterializationTransformedNodeCount() > 0
                || totalMixMaterializationTransformedNodeCount() > 0
                || totalLoopVectorizationMaterializationTransformedLoopCount() > 0
                || totalTypedDeadCodeMaterializationRemovedNodeCount() > 0;
    }

    String runtimeEquivalenceReviewFirstBlocker() {
        if (totalConstantFoldingMaterializationTransformedNodeCount() > 0
                && !"review-ready".equals(constantFoldingMaterializationStatus())) {
            return constantFoldingMaterializationFirstBlocker();
        }
        if (totalSafeLocalCseMaterializationTransformedNodeCount() > 0
                && !"review-ready".equals(safeLocalCseMaterializationStatus())) {
            return safeLocalCseMaterializationFirstBlocker();
        }
        if (totalMadFmaMaterializationTransformedNodeCount() > 0
                && !"review-ready".equals(madFmaMaterializationStatus())) {
            return madFmaMaterializationFirstBlocker();
        }
        if (totalClampMaterializationTransformedNodeCount() > 0
                && !"review-ready".equals(clampMaterializationStatus())) {
            return clampMaterializationFirstBlocker();
        }
        if (totalStepMaterializationTransformedNodeCount() > 0
                && !"review-ready".equals(stepMaterializationStatus())) {
            return stepMaterializationFirstBlocker();
        }
        if (totalMixMaterializationTransformedNodeCount() > 0
                && !"review-ready".equals(mixMaterializationStatus())) {
            return mixMaterializationFirstBlocker();
        }
        if (totalLoopVectorizationMaterializationTransformedLoopCount() > 0
                && !"review-ready".equals(loopVectorizationMaterializationStatus())) {
            return loopVectorizationMaterializationFirstBlocker();
        }
        if (totalTypedDeadCodeMaterializationRemovedNodeCount() > 0
                && !"review-ready".equals(typedDeadCodeMaterializationStatus())) {
            return typedDeadCodeMaterializationFirstBlocker();
        }
        if (totalPreviewReadinessCandidateFamilyCount() <= 0
                && (totalConstantFoldingMaterializationTransformedNodeCount() > 0
                || totalSafeLocalCseMaterializationTransformedNodeCount() > 0
                || totalMadFmaMaterializationTransformedNodeCount() > 0
                || totalClampMaterializationTransformedNodeCount() > 0
                || totalStepMaterializationTransformedNodeCount() > 0
                || totalMixMaterializationTransformedNodeCount() > 0
                || totalLoopVectorizationMaterializationTransformedLoopCount() > 0
                || totalTypedDeadCodeMaterializationRemovedNodeCount() > 0)) {
            return "none";
        }
        return switch (previewReadinessStatus()) {
            case "ready-for-runtime-equivalence-review" -> "none";
            case "not-recorded" -> "preview-readiness-not-recorded";
            case "no-candidates" -> "preview-readiness-no-candidates";
            case "blocked-by-proof" -> "preview-readiness-blocked-by-proof";
            case "candidates-recorded" -> "preview-readiness-candidates-not-proof-clean";
            default -> "preview-readiness-unknown";
        };
    }

    int totalReviewPackageRequiredCount() {
        return (int) entries.stream().filter(Entry::reviewPackageRequired).count();
    }

    int totalReviewPackageCompleteCount() {
        return (int) entries.stream().filter(Entry::reviewPackageComplete).count();
    }

    int totalReviewPackageProposalPassCount() {
        return entries.stream().mapToInt(Entry::reviewPackageProposalPassCount).sum();
    }

    int totalReviewPackagePendingApprovalCount() {
        return entries.stream().mapToInt(Entry::reviewPackagePendingApprovalCount).sum();
    }

    int totalReviewPackageApprovalManifestRequiredCount() {
        return (int) entries.stream().filter(Entry::reviewPackageApprovalManifestRequired).count();
    }

    int totalReviewPackageApprovalManifestPresentCount() {
        return entries.stream().mapToInt(Entry::reviewPackageApprovalManifestPresentCount).sum();
    }

    int totalReviewPackageApprovalManifestAcceptedCount() {
        return entries.stream().mapToInt(Entry::reviewPackageApprovalManifestAcceptedCount).sum();
    }

    String reviewPackageApprovalManifestStatus() {
        if (totalReviewPackageApprovalManifestRequiredCount() <= 0) {
            return "not-required";
        }
        if (totalReviewPackageApprovalManifestAcceptedCount() >= totalReviewPackageApprovalManifestRequiredCount()) {
            return "accepted";
        }
        return entries.stream()
                .filter(Entry::reviewPackageApprovalManifestRequired)
                .map(Entry::reviewPackageApprovalManifestStatus)
                .filter(status -> !status.isBlank() && !"not-recorded".equals(status))
                .findFirst()
                .orElse("pending-manifest-validation");
    }

    String reviewPackageApprovalManifestFirstBlocker() {
        return entries.stream()
                .filter(Entry::reviewPackageApprovalManifestRequired)
                .filter(entry -> entry.reviewPackageApprovalManifestAcceptedCount() <= 0)
                .map(Entry::reviewPackageApprovalManifestFirstBlocker)
                .filter(blocker -> !blocker.isBlank() && !"none".equals(blocker))
                .findFirst()
                .orElse("none");
    }

    String reviewPackageStatus() {
        if (entries.isEmpty() || entries.stream().allMatch(entry -> "missing".equals(entry.status()))) {
            return "not-recorded";
        }
        if (totalReviewPackageRequiredCount() <= 0) {
            return "not-required";
        }
        return totalReviewPackageCompleteCount() == totalReviewPackageRequiredCount()
                ? "complete"
                : "pending-manual-review";
    }

    String reviewPackageFirstBlocker() {
        for (Entry entry : entries) {
            if (entry.reviewPackageRequired() && !entry.reviewPackageComplete()) {
                return entry.reviewPackageFirstBlocker();
            }
        }
        return "none";
    }

    private List<PreviewFamilyReadiness> previewReadinessFamilies() {
        return List.of(
                new PreviewFamilyReadiness(
                        "constant-folding",
                        previewFamilyStatus(
                                totalConstantFoldingPreviewPassCount(),
                                totalConstantFoldingPreviewCandidateCount(),
                                totalConstantFoldingPreviewSkippedCount() + totalConstantFoldingPreviewProofBlockerCount()
                        ),
                        totalConstantFoldingPreviewCandidateCount(),
                        totalConstantFoldingPreviewSkippedCount() + totalConstantFoldingPreviewProofBlockerCount()
                ),
                new PreviewFamilyReadiness(
                        "safe-local-cse",
                        previewFamilyStatus(
                                totalSafeLocalCsePreviewPassCount(),
                                totalSafeLocalCsePreviewDuplicateExpressionCount(),
                                totalSafeLocalCsePreviewBlockedCount() + totalSafeLocalCsePreviewProofBlockerCount()
                        ),
                        totalSafeLocalCsePreviewDuplicateExpressionCount(),
                        totalSafeLocalCsePreviewBlockedCount() + totalSafeLocalCsePreviewProofBlockerCount()
                ),
                new PreviewFamilyReadiness(
                        "typed-dead-code",
                        previewFamilyStatus(
                                totalTypedDeadCodePreviewPassCount(),
                                totalTypedDeadCodePreviewUnreachableNodeCount(),
                                totalTypedDeadCodePreviewBlockedCount() + totalTypedDeadCodePreviewProofBlockerCount()
                        ),
                        totalTypedDeadCodePreviewUnreachableNodeCount(),
                        totalTypedDeadCodePreviewBlockedCount() + totalTypedDeadCodePreviewProofBlockerCount()
                )
        );
    }

    private int totalConstantFoldingPreviewProofBlockerCount() {
        return entries.stream().mapToInt(Entry::constantFoldingPreviewProofBlockerCount).sum();
    }

    private int totalSafeLocalCsePreviewProofBlockerCount() {
        return entries.stream().mapToInt(Entry::safeLocalCsePreviewProofBlockerCount).sum();
    }

    private int totalTypedDeadCodePreviewProofBlockerCount() {
        return entries.stream().mapToInt(Entry::typedDeadCodePreviewProofBlockerCount).sum();
    }

    private static String previewFamilyStatus(int passCount, int candidateCount, int blockerCount) {
        if (passCount <= 0) {
            return "not-recorded";
        }
        if (candidateCount <= 0 && blockerCount <= 0) {
            return "no-candidates";
        }
        if (blockerCount > 0) {
            return "blocked-by-proof";
        }
        if (candidateCount > 0) {
            return "ready-for-runtime-equivalence-review";
        }
        return "candidates-recorded";
    }

    String providerSummary() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (Entry entry : entries) {
            for (Map.Entry<String, Integer> provider : entry.providerCounts().entrySet()) {
                counts.merge(provider.getKey(), provider.getValue(), Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return "none";
        }
        StringBuilder summary = new StringBuilder();
        counts.forEach((provider, count) -> {
            if (!summary.isEmpty()) {
                summary.append(", ");
            }
            summary.append(provider).append('=').append(count);
        });
        return summary.toString();
    }

    String toMarkdown() {
        StringBuilder markdown = new StringBuilder();
        markdown.append("## Runtime IR Optimizer Evidence\n\n");
        markdown.append("- Status: `").append(inline(status)).append("`\n");
        if (!diagnostic.isBlank()) {
            markdown.append("- Diagnostic: `").append(inline(diagnostic)).append("`\n\n");
            return markdown.toString();
        }
        markdown.append("- Kernel count: `").append(entries.size()).append("`\n");
        markdown.append("- Recorded kernels: `").append(count("recorded")).append("`\n");
        markdown.append("- Missing artifacts: `").append(count("missing")).append("`\n");
        markdown.append("- Proposal pass count: `").append(totalPassCount()).append("`\n");
        markdown.append("- Proposal-only count: `").append(totalProposalOnlyCount()).append("`\n");
        markdown.append("- Selected optimized count: `").append(totalSelectedOptimizedCount()).append("`\n");
        markdown.append("- Rolled back count: `").append(totalRolledBackCount()).append("`\n");
        markdown.append("- Approval templates pending: `").append(totalApprovalTemplatePendingCount()).append("`\n");
        markdown.append("- Approval templates not applicable: `").append(totalApprovalTemplateNotApplicableCount()).append("`\n");
        markdown.append("- Approval templates runtime-equivalence payload required: `")
                .append(totalApprovalTemplateRuntimeEquivalencePayloadRequiredCount()).append("`\n");
        markdown.append("- Approval templates runtime-equivalence payload present: `")
                .append(totalApprovalTemplateRuntimeEquivalencePayloadPresentCount()).append("`\n");
        markdown.append("- Approval templates runtime-equivalence payload passed: `")
                .append(totalApprovalTemplateRuntimeEquivalencePayloadPassedCount()).append("`\n");
        markdown.append("- Approval templates runtime-equivalence payload complete: `")
                .append(totalApprovalTemplateRuntimeEquivalencePayloadCompleteCount()).append("`\n");
        markdown.append("- Policy-gated optimizer skips: `")
                .append(totalPolicyGateSkippedCount()).append("`\n");
        markdown.append("- Policy-gated optimizer disabled skips: `")
                .append(totalPolicyGateOptimizerPolicyDisabledCount()).append("`\n");
        markdown.append("- Policy-gated family disabled skips: `")
                .append(totalPolicyGateFamilyDisabledCount()).append("`\n");
        markdown.append("- Policy-gated family not-enabled skips: `")
                .append(totalPolicyGateFamilyNotEnabledCount()).append("`\n");
        markdown.append("- Policy-gated provider invoked count: `")
                .append(totalPolicyGateProviderInvokedCount()).append("`\n");
        markdown.append("- Policy-gate first blocker: `")
                .append(inline(policyGateFirstBlocker())).append("`\n");
        markdown.append("- Policy-gate family summary: `")
                .append(inline(policyGateFamilySummary())).append("`\n");
        markdown.append("- Optimized artifact candidate status: `")
                .append(optimizedArtifactCandidateStatus()).append("`\n");
        markdown.append("- Optimized artifact candidates: `")
                .append(totalOptimizedArtifactCandidateCount()).append("`\n");
        markdown.append("- Optimized artifact candidates ready: `")
                .append(totalOptimizedArtifactCandidateReadyCount()).append("`\n");
        markdown.append("- Optimized artifact candidates blocked: `")
                .append(totalOptimizedArtifactCandidateBlockedCount()).append("`\n");
        markdown.append("- Optimized artifact candidate first blocker: `")
                .append(inline(optimizedArtifactCandidateFirstBlocker())).append("`\n");
        markdown.append("- Optimized artifact candidate selection first blocker: `")
                .append(inline(optimizedArtifactCandidateSelectionFirstBlocker())).append("`\n");
        markdown.append("- Optimized artifact candidate selection ready count: `")
                .append(totalOptimizedArtifactCandidateSelectionReadyCount()).append("`\n");
        markdown.append("- Optimized artifact candidate selection applied count: `")
                .append(totalOptimizedArtifactCandidateSelectionAppliedCount()).append("`\n");
        markdown.append("- Optimized artifact candidate selected IR replacement count: `")
                .append(totalOptimizedArtifactCandidateSelectedIrReplacementCount()).append("`\n");
        markdown.append("- Optimized artifact candidate mutation-allowed count: `")
                .append(totalOptimizedArtifactCandidateMutationAllowedCount()).append("`\n");
        markdown.append("- Optimized artifact candidate selection applied: `")
                .append(totalOptimizedArtifactCandidateSelectionAppliedCount() > 0).append("`\n");
        markdown.append("- Optimized artifact candidate selected IR replacement: `")
                .append(totalOptimizedArtifactCandidateSelectedIrReplacementCount() > 0).append("`\n");
        markdown.append("- Backend-neutral source materialization status: `")
                .append(backendNeutralSourceMaterializationStatus()).append("`\n");
        markdown.append("- Backend-neutral source materialization passes: `")
                .append(totalBackendNeutralSourceMaterializationPassCount()).append("`\n");
        markdown.append("- Backend-neutral source materialization candidates: `")
                .append(totalBackendNeutralSourceMaterializationCandidateCount()).append("`\n");
        markdown.append("- Backend-neutral source materialization source-ready count: `")
                .append(totalBackendNeutralSourceMaterializationSourceReadyCount()).append("`\n");
        markdown.append("- Backend-neutral source materialization source length total: `")
                .append(totalBackendNeutralSourceMaterializationSourceLength()).append("`\n");
        markdown.append("- Backend-neutral source materialization first blocker: `")
                .append(inline(backendNeutralSourceMaterializationFirstBlocker())).append("`\n");
        markdown.append("- Constant folding preview passes: `").append(totalConstantFoldingPreviewPassCount()).append("`\n");
        markdown.append("- Constant folding preview candidates: `").append(totalConstantFoldingPreviewCandidateCount()).append("`\n");
        markdown.append("- Constant folding preview skipped blockers: `").append(totalConstantFoldingPreviewSkippedCount()).append("`\n");
        markdown.append("- Constant folding materialization status: `")
                .append(constantFoldingMaterializationStatus()).append("`\n");
        markdown.append("- Constant folding materialization passes: `")
                .append(totalConstantFoldingMaterializationPassCount()).append("`\n");
        markdown.append("- Constant folding materialized nodes: `")
                .append(totalConstantFoldingMaterializationTransformedNodeCount()).append("`\n");
        markdown.append("- Constant folding materialization literal rewrites: `")
                .append(totalConstantFoldingMaterializationLiteralRewriteCount()).append("`\n");
        markdown.append("- Constant folding materialization identity rewrites: `")
                .append(totalConstantFoldingMaterializationIdentityRewriteCount()).append("`\n");
        markdown.append("- Constant folding materialization fixed-point passes: `")
                .append(totalConstantFoldingMaterializationFixedPointPassCount()).append("`\n");
        markdown.append("- Constant folding materialization skipped blockers: `")
                .append(totalConstantFoldingMaterializationSkippedCount()).append("`\n");
        markdown.append("- Constant folding materialization runtime-equivalence payloads: `")
                .append(totalConstantFoldingMaterializationRuntimeEquivalencePayloadPresentCount()).append("`\n");
        markdown.append("- Constant folding materialization runtime-equivalence passed: `")
                .append(totalConstantFoldingMaterializationRuntimeEquivalencePassedCount()).append("`\n");
        markdown.append("- Constant folding materialization first blocker: `")
                .append(inline(constantFoldingMaterializationFirstBlocker())).append("`\n");
        markdown.append("- Safe local CSE materialization status: `")
                .append(safeLocalCseMaterializationStatus()).append("`\n");
        markdown.append("- Safe local CSE materialization passes: `")
                .append(totalSafeLocalCseMaterializationPassCount()).append("`\n");
        markdown.append("- Safe local CSE materialization local bindings: `")
                .append(totalSafeLocalCseMaterializationLocalBindingCount()).append("`\n");
        markdown.append("- Safe local CSE materialized nodes: `")
                .append(totalSafeLocalCseMaterializationTransformedNodeCount()).append("`\n");
        markdown.append("- Safe local CSE materialization text replacements: `")
                .append(totalSafeLocalCseMaterializationBodyTextReplacementCount()).append("`\n");
        markdown.append("- Safe local CSE materialization runtime-equivalence payloads: `")
                .append(totalSafeLocalCseMaterializationRuntimeEquivalencePayloadPresentCount()).append("`\n");
        markdown.append("- Safe local CSE materialization runtime-equivalence passed: `")
                .append(totalSafeLocalCseMaterializationRuntimeEquivalencePassedCount()).append("`\n");
        markdown.append("- Safe local CSE materialization first blocker: `")
                .append(inline(safeLocalCseMaterializationFirstBlocker())).append("`\n");
        markdown.append("- Mad/FMA materialization status: `")
                .append(madFmaMaterializationStatus()).append("`\n");
        markdown.append("- Mad/FMA materialization passes: `")
                .append(totalMadFmaMaterializationPassCount()).append("`\n");
        markdown.append("- Mad/FMA materialization candidates: `")
                .append(totalMadFmaMaterializationCandidateCount()).append("`\n");
        markdown.append("- Mad/FMA materialized nodes: `")
                .append(totalMadFmaMaterializationTransformedNodeCount()).append("`\n");
        markdown.append("- Mad/FMA materialization text replacements: `")
                .append(totalMadFmaMaterializationBodyTextReplacementCount()).append("`\n");
        markdown.append("- Mad/FMA materialization fixed-point passes: `")
                .append(totalMadFmaMaterializationFixedPointPassCount()).append("`\n");
        markdown.append("- Mad/FMA materialization skipped blockers: `")
                .append(totalMadFmaMaterializationSkippedCount()).append("`\n");
        markdown.append("- Mad/FMA materialization runtime-equivalence payloads: `")
                .append(totalMadFmaMaterializationRuntimeEquivalencePayloadPresentCount()).append("`\n");
        markdown.append("- Mad/FMA materialization runtime-equivalence passed: `")
                .append(totalMadFmaMaterializationRuntimeEquivalencePassedCount()).append("`\n");
        markdown.append("- Mad/FMA materialization fast-math allowed: `")
                .append(madFmaMaterializationFastMathAllowed()).append("`\n");
        markdown.append("- Mad/FMA materialization first blocker: `")
                .append(inline(madFmaMaterializationFirstBlocker())).append("`\n");
        markdown.append("- Clamp materialization status: `")
                .append(clampMaterializationStatus()).append("`\n");
        markdown.append("- Clamp materialization passes: `")
                .append(totalClampMaterializationPassCount()).append("`\n");
        markdown.append("- Clamp materialization candidates: `")
                .append(totalClampMaterializationCandidateCount()).append("`\n");
        markdown.append("- Clamp materialized nodes: `")
                .append(totalClampMaterializationTransformedNodeCount()).append("`\n");
        markdown.append("- Clamp materialization text replacements: `")
                .append(totalClampMaterializationBodyTextReplacementCount()).append("`\n");
        markdown.append("- Clamp materialization runtime-equivalence payloads: `")
                .append(totalClampMaterializationRuntimeEquivalencePayloadPresentCount()).append("`\n");
        markdown.append("- Clamp materialization runtime-equivalence passed: `")
                .append(totalClampMaterializationRuntimeEquivalencePassedCount()).append("`\n");
        markdown.append("- Clamp materialization first blocker: `")
                .append(inline(clampMaterializationFirstBlocker())).append("`\n");
        markdown.append("- Step materialization status: `")
                .append(stepMaterializationStatus()).append("`\n");
        markdown.append("- Step materialization passes: `")
                .append(totalStepMaterializationPassCount()).append("`\n");
        markdown.append("- Step materialization candidates: `")
                .append(totalStepMaterializationCandidateCount()).append("`\n");
        markdown.append("- Step materialized nodes: `")
                .append(totalStepMaterializationTransformedNodeCount()).append("`\n");
        markdown.append("- Step materialization direct step count: `")
                .append(totalStepMaterializationDirectStepCount()).append("`\n");
        markdown.append("- Step materialization inverted step count: `")
                .append(totalStepMaterializationInvertedStepCount()).append("`\n");
        markdown.append("- Step materialization text replacements: `")
                .append(totalStepMaterializationBodyTextReplacementCount()).append("`\n");
        markdown.append("- Step materialization runtime-equivalence payloads: `")
                .append(totalStepMaterializationRuntimeEquivalencePayloadPresentCount()).append("`\n");
        markdown.append("- Step materialization runtime-equivalence passed: `")
                .append(totalStepMaterializationRuntimeEquivalencePassedCount()).append("`\n");
        markdown.append("- Step materialization first blocker: `")
                .append(inline(stepMaterializationFirstBlocker())).append("`\n");
        markdown.append("- Mix materialization status: `")
                .append(mixMaterializationStatus()).append("`\n");
        markdown.append("- Mix materialization passes: `")
                .append(totalMixMaterializationPassCount()).append("`\n");
        markdown.append("- Mix materialization candidates: `")
                .append(totalMixMaterializationCandidateCount()).append("`\n");
        markdown.append("- Mix materialized nodes: `")
                .append(totalMixMaterializationTransformedNodeCount()).append("`\n");
        markdown.append("- Mix materialization canonical count: `")
                .append(totalMixMaterializationCanonicalMixCount()).append("`\n");
        markdown.append("- Mix materialization expanded count: `")
                .append(totalMixMaterializationExpandedMixCount()).append("`\n");
        markdown.append("- Mix materialization MAD-expanded count: `")
                .append(totalMixMaterializationMadExpandedMixCount()).append("`\n");
        markdown.append("- Mix materialization text replacements: `")
                .append(totalMixMaterializationBodyTextReplacementCount()).append("`\n");
        markdown.append("- Mix materialization runtime-equivalence payloads: `")
                .append(totalMixMaterializationRuntimeEquivalencePayloadPresentCount()).append("`\n");
        markdown.append("- Mix materialization runtime-equivalence passed: `")
                .append(totalMixMaterializationRuntimeEquivalencePassedCount()).append("`\n");
        markdown.append("- Mix materialization fast-math allowed: `")
                .append(mixMaterializationFastMathAllowed()).append("`\n");
        markdown.append("- Mix materialization fast-math required: `")
                .append(mixMaterializationFastMathRequired()).append("`\n");
        markdown.append("- Mix materialization algebraic reassociation required: `")
                .append(mixMaterializationAlgebraicReassociationRequired()).append("`\n");
        markdown.append("- Mix materialization first blocker: `")
                .append(inline(mixMaterializationFirstBlocker())).append("`\n");
        markdown.append("- Loop vectorization materialization status: `")
                .append(loopVectorizationMaterializationStatus()).append("`\n");
        markdown.append("- Loop vectorization materialization passes: `")
                .append(totalLoopVectorizationMaterializationPassCount()).append("`\n");
        markdown.append("- Loop vectorization materialization candidates: `")
                .append(totalLoopVectorizationMaterializationCandidateCount()).append("`\n");
        markdown.append("- Loop vectorization transformed loops: `")
                .append(totalLoopVectorizationMaterializationTransformedLoopCount()).append("`\n");
        markdown.append("- Loop vectorization materialization text replacements: `")
                .append(totalLoopVectorizationMaterializationBodyTextReplacementCount()).append("`\n");
        markdown.append("- Loop vectorization materialization typed bodies materialized: `")
                .append(totalLoopVectorizationMaterializationTypedBodyMaterializedCount()).append("`\n");
        markdown.append("- Loop vectorization materialization typed bodies invalidated: `")
                .append(totalLoopVectorizationMaterializationTypedBodyInvalidatedCount()).append("`\n");
        markdown.append("- Loop vectorization materialization skipped blockers: `")
                .append(totalLoopVectorizationMaterializationSkippedCount()).append("`\n");
        markdown.append("- Loop vectorization materialization runtime-equivalence payloads: `")
                .append(totalLoopVectorizationMaterializationRuntimeEquivalencePayloadPresentCount()).append("`\n");
        markdown.append("- Loop vectorization materialization runtime-equivalence passed: `")
                .append(totalLoopVectorizationMaterializationRuntimeEquivalencePassedCount()).append("`\n");
        markdown.append("- Loop vectorization materialization first blocker: `")
                .append(inline(loopVectorizationMaterializationFirstBlocker())).append("`\n");
        markdown.append("- Typed dead-code materialization status: `")
                .append(typedDeadCodeMaterializationStatus()).append("`\n");
        markdown.append("- Typed dead-code materialization passes: `")
                .append(totalTypedDeadCodeMaterializationPassCount()).append("`\n");
        markdown.append("- Typed dead-code materialization nodes: `")
                .append(totalTypedDeadCodeMaterializationNodeCount()).append("`\n");
        markdown.append("- Typed dead-code materialization unreachable nodes: `")
                .append(totalTypedDeadCodeMaterializationUnreachableNodeCount()).append("`\n");
        markdown.append("- Typed dead-code materialization removed nodes: `")
                .append(totalTypedDeadCodeMaterializationRemovedNodeCount()).append("`\n");
        markdown.append("- Typed dead-code materialization runtime-equivalence payloads: `")
                .append(totalTypedDeadCodeMaterializationRuntimeEquivalencePayloadPresentCount()).append("`\n");
        markdown.append("- Typed dead-code materialization runtime-equivalence passed: `")
                .append(totalTypedDeadCodeMaterializationRuntimeEquivalencePassedCount()).append("`\n");
        markdown.append("- Typed dead-code materialization first blocker: `")
                .append(inline(typedDeadCodeMaterializationFirstBlocker())).append("`\n");
        markdown.append("- Safe local CSE preview passes: `").append(totalSafeLocalCsePreviewPassCount()).append("`\n");
        markdown.append("- Safe local CSE preview candidate expressions: `")
                .append(totalSafeLocalCsePreviewCandidateExpressionCount()).append("`\n");
        markdown.append("- Safe local CSE preview duplicate expressions: `")
                .append(totalSafeLocalCsePreviewDuplicateExpressionCount()).append("`\n");
        markdown.append("- Safe local CSE preview blockers: `")
                .append(totalSafeLocalCsePreviewBlockedCount()).append("`\n");
        markdown.append("- Typed dead-code preview passes: `").append(totalTypedDeadCodePreviewPassCount()).append("`\n");
        markdown.append("- Typed dead-code preview unreachable nodes: `")
                .append(totalTypedDeadCodePreviewUnreachableNodeCount()).append("`\n");
        markdown.append("- Typed dead-code preview blockers: `")
                .append(totalTypedDeadCodePreviewBlockedCount()).append("`\n");
        markdown.append("- Preview readiness status: `").append(previewReadinessStatus()).append("`\n");
        markdown.append("- Preview readiness families: `").append(inline(previewReadinessFamilySummary())).append("`\n");
        markdown.append("- Preview readiness recorded families: `").append(totalPreviewReadinessFamilyCount()).append("`\n");
        markdown.append("- Preview readiness candidate families: `")
                .append(totalPreviewReadinessCandidateFamilyCount()).append("`\n");
        markdown.append("- Preview readiness blocked families: `")
                .append(totalPreviewReadinessBlockedFamilyCount()).append("`\n");
        markdown.append("- Runtime-equivalence review status: `").append(runtimeEquivalenceReviewStatus()).append("`\n");
        markdown.append("- Runtime-equivalence review eligible: `").append(runtimeEquivalenceReviewEligible()).append("`\n");
        markdown.append("- Runtime-equivalence review required: `").append(runtimeEquivalenceReviewRequired()).append("`\n");
        markdown.append("- Runtime-equivalence review first blocker: `")
                .append(inline(runtimeEquivalenceReviewFirstBlocker())).append("`\n");
        markdown.append("- Runtime-equivalence review production mutation: `disabled`\n");
        markdown.append("- Runtime-equivalence review selected IR replacement: `disabled`\n");
        markdown.append("- Review package status: `").append(reviewPackageStatus()).append("`\n");
        markdown.append("- Review package required kernels: `").append(totalReviewPackageRequiredCount()).append("`\n");
        markdown.append("- Review package complete kernels: `").append(totalReviewPackageCompleteCount()).append("`\n");
        markdown.append("- Review package proposal passes: `").append(totalReviewPackageProposalPassCount()).append("`\n");
        markdown.append("- Review package pending approvals: `")
                .append(totalReviewPackagePendingApprovalCount()).append("`\n");
        markdown.append("- Review package approval manifest status: `")
                .append(reviewPackageApprovalManifestStatus()).append("`\n");
        markdown.append("- Review package approval manifests required: `")
                .append(totalReviewPackageApprovalManifestRequiredCount()).append("`\n");
        markdown.append("- Review package approval manifests present: `")
                .append(totalReviewPackageApprovalManifestPresentCount()).append("`\n");
        markdown.append("- Review package approval manifests accepted: `")
                .append(totalReviewPackageApprovalManifestAcceptedCount()).append("`\n");
        markdown.append("- Review package approval manifest first blocker: `")
                .append(inline(reviewPackageApprovalManifestFirstBlocker())).append("`\n");
        markdown.append("- Review package first blocker: `")
                .append(inline(reviewPackageFirstBlocker())).append("`\n");
        markdown.append("- Review package manual review only: `true`\n");
        markdown.append("- Review package production mutation: `disabled`\n");
        markdown.append("- Review package selected IR replacement: `disabled`\n");
        markdown.append("- Providers: `").append(inline(providerSummary())).append("`\n\n");
        if (entries.isEmpty()) {
            return markdown.toString();
        }
        markdown.append("| Kernel resource | Status | Passes | Proposal-only | Selected | Rolled back | Approval pending | Approval N/A | Candidate | Candidate blocker | Selection blocker | CF candidates | CF skipped | CF materialized | CF materialization blocker | CSE candidates | CSE duplicates | CSE blocked | CSE materialized | CSE materialization blocker | TDC unreachable | TDC blocked | TDC materialized | TDC materialization blocker | Review package | Review blocker | Providers |\n");
        markdown.append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- | --- | ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: | --- | ---: | ---: | ---: | --- | --- | --- | --- |\n");
        for (Entry entry : entries) {
            markdown.append("| `").append(table(entry.kernelResource())).append("` | `")
                    .append(table(entry.status())).append("` | `")
                    .append(entry.passCount()).append("` | `")
                    .append(entry.proposalOnlyCount()).append("` | `")
                    .append(entry.selectedOptimizedCount()).append("` | `")
                    .append(entry.rolledBackCount()).append("` | `")
                    .append(entry.approvalTemplatePendingCount()).append("` | `")
                    .append(entry.approvalTemplateNotApplicableCount()).append("` | `")
                    .append(table(entry.optimizedArtifactCandidateStatus())).append("` | `")
                    .append(table(entry.optimizedArtifactCandidateFirstBlocker())).append("` | `")
                    .append(table(entry.optimizedArtifactCandidateSelectionFirstBlocker())).append("` | `")
                    .append(entry.constantFoldingPreviewCandidateCount()).append("` | `")
                    .append(entry.constantFoldingPreviewSkippedCount()).append("` | `")
                    .append(entry.constantFoldingMaterializationTransformedNodeCount()).append("` | `")
                    .append(table(entry.constantFoldingMaterializationFirstBlocker())).append("` | `")
                    .append(entry.safeLocalCsePreviewCandidateExpressionCount()).append("` | `")
                    .append(entry.safeLocalCsePreviewDuplicateExpressionCount()).append("` | `")
                    .append(entry.safeLocalCsePreviewBlockedCount()).append("` | `")
                    .append(entry.safeLocalCseMaterializationTransformedNodeCount()).append("` | `")
                    .append(table(entry.safeLocalCseMaterializationFirstBlocker())).append("` | `")
                    .append(entry.typedDeadCodePreviewUnreachableNodeCount()).append("` | `")
                    .append(entry.typedDeadCodePreviewBlockedCount()).append("` | `")
                    .append(entry.typedDeadCodeMaterializationRemovedNodeCount()).append("` | `")
                    .append(table(entry.typedDeadCodeMaterializationFirstBlocker())).append("` | `")
                    .append(table(entry.reviewPackageStatus())).append("` | `")
                    .append(table(entry.reviewPackageFirstBlocker())).append("` | `")
                    .append(table(formatProviderCounts(entry.providerCounts()))).append("` |\n");
        }
        markdown.append('\n');
        return markdown.toString();
    }

    private static Map<String, Properties> loadEvidenceArtifacts(Path artifactRoot) throws IOException {
        LinkedHashMap<String, Properties> artifacts = new LinkedHashMap<>();
        if (artifactRoot == null || !Files.isDirectory(artifactRoot)) {
            return artifacts;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(artifactRoot)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(candidate -> ARTIFACT_FILE_NAME.equals(candidate.getFileName().toString()))
                    .toList()) {
                Properties properties = loadProperties(path);
                String resource = firstNonBlank(
                        properties.getProperty("backendResource", ""),
                        properties.getProperty("kernelResource", "")
                );
                if (resource.isBlank()) {
                    resource = inferResourceFromSiblingArtifacts(path.getParent());
                }
                if (!resource.isBlank() && !"unknown".equals(resource)) {
                    artifacts.put(resource, properties);
                }
            }
        }
        return artifacts;
    }

    private static String inferResourceFromSiblingArtifacts(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            return "";
        }
        for (String sibling : List.of(
                GpuRuntimeCompileArtifactDumper.RUNTIME_EXTENSION_PARTICIPATION_ARTIFACT,
                "backend-module.properties",
                "backend-diagnostics.properties"
        )) {
            Path path = directory.resolve(sibling);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            Properties properties = loadProperties(path);
            String resource = firstNonBlank(
                    properties.getProperty("backendResource", ""),
                    properties.getProperty("resource", "")
            );
            if (!resource.isBlank() && !"unknown".equals(resource)) {
                return resource;
            }
        }
        return "";
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String inline(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replace('`', '\'');
    }

    private static String table(String value) {
        return inline(value).replace("|", "\\|");
    }

    private static String formatProviderCounts(Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return "none";
        }
        StringBuilder summary = new StringBuilder();
        counts.forEach((provider, count) -> {
            if (!summary.isEmpty()) {
                summary.append(", ");
            }
            summary.append(provider).append('=').append(count);
        });
        return summary.toString();
    }

    private static Map<String, Integer> parseCountSummary(String summary) {
        if (summary == null || summary.isBlank() || "none".equals(summary)) {
            return Map.of();
        }
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (String part : summary.split(",")) {
            String trimmed = part.trim();
            int separator = trimmed.lastIndexOf('=');
            if (separator <= 0 || separator >= trimmed.length() - 1) {
                continue;
            }
            String key = trimmed.substring(0, separator).trim();
            int count = parseInt(trimmed.substring(separator + 1).trim(), 0);
            if (!key.isBlank() && count > 0) {
                counts.merge(key, count, Integer::sum);
            }
        }
        return Map.copyOf(counts);
    }

    private record PreviewFamilyReadiness(String family, String status, int candidateCount, int blockerCount) {
    }

    record Entry(
            String kernelResource,
            String status,
            int passCount,
            int proposalOnlyCount,
            int selectedOptimizedCount,
            int rolledBackCount,
            int approvalTemplatePendingCount,
            int approvalTemplateNotApplicableCount,
            int approvalTemplateRuntimeEquivalencePayloadRequiredCount,
            int approvalTemplateRuntimeEquivalencePayloadPresentCount,
            int approvalTemplateRuntimeEquivalencePayloadPassedCount,
            int approvalTemplateRuntimeEquivalencePayloadCompleteCount,
            String optimizedArtifactCandidateStatus,
            int optimizedArtifactCandidateCount,
            int optimizedArtifactCandidateReadyCount,
            int optimizedArtifactCandidateBlockedCount,
            int optimizedArtifactCandidateSelectionReadyCount,
            int optimizedArtifactCandidateSelectionAppliedCount,
            int optimizedArtifactCandidateSelectedIrReplacementCount,
            int optimizedArtifactCandidateMutationAllowedCount,
            String optimizedArtifactCandidateFirstBlocker,
            String optimizedArtifactCandidateSelectionFirstBlocker,
            int backendNeutralSourceMaterializationPassCount,
            int backendNeutralSourceMaterializationCandidateCount,
            int backendNeutralSourceMaterializationSourceReadyCount,
            int backendNeutralSourceMaterializationSourceLengthTotal,
            int backendNeutralSourceMaterializationMaterializationOnlyCount,
            String backendNeutralSourceMaterializationStatus,
            String backendNeutralSourceMaterializationFirstBlocker,
            int constantFoldingPreviewPassCount,
            int constantFoldingPreviewCandidateCount,
            int constantFoldingPreviewSkippedNonPlainLiteralCount,
            int constantFoldingPreviewSkippedDivideByZeroCount,
            int constantFoldingPreviewSkippedNonEvenDivisionCount,
            int constantFoldingPreviewSkippedUnsupportedOperatorCount,
            int constantFoldingPreviewSkippedNonLiteralOperandCount,
            boolean constantFoldingPreviewRuntimeEquivalenceRequiredBeforeRewrite,
            boolean constantFoldingPreviewApprovalRequiredBeforeRewrite,
            boolean constantFoldingPreviewIntegerOverflowProven,
            boolean constantFoldingPreviewFloatingPointRoundingProven,
            int constantFoldingMaterializationPassCount,
            int constantFoldingMaterializationCandidateCount,
            int constantFoldingMaterializationTransformedNodeCount,
            int constantFoldingMaterializationLiteralRewriteCount,
            int constantFoldingMaterializationIdentityRewriteCount,
            int constantFoldingMaterializationFixedPointPassCount,
            int constantFoldingMaterializationChangedMethodBodyCount,
            int constantFoldingMaterializationBodyTextReplacementCount,
            int constantFoldingMaterializationSkippedDivideByZeroCount,
            int constantFoldingMaterializationSkippedNonEvenDivisionCount,
            boolean constantFoldingMaterializationRuntimeEquivalenceRequiredBeforeSelection,
            boolean constantFoldingMaterializationRuntimeEquivalencePayloadRequired,
            int constantFoldingMaterializationRuntimeEquivalencePayloadPresentCount,
            int constantFoldingMaterializationRuntimeEquivalencePassedCount,
            boolean constantFoldingMaterializationApprovalRequiredBeforeProduction,
            String constantFoldingMaterializationStatus,
            String constantFoldingMaterializationFirstBlocker,
            int safeLocalCseMaterializationPassCount,
            int safeLocalCseMaterializationLocalBindingCount,
            int safeLocalCseMaterializationTransformedNodeCount,
            int safeLocalCseMaterializationBodyTextReplacementCount,
            int safeLocalCseMaterializationRuntimeEquivalencePayloadPresentCount,
            int safeLocalCseMaterializationRuntimeEquivalencePassedCount,
            String safeLocalCseMaterializationStatus,
            String safeLocalCseMaterializationFirstBlocker,
            int madFmaMaterializationPassCount,
            int madFmaMaterializationCandidateCount,
            int madFmaMaterializationTransformedNodeCount,
            int madFmaMaterializationChangedMethodBodyCount,
            int madFmaMaterializationBodyTextReplacementCount,
            int madFmaMaterializationFixedPointPassCount,
            int madFmaMaterializationSkippedFastMathPolicyCount,
            int madFmaMaterializationSkippedBodyTextPatternMissingCount,
            boolean madFmaMaterializationRuntimeEquivalenceRequiredBeforeSelection,
            boolean madFmaMaterializationRuntimeEquivalencePayloadRequired,
            int madFmaMaterializationRuntimeEquivalencePayloadPresentCount,
            int madFmaMaterializationRuntimeEquivalencePassedCount,
            boolean madFmaMaterializationApprovalRequiredBeforeProduction,
            boolean madFmaMaterializationFastMathAllowed,
            String madFmaMaterializationStatus,
            String madFmaMaterializationFirstBlocker,
            int loopVectorizationMaterializationPassCount,
            int loopVectorizationMaterializationCandidateCount,
            int loopVectorizationMaterializationTransformedLoopCount,
            int loopVectorizationMaterializationChangedMethodBodyCount,
            int loopVectorizationMaterializationBodyTextReplacementCount,
            int loopVectorizationMaterializationTypedBodyMaterializedCount,
            int loopVectorizationMaterializationTypedBodyInvalidatedCount,
            int loopVectorizationMaterializationSkippedLoopShapeCount,
            int loopVectorizationMaterializationSkippedUnsupportedWidthCount,
            int loopVectorizationMaterializationSkippedUnsafeLoadPatternCount,
            int loopVectorizationMaterializationRuntimeEquivalencePayloadPresentCount,
            int loopVectorizationMaterializationRuntimeEquivalencePassedCount,
            String loopVectorizationMaterializationStatus,
            String loopVectorizationMaterializationFirstBlocker,
            int typedDeadCodeMaterializationPassCount,
            int typedDeadCodeMaterializationNodeCount,
            int typedDeadCodeMaterializationUnreachableNodeCount,
            int typedDeadCodeMaterializationRemovedNodeCount,
            int typedDeadCodeMaterializationChangedMethodBodyCount,
            int typedDeadCodeMaterializationBlockedMissingRootCount,
            int typedDeadCodeMaterializationBlockedMissingChildReferenceCount,
            int typedDeadCodeMaterializationBlockedSideEffectingUnreachableNodeCount,
            boolean typedDeadCodeMaterializationRuntimeEquivalenceRequiredBeforeSelection,
            boolean typedDeadCodeMaterializationRuntimeEquivalencePayloadRequired,
            int typedDeadCodeMaterializationRuntimeEquivalencePayloadPresentCount,
            int typedDeadCodeMaterializationRuntimeEquivalencePassedCount,
            boolean typedDeadCodeMaterializationApprovalRequiredBeforeProduction,
            boolean typedDeadCodeMaterializationSideEffectFreedomProven,
            String typedDeadCodeMaterializationStatus,
            String typedDeadCodeMaterializationFirstBlocker,
            int safeLocalCsePreviewPassCount,
            int safeLocalCsePreviewExpressionCount,
            int safeLocalCsePreviewCandidateExpressionCount,
            int safeLocalCsePreviewDuplicateExpressionCount,
            int safeLocalCsePreviewEquivalenceClassCount,
            int safeLocalCsePreviewBlockedUnsupportedOperatorCount,
            int safeLocalCsePreviewBlockedImpureOperandCount,
            int safeLocalCsePreviewBlockedControlFlowBoundaryCount,
            boolean safeLocalCsePreviewRuntimeEquivalenceRequiredBeforeRewrite,
            boolean safeLocalCsePreviewApprovalRequiredBeforeRewrite,
            boolean safeLocalCsePreviewDominanceProven,
            boolean safeLocalCsePreviewSideEffectFreedomProven,
            int typedDeadCodePreviewPassCount,
            int typedDeadCodePreviewNodeCount,
            int typedDeadCodePreviewReachableNodeCount,
            int typedDeadCodePreviewUnreachableNodeCount,
            int typedDeadCodePreviewBlockedMissingRootCount,
            int typedDeadCodePreviewBlockedMissingChildReferenceCount,
            int typedDeadCodePreviewBlockedSideEffectingUnreachableNodeCount,
            boolean typedDeadCodePreviewRuntimeEquivalenceRequiredBeforeRewrite,
            boolean typedDeadCodePreviewApprovalRequiredBeforeRewrite,
            boolean typedDeadCodePreviewSideEffectFreedomProven,
            String reviewPackageStatus,
            boolean reviewPackageRequired,
            boolean reviewPackageComplete,
            String reviewPackageFirstBlocker,
            int reviewPackageProposalPassCount,
            int reviewPackagePendingApprovalCount,
            String reviewPackageRuntimeEquivalenceStatus,
            String reviewPackageApprovalManifestStatus,
            boolean reviewPackageApprovalManifestRequired,
            int reviewPackageApprovalManifestPresentCount,
            int reviewPackageApprovalManifestAcceptedCount,
            String reviewPackageApprovalManifestResourcePathSummary,
            String reviewPackageApprovalManifestFirstBlocker,
            boolean reviewPackageManualReviewOnly,
            Map<String, Integer> providerCounts,
            Map<String, String> evidenceProperties
    ) {

        Entry {
            kernelResource = normalize(kernelResource, "unknown");
            status = normalize(status, "unknown");
            passCount = Math.max(0, passCount);
            proposalOnlyCount = Math.max(0, proposalOnlyCount);
            selectedOptimizedCount = Math.max(0, selectedOptimizedCount);
            rolledBackCount = Math.max(0, rolledBackCount);
            approvalTemplatePendingCount = Math.max(0, approvalTemplatePendingCount);
            approvalTemplateNotApplicableCount = Math.max(0, approvalTemplateNotApplicableCount);
            approvalTemplateRuntimeEquivalencePayloadRequiredCount = Math.max(
                    0,
                    approvalTemplateRuntimeEquivalencePayloadRequiredCount
            );
            approvalTemplateRuntimeEquivalencePayloadPresentCount = Math.max(
                    0,
                    approvalTemplateRuntimeEquivalencePayloadPresentCount
            );
            approvalTemplateRuntimeEquivalencePayloadPassedCount = Math.max(
                    0,
                    approvalTemplateRuntimeEquivalencePayloadPassedCount
            );
            approvalTemplateRuntimeEquivalencePayloadCompleteCount = Math.max(
                    0,
                    approvalTemplateRuntimeEquivalencePayloadCompleteCount
            );
            optimizedArtifactCandidateStatus = normalize(optimizedArtifactCandidateStatus, "not-recorded");
            optimizedArtifactCandidateCount = Math.max(0, optimizedArtifactCandidateCount);
            optimizedArtifactCandidateReadyCount = Math.max(0, optimizedArtifactCandidateReadyCount);
            optimizedArtifactCandidateBlockedCount = Math.max(0, optimizedArtifactCandidateBlockedCount);
            optimizedArtifactCandidateSelectionReadyCount = Math.max(0, optimizedArtifactCandidateSelectionReadyCount);
            optimizedArtifactCandidateSelectionAppliedCount = Math.max(0, optimizedArtifactCandidateSelectionAppliedCount);
            optimizedArtifactCandidateSelectedIrReplacementCount = Math.max(0, optimizedArtifactCandidateSelectedIrReplacementCount);
            optimizedArtifactCandidateMutationAllowedCount = Math.max(0, optimizedArtifactCandidateMutationAllowedCount);
            optimizedArtifactCandidateFirstBlocker = normalize(optimizedArtifactCandidateFirstBlocker, "no-candidates");
            optimizedArtifactCandidateSelectionFirstBlocker = normalize(
                    optimizedArtifactCandidateSelectionFirstBlocker,
                    "no-candidates"
            );
            backendNeutralSourceMaterializationPassCount = Math.max(
                    0,
                    backendNeutralSourceMaterializationPassCount
            );
            backendNeutralSourceMaterializationCandidateCount = Math.max(
                    0,
                    backendNeutralSourceMaterializationCandidateCount
            );
            backendNeutralSourceMaterializationSourceReadyCount = Math.max(
                    0,
                    backendNeutralSourceMaterializationSourceReadyCount
            );
            backendNeutralSourceMaterializationSourceLengthTotal = Math.max(
                    0,
                    backendNeutralSourceMaterializationSourceLengthTotal
            );
            backendNeutralSourceMaterializationMaterializationOnlyCount = Math.max(
                    0,
                    backendNeutralSourceMaterializationMaterializationOnlyCount
            );
            backendNeutralSourceMaterializationStatus = normalize(
                    backendNeutralSourceMaterializationStatus,
                    "not-recorded"
            );
            backendNeutralSourceMaterializationFirstBlocker = normalize(
                    backendNeutralSourceMaterializationFirstBlocker,
                    "not-recorded"
            );
            constantFoldingPreviewPassCount = Math.max(0, constantFoldingPreviewPassCount);
            constantFoldingPreviewCandidateCount = Math.max(0, constantFoldingPreviewCandidateCount);
            constantFoldingPreviewSkippedNonPlainLiteralCount = Math.max(0, constantFoldingPreviewSkippedNonPlainLiteralCount);
            constantFoldingPreviewSkippedDivideByZeroCount = Math.max(0, constantFoldingPreviewSkippedDivideByZeroCount);
            constantFoldingPreviewSkippedNonEvenDivisionCount = Math.max(0, constantFoldingPreviewSkippedNonEvenDivisionCount);
            constantFoldingPreviewSkippedUnsupportedOperatorCount = Math.max(0, constantFoldingPreviewSkippedUnsupportedOperatorCount);
            constantFoldingPreviewSkippedNonLiteralOperandCount = Math.max(0, constantFoldingPreviewSkippedNonLiteralOperandCount);
            constantFoldingMaterializationPassCount = Math.max(0, constantFoldingMaterializationPassCount);
            constantFoldingMaterializationCandidateCount = Math.max(0, constantFoldingMaterializationCandidateCount);
            constantFoldingMaterializationTransformedNodeCount = Math.max(0, constantFoldingMaterializationTransformedNodeCount);
            constantFoldingMaterializationLiteralRewriteCount = Math.max(
                    0,
                    constantFoldingMaterializationLiteralRewriteCount
            );
            constantFoldingMaterializationIdentityRewriteCount = Math.max(
                    0,
                    constantFoldingMaterializationIdentityRewriteCount
            );
            constantFoldingMaterializationFixedPointPassCount = Math.max(
                    0,
                    constantFoldingMaterializationFixedPointPassCount
            );
            constantFoldingMaterializationChangedMethodBodyCount = Math.max(0, constantFoldingMaterializationChangedMethodBodyCount);
            constantFoldingMaterializationBodyTextReplacementCount = Math.max(0, constantFoldingMaterializationBodyTextReplacementCount);
            constantFoldingMaterializationSkippedDivideByZeroCount = Math.max(
                    0,
                    constantFoldingMaterializationSkippedDivideByZeroCount
            );
            constantFoldingMaterializationSkippedNonEvenDivisionCount = Math.max(
                    0,
                    constantFoldingMaterializationSkippedNonEvenDivisionCount
            );
            constantFoldingMaterializationRuntimeEquivalencePayloadPresentCount = Math.max(
                    0,
                    constantFoldingMaterializationRuntimeEquivalencePayloadPresentCount
            );
            constantFoldingMaterializationRuntimeEquivalencePassedCount = Math.max(
                    0,
                    constantFoldingMaterializationRuntimeEquivalencePassedCount
            );
            constantFoldingMaterializationStatus = normalize(
                    constantFoldingMaterializationStatus,
                    "not-recorded"
            );
            constantFoldingMaterializationFirstBlocker = normalize(
                    constantFoldingMaterializationFirstBlocker,
                    "not-recorded"
            );
            safeLocalCseMaterializationPassCount = Math.max(0, safeLocalCseMaterializationPassCount);
            safeLocalCseMaterializationLocalBindingCount = Math.max(
                    0,
                    safeLocalCseMaterializationLocalBindingCount
            );
            safeLocalCseMaterializationTransformedNodeCount = Math.max(
                    0,
                    safeLocalCseMaterializationTransformedNodeCount
            );
            safeLocalCseMaterializationBodyTextReplacementCount = Math.max(
                    0,
                    safeLocalCseMaterializationBodyTextReplacementCount
            );
            safeLocalCseMaterializationRuntimeEquivalencePayloadPresentCount = Math.max(
                    0,
                    safeLocalCseMaterializationRuntimeEquivalencePayloadPresentCount
            );
            safeLocalCseMaterializationRuntimeEquivalencePassedCount = Math.max(
                    0,
                    safeLocalCseMaterializationRuntimeEquivalencePassedCount
            );
            safeLocalCseMaterializationStatus = normalize(
                    safeLocalCseMaterializationStatus,
                    "not-recorded"
            );
            safeLocalCseMaterializationFirstBlocker = normalize(
                    safeLocalCseMaterializationFirstBlocker,
                    "not-recorded"
            );
            madFmaMaterializationPassCount = Math.max(0, madFmaMaterializationPassCount);
            madFmaMaterializationCandidateCount = Math.max(0, madFmaMaterializationCandidateCount);
            madFmaMaterializationTransformedNodeCount = Math.max(
                    0,
                    madFmaMaterializationTransformedNodeCount
            );
            madFmaMaterializationChangedMethodBodyCount = Math.max(
                    0,
                    madFmaMaterializationChangedMethodBodyCount
            );
            madFmaMaterializationBodyTextReplacementCount = Math.max(
                    0,
                    madFmaMaterializationBodyTextReplacementCount
            );
            madFmaMaterializationFixedPointPassCount = Math.max(0, madFmaMaterializationFixedPointPassCount);
            madFmaMaterializationSkippedFastMathPolicyCount = Math.max(
                    0,
                    madFmaMaterializationSkippedFastMathPolicyCount
            );
            madFmaMaterializationSkippedBodyTextPatternMissingCount = Math.max(
                    0,
                    madFmaMaterializationSkippedBodyTextPatternMissingCount
            );
            madFmaMaterializationRuntimeEquivalencePayloadPresentCount = Math.max(
                    0,
                    madFmaMaterializationRuntimeEquivalencePayloadPresentCount
            );
            madFmaMaterializationRuntimeEquivalencePassedCount = Math.max(
                    0,
                    madFmaMaterializationRuntimeEquivalencePassedCount
            );
            madFmaMaterializationStatus = normalize(
                    madFmaMaterializationStatus,
                    "not-recorded"
            );
            madFmaMaterializationFirstBlocker = normalize(
                    madFmaMaterializationFirstBlocker,
                    "not-recorded"
            );
            loopVectorizationMaterializationPassCount = Math.max(0, loopVectorizationMaterializationPassCount);
            loopVectorizationMaterializationCandidateCount = Math.max(0, loopVectorizationMaterializationCandidateCount);
            loopVectorizationMaterializationTransformedLoopCount = Math.max(
                    0,
                    loopVectorizationMaterializationTransformedLoopCount
            );
            loopVectorizationMaterializationChangedMethodBodyCount = Math.max(
                    0,
                    loopVectorizationMaterializationChangedMethodBodyCount
            );
            loopVectorizationMaterializationBodyTextReplacementCount = Math.max(
                    0,
                    loopVectorizationMaterializationBodyTextReplacementCount
            );
            loopVectorizationMaterializationTypedBodyMaterializedCount = Math.max(
                    0,
                    loopVectorizationMaterializationTypedBodyMaterializedCount
            );
            loopVectorizationMaterializationTypedBodyInvalidatedCount = Math.max(
                    0,
                    loopVectorizationMaterializationTypedBodyInvalidatedCount
            );
            loopVectorizationMaterializationSkippedLoopShapeCount = Math.max(
                    0,
                    loopVectorizationMaterializationSkippedLoopShapeCount
            );
            loopVectorizationMaterializationSkippedUnsupportedWidthCount = Math.max(
                    0,
                    loopVectorizationMaterializationSkippedUnsupportedWidthCount
            );
            loopVectorizationMaterializationSkippedUnsafeLoadPatternCount = Math.max(
                    0,
                    loopVectorizationMaterializationSkippedUnsafeLoadPatternCount
            );
            loopVectorizationMaterializationRuntimeEquivalencePayloadPresentCount = Math.max(
                    0,
                    loopVectorizationMaterializationRuntimeEquivalencePayloadPresentCount
            );
            loopVectorizationMaterializationRuntimeEquivalencePassedCount = Math.max(
                    0,
                    loopVectorizationMaterializationRuntimeEquivalencePassedCount
            );
            loopVectorizationMaterializationStatus = normalize(
                    loopVectorizationMaterializationStatus,
                    "not-recorded"
            );
            loopVectorizationMaterializationFirstBlocker = normalize(
                    loopVectorizationMaterializationFirstBlocker,
                    "not-recorded"
            );
            typedDeadCodeMaterializationPassCount = Math.max(0, typedDeadCodeMaterializationPassCount);
            typedDeadCodeMaterializationNodeCount = Math.max(0, typedDeadCodeMaterializationNodeCount);
            typedDeadCodeMaterializationUnreachableNodeCount = Math.max(
                    0,
                    typedDeadCodeMaterializationUnreachableNodeCount
            );
            typedDeadCodeMaterializationRemovedNodeCount = Math.max(
                    0,
                    typedDeadCodeMaterializationRemovedNodeCount
            );
            typedDeadCodeMaterializationChangedMethodBodyCount = Math.max(
                    0,
                    typedDeadCodeMaterializationChangedMethodBodyCount
            );
            typedDeadCodeMaterializationBlockedMissingRootCount = Math.max(
                    0,
                    typedDeadCodeMaterializationBlockedMissingRootCount
            );
            typedDeadCodeMaterializationBlockedMissingChildReferenceCount = Math.max(
                    0,
                    typedDeadCodeMaterializationBlockedMissingChildReferenceCount
            );
            typedDeadCodeMaterializationBlockedSideEffectingUnreachableNodeCount = Math.max(
                    0,
                    typedDeadCodeMaterializationBlockedSideEffectingUnreachableNodeCount
            );
            typedDeadCodeMaterializationRuntimeEquivalencePayloadPresentCount = Math.max(
                    0,
                    typedDeadCodeMaterializationRuntimeEquivalencePayloadPresentCount
            );
            typedDeadCodeMaterializationRuntimeEquivalencePassedCount = Math.max(
                    0,
                    typedDeadCodeMaterializationRuntimeEquivalencePassedCount
            );
            typedDeadCodeMaterializationStatus = normalize(
                    typedDeadCodeMaterializationStatus,
                    "not-recorded"
            );
            typedDeadCodeMaterializationFirstBlocker = normalize(
                    typedDeadCodeMaterializationFirstBlocker,
                    "not-recorded"
            );
            safeLocalCsePreviewPassCount = Math.max(0, safeLocalCsePreviewPassCount);
            safeLocalCsePreviewExpressionCount = Math.max(0, safeLocalCsePreviewExpressionCount);
            safeLocalCsePreviewCandidateExpressionCount = Math.max(0, safeLocalCsePreviewCandidateExpressionCount);
            safeLocalCsePreviewDuplicateExpressionCount = Math.max(0, safeLocalCsePreviewDuplicateExpressionCount);
            safeLocalCsePreviewEquivalenceClassCount = Math.max(0, safeLocalCsePreviewEquivalenceClassCount);
            safeLocalCsePreviewBlockedUnsupportedOperatorCount = Math.max(0, safeLocalCsePreviewBlockedUnsupportedOperatorCount);
            safeLocalCsePreviewBlockedImpureOperandCount = Math.max(0, safeLocalCsePreviewBlockedImpureOperandCount);
            safeLocalCsePreviewBlockedControlFlowBoundaryCount = Math.max(0, safeLocalCsePreviewBlockedControlFlowBoundaryCount);
            typedDeadCodePreviewPassCount = Math.max(0, typedDeadCodePreviewPassCount);
            typedDeadCodePreviewNodeCount = Math.max(0, typedDeadCodePreviewNodeCount);
            typedDeadCodePreviewReachableNodeCount = Math.max(0, typedDeadCodePreviewReachableNodeCount);
            typedDeadCodePreviewUnreachableNodeCount = Math.max(0, typedDeadCodePreviewUnreachableNodeCount);
            typedDeadCodePreviewBlockedMissingRootCount = Math.max(0, typedDeadCodePreviewBlockedMissingRootCount);
            typedDeadCodePreviewBlockedMissingChildReferenceCount = Math.max(0, typedDeadCodePreviewBlockedMissingChildReferenceCount);
            typedDeadCodePreviewBlockedSideEffectingUnreachableNodeCount = Math.max(0, typedDeadCodePreviewBlockedSideEffectingUnreachableNodeCount);
            reviewPackageStatus = normalize(reviewPackageStatus, "not-recorded");
            reviewPackageFirstBlocker = normalize(reviewPackageFirstBlocker, "none");
            reviewPackageProposalPassCount = Math.max(0, reviewPackageProposalPassCount);
            reviewPackagePendingApprovalCount = Math.max(0, reviewPackagePendingApprovalCount);
            reviewPackageRuntimeEquivalenceStatus = normalize(reviewPackageRuntimeEquivalenceStatus, "unknown");
            reviewPackageApprovalManifestStatus = normalize(reviewPackageApprovalManifestStatus, "not-recorded");
            reviewPackageApprovalManifestPresentCount = Math.max(0, reviewPackageApprovalManifestPresentCount);
            reviewPackageApprovalManifestAcceptedCount = Math.max(0, reviewPackageApprovalManifestAcceptedCount);
            reviewPackageApprovalManifestResourcePathSummary = normalize(
                    reviewPackageApprovalManifestResourcePathSummary,
                    "none"
            );
            reviewPackageApprovalManifestFirstBlocker = normalize(
                    reviewPackageApprovalManifestFirstBlocker,
                    "none"
            );
            providerCounts = normalizeProviderCounts(providerCounts);
            evidenceProperties = normalizeEvidenceProperties(evidenceProperties);
        }

        int constantFoldingPreviewSkippedCount() {
            return constantFoldingPreviewSkippedNonPlainLiteralCount
                    + constantFoldingPreviewSkippedDivideByZeroCount
                    + constantFoldingPreviewSkippedNonEvenDivisionCount
                    + constantFoldingPreviewSkippedUnsupportedOperatorCount
                    + constantFoldingPreviewSkippedNonLiteralOperandCount;
        }

        int constantFoldingMaterializationSkippedCount() {
            return constantFoldingMaterializationSkippedDivideByZeroCount
                    + constantFoldingMaterializationSkippedNonEvenDivisionCount;
        }

        int safeLocalCsePreviewBlockedCount() {
            return safeLocalCsePreviewBlockedUnsupportedOperatorCount
                    + safeLocalCsePreviewBlockedImpureOperandCount
                    + safeLocalCsePreviewBlockedControlFlowBoundaryCount;
        }

        int madFmaMaterializationSkippedCount() {
            return madFmaMaterializationSkippedFastMathPolicyCount
                    + madFmaMaterializationSkippedBodyTextPatternMissingCount;
        }

        int intrinsicMaterializationInt(String prefix, String suffix) {
            return parseInt(evidenceProperties.get(prefix + "." + suffix), 0);
        }

        boolean intrinsicMaterializationBoolean(String prefix, String suffix) {
            return parseBoolean(evidenceProperties.get(prefix + "." + suffix));
        }

        String intrinsicMaterializationStatus(String prefix) {
            return evidenceProperties.getOrDefault(prefix + ".status", "not-recorded");
        }

        String intrinsicMaterializationFirstBlocker(String prefix) {
            return evidenceProperties.getOrDefault(prefix + ".firstBlocker", "not-recorded");
        }

        int intrinsicMaterializationSkippedCount(String prefix) {
            return intrinsicMaterializationInt(prefix, "skipped.typedBodyMissing.count")
                    + intrinsicMaterializationInt(prefix, "skipped.unsupportedFormat.count")
                    + intrinsicMaterializationInt(prefix, "skipped.fastMathPolicy.count")
                    + intrinsicMaterializationInt(prefix, "skipped.missingChildReference.count")
                    + intrinsicMaterializationInt(prefix, "skipped.unsupportedShape.count")
                    + intrinsicMaterializationInt(prefix, "skipped.bodyTextPatternMissing.count");
        }

        int loopVectorizationMaterializationSkippedCount() {
            return loopVectorizationMaterializationSkippedLoopShapeCount
                    + loopVectorizationMaterializationSkippedUnsupportedWidthCount
                    + loopVectorizationMaterializationSkippedUnsafeLoadPatternCount;
        }

        int typedDeadCodePreviewBlockedCount() {
            return typedDeadCodePreviewBlockedMissingRootCount
                    + typedDeadCodePreviewBlockedMissingChildReferenceCount
                    + typedDeadCodePreviewBlockedSideEffectingUnreachableNodeCount;
        }

        int typedDeadCodeMaterializationBlockedCount() {
            return typedDeadCodeMaterializationBlockedMissingRootCount
                    + typedDeadCodeMaterializationBlockedMissingChildReferenceCount
                    + typedDeadCodeMaterializationBlockedSideEffectingUnreachableNodeCount;
        }

        int policyGateSkippedCount() {
            return parseInt(evidenceProperties.get("policyGate.skipped.count"), 0);
        }

        int policyGateOptimizerPolicyDisabledCount() {
            return parseInt(evidenceProperties.get("policyGate.optimizerPolicyDisabled.count"), 0);
        }

        int policyGateFamilyDisabledCount() {
            return parseInt(evidenceProperties.get("policyGate.familyDisabled.count"), 0);
        }

        int policyGateFamilyNotEnabledCount() {
            return parseInt(evidenceProperties.get("policyGate.familyNotEnabled.count"), 0);
        }

        int policyGateProviderInvokedCount() {
            return parseInt(evidenceProperties.get("policyGate.providerInvoked.count"), 0);
        }

        String policyGateFirstBlocker() {
            return evidenceProperties.getOrDefault("policyGate.firstBlocker", "none");
        }

        String policyGateFamilySummary() {
            return evidenceProperties.getOrDefault("policyGate.family.summary", "none");
        }

        int constantFoldingPreviewProofBlockerCount() {
            if (constantFoldingPreviewCandidateCount <= 0) {
                return 0;
            }
            int blockers = 0;
            if (!constantFoldingPreviewIntegerOverflowProven) {
                blockers++;
            }
            if (!constantFoldingPreviewFloatingPointRoundingProven) {
                blockers++;
            }
            return blockers;
        }

        int safeLocalCsePreviewProofBlockerCount() {
            if (safeLocalCsePreviewDuplicateExpressionCount <= 0) {
                return 0;
            }
            int blockers = 0;
            if (!safeLocalCsePreviewDominanceProven) {
                blockers++;
            }
            if (!safeLocalCsePreviewSideEffectFreedomProven) {
                blockers++;
            }
            return blockers;
        }

        int typedDeadCodePreviewProofBlockerCount() {
            if (typedDeadCodePreviewUnreachableNodeCount <= 0) {
                return 0;
            }
            return typedDeadCodePreviewSideEffectFreedomProven ? 0 : 1;
        }

        static Entry from(String kernelResource, Properties properties) {
            if (properties == null || properties.isEmpty()) {
                return new Entry(kernelResource, "missing", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        "not-recorded", 0, 0, 0, 0, 0, 0, 0, "no-candidates", "no-candidates",
                        0, 0, 0, 0, 0, "not-recorded", "not-recorded",
                        0, 0, 0, 0, 0, 0, 0,
                        false, false, false, false,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, 0, 0, false,
                        "not-recorded", "not-recorded",
                        0, 0, 0, 0, 0, 0, "not-recorded", "not-recorded",
                        0, 0, 0, 0, 0, 0, 0, 0, false, false, 0, 0, false, false,
                        "not-recorded", "not-recorded",
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "not-recorded", "not-recorded",
                        0, 0, 0, 0, 0, 0, 0, 0, false, false, 0, 0, false, false,
                        "not-recorded", "not-recorded",
                        0, 0, 0, 0, 0, 0, 0, 0, false, false, false, false,
                        0, 0, 0, 0, 0, 0, 0, false, false, false,
                        "not-recorded", false, false, "review-package-not-recorded", 0, 0, "unknown",
                        "not-recorded", false, 0, 0, "none", "approval-manifest-not-recorded", false,
                        Map.of(), Map.of());
            }
            CandidateEvidence optimizedArtifactCandidate = optimizedArtifactCandidateEvidence(properties);
            return new Entry(
                    kernelResource,
                    properties.getProperty("status", "unknown"),
                    parseInt(properties.getProperty("pass.count"), 0),
                    parseInt(properties.getProperty("proposalOnly.count"), 0),
                    parseInt(properties.getProperty("selectedOptimized.count"), 0),
                    parseInt(properties.getProperty("rolledBack.count"), 0),
                    parseInt(properties.getProperty("approvalTemplate.pending.count"), 0),
                    parseInt(properties.getProperty("approvalTemplate.notApplicable.count"), 0),
                    parseInt(properties.getProperty("approvalTemplate.runtimeEquivalencePayloadRequired.count"), 0),
                    parseInt(properties.getProperty("approvalTemplate.runtimeEquivalencePayloadPresent.count"), 0),
                    parseInt(properties.getProperty("approvalTemplate.runtimeEquivalencePayloadPassed.count"), 0),
                    parseInt(properties.getProperty("approvalTemplate.runtimeEquivalencePayloadComplete.count"), 0),
                    optimizedArtifactCandidate.status(),
                    optimizedArtifactCandidate.count(),
                    optimizedArtifactCandidate.readyCount(),
                    optimizedArtifactCandidate.blockedCount(),
                    optimizedArtifactCandidate.selectionReadyCount(),
                    optimizedArtifactCandidate.selectionAppliedCount(),
                    optimizedArtifactCandidate.selectedIrReplacementCount(),
                    optimizedArtifactCandidate.mutationAllowedCount(),
                    optimizedArtifactCandidate.firstBlocker(),
                    optimizedArtifactCandidate.selectionFirstBlocker(),
                    parseInt(properties.getProperty("backendNeutralSourceMaterialization.pass.count"), 0),
                    parseInt(properties.getProperty("backendNeutralSourceMaterialization.candidate.count"), 0),
                    parseInt(properties.getProperty("backendNeutralSourceMaterialization.sourceReady.count"), 0),
                    parseInt(properties.getProperty("backendNeutralSourceMaterialization.sourceLength.total"), 0),
                    parseInt(properties.getProperty("backendNeutralSourceMaterialization.materializationOnly.count"), 0),
                    properties.getProperty("backendNeutralSourceMaterialization.status", "not-recorded"),
                    properties.getProperty("backendNeutralSourceMaterialization.firstBlocker", "not-recorded"),
                    parseInt(properties.getProperty("constantFoldingPreview.pass.count"), 0),
                    parseInt(properties.getProperty("constantFoldingPreview.candidate.count"), 0),
                    parseInt(properties.getProperty("constantFoldingPreview.skipped.nonPlainLiteral.count"), 0),
                    parseInt(properties.getProperty("constantFoldingPreview.skipped.divideByZero.count"), 0),
                    parseInt(properties.getProperty("constantFoldingPreview.skipped.nonEvenDivision.count"), 0),
                    parseInt(properties.getProperty("constantFoldingPreview.skipped.unsupportedOperator.count"), 0),
                    parseInt(properties.getProperty("constantFoldingPreview.skipped.nonLiteralOperand.count"), 0),
                    parseBoolean(properties.getProperty("constantFoldingPreview.runtimeEquivalenceRequiredBeforeRewrite")),
                    parseBoolean(properties.getProperty("constantFoldingPreview.approvalRequiredBeforeRewrite")),
                    parseBoolean(properties.getProperty("constantFoldingPreview.integerOverflowProven")),
                    parseBoolean(properties.getProperty("constantFoldingPreview.floatingPointRoundingProven")),
                    parseInt(properties.getProperty("constantFoldingMaterialization.pass.count"), 0),
                    parseInt(properties.getProperty("constantFoldingMaterialization.candidate.count"), 0),
                    parseInt(properties.getProperty("constantFoldingMaterialization.transformedNode.count"), 0),
                    parseInt(properties.getProperty("constantFoldingMaterialization.literalRewrite.count"), 0),
                    parseInt(properties.getProperty("constantFoldingMaterialization.identityRewrite.count"), 0),
                    parseInt(properties.getProperty("constantFoldingMaterialization.fixedPointPass.count"), 0),
                    parseInt(properties.getProperty("constantFoldingMaterialization.changedMethodBody.count"), 0),
                    parseInt(properties.getProperty("constantFoldingMaterialization.bodyTextReplacement.count"), 0),
                    parseInt(properties.getProperty("constantFoldingMaterialization.skipped.divideByZero.count"), 0),
                    parseInt(properties.getProperty("constantFoldingMaterialization.skipped.nonEvenDivision.count"), 0),
                    parseBoolean(properties.getProperty(
                            "constantFoldingMaterialization.runtimeEquivalenceRequiredBeforeSelection"
                    )),
                    parseBoolean(properties.getProperty(
                            "constantFoldingMaterialization.runtimeEquivalencePayloadRequired"
                    )),
                    parseInt(properties.getProperty(
                            "constantFoldingMaterialization.runtimeEquivalencePayloadPresent.count"
                    ), 0),
                    parseInt(properties.getProperty(
                            "constantFoldingMaterialization.runtimeEquivalencePassed.count"
                    ), 0),
                    parseBoolean(properties.getProperty(
                            "constantFoldingMaterialization.approvalRequiredBeforeProduction"
                    )),
                    properties.getProperty("constantFoldingMaterialization.status", "not-recorded"),
                    properties.getProperty("constantFoldingMaterialization.firstBlocker", "not-recorded"),
                    parseInt(properties.getProperty("safeLocalCseMaterialization.pass.count"), 0),
                    parseInt(properties.getProperty("safeLocalCseMaterialization.localBinding.count"), 0),
                    parseInt(properties.getProperty("safeLocalCseMaterialization.transformedNode.count"), 0),
                    parseInt(properties.getProperty("safeLocalCseMaterialization.bodyTextReplacement.count"), 0),
                    parseInt(properties.getProperty(
                            "safeLocalCseMaterialization.runtimeEquivalencePayloadPresent.count"
                    ), 0),
                    parseInt(properties.getProperty(
                            "safeLocalCseMaterialization.runtimeEquivalencePassed.count"
                    ), 0),
                    properties.getProperty("safeLocalCseMaterialization.status", "not-recorded"),
                    properties.getProperty("safeLocalCseMaterialization.firstBlocker", "not-recorded"),
                    parseInt(properties.getProperty("madFmaMaterialization.pass.count"), 0),
                    parseInt(properties.getProperty("madFmaMaterialization.candidate.count"), 0),
                    parseInt(properties.getProperty("madFmaMaterialization.transformedNode.count"), 0),
                    parseInt(properties.getProperty("madFmaMaterialization.changedMethodBody.count"), 0),
                    parseInt(properties.getProperty("madFmaMaterialization.bodyTextReplacement.count"), 0),
                    parseInt(properties.getProperty("madFmaMaterialization.fixedPoint.pass.count"), 0),
                    parseInt(properties.getProperty("madFmaMaterialization.skipped.fastMathPolicy.count"), 0),
                    parseInt(properties.getProperty("madFmaMaterialization.skipped.bodyTextPatternMissing.count"), 0),
                    parseBoolean(properties.getProperty(
                            "madFmaMaterialization.runtimeEquivalenceRequiredBeforeSelection"
                    )),
                    parseBoolean(properties.getProperty(
                            "madFmaMaterialization.runtimeEquivalencePayloadRequired"
                    )),
                    parseInt(properties.getProperty(
                            "madFmaMaterialization.runtimeEquivalencePayloadPresent.count"
                    ), 0),
                    parseInt(properties.getProperty(
                            "madFmaMaterialization.runtimeEquivalencePassed.count"
                    ), 0),
                    parseBoolean(properties.getProperty(
                            "madFmaMaterialization.approvalRequiredBeforeProduction"
                    )),
                    parseBoolean(properties.getProperty("madFmaMaterialization.fastMathAllowed")),
                    properties.getProperty("madFmaMaterialization.status", "not-recorded"),
                    properties.getProperty("madFmaMaterialization.firstBlocker", "not-recorded"),
                    parseInt(properties.getProperty("loopVectorizationMaterialization.pass.count"), 0),
                    parseInt(properties.getProperty("loopVectorizationMaterialization.candidate.count"), 0),
                    parseInt(properties.getProperty("loopVectorizationMaterialization.transformedLoop.count"), 0),
                    parseInt(properties.getProperty("loopVectorizationMaterialization.changedMethodBody.count"), 0),
                    parseInt(properties.getProperty("loopVectorizationMaterialization.bodyTextReplacement.count"), 0),
                    parseInt(properties.getProperty("loopVectorizationMaterialization.typedBody.materialized.count"), 0),
                    parseInt(properties.getProperty("loopVectorizationMaterialization.typedBody.invalidated.count"), 0),
                    parseInt(properties.getProperty("loopVectorizationMaterialization.skipped.loopShape.count"), 0),
                    parseInt(properties.getProperty("loopVectorizationMaterialization.skipped.unsupportedWidth.count"), 0),
                    parseInt(properties.getProperty("loopVectorizationMaterialization.skipped.unsafeLoadPattern.count"), 0),
                    parseInt(properties.getProperty(
                            "loopVectorizationMaterialization.runtimeEquivalencePayloadPresent.count"
                    ), 0),
                    parseInt(properties.getProperty(
                            "loopVectorizationMaterialization.runtimeEquivalencePassed.count"
                    ), 0),
                    properties.getProperty("loopVectorizationMaterialization.status", "not-recorded"),
                    properties.getProperty("loopVectorizationMaterialization.firstBlocker", "not-recorded"),
                    parseInt(properties.getProperty("typedDeadCodeMaterialization.pass.count"), 0),
                    parseInt(properties.getProperty("typedDeadCodeMaterialization.node.count"), 0),
                    parseInt(properties.getProperty("typedDeadCodeMaterialization.unreachableNode.count"), 0),
                    parseInt(properties.getProperty("typedDeadCodeMaterialization.removedNode.count"), 0),
                    parseInt(properties.getProperty("typedDeadCodeMaterialization.changedMethodBody.count"), 0),
                    parseInt(properties.getProperty("typedDeadCodeMaterialization.blocked.missingRoot.count"), 0),
                    parseInt(properties.getProperty(
                            "typedDeadCodeMaterialization.blocked.missingChildReference.count"
                    ), 0),
                    parseInt(properties.getProperty(
                            "typedDeadCodeMaterialization.blocked.sideEffectingUnreachableNode.count"
                    ), 0),
                    parseBoolean(properties.getProperty(
                            "typedDeadCodeMaterialization.runtimeEquivalenceRequiredBeforeSelection"
                    )),
                    parseBoolean(properties.getProperty(
                            "typedDeadCodeMaterialization.runtimeEquivalencePayloadRequired"
                    )),
                    parseInt(properties.getProperty(
                            "typedDeadCodeMaterialization.runtimeEquivalencePayloadPresent.count"
                    ), 0),
                    parseInt(properties.getProperty(
                            "typedDeadCodeMaterialization.runtimeEquivalencePassed.count"
                    ), 0),
                    parseBoolean(properties.getProperty(
                            "typedDeadCodeMaterialization.approvalRequiredBeforeProduction"
                    )),
                    parseBoolean(properties.getProperty(
                            "typedDeadCodeMaterialization.sideEffectFreedomProven"
                    )),
                    properties.getProperty("typedDeadCodeMaterialization.status", "not-recorded"),
                    properties.getProperty("typedDeadCodeMaterialization.firstBlocker", "not-recorded"),
                    parseInt(properties.getProperty("safeLocalCsePreview.pass.count"), 0),
                    parseInt(properties.getProperty("safeLocalCsePreview.expression.count"), 0),
                    parseInt(properties.getProperty("safeLocalCsePreview.candidateExpression.count"), 0),
                    parseInt(properties.getProperty("safeLocalCsePreview.duplicateExpression.count"), 0),
                    parseInt(properties.getProperty("safeLocalCsePreview.equivalenceClass.count"), 0),
                    parseInt(properties.getProperty("safeLocalCsePreview.blocked.unsupportedOperator.count"), 0),
                    parseInt(properties.getProperty("safeLocalCsePreview.blocked.impureOperand.count"), 0),
                    parseInt(properties.getProperty("safeLocalCsePreview.blocked.controlFlowBoundary.count"), 0),
                    parseBoolean(properties.getProperty("safeLocalCsePreview.runtimeEquivalenceRequiredBeforeRewrite")),
                    parseBoolean(properties.getProperty("safeLocalCsePreview.approvalRequiredBeforeRewrite")),
                    parseBoolean(properties.getProperty("safeLocalCsePreview.dominanceProven")),
                    parseBoolean(properties.getProperty("safeLocalCsePreview.sideEffectFreedomProven")),
                    parseInt(properties.getProperty("typedDeadCodePreview.pass.count"), 0),
                    parseInt(properties.getProperty("typedDeadCodePreview.node.count"), 0),
                    parseInt(properties.getProperty("typedDeadCodePreview.reachableNode.count"), 0),
                    parseInt(properties.getProperty("typedDeadCodePreview.unreachableNode.count"), 0),
                    parseInt(properties.getProperty("typedDeadCodePreview.blocked.missingRoot.count"), 0),
                    parseInt(properties.getProperty("typedDeadCodePreview.blocked.missingChildReference.count"), 0),
                    parseInt(properties.getProperty("typedDeadCodePreview.blocked.sideEffectingUnreachableNode.count"), 0),
                    parseBoolean(properties.getProperty("typedDeadCodePreview.runtimeEquivalenceRequiredBeforeRewrite")),
                    parseBoolean(properties.getProperty("typedDeadCodePreview.approvalRequiredBeforeRewrite")),
                    parseBoolean(properties.getProperty("typedDeadCodePreview.sideEffectFreedomProven")),
                    properties.getProperty("reviewPackage.status", "not-recorded"),
                    parseBoolean(properties.getProperty("reviewPackage.required")),
                    parseBoolean(properties.getProperty("reviewPackage.complete")),
                    properties.getProperty("reviewPackage.firstBlocker", "none"),
                    parseInt(properties.getProperty("reviewPackage.proposalPass.count"), 0),
                    parseInt(properties.getProperty("reviewPackage.pendingApproval.count"), 0),
                    properties.getProperty("reviewPackage.runtimeEquivalence.status", "unknown"),
                    properties.getProperty("reviewPackage.approvalManifest.status", "not-recorded"),
                    parseBoolean(properties.getProperty("reviewPackage.approvalManifest.required")),
                    parseInt(properties.getProperty("reviewPackage.approvalManifest.present.count"), 0),
                    parseInt(properties.getProperty("reviewPackage.approvalManifest.accepted.count"), 0),
                    properties.getProperty("reviewPackage.approvalManifest.resourcePath.summary", "none"),
                    properties.getProperty("reviewPackage.approvalManifest.firstBlocker", "none"),
                    parseBoolean(properties.getProperty("reviewPackage.manualReviewOnly")),
                    parseProviderCounts(properties),
                    copyProperties(properties)
            );
        }

        private static boolean parseBoolean(String value) {
            return "true".equalsIgnoreCase(value)
                    || "yes".equalsIgnoreCase(value)
                    || "enabled".equalsIgnoreCase(value)
                    || "required".equalsIgnoreCase(value);
        }

        private static String normalize(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }

        private static CandidateEvidence optimizedArtifactCandidateEvidence(Properties properties) {
            if (properties.containsKey("optimizedArtifactCandidate.count")
                    || properties.containsKey("optimizedArtifactCandidate.status")) {
                return new CandidateEvidence(
                        properties.getProperty("optimizedArtifactCandidate.status", "not-recorded"),
                        parseInt(properties.getProperty("optimizedArtifactCandidate.count"), 0),
                        parseInt(properties.getProperty("optimizedArtifactCandidate.ready.count"), 0),
                        parseInt(properties.getProperty("optimizedArtifactCandidate.blocked.count"), 0),
                        parseInt(properties.getProperty("optimizedArtifactCandidate.selectionReady.count"), 0),
                        parseInt(properties.getProperty("optimizedArtifactCandidate.selectionApplied.count"), 0),
                        parseInt(properties.getProperty("optimizedArtifactCandidate.selectedIrReplacement.count"), 0),
                        parseInt(properties.getProperty("optimizedArtifactCandidate.mutationAllowed.count"), 0),
                        properties.getProperty("optimizedArtifactCandidate.firstBlocker", "no-candidates"),
                        properties.getProperty("optimizedArtifactCandidate.selectionFirstBlocker", "no-candidates")
                );
            }
            return optimizedArtifactCandidateEvidenceFromPassFields(properties);
        }

        private static CandidateEvidence optimizedArtifactCandidateEvidenceFromPassFields(Properties properties) {
            int count = 0;
            int readyCount = 0;
            int blockedCount = 0;
            int selectionReadyCount = 0;
            int selectionAppliedCount = 0;
            int selectedIrReplacementCount = 0;
            int mutationAllowedCount = 0;
            String firstBlocker = "no-candidates";
            String selectionFirstBlocker = "no-candidates";
            int passCount = parseInt(properties.getProperty("pass.count"), 0);
            for (int index = 0; index < passCount; index++) {
                String prefix = "pass." + index + ".proofArtifact.field.optimizedArtifactCandidate.";
                if (!hasCandidateField(properties, prefix)) {
                    continue;
                }
                count++;
                String status = properties.getProperty(prefix + "status", "unknown");
                if ("candidate-ready".equals(status)) {
                    readyCount++;
                } else {
                    blockedCount++;
                }
                if (parseBoolean(properties.getProperty(prefix + "selectionReady"))) {
                    selectionReadyCount++;
                }
                if (parseBoolean(properties.getProperty(prefix + "selectionApplied"))) {
                    selectionAppliedCount++;
                }
                if (parseBoolean(properties.getProperty(prefix + "selectedIrReplacement"))) {
                    selectedIrReplacementCount++;
                }
                if (parseBoolean(properties.getProperty(prefix + "mutationAllowed"))) {
                    mutationAllowedCount++;
                }
                String candidateBlocker = properties.getProperty(prefix + "firstBlocker", "none");
                if (isPreferredCandidateBlocker(firstBlocker, candidateBlocker)) {
                    firstBlocker = candidateBlocker;
                }
                String candidateSelectionBlocker = properties.getProperty(
                        prefix + "selectionFirstBlocker",
                        "selection-gate-not-bound"
                );
                if (isPreferredCandidateBlocker(selectionFirstBlocker, candidateSelectionBlocker)) {
                    selectionFirstBlocker = candidateSelectionBlocker;
                }
            }
            String status = optimizedArtifactCandidateStatus(count, readyCount, blockedCount);
            if (count > 0 && "no-candidates".equals(firstBlocker)) {
                firstBlocker = "none";
            }
            if (count > 0 && "no-candidates".equals(selectionFirstBlocker)) {
                selectionFirstBlocker = "selection-gate-not-bound";
            }
            return new CandidateEvidence(
                    status,
                    count,
                    readyCount,
                    blockedCount,
                    selectionReadyCount,
                    selectionAppliedCount,
                    selectedIrReplacementCount,
                    mutationAllowedCount,
                    firstBlocker,
                    selectionFirstBlocker
            );
        }

        private static boolean hasCandidateField(Properties properties, String prefix) {
            return properties.stringPropertyNames().stream().anyMatch(key -> key.startsWith(prefix));
        }

        private static boolean isPreferredCandidateBlocker(String currentBlocker, String candidateBlocker) {
            if (candidateBlocker == null || candidateBlocker.isBlank()) {
                return false;
            }
            if ("no-candidates".equals(currentBlocker)) {
                return true;
            }
            return "none".equals(currentBlocker) && !"none".equals(candidateBlocker);
        }

        private static String optimizedArtifactCandidateStatus(int count, int readyCount, int blockedCount) {
            if (count <= 0) {
                return "not-recorded";
            }
            if (readyCount > 0 && blockedCount > 0) {
                return "mixed";
            }
            return blockedCount > 0 ? "blocked" : "candidate-ready";
        }

        private record CandidateEvidence(
                String status,
                int count,
                int readyCount,
                int blockedCount,
                int selectionReadyCount,
                int selectionAppliedCount,
                int selectedIrReplacementCount,
                int mutationAllowedCount,
                String firstBlocker,
                String selectionFirstBlocker
        ) {
            private CandidateEvidence {
                status = normalize(status, "not-recorded");
                count = Math.max(0, count);
                readyCount = Math.max(0, readyCount);
                blockedCount = Math.max(0, blockedCount);
                selectionReadyCount = Math.max(0, selectionReadyCount);
                selectionAppliedCount = Math.max(0, selectionAppliedCount);
                selectedIrReplacementCount = Math.max(0, selectedIrReplacementCount);
                mutationAllowedCount = Math.max(0, mutationAllowedCount);
                firstBlocker = normalize(firstBlocker, "no-candidates");
                selectionFirstBlocker = normalize(selectionFirstBlocker, "no-candidates");
            }
        }

        private static Map<String, Integer> parseProviderCounts(Properties properties) {
            LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
            int count = parseInt(properties.getProperty("pass.count"), 0);
            for (int index = 0; index < count; index++) {
                String provider = properties.getProperty("pass." + index + ".passVersion", "");
                if (!provider.isBlank()) {
                    counts.merge(provider, 1, Integer::sum);
                }
            }
            return counts;
        }

        private static Map<String, Integer> normalizeProviderCounts(Map<String, Integer> values) {
            if (values == null || values.isEmpty()) {
                return Map.of();
            }
            LinkedHashMap<String, Integer> normalized = new LinkedHashMap<>();
            values.forEach((provider, count) -> {
                if (provider != null && !provider.isBlank() && count != null && count > 0) {
                    normalized.put(provider, count);
                }
            });
            return java.util.Collections.unmodifiableMap(normalized);
        }

        private static Map<String, String> copyProperties(Properties properties) {
            if (properties == null || properties.isEmpty()) {
                return Map.of();
            }
            LinkedHashMap<String, String> copied = new LinkedHashMap<>();
            for (String name : properties.stringPropertyNames()) {
                String value = properties.getProperty(name);
                if (name != null && !name.isBlank() && value != null) {
                    copied.put(name, value);
                }
            }
            return java.util.Collections.unmodifiableMap(copied);
        }

        private static Map<String, String> normalizeEvidenceProperties(Map<String, String> values) {
            if (values == null || values.isEmpty()) {
                return Map.of();
            }
            LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    normalized.put(key, value);
                }
            });
            return java.util.Collections.unmodifiableMap(normalized);
        }
    }
}
