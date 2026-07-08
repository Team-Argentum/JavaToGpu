package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeIrSelectionTest {

    @Test
    void selectsOptimizedArtifactWhenNoFallbackOrRollbackExists() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuRuntimeCompileArtifactSnapshot snapshot = snapshot(original, optimized, GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized)));

        GpuRuntimeIrSelection selection = GpuRuntimeIrSelection.from(snapshot);

        assertEquals(selection, snapshot.runtimeIrSelection());
        assertEquals("optimized", selection.selectedStage());
        assertTrue(selection.selectedArtifact().isPresent());
        assertTrue(selection.transformed());
        assertFalse(selection.optimizedRejected());
        assertEquals(GpuRuntimeCompileProvenance.NO_FALLBACK, selection.fallbackDecision());
        assertTrue(selection.diagnostic().contains("optimized IrGpu is selected"));
    }

    @Test
    void selectsOriginalArtifactWhenRuntimeEquivalenceFailed() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuRuntimeCompileRequest originalRequest = request(original);
        GpuRuntimeCompileRequest optimizedRequest = request(optimized);
        GpuBackendModuleArtifact backendArtifact = backendArtifact();
        GpuRuntimeIrOptimizationReport report = GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized));
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                originalRequest,
                optimizedRequest,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(optimizedRequest, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(optimizedRequest),
                report,
                GpuRuntimeEquivalenceEvidence.failed(optimizedRequest, 1, 1, List.of("output differs"))
        );

        GpuRuntimeIrSelection selection = GpuRuntimeIrSelection.from(snapshot);

        assertEquals(selection, snapshot.runtimeIrSelection());
        assertEquals("original", selection.selectedStage());
        assertTrue(selection.optimizedRejected());
        assertEquals("runtime-equivalence-failed", selection.fallbackDecision());
        assertTrue(selection.diagnostic().contains("optimized IrGpu was rejected"));
    }

    @Test
    void selectsOriginalArtifactWhenOptimizerRequiresRollback() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuRuntimeIrOptimizationReport report = new GpuRuntimeIrOptimizationReport(
                Optional.of(optimized),
                List.of(GpuRuntimeIrOptimizationPassReport.rolledBack(
                        "optimizer:rollback",
                        "irgpu:sha256:optimized",
                        "irgpu:sha256:unsafe",
                        "proof:failed",
                        "unsafe proof",
                        List.of("rolled back unsafe transform")
                )),
                GpuOptimizationStrategyDecision.none(request(optimized))
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = snapshot(original, optimized, report);

        GpuRuntimeIrSelection selection = GpuRuntimeIrSelection.from(snapshot);

        assertEquals(selection, snapshot.runtimeIrSelection());
        assertEquals("original", selection.selectedStage());
        assertTrue(selection.optimizedRejected());
        assertEquals("optimizer-rollback", selection.fallbackDecision());
    }

    @Test
    void missingSnapshotProducesMissingSelection() {
        GpuRuntimeIrSelection selection = GpuRuntimeIrSelection.from(null);

        assertEquals("missing", selection.selectedStage());
        assertTrue(selection.selectedArtifact().isEmpty());
        assertTrue(selection.diagnostic().contains("no IrGpu artifact"));
    }

    @Test
    void withCompileProvenanceRefreshesProductionGateAndStoredSelection() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuRuntimeCompileArtifactSnapshot snapshot = snapshot(
                original,
                optimized,
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized))
        );
        GpuRuntimeCompileProvenance vendorTunedProvenance = GpuRuntimeCompileProvenance.from(
                new GpuRuntimeCompileRequest(
                        descriptor(),
                        GpuRuntimeCompileOptions.openClIrGpuSource(List.of(), "vendor-tuned"),
                        GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                        Optional.of(optimized)
                )
        );

        GpuRuntimeCompileArtifactSnapshot refreshed = snapshot.withCompileProvenance(vendorTunedProvenance);

        assertEquals("vendor-tuned", refreshed.compileProvenance().optimizationProfile());
        assertTrue(refreshed.productionOptimizerGate().productionProfileRequested());
        assertEquals("blocked", refreshed.productionOptimizerGate().status());
        assertEquals("blocked", refreshed.runtimeIrSelection().productionIrGate().status());
        assertEquals("diagnostic-only", refreshed.runtimeIrSelection().productionIrGate().decisionMode());
        assertTrue(refreshed.runtimeIrSelection().productionIrGate().diagnostic().contains("vendor-tuned"));
        assertEquals("optimized", refreshed.runtimeIrSelection().selectedStage());
    }

    private static GpuRuntimeCompileArtifactSnapshot snapshot(
            IrGpuArtifact original,
            IrGpuArtifact optimized,
            GpuRuntimeIrOptimizationReport report
    ) {
        GpuRuntimeCompileRequest originalRequest = request(original);
        GpuRuntimeCompileRequest optimizedRequest = request(optimized);
        GpuBackendModuleArtifact backendArtifact = backendArtifact();
        return GpuRuntimeCompileArtifactSnapshot.from(
                originalRequest,
                optimizedRequest,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(optimizedRequest, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(optimizedRequest),
                report
        );
    }

    private static GpuRuntimeCompileRequest request(IrGpuArtifact artifact) {
        return new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(artifact)
        );
    }

    private static GpuBackendModuleArtifact backendArtifact() {
        return GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 2; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );
    }

    private static GpuKernelDescriptor descriptor() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
    }

    private static IrGpuArtifact artifact(String body) {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry("kernel", "jtg_kernel", body, List.of()))
                ),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }
}
