package net.sixik.ga_utils.javatogpu.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Advisory backend score bridge for caller-provided workload intent.
 */
public final class GpuRuntimeWorkloadHintBackendScoreContributor implements GpuRuntimeBackendScoreContributor {

    public static final String CONTRIBUTOR_ID = "javatogpu.backend.workload-hints-score";
    public static final String CONTRIBUTOR_VERSION = "1";

    private static final int MAX_ADJUSTMENT = 500_000;
    private static final int MIN_ADJUSTMENT = -500_000;

    private GpuRuntimeWorkloadHintBackendScoreContributor() {
    }

    public static GpuRuntimeWorkloadHintBackendScoreContributor fromContext() {
        return new GpuRuntimeWorkloadHintBackendScoreContributor();
    }

    @Override
    public GpuRuntimeBackendScoreContribution scoreCandidate(GpuRuntimeBackendScoreContext context) {
        Objects.requireNonNull(context, "context");
        if (context.workloadHints().isEmpty() || context.workloadHints().orElseThrow().empty()) {
            return GpuRuntimeBackendScoreContribution.none();
        }

        GpuRuntimeWorkloadHints hints = context.workloadHints().orElseThrow();
        return scoreHints(context, hints, "workload");
    }

    static GpuRuntimeBackendScoreContribution scoreHints(
            GpuRuntimeBackendScoreContext context,
            GpuRuntimeWorkloadHints hints,
            String diagnosticPrefix
    ) {
        Objects.requireNonNull(context, "context");
        if (hints == null || hints.empty()) {
            return GpuRuntimeBackendScoreContribution.none();
        }
        Score score = score(context, hints, diagnosticPrefix);
        return GpuRuntimeBackendScoreContribution.of(
                bounded(score.adjustment()),
                score.diagnostics()
        );
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
        return 40;
    }

    private static Score score(
            GpuRuntimeBackendScoreContext context,
            GpuRuntimeWorkloadHints hints,
            String diagnosticPrefix
    ) {
        String prefix = diagnosticPrefix == null || diagnosticPrefix.isBlank() ? "workload" : diagnosticPrefix.trim();
        int score = 0;
        ArrayList<String> diagnostics = new ArrayList<>();

        CapabilityScore capabilityScore = capabilityScore(context, hints, prefix);
        score += capabilityScore.adjustment();
        diagnostics.addAll(capabilityScore.diagnostics());

        int moduleScore = moduleFormatScore(context, hints);
        score += moduleScore;
        if (!hints.preferredModuleFormats().isEmpty()) {
            diagnostics.add(prefix + " preferred module formats " + signed(moduleScore)
                    + " formats=" + moduleFormatKeys(hints));
        }

        int memoryScore = memoryIntensityScore(context, hints);
        score += memoryScore;
        if (hints.memoryIntensity() != GpuRuntimeWorkloadIntensity.UNKNOWN) {
            diagnostics.add(prefix + " memory intensity " + hints.memoryIntensity() + " " + signed(memoryScore));
        }

        int arithmeticScore = arithmeticIntensityScore(context, hints);
        score += arithmeticScore;
        if (hints.arithmeticIntensity() != GpuRuntimeWorkloadIntensity.UNKNOWN) {
            diagnostics.add(prefix + " arithmetic intensity " + hints.arithmeticIntensity() + " " + signed(arithmeticScore));
        }

        int workGroupScore = preferredWorkGroupScore(context, hints);
        score += workGroupScore;
        if (hints.preferredWorkGroupSize() > 0) {
            diagnostics.add(prefix + " preferred work-group size " + hints.preferredWorkGroupSize()
                    + " " + signed(workGroupScore));
        }

        int parallelismScore = expectedParallelismScore(context, hints);
        score += parallelismScore;
        if (hints.expectedItemCount() > 0L) {
            diagnostics.add(prefix + " expected item count " + hints.expectedItemCount()
                    + " " + signed(parallelismScore));
        }

        int latencyScore = latencyScore(context, hints);
        score += latencyScore;
        if (hints.latencySensitive()) {
            diagnostics.add(prefix + " latency-sensitive " + signed(latencyScore));
        }

        if (diagnostics.isEmpty()) {
            diagnostics.add(prefix + " hints neutral +0");
        }
        return new Score(score, diagnostics);
    }

    private static CapabilityScore capabilityScore(
            GpuRuntimeBackendScoreContext context,
            GpuRuntimeWorkloadHints hints,
            String diagnosticPrefix
    ) {
        if (hints.requiredCapabilities().isEmpty()) {
            return new CapabilityScore(0, List.of());
        }
        int score = 0;
        ArrayList<String> diagnostics = new ArrayList<>();
        for (GpuRuntimeCapability capability : hints.requiredCapabilities()) {
            boolean supported = supportsCapability(context, capability);
            if (supported) {
                score += 60_000;
                diagnostics.add(diagnosticPrefix + " capability " + capability.key() + " supported +60000");
            } else {
                score -= 160_000;
                diagnostics.add(diagnosticPrefix + " capability " + capability.key() + " missing -160000");
            }
        }
        return new CapabilityScore(score, diagnostics);
    }

    private static int moduleFormatScore(GpuRuntimeBackendScoreContext context, GpuRuntimeWorkloadHints hints) {
        if (hints.preferredModuleFormats().isEmpty()) {
            return 0;
        }
        boolean hasMetadata = context.metadata().executionSupportPresent();
        boolean matches = context.metadata().executionSupport()
                .map(support -> hints.preferredModuleFormats().stream().anyMatch(support::declaresModuleFormat))
                .orElse(false);
        if (matches) {
            return 45_000;
        }
        return hasMetadata ? -45_000 : 0;
    }

