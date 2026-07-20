package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Stable authorization statuses for backend hook execution reports.
 */
public enum GpuBackendHookAuthorizationStatus {
    READ_ONLY_AUTHORIZED,
    TARGET_FILTERED,
    PHASE_FILTERED,
    EXPLICIT_AUTHORIZATION_MISSING,
    PERMISSION_EXCEEDS_POLICY,
    AUTHORIZED_BUT_EXECUTION_DISABLED
}
