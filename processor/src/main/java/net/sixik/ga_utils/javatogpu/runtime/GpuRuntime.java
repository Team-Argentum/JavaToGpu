package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuPreparedLauncher;
import net.sixik.ga_utils.javatogpu.api.observability.GpuPreparedInvocationTimings;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Global entry point for executing generated GPU kernels at runtime.
 *
 * <p>Generated launchers call into this class with a {@link GpuKernelDescriptor} and the original Java arguments.
 * The actual execution strategy is delegated to the currently configured {@link GpuRuntimeBackend}.
 *
 * <p>Typical usage is to install a backend once for an application scope or test scope:
 *
 * <pre>{@code
 * GpuRuntime.setBackend(net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend.sharedCache());
 * try {
 *     DemoKernel.invoke(input, output);
 * } finally {
 *     net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend.shutdownSharedCache();
 *     GpuRuntime.resetBackend();
 * }
 * }</pre>
 *
 * <p>If no backend is configured, the default backend fails fast with a descriptive exception.
 */
public final class GpuRuntime {

    private static final GpuRuntimeBackend DEFAULT_BACKEND = invocation -> {
        throw new UnsupportedOperationException(
                "GPU runtime backend is not configured for kernel " + invocation.descriptor().kernelName()
        );
    };

    private static volatile GpuRuntimeBackend backend = DEFAULT_BACKEND;
    private static volatile Function<GpuRuntimeCompileOptions, GpuRuntimeScope> standardBackendDeviceScopeFactory =
            GpuRuntime::selectAndUseStandardBackendDevice;
    private static volatile BiFunction<GpuRuntimeCompileOptions, GpuRuntimeLifecycleEventBus, GpuRuntimeScope>
            automaticBackendDevicePreflightScopeFactory = GpuRuntime::selectAndUseStandardBackendDevice;
    private static volatile Supplier<GpuRuntimeLifecycleEventBus> automaticBackendDevicePreflightLifecycleEventBusFactory =
            GpuRuntimeLifecycleEventBus::loadFromServiceLoader;

    private GpuRuntime() {
    }

    /**
     * Returns the default fail-fast backend.
     *
     * <p>This backend does not execute kernels. It exists so direct GPU launcher calls fail with a predictable error
     * when the application forgot to install a real runtime backend.
     */
    public static GpuRuntimeBackend defaultBackend() {
        return DEFAULT_BACKEND;
    }

    /**
     * Returns the backend currently used for GPU kernel execution.
     */
    public static GpuRuntimeBackend backend() {
        return backend;
    }

    /**
     * Replaces the active runtime backend.
     *
     * @param newBackend backend that should handle subsequent GPU kernel invocations
     */
    public static void setBackend(GpuRuntimeBackend newBackend) {
        backend = Objects.requireNonNull(newBackend, "newBackend");
    }

    /**
     * Restores the default fail-fast backend.
     */
    public static void resetBackend() {
        backend = DEFAULT_BACKEND;
    }

    /**
     * Installs a backend for the current scope and restores the previous backend when the returned scope is closed.
     *
     * <p>The installed backend is not automatically closed by this method.
     *
     * @param newBackend backend that should be active while the returned scope is open
     * @return scope that restores the previous backend on close
     */
    public static GpuRuntimeScope useBackend(GpuRuntimeBackend newBackend) {
        return installScopedBackend(newBackend, false);
    }

    /**
     * Installs a backend for the current scope and closes it when the returned scope is closed, if it implements
     * {@link AutoCloseable}.
     *
     * @param newBackend backend that should be active while the returned scope is open
     * @return scope that restores the previous backend and closes the installed backend on close
     */
    public static GpuRuntimeScope useOwnedBackend(GpuRuntimeBackend newBackend) {
        return installScopedBackend(newBackend, true);
    }

    /**
     * Installs a fresh instance-local OpenCL backend for the current scope.
     *
     * <p>When the returned scope is closed, the previous backend is restored and the OpenCL backend instance is closed.
     */
    public static GpuRuntimeScope useOpenCl() {
        return useOwnedBackend(new OpenClGpuRuntimeBackend());
    }

    /**
     * Installs a shared-cache OpenCL backend for the current scope.
     *
     * <p>When the scope is closed, the previous backend is restored and the backend instance itself is closed, but the
     * shared OpenCL compile/session cache remains warm. Use {@link #shutdownOpenClSharedCache()} to explicitly release
     * that global cache.
     */
    public static GpuRuntimeScope useOpenClSharedCache() {
        return useOwnedBackend(OpenClGpuRuntimeBackend.sharedCache());
    }

