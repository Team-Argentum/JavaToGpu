/**
 * Backend hook registry, inspection, and read-only execution infrastructure.
 *
 * <p>Hook implementations are registered through ServiceLoader using the root compatibility hook interfaces such as
 * {@link net.sixik.ga_utils.javatogpu.runtime.GpuBackendHook},
 * {@link net.sixik.ga_utils.javatogpu.runtime.GpuBackendPolicyContributor},
 * {@link net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendScoreContributor}, and the stage-specific hook
 * subtypes. The registry in this package owns deterministic discovery, duplicate-id checks, authorization preview, and
 * fail-soft read-only execution.</p>
 */
package net.sixik.ga_utils.javatogpu.runtime.hooks;
