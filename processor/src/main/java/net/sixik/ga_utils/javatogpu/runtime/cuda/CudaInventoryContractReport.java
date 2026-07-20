package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.CudaRuntimeBackendProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleFormat;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceSelectionPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendAdapter;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCatalogEntry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendExecutionSupport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProviders;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCapability;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardware-free CUDA inventory contract snapshot.
 */
public record CudaInventoryContractReport(
        GpuRuntimeBackendProvider provider,
        GpuRuntimeBackendAdapter adapter,
        GpuRuntimeBackendCatalogEntry catalogEntry,
        GpuBackendSourceSelectionPlan lowererSourceSelectionPlan,
        Map<String, String> providerFields,
        Map<String, String> adapterFields
) {

    public CudaInventoryContractReport {
        provider = provider == null ? new CudaRuntimeBackendProvider() : provider;
        adapter = adapter == null
                ? GpuRuntimeBackendProviders.adapters(List.of(provider)).get(0)
                : adapter;
        catalogEntry = catalogEntry == null ? adapter.catalogEntry() : catalogEntry;
        lowererSourceSelectionPlan = lowererSourceSelectionPlan == null
                ? adapter.lowerer().sourceSelectionPlan(sampleCompileRequest())
                : lowererSourceSelectionPlan;
        providerFields = providerFields == null ? Map.of() : Map.copyOf(providerFields);
        adapterFields = adapterFields == null ? Map.of() : Map.copyOf(adapterFields);
    }

    public static CudaInventoryContractReport inspectBuiltInProvider() {
        GpuRuntimeBackendProvider provider = new CudaRuntimeBackendProvider();
        GpuRuntimeBackendAdapter adapter = GpuRuntimeBackendProviders.adapters(List.of(provider)).get(0);
        return new CudaInventoryContractReport(
                provider,
                adapter,
                adapter.catalogEntry(),
                adapter.lowerer().sourceSelectionPlan(sampleCompileRequest()),
                provider.artifactFields("cuda.inventory.provider"),
                adapter.artifactFields("cuda.inventory.adapter")
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

    public List<String> blockers() {
        ArrayList<String> blockers = new ArrayList<>();
        GpuRuntimeBackendExecutionSupport support = provider.executionSupport();
        if (provider.backendTarget() != GpuBackendTarget.CUDA) {
            blockers.add("cuda-provider-target-mismatch:" + provider.backendTarget());
        }
        if (!"backend-provider:cuda".equals(provider.providerId())) {
            blockers.add("cuda-provider-id-mismatch:" + provider.providerId());
        }
        if (adapter.backendTarget() != GpuBackendTarget.CUDA) {
            blockers.add("cuda-adapter-target-mismatch:" + adapter.backendTarget());
        }
        if (catalogEntry.backendTarget() != GpuBackendTarget.CUDA) {
            blockers.add("cuda-catalog-target-mismatch:" + catalogEntry.backendTarget());
        }
        if (catalogEntry.productionAdapter()) {
            blockers.add("cuda-catalog-production-enabled-before-execution-slice");
        }
        if (catalogEntry.executionSupport().isEmpty()) {
            blockers.add("cuda-catalog-execution-support-missing");
        }
        if (support.productionExecution()) {
            blockers.add("cuda-provider-production-enabled-before-execution-slice");
        }
        if (support.executionPipelineAvailable()) {
            blockers.add("cuda-provider-execution-pipeline-enabled-before-execution-slice");
        }
        if (provider.executionPipelineFactory().isPresent()) {
            blockers.add("cuda-provider-execution-factory-present-before-execution-slice");
        }
        if (!support.declaresModuleFormat(GpuBackendModuleFormat.CUDA_C)) {
            blockers.add("cuda-provider-module-format-missing:cuda-c");
        }
        if (!support.declaresModuleFormat(GpuBackendModuleFormat.PTX)) {
            blockers.add("cuda-provider-module-format-missing:ptx");
        }
        if (!support.declaresCapability(GpuRuntimeCapability.COMPUTE_CAPABILITY)) {
            blockers.add("cuda-provider-capability-missing:compute-capability");
        }
        if (!support.declaresCapability(GpuRuntimeCapability.GLOBAL_MEMORY)) {
            blockers.add("cuda-provider-capability-missing:global-memory");
        }
        if (lowererSourceSelectionPlan.backendTarget() != GpuBackendTarget.CUDA) {
            blockers.add("cuda-lowerer-source-plan-target-mismatch:" + lowererSourceSelectionPlan.backendTarget());
        }
        if (!"cuda-irgpu-source-unavailable".equals(lowererSourceSelectionPlan.selectedSource())) {
            blockers.add("cuda-lowerer-source-plan-selected-source-unexpected:" + lowererSourceSelectionPlan.selectedSource());
        }
        if (!"irgpu-missing".equals(lowererSourceSelectionPlan.payloadFormat())) {
            blockers.add("cuda-lowerer-source-plan-payload-format-unexpected:" + lowererSourceSelectionPlan.payloadFormat());
        }
        if (!"cuda-source-preview-unavailable".equals(lowererSourceSelectionPlan.runtimeLoadMode())) {
            blockers.add("cuda-lowerer-source-plan-load-mode-unexpected:" + lowererSourceSelectionPlan.runtimeLoadMode());
        }
        if (!lowererSourceSelectionPlan.blockers().contains("cuda-irgpu-artifact-missing")) {
            blockers.add("cuda-lowerer-source-plan-blocker-missing");
        }
        return List.copyOf(blockers);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.inventoryContract"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".provider.id", provider.providerId());
        fields.put(normalizedPrefix + ".provider.version", provider.providerVersion());
        fields.put(normalizedPrefix + ".provider.productionExecution", Boolean.toString(provider.executionSupport().productionExecution()));
        fields.put(normalizedPrefix + ".provider.executionPipeline.available", Boolean.toString(provider.executionSupport().executionPipelineAvailable()));
        fields.put(normalizedPrefix + ".provider.executionPipeline.factory.present", Boolean.toString(provider.executionPipelineFactory().isPresent()));
        fields.put(normalizedPrefix + ".provider.moduleFormats", provider.executionSupport().moduleFormatKeys());
        fields.put(normalizedPrefix + ".provider.capabilities", provider.executionSupport().capabilityKeys());
        fields.put(normalizedPrefix + ".adapter.backendName", adapter.backendName());
        fields.put(normalizedPrefix + ".catalog.productionAdapter", Boolean.toString(catalogEntry.productionAdapter()));
        fields.put(normalizedPrefix + ".catalog.executionSupport.present", Boolean.toString(catalogEntry.executionSupport().isPresent()));
        fields.put(normalizedPrefix + ".lowerer.selectedSource", lowererSourceSelectionPlan.selectedSource());
        fields.put(normalizedPrefix + ".lowerer.payloadFormat", lowererSourceSelectionPlan.payloadFormat());
        fields.put(normalizedPrefix + ".lowerer.runtimeLoadMode", lowererSourceSelectionPlan.runtimeLoadMode());
        fields.put(normalizedPrefix + ".lowerer.blocker.count", Integer.toString(lowererSourceSelectionPlan.blockers().size()));
        for (int index = 0; index < lowererSourceSelectionPlan.blockers().size(); index++) {
            fields.put(normalizedPrefix + ".lowerer.blocker." + index, lowererSourceSelectionPlan.blockers().get(index));
        }
        List<String> blockers = blockers();
        fields.put(normalizedPrefix + ".blocker.count", Integer.toString(blockers.size()));
        for (int index = 0; index < blockers.size(); index++) {
            fields.put(normalizedPrefix + ".blocker." + index, blockers.get(index));
        }
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        fields.putAll(prefixed(providerFields, normalizedPrefix + ".providerFields"));
        fields.putAll(prefixed(adapterFields, normalizedPrefix + ".adapterFields"));
        fields.put("runtime.cuda.inventoryContract.present", "true");
        fields.put("runtime.cuda.inventoryContract.status", status());
        fields.put("runtime.cuda.inventoryContract.firstBlocker", firstBlocker());
        fields.put("runtime.cuda.inventoryContract.provider.id", provider.providerId());
        fields.put("runtime.cuda.inventoryContract.provider.executionPipeline.available", Boolean.toString(provider.executionSupport().executionPipelineAvailable()));
        fields.put("runtime.cuda.inventoryContract.lowerer.selectedSource", lowererSourceSelectionPlan.selectedSource());
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA inventory contract: ").append(status()).append('\n');
        builder.append("Provider: ").append(provider.providerId()).append(" (`")
                .append(provider.providerVersion()).append("`)\n");
        builder.append("Adapter: ").append(adapter.backendName()).append(" (`")
                .append(adapter.backendTarget()).append("`)\n");
        builder.append("Catalog production adapter: ").append(catalogEntry.productionAdapter()).append('\n');
        builder.append("Module formats: ").append(provider.executionSupport().moduleFormatKeys()).append('\n');
        builder.append("Capability vocabulary: ").append(provider.executionSupport().capabilityKeys()).append('\n');
        builder.append("Lowerer selected source: ").append(lowererSourceSelectionPlan.selectedSource()).append('\n');
        if (!blockers().isEmpty()) {
            builder.append('\n').append("Blockers:").append('\n');
            for (String blocker : blockers()) {
                builder.append("- ").append(blocker).append('\n');
            }
        }
        return builder.toString();
    }

    private static GpuRuntimeCompileRequest sampleCompileRequest() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "cudaInventoryKernel",
                "javatogpu/sample/CudaInventoryKernel.cu",
                "extern \"C\" __global__ void cudaInventoryKernel() {}",
                List.of()
        );
        return new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA")
        );
    }

    private static Map<String, String> prefixed(Map<String, String> source, String prefix) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        source.forEach((key, value) -> fields.put(prefix + "." + key, value));
        return fields;
    }
}
