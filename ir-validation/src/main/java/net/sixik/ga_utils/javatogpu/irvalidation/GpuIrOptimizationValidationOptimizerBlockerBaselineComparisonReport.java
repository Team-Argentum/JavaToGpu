package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only comparison between a stored optimizer-blocker baseline and current blocker state.
 */
public record GpuIrOptimizationValidationOptimizerBlockerBaselineComparisonReport(
        GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot baseline,
        GpuIrOptimizationValidationOptimizerBlockerIndex current
) {
    public static final String DEFAULT_PREFIX = "optimizerBlockerBaselineComparison";

    public GpuIrOptimizationValidationOptimizerBlockerBaselineComparisonReport {
        baseline = Objects.requireNonNull(baseline, "baseline");
        current = Objects.requireNonNull(current, "current");
        if (!baseline.methodName().equals(current.methodName())) {
            throw new IllegalArgumentException("current method must match stored optimizer blocker baseline method");
        }
    }

    public static GpuIrOptimizationValidationOptimizerBlockerBaselineComparisonReport from(
            GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot baseline,
            GpuIrOptimizationValidationOptimizerBlockerIndex current
    ) {
        return new GpuIrOptimizationValidationOptimizerBlockerBaselineComparisonReport(baseline, current);
    }

    public String methodName() {
        return baseline.methodName();
    }

    public String outcome() {
        if (scoreDelta() > 0) {
            return "improved";
        }
        if (scoreDelta() < 0) {
            return "regressed";
        }
        return changed() ? "changed" : "unchanged";
    }

    public int baselineScore() {
        return blockerScore(baseline.verdict(), baseline.source());
    }

    public int currentScore() {
        return blockerScore(current.verdict(), current.source());
    }

    public int scoreDelta() {
        return currentScore() - baselineScore();
    }

    public boolean improved() {
        return "improved".equals(outcome());
    }

    public boolean regressed() {
        return "regressed".equals(outcome());
    }

    public boolean changed() {
        return sourceChanged() || familyChanged() || remainingWorkChanged() || verdictChanged();
    }

    public boolean sourceChanged() {
        return !baseline.source().equals(current.source());
    }

    public boolean familyChanged() {
        return !baseline.family().equals(current.family());
    }

    public boolean remainingWorkChanged() {
        return !baseline.remainingWork().equals(current.remainingWork());
    }

    public boolean verdictChanged() {
        return !baseline.verdict().equals(current.verdict());
    }

    public String sourceTransition() {
        return baseline.source() + "->" + current.source();
    }

    public String familyTransition() {
        return baseline.family() + "->" + current.family();
    }

    public String remainingWorkTransition() {
        return baseline.remainingWork() + "->" + current.remainingWork();
    }

    public Optional<String> firstChangedDimension() {
        if (verdictChanged()) {
            return Optional.of("verdict");
        }
        if (sourceChanged()) {
            return Optional.of("source");
        }
        if (familyChanged()) {
            return Optional.of("family");
        }
        if (remainingWorkChanged()) {
            return Optional.of("remainingWork");
        }
        return Optional.empty();
    }

    public Map<String, String> artifactFields() {
        return artifactFields(DEFAULT_PREFIX);
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName());
        values.put(prefix + "Outcome", outcome());
        values.put(prefix + "Improved", Boolean.toString(improved()));
        values.put(prefix + "Regressed", Boolean.toString(regressed()));
        values.put(prefix + "Changed", Boolean.toString(changed()));
        values.put(prefix + "BaselineScore", Integer.toString(baselineScore()));
        values.put(prefix + "CurrentScore", Integer.toString(currentScore()));
        values.put(prefix + "ScoreDelta", Integer.toString(scoreDelta()));
        values.put(prefix + "BaselineVerdict", baseline.verdict());
        values.put(prefix + "CurrentVerdict", current.verdict());
        values.put(prefix + "BaselineSource", baseline.source());
        values.put(prefix + "CurrentSource", current.source());
        values.put(prefix + "BaselineFamily", baseline.family());
        values.put(prefix + "CurrentFamily", current.family());
        values.put(prefix + "BaselineRemainingWork", baseline.remainingWork());
        values.put(prefix + "CurrentRemainingWork", current.remainingWork());
        values.put(prefix + "SourceChanged", Boolean.toString(sourceChanged()));
        values.put(prefix + "FamilyChanged", Boolean.toString(familyChanged()));
        values.put(prefix + "RemainingWorkChanged", Boolean.toString(remainingWorkChanged()));
        values.put(prefix + "VerdictChanged", Boolean.toString(verdictChanged()));
        values.put(prefix + "SourceTransition", sourceTransition());
        values.put(prefix + "FamilyTransition", familyTransition());
        values.put(prefix + "RemainingWorkTransition", remainingWorkTransition());
        firstChangedDimension().ifPresent(dimension -> values.put(prefix + "FirstChangedDimension", dimension));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "optimizer blocker baseline comparison method=" + methodName()
                + " outcome=" + outcome()
                + " scoreDelta=" + scoreDelta()
                + " sourceTransition=" + sourceTransition()
                + " familyTransition=" + familyTransition()
                + " remainingWorkTransition=" + remainingWorkTransition()
                + firstChangedDimension().map(dimension -> " firstChangedDimension=" + dimension).orElse("");
    }

    public String summary() {
        return ciSummaryLine();
    }

    private static int blockerScore(String verdict, String source) {
        if ("ready".equals(verdict)) {
            return 4;
        }
        return switch (source) {
            case "autoVectorization" -> 3;
            case "cseRewritePolicy" -> 2;
            case "safety" -> 1;
            default -> 0;
        };
    }
}
