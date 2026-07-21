package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.Float3;
import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Sampler;
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
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
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
    void driverArgumentBinderBindsStructLocalSharedMemorySlices() {
        CudaDriverLoadedModule loadedModule = loadedModule();
        CudaArgumentBindingRequest request = bindingRequest(
                structLocalParameters(),
                loadedModule,
                new Object[]{new byte[3], new CudaParticle[]{new CudaParticle(), new CudaParticle()}}
        );

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);
        Map<String, String> fields = result.artifactFields("test.cuda.argumentBinding");

        assertTrue(result.succeeded());
        assertTrue(result.argumentFrame() != null);
        assertEquals(80L, result.argumentFrame().localSharedMemoryByteSize());
        assertEquals(2, result.argumentFrame().kernelParameterSlotCount());
        assertEquals(2, result.argumentFrame().scalarArgumentSlotCount());
        assertEquals(0, result.argumentFrame().deviceAllocationCount());
        assertEquals("80", fields.get("runtime.cuda.argumentFrame.localSharedMemory.byteSize"));
        assertEquals("2", fields.get("runtime.cuda.argumentFrame.localSharedMemory.slice.count"));
        assertEquals("0", fields.get("runtime.cuda.argumentFrame.localSharedMemory.layout.slice.0.byteOffset"));
        assertEquals("3", fields.get("runtime.cuda.argumentFrame.localSharedMemory.layout.slice.0.byteSize"));
        assertEquals("16", fields.get("runtime.cuda.argumentFrame.localSharedMemory.layout.slice.1.byteOffset"));
        assertEquals("64", fields.get("runtime.cuda.argumentFrame.localSharedMemory.layout.slice.1.byteSize"));
        assertEquals("16", fields.get("runtime.cuda.argumentFrame.localSharedMemory.layout.slice.1.alignment"));
        assertEquals(CudaParticle.class.getName() + "[]", fields.get("runtime.cuda.argumentFrame.localSharedMemory.layout.slice.1.parameter.javaType"));

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
    void cudaValuePackerPacksStructValueWithVectorFieldPadding() {
        ByteBuffer buffer = CudaValuePacker.packStructValue(
                new CudaParticle(1.5f, new Float3(2.0f, 3.0f, 4.0f))
        );
        try {
            assertEquals(32, buffer.capacity());
            assertEquals(1.5f, buffer.getFloat(0), 0.0001f);
            assertEquals(2.0f, buffer.getFloat(16), 0.0001f);
            assertEquals(3.0f, buffer.getFloat(20), 0.0001f);
            assertEquals(4.0f, buffer.getFloat(24), 0.0001f);
            assertEquals(0.0f, buffer.getFloat(28), 0.0001f);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    @Test
    void driverArgumentBinderBindsStructValueArgument() {
        CudaDriverLoadedModule loadedModule = loadedModule();
        CudaArgumentBindingRequest request = bindingRequest(
                structValueParameters(),
                loadedModule,
                new Object[]{new CudaParticle(1.5f, new Float3(1.0f, 2.0f, 3.0f))}
        );

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);
        Map<String, String> fields = result.artifactFields("test.cuda.argumentBinding");

        assertTrue(result.succeeded());
        assertTrue(result.argumentFrame() != null);
        assertTrue(result.argumentFrame().nativePointerTablePresent());
        assertFalse(result.argumentFrame().deviceMemoryPresent());
        assertEquals(0, result.argumentFrame().deviceAllocationCount());
        assertEquals(1, result.argumentFrame().scalarArgumentSlotCount());
        assertEquals(32, result.argumentFrame().scalarArgumentByteSize());
        assertEquals(1, result.argumentFrame().kernelParameterSlotCount());
        assertEquals("1", fields.get("runtime.cuda.argumentFrame.scalarArgumentSlot.count"));
        assertEquals("32", fields.get("runtime.cuda.argumentFrame.scalarArgumentSlot.byteSize"));

        result.argumentFrame().close();
        loadedModule.close();
    }

    @Test
    void driverArgumentBinderRejectsNonStructValueObject() {
        CudaDriverLoadedModule loadedModule = loadedModule();
        CudaArgumentBindingRequest request = bindingRequest(
                List.of(new GpuKernelParameterDescriptor("value", PlainValue.class.getName(), GpuKernelParameterAccess.VALUE)),
                loadedModule,
                new Object[]{new PlainValue()}
        );

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);

        assertFalse(result.succeeded());
        assertTrue(result.blockers().contains("cuda-driver-value-type-unsupported:0:" + PlainValue.class.getName()));
        assertTrue(result.argumentFrame() == null);

        loadedModule.close();
    }

    @Test
    void driverArgumentBinderRejectsImageAndSamplerArgumentsWithExplicitBlockers() {
        CudaDriverLoadedModule loadedModule = loadedModule();
        CudaArgumentBindingRequest request = bindingRequest(
                imageAndSamplerParameters(),
                loadedModule,
                new Object[]{
                        Image2DReadOnly.borrowed(0xCAFE_2101L, 8, 4),
                        Sampler.borrowed(0xCAFE_2102L),
                        Image2DWriteOnly.borrowed(0xCAFE_2103L, 8, 4)
                }
        );

        CudaArgumentBindingResult result = new CudaDriverArgumentBinderBridge().bind(request);
        Map<String, String> fields = result.artifactFields("test.cuda.argumentBinding");

        assertFalse(result.succeeded());
        assertTrue(result.blockers().contains("cuda-driver-image-argument-unsupported:0:" + Image2DReadOnly.class.getName()));
        assertTrue(result.blockers().contains("cuda-driver-sampler-argument-unsupported:1:" + Sampler.class.getName()));
        assertTrue(result.blockers().contains("cuda-driver-image-argument-unsupported:2:" + Image2DWriteOnly.class.getName()));
        assertFalse(result.blockers().stream().anyMatch(blocker -> blocker.startsWith("cuda-driver-value-type-unsupported")));
        assertFalse(result.blockers().stream().anyMatch(blocker -> blocker.startsWith("cuda-driver-array-argument-type-mismatch")));
        assertTrue(result.argumentFrame() == null);
        assertTrue(result.imageSamplerRuntimeBindingPlan().present());
        assertEquals("fail-closed", result.imageSamplerRuntimeBindingPlan().status());
        assertEquals(3, result.imageSamplerRuntimeBindingPlan().entries().size());
        assertEquals(6, result.imageSamplerRuntimeBindingPlan().plannedRuntimeKernelParameterSlotCount());
        assertEquals(0, result.imageSamplerRuntimeBindingPlan().runtimeBindingKernelParameterSlotCount());
        assertTrue(result.imageSamplerDescriptorBuildPlan().present());
        assertEquals("ready", result.imageSamplerDescriptorBuildPlan().status());
        assertEquals(3, result.imageSamplerDescriptorBuildPlan().entries().size());
        assertEquals(2, result.imageSamplerDescriptorBuildPlan().resourceDescriptorPayloadPlannedCount());
        assertEquals(2, result.imageSamplerDescriptorBuildPlan().textureDescriptorPayloadPlannedCount());
        assertEquals(0, result.imageSamplerDescriptorBuildPlan().activeDescriptorPayloadCount());
        assertEquals(0, result.imageSamplerDescriptorBuildPlan().activeNativeDescriptorCount());
        assertTrue(result.imageSamplerDescriptorPayloadModel().present());
        assertEquals("ready", result.imageSamplerDescriptorPayloadModel().status());
        assertEquals(3, result.imageSamplerDescriptorPayloadModel().entries().size());
        assertEquals(2, result.imageSamplerDescriptorPayloadModel().resourcePayloadBuiltCount());
        assertEquals(2, result.imageSamplerDescriptorPayloadModel().texturePayloadBuiltCount());
        assertEquals(1, result.imageSamplerDescriptorPayloadModel().samplerPayloadCount());
        assertEquals(0, result.imageSamplerDescriptorPayloadModel().activeNativeDescriptorCount());
        assertTrue(result.imageSamplerNativeDescriptorEncodingPlan().present());
        assertEquals("ready", result.imageSamplerNativeDescriptorEncodingPlan().status());
        assertEquals(3, result.imageSamplerNativeDescriptorEncodingPlan().entries().size());
        assertEquals(4, result.imageSamplerNativeDescriptorEncodingPlan().resourceFieldWriteCount());
        assertEquals(12, result.imageSamplerNativeDescriptorEncodingPlan().textureFieldWriteCount());
        assertEquals(16, result.imageSamplerNativeDescriptorEncodingPlan().fieldWriteCount());
        assertEquals(0, result.imageSamplerNativeDescriptorEncodingPlan().nativeWriteEnabledCount());
        assertEquals(0, result.imageSamplerNativeDescriptorEncodingPlan().sdkStructByteEncodingEnabledCount());
        assertEquals(0, result.imageSamplerNativeDescriptorEncodingPlan().activeNativeDescriptorCount());
        assertTrue(result.imageSamplerNativeDescriptorAllocationPreflight().present());
        assertEquals("blocked", result.imageSamplerNativeDescriptorAllocationPreflight().status());
        assertEquals("cuda-image-sampler-native-descriptor-allocation-disabled:0:" + Image2DReadOnly.class.getName(), result.imageSamplerNativeDescriptorAllocationPreflight().firstBlocker());
        assertEquals(3, result.imageSamplerNativeDescriptorAllocationPreflight().entries().size());
        assertEquals(2, result.imageSamplerNativeDescriptorAllocationPreflight().resourceDescriptorAllocationCount());
        assertEquals(0, result.imageSamplerNativeDescriptorAllocationPreflight().resourceDescriptorAllocatedCount());
        assertEquals(2, result.imageSamplerNativeDescriptorAllocationPreflight().textureDescriptorAllocationCount());
        assertEquals(0, result.imageSamplerNativeDescriptorAllocationPreflight().textureDescriptorAllocatedCount());
        assertEquals(4, result.imageSamplerNativeDescriptorAllocationPreflight().plannedNativeDescriptorCount());
        assertEquals(0, result.imageSamplerNativeDescriptorAllocationPreflight().allocatedNativeDescriptorCount());
        assertEquals(4, result.imageSamplerNativeDescriptorAllocationPreflight().nativeDescriptorOwnershipPlannedCount());
        assertEquals(0, result.imageSamplerNativeDescriptorAllocationPreflight().nativeDescriptorOwnershipActiveCount());
        assertEquals(4, result.imageSamplerNativeDescriptorAllocationPreflight().cleanupPlannedCount());
        assertEquals(0, result.imageSamplerNativeDescriptorAllocationPreflight().cleanupActiveCount());
        assertEquals(4, result.imageSamplerNativeDescriptorAllocationPreflight().rollbackPlannedCount());
        assertEquals(0, result.imageSamplerNativeDescriptorAllocationPreflight().rollbackActiveCount());
        assertEquals(0, result.imageSamplerNativeDescriptorAllocationPreflight().allocationEnabledCount());
        assertEquals(0, result.imageSamplerNativeDescriptorAllocationPreflight().sdkStructByteEncodingEnabledCount());
        assertEquals(0, result.imageSamplerNativeDescriptorAllocationPreflight().activeNativeDescriptorCount());
        assertTrue(result.imageSamplerNativeDescriptorAllocationTransactionPlan().present());
        assertEquals("blocked", result.imageSamplerNativeDescriptorAllocationTransactionPlan().status());
        assertEquals("cuda-image-sampler-native-descriptor-allocation-transaction-disabled:0:" + Image2DReadOnly.class.getName(), result.imageSamplerNativeDescriptorAllocationTransactionPlan().firstBlocker());
        assertEquals(3, result.imageSamplerNativeDescriptorAllocationTransactionPlan().entries().size());
        assertEquals(4, result.imageSamplerNativeDescriptorAllocationTransactionPlan().descriptorOwnerCount());
        assertEquals(2, result.imageSamplerNativeDescriptorAllocationTransactionPlan().resourceDescriptorOwnerCount());
        assertEquals(2, result.imageSamplerNativeDescriptorAllocationTransactionPlan().textureDescriptorOwnerCount());
        assertEquals(0, result.imageSamplerNativeDescriptorAllocationTransactionPlan().activeDescriptorOwnerCount());
        assertEquals(0, result.imageSamplerNativeDescriptorAllocationTransactionPlan().nativeAddressPresentCount());
        assertEquals(0, result.imageSamplerNativeDescriptorAllocationTransactionPlan().allocationEnabledCount());
        assertEquals(4, result.imageSamplerNativeDescriptorAllocationTransactionPlan().cleanupPlannedCount());
        assertEquals(0, result.imageSamplerNativeDescriptorAllocationTransactionPlan().cleanupActiveCount());
        assertEquals(4, result.imageSamplerNativeDescriptorAllocationTransactionPlan().rollbackPlannedCount());
        assertEquals(0, result.imageSamplerNativeDescriptorAllocationTransactionPlan().rollbackActiveCount());
        assertEquals(0, result.imageSamplerNativeDescriptorAllocationTransactionPlan().activeNativeDescriptorCount());
        assertTrue(result.imageSamplerNativeDescriptorEncodingTransactionPlan().present());
        assertEquals("blocked", result.imageSamplerNativeDescriptorEncodingTransactionPlan().status());
        assertEquals("cuda-image-sampler-native-descriptor-encoding-transaction-disabled:0:" + Image2DReadOnly.class.getName(), result.imageSamplerNativeDescriptorEncodingTransactionPlan().firstBlocker());
        assertEquals(3, result.imageSamplerNativeDescriptorEncodingTransactionPlan().entries().size());
        assertEquals(4, result.imageSamplerNativeDescriptorEncodingTransactionPlan().descriptorWriteCount());
        assertEquals(2, result.imageSamplerNativeDescriptorEncodingTransactionPlan().resourceDescriptorWriteCount());
        assertEquals(2, result.imageSamplerNativeDescriptorEncodingTransactionPlan().textureDescriptorWriteCount());
        assertEquals(4, result.imageSamplerNativeDescriptorEncodingTransactionPlan().resourceFieldWriteCount());
        assertEquals(12, result.imageSamplerNativeDescriptorEncodingTransactionPlan().textureFieldWriteCount());
        assertEquals(16, result.imageSamplerNativeDescriptorEncodingTransactionPlan().fieldWriteCount());
        assertEquals(4, result.imageSamplerNativeDescriptorEncodingTransactionPlan().ownerPresentCount());
        assertEquals(0, result.imageSamplerNativeDescriptorEncodingTransactionPlan().ownerActiveCount());
        assertEquals(0, result.imageSamplerNativeDescriptorEncodingTransactionPlan().nativeAddressPresentCount());
        assertEquals(0, result.imageSamplerNativeDescriptorEncodingTransactionPlan().nativeWriteEnabledCount());
        assertEquals(0, result.imageSamplerNativeDescriptorEncodingTransactionPlan().sdkStructByteEncodingEnabledCount());
        assertEquals(0, result.imageSamplerNativeDescriptorEncodingTransactionPlan().activeNativeDescriptorCount());
        assertTrue(result.imageSamplerObjectCreationRequestPlan().present());
        assertEquals("ready", result.imageSamplerObjectCreationRequestPlan().status());
        assertEquals(3, result.imageSamplerObjectCreationRequestPlan().entries().size());
        assertEquals(2, result.imageSamplerObjectCreationRequestPlan().objectCreationRequestCount());
        assertEquals(1, result.imageSamplerObjectCreationRequestPlan().textureObjectRequestCount());
        assertEquals(1, result.imageSamplerObjectCreationRequestPlan().surfaceObjectRequestCount());
        assertEquals(1, result.imageSamplerObjectCreationRequestPlan().foldedSamplerCount());
        assertEquals(0, result.imageSamplerObjectCreationRequestPlan().objectCreationCallEnabledCount());
        assertEquals(0, result.imageSamplerObjectCreationRequestPlan().activeObjectCount());
        assertTrue(result.imageSamplerNativeObjectPreparationPreflight().present());
        assertEquals("blocked", result.imageSamplerNativeObjectPreparationPreflight().status());
        assertEquals("cuda-image-sampler-native-resource-descriptor-address-unavailable:0:" + Image2DReadOnly.class.getName(), result.imageSamplerNativeObjectPreparationPreflight().firstBlocker());
        assertEquals(3, result.imageSamplerNativeObjectPreparationPreflight().entries().size());
        assertEquals(2, result.imageSamplerNativeObjectPreparationPreflight().objectPreparationCount());
        assertEquals(1, result.imageSamplerNativeObjectPreparationPreflight().textureObjectPreparationCount());
        assertEquals(1, result.imageSamplerNativeObjectPreparationPreflight().surfaceObjectPreparationCount());
        assertEquals(1, result.imageSamplerNativeObjectPreparationPreflight().foldedSamplerPreparationCount());
        assertEquals(2, result.imageSamplerNativeObjectPreparationPreflight().resourceDescriptorRequiredCount());
        assertEquals(0, result.imageSamplerNativeObjectPreparationPreflight().resourceDescriptorAvailableCount());
        assertEquals(2, result.imageSamplerNativeObjectPreparationPreflight().resourceDescriptorOwnerPresentCount());
        assertEquals(0, result.imageSamplerNativeObjectPreparationPreflight().resourceDescriptorNativeAddressPresentCount());
        assertEquals(2, result.imageSamplerNativeObjectPreparationPreflight().resourceDescriptorWritePlannedCount());
        assertEquals(0, result.imageSamplerNativeObjectPreparationPreflight().resourceDescriptorNativeWriteEnabledCount());
        assertEquals(1, result.imageSamplerNativeObjectPreparationPreflight().textureDescriptorRequiredCount());
        assertEquals(0, result.imageSamplerNativeObjectPreparationPreflight().textureDescriptorAvailableCount());
        assertEquals(1, result.imageSamplerNativeObjectPreparationPreflight().textureDescriptorOwnerPresentCount());
        assertEquals(0, result.imageSamplerNativeObjectPreparationPreflight().textureDescriptorNativeAddressPresentCount());
        assertEquals(1, result.imageSamplerNativeObjectPreparationPreflight().textureDescriptorWritePlannedCount());
        assertEquals(0, result.imageSamplerNativeObjectPreparationPreflight().textureDescriptorNativeWriteEnabledCount());
        assertEquals(2, result.imageSamplerNativeObjectPreparationPreflight().createFunctionAvailableCount());
        assertEquals(2, result.imageSamplerNativeObjectPreparationPreflight().destroyFunctionAvailableCount());
        assertEquals(0, result.imageSamplerNativeObjectPreparationPreflight().objectHandleAvailableCount());
        assertEquals(0, result.imageSamplerNativeObjectPreparationPreflight().objectCreationCallEnabledCount());
        assertEquals(0, result.imageSamplerNativeObjectPreparationPreflight().activeNativeDescriptorCount());
        assertEquals(0, result.imageSamplerNativeObjectPreparationPreflight().activeObjectCount());
        assertTrue(result.imageSamplerRuntimeObjectBindingPlan().present());
        assertEquals("ready", result.imageSamplerRuntimeObjectBindingPlan().status());
        assertEquals(3, result.imageSamplerRuntimeObjectBindingPlan().entries().size());
        assertEquals(2, result.imageSamplerRuntimeObjectBindingPlan().objectBindingCount());
        assertEquals(1, result.imageSamplerRuntimeObjectBindingPlan().textureObjectBindingCount());
        assertEquals(1, result.imageSamplerRuntimeObjectBindingPlan().surfaceObjectBindingCount());
        assertEquals(1, result.imageSamplerRuntimeObjectBindingPlan().foldedSamplerBindingCount());
        assertEquals(2, result.imageSamplerRuntimeObjectBindingPlan().plannedObjectKernelParameterSlotCount());
        assertEquals(4, result.imageSamplerRuntimeObjectBindingPlan().plannedMetadataKernelParameterSlotCount());
        assertEquals(6, result.imageSamplerRuntimeObjectBindingPlan().plannedKernelParameterSlotCount());
        assertEquals(0, result.imageSamplerRuntimeObjectBindingPlan().runtimeBindingKernelParameterSlotCount());
        assertEquals(0, result.imageSamplerRuntimeObjectBindingPlan().objectCreationCallEnabledCount());
        assertEquals(0, result.imageSamplerRuntimeObjectBindingPlan().activeObjectCount());
        assertTrue(result.imageSamplerRuntimeObjectBindingTransactionPreflight().present());
        assertEquals("blocked", result.imageSamplerRuntimeObjectBindingTransactionPreflight().status());
        assertEquals("cuda-image-sampler-runtime-native-resource-descriptor-address-unavailable:0:" + Image2DReadOnly.class.getName(), result.imageSamplerRuntimeObjectBindingTransactionPreflight().firstBlocker());
        assertEquals(3, result.imageSamplerRuntimeObjectBindingTransactionPreflight().entries().size());
        assertEquals(2, result.imageSamplerRuntimeObjectBindingTransactionPreflight().objectBindingTransactionCount());
        assertEquals(1, result.imageSamplerRuntimeObjectBindingTransactionPreflight().textureObjectTransactionCount());
        assertEquals(1, result.imageSamplerRuntimeObjectBindingTransactionPreflight().surfaceObjectTransactionCount());
        assertEquals(1, result.imageSamplerRuntimeObjectBindingTransactionPreflight().foldedSamplerTransactionCount());
        assertEquals(2, result.imageSamplerRuntimeObjectBindingTransactionPreflight().objectHandleRequiredCount());
        assertEquals(0, result.imageSamplerRuntimeObjectBindingTransactionPreflight().objectHandleAvailableCount());
        assertEquals(0, result.imageSamplerRuntimeObjectBindingTransactionPreflight().nativeDescriptorAvailableCount());
        assertEquals(2, result.imageSamplerRuntimeObjectBindingTransactionPreflight().resourceDescriptorRequiredCount());
        assertEquals(2, result.imageSamplerRuntimeObjectBindingTransactionPreflight().resourceDescriptorOwnerPresentCount());
        assertEquals(0, result.imageSamplerRuntimeObjectBindingTransactionPreflight().resourceDescriptorNativeAddressPresentCount());
        assertEquals(2, result.imageSamplerRuntimeObjectBindingTransactionPreflight().resourceDescriptorWritePlannedCount());
        assertEquals(0, result.imageSamplerRuntimeObjectBindingTransactionPreflight().resourceDescriptorNativeWriteEnabledCount());
        assertEquals(1, result.imageSamplerRuntimeObjectBindingTransactionPreflight().textureDescriptorRequiredCount());
        assertEquals(1, result.imageSamplerRuntimeObjectBindingTransactionPreflight().textureDescriptorOwnerPresentCount());
        assertEquals(0, result.imageSamplerRuntimeObjectBindingTransactionPreflight().textureDescriptorNativeAddressPresentCount());
        assertEquals(1, result.imageSamplerRuntimeObjectBindingTransactionPreflight().textureDescriptorWritePlannedCount());
        assertEquals(0, result.imageSamplerRuntimeObjectBindingTransactionPreflight().textureDescriptorNativeWriteEnabledCount());
        assertEquals(0, result.imageSamplerRuntimeObjectBindingTransactionPreflight().transactionApplyEnabledCount());
        assertEquals(0, result.imageSamplerRuntimeObjectBindingTransactionPreflight().kernelParameterWriteEnabledCount());
        assertEquals(0, result.imageSamplerRuntimeObjectBindingTransactionPreflight().activeObjectCount());
        assertEquals("true", fields.get("runtime.cuda.argumentBinding.imageSamplerRuntimeBindingPlan.present"));
        assertEquals("fail-closed", fields.get("runtime.cuda.imageSamplerRuntimeBindingPlan.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerRuntimeBindingPlan.entry.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerRuntimeBindingPlan.entry.image.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeBindingPlan.entry.sampler.count"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerRuntimeBindingPlan.argument.compatible.count"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerRuntimeBindingPlan.argument.metadata.available.count"));
        assertEquals("6", fields.get("runtime.cuda.imageSamplerRuntimeBindingPlan.sourcePreview.kernelParameterSlot.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerRuntimeBindingPlan.sourcePreview.metadataSlot.count"));
        assertEquals("6", fields.get("runtime.cuda.imageSamplerRuntimeBindingPlan.runtimeBinding.plannedKernelParameterSlot.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerRuntimeBindingPlan.runtimeBinding.plannedMetadataSlot.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeBindingPlan.runtimeBinding.kernelParameterSlot.count"));
        assertEquals("cuda-image-runtime-binding-disabled:0:" + Image2DReadOnly.class.getName(), fields.get("runtime.cuda.imageSamplerRuntimeBindingPlan.firstBlocker"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerRuntimeBindingPlan.entry.0.argument.metadata.width"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerRuntimeBindingPlan.entry.0.argument.metadata.height"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerRuntimeBindingPlan.entry.0.argument.handle.valid"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeBindingPlan.entry.0.runtimeBinding.enabled"));
        assertEquals("true", fields.get("runtime.cuda.argumentBinding.imageSamplerDescriptorBuildPlan.present"));
        assertEquals("ready", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.entry.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.resourceDescriptorPayload.planned.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.textureDescriptorPayload.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.activeDescriptorPayload.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.activeNativeDescriptor.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.nativeDescriptorAllocation.enabled"));
        assertEquals("8", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.entry.0.argument.metadata.width"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.entry.0.argument.metadata.height"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.entry.0.argument.handle.valid"));
        assertEquals("planned", fields.get("runtime.cuda.imageSamplerDescriptorBuildPlan.entry.0.descriptorPayload.status"));
        assertEquals("true", fields.get("runtime.cuda.argumentBinding.imageSamplerDescriptorPayloadModel.present"));
        assertEquals("ready", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.resourcePayload.built.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.texturePayload.built.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.samplerPayload.built.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.activeNativeDescriptor.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.nativeDescriptorAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.objectCreation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.runtimeBinding.enabled"));
        assertEquals("CUDA_RESOURCE_DESC", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.0.resourcePayload.struct"));
        assertEquals("CUDA_TEXTURE_DESC", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.0.texturePayload.struct"));
        assertEquals("folded-sampler-parameter-default", fields.get("runtime.cuda.imageSamplerDescriptorPayloadModel.entry.1.texturePayload.samplerState.source"));
        assertEquals("true", fields.get("runtime.cuda.argumentBinding.imageSamplerNativeDescriptorEncodingPlan.present"));
        assertEquals("ready", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.resourceFieldWrite.count"));
        assertEquals("12", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.textureFieldWrite.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.fieldWrite.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.nativeWrite.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.sdkStructByteEncoding.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.activeNativeDescriptor.count"));
        assertEquals("res.array.hArray", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.0.resourceFieldWrite.1.fieldPath"));
        assertEquals("addressMode[0]", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingPlan.entry.0.textureFieldWrite.0.fieldPath"));
        assertEquals("true", fields.get("runtime.cuda.argumentBinding.imageSamplerNativeDescriptorAllocationPreflight.present"));
        assertEquals("blocked", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.entry.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.resourceDescriptorAllocation.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.resourceDescriptor.allocated.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.textureDescriptorAllocation.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.textureDescriptor.allocated.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.plannedNativeDescriptor.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.allocatedNativeDescriptor.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.nativeDescriptorOwnership.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.nativeDescriptorOwnership.active.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.cleanup.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.cleanup.active.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.rollback.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.rollback.active.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.allocation.enabled.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.nativeDescriptorAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.sdkStructByteEncoding.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.objectCreation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.runtimeBinding.enabled"));
        assertEquals("cuda-image-sampler-native-descriptor-allocation-disabled:0:" + Image2DReadOnly.class.getName(), fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.firstBlocker"));
        assertEquals("CUDA_RESOURCE_DESC", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.entry.0.resourceDescriptor.struct"));
        assertEquals("CUDA_TEXTURE_DESC", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.entry.0.textureDescriptor.struct"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.entry.0.resourceDescriptor.allocated"));
        assertEquals("CUDA_TEXTURE_DESC", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.entry.1.textureDescriptor.struct"));
        assertEquals("CUDA_RESOURCE_DESC", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationPreflight.entry.2.resourceDescriptor.struct"));
        assertEquals("true", fields.get("runtime.cuda.argumentBinding.imageSamplerNativeDescriptorAllocationTransactionPlan.present"));
        assertEquals("blocked", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.entry.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.descriptorOwner.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.resourceDescriptorOwner.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.textureDescriptorOwner.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.activeDescriptorOwner.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.nativeAddress.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.allocation.enabled.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.cleanup.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.cleanup.active.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.rollback.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.rollback.active.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.allocationApply.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.nativeMemoryAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.cleanupApply.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.rollbackApply.enabled"));
        assertEquals("cuda-image-sampler-native-descriptor-allocation-transaction-disabled:0:" + Image2DReadOnly.class.getName(), fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.firstBlocker"));
        assertEquals("resource", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.entry.0.descriptorOwner.0.descriptor.kind"));
        assertEquals("CUDA_RESOURCE_DESC", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.entry.0.descriptorOwner.0.struct"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.entry.0.descriptorOwner.0.nativeAddress.present"));
        assertEquals("texture", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.entry.1.descriptorOwner.0.descriptor.kind"));
        assertEquals("resource", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationTransactionPlan.entry.2.descriptorOwner.0.descriptor.kind"));
        assertEquals("true", fields.get("runtime.cuda.argumentBinding.imageSamplerNativeDescriptorEncodingTransactionPlan.present"));
        assertEquals("blocked", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.entry.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.descriptorWrite.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.resourceDescriptorWrite.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.textureDescriptorWrite.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.resourceFieldWrite.count"));
        assertEquals("12", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.textureFieldWrite.count"));
        assertEquals("16", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.fieldWrite.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.owner.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.owner.active.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.nativeAddress.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.nativeWrite.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.sdkStructByteEncoding.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.activeNativeDescriptor.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.writeTransactionApply.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.nativeMemoryAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.sdkStructByteEncoding.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.objectCreation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.runtimeBinding.enabled"));
        assertEquals("cuda-image-sampler-native-descriptor-encoding-transaction-disabled:0:" + Image2DReadOnly.class.getName(), fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.firstBlocker"));
        assertEquals("resource", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.entry.0.descriptorWrite.0.descriptor.kind"));
        assertEquals("CUDA_RESOURCE_DESC", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.entry.0.descriptorWrite.0.targetStruct"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.entry.0.descriptorWrite.0.owner.present"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.entry.0.descriptorWrite.0.nativeAddress.present"));
        assertEquals("res.array.hArray", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.entry.0.descriptorWrite.0.fieldWrite.1.fieldPath"));
        assertEquals("texture", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.entry.1.descriptorWrite.0.descriptor.kind"));
        assertEquals("resource", fields.get("runtime.cuda.imageSamplerNativeDescriptorEncodingTransactionPlan.entry.2.descriptorWrite.0.descriptor.kind"));
        assertEquals("true", fields.get("runtime.cuda.argumentBinding.imageSamplerObjectCreationRequestPlan.present"));
        assertEquals("ready", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.entry.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.request.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.textureObjectRequest.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.surfaceObjectRequest.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.foldedSampler.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.objectCreationCall.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.activeObject.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.objectCreationCall.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.runtimeBinding.enabled"));
        assertEquals("texture", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.entry.0.object.kind"));
        assertEquals("CUtexObject", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.entry.0.parameter.carrier"));
        assertEquals("cuTexObjectCreate", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.entry.0.createFunction.symbol"));
        assertEquals("surface", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.entry.2.object.kind"));
        assertEquals("CUsurfObject", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.entry.2.parameter.carrier"));
        assertEquals("cuSurfObjectCreate", fields.get("runtime.cuda.imageSamplerObjectCreationRequestPlan.entry.2.createFunction.symbol"));
        assertEquals("true", fields.get("runtime.cuda.argumentBinding.imageSamplerNativeObjectPreparationPreflight.present"));
        assertEquals("blocked", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.entry.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.objectPreparation.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.textureObjectPreparation.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.surfaceObjectPreparation.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.foldedSamplerPreparation.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.resourceDescriptor.required.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.resourceDescriptor.available.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.resourceDescriptorOwner.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.resourceDescriptor.nativeAddress.present.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.resourceDescriptorWrite.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.resourceDescriptorNativeWrite.enabled.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.textureDescriptor.required.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.textureDescriptor.available.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.textureDescriptorOwner.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.textureDescriptor.nativeAddress.present.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.textureDescriptorWrite.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.textureDescriptorNativeWrite.enabled.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.createFunction.available.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.destroyFunction.available.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.objectHandle.available.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.objectCreationCall.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.activeNativeDescriptor.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.activeObject.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.nativeDescriptorAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.objectCreationCall.enabled"));
        assertEquals("cuda-image-sampler-native-resource-descriptor-address-unavailable:0:" + Image2DReadOnly.class.getName(), fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.firstBlocker"));
        assertEquals("texture", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.entry.0.object.kind"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.entry.0.resourceDescriptor.available"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.entry.0.resourceDescriptorOwner.present"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.entry.0.resourceDescriptor.nativeAddress.present"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.entry.0.resourceDescriptorWrite.planned"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.entry.0.resourceDescriptorNativeWrite.enabled"));
        assertEquals("surface", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.entry.2.object.kind"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeObjectPreparationPreflight.entry.2.objectHandle.available"));
        assertEquals("true", fields.get("runtime.cuda.argumentBinding.imageSamplerRuntimeObjectBindingPlan.present"));
        assertEquals("ready", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.entry.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.objectBinding.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.textureObjectBinding.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.surfaceObjectBinding.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.foldedSamplerBinding.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.plannedObjectKernelParameterSlot.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.plannedMetadataKernelParameterSlot.count"));
        assertEquals("6", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.plannedKernelParameterSlot.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.runtimeBinding.kernelParameterSlot.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.objectCreationCall.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.activeObject.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.objectCreationCall.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.runtimeBinding.enabled"));
        assertEquals("texture", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.entry.0.object.kind"));
        assertEquals("CUtexObject", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.entry.0.parameter.carrier"));
        assertEquals("future-cuTexObjectCreate-result", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.entry.0.objectHandle.source"));
        assertEquals("surface", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.entry.2.object.kind"));
        assertEquals("CUsurfObject", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.entry.2.parameter.carrier"));
        assertEquals("future-cuSurfObjectCreate-result", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingPlan.entry.2.objectHandle.source"));
        assertEquals("true", fields.get("runtime.cuda.argumentBinding.imageSamplerRuntimeObjectBindingTransactionPreflight.present"));
        assertEquals("blocked", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.status"));
        assertEquals("3", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.objectBindingTransaction.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.textureObjectTransaction.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.surfaceObjectTransaction.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.foldedSamplerTransaction.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.objectHandle.required.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.objectHandle.available.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.nativeDescriptor.available.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.resourceDescriptor.required.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.resourceDescriptorOwner.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.resourceDescriptor.nativeAddress.present.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.resourceDescriptorWrite.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.resourceDescriptorNativeWrite.enabled.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.textureDescriptor.required.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.textureDescriptorOwner.present.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.textureDescriptor.nativeAddress.present.count"));
        assertEquals("1", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.textureDescriptorWrite.planned.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.textureDescriptorNativeWrite.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.transactionApply.enabled.count"));
        assertEquals("0", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.kernelParameterWrite.enabled.count"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.nativeDescriptorAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.objectCreationCall.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.transactionApply.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.kernelParameterWrite.enabled"));
        assertEquals("cuda-image-sampler-runtime-native-resource-descriptor-address-unavailable:0:" + Image2DReadOnly.class.getName(), fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.firstBlocker"));
        assertEquals("texture", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry.0.object.kind"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry.0.resourceDescriptorOwner.present"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry.0.resourceDescriptor.nativeAddress.present"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry.0.resourceDescriptorWrite.planned"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry.0.resourceDescriptorNativeWrite.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry.0.objectHandle.available"));
        assertEquals("surface", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry.2.object.kind"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerRuntimeObjectBindingTransactionPreflight.entry.2.kernelParameterWrite.enabled"));

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

    private static List<GpuKernelParameterDescriptor> structValueParameters() {
        return List.of(
                new GpuKernelParameterDescriptor("particle", CudaParticle.class.getName(), GpuKernelParameterAccess.VALUE)
        );
    }

    private static List<GpuKernelParameterDescriptor> imageAndSamplerParameters() {
        return List.of(
                new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY),
                new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE),
                new GpuKernelParameterDescriptor("outputImage", Image2DWriteOnly.class.getName(), GpuKernelParameterAccess.READ_WRITE)
        );
    }

    private static List<GpuKernelParameterDescriptor> multipleLocalParameters() {
        return List.of(
                new GpuKernelParameterDescriptor("scratchA", "byte[]", GpuKernelParameterAccess.LOCAL),
                new GpuKernelParameterDescriptor("scratchB", "double[]", GpuKernelParameterAccess.LOCAL)
        );
    }

    private static List<GpuKernelParameterDescriptor> structLocalParameters() {
        return List.of(
                new GpuKernelParameterDescriptor("scratchA", "byte[]", GpuKernelParameterAccess.LOCAL),
                new GpuKernelParameterDescriptor("scratchB", CudaParticle.class.getName() + "[]", GpuKernelParameterAccess.LOCAL)
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

    private static final class PlainValue {
        int value = 1;
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
