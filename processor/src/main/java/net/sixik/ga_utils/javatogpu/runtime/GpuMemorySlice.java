package net.sixik.ga_utils.javatogpu.runtime;

import java.lang.reflect.Array;
import java.util.Objects;

/**
 * Array-backed memory view for uploading and reading back a contiguous subrange.
 *
 * <p>Use this when a kernel should operate on only part of a Java array without copying that range into a temporary
 * array first. The slice keeps the original backing array so read-write arguments can still be copied back into the
 * caller-owned storage.</p>
 *
 * <p>The offset and length are measured in array elements, not bytes. Backend binders are responsible for converting
 * those element counts to byte ranges for the concrete native API.</p>
 *
 * @param array backing Java array passed to the generated launcher
 * @param offset first element exposed to the GPU kernel
 * @param length number of elements exposed to the GPU kernel
 * @param <T> array type, for example {@code float[]} or a supported struct array
 */
public record GpuMemorySlice<T>(T array, int offset, int length) {

    public GpuMemorySlice {
        Objects.requireNonNull(array, "array");
        Class<?> type = array.getClass();
        if (!type.isArray()) {
            throw new IllegalArgumentException("GpuMemorySlice requires an array-backed value: " + type.getName());
        }
        int arrayLength = Array.getLength(array);
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0: " + offset);
        }
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0: " + length);
        }
        if (offset > arrayLength || length > arrayLength - offset) {
            throw new IllegalArgumentException(
                    "slice [" + offset + ", " + (offset + length) + ") exceeds array length " + arrayLength
            );
        }
    }

    /**
     * Creates a slice over {@code array[offset, offset + length)}.
     */
    public static <T> GpuMemorySlice<T> of(T array, int offset, int length) {
        return new GpuMemorySlice<>(array, offset, length);
    }

    /**
     * Creates a slice that exposes the full backing array.
     */
    public static <T> GpuMemorySlice<T> all(T array) {
        return new GpuMemorySlice<>(array, 0, Array.getLength(Objects.requireNonNull(array, "array")));
    }

    /**
     * Returns the first element index after the exposed range.
     */
    public int endExclusive() {
        return offset + length;
    }

    /**
     * Returns the element count of the original backing array.
     */
    public int backingArrayLength() {
        return Array.getLength(array);
    }

    /**
     * Returns whether this slice exposes the whole backing array.
     */
    public boolean coversWholeArray() {
        return offset == 0 && length == backingArrayLength();
    }

    /**
     * Human-readable range summary for diagnostics and examples.
     */
    public String summary() {
        return "offset=" + offset + ", length=" + length + ", backingLength=" + backingArrayLength();
    }
}
