package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Identity-bound operator acceptance for one production promotion context.
 */
public record GpuProductionPromotionOperatorAcceptance(
        String acceptanceId,
        GpuBackendTarget backendTarget,
        String deviceVendor,
        String deviceLabel,
        String driverVersion,
        String optimizationProfile,
        String sourceKernelResource,
        String decisionMode
) {

    public static final String ACCEPTANCE_ID_PROPERTY = "productionPromotion.acceptance.id";
    public static final String BACKEND_TARGET_PROPERTY = "productionPromotion.acceptance.backendTarget";
    public static final String DEVICE_VENDOR_PROPERTY = "productionPromotion.acceptance.deviceVendor";
    public static final String DEVICE_LABEL_PROPERTY = "productionPromotion.acceptance.deviceLabel";
    public static final String DRIVER_VERSION_PROPERTY = "productionPromotion.acceptance.driverVersion";
    public static final String OPTIMIZATION_PROFILE_PROPERTY = "productionPromotion.acceptance.optimizationProfile";
    public static final String SOURCE_KERNEL_RESOURCE_PROPERTY = "productionPromotion.acceptance.sourceKernelResource";
    public static final String DECISION_MODE_PROPERTY = "productionPromotion.acceptance.decisionMode";

    private static final Set<String> PROPERTY_NAMES = Set.of(
            ACCEPTANCE_ID_PROPERTY,
            BACKEND_TARGET_PROPERTY,
            DEVICE_VENDOR_PROPERTY,
            DEVICE_LABEL_PROPERTY,
            DRIVER_VERSION_PROPERTY,
            OPTIMIZATION_PROFILE_PROPERTY,
            SOURCE_KERNEL_RESOURCE_PROPERTY,
            DECISION_MODE_PROPERTY
    );

    public GpuProductionPromotionOperatorAcceptance {
        acceptanceId = normalize(acceptanceId, "acceptance:missing");
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        deviceVendor = normalize(deviceVendor, "unknown");
        deviceLabel = normalize(deviceLabel, "unknown");
        driverVersion = normalize(driverVersion, "unknown");
        optimizationProfile = normalize(optimizationProfile, "off");
        sourceKernelResource = normalize(sourceKernelResource, "unknown");
        decisionMode = normalize(decisionMode, GpuProductionPromotionDecision.DIAGNOSTIC_ONLY);
    }

    public static GpuProductionPromotionOperatorAcceptance forContext(
            String acceptanceId,
            GpuBackendTarget backendTarget,
            GpuRuntimeDeviceProfile deviceProfile,
            String optimizationProfile,
            GpuKernelDescriptor descriptor,
            String decisionMode
    ) {
        GpuRuntimeDeviceProfile device = deviceProfile == null
                ? GpuRuntimeDeviceProfile.generic(backendTarget, "unknown")
                : deviceProfile;
        return new GpuProductionPromotionOperatorAcceptance(
                acceptanceId,
                backendTarget,
                device.vendor(),
                device.deviceLabel(),
                device.driverVersion(),
                optimizationProfile,
                descriptor == null ? "unknown" : descriptor.kernelResource(),
                decisionMode
        );
    }

    public static Optional<GpuProductionPromotionOperatorAcceptance> from(GpuBackendCompileOptions options) {
        if (options == null) {
            return Optional.empty();
        }
        Map<String, String> properties = options.properties();
        String acceptanceId = properties.get(ACCEPTANCE_ID_PROPERTY);
        if (acceptanceId == null || acceptanceId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new GpuProductionPromotionOperatorAcceptance(
                acceptanceId,
                parseBackendTarget(properties.get(BACKEND_TARGET_PROPERTY)),
                properties.get(DEVICE_VENDOR_PROPERTY),
                properties.get(DEVICE_LABEL_PROPERTY),
                properties.get(DRIVER_VERSION_PROPERTY),
                properties.get(OPTIMIZATION_PROFILE_PROPERTY),
                properties.get(SOURCE_KERNEL_RESOURCE_PROPERTY),
                properties.get(DECISION_MODE_PROPERTY)
        ));
    }

    public static Result evaluate(GpuRuntimeCompileRequest request) {
        if (request == null) {
            return Result.blocked("acceptance:missing", "operator-acceptance-request-missing");
        }
        GpuBackendCompileOptions options = request.options().backendOptions();
        if (!options.productionPromotionOperatorAccepted()) {
            return Result.blocked("acceptance:missing", "operator-acceptance-flag-not-set");
        }
        return from(options)
                .map(acceptance -> acceptance.evaluateAgainst(request))
                .orElseGet(() -> Result.blocked(
                        "acceptance:missing",
                        "operator-acceptance-binding-missing"
                ));
    }

    public Result evaluateAgainst(GpuRuntimeCompileRequest request) {
        if (request == null) {
            return Result.blocked(acceptanceId, "operator-acceptance-request-missing");
        }
        ArrayList<String> blockers = new ArrayList<>();
        GpuRuntimeCompileOptions options = request.options();
        GpuRuntimeDeviceProfile device = request.deviceProfile();
        if (backendTarget != options.backendTarget()) {
            blockers.add("operator-acceptance-backend-target-mismatch");
        }
        if (!deviceVendor.equals(device.vendor())) {
            blockers.add("operator-acceptance-device-vendor-mismatch");
        }
        if (!deviceLabel.equals(device.deviceLabel())) {
            blockers.add("operator-acceptance-device-label-mismatch");
        }
        if (!driverVersion.equals(device.driverVersion())) {
            blockers.add("operator-acceptance-driver-version-mismatch");
        }
        if (!optimizationProfile.equals(options.optimizationProfile())) {
            blockers.add("operator-acceptance-optimization-profile-mismatch");
        }
        if (!sourceKernelResource.equals(request.descriptor().kernelResource())) {
            blockers.add("operator-acceptance-kernel-resource-mismatch");
        }
        if (!decisionMode.equals(options.backendOptions().productionPromotionDecisionMode())) {
            blockers.add("operator-acceptance-decision-mode-mismatch");
        }
        return blockers.isEmpty()
                ? Result.accepted(acceptanceId)
                : new Result(false, "blocked", acceptanceId, blockers);
    }

    public Map<String, String> properties() {
        LinkedHashMap<String, String> properties = new LinkedHashMap<>();
        properties.put(ACCEPTANCE_ID_PROPERTY, acceptanceId);
        properties.put(BACKEND_TARGET_PROPERTY, backendTarget.name());
        properties.put(DEVICE_VENDOR_PROPERTY, deviceVendor);
        properties.put(DEVICE_LABEL_PROPERTY, deviceLabel);
        properties.put(DRIVER_VERSION_PROPERTY, driverVersion);
        properties.put(OPTIMIZATION_PROFILE_PROPERTY, optimizationProfile);
        properties.put(SOURCE_KERNEL_RESOURCE_PROPERTY, sourceKernelResource);
        properties.put(DECISION_MODE_PROPERTY, decisionMode);
        return Map.copyOf(properties);
    }

    public static Set<String> propertyNames() {
        return PROPERTY_NAMES;
    }

    public static Result legacy(boolean accepted) {
        return accepted
                ? Result.accepted("legacy-unbound")
                : Result.blocked("legacy-unbound", "production-promotion-not-operator-accepted");
    }

    private static GpuBackendTarget parseBackendTarget(String value) {
        try {
            return GpuBackendTarget.valueOf(normalize(value, "UNKNOWN").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return GpuBackendTarget.UNKNOWN;
        }
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record Result(boolean accepted, String status, String acceptanceId, List<String> blockers) {
        public Result {
            status = normalize(status, accepted ? "accepted" : "blocked");
            acceptanceId = normalize(acceptanceId, "acceptance:missing");
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            if (accepted && !blockers.isEmpty()) {
                throw new IllegalArgumentException("Accepted operator acceptance must not contain blockers");
            }
        }

        public static Result accepted(String acceptanceId) {
            return new Result(true, "accepted", acceptanceId, List.of());
        }

        public static Result blocked(String acceptanceId, String blocker) {
            return new Result(false, "blocked", acceptanceId, List.of(normalize(blocker, "operator-acceptance-blocked")));
        }

        public String diagnostic() {
            return accepted
                    ? "production promotion operator acceptance " + acceptanceId + " matches the runtime compile context"
                    : "production promotion operator acceptance is blocked by "
                    + (blockers.isEmpty() ? "operator-acceptance-blocked" : blockers.get(0));
        }
    }
}
