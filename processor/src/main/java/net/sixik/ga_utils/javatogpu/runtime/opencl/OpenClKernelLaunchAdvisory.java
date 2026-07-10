package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;

record OpenClKernelLaunchAdvisory(
        String kernelName,
        String kernelResource,
        String status,
        boolean explicitLocalSize,
        long requestedLocalWorkGroupSize,
        String requestedLocalWorkGroupShape,
        long kernelMaxWorkGroupSize,
        long preferredWorkGroupSizeMultiple,
        boolean comparisonPerformed,
        boolean preferredMultipleMatched,
        String diagnostic
) {

    static final String ARTIFACT_FILE_NAME = "runtime-launch-advisory.properties";

    static OpenClKernelLaunchAdvisory evaluate(
            OpenClCompiledKernel compiledKernel,
            GpuExecutionConfig executionConfig
    ) {
        long requestedSize = requestedLocalWorkGroupSize(executionConfig);
        String shape = localWorkGroupShape(executionConfig);
        long kernelLimit = compiledKernel.kernelMaxWorkGroupSize();
        long preferredMultiple = compiledKernel.kernelPreferredWorkGroupSizeMultiple();
        if (requestedSize <= 0L) {
            return new OpenClKernelLaunchAdvisory(
                    compiledKernel.descriptor().kernelName(),
                    compiledKernel.descriptor().kernelResource(),
                    "driver-selected",
                    false,
                    0L,
                    shape,
                    kernelLimit,
                    preferredMultiple,
                    false,
                    false,
                    "Local work-group size is unspecified; the OpenCL driver selects the launch shape"
            );
        }
        if (preferredMultiple <= 0L) {
            return new OpenClKernelLaunchAdvisory(
                    compiledKernel.descriptor().kernelName(),
                    compiledKernel.descriptor().kernelResource(),
                    "unavailable",
                    true,
                    requestedSize,
                    shape,
                    kernelLimit,
                    preferredMultiple,
                    false,
                    false,
                    "The compiled kernel did not report a preferred work-group size multiple"
            );
        }

        boolean matched = requestedSize % preferredMultiple == 0L;
        return new OpenClKernelLaunchAdvisory(
                compiledKernel.descriptor().kernelName(),
                compiledKernel.descriptor().kernelResource(),
                matched ? "aligned" : "non-preferred-multiple",
                true,
                requestedSize,
                shape,
                kernelLimit,
                preferredMultiple,
                true,
                matched,
                matched
                        ? "Explicit local work-group size is aligned with the kernel preferred multiple"
                        : "Explicit local work-group size is not aligned with the kernel preferred multiple; execution remains allowed"
        );
    }

    String toProperties() {
        StringBuilder builder = new StringBuilder();
        builder.append("formatVersion=1\n");
        builder.append("status=").append(status).append('\n');
        builder.append("blocking=false\n");
        builder.append("kernelName=").append(kernelName).append('\n');
        builder.append("kernelResource=").append(kernelResource).append('\n');
        builder.append("explicitLocalSize=").append(explicitLocalSize).append('\n');
        builder.append("requestedLocalWorkGroupSize=").append(requestedLocalWorkGroupSize).append('\n');
        builder.append("requestedLocalWorkGroupShape=").append(requestedLocalWorkGroupShape).append('\n');
        builder.append("kernelMaxWorkGroupSize=").append(kernelMaxWorkGroupSize).append('\n');
        builder.append("preferredWorkGroupSizeMultiple=").append(preferredWorkGroupSizeMultiple).append('\n');
        builder.append("comparisonPerformed=").append(comparisonPerformed).append('\n');
        builder.append("preferredMultipleMatched=").append(preferredMultipleMatched).append('\n');
        builder.append("diagnostic.count=1\n");
        builder.append("diagnostic.0=").append(diagnostic).append('\n');
        return builder.toString();
    }

    private static long requestedLocalWorkGroupSize(GpuExecutionConfig executionConfig) {
        if (executionConfig.localX() <= 0L) {
            return 0L;
        }
        try {
            return switch (executionConfig.dimensions()) {
                case 1 -> executionConfig.localX();
                case 2 -> Math.multiplyExact(executionConfig.localX(), executionConfig.localY());
                case 3 -> Math.multiplyExact(
                        Math.multiplyExact(executionConfig.localX(), executionConfig.localY()),
                        executionConfig.localZ()
                );
                default -> Long.MAX_VALUE;
            };
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static String localWorkGroupShape(GpuExecutionConfig executionConfig) {
        if (executionConfig.localX() <= 0L) {
            return "driver-selected";
        }
        return switch (executionConfig.dimensions()) {
            case 1 -> Long.toString(executionConfig.localX());
            case 2 -> executionConfig.localX() + "x" + executionConfig.localY();
            case 3 -> executionConfig.localX() + "x" + executionConfig.localY() + "x" + executionConfig.localZ();
            default -> "unknown";
        };
    }
}
