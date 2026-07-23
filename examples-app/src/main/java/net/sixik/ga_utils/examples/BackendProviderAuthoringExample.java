package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompiledKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipeline;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipelineFactory;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipelineResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelCompiler;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelInvoker;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelPreparer;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLowerer;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLoweringResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleFormat;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendPipelineStage;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceSelectionPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuPreparedKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackend;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendAdapter;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCatalogEntry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendExecutionAvailability;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendExecutionSupport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactSnapshot;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCapability;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationBindingSummary;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Runnable checklist for authors of future backend providers.
 */
public final class BackendProviderAuthoringExample {

    private static final GpuBackendTarget TARGET = GpuBackendTarget.CUDA;

    private BackendProviderAuthoringExample() {
    }

    public static void main(String[] args) {
        System.out.println(renderProviderAuthoringChecklist());
    }

    static String renderProviderAuthoringChecklist() {
        List<GpuRuntimeBackendProvider> providers = List.of(
                discoveryOnlyProvider(),
                loweringOnlyProvider(),
                productionPipelineProvider()
        );
        StringBuilder builder = new StringBuilder();
        builder.append("Backend provider authoring checklist:").append(System.lineSeparator());
        builder.append("Use ServiceLoader for real modules; this example keeps the providers local for readability.")
                .append(System.lineSeparator())
                .append(System.lineSeparator());
        for (int index = 0; index < providers.size(); index++) {
            appendProviderStep(builder, index + 1, providers.get(index));
        }
        return builder.toString();
    }

    private static void appendProviderStep(StringBuilder builder, int stepNumber, GpuRuntimeBackendProvider provider) {
        GpuRuntimeBackendExecutionAvailability availability = provider.executionAvailability();
        builder.append(stepNumber)
                .append(". ")
                .append(stepTitle(provider))
                .append(System.lineSeparator());
        builder.append("- provider=")
                .append(provider.providerId())
                .append(", target=")
                .append(provider.backendTarget())
                .append(", status=")
                .append(availability.status())
                .append(System.lineSeparator());
        builder.append("- supportedStages=")
                .append(provider.executionSupport().supportedStageKeys())
                .append(", sharedRunner=")
                .append(availability.sharedPipelineRunnerAvailable())
                .append(System.lineSeparator());
        builder.append("- moduleFormats=")
                .append(provider.executionSupport().moduleFormatKeys())
                .append(System.lineSeparator());
        builder.append("- capabilityVocabulary=")
                .append(provider.executionSupport().capabilityKeys())
                .append(System.lineSeparator());
        builder.append("- summary=")
                .append(availability.summary())
                .append(System.lineSeparator());
        if (!availability.blockers().isEmpty()) {
            builder.append("- blockers=")
                    .append(String.join(", ", availability.blockers()))
                    .append(System.lineSeparator());
            GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> unsupported =
                    provider.unsupportedExecutionResult(exampleLoweringResult(provider.backendTarget()));
            builder.append("- unsupportedReceipt=compile=")
                    .append(unsupported.compilationResult().stageResult().status())
                    .append(", prepare=")
                    .append(unsupported.preparationResult().stageResult().status())
                    .append(", invoke=")
                    .append(unsupported.invocationResult().stageResult().status())
                    .append(System.lineSeparator());
        }
        builder.append("- next=")
                .append(nextStep(provider))
                .append(System.lineSeparator())
                .append(System.lineSeparator());
    }

    private static String stepTitle(GpuRuntimeBackendProvider provider) {
        if (provider.executionAvailability().sharedPipelineRunnerAvailable()) {
            return "Production pipeline provider";
        }
        if (provider.executionSupport().supportsStage(GpuBackendPipelineStage.LOWER)) {
            return "Lowering-only provider";
        }
        return "Discovery-only provider";
    }

    private static String nextStep(GpuRuntimeBackendProvider provider) {
        if (!provider.executionSupport().supportsStage(GpuBackendPipelineStage.LOWER)) {
            return "add a lowerer and module artifact format such as cuda-c or ptx";
        }
        if (!provider.executionAvailability().sharedPipelineRunnerAvailable()) {
            return "add compiler/preparer/invoker handles and expose an executionPipelineFactory";
        }
        return "wire native execution, lifecycle fields, artifact dumps, and production validation evidence";
    }

