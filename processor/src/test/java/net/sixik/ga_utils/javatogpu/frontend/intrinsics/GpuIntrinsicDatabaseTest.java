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
}
