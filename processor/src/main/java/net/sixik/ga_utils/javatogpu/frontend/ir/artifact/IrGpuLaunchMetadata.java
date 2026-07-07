package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

/**
 * Runtime launch contract stored with IrGpu so backends can preflight execution without Java descriptors.
 */
public record IrGpuLaunchMetadata(
        int requiredDimensions,
        String globalWorkSizeSource,
        boolean explicitConfigSupported
) {

    public IrGpuLaunchMetadata {
        if (requiredDimensions < 1 || requiredDimensions > 3) {
            requiredDimensions = 1;
        }
        globalWorkSizeSource = globalWorkSizeSource == null || globalWorkSizeSource.isBlank()
                ? "first-buffer-parameter"
                : globalWorkSizeSource;
    }

    public static IrGpuLaunchMetadata defaultOneDimensional() {
        return new IrGpuLaunchMetadata(1, "first-buffer-parameter", true);
    }
}
