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
        assertEquals(0, Builtins.idOf("getGlobalId"), "the id is stable: binary IR refers to it");
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
        assertTrue(Builtins.has(Builtins.idOf("barrier"), Builtins.SYNC));
        assertTrue(Builtins.has(Builtins.idOf("barrierGlobal"), Builtins.SYNC));
        assertFalse(Builtins.has(atomic, Builtins.SYNC));
        assertFalse(Builtins.has(atomic, Builtins.EXT), "add and sub are in the base set of 64-bit atomics");
        for (String ext : new String[]{"atomicMin", "atomicMax", "atomicAnd", "atomicOr", "atomicXor"}) {
            assertTrue(Builtins.has(Builtins.idOf(ext), Builtins.EXT), ext);
            assertTrue(Builtins.has(Builtins.idOf(ext), Builtins.ATOMIC), ext);
        }
        assertFalse(Builtins.has(Builtins.idOf("atomicXchg"), Builtins.EXT));
    }

    @Test
    void ulpTolerances() {
        assertEquals(4, Builtins.ulp(Builtins.idOf("sin")));
        assertEquals(0, Builtins.ulp(Builtins.idOf("fma")));
        assertEquals(-1, Builtins.ulp(Builtins.idOf("max")), "max has no tolerance - compare exactly");
    }

    @Test
    void ulp64DefaultsToUlpAndCanDiffer() {
        int sin = Builtins.idOf("sin");
        assertEquals(Builtins.ulp(sin), Builtins.ulp64(sin), "without ulp64= the double tolerance equals the float tolerance");
        int sqrt = Builtins.idOf("sqrt");
        assertEquals(3, Builtins.ulp(sqrt), "sqrt float: 3 ULP");
        assertEquals(0, Builtins.ulp64(sqrt), "sqrt double: correct rounding");
        assertEquals(-1, Builtins.ulp64(Builtins.idOf("max")));
    }

    @Test
    void generatorDirectivesDoNotCreateBuiltins() {
        // doc/jvm/vectors/pointers относятся к генератору api: ir видит только строки с id
        int occupied = 0;
        for (int id = 0; id < Builtins.count(); id++) {
            if (Builtins.isValid(id)) {
                occupied++;
            }
        }
        assertEquals(24, occupied);
        assertEquals(24, Builtins.count());
    }

    @Test
    void concreteSignature() {
        assertEquals(Types.I32, result("getGlobalId", Types.I32));
        assertEquals(-1, result("getGlobalId", Types.F32), "the argument must be i32");
        assertEquals(-1, result("getGlobalId"), "argument count");
        assertEquals(Types.I32, result("getWorkDim"));
    }

    @Test
    void genericFloatSignature() {
        assertEquals(Types.F32, result("sin", Types.F32));
        assertEquals(Types.F64, result("sin", Types.F64));
        assertEquals(-1, result("sin", Types.I32), "sin only for floating types");
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
        assertEquals(-1, result("atomicAdd", pp, Types.I32), "atomic only global/local");
        assertEquals(-1, result("atomicAdd", pc, Types.I32));
        assertEquals(-1, result("atomicAdd", pf, Types.F32), "atomic only for integers");
        assertEquals(-1, result("atomicAdd", pg, Types.U32), "the value must match pointee");
        assertEquals(-1, result("atomicAdd", Types.I32, Types.I32), "the first argument must be a pointer");
    }

    @Test
    void invalidIdIsRejected() {
        IrModule m = b.build();
        assertEquals(-1, Builtins.resultType(m, 9999, new int[0], 0));
        assertEquals(-1, Builtins.resultType(m, -1, new int[0], 0));
    }
}
