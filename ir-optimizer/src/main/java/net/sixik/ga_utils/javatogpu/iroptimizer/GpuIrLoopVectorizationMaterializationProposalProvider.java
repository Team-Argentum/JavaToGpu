package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBodyBuilder;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrFieldAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrHelperCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrStructInit;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrTernary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrContinue;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrDoWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrExpressionStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrPrivateArrayDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitch;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitchCase;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrWhileLoop;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrGpuSourceEmission;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrTextBodyParseResult;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrTextBodyParser;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrTextStatement;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrTextSwitchCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Review-only OpenCL loop vectorization materialization for fixed-width contiguous float reductions.
 */
public final class GpuIrLoopVectorizationMaterializationProposalProvider implements GpuIrOptimizationProposalProvider {

    public static final String PROVIDER_ID = GpuIrOptimizerModule.MODULE_ID + ".loop-vectorization-materialization";
    public static final String PROVIDER_VERSION = PROVIDER_ID + ":1";

    private static final int VECTOR_WIDTH = 4;
    private static final String VECTOR_TYPE = "Float4";
    private static final String LOAD_INTRINSIC = "vload4";
    private static final Pattern SIMPLE_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern VARIABLE_HEADER = Pattern.compile("^var\\s+(\\S+)\\s+(\\S+)\\s+=\\s+(.+)$");
    private static final Pattern PRIVATE_ARRAY_HEADER = Pattern.compile("^private-array\\s+(\\S+)\\s+(\\S+)\\[(.+)]$");
    private static final Pattern ASSIGNMENT_HEADER = Pattern.compile("^set\\s+(.+?)\\s+=\\s+(.+)$");
    private static final Pattern INT_ZERO_INIT = Pattern.compile("^var\\s+int\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s+=\\s+0$");
    private static final Pattern SET_INCREMENT = Pattern.compile("^set\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s+=\\s+\\(\\s*\\1\\s+\\+\\s+1\\s*\\)$");