    /**
     * Releases the global shared OpenCL cache created by {@link #useOpenClSharedCache()}.
     */
    public static void shutdownOpenClSharedCache() {
        OpenClGpuRuntimeBackend.shutdownSharedCache();
    }

    /**
     * Returns a capability report for the given backend instance.
     */
    public static GpuRuntimeBackendReport describeBackend(GpuRuntimeBackend backend) {
        return backend.describeCapabilities();
    }

    /**
     * Selects the first backend whose capability report satisfies the given requirements.
     *
     * <p>Backends are checked in the order provided. This lets callers express preference order such as
     * {@code CUDA -> OpenCL -> custom fallback}.
     *
     * @throws UnsupportedOperationException when no backend satisfies the requirements
     */
    public static GpuRuntimeBackendSelection selectFirstMatching(
            List<GpuRuntimeRequirement> requirements,
            GpuRuntimeBackend... candidates
    ) {
        return trySelectFirstMatching(requirements, candidates).requireSelection();
    }

    /**
     * Attempts to select the first backend whose capability report satisfies the given requirements without throwing
     * when no match is found.
     */
    public static GpuRuntimeSelectionResult trySelectFirstMatching(
            List<GpuRuntimeRequirement> requirements,
            GpuRuntimeBackend... candidates
    ) {
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(candidates, "candidates");
        return GpuRuntimeBackendSelectionOrchestrator.selectBorrowed(requirements, candidates);
    }

    /**
     * Selects the first available backend without additional requirements.
     */
    public static GpuRuntimeBackendSelection selectFirstAvailable(GpuRuntimeBackend... candidates) {
        return selectFirstMatching(List.of(), candidates);
    }

    /**
     * Attempts to select the first available backend without additional requirements.
     */
    public static GpuRuntimeSelectionResult trySelectFirstAvailable(GpuRuntimeBackend... candidates) {
        return trySelectFirstMatching(List.of(), candidates);
    }

    /**
     * Attempts to select from the standard production-ready backend catalog.
     */
    public static GpuRuntimeSelectionResult trySelectStandardBackends(List<GpuRuntimeRequirement> requirements) {
        Objects.requireNonNull(requirements, "requirements");
        GpuRuntimeBackendPolicy.Builder builder = GpuRuntimeBackendPolicy.builder().preferStandardBackends();
        requirements.forEach(builder::require);
        return builder.build().trySelect();
    }

    /**
     * Attempts to select from the standard production-ready backend catalog without additional requirements.
     */
    public static GpuRuntimeSelectionResult trySelectStandardBackends() {
        return trySelectStandardBackends(List.of());
    }

    /**
     * Installs the first matching backend from the standard production-ready backend catalog.
     */
    public static GpuRuntimeScope useStandardBackends(List<GpuRuntimeRequirement> requirements) {
        Objects.requireNonNull(requirements, "requirements");
        GpuRuntimeBackendPolicy.Builder builder = GpuRuntimeBackendPolicy.builder().preferStandardBackends();
        requirements.forEach(builder::require);
        return builder.build().use();
    }

    /**
     * Installs the first available backend from the standard production-ready backend catalog.
     */
    public static GpuRuntimeScope useStandardBackends() {
        return useStandardBackends(List.of());
    }

    /**
     * Selects a backend using an immutable fallback policy.
     */
    public static GpuRuntimeBackendSelection select(GpuRuntimeBackendPolicy policy) {
        return Objects.requireNonNull(policy, "policy").select();
    }

    /**
     * Attempts to select a backend using an immutable fallback policy without throwing on a miss.
     */
    public static GpuRuntimeSelectionResult trySelect(GpuRuntimeBackendPolicy policy) {
        return Objects.requireNonNull(policy, "policy").trySelect();
    }

    /**
     * Attempts to select a backend and attaches native device discovery evidence to the same result object.
     */
    public static GpuRuntimeBackendDeviceSelection trySelectWithDeviceDiscovery(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeDeviceDiscoveryCatalog deviceDiscoveryCatalog
    ) {
        return trySelectWithDeviceDiscovery(policy, deviceDiscoveryCatalog, GpuRuntimeLifecycleEventBus.empty());
    }

