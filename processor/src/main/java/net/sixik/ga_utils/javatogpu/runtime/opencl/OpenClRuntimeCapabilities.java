package net.sixik.ga_utils.javatogpu.runtime.opencl;

record OpenClRuntimeCapabilities(
        String deviceLabel,
        String vendor,
        String driverVersion,
        String deviceVersion,
        boolean supportsDoublePrecision,
        boolean supportsImages,
        boolean supportsImage3dWrites,
        long localMemoryBytes,
        long maxWorkGroupSize,
        long computeUnits,
        long preferredVectorWidthFloat,
        boolean supportsSubgroups
) {
    OpenClRuntimeCapabilities(
            String deviceLabel,
            String deviceVersion,
            boolean supportsDoublePrecision,
            boolean supportsImages,
            boolean supportsImage3dWrites,
            long localMemoryBytes,
            long maxWorkGroupSize
    ) {
        this(
                deviceLabel,
                "unknown",
                "unknown",
                deviceVersion,
                supportsDoublePrecision,
                supportsImages,
                supportsImage3dWrites,
                localMemoryBytes,
                maxWorkGroupSize,
                -1L,
                -1L,
                false
        );
    }
}
