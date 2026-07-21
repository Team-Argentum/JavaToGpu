package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hardware-free CUDA image/sampler contract: recognized today, but fail-closed until a CUDA ABI exists.
 */
public record CudaImageSamplerContractReport(List<Case> cases) {

    public record Case(
            String key,
            List<String> expectedBlockers,
            CudaArgumentBindingResult bindingResult
    ) {
        public Case {
            key = key == null || key.isBlank() ? "unknown" : key.trim();
            expectedBlockers = expectedBlockers == null ? List.of() : List.copyOf(expectedBlockers);
        }

        public boolean ready() {
            return bindingResult != null
                    && "unsupported".equals(bindingResult.status())
                    && bindingResult.argumentFrame() == null
                    && bindingResult.blockers().containsAll(expectedBlockers);
        }

        public String status() {
            return ready() ? "ready" : "blocked";
        }

        public String firstBlocker() {
            if (ready()) {
                return "none";
            }
            if (bindingResult == null) {
                return "cuda-image-sampler-contract-result-missing:" + key;
            }
            if (!"unsupported".equals(bindingResult.status())) {
                return "cuda-image-sampler-contract-status-mismatch:" + key + ":" + bindingResult.status();
            }
            if (bindingResult.argumentFrame() != null) {
                return "cuda-image-sampler-contract-frame-created:" + key;
            }
            Optional<String> missing = expectedBlockers.stream()
                    .filter(blocker -> !bindingResult.blockers().contains(blocker))
                    .findFirst();
            return missing.map(blocker -> "cuda-image-sampler-contract-blocker-missing:" + key + ":" + blocker)
                    .orElse("cuda-image-sampler-contract-unknown:" + key);
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerContract.case"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".key", key);
            fields.put(normalizedPrefix + ".status", status());
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".expected.blocker.count", Integer.toString(expectedBlockers.size()));
            for (int index = 0; index < expectedBlockers.size(); index++) {
                fields.put(normalizedPrefix + ".expected.blocker." + index, expectedBlockers.get(index));
            }
            fields.put(normalizedPrefix + ".actual.status", bindingResult == null ? "missing" : bindingResult.status());
            fields.put(normalizedPrefix + ".actual.argumentFrame.present", Boolean.toString(bindingResult != null && bindingResult.argumentFrame() != null));
            fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
            if (bindingResult != null) {
                fields.putAll(bindingResult.artifactFields(normalizedPrefix + ".binding"));
            }
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerContractReport {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public static CudaImageSamplerContractReport inspectBuiltIns() {
        ArrayList<Case> cases = new ArrayList<>();
        cases.add(runCase(
                "image2d-read-only",
                List.of(new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY)),
                new Object[]{Image2DReadOnly.borrowed(0xCAFE_2001L, 8, 4)},
                List.of("cuda-driver-image-argument-unsupported:0:" + Image2DReadOnly.class.getName())
        ));
        cases.add(runCase(
                "image2d-write-only",
                List.of(new GpuKernelParameterDescriptor("outputImage", Image2DWriteOnly.class.getName(), GpuKernelParameterAccess.READ_WRITE)),
                new Object[]{Image2DWriteOnly.borrowed(0xCAFE_2002L, 8, 4)},
                List.of("cuda-driver-image-argument-unsupported:0:" + Image2DWriteOnly.class.getName())
        ));
        cases.add(runCase(
                "sampler-value",
                List.of(new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE)),
                new Object[]{Sampler.borrowed(0xCAFE_2003L)},
                List.of("cuda-driver-sampler-argument-unsupported:0:" + Sampler.class.getName())
        ));
        cases.add(runCase(
                "image-sampler-mixed",
                List.of(
                        new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", Image2DWriteOnly.class.getName(), GpuKernelParameterAccess.READ_WRITE)
                ),
                new Object[]{
                        Image2DReadOnly.borrowed(0xCAFE_2004L, 8, 4),
                        Sampler.borrowed(0xCAFE_2005L),
                        Image2DWriteOnly.borrowed(0xCAFE_2006L, 8, 4)
                },
                List.of(
                        "cuda-driver-image-argument-unsupported:0:" + Image2DReadOnly.class.getName(),
                        "cuda-driver-sampler-argument-unsupported:1:" + Sampler.class.getName(),
                        "cuda-driver-image-argument-unsupported:2:" + Image2DWriteOnly.class.getName()
                )
        ));
        return new CudaImageSamplerContractReport(cases);
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
            return "cuda-image-sampler-contract-cases-missing";
        }
        return cases.stream()
                .filter(testCase -> !testCase.ready())
                .map(Case::firstBlocker)
                .findFirst()
                .orElse("none");
    }

    public long runtimeBindingPlanEntryCount() {
        return cases.stream()
                .map(Case::bindingResult)
                .filter(result -> result != null && result.imageSamplerRuntimeBindingPlan().present())
                .mapToLong(result -> result.imageSamplerRuntimeBindingPlan().entries().size())
                .sum();
    }

    public long runtimeBindingPlannedKernelParameterSlotCount() {
        return cases.stream()
                .map(Case::bindingResult)
                .filter(result -> result != null && result.imageSamplerRuntimeBindingPlan().present())
                .mapToLong(result -> result.imageSamplerRuntimeBindingPlan().plannedRuntimeKernelParameterSlotCount())
                .sum();
    }

    public long runtimeBindingKernelParameterSlotCount() {
        return cases.stream()
                .map(Case::bindingResult)
                .filter(result -> result != null && result.imageSamplerRuntimeBindingPlan().present())
                .mapToLong(result -> result.imageSamplerRuntimeBindingPlan().runtimeBindingKernelParameterSlotCount())
                .sum();
    }

    public long sourcePreviewKernelParameterSlotCount() {
        return cases.stream()
                .map(Case::bindingResult)
                .filter(result -> result != null && result.imageSamplerRuntimeBindingPlan().present())
                .mapToLong(result -> result.imageSamplerRuntimeBindingPlan().sourcePreviewKernelParameterSlotCount())
                .sum();
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerContract"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
        fields.put(normalizedPrefix + ".case.count", Integer.toString(cases.size()));
        fields.put(normalizedPrefix + ".case.ready.count", Long.toString(readyCount()));
        fields.put(normalizedPrefix + ".case.blocked.count", Long.toString(blockedCount()));
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        fields.put(normalizedPrefix + ".productionSupport.enabled", "false");
        fields.put(normalizedPrefix + ".runtimeBindingPlan.entry.count", Long.toString(runtimeBindingPlanEntryCount()));
        fields.put(normalizedPrefix + ".runtimeBindingPlan.sourcePreviewKernelParameterSlot.count", Long.toString(sourcePreviewKernelParameterSlotCount()));
        fields.put(normalizedPrefix + ".runtimeBindingPlan.plannedKernelParameterSlot.count", Long.toString(runtimeBindingPlannedKernelParameterSlotCount()));
        fields.put(normalizedPrefix + ".runtimeBindingPlan.kernelParameterSlot.count", Long.toString(runtimeBindingKernelParameterSlotCount()));
        for (int index = 0; index < cases.size(); index++) {
            fields.putAll(cases.get(index).artifactFields(normalizedPrefix + ".case." + index));
        }
        fields.put("runtime.cuda.imageSamplerContract.present", "true");
        fields.put("runtime.cuda.imageSamplerContract.status", status());
        fields.put("runtime.cuda.imageSamplerContract.ready", Boolean.toString(ready()));
        fields.put("runtime.cuda.imageSamplerContract.case.count", Integer.toString(cases.size()));
        fields.put("runtime.cuda.imageSamplerContract.case.ready.count", Long.toString(readyCount()));
        fields.put("runtime.cuda.imageSamplerContract.case.blocked.count", Long.toString(blockedCount()));
        fields.put("runtime.cuda.imageSamplerContract.productionSupport.enabled", "false");
        fields.put("runtime.cuda.imageSamplerContract.runtimeBindingPlan.entry.count", Long.toString(runtimeBindingPlanEntryCount()));
        fields.put("runtime.cuda.imageSamplerContract.runtimeBindingPlan.sourcePreviewKernelParameterSlot.count", Long.toString(sourcePreviewKernelParameterSlotCount()));
        fields.put("runtime.cuda.imageSamplerContract.runtimeBindingPlan.plannedKernelParameterSlot.count", Long.toString(runtimeBindingPlannedKernelParameterSlotCount()));
        fields.put("runtime.cuda.imageSamplerContract.runtimeBindingPlan.kernelParameterSlot.count", Long.toString(runtimeBindingKernelParameterSlotCount()));
        fields.put("runtime.cuda.imageSamplerContract.firstBlocker", firstBlocker());
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler contract: ").append(status()).append('\n');
        builder.append("Cases: ").append(readyCount()).append('/').append(cases.size()).append(" ready").append('\n');
        builder.append("Production support: disabled").append('\n');
        builder.append("Runtime binding plan: ")
                .append(runtimeBindingPlanEntryCount())
                .append(" entries, ")
                .append(runtimeBindingPlannedKernelParameterSlotCount())
                .append(" planned slots, ")
                .append(runtimeBindingKernelParameterSlotCount())
                .append(" active slots")
                .append('\n');
        builder.append("First blocker: ").append(firstBlocker()).append('\n');
        builder.append('\n').append("Checks:").append('\n');
        for (Case testCase : cases) {
            builder.append("- ")
                    .append(testCase.key())
                    .append(": ")
                    .append(testCase.status())
                    .append(" (`actual=")
                    .append(testCase.bindingResult() == null ? "missing" : testCase.bindingResult().status())
                    .append(", firstBlocker=")
                    .append(testCase.firstBlocker())
                    .append("`)")
                    .append('\n');
        }
        return builder.toString();
    }

    private static Case runCase(
            String key,
            List<GpuKernelParameterDescriptor> parameters,
            Object[] arguments,
            List<String> expectedBlockers
    ) {
        CudaDriverLoadedModule loadedModule = loadedModule();
        try {
            return new Case(
                    key,
                    expectedBlockers,
                    new CudaDriverArgumentBinderBridge().bind(bindingRequest(key, parameters, arguments, loadedModule))
            );
        } finally {
            loadedModule.close();
        }
    }

    private static CudaArgumentBindingRequest bindingRequest(
            String key,
            List<GpuKernelParameterDescriptor> parameters,
            Object[] arguments,
            CudaDriverLoadedModule loadedModule
    ) {
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                new GpuKernelDescriptor(
                        "jtg_cuda_image_sampler_contract_" + key.replace('-', '_'),
                        "inline://cuda/image-sampler-contract.cu",
                        "extern \"C\" __global__ void jtg_cuda_image_sampler_contract() { }",
                        parameters
                ),
                GpuRuntimeCompileOptions.cuda(
                        List.of("--gpu-architecture=compute_86"),
                        Map.of(GpuBackendCompileOptions.CUDA_ARGUMENT_BINDER_PROPERTY, "driver"),
                        "off"
                ),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA")
        );
        CudaCompiledKernel compiledKernel = CudaCompiledKernel.nativeCompiled(
                compileRequest,
                GpuBackendModuleArtifact.cudaSource("", "inline://cuda/image-sampler-contract.cu", key),
                CudaNativeCompilationResult.succeeded(
                        "cuda-native-compiler:contract",
                        GpuBackendModuleArtifact.ptx(
                                ".version 8.0\n.target sm_86\n.address_size 64\n",
                                "inline://cuda/image-sampler-contract.ptx",
                                key
                        ),
                        "synthetic CUDA image/sampler contract compiler receipt",
                        List.of("synthetic compile receipt for image/sampler contract validation")
                )
        );
        CudaModuleLoadResult moduleLoadResult = CudaModuleLoadResult.succeeded(
                "cuda-module-loader:contract",
                loadedModule,
                List.of("synthetic CUDA image/sampler contract module")
        );
        return CudaArgumentBindingRequest.from(
                compiledKernel,
                moduleLoadResult,
                CudaExecutionPlan.from(new GpuKernelInvocation(compileRequest.descriptor(), arguments))
        );
    }

    private static CudaDriverLoadedModule loadedModule() {
        return new CudaDriverLoadedModule(
                "cuda-module-loader:contract",
                "synthetic-nvcuda",
                "inline://cuda/image-sampler-contract",
                "jtg_cuda_image_sampler_contract",
                0xCADA_1001L,
                0xCADA_1002L,
                0xCADA_1003L,
                new SyntheticLibraryHandle(),
                new SyntheticDriverApiInvoker(),
                List.of("synthetic CUDA Driver API handles for image/sampler contract validation")
        );
    }

    private static final class SyntheticLibraryHandle implements CudaDriverLibrary.SharedLibraryHandle {
        @Override
        public String loadedName() {
            return "synthetic-nvcuda";
        }

        @Override
        public String path() {
            return "inline://cuda/image-sampler-contract";
        }

        @Override
        public long findSymbol(String symbolName) {
            return 0x1000L;
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
    }
}
