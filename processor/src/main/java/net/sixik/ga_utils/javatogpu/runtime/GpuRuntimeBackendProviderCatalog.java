package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * User-facing catalog of backend providers and their execution readiness.
 */
public record GpuRuntimeBackendProviderCatalog(List<GpuRuntimeBackendProvider> providers) {

    public GpuRuntimeBackendProviderCatalog {
        Objects.requireNonNull(providers, "providers");
        providers = GpuRuntimeBackendProviders.orderedProviders(providers);
    }

    public static GpuRuntimeBackendProviderCatalog standard() {
        return new GpuRuntimeBackendProviderCatalog(GpuRuntimeBackendProviders.standard());
    }

    public static GpuRuntimeBackendProviderCatalog standardWithPlannedBackends() {
        return new GpuRuntimeBackendProviderCatalog(GpuRuntimeBackendProviders.standardWithPlannedBackends());
    }

    public static GpuRuntimeBackendProviderCatalog standardWithPlannedBackends(ClassLoader classLoader) {
        return new GpuRuntimeBackendProviderCatalog(GpuRuntimeBackendProviders.standardWithPlannedBackends(classLoader));
    }

    public static GpuRuntimeBackendProviderCatalog of(List<GpuRuntimeBackendProvider> providers) {
        return new GpuRuntimeBackendProviderCatalog(providers);
    }

    public Optional<GpuRuntimeBackendProvider> forProviderId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return Optional.empty();
        }
        String normalizedProviderId = providerId.trim();
        return providers.stream()
                .filter(provider -> provider.providerId().equals(normalizedProviderId))
                .findFirst();
    }

    public Optional<GpuRuntimeBackendProvider> forTarget(GpuBackendTarget backendTarget) {
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        return providers.stream()
                .filter(provider -> provider.backendTarget() == target)
                .findFirst();
    }

    public List<GpuRuntimeBackendExecutionAvailability> executionAvailabilities() {
        return providers.stream()
                .map(GpuRuntimeBackendProvider::executionAvailability)
                .toList();
    }

    public long sharedPipelineRunnerAvailableCount() {
        return executionAvailabilities().stream()
                .filter(GpuRuntimeBackendExecutionAvailability::sharedPipelineRunnerAvailable)
                .count();
    }

    public long executionUnavailableCount() {
        return executionAvailabilities().stream()
                .filter(availability -> !availability.sharedPipelineRunnerAvailable())
                .count();
    }

    public boolean anySharedPipelineRunnerAvailable() {
        return sharedPipelineRunnerAvailableCount() > 0;
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.backend.providerCatalog"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".provider.count", Integer.toString(providers.size()));
        fields.put(
                normalizedPrefix + ".sharedRunner.available.count",
                Long.toString(sharedPipelineRunnerAvailableCount())
        );
        fields.put(normalizedPrefix + ".executionUnavailable.count", Long.toString(executionUnavailableCount()));
        fields.put(
                normalizedPrefix + ".sharedRunner.anyAvailable",
                Boolean.toString(anySharedPipelineRunnerAvailable())
        );
        for (int index = 0; index < providers.size(); index++) {
            GpuRuntimeBackendProvider provider = providers.get(index);
            String providerPrefix = normalizedPrefix + ".provider." + index;
            fields.put(providerPrefix + ".backendTarget", provider.backendTarget().name());
            fields.put(providerPrefix + ".providerId", provider.providerId());
            fields.put(providerPrefix + ".providerVersion", provider.providerVersion());
            fields.put(providerPrefix + ".providerOrder", Integer.toString(provider.providerOrder()));
            provider.artifactFields(providerPrefix).forEach((key, value) -> {
                if (key.startsWith(providerPrefix + ".")) {
                    fields.put(key, value);
                }
            });
        }
        fields.put("runtime.backend.providerCatalog.present", "true");
        fields.put("runtime.backend.providerCatalog.provider.count", Integer.toString(providers.size()));
        fields.put(
                "runtime.backend.providerCatalog.sharedRunner.available.count",
                Long.toString(sharedPipelineRunnerAvailableCount())
        );
        fields.put(
                "runtime.backend.providerCatalog.executionUnavailable.count",
                Long.toString(executionUnavailableCount())
        );
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Backend execution availability:").append(System.lineSeparator());
        for (GpuRuntimeBackendExecutionAvailability availability : executionAvailabilities()) {
            builder.append("- ")
                    .append(availability.backendTarget())
                    .append(": status=")
                    .append(availability.status())
                    .append(", sharedRunner=")
                    .append(availability.sharedPipelineRunnerAvailable())
                    .append(", provider=")
                    .append(availability.providerId())
                    .append(System.lineSeparator());
            builder.append("  summary: ").append(availability.summary()).append(System.lineSeparator());
            if (!availability.executionSupport().moduleFormats().isEmpty()) {
                builder.append("  moduleFormats: ")
                        .append(availability.executionSupport().moduleFormatKeys())
                        .append(System.lineSeparator());
            }
            if (!availability.executionSupport().capabilityVocabulary().isEmpty()) {
                builder.append("  capabilityVocabulary: ")
                        .append(availability.executionSupport().capabilityKeys())
                        .append(System.lineSeparator());
            }
            if (!availability.blockers().isEmpty()) {
                builder.append("  blockers: ")
                        .append(String.join(", ", availability.blockers()))
                        .append(System.lineSeparator());
            }
        }
        return builder.toString();
    }
}
