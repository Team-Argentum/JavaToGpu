package net.sixik.ga_utils.javatogpu.runtime;

/**
 * ServiceLoader-facing runtime lifecycle hook.
 *
 * <p>External modules should implement this interface and register their implementation in
 * {@code META-INF/services/net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleService}. Applications then enable
 * the hook by putting that module on the runtime classpath; they do not need to call an ad-hoc listener registration
 * API.</p>
 */
public interface GpuRuntimeLifecycleService extends GpuRuntimeLifecycleEventListener {
}
