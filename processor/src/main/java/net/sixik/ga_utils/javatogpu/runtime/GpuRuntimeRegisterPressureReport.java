package net.sixik.ga_utils.javatogpu.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Aggregate advisory register-pressure evidence for one runtime IrGpu artifact.
 */
public record GpuRuntimeRegisterPressureReport(
        GpuRuntimeRegisterPressureBudget budget,
        List<GpuRuntimeRegisterPressureMethodEstimate> methods,
        List<GpuRuntimeRegisterPressureCallEstimate> calls
) {

    public GpuRuntimeRegisterPressureReport(
            GpuRuntimeRegisterPressureBudget budget,
            List<GpuRuntimeRegisterPressureMethodEstimate> methods
    ) {
        this(budget, methods, List.of());
    }

    public GpuRuntimeRegisterPressureReport {
        budget = budget == null
                ? new GpuRuntimeRegisterPressureBudget(null, 32, null)
                : budget;
        methods = methods == null ? List.of() : List.copyOf(methods);
        calls = calls == null ? List.of() : List.copyOf(calls);
    }

    public boolean available() {
        return methods.stream().anyMatch(GpuRuntimeRegisterPressureMethodEstimate::available);
    }

    public Optional<GpuRuntimeRegisterPressureMethodEstimate> hottestMethod() {
        return methods.stream()
                .filter(GpuRuntimeRegisterPressureMethodEstimate::available)
                .max(Comparator
                        .comparingInt(GpuRuntimeRegisterPressureMethodEstimate::estimatedValueRegisters)
                        .thenComparing(GpuRuntimeRegisterPressureMethodEstimate::methodName));
    }

    public GpuRuntimeRegisterPressureLevel level() {
        return GpuRuntimeRegisterPressureLevel.fromUtilization(
                available(),
                utilizationPermille(effectiveEstimatedValueRegisters())
        );
    }

    public int effectiveEstimatedValueRegisters() {
        int methodEstimate = hottestMethod()
                .map(GpuRuntimeRegisterPressureMethodEstimate::estimatedValueRegisters)
                .orElse(0);
        int callEstimate = calls.stream()
                .mapToInt(GpuRuntimeRegisterPressureCallEstimate::combinedEstimatedRegisters)
                .max()
                .orElse(0);
        return Math.max(methodEstimate, callEstimate);
    }

    public Optional<GpuRuntimeRegisterPressureCallEstimate> hottestCall() {
        return calls.stream()
                .max(Comparator
                        .comparingInt(GpuRuntimeRegisterPressureCallEstimate::combinedEstimatedRegisters)
                        .thenComparing(GpuRuntimeRegisterPressureCallEstimate::callerMethod)
                        .thenComparingInt(GpuRuntimeRegisterPressureCallEstimate::callNodeId));
    }

    public List<String> diagnostics() {
        if (!available()) {
            return List.of("register-pressure analysis unavailable: no typed IrGpu method bodies");
        }
        GpuRuntimeRegisterPressureMethodEstimate hottest = hottestMethod().orElseThrow();
        ArrayList<String> diagnostics = new ArrayList<>();
        GpuRuntimeRegisterPressureCallEstimate hottestCall = hottestCall().orElse(null);
        boolean callDominates = hottestCall != null
                && hottestCall.combinedEstimatedRegisters() > hottest.estimatedValueRegisters();
        if (level() == GpuRuntimeRegisterPressureLevel.CRITICAL && callDominates) {
            diagnostics.add(
                    "interprocedural register-pressure estimate exceeds advisory budget at "
                            + hottestCall.callerMethod() + " -> " + hottestCall.helperName()
                            + ": estimated=" + hottestCall.combinedEstimatedRegisters()
                            + ", budget=" + budget.valueRegisterBudget()
            );
        } else if (level() == GpuRuntimeRegisterPressureLevel.HIGH && callDominates) {
            diagnostics.add(
                    "interprocedural register-pressure estimate is close to advisory budget at "
                            + hottestCall.callerMethod() + " -> " + hottestCall.helperName()
                            + ": estimated=" + hottestCall.combinedEstimatedRegisters()
                            + ", budget=" + budget.valueRegisterBudget()
            );
        } else if (hottest.level() == GpuRuntimeRegisterPressureLevel.CRITICAL) {
            diagnostics.add(
                    "register-pressure estimate exceeds advisory budget for " + hottest.methodName()
                            + ": estimated=" + hottest.estimatedValueRegisters()
                            + ", budget=" + hottest.advisoryBudget()
                            + "; reduce simultaneously live temporaries, private arrays, vector width, or unrolling"
            );
        } else if (hottest.level() == GpuRuntimeRegisterPressureLevel.HIGH) {
            diagnostics.add(
                    "register-pressure estimate is close to advisory budget for " + hottest.methodName()
                            + ": estimated=" + hottest.estimatedValueRegisters()
                            + ", budget=" + hottest.advisoryBudget()
            );
        }
        if (hottest.unresolvedReferenceCount() > 0) {
            diagnostics.add(
                    "register-pressure liveness contains unresolved references for " + hottest.methodName()
                            + ": count=" + hottest.unresolvedReferenceCount()
                            + "; pressure remains advisory until the typed IrGpu reference is resolved"
            );
        }
        calls.stream()
                .filter(call -> !call.resolved())
                .forEach(call -> diagnostics.add(
                        "register-pressure helper call is unresolved at " + call.callerMethod()
                                + " node=" + call.callNodeId()
                                + ": helper=" + call.helperName()
                ));
        calls.stream()
                .filter(GpuRuntimeRegisterPressureCallEstimate::recursive)
                .forEach(call -> diagnostics.add(
                        "register-pressure helper recursion detected at " + call.callerMethod()
                                + " -> " + call.helperName()
                                + "; recursive frame growth is not promoted from advisory evidence"
                ));
        return List.copyOf(diagnostics);
    }

    public Map<String, String> artifactFields() {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("analysisOnly", "true");
        fields.put("analysisKind", "register-pressure");
        fields.put("registerPressure.analysisVersion", GpuRuntimeRegisterPressureAnalyzer.ANALYSIS_VERSION);
        fields.put("registerPressure.modelVersion", budget.modelVersion());
        fields.put("registerPressure.budget", Integer.toString(budget.valueRegisterBudget()));
        fields.put("registerPressure.budgetSource", budget.source());
        fields.put("registerPressure.available", Boolean.toString(available()));
        fields.put("registerPressure.level", level().name().toLowerCase(java.util.Locale.ROOT));
        fields.put("registerPressure.estimatedValueRegisters", Integer.toString(
                effectiveEstimatedValueRegisters()
        ));
        fields.put("registerPressure.utilizationPermille", Integer.toString(
                utilizationPermille(effectiveEstimatedValueRegisters())
        ));
        fields.put("registerPressure.method.count", Integer.toString(methods.size()));
        long availableMethodCount = methods.stream().filter(GpuRuntimeRegisterPressureMethodEstimate::available).count();
        fields.put("registerPressure.method.available.count", Long.toString(availableMethodCount));
        fields.put("registerPressure.scopedVariable.count", Long.toString(methods.stream()
                .mapToLong(GpuRuntimeRegisterPressureMethodEstimate::scopedVariableCount)
                .sum()));
        fields.put("registerPressure.shadowedVariable.count", Long.toString(methods.stream()
                .mapToLong(GpuRuntimeRegisterPressureMethodEstimate::shadowedVariableCount)
                .sum()));
        fields.put("registerPressure.unresolvedReference.count", Long.toString(methods.stream()
                .mapToLong(GpuRuntimeRegisterPressureMethodEstimate::unresolvedReferenceCount)
                .sum()));
        hottestMethod().ifPresent(hottest -> fields.put("registerPressure.hottestMethod", hottest.methodName()));
        fields.put("registerPressure.call.count", Integer.toString(calls.size()));
        fields.put("registerPressure.call.resolved.count", Long.toString(calls.stream()
                .filter(GpuRuntimeRegisterPressureCallEstimate::resolved)
                .count()));
        fields.put("registerPressure.call.inline.count", Long.toString(calls.stream()
                .filter(GpuRuntimeRegisterPressureCallEstimate::inline)
                .count()));
        fields.put("registerPressure.call.recursive.count", Long.toString(calls.stream()
                .filter(GpuRuntimeRegisterPressureCallEstimate::recursive)
                .count()));
        hottestCall().ifPresent(call -> {
            fields.put("registerPressure.hottestCall.callerMethod", call.callerMethod());
            fields.put("registerPressure.hottestCall.helperName", call.helperName());
            fields.put("registerPressure.hottestCall.combinedEstimatedRegisters", Integer.toString(
                    call.combinedEstimatedRegisters()
            ));
        });
        for (int index = 0; index < methods.size(); index++) {
            fields.putAll(methods.get(index).artifactFields("registerPressure.method." + index));
        }
        for (int index = 0; index < calls.size(); index++) {
            fields.putAll(calls.get(index).artifactFields("registerPressure.call." + index));
        }
        List<String> diagnostics = diagnostics();
        fields.put("registerPressure.diagnostic.count", Integer.toString(diagnostics.size()));
        for (int index = 0; index < diagnostics.size(); index++) {
            fields.put("registerPressure.diagnostic." + index, diagnostics.get(index));
        }
        return Map.copyOf(fields);
    }

    private int utilizationPermille(int registers) {
        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.round((double) Math.max(0, registers) * 1_000.0 / budget.valueRegisterBudget())
        );
    }
}
