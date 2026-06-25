package net.sixik.ga_utils.javatogpu.runtime.opencl;

record OpenClValidationDeviceInfo(
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
}
