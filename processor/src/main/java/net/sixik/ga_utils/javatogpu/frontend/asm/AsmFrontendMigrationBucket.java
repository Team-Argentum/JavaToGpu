package net.sixik.ga_utils.javatogpu.frontend.asm;

/**
 * Coarse migration bucket for broader ASM ingestion triage and CI dashboards.
 */
public enum AsmFrontendMigrationBucket {
    NONE("none", "No migration is required for the current GPU-safe ASM subset."),
    ARRAY_METADATA("arrayMetadata", "Pass array lengths, dimensions, and allocation ownership explicitly."),
    HOST_MEMORY_MODEL("hostMemoryModel", "Move runtime allocation and object graph ownership to host-side setup."),
    EXPLICIT_FAILURE_MODEL("explicitFailureModel", "Replace exceptions with explicit status outputs or deliberate GPU trap/unreachable calls."),
    SYNCHRONIZATION_MODEL("synchronizationModel", "Remove JVM monitor semantics and use GPU-safe synchronization boundaries only."),
    STATIC_DISPATCH_MODEL("staticDispatchModel", "Normalize calls to static GPU helpers with supported owners and descriptors."),
    FIELD_STATE_MODEL("fieldStateModel", "Flatten field state into explicit parameters, structs, or local values."),
    OBJECT_MODEL("objectModel", "Replace heap objects, casts, and type checks with GPU-safe value shapes."),
    TYPE_SIGNATURE_MODEL("typeSignatureModel", "Redesign unsupported descriptors into GPU-safe primitive, vector, pointer, image, or struct types."),
    CONTROL_FLOW_MODEL("controlFlowModel", "Normalize unsupported control flow into structured GPU-safe branches and loops."),
    UNKNOWN_FRONTEND_GAP("unknownFrontendGap", "Investigate the frontend gap before attempting automatic rewriting.");

    private final String artifactValue;
    private final String guidance;

    AsmFrontendMigrationBucket(String artifactValue, String guidance) {
        this.artifactValue = artifactValue;
        this.guidance = guidance;
    }

    public String artifactValue() {
        return artifactValue;
    }

    public String guidance() {
        return guidance;
    }
}
