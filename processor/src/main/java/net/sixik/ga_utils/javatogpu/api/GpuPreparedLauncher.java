package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;

/**
 * Reusable handle for hot loops that call the same generated GPU kernel many times.
 *
 * <p>A prepared launcher pays the cold runtime cost once: descriptor validation, backend/device selection, source
 * selection, optimizer gates, and kernel compilation. Each later call should stay on the backend's hot path and only
 * update arguments, enqueue the kernel, and read results back.</p>
 *
 * <p>The handle does not own the active runtime scope. Keep the {@link GpuScope} returned by {@link JavaToGpu} open for
 * as long as prepared launchers are used.</p>
 */
public interface GpuPreparedLauncher extends AutoCloseable {

    /**
     * Descriptor selected during the cold prepare step.
     */
    GpuKernelDescriptor descriptor();

    /**
     * Compile options used for the cold prepare step.
     */
    GpuRuntimeCompileOptions compileOptions();

    /**
     * Default execution config selected during prepare, or {@code null} when it is inferred per call.
     */
    GpuExecutionConfig defaultExecutionConfig();

    /**
     * Invokes the prepared kernel with the default or inferred launch shape.
     */
    void invoke(Object... arguments);

    /**
     * Invokes the prepared kernel with an explicit launch shape.
     */
    void invokeWithConfig(GpuExecutionConfig executionConfig, Object... arguments);

    /**
     * Invokes the prepared kernel with an explicit 1D global work size.
     */
    default void invokeWithGlobalWorkSize(long globalWorkSize, Object... arguments) {
        invokeWithConfig(GpuExecutionConfig.oneDimensional(globalWorkSize), arguments);
    }

    @Override
    default void close() {
    }
}
