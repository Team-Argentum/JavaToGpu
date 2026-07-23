package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lightweight catalog/provider metadata attached to one backend selection candidate.
 */
public record GpuRuntimeBackendCandidateMetadata(
        GpuBackendTarget backendTarget,
        String backendName,
        boolean productionAdapter,
        Optional<GpuRuntimeBackendExecutionSupport> executionSupport,
        String diagnostic
) {

    public GpuRuntimeBackendCandidateMetadata {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        backendName = backendName == null || backendName.isBlank() ? backendTarget.name() : backendName;
        executionSupport = executionSupport == null ? Optional.empty() : executionSupport;
        diagnostic = diagnostic == null || diagnostic.isBlank() ? "none" : diagnostic;
    }

    public static GpuRuntimeBackendCandidateMetadata unknown() {
        return new GpuRuntimeBackendCandidateMetadata(
                GpuBackendTarget.UNKNOWN,
                "unknown",
                false,
                Optional.empty(),
                "candidate was not created from an inspectable backend catalog entry"
        );
    }

    public static GpuRuntimeBackendCandidateMetadata from(GpuRuntimeBackendCatalogEntry entry) {
        if (entry == null) {
            return unknown();
        }
        return new GpuRuntimeBackendCandidateMetadata(
                entry.backendTarget(),
                entry.backendName(),
                entry.productionAdapter(),
                entry.executionSupport(),
                entry.diagnostic()
        );
    }

    public boolean executionSupportPresent() {
        return executionSupport.isPresent();
    }

    public boolean declaresModuleFormat(GpuBackendModuleFormat moduleFormat) {
        return moduleFormat != null && executionSupport.map(support -> support.declaresModuleFormat(moduleFormat)).orElse(false);
    }

    public boolean declaresCapability(GpuRuntimeCapability capability) {
        return capability != null && executionSupport.map(support -> support.declaresCapability(capability)).orElse(false);
    }

    public String moduleFormatKeys() {
        return executionSupport.map(GpuRuntimeBackendExecutionSupport::moduleFormatKeys).orElse("");
    }

    public String capabilityKeys() {
        return executionSupport.map(GpuRuntimeBackendExecutionSupport::capabilityKeys).orElse("");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "backendCandidate.metadata" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".backendTarget", backendTarget.name());
        fields.put(normalizedPrefix + ".backendName", backendName);
        fields.put(normalizedPrefix + ".productionAdapter", Boolean.toString(productionAdapter));
        fields.put(normalizedPrefix + ".executionSupport.present", Boolean.toString(executionSupportPresent()));
        fields.put(normalizedPrefix + ".executionSupport.moduleFormats", moduleFormatKeys());
        fields.put(normalizedPrefix + ".executionSupport.capabilities", capabilityKeys());
        fields.put(normalizedPrefix + ".diagnostic", diagnostic);
        executionSupport.ifPresent(support -> fields.putAll(support.artifactFields(normalizedPrefix + ".executionSupport")));
        return Collections.unmodifiableMap(fields);
    }
}
