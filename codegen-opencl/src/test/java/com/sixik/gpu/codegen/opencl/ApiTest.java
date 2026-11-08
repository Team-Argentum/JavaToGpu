package com.sixik.gpu.codegen.opencl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Значения-параметры публичного API: {@link Target}, {@link EmitOptions}, {@link KernelArg}, {@link Artifact}. */
class ApiTest {

    @Test
    void genericTarget() {
        assertEquals("opencl", Target.GENERIC.backend());
        assertEquals("generic", Target.GENERIC.vendor());
        assertEquals("any", Target.GENERIC.deviceClass());
        assertEquals(0, Target.GENERIC.features());
        assertEquals("opencl-generic", Target.GENERIC.id());
    }

    @Test
    void targetIdIncludesKnownDeviceClass() {
        Target t = new Target("opencl", "nvidia", "gpu", 1, 32, 48);
        assertEquals("opencl-nvidia-gpu", t.id());
        assertEquals(32, t.subGroupSize());
        assertEquals(48, t.computeUnits());
    }

    @Test
    void targetRejectsNulls() {
        assertThrows(IllegalArgumentException.class, () -> new Target(null, "a", "b", 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Target("a", null, "b", 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Target("a", "b", null, 0, 0, 0));
    }

    @Test
    void equalTargetsAreEqual() {
        assertEquals(new Target("opencl", "amd", "gpu", 3, 64, 20), new Target("opencl", "amd", "gpu", 3, 64, 20));
    }

    @Test
    void emitOptionsConstantsAndWithers() {
        assertFalse(EmitOptions.RELEASE.debug());
        assertTrue(EmitOptions.DEBUG.debug());
        assertFalse(EmitOptions.RELEASE.fastMath());
        assertNull(EmitOptions.RELEASE.sourceName());
        EmitOptions fast = EmitOptions.DEBUG.withFastMath(true);
        assertTrue(fast.fastMath());
        assertTrue(fast.debug());
        EmitOptions named = fast.withSourceName("Kernels.java");
        assertEquals("Kernels.java", named.sourceName());
        assertTrue(named.fastMath());
        assertFalse(EmitOptions.DEBUG.fastMath(), "исходная константа не меняется");
        assertNull(EmitOptions.DEBUG.sourceName());
    }

    @Test
    void kernelArgKinds() {
        assertEquals(0, KernelArg.SCALAR);
        assertEquals(1, KernelArg.ARRAY_PTR);
        assertEquals(2, KernelArg.ARRAY_LEN);
        KernelArg a = new KernelArg(KernelArg.ARRAY_PTR, 2, 10, 0, "x");
        assertEquals(2, a.param());
        assertEquals("x", a.name());
    }

    @Test
    void artifactCopiesArgs() {
        List<KernelArg> args = new ArrayList<>();
        args.add(new KernelArg(KernelArg.SCALAR, 0, 6, -1, "n"));
        Artifact art = new Artifact("src", "k", args, 1, "", Target.GENERIC, EmitOptions.RELEASE);
        args.add(new KernelArg(KernelArg.SCALAR, 1, 6, -1, "m"));
        assertEquals(1, art.args().size(), "список аргументов не зависит от исходного");
        assertThrows(UnsupportedOperationException.class, () -> art.args().add(null));
    }
}
