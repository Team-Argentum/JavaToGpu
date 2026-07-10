package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Captures the artifacts that participate in one runtime compilation.
 *
 * <p>This is intentionally backend-neutral: future CUDA/Vulkan/Metal paths can keep the same original IR, optimized IR,
 * lowered backend artifact, compile log, and runtime validation evidence without depending on the OpenCL classes.</p>
 */
public record GpuRuntimeCompileArtifactSnapshot(
        Optional<IrGpuArtifact> originalIrGpuArtifact,
        Optional<IrGpuArtifact> optimizedIrGpuArtifact,
        GpuBackendModuleArtifact backendModuleArtifact,
        GpuRuntimeCompileInvalidationStamp invalidationStamp,
        GpuRuntimeCompileProvenance compileProvenance,
        GpuRuntimeIrOptimizationReport optimizationReport,
        GpuRuntimeEquivalenceEvidence runtimeEquivalenceEvidence,
        GpuRuntimeFallbackEvidence fallbackEvidence,
        GpuRuntimeProductionOptimizerGate productionOptimizerGate,
        GpuRuntimeIrSelection runtimeIrSelection,
        Optional<GpuBackendSourceSwitchingDecision> backendSourceSwitchingDecision,
        Optional<GpuBackendSourcePromotionGate> backendSourcePromotionGate,
        List<IrGpuSourceLocation> sourceLocations,
        String compileLog,
        List<String> runtimeValidationEvidence,
        Optional<GpuRuntimeDeviceSelection> deviceSelection,
        List<GpuRuntimeBinaryArtifact> binaryArtifacts
) {

    public GpuRuntimeCompileArtifactSnapshot {
        originalIrGpuArtifact = originalIrGpuArtifact == null ? Optional.empty() : originalIrGpuArtifact;
        optimizedIrGpuArtifact = optimizedIrGpuArtifact == null ? Optional.empty() : optimizedIrGpuArtifact;
        backendModuleArtifact = backendModuleArtifact == null
                ? GpuBackendModuleArtifact.unknown()
                : backendModuleArtifact;
        invalidationStamp = invalidationStamp == null
                ? GpuRuntimeCompileInvalidationStamp.from(null, backendModuleArtifact, null)
                : invalidationStamp;
        compileProvenance = compileProvenance == null
                ? GpuRuntimeCompileProvenance.unknown()
                : compileProvenance;
        optimizationReport = optimizationReport == null
                ? GpuRuntimeIrOptimizationReport.empty(optimizedIrGpuArtifact)
                : optimizationReport;
        runtimeEquivalenceEvidence = runtimeEquivalenceEvidence == null
                ? GpuRuntimeEquivalenceEvidence.notRun(null, "runtime equivalence was not executed")
                : runtimeEquivalenceEvidence;
        fallbackEvidence = fallbackEvidence == null ? GpuRuntimeFallbackEvidence.none() : fallbackEvidence;
        productionOptimizerGate = productionOptimizerGate == null
                ? productionGate(compileProvenance, optimizationReport, runtimeEquivalenceEvidence, fallbackEvidence)
                : productionOptimizerGate;
        runtimeIrSelection = runtimeIrSelection == null
                ? GpuRuntimeIrSelection.fromFields(
                originalIrGpuArtifact,
                optimizedIrGpuArtifact,
                fallbackEvidence,
                optimizationReport,
                compileProvenance,
                productionOptimizerGate
        )
                : runtimeIrSelection;
        backendSourceSwitchingDecision = backendSourceSwitchingDecision == null
                ? Optional.empty()
                : backendSourceSwitchingDecision;
        backendSourcePromotionGate = backendSourcePromotionGate == null
                ? Optional.empty()
                : backendSourcePromotionGate;
        sourceLocations = sourceLocations == null ? List.of() : List.copyOf(sourceLocations);
        compileLog = compileLog == null ? "" : compileLog;
        runtimeValidationEvidence = runtimeValidationEvidence == null
                ? List.of()
                : List.copyOf(runtimeValidationEvidence);
        deviceSelection = deviceSelection == null ? Optional.empty() : deviceSelection;
        binaryArtifacts = binaryArtifacts == null ? List.of() : List.copyOf(binaryArtifacts);
    }

    public GpuRuntimeCompileArtifactSnapshot(
            Optional<IrGpuArtifact> originalIrGpuArtifact,
            Optional<IrGpuArtifact> optimizedIrGpuArtifact,
            GpuBackendModuleArtifact backendModuleArtifact,
            GpuRuntimeCompileInvalidationStamp invalidationStamp,
            GpuRuntimeCompileProvenance compileProvenance,
            GpuRuntimeIrOptimizationReport optimizationReport,
            GpuRuntimeEquivalenceEvidence runtimeEquivalenceEvidence,
            GpuRuntimeFallbackEvidence fallbackEvidence,
            GpuRuntimeProductionOptimizerGate productionOptimizerGate,
            GpuRuntimeIrSelection runtimeIrSelection,
            Optional<GpuBackendSourceSwitchingDecision> backendSourceSwitchingDecision,
            Optional<GpuBackendSourcePromotionGate> backendSourcePromotionGate,
            List<IrGpuSourceLocation> sourceLocations,
            String compileLog,
            List<String> runtimeValidationEvidence,
            Optional<GpuRuntimeDeviceSelection> deviceSelection
    ) {
        this(
                originalIrGpuArtifact,
                optimizedIrGpuArtifact,
                backendModuleArtifact,
                invalidationStamp,
                compileProvenance,
                optimizationReport,
                runtimeEquivalenceEvidence,
                fallbackEvidence,
                productionOptimizerGate,
                runtimeIrSelection,
                backendSourceSwitchingDecision,
                backendSourcePromotionGate,
                sourceLocations,
                compileLog,
                runtimeValidationEvidence,
                deviceSelection,
                List.of()
        );
    }

    public GpuRuntimeCompileArtifactSnapshot(
            Optional<IrGpuArtifact> originalIrGpuArtifact,
            Optional<IrGpuArtifact> optimizedIrGpuArtifact,
            GpuBackendModuleArtifact backendModuleArtifact,
            GpuRuntimeCompileInvalidationStamp invalidationStamp,
            GpuRuntimeCompileProvenance compileProvenance,
            GpuRuntimeIrOptimizationReport optimizationReport,
            GpuRuntimeEquivalenceEvidence runtimeEquivalenceEvidence,
            GpuRuntimeFallbackEvidence fallbackEvidence,
            GpuRuntimeProductionOptimizerGate productionOptimizerGate,
            GpuRuntimeIrSelection runtimeIrSelection,
            Optional<GpuBackendSourceSwitchingDecision> backendSourceSwitchingDecision,
            Optional<GpuBackendSourcePromotionGate> backendSourcePromotionGate,
            List<IrGpuSourceLocation> sourceLocations,
            String compileLog,
            List<String> runtimeValidationEvidence
    ) {
        this(
                originalIrGpuArtifact,
                optimizedIrGpuArtifact,
                backendModuleArtifact,
                invalidationStamp,
                compileProvenance,
                optimizationReport,
                runtimeEquivalenceEvidence,
                fallbackEvidence,
                productionOptimizerGate,
                runtimeIrSelection,
                backendSourceSwitchingDecision,
                backendSourcePromotionGate,
                sourceLocations,
                compileLog,
                runtimeValidationEvidence,
                Optional.empty()
        );
    }

    public GpuRuntimeCompileArtifactSnapshot(
            Optional<IrGpuArtifact> originalIrGpuArtifact,
            Optional<IrGpuArtifact> optimizedIrGpuArtifact,
            GpuBackendModuleArtifact backendModuleArtifact,
            GpuRuntimeCompileInvalidationStamp invalidationStamp,
            List<IrGpuSourceLocation> sourceLocations,
            String compileLog,
            List<String> runtimeValidationEvidence
    ) {
        this(
                originalIrGpuArtifact,
                optimizedIrGpuArtifact,
                backendModuleArtifact,
                invalidationStamp,
                GpuRuntimeCompileProvenance.unknown(),
                GpuRuntimeIrOptimizationReport.empty(optimizedIrGpuArtifact),
                GpuRuntimeEquivalenceEvidence.notRun(null, "runtime equivalence was not executed"),
                GpuRuntimeFallbackEvidence.none(),
                GpuRuntimeProductionOptimizerGate.evaluate("off", null, null, null),
                null,
                Optional.empty(),
                Optional.empty(),
                sourceLocations,
                compileLog,
                runtimeValidationEvidence,
                Optional.empty()
        );
    }

    public static GpuRuntimeCompileArtifactSnapshot legacy(GpuKernelDescriptor descriptor) {
        return new GpuRuntimeCompileArtifactSnapshot(
                Optional.empty(),
                Optional.empty(),
                GpuBackendModuleArtifact.openClSource(
                        descriptor.kernelSource(),
                        descriptor.kernelResource(),
                        "legacy-opencl-source"
                ),
                GpuRuntimeCompileInvalidationStamp.from(
                        null,
                        GpuBackendModuleArtifact.openClSource(
                                descriptor.kernelSource(),
                                descriptor.kernelResource(),
                                "legacy-opencl-source"
                        ),
                        null
                ),
                GpuRuntimeCompileProvenance.unknown(),
                GpuRuntimeIrOptimizationReport.empty(Optional.empty()),
                GpuRuntimeEquivalenceEvidence.notRun(null, "legacy OpenCL source path has no runtime equivalence evidence"),
                GpuRuntimeFallbackEvidence.none(),
                GpuRuntimeProductionOptimizerGate.evaluate("off", null, null, null),
                null,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                "",
                List.of()
        );
    }

    public static GpuRuntimeCompileArtifactSnapshot from(
            GpuRuntimeCompileRequest originalRequest,
            GpuRuntimeCompileRequest optimizedRequest,
            GpuBackendModuleArtifact backendModuleArtifact,
            GpuRuntimeCompileInvalidationStamp invalidationStamp,
            GpuRuntimeCompileProvenance compileProvenance,
            GpuRuntimeIrOptimizationReport optimizationReport,
            GpuRuntimeEquivalenceEvidence runtimeEquivalenceEvidence,
            GpuRuntimeFallbackEvidence fallbackEvidence
    ) {
        GpuRuntimeFallbackEvidence resolvedFallbackEvidence = fallbackEvidence == null
                ? GpuRuntimeFallbackEvidence.none()
                : fallbackEvidence;
        return new GpuRuntimeCompileArtifactSnapshot(
                originalRequest == null ? Optional.empty() : originalRequest.irGpuArtifact(),
                optimizedRequest == null ? Optional.empty() : optimizedRequest.irGpuArtifact(),
                backendModuleArtifact,
                invalidationStamp,
                syncFallbackDecision(compileProvenance, resolvedFallbackEvidence),
                optimizationReport,
                runtimeEquivalenceEvidence,
                resolvedFallbackEvidence,
                GpuRuntimeProductionOptimizerGate.evaluate(
                        optimizedRequest,
                        optimizationReport,
                        runtimeEquivalenceEvidence,
                        resolvedFallbackEvidence
                ),
                null,
                Optional.empty(),
                Optional.empty(),
                collectSourceLocations(originalRequest, optimizedRequest),
                "",
                List.of()
        );
    }

    private static GpuRuntimeCompileProvenance syncFallbackDecision(
            GpuRuntimeCompileProvenance compileProvenance,
            GpuRuntimeFallbackEvidence fallbackEvidence
    ) {
        GpuRuntimeCompileProvenance resolvedProvenance = compileProvenance == null
                ? GpuRuntimeCompileProvenance.unknown()
                : compileProvenance;
        if (fallbackEvidence == null || GpuRuntimeFallbackEvidence.NONE.equals(fallbackEvidence.decision())) {
            return resolvedProvenance;
        }
        return resolvedProvenance.withFallbackDecision(fallbackEvidence.decision());
    }

    private static GpuRuntimeProductionOptimizerGate productionGate(
            GpuRuntimeCompileProvenance compileProvenance,
            GpuRuntimeIrOptimizationReport optimizationReport,
            GpuRuntimeEquivalenceEvidence runtimeEquivalenceEvidence,
            GpuRuntimeFallbackEvidence fallbackEvidence
    ) {
        String profile = compileProvenance == null ? "off" : compileProvenance.optimizationProfile();
        return GpuRuntimeProductionOptimizerGate.evaluate(
                profile,
                optimizationReport,
                runtimeEquivalenceEvidence,
                fallbackEvidence
        );
    }

    public static GpuRuntimeCompileArtifactSnapshot from(
            GpuRuntimeCompileRequest originalRequest,
            GpuRuntimeCompileRequest optimizedRequest,
            GpuBackendModuleArtifact backendModuleArtifact,
            GpuRuntimeCompileInvalidationStamp invalidationStamp,
            GpuRuntimeCompileProvenance compileProvenance,
            GpuRuntimeIrOptimizationReport optimizationReport
    ) {
        return from(
                originalRequest,
                optimizedRequest,
                backendModuleArtifact,
                invalidationStamp,
                compileProvenance,
                optimizationReport,
                GpuRuntimeEquivalenceEvidence.notRun(
                        optimizedRequest,
                        "runtime equivalence execution has not been wired for this compile request"
                ),
                fallbackEvidence(optimizationReport, null)
        );
    }

    public static GpuRuntimeCompileArtifactSnapshot from(
            GpuRuntimeCompileRequest originalRequest,
            GpuRuntimeCompileRequest optimizedRequest,
            GpuBackendModuleArtifact backendModuleArtifact,
            GpuRuntimeCompileInvalidationStamp invalidationStamp,
            GpuRuntimeCompileProvenance compileProvenance,
            GpuRuntimeIrOptimizationReport optimizationReport,
            GpuRuntimeEquivalenceEvidence runtimeEquivalenceEvidence
    ) {
        return from(
                originalRequest,
                optimizedRequest,
                backendModuleArtifact,
                invalidationStamp,
                compileProvenance,
                optimizationReport,
                runtimeEquivalenceEvidence,
                fallbackEvidence(optimizationReport, runtimeEquivalenceEvidence)
        );
    }

    private static GpuRuntimeFallbackEvidence fallbackEvidence(
            GpuRuntimeIrOptimizationReport optimizationReport,
            GpuRuntimeEquivalenceEvidence runtimeEquivalenceEvidence
    ) {
        if (optimizationReport != null && optimizationReport.requiresRollback()) {
            return GpuRuntimeFallbackEvidence.optimizerRollback(
                    optimizationReport.passReports().stream()
                            .filter(report -> report.outcome() == GpuRuntimeIrOptimizationOutcome.ROLLED_BACK
                                    || report.outcome() == GpuRuntimeIrOptimizationOutcome.FAILED)
                            .map(GpuRuntimeIrOptimizationPassReport::toLine)
                            .toList()
            );
        }
        if (runtimeEquivalenceEvidence != null
                && runtimeEquivalenceEvidence.executed()
                && !runtimeEquivalenceEvidence.equivalent()) {
            return GpuRuntimeFallbackEvidence.runtimeEquivalenceFailure(runtimeEquivalenceEvidence.diagnostics());
        }
        return GpuRuntimeFallbackEvidence.none();
    }

    public static GpuRuntimeCompileArtifactSnapshot from(
            GpuRuntimeCompileRequest originalRequest,
            GpuRuntimeCompileRequest optimizedRequest,
            GpuBackendModuleArtifact backendModuleArtifact,
            GpuRuntimeCompileInvalidationStamp invalidationStamp,
            GpuRuntimeCompileProvenance compileProvenance
    ) {
        return from(
                originalRequest,
                optimizedRequest,
                backendModuleArtifact,
                invalidationStamp,
                compileProvenance,
                GpuRuntimeIrOptimizationReport.empty(optimizedRequest == null
                        ? Optional.empty()
                        : optimizedRequest.irGpuArtifact())
        );
    }

    public static GpuRuntimeCompileArtifactSnapshot from(
            GpuRuntimeCompileRequest originalRequest,
            GpuRuntimeCompileRequest optimizedRequest,
            GpuBackendModuleArtifact backendModuleArtifact,
            GpuRuntimeCompileInvalidationStamp invalidationStamp
    ) {
        return from(
                originalRequest,
                optimizedRequest,
                backendModuleArtifact,
                invalidationStamp,
                GpuRuntimeCompileProvenance.from(optimizedRequest)
        );
    }

    private static List<IrGpuSourceLocation> collectSourceLocations(
            GpuRuntimeCompileRequest originalRequest,
            GpuRuntimeCompileRequest optimizedRequest
    ) {
        java.util.LinkedHashMap<String, IrGpuSourceLocation> sourceLocations = new java.util.LinkedHashMap<>();
        addSourceLocations(sourceLocations, originalRequest == null ? Optional.empty() : originalRequest.irGpuArtifact());
        addSourceLocations(sourceLocations, optimizedRequest == null ? Optional.empty() : optimizedRequest.irGpuArtifact());
        return List.copyOf(sourceLocations.values());
    }

    private static void addSourceLocations(
            java.util.LinkedHashMap<String, IrGpuSourceLocation> sourceLocations,
            Optional<IrGpuArtifact> artifact
    ) {
        artifact.ifPresent(irGpuArtifact -> irGpuArtifact.module().methodBodies().forEach(methodBody -> {
            IrGpuSourceLocation location = methodBody.sourceLocation();
            if (!location.knownRange()) {
                return;
            }
            sourceLocations.putIfAbsent(
                    location.sourceKind()
                            + "|"
                            + location.ownerQualifiedName()
                            + "|"
                            + location.methodName()
                            + "|"
                            + location.beginLine()
                            + ":"
                            + location.beginColumn(),
                    location
            );
        }));
    }

    public static GpuRuntimeCompileArtifactSnapshot from(
            GpuRuntimeCompileRequest originalRequest,
            GpuRuntimeCompileRequest optimizedRequest,
            GpuBackendModuleArtifact backendModuleArtifact
    ) {
        return from(
                originalRequest,
                optimizedRequest,
                backendModuleArtifact,
                GpuRuntimeCompileInvalidationStamp.from(optimizedRequest, backendModuleArtifact, null)
        );
    }

    public GpuRuntimeCompileArtifactSnapshot withCompileLog(String compileLog) {
        return new GpuRuntimeCompileArtifactSnapshot(
                originalIrGpuArtifact,
                optimizedIrGpuArtifact,
                backendModuleArtifact,
                invalidationStamp,
                compileProvenance,
                optimizationReport,
                runtimeEquivalenceEvidence,
                fallbackEvidence,
                productionOptimizerGate,
                runtimeIrSelection,
                backendSourceSwitchingDecision,
                backendSourcePromotionGate,
                sourceLocations,
                compileLog,
                runtimeValidationEvidence,
                deviceSelection,
                binaryArtifacts
        );
    }

    public GpuRuntimeCompileArtifactSnapshot withBinaryArtifacts(List<GpuRuntimeBinaryArtifact> artifacts) {
        return new GpuRuntimeCompileArtifactSnapshot(
                originalIrGpuArtifact,
                optimizedIrGpuArtifact,
                backendModuleArtifact,
                invalidationStamp,
                compileProvenance,
                optimizationReport,
                runtimeEquivalenceEvidence,
                fallbackEvidence,
                productionOptimizerGate,
                runtimeIrSelection,
                backendSourceSwitchingDecision,
                backendSourcePromotionGate,
                sourceLocations,
                compileLog,
                runtimeValidationEvidence,
                deviceSelection,
                artifacts
        );
    }

    public GpuRuntimeCompileArtifactSnapshot withRuntimeValidationEvidence(List<String> evidence) {
        return new GpuRuntimeCompileArtifactSnapshot(
                originalIrGpuArtifact,
                optimizedIrGpuArtifact,
                backendModuleArtifact,
                invalidationStamp,
                compileProvenance,
                optimizationReport,
                runtimeEquivalenceEvidence,
                fallbackEvidence,
                productionOptimizerGate,
                runtimeIrSelection,
                backendSourceSwitchingDecision,
                backendSourcePromotionGate,
                sourceLocations,
                compileLog,
                evidence,
                deviceSelection,
                binaryArtifacts
        );
    }

    public GpuRuntimeCompileArtifactSnapshot withCompileProvenance(GpuRuntimeCompileProvenance provenance) {
        GpuRuntimeCompileProvenance resolvedProvenance = syncFallbackDecision(provenance, fallbackEvidence);
        return new GpuRuntimeCompileArtifactSnapshot(
                originalIrGpuArtifact,
                optimizedIrGpuArtifact,
                backendModuleArtifact,
                invalidationStamp,
                resolvedProvenance,
                optimizationReport,
                runtimeEquivalenceEvidence,
                fallbackEvidence,
                productionGate(resolvedProvenance, optimizationReport, runtimeEquivalenceEvidence, fallbackEvidence),
                null,
                Optional.empty(),
                Optional.empty(),
                sourceLocations,
                compileLog,
                runtimeValidationEvidence,
                deviceSelection,
                binaryArtifacts
        );
    }

    public GpuRuntimeCompileArtifactSnapshot withOptimizationReport(GpuRuntimeIrOptimizationReport report) {
        return new GpuRuntimeCompileArtifactSnapshot(
                originalIrGpuArtifact,
                optimizedIrGpuArtifact,
                backendModuleArtifact,
                invalidationStamp,
                compileProvenance,
                report,
                runtimeEquivalenceEvidence,
                fallbackEvidence(report, runtimeEquivalenceEvidence),
                productionGate(compileProvenance, report, runtimeEquivalenceEvidence, fallbackEvidence(report, runtimeEquivalenceEvidence)),
                null,
                Optional.empty(),
                Optional.empty(),
                sourceLocations,
                compileLog,
                runtimeValidationEvidence,
                deviceSelection,
                binaryArtifacts
        );
    }

    public GpuRuntimeCompileArtifactSnapshot withRuntimeEquivalenceEvidence(GpuRuntimeEquivalenceEvidence evidence) {
        return new GpuRuntimeCompileArtifactSnapshot(
                originalIrGpuArtifact,
                optimizedIrGpuArtifact,
                backendModuleArtifact,
                invalidationStamp,
                compileProvenance,
                optimizationReport,
                evidence,
                fallbackEvidence(optimizationReport, evidence),
                productionGate(compileProvenance, optimizationReport, evidence, fallbackEvidence(optimizationReport, evidence)),
                null,
                Optional.empty(),
                Optional.empty(),
                sourceLocations,
                compileLog,
                runtimeValidationEvidence,
                deviceSelection,
                binaryArtifacts
        );
    }

    public GpuRuntimeCompileArtifactSnapshot withFallbackEvidence(GpuRuntimeFallbackEvidence evidence) {
        return new GpuRuntimeCompileArtifactSnapshot(
                originalIrGpuArtifact,
                optimizedIrGpuArtifact,
                backendModuleArtifact,
                invalidationStamp,
                compileProvenance.withFallbackDecision(evidence == null ? null : evidence.decision()),
                optimizationReport,
                runtimeEquivalenceEvidence,
                evidence,
                productionGate(compileProvenance, optimizationReport, runtimeEquivalenceEvidence, evidence),
                null,
                Optional.empty(),
                Optional.empty(),
                sourceLocations,
                compileLog,
                runtimeValidationEvidence,
                deviceSelection,
                binaryArtifacts
        );
    }

    public GpuRuntimeCompileArtifactSnapshot withRuntimeIrSelection(GpuRuntimeIrSelection selection) {
        return new GpuRuntimeCompileArtifactSnapshot(
                originalIrGpuArtifact,
                optimizedIrGpuArtifact,
                backendModuleArtifact,
                invalidationStamp,
                compileProvenance,
                optimizationReport,
                runtimeEquivalenceEvidence,
                fallbackEvidence,
                productionOptimizerGate,
                selection,
                backendSourceSwitchingDecision,
                backendSourcePromotionGate,
                sourceLocations,
                compileLog,
                runtimeValidationEvidence,
                deviceSelection,
                binaryArtifacts
        );
    }

    public GpuRuntimeCompileArtifactSnapshot withBackendSourceSwitchingDecision(
            GpuBackendSourceSwitchingDecision decision
    ) {
        return new GpuRuntimeCompileArtifactSnapshot(
                originalIrGpuArtifact,
                optimizedIrGpuArtifact,
                backendModuleArtifact,
                invalidationStamp,
                compileProvenance,
                optimizationReport,
                runtimeEquivalenceEvidence,
                fallbackEvidence,
                productionOptimizerGate,
                runtimeIrSelection,
                Optional.ofNullable(decision),
                backendSourcePromotionGate,
                sourceLocations,
                compileLog,
                runtimeValidationEvidence,
                deviceSelection,
                binaryArtifacts
        );
    }

    public GpuRuntimeCompileArtifactSnapshot withBackendSourceState(
            GpuBackendSourcePromotionGate gate,
            GpuBackendSourceSwitchingDecision decision
    ) {
        return new GpuRuntimeCompileArtifactSnapshot(
                originalIrGpuArtifact,
                optimizedIrGpuArtifact,
                backendModuleArtifact,
                invalidationStamp,
                compileProvenance,
                optimizationReport,
                runtimeEquivalenceEvidence,
                fallbackEvidence,
                productionOptimizerGate,
                runtimeIrSelection,
                Optional.ofNullable(decision),
                Optional.ofNullable(gate),
                sourceLocations,
                compileLog,
                runtimeValidationEvidence,
                deviceSelection
        );
    }

    public GpuRuntimeCompileArtifactSnapshot withBackendSourcePromotionGate(
            GpuBackendSourcePromotionGate gate
    ) {
        return new GpuRuntimeCompileArtifactSnapshot(
                originalIrGpuArtifact,
                optimizedIrGpuArtifact,
                backendModuleArtifact,
                invalidationStamp,
                compileProvenance,
                optimizationReport,
                runtimeEquivalenceEvidence,
                fallbackEvidence,
                productionOptimizerGate,
                runtimeIrSelection,
                backendSourceSwitchingDecision,
                Optional.ofNullable(gate),
                sourceLocations,
                compileLog,
                runtimeValidationEvidence,
                Optional.empty(),
                binaryArtifacts
        );
    }

    public GpuRuntimeCompileArtifactSnapshot withDeviceSelection(GpuRuntimeDeviceSelection selection) {
        return new GpuRuntimeCompileArtifactSnapshot(
                originalIrGpuArtifact,
                optimizedIrGpuArtifact,
                backendModuleArtifact,
                invalidationStamp,
                compileProvenance,
                optimizationReport,
                runtimeEquivalenceEvidence,
                fallbackEvidence,
                productionOptimizerGate,
                runtimeIrSelection,
                backendSourceSwitchingDecision,
                backendSourcePromotionGate,
                sourceLocations,
                compileLog,
                runtimeValidationEvidence,
                Optional.ofNullable(selection),
                binaryArtifacts
        );
    }
}
