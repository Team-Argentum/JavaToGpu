package net.sixik.ga_utils.javatogpu.runtime;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Closeable native host-memory allocation returned by a runtime native-memory service.
 */
public final class GpuRuntimeNativeMemoryAllocation implements AutoCloseable {

    private final String serviceId;
    private final String serviceVersion;
    private final GpuRuntimeNativeMemoryAllocationRequest request;
    private final ByteBuffer byteBuffer;
    private final long nativeAddress;
    private final Runnable releaseAction;
    private boolean closed;

    public GpuRuntimeNativeMemoryAllocation(
            String serviceId,
            String serviceVersion,
            GpuRuntimeNativeMemoryAllocationRequest request,
            ByteBuffer byteBuffer,
            long nativeAddress,
            Runnable releaseAction
    ) {
        this.serviceId = normalize(serviceId, "native-memory:unknown");
        this.serviceVersion = normalize(serviceVersion, "1");
        this.request = Objects.requireNonNull(request, "request");
        this.byteBuffer = Objects.requireNonNull(byteBuffer, "byteBuffer");
        this.nativeAddress = Math.max(0L, nativeAddress);
        this.releaseAction = Objects.requireNonNull(releaseAction, "releaseAction");
    }

    public String serviceId() {
        return serviceId;
    }

    public String serviceVersion() {
        return serviceVersion;
    }

    public GpuRuntimeNativeMemoryAllocationRequest request() {
        return request;
    }

    public ByteBuffer byteBuffer() {
        return byteBuffer;
    }

    public long nativeAddress() {
        return closed ? 0L : nativeAddress;
    }

    public boolean nativeAddressPresent() {
        return nativeAddress() != 0L;
    }

    public int nativeByteSize() {
        return byteBuffer.capacity();
    }

    public boolean closed() {
        return closed;
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.nativeMemory.allocation"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".service.id", serviceId);
        fields.put(normalizedPrefix + ".service.version", serviceVersion);
        fields.put(normalizedPrefix + ".nativeAddress.present", Boolean.toString(nativeAddressPresent()));
        fields.put(normalizedPrefix + ".nativeAddress", Long.toString(nativeAddress()));
        fields.put(normalizedPrefix + ".nativeByteSize", Integer.toString(nativeByteSize()));
        fields.put(normalizedPrefix + ".closed", Boolean.toString(closed));
        fields.putAll(request.artifactFields(normalizedPrefix + ".request"));
        return Collections.unmodifiableMap(fields);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        releaseAction.run();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
