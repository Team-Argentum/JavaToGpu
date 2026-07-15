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
}
