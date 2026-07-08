package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * Read-only classification for sibling statements that block future vector rewrites.
 */
public enum GpuIrAutoVectorizationControlFlowBoundaryKind {
    NONE(null),
    CONTROL_FLOW_BOUNDARY(GpuIrAutoVectorizationRewriteGuardFamily.CONTROL_FLOW_BOUNDARY),
    EARLY_EXIT_BOUNDARY(GpuIrAutoVectorizationRewriteGuardFamily.EARLY_EXIT_BOUNDARY);

    private final GpuIrAutoVectorizationRewriteGuardFamily guardFamily;

    GpuIrAutoVectorizationControlFlowBoundaryKind(GpuIrAutoVectorizationRewriteGuardFamily guardFamily) {
        this.guardFamily = guardFamily;
    }

    public boolean blocksRewrite() {
        return guardFamily != null;
    }

    public GpuIrAutoVectorizationRewriteGuardFamily guardFamily() {
        return guardFamily;
    }
}
