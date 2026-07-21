package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.Float2;
import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompiledKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipeline;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipelineResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLoweringResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceSelectionPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuMemorySlice;
import net.sixik.ga_utils.javatogpu.runtime.GpuPreparedKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestMode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CudaDriverExecutionIntegrationTest {

    private static final String SMOKE_ARCH_PROPERTY = "javatogpu.cuda.smoke.arch";
    private static final String SMOKE_ARCH_ENVIRONMENT = "JTG_CUDA_SMOKE_ARCH";
    private static final String SMOKE_ENABLED_PROPERTY = "javatogpu.cuda.smoke.enabled";
    private static final String SMOKE_REQUIRED_PROPERTY = "javatogpu.cuda.smoke.required";
    private static final String NVCC_PATH_PROPERTY = "javatogpu.cuda.nvcc.path";
    private static final String NVCC_PATH_ENVIRONMENT = "JTG_CUDA_NVCC";
    private static final String NVCC_OUTPUT_FORMAT_PROPERTY = "javatogpu.cuda.nvcc.outputFormat";
    private static final String NVCC_OUTPUT_FORMAT_ENVIRONMENT = "JTG_CUDA_NVCC_OUTPUT_FORMAT";
    private static final String SMOKE_EVIDENCE_PREFIX = "JTG_CUDA_SMOKE_EVIDENCE";

    @Test
    void executesFloatArrayScalarKernelThroughCudaDriverPipelineWhenAvailable() {
        CudaSmokeEnvironment environment = assumeCudaSmokeEnvironment();
        String kernelName = "jtg_cuda_smoke_float_kernel";
        String source = """
                extern "C" __global__ void jtg_cuda_smoke_float_kernel(const float* input, float scale, float* output) {
                    int id = (blockIdx.x * blockDim.x) + threadIdx.x;
                    output[id] = input[id] * scale;
                }
                """;
        GpuKernelDescriptor descriptor = descriptor(
                kernelName,
                source,
                List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        float[] input = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
        float[] output = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

        try (GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result = executeSmoke(
                environment,
                descriptor,
                source,
                new Object[]{input, 2.5f, output},
                GpuExecutionConfig.oneDimensional(input.length, input.length)
        )) {
            assumeOrFailSucceeded(result, "primitive-array-scalar");
            assertArrayEquals(new float[]{2.5f, 5.0f, 7.5f, 10.0f}, output, 0.0001f);
            assertTrue(result.invocationResult().readbackComplete());
            assertLaunchShape(result, "1x1x1", "4x1x1");
        }
    }

    @Test
    void executesFloatArraySliceKernelThroughCudaDriverPipelineWhenAvailable() {
        CudaSmokeEnvironment environment = assumeCudaSmokeEnvironment();
        String kernelName = "jtg_cuda_smoke_float_slice_kernel";
        String source = """
                extern "C" __global__ void jtg_cuda_smoke_float_slice_kernel(const float* input, float* output) {
                    int id = (blockIdx.x * blockDim.x) + threadIdx.x;
                    output[id] = input[id] + 10.0f;
                }
                """;
        GpuKernelDescriptor descriptor = descriptor(
                kernelName,
                source,
                List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        float[] input = new float[]{100.0f, 1.0f, 2.0f, 3.0f, 500.0f};
        float[] output = new float[]{-1.0f, 0.0f, 0.0f, 0.0f, -5.0f};

        try (GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result = executeSmoke(
                environment,
                descriptor,
                source,
                new Object[]{GpuMemorySlice.of(input, 1, 3), GpuMemorySlice.of(output, 1, 3)},
                GpuExecutionConfig.oneDimensional(3L, 3L)
        )) {
            assumeOrFailSucceeded(result, "primitive-array-slice");
            assertArrayEquals(new float[]{-1.0f, 11.0f, 12.0f, 13.0f, -5.0f}, output, 0.0001f);
            assertTrue(result.invocationResult().readbackComplete());
            assertLaunchShape(result, "1x1x1", "3x1x1");
            assertEquals("true", result.artifactFields("cudaSmoke")
                    .get("runtime.cuda.argumentFrame.deviceAllocation.0.hostSlice.enabled"));
            assertEquals("1", result.artifactFields("cudaSmoke")
                    .get("runtime.cuda.argumentFrame.deviceAllocation.0.hostElement.offset"));
            assertEquals("4", result.artifactFields("cudaSmoke")
                    .get("runtime.cuda.argumentFrame.deviceAllocation.0.hostElement.endExclusive"));
            assertEquals("3", result.artifactFields("cudaSmoke")
                    .get("runtime.cuda.argumentFrame.deviceAllocation.0.element.count"));
        }
    }

    @Test
    void executesFloat2ArrayKernelThroughCudaDriverPipelineWhenAvailable() {
        CudaSmokeEnvironment environment = assumeCudaSmokeEnvironment();
        String kernelName = "jtg_cuda_smoke_float2_kernel";
        String source = """
                #include <cuda_runtime.h>

                extern "C" __global__ void jtg_cuda_smoke_float2_kernel(const float2* input, float2* output) {
                    int id = (blockIdx.x * blockDim.x) + threadIdx.x;
                    float2 value = input[id];
                    output[id] = make_float2(value.x + 1.0f, value.y * 2.0f);
                }
                """;
        GpuKernelDescriptor descriptor = descriptor(
                kernelName,
                source,
                List.of(
                        new GpuKernelParameterDescriptor("input", Float2.class.getName() + "[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", Float2.class.getName() + "[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        Float2[] input = new Float2[]{new Float2(1.0f, 2.0f), new Float2(3.0f, 4.0f)};
        Float2[] output = new Float2[]{new Float2(), new Float2()};

        try (GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result = executeSmoke(
                environment,
                descriptor,
                source,
                new Object[]{input, output},
                GpuExecutionConfig.oneDimensional(input.length, input.length)
        )) {
            assumeOrFailSucceeded(result, "vector-array");
            assertEquals(2.0f, output[0].x, 0.0001f);
            assertEquals(4.0f, output[0].y, 0.0001f);
            assertEquals(4.0f, output[1].x, 0.0001f);
            assertEquals(8.0f, output[1].y, 0.0001f);
            assertTrue(result.invocationResult().readbackComplete());
            assertLaunchShape(result, "1x1x1", "2x1x1");
        }
    }

    @Test
    void executesStructArrayKernelThroughCudaDriverPipelineWhenAvailable() {
        CudaSmokeEnvironment environment = assumeCudaSmokeEnvironment();
        String kernelName = "jtg_cuda_smoke_struct_kernel";
        String source = """
                typedef struct CudaSmokeParticle {
                    float weight;
                    int count;
                } CudaSmokeParticle;

                extern "C" __global__ void jtg_cuda_smoke_struct_kernel(const CudaSmokeParticle* input, CudaSmokeParticle* output) {
                    int id = (blockIdx.x * blockDim.x) + threadIdx.x;
                    output[id].weight = input[id].weight + (float) input[id].count;
                    output[id].count = input[id].count + 10;
                }
                """;
        GpuKernelDescriptor descriptor = descriptor(
                kernelName,
                source,
                List.of(
                        new GpuKernelParameterDescriptor("input", CudaSmokeParticle.class.getName() + "[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", CudaSmokeParticle.class.getName() + "[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        CudaSmokeParticle[] input = new CudaSmokeParticle[]{
                new CudaSmokeParticle(1.5f, 2),
                new CudaSmokeParticle(3.5f, 4)
        };
        CudaSmokeParticle[] output = new CudaSmokeParticle[]{new CudaSmokeParticle(), new CudaSmokeParticle()};

        try (GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result = executeSmoke(
                environment,
                descriptor,
                source,
                new Object[]{input, output},
                GpuExecutionConfig.oneDimensional(input.length, input.length)
        )) {
            assumeOrFailSucceeded(result, "struct-array");
            assertEquals(3.5f, output[0].weight, 0.0001f);
            assertEquals(12, output[0].count);
            assertEquals(7.5f, output[1].weight, 0.0001f);
            assertEquals(14, output[1].count);
            assertTrue(result.invocationResult().readbackComplete());
            assertLaunchShape(result, "1x1x1", "2x1x1");
        }
    }

    @Test
    void executesStructValueKernelThroughCudaDriverPipelineWhenAvailable() {
        CudaSmokeEnvironment environment = assumeCudaSmokeEnvironment();
        String kernelName = "jtg_cuda_smoke_struct_value_kernel";
        String source = """
                typedef struct CudaSmokeParticle {
                    float weight;
                    int count;
                } CudaSmokeParticle;

                extern "C" __global__ void jtg_cuda_smoke_struct_value_kernel(
                        CudaSmokeParticle particle,
                        float bias,
                        float* output) {
                    output[0] = particle.weight + (float) particle.count + bias;
                }
                """;
        GpuKernelDescriptor descriptor = descriptor(
                kernelName,
                source,
                List.of(
                        new GpuKernelParameterDescriptor("particle", CudaSmokeParticle.class.getName(), GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("bias", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        CudaSmokeParticle particle = new CudaSmokeParticle(2.5f, 4);
        float[] output = new float[]{0.0f};

        try (GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result = executeSmoke(
                environment,
                descriptor,
                source,
                new Object[]{particle, 1.25f, output},
                GpuExecutionConfig.oneDimensional(1L, 1L)
        )) {
            assumeOrFailSucceeded(result, "struct-value");
            assertArrayEquals(new float[]{7.75f}, output, 0.0001f);
            assertTrue(result.invocationResult().readbackComplete());
            assertLaunchShape(result, "1x1x1", "1x1x1");
            assertEquals("2", result.artifactFields("cudaSmoke")
                    .get("runtime.cuda.argumentFrame.scalarArgumentSlot.count"));
            assertEquals("12", result.artifactFields("cudaSmoke")
                    .get("runtime.cuda.argumentFrame.scalarArgumentSlot.byteSize"));
        }
    }

    @Test
    void executesMultiLocalSharedMemoryKernelThroughCudaDriverPipelineWhenAvailable() {
        CudaSmokeEnvironment environment = assumeCudaSmokeEnvironment();
        String kernelName = "jtg_cuda_smoke_multi_local_kernel";
        String source = """
                extern "C" __global__ void jtg_cuda_smoke_multi_local_kernel(
                        const float* input,
                        float* output,
                        unsigned int __jtg_local_scratchA_byte_offset,
                        unsigned int __jtg_local_scratchB_byte_offset) {
                    extern __shared__ __align__(8) unsigned char __jtg_cuda_dynamic_shared[];
                    float* scratchA = (float*)(__jtg_cuda_dynamic_shared + __jtg_local_scratchA_byte_offset);
                    int* scratchB = (int*)(__jtg_cuda_dynamic_shared + __jtg_local_scratchB_byte_offset);
                    int id = threadIdx.x;
                    scratchA[id] = input[id] + 1.0f;
                    scratchB[id] = id;
                    __syncthreads();
                    output[id] = scratchA[id] + (float) scratchB[id];
                }
                """;
        GpuKernelDescriptor descriptor = descriptor(
                kernelName,
                source,
                List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scratchA", "float[]", GpuKernelParameterAccess.LOCAL),
                        new GpuKernelParameterDescriptor("scratchB", "int[]", GpuKernelParameterAccess.LOCAL),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        float[] input = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
        float[] scratchA = new float[input.length];
        int[] scratchB = new int[input.length];
        float[] output = new float[input.length];

        try (GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result = executeSmoke(
                environment,
                descriptor,
                source,
                new Object[]{input, scratchA, scratchB, output},
                GpuExecutionConfig.oneDimensional(input.length, input.length)
        )) {
            assumeOrFailSucceeded(result, "multi-local-shared-memory");
            assertArrayEquals(new float[]{2.0f, 4.0f, 6.0f, 8.0f}, output, 0.0001f);
            assertTrue(result.invocationResult().readbackComplete());
            assertLaunchShape(result, "1x1x1", "4x1x1");
            assertEquals("true", result.artifactFields("cudaSmoke").get("runtime.cuda.kernelLaunch.sharedMemory.present"));
        }
    }

    @Test
    void executesStructLocalSharedMemoryKernelThroughCudaDriverPipelineWhenAvailable() {
        CudaSmokeEnvironment environment = assumeCudaSmokeEnvironment();
        String kernelName = "jtg_cuda_smoke_struct_local_kernel";
        String source = """
                typedef struct CudaSmokeParticle {
                    float weight;
                    int count;
                } CudaSmokeParticle;

                extern "C" __global__ void jtg_cuda_smoke_struct_local_kernel(
                        const float* input,
                        float* output) {
                    extern __shared__ CudaSmokeParticle scratch[];
                    int id = threadIdx.x;
                    scratch[id].weight = input[id] + 1.0f;
                    scratch[id].count = id;
                    __syncthreads();
                    output[id] = scratch[id].weight + (float) scratch[id].count;
                }
                """;
        GpuKernelDescriptor descriptor = descriptor(
                kernelName,
                source,
                List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scratch", CudaSmokeParticle.class.getName() + "[]", GpuKernelParameterAccess.LOCAL),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        float[] input = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
        CudaSmokeParticle[] scratch = new CudaSmokeParticle[]{
                new CudaSmokeParticle(),
                new CudaSmokeParticle(),
                new CudaSmokeParticle(),
                new CudaSmokeParticle()
        };
        float[] output = new float[input.length];

        try (GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result = executeSmoke(
                environment,
                descriptor,
                source,
                new Object[]{input, scratch, output},
                GpuExecutionConfig.oneDimensional(input.length, input.length)
        )) {
            assumeOrFailSucceeded(result, "local-struct");
            assertArrayEquals(new float[]{2.0f, 4.0f, 6.0f, 8.0f}, output, 0.0001f);
            assertTrue(result.invocationResult().readbackComplete());
            assertLaunchShape(result, "1x1x1", "4x1x1");
            assertEquals("true", result.artifactFields("cudaSmoke").get("runtime.cuda.kernelLaunch.sharedMemory.present"));
            assertEquals("32", result.artifactFields("cudaSmoke").get("runtime.cuda.kernelLaunch.sharedMemory.byteSize"));
        }
    }

    private static void assertLaunchShape(
            GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result,
            String expectedGridShape,
            String expectedBlockShape
    ) {
        assertEquals("true", result.artifactFields("cudaSmoke")
                .get("runtime.cuda.kernelLaunch.launchShape.present"));
        assertEquals(expectedGridShape, result.artifactFields("cudaSmoke")
                .get("runtime.cuda.kernelLaunch.launchShape.gridShape"));
        assertEquals(expectedBlockShape, result.artifactFields("cudaSmoke")
                .get("runtime.cuda.kernelLaunch.launchShape.blockShape"));
    }

    private static CudaSmokeEnvironment assumeCudaSmokeEnvironment() {
        Assumptions.assumeTrue(
                Boolean.getBoolean(SMOKE_ENABLED_PROPERTY),
                "CUDA driver execution smoke is opt-in; run :processor:integrationCudaSmokeTest to enable it"
        );
        GpuRuntimeCompileOptions discoveryOptions = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA)
                .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.DISABLED);
        GpuRuntimeDeviceDiscoveryResult discovery = CudaRuntimeDeviceDiscovery.discover(
                discoveryOptions,
                GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns()
        );
        Assumptions.assumeTrue(
                discovery.discoveryAvailable(),
                "CUDA driver execution smoke skipped: " + discovery.firstBlocker()
        );
        GpuRuntimeDeviceProfile selectedDevice = discovery.selectedDevice().orElse(null);
        Assumptions.assumeTrue(
                selectedDevice != null,
                "CUDA driver execution smoke skipped: CUDA discovery did not select a device"
        );
        String nvccOutputFormat = cudaNvccOutputFormat();
        String architecture = cudaSmokeArchitecture(selectedDevice, nvccOutputFormat);
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.cudaNvcc(
                        List.of("--gpu-architecture=" + architecture),
                        configuredNvccPath(),
                        "off"
                )
                .withCudaDriverModuleLoader()
                .withCudaDriverArgumentBinder()
                .withCudaDriverKernelLauncher()
                .withCudaDriverReadback()
                .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.DISABLED);
        return new CudaSmokeEnvironment(selectedDevice, options, architecture);
    }

    private static GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> executeSmoke(
            CudaSmokeEnvironment environment,
            GpuKernelDescriptor descriptor,
            String source,
            Object[] arguments,
            GpuExecutionConfig executionConfig
    ) {
        GpuBackendModuleArtifact moduleArtifact = GpuBackendModuleArtifact.cudaSource(
                source,
                descriptor.kernelResource(),
                "cuda-driver-execution-smoke"
        );
        GpuBackendLoweringResult loweringResult = GpuBackendLoweringResult.succeeded(
                moduleArtifact,
                GpuBackendSourceSelectionPlan.descriptorSource(
                        GpuBackendTarget.CUDA,
                        "cuda-c",
                        "CUDA driver integration smoke uses inline CUDA-C descriptor source"
                ),
                List.of("inline CUDA-C smoke source prepared for nvcc")
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                environment.options(),
                environment.selectedDevice()
        );
        CudaExecutionPlan executionPlan = CudaExecutionPlan.from(new GpuKernelInvocation(
                descriptor,
                arguments,
                executionConfig,
                environment.options()
        ));
        GpuBackendExecutionPipeline<GpuBackendCompiledKernel, GpuPreparedKernel, CudaExecutionPlan> pipeline =
                new CudaBackendExecutionPipelineFactory().createPipeline(new CudaGpuRuntimeBackend());
        return pipeline.executeSafely(
                compileRequest,
                loweringResult,
                moduleArtifact,
                executionPlan,
                executionConfig
        );
    }

    private static void assumeOrFailSucceeded(
            GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result,
            String scenario
    ) {
        if (result.succeeded()) {
            recordCudaSmokeEvidence(result, scenario);
            return;
        }
        String message = "CUDA driver execution smoke did not complete: " + pipelineSummary(result);
        if (Boolean.getBoolean(SMOKE_REQUIRED_PROPERTY) || !environmentBlockers(result)) {
            fail(message);
        }
        Assumptions.assumeTrue(false, message);
    }

    private static void recordCudaSmokeEvidence(
            GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result,
            String scenario
    ) {
        Map<String, String> fields = result.artifactFields("cudaSmoke");
        List<String> keys = List.of(
                "runtime.backend.executionPipeline.succeeded",
                "runtime.cuda.loadedModule.module.format",
                "runtime.cuda.loadedModule.module.payload.byteSize",
                "runtime.cuda.loadedModule.driver.version",
                "runtime.cuda.loadedModule.moduleHandle.present",
                "runtime.cuda.loadedModule.functionHandle.present",
                "runtime.cuda.loadedModule.contextHandle.present",
                "runtime.cuda.kernelLaunch.submitted",
                "runtime.cuda.kernelLaunch.sharedMemory.present",
                "runtime.cuda.kernelLaunch.sharedMemory.byteSize",
                "runtime.cuda.kernelLaunch.readback.required.count",
                "runtime.cuda.kernelLaunch.readback.completed.count",
                "runtime.cuda.kernelLaunch.launchShape.gridShape",
                "runtime.cuda.kernelLaunch.launchShape.blockShape",
                "runtime.cuda.readback.complete",
                "runtime.backend.invoke.readback.complete"
        );
        StringBuilder line = new StringBuilder(SMOKE_EVIDENCE_PREFIX);
        line.append(' ')
                .append("runtime.cuda.smoke.scenario")
                .append('=')
                .append(escapeEvidenceValue(scenario));
        for (String key : keys) {
            line.append(' ')
                    .append(key)
                    .append('=')
                    .append(escapeEvidenceValue(fields.getOrDefault(key, "unknown")));
        }
        System.out.println(line);
    }

    private static String escapeEvidenceValue(String value) {
        String normalized = value == null || value.isBlank() ? "unknown" : value.trim();
        return normalized
                .replace("%", "%25")
                .replace("\r", "%0D")
                .replace("\n", "%0A")
                .replace("\t", "%09")
                .replace(" ", "%20");
    }

    private static boolean environmentBlockers(
            GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result
    ) {
        List<String> blockers = blockers(result);
        return !blockers.isEmpty() && blockers.stream().allMatch(CudaDriverExecutionIntegrationTest::environmentBlocker);
    }

    private static boolean environmentBlocker(String blocker) {
        return startsWithAny(
                blocker,
                "cuda-nvcc-not-available",
                "cuda-nvcc-process-timeout",
                "cuda-nvcc-process-interrupted",
                "cuda-nvcc-host-compiler-missing",
                "cuda-nvcc-host-compiler-unsupported",
                "cuda-nvcc-virtual-architecture-not-allowed",
                "cuda-nvcc-process-failed",
                "cuda-nvcc-ptx-missing",
                "cuda-driver-library-unavailable",
                "cuda-driver-symbols-missing",
                "cuda-driver-symbol-missing:",
                "cuda-driver-cuInit-failed:",
                "cuda-driver-cuDriverGetVersion-failed:",
                "cuda-driver-ptx-version-unsupported:",
                "cuda-ptx-target-too-new:",
                "cuda-driver-cuModuleLoadDataEx-failed:",
                "cuda-driver-cuModuleGetFunction-failed:",
                "cuda-driver-module-load-exception:",
                "cuda-driver-cuMemAlloc-failed:",
                "cuda-driver-cuMemcpyHtoD-failed:",
                "cuda-driver-cuLaunchKernel-failed:",
                "cuda-driver-cuMemcpyDtoH-failed:",
                "cuda-compile-stage-not-available",
                "cuda-prepare-stage-not-available",
                "compile-stage-unsupported",
                "prepare-stage-skipped",
                "compile-stage-failed",
                "prepare-stage-failed"
        );
    }

    private static List<String> blockers(
            GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result
    ) {
        ArrayList<String> blockers = new ArrayList<>();
        blockers.addAll(result.compilationResult().stageResult().blockers());
        blockers.addAll(result.preparationResult().stageResult().blockers());
        blockers.addAll(result.invocationResult().stageResult().blockers());
        return List.copyOf(blockers);
    }

    private static List<String> diagnostics(
            GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result
    ) {
        ArrayList<String> diagnostics = new ArrayList<>();
        diagnostics.addAll(result.compilationResult().stageResult().diagnostics());
        diagnostics.addAll(result.preparationResult().stageResult().diagnostics());
        diagnostics.addAll(result.invocationResult().stageResult().diagnostics());
        return List.copyOf(diagnostics);
    }

    private static String pipelineSummary(
            GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> result
    ) {
        return "compile="
                + result.compilationResult().stageResult().status()
                + ", prepare="
                + result.preparationResult().stageResult().status()
                + ", invoke="
                + result.invocationResult().stageResult().status()
                + ", blockers="
                + String.join(",", blockers(result))
                + ", diagnostics="
                + String.join(" | ", diagnostics(result));
    }

    private static boolean startsWithAny(String value, String... prefixes) {
        if (value == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static GpuKernelDescriptor descriptor(
            String kernelName,
            String source,
            List<GpuKernelParameterDescriptor> parameters
    ) {
        return new GpuKernelDescriptor(
                kernelName,
                "inline://tests/" + kernelName + ".cu",
                source,
                parameters
        );
    }

    private static String cudaSmokeArchitecture(GpuRuntimeDeviceProfile selectedDevice, String nvccOutputFormat) {
        String configured = firstNonBlank(
                System.getProperty(SMOKE_ARCH_PROPERTY),
                System.getenv(SMOKE_ARCH_ENVIRONMENT)
        );
        if (configured != null) {
            return normalizeCudaArchitecture(configured, nvccOutputFormat);
        }
        String capability = selectedDevice.cudaComputeCapability();
        if (capability != null && capability.matches("[0-9]+\\.[0-9]+")) {
            return cudaArchitecturePrefix(nvccOutputFormat) + capability.replace(".", "");
        }
        return cudaArchitecturePrefix(nvccOutputFormat) + "52";
    }

    private static String normalizeCudaArchitecture(String value, String nvccOutputFormat) {
        String trimmed = value.trim();
        String suffix;
        if (trimmed.startsWith("compute_")) {
            suffix = trimmed.substring("compute_".length());
        } else if (trimmed.startsWith("sm_")) {
            suffix = trimmed.substring("sm_".length());
        } else {
            suffix = trimmed.replace(".", "");
        }
        return cudaArchitecturePrefix(nvccOutputFormat) + suffix;
    }

    private static String cudaArchitecturePrefix(String nvccOutputFormat) {
        return "cubin".equals(nvccOutputFormat) || "fatbin".equals(nvccOutputFormat) ? "sm_" : "compute_";
    }

    private static String cudaNvccOutputFormat() {
        String outputFormat = firstNonBlank(
                System.getProperty(NVCC_OUTPUT_FORMAT_PROPERTY),
                System.getenv(NVCC_OUTPUT_FORMAT_ENVIRONMENT)
        );
        if (outputFormat == null) {
            return "ptx";
        }
        String normalized = outputFormat.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "cubin", "fatbin" -> normalized;
            default -> "ptx";
        };
    }

    private static String configuredNvccPath() {
        return firstNonBlank(System.getProperty(NVCC_PATH_PROPERTY), System.getenv(NVCC_PATH_ENVIRONMENT));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private record CudaSmokeEnvironment(
            GpuRuntimeDeviceProfile selectedDevice,
            GpuRuntimeCompileOptions options,
            String architecture
    ) {
    }

    @GPUStruct
    static final class CudaSmokeParticle {
        float weight;
        int count;

        CudaSmokeParticle() {
            this(0.0f, 0);
        }

        CudaSmokeParticle(float weight, int count) {
            this.weight = weight;
            this.count = count;
        }
    }
}
