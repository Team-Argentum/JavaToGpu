package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeCompileArtifactDumper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/** Command-line entrypoint for validating fail-closed runtime IR optimizer evidence guardrails. */
public final class OpenClRuntimeIrOptimizerEvidenceValidatorCli {

    private static final String ARTIFACT_FILE_NAME =
            GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT;

    private OpenClRuntimeIrOptimizerEvidenceValidatorCli() {
    }

    public static void main(String[] args) throws IOException {
        CliArguments cliArguments = parseArguments(args);
        if (cliArguments.artifactPath().isEmpty()) {
            throw new IllegalArgumentException(
                    "Expected runtime IR optimizer evidence file or artifact directory"
            );
        }
        Path artifactPath = cliArguments.artifactPath().orElseThrow();
        List<Path> artifacts = evidenceArtifacts(artifactPath);
        if (artifacts.isEmpty()) {
            if (cliArguments.allowMissing()) {
                System.out.println("Runtime IR optimizer evidence not recorded under " + artifactPath
                        + "; optional optimizer evidence validation skipped.");
                return;
            }
            throw new IllegalStateException("Missing runtime IR optimizer evidence artifact under " + artifactPath);
        }

        int recorded = 0;
        int notRequired = 0;
        int pendingManualReview = 0;
        for (Path artifact : artifacts) {
            Properties properties = loadProperties(artifact);
            String status = properties.getProperty("status", "unknown");
            if (!"recorded".equals(status)) {
                throw new IllegalStateException("Runtime IR optimizer evidence must be recorded for "
                        + artifact + ": status=" + status);
            }
            validateGuardrails(artifact, properties, cliArguments.allowExperimentalApply());
            String reviewPackageStatus = properties.getProperty("reviewPackage.status", "not-recorded");
            if ("not-required".equals(reviewPackageStatus)) {
                notRequired++;
            } else if ("pending-manual-review".equals(reviewPackageStatus)) {
                pendingManualReview++;
            }
            recorded++;
        }

        System.out.println("Runtime IR optimizer evidence OK: recorded=" + recorded
                + ", reviewPackage.notRequired=" + notRequired
                + ", reviewPackage.pendingManualReview=" + pendingManualReview
                + ", experimentalApply.allowed=" + cliArguments.allowExperimentalApply());
    }

