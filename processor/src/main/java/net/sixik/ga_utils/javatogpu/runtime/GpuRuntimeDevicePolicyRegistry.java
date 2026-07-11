package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtension;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionFailurePolicy;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Core-owned conflict resolution for built-in and third-party device policies.
 */
public final class GpuRuntimeDevicePolicyRegistry {

    private final List<GpuRuntimeDevicePolicy> policies;
    private final GpuExtensionRegistry extensionRegistry;
    private final GpuRuntimeDeviceSelfTestCache deviceSelfTestCache;

    private GpuRuntimeDevicePolicyRegistry(List<GpuRuntimeDevicePolicy> policies) {
        this.policies = List.copyOf(policies);
        validateUniquePolicyIds(this.policies);
        this.deviceSelfTestCache = this.policies.stream()
                .filter(GpuRuntimeDeviceSelfTestPolicy.class::isInstance)
                .map(GpuRuntimeDeviceSelfTestPolicy.class::cast)
                .map(GpuRuntimeDeviceSelfTestPolicy::cache)
                .findFirst()
                .orElseGet(GpuRuntimeDeviceSelfTestCache::shared);
        this.extensionRegistry = GpuExtensionRegistry.of(this.policies);
        this.extensionRegistry.requirePipelineContract(
                "runtime device policy pipeline",
                GpuExtensionPhase.DEVICE_SELECTION,
                GpuExtensionPermission.READ_ONLY,
                GpuExtensionCapability.DEVICE_SELECTION_POLICY
        );
    }

    public static GpuRuntimeDevicePolicyRegistry of(List<GpuRuntimeDevicePolicy> policies) {
        return new GpuRuntimeDevicePolicyRegistry(policies == null ? List.of() : policies);
    }

    public static GpuRuntimeDevicePolicyRegistry loadWithBuiltIns() {
        return loadWithBuiltIns(GpuRuntimeDeviceSelfTestCache.shared());
    }

    public static GpuRuntimeDevicePolicyRegistry loadWithBuiltIns(GpuRuntimeDeviceSelfTestCache selfTestCache) {
        ArrayList<GpuRuntimeDevicePolicy> loaded = new ArrayList<>();
        loaded.add(new GpuRuntimeBackendCompatibilityDevicePolicy());
        loaded.add(new GpuRuntimeExplicitDeviceOverridePolicy());
        loaded.add(new GpuRuntimeMethodDeviceConstraintPolicy());
        loaded.add(new GpuRuntimeDeviceSelfTestPolicy(
                Objects.requireNonNull(selfTestCache, "selfTestCache")
        ));
        ServiceLoader.load(GpuRuntimeDevicePolicy.class, GpuRuntimeDevicePolicy.class.getClassLoader())
                .forEach(loaded::add);
        loaded.sort(Comparator
                .comparingInt((GpuRuntimeDevicePolicy policy) -> policy.extensionOrder())
                .thenComparing(GpuRuntimeDevicePolicy::policyId)
                .thenComparing(GpuRuntimeDevicePolicy::policyVersion));
        return of(loaded);
    }

