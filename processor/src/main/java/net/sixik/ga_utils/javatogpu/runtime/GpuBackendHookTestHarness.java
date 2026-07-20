package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hardware-free fixture runner for backend hook authors.
 *
 * <p>The harness creates synthetic backend discovery, lowering, compilation, invocation, and artifact receipts, then
 * executes a {@link GpuBackendHookRegistry} against those receipts. It does not open OpenCL, CUDA, or any other native
 * runtime.</p>
 */
public final class GpuBackendHookTestHarness {

    private final GpuBackendHookRegistry registry;

    private GpuBackendHookTestHarness(GpuBackendHookRegistry registry) {
        this.registry = registry == null ? GpuBackendHookRegistry.empty() : registry;
    }

    public static GpuBackendHookTestHarness empty() {
        return new GpuBackendHookTestHarness(GpuBackendHookRegistry.empty());
    }

    public static GpuBackendHookTestHarness of(Collection<? extends GpuBackendHook> hooks) {
        return new GpuBackendHookTestHarness(GpuBackendHookRegistry.of(hooks));
    }

    public static GpuBackendHookTestHarness of(GpuBackendHookRegistry registry) {
        return new GpuBackendHookTestHarness(registry);
    }

    public static GpuBackendHookTestHarness loadWithServiceLoader() {
        return of(GpuBackendHookRegistry.loadWithServiceLoader());
    }

    public static GpuBackendHookTestHarness loadWithServiceLoader(ClassLoader classLoader) {
        return of(GpuBackendHookRegistry.loadWithServiceLoader(classLoader));
    }

    public GpuBackendHookRegistry registry() {
        return registry;
    }

    public GpuBackendHookTestHarnessReport runSyntheticOpenCl() {
        return runSynthetic(GpuBackendTarget.OPENCL);
    }

    public GpuBackendHookTestHarnessReport runSynthetic(GpuBackendTarget backendTarget) {
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        GpuRuntimeCompileOptions compileOptions = GpuRuntimeCompileOptions.defaults(target);
        GpuRuntimeCompileRequest compileRequest = syntheticCompileRequest(target, compileOptions);
        GpuRuntimeDeviceDiscoveryResult discoveryResult = syntheticDiscoveryResult(target);
        GpuBackendLoweringResult loweringResult = syntheticLoweringResult(target);
        GpuBackendCompilationResult compilationResult = syntheticCompilationResult(loweringResult);
        GpuBackendPreparationResult preparationResult = syntheticPreparationResult(compilationResult);
        GpuBackendInvocationResult invocationResult = syntheticInvocationResult(preparationResult);

        Map<String, String> registryFields = registry.artifactFields("runtime.backend.hookRegistry");
        Map<String, String> authorizationFields = registry.authorizationCatalog(target)
                .artifactFields("runtime.backend.hookAuthorization");
        Map<String, String> discoveryFields = registry.observeDiscovery(
                target,
                compileOptions,
                discoveryResult,
                "runtime.backend.hookExecution.discovery"
        );
        Map<String, String> loweringFields = registry.observeLowering(
                target,
                compileRequest,
                loweringResult,
                "runtime.backend.hookExecution.lowering"
        );
        Map<String, String> compilationFields = registry.observeCompilation(
                target,
                compileRequest,
                compilationResult,
                "runtime.backend.hookExecution.compilation"
        );
        Map<String, String> invocationFields = registry.observeInvocation(
                target,
                compileRequest,
                invocationResult,
                "runtime.backend.hookExecution.invocation"
        );
        Map<String, String> artifactFields = registry.contributeArtifactFields(
                target,
                compileRequest,
                Map.of("status", "succeeded", "runtime.backend.target", target.name()),
                "runtime.backend.hookExecution.artifact"
        );
        return new GpuBackendHookTestHarnessReport(
                target,
                registry.size(),
                registryFields,
                authorizationFields,
                discoveryFields,
                loweringFields,
                compilationFields,
                invocationFields,
                artifactFields
        );
    }

    public static GpuRuntimeCompileRequest syntheticCompileRequest(GpuBackendTarget backendTarget) {
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        return syntheticCompileRequest(target, GpuRuntimeCompileOptions.defaults(target));
    }

