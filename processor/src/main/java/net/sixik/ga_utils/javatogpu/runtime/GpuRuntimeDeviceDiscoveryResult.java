package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * User-facing snapshot of backend device discovery plus deterministic device-policy selection.
 *
 * <p>The result is backend-neutral: OpenCL is the first adapter that can populate it, while future CUDA, Vulkan/SPIR-V,
 * Metal, or custom backends can expose the same shape without leaking native discovery APIs to applications.</p>
 */
public record GpuRuntimeDeviceDiscoveryResult(
        GpuBackendTarget backendTarget,
        String backendName,
        boolean discoveryAvailable,
        List<GpuRuntimeDeviceProfile> discoveredDevices,
        Optional<GpuRuntimeDeviceSelection> deviceSelection,
        String firstBlocker,
        List<String> diagnostics
) {

    public GpuRuntimeDeviceDiscoveryResult {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        backendName = backendName == null || backendName.isBlank() ? backendTarget.name() : backendName;
        discoveredDevices = discoveredDevices == null ? List.of() : List.copyOf(discoveredDevices);
        deviceSelection = deviceSelection == null ? Optional.empty() : deviceSelection;
        firstBlocker = firstBlocker == null || firstBlocker.isBlank() ? "none" : firstBlocker;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    /**
     * Creates an available discovery result from discovered device profiles and a policy selection.
     */
    public static GpuRuntimeDeviceDiscoveryResult available(
            GpuBackendTarget backendTarget,
            String backendName,
            List<GpuRuntimeDeviceProfile> discoveredDevices,
            GpuRuntimeDeviceSelection selection
    ) {
        List<GpuRuntimeDeviceProfile> devices = discoveredDevices == null ? List.of() : List.copyOf(discoveredDevices);
        GpuRuntimeDeviceSelection resolvedSelection = selection == null
                ? null
                : selection;
        ArrayList<String> diagnostics = new ArrayList<>();
        if (resolvedSelection != null) {
            diagnostics.addAll(resolvedSelection.diagnostics());
        }
        return new GpuRuntimeDeviceDiscoveryResult(
                backendTarget,
                backendName,
                true,
                devices,
                Optional.ofNullable(resolvedSelection),
                resolvedSelection == null ? "device-selection-not-run" : resolvedSelection.firstBlocker(),
                diagnostics
        );
    }

    /**
     * Creates a fail-soft discovery result when the native backend cannot be queried.
     */
    public static GpuRuntimeDeviceDiscoveryResult unavailable(
            GpuBackendTarget backendTarget,
            String backendName,
            String firstBlocker,
            Throwable failure
    ) {
        String diagnostic = failure == null || failure.getMessage() == null || failure.getMessage().isBlank()
                ? "device discovery failed before any native device profile was produced"
                : failure.getClass().getSimpleName() + ": " + failure.getMessage();
        return new GpuRuntimeDeviceDiscoveryResult(
                backendTarget,
                backendName,
                false,
                List.of(),
                Optional.empty(),
                firstBlocker == null || firstBlocker.isBlank() ? "device-discovery-failed" : firstBlocker,
                List.of(diagnostic)
        );
    }

    public Optional<GpuRuntimeDeviceProfile> selectedDevice() {
        return deviceSelection.flatMap(GpuRuntimeDeviceSelection::selectedDevice);
    }

    /**
     * Groups discovered devices by native platform identity when the backend exposes it.
     */
    public Map<String, List<GpuRuntimeDeviceProfile>> platformGroups() {
        LinkedHashMap<String, List<GpuRuntimeDeviceProfile>> grouped = new LinkedHashMap<>();
        for (GpuRuntimeDeviceProfile profile : discoveredDevices) {
            String key = platformKey(profile);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(profile);
        }
        LinkedHashMap<String, List<GpuRuntimeDeviceProfile>> immutable = new LinkedHashMap<>();
        grouped.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(immutable);
    }

    /**
     * Returns the runtime device self-test mode captured by the device-selection policy evidence.
     */
    public String selfTestMode() {
        return capabilityFact("runtime.deviceSelfTest.mode").orElse("not-recorded");
    }

    /**
     * Returns compact self-test status counts by device, derived from device-selection policy facts.
     */
    public Map<String, Integer> selfTestStatusCounts() {
        String mode = selfTestMode();
        if ("disabled".equals(mode)) {
            return discoveredDevices.isEmpty() ? Map.of() : Map.of("disabled", discoveredDevices.size());
        }
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (GpuRuntimeDeviceProfile profile : discoveredDevices) {
            String status = capabilityFact(GpuRuntimeDevicePolicyContext.deviceKey(profile) + ".selfTest.status")
                    .orElse("not-recorded");
            counts.merge(status, 1, Integer::sum);
        }
        return Collections.unmodifiableMap(counts);
    }

    /**
     * Returns deterministic key/value fields for logs, reports, or lifecycle journal events.
     */
    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "deviceDiscovery" : prefix.trim();
        LinkedHashMap<String, String> fields = GpuRuntimeLifecycleFields.deviceDiscoveryFields(this);
        fields.put(normalizedPrefix + ".backendTarget", backendTarget.name());
        fields.put(normalizedPrefix + ".backendName", backendName);
        fields.put(normalizedPrefix + ".available", Boolean.toString(discoveryAvailable));
        fields.put(normalizedPrefix + ".device.count", Integer.toString(discoveredDevices.size()));
        fields.put(normalizedPrefix + ".selection.present", Boolean.toString(deviceSelection.isPresent()));
        fields.put(normalizedPrefix + ".selectedDeviceKey", selectedDevice()
                .map(GpuRuntimeDevicePolicyContext::deviceKey)
                .orElse("none"));
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
        for (int index = 0; index < discoveredDevices.size(); index++) {
            GpuRuntimeDeviceProfile profile = discoveredDevices.get(index);
            String devicePrefix = normalizedPrefix + ".device." + index;
            fields.put(devicePrefix + ".deviceKey", GpuRuntimeDevicePolicyContext.deviceKey(profile));
            fields.put(devicePrefix + ".deviceId", profile.deviceId());
            fields.put(devicePrefix + ".deviceLabel", profile.deviceLabel());
            fields.put(devicePrefix + ".vendor", profile.vendor());
            fields.put(devicePrefix + ".deviceClass", profile.deviceClass().name().toLowerCase(Locale.ROOT));
            fields.put(devicePrefix + ".platformName", profile.platformName());
            fields.put(devicePrefix + ".platformVersion", profile.platformVersion());
            fields.put(devicePrefix + ".driverVersion", profile.driverVersion());
            fields.put(devicePrefix + ".apiVersionText", profile.apiVersionText());
            fields.put(devicePrefix + ".computeUnits", Long.toString(profile.computeUnits()));
            fields.put(devicePrefix + ".globalMemoryBytes", Long.toString(profile.globalMemoryBytes()));
            fields.put(devicePrefix + ".maxWorkGroupSize", Long.toString(profile.maxWorkGroupSize()));
            fields.put(devicePrefix + ".capability.count", Integer.toString(profile.runtimeCapabilities().size()));
            int capabilityIndex = 0;
            for (GpuRuntimeCapability capability : profile.runtimeCapabilities()) {
                fields.put(devicePrefix + ".capability." + capabilityIndex, capability.key());
                fields.put(devicePrefix + ".capability." + capability.key(), "true");
                capabilityIndex++;
            }
            if (profile.backendTarget() == GpuBackendTarget.CUDA) {
                fields.put(devicePrefix + ".cuda.runtimeVersion", profile.cudaRuntimeVersion());
                fields.put(devicePrefix + ".cuda.computeCapability", profile.cudaComputeCapability());
            }
        }
        appendPlatformFields(fields, normalizedPrefix);
        appendSelfTestFields(fields, normalizedPrefix);
        deviceSelection.ifPresent(selection -> fields.putAll(selection.artifactFields(normalizedPrefix + ".selection")));
        fields.put(normalizedPrefix + ".diagnostic.count", Integer.toString(diagnostics.size()));
        for (int index = 0; index < diagnostics.size(); index++) {
            fields.put(normalizedPrefix + ".diagnostic." + index, diagnostics.get(index));
        }
        return Collections.unmodifiableMap(fields);
    }

    /**
     * Renders a compact explanation suitable for examples, CLIs, and troubleshooting output.
     */
    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Device discovery: ").append(discoveryAvailable ? "available" : "unavailable").append('\n');
        builder.append("Backend: ").append(backendName).append(" (`").append(backendTarget).append("`)").append('\n');
        builder.append("Discovered devices: ").append(discoveredDevices.size()).append('\n');
        selectedDevice().ifPresent(profile -> builder.append("Selected device: ")
                .append(profile.deviceLabel())
                .append(" (`")
                .append(GpuRuntimeDevicePolicyContext.deviceKey(profile))
                .append("`)")
                .append('\n'));
        if (!"none".equals(firstBlocker)) {
            builder.append("First blocker: ").append(firstBlocker).append('\n');
        }
        appendPlatformGroups(builder);
        appendSelfTestSummary(builder);
        appendRankedCandidates(builder);
        if (!diagnostics.isEmpty()) {
            builder.append('\n').append("Diagnostics:").append('\n');
            for (String diagnostic : diagnostics) {
                builder.append("- ").append(diagnostic).append('\n');
            }
        }
        return builder.toString();
    }

    private void appendRankedCandidates(StringBuilder builder) {
        if (deviceSelection.isPresent() && !deviceSelection.orElseThrow().rankedCandidates().isEmpty()) {
            builder.append('\n').append("Ranked devices:").append('\n');
            for (GpuRuntimeDeviceCandidateRanking ranking : deviceSelection.orElseThrow().rankedCandidates()) {
                GpuRuntimeDeviceProfile profile = ranking.profile();
                builder.append("- ")
                        .append(profile.deviceLabel())
                        .append(" (`")
                        .append(GpuRuntimeDevicePolicyContext.deviceKey(profile))
                        .append("`), vendor=")
                        .append(profile.vendor())
                        .append(", class=")
                        .append(profile.deviceClass().name().toLowerCase(Locale.ROOT))
                        .append(", platform=")
                        .append(platformLabel(profile))
                        .append(", score=")
                        .append(ranking.totalScore())
                        .append(", rejected=")
                        .append(ranking.rejected())
                        .append('\n');
            }
            return;
        }
        if (!discoveredDevices.isEmpty()) {
            builder.append('\n').append("Devices:").append('\n');
            for (GpuRuntimeDeviceProfile profile : discoveredDevices) {
                builder.append("- ")
                        .append(profile.deviceLabel())
                        .append(" (`")
                        .append(GpuRuntimeDevicePolicyContext.deviceKey(profile))
                        .append("`), vendor=")
                        .append(profile.vendor())
                        .append(", class=")
                        .append(profile.deviceClass().name().toLowerCase(Locale.ROOT))
                        .append(", platform=")
                        .append(platformLabel(profile))
                        .append('\n');
            }
        }
    }

    private void appendPlatformFields(LinkedHashMap<String, String> fields, String normalizedPrefix) {
        Map<String, List<GpuRuntimeDeviceProfile>> groups = platformGroups();
        fields.put(normalizedPrefix + ".platform.count", Integer.toString(groups.size()));
        int index = 0;
        for (List<GpuRuntimeDeviceProfile> devices : groups.values()) {
            GpuRuntimeDeviceProfile first = devices.get(0);
            String platformPrefix = normalizedPrefix + ".platform." + index;
            fields.put(platformPrefix + ".key", platformKey(first));
            fields.put(platformPrefix + ".name", first.platformName());
            fields.put(platformPrefix + ".version", first.platformVersion());
            fields.put(platformPrefix + ".device.count", Integer.toString(devices.size()));
            index++;
        }
    }

    private void appendSelfTestFields(LinkedHashMap<String, String> fields, String normalizedPrefix) {
        Map<String, Integer> counts = selfTestStatusCounts();
        fields.put(normalizedPrefix + ".selfTest.mode", selfTestMode());
        fields.put(normalizedPrefix + ".selfTest.status.count", Integer.toString(counts.size()));
        int index = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String statusPrefix = normalizedPrefix + ".selfTest.status." + index;
            fields.put(statusPrefix + ".name", entry.getKey());
            fields.put(statusPrefix + ".count", Integer.toString(entry.getValue()));
            index++;
        }
    }

    private void appendPlatformGroups(StringBuilder builder) {
        Map<String, List<GpuRuntimeDeviceProfile>> groups = platformGroups();
        if (groups.isEmpty()) {
            return;
        }
        builder.append('\n').append("Platforms:").append('\n');
        for (List<GpuRuntimeDeviceProfile> devices : groups.values()) {
            GpuRuntimeDeviceProfile first = devices.get(0);
            builder.append("- ")
                    .append(platformLabel(first))
                    .append(", devices=")
                    .append(devices.size())
                    .append('\n');
        }
    }

    private void appendSelfTestSummary(StringBuilder builder) {
        String mode = selfTestMode();
        Map<String, Integer> counts = selfTestStatusCounts();
        if ("not-recorded".equals(mode) && counts.isEmpty()) {
            return;
        }
        builder.append('\n').append("Self-tests: mode=").append(mode);
        if (!counts.isEmpty()) {
            builder.append(", ").append(summary(counts));
        }
        builder.append('\n');
    }

    private Optional<String> capabilityFact(String key) {
        if (deviceSelection.isEmpty()) {
            return Optional.empty();
        }
        return deviceSelection.orElseThrow().policyDecisions().stream()
                .map(GpuRuntimeDevicePolicyDecision::capabilityFacts)
                .filter(facts -> facts.containsKey(key))
                .map(facts -> facts.get(key))
                .findFirst();
    }

    private static String platformKey(GpuRuntimeDeviceProfile profile) {
        return profile.backendTarget().name()
                + ':'
                + sanitize(profile.platformName())
                + ':'
                + sanitize(profile.platformVersion());
    }

    private static String platformLabel(GpuRuntimeDeviceProfile profile) {
        return profile.platformName() + " (`" + profile.platformVersion() + "`)";
    }

    private static String summary(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + '=' + entry.getValue())
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }

    private static String sanitize(String value) {
        return value == null || value.isBlank()
                ? "unknown"
                : value.trim().replaceAll("\\s+", "_");
    }
}
