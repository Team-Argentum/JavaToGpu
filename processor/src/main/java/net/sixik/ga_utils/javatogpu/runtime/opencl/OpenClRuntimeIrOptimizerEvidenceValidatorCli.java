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
