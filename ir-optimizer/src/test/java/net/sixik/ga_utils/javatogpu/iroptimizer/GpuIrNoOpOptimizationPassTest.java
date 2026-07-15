package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationOutcome;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationStage;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizerRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrNoOpOptimizationPassTest {

    @Test
    void noOpPassReportsSkippedWithoutChangingArtifact() {
        IrGpuArtifact artifact = artifact("body\n  return original\n");
        GpuRuntimeIrOptimizationReport report = new GpuIrNoOpOptimizationPass()
                .run(new GpuRuntimeIrOptimizationRequest(request(artifact), Optional.of(artifact)));

        assertSame(artifact, report.artifact().orElseThrow());
        assertFalse(report.requiresRollback());
        assertEquals(1, report.passReports().size());
        assertEquals(GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY, report.passReports().get(0).stage());
        assertEquals(GpuIrNoOpOptimizationPass.PASS_ID + ":" + GpuIrNoOpOptimizationPass.PASS_VERSION,
                report.passReports().get(0).optimizerVersion());
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, report.passReports().get(0).outcome());
        assertTrue(report.passReports().get(0).toLine().contains("does not mutate IR"));
    }

    @Test
    void directSkeletonPassKeepsRuntimeRegistryNoOpSemantics() {
        IrGpuArtifact artifact = artifact("body\n  return original\n");

        GpuRuntimeIrOptimizationReport report = GpuRuntimeIrOptimizerRegistry.ofPasses(
                        List.of(new GpuIrNoOpOptimizationPass())
                )
                .optimizeWithReport(new GpuRuntimeIrOptimizationRequest(request(artifact), Optional.of(artifact)));

        assertSame(artifact, report.artifact().orElseThrow());
        assertFalse(report.requiresRollback());
        assertEquals(1, report.passReports().size());
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, report.passReports().get(0).outcome());
    }

    private static GpuRuntimeCompileRequest request(IrGpuArtifact artifact) {
        GpuRuntimeDeviceProfile profile = GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "test-device");
        return new GpuRuntimeCompileRequest(
                new GpuKernelDescriptor("run", "test.Kernel.run", "", "test.Kernel.run.irgpu", List.of()),
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                profile,
                Optional.of(artifact)
        );
    }

    private static IrGpuArtifact artifact(String body) {
        IrGpuMethodBody methodBody = IrGpuMethodBody.entry(
                "test.Kernel.run",
                "run",
                body,
                List.of()
        );
        IrGpuModule module = new IrGpuModule("test.Kernel.run", "run", List.of(), List.of(), List.of(methodBody));
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                module,
                List.of(IrGpuBackendOutput.openClSource("test.cl")),
                "opencl",
                "off"
        );
    }
}
