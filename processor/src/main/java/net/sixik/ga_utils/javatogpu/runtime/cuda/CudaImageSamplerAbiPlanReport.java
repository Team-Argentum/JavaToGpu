package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hardware-free CUDA image/sampler ABI plan.
 *
 * <p>This is a planning contract, not runtime support. CUDA image/sampler binding stays fail-closed until the planned
 * texture/surface/sampler ABI is implemented and real-device evidence exists.</p>
 */
public record CudaImageSamplerAbiPlanReport(
        List<Entry> entries,
        CudaImageSamplerContractReport failClosedContract
) {
    public record Entry(
            String key,
            String javaType,
            String openClType,
            String cudaAbiRole,
            String cudaResourceKind,
            String parameterCarrier,
            String requiredDriverSymbols,
            String sourcePreviewStatus,
            String sourcePreviewCarrier,
            int sourcePreviewKernelParameterSlotCount,
            int sourcePreviewMetadataSlotCount,
            String runtimeBindingStatus,
            int plannedRuntimeKernelParameterSlotCount,
            int plannedRuntimeMetadataSlotCount,
            int runtimeBindingKernelParameterSlotCount,
            String implementationStatus,
            boolean productionSupportEnabled
    ) {
        public Entry {
            key = normalize(key, "unknown");
            javaType = normalize(javaType, "unknown");
            openClType = normalize(openClType, "unknown");
            cudaAbiRole = normalize(cudaAbiRole, "unknown");
            cudaResourceKind = normalize(cudaResourceKind, "unknown");
            parameterCarrier = normalize(parameterCarrier, "unknown");
            requiredDriverSymbols = normalize(requiredDriverSymbols, "none");
            sourcePreviewStatus = normalize(sourcePreviewStatus, "pending");
            sourcePreviewCarrier = normalize(sourcePreviewCarrier, "pending");
            sourcePreviewKernelParameterSlotCount = Math.max(0, sourcePreviewKernelParameterSlotCount);
            sourcePreviewMetadataSlotCount = Math.max(0, sourcePreviewMetadataSlotCount);
            runtimeBindingStatus = normalize(runtimeBindingStatus, "fail-closed");
            plannedRuntimeKernelParameterSlotCount = Math.max(0, plannedRuntimeKernelParameterSlotCount);
            plannedRuntimeMetadataSlotCount = Math.max(0, plannedRuntimeMetadataSlotCount);
            runtimeBindingKernelParameterSlotCount = Math.max(0, runtimeBindingKernelParameterSlotCount);
            implementationStatus = normalize(implementationStatus, "planned");
        }

        public boolean ready() {
            return !"unknown".equals(javaType)
                    && !"unknown".equals(openClType)
                    && !"unknown".equals(cudaAbiRole)
                    && !"unknown".equals(cudaResourceKind)
                    && !"unknown".equals(parameterCarrier)
                    && !"unknown".equals(sourcePreviewStatus)
                    && !"unknown".equals(sourcePreviewCarrier)
                    && "fail-closed".equals(runtimeBindingStatus)
                    && runtimeBindingKernelParameterSlotCount == 0
                    && "planned".equals(implementationStatus)
                    && !productionSupportEnabled;
        }

        public boolean sourcePreviewEnabled() {
            return "enabled".equals(sourcePreviewStatus);
        }

        public boolean sourcePreviewFolded() {
            return "folded".equals(sourcePreviewStatus);
        }

        public boolean sourcePreviewPending() {
            return "pending".equals(sourcePreviewStatus);
        }

        public boolean runtimeBindingEnabled() {
            return !"fail-closed".equals(runtimeBindingStatus);
        }

        public Map<String, String> artifactFields(String prefix) {
            String normalizedPrefix = prefix == null || prefix.isBlank()
                    ? "runtime.cuda.imageSamplerAbiPlan.entry"
                    : prefix.trim();
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put(normalizedPrefix + ".key", key);
            fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
            fields.put(normalizedPrefix + ".javaType", javaType);
            fields.put(normalizedPrefix + ".openClType", openClType);
            fields.put(normalizedPrefix + ".cudaAbi.role", cudaAbiRole);
            fields.put(normalizedPrefix + ".cudaResource.kind", cudaResourceKind);
            fields.put(normalizedPrefix + ".parameter.carrier", parameterCarrier);
            fields.put(normalizedPrefix + ".driverSymbols.required", requiredDriverSymbols);
            fields.put(normalizedPrefix + ".sourcePreview.status", sourcePreviewStatus);
            fields.put(normalizedPrefix + ".sourcePreview.carrier", sourcePreviewCarrier);
            fields.put(normalizedPrefix + ".sourcePreview.kernelParameterSlot.count", Integer.toString(sourcePreviewKernelParameterSlotCount));
            fields.put(normalizedPrefix + ".sourcePreview.metadataSlot.count", Integer.toString(sourcePreviewMetadataSlotCount));
            fields.put(normalizedPrefix + ".runtimeBinding.status", runtimeBindingStatus);
            fields.put(normalizedPrefix + ".runtimeBinding.enabled", Boolean.toString(runtimeBindingEnabled()));
            fields.put(normalizedPrefix + ".runtimeBinding.plannedKernelParameterSlot.count", Integer.toString(plannedRuntimeKernelParameterSlotCount));
            fields.put(normalizedPrefix + ".runtimeBinding.plannedMetadataSlot.count", Integer.toString(plannedRuntimeMetadataSlotCount));
            fields.put(normalizedPrefix + ".runtimeBinding.kernelParameterSlot.count", Integer.toString(runtimeBindingKernelParameterSlotCount));
            fields.put(normalizedPrefix + ".implementation.status", implementationStatus);
            fields.put(normalizedPrefix + ".productionSupport.enabled", Boolean.toString(productionSupportEnabled));
            return Collections.unmodifiableMap(fields);
        }
    }

    public CudaImageSamplerAbiPlanReport {
        entries = entries == null ? List.of() : List.copyOf(entries);
        failClosedContract = failClosedContract == null
                ? CudaImageSamplerContractReport.inspectBuiltIns()
                : failClosedContract;
    }

    public static CudaImageSamplerAbiPlanReport inspectBuiltIns() {
        return new CudaImageSamplerAbiPlanReport(
                CudaImageSamplerAbi.descriptors().stream()
                        .map(CudaImageSamplerAbiPlanReport::entry)
                        .collect(Collectors.toUnmodifiableList()),
                CudaImageSamplerContractReport.inspectBuiltIns()
        );
    }

    public boolean ready() {
        return !entries.isEmpty()
                && entries.stream().allMatch(Entry::ready)
                && failClosedContract.ready();
    }

    public String status() {
        return ready() ? "ready" : "blocked";
    }

    public long entryReadyCount() {
        return entries.stream().filter(Entry::ready).count();
    }

    public long entryBlockedCount() {
        return entries.stream().filter(entry -> !entry.ready()).count();
    }

    public long sourcePreviewEnabledCount() {
        return entries.stream().filter(Entry::sourcePreviewEnabled).count();
    }

    public long sourcePreviewFoldedCount() {
        return entries.stream().filter(Entry::sourcePreviewFolded).count();
    }

    public long sourcePreviewPendingCount() {
        return entries.stream().filter(Entry::sourcePreviewPending).count();
    }

    public long runtimeBindingEnabledCount() {
        return entries.stream().filter(Entry::runtimeBindingEnabled).count();
    }

    public long sourcePreviewKernelParameterSlotCount() {
        return entries.stream().mapToLong(Entry::sourcePreviewKernelParameterSlotCount).sum();
    }

    public long sourcePreviewMetadataSlotCount() {
        return entries.stream().mapToLong(Entry::sourcePreviewMetadataSlotCount).sum();
    }

    public long plannedRuntimeKernelParameterSlotCount() {
        return entries.stream().mapToLong(Entry::plannedRuntimeKernelParameterSlotCount).sum();
    }

    public long plannedRuntimeMetadataSlotCount() {
        return entries.stream().mapToLong(Entry::plannedRuntimeMetadataSlotCount).sum();
    }

    public long runtimeBindingKernelParameterSlotCount() {
        return entries.stream().mapToLong(Entry::runtimeBindingKernelParameterSlotCount).sum();
    }

    public String firstBlocker() {
        if (entries.isEmpty()) {
            return "cuda-image-sampler-abi-plan-entries-missing";
        }
        for (Entry entry : entries) {
            if (!entry.ready()) {
                return "cuda-image-sampler-abi-plan-entry-not-ready:" + entry.key();
            }
        }
        if (!failClosedContract.ready()) {
            return "cuda-image-sampler-fail-closed-contract-not-ready:" + failClosedContract.firstBlocker();
        }
        return "none";
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerAbiPlan"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".ready", Boolean.toString(ready()));
        fields.put(normalizedPrefix + ".productionSupport.enabled", "false");
        fields.put(normalizedPrefix + ".implementation.status", "planned");
        fields.put(normalizedPrefix + ".entry.count", Integer.toString(entries.size()));
        fields.put(normalizedPrefix + ".entry.ready.count", Long.toString(entryReadyCount()));
        fields.put(normalizedPrefix + ".entry.blocked.count", Long.toString(entryBlockedCount()));
        fields.put(normalizedPrefix + ".sourcePreview.enabled.count", Long.toString(sourcePreviewEnabledCount()));
        fields.put(normalizedPrefix + ".sourcePreview.folded.count", Long.toString(sourcePreviewFoldedCount()));
        fields.put(normalizedPrefix + ".sourcePreview.pending.count", Long.toString(sourcePreviewPendingCount()));
        fields.put(normalizedPrefix + ".sourcePreview.kernelParameterSlot.count", Long.toString(sourcePreviewKernelParameterSlotCount()));
        fields.put(normalizedPrefix + ".sourcePreview.metadataSlot.count", Long.toString(sourcePreviewMetadataSlotCount()));
        fields.put(normalizedPrefix + ".runtimeBinding.enabled.count", Long.toString(runtimeBindingEnabledCount()));
        fields.put(normalizedPrefix + ".runtimeBinding.plannedKernelParameterSlot.count", Long.toString(plannedRuntimeKernelParameterSlotCount()));
        fields.put(normalizedPrefix + ".runtimeBinding.plannedMetadataSlot.count", Long.toString(plannedRuntimeMetadataSlotCount()));
        fields.put(normalizedPrefix + ".runtimeBinding.kernelParameterSlot.count", Long.toString(runtimeBindingKernelParameterSlotCount()));
        fields.put(normalizedPrefix + ".failClosedContract.status", failClosedContract.status());
        fields.put(normalizedPrefix + ".failClosedContract.ready", Boolean.toString(failClosedContract.ready()));
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < entries.size(); index++) {
            fields.putAll(entries.get(index).artifactFields(normalizedPrefix + ".entry." + index));
        }
        fields.put("runtime.cuda.imageSamplerAbiPlan.present", "true");
        fields.put("runtime.cuda.imageSamplerAbiPlan.status", status());
        fields.put("runtime.cuda.imageSamplerAbiPlan.ready", Boolean.toString(ready()));
        fields.put("runtime.cuda.imageSamplerAbiPlan.productionSupport.enabled", "false");
        fields.put("runtime.cuda.imageSamplerAbiPlan.implementation.status", "planned");
        fields.put("runtime.cuda.imageSamplerAbiPlan.entry.count", Integer.toString(entries.size()));
        fields.put("runtime.cuda.imageSamplerAbiPlan.entry.ready.count", Long.toString(entryReadyCount()));
        fields.put("runtime.cuda.imageSamplerAbiPlan.entry.blocked.count", Long.toString(entryBlockedCount()));
        fields.put("runtime.cuda.imageSamplerAbiPlan.sourcePreview.enabled.count", Long.toString(sourcePreviewEnabledCount()));
        fields.put("runtime.cuda.imageSamplerAbiPlan.sourcePreview.folded.count", Long.toString(sourcePreviewFoldedCount()));
        fields.put("runtime.cuda.imageSamplerAbiPlan.sourcePreview.pending.count", Long.toString(sourcePreviewPendingCount()));
        fields.put("runtime.cuda.imageSamplerAbiPlan.sourcePreview.kernelParameterSlot.count", Long.toString(sourcePreviewKernelParameterSlotCount()));
        fields.put("runtime.cuda.imageSamplerAbiPlan.sourcePreview.metadataSlot.count", Long.toString(sourcePreviewMetadataSlotCount()));
        fields.put("runtime.cuda.imageSamplerAbiPlan.runtimeBinding.enabled.count", Long.toString(runtimeBindingEnabledCount()));
        fields.put("runtime.cuda.imageSamplerAbiPlan.runtimeBinding.plannedKernelParameterSlot.count", Long.toString(plannedRuntimeKernelParameterSlotCount()));
        fields.put("runtime.cuda.imageSamplerAbiPlan.runtimeBinding.plannedMetadataSlot.count", Long.toString(plannedRuntimeMetadataSlotCount()));
        fields.put("runtime.cuda.imageSamplerAbiPlan.runtimeBinding.kernelParameterSlot.count", Long.toString(runtimeBindingKernelParameterSlotCount()));
        fields.put("runtime.cuda.imageSamplerAbiPlan.failClosedContract.ready", Boolean.toString(failClosedContract.ready()));
        fields.put("runtime.cuda.imageSamplerAbiPlan.firstBlocker", firstBlocker());
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler ABI plan: ").append(status()).append('\n');
        builder.append("Entries: ").append(entryReadyCount()).append('/').append(entries.size()).append(" ready").append('\n');
        builder.append("Implementation: planned").append('\n');
        builder.append("Production support: disabled").append('\n');
        builder.append("Source preview: ")
                .append(sourcePreviewEnabledCount())
                .append(" enabled, ")
                .append(sourcePreviewFoldedCount())
                .append(" folded, ")
                .append(sourcePreviewPendingCount())
                .append(" pending")
                .append('\n');
        builder.append("Runtime binding: fail-closed").append('\n');
        builder.append("Preview kernel slots: ")
                .append(sourcePreviewKernelParameterSlotCount())
                .append(" total, ")
                .append(sourcePreviewMetadataSlotCount())
                .append(" metadata")
                .append('\n');
        builder.append("Planned runtime slots: ")
                .append(plannedRuntimeKernelParameterSlotCount())
                .append(" total, ")
                .append(plannedRuntimeMetadataSlotCount())
                .append(" metadata; active runtime slots: ")
                .append(runtimeBindingKernelParameterSlotCount())
                .append('\n');
        builder.append("Fail-closed binder contract: ").append(failClosedContract.status()).append('\n');
        builder.append("First blocker: ").append(firstBlocker()).append('\n');
        builder.append('\n').append("Planned ABI:").append('\n');
        for (Entry entry : entries) {
            builder.append("- ")
                    .append(entry.key())
                    .append(": ")
                    .append(entry.cudaAbiRole())
                    .append(" via ")
                    .append(entry.parameterCarrier())
                    .append(" (`")
                    .append(entry.cudaResourceKind())
                    .append("`, sourcePreview=")
                    .append(entry.sourcePreviewStatus())
                    .append(", previewSlots=")
                    .append(entry.sourcePreviewKernelParameterSlotCount())
                    .append(", plannedRuntimeSlots=")
                    .append(entry.plannedRuntimeKernelParameterSlotCount())
                    .append(")")
                    .append('\n');
        }
        return builder.toString();
    }

    private static Entry entry(CudaImageSamplerAbi.Descriptor descriptor) {
        return new Entry(
                descriptor.key(),
                descriptor.javaQualifiedName(),
                descriptor.openClType(),
                descriptor.cudaAbiRole(),
                descriptor.cudaResourceKind(),
                descriptor.parameterCarrier(),
                descriptor.requiredDriverSymbols(),
                descriptor.sourcePreviewStatus(),
                descriptor.sourcePreviewCarrier(),
                descriptor.sourcePreviewKernelParameterSlotCount(),
                descriptor.sourcePreviewMetadataSlotCount(),
                descriptor.runtimeBindingStatus(),
                descriptor.plannedRuntimeKernelParameterSlotCount(),
                descriptor.plannedRuntimeMetadataSlotCount(),
                descriptor.runtimeBindingKernelParameterSlotCount(),
                descriptor.implementationStatus(),
                descriptor.productionSupportEnabled()
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
