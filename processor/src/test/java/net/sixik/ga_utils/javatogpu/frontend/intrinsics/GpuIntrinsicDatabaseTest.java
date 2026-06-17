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
        GpuIntrinsic globalId = database.require("GPU", "get_global_id", List.of("int"));

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
        assertEquals(GpuIntrinsicKind.BUILTIN_ID, globalId.kind());
        assertEquals("get_global_id", globalId.backendName());
        assertEquals("int", globalId.resultType());
        assertEquals(List.of("int"), globalId.argumentTypes());
        assertTrue(database.isAllowedOwner("GPU"));
        assertTrue(database.isAllowedAllocationType("IntPtr"));
    }
}
