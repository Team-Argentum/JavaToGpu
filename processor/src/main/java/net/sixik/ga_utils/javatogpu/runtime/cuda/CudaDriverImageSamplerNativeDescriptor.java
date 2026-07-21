package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeNativeMemoryAllocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeNativeMemoryAllocationRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeNativeMemoryServiceRegistry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owner for one planned or explicitly allocated CUDA native image/sampler descriptor.
 *
 * <p>Planned owners are metadata-only and remain inactive. Explicitly allocated owners are an opt-in internal step
 * that owns native host memory for a future {@code CUDA_RESOURCE_DESC} or {@code CUDA_TEXTURE_DESC}; they still do not
 * encode CUDA SDK struct bytes, create texture/surface objects, or bind runtime kernel arguments.</p>
 */
final class CudaDriverImageSamplerNativeDescriptor implements AutoCloseable {

    private final int parameterIndex;
    private final String parameterName;
    private final String javaType;
    private final String descriptorKind;
    private final String structName;
    private final int cleanupOrder;
    private final int rollbackOrder;
    private final GpuRuntimeNativeMemoryAllocation nativeMemory;
    private final boolean allocationEnabled;
    private final String ownershipStatus;
    private final String cleanupStatus;
    private final String rollbackStatus;
    private boolean closed;

    private CudaDriverImageSamplerNativeDescriptor(
            int parameterIndex,
            String parameterName,
            String javaType,
            String descriptorKind,
            String structName,
            int cleanupOrder,
            int rollbackOrder,
            GpuRuntimeNativeMemoryAllocation nativeMemory,
            boolean allocationEnabled,
            String ownershipStatus,
            String cleanupStatus,
            String rollbackStatus
    ) {
        this.parameterIndex = Math.max(0, parameterIndex);
        this.parameterName = parameterName == null || parameterName.isBlank()
                ? "arg" + this.parameterIndex
                : parameterName.trim();
        this.javaType = javaType == null || javaType.isBlank() ? "unknown" : javaType.trim();
        this.descriptorKind = normalizeDescriptorKind(descriptorKind);
        this.structName = structName == null || structName.isBlank()
                ? structFor(this.descriptorKind)
                : structName.trim();
        this.cleanupOrder = Math.max(0, cleanupOrder);
        this.rollbackOrder = Math.max(0, rollbackOrder);
        this.nativeMemory = nativeMemory;
        this.allocationEnabled = allocationEnabled;
        this.ownershipStatus = normalize(ownershipStatus, "planned-inactive");
        this.cleanupStatus = normalize(cleanupStatus, "planned-inactive");
        this.rollbackStatus = normalize(rollbackStatus, "planned-inactive");
    }

    static CudaDriverImageSamplerNativeDescriptor plannedResource(
            int parameterIndex,
            String parameterName,
            String javaType,
            int cleanupOrder,
            int rollbackOrder
    ) {
        return new CudaDriverImageSamplerNativeDescriptor(
                parameterIndex,
                parameterName,
                javaType,
                "resource",
                "CUDA_RESOURCE_DESC",
                cleanupOrder,
                rollbackOrder,
                null,
                false,
                "planned-inactive",
                "planned-inactive",
                "planned-inactive"
        );
    }

    static CudaDriverImageSamplerNativeDescriptor plannedTexture(
            int parameterIndex,
            String parameterName,
            String javaType,
            int cleanupOrder,
            int rollbackOrder
    ) {
        return new CudaDriverImageSamplerNativeDescriptor(
                parameterIndex,
                parameterName,
                javaType,
                "texture",
                "CUDA_TEXTURE_DESC",
                cleanupOrder,
                rollbackOrder,
                null,
                false,
                "planned-inactive",
                "planned-inactive",
                "planned-inactive"
        );
    }

    static CudaDriverImageSamplerNativeDescriptor allocatedResource(
            int parameterIndex,
            String parameterName,
            String javaType,
            int cleanupOrder,
            int rollbackOrder,
            int nativeByteSize
    ) {
        return allocated(
                parameterIndex,
                parameterName,
                javaType,
                "resource",
                "CUDA_RESOURCE_DESC",
                cleanupOrder,
                rollbackOrder,
                nativeByteSize,
                GpuRuntimeNativeMemoryServiceRegistry.loadWithBuiltIns()
        );
    }

    static CudaDriverImageSamplerNativeDescriptor allocatedResource(
            int parameterIndex,
            String parameterName,
            String javaType,
            int cleanupOrder,
            int rollbackOrder,
            int nativeByteSize,
            GpuRuntimeNativeMemoryServiceRegistry nativeMemoryServices
    ) {
        return allocated(
                parameterIndex,
                parameterName,
                javaType,
                "resource",
                "CUDA_RESOURCE_DESC",
                cleanupOrder,
                rollbackOrder,
                nativeByteSize,
                nativeMemoryServices
        );
    }

    static CudaDriverImageSamplerNativeDescriptor allocatedTexture(
            int parameterIndex,
            String parameterName,
            String javaType,
            int cleanupOrder,
            int rollbackOrder,
            int nativeByteSize
    ) {
        return allocated(
                parameterIndex,
                parameterName,
                javaType,
                "texture",
                "CUDA_TEXTURE_DESC",
                cleanupOrder,
                rollbackOrder,
                nativeByteSize,
                GpuRuntimeNativeMemoryServiceRegistry.loadWithBuiltIns()
        );
    }

