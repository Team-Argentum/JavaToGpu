package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resolves typed rewrite previews against the current IR without mutating the method tree.
 */
public final class GpuIrAutoVectorizationRewriteOperationResolver {
    public GpuIrAutoVectorizationResolvedRewriteOperations resolve(
            GpuIrMethod method,
            GpuIrAutoVectorizationRewritePlan plan
    ) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(plan, "plan");
        if (method.statements() == null) {
            throw new IllegalArgumentException("Auto-vectorization rewrite dry-run failed for "
                    + plan.methodName() + ": method statements are missing");
        }

        List<GpuIrAutoVectorizationResolvedInsertionOperation> insertions = new ArrayList<>();
        List<GpuIrAutoVectorizationResolvedReplacementOperation> replacements = new ArrayList<>();
        for (int index = 0; index < plan.candidates().size(); index++) {
            GpuIrAutoVectorizationRewriteCandidatePreview candidate = plan.candidates().get(index);
            GpuIrForLoop loop = requireLoop(method, plan, candidate.loopLocation());
            int statementIndex = requireStatementIndex(plan, candidate.loopLocation());
            int bodyStatementCount = loop.body() == null ? 0 : loop.body().size();
            int bodyAssignmentCount = assignmentCount(loop);

            GpuIrAutoVectorizationRewriteInsertionOperation insertion = plan.insertionOperations().get(index);
            insertions.add(new GpuIrAutoVectorizationResolvedInsertionOperation(
                    insertion.loopLocation(),
                    statementIndex,
                    insertion.vectorType(),
                    insertion.startInclusive(),
                    insertion.endExclusive(),
                    bodyStatementCount,
                    bodyAssignmentCount,
                    insertion.plannedVectorReads()
            ));

            GpuIrAutoVectorizationRewriteReplacementOperation replacement = plan.replacementOperations().get(index);
            replacements.add(new GpuIrAutoVectorizationResolvedReplacementOperation(
                    replacement.loopLocation(),
                    statementIndex,
                    replacement.inductionVariable(),
                    replacement.startInclusive(),
                    replacement.endExclusive(),
                    bodyStatementCount,
                    bodyAssignmentCount,
                    replacement.plannedVectorWrites()
            ));
        }
        return new GpuIrAutoVectorizationResolvedRewriteOperations(plan.methodName(), insertions, replacements);
    }

    private GpuIrForLoop requireLoop(
            GpuIrMethod method,
            GpuIrAutoVectorizationRewritePlan plan,
            String loopLocation
    ) {
        int statementIndex = requireStatementIndex(plan, loopLocation);
        if (statementIndex >= method.statements().size()) {
            throw new IllegalArgumentException("Auto-vectorization rewrite dry-run failed for "
                    + plan.methodName()
                    + ": loop location " + loopLocation
                    + " is outside method statements");
        }
        GpuIrStatement statement = method.statements().get(statementIndex);
        if (statement instanceof GpuIrForLoop loop) {
            return loop;
        }
        throw new IllegalArgumentException("Auto-vectorization rewrite dry-run failed for "
                + plan.methodName()
                + ": loop location " + loopLocation
                + " does not point to a for-loop");
    }

    private int requireStatementIndex(GpuIrAutoVectorizationRewritePlan plan, String loopLocation) {
        return GpuIrCommonSubexpressionLocation.requireTopLevelStatementIndex(
                loopLocation,
                "Auto-vectorization rewrite dry-run failed for " + plan.methodName()
        );
    }

    private int assignmentCount(GpuIrForLoop loop) {
        if (loop.body() == null) {
            return 0;
        }
        int count = 0;
        for (GpuIrStatement statement : loop.body()) {
            if (statement instanceof GpuIrAssignment) {
                count++;
            }
        }
        return count;
    }
}
