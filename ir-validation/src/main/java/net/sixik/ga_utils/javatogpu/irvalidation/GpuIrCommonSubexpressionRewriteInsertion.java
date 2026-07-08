package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * Read-only preview of the future temporary declaration insertion point.
 */
public record GpuIrCommonSubexpressionRewriteInsertion(
        String temporaryName,
        String fingerprint,
        int statementIndex,
        String anchorLocation
) {
    public GpuIrCommonSubexpressionRewriteInsertion {
        if (temporaryName == null || temporaryName.isBlank()) {
            throw new IllegalArgumentException("temporaryName must not be blank");
        }
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("fingerprint must not be blank");
        }
        if (statementIndex < 0) {
            throw new IllegalArgumentException("statementIndex must be non-negative");
        }
        if (anchorLocation == null || anchorLocation.isBlank()) {
            throw new IllegalArgumentException("anchorLocation must not be blank");
        }
    }

    public GpuIrCommonSubexpressionLocation anchor() {
        return GpuIrCommonSubexpressionLocation.parse(anchorLocation);
    }
}
