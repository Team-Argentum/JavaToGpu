package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendCompilerFeedbackRegistryTest {

    @Test
    void parsesNvidiaRegistersSpillsAndOccupancy() {
        GpuBackendCompilerFeedbackReport report = genericRegistry().inspect(request("""
                ptxas info    : Function properties for jtg_kernel
                    0 bytes stack frame, 0 bytes spill stores, 0 bytes spill loads
                ptxas info    : Used 32 registers, 384 bytes cmem[0]
                Occupancy: 75%
                """));

        GpuBackendCompilerFeedback feedback = report.selected().orElseThrow();
        assertEquals("jtg_kernel", feedback.kernelName());
        assertEquals(32, feedback.generalRegisterCount());
        assertEquals(32, feedback.effectiveRegisterCount());
        assertEquals(0, feedback.spillStoreBytes());
        assertEquals(0, feedback.spillLoadBytes());
        assertEquals(0, feedback.stackFrameBytes());
        assertEquals(750, feedback.occupancyPermille());
    }

    @Test
    void keepsAmdVectorAndScalarRegisterFilesSeparate() {
        GpuBackendCompilerFeedback feedback = genericRegistry().inspect(request("""
                VGPRs: 24
                SGPRs: 32
                Scratch: 128 bytes
                Occupancy: 62.5%
                """)).selected().orElseThrow();

        assertEquals(GpuBackendCompilerFeedback.UNKNOWN, feedback.generalRegisterCount());
        assertEquals(24, feedback.vectorRegisterCount());
        assertEquals(32, feedback.scalarRegisterCount());
        assertEquals(24, feedback.effectiveRegisterCount());
        assertEquals(128, feedback.stackFrameBytes());
        assertEquals(625, feedback.occupancyPermille());
    }

    @Test
    void parsesGenericIntelStyleRegisterAndSpillMetrics() {
        GpuBackendCompilerFeedback feedback = genericRegistry().inspect(request("""
                registers: 16
                spill size: 0
                local memory: 256 bytes
                """)).selected().orElseThrow();

        assertEquals(16, feedback.generalRegisterCount());
        assertEquals(0, feedback.spillStoreBytes());
        assertEquals(256, feedback.localMemoryBytes());
    }

    @Test
    void returnsUnavailableWhenNoResourceMetricsAreRecognized() {
        GpuBackendCompilerFeedbackReport report = genericRegistry().inspect(request(
                "backend compilation completed successfully"
        ));

        assertFalse(report.available());
        assertTrue(report.feedback().isEmpty());
        assertEquals("unavailable", report.artifactFields().get("status"));
    }

    @Test
    void persistsDiagnosticCompilationStatusWithoutResourceMetrics() {
        GpuBackendCompilerFeedbackReport report = genericRegistry().inspect(request("""
                [javatogpu-opencl-compiler-diagnostics]
                status=completed-empty
                source=nvidia-default
                options=-cl-nv-verbose
                diagnostic=diagnostic compilation completed but the driver returned an empty build log
                """));

        assertFalse(report.available());
        assertEquals("true", report.artifactFields().get("diagnosticCompilation.present"));
        assertEquals("completed-empty", report.artifactFields().get("diagnosticCompilation.status"));
        assertEquals("nvidia-default", report.artifactFields().get("diagnosticCompilation.source"));
        assertEquals("-cl-nv-verbose", report.artifactFields().get("diagnosticCompilation.options"));
    }

    @Test
    void parsesStandardOpenClKernelResourceMetrics() {
        GpuBackendCompilerFeedback feedback = genericRegistry().inspect(request("""
                [javatogpu-opencl-kernel-resource-info]
                status=recorded
                max work-group size: 1024
                preferred work-group size multiple: 32
                local memory: 2048 bytes
                private memory: 96 bytes
                """)).selected().orElseThrow();

        assertEquals(2_048, feedback.localMemoryBytes());
        assertEquals("96", feedback.rawFields().get("privateMemoryBytes"));
        assertEquals("1024", feedback.rawFields().get("maxWorkGroupSize"));
        assertEquals("32", feedback.rawFields().get("preferredWorkGroupSizeMultiple"));
        assertEquals(GpuBackendCompilerFeedback.UNKNOWN, feedback.effectiveRegisterCount());
    }

    @Test
    void isolatesFailingProviderAndContinuesWithGenericParser() {
        GpuBackendCompilerFeedbackRegistry registry = GpuBackendCompilerFeedbackRegistry.of(List.of(
                new FailingProvider("compiler-feedback:failing"),
                new GpuGenericCompilerFeedbackProvider()
        ));

        GpuBackendCompilerFeedbackReport report = registry.inspect(request("Used 20 registers"));

        assertTrue(report.available());
        assertEquals(20, report.selected().orElseThrow().effectiveRegisterCount());
        assertEquals(2, report.executions().size());
        assertTrue(report.executions().get(0).pipelineContinued());
        assertTrue(report.executions().get(0).failureType().contains("IllegalStateException"));
    }

    @Test
    void rejectsDuplicateProviderIds() {
        assertThrows(IllegalArgumentException.class, () -> GpuBackendCompilerFeedbackRegistry.of(List.of(
                new FailingProvider("compiler-feedback:duplicate"),
                new FailingProvider("compiler-feedback:duplicate")
        )));
    }

    @Test
    void dumperEmitsCompilerFeedbackAndHeuristicComparison() {
        GpuRuntimeCompileArtifactSnapshot snapshot = snapshot(
                "Used 32 registers, 8 bytes spill stores, 4 bytes spill loads",
                registerPressureReport(28)
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot, genericRegistry());
        String feedbackArtifact = dump.artifact(GpuRuntimeCompileArtifactDumper.BACKEND_COMPILER_FEEDBACK_ARTIFACT);
        String analysisArtifact = dump.artifact("runtime-ir-analysis.properties");

        assertTrue(feedbackArtifact.contains("status=recorded"));
        assertTrue(feedbackArtifact.contains("selected.register.general=32"));
        assertTrue(feedbackArtifact.contains("selected.spill.knownBytes=12"));
        assertTrue(analysisArtifact.contains("compilerFeedback.registerPressure.heuristicEstimatedValueRegisters=28"));
        assertTrue(analysisArtifact.contains("compilerFeedback.registerPressure.compilerEffectiveRegisters=32"));
        assertTrue(analysisArtifact.contains("compilerFeedback.registerPressure.delta=4"));
        assertTrue(analysisArtifact.contains("compilerFeedback.registerPressure.comparisonStatus=heuristic-underestimated"));
    }

    @Test
    void emptyCompileLogPreservesHeuristicAnalysisWithoutCompilerComparison() {
        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(
                snapshot("", registerPressureReport(24)),
                genericRegistry()
        );
        String feedbackArtifact = dump.artifact(GpuRuntimeCompileArtifactDumper.BACKEND_COMPILER_FEEDBACK_ARTIFACT);
        String analysisArtifact = dump.artifact("runtime-ir-analysis.properties");

        assertTrue(feedbackArtifact.contains("status=unavailable"));
        assertTrue(feedbackArtifact.contains("compileLogAvailable=false"));
        assertTrue(analysisArtifact.contains("analysis.0.field.registerPressure.estimatedValueRegisters=24"));
        assertFalse(analysisArtifact.contains("compilerFeedback.selected"));
    }

    private static GpuBackendCompilerFeedbackRegistry genericRegistry() {
        return GpuBackendCompilerFeedbackRegistry.of(List.of(new GpuGenericCompilerFeedbackProvider()));
    }

    private static GpuBackendCompilerFeedbackRequest request(String compileLog) {
        return new GpuBackendCompilerFeedbackRequest(
                GpuBackendTarget.OPENCL,
                "opencl-c",
                "runtime/kernel.cl",
                compileLog
        );
    }

    private static GpuRuntimeIrOptimizationReport registerPressureReport(int estimate) {
        GpuRuntimeIrOptimizationPassReport passReport = new GpuRuntimeIrOptimizationPassReport(
                GpuRuntimeIrOptimizationStage.TARGET_PROFILE_ANALYSIS,
                "register-pressure:test",
                GpuRuntimeIrOptimizationOutcome.SKIPPED,
                "irgpu:test",
                "irgpu:test",
                "analysis-only",
                "",
                GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "register-pressure",
                        "recorded",
                        Map.of(
                                "analysisOnly", "true",
                                "analysisKind", "register-pressure",
                                "registerPressure.estimatedValueRegisters", Integer.toString(estimate)
                        )
                ),
                List.of()
        );
        return new GpuRuntimeIrOptimizationReport(Optional.<IrGpuArtifact>empty(), List.of(passReport));
    }

    private static GpuRuntimeCompileArtifactSnapshot snapshot(
            String compileLog,
            GpuRuntimeIrOptimizationReport optimizationReport
    ) {
        GpuBackendModuleArtifact module = GpuBackendModuleArtifact.openClSource(
                "__kernel void jtg_kernel(__global float* output) { output[0] = 1.0f; }",
                "runtime/kernel.cl",
                "test-lowerer:v1"
        );
        return new GpuRuntimeCompileArtifactSnapshot(
                Optional.empty(),
                Optional.empty(),
                module,
                null,
                null,
                optimizationReport,
                null,
                null,
                null,
                null,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                compileLog,
                List.of(),
                Optional.empty()
        );
    }

    private record FailingProvider(String id) implements GpuBackendCompilerFeedbackProvider {

        @Override
        public Optional<GpuBackendCompilerFeedback> inspect(GpuBackendCompilerFeedbackRequest request) {
            throw new IllegalStateException("synthetic provider failure");
        }

        @Override
        public String extensionId() {
            return id;
        }
    }
}
