package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationBindingSummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hardware-free CUDA launch contract checks for the staged Driver API launcher.
 */
public record CudaLaunchContractReport(List<Case> cases) {

    public record Case(
            String key,
            String expectedStatus,
            Optional<String> expectedBlocker,
            CudaKernelLaunchResult result
    ) {
        public Case {
            key = key == null || key.isBlank() ? "unknown" : key.trim();
            expectedStatus = expectedStatus == null || expectedStatus.isBlank() ? "unknown" : expectedStatus.trim();
            expectedBlocker = expectedBlocker == null ? Optional.empty() : expectedBlocker;
        }

        public boolean ready() {
            if (result == null || !expectedStatus.equals(result.status())) {
                return false;
            }
            return expectedBlocker.map(blocker -> result.blockers().contains(blocker)).orElseGet(result.blockers()::isEmpty);
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public String firstBlocker() {
            if (ready()) {
                return "none";
            }
            if (result == null) {
                return "cuda-launch-contract-result-missing:" + key;
            }
            if (!expectedStatus.equals(result.status())) {
                return "cuda-launch-contract-status-mismatch:" + key + ":" + result.status();
            }
            return expectedBlocker
                    .map(blocker -> "cuda-launch-contract-blocker-missing:" + key + ":" + blocker)
                    .orElse("cuda-launch-contract-unexpected-blocker:" + key + ":" + result.blockers());
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.launchContract.case"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".key", key);
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".expected.status", expectedStatus);
            fields.put(normalizedPrefix + ".expected.blocker", expectedBlocker.orElse("none"));
            fields.put(normalizedPrefix + ".actual.status", result == null ? "missing" : result.status());
            fields.put(normalizedPrefix + ".actual.firstBlocker", result == null
                    ? "missing"
                    : result.firstBlocker().orElse("none"));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
            if (result != null) {
                fields.putAll(result.artifactFields(normalizedPrefix + ".result"));
            }
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaLaunchContractReport {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public static CudaLaunchContractReport inspectBuiltIns() {
        CudaDriverKernelLauncherBridge launcher = new CudaDriverKernelLauncherBridge();
        ArrayList<Case> cases = new ArrayList<>();
        cases.add(runCase(
                launcher,
                "valid-1d",
                "succeeded",
                Optional.empty(),
                GpuExecutionConfig.oneDimensional(64L, 16L),
                0L,
                cudaDeviceProfile(1_024L, 1_024L)
        ));
        cases.add(runCase(
                launcher,
                "valid-3d",
                "succeeded",
                Optional.empty(),
                GpuExecutionConfig.threeDimensional(32L, 16L, 8L, 8L, 4L, 2L),
                0L,
                cudaDeviceProfile(1_024L, 1_024L)
        ));
        cases.add(runCase(
                launcher,
                "dynamic-shared-memory",
                "succeeded",
                Optional.empty(),
                GpuExecutionConfig.oneDimensional(32L, 8L),
                32L,
                cudaDeviceProfile(1_024L, 1_024L)
        ));
        cases.add(runCase(
                launcher,
                "auto-local-rejected",
                "unsupported",
                Optional.of("cuda-driver-launch-local-size-required"),
                GpuExecutionConfig.oneDimensional(64L),
                0L,
                cudaDeviceProfile(1_024L, 1_024L)
        ));
        cases.add(runCase(
                launcher,
                "non-divisible-x-rejected",
                "unsupported",
                Optional.of("cuda-driver-launch-global-local-mismatch:x"),
                GpuExecutionConfig.oneDimensional(10L, 4L),
                0L,
                cudaDeviceProfile(1_024L, 1_024L)
        ));
        cases.add(runCase(
                launcher,
                "block-limit-rejected",
                "unsupported",
                Optional.of("cuda-driver-launch-block-item-count-exceeds-device"),
                GpuExecutionConfig.oneDimensional(64L, 32L),
                0L,
                cudaDeviceProfile(1_024L, 16L)
        ));
        cases.add(runCase(
                launcher,
                "shared-memory-limit-rejected",
                "unsupported",
                Optional.of("cuda-driver-launch-shared-memory-exceeds-device-local-memory"),
                GpuExecutionConfig.oneDimensional(32L, 8L),
                32L,
                cudaDeviceProfile(16L, 1_024L)
        ));
        cases.add(runCase(
                launcher,
                "shared-memory-int-range-rejected",
                "unsupported",
                Optional.of("cuda-driver-launch-shared-memory-too-large"),
                GpuExecutionConfig.oneDimensional(32L, 8L),
                (long) Integer.MAX_VALUE + 1L,
                cudaDeviceProfile(0L, 1_024L)
        ));
        return new CudaLaunchContractReport(cases);
    }

    public boolean ready() {
        return !cases.isEmpty() && cases.stream().allMatch(Case::ready);
    }

    public String status() {
        return ready() ? "ready" : "blocked";
    }

    public long readyCount() {
        return cases.stream().filter(Case::ready).count();
    }

    public long blockedCount() {
        return cases.stream().filter(testCase -> !testCase.ready()).count();
    }

    public String firstBlocker() {
        if (cases.isEmpty()) {
            return "cuda-launch-contract-cases-missing";
        }
        return cases.stream()
                .filter(testCase -> !testCase.ready())
                .map(Case::firstBlocker)
                .findFirst()
                .orElse("none");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.launchContract"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
        fields.put(normalizedPrefix + ".case.count", Integer.toString(cases.size()));
        fields.put(normalizedPrefix + ".case.ready.count", Long.toString(readyCount()));
        fields.put(normalizedPrefix + ".case.blocked.count", Long.toString(blockedCount()));
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < cases.size(); index++) {
            fields.putAll(cases.get(index).artifactFields(normalizedPrefix + ".case." + index));
        }
        fields.put("runtime.cuda.launchContract.present", "true");
        fields.put("runtime.cuda.launchContract.status", status());
        fields.put("runtime.cuda.launchContract.ready", Boolean.toString(ready()));
        fields.put("runtime.cuda.launchContract.case.count", Integer.toString(cases.size()));
        fields.put("runtime.cuda.launchContract.case.ready.count", Long.toString(readyCount()));
        fields.put("runtime.cuda.launchContract.case.blocked.count", Long.toString(blockedCount()));
        fields.put("runtime.cuda.launchContract.firstBlocker", firstBlocker());
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA launch contract: ").append(status()).append('\n');
        builder.append("Cases: ").append(readyCount()).append('/').append(cases.size()).append(" ready").append('\n');
        builder.append("First blocker: ").append(firstBlocker()).append('\n');
        builder.append('\n').append("Checks:").append('\n');
        for (Case testCase : cases) {
            builder.append("- ")
                    .append(testCase.key())
                    .append(": ")
                    .append(testCase.status())
                    .append(" (`expected=")
                    .append(testCase.expectedStatus())
                    .append(", actual=")
                    .append(testCase.result() == null ? "missing" : testCase.result().status())
                    .append(", firstBlocker=")
                    .append(testCase.firstBlocker())
                    .append("`)")
                    .append('\n');
        }
        return builder.toString();
    }

    private static Case runCase(
            CudaDriverKernelLauncherBridge launcher,
            String key,
            String expectedStatus,
            Optional<String> expectedBlocker,
            GpuExecutionConfig executionConfig,
            long sharedMemoryBytes,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        SyntheticDriverApiInvoker invoker = new SyntheticDriverApiInvoker();
        CudaDriverLoadedModule loadedModule = loadedModule(invoker);
        CudaKernelArgumentFrame frame = null;
        try {
            frame = CudaKernelArgumentFrame.nativeBindings(
                    "cuda-argument-binder:contract",
                    new GpuRuntimeInvocationBindingSummary(0, sharedMemoryBytes > 0L ? 1 : 0, 0, 1),
                    List.of(),
                    null,
                    List.of(),
                    List.of(),
                    sharedMemoryBytes
            );
            CudaArgumentBindingResult bindingResult = CudaArgumentBindingResult.succeeded(
                    "cuda-argument-binder:contract",
                    frame.bindingSummary(),
                    frame,
                    List.of("synthetic CUDA launch contract argument frame")
            );
            CudaPreparedKernel preparedKernel = new CudaPreparedKernel(
                    compiledKernel(deviceProfile),
                    CudaModuleLoadResult.succeeded(
                            "cuda-module-loader:contract",
                            loadedModule,
                            List.of("synthetic CUDA launch contract module")
                    ),
                    bindingResult,
                    bindingResult.bindingSummary()
            );
            return new Case(
                    key,
                    expectedStatus,
                    expectedBlocker,
                    launcher.launch(CudaKernelLaunchRequest.from(preparedKernel, executionConfig))
            );
        } finally {
            if (frame != null) {
                frame.close();
            }
            loadedModule.close();
        }
    }

    private static CudaDriverLoadedModule loadedModule(SyntheticDriverApiInvoker invoker) {
        return new CudaDriverLoadedModule(
                "cuda-module-loader:contract",
                "synthetic-nvcuda",
                "inline://cuda/launch-contract",
                "jtg_cuda_launch_contract",
                0xCAFE_1001L,
                0xCAFE_1002L,
                0xCAFE_1003L,
                new SyntheticLibraryHandle(),
                invoker,
                List.of("synthetic CUDA Driver API handles for launch contract validation")
        );
    }

    private static CudaCompiledKernel compiledKernel(GpuRuntimeDeviceProfile deviceProfile) {
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                new GpuKernelDescriptor(
                        "jtg_cuda_launch_contract",
                        "inline://cuda/launch-contract.cu",
                        "extern \"C\" __global__ void jtg_cuda_launch_contract() { }",
                        List.of()
                ),
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
                deviceProfile
        );
        GpuBackendModuleArtifact ptx = GpuBackendModuleArtifact.ptx(
                ".version 8.0\n.target sm_86\n.address_size 64\n",
                "inline://cuda/launch-contract.ptx",
                "cuda-launch-contract"
        );
        return CudaCompiledKernel.nativeCompiled(
                request,
                GpuBackendModuleArtifact.cudaSource(
                        "extern \"C\" __global__ void jtg_cuda_launch_contract() { }",
                        "inline://cuda/launch-contract.cu",
                        "cuda-launch-contract-source"
                ),
                CudaNativeCompilationResult.succeeded(
                        "cuda-native-compiler:contract",
                        ptx,
                        "synthetic CUDA launch contract compiler receipt",
                        List.of("synthetic compile receipt for launch contract validation")
                )
        );
    }

    private static GpuRuntimeDeviceProfile cudaDeviceProfile(long localMemoryBytes, long maxWorkGroupSize) {
        return new GpuRuntimeDeviceProfile(
                GpuBackendTarget.CUDA,
                "CUDA",
                "synthetic CUDA contract device",
                "NVIDIA",
                "synthetic-driver",
                "CUDA synthetic",
                16L,
                localMemoryBytes,
                maxWorkGroupSize,
                1L,
                false,
                false,
                false
        );
    }

    private static final class SyntheticLibraryHandle implements CudaDriverLibrary.SharedLibraryHandle {
        @Override
        public String loadedName() {
            return "synthetic-nvcuda";
        }

        @Override
        public String path() {
            return "inline://cuda/launch-contract";
        }

        @Override
        public long findSymbol(String symbolName) {
            return "cuLaunchKernel".equals(symbolName) ? 0x1000L : 0L;
        }

        @Override
        public void close() {
        }
    }

    private static final class SyntheticDriverApiInvoker implements CudaDriverLibrary.DriverApiInvoker {
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

        @Override
        public int cuMemFree(long devicePointer, long functionAddress) {
            return CudaDriverLibrary.CUDA_SUCCESS;
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
            return CudaDriverLibrary.CUDA_SUCCESS;
        }
    }
}
