package com.sixik.gpu.api;

import com.sixik.gpu.api.types.Float4;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Hot path JVM-fallback: цена одного work-item в цикле ядра (контекст work-item + builtins) против прямого
 * цикла без контекста. Разница = накладные расходы механизма {@link WorkItem} (REWORK_ARCHITECTURE.md, O6).
 *
 * <p>Запуск: {@code ./gradlew :api:jmh}; фильтр: {@code -PjmhArgs="fallback -f 1 -wi 2 -i 3"}.</p>
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ApiBenchmarks {

    private static final int N = 1 << 16;

    private float[] input;
    private float[] output;
    private WorkItemContext ctx;
    private Float4 vector;

    @Setup
    public void setup() {
        input = new float[N];
        output = new float[N];
        for (int i = 0; i < N; i++) {
            input[i] = i * 0.001f;
        }
        ctx = WorkItemContext.of1D(N, 256);
        vector = new Float4(0.1f, 0.2f, 0.3f, 0.4f);
    }

    /** Тело ядра: то же, что в образце {@code computeSine}. */
    private static void kernel(float[] in, float[] out) {
        int id = Gpu.getGlobalId(0);
        out[id] = Gpu.sin(in[id]) + 2.0f;
    }

    /** Один work-item = одна итерация: установка положения + вызов ядра через builtins. */
    @Benchmark
    @OperationsPerInvocation(N)
    public float[] fallbackLoop() {
        WorkItem.run(ctx, () -> {
            for (int i = 0; i < N; i++) {
                ctx.setGlobalId(i, 0, 0);
                kernel(input, output);
            }
        });
        return output;
    }

    /** База для сравнения: та же математика без контекста. */
    @Benchmark
    @OperationsPerInvocation(N)
    public float[] plainLoop() {
        for (int i = 0; i < N; i++) {
            output[i] = Gpu.sin(input[i]) + 2.0f;
        }
        return output;
    }

    /** Цена привязки контекста ({@code WorkItem.run}) вместе с одним чтением id: верхняя оценка разового запуска. */
    @Benchmark
    public void getGlobalIdOnly(Blackhole bh) {
        WorkItem.run(ctx, () -> bh.consume(Gpu.getGlobalId(0)));
    }

    /** Векторная перегрузка: покомпонентный {@code sin(Float4)} (аллокация результата). */
    @Benchmark
    public Float4 vectorSin() {
        return Gpu.sin(vector);
    }
}
