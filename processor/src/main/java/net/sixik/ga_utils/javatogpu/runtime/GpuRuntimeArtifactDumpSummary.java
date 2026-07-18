package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backend-neutral summary of one runtime artifact dump attempt.
 *
 * <p>The dump payload remains backend-specific, but lifecycle journals should expose these small count fields with the
 * same {@code runtime.artifactDump.*} vocabulary for OpenCL, CUDA, and future adapters.</p>
 *
 * @param artifactCount number of text artifacts in the dump bundle
 * @param binaryArtifactCount number of binary artifacts in the dump bundle
 * @param sourceLocationCount number of source-location entries attached to the dump bundle
 * @param directoryCount number of output directories planned or written for the dump attempt
 */
public record GpuRuntimeArtifactDumpSummary(
        int artifactCount,
        int binaryArtifactCount,
        int sourceLocationCount,
        int directoryCount
) {

    public GpuRuntimeArtifactDumpSummary {
        artifactCount = Math.max(0, artifactCount);
        binaryArtifactCount = Math.max(0, binaryArtifactCount);
        sourceLocationCount = Math.max(0, sourceLocationCount);
        directoryCount = Math.max(0, directoryCount);
    }

    public static GpuRuntimeArtifactDumpSummary planned(int directoryCount) {
        return new GpuRuntimeArtifactDumpSummary(0, 0, 0, directoryCount);
    }

    public static GpuRuntimeArtifactDumpSummary from(GpuRuntimeCompileArtifactDump dump, int directoryCount) {
        if (dump == null) {
            return planned(directoryCount);
        }
        return new GpuRuntimeArtifactDumpSummary(
                dump.artifacts().size(),
                dump.binaryArtifacts().size(),
                dump.sourceLocations().size(),
                directoryCount
        );
    }

    /**
     * Renders the summary using the portable {@code runtime.artifactDump.*} lifecycle vocabulary.
     *
     * @param prefix field prefix, or {@code runtime.artifactDump} when blank
     * @return immutable portable field map
     */
    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.artifactDump" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        GpuRuntimeArtifactProperties.putPortable(fields, normalizedPrefix, "present", true);
        GpuRuntimeArtifactProperties.putPortable(fields, normalizedPrefix, "artifact.count", artifactCount);
        GpuRuntimeArtifactProperties.putPortable(fields, normalizedPrefix, "binaryArtifact.count", binaryArtifactCount);
        GpuRuntimeArtifactProperties.putPortable(fields, normalizedPrefix, "sourceLocation.count", sourceLocationCount);
        GpuRuntimeArtifactProperties.putPortable(fields, normalizedPrefix, "directory.count", directoryCount);
        return Collections.unmodifiableMap(fields);
    }
}
