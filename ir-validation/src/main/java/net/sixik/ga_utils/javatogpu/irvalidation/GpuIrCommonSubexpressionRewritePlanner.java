package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrPrivateArrayDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds proposed CSE rewrite plans without mutating IR.
 */
public final class GpuIrCommonSubexpressionRewritePlanner {
    private final GpuIrCommonSubexpressionClassifier classifier = new GpuIrCommonSubexpressionClassifier();
    private final GpuIrCommonSubexpressionScopeClassifier scopeClassifier = new GpuIrCommonSubexpressionScopeClassifier();
    private final GpuIrCommonSubexpressionMutationGuard mutationGuard = new GpuIrCommonSubexpressionMutationGuard();
    private final GpuIrCommonSubexpressionDominanceGuard dominanceGuard = new GpuIrCommonSubexpressionDominanceGuard();

    public List<GpuIrCommonSubexpressionRewritePlan> plan(GpuIrMethod method, GpuIrCommonSubexpressionReport report) {
        return planReport(method, report).plans();
    }

    public List<GpuIrCommonSubexpressionRewritePlan> plan(GpuIrCompiledMethod method, GpuIrCommonSubexpressionReport report) {
        return planReport(method, report).plans();
    }

    public GpuIrCommonSubexpressionRewritePlanReport planReport(GpuIrMethod method, GpuIrCommonSubexpressionReport report) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(report, "report");
        return planReport(method, report, existingLocalNames(method));
    }

    public GpuIrCommonSubexpressionRewritePlanReport planReport(GpuIrCompiledMethod method, GpuIrCommonSubexpressionReport report) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(report, "report");
        Set<String> usedNames = existingParameterNames(method);
        usedNames.addAll(existingLocalNames(method.irMethod()));
        return planReport(method.irMethod(), report, usedNames);
    }

    private GpuIrCommonSubexpressionRewritePlanReport planReport(
            GpuIrMethod method,
            GpuIrCommonSubexpressionReport report,
            Set<String> usedLocalNames
    ) {
        List<GpuIrCommonSubexpressionRewritePlan> plans = new ArrayList<>();
        List<GpuIrCommonSubexpressionSkippedCandidate> skippedCandidates = new ArrayList<>();
        Set<String> plannedCoverageLocations = new HashSet<>();
        int temporaryIndex = 0;

        for (GpuIrCommonSubexpression candidate : report.topCandidates()) {
            GpuIrCommonSubexpressionKind kind = classifier.classify(candidate);
            GpuIrCommonSubexpressionScope scope = scopeClassifier.classify(candidate);
            GpuIrCommonSubexpressionDominanceStatus dominanceStatus = dominanceGuard.status(candidate);
            GpuIrCommonSubexpressionSkipReason skipReason = skipReason(method, candidate, kind, scope, dominanceStatus);
            if (skipReason == null && isCoveredByExistingParentRewrite(candidate, plannedCoverageLocations)) {
                skipReason = GpuIrCommonSubexpressionSkipReason.COVERED_BY_PARENT_REWRITE;
            }
            if (skipReason == null) {
                String temporaryName = nextTemporaryName(usedLocalNames, temporaryIndex);
                usedLocalNames.add(temporaryName);
                temporaryIndex++;
                GpuIrCommonSubexpressionRewritePlan plan = planCandidate(temporaryName, candidate);
                plans.add(plan);
                plannedCoverageLocations.addAll(plan.replacementLocationsAfterAnchor());
            } else {
                skippedCandidates.add(new GpuIrCommonSubexpressionSkippedCandidate(candidate, kind, scope, skipReason, dominanceStatus));
            }
        }

        return new GpuIrCommonSubexpressionRewritePlanReport(plans, skippedCandidates);
    }

    private boolean isCoveredByExistingParentRewrite(
            GpuIrCommonSubexpression candidate,
            Set<String> plannedCoverageLocations
    ) {
        int anchorStatementIndex = dominanceGuard.firstDominatingStatementIndex(candidate)
                .orElseThrow(() -> new IllegalStateException("rewrite candidate has no dominating insertion anchor"));
        boolean hasPostAnchorLocation = false;
        for (String location : candidate.locations()) {
            int locationStatementIndex = GpuIrCommonSubexpressionLocation.parse(location)
                    .topLevelStatementIndex()
                    .orElse(-1);
            if (locationStatementIndex <= anchorStatementIndex) {
                continue;
            }
            hasPostAnchorLocation = true;
            if (!isCoveredByExistingParentRewrite(location, plannedCoverageLocations)) {
                return false;
            }
        }
        return hasPostAnchorLocation;
    }

    private boolean isCoveredByExistingParentRewrite(String location, Set<String> plannedCoverageLocations) {
        for (String plannedLocation : plannedCoverageLocations) {
            if (location.startsWith(plannedLocation + ".")) {
                return true;
            }
        }
        return false;
    }

    private GpuIrCommonSubexpressionSkipReason skipReason(
            GpuIrMethod method,
            GpuIrCommonSubexpression candidate,
            GpuIrCommonSubexpressionKind kind,
            GpuIrCommonSubexpressionScope scope,
            GpuIrCommonSubexpressionDominanceStatus dominanceStatus
    ) {
        if (kind != GpuIrCommonSubexpressionKind.LOCAL_REUSE) {
            return GpuIrCommonSubexpressionSkipReason.NOT_LOCAL_REUSE;
        }
        if (scope != GpuIrCommonSubexpressionScope.STRAIGHT_LINE) {
            return GpuIrCommonSubexpressionSkipReason.CONTROL_FLOW_BOUNDARY;
        }
        if (!dominanceGuard.hasDominatingOccurrence(dominanceStatus)) {
            return GpuIrCommonSubexpressionSkipReason.NO_DOMINATING_FIRST_OCCURRENCE;
        }
        if (!mutationGuard.isStableBetweenOccurrences(method, candidate)) {
            return GpuIrCommonSubexpressionSkipReason.MUTATED_BETWEEN_OCCURRENCES;
        }
        return null;
    }

    private GpuIrCommonSubexpressionRewritePlan planCandidate(String temporaryName, GpuIrCommonSubexpression candidate) {
        int insertionStatementIndex = dominanceGuard.firstDominatingStatementIndex(candidate)
                .orElseThrow(() -> new IllegalStateException("rewrite candidate has no dominating insertion anchor"));
        return new GpuIrCommonSubexpressionRewritePlan(
                temporaryName,
                candidate.fingerprint(),
                candidate.estimatedReuseSavings(),
                insertionStatementIndex,
                candidate.locations().get(0),
                candidate.locations()
        );
    }

    private Set<String> existingLocalNames(GpuIrMethod method) {
        Set<String> names = new HashSet<>();
        for (GpuIrStatement statement : method.statements()) {
            if (statement instanceof GpuIrVariableDeclaration declaration) {
                names.add(declaration.name());
            } else if (statement instanceof GpuIrPrivateArrayDeclaration declaration) {
                names.add(declaration.name());
            }
        }
        return names;
    }

    private Set<String> existingParameterNames(GpuIrCompiledMethod method) {
        Set<String> names = new HashSet<>();
        for (ParsedGpuParameter parameter : method.parsedMethod().parameters()) {
            names.add(parameter.name());
        }
        return names;
    }

    private String nextTemporaryName(Set<String> usedLocalNames, int startIndex) {
        int index = startIndex;
        String candidate;
        do {
            candidate = "__gpu_cse_" + index;
            index++;
        } while (usedLocalNames.contains(candidate));
        return candidate;
    }
}
