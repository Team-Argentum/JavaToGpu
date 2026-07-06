package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only decision object for future production CSE rewrite entrypoints.
 *
 * <p>The policy is intentionally conservative: any skipped candidate blocks production rewrite
 * readiness, even when some prototype plans are available.</p>
 */
public record GpuIrCommonSubexpressionRewritePolicy(
        String methodName,
        int planCount,
        int insertionCount,
        int replacementCount,
        List<GpuIrCommonSubexpressionSkippedCandidate> blockingSkippedCandidates
) {
    public GpuIrCommonSubexpressionRewritePolicy {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (planCount < 0) {
            throw new IllegalArgumentException("planCount must be non-negative");
        }
        if (insertionCount < 0) {
            throw new IllegalArgumentException("insertionCount must be non-negative");
        }
        if (replacementCount < 0) {
            throw new IllegalArgumentException("replacementCount must be non-negative");
        }
        blockingSkippedCandidates = List.copyOf(Objects.requireNonNull(
                blockingSkippedCandidates,
                "blockingSkippedCandidates"
        ));
        if (planCount == 0 && (insertionCount != 0 || replacementCount != 0)) {
            throw new IllegalArgumentException("insertion/replacement counts must be zero when no plans exist");
        }
    }

    public static GpuIrCommonSubexpressionRewritePolicy from(
            GpuIrCompiledMethod method,
            GpuIrCommonSubexpressionRewritePlanReport report
    ) {
        Objects.requireNonNull(method, "method");
        if (method.irMethod() == null) {
            throw new IllegalArgumentException("method must contain IR metadata");
        }
        return from(method.irMethod().name(), report);
    }

    public static GpuIrCommonSubexpressionRewritePolicy from(
            String methodName,
            GpuIrCommonSubexpressionRewritePlanReport report
    ) {
        Objects.requireNonNull(report, "report");
        return new GpuIrCommonSubexpressionRewritePolicy(
                methodName,
                report.plans().size(),
                report.insertionCount(),
                report.replacementEditCount(),
                report.skippedCandidates()
        );
    }

    public boolean hasPlans() {
        return planCount > 0;
    }

    public boolean hasBlockingSkippedCandidates() {
        return !blockingSkippedCandidates.isEmpty();
    }

    public boolean canRewrite() {
        return hasPlans() && !hasBlockingSkippedCandidates();
    }

    public int blockingSkippedCandidateCount() {
        return blockingSkippedCandidates.size();
    }

    public Optional<GpuIrCommonSubexpressionSkippedCandidate> firstBlockingSkippedCandidate() {
        if (blockingSkippedCandidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(blockingSkippedCandidates.get(0));
    }

    public Map<GpuIrCommonSubexpressionSkipReason, Long> blockingSkipReasonTypeCounts() {
        return blockingSkippedCandidates.stream()
                .collect(Collectors.groupingBy(
                        GpuIrCommonSubexpressionSkippedCandidate::reason,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, Long> blockingSkipReasonCounts() {
        return blockingSkipReasonTypeCounts().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().name(),
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    public Map<GpuIrCommonSubexpressionDominanceStatus, Long> blockingDominanceStatusTypeCounts() {
        return blockingSkippedCandidates.stream()
                .collect(Collectors.groupingBy(
                        GpuIrCommonSubexpressionSkippedCandidate::dominanceStatus,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, Long> blockingDominanceStatusCounts() {
        return blockingDominanceStatusTypeCounts().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().artifactValue(),
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    public GpuIrCommonSubexpressionRewriteReadiness readiness() {
        if (hasBlockingSkippedCandidates()) {
            return GpuIrCommonSubexpressionRewriteReadiness.BLOCKED_BY_SKIPPED_CANDIDATE;
        }
        if (!hasPlans()) {
            return GpuIrCommonSubexpressionRewriteReadiness.NONE;
        }
        return GpuIrCommonSubexpressionRewriteReadiness.READY;
    }

    public GpuIrCommonSubexpressionRewriteBlockerExplanation blockerExplanation() {
        return GpuIrCommonSubexpressionRewriteBlockerExplanation.from(this);
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "CanRewrite", Boolean.toString(canRewrite()));
        values.put(prefix + "Readiness", readiness().artifactValue());
        values.put(prefix + "Plans", Integer.toString(planCount));
        values.put(prefix + "Insertions", Integer.toString(insertionCount));
        values.put(prefix + "Replacements", Integer.toString(replacementCount));
        values.put(prefix + "BlockingSkippedCandidates", Integer.toString(blockingSkippedCandidateCount()));
        values.put(prefix + "BlockingSkipReasonCounts", mapSummary(blockingSkipReasonCounts()));
        values.put(prefix + "BlockingDominanceStatusCounts", mapSummary(blockingDominanceStatusCounts()));
        firstBlockingSkippedCandidate().ifPresent(candidate -> {
            GpuIrCommonSubexpressionSkippedDiagnostic diagnostic = candidate.diagnostic();
            values.put(prefix + "FirstBlockingSkippedReason", candidate.reason().name());
            values.put(prefix + "FirstBlockingSkippedDominanceStatus", candidate.dominanceStatus().artifactValue());
            values.put(prefix + "FirstBlockingSkippedSummary", diagnostic.summary());
        });
        blockingSkipReasonCounts().forEach((reason, count) ->
                values.put(prefix + "BlockingSkipReason." + reason, Long.toString(count)));
        blockingDominanceStatusCounts().forEach((status, count) ->
                values.put(prefix + "BlockingDominanceStatus." + status, Long.toString(count)));
        values.putAll(blockerExplanation().artifactFields(prefix + "BlockerExplanation"));
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseRewritePolicy");
    }

    public String summary() {
        return "CSE rewrite policy method=" + methodName
                + " plans=" + planCount
                + " insertions=" + insertionCount
                + " replacements=" + replacementCount
                + " readiness=" + readiness().artifactValue()
                + " canRewrite=" + canRewrite()
                + " blockingSkippedCandidates=" + blockingSkippedCandidateCount()
                + (hasBlockingSkippedCandidates() ? " blockingSkipReasons=" + blockingSkipReasonCounts() : "")
                + (hasBlockingSkippedCandidates() ? " blockingDominanceStatuses=" + blockingDominanceStatusCounts() : "")
                + " blockerExplanation={" + blockerExplanation().ciSummaryLine() + "}"
                + firstBlockingSkippedCandidate()
                .map(candidate -> " firstBlockingSkipped=" + candidate.diagnostic().summary())
                .orElse("");
    }

    private String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }
}
