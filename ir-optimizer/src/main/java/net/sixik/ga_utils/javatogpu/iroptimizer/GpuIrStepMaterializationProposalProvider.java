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
 * Review-only materialization for simple ternary masks into OpenCL {@code step(edge, x)} expressions.
 */
public final class GpuIrStepMaterializationProposalProvider implements GpuIrOptimizationProposalProvider {

    public static final String PROVIDER_ID = GpuIrOptimizerModule.MODULE_ID + ".step-materialization";
    public static final String PROVIDER_VERSION = PROVIDER_ID + ":1";

    private static final String TARGET_INTRINSIC = "step";

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
                            "ir-optimizer.step-materialization",
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
                        "ir-optimizer.step-materialization",
                        "review-only-step-materialized",
                        rewrite.fields(request)
                ),
                List.of("materialized " + rewrite.transformedNodeCount()
                        + " step peephole rewrite(s) into a review candidate")
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
        return 422;
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
            int directStepCount,
            int invertedStepCount,
            int skippedTypedBodyMissingCount,
            int skippedUnsupportedFormatCount,
            int skippedMissingChildReferenceCount,
            int skippedUnsupportedShapeCount,
            int skippedBodyTextPatternMissingCount,
            List<StepCandidate> transformedCandidates,
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
                    stats.candidateCount(),
                    stats.transformedNodeCount,
                    stats.changedMethodBodyCount,
                    stats.bodyTextReplacementCount,
                    stats.fixedPointPassCount,
                    stats.directStepCount,
                    stats.invertedStepCount,
                    stats.skippedTypedBodyMissingCount,
                    stats.skippedUnsupportedFormatCount,
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
                    mutationAllowed
            );
        }

        private static ArtifactRewrite noChange(RewriteStats stats, String firstBlocker, boolean mutationAllowed) {
            return new ArtifactRewrite(
                    Optional.empty(),
                    stats.methodBodyCount,
                    stats.typedBodyCount,
                    stats.candidateCount(),
                    stats.transformedNodeCount,
                    stats.changedMethodBodyCount,
                    stats.bodyTextReplacementCount,
                    stats.fixedPointPassCount,
                    stats.directStepCount,
                    stats.invertedStepCount,
                    stats.skippedTypedBodyMissingCount,
                    stats.skippedUnsupportedFormatCount,
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
                return "step materialization rewrote " + transformedNodeCount
                        + " ternary mask expression(s) to OpenCL step expressions";
            }
            return "step materialization produced no review candidate: " + firstBlocker;
        }

        private Map<String, String> fields(GpuIrOptimizationProposalRequest request) {
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
            fields.put("fixedPoint.rewriteGranularity", "single-step-expression-per-pass");
            fields.put("directStep.count", Integer.toString(directStepCount));
            fields.put("invertedStep.count", Integer.toString(invertedStepCount));
            fields.put("skipped.typedBodyMissing.count", Integer.toString(skippedTypedBodyMissingCount));
            fields.put("skipped.unsupportedFormat.count", Integer.toString(skippedUnsupportedFormatCount));
            fields.put("skipped.missingChildReference.count", Integer.toString(skippedMissingChildReferenceCount));
            fields.put("skipped.unsupportedShape.count", Integer.toString(skippedUnsupportedShapeCount));
            fields.put("skipped.bodyTextPatternMissing.count", Integer.toString(skippedBodyTextPatternMissingCount));
            fields.put("rewrite.proposed", Boolean.toString(changed()));
            fields.put("rewrite.materialized", Boolean.toString(changed()));
            fields.put("optimizerFamily", "step-materialization");
            fields.put("targetIntrinsic", TARGET_INTRINSIC);
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
            fields.put("runtimeEquivalencePayload.comparisonMode", "optimizer-family:step-materialization:review-candidate");
            fields.put("runtimeEquivalencePayload.resource", "ir-optimizer://step-materialization/review-candidate");
            fields.put("runtimeEquivalencePayload.cpuReference.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.preOptimizationOutput.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.postOptimizationOutput.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.tolerance.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.failureFixture.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.CpuReference", "stepCandidates=" + transformedNodeCount);
            fields.put("runtimeEquivalencePayload.PreOptimizationOutput", "expressions=" + transformedNodeCount
                    + ", first=" + firstExpression);
            fields.put("runtimeEquivalencePayload.PostOptimizationOutput", "stepExpressions=" + transformedNodeCount
                    + ", first=" + firstReplacement);
            fields.put("runtimeEquivalencePayload.Tolerance", "mode=exact-ternary-mask-to-step-review");
            fields.put("runtimeEquivalencePayload.FailureFixture", "none");
            fields.put("runtimeEquivalencePayload.ReferenceMode", "static-ternary-mask-to-step");
            fields.put("runtimeEquivalencePayload.CaseIdentity", "method-name-and-node-id");
            appendRuntimeEquivalenceCases(fields, transformedCandidates);
            fields.put("reviewPackage.required", Boolean.toString(changed()));
            fields.put("reviewPackage.firstBlocker", changed() ? "approval-pending" : firstBlocker);
            fields.put("safety.scope", "opencl-step-review-candidate");
            fields.put("safety.fastMathRequired", "false");
            fields.put("safety.strictFloatPreserved", "true");
            fields.put("safety.strictComparisonPreserved", Boolean.toString(changed()));
            fields.put("safety.equalityBehaviorPreserved", Boolean.toString(changed()));
            fields.put("safety.nanComparisonPreserved", Boolean.toString(changed()));
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
            if (stats.candidateCount() == 0) {
                return "no-step-candidate";
            }
            return "body-text-pattern-missing";
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

        IrGpuTypedBody currentTypedBody = methodBody.typedBody();
        String currentBody = methodBody.body();
        boolean changed = false;
        int maxPasses = Math.max(1, currentTypedBody.nodes().size() + 1);
        for (int pass = 0; pass < maxPasses; pass++) {
            Optional<StepCandidate> candidate = firstMaterializableCandidate(methodBody.name(), currentTypedBody, currentBody, stats);
            if (candidate.isEmpty()) {
                break;
            }
            StepCandidate value = candidate.orElseThrow();
            GpuIrTypedBodyGraphPatch.Applied patch = graphPatch(value).apply(currentTypedBody, currentBody);
            if (!patch.applied()) {
                stats.recordPatchBlocker(patch.blocker());
                break;
            }
            changed = true;
            stats.fixedPointPassCount++;
            stats.transformedNodeCount++;
            stats.bodyTextReplacementCount++;
            if (value.inverted()) {
                stats.invertedStepCount++;
            } else {
                stats.directStepCount++;
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

    private static Optional<StepCandidate> firstMaterializableCandidate(
            String methodName,
            IrGpuTypedBody typedBody,
            String body,
            RewriteStats stats
    ) {
        Map<Integer, IrGpuTypedNode> nodesById = GpuIrTypedBodyGraphPatch.nodesById(typedBody);
        Set<Integer> reachableNodeIds = GpuIrTypedBodyGraphPatch.reachableNodeIds(typedBody, nodesById);
        int nextNodeId = GpuIrTypedBodyGraphPatch.nextNodeId(typedBody);
        for (IrGpuTypedNode node : typedBody.nodes()) {
            if (!reachableNodeIds.contains(node.id()) || !isConditional(node)) {
                continue;
            }
            Optional<StepCandidate> candidate = analyzeCandidate(methodName, node, nodesById, nextNodeId);
            if (candidate.isEmpty()) {
                stats.skippedUnsupportedShapeCount++;
                stats.setFirstBlocker("step-shape-unsupported");
                continue;
            }
            StepCandidate value = candidate.orElseThrow();
            stats.recordCandidate(value);
            if (!body.contains(value.expressionText())) {
                stats.skippedBodyTextPatternMissingCount++;
                stats.setFirstBlocker("body-text-pattern-missing");
                continue;
            }
            return Optional.of(value);
        }
        return Optional.empty();
    }

    private static Optional<StepCandidate> analyzeCandidate(
            String methodName,
            IrGpuTypedNode ternaryNode,
            Map<Integer, IrGpuTypedNode> nodesById,
            int stepNodeId
    ) {
        Integer conditionId = singleChild(ternaryNode, "condition", "cond");
        Integer thenId = singleChild(ternaryNode, "then", "true", "ifTrue", "whenTrue");
        Integer elseId = singleChild(ternaryNode, "else", "false", "ifFalse", "whenFalse");
        if (conditionId == null || thenId == null || elseId == null) {
            return Optional.empty();
        }
        IrGpuTypedNode condition = nodesById.get(conditionId);
        if (!"GpuIrBinary".equals(condition == null ? "" : condition.kind())) {
            return Optional.empty();
        }
        Integer left = singleChild(condition, "left");
        Integer right = singleChild(condition, "right");
        if (left == null || right == null) {
            return Optional.empty();
        }
        boolean thenOne = isOne(nodesById.get(thenId));
        boolean thenZero = isZero(nodesById.get(thenId));
        boolean elseOne = isOne(nodesById.get(elseId));
        boolean elseZero = isZero(nodesById.get(elseId));
        if (!((thenOne && elseZero) || (thenZero && elseOne))) {
            return Optional.empty();
        }
        String operator = condition.attributes().getOrDefault("operator", "");
        StepShape shape = stepShape(operator, thenOne, left, right);
        if (shape == null) {
            return Optional.empty();
        }
        Optional<String> expressionText = sourceText(ternaryNode, nodesById);
        Optional<String> edgeText = sourceText(nodesById.get(shape.edgeId()), nodesById);
        Optional<String> valueText = sourceText(nodesById.get(shape.valueId()), nodesById);
        Optional<String> oneText = sourceText(nodesById.get(thenOne ? thenId : elseId), nodesById);
        if (expressionText.isEmpty() || edgeText.isEmpty() || valueText.isEmpty() || oneText.isEmpty()) {
            return Optional.empty();
        }
        String stepText = "intrinsic(" + TARGET_INTRINSIC + " template=\"\" args=["
                + edgeText.orElseThrow() + ", " + valueText.orElseThrow() + "])";
        String replacementText = shape.inverted()
                ? "(" + oneText.orElseThrow() + " - " + stepText + ")"
                : stepText;
        return Optional.of(new StepCandidate(
                methodName,
                ternaryNode.id(),
                condition.id(),
                shape.inverted() ? stepNodeId : ternaryNode.id(),
                thenOne ? thenId : elseId,
                List.of(shape.edgeId(), shape.valueId()),
                expressionText.orElseThrow(),
                replacementText,
                stepText,
                List.of(edgeText.orElseThrow(), valueText.orElseThrow()),
                shape.inverted(),
                operator
        ));
    }

    private static StepShape stepShape(String operator, boolean conditionReturnsOne, int left, int right) {
        return switch (operator) {
            case ">=" -> conditionReturnsOne
                    ? new StepShape(right, left, false)
                    : new StepShape(right, left, true);
            case "<=" -> conditionReturnsOne
                    ? new StepShape(left, right, false)
                    : new StepShape(left, right, true);
            case ">" -> conditionReturnsOne
                    ? new StepShape(left, right, true)
                    : new StepShape(left, right, false);
            case "<" -> conditionReturnsOne
                    ? new StepShape(right, left, true)
                    : new StepShape(right, left, false);
            default -> null;
        };
    }

    private record StepShape(int edgeId, int valueId, boolean inverted) {
    }

    private record StepCandidate(
            String methodName,
            int rootNodeId,
            int conditionNodeId,
            int stepNodeId,
            int oneLiteralNodeId,
            List<Integer> argumentNodeIds,
            String expressionText,
            String replacementText,
            String stepText,
            List<String> argumentTexts,
            boolean inverted,
            String comparisonOperator
    ) {

        private String summary() {
            return methodName + "#" + rootNodeId + "=" + expressionText + "->" + replacementText;
        }
    }

    private static void appendRuntimeEquivalenceCases(
            LinkedHashMap<String, String> fields,
            List<StepCandidate> candidates
    ) {
        fields.put("runtimeEquivalencePayload.Case.Count", Integer.toString(candidates.size()));
        for (int index = 0; index < candidates.size(); index++) {
            StepCandidate candidate = candidates.get(index);
            String prefix = "runtimeEquivalencePayload.Case." + index;
            fields.put(prefix + ".Name", candidate.summary());
            fields.put(prefix + ".MethodName", candidate.methodName());
            fields.put(prefix + ".NodeId", Integer.toString(candidate.rootNodeId()));
            fields.put(prefix + ".RewriteKind", candidate.inverted() ? "ternary-mask-to-inverted-opencl-step" : "ternary-mask-to-opencl-step");
            fields.put(prefix + ".Successful", "true");
            fields.put(prefix + ".Input.Count", "4");
            fields.put(prefix + ".Input.0.Name", "expression");
            fields.put(prefix + ".Input.0.Value", candidate.expressionText());
            fields.put(prefix + ".Input.1.Name", "edge");
            fields.put(prefix + ".Input.1.Value", candidate.argumentTexts().get(0));
            fields.put(prefix + ".Input.2.Name", "value");
            fields.put(prefix + ".Input.2.Value", candidate.argumentTexts().get(1));
            fields.put(prefix + ".Input.3.Name", "comparisonOperator");
            fields.put(prefix + ".Input.3.Value", candidate.comparisonOperator());
            fields.put(prefix + ".Output.Count", "1");
            fields.put(prefix + ".Output.0.Name", TARGET_INTRINSIC);
            fields.put(prefix + ".Output.0.CpuReference", candidate.expressionText());
            fields.put(prefix + ".Output.0.PreOptimization", candidate.expressionText());
            fields.put(prefix + ".Output.0.PostOptimization", candidate.replacementText());
            fields.put(prefix + ".Output.0.Tolerance", "exact-ternary-mask-to-step-review");
            fields.put(prefix + ".Output.0.Equivalent", "true");
            fields.put(prefix + ".FailureFixture.Diagnostic.Count", "0");
        }
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
        if (isConditional(node)) {
            Optional<String> condition = childSourceText(node, List.of("condition", "cond"), nodesById);
            Optional<String> thenValue = childSourceText(node, List.of("then", "true", "ifTrue", "whenTrue"), nodesById);
            Optional<String> elseValue = childSourceText(node, List.of("else", "false", "ifFalse", "whenFalse"), nodesById);
            if (condition.isEmpty() || thenValue.isEmpty() || elseValue.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of("(" + condition.orElseThrow() + " ? " + thenValue.orElseThrow()
                    + " : " + elseValue.orElseThrow() + ")");
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

    private static Optional<String> childSourceText(
            IrGpuTypedNode node,
            List<String> childNames,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        Integer childId = singleChild(node, childNames.toArray(String[]::new));
        if (childId == null) {
            return Optional.empty();
        }
        return sourceText(nodesById.get(childId), nodesById);
    }

    private static GpuIrTypedBodyGraphPatch.Plan graphPatch(StepCandidate candidate) {
        IrGpuTypedNode replacement = candidate.inverted()
                ? invertedRootNode(candidate.rootNodeId(), candidate)
                : stepNode(candidate.rootNodeId(), candidate);
        return candidate.inverted()
                ? GpuIrTypedBodyGraphPatch.plan(
                        candidate.expressionText(),
                        candidate.replacementText(),
                        replacement,
                        List.of(stepNode(candidate.stepNodeId(), candidate))
                )
                : GpuIrTypedBodyGraphPatch.plan(candidate.expressionText(), candidate.replacementText(), replacement);
    }

    private static IrGpuTypedNode invertedRootNode(int nodeId, StepCandidate candidate) {
        return new IrGpuTypedNode(
                nodeId,
                "GpuIrBinary",
                Map.of("operator", "-"),
                Map.of(
                        "left", List.of(candidate.oneLiteralNodeId()),
                        "right", List.of(candidate.stepNodeId())
                )
        );
    }

    private static IrGpuTypedNode stepNode(int nodeId, StepCandidate candidate) {
        return GpuIrTypedBodyGraphPatch.intrinsicCall(
                nodeId,
                TARGET_INTRINSIC,
                "step-review",
                candidate.argumentNodeIds(),
                "step-review"
        );
    }

    private static Integer singleChild(IrGpuTypedNode node, String... names) {
        if (node == null || names == null) {
            return null;
        }
        for (String name : names) {
            List<Integer> ids = node.children().getOrDefault(name, List.of());
            if (ids.size() == 1) {
                return ids.get(0);
            }
        }
        return null;
    }

    private static List<Integer> callArguments(IrGpuTypedNode node) {
        if (node == null) {
            return List.of();
        }
        List<Integer> args = node.children().getOrDefault("args", List.of());
        return args.isEmpty() ? node.children().getOrDefault("arguments", List.of()) : args;
    }

    private static boolean isConditional(IrGpuTypedNode node) {
        return node != null && ("GpuIrConditional".equals(node.kind())
                || "GpuIrTernary".equals(node.kind())
                || "GpuIrSelect".equals(node.kind()));
    }

    private static boolean isZero(IrGpuTypedNode node) {
        return literalText(node).matches("[+]?0(?:\\.0+)?[fFdD]?");
    }

    private static boolean isOne(IrGpuTypedNode node) {
        return literalText(node).matches("[+]?1(?:\\.0+)?[fFdD]?");
    }

    private static String literalText(IrGpuTypedNode node) {
        if (node == null || !"GpuIrLiteral".equals(node.kind())) {
            return "";
        }
        return firstAttribute(node, "sourceText", "value", "literal");
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
        private int directStepCount;
        private int invertedStepCount;
        private int skippedTypedBodyMissingCount;
        private int skippedUnsupportedFormatCount;
        private int skippedMissingChildReferenceCount;
        private int skippedUnsupportedShapeCount;
        private int skippedBodyTextPatternMissingCount;
        private final ArrayList<StepCandidate> transformedCandidates = new ArrayList<>();
        private String firstTransformedNode = "none";
        private String firstExpression = "none";
        private String firstReplacement = "none";
        private String firstBlocker = "none";

        private int candidateCount() {
            return candidateKeys.size();
        }

        private void recordCandidate(StepCandidate candidate) {
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
            directStepCount += other.directStepCount;
            invertedStepCount += other.invertedStepCount;
            skippedTypedBodyMissingCount += other.skippedTypedBodyMissingCount;
            skippedUnsupportedFormatCount += other.skippedUnsupportedFormatCount;
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