    private static GpuBackendLoweringResult exampleLoweringResult(GpuBackendTarget target) {
        return GpuBackendLoweringResult.unsupported(
                target,
                GpuBackendSourceSelectionPlan.descriptorSource(
                        target,
                        "cuda-c",
                        "example provider has not produced executable source yet"
                ),
                List.of("example-lowering-not-ready"),
                List.of("authoring checklist preview")
        );
    }

    private static GpuRuntimeBackendProvider discoveryOnlyProvider() {
        return new ExampleProvider(
                "example.cuda.discovery-only",
                GpuRuntimeBackendExecutionSupport.discoveryOnly(
                        TARGET,
                        "example.cuda.discovery-only",
                        Set.of(GpuBackendModuleFormat.CUDA_C, GpuBackendModuleFormat.PTX),
                        cudaCapabilityVocabulary(),
                        "provider can be listed and can add native inventory later"
                ),
                Optional.empty()
        );
    }

    private static GpuRuntimeBackendProvider loweringOnlyProvider() {
        return new ExampleProvider(
                "example.cuda.lowering-only",
                GpuRuntimeBackendExecutionSupport.loweringOnly(
                        TARGET,
                        "example.cuda.lowering-only",
                        Set.of(GpuBackendModuleFormat.CUDA_C, GpuBackendModuleFormat.PTX),
                        cudaCapabilityVocabulary(),
                        "provider can emit backend source, but cannot execute it yet"
                ),
                Optional.empty()
        );
    }

    private static GpuRuntimeBackendProvider productionPipelineProvider() {
        return new ExampleProvider(
                "example.cuda.production-pipeline",
                GpuRuntimeBackendExecutionSupport.productionPipeline(
                        TARGET,
                        "example.cuda.production-pipeline",
                        Set.of(GpuBackendModuleFormat.CUDA_C, GpuBackendModuleFormat.PTX),
                        cudaCapabilityVocabulary(),
                        "provider exposes compile, prepare, invoke, readback, and cleanup stages"
                ),
                Optional.of(new ExamplePipelineFactory())
        );
    }

    private static Set<GpuRuntimeCapability> cudaCapabilityVocabulary() {
        return Set.of(
                GpuRuntimeCapability.DEVICE_CLASS,
                GpuRuntimeCapability.DRIVER_VERSION,
                GpuRuntimeCapability.RUNTIME_VERSION,
                GpuRuntimeCapability.COMPUTE_CAPABILITY,
                GpuRuntimeCapability.GLOBAL_MEMORY
        );
    }

    private record ExampleProvider(
            String providerId,
            GpuRuntimeBackendExecutionSupport executionSupport,
            Optional<GpuBackendExecutionPipelineFactory<?, ?, ?>> executionPipelineFactory
    ) implements GpuRuntimeBackendProvider {
        @Override
        public GpuBackendTarget backendTarget() {
            return TARGET;
        }

        @Override
        public String providerVersion() {
            return "1";
        }

        @Override
        public int providerOrder() {
            return 1_000;
        }

        @Override
        public GpuRuntimeBackendAdapter createAdapter() {
            return new ExampleAdapter(providerId, executionSupport);
        }
    }

    private record ExampleAdapter(
            String providerId,
            GpuRuntimeBackendExecutionSupport support
    ) implements GpuRuntimeBackendAdapter {
        @Override
        public GpuBackendTarget backendTarget() {
            return TARGET;
        }

        @Override
        public String backendName() {
            return "Example CUDA";
        }

        @Override
        public GpuRuntimeBackendCatalogEntry catalogEntry() {
            return GpuRuntimeBackendCatalogEntry.owned(
                    TARGET,
                    backendName(),
                    () -> new ExampleRuntimeBackend(TARGET, backendName()),
                    support.productionExecution(),
                    support,
                    providerId + " - " + support.diagnostic()
            );
        }

        @Override
        public GpuRuntimeDeviceDiscoveryResult discoverDevices(
                GpuRuntimeCompileOptions compileOptions,
                GpuRuntimeDevicePolicyRegistry devicePolicyRegistry
        ) {
            return GpuRuntimeDeviceDiscoveryResult.unavailable(
                    TARGET,
                    backendName(),
                    "example-native-discovery-not-implemented",
                    null
            );
        }

        @Override
        public GpuBackendLowerer lowerer() {
            return new ExampleLowerer();
        }
    }

    private static final class ExampleLowerer implements GpuBackendLowerer {
        @Override
        public GpuBackendTarget backendTarget() {
            return TARGET;
        }

        @Override
        public String lowererVersion() {
            return "example:1";
        }

