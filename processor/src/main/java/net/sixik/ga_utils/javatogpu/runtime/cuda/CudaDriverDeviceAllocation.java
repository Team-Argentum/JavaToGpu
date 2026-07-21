package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns one CUDA Driver API device allocation created for a kernel argument.
 */
public final class CudaDriverDeviceAllocation implements AutoCloseable {

    private final int parameterIndex;
    private final String parameterName;
    private final String javaType;
    private final GpuKernelParameterAccess access;
    private final Object hostArray;
    private final int hostElementOffset;
    private final int elementCount;
    private final long byteSize;
    private final long devicePointer;
    private final boolean hostUploadCompleted;
    private final long memFreeAddress;
    private final CudaDriverLibrary.DriverApiInvoker invoker;
    private boolean readbackCompleted;
    private int readbackStatus = -1;
    private boolean closed;
    private int closeStatus;

    CudaDriverDeviceAllocation(
            int parameterIndex,
            String parameterName,
            String javaType,
            GpuKernelParameterAccess access,
            Object hostArray,
            int elementCount,
            long byteSize,
            long devicePointer,
            boolean hostUploadCompleted,
            long memFreeAddress,
            CudaDriverLibrary.DriverApiInvoker invoker
    ) {
        this(
                parameterIndex,
                parameterName,
                javaType,
                access,
                hostArray,
                0,
                elementCount,
                byteSize,
                devicePointer,
                hostUploadCompleted,
                memFreeAddress,
                invoker
        );
    }

    CudaDriverDeviceAllocation(
            int parameterIndex,
            String parameterName,
            String javaType,
            GpuKernelParameterAccess access,
            Object hostArray,
            int hostElementOffset,
            int elementCount,
            long byteSize,
            long devicePointer,
            boolean hostUploadCompleted,
            long memFreeAddress,
            CudaDriverLibrary.DriverApiInvoker invoker
    ) {
        this.parameterIndex = Math.max(0, parameterIndex);
        this.parameterName = parameterName == null || parameterName.isBlank()
                ? "arg" + this.parameterIndex
                : parameterName.trim();
        this.javaType = javaType == null || javaType.isBlank() ? "unknown" : javaType.trim();
        this.access = access == null ? GpuKernelParameterAccess.READ_WRITE : access;
        this.hostArray = hostArray;
        this.hostElementOffset = Math.max(0, hostElementOffset);
        this.elementCount = Math.max(0, elementCount);
        this.byteSize = Math.max(0L, byteSize);
        this.devicePointer = devicePointer;
        this.hostUploadCompleted = hostUploadCompleted;
        this.memFreeAddress = memFreeAddress;
        this.invoker = java.util.Objects.requireNonNull(invoker, "invoker");
    }

    public int parameterIndex() {
        return parameterIndex;
    }

    public String parameterName() {
        return parameterName;
    }

    public String javaType() {
        return javaType;
    }

    public GpuKernelParameterAccess access() {
        return access;
    }

    public Object hostArray() {
        return hostArray;
    }

    public int hostElementOffset() {
        return hostElementOffset;
    }

    public int hostElementEndExclusive() {
        return hostElementOffset + elementCount;
    }

    public int hostArrayLength() {
        return hostArray != null && hostArray.getClass().isArray()
                ? java.lang.reflect.Array.getLength(hostArray)
                : elementCount;
    }

    public boolean hostSliceEnabled() {
        return hostElementOffset != 0 || elementCount != hostArrayLength();
    }

    public int elementCount() {
        return elementCount;
    }

    public long byteSize() {
        return byteSize;
    }

    public long devicePointer() {
        return devicePointer;
    }

    public boolean hostUploadCompleted() {
        return hostUploadCompleted;
    }

    public boolean readbackRequired() {
        return access == GpuKernelParameterAccess.READ_WRITE;
    }

    public boolean readbackCompleted() {
        return readbackCompleted;
    }

    public int readbackStatus() {
        return readbackStatus;
    }

    void recordReadbackStatus(int status) {
        readbackStatus = status;
        readbackCompleted = status == CudaDriverLibrary.CUDA_SUCCESS;
    }

    public boolean closed() {
        return closed;
    }

    public int closeStatus() {
        return closeStatus;
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.deviceAllocation"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.deviceAllocation." + parameterIndex);
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", "true");
        fields.put(prefix + ".parameter.index", Integer.toString(parameterIndex));
        fields.put(prefix + ".parameter.name", parameterName);
        fields.put(prefix + ".parameter.javaType", javaType);
        fields.put(prefix + ".parameter.access", access.name());
        fields.put(prefix + ".hostSlice.enabled", Boolean.toString(hostSliceEnabled()));
        fields.put(prefix + ".hostElement.backingLength", Integer.toString(hostArrayLength()));
        fields.put(prefix + ".hostElement.offset", Integer.toString(hostElementOffset));
        fields.put(prefix + ".hostElement.endExclusive", Integer.toString(hostElementEndExclusive()));
        fields.put(prefix + ".element.count", Integer.toString(elementCount));
        fields.put(prefix + ".byteSize", Long.toString(byteSize));
        fields.put(prefix + ".devicePointer.present", Boolean.toString(devicePointer != 0L));
        fields.put(prefix + ".hostUpload.completed", Boolean.toString(hostUploadCompleted));
        fields.put(prefix + ".readback.required", Boolean.toString(readbackRequired()));
        fields.put(prefix + ".readback.completed", Boolean.toString(readbackCompleted));
        fields.put(prefix + ".readback.status", Integer.toString(readbackStatus));
        fields.put(prefix + ".closed", Boolean.toString(closed));
        fields.put(prefix + ".close.status", Integer.toString(closeStatus));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (devicePointer != 0L) {
            closeStatus = invoker.cuMemFree(devicePointer, memFreeAddress);
            if (closeStatus != CudaDriverLibrary.CUDA_SUCCESS) {
                closed = true;
                throw new IllegalStateException("CUDA cuMemFree failed with code " + closeStatus);
            }
        }
        closed = true;
    }
}
