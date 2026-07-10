package net.sixik.ga_utils.javatogpu.runtime.opencl;

/**
 * Standard OpenCL compiler resource data queried for one built kernel.
 */
record OpenClKernelResourceInfo(
        long maxWorkGroupSize,
        long preferredWorkGroupSizeMultiple,
        long localMemoryBytes,
        long privateMemoryBytes
) {

    static final long UNKNOWN = -1L;

    static OpenClKernelResourceInfo unavailable() {
        return new OpenClKernelResourceInfo(UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN);
    }

    OpenClKernelResourceInfo {
        maxWorkGroupSize = metric(maxWorkGroupSize);
        preferredWorkGroupSizeMultiple = metric(preferredWorkGroupSizeMultiple);
        localMemoryBytes = metric(localMemoryBytes);
        privateMemoryBytes = metric(privateMemoryBytes);
    }

    boolean available() {
        return maxWorkGroupSize >= 0L
                || preferredWorkGroupSizeMultiple >= 0L
                || localMemoryBytes >= 0L
                || privateMemoryBytes >= 0L;
    }

    String appendToCompilerLog(String compilerLog) {
        String normalizedLog = compilerLog == null ? "" : compilerLog.strip();
        if (!available()) {
            return normalizedLog;
        }
        StringBuilder builder = new StringBuilder();
        if (!normalizedLog.isBlank()) {
            builder.append(normalizedLog).append('\n');
        }
        builder.append("[javatogpu-opencl-kernel-resource-info]\n");
        builder.append("status=recorded\n");
        appendMetric(builder, "max work-group size", maxWorkGroupSize, "");
        appendMetric(builder, "preferred work-group size multiple", preferredWorkGroupSizeMultiple, "");
        appendMetric(builder, "local memory", localMemoryBytes, " bytes");
        appendMetric(builder, "private memory", privateMemoryBytes, " bytes");
        return builder.toString().strip();
    }

    private static void appendMetric(StringBuilder builder, String label, long value, String suffix) {
        if (value >= 0L) {
            builder.append(label).append(": ").append(value).append(suffix).append('\n');
        }
    }

    private static long metric(long value) {
        return value < 0L ? UNKNOWN : value;
    }
}
