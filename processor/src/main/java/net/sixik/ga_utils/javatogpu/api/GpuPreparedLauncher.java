package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.api.observability.GpuPreparedInvocationTimings;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;

import java.util.List;

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
     * Argument names expected by this launcher when {@link #invoke(Object...)} is called.
     *
     * <p>For a normal prepared launcher this is the full descriptor parameter list. For a static-argument derived
     * launcher this contains only the remaining dynamic arguments, in the order they must be passed to hot invokes.</p>
     */
    default List<String> dynamicArgumentNames() {
        return descriptor().parameterDescriptors().stream()
                .map(GpuKernelParameterDescriptor::name)
                .toList();
    }

    /**
     * Argument names fixed during prepare and omitted from hot invokes.
     */
    default List<String> staticArgumentNames() {
        return List.of();
    }

    /**
     * Host-side timing receipt for the last invocation observed by this prepared launcher.
     */
    default GpuPreparedInvocationTimings lastInvocationTimings() {
        return GpuPreparedInvocationTimings.empty();
    }

    /**
     * Returns a derived launcher that keeps selected prepare-time arguments static.
     *
     * <p>The derived launcher accepts only the remaining dynamic arguments in the original descriptor order. For
     * example, if a five-argument kernel freezes indexes {@code 1} and {@code 3}, calls to the returned launcher pass
     * arguments {@code 0}, {@code 2}, and {@code 4}. Backends may reject unsafe static arguments such as writable output
     * buffers.</p>
     *
     * @param argumentIndexes zero-based descriptor parameter indexes to keep static
     * @return a prepared launcher optimized for the remaining dynamic arguments
     */
    default GpuPreparedLauncher withStaticArguments(int... argumentIndexes) {
        throw new UnsupportedOperationException("Prepared static arguments are not supported by this backend");
    }

    /**
     * Returns a derived launcher that does not copy selected dynamic host buffers to the device before each launch.
     *
     * <p>Use this only for buffers fully written by the kernel before they are read, such as pure output buffers or
     * device scratch buffers. Incorrect use can leave stale device data visible to the kernel.</p>
     */
    default GpuPreparedLauncher withoutHostUploadArguments(int... argumentIndexes) {
        throw new UnsupportedOperationException("Prepared host-upload suppression is not supported by this backend");
    }

    /**
     * Returns a derived launcher that does not copy selected dynamic device buffers back to the host after each launch.
     *
     * <p>Use this only for transient scratch/intermediate buffers whose contents are not observed by Java after the
     * kernel call. Incorrect use can leave Java-side arrays stale.</p>
     */
    default GpuPreparedLauncher withoutHostReadbackArguments(int... argumentIndexes) {
        throw new UnsupportedOperationException("Prepared host-readback suppression is not supported by this backend");
    }

    /**
     * Name-based variant of {@link #withStaticArguments(int...)}.
     *
     * <p>This is the preferred user-facing form for kernels with many payload arguments because it avoids fragile
     * positional lists.</p>
     */
    default GpuPreparedLauncher withStaticArgumentNames(String... argumentNames) {
        return withStaticArguments(parameterIndexes(argumentNames));
    }

    /**
     * Name-based variant of {@link #withoutHostUploadArguments(int...)}.
     */
    default GpuPreparedLauncher withoutHostUploadArgumentNames(String... argumentNames) {
        return withoutHostUploadArguments(parameterIndexes(argumentNames));
    }

    /**
     * Name-based variant of {@link #withoutHostReadbackArguments(int...)}.
     */
    default GpuPreparedLauncher withoutHostReadbackArgumentNames(String... argumentNames) {
        return withoutHostReadbackArguments(parameterIndexes(argumentNames));
    }

    private int[] parameterIndexes(String... argumentNames) {
        if (argumentNames == null) {
            throw new NullPointerException("argumentNames");
        }
        List<GpuKernelParameterDescriptor> parameters = descriptor().parameterDescriptors();
        int[] indexes = new int[argumentNames.length];
        for (int nameIndex = 0; nameIndex < argumentNames.length; nameIndex++) {
            String requestedName = argumentNames[nameIndex];
            if (requestedName == null || requestedName.isBlank()) {
                throw new IllegalArgumentException("Static argument name must not be blank at index " + nameIndex);
            }
            indexes[nameIndex] = findParameterIndex(parameters, requestedName);
        }
        return indexes;
    }

    /**
     * Invokes the prepared kernel with an explicit 1D global work size.
     */
    default void invokeWithGlobalWorkSize(long globalWorkSize, Object... arguments) {
        invokeWithConfig(GpuExecutionConfig.oneDimensional(globalWorkSize), arguments);
    }

    private static int findParameterIndex(List<GpuKernelParameterDescriptor> parameters, String requestedName) {
        for (int index = 0; index < parameters.size(); index++) {
            if (requestedName.equals(parameters.get(index).name())) {
                return index;
            }
        }
        throw new IllegalArgumentException(
                "Unknown GPU kernel argument name '" + requestedName + "'. Available names: "
                        + parameters.stream().map(GpuKernelParameterDescriptor::name).toList()
        );
    }

    @Override
    default void close() {
    }
}
