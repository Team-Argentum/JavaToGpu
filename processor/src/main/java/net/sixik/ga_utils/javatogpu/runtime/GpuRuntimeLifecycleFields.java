package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;
import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.LinkedHashMap;
import java.util.Locale;
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
            putRuntimeBackendField(fields, "target", "UNKNOWN");
            putRuntimeBackendField(fields, "name", "unknown");
            putRuntimeCompileField(fields, "optimizationProfile", "off");
            return fields;
        }
        putKernelFields(fields, compileRequest.descriptor());
        putCompileOptionsFields(fields, compileRequest.options());
        putDeviceProfileFields(fields, compileRequest.deviceProfile());
        putRuntimeIrGpuField(fields, "present", compileRequest.irGpuArtifact().isPresent());
        putRuntimeIrGpuField(fields, "identity", IrGpuArtifactIdentity.stableIdentity(compileRequest.irGpuArtifact()));
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
            putRuntimeArtifactSnapshotField(fields, "present", false);
            return fields;
        }
        putRuntimeArtifactSnapshotField(fields, "present", true);
        fields.putAll(moduleArtifactFields(artifactSnapshot.backendModuleArtifact()));
        GpuRuntimeCompileProvenance provenance = artifactSnapshot.compileProvenance();
        putRuntimeBackendField(fields, "target", provenance.backendTarget().name());
        putRuntimeBackendField(fields, "name", provenance.backendName());
        putRuntimeDeviceField(fields, "label", provenance.deviceLabel());
        putRuntimeDeviceField(fields, "vendor", provenance.vendor());
        putRuntimeDeviceField(fields, "driverVersion", provenance.driverVersion());
        putRuntimeDeviceField(fields, "apiVersionText", provenance.apiVersionText());
        putRuntimeCompileField(fields, "optimizationProfile", provenance.optimizationProfile());
        putRuntimeCompileField(fields, "arg.count", provenance.compileArgs().size());
        putRuntimeCompileField(fields, "deviceOverride", provenance.deviceOverride());
        putRuntimeCompileField(fields, "devicePreference", provenance.devicePreference());
        fields.putAll(runtimeIrSelectionFields(artifactSnapshot.runtimeIrSelection()));
        fields.putAll(fallbackEvidenceFields(artifactSnapshot.fallbackEvidence()));
        putRuntimeCompileField(fields, "log.present", !artifactSnapshot.compileLog().isBlank());
        putRuntimeBinaryArtifactField(fields, "count", artifactSnapshot.binaryArtifacts().size());
        putRuntimeValidationEvidenceField(fields, "count", artifactSnapshot.runtimeValidationEvidence().size());
        return fields;
    }

    public static LinkedHashMap<String, String> runtimeIrSelectionFields(GpuRuntimeIrSelection runtimeIrSelection) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (runtimeIrSelection == null) {
            putRuntimeIrSelectionField(fields, "present", false);
            putRuntimeIrField(fields, "selectedStage", "missing");
            putRuntimeIrField(fields, "fallbackDecision", GpuRuntimeCompileProvenance.NO_FALLBACK);
            return fields;
        }
        putRuntimeIrSelectionField(fields, "present", true);
        putRuntimeIrField(fields, "selectedStage", runtimeIrSelection.selectedStage());
        putRuntimeIrField(fields, "originalIdentity", runtimeIrSelection.originalIdentity());
        putRuntimeIrField(fields, "optimizedIdentity", runtimeIrSelection.optimizedIdentity());
        putRuntimeIrField(fields, "selectedIdentity", runtimeIrSelection.selectedIdentity());
        putRuntimeIrField(fields, "transformed", runtimeIrSelection.transformed());
        putRuntimeIrField(fields, "optimizedRejected", runtimeIrSelection.optimizedRejected());
        putRuntimeIrField(fields, "fallbackDecision", runtimeIrSelection.fallbackDecision());
        putRuntimeIrField(fields, "diagnostic", runtimeIrSelection.diagnostic());
        GpuProductionIrAcceptanceGate.Result productionIrGate = runtimeIrSelection.productionIrGate();
        putRuntimeIrProductionGateField(fields, "accepted", productionIrGate.accepted());
        putRuntimeIrProductionGateField(fields, "status", productionIrGate.status());
        putRuntimeIrProductionGateField(fields, "decisionMode", productionIrGate.decisionMode());
        putRuntimeIrProductionGateField(fields, "diagnostic", productionIrGate.diagnostic());
        boolean optimizedSelected = "optimized".equals(runtimeIrSelection.selectedStage());
        boolean productionMutationEnabled = productionIrGate.accepted()
                && optimizedSelected
                && runtimeIrSelection.transformed();
        putRuntimeIrProductionMutationField(fields, "status", productionMutationEnabled ? "enabled" : "disabled");
        putRuntimeIrProductionMutationField(fields, "enabled", productionMutationEnabled);
        putRuntimeIrProductionMutationField(fields, "productionGateStatus", productionIrGate.status());
        putRuntimeIrProductionMutationField(fields, "selectedStage", runtimeIrSelection.selectedStage());
        putRuntimeIrProductionMutationField(fields, "optimizedSelected", optimizedSelected);
        putRuntimeIrProductionMutationField(fields, "optimizedDiffersFromOriginal", runtimeIrSelection.transformed());
        putRuntimeIrProductionMutationField(fields, "optimizedIrRejected", runtimeIrSelection.optimizedRejected());
        putRuntimeIrProductionMutationField(fields, "fallbackDecision", runtimeIrSelection.fallbackDecision());
        return fields;
    }

    public static LinkedHashMap<String, String> fallbackEvidenceFields(GpuRuntimeFallbackEvidence fallbackEvidence) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        GpuRuntimeFallbackEvidence fallback = fallbackEvidence == null
                ? GpuRuntimeFallbackEvidence.none()
                : fallbackEvidence;
        putRuntimeFallbackField(fields, "decision", fallback.decision());
        putRuntimeFallbackField(fields, "reason", fallback.reason());
        putRuntimeFallbackField(fields, "originalIrSelected", fallback.originalIrSelected());
        putRuntimeFallbackField(fields, "optimizedIrRejected", fallback.optimizedIrRejected());
        putRuntimeFallbackField(fields, "diagnostic.count", fallback.diagnostics().size());
        for (int index = 0; index < fallback.diagnostics().size(); index++) {
            putRuntimeFallbackDiagnosticField(fields, Integer.toString(index), fallback.diagnostics().get(index));
        }
        return fields;
    }

    public static LinkedHashMap<String, String> backendSelectionFields(
            GpuRuntimeBackendSelectionExplanation backendSelection
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (backendSelection == null) {
            putRuntimeBackendSelectionField(fields, "present", false);
            putRuntimeBackendSelectionField(fields, "matched", false);
            putRuntimeBackendField(fields, "target", "UNKNOWN");
            putRuntimeBackendField(fields, "name", "unknown");
            putStatus(fields, "backend-selection-not-recorded");
            return fields;
        }
        putRuntimeBackendSelectionField(fields, "present", true);
        putRuntimeBackendSelectionField(fields, "matched", backendSelection.matched());
        putRuntimeBackendField(fields, "target", backendSelection.selectedBackendTarget().name());
        putRuntimeBackendField(fields, "name", backendSelection.selectedBackendName());
        putRuntimeBackendSelectionField(fields, "summary", backendSelection.summary());
        putRuntimeBackendSelectionField(fields, "failure.count", backendSelection.failureReasons().size());
        putStatus(fields, backendSelection.matched() ? "backend-selected" : "backend-not-selected");
        return fields;
    }

    public static LinkedHashMap<String, String> backendAdapterFields(GpuRuntimeBackendAdapter adapter) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (adapter == null) {
            putRuntimeBackendAdapterField(fields, "present", false);
            putRuntimeBackendField(fields, "target", "UNKNOWN");
            putRuntimeBackendField(fields, "name", "unknown");
            putRuntimeBackendAdapterField(fields, "productionAdapter", false);
            putRuntimeBackendAdapterField(fields, "ownership", "UNKNOWN");
            putRuntimeBackendAdapterField(fields, "diagnostic", "backend adapter was not recorded");
            putStatus(fields, "backend-adapter-not-recorded");
            return fields;
        }
        GpuRuntimeBackendCatalogEntry entry = adapter.catalogEntry();
        GpuBackendLowerer lowerer = adapter.lowerer();
        putRuntimeBackendAdapterField(fields, "present", true);
        putRuntimeBackendField(fields, "target", adapter.backendTarget().name());
        putRuntimeBackendField(fields, "name", adapter.backendName());
        putRuntimeBackendAdapterField(fields, "productionAdapter", entry.productionAdapter());
        putRuntimeBackendAdapterField(fields, "ownership", entry.ownership().name());
        putRuntimeBackendAdapterField(fields, "diagnostic", adapter.diagnostic());
        putRuntimeBackendLowererField(fields, "id", lowerer.extensionId());
        putRuntimeBackendLowererField(fields, "version", lowerer.lowererVersion());
        putRuntimeBackendLowererField(fields, "target", lowerer.backendTarget().name());
        putStatus(fields, entry.productionAdapter() ? "production-adapter" : "non-production-adapter");
        return fields;
    }

    public static LinkedHashMap<String, String> deviceDiscoveryFields(
            GpuRuntimeDeviceDiscoveryResult deviceDiscovery
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (deviceDiscovery == null) {
            putRuntimeDeviceDiscoveryField(fields, "present", false);
            putRuntimeDeviceField(fields, "selected", false);
            return fields;
        }
        putRuntimeDeviceDiscoveryField(fields, "present", true);
        putRuntimeBackendField(fields, "target", deviceDiscovery.backendTarget().name());
        putRuntimeBackendField(fields, "name", deviceDiscovery.backendName());
        putRuntimeDeviceDiscoveryField(fields, "available", deviceDiscovery.discoveryAvailable());
        putRuntimeDeviceDiscoveryField(fields, "device.count", deviceDiscovery.discoveredDevices().size());
        putRuntimeDeviceDiscoveryField(fields, "selection.present", deviceDiscovery.deviceSelection().isPresent());
        putRuntimeDeviceDiscoveryField(fields, "selectedDeviceKey", deviceDiscovery.selectedDevice()
                .map(GpuRuntimeDevicePolicyContext::deviceKey)
                .orElse("none"));
        putRuntimeDeviceDiscoveryField(fields, "firstBlocker", deviceDiscovery.firstBlocker());
        putRuntimeDeviceField(fields, "selected", deviceDiscovery.selectedDevice().isPresent());
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
            putRuntimeSelectionField(fields, "status", "not-recorded");
            putRuntimeSelectionField(fields, "summary", "runtime backend/device selection was not recorded");
            return fields;
        }
        fields.putAll(backendSelectionFields(selection.backendSelection()));
        putRuntimeSelectionField(fields, "status", selection.status());
        putRuntimeSelectionField(fields, "summary", selection.summary());
        putRuntimeDeviceDiscoveryCatalogField(fields, "present", !selection.deviceDiscoveryCatalog().emptyCatalog());
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
            putRuntimeModuleField(fields, "present", false);
            return fields;
        }
        putRuntimeModuleField(fields, "present", true);
        putRuntimeModuleField(fields, "backendTarget", moduleArtifact.backendTarget().name());
        putRuntimeModuleField(fields, "kind", moduleArtifact.kind());
        putRuntimeModuleField(fields, "format", moduleArtifact.format());
        putRuntimeModuleField(fields, "format.canonical", moduleArtifact.moduleFormat().key());
        putRuntimeModuleField(fields, "format.sourceLike", moduleArtifact.sourceLikeFormat());
        putRuntimeModuleField(fields, "format.binaryLike", moduleArtifact.binaryLikeFormat());
        putRuntimeModuleField(fields, "format.matchesBackendTarget", moduleArtifact.formatMatchesBackendTarget());
        putRuntimeModuleField(fields, "resource", normalize(moduleArtifact.resource(), "unknown"));
        putRuntimeModuleField(fields, "artifactVersion", moduleArtifact.artifactVersion());
        putRuntimeModuleField(fields, "lowererVersion", moduleArtifact.lowererVersion());
        putRuntimeModuleField(fields, "sourceOrigin", moduleArtifact.sourceOrigin());
        putRuntimeModuleField(fields, "runtimeLoadMode", moduleArtifact.runtimeLoadMode());
        putRuntimeModuleField(fields, "sourceAvailable", moduleArtifact.sourceAvailable());
        putRuntimeModuleField(fields, "binaryAvailable", moduleArtifact.binaryAvailable());
        return fields;
    }

    public static LinkedHashMap<String, String> backendSourceSelectionFields(
            GpuBackendModuleArtifact moduleArtifact,
            GpuBackendSourceSwitchingDecision sourceSwitchingDecision
    ) {
        LinkedHashMap<String, String> fields = moduleArtifactFields(moduleArtifact);
        if (sourceSwitchingDecision == null) {
            putRuntimeBackendSourceField(fields, "selection.present", false);
            putRuntimeBackendSourceField(fields, "status", "not-recorded");
            putRuntimeBackendSourceField(fields, "decision", "not-recorded");
            putStatus(fields, "source-selection-not-recorded");
            return fields;
        }
        putRuntimeBackendSourceField(fields, "selection.present", true);
        putRuntimeBackendSourceField(fields, "status", sourceSwitchingDecision.status());
        putRuntimeBackendSourceField(fields, "decision", sourceSwitchingDecision.decision());
        putRuntimeBackendSourceField(fields, "selection", sourceSwitchingDecision.sourceSelection());
        putRuntimeBackendSourceField(fields, "irgpuRequested", sourceSwitchingDecision.irGpuSourceRequested());
        putRuntimeBackendSourceField(fields, "ready", sourceSwitchingDecision.sourceReady());
        putRuntimeBackendSourceField(fields, "reconstructed", sourceSwitchingDecision.sourceReconstructed());
        putRuntimeBackendSourceField(fields, "available", sourceSwitchingDecision.sourceAvailable());
        putRuntimeBackendSourceField(fields, "parityChecked", sourceSwitchingDecision.sourceParityChecked());
        putRuntimeBackendSourceField(fields, "parityMatched", sourceSwitchingDecision.sourceParityMatched());
        putRuntimeBackendSourceField(fields, "promotionStatus", sourceSwitchingDecision.sourcePromotionStatus());
        putRuntimeBackendSourceField(fields, "promotionReviewReady", sourceSwitchingDecision.sourcePromotionReviewReady());
        putRuntimeBackendSourceField(fields, "promotionFirstBlocker", sourceSwitchingDecision.sourcePromotionFirstBlocker());
        putRuntimeBackendSourceField(fields, "productionProfileRequested", sourceSwitchingDecision.productionProfileRequested());
        putRuntimeBackendSourceField(fields, "productionSwitching", sourceSwitchingDecision.productionSourceSwitching());
        putRuntimeBackendSourceField(fields, "productionSwitchingEnabled", sourceSwitchingDecision.productionSourceSwitchingEnabled());
        putRuntimeBackendSourceField(fields, "productionPromotionDecisionMode", sourceSwitchingDecision.productionPromotionDecisionMode());
        putRuntimeBackendSourceField(fields, "productionPromotionOperatorAccepted", sourceSwitchingDecision.productionPromotionOperatorAccepted());
        putRuntimeBackendSourceField(fields, "runtimeLoadMode", sourceSwitchingDecision.runtimeLoadMode());
        putRuntimeBackendSourceField(fields, "diagnostic", sourceSwitchingDecision.diagnostic());
        putStatus(fields, sourceSwitchingDecision.status());
        return fields;
    }

    public static LinkedHashMap<String, String> executionConfigFields(GpuExecutionConfig executionConfig) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (executionConfig == null) {
            putRuntimeWorkField(fields, "present", false);
            return fields;
        }
        putRuntimeWorkField(fields, "present", true);
        putRuntimeWorkField(fields, "dimensions", executionConfig.dimensions());
        putRuntimeWorkField(fields, "globalShape", executionConfig.globalShape());
        putRuntimeWorkField(fields, "localShape", executionConfig.localShape());
        putRuntimeWorkField(fields, "globalX", executionConfig.globalX());
        putRuntimeWorkField(fields, "globalY", executionConfig.globalY());
        putRuntimeWorkField(fields, "globalZ", executionConfig.globalZ());
        putRuntimeWorkField(fields, "localX", executionConfig.localX());
        putRuntimeWorkField(fields, "localY", executionConfig.localY());
        putRuntimeWorkField(fields, "localZ", executionConfig.localZ());
        putRuntimeWorkField(fields, "globalItemCount", executionConfig.globalItemCount());
        putRuntimeWorkField(fields, "localItemCount", executionConfig.localItemCount());
        putRuntimeWorkField(fields, "explicitLocal", executionConfig.hasExplicitLocalSize());
        return fields;
    }

    public static LinkedHashMap<String, String> invocationBindingFields(
            GpuRuntimeInvocationBindingSummary bindingSummary
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (bindingSummary == null) {
            putRuntimeInvocationBindingField(fields, "present", false);
            return fields;
        }
        fields.putAll(bindingSummary.artifactFields("runtime.invocation.binding"));
        return fields;
    }

    public static LinkedHashMap<String, String> artifactDumpSummaryFields(
            GpuRuntimeArtifactDumpSummary dumpSummary
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (dumpSummary == null) {
            putRuntimeArtifactDumpField(fields, "present", false);
            return fields;
        }
        fields.putAll(dumpSummary.artifactFields("runtime.artifactDump"));
        return fields;
    }

    public static LinkedHashMap<String, String> backendCompilationSummaryFields(
            GpuRuntimeBackendCompilationSummary compilationSummary
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (compilationSummary == null) {
            putRuntimeCompilationField(fields, "present", false);
            return fields;
        }
        fields.putAll(compilationSummary.artifactFields("runtime.compilation"));
        return fields;
    }

    public static LinkedHashMap<String, String> backendRuntimeStateFields(
            GpuRuntimeBackendStateSummary stateSummary
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (stateSummary == null) {
            putRuntimeBackendStateField(fields, "present", false);
            return fields;
        }
        fields.putAll(stateSummary.artifactFields("runtime.backend.state"));
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
        return backendRuntimeStateFields(new GpuRuntimeBackendStateSummary(
                cacheMode,
                compiledKernelCount,
                nativeBufferCount,
                invocationCount,
                compileCount,
                compileCacheHitCount,
                sessionCreationCount,
                deviceBufferCreationCount
        ));
    }

    public static LinkedHashMap<String, String> runtimeStateEventFields(
            GpuRuntimeBackendStateSummary stateSummary,
            String status,
            RuntimeException failure
    ) {
        return runtimeStateEventFields(backendRuntimeStateFields(stateSummary), status, failure);
    }

    public static LinkedHashMap<String, String> runtimeStateEventFields(
            Map<String, String> backendRuntimeStateFields,
            String status,
            RuntimeException failure
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (backendRuntimeStateFields != null) {
            fields.putAll(backendRuntimeStateFields);
        }
        putStatus(fields, status);
        putFailureFields(fields, failure);
        return fields;
    }

    public static LinkedHashMap<String, String> backendCompilationFields(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            Map<String, String> backendRuntimeStateFields,
            String status,
            String cacheKey,
            RuntimeException failure
    ) {
        return backendCompilationFields(
                compileRequest,
                moduleArtifact,
                artifactSnapshot,
                backendRuntimeStateFields,
                null,
                status,
                cacheKey,
                failure
        );
    }

    public static LinkedHashMap<String, String> backendCompilationFields(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            Map<String, String> backendRuntimeStateFields,
            GpuRuntimeBackendCompilationSummary compilationSummary,
            String status,
            String cacheKey,
            RuntimeException failure
    ) {
        LinkedHashMap<String, String> fields = compileRequestFields(compileRequest);
        if (backendRuntimeStateFields != null) {
            fields.putAll(backendRuntimeStateFields);
        }
        fields.putAll(backendCompilationSummaryFields(compilationSummary));
        putStatus(fields, status);
        putCacheKey(fields, cacheKey);
        putAllMissing(fields, moduleArtifactFields(moduleArtifact));
        putAllMissing(fields, artifactSnapshotFields(artifactSnapshot));
        putFailureFields(fields, failure);
        return fields;
    }

    public static LinkedHashMap<String, String> invocationFields(
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            Map<String, String> backendRuntimeStateFields,
            GpuExecutionConfig executionConfig,
            String status,
            String cacheKey,
            RuntimeException failure
    ) {
        return invocationFields(
                artifactSnapshot,
                backendRuntimeStateFields,
                executionConfig,
                null,
                status,
                cacheKey,
                failure
        );
    }

    public static LinkedHashMap<String, String> invocationFields(
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            Map<String, String> backendRuntimeStateFields,
            GpuExecutionConfig executionConfig,
            GpuRuntimeInvocationBindingSummary bindingSummary,
            String status,
            String cacheKey,
            RuntimeException failure
    ) {
        LinkedHashMap<String, String> fields = artifactSnapshotFields(artifactSnapshot);
        if (backendRuntimeStateFields != null) {
            fields.putAll(backendRuntimeStateFields);
        }
        fields.putAll(executionConfigFields(executionConfig));
        fields.putAll(invocationBindingFields(bindingSummary));
        putStatus(fields, status);
        putCacheKey(fields, cacheKey);
        putFailureFields(fields, failure);
        return fields;
    }

    public static LinkedHashMap<String, String> artifactDumpFields(
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            String status,
            RuntimeException failure
    ) {
        return artifactDumpFields(artifactSnapshot, null, status, failure);
    }

    public static LinkedHashMap<String, String> artifactDumpFields(
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            GpuRuntimeArtifactDumpSummary dumpSummary,
            String status,
            RuntimeException failure
    ) {
        LinkedHashMap<String, String> fields = artifactSnapshotFields(artifactSnapshot);
        fields.putAll(artifactDumpSummaryFields(dumpSummary));
        putStatus(fields, status);
        putFailureFields(fields, failure);
        return fields;
    }

    public static void putStatus(LinkedHashMap<String, String> fields, String status) {
        putRuntimeField(fields, "runtime", "status", normalize(status, "unknown"));
    }

    public static void putCacheKey(LinkedHashMap<String, String> fields, String cacheKey) {
        if (cacheKey != null && !cacheKey.isBlank()) {
            putRuntimeField(fields, "runtime.cache", "key", cacheKey);
        }
    }

    public static void putFailureFields(LinkedHashMap<String, String> fields, RuntimeException failure) {
        if (failure == null) {
            return;
        }
        fields.putAll(failureFields(failure));
    }

    public static LinkedHashMap<String, String> failureFields(Throwable failure) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        if (failure == null) {
            return fields;
        }
        GpuRuntimeFailurePhase phase = failurePhase(failure);
        putRuntimeFailureField(fields, "present", true);
        putRuntimeFailureField(fields, "type", failure.getClass().getName());
        putRuntimeFailureField(fields, "simpleType", failure.getClass().getSimpleName());
        putRuntimeFailureField(fields, "message", normalize(failure.getMessage(), ""));
        putRuntimeFailureField(fields, "phase", phase.name());
        putRuntimeFailureField(fields, "category", failureCategory(phase));
        putRuntimeFailureField(fields, "code", failureCode(failure));
        putRuntimeFailureField(fields, "summary", failureSummary(failure));
        putRuntimeFailureField(fields, "catchable", failure instanceof GpuRuntimeException);
        putRuntimeFailureField(fields, "suppressed.count", failure.getSuppressed().length);
        Throwable cause = failure.getCause();
        if (cause != null) {
            putRuntimeFailureCauseField(fields, "type", cause.getClass().getName());
            putRuntimeFailureCauseField(fields, "simpleType", cause.getClass().getSimpleName());
            putRuntimeFailureCauseField(fields, "message", normalize(cause.getMessage(), ""));
        }
        if (failure instanceof GpuRuntimeException runtimeFailure) {
            putRuntimeFailureField(fields, "diagnostic.present", true);
            putRuntimeFailureField(fields, "help.count", runtimeFailure.helpMessages().size());
            for (int index = 0; index < runtimeFailure.helpMessages().size(); index++) {
                putRuntimeFailureHelpField(fields, Integer.toString(index), runtimeFailure.helpMessages().get(index));
            }
            fields.putAll(runtimeFailure.context().artifactFields("runtime.failure.context"));
        } else {
            putRuntimeFailureField(fields, "diagnostic.present", false);
            putRuntimeFailureField(fields, "help.count", 0);
        }
        return fields;
    }

    public static void putAllMissing(LinkedHashMap<String, String> fields, Map<String, String> additions) {
        if (additions == null || additions.isEmpty()) {
            return;
        }
        additions.forEach(fields::putIfAbsent);
    }

    private static void putRuntimeBackendField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.backend", key, value);
    }

    private static void putRuntimeBackendSelectionField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.backend.selection", key, value);
    }

    private static void putRuntimeBackendAdapterField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.backend.adapter", key, value);
    }

    private static void putRuntimeBackendLowererField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.backend.lowerer", key, value);
    }

    private static void putRuntimeDeviceField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.device", key, value);
    }

    private static void putRuntimeDeviceDiscoveryField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.device.discovery", key, value);
    }

    private static void putRuntimeDeviceDiscoveryCatalogField(
            LinkedHashMap<String, String> fields,
            String key,
            Object value
    ) {
        putRuntimeField(fields, "runtime.device.discovery.catalog", key, value);
    }

    private static void putRuntimeSelectionField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.selection", key, value);
    }

    private static void putRuntimeArtifactSnapshotField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.artifact.snapshot", key, value);
    }

    private static void putRuntimeArtifactDumpField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.artifactDump", key, value);
    }

    private static void putRuntimeIrGpuField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.irgpu", key, value);
    }

    private static void putRuntimeIrField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.ir", key, value);
    }

    private static void putRuntimeIrSelectionField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.ir.selection", key, value);
    }

    private static void putRuntimeIrProductionGateField(
            LinkedHashMap<String, String> fields,
            String key,
            Object value
    ) {
        putRuntimeField(fields, "runtime.ir.productionGate", key, value);
    }

    private static void putRuntimeIrProductionMutationField(
            LinkedHashMap<String, String> fields,
            String key,
            Object value
    ) {
        putRuntimeField(fields, "runtime.ir.productionMutation", key, value);
    }

    private static void putRuntimeFallbackField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.fallback", key, value);
    }

    private static void putRuntimeFallbackDiagnosticField(
            LinkedHashMap<String, String> fields,
            String key,
            Object value
    ) {
        putRuntimeField(fields, "runtime.fallback.diagnostic", key, value);
    }

    private static void putRuntimeBinaryArtifactField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.binaryArtifact", key, value);
    }

    private static void putRuntimeValidationEvidenceField(
            LinkedHashMap<String, String> fields,
            String key,
            Object value
    ) {
        putRuntimeField(fields, "runtime.validationEvidence", key, value);
    }

    private static void putRuntimeModuleField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.module", key, value);
    }

    private static void putRuntimeBackendSourceField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.backend.source", key, value);
    }

    private static void putRuntimeWorkField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.work", key, value);
    }

    private static void putRuntimeInvocationBindingField(
            LinkedHashMap<String, String> fields,
            String key,
            Object value
    ) {
        putRuntimeField(fields, "runtime.invocation.binding", key, value);
    }

    private static void putRuntimeBackendCacheField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.backend.cache", key, value);
    }

    private static void putRuntimeBackendStateField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.backend.state", key, value);
    }

    private static void putRuntimeBackendCompileField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.backend.compile", key, value);
    }

    private static void putRuntimeBackendInvocationField(
            LinkedHashMap<String, String> fields,
            String key,
            Object value
    ) {
        putRuntimeField(fields, "runtime.backend.invocation", key, value);
    }

    private static void putRuntimeBackendSessionField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.backend.session", key, value);
    }

    private static void putRuntimeBackendBufferField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.backend.buffer", key, value);
    }

    private static void putRuntimeFailureField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.failure", key, value);
    }

    private static void putRuntimeFailureCauseField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.failure.cause", key, value);
    }

    private static void putRuntimeFailureHelpField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.failure.help", key, value);
    }

    private static void putRuntimeKernelField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.kernel", key, value);
    }

    private static void putRuntimeCompileField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.compile", key, value);
    }

    private static void putRuntimeCompilationField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.compilation", key, value);
    }

    private static void putRuntimeCompileBackendOptionField(
            LinkedHashMap<String, String> fields,
            String key,
            Object value
    ) {
        putRuntimeField(fields, "runtime.compile.backendOption", key, value);
    }

    private static void putRuntimeDeviceCudaField(LinkedHashMap<String, String> fields, String key, Object value) {
        putRuntimeField(fields, "runtime.device.cuda", key, value);
    }

    private static void putRuntimeField(
            LinkedHashMap<String, String> fields,
            String portablePrefix,
            String key,
            Object value
    ) {
        GpuRuntimeArtifactProperties.putPortable(fields, portablePrefix, key, value);
    }

    private static void putKernelFields(LinkedHashMap<String, String> fields, GpuKernelDescriptor descriptor) {
        if (descriptor == null) {
            putRuntimeKernelField(fields, "name", "unknown");
            putRuntimeKernelField(fields, "resource", "unknown");
            putRuntimeKernelField(fields, "irgpuResource", "unknown");
            return;
        }
        putRuntimeKernelField(fields, "name", normalize(descriptor.kernelName(), "unknown"));
        putRuntimeKernelField(fields, "resource", normalize(descriptor.kernelResource(), "unknown"));
        putRuntimeKernelField(fields, "irgpuResource", normalize(descriptor.irGpuResource(), "unknown"));
    }

    private static void putCompileOptionsFields(
            LinkedHashMap<String, String> fields,
            GpuRuntimeCompileOptions compileOptions
    ) {
        GpuRuntimeCompileOptions options = compileOptions == null
                ? GpuRuntimeCompileOptions.defaults(null)
                : compileOptions;
        putRuntimeBackendField(fields, "target", options.backendTarget().name());
        putRuntimeCompileField(fields, "optimizationProfile", options.optimizationProfile());
        putRuntimeCompileField(fields, "arg.count", options.compileArgs().size());
        putRuntimeCompileBackendOptionField(fields, "flag.count", options.backendOptions().flags().size());
        putRuntimeCompileBackendOptionField(fields, "property.count", options.backendOptions().stableProperties().size());
        putRuntimeCompileField(fields, "deviceOverride", options.deviceOverride().describe());
        putRuntimeCompileField(fields, "devicePreference", options.devicePreference().describe());
    }

    private static void putDeviceProfileFields(
            LinkedHashMap<String, String> fields,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        GpuRuntimeDeviceProfile profile = deviceProfile == null
                ? GpuRuntimeDeviceProfile.generic(null, "unknown")
                : deviceProfile;
        putRuntimeBackendField(fields, "target", profile.backendTarget().name());
        putRuntimeBackendField(fields, "name", profile.backendName());
        putRuntimeDeviceField(fields, "id", profile.deviceId());
        putRuntimeDeviceField(fields, "label", profile.deviceLabel());
        putRuntimeDeviceField(fields, "vendor", profile.vendor());
        putRuntimeDeviceField(fields, "class", profile.deviceClass().name());
        putRuntimeDeviceField(fields, "driverVersion", profile.driverVersion());
        putRuntimeDeviceField(fields, "apiVersionText", profile.apiVersionText());
        putRuntimeDeviceField(fields, "compilerVersion", profile.compilerVersion());
        putRuntimeDeviceField(fields, "platformName", profile.platformName());
        putRuntimeDeviceField(fields, "platformVersion", profile.platformVersion());
        putRuntimeDeviceField(fields, "computeUnits", profile.computeUnits());
        putRuntimeDeviceField(fields, "globalMemoryBytes", profile.globalMemoryBytes());
        putRuntimeDeviceField(fields, "localMemoryBytes", profile.localMemoryBytes());
        putRuntimeDeviceField(fields, "maxWorkGroupSize", profile.maxWorkGroupSize());
        putRuntimeDeviceField(fields, "preferredVectorWidthFloat", profile.preferredVectorWidthFloat());
        putRuntimeDeviceField(fields, "unifiedMemory", profile.unifiedMemory());
        putRuntimeDeviceField(fields, "supportsDoublePrecision", profile.supportsDoublePrecision());
        putRuntimeDeviceField(fields, "supportsImages", profile.supportsImages());
        putRuntimeDeviceField(fields, "supportsImage3dWrites", profile.supportsImage3dWrites());
        putRuntimeDeviceField(fields, "supportsAtomics", profile.supportsAtomics());
        putRuntimeDeviceField(fields, "supportsSubgroups", profile.supportsSubgroups());
        putRuntimeDeviceField(fields, "capability.count", profile.runtimeCapabilities().size());
        int capabilityIndex = 0;
        for (GpuRuntimeCapability capability : profile.runtimeCapabilities()) {
            putRuntimeDeviceField(fields, "capability." + capabilityIndex, capability.key());
            putRuntimeDeviceField(fields, "capability." + capability.key(), true);
            capabilityIndex++;
        }
        if (profile.backendTarget() == GpuBackendTarget.CUDA) {
            putRuntimeDeviceCudaField(fields, "runtimeVersion", profile.cudaRuntimeVersion());
            putRuntimeDeviceCudaField(fields, "computeCapability", profile.cudaComputeCapability());
        }
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static GpuRuntimeFailurePhase failurePhase(Throwable failure) {
        if (failure instanceof GpuRuntimeException runtimeFailure) {
            return runtimeFailure.phase();
        }
        if (failure instanceof IllegalArgumentException) {
            return GpuRuntimeFailurePhase.COMPILE_OPTIONS;
        }
        if (failure instanceof UnsupportedOperationException) {
            return GpuRuntimeFailurePhase.BACKEND_INITIALIZATION;
        }
        return GpuRuntimeFailurePhase.RUNTIME_SETUP;
    }

    private static String failureCategory(GpuRuntimeFailurePhase phase) {
        GpuRuntimeFailurePhase resolvedPhase = phase == null ? GpuRuntimeFailurePhase.RUNTIME_SETUP : phase;
        return resolvedPhase.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String failureCode(Throwable failure) {
        if (failure instanceof GpuRuntimeException runtimeFailure) {
            return runtimeFailure.code();
        }
        return "JTG-RUNTIME-UNCLASSIFIED";
    }

    private static String failureSummary(Throwable failure) {
        if (failure instanceof GpuRuntimeException runtimeFailure) {
            return runtimeFailure.summary();
        }
        return normalize(failure.getMessage(), failure.getClass().getSimpleName());
    }

}