    /**
     * Attempts to select a backend, attaches native device discovery evidence, and publishes lifecycle events.
     */
    public static GpuRuntimeBackendDeviceSelection trySelectWithDeviceDiscovery(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeDeviceDiscoveryCatalog deviceDiscoveryCatalog,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        return GpuRuntimeBackendSelectionOrchestrator.selectWithDeviceDiscovery(
                Objects.requireNonNull(policy, "policy"),
                deviceDiscoveryCatalog,
                lifecycleEventBus
        );
    }

    /**
     * Attempts standard backend selection and standard backend device discovery with default OpenCL controls.
     *
     * <p>This is a preflight/reporting API: it does not install the selected backend. Use
     * {@link #useStandardBackendAndDevice()} when the selected backend should be installed for a try-with-resources
     * scope.</p>
     */
    public static GpuRuntimeBackendDeviceSelection trySelectStandardBackendAndDevice() {
        return trySelectStandardBackendAndDevice(GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL));
    }

    /**
     * Attempts standard backend selection and standard backend device discovery as one preflight.
     *
     * <p>The compile options are used for device discovery and policy evidence, especially OpenCL platform/device
     * overrides, artifact fields, lifecycle context, and standard backend/device preflight mode.</p>
     */
    public static GpuRuntimeBackendDeviceSelection trySelectStandardBackendAndDevice(
            GpuRuntimeCompileOptions openClDiscoveryOptions
    ) {
        GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
                .preferStandardBackendsWithPlannedDiagnostics()
                .build();
        return trySelectStandardBackendAndDevice(policy, openClDiscoveryOptions);
    }

    /**
     * Attempts caller-supplied backend selection and standard backend device discovery as one preflight.
     */
    public static GpuRuntimeBackendDeviceSelection trySelectStandardBackendAndDevice(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeCompileOptions openClDiscoveryOptions
    ) {
        return trySelectStandardBackendAndDevice(
                policy,
                openClDiscoveryOptions,
                GpuRuntimeLifecycleEventBus.empty()
        );
    }

    /**
     * Attempts caller-supplied backend selection and standard backend device discovery with lifecycle events.
     */
    public static GpuRuntimeBackendDeviceSelection trySelectStandardBackendAndDevice(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeCompileOptions openClDiscoveryOptions,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        return GpuRuntimeBackendSelectionOrchestrator.selectStandardBackendAndDevice(
                Objects.requireNonNull(policy, "policy"),
                openClDiscoveryOptions,
                lifecycleEventBus
        );
    }

    /**
     * Installs a precomputed backend+device selection and forwards the selected device to capable backends.
     */
    public static GpuRuntimeScope use(GpuRuntimeBackendDeviceSelection selection) {
        return Objects.requireNonNull(selection, "selection").installSelectedBackend();
    }

    /**
     * Selects and installs the standard backend+device pair using default OpenCL controls.
     *
     * <p>The selected backend is installed only while the returned {@link GpuRuntimeScope} is open. Closing the scope
     * restores the previous backend, so this is safe for tests and examples that should not leave global runtime state
     * behind.</p>
     */
    public static GpuRuntimeScope useStandardBackendAndDevice() {
        return useStandardBackendAndDevice(GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL));
    }

    /**
     * Selects and installs the standard backend+device pair using caller-provided device-selection controls.
     *
     * <p>This is the recommended lower-level runtime helper when application code does not call the generated
     * {@code invokeWithStandardBackendAndDevice(...)} convenience methods. It performs selection immediately and fails
     * before kernel compilation if no backend/device pair satisfies the requested policy.</p>
     */
    public static GpuRuntimeScope useStandardBackendAndDevice(GpuRuntimeCompileOptions compileOptions) {
        return standardBackendDeviceScopeFactory.apply(compileOptions);
    }

    /**
     * Selects and installs a backend+device pair using caller-provided backend policy and device-selection controls.
     */
    public static GpuRuntimeScope useStandardBackendAndDevice(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeCompileOptions compileOptions
    ) {
        return use(trySelectStandardBackendAndDevice(policy, compileOptions));
    }

    /**
     * Selects and installs a backend+device pair while publishing lifecycle events for selection and discovery.
     */
    public static GpuRuntimeScope useStandardBackendAndDevice(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        return use(trySelectStandardBackendAndDevice(policy, compileOptions, lifecycleEventBus));
    }

    /**
     * Creates backend candidates from factories, selects the first matching backend, installs it as an owned scope,
     * and closes rejected backend instances automatically when possible.
     *
     * @throws UnsupportedOperationException when no backend satisfies the requirements
     */
    public static GpuRuntimeScope useFirstMatching(
            List<GpuRuntimeRequirement> requirements,
            GpuRuntimeBackendFactory... candidateFactories
    ) {
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(candidateFactories, "candidateFactories");
        ArrayList<GpuRuntimeBackendFactory> factories = new ArrayList<>(candidateFactories.length);
        ArrayList<GpuRuntimeBackendOwnership> ownerships = new ArrayList<>(candidateFactories.length);
        for (int index = 0; index < candidateFactories.length; index++) {
            factories.add(Objects.requireNonNull(candidateFactories[index], "candidateFactories[" + index + "]"));
            ownerships.add(GpuRuntimeBackendOwnership.OWNED);
        }
        return GpuRuntimeBackendSelectionOrchestrator.select(requirements, factories, ownerships).install();
    }

    /**
     * Creates backend candidates from factories and installs the first available one as an owned scope.
     */
    public static GpuRuntimeScope useFirstAvailable(GpuRuntimeBackendFactory... candidateFactories) {
        return useFirstMatching(List.of(), candidateFactories);
    }

    /**
     * Installs the first backend selected by the given immutable fallback policy.
     */
    public static GpuRuntimeScope use(GpuRuntimeBackendPolicy policy) {
        return Objects.requireNonNull(policy, "policy").use();
    }

    /**
     * Invokes a generated GPU kernel through the currently configured backend.
     *
     * @param descriptor generated kernel descriptor containing the kernel name, source, and parameter metadata
     * @param arguments original Java launch arguments in generated launcher order
     */
    public static void invoke(GpuKernelDescriptor descriptor, Object... arguments) {
        backend.invoke(new GpuKernelInvocation(descriptor, arguments));
    }

    /**
     * Invokes a generated GPU kernel and resolves paired frontend artifacts with the generated launcher classloader.
     */
    public static void invokeFromGeneratedLauncher(
            Class<?> launcherClass,
            GpuKernelDescriptor descriptor,
            Object... arguments
    ) {
        backend.invoke(withLauncherClassLoader(launcherClass, new GpuKernelInvocation(descriptor, arguments)));
    }

    /**
     * Invokes a generated GPU kernel through the currently configured backend using an explicit 1D global work size.
     */
    public static void invoke(long globalWorkSize, GpuKernelDescriptor descriptor, Object... arguments) {
        backend.invoke(new GpuKernelInvocation(descriptor, arguments, globalWorkSize));
    }

    /**
     * Invokes a generated GPU kernel with explicit 1D work size and launcher-scoped artifact resolution.
     */
    public static void invokeFromGeneratedLauncher(
            Class<?> launcherClass,
            long globalWorkSize,
            GpuKernelDescriptor descriptor,
            Object... arguments
    ) {
        backend.invoke(withLauncherClassLoader(
                launcherClass,
                new GpuKernelInvocation(descriptor, arguments, globalWorkSize)
        ));
    }

    /**
     * Invokes a generated GPU kernel through the currently configured backend using an explicit execution config.
     */
    public static void invoke(GpuExecutionConfig executionConfig, GpuKernelDescriptor descriptor, Object... arguments) {
        backend.invoke(new GpuKernelInvocation(descriptor, arguments, executionConfig));
    }

    /**
     * Invokes a generated GPU kernel with explicit execution config and launcher-scoped artifact resolution.
     */
    public static void invokeFromGeneratedLauncher(
            Class<?> launcherClass,
            GpuExecutionConfig executionConfig,
            GpuKernelDescriptor descriptor,
            Object... arguments
    ) {
        backend.invoke(withLauncherClassLoader(
                launcherClass,
                new GpuKernelInvocation(descriptor, arguments, executionConfig)
        ));
    }

    /**
     * Invokes a generated GPU kernel with explicit runtime compile options.
     *
     * <p>If the active backend is still the default fail-fast backend and the compile options request standard
     * backend/device preflight, this method opens a temporary selected-backend scope before invoking the kernel. Already
     * configured custom/OpenCL scopes are not replaced.</p>
     */
    public static void invokeWithCompileOptions(
            GpuRuntimeCompileOptions compileOptions,
            GpuKernelDescriptor descriptor,
            Object... arguments
    ) {
        invokeWithOptionalBackendDevicePreflight(new GpuKernelInvocation(descriptor, arguments, compileOptions));
    }

    /**
     * Invokes a generated GPU kernel with compile options and launcher-scoped artifact resolution.
     */
    public static void invokeFromGeneratedLauncherWithCompileOptions(
            Class<?> launcherClass,
            GpuRuntimeCompileOptions compileOptions,
            GpuKernelDescriptor descriptor,
            Object... arguments
    ) {
        invokeWithOptionalBackendDevicePreflight(withLauncherClassLoader(
                launcherClass,
                new GpuKernelInvocation(descriptor, arguments, compileOptions)
        ));
    }

    /**
     * Invokes a generated GPU kernel with explicit work size and runtime compile options.
     */
    public static void invokeWithCompileOptions(
            long globalWorkSize,
            GpuRuntimeCompileOptions compileOptions,
            GpuKernelDescriptor descriptor,
            Object... arguments
    ) {
        invokeWithOptionalBackendDevicePreflight(
                new GpuKernelInvocation(descriptor, arguments, globalWorkSize, compileOptions)
        );
    }

    /**
     * Invokes a generated GPU kernel with 1D work size, compile options, and launcher artifact resolution.
     */
    public static void invokeFromGeneratedLauncherWithCompileOptions(
            Class<?> launcherClass,
            long globalWorkSize,
            GpuRuntimeCompileOptions compileOptions,
            GpuKernelDescriptor descriptor,
            Object... arguments
    ) {
        invokeWithOptionalBackendDevicePreflight(withLauncherClassLoader(
                launcherClass,
                new GpuKernelInvocation(descriptor, arguments, globalWorkSize, compileOptions)
        ));
    }

    /**
     * Invokes a generated GPU kernel with explicit execution config and runtime compile options.
     */
    public static void invokeWithCompileOptions(
            GpuExecutionConfig executionConfig,
            GpuRuntimeCompileOptions compileOptions,
            GpuKernelDescriptor descriptor,
            Object... arguments
    ) {
        invokeWithOptionalBackendDevicePreflight(
                new GpuKernelInvocation(descriptor, arguments, executionConfig, compileOptions)
        );
    }

    /**
     * Invokes a generated GPU kernel with execution config, compile options, and launcher artifact resolution.
     */
    public static void invokeFromGeneratedLauncherWithCompileOptions(
            Class<?> launcherClass,
            GpuExecutionConfig executionConfig,
            GpuRuntimeCompileOptions compileOptions,
            GpuKernelDescriptor descriptor,
            Object... arguments
    ) {
        invokeWithOptionalBackendDevicePreflight(withLauncherClassLoader(
                launcherClass,
                new GpuKernelInvocation(descriptor, arguments, executionConfig, compileOptions)
        ));
    }

    /**
     * Invokes one generated method with its deterministic runtime fallback variants.
     *
     * <p>The backend receives every descriptor and selects a compatible method/device pair before compilation. This
     * method is intentionally shaped as one universal generated-launcher entry point so future execution configuration
     * and backend options do not require another fallback-specific overload family.</p>
     */
    public static void invokeVariantsFromGeneratedLauncher(
            Class<?> launcherClass,
            GpuExecutionConfig executionConfig,
            GpuRuntimeCompileOptions compileOptions,
            GpuKernelDescriptor descriptor,
            List<GpuKernelDescriptor> fallbackDescriptors,
            Object... arguments
    ) {
        ClassLoader classLoader = launcherClass == null ? null : launcherClass.getClassLoader();
        invokeWithOptionalBackendDevicePreflight(new GpuKernelInvocation(
                descriptor,
                arguments,
                executionConfig,
                compileOptions,
                classLoader,
                fallbackDescriptors
        ));
    }

    /**
     * Prepares a reusable launcher through the currently configured backend.
     *
     * <p>This is the low-level runtime entry point for hot loops. It performs the backend's cold prepare path once and
     * returns a handle whose later invocations should avoid descriptor selection, source diagnostics, capability
     * discovery, artifact dumping, and kernel compilation.</p>
     */
    public static GpuPreparedLauncher prepare(GpuKernelDescriptor descriptor, Object... arguments) {
        return backend.prepare(new GpuKernelInvocation(descriptor, arguments));
    }

    /**
     * Prepares a reusable launcher with an explicit default execution config.
     */
    public static GpuPreparedLauncher prepare(
            GpuExecutionConfig executionConfig,
            GpuKernelDescriptor descriptor,
            Object... arguments
    ) {
        return backend.prepare(new GpuKernelInvocation(descriptor, arguments, executionConfig));
    }

    /**
     * Prepares a reusable launcher with compile options.
     */
    public static GpuPreparedLauncher prepareWithCompileOptions(
            GpuRuntimeCompileOptions compileOptions,
            GpuKernelDescriptor descriptor,
            Object... arguments
    ) {
        return prepareWithOptionalBackendDevicePreflight(new GpuKernelInvocation(descriptor, arguments, compileOptions));
    }

    /**
     * Prepares a reusable launcher with compile options and an explicit default execution config.
     */
    public static GpuPreparedLauncher prepareWithCompileOptions(
            GpuExecutionConfig executionConfig,
            GpuRuntimeCompileOptions compileOptions,
            GpuKernelDescriptor descriptor,
            Object... arguments
    ) {
        return prepareWithOptionalBackendDevicePreflight(new GpuKernelInvocation(
                descriptor,
                arguments,
                executionConfig,
                compileOptions
        ));
    }

    /**
     * Prepares one generated method with its deterministic runtime fallback variants.
     */
    public static GpuPreparedLauncher prepareVariantsFromGeneratedLauncher(
            Class<?> launcherClass,
            GpuExecutionConfig executionConfig,
            GpuRuntimeCompileOptions compileOptions,
            GpuKernelDescriptor descriptor,
            List<GpuKernelDescriptor> fallbackDescriptors,
            Object... arguments
    ) {
        ClassLoader classLoader = launcherClass == null ? null : launcherClass.getClassLoader();
        return prepareWithOptionalBackendDevicePreflight(new GpuKernelInvocation(
                descriptor,
                arguments,
                executionConfig,
                compileOptions,
                classLoader,
                fallbackDescriptors
        ));
    }

    private static GpuPreparedLauncher prepareWithOptionalBackendDevicePreflight(GpuKernelInvocation invocation) {
        GpuRuntimeBackend activeBackend = backend();
        if (!requiresAutomaticBackendDevicePreflight(activeBackend, invocation.compileOptions())) {
            return activeBackend.prepare(invocation);
        }

        GpuRuntimeLifecycleEventBus lifecycleEventBus = automaticBackendDevicePreflightLifecycleEventBus();
        publishAutomaticBackendDevicePreflightEvent(
                lifecycleEventBus,
                GpuRuntimeLifecycleEventKind.BACKEND_DEVICE_PREFLIGHT_STARTED,
                invocation,
                "started",
                "automatic backend/device preflight started",
                null
        );
        try {
            GpuRuntimeScope scope = automaticBackendDevicePreflightScopeFactory.apply(
                    invocation.compileOptions(),
                    lifecycleEventBus
            );
            GpuPreparedLauncher preparedLauncher = backend().prepare(invocation);
            publishAutomaticBackendDevicePreflightEvent(
                    lifecycleEventBus,
                    GpuRuntimeLifecycleEventKind.BACKEND_DEVICE_PREFLIGHT_COMPLETED,
                    invocation,
                    "success",
                    "automatic backend/device preflight completed",
                    null
            );
            return new ScopedPreparedLauncher(preparedLauncher, scope);
        } catch (RuntimeException failure) {
            publishAutomaticBackendDevicePreflightEvent(
                    lifecycleEventBus,
                    GpuRuntimeLifecycleEventKind.BACKEND_DEVICE_PREFLIGHT_COMPLETED,
                    invocation,
                    "failed",
                    "automatic backend/device preflight failed",
                    failure
            );
            throw failure;
        }
    }

    private record ScopedPreparedLauncher(
            GpuPreparedLauncher delegate,
            GpuRuntimeScope scope
    ) implements GpuPreparedLauncher {

        private ScopedPreparedLauncher {
            delegate = Objects.requireNonNull(delegate, "delegate");
            scope = Objects.requireNonNull(scope, "scope");
        }

        @Override
        public GpuKernelDescriptor descriptor() {
            return delegate.descriptor();
        }

        @Override
        public GpuRuntimeCompileOptions compileOptions() {
            return delegate.compileOptions();
        }

        @Override
        public GpuExecutionConfig defaultExecutionConfig() {
            return delegate.defaultExecutionConfig();
        }

        @Override
        public void invoke(Object... arguments) {
            delegate.invoke(arguments);
        }

        @Override
        public void invokeWithConfig(GpuExecutionConfig executionConfig, Object... arguments) {
            delegate.invokeWithConfig(executionConfig, arguments);
        }

        @Override
        public List<String> dynamicArgumentNames() {
            return delegate.dynamicArgumentNames();
        }

        @Override
        public List<String> staticArgumentNames() {
            return delegate.staticArgumentNames();
        }

        @Override
        public GpuPreparedInvocationTimings lastInvocationTimings() {
            return delegate.lastInvocationTimings();
        }

        @Override
        public GpuPreparedLauncher withStaticArguments(int... argumentIndexes) {
            return new ScopedPreparedLauncher(delegate.withStaticArguments(argumentIndexes), scope);
        }

        @Override
        public GpuPreparedLauncher withoutHostUploadArguments(int... argumentIndexes) {
            return new ScopedPreparedLauncher(delegate.withoutHostUploadArguments(argumentIndexes), scope);
        }

        @Override
        public GpuPreparedLauncher withoutHostReadbackArguments(int... argumentIndexes) {
            return new ScopedPreparedLauncher(delegate.withoutHostReadbackArguments(argumentIndexes), scope);
        }

        @Override
        public void close() {
            RuntimeException failure = null;
            try {
                delegate.close();
            } catch (RuntimeException exception) {
                failure = exception;
            }
            try {
                scope.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static void invokeWithOptionalBackendDevicePreflight(GpuKernelInvocation invocation) {
        GpuRuntimeBackend activeBackend = backend();
        if (!requiresAutomaticBackendDevicePreflight(activeBackend, invocation.compileOptions())) {
            activeBackend.invoke(invocation);
            return;
        }

        GpuRuntimeLifecycleEventBus lifecycleEventBus = automaticBackendDevicePreflightLifecycleEventBus();
        publishAutomaticBackendDevicePreflightEvent(
                lifecycleEventBus,
                GpuRuntimeLifecycleEventKind.BACKEND_DEVICE_PREFLIGHT_STARTED,
                invocation,
                "started",
                "automatic backend/device preflight started",
                null
        );
        try (GpuRuntimeScope ignored = automaticBackendDevicePreflightScopeFactory.apply(
                invocation.compileOptions(),
                lifecycleEventBus
        )) {
            backend().invoke(invocation);
        } catch (RuntimeException failure) {
            publishAutomaticBackendDevicePreflightEvent(
                    lifecycleEventBus,
                    GpuRuntimeLifecycleEventKind.BACKEND_DEVICE_PREFLIGHT_COMPLETED,
                    invocation,
                    "failed",
                    "automatic backend/device preflight failed",
                    failure
            );
            throw failure;
        }
        publishAutomaticBackendDevicePreflightEvent(
                lifecycleEventBus,
                GpuRuntimeLifecycleEventKind.BACKEND_DEVICE_PREFLIGHT_COMPLETED,
                invocation,
                "success",
                "automatic backend/device preflight completed",
                null
        );
    }

    private static GpuRuntimeLifecycleEventBus automaticBackendDevicePreflightLifecycleEventBus() {
        GpuRuntimeLifecycleEventBus eventBus = automaticBackendDevicePreflightLifecycleEventBusFactory.get();
        return eventBus == null ? GpuRuntimeLifecycleEventBus.empty() : eventBus;
    }

    private static void publishAutomaticBackendDevicePreflightEvent(
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            GpuRuntimeLifecycleEventKind kind,
            GpuKernelInvocation invocation,
            String status,
            String message,
            RuntimeException failure
    ) {
        GpuRuntimeCompileOptions compileOptions = invocation.compileOptions();
        GpuBackendCompileOptions backendOptions = compileOptions.backendOptions();
        LinkedHashMap<String, String> fields = GpuRuntimeLifecycleFields.descriptorCompileOptionsFields(
                invocation.descriptor(),
                compileOptions
        );
        fields.putAll(GpuRuntimeLifecycleFields.executionConfigFields(invocation.executionConfig()));
        GpuRuntimeLifecycleFields.putStatus(fields, status);
        GpuRuntimeLifecycleFields.putFailureFields(fields, failure);
        GpuRuntimeArtifactProperties.putPortable(
                fields,
                "runtime.backendDevicePreflight",
                "mode",
                backendOptions.backendDevicePreflightMode()
        );
        GpuRuntimeArtifactProperties.putPortable(
                fields,
                "runtime.backendDevicePreflight",
                "requested",
                backendOptions.requestsStandardBackendDevicePreflight()
        );
        fields.put("pipeline", "runtime-backend-device-preflight");
        fields.put("trigger", "compile-options");
        fields.put("status", status);
        fields.put("backendDevicePreflight.mode", backendOptions.backendDevicePreflightMode());
        fields.put("backendDevicePreflight.requested", Boolean.toString(
                backendOptions.requestsStandardBackendDevicePreflight()
        ));
        fields.put("kernel.name", invocation.descriptor().kernelName());
        fields.put("execution.dimensions", Integer.toString(invocation.executionConfig().dimensions()));
        fields.put("execution.globalShape", invocation.executionConfig().globalShape());
        if (failure != null) {
            fields.put("error.type", failure.getClass().getName());
            fields.put("error.message", failure.getMessage() == null ? "" : failure.getMessage());
        }
        lifecycleEventBus.publish(new GpuRuntimeLifecycleEvent(
                kind,
                compileOptions.backendTarget(),
                invocation.descriptor().kernelResource(),
                compileOptions.optimizationProfile(),
                message,
                fields
        ));
    }

    private static boolean requiresAutomaticBackendDevicePreflight(
            GpuRuntimeBackend activeBackend,
            GpuRuntimeCompileOptions compileOptions
    ) {
        return activeBackend == DEFAULT_BACKEND
                && compileOptions != null
                && compileOptions.backendOptions().requestsStandardBackendDevicePreflight();
    }

    static void setAutomaticBackendDevicePreflightScopeFactoryForTesting(
            Function<GpuRuntimeCompileOptions, GpuRuntimeScope> scopeFactory
    ) {
        Function<GpuRuntimeCompileOptions, GpuRuntimeScope> factory = Objects.requireNonNull(
                scopeFactory,
                "scopeFactory"
        );
        automaticBackendDevicePreflightScopeFactory = (options, ignoredEventBus) -> factory.apply(options);
    }

    static void setAutomaticBackendDevicePreflightScopeFactoryForTesting(
            BiFunction<GpuRuntimeCompileOptions, GpuRuntimeLifecycleEventBus, GpuRuntimeScope> scopeFactory
    ) {
        automaticBackendDevicePreflightScopeFactory = Objects.requireNonNull(scopeFactory, "scopeFactory");
    }

    static void resetAutomaticBackendDevicePreflightScopeFactoryForTesting() {
        automaticBackendDevicePreflightScopeFactory = GpuRuntime::selectAndUseStandardBackendDevice;
    }

    static void setAutomaticBackendDevicePreflightLifecycleEventBusFactoryForTesting(
            Supplier<GpuRuntimeLifecycleEventBus> eventBusFactory
    ) {
        automaticBackendDevicePreflightLifecycleEventBusFactory = Objects.requireNonNull(
                eventBusFactory,
                "eventBusFactory"
        );
    }

    static void resetAutomaticBackendDevicePreflightLifecycleEventBusFactoryForTesting() {
        automaticBackendDevicePreflightLifecycleEventBusFactory = GpuRuntimeLifecycleEventBus::loadFromServiceLoader;
    }

    static void setStandardBackendDeviceScopeFactoryForTesting(
            Function<GpuRuntimeCompileOptions, GpuRuntimeScope> scopeFactory
    ) {
        standardBackendDeviceScopeFactory = Objects.requireNonNull(scopeFactory, "scopeFactory");
    }

    static void resetStandardBackendDeviceScopeFactoryForTesting() {
        standardBackendDeviceScopeFactory = GpuRuntime::selectAndUseStandardBackendDevice;
    }

    private static GpuRuntimeScope selectAndUseStandardBackendDevice(GpuRuntimeCompileOptions compileOptions) {
        return use(trySelectStandardBackendAndDevice(compileOptions));
    }

    private static GpuRuntimeScope selectAndUseStandardBackendDevice(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
                .preferStandardBackendsWithPlannedDiagnostics()
                .build();
        return use(trySelectStandardBackendAndDevice(policy, compileOptions, lifecycleEventBus));
    }

    private static GpuKernelInvocation withLauncherClassLoader(
            Class<?> launcherClass,
            GpuKernelInvocation invocation
    ) {
        ClassLoader classLoader = launcherClass == null ? null : launcherClass.getClassLoader();
        return invocation.withArtifactClassLoader(classLoader);
    }

    private static GpuRuntimeScope installScopedBackend(GpuRuntimeBackend newBackend, boolean closeInstalledBackend) {
        Objects.requireNonNull(newBackend, "newBackend");
        GpuRuntimeBackend previousBackend = backend();
        setBackend(newBackend);
        return new GpuRuntimeScope(previousBackend, newBackend, closeInstalledBackend);
    }

}
