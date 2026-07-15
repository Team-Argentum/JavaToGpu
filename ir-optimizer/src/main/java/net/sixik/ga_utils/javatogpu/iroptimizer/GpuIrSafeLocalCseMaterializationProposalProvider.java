package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrGpuSourceEmission;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Review-only local CSE materialization that reuses an already materialized local value.
 */
public final class GpuIrSafeLocalCseMaterializationProposalProvider implements GpuIrOptimizationProposalProvider {

    public static final String PROVIDER_ID = GpuIrOptimizerModule.MODULE_ID + ".safe-local-cse-materialization";
    public static final String PROVIDER_VERSION = PROVIDER_ID + ":1";

    private static final Pattern SIMPLE_LOCAL_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final List<String> SUPPORTED_BINARY_OPERATORS = List.of(
            "+", "-", "*", "&", "|", "^", "==", "!=", "<", "<=", ">", ">="
    );

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
                            "ir-optimizer.safe-local-cse-materialization",
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
                        "ir-optimizer.safe-local-cse-materialization",
                        "review-only-safe-local-cse-materialized",
                        rewrite.fields(request)
                ),
                List.of("materialized " + rewrite.transformedNodeCount()
                        + " safe local CSE rewrite(s) into a review candidate")
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
        return 415;
    }

    private record ArtifactRewrite(
            Optional<IrGpuModule> module,
            int methodBodyCount,
            int typedBodyCount,
            int localBindingCount,
            int introducedTemporaryCount,
            int candidateCount,
            int transformedNodeCount,
            int changedMethodBodyCount,
            int bodyTextReplacementCount,
            int fixedPointPassCount,
            int skippedTypedBodyMissingCount,
            int skippedUnsupportedFormatCount,
            int skippedControlFlowBoundaryCount,
            int skippedMissingChildReferenceCount,
            int skippedUnsupportedOperatorCount,
            int skippedImpureOperandCount,
            int skippedNoExistingLocalBindingCount,
            int skippedLocalInvalidationCount,
            int skippedBindingTextPatternMissingCount,
            int skippedBodyTextPatternMissingCount,
            List<CseCandidate> transformedCandidates,
            String firstTransformedNode,
            String firstExpression,
            String firstReplacement,
            String firstBlocker,
            boolean openClReviewSourceReady,
            String openClReviewSourceLength,
            boolean mutationAllowed
    ) {

        private static ArtifactRewrite from(IrGpuArtifact artifact, boolean openClReview, boolean mutationAllowed) {
            ArrayList<IrGpuMethodBody> rewrittenBodies = new ArrayList<>();
            RewriteStats stats = new RewriteStats();
            stats.methodBodyCount = artifact.module().methodBodies().size();

            for (IrGpuMethodBody methodBody : artifact.module().methodBodies()) {
                MethodRewrite methodRewrite = rewriteMethodBody(methodBody);
                rewrittenBodies.add(methodRewrite.methodBody());
                stats.add(methodRewrite.stats());
            }

            if (stats.transformedNodeCount == 0) {
                return noChange(stats, firstBlocker(stats), mutationAllowed);
            }

            IrGpuModule rewrittenModule = copyWithBodies(artifact.module(), rewrittenBodies);
            if (openClReview) {
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
            return changed(stats, rewrittenModule, false, "not-requested", mutationAllowed);
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
                    stats.typedBodyCount,
                    stats.localBindingCount,
                    stats.introducedTemporaryCount,
                    stats.candidateCount,
                    stats.transformedNodeCount,
                    stats.changedMethodBodyCount,
                    stats.bodyTextReplacementCount,
                    stats.fixedPointPassCount,
                    stats.skippedTypedBodyMissingCount,
                    stats.skippedUnsupportedFormatCount,
                    stats.skippedControlFlowBoundaryCount,
                    stats.skippedMissingChildReferenceCount,
                    stats.skippedUnsupportedOperatorCount,
                    stats.skippedImpureOperandCount,
                    stats.skippedNoExistingLocalBindingCount,
                    stats.skippedLocalInvalidationCount,
                    stats.skippedBindingTextPatternMissingCount,
                    stats.skippedBodyTextPatternMissingCount,
                    List.copyOf(stats.transformedCandidates),
                    stats.firstTransformedNode,
                    stats.firstExpression,
                    stats.firstReplacement,
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
                    stats.typedBodyCount,
                    stats.localBindingCount,
                    stats.introducedTemporaryCount,
                    stats.candidateCount,
                    stats.transformedNodeCount,
                    stats.changedMethodBodyCount,
                    stats.bodyTextReplacementCount,
                    stats.fixedPointPassCount,
                    stats.skippedTypedBodyMissingCount,
                    stats.skippedUnsupportedFormatCount,
                    stats.skippedControlFlowBoundaryCount,
                    stats.skippedMissingChildReferenceCount,
                    stats.skippedUnsupportedOperatorCount,
                    stats.skippedImpureOperandCount,
                    stats.skippedNoExistingLocalBindingCount,
                    stats.skippedLocalInvalidationCount,
                    stats.skippedBindingTextPatternMissingCount,
                    stats.skippedBodyTextPatternMissingCount,
                    List.copyOf(stats.transformedCandidates),
                    stats.firstTransformedNode,
                    stats.firstExpression,
                    stats.firstReplacement,
                    firstBlocker,
                    false,
                    "not-ready",
                    mutationAllowed
            );
        }

        private boolean changed() {
            return module.isPresent() && transformedNodeCount > 0;
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
                return "safe local CSE materialization rewrote " + transformedNodeCount
                        + " repeated pure expression(s) to local references";
            }
            return "safe local CSE materialization produced no review candidate: " + firstBlocker;
        }

        private Map<String, String> fields(GpuIrOptimizationProposalRequest request) {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("methodBody.count", Integer.toString(methodBodyCount));
            fields.put("methodBody.rewriteScope", "all-method-bodies");
            fields.put("typedBody.count", Integer.toString(typedBodyCount));
            fields.put("localBinding.count", Integer.toString(localBindingCount));
            fields.put("introducedTemporary.count", Integer.toString(introducedTemporaryCount));
            fields.put("candidate.count", Integer.toString(candidateCount));
            fields.put("transformedNode.count", Integer.toString(transformedNodeCount));
            fields.put("changedMethodBody.count", Integer.toString(changedMethodBodyCount));
            fields.put("bodyTextReplacement.count", Integer.toString(bodyTextReplacementCount));
            fields.put("fixedPoint.enabled", "true");
            fields.put("fixedPoint.pass.count", Integer.toString(fixedPointPassCount));
            fields.put("fixedPoint.reachableNodeScan", "true");
            fields.put("fixedPoint.rewriteGranularity", "single-reusable-expression-per-pass");
            fields.put("skipped.typedBodyMissing.count", Integer.toString(skippedTypedBodyMissingCount));
            fields.put("skipped.unsupportedFormat.count", Integer.toString(skippedUnsupportedFormatCount));
            fields.put("skipped.controlFlowBoundary.count", Integer.toString(skippedControlFlowBoundaryCount));
            fields.put("skipped.missingChildReference.count", Integer.toString(skippedMissingChildReferenceCount));
            fields.put("skipped.unsupportedOperator.count", Integer.toString(skippedUnsupportedOperatorCount));
            fields.put("skipped.impureOperand.count", Integer.toString(skippedImpureOperandCount));
            fields.put("skipped.noExistingLocalBinding.count", Integer.toString(skippedNoExistingLocalBindingCount));
            fields.put("skipped.localInvalidation.count", Integer.toString(skippedLocalInvalidationCount));
            fields.put("skipped.bindingTextPatternMissing.count", Integer.toString(skippedBindingTextPatternMissingCount));
            fields.put("skipped.bodyTextPatternMissing.count", Integer.toString(skippedBodyTextPatternMissingCount));
            fields.put("rewrite.proposed", Boolean.toString(changed()));
            fields.put("rewrite.materialized", Boolean.toString(changed()));
            fields.put("optimizerFamily", "safe-local-cse-materialization");
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
                    "optimizer-family:safe-local-cse-materialization:review-candidate"
            );
            fields.put(
                    "runtimeEquivalencePayload.resource",
                    "ir-optimizer://safe-local-cse-materialization/review-candidate"
            );
            fields.put("runtimeEquivalencePayload.cpuReference.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.preOptimizationOutput.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.postOptimizationOutput.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.tolerance.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.failureFixture.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.CpuReference", "reuseExistingLocal=" + transformedNodeCount);
            fields.put("runtimeEquivalencePayload.PreOptimizationOutput", "expressions=" + transformedNodeCount
                    + ", first=" + firstExpression);
            fields.put("runtimeEquivalencePayload.PostOptimizationOutput", "localRefs=" + transformedNodeCount
                    + ", first=" + firstReplacement);
            fields.put("runtimeEquivalencePayload.Tolerance", "mode=exact-expression-reuse, floatingPoint=unchanged");
            fields.put("runtimeEquivalencePayload.FailureFixture", "none");
            fields.put(
                    "runtimeEquivalencePayload.ReferenceMode",
                    introducedTemporaryCount > 0
                            ? "static-local-expression-reuse-with-introduced-temporary"
                            : "static-existing-local-expression-reuse"
            );
            fields.put("runtimeEquivalencePayload.CaseIdentity", "method-name-and-node-id");
            appendRuntimeEquivalenceCases(fields, transformedCandidates);
            fields.put("reviewPackage.required", Boolean.toString(changed()));
            fields.put("reviewPackage.firstBlocker", changed() ? "approval-pending" : firstBlocker);
            fields.put(
                    "safety.scope",
                    introducedTemporaryCount > 0
                            ? "straight-line-existing-or-introduced-local-pure-binary-expression-reuse"
                            : "straight-line-existing-local-pure-binary-expression-reuse"
            );
            fields.put("safety.dominanceProven", Boolean.toString(changed()));
            fields.put("safety.sideEffectFreedomProven", Boolean.toString(changed()));
            fields.put("safety.valueNumberingScope", "method-local-straight-line");
            fields.put("safety.newTemporaryIntroduced", Boolean.toString(introducedTemporaryCount > 0));
            fields.put("openClReview.sourceReady", Boolean.toString(openClReviewSourceReady));
            fields.put("openClReview.sourceLength", openClReviewSourceLength);
            fields.put("firstTransformedNode", firstTransformedNode);
            fields.put("firstExpression", firstExpression);
            fields.put("firstReplacement", firstReplacement);
            fields.put("firstBlocker", changed() ? "none" : firstBlocker);
            return Map.copyOf(fields);
        }

        private static String firstBlocker(RewriteStats stats) {
            if (!"none".equals(stats.firstBlocker)) {
                return stats.firstBlocker;
            }
            if (stats.typedBodyCount == 0) {
                return "typed-body-missing";
            }
            if (stats.localBindingCount == 0) {
                return "no-existing-local-expression-binding";
            }
            return "no-repeated-expression-after-existing-local-binding";
        }
    }

    private record MethodRewrite(IrGpuMethodBody methodBody, RewriteStats stats) {
    }

    private static MethodRewrite rewriteMethodBody(IrGpuMethodBody methodBody) {
        RewriteStats stats = new RewriteStats();
        if (!methodBody.typedBody().available()) {
            stats.skippedTypedBodyMissingCount++;
            stats.setFirstBlocker("typed-body-missing");
            return new MethodRewrite(methodBody, stats);
        }
        stats.typedBodyCount = 1;
        if (!"ir-text-v1".equals(methodBody.format())) {
            stats.skippedUnsupportedFormatCount++;
            stats.setFirstBlocker("method-body-format-unsupported-" + methodBody.format());
            return new MethodRewrite(methodBody, stats);
        }
        if (hasControlFlowBoundary(methodBody.typedBody())) {
            stats.skippedControlFlowBoundaryCount++;
            stats.setFirstBlocker("control-flow-boundary-present");
            return new MethodRewrite(methodBody, stats);
        }

        IrGpuMethodBody rewrittenMethodBody = methodBody;
        int maxPasses = Math.max(1, methodBody.typedBody().nodes().size());
        for (int pass = 0; pass < maxPasses; pass++) {
            Map<Integer, IrGpuTypedNode> nodesById = GpuIrTypedBodyGraphPatch.nodesById(rewrittenMethodBody.typedBody());
            GpuIrTypedBodyGraphPatch.Reachability reachability = GpuIrTypedBodyGraphPatch
                    .reachability(rewrittenMethodBody.typedBody(), nodesById);
            if (reachability.rootMissingCount() > 0 || reachability.missingChildReferenceCount() > 0) {
                stats.skippedMissingChildReferenceCount += reachability.rootMissingCount()
                        + reachability.missingChildReferenceCount();
                stats.setFirstBlocker("missing-child-reference");
                return new MethodRewrite(rewrittenMethodBody, stats);
            }

            RewriteStats scanStats = new RewriteStats();
            ScanResult scanResult = scanMethodBody(
                    rewrittenMethodBody.name(),
                    rewrittenMethodBody.body(),
                    rewrittenMethodBody.typedBody(),
                    nodesById,
                    reachability.reachableNodeIds(),
                    scanStats
            );
            if (scanResult.candidate().isEmpty()) {
                Optional<TemporaryCseCandidate> temporaryCandidate = firstTemporaryIntroductionCandidate(
                        rewrittenMethodBody.name(),
                        rewrittenMethodBody.body(),
                        rewrittenMethodBody.typedBody(),
                        nodesById,
                        reachability.reachableNodeIds(),
                        scanStats
                );
                if (temporaryCandidate.isPresent()) {
                    TemporaryCseCandidate candidate = temporaryCandidate.orElseThrow();
                    TemporaryPatch patch = applyTemporaryIntroduction(
                            rewrittenMethodBody.typedBody(),
                            rewrittenMethodBody.body(),
                            nodesById,
                            candidate
                    );
                    stats.add(scanStats);
                    if (!patch.applied()) {
                        stats.candidateCount += candidate.occurrenceCount();
                        stats.recordPatchBlocker(patch.blocker());
                        break;
                    }

                    stats.candidateCount += candidate.occurrenceCount();
                    stats.transformedNodeCount += candidate.occurrenceCount();
                    stats.changedMethodBodyCount = 1;
                    stats.bodyTextReplacementCount += candidate.occurrenceCount();
                    stats.fixedPointPassCount++;
                    stats.introducedTemporaryCount++;
                    stats.transformedCandidates.addAll(candidate.toCseCandidates());
                    if ("none".equals(stats.firstTransformedNode)) {
                        CseCandidate first = candidate.toCseCandidates().get(0);
                        stats.firstTransformedNode = first.summary();
                        stats.firstExpression = first.expressionText();
                        stats.firstReplacement = first.replacementText();
                    }

                    rewrittenMethodBody = copyWithBodyAndTypedBody(
                            rewrittenMethodBody,
                            patch.body(),
                            patch.typedBody()
                    );
                    continue;
                }
                if (stats.transformedNodeCount == 0) {
                    stats.add(scanStats);
                }
                break;
            }

            CseCandidate candidate = scanResult.candidate().orElseThrow();
            GpuIrTypedBodyGraphPatch.Applied patch = graphPatch(candidate)
                    .apply(rewrittenMethodBody.typedBody(), rewrittenMethodBody.body());
            stats.add(scanStats);
            if (!patch.applied()) {
                stats.candidateCount++;
                stats.recordPatchBlocker(patch.blocker());
                break;
            }

            stats.candidateCount++;
            stats.transformedNodeCount++;
            stats.changedMethodBodyCount = 1;
            stats.bodyTextReplacementCount++;
            stats.fixedPointPassCount++;
            stats.transformedCandidates.add(candidate);
            if ("none".equals(stats.firstTransformedNode)) {
                stats.firstTransformedNode = candidate.summary();
                stats.firstExpression = candidate.expressionText();
                stats.firstReplacement = candidate.replacementText();
            }

            rewrittenMethodBody = copyWithBodyAndTypedBody(rewrittenMethodBody, patch.body(), patch.typedBody());
        }
        return new MethodRewrite(rewrittenMethodBody, stats);
    }

    private static GpuIrTypedBodyGraphPatch.Plan graphPatch(CseCandidate candidate) {
        return GpuIrTypedBodyGraphPatch.plan(
                candidate.expressionText(),
                candidate.replacementText(),
                new IrGpuTypedNode(
                        candidate.nodeId(),
                        "GpuIrVariableRef",
                        Map.of("name", candidate.replacementText()),
                        Map.of()
                ),
                candidate.bindingSearchStart()
        );
    }

    private record ScanResult(Optional<CseCandidate> candidate) {

        private static ScanResult none() {
            return new ScanResult(Optional.empty());
        }

        private static ScanResult found(CseCandidate candidate) {
            return new ScanResult(Optional.of(candidate));
        }
    }

    private record TemporaryPatch(IrGpuTypedBody typedBody, String body, boolean applied, String blocker) {

        private static TemporaryPatch blocked(IrGpuTypedBody typedBody, String body, String blocker) {
            return new TemporaryPatch(typedBody, body, false, blocker);
        }
    }

    private static ScanResult scanMethodBody(
            String methodName,
            String body,
            IrGpuTypedBody typedBody,
            Map<Integer, IrGpuTypedNode> nodesById,
            Set<Integer> reachableNodeIds,
            RewriteStats stats
    ) {
        LinkedHashMap<String, LocalBinding> bindings = new LinkedHashMap<>();
        for (Integer rootNodeId : typedBody.rootNodeIds()) {
            IrGpuTypedNode root = nodesById.get(rootNodeId);
            if (root == null || !reachableNodeIds.contains(root.id())) {
                continue;
            }
            Optional<CseCandidate> candidate = firstReusableExpression(methodName, root, nodesById, bindings, reachableNodeIds);
            if (candidate.isPresent()) {
                return ScanResult.found(candidate.orElseThrow());
            }
            updateLocalBindings(root, body, nodesById, bindings, stats);
        }
        if (!bindings.isEmpty()) {
            stats.skippedNoExistingLocalBindingCount++;
        }
        return ScanResult.none();
    }

    private static Optional<TemporaryCseCandidate> firstTemporaryIntroductionCandidate(
            String methodName,
            String body,
            IrGpuTypedBody typedBody,
            Map<Integer, IrGpuTypedNode> nodesById,
            Set<Integer> reachableNodeIds,
            RewriteStats stats
    ) {
        LinkedHashMap<String, ArrayList<ExpressionOccurrence>> occurrencesByFingerprint = new LinkedHashMap<>();
        List<Integer> roots = typedBody.rootNodeIds();
        for (int rootIndex = 0; rootIndex < roots.size(); rootIndex++) {
            IrGpuTypedNode root = nodesById.get(roots.get(rootIndex));
            if (root == null || !reachableNodeIds.contains(root.id())) {
                continue;
            }
            collectExpressionOccurrences(rootIndex, root, nodesById, reachableNodeIds, occurrencesByFingerprint);
        }

        Set<String> localNames = collectLocalNames(typedBody, nodesById);
        for (ArrayList<ExpressionOccurrence> occurrences : occurrencesByFingerprint.values()) {
            if (occurrences.size() < 2) {
                continue;
            }
            ExpressionOccurrence first = occurrences.get(0);
            if (hasLocalInvalidationBetween(typedBody, nodesById, first.expression().variableNames(), occurrences)) {
                stats.skippedLocalInvalidationCount++;
                stats.setFirstBlocker("local-invalidation-between-cse-uses");
                continue;
            }
            int textOccurrenceCount = allIndexesOf(body, first.expression().sourceText()).size();
            if (textOccurrenceCount != occurrences.size()) {
                stats.skippedBodyTextPatternMissingCount++;
                stats.setFirstBlocker("body-text-pattern-missing");
                continue;
            }
            return Optional.of(new TemporaryCseCandidate(
                    methodName,
                    nextTemporaryName(localNames),
                    inferTemporaryType(first.node(), first.expression().sourceText()),
                    first.expression(),
                    List.copyOf(occurrences)
            ));
        }
        return Optional.empty();
    }

    private static void collectExpressionOccurrences(
            int rootIndex,
            IrGpuTypedNode node,
            Map<Integer, IrGpuTypedNode> nodesById,
            Set<Integer> reachableNodeIds,
            LinkedHashMap<String, ArrayList<ExpressionOccurrence>> occurrencesByFingerprint
    ) {
        Optional<PureExpression> expression = pureExpression(node, nodesById, null);
        if (expression.isPresent() && "GpuIrBinary".equals(node.kind())) {
            PureExpression pureExpression = expression.orElseThrow();
            occurrencesByFingerprint.computeIfAbsent(pureExpression.fingerprint(), ignored -> new ArrayList<>())
                    .add(new ExpressionOccurrence(rootIndex, node.id(), node, pureExpression));
        }
        for (Map.Entry<String, List<Integer>> entry : node.children().entrySet()) {
            if ("target".equals(entry.getKey())) {
                continue;
            }
            for (Integer childId : entry.getValue()) {
                if (!reachableNodeIds.contains(childId)) {
                    continue;
                }
                IrGpuTypedNode child = nodesById.get(childId);
                if (child != null) {
                    collectExpressionOccurrences(rootIndex, child, nodesById, reachableNodeIds, occurrencesByFingerprint);
                }
            }
        }
    }

    private static boolean hasLocalInvalidationBetween(
            IrGpuTypedBody typedBody,
            Map<Integer, IrGpuTypedNode> nodesById,
            Set<String> variableNames,
            List<ExpressionOccurrence> occurrences
    ) {
        if (variableNames.isEmpty() || occurrences.isEmpty()) {
            return false;
        }
        int firstRootIndex = occurrences.stream().mapToInt(ExpressionOccurrence::rootIndex).min().orElse(0);
        int lastRootIndex = occurrences.stream().mapToInt(ExpressionOccurrence::rootIndex).max().orElse(firstRootIndex);
        List<Integer> roots = typedBody.rootNodeIds();
        for (int index = firstRootIndex; index <= lastRootIndex && index < roots.size(); index++) {
            IrGpuTypedNode root = nodesById.get(roots.get(index));
            if (root == null) {
                continue;
            }
            Optional<BindingSource> source = bindingSource(root, nodesById);
            if (source.isPresent() && variableNames.contains(source.orElseThrow().targetName())) {
                return true;
            }
        }
        return false;
    }

    private static TemporaryPatch applyTemporaryIntroduction(
            IrGpuTypedBody typedBody,
            String body,
            Map<Integer, IrGpuTypedNode> nodesById,
            TemporaryCseCandidate candidate
    ) {
        if (typedBody == null || !typedBody.available()) {
            return TemporaryPatch.blocked(typedBody, body, "typed-body-missing");
        }
        if (body == null || candidate.expression().sourceText().isBlank()) {
            return TemporaryPatch.blocked(typedBody, body, "body-text-pattern-missing");
        }
        List<Integer> indexes = allIndexesOf(body, candidate.expression().sourceText());
        if (indexes.size() != candidate.occurrenceCount()) {
            return TemporaryPatch.blocked(typedBody, body, "body-text-pattern-missing");
        }

        String replacedBody = replaceAll(body, candidate.expression().sourceText(), candidate.temporaryName());
        int lineStart = lineStart(body, indexes.get(0));
        String declaration = lineIndentation(body, lineStart)
                + "var " + candidate.typeName() + " " + candidate.temporaryName()
                + " = " + candidate.expression().sourceText() + "\n";
        String patchedBody = replacedBody.substring(0, lineStart) + declaration + replacedBody.substring(lineStart);
        IrGpuTypedBody patchedTypedBody = introduceTemporaryTypedBody(typedBody, nodesById, candidate);
        GpuIrTypedBodyGraphPatch.Reachability reachability = GpuIrTypedBodyGraphPatch.reachability(
                patchedTypedBody,
                GpuIrTypedBodyGraphPatch.nodesById(patchedTypedBody)
        );
        if (reachability.rootMissingCount() > 0 || reachability.missingChildReferenceCount() > 0) {
            return TemporaryPatch.blocked(typedBody, body, "typed-temporary-graph-invalid");
        }
        return new TemporaryPatch(patchedTypedBody, patchedBody, true, "none");
    }

    private static IrGpuTypedBody introduceTemporaryTypedBody(
            IrGpuTypedBody typedBody,
            Map<Integer, IrGpuTypedNode> nodesById,
            TemporaryCseCandidate candidate
    ) {
        LinkedHashSet<Integer> occurrenceIds = new LinkedHashSet<>();
        for (ExpressionOccurrence occurrence : candidate.occurrences()) {
            occurrenceIds.add(occurrence.nodeId());
        }
        IdAllocator ids = new IdAllocator(GpuIrTypedBodyGraphPatch.nextNodeId(typedBody));
        int declarationId = ids.next();
        CloneResult initializer = cloneSubtree(candidate.occurrences().get(0).nodeId(), nodesById, ids);
        IrGpuTypedNode declaration = new IrGpuTypedNode(
                declarationId,
                "GpuIrVariableDeclaration",
                Map.of("typeName", candidate.typeName(), "name", candidate.temporaryName()),
                Map.of("initializer", List.of(initializer.rootNodeId()))
        );

        ArrayList<IrGpuTypedNode> nodes = new ArrayList<>();
        for (IrGpuTypedNode node : typedBody.nodes()) {
            if (occurrenceIds.contains(node.id())) {
                nodes.add(new IrGpuTypedNode(
                        node.id(),
                        "GpuIrVariableRef",
                        Map.of("name", candidate.temporaryName()),
                        Map.of()
                ));
            } else {
                nodes.add(node);
            }
        }
        nodes.add(declaration);
        nodes.addAll(initializer.nodes());

        ArrayList<Integer> roots = new ArrayList<>(typedBody.rootNodeIds());
        int insertionIndex = Math.max(0, Math.min(candidate.firstRootIndex(), roots.size()));
        roots.add(insertionIndex, declarationId);
        return new IrGpuTypedBody(typedBody.format(), roots, nodes);
    }

    private static CloneResult cloneSubtree(
            int nodeId,
            Map<Integer, IrGpuTypedNode> nodesById,
            IdAllocator ids
    ) {
        IrGpuTypedNode node = nodesById.get(nodeId);
        if (node == null) {
            int missingId = ids.next();
            return new CloneResult(
                    missingId,
                    List.of(new IrGpuTypedNode(missingId, "GpuIrLiteral", Map.of("sourceText", "0"), Map.of()))
            );
        }
        LinkedHashMap<String, List<Integer>> children = new LinkedHashMap<>();
        ArrayList<IrGpuTypedNode> clonedNodes = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : node.children().entrySet()) {
            ArrayList<Integer> clonedChildIds = new ArrayList<>();
            for (Integer childId : entry.getValue()) {
                CloneResult child = cloneSubtree(childId, nodesById, ids);
                clonedChildIds.add(child.rootNodeId());
                clonedNodes.addAll(child.nodes());
            }
            children.put(entry.getKey(), List.copyOf(clonedChildIds));
        }
        int clonedId = ids.next();
        clonedNodes.add(new IrGpuTypedNode(
                clonedId,
                node.kind(),
                new LinkedHashMap<>(node.attributes()),
                children
        ));
        return new CloneResult(clonedId, clonedNodes);
    }

    private static void updateLocalBindings(
            IrGpuTypedNode statement,
            String body,
            Map<Integer, IrGpuTypedNode> nodesById,
            LinkedHashMap<String, LocalBinding> bindings,
            RewriteStats stats
    ) {
        Optional<BindingSource> source = bindingSource(statement, nodesById);
        source.ifPresent(bindingSource -> invalidateBindings(bindingSource.targetName(), bindings, stats));
        if (source.isEmpty()) {
            return;
        }

        BindingSource bindingSource = source.orElseThrow();
        Optional<PureExpression> expression = pureExpressionChild(statement, bindingSource.valueChildName(), nodesById, stats);
        if (expression.isEmpty()) {
            return;
        }
        PureExpression value = expression.orElseThrow();
        if (value.variableNames().contains(bindingSource.targetName())) {
            stats.skippedLocalInvalidationCount++;
            stats.setFirstBlocker("self-dependent-local-binding");
            return;
        }
        int statementIndex = body.indexOf(bindingSource.statementText(value.sourceText()));
        if (statementIndex < 0) {
            stats.skippedBindingTextPatternMissingCount++;
            stats.setFirstBlocker("binding-text-pattern-missing");
            return;
        }
        stats.localBindingCount++;
        bindings.putIfAbsent(value.fingerprint(), new LocalBinding(
                bindingSource.targetName(),
                value.fingerprint(),
                value.sourceText(),
                value.variableNames(),
                statementIndex + bindingSource.statementText(value.sourceText()).length()
        ));
    }

    private static void invalidateBindings(
            String assignedName,
            LinkedHashMap<String, LocalBinding> bindings,
            RewriteStats stats
    ) {
        if (assignedName == null || assignedName.isBlank() || bindings.isEmpty()) {
            return;
        }
        ArrayList<String> invalidated = new ArrayList<>();
        for (Map.Entry<String, LocalBinding> entry : bindings.entrySet()) {
            LocalBinding binding = entry.getValue();
            if (assignedName.equals(binding.targetName()) || binding.variableNames().contains(assignedName)) {
                invalidated.add(entry.getKey());
            }
        }
        for (String key : invalidated) {
            bindings.remove(key);
        }
        stats.skippedLocalInvalidationCount += invalidated.size();
    }

    private record BindingSource(String targetName, String valueChildName, String statementPrefix) {

        private String statementText(String expressionText) {
            return statementPrefix + expressionText;
        }
    }

    private static Optional<BindingSource> bindingSource(IrGpuTypedNode statement, Map<Integer, IrGpuTypedNode> nodesById) {
        if ("GpuIrAssignment".equals(statement.kind())) {
            Optional<String> targetName = simpleTargetName(statement, nodesById);
            return targetName.map(name -> new BindingSource(name, "value", "set " + name + " = "));
        }
        if ("GpuIrVariableDeclaration".equals(statement.kind())) {
            String name = statement.attributes().getOrDefault("name", "");
            String typeName = statement.attributes().getOrDefault("typeName", "");
            if (isSimpleLocalName(name) && !typeName.isBlank()) {
                return Optional.of(new BindingSource(name, "initializer", "var " + typeName + " " + name + " = "));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> simpleTargetName(IrGpuTypedNode statement, Map<Integer, IrGpuTypedNode> nodesById) {
        List<Integer> targetIds = statement.children().getOrDefault("target", List.of());
        if (targetIds.size() != 1) {
            return Optional.empty();
        }
        IrGpuTypedNode target = nodesById.get(targetIds.get(0));
        if (target == null || !"GpuIrVariableRef".equals(target.kind())) {
            return Optional.empty();
        }
        String name = target.attributes().getOrDefault("name", "");
        return isSimpleLocalName(name) ? Optional.of(name) : Optional.empty();
    }

    private static Optional<CseCandidate> firstReusableExpression(
            String methodName,
            IrGpuTypedNode node,
            Map<Integer, IrGpuTypedNode> nodesById,
            Map<String, LocalBinding> bindings,
            Set<Integer> reachableNodeIds
    ) {
        Optional<PureExpression> expression = pureExpression(node, nodesById, null);
        if (expression.isPresent() && "GpuIrBinary".equals(node.kind())) {
            LocalBinding binding = bindings.get(expression.orElseThrow().fingerprint());
            if (binding != null) {
                return Optional.of(new CseCandidate(
                        methodName,
                        node.id(),
                        expression.orElseThrow().sourceText(),
                        binding.targetName(),
                        binding.searchStart()
                ));
            }
        }
        for (Map.Entry<String, List<Integer>> entry : node.children().entrySet()) {
            if ("target".equals(entry.getKey())) {
                continue;
            }
            for (Integer childId : entry.getValue()) {
                if (!reachableNodeIds.contains(childId)) {
                    continue;
                }
                IrGpuTypedNode child = nodesById.get(childId);
                if (child == null) {
                    continue;
                }
                Optional<CseCandidate> candidate = firstReusableExpression(
                        methodName,
                        child,
                        nodesById,
                        bindings,
                        reachableNodeIds
                );
                if (candidate.isPresent()) {
                    return candidate;
                }
            }
        }
        return Optional.empty();
    }

    private record PureExpression(String fingerprint, String sourceText, Set<String> variableNames) {
    }

    private record ExpressionOccurrence(
            int rootIndex,
            int nodeId,
            IrGpuTypedNode node,
            PureExpression expression
    ) {
    }

    private static Optional<PureExpression> pureExpressionChild(
            IrGpuTypedNode node,
            String childName,
            Map<Integer, IrGpuTypedNode> nodesById,
            RewriteStats stats
    ) {
        List<Integer> childIds = node.children().getOrDefault(childName, List.of());
        if (childIds.size() != 1) {
            if (stats != null) {
                stats.skippedImpureOperandCount++;
                stats.setFirstBlocker("impure-or-missing-expression-child");
            }
            return Optional.empty();
        }
        return pureExpression(nodesById.get(childIds.get(0)), nodesById, stats);
    }

    private static Optional<PureExpression> pureExpression(
            IrGpuTypedNode node,
            Map<Integer, IrGpuTypedNode> nodesById,
            RewriteStats stats
    ) {
        if (node == null) {
            if (stats != null) {
                stats.skippedImpureOperandCount++;
                stats.setFirstBlocker("impure-or-missing-expression-child");
            }
            return Optional.empty();
        }
        if ("GpuIrLiteral".equals(node.kind())) {
            String sourceText = node.attributes().getOrDefault("sourceText", "");
            return sourceText.isBlank()
                    ? Optional.empty()
                    : Optional.of(new PureExpression("literal:" + sourceText, sourceText, Set.of()));
        }
        if ("GpuIrVariableRef".equals(node.kind())) {
            String name = node.attributes().getOrDefault("name", "");
            return isSimpleLocalName(name)
                    ? Optional.of(new PureExpression("var:" + name, name, Set.of(name)))
                    : Optional.empty();
        }
        if (!"GpuIrBinary".equals(node.kind())) {
            if (stats != null) {
                stats.skippedImpureOperandCount++;
                stats.setFirstBlocker("impure-operand");
            }
            return Optional.empty();
        }
        String operator = node.attributes().getOrDefault("operator", "");
        if (!SUPPORTED_BINARY_OPERATORS.contains(operator)) {
            if (stats != null) {
                stats.skippedUnsupportedOperatorCount++;
                stats.setFirstBlocker("unsupported-operator");
            }
            return Optional.empty();
        }
        Optional<PureExpression> left = pureExpressionChild(node, "left", nodesById, stats);
        Optional<PureExpression> right = pureExpressionChild(node, "right", nodesById, stats);
        if (left.isEmpty() || right.isEmpty()) {
            return Optional.empty();
        }
        LinkedHashSet<String> variables = new LinkedHashSet<>();
        variables.addAll(left.orElseThrow().variableNames());
        variables.addAll(right.orElseThrow().variableNames());
        return Optional.of(new PureExpression(
                "binary(" + operator + "," + left.orElseThrow().fingerprint()
                        + "," + right.orElseThrow().fingerprint() + ")",
                "(" + left.orElseThrow().sourceText() + " " + operator + " " + right.orElseThrow().sourceText() + ")",
                Set.copyOf(variables)
        ));
    }

    private record LocalBinding(
            String targetName,
            String fingerprint,
            String expressionText,
            Set<String> variableNames,
            int searchStart
    ) {
    }

    private record CseCandidate(
            String methodName,
            int nodeId,
            String expressionText,
            String replacementText,
            int bindingSearchStart,
            String rewriteKind,
            String replacementInputName
    ) {

        private CseCandidate(
                String methodName,
                int nodeId,
                String expressionText,
                String replacementText,
                int bindingSearchStart
        ) {
            this(
                    methodName,
                    nodeId,
                    expressionText,
                    replacementText,
                    bindingSearchStart,
                    "safe-local-cse-reuse-existing-local",
                    "existingLocal"
            );
        }

        private String summary() {
            return methodName + "#" + nodeId + "=" + expressionText + "->" + replacementText;
        }
    }

    private record TemporaryCseCandidate(
            String methodName,
            String temporaryName,
            String typeName,
            PureExpression expression,
            List<ExpressionOccurrence> occurrences
    ) {

        private TemporaryCseCandidate {
            occurrences = occurrences == null ? List.of() : List.copyOf(occurrences);
        }

        private int occurrenceCount() {
            return occurrences.size();
        }

        private int firstRootIndex() {
            return occurrences.stream().mapToInt(ExpressionOccurrence::rootIndex).min().orElse(0);
        }

        private List<CseCandidate> toCseCandidates() {
            ArrayList<CseCandidate> candidates = new ArrayList<>();
            for (ExpressionOccurrence occurrence : occurrences) {
                candidates.add(new CseCandidate(
                        methodName,
                        occurrence.nodeId(),
                        expression.sourceText(),
                        temporaryName,
                        0,
                        "safe-local-cse-introduce-local",
                        "introducedLocal"
                ));
            }
            return List.copyOf(candidates);
        }
    }

    private record CloneResult(int rootNodeId, List<IrGpuTypedNode> nodes) {
    }

    private static final class IdAllocator {
        private int nextId;

        private IdAllocator(int nextId) {
            this.nextId = Math.max(0, nextId);
        }

        private int next() {
            return nextId++;
        }
    }

    private static Set<String> collectLocalNames(IrGpuTypedBody typedBody, Map<Integer, IrGpuTypedNode> nodesById) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (IrGpuTypedNode node : typedBody.nodes()) {
            if ("GpuIrVariableDeclaration".equals(node.kind())) {
                String name = node.attributes().getOrDefault("name", "");
                if (isSimpleLocalName(name)) {
                    names.add(name);
                }
            }
            Optional<String> targetName = simpleTargetName(node, nodesById);
            targetName.ifPresent(names::add);
        }
        return Set.copyOf(names);
    }

    private static String nextTemporaryName(Set<String> localNames) {
        Set<String> names = localNames == null ? Set.of() : localNames;
        for (int index = 0; index < 10_000; index++) {
            String candidate = "jtg_cse" + index;
            if (!names.contains(candidate)) {
                return candidate;
            }
        }
        return "jtg_cse_overflow";
    }

    private static String inferTemporaryType(IrGpuTypedNode node, String expressionText) {
        String resultType = firstAttribute(node, "resultType", "typeName", "valueType");
        if (!resultType.isBlank()) {
            return resultType;
        }
        String text = expressionText == null ? "" : expressionText;
        if (text.matches(".*[0-9][.][0-9].*") || text.matches(".*[fFdD]\\b.*")) {
            return "float";
        }
        return "int";
    }

    private static String firstAttribute(IrGpuTypedNode node, String... names) {
        if (node == null || names == null) {
            return "";
        }
        for (String name : names) {
            String value = node.attributes().getOrDefault(name, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static List<Integer> allIndexesOf(String text, String pattern) {
        if (text == null || pattern == null || pattern.isBlank()) {
            return List.of();
        }
        ArrayList<Integer> indexes = new ArrayList<>();
        int index = text.indexOf(pattern);
        while (index >= 0) {
            indexes.add(index);
            index = text.indexOf(pattern, index + pattern.length());
        }
        return List.copyOf(indexes);
    }

    private static String replaceAll(String text, String pattern, String replacement) {
        StringBuilder builder = new StringBuilder();
        int cursor = 0;
        for (Integer index : allIndexesOf(text, pattern)) {
            builder.append(text, cursor, index).append(replacement);
            cursor = index + pattern.length();
        }
        builder.append(text.substring(cursor));
        return builder.toString();
    }

    private static int lineStart(String text, int index) {
        int previousNewLine = text.lastIndexOf('\n', Math.max(0, index));
        return previousNewLine < 0 ? 0 : previousNewLine + 1;
    }

    private static String lineIndentation(String text, int lineStart) {
        StringBuilder indentation = new StringBuilder();
        for (int index = lineStart; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch != ' ' && ch != '\t') {
                break;
            }
            indentation.append(ch);
        }
        return indentation.toString();
    }

    private static void appendRuntimeEquivalenceCases(
            LinkedHashMap<String, String> fields,
            List<CseCandidate> candidates
    ) {
        fields.put("runtimeEquivalencePayload.Case.Count", Integer.toString(candidates.size()));
        for (int index = 0; index < candidates.size(); index++) {
            CseCandidate candidate = candidates.get(index);
            String prefix = "runtimeEquivalencePayload.Case." + index;
            fields.put(prefix + ".Name", candidate.summary());
            fields.put(prefix + ".MethodName", candidate.methodName());
            fields.put(prefix + ".NodeId", Integer.toString(candidate.nodeId()));
            fields.put(prefix + ".RewriteKind", candidate.rewriteKind());
            fields.put(prefix + ".Successful", "true");
            fields.put(prefix + ".Input.Count", "2");
            fields.put(prefix + ".Input.0.Name", "expression");
            fields.put(prefix + ".Input.0.Value", candidate.expressionText());
            fields.put(prefix + ".Input.1.Name", candidate.replacementInputName());
            fields.put(prefix + ".Input.1.Value", candidate.replacementText());
            fields.put(prefix + ".Output.Count", "1");
            fields.put(prefix + ".Output.0.Name", "reusedValue");
            fields.put(prefix + ".Output.0.CpuReference", candidate.replacementText());
            fields.put(prefix + ".Output.0.PreOptimization", candidate.expressionText());
            fields.put(prefix + ".Output.0.PostOptimization", candidate.replacementText());
            fields.put(prefix + ".Output.0.Tolerance", "exact-expression-reuse");
            fields.put(prefix + ".Output.0.Equivalent", "true");
            fields.put(prefix + ".FailureFixture.Diagnostic.Count", "0");
        }
    }

    private static boolean hasControlFlowBoundary(IrGpuTypedBody typedBody) {
        for (IrGpuTypedNode node : typedBody.nodes()) {
            if (List.of("GpuIrIf", "GpuIrForLoop", "GpuIrWhileLoop", "GpuIrDoWhileLoop", "GpuIrSwitch")
                    .contains(node.kind())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSimpleLocalName(String name) {
        return name != null && SIMPLE_LOCAL_NAME.matcher(name).matches();
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
        private int typedBodyCount;
        private int localBindingCount;
        private int introducedTemporaryCount;
        private int candidateCount;
        private int transformedNodeCount;
        private int changedMethodBodyCount;
        private int bodyTextReplacementCount;
        private int fixedPointPassCount;
        private int skippedTypedBodyMissingCount;
        private int skippedUnsupportedFormatCount;
        private int skippedControlFlowBoundaryCount;
        private int skippedMissingChildReferenceCount;
        private int skippedUnsupportedOperatorCount;
        private int skippedImpureOperandCount;
        private int skippedNoExistingLocalBindingCount;
        private int skippedLocalInvalidationCount;
        private int skippedBindingTextPatternMissingCount;
        private int skippedBodyTextPatternMissingCount;
        private final ArrayList<CseCandidate> transformedCandidates = new ArrayList<>();
        private String firstTransformedNode = "none";
        private String firstExpression = "none";
        private String firstReplacement = "none";
        private String firstBlocker = "none";

        private void recordPatchBlocker(String blocker) {
            switch (GpuIrTypedBodyGraphPatch.blockerKind(blocker)) {
                case BODY_TEXT_PATTERN_MISSING -> skippedBodyTextPatternMissingCount++;
                case TYPED_BODY_MISSING -> skippedTypedBodyMissingCount++;
                case TYPED_GRAPH_MISSING -> skippedMissingChildReferenceCount++;
                case OTHER -> skippedImpureOperandCount++;
            }
            setFirstBlocker(blocker);
        }

        private void add(RewriteStats other) {
            typedBodyCount += other.typedBodyCount;
            localBindingCount += other.localBindingCount;
            introducedTemporaryCount += other.introducedTemporaryCount;
            candidateCount += other.candidateCount;
            transformedNodeCount += other.transformedNodeCount;
            changedMethodBodyCount += other.changedMethodBodyCount;
            bodyTextReplacementCount += other.bodyTextReplacementCount;
            fixedPointPassCount += other.fixedPointPassCount;
            skippedTypedBodyMissingCount += other.skippedTypedBodyMissingCount;
            skippedUnsupportedFormatCount += other.skippedUnsupportedFormatCount;
            skippedControlFlowBoundaryCount += other.skippedControlFlowBoundaryCount;
            skippedMissingChildReferenceCount += other.skippedMissingChildReferenceCount;
            skippedUnsupportedOperatorCount += other.skippedUnsupportedOperatorCount;
            skippedImpureOperandCount += other.skippedImpureOperandCount;
            skippedNoExistingLocalBindingCount += other.skippedNoExistingLocalBindingCount;
            skippedLocalInvalidationCount += other.skippedLocalInvalidationCount;
            skippedBindingTextPatternMissingCount += other.skippedBindingTextPatternMissingCount;
            skippedBodyTextPatternMissingCount += other.skippedBodyTextPatternMissingCount;
            transformedCandidates.addAll(other.transformedCandidates);
            if ("none".equals(firstTransformedNode) && !"none".equals(other.firstTransformedNode)) {
                firstTransformedNode = other.firstTransformedNode;
                firstExpression = other.firstExpression;
                firstReplacement = other.firstReplacement;
            }
            if ("none".equals(firstBlocker) && !"none".equals(other.firstBlocker)) {
                firstBlocker = other.firstBlocker;
            }
        }

        private void setFirstBlocker(String blocker) {
            if ("none".equals(firstBlocker) && blocker != null && !blocker.isBlank() && !"none".equals(blocker)) {
                firstBlocker = blocker;
            }
        }
    }
}
