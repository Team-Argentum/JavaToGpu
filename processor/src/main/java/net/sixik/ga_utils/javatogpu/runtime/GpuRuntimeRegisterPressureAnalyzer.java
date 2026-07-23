package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

/**
 * Compatibility facade for advisory typed-IR register-pressure analysis.
 */
public final class GpuRuntimeRegisterPressureAnalyzer {

    public static final String ANALYSIS_VERSION =
            net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeRegisterPressureAnalyzer.ANALYSIS_VERSION;

    private GpuRuntimeRegisterPressureAnalyzer() {
    }

    public static GpuRuntimeRegisterPressureReport analyze(
            IrGpuArtifact artifact,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        return net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeRegisterPressureAnalyzer.analyze(
                artifact,
                deviceProfile
        );
    }
}
