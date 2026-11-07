package com.sixik.gpu.ir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Валидные модули не должны давать ни ошибок, ни предупреждений; проверка вычисляемых метаданных. */
class ValidatorPositiveTest {

    @Test
    void computeSineMatchesArchitectureExample() {
        IrModule m = Fixtures.computeSine();
        assertEquals(15, m.nodeCount(), "в 5.11 пример состоит из 15 узлов");
        assertEquals(15 * IrModule.N_COLS * 4, 300, "таблица узлов - 300 байт");
        assertEquals(1, m.functionCount());
        assertEquals(Op.BLOCK, m.op(m.fBody(0)));
        assertEquals(14, m.fBody(0));
        assertEquals(Op.ELEM_PTR, m.op(5));
        assertEquals(Op.LOAD, m.op(6));
        assertEquals(Op.STORE, m.op(13));
        // t16 = Array<global,f32>, t17 = Ptr<global,f32>, как в документе
        assertEquals(Types.KIND_ARRAY, m.typeKind(16));
        assertEquals(Types.KIND_PTR, m.typeKind(17));
    }

    @Test
    void computeSineIsValid() {
        Validator.Report r = Validator.validate(Fixtures.computeSine());
        assertTrue(r.diagnostics().isEmpty(), r.diagnostics().toString());
    }

    @Test
    void richModuleIsValid() {
        Validator.Report r = Validator.validate(Fixtures.rich());
        assertTrue(r.diagnostics().isEmpty(), r.diagnostics().toString());
    }

    @Test
    void computeSineParamAccessModes() {
        Validator.FunctionInfo info = Validator.validate(Fixtures.computeSine()).functions()[0];
        assertArrayEquals(new int[]{Validator.ACCESS_READ, Validator.ACCESS_WRITE}, info.paramAccess());
        assertEquals(0, info.requiredFeatures());
        assertFalse(info.usesGroupSync());
        assertTrue(info.effects(), "store - это эффект");
        assertTrue(info.readsMemory());
    }

    @Test
    void richModuleMetadata() {
        Validator.Report r = Validator.validate(Fixtures.rich());
        Validator.FunctionInfo pure = r.functions()[0];
        Validator.FunctionInfo inc = r.functions()[1];
        Validator.FunctionInfo kernel = r.functions()[2];

        assertFalse(pure.effects());
        assertFalse(pure.readsMemory());
        assertTrue(inc.effects(), "inc пишет по указателю");
        assertTrue(inc.readsMemory());

        assertTrue(kernel.usesGroupSync(), "в kernel есть barrier и local-массив");
        assertEquals(0, kernel.requiredFeatures(), "double не используется");
        // параметры: in(READ), idx(READ|WRITE - atomicAdd), items(WRITE), out(WRITE)
        assertArrayEquals(new int[]{
                Validator.ACCESS_READ,
                Validator.ACCESS_READ | Validator.ACCESS_WRITE,
                Validator.ACCESS_WRITE,
                Validator.ACCESS_WRITE}, kernel.paramAccess());
    }

    @Test
    void richModuleCoversMostOpcodes() {
        IrModule m = Fixtures.rich();
        boolean[] seen = new boolean[Op.LIMIT];
        for (int n = 0; n < m.nodeCount(); n++) {
            seen[m.op(n)] = true;
        }
        StringBuilder missing = new StringBuilder();
        for (int op = 1; op < Op.LIMIT; op++) {
            // spec_const зарезервирован под AutoTune и в v1 не используется в «обычных» модулях
            if (Op.isValid(op) && !seen[op] && op != Op.SPEC_CONST) {
                missing.append(Op.nameOf(op)).append(' ');
            }
        }
        assertEquals("", missing.toString().trim(), "фикстура rich() должна покрывать каталог узлов");
    }
}
