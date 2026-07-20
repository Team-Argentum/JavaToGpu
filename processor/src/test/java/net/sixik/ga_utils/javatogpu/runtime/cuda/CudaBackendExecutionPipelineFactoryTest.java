package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.CudaRuntimeBackendProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompiledKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipeline;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipelineFactory;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipelineResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLoweringResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleFormat;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceSelectionPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendStageStatus;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuPreparedKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEvent;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEventBus;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEventKind;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaBackendExecutionPipelineFactoryTest {

    @Test
    void skeletonPipelineCompilesPreviewAndFailsClosedAtNativePrepareBoundary() {
        CudaRuntimeBackendProvider provider = new CudaRuntimeBackendProvider();
        GpuBackendExecutionPipelineFactory<?, ?, ?> genericFactory = provider.executionPipelineFactory().orElseThrow();
        assertEquals("backend-execution-pipeline:cuda-preview", genericFactory.factoryId());

        CudaBackendExecutionPipelineFactory factory = new CudaBackendExecutionPipelineFactory();
        GpuBackendExecutionPipeline<GpuBackendCompiledKernel, GpuPreparedKernel, CudaExecutionPlan> pipeline =
                factory.createPipeline(new CudaGpuRuntimeBackend());
        GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result = pipeline.executeSafely(
                compileRequest(),
                loweringResult(),
                loweringResult().moduleArtifact(),
                CudaExecutionPlan.empty(),
                null
        );

        assertEquals(GpuBackendTarget.CUDA, pipeline.backendTarget());
        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.compilationResult().stageResult().status());
        assertTrue(result.compilationResult().compiled());
        assertTrue(result.compiledKernel() instanceof CudaCompiledKernel);
        CudaCompiledKernel compiledKernel = (CudaCompiledKernel) result.compiledKernel();
        assertEquals("cuda-compile-preview", compiledKernel.compiledKernelKind());
        assertTrue(!compiledKernel.nativeHandleAvailable());
        assertEquals(GpuBackendStageStatus.UNSUPPORTED, result.preparationResult().stageResult().status());
        assertTrue(result.preparationResult().stageResult().blockers()
                .contains("cuda-native-argument-binding-missing"));
        assertEquals(GpuBackendStageStatus.SKIPPED, result.invocationResult().stageResult().status());
        assertTrue(result.invocationResult().stageResult().blockers()
                .contains("cuda-prepare-stage-not-available"));
        assertTrue(result.artifactFields("cuda.pipeline").get("runtime.backend.compilation.status")
                .equals("SUCCEEDED"));
        assertEquals("source-preview", result.artifactFields("cuda.pipeline")
                .get("runtime.cuda.compiledKernel.compilerBridge.mode"));
        assertEquals("false", result.artifactFields("cuda.pipeline")
                .get("runtime.cuda.compiledKernel.nativeHandle.available"));
    }

    @Test
    void nativeCompilerBridgeCanProducePtxArtifactBeforePrepareFailsClosed() {
        GpuBackendExecutionPipeline<GpuBackendCompiledKernel, GpuPreparedKernel, CudaExecutionPlan> pipeline =
                new GpuBackendExecutionPipeline<>(
                        new CudaKernelCompiler(CudaNativeCompilerBridgeRegistry.of(List.of(new TestPtxBridge()))),
                        new CudaKernelPreparer(),
                        new CudaKernelInvoker()
                );
        GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result = pipeline.executeSafely(
                nativeCompileRequest(),
                loweringResult(),
                loweringResult().moduleArtifact(),
                CudaExecutionPlan.empty(),
                null
        );
        Map<String, String> fields = result.artifactFields("cuda.pipeline");

        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.compilationResult().stageResult().status());
        assertTrue(result.compiledKernel() instanceof CudaCompiledKernel);
        CudaCompiledKernel compiledKernel = (CudaCompiledKernel) result.compiledKernel();
        assertEquals(GpuBackendModuleFormat.PTX.key(), compiledKernel.moduleArtifact().format());
        assertEquals("cuda-native-compiler:test-ptx", compiledKernel.compilerBridgeMode());
        assertTrue(!compiledKernel.nativeHandleAvailable());
        assertEquals("ptx", fields.get("runtime.backend.compiledKernel.module.format.canonical"));
        assertEquals("succeeded", fields.get("runtime.cuda.nativeCompilation.status"));
        assertEquals(GpuBackendStageStatus.UNSUPPORTED, result.preparationResult().stageResult().status());
        assertTrue(result.preparationResult().stageResult().blockers()
                .contains("cuda-native-argument-binding-missing"));
    }

    @Test
    void moduleLoaderBridgeCanPrepareFunctionHandleBeforeLaunchFailsClosed() {
        GpuBackendExecutionPipeline<GpuBackendCompiledKernel, GpuPreparedKernel, CudaExecutionPlan> pipeline =
                new GpuBackendExecutionPipeline<>(
                        new CudaKernelCompiler(CudaNativeCompilerBridgeRegistry.of(List.of(new TestPtxBridge()))),
                        new CudaKernelPreparer(CudaModuleLoaderBridgeRegistry.of(List.of(new TestModuleLoader()))),
                        new CudaKernelInvoker()
                );
        GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result = pipeline.executeSafely(
                nativeCompileAndLoadRequest(),
                loweringResult(),
                loweringResult().moduleArtifact(),
                CudaExecutionPlan.empty(),
                null
        );
        Map<String, String> fields = result.artifactFields("cuda.pipeline");

        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.compilationResult().stageResult().status());
        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.preparationResult().stageResult().status());
        assertTrue(result.preparedKernel() instanceof CudaPreparedKernel);
        assertEquals("cuda-module-function-preview", result.preparedKernel().preparedKernelKind());
        assertEquals("succeeded", fields.get("runtime.cuda.moduleLoad.status"));
        assertEquals("test-module-handle", fields.get("runtime.cuda.preparedKernel.moduleHandle.kind"));
        assertEquals("test-function-handle", fields.get("runtime.cuda.preparedKernel.functionHandle.kind"));
        assertEquals(GpuBackendStageStatus.UNSUPPORTED, result.invocationResult().stageResult().status());
        assertTrue(result.invocationResult().stageResult().blockers()
                .contains("cuda-native-kernel-launch-missing"));
    }

    @Test
    void kernelLauncherBridgeCanSubmitBoundKernelBeforeReadbackExists() {
        GpuBackendExecutionPipeline<GpuBackendCompiledKernel, GpuPreparedKernel, CudaExecutionPlan> pipeline =
                new GpuBackendExecutionPipeline<>(
                        new CudaKernelCompiler(CudaNativeCompilerBridgeRegistry.of(List.of(new TestPtxBridge()))),
                        new CudaKernelPreparer(
                                CudaModuleLoaderBridgeRegistry.of(List.of(new TestModuleLoader())),
                                CudaArgumentBinderBridgeRegistry.of(List.of(new TestArgumentBinder()))
                        ),
                        new CudaKernelInvoker(CudaKernelLauncherBridgeRegistry.of(List.of(new TestKernelLauncher())))
                );
        GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result = pipeline.executeSafely(
                nativeCompileLoadBindAndLaunchRequest(),
                loweringResult(),
                loweringResult().moduleArtifact(),
                CudaExecutionPlan.empty(),
                GpuExecutionConfig.oneDimensional(64L, 16L)
        );
        Map<String, String> fields = result.artifactFields("cuda.pipeline");

        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.compilationResult().stageResult().status());
        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.preparationResult().stageResult().status());
        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.invocationResult().stageResult().status());
        assertTrue(result.succeeded());
        assertEquals("succeeded", fields.get("runtime.cuda.kernelLaunch.status"));
        assertEquals("true", fields.get("runtime.cuda.kernelLaunch.submitted"));
        assertEquals("true", fields.get("runtime.cuda.kernelLaunch.sharedMemory.present"));
        assertEquals("16", fields.get("runtime.cuda.kernelLaunch.sharedMemory.byteSize"));
        assertEquals("64", fields.get("runtime.cuda.kernelLaunch.work.globalShape"));
        assertEquals("16", fields.get("runtime.cuda.kernelLaunch.work.localShape"));
        assertEquals("16", fields.get("runtime.cuda.argumentFrame.localSharedMemory.byteSize"));
        assertEquals("3", fields.get("runtime.cuda.argumentFrame.kernelParameterSlot.count"));
        assertEquals("64", fields.get("runtime.backend.invoke.work.globalShape"));
        assertEquals("16", fields.get("runtime.backend.invoke.work.localShape"));
        assertEquals("true", fields.get("runtime.backend.executionPipeline.succeeded"));
    }

    @Test
    void readbackBridgeCanCompleteLaunchedKernelReadbackReceipt() {
        GpuBackendExecutionPipeline<GpuBackendCompiledKernel, GpuPreparedKernel, CudaExecutionPlan> pipeline =
                new GpuBackendExecutionPipeline<>(
                        new CudaKernelCompiler(CudaNativeCompilerBridgeRegistry.of(List.of(new TestPtxBridge()))),
                        new CudaKernelPreparer(
                                CudaModuleLoaderBridgeRegistry.of(List.of(new TestModuleLoader())),
                                CudaArgumentBinderBridgeRegistry.of(List.of(new TestArgumentBinder()))
                        ),
                        new CudaKernelInvoker(
                                CudaKernelLauncherBridgeRegistry.of(List.of(new TestKernelLauncherWithReadback())),
                                CudaKernelReadbackBridgeRegistry.of(List.of(new TestReadbackBridge()))
                        )
                );
        GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result = pipeline.executeSafely(
                nativeCompileLoadBindLaunchAndReadbackRequest(),
                loweringResult(),
                loweringResult().moduleArtifact(),
                CudaExecutionPlan.empty(),
                GpuExecutionConfig.oneDimensional(64L, 16L)
        );
        Map<String, String> fields = result.artifactFields("cuda.pipeline");

        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.compilationResult().stageResult().status());
        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.preparationResult().stageResult().status());
        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.invocationResult().stageResult().status());
        assertTrue(result.succeeded());
        assertTrue(result.invocationResult().readbackComplete());
        assertEquals("succeeded", fields.get("runtime.cuda.kernelLaunch.status"));
        assertEquals("succeeded", fields.get("runtime.cuda.readback.status"));
        assertEquals("true", fields.get("runtime.cuda.readback.complete"));
        assertEquals("1", fields.get("runtime.cuda.readback.required.count"));
        assertEquals("1", fields.get("runtime.cuda.readback.completed.count"));
        assertEquals("1", fields.get("runtime.backend.invoke.readback.required.count"));
        assertEquals("1", fields.get("runtime.backend.invoke.readback.completed.count"));
        assertEquals("true", fields.get("runtime.backend.invoke.readback.complete"));
    }

    @Test
    void cudaPipelinePublishesLifecycleEventsForCompilePrepareInvokeReceipts() {
        GpuBackendExecutionPipeline<GpuBackendCompiledKernel, GpuPreparedKernel, CudaExecutionPlan> pipeline =
                new GpuBackendExecutionPipeline<>(
                        new CudaKernelCompiler(CudaNativeCompilerBridgeRegistry.of(List.of(new TestPtxBridge()))),
                        new CudaKernelPreparer(
                                CudaModuleLoaderBridgeRegistry.of(List.of(new TestModuleLoader())),
                                CudaArgumentBinderBridgeRegistry.of(List.of(new TestArgumentBinder()))
                        ),
                        new CudaKernelInvoker(
                                CudaKernelLauncherBridgeRegistry.of(List.of(new TestKernelLauncherWithReadback())),
                                CudaKernelReadbackBridgeRegistry.of(List.of(new TestReadbackBridge()))
                        )
                );
        List<GpuRuntimeLifecycleEvent> events = new ArrayList<>();
        GpuRuntimeLifecycleEventBus eventBus = GpuRuntimeLifecycleEventBus.of(List.of(events::add));

        GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result = pipeline.executeSafely(
                nativeCompileLoadBindLaunchAndReadbackRequest(),
                loweringResult(),
                loweringResult().moduleArtifact(),
                CudaExecutionPlan.empty(),
                GpuExecutionConfig.oneDimensional(64L, 16L),
                eventBus
        );

        assertTrue(result.succeeded());
        assertEquals(
                List.of(
                        GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_STARTED,
                        GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_COMPLETED,
                        GpuRuntimeLifecycleEventKind.MODULE_LOAD_STARTED,
                        GpuRuntimeLifecycleEventKind.MODULE_LOAD_COMPLETED,
                        GpuRuntimeLifecycleEventKind.INVOCATION_STARTED,
                        GpuRuntimeLifecycleEventKind.INVOCATION_COMPLETED
                ),
                events.stream().map(GpuRuntimeLifecycleEvent::kind).toList()
        );
        assertEquals(GpuBackendTarget.CUDA, events.get(0).backendTarget());
        assertEquals("compile", events.get(0).fields().get("runtime.backend.executionPipeline.stage"));
        assertEquals("prepare", events.get(2).fields().get("runtime.backend.executionPipeline.stage"));
        assertEquals("invoke", events.get(4).fields().get("runtime.backend.executionPipeline.stage"));
        assertEquals("succeeded", events.get(5).fields().get("runtime.backend.executionPipeline.stage.status"));
        assertEquals("succeeded", events.get(5).fields().get("runtime.cuda.kernelLaunch.status"));
        assertEquals("true", events.get(5).fields().get("runtime.cuda.kernelLaunch.sharedMemory.present"));
        assertEquals("16", events.get(5).fields().get("runtime.cuda.kernelLaunch.sharedMemory.byteSize"));
        assertEquals("16", events.get(5).fields().get("runtime.cuda.argumentFrame.localSharedMemory.byteSize"));
        assertEquals("3", events.get(5).fields().get("runtime.cuda.argumentFrame.kernelParameterSlot.count"));
        assertEquals("succeeded", events.get(5).fields().get("runtime.cuda.readback.status"));
        assertEquals("true", events.get(5).fields().get("runtime.backend.invoke.readback.complete"));
    }

    @Test
    void argumentBinderBridgeCanBindDescriptorArgumentsBeforeLaunchFailsClosed() {
        GpuBackendExecutionPipeline<GpuBackendCompiledKernel, GpuPreparedKernel, CudaExecutionPlan> pipeline =
                new GpuBackendExecutionPipeline<>(
                        new CudaKernelCompiler(CudaNativeCompilerBridgeRegistry.of(List.of(new TestPtxBridge()))),
                        new CudaKernelPreparer(
                                CudaModuleLoaderBridgeRegistry.of(List.of(new TestModuleLoader())),
                                CudaArgumentBinderBridgeRegistry.of(List.of(new TestArgumentBinder()))
                        ),
                        new CudaKernelInvoker()
                );
        GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result = pipeline.executeSafely(
                nativeCompileLoadAndBindRequest(),
                loweringResult(),
                loweringResult().moduleArtifact(),
                CudaExecutionPlan.empty(),
                null
        );
        Map<String, String> fields = result.artifactFields("cuda.pipeline");

        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.compilationResult().stageResult().status());
        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.preparationResult().stageResult().status());
        assertTrue(result.preparedKernel() instanceof CudaPreparedKernel);
        assertEquals("cuda-argument-bound-preview", result.preparedKernel().preparedKernelKind());
        assertEquals("succeeded", fields.get("runtime.cuda.argumentBinding.status"));
        assertEquals("4", fields.get("runtime.backend.prepare.binding.argument.count"));
        assertEquals("2", fields.get("runtime.backend.prepare.binding.buffer.count"));
        assertEquals("1", fields.get("runtime.backend.prepare.binding.local.count"));
        assertEquals("1", fields.get("runtime.backend.prepare.binding.scalar.count"));
        assertEquals("4", fields.get("runtime.cuda.preparedKernel.argumentBinding.argument.count"));
        assertEquals(GpuBackendStageStatus.UNSUPPORTED, result.invocationResult().stageResult().status());
        assertTrue(result.invocationResult().stageResult().blockers()
                .contains("cuda-native-kernel-launch-missing"));
    }

    private static GpuRuntimeCompileRequest compileRequest() {
        return new GpuRuntimeCompileRequest(
                new GpuKernelDescriptor(
                        "jtg_cuda_preview_kernel",
                        "inline://tests/cuda-preview.cu",
                        "extern \"C\" __global__ void jtg_cuda_preview_kernel(const float* input, float* output) { }",
                        List.of()
                ),
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA")
        );
    }

    private static GpuRuntimeCompileRequest nativeCompileRequest() {
        return new GpuRuntimeCompileRequest(
                new GpuKernelDescriptor(
                        "jtg_cuda_preview_kernel",
                        "inline://tests/cuda-preview.cu",
                        "extern \"C\" __global__ void jtg_cuda_preview_kernel(const float* input, float* output) { }",
                        List.of()
                ),
                GpuRuntimeCompileOptions.cuda(
                        List.of("--gpu-architecture=compute_86"),
                        Map.of(GpuBackendCompileOptions.CUDA_COMPILER_BRIDGE_PROPERTY, "test-ptx"),
                        "off"
                ),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA")
        );
    }

    private static GpuRuntimeCompileRequest nativeCompileAndLoadRequest() {
        return new GpuRuntimeCompileRequest(
                new GpuKernelDescriptor(
                        "jtg_cuda_preview_kernel",
                        "inline://tests/cuda-preview.cu",
                        "extern \"C\" __global__ void jtg_cuda_preview_kernel(const float* input, float* output) { }",
                        List.of()
                ),
                GpuRuntimeCompileOptions.cuda(
                        List.of("--gpu-architecture=compute_86"),
                        Map.of(
                                GpuBackendCompileOptions.CUDA_COMPILER_BRIDGE_PROPERTY,
                                "test-ptx",
                                GpuBackendCompileOptions.CUDA_MODULE_LOADER_PROPERTY,
                                "test-module"
                        ),
                        "off"
                ),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA")
        );
    }

    private static GpuRuntimeCompileRequest nativeCompileLoadAndBindRequest() {
        return new GpuRuntimeCompileRequest(
                new GpuKernelDescriptor(
                        "jtg_cuda_preview_kernel",
                        "inline://tests/cuda-preview.cu",
                        "extern \"C\" __global__ void jtg_cuda_preview_kernel(const float* input, float* output) { }",
                        List.of(
                                new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                                new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE),
                                new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                                new GpuKernelParameterDescriptor("scratch", "float[]", GpuKernelParameterAccess.LOCAL)
                        )
                ),
                GpuRuntimeCompileOptions.cuda(
                        List.of("--gpu-architecture=compute_86"),
                        Map.of(
                                GpuBackendCompileOptions.CUDA_COMPILER_BRIDGE_PROPERTY,
                                "test-ptx",
                                GpuBackendCompileOptions.CUDA_MODULE_LOADER_PROPERTY,
                                "test-module",
                                GpuBackendCompileOptions.CUDA_ARGUMENT_BINDER_PROPERTY,
                                "test-binding"
                        ),
                        "off"
                ),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA")
        );
    }

    private static GpuRuntimeCompileRequest nativeCompileLoadBindAndLaunchRequest() {
        return new GpuRuntimeCompileRequest(
                new GpuKernelDescriptor(
                        "jtg_cuda_preview_kernel",
                        "inline://tests/cuda-preview.cu",
                        "extern \"C\" __global__ void jtg_cuda_preview_kernel(const float* input, float* output) { }",
                        List.of(
                                new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                                new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE),
                                new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                                new GpuKernelParameterDescriptor("scratch", "float[]", GpuKernelParameterAccess.LOCAL)
                        )
                ),
                GpuRuntimeCompileOptions.cuda(
                        List.of("--gpu-architecture=compute_86"),
                        Map.of(
                                GpuBackendCompileOptions.CUDA_COMPILER_BRIDGE_PROPERTY,
                                "test-ptx",
                                GpuBackendCompileOptions.CUDA_MODULE_LOADER_PROPERTY,
                                "test-module",
                                GpuBackendCompileOptions.CUDA_ARGUMENT_BINDER_PROPERTY,
                                "test-binding",
                                GpuBackendCompileOptions.CUDA_KERNEL_LAUNCHER_PROPERTY,
                                "test-launch"
                        ),
                        "off"
                ),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA")
        );
    }

    private static GpuRuntimeCompileRequest nativeCompileLoadBindLaunchAndReadbackRequest() {
        return new GpuRuntimeCompileRequest(
                new GpuKernelDescriptor(
                        "jtg_cuda_preview_kernel",
                        "inline://tests/cuda-preview.cu",
                        "extern \"C\" __global__ void jtg_cuda_preview_kernel(const float* input, float* output) { }",
                        List.of(
                                new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                                new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE),
                                new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                                new GpuKernelParameterDescriptor("scratch", "float[]", GpuKernelParameterAccess.LOCAL)
                        )
                ),
                GpuRuntimeCompileOptions.cuda(
                        List.of("--gpu-architecture=compute_86"),
                        Map.of(
                                GpuBackendCompileOptions.CUDA_COMPILER_BRIDGE_PROPERTY,
                                "test-ptx",
                                GpuBackendCompileOptions.CUDA_MODULE_LOADER_PROPERTY,
                                "test-module",
                                GpuBackendCompileOptions.CUDA_ARGUMENT_BINDER_PROPERTY,
                                "test-binding",
                                GpuBackendCompileOptions.CUDA_KERNEL_LAUNCHER_PROPERTY,
                                "test-launch-readback",
                                GpuBackendCompileOptions.CUDA_READBACK_PROPERTY,
                                "test-readback"
                        ),
                        "off"
                ),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA")
        );
    }

    private static GpuBackendLoweringResult loweringResult() {
        GpuBackendModuleArtifact module = GpuBackendModuleArtifact.cudaSource(
                "extern \"C\" __global__ void jtg_cuda_preview_kernel(const float* input, float* output) { }",
                "inline://tests/cuda-preview.cu",
                "test-cuda-preview-lowerer"
        );
        return GpuBackendLoweringResult.succeeded(
                module,
                GpuBackendSourceSelectionPlan.descriptorSource(
                        GpuBackendTarget.CUDA,
                        GpuBackendModuleFormat.CUDA_C.key(),
                        "test cuda source preview"
                ),
                List.of("CUDA source preview reached execution skeleton")
        );
    }

    private static final class TestPtxBridge implements CudaNativeCompilerBridge {
        @Override
        public String bridgeId() {
            return "cuda-native-compiler:test-ptx";
        }

        @Override
        public int bridgeOrder() {
            return 1;
        }

        @Override
        public boolean supports(CudaNativeCompilationRequest request) {
            return "test-ptx".equals(request.bridgeMode());
        }

        @Override
        public CudaNativeCompilationResult compile(CudaNativeCompilationRequest request) {
            return CudaNativeCompilationResult.succeeded(
                    bridgeId(),
                    GpuBackendModuleArtifact.ptx(
                            ".version 8.0\n.target sm_86\n.address_size 64\n",
                            "inline://tests/cuda-preview.ptx",
                            bridgeId()
                    ),
                    "ptxas info : synthetic PTX bridge",
                    List.of("synthetic PTX bridge emitted PTX")
            );
        }
    }

    private static final class TestModuleLoader implements CudaModuleLoaderBridge {
        @Override
        public String loaderId() {
            return "cuda-module-loader:test-module";
        }

        @Override
        public int loaderOrder() {
            return 1;
        }

        @Override
        public boolean supports(CudaModuleLoadRequest request) {
            return "test-module".equals(request.loaderMode());
        }

        @Override
        public CudaModuleLoadResult load(CudaModuleLoadRequest request) {
            return CudaModuleLoadResult.succeeded(
                    loaderId(),
                    "test-module-handle",
                    "test-function-handle",
                    List.of("synthetic CUDA module/function handle loaded")
            );
        }
    }

    private static final class TestArgumentBinder implements CudaArgumentBinderBridge {
        @Override
        public String binderId() {
            return "cuda-argument-binder:test-binding";
        }

        @Override
        public int binderOrder() {
            return 1;
        }

        @Override
        public boolean supports(CudaArgumentBindingRequest request) {
            return "test-binding".equals(request.binderMode());
        }

        @Override
        public CudaArgumentBindingResult bind(CudaArgumentBindingRequest request) {
            ByteBuffer scalarSlot = MemoryUtil.memAlloc(Float.BYTES);
            scalarSlot.putFloat(0, 1.0F);
            return CudaArgumentBindingResult.succeeded(
                    binderId(),
                    request.descriptorBindingSummary(),
                    CudaKernelArgumentFrame.nativeBindings(
                            binderId(),
                            request.descriptorBindingSummary(),
                            List.of(new CudaDriverDeviceAllocation(
                                    1,
                                    "output",
                                    "float[]",
                                    GpuKernelParameterAccess.READ_WRITE,
                                    new float[64],
                                    64,
                                    256L,
                                    0L,
                                    true,
                                    0L,
                                    NoOpDriverApiInvoker.INSTANCE
                            )),
                            MemoryUtil.memAllocPointer(3),
                            List.of(MemoryUtil.memAllocPointer(1), MemoryUtil.memAllocPointer(1)),
                            List.of(scalarSlot),
                            16L
                    ),
                    List.of("synthetic CUDA argument binding completed")
            );
        }
    }

    private enum NoOpDriverApiInvoker implements CudaDriverLibrary.DriverApiInvoker {
        INSTANCE;

        @Override
        public int cuInit(int flags, long functionAddress) {
            return CudaDriverLibrary.CUDA_SUCCESS;
        }

        @Override
        public int cuModuleLoadDataEx(
                long moduleOutAddress,
                long imageAddress,
                int optionCount,
                long optionsAddress,
                long optionValuesAddress,
                long functionAddress
        ) {
            return CudaDriverLibrary.CUDA_SUCCESS;
        }

        @Override
        public int cuModuleGetFunction(
                long functionOutAddress,
                long moduleHandle,
                long kernelNameAddress,
                long functionAddress
        ) {
            return CudaDriverLibrary.CUDA_SUCCESS;
        }

        @Override
        public int cuModuleUnload(long moduleHandle, long functionAddress) {
            return CudaDriverLibrary.CUDA_SUCCESS;
        }
    }

    private static final class TestKernelLauncher implements CudaKernelLauncherBridge {
        @Override
        public String launcherId() {
            return "cuda-kernel-launcher:test-launch";
        }

        @Override
        public int launcherOrder() {
            return 1;
        }

        @Override
        public boolean supports(CudaKernelLaunchRequest request) {
            return "test-launch".equals(request.launcherMode());
        }

        @Override
        public CudaKernelLaunchResult launch(CudaKernelLaunchRequest request) {
            return CudaKernelLaunchResult.succeeded(
                    launcherId(),
                    request.executionConfig(),
                    request.sharedMemoryByteSize(),
                    0,
                    0,
                    List.of("synthetic CUDA launch submitted")
            );
        }
    }

    private static final class TestKernelLauncherWithReadback implements CudaKernelLauncherBridge {
        @Override
        public String launcherId() {
            return "cuda-kernel-launcher:test-launch-readback";
        }

        @Override
        public int launcherOrder() {
            return 1;
        }

        @Override
        public boolean supports(CudaKernelLaunchRequest request) {
            return "test-launch-readback".equals(request.launcherMode());
        }

        @Override
        public CudaKernelLaunchResult launch(CudaKernelLaunchRequest request) {
            return CudaKernelLaunchResult.succeeded(
                    launcherId(),
                    request.executionConfig(),
                    request.sharedMemoryByteSize(),
                    request.readbackRequiredCount(),
                    0,
                    List.of("synthetic CUDA launch submitted with pending readback")
            );
        }
    }

    private static final class TestReadbackBridge implements CudaKernelReadbackBridge {
        @Override
        public String readbackId() {
            return "cuda-readback:test-readback";
        }

        @Override
        public int readbackOrder() {
            return 1;
        }

        @Override
        public boolean supports(CudaKernelReadbackRequest request) {
            return "test-readback".equals(request.readbackMode());
        }

        @Override
        public CudaKernelReadbackResult readBack(CudaKernelReadbackRequest request) {
            return CudaKernelReadbackResult.succeeded(
                    readbackId(),
                    request.readbackRequiredCount(),
                    request.readbackRequiredCount(),
                    List.of("synthetic CUDA readback completed")
            );
        }
    }
}
