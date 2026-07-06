package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
import net.sixik.ga_utils.javatogpu.frontend.diagnostics.GpuDiagnosticLabel;
import net.sixik.ga_utils.javatogpu.frontend.diagnostics.GpuDiagnosticRenderer;
import net.sixik.ga_utils.javatogpu.frontend.diagnostics.GpuSourceDiagnostic;
import net.sixik.ga_utils.javatogpu.frontend.diagnostics.GpuSourceSpan;

import java.util.List;
import java.util.Map;

/**
 * Small IR-analysis showcase used by the example app and generated validation reports.
 *
 * <p>The first two methods are real {@code @GPU} examples. With the optional
 * {@code javatogpu-ir-validation} annotation processor enabled, they appear in the generated
 * {@code examples-app-ir-validation.properties} report. The rejected case is intentionally shown as
 * a rendered diagnostic instead of a real annotated method, because a real rejected {@code @GPU}
 * method would break normal example compilation.</p>
 */
public final class IrAnalysisExamples {
    private static final List<IrOptimizerBlockerRow> OPTIMIZER_BLOCKER_ROWS = List.of(
            new IrOptimizerBlockerRow(
                    "irPassesCleanly",
                    "autoVectorization",
                    "candidateDiscovery.noRewriteCandidates",
                    "collectRewriteCandidates",
                    "Safety passes; optimizer readiness is still read-only until a supported fixed-width vector candidate is proven."
            ),
            new IrOptimizerBlockerRow(
                    "irPassesButNeedsOptimizerEvidence",
                    "cseRewritePolicy",
                    "skipReason.NOT_LOCAL_REUSE",
                    "proveLocalReuseOrKeepExpressionInline",
                    "Safety passes, but the repeated intrinsic expression still needs CSE reuse evidence before mutation."
            ),
            new IrOptimizerBlockerRow(
                    "rejectedObjectAllocation",
                    "safety",
                    "safety.validationError",
                    "fixIrSafetyError",
                    "Rejected example: object/String allocation must be removed before optimizer readiness matters."
            )
    );

    private static final List<IrProductionReadinessRow> PRODUCTION_READINESS_ROWS = List.of(
            new IrProductionReadinessRow(
                    "irPassesCleanly",
                    "validationBundle",
                    "blocked/productionSwitchNotReady",
                    "This example is safe to compile, but production optimizer mutation is still blocked by validation-bundle readiness."
            ),
            new IrProductionReadinessRow(
                    "irPassesButNeedsOptimizerEvidence",
                    "validationBundle",
                    "blocked/productionSwitchNotReady",
                    "This example is a good analysis target: the chain stops before production switch review until optimizer evidence improves."
            ),
            new IrProductionReadinessRow(
                    "rejectedObjectAllocation",
                    "safety",
                    "rejected/sourceCannotLower",
                    "Rejected source does not reach production-readiness analysis; fix the safety error first."
            )
    );

