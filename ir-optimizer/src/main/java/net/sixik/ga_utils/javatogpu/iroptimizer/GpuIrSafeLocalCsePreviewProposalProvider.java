package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Preview-only local CSE readiness analysis for repeated pure typed expressions.
 */
public final class GpuIrSafeLocalCsePreviewProposalProvider implements GpuIrOptimizationProposalProvider {

    public static final String PROVIDER_ID = GpuIrOptimizerModule.MODULE_ID + ".safe-local-cse-preview";
    public static final String PROVIDER_VERSION = PROVIDER_ID + ":1";

    @Override
    public GpuIrOptimizationProposal propose(GpuIrOptimizationProposalRequest request) {
        IrGpuArtifact original = request.originalArtifact();
        Preview preview = Preview.from(original);
        return new GpuIrOptimizationProposal(
                extensionId(),
                extensionVersion(),
                original,
                Optional.empty(),
                GpuIrOptimizationProposalDecision.NO_CHANGE,
                GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "ir-optimizer.safe-local-cse-preview",
                        preview.verdict(),
                        preview.fields()
                ),
                "",
                List.of(preview.diagnostic())
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
        return 410;
    }

    private record Preview(
            int methodBodyCount,
            int typedBodyCount,
            int expressionCount,
            int candidateExpressionCount,
            int duplicateExpressionCount,
            int equivalenceClassCount,
            int blockedUnsupportedOperatorCount,
            int blockedImpureOperandCount,
            int blockedControlFlowBoundaryCount,
            Map<String, Integer> operatorCounts,
            String firstCandidate,
            String firstBlocker
    ) {

        private static Preview from(IrGpuArtifact artifact) {
            int methodBodyCount = artifact.module().methodBodies().size();
            int typedBodyCount = 0;
            int expressionCount = 0;
            int candidateExpressionCount = 0;
            int duplicateExpressionCount = 0;
            int blockedUnsupportedOperatorCount = 0;
            int blockedImpureOperandCount = 0;
            int blockedControlFlowBoundaryCount = 0;
            LinkedHashMap<String, Integer> operatorCounts = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> expressionCounts = new LinkedHashMap<>();
            LinkedHashMap<String, String> firstExpressionSummary = new LinkedHashMap<>();
            String firstBlocker = "none";

            for (IrGpuMethodBody methodBody : artifact.module().methodBodies()) {
                if (!methodBody.typedBody().available()) {
                    continue;
                }
                typedBodyCount++;
                Map<Integer, IrGpuTypedNode> nodesById = nodesById(methodBody);
                if (hasControlFlowBoundary(methodBody)) {
                    blockedControlFlowBoundaryCount++;
                    if ("none".equals(firstBlocker)) {
                        firstBlocker = "control-flow-boundary-present";
                    }
                }
                for (IrGpuTypedNode node : methodBody.typedBody().nodes()) {
                    if (!"GpuIrBinary".equals(node.kind())) {
                        continue;
                    }
                    expressionCount++;
                    CandidateAnalysis analysis = analyzeCandidate(methodBody.name(), node, nodesById);
                    if (analysis.candidate().isEmpty()) {
                        switch (analysis.blocker()) {
                            case "unsupported-operator" -> blockedUnsupportedOperatorCount++;
                            case "impure-operand" -> blockedImpureOperandCount++;
                            default -> { }
                        }
                        if ("none".equals(firstBlocker) && !"none".equals(analysis.blocker())) {
                            firstBlocker = analysis.blocker();
                        }
                        continue;
                    }
                    Candidate candidate = analysis.candidate().orElseThrow();
                    candidateExpressionCount++;
                    operatorCounts.merge(candidate.operator(), 1, Integer::sum);
                    expressionCounts.merge(candidate.fingerprint(), 1, Integer::sum);
                    firstExpressionSummary.putIfAbsent(candidate.fingerprint(), candidate.summary());
                }
            }

            int equivalenceClassCount = 0;
            String firstCandidate = "none";
            for (Map.Entry<String, Integer> entry : expressionCounts.entrySet()) {
                if (entry.getValue() <= 1) {
                    continue;
                }
                equivalenceClassCount++;
                duplicateExpressionCount += entry.getValue();
                if ("none".equals(firstCandidate)) {
                    firstCandidate = firstExpressionSummary.getOrDefault(entry.getKey(), entry.getKey());
                }
            }
            if (equivalenceClassCount == 0 && "none".equals(firstBlocker)) {
                firstBlocker = candidateExpressionCount == 0 ? "no-pure-local-expressions" : "no-repeated-pure-expressions";
            }

            return new Preview(
                    methodBodyCount,
                    typedBodyCount,
                    expressionCount,
                    candidateExpressionCount,
                    duplicateExpressionCount,
                    equivalenceClassCount,
                    blockedUnsupportedOperatorCount,
                    blockedImpureOperandCount,
                    blockedControlFlowBoundaryCount,
                    Map.copyOf(operatorCounts),
                    firstCandidate,
                    firstBlocker
            );
        }

        private String verdict() {
            return equivalenceClassCount == 0 ? "preview-no-candidates" : "preview-candidates-recorded";
        }

        private String diagnostic() {
            return equivalenceClassCount == 0
                    ? "safe local CSE preview found no repeated pure typed expressions"
                    : "safe local CSE preview recorded " + equivalenceClassCount + " repeated pure expression class(es); no rewrite was proposed";
        }

        private Map<String, String> fields() {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("methodBody.count", Integer.toString(methodBodyCount));
            fields.put("typedBody.count", Integer.toString(typedBodyCount));
            fields.put("expression.count", Integer.toString(expressionCount));
            fields.put("candidateExpression.count", Integer.toString(candidateExpressionCount));
            fields.put("duplicateExpression.count", Integer.toString(duplicateExpressionCount));
            fields.put("equivalenceClass.count", Integer.toString(equivalenceClassCount));
            fields.put("blocked.unsupportedOperator.count", Integer.toString(blockedUnsupportedOperatorCount));
            fields.put("blocked.impureOperand.count", Integer.toString(blockedImpureOperandCount));
            fields.put("blocked.controlFlowBoundary.count", Integer.toString(blockedControlFlowBoundaryCount));
            fields.put("rewrite.proposed", "false");
            fields.put("mutationRequired", "false");
            fields.put("productionAffecting", "false");
            fields.put("previewOnly", "true");
            fields.put("policy.proposalOnly", "true");
            fields.put("policy.mutationAllowed", "false");
            fields.put("policy.rollbackRequired", "true");
            fields.put("policy.proofRequired", "true");
            fields.put("proof.runtimeEquivalenceRequiredBeforeRewrite", "true");
            fields.put("proof.approvalRequiredBeforeRewrite", "true");
            fields.put("safety.dominanceProven", "false");
            fields.put("safety.sideEffectFreedomProven", "false");
            fields.put("safety.valueNumberingScope", "method-local-preview");
            fields.put("operator.counts", countsSummary(operatorCounts));
            fields.put("firstCandidate", firstCandidate);
            fields.put("firstBlocker", equivalenceClassCount == 0 ? firstBlocker : "preview-only-no-rewrite");
            return Map.copyOf(fields);
        }

        private static String countsSummary(Map<String, Integer> counts) {
            if (counts == null || counts.isEmpty()) {
                return "{}";
            }
            return counts.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(",", "{", "}"));
        }
    }

    private record Candidate(String methodName, int nodeId, String operator, String fingerprint) {
        private String summary() {
            return methodName + "#" + nodeId + "=" + fingerprint;
        }
    }

    private record CandidateAnalysis(Optional<Candidate> candidate, String blocker) {
        private static CandidateAnalysis candidate(Candidate candidate) {
            return new CandidateAnalysis(Optional.of(candidate), "none");
        }

        private static CandidateAnalysis blocked(String blocker) {
            return new CandidateAnalysis(Optional.empty(), blocker);
        }
    }

    private static CandidateAnalysis analyzeCandidate(
            String methodName,
            IrGpuTypedNode node,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        String operator = node.attributes().getOrDefault("operator", "");
        if (!List.of("+", "-", "*", "&", "|", "^", "==", "!=", "<", "<=", ">", ">=").contains(operator)) {
            return CandidateAnalysis.blocked("unsupported-operator");
        }
        Optional<String> left = pureFingerprint(node, "left", nodesById);
        Optional<String> right = pureFingerprint(node, "right", nodesById);
        if (left.isEmpty() || right.isEmpty()) {
            return CandidateAnalysis.blocked("impure-operand");
        }
        String fingerprint = "binary(" + operator + "," + left.orElseThrow() + "," + right.orElseThrow() + ")";
        return CandidateAnalysis.candidate(new Candidate(methodName, node.id(), operator, fingerprint));
    }

    private static Optional<String> pureFingerprint(
            IrGpuTypedNode node,
            String childName,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        List<Integer> childIds = node.children().getOrDefault(childName, List.of());
        if (childIds.size() != 1) {
            return Optional.empty();
        }
        IrGpuTypedNode child = nodesById.get(childIds.get(0));
        if (child == null) {
            return Optional.empty();
        }
        if ("GpuIrLiteral".equals(child.kind())) {
            return Optional.of("literal:" + child.attributes().getOrDefault("sourceText", ""));
        }
        if ("GpuIrVariableRef".equals(child.kind())) {
            return Optional.of("var:" + child.attributes().getOrDefault("name", ""));
        }
        if ("GpuIrBinary".equals(child.kind())) {
            CandidateAnalysis nested = analyzeCandidate("nested", child, nodesById);
            return nested.candidate().map(Candidate::fingerprint);
        }
        return Optional.empty();
    }

    private static boolean hasControlFlowBoundary(IrGpuMethodBody methodBody) {
        for (IrGpuTypedNode node : methodBody.typedBody().nodes()) {
            if (List.of("GpuIrIf", "GpuIrForLoop", "GpuIrWhileLoop", "GpuIrDoWhileLoop", "GpuIrSwitch").contains(node.kind())) {
                return true;
            }
        }
        return false;
    }

    private static Map<Integer, IrGpuTypedNode> nodesById(IrGpuMethodBody methodBody) {
        LinkedHashMap<Integer, IrGpuTypedNode> nodes = new LinkedHashMap<>();
        for (IrGpuTypedNode node : methodBody.typedBody().nodes()) {
            nodes.put(node.id(), node);
        }
        return Map.copyOf(nodes);
    }
}
