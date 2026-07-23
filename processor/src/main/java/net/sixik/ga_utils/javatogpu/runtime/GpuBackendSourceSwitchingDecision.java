package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
        String sourcePromotionFirstBlocker,
        String productionSourceSwitching,
        boolean productionSourceSwitchingEnabled,
        String productionPromotionDecisionMode,
        boolean productionPromotionOperatorAccepted,
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
        sourcePromotionFirstBlocker = normalize(sourcePromotionFirstBlocker, "none");
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
        return evaluate(provenance, module, reconstruction, sourcePromotionGate, sourceSwitchingPolicy, null);
    }

    public static GpuBackendSourceSwitchingDecision evaluate(
            GpuRuntimeCompileProvenance provenance,
            GpuBackendModuleArtifact module,
            GpuBackendSourceReconstructionResult reconstruction,
            GpuBackendSourcePromotionGate sourcePromotionGate,
            GpuBackendSourceSwitchingPolicy sourceSwitchingPolicy,
            GpuProductionPromotionOperatorAcceptance.Result operatorAcceptance
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
        GpuProductionPromotionOperatorAcceptance.Result resolvedOperatorAcceptance = operatorAcceptance == null
                ? GpuProductionPromotionOperatorAcceptance.legacy(policy.productionPromotionOperatorAccepted())
                : operatorAcceptance;
        boolean productionPromotionOperatorAccepted = resolvedOperatorAcceptance.accepted();
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
        } else if (!productionSwitchingEnabled) {
            status = "blocked";
            decision = "reject-production-irgpu-source";
            diagnostic = "production-like profile requested IrGpu source but opencl.productionSourceSwitching is disabled";
        } else if (!resolvedPromotionGate.reviewReady()) {
            status = "blocked";
            decision = "reject-production-irgpu-source";
            diagnostic = "production-like profile requested IrGpu source but backend source promotion gate is not review-ready";
        } else if (!GpuProductionPromotionDecision.PRODUCTION_ENABLED.equals(productionPromotionDecisionMode)) {
            status = "blocked";
            decision = "reject-production-irgpu-source";
            diagnostic = "production-like profile requested IrGpu source but production promotion decision is not production-enabled";
        } else if (!productionPromotionOperatorAccepted) {
            status = "blocked";
            decision = "reject-production-irgpu-source";
            diagnostic = "production-like profile requested IrGpu source but production promotion was not explicitly accepted by the operator: "
                    + resolvedOperatorAcceptance.diagnostic();
        } else {
            status = "production-switch-enabled";
            decision = "compile-irgpu-source-production";
            diagnostic = "production source switching was explicitly enabled and operator-accepted for a production-like profile";
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
                firstSourcePromotionBlocker(resolvedPromotionGate),
                productionSourceSwitching,
                productionSwitchingEnabled,
                productionPromotionDecisionMode,
                productionPromotionOperatorAccepted,
                diagnostic
        );
    }

    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        artifactFields("").forEach((key, value) -> builder.append(key).append('=').append(value).append('\n'));
        return builder.toString();
    }

    public Map<String, String> artifactFields(String prefix) {
        String safePrefix = prefix == null ? "" : prefix.trim();
        String keyPrefix = safePrefix.isBlank() ? "" : safePrefix + ".";
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(keyPrefix + "status", status);
        fields.put(keyPrefix + "decision", decision);
        fields.put(keyPrefix + "backendTarget", backendTarget.name());
        fields.put(keyPrefix + "backendFormat", backendFormat);
        fields.put(keyPrefix + "backendResource", backendResource);
        fields.put(keyPrefix + "sourceOrigin", sourceOrigin);
        fields.put(keyPrefix + "runtimeLoadMode", runtimeLoadMode);
        fields.put(keyPrefix + "optimizationProfile", optimizationProfile);
        fields.put(keyPrefix + "productionProfileRequested", Boolean.toString(productionProfileRequested));
        fields.put(keyPrefix + "sourceSelection", sourceSelection);
        fields.put(keyPrefix + "irGpuSourceRequested", Boolean.toString(irGpuSourceRequested));
        fields.put(keyPrefix + "sourceReady", Boolean.toString(sourceReady));
        fields.put(keyPrefix + "sourceReconstructed", Boolean.toString(sourceReconstructed));
        fields.put(keyPrefix + "sourceAvailable", Boolean.toString(sourceAvailable));
        fields.put(keyPrefix + "sourceParityChecked", Boolean.toString(sourceParityChecked));
        fields.put(keyPrefix + "sourceParityMatched", Boolean.toString(sourceParityMatched));
        fields.put(keyPrefix + "sourcePromotionStatus", sourcePromotionStatus);
        fields.put(keyPrefix + "sourcePromotionReviewReady", Boolean.toString(sourcePromotionReviewReady));
        fields.put(keyPrefix + "sourcePromotionFirstBlocker", sourcePromotionFirstBlocker);
        fields.put(keyPrefix + "productionSourceSwitching", productionSourceSwitching);
        fields.put(keyPrefix + "productionSourceSwitchingEnabled", Boolean.toString(productionSourceSwitchingEnabled));
        fields.put(keyPrefix + "productionPromotionDecisionMode", productionPromotionDecisionMode);
        fields.put(keyPrefix + "productionPromotionOperatorAccepted", Boolean.toString(productionPromotionOperatorAccepted));
        fields.put(keyPrefix + "diagnostic.count", "1");
        fields.put(keyPrefix + "diagnostic.0", diagnostic);
        putPortableRuntimeFields(fields, keyPrefix);
        return Collections.unmodifiableMap(fields);
    }

    private void putPortableRuntimeFields(Map<String, String> fields, String keyPrefix) {
        fields.put(keyPrefix + "runtime.backend.source.selection.present", "true");
        fields.put(keyPrefix + "runtime.backend.source.status", status);
        fields.put(keyPrefix + "runtime.backend.source.decision", decision);
        fields.put(keyPrefix + "runtime.backend.source.selection", sourceSelection);
        fields.put(keyPrefix + "runtime.backend.source.irgpuRequested", Boolean.toString(irGpuSourceRequested));
        fields.put(keyPrefix + "runtime.backend.source.ready", Boolean.toString(sourceReady));
        fields.put(keyPrefix + "runtime.backend.source.reconstructed", Boolean.toString(sourceReconstructed));
        fields.put(keyPrefix + "runtime.backend.source.available", Boolean.toString(sourceAvailable));
        fields.put(keyPrefix + "runtime.backend.source.parityChecked", Boolean.toString(sourceParityChecked));
        fields.put(keyPrefix + "runtime.backend.source.parityMatched", Boolean.toString(sourceParityMatched));
        fields.put(keyPrefix + "runtime.backend.source.promotionStatus", sourcePromotionStatus);
        fields.put(keyPrefix + "runtime.backend.source.promotionReviewReady", Boolean.toString(sourcePromotionReviewReady));
        fields.put(keyPrefix + "runtime.backend.source.promotionFirstBlocker", sourcePromotionFirstBlocker);
        fields.put(keyPrefix + "runtime.backend.source.productionProfileRequested", Boolean.toString(productionProfileRequested));
        fields.put(keyPrefix + "runtime.backend.source.productionSwitching", productionSourceSwitching);
        fields.put(keyPrefix + "runtime.backend.source.productionSwitchingEnabled", Boolean.toString(productionSourceSwitchingEnabled));
        fields.put(keyPrefix + "runtime.backend.source.productionPromotionDecisionMode", productionPromotionDecisionMode);
        fields.put(keyPrefix + "runtime.backend.source.productionPromotionOperatorAccepted", Boolean.toString(productionPromotionOperatorAccepted));
        fields.put(keyPrefix + "runtime.backend.source.runtimeLoadMode", runtimeLoadMode);
        fields.put(keyPrefix + "runtime.backend.source.diagnostic", diagnostic);
        fields.put(keyPrefix + "runtime.status", status);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String firstSourcePromotionBlocker(GpuBackendSourcePromotionGate gate) {
        if (gate == null || gate.reviewReady()) {
            return "none";
        }
        return gate.diagnostics().isEmpty() ? "source-promotion-gate-not-review-ready" : gate.diagnostics().get(0);
    }
}
