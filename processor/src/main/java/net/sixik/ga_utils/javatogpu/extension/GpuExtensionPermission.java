package net.sixik.ga_utils.javatogpu.extension;

/**
 * Maximum authority granted to an extension inside a pipeline phase.
 */
public enum GpuExtensionPermission {
    READ_ONLY(0),
    MUTATION_PROPOSAL(1),
    PRODUCTION_AFFECTING(2);

    private final int authorityLevel;

    GpuExtensionPermission(int authorityLevel) {
        this.authorityLevel = authorityLevel;
    }

    public boolean isAtMost(GpuExtensionPermission maximumPermission) {
        return authorityLevel <= maximumPermission.authorityLevel;
    }
}
