package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompiledKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipeline;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipelineFactory;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipelineResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLoweringResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleFormat;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendPipelineStage;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceSelectionPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuPreparedKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendAdapter;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendExecutionSupport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCapability;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Built-in CUDA backend provider for source preview and fail-closed execution bring-up.
 */
public final class CudaRuntimeBackendProvider implements GpuRuntimeBackendProvider {

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.CUDA;
    }

    @Override
    public String providerId() {
        return "backend-provider:cuda";
    }

    @Override
    public String providerVersion() {
        return "1";
    }

    @Override
    public int providerOrder() {
        return 100;
    }

    @Override
    public GpuRuntimeBackendAdapter createAdapter() {
        return new CudaRuntimeBackendAdapter();
    }

    @Override
    public GpuRuntimeBackendExecutionSupport executionSupport() {
        return new GpuRuntimeBackendExecutionSupport(
                backendTarget(),
                providerId(),
                false,
                EnumSet.of(
                        GpuBackendPipelineStage.DISCOVER,
                        GpuBackendPipelineStage.SELECT,
                        GpuBackendPipelineStage.LOWER,
                        GpuBackendPipelineStage.COMPILE,
                        GpuBackendPipelineStage.PREPARE,
                        GpuBackendPipelineStage.INVOKE
                ),
                Set.of(
                        GpuBackendModuleFormat.CUDA_C,
                        GpuBackendModuleFormat.PTX,
                        GpuBackendModuleFormat.CUBIN,
                        GpuBackendModuleFormat.FATBIN
                ),
                Set.of(
                        GpuRuntimeCapability.DEVICE_CLASS,
                        GpuRuntimeCapability.DRIVER_VERSION,
                        GpuRuntimeCapability.RUNTIME_VERSION,
                        GpuRuntimeCapability.COMPUTE_CAPABILITY,
                        GpuRuntimeCapability.GLOBAL_MEMORY
                ),
                "CUDA exposes a non-production compile/prepare/invoke skeleton with staged native bridges; production execution remains fail-closed until hardware validation"
        );
    }

    @Override
    public Optional<GpuBackendExecutionPipelineFactory<?, ?, ?>> executionPipelineFactory() {
        return Optional.of(new CudaBackendExecutionPipelineFactory());
    }

    @Override
    public GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> unsupportedExecutionResult(
            GpuBackendLoweringResult loweringResult
    ) {
        CudaBackendExecutionPipelineFactory factory = new CudaBackendExecutionPipelineFactory();
        GpuBackendExecutionPipeline<GpuBackendCompiledKernel, GpuPreparedKernel, CudaExecutionPlan> pipeline =
                factory.createPipeline(new CudaGpuRuntimeBackend());
        GpuBackendLoweringResult lowering = loweringResult == null
                ? GpuBackendLoweringResult.unsupported(
                        GpuBackendTarget.CUDA,
                        GpuBackendSourceSelectionPlan.descriptorSource(
                                GpuBackendTarget.CUDA,
                                GpuBackendModuleFormat.CUDA_C.key(),
                                "CUDA unsupported receipt was requested without a lowered module"
                        ),
                        List.of("cuda-lowering-result-missing"),
                        List.of("CUDA execution skeleton did not receive a lowering result")
                )
                : loweringResult;
        return pipeline.executeSafely(
                new GpuRuntimeCompileRequest(
                        new GpuKernelDescriptor("cudaUnsupportedReceipt", "inline://cuda/unsupported.cu", "", List.of()),
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                        GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA")
                ),
                lowering,
                lowering.moduleArtifact(),
                CudaExecutionPlan.empty(),
                null
        );
    }
}
