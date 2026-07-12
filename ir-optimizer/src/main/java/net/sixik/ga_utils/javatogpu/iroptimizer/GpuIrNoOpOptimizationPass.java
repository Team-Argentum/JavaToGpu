package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationPass;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationPassReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationStage;

import java.util.List;

/**
 * Safe skeleton pass proving that the optional optimizer module can participate without mutating IR.
 */
public final class GpuIrNoOpOptimizationPass implements GpuRuntimeIrOptimizationPass {

    public static final String PASS_ID = GpuIrOptimizerModule.MODULE_ID + ".noop";
    public static final String PASS_VERSION = GpuIrOptimizerModule.MODULE_VERSION;

    @Override
    public GpuRuntimeIrOptimizationReport run(GpuRuntimeIrOptimizationRequest request) {
        String identity = IrGpuArtifactIdentity.stableIdentity(request.artifact());
        GpuRuntimeIrOptimizationPassReport passReport = GpuRuntimeIrOptimizationPassReport.skipped(
                passVersion(),
                identity,
                "optional ir-optimizer skeleton pass is proposal-only and does not mutate IR"
        ).withStage(stage());
        return new GpuRuntimeIrOptimizationReport(request.artifact(), List.of(passReport));
    }

    @Override
    public GpuRuntimeIrOptimizationStage stage() {
        return GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY;
    }

    @Override
    public String passName() {
        return PASS_ID;
    }

    @Override
    public String passVersion() {
        return PASS_ID + ":" + PASS_VERSION;
    }

    @Override
    public int extensionOrder() {
        return 100;
    }
}
