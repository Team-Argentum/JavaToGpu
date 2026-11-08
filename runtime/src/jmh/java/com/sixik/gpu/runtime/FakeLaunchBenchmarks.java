package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;
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

import java.util.concurrent.TimeUnit;

/**
 * Накладные расходы рантайма на один запуск без драйвера: устройство - {@link FakeDeviceApi} с пустым ядром, поэтому
 * измеряется только то, что делает сама библиотека (проверка аргументов, упаковка, учёт буферов, установка аргументов).
 * Сравниваются обычный запуск ({@code GpuContext.launch}) и {@link PreparedLauncher}, на массивах хоста и на
 * {@link GpuBuffer}.
 *
 * <p>Запуск: {@code ./gradlew :runtime:jmh -PjmhArgs="FakeLaunch -f 1 -wi 2 -i 3"}.</p>
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class FakeLaunchBenchmarks {

    private static final int N = 1024;

    private GpuContext ctx;
    private CompiledKernel kernel;
    private LaunchConfig config;
    private float[] in;
    private float[] out;
    private GpuBuffer bufIn;
    private GpuBuffer bufOut;
    private PreparedLauncher arrays;
    private PreparedLauncher buffers;
    private int counter;

    @Setup
    public void setup() {
        ctx = GpuContext.builder().api(FakeDeviceApi.withGpu().withoutJournal().onKernel("k0", l -> { }))
                .ignoreEnvironment().build();
        kernel = ctx.compile(scaled()).value();
        config = LaunchConfig.of1D(N);
        in = new float[N];
        out = new float[N];
        bufIn = ctx.allocate(Types.F32, N);
        bufOut = ctx.allocate(Types.F32, N);
        arrays = ctx.prepare(kernel, in, out, 1, 2f).value();
        buffers = ctx.prepare(kernel, bufIn, bufOut, 1, 2f).value();
    }

    @TearDown
    public void tearDown() {
        ctx.close();
    }

    /** Ядро {@code out[g] = in[g] * k + n}: параметры (in, out, int n, float k). */
    static IrModule scaled() {
        IrBuilder b = new IrBuilder();
        int a = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, a, a, Types.I32, Types.F32);
        int g = b.declareLocal(Types.I32);
        b.endFunction(b.block(
                b.localDecl(g, b.intrinsic(0, b.constI32(0))),
                b.store(b.elemPtr(b.param(1), b.localGet(g)),
                        b.binary(Op.ADD, b.binary(Op.MUL, b.load(b.elemPtr(b.param(0), b.localGet(g))), b.param(3)),
                                b.convert(Types.F32, b.param(2))))));
        return b.build();
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

    @Benchmark
    public void preparedLaunchWithBuffersChangingAScalar(Blackhole bh) {
        bh.consume(buffers.set(2, ++counter).launch(config));
    }
}
