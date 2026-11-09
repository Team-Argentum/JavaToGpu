package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.Types;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * То же сравнение на настоящем OpenCL-устройстве: полная цена запуска вместе с драйвером, передачами и ядром
 * {@code out[g] = in[g] * k + n}. Нужен драйвер OpenCL: без него {@code setup} бросает исключение.
 *
 * <p>Запуск: {@code ./gradlew :runtime:jmh -PjmhArgs="DeviceLaunch -f 1 -wi 2 -i 3"}.</p>
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class DeviceLaunchBenchmarks {

    /** Число элементов: малое показывает накладные расходы, большое - долю передач. */
    @Param({"1024", "1048576"})
    public int n;

    private GpuContext ctx;
    private CompiledKernel kernel;
    private LaunchConfig config;
    private float[] in;
    private float[] out;
    private GpuBuffer bufIn;
    private GpuBuffer bufOut;
    private PreparedLauncher arrays;
    private PreparedLauncher buffers;

    @Setup
    public void setup() {
        var built = GpuContext.builder().tryBuild();
        if (!built.isOk()) {
            throw new IllegalStateException("no OpenCL device: " + built.diagnostics());
        }
        ctx = built.value();
        kernel = ctx.compile(FakeLaunchBenchmarks.scaled()).value();
        config = LaunchConfig.of1D(n);
        in = new float[n];
        out = new float[n];
        bufIn = ctx.allocate(Types.F32, n);
        bufOut = ctx.allocate(Types.F32, n);
        arrays = ctx.prepare(kernel, in, out, 1, 2f).value();
        buffers = ctx.prepare(kernel, bufIn, bufOut, 1, 2f).value();
    }

    @TearDown
    public void tearDown() {
        ctx.close();
    }

    @Benchmark
    public void plainLaunchWithArrays(Blackhole bh) {
        bh.consume(ctx.launch(kernel, config, in, out, 1, 2f));
    }

    @Benchmark
    public void preparedLaunchWithArrays(Blackhole bh) {
        bh.consume(arrays.launch(config));
    }

    @Benchmark
    public void plainLaunchWithBuffers(Blackhole bh) {
        bh.consume(ctx.launch(kernel, config, bufIn, bufOut, 1, 2f));
    }

    @Benchmark
    public void preparedLaunchWithBuffers(Blackhole bh) {
        bh.consume(buffers.launch(config));
    }
}
