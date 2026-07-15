package net.sixik.ga_utils.javatogpu.iroptimizer;

import java.util.List;
import java.util.Map;

/**
 * Compact validation verdict used by the optimizer sandwich runner.
 */
public record GpuIrOptimizationValidationResult(
        GpuIrOptimizationValidationStage stage,
        boolean valid,
        String verdict,
        Map<String, String> fields,
        List<String> diagnostics
) {

    public GpuIrOptimizationValidationResult {
        stage = stage == null ? GpuIrOptimizationValidationStage.ORIGINAL_BEFORE : stage;
        verdict = verdict == null || verdict.isBlank() ? (valid ? "valid" : "invalid") : verdict;
        fields = fields == null ? Map.of() : Map.copyOf(fields);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static GpuIrOptimizationValidationResult valid(GpuIrOptimizationValidationStage stage) {
        return new GpuIrOptimizationValidationResult(stage, true, "valid", Map.of(), List.of());
    }

    public static GpuIrOptimizationValidationResult invalid(
            GpuIrOptimizationValidationStage stage,
            String verdict,
            String diagnostic
    ) {
        return new GpuIrOptimizationValidationResult(
                stage,
                false,
                verdict,
                Map.of(),
                diagnostic == null || diagnostic.isBlank() ? List.of() : List.of(diagnostic)
        );
    }
}
