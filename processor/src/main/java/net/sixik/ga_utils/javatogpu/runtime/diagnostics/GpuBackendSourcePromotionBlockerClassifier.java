package net.sixik.ga_utils.javatogpu.runtime.diagnostics;

import java.util.Locale;

/**
 * Classifies backend-source promotion diagnostics into stable blocker families.
 *
 * <p>The families are intentionally backend-neutral so OpenCL, CUDA, Vulkan, and Metal promotion gates can share the
 * same report/history vocabulary.</p>
 */
public final class GpuBackendSourcePromotionBlockerClassifier {

    public static final String RECONSTRUCTION = "reconstruction";
    public static final String SOURCE_PARITY = "source-parity";
    public static final String RUNTIME_EQUIVALENCE = "runtime-equivalence";
    public static final String FALLBACK_CLEAN = "fallback-clean";
    public static final String OTHER = "other";

    private GpuBackendSourcePromotionBlockerClassifier() {
    }

    public static String classify(String diagnostic) {
        String value = diagnostic == null ? "" : diagnostic.toLowerCase(Locale.ROOT);
        if (value.contains("runtime equivalence")) {
            return RUNTIME_EQUIVALENCE;
        }
        if (value.contains("fallback")) {
            return FALLBACK_CLEAN;
        }
        if (value.contains("parity") || value.contains("match descriptor") || value.contains("source must match")) {
            return SOURCE_PARITY;
        }
        if (value.contains("reconstruct") || value.contains("source payload") || value.contains("backend source")) {
            return RECONSTRUCTION;
        }
        return OTHER;
    }
}