    public GpuRuntimeDeviceSelection select(GpuRuntimeDevicePolicyContext context) {
        Objects.requireNonNull(context, "context");
        ArrayList<GpuRuntimeDevicePolicyDecision> decisions = new ArrayList<>();
        ArrayList<GpuExtensionExecutionReport> executions = new ArrayList<>();
        boolean failedClosed = false;
        for (GpuRuntimeDevicePolicy policy : policies) {
            try {
                GpuRuntimeDevicePolicyDecision decision = Objects.requireNonNull(
                        policy.evaluate(context),
                        "device policy decision"
                );
                validateDecisionIdentity(policy, decision);
                decisions.add(decision);
                executions.add(GpuExtensionExecutionReport.succeeded(policy, "runtime device policy evaluation"));
            } catch (RuntimeException exception) {
                GpuExtensionFailurePolicy failurePolicy = GpuRuntimeProductionProfiles.isProductionProfile(
                        context.compileOptions().optimizationProfile()
                ) ? GpuExtensionFailurePolicy.STOP_PIPELINE : GpuExtensionFailurePolicy.CONTINUE;
                executions.add(GpuExtensionExecutionReport.failed(
                        policy,
                        "runtime device policy evaluation",
                        failurePolicy,
                        exception
                ));
                if (failurePolicy == GpuExtensionFailurePolicy.STOP_PIPELINE) {
                    failedClosed = true;
                    break;
                }
            }
        }

        boolean compileOptionsValid = decisions.stream().allMatch(GpuRuntimeDevicePolicyDecision::compileOptionsValid);
        LinkedHashMap<String, Integer> scoreAdjustments = new LinkedHashMap<>();
        LinkedHashSet<String> rejected = new LinkedHashSet<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        for (GpuRuntimeDevicePolicyDecision decision : decisions) {
            decision.scoreAdjustments().forEach((key, value) -> scoreAdjustments.merge(key, value, Integer::sum));
            rejected.addAll(decision.rejectedDeviceKeys());
            diagnostics.addAll(decision.vendorQuirks());
            diagnostics.addAll(decision.compileOptionDiagnostics());
            diagnostics.addAll(decision.diagnostics());
        }

        ArrayList<GpuRuntimeDeviceCandidateRanking> rankings = new ArrayList<>();
        for (GpuRuntimeDeviceProfile candidate : context.candidates()) {
            String key = GpuRuntimeDevicePolicyContext.deviceKey(candidate);
            int baseScore = baseScore(candidate);
            int adjustment = scoreAdjustments.getOrDefault(key, 0);
            rankings.add(new GpuRuntimeDeviceCandidateRanking(
                    key,
                    candidate,
                    baseScore,
                    adjustment,
                    saturatingAdd(baseScore, adjustment),
                    rejected.contains(key),
                    rejected.contains(key) ? List.of("candidate rejected by device policy") : List.of()
            ));
        }
        rankings.sort(Comparator
                .comparing(GpuRuntimeDeviceCandidateRanking::rejected)
                .thenComparing(Comparator.comparingInt(GpuRuntimeDeviceCandidateRanking::totalScore).reversed())
                .thenComparing(GpuRuntimeDeviceCandidateRanking::deviceKey));

        Optional<GpuRuntimeDeviceProfile> selected = !compileOptionsValid || failedClosed
                ? Optional.empty()
                : rankings.stream().filter(ranking -> !ranking.rejected()).map(GpuRuntimeDeviceCandidateRanking::profile).findFirst();
        String firstBlocker = firstBlocker(context, selected, compileOptionsValid, failedClosed);
        if (selected.isPresent()) {
            diagnostics.add("selected device " + GpuRuntimeDevicePolicyContext.deviceKey(selected.orElseThrow()));
        }
        return new GpuRuntimeDeviceSelection(
                selected,
                rankings,
                decisions,
                executions,
                compileOptionsValid,
                failedClosed,
                firstBlocker,
                diagnostics
        );
    }

    public List<GpuRuntimeDevicePolicy> policies() {
        return policies;
    }

    public GpuExtensionRegistry extensionRegistry() {
        return extensionRegistry;
    }

    public GpuRuntimeDeviceSelfTestCache deviceSelfTestCache() {
        return deviceSelfTestCache;
    }

    private static void validateUniquePolicyIds(List<GpuRuntimeDevicePolicy> policies) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (GpuRuntimeDevicePolicy policy : policies) {
            String policyId = Objects.requireNonNull(policy, "policy").policyId();
            if (policyId == null || policyId.isBlank()) {
                throw new IllegalArgumentException("Device policy id must not be blank");
            }
            if (!ids.add(policyId)) {
                throw new IllegalArgumentException("Duplicate device policy id '" + policyId + "'");
            }
        }
    }

    private static void validateDecisionIdentity(
            GpuRuntimeDevicePolicy policy,
            GpuRuntimeDevicePolicyDecision decision
    ) {
        if (!policy.policyId().equals(decision.policyId())
                || !policy.policyVersion().equals(decision.policyVersion())) {
            throw new IllegalArgumentException(
                    "Device policy decision identity does not match registered policy " + policy.policyId()
            );
        }
    }

    private static int baseScore(GpuRuntimeDeviceProfile profile) {
        long score = deviceClassScore(profile)
                + positive(profile.computeUnits()) * 10_000L
                + positive(profile.globalMemoryBytes()) / (1024L * 1024L)
                + positive(profile.maxWorkGroupSize()) * 10L
                + positive(profile.localMemoryBytes()) / 1024L
                + positive(profile.preferredVectorWidthFloat()) * 100L
                + (profile.supportsDoublePrecision() ? 500L : 0L)
                + (profile.supportsImages() ? 250L : 0L)
                + (profile.supportsSubgroups() ? 250L : 0L);
        return score > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) score;
    }

    private static long deviceClassScore(GpuRuntimeDeviceProfile profile) {
        return switch (profile.deviceClass()) {
            case DGPU -> 1_000_000_000L;
            case IGPU -> 100_000_000L;
            case UNKNOWN, ANY -> 10_000_000L;
            case CPU -> 0L;
        };
    }

    private static long positive(long value) {
        return Math.max(0L, value);
    }

    private static int saturatingAdd(int left, int right) {
        long sum = (long) left + right;
        if (sum > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (sum < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) sum;
    }

    private static String firstBlocker(
            GpuRuntimeDevicePolicyContext context,
            Optional<GpuRuntimeDeviceProfile> selected,
            boolean compileOptionsValid,
            boolean failedClosed
    ) {
        if (context.candidates().isEmpty()) {
            return "device-candidates-missing";
        }
        if (failedClosed) {
            return "device-policy-failed-closed";
        }
        if (!compileOptionsValid) {
            return "compile-options-rejected-by-device-policy";
        }
        return selected.isEmpty() ? "compatible-device-missing" : "none";
    }
}
