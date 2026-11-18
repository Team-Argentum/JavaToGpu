package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.Types;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.Test;

import com.sixik.gpu.runtime.device.HostMemory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GpuBuffer#readView()} и {@link GpuBuffer#writeView()}/{@link GpuBuffer#commitView()}: передачи без копирования
 * через Java-массив и безопасное время жизни видов.
 */
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
            HostMemory v = b.readView();
            assertEquals(4000, v.byteSize());
            assertTrue(v.isReadOnly());
            for (int i = 0; i < data.length; i++) {
                assertEquals(data[i], v.getFloat(4L * i));
            }
            // запись в read-only сегмент: IllegalArgumentException (так бросает FFM)
            assertThrows(IllegalArgumentException.class, () -> v.setFloat(4L * 0, 1f));
            assertEquals(1, fake.livePinned());
        }
        assertEquals(0, fake.livePinned(), "the view memory is released with the buffer");
    }

    @Test
    void nextReadViewReusesTheMemoryAndShowsNewContent() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        try (GpuContext ctx = open(fake); GpuBuffer b = ctx.allocate(Types.I32, 4)) {
            b.write(new int[]{1, 2, 3, 4});
            HostMemory first = b.readView();
            b.write(new int[]{5, 6, 7, 8});
            HostMemory second = b.readView();
            assertEquals(first.address(), second.address(), "the same pinned memory is reused");
            assertEquals(8, second.getInt(4L * 3));
            assertEquals(8, first.getInt(4L * 3), "an older view sees the latest read");
            assertEquals(1, fake.livePinned());
        }
    }

    @Test
    void viewIsInaccessibleAfterTheBufferIsClosed() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        try (GpuContext ctx = open(fake)) {
            GpuBuffer b = ctx.allocate(Types.F32, 16);
            HostMemory v = b.readView();
            b.close();
            assertThrows(IllegalStateException.class, () -> v.getFloat(4L * 0),
                    "a closed view must fail instead of touching freed memory");
            assertThrows(GpuException.class, b::readView);
            assertEquals(0, fake.livePinned());
        }
    }

    @Test
    void closingTheContextInvalidatesViews() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        HostMemory v;
        try (GpuContext ctx = open(fake)) {
            GpuBuffer b = ctx.allocate(Types.F32, 16);
            v = b.readView();
        }
        assertThrows(IllegalStateException.class, () -> v.getFloat(4L * 0));
        assertEquals(0, fake.livePinned());
    }

    @Test
    void writeViewIsFilledInPlaceAndCommittedWithoutAHostArray() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        try (GpuContext ctx = open(fake); GpuBuffer b = ctx.allocate(Types.F32, 1000)) {
            HostMemory w = b.writeView();
            assertEquals(4000, w.byteSize());
            assertFalse(w.isReadOnly());
            for (int i = 0; i < 1000; i++) {
                w.setFloat(4L * i, i * 0.25f);
            }
            b.commitView();
            float[] back = new float[1000];
            b.read(back);
            for (int i = 0; i < back.length; i++) {
                assertEquals(i * 0.25f, back[i]);
            }
            assertEquals(1, fake.livePinned(), "writeView and readView share one pinned allocation");
            assertEquals(w.address(), b.readView().address());
        }
        assertEquals(0, fake.livePinned());
    }

    @Test
    void committedDataIsACopyAndTheViewCanBeRefilled() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        try (GpuContext ctx = open(fake); GpuBuffer b = ctx.allocate(Types.I32, 3)) {
            HostMemory w = b.writeView();
            w.setInt(4L * 0, 1);
            w.setInt(4L * 1, 2);
            w.setInt(4L * 2, 3);
            b.commitView();
            w.setInt(4L * 0, 99); // не зафиксировано
            int[] back = new int[3];
            b.read(back);
            assertArrayEquals(new int[]{1, 2, 3}, back);
            b.commitView();
            b.read(back);
            assertArrayEquals(new int[]{99, 2, 3}, back);
        }
    }

    @Test
    void readViewThenWriteViewEditsTheBufferInPlace() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        try (GpuContext ctx = open(fake); GpuBuffer b = ctx.upload(new int[]{10, 20, 30})) {
            b.readView();
            HostMemory w = b.writeView();
            assertEquals(20, w.getInt(4L * 1), "the write view holds the last read content");
            w.setInt(4L * 1, 21);
            b.commitView();
            int[] back = new int[3];
            b.read(back);
            assertArrayEquals(new int[]{10, 21, 30}, back);
        }
    }

    @Test
    void commitWithoutAViewAndClosedBuffersAreRejected() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        try (GpuContext ctx = open(fake); GpuContext other = open(FakeDeviceApi.withGpu())) {
            GpuBuffer b = ctx.allocate(Types.F32, 8);
            GpuException noView = assertThrows(GpuException.class, b::commitView);
            assertTrue(noView.getMessage().contains("writeView"), noView.getMessage());
            HostMemory w = b.writeView();
            b.close();
            assertThrows(GpuException.class, b::writeView);
            assertThrows(GpuException.class, b::commitView);
            assertThrows(IllegalStateException.class, () -> w.setFloat(4L * 0, 1f),
                    "a closed write view must fail instead of touching freed memory");
            GpuBuffer foreign = other.allocate(Types.F32, 8);
            assertThrows(GpuException.class, () -> ctx.writeView(foreign));
            assertThrows(GpuException.class, () -> ctx.commitView(foreign));
        }
    }

    @Test
    void commitViewReportsAnUpload() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        java.util.List<GpuEvent> events = new java.util.ArrayList<>();
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().listener(events::add).build();
             GpuBuffer b = ctx.allocate(Types.F64, 10)) {
            b.writeView();
            b.commitView();
        }
        assertTrue(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.UPLOAD && e.bytes() == 80
                && e.detail().equals("view")), events.toString());
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
