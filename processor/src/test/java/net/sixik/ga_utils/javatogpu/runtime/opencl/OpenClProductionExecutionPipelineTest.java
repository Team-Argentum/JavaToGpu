package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipelineResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelCompiler;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelInvoker;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelPreparer;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactSnapshot;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClProductionExecutionPipelineTest {

    @Test
    void executesCompilePrepareInvokeThroughSharedPipelineAndPreservesReceipts() {
        List<String> events = new ArrayList<>();
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "testKernel",
                "test/TestKernel.cl",
                "__kernel void testKernel(__global int* output) { output[0] = 1; }",
                List.of()
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL")
        );
        GpuBackendModuleArtifact moduleArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void testKernel(__global int* output) { output[0] = 1; }",
                "test/TestKernel.cl",
                "test-opencl-lowerer"
        );
        OpenClCompiledKernel compiledKernel = new OpenClCompiledKernel(
                descriptor,
                "compiled:testKernel",
                GpuRuntimeCompileArtifactSnapshot.from(compileRequest, compileRequest, moduleArtifact),
                null,
                null
        );
        GpuExecutionConfig executionConfig = GpuExecutionConfig.oneDimensional(4L);
        OpenClPreparedExecution preparedExecution = new OpenClPreparedExecution(
                compiledKernel,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                executionConfig
        );
        OpenClExecutionPlan executionPlan = new OpenClExecutionPlan(List.of(), List.of(), List.of(), List.of());
        RecordingCompiler compiler = new RecordingCompiler(events, compiledKernel);
        RecordingPreparer preparer = new RecordingPreparer(events, preparedExecution);
        RecordingInvoker invoker = new RecordingInvoker(events);

        OpenClProductionExecutionPipeline pipeline = new OpenClProductionExecutionPipeline(
                compiler,
                preparer,
                invoker
        );

        GpuBackendExecutionPipelineResult<OpenClCompiledKernel, OpenClPreparedExecution> result = pipeline.execute(
                compileRequest,
                moduleArtifact,
                executionPlan,
                executionConfig
        );

        assertEquals(List.of("compile", "prepare", "invoke"), events);
        assertSame(compileRequest, compiler.compileRequest);
        assertSame(moduleArtifact, compiler.moduleArtifact);
        assertSame(compiledKernel, preparer.compiledKernel);
        assertSame(executionPlan, preparer.executionPlan);
        assertSame(preparedExecution, invoker.preparedExecution);
        assertSame(executionConfig, invoker.executionConfig);
        assertTrue(result.succeeded());
        assertSame(compiledKernel, result.compiledKernel());
        assertSame(preparedExecution, result.preparedKernel());
        assertSame(moduleArtifact, result.compilationResult().loweringResult().moduleArtifact());
        Map<String, String> fields = result.artifactFields("pipeline");
        assertEquals("true", fields.get("runtime.backend.executionPipeline.succeeded"));
        assertEquals("SUCCEEDED", fields.get("runtime.backend.compilation.status"));
        assertEquals("SUCCEEDED", fields.get("runtime.backend.prepare.status"));
        assertEquals("SUCCEEDED", fields.get("runtime.backend.invoke.status"));
        assertEquals("true", fields.get("runtime.backend.compiledKernel.present"));
        assertEquals("true", fields.get("runtime.backend.preparedKernel.present"));
        assertEquals("OPENCL", fields.get("runtime.backend.target"));
    }

    private static final class RecordingCompiler implements GpuBackendKernelCompiler<OpenClCompiledKernel> {
        private final List<String> events;
        private final OpenClCompiledKernel compiledKernel;
        private GpuRuntimeCompileRequest compileRequest;
        private GpuBackendModuleArtifact moduleArtifact;

        private RecordingCompiler(List<String> events, OpenClCompiledKernel compiledKernel) {
            this.events = events;
            this.compiledKernel = compiledKernel;
        }

        @Override
        public GpuBackendTarget backendTarget() {
            return GpuBackendTarget.OPENCL;
        }

        @Override
        public OpenClCompiledKernel compile(
                GpuRuntimeCompileRequest compileRequest,
                GpuBackendModuleArtifact moduleArtifact
        ) {
            events.add("compile");
            this.compileRequest = compileRequest;
            this.moduleArtifact = moduleArtifact;
            return compiledKernel;
        }
    }

    private static final class RecordingPreparer implements GpuBackendKernelPreparer<
            OpenClCompiledKernel,
            OpenClPreparedExecution,
            OpenClExecutionPlan> {
        private final List<String> events;
        private final OpenClPreparedExecution preparedExecution;
        private OpenClCompiledKernel compiledKernel;
        private OpenClExecutionPlan executionPlan;

        private RecordingPreparer(List<String> events, OpenClPreparedExecution preparedExecution) {
            this.events = events;
            this.preparedExecution = preparedExecution;
        }

        @Override
        public GpuBackendTarget backendTarget() {
            return GpuBackendTarget.OPENCL;
        }

        @Override
        public OpenClPreparedExecution prepare(OpenClCompiledKernel compiledKernel, OpenClExecutionPlan executionPlan) {
            events.add("prepare");
            this.compiledKernel = compiledKernel;
            this.executionPlan = executionPlan;
            return preparedExecution;
        }
    }

    private static final class RecordingInvoker implements GpuBackendKernelInvoker<OpenClPreparedExecution> {
        private final List<String> events;
        private OpenClPreparedExecution preparedExecution;
        private GpuExecutionConfig executionConfig;

        private RecordingInvoker(List<String> events) {
            this.events = events;
        }

        @Override
        public GpuBackendTarget backendTarget() {
            return GpuBackendTarget.OPENCL;
        }

        @Override
        public void invoke(OpenClPreparedExecution preparedExecution, GpuExecutionConfig executionConfig) {
            events.add("invoke");
            this.preparedExecution = preparedExecution;
            this.executionConfig = executionConfig;
        }
    }
}
