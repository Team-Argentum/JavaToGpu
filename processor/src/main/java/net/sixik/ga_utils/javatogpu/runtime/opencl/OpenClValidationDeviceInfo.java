package net.sixik.ga_utils.javatogpu.runtime.opencl;

record OpenClValidationDeviceInfo(
        String deviceLabel,
        String vendor,
        String driverVersion,
        String deviceVersion,
        String compilerVersion,
        String platformName,
        String platformVersion,
        boolean supportsDoublePrecision,
        boolean supportsImages,
        boolean supportsImage3dWrites,
        long localMemoryBytes,
        long maxWorkGroupSize,
        long computeUnits,
        long preferredVectorWidthFloat,
        boolean supportsAtomics,
        boolean supportsSubgroups
) {
    OpenClValidationDeviceInfo(
            String deviceLabel,
            String vendor,
            String driverVersion,
            String deviceVersion,
            String platformName,
            String platformVersion,
            boolean supportsDoublePrecision,
            boolean supportsImages,
            boolean supportsImage3dWrites,
            long localMemoryBytes,
            long maxWorkGroupSize,
            long computeUnits,
            long preferredVectorWidthFloat,
            boolean supportsAtomics,
            boolean supportsSubgroups
    ) {
        this(
                deviceLabel,
                vendor,
                driverVersion,
                deviceVersion,
                "unknown",
                platformName,
                platformVersion,
                supportsDoublePrecision,
                supportsImages,
                supportsImage3dWrites,
                localMemoryBytes,
                maxWorkGroupSize,
                computeUnits,
                preferredVectorWidthFloat,
                supportsAtomics,
                supportsSubgroups
        );
    }

    OpenClValidationDeviceInfo(
            String deviceLabel,
            String vendor,
            String driverVersion,
            String deviceVersion,
            String platformName,
            String platformVersion,
            boolean supportsDoublePrecision,
            boolean supportsImages,
            boolean supportsImage3dWrites,
            long localMemoryBytes,
            long maxWorkGroupSize
    ) {
        this(
                deviceLabel,
                vendor,
                driverVersion,
                deviceVersion,
                "unknown",
                platformName,
                platformVersion,
                supportsDoublePrecision,
                supportsImages,
                supportsImage3dWrites,
                localMemoryBytes,
                maxWorkGroupSize,
                -1L,
                -1L,
                false,
                false
        );
    }
}
