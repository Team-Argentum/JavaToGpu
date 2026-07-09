package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Diagnostic-only entrypoint for future structural runtime IR peephole rewrites.
 *
 * <p>The current packaged artifact stores {@code ir-text-v1} method bodies rather than a typed runtime expression tree.
 * This pass refuses to implement string-based rewrites and reports the missing typed-IR capability instead. Future rule
 * implementations can replace this blocker once reconstructable typed nodes are available at runtime.</p>
 */
public final class GpuRuntimeIrPeepholePass implements GpuRuntimeIrOptimizationPass {

    public static final String VERSION = "optimizer:peephole-diagnostic-v1";
    public static final String TYPED_BODY_FORMAT = net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody.FORMAT;
    private final GpuRuntimeIrPeepholeRuleRegistry ruleRegistry;

    public GpuRuntimeIrPeepholePass() {
        this(GpuRuntimeIrPeepholeRuleRegistry.loadWithBuiltIns());
    }

    public GpuRuntimeIrPeepholePass(GpuRuntimeIrPeepholeRuleRegistry ruleRegistry) {
        this.ruleRegistry = java.util.Objects.requireNonNull(ruleRegistry, "ruleRegistry");
    }

    @Override
    public GpuRuntimeIrOptimizationReport run(GpuRuntimeIrOptimizationRequest request) {
        Optional<IrGpuArtifact> artifact = request.artifact();
        String identity = GpuRuntimeIrOptimizerRegistry.identityOf(artifact);
        GpuRuntimeIrPeepholeRuleRegistry.Analysis analysis = artifact
                .map(value -> ruleRegistry.analyze(request, value))
                .orElseGet(() -> new GpuRuntimeIrPeepholeRuleRegistry.Analysis(List.of(), List.of(), false));
        Map<String, String> fields = diagnosticFields(artifact, request, analysis);
        GpuRuntimeIrOptimizationPassReport passReport = GpuRuntimeIrOptimizationPassReport.skipped(
                VERSION,
                identity,
                diagnostic(fields)
        ).withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                "runtime.peephole.preflight",
                "blocked",
                fields
        ));
        return new GpuRuntimeIrOptimizationReport(artifact, List.of(passReport), request.strategyDecision());
    }

    @Override
    public String passName() {
        return "peephole";
    }

    @Override
    public String passVersion() {
        return VERSION;
    }

    @Override
    public boolean requiresFastMath() {
        return true;
    }

    private Map<String, String> diagnosticFields(
            Optional<IrGpuArtifact> artifact,
            GpuRuntimeIrOptimizationRequest request,
            GpuRuntimeIrPeepholeRuleRegistry.Analysis analysis
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("optimizerFamily", "peephole");
        fields.put("mode", "diagnostic-only");
        fields.put("mutationEnabled", "false");
        fields.put("fastMath", Boolean.toString(request.fastMathEnabled()));
        fields.put("policySource", request.optimizerPolicy().source());
        int methodBodyCount = artifact.map(value -> value.module().methodBodies().size()).orElse(0);
        long typedBodyCount = artifact.stream()
                .flatMap(value -> value.module().methodBodies().stream())
                .filter(body -> body.typedBody().available())
                .count();
        long textBodyCount = artifact.stream()
                .flatMap(value -> value.module().methodBodies().stream())
                .filter(body -> "ir-text-v1".equals(body.format()))
                .count();
        boolean typedIrAvailable = methodBodyCount > 0 && typedBodyCount == methodBodyCount;
        fields.put("methodBody.count", Integer.toString(methodBodyCount));
        fields.put("typedBody.count", Long.toString(typedBodyCount));
        fields.put("irTextBody.count", Long.toString(textBodyCount));
        fields.put("typedIrAvailable", Boolean.toString(typedIrAvailable));
        fields.put("rule.count", Integer.toString(ruleRegistry.rules().size()));
        fields.put("candidate.count", Integer.toString(analysis.candidateCount()));
        fields.put("proposal.count", Integer.toString(analysis.proposalCount()));
        fields.put("rule.mix.candidate.count", "0");
        fields.put("rule.madFma.candidate.count", Integer.toString(candidateCount(analysis, "madFma")));
        fields.put("rule.clamp.candidate.count", "0");
        fields.put("rule.step.candidate.count", "0");
        fields.put("rule.dot.candidate.count", "0");
        appendRuleFields(fields, analysis);
        fields.put("rule.execution.count", Integer.toString(analysis.executionReports().size()));
        fields.put("rule.execution.failedContinued.count", Long.toString(analysis.executionReports().stream()
                .filter(report -> report.outcome() == net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome.FAILED_CONTINUED)
                .count()));
        fields.put("rule.execution.failedClosed.count", Long.toString(analysis.executionReports().stream()
                .filter(report -> report.outcome() == net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome.FAILED_CLOSED)
                .count()));
        fields.put("firstBlocker", firstBlocker(
                artifact,
                typedIrAvailable,
                analysis.candidateCount(),
                analysis.failedClosed()
        ));
        return Map.copyOf(fields);
    }

    private static int candidateCount(GpuRuntimeIrPeepholeRuleRegistry.Analysis analysis, String ruleId) {
        return analysis.ruleReports().stream()
                .filter(report -> ruleId.equals(report.ruleId()))
                .mapToInt(GpuRuntimeIrPeepholeRuleReport::candidateCount)
                .sum();
    }

    private void appendRuleFields(
            LinkedHashMap<String, String> fields,
            GpuRuntimeIrPeepholeRuleRegistry.Analysis analysis
    ) {
        java.util.LinkedHashMap<String, List<GpuRuntimeIrPeepholeRuleReport>> reportsByRule = new java.util.LinkedHashMap<>();
        for (GpuRuntimeIrPeepholeRuleReport report : analysis.ruleReports()) {
            reportsByRule.computeIfAbsent(report.ruleId(), ignored -> new java.util.ArrayList<>()).add(report);
        }
        for (int index = 0; index < ruleRegistry.rules().size(); index++) {
            GpuRuntimeIrPeepholeRule rule = ruleRegistry.rules().get(index);
            List<GpuRuntimeIrPeepholeRuleReport> reports = reportsByRule.getOrDefault(rule.ruleId(), List.of());
            String prefix = "rule." + index;
            fields.put(prefix + ".id", rule.ruleId());
            fields.put(prefix + ".version", rule.ruleVersion());
            fields.put(prefix + ".extensionId", rule.extensionId());
            fields.put(prefix + ".extensionVersion", rule.extensionVersion());
            fields.put(prefix + ".candidate.count", Integer.toString(reports.stream()
                    .mapToInt(GpuRuntimeIrPeepholeRuleReport::candidateCount)
                    .sum()));
            fields.put(prefix + ".proposal.count", Integer.toString(reports.stream()
                    .mapToInt(GpuRuntimeIrPeepholeRuleReport::proposalCount)
                    .sum()));
            fields.put(prefix + ".mutationProposed", Boolean.toString(reports.stream()
                    .anyMatch(GpuRuntimeIrPeepholeRuleReport::mutationProposed)));
            fields.put(prefix + ".proofStatus", reports.isEmpty()
                    ? executionProofStatus(rule, analysis)
                    : reports.get(0).proofStatus());
        }
    }

    private static String executionProofStatus(
            GpuRuntimeIrPeepholeRule rule,
            GpuRuntimeIrPeepholeRuleRegistry.Analysis analysis
    ) {
        return analysis.executionReports().stream()
                .filter(report -> rule.extensionId().equals(report.extensionId()))
                .map(report -> switch (report.outcome()) {
                    case FAILED_CONTINUED -> "rule-execution-failed-continued";
                    case FAILED_CLOSED -> "rule-execution-failed-closed";
                    case SKIPPED -> "rule-execution-skipped";
                    case SUCCEEDED -> "no-report";
                })
                .findFirst()
                .orElse("not-run");
    }

    private static String firstBlocker(
            Optional<IrGpuArtifact> artifact,
            boolean typedIrAvailable,
            int candidateCount,
            boolean failedClosed
    ) {
        if (artifact.isEmpty()) {
            return "irgpu-artifact-missing";
        }
        if (!typedIrAvailable) {
            return "typed-ir-unavailable";
        }
        if (failedClosed) {
            return "rule-execution-failed-closed";
        }
        return candidateCount == 0 ? "no-peephole-candidates" : "rewrite-engine-not-implemented";
    }

    private static String diagnostic(Map<String, String> fields) {
        return switch (fields.getOrDefault("firstBlocker", "unknown")) {
            case "irgpu-artifact-missing" -> "IrGpu artifact is unavailable; peephole analysis skipped";
            case "typed-ir-unavailable" ->
                    "typed runtime IR is required; text-based peephole rewriting is disabled";
            case "no-peephole-candidates" -> "typed runtime IR contains no supported peephole candidates";
            case "rule-execution-failed-closed" -> "peephole rule execution failed closed in a production profile";
            case "rewrite-engine-not-implemented" ->
                    "typed peephole candidates detected, but structural rewrite and proof emission are not implemented";
            default -> "peephole preflight blocked";
        };
    }

}
