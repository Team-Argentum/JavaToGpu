package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodTestVectorMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Diagnostic artifact that makes {@code @GPUTest} metadata and cache-only probe evidence visible in validation reports.
 */
public final class GpuRuntimeMethodTestEvidenceArtifact {

    private GpuRuntimeMethodTestEvidenceArtifact() {
    }

    public static String from(GpuRuntimeCompileArtifactSnapshot snapshot) {
        if (snapshot == null) {
            return "status=not-recorded\nfirstBlocker=snapshot-missing\n";
        }
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", "recorded");
        fields.put("backendTarget", snapshot.backendModuleArtifact().backendTarget().name());
        fields.put("backendFormat", snapshot.backendModuleArtifact().format());
        fields.put("backendResource", snapshot.backendModuleArtifact().resource());
        appendArtifactStage(fields, "original", snapshot.originalIrGpuArtifact());
        appendArtifactStage(fields, "optimized", snapshot.optimizedIrGpuArtifact());
        appendActiveMetadata(fields, snapshot.optimizedIrGpuArtifact().or(snapshot::originalIrGpuArtifact));
        appendCacheEvidence(fields, snapshot.deviceSelection());
        fields.put("firstBlocker", firstBlocker(fields));
        fields.put("diagnostic.count", "1");
        fields.put("diagnostic.0", diagnostic(fields));
        return formatProperties(fields);
    }

    private static void appendArtifactStage(
            LinkedHashMap<String, String> fields,
            String prefix,
            Optional<IrGpuArtifact> artifact
    ) {
        if (artifact == null || artifact.isEmpty()) {
            fields.put(prefix + ".present", "false");
            fields.put(prefix + ".methodTestVector.count", "0");
            fields.put(prefix + ".entryTestVector.count", "0");
            fields.put(prefix + ".entrySelectionProbe.count", "0");
            return;
        }
        IrGpuArtifact value = artifact.orElseThrow();
        List<IrGpuMethodTestVectorMetadata> entryVectors = value.entryTestVectors();
        fields.put(prefix + ".present", "true");
        fields.put(prefix + ".entryMethod", value.module().entryMethod());
        fields.put(prefix + ".entryEmittedName", value.module().entryEmittedName());
        fields.put(prefix + ".methodTestVector.count", Integer.toString(value.methodTestVectors().size()));
        fields.put(prefix + ".entryTestVector.count", Integer.toString(entryVectors.size()));
        fields.put(prefix + ".entrySelectionProbe.count", Long.toString(entryVectors.stream()
                .filter(IrGpuMethodTestVectorMetadata::selectionProbe)
                .count()));
        for (int index = 0; index < entryVectors.size(); index++) {
            appendVector(fields, prefix + ".entryTestVector." + index, entryVectors.get(index));
        }
    }

    private static void appendActiveMetadata(
            LinkedHashMap<String, String> fields,
            Optional<IrGpuArtifact> artifact
    ) {
        List<IrGpuMethodTestVectorMetadata> vectors = artifact == null || artifact.isEmpty()
                ? List.of()
                : artifact.orElseThrow().entryTestVectors();
        long selectionProbeCount = vectors.stream().filter(IrGpuMethodTestVectorMetadata::selectionProbe).count();
        fields.put("metadata.status", vectors.isEmpty() ? "missing" : "recorded");
        fields.put("metadata.entryTestVector.count", Integer.toString(vectors.size()));
        fields.put("metadata.selectionProbe.count", Long.toString(selectionProbeCount));
    }

    private static void appendVector(
            LinkedHashMap<String, String> fields,
            String prefix,
            IrGpuMethodTestVectorMetadata vector
    ) {
        fields.put(prefix + ".methodName", vector.methodName());
        fields.put(prefix + ".emittedName", vector.emittedName());
        fields.put(prefix + ".testId", vector.testId());
        fields.put(prefix + ".selectionProbe", Boolean.toString(vector.selectionProbe()));
        fields.put(prefix + ".inputRef.count", Integer.toString(vector.inputRefs().size()));
        fields.put(prefix + ".expectedOutputRef.count", Integer.toString(vector.expectedOutputRefs().size()));
        fields.put(prefix + ".tolerance", vector.tolerance().isBlank() ? "none" : vector.tolerance());
        fields.put(prefix + ".tag.count", Integer.toString(vector.tags().size()));
        fields.put(prefix + ".source", vector.source());
    }

