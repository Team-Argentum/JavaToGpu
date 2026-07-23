package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.types.floats.Float2;
import net.sixik.ga_utils.javatogpu.api.types.floats.Float3;
import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationBindingSummary;
import org.junit.jupiter.api.Test;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaDriverReadbackBridgeTest {

    @Test
    void builtInReadbackRegistryContainsDriverBridge() {
        assertTrue(CudaKernelReadbackBridgeRegistry.loadWithBuiltIns().readbacks().stream()
                .anyMatch(readback -> CudaDriverReadbackBridge.ID.equals(readback.readbackId())));
    }

    @Test
    void driverReadbackCopiesReadWriteFloatArrayBackToHost() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        invoker.nextReadbackValues = new float[]{3.0f, 5.0f};
        CudaDriverLoadedModule loadedModule = loadedModule(invoker, new FakeHandle(true));
        float[] output = new float[]{0.0f, 0.0f};
        CudaKernelArgumentFrame frame = argumentFrame(invoker, output);
        CudaKernelReadbackRequest request = readbackRequest(loadedModule, frame);

        CudaKernelReadbackResult result = new CudaDriverReadbackBridge().readBack(request);
        Map<String, String> fields = frame.artifactFields("test.cuda.argumentFrame");

        assertTrue(result.succeeded());
        assertTrue(result.complete());
        assertEquals(CudaDriverReadbackBridge.ID, result.readbackId());
        assertEquals(1, result.readbackRequiredCount());
        assertEquals(1, result.readbackCompletedCount());
        assertArrayEquals(new float[]{3.0f, 5.0f}, output);
        assertEquals(List.of(0xD00D_0000L + ":8"), invoker.deviceToHostCopies);
        assertEquals("true", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.readback.completed"));
        assertEquals("0", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.readback.status"));

        frame.close();
        loadedModule.close();
    }

    @Test
    void driverReadbackCopiesReadWriteFloatSliceBackToHost() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        invoker.nextReadbackValues = new float[]{3.0f, 5.0f};
        CudaDriverLoadedModule loadedModule = loadedModule(invoker, new FakeHandle(true));
        float[] output = new float[]{100.0f, 0.0f, 0.0f, 400.0f};
        CudaKernelArgumentFrame frame = argumentFrame(invoker, output, "float[]", Float.BYTES, 1, 2);
        CudaKernelReadbackRequest request = readbackRequest(loadedModule, frame);

        CudaKernelReadbackResult result = new CudaDriverReadbackBridge().readBack(request);
        Map<String, String> fields = frame.artifactFields("test.cuda.argumentFrame");

        assertTrue(result.succeeded());
        assertTrue(result.complete());
        assertArrayEquals(new float[]{100.0f, 3.0f, 5.0f, 400.0f}, output);
        assertEquals(List.of(0xD00D_0000L + ":8"), invoker.deviceToHostCopies);
        assertEquals("true", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.hostSlice.enabled"));
        assertEquals("1", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.hostElement.offset"));
        assertEquals("3", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.hostElement.endExclusive"));

        frame.close();
        loadedModule.close();
    }

    @Test
    void driverReadbackCopiesReadWriteIntArrayBackToHost() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        invoker.nextReadbackValues = new int[]{7, 11, 13};
        CudaDriverLoadedModule loadedModule = loadedModule(invoker, new FakeHandle(true));
        int[] output = new int[]{0, 0, 0};
        CudaKernelArgumentFrame frame = argumentFrame(invoker, output, "int[]", Integer.BYTES);
        CudaKernelReadbackRequest request = readbackRequest(loadedModule, frame);

        CudaKernelReadbackResult result = new CudaDriverReadbackBridge().readBack(request);

        assertTrue(result.succeeded());
        assertTrue(result.complete());
        assertArrayEquals(new int[]{7, 11, 13}, output);
        assertEquals(List.of(0xD00D_0000L + ":12"), invoker.deviceToHostCopies);

        frame.close();
        loadedModule.close();
    }

    @Test
    void driverReadbackCopiesReadWriteVectorArrayBackToHost() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        invoker.nextReadbackValues = new Float2[]{new Float2(3.0f, 5.0f), new Float2(7.0f, 11.0f)};
        CudaDriverLoadedModule loadedModule = loadedModule(invoker, new FakeHandle(true));
        Float2[] output = new Float2[]{new Float2(), new Float2()};
        CudaKernelArgumentFrame frame = argumentFrame(invoker, output, Float2.class.getName() + "[]", 8);
        CudaKernelReadbackRequest request = readbackRequest(loadedModule, frame);

        CudaKernelReadbackResult result = new CudaDriverReadbackBridge().readBack(request);

        assertTrue(result.succeeded());
        assertTrue(result.complete());
        assertEquals(3.0f, output[0].x);
        assertEquals(5.0f, output[0].y);
        assertEquals(7.0f, output[1].x);
        assertEquals(11.0f, output[1].y);
        assertEquals(List.of(0xD00D_0000L + ":16"), invoker.deviceToHostCopies);

        frame.close();
        loadedModule.close();
    }

    @Test
    void driverReadbackCopiesReadWriteStructArrayBackToHost() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        invoker.nextReadbackValues = new CudaParticle[]{
                new CudaParticle(1.5f, new Float3(1.0f, 2.0f, 3.0f)),
                new CudaParticle(2.5f, new Float3(4.0f, 5.0f, 6.0f))
        };
        CudaDriverLoadedModule loadedModule = loadedModule(invoker, new FakeHandle(true));
        CudaParticle[] output = new CudaParticle[]{new CudaParticle(), new CudaParticle()};
        CudaKernelArgumentFrame frame = argumentFrame(invoker, output, CudaParticle.class.getName() + "[]", 32);
        CudaKernelReadbackRequest request = readbackRequest(loadedModule, frame);

        CudaKernelReadbackResult result = new CudaDriverReadbackBridge().readBack(request);

        assertTrue(result.succeeded());
        assertTrue(result.complete());
        assertEquals(1.5f, output[0].weight);
        assertEquals(1.0f, output[0].normal.x);
        assertEquals(2.0f, output[0].normal.y);
        assertEquals(3.0f, output[0].normal.z);
        assertEquals(2.5f, output[1].weight);
        assertEquals(4.0f, output[1].normal.x);
        assertEquals(5.0f, output[1].normal.y);
        assertEquals(6.0f, output[1].normal.z);
        assertEquals(List.of(0xD00D_0000L + ":64"), invoker.deviceToHostCopies);

        frame.close();
        loadedModule.close();
    }

    @Test
    void driverReadbackReportsMissingReadbackSymbol() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        CudaDriverLoadedModule loadedModule = loadedModule(invoker, new FakeHandle(false));
        float[] output = new float[]{0.0f, 0.0f};
        CudaKernelArgumentFrame frame = argumentFrame(invoker, output);
        CudaKernelReadbackRequest request = readbackRequest(loadedModule, frame);

        CudaKernelReadbackResult result = new CudaDriverReadbackBridge().readBack(request);

        assertFalse(result.succeeded());
        assertEquals("unsupported", result.status());
        assertTrue(result.blockers().contains("cuda-driver-symbols-missing"));
        assertTrue(result.blockers().contains("cuda-driver-symbol-missing:cuMemcpyDtoH_v2"));
        assertArrayEquals(new float[]{0.0f, 0.0f}, output);

        frame.close();
        loadedModule.close();
    }

    @Test
    void driverReadbackReportsDeviceToHostCopyFailure() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        invoker.deviceToHostStatus = 700;
        CudaDriverLoadedModule loadedModule = loadedModule(invoker, new FakeHandle(true));
        float[] output = new float[]{0.0f, 0.0f};
        CudaKernelArgumentFrame frame = argumentFrame(invoker, output);
        CudaKernelReadbackRequest request = readbackRequest(loadedModule, frame);

        CudaKernelReadbackResult result = new CudaDriverReadbackBridge().readBack(request);
        Map<String, String> fields = frame.artifactFields("test.cuda.argumentFrame");

        assertFalse(result.succeeded());
        assertEquals("failed", result.status());
        assertEquals(0, result.readbackCompletedCount());
        assertTrue(result.blockers().contains("cuda-driver-cuMemcpyDtoH-failed:700"));
        assertArrayEquals(new float[]{0.0f, 0.0f}, output);
        assertEquals("false", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.readback.completed"));
        assertEquals("700", fields.get("runtime.cuda.argumentFrame.deviceAllocation.0.readback.status"));

        frame.close();
        loadedModule.close();
    }

    @Test
    void driverReadbackRequiresArgumentFrame() {
        CudaDriverLoadedModule loadedModule = loadedModule(new FakeDriverApiInvoker(), new FakeHandle(true));
        CudaKernelReadbackRequest request = readbackRequest(loadedModule, null);

        CudaKernelReadbackResult result = new CudaDriverReadbackBridge().readBack(request);

        assertFalse(result.succeeded());
        assertEquals("unsupported", result.status());
        assertTrue(result.blockers().contains("cuda-driver-argument-frame-missing"));

        loadedModule.close();
    }

    private static CudaKernelReadbackRequest readbackRequest(
            CudaDriverLoadedModule loadedModule,
            CudaKernelArgumentFrame frame
    ) {
        CudaCompiledKernel compiledKernel = compiledKernel();
        CudaModuleLoadResult moduleLoadResult = CudaModuleLoadResult.succeeded(
                "cuda-module-loader:driver",
                loadedModule,
                List.of("loaded")
        );
        CudaArgumentBindingResult bindingResult = CudaArgumentBindingResult.succeeded(
                "cuda-argument-binder:driver",
                new GpuRuntimeInvocationBindingSummary(1, 0, 0, 1),
                frame,
                List.of("bound")
        );
        CudaPreparedKernel preparedKernel = new CudaPreparedKernel(
                compiledKernel,
                moduleLoadResult,
                bindingResult,
                bindingResult.bindingSummary()
        );
        CudaKernelLaunchResult launchResult = CudaKernelLaunchResult.succeeded(
                "cuda-kernel-launcher:driver",
                GpuExecutionConfig.oneDimensional(2L, 2L),
                1,
                0,
                List.of("launched")
        );
        return CudaKernelReadbackRequest.from(
                preparedKernel,
                launchResult,
                launchResult.executionConfig()
        );
    }

    private static CudaKernelArgumentFrame argumentFrame(FakeDriverApiInvoker invoker, float[] output) {
        return argumentFrame(invoker, output, "float[]", Float.BYTES);
    }

    private static CudaKernelArgumentFrame argumentFrame(
            FakeDriverApiInvoker invoker,
            Object output,
            String javaType,
            int elementByteSize
    ) {
        return argumentFrame(invoker, output, javaType, elementByteSize, 0, arrayLength(output));
    }

    private static CudaKernelArgumentFrame argumentFrame(
            FakeDriverApiInvoker invoker,
            Object output,
            String javaType,
            int elementByteSize,
            int hostElementOffset,
            int elementCount
    ) {
        CudaDriverDeviceAllocation allocation = new CudaDriverDeviceAllocation(
                0,
                "output",
                javaType,
                GpuKernelParameterAccess.READ_WRITE,
                output,
                hostElementOffset,
                elementCount,
                (long) elementCount * elementByteSize,
                0xD00D_0000L,
                true,
                0x2003L,
                invoker
        );
        PointerBuffer slot = MemoryUtil.memAllocPointer(1);
        PointerBuffer table = MemoryUtil.memAllocPointer(1);
        slot.put(0, allocation.devicePointer());
        table.put(0, MemoryUtil.memAddress(slot));
        return CudaKernelArgumentFrame.deviceBindings(
                "cuda-argument-binder:driver",
                new GpuRuntimeInvocationBindingSummary(1, 0, 0, 1),
                List.of(allocation),
                table,
                List.of(slot)
        );
    }

    private static int arrayLength(Object output) {
        if (output instanceof float[] values) {
            return values.length;
        }
        if (output instanceof int[] values) {
            return values.length;
        }
        if (CudaValuePacker.isVectorArrayInstance(output)) {
            return CudaValuePacker.vectorArrayLength(output);
        }
        if (CudaValuePacker.isStructArrayInstance(output)) {
            return CudaValuePacker.structArrayLength(output);
        }
        throw new IllegalArgumentException("Unsupported test output array: " + output.getClass().getName());
    }

    private static CudaCompiledKernel compiledKernel() {
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.cuda(
                        List.of("--gpu-architecture=compute_86"),
                        Map.of(
                                GpuBackendCompileOptions.CUDA_MODULE_LOADER_PROPERTY,
                                "driver",
                                GpuBackendCompileOptions.CUDA_ARGUMENT_BINDER_PROPERTY,
                                "driver",
                                GpuBackendCompileOptions.CUDA_KERNEL_LAUNCHER_PROPERTY,
                                "driver",
                                GpuBackendCompileOptions.CUDA_READBACK_PROPERTY,
                                "driver"
                        ),
                        "off"
                ),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA")
        );
        GpuBackendModuleArtifact ptx = GpuBackendModuleArtifact.ptx(
                ".version 8.0\n.target sm_86\n.address_size 64\n",
                "inline://tests/cuda-preview.ptx",
                "test-cuda-ptx"
        );
        return CudaCompiledKernel.nativeCompiled(
                compileRequest,
                GpuBackendModuleArtifact.cudaSource("", "inline://tests/cuda-preview.cu", "test"),
                CudaNativeCompilationResult.succeeded(
                        "cuda-native-compiler:test",
                        ptx,
                        "ptxas info : synthetic PTX bridge",
                        List.of("synthetic PTX bridge emitted PTX")
                )
        );
    }

    private static GpuKernelDescriptor descriptor() {
        return new GpuKernelDescriptor(
                "jtg_cuda_preview_kernel",
                "inline://tests/cuda-preview.cu",
                "extern \"C\" __global__ void jtg_cuda_preview_kernel(float* output) { }",
                List.of(new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE))
        );
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
        private final boolean readbackSymbolAvailable;

        private FakeHandle(boolean readbackSymbolAvailable) {
            this.readbackSymbolAvailable = readbackSymbolAvailable;
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
            if ("cuMemcpyDtoH_v2".equals(symbolName)) {
                return readbackSymbolAvailable ? 0x4000L : 0L;
            }
            return 0x1000L;
        }

        @Override
        public void close() {
        }
    }

    private static final class FakeDriverApiInvoker implements CudaDriverLibrary.DriverApiInvoker {
        private final List<Long> unloadedModules = new ArrayList<>();
        private final List<Long> freedPointers = new ArrayList<>();
        private final List<String> deviceToHostCopies = new ArrayList<>();
        private Object nextReadbackValues = new float[]{1.0f, 2.0f};
        private int deviceToHostStatus;

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
        public int cuMemFree(long devicePointer, long functionAddress) {
            freedPointers.add(devicePointer);
            return 0;
        }

        @Override
        public int cuMemcpyDtoH(long hostPointerAddress, long devicePointer, long byteCount, long functionAddress) {
            deviceToHostCopies.add(devicePointer + ":" + byteCount);
            if (deviceToHostStatus == 0) {
                writeReadbackValues(MemoryUtil.memByteBuffer(hostPointerAddress, Math.toIntExact(byteCount))
                        .order(ByteOrder.nativeOrder()), nextReadbackValues);
            }
            return deviceToHostStatus;
        }

        private static void writeReadbackValues(java.nio.ByteBuffer buffer, Object values) {
            if (values instanceof float[] array) {
                buffer.asFloatBuffer().put(array);
            } else if (values instanceof int[] array) {
                buffer.asIntBuffer().put(array);
            } else if (CudaValuePacker.isVectorArrayInstance(values)) {
                java.nio.ByteBuffer packed = CudaValuePacker.packVectorArray(
                        values.getClass().getComponentType().getName() + "[]",
                        values
                );
                try {
                    buffer.put(packed);
                    buffer.position(0);
                } finally {
                    MemoryUtil.memFree(packed);
                }
            } else if (CudaValuePacker.isStructArrayInstance(values)) {
                java.nio.ByteBuffer packed = CudaValuePacker.packStructArray(values);
                try {
                    buffer.put(packed);
                    buffer.position(0);
                } finally {
                    MemoryUtil.memFree(packed);
                }
            } else {
                throw new IllegalArgumentException("Unsupported fake readback values: " + values.getClass().getName());
            }
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
}
