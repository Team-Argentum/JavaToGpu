package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Multi-stage authorization view for backend hook execution.
 */
public record GpuBackendHookAuthorizationCatalog(
        GpuBackendTarget backendTarget,
        GpuBackendHookAuthorizationPolicy policy,
        List<GpuBackendHookAuthorizationReport> reports
) {

    public GpuBackendHookAuthorizationCatalog {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        policy = policy == null ? GpuBackendHookAuthorizationPolicy.readOnlyOnly() : policy;
        reports = reports == null ? List.of() : List.copyOf(reports);
    }

    public Optional<GpuBackendHookAuthorizationReport> reportForPhase(GpuExtensionPhase phase) {
        if (phase == null) {
            return Optional.empty();
        }
        return reports.stream()
                .filter(report -> report.phase() == phase)
                .findFirst();
    }

    public long decisionCount() {
        return reports.stream()
                .mapToLong(report -> report.decisions().size())
                .sum();
    }

    public long currentRegistryExecutableCount() {
        return reports.stream()
                .mapToLong(GpuBackendHookAuthorizationReport::currentRegistryExecutableCount)
                .sum();
    }

    public long blockedCount() {
        return reports.stream()
                .mapToLong(GpuBackendHookAuthorizationReport::blockedCount)
                .sum();
    }

    public long futureAuthorizedButDisabledCount() {
        return reports.stream()
                .mapToLong(GpuBackendHookAuthorizationReport::futureAuthorizedButDisabledCount)
                .sum();
    }

    public Optional<GpuBackendHookAuthorizationReport> firstBlockedReport() {
        return reports.stream()
                .filter(report -> report.blockedCount() > 0)
                .findFirst();
    }

    public String firstBlocker() {
        return firstBlockedReport()
                .map(report -> phaseKey(report.phase()) + ":" + report.firstBlocker())
                .orElse("none");
    }

    public String status() {
        if (decisionCount() == 0) {
            return "empty";
        }
        if (blockedCount() > 0) {
            return "blocked";
        }
        if (futureAuthorizedButDisabledCount() > 0) {
            return "future-authorized-execution-disabled";
        }
        return "read-only-ready";
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.backend.hookAuthorization"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".backendTarget", backendTarget.name());
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".policy.maximumPermission", policy.maximumPermission().name());
        fields.put(normalizedPrefix + ".stage.count", Integer.toString(reports.size()));
        fields.put(normalizedPrefix + ".decision.count", Long.toString(decisionCount()));
        fields.put(normalizedPrefix + ".currentRegistryExecutable.count", Long.toString(currentRegistryExecutableCount()));
        fields.put(normalizedPrefix + ".blocked.count", Long.toString(blockedCount()));
        fields.put(normalizedPrefix + ".futureAuthorizedButDisabled.count",
                Long.toString(futureAuthorizedButDisabledCount()));
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        for (GpuBackendHookAuthorizationReport report : reports) {
            String reportPrefix = normalizedPrefix + "." + phaseKey(report.phase());
            report.artifactFields(reportPrefix).forEach((key, value) -> {
                if (key.startsWith(reportPrefix + ".")) {
                    fields.put(key, value);
                }
            });
        }
        fields.put("runtime.backend.hookAuthorization.present", "true");
        fields.put("runtime.backend.hookAuthorization.status", status());
        fields.put("runtime.backend.hookAuthorization.backendTarget", backendTarget.name());
        fields.put("runtime.backend.hookAuthorization.blocked.count", Long.toString(blockedCount()));
        fields.put("runtime.backend.hookAuthorization.firstBlocker", firstBlocker());
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Backend hook authorization catalog: ").append(status()).append('\n');
        builder.append("Backend: ").append(backendTarget).append('\n');
        builder.append("Policy: ").append(policy.summary()).append('\n');
        if (blockedCount() > 0) {
            builder.append("First blocker: ").append(firstBlocker()).append('\n');
        }
        if (!reports.isEmpty()) {
            builder.append('\n').append("Stages:").append('\n');
            for (GpuBackendHookAuthorizationReport report : reports) {
                builder.append("- ")
                        .append(phaseKey(report.phase()))
                        .append(": status=")
                        .append(report.status())
                        .append(", executable=")
                        .append(report.currentRegistryExecutableCount())
                        .append(", blocked=")
                        .append(report.blockedCount())
                        .append(", firstBlocker=")
                        .append(report.firstBlocker())
                        .append('\n');
            }
        }
        return builder.toString();
    }

    static String phaseKey(GpuExtensionPhase phase) {
        if (phase == null) {
            return "unknown";
        }
        return switch (phase) {
            case BACKEND_DISCOVERY -> "discovery";
            case BACKEND_LOWERING -> "lowering";
            case BACKEND_COMPILATION -> "compilation";
            case BACKEND_INVOCATION -> "invocation";
            case ARTIFACT_EMISSION -> "artifact";
            default -> phase.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        };
    }
}
