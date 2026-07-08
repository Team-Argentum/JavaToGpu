package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only index of the first actionable blocker across safety and optimizer readiness layers.
 *
 * <p>The index intentionally does not enable any rewrite path. It only turns the existing safety,
 * CSE, and auto-vectorization evidence into one stable CI/report surface so users can see the next
 * thing to fix without digging through every nested artifact.</p>
 */
public record GpuIrOptimizationValidationOptimizerBlockerIndex(
        String methodName,
        String verdict,
        String source,
        String family,
        String reason,
        String remainingWork,
        String hint,
        String cseFamily,
        String autoVectorizationFamily
) {
    public GpuIrOptimizationValidationOptimizerBlockerIndex {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        verdict = requireNonBlank(verdict, "verdict");
        source = requireNonBlank(source, "source");
        family = requireNonBlank(family, "family");
        reason = requireNonBlank(reason, "reason");
        remainingWork = requireNonBlank(remainingWork, "remainingWork");
        hint = requireNonBlank(hint, "hint");
        cseFamily = requireNonBlank(cseFamily, "cseFamily");
        autoVectorizationFamily = requireNonBlank(autoVectorizationFamily, "autoVectorizationFamily");
    }

    public static GpuIrOptimizationValidationOptimizerBlockerIndex from(
            GpuIrOptimizationValidationReport report
    ) {
        Objects.requireNonNull(report, "report");
        GpuIrCommonSubexpressionRewriteBlockerExplanation cseBlocker =
                report.commonSubexpressionArtifactSnapshot().rewriteBlockerExplanation();
        GpuIrAutoVectorizationBlockerExplanation autoVectorizationBlocker =
                report.autoVectorizationArtifactSnapshot().blockerExplanation();

        if (report.hasSafetyError()) {
            return new GpuIrOptimizationValidationOptimizerBlockerIndex(
                    report.methodName(),
                    "blocked",
                    "safety",
                    "safety.validationError",
                    report.safetyError().orElseThrow(),
                    "fixIrSafetyError",
                    "Fix the IR safety validation error before reviewing optimizer readiness.",
                    cseBlocker.firstBlockerFamily(),
                    autoVectorizationBlocker.firstBlockerFamily()
            );
        }
        if (cseBlocker.blocked()) {
            return fromCseBlocker(report.methodName(), cseBlocker, autoVectorizationBlocker);
        }
        if (autoVectorizationBlocker.blocked()) {
            return fromAutoVectorizationBlocker(report.methodName(), cseBlocker, autoVectorizationBlocker);
        }
        return new GpuIrOptimizationValidationOptimizerBlockerIndex(
                report.methodName(),
                "ready",
                "none",
                "none",
                "none",
                "none",
                "Safety and optimizer readiness blockers are clear for the current read-only artifact scope.",
                cseBlocker.firstBlockerFamily(),
                autoVectorizationBlocker.firstBlockerFamily()
        );
    }

    public boolean blocked() {
        return "blocked".equals(verdict);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerBlocker");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName);
        values.put(prefix + "Verdict", verdict);
        values.put(prefix + "Blocked", Boolean.toString(blocked()));
        values.put(prefix + "Source", source);
        values.put(prefix + "Family", family);
        values.put(prefix + "Reason", reason);
        values.put(prefix + "RemainingWork", remainingWork);
        values.put(prefix + "Hint", hint);
        values.put(prefix + "CseFamily", cseFamily);
        values.put(prefix + "AutoVectorizationFamily", autoVectorizationFamily);
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "optimizer blocker method=" + methodName
                + " verdict=" + verdict
                + " source=" + source
                + " family=" + family
                + " remainingWork=" + remainingWork;
    }

    public String summary() {
        return ciSummaryLine()
                + " reason=" + reason
                + " cseFamily=" + cseFamily
                + " autoVectorizationFamily=" + autoVectorizationFamily
                + " hint=" + hint;
    }

    private static GpuIrOptimizationValidationOptimizerBlockerIndex fromCseBlocker(
            String methodName,
            GpuIrCommonSubexpressionRewriteBlockerExplanation cseBlocker,
            GpuIrAutoVectorizationBlockerExplanation autoVectorizationBlocker
    ) {
        return new GpuIrOptimizationValidationOptimizerBlockerIndex(
                methodName,
                "blocked",
                "cseRewritePolicy",
                cseBlocker.firstBlockerFamily(),
                cseBlocker.firstBlockingDiagnostic()
                        .map(diagnostic -> diagnostic.reason().name())
                        .orElse("cseRewritePolicyBlocked"),
                cseBlocker.firstRemainingWork(),
                firstField(cseBlocker.artifactFields(), "cseRewriteBlockerFirstHint")
                        .orElse("Resolve the first CSE rewrite policy blocker before enabling mutation."),
                cseBlocker.firstBlockerFamily(),
                autoVectorizationBlocker.firstBlockerFamily()
        );
    }

    private static GpuIrOptimizationValidationOptimizerBlockerIndex fromAutoVectorizationBlocker(
            String methodName,
            GpuIrCommonSubexpressionRewriteBlockerExplanation cseBlocker,
            GpuIrAutoVectorizationBlockerExplanation autoVectorizationBlocker
    ) {
        return new GpuIrOptimizationValidationOptimizerBlockerIndex(
                methodName,
                "blocked",
                "autoVectorization",
                autoVectorizationBlocker.firstBlockerFamily(),
                autoVectorizationBlocker.firstBlockingReason().orElse("autoVectorizationBlocked"),
                autoVectorizationBlocker.firstRemainingWork(),
                autoVectorizationBlocker.artifactFields().get("autoVectorizationBlockerFirstHint"),
                cseBlocker.firstBlockerFamily(),
                autoVectorizationBlocker.firstBlockerFamily()
        );
    }

    private static Optional<String> firstField(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
