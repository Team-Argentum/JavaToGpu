package net.sixik.ga_utils.javatogpu.api;

/**
 * Vendor selector used by public source annotations and future runtime compatibility metadata.
 */
public enum GpuVendorTarget {
    /**
     * Matches any vendor.
     */
    ANY,

    /**
     * Unknown or custom vendor.
     */
    UNKNOWN,

    /**
     * NVIDIA GPU or runtime stack.
     */
    NVIDIA,

    /**
     * AMD GPU or runtime stack.
     */
    AMD,

    /**
     * Intel GPU or runtime stack.
     */
    INTEL,

    /**
     * Apple GPU or runtime stack.
     */
    APPLE
}