    private static CliArguments parseArguments(String[] args) {
        if (args == null || args.length == 0 || args.length > 3) {
            return new CliArguments(java.util.Optional.empty(), false, false);
        }
        Path artifactPath = null;
        boolean allowExperimentalApply = false;
        boolean allowMissing = false;
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            if ("--allow-experimental-apply".equals(arg)) {
                allowExperimentalApply = true;
                continue;
            }
            if ("--allow-missing".equals(arg)) {
                allowMissing = true;
                continue;
            }
            if (artifactPath != null) {
                return new CliArguments(java.util.Optional.empty(), allowExperimentalApply, allowMissing);
            }
            artifactPath = Path.of(arg);
        }
        return new CliArguments(java.util.Optional.ofNullable(artifactPath), allowExperimentalApply, allowMissing);
    }

    private static List<Path> evidenceArtifacts(Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            return ARTIFACT_FILE_NAME.equals(path.getFileName().toString()) ? List.of(path) : List.of();
        }
        if (!Files.isDirectory(path)) {
            return List.of();
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(path)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(candidate -> ARTIFACT_FILE_NAME.equals(candidate.getFileName().toString()))
                    .sorted()
                    .toList();
        }
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        }
        return properties;
    }

    private static void validateGuardrails(
            Path artifact,
            Properties properties,
            boolean allowExperimentalApply
    ) {
        requireValue(artifact, properties, "runtimeEquivalenceReview.productionMutation", "disabled");
        requireValue(artifact, properties, "runtimeEquivalenceReview.selectedIrReplacement", "disabled");
        requireValue(artifact, properties, "runtimeEquivalenceReview.manualReviewOnly", "true");
        requireValue(artifact, properties, "reviewPackage.manualReviewOnly", "true");
        requireValue(artifact, properties, "reviewPackage.productionMutation", "disabled");
        requireValue(artifact, properties, "reviewPackage.selectedIrReplacement", "disabled");
        requireValue(artifact, properties, "reviewPackage.originalIrRequired", "true");
        requireValue(artifact, properties, "reviewPackage.optimizedIrRequired", "true");
        requireValue(artifact, properties, "reviewPackage.proofSummaryRequired", "true");
        validateOptimizedArtifactCandidateGuardrails(artifact, properties, allowExperimentalApply);
        validateSafeLocalCseMaterializationEvidence(artifact, properties);
        validateMadFmaMaterializationEvidence(artifact, properties);
        validateIntrinsicMaterializationEvidence(
                artifact,
                properties,
                "clampMaterialization",
                "clamp",
                IntrinsicMaterializationKind.CLAMP
        );
        validateIntrinsicMaterializationEvidence(
                artifact,
                properties,
                "stepMaterialization",
                "step",
                IntrinsicMaterializationKind.STEP
        );
        validateIntrinsicMaterializationEvidence(
                artifact,
                properties,
                "mixMaterialization",
                "mix",
                IntrinsicMaterializationKind.MIX
        );
        validateLoopVectorizationMaterializationEvidence(artifact, properties);
        validateTypedDeadCodeMaterializationEvidence(artifact, properties);

        String reviewPackageStatus = requirePresent(artifact, properties, "reviewPackage.status");
        boolean required = parseBoolean(requirePresent(artifact, properties, "reviewPackage.required"));
        boolean complete = parseBoolean(requirePresent(artifact, properties, "reviewPackage.complete"));
        String firstBlocker = requirePresent(artifact, properties, "reviewPackage.firstBlocker");
        int proposalPassCount = parseNonNegativeInt(artifact, properties, "reviewPackage.proposalPass.count");
        int pendingApprovalCount = parseNonNegativeInt(artifact, properties, "reviewPackage.pendingApproval.count");

        if (!List.of("not-required", "pending-manual-review").contains(reviewPackageStatus)) {
            throw new IllegalStateException("Unsupported review package status for " + artifact
                    + ": " + reviewPackageStatus);
        }
        if (complete) {
            throw new IllegalStateException("Runtime IR optimizer review packages must remain incomplete/manual-review-only for "
                    + artifact);
        }
        if (!required && !"not-required".equals(reviewPackageStatus)) {
            throw new IllegalStateException("Non-required review package must use not-required status for " + artifact
                    + ": status=" + reviewPackageStatus);
        }
        if (required && !"pending-manual-review".equals(reviewPackageStatus)) {
            throw new IllegalStateException("Required review package must stay pending-manual-review for " + artifact
                    + ": status=" + reviewPackageStatus);
        }
        if (required && "none".equals(firstBlocker)) {
            throw new IllegalStateException("Required review package must expose a first blocker for " + artifact);
        }
        if (!required && !"review-package-not-required".equals(firstBlocker)) {
            throw new IllegalStateException("Non-required review package must expose review-package-not-required for "
                    + artifact + ": " + firstBlocker);
        }
        if (pendingApprovalCount > proposalPassCount) {
            throw new IllegalStateException("Pending approval count exceeds proposal pass count for " + artifact
                    + ": pending=" + pendingApprovalCount + ", proposalPasses=" + proposalPassCount);
        }
    }

    private static void validateSafeLocalCseMaterializationEvidence(Path artifact, Properties properties) {
        if (!properties.containsKey("safeLocalCseMaterialization.pass.count")
                && !properties.containsKey("safeLocalCseMaterialization.status")) {
            return;
        }

        int passCount = parseNonNegativeInt(artifact, properties, "safeLocalCseMaterialization.pass.count");
        int localBindingCount = parseNonNegativeInt(artifact, properties, "safeLocalCseMaterialization.localBinding.count");
        int introducedTemporaryCount = parseNonNegativeInt(
                artifact,
                properties,
                "safeLocalCseMaterialization.introducedTemporary.count"
        );
        parseNonNegativeInt(artifact, properties, "safeLocalCseMaterialization.candidate.count");
        int transformedNodeCount = parseNonNegativeInt(
                artifact,
                properties,
                "safeLocalCseMaterialization.transformedNode.count"
        );
        int bodyTextReplacementCount = parseNonNegativeInt(
                artifact,
                properties,
                "safeLocalCseMaterialization.bodyTextReplacement.count"
        );
        parseNonNegativeInt(artifact, properties, "safeLocalCseMaterialization.changedMethodBody.count");
        parseNonNegativeInt(artifact, properties, "safeLocalCseMaterialization.fixedPoint.pass.count");
        int skippedControlFlowBoundaryCount = parseNonNegativeInt(
                artifact,
                properties,
                "safeLocalCseMaterialization.skipped.controlFlowBoundary.count"
        );
        int skippedUnsupportedOperatorCount = parseNonNegativeInt(
                artifact,
                properties,
                "safeLocalCseMaterialization.skipped.unsupportedOperator.count"
        );
        int skippedImpureOperandCount = parseNonNegativeInt(
                artifact,
                properties,
                "safeLocalCseMaterialization.skipped.impureOperand.count"
        );
        int skippedBodyTextPatternMissingCount = parseNonNegativeInt(
                artifact,
                properties,
                "safeLocalCseMaterialization.skipped.bodyTextPatternMissing.count"
        );
        int payloadPresentCount = parseNonNegativeInt(
                artifact,
                properties,
                "safeLocalCseMaterialization.runtimeEquivalencePayloadPresent.count"
        );
        int payloadPassedCount = parseNonNegativeInt(
                artifact,
                properties,
                "safeLocalCseMaterialization.runtimeEquivalencePassed.count"
        );
        boolean payloadRequired = parseBoolean(requirePresent(
                artifact,
                properties,
                "safeLocalCseMaterialization.runtimeEquivalencePayloadRequired"
        ));
        boolean approvalRequired = parseBoolean(requirePresent(
                artifact,
                properties,
                "safeLocalCseMaterialization.approvalRequiredBeforeProduction"
        ));
        boolean dominanceProven = parseBoolean(requirePresent(
                artifact,
                properties,
                "safeLocalCseMaterialization.dominanceProven"
        ));
        boolean sideEffectFreedomProven = parseBoolean(requirePresent(
                artifact,
                properties,
                "safeLocalCseMaterialization.sideEffectFreedomProven"
        ));
        String status = requirePresent(artifact, properties, "safeLocalCseMaterialization.status");
        String firstBlocker = requirePresent(artifact, properties, "safeLocalCseMaterialization.firstBlocker");

        validateMaterializationStatus(artifact, "safe-local-CSE", status);
        if (passCount == 0 && (!"not-recorded".equals(status) || !"not-recorded".equals(firstBlocker))) {
            throw new IllegalStateException("Missing safe-local-CSE materialization evidence must use not-recorded state for "
                    + artifact + ": status=" + status + ", firstBlocker=" + firstBlocker);
        }
        if (passCount > 0 && "not-recorded".equals(status)) {
            throw new IllegalStateException("Recorded safe-local-CSE materialization evidence cannot use not-recorded status for "
                    + artifact);
        }
        if (payloadPassedCount > payloadPresentCount) {
            throw new IllegalStateException("safe-local-CSE runtime-equivalence passed count exceeds present count for "
                    + artifact + ": passed=" + payloadPassedCount + ", present=" + payloadPresentCount);
        }
        if (transformedNodeCount > 0
                && (localBindingCount + introducedTemporaryCount <= 0 || bodyTextReplacementCount <= 0)) {
            throw new IllegalStateException("safe-local-CSE materialized nodes must have existing or introduced local bindings and text replacements for "
                    + artifact);
        }
        if (transformedNodeCount > 0 && (!payloadRequired || !approvalRequired)) {
            throw new IllegalStateException("safe-local-CSE materialized nodes must require runtime-equivalence and approval for "
                    + artifact);
        }

        int skippedCount = skippedControlFlowBoundaryCount
                + skippedUnsupportedOperatorCount
                + skippedImpureOperandCount
                + skippedBodyTextPatternMissingCount;
        boolean proofClean = dominanceProven && sideEffectFreedomProven;
        switch (status) {
            case "no-candidates" -> {
                if (transformedNodeCount > 0 || skippedCount > 0 || !"no-materialized-candidates".equals(firstBlocker)) {
                    throw new IllegalStateException("safe-local-CSE no-candidates evidence is inconsistent for " + artifact);
                }
            }
            case "blocked" -> {
                if (transformedNodeCount <= 0 && skippedCount <= 0) {
                    throw new IllegalStateException("safe-local-CSE blocked evidence must expose a skipped blocker for " + artifact);
                }
                if ("none".equals(firstBlocker) || "not-recorded".equals(firstBlocker)) {
                    throw new IllegalStateException("safe-local-CSE blocked evidence must expose a first blocker for " + artifact);
                }
            }
            case "pending-runtime-equivalence" -> {
                if (transformedNodeCount <= 0 || payloadPresentCount > 0 || !proofClean
                        || !"runtime-equivalence-payload-not-recorded".equals(firstBlocker)) {
                    throw new IllegalStateException("safe-local-CSE pending runtime-equivalence evidence is inconsistent for "
                            + artifact);
                }
            }
            case "runtime-equivalence-not-passed" -> {
                if (transformedNodeCount <= 0 || payloadPresentCount <= 0
                        || payloadPassedCount >= payloadPresentCount || !proofClean
                        || !"runtime-equivalence-not-passed".equals(firstBlocker)) {
                    throw new IllegalStateException("safe-local-CSE failed runtime-equivalence evidence is inconsistent for "
                            + artifact);
                }
            }
            case "review-ready" -> {
                if (transformedNodeCount <= 0 || payloadPresentCount <= 0
                        || payloadPassedCount < payloadPresentCount || !proofClean
                        || !"none".equals(firstBlocker)) {
                    throw new IllegalStateException("safe-local-CSE review-ready evidence must include proofs and passed payloads for "
                            + artifact);
                }
            }
            default -> {
            }
        }
    }

    private static void validateOptimizedArtifactCandidateGuardrails(
            Path artifact,
            Properties properties,
            boolean allowExperimentalApply
    ) {
        String status = requirePresent(artifact, properties, "optimizedArtifactCandidate.status");
        int count = parseNonNegativeInt(artifact, properties, "optimizedArtifactCandidate.count");
        int readyCount = parseNonNegativeInt(artifact, properties, "optimizedArtifactCandidate.ready.count");
        int blockedCount = parseNonNegativeInt(artifact, properties, "optimizedArtifactCandidate.blocked.count");
        int selectionReadyCount = parseNonNegativeInt(
                artifact,
                properties,
                "optimizedArtifactCandidate.selectionReady.count"
        );
        int selectionAppliedCount = parseNonNegativeInt(
                artifact,
                properties,
                "optimizedArtifactCandidate.selectionApplied.count"
        );
        int selectedIrReplacementCount = parseNonNegativeInt(
                artifact,
                properties,
                "optimizedArtifactCandidate.selectedIrReplacement.count"
        );
        parseNonNegativeInt(artifact, properties, "optimizedArtifactCandidate.mutationAllowed.count");
        int experimentalApplySelectedCount = parseOptionalNonNegativeInt(
                artifact,
                properties,
                "experimentalApply.selected.count",
                0
        );
        boolean experimentalApplyRequested = parseBoolean(properties.getProperty("experimentalApply.requested"));
        boolean experimentalApplyEnabled = parseBoolean(properties.getProperty("experimentalApply.enabled"));
        String firstBlocker = requirePresent(artifact, properties, "optimizedArtifactCandidate.firstBlocker");
        String selectionFirstBlocker = requirePresent(
                artifact,
                properties,
                "optimizedArtifactCandidate.selectionFirstBlocker"
        );
        if (!allowExperimentalApply) {
            requireValue(artifact, properties, "optimizedArtifactCandidate.selectionApplied", "false");
            requireValue(artifact, properties, "optimizedArtifactCandidate.selectedIrReplacement", "false");
        }

        if (!List.of("not-recorded", "candidate-ready", "blocked", "mixed").contains(status)) {
            throw new IllegalStateException("Unsupported optimized artifact candidate status for " + artifact
                    + ": " + status);
        }
        if (readyCount + blockedCount > count) {
            throw new IllegalStateException("Optimized artifact candidate ready/blocked counts exceed total for "
                    + artifact + ": ready=" + readyCount + ", blocked=" + blockedCount + ", total=" + count);
        }
        if (count == 0 && !"not-recorded".equals(status)) {
            throw new IllegalStateException("Missing optimized artifact candidates must use not-recorded status for "
                    + artifact + ": status=" + status);
        }
        if (count == 0 && (!"no-candidates".equals(firstBlocker)
                || !"no-candidates".equals(selectionFirstBlocker))) {
            throw new IllegalStateException("Missing optimized artifact candidates must expose no-candidates blockers for "
                    + artifact);
        }
        if (count > 0 && "not-recorded".equals(status)) {
            throw new IllegalStateException("Recorded optimized artifact candidates cannot use not-recorded status for "
                    + artifact);
        }
        if (count > 0
                && !allowExperimentalApply
                && ("none".equals(selectionFirstBlocker) || "no-candidates".equals(selectionFirstBlocker))) {
            throw new IllegalStateException("Optimized artifact candidates must keep selection blocked for "
                    + artifact + ": selectionFirstBlocker=" + selectionFirstBlocker);
        }
        if (!allowExperimentalApply
                && (selectionReadyCount > 0 || selectionAppliedCount > 0 || selectedIrReplacementCount > 0)) {
            throw new IllegalStateException("Optimized artifact candidates must not become runtime selection for "
                    + artifact + ": selectionReady=" + selectionReadyCount
                    + ", selectionApplied=" + selectionAppliedCount
                    + ", selectedIrReplacement=" + selectedIrReplacementCount);
        }
        if (allowExperimentalApply
                && (selectionReadyCount > 0 || selectionAppliedCount > 0 || selectedIrReplacementCount > 0)) {
            if (!experimentalApplyRequested || !experimentalApplyEnabled || experimentalApplySelectedCount <= 0) {
                throw new IllegalStateException("Experimental optimized artifact selection requires explicit apply evidence for "
                        + artifact);
            }
            if (selectionAppliedCount != selectedIrReplacementCount) {
                throw new IllegalStateException("Experimental optimized artifact selection must keep selection/replacement counts aligned for "
                        + artifact + ": selectionApplied=" + selectionAppliedCount
                        + ", selectedIrReplacement=" + selectedIrReplacementCount);
            }
            if (!"none".equals(selectionFirstBlocker)) {
                throw new IllegalStateException("Experimental optimized artifact selection must clear selection blocker for "
                        + artifact + ": selectionFirstBlocker=" + selectionFirstBlocker);
            }
        }
    }

    private static void validateMadFmaMaterializationEvidence(Path artifact, Properties properties) {
        if (!properties.containsKey("madFmaMaterialization.pass.count")
                && !properties.containsKey("madFmaMaterialization.status")) {
            return;
        }

        int passCount = parseNonNegativeInt(artifact, properties, "madFmaMaterialization.pass.count");
        parseNonNegativeInt(artifact, properties, "madFmaMaterialization.candidate.count");
        int transformedNodeCount = parseNonNegativeInt(
                artifact,
                properties,
                "madFmaMaterialization.transformedNode.count"
        );
        int bodyTextReplacementCount = parseNonNegativeInt(
                artifact,
                properties,
                "madFmaMaterialization.bodyTextReplacement.count"
        );
        parseNonNegativeInt(artifact, properties, "madFmaMaterialization.changedMethodBody.count");
        parseNonNegativeInt(artifact, properties, "madFmaMaterialization.fixedPoint.pass.count");
        int skippedFastMathPolicyCount = parseNonNegativeInt(
                artifact,
                properties,
                "madFmaMaterialization.skipped.fastMathPolicy.count"
        );
        int skippedBodyTextPatternMissingCount = parseNonNegativeInt(
                artifact,
                properties,
                "madFmaMaterialization.skipped.bodyTextPatternMissing.count"
        );
        int payloadPresentCount = parseNonNegativeInt(
                artifact,
                properties,
                "madFmaMaterialization.runtimeEquivalencePayloadPresent.count"
        );
        int payloadPassedCount = parseNonNegativeInt(
                artifact,
                properties,
                "madFmaMaterialization.runtimeEquivalencePassed.count"
        );
        boolean payloadRequired = parseBoolean(requirePresent(
                artifact,
                properties,
                "madFmaMaterialization.runtimeEquivalencePayloadRequired"
        ));
        boolean approvalRequired = parseBoolean(requirePresent(
                artifact,
                properties,
                "madFmaMaterialization.approvalRequiredBeforeProduction"
        ));
        boolean fastMathAllowed = parseBoolean(requirePresent(
                artifact,
                properties,
                "madFmaMaterialization.fastMathAllowed"
        ));
        String status = requirePresent(artifact, properties, "madFmaMaterialization.status");
        String firstBlocker = requirePresent(artifact, properties, "madFmaMaterialization.firstBlocker");

        validateMaterializationStatus(artifact, "mad/FMA", status);
        if (passCount == 0 && (!"not-recorded".equals(status) || !"not-recorded".equals(firstBlocker))) {
            throw new IllegalStateException("Missing mad/FMA materialization evidence must use not-recorded state for "
                    + artifact + ": status=" + status + ", firstBlocker=" + firstBlocker);
        }
        if (passCount > 0 && "not-recorded".equals(status)) {
            throw new IllegalStateException("Recorded mad/FMA materialization evidence cannot use not-recorded status for "
                    + artifact);
        }
        if (payloadPassedCount > payloadPresentCount) {
            throw new IllegalStateException("mad/FMA runtime-equivalence passed count exceeds present count for "
                    + artifact + ": passed=" + payloadPassedCount + ", present=" + payloadPresentCount);
        }
        if (transformedNodeCount > 0 && bodyTextReplacementCount <= 0) {
            throw new IllegalStateException("mad/FMA materialized nodes must have matching body text replacements for "
                    + artifact);
        }
        if (transformedNodeCount > 0 && (!payloadRequired || !approvalRequired)) {
            throw new IllegalStateException("mad/FMA materialized nodes must require runtime-equivalence and approval for "
                    + artifact);
        }
        if (transformedNodeCount > 0 && !fastMathAllowed) {
            throw new IllegalStateException("mad/FMA materialization requires fast-math policy evidence for " + artifact);
        }

        int skippedCount = skippedFastMathPolicyCount + skippedBodyTextPatternMissingCount;
        switch (status) {
            case "no-candidates" -> {
                if (transformedNodeCount > 0 || skippedCount > 0 || !"no-materialized-candidates".equals(firstBlocker)) {
                    throw new IllegalStateException("mad/FMA no-candidates evidence is inconsistent for " + artifact);
                }
            }
            case "blocked" -> {
                if (transformedNodeCount <= 0 && skippedCount <= 0) {
                    throw new IllegalStateException("mad/FMA blocked evidence must expose a skipped blocker for " + artifact);
                }
                if ("none".equals(firstBlocker) || "not-recorded".equals(firstBlocker)) {
                    throw new IllegalStateException("mad/FMA blocked evidence must expose a first blocker for " + artifact);
                }
            }
            case "pending-runtime-equivalence" -> {
                if (transformedNodeCount <= 0 || payloadPresentCount > 0
                        || !"runtime-equivalence-payload-not-recorded".equals(firstBlocker)) {
                    throw new IllegalStateException("mad/FMA pending runtime-equivalence evidence is inconsistent for "
                            + artifact);
                }
            }
            case "runtime-equivalence-not-passed" -> {
                if (transformedNodeCount <= 0 || payloadPresentCount <= 0
                        || payloadPassedCount >= payloadPresentCount
                        || !"runtime-equivalence-not-passed".equals(firstBlocker)) {
                    throw new IllegalStateException("mad/FMA failed runtime-equivalence evidence is inconsistent for "
                            + artifact);
                }
            }
            case "review-ready" -> {
                if (transformedNodeCount <= 0 || payloadPresentCount <= 0
                        || payloadPassedCount < payloadPresentCount
                        || !"none".equals(firstBlocker)) {
                    throw new IllegalStateException("mad/FMA review-ready evidence must include passed payloads for "
                            + artifact);
                }
            }
            default -> {
            }
        }
    }

    private static void validateIntrinsicMaterializationEvidence(
            Path artifact,
            Properties properties,
            String prefix,
            String family,
            IntrinsicMaterializationKind kind
    ) {
        if (!properties.containsKey(prefix + ".pass.count")
                && !properties.containsKey(prefix + ".status")) {
            return;
        }

        int passCount = parseNonNegativeInt(artifact, properties, prefix + ".pass.count");
        parseNonNegativeInt(artifact, properties, prefix + ".candidate.count");
        int transformedNodeCount = parseNonNegativeInt(artifact, properties, prefix + ".transformedNode.count");
        int bodyTextReplacementCount = parseNonNegativeInt(
                artifact,
                properties,
                prefix + ".bodyTextReplacement.count"
        );
        parseNonNegativeInt(artifact, properties, prefix + ".changedMethodBody.count");
        parseNonNegativeInt(artifact, properties, prefix + ".fixedPoint.pass.count");
        int skippedCount = parseNonNegativeInt(artifact, properties, prefix + ".skipped.typedBodyMissing.count")
                + parseNonNegativeInt(artifact, properties, prefix + ".skipped.unsupportedFormat.count")
                + parseNonNegativeInt(artifact, properties, prefix + ".skipped.fastMathPolicy.count")
                + parseNonNegativeInt(artifact, properties, prefix + ".skipped.missingChildReference.count")
                + parseNonNegativeInt(artifact, properties, prefix + ".skipped.unsupportedShape.count")
                + parseNonNegativeInt(artifact, properties, prefix + ".skipped.bodyTextPatternMissing.count");
        int payloadPresentCount = parseNonNegativeInt(
                artifact,
                properties,
                prefix + ".runtimeEquivalencePayloadPresent.count"
        );
        int payloadPassedCount = parseNonNegativeInt(
                artifact,
                properties,
                prefix + ".runtimeEquivalencePassed.count"
        );
        boolean runtimeEquivalenceRequired = parseBoolean(requirePresent(
                artifact,
                properties,
                prefix + ".runtimeEquivalenceRequiredBeforeSelection"
        ));
        boolean payloadRequired = parseBoolean(requirePresent(
                artifact,
                properties,
                prefix + ".runtimeEquivalencePayloadRequired"
        ));
        boolean approvalRequired = parseBoolean(requirePresent(
                artifact,
                properties,
                prefix + ".approvalRequiredBeforeProduction"
        ));
        String status = requirePresent(artifact, properties, prefix + ".status");
        String firstBlocker = requirePresent(artifact, properties, prefix + ".firstBlocker");

        validateMaterializationStatus(artifact, family, status);
        if (passCount == 0 && (!"not-recorded".equals(status) || !"not-recorded".equals(firstBlocker))) {
            throw new IllegalStateException("Missing " + family
                    + " materialization evidence must use not-recorded state for "
                    + artifact + ": status=" + status + ", firstBlocker=" + firstBlocker);
        }
        if (passCount > 0 && "not-recorded".equals(status)) {
            throw new IllegalStateException("Recorded " + family
                    + " materialization evidence cannot use not-recorded status for " + artifact);
        }
        if (payloadPassedCount > payloadPresentCount) {
            throw new IllegalStateException(family + " runtime-equivalence passed count exceeds present count for "
                    + artifact + ": passed=" + payloadPassedCount + ", present=" + payloadPresentCount);
        }
        if (transformedNodeCount > 0 && bodyTextReplacementCount <= 0) {
            throw new IllegalStateException(family
                    + " materialized nodes must have matching body text replacements for " + artifact);
        }
        if (transformedNodeCount > 0 && (!runtimeEquivalenceRequired || !payloadRequired || !approvalRequired)) {
            throw new IllegalStateException(family
                    + " materialized nodes must require runtime-equivalence payloads and approval for " + artifact);
        }
        validateIntrinsicMaterializationSafety(artifact, properties, prefix, family, kind, transformedNodeCount);

        switch (status) {
            case "no-candidates" -> {
                if (transformedNodeCount > 0 || skippedCount > 0 || !"no-materialized-candidates".equals(firstBlocker)) {
                    throw new IllegalStateException(family + " no-candidates evidence is inconsistent for " + artifact);
                }
            }
            case "blocked" -> {
                if ("none".equals(firstBlocker) || "not-recorded".equals(firstBlocker)) {
                    throw new IllegalStateException(family + " blocked evidence must expose a first blocker for "
                            + artifact);
                }
            }
            case "pending-runtime-equivalence" -> {
                if (transformedNodeCount <= 0 || payloadPresentCount > 0
                        || !"runtime-equivalence-payload-not-recorded".equals(firstBlocker)) {
                    throw new IllegalStateException(family
                            + " pending runtime-equivalence evidence is inconsistent for " + artifact);
                }
            }
            case "runtime-equivalence-not-passed" -> {
                if (transformedNodeCount <= 0 || payloadPresentCount <= 0
                        || payloadPassedCount >= payloadPresentCount
                        || !"runtime-equivalence-not-passed".equals(firstBlocker)) {
                    throw new IllegalStateException(family
                            + " failed runtime-equivalence evidence is inconsistent for " + artifact);
                }
            }
            case "review-ready" -> {
                if (transformedNodeCount <= 0 || payloadPresentCount <= 0
                        || payloadPassedCount < payloadPresentCount
                        || !"none".equals(firstBlocker)) {
                    throw new IllegalStateException(family
                            + " review-ready evidence must include passed payloads for " + artifact);
                }
            }
            default -> {
            }
        }
    }

    private static void validateIntrinsicMaterializationSafety(
            Path artifact,
            Properties properties,
            String prefix,
            String family,
            IntrinsicMaterializationKind kind,
            int transformedNodeCount
    ) {
        if (transformedNodeCount <= 0) {
            return;
        }
        switch (kind) {
            case CLAMP -> validateClampMaterializationSafety(artifact, properties, prefix, family);
            case STEP -> validateStepMaterializationSafety(artifact, properties, prefix, family, transformedNodeCount);
            case MIX -> validateMixMaterializationSafety(artifact, properties, prefix, family, transformedNodeCount);
        }
    }

    private static void validateClampMaterializationSafety(
            Path artifact,
            Properties properties,
            String prefix,
            String family
    ) {
        boolean strictFloatPreserved = parseBoolean(requirePresent(
                artifact,
                properties,
                prefix + ".strictFloatPreserved"
        ));
        boolean argumentOrderPreserved = parseBoolean(requirePresent(
                artifact,
                properties,
                prefix + ".argumentOrderPreserved"
        ));
        boolean fastMathRequired = parseBoolean(requirePresent(artifact, properties, prefix + ".fastMathRequired"));
        if (!strictFloatPreserved || !argumentOrderPreserved || fastMathRequired) {
            throw new IllegalStateException(family
                    + " materialization must preserve strict float behavior and argument order without fast-math for "
                    + artifact);
        }
    }

    private static void validateStepMaterializationSafety(
            Path artifact,
            Properties properties,
            String prefix,
            String family,
            int transformedNodeCount
    ) {
        int directStepCount = parseNonNegativeInt(artifact, properties, prefix + ".directStep.count");
        int invertedStepCount = parseNonNegativeInt(artifact, properties, prefix + ".invertedStep.count");
        boolean strictFloatPreserved = parseBoolean(requirePresent(
                artifact,
                properties,
                prefix + ".strictFloatPreserved"
        ));
        boolean strictComparisonPreserved = parseBoolean(requirePresent(
                artifact,
                properties,
                prefix + ".strictComparisonPreserved"
        ));
        boolean equalityBehaviorPreserved = parseBoolean(requirePresent(
                artifact,
                properties,
                prefix + ".equalityBehaviorPreserved"
        ));
        boolean nanComparisonPreserved = parseBoolean(requirePresent(
                artifact,
                properties,
                prefix + ".nanComparisonPreserved"
        ));
        boolean fastMathRequired = parseBoolean(requirePresent(artifact, properties, prefix + ".fastMathRequired"));
        if (directStepCount + invertedStepCount != transformedNodeCount) {
            throw new IllegalStateException(family
                    + " materialized nodes must be accounted as direct or inverted step expressions for " + artifact);
        }
        if (!strictFloatPreserved || !strictComparisonPreserved
                || !equalityBehaviorPreserved || !nanComparisonPreserved || fastMathRequired) {
            throw new IllegalStateException(family
                    + " materialization must preserve comparison/equality/NaN behavior without fast-math for "
                    + artifact);
        }
    }

    private static void validateMixMaterializationSafety(
            Path artifact,
            Properties properties,
            String prefix,
            String family,
            int transformedNodeCount
    ) {
        int canonicalMixCount = parseNonNegativeInt(artifact, properties, prefix + ".canonicalMix.count");
        int expandedMixCount = parseNonNegativeInt(artifact, properties, prefix + ".expandedMix.count");
        int madExpandedMixCount = parseNonNegativeInt(artifact, properties, prefix + ".madExpandedMix.count");
        boolean fastMathAllowed = parseBoolean(requirePresent(artifact, properties, prefix + ".fastMathAllowed"));
        boolean fastMathRequired = parseBoolean(requirePresent(artifact, properties, prefix + ".fastMathRequired"));
        boolean strictFloatPreserved = parseBoolean(requirePresent(
                artifact,
                properties,
                prefix + ".strictFloatPreserved"
        ));
        boolean algebraicReassociationRequired = parseBoolean(requirePresent(
                artifact,
                properties,
                prefix + ".algebraicReassociationRequired"
        ));
        boolean mixArgumentOrderPreserved = parseBoolean(requirePresent(
                artifact,
                properties,
                prefix + ".mixArgumentOrderPreserved"
        ));
        boolean expandedRewrite = expandedMixCount > 0
                || madExpandedMixCount > 0
                || fastMathRequired
                || algebraicReassociationRequired;
        if (canonicalMixCount + expandedMixCount + madExpandedMixCount != transformedNodeCount) {
            throw new IllegalStateException(family
                    + " materialized nodes must be accounted as canonical/expanded/MAD-expanded mix expressions for "
                    + artifact);
        }
        if (!mixArgumentOrderPreserved) {
            throw new IllegalStateException(family + " materialization must preserve mix argument order for " + artifact);
        }
        if (expandedRewrite && (!fastMathAllowed || !fastMathRequired || !algebraicReassociationRequired)) {
            throw new IllegalStateException(family
                    + " expanded materialization must carry fast-math and reassociation evidence for " + artifact);
        }
        if (!expandedRewrite && (!strictFloatPreserved || fastMathRequired || algebraicReassociationRequired)) {
            throw new IllegalStateException(family
                    + " canonical materialization must preserve strict float behavior without fast-math for " + artifact);
        }
    }

    private static void validateLoopVectorizationMaterializationEvidence(Path artifact, Properties properties) {
        if (!properties.containsKey("loopVectorizationMaterialization.pass.count")
                && !properties.containsKey("loopVectorizationMaterialization.status")) {
            return;
        }

        int passCount = parseNonNegativeInt(artifact, properties, "loopVectorizationMaterialization.pass.count");
        parseNonNegativeInt(artifact, properties, "loopVectorizationMaterialization.candidate.count");
        int transformedLoopCount = parseNonNegativeInt(
                artifact,
                properties,
                "loopVectorizationMaterialization.transformedLoop.count"
        );
        int bodyTextReplacementCount = parseNonNegativeInt(
                artifact,
                properties,
                "loopVectorizationMaterialization.bodyTextReplacement.count"
        );
        parseNonNegativeInt(artifact, properties, "loopVectorizationMaterialization.changedMethodBody.count");
        int typedBodyMaterializedCount = parseNonNegativeInt(
                artifact,
                properties,
                "loopVectorizationMaterialization.typedBody.materialized.count"
        );
        int typedBodyInvalidatedCount = parseNonNegativeInt(
                artifact,
                properties,
                "loopVectorizationMaterialization.typedBody.invalidated.count"
        );
        int typedBodyRebuildAttemptedCount = parseNonNegativeInt(
                artifact,
                properties,
                "loopVectorizationMaterialization.typedBody.rebuild.attempted.count"
        );
        int typedBodyRebuildParsedCount = parseNonNegativeInt(
                artifact,
                properties,
                "loopVectorizationMaterialization.typedBody.rebuild.parsed.count"
        );
        int typedBodyRebuildBuiltCount = parseNonNegativeInt(
                artifact,
                properties,
                "loopVectorizationMaterialization.typedBody.rebuild.built.count"
        );
        int typedBodyRebuildGraphValidatedCount = parseNonNegativeInt(
                artifact,
                properties,
                "loopVectorizationMaterialization.typedBody.rebuild.graphValidated.count"
        );
        int typedBodyRebuildRejectedCount = parseNonNegativeInt(
                artifact,
                properties,
                "loopVectorizationMaterialization.typedBody.rebuild.rejected.count"
        );
        String typedBodyRebuildStatus = requirePresent(
                artifact,
                properties,
                "loopVectorizationMaterialization.typedBody.rebuild.status"
        );
        String typedBodyRebuildFirstBlocker = requirePresent(
                artifact,
                properties,
                "loopVectorizationMaterialization.typedBody.rebuild.firstBlocker"
        );
        int skippedLoopShapeCount = parseNonNegativeInt(
                artifact,
                properties,
                "loopVectorizationMaterialization.skipped.loopShape.count"
        );
        int skippedUnsupportedWidthCount = parseNonNegativeInt(
                artifact,
                properties,
                "loopVectorizationMaterialization.skipped.unsupportedWidth.count"
        );
        int skippedUnsafeLoadPatternCount = parseNonNegativeInt(
                artifact,
                properties,
                "loopVectorizationMaterialization.skipped.unsafeLoadPattern.count"
        );
        int payloadPresentCount = parseNonNegativeInt(
                artifact,
                properties,
                "loopVectorizationMaterialization.runtimeEquivalencePayloadPresent.count"
        );
        int payloadPassedCount = parseNonNegativeInt(
                artifact,
                properties,
                "loopVectorizationMaterialization.runtimeEquivalencePassed.count"
        );
        boolean payloadRequired = parseBoolean(requirePresent(
                artifact,
                properties,
                "loopVectorizationMaterialization.runtimeEquivalencePayloadRequired"
        ));
        boolean approvalRequired = parseBoolean(requirePresent(
                artifact,
                properties,
                "loopVectorizationMaterialization.approvalRequiredBeforeProduction"
        ));
        boolean loopTripCountProven = parseBoolean(requirePresent(
                artifact,
                properties,
                "loopVectorizationMaterialization.loopTripCountProven"
        ));
        boolean contiguousLoadProven = parseBoolean(requirePresent(
                artifact,
                properties,
                "loopVectorizationMaterialization.contiguousLoadProven"
        ));
        boolean orderedReductionPreserved = parseBoolean(requirePresent(
                artifact,
                properties,
                "loopVectorizationMaterialization.orderedReductionPreserved"
        ));
        String status = requirePresent(artifact, properties, "loopVectorizationMaterialization.status");
        String firstBlocker = requirePresent(artifact, properties, "loopVectorizationMaterialization.firstBlocker");

        validateMaterializationStatus(artifact, "loop-vectorization", status);
        if (passCount == 0 && (!"not-recorded".equals(status) || !"not-recorded".equals(firstBlocker))) {
            throw new IllegalStateException("Missing loop-vectorization materialization evidence must use not-recorded state for "
                    + artifact + ": status=" + status + ", firstBlocker=" + firstBlocker);
        }
        if (passCount > 0 && "not-recorded".equals(status)) {
            throw new IllegalStateException("Recorded loop-vectorization materialization evidence cannot use not-recorded status for "
                    + artifact);
        }
        if (payloadPassedCount > payloadPresentCount) {
            throw new IllegalStateException("loop-vectorization runtime-equivalence passed count exceeds present count for "
                    + artifact + ": passed=" + payloadPassedCount + ", present=" + payloadPresentCount);
        }
        if (transformedLoopCount > 0 && bodyTextReplacementCount <= 0) {
            throw new IllegalStateException("loop-vectorization transformed loops must have matching body text replacements for "
                    + artifact);
        }
        if (typedBodyRebuildParsedCount > typedBodyRebuildAttemptedCount
                || typedBodyRebuildBuiltCount > typedBodyRebuildParsedCount
                || typedBodyRebuildGraphValidatedCount > typedBodyRebuildBuiltCount
                || typedBodyRebuildRejectedCount > typedBodyRebuildAttemptedCount) {
            throw new IllegalStateException("loop-vectorization typed-body rebuild counters are inconsistent for " + artifact);
        }
        if (transformedLoopCount > 0 && typedBodyRebuildAttemptedCount <= 0) {
            throw new IllegalStateException("loop-vectorization transformed loops must attempt typed-body rebuild for "
                    + artifact);
        }
        switch (typedBodyRebuildStatus) {
            case "not-attempted" -> {
                if (typedBodyRebuildAttemptedCount > 0 || !"not-attempted".equals(typedBodyRebuildFirstBlocker)) {
                    throw new IllegalStateException("loop-vectorization typed-body rebuild not-attempted evidence is inconsistent for "
                            + artifact);
                }
            }
            case "validated" -> {
                if (typedBodyRebuildAttemptedCount <= 0 || typedBodyRebuildRejectedCount > 0
                        || typedBodyMaterializedCount <= 0 || !"none".equals(typedBodyRebuildFirstBlocker)) {
                    throw new IllegalStateException("loop-vectorization typed-body rebuild validated evidence is inconsistent for "
                            + artifact);
                }
            }
            case "partially-validated", "invalidated" -> {
                if (typedBodyRebuildRejectedCount <= 0 || typedBodyInvalidatedCount <= 0
                        || "none".equals(typedBodyRebuildFirstBlocker)
                        || "not-attempted".equals(typedBodyRebuildFirstBlocker)) {
                    throw new IllegalStateException("loop-vectorization typed-body rebuild rejection evidence is inconsistent for "
                            + artifact);
                }
            }
            default -> throw new IllegalStateException("Unsupported loop-vectorization typed-body rebuild status for "
                    + artifact + ": " + typedBodyRebuildStatus);
        }
        boolean typedBodyRebuildClean = "validated".equals(typedBodyRebuildStatus);
        if (transformedLoopCount > 0
                && (typedBodyRebuildRejectedCount > 0 || typedBodyInvalidatedCount > 0)
                && !"blocked".equals(status)) {
            throw new IllegalStateException("loop-vectorization rejected typed-body rebuild evidence must block review readiness for "
                    + artifact);
        }
        if (transformedLoopCount > 0 && (!payloadRequired || !approvalRequired)) {
            throw new IllegalStateException("loop-vectorization transformed loops must require runtime-equivalence and approval for "
                    + artifact);
        }

        int skippedCount = skippedLoopShapeCount + skippedUnsupportedWidthCount + skippedUnsafeLoadPatternCount;
        boolean proofClean = loopTripCountProven && contiguousLoadProven && orderedReductionPreserved;
        switch (status) {
            case "no-candidates" -> {
                if (transformedLoopCount > 0 || skippedCount > 0 || !"no-materialized-candidates".equals(firstBlocker)) {
                    throw new IllegalStateException("loop-vectorization no-candidates evidence is inconsistent for " + artifact);
                }
            }
            case "blocked" -> {
                if (transformedLoopCount <= 0 && skippedCount <= 0) {
                    throw new IllegalStateException("loop-vectorization blocked evidence must expose a skipped blocker for " + artifact);
                }
                if ("none".equals(firstBlocker) || "not-recorded".equals(firstBlocker)) {
                    throw new IllegalStateException("loop-vectorization blocked evidence must expose a first blocker for " + artifact);
                }
            }
            case "pending-runtime-equivalence" -> {
                if (transformedLoopCount <= 0 || payloadPresentCount > 0 || !proofClean || !typedBodyRebuildClean
                        || !"runtime-equivalence-payload-not-recorded".equals(firstBlocker)) {
                    throw new IllegalStateException("loop-vectorization pending runtime-equivalence evidence is inconsistent for "
                            + artifact);
                }
            }
            case "runtime-equivalence-not-passed" -> {
                if (transformedLoopCount <= 0 || payloadPresentCount <= 0
                        || payloadPassedCount >= payloadPresentCount || !proofClean || !typedBodyRebuildClean
                        || !"runtime-equivalence-not-passed".equals(firstBlocker)) {
                    throw new IllegalStateException("loop-vectorization failed runtime-equivalence evidence is inconsistent for "
                            + artifact);
                }
            }
            case "review-ready" -> {
                if (transformedLoopCount <= 0 || payloadPresentCount <= 0
                        || payloadPassedCount < payloadPresentCount || !proofClean || !typedBodyRebuildClean
                        || !"none".equals(firstBlocker)) {
                    throw new IllegalStateException("loop-vectorization review-ready evidence must include proofs and passed payloads for "
                            + artifact);
                }
            }
            default -> {
            }
        }
    }

    private static void validateTypedDeadCodeMaterializationEvidence(Path artifact, Properties properties) {
        if (!properties.containsKey("typedDeadCodeMaterialization.pass.count")
                && !properties.containsKey("typedDeadCodeMaterialization.status")) {
            return;
        }

        int passCount = parseNonNegativeInt(artifact, properties, "typedDeadCodeMaterialization.pass.count");
        int nodeCount = parseNonNegativeInt(artifact, properties, "typedDeadCodeMaterialization.node.count");
        int unreachableNodeCount = parseNonNegativeInt(
                artifact,
                properties,
                "typedDeadCodeMaterialization.unreachableNode.count"
        );
        int removedNodeCount = parseNonNegativeInt(
                artifact,
                properties,
                "typedDeadCodeMaterialization.removedNode.count"
        );
        parseNonNegativeInt(artifact, properties, "typedDeadCodeMaterialization.changedMethodBody.count");
        int blockedMissingRootCount = parseNonNegativeInt(
                artifact,
                properties,
                "typedDeadCodeMaterialization.blocked.missingRoot.count"
        );
        int blockedMissingChildReferenceCount = parseNonNegativeInt(
                artifact,
                properties,
                "typedDeadCodeMaterialization.blocked.missingChildReference.count"
        );
        int blockedSideEffectingUnreachableNodeCount = parseNonNegativeInt(
                artifact,
                properties,
                "typedDeadCodeMaterialization.blocked.sideEffectingUnreachableNode.count"
        );
        int payloadPresentCount = parseNonNegativeInt(
                artifact,
                properties,
                "typedDeadCodeMaterialization.runtimeEquivalencePayloadPresent.count"
        );
        int payloadPassedCount = parseNonNegativeInt(
                artifact,
                properties,
                "typedDeadCodeMaterialization.runtimeEquivalencePassed.count"
        );
        boolean payloadRequired = parseBoolean(requirePresent(
                artifact,
                properties,
                "typedDeadCodeMaterialization.runtimeEquivalencePayloadRequired"
        ));
        boolean approvalRequired = parseBoolean(requirePresent(
                artifact,
                properties,
                "typedDeadCodeMaterialization.approvalRequiredBeforeProduction"
        ));
        boolean sideEffectFreedomProven = parseBoolean(requirePresent(
                artifact,
                properties,
                "typedDeadCodeMaterialization.sideEffectFreedomProven"
        ));
        String status = requirePresent(artifact, properties, "typedDeadCodeMaterialization.status");
        String firstBlocker = requirePresent(artifact, properties, "typedDeadCodeMaterialization.firstBlocker");

        validateMaterializationStatus(artifact, "typed-dead-code", status);
        if (passCount == 0 && (!"not-recorded".equals(status) || !"not-recorded".equals(firstBlocker))) {
            throw new IllegalStateException("Missing typed-dead-code materialization evidence must use not-recorded state for "
                    + artifact + ": status=" + status + ", firstBlocker=" + firstBlocker);
        }
        if (passCount > 0 && "not-recorded".equals(status)) {
            throw new IllegalStateException("Recorded typed-dead-code materialization evidence cannot use not-recorded status for "
                    + artifact);
        }
        if (unreachableNodeCount > nodeCount || removedNodeCount > unreachableNodeCount) {
            throw new IllegalStateException("typed-dead-code materialization node counts are inconsistent for "
                    + artifact + ": nodes=" + nodeCount + ", unreachable=" + unreachableNodeCount
                    + ", removed=" + removedNodeCount);
        }
        if (payloadPassedCount > payloadPresentCount) {
            throw new IllegalStateException("typed-dead-code runtime-equivalence passed count exceeds present count for "
                    + artifact + ": passed=" + payloadPassedCount + ", present=" + payloadPresentCount);
        }
        if (removedNodeCount > 0 && (!payloadRequired || !approvalRequired)) {
            throw new IllegalStateException("typed-dead-code removed nodes must require runtime-equivalence and approval for "
                    + artifact);
        }

        int blockedCount = blockedMissingRootCount
                + blockedMissingChildReferenceCount
                + blockedSideEffectingUnreachableNodeCount;
        switch (status) {
            case "no-candidates" -> {
                if (removedNodeCount > 0 || blockedCount > 0 || !"no-materialized-candidates".equals(firstBlocker)) {
                    throw new IllegalStateException("typed-dead-code no-candidates evidence is inconsistent for " + artifact);
                }
            }
            case "blocked" -> {
                if (removedNodeCount <= 0 && blockedCount <= 0) {
                    throw new IllegalStateException("typed-dead-code blocked evidence must expose a blocker for " + artifact);
                }
                if ("none".equals(firstBlocker) || "not-recorded".equals(firstBlocker)) {
                    throw new IllegalStateException("typed-dead-code blocked evidence must expose a first blocker for " + artifact);
                }
            }
            case "pending-runtime-equivalence" -> {
                if (removedNodeCount <= 0 || payloadPresentCount > 0 || !sideEffectFreedomProven
                        || !"runtime-equivalence-payload-not-recorded".equals(firstBlocker)) {
                    throw new IllegalStateException("typed-dead-code pending runtime-equivalence evidence is inconsistent for "
                            + artifact);
                }
            }
            case "runtime-equivalence-not-passed" -> {
                if (removedNodeCount <= 0 || payloadPresentCount <= 0
                        || payloadPassedCount >= payloadPresentCount || !sideEffectFreedomProven
                        || !"runtime-equivalence-not-passed".equals(firstBlocker)) {
                    throw new IllegalStateException("typed-dead-code failed runtime-equivalence evidence is inconsistent for "
                            + artifact);
                }
            }
            case "review-ready" -> {
                if (removedNodeCount <= 0 || payloadPresentCount <= 0
                        || payloadPassedCount < payloadPresentCount || !sideEffectFreedomProven
                        || !"none".equals(firstBlocker)) {
                    throw new IllegalStateException("typed-dead-code review-ready evidence must include side-effect proof and passed payloads for "
                            + artifact);
                }
            }
            default -> {
            }
        }
    }

    private static void validateMaterializationStatus(Path artifact, String family, String status) {
        if (!List.of(
                "not-recorded",
                "no-candidates",
                "blocked",
                "pending-runtime-equivalence",
                "runtime-equivalence-not-passed",
                "review-ready"
        ).contains(status)) {
            throw new IllegalStateException("Unsupported " + family + " materialization status for " + artifact
                    + ": " + status);
        }
    }

    private enum IntrinsicMaterializationKind {
        CLAMP,
        STEP,
        MIX
    }

    private record CliArguments(
            java.util.Optional<Path> artifactPath,
            boolean allowExperimentalApply,
            boolean allowMissing
    ) {
    }

    private static String requirePresent(Path artifact, Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing runtime IR optimizer evidence key for " + artifact + ": " + key);
        }
        return value;
    }

    private static void requireValue(Path artifact, Properties properties, String key, String expected) {
        String actual = requirePresent(artifact, properties, key);
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Unexpected runtime IR optimizer evidence value for "
                    + artifact + ": " + key + "=" + actual + ", expected=" + expected);
        }
    }

    private static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value);
    }

    private static int parseNonNegativeInt(Path artifact, Properties properties, String key) {
        String value = requirePresent(artifact, properties, key);
        return parseNonNegativeInt(artifact, key, value);
    }

    private static int parseOptionalNonNegativeInt(
            Path artifact,
            Properties properties,
            String key,
            int fallback
    ) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return parseNonNegativeInt(artifact, key, value);
    }

    private static int parseNonNegativeInt(Path artifact, String key, String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new NumberFormatException("negative");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid non-negative integer runtime IR optimizer evidence key for "
                    + artifact + ": " + key + "=" + value, exception);
        }
    }
}
