package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * Read-only preview of one future CSE replacement operation.
 */
public record GpuIrCommonSubexpressionRewriteEdit(
        String temporaryName,
        String fingerprint,
        int insertionStatementIndex,
        String insertionAnchorLocation,
        String replacementLocation
) {
    public GpuIrCommonSubexpressionRewriteEdit {
        if (temporaryName == null || temporaryName.isBlank()) {
            throw new IllegalArgumentException("temporaryName must not be blank");
        }
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("fingerprint must not be blank");
        }
        if (insertionStatementIndex < 0) {
            throw new IllegalArgumentException("insertionStatementIndex must be non-negative");
        }
        if (insertionAnchorLocation == null || insertionAnchorLocation.isBlank()) {
            throw new IllegalArgumentException("insertionAnchorLocation must not be blank");
        }
        if (replacementLocation == null || replacementLocation.isBlank()) {
            throw new IllegalArgumentException("replacementLocation must not be blank");
        }
        if (replacementLocation.equals(insertionAnchorLocation)) {
            throw new IllegalArgumentException("replacementLocation must point after the insertion anchor");
        }
        GpuIrCommonSubexpressionLocation anchor = GpuIrCommonSubexpressionLocation.parse(insertionAnchorLocation);
        GpuIrCommonSubexpressionLocation replacement = GpuIrCommonSubexpressionLocation.parse(replacementLocation);
        if (anchor.topLevelStatementIndex().isEmpty() || replacement.topLevelStatementIndex().isEmpty()) {
            throw new IllegalArgumentException("replacement edits must use top-level statement locations");
        }
        if (replacement.topLevelStatementIndex().getAsInt() < anchor.topLevelStatementIndex().getAsInt()) {
            throw new IllegalArgumentException("replacementLocation must not appear before the insertion anchor");
        }
    }

    public GpuIrCommonSubexpressionLocation insertionAnchor() {
        return GpuIrCommonSubexpressionLocation.parse(insertionAnchorLocation);
    }

    public GpuIrCommonSubexpressionLocation replacement() {
        return GpuIrCommonSubexpressionLocation.parse(replacementLocation);
    }
}