    private static int memoryIntensityScore(GpuRuntimeBackendScoreContext context, GpuRuntimeWorkloadHints hints) {
        return switch (hints.memoryIntensity()) {
            case UNKNOWN -> 0;
            case LOW -> 10_000;
            case MEDIUM -> memoryCapacityScore(context, 32_768L, 70_000, -25_000);
            case HIGH -> memoryCapacityScore(context, 65_536L, 130_000, -60_000);
        };
    }

    private static int arithmeticIntensityScore(GpuRuntimeBackendScoreContext context, GpuRuntimeWorkloadHints hints) {
        return switch (hints.arithmeticIntensity()) {
            case UNKNOWN -> 0;
            case LOW -> 5_000;
            case MEDIUM -> computeScore(context, 80_000, -15_000);
            case HIGH -> computeScore(context, 140_000, -30_000);
        };
    }

    private static int preferredWorkGroupScore(GpuRuntimeBackendScoreContext context, GpuRuntimeWorkloadHints hints) {
        if (hints.preferredWorkGroupSize() <= 0) {
            return 0;
        }
        long maxWorkGroupSize = maxWorkGroupSize(context);
        if (maxWorkGroupSize <= 0L) {
            return 0;
        }
        return maxWorkGroupSize >= hints.preferredWorkGroupSize() ? 35_000 : -70_000;
    }

    private static int expectedParallelismScore(GpuRuntimeBackendScoreContext context, GpuRuntimeWorkloadHints hints) {
        if (hints.expectedItemCount() <= 0L) {
            return 0;
        }
        if (hints.expectedItemCount() < 4_096L) {
            return context.metadata().productionAdapter() ? 15_000 : -10_000;
        }
        if (hints.expectedItemCount() < 262_144L) {
            return computeScore(context, 45_000, 0);
        }
        long globalMemory = globalMemoryBytes(context);
        int memoryBonus = globalMemory >= (4L * 1024L * 1024L * 1024L) ? 55_000 : 0;
        return computeScore(context, 55_000 + memoryBonus, -20_000);
    }

    private static int latencyScore(GpuRuntimeBackendScoreContext context, GpuRuntimeWorkloadHints hints) {
        if (!hints.latencySensitive()) {
            return 0;
        }
        return context.metadata().productionAdapter() && context.report().available() ? 35_000 : -20_000;
    }

    private static int memoryCapacityScore(
            GpuRuntimeBackendScoreContext context,
            long localMemoryThreshold,
            int positive,
            int negative
    ) {
        long localMemory = localMemoryBytes(context);
        if (localMemory <= 0L) {
            return 0;
        }
        return localMemory >= localMemoryThreshold ? positive : negative;
    }

    private static int computeScore(GpuRuntimeBackendScoreContext context, int positive, int negative) {
        GpuRuntimeDeviceProfile profile = matchingDeviceProfile(context);
        if (profile != null && profile.computeUnits() > 0L) {
            return profile.computeUnits() >= 16L ? positive : positive / 2;
        }
        return supportsCapability(context, GpuRuntimeCapability.COMPUTE_CAPABILITY) ? positive : negative;
    }

    private static boolean supportsCapability(GpuRuntimeBackendScoreContext context, GpuRuntimeCapability capability) {
        if (capability == null) {
            return false;
        }
        if (context.report().supports(capability)) {
            return true;
        }
        if (context.metadata().declaresCapability(capability)) {
            return true;
        }
        GpuRuntimeDeviceProfile profile = matchingDeviceProfile(context);
        return profile != null && profile.supportsCapability(capability);
    }

    private static long localMemoryBytes(GpuRuntimeBackendScoreContext context) {
        GpuRuntimeDeviceProfile profile = matchingDeviceProfile(context);
        if (profile != null && profile.localMemoryBytes() > 0L) {
            return profile.localMemoryBytes();
        }
        Long reportValue = context.report().localMemoryBytes();
        return reportValue == null ? -1L : reportValue;
    }

    private static long globalMemoryBytes(GpuRuntimeBackendScoreContext context) {
        GpuRuntimeDeviceProfile profile = matchingDeviceProfile(context);
        return profile == null ? -1L : profile.globalMemoryBytes();
    }

    private static long maxWorkGroupSize(GpuRuntimeBackendScoreContext context) {
        GpuRuntimeDeviceProfile profile = matchingDeviceProfile(context);
        if (profile != null && profile.maxWorkGroupSize() > 0L) {
            return profile.maxWorkGroupSize();
        }
        Long reportValue = context.report().maxWorkGroupSize();
        return reportValue == null ? -1L : reportValue;
    }

    private static GpuRuntimeDeviceProfile matchingDeviceProfile(GpuRuntimeBackendScoreContext context) {
        if (context.deviceProfile().isEmpty()) {
            return null;
        }
        GpuRuntimeDeviceProfile profile = context.deviceProfile().orElseThrow();
        return profile.backendTarget() == context.report().backendTarget() ? profile : null;
    }

    private static String moduleFormatKeys(GpuRuntimeWorkloadHints hints) {
        return hints.preferredModuleFormats().stream()
                .map(GpuBackendModuleFormat::key)
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static int bounded(int value) {
        return Math.max(MIN_ADJUSTMENT, Math.min(MAX_ADJUSTMENT, value));
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    private record Score(int adjustment, List<String> diagnostics) {
    }

    private record CapabilityScore(int adjustment, List<String> diagnostics) {
    }
}
