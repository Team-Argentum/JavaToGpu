package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.Result;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Дисковый кэш на настоящем драйвере (M12): бинарный образ программы, который отдаёт {@code clGetProgramInfo}, принимает
 * {@code clCreateProgramWithBinary}, ядро из образа считает так же, а негодный образ молча заменяется пересборкой.
 * Без драйвера тест пропускается. Запуск: {@code ./gradlew :runtime:deviceTest}.
 */
@Tag("device")
class DiskCacheDeviceTest {

    private static GpuContext open(Path dir, List<GpuEvent> events) {
        Result<GpuContext> r = GpuContext.builder().ignoreEnvironment().diskCache(dir).listener(events::add).tryBuild();
        Assumptions.assumeTrue(r.isOk(), "нет OpenCL-устройства");
        return r.value();
    }

    private static String detail(List<GpuEvent> events) {
        return events.stream().filter(e -> e.kind() == GpuEvent.Kind.COMPILE).reduce((a, b) -> b).orElseThrow().detail();
    }

    private static List<Path> images(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".gbc")).toList();
        }
    }

    /** Запускает computeSine и сверяет с {@code Math.sin} в пределах 4 ULP. */
    private static void checkSine(GpuContext ctx, CompiledKernel k) {
        float[] in = new float[256];
        Random rnd = new Random(5);
        for (int i = 0; i < in.length; i++) {
            in[i] = (rnd.nextFloat() - 0.5f) * 20f;
        }
        float[] out = new float[256];
        Result<LaunchStats> r = ctx.launch(k, LaunchConfig.of1D(256), in, out);
        assertTrue(r.isOk(), r.diagnostics().toString());
        for (int i = 0; i < in.length; i++) {
            float expected = (float) Math.sin(in[i]) + 2f;
            assertTrue(Math.abs(expected - out[i]) <= 4 * Math.ulp(expected) + 1e-6f, "i=" + i + ": " + expected + " / " + out[i]);
        }
    }

    @Test
    void imageFromTheRealDriverIsReusedByTheNextContext(@TempDir Path dir) throws IOException {
        List<GpuEvent> first = new ArrayList<>();
        try (GpuContext ctx = open(dir, first)) {
            Result<CompiledKernel> k = ctx.compile(Fixtures.computeSine());
            assertTrue(k.isOk(), k.diagnostics().toString());
            assertEquals("built", detail(first));
            checkSine(ctx, k.value());
        }
        Assumptions.assumeTrue(!images(dir).isEmpty(), "драйвер не отдаёт бинарные образы программ");

        List<GpuEvent> second = new ArrayList<>();
        try (GpuContext ctx = open(dir, second)) {
            Result<CompiledKernel> k = ctx.compile(Fixtures.computeSine());
            assertTrue(k.isOk(), k.diagnostics().toString());
            assertEquals("disk-hit", detail(second), "ядро собрано из образа, а не из исходника");
            assertEquals(1, ctx.cacheStats().diskHits());
            checkSine(ctx, k.value());
        }
    }

    @Test
    void corruptedAndForeignImagesAreReplacedByARebuild(@TempDir Path dir) throws IOException {
        try (GpuContext ctx = open(dir, new ArrayList<>())) {
            ctx.compile(Fixtures.computeSine());
        }
        Assumptions.assumeTrue(!images(dir).isEmpty(), "драйвер не отдаёт бинарные образы программ");
        Path file = images(dir).get(0);

        // 1. побитая контрольная сумма
        byte[] raw = Files.readAllBytes(file);
        raw[raw.length - 3] ^= 0x42;
        Files.write(file, raw);
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = open(dir, events)) {
            Result<CompiledKernel> k = ctx.compile(Fixtures.computeSine());
            assertEquals("built", detail(events));
            checkSine(ctx, k.value());
        }

        // 2. целый файл формата кэша, но внутри мусор вместо образа драйвера: драйвер откажет, ядро соберётся из исходника
        byte[] garbage = new byte[2048];
        new Random(1).nextBytes(garbage);
        Files.write(images(dir).get(0), DiskCache.encode(garbage));
        events.clear();
        try (GpuContext ctx = open(dir, events)) {
            Result<CompiledKernel> k = ctx.compile(Fixtures.computeSine());
            assertTrue(k.isOk(), k.diagnostics().toString());
            assertEquals("built", detail(events));
            assertEquals(1, ctx.cacheStats().diskMisses());
            checkSine(ctx, k.value());
        }

        // и после этого в кэше снова годный образ
        events.clear();
        try (GpuContext ctx = open(dir, events)) {
            ctx.compile(Fixtures.computeSine());
            assertEquals("disk-hit", detail(events));
        }
    }
}
