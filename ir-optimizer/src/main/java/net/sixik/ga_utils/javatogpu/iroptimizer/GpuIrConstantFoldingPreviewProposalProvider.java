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
            Map<String, Integer> operatorCounts,
            Map<String, Integer> numericKindCounts,
            String firstCandidate,
            String firstOperator,
            String firstNumericKind,
            String firstFoldedValue
    ) {

        private static Preview from(IrGpuArtifact artifact) {
            int methodBodyCount = artifact.module().methodBodies().size();
            int typedBodyCount = 0;
            int candidateCount = 0;
            LinkedHashMap<String, Integer> operatorCounts = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> numericKindCounts = new LinkedHashMap<>();
            String firstCandidate = "none";
            String firstOperator = "none";
            String firstNumericKind = "none";
            String firstFoldedValue = "none";
            for (IrGpuMethodBody methodBody : artifact.module().methodBodies()) {
                if (!methodBody.typedBody().available()) {
                    continue;
                }
                typedBodyCount++;
                Map<Integer, IrGpuTypedNode> nodesById = nodesById(methodBody);
                for (IrGpuTypedNode node : methodBody.typedBody().nodes()) {
                    Optional<FoldCandidate> candidate = foldCandidate(methodBody.name(), node, nodesById);
                    if (candidate.isEmpty()) {
                        continue;
                    }
                    candidateCount++;
                    operatorCounts.merge(candidate.get().operator(), 1, Integer::sum);
                    numericKindCounts.merge(candidate.get().numericKind(), 1, Integer::sum);
                    if ("none".equals(firstCandidate)) {
                        firstCandidate = candidate.get().summary();
                        firstOperator = candidate.get().operator();
                        firstNumericKind = candidate.get().numericKind();
                        firstFoldedValue = candidate.get().foldedValue();
                    }
                }
            }
            return new Preview(
                    methodBodyCount,
                    typedBodyCount,
                    candidateCount,
                    Map.copyOf(operatorCounts),
                    Map.copyOf(numericKindCounts),
                    firstCandidate,
                    firstOperator,
                    firstNumericKind,
                    firstFoldedValue
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
            fields.put("rewrite.proposed", "false");
            fields.put("mutationRequired", "false");
            fields.put("productionAffecting", "false");
            fields.put("previewOnly", "true");
            fields.put("operator.counts", countsSummary(operatorCounts));
            fields.put("numericKind.counts", countsSummary(numericKindCounts));
            fields.put("firstCandidate", firstCandidate);
            fields.put("firstOperator", firstOperator);
            fields.put("firstNumericKind", firstNumericKind);
            fields.put("firstFoldedValue", firstFoldedValue);
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

    private static Map<Integer, IrGpuTypedNode> nodesById(IrGpuMethodBody methodBody) {
        LinkedHashMap<Integer, IrGpuTypedNode> nodes = new LinkedHashMap<>();
        for (IrGpuTypedNode node : methodBody.typedBody().nodes()) {
            nodes.put(node.id(), node);
        }
        return Map.copyOf(nodes);
    }

    private static Optional<FoldCandidate> foldCandidate(
            String methodName,
            IrGpuTypedNode node,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        if (!"GpuIrBinary".equals(node.kind())) {
            return Optional.empty();
        }
        String operator = node.attributes().getOrDefault("operator", "");
        if (!List.of("+", "-", "*", "/").contains(operator)) {
            return Optional.empty();
        }
        Optional<String> left = literalChild(node, "left", nodesById);
        Optional<String> right = literalChild(node, "right", nodesById);
        if (left.isEmpty() || right.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> folded = fold(operator, left.get(), right.get());
        String numericKind = numericKind(left.get(), right.get());
        return folded.map(value -> new FoldCandidate(
                methodName,
                node.id(),
                operator,
                left.get(),
                right.get(),
                numericKind,
                value
        ));
    }

    private static Optional<String> literalChild(
            IrGpuTypedNode node,
            String childName,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        List<Integer> childIds = node.children().getOrDefault(childName, List.of());
        if (childIds.size() != 1) {
            return Optional.empty();
        }
        IrGpuTypedNode child = nodesById.get(childIds.get(0));
        if (child == null || !"GpuIrLiteral".equals(child.kind())) {
            return Optional.empty();
        }
        return Optional.ofNullable(child.attributes().get("sourceText"))
                .filter(value -> !value.isBlank())
                .filter(GpuIrConstantFoldingPreviewProposalProvider::isPlainDecimalLiteral);
    }

    private static Optional<String> fold(String operator, String left, String right) {
        BigDecimal leftValue = new BigDecimal(left);
        BigDecimal rightValue = new BigDecimal(right);
        if ("/".equals(operator) && BigDecimal.ZERO.compareTo(rightValue) == 0) {
            return Optional.empty();
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
        return folded == null ? Optional.empty() : Optional.of(folded.stripTrailingZeros().toPlainString());
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
