package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only handoff from validation-rule artifacts to future optimizer enablement decisions.
 *
 * <p>This report deliberately does not enable production rewrites. It gives CI and tooling one
 * compact artifact that explains whether a validation-rule artifact is clean enough to be used as
 * input for a later optimizer enablement review.</p>
 */
public record GpuIrOptimizationValidationOptimizerReadinessHandoffReport(
        String methodName,
        String verdict,
        boolean readyForOptimizerEnablement,
        String ruleArtifactVerdict,
        boolean ruleArtifactPassed,
        boolean ruleArtifactAccepted,
        boolean ruleArtifactAcceptedWithWarnings,
        String acceptanceReason,
        String firstBlockingRuleId,
        String firstFailedRuleId,
        String firstWarningRuleId,
        List<String> blockingReasons,
        List<String> remainingWork
) {
    private static final String VERDICT_READY = "readyForOptimizerEnablementReview";
    private static final String VERDICT_WARNINGS = "notReady/warningsPresent";
    private static final String VERDICT_REJECTED = "notReady/ruleArtifactRejected";
    private static final String VERDICT_FAILED = "notReady/ruleArtifactFailed";

    public GpuIrOptimizationValidationOptimizerReadinessHandoffReport {
        methodName = requireNonBlank(methodName, "methodName");
        verdict = requireNonBlank(verdict, "verdict");
        ruleArtifactVerdict = requireNonBlank(ruleArtifactVerdict, "ruleArtifactVerdict");
        acceptanceReason = requireNonBlank(acceptanceReason, "acceptanceReason");
        firstBlockingRuleId = Objects.requireNonNull(firstBlockingRuleId, "firstBlockingRuleId");
        firstFailedRuleId = Objects.requireNonNull(firstFailedRuleId, "firstFailedRuleId");
        firstWarningRuleId = Objects.requireNonNull(firstWarningRuleId, "firstWarningRuleId");
        blockingReasons = List.copyOf(Objects.requireNonNull(blockingReasons, "blockingReasons"));
        remainingWork = List.copyOf(Objects.requireNonNull(remainingWork, "remainingWork"));
        if (readyForOptimizerEnablement != VERDICT_READY.equals(verdict)) {
            throw new IllegalArgumentException("readyForOptimizerEnablement must match ready verdict");
        }
    }

    public static GpuIrOptimizationValidationOptimizerReadinessHandoffReport from(
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        Objects.requireNonNull(report, "report");
        return from(report, report.consistencyReport());
    }

    public static GpuIrOptimizationValidationOptimizerReadinessHandoffReport from(
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        Objects.requireNonNull(report, "report");
        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance =
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(report, consistencyReport);
        List<String> blockers = blockingReasons(report, acceptance);
        String verdict = verdict(report, acceptance, blockers);
        return new GpuIrOptimizationValidationOptimizerReadinessHandoffReport(
                report.validationReport().methodName(),
                verdict,
                VERDICT_READY.equals(verdict),
                report.verdict(),
                report.passed(),
                acceptance.accepted(),
                acceptance.acceptedWithWarnings(),
                acceptance.reason(),
                acceptance.firstBlockingRuleId(),
                acceptance.firstFailedRuleId(),
                acceptance.firstWarningRuleId(),
                blockers,
                remainingWork(blockers)
        );
    }

    public int blockingReasonCount() {
        return blockingReasons.size();
    }

    public int remainingWorkCount() {
        return remainingWork.size();
    }

    public Optional<String> firstBlockingReason() {
        return blockingReasons.stream().findFirst();
    }

    public Optional<String> firstRemainingWork() {
        return remainingWork.stream().findFirst();
    }

    public Map<String, Long> blockingReasonCounts() {
        return blockingReasons.stream()
                .collect(Collectors.groupingBy(
                        reason -> reason,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerReadinessHandoff");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName);
        values.put(prefix + "Verdict", verdict);
        values.put(prefix + "ReadyForOptimizerEnablement", Boolean.toString(readyForOptimizerEnablement));
        values.put(prefix + "RuleArtifactVerdict", ruleArtifactVerdict);
        values.put(prefix + "RuleArtifactPassed", Boolean.toString(ruleArtifactPassed));
        values.put(prefix + "RuleArtifactAccepted", Boolean.toString(ruleArtifactAccepted));
        values.put(prefix + "RuleArtifactAcceptedWithWarnings", Boolean.toString(ruleArtifactAcceptedWithWarnings));
        values.put(prefix + "AcceptanceReason", acceptanceReason);
        values.put(prefix + "FirstBlockingRuleId", firstBlockingRuleId);
        values.put(prefix + "FirstFailedRuleId", firstFailedRuleId);
        values.put(prefix + "FirstWarningRuleId", firstWarningRuleId);
        values.put(prefix + "BlockingReasons", listSummary(blockingReasons));
        values.put(prefix + "BlockingReasonCount", Integer.toString(blockingReasonCount()));
        values.put(prefix + "BlockingReasonCounts", mapSummary(blockingReasonCounts()));
        values.put(prefix + "RemainingWork", listSummary(remainingWork));
        values.put(prefix + "RemainingWorkCount", Integer.toString(remainingWorkCount()));
        firstBlockingReason().ifPresent(reason -> values.put(prefix + "FirstBlockingReason", reason));
        firstRemainingWork().ifPresent(work -> values.put(prefix + "FirstRemainingWork", work));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        if (readyForOptimizerEnablement) {
            return "optimizer readiness handoff ready method=" + methodName;
        }
        return "optimizer readiness handoff " + verdict
                + " method=" + methodName
                + " blockers=" + blockingReasonCount()
                + firstBlockingReason().map(reason -> " first=" + reason).orElse("");
    }

    public String summary() {
        return "optimizer readiness handoff method=" + methodName
                + " verdict=" + verdict
                + " readyForOptimizerEnablement=" + readyForOptimizerEnablement
                + " ruleArtifactVerdict=" + ruleArtifactVerdict
                + " ruleArtifactAccepted=" + ruleArtifactAccepted
                + " acceptanceReason=" + acceptanceReason
                + " blockingReasons=" + listSummary(blockingReasons)
                + " remainingWork=" + listSummary(remainingWork);
    }

    private static String verdict(
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactAcceptance acceptance,
            List<String> blockers
    ) {
        if (!acceptance.accepted()) {
            return VERDICT_REJECTED;
        }
        if (!report.passed()) {
            return VERDICT_FAILED;
        }
        if (acceptance.acceptedWithWarnings() || report.hasWarnings()) {
            return VERDICT_WARNINGS;
        }
        if (blockers.isEmpty()) {
            return VERDICT_READY;
        }
        return VERDICT_REJECTED;
    }

    private static List<String> blockingReasons(
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactAcceptance acceptance
    ) {
        java.util.LinkedHashSet<String> reasons = new java.util.LinkedHashSet<>();
        if (!acceptance.accepted()) {
            reasons.add("ruleArtifactRejected");
        }
        if (!report.passed()) {
            reasons.add("ruleArtifactFailed");
        }
        if (report.hasBlockingResults()) {
            reasons.add("blockingRuleResultsPresent");
        }
        if (acceptance.acceptedWithWarnings() || report.hasWarnings()) {
            reasons.add("ruleArtifactWarningsPresent");
        }
        if (acceptance.reason().equals("rejected/inconsistentArtifact")) {
            reasons.add("ruleArtifactInconsistent");
        }
        return List.copyOf(reasons);
    }

    private static List<String> remainingWork(List<String> blockingReasons) {
        java.util.LinkedHashSet<String> work = new java.util.LinkedHashSet<>();
        for (String reason : blockingReasons) {
            switch (reason) {
                case "ruleArtifactRejected" -> work.add("produceAcceptedRuleArtifact");
                case "ruleArtifactFailed" -> work.add("clearFailedValidationRules");
                case "blockingRuleResultsPresent" -> work.add("clearBlockingValidationRules");
                case "ruleArtifactWarningsPresent" -> work.add("reviewValidationRuleWarnings");
                case "ruleArtifactInconsistent" -> work.add("fixRuleArtifactConsistency");
                default -> work.add("reviewOptimizerHandoffBlocker:" + reason);
            }
        }
        return List.copyOf(work);
    }

    private static String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String listSummary(List<String> values) {
        return values.stream().collect(Collectors.joining(",", "[", "]"));
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
