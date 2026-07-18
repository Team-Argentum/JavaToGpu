package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionFailurePolicy;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Failure-isolated dispatcher for runtime logging services.
 */
public final class GpuRuntimeLogBus {

    private static final GpuRuntimeLogBus EMPTY = new GpuRuntimeLogBus(List.of());

    private final List<GpuRuntimeLogService> services;
    private final GpuExtensionRegistry extensionRegistry;

    private GpuRuntimeLogBus(Collection<? extends GpuRuntimeLogService> services) {
        List<GpuRuntimeLogService> rawServices = services == null ? List.of() : List.copyOf(services);
        this.extensionRegistry = GpuExtensionRegistry.of(rawServices);
        this.extensionRegistry.requirePipelineContract(
                "runtime log bus",
                GpuExtensionPhase.DIAGNOSTICS,
                GpuExtensionPermission.READ_ONLY,
                GpuExtensionCapability.RUNTIME_LOGGING_SERVICE
        );
        LinkedHashMap<String, GpuRuntimeLogService> servicesById = new LinkedHashMap<>();
        for (GpuRuntimeLogService service : rawServices) {
            servicesById.put(service.extensionId().trim(), service);
        }
        this.services = this.extensionRegistry.descriptors().stream()
                .map(descriptor -> servicesById.get(descriptor.id()))
                .toList();
    }

    public static GpuRuntimeLogBus empty() {
        return EMPTY;
    }

    public static GpuRuntimeLogBus of(Collection<? extends GpuRuntimeLogService> services) {
        if (services == null || services.isEmpty()) {
            return empty();
        }
        return new GpuRuntimeLogBus(services);
    }

    public static GpuRuntimeLogBus loadFromServiceLoader() {
        LinkedHashMap<String, GpuRuntimeLogService> services = new LinkedHashMap<>();
        ServiceLoader.load(GpuRuntimeLogService.class)
                .forEach(service -> addLoadedService(services, service));
        return of(services.values());
    }

    public static GpuRuntimeLogBus loadDefault() {
        String mode = System.getProperty(GpuRuntimeLogger.LOG_PROPERTY, "service-loader");
        List<String> tokens = modeTokens(mode);
        if (tokens.contains("off") || tokens.contains("none") || tokens.contains("false")) {
            return empty();
        }

        LinkedHashMap<String, GpuRuntimeLogService> services = new LinkedHashMap<>();
        ServiceLoader.load(GpuRuntimeLogService.class)
                .forEach(service -> addLoadedService(services, service));
        if (containsAny(tokens, "system-out", "stdout", "out")) {
            addLoadedService(services, GpuRuntimeSystemStreamLogService.systemOut());
        }
        if (containsAny(tokens, "system-err", "stderr", "err")) {
            addLoadedService(services, GpuRuntimeSystemStreamLogService.systemErr());
        }
        return of(services.values());
    }

    private static void addLoadedService(
            LinkedHashMap<String, GpuRuntimeLogService> services,
            GpuRuntimeLogService service
    ) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(service, "service");
        services.putIfAbsent(service.getClass().getName() + ":" + service.extensionId(), service);
    }

    public int serviceCount() {
        return services.size();
    }

    public List<GpuRuntimeLogService> services() {
        return services;
    }

    public Map<String, String> extensionArtifactFields(String prefix) {
        return extensionRegistry.artifactFields(prefix == null || prefix.isBlank()
                ? "runtimeLoggingExtension"
                : prefix.trim());
    }

    public GpuRuntimeLogDispatchReport publish(GpuRuntimeLogRecord record) {
        GpuRuntimeLogRecord value = Objects.requireNonNull(record, "record");
        ArrayList<GpuExtensionExecutionReport> reports = new ArrayList<>();
        for (GpuRuntimeLogService service : services) {
            try {
                service.log(value);
                reports.add(GpuExtensionExecutionReport.succeeded(service, "runtime log " + value.level()));
            } catch (RuntimeException failure) {
                reports.add(GpuExtensionExecutionReport.failed(
                        service,
                        "runtime log " + value.level(),
                        GpuExtensionFailurePolicy.CONTINUE,
                        failure
                ));
            }
        }
        return new GpuRuntimeLogDispatchReport(value, reports);
    }

    private static List<String> modeTokens(String value) {
        if (value == null || value.isBlank()) {
            return List.of("service-loader");
        }
        ArrayList<String> tokens = new ArrayList<>();
        for (String token : value.split("[,;\\s]+")) {
            if (!token.isBlank()) {
                tokens.add(token.trim().toLowerCase(Locale.ROOT));
            }
        }
        return tokens.isEmpty() ? List.of("service-loader") : List.copyOf(tokens);
    }

    private static boolean containsAny(List<String> tokens, String... values) {
        for (String value : values) {
            if (tokens.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
