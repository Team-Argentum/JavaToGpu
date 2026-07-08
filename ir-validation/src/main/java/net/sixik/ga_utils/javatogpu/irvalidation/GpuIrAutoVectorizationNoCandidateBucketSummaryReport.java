package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only split of the broad auto-vectorization no-candidate state.
 *
 * <p>The scanner currently reports zero rewrite candidates as one coarse blocker. This report keeps
 * that production behavior intact, but gives CI and real-kernel dogfooding a more useful first
 * bucket for deciding whether candidate discovery, proof coverage, or user guidance should move
 * next.</p>
 */
public record GpuIrAutoVectorizationNoCandidateBucketSummaryReport(
        String methodName,
        int candidateCount,
        int warningCount,
        int rejectionCount,
        int rewritePlanGuardCount,
        String rewriteReadiness,
        String proofDecisionStatus,
        String rewritePolicyReadiness,
        String dryRunReadiness,
        List<String> buckets,
        List<GpuIrAutoVectorizationNoCandidateExample> examples
) {
    private static final GpuIrAutoVectorizationNoCandidateDiagnosticFormatter DIAGNOSTIC_FORMATTER =
            new GpuIrAutoVectorizationNoCandidateDiagnosticFormatter();

    public GpuIrAutoVectorizationNoCandidateBucketSummaryReport(
            String methodName,
            int candidateCount,
            int warningCount,
            int rejectionCount,
            int rewritePlanGuardCount,
            String rewriteReadiness,
            String proofDecisionStatus,
            String rewritePolicyReadiness,
            String dryRunReadiness,
            List<String> buckets
    ) {
        this(
                methodName,
                candidateCount,
                warningCount,
                rejectionCount,
                rewritePlanGuardCount,
                rewriteReadiness,
                proofDecisionStatus,
                rewritePolicyReadiness,
                dryRunReadiness,
                buckets,
                List.of()
        );
    }

    public GpuIrAutoVectorizationNoCandidateBucketSummaryReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (candidateCount < 0) {
            throw new IllegalArgumentException("candidateCount must be non-negative");
        }
        if (warningCount < 0) {
            throw new IllegalArgumentException("warningCount must be non-negative");
        }
        if (rejectionCount < 0) {
            throw new IllegalArgumentException("rejectionCount must be non-negative");
        }
        if (rewritePlanGuardCount < 0) {
            throw new IllegalArgumentException("rewritePlanGuardCount must be non-negative");
        }
        if (rewriteReadiness == null || rewriteReadiness.isBlank()) {
            throw new IllegalArgumentException("rewriteReadiness must not be blank");
        }
        if (proofDecisionStatus == null || proofDecisionStatus.isBlank()) {
            throw new IllegalArgumentException("proofDecisionStatus must not be blank");
        }
        if (rewritePolicyReadiness == null || rewritePolicyReadiness.isBlank()) {
            throw new IllegalArgumentException("rewritePolicyReadiness must not be blank");
        }
        if (dryRunReadiness == null || dryRunReadiness.isBlank()) {
            throw new IllegalArgumentException("dryRunReadiness must not be blank");
        }
        buckets = copyBuckets(buckets);
        examples = copyExamples(examples);
    }

    public static GpuIrAutoVectorizationNoCandidateBucketSummaryReport from(
            GpuIrAutoVectorizationReadinessSummaryReport readiness
    ) {
        Objects.requireNonNull(readiness, "readiness");
        return new GpuIrAutoVectorizationNoCandidateBucketSummaryReport(
                readiness.methodName(),
                readiness.candidateCount(),
                readiness.warningCount(),
                readiness.rejectionCount(),
                readiness.rewritePlanGuardCount(),
                readiness.rewriteReadiness(),
                readiness.proofDecisionStatus(),
                readiness.rewritePolicyReadiness(),
                readiness.dryRunReadiness(),
                buckets(readiness),
                examples(readiness)
        );
    }

    public boolean noCandidates() {
        return candidateCount == 0;
    }

    public String readiness() {
        return noCandidates() ? "bucketed" : "notApplicable";
    }

    public int bucketCount() {
        return buckets.size();
    }

    public Optional<String> firstBucket() {
        return buckets.stream().findFirst();
    }

    public Optional<GpuIrAutoVectorizationNoCandidateExample> firstExample() {
        return examples.stream().findFirst();
    }

    public Optional<String> firstDiagnostic() {
        return DIAGNOSTIC_FORMATTER.format(methodName, this);
    }

    public String diagnosticCode() {
        return firstExample().isPresent()
                ? GpuIrAutoVectorizationNoCandidateDiagnosticFormatter.DIAGNOSTIC_CODE
                : "none";
    }

    public String firstHelp() {
        return firstExample().map(ignored -> DIAGNOSTIC_FORMATTER.helpFor(this)).orElse("none");
    }

    public String firstRemainingWork() {
        return firstBucket()
                .map(GpuIrAutoVectorizationNoCandidateBucketSummaryReport::remainingWorkForBucket)
                .orElse("none");
    }

    public Map<String, Long> bucketCounts() {
        return buckets.stream()
                .collect(Collectors.groupingBy(
                        bucket -> bucket,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorizationNoCandidate");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Readiness", readiness());
        values.put(prefix + "NoCandidates", Boolean.toString(noCandidates()));
        values.put(prefix + "CandidateCount", Integer.toString(candidateCount));
        values.put(prefix + "BucketCount", Integer.toString(bucketCount()));
        values.put(prefix + "Buckets", listSummary(buckets));
        values.put(prefix + "BucketCounts", mapSummary(bucketCounts()));
        values.put(prefix + "FirstBucket", firstBucket().orElse("none"));
        values.put(prefix + "FirstRemainingWork", firstRemainingWork());
        values.put(prefix + "FirstExampleBucket", firstExample().map(GpuIrAutoVectorizationNoCandidateExample::bucket).orElse("none"));
        values.put(prefix + "FirstExampleLocation", firstExample().map(GpuIrAutoVectorizationNoCandidateExample::location).orElse("none"));
        values.put(prefix + "FirstExample", firstExample().map(GpuIrAutoVectorizationNoCandidateExample::artifactValue).orElse("none"));
        values.put(prefix + "DiagnosticCode", diagnosticCode());
        values.put(prefix + "FirstHelp", firstHelp());
        values.put(prefix + "FirstDiagnostic", firstDiagnostic().orElse("none"));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        bucketCounts().forEach((bucket, count) ->
                values.put(prefix + "Bucket." + bucket, Long.toString(count)));
        examplesByBucket().forEach((bucket, example) ->
                values.put(prefix + "Example." + bucket, example.artifactValue()));
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "auto-vectorization no-candidate readiness=" + readiness()
                + " buckets=" + bucketCount()
                + " firstBucket=" + firstBucket().orElse("none")
                + " firstRemainingWork=" + firstRemainingWork()
                + " firstExample=" + firstExample().map(GpuIrAutoVectorizationNoCandidateExample::artifactValue).orElse("none");
    }

    public String summary() {
        return ciSummaryLine()
                + " bucketCounts=" + mapSummary(bucketCounts())
                + " rewriteReadiness=" + rewriteReadiness
                + " proofDecisionStatus=" + proofDecisionStatus
                + " rewritePolicyReadiness=" + rewritePolicyReadiness
                + " dryRunReadiness=" + dryRunReadiness;
    }

    public Map<String, GpuIrAutoVectorizationNoCandidateExample> examplesByBucket() {
        LinkedHashMap<String, GpuIrAutoVectorizationNoCandidateExample> values = new LinkedHashMap<>();
        for (GpuIrAutoVectorizationNoCandidateExample example : examples) {
            values.putIfAbsent(example.bucket(), example);
        }
        return Collections.unmodifiableMap(values);
    }

    private static List<String> buckets(GpuIrAutoVectorizationReadinessSummaryReport readiness) {
        if (readiness.candidateCount() > 0) {
            return List.of();
        }
        if (!readiness.noCandidateBuckets().isEmpty()) {
            return readiness.noCandidateBuckets();
        }
        java.util.LinkedHashSet<String> buckets = new java.util.LinkedHashSet<>();
        if (readiness.rejectionCount() > 0) {
            buckets.add("candidateRejectedBeforeRewrite");
        }
        if (readiness.warningCount() > 0) {
            buckets.add("candidateWarningBeforeRewrite");
        }
        if (readiness.rewritePlanGuardCount() > 0) {
            buckets.add("candidateGuardedBeforeRewrite");
        }
        if ("none".equals(readiness.rewriteReadiness())
                && "allow".equals(readiness.proofDecisionStatus())
                && "none".equals(readiness.rewritePolicyReadiness())) {
            buckets.add("scannerFoundNoVectorShape");
        }
        if (buckets.isEmpty()) {
            buckets.add("candidateDiscoveryUnknown");
        }
        return List.copyOf(buckets);
    }

    private static List<GpuIrAutoVectorizationNoCandidateExample> examples(
            GpuIrAutoVectorizationReadinessSummaryReport readiness
    ) {
        if (readiness.candidateCount() > 0) {
            return List.of();
        }
        if (!readiness.noCandidateExamples().isEmpty()) {
            return readiness.noCandidateExamples();
        }
        return buckets(readiness).stream()
                .map(bucket -> new GpuIrAutoVectorizationNoCandidateExample(
                        bucket,
                        readiness.methodName(),
                        "method",
                        remainingWorkForBucket(bucket)
                ))
                .toList();
    }

    private static String remainingWorkForBucket(String bucket) {
        return switch (bucket) {
            case "scannerFoundNoVectorShape" -> "classifyScalarOrLoopShape";
            case "emptyMethod" -> "addVectorizableWorkOrSkipVectorization";
            case "scalarOnlyMethod" -> "skipOrDocumentScalarOnlyMethod";
            case "arrayWorkWithoutLoop" -> "introduceOrDetectLaneLoop";
            case "noForLoop" -> "supportWhileLikeLoopDiscovery";
            case "unsupportedLoopShape" -> "normalizeLoopToSupportedForShape";
            case "noFixedWidthLaneLoop" -> "adjustLaneCountOrExtendSupportedLanes";
            case "noLaneArrayAssignment" -> "detectOrIntroduceLaneArrayAssignment";
            case "incompleteIr" -> "fixIncompleteIrBeforeVectorization";
            case "candidateRejectedBeforeRewrite" -> "splitRejectedCandidateReason";
            case "candidateWarningBeforeRewrite" -> "clearCandidateWarnings";
            case "candidateGuardedBeforeRewrite" -> "clearCandidateRewriteGuards";
            case "candidateDiscoveryUnknown" -> "inspectCandidateScannerCoverage";
            default -> "reviewNoCandidateBucket:" + bucket;
        };
    }

    private static List<String> copyBuckets(List<String> values) {
        Objects.requireNonNull(values, "buckets");
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("buckets must not contain blank entries");
        }
        return values.stream().distinct().toList();
    }

    private static List<GpuIrAutoVectorizationNoCandidateExample> copyExamples(
            List<GpuIrAutoVectorizationNoCandidateExample> values
    ) {
        Objects.requireNonNull(values, "examples");
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("examples must not contain null entries");
        }
        return List.copyOf(values);
    }

    private static String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String listSummary(List<String> values) {
        return values.stream().collect(Collectors.joining(",", "[", "]"));
    }
}
