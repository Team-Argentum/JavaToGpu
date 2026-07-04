package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;

import java.util.List;
import java.util.Objects;

/**
 * Result artifact for the opt-in prototype auto-vectorization rewrite path.
 */
public record GpuIrAutoVectorizationPrototypeRewriteReport(
        GpuIrMethod method,
        List<GpuIrAutoVectorizationPrototypeAppliedRewrite> appliedRewrites
) {
    public GpuIrAutoVectorizationPrototypeRewriteReport {
        method = Objects.requireNonNull(method, "method");
        appliedRewrites = List.copyOf(Objects.requireNonNull(appliedRewrites, "appliedRewrites"));
        if (appliedRewrites.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("appliedRewrites must not contain null entries");
        }
    }

    public boolean hasAppliedRewrites() {
        return !appliedRewrites.isEmpty();
    }

    public int appliedRewriteCount() {
        return appliedRewrites.size();
    }

    public GpuIrAutoVectorizationPrototypeAppliedRewrite firstAppliedRewrite() {
        return appliedRewrites.isEmpty() ? null : appliedRewrites.get(0);
    }

    public String firstAppliedRewriteSummary() {
        return appliedRewrites.isEmpty() ? "" : appliedRewrites.get(0).summary();
    }

    public String summary() {
        return "auto-vectorization prototype rewrite method=" + method.name()
                + " appliedRewrites=" + appliedRewriteCount()
                + (hasAppliedRewrites() ? " firstAppliedRewrite=" + firstAppliedRewriteSummary() : "");
    }
}
