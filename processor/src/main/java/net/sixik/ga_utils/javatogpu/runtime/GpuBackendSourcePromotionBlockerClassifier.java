package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Compatibility facade for backend-source promotion blocker classification.
 */
public final class GpuBackendSourcePromotionBlockerClassifier {

    public static final String RECONSTRUCTION =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuBackendSourcePromotionBlockerClassifier.RECONSTRUCTION;
    public static final String SOURCE_PARITY =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuBackendSourcePromotionBlockerClassifier.SOURCE_PARITY;
    public static final String RUNTIME_EQUIVALENCE =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuBackendSourcePromotionBlockerClassifier.RUNTIME_EQUIVALENCE;
    public static final String FALLBACK_CLEAN =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuBackendSourcePromotionBlockerClassifier.FALLBACK_CLEAN;
    public static final String OTHER =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuBackendSourcePromotionBlockerClassifier.OTHER;

    private GpuBackendSourcePromotionBlockerClassifier() {
    }

    public static String classify(String diagnostic) {
        return net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuBackendSourcePromotionBlockerClassifier.classify(
                diagnostic
        );
    }
}
