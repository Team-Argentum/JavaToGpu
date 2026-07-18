package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Duration;

/**
 * Backend-specific compile options kept separate from legacy OpenCL-style command-line args.
 */
public record GpuBackendCompileOptions(
        GpuBackendTarget backendTarget,
        List<String> flags,
        Map<String, String> properties
) {

    public static final String OPENCL_SOURCE_SELECTION_PROPERTY = "opencl.sourceSelection";
    public static final String OPENCL_SOURCE_SELECTION_DESCRIPTOR = "descriptor";
    public static final String OPENCL_SOURCE_SELECTION_IRGPU = "irgpu";
    public static final String OPENCL_PRODUCTION_SOURCE_SWITCHING_PROPERTY = "opencl.productionSourceSwitching";
    public static final String OPENCL_PRODUCTION_SOURCE_SWITCHING_DISABLED = "disabled";
    public static final String OPENCL_PRODUCTION_SOURCE_SWITCHING_ENABLED = "enabled";
    public static final String RUNTIME_IR_OPTIMIZER_SELECTION_PROPERTY = "runtime.irOptimizerSelection";
    public static final String RUNTIME_IR_OPTIMIZER_SELECTION_REVIEW_ONLY = "review-only";
    public static final String RUNTIME_IR_OPTIMIZER_SELECTION_EXPERIMENTAL_APPLY = "experimental-apply";
    public static final String RUNTIME_BACKEND_DEVICE_PREFLIGHT_PROPERTY = "runtime.backendDevicePreflight";
    public static final String RUNTIME_BACKEND_DEVICE_PREFLIGHT_DISABLED = "disabled";
    public static final String RUNTIME_BACKEND_DEVICE_PREFLIGHT_STANDARD = "standard";
    public static final String PRODUCTION_PROMOTION_DECISION_MODE_PROPERTY = "productionPromotion.decisionMode";
    public static final String PRODUCTION_PROMOTION_OPERATOR_ACCEPTED_PROPERTY = "productionPromotion.operatorAccepted";
    public static final String RUNTIME_DEVICE_SELF_TEST_PROPERTY = "runtime.deviceSelfTest";
    public static final String RUNTIME_METHOD_TEST_PROBE_MODE_PROPERTY = "runtime.methodTestProbeMode";
    public static final String RUNTIME_METHOD_TEST_PROBE_MODE_DISABLED = "disabled";
    public static final String RUNTIME_METHOD_TEST_PROBE_MODE_CACHE_ONLY = "cache-only";
    public static final String RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_PROPERTY = "runtime.methodTestProbeEvidenceRanking";
    public static final String RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_DISABLED = "disabled";
    public static final String RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_CACHED = "cached";
    public static final String RUNTIME_METHOD_TEST_PROBE_EVIDENCE_CACHE_PATH_PROPERTY = "runtime.methodTestProbeEvidenceCachePath";
    public static final String RUNTIME_METHOD_TEST_PROBE_EVIDENCE_MAX_AGE_MILLIS_PROPERTY = "runtime.methodTestProbeEvidenceMaxAgeMillis";

    public GpuBackendCompileOptions {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        flags = flags == null ? List.of() : List.copyOf(flags);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public static GpuBackendCompileOptions empty(GpuBackendTarget backendTarget) {
        return new GpuBackendCompileOptions(backendTarget, List.of(), Map.of());
    }

    public static GpuBackendCompileOptions openCl(List<String> compileArgs) {
        return new GpuBackendCompileOptions(GpuBackendTarget.OPENCL, compileArgs, Map.of());
    }

    public static GpuBackendCompileOptions openCl(List<String> compileArgs, Map<String, String> properties) {
        return new GpuBackendCompileOptions(GpuBackendTarget.OPENCL, compileArgs, properties);
    }

    public static GpuBackendCompileOptions openClIrGpuSource(List<String> compileArgs) {
        return openCl(
                compileArgs,
                Map.of(OPENCL_SOURCE_SELECTION_PROPERTY, OPENCL_SOURCE_SELECTION_IRGPU)
        );
    }

    public static GpuBackendCompileOptions openClProductionIrGpuSource(List<String> compileArgs) {
        return openCl(
                compileArgs,
                Map.of(
                        OPENCL_SOURCE_SELECTION_PROPERTY,
                        OPENCL_SOURCE_SELECTION_IRGPU,
                        OPENCL_PRODUCTION_SOURCE_SWITCHING_PROPERTY,
                        OPENCL_PRODUCTION_SOURCE_SWITCHING_ENABLED
                )
        );
    }

    public static GpuBackendCompileOptions cuda(List<String> nvrtcOptions, Map<String, String> properties) {
        return new GpuBackendCompileOptions(GpuBackendTarget.CUDA, nvrtcOptions, properties);
    }

    public static GpuBackendCompileOptions vulkan(List<String> spirvOptions, Map<String, String> properties) {
        return new GpuBackendCompileOptions(GpuBackendTarget.VULKAN, spirvOptions, properties);
    }

    public static GpuBackendCompileOptions metal(List<String> metalOptions, Map<String, String> properties) {
        return new GpuBackendCompileOptions(GpuBackendTarget.METAL, metalOptions, properties);
    }

    public boolean empty() {
        return flags.isEmpty() && properties.isEmpty();
    }

    public boolean requestsOpenClIrGpuSource() {
        return backendTarget == GpuBackendTarget.OPENCL
                && OPENCL_SOURCE_SELECTION_IRGPU.equals(properties.get(OPENCL_SOURCE_SELECTION_PROPERTY));
    }

    public boolean enablesOpenClProductionSourceSwitching() {
        return backendTarget == GpuBackendTarget.OPENCL
                && OPENCL_PRODUCTION_SOURCE_SWITCHING_ENABLED.equals(
                properties.get(OPENCL_PRODUCTION_SOURCE_SWITCHING_PROPERTY)
        );
    }

    public boolean requestsRuntimeIrOptimizerExperimentalApply() {
        return RUNTIME_IR_OPTIMIZER_SELECTION_EXPERIMENTAL_APPLY.equals(
                properties.get(RUNTIME_IR_OPTIMIZER_SELECTION_PROPERTY)
        );
    }

    public String backendDevicePreflightMode() {
        if (backendDevicePreflightModeBlocker().isPresent()) {
            return RUNTIME_BACKEND_DEVICE_PREFLIGHT_DISABLED;
        }
        String value = properties.get(RUNTIME_BACKEND_DEVICE_PREFLIGHT_PROPERTY);
        return value == null || value.isBlank() ? RUNTIME_BACKEND_DEVICE_PREFLIGHT_DISABLED : value;
    }

    public Optional<String> backendDevicePreflightModeBlocker() {
        String value = properties.get(RUNTIME_BACKEND_DEVICE_PREFLIGHT_PROPERTY);
        if (value == null || value.isBlank()
                || RUNTIME_BACKEND_DEVICE_PREFLIGHT_DISABLED.equals(value)
                || RUNTIME_BACKEND_DEVICE_PREFLIGHT_STANDARD.equals(value)) {
            return Optional.empty();
        }
        return Optional.of("runtime-backend-device-preflight-mode-invalid");
    }

    public boolean requestsStandardBackendDevicePreflight() {
        return backendDevicePreflightModeBlocker().isEmpty()
                && RUNTIME_BACKEND_DEVICE_PREFLIGHT_STANDARD.equals(backendDevicePreflightMode());
    }

    public GpuBackendCompileOptions withStandardBackendDevicePreflight() {
        Map<String, String> updated = new LinkedHashMap<>(properties);
        updated.put(RUNTIME_BACKEND_DEVICE_PREFLIGHT_PROPERTY, RUNTIME_BACKEND_DEVICE_PREFLIGHT_STANDARD);
        return new GpuBackendCompileOptions(backendTarget, flags, updated);
    }

    public GpuBackendCompileOptions withoutBackendDevicePreflight() {
        Map<String, String> updated = new LinkedHashMap<>(properties);
        updated.remove(RUNTIME_BACKEND_DEVICE_PREFLIGHT_PROPERTY);
        return new GpuBackendCompileOptions(backendTarget, flags, updated);
    }

    public GpuBackendCompileOptions withRuntimeIrOptimizerSelection(String selection) {
        String normalizedSelection = selection == null || selection.isBlank()
                ? RUNTIME_IR_OPTIMIZER_SELECTION_REVIEW_ONLY
                : selection;
        Map<String, String> updated = new LinkedHashMap<>(properties);
        if (RUNTIME_IR_OPTIMIZER_SELECTION_REVIEW_ONLY.equals(normalizedSelection)) {
            updated.remove(RUNTIME_IR_OPTIMIZER_SELECTION_PROPERTY);
        } else {
            updated.put(RUNTIME_IR_OPTIMIZER_SELECTION_PROPERTY, normalizedSelection);
        }
        return new GpuBackendCompileOptions(backendTarget, flags, updated);
    }

    public GpuBackendCompileOptions withRuntimeIrOptimizerExperimentalApply() {
        return withRuntimeIrOptimizerSelection(RUNTIME_IR_OPTIMIZER_SELECTION_EXPERIMENTAL_APPLY);
    }

    public String productionPromotionDecisionMode() {
        return properties.getOrDefault(
                PRODUCTION_PROMOTION_DECISION_MODE_PROPERTY,
                GpuProductionPromotionDecision.DIAGNOSTIC_ONLY
        );
    }

    public boolean productionPromotionOperatorAccepted() {
        return "true".equals(properties.getOrDefault(PRODUCTION_PROMOTION_OPERATOR_ACCEPTED_PROPERTY, "false"));
    }

    public Optional<GpuProductionPromotionOperatorAcceptance> productionPromotionOperatorAcceptance() {
        return GpuProductionPromotionOperatorAcceptance.from(this);
    }

    public Optional<GpuProductionActivationToken> productionActivationToken() {
        return GpuProductionActivationToken.from(this);
    }

    public GpuRuntimeDeviceSelfTestMode deviceSelfTestMode() {
        return GpuRuntimeDeviceSelfTestMode.parse(properties.get(RUNTIME_DEVICE_SELF_TEST_PROPERTY));
    }

    public GpuRuntimeMethodTestProbeMode methodTestProbeMode() {
        if (methodTestProbeModeBlocker().isPresent()) {
            return GpuRuntimeMethodTestProbeMode.DISABLED;
        }
        String explicitMode = properties.get(RUNTIME_METHOD_TEST_PROBE_MODE_PROPERTY);
        if (explicitMode != null && !explicitMode.isBlank()) {
            return GpuRuntimeMethodTestProbeMode.parse(explicitMode)
                    .orElse(GpuRuntimeMethodTestProbeMode.DISABLED);
        }
        if (RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_CACHED.equals(
                properties.get(RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_PROPERTY)
        )) {
            return GpuRuntimeMethodTestProbeMode.CACHE_ONLY;
        }
        return GpuRuntimeMethodTestProbeMode.DISABLED;
    }

    public Optional<String> methodTestProbeModeBlocker() {
        String explicitMode = properties.get(RUNTIME_METHOD_TEST_PROBE_MODE_PROPERTY);
        if (explicitMode != null && !explicitMode.isBlank()
                && GpuRuntimeMethodTestProbeMode.parse(explicitMode).isEmpty()) {
            return Optional.of("runtime-method-test-probe-mode-invalid");
        }
        String legacyRanking = properties.get(RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_PROPERTY);
        if (legacyRanking != null && !legacyRanking.isBlank()
                && !RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_CACHED.equals(legacyRanking)
                && !RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_DISABLED.equals(legacyRanking)) {
            return Optional.of("runtime-method-test-probe-evidence-ranking-invalid");
        }
        return Optional.empty();
    }

    public boolean requestsMethodTestProbeEvidenceRanking() {
        return methodTestProbeMode() == GpuRuntimeMethodTestProbeMode.CACHE_ONLY
                && methodTestProbeModeBlocker().isEmpty();
    }

    public Optional<String> methodTestProbeEvidenceCachePath() {
        String value = properties.get(RUNTIME_METHOD_TEST_PROBE_EVIDENCE_CACHE_PATH_PROPERTY);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    public Optional<Duration> methodTestProbeEvidenceMaxAge() {
        String value = properties.get(RUNTIME_METHOD_TEST_PROBE_EVIDENCE_MAX_AGE_MILLIS_PROPERTY);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            long millis = Long.parseLong(value.trim());
            return millis <= 0L ? Optional.empty() : Optional.of(Duration.ofMillis(millis));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public GpuBackendCompileOptions withProductionPromotionDecision(GpuProductionPromotionDecision decision) {
        GpuProductionPromotionDecision normalized = decision == null
                ? GpuProductionPromotionDecision.diagnosticOnly()
                : decision;
        Map<String, String> updated = new LinkedHashMap<>(properties);
        updated.put(PRODUCTION_PROMOTION_DECISION_MODE_PROPERTY, normalized.mode());
        return new GpuBackendCompileOptions(backendTarget, flags, updated);
    }

    public GpuBackendCompileOptions withProductionPromotionOperatorAccepted(boolean accepted) {
        Map<String, String> updated = new LinkedHashMap<>(properties);
        updated.put(PRODUCTION_PROMOTION_OPERATOR_ACCEPTED_PROPERTY, Boolean.toString(accepted));
        return new GpuBackendCompileOptions(backendTarget, flags, updated);
    }

    public GpuBackendCompileOptions withProductionPromotionOperatorAcceptance(
            GpuProductionPromotionOperatorAcceptance acceptance
    ) {
        Map<String, String> updated = new LinkedHashMap<>(properties);
        for (String propertyName : GpuProductionPromotionOperatorAcceptance.propertyNames()) {
            updated.remove(propertyName);
        }
        if (acceptance == null) {
            updated.put(PRODUCTION_PROMOTION_OPERATOR_ACCEPTED_PROPERTY, "false");
        } else {
            updated.put(PRODUCTION_PROMOTION_OPERATOR_ACCEPTED_PROPERTY, "true");
            updated.putAll(acceptance.properties());
        }
        return new GpuBackendCompileOptions(backendTarget, flags, updated);
    }

    public GpuBackendCompileOptions withProductionActivationToken(GpuProductionActivationToken token) {
        Map<String, String> updated = new LinkedHashMap<>(properties);
        updated.keySet().removeIf(key -> key.startsWith(GpuProductionActivationToken.PROPERTY_PREFIX));
        if (token != null) {
            updated.putAll(token.properties());
        }
        return new GpuBackendCompileOptions(backendTarget, flags, updated);
    }

    public GpuBackendCompileOptions withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode mode) {
        GpuRuntimeDeviceSelfTestMode normalized = mode == null ? GpuRuntimeDeviceSelfTestMode.AUTO : mode;
        Map<String, String> updated = new LinkedHashMap<>(properties);
        updated.put(RUNTIME_DEVICE_SELF_TEST_PROPERTY, normalized.optionValue());
        return new GpuBackendCompileOptions(backendTarget, flags, updated);
    }

    public GpuBackendCompileOptions withMethodTestProbeEvidenceRankingCached() {
        return withMethodTestProbeMode(GpuRuntimeMethodTestProbeMode.CACHE_ONLY);
    }

    public GpuBackendCompileOptions withMethodTestProbeMode(GpuRuntimeMethodTestProbeMode mode) {
        GpuRuntimeMethodTestProbeMode normalized = mode == null ? GpuRuntimeMethodTestProbeMode.DISABLED : mode;
        Map<String, String> updated = new LinkedHashMap<>(properties);
        if (normalized == GpuRuntimeMethodTestProbeMode.DISABLED) {
            updated.put(RUNTIME_METHOD_TEST_PROBE_MODE_PROPERTY, RUNTIME_METHOD_TEST_PROBE_MODE_DISABLED);
            updated.remove(RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_PROPERTY);
        } else {
            updated.put(RUNTIME_METHOD_TEST_PROBE_MODE_PROPERTY, normalized.optionValue());
            updated.put(
                    RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_PROPERTY,
                    RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_CACHED
            );
        }
        return new GpuBackendCompileOptions(backendTarget, flags, updated);
    }

    public GpuBackendCompileOptions withPersistentMethodTestProbeEvidenceRanking(
            java.nio.file.Path cacheDirectory,
            Duration maxEntryAge
    ) {
        Map<String, String> updated = new LinkedHashMap<>(withMethodTestProbeMode(GpuRuntimeMethodTestProbeMode.CACHE_ONLY).properties());
        if (cacheDirectory != null) {
            updated.put(RUNTIME_METHOD_TEST_PROBE_EVIDENCE_CACHE_PATH_PROPERTY, cacheDirectory.toString());
        }
        if (maxEntryAge != null && !maxEntryAge.isNegative() && !maxEntryAge.isZero()) {
            updated.put(
                    RUNTIME_METHOD_TEST_PROBE_EVIDENCE_MAX_AGE_MILLIS_PROPERTY,
                    Long.toString(maxEntryAge.toMillis())
            );
        }
        return new GpuBackendCompileOptions(backendTarget, flags, updated);
    }

    public GpuBackendCompileOptions withoutMethodTestProbeEvidenceRanking() {
        Map<String, String> updated = new LinkedHashMap<>(properties);
        updated.remove(RUNTIME_METHOD_TEST_PROBE_MODE_PROPERTY);
        updated.remove(RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_PROPERTY);
        updated.remove(RUNTIME_METHOD_TEST_PROBE_EVIDENCE_CACHE_PATH_PROPERTY);
        updated.remove(RUNTIME_METHOD_TEST_PROBE_EVIDENCE_MAX_AGE_MILLIS_PROPERTY);
        return new GpuBackendCompileOptions(backendTarget, flags, updated);
    }

    public Map<String, String> stableProperties() {
        return new LinkedHashMap<>(new java.util.TreeMap<>(properties));
    }
}
