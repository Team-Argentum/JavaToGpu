package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only typed preflight for future literal arithmetic CSE rewrites.
 *
 * <p>This report summarizes which literal canonicalization preview candidates would be eligible for
 * a future typed rewrite path. It never mutates IR and never enables production fingerprints.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport(
        String methodName,
        String readiness,
        int candidateCount,
        int eligibleCandidateCount,
        int blockedCandidateCount,
        int uniqueCanonicalKeyCount,
        boolean numericSemanticsFullyProven,
        boolean runtimeEquivalenceSuccessful,
        boolean evidenceComplete,
        boolean previewOnlyBlastRadius,
        String enablementVerdict,
        List<Candidate> eligibleCandidates,
        List<BlockedCandidate> blockedCandidates
) {
    private static final String BLOCKER_NO_PREVIEW_CANDIDATES = "noPreviewCandidates";
    private static final String BLOCKER_NUMERIC_SEMANTICS = "typedNumericSemanticsNotProven";
    private static final String BLOCKER_RUNTIME_EQUIVALENCE = "runtimeEquivalenceNotProven";
    private static final String BLOCKER_PREVIEW_ONLY = "previewOnlyBlastRadius";
    private static final String BLOCKER_FINGERPRINT_DISABLED = "productionFingerprintIntegrationDisabled";

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (readiness == null || readiness.isBlank()) {
            throw new IllegalArgumentException("readiness must not be blank");
        }
        if (candidateCount < 0) {
            throw new IllegalArgumentException("candidateCount must be non-negative");
        }
        if (eligibleCandidateCount < 0) {
            throw new IllegalArgumentException("eligibleCandidateCount must be non-negative");
        }
        if (blockedCandidateCount < 0) {
            throw new IllegalArgumentException("blockedCandidateCount must be non-negative");
        }
        if (uniqueCanonicalKeyCount < 0) {
            throw new IllegalArgumentException("uniqueCanonicalKeyCount must be non-negative");
        }
        if (enablementVerdict == null || enablementVerdict.isBlank()) {
            throw new IllegalArgumentException("enablementVerdict must not be blank");
        }
        eligibleCandidates = List.copyOf(Objects.requireNonNull(eligibleCandidates, "eligibleCandidates"));
        blockedCandidates = List.copyOf(Objects.requireNonNull(blockedCandidates, "blockedCandidates"));
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport from(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalenceReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport fingerprintParityReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport enablementReport
    ) {
        Objects.requireNonNull(canonicalizationReport, "canonicalizationReport");
        Objects.requireNonNull(numericSemanticsProofReport, "numericSemanticsProofReport");
        Objects.requireNonNull(runtimeEquivalenceReport, "runtimeEquivalenceReport");
        Objects.requireNonNull(fingerprintParityReport, "fingerprintParityReport");
        Objects.requireNonNull(enablementReport, "enablementReport");

        List<String> candidateBlockers = candidateBlockers(
                canonicalizationReport,
                numericSemanticsProofReport,
                runtimeEquivalenceReport,
                fingerprintParityReport,
                enablementReport
        );
        List<Candidate> eligibleCandidates = candidateBlockers.isEmpty()
                ? canonicalizationReport.candidates().stream().map(Candidate::from).toList()
                : List.of();
        List<BlockedCandidate> blockedCandidates = candidateBlockers.isEmpty()
                ? List.of()
                : canonicalizationReport.candidates().stream()
                .map(candidate -> BlockedCandidate.from(candidate, candidateBlockers))
                .toList();
        if (!canonicalizationReport.hasCandidates()) {
            blockedCandidates = List.of(new BlockedCandidate(
                    "<method>",
                    "none",
                    "none",
                    List.of(BLOCKER_NO_PREVIEW_CANDIDATES),
                    "no literal canonicalization preview candidates"
            ));
        }
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport(
                canonicalizationReport.methodName(),
                readiness(canonicalizationReport, candidateBlockers),
                canonicalizationReport.candidateCount(),
                eligibleCandidates.size(),
                blockedCandidates.size(),
                canonicalizationReport.uniqueCanonicalKeyCount(),
                numericSemanticsProofReport.fullyProven(),
                runtimeEquivalenceReport.successful(),
                enablementReport.evidenceComplete(),
                fingerprintParityReport.hasPreviewOnlyKeys(),
                enablementReport.verdict(),
                eligibleCandidates,
                blockedCandidates
        );
    }

    public boolean hasCandidates() {
        return candidateCount > 0;
    }

    public boolean hasEligibleCandidates() {
        return eligibleCandidateCount > 0;
    }

    public boolean hasBlockedCandidates() {
        return blockedCandidateCount > 0;
    }

    public Optional<Candidate> firstEligibleCandidate() {
        return eligibleCandidates.stream().findFirst();
    }

    public Optional<BlockedCandidate> firstBlockedCandidate() {
        return blockedCandidates.stream().findFirst();
    }

    public Map<String, Long> blockerCounts() {
        return blockedCandidates.stream()
                .flatMap(candidate -> candidate.blockers().stream())
                .collect(Collectors.groupingBy(
                        blocker -> blocker,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Readiness", readiness);
        values.put(prefix + "Candidates", Integer.toString(candidateCount));
        values.put(prefix + "EligibleCandidates", Integer.toString(eligibleCandidateCount));
        values.put(prefix + "BlockedCandidates", Integer.toString(blockedCandidateCount));
        values.put(prefix + "UniqueCanonicalKeys", Integer.toString(uniqueCanonicalKeyCount));
        values.put(prefix + "NumericSemanticsFullyProven", Boolean.toString(numericSemanticsFullyProven));
        values.put(prefix + "RuntimeEquivalenceSuccessful", Boolean.toString(runtimeEquivalenceSuccessful));
        values.put(prefix + "EvidenceComplete", Boolean.toString(evidenceComplete));
        values.put(prefix + "PreviewOnlyBlastRadius", Boolean.toString(previewOnlyBlastRadius));
        values.put(prefix + "EnablementVerdict", enablementVerdict);
        values.put(prefix + "BlockerCounts", mapSummary(blockerCounts()));
        firstEligibleCandidate().ifPresent(candidate -> {
            values.put(prefix + "FirstEligibleLocation", candidate.location());
            values.put(prefix + "FirstEligibleCanonicalKey", candidate.canonicalKey());
            values.put(prefix + "FirstEligibleSummary", candidate.summary());
        });
        firstBlockedCandidate().ifPresent(candidate -> {
            values.put(prefix + "FirstBlockedLocation", candidate.location());
            values.put(prefix + "FirstBlockedCanonicalKey", candidate.canonicalKey());
            values.put(prefix + "FirstBlockedBlockers", listSummary(candidate.blockers()));
            values.put(prefix + "FirstBlockedReason", candidate.firstBlocker().orElse(""));
            values.put(prefix + "FirstBlockedSummary", candidate.summary());
        });
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseSimpleArithmeticLiteralRewritePreflight");
    }

    public String summary() {
        return "CSE simple arithmetic literal rewrite preflight method=" + methodName
                + " readiness=" + readiness
                + " candidates=" + candidateCount
                + " eligibleCandidates=" + eligibleCandidateCount
                + " blockedCandidates=" + blockedCandidateCount
                + " uniqueCanonicalKeys=" + uniqueCanonicalKeyCount
                + " numericSemanticsFullyProven=" + numericSemanticsFullyProven
                + " runtimeEquivalenceSuccessful=" + runtimeEquivalenceSuccessful
                + " evidenceComplete=" + evidenceComplete
                + " previewOnlyBlastRadius=" + previewOnlyBlastRadius
                + " enablementVerdict=" + enablementVerdict
                + " blockers=" + mapSummary(blockerCounts());
    }

    private static String readiness(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
            List<String> candidateBlockers
    ) {
        if (!canonicalizationReport.hasCandidates()) {
            return "none";
        }
        return candidateBlockers.isEmpty() ? "eligible" : "blocked";
    }

    private static List<String> candidateBlockers(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalenceReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport fingerprintParityReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport enablementReport
    ) {
        java.util.ArrayList<String> blockers = new java.util.ArrayList<>();
        if (!canonicalizationReport.hasCandidates()) {
            blockers.add(BLOCKER_NO_PREVIEW_CANDIDATES);
        }
        if (!numericSemanticsProofReport.fullyProven()) {
            blockers.add(BLOCKER_NUMERIC_SEMANTICS);
        }
        if (!runtimeEquivalenceReport.successful()) {
            blockers.add(BLOCKER_RUNTIME_EQUIVALENCE);
        }
        if (fingerprintParityReport.hasPreviewOnlyKeys()) {
            blockers.add(BLOCKER_PREVIEW_ONLY);
        }
        if (!enablementReport.readyForProduction()) {
            blockers.add(BLOCKER_FINGERPRINT_DISABLED);
        }
        return List.copyOf(blockers);
    }

    private static String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String listSummary(List<String> values) {
        return values.stream().collect(Collectors.joining(",", "[", "]"));
    }

    public record Candidate(
            String location,
            String canonicalKey,
            String operatorTypeKey,
            List<String> literalSources
    ) {
        public Candidate {
            if (location == null || location.isBlank()) {
                throw new IllegalArgumentException("location must not be blank");
            }
            if (canonicalKey == null || canonicalKey.isBlank()) {
                throw new IllegalArgumentException("canonicalKey must not be blank");
            }
            if (operatorTypeKey == null || operatorTypeKey.isBlank()) {
                throw new IllegalArgumentException("operatorTypeKey must not be blank");
            }
            literalSources = List.copyOf(Objects.requireNonNull(literalSources, "literalSources"));
        }

        private static Candidate from(GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.Candidate candidate) {
            return new Candidate(
                    candidate.location(),
                    candidate.canonicalKey(),
                    candidate.operatorTypeKey(),
                    candidate.literalSources()
            );
        }

        public String summary() {
            return "location=" + location
                    + " canonicalKey=" + canonicalKey
                    + " operatorTypeKey=" + operatorTypeKey
                    + " literalSources=" + listSummary(literalSources);
        }
    }

    public record BlockedCandidate(
            String location,
            String canonicalKey,
            String operatorTypeKey,
            List<String> blockers,
            String summary
    ) {
        public BlockedCandidate {
            if (location == null || location.isBlank()) {
                throw new IllegalArgumentException("location must not be blank");
            }
            if (canonicalKey == null || canonicalKey.isBlank()) {
                throw new IllegalArgumentException("canonicalKey must not be blank");
            }
            if (operatorTypeKey == null || operatorTypeKey.isBlank()) {
                throw new IllegalArgumentException("operatorTypeKey must not be blank");
            }
            blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
            if (summary == null || summary.isBlank()) {
                throw new IllegalArgumentException("summary must not be blank");
            }
        }

        private static BlockedCandidate from(
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.Candidate candidate,
                List<String> blockers
        ) {
            return new BlockedCandidate(
                    candidate.location(),
                    candidate.canonicalKey(),
                    candidate.operatorTypeKey(),
                    blockers,
                    "location=" + candidate.location()
                            + " canonicalKey=" + candidate.canonicalKey()
                            + " blockers=" + listSummary(blockers)
            );
        }

        public Optional<String> firstBlocker() {
            return blockers.stream().findFirst();
        }
    }
}
