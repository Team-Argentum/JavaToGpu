package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;

/**
 * Human-readable dump bundle for runtime compilation artifacts.
 */
public record GpuRuntimeCompileArtifactDump(
        Map<String, String> artifacts,
        List<String> sourceLocations,
        GpuRuntimeCompileInvalidationStamp invalidationStamp
) {

    public GpuRuntimeCompileArtifactDump {
        artifacts = artifacts == null ? Map.of() : Map.copyOf(artifacts);
        sourceLocations = sourceLocations == null ? List.of() : List.copyOf(sourceLocations);
    }

    public String artifact(String name) {
        return artifacts.getOrDefault(name, "");
    }

    public boolean hasArtifact(String name) {
        return artifacts.containsKey(name) && !artifacts.get(name).isBlank();
    }
}
