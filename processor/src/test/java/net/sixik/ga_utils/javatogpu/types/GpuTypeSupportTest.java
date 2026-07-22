package net.sixik.ga_utils.javatogpu.types;

import net.sixik.ga_utils.javatogpu.api.pointers.constant.ConstantBytePtr;
import net.sixik.ga_utils.javatogpu.api.pointers.constant.ConstantCharPtr;
import net.sixik.ga_utils.javatogpu.api.pointers.constant.ConstantDoublePtr;
import net.sixik.ga_utils.javatogpu.api.pointers.constant.ConstantFloatPtr;
import net.sixik.ga_utils.javatogpu.api.pointers.constant.ConstantIntPtr;
import net.sixik.ga_utils.javatogpu.api.pointers.constant.ConstantLongPtr;
import net.sixik.ga_utils.javatogpu.api.pointers.constant.ConstantShortPtr;
import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.pointers.global.GlobalBytePtr;
import net.sixik.ga_utils.javatogpu.api.pointers.global.GlobalCharPtr;
import net.sixik.ga_utils.javatogpu.api.pointers.global.GlobalDoublePtr;
import net.sixik.ga_utils.javatogpu.api.pointers.global.GlobalFloatPtr;
import net.sixik.ga_utils.javatogpu.api.pointers.global.GlobalIntPtr;
import net.sixik.ga_utils.javatogpu.api.pointers.global.GlobalLongPtr;
import net.sixik.ga_utils.javatogpu.api.pointers.global.GlobalShortPtr;
import net.sixik.ga_utils.javatogpu.api.pointers.local.LocalBytePtr;
import net.sixik.ga_utils.javatogpu.api.pointers.local.LocalCharPtr;
import net.sixik.ga_utils.javatogpu.api.pointers.local.LocalDoublePtr;
import net.sixik.ga_utils.javatogpu.api.pointers.local.LocalFloatPtr;
import net.sixik.ga_utils.javatogpu.api.pointers.local.LocalIntPtr;
import net.sixik.ga_utils.javatogpu.api.pointers.local.LocalLongPtr;
import net.sixik.ga_utils.javatogpu.api.pointers.local.LocalShortPtr;
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
        assertTrue(GpuTypeSupport.isSupportedPointerType("net.sixik.ga_utils.javatogpu.api.pointers.FloatPtr"));
        assertTrue(GpuTypeSupport.isSupportedPointerClassName("net.sixik.ga_utils.javatogpu.api.pointers.DoublePtr"));

        assertEquals("int", GpuTypeSupport.pointerValueType("IntPtr"));
        assertEquals("float", GpuTypeSupport.pointerValueType("net.sixik.ga_utils.javatogpu.api.pointers.FloatPtr"));
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
    void discoversGroupedPointerPackagesByQualifiedName() {
        String privateIntPtr = "net.sixik.ga_utils.javatogpu.api.pointers.IntPtr";
        String globalFloatPtr = "net.sixik.ga_utils.javatogpu.api.pointers.global.GlobalFloatPtr";
        String constantBytePtr = "net.sixik.ga_utils.javatogpu.api.pointers.constant.ConstantBytePtr";
        String localIntPtr = "net.sixik.ga_utils.javatogpu.api.pointers.local.LocalIntPtr";

        assertTrue(GpuTypeSupport.isSupportedPointerType(privateIntPtr));
        assertTrue(GpuTypeSupport.isSupportedPointerClassName(globalFloatPtr));
        assertTrue(GpuTypeSupport.isSupportedPointerType(constantBytePtr));
        assertTrue(GpuTypeSupport.isSupportedPointerType(localIntPtr));

        assertEquals("int", GpuTypeSupport.pointerValueType(privateIntPtr));
        assertEquals("float", GpuTypeSupport.pointerValueType(globalFloatPtr));
        assertEquals("GLOBAL", GpuTypeSupport.pointerAddressSpace(globalFloatPtr));
        assertEquals("CONSTANT", GpuTypeSupport.pointerAddressSpace(constantBytePtr));
        assertEquals("LOCAL", GpuTypeSupport.pointerAddressSpace(localIntPtr));
        assertTrue(GpuTypeSupport.isArrayCompatibleWithPointerType("float[]", globalFloatPtr));

        GpuIntrinsicDatabase database = GpuIntrinsicDatabase.createDefault();
        assertTrue(database.isAllowedAllocationType(privateIntPtr));
        assertTrue(database.isAllowedAllocationType(globalFloatPtr));
    }

    @Test
    void groupedAddressSpaceByteViewsExposeConsistentHelpers() throws ReflectiveOperationException {
        assertGroupedByteViewHelpers("net.sixik.ga_utils.javatogpu.api.pointers.global", "Global");
        assertGroupedByteViewHelpers("net.sixik.ga_utils.javatogpu.api.pointers.constant", "Constant");
        assertGroupedByteViewHelpers("net.sixik.ga_utils.javatogpu.api.pointers.local", "Local");
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
    void addressSpaceByteViewsExposeConsistentOffsetReadHelpers() throws ReflectiveOperationException {
        Map<String, Class<?>> expectations = Map.of(
                "readByteAt", byte.class,
                "readShortAt", short.class,
                "readIntAt", int.class,
                "readLongAt", long.class,
                "readFloatAt", float.class,
                "readDoubleAt", double.class
        );

        for (Class<?> owner : List.of(GlobalBytePtr.class, ConstantBytePtr.class, LocalBytePtr.class)) {
            for (Map.Entry<String, Class<?>> methodExpectation : expectations.entrySet()) {
                Method method = owner.getMethod(methodExpectation.getKey(), int.class);
                assertEquals(methodExpectation.getValue(), method.getReturnType());
            }
        }
    }

    @Test
    void addressSpaceByteViewsExposeConsistentOffsetPointerHelpers() throws ReflectiveOperationException {
        Map<Class<?>, Map<String, Class<?>>> expectations = Map.of(
                GlobalBytePtr.class, Map.of(
                        "bytePtrAt", GlobalBytePtr.class,
                        "charPtrAt", GlobalCharPtr.class,
                        "shortPtrAt", GlobalShortPtr.class,
                        "intPtrAt", GlobalIntPtr.class,
                        "longPtrAt", GlobalLongPtr.class,
                        "floatPtrAt", GlobalFloatPtr.class,
                        "doublePtrAt", GlobalDoublePtr.class
                ),
                ConstantBytePtr.class, Map.of(
                        "bytePtrAt", ConstantBytePtr.class,
                        "charPtrAt", ConstantCharPtr.class,
                        "shortPtrAt", ConstantShortPtr.class,
                        "intPtrAt", ConstantIntPtr.class,
                        "longPtrAt", ConstantLongPtr.class,
                        "floatPtrAt", ConstantFloatPtr.class,
                        "doublePtrAt", ConstantDoublePtr.class
                ),
                LocalBytePtr.class, Map.of(
                        "bytePtrAt", LocalBytePtr.class,
                        "charPtrAt", LocalCharPtr.class,
                        "shortPtrAt", LocalShortPtr.class,
                        "intPtrAt", LocalIntPtr.class,
                        "longPtrAt", LocalLongPtr.class,
                        "floatPtrAt", LocalFloatPtr.class,
                        "doublePtrAt", LocalDoublePtr.class
                )
        );

        for (Map.Entry<Class<?>, Map<String, Class<?>>> owner : expectations.entrySet()) {
            for (Map.Entry<String, Class<?>> methodExpectation : owner.getValue().entrySet()) {
                Method method = owner.getKey().getMethod(methodExpectation.getKey(), int.class);
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
        assertTrue(GpuTypeSupport.isSupportedScalarAliasType("net.sixik.ga_utils.javatogpu.api.types.longs.ULong"));
        assertTrue(GpuTypeSupport.isSupportedScalarAliasClassName("net.sixik.ga_utils.javatogpu.api.types.shorts.UShort"));

        assertEquals("uint", GpuTypeSupport.openClScalarAliasTypeName("UInt"));
        assertEquals("int", GpuTypeSupport.scalarAliasValueType("UInt"));
        assertEquals(Integer.BYTES, GpuTypeSupport.scalarByteSize("UInt"));
    }

    @Test
    void intrinsicAllocationWhitelistUsesAnnotatedPointerAndAliasTypes() {
        GpuIntrinsicDatabase database = GpuIntrinsicDatabase.createDefault();

        assertTrue(database.isAllowedAllocationType("BytePtr"));
        assertTrue(database.isAllowedAllocationType("net.sixik.ga_utils.javatogpu.api.types.integers.UInt"));
    }

    @Test
    void discoversAnnotatedUnsignedVectorTypesBySimpleAndQualifiedName() {
        assertTrue(GpuTypeSupport.isSupportedVectorType("UInt2"));
        assertTrue(GpuTypeSupport.isSupportedVectorType("net.sixik.ga_utils.javatogpu.api.types.bytes.UByte16"));
        assertTrue(GpuTypeSupport.isSupportedVectorClassName("net.sixik.ga_utils.javatogpu.api.types.longs.ULong8"));

        assertEquals("uint16", GpuTypeSupport.openClVectorTypeName("UInt16"));
        assertEquals("int", GpuTypeSupport.vectorComponentType("UInt16", "sa"));
        assertEquals(List.of("s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "sa", "sb", "sc", "sd", "se", "sf"),
                GpuTypeSupport.vectorFieldNames("net.sixik.ga_utils.javatogpu.api.types.integers.UInt16"));
        assertEquals(16 * Integer.BYTES, GpuTypeSupport.vectorByteSize("UInt16"));
        assertEquals(4, GpuTypeSupport.vectorStorageWidth("UByte3"));
    }

    @Test
    void discoversAnnotatedSignedNarrowVectorTypes() {
        assertTrue(GpuTypeSupport.isSupportedVectorType("Byte2"));
        assertTrue(GpuTypeSupport.isSupportedVectorType("Short4"));
        assertTrue(GpuTypeSupport.isSupportedVectorClassName("net.sixik.ga_utils.javatogpu.api.types.bytes.Byte3"));

        assertEquals("char2", GpuTypeSupport.openClVectorTypeName("Byte2"));
        assertEquals("short", GpuTypeSupport.vectorComponentType("Short3", "y"));
        assertEquals(List.of("x", "y", "z"), GpuTypeSupport.vectorFieldNames("net.sixik.ga_utils.javatogpu.api.types.bytes.Byte3"));
        assertEquals(4, GpuTypeSupport.vectorStorageWidth("Byte3"));
        assertEquals(4 * Short.BYTES, GpuTypeSupport.vectorByteSize("Short4"));
    }

    @Test
    void discoversAnnotatedWideSignedIntVectorTypes() {
        assertTrue(GpuTypeSupport.isSupportedVectorType("Int8"));
        assertTrue(GpuTypeSupport.isSupportedVectorType("Int16"));
        assertTrue(GpuTypeSupport.isSupportedVectorClassName("net.sixik.ga_utils.javatogpu.api.types.integers.Int8"));

        assertEquals("int8", GpuTypeSupport.openClVectorTypeName("Int8"));
        assertEquals("int16", GpuTypeSupport.openClVectorTypeName("Int16"));
        assertEquals("int", GpuTypeSupport.vectorComponentType("Int16", "sf"));
        assertEquals(8 * Integer.BYTES, GpuTypeSupport.vectorByteSize("Int8"));
        assertEquals(16 * Integer.BYTES, GpuTypeSupport.vectorByteSize("Int16"));
    }

    private static void assertGroupedByteViewHelpers(String packageName, String prefix) throws ReflectiveOperationException {
        Class<?> bytePtr = Class.forName(packageName + "." + prefix + "BytePtr");
        Map<String, Class<?>> expectations = Map.of(
                "bytePtrAt", bytePtr,
                "charPtrAt", Class.forName(packageName + "." + prefix + "CharPtr"),
                "shortPtrAt", Class.forName(packageName + "." + prefix + "ShortPtr"),
                "intPtrAt", Class.forName(packageName + "." + prefix + "IntPtr"),
                "longPtrAt", Class.forName(packageName + "." + prefix + "LongPtr"),
                "floatPtrAt", Class.forName(packageName + "." + prefix + "FloatPtr"),
                "doublePtrAt", Class.forName(packageName + "." + prefix + "DoublePtr")
        );

        for (Map.Entry<String, Class<?>> methodExpectation : expectations.entrySet()) {
            Method method = bytePtr.getMethod(methodExpectation.getKey(), int.class);
            assertEquals(methodExpectation.getValue(), method.getReturnType());
        }

        assertEquals(expectations.get("charPtrAt"), bytePtr.getMethod("asCharPtr").getReturnType());
        assertEquals(expectations.get("shortPtrAt"), bytePtr.getMethod("asShortPtr").getReturnType());
        assertEquals(expectations.get("intPtrAt"), bytePtr.getMethod("asIntPtr").getReturnType());
        assertEquals(expectations.get("longPtrAt"), bytePtr.getMethod("asLongPtr").getReturnType());
        assertEquals(expectations.get("floatPtrAt"), bytePtr.getMethod("asFloatPtr").getReturnType());
        assertEquals(expectations.get("doublePtrAt"), bytePtr.getMethod("asDoublePtr").getReturnType());
    }

}
