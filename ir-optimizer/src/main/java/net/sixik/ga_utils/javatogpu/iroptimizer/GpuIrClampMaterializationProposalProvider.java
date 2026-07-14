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
 * Review-only materialization for {@code min(max(x, lo), hi)} into an OpenCL {@code clamp(x, lo, hi)} intrinsic.
 */
public final class GpuIrClampMaterializationProposalProvider implements GpuIrOptimizationProposalProvider {

    public static final String PROVIDER_ID = GpuIrOptimizerModule.MODULE_ID + ".clamp-materialization";
    public static final String PROVIDER_VERSION = PROVIDER_ID + ":1";

    private static final String TARGET_INTRINSIC = "clamp";

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
                            "ir-optimizer.clamp-materialization",
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
                        "ir-optimizer.clamp-materialization",
                        "review-only-clamp-materialized",
                        rewrite.fields(request)
                ),
                List.of("materialized " + rewrite.transformedNodeCount()
                        + " clamp peephole rewrite(s) into a review candidate")
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
        return 421;
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
            int skippedMissingChildReferenceCount,
            int skippedUnsupportedShapeCount,
            int skippedBodyTextPatternMissingCount,
            List<ClampCandidate> transformedCandidates,
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
                return "clamp materialization rewrote " + transformedNodeCount
                        + " min/max expression(s) to OpenCL clamp intrinsic calls";
            }
            return "clamp materialization produced no review candidate: " + firstBlocker;
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
            fields.put("fixedPoint.rewriteGranularity", "single-clamp-expression-per-pass");
            fields.put("skipped.typedBodyMissing.count", Integer.toString(skippedTypedBodyMissingCount));
            fields.put("skipped.unsupportedFormat.count", Integer.toString(skippedUnsupportedFormatCount));
            fields.put("skipped.missingChildReference.count", Integer.toString(skippedMissingChildReferenceCount));
            fields.put("skipped.unsupportedShape.count", Integer.toString(skippedUnsupportedShapeCount));
            fields.put("skipped.bodyTextPatternMissing.count", Integer.toString(skippedBodyTextPatternMissingCount));
            fields.put("rewrite.proposed", Boolean.toString(changed()));
            fields.put("rewrite.materialized", Boolean.toString(changed()));
            fields.put("optimizerFamily", "clamp-materialization");
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
            fields.put("runtimeEquivalencePayload.comparisonMode", "optimizer-family:clamp-materialization:review-candidate");
            fields.put("runtimeEquivalencePayload.resource", "ir-optimizer://clamp-materialization/review-candidate");
            fields.put("runtimeEquivalencePayload.cpuReference.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.preOptimizationOutput.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.postOptimizationOutput.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.tolerance.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.failureFixture.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.CpuReference", "clampCandidates=" + transformedNodeCount);
            fields.put("runtimeEquivalencePayload.PreOptimizationOutput", "expressions=" + transformedNodeCount
                    + ", first=" + firstExpression);
            fields.put("runtimeEquivalencePayload.PostOptimizationOutput", "intrinsicCalls=" + transformedNodeCount
                    + ", first=" + firstReplacement);
            fields.put("runtimeEquivalencePayload.Tolerance", "mode=exact-min-max-to-clamp-review");
            fields.put("runtimeEquivalencePayload.FailureFixture", "none");
            fields.put("runtimeEquivalencePayload.ReferenceMode", "static-min-max-to-clamp");
            fields.put("runtimeEquivalencePayload.CaseIdentity", "method-name-and-node-id");
            appendRuntimeEquivalenceCases(fields, transformedCandidates);
            fields.put("reviewPackage.required", Boolean.toString(changed()));
            fields.put("reviewPackage.firstBlocker", changed() ? "approval-pending" : firstBlocker);
            fields.put("safety.scope", "opencl-clamp-review-candidate");
            fields.put("safety.fastMathRequired", "false");
            fields.put("safety.strictFloatPreserved", "true");
            fields.put("safety.sideEffectFreedomProven", Boolean.toString(changed()));
            fields.put("safety.argumentOrderPreserved", Boolean.toString(changed()));
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
                return "no-clamp-candidate";
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
            Optional<ClampCandidate> candidate = firstMaterializableCandidate(methodBody.name(), currentTypedBody, currentBody, stats);
            if (candidate.isEmpty()) {
                break;
            }
            ClampCandidate value = candidate.orElseThrow();
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

    private static Optional<ClampCandidate> firstMaterializableCandidate(
            String methodName,
            IrGpuTypedBody typedBody,
            String body,
            RewriteStats stats
    ) {
        Map<Integer, IrGpuTypedNode> nodesById = GpuIrTypedBodyGraphPatch.nodesById(typedBody);
        Set<Integer> reachableNodeIds = GpuIrTypedBodyGraphPatch.reachableNodeIds(typedBody, nodesById);
        for (IrGpuTypedNode node : typedBody.nodes()) {
            if (!reachableNodeIds.contains(node.id()) || !isCall(node, "min")) {
                continue;
            }
            Optional<ClampCandidate> candidate = analyzeCandidate(methodName, node, nodesById);
            if (candidate.isEmpty()) {
                stats.skippedUnsupportedShapeCount++;
                stats.setFirstBlocker("clamp-shape-unsupported");
                continue;
            }
            ClampCandidate value = candidate.orElseThrow();
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

    private static Optional<ClampCandidate> analyzeCandidate(
            String methodName,
            IrGpuTypedNode minNode,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        List<Integer> minArgs = callArguments(minNode);
        if (minArgs.size() != 2) {
            return Optional.empty();
        }
        IrGpuTypedNode maxNode = nodesById.get(minArgs.get(0));
        if (!isCall(maxNode, "max")) {
            return Optional.empty();
        }
        List<Integer> maxArgs = callArguments(maxNode);
        if (maxArgs.size() != 2) {
            return Optional.empty();
        }
        int valueId = maxArgs.get(0);
        int lowId = maxArgs.get(1);
        int highId = minArgs.get(1);
        Optional<String> expressionText = sourceText(minNode, nodesById);
        Optional<String> valueText = sourceText(nodesById.get(valueId), nodesById);
        Optional<String> lowText = sourceText(nodesById.get(lowId), nodesById);
        Optional<String> highText = sourceText(nodesById.get(highId), nodesById);
        if (expressionText.isEmpty() || valueText.isEmpty() || lowText.isEmpty() || highText.isEmpty()) {
            return Optional.empty();
        }
        String replacementText = "intrinsic(" + TARGET_INTRINSIC + " template=\"\" args=["
                + valueText.orElseThrow() + ", "
                + lowText.orElseThrow() + ", "
                + highText.orElseThrow() + "])";
        return Optional.of(new ClampCandidate(
                methodName,
                minNode.id(),
                maxNode.id(),
                List.of(valueId, lowId, highId),
                expressionText.orElseThrow(),
                replacementText,
                List.of(valueText.orElseThrow(), lowText.orElseThrow(), highText.orElseThrow())
        ));
    }

    private record ClampCandidate(
            String methodName,
            int rootNodeId,
            int maxNodeId,
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
            List<ClampCandidate> candidates
    ) {
        fields.put("runtimeEquivalencePayload.Case.Count", Integer.toString(candidates.size()));
        for (int index = 0; index < candidates.size(); index++) {
            ClampCandidate candidate = candidates.get(index);
            String prefix = "runtimeEquivalencePayload.Case." + index;
            fields.put(prefix + ".Name", candidate.summary());
            fields.put(prefix + ".MethodName", candidate.methodName());
            fields.put(prefix + ".NodeId", Integer.toString(candidate.rootNodeId()));
            fields.put(prefix + ".RewriteKind", "min-max-to-opencl-clamp");
            fields.put(prefix + ".Successful", "true");
            fields.put(prefix + ".Input.Count", "4");
            fields.put(prefix + ".Input.0.Name", "expression");
            fields.put(prefix + ".Input.0.Value", candidate.expressionText());
            fields.put(prefix + ".Input.1.Name", "value");
            fields.put(prefix + ".Input.1.Value", candidate.argumentTexts().get(0));
            fields.put(prefix + ".Input.2.Name", "low");
            fields.put(prefix + ".Input.2.Value", candidate.argumentTexts().get(1));
            fields.put(prefix + ".Input.3.Name", "high");
            fields.put(prefix + ".Input.3.Value", candidate.argumentTexts().get(2));
            fields.put(prefix + ".Output.Count", "1");
            fields.put(prefix + ".Output.0.Name", TARGET_INTRINSIC);
            fields.put(prefix + ".Output.0.CpuReference", candidate.expressionText());
            fields.put(prefix + ".Output.0.PreOptimization", candidate.expressionText());
            fields.put(prefix + ".Output.0.PostOptimization", candidate.replacementText());
            fields.put(prefix + ".Output.0.Tolerance", "exact-min-max-to-clamp-review");
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

    private static GpuIrTypedBodyGraphPatch.Plan graphPatch(ClampCandidate candidate) {
        return GpuIrTypedBodyGraphPatch.plan(
                candidate.expressionText(),
                candidate.replacementText(),
                GpuIrTypedBodyGraphPatch.intrinsicCall(
                        candidate.rootNodeId(),
                        TARGET_INTRINSIC,
                        "clamp-review",
                        candidate.argumentNodeIds(),
                        "clamp-review"
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
        private int skippedMissingChildReferenceCount;
        private int skippedUnsupportedShapeCount;
        private int skippedBodyTextPatternMissingCount;
        private final ArrayList<ClampCandidate> transformedCandidates = new ArrayList<>();
        private String firstTransformedNode = "none";
        private String firstExpression = "none";
        private String firstReplacement = "none";
        private String firstBlocker = "none";

        private int candidateCount() {
            return candidateKeys.size();
        }

        private void recordCandidate(ClampCandidate candidate) {
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
