package net.sixik.ga_utils.javatogpu.frontend.asm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CI-oriented classification of ASM preflight output without changing accepted bytecode rules.
 */
public record AsmFrontendReadinessReport(
        AsmFrontendFailureReport failureReport
) {
    public AsmFrontendReadinessReport {
        failureReport = Objects.requireNonNull(failureReport, "failureReport");
    }

    public AsmFrontendReadinessVerdict verdict() {
        if (failureReport.successful()) {
            return AsmFrontendReadinessVerdict.SUPPORTED;
        }
        if (rejectedFailureCount() > 0) {
            return AsmFrontendReadinessVerdict.REJECTED;
        }
        return AsmFrontendReadinessVerdict.REWRITE_REQUIRED;
    }

    public boolean supported() {
        return verdict() == AsmFrontendReadinessVerdict.SUPPORTED;
    }

    public boolean rewriteRequired() {
        return verdict() == AsmFrontendReadinessVerdict.REWRITE_REQUIRED;
    }

    public boolean rejected() {
        return verdict() == AsmFrontendReadinessVerdict.REJECTED;
    }

    public int rewriteRequiredFailureCount() {
        return countFailures(AsmFrontendFailureAction.REWRITE_TO_GPU_SAFE_ASM);
    }

    public int rejectedFailureCount() {
        return countFailures(AsmFrontendFailureAction.REJECT_UNTIL_MANUAL_REDESIGN);
    }

    public Map<String, Long> actionCounts() {
        return failureReport.failures().stream()
                .collect(Collectors.groupingBy(
                        failure -> actionFor(failure).artifactValue(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, Long> migrationBucketCounts() {
        return failureReport.failures().stream()
                .collect(Collectors.groupingBy(
                        failure -> migrationBucketFor(failure).artifactValue(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public List<String> migrationGuidance() {
        return failureReport.failures().stream()
                .map(AsmFrontendReadinessReport::migrationBucketFor)
                .distinct()
                .filter(bucket -> bucket != AsmFrontendMigrationBucket.NONE)
                .map(bucket -> bucket.artifactValue() + ": " + bucket.guidance())
                .toList();
    }

    public String firstMigrationBucket() {
        if (failureReport.successful()) {
            return AsmFrontendMigrationBucket.NONE.artifactValue();
        }
        return migrationBucketFor(failureReport.failures().get(0)).artifactValue();
    }

    public List<String> rewriteRequiredSummaries() {
        return summariesFor(AsmFrontendFailureAction.REWRITE_TO_GPU_SAFE_ASM);
    }

    public List<String> rejectedSummaries() {
        return summariesFor(AsmFrontendFailureAction.REJECT_UNTIL_MANUAL_REDESIGN);
    }

    public String summaryLine() {
        return "asmReadiness verdict="
                + verdict().artifactValue()
                + " failures="
                + failureReport.failureCount()
                + " rewriteRequired="
                + rewriteRequiredFailureCount()
                + " rejected="
                + rejectedFailureCount()
                + " firstBucket="
                + firstMigrationBucket();
    }

    public Map<String, String> artifactFields(String prefix) {
        String safePrefix = prefix == null || prefix.isBlank() ? "asmReadiness" : prefix;
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(safePrefix + ".verdict", verdict().artifactValue());
        fields.put(safePrefix + ".supported", Boolean.toString(supported()));
        fields.put(safePrefix + ".rewriteRequired", Boolean.toString(rewriteRequired()));
        fields.put(safePrefix + ".rejected", Boolean.toString(rejected()));
        fields.put(safePrefix + ".failureCount", Integer.toString(failureReport.failureCount()));
        fields.put(safePrefix + ".rewriteRequiredFailureCount", Integer.toString(rewriteRequiredFailureCount()));
        fields.put(safePrefix + ".rejectedFailureCount", Integer.toString(rejectedFailureCount()));
        fields.put(safePrefix + ".actionCounts", actionCounts().toString());
        fields.put(safePrefix + ".migrationBucketCounts", migrationBucketCounts().toString());
        fields.put(safePrefix + ".firstMigrationBucket", firstMigrationBucket());
        fields.put(safePrefix + ".summary", summaryLine());
        putJoined(fields, safePrefix + ".rewriteRequiredSummaries", rewriteRequiredSummaries());
        putJoined(fields, safePrefix + ".rejectedSummaries", rejectedSummaries());
        putJoined(fields, safePrefix + ".migrationGuidance", migrationGuidance());
        for (Map.Entry<String, Long> entry : actionCounts().entrySet()) {
            fields.put(safePrefix + ".actionCount." + entry.getKey(), Long.toString(entry.getValue()));
        }
        for (Map.Entry<String, Long> entry : migrationBucketCounts().entrySet()) {
            fields.put(safePrefix + ".migrationBucket." + entry.getKey(), Long.toString(entry.getValue()));
        }
        return Map.copyOf(fields);
    }

    public AsmFrontendReadinessReport requireSupported() {
        if (supported()) {
            return this;
        }
        AsmFrontendFailureMetadata firstFailure = failureReport.failures().get(0);
        throw new AsmFrontendException(
                summaryLine() + "; " + failureReport.summaryLine(),
                firstFailure
        );
    }

    public static AsmFrontendFailureAction actionFor(AsmFrontendFailureMetadata failure) {
        Objects.requireNonNull(failure, "failure");
        return switch (failure.family()) {
            case "arrayLength", "arrayAllocation", "exceptionControlFlow", "monitorSynchronization",
                    "dynamicInvocation", "methodInvocation", "fieldAccess", "objectType" ->
                    AsmFrontendFailureAction.REWRITE_TO_GPU_SAFE_ASM;
            default -> AsmFrontendFailureAction.REJECT_UNTIL_MANUAL_REDESIGN;
        };
    }

    public static AsmFrontendMigrationBucket migrationBucketFor(AsmFrontendFailureMetadata failure) {
        Objects.requireNonNull(failure, "failure");
        return switch (failure.family()) {
            case "arrayLength" -> AsmFrontendMigrationBucket.ARRAY_METADATA;
            case "arrayAllocation" -> AsmFrontendMigrationBucket.HOST_MEMORY_MODEL;
            case "exceptionControlFlow" -> AsmFrontendMigrationBucket.EXPLICIT_FAILURE_MODEL;
            case "monitorSynchronization" -> AsmFrontendMigrationBucket.SYNCHRONIZATION_MODEL;
            case "dynamicInvocation", "methodInvocation" -> AsmFrontendMigrationBucket.STATIC_DISPATCH_MODEL;
            case "fieldAccess" -> AsmFrontendMigrationBucket.FIELD_STATE_MODEL;
            case "objectType" -> AsmFrontendMigrationBucket.OBJECT_MODEL;
            case "methodDescriptor" -> AsmFrontendMigrationBucket.TYPE_SIGNATURE_MODEL;
            case "controlFlow" -> AsmFrontendMigrationBucket.CONTROL_FLOW_MODEL;
            default -> AsmFrontendMigrationBucket.UNKNOWN_FRONTEND_GAP;
        };
    }

    private int countFailures(AsmFrontendFailureAction action) {
        return (int) failureReport.failures().stream()
                .filter(failure -> actionFor(failure) == action)
                .count();
    }

    private List<String> summariesFor(AsmFrontendFailureAction action) {
        return failureReport.failures().stream()
                .filter(failure -> actionFor(failure) == action)
                .map(AsmFrontendFailureMetadata::summary)
                .toList();
    }

    private void putJoined(Map<String, String> fields, String key, List<String> values) {
        if (!values.isEmpty()) {
            fields.put(key, String.join(" | ", values));
        }
    }
}
