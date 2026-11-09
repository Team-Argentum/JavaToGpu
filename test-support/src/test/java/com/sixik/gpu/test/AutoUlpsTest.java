package com.sixik.gpu.test;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Проверяет выбор допуска по типу операции и достижимости, включая накопление ошибки после builtin. */
class AutoUlpsTest {

    /**
     * Добавляет ядро, записывающее один результат builtin в массив.
     *
     * @param b сборщик модуля
     * @param type тип элемента массива: скаляр или вектор
     * @param builtin имя unary-builtin
     * @return индекс добавленного ядра
     */
    private static int kernel(IrBuilder b, int type, String builtin) {
        int array = b.array(AddressSpace.GLOBAL, type);
        int f = b.beginFunction(IrModule.KERNEL, Types.VOID, array, array);
        int value = b.load(b.elemPtr(b.param(1), b.constI32(0)));
        int result = b.intrinsic(Builtins.idOf(builtin), value);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), result)));
        return f;
    }

    /**
     * Проверяет, что тестовая фикстура удовлетворяет контракту IR.
     *
     * @param b заполненный сборщик
     * @return валидный модуль
     */
    private static IrModule valid(IrBuilder b) {
        IrModule m = b.build();
        var v = Validator.validate(m);
        assertFalse(v.hasErrors(), v.diagnostics().toString());
        return m;
    }

    /**
     * Проверяет различие допусков sqrt для f32/f64, в том числе вектора.
     *
     * @param wide true для f64, false для f32
     * @param lanes число компонент: 1 для скаляра, 2 для вектора
     * @param expected допуск соответствующей перегрузки sqrt, ULP
     */
    @ParameterizedTest
    @CsvSource({"false, 1, 3", "true, 1, 0", "false, 2, 3", "true, 2, 0"})
    void sqrt_usesActualComponentType(boolean wide, int lanes, int expected) {
        IrBuilder b = new IrBuilder();
        int scalar = wide ? Types.F64 : Types.F32;
        kernel(b, lanes == 1 ? scalar : b.vec(scalar, lanes), "sqrt");
        assertEquals(expected, GpuAssert.autoUlps(valid(b)));
    }

    /** Неиспользуемый вариант с sin не ослабляет точное сравнение double-sqrt. */
    @Test
    void unusedVariant_doesNotIncreaseTolerance() {
        IrBuilder b = new IrBuilder();
        kernel(b, Types.F64, "sin");
        kernel(b, Types.F64, "sqrt");
        assertEquals(0, GpuAssert.autoUlps(valid(b)));
    }

    /** Допуск выбранного варианта не берётся у основного ядра. */
    @Test
    void selectedVariant_usesItsOwnTolerance() {
        IrBuilder b = new IrBuilder();
        int fallback = kernel(b, Types.F64, "sqrt");
        kernel(b, Types.F64, "sin");
        IrModule m = valid(b);
        assertEquals(Builtins.ulp64(Builtins.idOf("sin")), GpuAssert.autoUlps(m));
        assertEquals(0, GpuAssert.autoUlps(m, fallback));
    }

    /** Допуск helper-функции сохраняется при транзитивном вызове. */
    @Test
    void transitiveHelper_contributesItsTolerance() {
        IrBuilder b = new IrBuilder();
        int leaf = b.beginFunction(IrModule.DEVICE, Types.F32, Types.F32);
        b.endFunction(b.block(b.returnStmt(b.intrinsic(Builtins.idOf("sqrt"), b.param(0)))));
        int helper = b.beginFunction(IrModule.DEVICE, Types.F32, Types.F32);
        b.endFunction(b.block(b.returnStmt(b.call(leaf, b.param(0)))));
        int array = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, array, array);
        int result = b.call(helper, b.load(b.elemPtr(b.param(1), b.constI32(0))));
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), result)));
        assertEquals(3, GpuAssert.autoUlps(valid(b)));
    }

    /**
     * Демонстрирует, почему максимум допусков операций не является границей результата.
     * Для y = x - c при x = c = 1 ошибка x в 1 ULP даёт ненулевой y вместо точного нуля;
     * шаг представления около нуля намного меньше шага около единицы.
     */
    @Test
    void cancellation_amplifiesOneUlpOfAnIntermediateValue() {
        float exact = 1f;
        float approximate = Math.nextUp(exact);
        assertTrue(Compare.sameFloat(exact, approximate, 1));
        assertFalse(Compare.sameFloat(exact - 1f, approximate - 1f, 4),
                "a per-operation budget cannot bound cancellation in output ULPs");
    }
}
