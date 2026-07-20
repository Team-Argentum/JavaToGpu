package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationBindingSummary;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Closeable CUDA kernel argument-frame receipt owned by a prepared kernel.
 */
public final class CudaKernelArgumentFrame implements AutoCloseable {

    private final String binderId;
    private final GpuRuntimeInvocationBindingSummary bindingSummary;
    private final boolean nativePointerTablePresent;
    private final boolean deviceMemoryPresent;
    private final List<CudaDriverDeviceAllocation> deviceAllocations;
    private final PointerBuffer kernelParameterTable;
    private final List<PointerBuffer> kernelArgumentSlots;
    private final List<ByteBuffer> scalarArgumentSlots;
    private final long localSharedMemoryByteSize;
    private final CudaLocalSharedMemoryLayout localSharedMemoryLayout;
    private boolean closed;

    private CudaKernelArgumentFrame(
            String binderId,
            GpuRuntimeInvocationBindingSummary bindingSummary,
            boolean nativePointerTablePresent,
            boolean deviceMemoryPresent,
            List<CudaDriverDeviceAllocation> deviceAllocations,
            PointerBuffer kernelParameterTable,
            List<PointerBuffer> kernelArgumentSlots,
            List<ByteBuffer> scalarArgumentSlots,
            long localSharedMemoryByteSize,
            CudaLocalSharedMemoryLayout localSharedMemoryLayout
    ) {
        this.binderId = binderId == null || binderId.isBlank()
                ? "cuda-argument-binder:unknown"
                : binderId.trim();
        this.bindingSummary = bindingSummary == null
                ? GpuRuntimeInvocationBindingSummary.empty()
                : bindingSummary;
        this.deviceAllocations = deviceAllocations == null ? List.of() : List.copyOf(deviceAllocations);
        this.kernelParameterTable = kernelParameterTable;
        this.kernelArgumentSlots = kernelArgumentSlots == null ? List.of() : List.copyOf(kernelArgumentSlots);
        this.scalarArgumentSlots = scalarArgumentSlots == null ? List.of() : List.copyOf(scalarArgumentSlots);
        this.localSharedMemoryLayout = localSharedMemoryLayout == null
                ? CudaLocalSharedMemoryLayout.empty()
                : localSharedMemoryLayout;
        this.localSharedMemoryByteSize = this.localSharedMemoryLayout.present()
                ? this.localSharedMemoryLayout.totalByteSize()
                : Math.max(0L, localSharedMemoryByteSize);
        this.nativePointerTablePresent = nativePointerTablePresent || kernelParameterTable != null;
        this.deviceMemoryPresent = deviceMemoryPresent || !this.deviceAllocations.isEmpty();
    }

    public static CudaKernelArgumentFrame empty(String binderId) {
        return new CudaKernelArgumentFrame(
                binderId,
                GpuRuntimeInvocationBindingSummary.empty(),
                false,
                false,
                List.of(),
                null,
                List.of(),
                List.of(),
                0L,
                CudaLocalSharedMemoryLayout.empty()
        );
    }

    static CudaKernelArgumentFrame deviceBindings(
            String binderId,
            GpuRuntimeInvocationBindingSummary bindingSummary,
            List<CudaDriverDeviceAllocation> deviceAllocations,
            PointerBuffer kernelParameterTable,
            List<PointerBuffer> kernelArgumentSlots
    ) {
        return new CudaKernelArgumentFrame(
                binderId,
                bindingSummary,
                kernelParameterTable != null,
                deviceAllocations != null && !deviceAllocations.isEmpty(),
                deviceAllocations,
                kernelParameterTable,
                kernelArgumentSlots,
                List.of(),
                0L,
                CudaLocalSharedMemoryLayout.empty()
        );
    }

