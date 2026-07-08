package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Map;
import java.util.Objects;

/**
 * Read-only proof artifact for a sibling control-flow or early-exit boundary.
 */
public record GpuIrAutoVectorizationControlFlowBoundaryReport(
        String candidateLocation,
        String siblingLocation,
        String side,
        GpuIrAutoVectorizationControlFlowBoundaryKind kind
) {
    public GpuIrAutoVectorizationControlFlowBoundaryReport {
        if (candidateLocation == null || candidateLocation.isBlank()) {
            throw new IllegalArgumentException("candidateLocation must not be blank");
        }
        if (siblingLocation == null || siblingLocation.isBlank()) {
            throw new IllegalArgumentException("siblingLocation must not be blank");
        }
        if (side == null || side.isBlank()) {
            throw new IllegalArgumentException("side must not be blank");
        }
        kind = Objects.requireNonNull(kind, "kind");
    }

    public boolean blocksRewrite() {
        return kind.blocksRewrite();
    }

    public GpuIrAutoVectorizationRewriteGuardDiagnostic guardDiagnostic() {
        if (!blocksRewrite()) {
            throw new IllegalStateException("control-flow boundary report does not block rewrite");
        }
        return new GpuIrAutoVectorizationRewriteGuardDiagnostic(kind.guardFamily(), candidateLocation, guardMessage());
    }

    public GpuIrAutoVectorizationProofSummary proofSummary() {
        return GpuIrAutoVectorizationProofSummary.fromGuards(
                "controlFlowBoundary",
                candidateLocation,
                0,
                blocksRewrite() ? java.util.List.of(guardDiagnostic()) : java.util.List.of()
        );
    }

    /**
     * Exposes the shared proof summary fields through this analyzer report.
     */
    public Map<String, String> artifactFields(String prefix) {
        return proofSummary().artifactFields(prefix);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorizationProofControlFlowBoundary");
    }

    public String guardMessage() {
        return side + " statement " + siblingLocation + " is " + article() + " " + boundaryName()
                + " before vector rewrite safety is proven";
    }

    public String summary() {
        return proofSummary().summaryLine()
                + " candidate=" + candidateLocation
                + " sibling=" + siblingLocation
                + " side=" + side
                + " kind=" + kind
                + " blocksRewrite=" + blocksRewrite();
    }

    private String article() {
        return kind == GpuIrAutoVectorizationControlFlowBoundaryKind.EARLY_EXIT_BOUNDARY ? "an" : "a";
    }

    private String boundaryName() {
        return switch (kind) {
            case CONTROL_FLOW_BOUNDARY -> "control-flow boundary";
            case EARLY_EXIT_BOUNDARY -> "early-exit boundary";
            case NONE -> "non-boundary";
        };
    }
}
