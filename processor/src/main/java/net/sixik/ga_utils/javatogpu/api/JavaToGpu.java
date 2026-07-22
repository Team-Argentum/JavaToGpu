package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendDeviceSelection;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;

import java.util.Objects;

/**
 * Small application-facing facade for common JavaToGpu runtime tasks.
 *
 * <p>The lower-level runtime package remains supported for advanced configuration, generated launchers, tests, and
 * extension modules. New user documentation should prefer this facade for the first-run path so application code does
 * not need to discover the implementation-heavy runtime package before launching a simple OpenCL kernel.</p>
 */
public final class JavaToGpu {

    private JavaToGpu() {
    }

    /**
     * Installs a fresh OpenCL runtime for the current try-with-resources scope.
     *
     * <p>Use this for one-off calls or tests. For repeated calls, prefer {@link #useOpenClSharedCache()} so the OpenCL
     * session and compile cache can stay warm.</p>
     */
    public static GpuScope useOpenCl() {
        return GpuScope.wrap(GpuRuntime.useOpenCl());
    }

    /**
     * Installs an OpenCL runtime backed by the process-wide shared cache.
     *
     * <p>Use this for application loops, demos, benchmarks, and repeated generated launcher calls. Call
     * {@link #shutdownOpenClSharedCache()} when the application no longer needs the shared OpenCL session/cache.</p>
     */
    public static GpuScope useOpenClSharedCache() {
        return GpuScope.wrap(GpuRuntime.useOpenClSharedCache());
    }

    /**
     * Releases the process-wide shared OpenCL cache created by {@link #useOpenClSharedCache()}.
     */
    public static void shutdownOpenClSharedCache() {
        GpuRuntime.shutdownOpenClSharedCache();
    }

    /**
     * Selects and installs the standard backend/device pair using default OpenCL controls.
     *
     * <p>OpenCL is the production backend in the current alpha. Planned or staged backends such as CUDA remain
     * fail-closed unless explicitly enabled by their lower-level runtime options.</p>
     */
    public static GpuScope useStandardBackendAndDevice() {
        return GpuScope.wrap(GpuRuntime.useStandardBackendAndDevice());
    }

    /**
     * Selects and installs the standard backend/device pair with caller-provided OpenCL discovery controls.
     */
    public static GpuScope useStandardBackendAndDevice(GpuRuntimeCompileOptions compileOptions) {
        return GpuScope.wrap(GpuRuntime.useStandardBackendAndDevice(
                Objects.requireNonNull(compileOptions, "compileOptions")
        ));
    }

    /**
     * Explains standard backend/device selection without installing a runtime backend.
     *
     * <p>Use this for diagnostics, setup screens, and first-run support when you want a structured answer to "why this
     * backend/device?" before launching a kernel.</p>
     */
    public static GpuRuntimeBackendDeviceSelection explainStandardBackendAndDevice() {
        return GpuRuntime.trySelectStandardBackendAndDevice();
    }

    /**
     * Explains standard backend/device selection with caller-provided OpenCL discovery controls.
     */
    public static GpuRuntimeBackendDeviceSelection explainStandardBackendAndDevice(
            GpuRuntimeCompileOptions compileOptions
    ) {
        return GpuRuntime.trySelectStandardBackendAndDevice(Objects.requireNonNull(compileOptions, "compileOptions"));
    }

    /**
     * Creates a 1D launch shape with backend-selected local work size.
     */
    public static GpuExecutionConfig launch1D(long globalWorkSize) {
        return GpuExecutionConfig.oneDimensional(globalWorkSize);
    }

    /**
     * Creates a 1D launch shape with explicit local work size.
     */
    public static GpuExecutionConfig launch1D(long globalWorkSize, long localWorkSize) {
        return GpuExecutionConfig.oneDimensional(globalWorkSize, localWorkSize);
    }

    /**
     * Creates a 2D launch shape with backend-selected local work size.
     */
    public static GpuExecutionConfig launch2D(long globalX, long globalY) {
        return GpuExecutionConfig.twoDimensional(globalX, globalY);
    }

    /**
     * Creates a 2D launch shape with explicit local work size.
     */
    public static GpuExecutionConfig launch2D(long globalX, long globalY, long localX, long localY) {
        return GpuExecutionConfig.twoDimensional(globalX, globalY, localX, localY);
    }

    /**
     * Creates a 3D launch shape with backend-selected local work size.
     */
    public static GpuExecutionConfig launch3D(long globalX, long globalY, long globalZ) {
        return GpuExecutionConfig.threeDimensional(globalX, globalY, globalZ);
    }

    /**
     * Creates a 3D launch shape with explicit local work size.
     */
    public static GpuExecutionConfig launch3D(
            long globalX,
            long globalY,
            long globalZ,
            long localX,
            long localY,
            long localZ
    ) {
        return GpuExecutionConfig.threeDimensional(globalX, globalY, globalZ, localX, localY, localZ);
    }
}
