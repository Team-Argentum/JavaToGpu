package net.sixik.ga_utils.javatogpu.api;

/**
 * Device-class selector used by public source annotations and future runtime compatibility metadata.
 */
public enum GpuDeviceClassTarget {
    /**
     * Matches any device class.
     */
    ANY,

    /**
     * Unknown or custom device class.
     */
    UNKNOWN,

    /**
     * Discrete GPU.
     */
    DGPU,

    /**
     * Integrated GPU.
     */
    IGPU,

    /**
     * CPU device exposed through a GPU backend such as OpenCL.
     */
    CPU
}