    private static void appendCacheEvidence(
            LinkedHashMap<String, String> fields,
            Optional<GpuRuntimeDeviceSelection> selection
    ) {
        Optional<GpuRuntimeDevicePolicyDecision> decision = selection == null || selection.isEmpty()
                ? Optional.empty()
                : selection.orElseThrow().policyDecisions().stream()
                .filter(value -> GpuRuntimeMethodTestGpuProbeEvidencePolicy.POLICY_ID.equals(value.policyId()))
                .findFirst();
        if (decision.isEmpty()) {
            fields.put("cacheEvidence.status", "not-recorded");
            fields.put("cacheEvidence.candidate.count", "0");
            fields.put("cacheEvidence.passed.count", "0");
            fields.put("cacheEvidence.failed.count", "0");
            fields.put("cacheEvidence.missing.count", "0");
            fields.put("cacheEvidence.blocked.count", "0");
            return;
        }

        Map<String, String> facts = decision.orElseThrow().capabilityFacts();
        fields.put("cacheEvidence.status", facts.getOrDefault("methodTestProbeEvidence.status", "unknown"));
        fields.put("cacheEvidence.mode", facts.getOrDefault("methodTestProbeEvidence.mode", "unknown"));
        fields.put("cacheEvidence.cache.persistent", facts.getOrDefault("methodTestProbeEvidence.cache.persistent", "false"));
        fields.put("cacheEvidence.cache.path", facts.getOrDefault("methodTestProbeEvidence.cache.path", "not-recorded"));
        List<String> deviceKeys = deviceKeys(facts);
        fields.put("cacheEvidence.candidate.count", Integer.toString(deviceKeys.size()));
        int passed = 0;
        int failed = 0;
        int missing = 0;
        int blocked = 0;
        for (int index = 0; index < deviceKeys.size(); index++) {
            String deviceKey = deviceKeys.get(index);
            String sourcePrefix = deviceKey + ".methodTestProbeEvidence";
            String targetPrefix = "cacheEvidence.candidate." + index;
            String status = facts.getOrDefault(sourcePrefix + ".status", "unknown");
            fields.put(targetPrefix + ".deviceKey", deviceKey);
            fields.put(targetPrefix + ".status", status);
            fields.put(targetPrefix + ".passed.count", facts.getOrDefault(sourcePrefix + ".passed.count", "0"));
            fields.put(targetPrefix + ".failed.count", facts.getOrDefault(sourcePrefix + ".failed.count", "0"));
            fields.put(targetPrefix + ".missing.count", facts.getOrDefault(sourcePrefix + ".missing.count", "0"));
            fields.put(targetPrefix + ".blocked.count", facts.getOrDefault(sourcePrefix + ".blocked.count", "0"));
            passed += parseInt(facts.get(sourcePrefix + ".passed.count"));
            failed += parseInt(facts.get(sourcePrefix + ".failed.count"));
            missing += parseInt(facts.get(sourcePrefix + ".missing.count"));
            blocked += parseInt(facts.get(sourcePrefix + ".blocked.count"));
        }
        fields.put("cacheEvidence.passed.count", Integer.toString(passed));
        fields.put("cacheEvidence.failed.count", Integer.toString(failed));
        fields.put("cacheEvidence.missing.count", Integer.toString(missing));
        fields.put("cacheEvidence.blocked.count", Integer.toString(blocked));
    }

    private static List<String> deviceKeys(Map<String, String> facts) {
        ArrayList<String> keys = new ArrayList<>();
        facts.keySet().stream()
                .filter(key -> key.endsWith(".methodTestProbeEvidence.status"))
                .sorted()
                .forEach(key -> keys.add(key.substring(0, key.length() - ".methodTestProbeEvidence.status".length())));
        return List.copyOf(keys);
    }

    private static String firstBlocker(Map<String, String> fields) {
        if ("missing".equals(fields.get("metadata.status"))) {
            return "method-test-metadata-missing";
        }
        if ("not-recorded".equals(fields.get("cacheEvidence.status"))) {
            return "method-test-cache-evidence-not-recorded";
        }
        return "none";
    }

    private static String diagnostic(Map<String, String> fields) {
        if ("none".equals(fields.get("firstBlocker"))) {
            return "method-test metadata and cache evidence were recorded for validation reporting";
        }
        if ("method-test-cache-evidence-not-recorded".equals(fields.get("firstBlocker"))) {
            return "method-test metadata was recorded, but cache-only probe evidence ranking did not participate";
        }
        return "method-test metadata was not available in the runtime compile snapshot";
    }

    private static int parseInt(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String formatProperties(Map<String, String> fields) {
        StringBuilder builder = new StringBuilder();
        fields.forEach((key, value) -> builder
                .append(key)
                .append('=')
                .append(safePropertyValue(value))
                .append('\n'));
        return builder.toString();
    }

    private static String safePropertyValue(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
    }
}
