package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;

import java.util.List;
import java.util.Objects;

/**
 * Safe entrypoint for future auto-vectorization rewrites.
 *
 * <p>The current implementation intentionally does not mutate IR. It only proves that callers
 * go through the same readiness and guard policy gates that a future mutating vector rewrite will
 * have to satisfy before replacing scalar lane loops.</p>
 */
public final class GpuIrAutoVectorizationRewriteApplicator {
    public GpuIrMethod apply(GpuIrMethod method, GpuIrAutoVectorizationPreview preview) {
        Objects.requireNonNull(method, "method");
        requireApplicable(preview);
        dryRunValidate(method, preview.rewritePlan());
        return method;
    }

    public GpuIrMethod apply(GpuIrCompiledMethod method, GpuIrAutoVectorizationPreview preview) {
        Objects.requireNonNull(method, "method");
        return apply(method.irMethod(), preview);
    }

    public void requireApplicable(GpuIrAutoVectorizationPreview preview) {
        Objects.requireNonNull(preview, "preview");
        if (preview.canApplyRewrite()) {
            return;
        }

        String blockingDiagnostic = preview.firstBlockingDiagnosticSummary().orElse("no rewrite-ready candidate");
        throw new IllegalArgumentException("Auto-vectorization rewrite cannot be applied for "
                + preview.methodName()
                + ": readiness=" + preview.rewriteReadiness().artifactValue()
                + " canApplyRewrite=" + preview.canApplyRewrite()
                + " policyCanRewrite=" + preview.rewritePolicy().canRewrite()
                + " blocking=" + blockingDiagnostic);
    }

    public void dryRunValidate(GpuIrMethod method, GpuIrAutoVectorizationRewritePlan plan) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(plan, "plan");
        if (method.statements() == null) {
            throw new IllegalArgumentException("Auto-vectorization rewrite dry-run failed for "
                    + plan.methodName() + ": method statements are missing");
        }

        List<GpuIrAutoVectorizationRewriteCandidatePreview> candidates = plan.candidates();
        for (int index = 0; index < candidates.size(); index++) {
            GpuIrAutoVectorizationRewriteCandidatePreview candidate = candidates.get(index);
            GpuIrAutoVectorizationRewriteInsertionOperation insertion = plan.insertionOperations().get(index);
            GpuIrAutoVectorizationRewriteReplacementOperation replacement = plan.replacementOperations().get(index);
            validateCandidateLoop(method, plan, candidate);
            validateInsertionOperation(plan, candidate, insertion);
            validateReplacementOperation(plan, candidate, replacement);
        }
    }

    private void validateCandidateLoop(
            GpuIrMethod method,
            GpuIrAutoVectorizationRewritePlan plan,
            GpuIrAutoVectorizationRewriteCandidatePreview candidate
    ) {
        int statementIndex = statementIndex(candidate.loopLocation(), plan.methodName());
        if (statementIndex >= method.statements().size()) {
            throw new IllegalArgumentException("Auto-vectorization rewrite dry-run failed for "
                    + plan.methodName()
                    + ": loop location " + candidate.loopLocation()
                    + " is outside method statements");
        }
        GpuIrStatement statement = method.statements().get(statementIndex);
        if (!(statement instanceof GpuIrForLoop)) {
            throw new IllegalArgumentException("Auto-vectorization rewrite dry-run failed for "
                    + plan.methodName()
                    + ": loop location " + candidate.loopLocation()
                    + " does not point to a for-loop");
        }
    }

    private void validateInsertionOperation(
            GpuIrAutoVectorizationRewritePlan plan,
            GpuIrAutoVectorizationRewriteCandidatePreview candidate,
            GpuIrAutoVectorizationRewriteInsertionOperation insertion
    ) {
        if (!candidate.loopLocation().equals(insertion.loopLocation())) {
            throw mismatch(plan, candidate, "insertion loopLocation", candidate.loopLocation(), insertion.loopLocation());
        }
        if (!candidate.vectorType().equals(insertion.vectorType())) {
            throw mismatch(plan, candidate, "insertion vectorType", candidate.vectorType(), insertion.vectorType());
        }
        if (candidate.startInclusive() != insertion.startInclusive()) {
            throw mismatch(plan, candidate, "insertion startInclusive", candidate.startInclusive(), insertion.startInclusive());
        }
        if (candidate.endExclusive() != insertion.endExclusive()) {
            throw mismatch(plan, candidate, "insertion endExclusive", candidate.endExclusive(), insertion.endExclusive());
        }
        if (!candidate.plannedVectorReads().equals(insertion.plannedVectorReads())) {
            throw mismatch(plan, candidate, "insertion plannedVectorReads", candidate.plannedVectorReads(), insertion.plannedVectorReads());
        }
    }

    private void validateReplacementOperation(
            GpuIrAutoVectorizationRewritePlan plan,
            GpuIrAutoVectorizationRewriteCandidatePreview candidate,
            GpuIrAutoVectorizationRewriteReplacementOperation replacement
    ) {
        if (!candidate.loopLocation().equals(replacement.loopLocation())) {
            throw mismatch(plan, candidate, "replacement loopLocation", candidate.loopLocation(), replacement.loopLocation());
        }
        if (!candidate.inductionVariable().equals(replacement.inductionVariable())) {
            throw mismatch(plan, candidate, "replacement inductionVariable", candidate.inductionVariable(), replacement.inductionVariable());
        }
        if (candidate.startInclusive() != replacement.startInclusive()) {
            throw mismatch(plan, candidate, "replacement startInclusive", candidate.startInclusive(), replacement.startInclusive());
        }
        if (candidate.endExclusive() != replacement.endExclusive()) {
            throw mismatch(plan, candidate, "replacement endExclusive", candidate.endExclusive(), replacement.endExclusive());
        }
        if (!candidate.plannedVectorWrites().equals(replacement.plannedVectorWrites())) {
            throw mismatch(plan, candidate, "replacement plannedVectorWrites", candidate.plannedVectorWrites(), replacement.plannedVectorWrites());
        }
    }

    private int statementIndex(String location, String methodName) {
        if (!location.startsWith("stmt[")) {
            throw new IllegalArgumentException("Auto-vectorization rewrite dry-run failed for "
                    + methodName + ": unsupported loop location " + location);
        }
        int closingBracket = location.indexOf(']');
        if (closingBracket < 0) {
            throw new IllegalArgumentException("Auto-vectorization rewrite dry-run failed for "
                    + methodName + ": malformed loop location " + location);
        }
        try {
            return Integer.parseInt(location.substring("stmt[".length(), closingBracket));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Auto-vectorization rewrite dry-run failed for "
                    + methodName + ": malformed loop location " + location, exception);
        }
    }

    private IllegalArgumentException mismatch(
            GpuIrAutoVectorizationRewritePlan plan,
            GpuIrAutoVectorizationRewriteCandidatePreview candidate,
            String field,
            Object expected,
            Object actual
    ) {
        return new IllegalArgumentException("Auto-vectorization rewrite dry-run failed for "
                + plan.methodName()
                + " at " + candidate.loopLocation()
                + ": " + field
                + " expected=" + expected
                + " actual=" + actual);
    }
}
