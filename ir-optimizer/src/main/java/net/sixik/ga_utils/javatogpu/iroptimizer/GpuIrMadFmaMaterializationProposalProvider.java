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
 * Review-only fast-math materialization for {@code a * b + c} into an OpenCL {@code mad(a, b, c)} intrinsic.
 */
public final class GpuIrMadFmaMaterializationProposalProvider implements GpuIrOptimizationProposalProvider {

    public static final String PROVIDER_ID = GpuIrOptimizerModule.MODULE_ID + ".mad-fma-materialization";
    public static final String PROVIDER_VERSION = PROVIDER_ID + ":1";

    private static final String TARGET_INTRINSIC = "mad";

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
                            "ir-optimizer.mad-fma-materialization",
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
                        "ir-optimizer.mad-fma-materialization",
                        "review-only-mad-fma-materialized",
                        rewrite.fields(request)
                ),
                List.of("materialized " + rewrite.transformedNodeCount()
                        + " mad/fma peephole rewrite(s) into a review candidate")
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
        return 420;
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
            int skippedTypedBodyMissingCount,
            int skippedUnsupportedFormatCount,
            int skippedFastMathPolicyCount,
            int skippedMissingChildReferenceCount,
            int skippedImpureOperandCount,
            int skippedBodyTextPatternMissingCount,
            List<MadCandidate> transformedCandidates,
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
                    stats.skippedTypedBodyMissingCount,
                    stats.skippedUnsupportedFormatCount,
                    stats.skippedFastMathPolicyCount,
                    stats.skippedMissingChildReferenceCount,
                    stats.skippedImpureOperandCount,
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
                    stats.skippedTypedBodyMissingCount,
                    stats.skippedUnsupportedFormatCount,
                    stats.skippedFastMathPolicyCount,
                    stats.skippedMissingChildReferenceCount,
                    stats.skippedImpureOperandCount,
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
                return "mad/fma materialization rewrote " + transformedNodeCount
                        + " multiply-add expression(s) to OpenCL mad intrinsic calls";
            }
            return "mad/fma materialization produced no review candidate: " + firstBlocker;
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
            fields.put("fixedPoint.rewriteGranularity", "single-mad-fma-expression-per-pass");
            fields.put("skipped.typedBodyMissing.count", Integer.toString(skippedTypedBodyMissingCount));
            fields.put("skipped.unsupportedFormat.count", Integer.toString(skippedUnsupportedFormatCount));
            fields.put("skipped.fastMathPolicy.count", Integer.toString(skippedFastMathPolicyCount));
            fields.put("skipped.missingChildReference.count", Integer.toString(skippedMissingChildReferenceCount));
            fields.put("skipped.impureOperand.count", Integer.toString(skippedImpureOperandCount));
            fields.put("skipped.bodyTextPatternMissing.count", Integer.toString(skippedBodyTextPatternMissingCount));
            fields.put("rewrite.proposed", Boolean.toString(changed()));
            fields.put("rewrite.materialized", Boolean.toString(changed()));
            fields.put("optimizerFamily", "mad-fma-materialization");
            fields.put("targetIntrinsic", TARGET_INTRINSIC);
            fields.put("mutationRequired", "false");
            fields.put("productionAffecting", "false");
            fields.put("previewOnly", "false");
            fields.put("provider.mutatesOriginal", "false");
            fields.put("policy.mutationAllowed", Boolean.toString(mutationAllowed));
            fields.put("policy.proposalOnly", Boolean.toString(!mutationAllowed));
            fields.put("policy.fastMathAllowed", Boolean.toString(fastMathAllowed));
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
                    "optimizer-family:mad-fma-materialization:review-candidate"
            );
            fields.put("runtimeEquivalencePayload.resource", "ir-optimizer://mad-fma-materialization/review-candidate");
            fields.put("runtimeEquivalencePayload.cpuReference.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.preOptimizationOutput.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.postOptimizationOutput.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.tolerance.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.failureFixture.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.CpuReference", "fastMathMadCandidates=" + transformedNodeCount);
            fields.put("runtimeEquivalencePayload.PreOptimizationOutput", "expressions=" + transformedNodeCount
                    + ", first=" + firstExpression);
            fields.put("runtimeEquivalencePayload.PostOptimizationOutput", "intrinsicCalls=" + transformedNodeCount
                    + ", first=" + firstReplacement);
            fields.put("runtimeEquivalencePayload.Tolerance", "mode=fast-math-mad-review, strictFloat=false");
            fields.put("runtimeEquivalencePayload.FailureFixture", "none");
            fields.put("runtimeEquivalencePayload.ReferenceMode", "static-fast-math-multiply-add-to-mad");
            fields.put("runtimeEquivalencePayload.CaseIdentity", "method-name-and-node-id");
            appendRuntimeEquivalenceCases(fields, transformedCandidates);
            fields.put("reviewPackage.required", Boolean.toString(changed()));
            fields.put("reviewPackage.firstBlocker", changed() ? "approval-pending" : firstBlocker);
            fields.put("safety.scope", "fast-math-opencl-mad-review-candidate");
            fields.put("safety.fastMathRequired", "true");
            fields.put("safety.fastMathAllowed", Boolean.toString(fastMathAllowed));
            fields.put("safety.strictFloatPreserved", "false");
            fields.put("safety.sideEffectFreedomProven", Boolean.toString(changed()));
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
                return "no-mad-fma-candidate";
            }
            if (stats.skippedFastMathPolicyCount > 0) {
                return "fast-math-policy-disabled";
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
            Optional<MadCandidate> candidate = firstMaterializableCandidate(
                    methodBody.name(),
                    currentTypedBody,
                    currentBody,
                    fastMathAllowed,
                    stats
            );
            if (candidate.isEmpty()) {
                break;
            }
            MadCandidate value = candidate.orElseThrow();
            GpuIrTypedBodyGraphPatch.Applied patch = graphPatch(value).apply(currentTypedBody, currentBody);
            if (!patch.applied()) {
                stats.recordPatchBlocker(patch.blocker());
                break;
            }
            changed = true;
            stats.fixedPointPassCount++;
            stats.transformedNodeCount++;
            stats.bodyTextReplacementCount++;
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

    private static Optional<MadCandidate> firstMaterializableCandidate(
            String methodName,
            IrGpuTypedBody typedBody,
            String body,
            boolean fastMathAllowed,
            RewriteStats stats
    ) {
        Map<Integer, IrGpuTypedNode> nodesById = GpuIrTypedBodyGraphPatch.nodesById(typedBody);
        Set<Integer> reachableNodeIds = GpuIrTypedBodyGraphPatch.reachableNodeIds(typedBody, nodesById);
        for (IrGpuTypedNode node : typedBody.nodes()) {
            if (!reachableNodeIds.contains(node.id()) || !isBinary(node, "+")) {
                continue;
            }
            Optional<MadCandidate> candidate = analyzeCandidate(methodName, node, nodesById);
            if (candidate.isEmpty()) {
                continue;
            }
            MadCandidate value = candidate.orElseThrow();
            stats.recordCandidate(value);
            if (!fastMathAllowed) {
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

    private static Optional<MadCandidate> analyzeCandidate(
            String methodName,
            IrGpuTypedNode addNode,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        Integer left = singleChild(addNode, "left");
        Integer right = singleChild(addNode, "right");
        if (left == null || right == null) {
            return Optional.empty();
        }
        IrGpuTypedNode leftNode = nodesById.get(left);
        IrGpuTypedNode rightNode = nodesById.get(right);
        boolean leftMultiply = isBinary(leftNode, "*");
        boolean rightMultiply = isBinary(rightNode, "*");
        if (!leftMultiply && !rightMultiply) {
            return Optional.empty();
        }
        IrGpuTypedNode multiply = leftMultiply ? leftNode : rightNode;
        int addendId = leftMultiply ? right : left;
        Integer multiplyLeft = singleChild(multiply, "left");
        Integer multiplyRight = singleChild(multiply, "right");
        if (multiplyLeft == null || multiplyRight == null) {
            return Optional.empty();
        }
        Optional<String> expressionText = sourceText(addNode, nodesById);
        Optional<String> multiplyLeftText = sourceText(nodesById.get(multiplyLeft), nodesById);
        Optional<String> multiplyRightText = sourceText(nodesById.get(multiplyRight), nodesById);
        Optional<String> addendText = sourceText(nodesById.get(addendId), nodesById);
        if (expressionText.isEmpty() || multiplyLeftText.isEmpty() || multiplyRightText.isEmpty() || addendText.isEmpty()) {
            return Optional.empty();
        }
        String replacementText = "intrinsic(" + TARGET_INTRINSIC + " template=\"\" args=["
                + multiplyLeftText.orElseThrow() + ", "
                + multiplyRightText.orElseThrow() + ", "
                + addendText.orElseThrow() + "])";
        return Optional.of(new MadCandidate(
                methodName,
                addNode.id(),
                multiply.id(),
                List.of(multiplyLeft, multiplyRight, addendId),
                expressionText.orElseThrow(),
                replacementText,
                List.of(multiplyLeftText.orElseThrow(), multiplyRightText.orElseThrow(), addendText.orElseThrow())
        ));
    }

    private record MadCandidate(
            String methodName,
            int rootNodeId,
            int multiplyNodeId,
            List<Integer> argumentNodeIds,
            String expressionText,
            String replacementText,
            List<String> argumentTexts
    ) {

        private String summary() {
            return methodName + "#" + rootNodeId + "=" + expressionText + "->" + replacementText;
        }
    }

    private static void appendRuntimeEquivalenceCases(
            LinkedHashMap<String, String> fields,
            List<MadCandidate> candidates
    ) {
        fields.put("runtimeEquivalencePayload.Case.Count", Integer.toString(candidates.size()));
        for (int index = 0; index < candidates.size(); index++) {
            MadCandidate candidate = candidates.get(index);
            String prefix = "runtimeEquivalencePayload.Case." + index;
            fields.put(prefix + ".Name", candidate.summary());
            fields.put(prefix + ".MethodName", candidate.methodName());
            fields.put(prefix + ".NodeId", Integer.toString(candidate.rootNodeId()));
            fields.put(prefix + ".RewriteKind", "mad-fma-to-opencl-mad");
            fields.put(prefix + ".Successful", "true");
            fields.put(prefix + ".Input.Count", "4");
            fields.put(prefix + ".Input.0.Name", "expression");
            fields.put(prefix + ".Input.0.Value", candidate.expressionText());
            fields.put(prefix + ".Input.1.Name", "mulLeft");
            fields.put(prefix + ".Input.1.Value", candidate.argumentTexts().get(0));
            fields.put(prefix + ".Input.2.Name", "mulRight");
            fields.put(prefix + ".Input.2.Value", candidate.argumentTexts().get(1));
            fields.put(prefix + ".Input.3.Name", "addend");
            fields.put(prefix + ".Input.3.Value", candidate.argumentTexts().get(2));
            fields.put(prefix + ".Output.Count", "1");
            fields.put(prefix + ".Output.0.Name", TARGET_INTRINSIC);
            fields.put(prefix + ".Output.0.CpuReference", candidate.expressionText());
            fields.put(prefix + ".Output.0.PreOptimization", candidate.expressionText());
            fields.put(prefix + ".Output.0.PostOptimization", candidate.replacementText());
            fields.put(prefix + ".Output.0.Tolerance", "fast-math-mad-review");
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

    private static GpuIrTypedBodyGraphPatch.Plan graphPatch(MadCandidate candidate) {
        return GpuIrTypedBodyGraphPatch.plan(
                candidate.expressionText(),
                candidate.replacementText(),
                GpuIrTypedBodyGraphPatch.intrinsicCall(
                        candidate.rootNodeId(),
                        TARGET_INTRINSIC,
                        "fast-math-review",
                        candidate.argumentNodeIds(),
                        "fast-math-review"
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
        List<Integer> args = node.children().getOrDefault("args", List.of());
        return args.isEmpty() ? node.children().getOrDefault("arguments", List.of()) : args;
    }

    private static boolean isBinary(IrGpuTypedNode node, String operator) {
        return node != null
                && "GpuIrBinary".equals(node.kind())
                && operator.equals(node.attributes().get("operator"));
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
        private int skippedTypedBodyMissingCount;
        private int skippedUnsupportedFormatCount;
        private int skippedFastMathPolicyCount;
        private int skippedMissingChildReferenceCount;
        private int skippedImpureOperandCount;
        private int skippedBodyTextPatternMissingCount;
        private final ArrayList<MadCandidate> transformedCandidates = new ArrayList<>();
        private String firstTransformedNode = "none";
        private String firstExpression = "none";
        private String firstReplacement = "none";
        private String firstBlocker = "none";

        private int candidateCount() {
            return candidateKeys.size();
        }

        private void recordCandidate(MadCandidate candidate) {
            candidateKeys.add(candidate.methodName() + "#" + candidate.rootNodeId());
        }

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
            candidateKeys.addAll(other.candidateKeys);
            transformedNodeCount += other.transformedNodeCount;
            changedMethodBodyCount += other.changedMethodBodyCount;
            bodyTextReplacementCount += other.bodyTextReplacementCount;
            fixedPointPassCount += other.fixedPointPassCount;
            skippedTypedBodyMissingCount += other.skippedTypedBodyMissingCount;
            skippedUnsupportedFormatCount += other.skippedUnsupportedFormatCount;
            skippedFastMathPolicyCount += other.skippedFastMathPolicyCount;
            skippedMissingChildReferenceCount += other.skippedMissingChildReferenceCount;
            skippedImpureOperandCount += other.skippedImpureOperandCount;
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
