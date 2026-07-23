package net.sixik.ga_utils.javatogpu.runtime.selection;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.Objects;

/**
 * Read-only backend score bridge for precomputed compiler resource feedback.
 *
 * <p>The contributor never compiles or probes a candidate. It only reads a report that was produced earlier from a
 * compile artifact/log and turns known resource metrics into bounded advisory score evidence.</p>
 */
public final class GpuBackendCompilerFeedbackScoreContributor implements GpuRuntimeBackendScoreContributor {

    public static final String CONTRIBUTOR_ID = "javatogpu.backend.compiler-feedback-score";
    public static final String CONTRIBUTOR_VERSION = "1";

    private static final int MAX_ADJUSTMENT = 250_000;
    private static final int MIN_ADJUSTMENT = -250_000;

    private GpuBackendCompilerFeedbackScoreContributor() {
    }

    public static GpuBackendCompilerFeedbackScoreContributor fromContext() {
        return new GpuBackendCompilerFeedbackScoreContributor();
    }

    @Override
    public GpuRuntimeBackendScoreContribution scoreCandidate(GpuRuntimeBackendScoreContext context) {
        Objects.requireNonNull(context, "context");
        if (context.compilerFeedbackReport().isEmpty()) {
            return GpuRuntimeBackendScoreContribution.of(0, "compiler feedback unavailable +0");
        }

        GpuBackendCompilerFeedbackReport report = context.compilerFeedbackReport().orElseThrow();
        if (!targetMatches(report.request().backendTarget(), context.report().backendTarget())) {
            return GpuRuntimeBackendScoreContribution.none();
        }
        if (!report.available()) {
            return GpuRuntimeBackendScoreContribution.of(
                    0,
                    "compiler feedback unavailable for " + context.report().backendTarget() + " +0"
            );
        }

        GpuBackendCompilerFeedback feedback = report.selected().orElseThrow();
        int adjustment = bounded(score(feedback));
        return GpuRuntimeBackendScoreContribution.of(adjustment, diagnostic(context, feedback, adjustment));
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
        return 30;
    }

    private static boolean targetMatches(GpuBackendTarget requestTarget, GpuBackendTarget candidateTarget) {
        GpuBackendTarget request = requestTarget == null ? GpuBackendTarget.UNKNOWN : requestTarget;
        GpuBackendTarget candidate = candidateTarget == null ? GpuBackendTarget.UNKNOWN : candidateTarget;
        return request == GpuBackendTarget.UNKNOWN || request == candidate;
    }

    private static int score(GpuBackendCompilerFeedback feedback) {
        int score = 25_000;
        score += feedback.metricCount() * 3_000;

        int registers = feedback.effectiveRegisterCount();
        if (known(registers)) {
            if (registers <= 32) {
                score += 60_000;
            } else if (registers <= 64) {
                score += 35_000;
            } else if (registers <= 96) {
                score += 10_000;
            } else {
                score -= Math.min(80_000, (registers - 96) * 1_000);
            }
        }

        int spillBytes = feedback.knownSpillBytes();
        if (known(spillBytes)) {
            if (spillBytes == 0) {
                score += 35_000;
            } else {
                score -= Math.min(100_000, spillBytes * 1_000);
            }
        }

        if (known(feedback.stackFrameBytes())) {
            if (feedback.stackFrameBytes() == 0) {
                score += 15_000;
            } else {
                score -= Math.min(50_000, Math.max(1, feedback.stackFrameBytes() / 16) * 1_000);
            }
        }

        if (known(feedback.localMemoryBytes())) {
            score += 10_000;
            score -= Math.min(60_000, Math.max(0, feedback.localMemoryBytes() - 32_768) / 256);
        }

        if (known(feedback.occupancyPermille())) {
            score += Math.min(80_000, feedback.occupancyPermille() * 80);
        }
        return score;
    }

    private static String diagnostic(
            GpuRuntimeBackendScoreContext context,
            GpuBackendCompilerFeedback feedback,
            int adjustment
    ) {
        return "compiler feedback target=" + context.report().backendTarget()
                + ", provider=" + feedback.providerId()
                + ", metrics=" + feedback.metricCount()
                + ", registers=" + metricText(feedback.effectiveRegisterCount())
                + ", spillBytes=" + metricText(feedback.knownSpillBytes())
                + ", stackFrameBytes=" + metricText(feedback.stackFrameBytes())
                + ", localMemoryBytes=" + metricText(feedback.localMemoryBytes())
                + ", occupancyPermille=" + metricText(feedback.occupancyPermille())
                + ", adjustment=" + signed(adjustment);
    }

    private static boolean known(int value) {
        return value >= 0;
    }

    private static int bounded(int value) {
        return Math.max(MIN_ADJUSTMENT, Math.min(MAX_ADJUSTMENT, value));
    }

    private static String metricText(int value) {
        return known(value) ? Integer.toString(value) : "unknown";
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }
}
