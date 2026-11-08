package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Hot path кэшей (REWORK_ARCHITECTURE.md, 13): попадание в кэш скомпилированных ядер в памяти (его платит каждый
 * вызов {@code @Kernel}-метода через фасад) и чтение образа с диска (платит первый запрос ядра после старта процесса).
 *
 * <p>Запуск: {@code ./gradlew :runtime:jmh -PjmhArgs="CompileCache -f 1 -wi 2 -i 3"}.</p>
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class CompileCacheBenchmarks {

    private Path dir;
    private GpuContext ctx;
    private IrModule module;
    private DiskCache disk;
    private byte[] image;

    @Setup
    public void setup() throws IOException {
        ctx = GpuContext.builder().api(FakeDeviceApi.withGpu().withoutJournal()).ignoreEnvironment().build();
        module = Fixtures.computeSine();
        ctx.compile(module);
        dir = Files.createTempDirectory("javatogpu-bench");
        disk = new DiskCache(dir, DiskCache.DEFAULT_MAX_BYTES);
        image = new byte[64 * 1024]; // типичный размер бинаря небольшого ядра
        for (int i = 0; i < image.length; i++) {
            image[i] = (byte) (i * 31);
        }
        disk.put("bench", image);
    }

    @TearDown
    public void tearDown() throws IOException {
        ctx.close();
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    /** Повторный запрос уже скомпилированного ядра: ключ кэша, поиск, учёт. */
    @Benchmark
    public void memoryCacheHit(Blackhole bh) {
        bh.consume(ctx.compile(module));
    }

    /** Чтение и проверка контрольной суммы образа в 64 КиБ. */
    @Benchmark
    public void diskImageRead(Blackhole bh) {
        bh.consume(disk.get("bench"));
    }
}
