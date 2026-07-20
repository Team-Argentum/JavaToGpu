package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClBackendExecutionPipelineFactory;

import java.util.Optional;
import java.util.Set;

/**
 * Built-in OpenCL backend provider.
 */
public final class OpenClRuntimeBackendProvider implements GpuRuntimeBackendProvider {

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.OPENCL;
    }

    @Override
    public String providerId() {
        return "backend-provider:opencl";
    }

    @Override
    public String providerVersion() {
        return "1";
    }

    @Override
    public int providerOrder() {
        return 0;
    }

    @Override
    public GpuRuntimeBackendAdapter createAdapter() {
        return new OpenClRuntimeBackendAdapter();
    }

    @Override
    public GpuRuntimeBackendExecutionSupport executionSupport() {
        return GpuRuntimeBackendExecutionSupport.productionPipeline(
                backendTarget(),
                providerId(),
                Set.of(GpuBackendModuleFormat.OPENCL_C),
                Set.of(
                        GpuRuntimeCapability.DEVICE_CLASS,
                        GpuRuntimeCapability.DRIVER_VERSION,
                        GpuRuntimeCapability.RUNTIME_VERSION,
                        GpuRuntimeCapability.COMPUTE_UNITS,
                        GpuRuntimeCapability.GLOBAL_MEMORY,
                        GpuRuntimeCapability.LOCAL_MEMORY,
                        GpuRuntimeCapability.MAX_WORK_GROUP_SIZE,
                        GpuRuntimeCapability.PREFERRED_FLOAT_VECTOR_WIDTH,
                        GpuRuntimeCapability.ATOMICS,
                        GpuRuntimeCapability.FP64,
                        GpuRuntimeCapability.IMAGES,
                        GpuRuntimeCapability.IMAGE_3D_WRITES,
                        GpuRuntimeCapability.SUBGROUPS,
                        GpuRuntimeCapability.SHARED_CACHE,
                        GpuRuntimeCapability.UNIFIED_MEMORY,
                        GpuRuntimeCapability.ADDRESS_SPACE_GLOBAL,
                        GpuRuntimeCapability.ADDRESS_SPACE_LOCAL,
                        GpuRuntimeCapability.ADDRESS_SPACE_CONSTANT,
                        GpuRuntimeCapability.STRUCT_ABI,
                        GpuRuntimeCapability.IMAGE_ABI
                ),
                "OpenCL provides the complete production compile/prepare/invoke execution pipeline"
        );
    }

    @Override
    public Optional<GpuBackendExecutionPipelineFactory<?, ?, ?>> executionPipelineFactory() {
        return Optional.of(new OpenClBackendExecutionPipelineFactory());
    }
}