    public static GpuRuntimeDeviceDiscoveryResult syntheticDiscoveryResult(GpuBackendTarget backendTarget) {
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        return GpuRuntimeDeviceDiscoveryResult.unavailable(
                target,
                target.name(),
                "synthetic-device-discovery",
                new UnsupportedOperationException("Synthetic hook harness does not open native device discovery")
        );
    }

    public static GpuBackendLoweringResult syntheticLoweringResult(GpuBackendTarget backendTarget) {
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        GpuBackendModuleArtifact moduleArtifact = syntheticModuleArtifact(target);
        return GpuBackendLoweringResult.succeeded(
                moduleArtifact,
                GpuBackendSourceSelectionPlan.descriptorSource(
                        target,
                        moduleArtifact.format(),
                        "synthetic backend hook harness source"
                ),
                List.of("synthetic lowering receipt for backend hook testing")
        );
    }

    public static GpuBackendCompilationResult syntheticCompilationResult(GpuBackendLoweringResult loweringResult) {
        GpuBackendLoweringResult lowering = Objects.requireNonNullElseGet(
                loweringResult,
                () -> syntheticLoweringResult(GpuBackendTarget.UNKNOWN)
        );
        return GpuBackendCompilationResult.succeeded(
                lowering,
                GpuRuntimeBackendCompilationSummary.from(lowering.moduleArtifact(), null, "synthetic-cache-key"),
                "synthetic-cache-key",
                List.of("synthetic compilation receipt for backend hook testing")
        );
    }

    public static GpuBackendPreparationResult syntheticPreparationResult(
            GpuBackendCompilationResult compilationResult
    ) {
        return GpuBackendPreparationResult.prepared(
                compilationResult,
                "synthetic-prepared-kernel",
                GpuRuntimeInvocationBindingSummary.empty(),
                List.of("synthetic preparation receipt for backend hook testing")
        );
    }

    public static GpuBackendInvocationResult syntheticInvocationResult(GpuBackendPreparationResult preparationResult) {
        return GpuBackendInvocationResult.invoked(
                preparationResult,
                GpuExecutionConfig.oneDimensional(1L),
                0,
                0,
                List.of("synthetic invocation receipt for backend hook testing")
        );
    }

    private static GpuRuntimeCompileRequest syntheticCompileRequest(
            GpuBackendTarget target,
            GpuRuntimeCompileOptions compileOptions
    ) {
        return new GpuRuntimeCompileRequest(
                new GpuKernelDescriptor(
                        "syntheticKernel",
                        "synthetic/backend-hook/Kernel." + syntheticFileExtension(target),
                        syntheticSource(target),
                        List.of()
                ),
                compileOptions,
                GpuRuntimeDeviceProfile.generic(target, "Synthetic " + target.name() + " device")
        );
    }

    private static GpuBackendModuleArtifact syntheticModuleArtifact(GpuBackendTarget target) {
        return switch (target) {
            case OPENCL -> GpuBackendModuleArtifact.openClSource(
                    syntheticSource(target),
                    "synthetic/backend-hook/Kernel.cl",
                    "synthetic-opencl-lowerer:1"
            );
            case CUDA -> GpuBackendModuleArtifact.cudaSource(
                    syntheticSource(target),
                    "synthetic/backend-hook/Kernel.cu",
                    "synthetic-cuda-lowerer:1"
            );
            case VULKAN -> GpuBackendModuleArtifact.spirV(
                    "synthetic/backend-hook/Kernel.spv",
                    "synthetic-spirv-lowerer:1",
                    true
            );
            default -> new GpuBackendModuleArtifact(
                    target,
                    "source",
                    "unknown",
                    syntheticSource(target),
                    "synthetic/backend-hook/Kernel.txt",
                    "synthetic:source:unknown:v1",
                    "synthetic-lowerer:1"
            );
        };
    }

    private static String syntheticSource(GpuBackendTarget target) {
        return switch (target) {
            case CUDA -> "extern \"C\" __global__ void syntheticKernel() {}";
            case VULKAN -> "// synthetic SPIR-V placeholder";
            default -> "__kernel void syntheticKernel() {}";
        };
    }

    private static String syntheticFileExtension(GpuBackendTarget target) {
        return switch (target) {
            case CUDA -> "cu";
            case VULKAN -> "spv";
            case OPENCL -> "cl";
            default -> "txt";
        };
    }
}
