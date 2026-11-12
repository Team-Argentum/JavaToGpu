package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.optimizer.OptimizeOptions;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Данные драйвера о регистрах вместо оценки: они переживают попадание в дисковый кэш (лог сборки образа пуст),
 * предупреждение о давлении опирается на них, а {@link CompiledKernel#registers()} без них - на калиброванную оценку.
 * Устройство - фейк NVIDIA, «лог ptxas» задаётся тестом.
 */
class DriverStatsCacheTest {

    private static final String LOG_40 = """
            ptxas info    : Compiling entry function 'k0' for 'sm_120'
            ptxas info    : Function properties for k0
                0 bytes stack frame, 0 bytes spill stores, 0 bytes spill loads
            ptxas info    : Used 40 registers, used 0 barriers, 368 bytes cmem[0]
            """;

    private static final String LOG_SPILL = """
            ptxas info    : Compiling entry function 'k0' for 'sm_120'
            ptxas info    : Function properties for k0
                96 bytes stack frame, 48 bytes spill stores, 40 bytes spill loads
            ptxas info    : Used 255 registers, used 0 barriers, 368 bytes cmem[0]
            """;

    private static FakeDeviceApi nvidia(String log) {
        return new FakeDeviceApi(FakeDeviceApi.Spec.gpu().withVendor("NVIDIA Corporation")).successLog(log);
    }

    private static GpuContext.Builder builder(FakeDeviceApi fake, List<GpuEvent> events) {
        return GpuContext.builder().api(fake).ignoreEnvironment().listener(events::add);
    }

    @Test
    void driverRegistersSurviveADiskCacheHit(@TempDir Path dir) {
        try (GpuContext first = builder(nvidia(LOG_40), new ArrayList<>()).diskCache(dir).build()) {
            CompiledKernel k = first.compile(Fixtures.computeSine()).value();
            assertEquals(40, k.driverStats().registers());
            assertTrue(k.registersMeasured());
        }
        FakeDeviceApi second = nvidia(LOG_40);
        try (GpuContext ctx = builder(second, new ArrayList<>()).diskCache(dir).build()) {
            CompiledKernel k = ctx.compile(Fixtures.computeSine()).value();
            assertEquals(1, second.binaryLoads(), "the program comes from the disk cache, its build log is empty");
            assertEquals(40, k.driverStats().registers(), "the driver numbers are read back from the cache");
            assertEquals(40, k.registers());
        }
    }

    @Test
    void withoutADiskCacheAnEmptyLogLeavesTheEstimate() {
        try (GpuContext ctx = builder(nvidia(""), new ArrayList<>()).noDiskCache().build()) {
            CompiledKernel k = ctx.compile(Fixtures.computeSine()).value();
            assertFalse(k.registersMeasured());
            int e = k.estimatedRegisters();
            assertTrue(e > 0);
            assertEquals((int) Math.ceil(1.05 * e + 0.3), k.registers(), "the estimate is calibrated to driver units");
        }
    }

    @Test
    void spillsReportedByTheDriverProduceAWarning() {
        List<GpuEvent> events = new ArrayList<>();
        try (GpuContext ctx = builder(nvidia(LOG_SPILL), events).noDiskCache().build()) {
            assertTrue(ctx.compile(Fixtures.computeSine()).isOk());
        }
        assertTrue(events.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.WARNING
                && e.detail().contains("the driver spills 48 bytes")), events.toString());
    }

    @Test
    void driverDataReplacesAnEstimateAboveTheBudget() {
        // оценка выше бюджета 1, но драйвер выделил 40 регистров без проливания: предупреждать не о чем
        List<GpuEvent> measured = new ArrayList<>();
        try (GpuContext ctx = builder(nvidia(LOG_40), measured).noDiskCache()
                .optimize(OptimizeOptions.DEFAULT.withRegisterBudget(1)).build()) {
            assertTrue(ctx.compile(Fixtures.computeSine()).isOk());
        }
        assertFalse(measured.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.WARNING
                && e.detail().contains("register")), measured.toString());
        // без данных драйвера остаётся предупреждение по оценке
        List<GpuEvent> estimated = new ArrayList<>();
        try (GpuContext ctx = builder(nvidia(""), estimated).noDiskCache()
                .optimize(OptimizeOptions.DEFAULT.withRegisterBudget(1)).build()) {
            assertTrue(ctx.compile(Fixtures.computeSine()).isOk());
        }
        assertTrue(estimated.stream().anyMatch(e -> e.kind() == GpuEvent.Kind.WARNING
                && e.detail().contains("estimated register pressure")), estimated.toString());
    }

    @Test
    void driverStatsRecordsRoundTrip() {
        DriverStats s = new DriverStats(57, 8, 4, 16, 1234);
        DriverStats back = DriverStats.decode(s.encode());
        assertEquals(new DriverStats(57, 8, 4, 16, -1), back);
        assertEquals(null, DriverStats.decode("garbage".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(null, DriverStats.decode(DriverStats.UNKNOWN.encode()), "unknown numbers are not a record");
    }
}
