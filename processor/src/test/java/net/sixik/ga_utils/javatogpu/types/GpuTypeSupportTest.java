package net.sixik.ga_utils.javatogpu.types;

import net.sixik.ga_utils.javatogpu.api.ConstantBytePtr;
import net.sixik.ga_utils.javatogpu.api.ConstantCharPtr;
import net.sixik.ga_utils.javatogpu.api.ConstantDoublePtr;
import net.sixik.ga_utils.javatogpu.api.ConstantFloatPtr;
import net.sixik.ga_utils.javatogpu.api.ConstantIntPtr;
import net.sixik.ga_utils.javatogpu.api.ConstantLongPtr;
import net.sixik.ga_utils.javatogpu.api.ConstantShortPtr;
import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.GlobalBytePtr;
import net.sixik.ga_utils.javatogpu.api.GlobalCharPtr;
import net.sixik.ga_utils.javatogpu.api.GlobalDoublePtr;
import net.sixik.ga_utils.javatogpu.api.GlobalFloatPtr;
import net.sixik.ga_utils.javatogpu.api.GlobalIntPtr;
import net.sixik.ga_utils.javatogpu.api.GlobalLongPtr;
import net.sixik.ga_utils.javatogpu.api.GlobalShortPtr;
import net.sixik.ga_utils.javatogpu.api.LocalBytePtr;
import net.sixik.ga_utils.javatogpu.api.LocalCharPtr;
import net.sixik.ga_utils.javatogpu.api.LocalDoublePtr;
import net.sixik.ga_utils.javatogpu.api.LocalFloatPtr;
import net.sixik.ga_utils.javatogpu.api.LocalIntPtr;
import net.sixik.ga_utils.javatogpu.api.LocalLongPtr;
import net.sixik.ga_utils.javatogpu.api.LocalShortPtr;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuTypeSupportTest {

    @Test
    void discoversAnnotatedPointerTypesBySimpleAndQualifiedName() {
        assertTrue(GpuTypeSupport.isSupportedPointerType("IntPtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerType("net.sixik.ga_utils.javatogpu.api.FloatPtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerClassName("net.sixik.ga_utils.javatogpu.api.DoublePtr"));

        assertEquals("int", GpuTypeSupport.pointerValueType("IntPtr"));
        assertEquals("float", GpuTypeSupport.pointerValueType("net.sixik.ga_utils.javatogpu.api.FloatPtr"));
    }

    @Test
    void discoversAnnotatedAddressSpacePointerTypes() {
        assertTrue(GpuTypeSupport.isSupportedPointerType("GlobalFloatPtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerType("GlobalBytePtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerType("GlobalCharPtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerType("GlobalShortPtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerType("GlobalIntPtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerType("GlobalLongPtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerType("GlobalDoublePtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerType("ConstantBytePtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerType("ConstantFloatPtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerType("ConstantIntPtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerType("ConstantLongPtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerType("ConstantDoublePtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerType("LocalBytePtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerType("LocalFloatPtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerType("LocalIntPtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerType("LocalLongPtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerType("LocalDoublePtr"));

        assertEquals("float", GpuTypeSupport.pointerValueType("GlobalFloatPtr"));
        assertEquals("byte", GpuTypeSupport.pointerValueType("GlobalBytePtr"));
        assertEquals("char", GpuTypeSupport.pointerValueType("GlobalCharPtr"));
        assertEquals("short", GpuTypeSupport.pointerValueType("GlobalShortPtr"));
        assertEquals("int", GpuTypeSupport.pointerValueType("GlobalIntPtr"));
        assertEquals("long", GpuTypeSupport.pointerValueType("GlobalLongPtr"));
        assertEquals("double", GpuTypeSupport.pointerValueType("GlobalDoublePtr"));
        assertEquals("GLOBAL", GpuTypeSupport.pointerAddressSpace("GlobalFloatPtr"));
        assertEquals("CONSTANT", GpuTypeSupport.pointerAddressSpace("ConstantBytePtr"));
        assertEquals("CONSTANT", GpuTypeSupport.pointerAddressSpace("ConstantFloatPtr"));
        assertEquals("LOCAL", GpuTypeSupport.pointerAddressSpace("LocalBytePtr"));
        assertEquals("LOCAL", GpuTypeSupport.pointerAddressSpace("LocalFloatPtr"));
        assertTrue(GpuTypeSupport.isAddressSpacePointerType("GlobalFloatPtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("float[]", "GlobalFloatPtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("byte[]", "GlobalBytePtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("char[]", "GlobalCharPtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("short[]", "GlobalShortPtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("int[]", "GlobalIntPtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("long[]", "GlobalLongPtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("double[]", "GlobalDoublePtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("byte[]", "ConstantBytePtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("char[]", "ConstantCharPtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("short[]", "ConstantShortPtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("int[]", "ConstantIntPtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("long[]", "ConstantLongPtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("float[]", "ConstantFloatPtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("double[]", "ConstantDoublePtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("byte[]", "LocalBytePtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("char[]", "LocalCharPtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("short[]", "LocalShortPtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("int[]", "LocalIntPtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("long[]", "LocalLongPtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("float[]", "LocalFloatPtr"));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("double[]", "LocalDoublePtr"));
    }

    @Test
    void addressSpaceByteViewsExposeConsistentReinterpretHelpers() throws ReflectiveOperationException {
        Map<Class<?>, Map<String, Class<?>>> expectations = Map.of(
                GlobalBytePtr.class, Map.of(
                        "asCharPtr", GlobalCharPtr.class,
                        "asShortPtr", GlobalShortPtr.class,
                        "asIntPtr", GlobalIntPtr.class,
                        "asLongPtr", GlobalLongPtr.class,
                        "asFloatPtr", GlobalFloatPtr.class,
                        "asDoublePtr", GlobalDoublePtr.class
                ),
                ConstantBytePtr.class, Map.of(
                        "asCharPtr", ConstantCharPtr.class,
                        "asShortPtr", ConstantShortPtr.class,
                        "asIntPtr", ConstantIntPtr.class,
                        "asLongPtr", ConstantLongPtr.class,
                        "asFloatPtr", ConstantFloatPtr.class,
                        "asDoublePtr", ConstantDoublePtr.class
                ),
                LocalBytePtr.class, Map.of(
                        "asCharPtr", LocalCharPtr.class,
                        "asShortPtr", LocalShortPtr.class,
                        "asIntPtr", LocalIntPtr.class,
                        "asLongPtr", LocalLongPtr.class,
                        "asFloatPtr", LocalFloatPtr.class,
                        "asDoublePtr", LocalDoublePtr.class
                )
        );

        for (Map.Entry<Class<?>, Map<String, Class<?>>> owner : expectations.entrySet()) {
            for (Map.Entry<String, Class<?>> methodExpectation : owner.getValue().entrySet()) {
                Method method = owner.getKey().getMethod(methodExpectation.getKey());
                assertEquals(methodExpectation.getValue(), method.getReturnType());
            }
        }
    }

    @Test
    void addressSpacePointersExposeSymmetricBridgeHelpersOnGpuFacade() throws ReflectiveOperationException {
        assertEquals(GlobalBytePtr.class, GPU.class.getMethod("global", byte[].class).getReturnType());
        assertEquals(GlobalCharPtr.class, GPU.class.getMethod("global", char[].class).getReturnType());
        assertEquals(GlobalShortPtr.class, GPU.class.getMethod("global", short[].class).getReturnType());
        assertEquals(GlobalIntPtr.class, GPU.class.getMethod("global", int[].class).getReturnType());
        assertEquals(GlobalLongPtr.class, GPU.class.getMethod("global", long[].class).getReturnType());
        assertEquals(GlobalFloatPtr.class, GPU.class.getMethod("global", float[].class).getReturnType());
        assertEquals(GlobalDoublePtr.class, GPU.class.getMethod("global", double[].class).getReturnType());

        assertEquals(ConstantBytePtr.class, GPU.class.getMethod("constant", byte[].class).getReturnType());
        assertEquals(ConstantCharPtr.class, GPU.class.getMethod("constant", char[].class).getReturnType());
        assertEquals(ConstantShortPtr.class, GPU.class.getMethod("constant", short[].class).getReturnType());
        assertEquals(ConstantIntPtr.class, GPU.class.getMethod("constant", int[].class).getReturnType());
        assertEquals(ConstantLongPtr.class, GPU.class.getMethod("constant", long[].class).getReturnType());
        assertEquals(ConstantFloatPtr.class, GPU.class.getMethod("constant", float[].class).getReturnType());
        assertEquals(ConstantDoublePtr.class, GPU.class.getMethod("constant", double[].class).getReturnType());

        assertEquals(LocalBytePtr.class, GPU.class.getMethod("local", byte[].class).getReturnType());
        assertEquals(LocalCharPtr.class, GPU.class.getMethod("local", char[].class).getReturnType());
        assertEquals(LocalShortPtr.class, GPU.class.getMethod("local", short[].class).getReturnType());
        assertEquals(LocalIntPtr.class, GPU.class.getMethod("local", int[].class).getReturnType());
        assertEquals(LocalLongPtr.class, GPU.class.getMethod("local", long[].class).getReturnType());
        assertEquals(LocalFloatPtr.class, GPU.class.getMethod("local", float[].class).getReturnType());
        assertEquals(LocalDoublePtr.class, GPU.class.getMethod("local", double[].class).getReturnType());
    }

    @Test
    void discoversAnnotatedScalarAliasTypesBySimpleAndQualifiedName() {
        assertTrue(GpuTypeSupport.isSupportedScalarAliasType("UInt"));
        assertTrue(GpuTypeSupport.isSupportedScalarAliasType("net.sixik.ga_utils.javatogpu.api.ULong"));
        assertTrue(GpuTypeSupport.isSupportedScalarAliasClassName("net.sixik.ga_utils.javatogpu.api.UShort"));

        assertEquals("uint", GpuTypeSupport.openClScalarAliasTypeName("UInt"));
        assertEquals("int", GpuTypeSupport.scalarAliasValueType("UInt"));
        assertEquals(Integer.BYTES, GpuTypeSupport.scalarByteSize("UInt"));
    }

    @Test
    void intrinsicAllocationWhitelistUsesAnnotatedPointerAndAliasTypes() {
        GpuIntrinsicDatabase database = GpuIntrinsicDatabase.createDefault();

        assertTrue(database.isAllowedAllocationType("BytePtr"));
        assertTrue(database.isAllowedAllocationType("net.sixik.ga_utils.javatogpu.api.UInt"));
    }

    @Test
    void discoversAnnotatedUnsignedVectorTypesBySimpleAndQualifiedName() {
        assertTrue(GpuTypeSupport.isSupportedVectorType("UInt2"));
        assertTrue(GpuTypeSupport.isSupportedVectorType("net.sixik.ga_utils.javatogpu.api.UByte16"));
        assertTrue(GpuTypeSupport.isSupportedVectorClassName("net.sixik.ga_utils.javatogpu.api.ULong8"));

        assertEquals("uint16", GpuTypeSupport.openClVectorTypeName("UInt16"));
        assertEquals("int", GpuTypeSupport.vectorComponentType("UInt16", "sa"));
        assertEquals(List.of("s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "sa", "sb", "sc", "sd", "se", "sf"),
                GpuTypeSupport.vectorFieldNames("net.sixik.ga_utils.javatogpu.api.UInt16"));
        assertEquals(16 * Integer.BYTES, GpuTypeSupport.vectorByteSize("UInt16"));
        assertEquals(4, GpuTypeSupport.vectorStorageWidth("UByte3"));
    }

    @Test
    void discoversAnnotatedSignedNarrowVectorTypes() {
        assertTrue(GpuTypeSupport.isSupportedVectorType("Byte2"));
        assertTrue(GpuTypeSupport.isSupportedVectorType("Short4"));
        assertTrue(GpuTypeSupport.isSupportedVectorClassName("net.sixik.ga_utils.javatogpu.api.Byte3"));

        assertEquals("char2", GpuTypeSupport.openClVectorTypeName("Byte2"));
        assertEquals("short", GpuTypeSupport.vectorComponentType("Short3", "y"));
        assertEquals(List.of("x", "y", "z"), GpuTypeSupport.vectorFieldNames("net.sixik.ga_utils.javatogpu.api.Byte3"));
        assertEquals(4, GpuTypeSupport.vectorStorageWidth("Byte3"));
        assertEquals(4 * Short.BYTES, GpuTypeSupport.vectorByteSize("Short4"));
    }

    @Test
    void discoversAnnotatedWideSignedIntVectorTypes() {
        assertTrue(GpuTypeSupport.isSupportedVectorType("Int8"));
        assertTrue(GpuTypeSupport.isSupportedVectorType("Int16"));
        assertTrue(GpuTypeSupport.isSupportedVectorClassName("net.sixik.ga_utils.javatogpu.api.Int8"));

        assertEquals("int8", GpuTypeSupport.openClVectorTypeName("Int8"));
        assertEquals("int16", GpuTypeSupport.openClVectorTypeName("Int16"));
        assertEquals("int", GpuTypeSupport.vectorComponentType("Int16", "sf"));
        assertEquals(8 * Integer.BYTES, GpuTypeSupport.vectorByteSize("Int8"));
        assertEquals(16 * Integer.BYTES, GpuTypeSupport.vectorByteSize("Int16"));
    }

}
