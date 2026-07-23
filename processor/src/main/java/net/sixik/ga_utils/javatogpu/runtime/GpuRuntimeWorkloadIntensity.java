package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Coarse backend-neutral workload intensity hint used by advisory backend scoring.
 */
public enum GpuRuntimeWorkloadIntensity {
    UNKNOWN,
    LOW,
    MEDIUM,
    HIGH
}
