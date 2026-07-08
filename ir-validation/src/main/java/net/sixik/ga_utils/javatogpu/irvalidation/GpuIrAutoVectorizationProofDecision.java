package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only gate result derived from the aggregate proof bundle.
 */
public record GpuIrAutoVectorizationProofDecision(
        GpuIrAutoVectorizationProofDecisionStatus status,
        List<String> blockingProofKinds,
        Optional<GpuIrAutoVectorizationProofSummary> firstBlockingProof
) {
    public GpuIrAutoVectorizationProofDecision {
        status = Objects.requireNonNull(status, "status");
        blockingProofKinds = List.copyOf(Objects.requireNonNull(blockingProofKinds, "blockingProofKinds"));
        firstBlockingProof = Objects.requireNonNull(firstBlockingProof, "firstBlockingProof");
        if (status == GpuIrAutoVectorizationProofDecisionStatus.ALLOW && !blockingProofKinds.isEmpty()) {
            throw new IllegalArgumentException("allowed proof decisions must not have blocking proof kinds");
        }
        if (status != GpuIrAutoVectorizationProofDecisionStatus.ALLOW && blockingProofKinds.isEmpty()) {
            throw new IllegalArgumentException("blocked proof decisions must have at least one blocking proof kind");
        }
    }

    public static GpuIrAutoVectorizationProofDecision from(GpuIrAutoVectorizationProofBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        List<String> blockingKinds = bundle.unsafeProofKindCounts().keySet().stream().toList();
        Optional<GpuIrAutoVectorizationProofSummary> firstBlockingProof = bundle.firstUnsafeProofSummary();
        if (blockingKinds.isEmpty()) {
            return new GpuIrAutoVectorizationProofDecision(
                    GpuIrAutoVectorizationProofDecisionStatus.ALLOW,
                    List.of(),
                    Optional.empty()
            );
        }
        return new GpuIrAutoVectorizationProofDecision(
                statusFor(blockingKinds),
                blockingKinds,
                firstBlockingProof
        );
    }

    public boolean allowRewrite() {
        return status == GpuIrAutoVectorizationProofDecisionStatus.ALLOW;
    }

    public boolean blocksRewrite() {
        return !allowRewrite();
    }

    public String blockingProofKindsArtifactValue() {
        return String.join(",", blockingProofKinds);
    }

    public Map<String, String> artifactFields(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(prefix + "Status", status.artifactValue());
        values.put(prefix + "AllowRewrite", Boolean.toString(allowRewrite()));
        values.put(prefix + "BlockingProofKinds", blockingProofKindsArtifactValue());
        firstBlockingProof.ifPresent(summary -> {
            values.put(prefix + "FirstBlockingProofKind", summary.proofKind());
            values.put(prefix + "FirstBlockingProofLocation", summary.location());
            values.put(prefix + "FirstBlockingProofDiagnostics", Integer.toString(summary.diagnosticCount()));
            values.put(prefix + "FirstBlockingProofSummary", summary.summaryLine());
        });
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorizationProofDecision");
    }

    public String summary() {
        return "auto-vectorization proof decision"
                + " status=" + status.artifactValue()
                + " allowRewrite=" + allowRewrite()
                + (blockingProofKinds.isEmpty() ? "" : " blockingProofKinds=" + blockingProofKinds.stream().collect(Collectors.joining(",", "[", "]")))
                + firstBlockingProof.map(summary -> " firstBlockingProof=" + summary.proofKind() + "@" + summary.location()).orElse("");
    }

    private static GpuIrAutoVectorizationProofDecisionStatus statusFor(List<String> blockingKinds) {
        if (blockingKinds.size() > 1) {
            return GpuIrAutoVectorizationProofDecisionStatus.BLOCKED_BY_MULTIPLE_PROOFS;
        }
        return switch (blockingKinds.get(0)) {
            case "rewritePlan" -> GpuIrAutoVectorizationProofDecisionStatus.BLOCKED_BY_REWRITE_PLAN;
            case "memoryLegality" -> GpuIrAutoVectorizationProofDecisionStatus.BLOCKED_BY_MEMORY;
            case "controlFlowBoundary" -> GpuIrAutoVectorizationProofDecisionStatus.BLOCKED_BY_CONTROL_FLOW;
            case "sideEffect" -> GpuIrAutoVectorizationProofDecisionStatus.BLOCKED_BY_SIDE_EFFECT;
            case "mutation" -> GpuIrAutoVectorizationProofDecisionStatus.BLOCKED_BY_MUTATION;
            case "backend" -> GpuIrAutoVectorizationProofDecisionStatus.BLOCKED_BY_BACKEND;
            case "unknownVector" -> GpuIrAutoVectorizationProofDecisionStatus.BLOCKED_BY_UNKNOWN_VECTOR;
            default -> GpuIrAutoVectorizationProofDecisionStatus.BLOCKED_BY_UNKNOWN_PROOF;
        };
    }
}
