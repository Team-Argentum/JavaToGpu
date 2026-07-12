package net.sixik.ga_utils.javatogpu.iroptimizer;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Backend-neutral optimizer controls shared by proposal providers and runtime adapters.
 */
public record GpuIrOptimizationPolicy(
        String optimizerProfile,
        String optimizationLevel,
        boolean proposalOnly,
        boolean mutationAllowed,
        boolean fastMathAllowed,
        boolean vendorAdaptationAllowed,
        boolean registerPressureSplittingAllowed,
        boolean rollbackRequired,
        boolean proofRequired
) {

    public static final String OPTIMIZER_PROFILE_FIELD = "policy.optimizerProfile";
    public static final String OPTIMIZATION_LEVEL_FIELD = "policy.optimizationLevel";
    public static final String PROPOSAL_ONLY_FIELD = "policy.proposalOnly";
    public static final String MUTATION_ALLOWED_FIELD = "policy.mutationAllowed";
    public static final String FAST_MATH_ALLOWED_FIELD = "policy.fastMathAllowed";
    public static final String VENDOR_ADAPTATION_ALLOWED_FIELD = "policy.vendorAdaptationAllowed";
    public static final String REGISTER_PRESSURE_SPLITTING_ALLOWED_FIELD = "policy.registerPressureSplittingAllowed";
    public static final String ROLLBACK_REQUIRED_FIELD = "policy.rollbackRequired";
    public static final String PROOF_REQUIRED_FIELD = "policy.proofRequired";

    public GpuIrOptimizationPolicy {
        optimizerProfile = normalize(optimizerProfile, "off");
        optimizationLevel = normalize(optimizationLevel, optimizerProfile);
        proposalOnly = proposalOnly || !mutationAllowed;
    }

    public static GpuIrOptimizationPolicy off() {
        return fromContext("off", false, Map.of());
    }

    public static GpuIrOptimizationPolicy fromContext(
            String optimizerProfile,
            boolean mutationAllowed,
            Map<String, String> contextFields
    ) {
        Map<String, String> fields = contextFields == null ? Map.of() : contextFields;
        boolean resolvedMutationAllowed = parseBoolean(
                firstNonBlank(fields.get(MUTATION_ALLOWED_FIELD), fields.get("mutationAllowed")),
                mutationAllowed
        );
        return new GpuIrOptimizationPolicy(
                firstNonBlank(fields.get(OPTIMIZER_PROFILE_FIELD), fields.get("optimizationProfile"), optimizerProfile),
                firstNonBlank(fields.get(OPTIMIZATION_LEVEL_FIELD), fields.get("optimizationLevel"), optimizerProfile),
                parseBoolean(firstNonBlank(fields.get(PROPOSAL_ONLY_FIELD), fields.get("proposalOnly")), !resolvedMutationAllowed),
                resolvedMutationAllowed,
                parseBoolean(firstNonBlank(fields.get(FAST_MATH_ALLOWED_FIELD), fields.get("fastMathAllowed")), false),
                parseBoolean(firstNonBlank(fields.get(VENDOR_ADAPTATION_ALLOWED_FIELD), fields.get("vendorAdaptationAllowed")), false),
                parseBoolean(firstNonBlank(
                        fields.get(REGISTER_PRESSURE_SPLITTING_ALLOWED_FIELD),
                        fields.get("registerPressureSplittingAllowed")
                ), false),
                parseBoolean(firstNonBlank(fields.get(ROLLBACK_REQUIRED_FIELD), fields.get("rollbackRequired")), true),
                parseBoolean(firstNonBlank(fields.get(PROOF_REQUIRED_FIELD), fields.get("proofRequired")), true)
        );
    }

    public Map<String, String> asContextFields() {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(OPTIMIZER_PROFILE_FIELD, optimizerProfile);
        fields.put(OPTIMIZATION_LEVEL_FIELD, optimizationLevel);
        fields.put(PROPOSAL_ONLY_FIELD, Boolean.toString(proposalOnly));
        fields.put(MUTATION_ALLOWED_FIELD, Boolean.toString(mutationAllowed));
        fields.put(FAST_MATH_ALLOWED_FIELD, Boolean.toString(fastMathAllowed));
        fields.put(VENDOR_ADAPTATION_ALLOWED_FIELD, Boolean.toString(vendorAdaptationAllowed));
        fields.put(REGISTER_PRESSURE_SPLITTING_ALLOWED_FIELD, Boolean.toString(registerPressureSplittingAllowed));
        fields.put(ROLLBACK_REQUIRED_FIELD, Boolean.toString(rollbackRequired));
        fields.put(PROOF_REQUIRED_FIELD, Boolean.toString(proofRequired));
        return Map.copyOf(fields);
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "enabled", "allow", "allowed", "required" -> true;
            case "false", "no", "disabled", "deny", "denied", "optional" -> false;
            default -> fallback;
        };
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
