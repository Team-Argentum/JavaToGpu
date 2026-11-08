package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Выгрузка сгенерированного OpenCL C в каталог: явная, из свойства, при попадании в дисковый кэш и выключенная. */
class SourceDumpTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty(GpuContext.DUMP_PROPERTY);
    }

    private static List<Path> clFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".cl")).toList();
        }
    }

    @Test
    void dumpsTheExactSourceGivenToTheDriver(@TempDir Path dir) throws IOException {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().noDiskCache().dumpSources(dir).build()) {
            assertTrue(ctx.compile(Fixtures.computeSine()).isOk());
        }
        List<Path> files = clFiles(dir);
        assertEquals(1, files.size());
        assertEquals(fake.sources().get(0), Files.readString(files.get(0)));
    }

    @Test
    void dumpsAlsoWhenTheBinaryComesFromTheDiskCache(@TempDir Path cache, @TempDir Path dump) throws IOException {
        for (int i = 0; i < 2; i++) {
            try (GpuContext ctx = GpuContext.builder().api(FakeDeviceApi.withGpu()).ignoreEnvironment()
                    .diskCache(cache).build()) {
                assertTrue(ctx.compile(Fixtures.computeSine()).isOk());
            }
        }
        try (GpuContext ctx = GpuContext.builder().api(FakeDeviceApi.withGpu()).ignoreEnvironment()
                .diskCache(cache).dumpSources(dump).build()) {
            assertTrue(ctx.compile(Fixtures.computeSine()).isOk());
            assertEquals(1, ctx.cacheStats().diskHits());
        }
        assertEquals(1, clFiles(dump).size());
    }

    @Test
    void readsDirectoryFromSystemProperty(@TempDir Path dir) throws IOException {
        System.setProperty(GpuContext.DUMP_PROPERTY, dir.toString());
        try (GpuContext ctx = GpuContext.builder().api(FakeDeviceApi.withGpu()).noDiskCache().build()) {
            assertTrue(ctx.compile(Fixtures.computeSine()).isOk());
        }
        assertEquals(1, clFiles(dir).size());
    }

    @Test
    void doesNotDumpByDefault(@TempDir Path dir) throws IOException {
        try (GpuContext ctx = GpuContext.builder().api(FakeDeviceApi.withGpu()).ignoreEnvironment().noDiskCache().build()) {
            assertTrue(ctx.compile(Fixtures.computeSine()).isOk());
        }
        assertEquals(0, clFiles(dir).size());
    }
}
