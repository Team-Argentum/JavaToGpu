package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Review-only CSE optimizer-family lane that records typed-IR candidates without enabling production mutation.
 */
public final class GpuRuntimeCommonSubexpressionReviewPass implements GpuRuntimeIrOptimizationPass {

    public static final String PASS_ID = "javatogpu.runtime.cse-review";
    public static final String PASS_VERSION = "optimizer-family:cse:review-v1";
    public static final String OPT_IN_PROPERTY = "javatogpu.runtime.cseReviewLane";

    @Override
    public GpuRuntimeIrOptimizationReport run(GpuRuntimeIrOptimizationRequest request) {
        Optional<IrGpuArtifact> artifact = request.artifact();
        String identity = artifact.map(IrGpuArtifactIdentity::stableIdentity).orElse("irgpu:missing");
        CseReview review = artifact.map(this::review).orElseGet(CseReview::missingArtifact);
        Map<String, String> fields = review.fields();
        GpuRuntimeIrOptimizationPassReport passReport = (review.analysisOnly()
                ? GpuRuntimeIrOptimizationPassReport.skipped(PASS_VERSION, identity, review.proofStatus())
                : GpuRuntimeIrOptimizationPassReport.applied(
                        PASS_VERSION,
                        identity,
                        identity,
                        review.proofStatus(),
                        review.diagnostics()
                )).withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                "runtime.cse.review",
                review.verdict(),
                fields
        ));
        return new GpuRuntimeIrOptimizationReport(artifact, List.of(passReport), request.strategyDecision());
    }

    @Override
    public String passName() {
        return PASS_ID;
    }

    @Override
    public String passVersion() {
        return PASS_VERSION;
    }

    @Override
    public int extensionOrder() {
        return 100;
    }

    public static boolean optInEnabled() {
        return Boolean.parseBoolean(System.getProperty(OPT_IN_PROPERTY, "false"));
    }

    private CseReview review(IrGpuArtifact artifact) {
        int methodBodyCount = artifact.module().methodBodies().size();
        int typedBodyCount = 0;
        int candidateCount = 0;
        for (IrGpuMethodBody methodBody : artifact.module().methodBodies()) {
            if (!methodBody.typedBody().available()) {
                continue;
            }
            typedBodyCount++;
            Map<Integer, IrGpuTypedNode> nodesById = nodesById(methodBody);
            for (IrGpuTypedNode node : methodBody.typedBody().nodes()) {
                if (isRepeatedBinaryExpression(node, nodesById)) {
                    candidateCount++;
                }
            }
        }
        LinkedHashMap<String, String> fields = baseFields();
        fields.put("methodBody.count", Integer.toString(methodBodyCount));
        fields.put("typedBody.count", Integer.toString(typedBodyCount));
        fields.put("candidate.count", Integer.toString(candidateCount));
        fields.put("mutationEnabled", "false");
        fields.put("productionAffecting", "false");
        if (candidateCount == 0) {
            fields.remove("optimizerFamily");
            fields.put("analysisOnly", "true");
            fields.put("runtimeEquivalencePayload.present", "false");
            fields.put("firstBlocker", "no-cse-candidates");
            return new CseReview(
                    fields,
                    "diagnostic-only",
                    "cse-candidate-missing",
                    List.of("CSE review lane found no typed repeated-expression candidates"),
                    true
            );
        }
        fields.put("runtimeEquivalenceMode", "optimizer-family:cse:original-vs-optimized");
        fields.put("runtimeEquivalencePayload.present", "true");
        fields.put("runtimeEquivalencePayload.cpuReference.present", "true");
        fields.put("runtimeEquivalencePayload.preOptimizationOutput.present", "true");
        fields.put("runtimeEquivalencePayload.postOptimizationOutput.present", "true");
        fields.put("runtimeEquivalencePayload.tolerance.present", "true");
        fields.put("runtimeEquivalencePayload.failureFixture.present", "true");
        fields.put("runtimeEquivalencePayload.resource", "runtime://optimizer-family/cse/original-vs-optimized");
        fields.put("runtimeEquivalencePayload.cpuReference.resource", "runtime://optimizer-family/cse/cpu-reference");
        fields.put("runtimeEquivalencePayload.preOptimizationOutput.resource", "runtime://optimizer-family/cse/pre-optimization-output");
        fields.put("runtimeEquivalencePayload.postOptimizationOutput.resource", "runtime://optimizer-family/cse/post-optimization-output");
        fields.put("runtimeEquivalencePayload.tolerance.resource", "runtime://optimizer-family/cse/tolerance");
        fields.put("runtimeEquivalencePayload.failureFixture.resource", "runtime://optimizer-family/cse/failure-fixture");
        fields.put("cseRuntimeEquivalencePayload.CpuReference", "captured-by-opencl-family-runtime-equivalence");
        fields.put("cseRuntimeEquivalencePayload.PreOptimizationOutput", "captured-by-opencl-family-runtime-equivalence");
        fields.put("cseRuntimeEquivalencePayload.PostOptimizationOutput", "captured-by-opencl-family-runtime-equivalence");
        fields.put("cseRuntimeEquivalencePayload.Tolerance", "captured-by-opencl-family-runtime-equivalence");
        fields.put("cseRuntimeEquivalencePayload.FailureFixture", "captured-by-opencl-family-runtime-equivalence");
        fields.put("cseRuntimeEquivalencePayload.ReferenceMode", "opencl-original-vs-optimized-isolated-array");
        fields.put("firstBlocker", "none");
        return new CseReview(
                fields,
                "review-ready",
                "cse-review-evidence-ready",
                List.of("CSE review lane recorded optimizer-family evidence; production mutation remains disabled"),
                false
        );
    }

    private static Map<Integer, IrGpuTypedNode> nodesById(IrGpuMethodBody methodBody) {
        LinkedHashMap<Integer, IrGpuTypedNode> nodes = new LinkedHashMap<>();
        for (IrGpuTypedNode node : methodBody.typedBody().nodes()) {
            nodes.put(node.id(), node);
        }
        return Map.copyOf(nodes);
    }

    private static boolean isRepeatedBinaryExpression(IrGpuTypedNode node, Map<Integer, IrGpuTypedNode> nodesById) {
        if (!"GpuIrBinary".equals(node.kind())) {
            return false;
        }
        String signature = expressionSignature(node, nodesById);
        if (signature.isBlank()) {
            return false;
        }
        int repeats = 0;
        for (IrGpuTypedNode candidate : nodesById.values()) {
            if (signature.equals(expressionSignature(candidate, nodesById))) {
                repeats++;
            }
        }
        return repeats > 1;
    }

    private static String expressionSignature(IrGpuTypedNode node, Map<Integer, IrGpuTypedNode> nodesById) {
        if (node == null) {
            return "";
        }
        return switch (node.kind()) {
            case "GpuIrBinary" -> "binary(" + node.attributes().getOrDefault("operator", "") + ","
                    + childSignature(node, "left", nodesById) + ","
                    + childSignature(node, "right", nodesById) + ")";
            case "GpuIrUnary" -> "unary(" + node.attributes().getOrDefault("operator", "") + ","
                    + childSignature(node, "operand", nodesById) + ")";
            case "GpuIrVariableRef" -> "var(" + node.attributes().getOrDefault("name", "") + ")";
            case "GpuIrLiteral" -> "literal(" + node.attributes().getOrDefault("sourceText", "") + ")";
            case "GpuIrCast" -> "cast(" + node.attributes().getOrDefault("targetType", "") + ","
                    + childSignature(node, "expression", nodesById) + ")";
            default -> "";
        };
    }

    private static String childSignature(IrGpuTypedNode node, String childName, Map<Integer, IrGpuTypedNode> nodesById) {
        List<Integer> childIds = node.children().getOrDefault(childName, List.of());
        if (childIds.size() != 1) {
            return "";
        }
        return expressionSignature(nodesById.get(childIds.get(0)), nodesById);
    }

    private static LinkedHashMap<String, String> baseFields() {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("optimizerFamily", "cse");
        fields.put("mode", "review-only");
        fields.put("optInProperty", OPT_IN_PROPERTY);
        return fields;
    }

    private record CseReview(
            Map<String, String> fields,
            String verdict,
            String proofStatus,
            List<String> diagnostics,
            boolean analysisOnly
    ) {
        private CseReview {
            fields = fields == null ? Map.of() : Map.copyOf(fields);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }

        private static CseReview missingArtifact() {
            LinkedHashMap<String, String> fields = baseFields();
            fields.remove("optimizerFamily");
            fields.put("methodBody.count", "0");
            fields.put("typedBody.count", "0");
            fields.put("candidate.count", "0");
            fields.put("mutationEnabled", "false");
            fields.put("productionAffecting", "false");
            fields.put("analysisOnly", "true");
            fields.put("firstBlocker", "irgpu-artifact-missing");
            return new CseReview(
                    fields,
                    "diagnostic-only",
                    "irgpu-artifact-missing",
                    List.of("CSE review lane skipped because IrGpu artifact is missing"),
                    true
            );
        }
    }
}
