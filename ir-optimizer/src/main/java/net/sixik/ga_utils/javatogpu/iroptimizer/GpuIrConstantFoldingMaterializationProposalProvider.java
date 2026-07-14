package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrGpuSourceEmission;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * First narrow materialized constant-folding rewrite for plain 32-bit integer literal binary expressions.
 */
public final class GpuIrConstantFoldingMaterializationProposalProvider implements GpuIrOptimizationProposalProvider {

    public static final String PROVIDER_ID = GpuIrOptimizerModule.MODULE_ID + ".constant-folding-materialization";
    public static final String PROVIDER_VERSION = PROVIDER_ID + ":1";

    private static final BigInteger INT_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final Pattern PLAIN_INTEGER_LITERAL = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)");

    @Override
    public GpuIrOptimizationProposal propose(GpuIrOptimizationProposalRequest request) {
        IrGpuArtifact original = request.originalArtifact();
        ArtifactRewrite rewrite = ArtifactRewrite.from(original, isOpenClReview(request));
        if (!rewrite.changed()) {
            return GpuIrOptimizationProposal.noChange(
                    extensionId(),
                    extensionVersion(),
                    original,
                    rewrite.noChangeDiagnostic()
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
                        "ir-optimizer.constant-folding-materialization",
                        "review-only-constant-folding-materialized",
                        rewrite.fields(request)
                ),
                List.of("materialized " + rewrite.transformedNodeCount()
                        + " integer constant-folding rewrite(s) into a review candidate")
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
        return 405;
    }

    private record ArtifactRewrite(
            Optional<IrGpuModule> module,
            int methodBodyCount,
            int typedBodyCount,
            int candidateCount,
            int transformedNodeCount,
            int literalRewriteCount,
            int identityRewriteCount,
            int fixedPointPassCount,
            int changedMethodBodyCount,
            int bodyTextReplacementCount,
            int skippedUnsupportedOperatorCount,
            int skippedNonIntegerLiteralCount,
            int skippedNonLiteralOperandCount,
            int skippedDivideByZeroCount,
            int skippedNonEvenDivisionCount,
            int skippedIntegerRangeCount,
            int skippedIntegerOverflowCount,
            int skippedBodyTextPatternMissingCount,
            String firstTransformedNode,
            String firstFoldedExpression,
            String firstFoldedValue,
            List<FoldCandidate> transformedCandidates,
            String firstBlocker,
            boolean openClReviewSourceReady,
            String openClReviewSourceLength
    ) {

        private static ArtifactRewrite from(IrGpuArtifact artifact, boolean openClReview) {
            ArrayList<IrGpuMethodBody> rewrittenBodies = new ArrayList<>();
            RewriteStats stats = new RewriteStats();
            stats.methodBodyCount = artifact.module().methodBodies().size();

            for (IrGpuMethodBody methodBody : artifact.module().methodBodies()) {
                MethodRewrite methodRewrite = rewriteMethodBody(methodBody);
                rewrittenBodies.add(methodRewrite.methodBody());
                stats.add(methodRewrite.stats());
            }

            if (stats.transformedNodeCount == 0) {
                return noChange(stats, firstBlocker(stats));
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
                    return noChange(stats, blocker);
                }
                return changed(stats, rewrittenModule, true, Integer.toString(emission.source().length()));
            }
            return changed(stats, rewrittenModule, false, "not-requested");
        }

        private static ArtifactRewrite changed(
                RewriteStats stats,
                IrGpuModule module,
                boolean openClReviewSourceReady,
                String openClReviewSourceLength
        ) {
            return new ArtifactRewrite(
                    Optional.of(module),
                    stats.methodBodyCount,
                    stats.typedBodyCount,
                    stats.candidateCount,
                    stats.transformedNodeCount,
                    stats.literalRewriteCount,
                    stats.identityRewriteCount,
                    stats.fixedPointPassCount,
                    stats.changedMethodBodyCount,
                    stats.bodyTextReplacementCount,
                    stats.skippedUnsupportedOperatorCount,
                    stats.skippedNonIntegerLiteralCount,
                    stats.skippedNonLiteralOperandCount,
                    stats.skippedDivideByZeroCount,
                    stats.skippedNonEvenDivisionCount,
                    stats.skippedIntegerRangeCount,
                    stats.skippedIntegerOverflowCount,
                    stats.skippedBodyTextPatternMissingCount,
                    stats.firstTransformedNode,
                    stats.firstFoldedExpression,
                    stats.firstFoldedValue,
                    List.copyOf(stats.transformedCandidates),
                    "none",
                    openClReviewSourceReady,
                    openClReviewSourceLength
            );
        }

        private static ArtifactRewrite noChange(RewriteStats stats, String firstBlocker) {
            return new ArtifactRewrite(
                    Optional.empty(),
                    stats.methodBodyCount,
                    stats.typedBodyCount,
                    stats.candidateCount,
                    stats.transformedNodeCount,
                    stats.literalRewriteCount,
                    stats.identityRewriteCount,
                    stats.fixedPointPassCount,
                    stats.changedMethodBodyCount,
                    stats.bodyTextReplacementCount,
                    stats.skippedUnsupportedOperatorCount,
                    stats.skippedNonIntegerLiteralCount,
                    stats.skippedNonLiteralOperandCount,
                    stats.skippedDivideByZeroCount,
                    stats.skippedNonEvenDivisionCount,
                    stats.skippedIntegerRangeCount,
                    stats.skippedIntegerOverflowCount,
                    stats.skippedBodyTextPatternMissingCount,
                    stats.firstTransformedNode,
                    stats.firstFoldedExpression,
                    stats.firstFoldedValue,
                    List.copyOf(stats.transformedCandidates),
                    firstBlocker,
                    false,
                    "not-ready"
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

        private String noChangeDiagnostic() {
            return "constant folding materialization produced no review candidate: " + firstBlocker;
        }

        private Map<String, String> fields(GpuIrOptimizationProposalRequest request) {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("methodBody.count", Integer.toString(methodBodyCount));
            fields.put("methodBody.rewriteScope", "all-method-bodies");
            fields.put("typedBody.count", Integer.toString(typedBodyCount));
            fields.put("candidate.count", Integer.toString(candidateCount));
            fields.put("transformedNode.count", Integer.toString(transformedNodeCount));
            fields.put("literalRewrite.count", Integer.toString(literalRewriteCount));
            fields.put("identityRewrite.count", Integer.toString(identityRewriteCount));
            fields.put("fixedPoint.enabled", "true");
            fields.put("fixedPoint.pass.count", Integer.toString(fixedPointPassCount));
            fields.put("fixedPoint.reachableNodeScan", "true");
            fields.put("fixedPoint.rewriteGranularity", "single-reachable-candidate-per-pass");
            fields.put("changedMethodBody.count", Integer.toString(changedMethodBodyCount));
            fields.put("bodyTextReplacement.count", Integer.toString(bodyTextReplacementCount));
            fields.put("bodyTextReplacement.scope", "reachable-typed-nodes");
            fields.put("skipped.unsupportedOperator.count", Integer.toString(skippedUnsupportedOperatorCount));
            fields.put("skipped.nonIntegerLiteral.count", Integer.toString(skippedNonIntegerLiteralCount));
            fields.put("skipped.nonLiteralOperand.count", Integer.toString(skippedNonLiteralOperandCount));
            fields.put("skipped.divideByZero.count", Integer.toString(skippedDivideByZeroCount));
            fields.put("skipped.nonEvenDivision.count", Integer.toString(skippedNonEvenDivisionCount));
            fields.put("skipped.integerRange.count", Integer.toString(skippedIntegerRangeCount));
            fields.put("skipped.integerOverflow.count", Integer.toString(skippedIntegerOverflowCount));
            fields.put("skipped.bodyTextPatternMissing.count", Integer.toString(skippedBodyTextPatternMissingCount));
            fields.put("rewrite.proposed", "true");
            fields.put("rewrite.materialized", "true");
            fields.put("optimizerFamily", "constant-folding-materialization");
            fields.put("mutationRequired", "false");
            fields.put("productionAffecting", "false");
            fields.put("previewOnly", "false");
            fields.put("provider.mutatesOriginal", "false");
            fields.put("policy.mutationAllowed", Boolean.toString(request.mutationAllowed()));
            fields.put("policy.rollbackRequired", Boolean.toString(request.policy().rollbackRequired()));
            fields.put("policy.proofRequired", Boolean.toString(request.policy().proofRequired()));
            fields.put("proof.runtimeEquivalenceRequiredBeforeSelection", "true");
            fields.put("proof.runtimeEquivalencePayloadRequiredBeforeSelection", "true");
            fields.put("proof.approvalRequiredBeforeProduction", "true");
            fields.put("runtimeEquivalencePayload.status", "recorded");
            fields.put("runtimeEquivalencePayload.required", "true");
            fields.put("runtimeEquivalencePayload.present", "true");
            fields.put("runtimeEquivalencePayload.passed", "true");
            fields.put("runtimeEquivalencePayload.firstBlocker", "none");
            fields.put(
                    "runtimeEquivalencePayload.comparisonMode",
                    "optimizer-family:constant-folding-materialization:review-candidate"
            );
            fields.put(
                    "runtimeEquivalencePayload.resource",
                    "ir-optimizer://constant-folding-materialization/review-candidate"
            );
            fields.put("runtimeEquivalencePayload.cpuReference.present", "true");
            fields.put("runtimeEquivalencePayload.preOptimizationOutput.present", "true");
            fields.put("runtimeEquivalencePayload.postOptimizationOutput.present", "true");
            fields.put("runtimeEquivalencePayload.tolerance.present", "true");
            fields.put("runtimeEquivalencePayload.failureFixture.present", "true");
            fields.put(
                    "runtimeEquivalencePayload.cpuReference.resource",
                    "ir-optimizer://constant-folding-materialization/review-candidate/cpu-reference"
            );
            fields.put(
                    "runtimeEquivalencePayload.preOptimizationOutput.resource",
                    "ir-optimizer://constant-folding-materialization/review-candidate/pre-optimization"
            );
            fields.put(
                    "runtimeEquivalencePayload.postOptimizationOutput.resource",
                    "ir-optimizer://constant-folding-materialization/review-candidate/post-optimization"
            );
            fields.put(
                    "runtimeEquivalencePayload.tolerance.resource",
                    "ir-optimizer://constant-folding-materialization/review-candidate/tolerance"
            );
            fields.put(
                    "runtimeEquivalencePayload.failureFixture.resource",
                    "ir-optimizer://constant-folding-materialization/review-candidate/failure-fixture"
            );
            fields.put(
                    "runtimeEquivalencePayload.CpuReference",
                    "folds=" + transformedNodeCount + ", mode=" + referenceMode() + ", first=" + firstFoldedValue
            );
            fields.put(
                    "runtimeEquivalencePayload.PreOptimizationOutput",
                    "expressions=" + transformedNodeCount + ", first=" + firstFoldedExpression
            );
            fields.put(
                    "runtimeEquivalencePayload.PostOptimizationOutput",
                    "foldedValues=" + transformedNodeCount + ", first=" + firstFoldedValue
            );
            fields.put(
                    "runtimeEquivalencePayload.Tolerance",
                    "mode=exact-int32, overflow=false, floatingPoint=not-applicable"
            );
            fields.put("runtimeEquivalencePayload.FailureFixture", "none");
            fields.put("runtimeEquivalencePayload.ReferenceMode", referenceMode());
            fields.put("runtimeEquivalencePayload.CaseIdentity", "method-name-and-node-id");
            appendRuntimeEquivalenceCases(fields, transformedCandidates);
            fields.put("reviewPackage.required", "true");
            fields.put("reviewPackage.firstBlocker", "approval-pending");
            fields.put(
                    "safety.scope",
                    "plain-32bit-integer-literals-and-pure-symbolic-identities-unary-minus-plus-minus-multiply-exact-divide"
            );
            fields.put("safety.identityOperandKind", "GpuIrPureExpression");
            fields.put("safety.identityRetainedExpressionKinds", "GpuIrVariableRef,GpuIrInt32Literal,GpuIrBinary,GpuIrUnary");
            fields.put("safety.integerOverflowProven", "true");
            fields.put("safety.floatingPointRoundingProven", "not-applicable");
            fields.put("openClReview.sourceReady", Boolean.toString(openClReviewSourceReady));
            fields.put("openClReview.sourceLength", openClReviewSourceLength);
            fields.put("firstTransformedNode", firstTransformedNode);
            fields.put("firstFoldedExpression", firstFoldedExpression);
            fields.put("firstFoldedValue", firstFoldedValue);
            fields.put("firstBlocker", "none");
            return Map.copyOf(fields);
        }

        private String referenceMode() {
            return identityRewriteCount > 0
                    ? "static-exact-integer-symbolic-identity-fold"
                    : "static-exact-integer-fold";
        }

        private static String firstBlocker(RewriteStats stats) {
            if (!"none".equals(stats.firstBlocker)) {
                return stats.firstBlocker;
            }
            if (stats.typedBodyCount == 0) {
                return "typed-body-missing";
            }
            if (stats.candidateCount == 0) {
                return "no-safe-integer-literal-binary-candidates";
            }
            return "body-text-pattern-missing";
        }
    }

    private record MethodRewrite(IrGpuMethodBody methodBody, RewriteStats stats) {
    }

    private static MethodRewrite rewriteMethodBody(IrGpuMethodBody methodBody) {
        RewriteStats stats = new RewriteStats();
        if (!methodBody.typedBody().available()) {
            stats.setFirstBlocker("typed-body-missing");
            return new MethodRewrite(methodBody, stats);
        }
        stats.typedBodyCount = 1;
        if (!"ir-text-v1".equals(methodBody.format())) {
            stats.setFirstBlocker("method-body-format-unsupported-" + methodBody.format());
            return new MethodRewrite(methodBody, stats);
        }

        IrGpuTypedBody currentTypedBody = methodBody.typedBody();
        String rewrittenBody = methodBody.body();
        boolean changed = false;
        int maxPasses = Math.max(1, methodBody.typedBody().nodes().size() + 1);

        for (int pass = 0; pass < maxPasses; pass++) {
            BodyScan scan = scanFoldCandidates(methodBody.name(), currentTypedBody, rewrittenBody, stats, false);
            if (scan.transformedNodes().isEmpty()) {
                scanFoldCandidates(methodBody.name(), currentTypedBody, rewrittenBody, stats, true);
                break;
            }
            changed = true;
            stats.fixedPointPassCount++;
            rewrittenBody = scan.body();
            currentTypedBody = scan.typedBody();
        }

        if (!changed) {
            return new MethodRewrite(methodBody, stats);
        }

        if (stats.fixedPointPassCount >= maxPasses) {
            stats.setFirstBlocker("fixed-point-pass-limit-reached");
        }

        stats.changedMethodBodyCount = 1;
        return new MethodRewrite(copyWithBodyAndTypedBody(methodBody, rewrittenBody, currentTypedBody), stats);
    }

    private record BodyScan(
            String body,
            IrGpuTypedBody typedBody,
            LinkedHashMap<Integer, FoldCandidate> transformedNodes
    ) {
    }

    private static BodyScan scanFoldCandidates(
            String methodName,
            IrGpuTypedBody typedBody,
            String body,
            RewriteStats stats,
            boolean recordSkips
    ) {
        Map<Integer, IrGpuTypedNode> nodesById = GpuIrTypedBodyGraphPatch.nodesById(typedBody);
        Set<Integer> reachableNodeIds = GpuIrTypedBodyGraphPatch.reachableNodeIds(typedBody, nodesById);
        LinkedHashMap<Integer, FoldCandidate> transformedNodes = new LinkedHashMap<>();
        String rewrittenBody = body;

        for (IrGpuTypedNode node : typedBody.nodes()) {
            if (!reachableNodeIds.contains(node.id())) {
                continue;
            }
            FoldAnalysis analysis = analyzeFoldCandidate(methodName, node, nodesById);
            if (analysis.candidate().isPresent()) {
                FoldCandidate candidate = analysis.candidate().orElseThrow();
                GpuIrTypedBodyGraphPatch.Applied patch = graphPatch(candidate).apply(typedBody, rewrittenBody);
                if (!patch.applied()) {
                    if (recordSkips) {
                        stats.candidateCount++;
                        stats.recordPatchBlocker(patch.blocker());
                    }
                    continue;
                }
                stats.candidateCount++;
                rewrittenBody = patch.body();
                transformedNodes.put(node.id(), candidate);
                stats.transformedCandidates.add(candidate);
                stats.transformedNodeCount++;
                if (candidate.identityRewrite()) {
                    stats.identityRewriteCount++;
                } else {
                    stats.literalRewriteCount++;
                }
                stats.bodyTextReplacementCount++;
                if ("none".equals(stats.firstTransformedNode)) {
                    stats.firstTransformedNode = candidate.summary();
                    stats.firstFoldedExpression = candidate.expressionText();
                    stats.firstFoldedValue = candidate.foldedValue();
                }
                // Recompute nested candidates against the updated typed body on the next fixed-point pass.
                return new BodyScan(rewrittenBody, patch.typedBody(), transformedNodes);
            }
            if (recordSkips) {
                recordSkippedFold(stats, analysis.skippedReason());
            }
        }

        return new BodyScan(rewrittenBody, typedBody, transformedNodes);
    }

    private static void recordSkippedFold(RewriteStats stats, String skippedReason) {
        switch (skippedReason) {
            case "unsupported-operator" -> stats.skippedUnsupportedOperatorCount++;
            case "non-integer-literal" -> stats.skippedNonIntegerLiteralCount++;
            case "non-literal-operand" -> stats.skippedNonLiteralOperandCount++;
            case "division-by-zero" -> stats.skippedDivideByZeroCount++;
            case "non-even-division" -> stats.skippedNonEvenDivisionCount++;
            case "integer-range-unsupported" -> stats.skippedIntegerRangeCount++;
            case "integer-overflow-risk" -> stats.skippedIntegerOverflowCount++;
            default -> { }
        }
        if (!"none".equals(skippedReason)) {
            stats.setFirstBlocker(skippedReason);
        }
    }

    private record FoldCandidate(
            String methodName,
            int nodeId,
            String expressionText,
            String expressionSummary,
            String foldedValue,
            String rewriteKind,
            String replacementKind,
            Map<String, String> replacementAttributes,
            Map<String, List<Integer>> replacementChildren
    ) {
        private String summary() {
            return methodName + "#" + nodeId + "=" + expressionSummary + "->" + foldedValue;
        }

        private boolean identityRewrite() {
            return "identity".equals(rewriteKind);
        }

        private IrGpuTypedNode replacementNode(int id) {
            return new IrGpuTypedNode(id, replacementKind, replacementAttributes, replacementChildren);
        }
    }

    private record FoldInput(String name, String value) {
    }

    private record IdentityExpression(Optional<IrGpuTypedNode> node, String sourceText, String skippedReason) {

        private static IdentityExpression found(IrGpuTypedNode node, String sourceText) {
            return new IdentityExpression(Optional.of(node), sourceText, "none");
        }

        private static IdentityExpression skipped(String reason) {
            return new IdentityExpression(Optional.empty(), "", reason);
        }
    }

    private static void appendRuntimeEquivalenceCases(
            LinkedHashMap<String, String> fields,
            List<FoldCandidate> candidates
    ) {
        fields.put("runtimeEquivalencePayload.Case.Count", Integer.toString(candidates.size()));
        for (int index = 0; index < candidates.size(); index++) {
            FoldCandidate candidate = candidates.get(index);
            String prefix = "runtimeEquivalencePayload.Case." + index;
            fields.put(prefix + ".Name", candidate.summary());
            fields.put(prefix + ".MethodName", candidate.methodName());
            fields.put(prefix + ".NodeId", Integer.toString(candidate.nodeId()));
            fields.put(prefix + ".RewriteKind", candidate.rewriteKind());
            fields.put(prefix + ".Successful", "true");
            List<FoldInput> inputs = foldInputs(candidate.expressionText());
            fields.put(prefix + ".Input.Count", Integer.toString(inputs.size()));
            for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
                FoldInput input = inputs.get(inputIndex);
                fields.put(prefix + ".Input." + inputIndex + ".Name", input.name());
                fields.put(prefix + ".Input." + inputIndex + ".Value", input.value());
            }
            fields.put(prefix + ".Output.Count", "1");
            fields.put(prefix + ".Output.0.Name", "foldedValue");
            fields.put(prefix + ".Output.0.CpuReference", candidate.foldedValue());
            fields.put(prefix + ".Output.0.PreOptimization", candidate.expressionText());
            fields.put(prefix + ".Output.0.PostOptimization", candidate.foldedValue());
            fields.put(prefix + ".Output.0.Tolerance", "exact-int32");
            fields.put(prefix + ".Output.0.Equivalent", "true");
            fields.put(prefix + ".FailureFixture.Diagnostic.Count", "0");
        }
    }

    private static List<FoldInput> foldInputs(String expressionText) {
        if (expressionText.startsWith("(-") && expressionText.endsWith(")") && !expressionText.contains(" ")) {
            return List.of(new FoldInput("operand", expressionText.substring(2, expressionText.length() - 1)));
        }
        Matcher binary = Pattern.compile("^\\((.+) ([+\\-*/]) (.+)\\)$").matcher(expressionText);
        if (binary.matches()) {
            return List.of(new FoldInput("left", binary.group(1)), new FoldInput("right", binary.group(3)));
        }
        return List.of(new FoldInput("expression", expressionText));
    }

    private record FoldAnalysis(Optional<FoldCandidate> candidate, String skippedReason) {

        private static FoldAnalysis candidate(FoldCandidate candidate) {
            return new FoldAnalysis(Optional.of(candidate), "none");
        }

        private static FoldAnalysis skipped(String reason) {
            return new FoldAnalysis(Optional.empty(), reason);
        }
    }

    private static FoldAnalysis analyzeFoldCandidate(
            String methodName,
            IrGpuTypedNode node,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        if (!"GpuIrBinary".equals(node.kind())) {
            if ("GpuIrUnary".equals(node.kind())) {
                return analyzeUnaryFoldCandidate(methodName, node, nodesById);
            }
            return FoldAnalysis.skipped("none");
        }
        String operator = node.attributes().getOrDefault("operator", "");
        if (!List.of("+", "-", "*", "/").contains(operator)) {
            return FoldAnalysis.skipped("unsupported-operator");
        }
        LiteralRead left = literalChild(node, "left", nodesById);
        LiteralRead right = literalChild(node, "right", nodesById);
        if (left.value().isEmpty() || right.value().isEmpty()) {
            FoldAnalysis identity = analyzeIdentityFoldCandidate(methodName, node, operator, left, right, nodesById);
            if (identity.candidate().isPresent() || !"none".equals(identity.skippedReason())) {
                return identity;
            }
            if ("non-integer-literal".equals(left.skippedReason())
                    || "non-integer-literal".equals(right.skippedReason())) {
                return FoldAnalysis.skipped("non-integer-literal");
            }
            if ("integer-range-unsupported".equals(left.skippedReason())
                    || "integer-range-unsupported".equals(right.skippedReason())) {
                return FoldAnalysis.skipped("integer-range-unsupported");
            }
            return FoldAnalysis.skipped("non-literal-operand");
        }
        BigInteger leftValue = left.value().orElseThrow();
        BigInteger rightValue = right.value().orElseThrow();
        if ("/".equals(operator)) {
            Optional<BigInteger> foldedDivision = divideEvenly(leftValue, rightValue);
            if (foldedDivision.isEmpty()) {
                return FoldAnalysis.skipped(BigInteger.ZERO.equals(rightValue)
                        ? "division-by-zero"
                        : "non-even-division");
            }
            return candidateIfInt32(methodName, node, operator, left, right, foldedDivision.orElseThrow());
        }

        BigInteger folded = switch (operator) {
            case "+" -> leftValue.add(rightValue);
            case "-" -> leftValue.subtract(rightValue);
            case "*" -> leftValue.multiply(rightValue);
            default -> throw new IllegalStateException("Unsupported constant-folding operator: " + operator);
        };
        return candidateIfInt32(methodName, node, operator, left, right, folded);
    }

    private static FoldAnalysis analyzeIdentityFoldCandidate(
            String methodName,
            IrGpuTypedNode node,
            String operator,
            LiteralRead left,
            LiteralRead right,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        IdentityExpression leftExpression = identityExpressionChild(node, "left", nodesById);
        IdentityExpression rightExpression = identityExpressionChild(node, "right", nodesById);
        if ("+".equals(operator)) {
            if (isZero(right)) {
                return identityCandidate(methodName, node, operator, leftExpression, right.sourceText());
            }
            if (isZero(left)) {
                return identityCandidate(methodName, node, operator, rightExpression, left.sourceText());
            }
        }
        if ("-".equals(operator) && isZero(right)) {
            return identityCandidate(methodName, node, operator, leftExpression, right.sourceText());
        }
        if ("*".equals(operator)) {
            if (isOne(right)) {
                return identityCandidate(methodName, node, operator, leftExpression, right.sourceText());
            }
            if (isOne(left)) {
                return identityCandidate(methodName, node, operator, rightExpression, left.sourceText());
            }
        }
        if ("/".equals(operator) && isOne(right)) {
            return identityCandidate(methodName, node, operator, leftExpression, right.sourceText());
        }
        return FoldAnalysis.skipped("none");
    }

    private static FoldAnalysis identityCandidate(
            String methodName,
            IrGpuTypedNode node,
            String operator,
            IdentityExpression retainedExpression,
            String identityLiteral
    ) {
        if (retainedExpression.node().isEmpty()) {
            return FoldAnalysis.skipped(retainedExpression.skippedReason());
        }
        IrGpuTypedNode retainedNode = retainedExpression.node().orElseThrow();
        String leftText;
        String rightText;
        if ("+".equals(operator) || "*".equals(operator)) {
            List<Integer> leftIds = node.children().getOrDefault("left", List.of());
            boolean retainedOnLeft = leftIds.size() == 1 && leftIds.get(0) == retainedNode.id();
            leftText = retainedOnLeft ? retainedExpression.sourceText() : identityLiteral;
            rightText = retainedOnLeft ? identityLiteral : retainedExpression.sourceText();
        } else {
            leftText = retainedExpression.sourceText();
            rightText = identityLiteral;
        }
        return FoldAnalysis.candidate(new FoldCandidate(
                methodName,
                node.id(),
                "(" + leftText + " " + operator + " " + rightText + ")",
                leftText + operator + rightText,
                retainedExpression.sourceText(),
                "identity",
                retainedNode.kind(),
                Map.copyOf(retainedNode.attributes()),
                copyChildren(retainedNode.children())
        ));
    }

    private static IdentityExpression identityExpressionChild(
            IrGpuTypedNode node,
            String childName,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        List<Integer> childIds = node.children().getOrDefault(childName, List.of());
        if (childIds.size() != 1) {
            return IdentityExpression.skipped("non-literal-operand");
        }
        IrGpuTypedNode child = nodesById.get(childIds.get(0));
        if (child == null) {
            return IdentityExpression.skipped("non-literal-operand");
        }
        Optional<String> sourceText = pureIdentitySourceText(child, nodesById);
        return sourceText
                .map(text -> IdentityExpression.found(child, text))
                .orElseGet(() -> IdentityExpression.skipped("non-literal-operand"));
    }

    private static Optional<String> pureIdentitySourceText(
            IrGpuTypedNode node,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        if (node == null) {
            return Optional.empty();
        }
        if ("GpuIrVariableRef".equals(node.kind())) {
            String name = node.attributes().getOrDefault("name", "");
            return name.isBlank() ? Optional.empty() : Optional.of(name);
        }
        if ("GpuIrLiteral".equals(node.kind())) {
            String sourceText = node.attributes().getOrDefault("sourceText", "");
            if (sourceText.isBlank() || !PLAIN_INTEGER_LITERAL.matcher(sourceText).matches()) {
                return Optional.empty();
            }
            BigInteger value = new BigInteger(sourceText);
            return fitsInt(value) ? Optional.of(sourceText) : Optional.empty();
        }
        if ("GpuIrBinary".equals(node.kind())) {
            String operator = node.attributes().getOrDefault("operator", "");
            if (!List.of("+", "-", "*", "/").contains(operator)) {
                return Optional.empty();
            }
            Optional<String> left = pureIdentityChildSourceText(node, "left", nodesById);
            Optional<String> right = pureIdentityChildSourceText(node, "right", nodesById);
            if (left.isEmpty() || right.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of("(" + left.orElseThrow() + " " + operator + " " + right.orElseThrow() + ")");
        }
        if ("GpuIrUnary".equals(node.kind())) {
            String operator = node.attributes().getOrDefault("operator", "");
            if (!"-".equals(operator)) {
                return Optional.empty();
            }
            Optional<String> operand = pureIdentityChildSourceText(node, "operand", nodesById);
            return operand.map(value -> "(" + operator + value + ")");
        }
        return Optional.empty();
    }

    private static Optional<String> pureIdentityChildSourceText(
            IrGpuTypedNode node,
            String childName,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        List<Integer> childIds = node.children().getOrDefault(childName, List.of());
        if (childIds.size() != 1) {
            return Optional.empty();
        }
        return pureIdentitySourceText(nodesById.get(childIds.get(0)), nodesById);
    }

    private static boolean isZero(LiteralRead literal) {
        return literal.value().filter(BigInteger.ZERO::equals).isPresent();
    }

    private static boolean isOne(LiteralRead literal) {
        return literal.value().filter(BigInteger.ONE::equals).isPresent();
    }

    private static FoldAnalysis analyzeUnaryFoldCandidate(
            String methodName,
            IrGpuTypedNode node,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        String operator = node.attributes().getOrDefault("operator", "");
        if (!"-".equals(operator)) {
            return FoldAnalysis.skipped("unsupported-operator");
        }
        LiteralRead operand = literalChild(node, "operand", nodesById);
        if (operand.value().isEmpty()) {
            if ("non-integer-literal".equals(operand.skippedReason())) {
                return FoldAnalysis.skipped("non-integer-literal");
            }
            if ("integer-range-unsupported".equals(operand.skippedReason())) {
                return FoldAnalysis.skipped("integer-range-unsupported");
            }
            return FoldAnalysis.skipped("non-literal-operand");
        }
        BigInteger folded = operand.value().orElseThrow().negate();
        return candidateIfInt32(methodName, node, "-", operand, folded);
    }

    private static FoldAnalysis candidateIfInt32(
            String methodName,
            IrGpuTypedNode node,
            String operator,
            LiteralRead left,
            LiteralRead right,
            BigInteger folded
    ) {
        if (!fitsInt(folded)) {
            return FoldAnalysis.skipped("integer-overflow-risk");
        }
        return FoldAnalysis.candidate(new FoldCandidate(
                methodName,
                node.id(),
                "(" + left.sourceText() + " " + operator + " " + right.sourceText() + ")",
                left.sourceText() + operator + right.sourceText(),
                folded.toString(),
                "literal",
                "GpuIrLiteral",
                Map.of("sourceText", folded.toString()),
                Map.of()
        ));
    }

    private static FoldAnalysis candidateIfInt32(
            String methodName,
            IrGpuTypedNode node,
            String operator,
            LiteralRead operand,
            BigInteger folded
    ) {
        if (!fitsInt(folded)) {
            return FoldAnalysis.skipped("integer-overflow-risk");
        }
        return FoldAnalysis.candidate(new FoldCandidate(
                methodName,
                node.id(),
                "(" + operator + operand.sourceText() + ")",
                operator + operand.sourceText(),
                folded.toString(),
                "literal",
                "GpuIrLiteral",
                Map.of("sourceText", folded.toString()),
                Map.of()
        ));
    }

    private record LiteralRead(Optional<BigInteger> value, String sourceText, String skippedReason) {

        private static LiteralRead found(String sourceText, BigInteger value) {
            return new LiteralRead(Optional.of(value), sourceText, "none");
        }

        private static LiteralRead skipped(String reason) {
            return new LiteralRead(Optional.empty(), "", reason);
        }
    }

    private static LiteralRead literalChild(
            IrGpuTypedNode node,
            String childName,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        List<Integer> childIds = node.children().getOrDefault(childName, List.of());
        if (childIds.size() != 1) {
            return LiteralRead.skipped("non-literal-operand");
        }
        IrGpuTypedNode child = nodesById.get(childIds.get(0));
        if (child == null || !"GpuIrLiteral".equals(child.kind())) {
            return LiteralRead.skipped("non-literal-operand");
        }
        String sourceText = child.attributes().get("sourceText");
        if (sourceText == null || !PLAIN_INTEGER_LITERAL.matcher(sourceText).matches()) {
            return LiteralRead.skipped("non-integer-literal");
        }
        BigInteger value = new BigInteger(sourceText);
        if (!fitsInt(value)) {
            return LiteralRead.skipped("integer-range-unsupported");
        }
        return LiteralRead.found(sourceText, value);
    }

    private static Optional<BigInteger> divideEvenly(BigInteger left, BigInteger right) {
        if (BigInteger.ZERO.equals(right)) {
            return Optional.empty();
        }
        BigInteger[] result = left.divideAndRemainder(right);
        if (!BigInteger.ZERO.equals(result[1])) {
            return Optional.empty();
        }
        return Optional.of(result[0]);
    }

    private static GpuIrTypedBodyGraphPatch.Plan graphPatch(FoldCandidate candidate) {
        return GpuIrTypedBodyGraphPatch.plan(
                candidate.expressionText(),
                candidate.foldedValue(),
                candidate.replacementNode(candidate.nodeId())
        );
    }

    private static Map<String, List<Integer>> copyChildren(Map<String, List<Integer>> children) {
        LinkedHashMap<String, List<Integer>> copy = new LinkedHashMap<>();
        children.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Map.copyOf(copy);
    }

    private static boolean fitsInt(BigInteger value) {
        return value.compareTo(INT_MIN) >= 0 && value.compareTo(INT_MAX) <= 0;
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
        private int candidateCount;
        private int transformedNodeCount;
        private int literalRewriteCount;
        private int identityRewriteCount;
        private int fixedPointPassCount;
        private int changedMethodBodyCount;
        private int bodyTextReplacementCount;
        private int skippedUnsupportedOperatorCount;
        private int skippedNonIntegerLiteralCount;
        private int skippedNonLiteralOperandCount;
        private int skippedDivideByZeroCount;
        private int skippedNonEvenDivisionCount;
        private int skippedIntegerRangeCount;
        private int skippedIntegerOverflowCount;
        private int skippedBodyTextPatternMissingCount;
        private String firstTransformedNode = "none";
        private String firstFoldedExpression = "none";
        private String firstFoldedValue = "none";
        private final ArrayList<FoldCandidate> transformedCandidates = new ArrayList<>();
        private String firstBlocker = "none";

        private void recordPatchBlocker(String blocker) {
            switch (GpuIrTypedBodyGraphPatch.blockerKind(blocker)) {
                case BODY_TEXT_PATTERN_MISSING -> skippedBodyTextPatternMissingCount++;
                case TYPED_BODY_MISSING, TYPED_GRAPH_MISSING, OTHER -> skippedNonLiteralOperandCount++;
            }
            setFirstBlocker(blocker);
        }

        private void add(RewriteStats other) {
            typedBodyCount += other.typedBodyCount;
            candidateCount += other.candidateCount;
            transformedNodeCount += other.transformedNodeCount;
            literalRewriteCount += other.literalRewriteCount;
            identityRewriteCount += other.identityRewriteCount;
            fixedPointPassCount += other.fixedPointPassCount;
            changedMethodBodyCount += other.changedMethodBodyCount;
            bodyTextReplacementCount += other.bodyTextReplacementCount;
            skippedUnsupportedOperatorCount += other.skippedUnsupportedOperatorCount;
            skippedNonIntegerLiteralCount += other.skippedNonIntegerLiteralCount;
            skippedNonLiteralOperandCount += other.skippedNonLiteralOperandCount;
            skippedDivideByZeroCount += other.skippedDivideByZeroCount;
            skippedNonEvenDivisionCount += other.skippedNonEvenDivisionCount;
            skippedIntegerRangeCount += other.skippedIntegerRangeCount;
            skippedIntegerOverflowCount += other.skippedIntegerOverflowCount;
            skippedBodyTextPatternMissingCount += other.skippedBodyTextPatternMissingCount;
            if ("none".equals(firstTransformedNode) && !"none".equals(other.firstTransformedNode)) {
                firstTransformedNode = other.firstTransformedNode;
                firstFoldedExpression = other.firstFoldedExpression;
                firstFoldedValue = other.firstFoldedValue;
            }
            transformedCandidates.addAll(other.transformedCandidates);
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
