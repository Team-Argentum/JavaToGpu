package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendExecutionPipelineTest {

    @Test
    void executionPipelineRunsCompilePrepareInvokeAndReturnsStageReceipts() {
        GpuKernelDescriptor descriptor = sampleDescriptor();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL")
        );
        GpuBackendModuleArtifact moduleArtifact = GpuBackendModuleArtifact.openClSource(
                descriptor.kernelSource(),
                descriptor.kernelResource(),
                "test-lowerer"
        );
        GpuBackendLoweringResult loweringResult = GpuBackendLoweringResult.succeeded(
                moduleArtifact,
                GpuBackendSourceSelectionPlan.descriptorSource(
                        GpuBackendTarget.OPENCL,
                        "opencl-c",
                        "test descriptor source"
                ),
                List.of("lowered for test")
        );
        List<String> order = new ArrayList<>();
        GpuBackendKernelCompiler<FakeCompiledKernel> compiler = new GpuBackendKernelCompiler<>() {
            @Override
            public GpuBackendTarget backendTarget() {
                return GpuBackendTarget.OPENCL;
            }

            @Override
            public FakeCompiledKernel compile(
                    GpuRuntimeCompileRequest request,
                    GpuBackendModuleArtifact module
            ) {
                order.add("compile");
                assertSame(compileRequest, request);
                assertSame(moduleArtifact, module);
                return new FakeCompiledKernel(
                        descriptor,
                        "cache:test",
                        GpuRuntimeCompileArtifactSnapshot.from(request, request, module)
                );
            }
        };
        GpuBackendKernelPreparer<FakeCompiledKernel, FakePreparedKernel, String> preparer =
                new GpuBackendKernelPreparer<>() {
                    @Override
                    public GpuBackendTarget backendTarget() {
                        return GpuBackendTarget.OPENCL;
                    }

                    @Override
                    public FakePreparedKernel prepare(FakeCompiledKernel compiledKernel, String executionPlan) {
                        order.add("prepare");
                        assertEquals("test-plan", executionPlan);
                        return new FakePreparedKernel(
                                compiledKernel,
                                new GpuRuntimeInvocationBindingSummary(1, 1, 2, 4),
                                null,
                                1
                        );
                    }
                };
        GpuBackendKernelInvoker<FakePreparedKernel> invoker = new GpuBackendKernelInvoker<>() {
            @Override
            public GpuBackendTarget backendTarget() {
                return GpuBackendTarget.OPENCL;
            }

            @Override
            public void invoke(FakePreparedKernel preparedKernel, GpuExecutionConfig executionConfig) {
                order.add("invoke");
                assertEquals("8", executionConfig.globalShape());
                assertEquals(1, preparedKernel.readbackRequiredCount());
            }
        };
        GpuBackendExecutionPipeline<FakeCompiledKernel, FakePreparedKernel, String> pipeline =
                new GpuBackendExecutionPipeline<>(compiler, preparer, invoker);

        GpuBackendExecutionPipelineResult<FakeCompiledKernel, FakePreparedKernel> result = pipeline.execute(
                compileRequest,
                loweringResult,
                moduleArtifact,
                "test-plan",
                GpuExecutionConfig.oneDimensional(8L, 4L)
        );
        Map<String, String> fields = result.artifactFields("pipeline");

        assertEquals(GpuBackendTarget.OPENCL, pipeline.backendTarget());
        assertEquals(List.of("compile", "prepare", "invoke"), order);
        assertTrue(result.succeeded());
        assertEquals("SUCCEEDED", result.compilationResult().stageResult().status().name());
        assertEquals("SUCCEEDED", result.preparationResult().stageResult().status().name());
        assertEquals("SUCCEEDED", result.invocationResult().stageResult().status().name());
        assertEquals("true", fields.get("runtime.backend.executionPipeline.succeeded"));
        assertEquals("true", fields.get("runtime.backend.compilation.compiled"));
        assertEquals("true", fields.get("runtime.backend.prepare.prepared"));
        assertEquals("true", fields.get("runtime.backend.invoke.invoked"));
        assertEquals("true", fields.get("runtime.backend.invoke.readback.complete"));
        assertEquals("test-kernel", fields.get("runtime.backend.compiledKernel.kind"));
        assertEquals("test-kernel", fields.get("runtime.backend.preparedKernel.kind"));
        assertEquals("OPENCL", fields.get("runtime.backend.target"));
    }

    @Test
    void executionPipelineRejectsMismatchedStageTargets() {
        GpuBackendKernelCompiler<FakeCompiledKernel> compiler = new NoOpCompiler(GpuBackendTarget.OPENCL);
        GpuBackendKernelPreparer<FakeCompiledKernel, FakePreparedKernel, String> preparer =
                new NoOpPreparer(GpuBackendTarget.CUDA);
        GpuBackendKernelInvoker<FakePreparedKernel> invoker = new NoOpInvoker(GpuBackendTarget.OPENCL);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new GpuBackendExecutionPipeline<>(compiler, preparer, invoker)
        );

        assertTrue(exception.getMessage().contains("target mismatch"));
    }

    @Test
    void executionPipelineResultCanRepresentUnsupportedBackendExecution() {
        GpuBackendModuleArtifact moduleArtifact = GpuBackendModuleArtifact.unknown();
        GpuBackendLoweringResult loweringResult = GpuBackendLoweringResult.succeeded(
                moduleArtifact,
                GpuBackendSourceSelectionPlan.descriptorSource(
                        GpuBackendTarget.CUDA,
                        "cuda-c",
                        "CUDA source is not available in this test"
                ),
                List.of("lowering preview skipped")
        );

        GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result =
                GpuBackendExecutionPipelineResult.unsupported(
                        GpuBackendTarget.CUDA,
                        loweringResult,
                        List.of("cuda-execution-not-implemented"),
                        List.of("Native CUDA execution bridge is not implemented")
                );
        Map<String, String> fields = result.artifactFields("pipeline");

        assertTrue(!result.succeeded());
        assertEquals(GpuBackendStageStatus.UNSUPPORTED, result.compilationResult().stageResult().status());
        assertEquals(GpuBackendStageStatus.SKIPPED, result.preparationResult().stageResult().status());
        assertEquals(GpuBackendStageStatus.SKIPPED, result.invocationResult().stageResult().status());
        assertEquals("cuda-execution-not-implemented", result.compilationResult().stageResult().blockers().get(0));
        assertEquals("false", fields.get("runtime.backend.executionPipeline.succeeded"));
        assertEquals("UNSUPPORTED", fields.get("runtime.backend.compilation.status"));
        assertEquals("SKIPPED", fields.get("runtime.backend.prepare.status"));
        assertEquals("SKIPPED", fields.get("runtime.backend.invoke.status"));
        assertEquals("CUDA", fields.get("runtime.backend.target"));
    }

    @Test
    void executionPipelineExecuteSafelyCapturesCompilerFailureReceipts() {
        GpuKernelDescriptor descriptor = sampleDescriptor();
        GpuRuntimeCompileRequest compileRequest = sampleCompileRequest(descriptor);
        GpuBackendModuleArtifact moduleArtifact = sampleModuleArtifact(descriptor);
        GpuBackendLoweringResult loweringResult = sampleLoweringResult(moduleArtifact);
        List<String> order = new ArrayList<>();
        GpuBackendKernelCompiler<FakeCompiledKernel> compiler = new GpuBackendKernelCompiler<>() {
            @Override
            public GpuBackendTarget backendTarget() {
                return GpuBackendTarget.OPENCL;
            }

            @Override
            public FakeCompiledKernel compile(
                    GpuRuntimeCompileRequest request,
                    GpuBackendModuleArtifact module
            ) {
                order.add("compile");
                throw new IllegalStateException("compiler fixture failed");
            }
        };
        GpuBackendExecutionPipeline<FakeCompiledKernel, FakePreparedKernel, String> pipeline =
                new GpuBackendExecutionPipeline<>(
                        compiler,
                        new NoOpPreparer(GpuBackendTarget.OPENCL),
                        new NoOpInvoker(GpuBackendTarget.OPENCL)
                );

        GpuBackendExecutionPipelineResult<FakeCompiledKernel, FakePreparedKernel> result = pipeline.executeSafely(
                compileRequest,
                loweringResult,
                moduleArtifact,
                "test-plan",
                GpuExecutionConfig.oneDimensional(8L)
        );

        assertEquals(List.of("compile"), order);
        assertTrue(!result.succeeded());
        assertEquals(GpuBackendStageStatus.FAILED, result.compilationResult().stageResult().status());
        assertEquals(GpuBackendStageStatus.SKIPPED, result.preparationResult().stageResult().status());
        assertEquals(GpuBackendStageStatus.SKIPPED, result.invocationResult().stageResult().status());
        assertEquals("compile-stage-failed", result.preparationResult().stageResult().blockers().get(0));
        assertEquals(
                IllegalStateException.class.getName(),
                result.compilationResult().stageResult().details().get("failure.type")
        );
    }

    @Test
    void executionPipelineExecuteSafelyCapturesInvokerFailureReceipts() {
        GpuKernelDescriptor descriptor = sampleDescriptor();
        GpuRuntimeCompileRequest compileRequest = sampleCompileRequest(descriptor);
        GpuBackendModuleArtifact moduleArtifact = sampleModuleArtifact(descriptor);
        GpuBackendLoweringResult loweringResult = sampleLoweringResult(moduleArtifact);
        List<String> order = new ArrayList<>();
        GpuBackendKernelCompiler<FakeCompiledKernel> compiler = new GpuBackendKernelCompiler<>() {
            @Override
            public GpuBackendTarget backendTarget() {
                return GpuBackendTarget.OPENCL;
            }

            @Override
            public FakeCompiledKernel compile(
                    GpuRuntimeCompileRequest request,
                    GpuBackendModuleArtifact module
            ) {
                order.add("compile");
                return new FakeCompiledKernel(
                        descriptor,
                        "cache:test",
                        GpuRuntimeCompileArtifactSnapshot.from(request, request, module)
                );
            }
        };
        GpuBackendKernelPreparer<FakeCompiledKernel, FakePreparedKernel, String> preparer =
                new GpuBackendKernelPreparer<>() {
                    @Override
                    public GpuBackendTarget backendTarget() {
                        return GpuBackendTarget.OPENCL;
                    }

                    @Override
                    public FakePreparedKernel prepare(FakeCompiledKernel compiledKernel, String executionPlan) {
                        order.add("prepare");
                        return new FakePreparedKernel(
                                compiledKernel,
                                new GpuRuntimeInvocationBindingSummary(1, 0, 0, 1),
                                null,
                                1
                        );
                    }
                };
        GpuBackendKernelInvoker<FakePreparedKernel> invoker = new GpuBackendKernelInvoker<>() {
            @Override
            public GpuBackendTarget backendTarget() {
                return GpuBackendTarget.OPENCL;
            }

            @Override
            public void invoke(FakePreparedKernel preparedKernel, GpuExecutionConfig executionConfig) {
                order.add("invoke");
                throw new IllegalArgumentException("invoker fixture failed");
            }
        };
        GpuBackendExecutionPipeline<FakeCompiledKernel, FakePreparedKernel, String> pipeline =
                new GpuBackendExecutionPipeline<>(compiler, preparer, invoker);

        GpuBackendExecutionPipelineResult<FakeCompiledKernel, FakePreparedKernel> result = pipeline.executeSafely(
                compileRequest,
                loweringResult,
                moduleArtifact,
                "test-plan",
                GpuExecutionConfig.oneDimensional(8L)
        );

        assertEquals(List.of("compile", "prepare", "invoke"), order);
        assertTrue(!result.succeeded());
        assertTrue(result.compilationResult().compiled());
        assertTrue(result.preparationResult().prepared());
        assertEquals(GpuBackendStageStatus.FAILED, result.invocationResult().stageResult().status());
        assertEquals(1, result.invocationResult().readbackRequiredCount());
        assertEquals(0, result.invocationResult().readbackCompletedCount());
        assertEquals(
                IllegalArgumentException.class.getName(),
                result.invocationResult().stageResult().details().get("failure.type")
        );
    }

    @Test
    void executionPipelineResultClosesPreparedThenCompiledHandles() {
        List<String> closeOrder = new ArrayList<>();
        CloseableCompiledKernel compiledKernel = new CloseableCompiledKernel(sampleDescriptor(), closeOrder);
        CloseablePreparedKernel preparedKernel = new CloseablePreparedKernel(compiledKernel, closeOrder);
        GpuBackendExecutionPipelineResult<CloseableCompiledKernel, CloseablePreparedKernel> result =
                new GpuBackendExecutionPipelineResult<>(
                        compiledKernel,
                        preparedKernel,
                        GpuBackendCompilationResult.succeeded(
                                sampleLoweringResult(sampleModuleArtifact(sampleDescriptor())),
                                new GpuRuntimeBackendCompilationSummary(true, true, "opencl-c", false, 0, 0),
                                compiledKernel.cacheKey(),
                                List.of("compiled")
                        ),
                        GpuBackendPreparationResult.prepared(
                                null,
                                preparedKernel.preparedKernelKind(),
                                preparedKernel.bindingSummary(),
                                List.of("prepared")
                        ),
                        null
                );

        result.close();

        assertEquals(List.of("prepared", "compiled"), closeOrder);
    }

    private static GpuKernelDescriptor sampleDescriptor() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
    }

    private static GpuRuntimeCompileRequest sampleCompileRequest(GpuKernelDescriptor descriptor) {
        return new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL")
        );
    }

    private static GpuBackendModuleArtifact sampleModuleArtifact(GpuKernelDescriptor descriptor) {
        return GpuBackendModuleArtifact.openClSource(
                descriptor.kernelSource(),
                descriptor.kernelResource(),
                "test-lowerer"
        );
    }

    private static GpuBackendLoweringResult sampleLoweringResult(GpuBackendModuleArtifact moduleArtifact) {
        return GpuBackendLoweringResult.succeeded(
                moduleArtifact,
                GpuBackendSourceSelectionPlan.descriptorSource(
                        GpuBackendTarget.OPENCL,
                        "opencl-c",
                        "test descriptor source"
                ),
                List.of("lowered for test")
        );
    }

    private record FakeCompiledKernel(
            GpuKernelDescriptor descriptor,
            String cacheKey,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot
    ) implements GpuBackendCompiledKernel {
        @Override
        public String compiledKernelKind() {
            return "test-kernel";
        }
    }

    private record FakePreparedKernel(
            GpuBackendCompiledKernel compiledKernel,
            GpuRuntimeInvocationBindingSummary bindingSummary,
            GpuExecutionConfig explicitExecutionConfig,
            int readbackRequiredCount
    ) implements GpuPreparedKernel {
        @Override
        public String preparedKernelKind() {
            return "test-kernel";
        }
    }

    private static final class CloseableCompiledKernel implements GpuBackendCompiledKernel {
        private final GpuKernelDescriptor descriptor;
        private final List<String> closeOrder;

        private CloseableCompiledKernel(GpuKernelDescriptor descriptor, List<String> closeOrder) {
            this.descriptor = descriptor;
            this.closeOrder = closeOrder;
        }

        @Override
        public GpuKernelDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public String cacheKey() {
            return "cache:closeable";
        }

        @Override
        public GpuRuntimeCompileArtifactSnapshot artifactSnapshot() {
            GpuRuntimeCompileRequest request = sampleCompileRequest(descriptor);
            return GpuRuntimeCompileArtifactSnapshot.from(request, request, sampleModuleArtifact(descriptor));
        }

        @Override
        public void close() {
            closeOrder.add("compiled");
        }
    }

    private record CloseablePreparedKernel(
            GpuBackendCompiledKernel compiledKernel,
            List<String> closeOrder
    ) implements GpuPreparedKernel {
        @Override
        public GpuRuntimeInvocationBindingSummary bindingSummary() {
            return GpuRuntimeInvocationBindingSummary.empty();
        }

        @Override
        public GpuExecutionConfig explicitExecutionConfig() {
            return null;
        }

        @Override
        public void close() {
            closeOrder.add("prepared");
        }
    }

    private record NoOpCompiler(GpuBackendTarget backendTarget) implements GpuBackendKernelCompiler<FakeCompiledKernel> {
        @Override
        public FakeCompiledKernel compile(GpuRuntimeCompileRequest compileRequest, GpuBackendModuleArtifact moduleArtifact) {
            return null;
        }
    }

    private record NoOpPreparer(GpuBackendTarget backendTarget) implements GpuBackendKernelPreparer<FakeCompiledKernel, FakePreparedKernel, String> {
        @Override
        public FakePreparedKernel prepare(FakeCompiledKernel compiledKernel, String executionPlan) {
            return null;
        }
    }

    private record NoOpInvoker(GpuBackendTarget backendTarget) implements GpuBackendKernelInvoker<FakePreparedKernel> {
        @Override
        public void invoke(FakePreparedKernel preparedKernel, GpuExecutionConfig executionConfig) {
        }
    }
}
