package net.sixik.ga_utils.javatogpu.api.observability;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Host-side timing receipt for one prepared GPU invocation.
 *
 * <p>Values are nanoseconds and host-observed counters. They are diagnostics, not a stable benchmark contract: driver
 * scheduling, queue mode, JVM warm-up, and backend implementation details can change the numbers between machines.</p>
 */
public record GpuPreparedInvocationTimings(
        long totalNanos,
        long bufferAllocateNanos,
        int bufferAllocateCount,
        long bufferReuseNanos,
        int bufferReuseCount,
        long uploadNanos,
        int uploadCount,
        long uploadBytes,
        int skippedUploadCount,
        long skippedUploadBytes,
        long bindNanos,
        int bindCount,
        long enqueueSubmitNanos,
        long enqueueWaitNanos,
        long queueFinishNanos,
        long readbackNanos,
        int readbackCount,
        long readbackBytes
) {

    public static GpuPreparedInvocationTimings empty() {
        return new GpuPreparedInvocationTimings(0L, 0L, 0, 0L, 0, 0L, 0, 0L, 0, 0L, 0L, 0, 0L, 0L, 0L, 0L, 0, 0L);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.preparedInvoke.timing" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        put(fields, normalizedPrefix + ".total.nanos", totalNanos);
        put(fields, normalizedPrefix + ".buffer.allocate.nanos", bufferAllocateNanos);
        fields.put(normalizedPrefix + ".buffer.allocate.count", Integer.toString(bufferAllocateCount));
        put(fields, normalizedPrefix + ".buffer.reuse.nanos", bufferReuseNanos);
        fields.put(normalizedPrefix + ".buffer.reuse.count", Integer.toString(bufferReuseCount));
        put(fields, normalizedPrefix + ".upload.nanos", uploadNanos);
        fields.put(normalizedPrefix + ".upload.count", Integer.toString(uploadCount));
        put(fields, normalizedPrefix + ".upload.bytes", uploadBytes);
        fields.put(normalizedPrefix + ".upload.skipped.count", Integer.toString(skippedUploadCount));
        put(fields, normalizedPrefix + ".upload.skipped.bytes", skippedUploadBytes);
        put(fields, normalizedPrefix + ".bind.nanos", bindNanos);
        fields.put(normalizedPrefix + ".bind.count", Integer.toString(bindCount));
        put(fields, normalizedPrefix + ".enqueue.submit.nanos", enqueueSubmitNanos);
        put(fields, normalizedPrefix + ".enqueue.wait.nanos", enqueueWaitNanos);
        put(fields, normalizedPrefix + ".queue.finish.nanos", queueFinishNanos);
        put(fields, normalizedPrefix + ".readback.nanos", readbackNanos);
        fields.put(normalizedPrefix + ".readback.count", Integer.toString(readbackCount));
        put(fields, normalizedPrefix + ".readback.bytes", readbackBytes);
        return Map.copyOf(fields);
    }

    private static void put(Map<String, String> fields, String key, long value) {
        fields.put(key, Long.toString(Math.max(0L, value)));
    }
}
