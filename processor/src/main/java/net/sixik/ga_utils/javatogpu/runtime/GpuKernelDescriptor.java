package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

public record GpuKernelDescriptor(
        String kernelName,
        String kernelResource,
        String kernelSource,
        String irGpuResource,
        List<GpuKernelParameterDescriptor> parameterDescriptors
) {

    public GpuKernelDescriptor(
            String kernelName,
            String kernelResource,
            String kernelSource,
            List<GpuKernelParameterDescriptor> parameterDescriptors
    ) {
        this(kernelName, kernelResource, kernelSource, "", parameterDescriptors);
    }
}
