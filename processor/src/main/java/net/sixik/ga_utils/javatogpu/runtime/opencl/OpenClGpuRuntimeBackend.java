package net.sixik.ga_utils.javatogpu.runtime.opencl;

import dev.denismasterherobrine.packager.opencl.core.OpenClBuffer;
import dev.denismasterherobrine.packager.opencl.core.OpenClEvents;
import dev.denismasterherobrine.packager.opencl.core.OpenClException;
import net.sixik.ga_utils.javatogpu.api.images.Image1DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image1DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image1DBufferReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image1DBufferWriteOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image1DReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image1DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image2DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image2DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image2DMipmappedReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image2DMipmappedWriteOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image3DReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image3DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.images.Sampler;
import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilationResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipelineResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLowerer;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLowerers;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendInvocationResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelCompiler;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelInvoker;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelPreparer;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendHookRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLoweringResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendPipelineStage;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendPreparationResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceReconstructionResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceSelectionPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourcePromotionGate;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceSwitchingDecision;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceSwitchingPolicy;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendStageResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeApiVersion;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeArtifactDumpSummary;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeArtifactProperties;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCompilationSummary;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendStateSummary;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEvent;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEventBus;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEventKind;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeFeature;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuPromotionArtifactRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuPromotionArtifactSupport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileCacheKey;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDump;
import net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeCompileArtifactDumper;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactSnapshot;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendUnavailableException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCapabilityException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptionsException;
import net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeCallSiteResolverSupport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDiagnosticContext;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileInvalidationStamp;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileProvenance;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleFields;
import net.sixik.ga_utils.javatogpu.runtime.launch.GpuRuntimeCompileRequestSupport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendDevicePreselector;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelection;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelectionException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeEquivalenceCaseEvidence;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeEquivalenceEvidence;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeEquivalenceExecutor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeEquivalenceRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeFallbackEvidence;
import net.sixik.ga_utils.javatogpu.runtime.validation.GpuBackendSourcePromotionWorkloadGateFormatter;
import net.sixik.ga_utils.javatogpu.runtime.GpuOptimizationStrategy;
import net.sixik.ga_utils.javatogpu.runtime.GpuOptimizationStrategyDecision;
import net.sixik.ga_utils.javatogpu.runtime.GpuProductionPromotionDecision;
import net.sixik.ga_utils.javatogpu.runtime.GpuProductionPromotionOperatorAcceptance;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrArtifactLoader;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationOutcome;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationPassReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizerRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrSelection;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationBindingSummary;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodVariantSelection;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackend;
import net.sixik.ga_utils.javatogpu.runtime.variants.GpuRuntimeMethodVariantSelector;
import java.util.Objects;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;

/**
 * OpenCL implementation of {@link GpuRuntimeBackend}.
 *
 * <p>This backend compiles generated OpenCL kernel source, marshals Java arguments into OpenCL values and buffers,
 * launches the kernel, and copies writable results back into the original Java objects.
 *
 * <p>Two cache modes are available:
 *
 * <ul>
 *   <li>{@link CacheMode#INSTANCE}: the default mode. Kernel/session caches live only inside this backend instance and
 *   are released on {@link #close()}.</li>
 *   <li>{@link CacheMode#SHARED}: compile/session caches are shared across backend instances and survive ordinary
 *   {@link #close()} calls. This mode is meant for hot-path repeated invocations where compile latency should be paid
 *   once and reused broadly.</li>
 * </ul>
 *
 * <p>Use {@link #sharedCache()} to opt into the shared mode and {@link #shutdownSharedCache()} when the application is
 * done with the global OpenCL cache.
 */
public class OpenClGpuRuntimeBackend implements GpuRuntimeBackend, GpuRuntimeBackendDevicePreselector, AutoCloseable {

    /**
     * Controls whether compiled kernels and runtime session state are local to one backend instance or reused globally.
     */
    public enum CacheMode {
        /**
         * Keep caches only inside the current backend instance.
         */
        INSTANCE,

        /**
         * Reuse compiled kernels and OpenCL runtime session state across backend instances.
         */
        SHARED
    }

    private static final java.util.regex.Pattern DOUBLE_USAGE_PATTERN = java.util.regex.Pattern.compile("\\bdouble(?:[234])?\\b");
    private static final String BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE_FILE_PROPERTY = "javatogpu.opencl.backendSourcePromotionWorkloadGateFile";
    private static final String RUNTIME_COMPILE_ARTIFACT_DIRECTORY_PROPERTY = "javatogpu.opencl.runtimeCompileArtifactDirectory";
    private static final String PRODUCTION_PROMOTION_EXPLAINABILITY_FILE_PROPERTY = "javatogpu.opencl.productionPromotionExplainabilityFile";
    private static final Object SHARED_RUNTIME_LOCK = new Object();
    private static final Map<GpuRuntimeCompileCacheKey, OpenClCompiledKernel> SHARED_COMPILED_KERNELS = new ConcurrentHashMap<>();
    private static volatile OpenClRuntimeSession sharedSession;
    private static volatile OpenClRuntimeCapabilities sharedCapabilities;

    private final CacheMode cacheMode;
    private final Map<GpuRuntimeCompileCacheKey, OpenClCompiledKernel> compiledKernels = new ConcurrentHashMap<>();
    private final OpenClDeviceBufferRegistry bufferRegistry = new OpenClDeviceBufferRegistry();
    private final OpenClExecutionPreparer executionPreparer = new OpenClExecutionPreparer(bufferRegistry);
    private final OpenClKernelCompiler kernelCompiler = new OpenClKernelCompiler(this);
    private final OpenClKernelInvoker kernelInvoker = new OpenClKernelInvoker(this);
    private final GpuRuntimeIrOptimizerRegistry irOptimizerRegistry;
    private final GpuOptimizationStrategy optimizationStrategy;
    private final GpuRuntimeDevicePolicyRegistry devicePolicyRegistry;
    private final GpuRuntimeLifecycleEventBus lifecycleEventBus;
    private final GpuBackendHookRegistry backendHookRegistry;
    private final ThreadLocal<OpenClSessionSelectionRequest> sessionSelectionRequest = new ThreadLocal<>();
    private final Map<String, Object> nativeBuffers = new ConcurrentHashMap<>();
    private final AtomicLong invocationCount = new AtomicLong();
    private final AtomicLong compileCount = new AtomicLong();
    private final AtomicLong compileCacheHitCount = new AtomicLong();
    private final AtomicLong sessionCreationCount = new AtomicLong();
    private final AtomicLong deviceBufferCreationCount = new AtomicLong();
    private volatile OpenClRuntimeSession session;
    private volatile OpenClRuntimeCapabilities capabilities;
    private volatile GpuRuntimeDeviceDiscoveryResult preselectedDeviceDiscovery;

    /**
     * Creates an OpenCL backend with instance-local cache lifetime.
     *
     * <p>This is the safest default for explicit lifecycle management: kernel cache, native buffers, and session state
     * belong only to this backend instance.
     */
    public OpenClGpuRuntimeBackend() {
        this(CacheMode.INSTANCE);
    }

    /**
     * Creates a backend with the requested cache mode.
     *
     * <p>This constructor is protected so custom test backends or specialized subclasses can opt into a specific cache
     * strategy while the public API stays small.
     */
    protected OpenClGpuRuntimeBackend(CacheMode cacheMode) {
        this(cacheMode, GpuRuntimeIrOptimizerRegistry.loadFromServiceLoader());
    }

    protected OpenClGpuRuntimeBackend(CacheMode cacheMode, GpuRuntimeLifecycleEventBus lifecycleEventBus) {
        this(cacheMode, lifecycleEventBus, GpuBackendHookRegistry.loadWithServiceLoader());
    }

    protected OpenClGpuRuntimeBackend(
            CacheMode cacheMode,
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            GpuBackendHookRegistry backendHookRegistry
    ) {
        this(
                cacheMode,
                GpuRuntimeIrOptimizerRegistry.loadFromServiceLoader(),
                GpuOptimizationStrategy.advisoryDefault(),
                GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns(),
                lifecycleEventBus,
                backendHookRegistry
        );
    }

    protected OpenClGpuRuntimeBackend(CacheMode cacheMode, GpuRuntimeIrOptimizerRegistry irOptimizerRegistry) {
        this(cacheMode, irOptimizerRegistry, GpuOptimizationStrategy.advisoryDefault());
    }

    protected OpenClGpuRuntimeBackend(
            CacheMode cacheMode,
            GpuRuntimeIrOptimizerRegistry irOptimizerRegistry,
            GpuOptimizationStrategy optimizationStrategy
    ) {
        this(
                cacheMode,
                irOptimizerRegistry,
                optimizationStrategy,
                GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns()
        );
    }

    protected OpenClGpuRuntimeBackend(
            CacheMode cacheMode,
            GpuRuntimeIrOptimizerRegistry irOptimizerRegistry,
            GpuOptimizationStrategy optimizationStrategy,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry
    ) {
        this(
                cacheMode,
                irOptimizerRegistry,
                optimizationStrategy,
                devicePolicyRegistry,
                GpuRuntimeLifecycleEventBus.loadFromServiceLoader()
        );
    }

    protected OpenClGpuRuntimeBackend(
            CacheMode cacheMode,
            GpuRuntimeIrOptimizerRegistry irOptimizerRegistry,
            GpuOptimizationStrategy optimizationStrategy,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        this(
                cacheMode,
                irOptimizerRegistry,
                optimizationStrategy,
                devicePolicyRegistry,
                lifecycleEventBus,
                GpuBackendHookRegistry.loadWithServiceLoader()
        );
    }

    protected OpenClGpuRuntimeBackend(
            CacheMode cacheMode,
            GpuRuntimeIrOptimizerRegistry irOptimizerRegistry,
            GpuOptimizationStrategy optimizationStrategy,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry,
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            GpuBackendHookRegistry backendHookRegistry
    ) {
        this.cacheMode = Objects.requireNonNull(cacheMode, "cacheMode");
        this.irOptimizerRegistry = Objects.requireNonNull(irOptimizerRegistry, "irOptimizerRegistry");
        this.optimizationStrategy = Objects.requireNonNull(optimizationStrategy, "optimizationStrategy");
        this.devicePolicyRegistry = Objects.requireNonNull(devicePolicyRegistry, "devicePolicyRegistry");
        this.lifecycleEventBus = lifecycleEventBus == null ? GpuRuntimeLifecycleEventBus.empty() : lifecycleEventBus;
        this.backendHookRegistry = backendHookRegistry == null ? GpuBackendHookRegistry.empty() : backendHookRegistry;
    }

    /**
     * Creates an OpenCL backend that reuses compiled kernels and session state across backend instances.
     *
     * <p>This mode is useful when application code repeatedly installs/disposes backend objects but still wants hot
     * repeated kernel calls with near-zero compile overhead after warm-up.
     */
    public static OpenClGpuRuntimeBackend sharedCache() {
        return new OpenClGpuRuntimeBackend(CacheMode.SHARED);
    }

    @Override
    public void preselectDevice(GpuRuntimeDeviceDiscoveryResult discoveryResult) {
        GpuRuntimeDeviceDiscoveryResult discovery = Objects.requireNonNull(discoveryResult, "discoveryResult");
        if (discovery.backendTarget() != GpuBackendTarget.OPENCL) {
            throw new IllegalArgumentException("OpenCL backend cannot preselect device discovery for "
                    + discovery.backendTarget());
        }
        discovery.selectedDevice().orElseThrow(() -> new IllegalArgumentException(
                "OpenCL backend preselection requires a selected OpenCL device"
        ));
        this.preselectedDeviceDiscovery = discovery;
    }

