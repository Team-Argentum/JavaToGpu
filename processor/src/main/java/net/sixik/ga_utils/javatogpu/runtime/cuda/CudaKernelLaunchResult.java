package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Result of submitting a CUDA kernel launch through an optional native bridge.
 */
public record CudaKernelLaunchResult(
        String launcherId,
        String status,
        GpuExecutionConfig executionConfig,
        int sharedMemoryByteSize,
        int readbackRequiredCount,
        int readbackCompletedCount,
        List<String> blockers,
        List<String> diagnostics
) {

    public CudaKernelLaunchResult {
        launcherId = launcherId == null || launcherId.isBlank() ? "cuda-kernel-launcher:unknown" : launcherId.trim();
        status = status == null || status.isBlank() ? "unknown" : status.trim();
        sharedMemoryByteSize = Math.max(0, sharedMemoryByteSize);
        readbackRequiredCount = Math.max(0, readbackRequiredCount);
        readbackCompletedCount = Math.max(0, readbackCompletedCount);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static CudaKernelLaunchResult disabled(String launcherMode) {
        return new CudaKernelLaunchResult(
                launcherId(launcherMode),
                "disabled",
                null,
                0,
                0,
                0,
                List.of("cuda-native-kernel-launcher-disabled"),
                List.of("CUDA native kernel launcher was not requested")
        );
    }

    public static CudaKernelLaunchResult unsupported(
            String launcherMode,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new CudaKernelLaunchResult(launcherId(launcherMode), "unsupported", null, 0, 0, 0, blockers, diagnostics);
    }

    public static CudaKernelLaunchResult failed(
            String launcherId,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new CudaKernelLaunchResult(launcherId, "failed", null, 0, 0, 0, blockers, diagnostics);
    }

    public static CudaKernelLaunchResult succeeded(
            String launcherId,
            GpuExecutionConfig executionConfig,
            int readbackRequiredCount,
            int readbackCompletedCount,
            List<String> diagnostics
    ) {
        return succeeded(launcherId, executionConfig, 0, readbackRequiredCount, readbackCompletedCount, diagnostics);
    }

    public static CudaKernelLaunchResult succeeded(
            String launcherId,
            GpuExecutionConfig executionConfig,
            int sharedMemoryByteSize,
            int readbackRequiredCount,
            int readbackCompletedCount,
            List<String> diagnostics
    ) {
        return new CudaKernelLaunchResult(
                launcherId,
                "succeeded",
                executionConfig,
                sharedMemoryByteSize,
                readbackRequiredCount,
                readbackCompletedCount,
                List.of(),
                diagnostics
        );
    }

    public boolean succeeded() {
        return "succeeded".equals(status);
    }

    public Optional<String> firstBlocker() {
        return blockers.stream().findFirst();
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.cuda.kernelLaunch" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.kernelLaunch");
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", "true");
        fields.put(prefix + ".launcher.id", launcherId);
        fields.put(prefix + ".status", status);
        fields.put(prefix + ".succeeded", Boolean.toString(succeeded()));
        fields.put(prefix + ".submitted", Boolean.toString(succeeded()));
        fields.put(prefix + ".sharedMemory.present", Boolean.toString(sharedMemoryByteSize > 0));
        fields.put(prefix + ".sharedMemory.byteSize", Integer.toString(sharedMemoryByteSize));
        fields.put(prefix + ".readback.required.count", Integer.toString(readbackRequiredCount));
        fields.put(prefix + ".readback.completed.count", Integer.toString(readbackCompletedCount));
        fields.put(prefix + ".blocker.count", Integer.toString(blockers.size()));
        for (int index = 0; index < blockers.size(); index++) {
            fields.put(prefix + ".blocker." + index, blockers.get(index));
        }
        if (executionConfig != null) {
            fields.put(prefix + ".work.dimensions", Integer.toString(executionConfig.dimensions()));
            fields.put(prefix + ".work.globalShape", executionConfig.globalShape());
            fields.put(prefix + ".work.localShape", executionConfig.localShape());
            fields.put(prefix + ".work.globalItem.count", Long.toString(executionConfig.globalItemCount()));
            fields.put(prefix + ".work.localItem.count", Long.toString(executionConfig.localItemCount()));
        }
    }

    private static String launcherId(String launcherMode) {
        return launcherMode == null || launcherMode.isBlank()
                ? "cuda-kernel-launcher:unknown"
                : "cuda-kernel-launcher:" + launcherMode.trim();
    }
}
