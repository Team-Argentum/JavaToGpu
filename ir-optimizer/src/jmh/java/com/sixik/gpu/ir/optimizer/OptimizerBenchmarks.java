package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Время оптимизации: оптимизатор запускается при каждой компиляции ядра (до кэша драйвера), поэтому его стоимость входит
 * в «холодный» первый вызов ядра. Запуск: {@code ./gradlew :ir-optimizer:jmh}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class OptimizerBenchmarks {

    /** Образец: «богатая» ручная фикстура либо ядро из Java-образцов. */
    @Param({"rich", "MemoryKernels.manyCalls", "ControlKernels.loops", "StructKernels.step"})
    public String sample;

    private IrModule module;

    /** Выбирает модуль образца. */
    @Setup
    public void setup() {
        module = sample.equals("rich") ? Fixtures.rich()
                : SampleKernels.all().stream().filter(k -> k.label().equals(sample)).findFirst().orElseThrow().module();
    }

    /** @return модуль после полного конвейера (уровень 2, с проверкой каждого прохода) */
    @Benchmark
    public IrModule optimizeDefault() {
        return Optimizer.optimize(module, OptimizeOptions.DEFAULT).module();
    }

    /** @return модуль после конвейера без промежуточной валидации */
    @Benchmark
    public IrModule optimizeWithoutValidation() {
        return Optimizer.optimize(module, OptimizeOptions.DEFAULT.withValidation(false)).module();
    }

    /** @return оценка давления на регистры */
    @Benchmark
    public RegisterPressure.Report pressure() {
        return RegisterPressure.analyze(module);
    }
}
