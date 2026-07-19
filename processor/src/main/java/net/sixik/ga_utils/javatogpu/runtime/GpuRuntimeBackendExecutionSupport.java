package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provider-level declaration of which backend pipeline stages are available.
 *
 * <p>This is intentionally metadata-only. It lets catalogs, diagnostics, and future backend selection see whether a
 * backend is discovery-only, lowering-only, or able to compile/prepare/invoke kernels without forcing native runtime
 * initialization during provider inspection.</p>
 */
public record GpuRuntimeBackendExecutionSupport(
        GpuBackendTarget backendTarget,
        String supportId,
        boolean productionExecution,
        Set<GpuBackendPipelineStage> supportedStages,
        Set<GpuBackendModuleFormat> moduleFormats,
        Set<GpuRuntimeCapability> capabilityVocabulary,
        String diagnostic
) {

    public GpuRuntimeBackendExecutionSupport(
            GpuBackendTarget backendTarget,
            String supportId,
            boolean productionExecution,
            Set<GpuBackendPipelineStage> supportedStages,
            String diagnostic
    ) {
        this(backendTarget, supportId, productionExecution, supportedStages, Set.of(), Set.of(), diagnostic);
    }

    public GpuRuntimeBackendExecutionSupport {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        supportId = supportId == null || supportId.isBlank()
                ? "backend-execution-support:" + backendTarget.name().toLowerCase(java.util.Locale.ROOT)
                : supportId.trim();
        supportedStages = immutableStageSet(supportedStages);
        moduleFormats = immutableFormatSet(moduleFormats);
        capabilityVocabulary = immutableCapabilitySet(capabilityVocabulary);
        diagnostic = diagnostic == null ? "" : diagnostic.trim();
    }

    public static GpuRuntimeBackendExecutionSupport discoveryOnly(
            GpuBackendTarget backendTarget,
            String supportId,
            String diagnostic
    ) {
        return new GpuRuntimeBackendExecutionSupport(
                backendTarget,
                supportId,
                false,
                EnumSet.of(GpuBackendPipelineStage.DISCOVER),
                Set.of(),
                Set.of(),
                diagnostic
        );
    }

    public static GpuRuntimeBackendExecutionSupport discoveryOnly(
            GpuBackendTarget backendTarget,
            String supportId,
            Set<GpuBackendModuleFormat> moduleFormats,
            Set<GpuRuntimeCapability> capabilityVocabulary,
            String diagnostic
    ) {
        return new GpuRuntimeBackendExecutionSupport(
                backendTarget,
                supportId,
                false,
                EnumSet.of(GpuBackendPipelineStage.DISCOVER),
                moduleFormats,
                capabilityVocabulary,
                diagnostic
        );
    }

    public static GpuRuntimeBackendExecutionSupport loweringOnly(
            GpuBackendTarget backendTarget,
            String supportId,
            String diagnostic
    ) {
        return new GpuRuntimeBackendExecutionSupport(
                backendTarget,
                supportId,
                false,
                EnumSet.of(GpuBackendPipelineStage.DISCOVER, GpuBackendPipelineStage.LOWER),
                Set.of(),
                Set.of(),
                diagnostic
        );
    }

    public static GpuRuntimeBackendExecutionSupport loweringOnly(
            GpuBackendTarget backendTarget,
            String supportId,
            Set<GpuBackendModuleFormat> moduleFormats,
            Set<GpuRuntimeCapability> capabilityVocabulary,
            String diagnostic
    ) {
        return new GpuRuntimeBackendExecutionSupport(
                backendTarget,
                supportId,
                false,
                EnumSet.of(GpuBackendPipelineStage.DISCOVER, GpuBackendPipelineStage.LOWER),
                moduleFormats,
                capabilityVocabulary,
                diagnostic
        );
    }

    public static GpuRuntimeBackendExecutionSupport productionPipeline(
            GpuBackendTarget backendTarget,
            String supportId,
            String diagnostic
    ) {
        return new GpuRuntimeBackendExecutionSupport(
                backendTarget,
                supportId,
                true,
                EnumSet.of(
                        GpuBackendPipelineStage.DISCOVER,
                        GpuBackendPipelineStage.SELECT,
                        GpuBackendPipelineStage.LOWER,
                        GpuBackendPipelineStage.COMPILE,
                        GpuBackendPipelineStage.LOAD,
                        GpuBackendPipelineStage.PREPARE,
                        GpuBackendPipelineStage.BIND,
                        GpuBackendPipelineStage.INVOKE,
                        GpuBackendPipelineStage.READBACK,
                        GpuBackendPipelineStage.CLOSE
                ),
                Set.of(),
                Set.of(),
                diagnostic
        );
    }

    public static GpuRuntimeBackendExecutionSupport productionPipeline(
            GpuBackendTarget backendTarget,
            String supportId,
            Set<GpuBackendModuleFormat> moduleFormats,
            Set<GpuRuntimeCapability> capabilityVocabulary,
            String diagnostic
    ) {
        return new GpuRuntimeBackendExecutionSupport(
                backendTarget,
                supportId,
                true,
                EnumSet.of(
                        GpuBackendPipelineStage.DISCOVER,
                        GpuBackendPipelineStage.SELECT,
                        GpuBackendPipelineStage.LOWER,
                        GpuBackendPipelineStage.COMPILE,
                        GpuBackendPipelineStage.LOAD,
                        GpuBackendPipelineStage.PREPARE,
                        GpuBackendPipelineStage.BIND,
                        GpuBackendPipelineStage.INVOKE,
                        GpuBackendPipelineStage.READBACK,
                        GpuBackendPipelineStage.CLOSE
                ),
                moduleFormats,
                capabilityVocabulary,
                diagnostic
        );
    }

    public boolean supportsStage(GpuBackendPipelineStage stage) {
        return stage != null && supportedStages.contains(stage);
    }

    public boolean executionPipelineAvailable() {
        return supportsStage(GpuBackendPipelineStage.COMPILE)
                && supportsStage(GpuBackendPipelineStage.PREPARE)
                && supportsStage(GpuBackendPipelineStage.INVOKE);
    }

    public java.util.List<GpuBackendPipelineStage> missingExecutionStages() {
        java.util.ArrayList<GpuBackendPipelineStage> missing = new java.util.ArrayList<>();
        for (GpuBackendPipelineStage stage : java.util.List.of(
                GpuBackendPipelineStage.COMPILE,
                GpuBackendPipelineStage.PREPARE,
                GpuBackendPipelineStage.INVOKE
        )) {
            if (!supportsStage(stage)) {
                missing.add(stage);
            }
        }
        return java.util.List.copyOf(missing);
    }

    public String supportedStageKeys() {
        return supportedStages.stream()
                .sorted(java.util.Comparator.comparingInt(GpuBackendPipelineStage::order))
                .map(GpuBackendPipelineStage::key)
                .collect(Collectors.joining(","));
    }

    public boolean declaresModuleFormat(GpuBackendModuleFormat moduleFormat) {
        return moduleFormat != null && moduleFormats.contains(moduleFormat);
    }

    public boolean declaresCapability(GpuRuntimeCapability capability) {
        return capability != null && capabilityVocabulary.contains(capability);
    }

    public String moduleFormatKeys() {
        return moduleFormats.stream()
                .sorted(Comparator.comparing(GpuBackendModuleFormat::key))
                .map(GpuBackendModuleFormat::key)
                .collect(Collectors.joining(","));
    }

    public String capabilityKeys() {
        return capabilityVocabulary.stream()
                .sorted(Comparator.comparing(GpuRuntimeCapability::key))
                .map(GpuRuntimeCapability::key)
                .collect(Collectors.joining(","));
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.backend.executionSupport"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".backendTarget", backendTarget.name());
        fields.put(normalizedPrefix + ".id", supportId);
        fields.put(normalizedPrefix + ".productionExecution", Boolean.toString(productionExecution));
        fields.put(normalizedPrefix + ".executionPipeline.available", Boolean.toString(executionPipelineAvailable()));
        fields.put(normalizedPrefix + ".supportedStages", supportedStageKeys());
        fields.put(normalizedPrefix + ".moduleFormat.count", Integer.toString(moduleFormats.size()));
        int moduleFormatIndex = 0;
        for (GpuBackendModuleFormat moduleFormat : moduleFormats.stream()
                .sorted(Comparator.comparing(GpuBackendModuleFormat::key))
                .toList()) {
            fields.put(normalizedPrefix + ".moduleFormat." + moduleFormatIndex, moduleFormat.key());
            fields.put(normalizedPrefix + ".moduleFormat." + moduleFormat.key(), "true");
            moduleFormatIndex++;
        }
        fields.put(normalizedPrefix + ".moduleFormats", moduleFormatKeys());
        fields.put(normalizedPrefix + ".capability.count", Integer.toString(capabilityVocabulary.size()));
        int capabilityIndex = 0;
        for (GpuRuntimeCapability capability : capabilityVocabulary.stream()
                .sorted(Comparator.comparing(GpuRuntimeCapability::key))
                .toList()) {
            fields.put(normalizedPrefix + ".capability." + capabilityIndex, capability.key());
            fields.put(normalizedPrefix + ".capability." + capability.key(), "true");
            capabilityIndex++;
        }
        fields.put(normalizedPrefix + ".capabilities", capabilityKeys());
        fields.put(normalizedPrefix + ".diagnostic", diagnostic);
        fields.put("runtime.backend.executionSupport.present", "true");
        fields.put("runtime.backend.executionSupport.id", supportId);
        fields.put("runtime.backend.executionSupport.productionExecution", Boolean.toString(productionExecution));
        fields.put("runtime.backend.executionPipeline.available", Boolean.toString(executionPipelineAvailable()));
        fields.put("runtime.backend.executionSupport.supportedStages", supportedStageKeys());
        fields.put("runtime.backend.executionSupport.moduleFormats", moduleFormatKeys());
        fields.put("runtime.backend.executionSupport.capabilities", capabilityKeys());
        fields.put("runtime.backend.executionSupport.moduleFormat.count", Integer.toString(moduleFormats.size()));
        fields.put("runtime.backend.executionSupport.capability.count", Integer.toString(capabilityVocabulary.size()));
        fields.put("runtime.backend.target", backendTarget.name());
        return Collections.unmodifiableMap(fields);
    }

    private static Set<GpuBackendPipelineStage> immutableStageSet(Set<GpuBackendPipelineStage> stages) {
        if (stages == null || stages.isEmpty()) {
            return Set.of();
        }
        EnumSet<GpuBackendPipelineStage> normalized = EnumSet.noneOf(GpuBackendPipelineStage.class);
        for (GpuBackendPipelineStage stage : stages) {
            normalized.add(Objects.requireNonNull(stage, "stage"));
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static Set<GpuBackendModuleFormat> immutableFormatSet(Set<GpuBackendModuleFormat> formats) {
        if (formats == null || formats.isEmpty()) {
            return Set.of();
        }
        EnumSet<GpuBackendModuleFormat> normalized = EnumSet.noneOf(GpuBackendModuleFormat.class);
        for (GpuBackendModuleFormat format : formats) {
            normalized.add(Objects.requireNonNull(format, "format"));
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static Set<GpuRuntimeCapability> immutableCapabilitySet(Set<GpuRuntimeCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return Set.of();
        }
        EnumSet<GpuRuntimeCapability> normalized = EnumSet.noneOf(GpuRuntimeCapability.class);
        for (GpuRuntimeCapability capability : capabilities) {
            normalized.add(Objects.requireNonNull(capability, "capability"));
        }
        return Collections.unmodifiableSet(normalized);
    }
}
