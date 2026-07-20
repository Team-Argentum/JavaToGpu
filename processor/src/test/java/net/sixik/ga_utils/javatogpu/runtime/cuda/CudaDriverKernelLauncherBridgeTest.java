package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaDriverKernelLauncherBridgeTest {

    @Test
    void builtInKernelLauncherRegistryContainsDriverBridge() {
        assertTrue(CudaKernelLauncherBridgeRegistry.loadWithBuiltIns().launchers().stream()
                .anyMatch(launcher -> CudaDriverKernelLauncherBridge.ID.equals(launcher.launcherId())));
    }

    @Test
    void driverKernelLauncherSubmitsBoundKernelThroughDriverApi() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        CudaDriverLoadedModule loadedModule = loadedModule(invoker, new FakeHandle(true));
        CudaKernelArgumentFrame frame = argumentFrame(invoker);
        CudaKernelLaunchRequest request = launchRequest(loadedModule, frame, GpuExecutionConfig.oneDimensional(64L, 16L));

        CudaKernelLaunchResult result = new CudaDriverKernelLauncherBridge().launch(request);
        Map<String, String> fields = result.artifactFields("test.cuda.kernelLaunch");

        assertTrue(result.succeeded());
        assertEquals(CudaDriverKernelLauncherBridge.ID, result.launcherId());
        assertEquals(4, invoker.gridDimX);
        assertEquals(1, invoker.gridDimY);
        assertEquals(1, invoker.gridDimZ);
        assertEquals(16, invoker.blockDimX);
        assertEquals(1, invoker.blockDimY);
        assertEquals(1, invoker.blockDimZ);
        assertEquals(0, invoker.sharedMemoryBytes);
        assertEquals(0L, invoker.streamHandle);
        assertEquals(frame.kernelParameterTableAddress(), invoker.kernelParameterTableAddress);
        assertEquals(0L, invoker.extraAddress);
        assertEquals("succeeded", fields.get("runtime.cuda.kernelLaunch.status"));
        assertEquals("true", fields.get("runtime.cuda.kernelLaunch.submitted"));
        assertEquals("1", fields.get("runtime.cuda.kernelLaunch.readback.required.count"));

        frame.close();
        loadedModule.close();
    }

    @Test
    void driverKernelLauncherSubmitsLocalSharedMemoryWithoutParameterTable() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        CudaDriverLoadedModule loadedModule = loadedModule(invoker, new FakeHandle(true));
        CudaKernelArgumentFrame frame = localSharedMemoryOnlyFrame();
        CudaKernelLaunchRequest request = launchRequest(loadedModule, frame, GpuExecutionConfig.oneDimensional(32L, 8L));

        CudaKernelLaunchResult result = new CudaDriverKernelLauncherBridge().launch(request);
        Map<String, String> fields = result.artifactFields("test.cuda.kernelLaunch");

        assertTrue(result.succeeded());
        assertEquals(4, invoker.gridDimX);
        assertEquals(8, invoker.blockDimX);
        assertEquals(32, invoker.sharedMemoryBytes);
        assertEquals(0L, invoker.kernelParameterTableAddress);
        assertEquals("true", fields.get("runtime.cuda.kernelLaunch.sharedMemory.present"));
        assertEquals("32", fields.get("runtime.cuda.kernelLaunch.sharedMemory.byteSize"));

        frame.close();
        loadedModule.close();
    }

    @Test
    void driverKernelLauncherReportsMissingLaunchSymbol() {
        CudaDriverLoadedModule loadedModule = loadedModule(new FakeDriverApiInvoker(), new FakeHandle(false));
        CudaKernelArgumentFrame frame = argumentFrame(new FakeDriverApiInvoker());
        CudaKernelLaunchRequest request = launchRequest(loadedModule, frame, GpuExecutionConfig.oneDimensional(8L));

        CudaKernelLaunchResult result = new CudaDriverKernelLauncherBridge().launch(request);

        assertFalse(result.succeeded());
        assertEquals("unsupported", result.status());
        assertTrue(result.blockers().contains("cuda-driver-symbols-missing"));
        assertTrue(result.blockers().contains("cuda-driver-symbol-missing:cuLaunchKernel"));

        frame.close();
        loadedModule.close();
    }

    @Test
    void driverKernelLauncherReportsLaunchFailureStatus() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        invoker.launchStatus = 719;
        CudaDriverLoadedModule loadedModule = loadedModule(invoker, new FakeHandle(true));
        CudaKernelArgumentFrame frame = argumentFrame(invoker);
        CudaKernelLaunchRequest request = launchRequest(loadedModule, frame, GpuExecutionConfig.oneDimensional(8L));

        CudaKernelLaunchResult result = new CudaDriverKernelLauncherBridge().launch(request);

        assertFalse(result.succeeded());
        assertEquals("failed", result.status());
        assertTrue(result.blockers().contains("cuda-driver-cuLaunchKernel-failed:719"));

        frame.close();
        loadedModule.close();
    }

    @Test
    void driverKernelLauncherRequiresArgumentFrameForDescriptorArguments() {
        CudaDriverLoadedModule loadedModule = loadedModule(new FakeDriverApiInvoker(), new FakeHandle(true));
        CudaKernelLaunchRequest request = launchRequest(loadedModule, null, GpuExecutionConfig.oneDimensional(8L));

        CudaKernelLaunchResult result = new CudaDriverKernelLauncherBridge().launch(request);

        assertFalse(result.succeeded());
        assertEquals("unsupported", result.status());
        assertTrue(result.blockers().contains("cuda-driver-argument-frame-missing"));

        loadedModule.close();
    }

    private static CudaKernelLaunchRequest launchRequest(
            CudaDriverLoadedModule loadedModule,
            CudaKernelArgumentFrame frame,
            GpuExecutionConfig executionConfig
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
        return CudaKernelLaunchRequest.from(preparedKernel, executionConfig);
    }

    private static CudaKernelArgumentFrame argumentFrame(FakeDriverApiInvoker invoker) {
        CudaDriverDeviceAllocation allocation = new CudaDriverDeviceAllocation(
                0,
                "output",
                "float[]",
                GpuKernelParameterAccess.READ_WRITE,
                new float[]{0.0f, 0.0f},
                2,
                8L,
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

    private static CudaKernelArgumentFrame localSharedMemoryOnlyFrame() {
        return CudaKernelArgumentFrame.nativeBindings(
                "cuda-argument-binder:driver",
                new GpuRuntimeInvocationBindingSummary(0, 1, 0, 1),
                List.of(),
                null,
                List.of(),
                List.of(),
                32L
        );
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
        private final boolean launchSymbolAvailable;
        private boolean closed;

        private FakeHandle(boolean launchSymbolAvailable) {
            this.launchSymbolAvailable = launchSymbolAvailable;
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
            if ("cuLaunchKernel".equals(symbolName)) {
                return launchSymbolAvailable ? 0x3000L : 0L;
            }
            return 0x1000L;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FakeDriverApiInvoker implements CudaDriverLibrary.DriverApiInvoker {
        private final List<Long> unloadedModules = new ArrayList<>();
        private final List<Long> freedPointers = new ArrayList<>();
        private int launchStatus;
        private int gridDimX;
        private int gridDimY;
        private int gridDimZ;
        private int blockDimX;
        private int blockDimY;
        private int blockDimZ;
        private int sharedMemoryBytes;
        private long streamHandle;
        private long kernelParameterTableAddress;
        private long extraAddress;

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
        public int cuLaunchKernel(
                long functionHandle,
                int gridDimX,
                int gridDimY,
                int gridDimZ,
                int blockDimX,
                int blockDimY,
                int blockDimZ,
                int sharedMemoryBytes,
                long streamHandle,
                long kernelParameterTableAddress,
                long extraAddress,
                long functionAddress
        ) {
            this.gridDimX = gridDimX;
            this.gridDimY = gridDimY;
            this.gridDimZ = gridDimZ;
            this.blockDimX = blockDimX;
            this.blockDimY = blockDimY;
            this.blockDimZ = blockDimZ;
            this.sharedMemoryBytes = sharedMemoryBytes;
            this.streamHandle = streamHandle;
            this.kernelParameterTableAddress = kernelParameterTableAddress;
            this.extraAddress = extraAddress;
            return launchStatus;
        }
    }
}
