package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small factories for explicit {@code @GPUTest} probe-evidence warm-up candidate lists.
 */
public final class GpuRuntimeMethodTestProbeEvidenceWarmupCandidates {

    public static final int DEFAULT_OPENCL_GPU_WARMUP_LIMIT = 4;

    private GpuRuntimeMethodTestProbeEvidenceWarmupCandidates() {
    }

    /**
     * Creates owned OpenCL warm-up candidates for discovered non-CPU OpenCL devices.
     */
    public static List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> openClGpuDevices(
            GpuRuntimeDeviceDiscoveryResult discoveryResult
    ) {
        return openClGpuDevices(discoveryResult, DEFAULT_OPENCL_GPU_WARMUP_LIMIT);
    }

    /**
     * Creates owned OpenCL warm-up candidates for discovered non-CPU OpenCL devices, capped by {@code maxCandidates}.
     */
    public static List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> openClGpuDevices(
            GpuRuntimeDeviceDiscoveryResult discoveryResult,
            int maxCandidates
    ) {
        if (discoveryResult == null || !discoveryResult.discoveryAvailable() || maxCandidates <= 0) {
            return List.of();
        }
        return orderedProfiles(discoveryResult).stream()
                .filter(profile -> profile.backendTarget() == GpuBackendTarget.OPENCL)
                .filter(profile -> profile.deviceClass() != GpuDeviceClassTarget.CPU)
                .limit(maxCandidates)
                .map(GpuRuntimeMethodTestProbeEvidenceWarmupCandidates::ownedOpenClCandidate)
                .toList();
    }

    /**
     * Creates one owned OpenCL warm-up candidate for the currently selected non-CPU device, falling back to the first GPU.
     */
    public static List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> openClSelectedGpuDevice(
            GpuRuntimeDeviceDiscoveryResult discoveryResult
    ) {
        if (discoveryResult == null || !discoveryResult.discoveryAvailable()) {
            return List.of();
        }
        return discoveryResult.selectedDevice()
                .filter(profile -> profile.backendTarget() == GpuBackendTarget.OPENCL)
                .filter(profile -> profile.deviceClass() != GpuDeviceClassTarget.CPU)
                .map(GpuRuntimeMethodTestProbeEvidenceWarmupCandidates::ownedOpenClCandidate)
                .map(List::of)
                .orElseGet(() -> openClGpuDevices(discoveryResult, 1));
    }

    private static GpuRuntimeMethodTestProbeEvidenceWarmupCandidate ownedOpenClCandidate(
            GpuRuntimeDeviceProfile profile
    ) {
        return GpuRuntimeMethodTestProbeEvidenceWarmupCandidate.owned(
                profile,
                OpenClGpuRuntimeBackend::new
        );
    }

    private static List<GpuRuntimeDeviceProfile> orderedProfiles(GpuRuntimeDeviceDiscoveryResult discoveryResult) {
        Map<String, GpuRuntimeDeviceProfile> ordered = new LinkedHashMap<>();
        discoveryResult.deviceSelection().ifPresent(selection -> selection.rankedCandidates().stream()
                .filter(candidate -> !candidate.rejected())
                .map(GpuRuntimeDeviceCandidateRanking::profile)
                .forEach(profile -> ordered.put(GpuRuntimeDevicePolicyContext.deviceKey(profile), profile)));
        for (GpuRuntimeDeviceProfile profile : discoveryResult.discoveredDevices()) {
            ordered.putIfAbsent(GpuRuntimeDevicePolicyContext.deviceKey(profile), profile);
        }
        return new ArrayList<>(ordered.values());
    }
}
