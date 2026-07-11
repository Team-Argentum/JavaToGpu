package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;

import java.util.List;
import java.util.Optional;

/**
 * Built-in advisory pass that records register-pressure estimates without mutating IrGpu.
 */
public final class GpuRuntimeRegisterPressureAnalysisPass implements GpuRuntimeIrOptimizationPass {

    public static final String PASS_ID = "javatogpu.runtime.register-pressure-analysis";
    public static final String PASS_VERSION = "register-pressure-analysis:v1";

    @Override
    public GpuRuntimeIrOptimizationReport run(GpuRuntimeIrOptimizationRequest request) {
        Optional<IrGpuArtifact> artifact = request.artifact();
        String identity = artifact.map(IrGpuArtifactIdentity::stableIdentity).orElse("irgpu:missing");
        GpuRuntimeRegisterPressureReport pressureReport = artifact
                .map(value -> GpuRuntimeRegisterPressureAnalyzer.analyze(
                        value,
                        request.compileRequest().deviceProfile()
                ))
                .orElseGet(() -> GpuRuntimeRegisterPressureAnalyzer.analyze(
                        null,
                        request.compileRequest().deviceProfile()
                ));
        GpuRuntimeIrOptimizationPassReport passReport = new GpuRuntimeIrOptimizationPassReport(
                stage(),
                passVersion(),
                pressureReport.available()
                        ? GpuRuntimeIrOptimizationOutcome.APPLIED
                        : GpuRuntimeIrOptimizationOutcome.SKIPPED,
                identity,
                identity,
                pressureReport.available() ? "advisory-estimate" : "typed-ir-unavailable",
                "",
                GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "runtime-ir-analysis",
                        "analysis-only",
                        pressureReport.artifactFields()
                ),
                pressureReport.diagnostics()
        );
        return new GpuRuntimeIrOptimizationReport(artifact, List.of(passReport));
    }

    @Override
    public GpuRuntimeIrOptimizationStage stage() {
        return GpuRuntimeIrOptimizationStage.TARGET_PROFILE_ANALYSIS;
    }

    @Override
    public String passName() {
        return PASS_ID;
    }

    @Override
    public String passVersion() {
        return PASS_VERSION;
    }

    @Override
    public int extensionOrder() {
        return -1_000;
    }
}
