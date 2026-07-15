package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Preview-only constant-folding analysis for simple typed literal binary expressions.
 */
public final class GpuIrConstantFoldingPreviewProposalProvider implements GpuIrOptimizationProposalProvider {

    public static final String PROVIDER_ID = GpuIrOptimizerModule.MODULE_ID + ".constant-folding-preview";
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
                        "ir-optimizer.constant-folding-preview",
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
        return 400;
    }

    private record Preview(
            int methodBodyCount,
            int typedBodyCount,
            int candidateCount,
            int skippedNonPlainLiteralCount,
            int skippedDivideByZeroCount,
            int skippedNonEvenDivisionCount,
            int skippedUnsupportedOperatorCount,
            int skippedNonLiteralOperandCount,
            Map<String, Integer> operatorCounts,
            Map<String, Integer> numericKindCounts,
            String firstCandidate,
            String firstOperator,
            String firstNumericKind,
            String firstFoldedValue,
            String firstSkippedReason
    ) {

        private static Preview from(IrGpuArtifact artifact) {
            int methodBodyCount = artifact.module().methodBodies().size();
            int typedBodyCount = 0;
            int candidateCount = 0;
            int skippedNonPlainLiteralCount = 0;
            int skippedDivideByZeroCount = 0;
            int skippedNonEvenDivisionCount = 0;
            int skippedUnsupportedOperatorCount = 0;
            int skippedNonLiteralOperandCount = 0;
            LinkedHashMap<String, Integer> operatorCounts = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> numericKindCounts = new LinkedHashMap<>();
            String firstCandidate = "none";
            String firstOperator = "none";
            String firstNumericKind = "none";
            String firstFoldedValue = "none";
            String firstSkippedReason = "none";
            for (IrGpuMethodBody methodBody : artifact.module().methodBodies()) {
                if (!methodBody.typedBody().available()) {
                    continue;
                }
                typedBodyCount++;
                Map<Integer, IrGpuTypedNode> nodesById = nodesById(methodBody);
                for (IrGpuTypedNode node : methodBody.typedBody().nodes()) {
                    FoldAnalysis analysis = analyzeFoldCandidate(methodBody.name(), node, nodesById);
                    if (analysis.candidate().isEmpty()) {
                        switch (analysis.skippedReason()) {
                            case "non-plain-literal" -> skippedNonPlainLiteralCount++;
                            case "division-by-zero" -> skippedDivideByZeroCount++;
                            case "non-even-division" -> skippedNonEvenDivisionCount++;
                            case "unsupported-operator" -> skippedUnsupportedOperatorCount++;
                            case "non-literal-operand" -> skippedNonLiteralOperandCount++;
                            default -> { }
                        }
                        if ("none".equals(firstSkippedReason) && !"none".equals(analysis.skippedReason())) {
                            firstSkippedReason = analysis.skippedReason();
                        }
                        continue;
                    }
                    FoldCandidate candidate = analysis.candidate().orElseThrow();
                    candidateCount++;
                    operatorCounts.merge(candidate.operator(), 1, Integer::sum);
                    numericKindCounts.merge(candidate.numericKind(), 1, Integer::sum);
                    if ("none".equals(firstCandidate)) {
                        firstCandidate = candidate.summary();
                        firstOperator = candidate.operator();
                        firstNumericKind = candidate.numericKind();
                        firstFoldedValue = candidate.foldedValue();
                    }
                }
            }
            return new Preview(
                    methodBodyCount,
                    typedBodyCount,
                    candidateCount,
                    skippedNonPlainLiteralCount,
                    skippedDivideByZeroCount,
                    skippedNonEvenDivisionCount,
                    skippedUnsupportedOperatorCount,
                    skippedNonLiteralOperandCount,
                    Map.copyOf(operatorCounts),
                    Map.copyOf(numericKindCounts),
                    firstCandidate,
                    firstOperator,
                    firstNumericKind,
                    firstFoldedValue,
                    firstSkippedReason
            );
        }

        private String verdict() {
            return candidateCount == 0 ? "preview-no-candidates" : "preview-candidates-recorded";
        }

        private String diagnostic() {
            return candidateCount == 0
                    ? "constant folding preview found no simple typed literal binary candidates"
                    : "constant folding preview recorded " + candidateCount + " simple typed literal binary candidate(s); no rewrite was proposed";
        }

        private Map<String, String> fields() {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("methodBody.count", Integer.toString(methodBodyCount));
            fields.put("typedBody.count", Integer.toString(typedBodyCount));
            fields.put("candidate.count", Integer.toString(candidateCount));
            fields.put("skipped.nonPlainLiteral.count", Integer.toString(skippedNonPlainLiteralCount));
            fields.put("skipped.divideByZero.count", Integer.toString(skippedDivideByZeroCount));
            fields.put("skipped.nonEvenDivision.count", Integer.toString(skippedNonEvenDivisionCount));
            fields.put("skipped.unsupportedOperator.count", Integer.toString(skippedUnsupportedOperatorCount));
            fields.put("skipped.nonLiteralOperand.count", Integer.toString(skippedNonLiteralOperandCount));
            fields.put("rewrite.proposed", "false");
            fields.put("mutationRequired", "false");
            fields.put("productionAffecting", "false");
            fields.put("previewOnly", "true");
            fields.put("policy.proposalOnly", "true");
            fields.put("policy.mutationAllowed", "false");
            fields.put("policy.fastMathAllowed", "false");
            fields.put("policy.rollbackRequired", "true");
            fields.put("policy.proofRequired", "true");
            fields.put("proof.runtimeEquivalenceRequiredBeforeRewrite", "true");
            fields.put("proof.approvalRequiredBeforeRewrite", "true");
            fields.put("safety.integerOverflowProven", "false");
            fields.put("safety.floatingPointRoundingProven", "false");
            fields.put("operator.counts", countsSummary(operatorCounts));
            fields.put("numericKind.counts", countsSummary(numericKindCounts));
            fields.put("firstCandidate", firstCandidate);
            fields.put("firstOperator", firstOperator);
            fields.put("firstNumericKind", firstNumericKind);
            fields.put("firstFoldedValue", firstFoldedValue);
            fields.put("firstSkippedReason", firstSkippedReason);
            fields.put("firstBlocker", candidateCount == 0 ? "no-simple-literal-binary-candidates" : "preview-only-no-rewrite");
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

    private record FoldCandidate(
            String methodName,
            int nodeId,
            String operator,
            String left,
            String right,
            String numericKind,
            String foldedValue
    ) {
        private String summary() {
            return methodName + "#" + nodeId + "=" + left + operator + right;
        }
    }

    private record FoldAnalysis(Optional<FoldCandidate> candidate, String skippedReason) {

        private static FoldAnalysis candidate(FoldCandidate candidate) {
            return new FoldAnalysis(Optional.of(candidate), "none");
        }

        private static FoldAnalysis skipped(String reason) {
            return new FoldAnalysis(Optional.empty(), reason);
        }
    }

    private static Map<Integer, IrGpuTypedNode> nodesById(IrGpuMethodBody methodBody) {
        LinkedHashMap<Integer, IrGpuTypedNode> nodes = new LinkedHashMap<>();
        for (IrGpuTypedNode node : methodBody.typedBody().nodes()) {
            nodes.put(node.id(), node);
        }
        return Map.copyOf(nodes);
    }

    private static FoldAnalysis analyzeFoldCandidate(
            String methodName,
            IrGpuTypedNode node,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        if (!"GpuIrBinary".equals(node.kind())) {
            return FoldAnalysis.skipped("none");
        }
        String operator = node.attributes().getOrDefault("operator", "");
        if (!List.of("+", "-", "*", "/").contains(operator)) {
            return FoldAnalysis.skipped("unsupported-operator");
        }
        LiteralRead left = literalChild(node, "left", nodesById);
        LiteralRead right = literalChild(node, "right", nodesById);
        if (left.value().isEmpty() || right.value().isEmpty()) {
            if ("non-plain-literal".equals(left.skippedReason()) || "non-plain-literal".equals(right.skippedReason())) {
                return FoldAnalysis.skipped("non-plain-literal");
            }
            return FoldAnalysis.skipped("non-literal-operand");
        }
        FoldResult folded = fold(operator, left.value().orElseThrow(), right.value().orElseThrow());
        if (folded.value().isEmpty()) {
            return FoldAnalysis.skipped(folded.skippedReason());
        }
        String numericKind = numericKind(left.value().orElseThrow(), right.value().orElseThrow());
        return FoldAnalysis.candidate(new FoldCandidate(
                methodName,
                node.id(),
                operator,
                left.value().orElseThrow(),
                right.value().orElseThrow(),
                numericKind,
                folded.value().orElseThrow()
        ));
    }

    private record LiteralRead(Optional<String> value, String skippedReason) {

        private static LiteralRead found(String value) {
            return new LiteralRead(Optional.of(value), "none");
        }

        private static LiteralRead skipped(String reason) {
            return new LiteralRead(Optional.empty(), reason);
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
        String value = child.attributes().get("sourceText");
        if (value == null || value.isBlank()) {
            return LiteralRead.skipped("non-plain-literal");
        }
        if (!isPlainDecimalLiteral(value)) {
            return LiteralRead.skipped("non-plain-literal");
        }
        return LiteralRead.found(value);
    }

    private record FoldResult(Optional<String> value, String skippedReason) {

        private static FoldResult folded(String value) {
            return new FoldResult(Optional.of(value), "none");
        }

        private static FoldResult skipped(String reason) {
            return new FoldResult(Optional.empty(), reason);
        }
    }

    private static FoldResult fold(String operator, String left, String right) {
        BigDecimal leftValue = new BigDecimal(left);
        BigDecimal rightValue = new BigDecimal(right);
        if ("/".equals(operator) && BigDecimal.ZERO.compareTo(rightValue) == 0) {
            return FoldResult.skipped("division-by-zero");
        }
        BigDecimal folded = switch (operator) {
            case "+" -> leftValue.add(rightValue);
            case "-" -> leftValue.subtract(rightValue);
            case "*" -> leftValue.multiply(rightValue);
            case "/" -> dividesEvenly(leftValue, rightValue)
                    ? leftValue.divide(rightValue)
                    : null;
            default -> null;
        };
        if (folded == null) {
            return FoldResult.skipped("/".equals(operator) ? "non-even-division" : "unsupported-operator");
        }
        return FoldResult.folded(folded.stripTrailingZeros().toPlainString());
    }

    private static boolean dividesEvenly(BigDecimal left, BigDecimal right) {
        try {
            left.divide(right);
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private static boolean isPlainDecimalLiteral(String value) {
        return value.matches("[-+]?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?");
    }

    private static String numericKind(String left, String right) {
        return left.contains(".") || right.contains(".") ? "decimal" : "integer";
    }
}
