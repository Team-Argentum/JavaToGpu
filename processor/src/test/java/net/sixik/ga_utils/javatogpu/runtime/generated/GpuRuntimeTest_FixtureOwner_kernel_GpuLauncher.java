package net.sixik.ga_utils.javatogpu.runtime.generated;

public final class GpuRuntimeTest_FixtureOwner_kernel_GpuLauncher {

    public static final net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor KERNEL_DESCRIPTOR =
            new net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor(
                    "fixture_kernel",
                    "javatogpu/runtime/FixtureOwner/kernel.cl",
                    "__kernel void fixture_kernel(__global int* output) { output[0] = 1; }",
                    java.util.List.of(
                            new net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor(
                                    "output",
                                    "int[]",
                                    net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess.READ_WRITE
                            )
                    )
            );

    private GpuRuntimeTest_FixtureOwner_kernel_GpuLauncher() {
    }
}
