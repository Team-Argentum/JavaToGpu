package net.sixik.ga_utils.javatogpu.runtime;

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
        List<GpuRuntimeRegisterPressureMethodEstimate> methods
) {

    public GpuRuntimeRegisterPressureReport {
        budget = budget == null
                ? new GpuRuntimeRegisterPressureBudget(null, 32, null)
                : budget;
        methods = methods == null ? List.of() : List.copyOf(methods);
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
        return hottestMethod()
                .map(GpuRuntimeRegisterPressureMethodEstimate::level)
                .orElse(GpuRuntimeRegisterPressureLevel.UNAVAILABLE);
    }

    public List<String> diagnostics() {
        if (!available()) {
            return List.of("register-pressure analysis unavailable: no typed IrGpu method bodies");
        }
        GpuRuntimeRegisterPressureMethodEstimate hottest = hottestMethod().orElseThrow();
        if (hottest.level() == GpuRuntimeRegisterPressureLevel.CRITICAL) {
            return List.of(
                    "register-pressure estimate exceeds advisory budget for " + hottest.methodName()
                            + ": estimated=" + hottest.estimatedValueRegisters()
                            + ", budget=" + hottest.advisoryBudget()
                            + "; reduce simultaneously live temporaries, private arrays, vector width, or unrolling"
            );
        }
        if (hottest.level() == GpuRuntimeRegisterPressureLevel.HIGH) {
            return List.of(
                    "register-pressure estimate is close to advisory budget for " + hottest.methodName()
                            + ": estimated=" + hottest.estimatedValueRegisters()
                            + ", budget=" + hottest.advisoryBudget()
            );
        }
        return List.of();
    }

    public Map<String, String> artifactFields() {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("analysisOnly", "true");
        fields.put("analysisKind", "register-pressure");
        fields.put("registerPressure.modelVersion", budget.modelVersion());
        fields.put("registerPressure.budget", Integer.toString(budget.valueRegisterBudget()));
        fields.put("registerPressure.budgetSource", budget.source());
        fields.put("registerPressure.available", Boolean.toString(available()));
        fields.put("registerPressure.level", level().name().toLowerCase(java.util.Locale.ROOT));
        fields.put("registerPressure.method.count", Integer.toString(methods.size()));
        long availableMethodCount = methods.stream().filter(GpuRuntimeRegisterPressureMethodEstimate::available).count();
        fields.put("registerPressure.method.available.count", Long.toString(availableMethodCount));
        hottestMethod().ifPresent(hottest -> {
            fields.put("registerPressure.hottestMethod", hottest.methodName());
            fields.put("registerPressure.estimatedValueRegisters", Integer.toString(hottest.estimatedValueRegisters()));
            fields.put("registerPressure.utilizationPermille", Integer.toString(hottest.utilizationPermille()));
        });
        for (int index = 0; index < methods.size(); index++) {
            fields.putAll(methods.get(index).artifactFields("registerPressure.method." + index));
        }
        List<String> diagnostics = diagnostics();
        fields.put("registerPressure.diagnostic.count", Integer.toString(diagnostics.size()));
        for (int index = 0; index < diagnostics.size(); index++) {
            fields.put("registerPressure.diagnostic." + index, diagnostics.get(index));
        }
        return Map.copyOf(fields);
    }
}
