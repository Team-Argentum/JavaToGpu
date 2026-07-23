package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionRegistry;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

import java.util.List;

/**
 * Compatibility facade for typed-IR peephole rule discovery and analysis.
 */
public final class GpuRuntimeIrPeepholeRuleRegistry {

    private final net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholeRuleRegistry delegate;

    private GpuRuntimeIrPeepholeRuleRegistry(
            net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholeRuleRegistry delegate
    ) {
        this.delegate = delegate;
    }

    public static GpuRuntimeIrPeepholeRuleRegistry of(List<GpuRuntimeIrPeepholeRule> rules) {
        return new GpuRuntimeIrPeepholeRuleRegistry(
                net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholeRuleRegistry.of(rules)
        );
    }

    public static GpuRuntimeIrPeepholeRuleRegistry loadWithBuiltIns() {
        return new GpuRuntimeIrPeepholeRuleRegistry(
                net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholeRuleRegistry.loadWithBuiltIns()
        );
    }

    public Analysis analyze(GpuRuntimeIrOptimizationRequest request, IrGpuArtifact artifact) {
        return Analysis.from(delegate.analyze(request, artifact));
    }

    public List<GpuRuntimeIrPeepholeRule> rules() {
        return delegate.rules();
    }

    public GpuExtensionRegistry extensionRegistry() {
        return delegate.extensionRegistry();
    }

    net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholeRuleRegistry unwrap() {
        return delegate;
    }

    public record Analysis(
            List<GpuRuntimeIrPeepholeRuleReport> ruleReports,
            List<GpuExtensionExecutionReport> executionReports,
            boolean failedClosed
    ) {
        public Analysis {
            ruleReports = ruleReports == null ? List.of() : List.copyOf(ruleReports);
            executionReports = executionReports == null ? List.of() : List.copyOf(executionReports);
        }

        private static Analysis from(
                net.sixik.ga_utils.javatogpu.runtime.optimization.GpuRuntimeIrPeepholeRuleRegistry.Analysis analysis
        ) {
            return new Analysis(analysis.ruleReports(), analysis.executionReports(), analysis.failedClosed());
        }

        public int candidateCount() {
            return ruleReports.stream().mapToInt(GpuRuntimeIrPeepholeRuleReport::candidateCount).sum();
        }

        public int proposalCount() {
            return ruleReports.stream().mapToInt(GpuRuntimeIrPeepholeRuleReport::proposalCount).sum();
        }

        public int partialReplacementPlanCount() {
            return (int) ruleReports.stream()
                    .flatMap(report -> report.replacementPlans().stream())
                    .filter(plan -> !plan.complete())
                    .count();
        }

        public String firstReplacementPlanBlocker() {
            return ruleReports.stream()
                    .flatMap(report -> report.replacementPlans().stream())
                    .filter(plan -> !plan.complete())
                    .map(GpuRuntimeIrPeepholeReplacementPlan::firstBlocker)
                    .findFirst()
                    .orElse("none");
        }

        public int invalidReplacementPlanValidationCount() {
            return (int) ruleReports.stream()
                    .flatMap(report -> report.replacementPlanValidations().stream())
                    .filter(validation -> !validation.valid())
                    .count();
        }

        public String firstReplacementPlanValidationBlocker() {
            return ruleReports.stream()
                    .flatMap(report -> report.replacementPlanValidations().stream())
                    .filter(validation -> !validation.valid())
                    .map(GpuRuntimeIrPeepholeReplacementPlanValidation::firstBlocker)
                    .findFirst()
                    .orElse("none");
        }

        public int rewriteVisitPreflightCount() {
            return (int) ruleReports.stream()
                    .flatMap(report -> report.rewriteVisitPreflights().stream())
                    .count();
        }

        public int rewriteVisitPreflightReadyCount() {
            return (int) ruleReports.stream()
                    .flatMap(report -> report.rewriteVisitPreflights().stream())
                    .filter(GpuRuntimeIrPeepholeRewriteVisitPreflight::visitorReady)
                    .count();
        }

        public int rewriteVisitPreflightBlockedCount() {
            return (int) ruleReports.stream()
                    .flatMap(report -> report.rewriteVisitPreflights().stream())
                    .filter(preflight -> !preflight.visitorReady())
                    .count();
        }

        public String firstRewriteVisitPreflightBlocker() {
            return ruleReports.stream()
                    .flatMap(report -> report.rewriteVisitPreflights().stream())
                    .filter(preflight -> !preflight.visitorReady())
                    .map(GpuRuntimeIrPeepholeRewriteVisitPreflight::firstBlocker)
                    .findFirst()
                    .orElse("none");
        }
    }
}
