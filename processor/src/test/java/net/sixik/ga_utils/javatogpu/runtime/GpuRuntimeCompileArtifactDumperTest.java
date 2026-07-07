package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeCompileArtifactDumperTest {

    @Test
    void dumpsOriginalOptimizedAndBackendArtifacts() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 2; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );
        GpuRuntimeCompileInvalidationStamp stamp = GpuRuntimeCompileInvalidationStamp.from(
                new GpuRuntimeCompileRequest(
                        descriptor(),
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                        GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                        Optional.of(optimized)
                ),
                backendArtifact,
                "optimizer:test-v1"
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = new GpuRuntimeCompileArtifactSnapshot(
                Optional.of(original),
                Optional.of(optimized),
                backendArtifact,
                stamp,
                new GpuRuntimeCompileProvenance(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Mock GPU",
                        "Mock Vendor",
                        "Mock Driver",
                        "OpenCL 3.0 Mock",
                        48L,
                        65_536L,
                        512L,
                        1L,
                        true,
                        true,
                        false,
                        List.of("-cl-fast-relaxed-math"),
                        "fast",
                        "none"
                ),
                new GpuRuntimeIrOptimizationReport(
                        Optional.of(optimized),
                        List.of(GpuRuntimeIrOptimizationPassReport.applied(
                                "optimizer:test-v1",
                                "irgpu:sha256:original",
                                "irgpu:sha256:optimized",
                                "proof:mocked",
                                List.of("folded duplicated arithmetic")
                        )),
                        GpuOptimizationStrategyDecision.advisory(
                                "strategy:opencl-nvidia-advisory",
                                "nvidia",
                                "fast",
                                "NVIDIA remains scalar-safe until evidence-backed",
                                GpuOptimizationVendorBaseline.nvidiaRecorded(),
                                List.of("strategy is diagnostic-only")
                        )
                ),
                GpuRuntimeEquivalenceEvidence.notRun(
                        new GpuRuntimeCompileRequest(
                                descriptor(),
                                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "fast"),
                                GpuRuntimeDeviceProfile.openCl(
                                        "OpenCL",
                                        "Mock GPU",
                                        "Mock Vendor",
                                        "Mock Driver",
                                        "OpenCL 3.0 Mock",
                                        48L,
                                        65_536L,
                                        512L,
                                        1L,
                                        true,
                                        true,
                                        false
                                ),
                                Optional.of(optimized)
                        ),
                        "pre/post runtime equivalence not executed in unit test"
                ),
                GpuRuntimeFallbackEvidence.none(),
                GpuRuntimeProductionOptimizerGate.evaluate(
                        "off",
                        GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized)),
                        null,
                        null
                ),
                List.of(location()),
                "build ok",
                List.of("equivalence:skipped")
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.hasArtifact("original.irgpu.properties"));
        assertTrue(dump.artifact("original.irgpu.properties").contains("return original"));
        assertTrue(dump.hasArtifact("optimized.irgpu.properties"));
        assertTrue(dump.artifact("optimized.irgpu.properties").contains("return optimized"));
        assertEquals(backendArtifact.source(), dump.artifact("backend.opencl-c"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("backendTarget=OPENCL"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("deviceLabel=Mock GPU"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("computeUnits=48"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("localMemoryBytes=65536"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("maxWorkGroupSize=512"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("preferredVectorWidthFloat=1"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("supportsDoublePrecision=true"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("supportsImages=true"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("supportsSubgroups=false"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("compileArg.0=-cl-fast-relaxed-math"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("optimizationProfile=fast"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("fallbackDecision=none"));
        assertTrue(dump.artifact("optimizer-report.txt").contains("outcome=APPLIED"));
        assertTrue(dump.artifact("optimizer-report.txt").contains("strategy:opencl-nvidia-advisory"));
        assertTrue(dump.artifact("optimizer-report.txt").contains("advisoryOnly=true"));
        assertTrue(dump.artifact("optimizer-report.txt").contains("baselineStatus=recorded-nvidia-only"));
        assertTrue(dump.artifact("optimizer-report.txt").contains("promotionEligible=false"));
        assertTrue(dump.artifact("optimizer-report.txt").contains("proof=proof:mocked"));
        assertTrue(dump.artifact("optimizer-report.txt").contains("folded duplicated arithmetic"));
        assertTrue(dump.artifact("runtime-equivalence.properties").contains("status=not-run"));
        assertTrue(dump.artifact("runtime-equivalence.properties").contains("backendTarget=OPENCL"));
        assertTrue(dump.artifact("runtime-equivalence.properties").contains("optimizationProfile=fast"));
        assertTrue(dump.artifact("runtime-equivalence.properties").contains("executed=false"));
        assertTrue(dump.artifact("runtime-equivalence.properties").contains("diagnostic.0=pre/post runtime equivalence not executed in unit test"));
        assertTrue(dump.artifact("fallback.properties").contains("decision=none"));
        assertTrue(dump.artifact("fallback.properties").contains("originalIrSelected=false"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("status=not-requested"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("runtimeEquivalenceRequired=true"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.applied.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.rolledBack.count=0"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("fallbackDecision=none"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("strategyName=strategy:opencl-nvidia-advisory"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("baselineStatus=recorded-nvidia-only"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("productionGateStatus=not-requested"));
        assertEquals("build ok", dump.artifact("compile.log"));
        assertEquals("equivalence:skipped", dump.artifact("runtime-validation.txt"));
        assertEquals(List.of("java-source:sample.Demo#kernel:4:17-7:5"), dump.sourceLocations());
        assertEquals("optimizer:test-v1", dump.invalidationStamp().optimizerPipelineVersion());
    }

    @Test
    void emptyDumpHandlesMissingSnapshot() {
        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(null);

        assertFalse(dump.hasArtifact("original.irgpu.properties"));
        assertTrue(dump.sourceLocations().isEmpty());
    }

    @Test
    void fallbackEvidenceRecordsRuntimeEquivalenceFailure() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 2; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "fast"),
                GpuRuntimeDeviceProfile.openCl(
                        "OpenCL",
                        "Mock GPU",
                        "Mock Vendor",
                        "Mock Driver",
                        "OpenCL 3.0 Mock",
                        48L,
                        65_536L,
                        512L,
                        1L,
                        true,
                        true,
                        false
                ),
                Optional.of(optimized)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request.withIrGpuArtifact(Optional.of(original)),
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized)),
                GpuRuntimeEquivalenceEvidence.failed(request, 2, 1, List.of("case 1 output differs"))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.artifact("runtime-equivalence.properties").contains("status=failed"));
        assertTrue(dump.artifact("runtime-equivalence.properties").contains("equivalent=false"));
        assertTrue(dump.artifact("fallback.properties").contains("decision=runtime-equivalence-failed"));
        assertTrue(dump.artifact("fallback.properties").contains("originalIrSelected=true"));
        assertTrue(dump.artifact("fallback.properties").contains("optimizedIrRejected=true"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("fallbackDecision=runtime-equivalence-failed"));
    }

    @Test
    void productionOptimizerGateBlocksVendorTunedProfilesUntilA1A2EvidenceExists() {
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 2; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "vendor-tuned"),
                GpuRuntimeDeviceProfile.openCl(
                        "OpenCL",
                        "Mock NVIDIA GPU",
                        "NVIDIA Corporation",
                        "Mock Driver",
                        "OpenCL 3.0 Mock",
                        48L,
                        65_536L,
                        512L,
                        1L,
                        true,
                        true,
                        false
                ),
                Optional.of(optimized)
        );
        GpuRuntimeIrOptimizationReport optimizationReport = new GpuRuntimeIrOptimizationReport(
                Optional.of(optimized),
                List.of(),
                GpuOptimizationStrategy.advisoryDefault().select(request)
        );

        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                optimizationReport,
                GpuRuntimeEquivalenceEvidence.passed(request, 2, 2, List.of("mock equivalence passed"))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("status=blocked"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("productionProfileRequested=true"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("runtimeEquivalencePassed=true"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("fallbackClean=true"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("strategyEvidenceBacked=false"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("vendorPromotionEligible=false"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("diagnostic.0=optimization strategy must be evidence-backed and non-advisory"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("diagnostic.1=vendor baseline is not promotion-eligible under A1/A2 gates"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.count=0"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("fallbackDecision=none"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("strategyName=strategy:opencl-nvidia-advisory"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("selectedProfile=vendor-tuned"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("productionGateStatus=blocked"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("productionProfileRequested=true"));
        assertTrue(dump.artifact("optimizer-report.txt").contains("strategy:opencl-nvidia-advisory"));
    }

    @Test
    void optimizerDriftArtifactCapturesRollbackAndFallbackCounts() {
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 2; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "fast"),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeIrOptimizationReport optimizationReport = new GpuRuntimeIrOptimizationReport(
                Optional.of(optimized),
                List.of(
                        GpuRuntimeIrOptimizationPassReport.applied(
                                "optimizer:applied",
                                "irgpu:sha256:original",
                                "irgpu:sha256:optimized",
                                "proof:mocked",
                                List.of("applied safe transform")
                        ),
                        GpuRuntimeIrOptimizationPassReport.rolledBack(
                                "optimizer:rollback",
                                "irgpu:sha256:optimized",
                                "irgpu:sha256:unsafe",
                                "proof:failed",
                                "unsafe proof",
                                List.of("rolled back unsafe transform")
                        )
                ),
                GpuOptimizationStrategyDecision.none(request)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                optimizationReport
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.count=2"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.applied.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.rolledBack.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.failed.count=0"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("fallbackDecision=optimizer-rollback"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("strategyName=strategy:none"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("productionGateStatus=not-requested"));
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
                        List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "jtg_kernel",
                                body,
                                List.of(),
                                location()
                        ))
                ),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuSourceLocation location() {
        return new IrGpuSourceLocation("java-source", "sample.Demo", "kernel", 4, 17, 7, 5);
    }
}
