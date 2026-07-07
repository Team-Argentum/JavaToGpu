package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Pluggable advisory strategy hook for backend/vendor/device-family optimization choices.
 */
@FunctionalInterface
public interface GpuOptimizationStrategy {

    GpuOptimizationStrategyDecision select(GpuRuntimeCompileRequest request);

    static GpuOptimizationStrategy advisoryDefault() {
        return request -> {
            if (request == null) {
                return GpuOptimizationStrategyDecision.none(null);
            }
            GpuRuntimeDeviceProfile profile = request.deviceProfile();
            String vendor = profile.vendor();
            String normalizedVendor = vendor.toLowerCase(java.util.Locale.ROOT);
            if (normalizedVendor.contains("nvidia")) {
                return GpuOptimizationStrategyDecision.advisory(
                        "strategy:opencl-nvidia-advisory",
                        "nvidia",
                        request.options().optimizationProfile(),
                        "NVIDIA path stays conservative until runtime-equivalence promotion gates are stable",
                        GpuOptimizationVendorBaseline.nvidiaRecorded(),
                        java.util.List.of(
                                "prefer scalar-safe lowering for now",
                                "vectorization remains proof-gated and disabled by default"
                        )
                );
            }
            if (normalizedVendor.contains("amd") || normalizedVendor.contains("advanced micro devices")) {
                return GpuOptimizationStrategyDecision.advisory(
                        "strategy:opencl-amd-advisory",
                        "amd",
                        request.options().optimizationProfile(),
                        "AMD vector-friendly strategy is reserved until real AMD hardware baselines exist",
                        GpuOptimizationVendorBaseline.pendingHardware("amd"),
                        java.util.List.of("do not promote vectorization without AMD runtime evidence")
                );
            }
            if (normalizedVendor.contains("intel")) {
                return GpuOptimizationStrategyDecision.advisory(
                        "strategy:opencl-intel-advisory",
                        "intel",
                        request.options().optimizationProfile(),
                        "Intel vector-friendly strategy is reserved until real Intel hardware baselines exist",
                        GpuOptimizationVendorBaseline.pendingHardware("intel"),
                        java.util.List.of("do not promote vectorization without Intel runtime evidence")
                );
            }
            return GpuOptimizationStrategyDecision.none(request);
        };
    }
}