    static CudaDriverImageSamplerNativeDescriptor allocatedTexture(
            int parameterIndex,
            String parameterName,
            String javaType,
            int cleanupOrder,
            int rollbackOrder,
            int nativeByteSize,
            GpuRuntimeNativeMemoryServiceRegistry nativeMemoryServices
    ) {
        return allocated(
                parameterIndex,
                parameterName,
                javaType,
                "texture",
                "CUDA_TEXTURE_DESC",
                cleanupOrder,
                rollbackOrder,
                nativeByteSize,
                nativeMemoryServices
        );
    }

    int parameterIndex() {
        return parameterIndex;
    }

    String parameterName() {
        return parameterName;
    }

    String javaType() {
        return javaType;
    }

    String descriptorKind() {
        return descriptorKind;
    }

    String structName() {
        return structName;
    }

    int cleanupOrder() {
        return cleanupOrder;
    }

    int rollbackOrder() {
        return rollbackOrder;
    }

    long nativeAddress() {
        return nativeMemory == null ? 0L : nativeMemory.nativeAddress();
    }

    int nativeByteSize() {
        return nativeMemory == null ? 0 : nativeMemory.nativeByteSize();
    }

    String nativeMemoryServiceId() {
        return nativeMemory == null ? "none" : nativeMemory.serviceId();
    }

    boolean nativeAddressPresent() {
        return nativeAddress() != 0L;
    }

    boolean allocationEnabled() {
        return allocationEnabled;
    }

    String ownershipStatus() {
        return ownershipStatus;
    }

    String cleanupStatus() {
        return cleanupStatus;
    }

    String rollbackStatus() {
        return rollbackStatus;
    }

    boolean active() {
        return !closed && allocationEnabled && nativeAddressPresent() && "active".equals(ownershipStatus);
    }

    boolean closed() {
        return closed;
    }

    Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerNativeDescriptorOwner"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".parameter.index", Integer.toString(parameterIndex));
        fields.put(normalizedPrefix + ".parameter.name", parameterName);
        fields.put(normalizedPrefix + ".parameter.javaType", javaType);
        fields.put(normalizedPrefix + ".descriptor.kind", descriptorKind);
        fields.put(normalizedPrefix + ".struct", structName);
        fields.put(normalizedPrefix + ".cleanup.order", Integer.toString(cleanupOrder));
        fields.put(normalizedPrefix + ".rollback.order", Integer.toString(rollbackOrder));
        fields.put(normalizedPrefix + ".nativeAddress.present", Boolean.toString(nativeAddressPresent()));
        fields.put(normalizedPrefix + ".nativeAddress", Long.toString(nativeAddress()));
        fields.put(normalizedPrefix + ".nativeByteSize", Integer.toString(nativeByteSize()));
        fields.put(normalizedPrefix + ".nativeMemory.service.id", nativeMemoryServiceId());
        fields.put(normalizedPrefix + ".allocation.enabled", Boolean.toString(allocationEnabled));
        fields.put(normalizedPrefix + ".ownership.status", ownershipStatus);
        fields.put(normalizedPrefix + ".cleanup.status", cleanupStatus);
        fields.put(normalizedPrefix + ".rollback.status", rollbackStatus);
        fields.put(normalizedPrefix + ".active", Boolean.toString(active()));
        fields.put(normalizedPrefix + ".closed", Boolean.toString(closed));
        return Collections.unmodifiableMap(fields);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (nativeMemory != null) {
            nativeMemory.close();
        }
        closed = true;
    }

    private static CudaDriverImageSamplerNativeDescriptor allocated(
            int parameterIndex,
            String parameterName,
            String javaType,
            String descriptorKind,
            String structName,
            int cleanupOrder,
            int rollbackOrder,
            int nativeByteSize,
            GpuRuntimeNativeMemoryServiceRegistry nativeMemoryServices
    ) {
        int byteSize = Math.max(1, nativeByteSize);
        GpuRuntimeNativeMemoryServiceRegistry services = nativeMemoryServices == null
                ? GpuRuntimeNativeMemoryServiceRegistry.loadWithBuiltIns()
                : nativeMemoryServices;
        GpuRuntimeNativeMemoryAllocation nativeMemory = services.allocate(
                GpuRuntimeNativeMemoryAllocationRequest.cudaDescriptor(structName, byteSize, true)
        );
        return new CudaDriverImageSamplerNativeDescriptor(
                parameterIndex,
                parameterName,
                javaType,
                descriptorKind,
                structName,
                cleanupOrder,
                rollbackOrder,
                nativeMemory,
                true,
                "active",
                "active",
                "active"
        );
    }

    private static String normalizeDescriptorKind(String descriptorKind) {
        if ("texture".equals(descriptorKind)) {
            return "texture";
        }
        return "resource";
    }

    private static String structFor(String descriptorKind) {
        return "texture".equals(descriptorKind) ? "CUDA_TEXTURE_DESC" : "CUDA_RESOURCE_DESC";
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
