package com.sixik.gpu.runtime.ffm;

import com.sixik.gpu.runtime.device.HostArena;
import com.sixik.gpu.runtime.device.HostMemory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Память хоста FFM-бэкенда: контракт {@link HostMemory} и мост в {@code MemorySegment}. */
class FfmMemoryTest {

    private static HostArena arena(HostArena.Kind kind) {
        return FfmMemory.arena(kind);
    }

    @Test
    void ffmMemoryIsTheSameSegment() {
        try (HostArena a = arena(HostArena.Kind.CONFINED)) {
            HostMemory m = a.allocate(16, 8);
            java.lang.foreign.MemorySegment seg = FfmMemory.segment(m);
            seg.set(java.lang.foreign.ValueLayout.JAVA_INT, 4, 9);
            assertEquals(9, m.getInt(4));
            assertEquals(m.address(), seg.address());
            assertThrows(IllegalArgumentException.class,
                    () -> FfmMemory.segment(com.sixik.gpu.runtime.device.HeapMemory.allocate(8)));
        }
    }

    @Test
    void providerIsFoundAndPreferredOnJava25() {
        var providers = com.sixik.gpu.runtime.device.DeviceApis.providers();
        assertTrue(providers.stream().anyMatch(p -> p.name().equals(FfmDeviceApiProvider.NAME)), providers.toString());
        assertEquals(FfmDeviceApiProvider.NAME, providers.get(0).name());
    }

    // ---- общий контракт HostMemory (одинаковый текст в тестах обоих бэкендов) ------------------------

    @Test
    void valuesAndArraysRoundTripInNativeOrder() {
        try (HostArena a = arena(HostArena.Kind.CONFINED)) {
            HostMemory m = a.allocate(64, 8);
            assertEquals(64, m.byteSize());
            assertTrue(m.address() != 0, "native memory has an address");
            for (int i = 0; i < 64; i++) {
                assertEquals(0, m.getByte(i), "fresh memory is zeroed");
            }
            m.setInt(1, 0x01020304); // невыровненный доступ допустим
            assertEquals(0x01020304, m.getInt(1));
            assertEquals(java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN ? 0x04 : 0x01, m.getByte(1));
            m.setDouble(8, -0.0);
            assertEquals(Double.doubleToRawLongBits(-0.0), m.getLong(8));
            m.setFloat(16, Float.NaN);
            assertTrue(Float.isNaN(m.getFloat(16)));
            m.setShort(20, (short) -2);
            assertEquals((char) 0xFFFE, m.getChar(20));
            float[] f = {1.5f, -2f, 3.25f};
            m.copyFrom(f, 1, 24, 2);
            assertEquals(-2f, m.getFloat(24));
            assertEquals(3.25f, m.getFloat(28));
            float[] back = new float[3];
            m.copyTo(24, back, 1, 2);
            assertArrayEquals(new float[]{0f, -2f, 3.25f}, back);
            long[] l = {Long.MIN_VALUE, 7};
            m.copyFrom(l, 0, 32, 2);
            long[] lb = new long[2];
            m.copyTo(32, lb, 0, 2);
            assertArrayEquals(l, lb);
        }
    }

    @Test
    void slicesReadOnlyViewsAndBoundsBehaveLikeSegments() {
        try (HostArena a = arena(HostArena.Kind.SHARED)) {
            HostMemory m = a.allocate(16, 8);
            assertTrue(m.isShared());
            HostMemory s = m.asSlice(8, 8);
            s.setLong(0, 42);
            assertEquals(42, m.getLong(8), "a slice is a view on the same memory");
            assertEquals(m.address() + 8, s.address());
            HostMemory ro = m.asReadOnly();
            assertTrue(ro.isReadOnly());
            assertEquals(42, ro.getLong(8));
            assertThrows(IllegalArgumentException.class, () -> ro.setLong(8, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> m.getLong(9));
            assertThrows(IndexOutOfBoundsException.class, () -> m.copyFrom(new int[8], 0, 0, 5));
        }
        try (HostArena a = arena(HostArena.Kind.CONFINED)) {
            assertFalse(a.allocate(8, 8).isShared(), "confined memory is not split between threads");
        }
    }

    @Test
    void closedArenaMakesItsMemoryAndAttachedViewsUnusable() {
        HostMemory m;
        HostMemory attached;
        try (HostArena owner = arena(HostArena.Kind.SHARED)) {
            HostMemory foreign = owner.allocate(8, 8);
            HostArena scope = arena(HostArena.Kind.SHARED);
            m = scope.allocate(8, 8);
            attached = scope.attach(foreign);
            attached.setInt(0, 5);
            assertEquals(5, foreign.getInt(0), "attach does not copy");
            scope.close();
            assertThrows(IllegalStateException.class, () -> m.getInt(0));
            assertThrows(IllegalStateException.class, () -> attached.getInt(0));
            assertEquals(5, foreign.getInt(0), "the attached memory itself stays alive");
        }
    }
}
