package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Objects;

/**
 * Read-only description of a possible temporary extraction for one repeated expression.
 */
public record GpuIrCommonSubexpressionRewritePlan(
        String temporaryName,
        String fingerprint,
        int estimatedReuseSavings,
        int insertionStatementIndex,
        String insertionAnchorLocation,
        List<String> replacementLocations
) {
    public GpuIrCommonSubexpressionRewritePlan {
        if (temporaryName == null || temporaryName.isBlank()) {
            throw new IllegalArgumentException("temporaryName must not be blank");
        }
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("fingerprint must not be blank");
        }
        if (estimatedReuseSavings < 0) {
            throw new IllegalArgumentException("estimatedReuseSavings must be non-negative");
        }
        if (insertionStatementIndex < 0) {
            throw new IllegalArgumentException("insertionStatementIndex must be non-negative");
        }
        if (insertionAnchorLocation == null || insertionAnchorLocation.isBlank()) {
            throw new IllegalArgumentException("insertionAnchorLocation must not be blank");
        }
        replacementLocations = List.copyOf(Objects.requireNonNull(replacementLocations, "replacementLocations"));
        if (replacementLocations.stream().anyMatch(location -> location == null || location.isBlank())) {
            throw new IllegalArgumentException("replacementLocations must not contain blank locations");
        }
        if (!replacementLocations.contains(insertionAnchorLocation)) {
            throw new IllegalArgumentException("replacementLocations must include the insertion anchor location");
        }
        if (replacementLocations.stream().noneMatch(location -> !location.equals(insertionAnchorLocation))) {
            throw new IllegalArgumentException("replacementLocations must include at least one post-anchor replacement");
        }
    }

    /**
     * Returns only the occurrences that a future applicator should replace with the extracted temp.
     */
    public List<String> replacementLocationsAfterAnchor() {
        return replacementLocations.stream()
                .filter(location -> !location.equals(insertionAnchorLocation))
                .toList();
    }

    public int replacementCountAfterAnchor() {
        return replacementLocationsAfterAnchor().size();
    }

    public boolean hasReplacementLocationsAfterAnchor() {
        return replacementCountAfterAnchor() > 0;
    }

    public List<GpuIrCommonSubexpressionRewriteEdit> previewReplacementEdits() {
        return replacementLocationsAfterAnchor().stream()
                .map(location -> new GpuIrCommonSubexpressionRewriteEdit(
                        temporaryName,
                        fingerprint,
                        insertionStatementIndex,
                        insertionAnchorLocation,
                        location
                ))
                .toList();
    }

    public GpuIrCommonSubexpressionRewriteInsertion previewInsertion() {
        return new GpuIrCommonSubexpressionRewriteInsertion(
                temporaryName,
                fingerprint,
                insertionStatementIndex,
                insertionAnchorLocation
        );
    }
}
