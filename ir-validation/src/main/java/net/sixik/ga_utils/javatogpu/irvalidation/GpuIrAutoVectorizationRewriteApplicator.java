package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrFieldAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrStructInit;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Safe entrypoint for future auto-vectorization rewrites.
 *
 * <p>The production-facing {@link #apply(GpuIrMethod, GpuIrAutoVectorizationPreview)} path
 * intentionally stays a no-op safety gate. Explicit prototype helpers may mutate only the small
 * expression families that already have dedicated detector, report, and equivalence coverage.</p>
 */
public final class GpuIrAutoVectorizationRewriteApplicator {
    private final GpuIrAutoVectorizationRewriteOperationResolver operationResolver;
    private final GpuIrAutoVectorizationPrototypeRewriteShapeDetector prototypeShapeDetector;
    private final GpuIrAutoVectorizationPrototypeLaneExpressionRewriter laneExpressionRewriter;

    public GpuIrAutoVectorizationRewriteApplicator() {
        this(
                new GpuIrAutoVectorizationRewriteOperationResolver(),
                new GpuIrAutoVectorizationPrototypeRewriteShapeDetector()
        );
    }

    public GpuIrAutoVectorizationRewriteApplicator(
            GpuIrAutoVectorizationRewriteOperationResolver operationResolver
    ) {
        this(operationResolver, new GpuIrAutoVectorizationPrototypeRewriteShapeDetector());
    }

    GpuIrAutoVectorizationRewriteApplicator(
            GpuIrAutoVectorizationRewriteOperationResolver operationResolver,
            GpuIrAutoVectorizationPrototypeRewriteShapeDetector prototypeShapeDetector
    ) {
        this.operationResolver = Objects.requireNonNull(operationResolver, "operationResolver");
        this.prototypeShapeDetector = Objects.requireNonNull(prototypeShapeDetector, "prototypeShapeDetector");
        this.laneExpressionRewriter = new GpuIrAutoVectorizationPrototypeLaneExpressionRewriter(prototypeShapeDetector);
    }

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

    /**
     * Opt-in prototype rewrite for the currently proven lane-wise expression shapes.
     *
     * <p>The production {@link #apply(GpuIrMethod, GpuIrAutoVectorizationPreview)} path stays a
     * no-op safety gate. This method exists so tests and future integration points can validate
     * narrow lane-copy, unary lane, lane-wise binary, and lane/literal binary IR mutations behind
     * the same preview/dry-run boundary.</p>
     */
    public GpuIrMethod rewritePrototype(GpuIrMethod method, GpuIrAutoVectorizationPreview preview) {
        return rewritePrototypeReport(method, preview).method();
    }

    public GpuIrAutoVectorizationPrototypeRewriteReport rewritePrototypeReport(
            GpuIrMethod method,
            GpuIrAutoVectorizationPreview preview
    ) {
        Objects.requireNonNull(method, "method");
        requireApplicable(preview);
        dryRunValidate(method, preview.rewritePlan());
        GpuIrAutoVectorizationResolvedRewriteOperations resolved = resolveOperations(method, preview.rewritePlan());
        if (resolved.insertions().isEmpty()) {
            return new GpuIrAutoVectorizationPrototypeRewriteReport(method, List.of());
        }

        List<GpuIrStatement> rewrittenStatements = new ArrayList<>();
        List<GpuIrAutoVectorizationPrototypeAppliedRewrite> appliedRewrites = new ArrayList<>();
        for (int statementIndex = 0; statementIndex < method.statements().size(); statementIndex++) {
            int candidateIndex = candidateIndexAtStatement(resolved, statementIndex);
            if (candidateIndex < 0) {
                rewrittenStatements.add(method.statements().get(statementIndex));
                continue;
            }
            GpuIrAutoVectorizationPrototypeRewriteShape shape = prototypeShapeDetector.detect(
                    (GpuIrForLoop) method.statements().get(statementIndex),
                    preview.rewritePlan().candidates().get(candidateIndex),
                    resolved.insertions().get(candidateIndex)
            );
            rewrittenStatements.addAll(rewriteSingleLaneCandidate(shape));
            appliedRewrites.add(appliedRewrite(shape));
        }
        return new GpuIrAutoVectorizationPrototypeRewriteReport(
                new GpuIrMethod(method.name(), rewrittenStatements),
                appliedRewrites
        );
    }

    public GpuIrCompiledMethod rewritePrototype(GpuIrCompiledMethod method, GpuIrAutoVectorizationPreview preview) {
        Objects.requireNonNull(method, "method");
        return new GpuIrCompiledMethod(
                method.parsedMethod(),
                rewritePrototype(method.irMethod(), preview),
                method.emittedName(),
                method.helperDependencies()
        );
    }

    private GpuIrAutoVectorizationPrototypeAppliedRewrite appliedRewrite(
            GpuIrAutoVectorizationPrototypeRewriteShape shape
    ) {
        return new GpuIrAutoVectorizationPrototypeAppliedRewrite(
                shape.loopLocation(),
                shape.statementIndex(),
                shape.vectorType(),
                shape.startInclusive(),
                shape.endExclusive(),
                shape.targetArrays(),
                shape.sourceArrays(),
                shape.expressionKind(),
                shape.binaryOperator(),
                shape.unaryOperator()
        );
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
        GpuIrAutoVectorizationRewriteDryRunReport report = dryRun(method, plan);
        if (report.successful()) {
            return;
        }
        throw new IllegalArgumentException(report.firstDiagnostic());
    }

    public GpuIrAutoVectorizationRewriteDryRunReport dryRun(GpuIrMethod method, GpuIrAutoVectorizationRewritePlan plan) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(plan, "plan");
        List<String> diagnostics = new ArrayList<>();
        if (method.statements() == null) {
            diagnostics.add("Auto-vectorization rewrite dry-run failed for "
                    + plan.methodName() + ": method statements are missing");
            return dryRunReport(plan, diagnostics);
        }

        List<GpuIrAutoVectorizationRewriteCandidatePreview> candidates = plan.candidates();
        for (int index = 0; index < candidates.size(); index++) {
            GpuIrAutoVectorizationRewriteCandidatePreview candidate = candidates.get(index);
            GpuIrAutoVectorizationRewriteInsertionOperation insertion = plan.insertionOperations().get(index);
            GpuIrAutoVectorizationRewriteReplacementOperation replacement = plan.replacementOperations().get(index);
            validateInsertionOperation(plan, candidate, insertion, diagnostics);
            validateReplacementOperation(plan, candidate, replacement, diagnostics);
        }
        resolveOperations(method, plan, diagnostics);
        return dryRunReport(plan, diagnostics);
    }

    public GpuIrAutoVectorizationResolvedRewriteOperations resolveOperations(
            GpuIrMethod method,
            GpuIrAutoVectorizationRewritePlan plan
    ) {
        return operationResolver.resolve(method, plan);
    }

    private int candidateIndexAtStatement(
            GpuIrAutoVectorizationResolvedRewriteOperations resolved,
            int statementIndex
    ) {
        for (int index = 0; index < resolved.insertions().size(); index++) {
            if (resolved.insertions().get(index).statementIndex() == statementIndex) {
                return index;
            }
        }
        return -1;
    }

    private List<GpuIrStatement> rewriteSingleLaneCandidate(GpuIrAutoVectorizationPrototypeRewriteShape shape) {
        String vectorName = "__jtg_vec_" + shape.statementIndex();
        List<GpuIrStatement> rewritten = new ArrayList<>();
        rewritten.add(new GpuIrVariableDeclaration(
                shape.vectorType(),
                vectorName,
                vectorInitializer(shape)
        ));
        rewritten.addAll(scalarLaneWrites(shape, vectorName));
        return rewritten;
    }

    private GpuIrStructInit vectorInitializer(GpuIrAutoVectorizationPrototypeRewriteShape shape) {
        List<GpuIrExpression> lanes = new ArrayList<>();
        for (int lane = shape.startInclusive(); lane < shape.endExclusive(); lane++) {
            lanes.add(laneExpressionRewriter.rewrite(shape.assignment().value(), shape.inductionVariable(), lane));
        }
        return new GpuIrStructInit(shape.vectorType(), lanes);
    }

    private List<GpuIrStatement> scalarLaneWrites(
            GpuIrAutoVectorizationPrototypeRewriteShape shape,
            String vectorName
    ) {
        GpuIrArrayAccess target = (GpuIrArrayAccess) shape.assignment().target();
        List<String> fields = vectorFields(shape.laneCount());
        List<GpuIrStatement> writes = new ArrayList<>();
        for (int laneOffset = 0; laneOffset < shape.laneCount(); laneOffset++) {
            int lane = shape.startInclusive() + laneOffset;
            writes.add(new GpuIrAssignment(
                    new GpuIrArrayAccess(target.arrayName(), new GpuIrLiteral(Integer.toString(lane))),
                    new GpuIrFieldAccess(new GpuIrVariableRef(vectorName), fields.get(laneOffset))
            ));
        }
        return writes;
    }

    private List<String> vectorFields(int laneCount) {
        // OpenCL names the first four vector lanes x/y/z/w; wider vectors use sN fields.
        return switch (laneCount) {
            case 2 -> List.of("x", "y");
            case 3 -> List.of("x", "y", "z");
            case 4 -> List.of("x", "y", "z", "w");
            case 8 -> List.of("s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7");
            case 16 -> List.of("s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "sa", "sb", "sc", "sd", "se", "sf");
            default -> throw new IllegalArgumentException("Unsupported prototype vector lane count: " + laneCount);
        };
    }

    private void resolveOperations(
            GpuIrMethod method,
            GpuIrAutoVectorizationRewritePlan plan,
            List<String> diagnostics
    ) {
        try {
            operationResolver.resolve(method, plan);
        } catch (IllegalArgumentException exception) {
            diagnostics.add(exception.getMessage());
        }
    }

    private void validateInsertionOperation(
            GpuIrAutoVectorizationRewritePlan plan,
            GpuIrAutoVectorizationRewriteCandidatePreview candidate,
            GpuIrAutoVectorizationRewriteInsertionOperation insertion,
            List<String> diagnostics
    ) {
        if (!candidate.loopLocation().equals(insertion.loopLocation())) {
            diagnostics.add(mismatch(plan, candidate, "insertion loopLocation", candidate.loopLocation(), insertion.loopLocation()));
        }
        if (!candidate.vectorType().equals(insertion.vectorType())) {
            diagnostics.add(mismatch(plan, candidate, "insertion vectorType", candidate.vectorType(), insertion.vectorType()));
        }
        if (candidate.startInclusive() != insertion.startInclusive()) {
            diagnostics.add(mismatch(plan, candidate, "insertion startInclusive", candidate.startInclusive(), insertion.startInclusive()));
        }
        if (candidate.endExclusive() != insertion.endExclusive()) {
            diagnostics.add(mismatch(plan, candidate, "insertion endExclusive", candidate.endExclusive(), insertion.endExclusive()));
        }
        if (!candidate.plannedVectorReads().equals(insertion.plannedVectorReads())) {
            diagnostics.add(mismatch(plan, candidate, "insertion plannedVectorReads", candidate.plannedVectorReads(), insertion.plannedVectorReads()));
        }
    }

    private void validateReplacementOperation(
            GpuIrAutoVectorizationRewritePlan plan,
            GpuIrAutoVectorizationRewriteCandidatePreview candidate,
            GpuIrAutoVectorizationRewriteReplacementOperation replacement,
            List<String> diagnostics
    ) {
        if (!candidate.loopLocation().equals(replacement.loopLocation())) {
            diagnostics.add(mismatch(plan, candidate, "replacement loopLocation", candidate.loopLocation(), replacement.loopLocation()));
        }
        if (!candidate.inductionVariable().equals(replacement.inductionVariable())) {
            diagnostics.add(mismatch(plan, candidate, "replacement inductionVariable", candidate.inductionVariable(), replacement.inductionVariable()));
        }
        if (candidate.startInclusive() != replacement.startInclusive()) {
            diagnostics.add(mismatch(plan, candidate, "replacement startInclusive", candidate.startInclusive(), replacement.startInclusive()));
        }
        if (candidate.endExclusive() != replacement.endExclusive()) {
            diagnostics.add(mismatch(plan, candidate, "replacement endExclusive", candidate.endExclusive(), replacement.endExclusive()));
        }
        if (!candidate.plannedVectorWrites().equals(replacement.plannedVectorWrites())) {
            diagnostics.add(mismatch(plan, candidate, "replacement plannedVectorWrites", candidate.plannedVectorWrites(), replacement.plannedVectorWrites()));
        }
    }

    private GpuIrAutoVectorizationRewriteDryRunReport dryRunReport(
            GpuIrAutoVectorizationRewritePlan plan,
            List<String> diagnostics
    ) {
        if (diagnostics.isEmpty()) {
            return GpuIrAutoVectorizationRewriteDryRunReport.ready(
                    plan.methodName(),
                    plan.candidateCount(),
                    plan.rawInsertionOperationCount(),
                    plan.rawReplacementOperationCount()
            );
        }
        return GpuIrAutoVectorizationRewriteDryRunReport.failed(
                plan.methodName(),
                plan.candidateCount(),
                plan.rawInsertionOperationCount(),
                plan.rawReplacementOperationCount(),
                diagnostics
        );
    }

    private String mismatch(
            GpuIrAutoVectorizationRewritePlan plan,
            GpuIrAutoVectorizationRewriteCandidatePreview candidate,
            String field,
            Object expected,
            Object actual
    ) {
        return "Auto-vectorization rewrite dry-run failed for "
                + plan.methodName()
                + " at " + candidate.loopLocation()
                + ": " + field
                + " expected=" + expected
                + " actual=" + actual;
    }
}
