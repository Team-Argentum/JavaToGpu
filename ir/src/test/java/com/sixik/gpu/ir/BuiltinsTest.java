package com.sixik.gpu.ir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Таблица builtins: чтение builtins.def и проверка сигнатур. */
class BuiltinsTest {

    private final IrBuilder b = new IrBuilder();

    private int result(String name, int... argTypes) {
        return Builtins.resultType(b.build(), Builtins.idOf(name), argTypes, argTypes.length);
    }

    @Test
    void tableIsLoadedAndVersioned() {
        assertTrue(Builtins.VERSION >= 1);
        assertTrue(Builtins.count() >= 15);
        assertEquals(0, Builtins.idOf("getGlobalId"), "id стабилен: на него ссылается бинарный IR");
        assertEquals(-1, Builtins.idOf("noSuchBuiltin"));
    }

    @Test
    void everyOccupiedIdHasName() {
        int occupied = 0;
        for (int id = 0; id < Builtins.count(); id++) {
            if (Builtins.isValid(id)) {
                occupied++;
                assertFalse(Builtins.name(id).isBlank());
                assertEquals(id, Builtins.idOf(Builtins.name(id)));
            }
        }
        assertTrue(occupied >= 15);
    }

    @Test
    void flags() {
        int gid = Builtins.idOf("getGlobalId");
        assertTrue(Builtins.has(gid, Builtins.PURE));
        assertTrue(Builtins.has(gid, Builtins.DIV));
        assertFalse(Builtins.has(Builtins.idOf("getGroupId"), Builtins.DIV));
        assertTrue(Builtins.has(Builtins.idOf("getGroupId"), Builtins.UNIFORM));
        int atomic = Builtins.idOf("atomicAdd");
        assertTrue(Builtins.has(atomic, Builtins.EFFECT));
        assertTrue(Builtins.has(atomic, Builtins.READS));
        assertTrue(Builtins.has(atomic, Builtins.ATOMIC));
        assertFalse(Builtins.has(atomic, Builtins.PURE));
    }

    @Test
    void ulpTolerances() {
        assertEquals(4, Builtins.ulp(Builtins.idOf("sin")));
        assertEquals(0, Builtins.ulp(Builtins.idOf("fma")));
        assertEquals(-1, Builtins.ulp(Builtins.idOf("max")), "для max допуска нет - сравнивать точно");
    }

    @Test
    void concreteSignature() {
        assertEquals(Types.I32, result("getGlobalId", Types.I32));
        assertEquals(-1, result("getGlobalId", Types.F32), "аргумент должен быть i32");
        assertEquals(-1, result("getGlobalId"), "число аргументов");
        assertEquals(Types.I32, result("getWorkDim"));
    }

    @Test
    void genericFloatSignature() {
        assertEquals(Types.F32, result("sin", Types.F32));
        assertEquals(Types.F64, result("sin", Types.F64));
        assertEquals(-1, result("sin", Types.I32), "sin только для вещественных");
    }

    @Test
    void genericVectorSignature() {
        int v4 = b.vec(Types.F32, 4);
        assertEquals(v4, result("sin", v4));
        int i4 = b.vec(Types.I32, 4);
        assertEquals(-1, result("sin", i4));
    }

    @Test
    void boundTypeMustBeConsistent() {
        assertEquals(Types.F32, result("fma", Types.F32, Types.F32, Types.F32));
        assertEquals(-1, result("fma", Types.F32, Types.F64, Types.F32));
        assertEquals(Types.I32, result("max", Types.I32, Types.I32));
        assertEquals(-1, result("max", Types.F32, Types.I32));
    }

    @Test
    void pointerSignature() {
        int pg = b.ptr(AddressSpace.GLOBAL, Types.I32);
        int pl = b.ptr(AddressSpace.LOCAL, Types.U32);
        int pp = b.ptr(AddressSpace.PRIVATE, Types.I32);
        int pc = b.ptr(AddressSpace.CONSTANT, Types.I32);
        int pf = b.ptr(AddressSpace.GLOBAL, Types.F32);
        assertEquals(Types.I32, result("atomicAdd", pg, Types.I32));
        assertEquals(Types.U32, result("atomicAdd", pl, Types.U32));
        assertEquals(-1, result("atomicAdd", pp, Types.I32), "atomic только global/local");
        assertEquals(-1, result("atomicAdd", pc, Types.I32));
        assertEquals(-1, result("atomicAdd", pf, Types.F32), "atomic только для целых");
        assertEquals(-1, result("atomicAdd", pg, Types.U32), "значение должно совпасть с pointee");
        assertEquals(-1, result("atomicAdd", Types.I32, Types.I32), "первый аргумент должен быть указателем");
    }

    @Test
    void invalidIdIsRejected() {
        IrModule m = b.build();
        assertEquals(-1, Builtins.resultType(m, 9999, new int[0], 0));
        assertEquals(-1, Builtins.resultType(m, -1, new int[0], 0));
    }
}
