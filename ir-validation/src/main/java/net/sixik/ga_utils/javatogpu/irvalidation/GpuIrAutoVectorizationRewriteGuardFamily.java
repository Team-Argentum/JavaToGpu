package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * Stable grouping keys for read-only auto-vectorization rewrite guards.
 */
public enum GpuIrAutoVectorizationRewriteGuardFamily {
    UNKNOWN_VECTOR_TYPE("unknownVectorType"),
    BACKEND_VECTOR_WIDTH("backendVectorWidth"),
    BACKEND_DOUBLE_VECTOR("backendDoubleVector"),
    MEMORY_ADDRESS_SPACE("memoryAddressSpace"),
    TARGET_SOURCE_ALIAS("targetSourceAlias"),
    NEIGHBOR_SOURCE_WRITE("neighborSourceWrite"),
    NEIGHBOR_TARGET_WRITE("neighborTargetWrite"),
    CONTROL_FLOW_BOUNDARY("controlFlowBoundary"),
    EARLY_EXIT_BOUNDARY("earlyExitBoundary"),
    SIDE_EFFECT("sideEffect"),
    OTHER("other");

    private final String artifactValue;

    GpuIrAutoVectorizationRewriteGuardFamily(String artifactValue) {
        this.artifactValue = artifactValue;
    }

    public String artifactValue() {
        return artifactValue;
    }
}
