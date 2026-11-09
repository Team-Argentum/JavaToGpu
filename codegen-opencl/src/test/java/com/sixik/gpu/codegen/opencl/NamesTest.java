package com.sixik.gpu.codegen.opencl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Манглинг идентификаторов. */
class NamesTest {

    @Test
    void sanitizeKeepsIdentifiers() {
        assertEquals("computeSine", Names.sanitize("computeSine"));
        assertEquals("a_b1", Names.sanitize("a_b1"));
    }

    @Test
    void sanitizeReplacesIllegalCharacters() {
        assertEquals("a_b_c", Names.sanitize("a-b c"));
        assertEquals("_1abc", Names.sanitize("1abc"));
        assertEquals("_", Names.sanitize(""));
        assertEquals("n__x", Names.sanitize("__x"), "leading double underscore is reserved by C");
        assertEquals("n_________", Names.sanitize("кириллица"));
    }

    @Test
    void reservedWords() {
        for (String w : new String[]{"int", "float", "double", "long", "if", "for", "while", "switch", "return",
                "kernel", "global", "bool", "true", "false", "sin", "max", "isnan", "NAN", "INT_MIN", "float4", "uchar16",
                "dot", "length", "mix", "clamp", "cross", "native_sin", "half_cos", "vload4", "select", "get_global_id", "as_int", "convert_int_sat", "atomic_add", "atom_add", "gpu_div_i32", "cont_1", "CLK_X"}) {
            assertTrue(Names.isReserved(w), w);
        }
        for (String w : new String[]{"x", "id", "i", "sum", "floaty", "float5", "myint", "output", "p0", "v12"}) {
            assertFalse(Names.isReserved(w), w);
        }
    }

    @Test
    void uniqueGivesSuffixesInRequestOrder() {
        Names n = new Names();
        assertEquals("x", n.unique("x", "v0"));
        assertEquals("x_1", n.unique("x", "v1"));
        assertEquals("x_2", n.unique("x", "v2"));
        assertEquals("y", n.unique("y", "v3"));
    }

    @Test
    void uniqueFallsBackToDefault() {
        Names n = new Names();
        assertEquals("v0", n.unique(null, "v0"));
        assertEquals("v1", n.unique("", "v1"));
    }

    @Test
    void reservedNamesGetPrefixAndNeverLoop() {
        Names n = new Names();
        assertEquals("u_int", n.unique("int", "v0"));
        assertEquals("u_sin", n.unique("sin", "v1"));
        // префиксные зарезервированные имена раньше зацикливали выдачу имён (суффикс не меняет префикс)
        assertEquals("u_convert_from_i8", n.unique("convert_from_i8", "v2"));
        assertEquals("u_get_x", n.unique("get_x", "v3"));
        assertEquals("u_get_x_1", n.unique("get_x", "v4"));
        assertFalse(Names.isReserved(n.unique("gpu_thing", "v5")));
    }

    @Test
    void reserveBlocksName() {
        Names n = new Names();
        n.reserve("a");
        assertEquals("a_1", n.unique("a", "v0"));
    }

    @Test
    void copyIsIndependent() {
        Names n = new Names();
        n.unique("a", "v0");
        Names c = n.copy();
        assertEquals("a_1", c.unique("a", "v1"));
        assertEquals("a_1", n.unique("a", "v1"), "the copy did not take the name in the original");
    }
}
