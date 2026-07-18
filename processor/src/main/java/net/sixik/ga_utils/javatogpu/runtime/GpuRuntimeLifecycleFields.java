package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;
import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared lifecycle field vocabulary for backend-neutral runtime diagnostics.
 */
public final class GpuRuntimeLifecycleFields {

    private GpuRuntimeLifecycleFields() {
    }

    public static LinkedHashMap<String, String> compileRequestFields(GpuRuntimeCompileRequest compileRequest) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (compileRequest == null) {
            fields.put("runtime.backend.target", "UNKNOWN");
            fields.put("runtime.backend.name", "unknown");
            fields.put("runtime.compile.optimizationProfile", "off");
            return fields;
        }
        putKernelFields(fields, compileRequest.descriptor());
        putCompileOptionsFields(fields, compileRequest.options());
        putDeviceProfileFields(fields, compileRequest.deviceProfile());
        fields.put("runtime.irgpu.present", Boolean.toString(compileRequest.irGpuArtifact().isPresent()));
        fields.put("runtime.irgpu.identity", IrGpuArtifactIdentity.stableIdentity(compileRequest.irGpuArtifact()));
        return fields;
    }

    public static LinkedHashMap<String, String> descriptorCompileOptionsFields(
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileOptions compileOptions
    ) {
        LinkedHashMap<String, String> fields = descriptorFields(descriptor);
        fields.putAll(compileOptionsFields(compileOptions));
        return fields;
    }

    public static LinkedHashMap<String, String> descriptorFields(GpuKernelDescriptor descriptor) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putKernelFields(fields, descriptor);
        return fields;
    }

    public static LinkedHashMap<String, String> compileOptionsFields(GpuRuntimeCompileOptions compileOptions) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putCompileOptionsFields(fields, compileOptions);
        return fields;
    }

    public static LinkedHashMap<String, String> artifactSnapshotFields(
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (artifactSnapshot == null) {
            fields.put("runtime.artifact.snapshot.present", "false");
            return fields;
        }
        fields.put("runtime.artifact.snapshot.present", "true");
        fields.putAll(moduleArtifactFields(artifactSnapshot.backendModuleArtifact()));
        GpuRuntimeCompileProvenance provenance = artifactSnapshot.compileProvenance();
        fields.put("runtime.backend.target", provenance.backendTarget().name());
        fields.put("runtime.backend.name", provenance.backendName());
        fields.put("runtime.device.label", provenance.deviceLabel());
        fields.put("runtime.device.vendor", provenance.vendor());
        fields.put("runtime.device.driverVersion", provenance.driverVersion());
        fields.put("runtime.device.apiVersionText", provenance.apiVersionText());
        fields.put("runtime.compile.optimizationProfile", provenance.optimizationProfile());
        fields.put("runtime.compile.arg.count", Integer.toString(provenance.compileArgs().size()));
        fields.put("runtime.compile.deviceOverride", provenance.deviceOverride());
        fields.put("runtime.compile.devicePreference", provenance.devicePreference());
        fields.putAll(runtimeIrSelectionFields(artifactSnapshot.runtimeIrSelection()));
        fields.putAll(fallbackEvidenceFields(artifactSnapshot.fallbackEvidence()));
        fields.put("runtime.compile.log.present", Boolean.toString(!artifactSnapshot.compileLog().isBlank()));
        fields.put("runtime.binaryArtifact.count", Integer.toString(artifactSnapshot.binaryArtifacts().size()));
        fields.put("runtime.validationEvidence.count", Integer.toString(artifactSnapshot.runtimeValidationEvidence().size()));
        return fields;
    }

    public static LinkedHashMap<String, String> runtimeIrSelectionFields(GpuRuntimeIrSelection runtimeIrSelection) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (runtimeIrSelection == null) {
            fields.put("runtime.ir.selection.present", "false");
            fields.put("runtime.ir.selectedStage", "missing");
            fields.put("runtime.ir.fallbackDecision", GpuRuntimeCompileProvenance.NO_FALLBACK);
            return fields;
        }
        fields.put("runtime.ir.selection.present", "true");
        fields.put("runtime.ir.selectedStage", runtimeIrSelection.selectedStage());
        fields.put("runtime.ir.originalIdentity", runtimeIrSelection.originalIdentity());
        fields.put("runtime.ir.optimizedIdentity", runtimeIrSelection.optimizedIdentity());
        fields.put("runtime.ir.selectedIdentity", runtimeIrSelection.selectedIdentity());
        fields.put("runtime.ir.transformed", Boolean.toString(runtimeIrSelection.transformed()));
        fields.put("runtime.ir.optimizedRejected", Boolean.toString(runtimeIrSelection.optimizedRejected()));
        fields.put("runtime.ir.fallbackDecision", runtimeIrSelection.fallbackDecision());
        fields.put("runtime.ir.diagnostic", runtimeIrSelection.diagnostic());
        GpuProductionIrAcceptanceGate.Result productionIrGate = runtimeIrSelection.productionIrGate();
        fields.put("runtime.ir.productionGate.accepted", Boolean.toString(productionIrGate.accepted()));
        fields.put("runtime.ir.productionGate.status", productionIrGate.status());
        fields.put("runtime.ir.productionGate.decisionMode", productionIrGate.decisionMode());
        fields.put("runtime.ir.productionGate.diagnostic", productionIrGate.diagnostic());
        return fields;
    }

    public static LinkedHashMap<String, String> fallbackEvidenceFields(GpuRuntimeFallbackEvidence fallbackEvidence) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        GpuRuntimeFallbackEvidence fallback = fallbackEvidence == null
                ? GpuRuntimeFallbackEvidence.none()
                : fallbackEvidence;
        fields.put("runtime.fallback.decision", fallback.decision());
        fields.put("runtime.fallback.reason", fallback.reason());
        fields.put("runtime.fallback.originalIrSelected", Boolean.toString(fallback.originalIrSelected()));
        fields.put("runtime.fallback.optimizedIrRejected", Boolean.toString(fallback.optimizedIrRejected()));
        fields.put("runtime.fallback.diagnostic.count", Integer.toString(fallback.diagnostics().size()));
        for (int index = 0; index < fallback.diagnostics().size(); index++) {
            fields.put("runtime.fallback.diagnostic." + index, fallback.diagnostics().get(index));
        }
        return fields;
    }

    public static LinkedHashMap<String, String> backendSelectionFields(
            GpuRuntimeBackendSelectionExplanation backendSelection
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (backendSelection == null) {
            fields.put("runtime.backend.selection.present", "false");
            fields.put("runtime.backend.selection.matched", "false");
            fields.put("runtime.backend.target", "UNKNOWN");
            fields.put("runtime.backend.name", "unknown");
            putStatus(fields, "backend-selection-not-recorded");
            return fields;
        }
        fields.put("runtime.backend.selection.present", "true");
        fields.put("runtime.backend.selection.matched", Boolean.toString(backendSelection.matched()));
        fields.put("runtime.backend.target", backendSelection.selectedBackendTarget().name());
        fields.put("runtime.backend.name", backendSelection.selectedBackendName());
        fields.put("runtime.backend.selection.summary", backendSelection.summary());
        fields.put("runtime.backend.selection.failure.count", Integer.toString(backendSelection.failureReasons().size()));
        putStatus(fields, backendSelection.matched() ? "backend-selected" : "backend-not-selected");
        return fields;
    }

    public static LinkedHashMap<String, String> backendAdapterFields(GpuRuntimeBackendAdapter adapter) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (adapter == null) {
            fields.put("runtime.backend.adapter.present", "false");
            fields.put("runtime.backend.target", "UNKNOWN");
            fields.put("runtime.backend.name", "unknown");
            fields.put("runtime.backend.adapter.productionAdapter", "false");
            fields.put("runtime.backend.adapter.ownership", "UNKNOWN");
            fields.put("runtime.backend.adapter.diagnostic", "backend adapter was not recorded");
            putStatus(fields, "backend-adapter-not-recorded");
            return fields;
        }
        GpuRuntimeBackendCatalogEntry entry = adapter.catalogEntry();
        GpuBackendLowerer lowerer = adapter.lowerer();
        fields.put("runtime.backend.adapter.present", "true");
        fields.put("runtime.backend.target", adapter.backendTarget().name());
        fields.put("runtime.backend.name", adapter.backendName());
        fields.put("runtime.backend.adapter.productionAdapter", Boolean.toString(entry.productionAdapter()));
        fields.put("runtime.backend.adapter.ownership", entry.ownership().name());
        fields.put("runtime.backend.adapter.diagnostic", adapter.diagnostic());
        fields.put("runtime.backend.lowerer.id", lowerer.extensionId());
        fields.put("runtime.backend.lowerer.version", lowerer.lowererVersion());
        fields.put("runtime.backend.lowerer.target", lowerer.backendTarget().name());
        putStatus(fields, entry.productionAdapter() ? "production-adapter" : "non-production-adapter");
        return fields;
    }

    public static LinkedHashMap<String, String> deviceDiscoveryFields(
            GpuRuntimeDeviceDiscoveryResult deviceDiscovery
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (deviceDiscovery == null) {
            fields.put("runtime.device.discovery.present", "false");
            fields.put("runtime.device.selected", "false");
            return fields;
        }
        fields.put("runtime.device.discovery.present", "true");
        fields.put("runtime.backend.target", deviceDiscovery.backendTarget().name());
        fields.put("runtime.backend.name", deviceDiscovery.backendName());
        fields.put("runtime.device.discovery.available", Boolean.toString(deviceDiscovery.discoveryAvailable()));
        fields.put("runtime.device.discovery.device.count", Integer.toString(deviceDiscovery.discoveredDevices().size()));
        fields.put("runtime.device.discovery.selection.present", Boolean.toString(deviceDiscovery.deviceSelection().isPresent()));
        fields.put("runtime.device.discovery.selectedDeviceKey", deviceDiscovery.selectedDevice()
                .map(GpuRuntimeDevicePolicyContext::deviceKey)
                .orElse("none"));
        fields.put("runtime.device.discovery.firstBlocker", deviceDiscovery.firstBlocker());
        fields.put("runtime.device.selected", Boolean.toString(deviceDiscovery.selectedDevice().isPresent()));
        deviceDiscovery.selectedDevice().ifPresent(profile -> putDeviceProfileFields(fields, profile));
        return fields;
    }

    public static LinkedHashMap<String, String> backendDeviceSelectionFields(
            GpuRuntimeBackendDeviceSelectionExplanation selection
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (selection == null) {
            fields.putAll(backendSelectionFields(null));
            fields.putAll(deviceDiscoveryFields(null));
            fields.put("runtime.selection.status", "not-recorded");
            fields.put("runtime.selection.summary", "runtime backend/device selection was not recorded");
            return fields;
        }
        fields.putAll(backendSelectionFields(selection.backendSelection()));
        fields.put("runtime.selection.status", selection.status());
        fields.put("runtime.selection.summary", selection.summary());
        fields.put("runtime.device.discovery.catalog.present", Boolean.toString(!selection.deviceDiscoveryCatalog().emptyCatalog()));
        selection.deviceDiscoveryCatalog()
                .forBackendSelection(selection.backendSelection())
                .ifPresentOrElse(
                        discovery -> fields.putAll(deviceDiscoveryFields(discovery)),
                        () -> fields.putAll(deviceDiscoveryFields(null))
                );
        putStatus(fields, selection.status());
        return fields;
    }

    public static LinkedHashMap<String, String> moduleArtifactFields(GpuBackendModuleArtifact moduleArtifact) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (moduleArtifact == null) {
            fields.put("runtime.module.present", "false");
            return fields;
        }
        fields.put("runtime.module.present", "true");
        fields.put("runtime.module.backendTarget", moduleArtifact.backendTarget().name());
        fields.put("runtime.module.kind", moduleArtifact.kind());
        fields.put("runtime.module.format", moduleArtifact.format());
        fields.put("runtime.module.resource", normalize(moduleArtifact.resource(), "unknown"));
        fields.put("runtime.module.artifactVersion", moduleArtifact.artifactVersion());
        fields.put("runtime.module.lowererVersion", moduleArtifact.lowererVersion());
        fields.put("runtime.module.sourceOrigin", moduleArtifact.sourceOrigin());
        fields.put("runtime.module.runtimeLoadMode", moduleArtifact.runtimeLoadMode());
        fields.put("runtime.module.sourceAvailable", Boolean.toString(moduleArtifact.sourceAvailable()));
        fields.put("runtime.module.binaryAvailable", Boolean.toString(moduleArtifact.binaryAvailable()));
        return fields;
    }

    public static LinkedHashMap<String, String> backendSourceSelectionFields(
            GpuBackendModuleArtifact moduleArtifact,
            GpuBackendSourceSwitchingDecision sourceSwitchingDecision
    ) {
        LinkedHashMap<String, String> fields = moduleArtifactFields(moduleArtifact);
        if (sourceSwitchingDecision == null) {
            fields.put("runtime.backend.source.selection.present", "false");
            fields.put("runtime.backend.source.status", "not-recorded");
            fields.put("runtime.backend.source.decision", "not-recorded");
            putStatus(fields, "source-selection-not-recorded");
            return fields;
        }
        fields.put("runtime.backend.source.selection.present", "true");
        fields.put("runtime.backend.source.status", sourceSwitchingDecision.status());
        fields.put("runtime.backend.source.decision", sourceSwitchingDecision.decision());
        fields.put("runtime.backend.source.selection", sourceSwitchingDecision.sourceSelection());
        fields.put("runtime.backend.source.irgpuRequested", Boolean.toString(sourceSwitchingDecision.irGpuSourceRequested()));
        fields.put("runtime.backend.source.ready", Boolean.toString(sourceSwitchingDecision.sourceReady()));
        fields.put("runtime.backend.source.reconstructed", Boolean.toString(sourceSwitchingDecision.sourceReconstructed()));
        fields.put("runtime.backend.source.available", Boolean.toString(sourceSwitchingDecision.sourceAvailable()));
        fields.put("runtime.backend.source.parityChecked", Boolean.toString(sourceSwitchingDecision.sourceParityChecked()));
        fields.put("runtime.backend.source.parityMatched", Boolean.toString(sourceSwitchingDecision.sourceParityMatched()));
        fields.put("runtime.backend.source.promotionStatus", sourceSwitchingDecision.sourcePromotionStatus());
        fields.put("runtime.backend.source.promotionReviewReady", Boolean.toString(sourceSwitchingDecision.sourcePromotionReviewReady()));
        fields.put("runtime.backend.source.promotionFirstBlocker", sourceSwitchingDecision.sourcePromotionFirstBlocker());
        fields.put("runtime.backend.source.productionProfileRequested", Boolean.toString(sourceSwitchingDecision.productionProfileRequested()));
        fields.put("runtime.backend.source.productionSwitching", sourceSwitchingDecision.productionSourceSwitching());
        fields.put("runtime.backend.source.productionSwitchingEnabled", Boolean.toString(sourceSwitchingDecision.productionSourceSwitchingEnabled()));
        fields.put("runtime.backend.source.productionPromotionDecisionMode", sourceSwitchingDecision.productionPromotionDecisionMode());
        fields.put("runtime.backend.source.productionPromotionOperatorAccepted", Boolean.toString(sourceSwitchingDecision.productionPromotionOperatorAccepted()));
        fields.put("runtime.backend.source.runtimeLoadMode", sourceSwitchingDecision.runtimeLoadMode());
        fields.put("runtime.backend.source.diagnostic", sourceSwitchingDecision.diagnostic());
        putStatus(fields, sourceSwitchingDecision.status());
        return fields;
    }

    public static LinkedHashMap<String, String> executionConfigFields(GpuExecutionConfig executionConfig) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (executionConfig == null) {
            fields.put("runtime.work.present", "false");
            return fields;
        }
        fields.put("runtime.work.present", "true");
        fields.put("runtime.work.dimensions", Integer.toString(executionConfig.dimensions()));
        fields.put("runtime.work.globalShape", executionConfig.globalShape());
        fields.put("runtime.work.localShape", executionConfig.localShape());
        fields.put("runtime.work.globalX", Long.toString(executionConfig.globalX()));
        fields.put("runtime.work.globalY", Long.toString(executionConfig.globalY()));
        fields.put("runtime.work.globalZ", Long.toString(executionConfig.globalZ()));
        fields.put("runtime.work.localX", Long.toString(executionConfig.localX()));
        fields.put("runtime.work.localY", Long.toString(executionConfig.localY()));
        fields.put("runtime.work.localZ", Long.toString(executionConfig.localZ()));
        fields.put("runtime.work.globalItemCount", Long.toString(executionConfig.globalItemCount()));
        fields.put("runtime.work.localItemCount", Long.toString(executionConfig.localItemCount()));
        fields.put("runtime.work.explicitLocal", Boolean.toString(executionConfig.hasExplicitLocalSize()));
        return fields;
    }

    public static LinkedHashMap<String, String> backendRuntimeStateFields(
            String cacheMode,
            long compiledKernelCount,
            long nativeBufferCount,
            long invocationCount,
            long compileCount,
            long compileCacheHitCount,
            long sessionCreationCount,
            long deviceBufferCreationCount
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("runtime.backend.cache.mode", normalize(cacheMode, "unknown"));
        fields.put("runtime.backend.cache.compiledKernel.count", Long.toString(normalizeCounter(compiledKernelCount)));
        fields.put("runtime.backend.cache.compileHit.count", Long.toString(normalizeCounter(compileCacheHitCount)));
        fields.put("runtime.backend.compile.count", Long.toString(normalizeCounter(compileCount)));
        fields.put("runtime.backend.invocation.count", Long.toString(normalizeCounter(invocationCount)));
        fields.put("runtime.backend.session.creation.count", Long.toString(normalizeCounter(sessionCreationCount)));
        fields.put("runtime.backend.buffer.native.count", Long.toString(normalizeCounter(nativeBufferCount)));
        fields.put("runtime.backend.buffer.device.creation.count", Long.toString(normalizeCounter(deviceBufferCreationCount)));
        return fields;
    }

    public static void putStatus(LinkedHashMap<String, String> fields, String status) {
        fields.put("runtime.status", normalize(status, "unknown"));
    }

    public static void putCacheKey(LinkedHashMap<String, String> fields, String cacheKey) {
        if (cacheKey != null && !cacheKey.isBlank()) {
            fields.put("runtime.cache.key", cacheKey);
        }
    }

    public static void putFailureFields(LinkedHashMap<String, String> fields, RuntimeException failure) {
        if (failure == null) {
            return;
        }
        fields.put("runtime.failure.type", failure.getClass().getName());
        fields.put("runtime.failure.message", normalize(failure.getMessage(), ""));
    }

    public static void putAllMissing(LinkedHashMap<String, String> fields, Map<String, String> additions) {
        if (additions == null || additions.isEmpty()) {
            return;
        }
        additions.forEach(fields::putIfAbsent);
    }

    private static void putKernelFields(LinkedHashMap<String, String> fields, GpuKernelDescriptor descriptor) {
        if (descriptor == null) {
            fields.put("runtime.kernel.name", "unknown");
            fields.put("runtime.kernel.resource", "unknown");
            fields.put("runtime.kernel.irgpuResource", "unknown");
            return;
        }
        fields.put("runtime.kernel.name", normalize(descriptor.kernelName(), "unknown"));
        fields.put("runtime.kernel.resource", normalize(descriptor.kernelResource(), "unknown"));
        fields.put("runtime.kernel.irgpuResource", normalize(descriptor.irGpuResource(), "unknown"));
    }

    private static void putCompileOptionsFields(
            LinkedHashMap<String, String> fields,
            GpuRuntimeCompileOptions compileOptions
    ) {
        GpuRuntimeCompileOptions options = compileOptions == null
                ? GpuRuntimeCompileOptions.defaults(null)
                : compileOptions;
        fields.put("runtime.backend.target", options.backendTarget().name());
        fields.put("runtime.compile.optimizationProfile", options.optimizationProfile());
        fields.put("runtime.compile.arg.count", Integer.toString(options.compileArgs().size()));
        fields.put("runtime.compile.backendOption.flag.count", Integer.toString(options.backendOptions().flags().size()));
        fields.put("runtime.compile.backendOption.property.count", Integer.toString(
                options.backendOptions().stableProperties().size()
        ));
        fields.put("runtime.compile.deviceOverride", options.deviceOverride().describe());
        fields.put("runtime.compile.devicePreference", options.devicePreference().describe());
    }

    private static void putDeviceProfileFields(
            LinkedHashMap<String, String> fields,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        GpuRuntimeDeviceProfile profile = deviceProfile == null
                ? GpuRuntimeDeviceProfile.generic(null, "unknown")
                : deviceProfile;
        fields.put("runtime.backend.target", profile.backendTarget().name());
        fields.put("runtime.backend.name", profile.backendName());
        fields.put("runtime.device.id", profile.deviceId());
        fields.put("runtime.device.label", profile.deviceLabel());
        fields.put("runtime.device.vendor", profile.vendor());
        fields.put("runtime.device.class", profile.deviceClass().name());
        fields.put("runtime.device.driverVersion", profile.driverVersion());
        fields.put("runtime.device.apiVersionText", profile.apiVersionText());
        fields.put("runtime.device.platformName", profile.platformName());
        fields.put("runtime.device.platformVersion", profile.platformVersion());
        fields.put("runtime.device.computeUnits", Long.toString(profile.computeUnits()));
        fields.put("runtime.device.globalMemoryBytes", Long.toString(profile.globalMemoryBytes()));
        fields.put("runtime.device.localMemoryBytes", Long.toString(profile.localMemoryBytes()));
        fields.put("runtime.device.maxWorkGroupSize", Long.toString(profile.maxWorkGroupSize()));
        fields.put("runtime.device.preferredVectorWidthFloat", Long.toString(profile.preferredVectorWidthFloat()));
        fields.put("runtime.device.unifiedMemory", Boolean.toString(profile.unifiedMemory()));
        fields.put("runtime.device.supportsDoublePrecision", Boolean.toString(profile.supportsDoublePrecision()));
        fields.put("runtime.device.supportsImages", Boolean.toString(profile.supportsImages()));
        fields.put("runtime.device.supportsSubgroups", Boolean.toString(profile.supportsSubgroups()));
        if (profile.backendTarget() == GpuBackendTarget.CUDA) {
            fields.put("runtime.device.cuda.runtimeVersion", profile.cudaRuntimeVersion());
            fields.put("runtime.device.cuda.computeCapability", profile.cudaComputeCapability());
        }
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static long normalizeCounter(long value) {
        return value < 0L ? 0L : value;
    }
}
