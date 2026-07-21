package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit opt-in diagnostic for native CUDA image/sampler descriptor host-memory allocation.
 *
 * <p>This report intentionally sits outside the aggregate fail-closed gate. It allocates and immediately releases
 * zeroed native host memory for descriptor owner skeletons, but still does not encode CUDA SDK struct bytes, call CUDA
 * texture/surface object creation functions, or bind image/sampler handles into kernel arguments.</p>
 */
public record CudaImageSamplerNativeDescriptorAllocationResultReport(
        int resourceDescriptorByteSize,
        int textureDescriptorByteSize,
        String allocationTransactionPlanStatus,
        boolean allocationTransactionPlanPresent,
        boolean allocationApplyEnabled,
        boolean nativeMemoryAllocationEnabled,
        boolean sdkStructByteEncodingEnabled,
        boolean cleanupApplyEnabled,
        boolean rollbackApplyEnabled,
        boolean objectCreationEnabled,
        boolean runtimeBindingEnabled,
        String nativeMemoryServiceSummary,
        String statusBeforeClose,
        String firstBlockerBeforeClose,
        long entryCount,
        long entryReadyCount,
        long entryBlockedCount,
        long descriptorOwnerCount,
        long resourceDescriptorOwnerCount,
        long textureDescriptorOwnerCount,
        long activeDescriptorOwnerCountBeforeClose,
        long nativeAddressPresentCountBeforeClose,
        long allocationEnabledCount,
        long cleanupActiveCountBeforeClose,
        long rollbackActiveCountBeforeClose,
        long nativeByteSize,
        boolean closedAfterClose,
        String statusAfterClose,
        String firstBlockerAfterClose,
        long activeDescriptorOwnerCountAfterClose,
        long nativeAddressPresentCountAfterClose,
        long cleanupActiveCountAfterClose,
        long rollbackActiveCountAfterClose
) {

    public CudaImageSamplerNativeDescriptorAllocationResultReport {
        resourceDescriptorByteSize = Math.max(1, resourceDescriptorByteSize);
        textureDescriptorByteSize = Math.max(1, textureDescriptorByteSize);
        allocationTransactionPlanStatus = normalize(allocationTransactionPlanStatus, "not-present");
        nativeMemoryServiceSummary = normalize(nativeMemoryServiceSummary, "none");
        statusBeforeClose = normalize(statusBeforeClose, "blocked");
        firstBlockerBeforeClose = normalize(firstBlockerBeforeClose, "none");
        statusAfterClose = normalize(statusAfterClose, "blocked");
        firstBlockerAfterClose = normalize(firstBlockerAfterClose, "none");
        entryCount = Math.max(0, entryCount);
        entryReadyCount = Math.max(0, entryReadyCount);
        entryBlockedCount = Math.max(0, entryBlockedCount);
        descriptorOwnerCount = Math.max(0, descriptorOwnerCount);
        resourceDescriptorOwnerCount = Math.max(0, resourceDescriptorOwnerCount);
        textureDescriptorOwnerCount = Math.max(0, textureDescriptorOwnerCount);
        activeDescriptorOwnerCountBeforeClose = Math.max(0, activeDescriptorOwnerCountBeforeClose);
        nativeAddressPresentCountBeforeClose = Math.max(0, nativeAddressPresentCountBeforeClose);
        allocationEnabledCount = Math.max(0, allocationEnabledCount);
        cleanupActiveCountBeforeClose = Math.max(0, cleanupActiveCountBeforeClose);
        rollbackActiveCountBeforeClose = Math.max(0, rollbackActiveCountBeforeClose);
        nativeByteSize = Math.max(0, nativeByteSize);
        activeDescriptorOwnerCountAfterClose = Math.max(0, activeDescriptorOwnerCountAfterClose);
        nativeAddressPresentCountAfterClose = Math.max(0, nativeAddressPresentCountAfterClose);
        cleanupActiveCountAfterClose = Math.max(0, cleanupActiveCountAfterClose);
        rollbackActiveCountAfterClose = Math.max(0, rollbackActiveCountAfterClose);
    }

    public static CudaImageSamplerNativeDescriptorAllocationResultReport inspectSample() {
        return inspectSample(128, 64);
    }

    public static CudaImageSamplerNativeDescriptorAllocationResultReport inspectSample(
            int resourceDescriptorByteSize,
            int textureDescriptorByteSize
    ) {
        int resourceByteSize = Math.max(1, resourceDescriptorByteSize);
        int textureByteSize = Math.max(1, textureDescriptorByteSize);
        CudaImageSamplerNativeDescriptorAllocationResult result =
                CudaImageSamplerNativeDescriptorAllocationResult.allocate(samplePlan(), resourceByteSize, textureByteSize);
        Snapshot beforeClose = Snapshot.from(result);
        result.close();
        Snapshot afterClose = Snapshot.from(result);
        return new CudaImageSamplerNativeDescriptorAllocationResultReport(
                resourceByteSize,
                textureByteSize,
                result.allocationTransactionPlanStatus(),
                result.allocationTransactionPlanPresent(),
                result.allocationApplyEnabled(),
                result.nativeMemoryAllocationEnabled(),
                result.sdkStructByteEncodingEnabled(),
                result.cleanupApplyEnabled(),
                result.rollbackApplyEnabled(),
                result.objectCreationEnabled(),
                result.runtimeBindingEnabled(),
                result.nativeMemoryServiceSummary(),
                beforeClose.status(),
                beforeClose.firstBlocker(),
                beforeClose.entryCount(),
                beforeClose.entryReadyCount(),
                beforeClose.entryBlockedCount(),
                beforeClose.descriptorOwnerCount(),
                beforeClose.resourceDescriptorOwnerCount(),
                beforeClose.textureDescriptorOwnerCount(),
                beforeClose.activeDescriptorOwnerCount(),
                beforeClose.nativeAddressPresentCount(),
                beforeClose.allocationEnabledCount(),
                beforeClose.cleanupActiveCount(),
                beforeClose.rollbackActiveCount(),
                beforeClose.nativeByteSize(),
                result.closed(),
                afterClose.status(),
                afterClose.firstBlocker(),
                afterClose.activeDescriptorOwnerCount(),
                afterClose.nativeAddressPresentCount(),
                afterClose.cleanupActiveCount(),
                afterClose.rollbackActiveCount()
        );
    }

    public boolean ready() {
        return "ready".equals(statusBeforeClose)
                && "none".equals(firstBlockerBeforeClose)
                && "blocked".equals(allocationTransactionPlanStatus)
                && allocationTransactionPlanPresent
                && allocationApplyEnabled
                && nativeMemoryAllocationEnabled
                && !sdkStructByteEncodingEnabled
                && cleanupApplyEnabled
                && rollbackApplyEnabled
                && !objectCreationEnabled
                && !runtimeBindingEnabled
                && !"none".equals(nativeMemoryServiceSummary)
                && entryCount == 3
                && entryReadyCount == entryCount
                && entryBlockedCount == 0
                && descriptorOwnerCount == 4
                && resourceDescriptorOwnerCount == 2
                && textureDescriptorOwnerCount == 2
                && activeDescriptorOwnerCountBeforeClose == descriptorOwnerCount
                && nativeAddressPresentCountBeforeClose == descriptorOwnerCount
                && allocationEnabledCount == descriptorOwnerCount
                && cleanupActiveCountBeforeClose == descriptorOwnerCount
                && rollbackActiveCountBeforeClose == descriptorOwnerCount
                && nativeByteSize == expectedNativeByteSize()
                && closedAfterClose
                && "blocked".equals(statusAfterClose)
                && "cuda-image-sampler-native-descriptor-allocation-result-closed".equals(firstBlockerAfterClose)
                && activeDescriptorOwnerCountAfterClose == 0
                && nativeAddressPresentCountAfterClose == 0
                && cleanupActiveCountAfterClose == 0
                && rollbackActiveCountAfterClose == 0;
    }

    public String status() {
        return ready() ? "ready" : "blocked";
    }

    public long expectedNativeByteSize() {
        return (resourceDescriptorByteSize * 2L) + (textureDescriptorByteSize * 2L);
    }

    public String firstBlocker() {
        if (!"ready".equals(statusBeforeClose)) {
            return "cuda-image-sampler-native-descriptor-allocation-result-status-mismatch:" + statusBeforeClose;
        }
        if (!"none".equals(firstBlockerBeforeClose)) {
            return "cuda-image-sampler-native-descriptor-allocation-result-unexpected-blocker:" + firstBlockerBeforeClose;
        }
        if (!allocationTransactionPlanPresent) {
            return "cuda-image-sampler-native-descriptor-allocation-result-plan-missing";
        }
        if (!allocationApplyEnabled || !nativeMemoryAllocationEnabled || !cleanupApplyEnabled || !rollbackApplyEnabled) {
            return "cuda-image-sampler-native-descriptor-allocation-result-lifecycle-disabled";
        }
        if (sdkStructByteEncodingEnabled || objectCreationEnabled || runtimeBindingEnabled) {
            return "cuda-image-sampler-native-descriptor-allocation-result-crossed-disabled-boundary";
        }
        if ("none".equals(nativeMemoryServiceSummary)) {
            return "cuda-image-sampler-native-descriptor-allocation-result-native-memory-service-missing";
        }
        if (entryCount != 3 || entryReadyCount != entryCount || entryBlockedCount != 0) {
            return "cuda-image-sampler-native-descriptor-allocation-result-entry-count-mismatch";
        }
        if (descriptorOwnerCount != 4 || resourceDescriptorOwnerCount != 2 || textureDescriptorOwnerCount != 2) {
            return "cuda-image-sampler-native-descriptor-allocation-result-owner-count-mismatch";
        }
        if (activeDescriptorOwnerCountBeforeClose != descriptorOwnerCount
                || nativeAddressPresentCountBeforeClose != descriptorOwnerCount
                || allocationEnabledCount != descriptorOwnerCount) {
            return "cuda-image-sampler-native-descriptor-allocation-result-owner-not-active-before-close";
        }
        if (cleanupActiveCountBeforeClose != descriptorOwnerCount || rollbackActiveCountBeforeClose != descriptorOwnerCount) {
            return "cuda-image-sampler-native-descriptor-allocation-result-lifecycle-not-active-before-close";
        }
        if (nativeByteSize != expectedNativeByteSize()) {
            return "cuda-image-sampler-native-descriptor-allocation-result-byte-size-mismatch:" + nativeByteSize;
        }
        if (!closedAfterClose || !"blocked".equals(statusAfterClose)) {
            return "cuda-image-sampler-native-descriptor-allocation-result-close-state-mismatch";
        }
        if (activeDescriptorOwnerCountAfterClose != 0
                || nativeAddressPresentCountAfterClose != 0
                || cleanupActiveCountAfterClose != 0
                || rollbackActiveCountAfterClose != 0) {
            return "cuda-image-sampler-native-descriptor-allocation-result-cleanup-incomplete";
        }
        return "none";
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerNativeDescriptorAllocationResultReport"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerNativeDescriptorAllocationResultReport");
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler native descriptor allocation result: ").append(status()).append('\n');
        builder.append("Allocation transaction plan: ").append(allocationTransactionPlanStatus).append('\n');
        builder.append("Descriptor owners: resource=").append(resourceDescriptorOwnerCount)
                .append(", texture=").append(textureDescriptorOwnerCount)
                .append(", total=").append(descriptorOwnerCount).append('\n');
        builder.append("Native addresses before close: ").append(nativeAddressPresentCountBeforeClose).append('\n');
        builder.append("Native addresses after close: ").append(nativeAddressPresentCountAfterClose).append('\n');
        builder.append("Native bytes: expected=").append(expectedNativeByteSize())
                .append(", actual=").append(nativeByteSize).append('\n');
        builder.append("Disabled boundaries: sdkStructByteEncoding=").append(sdkStructByteEncodingEnabled)
                .append(", objectCreation=").append(objectCreationEnabled)
                .append(", runtimeBinding=").append(runtimeBindingEnabled).append('\n');
        builder.append("First blocker: ").append(firstBlocker()).append('\n');
        return builder.toString();
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", "true");
        fields.put(prefix + ".status", status());
        fields.put(prefix + ".ready", Boolean.toString(ready()));
        fields.put(prefix + ".resourceDescriptor.byteSize", Integer.toString(resourceDescriptorByteSize));
        fields.put(prefix + ".textureDescriptor.byteSize", Integer.toString(textureDescriptorByteSize));
        fields.put(prefix + ".allocationTransactionPlan.status", allocationTransactionPlanStatus);
        fields.put(prefix + ".allocationTransactionPlan.present", Boolean.toString(allocationTransactionPlanPresent));
        fields.put(prefix + ".allocationApply.enabled", Boolean.toString(allocationApplyEnabled));
        fields.put(prefix + ".nativeMemoryAllocation.enabled", Boolean.toString(nativeMemoryAllocationEnabled));
        fields.put(prefix + ".sdkStructByteEncoding.enabled", Boolean.toString(sdkStructByteEncodingEnabled));
        fields.put(prefix + ".cleanupApply.enabled", Boolean.toString(cleanupApplyEnabled));
        fields.put(prefix + ".rollbackApply.enabled", Boolean.toString(rollbackApplyEnabled));
        fields.put(prefix + ".objectCreation.enabled", Boolean.toString(objectCreationEnabled));
        fields.put(prefix + ".runtimeBinding.enabled", Boolean.toString(runtimeBindingEnabled));
        fields.put(prefix + ".nativeMemory.service.summary", nativeMemoryServiceSummary);
        fields.put(prefix + ".status.beforeClose", statusBeforeClose);
        fields.put(prefix + ".firstBlocker.beforeClose", firstBlockerBeforeClose);
        fields.put(prefix + ".entry.count", Long.toString(entryCount));
        fields.put(prefix + ".entry.ready.count", Long.toString(entryReadyCount));
        fields.put(prefix + ".entry.blocked.count", Long.toString(entryBlockedCount));
        fields.put(prefix + ".descriptorOwner.count", Long.toString(descriptorOwnerCount));
        fields.put(prefix + ".resourceDescriptorOwner.count", Long.toString(resourceDescriptorOwnerCount));
        fields.put(prefix + ".textureDescriptorOwner.count", Long.toString(textureDescriptorOwnerCount));
        fields.put(prefix + ".activeDescriptorOwner.beforeClose.count", Long.toString(activeDescriptorOwnerCountBeforeClose));
        fields.put(prefix + ".nativeAddress.present.beforeClose.count", Long.toString(nativeAddressPresentCountBeforeClose));
        fields.put(prefix + ".allocation.enabled.count", Long.toString(allocationEnabledCount));
        fields.put(prefix + ".cleanup.active.beforeClose.count", Long.toString(cleanupActiveCountBeforeClose));
        fields.put(prefix + ".rollback.active.beforeClose.count", Long.toString(rollbackActiveCountBeforeClose));
        fields.put(prefix + ".nativeByteSize.expected", Long.toString(expectedNativeByteSize()));
        fields.put(prefix + ".nativeByteSize", Long.toString(nativeByteSize));
        fields.put(prefix + ".closed.afterClose", Boolean.toString(closedAfterClose));
        fields.put(prefix + ".status.afterClose", statusAfterClose);
        fields.put(prefix + ".firstBlocker.afterClose", firstBlockerAfterClose);
        fields.put(prefix + ".activeDescriptorOwner.afterClose.count", Long.toString(activeDescriptorOwnerCountAfterClose));
        fields.put(prefix + ".nativeAddress.present.afterClose.count", Long.toString(nativeAddressPresentCountAfterClose));
        fields.put(prefix + ".cleanup.active.afterClose.count", Long.toString(cleanupActiveCountAfterClose));
        fields.put(prefix + ".rollback.active.afterClose.count", Long.toString(rollbackActiveCountAfterClose));
        fields.put(prefix + ".firstBlocker", firstBlocker());
    }

    private static CudaImageSamplerNativeDescriptorAllocationTransactionPlan samplePlan() {
        return CudaImageSamplerNativeDescriptorAllocationTransactionPlan.from(
                CudaImageSamplerNativeDescriptorAllocationPreflight.from(
                        CudaImageSamplerNativeDescriptorEncodingPlan.from(
                                CudaImageSamplerDescriptorPayloadModel.from(
                                        CudaImageSamplerDescriptorBuildPlan.from(
                                                List.of(
                                                        new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY),
                                                        new GpuKernelParameterDescriptor("outputImage", Image2DWriteOnly.class.getName(), GpuKernelParameterAccess.READ_WRITE),
                                                        new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE)
                                                ),
                                                new Object[]{
                                                        Image2DReadOnly.borrowed(0xCAFE_7A01L, 8, 4),
                                                        Image2DWriteOnly.borrowed(0xCAFE_7A02L, 8, 4),
                                                        Sampler.borrowed(0xCAFE_7A03L)
                                                }
                                        )
                                )
                        )
                )
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record Snapshot(
            String status,
            String firstBlocker,
            long entryCount,
            long entryReadyCount,
            long entryBlockedCount,
            long descriptorOwnerCount,
            long resourceDescriptorOwnerCount,
            long textureDescriptorOwnerCount,
            long activeDescriptorOwnerCount,
            long nativeAddressPresentCount,
            long allocationEnabledCount,
            long cleanupActiveCount,
            long rollbackActiveCount,
            long nativeByteSize
    ) {
        static Snapshot from(CudaImageSamplerNativeDescriptorAllocationResult result) {
            return new Snapshot(
                    result.status(),
                    result.firstBlocker(),
                    result.entries().size(),
                    result.entryReadyCount(),
                    result.entryBlockedCount(),
                    result.descriptorOwnerCount(),
                    result.resourceDescriptorOwnerCount(),
                    result.textureDescriptorOwnerCount(),
                    result.activeDescriptorOwnerCount(),
                    result.nativeAddressPresentCount(),
                    result.allocationEnabledCount(),
                    result.cleanupActiveCount(),
                    result.rollbackActiveCount(),
                    result.nativeByteSize()
            );
        }
    }
}
