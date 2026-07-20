package net.sixik.ga_utils.javatogpu.runtime;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Advisory backend score bridge for workload hints inferred from descriptor/IrGpu method metadata.
 */
public final class GpuRuntimeInferredWorkloadHintBackendScoreContributor implements GpuRuntimeBackendScoreContributor {

    public static final String CONTRIBUTOR_ID = "javatogpu.backend.inferred-workload-hints-score";
    public static final String CONTRIBUTOR_VERSION = "1";

    private GpuRuntimeInferredWorkloadHintBackendScoreContributor() {
    }

    public static GpuRuntimeInferredWorkloadHintBackendScoreContributor fromContext() {
        return new GpuRuntimeInferredWorkloadHintBackendScoreContributor();
    }

    @Override
    public GpuRuntimeBackendScoreContribution scoreCandidate(GpuRuntimeBackendScoreContext context) {
        Objects.requireNonNull(context, "context");
        if (context.descriptor().isEmpty() && context.irGpuArtifact().isEmpty()) {
            return GpuRuntimeBackendScoreContribution.none();
        }

        GpuRuntimeInferredWorkloadHints inferred = GpuRuntimeWorkloadHintInference.infer(
                context.descriptor().orElse(null),
                context.irGpuArtifact().orElse(null)
        );
        if (inferred.hints().empty()) {
            return GpuRuntimeBackendScoreContribution.none();
        }

        GpuRuntimeBackendScoreContribution scoring = GpuRuntimeWorkloadHintBackendScoreContributor.scoreHints(
                context,
                inferred.hints(),
                "inferred workload"
        );
        ArrayList<String> diagnostics = new ArrayList<>(inferred.diagnostics());
        diagnostics.addAll(scoring.diagnostics());
        return GpuRuntimeBackendScoreContribution.of(scoring.adjustment(), diagnostics);
    }

    @Override
    public String extensionId() {
        return CONTRIBUTOR_ID;
    }

    @Override
    public String extensionVersion() {
        return CONTRIBUTOR_VERSION;
    }

    @Override
    public int extensionOrder() {
        return 45;
    }
}
