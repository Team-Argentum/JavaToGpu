package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Objects;

/**
 * Runtime-equivalence evidence captured around an optimized IR compile attempt.
 *
 * <p>The evidence may include raw pipeline-level comparison cases. A comparison mode identifies the stages being
 * compared so source-reconstruction checks cannot be mistaken for per-family optimizer proof. Production selection
 * remains governed by the separate fail-closed promotion and mutation gates.</p>
 */
public record GpuRuntimeEquivalenceEvidence(
        String status,
        String backendTarget,
        String vendor,
        String deviceLabel,
        String optimizationProfile,
        boolean executed,
        boolean equivalent,
        int inputCaseCount,
        int comparedOutputCount,
        List<String> diagnostics,
        List<GpuRuntimeEquivalenceCaseEvidence> comparisonCases
) {

    public GpuRuntimeEquivalenceEvidence {
        status = normalize(status, "not-run");
        backendTarget = normalize(backendTarget, "unknown");
        vendor = normalize(vendor, "unknown");
        deviceLabel = normalize(deviceLabel, "unknown");
        optimizationProfile = normalize(optimizationProfile, "off");
        inputCaseCount = Math.max(0, inputCaseCount);
        comparedOutputCount = Math.max(0, comparedOutputCount);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        comparisonCases = comparisonCases == null ? List.of() : List.copyOf(comparisonCases);
        if (comparisonCases.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("comparisonCases must not contain null entries");
        }
        if (!comparisonCases.isEmpty() && comparisonCases.size() != inputCaseCount) {
            throw new IllegalArgumentException("comparisonCases size must match inputCaseCount when recorded");
        }
        for (GpuRuntimeEquivalenceCaseEvidence caseEvidence : comparisonCases) {
            if (caseEvidence.referenceOutputs().size() != comparedOutputCount) {
                throw new IllegalArgumentException("comparison case output count must match comparedOutputCount");
            }
        }
    }

    public GpuRuntimeEquivalenceEvidence(
            String status,
            String backendTarget,
            String vendor,
            String deviceLabel,
            String optimizationProfile,
            boolean executed,
            boolean equivalent,
            int inputCaseCount,
            int comparedOutputCount,
            List<String> diagnostics
    ) {
        this(
                status,
                backendTarget,
                vendor,
                deviceLabel,
                optimizationProfile,
                executed,
                equivalent,
                inputCaseCount,
                comparedOutputCount,
                diagnostics,
                List.of()
        );
    }

    public static GpuRuntimeEquivalenceEvidence notRun(GpuRuntimeCompileRequest request, String reason) {
        GpuRuntimeCompileOptions options = request == null ? GpuRuntimeCompileOptions.defaults(null) : request.options();
        GpuRuntimeDeviceProfile deviceProfile = request == null
                ? GpuRuntimeDeviceProfile.generic(options.backendTarget(), "unknown")
                : request.deviceProfile();
        return new GpuRuntimeEquivalenceEvidence(
                "not-run",
                options.backendTarget().name(),
                deviceProfile.vendor(),
                deviceProfile.deviceLabel(),
                options.optimizationProfile(),
                false,
                false,
                0,
                0,
                reason == null || reason.isBlank() ? List.of("runtime equivalence was not executed") : List.of(reason),
                List.of()
        );
    }

    public static GpuRuntimeEquivalenceEvidence passed(
            GpuRuntimeCompileRequest request,
            int inputCaseCount,
            int comparedOutputCount,
            List<String> diagnostics
    ) {
        GpuRuntimeCompileOptions options = request == null ? GpuRuntimeCompileOptions.defaults(null) : request.options();
        GpuRuntimeDeviceProfile deviceProfile = request == null
                ? GpuRuntimeDeviceProfile.generic(options.backendTarget(), "unknown")
                : request.deviceProfile();
        return new GpuRuntimeEquivalenceEvidence(
                "passed",
                options.backendTarget().name(),
                deviceProfile.vendor(),
                deviceProfile.deviceLabel(),
                options.optimizationProfile(),
                true,
                true,
                inputCaseCount,
                comparedOutputCount,
                diagnostics,
                List.of()
        );
    }

    public static GpuRuntimeEquivalenceEvidence passed(
            GpuRuntimeCompileRequest request,
            int inputCaseCount,
            int comparedOutputCount,
            List<String> diagnostics,
            List<GpuRuntimeEquivalenceCaseEvidence> comparisonCases
    ) {
        GpuRuntimeCompileOptions options = request == null ? GpuRuntimeCompileOptions.defaults(null) : request.options();
        GpuRuntimeDeviceProfile deviceProfile = request == null
                ? GpuRuntimeDeviceProfile.generic(options.backendTarget(), "unknown")
                : request.deviceProfile();
        return new GpuRuntimeEquivalenceEvidence(
                "passed",
                options.backendTarget().name(),
                deviceProfile.vendor(),
                deviceProfile.deviceLabel(),
                options.optimizationProfile(),
                true,
                true,
                inputCaseCount,
                comparedOutputCount,
                diagnostics,
                comparisonCases
        );
    }

    public static GpuRuntimeEquivalenceEvidence failed(
            GpuRuntimeCompileRequest request,
            int inputCaseCount,
            int comparedOutputCount,
            List<String> diagnostics
    ) {
        GpuRuntimeCompileOptions options = request == null ? GpuRuntimeCompileOptions.defaults(null) : request.options();
        GpuRuntimeDeviceProfile deviceProfile = request == null
                ? GpuRuntimeDeviceProfile.generic(options.backendTarget(), "unknown")
                : request.deviceProfile();
        return new GpuRuntimeEquivalenceEvidence(
                "failed",
                options.backendTarget().name(),
                deviceProfile.vendor(),
                deviceProfile.deviceLabel(),
                options.optimizationProfile(),
                true,
                false,
                inputCaseCount,
                comparedOutputCount,
                diagnostics,
                List.of()
        );
    }

    public static GpuRuntimeEquivalenceEvidence failed(
            GpuRuntimeCompileRequest request,
            int inputCaseCount,
            int comparedOutputCount,
            List<String> diagnostics,
            List<GpuRuntimeEquivalenceCaseEvidence> comparisonCases
    ) {
        GpuRuntimeCompileOptions options = request == null ? GpuRuntimeCompileOptions.defaults(null) : request.options();
        GpuRuntimeDeviceProfile deviceProfile = request == null
                ? GpuRuntimeDeviceProfile.generic(options.backendTarget(), "unknown")
                : request.deviceProfile();
        return new GpuRuntimeEquivalenceEvidence(
                "failed",
                options.backendTarget().name(),
                deviceProfile.vendor(),
                deviceProfile.deviceLabel(),
                options.optimizationProfile(),
                true,
                false,
                inputCaseCount,
                comparedOutputCount,
                diagnostics,
                comparisonCases
        );
    }

    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("status=").append(status).append('\n');
        builder.append("backendTarget=").append(backendTarget).append('\n');
        builder.append("vendor=").append(vendor).append('\n');
        builder.append("deviceLabel=").append(deviceLabel).append('\n');
        builder.append("optimizationProfile=").append(optimizationProfile).append('\n');
        builder.append("executed=").append(executed).append('\n');
        builder.append("equivalent=").append(equivalent).append('\n');
        builder.append("inputCase.count=").append(inputCaseCount).append('\n');
        builder.append("comparedOutput.count=").append(comparedOutputCount).append('\n');
        builder.append("diagnostic.count=").append(diagnostics.size()).append('\n');
        for (int index = 0; index < diagnostics.size(); index++) {
            builder.append("diagnostic.").append(index).append('=').append(diagnostics.get(index)).append('\n');
        }
        builder.append("comparison.case.count=").append(comparisonCases.size()).append('\n');
        for (int caseIndex = 0; caseIndex < comparisonCases.size(); caseIndex++) {
            comparisonCases.get(caseIndex).appendProperties(builder, "comparison.case." + caseIndex + ".");
        }
        return builder.toString();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNull(fallback, "fallback") : value;
    }
}
