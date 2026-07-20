package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipelineFactory;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleFormat;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendPipelineStage;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendExecutionAvailability;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendExecutionSupport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProvider;
import net.sixik.ga_utils.javatogpu.runtime.OpenClRuntimeBackendProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hardware-free OpenCL backend SPI contract snapshot.
 */
public record OpenClBackendSpiContractReport(
        String providerId,
        String providerVersion,
        GpuBackendTarget backendTarget,
        GpuRuntimeBackendExecutionSupport executionSupport,
        GpuRuntimeBackendExecutionAvailability executionAvailability,
        Optional<GpuBackendExecutionPipelineFactory<?, ?, ?>> executionPipelineFactory,
        Map<String, String> providerFields
) {

    public OpenClBackendSpiContractReport {
        providerId = providerId == null || providerId.isBlank() ? "unknown-provider" : providerId.trim();
        providerVersion = providerVersion == null || providerVersion.isBlank() ? "unknown" : providerVersion.trim();
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        executionSupport = executionSupport == null
                ? GpuRuntimeBackendExecutionSupport.discoveryOnly(
                        backendTarget,
                        providerId,
                        "OpenCL SPI contract report was created without execution support"
                )
                : executionSupport;
        executionAvailability = executionAvailability == null
                ? new GpuRuntimeBackendExecutionAvailability(
                        backendTarget,
                        providerId,
                        providerVersion,
                        executionSupport,
                        executionPipelineFactory != null && executionPipelineFactory.isPresent()
                )
                : executionAvailability;
        executionPipelineFactory = executionPipelineFactory == null ? Optional.empty() : executionPipelineFactory;
        providerFields = providerFields == null ? Map.of() : Map.copyOf(providerFields);
    }

    public static OpenClBackendSpiContractReport inspectBuiltInProvider() {
        return inspect(new OpenClRuntimeBackendProvider());
    }

    public static OpenClBackendSpiContractReport inspect(GpuRuntimeBackendProvider provider) {
        GpuRuntimeBackendProvider resolvedProvider = provider == null ? new OpenClRuntimeBackendProvider() : provider;
        return new OpenClBackendSpiContractReport(
                resolvedProvider.providerId(),
                resolvedProvider.providerVersion(),
                resolvedProvider.backendTarget(),
                resolvedProvider.executionSupport(),
                resolvedProvider.executionAvailability(),
                resolvedProvider.executionPipelineFactory(),
                resolvedProvider.artifactFields("opencl.spi.provider")
        );
    }

    public boolean ready() {
        return blockers().isEmpty();
    }

    public String status() {
        return ready() ? "ready" : "blocked";
    }

    public String firstBlocker() {
        return blockers().stream().findFirst().orElse("none");
    }

    public List<String> blockers() {
        ArrayList<String> blockers = new ArrayList<>();
        if (backendTarget != GpuBackendTarget.OPENCL) {
            blockers.add("backend-target-not-opencl:" + backendTarget);
        }
        if (!"backend-provider:opencl".equals(providerId)) {
            blockers.add("provider-id-not-opencl:" + providerId);
        }
        if (!executionSupport.productionExecution()) {
            blockers.add("opencl-production-execution-not-declared");
        }
        for (GpuBackendPipelineStage stage : List.of(
                GpuBackendPipelineStage.DISCOVER,
                GpuBackendPipelineStage.SELECT,
                GpuBackendPipelineStage.LOWER,
                GpuBackendPipelineStage.COMPILE,
                GpuBackendPipelineStage.PREPARE,
                GpuBackendPipelineStage.INVOKE,
                GpuBackendPipelineStage.READBACK,
                GpuBackendPipelineStage.CLOSE
        )) {
            if (!executionSupport.supportsStage(stage)) {
                blockers.add("opencl-stage-missing:" + stage.key());
            }
        }
        if (!executionSupport.declaresModuleFormat(GpuBackendModuleFormat.OPENCL_C)) {
            blockers.add("opencl-module-format-missing:opencl-c");
        }
        if (executionPipelineFactory.isEmpty()) {
            blockers.add("opencl-execution-pipeline-factory-missing");
        } else {
            GpuBackendExecutionPipelineFactory<?, ?, ?> factory = executionPipelineFactory.orElseThrow();
            if (factory.backendTarget() != GpuBackendTarget.OPENCL) {
                blockers.add("opencl-factory-target-mismatch:" + factory.backendTarget());
            }
            if (!"backend-execution-pipeline:opencl".equals(factory.factoryId())) {
                blockers.add("opencl-factory-id-mismatch:" + factory.factoryId());
            }
            if (!OpenClGpuRuntimeBackend.class.equals(factory.backendType())) {
                blockers.add("opencl-factory-backend-type-mismatch:" + factory.backendType().getName());
            }
        }
        if (!executionAvailability.sharedPipelineRunnerAvailable()) {
            blockers.add("opencl-shared-pipeline-runner-unavailable");
        }
        return List.copyOf(blockers);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.opencl.spiContract"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".providerId", providerId);
        fields.put(normalizedPrefix + ".providerVersion", providerVersion);
        fields.put(normalizedPrefix + ".backendTarget", backendTarget.name());
        fields.put(normalizedPrefix + ".productionExecution", Boolean.toString(executionSupport.productionExecution()));
        fields.put(normalizedPrefix + ".executionPipeline.available", Boolean.toString(executionSupport.executionPipelineAvailable()));
        fields.put(normalizedPrefix + ".executionPipeline.factory.present", Boolean.toString(executionPipelineFactory.isPresent()));
        fields.put(normalizedPrefix + ".executionAvailability.status", executionAvailability.status());
        fields.put(normalizedPrefix + ".moduleFormats", executionSupport.moduleFormatKeys());
        fields.put(normalizedPrefix + ".supportedStages", executionSupport.supportedStageKeys());
        fields.put(normalizedPrefix + ".capabilities", executionSupport.capabilityKeys());
        List<String> blockers = blockers();
        fields.put(normalizedPrefix + ".blocker.count", Integer.toString(blockers.size()));
        for (int index = 0; index < blockers.size(); index++) {
            fields.put(normalizedPrefix + ".blocker." + index, blockers.get(index));
        }
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        fields.putAll(prefixed(providerFields, normalizedPrefix + ".provider"));
        executionPipelineFactory.ifPresent(factory -> fields.putAll(
                factory.artifactFields(normalizedPrefix + ".executionPipelineFactory")
        ));
        fields.put("runtime.opencl.spiContract.present", "true");
        fields.put("runtime.opencl.spiContract.status", status());
        fields.put("runtime.opencl.spiContract.providerId", providerId);
        fields.put("runtime.opencl.spiContract.backendTarget", backendTarget.name());
        fields.put("runtime.opencl.spiContract.executionPipeline.available", Boolean.toString(executionSupport.executionPipelineAvailable()));
        fields.put("runtime.opencl.spiContract.executionPipeline.factory.present", Boolean.toString(executionPipelineFactory.isPresent()));
        fields.put("runtime.opencl.spiContract.firstBlocker", firstBlocker());
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("OpenCL backend SPI contract: ").append(status()).append('\n');
        builder.append("Provider: ").append(providerId).append(" (`").append(providerVersion).append("`)\n");
        builder.append("Backend: ").append(backendTarget).append('\n');
        builder.append("Stages: ").append(executionSupport.supportedStageKeys()).append('\n');
        builder.append("Module formats: ").append(executionSupport.moduleFormatKeys()).append('\n');
        builder.append("Pipeline factory: ").append(executionPipelineFactory.isPresent() ? "present" : "missing").append('\n');
        if (!blockers().isEmpty()) {
            builder.append('\n').append("Blockers:").append('\n');
            for (String blocker : blockers()) {
                builder.append("- ").append(blocker).append('\n');
            }
        }
        return builder.toString();
    }

    private static Map<String, String> prefixed(Map<String, String> source, String prefix) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        source.forEach((key, value) -> fields.put(prefix + "." + key, value));
        return fields;
    }
}
