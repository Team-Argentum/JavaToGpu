package net.sixik.ga_utils.javatogpu.runtime;

import java.lang.reflect.Array;
import java.util.Objects;

/**
 * Explicit array-backed memory view for backends that can upload/read back a contiguous subrange.
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

    public static <T> GpuMemorySlice<T> of(T array, int offset, int length) {
        return new GpuMemorySlice<>(array, offset, length);
    }

    public static <T> GpuMemorySlice<T> all(T array) {
        return new GpuMemorySlice<>(array, 0, Array.getLength(Objects.requireNonNull(array, "array")));
    }

    public int endExclusive() {
        return offset + length;
    }

    public int backingArrayLength() {
        return Array.getLength(array);
    }

    public boolean coversWholeArray() {
        return offset == 0 && length == backingArrayLength();
    }

    public String summary() {
        return "offset=" + offset + ", length=" + length + ", backingLength=" + backingArrayLength();
    }
}
