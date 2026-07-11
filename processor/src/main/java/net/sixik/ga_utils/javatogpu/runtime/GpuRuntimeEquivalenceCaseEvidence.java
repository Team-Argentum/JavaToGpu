package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable raw inputs and outputs captured for one runtime-equivalence comparison case.
 */
public record GpuRuntimeEquivalenceCaseEvidence(
        String caseName,
        String comparisonMode,
        Map<String, String> inputs,
        Map<String, String> referenceOutputs,
        Map<String, String> candidateOutputs,
        Map<String, String> tolerances,
        Map<String, Boolean> outputEquivalence,
        List<String> diagnostics
) {
    public GpuRuntimeEquivalenceCaseEvidence {
        if (caseName == null || caseName.isBlank()) {
            throw new IllegalArgumentException("caseName must not be blank");
        }
        if (comparisonMode == null || comparisonMode.isBlank()) {
            throw new IllegalArgumentException("comparisonMode must not be blank");
        }
        inputs = immutableStringMap(inputs, "inputs");
        referenceOutputs = immutableStringMap(referenceOutputs, "referenceOutputs");
        candidateOutputs = immutableStringMap(candidateOutputs, "candidateOutputs");
        tolerances = immutableStringMap(tolerances, "tolerances");
        outputEquivalence = immutableBooleanMap(outputEquivalence);
        diagnostics = immutableDiagnostics(diagnostics);

        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs must not be empty");
        }
        if (referenceOutputs.isEmpty()) {
            throw new IllegalArgumentException("output evidence must not be empty");
        }
        if (!referenceOutputs.keySet().equals(candidateOutputs.keySet())
                || !referenceOutputs.keySet().equals(tolerances.keySet())
                || !referenceOutputs.keySet().equals(outputEquivalence.keySet())) {
            throw new IllegalArgumentException("all output evidence maps must use the same output names");
        }
    }

    public boolean successful() {
        return diagnostics.isEmpty() && outputEquivalence.values().stream().allMatch(Boolean.TRUE::equals);
    }

    public void appendProperties(StringBuilder builder, String prefix) {
        Objects.requireNonNull(builder, "builder");
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        appendProperty(builder, prefix + "name", caseName);
        appendProperty(builder, prefix + "comparisonMode", comparisonMode);
        appendProperty(builder, prefix + "successful", Boolean.toString(successful()));
        appendProperty(builder, prefix + "input.count", Integer.toString(inputs.size()));
        int inputIndex = 0;
        for (Map.Entry<String, String> entry : inputs.entrySet()) {
            appendProperty(builder, prefix + "input." + inputIndex + ".name", entry.getKey());
            appendProperty(builder, prefix + "input." + inputIndex + ".value", entry.getValue());
            inputIndex++;
        }
        appendProperty(builder, prefix + "output.count", Integer.toString(referenceOutputs.size()));
        int outputIndex = 0;
        for (String outputName : referenceOutputs.keySet()) {
            String outputPrefix = prefix + "output." + outputIndex + ".";
            appendProperty(builder, outputPrefix + "name", outputName);
            appendProperty(builder, outputPrefix + "reference", referenceOutputs.get(outputName));
            appendProperty(builder, outputPrefix + "candidate", candidateOutputs.get(outputName));
            appendProperty(builder, outputPrefix + "tolerance", tolerances.get(outputName));
            appendProperty(builder, outputPrefix + "equivalent", Boolean.toString(outputEquivalence.get(outputName)));
            outputIndex++;
        }
        appendProperty(builder, prefix + "diagnostic.count", Integer.toString(diagnostics.size()));
        for (int diagnosticIndex = 0; diagnosticIndex < diagnostics.size(); diagnosticIndex++) {
            appendProperty(builder, prefix + "diagnostic." + diagnosticIndex, diagnostics.get(diagnosticIndex));
        }
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

    private static void appendProperty(StringBuilder builder, String key, String value) {
        builder.append(key)
                .append('=')
                .append(value.replace('\r', ' ').replace('\n', ' '))
                .append('\n');
    }
}
