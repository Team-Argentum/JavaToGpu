package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * User-facing provider card for backend execution readiness.
 */
public record GpuRuntimeBackendExecutionAvailability(
        GpuBackendTarget backendTarget,
        String providerId,
        String providerVersion,
        GpuRuntimeBackendExecutionSupport executionSupport,
        boolean executionPipelineFactoryPresent
) {

    public GpuRuntimeBackendExecutionAvailability {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        providerId = providerId == null || providerId.isBlank() ? "unknown-provider" : providerId.trim();
        providerVersion = providerVersion == null || providerVersion.isBlank() ? "unknown" : providerVersion.trim();
        executionSupport = executionSupport == null
                ? GpuRuntimeBackendExecutionSupport.discoveryOnly(
                        backendTarget,
                        providerId,
                        "backend provider did not declare execution support"
                )
                : executionSupport;
    }

    public static GpuRuntimeBackendExecutionAvailability from(GpuRuntimeBackendProvider provider) {
        Objects.requireNonNull(provider, "provider");
        return new GpuRuntimeBackendExecutionAvailability(
                provider.backendTarget(),
                provider.providerId(),
                provider.providerVersion(),
                provider.executionSupport(),
                provider.executionPipelineFactory().isPresent()
        );
    }

    public boolean executionStagesAvailable() {
        return executionSupport.executionPipelineAvailable();
    }

    public boolean sharedPipelineRunnerAvailable() {
        return executionStagesAvailable() && executionPipelineFactoryPresent;
    }

    public String status() {
        if (sharedPipelineRunnerAvailable()) {
            return "execution-pipeline-available";
        }
        if (executionStagesAvailable()) {
            return "execution-stages-available-no-shared-runner";
        }
        return "execution-unavailable";
    }

    public List<String> blockers() {
        ArrayList<String> blockers = new ArrayList<>();
        for (GpuBackendPipelineStage stage : executionSupport.missingExecutionStages()) {
            blockers.add("backend-execution-stage-missing:" + stage.key());
        }
        if (executionStagesAvailable() && !executionPipelineFactoryPresent) {
            blockers.add("backend-execution-pipeline-factory-not-declared");
        }
        if (blockers.isEmpty() && !sharedPipelineRunnerAvailable()) {
            blockers.add("backend-execution-pipeline-unavailable");
        }
        return List.copyOf(blockers);
    }

    public List<String> diagnostics() {
        ArrayList<String> diagnostics = new ArrayList<>();
        if (!executionSupport.diagnostic().isBlank()) {
            diagnostics.add(executionSupport.diagnostic());
        }
        if (sharedPipelineRunnerAvailable()) {
            diagnostics.add("backend provider exposes a shared compile/prepare/invoke runner");
        } else if (executionStagesAvailable()) {
            diagnostics.add("backend provider declares execution stages, but no shared runner factory is registered");
        } else {
            diagnostics.add("backend provider is not ready to execute kernels through the shared pipeline");
        }
        return List.copyOf(diagnostics);
    }

    public String summary() {
        if (sharedPipelineRunnerAvailable()) {
            return backendTarget + " execution is available through provider " + providerId;
        }
        if (executionStagesAvailable()) {
            return backendTarget + " execution stages are declared, but no shared runner factory is available";
        }
        return backendTarget
                + " execution unavailable through provider "
                + providerId
                + ": "
                + String.join(", ", blockers());
    }

    public GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> unsupportedPipelineResult(
            GpuBackendLoweringResult loweringResult
    ) {
        return GpuBackendExecutionPipelineResult.unsupported(
                backendTarget,
                loweringResult,
                blockers(),
                diagnostics()
        );
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.backend.executionAvailability"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.putAll(executionSupport.artifactFields(normalizedPrefix + ".support"));
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".backendTarget", backendTarget.name());
        fields.put(normalizedPrefix + ".providerId", providerId);
        fields.put(normalizedPrefix + ".providerVersion", providerVersion);
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".summary", summary());
        fields.put(normalizedPrefix + ".executionStages.available", Boolean.toString(executionStagesAvailable()));
        fields.put(normalizedPrefix + ".sharedRunner.available", Boolean.toString(sharedPipelineRunnerAvailable()));
        fields.put(normalizedPrefix + ".factory.present", Boolean.toString(executionPipelineFactoryPresent));
        fields.put(normalizedPrefix + ".moduleFormats", executionSupport.moduleFormatKeys());
        fields.put(normalizedPrefix + ".capabilities", executionSupport.capabilityKeys());
        List<String> blockers = blockers();
        fields.put(normalizedPrefix + ".blocker.count", Integer.toString(blockers.size()));
        for (int index = 0; index < blockers.size(); index++) {
            fields.put(normalizedPrefix + ".blocker." + index, blockers.get(index));
        }
        List<String> diagnostics = diagnostics();
        fields.put(normalizedPrefix + ".diagnostic.count", Integer.toString(diagnostics.size()));
        for (int index = 0; index < diagnostics.size(); index++) {
            fields.put(normalizedPrefix + ".diagnostic." + index, diagnostics.get(index));
        }
        fields.put("runtime.backend.executionAvailability.present", "true");
        fields.put("runtime.backend.executionAvailability.status", status());
        fields.put("runtime.backend.executionAvailability.summary", summary());
        fields.put("runtime.backend.executionAvailability.sharedRunner.available", Boolean.toString(sharedPipelineRunnerAvailable()));
        fields.put("runtime.backend.executionAvailability.blocker.count", Integer.toString(blockers.size()));
        fields.put("runtime.backend.executionAvailability.moduleFormats", executionSupport.moduleFormatKeys());
        fields.put("runtime.backend.executionAvailability.capabilities", executionSupport.capabilityKeys());
        fields.put("runtime.backend.target", backendTarget.name());
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Backend execution: ").append(status()).append('\n');
        builder.append("Backend: ").append(backendTarget).append('\n');
        builder.append("Provider: ").append(providerId).append(" (`").append(providerVersion).append("`)\n");
        builder.append("Summary: ").append(summary()).append('\n');
        if (!executionSupport.moduleFormats().isEmpty()) {
            builder.append("Module formats: ").append(executionSupport.moduleFormatKeys()).append('\n');
        }
        if (!executionSupport.capabilityVocabulary().isEmpty()) {
            builder.append("Capability vocabulary: ").append(executionSupport.capabilityKeys()).append('\n');
        }
        if (!blockers().isEmpty()) {
            builder.append('\n').append("Blockers:").append('\n');
            for (String blocker : blockers()) {
                builder.append("- ").append(blocker).append('\n');
            }
        }
        if (!diagnostics().isEmpty()) {
            builder.append('\n').append("Diagnostics:").append('\n');
            for (String diagnostic : diagnostics()) {
                builder.append("- ").append(diagnostic).append('\n');
            }
        }
        return builder.toString();
    }
}
