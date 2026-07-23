package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Factory and evaluation helpers for backend capability requirements.
 */
public final class GpuRuntimeRequirements {

    private GpuRuntimeRequirements() {
    }

    public static boolean isSatisfied(GpuRuntimeBackendReport report, List<GpuRuntimeRequirement> requirements) {
        return failureReasons(report, requirements).isEmpty();
    }

    public static List<String> failureReasons(GpuRuntimeBackendReport report, List<GpuRuntimeRequirement> requirements) {
        List<String> reasons = new ArrayList<>();
        if (!report.available()) {
            reasons.add(report.detail() == null || report.detail().isBlank()
                    ? "backend is unavailable"
                    : report.detail());
        }
        for (GpuRuntimeRequirement requirement : requirements) {
            String reason = requirement.failureReason(report);
            if (reason != null && !reason.isBlank()) {
                reasons.add(reason);
            }
        }
        return List.copyOf(reasons);
    }

    public static List<String> failureReasons(
            GpuRuntimeBackendReport report,
            GpuRuntimeBackendCandidateMetadata metadata,
            List<GpuRuntimeBackendRequirement> requirements
    ) {
        List<String> reasons = new ArrayList<>();
        GpuRuntimeBackendCandidateMetadata candidateMetadata = metadata == null
                ? GpuRuntimeBackendCandidateMetadata.unknown()
                : metadata;
        for (GpuRuntimeBackendRequirement requirement : requirements) {
            String reason = requirement.failureReason(report, candidateMetadata);
            if (reason != null && !reason.isBlank()) {
                reasons.add(reason);
            }
        }
        return List.copyOf(reasons);
    }

    public static GpuRuntimeRequirement requireBackendTarget(GpuBackendTarget backendTarget) {
        Objects.requireNonNull(backendTarget, "backendTarget");
        return report -> report.backendTarget() == backendTarget
                ? null
                : "requires backend target " + backendTarget + " but found " + report.backendTarget();
    }

    public static GpuRuntimeRequirement excludeBackendTarget(GpuBackendTarget backendTarget) {
        Objects.requireNonNull(backendTarget, "backendTarget");
        return report -> report.backendTarget() == backendTarget
                ? "backend target " + backendTarget + " is excluded"
                : null;
    }

    public static GpuRuntimeRequirement requireFeature(GpuRuntimeFeature feature) {
        return report -> report.supports(feature)
                ? null
                : "missing feature " + feature;
    }

    public static GpuRuntimeRequirement requireFeature(GpuBackendTarget backendTarget, GpuRuntimeFeature feature) {
        return report -> report.backendTarget() != backendTarget
                ? null
                : requireFeature(feature).failureReason(report);
    }

    public static GpuRuntimeRequirement requireCapability(GpuRuntimeCapability capability) {
        Objects.requireNonNull(capability, "capability");
        return report -> report.supports(capability)
                ? null
                : "missing capability " + capability.key();
    }

    public static GpuRuntimeRequirement requireCapability(
            GpuBackendTarget backendTarget,
            GpuRuntimeCapability capability
    ) {
        Objects.requireNonNull(backendTarget, "backendTarget");
        return report -> report.backendTarget() != backendTarget
                ? null
                : requireCapability(capability).failureReason(report);
    }

    public static GpuRuntimeBackendRequirement requireDeclaredModuleFormat(GpuBackendModuleFormat moduleFormat) {
        Objects.requireNonNull(moduleFormat, "moduleFormat");
        return (report, metadata) -> metadata.declaresModuleFormat(moduleFormat)
                ? null
                : "missing declared module format " + moduleFormat.key() + declaredSuffix(metadata.moduleFormatKeys());
    }

    public static GpuRuntimeBackendRequirement requireDeclaredModuleFormat(
            GpuBackendTarget backendTarget,
            GpuBackendModuleFormat moduleFormat
    ) {
        Objects.requireNonNull(backendTarget, "backendTarget");
        return (report, metadata) -> report.backendTarget() != backendTarget
                ? null
                : requireDeclaredModuleFormat(moduleFormat).failureReason(report, metadata);
    }

