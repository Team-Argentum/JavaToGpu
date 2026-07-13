package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper;

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
        if (args.length != 1 || args[0] == null || args[0].isBlank()) {
            throw new IllegalArgumentException("Expected runtime IR optimizer evidence file or artifact directory");
        }
        Path artifactPath = Path.of(args[0]);
        List<Path> artifacts = evidenceArtifacts(artifactPath);
        if (artifacts.isEmpty()) {
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
            validateGuardrails(artifact, properties);
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
                + ", reviewPackage.pendingManualReview=" + pendingManualReview);
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

    private static void validateGuardrails(Path artifact, Properties properties) {
        requireValue(artifact, properties, "runtimeEquivalenceReview.productionMutation", "disabled");
        requireValue(artifact, properties, "runtimeEquivalenceReview.selectedIrReplacement", "disabled");
        requireValue(artifact, properties, "runtimeEquivalenceReview.manualReviewOnly", "true");
        requireValue(artifact, properties, "reviewPackage.manualReviewOnly", "true");
        requireValue(artifact, properties, "reviewPackage.productionMutation", "disabled");
        requireValue(artifact, properties, "reviewPackage.selectedIrReplacement", "disabled");
        requireValue(artifact, properties, "reviewPackage.originalIrRequired", "true");
        requireValue(artifact, properties, "reviewPackage.optimizedIrRequired", "true");
        requireValue(artifact, properties, "reviewPackage.proofSummaryRequired", "true");
        validateOptimizedArtifactCandidateGuardrails(artifact, properties);

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

    private static void validateOptimizedArtifactCandidateGuardrails(Path artifact, Properties properties) {
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
        String firstBlocker = requirePresent(artifact, properties, "optimizedArtifactCandidate.firstBlocker");
        String selectionFirstBlocker = requirePresent(
                artifact,
                properties,
                "optimizedArtifactCandidate.selectionFirstBlocker"
        );
        requireValue(artifact, properties, "optimizedArtifactCandidate.selectionApplied", "false");
        requireValue(artifact, properties, "optimizedArtifactCandidate.selectedIrReplacement", "false");

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
        if (count > 0 && ("none".equals(selectionFirstBlocker) || "no-candidates".equals(selectionFirstBlocker))) {
            throw new IllegalStateException("Optimized artifact candidates must keep selection blocked for "
                    + artifact + ": selectionFirstBlocker=" + selectionFirstBlocker);
        }
        if (selectionReadyCount > 0 || selectionAppliedCount > 0 || selectedIrReplacementCount > 0) {
            throw new IllegalStateException("Optimized artifact candidates must not become runtime selection for "
                    + artifact + ": selectionReady=" + selectionReadyCount
                    + ", selectionApplied=" + selectionAppliedCount
                    + ", selectedIrReplacement=" + selectedIrReplacementCount);
        }
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
