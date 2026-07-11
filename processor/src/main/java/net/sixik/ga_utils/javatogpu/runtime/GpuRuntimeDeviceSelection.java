package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Auditable result of deterministic device policy evaluation.
 */
public record GpuRuntimeDeviceSelection(
        Optional<GpuRuntimeDeviceProfile> selectedDevice,
        List<GpuRuntimeDeviceCandidateRanking> rankedCandidates,
        List<GpuRuntimeDevicePolicyDecision> policyDecisions,
        List<GpuExtensionExecutionReport> executionReports,
        boolean compileOptionsValid,
        boolean failedClosed,
        String firstBlocker,
        List<String> diagnostics
) {

    public GpuRuntimeDeviceSelection {
        selectedDevice = selectedDevice == null ? Optional.empty() : selectedDevice;
        rankedCandidates = rankedCandidates == null ? List.of() : List.copyOf(rankedCandidates);
        policyDecisions = policyDecisions == null ? List.of() : List.copyOf(policyDecisions);
        executionReports = executionReports == null ? List.of() : List.copyOf(executionReports);
        firstBlocker = firstBlocker == null || firstBlocker.isBlank() ? "none" : firstBlocker;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "deviceSelection" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".selected", Boolean.toString(selectedDevice.isPresent()));
        fields.put(normalizedPrefix + ".selectedDeviceKey", selectedDevice
                .map(GpuRuntimeDevicePolicyContext::deviceKey)
                .orElse("none"));
        fields.put(normalizedPrefix + ".candidate.count", Integer.toString(rankedCandidates.size()));
        fields.put(normalizedPrefix + ".policy.count", Integer.toString(policyDecisions.size()));
        fields.put(normalizedPrefix + ".execution.count", Integer.toString(executionReports.size()));
        fields.put(normalizedPrefix + ".compileOptionsValid", Boolean.toString(compileOptionsValid));
        fields.put(normalizedPrefix + ".failedClosed", Boolean.toString(failedClosed));
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
        selectedDevice.ifPresent(profile -> {
            fields.put(normalizedPrefix + ".selected.deviceId", profile.deviceId());
            fields.put(normalizedPrefix + ".selected.deviceLabel", profile.deviceLabel());
            fields.put(normalizedPrefix + ".selected.vendor", profile.vendor());
            fields.put(normalizedPrefix + ".selected.deviceClass", profile.deviceClass().name().toLowerCase(java.util.Locale.ROOT));
            fields.put(normalizedPrefix + ".selected.driverVersion", profile.driverVersion());
            fields.put(normalizedPrefix + ".selected.apiVersionText", profile.apiVersionText());
        });
        for (int index = 0; index < rankedCandidates.size(); index++) {
            GpuRuntimeDeviceCandidateRanking ranking = rankedCandidates.get(index);
            String candidatePrefix = normalizedPrefix + ".candidate." + index;
            fields.put(candidatePrefix + ".deviceKey", ranking.deviceKey());
            fields.put(candidatePrefix + ".deviceId", ranking.profile().deviceId());
            fields.put(candidatePrefix + ".vendor", ranking.profile().vendor());
            fields.put(candidatePrefix + ".deviceLabel", ranking.profile().deviceLabel());
            fields.put(candidatePrefix + ".deviceClass", ranking.profile().deviceClass().name().toLowerCase(java.util.Locale.ROOT));
            fields.put(candidatePrefix + ".globalMemoryBytes", Long.toString(ranking.profile().globalMemoryBytes()));
            fields.put(candidatePrefix + ".unifiedMemory", Boolean.toString(ranking.profile().unifiedMemory()));
            fields.put(candidatePrefix + ".baseScore", Integer.toString(ranking.baseScore()));
            fields.put(candidatePrefix + ".policyScoreAdjustment", Integer.toString(ranking.policyScoreAdjustment()));
            fields.put(candidatePrefix + ".totalScore", Integer.toString(ranking.totalScore()));
            fields.put(candidatePrefix + ".rejected", Boolean.toString(ranking.rejected()));
            fields.put(candidatePrefix + ".diagnostic.count", Integer.toString(ranking.diagnostics().size()));
            for (int diagnosticIndex = 0; diagnosticIndex < ranking.diagnostics().size(); diagnosticIndex++) {
                fields.put(candidatePrefix + ".diagnostic." + diagnosticIndex, ranking.diagnostics().get(diagnosticIndex));
            }
        }
        for (int index = 0; index < policyDecisions.size(); index++) {
            GpuRuntimeDevicePolicyDecision decision = policyDecisions.get(index);
            String policyPrefix = normalizedPrefix + ".policy." + index;
            fields.put(policyPrefix + ".policyId", decision.policyId());
            fields.put(policyPrefix + ".policyVersion", decision.policyVersion());
            fields.put(policyPrefix + ".compileOptionsValid", Boolean.toString(decision.compileOptionsValid()));
            appendIntegerMap(fields, policyPrefix + ".scoreAdjustment", decision.scoreAdjustments());
            appendValues(fields, policyPrefix + ".rejectedDevice", decision.rejectedDeviceKeys().stream().sorted().toList());
            appendStringMap(fields, policyPrefix + ".capabilityFact", decision.capabilityFacts());
            appendValues(fields, policyPrefix + ".vendorQuirk", decision.vendorQuirks());
            appendValues(fields, policyPrefix + ".compileOptionDiagnostic", decision.compileOptionDiagnostics());
            appendValues(fields, policyPrefix + ".diagnostic", decision.diagnostics());
        }
        for (int index = 0; index < executionReports.size(); index++) {
            fields.putAll(executionReports.get(index).artifactFields(normalizedPrefix + ".execution." + index));
        }
        appendValues(fields, normalizedPrefix + ".diagnostic", diagnostics);
        return Collections.unmodifiableMap(fields);
    }

    public GpuRuntimeDeviceSelection withAdditionalDiagnostics(List<String> additionalDiagnostics) {
        if (additionalDiagnostics == null || additionalDiagnostics.isEmpty()) {
            return this;
        }
        java.util.ArrayList<String> combined = new java.util.ArrayList<>(diagnostics);
        combined.addAll(additionalDiagnostics);
        return new GpuRuntimeDeviceSelection(
                selectedDevice,
                rankedCandidates,
                policyDecisions,
                executionReports,
                compileOptionsValid,
                failedClosed,
                firstBlocker,
                combined
        );
    }

    private static void appendIntegerMap(
            LinkedHashMap<String, String> fields,
            String prefix,
            Map<String, Integer> values
    ) {
        fields.put(prefix + ".count", Integer.toString(values.size()));
        List<Map.Entry<String, Integer>> entries = values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (int index = 0; index < entries.size(); index++) {
            Map.Entry<String, Integer> entry = entries.get(index);
            fields.put(prefix + "." + index + ".key", entry.getKey());
            fields.put(prefix + "." + index + ".value", Integer.toString(entry.getValue()));
        }
    }

    private static void appendStringMap(
            LinkedHashMap<String, String> fields,
            String prefix,
            Map<String, String> values
    ) {
        fields.put(prefix + ".count", Integer.toString(values.size()));
        List<Map.Entry<String, String>> entries = values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (int index = 0; index < entries.size(); index++) {
            Map.Entry<String, String> entry = entries.get(index);
            fields.put(prefix + "." + index + ".key", entry.getKey());
            fields.put(prefix + "." + index + ".value", entry.getValue());
        }
    }

    private static void appendValues(
            LinkedHashMap<String, String> fields,
            String prefix,
            List<String> values
    ) {
        fields.put(prefix + ".count", Integer.toString(values.size()));
        for (int index = 0; index < values.size(); index++) {
            fields.put(prefix + "." + index, values.get(index));
        }
    }
}
