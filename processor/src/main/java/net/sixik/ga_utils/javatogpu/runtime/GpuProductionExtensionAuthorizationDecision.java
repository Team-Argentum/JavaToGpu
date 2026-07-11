package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionDescriptor;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Evidence-backed authorization for one production-affecting extension.
 */
public record GpuProductionExtensionAuthorizationDecision(
        String extensionId,
        String extensionVersion,
        String backendTarget,
        String deviceVendor,
        String deviceLabel,
        String optimizationProfile,
        String originalIrIdentity,
        String status,
        boolean authorized,
        boolean optimizerGateAccepted,
        boolean productionIrGateAccepted,
        boolean promotionContractValid,
        boolean productionMutationAllowed,
        boolean operatorAccepted,
        boolean runtimeEquivalencePassed,
        boolean proofArtifactAccepted,
        boolean rollbackSupported,
        boolean rollbackClean,
        List<String> blockers
) {

    public GpuProductionExtensionAuthorizationDecision {
        extensionId = normalize(extensionId, "extension:unknown");
        extensionVersion = normalize(extensionVersion, "unknown");
        backendTarget = normalize(backendTarget, "UNKNOWN");
        deviceVendor = normalize(deviceVendor, "unknown");
        deviceLabel = normalize(deviceLabel, "unknown");
        optimizationProfile = normalize(optimizationProfile, "off");
        originalIrIdentity = normalize(originalIrIdentity, "irgpu:missing");
        status = normalize(status, authorized ? "authorized" : "blocked");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        if (authorized && !blockers.isEmpty()) {
            throw new IllegalArgumentException("Authorized production extension decision must not contain blockers");
        }
    }

    public static GpuProductionExtensionAuthorizationDecision evaluate(
            GpuExtensionDescriptor extension,
            GpuRuntimeCompileRequest request,
            GpuRuntimeIrOptimizationReport optimizationReport,
            GpuRuntimeEquivalenceEvidence runtimeEquivalenceEvidence,
            GpuRuntimeFallbackEvidence fallbackEvidence,
            GpuProductionPromotionDecision promotionDecision,
            boolean rollbackSupported
    ) {
        Objects.requireNonNull(extension, "extension");
        GpuRuntimeCompileRequest resolvedRequest = Objects.requireNonNull(request, "request");
        GpuRuntimeIrOptimizationReport resolvedReport = optimizationReport == null
                ? GpuRuntimeIrOptimizationReport.empty(resolvedRequest.irGpuArtifact())
                : optimizationReport;
        GpuRuntimeEquivalenceEvidence resolvedEquivalence = runtimeEquivalenceEvidence == null
                ? GpuRuntimeEquivalenceEvidence.notRun(resolvedRequest, "runtime equivalence evidence was not provided")
                : runtimeEquivalenceEvidence;
        GpuRuntimeFallbackEvidence resolvedFallback = fallbackEvidence == null
                ? GpuRuntimeFallbackEvidence.none()
                : fallbackEvidence;
        GpuProductionPromotionDecision resolvedPromotion = promotionDecision == null
                ? GpuProductionPromotionDecision.diagnosticOnly()
                : promotionDecision;
        GpuRuntimeProductionOptimizerGate optimizerGate = GpuRuntimeProductionOptimizerGate.evaluate(
                resolvedRequest,
                resolvedReport,
                resolvedEquivalence,
                resolvedFallback
        );
        boolean operatorAccepted = resolvedRequest.options().backendOptions().productionPromotionOperatorAccepted();
        GpuProductionIrAcceptanceGate.Result productionIrGate = GpuProductionIrAcceptanceGate.evaluate(
                resolvedRequest.options().backendTarget().name(),
                "production extension " + extension.id(),
                resolvedRequest.options().optimizationProfile(),
                optimizerGate.productionProfileRequested(),
                optimizerGate.accepted(),
                resolvedPromotion.mode(),
                operatorAccepted
        );
        boolean proofAccepted = hasAcceptedProofEvidence(resolvedReport);
        boolean rollbackClean = !resolvedReport.requiresRollback();
        ArrayList<String> blockers = new ArrayList<>();
        if (extension.permission() != GpuExtensionPermission.PRODUCTION_AFFECTING) {
            blockers.add("extension-permission-not-production-affecting");
        }
        if (extension.phase() != GpuExtensionPhase.RUNTIME_IR_OPTIMIZATION
                && extension.phase() != GpuExtensionPhase.PRODUCTION_PROMOTION) {
            blockers.add("extension-phase-not-production-authorizable");
        }
        if (!optimizerGate.productionProfileRequested()) {
            blockers.add("production-profile-not-requested");
        }
        if (!optimizerGate.accepted()) {
            blockers.add("production-optimizer-gate-blocked");
        }
        if (!resolvedEquivalence.executed() || !resolvedEquivalence.equivalent()) {
            blockers.add("runtime-equivalence-not-passed");
        }
        if (!proofAccepted) {
            blockers.add("accepted-proof-artifact-missing");
        }
        if (!rollbackSupported) {
            blockers.add("rollback-support-not-declared");
        }
        if (!rollbackClean) {
            blockers.add("optimizer-report-requires-rollback");
        }
        if (!resolvedPromotion.contractValid()) {
            blockers.add("promotion-contract-invalid");
        }
        if (!GpuProductionPromotionDecision.PRODUCTION_ENABLED.equals(resolvedPromotion.mode())) {
            blockers.add("promotion-decision-not-production-enabled");
        }
        if (!resolvedPromotion.productionMutationAllowed()) {
            blockers.add("promotion-mutation-not-allowed");
        }
        if (!operatorAccepted) {
            blockers.add("production-promotion-not-operator-accepted");
        }
        if (!productionIrGate.accepted()) {
            blockers.add("production-ir-acceptance-gate-blocked");
        }
        boolean authorized = blockers.isEmpty();
        return new GpuProductionExtensionAuthorizationDecision(
                extension.id(),
                extension.version(),
                resolvedRequest.options().backendTarget().name(),
                resolvedRequest.deviceProfile().vendor(),
                resolvedRequest.deviceProfile().deviceLabel(),
                resolvedRequest.options().optimizationProfile(),
                IrGpuArtifactIdentity.stableIdentity(resolvedRequest.irGpuArtifact()),
                authorized ? "authorized" : "blocked",
                authorized,
                optimizerGate.accepted(),
                productionIrGate.accepted(),
                resolvedPromotion.contractValid(),
                resolvedPromotion.productionMutationAllowed(),
                operatorAccepted,
                resolvedEquivalence.executed() && resolvedEquivalence.equivalent(),
                proofAccepted,
                rollbackSupported,
                rollbackClean,
                blockers
        );
    }

    public boolean matches(
            GpuExtensionDescriptor extension,
            GpuRuntimeCompileRequest request,
            Optional<net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact> artifact
    ) {
        if (!authorized || extension == null || request == null) {
            return false;
        }
        return extensionId.equals(extension.id())
                && extensionVersion.equals(extension.version())
                && backendTarget.equals(request.options().backendTarget().name())
                && deviceVendor.equals(request.deviceProfile().vendor())
                && deviceLabel.equals(request.deviceProfile().deviceLabel())
                && optimizationProfile.equals(request.options().optimizationProfile())
                && originalIrIdentity.equals(IrGpuArtifactIdentity.stableIdentity(artifact));
    }

    public String diagnostic() {
        if (authorized) {
            return "production-affecting extension is authorized for the bound runtime compile context";
        }
        return blockers.isEmpty()
                ? "production-affecting extension authorization is blocked"
                : "production-affecting extension authorization is blocked by " + blockers.get(0);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "productionExtensionAuthorization" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".extensionId", extensionId);
        fields.put(normalizedPrefix + ".extensionVersion", extensionVersion);
        fields.put(normalizedPrefix + ".backendTarget", backendTarget);
        fields.put(normalizedPrefix + ".deviceVendor", deviceVendor);
        fields.put(normalizedPrefix + ".deviceLabel", deviceLabel);
        fields.put(normalizedPrefix + ".optimizationProfile", optimizationProfile);
        fields.put(normalizedPrefix + ".originalIrIdentity", originalIrIdentity);
        fields.put(normalizedPrefix + ".status", status);
        fields.put(normalizedPrefix + ".authorized", Boolean.toString(authorized));
        fields.put(normalizedPrefix + ".optimizerGateAccepted", Boolean.toString(optimizerGateAccepted));
        fields.put(normalizedPrefix + ".productionIrGateAccepted", Boolean.toString(productionIrGateAccepted));
        fields.put(normalizedPrefix + ".promotionContractValid", Boolean.toString(promotionContractValid));
        fields.put(normalizedPrefix + ".productionMutationAllowed", Boolean.toString(productionMutationAllowed));
        fields.put(normalizedPrefix + ".operatorAccepted", Boolean.toString(operatorAccepted));
        fields.put(normalizedPrefix + ".runtimeEquivalencePassed", Boolean.toString(runtimeEquivalencePassed));
        fields.put(normalizedPrefix + ".proofArtifactAccepted", Boolean.toString(proofArtifactAccepted));
        fields.put(normalizedPrefix + ".rollbackSupported", Boolean.toString(rollbackSupported));
        fields.put(normalizedPrefix + ".rollbackClean", Boolean.toString(rollbackClean));
        fields.put(normalizedPrefix + ".blocker.count", Integer.toString(blockers.size()));
        for (int index = 0; index < blockers.size(); index++) {
            fields.put(normalizedPrefix + ".blocker." + index, blockers.get(index));
        }
        fields.put(normalizedPrefix + ".diagnostic", diagnostic());
        return Collections.unmodifiableMap(fields);
    }

    private static boolean hasAcceptedProofEvidence(GpuRuntimeIrOptimizationReport report) {
        return report.passReports().stream()
                .map(GpuRuntimeIrOptimizationPassReport::proofArtifact)
                .filter(Objects::nonNull)
                .filter(proof -> !"none".equals(proof.source()) || !proof.fields().isEmpty())
                .map(GpuRuntimeIrOptimizationProofArtifact::verdict)
                .map(verdict -> verdict == null ? "" : verdict.toLowerCase(java.util.Locale.ROOT))
                .anyMatch(verdict -> verdict.contains("accepted") || verdict.contains("passed") || verdict.contains("ready"));
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
