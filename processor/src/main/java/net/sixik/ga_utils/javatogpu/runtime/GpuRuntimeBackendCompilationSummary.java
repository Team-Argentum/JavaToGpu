package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backend-neutral summary of one backend compilation attempt.
 *
 * <p>Detailed module, provenance, and failure fields remain in their existing namespaces. This compact
 * {@code runtime.compilation.*} surface gives lifecycle journals a stable result summary that future execution
 * adapters can emit without copying OpenCL-specific aliases.</p>
 *
 * @param cacheKeyPresent whether the completed compilation has a stable runtime cache key
 * @param modulePresent whether a backend module artifact was available for the event
 * @param moduleFormat backend module format such as {@code opencl-c}, {@code ptx}, or {@code unknown}
 * @param compileLogPresent whether backend compiler output was captured
 * @param binaryArtifactCount number of binary artifacts attached to the compile snapshot
 * @param validationEvidenceCount number of runtime validation evidence entries attached to the compile snapshot
 */
public record GpuRuntimeBackendCompilationSummary(
        boolean cacheKeyPresent,
        boolean modulePresent,
        String moduleFormat,
        boolean compileLogPresent,
        int binaryArtifactCount,
        int validationEvidenceCount
) {

    public GpuRuntimeBackendCompilationSummary {
        moduleFormat = moduleFormat == null || moduleFormat.isBlank() ? "unknown" : moduleFormat.trim();
        binaryArtifactCount = Math.max(0, binaryArtifactCount);
        validationEvidenceCount = Math.max(0, validationEvidenceCount);
    }

    public static GpuRuntimeBackendCompilationSummary from(
            GpuBackendModuleArtifact moduleArtifact,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            String cacheKey
    ) {
        boolean moduleAvailable = moduleArtifact != null;
        GpuBackendModuleArtifact summaryModule = moduleAvailable
                ? moduleArtifact
                : artifactSnapshot == null ? null : artifactSnapshot.backendModuleArtifact();
        return new GpuRuntimeBackendCompilationSummary(
                cacheKey != null && !cacheKey.isBlank(),
                summaryModule != null,
                summaryModule == null ? "unknown" : summaryModule.format(),
                artifactSnapshot != null && !artifactSnapshot.compileLog().isBlank(),
                artifactSnapshot == null ? 0 : artifactSnapshot.binaryArtifacts().size(),
                artifactSnapshot == null ? 0 : artifactSnapshot.runtimeValidationEvidence().size()
        );
    }

    /**
     * Renders the summary using the portable {@code runtime.compilation.*} lifecycle vocabulary.
     *
     * @param prefix field prefix, or {@code runtime.compilation} when blank
     * @return immutable portable field map
     */
    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.compilation" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        GpuRuntimeArtifactProperties.putPortable(fields, normalizedPrefix, "present", true);
        GpuRuntimeArtifactProperties.putPortable(fields, normalizedPrefix, "cacheKey.present", cacheKeyPresent);
        GpuRuntimeArtifactProperties.putPortable(fields, normalizedPrefix, "module.present", modulePresent);
        GpuRuntimeArtifactProperties.putPortable(fields, normalizedPrefix, "module.format", moduleFormat);
        GpuRuntimeArtifactProperties.putPortable(fields, normalizedPrefix, "compileLog.present", compileLogPresent);
        GpuRuntimeArtifactProperties.putPortable(fields, normalizedPrefix, "binaryArtifact.count", binaryArtifactCount);
        GpuRuntimeArtifactProperties.putPortable(
                fields,
                normalizedPrefix,
                "validationEvidence.count",
                validationEvidenceCount
        );
        return Collections.unmodifiableMap(fields);
    }
}
