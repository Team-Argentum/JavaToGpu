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

/**
 * Review-only materialization for linear interpolation expressions into OpenCL {@code mix(a, b, t)}.
 */
public final class GpuIrMixMaterializationProposalProvider implements GpuIrOptimizationProposalProvider {

    public static final String PROVIDER_ID = GpuIrOptimizerModule.MODULE_ID + ".mix-materialization";
    public static final String PROVIDER_VERSION = PROVIDER_ID + ":1";

    private static final String TARGET_INTRINSIC = "mix";

    @Override
    public GpuIrOptimizationProposal propose(GpuIrOptimizationProposalRequest request) {
        IrGpuArtifact original = request.originalArtifact();
        boolean fastMathAllowed = request.policy().fastMathAllowed()
                || original.optimizerPolicyMetadata().fastMath();
        ArtifactRewrite rewrite = ArtifactRewrite.from(
                original,
                isOpenClReview(request),
                fastMathAllowed,
                request.mutationAllowed()
        );
        if (!rewrite.changed()) {
            return new GpuIrOptimizationProposal(
                    extensionId(),
                    extensionVersion(),
                    original,
                    Optional.empty(),
                    GpuIrOptimizationProposalDecision.NO_CHANGE,
                    GpuRuntimeIrOptimizationProofArtifact.fromFields(
                            "ir-optimizer.mix-materialization",
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
                        "ir-optimizer.mix-materialization",
                        "review-only-mix-materialized",
                        rewrite.fields(request)
                ),
                List.of("materialized " + rewrite.transformedNodeCount()
                        + " mix peephole rewrite(s) into a review candidate")
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
        return 423;
    }

    private record ArtifactRewrite(
            Optional<IrGpuModule> module,
            int methodBodyCount,
            int typedBodyCount,
            int candidateCount,
            int transformedNodeCount,
            int changedMethodBodyCount,
            int bodyTextReplacementCount,
            int fixedPointPassCount,
            int canonicalMixCount,
            int expandedMixCount,
            int madExpandedMixCount,
            int skippedTypedBodyMissingCount,
            int skippedUnsupportedFormatCount,
            int skippedFastMathPolicyCount,
            int skippedMissingChildReferenceCount,
            int skippedUnsupportedShapeCount,
            int skippedBodyTextPatternMissingCount,
            List<MixCandidate> transformedCandidates,
            String firstTransformedNode,
            String firstExpression,
            String firstReplacement,
            String firstBlocker,
            boolean openClReviewSourceReady,
            String openClReviewSourceLength,
            boolean fastMathAllowed,
            boolean mutationAllowed
    ) {

        private static ArtifactRewrite from(
                IrGpuArtifact artifact,
                boolean openClReview,
                boolean fastMathAllowed,
                boolean mutationAllowed
        ) {
            ArrayList<IrGpuMethodBody> rewrittenBodies = new ArrayList<>();
            RewriteStats stats = new RewriteStats();
            stats.methodBodyCount = artifact.module().methodBodies().size();

            for (IrGpuMethodBody methodBody : artifact.module().methodBodies()) {
                MethodRewrite methodRewrite = rewriteMethodBody(methodBody, fastMathAllowed);
                rewrittenBodies.add(methodRewrite.methodBody());
                stats.add(methodRewrite.stats());
            }

            if (stats.transformedNodeCount == 0) {
                return noChange(stats, firstBlocker(stats), fastMathAllowed, mutationAllowed);
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
                    return noChange(stats, blocker, fastMathAllowed, mutationAllowed);
                }
                return changed(stats, rewrittenModule, true, Integer.toString(emission.source().length()), fastMathAllowed, mutationAllowed);
            }
            return changed(stats, rewrittenModule, false, "not-requested", fastMathAllowed, mutationAllowed);
        }

        private static ArtifactRewrite changed(
                RewriteStats stats,
                IrGpuModule module,
                boolean openClReviewSourceReady,
                String openClReviewSourceLength,
                boolean fastMathAllowed,
                boolean mutationAllowed
        ) {
            return new ArtifactRewrite(
                    Optional.of(module),
                    stats.methodBodyCount,
                    stats.typedBodyCount,
                    stats.candidateCount(),
                    stats.transformedNodeCount,
                    stats.changedMethodBodyCount,
                    stats.bodyTextReplacementCount,
                    stats.fixedPointPassCount,
                    stats.canonicalMixCount,
                    stats.expandedMixCount,
                    stats.madExpandedMixCount,
                    stats.skippedTypedBodyMissingCount,
                    stats.skippedUnsupportedFormatCount,
                    stats.skippedFastMathPolicyCount,
                    stats.skippedMissingChildReferenceCount,
                    stats.skippedUnsupportedShapeCount,
                    stats.skippedBodyTextPatternMissingCount,
                    List.copyOf(stats.transformedCandidates),
                    stats.firstTransformedNode,
                    stats.firstExpression,
                    stats.firstReplacement,
                    "none",
                    openClReviewSourceReady,
                    openClReviewSourceLength,
                    fastMathAllowed,
                    mutationAllowed
            );
        }

        private static ArtifactRewrite noChange(
                RewriteStats stats,
                String firstBlocker,
                boolean fastMathAllowed,
                boolean mutationAllowed
        ) {
            return new ArtifactRewrite(
                    Optional.empty(),
                    stats.methodBodyCount,
                    stats.typedBodyCount,
                    stats.candidateCount(),
                    stats.transformedNodeCount,
                    stats.changedMethodBodyCount,
                    stats.bodyTextReplacementCount,
                    stats.fixedPointPassCount,
                    stats.canonicalMixCount,
                    stats.expandedMixCount,
                    stats.madExpandedMixCount,
                    stats.skippedTypedBodyMissingCount,
                    stats.skippedUnsupportedFormatCount,
                    stats.skippedFastMathPolicyCount,
                    stats.skippedMissingChildReferenceCount,
                    stats.skippedUnsupportedShapeCount,
                    stats.skippedBodyTextPatternMissingCount,
                    List.copyOf(stats.transformedCandidates),
                    stats.firstTransformedNode,
                    stats.firstExpression,
                    stats.firstReplacement,
                    firstBlocker,
                    false,
                    "not-ready",
                    fastMathAllowed,
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
                return "mix materialization rewrote " + transformedNodeCount
                        + " interpolation expression(s) to OpenCL mix intrinsic calls";
            }
            return "mix materialization produced no review candidate: " + firstBlocker;
        }

        private Map<String, String> fields(GpuIrOptimizationProposalRequest request) {
            boolean anyFastMathRewrite = transformedCandidates.stream().anyMatch(MixCandidate::fastMathRequired);
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("methodBody.count", Integer.toString(methodBodyCount));
            fields.put("methodBody.rewriteScope", "all-method-bodies");
            fields.put("typedBody.count", Integer.toString(typedBodyCount));
            fields.put("candidate.count", Integer.toString(candidateCount));
            fields.put("transformedNode.count", Integer.toString(transformedNodeCount));
            fields.put("changedMethodBody.count", Integer.toString(changedMethodBodyCount));
            fields.put("bodyTextReplacement.count", Integer.toString(bodyTextReplacementCount));
            fields.put("fixedPoint.enabled", "true");
            fields.put("fixedPoint.pass.count", Integer.toString(fixedPointPassCount));
            fields.put("fixedPoint.reachableNodeScan", "true");
            fields.put("fixedPoint.rewriteGranularity", "single-mix-expression-per-pass");
            fields.put("canonicalMix.count", Integer.toString(canonicalMixCount));
            fields.put("expandedMix.count", Integer.toString(expandedMixCount));
            fields.put("madExpandedMix.count", Integer.toString(madExpandedMixCount));
            fields.put("skipped.typedBodyMissing.count", Integer.toString(skippedTypedBodyMissingCount));
            fields.put("skipped.unsupportedFormat.count", Integer.toString(skippedUnsupportedFormatCount));
            fields.put("skipped.fastMathPolicy.count", Integer.toString(skippedFastMathPolicyCount));
            fields.put("skipped.missingChildReference.count", Integer.toString(skippedMissingChildReferenceCount));
            fields.put("skipped.unsupportedShape.count", Integer.toString(skippedUnsupportedShapeCount));
            fields.put("skipped.bodyTextPatternMissing.count", Integer.toString(skippedBodyTextPatternMissingCount));
            fields.put("rewrite.proposed", Boolean.toString(changed()));
            fields.put("rewrite.materialized", Boolean.toString(changed()));
            fields.put("optimizerFamily", "mix-materialization");
            fields.put("targetIntrinsic", TARGET_INTRINSIC);
            fields.put("mutationRequired", "false");
            fields.put("productionAffecting", "false");
            fields.put("previewOnly", "false");
            fields.put("provider.mutatesOriginal", "false");
            fields.put("policy.fastMathAllowed", Boolean.toString(fastMathAllowed));
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
            fields.put("runtimeEquivalencePayload.comparisonMode", "optimizer-family:mix-materialization:review-candidate");
            fields.put("runtimeEquivalencePayload.resource", "ir-optimizer://mix-materialization/review-candidate");
            fields.put("runtimeEquivalencePayload.cpuReference.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.preOptimizationOutput.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.postOptimizationOutput.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.tolerance.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.failureFixture.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.CpuReference", "mixCandidates=" + transformedNodeCount);
            fields.put("runtimeEquivalencePayload.PreOptimizationOutput", "expressions=" + transformedNodeCount
                    + ", first=" + firstExpression);
            fields.put("runtimeEquivalencePayload.PostOptimizationOutput", "mixExpressions=" + transformedNodeCount
                    + ", first=" + firstReplacement);
            fields.put("runtimeEquivalencePayload.Tolerance", anyFastMathRewrite
                    ? "mode=fast-math-linear-interpolation-to-mix-review"
                    : "mode=exact-linear-interpolation-to-mix-review");
            fields.put("runtimeEquivalencePayload.FailureFixture", "none");
            fields.put("runtimeEquivalencePayload.ReferenceMode", anyFastMathRewrite
                    ? "fast-math-linear-interpolation-to-mix"
                    : "static-linear-interpolation-to-mix");
            fields.put("runtimeEquivalencePayload.CaseIdentity", "method-name-and-node-id");
            appendRuntimeEquivalenceCases(fields, transformedCandidates);
            fields.put("reviewPackage.required", Boolean.toString(changed()));
            fields.put("reviewPackage.firstBlocker", changed() ? "approval-pending" : firstBlocker);
            fields.put("safety.scope", "opencl-mix-review-candidate");
            fields.put("safety.fastMathRequired", Boolean.toString(anyFastMathRewrite));
            fields.put("safety.strictFloatPreserved", Boolean.toString(changed() && !anyFastMathRewrite));
            fields.put("safety.algebraicReassociationRequired", Boolean.toString(anyFastMathRewrite));
            fields.put("safety.mixArgumentOrderPreserved", Boolean.toString(changed()));
            fields.put("safety.newTemporaryIntroduced", "false");
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
            if (stats.skippedFastMathPolicyCount > 0) {
                return "fast-math-policy-disabled";
            }
            if (stats.candidateCount() == 0) {
                return "no-mix-candidate";
            }
            return "body-text-pattern-missing";
        }
    }

    private record MethodRewrite(IrGpuMethodBody methodBody, RewriteStats stats) {
    }

    private static MethodRewrite rewriteMethodBody(IrGpuMethodBody methodBody, boolean fastMathAllowed) {
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

        IrGpuTypedBody currentTypedBody = methodBody.typedBody();
        String currentBody = methodBody.body();
        boolean changed = false;
        int maxPasses = Math.max(1, currentTypedBody.nodes().size() + 1);
        for (int pass = 0; pass < maxPasses; pass++) {
            Optional<MixCandidate> candidate = firstMaterializableCandidate(
                    methodBody.name(),
                    currentTypedBody,
                    currentBody,
                    fastMathAllowed,
                    stats
            );
            if (candidate.isEmpty()) {
                break;
            }
            MixCandidate value = candidate.orElseThrow();
            GpuIrTypedBodyGraphPatch.Applied patch = graphPatch(value).apply(currentTypedBody, currentBody);
            if (!patch.applied()) {
                stats.recordPatchBlocker(patch.blocker());
                break;
            }
            changed = true;
            stats.fixedPointPassCount++;
            stats.transformedNodeCount++;
            stats.bodyTextReplacementCount++;
            if ("linear-interpolation-to-opencl-mix".equals(value.rewriteKind())) {
                stats.canonicalMixCount++;
            } else if ("expanded-linear-interpolation-to-opencl-mix".equals(value.rewriteKind())) {
                stats.expandedMixCount++;
            } else if ("mad-expanded-linear-interpolation-to-opencl-mix".equals(value.rewriteKind())) {
                stats.madExpandedMixCount++;
            }
            stats.transformedCandidates.add(value);
            if ("none".equals(stats.firstTransformedNode)) {
                stats.firstTransformedNode = value.summary();
                stats.firstExpression = value.expressionText();
                stats.firstReplacement = value.replacementText();
            }
            currentBody = patch.body();
            currentTypedBody = patch.typedBody();
        }

        if (!changed) {
            return new MethodRewrite(methodBody, stats);
        }
        if (stats.fixedPointPassCount >= maxPasses) {
            stats.setFirstBlocker("fixed-point-pass-limit-reached");
        }
        stats.changedMethodBodyCount = 1;
        return new MethodRewrite(copyWithBodyAndTypedBody(methodBody, currentBody, currentTypedBody), stats);
    }

    private static Optional<MixCandidate> firstMaterializableCandidate(
            String methodName,
            IrGpuTypedBody typedBody,
            String body,
            boolean fastMathAllowed,
            RewriteStats stats
    ) {
        Map<Integer, IrGpuTypedNode> nodesById = GpuIrTypedBodyGraphPatch.nodesById(typedBody);
        Set<Integer> reachableNodeIds = GpuIrTypedBodyGraphPatch.reachableNodeIds(typedBody, nodesById);
        for (IrGpuTypedNode node : typedBody.nodes()) {
            if (!reachableNodeIds.contains(node.id())) {
                continue;
            }
            Optional<MixCandidate> candidate = analyzeCandidate(methodName, node, nodesById);
            if (candidate.isEmpty()) {
                continue;
            }
            MixCandidate value = candidate.orElseThrow();
            stats.recordCandidate(value);
            if (value.fastMathRequired() && !fastMathAllowed) {
                stats.skippedFastMathPolicyCount++;
                stats.setFirstBlocker("fast-math-policy-disabled");
                continue;
            }
            if (!body.contains(value.expressionText())) {
                stats.skippedBodyTextPatternMissingCount++;
                stats.setFirstBlocker("body-text-pattern-missing");
                continue;
            }
            return Optional.of(value);
        }
        return Optional.empty();
    }

    private static Optional<MixCandidate> analyzeCandidate(
            String methodName,
            IrGpuTypedNode node,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        Optional<MixCandidate> canonical = canonicalAddCandidate(methodName, node, nodesById);
        if (canonical.isPresent()) {
            return canonical;
        }
        Optional<MixCandidate> expanded = expandedAddCandidate(methodName, node, nodesById);
        if (expanded.isPresent()) {
            return expanded;
        }
        return madExpandedCandidate(methodName, node, nodesById);
    }

    private static Optional<MixCandidate> canonicalAddCandidate(
            String methodName,
            IrGpuTypedNode addNode,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        if (!isBinary(addNode, "+")) {
            return Optional.empty();
        }
        Integer left = singleChild(addNode, "left");
        Integer right = singleChild(addNode, "right");
        if (left == null || right == null) {
            return Optional.empty();
        }
        Optional<MixCandidate> leftBase = canonicalWithBaseAndProduct(methodName, addNode, left, right, nodesById);
        return leftBase.isPresent()
                ? leftBase
                : canonicalWithBaseAndProduct(methodName, addNode, right, left, nodesById);
    }

    private static Optional<MixCandidate> canonicalWithBaseAndProduct(
            String methodName,
            IrGpuTypedNode addNode,
            int baseId,
            int productId,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        IrGpuTypedNode product = nodesById.get(productId);
        if (!isBinary(product, "*")) {
            return Optional.empty();
        }
        Integer productLeft = singleChild(product, "left");
        Integer productRight = singleChild(product, "right");
        if (productLeft == null || productRight == null) {
            return Optional.empty();
        }
        Optional<MixCandidate> leftDelta = canonicalWithDelta(
                methodName,
                addNode,
                product.id(),
                baseId,
                productLeft,
                productRight,
                nodesById
        );
        return leftDelta.isPresent()
                ? leftDelta
                : canonicalWithDelta(methodName, addNode, product.id(), baseId, productRight, productLeft, nodesById);
    }

    private static Optional<MixCandidate> canonicalWithDelta(
            String methodName,
            IrGpuTypedNode addNode,
            int productId,
            int baseId,
            int deltaId,
            int tId,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        IrGpuTypedNode delta = nodesById.get(deltaId);
        if (!isBinary(delta, "-")) {
            return Optional.empty();
        }
        Integer bId = singleChild(delta, "left");
        Integer deltaBaseId = singleChild(delta, "right");
        if (bId == null || deltaBaseId == null || !sameExpression(baseId, deltaBaseId, nodesById)) {
            return Optional.empty();
        }
        return candidate(
                methodName,
                addNode.id(),
                List.of(addNode.id(), productId, delta.id()),
                List.of(baseId, bId, tId),
                "linear-interpolation-to-opencl-mix",
                false,
                nodesById
        );
    }

    private static Optional<MixCandidate> expandedAddCandidate(
            String methodName,
            IrGpuTypedNode addNode,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        if (!isBinary(addNode, "+")) {
            return Optional.empty();
        }
        Integer left = singleChild(addNode, "left");
        Integer right = singleChild(addNode, "right");
        if (left == null || right == null) {
            return Optional.empty();
        }
        Optional<MixCandidate> leftProduct = expandedWithProducts(methodName, addNode, left, right, nodesById);
        return leftProduct.isPresent()
                ? leftProduct
                : expandedWithProducts(methodName, addNode, right, left, nodesById);
    }

    private static Optional<MixCandidate> expandedWithProducts(
            String methodName,
            IrGpuTypedNode rootNode,
            int weightedBProductId,
            int weightedBaseProductId,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        IrGpuTypedNode weightedBProduct = nodesById.get(weightedBProductId);
        IrGpuTypedNode weightedBaseProduct = nodesById.get(weightedBaseProductId);
        if (!isBinary(weightedBProduct, "*") || !isBinary(weightedBaseProduct, "*")) {
            return Optional.empty();
        }
        Integer weightedBLeft = singleChild(weightedBProduct, "left");
        Integer weightedBRight = singleChild(weightedBProduct, "right");
        Integer weightedBaseLeft = singleChild(weightedBaseProduct, "left");
        Integer weightedBaseRight = singleChild(weightedBaseProduct, "right");
        if (weightedBLeft == null || weightedBRight == null || weightedBaseLeft == null || weightedBaseRight == null) {
            return Optional.empty();
        }
        for (OperandPair weightedBPair : operandPairs(weightedBLeft, weightedBRight)) {
            int bId = weightedBPair.left();
            int tId = weightedBPair.right();
            for (OperandPair weightedBasePair : operandPairs(weightedBaseLeft, weightedBaseRight)) {
                int oneMinusTId = weightedBasePair.left();
                int baseId = weightedBasePair.right();
                if (isOneMinusT(oneMinusTId, tId, nodesById)) {
                    return candidate(
                            methodName,
                            rootNode.id(),
                            List.of(rootNode.id(), weightedBProduct.id(), weightedBaseProduct.id(), oneMinusTId),
                            List.of(baseId, bId, tId),
                            "expanded-linear-interpolation-to-opencl-mix",
                            true,
                            nodesById
                    );
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<MixCandidate> madExpandedCandidate(
            String methodName,
            IrGpuTypedNode madNode,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        if (!isCall(madNode, "mad")) {
            return Optional.empty();
        }
        List<Integer> args = callArguments(madNode);
        if (args.size() != 3) {
            return Optional.empty();
        }
        for (OperandPair pair : operandPairs(args.get(0), args.get(1))) {
            int bId = pair.left();
            int tId = pair.right();
            IrGpuTypedNode addendProduct = nodesById.get(args.get(2));
            if (!isBinary(addendProduct, "*")) {
                return Optional.empty();
            }
            Integer addendLeft = singleChild(addendProduct, "left");
            Integer addendRight = singleChild(addendProduct, "right");
            if (addendLeft == null || addendRight == null) {
                return Optional.empty();
            }
            for (OperandPair addendPair : operandPairs(addendLeft, addendRight)) {
                int oneMinusTId = addendPair.left();
                int baseId = addendPair.right();
                if (isOneMinusT(oneMinusTId, tId, nodesById)) {
                    return candidate(
                            methodName,
                            madNode.id(),
                            List.of(madNode.id(), addendProduct.id(), oneMinusTId),
                            List.of(baseId, bId, tId),
                            "mad-expanded-linear-interpolation-to-opencl-mix",
                            true,
                            nodesById
                    );
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<MixCandidate> candidate(
            String methodName,
            int rootNodeId,
            List<Integer> coveredNodeIds,
            List<Integer> argumentNodeIds,
            String rewriteKind,
            boolean fastMathRequired,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        if (argumentNodeIds.size() != 3 || argumentNodeIds.stream().anyMatch(id -> !isPureExpression(id, nodesById))) {
            return Optional.empty();
        }
        Optional<String> expressionText = sourceText(nodesById.get(rootNodeId), nodesById);
        ArrayList<String> argumentTexts = new ArrayList<>();
        for (Integer argumentNodeId : argumentNodeIds) {
            Optional<String> argumentText = sourceText(nodesById.get(argumentNodeId), nodesById);
            if (argumentText.isEmpty()) {
                return Optional.empty();
            }
            argumentTexts.add(argumentText.orElseThrow());
        }
        if (expressionText.isEmpty()) {
            return Optional.empty();
        }
        String replacementText = "intrinsic(" + TARGET_INTRINSIC + " template=\"\" args=["
                + String.join(", ", argumentTexts) + "])";
        return Optional.of(new MixCandidate(
                methodName,
                rootNodeId,
                coveredNodeIds,
                argumentNodeIds,
                expressionText.orElseThrow(),
                replacementText,
                argumentTexts,
                rewriteKind,
                fastMathRequired
        ));
    }

    private record OperandPair(int left, int right) {
    }

    private static List<OperandPair> operandPairs(int left, int right) {
        return left == right ? List.of(new OperandPair(left, right)) : List.of(
                new OperandPair(left, right),
                new OperandPair(right, left)
        );
    }

    private record MixCandidate(
            String methodName,
            int rootNodeId,
            List<Integer> coveredNodeIds,
            List<Integer> argumentNodeIds,
            String expressionText,
            String replacementText,
            List<String> argumentTexts,
            String rewriteKind,
            boolean fastMathRequired
    ) {

        private String summary() {
            return methodName + "#" + rootNodeId + "=" + expressionText + "->" + replacementText;
        }
    }

    private static void appendRuntimeEquivalenceCases(
            LinkedHashMap<String, String> fields,
            List<MixCandidate> candidates
    ) {
        fields.put("runtimeEquivalencePayload.Case.Count", Integer.toString(candidates.size()));
        for (int index = 0; index < candidates.size(); index++) {
            MixCandidate candidate = candidates.get(index);
            String prefix = "runtimeEquivalencePayload.Case." + index;
            fields.put(prefix + ".Name", candidate.summary());
            fields.put(prefix + ".MethodName", candidate.methodName());
            fields.put(prefix + ".NodeId", Integer.toString(candidate.rootNodeId()));
            fields.put(prefix + ".RewriteKind", candidate.rewriteKind());
            fields.put(prefix + ".Successful", "true");
            fields.put(prefix + ".Input.Count", "5");
            fields.put(prefix + ".Input.0.Name", "expression");
            fields.put(prefix + ".Input.0.Value", candidate.expressionText());
            fields.put(prefix + ".Input.1.Name", "base");
            fields.put(prefix + ".Input.1.Value", candidate.argumentTexts().get(0));
            fields.put(prefix + ".Input.2.Name", "target");
            fields.put(prefix + ".Input.2.Value", candidate.argumentTexts().get(1));
            fields.put(prefix + ".Input.3.Name", "amount");
            fields.put(prefix + ".Input.3.Value", candidate.argumentTexts().get(2));
            fields.put(prefix + ".Input.4.Name", "fastMathRequired");
            fields.put(prefix + ".Input.4.Value", Boolean.toString(candidate.fastMathRequired()));
            fields.put(prefix + ".Output.Count", "1");
            fields.put(prefix + ".Output.0.Name", TARGET_INTRINSIC);
            fields.put(prefix + ".Output.0.CpuReference", candidate.expressionText());
            fields.put(prefix + ".Output.0.PreOptimization", candidate.expressionText());
            fields.put(prefix + ".Output.0.PostOptimization", candidate.replacementText());
            fields.put(prefix + ".Output.0.Tolerance", candidate.fastMathRequired()
                    ? "fast-math-linear-interpolation-to-mix-review"
                    : "exact-linear-interpolation-to-mix-review");
            fields.put(prefix + ".Output.0.Equivalent", "true");
            fields.put(prefix + ".FailureFixture.Diagnostic.Count", "0");
        }
    }

    private static boolean isOneMinusT(int oneMinusTId, int tId, Map<Integer, IrGpuTypedNode> nodesById) {
        IrGpuTypedNode oneMinusT = nodesById.get(oneMinusTId);
        if (!isBinary(oneMinusT, "-")) {
            return false;
        }
        Integer left = singleChild(oneMinusT, "left");
        Integer right = singleChild(oneMinusT, "right");
        return left != null
                && right != null
                && isOne(nodesById.get(left))
                && sameExpression(tId, right, nodesById);
    }

    private static boolean sameExpression(int leftId, int rightId, Map<Integer, IrGpuTypedNode> nodesById) {
        if (leftId == rightId) {
            return true;
        }
        Optional<String> left = sourceText(nodesById.get(leftId), nodesById);
        Optional<String> right = sourceText(nodesById.get(rightId), nodesById);
        return left.isPresent() && left.equals(right);
    }

    private static Optional<String> sourceText(IrGpuTypedNode node, Map<Integer, IrGpuTypedNode> nodesById) {
        if (node == null) {
            return Optional.empty();
        }
        if ("GpuIrVariableRef".equals(node.kind())) {
            String name = node.attributes().getOrDefault("name", "");
            return name.isBlank() ? Optional.empty() : Optional.of(name);
        }
        if ("GpuIrLiteral".equals(node.kind())) {
            String sourceText = node.attributes().getOrDefault("sourceText", "");
            return sourceText.isBlank() ? Optional.empty() : Optional.of(sourceText);
        }
        if ("GpuIrUnary".equals(node.kind())) {
            String operator = node.attributes().getOrDefault("operator", "");
            Optional<String> operand = childSourceText(node, "operand", nodesById);
            if (operator.isBlank() || operand.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of("(" + operator + operand.orElseThrow() + ")");
        }
        if ("GpuIrBinary".equals(node.kind())) {
            String operator = node.attributes().getOrDefault("operator", "");
            Optional<String> left = childSourceText(node, "left", nodesById);
            Optional<String> right = childSourceText(node, "right", nodesById);
            if (operator.isBlank() || left.isEmpty() || right.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of("(" + left.orElseThrow() + " " + operator + " " + right.orElseThrow() + ")");
        }
        if ("GpuIrIntrinsicCall".equals(node.kind())) {
            String name = firstAttribute(node, "name", "backendName", "function", "intrinsic");
            if (name.isBlank()) {
                return Optional.empty();
            }
            ArrayList<String> args = new ArrayList<>();
            for (Integer argumentId : callArguments(node)) {
                Optional<String> argumentText = sourceText(nodesById.get(argumentId), nodesById);
                if (argumentText.isEmpty()) {
                    return Optional.empty();
                }
                args.add(argumentText.orElseThrow());
            }
            String template = node.attributes().getOrDefault("codeTemplate", "");
            return Optional.of("intrinsic(" + name + " template=\"" + template + "\" args=["
                    + String.join(", ", args) + "])");
        }
        return Optional.empty();
    }

    private static Optional<String> childSourceText(
            IrGpuTypedNode node,
            String childName,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        Integer childId = singleChild(node, childName);
        if (childId == null) {
            return Optional.empty();
        }
        return sourceText(nodesById.get(childId), nodesById);
    }

    private static GpuIrTypedBodyGraphPatch.Plan graphPatch(MixCandidate candidate) {
        return GpuIrTypedBodyGraphPatch.plan(
                candidate.expressionText(),
                candidate.replacementText(),
                GpuIrTypedBodyGraphPatch.intrinsicCall(
                        candidate.rootNodeId(),
                        TARGET_INTRINSIC,
                        "mix-review",
                        candidate.argumentNodeIds(),
                        "mix-review"
                )
        );
    }

    private static Integer singleChild(IrGpuTypedNode node, String name) {
        if (node == null) {
            return null;
        }
        List<Integer> ids = node.children().getOrDefault(name, List.of());
        return ids.size() == 1 ? ids.get(0) : null;
    }

    private static List<Integer> callArguments(IrGpuTypedNode node) {
        if (node == null) {
            return List.of();
        }
        List<Integer> args = node.children().getOrDefault("args", List.of());
        return args.isEmpty() ? node.children().getOrDefault("arguments", List.of()) : args;
    }

    private static boolean isCall(IrGpuTypedNode node, String backendName) {
        return node != null
                && "GpuIrIntrinsicCall".equals(node.kind())
                && backendName.equals(firstAttribute(node, "backendName", "name", "function", "intrinsic"));
    }

    private static boolean isBinary(IrGpuTypedNode node, String operator) {
        return node != null
                && "GpuIrBinary".equals(node.kind())
                && operator.equals(node.attributes().get("operator"));
    }

    private static boolean isOne(IrGpuTypedNode node) {
        return normalizedLiteralText(node).matches("[+]?1(?:\\.0+)?[fFdD]?");
    }

    private static String normalizedLiteralText(IrGpuTypedNode node) {
        if (node == null || !"GpuIrLiteral".equals(node.kind())) {
            return "";
        }
        String literal = firstAttribute(node, "sourceText", "value", "literal").trim();
        while (literal.startsWith("(") && literal.endsWith(")") && literal.length() > 2) {
            literal = literal.substring(1, literal.length() - 1).trim();
        }
        return literal;
    }

    private static boolean isPureExpression(int nodeId, Map<Integer, IrGpuTypedNode> nodesById) {
        return isPureExpression(nodeId, nodesById, new LinkedHashSet<>());
    }

    private static boolean isPureExpression(
            int nodeId,
            Map<Integer, IrGpuTypedNode> nodesById,
            Set<Integer> visiting
    ) {
        if (!visiting.add(nodeId)) {
            return false;
        }
        IrGpuTypedNode node = nodesById.get(nodeId);
        if (node == null) {
            return false;
        }
        if ("GpuIrVariableRef".equals(node.kind()) || "GpuIrLiteral".equals(node.kind())) {
            return true;
        }
        if ("GpuIrUnary".equals(node.kind()) || "GpuIrBinary".equals(node.kind()) || "GpuIrIntrinsicCall".equals(node.kind())) {
            for (List<Integer> childIds : node.children().values()) {
                for (Integer childId : childIds) {
                    if (childId == null || !isPureExpression(childId, nodesById, visiting)) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    private static String firstAttribute(IrGpuTypedNode node, String... names) {
        if (node == null || names == null) {
            return "";
        }
        for (String name : names) {
            String value = node.attributes().get(name);
            if (value != null) {
                return value;
            }
        }
        return "";
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
        private final LinkedHashSet<String> candidateKeys = new LinkedHashSet<>();
        private int transformedNodeCount;
        private int changedMethodBodyCount;
        private int bodyTextReplacementCount;
        private int fixedPointPassCount;
        private int canonicalMixCount;
        private int expandedMixCount;
        private int madExpandedMixCount;
        private int skippedTypedBodyMissingCount;
        private int skippedUnsupportedFormatCount;
        private int skippedFastMathPolicyCount;
        private int skippedMissingChildReferenceCount;
        private int skippedUnsupportedShapeCount;
        private int skippedBodyTextPatternMissingCount;
        private final ArrayList<MixCandidate> transformedCandidates = new ArrayList<>();
        private String firstTransformedNode = "none";
        private String firstExpression = "none";
        private String firstReplacement = "none";
        private String firstBlocker = "none";

        private int candidateCount() {
            return candidateKeys.size();
        }

        private void recordCandidate(MixCandidate candidate) {
            candidateKeys.add(candidate.methodName() + "#" + candidate.rootNodeId());
        }

        private void recordPatchBlocker(String blocker) {
            switch (GpuIrTypedBodyGraphPatch.blockerKind(blocker)) {
                case BODY_TEXT_PATTERN_MISSING -> skippedBodyTextPatternMissingCount++;
                case TYPED_BODY_MISSING -> skippedTypedBodyMissingCount++;
                case TYPED_GRAPH_MISSING -> skippedMissingChildReferenceCount++;
                case OTHER -> skippedUnsupportedShapeCount++;
            }
            setFirstBlocker(blocker);
        }

        private void add(RewriteStats other) {
            typedBodyCount += other.typedBodyCount;
            candidateKeys.addAll(other.candidateKeys);
            transformedNodeCount += other.transformedNodeCount;
            changedMethodBodyCount += other.changedMethodBodyCount;
            bodyTextReplacementCount += other.bodyTextReplacementCount;
            fixedPointPassCount += other.fixedPointPassCount;
            canonicalMixCount += other.canonicalMixCount;
            expandedMixCount += other.expandedMixCount;
            madExpandedMixCount += other.madExpandedMixCount;
            skippedTypedBodyMissingCount += other.skippedTypedBodyMissingCount;
            skippedUnsupportedFormatCount += other.skippedUnsupportedFormatCount;
            skippedFastMathPolicyCount += other.skippedFastMathPolicyCount;
            skippedMissingChildReferenceCount += other.skippedMissingChildReferenceCount;
            skippedUnsupportedShapeCount += other.skippedUnsupportedShapeCount;
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
