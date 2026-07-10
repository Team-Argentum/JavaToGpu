package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;

/**
 * Human-readable dump bundle for runtime compilation artifacts.
 */
public record GpuRuntimeCompileArtifactDump(
        Map<String, String> artifacts,
        List<String> sourceLocations,
        GpuRuntimeCompileInvalidationStamp invalidationStamp,
        Map<String, GpuRuntimeBinaryArtifact> binaryArtifacts
) {

    public GpuRuntimeCompileArtifactDump {
        artifacts = artifacts == null ? Map.of() : Map.copyOf(artifacts);
        sourceLocations = sourceLocations == null ? List.of() : List.copyOf(sourceLocations);
        binaryArtifacts = binaryArtifacts == null ? Map.of() : Map.copyOf(binaryArtifacts);
    }

    public GpuRuntimeCompileArtifactDump(
            Map<String, String> artifacts,
            List<String> sourceLocations,
            GpuRuntimeCompileInvalidationStamp invalidationStamp
    ) {
        this(artifacts, sourceLocations, invalidationStamp, Map.of());
    }

    public String artifact(String name) {
        return artifacts.getOrDefault(name, "");
    }

    public boolean hasArtifact(String name) {
        return artifacts.containsKey(name) && !artifacts.get(name).isBlank();
    }

    public boolean hasBinaryArtifact(String name) {
        return binaryArtifacts.containsKey(name) && binaryArtifacts.get(name).size() > 0;
    }
}
