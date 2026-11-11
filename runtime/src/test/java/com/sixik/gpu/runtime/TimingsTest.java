package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link Timings}: накопление событий контекста по видам, счётчики кэша, потокобезопасность. */
class TimingsTest {

    private static FakeDeviceApi device() {
        return FakeDeviceApi.withGpu().onKernel("k0", l -> {
            ByteBuffer in = l.buffer(0);
            ByteBuffer out = l.buffer(2);
            for (int i = 0; i < l.intArg(1); i++) {
                out.putFloat(4 * i, in.getFloat(4 * i));
            }
        });
    }

    @Test
    void accumulatesUploadKernelAndReadbackOfEveryLaunch() {
        Timings timings = new Timings();
        try (GpuContext ctx = GpuContext.builder().api(device()).ignoreEnvironment().listener(timings).build()) {
            CompiledKernel k = ctx.compile(Fixtures.computeSine()).value();
            for (int i = 0; i < 3; i++) {
                assertTrue(ctx.launch(k, LaunchConfig.of1D(256), new float[256], new float[256]).isOk());
            }
        }
        Timings.Snapshot s = timings.snapshot();
        assertEquals(3, s.of(GpuEvent.Kind.UPLOAD).count());
        assertEquals(3 * 1024, s.of(GpuEvent.Kind.UPLOAD).bytes(),
                "only the input is uploaded; out[gid] is overwritten entirely (WriteCoverage)");
        assertEquals(3, s.of(GpuEvent.Kind.KERNEL).count());
        assertTrue(s.of(GpuEvent.Kind.KERNEL).nanos() > 0);
        assertEquals(3, s.of(GpuEvent.Kind.READBACK).count());
        assertEquals(3 * 1024, s.of(GpuEvent.Kind.READBACK).bytes(), "back - only the writable array");
        assertEquals(1, s.of(GpuEvent.Kind.DEVICE_SELECTED).count());
        assertEquals(1, s.of(GpuEvent.Kind.CLOSED).count());
    }

    @Test
    void countsBuiltMemoryHitAndDiskHitCompilations(@TempDir Path dir) {
        Timings first = new Timings();
        try (GpuContext ctx = GpuContext.builder().api(device()).ignoreEnvironment().diskCache(dir).listener(first).build()) {
            ctx.compile(Fixtures.computeSine());
            ctx.compile(Fixtures.computeSine());
            ctx.compile(Fixtures.computeSine());
        }
        Timings.Snapshot a = first.snapshot();
        assertEquals(1, a.built());
        assertEquals(2, a.memoryHits());
        assertEquals(0, a.diskHits());
        assertEquals(3, a.of(GpuEvent.Kind.COMPILE).count());

        Timings second = new Timings();
        try (GpuContext ctx = GpuContext.builder().api(device()).ignoreEnvironment().diskCache(dir).listener(second).build()) {
            ctx.compile(Fixtures.computeSine());
        }
        Timings.Snapshot b = second.snapshot();
        assertEquals(0, b.built());
        assertEquals(1, b.diskHits());
    }

    @Test
    void failedCompilationIsCountedSeparately() {
        Timings timings = new Timings();
        FakeDeviceApi fake = device().rejectBuildWhen(src -> true, "error: bad");
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().listener(timings).build()) {
            ctx.compile(Fixtures.computeSine());
        }
        assertEquals(1, timings.snapshot().failedBuilds());
        assertEquals(0, timings.snapshot().built());
    }

    @Test
    void variantNoteDoesNotBreakTheClassification() {
        Timings timings = new Timings();
        timings.onEvent(new GpuEvent(GpuEvent.Kind.COMPILE, "k", 5, 0, "built; variant f0 instead of f1: the device does not support FP64"));
        timings.onEvent(new GpuEvent(GpuEvent.Kind.COMPILE, "k", 0, 0, "cache-hit; variant f0 instead of f1: x"));
        Timings.Snapshot s = timings.snapshot();
        assertEquals(1, s.built());
        assertEquals(1, s.memoryHits());
    }

    @Test
    void resetClearsEverythingAndSnapshotsAreImmutable() {
        Timings timings = new Timings();
        timings.onEvent(new GpuEvent(GpuEvent.Kind.KERNEL, "k", 100, 0, ""));
        Timings.Snapshot before = timings.snapshot();
        timings.reset();
        assertEquals(1, before.of(GpuEvent.Kind.KERNEL).count(), "the snapshot does not change");
        assertEquals(0, timings.snapshot().of(GpuEvent.Kind.KERNEL).count());
        assertEquals(0, timings.snapshot().of(GpuEvent.Kind.KERNEL).nanos());
    }

    @Test
    void concurrentEventsAreNotLost() throws Exception {
        Timings timings = new Timings();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> all = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                all.add(pool.submit(() -> {
                    for (int i = 0; i < 10_000; i++) {
                        timings.onEvent(new GpuEvent(GpuEvent.Kind.KERNEL, "k", 3, 7, ""));
                    }
                }));
            }
            for (Future<?> f : all) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        Timings.Total k = timings.snapshot().of(GpuEvent.Kind.KERNEL);
        assertEquals(80_000, k.count());
        assertEquals(240_000, k.nanos());
        assertEquals(560_000, k.bytes());
    }
}