    public static GpuRuntimeBackendRequirement requireDeclaredCapability(GpuRuntimeCapability capability) {
        Objects.requireNonNull(capability, "capability");
        return (report, metadata) -> metadata.declaresCapability(capability)
                ? null
                : "missing declared capability " + capability.key() + declaredSuffix(metadata.capabilityKeys());
    }

    public static GpuRuntimeBackendRequirement requireDeclaredCapability(
            GpuBackendTarget backendTarget,
            GpuRuntimeCapability capability
    ) {
        Objects.requireNonNull(backendTarget, "backendTarget");
        return (report, metadata) -> report.backendTarget() != backendTarget
                ? null
                : requireDeclaredCapability(capability).failureReason(report, metadata);
    }

    public static GpuRuntimeBackendRequirement requireExecutionPipelineAvailable() {
        return (report, metadata) -> {
            if (metadata.executionSupport().isEmpty()) {
                return "missing backend execution support metadata";
            }
            GpuRuntimeBackendExecutionSupport support = metadata.executionSupport().orElseThrow();
            return support.executionPipelineAvailable()
                    ? null
                    : "backend execution pipeline is not available (declared stages: "
                            + declaredKeysOrNone(support.supportedStageKeys())
                            + ')';
        };
    }

    public static GpuRuntimeBackendRequirement requireExecutionPipelineAvailable(GpuBackendTarget backendTarget) {
        Objects.requireNonNull(backendTarget, "backendTarget");
        return (report, metadata) -> report.backendTarget() != backendTarget
                ? null
                : requireExecutionPipelineAvailable().failureReason(report, metadata);
    }

    public static GpuRuntimeRequirement minimumApiVersion(GpuBackendTarget backendTarget, int major, int minor) {
        GpuRuntimeApiVersion minimum = new GpuRuntimeApiVersion(major, minor);
        return report -> {
            if (report.backendTarget() != backendTarget) {
                return null;
            }
            if (report.apiVersion() == null) {
                return "missing API version, required at least " + minimum;
            }
            return report.apiVersion().compareTo(minimum) >= 0
                    ? null
                    : "requires API version at least " + minimum + " but found " + report.apiVersion();
        };
    }

    public static GpuRuntimeRequirement minimumLocalMemoryBytes(long bytes) {
        return report -> {
            if (report.localMemoryBytes() == null) {
                return "missing local memory capability, required at least " + bytes + " bytes";
            }
            return report.localMemoryBytes() >= bytes
                    ? null
                    : "requires at least " + bytes + " bytes of local memory but found " + report.localMemoryBytes();
        };
    }

    public static GpuRuntimeRequirement minimumLocalMemoryBytes(GpuBackendTarget backendTarget, long bytes) {
        return report -> report.backendTarget() != backendTarget
                ? null
                : minimumLocalMemoryBytes(bytes).failureReason(report);
    }

    public static GpuRuntimeRequirement minimumMaxWorkGroupSize(long size) {
        return report -> {
            if (report.maxWorkGroupSize() == null) {
                return "missing max work-group size capability, required at least " + size;
            }
            return report.maxWorkGroupSize() >= size
                    ? null
                    : "requires max work-group size at least " + size + " but found " + report.maxWorkGroupSize();
        };
    }

    public static GpuRuntimeRequirement minimumMaxWorkGroupSize(GpuBackendTarget backendTarget, long size) {
        return report -> report.backendTarget() != backendTarget
                ? null
                : minimumMaxWorkGroupSize(size).failureReason(report);
    }

    private static String declaredSuffix(String declaredKeys) {
        return declaredKeys == null || declaredKeys.isBlank()
                ? " (declared: none)"
                : " (declared: " + declaredKeys + ')';
    }

    private static String declaredKeysOrNone(String declaredKeys) {
        return declaredKeys == null || declaredKeys.isBlank() ? "none" : declaredKeys;
    }
}
