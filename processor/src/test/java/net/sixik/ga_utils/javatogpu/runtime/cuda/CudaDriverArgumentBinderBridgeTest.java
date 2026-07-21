package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.Float3;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuMemorySlice;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaDriverArgumentBinderBridgeTest {

    @Test
    void builtInArgumentBinderRegistryContainsDriverBridge() {
        assertTrue(CudaArgumentBinderBridgeRegistry.loadWithBuiltIns().binders().stream()
                .anyMatch(binder -> CudaDriverArgumentBinderBridge.ID.equals(binder.binderId())));
    }

    @Test
    void driverArgumentBinderPreparesEmptyFrameForZeroArgumentKernel() {
        CudaDriverLoadedModule loadedModule = loadedModule();
        CudaArgumentBindingRequest request = bindingRequest(List.of(), loadedModule);

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);
        Map<String, String> fields = result.artifactFields("test.cuda.argumentBinding");

        assertTrue(result.succeeded());
        assertEquals(CudaDriverArgumentBinderBridge.ID, result.binderId());
        assertTrue(result.argumentFrame() != null);
        assertFalse(result.argumentFrame().nativePointerTablePresent());
        assertFalse(result.argumentFrame().deviceMemoryPresent());
        assertEquals(0, result.bindingSummary().argumentBindingCount());
        assertEquals("true", fields.get("runtime.cuda.argumentBinding.argumentFrame.present"));
        assertEquals("false", fields.get("runtime.cuda.argumentFrame.nativePointerTable.present"));

        result.argumentFrame().close();
        loadedModule.close();
    }

    @Test
    void driverArgumentBinderRequiresRealDriverModuleHandle() {
        CudaArgumentBindingRequest request = bindingRequest(List.of(), null);

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);

        assertFalse(result.succeeded());
        assertTrue(result.blockers().contains("cuda-driver-module-handle-missing"));
        assertTrue(result.argumentFrame() == null);
    }

    @Test
    void driverArgumentBinderBlocksDescriptorArgumentsUntilInvocationValuesExist() {
        CudaDriverLoadedModule loadedModule = loadedModule();
        CudaArgumentBindingRequest request = bindingRequest(descriptorParameters(), loadedModule);

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);

        assertFalse(result.succeeded());
        assertTrue(result.blockers().contains("cuda-driver-argument-values-missing"));
        assertTrue(result.blockers().contains("cuda-driver-buffer-binding-missing"));
        assertTrue(result.blockers().contains("cuda-driver-local-binding-missing"));
        assertTrue(result.blockers().contains("cuda-driver-scalar-value-binding-missing"));
        assertEquals(0, result.bindingSummary().argumentBindingCount());
        assertTrue(result.argumentFrame() == null);

        loadedModule.close();
    }

    @Test
    void cudaExecutionPlanCapturesInvocationArgumentPayload() {
        GpuKernelDescriptor descriptor = descriptor(descriptorParameters());
        Object[] arguments = new Object[]{new float[]{1.0f}, new float[1], 2.0f, new float[8]};
        CudaExecutionPlan plan = CudaExecutionPlan.from(new GpuKernelInvocation(descriptor, arguments));
        Map<String, String> fields = plan.artifactFields("test.cuda.executionPlan");

        assertTrue(plan.invocationArgumentsPresent());
        assertEquals(4, plan.invocationArgumentCount());
        assertTrue(plan.invocationArgumentAt(0) == arguments[0]);
        assertEquals("true", fields.get("runtime.cuda.executionPlan.invocationArguments.present"));
        assertEquals("4", fields.get("runtime.cuda.executionPlan.invocationArguments.count"));

        arguments[2] = 3.0f;

        assertEquals(2.0f, plan.invocationArgumentAt(2));
    }

    @Test
    void driverArgumentBinderBindsPayloadWithLocalSharedMemory() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        CudaDriverLoadedModule loadedModule = loadedModule(invoker);
        CudaArgumentBindingRequest request = bindingRequest(
                descriptorParameters(),
                loadedModule,
                new Object[]{new float[]{1.0f}, new float[1], 2.0f, new float[8]}
        );

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);
        Map<String, String> fields = result.artifactFields("test.cuda.argumentBinding");

        assertTrue(result.succeeded());
        assertFalse(result.blockers().contains("cuda-driver-argument-values-missing"));
        assertFalse(result.blockers().contains("cuda-driver-local-binding-missing"));
        assertFalse(result.blockers().contains("cuda-driver-scalar-value-binding-missing"));
        assertTrue(result.argumentFrame() != null);
        assertEquals(2, result.argumentFrame().deviceAllocationCount());
        assertEquals(1, result.argumentFrame().scalarArgumentSlotCount());
        assertEquals(32L, result.argumentFrame().localSharedMemoryByteSize());
        assertEquals(3, result.argumentFrame().kernelParameterSlotCount());
        assertEquals(List.of(4L, 4L), invoker.memAllocByteCounts);
        assertEquals("true", fields.get("runtime.cuda.argumentBinding.executionPlan.present"));
        assertEquals("true", fields.get("runtime.cuda.executionPlan.invocationArguments.present"));
        assertEquals("4", fields.get("runtime.cuda.executionPlan.invocationArguments.count"));
        assertEquals("true", fields.get("runtime.cuda.argumentFrame.localSharedMemory.present"));
        assertEquals("32", fields.get("runtime.cuda.argumentFrame.localSharedMemory.byteSize"));
        assertEquals("3", fields.get("runtime.cuda.argumentFrame.kernelParameterSlot.count"));

        result.argumentFrame().close();

        loadedModule.close();
    }

    @Test
    void driverArgumentBinderBindsMultipleLocalSharedMemorySlices() {
        CudaDriverLoadedModule loadedModule = loadedModule();
        CudaArgumentBindingRequest request = bindingRequest(
                multipleLocalParameters(),
                loadedModule,
                new Object[]{new byte[3], new double[2]}
        );

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);
        Map<String, String> fields = result.artifactFields("test.cuda.argumentBinding");

        assertTrue(result.succeeded());
        assertTrue(result.argumentFrame() != null);
        assertTrue(result.argumentFrame().nativePointerTablePresent());
        assertFalse(result.argumentFrame().deviceMemoryPresent());
        assertEquals(24L, result.argumentFrame().localSharedMemoryByteSize());
        assertEquals(2, result.argumentFrame().kernelParameterSlotCount());
        assertEquals(2, result.argumentFrame().scalarArgumentSlotCount());
        assertEquals(0, result.argumentFrame().deviceAllocationCount());
        assertEquals("24", fields.get("runtime.cuda.argumentFrame.localSharedMemory.byteSize"));
        assertEquals("2", fields.get("runtime.cuda.argumentFrame.localSharedMemory.slice.count"));
        assertEquals("2", fields.get("runtime.cuda.argumentFrame.localSharedMemory.hiddenOffsetParameter.count"));
        assertEquals("0", fields.get("runtime.cuda.argumentFrame.localSharedMemory.layout.slice.0.byteOffset"));
        assertEquals("3", fields.get("runtime.cuda.argumentFrame.localSharedMemory.layout.slice.0.byteSize"));
        assertEquals("8", fields.get("runtime.cuda.argumentFrame.localSharedMemory.layout.slice.1.byteOffset"));
        assertEquals("8", fields.get("runtime.cuda.argumentFrame.localSharedMemory.layout.slice.1.alignment"));
        assertEquals("__jtg_local_scratchA_byte_offset", fields.get("runtime.cuda.argumentFrame.localSharedMemory.layout.slice.0.hiddenOffsetParameter.name"));
        assertEquals("__jtg_local_scratchB_byte_offset", fields.get("runtime.cuda.argumentFrame.localSharedMemory.layout.slice.1.hiddenOffsetParameter.name"));

        result.argumentFrame().close();

        loadedModule.close();
    }

    @Test
    void driverArgumentBinderAllocatesMixedFloatBuffersAndScalarValues() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        CudaDriverLoadedModule loadedModule = loadedModule(invoker);
        CudaArgumentBindingRequest request = bindingRequest(
                mixedBufferAndScalarParameters(),
                loadedModule,
                new Object[]{new float[]{1.0f, 2.0f}, 2.5f, 2, new float[]{0.0f, 0.0f}}
        );

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);
        Map<String, String> fields = result.artifactFields("test.cuda.argumentBinding");

        assertTrue(result.succeeded());
        assertTrue(result.argumentFrame() != null);
        assertTrue(result.argumentFrame().nativePointerTablePresent());
        assertTrue(result.argumentFrame().deviceMemoryPresent());
        assertEquals(2, result.argumentFrame().deviceAllocationCount());
        assertEquals(2, result.argumentFrame().scalarArgumentSlotCount());
        assertEquals(8, result.argumentFrame().scalarArgumentByteSize());
        assertEquals(1, result.argumentFrame().readbackRequiredCount());
        assertEquals(List.of(8L, 8L), invoker.memAllocByteCounts);
        assertEquals(2, invoker.hostToDeviceCopies.size());
        assertEquals("2", fields.get("runtime.cuda.argumentFrame.scalarArgumentSlot.count"));
        assertEquals("8", fields.get("runtime.cuda.argumentFrame.scalarArgumentSlot.byteSize"));
        assertEquals("2", fields.get("runtime.cuda.argumentFrame.deviceAllocation.count"));
        assertEquals("2", fields.get("runtime.cuda.argumentFrame.binding.scalar.count"));

        result.argumentFrame().close();

        assertTrue(result.argumentFrame().closed());
        assertEquals(invoker.allocatedPointers, invoker.freedPointers);

        loadedModule.close();
    }

    @Test
    void driverArgumentBinderRejectsScalarArgumentTypeMismatch() {
        CudaDriverLoadedModule loadedModule = loadedModule();
        CudaArgumentBindingRequest request = bindingRequest(
                mixedBufferAndScalarParameters(),
                loadedModule,
                new Object[]{new float[]{1.0f}, 2, 1, new float[]{0.0f}}
        );

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);

        assertFalse(result.succeeded());
        assertTrue(result.blockers().contains("cuda-driver-scalar-argument-type-mismatch:1:float"));
        assertTrue(result.argumentFrame() == null);

        loadedModule.close();
    }

    @Test
    void driverArgumentBinderAllocatesPrimitiveArrayBuffers() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        CudaDriverLoadedModule loadedModule = loadedModule(invoker);
        CudaArgumentBindingRequest request = bindingRequest(
                primitiveArrayBufferParameters(),
                loadedModule,
                new Object[]{new byte[]{1, 2, 3, 4}, new int[]{0, 0}, new double[]{1.5, 2.5}}
        );

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);
        Map<String, String> fields = result.artifactFields("test.cuda.argumentBinding");

        assertTrue(result.succeeded());
        assertTrue(result.argumentFrame() != null);
        assertEquals(3, result.argumentFrame().deviceAllocationCount());
        assertEquals(1, result.argumentFrame().readbackRequiredCount());
        assertEquals(List.of(4L, 8L, 16L), invoker.memAllocByteCounts);
        assertEquals(3, invoker.hostToDeviceCopies.size());
        assertEquals("3", fields.get("runtime.cuda.argumentFrame.deviceAllocation.count"));
        assertEquals("byte[]", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.parameter.javaType"));
        assertEquals("int[]", fields.get("runtime.cuda.argumentFrame.deviceAllocation.1.parameter.javaType"));
        assertEquals("double[]", fields.get("runtime.cuda.argumentFrame.deviceAllocation.2.parameter.javaType"));

        result.argumentFrame().close();

        assertTrue(result.argumentFrame().closed());
        assertEquals(invoker.allocatedPointers, invoker.freedPointers);

        loadedModule.close();
    }

    @Test
    void driverArgumentBinderAllocatesVectorArrayBuffersWithStorageStride() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        CudaDriverLoadedModule loadedModule = loadedModule(invoker);
        CudaArgumentBindingRequest request = bindingRequest(
                vectorArrayBufferParameters(),
                loadedModule,
                new Object[]{
                        new Float3[]{new Float3(1.0f, 2.0f, 3.0f), new Float3(4.0f, 5.0f, 6.0f)},
                        new Float3[]{new Float3(), new Float3()}
                }
        );

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);
        Map<String, String> fields = result.artifactFields("test.cuda.argumentBinding");

        assertTrue(result.succeeded());
        assertTrue(result.argumentFrame() != null);
        assertEquals(2, result.argumentFrame().deviceAllocationCount());
        assertEquals(1, result.argumentFrame().readbackRequiredCount());
        assertEquals(List.of(32L, 32L), invoker.memAllocByteCounts);
        assertEquals("net.sixik.ga_utils.javatogpu.api.Float3[]", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.parameter.javaType"));
        assertEquals("32", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.byteSize"));
        assertEquals("2", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.element.count"));

        result.argumentFrame().close();

        assertTrue(result.argumentFrame().closed());
        assertEquals(invoker.allocatedPointers, invoker.freedPointers);

        loadedModule.close();
    }

    @Test
    void driverArgumentBinderAllocatesStructArrayBuffersWithVectorFieldPadding() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        CudaDriverLoadedModule loadedModule = loadedModule(invoker);
        CudaArgumentBindingRequest request = bindingRequest(
                structArrayBufferParameters(),
                loadedModule,
                new Object[]{
                        new CudaParticle[]{
                                new CudaParticle(1.5f, new Float3(1.0f, 2.0f, 3.0f)),
                                new CudaParticle(2.5f, new Float3(4.0f, 5.0f, 6.0f))
                        },
                        new CudaParticle[]{new CudaParticle(), new CudaParticle()}
                }
        );

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);
        Map<String, String> fields = result.artifactFields("test.cuda.argumentBinding");

        assertTrue(result.succeeded());
        assertTrue(result.argumentFrame() != null);
        assertEquals(2, result.argumentFrame().deviceAllocationCount());
        assertEquals(1, result.argumentFrame().readbackRequiredCount());
        assertEquals(List.of(64L, 64L), invoker.memAllocByteCounts);
        assertEquals(CudaParticle.class.getName() + "[]", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.parameter.javaType"));
        assertEquals("64", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.byteSize"));
        assertEquals("2", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.element.count"));

        result.argumentFrame().close();

        assertTrue(result.argumentFrame().closed());
        assertEquals(invoker.allocatedPointers, invoker.freedPointers);

        loadedModule.close();
    }

    @Test
    void driverArgumentBinderRejectsPrimitiveArrayArgumentTypeMismatch() {
        CudaDriverLoadedModule loadedModule = loadedModule();
        CudaArgumentBindingRequest request = bindingRequest(
                primitiveArrayBufferParameters(),
                loadedModule,
                new Object[]{new byte[]{1}, new float[]{0.0f}, new double[]{1.0}}
        );

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);

        assertFalse(result.succeeded());
        assertTrue(result.blockers().contains("cuda-driver-array-argument-type-mismatch:1:int[]"));
        assertTrue(result.argumentFrame() == null);

        loadedModule.close();
    }

    @Test
    void driverArgumentBinderAllocatesFloatArrayBuffersAndFreesThemWithFrame() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        CudaDriverLoadedModule loadedModule = loadedModule(invoker);
        CudaArgumentBindingRequest request = bindingRequest(
                bufferOnlyParameters(),
                loadedModule,
                new Object[]{new float[]{1.0f, 2.0f}, new float[]{0.0f, 0.0f}}
        );

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);
        Map<String, String> fields = result.artifactFields("test.cuda.argumentBinding");

        assertTrue(result.succeeded());
        assertTrue(result.argumentFrame() != null);
        assertTrue(result.argumentFrame().nativePointerTablePresent());
        assertTrue(result.argumentFrame().deviceMemoryPresent());
        assertEquals(2, result.argumentFrame().deviceAllocationCount());
        assertEquals(1, result.argumentFrame().readbackRequiredCount());
        assertEquals(List.of(8L, 8L), invoker.memAllocByteCounts);
        assertEquals(2, invoker.hostToDeviceCopies.size());
        assertEquals("true", fields.get("runtime.cuda.argumentFrame.nativePointerTable.present"));
        assertEquals("true", fields.get("runtime.cuda.argumentFrame.deviceMemory.present"));
        assertEquals("2", fields.get("runtime.cuda.argumentFrame.deviceAllocation.count"));
        assertEquals("1", fields.get("runtime.cuda.argumentFrame.readback.required.count"));

        result.argumentFrame().close();

        assertTrue(result.argumentFrame().closed());
        assertEquals(invoker.allocatedPointers, invoker.freedPointers);

        loadedModule.close();
    }

    @Test
    void driverArgumentBinderAllocatesPrimitiveArraySlices() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        CudaDriverLoadedModule loadedModule = loadedModule(invoker);
        float[] input = new float[]{10.0f, 20.0f, 30.0f, 40.0f};
        float[] output = new float[]{-1.0f, -1.0f, -1.0f, -1.0f};
        CudaArgumentBindingRequest request = bindingRequest(
                bufferOnlyParameters(),
                loadedModule,
                new Object[]{GpuMemorySlice.of(input, 1, 2), GpuMemorySlice.of(output, 1, 2)}
        );

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);
        Map<String, String> fields = result.artifactFields("test.cuda.argumentBinding");

        assertTrue(result.succeeded());
        assertTrue(result.argumentFrame() != null);
        assertEquals(2, result.argumentFrame().deviceAllocationCount());
        assertEquals(List.of(8L, 8L), invoker.memAllocByteCounts);
        assertArrayEquals(new float[]{20.0f, 30.0f}, invoker.hostToDeviceFloatCopies.get(0), 0.0001f);
        assertEquals("true", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.hostSlice.enabled"));
        assertEquals("1", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.hostElement.offset"));
        assertEquals("3", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.hostElement.endExclusive"));
        assertEquals("2", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.element.count"));
        assertEquals("8", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.byteSize"));

        result.argumentFrame().close();
        loadedModule.close();
    }

    @Test
    void driverArgumentBinderReportsMissingMemorySymbols() {
        CudaDriverLoadedModule loadedModule = loadedModule(new FakeDriverApiInvoker(), new FakeHandle(false));
        CudaArgumentBindingRequest request = bindingRequest(
                bufferOnlyParameters(),
                loadedModule,
                new Object[]{new float[]{1.0f}, new float[]{0.0f}}
        );

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);

        assertFalse(result.succeeded());
        assertTrue(result.blockers().contains("cuda-driver-symbols-missing"));
        assertTrue(result.blockers().contains("cuda-driver-symbol-missing:cuMemAlloc_v2"));
        assertTrue(result.argumentFrame() == null);

        loadedModule.close();
    }

    @Test
    void driverArgumentBinderRejectsInvocationArgumentCountMismatch() {
        CudaDriverLoadedModule loadedModule = loadedModule();
        CudaArgumentBindingRequest request = bindingRequest(
                descriptorParameters(),
                loadedModule,
                new Object[]{new float[]{1.0f}, new float[1]}
        );

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);

        assertFalse(result.succeeded());
        assertEquals(List.of("cuda-driver-argument-count-mismatch"), result.blockers());
        assertTrue(result.argumentFrame() == null);

        loadedModule.close();
    }

    private static CudaArgumentBindingRequest bindingRequest(
            List<GpuKernelParameterDescriptor> parameters,
            CudaDriverLoadedModule loadedModule
    ) {
        return bindingRequest(parameters, loadedModule, null);
    }

    private static CudaArgumentBindingRequest bindingRequest(
            List<GpuKernelParameterDescriptor> parameters,
            CudaDriverLoadedModule loadedModule,
            Object[] invocationArguments
    ) {
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor(parameters),
                GpuRuntimeCompileOptions.cuda(
                        List.of("--gpu-architecture=compute_86"),
                        Map.of(GpuBackendCompileOptions.CUDA_ARGUMENT_BINDER_PROPERTY, "driver"),
                        "off"
                ),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA")
        );
        CudaCompiledKernel compiledKernel = CudaCompiledKernel.nativeCompiled(
                compileRequest,
                GpuBackendModuleArtifact.cudaSource("", "inline://tests/cuda-preview.cu", "test"),
                CudaNativeCompilationResult.succeeded(
                        "cuda-native-compiler:test",
                        GpuBackendModuleArtifact.ptx(
                                ".version 8.0\n.target sm_86\n.address_size 64\n",
                                "inline://tests/cuda-preview.ptx",
                                "test-cuda-ptx"
                        ),
                        "ptxas info : synthetic PTX bridge",
                        List.of("synthetic PTX bridge emitted PTX")
                )
        );
        CudaModuleLoadResult moduleLoadResult = loadedModule == null
                ? CudaModuleLoadResult.succeeded(
                        "cuda-module-loader:test",
                        "test-module-handle",
                        "test-function-handle",
                        List.of("synthetic non-driver module/function handle")
                )
                : CudaModuleLoadResult.succeeded("cuda-module-loader:driver", loadedModule, List.of("loaded"));
        CudaExecutionPlan executionPlan = invocationArguments == null
                ? CudaExecutionPlan.empty()
                : CudaExecutionPlan.from(new GpuKernelInvocation(compileRequest.descriptor(), invocationArguments));
        return CudaArgumentBindingRequest.from(compiledKernel, moduleLoadResult, executionPlan);
    }

    private static List<GpuKernelParameterDescriptor> descriptorParameters() {
        return List.of(
                new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE),
                new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                new GpuKernelParameterDescriptor("scratch", "float[]", GpuKernelParameterAccess.LOCAL)
        );
    }

    private static List<GpuKernelParameterDescriptor> bufferOnlyParameters() {
        return List.of(
                new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
        );
    }

    private static List<GpuKernelParameterDescriptor> mixedBufferAndScalarParameters() {
        return List.of(
                new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                new GpuKernelParameterDescriptor("count", "int", GpuKernelParameterAccess.VALUE),
                new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
        );
    }

    private static List<GpuKernelParameterDescriptor> primitiveArrayBufferParameters() {
        return List.of(
                new GpuKernelParameterDescriptor("blob", "byte[]", GpuKernelParameterAccess.READ_ONLY),
                new GpuKernelParameterDescriptor("indices", "int[]", GpuKernelParameterAccess.READ_WRITE),
                new GpuKernelParameterDescriptor("weights", "double[]", GpuKernelParameterAccess.READ_ONLY)
        );
    }

    private static List<GpuKernelParameterDescriptor> vectorArrayBufferParameters() {
        return List.of(
                new GpuKernelParameterDescriptor("input", Float3.class.getName() + "[]", GpuKernelParameterAccess.READ_ONLY),
                new GpuKernelParameterDescriptor("output", Float3.class.getName() + "[]", GpuKernelParameterAccess.READ_WRITE)
        );
    }

    private static List<GpuKernelParameterDescriptor> structArrayBufferParameters() {
        return List.of(
                new GpuKernelParameterDescriptor("input", CudaParticle.class.getName() + "[]", GpuKernelParameterAccess.READ_ONLY),
                new GpuKernelParameterDescriptor("output", CudaParticle.class.getName() + "[]", GpuKernelParameterAccess.READ_WRITE)
        );
    }

    private static List<GpuKernelParameterDescriptor> multipleLocalParameters() {
        return List.of(
                new GpuKernelParameterDescriptor("scratchA", "byte[]", GpuKernelParameterAccess.LOCAL),
                new GpuKernelParameterDescriptor("scratchB", "double[]", GpuKernelParameterAccess.LOCAL)
        );
    }

    private static GpuKernelDescriptor descriptor(List<GpuKernelParameterDescriptor> parameters) {
        return new GpuKernelDescriptor(
                "jtg_cuda_preview_kernel",
                "inline://tests/cuda-preview.cu",
                "extern \"C\" __global__ void jtg_cuda_preview_kernel() { }",
                parameters
        );
    }

    private static CudaDriverLoadedModule loadedModule() {
        return loadedModule(new FakeDriverApiInvoker());
    }

    private static CudaDriverLoadedModule loadedModule(FakeDriverApiInvoker invoker) {
        return loadedModule(invoker, new FakeHandle(true));
    }

    private static CudaDriverLoadedModule loadedModule(FakeDriverApiInvoker invoker, FakeHandle handle) {
        return new CudaDriverLoadedModule(
                "cuda-module-loader:driver",
                "nvcuda",
                "C:/Windows/System32/nvcuda.dll",
                "jtg_cuda_preview_kernel",
                0xCAFE_0001L,
                0xCAFE_0002L,
                0xCAFE_0003L,
                handle,
                invoker,
                List.of("synthetic CUDA module/function handle loaded")
        );
    }

    private static final class FakeHandle implements CudaDriverLibrary.SharedLibraryHandle {
        private final boolean memorySymbolsAvailable;
        private boolean closed;

        private FakeHandle(boolean memorySymbolsAvailable) {
            this.memorySymbolsAvailable = memorySymbolsAvailable;
        }

        @Override
        public String loadedName() {
            return "nvcuda";
        }

        @Override
        public String path() {
            return "C:/Windows/System32/nvcuda.dll";
        }

        @Override
        public long findSymbol(String symbolName) {
            if (symbolName.startsWith("cuMem") || symbolName.startsWith("cuMemcpy")) {
                return memorySymbolsAvailable ? 0x2000L : 0L;
            }
            return 0x1000L;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @GPUStruct
    static final class CudaParticle {
        float weight;
        Float3 normal;

        CudaParticle() {
            this(0.0f, new Float3());
        }

        CudaParticle(float weight, Float3 normal) {
            this.weight = weight;
            this.normal = normal;
        }
    }

    private static final class FakeDriverApiInvoker implements CudaDriverLibrary.DriverApiInvoker {
        private final List<Long> unloadedModules = new ArrayList<>();
        private final List<Long> memAllocByteCounts = new ArrayList<>();
        private final List<Long> allocatedPointers = new ArrayList<>();
        private final List<String> hostToDeviceCopies = new ArrayList<>();
        private final List<float[]> hostToDeviceFloatCopies = new ArrayList<>();
        private final List<Long> freedPointers = new ArrayList<>();
        private long nextDevicePointer = 0xD00D_0000L;

        @Override
        public int cuInit(int flags, long functionAddress) {
            return 0;
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
            return 0;
        }

        @Override
        public int cuModuleGetFunction(
                long functionOutAddress,
                long moduleHandle,
                long kernelNameAddress,
                long functionAddress
        ) {
            return 0;
        }

        @Override
        public int cuModuleUnload(long moduleHandle, long functionAddress) {
            unloadedModules.add(moduleHandle);
            return 0;
        }

        @Override
        public int cuMemAlloc(long devicePointerOutAddress, long byteCount, long functionAddress) {
            long pointer = nextDevicePointer;
            nextDevicePointer += 0x100L;
            org.lwjgl.system.MemoryUtil.memPutAddress(devicePointerOutAddress, pointer);
            memAllocByteCounts.add(byteCount);
            allocatedPointers.add(pointer);
            return 0;
        }

        @Override
        public int cuMemcpyHtoD(long devicePointer, long hostPointerAddress, long byteCount, long functionAddress) {
            hostToDeviceCopies.add(devicePointer + ":" + byteCount);
            if (byteCount % Float.BYTES == 0L) {
                float[] values = new float[Math.toIntExact(byteCount / Float.BYTES)];
                org.lwjgl.system.MemoryUtil.memByteBuffer(hostPointerAddress, Math.toIntExact(byteCount))
                        .order(java.nio.ByteOrder.nativeOrder())
                        .asFloatBuffer()
                        .get(values);
                hostToDeviceFloatCopies.add(values);
            }
            return 0;
        }

        @Override
        public int cuMemFree(long devicePointer, long functionAddress) {
            freedPointers.add(devicePointer);
            return 0;
        }
    }
}
