package net.sixik.ga_utils.javatogpu.frontend.intrinsics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIntrinsicDatabaseTest {

    @Test
    void resolvesMathAndBuiltinIntrinsics() {
        GpuIntrinsicDatabase database = GpuIntrinsicDatabase.createDefault();

        GpuIntrinsic sin = database.require("GPU", "sin", List.of("float"));
        GpuIntrinsic doubleSin = database.require("GPU", "sin", List.of("double"));
        GpuIntrinsic cos = database.require("GPU", "cos", List.of("float"));
        GpuIntrinsic pow = database.require("GPU", "pow", List.of("double", "double"));
        GpuIntrinsic clamp = database.require("GPU", "clamp", List.of("float", "float", "float"));
        GpuIntrinsic barrier = database.require("GPU", "barrier", List.of("int"));
        GpuIntrinsic length = database.require("GPU", "length", List.of("double", "double"));
        GpuIntrinsic fract = database.require("GPU", "fract", List.of("float"));
        GpuIntrinsic abs = database.require("GPU", "abs", List.of("double"));
        GpuIntrinsic globalId = database.require("GPU", "get_global_id", List.of("int"));
        GpuIntrinsic localId = database.require("GPU", "get_local_id", List.of("int"));
        GpuIntrinsic nan = database.require("GPU", "nan", List.of("int"));
        GpuIntrinsic convertShortSat = database.require("GPU", "convert_short_sat", List.of("float"));
        GpuIntrinsic convertUshortSat = database.require("GPU", "convert_ushort_sat", List.of("float"));
        GpuIntrinsic convertIntSat = database.require("GPU", "convert_int_sat", List.of("double"));
        GpuIntrinsic convertUintSat = database.require("GPU", "convert_uint_sat", List.of("double"));
        GpuIntrinsic convertLongSat = database.require("GPU", "convert_long_sat", List.of("double"));
        GpuIntrinsic convertUlongSat = database.require("GPU", "convert_ulong_sat", List.of("double"));
        GpuIntrinsic convertChar = database.require("GPU", "convert_char", List.of("double"));
        GpuIntrinsic convertUchar = database.require("GPU", "convert_uchar", List.of("double"));
        GpuIntrinsic convertShort = database.require("GPU", "convert_short", List.of("double"));
        GpuIntrinsic convertUshort = database.require("GPU", "convert_ushort", List.of("double"));
        GpuIntrinsic convertIntFromUInt = database.require("GPU", "convert_int", List.of("UInt"));
        GpuIntrinsic convertLongFromULong = database.require("GPU", "convert_long", List.of("ULong"));
        GpuIntrinsic convertFloatFromUShort = database.require("GPU", "convert_float", List.of("UShort"));
        GpuIntrinsic convertDoubleFromUByte = database.require("GPU", "convert_double", List.of("UByte"));
        GpuIntrinsic hadd = database.require("GPU", "hadd", List.of("int", "int"));
        GpuIntrinsic rhadd = database.require("GPU", "rhadd", List.of("long", "long"));
        GpuIntrinsic mulHi = database.require("GPU", "mul_hi", List.of("int", "int"));
        GpuIntrinsic madHi = database.require("GPU", "mad_hi", List.of("long", "long", "long"));
        GpuIntrinsic addSat = database.require("GPU", "add_sat", List.of("int", "int"));
        GpuIntrinsic subSat = database.require("GPU", "sub_sat", List.of("long", "long"));
        GpuIntrinsic madSat = database.require("GPU", "mad_sat", List.of("int", "int", "int"));
        GpuIntrinsic addSatUInt = database.require("GPU", "add_sat", List.of("UInt", "UInt"));
        GpuIntrinsic subSatULong = database.require("GPU", "sub_sat", List.of("ULong", "ULong"));
        GpuIntrinsic madSatUShort = database.require("GPU", "mad_sat", List.of("UShort", "UShort", "UShort"));
        GpuIntrinsic mulSat = database.require("GPU", "mul_sat", List.of("int", "int"));
        GpuIntrinsic mulSatUInt = database.require("GPU", "mul_sat", List.of("UInt", "UInt"));
        GpuIntrinsic absDiffUInt = database.require("GPU", "abs_diff", List.of("UInt", "UInt"));
        GpuIntrinsic absDiffULong = database.require("GPU", "abs_diff", List.of("ULong", "ULong"));
        GpuIntrinsic globalFloatPtr = database.require("GPU", "global", List.of("float[]"));
        GpuIntrinsic globalIntPtr = database.require("GPU", "global", List.of("int[]"));
        GpuIntrinsic constantBytePtr = database.require("GPU", "constant", List.of("byte[]"));
        GpuIntrinsic constantFloatPtr = database.require("GPU", "constant", List.of("float[]"));
        GpuIntrinsic localBytePtr = database.require("GPU", "local", List.of("byte[]"));
        GpuIntrinsic localDoublePtr = database.require("GPU", "local", List.of("double[]"));

        assertEquals(GpuIntrinsicKind.MATH, sin.kind());
        assertEquals("sin", sin.backendName());
        assertEquals("float", sin.resultType());
        assertEquals(List.of("float"), sin.argumentTypes());
        assertEquals("double", doubleSin.resultType());
        assertEquals(GpuIntrinsicKind.MATH, cos.kind());
        assertEquals("cos", cos.backendName());
        assertEquals(GpuIntrinsicKind.MATH, pow.kind());
        assertEquals("pow", pow.backendName());
        assertEquals("double", pow.resultType());
        assertEquals(List.of("double", "double"), pow.argumentTypes());
        assertEquals(GpuIntrinsicKind.COMMON, clamp.kind());
        assertEquals("clamp", clamp.backendName());
        assertEquals("float", clamp.resultType());
        assertEquals(GpuIntrinsicKind.SYNCHRONIZATION, barrier.kind());
        assertEquals("barrier", barrier.backendName());
        assertEquals("void", barrier.resultType());
        assertEquals(GpuIntrinsicKind.COMMON, length.kind());
        assertEquals("hypot", length.backendName());
        assertEquals(GpuIntrinsicKind.MATH, fract.kind());
        assertEquals("fract", fract.backendName());
        assertEquals("(({0}) - floor({0}))", fract.codeTemplate());
        assertEquals("float", fract.resultType());
        assertEquals(GpuIntrinsicKind.MATH, abs.kind());
        assertEquals("fabs", abs.backendName());
        assertEquals("double", abs.resultType());
        assertEquals(GpuIntrinsicKind.BUILTIN_ID, globalId.kind());
        assertEquals("get_global_id", globalId.backendName());
        assertEquals("int", globalId.resultType());
        assertEquals(List.of("int"), globalId.argumentTypes());
        assertEquals(GpuIntrinsicKind.BUILTIN_ID, localId.kind());
        assertEquals(GpuIntrinsicKind.MATH, nan.kind());
        assertEquals("nan(((uint) ({0})))", nan.codeTemplate());
        assertEquals("float", nan.resultType());
        assertEquals(GpuIntrinsicKind.MATH, convertShortSat.kind());
        assertEquals("convert_short_sat", convertShortSat.backendName());
        assertEquals("short", convertShortSat.resultType());
        assertEquals(GpuIntrinsicKind.MATH, convertUshortSat.kind());
        assertEquals("convert_ushort_sat({0})", convertUshortSat.codeTemplate());
        assertEquals("UShort", convertUshortSat.resultType());
        assertEquals("convert_int_sat", convertIntSat.backendName());
        assertEquals("int", convertIntSat.resultType());
        assertEquals("convert_uint_sat({0})", convertUintSat.codeTemplate());
        assertEquals("UInt", convertUintSat.resultType());
        assertEquals("convert_long_sat", convertLongSat.backendName());
        assertEquals("long", convertLongSat.resultType());
        assertEquals("convert_ulong_sat({0})", convertUlongSat.codeTemplate());
        assertEquals("ULong", convertUlongSat.resultType());
        assertEquals("convert_char", convertChar.backendName());
        assertEquals("byte", convertChar.resultType());
        assertEquals("convert_uchar({0})", convertUchar.codeTemplate());
        assertEquals("UByte", convertUchar.resultType());
        assertEquals("convert_short", convertShort.backendName());
        assertEquals("short", convertShort.resultType());
        assertEquals("convert_ushort({0})", convertUshort.codeTemplate());
        assertEquals("UShort", convertUshort.resultType());
        assertEquals("convert_int", convertIntFromUInt.backendName());
        assertEquals("int", convertIntFromUInt.resultType());
        assertEquals("convert_long", convertLongFromULong.backendName());
        assertEquals("long", convertLongFromULong.resultType());
        assertEquals("convert_float", convertFloatFromUShort.backendName());
        assertEquals("float", convertFloatFromUShort.resultType());
        assertEquals("convert_double", convertDoubleFromUByte.backendName());
        assertEquals("double", convertDoubleFromUByte.resultType());
        assertEquals("hadd", hadd.backendName());
        assertEquals("int", hadd.resultType());
        assertEquals("rhadd", rhadd.backendName());
        assertEquals("long", rhadd.resultType());
        assertEquals("mul_hi", mulHi.backendName());
        assertEquals("int", mulHi.resultType());
        assertEquals("mad_hi", madHi.backendName());
        assertEquals("long", madHi.resultType());
        assertEquals("add_sat", addSat.backendName());
        assertEquals("int", addSat.resultType());
        assertEquals("sub_sat", subSat.backendName());
        assertEquals("long", subSat.resultType());
        assertEquals("mad_sat", madSat.backendName());
        assertEquals("int", madSat.resultType());
        assertEquals("add_sat({0}, {1})", addSatUInt.codeTemplate());
        assertEquals("UInt", addSatUInt.resultType());
        assertEquals("sub_sat({0}, {1})", subSatULong.codeTemplate());
        assertEquals("ULong", subSatULong.resultType());
        assertEquals("mad_sat({0}, {1}, {2})", madSatUShort.codeTemplate());
        assertEquals("UShort", madSatUShort.resultType());
        assertEquals("mul_sat", mulSat.backendName());
        assertEquals("int", mulSat.resultType());
        assertEquals("mul_sat({0}, {1})", mulSatUInt.codeTemplate());
        assertEquals("UInt", mulSatUInt.resultType());
        assertEquals("abs_diff({0}, {1})", absDiffUInt.codeTemplate());
        assertEquals("UInt", absDiffUInt.resultType());
        assertEquals("abs_diff({0}, {1})", absDiffULong.codeTemplate());
        assertEquals("ULong", absDiffULong.resultType());
        assertEquals("({0})", globalFloatPtr.codeTemplate());
        assertEquals("GlobalFloatPtr", globalFloatPtr.resultType());
        assertEquals("GlobalIntPtr", globalIntPtr.resultType());
        assertEquals("({0})", constantBytePtr.codeTemplate());
        assertEquals("ConstantBytePtr", constantBytePtr.resultType());
        assertEquals("ConstantFloatPtr", constantFloatPtr.resultType());
        assertEquals("({0})", localBytePtr.codeTemplate());
        assertEquals("LocalBytePtr", localBytePtr.resultType());
        assertEquals("LocalDoublePtr", localDoublePtr.resultType());
        assertTrue(database.isAllowedOwner("GPU"));
        assertTrue(database.isAllowedAllocationType("IntPtr"));
        assertTrue(database.builtinConstants().stream().anyMatch(constant ->
                constant.ownerSimpleName().equals("GPU")
                        && constant.name().equals("CLK_LOCAL_MEM_FENCE")
                        && constant.javaType().equals("int")
                        && constant.sourceText().equals("1")
        ));
        assertTrue(database.builtinConstants().stream().anyMatch(constant ->
                constant.ownerSimpleName().equals("GPU")
                        && constant.name().equals("CL_RGBA")
                        && constant.javaType().equals("int")
        ));
    }

    @Test
    void resolvesGeneratedConversionFamiliesForBroadVectorWidths() {
        GpuIntrinsicDatabase database = GpuIntrinsicDatabase.createDefault();

        GpuIntrinsic wideIntToUInt = database.require("GPU", "convert_uint", List.of("Int16"));
        GpuIntrinsic wideUIntToInt = database.require("GPU", "convert_int", List.of("UInt16"));
        GpuIntrinsic wideUByteToUShort = database.require("GPU", "convert_ushort_sat", List.of("UByte16"));
        GpuIntrinsic wideULongToUInt = database.require("GPU", "convert_uint", List.of("ULong8"));
        GpuIntrinsic floatVectorToUShort = database.require("GPU", "convert_ushort_sat", List.of("Float4"));
        GpuIntrinsic doubleVectorToChar = database.require("GPU", "convert_char", List.of("Double3"));

        assertEquals("UInt16", wideIntToUInt.resultType());
        assertEquals("convert_uint({0})", wideIntToUInt.codeTemplate());
        assertEquals("Int16", wideUIntToInt.resultType());
        assertEquals("UShort16", wideUByteToUShort.resultType());
        assertEquals("UInt8", wideULongToUInt.resultType());
        assertEquals("UShort4", floatVectorToUShort.resultType());
        assertEquals("Byte3", doubleVectorToChar.resultType());
    }

    @Test
    void generatedFamiliesFillRegistryGapsWithoutReplacingFacadeSpecialCases() {
        GpuIntrinsicDatabase database = GpuIntrinsicDatabase.createDefault();

        GpuIntrinsic handWrittenUnsignedConvert = database.require("GPU", "convert_int", List.of("ULong16"));
        GpuIntrinsic generatedUnsignedConvert = database.require("GPU", "convert_uint", List.of("ULong16"));

        assertEquals("Int16", handWrittenUnsignedConvert.resultType());
        assertEquals("convert_int", handWrittenUnsignedConvert.backendName());
        assertEquals("", handWrittenUnsignedConvert.codeTemplate());
        assertEquals("UInt16", generatedUnsignedConvert.resultType());
        assertEquals("convert_uint({0})", generatedUnsignedConvert.codeTemplate());
    }

    @Test
    void resolvesGeneratedIntegerCommonFamiliesForBroadVectorWidths() {
        GpuIntrinsicDatabase database = GpuIntrinsicDatabase.createDefault();

        GpuIntrinsic clamp = database.require("GPU", "clamp", List.of("UInt16", "UInt16", "UInt16"));
        GpuIntrinsic addSat = database.require("GPU", "add_sat", List.of("UByte16", "UByte16"));
        GpuIntrinsic madHi = database.require("GPU", "mad_hi", List.of("Int16", "Int16", "Int16"));
        GpuIntrinsic popcount = database.require("GPU", "popcount", List.of("ULong8"));
        GpuIntrinsic rotate = database.require("GPU", "rotate", List.of("UShort16", "UShort16"));

        assertEquals(GpuIntrinsicKind.COMMON, clamp.kind());
        assertEquals("UInt16", clamp.resultType());
        assertEquals("clamp({0}, {1}, {2})", clamp.codeTemplate());
        assertEquals("UByte16", addSat.resultType());
        assertEquals("mad_hi({0}, {1}, {2})", madHi.codeTemplate());
        assertEquals("Int8", popcount.resultType());
        assertEquals("UShort16", rotate.resultType());
    }
}
