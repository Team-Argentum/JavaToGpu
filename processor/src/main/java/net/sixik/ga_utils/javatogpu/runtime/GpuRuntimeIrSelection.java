package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;

import java.util.Optional;

/**
 * Backend-neutral decision describing which runtime IR artifact is handed to backend lowering.
 *
 * <p>This keeps original-vs-optimized selection out of diagnostics-only formatters, so future runtime pipelines can use
 * the same decision object before lowering OpenCL, CUDA, Vulkan, or Metal source.</p>
 */
public record GpuRuntimeIrSelection(
        Optional<IrGpuArtifact> originalArtifact,
        Optional<IrGpuArtifact> optimizedArtifact,
        Optional<IrGpuArtifact> selectedArtifact,
        String selectedStage,
        String originalIdentity,
        String optimizedIdentity,
        String selectedIdentity,
        boolean transformed,
        boolean optimizedRejected,
        String fallbackDecision,
        GpuProductionIrAcceptanceGate.Result productionIrGate,
        String diagnostic
) {

    public GpuRuntimeIrSelection {
        originalArtifact = originalArtifact == null ? Optional.empty() : originalArtifact;
        optimizedArtifact = optimizedArtifact == null ? Optional.empty() : optimizedArtifact;
        selectedArtifact = selectedArtifact == null ? Optional.empty() : selectedArtifact;
        selectedStage = normalize(selectedStage, selectedArtifact.isPresent() ? "unknown" : "missing");
        originalIdentity = normalize(originalIdentity, IrGpuArtifactIdentity.stableIdentity(originalArtifact));
        optimizedIdentity = normalize(optimizedIdentity, IrGpuArtifactIdentity.stableIdentity(optimizedArtifact));
        selectedIdentity = normalize(selectedIdentity, IrGpuArtifactIdentity.stableIdentity(selectedArtifact));
        fallbackDecision = normalize(fallbackDecision, GpuRuntimeCompileProvenance.NO_FALLBACK);
        productionIrGate = productionIrGate == null
                ? GpuProductionIrAcceptanceGate.evaluate(
                        "GPU backend",
                        "runtime optimized IrGpu",
                        "off",
                        false,
                        false,
                        null,
                        false
                )
                : productionIrGate;
        diagnostic = normalize(diagnostic, "runtime IR selection did not provide diagnostics");
    }

    public static GpuRuntimeIrSelection from(GpuRuntimeCompileArtifactSnapshot snapshot) {
        Optional<IrGpuArtifact> originalArtifact = snapshot == null ? Optional.empty() : snapshot.originalIrGpuArtifact();
        Optional<IrGpuArtifact> optimizedArtifact = snapshot == null ? Optional.empty() : snapshot.optimizedIrGpuArtifact();
        GpuRuntimeFallbackEvidence fallbackEvidence = snapshot == null
                ? GpuRuntimeFallbackEvidence.none()
                : snapshot.fallbackEvidence();
        GpuRuntimeIrOptimizationReport optimizationReport = snapshot == null
                ? GpuRuntimeIrOptimizationReport.empty(optimizedArtifact)
                : snapshot.optimizationReport();
        GpuRuntimeCompileProvenance compileProvenance = snapshot == null
                ? GpuRuntimeCompileProvenance.unknown()
                : snapshot.compileProvenance();
        GpuRuntimeProductionOptimizerGate productionOptimizerGate = snapshot == null
                ? GpuRuntimeProductionOptimizerGate.evaluate("off", null, null, null)
                : snapshot.productionOptimizerGate();
        return fromFields(
                originalArtifact,
                optimizedArtifact,
                fallbackEvidence,
                optimizationReport,
                compileProvenance,
                productionOptimizerGate
        );
    }

    public static GpuRuntimeIrSelection fromFields(
            Optional<IrGpuArtifact> originalArtifact,
            Optional<IrGpuArtifact> optimizedArtifact,
            GpuRuntimeFallbackEvidence fallbackEvidence,
            GpuRuntimeIrOptimizationReport optimizationReport,
            GpuRuntimeCompileProvenance compileProvenance,
            GpuRuntimeProductionOptimizerGate productionOptimizerGate
    ) {
        originalArtifact = originalArtifact == null ? Optional.empty() : originalArtifact;
        optimizedArtifact = optimizedArtifact == null ? Optional.empty() : optimizedArtifact;
        fallbackEvidence = fallbackEvidence == null ? GpuRuntimeFallbackEvidence.none() : fallbackEvidence;
        optimizationReport = optimizationReport == null
                ? GpuRuntimeIrOptimizationReport.empty(optimizedArtifact)
                : optimizationReport;
        compileProvenance = compileProvenance == null ? GpuRuntimeCompileProvenance.unknown() : compileProvenance;
        productionOptimizerGate = productionOptimizerGate == null
                ? GpuRuntimeProductionOptimizerGate.evaluate("off", null, null, null)
                : productionOptimizerGate;

        GpuProductionIrAcceptanceGate.Result productionIrGate = productionIrGate(
                compileProvenance,
                productionOptimizerGate
        );
        boolean productionGateBlocked = productionOptimizerGate.productionProfileRequested()
                && !productionIrGate.accepted();
        Optional<IrGpuArtifact> selectedArtifact = selectedArtifact(
                originalArtifact,
                optimizedArtifact,
                fallbackEvidence,
                optimizationReport,
                productionGateBlocked
        );
        String originalIdentity = IrGpuArtifactIdentity.stableIdentity(originalArtifact);
        String optimizedIdentity = IrGpuArtifactIdentity.stableIdentity(optimizedArtifact);
        String selectedIdentity = IrGpuArtifactIdentity.stableIdentity(selectedArtifact);
        boolean transformed = originalArtifact.isPresent()
                && optimizedArtifact.isPresent()
                && !originalIdentity.equals(optimizedIdentity);
        boolean optimizedRejected = fallbackEvidence.optimizedIrRejected()
                || optimizationReport.requiresRollback()
                || productionGateBlocked;
        String fallbackDecision = fallbackDecision(fallbackEvidence, optimizationReport, productionGateBlocked);

        return new GpuRuntimeIrSelection(
                originalArtifact,
                optimizedArtifact,
                selectedArtifact,
                selectedStage(originalArtifact, optimizedArtifact, selectedArtifact),
                originalIdentity,
                optimizedIdentity,
                selectedIdentity,
                transformed,
                optimizedRejected,
                fallbackDecision,
                productionIrGate,
                diagnostic(selectedArtifact, transformed, optimizedRejected, optimizedArtifact, productionGateBlocked)
        );
    }

    private static Optional<IrGpuArtifact> selectedArtifact(
            Optional<IrGpuArtifact> originalArtifact,
            Optional<IrGpuArtifact> optimizedArtifact,
            GpuRuntimeFallbackEvidence fallbackEvidence,
            GpuRuntimeIrOptimizationReport optimizationReport,
            boolean productionGateBlocked
    ) {
        if (fallbackEvidence.originalIrSelected() && originalArtifact.isPresent()) {
            return originalArtifact;
        }
        if (optimizationReport.requiresRollback() && originalArtifact.isPresent()) {
            return originalArtifact;
        }
        if (productionGateBlocked && originalArtifact.isPresent()) {
            return originalArtifact;
        }
        return optimizedArtifact.or(() -> originalArtifact);
    }

    private static String selectedStage(
            Optional<IrGpuArtifact> originalArtifact,
            Optional<IrGpuArtifact> optimizedArtifact,
            Optional<IrGpuArtifact> selectedArtifact
    ) {
        if (selectedArtifact.isEmpty()) {
            return "missing";
        }
        String selectedIdentity = IrGpuArtifactIdentity.stableIdentity(selectedArtifact);
        if (originalArtifact.isPresent()
                && selectedIdentity.equals(IrGpuArtifactIdentity.stableIdentity(originalArtifact))) {
            return "original";
        }
        if (optimizedArtifact.isPresent()
                && selectedIdentity.equals(IrGpuArtifactIdentity.stableIdentity(optimizedArtifact))) {
            return "optimized";
        }
        return "unknown";
    }

    private static GpuProductionIrAcceptanceGate.Result productionIrGate(
            GpuRuntimeCompileProvenance compileProvenance,
            GpuRuntimeProductionOptimizerGate productionOptimizerGate
    ) {
        return GpuProductionIrAcceptanceGate.evaluate(
                compileProvenance.backendName(),
                "runtime optimized IrGpu",
                compileProvenance.optimizationProfile(),
                productionOptimizerGate.productionProfileRequested(),
                productionOptimizerGate.accepted(),
                compileProvenance.backendOptions().productionPromotionDecisionMode(),
                compileProvenance.backendOptions().productionPromotionOperatorAccepted()
        );
    }

    private static String diagnostic(
            Optional<IrGpuArtifact> selectedArtifact,
            boolean transformed,
            boolean optimizedRejected,
            Optional<IrGpuArtifact> optimizedArtifact,
            boolean productionGateBlocked
    ) {
        if (selectedArtifact.isEmpty()) {
            return "runtime compile has no IrGpu artifact to hand off before backend lowering";
        }
        if (productionGateBlocked) {
            return "optimized IrGpu was rejected by the production IR acceptance gate; original IrGpu remains selected";
        }
        if (optimizedRejected) {
            return "optimized IrGpu was rejected; original IrGpu remains selected for backend lowering";
        }
        if (transformed) {
            return "optimized IrGpu is selected for backend lowering after runtime optimizer passes";
        }
        if (optimizedArtifact.isPresent()) {
            return "optimized IrGpu is a pass-through artifact and remains selected for backend lowering";
        }
        return "original IrGpu is selected for backend lowering because no optimized artifact is available";
    }

    private static String fallbackDecision(
            GpuRuntimeFallbackEvidence fallbackEvidence,
            GpuRuntimeIrOptimizationReport optimizationReport,
            boolean productionGateBlocked
    ) {
        if (!GpuRuntimeCompileProvenance.NO_FALLBACK.equals(fallbackEvidence.decision())) {
            return fallbackEvidence.decision();
        }
        if (optimizationReport.requiresRollback()) {
            return "optimizer-rollback";
        }
        if (productionGateBlocked) {
            return "production-ir-gate-blocked";
        }
        return GpuRuntimeCompileProvenance.NO_FALLBACK;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
