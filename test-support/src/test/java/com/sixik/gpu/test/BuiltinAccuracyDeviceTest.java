package com.sixik.gpu.test;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.runtime.GpuContext;
import com.sixik.gpu.runtime.LaunchConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Прямая сверка отдельных builtins с JVM: погрешность составного выражения здесь не скрывает ошибку функции. */
@Tag("device")
class BuiltinAccuracyDeviceTest {

    /** Контекст реального устройства; fallback запрещён. */
    private static GpuContext context;

    /** Открывает устройство, пропуская тесты только при его отсутствии. */
    @BeforeAll
    static void open() {
        var result = GpuContext.builder().fallback(GpuContext.Fallback.NEVER).tryBuild();
        assumeTrue(result.isOk(), "no OpenCL device: " + result.diagnostics());
        context = result.value();
        System.out.println("Builtin accuracy: " + context.device().describe() + ", driver " + context.device().driverVersion());
    }

    /** Освобождает контекст и скомпилированные ядра. */
    @AfterAll
    static void close() {
        if (context != null) {
            context.close();
        }
    }

    /**
     * Проверяет один builtin на конечных аргументах, краях области и специальных значениях.
     *
     * @param name имя builtin из декларативной таблицы
     * @param wide true для double, false для float
     */
    @ParameterizedTest(name = "{0}, double={1}")
    @CsvSource({"sin, false", "sin, true", "cos, false", "cos, true",
            "sqrt, false", "sqrt, true", "fma, false", "fma, true"})
    void builtin_matchesDeclaredTolerance(String name, boolean wide) {
        if (wide) {
            assumeTrue((context.device().features() & com.sixik.gpu.ir.Features.FP64) != 0, "device has no FP64");
        }
        int id = Builtins.idOf(name);
        int ulps = wide ? Builtins.ulp64(id) : Builtins.ulp(id);
        int n = 2048;
        double[] edge = {0d, -0d, 1d, -1d, 0.5, -0.5, Math.PI, -Math.PI,
                Math.nextDown(Math.PI), Math.nextUp(Math.PI), 100d, -100d,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN};
        Random random = new Random(4242);
        double[] input = new double[n];
        for (int i = 0; i < n; i++) {
            input[i] = i < edge.length ? edge[i] : (random.nextDouble() - 0.5) * 200;
        }
        IrModule module = module(id, wide ? Types.F64 : Types.F32, name.equals("fma"));
        if (wide) {
            double[] output = new double[n];
            context.run(module, LaunchConfig.of1D(n), output, input);
            for (int i = 0; i < n; i++) {
                double expected = reference(name, input[i]);
                assertTrue(Compare.sameDouble(expected, output[i], ulps),
                        name + "(double " + input[i] + "): expected " + expected + ", got " + output[i] + ", budget " + ulps + " ULP");
            }
        } else {
            float[] values = new float[n];
            for (int i = 0; i < n; i++) {
                values[i] = (float) input[i];
            }
            float[] output = new float[n];
            context.run(module, LaunchConfig.of1D(n), output, values);
            for (int i = 0; i < n; i++) {
                float expected = reference(name, values[i]);
                assertTrue(Compare.sameFloat(expected, output[i], ulps),
                        name + "(float " + values[i] + "): expected " + expected + ", got " + output[i] + ", budget " + ulps + " ULP");
            }
        }
    }

    /**
     * Строит ядро out[id] = builtin(in[id]), для fma — fma(in[id], 0.5, -1).
     *
     * @param builtin id функции
     * @param scalar F32 или F64
     * @param fma нужен ли набор из трёх аргументов
     * @return модуль без дополнительных математических операций
     */
    private static IrModule module(int builtin, int scalar, boolean fma) {
        IrBuilder b = new IrBuilder();
        int array = b.array(AddressSpace.GLOBAL, scalar);
        b.beginFunction(IrModule.KERNEL, Types.VOID, array, array);
        int index = b.intrinsic(Builtins.idOf("getGlobalId"), b.constI32(0));
        int value = b.load(b.elemPtr(b.param(1), index));
        int result = fma ? b.intrinsic(builtin, value,
                scalar == Types.F64 ? b.constF64(0.5) : b.constF32(0.5f),
                scalar == Types.F64 ? b.constF64(-1) : b.constF32(-1)) : b.intrinsic(builtin, value);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), index), result)));
        return b.build();
    }

    /**
     * Вычисляет независимый от кодгена JVM-эталон double.
     *
     * @param name builtin
     * @param x аргумент (радианы для sin/cos)
     * @return значение по JVM-реализации builtin
     */
    private static double reference(String name, double x) {
        return switch (name) {
            case "sin" -> Gpu.sin(x);
            case "cos" -> Gpu.cos(x);
            case "sqrt" -> Gpu.sqrt(x);
            case "fma" -> Gpu.fma(x, 0.5, -1);
            default -> throw new IllegalArgumentException(name);
        };
    }

    /**
     * Вычисляет независимый от кодгена JVM-эталон float.
     *
     * @param name builtin
     * @param x аргумент (радианы для sin/cos)
     * @return значение по JVM-реализации builtin
     */
    private static float reference(String name, float x) {
        return switch (name) {
            case "sin" -> Gpu.sin(x);
            case "cos" -> Gpu.cos(x);
            case "sqrt" -> Gpu.sqrt(x);
            case "fma" -> Gpu.fma(x, 0.5f, -1f);
            default -> throw new IllegalArgumentException(name);
        };
    }
}