        @Override
        public String extensionId() {
            return "example.cuda.lowerer";
        }

        @Override
        public GpuBackendSourceSelectionPlan sourceSelectionPlan(GpuRuntimeCompileRequest compileRequest) {
            return GpuBackendSourceSelectionPlan.descriptorSource(
                    TARGET,
                    "cuda-c",
                    "example lowerer would select generated CUDA source"
            );
        }

        @Override
        public GpuBackendModuleArtifact lower(GpuRuntimeCompileRequest compileRequest) {
            return GpuBackendModuleArtifact.cudaSource(
                    "extern \"C\" __global__ void example() {}",
                    "example/generated/example.cu",
                    lowererVersion()
            );
        }

        @Override
        public Set<net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability> extensionCapabilities() {
            return EnumSet.of(net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability.BACKEND_LOWERING);
        }
    }

    private record ExampleRuntimeBackend(
            GpuBackendTarget backendTarget,
            String backendName
    ) implements GpuRuntimeBackend {
        @Override
        public GpuRuntimeBackendReport describeCapabilities() {
            return GpuRuntimeBackendReport.unavailable(
                    backendTarget,
                    backendName,
                    "example backend does not open a native runtime"
            );
        }

        @Override
        public void invoke(GpuKernelInvocation invocation) {
            throw new UnsupportedOperationException("example backend does not execute kernels");
        }
    }

    private static final class ExamplePipelineFactory implements GpuBackendExecutionPipelineFactory<
            ExampleCompiledKernel,
            ExamplePreparedKernel,
            Object> {
        @Override
        public GpuBackendTarget backendTarget() {
            return TARGET;
        }

        @Override
        public String factoryId() {
            return "example.cuda.execution-pipeline";
        }

        @Override
        public String factoryVersion() {
            return "1";
        }

        @Override
        public Class<? extends GpuRuntimeBackend> backendType() {
            return ExampleRuntimeBackend.class;
        }

        @Override
        public GpuBackendExecutionPipeline<ExampleCompiledKernel, ExamplePreparedKernel, Object> createPipeline(
                GpuRuntimeBackend backend
        ) {
            requireSupportedBackend(backend);
            return new GpuBackendExecutionPipeline<>(new ExampleCompiler(), new ExamplePreparer(), new ExampleInvoker());
        }
    }

    private static final class ExampleCompiler implements GpuBackendKernelCompiler<ExampleCompiledKernel> {
        @Override
        public GpuBackendTarget backendTarget() {
            return TARGET;
        }

        @Override
        public ExampleCompiledKernel compile(
                GpuRuntimeCompileRequest compileRequest,
                GpuBackendModuleArtifact moduleArtifact
        ) {
            return new ExampleCompiledKernel(
                    compileRequest.descriptor(),
                    "example-cache-key",
                    GpuRuntimeCompileArtifactSnapshot.from(compileRequest, compileRequest, moduleArtifact)
            );
        }
    }

    private static final class ExamplePreparer implements GpuBackendKernelPreparer<
            ExampleCompiledKernel,
            ExamplePreparedKernel,
            Object> {
        @Override
        public GpuBackendTarget backendTarget() {
            return TARGET;
        }

        @Override
        public ExamplePreparedKernel prepare(ExampleCompiledKernel compiledKernel, Object executionPlan) {
            return new ExamplePreparedKernel(compiledKernel);
        }
    }

    private static final class ExampleInvoker implements GpuBackendKernelInvoker<ExamplePreparedKernel> {
        @Override
        public GpuBackendTarget backendTarget() {
            return TARGET;
        }

        @Override
        public void invoke(ExamplePreparedKernel preparedKernel, GpuExecutionConfig executionConfig) {
            // Authoring example only: a real backend would enqueue native work here.
        }
    }

    private record ExampleCompiledKernel(
            net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor descriptor,
            String cacheKey,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot
    ) implements GpuBackendCompiledKernel {
        @Override
        public String compiledKernelKind() {
            return "example-cuda-kernel";
        }
    }

    private record ExamplePreparedKernel(
            GpuBackendCompiledKernel compiledKernel
    ) implements GpuPreparedKernel {
        @Override
        public GpuRuntimeInvocationBindingSummary bindingSummary() {
            return GpuRuntimeInvocationBindingSummary.empty();
        }

        @Override
        public GpuExecutionConfig explicitExecutionConfig() {
            return null;
        }

        @Override
        public String preparedKernelKind() {
            return "example-cuda-kernel";
        }
    }
}
