package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.CudaRuntimeBackendProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipelineResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLoweringResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleFormat;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceSelectionPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendStageStatus;
import net.sixik.ga_utils.javatogpu.runtime.GpuPreparedKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompiledKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendExecutionAvailability;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProviderCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProviders;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCapability;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClBackendSpiContractReport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CUDA execution vertical-slice readiness checklist.
 */
public record CudaExecutionReadinessReport(
        OpenClBackendSpiContractReport openClSpiContract,
        GpuRuntimeBackendProviderCatalog providerCatalog,
        Optional<GpuRuntimeBackendProvider> cudaProvider,
        Optional<GpuRuntimeBackendExecutionAvailability> cudaExecutionAvailability,
        GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> cudaUnsupportedReceipt
) {

    public record ChecklistItem(String key, boolean ready, String diagnostic) {
        public ChecklistItem {
            key = key == null || key.isBlank() ? "unknown" : key.trim();
            diagnostic = diagnostic == null ? "" : diagnostic.trim();
        }

        public String status() {
            return ready ? "ready" : "blocked";
        }
    }

    public CudaExecutionReadinessReport {
        openClSpiContract = openClSpiContract == null
                ? OpenClBackendSpiContractReport.inspectBuiltInProvider()
                : openClSpiContract;
        providerCatalog = providerCatalog == null
                ? GpuRuntimeBackendProviderCatalog.of(GpuRuntimeBackendProviders.builtInsWithPlannedBackends())
                : providerCatalog;
        cudaProvider = cudaProvider == null ? Optional.empty() : cudaProvider;
        cudaExecutionAvailability = cudaExecutionAvailability == null
                ? cudaProvider.map(GpuRuntimeBackendProvider::executionAvailability)
                : cudaExecutionAvailability;
        cudaUnsupportedReceipt = cudaUnsupportedReceipt == null
                ? cudaProvider.map(provider -> provider.unsupportedExecutionResult(cudaLoweringPreviewResult()))
                        .orElseGet(() -> GpuRuntimeBackendExecutionAvailability
                                .from(new CudaRuntimeBackendProvider())
                                .unsupportedPipelineResult(cudaLoweringPreviewResult()))
                : cudaUnsupportedReceipt;
    }

    public static CudaExecutionReadinessReport inspectBuiltIns() {
        GpuRuntimeBackendProviderCatalog catalog = GpuRuntimeBackendProviderCatalog.of(
                GpuRuntimeBackendProviders.builtInsWithPlannedBackends()
        );
        Optional<GpuRuntimeBackendProvider> cudaProvider = catalog.forTarget(GpuBackendTarget.CUDA);
        return new CudaExecutionReadinessReport(
                OpenClBackendSpiContractReport.inspectBuiltInProvider(),
                catalog,
                cudaProvider,
                cudaProvider.map(GpuRuntimeBackendProvider::executionAvailability),
                cudaProvider.map(provider -> provider.unsupportedExecutionResult(cudaLoweringPreviewResult()))
                        .orElse(null)
        );
    }

    public boolean ready() {
        return blockers().isEmpty();
    }

    public String status() {
        return ready() ? "ready" : "blocked";
    }

    public String firstBlocker() {
        return blockers().stream().findFirst().orElse("none");
    }

    public List<ChecklistItem> checklist() {
        ArrayList<ChecklistItem> items = new ArrayList<>();
        boolean openClSharedRunnerVisible = providerCatalog.forTarget(GpuBackendTarget.OPENCL)
                .map(provider -> provider.executionAvailability().sharedPipelineRunnerAvailable())
                .orElse(false);
        items.add(new ChecklistItem(
                "opencl-spi-contract-ready",
                openClSpiContract.ready(),
                "status=" + openClSpiContract.status() + ", firstBlocker=" + openClSpiContract.firstBlocker()
        ));
        items.add(new ChecklistItem(
                "opencl-shared-runner-visible",
                openClSharedRunnerVisible,
                "sharedRunner=" + openClSharedRunnerVisible
        ));
        items.add(new ChecklistItem(
                "cuda-provider-present",
                cudaProvider.isPresent(),
                cudaProvider.map(provider -> "provider=" + provider.providerId()).orElse("provider=missing")
        ));
        if (cudaProvider.isEmpty()) {
            return List.copyOf(items);
        }

        GpuRuntimeBackendProvider provider = cudaProvider.orElseThrow();
        GpuRuntimeBackendExecutionAvailability availability = cudaExecutionAvailability.orElseGet(provider::executionAvailability);
        boolean providerIdentityStable = provider.backendTarget() == GpuBackendTarget.CUDA
                && "backend-provider:cuda".equals(provider.providerId());
        boolean verticalSliceSkeletonPresent = !provider.executionSupport().productionExecution()
                && provider.executionSupport().executionPipelineAvailable()
                && provider.executionPipelineFactory().isPresent();
        boolean nativeBridgeFailClosed = cudaUnsupportedReceipt.compilationResult().stageResult().status()
                == GpuBackendStageStatus.SUCCEEDED
                && cudaUnsupportedReceipt.compiledKernel() instanceof CudaCompiledKernel cudaCompiledKernel
                && !cudaCompiledKernel.nativeHandleAvailable()
                && cudaUnsupportedReceipt.preparationResult().stageResult().status() == GpuBackendStageStatus.UNSUPPORTED
                && cudaUnsupportedReceipt.preparationResult().stageResult().blockers()
                .contains("cuda-native-argument-binding-missing");
        boolean moduleFormatsDeclared = provider.executionSupport().declaresModuleFormat(GpuBackendModuleFormat.CUDA_C)
                && provider.executionSupport().declaresModuleFormat(GpuBackendModuleFormat.PTX);
        boolean capabilityVocabularyDeclared = provider.executionSupport().declaresCapability(GpuRuntimeCapability.COMPUTE_CAPABILITY)
                && provider.executionSupport().declaresCapability(GpuRuntimeCapability.GLOBAL_MEMORY);
        boolean unsupportedReceiptStructured = cudaUnsupportedReceipt.compilationResult().stageResult().status()
                == GpuBackendStageStatus.SUCCEEDED
                && cudaUnsupportedReceipt.preparationResult().stageResult().status() == GpuBackendStageStatus.UNSUPPORTED
                && cudaUnsupportedReceipt.invocationResult().stageResult().status() == GpuBackendStageStatus.SKIPPED;

        items.add(new ChecklistItem(
                "cuda-provider-identity-stable",
                providerIdentityStable,
                "target=" + provider.backendTarget() + ", provider=" + provider.providerId()
        ));
        items.add(new ChecklistItem(
                "cuda-vertical-slice-skeleton-present",
                verticalSliceSkeletonPresent,
                "productionExecution=" + provider.executionSupport().productionExecution()
                        + ", pipelineAvailable=" + provider.executionSupport().executionPipelineAvailable()
                        + ", factoryPresent=" + provider.executionPipelineFactory().isPresent()
        ));
        items.add(new ChecklistItem(
                "cuda-native-bridge-fail-closed",
                nativeBridgeFailClosed,
                "compile=" + cudaUnsupportedReceipt.compilationResult().stageResult().status()
                        + ", prepare=" + cudaUnsupportedReceipt.preparationResult().stageResult().status()
                        + ", prepareBlockers=" + String.join(",", cudaUnsupportedReceipt.preparationResult().stageResult().blockers())
        ));
        items.add(new ChecklistItem(
                "cuda-module-formats-declared",
                moduleFormatsDeclared,
                "moduleFormats=" + provider.executionSupport().moduleFormatKeys()
        ));
        items.add(new ChecklistItem(
                "cuda-capability-vocabulary-declared",
                capabilityVocabularyDeclared,
                "capabilities=" + provider.executionSupport().capabilityKeys()
        ));
        items.add(new ChecklistItem(
                "cuda-unsupported-receipt-structured",
                unsupportedReceiptStructured,
                "compile=" + cudaUnsupportedReceipt.compilationResult().stageResult().status()
                        + ", prepare=" + cudaUnsupportedReceipt.preparationResult().stageResult().status()
                        + ", invoke=" + cudaUnsupportedReceipt.invocationResult().stageResult().status()
        ));
        return List.copyOf(items);
    }

    public long checklistReadyCount() {
        return checklist().stream().filter(ChecklistItem::ready).count();
    }

    public long checklistBlockedCount() {
        return checklist().stream().filter(item -> !item.ready()).count();
    }

    public String firstBlockedChecklistItem() {
        return checklist().stream()
                .filter(item -> !item.ready())
                .map(ChecklistItem::key)
                .findFirst()
                .orElse("none");
    }

    public List<String> blockers() {
        ArrayList<String> blockers = new ArrayList<>();
        if (!openClSpiContract.ready()) {
            blockers.add("opencl-spi-contract-not-ready:" + openClSpiContract.firstBlocker());
        }
        if (!providerCatalog.forTarget(GpuBackendTarget.OPENCL)
                .map(provider -> provider.executionAvailability().sharedPipelineRunnerAvailable())
                .orElse(false)) {
            blockers.add("provider-catalog-opencl-shared-runner-missing");
        }
        if (cudaProvider.isEmpty()) {
            blockers.add("cuda-provider-missing");
            return List.copyOf(blockers);
        }

        GpuRuntimeBackendProvider provider = cudaProvider.orElseThrow();
        GpuRuntimeBackendExecutionAvailability availability = cudaExecutionAvailability.orElseGet(provider::executionAvailability);
        if (provider.backendTarget() != GpuBackendTarget.CUDA) {
            blockers.add("cuda-provider-target-mismatch:" + provider.backendTarget());
        }
        if (!"backend-provider:cuda".equals(provider.providerId())) {
            blockers.add("cuda-provider-id-mismatch:" + provider.providerId());
        }
        if (provider.executionSupport().productionExecution()) {
            blockers.add("cuda-production-execution-enabled-before-green-light");
        }
        if (!provider.executionSupport().executionPipelineAvailable()) {
            blockers.add("cuda-execution-skeleton-stages-missing");
        }
        if (provider.executionPipelineFactory().isEmpty()) {
            blockers.add("cuda-execution-skeleton-factory-missing");
        }
        if (availability.sharedPipelineRunnerAvailable() && availability.blockers().stream()
                .anyMatch(blocker -> blocker.startsWith("backend-execution-stage-missing:"))) {
            blockers.add("cuda-execution-availability-has-stale-stage-blocker");
        }
        if (!provider.executionSupport().declaresModuleFormat(GpuBackendModuleFormat.CUDA_C)) {
            blockers.add("cuda-module-format-missing:cuda-c");
        }
        if (!provider.executionSupport().declaresModuleFormat(GpuBackendModuleFormat.PTX)) {
            blockers.add("cuda-module-format-missing:ptx");
        }
        if (!provider.executionSupport().declaresCapability(GpuRuntimeCapability.COMPUTE_CAPABILITY)) {
            blockers.add("cuda-capability-missing:compute-capability");
        }
        if (!provider.executionSupport().declaresCapability(GpuRuntimeCapability.GLOBAL_MEMORY)) {
            blockers.add("cuda-capability-missing:global-memory");
        }
        if (cudaUnsupportedReceipt.compilationResult().stageResult().status() != GpuBackendStageStatus.SUCCEEDED) {
            blockers.add("cuda-compile-preview-not-succeeded");
        }
        if (!cudaUnsupportedReceipt.compilationResult().compiled()) {
            blockers.add("cuda-compile-preview-artifact-missing");
        }
        if (!(cudaUnsupportedReceipt.compiledKernel() instanceof CudaCompiledKernel cudaCompiledKernel)) {
            blockers.add("cuda-compiled-kernel-type-missing");
        } else if (cudaCompiledKernel.nativeHandleAvailable()) {
            blockers.add("cuda-native-handle-enabled-before-prepare-bridge");
        }
        if (cudaUnsupportedReceipt.preparationResult().stageResult().status() != GpuBackendStageStatus.UNSUPPORTED) {
            blockers.add("cuda-unsupported-receipt-prepare-not-unsupported");
        }
        if (!cudaUnsupportedReceipt.preparationResult().stageResult().blockers()
                .contains("cuda-native-argument-binding-missing")) {
            blockers.add("cuda-native-argument-binding-blocker-missing");
        }
        if (cudaUnsupportedReceipt.invocationResult().stageResult().status() != GpuBackendStageStatus.SKIPPED) {
            blockers.add("cuda-unsupported-receipt-invoke-not-skipped");
        }
        return List.copyOf(blockers);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.executionReadiness"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".opencl.ready", Boolean.toString(openClSpiContract.ready()));
        fields.put(normalizedPrefix + ".providerCatalog.provider.count", Integer.toString(providerCatalog.providers().size()));
        fields.put(normalizedPrefix + ".providerCatalog.sharedRunner.available.count", Long.toString(providerCatalog.sharedPipelineRunnerAvailableCount()));
        fields.put(normalizedPrefix + ".cuda.provider.present", Boolean.toString(cudaProvider.isPresent()));
        cudaProvider.ifPresent(provider -> {
            fields.put(normalizedPrefix + ".cuda.provider.id", provider.providerId());
            fields.put(normalizedPrefix + ".cuda.provider.version", provider.providerVersion());
            fields.put(normalizedPrefix + ".cuda.productionExecution", Boolean.toString(provider.executionSupport().productionExecution()));
            fields.put(normalizedPrefix + ".cuda.executionPipeline.available", Boolean.toString(provider.executionSupport().executionPipelineAvailable()));
            fields.put(normalizedPrefix + ".cuda.executionPipeline.factory.present", Boolean.toString(provider.executionPipelineFactory().isPresent()));
            fields.put(normalizedPrefix + ".cuda.moduleFormats", provider.executionSupport().moduleFormatKeys());
            fields.put(normalizedPrefix + ".cuda.capabilities", provider.executionSupport().capabilityKeys());
        });
        cudaExecutionAvailability.ifPresent(availability -> fields.putAll(
                availability.artifactFields(normalizedPrefix + ".cuda.executionAvailability")
        ));
        fields.put(
                normalizedPrefix + ".cuda.unsupportedReceipt.compile.status",
                cudaUnsupportedReceipt.compilationResult().stageResult().status().name()
        );
        fields.put(
                normalizedPrefix + ".cuda.unsupportedReceipt.prepare.status",
                cudaUnsupportedReceipt.preparationResult().stageResult().status().name()
        );
        fields.put(
                normalizedPrefix + ".cuda.unsupportedReceipt.invoke.status",
                cudaUnsupportedReceipt.invocationResult().stageResult().status().name()
        );
        List<String> blockers = blockers();
        fields.put(normalizedPrefix + ".blocker.count", Integer.toString(blockers.size()));
        for (int index = 0; index < blockers.size(); index++) {
            fields.put(normalizedPrefix + ".blocker." + index, blockers.get(index));
        }
        List<ChecklistItem> checklist = checklist();
        fields.put(normalizedPrefix + ".checklist.item.count", Integer.toString(checklist.size()));
        fields.put(normalizedPrefix + ".checklist.ready.count", Long.toString(checklistReadyCount()));
        fields.put(normalizedPrefix + ".checklist.blocked.count", Long.toString(checklistBlockedCount()));
        fields.put(normalizedPrefix + ".checklist.ready.all", Boolean.toString(checklistBlockedCount() == 0));
        fields.put(normalizedPrefix + ".checklist.firstBlocked", firstBlockedChecklistItem());
        for (int index = 0; index < checklist.size(); index++) {
            ChecklistItem item = checklist.get(index);
            String itemPrefix = normalizedPrefix + ".checklist.item." + index;
            fields.put(itemPrefix + ".key", item.key());
            fields.put(itemPrefix + ".ready", Boolean.toString(item.ready()));
            fields.put(itemPrefix + ".status", item.status());
            fields.put(itemPrefix + ".diagnostic", item.diagnostic());
        }
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        fields.put("runtime.cuda.executionReadiness.present", "true");
        fields.put("runtime.cuda.executionReadiness.status", status());
        fields.put("runtime.cuda.executionReadiness.firstBlocker", firstBlocker());
        fields.put("runtime.cuda.executionReadiness.checklist.item.count", Integer.toString(checklist.size()));
        fields.put("runtime.cuda.executionReadiness.checklist.ready.count", Long.toString(checklistReadyCount()));
        fields.put("runtime.cuda.executionReadiness.checklist.blocked.count", Long.toString(checklistBlockedCount()));
        fields.put("runtime.cuda.executionReadiness.checklist.firstBlocked", firstBlockedChecklistItem());
        fields.put("runtime.cuda.executionReadiness.cuda.provider.present", Boolean.toString(cudaProvider.isPresent()));
        fields.put("runtime.cuda.executionReadiness.cuda.executionPipeline.available", cudaProvider
                .map(provider -> Boolean.toString(provider.executionSupport().executionPipelineAvailable()))
                .orElse("false"));
        fields.put("runtime.cuda.executionReadiness.cuda.executionPipeline.factory.present", cudaProvider
                .map(provider -> Boolean.toString(provider.executionPipelineFactory().isPresent()))
                .orElse("false"));
        fields.put("runtime.cuda.executionReadiness.cuda.unsupportedReceipt.compile.status", cudaUnsupportedReceipt
                .compilationResult()
                .stageResult()
                .status()
                .name());
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA execution green-light checklist: ").append(status()).append('\n');
        builder.append("OpenCL SPI contract: ").append(openClSpiContract.status()).append('\n');
        builder.append("Provider catalog entries: ").append(providerCatalog.providers().size()).append('\n');
        builder.append("Shared runners available: ").append(providerCatalog.sharedPipelineRunnerAvailableCount()).append('\n');
        if (cudaProvider.isPresent()) {
            GpuRuntimeBackendProvider provider = cudaProvider.orElseThrow();
            GpuRuntimeBackendExecutionAvailability availability = cudaExecutionAvailability.orElseGet(provider::executionAvailability);
            builder.append("CUDA provider: ").append(provider.providerId()).append(" (`")
                    .append(provider.providerVersion()).append("`)\n");
            builder.append("CUDA execution availability: ").append(availability.status()).append('\n');
            builder.append("CUDA module formats: ").append(provider.executionSupport().moduleFormatKeys()).append('\n');
            builder.append("CUDA unsupported receipt: compile=")
                    .append(cudaUnsupportedReceipt.compilationResult().stageResult().status())
                    .append(", prepare=")
                    .append(cudaUnsupportedReceipt.preparationResult().stageResult().status())
                    .append(", invoke=")
                    .append(cudaUnsupportedReceipt.invocationResult().stageResult().status())
                    .append('\n');
        } else {
            builder.append("CUDA provider: missing\n");
        }
        if (!blockers().isEmpty()) {
            builder.append('\n').append("Blockers:").append('\n');
            for (String blocker : blockers()) {
                builder.append("- ").append(blocker).append('\n');
            }
        }
        builder.append('\n').append("Checklist:").append('\n');
        for (ChecklistItem item : checklist()) {
            builder.append("- ")
                    .append(item.key())
                    .append(": ")
                    .append(item.status())
                    .append(" (`")
                    .append(item.diagnostic())
                    .append("`)")
                    .append('\n');
        }
        return builder.toString();
    }

    private static GpuBackendLoweringResult cudaLoweringPreviewResult() {
        GpuBackendModuleArtifact module = GpuBackendModuleArtifact.cudaSource(
                "extern \"C\" __global__ void cudaExecutionReadinessKernel(const float* input, float* output) { }",
                "inline://cuda/execution-readiness.cu",
                "cuda-execution-readiness-preview"
        );
        return GpuBackendLoweringResult.succeeded(
                module,
                GpuBackendSourceSelectionPlan.descriptorSource(
                        GpuBackendTarget.CUDA,
                        GpuBackendModuleFormat.CUDA_C.key(),
                        "CUDA execution readiness check uses an in-memory cuda-c preview module"
                ),
                List.of("metadata-only CUDA green-light checklist with compile-preview module")
        );
    }
}
