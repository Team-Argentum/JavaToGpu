package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.Types;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Реестр вспомогательных функций преамбулы. */
class HelpersTest {

    @Test
    void emptyRegistry() {
        Helpers h = new Helpers();
        assertFalse(h.any());
        StringBuilder sb = new StringBuilder();
        h.appendTo(sb);
        assertEquals("", sb.toString());
    }

    @Test
    void useReturnsFunctionNameAndMarksIt() {
        Helpers h = new Helpers();
        assertEquals("gpu_div_i32", h.use(Helpers.DIV_I32));
        assertEquals("gpu_rem_i64", h.use(Helpers.REM_I64));
        assertEquals("gpu_f32_to_u64", h.use(Helpers.F32_TO_U64));
        assertEquals("gpu_f64_to_i32", h.use(Helpers.F64_TO_I32));
        assertEquals("gpu_max_f32", h.use(Helpers.MAX_F32));
        assertEquals("gpu_min_f64", h.use(Helpers.MIN_F64));
        assertTrue(h.any());
    }

    @Test
    void definitionsAreInFixedOrderRegardlessOfUseOrder() {
        Helpers a = new Helpers();
        a.use(Helpers.MIN_F64);
        a.use(Helpers.DIV_I32);
        Helpers b = new Helpers();
        b.use(Helpers.DIV_I32);
        b.use(Helpers.MIN_F64);
        StringBuilder sa = new StringBuilder();
        StringBuilder sb = new StringBuilder();
        a.appendTo(sa);
        b.appendTo(sb);
        assertEquals(sa.toString(), sb.toString());
        assertTrue(sa.indexOf("gpu_div_i32") < sa.indexOf("gpu_min_f64"));
    }

    @Test
    void onlyUsedFunctionsArePrinted() {
        Helpers h = new Helpers();
        h.use(Helpers.REM_I32);
        StringBuilder sb = new StringBuilder();
        h.appendTo(sb);
        assertTrue(sb.toString().contains("gpu_rem_i32"));
        assertFalse(sb.toString().contains("gpu_div_i32"));
    }

    @Test
    void floatToIntMapping() {
        assertEquals(Helpers.F32_TO_I32, Helpers.floatToInt(Types.F32, Types.I32));
        assertEquals(Helpers.F32_TO_U32, Helpers.floatToInt(Types.F32, Types.U32));
        assertEquals(Helpers.F32_TO_I64, Helpers.floatToInt(Types.F32, Types.I64));
        assertEquals(Helpers.F32_TO_U64, Helpers.floatToInt(Types.F32, Types.U64));
        assertEquals(Helpers.F64_TO_I32, Helpers.floatToInt(Types.F64, Types.I32));
        assertEquals(Helpers.F64_TO_U32, Helpers.floatToInt(Types.F64, Types.U32));
        assertEquals(Helpers.F64_TO_I64, Helpers.floatToInt(Types.F64, Types.I64));
        assertEquals(Helpers.F64_TO_U64, Helpers.floatToInt(Types.F64, Types.U64));
    }

    @Test
    void everyDefinitionNamesItself() {
        for (int i = 0; i <= Helpers.MIN_F64; i++) {
            Helpers h = new Helpers();
            String name = h.use(i);
            StringBuilder sb = new StringBuilder();
            h.appendTo(sb);
            assertTrue(sb.toString().contains(" " + name + "("), name);
            assertTrue(name.startsWith("gpu_"), name);
        }
    }
}
