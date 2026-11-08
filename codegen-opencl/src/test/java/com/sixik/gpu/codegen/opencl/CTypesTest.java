package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Types;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Имена типов и литералы OpenCL C. */
class CTypesTest {

    @Test
    void scalarNames() {
        String[] expected = {null, "bool", "char", "uchar", "short", "ushort", "int", "uint", "long", "ulong", "float",
                "double"};
        for (int t = Types.VOID; t <= Types.F64; t++) {
            assertEquals(expected[t], CTypes.scalar(t), "тип " + t);
        }
        assertNull(CTypes.scalar(Types.FIRST_DYNAMIC));
    }

    @Test
    void unsignedCounterpartAndBitcastNames() {
        assertEquals("uint", CTypes.unsignedOf(Types.I32));
        assertEquals("uint", CTypes.unsignedOf(Types.U32));
        assertEquals("ulong", CTypes.unsignedOf(Types.I64));
        assertEquals("ulong", CTypes.unsignedOf(Types.U64));
        assertEquals("as_float", CTypes.asName(Types.F32));
        assertEquals("as_ulong", CTypes.asName(Types.U64));
    }

    @Test
    void addressSpaces() {
        assertEquals("__global", CTypes.space(AddressSpace.GLOBAL));
        assertEquals("__local", CTypes.space(AddressSpace.LOCAL));
        assertEquals("__constant", CTypes.space(AddressSpace.CONSTANT));
        assertEquals("__private", CTypes.space(AddressSpace.PRIVATE));
    }

    @Test
    void signedIntegerLiterals() {
        assertEquals("0", CTypes.integer(Types.I32, 0));
        assertEquals("2147483647", CTypes.integer(Types.I32, Integer.MAX_VALUE));
        assertEquals("INT_MIN", CTypes.integer(Types.I32, Integer.MIN_VALUE));
        assertEquals("(-5)", CTypes.integer(Types.I32, -5));
        assertEquals("(-1)", CTypes.integer(Types.I32, 0xFFFFFFFFL), "младшие 32 бита");
        assertEquals("9223372036854775807L", CTypes.integer(Types.I64, Long.MAX_VALUE));
        assertEquals("LONG_MIN", CTypes.integer(Types.I64, Long.MIN_VALUE));
        assertEquals("(-7L)", CTypes.integer(Types.I64, -7));
    }

    @Test
    void unsignedIntegerLiterals() {
        assertEquals("0u", CTypes.integer(Types.U32, 0));
        assertEquals("4294967295u", CTypes.integer(Types.U32, -1));
        assertEquals("2147483648u", CTypes.integer(Types.U32, Integer.MIN_VALUE));
        assertEquals("18446744073709551615UL", CTypes.integer(Types.U64, -1L));
        assertEquals("9223372036854775808UL", CTypes.integer(Types.U64, Long.MIN_VALUE));
    }

    @Test
    void smallIntegerLiteralsAreCastsOfTheirOwnType() {
        assertEquals("((char)(-128))", CTypes.integer(Types.I8, -128));
        assertEquals("((char)(-1))", CTypes.integer(Types.I8, 255), "255 как значение I8 это -1");
        assertEquals("((uchar)255)", CTypes.integer(Types.U8, 255));
        assertEquals("((uchar)255)", CTypes.integer(Types.U8, -1));
        assertEquals("((short)(-32768))", CTypes.integer(Types.I16, Short.MIN_VALUE));
        assertEquals("((ushort)65535)", CTypes.integer(Types.U16, 65535));
    }

    @Test
    void integerLiteralRejectsNonInteger() {
        assertThrows(IllegalArgumentException.class, () -> CTypes.integer(Types.F32, 1));
        assertThrows(IllegalArgumentException.class, () -> CTypes.integer(Types.BOOL, 1));
    }

    @Test
    void unsignedBitsOfSignedConstants() {
        assertEquals("1u", CTypes.unsignedBits(Types.I32, 1));
        assertEquals("4294967295u", CTypes.unsignedBits(Types.I32, -1));
        assertEquals("18446744073709551615UL", CTypes.unsignedBits(Types.I64, -1));
        assertEquals("5UL", CTypes.unsignedBits(Types.U64, 5));
    }

    @Test
    void floatLiterals() {
        assertEquals("2.0f", CTypes.float32(Float.floatToRawIntBits(2f)));
        assertEquals("0.0f", CTypes.float32(0));
        assertEquals("(-0.0f)", CTypes.float32(Float.floatToRawIntBits(-0f)));
        assertEquals("1.0E-5f", CTypes.float32(Float.floatToRawIntBits(1e-5f)));
        assertEquals("(-2.5f)", CTypes.float32(Float.floatToRawIntBits(-2.5f)));
        assertEquals("INFINITY", CTypes.float32(Float.floatToRawIntBits(Float.POSITIVE_INFINITY)));
        assertEquals("(-INFINITY)", CTypes.float32(Float.floatToRawIntBits(Float.NEGATIVE_INFINITY)));
        assertEquals("NAN", CTypes.float32(0x7fc00000));
        assertEquals("as_float(0x7fc12345u)", CTypes.float32(0x7fc12345), "биты нестандартного NaN сохраняются");
    }

    @Test
    void doubleLiterals() {
        assertEquals("0.5", CTypes.float64(Double.doubleToRawLongBits(0.5)));
        assertEquals("(-0.0)", CTypes.float64(Double.doubleToRawLongBits(-0d)));
        assertEquals("1.0E-300", CTypes.float64(Double.doubleToRawLongBits(1e-300)));
        assertEquals("((double)INFINITY)", CTypes.float64(Double.doubleToRawLongBits(Double.POSITIVE_INFINITY)));
        assertEquals("((double)-INFINITY)", CTypes.float64(Double.doubleToRawLongBits(Double.NEGATIVE_INFINITY)));
        assertEquals("((double)NAN)", CTypes.float64(0x7ff8000000000000L));
        assertEquals("as_double(0x7ff8123456789abcUL)", CTypes.float64(0x7ff8123456789abcL));
    }

    @Test
    void decimalFloatLiteralsRoundTrip() {
        float[] fs = {1.1f, 0.1f, 3.4028235e38f, Float.MIN_VALUE, 16777216f, 1.17549435e-38f, 123456.79f};
        for (float f : fs) {
            String lit = CTypes.float32(Float.floatToRawIntBits(f));
            assertEquals(Float.floatToRawIntBits(f),
                    Float.floatToRawIntBits(Float.parseFloat(lit.substring(0, lit.length() - 1))), lit);
        }
        double[] ds = {1.1, 0.1, Double.MAX_VALUE, Double.MIN_VALUE, 9007199254740993d, 2.2250738585072014e-308};
        for (double d : ds) {
            assertEquals(Double.doubleToRawLongBits(d),
                    Double.doubleToRawLongBits(Double.parseDouble(CTypes.float64(Double.doubleToRawLongBits(d)))));
        }
    }
}
