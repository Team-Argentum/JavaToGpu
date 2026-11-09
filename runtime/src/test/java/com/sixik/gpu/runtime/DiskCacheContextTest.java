package com.sixik.gpu.runtime;

import com.sixik.gpu.codegen.opencl.EmitOptions;
import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Дисковый кэш в {@link GpuContext} на фейковом драйвере: попадание между контекстами (процессами), промахи при смене
 * устройства, драйвера, опций и версии кодгена, повреждённый и отвергнутый драйвером образ, драйвер без образов.
 */
class DiskCacheContextTest {

    private static FakeDeviceApi device() {
        return FakeDeviceApi.withGpu().onKernel("k0", l -> {
            ByteBuffer in = l.buffer(0);
            ByteBuffer out = l.buffer(2);
            for (int i = 0; i < l.intArg(1); i++) {
                out.putFloat(4 * i, (float) Math.sin(in.getFloat(4 * i)) + 2f);
            }
        });
    }

    private static GpuContext open(FakeDeviceApi fake, Path cache, List<GpuEvent> events) {
        return GpuContext.builder().api(fake).ignoreEnvironment().diskCache(cache).listener(events::add).build();
    }

    private static long files(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".gbc")).count();
        }
    }

    private static String lastCompileDetail(List<GpuEvent> events) {
        return events.stream().filter(e -> e.kind() == GpuEvent.Kind.COMPILE).reduce((a, b) -> b).orElseThrow().detail();
    }

    @AfterEach
    void clearProperty() {
        System.clearProperty(GpuContext.CACHE_PROPERTY);
    }

    @Test
    void secondContextBuildsFromTheDiskImageInsteadOfTheSource(@TempDir Path dir) throws IOException {
        List<GpuEvent> events1 = new ArrayList<>();
        FakeDeviceApi first = device();
        try (GpuContext ctx = open(first, dir, events1)) {
            assertTrue(ctx.compile(Fixtures.computeSine()).isOk());
            assertEquals("built", lastCompileDetail(events1));
            assertEquals(new CacheStats(0, 1, 1, 0, 1, 1), ctx.cacheStats());
        }
        assertEquals(1, first.sources().size());
        assertEquals(1, files(dir), "image written");

        List<GpuEvent> events2 = new ArrayList<>();
        FakeDeviceApi second = device();
        try (GpuContext ctx = open(second, dir, events2)) {
            Result<CompiledKernel> r = ctx.compile(Fixtures.computeSine());
            assertTrue(r.isOk(), r.diagnostics().toString());
            assertEquals("disk-hit", lastCompileDetail(events2));
            assertEquals(0, second.sources().size(), "the source was not passed to the driver");
            assertEquals(1, second.binaryLoads());
            assertEquals(new CacheStats(0, 1, 1, 1, 0, 0), ctx.cacheStats());

            // ядро, собранное из образа, работает
            float[] in = new float[256];
            in[1] = 1f;
            float[] out = new float[256];
            Result<LaunchStats> run = ctx.launch(r.value(), LaunchConfig.of1D(256), in, out);
            assertTrue(run.isOk(), run.diagnostics().toString());
            assertEquals((float) Math.sin(1f) + 2f, out[1]);
        }
        assertEquals(1, files(dir), "no repeated write");
    }

    @Test
    void memoryCacheIsAboveTheDiskOne(@TempDir Path dir) {
        List<GpuEvent> events = new ArrayList<>();
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake, dir, events)) {
            ctx.compile(Fixtures.computeSine());
            ctx.compile(Fixtures.computeSine());
            assertEquals("cache-hit", lastCompileDetail(events));
            assertEquals(new CacheStats(1, 1, 1, 0, 1, 1), ctx.cacheStats());
            assertEquals(0, fake.binaryLoads(), "the disk was not consulted for an image");
        }
    }

    @Test
    void anotherDeviceOrDriverDoesNotReuseTheImage(@TempDir Path dir) throws IOException {
        try (GpuContext ctx = open(device(), dir, new ArrayList<>())) {
            ctx.compile(Fixtures.computeSine());
        }
        FakeDeviceApi other = new FakeDeviceApi(FakeDeviceApi.Spec.gpu().withName("Another GPU")).onKernel("k0", l -> { });
        try (GpuContext ctx = open(other, dir, new ArrayList<>())) {
            assertTrue(ctx.compile(Fixtures.computeSine()).isOk());
            assertEquals(0, other.binaryLoads(), "another device's image is not used");
            assertEquals(1, other.sources().size());
        }
        assertEquals(2, files(dir), "each device has its own image");
    }

    @Test
    void differentEmitOptionsAreDifferentEntries(@TempDir Path dir) throws IOException {
        FakeDeviceApi fake = device();
        try (GpuContext ctx = open(fake, dir, new ArrayList<>())) {
            IrModule m = Fixtures.computeSine();
            ctx.compile(m, 0, EmitOptions.RELEASE);
            ctx.compile(m, 0, EmitOptions.DEBUG);
            ctx.compile(m, 0, EmitOptions.RELEASE.withFastMath(true));
        }
        assertEquals(3, files(dir));
    }

    @Test
    void newCodegenVersionInvalidatesOldImages(@TempDir Path dir) throws IOException {
        try (GpuContext ctx = GpuContext.builder().api(device()).ignoreEnvironment().diskCache(dir).codegenVersion("1").build()) {
            ctx.compile(Fixtures.computeSine());
        }
        FakeDeviceApi fake = device();
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().diskCache(dir).codegenVersion("2")
                .listener(events::add).build()) {
            assertTrue(ctx.compile(Fixtures.computeSine()).isOk());
            assertEquals("built", lastCompileDetail(events), "the version changed - the image does not fit");
            assertEquals(0, fake.binaryLoads());
        }
        assertEquals(2, files(dir), "the image of the new version sits next to the old one (the old one will be evicted by LRU)");
        // и обратно: прежняя версия снова находит свой образ
        FakeDeviceApi again = device();
        try (GpuContext ctx = GpuContext.builder().api(again).ignoreEnvironment().diskCache(dir).codegenVersion("1").build()) {
            ctx.compile(Fixtures.computeSine());
            assertEquals(1, again.binaryLoads());
        }
    }

    @Test
    void corruptedImageIsAMissAndIsRewritten(@TempDir Path dir) throws IOException {
        try (GpuContext ctx = open(device(), dir, new ArrayList<>())) {
            ctx.compile(Fixtures.computeSine());
        }
        Path file;
        try (Stream<Path> s = Files.list(dir)) {
            file = s.filter(p -> p.toString().endsWith(".gbc")).findFirst().orElseThrow();
        }
        byte[] raw = Files.readAllBytes(file);
        raw[raw.length / 2] ^= 0x7F;
        Files.write(file, raw);

        FakeDeviceApi fake = device();
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = open(fake, dir, events)) {
            assertTrue(ctx.compile(Fixtures.computeSine()).isOk());
            assertEquals("built", lastCompileDetail(events));
            assertEquals(1, fake.sources().size(), "built from source");
            assertEquals(0, fake.binaryLoads(), "the corrupted image was not shown to the driver");
            assertEquals(new CacheStats(0, 1, 1, 0, 1, 1), ctx.cacheStats());
        }
        byte[] repaired = Files.readAllBytes(file);
        assertFalse(java.util.Arrays.equals(raw, repaired));
        FakeDeviceApi third = device();
        try (GpuContext ctx = open(third, dir, new ArrayList<>())) {
            ctx.compile(Fixtures.computeSine());
            assertEquals(1, third.binaryLoads(), "after the rebuild the image is valid again");
        }
    }

    @Test
    void imageRejectedByTheDriverFallsBackToTheSource(@TempDir Path dir) throws IOException {
        try (GpuContext ctx = open(device(), dir, new ArrayList<>())) {
            ctx.compile(Fixtures.computeSine());
        }
        FakeDeviceApi fake = device().rejectingBinaries();
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = open(fake, dir, events)) {
            Result<CompiledKernel> r = ctx.compile(Fixtures.computeSine());
            assertTrue(r.isOk(), r.diagnostics().toString());
            assertEquals("built", lastCompileDetail(events));
            assertEquals(1, fake.binaryLoads(), "the image was offered to the driver");
            assertEquals(1, fake.sources().size(), "and after the rejection the kernel was built from source");
        }
        assertEquals(1, files(dir), "the unusable image was replaced with a new one");
    }

    @Test
    void driverWithoutBinariesWorksAndWritesNothing(@TempDir Path dir) throws IOException {
        FakeDeviceApi fake = device().withoutBinaries();
        try (GpuContext ctx = open(fake, dir, new ArrayList<>())) {
            assertTrue(ctx.compile(Fixtures.computeSine()).isOk());
            assertEquals(0, ctx.cacheStats().diskWrites());
        }
        assertEquals(0, files(dir));
    }

    @Test
    void failedCompilationIsNotCached(@TempDir Path dir) throws IOException {
        FakeDeviceApi fake = device().rejectBuildWhen(src -> true, "error: nope");
        try (GpuContext ctx = open(fake, dir, new ArrayList<>())) {
            assertFalse(ctx.compile(Fixtures.computeSine()).isOk());
        }
        assertEquals(0, files(dir));
    }

    @Test
    void cacheCanBeSwitchedOffAndLimited(@TempDir Path dir) throws IOException {
        try (GpuContext ctx = GpuContext.builder().api(device()).ignoreEnvironment().diskCache(dir).noDiskCache().build()) {
            ctx.compile(Fixtures.computeSine());
        }
        assertEquals(0, files(dir));

        try (GpuContext ctx = GpuContext.builder().api(device()).ignoreEnvironment().diskCache(dir).diskCacheLimit(10).build()) {
            assertTrue(ctx.compile(Fixtures.computeSine()).isOk());
            assertEquals(0, ctx.cacheStats().diskWrites(), "an image over the limit is not written");
        }
        assertEquals(0, files(dir));
    }

    @Test
    void injectedDriverDoesNotTouchTheUserCacheUnlessAskedTo(@TempDir Path dir) throws IOException {
        // без diskCache(...) и без свойства кэш подменённого драйвера выключен: тесты не пишут в каталог пользователя
        try (GpuContext ctx = GpuContext.builder().api(device()).ignoreEnvironment().build()) {
            ctx.compile(Fixtures.computeSine());
            assertEquals(new CacheStats(0, 1, 1, 0, 0, 0), ctx.cacheStats());
        }
    }

    @Test
    void environmentPropertyChoosesTheDirectoryOrSwitchesOff(@TempDir Path dir) throws IOException {
        System.setProperty(GpuContext.CACHE_PROPERTY, dir.toString());
        try (GpuContext ctx = GpuContext.builder().api(device()).build()) {
            ctx.compile(Fixtures.computeSine());
        }
        assertEquals(1, files(dir), "directory from the javatogpu.cache property");

        System.setProperty(GpuContext.CACHE_PROPERTY, "off");
        try (GpuContext ctx = GpuContext.builder().api(device()).diskCache(dir.resolve("explicit")).build()) {
            // явный каталог построителя побеждает окружение
            ctx.compile(Fixtures.computeSine());
        }
        assertEquals(1, files(dir.resolve("explicit")));

        try (GpuContext ctx = GpuContext.builder().api(device()).build()) {
            ctx.compile(Fixtures.computeSine());
            assertEquals(0, ctx.cacheStats().diskWrites(), "off disables the cache");
        }
    }

    @Test
    void eventsCarryTheDeviceIndex(@TempDir Path dir) {
        List<GpuEvent> events = new ArrayList<>();
        FakeDeviceApi fake = new FakeDeviceApi(FakeDeviceApi.Spec.cpu(), FakeDeviceApi.Spec.gpu()).onKernel("k0", l -> { });
        try (GpuContext ctx = open(fake, dir, events)) {
            assertEquals(1, ctx.device().index(), "GPU is preferred over CPU");
            ctx.compile(Fixtures.computeSine());
        }
        assertFalse(events.isEmpty());
        for (GpuEvent e : events) {
            assertEquals(1, e.device(), e.toString());
        }
        assertEquals(-1, new GpuEvent(GpuEvent.Kind.WARNING, "x", 0, 0, "").device(), "event outside the device");
    }
}