    static CudaKernelArgumentFrame nativeBindings(
            String binderId,
            GpuRuntimeInvocationBindingSummary bindingSummary,
            List<CudaDriverDeviceAllocation> deviceAllocations,
            PointerBuffer kernelParameterTable,
            List<PointerBuffer> kernelArgumentSlots,
            List<ByteBuffer> scalarArgumentSlots
    ) {
        return nativeBindings(
                binderId,
                bindingSummary,
                deviceAllocations,
                kernelParameterTable,
                kernelArgumentSlots,
                scalarArgumentSlots,
                0L,
                CudaLocalSharedMemoryLayout.empty()
        );
    }

    static CudaKernelArgumentFrame nativeBindings(
            String binderId,
            GpuRuntimeInvocationBindingSummary bindingSummary,
            List<CudaDriverDeviceAllocation> deviceAllocations,
            PointerBuffer kernelParameterTable,
            List<PointerBuffer> kernelArgumentSlots,
            List<ByteBuffer> scalarArgumentSlots,
            long localSharedMemoryByteSize
    ) {
        return nativeBindings(
                binderId,
                bindingSummary,
                deviceAllocations,
                kernelParameterTable,
                kernelArgumentSlots,
                scalarArgumentSlots,
                localSharedMemoryByteSize,
                CudaLocalSharedMemoryLayout.empty()
        );
    }

    static CudaKernelArgumentFrame nativeBindings(
            String binderId,
            GpuRuntimeInvocationBindingSummary bindingSummary,
            List<CudaDriverDeviceAllocation> deviceAllocations,
            PointerBuffer kernelParameterTable,
            List<PointerBuffer> kernelArgumentSlots,
            List<ByteBuffer> scalarArgumentSlots,
            long localSharedMemoryByteSize,
            CudaLocalSharedMemoryLayout localSharedMemoryLayout
    ) {
        return new CudaKernelArgumentFrame(
                binderId,
                bindingSummary,
                kernelParameterTable != null,
                deviceAllocations != null && !deviceAllocations.isEmpty(),
                deviceAllocations,
                kernelParameterTable,
                kernelArgumentSlots,
                scalarArgumentSlots,
                localSharedMemoryByteSize,
                localSharedMemoryLayout
        );
    }

    public String binderId() {
        return binderId;
    }

    public GpuRuntimeInvocationBindingSummary bindingSummary() {
        return bindingSummary;
    }

    public boolean nativePointerTablePresent() {
        return nativePointerTablePresent;
    }

    public boolean deviceMemoryPresent() {
        return deviceMemoryPresent;
    }

    public List<CudaDriverDeviceAllocation> deviceAllocations() {
        return deviceAllocations;
    }

    public long kernelParameterTableAddress() {
        return kernelParameterTable == null ? 0L : MemoryUtil.memAddress(kernelParameterTable);
    }

    public int kernelParameterSlotCount() {
        return kernelArgumentSlots.size() + scalarArgumentSlots.size();
    }

    public int deviceAllocationCount() {
        return deviceAllocations.size();
    }

    public int scalarArgumentSlotCount() {
        return scalarArgumentSlots.size();
    }

    public int scalarArgumentByteSize() {
        int byteSize = 0;
        for (ByteBuffer scalarArgumentSlot : scalarArgumentSlots) {
            if (scalarArgumentSlot != null) {
                byteSize += scalarArgumentSlot.capacity();
            }
        }
        return byteSize;
    }

    public long localSharedMemoryByteSize() {
        return localSharedMemoryByteSize;
    }

    CudaLocalSharedMemoryLayout localSharedMemoryLayout() {
        return localSharedMemoryLayout;
    }

    public int readbackRequiredCount() {
        int count = 0;
        for (CudaDriverDeviceAllocation allocation : deviceAllocations) {
            if (allocation.readbackRequired()) {
                count++;
            }
        }
        return count;
    }

