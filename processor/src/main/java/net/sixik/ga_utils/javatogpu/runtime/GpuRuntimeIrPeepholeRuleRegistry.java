package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtension;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionFailurePolicy;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionRegistry;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Deterministic registry for built-in and third-party typed peephole rules.
 */
public final class GpuRuntimeIrPeepholeRuleRegistry {

    private final List<GpuRuntimeIrPeepholeRule> rules;
    private final GpuExtensionRegistry extensionRegistry;

    private GpuRuntimeIrPeepholeRuleRegistry(List<GpuRuntimeIrPeepholeRule> rules) {
        this.rules = List.copyOf(rules);
        validateUniqueRuleIds(this.rules);
        this.extensionRegistry = GpuExtensionRegistry.of(this.rules);
        this.extensionRegistry.requirePipelineContract(
                "runtime IR peephole rule pipeline",
                GpuExtensionPhase.RUNTIME_IR_OPTIMIZATION,
                GpuExtensionPermission.MUTATION_PROPOSAL,
                GpuExtensionCapability.IR_OPTIMIZATION_PROPOSAL
        );
    }

    public static GpuRuntimeIrPeepholeRuleRegistry of(List<GpuRuntimeIrPeepholeRule> rules) {
        return new GpuRuntimeIrPeepholeRuleRegistry(rules == null ? List.of() : rules);
    }

    public static GpuRuntimeIrPeepholeRuleRegistry loadWithBuiltIns() {
        ArrayList<GpuRuntimeIrPeepholeRule> loaded = new ArrayList<>();
        loaded.add(new GpuRuntimeMadFmaPeepholeRule());
        ServiceLoader.load(GpuRuntimeIrPeepholeRule.class, GpuRuntimeIrPeepholeRule.class.getClassLoader())
                .forEach(loaded::add);
        loaded.sort(Comparator
                .comparingInt((GpuRuntimeIrPeepholeRule rule) -> rule.extensionOrder())
                .thenComparing(GpuRuntimeIrPeepholeRule::ruleId)
                .thenComparing(GpuRuntimeIrPeepholeRule::ruleVersion));
        return of(loaded);
    }

    public Analysis analyze(GpuRuntimeIrOptimizationRequest request, IrGpuArtifact artifact) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(artifact, "artifact");
        ArrayList<GpuRuntimeIrPeepholeRuleReport> reports = new ArrayList<>();
        ArrayList<GpuExtensionExecutionReport> executions = new ArrayList<>();
        boolean failedClosed = false;
        for (GpuRuntimeIrPeepholeRule rule : rules) {
            for (IrGpuMethodBody methodBody : artifact.module().methodBodies()) {
                if (!methodBody.typedBody().available()) {
                    continue;
                }
                try {
                    GpuRuntimeIrPeepholeRuleReport report = Objects.requireNonNull(
                            rule.analyze(new GpuRuntimeIrPeepholeRuleContext(request, artifact, methodBody)),
                            "peephole rule report"
                    );
                    validateRuleReport(rule, report);
                    reports.add(report);
                    executions.add(GpuExtensionExecutionReport.succeeded(
                            rule,
                            "peephole analysis " + methodBody.name()
                    ));
                } catch (RuntimeException exception) {
                    GpuExtensionFailurePolicy failurePolicy = GpuRuntimeProductionProfiles.isProductionProfile(
                            request.compileRequest().options().optimizationProfile()
                    ) ? GpuExtensionFailurePolicy.STOP_PIPELINE : GpuExtensionFailurePolicy.CONTINUE;
                    executions.add(GpuExtensionExecutionReport.failed(
                            rule,
                            "peephole analysis " + methodBody.name(),
                            failurePolicy,
                            exception
                    ));
                    if (failurePolicy == GpuExtensionFailurePolicy.STOP_PIPELINE) {
                        failedClosed = true;
                        break;
                    }
                }
            }
            if (failedClosed) {
                break;
            }
        }
        return new Analysis(reports, executions, failedClosed);
    }

    public List<GpuRuntimeIrPeepholeRule> rules() {
        return rules;
    }

    public GpuExtensionRegistry extensionRegistry() {
        return extensionRegistry;
    }

    private static void validateUniqueRuleIds(List<GpuRuntimeIrPeepholeRule> rules) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (GpuRuntimeIrPeepholeRule rule : rules) {
            String ruleId = Objects.requireNonNull(rule, "rule").ruleId();
            if (ruleId == null || ruleId.isBlank()) {
                throw new IllegalArgumentException("Peephole rule id must not be blank");
            }
            if (!ids.add(ruleId)) {
                throw new IllegalArgumentException("Duplicate peephole rule id '" + ruleId + "'");
            }
        }
    }

    private static void validateRuleReport(
            GpuRuntimeIrPeepholeRule rule,
            GpuRuntimeIrPeepholeRuleReport report
    ) {
        if (!rule.ruleId().equals(report.ruleId()) || !rule.ruleVersion().equals(report.ruleVersion())) {
            throw new IllegalArgumentException(
                    "Peephole rule report identity does not match registered rule " + rule.ruleId()
            );
        }
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

        public int candidateCount() {
            return ruleReports.stream().mapToInt(GpuRuntimeIrPeepholeRuleReport::candidateCount).sum();
        }

        public int proposalCount() {
            return ruleReports.stream().mapToInt(GpuRuntimeIrPeepholeRuleReport::proposalCount).sum();
        }
    }
}
