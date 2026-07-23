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

    public static final boolean RETURN_VALUE_CONVENIENCE_AVAILABLE = true;
    public static final String RETURN_VALUE_CONVENIENCE_STATUS = "available";
    public static final String RETURN_VALUE_CONVENIENCE_REASON = "single-primitive-output-array";
    public static final String RETURN_VALUE_CONVENIENCE_OUTPUT_PARAMETER = "output";
    public static final String RETURN_VALUE_CONVENIENCE_OUTPUT_TYPE = "int[]";
    public static final String RETURN_VALUE_CONVENIENCE_RETURN_TYPE = "int";

    private GpuRuntimeTest_FixtureOwner_kernel_GpuLauncher() {
    }

    public static int invokeReturningFirstWithCompileOptions(
            net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions
    ) {
        return invokeReturningFirstWithGlobalWorkSizeAndCompileOptions(1L, compileOptions);
    }

    public static int invokeReturningFirstWithGlobalWorkSizeAndCompileOptions(
            long globalWorkSize,
            net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions
    ) {
        return invokeReturningFirstWithConfigAndCompileOptions(
                net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.oneDimensional(globalWorkSize),
                compileOptions
        );
    }

    public static int invokeReturningFirstWithConfigAndCompileOptions(
            net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig,
            net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions
    ) {
        int[] output = new int[returnOutputLength(executionConfig)];
        net.sixik.ga_utils.javatogpu.runtime.GpuRuntime.invokeFromGeneratedLauncherWithCompileOptions(
                GpuRuntimeTest_FixtureOwner_kernel_GpuLauncher.class,
                executionConfig,
                compileOptions,
                KERNEL_DESCRIPTOR,
                output
        );
        return output[0];
    }

    private static int returnOutputLength(net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
        java.util.Objects.requireNonNull(executionConfig, "executionConfig");
        long itemCount = executionConfig.globalItemCount();
        if (itemCount <= 0L || itemCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Return-value convenience output length must be between 1 and Integer.MAX_VALUE: " + itemCount
            );
        }
        return (int) itemCount;
    }
}
