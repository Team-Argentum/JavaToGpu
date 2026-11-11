package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.Types;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link GpuBuffer#readView()}: чтение без копирования в массив и безопасное время жизни вида. */
class GpuBufferViewTest {

    private static GpuContext open(FakeDeviceApi fake) {
        return GpuContext.builder().api(fake).ignoreEnvironment().build();
    }

    @Test
    void viewShowsTheBufferContentWithoutAHostArray() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        try (GpuContext ctx = open(fake); GpuBuffer b = ctx.allocate(Types.F32, 1000)) {
            float[] data = new float[1000];
            for (int i = 0; i < data.length; i++) {
                data[i] = i * 0.5f;
            }
            b.write(data);
            MemorySegment v = b.readView();
            assertEquals(4000, v.byteSize());
            assertTrue(v.isReadOnly());
            for (int i = 0; i < data.length; i++) {
                assertEquals(data[i], v.getAtIndex(ValueLayout.JAVA_FLOAT, i));
            }
            // запись в read-only сегмент: IllegalArgumentException (так бросает FFM)
            assertThrows(IllegalArgumentException.class, () -> v.setAtIndex(ValueLayout.JAVA_FLOAT, 0, 1f));
            assertEquals(1, fake.livePinned());
        }
        assertEquals(0, fake.livePinned(), "the view memory is released with the buffer");
    }

    @Test
    void nextReadViewReusesTheMemoryAndShowsNewContent() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        try (GpuContext ctx = open(fake); GpuBuffer b = ctx.allocate(Types.I32, 4)) {
            b.write(new int[]{1, 2, 3, 4});
            MemorySegment first = b.readView();
            b.write(new int[]{5, 6, 7, 8});
            MemorySegment second = b.readView();
            assertEquals(first.address(), second.address(), "the same pinned memory is reused");
            assertEquals(8, second.getAtIndex(ValueLayout.JAVA_INT, 3));
            assertEquals(8, first.getAtIndex(ValueLayout.JAVA_INT, 3), "an older view sees the latest read");
            assertEquals(1, fake.livePinned());
        }
    }

    @Test
    void viewIsInaccessibleAfterTheBufferIsClosed() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        try (GpuContext ctx = open(fake)) {
            GpuBuffer b = ctx.allocate(Types.F32, 16);
            MemorySegment v = b.readView();
            b.close();
            assertThrows(IllegalStateException.class, () -> v.getAtIndex(ValueLayout.JAVA_FLOAT, 0),
                    "a closed view must fail instead of touching freed memory");
            assertThrows(GpuException.class, b::readView);
            assertEquals(0, fake.livePinned());
        }
    }

    @Test
    void closingTheContextInvalidatesViews() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        MemorySegment v;
        try (GpuContext ctx = open(fake)) {
            GpuBuffer b = ctx.allocate(Types.F32, 16);
            v = b.readView();
        }
        assertThrows(IllegalStateException.class, () -> v.getAtIndex(ValueLayout.JAVA_FLOAT, 0));
        assertEquals(0, fake.livePinned());
    }

    @Test
    void readViewReportsAReadback() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        java.util.List<GpuEvent> events = new java.util.ArrayList<>();
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().listener(events::add).build();
             GpuBuffer b = ctx.allocate(Types.F64, 10)) {
            b.readView();
        }
        assertTrue(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.READBACK && e.bytes() == 80
                && e.detail().equals("view")), events.toString());
    }
}