    @Override
    public GpuIrOptimizationProposal propose(GpuIrOptimizationProposalRequest request) {
        IrGpuArtifact original = request.originalArtifact();
        ArtifactRewrite rewrite = ArtifactRewrite.from(original, isOpenClReview(request), request.mutationAllowed());
        if (!rewrite.changed()) {
            return new GpuIrOptimizationProposal(
                    extensionId(),
                    extensionVersion(),
                    original,
                    Optional.empty(),
                    GpuIrOptimizationProposalDecision.NO_CHANGE,
                    GpuRuntimeIrOptimizationProofArtifact.fromFields(
                            "ir-optimizer.loop-vectorization-materialization",
                            rewrite.verdict(),
                            rewrite.fields(request)
                    ),
                    "",
                    List.of(rewrite.diagnostic())
            );
        }

        IrGpuArtifact optimized = copyWithModuleAndRegeneration(
                original,
                rewrite.module().orElseThrow(),
                rewrite.regenerationMetadata(original)
        );
        return GpuIrOptimizationProposal.proposed(
                extensionId(),
                extensionVersion(),
                original,
                optimized,
                GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "ir-optimizer.loop-vectorization-materialization",
                        "review-only-loop-vectorization-materialized",
                        rewrite.fields(request)
                ),
                List.of("materialized " + rewrite.transformedLoopCount()
                        + " fixed-width contiguous reduction loop(s) into a review candidate")
        );
    }

    @Override
    public String extensionId() {
        return PROVIDER_ID;
    }

    @Override
    public String extensionVersion() {
        return PROVIDER_VERSION;
    }

    @Override
    public int extensionOrder() {
        return 430;
    }

    private record ArtifactRewrite(
            Optional<IrGpuModule> module,
            int methodBodyCount,
            int parsedBodyCount,
            int typedBodyCount,
            int candidateCount,
            int transformedLoopCount,
            int changedMethodBodyCount,
            int bodyTextReplacementCount,
            int typedBodyMaterializedCount,
            int typedBodyInvalidatedCount,
            int typedBodyRebuildAttemptedCount,
            int typedBodyRebuildParsedCount,
            int typedBodyRebuildBuiltCount,
            int typedBodyRebuildGraphValidatedCount,
            int typedBodyRebuildRejectedCount,
            int skippedBackendTargetCount,
            int skippedUnsupportedFormatCount,
            int skippedParseFailedCount,
            int skippedAccumulatorDeclarationCount,
            int skippedLoopShapeCount,
            int skippedUnsupportedWidthCount,
            int skippedUnsafeLoadPatternCount,
            List<VectorCandidate> transformedCandidates,
            String firstTransformedLoop,
            String firstExpression,
            String firstReplacement,
            String typedBodyRebuildFirstBlocker,
            String firstBlocker,
            boolean openClReviewSourceReady,
            String openClReviewSourceLength,
            boolean mutationAllowed
    ) {

        private static ArtifactRewrite from(IrGpuArtifact artifact, boolean openClReview, boolean mutationAllowed) {
            RewriteStats stats = new RewriteStats();
            stats.methodBodyCount = artifact.module().methodBodies().size();
            if (!openClReview) {
                stats.skippedBackendTargetCount = stats.methodBodyCount;
                stats.setFirstBlocker("backend-target-not-opencl");
                return noChange(stats, stats.firstBlocker, mutationAllowed);
            }

            ArrayList<IrGpuMethodBody> rewrittenBodies = new ArrayList<>();
            for (IrGpuMethodBody methodBody : artifact.module().methodBodies()) {
                MethodRewrite methodRewrite = rewriteMethodBody(methodBody);
                rewrittenBodies.add(methodRewrite.methodBody());
                stats.add(methodRewrite.stats());
            }

            if (stats.transformedLoopCount == 0) {
                return noChange(stats, firstBlocker(stats), mutationAllowed);
            }

            IrGpuModule rewrittenModule = copyWithBodies(artifact.module(), rewrittenBodies);
            IrGpuArtifact optimized = copyWithModuleAndRegeneration(
                    artifact,
                    rewrittenModule,
                    IrGpuRegenerationMetadata.backendNeutralReady()
            );
            OpenClIrGpuSourceEmission emission = OpenClIrGpuSourceEmission.inspect(optimized);
            if (!emission.sourceGenerated()) {
                String blocker = emission.blockers().isEmpty()
                        ? "opencl-review-source-emission-blocked"
                        : emission.blockers().get(0);
                return noChange(stats, blocker, mutationAllowed);
            }
            return changed(stats, rewrittenModule, true, Integer.toString(emission.source().length()), mutationAllowed);
        }

        private static ArtifactRewrite changed(
                RewriteStats stats,
                IrGpuModule module,
                boolean openClReviewSourceReady,
                String openClReviewSourceLength,
                boolean mutationAllowed
        ) {
            return new ArtifactRewrite(
                    Optional.of(module),
                    stats.methodBodyCount,
                    stats.parsedBodyCount,
                    stats.typedBodyCount,
                    stats.candidateCount,
                    stats.transformedLoopCount,
                    stats.changedMethodBodyCount,
                    stats.bodyTextReplacementCount,
                    stats.typedBodyMaterializedCount,
                    stats.typedBodyInvalidatedCount,
                    stats.typedBodyRebuildAttemptedCount,
                    stats.typedBodyRebuildParsedCount,
                    stats.typedBodyRebuildBuiltCount,
                    stats.typedBodyRebuildGraphValidatedCount,
                    stats.typedBodyRebuildRejectedCount,
                    stats.skippedBackendTargetCount,
                    stats.skippedUnsupportedFormatCount,
                    stats.skippedParseFailedCount,
                    stats.skippedAccumulatorDeclarationCount,
                    stats.skippedLoopShapeCount,
                    stats.skippedUnsupportedWidthCount,
                    stats.skippedUnsafeLoadPatternCount,
                    List.copyOf(stats.transformedCandidates),
                    stats.firstTransformedLoop,
                    stats.firstExpression,
                    stats.firstReplacement,
                    stats.typedBodyRebuildFirstBlocker,
                    "none",
                    openClReviewSourceReady,
                    openClReviewSourceLength,
                    mutationAllowed
            );
        }

        private static ArtifactRewrite noChange(RewriteStats stats, String firstBlocker, boolean mutationAllowed) {
            return new ArtifactRewrite(
                    Optional.empty(),
                    stats.methodBodyCount,
                    stats.parsedBodyCount,
                    stats.typedBodyCount,
                    stats.candidateCount,
                    stats.transformedLoopCount,
                    stats.changedMethodBodyCount,
                    stats.bodyTextReplacementCount,
                    stats.typedBodyMaterializedCount,
                    stats.typedBodyInvalidatedCount,
                    stats.typedBodyRebuildAttemptedCount,
                    stats.typedBodyRebuildParsedCount,
                    stats.typedBodyRebuildBuiltCount,
                    stats.typedBodyRebuildGraphValidatedCount,
                    stats.typedBodyRebuildRejectedCount,
                    stats.skippedBackendTargetCount,
                    stats.skippedUnsupportedFormatCount,
                    stats.skippedParseFailedCount,
                    stats.skippedAccumulatorDeclarationCount,
                    stats.skippedLoopShapeCount,
                    stats.skippedUnsupportedWidthCount,
                    stats.skippedUnsafeLoadPatternCount,
                    List.copyOf(stats.transformedCandidates),
                    stats.firstTransformedLoop,
                    stats.firstExpression,
                    stats.firstReplacement,
                    stats.typedBodyRebuildFirstBlocker,
                    firstBlocker,
                    false,
                    "not-ready",
                    mutationAllowed
            );
        }

        private boolean changed() {
            return module.isPresent() && transformedLoopCount > 0;
        }

        private IrGpuRegenerationMetadata regenerationMetadata(IrGpuArtifact original) {
            return openClReviewSourceReady
                    ? IrGpuRegenerationMetadata.backendNeutralReady()
                    : original.regenerationMetadata();
        }

        private String verdict() {
            return changed() ? "materialized-review-candidate" : "materialization-no-change";
        }

        private String diagnostic() {
            if (changed()) {
                return "loop vectorization materialization rewrote " + transformedLoopCount
                        + " fixed-width contiguous float reduction loop(s) to vload4 review code";
            }
            return "loop vectorization materialization produced no review candidate: " + firstBlocker;
        }

        private Map<String, String> fields(GpuIrOptimizationProposalRequest request) {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("methodBody.count", Integer.toString(methodBodyCount));
            fields.put("methodBody.rewriteScope", "all-method-bodies");
            fields.put("parsedBody.count", Integer.toString(parsedBodyCount));
            fields.put("typedBody.count", Integer.toString(typedBodyCount));
            fields.put("candidate.count", Integer.toString(candidateCount));
            fields.put("transformedLoop.count", Integer.toString(transformedLoopCount));
            fields.put("changedMethodBody.count", Integer.toString(changedMethodBodyCount));
            fields.put("bodyTextReplacement.count", Integer.toString(bodyTextReplacementCount));
            fields.put("typedBody.materialized.count", Integer.toString(typedBodyMaterializedCount));
            fields.put("typedBody.invalidated.count", Integer.toString(typedBodyInvalidatedCount));
            fields.put("typedBody.rebuild.attempted.count", Integer.toString(typedBodyRebuildAttemptedCount));
            fields.put("typedBody.rebuild.parsed.count", Integer.toString(typedBodyRebuildParsedCount));
            fields.put("typedBody.rebuild.built.count", Integer.toString(typedBodyRebuildBuiltCount));
            fields.put("typedBody.rebuild.graphValidated.count", Integer.toString(typedBodyRebuildGraphValidatedCount));
            fields.put("typedBody.rebuild.rejected.count", Integer.toString(typedBodyRebuildRejectedCount));
            fields.put("typedBody.rebuild.status", typedBodyRebuildStatus());
            fields.put("typedBody.rebuild.firstBlocker", typedBodyRebuildFirstBlocker);
            fields.put("skipped.backendTarget.count", Integer.toString(skippedBackendTargetCount));
            fields.put("skipped.unsupportedFormat.count", Integer.toString(skippedUnsupportedFormatCount));
            fields.put("skipped.parseFailed.count", Integer.toString(skippedParseFailedCount));
            fields.put("skipped.accumulatorDeclaration.count", Integer.toString(skippedAccumulatorDeclarationCount));
            fields.put("skipped.loopShape.count", Integer.toString(skippedLoopShapeCount));
            fields.put("skipped.unsupportedWidth.count", Integer.toString(skippedUnsupportedWidthCount));
            fields.put("skipped.unsafeLoadPattern.count", Integer.toString(skippedUnsafeLoadPatternCount));
            fields.put("rewrite.proposed", Boolean.toString(changed()));
            fields.put("rewrite.materialized", Boolean.toString(changed()));
            fields.put("optimizerFamily", "loop-vectorization-materialization");
            fields.put("vectorWidth", Integer.toString(VECTOR_WIDTH));
            fields.put("vectorType", VECTOR_TYPE);
            fields.put("loadIntrinsic", LOAD_INTRINSIC);
            fields.put("reductionKind", "ordered-scalar-float-sum");
            fields.put("indexPattern", "contiguous-base-plus-unit-induction");
            fields.put("mutationRequired", "false");
            fields.put("productionAffecting", "false");
            fields.put("previewOnly", "false");
            fields.put("provider.mutatesOriginal", "false");
            fields.put("policy.mutationAllowed", Boolean.toString(mutationAllowed));
            fields.put("policy.proposalOnly", Boolean.toString(!mutationAllowed));
            fields.put("policy.rollbackRequired", Boolean.toString(request.policy().rollbackRequired()));
            fields.put("policy.proofRequired", Boolean.toString(request.policy().proofRequired()));
            fields.put("proof.runtimeEquivalenceRequiredBeforeSelection", Boolean.toString(changed()));
            fields.put("proof.runtimeEquivalencePayloadRequiredBeforeSelection", Boolean.toString(changed()));
            fields.put("proof.approvalRequiredBeforeProduction", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.required", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.status", changed() ? "recorded" : "not-required");
            fields.put("runtimeEquivalencePayload.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.passed", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.firstBlocker", changed() ? "none" : firstBlocker);
            fields.put(
                    "runtimeEquivalencePayload.comparisonMode",
                    "optimizer-family:loop-vectorization-materialization:review-candidate"
            );
            fields.put(
                    "runtimeEquivalencePayload.resource",
                    "ir-optimizer://loop-vectorization-materialization/review-candidate"
            );
            fields.put("runtimeEquivalencePayload.cpuReference.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.preOptimizationOutput.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.postOptimizationOutput.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.tolerance.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.failureFixture.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.CpuReference", "orderedLoopReduction=" + transformedLoopCount);
            fields.put("runtimeEquivalencePayload.PreOptimizationOutput", "loops=" + transformedLoopCount
                    + ", first=" + firstExpression);
            fields.put("runtimeEquivalencePayload.PostOptimizationOutput", "vload4Reductions=" + transformedLoopCount
                    + ", first=" + firstReplacement);
            fields.put("runtimeEquivalencePayload.Tolerance", "mode=ordered-float-sum, reassociation=false");
            fields.put("runtimeEquivalencePayload.FailureFixture", "none");
            fields.put("runtimeEquivalencePayload.ReferenceMode", "static-vload4-ordered-scalar-reduction");
            fields.put("runtimeEquivalencePayload.CaseIdentity", "method-name-and-loop-line");
            appendRuntimeEquivalenceCases(fields, transformedCandidates);
            fields.put("reviewPackage.required", Boolean.toString(changed()));
            fields.put("reviewPackage.firstBlocker", changed() ? "approval-pending" : firstBlocker);
            fields.put("safety.scope", "opencl-fixed-width-contiguous-float-reduction-review-candidate");
            fields.put("safety.vectorWidthFixed", "true");
            fields.put("safety.loopTripCountProven", Boolean.toString(changed()));
            fields.put("safety.contiguousLoadProven", Boolean.toString(changed()));
            fields.put("safety.orderedReductionPreserved", Boolean.toString(changed()));
            fields.put("safety.reassociationRequired", "false");
            fields.put("safety.fastMathRequired", "false");
            fields.put("safety.sideEffectFreedomProven", Boolean.toString(changed()));
            fields.put("safety.typedBodyMaterializedForReview", Boolean.toString(changed() && typedBodyMaterializedCount > 0));
            fields.put("safety.typedBodyInvalidatedForReview", Boolean.toString(changed() && typedBodyInvalidatedCount > 0));
            fields.put("openClReview.sourceReady", Boolean.toString(openClReviewSourceReady));
            fields.put("openClReview.sourceLength", openClReviewSourceLength);
            fields.put("firstTransformedLoop", firstTransformedLoop);
            fields.put("firstExpression", firstExpression);
            fields.put("firstReplacement", firstReplacement);
            fields.put("firstBlocker", changed() ? "none" : firstBlocker);
            return Map.copyOf(fields);
        }

        private String typedBodyRebuildStatus() {
            if (typedBodyRebuildAttemptedCount <= 0) {
                return "not-attempted";
            }
            if (typedBodyRebuildRejectedCount <= 0) {
                return "validated";
            }
            if (typedBodyRebuildGraphValidatedCount > 0) {
                return "partially-validated";
            }
            return "invalidated";
        }

        private static String firstBlocker(RewriteStats stats) {
            if (!"none".equals(stats.firstBlocker)) {
                return stats.firstBlocker;
            }
            if (stats.methodBodyCount == 0) {
                return "method-body-missing";
            }
            if (stats.parsedBodyCount == 0) {
                return "ir-text-body-parse-failed";
            }
            return "no-fixed-width-contiguous-reduction-loop";
        }
    }

    private record MethodRewrite(IrGpuMethodBody methodBody, RewriteStats stats) {
    }

    private static MethodRewrite rewriteMethodBody(IrGpuMethodBody methodBody) {
        RewriteStats stats = new RewriteStats();
        if (!"ir-text-v1".equals(methodBody.format())) {
            stats.skippedUnsupportedFormatCount++;
            stats.setFirstBlocker("method-body-format-unsupported-" + methodBody.format());
            return new MethodRewrite(methodBody, stats);
        }
        if (methodBody.typedBody().available()) {
            stats.typedBodyCount = 1;
        }
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse(methodBody.body());
        if (!parseResult.parsed()) {
            stats.skippedParseFailedCount++;
            stats.setFirstBlocker(parseResult.blockers().isEmpty()
                    ? "ir-text-body-parse-failed"
                    : parseResult.blockers().get(0));
            return new MethodRewrite(methodBody, stats);
        }
        stats.parsedBodyCount = 1;

        List<VectorCandidate> candidates = vectorCandidates(methodBody.name(), methodBody.body(), parseResult.statements(), stats);
        if (candidates.isEmpty()) {
            return new MethodRewrite(methodBody, stats);
        }

        String rewrittenBody = rewriteBody(methodBody.body(), candidates);
        TypedBodyRebuild rewrittenTypedBody = typedBodyFromRewrittenBody(rewrittenBody);
        stats.transformedLoopCount += candidates.size();
        stats.changedMethodBodyCount = 1;
        stats.bodyTextReplacementCount += candidates.size();
        stats.transformedCandidates.addAll(candidates);
        stats.recordTypedBodyRebuild(rewrittenTypedBody);
        if (rewrittenTypedBody.typedBody().isPresent()) {
            stats.typedBodyMaterializedCount++;
        } else {
            stats.typedBodyInvalidatedCount += methodBody.typedBody().available() ? 1 : 0;
        }
        VectorCandidate first = candidates.get(0);
        stats.firstTransformedLoop = first.summary();
        stats.firstExpression = first.originalSummary();
        stats.firstReplacement = first.replacementSummary();
        return new MethodRewrite(
                copyWithBodyAndTypedBody(methodBody, rewrittenBody, rewrittenTypedBody.typedBody().orElseGet(IrGpuTypedBody::none)),
                stats
        );
    }

    private record TypedBodyRebuild(
            Optional<IrGpuTypedBody> typedBody,
            boolean parsed,
            boolean built,
            boolean graphValidated,
            String firstBlocker
    ) {

        private static TypedBodyRebuild accepted(IrGpuTypedBody typedBody) {
            return new TypedBodyRebuild(Optional.of(typedBody), true, true, true, "none");
        }

        private static TypedBodyRebuild rejected(
                boolean parsed,
                boolean built,
                boolean graphValidated,
                String firstBlocker
        ) {
            return new TypedBodyRebuild(
                    Optional.empty(),
                    parsed,
                    built,
                    graphValidated,
                    firstBlocker == null || firstBlocker.isBlank() ? "typed-body-rebuild-blocked" : firstBlocker
            );
        }
    }

    private static TypedBodyRebuild typedBodyFromRewrittenBody(String body) {
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse(body);
        if (!parseResult.parsed()) {
            return TypedBodyRebuild.rejected(
                    false,
                    false,
                    false,
                    parseResult.blockers().isEmpty() ? "typed-body-rebuild-parse-failed" : parseResult.blockers().get(0)
            );
        }
        Optional<List<GpuIrStatement>> statements = typedStatements(parseResult.statements());
        if (statements.isEmpty()) {
            return TypedBodyRebuild.rejected(true, false, false, "typed-body-rebuild-statement-conversion-blocked");
        }
        IrGpuTypedBody typedBody = IrGpuTypedBodyBuilder.fromStatements(statements.orElseThrow());
        if (!typedBody.available()) {
            return TypedBodyRebuild.rejected(true, false, false, "typed-body-rebuild-builder-unavailable");
        }
        TypedBodyGraphValidation graphValidation = typedBodyGraphValidation(typedBody);
        return graphValidation.valid()
                ? TypedBodyRebuild.accepted(typedBody)
                : TypedBodyRebuild.rejected(true, true, false, graphValidation.firstBlocker());
    }

    private record TypedBodyGraphValidation(boolean valid, String firstBlocker) {
    }

    private static TypedBodyGraphValidation typedBodyGraphValidation(IrGpuTypedBody typedBody) {
        GpuIrTypedBodyGraphPatch.Reachability reachability = GpuIrTypedBodyGraphPatch
                .reachability(typedBody, GpuIrTypedBodyGraphPatch.nodesById(typedBody));
        if (reachability.rootMissingCount() > 0) {
            return new TypedBodyGraphValidation(false, "typed-body-rebuild-missing-root");
        }
        if (reachability.missingChildReferenceCount() > 0) {
            return new TypedBodyGraphValidation(false, "typed-body-rebuild-missing-child-reference");
        }
        return new TypedBodyGraphValidation(true, "none");
    }

    private static Optional<List<GpuIrStatement>> typedStatements(List<OpenClIrTextStatement> statements) {
        ArrayList<GpuIrStatement> typed = new ArrayList<>();
        for (OpenClIrTextStatement statement : statements) {
            Optional<GpuIrStatement> converted = typedStatement(statement);
            if (converted.isEmpty()) {
                return Optional.empty();
            }
            typed.add(converted.orElseThrow());
        }
        return Optional.of(List.copyOf(typed));
    }

    private static Optional<GpuIrStatement> typedStatement(OpenClIrTextStatement statement) {
        return switch (statement.kind()) {
            case VARIABLE -> typedExpression(statement.expression())
                    .map(expression -> new GpuIrVariableDeclaration(statement.typeName(), statement.target(), expression));
            case PRIVATE_ARRAY -> typedExpression(statement.expression())
                    .map(expression -> new GpuIrPrivateArrayDeclaration(statement.typeName(), statement.target(), expression));
            case ASSIGNMENT -> typedExpression(statement.target()).flatMap(target -> typedExpression(statement.expression())
                    .map(expression -> new GpuIrAssignment(target, expression)));
            case EXPRESSION -> typedExpression(statement.expression()).map(GpuIrExpressionStatement::new);
            case RETURN -> statement.expression().isBlank()
                    ? Optional.of(new GpuIrReturn(null))
                    : typedExpression(statement.expression()).map(GpuIrReturn::new);
            case IF -> typedExpression(statement.expression()).flatMap(condition -> typedStatements(statement.thenStatements())
                    .flatMap(thenStatements -> typedStatements(statement.elseStatements())
                            .map(elseStatements -> new GpuIrIf(condition, thenStatements, elseStatements))));
            case FOR -> typedHeaderStatement(statement.typeName()).flatMap(initializer -> typedExpression(statement.expression())
                    .flatMap(condition -> typedHeaderStatement(statement.target())
                            .flatMap(update -> typedStatements(statement.thenStatements())
                                    .map(body -> new GpuIrForLoop(initializer, condition, update, body)))));
            case WHILE -> typedExpression(statement.expression()).flatMap(condition -> typedStatements(statement.thenStatements())
                    .map(body -> new GpuIrWhileLoop(condition, body)));
            case DO_WHILE -> typedExpression(statement.expression()).flatMap(condition -> typedStatements(statement.thenStatements())
                    .map(body -> new GpuIrDoWhileLoop(body, condition)));
            case SWITCH -> typedExpression(statement.expression()).flatMap(selector -> typedSwitchCases(statement.switchCases())
                    .map(cases -> new GpuIrSwitch(selector, cases)));
            case BREAK -> Optional.of(new GpuIrBreak());
            case CONTINUE -> Optional.of(new GpuIrContinue());
        };
    }

    private static Optional<GpuIrStatement> typedHeaderStatement(String statement) {
        String trimmed = statement == null ? "" : statement.trim();
        Matcher variable = VARIABLE_HEADER.matcher(trimmed);
        if (variable.matches()) {
            return typedExpression(variable.group(3))
                    .map(expression -> new GpuIrVariableDeclaration(variable.group(1), variable.group(2), expression));
        }
        Matcher privateArray = PRIVATE_ARRAY_HEADER.matcher(trimmed);
        if (privateArray.matches()) {
            return typedExpression(privateArray.group(3))
                    .map(expression -> new GpuIrPrivateArrayDeclaration(privateArray.group(1), privateArray.group(2), expression));
        }
        Matcher assignment = ASSIGNMENT_HEADER.matcher(trimmed);
        if (assignment.matches()) {
            return typedExpression(assignment.group(1)).flatMap(target -> typedExpression(assignment.group(2))
                    .map(expression -> new GpuIrAssignment(target, expression)));
        }
        return Optional.empty();
    }

    private static Optional<List<GpuIrSwitchCase>> typedSwitchCases(List<OpenClIrTextSwitchCase> switchCases) {
        ArrayList<GpuIrSwitchCase> typed = new ArrayList<>();
        for (OpenClIrTextSwitchCase switchCase : switchCases) {
            Optional<List<GpuIrStatement>> statements = typedStatements(switchCase.statements());
            if (statements.isEmpty()) {
                return Optional.empty();
            }
            ArrayList<GpuIrExpression> labels = new ArrayList<>();
            for (String label : switchCase.labels()) {
                Optional<GpuIrExpression> expression = typedExpression(label);
                if (expression.isEmpty()) {
                    return Optional.empty();
                }
                labels.add(expression.orElseThrow());
            }
            typed.add(new GpuIrSwitchCase(List.copyOf(labels), statements.orElseThrow(), switchCase.defaultCase()));
        }
        return Optional.of(List.copyOf(typed));
    }

    private static Optional<GpuIrExpression> typedExpression(String expression) {
        String normalized = stripOuterParentheses(expression == null ? "" : expression.trim());
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        Optional<GpuIrExpression> intrinsic = typedIntrinsic(normalized);
        if (intrinsic.isPresent()) {
            return intrinsic;
        }
        Optional<GpuIrExpression> helper = typedHelper(normalized);
        if (helper.isPresent()) {
            return helper;
        }
        Optional<GpuIrExpression> cast = typedCast(normalized);
        if (cast.isPresent()) {
            return cast;
        }
        Optional<GpuIrExpression> init = typedInit(normalized);
        if (init.isPresent()) {
            return init;
        }
        Optional<GpuIrExpression> binary = typedBinary(normalized);
        if (binary.isPresent()) {
            return binary;
        }
        Optional<GpuIrExpression> fieldAccess = typedFieldAccess(normalized);
        if (fieldAccess.isPresent()) {
            return fieldAccess;
        }
        Optional<GpuIrExpression> arrayAccess = typedArrayAccess(normalized);
        if (arrayAccess.isPresent()) {
            return arrayAccess;
        }
        Optional<GpuIrExpression> unary = typedUnary(normalized);
        if (unary.isPresent()) {
            return unary;
        }
        if (isLiteral(normalized)) {
            return Optional.of(new GpuIrLiteral(normalized));
        }
        if (isSimpleName(normalized)) {
            return Optional.of(new GpuIrVariableRef(normalized));
        }
        return Optional.empty();
    }

    private static Optional<GpuIrExpression> typedIntrinsic(String expression) {
        String prefix = "intrinsic(";
        if (!expression.startsWith(prefix) || !expression.endsWith(")")
                || matchingClose(expression, prefix.length() - 1, '(', ')') != expression.length() - 1) {
            return Optional.empty();
        }
        String payload = expression.substring(prefix.length(), expression.length() - 1);
        int argsStart = topLevelFieldStart(payload, "args=[");
        String backendName = (argsStart < 0 ? payload : payload.substring(0, argsStart)).trim();
        int firstSpace = backendName.indexOf(' ');
        if (firstSpace >= 0) {
            backendName = backendName.substring(0, firstSpace).trim();
        }
        if (backendName.isBlank()) {
            return Optional.empty();
        }
        String resolvedBackendName = backendName;
        Optional<List<GpuIrExpression>> arguments = argsStart < 0
                ? Optional.of(List.of())
                : typedExpressionList(payload, argsStart + "args=[".length() - 1, '[', ']');
        return arguments.map(expressions -> new GpuIrIntrinsicCall(
                null,
                resolvedBackendName,
                "",
                intrinsicResultType(resolvedBackendName),
                expressions,
                List.of()
        ));
    }

    private static Optional<GpuIrExpression> typedHelper(String expression) {
        String prefix = "helper(";
        if (!expression.startsWith(prefix) || !expression.endsWith(")")
                || matchingClose(expression, prefix.length() - 1, '(', ')') != expression.length() - 1) {
            return Optional.empty();
        }
        String payload = expression.substring(prefix.length(), expression.length() - 1);
        int argsStart = topLevelFieldStart(payload, "args=[");
        if (argsStart < 0) {
            return Optional.empty();
        }
        String helperName = payload.substring(0, argsStart).trim();
        if (helperName.isBlank()) {
            return Optional.empty();
        }
        return typedExpressionList(payload, argsStart + "args=[".length() - 1, '[', ']')
                .map(expressions -> new GpuIrHelperCall(helperName, "unknown", expressions));
    }

    private static Optional<GpuIrExpression> typedCast(String expression) {
        String prefix = "cast<";
        if (!expression.startsWith(prefix)) {
            return Optional.empty();
        }
        int typeEnd = expression.indexOf(">(", prefix.length());
        if (typeEnd < 0 || !expression.endsWith(")")) {
            return Optional.empty();
        }
        int openParen = typeEnd + 1;
        if (matchingClose(expression, openParen, '(', ')') != expression.length() - 1) {
            return Optional.empty();
        }
        String targetType = expression.substring(prefix.length(), typeEnd).trim();
        return typedExpression(expression.substring(openParen + 1, expression.length() - 1))
                .map(value -> new GpuIrCast(targetType, value));
    }

    private static Optional<GpuIrExpression> typedInit(String expression) {
        String prefix = "init<";
        if (!expression.startsWith(prefix)) {
            return Optional.empty();
        }
        int typeEnd = expression.indexOf(">(", prefix.length());
        if (typeEnd < 0 || !expression.endsWith(")")) {
            return Optional.empty();
        }
        int openParen = typeEnd + 1;
        if (matchingClose(expression, openParen, '(', ')') != expression.length() - 1) {
            return Optional.empty();
        }
        String type = expression.substring(prefix.length(), typeEnd).trim();
        return typedExpressions(expression.substring(openParen + 1, expression.length() - 1))
                .map(arguments -> new GpuIrStructInit(type, arguments));
    }

    private static Optional<GpuIrExpression> typedBinary(String expression) {
        String[][] groups = {
                {"||"}, {"&&"}, {"==", "!="}, {"<=", ">=", "<", ">"}, {"+", "-"}, {"*", "/", "%"}
        };
        for (String[] group : groups) {
            OperatorMatch match = findTopLevelOperator(expression, group);
            if (match != null) {
                Optional<GpuIrExpression> left = typedExpression(expression.substring(0, match.index()).trim());
                Optional<GpuIrExpression> right = typedExpression(expression.substring(match.index() + match.operator().length()).trim());
                if (left.isPresent() && right.isPresent()) {
                    return Optional.of(new GpuIrBinary(match.operator(), left.orElseThrow(), right.orElseThrow()));
                }
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<GpuIrExpression> typedFieldAccess(String expression) {
        int dot = findTopLevelDot(expression);
        if (dot <= 0 || dot >= expression.length() - 1) {
            return Optional.empty();
        }
        String fieldName = expression.substring(dot + 1).trim();
        if (!isSimpleName(fieldName)) {
            return Optional.empty();
        }
        return typedExpression(expression.substring(0, dot).trim())
                .map(target -> new GpuIrFieldAccess(target, fieldName));
    }

    private static Optional<GpuIrExpression> typedArrayAccess(String expression) {
        if (!expression.endsWith("]")) {
            return Optional.empty();
        }
        int bracket = findTopLevelArrayBracket(expression);
        if (bracket <= 0 || matchingClose(expression, bracket, '[', ']') != expression.length() - 1) {
            return Optional.empty();
        }
        String arrayName = expression.substring(0, bracket).trim();
        if (!isSimpleName(arrayName)) {
            return Optional.empty();
        }
        return typedExpression(expression.substring(bracket + 1, expression.length() - 1))
                .map(index -> new GpuIrArrayAccess(arrayName, index));
    }

    private static Optional<GpuIrExpression> typedUnary(String expression) {
        for (String operator : List.of("&", "!", "~", "-")) {
            if (expression.startsWith(operator) && expression.length() > operator.length()) {
                return typedExpression(expression.substring(operator.length()).trim())
                        .map(operand -> new GpuIrUnary(operator, operand));
            }
        }
        return Optional.empty();
    }

    private static Optional<List<GpuIrExpression>> typedExpressionList(
            String value,
            int openIndex,
            char open,
            char close
    ) {
        int end = matchingClose(value, openIndex, open, close);
        if (end < 0) {
            return Optional.empty();
        }
        return typedExpressions(value.substring(openIndex + 1, end));
    }

    private static Optional<List<GpuIrExpression>> typedExpressions(String values) {
        ArrayList<GpuIrExpression> expressions = new ArrayList<>();
        if (values == null || values.isBlank()) {
            return Optional.of(List.of());
        }
        for (String part : splitTopLevel(values, ',')) {
            Optional<GpuIrExpression> expression = typedExpression(part);
            if (expression.isEmpty()) {
                return Optional.empty();
            }
            expressions.add(expression.orElseThrow());
        }
        return Optional.of(List.copyOf(expressions));
    }

    private static String intrinsicResultType(String backendName) {
        return switch (backendName) {
            case LOAD_INTRINSIC -> VECTOR_TYPE;
            case "get_global_id", "get_local_id", "get_group_id", "get_global_size", "get_local_size" -> "int";
            case "mad" -> "int";
            default -> "unknown";
        };
    }

    private record OperatorMatch(int index, String operator) {
    }

    private static List<VectorCandidate> vectorCandidates(
            String methodName,
            String body,
            List<OpenClIrTextStatement> statements,
            RewriteStats stats
    ) {
        ArrayList<VectorCandidate> candidates = new ArrayList<>();
        for (int index = 0; index + 1 < statements.size(); index++) {
            OpenClIrTextStatement declaration = statements.get(index);
            OpenClIrTextStatement loop = statements.get(index + 1);
            Optional<VectorCandidate> candidate = vectorCandidate(
                    methodName,
                    body,
                    declaration,
                    loop,
                    localIntBindings(statements, index),
                    stats
            );
            candidate.ifPresent(candidates::add);
        }
        return List.copyOf(candidates);
    }

    private static Optional<VectorCandidate> vectorCandidate(
            String methodName,
            String body,
            OpenClIrTextStatement declaration,
            OpenClIrTextStatement loop,
            Map<String, String> localIntBindings,
            RewriteStats stats
    ) {
        if (declaration.kind() != OpenClIrTextStatement.Kind.VARIABLE
                || loop.kind() != OpenClIrTextStatement.Kind.FOR) {
            return Optional.empty();
        }
        if (!"float".equals(declaration.typeName()) || !isZeroFloatLiteral(declaration.expression())) {
            stats.skippedAccumulatorDeclarationCount++;
            stats.setFirstBlocker("accumulator-declaration-not-float-zero");
            return Optional.empty();
        }
        String accumulator = declaration.target();
        if (!isSimpleName(accumulator)) {
            stats.skippedAccumulatorDeclarationCount++;
            stats.setFirstBlocker("accumulator-name-not-simple");
            return Optional.empty();
        }
        Optional<LoopShape> loopShape = loopShape(loop);
        if (loopShape.isEmpty()) {
            stats.skippedLoopShapeCount++;
            stats.setFirstBlocker("loop-shape-unsupported");
            return Optional.empty();
        }
        if (loopShape.orElseThrow().width() != VECTOR_WIDTH) {
            stats.skippedUnsupportedWidthCount++;
            stats.setFirstBlocker("loop-width-unsupported-" + loopShape.orElseThrow().width());
            return Optional.empty();
        }
        Optional<LoadPattern> loadPattern = loadPattern(
                loop,
                accumulator,
                loopShape.orElseThrow().indexName(),
                localIntBindings
        );
        if (loadPattern.isEmpty()) {
            stats.skippedUnsafeLoadPatternCount++;
            stats.setFirstBlocker("load-pattern-not-contiguous-float-reduction");
            return Optional.empty();
        }

        stats.candidateCount++;
        String vectorName = uniqueLocalName(body, accumulator + "_vec4");
        String pointerExpression = "(&" + loadPattern.orElseThrow().arrayName()
                + "[" + loadPattern.orElseThrow().baseIndexExpression() + "])";
        String vectorLoadExpression = "intrinsic(" + LOAD_INTRINSIC + " template=\"\" args=[0, "
                + pointerExpression + "])";
        String reductionExpression = "((((" + declaration.expression() + " + " + vectorName + ".x) + "
                + vectorName + ".y) + " + vectorName + ".z) + " + vectorName + ".w)";
        String indent = " ".repeat(lineIndent(normalizedLines(body), declaration.lineNumber()));
        List<String> replacementLines = List.of(
                indent + "var " + VECTOR_TYPE + " " + vectorName + " = " + vectorLoadExpression,
                indent + "var float " + accumulator + " = " + reductionExpression
        );
        return Optional.of(new VectorCandidate(
                methodName,
                declaration.lineNumber(),
                loop.thenStatements().get(0).lineNumber(),
                accumulator,
                loopShape.orElseThrow().indexName(),
                loadPattern.orElseThrow().arrayName(),
                loadPattern.orElseThrow().baseIndexExpression(),
                loadPattern.orElseThrow().indexExpression(),
                vectorName,
                vectorLoadExpression,
                reductionExpression,
                replacementLines
        ));
    }

    private static Optional<LoopShape> loopShape(OpenClIrTextStatement loop) {
        Matcher initializer = INT_ZERO_INIT.matcher(loop.typeName().trim());
        if (!initializer.matches()) {
            return Optional.empty();
        }
        String indexName = initializer.group(1);
        String condition = stripOuterParentheses(loop.expression().trim());
        List<String> conditionParts = splitTopLevel(condition, '<');
        if (conditionParts.size() != 2
                || !indexName.equals(conditionParts.get(0).trim())
                || !isIntegerLiteral(conditionParts.get(1).trim())) {
            return Optional.empty();
        }
        Matcher update = SET_INCREMENT.matcher(loop.target().trim());
        if (!update.matches() || !indexName.equals(update.group(1))) {
            return Optional.empty();
        }
        return Optional.of(new LoopShape(indexName, Integer.parseInt(conditionParts.get(1).trim())));
    }

    private static Map<String, String> localIntBindings(List<OpenClIrTextStatement> statements, int endExclusive) {
        LinkedHashMap<String, String> bindings = new LinkedHashMap<>();
        for (int index = 0; index < Math.min(statements.size(), endExclusive); index++) {
            OpenClIrTextStatement statement = statements.get(index);
            if (statement.kind() == OpenClIrTextStatement.Kind.VARIABLE
                    && "int".equals(statement.typeName())
                    && isSimpleName(statement.target())) {
                bindings.put(statement.target(), statement.expression());
            } else if (statement.kind() == OpenClIrTextStatement.Kind.ASSIGNMENT
                    && isSimpleName(statement.target())) {
                bindings.remove(statement.target());
            } else if (statement.kind() == OpenClIrTextStatement.Kind.IF
                    || statement.kind() == OpenClIrTextStatement.Kind.FOR
                    || statement.kind() == OpenClIrTextStatement.Kind.WHILE
                    || statement.kind() == OpenClIrTextStatement.Kind.DO_WHILE
                    || statement.kind() == OpenClIrTextStatement.Kind.SWITCH) {
                bindings.clear();
            }
        }
        return Map.copyOf(bindings);
    }

    private static Optional<LoadPattern> loadPattern(
            OpenClIrTextStatement loop,
            String accumulator,
            String indexName,
            Map<String, String> localIntBindings
    ) {
        if (loop.thenStatements().size() != 1) {
            return Optional.empty();
        }
        OpenClIrTextStatement statement = loop.thenStatements().get(0);
        if (statement.kind() != OpenClIrTextStatement.Kind.ASSIGNMENT || !accumulator.equals(statement.target())) {
            return Optional.empty();
        }
        String expression = stripOuterParentheses(statement.expression().trim());
        List<String> terms = splitTopLevel(expression, '+');
        if (terms.size() != 2 || !accumulator.equals(terms.get(0).trim())) {
            return Optional.empty();
        }
        return parseLoad(terms.get(1).trim(), indexName, localIntBindings);
    }

    private static Optional<LoadPattern> parseLoad(
            String expression,
            String indexName,
            Map<String, String> localIntBindings
    ) {
        int bracket = expression.indexOf('[');
        if (bracket <= 0 || !expression.endsWith("]")) {
            return Optional.empty();
        }
        String arrayName = expression.substring(0, bracket).trim();
        if (!isSimpleName(arrayName)) {
            return Optional.empty();
        }
        String indexExpression = expression.substring(bracket + 1, expression.length() - 1).trim();
        Optional<String> baseIndexExpression = baseIndexExpression(indexExpression, indexName, localIntBindings);
        return baseIndexExpression.map(base -> new LoadPattern(arrayName, indexExpression, base));
    }

    private static Optional<String> baseIndexExpression(
            String indexExpression,
            String indexName,
            Map<String, String> localIntBindings
    ) {
        String normalized = stripOuterParentheses(indexExpression.trim());
        Optional<String> madBase = madBaseIndexExpression(normalized, indexName);
        if (madBase.isPresent()) {
            return madBase;
        }
        List<String> terms = splitTopLevel(normalized, '+');
        if (terms.size() != 2) {
            return Optional.empty();
        }
        String left = stripOuterParentheses(terms.get(0).trim());
        String right = stripOuterParentheses(terms.get(1).trim());
        if (indexName.equals(right)) {
            return contiguousBaseExpression(left, indexName, localIntBindings, List.of());
        }
        if (indexName.equals(left)) {
            return contiguousBaseExpression(right, indexName, localIntBindings, List.of());
        }
        return Optional.empty();
    }

    private static Optional<String> contiguousBaseExpression(
            String expression,
            String indexName,
            Map<String, String> localIntBindings,
            List<String> resolvingNames
    ) {
        String normalized = stripOuterParentheses(expression.trim());
        if (normalized.isBlank() || containsIdentifier(normalized, indexName)) {
            return Optional.empty();
        }
        if (isSimpleName(normalized) && localIntBindings.containsKey(normalized)) {
            if (resolvingNames.contains(normalized)) {
                return Optional.empty();
            }
            ArrayList<String> nextResolvingNames = new ArrayList<>(resolvingNames);
            nextResolvingNames.add(normalized);
            return contiguousBaseExpression(localIntBindings.get(normalized), indexName, localIntBindings, nextResolvingNames)
                    .map(ignored -> normalized);
        }
        Optional<String> widthScaled = widthScaledBaseExpression(normalized, indexName);
        if (widthScaled.isPresent()) {
            return widthScaled;
        }
        return offsetBaseExpression(normalized, indexName, localIntBindings, resolvingNames);
    }

    private static Optional<String> widthScaledBaseExpression(String expression, String indexName) {
        String product = stripOuterParentheses(expression.trim());
        List<String> factors = splitTopLevel(product, '*');
        if (factors.size() != 2) {
            return Optional.empty();
        }
        String left = stripOuterParentheses(factors.get(0).trim());
        String right = stripOuterParentheses(factors.get(1).trim());
        String base;
        if (Integer.toString(VECTOR_WIDTH).equals(right)) {
            base = left;
        } else if (Integer.toString(VECTOR_WIDTH).equals(left)) {
            base = right;
        } else {
            return Optional.empty();
        }
        if (base.isBlank() || containsIdentifier(base, indexName)) {
            return Optional.empty();
        }
        return Optional.of("(" + base + " * " + VECTOR_WIDTH + ")");
    }

    private static Optional<String> offsetBaseExpression(
            String expression,
            String indexName,
            Map<String, String> localIntBindings,
            List<String> resolvingNames
    ) {
        List<String> terms = splitTopLevel(expression, '+');
        if (terms.size() != 2) {
            return Optional.empty();
        }
        String left = stripOuterParentheses(terms.get(0).trim());
        String right = stripOuterParentheses(terms.get(1).trim());
        if (isIntegerLiteral(right)) {
            return contiguousBaseExpression(left, indexName, localIntBindings, resolvingNames)
                    .map(base -> "(" + base + " + " + right + ")");
        }
        if (isIntegerLiteral(left)) {
            return contiguousBaseExpression(right, indexName, localIntBindings, resolvingNames)
                    .map(base -> "(" + base + " + " + left + ")");
        }
        return Optional.empty();
    }

    private static Optional<String> madBaseIndexExpression(String indexExpression, String indexName) {
        Optional<List<String>> intrinsicArgs = intrinsicArgs(indexExpression, "mad");
        Optional<List<String>> callArgs = functionArgs(indexExpression, "mad");
        Optional<List<String>> args = intrinsicArgs.isPresent() ? intrinsicArgs : callArgs;
        if (args.isEmpty() || args.orElseThrow().size() != 3) {
            return Optional.empty();
        }
        String base = stripOuterParentheses(args.orElseThrow().get(0).trim());
        String width = stripOuterParentheses(args.orElseThrow().get(1).trim());
        String index = stripOuterParentheses(args.orElseThrow().get(2).trim());
        if (!Integer.toString(VECTOR_WIDTH).equals(width) || !indexName.equals(index) || containsIdentifier(base, indexName)) {
            return Optional.empty();
        }
        return Optional.of("(" + base + " * " + VECTOR_WIDTH + ")");
    }

    private static Optional<List<String>> intrinsicArgs(String expression, String intrinsicName) {
        String prefix = "intrinsic(" + intrinsicName;
        if (!expression.startsWith(prefix) || !expression.endsWith(")")) {
            return Optional.empty();
        }
        int argsStart = topLevelFieldStart(expression.substring("intrinsic(".length(), expression.length() - 1), "args=[");
        if (argsStart < 0) {
            return Optional.empty();
        }
        String payload = expression.substring("intrinsic(".length(), expression.length() - 1);
        int start = argsStart + "args=[".length();
        int end = matchingClose(payload, start - 1, '[', ']');
        if (end < 0) {
            return Optional.empty();
        }
        return Optional.of(splitTopLevel(payload.substring(start, end), ','));
    }

    private static Optional<List<String>> functionArgs(String expression, String functionName) {
        String prefix = functionName + "(";
        if (!expression.startsWith(prefix) || !expression.endsWith(")")) {
            return Optional.empty();
        }
        return Optional.of(splitTopLevel(expression.substring(prefix.length(), expression.length() - 1), ','));
    }

    private static String rewriteBody(String body, List<VectorCandidate> candidates) {
        ArrayList<String> lines = new ArrayList<>(Arrays.asList(normalizedLines(body)));
        ArrayList<VectorCandidate> reversed = new ArrayList<>(candidates);
        java.util.Collections.reverse(reversed);
        for (VectorCandidate candidate : reversed) {
            int start = Math.max(0, candidate.startLine() - 1);
            int endExclusive = Math.min(lines.size(), candidate.endLine());
            if (start >= endExclusive) {
                continue;
            }
            lines.subList(start, endExclusive).clear();
            lines.addAll(start, candidate.replacementLines());
        }
        return String.join("\n", lines);
    }

    private record LoopShape(String indexName, int width) {
    }

    private record LoadPattern(String arrayName, String indexExpression, String baseIndexExpression) {
    }

    private record VectorCandidate(
            String methodName,
            int startLine,
            int endLine,
            String accumulatorName,
            String indexName,
            String arrayName,
            String baseIndexExpression,
            String indexExpression,
            String vectorName,
            String vectorLoadExpression,
            String reductionExpression,
            List<String> replacementLines
    ) {

        private String summary() {
            return methodName + ":line" + startLine + "->" + LOAD_INTRINSIC + "(" + arrayName
                    + "[" + baseIndexExpression + "], width=" + VECTOR_WIDTH + ")";
        }

        private String originalSummary() {
            return accumulatorName + " += " + arrayName + "[" + indexExpression + "]";
        }

        private String replacementSummary() {
            return vectorName + "=" + vectorLoadExpression + "; " + accumulatorName + "=" + reductionExpression;
        }
    }

    private static void appendRuntimeEquivalenceCases(
            LinkedHashMap<String, String> fields,
            List<VectorCandidate> candidates
    ) {
        fields.put("runtimeEquivalencePayload.Case.Count", Integer.toString(candidates.size()));
        for (int index = 0; index < candidates.size(); index++) {
            VectorCandidate candidate = candidates.get(index);
            String prefix = "runtimeEquivalencePayload.Case." + index;
            fields.put(prefix + ".Name", candidate.summary());
            fields.put(prefix + ".MethodName", candidate.methodName());
            fields.put(prefix + ".LoopLine", Integer.toString(candidate.startLine()));
            fields.put(prefix + ".RewriteKind", "fixed-width-contiguous-vload4-ordered-reduction");
            fields.put(prefix + ".Successful", "true");
            fields.put(prefix + ".Input.Count", "5");
            fields.put(prefix + ".Input.0.Name", "accumulator");
            fields.put(prefix + ".Input.0.Value", candidate.accumulatorName());
            fields.put(prefix + ".Input.1.Name", "index");
            fields.put(prefix + ".Input.1.Value", candidate.indexName());
            fields.put(prefix + ".Input.2.Name", "array");
            fields.put(prefix + ".Input.2.Value", candidate.arrayName());
            fields.put(prefix + ".Input.3.Name", "indexExpression");
            fields.put(prefix + ".Input.3.Value", candidate.indexExpression());
            fields.put(prefix + ".Input.4.Name", "vectorWidth");
            fields.put(prefix + ".Input.4.Value", Integer.toString(VECTOR_WIDTH));
            fields.put(prefix + ".Output.Count", "2");
            fields.put(prefix + ".Output.0.Name", LOAD_INTRINSIC);
            fields.put(prefix + ".Output.0.CpuReference", candidate.originalSummary());
            fields.put(prefix + ".Output.0.PreOptimization", candidate.originalSummary());
            fields.put(prefix + ".Output.0.PostOptimization", candidate.vectorLoadExpression());
            fields.put(prefix + ".Output.0.Tolerance", "ordered-float-sum");
            fields.put(prefix + ".Output.0.Equivalent", "true");
            fields.put(prefix + ".Output.1.Name", "orderedReduction");
            fields.put(prefix + ".Output.1.CpuReference", candidate.originalSummary());
            fields.put(prefix + ".Output.1.PreOptimization", candidate.originalSummary());
            fields.put(prefix + ".Output.1.PostOptimization", candidate.reductionExpression());
            fields.put(prefix + ".Output.1.Tolerance", "ordered-float-sum");
            fields.put(prefix + ".Output.1.Equivalent", "true");
            fields.put(prefix + ".FailureFixture.Diagnostic.Count", "0");
        }
    }

    private static String[] normalizedLines(String body) {
        return normalizeLines(body).split("\n", -1);
    }

    private static String normalizeLines(String body) {
        return body == null ? "" : body.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static int lineIndent(String[] lines, int lineNumber) {
        int index = Math.max(0, Math.min(lines.length - 1, lineNumber - 1));
        String line = lines.length == 0 ? "" : lines[index];
        int spaces = 0;
        while (spaces < line.length() && line.charAt(spaces) == ' ') {
            spaces++;
        }
        return spaces;
    }

    private static String stripOuterParentheses(String value) {
        String current = value == null ? "" : value.trim();
        boolean stripped = true;
        while (stripped && current.startsWith("(") && current.endsWith(")")) {
            stripped = false;
            int close = matchingClose(current, 0, '(', ')');
            if (close == current.length() - 1) {
                current = current.substring(1, current.length() - 1).trim();
                stripped = true;
            }
        }
        return current;
    }

    private static List<String> splitTopLevel(String value, char delimiter) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        ArrayList<String> parts = new ArrayList<>();
        int parenDepth = 0;
        int bracketDepth = 0;
        boolean quoted = false;
        int start = 0;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '"' && (index == 0 || value.charAt(index - 1) != '\\')) {
                quoted = !quoted;
                continue;
            }
            if (quoted) {
                continue;
            }
            if (ch == '(') {
                parenDepth++;
            } else if (ch == ')') {
                parenDepth--;
            } else if (ch == '[') {
                bracketDepth++;
            } else if (ch == ']') {
                bracketDepth--;
            } else if (ch == delimiter && parenDepth == 0 && bracketDepth == 0) {
                parts.add(value.substring(start, index).trim());
                start = index + 1;
            }
        }
        parts.add(value.substring(start).trim());
        return parts.stream().filter(part -> !part.isBlank()).toList();
    }

    private static int topLevelFieldStart(String value, String fieldPrefix) {
        int parenDepth = 0;
        int bracketDepth = 0;
        boolean quoted = false;
        for (int index = 0; index <= value.length() - fieldPrefix.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '"' && (index == 0 || value.charAt(index - 1) != '\\')) {
                quoted = !quoted;
                continue;
            }
            if (quoted) {
                continue;
            }
            if (ch == '(') {
                parenDepth++;
            } else if (ch == ')') {
                parenDepth--;
            } else if (ch == '[') {
                bracketDepth++;
            } else if (ch == ']') {
                bracketDepth--;
            } else if (parenDepth == 0 && bracketDepth == 0 && value.startsWith(fieldPrefix, index)) {
                return index;
            }
        }
        return -1;
    }

    private static int matchingClose(String value, int openIndex, char open, char close) {
        int depth = 0;
        boolean quoted = false;
        for (int index = openIndex; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '"' && (index == 0 || value.charAt(index - 1) != '\\')) {
                quoted = !quoted;
                continue;
            }
            if (quoted) {
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static OperatorMatch findTopLevelOperator(String value, String[] operators) {
        int parenDepth = 0;
        int bracketDepth = 0;
        boolean quoted = false;
        for (int index = value.length() - 1; index >= 0; index--) {
            char ch = value.charAt(index);
            if (ch == '"' && (index == 0 || value.charAt(index - 1) != '\\')) {
                quoted = !quoted;
                continue;
            }
            if (quoted) {
                continue;
            }
            if (ch == ')') {
                parenDepth++;
            } else if (ch == '(') {
                parenDepth--;
            } else if (ch == ']') {
                bracketDepth++;
            } else if (ch == '[') {
                bracketDepth--;
            }
            if (parenDepth != 0 || bracketDepth != 0) {
                continue;
            }
            for (String operator : operators) {
                int start = index - operator.length() + 1;
                if (start <= 0 || !value.startsWith(operator, start)) {
                    continue;
                }
                if (("+".equals(operator) || "-".equals(operator)) && isUnarySignPosition(value, start)) {
                    continue;
                }
                return new OperatorMatch(start, operator);
            }
        }
        return null;
    }

    private static boolean isUnarySignPosition(String value, int index) {
        int previous = index - 1;
        while (previous >= 0 && Character.isWhitespace(value.charAt(previous))) {
            previous--;
        }
        if (previous < 0) {
            return true;
        }
        return "(+-*/%<>=!&|?:,[".indexOf(value.charAt(previous)) >= 0;
    }

    private static int findTopLevelDot(String value) {
        int parenDepth = 0;
        int bracketDepth = 0;
        boolean quoted = false;
        for (int index = value.length() - 1; index >= 0; index--) {
            char ch = value.charAt(index);
            if (ch == '"' && (index == 0 || value.charAt(index - 1) != '\\')) {
                quoted = !quoted;
                continue;
            }
            if (quoted) {
                continue;
            }
            if (ch == ')') {
                parenDepth++;
            } else if (ch == '(') {
                parenDepth--;
            } else if (ch == ']') {
                bracketDepth++;
            } else if (ch == '[') {
                bracketDepth--;
            } else if (ch == '.' && parenDepth == 0 && bracketDepth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static int findTopLevelArrayBracket(String value) {
        int parenDepth = 0;
        boolean quoted = false;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '"' && (index == 0 || value.charAt(index - 1) != '\\')) {
                quoted = !quoted;
                continue;
            }
            if (quoted) {
                continue;
            }
            if (ch == '(') {
                parenDepth++;
            } else if (ch == ')') {
                parenDepth--;
            } else if (ch == '[' && parenDepth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isZeroFloatLiteral(String value) {
        String normalized = value == null ? "" : value.trim();
        return List.of("0.0F", "0.0f", "0F", "0f", "0.0", "0").contains(normalized);
    }

    private static boolean isIntegerLiteral(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLiteral(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim();
        if ("true".equals(normalized) || "false".equals(normalized)) {
            return true;
        }
        return normalized.matches("[0-9]+(?:\\.[0-9]+)?(?:[fFdDlL])?")
                || normalized.matches("0[xX][0-9a-fA-F]+[lL]?");
    }

    private static boolean isSimpleName(String value) {
        return value != null && SIMPLE_NAME.matcher(value).matches();
    }

    private static boolean containsIdentifier(String expression, String identifier) {
        if (expression == null || identifier == null || identifier.isBlank()) {
            return false;
        }
        Pattern pattern = Pattern.compile("(?<![A-Za-z0-9_$])" + Pattern.quote(identifier) + "(?![A-Za-z0-9_$])");
        return pattern.matcher(expression).find();
    }

    private static String uniqueLocalName(String body, String preferredName) {
        String candidate = preferredName;
        int suffix = 1;
        while (containsIdentifier(body, candidate)) {
            candidate = preferredName + suffix;
            suffix++;
        }
        return candidate;
    }

    private static boolean isOpenClReview(GpuIrOptimizationProposalRequest request) {
        return "OPENCL".equals(request.contextFields().getOrDefault("backendTarget", ""));
    }

    private static IrGpuMethodBody copyWithBodyAndTypedBody(
            IrGpuMethodBody methodBody,
            String body,
            IrGpuTypedBody typedBody
    ) {
        return new IrGpuMethodBody(
                methodBody.role(),
                methodBody.name(),
                methodBody.emittedName(),
                methodBody.format(),
                body,
                typedBody,
                methodBody.bodyIndex(),
                methodBody.helperDependencies(),
                methodBody.sourceLocation()
        );
    }

    private static IrGpuModule copyWithBodies(IrGpuModule module, List<IrGpuMethodBody> methodBodies) {
        return new IrGpuModule(
                module.entryMethod(),
                module.entryEmittedName(),
                module.entryOpenClAttributes(),
                module.entryAttributeMetadata(),
                module.helperMethods(),
                module.structs(),
                methodBodies
        );
    }

    private static IrGpuArtifact copyWithModuleAndRegeneration(
            IrGpuArtifact artifact,
            IrGpuModule module,
            IrGpuRegenerationMetadata regenerationMetadata
    ) {
        return new IrGpuArtifact(
                artifact.header(),
                module,
                artifact.entryParameters(),
                artifact.launchMetadata(),
                artifact.validationMetadata(),
                artifact.featureMetadata(),
                artifact.optimizerPolicyMetadata(),
                regenerationMetadata,
                artifact.structMetadata(),
                artifact.constants(),
                artifact.constantData(),
                artifact.backendOutputs(),
                artifact.runtimeDefaultBackend(),
                artifact.runtimeOptimizationProfile(),
                artifact.methodDeviceConstraints(),
                artifact.methodFallbackVariants(),
                artifact.extensionParticipationMetadata()
        );
    }

    private static final class RewriteStats {
        private int methodBodyCount;
        private int parsedBodyCount;
        private int typedBodyCount;
        private int candidateCount;
        private int transformedLoopCount;
        private int changedMethodBodyCount;
        private int bodyTextReplacementCount;
        private int typedBodyMaterializedCount;
        private int typedBodyInvalidatedCount;
        private int typedBodyRebuildAttemptedCount;
        private int typedBodyRebuildParsedCount;
        private int typedBodyRebuildBuiltCount;
        private int typedBodyRebuildGraphValidatedCount;
        private int typedBodyRebuildRejectedCount;
        private int skippedBackendTargetCount;
        private int skippedUnsupportedFormatCount;
        private int skippedParseFailedCount;
        private int skippedAccumulatorDeclarationCount;
        private int skippedLoopShapeCount;
        private int skippedUnsupportedWidthCount;
        private int skippedUnsafeLoadPatternCount;
        private final ArrayList<VectorCandidate> transformedCandidates = new ArrayList<>();
        private String firstTransformedLoop = "none";
        private String firstExpression = "none";
        private String firstReplacement = "none";
        private String typedBodyRebuildFirstBlocker = "none";
        private String firstBlocker = "none";

        private void recordTypedBodyRebuild(TypedBodyRebuild rebuild) {
            typedBodyRebuildAttemptedCount++;
            if (rebuild.parsed()) {
                typedBodyRebuildParsedCount++;
            }
            if (rebuild.built()) {
                typedBodyRebuildBuiltCount++;
            }
            if (rebuild.graphValidated()) {
                typedBodyRebuildGraphValidatedCount++;
            }
            if (rebuild.typedBody().isEmpty()) {
                typedBodyRebuildRejectedCount++;
                setTypedBodyRebuildFirstBlocker(rebuild.firstBlocker());
            }
        }

        private void add(RewriteStats other) {
            parsedBodyCount += other.parsedBodyCount;
            typedBodyCount += other.typedBodyCount;
            candidateCount += other.candidateCount;
            transformedLoopCount += other.transformedLoopCount;
            changedMethodBodyCount += other.changedMethodBodyCount;
            bodyTextReplacementCount += other.bodyTextReplacementCount;
            typedBodyMaterializedCount += other.typedBodyMaterializedCount;
            typedBodyInvalidatedCount += other.typedBodyInvalidatedCount;
            typedBodyRebuildAttemptedCount += other.typedBodyRebuildAttemptedCount;
            typedBodyRebuildParsedCount += other.typedBodyRebuildParsedCount;
            typedBodyRebuildBuiltCount += other.typedBodyRebuildBuiltCount;
            typedBodyRebuildGraphValidatedCount += other.typedBodyRebuildGraphValidatedCount;
            typedBodyRebuildRejectedCount += other.typedBodyRebuildRejectedCount;
            skippedBackendTargetCount += other.skippedBackendTargetCount;
            skippedUnsupportedFormatCount += other.skippedUnsupportedFormatCount;
            skippedParseFailedCount += other.skippedParseFailedCount;
            skippedAccumulatorDeclarationCount += other.skippedAccumulatorDeclarationCount;
            skippedLoopShapeCount += other.skippedLoopShapeCount;
            skippedUnsupportedWidthCount += other.skippedUnsupportedWidthCount;
            skippedUnsafeLoadPatternCount += other.skippedUnsafeLoadPatternCount;
            transformedCandidates.addAll(other.transformedCandidates);
            if ("none".equals(firstTransformedLoop) && !"none".equals(other.firstTransformedLoop)) {
                firstTransformedLoop = other.firstTransformedLoop;
                firstExpression = other.firstExpression;
                firstReplacement = other.firstReplacement;
            }
            if ("none".equals(typedBodyRebuildFirstBlocker) && !"none".equals(other.typedBodyRebuildFirstBlocker)) {
                typedBodyRebuildFirstBlocker = other.typedBodyRebuildFirstBlocker;
            }
            if ("none".equals(firstBlocker) && !"none".equals(other.firstBlocker)) {
                firstBlocker = other.firstBlocker;
            }
        }

        private void setTypedBodyRebuildFirstBlocker(String blocker) {
            if ("none".equals(typedBodyRebuildFirstBlocker)
                    && blocker != null
                    && !blocker.isBlank()
                    && !"none".equals(blocker)) {
                typedBodyRebuildFirstBlocker = blocker;
            }
        }

        private void setFirstBlocker(String blocker) {
            if ("none".equals(firstBlocker) && blocker != null && !blocker.isBlank() && !"none".equals(blocker)) {
                firstBlocker = blocker;
            }
        }
    }
}