    private IrAnalysisExamples() {
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void irPassesCleanly(
            @GPUGlobal float[] input,
            @GPUGlobal float[] output
    ) {
        int id = GPU.get_global_id(0);
        float value = input[id];

        output[id] = value * 2.0f + 1.0f;
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void irPassesButNeedsOptimizerEvidence(
            @GPUGlobal float[] input,
            @GPUGlobal float[] output
    ) {
        int id = GPU.get_global_id(0);
        float value = input[id];
        float repeatedA = GPU.sin(value) + 2.0f;
        float repeatedB = GPU.sin(value) + 2.0f;

        output[id] = repeatedA * repeatedB + GPU.cos(value);
    }

    public static String describeIrAnalysisExamples() {
        return """
                IR analysis examples:
                  pass: irPassesCleanly(...)
                    - should pass safety validation and compile normally.
                  needs-work: irPassesButNeedsOptimizerEvidence(...)
                    - should pass safety validation, but optimizer readiness may stay blocked until
                      CSE / auto-vectorization evidence is strong enough for production mutation.
                  production-readiness: see renderProductionReadinessStageTable()
                    - shows the first stage that blocks future optimizer production promotion.
                  rejected: see renderRejectedKernelDiagnostic()
                    - intentionally kept as a diagnostic sample so examples-app still compiles.
                """;
    }

    public static String renderOptimizerBlockerTable() {
        StringBuilder builder = new StringBuilder();
        builder.append("IR optimizer blocker quick view:\n");
        builder.append(String.format(
                "%s | %s | %s | %s%n",
                pad("method", 36),
                pad("source", 18),
                pad("family", 40),
                "next work"
        ));
        builder.append("-".repeat(120)).append('\n');
        for (IrOptimizerBlockerRow row : OPTIMIZER_BLOCKER_ROWS) {
            builder.append(String.format(
                    "%s | %s | %s | %s%n",
                    pad(row.method(), 36),
                    pad(row.source(), 18),
                    pad(row.family(), 40),
                    row.remainingWork()
            ));
            builder.append("  hint: ").append(row.hint()).append('\n');
        }
        return builder.toString();
    }

    public static String renderProductionReadinessStageTable() {
        StringBuilder builder = new StringBuilder();
        builder.append("IR production-readiness stage quick view:\n");
        builder.append(String.format(
                "%s | %s | %s | %s%n",
                pad("method", 36),
                pad("first blocking stage", 24),
                pad("verdict", 38),
                "note"
        ));
        builder.append("-".repeat(132)).append('\n');
        for (IrProductionReadinessRow row : PRODUCTION_READINESS_ROWS) {
            builder.append(String.format(
                    "%s | %s | %s | %s%n",
                    pad(row.method(), 36),
                    pad(row.firstBlockingStage(), 24),
                    pad(row.verdict(), 38),
                    row.note()
            ));
        }
        return builder.toString();
    }

    static List<IrOptimizerBlockerRow> optimizerBlockerRows() {
        return OPTIMIZER_BLOCKER_ROWS;
    }

    static List<IrProductionReadinessRow> productionReadinessRows() {
        return PRODUCTION_READINESS_ROWS;
    }

    public static String renderRejectedKernelDiagnostic() {
        List<String> sourceLines = List.of(
                "public static void rejectedObjectAllocation(float[] input, float[] output) {",
                "    int id = GPU.get_global_id(0);",
                "    String label = \"gpu-\" + id;",
                "    output[id] = GPU.sin(input[id]) / 2.0f;",
                "}"
        );
        GpuSourceSpan primary = new GpuSourceSpan(
                "IrAnalysisRejectedExample.java",
                3,
                5,
                3,
                32
        );
        GpuSourceSpan secondary = new GpuSourceSpan(
                "IrAnalysisRejectedExample.java",
                4,
                18,
                4,
                34
        );
        GpuSourceDiagnostic diagnostic = new GpuSourceDiagnostic(
                "JTG-IR-001",
                "GPU source cannot be lowered to GPU-safe IR",
                primary,
                List.of(
                        GpuDiagnosticLabel.primary(primary, "object/String allocation is not part of the GPU subset"),
                        GpuDiagnosticLabel.secondary(secondary, "this expression is GPU-friendly by itself")
                ),
                List.of(
                        "remove object allocation and keep only primitive scalars, arrays, vectors, structs, pointers, images, or samplers",
                        "if you meant to compute a numeric value, write it directly as output[id] = GPU.sin(input[id]) / 2.0f"
                ),
                Map.of(
                        "example.kind", "rejected",
                        "example.reason", "objectAllocation"
                )
        );

        return new GpuDiagnosticRenderer().render(diagnostic, sourceLines);
    }

    private static String pad(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }

    record IrOptimizerBlockerRow(
            String method,
            String source,
            String family,
            String remainingWork,
            String hint
    ) {
    }

    record IrProductionReadinessRow(
            String method,
            String firstBlockingStage,
            String verdict,
            String note
    ) {
    }
}
