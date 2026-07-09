package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Describes which production-promotion artifacts a runtime backend can write today.
 *
 * <p>The contract is intentionally backend-neutral: OpenCL can advertise the full current artifact set, while future
 * CUDA, Vulkan, and Metal backends can start with partial support without pretending they satisfy the complete I3
 * runtime-promotion surface.</p>
 */
public record GpuPromotionArtifactSupport(
        GpuBackendTarget backendTarget,
        List<String> supportedArtifacts,
        List<String> missingArtifacts
) {

    public GpuPromotionArtifactSupport {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        supportedArtifacts = stableDistinct(supportedArtifacts);
        missingArtifacts = stableDistinct(missingArtifacts);
    }

    /**
     * Returns a fail-closed support report for backends that have not wired promotion artifacts yet.
     */
    public static GpuPromotionArtifactSupport none(GpuBackendTarget backendTarget) {
        return new GpuPromotionArtifactSupport(
                backendTarget,
                List.of(),
                GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS
        );
    }

    /**
     * Returns a complete support report for backends that write every registered promotion artifact.
     */
    public static GpuPromotionArtifactSupport complete(GpuBackendTarget backendTarget) {
        return new GpuPromotionArtifactSupport(
                backendTarget,
                GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS,
                List.of()
        );
    }

    /**
     * Builds a partial report and derives missing artifacts from the backend-neutral registry.
     */
    public static GpuPromotionArtifactSupport partial(GpuBackendTarget backendTarget, List<String> supportedArtifacts) {
        List<String> supported = stableDistinct(supportedArtifacts);
        List<String> missing = GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS.stream()
                .filter(artifact -> !supported.contains(artifact))
                .toList();
        return new GpuPromotionArtifactSupport(backendTarget, supported, missing);
    }

    /**
     * Returns true only when the backend advertises every registered promotion artifact.
     */
    public boolean complete() {
        return missingArtifacts.isEmpty()
                && supportedArtifacts.containsAll(GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS);
    }

    /**
     * Returns whether the backend advertises one named promotion artifact.
     */
    public boolean supports(String artifactName) {
        return supportedArtifacts.contains(artifactName);
    }

    /**
     * Formats this support surface as a stable properties artifact for CI and backend bring-up diagnostics.
     */
    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("backendTarget=").append(backendTarget).append('\n');
        builder.append("complete=").append(complete()).append('\n');
        builder.append("supported.count=").append(supportedArtifacts.size()).append('\n');
        for (int index = 0; index < supportedArtifacts.size(); index++) {
            builder.append("supported.").append(index).append('=').append(supportedArtifacts.get(index)).append('\n');
        }
        builder.append("missing.count=").append(missingArtifacts.size()).append('\n');
        for (int index = 0; index < missingArtifacts.size(); index++) {
            builder.append("missing.").append(index).append('=').append(missingArtifacts.get(index)).append('\n');
        }
        builder.append("registry.count=").append(GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS.size()).append('\n');
        return builder.toString();
    }

    private static List<String> stableDistinct(List<String> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return List.of();
        }
        Set<String> stable = new LinkedHashSet<>();
        for (String artifact : artifacts) {
            if (artifact != null && !artifact.isBlank()) {
                stable.add(artifact);
            }
        }
        return List.copyOf(stable);
    }
}
