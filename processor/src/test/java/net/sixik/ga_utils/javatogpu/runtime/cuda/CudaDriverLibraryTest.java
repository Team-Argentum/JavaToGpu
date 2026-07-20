package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaDriverLibraryTest {

    @Test
    void probeReportsAvailableWhenLibraryAndRequiredSymbolsResolve() {
        Map<String, Long> symbols = new LinkedHashMap<>();
        for (String symbol : CudaDriverLibrary.REQUIRED_MODULE_LOADER_SYMBOLS) {
            symbols.put(symbol, 0x1000L + symbols.size());
        }
        FakeHandle handle = new FakeHandle("nvcuda", "C:/Windows/System32/nvcuda.dll", symbols);
        FakeResolver resolver = new FakeResolver(Map.of("nvcuda", handle));

        CudaDriverApiProbeResult result = CudaDriverLibrary.probe(
                resolver,
                List.of("missing-cuda", "nvcuda"),
                CudaDriverLibrary.REQUIRED_MODULE_LOADER_SYMBOLS
        );
        Map<String, String> fields = result.artifactFields("test.cuda.driver");

        assertTrue(result.available());
        assertEquals("available", result.status());
        assertEquals("nvcuda", result.libraryName());
        assertEquals("C:/Windows/System32/nvcuda.dll", result.libraryPath());
        assertEquals(CudaDriverLibrary.REQUIRED_MODULE_LOADER_SYMBOLS, result.resolvedSymbols());
        assertTrue(result.missingSymbols().isEmpty());
        assertTrue(result.blockers().isEmpty());
        assertEquals(List.of("missing-cuda", "nvcuda"), resolver.attempts);
        assertTrue(handle.closed);
        assertEquals("available", fields.get("runtime.cuda.driverApi.status"));
        assertEquals("true", fields.get("runtime.cuda.driverApi.available"));
    }

    @Test
    void probeReportsUnavailableWhenNoLibraryCandidateLoads() {
        FakeResolver resolver = new FakeResolver(Map.of());

        CudaDriverApiProbeResult result = CudaDriverLibrary.probe(
                resolver,
                List.of("nvcuda"),
                List.of("cuInit")
        );

        assertFalse(result.available());
        assertEquals("unavailable", result.status());
        assertTrue(result.blockers().contains("cuda-driver-library-unavailable"));
        assertEquals(List.of("nvcuda"), resolver.attempts);
    }

    @Test
    void probeReportsMissingSymbolsSeparatelyFromLibraryLoadFailure() {
        FakeHandle handle = new FakeHandle("nvcuda", "C:/Windows/System32/nvcuda.dll", Map.of("cuInit", 1L));
        FakeResolver resolver = new FakeResolver(Map.of("nvcuda", handle));

        CudaDriverApiProbeResult result = CudaDriverLibrary.probe(
                resolver,
                List.of("nvcuda"),
                List.of("cuInit", "cuModuleLoadDataEx")
        );

        assertFalse(result.available());
        assertEquals("missing-symbols", result.status());
        assertEquals(List.of("cuInit"), result.resolvedSymbols());
        assertEquals(List.of("cuModuleLoadDataEx"), result.missingSymbols());
        assertTrue(result.blockers().contains("cuda-driver-symbol-missing:cuModuleLoadDataEx"));
        assertTrue(handle.closed);
    }

    @Test
    void builtInModuleLoaderRegistryContainsDriverBridge() {
        assertTrue(CudaModuleLoaderBridgeRegistry.loadWithBuiltIns().loaders().stream()
                .anyMatch(loader -> "cuda-module-loader:driver".equals(loader.loaderId())));
    }

    @Test
    void moduleLoadCreatesCloseableDriverModuleHandle() {
        FakeHandle handle = new FakeHandle("nvcuda", "C:/Windows/System32/nvcuda.dll", requiredSymbols());
        FakeResolver resolver = new FakeResolver(Map.of("nvcuda", handle));
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();

        CudaModuleLoadResult result = CudaDriverLibrary.loadModuleFromPtx(
                moduleLoadRequest(),
                "cuda-module-loader:driver",
                resolver,
                invoker,
                List.of("nvcuda")
        );

        assertTrue(result.succeeded());
        assertEquals("cuda-driver-module-handle", result.moduleHandleKind());
        assertEquals("cuda-driver-function-handle", result.functionHandleKind());
        assertTrue(result.loadedModule() != null);
        assertEquals(0xCAFE_0001L, result.loadedModule().moduleHandle());
        assertEquals(0xCAFE_0002L, result.loadedModule().functionHandle());
        assertEquals(12040, result.loadedModule().driverVersionRaw());
        assertEquals("12.4", result.loadedModule().driverVersion());
        assertEquals("jtg_cuda_preview_kernel", invoker.kernelName);
        assertFalse(handle.closed);
        Map<String, String> fields = result.artifactFields("test.cuda.moduleLoad");
        assertEquals("12040", fields.get("runtime.cuda.loadedModule.driver.version.raw"));
        assertEquals("12.4", fields.get("runtime.cuda.loadedModule.driver.version"));
        assertEquals("8.0", fields.get("runtime.cuda.ptxCompatibility.ptx.version"));
        assertEquals("sm_86", fields.get("runtime.cuda.ptxCompatibility.target.architecture"));
        assertEquals("8.6", fields.get("runtime.cuda.ptxCompatibility.target.computeCapability"));
        assertEquals("passed", fields.get("runtime.cuda.ptxDeviceCompatibility.status"));
        assertEquals("8.6", fields.get("runtime.cuda.ptxDeviceCompatibility.device.computeCapability"));
        assertEquals("passed", fields.get("runtime.cuda.ptxDriverCompatibility.status"));
        assertEquals("12.0", fields.get("runtime.cuda.ptxDriverCompatibility.required.cudaRelease"));
        assertEquals("12040", fields.get("test.cuda.moduleLoad.driverLoadedModule.driver.version.raw"));
        assertEquals("12.4", fields.get("test.cuda.moduleLoad.driverLoadedModule.driver.version"));
        assertEquals("sm_86", fields.get("test.cuda.moduleLoad.driverLoadedModule.ptxCompatibility.target.architecture"));
        assertEquals("8.6", fields.get("test.cuda.moduleLoad.driverLoadedModule.ptxCompatibility.target.computeCapability"));
        assertEquals("passed", fields.get("test.cuda.moduleLoad.driverLoadedModule.ptxDeviceCompatibility.status"));
        assertEquals("passed", fields.get("test.cuda.moduleLoad.driverLoadedModule.ptxDriverCompatibility.status"));

        result.loadedModule().close();

        assertTrue(handle.closed);
        assertEquals(List.of(0xCAFE_0001L), invoker.unloadedModules);
        assertTrue(result.loadedModule().closed());
    }

    @Test
    void moduleLoadFailsClosedWhenDriverVersionCannotBeRead() {
        FakeHandle handle = new FakeHandle("nvcuda", "C:/Windows/System32/nvcuda.dll", requiredSymbols());
        FakeResolver resolver = new FakeResolver(Map.of("nvcuda", handle));
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        invoker.driverVersionStatus = 999;

        CudaModuleLoadResult result = CudaDriverLibrary.loadModuleFromPtx(
                moduleLoadRequest(),
                "cuda-module-loader:driver",
                resolver,
                invoker,
                List.of("nvcuda")
        );

        assertFalse(result.succeeded());
        assertEquals("failed", result.status());
        assertTrue(result.blockers().contains("cuda-driver-cuDriverGetVersion-failed:999"));
        assertTrue(handle.closed);
        assertTrue(invoker.unloadedModules.isEmpty());
    }

    @Test
    void moduleLoadRejectsPtxTargetNewerThanSelectedCudaDevice() {
        FakeHandle handle = new FakeHandle("nvcuda", "C:/Windows/System32/nvcuda.dll", requiredSymbols());
        FakeResolver resolver = new FakeResolver(Map.of("nvcuda", handle));
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();

        CudaModuleLoadResult result = CudaDriverLibrary.loadModuleFromPtx(
                moduleLoadRequest(
                        ".version 8.0\n.target sm_90\n.address_size 64\n",
                        cudaDevice("8.6")
                ),
                "cuda-module-loader:driver",
                resolver,
                invoker,
                List.of("nvcuda")
        );

        assertFalse(result.succeeded());
        assertEquals("unsupported", result.status());
        assertTrue(result.blockers().contains("cuda-ptx-target-too-new:ptx-sm_90:device-8.6"));
        assertEquals(0, invoker.moduleLoadCalls);
        assertTrue(handle.closed);
    }

    @Test
    void moduleLoadRejectsPtxIsaVersionNewerThanCudaDriverApi() {
        FakeHandle handle = new FakeHandle("nvcuda", "C:/Windows/System32/nvcuda.dll", requiredSymbols());
        FakeResolver resolver = new FakeResolver(Map.of("nvcuda", handle));
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        invoker.driverVersionRaw = 12030;

        CudaModuleLoadResult result = CudaDriverLibrary.loadModuleFromPtx(
                moduleLoadRequest(
                        ".version 8.4\n.target sm_86\n.address_size 64\n",
                        cudaDevice("8.6")
                ),
                "cuda-module-loader:driver",
                resolver,
                invoker,
                List.of("nvcuda")
        );

        assertFalse(result.succeeded());
        assertEquals("unsupported", result.status());
        assertTrue(result.blockers().contains(
                "cuda-driver-ptx-version-unsupported:ptx-8.4:driver-12.3:requires-12.4"
        ));
        assertEquals(0, invoker.moduleLoadCalls);
        assertTrue(handle.closed);
    }

    @Test
    void moduleLoadUnloadsModuleIfFunctionLookupFails() {
        FakeHandle handle = new FakeHandle("nvcuda", "C:/Windows/System32/nvcuda.dll", requiredSymbols());
        FakeResolver resolver = new FakeResolver(Map.of("nvcuda", handle));
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        invoker.moduleGetFunctionStatus = 209;

        CudaModuleLoadResult result = CudaDriverLibrary.loadModuleFromPtx(
                moduleLoadRequest(),
                "cuda-module-loader:driver",
                resolver,
                invoker,
                List.of("nvcuda")
        );

        assertFalse(result.succeeded());
        assertTrue(result.blockers().contains("cuda-driver-cuModuleGetFunction-failed:209"));
        assertEquals(List.of(0xCAFE_0001L), invoker.unloadedModules);
        assertTrue(handle.closed);
    }

    @Test
    void moduleLoadReportsMissingDriverSymbolsAsUnsupported() {
        FakeHandle handle = new FakeHandle("nvcuda", "C:/Windows/System32/nvcuda.dll", Map.of("cuInit", 1L));
        FakeResolver resolver = new FakeResolver(Map.of("nvcuda", handle));

        CudaModuleLoadResult result = CudaDriverLibrary.loadModuleFromPtx(
                moduleLoadRequest(),
                "cuda-module-loader:driver",
                resolver,
                new FakeDriverApiInvoker(),
                List.of("nvcuda")
        );

        assertFalse(result.succeeded());
        assertEquals("unsupported", result.status());
        assertTrue(result.blockers().contains("cuda-driver-symbols-missing"));
        assertTrue(result.blockers().contains("cuda-driver-symbol-missing:cuModuleLoadDataEx"));
        assertTrue(handle.closed);
    }

    private static Map<String, Long> requiredSymbols() {
        Map<String, Long> symbols = new LinkedHashMap<>();
        for (String symbol : CudaDriverLibrary.REQUIRED_MODULE_LOADER_SYMBOLS) {
            symbols.put(symbol, 0x1000L + symbols.size());
        }
        return symbols;
    }

    private static CudaModuleLoadRequest moduleLoadRequest() {
        return moduleLoadRequest(
                ".version 8.0\n.target sm_86\n.address_size 64\n",
                cudaDevice("8.6")
        );
    }

    private static CudaModuleLoadRequest moduleLoadRequest(String ptxSource, GpuRuntimeDeviceProfile deviceProfile) {
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                new GpuKernelDescriptor(
                        "jtg_cuda_preview_kernel",
                        "inline://tests/cuda-preview.cu",
                        "extern \"C\" __global__ void jtg_cuda_preview_kernel(const float* input, float* output) { }",
                        List.of()
                ),
                GpuRuntimeCompileOptions.cuda(
                        List.of("--gpu-architecture=compute_86"),
                        Map.of(GpuBackendCompileOptions.CUDA_MODULE_LOADER_PROPERTY, "driver"),
                        "off"
                ),
                deviceProfile
        );
        GpuBackendModuleArtifact ptx = GpuBackendModuleArtifact.ptx(
                ptxSource,
                "inline://tests/cuda-preview.ptx",
                "test-cuda-ptx"
        );
        CudaCompiledKernel compiledKernel = CudaCompiledKernel.nativeCompiled(
                compileRequest,
                GpuBackendModuleArtifact.cudaSource("", "inline://tests/cuda-preview.cu", "test"),
                CudaNativeCompilationResult.succeeded(
                        "cuda-native-compiler:test",
                        ptx,
                        "ptxas info : synthetic PTX bridge",
                        List.of("synthetic PTX bridge emitted PTX")
                )
        );
        return CudaModuleLoadRequest.from(compiledKernel, CudaExecutionPlan.empty());
    }

    private static GpuRuntimeDeviceProfile cudaDevice(String computeCapability) {
        return GpuRuntimeDeviceProfile.cuda(
                "cuda-0",
                "RTX Test",
                "NVIDIA",
                "551.86",
                "CUDA 12.4, compute capability " + computeCapability,
                GpuDeviceClassTarget.DGPU,
                12L * 1024L * 1024L * 1024L,
                "NVIDIA CUDA",
                "driver 551.86, CUDA 12.4"
        );
    }

    private static final class FakeResolver implements CudaDriverLibrary.SharedLibraryResolver {
        private final Map<String, FakeHandle> handles;
        private final List<String> attempts = new ArrayList<>();

        private FakeResolver(Map<String, FakeHandle> handles) {
            this.handles = handles;
        }

        @Override
        public CudaDriverLibrary.SharedLibraryHandle open(String libraryName) {
            attempts.add(libraryName);
            FakeHandle handle = handles.get(libraryName);
            if (handle == null) {
                throw new IllegalStateException("candidate unavailable: " + libraryName);
            }
            return handle;
        }
    }

    private static final class FakeHandle implements CudaDriverLibrary.SharedLibraryHandle {
        private final String loadedName;
        private final String path;
        private final Map<String, Long> symbols;
        private boolean closed;

        private FakeHandle(String loadedName, String path, Map<String, Long> symbols) {
            this.loadedName = loadedName;
            this.path = path;
            this.symbols = symbols;
        }

        @Override
        public String loadedName() {
            return loadedName;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public long findSymbol(String symbolName) {
            return symbols.getOrDefault(symbolName, 0L);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FakeDriverApiInvoker implements CudaDriverLibrary.DriverApiInvoker {
        private final List<Long> unloadedModules = new ArrayList<>();
        private int initStatus;
        private int driverVersionStatus;
        private int driverVersionRaw = 12040;
        private int moduleLoadCalls;
        private int moduleLoadStatus;
        private int moduleGetFunctionStatus;
        private int moduleUnloadStatus;
        private String kernelName;

        @Override
        public int cuInit(int flags, long functionAddress) {
            return initStatus;
        }

        @Override
        public int cuDriverGetVersion(long versionOutAddress, long functionAddress) {
            if (driverVersionStatus == 0) {
                MemoryUtil.memPutInt(versionOutAddress, driverVersionRaw);
            }
            return driverVersionStatus;
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
            moduleLoadCalls++;
            if (moduleLoadStatus == 0) {
                MemoryUtil.memPutAddress(moduleOutAddress, 0xCAFE_0001L);
            }
            return moduleLoadStatus;
        }

        @Override
        public int cuModuleGetFunction(
                long functionOutAddress,
                long moduleHandle,
                long kernelNameAddress,
                long functionAddress
        ) {
            kernelName = MemoryUtil.memUTF8(kernelNameAddress);
            if (moduleGetFunctionStatus == 0) {
                MemoryUtil.memPutAddress(functionOutAddress, 0xCAFE_0002L);
            }
            return moduleGetFunctionStatus;
        }

        @Override
        public int cuModuleUnload(long moduleHandle, long functionAddress) {
            unloadedModules.add(moduleHandle);
            return moduleUnloadStatus;
        }
    }
}
