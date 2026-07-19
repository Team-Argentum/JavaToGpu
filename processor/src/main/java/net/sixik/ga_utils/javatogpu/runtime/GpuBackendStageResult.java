package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Typed result for one backend pipeline stage.
 *
 * <p>This record is intentionally backend-neutral. Backend-specific adapters may add details, but the primary stage,
 * status, blockers, diagnostics, and artifact fields stay stable across OpenCL, CUDA, and future backends.</p>
 */
public record GpuBackendStageResult(
        GpuBackendPipelineStage stage,
        GpuBackendStageStatus status,
        GpuBackendTarget backendTarget,
        String summary,
        List<String> blockers,
        List<String> diagnostics,
        Map<String, String> details
) {

    public GpuBackendStageResult {
        stage = stage == null ? GpuBackendPipelineStage.DISCOVER : stage;
        status = status == null ? GpuBackendStageStatus.NOT_STARTED : status;
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        summary = normalize(summary, status.name().toLowerCase(java.util.Locale.ROOT));
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        details = details == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public static GpuBackendStageResult notStarted(GpuBackendPipelineStage stage, GpuBackendTarget backendTarget) {
        return new GpuBackendStageResult(
                stage,
                GpuBackendStageStatus.NOT_STARTED,
                backendTarget,
                "stage not started",
                List.of(),
                List.of(),
                Map.of()
        );
    }

    public static GpuBackendStageResult succeeded(
            GpuBackendPipelineStage stage,
            GpuBackendTarget backendTarget,
            String summary,
            List<String> diagnostics
    ) {
        return new GpuBackendStageResult(
                stage,
                GpuBackendStageStatus.SUCCEEDED,
                backendTarget,
                summary,
                List.of(),
                diagnostics,
                Map.of()
        );
    }

    public static GpuBackendStageResult skipped(
            GpuBackendPipelineStage stage,
            GpuBackendTarget backendTarget,
            String summary,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new GpuBackendStageResult(
                stage,
                GpuBackendStageStatus.SKIPPED,
                backendTarget,
                summary,
                blockers,
                diagnostics,
                Map.of()
        );
    }

    public static GpuBackendStageResult unsupported(
            GpuBackendPipelineStage stage,
            GpuBackendTarget backendTarget,
            String summary,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new GpuBackendStageResult(
                stage,
                GpuBackendStageStatus.UNSUPPORTED,
                backendTarget,
                summary,
                blockers,
                diagnostics,
                Map.of()
        );
    }

    public static GpuBackendStageResult failed(
            GpuBackendPipelineStage stage,
            GpuBackendTarget backendTarget,
            String summary,
            Throwable failure,
            List<String> diagnostics
    ) {
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        if (failure != null) {
            details.put("failure.type", failure.getClass().getName());
            details.put("failure.message", normalize(failure.getMessage(), ""));
        }
        return new GpuBackendStageResult(
                stage,
                GpuBackendStageStatus.FAILED,
                backendTarget,
                summary,
                failure == null ? List.of("stage-failed") : List.of("stage-failed:" + failure.getClass().getSimpleName()),
                diagnostics,
                details
        );
    }

    public boolean succeeded() {
        return status.successful();
    }

    public boolean blocked() {
        return !blockers.isEmpty() || status == GpuBackendStageStatus.FAILED || status == GpuBackendStageStatus.UNSUPPORTED;
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.backend.stage" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        put(fields, normalizedPrefix, "present", true);
        put(fields, normalizedPrefix, "name", stage.name());
        put(fields, normalizedPrefix, "key", stage.key());
        put(fields, normalizedPrefix, "order", stage.order());
        put(fields, normalizedPrefix, "productionAffecting", stage.productionAffecting());
        put(fields, normalizedPrefix, "status", status.name());
        put(fields, normalizedPrefix, "terminal", status.terminal());
        put(fields, normalizedPrefix, "successful", status.successful());
        put(fields, normalizedPrefix, "backendTarget", backendTarget.name());
        put(fields, normalizedPrefix, "summary", summary);
        put(fields, normalizedPrefix, "blocker.count", blockers.size());
        for (int index = 0; index < blockers.size(); index++) {
            put(fields, normalizedPrefix, "blocker." + index, blockers.get(index));
        }
        put(fields, normalizedPrefix, "diagnostic.count", diagnostics.size());
        for (int index = 0; index < diagnostics.size(); index++) {
            put(fields, normalizedPrefix, "diagnostic." + index, diagnostics.get(index));
        }
        put(fields, normalizedPrefix, "detail.count", details.size());
        int detailIndex = 0;
        for (Map.Entry<String, String> entry : details.entrySet()) {
            put(fields, normalizedPrefix, "detail." + detailIndex + ".key", entry.getKey());
            put(fields, normalizedPrefix, "detail." + detailIndex + ".value", entry.getValue());
            detailIndex++;
        }
        if ("runtime.backend.stage".equals(normalizedPrefix)) {
            fields.put("runtime.backend.target", backendTarget.name());
            fields.put("runtime.status", stage.key() + "-" + status.name().toLowerCase(java.util.Locale.ROOT));
        }
        return Collections.unmodifiableMap(fields);
    }

    private static void put(LinkedHashMap<String, String> fields, String prefix, String key, Object value) {
        fields.put(prefix + "." + key, Objects.toString(value, ""));
    }

    private static String normalize(String value, String fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return value == null || value.isBlank() ? fallback : value;
    }
}