    public boolean closed() {
        return closed;
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.argumentFrame"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.argumentFrame");
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", "true");
        fields.put(prefix + ".binder.id", binderId);
        fields.put(prefix + ".nativePointerTable.present", Boolean.toString(nativePointerTablePresent));
        fields.put(prefix + ".deviceMemory.present", Boolean.toString(deviceMemoryPresent));
        fields.put(prefix + ".deviceAllocation.count", Integer.toString(deviceAllocations.size()));
        fields.put(prefix + ".scalarArgumentSlot.count", Integer.toString(scalarArgumentSlotCount()));
        fields.put(prefix + ".scalarArgumentSlot.byteSize", Integer.toString(scalarArgumentByteSize()));
        fields.put(prefix + ".localSharedMemory.present", Boolean.toString(localSharedMemoryByteSize > 0L));
        fields.put(prefix + ".localSharedMemory.byteSize", Long.toString(localSharedMemoryByteSize));
        fields.put(prefix + ".localSharedMemory.slice.count", Integer.toString(localSharedMemoryLayout.sliceCount()));
        fields.put(prefix + ".localSharedMemory.hiddenOffsetParameter.count", Integer.toString(localSharedMemoryLayout.hiddenOffsetParameterCount()));
        fields.putAll(localSharedMemoryLayout.artifactFields(prefix + ".localSharedMemory.layout"));
        fields.put(prefix + ".readback.required.count", Integer.toString(readbackRequiredCount()));
        fields.put(prefix + ".kernelParameterTable.present", Boolean.toString(kernelParameterTable != null));
        fields.put(prefix + ".kernelParameterSlot.count", Integer.toString(kernelParameterSlotCount()));
        fields.put(prefix + ".binding.argument.count", Integer.toString(bindingSummary.argumentBindingCount()));
        fields.put(prefix + ".binding.buffer.count", Integer.toString(bindingSummary.bufferBindingCount()));
        fields.put(prefix + ".binding.local.count", Integer.toString(bindingSummary.localBindingCount()));
        fields.put(prefix + ".binding.scalar.count", Integer.toString(bindingSummary.scalarBindingCount()));
        fields.put(prefix + ".closed", Boolean.toString(closed));
        for (int index = 0; index < deviceAllocations.size(); index++) {
            fields.putAll(deviceAllocations.get(index).artifactFields(prefix + ".deviceAllocation." + index));
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        RuntimeException failure = null;
        for (CudaDriverDeviceAllocation allocation : deviceAllocations) {
            try {
                allocation.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        for (PointerBuffer slot : kernelArgumentSlots) {
            if (slot != null) {
                MemoryUtil.memFree(slot);
            }
        }
        for (ByteBuffer slot : scalarArgumentSlots) {
            if (slot != null) {
                MemoryUtil.memFree(slot);
            }
        }
        if (kernelParameterTable != null) {
            MemoryUtil.memFree(kernelParameterTable);
        }
        closed = true;
        if (failure != null) {
            throw failure;
        }
    }

    static void closeAll(List<CudaDriverDeviceAllocation> allocations) {
        RuntimeException failure = null;
        for (CudaDriverDeviceAllocation allocation : allocations == null ? List.<CudaDriverDeviceAllocation>of() : allocations) {
            try {
                allocation.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    static void freePointerBuffers(PointerBuffer table, List<PointerBuffer> slots) {
        freeArgumentStorage(table, slots, List.of());
    }

    static void freeArgumentStorage(PointerBuffer table, List<PointerBuffer> pointerSlots, List<ByteBuffer> scalarSlots) {
        for (PointerBuffer slot : pointerSlots == null ? List.<PointerBuffer>of() : pointerSlots) {
            if (slot != null) {
                MemoryUtil.memFree(slot);
            }
        }
        for (ByteBuffer slot : scalarSlots == null ? List.<ByteBuffer>of() : scalarSlots) {
            if (slot != null) {
                MemoryUtil.memFree(slot);
            }
        }
        if (table != null) {
            MemoryUtil.memFree(table);
        }
    }
}