    /**
     * Releases the global shared OpenCL cache created by {@link #sharedCache()}.
     *
     * <p>Call this during application shutdown, plugin unload, or when you explicitly want to drop all globally cached
     * compiled kernels and the shared OpenCL session.
     */
    public static void shutdownSharedCache() {
        synchronized (SHARED_RUNTIME_LOCK) {
            SHARED_COMPILED_KERNELS.values().forEach(OpenClCompiledKernel::close);
            SHARED_COMPILED_KERNELS.clear();

            OpenClRuntimeSession currentSession = sharedSession;
            sharedSession = null;
            sharedCapabilities = null;
            if (currentSession != null) {
                currentSession.close();
            }
        }
    }

    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.OPENCL;
    }

    @Override
    public GpuRuntimeBackendReport describeCapabilities() {
        try {
            OpenClRuntimeCapabilities capabilities = runtimeCapabilities();
            java.util.EnumSet<GpuRuntimeFeature> features = java.util.EnumSet.noneOf(GpuRuntimeFeature.class);
            if (capabilities.supportsDoublePrecision()) {
                features.add(GpuRuntimeFeature.DOUBLE_PRECISION);
            }
            if (capabilities.supportsImages()) {
                features.add(GpuRuntimeFeature.IMAGES);
            }
            if (capabilities.supportsImage3dWrites()) {
                features.add(GpuRuntimeFeature.IMAGE3D_WRITES);
            }
            if (cacheMode == CacheMode.SHARED) {
                features.add(GpuRuntimeFeature.SHARED_CACHE);
            }
            return GpuRuntimeBackendReport.available(
                    backendTarget(),
                    cacheMode == CacheMode.SHARED ? "OpenCL (shared cache)" : "OpenCL",
                    capabilities.deviceLabel(),
                    GpuRuntimeApiVersion.parseFirst(capabilities.deviceVersion()),
                    capabilities.deviceVersion(),
                    features,
                    capabilities.localMemoryBytes(),
                    capabilities.maxWorkGroupSize(),
                    null
            );
        } catch (UnsupportedOperationException exception) {
            return GpuRuntimeBackendReport.unavailable(backendTarget(), "OpenCL", exception.getMessage());
        }
    }

    @Override
    public GpuPromotionArtifactSupport promotionArtifactSupport() {
        return GpuPromotionArtifactSupport.complete(backendTarget());
    }

    @Override
    public final void invoke(GpuKernelInvocation invocation) {
        invocationCount.incrementAndGet();
        GpuRuntimeCompileOptions requestCompileOptions = invocation.compileOptions() == null
                ? GpuRuntimeCompileOptions.defaults(backendTarget())
                : invocation.compileOptions();
        Optional<IrGpuArtifact> primaryIrGpuArtifact = loadRuntimeIrArtifact(
                invocation.descriptor(),
                invocation.artifactClassLoader(),
                requestCompileOptions,
                "primary"
        );
        GpuRuntimeDiagnosticContext primaryContext = diagnosticContext(
                invocation.descriptor(),
                primaryIrGpuArtifact,
                requestCompileOptions
        );
        primaryContext = primaryContext.withCallSite(GpuRuntimeCallSiteResolverSupport.resolve(
                invocation.artifactClassLoader(),
                primaryContext.sourceLocation()
        ));
        publishLifecycleEvent(
                GpuRuntimeLifecycleEventKind.VALIDATION_STARTED,
                invocation.descriptor(),
                requestCompileOptions,
                "OpenCL compile option validation started",
                validationFields("compile-options", "started", null)
        );
        try {
            validateCompileOptions(requestCompileOptions);
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.VALIDATION_COMPLETED,
                    invocation.descriptor(),
                    requestCompileOptions,
                    "OpenCL compile option validation completed",
                    validationFields("compile-options", "succeeded", null)
            );
        } catch (RuntimeException exception) {
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.VALIDATION_COMPLETED,
                    invocation.descriptor(),
                    requestCompileOptions,
                    "OpenCL compile option validation failed",
                    validationFields("compile-options", "failed", exception)
            );
            if (exception instanceof GpuRuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new GpuRuntimeCompileOptionsException(
                    "OpenCL compile options are invalid: " + OpenClFailureFormatter.rootMessage(exception),
                    primaryContext,
                    exception
            );
        }
        publishLifecycleEvent(
                GpuRuntimeLifecycleEventKind.DESCRIPTOR_DISCOVERY_STARTED,
                invocation.descriptor(),
                requestCompileOptions,
                "OpenCL method variant descriptor selection started",
                descriptorSelectionFields(invocation, Optional.empty(), invocation.descriptor(), "started", null)
        );
        Optional<GpuRuntimeMethodVariantSelection> methodVariantSelection;
        try {
            methodVariantSelection = selectMethodVariant(
                    invocation,
                    requestCompileOptions
            );
        } catch (RuntimeException exception) {
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.DESCRIPTOR_DISCOVERY_COMPLETED,
                    invocation.descriptor(),
                    requestCompileOptions,
                    "OpenCL method variant descriptor selection failed",
                    descriptorSelectionFields(invocation, Optional.empty(), invocation.descriptor(), "failed", exception)
            );
            throw exception;
        }
        GpuKernelInvocation selectedInvocation = methodVariantSelection
                .map(selection -> invocation.withSelectedDescriptor(selection.selectedDescriptor()))
                .orElse(invocation);
        publishLifecycleEvent(
                GpuRuntimeLifecycleEventKind.DESCRIPTOR_DISCOVERY_COMPLETED,
                selectedInvocation.descriptor(),
                requestCompileOptions,
                "OpenCL method variant descriptor selection completed",
                descriptorSelectionFields(invocation, methodVariantSelection, selectedInvocation.descriptor(), "succeeded", null)
        );
        Optional<IrGpuArtifact> loadedIrGpuArtifact = methodVariantSelection
                .flatMap(GpuRuntimeMethodVariantSelection::selectedArtifact)
                .or(() -> sameDescriptor(invocation.descriptor(), selectedInvocation.descriptor())
                        ? primaryIrGpuArtifact
                        : loadRuntimeIrArtifact(
                        selectedInvocation.descriptor(),
                        selectedInvocation.artifactClassLoader(),
                        requestCompileOptions,
                        "selected-variant"
                ));
        GpuRuntimeDiagnosticContext selectedContextBase = diagnosticContext(
                selectedInvocation.descriptor(),
                loadedIrGpuArtifact,
                requestCompileOptions
        );
        GpuRuntimeDiagnosticContext selectedContext = selectedContextBase.withCallSite(GpuRuntimeCallSiteResolverSupport.resolve(
                selectedInvocation.artifactClassLoader(),
                selectedContextBase.sourceLocation()
        ));
        if (OpenClAbiDebug.enabled()) {
            System.err.println(OpenClAbiDebug.describeInvocation(
                    selectedInvocation.descriptor(),
                    selectedInvocation.arguments()
            ));
        }
        OpenClKernelArguments arguments;
        OpenClExecutionPlan plan;
        publishLifecycleEvent(
                GpuRuntimeLifecycleEventKind.VALIDATION_STARTED,
                selectedInvocation.descriptor(),
                requestCompileOptions,
                "OpenCL invocation precondition validation started",
                validationFields("invocation-preconditions", "started", null)
        );
        try {
            arguments = OpenClArgumentMarshaller.marshall(
                    selectedInvocation.descriptor(),
                    selectedInvocation.arguments()
            );
            plan = OpenClExecutionPlanner.plan(arguments);
            validateInvocationPreconditions(selectedInvocation, plan);
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.VALIDATION_COMPLETED,
                    selectedInvocation.descriptor(),
                    requestCompileOptions,
                    "OpenCL invocation precondition validation completed",
                    validationFields("invocation-preconditions", "succeeded", null)
            );
        } catch (RuntimeException exception) {
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.VALIDATION_COMPLETED,
                    selectedInvocation.descriptor(),
                    requestCompileOptions,
                    "OpenCL invocation precondition validation failed",
                    validationFields("invocation-preconditions", "failed", exception)
            );
            if (exception instanceof GpuRuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new GpuRuntimeInvocationException(
                    "OpenCL invocation preparation failed: " + OpenClFailureFormatter.contextualMessage(exception),
                    selectedContext,
                    exception
            );
        }
        GpuRuntimeCompileRequest compileRequest;
        OpenClSessionSelectionRequest previousSessionRequest = sessionSelectionRequest.get();
        sessionSelectionRequest.set(new OpenClSessionSelectionRequest(
                selectedInvocation.descriptor(),
                requestCompileOptions,
                loadedIrGpuArtifact,
                selectedContext
        ));
        try {
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.VALIDATION_STARTED,
                    selectedInvocation.descriptor(),
                    requestCompileOptions,
                    "OpenCL runtime capability validation started",
                    validationFields("runtime-capabilities", "started", null)
            );
            try {
                validateActiveSessionSelection(selectedInvocation.descriptor(), requestCompileOptions, loadedIrGpuArtifact);
                validateCapabilitySupport(selectedInvocation.descriptor(), plan);
                publishLifecycleEvent(
                        GpuRuntimeLifecycleEventKind.VALIDATION_COMPLETED,
                        selectedInvocation.descriptor(),
                        requestCompileOptions,
                        "OpenCL runtime capability validation completed",
                        validationFields("runtime-capabilities", "succeeded", null)
                );
            } catch (RuntimeException exception) {
                publishLifecycleEvent(
                        GpuRuntimeLifecycleEventKind.VALIDATION_COMPLETED,
                        selectedInvocation.descriptor(),
                        requestCompileOptions,
                        "OpenCL runtime capability validation failed",
                        validationFields("runtime-capabilities", "failed", exception)
                );
                if (exception instanceof GpuRuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new GpuRuntimeCapabilityException(
                        OpenClFailureFormatter.rootMessage(exception),
                        selectedContext,
                        exception
                );
            }
            compileRequest = buildCompileRequest(selectedInvocation)
                    .withIrGpuArtifact(loadedIrGpuArtifact);
        } finally {
            if (previousSessionRequest == null) {
                sessionSelectionRequest.remove();
            } else {
                sessionSelectionRequest.set(previousSessionRequest);
            }
        }
        compileRequest = applyProductionPromotionDecision(compileRequest);
        GpuRuntimeIrOptimizationResult optimizationResult = optimizeRuntimeIrWithReport(compileRequest);
        GpuRuntimeCompileRequest optimizedCompileRequest = optimizationResult.compileRequest();
        GpuBackendModuleArtifact originalModuleArtifact = runtimeCompileArtifactsConfigured()
                ? lowerBackendModuleChecked(compileRequest, "original-artifact-dump")
                : null;
        GpuBackendModuleArtifact optimizedModuleArtifact = runtimeCompileArtifactsConfigured()
                || runtimeIrOptimizerExperimentalApplyRequested(optimizedCompileRequest)
                ? lowerOptimizedReviewModuleChecked(optimizedCompileRequest, optimizationResult.report(), "optimized-review")
                : lowerBackendModuleChecked(optimizedCompileRequest, "optimized-selected");
        GpuRuntimeEquivalenceEvidence runtimeEquivalenceEvidence = executeRuntimeEquivalence(new GpuRuntimeEquivalenceRequest(
                compileRequest,
                optimizedCompileRequest,
                optimizedModuleArtifact,
                optimizationResult.report(),
                selectedInvocation.arguments(),
                selectedInvocation.executionConfig()
        ));
        GpuRuntimeCompileArtifactSnapshot selectionSnapshot = GpuRuntimeCompileArtifactSnapshot.from(
                compileRequest,
                optimizedCompileRequest,
                optimizedModuleArtifact,
                GpuRuntimeCompileInvalidationStamp.from(
                        optimizedCompileRequest,
                        optimizedModuleArtifact,
                        optimizerPipelineVersion()
                ),
                GpuRuntimeCompileProvenance.from(optimizedCompileRequest),
                optimizationResult.report(),
                runtimeEquivalenceEvidence
        );
        GpuRuntimeIrSelection runtimeIrSelection = selectionSnapshot.runtimeIrSelection();
        GpuRuntimeCompileRequest selectedCompileRequest = optimizedCompileRequest.withIrGpuArtifact(
                runtimeIrSelection.selectedArtifact()
        );
        publishLifecycleEvent(
                GpuRuntimeLifecycleEventKind.FALLBACK_OR_ROLLBACK_SELECTED,
                selectedCompileRequest,
                "OpenCL runtime IR fallback/rollback selection decided",
                fallbackOrRollbackSelectionFields(runtimeIrSelection, selectionSnapshot.fallbackEvidence())
        );
        GpuBackendModuleArtifact moduleArtifact = sameSelectedIr(optimizedCompileRequest, selectedCompileRequest)
                ? optimizedModuleArtifact
                : sameSelectedIr(compileRequest, selectedCompileRequest) && originalModuleArtifact != null
                ? originalModuleArtifact
                : lowerBackendModuleChecked(selectedCompileRequest, "runtime-ir-selection");
        GpuRuntimeCompileInvalidationStamp invalidationStamp = GpuRuntimeCompileInvalidationStamp.from(
                selectedCompileRequest,
                moduleArtifact,
                optimizerPipelineVersion()
        );
        GpuRuntimeCompileArtifactSnapshot artifactSnapshotBase = GpuRuntimeCompileArtifactSnapshot.from(
                compileRequest,
                optimizedCompileRequest,
                moduleArtifact,
                invalidationStamp,
                GpuRuntimeCompileProvenance.from(selectedCompileRequest),
                optimizationResult.report(),
                runtimeEquivalenceEvidence
        ).withBackendStageModuleArtifacts(originalModuleArtifact, optimizedModuleArtifact);
        GpuBackendSourcePromotionGate sourcePromotionGate = backendSourcePromotionGate(
                selectedCompileRequest,
                moduleArtifact,
                runtimeEquivalenceEvidence,
                artifactSnapshotBase.fallbackEvidence()
        );
        GpuBackendSourceSwitchingDecision sourceSwitchingDecision = backendSourceSwitchingDecision(
                selectedCompileRequest,
                moduleArtifact,
                sourcePromotionGate
        );
        publishLifecycleEvent(
                GpuRuntimeLifecycleEventKind.SOURCE_SELECTION_DECIDED,
                selectedCompileRequest,
                "OpenCL backend source selection decided",
                sourceSelectionFields(moduleArtifact, sourcePromotionGate, sourceSwitchingDecision, runtimeIrSelection)
        );
        GpuRuntimeCompileArtifactSnapshot artifactSnapshot = withRuntimeDeviceSelection(
                artifactSnapshotBase.withBackendSourceState(
                        sourcePromotionGate,
                        sourceSwitchingDecision
                ),
                methodVariantSelection
        );
        dumpBackendSourcePromotionWorkloadGate(artifactSnapshot);
        GpuRuntimeCompileCacheKey compileCacheKey = GpuRuntimeCompileCacheKey.from(
                selectedCompileRequest,
                moduleArtifact,
                invalidationStamp
        );
        executeProductionPipeline(
                selectedCompileRequest,
                moduleArtifact,
                artifactSnapshot,
                compileCacheKey,
                plan,
                selectedInvocation.executionConfig(),
                selectedContext
        );
    }

    private GpuBackendExecutionPipelineResult<OpenClCompiledKernel, OpenClPreparedExecution> executeProductionPipeline(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            GpuRuntimeCompileCacheKey compileCacheKey,
            OpenClExecutionPlan plan,
            GpuExecutionConfig executionConfig,
            GpuRuntimeDiagnosticContext diagnosticContext
    ) {
        OpenClProductionExecutionPipeline pipeline = new OpenClProductionExecutionPipeline(
                productionCachedCompiler(compileCacheKey, artifactSnapshot, diagnosticContext),
                kernelPreparer(),
                productionCheckedInvoker(diagnosticContext)
        );
        return pipeline.execute(
                compileRequest,
                moduleArtifact,
                plan,
                executionConfig
        );
    }

    private GpuBackendKernelCompiler<OpenClCompiledKernel> productionCachedCompiler(
            GpuRuntimeCompileCacheKey compileCacheKey,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            GpuRuntimeDiagnosticContext diagnosticContext
    ) {
        return new GpuBackendKernelCompiler<>() {
            @Override
            public GpuBackendTarget backendTarget() {
                return GpuBackendTarget.OPENCL;
            }

            @Override
            public OpenClCompiledKernel compile(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                OpenClCompiledKernel compiledKernel = compileKernelFromProductionCache(
                        compileRequest,
                        moduleArtifact,
                        compileCacheKey,
                        artifactSnapshot,
                        diagnosticContext
                );
                dumpRuntimeCompileArtifacts(compiledKernel.artifactSnapshot());
                return compiledKernel;
            }
        };
    }

    private OpenClCompiledKernel compileKernelFromProductionCache(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact,
            GpuRuntimeCompileCacheKey compileCacheKey,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            GpuRuntimeDiagnosticContext diagnosticContext
    ) {
        Map<GpuRuntimeCompileCacheKey, OpenClCompiledKernel> kernelCache = compiledKernelCache();
        OpenClCompiledKernel compiledKernel = kernelCache.get(compileCacheKey);
        if (compiledKernel != null) {
            compileCacheHitCount.incrementAndGet();
            return compiledKernel;
        }
        return kernelCache.computeIfAbsent(
                compileCacheKey,
                ignored -> compileKernelChecked(
                        compileRequest,
                        moduleArtifact,
                        artifactSnapshot,
                        diagnosticContext
                )
        );
    }

    private GpuBackendKernelInvoker<OpenClPreparedExecution> productionCheckedInvoker(
            GpuRuntimeDiagnosticContext diagnosticContext
    ) {
        return new GpuBackendKernelInvoker<>() {
            @Override
            public GpuBackendTarget backendTarget() {
                return GpuBackendTarget.OPENCL;
            }

            @Override
            public void invoke(OpenClPreparedExecution preparedKernel, GpuExecutionConfig executionConfig) {
                executeKernelChecked(withExecutionConfig(preparedKernel, executionConfig), diagnosticContext);
            }
        };
    }

    private static OpenClPreparedExecution withExecutionConfig(
            OpenClPreparedExecution preparedKernel,
            GpuExecutionConfig executionConfig
    ) {
        if (executionConfig == null || preparedKernel.explicitExecutionConfig() != null) {
            return preparedKernel;
        }
        return new OpenClPreparedExecution(
                preparedKernel.compiledKernel(),
                preparedKernel.bufferBindings(),
                preparedKernel.localBindings(),
                preparedKernel.scalarBindings(),
                preparedKernel.argumentBindings(),
                executionConfig
        );
    }

    private GpuRuntimeDiagnosticContext diagnosticContext(
            GpuKernelDescriptor descriptor,
            Optional<IrGpuArtifact> artifact,
            GpuRuntimeCompileOptions compileOptions
    ) {
        Optional<GpuRuntimeDeviceProfile> activeDevice = runtimeDeviceSelection()
                .flatMap(GpuRuntimeDeviceSelection::selectedDevice);
        return GpuRuntimeDiagnosticContext.from(descriptor, artifact, activeDevice, compileOptions);
    }

    private static boolean sameDescriptor(GpuKernelDescriptor left, GpuKernelDescriptor right) {
        return left.kernelName().equals(right.kernelName())
                && left.kernelResource().equals(right.kernelResource())
                && left.irGpuResource().equals(right.irGpuResource());
    }

    private Optional<IrGpuArtifact> loadRuntimeIrArtifact(
            GpuKernelDescriptor descriptor,
            ClassLoader artifactClassLoader,
            GpuRuntimeCompileOptions compileOptions,
            String loadRole
    ) {
        publishLifecycleEvent(
                GpuRuntimeLifecycleEventKind.IRGPU_LOAD_STARTED,
                descriptor,
                compileOptions,
                "Runtime IrGpu artifact load started",
                irGpuLoadFields(loadRole, Optional.empty(), "started", null)
        );
        try {
            Optional<IrGpuArtifact> artifact = GpuRuntimeIrArtifactLoader.load(descriptor, artifactClassLoader);
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.IRGPU_LOAD_COMPLETED,
                    descriptor,
                    compileOptions,
                    "Runtime IrGpu artifact load completed",
                    irGpuLoadFields(loadRole, artifact, "succeeded", null)
            );
            return artifact;
        } catch (RuntimeException exception) {
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.IRGPU_LOAD_COMPLETED,
                    descriptor,
                    compileOptions,
                    "Runtime IrGpu artifact load failed",
                    irGpuLoadFields(loadRole, Optional.empty(), "failed", exception)
            );
            throw exception;
        }
    }

    private GpuBackendModuleArtifact lowerBackendModuleChecked(
            GpuRuntimeCompileRequest compileRequest,
            String purpose
    ) {
        publishLifecycleEvent(
                GpuRuntimeLifecycleEventKind.BACKEND_LOWERER_SELECTION_STARTED,
                compileRequest,
                "OpenCL backend lowerer selection started",
                backendLowererFields(compileRequest, purpose, null, "started", null)
        );
        try {
            GpuBackendModuleArtifact moduleArtifact = lowerBackendModule(compileRequest);
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.BACKEND_LOWERER_SELECTION_COMPLETED,
                    compileRequest,
                    "OpenCL backend lowerer selection completed",
                    backendLowererFields(compileRequest, purpose, moduleArtifact, "succeeded", null)
            );
            return moduleArtifact;
        } catch (RuntimeException exception) {
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.BACKEND_LOWERER_SELECTION_COMPLETED,
                    compileRequest,
                    "OpenCL backend lowerer selection failed",
                    backendLowererFields(compileRequest, purpose, null, "failed", exception)
            );
            throw exception;
        }
    }

    private GpuBackendModuleArtifact lowerOptimizedReviewModuleChecked(
            GpuRuntimeCompileRequest compileRequest,
            GpuRuntimeIrOptimizationReport optimizationReport,
            String purpose
    ) {
        publishLifecycleEvent(
                GpuRuntimeLifecycleEventKind.BACKEND_LOWERER_SELECTION_STARTED,
                compileRequest,
                "OpenCL optimized review lowerer selection started",
                backendLowererFields(compileRequest, purpose, null, "started", null)
        );
        try {
            GpuBackendModuleArtifact moduleArtifact = lowerOptimizedReviewModule(compileRequest, optimizationReport);
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.BACKEND_LOWERER_SELECTION_COMPLETED,
                    compileRequest,
                    "OpenCL optimized review lowerer selection completed",
                    backendLowererFields(compileRequest, purpose, moduleArtifact, "succeeded", null)
            );
            return moduleArtifact;
        } catch (RuntimeException exception) {
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.BACKEND_LOWERER_SELECTION_COMPLETED,
                    compileRequest,
                    "OpenCL optimized review lowerer selection failed",
                    backendLowererFields(compileRequest, purpose, null, "failed", exception)
            );
            throw exception;
        }
    }

    private void publishLifecycleEvent(
            GpuRuntimeLifecycleEventKind kind,
            String message,
            Map<String, String> fields
    ) {
        LinkedHashMap<String, String> eventFields = new LinkedHashMap<>();
        GpuRuntimeArtifactProperties.putPortable(eventFields, "runtime.backend", "target", backendTarget().name());
        GpuRuntimeArtifactProperties.putPortable(
                eventFields,
                "runtime.backend",
                "name",
                cacheMode == CacheMode.SHARED ? "OpenCL (shared cache)" : "OpenCL"
        );
        if (fields != null) {
            eventFields.putAll(fields);
        }
        lifecycleEventBus.publish(new GpuRuntimeLifecycleEvent(
                kind,
                backendTarget(),
                "runtime",
                "off",
                message,
                eventFields
        ));
    }

    private void publishLifecycleEvent(
            GpuRuntimeLifecycleEventKind kind,
            GpuRuntimeCompileRequest compileRequest,
            String message,
            Map<String, String> fields
    ) {
        if (compileRequest == null) {
            publishLifecycleEvent(kind, (GpuKernelDescriptor) null, (GpuRuntimeCompileOptions) null, message, fields);
            return;
        }
        lifecycleEventBus.publish(new GpuRuntimeLifecycleEvent(
                kind,
                backendTarget(),
                compileRequest.descriptor().kernelResource(),
                compileRequest.options().optimizationProfile(),
                message,
                mergeRuntimeFields(GpuRuntimeLifecycleFields.compileRequestFields(compileRequest), fields)
        ));
    }

    private void publishLifecycleEvent(
            GpuRuntimeLifecycleEventKind kind,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            String message,
            Map<String, String> fields
    ) {
        GpuBackendModuleArtifact moduleArtifact = artifactSnapshot == null
                ? GpuBackendModuleArtifact.unknown()
                : artifactSnapshot.backendModuleArtifact();
        GpuRuntimeCompileProvenance provenance = artifactSnapshot == null
                ? GpuRuntimeCompileProvenance.unknown()
                : artifactSnapshot.compileProvenance();
        lifecycleEventBus.publish(new GpuRuntimeLifecycleEvent(
                kind,
                backendTarget(),
                normalizeLifecycleValue(moduleArtifact.resource(), "unknown"),
                provenance.optimizationProfile(),
                message,
                mergeRuntimeFields(GpuRuntimeLifecycleFields.artifactSnapshotFields(artifactSnapshot), fields)
        ));
    }

    private void publishLifecycleEvent(
            GpuRuntimeLifecycleEventKind kind,
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileOptions compileOptions,
            String message,
            Map<String, String> fields
    ) {
        publishLifecycleEvent(
                kind,
                descriptor,
                compileOptions == null ? "off" : compileOptions.optimizationProfile(),
                message,
                mergeRuntimeFields(GpuRuntimeLifecycleFields.descriptorCompileOptionsFields(descriptor, compileOptions), fields)
        );
    }

    private void publishLifecycleEvent(
            GpuRuntimeLifecycleEventKind kind,
            GpuKernelDescriptor descriptor,
            String optimizationProfile,
            String message,
            Map<String, String> fields
    ) {
        lifecycleEventBus.publish(new GpuRuntimeLifecycleEvent(
                kind,
                backendTarget(),
                descriptor == null ? "unknown" : descriptor.kernelResource(),
                optimizationProfile,
                message,
                mergeRuntimeFields(
                        GpuRuntimeLifecycleFields.descriptorFields(descriptor),
                        fieldsWithOptimizationProfile(fields, optimizationProfile)
                )
        ));
    }

    private static Map<String, String> mergeRuntimeFields(
            LinkedHashMap<String, String> runtimeFields,
            Map<String, String> fields
    ) {
        LinkedHashMap<String, String> merged = runtimeFields == null ? new LinkedHashMap<>() : runtimeFields;
        if (fields != null) {
            merged.putAll(fields);
        }
        return merged;
    }

    private static Map<String, String> fieldsWithOptimizationProfile(
            Map<String, String> fields,
            String optimizationProfile
    ) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        GpuRuntimeArtifactProperties.putPortable(
                merged,
                "runtime.compile",
                "optimizationProfile",
                normalizeLifecycleValue(optimizationProfile, "off")
        );
        if (fields != null) {
            merged.putAll(fields);
        }
        return merged;
    }

    private static Map<String, String> irGpuLoadFields(
            String loadRole,
            Optional<IrGpuArtifact> artifact,
            String status,
            RuntimeException failure
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", normalizeLifecycleValue(status, "unknown"));
        fields.put("loadRole", normalizeLifecycleValue(loadRole, "unknown"));
        fields.put("irgpu.present", Boolean.toString(artifact != null && artifact.isPresent()));
        fields.put("irgpu.identity", IrGpuArtifactIdentity.stableIdentity(artifact));
        GpuRuntimeLifecycleFields.putStatus(fields, status);
        putRuntimeIrGpuField(fields, "present", artifact != null && artifact.isPresent());
        putRuntimeIrGpuField(fields, "identity", IrGpuArtifactIdentity.stableIdentity(artifact));
        GpuRuntimeLifecycleFields.putFailureFields(fields, failure);
        putFailureFields(fields, failure);
        return fields;
    }

    private static Map<String, String> validationFields(
            String validationStage,
            String status,
            RuntimeException failure
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", normalizeLifecycleValue(status, "unknown"));
        fields.put("validation.stage", normalizeLifecycleValue(validationStage, "unknown"));
        GpuRuntimeLifecycleFields.putStatus(fields, status);
        GpuRuntimeLifecycleFields.putFailureFields(fields, failure);
        putFailureFields(fields, failure);
        return fields;
    }

    private static Map<String, String> descriptorSelectionFields(
            GpuKernelInvocation invocation,
            Optional<GpuRuntimeMethodVariantSelection> selection,
            GpuKernelDescriptor selectedDescriptor,
            String status,
            RuntimeException failure
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        Optional<GpuRuntimeMethodVariantSelection> resolvedSelection = selection == null ? Optional.empty() : selection;
        fields.put("status", normalizeLifecycleValue(status, "unknown"));
        fields.put("fallbackDescriptor.count", Integer.toString(invocation.fallbackDescriptors().size()));
        fields.put("descriptorVariant.count", Integer.toString(invocation.descriptorVariants().size()));
        fields.put("methodVariant.selected", Boolean.toString(resolvedSelection.isPresent()));
        fields.put("methodVariant.groupId", resolvedSelection.map(GpuRuntimeMethodVariantSelection::groupId).orElse("none"));
        fields.put("methodVariant.selectedVariantId", resolvedSelection
                .map(GpuRuntimeMethodVariantSelection::selectedVariantId)
                .orElse("primary"));
        if (selectedDescriptor != null) {
            fields.put("selected.kernelName", selectedDescriptor.kernelName());
            fields.put("selected.kernelResource", selectedDescriptor.kernelResource());
            fields.put("selected.irgpuResource", selectedDescriptor.irGpuResource());
        }
        resolvedSelection.ifPresent(value -> fields.putAll(value.artifactFields("methodVariantSelection")));
        GpuRuntimeLifecycleFields.putStatus(fields, status);
        GpuRuntimeLifecycleFields.putFailureFields(fields, failure);
        putFailureFields(fields, failure);
        return fields;
    }

    private Map<String, String> optimizerDiscoveryFields(
            GpuRuntimeCompileRequest compileRequest,
            String status,
            boolean legacyHook,
            RuntimeException failure
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", normalizeLifecycleValue(status, "unknown"));
        fields.put("optimizer.legacyHook", Boolean.toString(legacyHook));
        fields.put("optimizer.count", Integer.toString(legacyHook ? 1 : irOptimizerRegistry.optimizerCount()));
        fields.put("optimizer.pipelineVersion", optimizerPipelineVersion());
        fields.put("irgpu.present", Boolean.toString(compileRequest.irGpuArtifact().isPresent()));
        fields.put("irgpu.identity", IrGpuArtifactIdentity.stableIdentity(compileRequest.irGpuArtifact()));
        GpuRuntimeLifecycleFields.putStatus(fields, status);
        putRuntimeIrGpuField(fields, "present", compileRequest.irGpuArtifact().isPresent());
        putRuntimeIrGpuField(fields, "identity", IrGpuArtifactIdentity.stableIdentity(compileRequest.irGpuArtifact()));
        GpuRuntimeLifecycleFields.putFailureFields(fields, failure);
        putFailureFields(fields, failure);
        return fields;
    }

    private static Map<String, String> optimizerPassFields(
            GpuRuntimeIrOptimizationReport optimizationReport,
            String status,
            RuntimeException failure
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", normalizeLifecycleValue(status, "unknown"));
        if (optimizationReport != null) {
            fields.put("pass.count", Integer.toString(optimizationReport.passReports().size()));
            fields.put("pass.applied.count", Long.toString(optimizationReport.passReports().stream()
                    .filter(report -> report.outcome() == GpuRuntimeIrOptimizationOutcome.APPLIED)
                    .count()));
            fields.put("pass.skipped.count", Long.toString(optimizationReport.passReports().stream()
                    .filter(report -> report.outcome() == GpuRuntimeIrOptimizationOutcome.SKIPPED)
                    .count()));
            fields.put("pass.rolledBack.count", Long.toString(optimizationReport.passReports().stream()
                    .filter(report -> report.outcome() == GpuRuntimeIrOptimizationOutcome.ROLLED_BACK)
                    .count()));
            fields.put("pass.failed.count", Long.toString(optimizationReport.passReports().stream()
                    .filter(report -> report.outcome() == GpuRuntimeIrOptimizationOutcome.FAILED)
                    .count()));
            fields.put("rollback.required", Boolean.toString(optimizationReport.requiresRollback()));
            fields.put("artifact.present", Boolean.toString(optimizationReport.artifact().isPresent()));
            fields.put("candidateArtifact.present", Boolean.toString(optimizationReport.candidateArtifact().isPresent()));
            fields.put("strategy.name", optimizationReport.strategyDecision().strategyName());
            fields.put("strategy.deviceFamily", optimizationReport.strategyDecision().deviceFamily());
            fields.put("strategy.selectedProfile", optimizationReport.strategyDecision().selectedProfile());
            fields.put("strategy.advisoryOnly", Boolean.toString(optimizationReport.strategyDecision().advisoryOnly()));
        }
        GpuRuntimeLifecycleFields.putStatus(fields, status);
        GpuRuntimeLifecycleFields.putFailureFields(fields, failure);
        putFailureFields(fields, failure);
        return fields;
    }

    private Map<String, String> backendLowererFields(
            GpuRuntimeCompileRequest compileRequest,
            String purpose,
            GpuBackendModuleArtifact moduleArtifact,
            String status,
            RuntimeException failure
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", normalizeLifecycleValue(status, "unknown"));
        fields.put("lowerer.purpose", normalizeLifecycleValue(purpose, "unknown"));
        GpuRuntimeLifecycleFields.putStatus(fields, status);
        GpuRuntimeLifecycleFields.putAllMissing(fields, GpuRuntimeLifecycleFields.moduleArtifactFields(moduleArtifact));
        GpuRuntimeLifecycleFields.putFailureFields(fields, failure);
        putModuleArtifactFields(fields, moduleArtifact);
        putFailureFields(fields, failure);
        if (moduleArtifact != null) {
            GpuBackendLoweringResult loweringResult = backendLoweringResult(compileRequest, moduleArtifact);
            GpuRuntimeLifecycleFields.putAllMissing(
                    fields,
                    loweringResult.artifactFields("runtime.backend.lowering")
            );
            GpuRuntimeLifecycleFields.putAllMissing(
                    fields,
                    backendHookRegistry.observeLowering(
                            compileRequest,
                            loweringResult,
                            "runtime.backend.hookExecution.lowering"
                    )
            );
        }
        return fields;
    }

    private static Map<String, String> sourceSelectionFields(
            GpuBackendModuleArtifact moduleArtifact,
            GpuBackendSourcePromotionGate sourcePromotionGate,
            GpuBackendSourceSwitchingDecision sourceSwitchingDecision,
            GpuRuntimeIrSelection runtimeIrSelection
    ) {
        LinkedHashMap<String, String> fields = GpuRuntimeLifecycleFields.backendSourceSelectionFields(
                moduleArtifact,
                sourceSwitchingDecision
        );
        fields.put("status", sourceSwitchingDecision.status());
        fields.put("decision", sourceSwitchingDecision.decision());
        fields.put("sourceSelection", sourceSwitchingDecision.sourceSelection());
        fields.put("irgpuSourceRequested", Boolean.toString(sourceSwitchingDecision.irGpuSourceRequested()));
        fields.put("sourceReady", Boolean.toString(sourceSwitchingDecision.sourceReady()));
        fields.put("sourceReconstructed", Boolean.toString(sourceSwitchingDecision.sourceReconstructed()));
        fields.put("sourceAvailable", Boolean.toString(sourceSwitchingDecision.sourceAvailable()));
        fields.put("sourceParityChecked", Boolean.toString(sourceSwitchingDecision.sourceParityChecked()));
        fields.put("sourceParityMatched", Boolean.toString(sourceSwitchingDecision.sourceParityMatched()));
        fields.put("sourcePromotionStatus", sourceSwitchingDecision.sourcePromotionStatus());
        fields.put("sourcePromotionReviewReady", Boolean.toString(sourceSwitchingDecision.sourcePromotionReviewReady()));
        fields.put("selectedSource", sourcePromotionGate.selectedSource());
        fields.put("runtimeLoadMode", sourceSwitchingDecision.runtimeLoadMode());
        fields.put("runtimeIr.selectedStage", runtimeIrSelection.selectedStage());
        fields.put("runtimeIr.transformed", Boolean.toString(runtimeIrSelection.transformed()));
        fields.put("runtimeIr.optimizedRejected", Boolean.toString(runtimeIrSelection.optimizedRejected()));
        GpuRuntimeLifecycleFields.putStatus(fields, sourceSwitchingDecision.status());
        GpuRuntimeLifecycleFields.putAllMissing(fields, GpuRuntimeLifecycleFields.runtimeIrSelectionFields(runtimeIrSelection));
        putModuleArtifactFields(fields, moduleArtifact);
        return fields;
    }

    private static Map<String, String> fallbackOrRollbackSelectionFields(
            GpuRuntimeIrSelection runtimeIrSelection,
            GpuRuntimeFallbackEvidence fallbackEvidence
    ) {
        LinkedHashMap<String, String> fields = GpuRuntimeLifecycleFields.runtimeIrSelectionFields(runtimeIrSelection);
        fields.putAll(GpuRuntimeLifecycleFields.fallbackEvidenceFields(fallbackEvidence));
        String selectedStage = runtimeIrSelection == null ? "missing" : runtimeIrSelection.selectedStage();
        String status = "missing".equals(selectedStage) ? "missing" : "selected-" + selectedStage;
        fields.put("status", status);
        fields.put("decision", runtimeIrSelection == null
                ? GpuRuntimeCompileProvenance.NO_FALLBACK
                : runtimeIrSelection.fallbackDecision());
        GpuRuntimeLifecycleFields.putStatus(fields, status);
        return fields;
    }

    private Map<String, String> runtimeStateFields(String status, RuntimeException failure) {
        GpuRuntimeBackendStateSummary stateSummary = backendRuntimeStateSummary();
        LinkedHashMap<String, String> fields = GpuRuntimeLifecycleFields.runtimeStateEventFields(
                stateSummary,
                status,
                failure
        );
        fields.put("status", normalizeLifecycleValue(status, "unknown"));
        fields.put("cacheMode", stateSummary.cacheMode());
        fields.put("compiledKernel.count", Long.toString(stateSummary.compiledKernelCount()));
        fields.put("nativeBuffer.count", Long.toString(stateSummary.nativeBufferCount()));
        fields.put("invocation.count", Long.toString(stateSummary.invocationCount()));
        fields.put("compile.count", Long.toString(stateSummary.compileCount()));
        putFailureFields(fields, failure);
        return fields;
    }

    private Map<String, String> backendCompilationFields(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            String status,
            String cacheKey,
            RuntimeException failure
    ) {
        GpuRuntimeBackendCompilationSummary compilationSummary = GpuRuntimeBackendCompilationSummary.from(
                moduleArtifact,
                artifactSnapshot,
                cacheKey
        );
        LinkedHashMap<String, String> fields = GpuRuntimeLifecycleFields.backendCompilationFields(
                compileRequest,
                moduleArtifact,
                artifactSnapshot,
                backendRuntimeStateFields(),
                compilationSummary,
                status,
                cacheKey,
                failure
        );
        GpuBackendCompilationResult compilationResult = backendCompilationResult(
                compileRequest,
                moduleArtifact,
                artifactSnapshot,
                status,
                cacheKey,
                failure
        );
        GpuRuntimeLifecycleFields.putAllMissing(
                fields,
                compilationResult.artifactFields("runtime.backend.compilation")
        );
        GpuRuntimeLifecycleFields.putAllMissing(
                fields,
                backendHookRegistry.observeCompilation(
                        compileRequest,
                        compilationResult,
                        "runtime.backend.hookExecution.compilation"
                )
        );
        fields.put("status", normalizeLifecycleValue(status, "unknown"));
        if (cacheKey != null && !cacheKey.isBlank()) {
            fields.put("cacheKey", cacheKey);
        }
        putModuleArtifactFields(fields, moduleArtifact);
        if (artifactSnapshot != null) {
            fields.put("runtimeIr.selectedStage", artifactSnapshot.runtimeIrSelection().selectedStage());
            fields.put("runtimeIr.transformed", Boolean.toString(artifactSnapshot.runtimeIrSelection().transformed()));
            fields.put("runtimeIr.optimizedRejected", Boolean.toString(artifactSnapshot.runtimeIrSelection().optimizedRejected()));
            fields.put("runtimeIr.fallbackDecision", artifactSnapshot.runtimeIrSelection().fallbackDecision());
            fields.put("compileLog.present", Boolean.toString(!artifactSnapshot.compileLog().isBlank()));
            fields.put("binaryArtifact.count", Integer.toString(artifactSnapshot.binaryArtifacts().size()));
        }
        putFailureFields(fields, failure);
        GpuRuntimeLifecycleFields.putAllMissing(
                fields,
                backendHookRegistry.contributeArtifactFields(
                        compilationResult.moduleArtifact().backendTarget(),
                        compileRequest,
                        fields,
                        "runtime.backend.hookExecution.compilationArtifact"
                )
        );
        return fields;
    }

    private GpuBackendCompilationResult backendCompilationResult(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            String status,
            String cacheKey,
            RuntimeException failure
    ) {
        GpuBackendModuleArtifact module = stageModuleArtifact(moduleArtifact, artifactSnapshot);
        GpuBackendLoweringResult loweringResult = backendLoweringResult(compileRequest, module);
        GpuRuntimeBackendCompilationSummary compilationSummary = GpuRuntimeBackendCompilationSummary.from(
                moduleArtifact,
                artifactSnapshot,
                cacheKey
        );
        return new GpuBackendCompilationResult(
                backendStageResult(
                        GpuBackendPipelineStage.COMPILE,
                        stageBackendTarget(compileRequest, module, artifactSnapshot),
                        status,
                        "OpenCL backend compilation",
                        failure
                ),
                loweringResult,
                compilationSummary,
                cacheKey
        );
    }

    private GpuBackendLoweringResult backendLoweringResult(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact
    ) {
        GpuBackendTarget target = stageBackendTarget(compileRequest, moduleArtifact, null);
        GpuBackendSourceSelectionPlan sourceSelectionPlan = new GpuBackendSourceSelectionPlan(
                target,
                "irgpu-backend-neutral-source".equals(moduleArtifact.sourceOrigin()),
                moduleArtifact.sourceOrigin(),
                moduleArtifact.format(),
                moduleArtifact.runtimeLoadMode(),
                List.of(),
                moduleArtifact.sourceAvailable()
                        ? List.of("OpenCL compilation received a pre-lowered backend module artifact")
                        : List.of("OpenCL compilation has not recorded a lowered backend module artifact")
        );
        if (moduleArtifact.sourceAvailable() || moduleArtifact.binaryAvailable()) {
            return GpuBackendLoweringResult.succeeded(
                    moduleArtifact,
                    sourceSelectionPlan,
                    List.of("OpenCL backend module artifact is available for compilation")
            );
        }
        return new GpuBackendLoweringResult(
                GpuBackendStageResult.notStarted(GpuBackendPipelineStage.LOWER, target),
                sourceSelectionPlan,
                moduleArtifact
        );
    }

    private Map<String, String> invocationFields(
            OpenClPreparedExecution execution,
            GpuExecutionConfig executionConfig,
            String status,
            RuntimeException failure
    ) {
        GpuRuntimeInvocationBindingSummary bindingSummary = execution.bindingSummary();
        LinkedHashMap<String, String> fields = GpuRuntimeLifecycleFields.invocationFields(
                execution.compiledKernel().artifactSnapshot(),
                backendRuntimeStateFields(),
                executionConfig,
                bindingSummary,
                status,
                execution.compiledKernel().cacheKey(),
                failure
        );
        GpuBackendInvocationResult invocationResult = backendInvocationResult(execution, executionConfig, status, failure);
        GpuRuntimeLifecycleFields.putAllMissing(
                fields,
                invocationResult.artifactFields("runtime.backend.invoke")
        );
        GpuRuntimeLifecycleFields.putAllMissing(
                fields,
                backendHookRegistry.observeInvocation(
                        invocationResult.preparationResult().compilationResult().moduleArtifact().backendTarget(),
                        null,
                        invocationResult,
                        "runtime.backend.hookExecution.invocation"
                )
        );
        fields.put("status", normalizeLifecycleValue(status, "unknown"));
        fields.put("cacheKey", execution.compiledKernel().cacheKey());
        fields.put("bufferBinding.count", Integer.toString(bindingSummary.bufferBindingCount()));
        fields.put("localBinding.count", Integer.toString(bindingSummary.localBindingCount()));
        fields.put("scalarBinding.count", Integer.toString(bindingSummary.scalarBindingCount()));
        fields.put("argumentBinding.count", Integer.toString(bindingSummary.argumentBindingCount()));
        if (executionConfig != null) {
            fields.put("work.dimensions", Integer.toString(executionConfig.dimensions()));
            fields.put("work.globalX", Long.toString(executionConfig.globalX()));
            fields.put("work.globalY", Long.toString(executionConfig.globalY()));
            fields.put("work.globalZ", Long.toString(executionConfig.globalZ()));
            fields.put("work.localX", Long.toString(executionConfig.localX()));
            fields.put("work.localY", Long.toString(executionConfig.localY()));
            fields.put("work.localZ", Long.toString(executionConfig.localZ()));
        }
        putFailureFields(fields, failure);
        GpuRuntimeLifecycleFields.putAllMissing(
                fields,
                backendHookRegistry.contributeArtifactFields(
                        invocationResult.preparationResult().compilationResult().moduleArtifact().backendTarget(),
                        null,
                        fields,
                        "runtime.backend.hookExecution.invocationArtifact"
                )
        );
        return fields;
    }

    private GpuBackendInvocationResult backendInvocationResult(
            OpenClPreparedExecution execution,
            GpuExecutionConfig executionConfig,
            String status,
            RuntimeException failure
    ) {
        GpuBackendPreparationResult preparationResult = backendPreparationResult(execution);
        int readbackRequiredCount = readbackRequiredCount(execution);
        int readbackCompletedCount = "succeeded".equals(status) ? readbackRequiredCount : 0;
        return new GpuBackendInvocationResult(
                backendStageResult(
                        GpuBackendPipelineStage.INVOKE,
                        preparationResult.compilationResult().stageResult().backendTarget(),
                        status,
                        "OpenCL kernel invocation",
                        failure
                ),
                preparationResult,
                executionConfig,
                execution.bindingSummary(),
                readbackRequiredCount,
                readbackCompletedCount
        );
    }

    private GpuBackendPreparationResult backendPreparationResult(OpenClPreparedExecution execution) {
        OpenClCompiledKernel compiledKernel = execution.compiledKernel();
        GpuRuntimeCompileArtifactSnapshot artifactSnapshot = compiledKernel.artifactSnapshot();
        GpuBackendCompilationResult compilationResult = backendCompilationResult(
                null,
                artifactSnapshot.backendModuleArtifact(),
                artifactSnapshot,
                "succeeded",
                compiledKernel.cacheKey(),
                null
        );
        return GpuBackendPreparationResult.prepared(
                compilationResult,
                "opencl-kernel",
                execution.bindingSummary(),
                List.of("OpenCL kernel and argument bindings are prepared")
        );
    }

    private static int readbackRequiredCount(OpenClPreparedExecution execution) {
        if (execution.bufferBindings() == null) {
            return 0;
        }
        return (int) execution.bufferBindings().stream()
                .filter(binding -> binding != null && binding.binding() != null && binding.binding().readbackRequired())
                .count();
    }

    private static GpuBackendStageResult backendStageResult(
            GpuBackendPipelineStage stage,
            GpuBackendTarget backendTarget,
            String status,
            String summary,
            RuntimeException failure
    ) {
        String normalizedStatus = normalizeLifecycleValue(status, "started");
        String normalizedSummary = normalizeLifecycleValue(summary, stage.key());
        if ("succeeded".equals(normalizedStatus)) {
            return GpuBackendStageResult.succeeded(
                    stage,
                    backendTarget,
                    normalizedSummary + " succeeded",
                    List.of()
            );
        }
        if ("failed".equals(normalizedStatus)) {
            return GpuBackendStageResult.failed(
                    stage,
                    backendTarget,
                    normalizedSummary + " failed",
                    failure,
                    List.of()
            );
        }
        return GpuBackendStageResult.notStarted(stage, backendTarget);
    }

    private static GpuBackendModuleArtifact stageModuleArtifact(
            GpuBackendModuleArtifact moduleArtifact,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot
    ) {
        if (moduleArtifact != null) {
            return moduleArtifact;
        }
        if (artifactSnapshot != null) {
            return artifactSnapshot.backendModuleArtifact();
        }
        return GpuBackendModuleArtifact.unknown();
    }

    private static GpuBackendTarget stageBackendTarget(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot
    ) {
        if (moduleArtifact != null && moduleArtifact.backendTarget() != GpuBackendTarget.UNKNOWN) {
            return moduleArtifact.backendTarget();
        }
        if (artifactSnapshot != null && artifactSnapshot.backendModuleArtifact().backendTarget() != GpuBackendTarget.UNKNOWN) {
            return artifactSnapshot.backendModuleArtifact().backendTarget();
        }
        if (compileRequest != null && compileRequest.options() != null) {
            return compileRequest.options().backendTarget();
        }
        return GpuBackendTarget.OPENCL;
    }

    private LinkedHashMap<String, String> backendRuntimeStateFields() {
        return GpuRuntimeLifecycleFields.backendRuntimeStateFields(backendRuntimeStateSummary());
    }

    private GpuRuntimeBackendStateSummary backendRuntimeStateSummary() {
        return new GpuRuntimeBackendStateSummary(
                cacheMode.name(),
                compiledKernelCache().size(),
                nativeBuffers.size(),
                invocationCount.get(),
                compileCount.get(),
                compileCacheHitCount.get(),
                sessionCreationCount.get(),
                deviceBufferCreationCount.get()
        );
    }

    private Map<String, String> artifactDumpFields(
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            GpuRuntimeArtifactDumpSummary dumpSummary,
            String status,
            RuntimeException failure
    ) {
        LinkedHashMap<String, String> fields = GpuRuntimeLifecycleFields.artifactDumpFields(
                artifactSnapshot,
                dumpSummary,
                status,
                failure
        );
        fields.put("status", normalizeLifecycleValue(status, "unknown"));
        if (dumpSummary != null) {
            fields.put("directory.count", Integer.toString(dumpSummary.directoryCount()));
        }
        if (artifactSnapshot != null) {
            putModuleArtifactFields(fields, artifactSnapshot.backendModuleArtifact());
            fields.put("runtimeIr.selectedStage", artifactSnapshot.runtimeIrSelection().selectedStage());
            fields.put("runtimeIr.transformed", Boolean.toString(artifactSnapshot.runtimeIrSelection().transformed()));
            fields.put("compileLog.present", Boolean.toString(!artifactSnapshot.compileLog().isBlank()));
        }
        GpuBackendTarget backendTarget = artifactSnapshot == null
                ? GpuBackendTarget.OPENCL
                : artifactSnapshot.backendModuleArtifact().backendTarget();
        GpuRuntimeLifecycleFields.putAllMissing(
                fields,
                backendHookRegistry.contributeArtifactFields(
                        backendTarget,
                        null,
                        fields,
                        "runtime.backend.hookExecution.artifactDump"
                )
        );
        putFailureFields(fields, failure);
        return fields;
    }

    private static void putModuleArtifactFields(
            LinkedHashMap<String, String> fields,
            GpuBackendModuleArtifact moduleArtifact
    ) {
        if (moduleArtifact == null) {
            return;
        }
        fields.put("module.kind", moduleArtifact.kind());
        fields.put("module.format", moduleArtifact.format());
        fields.put("module.resource", normalizeLifecycleValue(moduleArtifact.resource(), "unknown"));
        fields.put("module.artifactVersion", moduleArtifact.artifactVersion());
        fields.put("module.lowererVersion", moduleArtifact.lowererVersion());
        fields.put("module.sourceOrigin", moduleArtifact.sourceOrigin());
        fields.put("module.runtimeLoadMode", moduleArtifact.runtimeLoadMode());
        fields.put("module.sourceAvailable", Boolean.toString(moduleArtifact.sourceAvailable()));
        fields.put("module.binaryAvailable", Boolean.toString(moduleArtifact.binaryAvailable()));
    }

    private static void putFailureFields(LinkedHashMap<String, String> fields, RuntimeException failure) {
        if (failure == null) {
            return;
        }
        fields.put("failure.type", failure.getClass().getName());
        fields.put("failure.message", normalizeLifecycleValue(failure.getMessage(), ""));
    }

    private static void putRuntimeIrGpuField(LinkedHashMap<String, String> fields, String key, Object value) {
        GpuRuntimeArtifactProperties.putPortable(fields, "runtime.irgpu", key, value);
    }

    private static String normalizeLifecycleValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Optional<GpuRuntimeMethodVariantSelection> selectMethodVariant(
            GpuKernelInvocation invocation,
            GpuRuntimeCompileOptions compileOptions
    ) {
        if (invocation.fallbackDescriptors().isEmpty()) {
            return Optional.empty();
        }
        List<GpuRuntimeDeviceProfile> profiles = runtimeDeviceSelection()
                .flatMap(GpuRuntimeDeviceSelection::selectedDevice)
                .map(List::of)
                .orElseGet(() -> OpenClRuntimeSession.discoverDeviceProfiles(
                        devicePolicyRegistry,
                        compileOptions
                ));
        return Optional.of(GpuRuntimeMethodVariantSelector.select(
                invocation.descriptor(),
                invocation.fallbackDescriptors(),
                invocation.artifactClassLoader(),
                compileOptions,
                profiles,
                devicePolicyRegistry
        ));
    }

    private static boolean sameSelectedIr(
            GpuRuntimeCompileRequest optimizedCompileRequest,
            GpuRuntimeCompileRequest selectedCompileRequest
    ) {
        return IrGpuArtifactIdentity.stableIdentity(optimizedCompileRequest.irGpuArtifact())
                .equals(IrGpuArtifactIdentity.stableIdentity(selectedCompileRequest.irGpuArtifact()));
    }

    /**
     * Returns a snapshot of lightweight runtime counters for this backend instance.
     */
    public final OpenClRuntimeStatistics statistics() {
        return new OpenClRuntimeStatistics(
                invocationCount.get(),
                compileCount.get(),
                compileCacheHitCount.get(),
                sessionCreationCount.get(),
                deviceBufferCreationCount.get()
        );
    }

    /**
     * Returns a vendor-validation snapshot suitable for CI artifacts and local device comparisons.
     */
    public final OpenClValidationReport validationReport() {
        OpenClValidationDeviceInfo deviceInfo = runtimeValidationDeviceInfo();
        return new OpenClValidationReport(
                java.time.Instant.now(),
                cacheMode == CacheMode.SHARED ? "OpenCL (shared cache)" : "OpenCL",
                cacheMode.name(),
                deviceInfo.deviceLabel(),
                deviceInfo.vendor(),
                deviceInfo.driverVersion(),
                deviceInfo.deviceVersion(),
                deviceInfo.platformName(),
                deviceInfo.platformVersion(),
                deviceInfo.supportsDoublePrecision(),
                deviceInfo.supportsImages(),
                deviceInfo.supportsImage3dWrites(),
                promotionArtifactSupport(),
                deviceInfo.localMemoryBytes(),
                deviceInfo.maxWorkGroupSize(),
                statistics()
        );
    }

    /**
     * Resets lightweight runtime counters collected by this backend instance.
     *
     * <p>This does not clear compiled kernels, native buffers, or shared caches. It only resets diagnostic counters so
     * the next measurement window starts from zero.
     */
    public final void resetStatistics() {
        invocationCount.set(0L);
        compileCount.set(0L);
        compileCacheHitCount.set(0L);
        sessionCreationCount.set(0L);
        deviceBufferCreationCount.set(0L);
    }

    public final Image2DReadOnly createReadOnlyRgbaFloatImage(int width, int height, float[] rgba) {
        return createReadOnlyRgbaFloatImageInternal(width, height, rgba);
    }

    public final Image2DWriteOnly createWriteOnlyRgbaFloatImage(int width, int height) {
        return createWriteOnlyRgbaFloatImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRFloatImage(int width, int height, float[] values) {
        return createReadOnlyRFloatImageInternal(width, height, values);
    }

    public final Image2DWriteOnly createWriteOnlyRFloatImage(int width, int height) {
        return createWriteOnlyRFloatImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRgFloatImage(int width, int height, float[] values) {
        return createReadOnlyRgFloatImageInternal(width, height, values);
    }

    public final Image2DWriteOnly createWriteOnlyRgFloatImage(int width, int height) {
        return createWriteOnlyRgFloatImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyDepthImage(int width, int height, float[] values) {
        return createReadOnlyDepthImageInternal(width, height, values);
    }

    public final Image2DWriteOnly createWriteOnlyDepthImage(int width, int height) {
        return createWriteOnlyDepthImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRIntImage(int width, int height, int[] values) {
        return createReadOnlyRIntImageInternal(width, height, values);
    }

    public final Image2DWriteOnly createWriteOnlyRIntImage(int width, int height) {
        return createWriteOnlyRIntImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRgIntImage(int width, int height, int[] values) {
        return createReadOnlyRgIntImageInternal(width, height, values);
    }

    public final Image2DWriteOnly createWriteOnlyRgIntImage(int width, int height) {
        return createWriteOnlyRgIntImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRgbaIntImage(int width, int height, int[] rgba) {
        return createReadOnlyRgbaIntImageInternal(width, height, rgba);
    }

    public final Image2DWriteOnly createWriteOnlyRgbaIntImage(int width, int height) {
        return createWriteOnlyRgbaIntImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRgbaUIntImage(int width, int height, int[] rgba) {
        return createReadOnlyRgbaUIntImageInternal(width, height, rgba);
    }

    public final Image2DWriteOnly createWriteOnlyRgbaUIntImage(int width, int height) {
        return createWriteOnlyRgbaUIntImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRUIntImage(int width, int height, int[] values) {
        return createReadOnlyRUIntImageInternal(width, height, values);
    }

    public final Image2DWriteOnly createWriteOnlyRUIntImage(int width, int height) {
        return createWriteOnlyRUIntImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRgUIntImage(int width, int height, int[] values) {
        return createReadOnlyRgUIntImageInternal(width, height, values);
    }

    public final Image2DWriteOnly createWriteOnlyRgUIntImage(int width, int height) {
        return createWriteOnlyRgUIntImageInternal(width, height);
    }

    public final Image2DReadOnly createReadOnlyRgba8Image(int width, int height, byte[] rgba) {
        return createReadOnlyRgba8ImageInternal(width, height, rgba);
    }

    public final Image2DWriteOnly createWriteOnlyRgba8Image(int width, int height) {
        return createWriteOnlyRgba8ImageInternal(width, height);
    }

    public final Image2DMipmappedReadOnly createReadOnlyRgbaFloatImageMipmapped(int width, int height, int mipLevels, float[] rgba) {
        return createReadOnlyRgbaFloatImageMipmappedInternal(width, height, mipLevels, rgba);
    }

    public final Image2DMipmappedWriteOnly createWriteOnlyRgbaFloatImageMipmapped(int width, int height, int mipLevels) {
        return createWriteOnlyRgbaFloatImageMipmappedInternal(width, height, mipLevels);
    }

    public final Image2DMipmappedReadOnly createReadOnlyRgba8ImageMipmapped(int width, int height, int mipLevels, byte[] rgba) {
        return createReadOnlyRgba8ImageMipmappedInternal(width, height, mipLevels, rgba);
    }

    public final Image2DMipmappedWriteOnly createWriteOnlyRgba8ImageMipmapped(int width, int height, int mipLevels) {
        return createWriteOnlyRgba8ImageMipmappedInternal(width, height, mipLevels);
    }

    public final Image2DMipmappedReadOnly createReadOnlyRgbaIntImageMipmapped(int width, int height, int mipLevels, int[] rgba) {
        return createReadOnlyRgbaIntImageMipmappedInternal(width, height, mipLevels, rgba);
    }

    public final Image2DMipmappedWriteOnly createWriteOnlyRgbaIntImageMipmapped(int width, int height, int mipLevels) {
        return createWriteOnlyRgbaIntImageMipmappedInternal(width, height, mipLevels);
    }

    public final Image2DMipmappedReadOnly createReadOnlyRgbaUIntImageMipmapped(int width, int height, int mipLevels, int[] rgba) {
        return createReadOnlyRgbaUIntImageMipmappedInternal(width, height, mipLevels, rgba);
    }

    public final Image2DMipmappedWriteOnly createWriteOnlyRgbaUIntImageMipmapped(int width, int height, int mipLevels) {
        return createWriteOnlyRgbaUIntImageMipmappedInternal(width, height, mipLevels);
    }

    public final Image1DReadOnly createReadOnlyRgbaFloatImage1D(int width, float[] rgba) {
        return createReadOnlyRgbaFloatImage1DInternal(width, rgba);
    }

    public final Image1DWriteOnly createWriteOnlyRgbaFloatImage1D(int width) {
        return createWriteOnlyRgbaFloatImage1DInternal(width);
    }

    public final Image1DReadOnly createReadOnlyRgbaUIntImage1D(int width, int[] rgba) {
        return createReadOnlyRgbaUIntImage1DInternal(width, rgba);
    }

    public final Image1DWriteOnly createWriteOnlyRgbaUIntImage1D(int width) {
        return createWriteOnlyRgbaUIntImage1DInternal(width);
    }

    public final Image1DReadOnly createReadOnlyRgbaIntImage1D(int width, int[] rgba) {
        return createReadOnlyRgbaIntImage1DInternal(width, rgba);
    }

    public final Image1DWriteOnly createWriteOnlyRgbaIntImage1D(int width) {
        return createWriteOnlyRgbaIntImage1DInternal(width);
    }

    public final Image1DArrayReadOnly createReadOnlyRgbaFloatImage1DArray(int width, int layers, float[] rgba) {
        return createReadOnlyRgbaFloatImage1DArrayInternal(width, layers, rgba);
    }

    public final Image1DArrayWriteOnly createWriteOnlyRgbaFloatImage1DArray(int width, int layers) {
        return createWriteOnlyRgbaFloatImage1DArrayInternal(width, layers);
    }

    public final Image1DArrayReadOnly createReadOnlyRgbaIntImage1DArray(int width, int layers, int[] rgba) {
        return createReadOnlyRgbaIntImage1DArrayInternal(width, layers, rgba);
    }

    public final Image1DArrayWriteOnly createWriteOnlyRgbaIntImage1DArray(int width, int layers) {
        return createWriteOnlyRgbaIntImage1DArrayInternal(width, layers);
    }

    public final Image1DArrayReadOnly createReadOnlyRgbaUIntImage1DArray(int width, int layers, int[] rgba) {
        return createReadOnlyRgbaUIntImage1DArrayInternal(width, layers, rgba);
    }

    public final Image1DArrayWriteOnly createWriteOnlyRgbaUIntImage1DArray(int width, int layers) {
        return createWriteOnlyRgbaUIntImage1DArrayInternal(width, layers);
    }

    public final Image1DBufferReadOnly createReadOnlyRgbaFloatImage1DBuffer(int width, float[] rgba) {
        return createReadOnlyRgbaFloatImage1DBufferInternal(width, rgba);
    }

    public final Image1DBufferWriteOnly createWriteOnlyRgbaFloatImage1DBuffer(int width) {
        return createWriteOnlyRgbaFloatImage1DBufferInternal(width);
    }

    public final Image1DBufferReadOnly createReadOnlyRgbaIntImage1DBuffer(int width, int[] rgba) {
        return createReadOnlyRgbaIntImage1DBufferInternal(width, rgba);
    }

    public final Image1DBufferWriteOnly createWriteOnlyRgbaIntImage1DBuffer(int width) {
        return createWriteOnlyRgbaIntImage1DBufferInternal(width);
    }

    public final Image1DBufferReadOnly createReadOnlyRgbaUIntImage1DBuffer(int width, int[] rgba) {
        return createReadOnlyRgbaUIntImage1DBufferInternal(width, rgba);
    }

    public final Image1DBufferWriteOnly createWriteOnlyRgbaUIntImage1DBuffer(int width) {
        return createWriteOnlyRgbaUIntImage1DBufferInternal(width);
    }

    public final Image2DArrayReadOnly createReadOnlyRgbaFloatImage2DArray(int width, int height, int layers, float[] rgba) {
        return createReadOnlyRgbaFloatImage2DArrayInternal(width, height, layers, rgba);
    }

    public final Image2DArrayWriteOnly createWriteOnlyRgbaFloatImage2DArray(int width, int height, int layers) {
        return createWriteOnlyRgbaFloatImage2DArrayInternal(width, height, layers);
    }

    public final Image2DArrayReadOnly createReadOnlyRgbaIntImage2DArray(int width, int height, int layers, int[] rgba) {
        return createReadOnlyRgbaIntImage2DArrayInternal(width, height, layers, rgba);
    }

    public final Image2DArrayWriteOnly createWriteOnlyRgbaIntImage2DArray(int width, int height, int layers) {
        return createWriteOnlyRgbaIntImage2DArrayInternal(width, height, layers);
    }

    public final Image2DArrayReadOnly createReadOnlyRgbaUIntImage2DArray(int width, int height, int layers, int[] rgba) {
        return createReadOnlyRgbaUIntImage2DArrayInternal(width, height, layers, rgba);
    }

    public final Image2DArrayWriteOnly createWriteOnlyRgbaUIntImage2DArray(int width, int height, int layers) {
        return createWriteOnlyRgbaUIntImage2DArrayInternal(width, height, layers);
    }

    public final Image3DReadOnly createReadOnlyRgbaFloatImage3D(int width, int height, int depth, float[] rgba) {
        return createReadOnlyRgbaFloatImage3DInternal(width, height, depth, rgba);
    }

    public final Image3DWriteOnly createWriteOnlyRgbaFloatImage3D(int width, int height, int depth) {
        return createWriteOnlyRgbaFloatImage3DInternal(width, height, depth);
    }

    public final Image3DReadOnly createReadOnlyRgbaIntImage3D(int width, int height, int depth, int[] rgba) {
        return createReadOnlyRgbaIntImage3DInternal(width, height, depth, rgba);
    }

    public final Image3DWriteOnly createWriteOnlyRgbaIntImage3D(int width, int height, int depth) {
        return createWriteOnlyRgbaIntImage3DInternal(width, height, depth);
    }

    public final Image3DReadOnly createReadOnlyRgbaUIntImage3D(int width, int height, int depth, int[] rgba) {
        return createReadOnlyRgbaUIntImage3DInternal(width, height, depth, rgba);
    }

    public final Image3DWriteOnly createWriteOnlyRgbaUIntImage3D(int width, int height, int depth) {
        return createWriteOnlyRgbaUIntImage3DInternal(width, height, depth);
    }

    public final float[] readRgbaFloatImage(Image2DReadOnly image) {
        return readRgbaFloatImageInternal(image);
    }

    public final float[] readRgbaFloatImage(Image2DWriteOnly image) {
        return readRgbaFloatImageInternal(image);
    }

    public final float[] readRFloatImage(Image2DReadOnly image) {
        return readRFloatImageInternal(image);
    }

    public final float[] readRFloatImage(Image2DWriteOnly image) {
        return readRFloatImageInternal(image);
    }

    public final float[] readRgFloatImage(Image2DReadOnly image) {
        return readRgFloatImageInternal(image);
    }

    public final float[] readRgFloatImage(Image2DWriteOnly image) {
        return readRgFloatImageInternal(image);
    }

    public final float[] readDepthImage(Image2DReadOnly image) {
        return readDepthImageInternal(image);
    }

    public final float[] readDepthImage(Image2DWriteOnly image) {
        return readDepthImageInternal(image);
    }

    public final int[] readRIntImage(Image2DReadOnly image) {
        return readRIntImageInternal(image);
    }

    public final int[] readRIntImage(Image2DWriteOnly image) {
        return readRIntImageInternal(image);
    }

    public final int[] readRgIntImage(Image2DReadOnly image) {
        return readRgIntImageInternal(image);
    }

    public final int[] readRgIntImage(Image2DWriteOnly image) {
        return readRgIntImageInternal(image);
    }

    public final int[] readRgbaIntImage(Image2DReadOnly image) {
        return readRgbaIntImageInternal(image);
    }

    public final int[] readRgbaIntImage(Image2DWriteOnly image) {
        return readRgbaIntImageInternal(image);
    }

    public final int[] readRgbaUIntImage(Image2DReadOnly image) {
        return readRgbaUIntImageInternal(image);
    }

    public final int[] readRgbaUIntImage(Image2DWriteOnly image) {
        return readRgbaUIntImageInternal(image);
    }

    public final int[] readRUIntImage(Image2DReadOnly image) {
        return readRUIntImageInternal(image);
    }

    public final int[] readRUIntImage(Image2DWriteOnly image) {
        return readRUIntImageInternal(image);
    }

    public final int[] readRgUIntImage(Image2DReadOnly image) {
        return readRgUIntImageInternal(image);
    }

    public final int[] readRgUIntImage(Image2DWriteOnly image) {
        return readRgUIntImageInternal(image);
    }

    public final byte[] readRgba8Image(Image2DReadOnly image) {
        return readRgba8ImageInternal(image);
    }

    public final byte[] readRgba8Image(Image2DWriteOnly image) {
        return readRgba8ImageInternal(image);
    }

    public final float[] readRgbaFloatImageMipmapped(Image2DMipmappedReadOnly image, int mipLevel) {
        return readRgbaFloatImageMipmappedInternal(image, mipLevel);
    }

    public final float[] readRgbaFloatImageMipmapped(Image2DMipmappedWriteOnly image, int mipLevel) {
        return readRgbaFloatImageMipmappedInternal(image, mipLevel);
    }

    public final byte[] readRgba8ImageMipmapped(Image2DMipmappedReadOnly image, int mipLevel) {
        return readRgba8ImageMipmappedInternal(image, mipLevel);
    }

    public final byte[] readRgba8ImageMipmapped(Image2DMipmappedWriteOnly image, int mipLevel) {
        return readRgba8ImageMipmappedInternal(image, mipLevel);
    }

    public final int[] readRgbaIntImageMipmapped(Image2DMipmappedReadOnly image, int mipLevel) {
        return readRgbaIntImageMipmappedInternal(image, mipLevel);
    }

    public final int[] readRgbaIntImageMipmapped(Image2DMipmappedWriteOnly image, int mipLevel) {
        return readRgbaIntImageMipmappedInternal(image, mipLevel);
    }

    public final int[] readRgbaUIntImageMipmapped(Image2DMipmappedReadOnly image, int mipLevel) {
        return readRgbaUIntImageMipmappedInternal(image, mipLevel);
    }

    public final int[] readRgbaUIntImageMipmapped(Image2DMipmappedWriteOnly image, int mipLevel) {
        return readRgbaUIntImageMipmappedInternal(image, mipLevel);
    }

    public final float[] readRgbaFloatImage1D(Image1DReadOnly image) {
        return readRgbaFloatImage1DInternal(image);
    }

    public final float[] readRgbaFloatImage1D(Image1DWriteOnly image) {
        return readRgbaFloatImage1DInternal(image);
    }

    public final int[] readRgbaUIntImage1D(Image1DReadOnly image) {
        return readRgbaUIntImage1DInternal(image);
    }

    public final int[] readRgbaUIntImage1D(Image1DWriteOnly image) {
        return readRgbaUIntImage1DInternal(image);
    }

    public final int[] readRgbaIntImage1D(Image1DReadOnly image) {
        return readRgbaIntImage1DInternal(image);
    }

    public final int[] readRgbaIntImage1D(Image1DWriteOnly image) {
        return readRgbaIntImage1DInternal(image);
    }

    public final float[] readRgbaFloatImage1DArray(Image1DArrayReadOnly image) {
        return readRgbaFloatImage1DArrayInternal(image);
    }

    public final float[] readRgbaFloatImage1DArray(Image1DArrayWriteOnly image) {
        return readRgbaFloatImage1DArrayInternal(image);
    }

    public final int[] readRgbaIntImage1DArray(Image1DArrayReadOnly image) {
        return readRgbaIntImage1DArrayInternal(image);
    }

    public final int[] readRgbaIntImage1DArray(Image1DArrayWriteOnly image) {
        return readRgbaIntImage1DArrayInternal(image);
    }

    public final int[] readRgbaUIntImage1DArray(Image1DArrayReadOnly image) {
        return readRgbaUIntImage1DArrayInternal(image);
    }

    public final int[] readRgbaUIntImage1DArray(Image1DArrayWriteOnly image) {
        return readRgbaUIntImage1DArrayInternal(image);
    }

    public final float[] readRgbaFloatImage1DBuffer(Image1DBufferReadOnly image) {
        return readRgbaFloatImage1DBufferInternal(image);
    }

    public final float[] readRgbaFloatImage1DBuffer(Image1DBufferWriteOnly image) {
        return readRgbaFloatImage1DBufferInternal(image);
    }

    public final int[] readRgbaIntImage1DBuffer(Image1DBufferReadOnly image) {
        return readRgbaIntImage1DBufferInternal(image);
    }

    public final int[] readRgbaIntImage1DBuffer(Image1DBufferWriteOnly image) {
        return readRgbaIntImage1DBufferInternal(image);
    }

    public final int[] readRgbaUIntImage1DBuffer(Image1DBufferReadOnly image) {
        return readRgbaUIntImage1DBufferInternal(image);
    }

    public final int[] readRgbaUIntImage1DBuffer(Image1DBufferWriteOnly image) {
        return readRgbaUIntImage1DBufferInternal(image);
    }

    public final float[] readRgbaFloatImage2DArray(Image2DArrayReadOnly image) {
        return readRgbaFloatImage2DArrayInternal(image);
    }

    public final float[] readRgbaFloatImage2DArray(Image2DArrayWriteOnly image) {
        return readRgbaFloatImage2DArrayInternal(image);
    }

    public final int[] readRgbaIntImage2DArray(Image2DArrayReadOnly image) {
        return readRgbaIntImage2DArrayInternal(image);
    }

    public final int[] readRgbaIntImage2DArray(Image2DArrayWriteOnly image) {
        return readRgbaIntImage2DArrayInternal(image);
    }

    public final int[] readRgbaUIntImage2DArray(Image2DArrayReadOnly image) {
        return readRgbaUIntImage2DArrayInternal(image);
    }

    public final int[] readRgbaUIntImage2DArray(Image2DArrayWriteOnly image) {
        return readRgbaUIntImage2DArrayInternal(image);
    }

    public final float[] readRgbaFloatImage3D(Image3DReadOnly image) {
        return readRgbaFloatImage3DInternal(image);
    }

    public final float[] readRgbaFloatImage3D(Image3DWriteOnly image) {
        return readRgbaFloatImage3DInternal(image);
    }

    public final int[] readRgbaIntImage3D(Image3DReadOnly image) {
        return readRgbaIntImage3DInternal(image);
    }

    public final int[] readRgbaIntImage3D(Image3DWriteOnly image) {
        return readRgbaIntImage3DInternal(image);
    }

    public final int[] readRgbaUIntImage3D(Image3DReadOnly image) {
        return readRgbaUIntImage3DInternal(image);
    }

    public final int[] readRgbaUIntImage3D(Image3DWriteOnly image) {
        return readRgbaUIntImage3DInternal(image);
    }

    public final Sampler createSampler(boolean normalizedCoordinates, int addressingMode, int filterMode) {
        return createSamplerInternal(normalizedCoordinates, addressingMode, filterMode);
    }

    public final Sampler createNearestClampToEdgeSampler() {
        return createNearestClampToEdgeSampler(false);
    }

    public final Sampler createNearestClampToEdgeSampler(boolean normalizedCoordinates) {
        return createSampler(
                normalizedCoordinates,
                CL10.CL_ADDRESS_CLAMP_TO_EDGE,
                CL10.CL_FILTER_NEAREST
        );
    }

    protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
        return session().compileKernel(kernelDescriptor);
    }

    protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
        return compileKernel(compileRequest, lowerBackendModule(compileRequest));
    }

    protected OpenClCompiledKernel compileKernel(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact
    ) {
        if (compileRequest.options().compileArgs().isEmpty()) {
            return compileKernel(moduleArtifact, compileRequest.descriptor());
        }
        return session().compileKernel(moduleArtifact, compileRequest.descriptor(), compileRequest.options());
    }

    protected OpenClCompiledKernel compileKernel(
            GpuBackendModuleArtifact moduleArtifact,
            GpuKernelDescriptor kernelDescriptor
    ) {
        if (moduleArtifact.source().equals(kernelDescriptor.kernelSource())
                && moduleArtifact.resource().equals(kernelDescriptor.kernelResource())) {
            return compileKernel(kernelDescriptor);
        }
        return session().compileKernel(moduleArtifact, kernelDescriptor, GpuRuntimeCompileOptions.defaults(backendTarget()));
    }

    protected GpuBackendKernelCompiler<OpenClCompiledKernel> kernelCompiler() {
        return kernelCompiler;
    }

    protected GpuBackendKernelPreparer<OpenClCompiledKernel, OpenClPreparedExecution, OpenClExecutionPlan> kernelPreparer() {
        return executionPreparer;
    }

    protected GpuBackendKernelInvoker<OpenClPreparedExecution> kernelInvoker() {
        return kernelInvoker;
    }

    private OpenClCompiledKernel compileBackendKernel(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact
    ) {
        if (overridesCompileRequestHook()) {
            return compileKernel(compileRequest);
        }
        GpuBackendModuleArtifact resolvedModuleArtifact = moduleArtifact == null
                ? lowerBackendModule(compileRequest)
                : moduleArtifact;
        return kernelCompiler().compile(compileRequest, resolvedModuleArtifact);
    }

    protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
        GpuBackendLowerer lowerer = GpuBackendLowerers.forTarget(backendTarget());
        return lowerer.lower(compileRequest);
    }

    private GpuBackendModuleArtifact lowerOptimizedReviewModule(
            GpuRuntimeCompileRequest optimizedCompileRequest,
            GpuRuntimeIrOptimizationReport optimizationReport
    ) {
        if (optimizationReport == null || optimizationReport.candidateArtifact().isEmpty()) {
            return lowerBackendModule(optimizedCompileRequest);
        }
        GpuBackendSourceReconstructionResult reconstruction = OpenClIrGpuSourceReconstructor.INSTANCE.reconstruct(
                optimizedCompileRequest.irGpuArtifact().orElse(null),
                optimizedCompileRequest.descriptor().kernelResource()
        );
        if (!reconstruction.reconstructed() || !reconstruction.sourceAvailable()) {
            return lowerBackendModule(optimizedCompileRequest);
        }
        return GpuBackendModuleArtifact.openClSource(
                reconstruction.source(),
                optimizedCompileRequest.descriptor().kernelResource() + "#irgpu-review-candidate",
                OpenClBackendLowerer.VERSION,
                "irgpu-review-candidate",
                "opencl-irgpu-review-candidate"
        );
    }

    private static boolean runtimeIrOptimizerExperimentalApplyRequested(
            GpuRuntimeCompileRequest optimizedCompileRequest
    ) {
        return optimizedCompileRequest != null
                && optimizedCompileRequest.options()
                .backendOptions()
                .requestsRuntimeIrOptimizerExperimentalApply();
    }

    private static boolean sameIr(Optional<IrGpuArtifact> first, Optional<IrGpuArtifact> second) {
        return IrGpuArtifactIdentity.stableIdentity(first).equals(IrGpuArtifactIdentity.stableIdentity(second));
    }

    protected GpuRuntimeCompileRequest optimizeRuntimeIr(GpuRuntimeCompileRequest compileRequest) {
        Optional<IrGpuArtifact> optimizedArtifact = irOptimizerRegistry.optimize(new GpuRuntimeIrOptimizationRequest(
                compileRequest,
                compileRequest.irGpuArtifact()
        ));
        return compileRequest.withIrGpuArtifact(optimizedArtifact);
    }

    protected GpuRuntimeIrOptimizationResult optimizeRuntimeIrWithReport(GpuRuntimeCompileRequest compileRequest) {
        boolean legacyHook = overridesLegacyOptimizeRuntimeIr();
        publishLifecycleEvent(
                GpuRuntimeLifecycleEventKind.OPTIMIZER_DISCOVERY_STARTED,
                compileRequest,
                "Runtime IR optimizer discovery started",
                optimizerDiscoveryFields(compileRequest, "started", legacyHook, null)
        );
        publishLifecycleEvent(
                GpuRuntimeLifecycleEventKind.OPTIMIZER_DISCOVERY_COMPLETED,
                compileRequest,
                "Runtime IR optimizer discovery completed",
                optimizerDiscoveryFields(compileRequest, "succeeded", legacyHook, null)
        );
        publishLifecycleEvent(
                GpuRuntimeLifecycleEventKind.OPTIMIZER_PASS_STARTED,
                compileRequest,
                "Runtime IR optimizer pass pipeline started",
                optimizerPassFields(null, "started", null)
        );
        try {
            GpuRuntimeIrOptimizationResult result;
            if (legacyHook) {
                GpuRuntimeCompileRequest optimizedCompileRequest = optimizeRuntimeIr(compileRequest);
                result = new GpuRuntimeIrOptimizationResult(
                        optimizedCompileRequest,
                        legacyOptimizationReport(compileRequest, optimizedCompileRequest)
                );
            } else {
                GpuOptimizationStrategyDecision strategyDecision = selectOptimizationStrategy(compileRequest);
                GpuRuntimeIrOptimizationReport optimizationReport = irOptimizerRegistry.optimizeWithReport(
                        new GpuRuntimeIrOptimizationRequest(
                                compileRequest,
                                compileRequest.irGpuArtifact(),
                                strategyDecision
                        )
                );
                GpuRuntimeCompileRequest optimizedCompileRequest = compileRequest.withIrGpuArtifact(
                        optimizationReport.artifactForOptimizedReview()
                );
                result = new GpuRuntimeIrOptimizationResult(optimizedCompileRequest, optimizationReport);
            }
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.OPTIMIZER_PASS_COMPLETED,
                    result.compileRequest(),
                    "Runtime IR optimizer pass pipeline completed",
                    optimizerPassFields(result.report(), "succeeded", null)
            );
            return result;
        } catch (RuntimeException exception) {
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.OPTIMIZER_PASS_COMPLETED,
                    compileRequest,
                    "Runtime IR optimizer pass pipeline failed",
                    optimizerPassFields(null, "failed", exception)
            );
            throw exception;
        }
    }

    protected String optimizerPipelineVersion() {
        return irOptimizerRegistry.optimizerPipelineVersion();
    }

    protected GpuOptimizationStrategyDecision selectOptimizationStrategy(GpuRuntimeCompileRequest compileRequest) {
        return optimizationStrategy.select(compileRequest);
    }

    protected GpuRuntimeEquivalenceEvidence executeRuntimeEquivalence(GpuRuntimeEquivalenceRequest request) {
        if (request == null || request.optimizedCompileRequest().irGpuArtifact().isEmpty()) {
            return GpuRuntimeEquivalenceExecutor.notRun(
                    "pre/post runtime equivalence execution is not enabled for this backend"
            ).execute(request);
        }

        String optimizerFamily = singleRuntimeOptimizerFamily(request.optimizationReport());
        if (!optimizerFamily.isBlank()) {
            try {
                return executeOptimizerFamilyRuntimeEquivalence(request, optimizerFamily);
            } catch (RuntimeException exception) {
                return GpuRuntimeEquivalenceEvidence.failed(
                        request.optimizedCompileRequest(),
                        0,
                        0,
                        java.util.List.of(
                                "optimizer-family OpenCL runtime equivalence failed",
                                exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage()
                        )
                );
            }
        }

        GpuBackendSourceReconstructionResult reconstruction = OpenClIrGpuSourceReconstructor.INSTANCE.reconstruct(
                request.optimizedCompileRequest().irGpuArtifact().orElseThrow(),
                request.optimizedBackendModuleArtifact().resource(),
                request.optimizedBackendModuleArtifact().source()
        );
        if (!reconstruction.reconstructed() || !reconstruction.sourceAvailable()) {
            return GpuRuntimeEquivalenceEvidence.notRun(
                    request.optimizedCompileRequest(),
                    "reconstructed OpenCL source is not available for runtime-equivalence preflight"
            );
        }
        if (!reconstruction.diagnostics().contains("sourceParity.matched=true")) {
            return GpuRuntimeEquivalenceEvidence.notRun(
                    request.optimizedCompileRequest(),
                    "reconstructed OpenCL source parity has not matched descriptor source"
            );
        }

        GpuBackendModuleArtifact reconstructedArtifact = GpuBackendModuleArtifact.openClSource(
                reconstruction.source(),
                request.optimizedBackendModuleArtifact().resource() + "#irgpu-reconstructed-equivalence-preflight",
                OpenClIrGpuSourceReconstructor.VERSION,
                "irgpu-reconstructed-equivalence-preflight",
                "opencl-source-equivalence-preflight"
        );
        try {
            return executeArrayRuntimeEquivalence(
                    request,
                    request.optimizedCompileRequest(),
                    request.optimizedBackendModuleArtifact(),
                    request.optimizedCompileRequest(),
                    reconstructedArtifact,
                    "descriptor-source-vs-irgpu-reconstructed-source",
                    "descriptor and reconstructed OpenCL outputs matched for isolated array runtime-equivalence"
            );
        } catch (RuntimeException exception) {
            return GpuRuntimeEquivalenceEvidence.failed(
                    request.optimizedCompileRequest(),
                    0,
                    0,
                    java.util.List.of(
                            "reconstructed OpenCL source failed runtime-equivalence compile preflight",
                            exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage()
                    )
            );
        }
    }

    private GpuRuntimeEquivalenceEvidence executeOptimizerFamilyRuntimeEquivalence(
            GpuRuntimeEquivalenceRequest request,
            String optimizerFamily
    ) {
        GpuBackendModuleArtifact originalArtifact = lowerBackendModule(request.originalCompileRequest());
        return executeArrayRuntimeEquivalence(
                request,
                request.originalCompileRequest(),
                originalArtifact,
                request.optimizedCompileRequest(),
                request.optimizedBackendModuleArtifact(),
                "optimizer-family:" + optimizerFamily + ":original-vs-optimized",
                "optimizer family " + optimizerFamily + " original and optimized OpenCL outputs matched"
        );
    }

    private GpuRuntimeEquivalenceEvidence executeArrayRuntimeEquivalence(
            GpuRuntimeEquivalenceRequest request,
            GpuRuntimeCompileRequest referenceCompileRequest,
            GpuBackendModuleArtifact referenceArtifact,
            GpuRuntimeCompileRequest candidateCompileRequest,
            GpuBackendModuleArtifact candidateArtifact,
            String comparisonMode,
            String successDiagnostic
    ) {
        Object[] invocationArguments = request.invocationArguments();
        if (invocationArguments == null) {
            return GpuRuntimeEquivalenceEvidence.notRun(
                    request.optimizedCompileRequest(),
                    "runtime-equivalence output comparison requires invocation arguments"
            );
        }
        ArrayEquivalencePlan equivalencePlan = arrayEquivalencePlan(
                candidateCompileRequest.descriptor(),
                invocationArguments
        );
        if (!equivalencePlan.supported()) {
            return GpuRuntimeEquivalenceEvidence.notRun(
                    request.optimizedCompileRequest(),
                    equivalencePlan.unsupportedReason()
            );
        }

        Object[] referenceArguments = cloneEquivalenceArguments(invocationArguments);
        Object[] candidateArguments = cloneEquivalenceArguments(invocationArguments);
        try (OpenClCompiledKernel referenceKernel = compileKernel(
                referenceCompileRequest,
                referenceArtifact);
             OpenClCompiledKernel candidateKernel = compileKernel(
                     candidateCompileRequest,
                     candidateArtifact)) {
            executeIsolatedRuntimeEquivalenceKernel(
                    referenceKernel,
                    referenceCompileRequest.descriptor(),
                    referenceArguments,
                    request.executionConfig()
            );
            executeIsolatedRuntimeEquivalenceKernel(
                    candidateKernel,
                    candidateCompileRequest.descriptor(),
                    candidateArguments,
                    request.executionConfig()
            );
        }

        GpuRuntimeEquivalenceCaseEvidence caseEvidence = captureArrayRuntimeEquivalenceCase(
                candidateCompileRequest.descriptor(),
                comparisonMode,
                invocationArguments,
                referenceArguments,
                candidateArguments,
                equivalencePlan.outputIndexes()
        );
        List<String> mismatches = caseEvidence.diagnostics();
        if (!mismatches.isEmpty()) {
            return GpuRuntimeEquivalenceEvidence.failed(
                    request.optimizedCompileRequest(),
                    1,
                    equivalencePlan.outputIndexes().size(),
                    mismatches,
                    List.of(caseEvidence)
            );
        }
        return GpuRuntimeEquivalenceEvidence.passed(
                candidateCompileRequest,
                1,
                equivalencePlan.outputIndexes().size(),
                List.of(successDiagnostic),
                List.of(caseEvidence)
        );
    }

    private static String singleRuntimeOptimizerFamily(GpuRuntimeIrOptimizationReport report) {
        if (report == null || report.requiresRollback()) {
            return "";
        }
        java.util.LinkedHashSet<String> families = new java.util.LinkedHashSet<>();
        for (GpuRuntimeIrOptimizationPassReport passReport : report.passReports()) {
            if (passReport.analysisOnly()) {
                continue;
            }
            Map<String, String> fields = passReport.proofArtifact().fields();
            if (!"none".equals(fields.getOrDefault("firstBlocker", "none"))) {
                continue;
            }
            String family = fields.getOrDefault("optimizerFamily", "");
            if (!family.isBlank()) {
                families.add(family);
            }
        }
        return families.size() == 1 ? families.iterator().next() : "";
    }

    private void executeIsolatedRuntimeEquivalenceKernel(
            OpenClCompiledKernel compiledKernel,
            GpuKernelDescriptor descriptor,
            Object[] invocationArguments,
            net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig
    ) {
        OpenClKernelArguments arguments = OpenClArgumentMarshaller.marshall(descriptor, invocationArguments);
        OpenClExecutionPlan plan = OpenClExecutionPlanner.plan(arguments);
        OpenClPreparedExecution execution = executionPreparer.prepare(compiledKernel, plan);
        if (executionConfig != null) {
            execution = new OpenClPreparedExecution(
                    execution.compiledKernel(),
                    execution.bufferBindings(),
                    execution.localBindings(),
                    execution.scalarBindings(),
                    execution.argumentBindings(),
                    executionConfig
            );
        }
        executeKernelChecked(execution);
    }

    private ArrayEquivalencePlan arrayEquivalencePlan(
            GpuKernelDescriptor descriptor,
            Object[] invocationArguments
    ) {
        if (descriptor.parameterDescriptors().size() != invocationArguments.length) {
            return ArrayEquivalencePlan.unsupported(
                    "runtime-equivalence argument count mismatch: expected "
                            + descriptor.parameterDescriptors().size()
                            + " but got "
                            + invocationArguments.length
            );
        }
        List<Integer> outputIndexes = new ArrayList<>();
        for (int index = 0; index < invocationArguments.length; index++) {
            Object argument = invocationArguments[index];
            GpuKernelParameterDescriptor parameter = descriptor.parameterDescriptors().get(index);
            if (parameter.access() == net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess.READ_WRITE) {
                if (!isEquivalenceArrayArgument(argument)) {
                    return ArrayEquivalencePlan.unsupported(
                            "runtime-equivalence output comparison currently supports primitive, vector, and struct array outputs only; parameter '"
                                    + parameter.name()
                                    + "' uses "
                                    + runtimeArgumentType(argument)
                    );
                }
                outputIndexes.add(index);
                continue;
            }
            if (argument != null && argument.getClass().isArray() && !isEquivalenceArrayArgument(argument)) {
                return ArrayEquivalencePlan.unsupported(
                        "runtime-equivalence isolation currently supports primitive, vector, and struct array inputs only; parameter '"
                                + parameter.name()
                                + "' uses "
                                + runtimeArgumentType(argument)
                );
            }
        }
        if (outputIndexes.isEmpty()) {
            return ArrayEquivalencePlan.unsupported(
                    "runtime-equivalence output comparison requires at least one READ_WRITE array output"
            );
        }
        return ArrayEquivalencePlan.supported(outputIndexes);
    }

    private Object[] cloneEquivalenceArguments(Object[] invocationArguments) {
        Object[] clonedArguments = invocationArguments.clone();
        for (int index = 0; index < clonedArguments.length; index++) {
            clonedArguments[index] = cloneEquivalenceArgument(clonedArguments[index]);
        }
        return clonedArguments;
    }

    private Object cloneEquivalenceArgument(Object argument) {
        if (argument instanceof byte[] values) {
            return values.clone();
        }
        if (argument instanceof short[] values) {
            return values.clone();
        }
        if (argument instanceof int[] values) {
            return values.clone();
        }
        if (argument instanceof long[] values) {
            return values.clone();
        }
        if (argument instanceof float[] values) {
            return values.clone();
        }
        if (argument instanceof double[] values) {
            return values.clone();
        }
        if (argument != null && OpenClValuePacker.isStructArrayInstance(argument)) {
            return clonePackedArray(argument, OpenClValuePacker.packStructArray(argument));
        }
        if (argument != null && OpenClValuePacker.isVectorArrayInstance(argument)) {
            return clonePackedArray(argument, OpenClValuePacker.packVectorArray(argument));
        }
        return argument;
    }

    private Object clonePackedArray(Object sourceArray, ByteBuffer packedBytes) {
        Object clonedArray = java.lang.reflect.Array.newInstance(
                sourceArray.getClass().getComponentType(),
                java.lang.reflect.Array.getLength(sourceArray)
        );
        ByteBuffer bytes = packedBytes.duplicate().order(ByteOrder.nativeOrder());
        bytes.position(0);
        if (OpenClValuePacker.isStructArrayInstance(sourceArray)) {
            OpenClValuePacker.unpackStructArray(bytes, clonedArray);
        } else if (OpenClValuePacker.isVectorArrayInstance(sourceArray)) {
            OpenClValuePacker.unpackVectorArray(bytes, clonedArray);
        }
        return clonedArray;
    }

    private GpuRuntimeEquivalenceCaseEvidence captureArrayRuntimeEquivalenceCase(
            GpuKernelDescriptor descriptor,
            String comparisonMode,
            Object[] invocationArguments,
            Object[] descriptorArguments,
            Object[] reconstructedArguments,
            List<Integer> outputIndexes
    ) {
        Map<String, String> inputs = new LinkedHashMap<>();
        for (int argumentIndex = 0; argumentIndex < invocationArguments.length; argumentIndex++) {
            GpuKernelParameterDescriptor parameter = descriptor.parameterDescriptors().get(argumentIndex);
            inputs.put(parameter.name(), formatEquivalenceValue(invocationArguments[argumentIndex]));
        }
        Map<String, String> referenceOutputs = new LinkedHashMap<>();
        Map<String, String> candidateOutputs = new LinkedHashMap<>();
        Map<String, String> tolerances = new LinkedHashMap<>();
        Map<String, Boolean> outputEquivalence = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        for (int outputIndex : outputIndexes) {
            Object descriptorOutput = descriptorArguments[outputIndex];
            Object reconstructedOutput = reconstructedArguments[outputIndex];
            GpuKernelParameterDescriptor parameter = descriptor.parameterDescriptors().get(outputIndex);
            boolean equivalent = equivalenceArraysEqual(descriptorOutput, reconstructedOutput);
            referenceOutputs.put(parameter.name(), formatEquivalenceValue(descriptorOutput));
            candidateOutputs.put(parameter.name(), formatEquivalenceValue(reconstructedOutput));
            tolerances.put(parameter.name(), equivalenceTolerance(descriptorOutput));
            outputEquivalence.put(parameter.name(), equivalent);
            if (!equivalent) {
                diagnostics.add(
                        "runtime-equivalence output mismatch for parameter '"
                                + parameter.name()
                                + "' at argument "
                                + outputIndex
                );
            }
        }
        return new GpuRuntimeEquivalenceCaseEvidence(
                "runtime-invocation-0",
                comparisonMode,
                inputs,
                referenceOutputs,
                candidateOutputs,
                tolerances,
                outputEquivalence,
                diagnostics
        );
    }

    private String formatEquivalenceValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof byte[] values) {
            return Arrays.toString(values);
        }
        if (value instanceof short[] values) {
            return Arrays.toString(values);
        }
        if (value instanceof int[] values) {
            return Arrays.toString(values);
        }
        if (value instanceof long[] values) {
            return Arrays.toString(values);
        }
        if (value instanceof float[] values) {
            return Arrays.toString(values);
        }
        if (value instanceof double[] values) {
            return Arrays.toString(values);
        }
        if (OpenClValuePacker.isStructArrayInstance(value)) {
            return packedEquivalenceValue("struct-array", OpenClValuePacker.packStructArray(value));
        }
        if (OpenClValuePacker.isVectorArrayInstance(value)) {
            return packedEquivalenceValue("vector-array", OpenClValuePacker.packVectorArray(value));
        }
        if (value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof CharSequence
                || value instanceof Enum<?>) {
            return String.valueOf(value);
        }
        return "opaque-type:" + value.getClass().getName();
    }

    private String equivalenceTolerance(Object value) {
        if (value instanceof byte[]) {
            return "exact-byte-array";
        }
        if (value instanceof short[]) {
            return "exact-short-array";
        }
        if (value instanceof int[]) {
            return "exact-int-array";
        }
        if (value instanceof long[]) {
            return "exact-long-array";
        }
        if (value instanceof float[]) {
            return "exact-float-array";
        }
        if (value instanceof double[]) {
            return "exact-double-array";
        }
        if (value != null
                && (OpenClValuePacker.isStructArrayInstance(value)
                || OpenClValuePacker.isVectorArrayInstance(value))) {
            return "exact-packed-bytes";
        }
        return "exact-value";
    }

    private String packedEquivalenceValue(String kind, ByteBuffer packedValue) {
        ByteBuffer bytes = packedValue.duplicate();
        bytes.position(0);
        byte[] value = new byte[bytes.remaining()];
        bytes.get(value);
        return kind + ":base64:" + java.util.Base64.getEncoder().encodeToString(value);
    }

    private boolean equivalenceArraysEqual(Object first, Object second) {
        if (first instanceof byte[] firstValues && second instanceof byte[] secondValues) {
            return Arrays.equals(firstValues, secondValues);
        }
        if (first instanceof short[] firstValues && second instanceof short[] secondValues) {
            return Arrays.equals(firstValues, secondValues);
        }
        if (first instanceof int[] firstValues && second instanceof int[] secondValues) {
            return Arrays.equals(firstValues, secondValues);
        }
        if (first instanceof long[] firstValues && second instanceof long[] secondValues) {
            return Arrays.equals(firstValues, secondValues);
        }
        if (first instanceof float[] firstValues && second instanceof float[] secondValues) {
            return Arrays.equals(firstValues, secondValues);
        }
        if (first instanceof double[] firstValues && second instanceof double[] secondValues) {
            return Arrays.equals(firstValues, secondValues);
        }
        if (first != null
                && second != null
                && OpenClValuePacker.isStructArrayInstance(first)
                && OpenClValuePacker.isStructArrayInstance(second)) {
            return byteBuffersEqual(OpenClValuePacker.packStructArray(first), OpenClValuePacker.packStructArray(second));
        }
        if (first != null
                && second != null
                && OpenClValuePacker.isVectorArrayInstance(first)
                && OpenClValuePacker.isVectorArrayInstance(second)) {
            return byteBuffersEqual(OpenClValuePacker.packVectorArray(first), OpenClValuePacker.packVectorArray(second));
        }
        return false;
    }

    private boolean byteBuffersEqual(ByteBuffer first, ByteBuffer second) {
        ByteBuffer firstBytes = first.duplicate();
        ByteBuffer secondBytes = second.duplicate();
        firstBytes.position(0);
        secondBytes.position(0);
        if (firstBytes.remaining() != secondBytes.remaining()) {
            return false;
        }
        while (firstBytes.hasRemaining()) {
            if (firstBytes.get() != secondBytes.get()) {
                return false;
            }
        }
        return true;
    }

    private boolean isEquivalenceArrayArgument(Object argument) {
        return argument instanceof byte[]
                || argument instanceof short[]
                || argument instanceof int[]
                || argument instanceof long[]
                || argument instanceof float[]
                || argument instanceof double[]
                || argument != null && OpenClValuePacker.isStructArrayInstance(argument)
                || argument != null && OpenClValuePacker.isVectorArrayInstance(argument);
    }

    private String runtimeArgumentType(Object argument) {
        return argument == null ? "null" : argument.getClass().getName();
    }

    private record ArrayEquivalencePlan(boolean supported, List<Integer> outputIndexes, String unsupportedReason) {
        private ArrayEquivalencePlan {
            outputIndexes = outputIndexes == null ? List.of() : List.copyOf(outputIndexes);
            unsupportedReason = unsupportedReason == null ? "" : unsupportedReason;
        }

        private static ArrayEquivalencePlan supported(List<Integer> outputIndexes) {
            return new ArrayEquivalencePlan(true, outputIndexes, "");
        }

        private static ArrayEquivalencePlan unsupported(String reason) {
            return new ArrayEquivalencePlan(false, List.of(), reason);
        }
    }

    private boolean overridesLegacyOptimizeRuntimeIr() {
        try {
            Method method = getClass().getDeclaredMethod("optimizeRuntimeIr", GpuRuntimeCompileRequest.class);
            return method.getDeclaringClass() != OpenClGpuRuntimeBackend.class;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private GpuRuntimeIrOptimizationReport legacyOptimizationReport(
            GpuRuntimeCompileRequest originalCompileRequest,
            GpuRuntimeCompileRequest optimizedCompileRequest
    ) {
        Optional<IrGpuArtifact> originalArtifact = originalCompileRequest == null
                ? Optional.empty()
                : originalCompileRequest.irGpuArtifact();
        Optional<IrGpuArtifact> optimizedArtifact = optimizedCompileRequest == null
                ? Optional.empty()
                : optimizedCompileRequest.irGpuArtifact();
        String originalIdentity = originalArtifact.map(IrGpuArtifactIdentity::stableIdentity).orElse("irgpu:missing");
        String optimizedIdentity = optimizedArtifact.map(IrGpuArtifactIdentity::stableIdentity).orElse("irgpu:missing");
        GpuRuntimeIrOptimizationPassReport passReport = originalIdentity.equals(optimizedIdentity)
                ? GpuRuntimeIrOptimizationPassReport.skipped(
                optimizerPipelineVersion(),
                originalIdentity,
                "legacy optimizeRuntimeIr hook returned unchanged IR"
        )
                : GpuRuntimeIrOptimizationPassReport.applied(
                optimizerPipelineVersion(),
                originalIdentity,
                optimizedIdentity,
                "legacy-opencl-hook",
                java.util.List.of("OpenClGpuRuntimeBackend.optimizeRuntimeIr override supplied optimized IR")
        );
        return new GpuRuntimeIrOptimizationReport(optimizedArtifact, java.util.List.of(passReport));
    }

    protected record GpuRuntimeIrOptimizationResult(
            GpuRuntimeCompileRequest compileRequest,
            GpuRuntimeIrOptimizationReport report
    ) {
        protected GpuRuntimeIrOptimizationResult {
            compileRequest = Objects.requireNonNull(compileRequest, "compileRequest");
            report = report == null ? GpuRuntimeIrOptimizationReport.empty(compileRequest.irGpuArtifact()) : report;
        }
    }

    protected void executeKernel(OpenClPreparedExecution execution) {
        for (OpenClPreparedBufferBinding binding : execution.bufferBindings()) {
            Object nativeBuffer = resolveNativeBuffer(binding);
            if (binding.binding().uploadRequired()) {
                uploadToDeviceBuffer(nativeBuffer, binding.binding());
            }
        }

        for (OpenClPreparedArgumentBinding binding : execution.argumentBindings()) {
            if (binding.bufferBinding() != null) {
                bindBufferArgument(
                        execution.compiledKernel(),
                        binding.parameterIndex(),
                        resolveNativeBuffer(binding.bufferBinding())
                );
                continue;
            }

            if (binding.localBinding() != null) {
                bindLocalArgument(execution.compiledKernel(), binding.parameterIndex(), binding.localBinding());
                continue;
            }

            bindScalarArgument(execution.compiledKernel(), binding.parameterIndex(), binding.scalarBinding());
        }

        enqueueKernel(execution.compiledKernel(), resolveExecutionConfig(execution));

        for (OpenClPreparedBufferBinding binding : execution.bufferBindings()) {
            if (binding.binding().readbackRequired()) {
                readBackFromDeviceBuffer(resolveNativeBuffer(binding), binding.binding());
            }
        }
    }

    protected OpenClRuntimeSession createSession() {
        return OpenClRuntimeSession.createDefault(
                devicePolicyRegistry,
                null,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                Optional.empty(),
                preselectedDeviceSelection()
        );
    }

    protected OpenClRuntimeSession createSession(
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileOptions compileOptions
    ) {
        return createSession(descriptor, compileOptions, Optional.empty());
    }

    protected OpenClRuntimeSession createSession(
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileOptions compileOptions,
            Optional<IrGpuArtifact> irGpuArtifact
    ) {
        if (overridesLegacyCreateSessionHook()) {
            return createSession();
        }
        return OpenClRuntimeSession.createDefault(
                devicePolicyRegistry,
                descriptor,
                compileOptions,
                irGpuArtifact,
                preselectedDeviceSelection()
        );
    }

    protected Optional<GpuRuntimeDeviceSelection> runtimeDeviceSelection() {
        OpenClRuntimeSession currentSession = cacheMode == CacheMode.SHARED ? sharedSession : session;
        return Optional.ofNullable(currentSession)
                .map(OpenClRuntimeSession::deviceSelection)
                .or(this::preselectedDeviceSelection);
    }

    protected Optional<GpuRuntimeDeviceSelection> preselectedDeviceSelection() {
        return Optional.ofNullable(preselectedDeviceDiscovery)
                .flatMap(GpuRuntimeDeviceDiscoveryResult::deviceSelection);
    }

    protected OpenClValidationDeviceInfo runtimeValidationDeviceInfo() {
        return session().validationDeviceInfo();
    }

    protected OpenClRuntimeCapabilities runtimeCapabilities() {
        if (cacheMode == CacheMode.SHARED) {
            OpenClRuntimeCapabilities current = sharedCapabilities;
            if (current != null) {
                return current;
            }

            synchronized (SHARED_RUNTIME_LOCK) {
                current = sharedCapabilities;
                if (current == null) {
                    current = session().capabilities();
                    sharedCapabilities = current;
                }
            }

            return current;
        }

        OpenClRuntimeCapabilities current = capabilities;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            current = capabilities;
            if (current == null) {
                current = session().capabilities();
                capabilities = current;
            }
        }

        return current;
    }

    protected Image2DReadOnly createReadOnlyRgbaFloatImageInternal(int width, int height, float[] rgba) {
        return session().createReadOnlyRgbaFloatImage(width, height, rgba);
    }

    protected Image2DWriteOnly createWriteOnlyRgbaFloatImageInternal(int width, int height) {
        return session().createWriteOnlyRgbaFloatImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRFloatImageInternal(int width, int height, float[] values) {
        return session().createReadOnlyRFloatImage(width, height, values);
    }

    protected Image2DWriteOnly createWriteOnlyRFloatImageInternal(int width, int height) {
        return session().createWriteOnlyRFloatImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRgFloatImageInternal(int width, int height, float[] values) {
        return session().createReadOnlyRgFloatImage(width, height, values);
    }

    protected Image2DWriteOnly createWriteOnlyRgFloatImageInternal(int width, int height) {
        return session().createWriteOnlyRgFloatImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyDepthImageInternal(int width, int height, float[] values) {
        return session().createReadOnlyDepthImage(width, height, values);
    }

    protected Image2DWriteOnly createWriteOnlyDepthImageInternal(int width, int height) {
        return session().createWriteOnlyDepthImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRIntImageInternal(int width, int height, int[] values) {
        return session().createReadOnlyRIntImage(width, height, values);
    }

    protected Image2DWriteOnly createWriteOnlyRIntImageInternal(int width, int height) {
        return session().createWriteOnlyRIntImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRgIntImageInternal(int width, int height, int[] values) {
        return session().createReadOnlyRgIntImage(width, height, values);
    }

    protected Image2DWriteOnly createWriteOnlyRgIntImageInternal(int width, int height) {
        return session().createWriteOnlyRgIntImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRgbaIntImageInternal(int width, int height, int[] rgba) {
        return session().createReadOnlyRgbaIntImage(width, height, rgba);
    }

    protected Image2DWriteOnly createWriteOnlyRgbaIntImageInternal(int width, int height) {
        return session().createWriteOnlyRgbaIntImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRgbaUIntImageInternal(int width, int height, int[] rgba) {
        return session().createReadOnlyRgbaUIntImage(width, height, rgba);
    }

    protected Image2DWriteOnly createWriteOnlyRgbaUIntImageInternal(int width, int height) {
        return session().createWriteOnlyRgbaUIntImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRUIntImageInternal(int width, int height, int[] values) {
        return session().createReadOnlyRUIntImage(width, height, values);
    }

    protected Image2DWriteOnly createWriteOnlyRUIntImageInternal(int width, int height) {
        return session().createWriteOnlyRUIntImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRgUIntImageInternal(int width, int height, int[] values) {
        return session().createReadOnlyRgUIntImage(width, height, values);
    }

    protected Image2DWriteOnly createWriteOnlyRgUIntImageInternal(int width, int height) {
        return session().createWriteOnlyRgUIntImage(width, height);
    }

    protected Image2DReadOnly createReadOnlyRgba8ImageInternal(int width, int height, byte[] rgba) {
        return session().createReadOnlyRgba8Image(width, height, rgba);
    }

    protected Image2DWriteOnly createWriteOnlyRgba8ImageInternal(int width, int height) {
        return session().createWriteOnlyRgba8Image(width, height);
    }

    protected Image2DMipmappedReadOnly createReadOnlyRgbaFloatImageMipmappedInternal(int width, int height, int mipLevels, float[] rgba) {
        return session().createReadOnlyRgbaFloatImageMipmapped(width, height, mipLevels, rgba);
    }

    protected Image2DMipmappedWriteOnly createWriteOnlyRgbaFloatImageMipmappedInternal(int width, int height, int mipLevels) {
        return session().createWriteOnlyRgbaFloatImageMipmapped(width, height, mipLevels);
    }

    protected Image2DMipmappedReadOnly createReadOnlyRgba8ImageMipmappedInternal(int width, int height, int mipLevels, byte[] rgba) {
        return session().createReadOnlyRgba8ImageMipmapped(width, height, mipLevels, rgba);
    }

    protected Image2DMipmappedWriteOnly createWriteOnlyRgba8ImageMipmappedInternal(int width, int height, int mipLevels) {
        return session().createWriteOnlyRgba8ImageMipmapped(width, height, mipLevels);
    }

    protected Image2DMipmappedReadOnly createReadOnlyRgbaIntImageMipmappedInternal(int width, int height, int mipLevels, int[] rgba) {
        return session().createReadOnlyRgbaIntImageMipmapped(width, height, mipLevels, rgba);
    }

    protected Image2DMipmappedWriteOnly createWriteOnlyRgbaIntImageMipmappedInternal(int width, int height, int mipLevels) {
        return session().createWriteOnlyRgbaIntImageMipmapped(width, height, mipLevels);
    }

    protected Image2DMipmappedReadOnly createReadOnlyRgbaUIntImageMipmappedInternal(int width, int height, int mipLevels, int[] rgba) {
        return session().createReadOnlyRgbaUIntImageMipmapped(width, height, mipLevels, rgba);
    }

    protected Image2DMipmappedWriteOnly createWriteOnlyRgbaUIntImageMipmappedInternal(int width, int height, int mipLevels) {
        return session().createWriteOnlyRgbaUIntImageMipmapped(width, height, mipLevels);
    }

    protected Image1DReadOnly createReadOnlyRgbaFloatImage1DInternal(int width, float[] rgba) {
        return session().createReadOnlyRgbaFloatImage1D(width, rgba);
    }

    protected Image1DWriteOnly createWriteOnlyRgbaFloatImage1DInternal(int width) {
        return session().createWriteOnlyRgbaFloatImage1D(width);
    }

    protected Image1DReadOnly createReadOnlyRgbaUIntImage1DInternal(int width, int[] rgba) {
        return session().createReadOnlyRgbaUIntImage1D(width, rgba);
    }

    protected Image1DWriteOnly createWriteOnlyRgbaUIntImage1DInternal(int width) {
        return session().createWriteOnlyRgbaUIntImage1D(width);
    }

    protected Image1DReadOnly createReadOnlyRgbaIntImage1DInternal(int width, int[] rgba) {
        return session().createReadOnlyRgbaIntImage1D(width, rgba);
    }

    protected Image1DWriteOnly createWriteOnlyRgbaIntImage1DInternal(int width) {
        return session().createWriteOnlyRgbaIntImage1D(width);
    }

    protected Image1DArrayReadOnly createReadOnlyRgbaFloatImage1DArrayInternal(int width, int layers, float[] rgba) {
        return session().createReadOnlyRgbaFloatImage1DArray(width, layers, rgba);
    }

    protected Image1DArrayWriteOnly createWriteOnlyRgbaFloatImage1DArrayInternal(int width, int layers) {
        return session().createWriteOnlyRgbaFloatImage1DArray(width, layers);
    }

    protected Image1DArrayReadOnly createReadOnlyRgbaIntImage1DArrayInternal(int width, int layers, int[] rgba) {
        return session().createReadOnlyRgbaIntImage1DArray(width, layers, rgba);
    }

    protected Image1DArrayWriteOnly createWriteOnlyRgbaIntImage1DArrayInternal(int width, int layers) {
        return session().createWriteOnlyRgbaIntImage1DArray(width, layers);
    }

    protected Image1DArrayReadOnly createReadOnlyRgbaUIntImage1DArrayInternal(int width, int layers, int[] rgba) {
        return session().createReadOnlyRgbaUIntImage1DArray(width, layers, rgba);
    }

    protected Image1DArrayWriteOnly createWriteOnlyRgbaUIntImage1DArrayInternal(int width, int layers) {
        return session().createWriteOnlyRgbaUIntImage1DArray(width, layers);
    }

    protected Image1DBufferReadOnly createReadOnlyRgbaFloatImage1DBufferInternal(int width, float[] rgba) {
        return session().createReadOnlyRgbaFloatImage1DBuffer(width, rgba);
    }

    protected Image1DBufferWriteOnly createWriteOnlyRgbaFloatImage1DBufferInternal(int width) {
        return session().createWriteOnlyRgbaFloatImage1DBuffer(width);
    }

    protected Image1DBufferReadOnly createReadOnlyRgbaIntImage1DBufferInternal(int width, int[] rgba) {
        return session().createReadOnlyRgbaIntImage1DBuffer(width, rgba);
    }

    protected Image1DBufferWriteOnly createWriteOnlyRgbaIntImage1DBufferInternal(int width) {
        return session().createWriteOnlyRgbaIntImage1DBuffer(width);
    }

    protected Image1DBufferReadOnly createReadOnlyRgbaUIntImage1DBufferInternal(int width, int[] rgba) {
        return session().createReadOnlyRgbaUIntImage1DBuffer(width, rgba);
    }

    protected Image1DBufferWriteOnly createWriteOnlyRgbaUIntImage1DBufferInternal(int width) {
        return session().createWriteOnlyRgbaUIntImage1DBuffer(width);
    }

    protected Image2DArrayReadOnly createReadOnlyRgbaFloatImage2DArrayInternal(int width, int height, int layers, float[] rgba) {
        return session().createReadOnlyRgbaFloatImage2DArray(width, height, layers, rgba);
    }

    protected Image2DArrayWriteOnly createWriteOnlyRgbaFloatImage2DArrayInternal(int width, int height, int layers) {
        return session().createWriteOnlyRgbaFloatImage2DArray(width, height, layers);
    }

    protected Image2DArrayReadOnly createReadOnlyRgbaIntImage2DArrayInternal(int width, int height, int layers, int[] rgba) {
        return session().createReadOnlyRgbaIntImage2DArray(width, height, layers, rgba);
    }

    protected Image2DArrayWriteOnly createWriteOnlyRgbaIntImage2DArrayInternal(int width, int height, int layers) {
        return session().createWriteOnlyRgbaIntImage2DArray(width, height, layers);
    }

    protected Image2DArrayReadOnly createReadOnlyRgbaUIntImage2DArrayInternal(int width, int height, int layers, int[] rgba) {
        return session().createReadOnlyRgbaUIntImage2DArray(width, height, layers, rgba);
    }

    protected Image2DArrayWriteOnly createWriteOnlyRgbaUIntImage2DArrayInternal(int width, int height, int layers) {
        return session().createWriteOnlyRgbaUIntImage2DArray(width, height, layers);
    }

    protected Image3DReadOnly createReadOnlyRgbaFloatImage3DInternal(int width, int height, int depth, float[] rgba) {
        return session().createReadOnlyRgbaFloatImage3D(width, height, depth, rgba);
    }

    protected Image3DWriteOnly createWriteOnlyRgbaFloatImage3DInternal(int width, int height, int depth) {
        return session().createWriteOnlyRgbaFloatImage3D(width, height, depth);
    }

    protected Image3DReadOnly createReadOnlyRgbaIntImage3DInternal(int width, int height, int depth, int[] rgba) {
        return session().createReadOnlyRgbaIntImage3D(width, height, depth, rgba);
    }

    protected Image3DWriteOnly createWriteOnlyRgbaIntImage3DInternal(int width, int height, int depth) {
        return session().createWriteOnlyRgbaIntImage3D(width, height, depth);
    }

    protected Image3DReadOnly createReadOnlyRgbaUIntImage3DInternal(int width, int height, int depth, int[] rgba) {
        return session().createReadOnlyRgbaUIntImage3D(width, height, depth, rgba);
    }

    protected Image3DWriteOnly createWriteOnlyRgbaUIntImage3DInternal(int width, int height, int depth) {
        return session().createWriteOnlyRgbaUIntImage3D(width, height, depth);
    }

    protected float[] readRgbaFloatImageInternal(Image2DReadOnly image) {
        return session().readRgbaFloatImage(image);
    }

    protected float[] readRgbaFloatImageInternal(Image2DWriteOnly image) {
        return session().readRgbaFloatImage(image);
    }

    protected float[] readRFloatImageInternal(Image2DReadOnly image) {
        return session().readRFloatImage(image);
    }

    protected float[] readRFloatImageInternal(Image2DWriteOnly image) {
        return session().readRFloatImage(image);
    }

    protected float[] readRgFloatImageInternal(Image2DReadOnly image) {
        return session().readRgFloatImage(image);
    }

    protected float[] readRgFloatImageInternal(Image2DWriteOnly image) {
        return session().readRgFloatImage(image);
    }

    protected float[] readDepthImageInternal(Image2DReadOnly image) {
        return session().readDepthImage(image);
    }

    protected float[] readDepthImageInternal(Image2DWriteOnly image) {
        return session().readDepthImage(image);
    }

    protected int[] readRIntImageInternal(Image2DReadOnly image) {
        return session().readRIntImage(image);
    }

    protected int[] readRIntImageInternal(Image2DWriteOnly image) {
        return session().readRIntImage(image);
    }

    protected int[] readRgIntImageInternal(Image2DReadOnly image) {
        return session().readRgIntImage(image);
    }

    protected int[] readRgIntImageInternal(Image2DWriteOnly image) {
        return session().readRgIntImage(image);
    }

    protected int[] readRgbaIntImageInternal(Image2DReadOnly image) {
        return session().readRgbaIntImage(image);
    }

    protected int[] readRgbaIntImageInternal(Image2DWriteOnly image) {
        return session().readRgbaIntImage(image);
    }

    protected int[] readRgbaUIntImageInternal(Image2DReadOnly image) {
        return session().readRgbaUIntImage(image);
    }

    protected int[] readRgbaUIntImageInternal(Image2DWriteOnly image) {
        return session().readRgbaUIntImage(image);
    }

    protected int[] readRUIntImageInternal(Image2DReadOnly image) {
        return session().readRUIntImage(image);
    }

    protected int[] readRUIntImageInternal(Image2DWriteOnly image) {
        return session().readRUIntImage(image);
    }

    protected int[] readRgUIntImageInternal(Image2DReadOnly image) {
        return session().readRgUIntImage(image);
    }

    protected int[] readRgUIntImageInternal(Image2DWriteOnly image) {
        return session().readRgUIntImage(image);
    }

    protected byte[] readRgba8ImageInternal(Image2DReadOnly image) {
        return session().readRgba8Image(image);
    }

    protected byte[] readRgba8ImageInternal(Image2DWriteOnly image) {
        return session().readRgba8Image(image);
    }

    protected float[] readRgbaFloatImageMipmappedInternal(Image2DMipmappedReadOnly image, int mipLevel) {
        return session().readRgbaFloatImageMipmapped(image, mipLevel);
    }

    protected float[] readRgbaFloatImageMipmappedInternal(Image2DMipmappedWriteOnly image, int mipLevel) {
        return session().readRgbaFloatImageMipmapped(image, mipLevel);
    }

    protected byte[] readRgba8ImageMipmappedInternal(Image2DMipmappedReadOnly image, int mipLevel) {
        return session().readRgba8ImageMipmapped(image, mipLevel);
    }

    protected byte[] readRgba8ImageMipmappedInternal(Image2DMipmappedWriteOnly image, int mipLevel) {
        return session().readRgba8ImageMipmapped(image, mipLevel);
    }

    protected int[] readRgbaIntImageMipmappedInternal(Image2DMipmappedReadOnly image, int mipLevel) {
        return session().readRgbaIntImageMipmapped(image, mipLevel);
    }

    protected int[] readRgbaIntImageMipmappedInternal(Image2DMipmappedWriteOnly image, int mipLevel) {
        return session().readRgbaIntImageMipmapped(image, mipLevel);
    }

    protected int[] readRgbaUIntImageMipmappedInternal(Image2DMipmappedReadOnly image, int mipLevel) {
        return session().readRgbaUIntImageMipmapped(image, mipLevel);
    }

    protected int[] readRgbaUIntImageMipmappedInternal(Image2DMipmappedWriteOnly image, int mipLevel) {
        return session().readRgbaUIntImageMipmapped(image, mipLevel);
    }

    protected float[] readRgbaFloatImage1DInternal(Image1DReadOnly image) {
        return session().readRgbaFloatImage1D(image);
    }

    protected float[] readRgbaFloatImage1DInternal(Image1DWriteOnly image) {
        return session().readRgbaFloatImage1D(image);
    }

    protected int[] readRgbaUIntImage1DInternal(Image1DReadOnly image) {
        return session().readRgbaUIntImage1D(image);
    }

    protected int[] readRgbaUIntImage1DInternal(Image1DWriteOnly image) {
        return session().readRgbaUIntImage1D(image);
    }

    protected int[] readRgbaIntImage1DInternal(Image1DReadOnly image) {
        return session().readRgbaIntImage1D(image);
    }

    protected int[] readRgbaIntImage1DInternal(Image1DWriteOnly image) {
        return session().readRgbaIntImage1D(image);
    }

    protected float[] readRgbaFloatImage1DArrayInternal(Image1DArrayReadOnly image) {
        return session().readRgbaFloatImage1DArray(image);
    }

    protected float[] readRgbaFloatImage1DArrayInternal(Image1DArrayWriteOnly image) {
        return session().readRgbaFloatImage1DArray(image);
    }

    protected int[] readRgbaIntImage1DArrayInternal(Image1DArrayReadOnly image) {
        return session().readRgbaIntImage1DArray(image);
    }

    protected int[] readRgbaIntImage1DArrayInternal(Image1DArrayWriteOnly image) {
        return session().readRgbaIntImage1DArray(image);
    }

    protected int[] readRgbaUIntImage1DArrayInternal(Image1DArrayReadOnly image) {
        return session().readRgbaUIntImage1DArray(image);
    }

    protected int[] readRgbaUIntImage1DArrayInternal(Image1DArrayWriteOnly image) {
        return session().readRgbaUIntImage1DArray(image);
    }

    protected float[] readRgbaFloatImage1DBufferInternal(Image1DBufferReadOnly image) {
        return session().readRgbaFloatImage1DBuffer(image);
    }

    protected float[] readRgbaFloatImage1DBufferInternal(Image1DBufferWriteOnly image) {
        return session().readRgbaFloatImage1DBuffer(image);
    }

    protected int[] readRgbaIntImage1DBufferInternal(Image1DBufferReadOnly image) {
        return session().readRgbaIntImage1DBuffer(image);
    }

    protected int[] readRgbaIntImage1DBufferInternal(Image1DBufferWriteOnly image) {
        return session().readRgbaIntImage1DBuffer(image);
    }

    protected int[] readRgbaUIntImage1DBufferInternal(Image1DBufferReadOnly image) {
        return session().readRgbaUIntImage1DBuffer(image);
    }

    protected int[] readRgbaUIntImage1DBufferInternal(Image1DBufferWriteOnly image) {
        return session().readRgbaUIntImage1DBuffer(image);
    }

    protected float[] readRgbaFloatImage2DArrayInternal(Image2DArrayReadOnly image) {
        return session().readRgbaFloatImage2DArray(image);
    }

    protected float[] readRgbaFloatImage2DArrayInternal(Image2DArrayWriteOnly image) {
        return session().readRgbaFloatImage2DArray(image);
    }

    protected int[] readRgbaIntImage2DArrayInternal(Image2DArrayReadOnly image) {
        return session().readRgbaIntImage2DArray(image);
    }

    protected int[] readRgbaIntImage2DArrayInternal(Image2DArrayWriteOnly image) {
        return session().readRgbaIntImage2DArray(image);
    }

    protected int[] readRgbaUIntImage2DArrayInternal(Image2DArrayReadOnly image) {
        return session().readRgbaUIntImage2DArray(image);
    }

    protected int[] readRgbaUIntImage2DArrayInternal(Image2DArrayWriteOnly image) {
        return session().readRgbaUIntImage2DArray(image);
    }

    protected float[] readRgbaFloatImage3DInternal(Image3DReadOnly image) {
        return session().readRgbaFloatImage3D(image);
    }

    protected float[] readRgbaFloatImage3DInternal(Image3DWriteOnly image) {
        return session().readRgbaFloatImage3D(image);
    }

    protected int[] readRgbaIntImage3DInternal(Image3DReadOnly image) {
        return session().readRgbaIntImage3D(image);
    }

    protected int[] readRgbaIntImage3DInternal(Image3DWriteOnly image) {
        return session().readRgbaIntImage3D(image);
    }

    protected int[] readRgbaUIntImage3DInternal(Image3DReadOnly image) {
        return session().readRgbaUIntImage3D(image);
    }

    protected int[] readRgbaUIntImage3DInternal(Image3DWriteOnly image) {
        return session().readRgbaUIntImage3D(image);
    }

    protected Sampler createSamplerInternal(boolean normalizedCoordinates, int addressingMode, int filterMode) {
        return session().createSampler(normalizedCoordinates, addressingMode, filterMode);
    }

    protected Object createDeviceBuffer(OpenClBufferBinding binding) {
        return session().createReadWriteBuffer(bytesFor(binding));
    }

    protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
        if (binding.sourceArray() instanceof byte[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length);
            buffer.put(values).flip();
            session().queue().writeBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            return;
        }
        if (binding.sourceArray() instanceof short[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length * Short.BYTES);
            for (short value : values) {
                buffer.putShort(value);
            }
            buffer.flip();
            writeBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            return;
        }
        if (binding.sourceArray() instanceof int[] values) {
            IntBuffer buffer = allocateIntBuffer(values.length);
            buffer.put(values).flip();
            session().queue().writeBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            return;
        }
        if (binding.sourceArray() instanceof long[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length * Long.BYTES);
            for (long value : values) {
                buffer.putLong(value);
            }
            buffer.flip();
            writeBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            return;
        }
        if (binding.sourceArray() instanceof float[] values) {
            FloatBuffer buffer = allocateFloatBuffer(values.length);
            buffer.put(values).flip();
            session().queue().writeBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            return;
        }
        if (binding.sourceArray() instanceof double[] values) {
            DoubleBuffer buffer = allocateDoubleBuffer(values.length);
            buffer.put(values).flip();
            readWriteBufferDirect((OpenClBuffer) nativeBuffer, buffer, true);
            return;
        }
        if (binding.kind() == OpenClArgumentKind.STRUCT_ARRAY) {
            writeBufferDirect((OpenClBuffer) nativeBuffer, OpenClValuePacker.packStructArray(binding.sourceArray()));
            return;
        }
        if (binding.kind() == OpenClArgumentKind.VECTOR_ARRAY) {
            writeBufferDirect((OpenClBuffer) nativeBuffer, OpenClValuePacker.packVectorArray(binding.sourceArray()));
            return;
        }

        throw new IllegalArgumentException(
                "Unsupported OpenCL upload source type: "
                        + binding.sourceArray().getClass().getName()
                        + "; use supported primitive arrays, vector arrays, struct arrays, or image/sampler wrappers"
                        + "; for packed structs use @GPUStruct[] and for vectors use wrapper arrays like Float2[] or UInt8[]"
                        + "; see docs/Troubleshooting.md"
        );
    }

    protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
        compiledKernel.kernel().setArg(parameterIndex, (OpenClBuffer) nativeBuffer);
    }

    protected void bindLocalArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClLocalBinding binding) {
        checkCl(
                CL10.clSetKernelArg(compiledKernel.kernel().handle(), parameterIndex, binding.byteSize()),
                "clSetKernelArg"
        );
    }

    protected void bindScalarArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClScalarBinding binding) {
        switch (binding.kind()) {
            case INT8 -> checkCl(
                    CL10.clSetKernelArg1b(compiledKernel.kernel().handle(), parameterIndex, (Byte) binding.value()),
                    "clSetKernelArg1b"
            );
            case INT16 -> checkCl(
                    CL10.clSetKernelArg1s(compiledKernel.kernel().handle(), parameterIndex, (Short) binding.value()),
                    "clSetKernelArg1s"
            );
            case INT32 -> compiledKernel.kernel().setArgInt(parameterIndex, (Integer) binding.value());
            case INT64 -> checkCl(
                    CL10.clSetKernelArg1l(compiledKernel.kernel().handle(), parameterIndex, (Long) binding.value()),
                    "clSetKernelArg1l"
            );
            case FLOAT32 -> compiledKernel.kernel().setArgFloat(parameterIndex, (Float) binding.value());
            case FLOAT64 -> checkCl(
                    CL10.clSetKernelArg1d(compiledKernel.kernel().handle(), parameterIndex, (Double) binding.value()),
                    "clSetKernelArg1d"
            );
            case PACKED_VALUE -> {
                ByteBuffer valueBuffer = ((ByteBuffer) binding.value()).duplicate().order(ByteOrder.nativeOrder());
                valueBuffer.clear();
                checkCl(
                        CL10.clSetKernelArg(compiledKernel.kernel().handle(), parameterIndex, valueBuffer),
                        "clSetKernelArg"
                );
            }
            case IMAGE1D, IMAGE1D_ARRAY, IMAGE1D_BUFFER, IMAGE2D, IMAGE2D_MSAA, IMAGE2D_ARRAY, IMAGE3D, SAMPLER ->
                    compiledKernel.kernel().setArgPointer(parameterIndex, (Long) binding.value());
            default -> throw new IllegalArgumentException("Unsupported OpenCL scalar binding kind: " + binding.kind());
        }
    }

    protected void enqueueKernel(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
        long event;
        if (executionConfig.dimensions() == 3) {
            event = enqueue3D(compiledKernel, executionConfig);
        } else if (executionConfig.dimensions() == 2) {
            event = compiledKernel.kernel().enqueue2D(
                    session().queue(),
                    executionConfig.globalX(),
                    executionConfig.globalY(),
                    executionConfig.localX(),
                    executionConfig.localY(),
                    null
            );
        } else {
            event = compiledKernel.kernel().enqueue1D(
                    session().queue(),
                    executionConfig.globalX(),
                    executionConfig.localX(),
                    null
            );
        }
        try {
            OpenClEvents.waitFor(event);
        } finally {
            OpenClEvents.release(event);
        }
        session().queue().finish();
    }

    private long enqueue3D(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer globalWorkSize = stack.mallocPointer(3);
            globalWorkSize.put(0, executionConfig.globalX());
            globalWorkSize.put(1, executionConfig.globalY());
            globalWorkSize.put(2, executionConfig.globalZ());

            PointerBuffer localWorkSize = null;
            if (executionConfig.localX() > 0L || executionConfig.localY() > 0L || executionConfig.localZ() > 0L) {
                localWorkSize = stack.mallocPointer(3);
                localWorkSize.put(0, executionConfig.localX());
                localWorkSize.put(1, executionConfig.localY());
                localWorkSize.put(2, executionConfig.localZ());
            }

            PointerBuffer event = stack.mallocPointer(1);
            checkCl(
                    CL10.clEnqueueNDRangeKernel(
                            session().queue().handle(),
                            compiledKernel.kernel().handle(),
                            3,
                            null,
                            globalWorkSize,
                            localWorkSize,
                            null,
                            event
                    ),
                    "clEnqueueNDRangeKernel"
            );
            return event.get(0);
        }
    }

    protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
        if (binding.sourceArray() instanceof byte[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length);
            session().queue().readBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            buffer.position(0);
            buffer.get(values);
            return;
        }
        if (binding.sourceArray() instanceof short[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length * Short.BYTES);
            readBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            buffer.position(0);
            for (int i = 0; i < values.length; i++) {
                values[i] = buffer.getShort();
            }
            return;
        }
        if (binding.sourceArray() instanceof int[] values) {
            IntBuffer buffer = allocateIntBuffer(values.length);
            session().queue().readBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            buffer.position(0);
            buffer.get(values);
            return;
        }
        if (binding.sourceArray() instanceof long[] values) {
            ByteBuffer buffer = allocateByteBuffer(values.length * Long.BYTES);
            readBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            buffer.position(0);
            for (int i = 0; i < values.length; i++) {
                values[i] = buffer.getLong();
            }
            return;
        }
        if (binding.sourceArray() instanceof float[] values) {
            FloatBuffer buffer = allocateFloatBuffer(values.length);
            session().queue().readBuffer((OpenClBuffer) nativeBuffer, true, 0L, buffer, null, null);
            buffer.position(0);
            buffer.get(values);
            return;
        }
        if (binding.sourceArray() instanceof double[] values) {
            DoubleBuffer buffer = allocateDoubleBuffer(values.length);
            readWriteBufferDirect((OpenClBuffer) nativeBuffer, buffer, false);
            buffer.position(0);
            buffer.get(values);
            return;
        }
        if (binding.kind() == OpenClArgumentKind.STRUCT_ARRAY) {
            ByteBuffer buffer = allocateByteBuffer(OpenClValuePacker.structArrayByteSize(binding.sourceArray()));
            readBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            OpenClValuePacker.unpackStructArray(buffer, binding.sourceArray());
            return;
        }
        if (binding.kind() == OpenClArgumentKind.VECTOR_ARRAY) {
            ByteBuffer buffer = allocateByteBuffer(OpenClValuePacker.vectorArrayByteSize(binding.sourceArray()));
            readBufferDirect((OpenClBuffer) nativeBuffer, buffer);
            OpenClValuePacker.unpackVectorArray(buffer, binding.sourceArray());
            return;
        }

        throw new IllegalArgumentException(
                "Unsupported OpenCL readback target type: "
                        + binding.sourceArray().getClass().getName()
                        + "; use supported primitive arrays, vector arrays, struct arrays, or image wrappers"
                        + "; for packed structs use @GPUStruct[] and for vectors use wrapper arrays like Float2[] or UInt8[]"
                        + "; see docs/Troubleshooting.md"
        );
    }

    /**
     * Closes resources owned by this backend instance.
     *
     * <p>In {@link CacheMode#INSTANCE}, this releases compiled kernels, transient native buffers, the OpenCL session,
     * and cached capabilities for the instance.
     *
     * <p>In {@link CacheMode#SHARED}, instance-local transient buffers are released, but globally shared compiled
     * kernels and session state stay alive until {@link #shutdownSharedCache()} is called.
     */
    @Override
    public void close() {
        publishLifecycleEvent(
                GpuRuntimeLifecycleEventKind.RUNTIME_SHUTDOWN_STARTED,
                "OpenCL runtime shutdown started",
                runtimeStateFields("started", null)
        );
        try {
            if (cacheMode == CacheMode.INSTANCE) {
                compiledKernels.values().forEach(OpenClCompiledKernel::close);
                compiledKernels.clear();
            }

            nativeBuffers.values().forEach(value -> {
                if (value instanceof AutoCloseable closeable) {
                    try {
                        closeable.close();
                    } catch (Exception exception) {
                        throw new RuntimeException("Failed to close OpenCL device buffer", exception);
                    }
                }
            });
            nativeBuffers.clear();
            bufferRegistry.clear();

            if (cacheMode == CacheMode.SHARED) {
                publishLifecycleEvent(
                        GpuRuntimeLifecycleEventKind.RUNTIME_SHUTDOWN_COMPLETED,
                        "OpenCL runtime shutdown completed",
                        runtimeStateFields("succeeded", null)
                );
                return;
            }

            OpenClRuntimeSession currentSession = session;
            session = null;
            capabilities = null;
            if (currentSession != null) {
                currentSession.close();
            }
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.RUNTIME_SHUTDOWN_COMPLETED,
                    "OpenCL runtime shutdown completed",
                    runtimeStateFields("succeeded", null)
            );
        } catch (RuntimeException exception) {
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.RUNTIME_SHUTDOWN_COMPLETED,
                    "OpenCL runtime shutdown failed",
                    runtimeStateFields("failed", exception)
            );
            throw exception;
        }
    }

    int cacheSize() {
        return compiledKernelCache().size();
    }

    int bufferCacheSize() {
        return bufferRegistry.cacheSize();
    }

    private OpenClRuntimeSession session() {
        if (cacheMode == CacheMode.SHARED) {
            OpenClRuntimeSession current = sharedSession;
            if (current != null) {
                return current;
            }

            synchronized (SHARED_RUNTIME_LOCK) {
                current = sharedSession;
                if (current == null) {
                    current = createSessionChecked();
                    sharedSession = current;
                }
            }

            return current;
        }

        OpenClRuntimeSession current = session;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            current = session;
            if (current == null) {
                current = createSessionChecked();
                session = current;
            }
        }

        return current;
    }

    private OpenClRuntimeSession createSessionChecked() {
        try {
            OpenClSessionSelectionRequest selectionRequest = sessionSelectionRequest.get();
            OpenClRuntimeSession runtimeSession = selectionRequest == null
                    ? createSession()
                    : createSession(
                            selectionRequest.descriptor(),
                            selectionRequest.compileOptions(),
                            selectionRequest.irGpuArtifact()
                    );
            sessionCreationCount.incrementAndGet();
            return runtimeSession;
        } catch (UnsatisfiedLinkError | IllegalStateException exception) {
            OpenClSessionSelectionRequest selectionRequest = sessionSelectionRequest.get();
            throw new GpuRuntimeBackendUnavailableException(
                    "OpenCL runtime is unavailable: "
                            + exception.getMessage()
                            + "; configure a fallback backend or use GpuRuntime.trySelect(...) when GPU execution is optional",
                    selectionRequest == null
                            ? GpuRuntimeDiagnosticContext.unknown()
                            : selectionRequest.diagnosticContext(),
                    exception
            );
        }
    }

    private Map<GpuRuntimeCompileCacheKey, OpenClCompiledKernel> compiledKernelCache() {
        return cacheMode == CacheMode.SHARED ? SHARED_COMPILED_KERNELS : compiledKernels;
    }

    private Object resolveNativeBuffer(OpenClPreparedBufferBinding binding) {
        Object existing = nativeBuffers.get(binding.handle().handleId());
        if (existing != null) {
            return existing;
        }

        return nativeBuffers.computeIfAbsent(
                binding.handle().handleId(),
                ignored -> {
                    deviceBufferCreationCount.incrementAndGet();
                    return createDeviceBuffer(binding.binding());
                }
        );
    }

    private void validateInvocationPreconditions(GpuKernelInvocation invocation, OpenClExecutionPlan plan) {
        if (invocation.executionConfig() != null) {
            return;
        }
        resolvePlannedGlobalWorkSize(invocation.descriptor().kernelName(), plan.bufferBindings());
    }

    private void validateCapabilitySupport(GpuKernelDescriptor descriptor, OpenClExecutionPlan plan) {
        OpenClRuntimeCapabilities capabilities = runtimeCapabilities();
        long requestedLocalMemoryBytes = requestedLocalMemoryBytes(plan);
        boolean usesDouble = usesDoublePrecision(descriptor);
        boolean usesImages = usesImages(descriptor);
        boolean usesImage3dWrites = usesImage3dWrites(descriptor);
        boolean usesAtomics = usesAtomics(descriptor);
        if (usesDouble && !capabilities.supportsDoublePrecision()) {
            throw new UnsupportedOperationException(
                    "OpenCL capability precheck failed for kernel "
                            + descriptor.kernelName()
                            + ": device "
                            + capabilities.deviceLabel()
                            + " does not advertise fp64 support, but the kernel uses double precision"
                            + "; gate this kernel behind a capability check or provide a float/fallback path"
            );
        }
        if (usesImages && !capabilities.supportsImages()) {
            throw new UnsupportedOperationException(
                    "OpenCL capability precheck failed for kernel "
                            + descriptor.kernelName()
                            + ": device "
                            + capabilities.deviceLabel()
                            + " does not support OpenCL images, but the kernel requires image/sampler parameters"
                            + "; use buffer-backed kernels on this device or switch to a backend/device with image support"
            );
        }
        if (usesImage3dWrites && !capabilities.supportsImage3dWrites()) {
            throw new UnsupportedOperationException(
                    "OpenCL capability precheck failed for kernel "
                            + descriptor.kernelName()
                            + ": device "
                            + capabilities.deviceLabel()
                            + " does not support 3D image writes required by the kernel"
                            + "; fall back to 2D/buffer workflows or select a device with 3D image write support"
            );
        }
        if (usesAtomics && !capabilities.supportsAtomics()) {
            throw new UnsupportedOperationException(
                    "OpenCL capability precheck failed for kernel "
                            + descriptor.kernelName()
                            + ": device "
                            + capabilities.deviceLabel()
                            + " does not advertise int32 atomic support required by the kernel"
                            + "; avoid GPU.atomic_* on this device or select a backend/device with atomics support"
            );
        }
        if (requestedLocalMemoryBytes > capabilities.localMemoryBytes()) {
            throw new UnsupportedOperationException(
                    "OpenCL capability precheck failed for kernel "
                            + descriptor.kernelName()
                            + ": requested "
                            + requestedLocalMemoryBytes
                            + " bytes of local memory, but device "
                            + capabilities.deviceLabel()
                            + " exposes only "
                            + capabilities.localMemoryBytes()
                            + " bytes"
                            + "; reduce the local scratch size or split the kernel into smaller work-group memory slices"
            );
        }
    }

    private void validateCompileOptions(GpuRuntimeCompileOptions compileOptions) {
        if (compileOptions == null) {
            return;
        }
        if (compileOptions.backendTarget() != GpuBackendTarget.OPENCL && compileOptions.backendTarget() != GpuBackendTarget.UNKNOWN) {
            throw new IllegalArgumentException(
                    "OpenCL backend cannot use compile options for backend "
                            + compileOptions.backendTarget()
                            + "; pass GpuBackendTarget.OPENCL or select a matching runtime backend"
            );
        }
        if (compileOptions.backendOptions().backendTarget() != GpuBackendTarget.OPENCL
                && compileOptions.backendOptions().backendTarget() != GpuBackendTarget.UNKNOWN) {
            throw new IllegalArgumentException(
                    "OpenCL backend cannot use structured compile options for backend "
                            + compileOptions.backendOptions().backendTarget()
                            + "; pass OpenCL backend options or select a matching runtime backend"
            );
        }
        String sourceSelection = compileOptions.backendOptions()
                .properties()
                .get(GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_PROPERTY);
        if (sourceSelection != null
                && !GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_DESCRIPTOR.equals(sourceSelection)
                && !GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_IRGPU.equals(sourceSelection)) {
            throw new IllegalArgumentException(
                    "Unsupported OpenCL source selection compile option '"
                            + sourceSelection
                            + "'; supported values are '"
                            + GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_DESCRIPTOR
                            + "' and '"
                            + GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_IRGPU
                            + "'"
            );
        }
        String productionSourceSwitching = compileOptions.backendOptions()
                .properties()
                .get(GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_PROPERTY);
        if (productionSourceSwitching != null
                && !GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_DISABLED.equals(productionSourceSwitching)
                && !GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_ENABLED.equals(productionSourceSwitching)) {
            throw new IllegalArgumentException(
                    "Unsupported OpenCL production source switching compile option '"
                            + productionSourceSwitching
                            + "'; supported values are '"
                            + GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_DISABLED
                            + "' and '"
                            + GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_ENABLED
                            + "'"
            );
        }
        String optimizerSelection = compileOptions.backendOptions()
                .properties()
                .get(GpuBackendCompileOptions.RUNTIME_IR_OPTIMIZER_SELECTION_PROPERTY);
        if (optimizerSelection != null
                && !GpuBackendCompileOptions.RUNTIME_IR_OPTIMIZER_SELECTION_REVIEW_ONLY.equals(optimizerSelection)
                && !GpuBackendCompileOptions.RUNTIME_IR_OPTIMIZER_SELECTION_EXPERIMENTAL_APPLY.equals(optimizerSelection)) {
            throw new IllegalArgumentException(
                    "Unsupported runtime IR optimizer selection compile option '"
                            + optimizerSelection
                            + "'; supported values are '"
                            + GpuBackendCompileOptions.RUNTIME_IR_OPTIMIZER_SELECTION_REVIEW_ONLY
                            + "' and '"
                            + GpuBackendCompileOptions.RUNTIME_IR_OPTIMIZER_SELECTION_EXPERIMENTAL_APPLY
                            + "'"
            );
        }
        OpenClCompileOptionValidator.toBuildOptions(compileOptions.compileArgs());
    }

    private OpenClCompiledKernel compileKernelChecked(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact
    ) {
        return compileKernelChecked(
                compileRequest,
                moduleArtifact,
                compileSnapshotWithSourcePromotion(
                        compileRequest,
                        moduleArtifact,
                        GpuRuntimeEquivalenceEvidence.notRun(
                                compileRequest,
                                "runtime equivalence execution has not been wired for this compile request"
                        ),
                        GpuRuntimeFallbackEvidence.none()
                )
        );
    }

    private GpuRuntimeCompileArtifactSnapshot compileSnapshotWithSourcePromotion(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact,
            GpuRuntimeEquivalenceEvidence runtimeEquivalenceEvidence,
            GpuRuntimeFallbackEvidence fallbackEvidence
    ) {
        GpuBackendSourcePromotionGate promotionGate = backendSourcePromotionGate(
                compileRequest,
                moduleArtifact,
                runtimeEquivalenceEvidence,
                fallbackEvidence
        );
        return withRuntimeDeviceSelection(
                GpuRuntimeCompileArtifactSnapshot.from(compileRequest, compileRequest, moduleArtifact).withBackendSourceState(
                        promotionGate,
                        backendSourceSwitchingDecision(
                                compileRequest,
                                moduleArtifact,
                                promotionGate
                        )
                )
        );
    }

    private void validateActiveSessionSelection(
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileOptions compileOptions,
            Optional<IrGpuArtifact> irGpuArtifact
    ) {
        Optional<GpuRuntimeDeviceSelection> activeSelection = runtimeDeviceSelection();
        if (activeSelection.isEmpty()) {
            return;
        }
        GpuRuntimeDeviceSelection current = activeSelection.orElseThrow();
        List<GpuRuntimeDeviceProfile> candidates = current.rankedCandidates().stream()
                .map(net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceCandidateRanking::profile)
                .toList();
        GpuRuntimeDeviceSelection requested = devicePolicyRegistry.select(new net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyContext(
                descriptor,
                compileOptions,
                candidates,
                irGpuArtifact
        ));
        GpuRuntimeDeviceProfile requestedProfile = requested.selectedDevice().orElseThrow(() ->
                new GpuRuntimeDeviceSelectionException(
                        "OpenCL device policy rejected the active runtime session for kernel "
                                + descriptor.kernelName()
                                + ": "
                                + requested.firstBlocker()
                                + (requested.diagnostics().isEmpty()
                                ? ""
                                : "; " + String.join("; ", requested.diagnostics())),
                        requested
                )
        );
        GpuRuntimeDeviceProfile activeProfile = current.selectedDevice().orElseThrow(() ->
                new GpuRuntimeDeviceSelectionException(
                        "Active OpenCL session has no selected device profile",
                        current
                )
        );
        String requestedKey = net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyContext.deviceKey(requestedProfile);
        String activeKey = net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyContext.deviceKey(activeProfile);
        if (!activeKey.equals(requestedKey)) {
            throw new GpuRuntimeDeviceSelectionException(
                    "OpenCL device policy selected "
                            + requestedKey
                            + " for kernel "
                            + descriptor.kernelName()
                            + ", but the active runtime session uses "
                            + activeKey
                            + "; create a new runtime scope/backend instance to switch devices",
                    requested
            );
        }
    }

    private GpuRuntimeCompileArtifactSnapshot withRuntimeDeviceSelection(
            GpuRuntimeCompileArtifactSnapshot snapshot
    ) {
        return runtimeDeviceSelection()
                .map(snapshot::withDeviceSelection)
                .orElse(snapshot);
    }

    private GpuRuntimeCompileArtifactSnapshot withRuntimeDeviceSelection(
            GpuRuntimeCompileArtifactSnapshot snapshot,
            Optional<GpuRuntimeMethodVariantSelection> methodVariantSelection
    ) {
        return methodVariantSelection
                .map(GpuRuntimeMethodVariantSelection::deviceSelection)
                .map(snapshot::withDeviceSelection)
                .orElseGet(() -> withRuntimeDeviceSelection(snapshot));
    }

    private GpuBackendSourcePromotionGate backendSourcePromotionGate(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact,
            GpuRuntimeEquivalenceEvidence runtimeEquivalenceEvidence,
            GpuRuntimeFallbackEvidence fallbackEvidence
    ) {
        GpuBackendSourceReconstructionResult reconstruction = OpenClIrGpuSourceReconstructor.INSTANCE.reconstruct(
                compileRequest.irGpuArtifact().orElse(null),
                compileRequest.descriptor().kernelResource(),
                moduleArtifact.source()
        );
        return GpuBackendSourcePromotionGate.evaluate(
                reconstruction,
                runtimeEquivalenceEvidence,
                fallbackEvidence
        );
    }

    private GpuBackendSourceSwitchingDecision backendSourceSwitchingDecision(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact,
            GpuBackendSourcePromotionGate promotionGate
    ) {
        return GpuBackendSourceSwitchingDecision.evaluate(
                GpuRuntimeCompileProvenance.from(compileRequest),
                moduleArtifact,
                reconstructionFromPromotionGate(moduleArtifact, promotionGate),
                promotionGate,
                GpuBackendSourceSwitchingPolicy.from(compileRequest.options().backendOptions()),
                GpuProductionPromotionOperatorAcceptance.evaluate(compileRequest)
        );
    }

    private GpuBackendSourceReconstructionResult reconstructionFromPromotionGate(
            GpuBackendModuleArtifact moduleArtifact,
            GpuBackendSourcePromotionGate promotionGate
    ) {
        if (!promotionGate.reconstructed()) {
            return GpuBackendSourceReconstructionResult.blocked(
                    moduleArtifact.backendTarget(),
                    promotionGate.selectedSource(),
                    promotionGate.payloadFormat(),
                    promotionGate.runtimeLoadMode(),
                    promotionGate.reconstructionBlockers(),
                    promotionGate.reconstructionDiagnostics()
            );
        }
        return GpuBackendSourceReconstructionResult.reconstructedSource(
                moduleArtifact.backendTarget(),
                moduleArtifact.source(),
                promotionGate.selectedSource(),
                promotionGate.payloadFormat(),
                promotionGate.runtimeLoadMode(),
                promotionGate.reconstructionDiagnostics()
        );
    }

    private OpenClCompiledKernel compileKernelChecked(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot
    ) {
        return compileKernelChecked(
                compileRequest,
                moduleArtifact,
                artifactSnapshot,
                GpuRuntimeDiagnosticContext.fromSnapshot(compileRequest.descriptor(), artifactSnapshot)
        );
    }

    private OpenClCompiledKernel compileKernelChecked(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            GpuRuntimeDiagnosticContext diagnosticContext
    ) {
        GpuKernelDescriptor descriptor = compileRequest.descriptor();
        publishLifecycleEvent(
                GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_STARTED,
                compileRequest,
                "OpenCL backend compilation started",
                backendCompilationFields(compileRequest, moduleArtifact, artifactSnapshot, "started", null, null)
        );
        try {
            compileCount.incrementAndGet();
            OpenClCompiledKernel compiledKernel = compileBackendKernel(compileRequest, moduleArtifact);
            OpenClCompiledKernel mergedKernel = compiledKernel.withArtifactSnapshot(mergeCompilerLog(
                    artifactSnapshot,
                    compiledKernel.artifactSnapshot()
            ));
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_COMPLETED,
                    compileRequest,
                    "OpenCL backend compilation completed",
                    backendCompilationFields(compileRequest, moduleArtifact, mergedKernel.artifactSnapshot(), "succeeded", mergedKernel.cacheKey(), null)
            );
            return mergedKernel;
        } catch (RuntimeException exception) {
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_COMPLETED,
                    compileRequest,
                    "OpenCL backend compilation failed",
                    backendCompilationFields(compileRequest, moduleArtifact, artifactSnapshot, "failed", null, exception)
            );
            if (exception instanceof GpuRuntimeException runtimeException) {
                throw runtimeException;
            }
            GpuRuntimeDiagnosticContext failureContext = GpuRuntimeDiagnosticContext.fromSnapshot(
                    descriptor,
                    artifactSnapshot
            ).withCallSite(diagnosticContext.callSite());
            throw OpenClFailureFormatter.buildFailure(compileRequest, failureContext, exception);
        }
    }

    private boolean overridesCompileRequestHook() {
        try {
            Method method = getClass().getDeclaredMethod("compileKernel", GpuRuntimeCompileRequest.class);
            return method.getDeclaringClass() != OpenClGpuRuntimeBackend.class;
        } catch (NoSuchMethodException exception) {
            return false;
        }
    }

    private OpenClCompiledKernel compileKernelChecked(GpuRuntimeCompileRequest compileRequest) {
        GpuKernelDescriptor descriptor = compileRequest.descriptor();
        publishLifecycleEvent(
                GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_STARTED,
                compileRequest,
                "OpenCL backend compilation started",
                backendCompilationFields(compileRequest, null, null, "started", null, null)
        );
        try {
            compileCount.incrementAndGet();
            OpenClCompiledKernel compiledKernel = compileBackendKernel(compileRequest, null);
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_COMPLETED,
                    compileRequest,
                    "OpenCL backend compilation completed",
                    backendCompilationFields(compileRequest, null, compiledKernel.artifactSnapshot(), "succeeded", compiledKernel.cacheKey(), null)
            );
            return compiledKernel;
        } catch (RuntimeException exception) {
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_COMPLETED,
                    compileRequest,
                    "OpenCL backend compilation failed",
                    backendCompilationFields(compileRequest, null, null, "failed", null, exception)
            );
            if (exception instanceof GpuRuntimeException runtimeException) {
                throw runtimeException;
            }
            throw OpenClFailureFormatter.buildFailure(compileRequest, exception);
        }
    }

    private GpuRuntimeCompileRequest buildCompileRequest(GpuKernelDescriptor descriptor) {
        return GpuRuntimeCompileRequestSupport.fromDescriptor(
                descriptor,
                GpuRuntimeCompileOptions.defaults(backendTarget()),
                compileDeviceProfile()
        );
    }

    private GpuRuntimeCompileRequest buildCompileRequest(GpuKernelInvocation invocation) {
        return GpuRuntimeCompileRequestSupport.fromInvocation(
                invocation,
                compileDeviceProfile()
        );
    }

    private GpuRuntimeCompileRequest applyProductionPromotionDecision(GpuRuntimeCompileRequest compileRequest) {
        Optional<GpuProductionPromotionDecision> decision = loadProductionPromotionDecision();
        if (decision.isEmpty()) {
            return compileRequest;
        }
        return compileRequest.withOptions(compileRequest.options().withProductionPromotionDecision(decision.get()));
    }

    private Optional<GpuProductionPromotionDecision> loadProductionPromotionDecision() {
        String path = System.getProperty(PRODUCTION_PROMOTION_EXPLAINABILITY_FILE_PROPERTY);
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(GpuProductionPromotionDecision.fromExplainabilityFileOrDiagnosticOnly(
                java.nio.file.Paths.get(path)
        ));
    }

    protected GpuRuntimeDeviceProfile compileDeviceProfile() {
        String backendName = cacheMode == CacheMode.SHARED ? "OpenCL (shared cache)" : "OpenCL";
        OpenClRuntimeSession currentSession = cacheMode == CacheMode.SHARED ? sharedSession : session;
        if (currentSession != null) {
            return currentSession.deviceProfile().withBackendName(backendName);
        }
        Optional<GpuRuntimeDeviceProfile> preselectedDevice = preselectedDeviceSelection()
                .flatMap(GpuRuntimeDeviceSelection::selectedDevice);
        if (preselectedDevice.isPresent()) {
            return preselectedDevice.orElseThrow().withBackendName(backendName);
        }
        OpenClRuntimeCapabilities runtimeCapabilities = runtimeCapabilities();
        return GpuRuntimeDeviceProfile.openCl(
                backendName,
                "unknown",
                runtimeCapabilities.deviceLabel(),
                runtimeCapabilities.vendor(),
                runtimeCapabilities.driverVersion(),
                runtimeCapabilities.deviceVersion(),
                runtimeCapabilities.compilerVersion(),
                "unknown",
                "unknown",
                GpuDeviceClassTarget.UNKNOWN,
                runtimeCapabilities.computeUnits(),
                -1L,
                runtimeCapabilities.localMemoryBytes(),
                runtimeCapabilities.maxWorkGroupSize(),
                runtimeCapabilities.preferredVectorWidthFloat(),
                false,
                runtimeCapabilities.supportsDoublePrecision(),
                runtimeCapabilities.supportsImages(),
                runtimeCapabilities.supportsImage3dWrites(),
                runtimeCapabilities.supportsAtomics(),
                runtimeCapabilities.supportsSubgroups()
        );
    }

    private static GpuRuntimeCompileArtifactSnapshot mergeCompilerLog(
            GpuRuntimeCompileArtifactSnapshot requestedSnapshot,
            GpuRuntimeCompileArtifactSnapshot compiledSnapshot
    ) {
        if (compiledSnapshot == null) {
            return requestedSnapshot;
        }
        GpuRuntimeCompileArtifactSnapshot merged = requestedSnapshot;
        if (!compiledSnapshot.compileLog().isBlank()) {
            merged = merged.withCompileLog(compiledSnapshot.compileLog());
        }
        if (!compiledSnapshot.binaryArtifacts().isEmpty()) {
            merged = merged.withBinaryArtifacts(compiledSnapshot.binaryArtifacts());
        }
        return merged;
    }

    private boolean overridesLegacyCreateSessionHook() {
        Class<?> type = getClass();
        while (type != null && type != OpenClGpuRuntimeBackend.class) {
            try {
                type.getDeclaredMethod("createSession");
                return true;
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            }
        }
        return false;
    }

    private void executeKernelChecked(OpenClPreparedExecution execution) {
        executeKernelChecked(
                execution,
                GpuRuntimeDiagnosticContext.fromSnapshot(
                        execution.compiledKernel().descriptor(),
                        execution.compiledKernel().artifactSnapshot()
                )
        );
    }

    private void executeKernelChecked(
            OpenClPreparedExecution execution,
            GpuRuntimeDiagnosticContext diagnosticContext
    ) {
        GpuExecutionConfig executionConfig = null;
        try {
            executionConfig = resolveExecutionConfig(execution);
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.INVOCATION_STARTED,
                    execution.compiledKernel().descriptor(),
                    execution.compiledKernel().artifactSnapshot().compileProvenance().optimizationProfile(),
                    "OpenCL kernel invocation started",
                    invocationFields(execution, executionConfig, "started", null)
            );
            OpenClKernelLaunchAdvisory launchAdvisory = OpenClKernelLaunchAdvisory.evaluate(
                    execution.compiledKernel(),
                    executionConfig
            );
            validateKernelWorkGroupSize(execution, diagnosticContext, launchAdvisory);
            dumpRuntimeLaunchAdvisory(execution.compiledKernel().artifactSnapshot(), launchAdvisory);
            kernelInvoker().invoke(execution, executionConfig);
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.INVOCATION_COMPLETED,
                    execution.compiledKernel().descriptor(),
                    execution.compiledKernel().artifactSnapshot().compileProvenance().optimizationProfile(),
                    "OpenCL kernel invocation completed",
                    invocationFields(execution, executionConfig, "succeeded", null)
            );
        } catch (RuntimeException exception) {
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.INVOCATION_COMPLETED,
                    execution.compiledKernel().descriptor(),
                    execution.compiledKernel().artifactSnapshot().compileProvenance().optimizationProfile(),
                    "OpenCL kernel invocation failed",
                    invocationFields(execution, executionConfig, "failed", exception)
            );
            if (exception instanceof GpuRuntimeException runtimeException) {
                throw runtimeException;
            }
            GpuRuntimeDiagnosticContext failureContext = GpuRuntimeDiagnosticContext.fromSnapshot(
                    execution.compiledKernel().descriptor(),
                    execution.compiledKernel().artifactSnapshot()
            ).withCallSite(diagnosticContext.callSite());
            throw OpenClFailureFormatter.executionFailure(
                    execution.compiledKernel(),
                    failureContext,
                    exception
            );
        }
    }

    private void dumpBackendSourcePromotionWorkloadGate(GpuRuntimeCompileArtifactSnapshot artifactSnapshot) {
        String outputPath = System.getProperty(BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE_FILE_PROPERTY);
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        try {
            GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(artifactSnapshot);
            java.nio.file.Path path = java.nio.file.Paths.get(outputPath);
            java.nio.file.Path parent = path.getParent();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }
            String gateProperties = GpuBackendSourcePromotionWorkloadGateFormatter.merge(
                    path,
                    artifactSnapshot.backendModuleArtifact().resource(),
                    dump.artifact(GpuPromotionArtifactRegistry.BACKEND_SOURCE_PROMOTION_GATE),
                    dump.artifact(GpuPromotionArtifactRegistry.BACKEND_SOURCE_SWITCHING_DECISION),
                    dump.artifact(GpuPromotionArtifactRegistry.RUNTIME_IR_HANDOFF),
                    dump.artifact(GpuPromotionArtifactRegistry.RUNTIME_PRODUCTION_MUTATION_SAFETY),
                    dump.artifact(GpuPromotionArtifactRegistry.I3_READINESS_SUMMARY),
                    dump.artifact(GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_DRIFT),
                    dump.artifact(GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD),
                    dump.artifact(GpuRuntimeCompileArtifactDumper.RUNTIME_EXTENSION_PARTICIPATION_ARTIFACT)
            );
            java.nio.file.Files.writeString(path, gateProperties, java.nio.charset.StandardCharsets.UTF_8);
        } catch (RuntimeException | java.io.IOException exception) {
            throw new IllegalStateException("Failed to write OpenCL backend source workload promotion gate", exception);
        }
    }

    private void validateKernelWorkGroupSize(
            OpenClPreparedExecution execution,
            GpuRuntimeDiagnosticContext diagnosticContext,
            OpenClKernelLaunchAdvisory launchAdvisory
    ) {
        long requestedSize = launchAdvisory.requestedLocalWorkGroupSize();
        long kernelLimit = launchAdvisory.kernelMaxWorkGroupSize();
        if (requestedSize <= 0L || kernelLimit < 0L || requestedSize <= kernelLimit) {
            return;
        }

        clearRuntimeLaunchAdvisory(execution.compiledKernel().artifactSnapshot());
        GpuRuntimeDiagnosticContext failureContext = GpuRuntimeDiagnosticContext.fromSnapshot(
                execution.compiledKernel().descriptor(),
                execution.compiledKernel().artifactSnapshot()
        ).withCallSite(diagnosticContext.callSite());
        throw new GpuRuntimeCapabilityException(
                "OpenCL kernel work-group validation failed for kernel "
                        + execution.compiledKernel().descriptor().kernelName()
                        + ": requested local work-group size "
                        + requestedSize
                        + " ("
                        + launchAdvisory.requestedLocalWorkGroupShape()
                        + "), but the compiled kernel limit is "
                        + kernelLimit
                        + "; reduce the explicit local size or leave it unspecified so the OpenCL driver can choose",
                failureContext,
                null
        );
    }

    private void dumpRuntimeLaunchAdvisory(
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            OpenClKernelLaunchAdvisory launchAdvisory
    ) {
        String outputPath = System.getProperty(BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE_FILE_PROPERTY);
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        try {
            java.nio.file.Path gatePath = java.nio.file.Paths.get(outputPath);
            java.nio.file.Path reportDirectory = gatePath.getParent();
            if (reportDirectory == null) {
                return;
            }
            java.nio.file.Path artifactDirectory = runtimeCompileArtifactDirectory(reportDirectory, artifactSnapshot);
            java.nio.file.Files.createDirectories(artifactDirectory);
            java.nio.file.Files.writeString(
                    artifactDirectory.resolve(OpenClKernelLaunchAdvisory.ARTIFACT_FILE_NAME),
                    launchAdvisory.toProperties(),
                    java.nio.charset.StandardCharsets.UTF_8
            );
        } catch (RuntimeException | java.io.IOException ignored) {
            // Launch advisories must never change the execution result.
        }
    }

    private void clearRuntimeLaunchAdvisory(GpuRuntimeCompileArtifactSnapshot artifactSnapshot) {
        String outputPath = System.getProperty(BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE_FILE_PROPERTY);
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        try {
            java.nio.file.Path gatePath = java.nio.file.Paths.get(outputPath);
            java.nio.file.Path reportDirectory = gatePath.getParent();
            if (reportDirectory == null) {
                return;
            }
            java.nio.file.Files.deleteIfExists(
                    runtimeCompileArtifactDirectory(reportDirectory, artifactSnapshot)
                            .resolve(OpenClKernelLaunchAdvisory.ARTIFACT_FILE_NAME)
            );
        } catch (RuntimeException | java.io.IOException ignored) {
            // Stale-advisory cleanup must never replace the structured validation failure.
        }
    }

    private void dumpRuntimeCompileArtifacts(GpuRuntimeCompileArtifactSnapshot artifactSnapshot) {
        if (!runtimeCompileArtifactsConfigured()) {
            return;
        }
        java.util.List<java.nio.file.Path> artifactDirectories = runtimeCompileArtifactDirectories(artifactSnapshot);
        GpuRuntimeArtifactDumpSummary plannedSummary = GpuRuntimeArtifactDumpSummary.planned(artifactDirectories.size());
        publishLifecycleEvent(
                GpuRuntimeLifecycleEventKind.ARTIFACT_DUMP_STARTED,
                artifactSnapshot,
                "OpenCL runtime artifact dump started",
                artifactDumpFields(artifactSnapshot, plannedSummary, "started", null)
        );
        try {
            GpuRuntimeArtifactDumpSummary dumpSummary = writeRuntimeCompileArtifactsIfConfigured(
                    artifactSnapshot,
                    artifactDirectories
            );
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.ARTIFACT_DUMP_COMPLETED,
                    artifactSnapshot,
                    "OpenCL runtime artifact dump completed",
                    artifactDumpFields(artifactSnapshot, dumpSummary, "succeeded", null)
            );
        } catch (RuntimeException exception) {
            publishLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.ARTIFACT_DUMP_COMPLETED,
                    artifactSnapshot,
                    "OpenCL runtime artifact dump failed",
                    artifactDumpFields(artifactSnapshot, plannedSummary, "failed", exception)
            );
            throw exception;
        }
    }

    private static boolean runtimeCompileArtifactsConfigured() {
        return propertyConfigured(RUNTIME_COMPILE_ARTIFACT_DIRECTORY_PROPERTY)
                || propertyConfigured(BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE_FILE_PROPERTY);
    }

    private static boolean propertyConfigured(String property) {
        String value = System.getProperty(property);
        return value != null && !value.isBlank();
    }

    static GpuRuntimeArtifactDumpSummary writeRuntimeCompileArtifactsIfConfigured(
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot
    ) {
        java.util.List<java.nio.file.Path> artifactDirectories = runtimeCompileArtifactDirectories(artifactSnapshot);
        return writeRuntimeCompileArtifactsIfConfigured(artifactSnapshot, artifactDirectories);
    }

    private static GpuRuntimeArtifactDumpSummary writeRuntimeCompileArtifactsIfConfigured(
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            java.util.List<java.nio.file.Path> artifactDirectories
    ) {
        java.util.List<java.nio.file.Path> directories = artifactDirectories == null
                ? java.util.List.of()
                : java.util.List.copyOf(artifactDirectories);
        if (directories.isEmpty()) {
            return GpuRuntimeArtifactDumpSummary.planned(0);
        }
        try {
            GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(artifactSnapshot);
            for (java.nio.file.Path artifactDirectory : directories) {
                java.nio.file.Files.createDirectories(artifactDirectory);
                writeRuntimeCompileArtifactDump(artifactDirectory, dump);
            }
            return GpuRuntimeArtifactDumpSummary.from(dump, directories.size());
        } catch (RuntimeException | java.io.IOException exception) {
            throw new IllegalStateException("Failed to write OpenCL runtime compile artifacts", exception);
        }
    }

    private static java.util.List<java.nio.file.Path> runtimeCompileArtifactDirectories(
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot
    ) {
        java.util.LinkedHashSet<java.nio.file.Path> directories = new java.util.LinkedHashSet<>();
        String explicitArtifactRoot = System.getProperty(RUNTIME_COMPILE_ARTIFACT_DIRECTORY_PROPERTY);
        if (explicitArtifactRoot != null && !explicitArtifactRoot.isBlank()) {
            directories.add(runtimeCompileArtifactDirectoryFromRoot(
                    java.nio.file.Paths.get(explicitArtifactRoot),
                    artifactSnapshot
            ));
        }
        String outputPath = System.getProperty(BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE_FILE_PROPERTY);
        if (outputPath != null && !outputPath.isBlank()) {
            java.nio.file.Path gatePath = java.nio.file.Paths.get(outputPath);
            java.nio.file.Path reportDirectory = gatePath.getParent();
            if (reportDirectory != null) {
                directories.add(runtimeCompileArtifactDirectory(reportDirectory, artifactSnapshot));
            }
        }
        return java.util.List.copyOf(directories);
    }

    static void writeRuntimeCompileArtifactDump(
            java.nio.file.Path artifactDirectory,
            GpuRuntimeCompileArtifactDump dump
    ) throws java.io.IOException {
        for (Map.Entry<String, String> artifact : dump.artifacts().entrySet()) {
            java.nio.file.Path artifactPath = runtimeCompileArtifactPath(artifactDirectory, artifact.getKey());
            java.nio.file.Path artifactParent = artifactPath.getParent();
            if (artifactParent != null) {
                java.nio.file.Files.createDirectories(artifactParent);
            }
            java.nio.file.Files.writeString(
                    artifactPath,
                    artifact.getValue(),
                    java.nio.charset.StandardCharsets.UTF_8
            );
        }
        for (net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBinaryArtifact artifact
                : dump.binaryArtifacts().values()) {
            java.nio.file.Path artifactPath = runtimeCompileArtifactPath(artifactDirectory, artifact.name());
            java.nio.file.Path artifactParent = artifactPath.getParent();
            if (artifactParent != null) {
                java.nio.file.Files.createDirectories(artifactParent);
            }
            java.nio.file.Files.write(artifactPath, artifact.content());
        }
        if (!dump.sourceLocations().isEmpty()) {
            java.nio.file.Files.writeString(
                    runtimeCompileArtifactPath(artifactDirectory, "source-locations.txt"),
                    String.join(System.lineSeparator(), dump.sourceLocations()) + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8
            );
        }
    }

    static java.nio.file.Path runtimeCompileArtifactPath(
            java.nio.file.Path artifactDirectory,
            String artifactName
    ) {
        java.nio.file.Path normalizedDirectory = artifactDirectory.toAbsolutePath().normalize();
        java.nio.file.Path artifactPath = normalizedDirectory.resolve(artifactName).normalize();
        if (!artifactPath.startsWith(normalizedDirectory)) {
            throw new IllegalArgumentException("Runtime compile artifact path escapes output directory: " + artifactName);
        }
        return artifactPath;
    }

    private static java.nio.file.Path runtimeCompileArtifactDirectory(
            java.nio.file.Path reportDirectory,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot
    ) {
        return reportDirectory
                .toAbsolutePath()
                .normalize()
                .resolve("runtime-compile-artifacts")
                .resolve(runtimeCompileArtifactDirectoryName(artifactSnapshot))
                .normalize();
    }

    private static java.nio.file.Path runtimeCompileArtifactDirectoryFromRoot(
            java.nio.file.Path artifactRootDirectory,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot
    ) {
        return artifactRootDirectory
                .toAbsolutePath()
                .normalize()
                .resolve(runtimeCompileArtifactDirectoryName(artifactSnapshot));
    }

    private static String runtimeCompileArtifactDirectoryName(GpuRuntimeCompileArtifactSnapshot artifactSnapshot) {
        String resource = artifactSnapshot.backendModuleArtifact().resource();
        String identity = resource == null || resource.isBlank() ? "kernel" : resource;
        String sanitized = identity.replaceAll("[^a-zA-Z0-9._-]+", "_");
        if (sanitized.length() > 96) {
            sanitized = sanitized.substring(sanitized.length() - 96);
        }
        return sanitized + "-" + Integer.toUnsignedString(identity.hashCode(), 16);
    }

    private long requestedLocalMemoryBytes(OpenClExecutionPlan plan) {
        long total = 0L;
        for (OpenClLocalBinding localBinding : plan.localBindings()) {
            total += localBinding.byteSize();
        }
        return total;
    }

    private boolean usesDoublePrecision(GpuKernelDescriptor descriptor) {
        if (DOUBLE_USAGE_PATTERN.matcher(descriptor.kernelSource()).find()) {
            return true;
        }
        for (GpuKernelParameterDescriptor parameterDescriptor : descriptor.parameterDescriptors()) {
            String javaType = parameterDescriptor.javaType();
            if ("double".equals(javaType) || "double[]".equals(javaType)) {
                return true;
            }
            if (javaType != null && (javaType.startsWith("Double") || javaType.contains(".Double"))) {
                return true;
            }
        }
        return false;
    }

    private boolean usesImages(GpuKernelDescriptor descriptor) {
        for (GpuKernelParameterDescriptor parameterDescriptor : descriptor.parameterDescriptors()) {
            String javaType = parameterDescriptor.javaType();
            if (javaType != null && javaType.startsWith("Image")) {
                return true;
            }
            if ("Sampler".equals(javaType) || (javaType != null && javaType.endsWith(".Sampler"))) {
                return true;
            }
        }
        return false;
    }

    private boolean usesImage3dWrites(GpuKernelDescriptor descriptor) {
        if (descriptor.kernelSource().contains("write_only image3d_t")) {
            return true;
        }
        for (GpuKernelParameterDescriptor parameterDescriptor : descriptor.parameterDescriptors()) {
            String javaType = parameterDescriptor.javaType();
            if ("Image3DWriteOnly".equals(javaType) || (javaType != null && javaType.endsWith(".Image3DWriteOnly"))) {
                return true;
            }
        }
        return false;
    }

    private boolean usesAtomics(GpuKernelDescriptor descriptor) {
        return descriptor.kernelSource() != null && descriptor.kernelSource().contains("atomic_");
    }

    private net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig resolveExecutionConfig(OpenClPreparedExecution execution) {
        if (execution.explicitExecutionConfig() != null) {
            return execution.explicitExecutionConfig();
        }
        return net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.oneDimensional(
                resolveGlobalWorkSize(execution.compiledKernel().descriptor().kernelName(), execution.bufferBindings())
        );
    }

    private long resolvePlannedGlobalWorkSize(String kernelName, java.util.List<OpenClBufferBinding> bufferBindings) {
        if (bufferBindings.isEmpty()) {
            throw new UnsupportedOperationException(
                            "OpenCL execution requires at least one buffer argument to derive global work size for kernel "
                                    + kernelName
                                    + "; add at least one array/vector/struct buffer parameter or launch with an explicit GpuExecutionConfig"
            );
        }

        int expectedLength = bufferBindings.get(0).length();
        for (OpenClBufferBinding binding : bufferBindings) {
            if (binding.length() != expectedLength) {
                throw new IllegalArgumentException(
                        "Mismatched GPU array lengths for kernel "
                                + kernelName
                                + ": expected "
                                + expectedLength
                                + " but found "
                                + binding.length()
                                + "; all buffer-style kernel arguments must share the same logical length"
                );
            }
        }

        return expectedLength;
    }

    private long resolveGlobalWorkSize(String kernelName, java.util.List<OpenClPreparedBufferBinding> bufferBindings) {
        if (bufferBindings.isEmpty()) {
            throw new UnsupportedOperationException(
                            "OpenCL execution requires at least one buffer argument to derive global work size for kernel "
                                    + kernelName
                                    + "; add at least one array/vector/struct buffer parameter or launch with an explicit GpuExecutionConfig"
            );
        }

        int expectedLength = bufferBindings.get(0).binding().length();
        for (OpenClPreparedBufferBinding binding : bufferBindings) {
            if (binding.binding().length() != expectedLength) {
                throw new IllegalArgumentException(
                        "Mismatched GPU array lengths for kernel "
                                + kernelName
                                + ": expected "
                                + expectedLength
                                + " but found "
                                + binding.binding().length()
                                + "; all buffer-style kernel arguments must share the same logical length"
                );
            }
        }

        return expectedLength;
    }

    private long bytesFor(OpenClBufferBinding binding) {
        return switch (binding.kind()) {
            case BYTE_ARRAY -> binding.length();
            case SHORT_ARRAY -> (long) binding.length() * Short.BYTES;
            case INT_ARRAY -> (long) binding.length() * Integer.BYTES;
            case LONG_ARRAY -> (long) binding.length() * Long.BYTES;
            case FLOAT_ARRAY -> (long) binding.length() * Float.BYTES;
            case DOUBLE_ARRAY -> (long) binding.length() * Double.BYTES;
            case STRUCT_ARRAY -> OpenClValuePacker.structArrayByteSize(binding.sourceArray());
            case VECTOR_ARRAY -> OpenClValuePacker.vectorArrayByteSize(binding.sourceArray());
            default -> throw new IllegalArgumentException("Unsupported OpenCL buffer kind: " + binding.kind());
        };
    }

    private void writeBufferDirect(OpenClBuffer buffer, ByteBuffer values) {
        checkCl(
                CL10.clEnqueueWriteBuffer(session().queue().handle(), buffer.handle(), true, 0L, values, null, null),
                "clEnqueueWriteBuffer"
        );
        session().queue().finish();
    }

    private void readBufferDirect(OpenClBuffer buffer, ByteBuffer values) {
        checkCl(
                CL10.clEnqueueReadBuffer(session().queue().handle(), buffer.handle(), true, 0L, values, null, null),
                "clEnqueueReadBuffer"
        );
        session().queue().finish();
    }

    private void readWriteBufferDirect(OpenClBuffer buffer, DoubleBuffer values, boolean write) {
        int result = write
                ? CL10.clEnqueueWriteBuffer(session().queue().handle(), buffer.handle(), true, 0L, values, null, null)
                : CL10.clEnqueueReadBuffer(session().queue().handle(), buffer.handle(), true, 0L, values, null, null);
        checkCl(result, write ? "clEnqueueWriteBuffer" : "clEnqueueReadBuffer");
        session().queue().finish();
    }

    private void checkCl(int errorCode, String operation) {
        OpenClException.check(errorCode, operation);
    }

    private ByteBuffer allocateByteBuffer(int sizeBytes) {
        return ByteBuffer.allocateDirect(sizeBytes).order(ByteOrder.nativeOrder());
    }

    private IntBuffer allocateIntBuffer(int length) {
        return allocateByteBuffer(length * Integer.BYTES).asIntBuffer();
    }

    private FloatBuffer allocateFloatBuffer(int length) {
        return allocateByteBuffer(length * Float.BYTES).asFloatBuffer();
    }

    private DoubleBuffer allocateDoubleBuffer(int length) {
        return allocateByteBuffer(length * Double.BYTES).asDoubleBuffer();
    }

    private record OpenClSessionSelectionRequest(
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileOptions compileOptions,
            Optional<IrGpuArtifact> irGpuArtifact,
            GpuRuntimeDiagnosticContext diagnosticContext
    ) {
    }
}
