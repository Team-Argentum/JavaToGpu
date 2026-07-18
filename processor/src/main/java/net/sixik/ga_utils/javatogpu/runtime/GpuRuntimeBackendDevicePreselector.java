package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Optional runtime-backend hook for honoring a backend-neutral device preflight result.
 *
 * <p>Backends that implement this interface can receive the selected discovery result before they are installed into
 * {@link GpuRuntime}. The backend still owns final native validation at invocation time, but it should use this hint
 * when creating its first native session so preflight and execution target the same device.</p>
 */
public interface GpuRuntimeBackendDevicePreselector {

    /**
     * Configures the backend with the selected device discovery result from a successful preflight.
     */
    void preselectDevice(GpuRuntimeDeviceDiscoveryResult discoveryResult);
}
