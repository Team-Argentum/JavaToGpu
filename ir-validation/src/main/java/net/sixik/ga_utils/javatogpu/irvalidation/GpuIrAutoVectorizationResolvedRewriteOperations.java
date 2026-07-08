package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Objects;

/**
 * Read-only resolved operation bundle that future mutating rewrites can consume.
 */
public record GpuIrAutoVectorizationResolvedRewriteOperations(
        String methodName,
        List<GpuIrAutoVectorizationResolvedInsertionOperation> insertions,
        List<GpuIrAutoVectorizationResolvedReplacementOperation> replacements
) {
    public GpuIrAutoVectorizationResolvedRewriteOperations {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        insertions = List.copyOf(Objects.requireNonNull(insertions, "insertions"));
        replacements = List.copyOf(Objects.requireNonNull(replacements, "replacements"));
        if (insertions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("insertions must not contain null entries");
        }
        if (replacements.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("replacements must not contain null entries");
        }
        if (insertions.size() != replacements.size()) {
            throw new IllegalArgumentException("resolved insertions and replacements must align");
        }
    }

    public static GpuIrAutoVectorizationResolvedRewriteOperations empty(String methodName) {
        return new GpuIrAutoVectorizationResolvedRewriteOperations(methodName, List.of(), List.of());
    }

    public int operationCount() {
        return insertions.size() + replacements.size();
    }

    public boolean hasOperations() {
        return operationCount() > 0;
    }

    public String summary() {
        return "resolved auto-vectorization rewrite operations method=" + methodName
                + " insertions=" + insertions.size()
                + " replacements=" + replacements.size()
                + " operations=" + operationCount()
                + (insertions.isEmpty() ? "" : " firstInsertion=" + insertions.get(0).summary());
    }
}
