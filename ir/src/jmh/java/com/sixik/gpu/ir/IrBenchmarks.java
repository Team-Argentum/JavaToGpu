package com.sixik.gpu.ir;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Бенчмарки hot path модуля {@code ir}: построение IR, валидация, запись/чтение бинарного формата, хэш.
 *
 * <p>Нагрузка - «богатый» модуль из тестовых фикстур (217 узлов, 3 функции). Бенчмарки измеряют то, что
 * выполняется на каждом холодном запуске ядра в JIT-режиме: чтение IR с диска, валидация, хэширование
 * для ключа кэша.</p>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class IrBenchmarks {

    private IrModule module;
    private byte[] bytes;

    @Setup
    public void setup() {
        module = Fixtures.rich();
        bytes = IrBinary.write(module);
    }

    /** Построение модуля через builder (фронтенд). */
    @Benchmark
    public IrModule buildRichModule() {
        return Fixtures.rich();
    }

    /** Полная валидация модуля. */
    @Benchmark
    public Validator.Report validate() {
        return Validator.validate(module);
    }

    /** Запись модуля в байты. */
    @Benchmark
    public byte[] write() {
        return IrBinary.write(module);
    }

    /** Чтение модуля из байтов. */
    @Benchmark
    public Result<IrModule> read() {
        return IrBinary.read(bytes);
    }

    /** Хэш для ключа кэша ядра. */
    @Benchmark
    public byte[] hash() {
        return IrBinary.hash(module);
    }

    /** Холодный путь рантайма: прочитать, проверить, получить ключ кэша. */
    @Benchmark
    public String readValidateKernelId() {
        Result<IrModule> r = IrBinary.read(bytes);
        Validator.validate(r.value());
        return IrBinary.kernelId(r.value());
    }

    /** Раскладка struct по правилам ABI. */
    @Benchmark
    public int abiLayout() {
        int total = 0;
        for (int t = Types.FIRST_DYNAMIC; t < module.typeCount(); t++) {
            int size = AbiLayout.sizeOf(module, t);
            total += size;
        }
        return total;
    }
}
