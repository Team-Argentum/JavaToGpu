package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds proposed CSE rewrite plans without mutating IR.
 */
public final class GpuIrCommonSubexpressionRewritePlanner {
    private final GpuIrCommonSubexpressionClassifier classifier = new GpuIrCommonSubexpressionClassifier();
    private final GpuIrCommonSubexpressionScopeClassifier scopeClassifier = new GpuIrCommonSubexpressionScopeClassifier();
    private final GpuIrCommonSubexpressionMutationGuard mutationGuard = new GpuIrCommonSubexpressionMutationGuard();

    public List<GpuIrCommonSubexpressionRewritePlan> plan(GpuIrMethod method, GpuIrCommonSubexpressionReport report) {
        return planReport(method, report).plans();
    }

    public GpuIrCommonSubexpressionRewritePlanReport planReport(GpuIrMethod method, GpuIrCommonSubexpressionReport report) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(report, "report");
        List<GpuIrCommonSubexpressionRewritePlan> plans = new ArrayList<>();
        List<GpuIrCommonSubexpressionSkippedCandidate> skippedCandidates = new ArrayList<>();

        for (GpuIrCommonSubexpression candidate : report.topCandidates()) {
            GpuIrCommonSubexpressionKind kind = classifier.classify(candidate);
            GpuIrCommonSubexpressionScope scope = scopeClassifier.classify(candidate);
            GpuIrCommonSubexpressionSkipReason skipReason = skipReason(method, candidate, kind, scope);
            if (skipReason == null) {
                plans.add(planCandidate(plans.size(), candidate));
            } else {
                skippedCandidates.add(new GpuIrCommonSubexpressionSkippedCandidate(candidate, kind, scope, skipReason));
            }
        }

        return new GpuIrCommonSubexpressionRewritePlanReport(plans, skippedCandidates);
    }

    private GpuIrCommonSubexpressionSkipReason skipReason(
            GpuIrMethod method,
            GpuIrCommonSubexpression candidate,
            GpuIrCommonSubexpressionKind kind,
            GpuIrCommonSubexpressionScope scope
    ) {
        if (kind != GpuIrCommonSubexpressionKind.LOCAL_REUSE) {
            return GpuIrCommonSubexpressionSkipReason.NOT_LOCAL_REUSE;
        }
        if (scope != GpuIrCommonSubexpressionScope.STRAIGHT_LINE) {
            return GpuIrCommonSubexpressionSkipReason.CONTROL_FLOW_BOUNDARY;
        }
        if (!mutationGuard.isStableBetweenOccurrences(method, candidate)) {
            return GpuIrCommonSubexpressionSkipReason.MUTATED_BETWEEN_OCCURRENCES;
        }
        return null;
    }

    private GpuIrCommonSubexpressionRewritePlan planCandidate(int index, GpuIrCommonSubexpression candidate) {
        return new GpuIrCommonSubexpressionRewritePlan(
                "__gpu_cse_" + index,
                candidate.fingerprint(),
                candidate.estimatedReuseSavings(),
                candidate.locations()
        );
    }
}
