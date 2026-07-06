package net.sixik.ga_utils.javatogpu.frontend.asm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Read-only inventory of risky JVM bytecode shapes seen during ASM artifact preflight.
 */
public record AsmBytecodeShapeInventoryReport(
        List<AsmBytecodeShapeObservation> observations
) {
    public AsmBytecodeShapeInventoryReport {
        observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
        if (observations.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("observations must not contain null entries");
        }
    }

    public int observationCount() {
        return observations.size();
    }

    public int riskyShapeCount() {
        return (int) observations.stream().filter(observation -> observation.kind().risky()).count();
    }

    public boolean hasRiskyShapes() {
        return riskyShapeCount() > 0;
    }

    public Map<String, Long> kindCounts() {
        return observations.stream()
                .collect(Collectors.groupingBy(
                        observation -> observation.kind().artifactValue(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public List<String> riskySummaries() {
        return observations.stream()
                .filter(observation -> observation.kind().risky())
                .map(AsmBytecodeShapeObservation::summary)
                .toList();
    }

    public String summaryLine() {
        return "asmShapeInventory observations="
                + observationCount()
                + " risky="
                + riskyShapeCount()
                + " kinds="
                + kindCounts();
    }

    public Map<String, String> artifactFields(String prefix) {
        String safePrefix = prefix == null || prefix.isBlank() ? "asmShapeInventory" : prefix;
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(safePrefix + ".observationCount", Integer.toString(observationCount()));
        fields.put(safePrefix + ".riskyShapeCount", Integer.toString(riskyShapeCount()));
        fields.put(safePrefix + ".hasRiskyShapes", Boolean.toString(hasRiskyShapes()));
        fields.put(safePrefix + ".kindCounts", kindCounts().toString());
        fields.put(safePrefix + ".summary", summaryLine());
        List<String> risky = riskySummaries();
        if (!risky.isEmpty()) {
            fields.put(safePrefix + ".firstRiskyShape", risky.get(0));
            fields.put(safePrefix + ".riskySummaries", String.join(" | ", risky));
        }
        for (Map.Entry<String, Long> entry : kindCounts().entrySet()) {
            fields.put(safePrefix + ".kind." + entry.getKey(), Long.toString(entry.getValue()));
        }
        for (int index = 0; index < observations.size(); index++) {
            fields.putAll(observations.get(index).artifactFields(safePrefix + ".observation." + index));
        }
        return Map.copyOf(fields);
    }
}
