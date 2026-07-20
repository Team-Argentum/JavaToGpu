package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Result of copying CUDA output resources back to host-visible arguments.
 */
public record CudaKernelReadbackResult(
        String readbackId,
        String status,
        int readbackRequiredCount,
        int readbackCompletedCount,
        List<String> blockers,
        List<String> diagnostics
) {

    public CudaKernelReadbackResult {
        readbackId = readbackId == null || readbackId.isBlank() ? "cuda-readback:unknown" : readbackId.trim();
        status = status == null || status.isBlank() ? "unknown" : status.trim();
        readbackRequiredCount = Math.max(0, readbackRequiredCount);
        readbackCompletedCount = Math.max(0, readbackCompletedCount);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static CudaKernelReadbackResult disabled(String readbackMode, int requiredCount, int completedCount) {
        return new CudaKernelReadbackResult(
                readbackId(readbackMode),
                "disabled",
                requiredCount,
                completedCount,
                List.of("cuda-native-readback-disabled"),
                List.of("CUDA native readback was not requested")
        );
    }

    public static CudaKernelReadbackResult unsupported(
            String readbackMode,
            int requiredCount,
            int completedCount,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new CudaKernelReadbackResult(
                readbackId(readbackMode),
                "unsupported",
                requiredCount,
                completedCount,
                blockers,
                diagnostics
        );
    }

    public static CudaKernelReadbackResult failed(
            String readbackId,
            int requiredCount,
            int completedCount,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new CudaKernelReadbackResult(
                readbackId,
                "failed",
                requiredCount,
                completedCount,
                blockers,
                diagnostics
        );
    }

    public static CudaKernelReadbackResult succeeded(
            String readbackId,
            int requiredCount,
            int completedCount,
            List<String> diagnostics
    ) {
        return new CudaKernelReadbackResult(
                readbackId,
                "succeeded",
                requiredCount,
                completedCount,
                List.of(),
                diagnostics
        );
    }

    public boolean succeeded() {
        return "succeeded".equals(status);
    }

    public boolean complete() {
        return readbackCompletedCount >= readbackRequiredCount;
    }

    public Optional<String> firstBlocker() {
        return blockers.stream().findFirst();
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.cuda.readback" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.readback");
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", "true");
        fields.put(prefix + ".id", readbackId);
        fields.put(prefix + ".status", status);
        fields.put(prefix + ".succeeded", Boolean.toString(succeeded()));
        fields.put(prefix + ".complete", Boolean.toString(complete()));
        fields.put(prefix + ".required.count", Integer.toString(readbackRequiredCount));
        fields.put(prefix + ".completed.count", Integer.toString(readbackCompletedCount));
        fields.put(prefix + ".blocker.count", Integer.toString(blockers.size()));
        for (int index = 0; index < blockers.size(); index++) {
            fields.put(prefix + ".blocker." + index, blockers.get(index));
        }
    }

    private static String readbackId(String readbackMode) {
        return readbackMode == null || readbackMode.isBlank()
                ? "cuda-readback:unknown"
                : "cuda-readback:" + readbackMode.trim();
    }
}
