package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable raw input and output evidence for one opt-in runtime-equivalence case.
 */
public record GpuIrRuntimeEquivalenceCaseEvidence(
        String caseName,
        Map<String, String> inputs,
        Map<String, String> cpuReferenceOutputs,
        Map<String, String> preOptimizationOutputs,
        Map<String, String> postOptimizationOutputs,
        Map<String, String> tolerances,
        Map<String, Boolean> outputEquivalence,
        List<String> diagnostics
) {
    public static final String NOT_RECORDED = "not-recorded";

    public GpuIrRuntimeEquivalenceCaseEvidence {
        if (caseName == null || caseName.isBlank()) {
            throw new IllegalArgumentException("caseName must not be blank");
        }
        inputs = immutableStringMap(inputs, "inputs");
        cpuReferenceOutputs = immutableStringMap(cpuReferenceOutputs, "cpuReferenceOutputs");
        preOptimizationOutputs = immutableStringMap(preOptimizationOutputs, "preOptimizationOutputs");
        postOptimizationOutputs = immutableStringMap(postOptimizationOutputs, "postOptimizationOutputs");
        tolerances = immutableStringMap(tolerances, "tolerances");
        outputEquivalence = immutableBooleanMap(outputEquivalence);
        diagnostics = immutableDiagnostics(diagnostics);

        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs must not be empty");
        }
        if (cpuReferenceOutputs.isEmpty()) {
            throw new IllegalArgumentException("output evidence must not be empty");
        }
        if (!cpuReferenceOutputs.keySet().equals(preOptimizationOutputs.keySet())
                || !cpuReferenceOutputs.keySet().equals(postOptimizationOutputs.keySet())
                || !cpuReferenceOutputs.keySet().equals(tolerances.keySet())
                || !cpuReferenceOutputs.keySet().equals(outputEquivalence.keySet())) {
            throw new IllegalArgumentException("all output evidence maps must use the same output names");
        }
    }

    public boolean successful() {
        return diagnostics.isEmpty() && outputEquivalence.values().stream().allMatch(Boolean.TRUE::equals);
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(prefix + "Name", caseName);
        fields.put(prefix + "Successful", Boolean.toString(successful()));
        fields.put(prefix + "Input.Count", Integer.toString(inputs.size()));
        int inputIndex = 0;
        for (Map.Entry<String, String> entry : inputs.entrySet()) {
            fields.put(prefix + "Input." + inputIndex + ".Name", entry.getKey());
            fields.put(prefix + "Input." + inputIndex + ".Value", entry.getValue());
            inputIndex++;
        }
        fields.put(prefix + "Output.Count", Integer.toString(cpuReferenceOutputs.size()));
        int outputIndex = 0;
        for (String outputName : cpuReferenceOutputs.keySet()) {
            String outputPrefix = prefix + "Output." + outputIndex + ".";
            fields.put(outputPrefix + "Name", outputName);
            fields.put(outputPrefix + "CpuReference", cpuReferenceOutputs.get(outputName));
            fields.put(outputPrefix + "PreOptimization", preOptimizationOutputs.get(outputName));
            fields.put(outputPrefix + "PostOptimization", postOptimizationOutputs.get(outputName));
            fields.put(outputPrefix + "Tolerance", tolerances.get(outputName));
            fields.put(outputPrefix + "Equivalent", Boolean.toString(outputEquivalence.get(outputName)));
            outputIndex++;
        }
        fields.put(prefix + "FailureFixture.Diagnostic.Count", Integer.toString(diagnostics.size()));
        for (int diagnosticIndex = 0; diagnosticIndex < diagnostics.size(); diagnosticIndex++) {
            fields.put(
                    prefix + "FailureFixture.Diagnostic." + diagnosticIndex,
                    diagnostics.get(diagnosticIndex)
            );
        }
        return Collections.unmodifiableMap(fields);
    }

    private static Map<String, String> immutableStringMap(Map<String, String> values, String name) {
        Objects.requireNonNull(values, name);
        TreeMap<String, String> sorted = new TreeMap<>();
        values.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException(name + " must not contain blank keys");
            }
            if (value == null) {
                throw new IllegalArgumentException(name + " must not contain null values");
            }
            sorted.put(key, value);
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Map<String, Boolean> immutableBooleanMap(Map<String, Boolean> values) {
        Objects.requireNonNull(values, "outputEquivalence");
        TreeMap<String, Boolean> sorted = new TreeMap<>();
        values.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("outputEquivalence must not contain blank keys");
            }
            if (value == null) {
                throw new IllegalArgumentException("outputEquivalence must not contain null values");
            }
            sorted.put(key, value);
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static List<String> immutableDiagnostics(List<String> diagnostics) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (diagnostics.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("diagnostics must not contain null entries");
        }
        return List.copyOf(diagnostics);
    }
}
