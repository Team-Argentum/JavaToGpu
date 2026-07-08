package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

/**
 * Fail-closed runtime decision for selecting descriptor source versus reconstructed IrGpu source.
 *
 * <p>Backend-specific option keys are normalized into {@link GpuBackendSourceSwitchingPolicy} before this decision
 * runs, so future CUDA, Vulkan/SPIR-V, and Metal paths can reuse the same fail-closed logic without inheriting
 * OpenCL-specific property names.</p>
 */
public record GpuBackendSourceSwitchingDecision(
        String status,
        String decision,
        GpuBackendTarget backendTarget,
        String backendFormat,
        String backendResource,
        String sourceOrigin,
        String runtimeLoadMode,
        String optimizationProfile,
        boolean productionProfileRequested,
        String sourceSelection,
        boolean irGpuSourceRequested,
        boolean sourceReady,
        boolean sourceReconstructed,
        boolean sourceAvailable,
        boolean sourceParityChecked,
        boolean sourceParityMatched,
        String sourcePromotionStatus,
        boolean sourcePromotionReviewReady,
        String productionSourceSwitching,
        boolean productionSourceSwitchingEnabled,
        String productionPromotionDecisionMode,
        String diagnostic
) {

    public GpuBackendSourceSwitchingDecision {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        status = normalize(status, "blocked");
        decision = normalize(decision, "unknown");
        backendFormat = normalize(backendFormat, "unknown");
        backendResource = backendResource == null ? "" : backendResource;
        sourceOrigin = normalize(sourceOrigin, "unknown");
        runtimeLoadMode = normalize(runtimeLoadMode, "source-compile");
        optimizationProfile = normalize(optimizationProfile, "off");
        sourceSelection = normalize(sourceSelection, GpuBackendSourceSwitchingPolicy.SOURCE_SELECTION_DESCRIPTOR);
        sourcePromotionStatus = normalize(sourcePromotionStatus, "blocked");
        productionSourceSwitching = normalize(
                productionSourceSwitching,
                GpuBackendSourceSwitchingPolicy.PRODUCTION_SOURCE_SWITCHING_DISABLED
        );
        productionPromotionDecisionMode = normalize(
                productionPromotionDecisionMode,
                GpuProductionPromotionDecision.DIAGNOSTIC_ONLY
        );
        diagnostic = normalize(diagnostic, "backend source switching decision was not available");
    }

    public static GpuBackendSourceSwitchingDecision evaluate(
            GpuRuntimeCompileProvenance provenance,
            GpuBackendModuleArtifact module,
            GpuBackendSourceReconstructionResult reconstruction,
            GpuBackendSourcePromotionGate sourcePromotionGate
    ) {
        GpuRuntimeCompileProvenance resolvedProvenance = provenance == null
                ? GpuRuntimeCompileProvenance.unknown()
                : provenance;
        GpuBackendSourceSwitchingPolicy policy = GpuBackendSourceSwitchingPolicy.from(
                resolvedProvenance.backendOptions()
        );
        return evaluate(provenance, module, reconstruction, sourcePromotionGate, policy);
    }

    public static GpuBackendSourceSwitchingDecision evaluate(
            GpuRuntimeCompileProvenance provenance,
            GpuBackendModuleArtifact module,
            GpuBackendSourceReconstructionResult reconstruction,
            GpuBackendSourcePromotionGate sourcePromotionGate,
            GpuBackendSourceSwitchingPolicy sourceSwitchingPolicy
    ) {
        GpuRuntimeCompileProvenance resolvedProvenance = provenance == null
                ? GpuRuntimeCompileProvenance.unknown()
                : provenance;
        GpuBackendModuleArtifact resolvedModule = module == null ? GpuBackendModuleArtifact.unknown() : module;
        GpuBackendSourceSwitchingPolicy policy = sourceSwitchingPolicy == null
                ? GpuBackendSourceSwitchingPolicy.from(resolvedProvenance.backendOptions())
                : sourceSwitchingPolicy;
        GpuBackendSourceReconstructionResult resolvedReconstruction = reconstruction == null
                ? GpuBackendSourceReconstructionResult.notAttempted(
                resolvedModule.backendTarget(),
                resolvedModule.sourceOrigin(),
                "unknown",
                resolvedModule.runtimeLoadMode(),
                java.util.List.of("backend-source-reconstruction-missing"),
                java.util.List.of("backend source reconstruction result is not available")
        )
                : reconstruction;
        GpuBackendSourcePromotionGate resolvedPromotionGate = sourcePromotionGate == null
                ? GpuBackendSourcePromotionGate.evaluate(
                resolvedReconstruction,
                GpuRuntimeEquivalenceEvidence.notRun(null, "runtime equivalence was not executed"),
                GpuRuntimeFallbackEvidence.none()
        )
                : sourcePromotionGate;

        String sourceSelection = policy.sourceSelection();
        String productionSourceSwitching = policy.productionSourceSwitching();
        String productionPromotionDecisionMode = policy.productionPromotionDecisionMode();
        boolean irGpuSourceRequested = policy.irGpuSourceRequested();
        boolean productionProfileRequested = GpuRuntimeProductionProfiles.isProductionProfile(
                resolvedProvenance.optimizationProfile()
        );
        boolean productionSwitchingEnabled = policy.productionSourceSwitchingEnabled();

        String status;
        String decision;
        String diagnostic;
        if (!irGpuSourceRequested) {
            status = "descriptor-default";
            decision = "compile-descriptor-source";
            diagnostic = "descriptor source remains selected because opencl.sourceSelection did not request IrGpu source";
        } else if (!resolvedReconstruction.reconstructed() || !resolvedReconstruction.sourceAvailable()) {
            status = "blocked";
            decision = "reject-irgpu-source-unavailable";
            diagnostic = resolvedReconstruction.ready()
                    ? "IrGpu source was requested and reconstruction is ready, but assembled source is not available"
                    : "IrGpu source was requested but reconstructed source is not available";
        } else if (!resolvedPromotionGate.sourceParityChecked() || !resolvedPromotionGate.sourceParityMatched()) {
            status = "blocked";
            decision = "reject-irgpu-source-parity";
            diagnostic = "IrGpu source was requested but reconstructed source parity has not matched descriptor source";
        } else if (!productionProfileRequested) {
            status = "review-ready";
            decision = "compile-irgpu-source-review";
            diagnostic = "IrGpu source was explicitly selected for review or smoke validation";
        } else if (productionSwitchingEnabled) {
            status = "production-switch-enabled";
            decision = "compile-irgpu-source-production";
            diagnostic = "production source switching was explicitly enabled for a production-like profile";
        } else {
            status = "blocked";
            decision = "reject-production-irgpu-source";
            diagnostic = "production-like profile requested IrGpu source but opencl.productionSourceSwitching is disabled";
        }

        return new GpuBackendSourceSwitchingDecision(
                status,
                decision,
                resolvedModule.backendTarget(),
                resolvedModule.format(),
                resolvedModule.resource(),
                resolvedModule.sourceOrigin(),
                resolvedModule.runtimeLoadMode(),
                resolvedProvenance.optimizationProfile(),
                productionProfileRequested,
                sourceSelection,
                irGpuSourceRequested,
                resolvedReconstruction.ready(),
                resolvedReconstruction.reconstructed(),
                resolvedReconstruction.sourceAvailable(),
                resolvedPromotionGate.sourceParityChecked(),
                resolvedPromotionGate.sourceParityMatched(),
                resolvedPromotionGate.status(),
                resolvedPromotionGate.reviewReady(),
                productionSourceSwitching,
                productionSwitchingEnabled,
                productionPromotionDecisionMode,
                diagnostic
        );
    }

    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("status=").append(status).append('\n');
        builder.append("decision=").append(decision).append('\n');
        builder.append("backendTarget=").append(backendTarget).append('\n');
        builder.append("backendFormat=").append(backendFormat).append('\n');
        builder.append("backendResource=").append(backendResource).append('\n');
        builder.append("sourceOrigin=").append(sourceOrigin).append('\n');
        builder.append("runtimeLoadMode=").append(runtimeLoadMode).append('\n');
        builder.append("optimizationProfile=").append(optimizationProfile).append('\n');
        builder.append("productionProfileRequested=").append(productionProfileRequested).append('\n');
        builder.append("sourceSelection=").append(sourceSelection).append('\n');
        builder.append("irGpuSourceRequested=").append(irGpuSourceRequested).append('\n');
        builder.append("sourceReady=").append(sourceReady).append('\n');
        builder.append("sourceReconstructed=").append(sourceReconstructed).append('\n');
        builder.append("sourceAvailable=").append(sourceAvailable).append('\n');
        builder.append("sourceParityChecked=").append(sourceParityChecked).append('\n');
        builder.append("sourceParityMatched=").append(sourceParityMatched).append('\n');
        builder.append("sourcePromotionStatus=").append(sourcePromotionStatus).append('\n');
        builder.append("sourcePromotionReviewReady=").append(sourcePromotionReviewReady).append('\n');
        builder.append("productionSourceSwitching=").append(productionSourceSwitching).append('\n');
        builder.append("productionSourceSwitchingEnabled=").append(productionSourceSwitchingEnabled).append('\n');
        builder.append("productionPromotionDecisionMode=").append(productionPromotionDecisionMode).append('\n');
        builder.append("diagnostic.count=1\n");
        builder.append("diagnostic.0=").append(diagnostic).append('\n');
        return builder.toString();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
