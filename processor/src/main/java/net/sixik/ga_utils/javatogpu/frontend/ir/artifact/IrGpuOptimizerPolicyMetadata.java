package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Method-level optimizer policy stored in an IrGpu artifact.
 */
public record IrGpuOptimizerPolicyMetadata(
        boolean fastMath,
        boolean enabled,
        String profile,
        List<String> enabledFamilies,
        List<String> disabledFamilies,
        boolean journal,
        boolean dumpArtifacts,
        boolean productionIntent,
        boolean vendorAdaptation,
        String vectorization,
        boolean resourceShaping,
        String source
) {

    public IrGpuOptimizerPolicyMetadata {
        profile = normalizeText(profile, "off");
        enabledFamilies = normalizeFamilies(enabledFamilies);
        disabledFamilies = normalizeFamilies(disabledFamilies);
        vectorization = normalizeText(vectorization, "auto");
        source = source == null || source.isBlank() ? "default-strict" : source;
    }

    public IrGpuOptimizerPolicyMetadata(boolean fastMath, String source) {
        this(
                fastMath,
                false,
                "off",
                List.of(),
                List.of(),
                false,
                false,
                false,
                false,
                "auto",
                false,
                source
        );
    }

    public static IrGpuOptimizerPolicyMetadata defaultStrict() {
        return new IrGpuOptimizerPolicyMetadata(false, "default-strict");
    }

    public static IrGpuOptimizerPolicyMetadata fromGpuOptimize(boolean fastMath) {
        return fromGpuOptimize(
                fastMath,
                true,
                "default",
                List.of(),
                List.of(),
                false,
                false,
                false,
                false,
                "auto",
                false
        );
    }

    public static IrGpuOptimizerPolicyMetadata fromGpuOptimize(
            boolean fastMath,
            boolean enabled,
            String profile,
            List<String> enabledFamilies,
            List<String> disabledFamilies,
            boolean journal,
            boolean dumpArtifacts,
            boolean productionIntent,
            boolean vendorAdaptation,
            String vectorization,
            boolean resourceShaping
    ) {
        return new IrGpuOptimizerPolicyMetadata(
                fastMath,
                enabled,
                profile,
                enabledFamilies,
                disabledFamilies,
                journal,
                dumpArtifacts,
                productionIntent,
                vendorAdaptation,
                vectorization,
                resourceShaping,
                "GPUOptimize"
        );
    }

    public boolean hasFamilyFilter() {
        return !enabledFamilies.isEmpty() || !disabledFamilies.isEmpty();
    }

    public boolean allowsFamily(String family) {
        String normalized = normalizeFamily(family);
        if (normalized.isBlank() || !enabled || disabledFamilies.contains(normalized)) {
            return false;
        }
        return enabledFamilies.isEmpty() || enabledFamilies.contains(normalized);
    }

    private static List<String> normalizeFamilies(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String family = normalizeFamily(value);
            if (!family.isBlank()) {
                normalized.add(family);
            }
        }
        return List.copyOf(normalized);
    }

    private static String normalizeFamily(String value) {
        String family = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (String suffix : List.of("-materialization", "-preview")) {
            if (family.endsWith(suffix)) {
                family = family.substring(0, family.length() - suffix.length());
            }
        }
        return switch (family) {
            case "mad", "fma", "madfma", "mad/fma" -> "mad-fma";
            case "cse", "safe-cse" -> "safe-local-cse";
            case "const-folding", "constantfolding" -> "constant-folding";
            case "dce", "typed-dce", "dead-code" -> "typed-dead-code";
            case "vectorization" -> "loop-vectorization";
            default -> family;
        };
    }

    private static String normalizeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
