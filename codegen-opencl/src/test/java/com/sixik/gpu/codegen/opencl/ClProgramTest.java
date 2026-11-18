package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Шаг B6 (FRONTEND_ASM_RUNTIME.md, S8): компиляторный выход для чужого рантайма - текст программы и ABI ядер.
 */
class ClProgramTest {

    /** Текст и опции - те же, что у {@link OpenClEmitter#emitProgram}; ядра - в порядке модуля, с именами. */
    @Test
    void programMatchesEmitProgram() {
        IrModule m = ProgramTest.threeKernels();
        Result<ClProgram> r = OpenClEmitter.program(m, ProgramTest.NV, EmitOptions.RELEASE);
        assertTrue(r.isOk(), r.diagnostics().toString());
        Program p = OpenClEmitter.emitProgram(m, ProgramTest.NV, EmitOptions.RELEASE).value();
        ClProgram c = r.value();
        assertEquals(p.source(), c.source());
        assertEquals(p.buildOptions(), c.buildOptions());
        assertEquals(p.requiredFeatures(), c.requiredFeatures());
        assertEquals(List.of(1, 2, 3), c.kernels().stream().map(ClProgram.KernelAbi::function).toList());
        for (ClProgram.KernelAbi k : c.kernels()) {
            assertTrue(c.source().contains("__kernel void " + k.name() + "("), k.name());
            assertSame(k, c.kernel(k.name()));
            assertSame(k, c.named(k.debugName()));
        }
        assertEquals(List.of("k0", "k1", "k2"), c.kernels().stream().map(ClProgram.KernelAbi::debugName).toList());
        assertNull(c.kernel("missing"));
        assertNull(c.named("missing"));
    }

    /** Массив - буфер и длина; доступ буфера - по валидатору: {@code k2} только пишет, {@code k0} читает и пишет. */
    @Test
    void bufferAccessFollowsTheKernel() {
        ClProgram c = OpenClEmitter.program(ProgramTest.threeKernels(), EmitOptions.RELEASE).value();
        ClProgram.KernelAbi k0 = c.named("k0");
        assertEquals(2, k0.params().size());
        ClProgram.Param buf = k0.params().get(0);
        ClProgram.Param len = k0.params().get(1);
        assertEquals(ClProgram.Param.BUFFER, buf.kind());
        assertEquals(Types.I32, buf.type());
        assertEquals(4, buf.size());
        assertEquals(AddressSpace.GLOBAL, buf.space());
        assertTrue(buf.reads() && buf.writes());
        assertEquals(ClProgram.Param.LENGTH, len.kind());
        assertEquals(0, len.param());
        assertEquals(Types.I32, len.type());
        assertEquals(0, len.access());
        ClProgram.Param out = c.named("k2").params().get(0);
        assertEquals(Validator.ACCESS_WRITE, out.access());
        assertFalse(out.reads());
    }

    /** {@code out[0] = in[0] * s} с {@code reqd_work_group_size(64, 1, 1)}: скаляр - по значению, вход - только чтение. */
    @Test
    void scalarAndWorkGroupAreDescribed() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        int in = b.array(AddressSpace.CONSTANT, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, in, Types.F32);
        b.requireWorkGroup(64, 1, 1);
        int v = b.binary(Op.MUL, b.load(b.elemPtr(b.param(1), b.constI32(0))), b.param(2));
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), v)));
        ClProgram c = OpenClEmitter.program(b.build(), EmitOptions.RELEASE).value();
        ClProgram.KernelAbi k = c.kernels().get(0);
        assertArrayEquals(new int[]{64, 1, 1}, k.reqdWorkGroup());
        assertEquals(5, k.params().size());
        assertEquals(List.of(ClProgram.Param.BUFFER, ClProgram.Param.LENGTH, ClProgram.Param.BUFFER,
                ClProgram.Param.LENGTH, ClProgram.Param.SCALAR), k.params().stream().map(ClProgram.Param::kind).toList());
        ClProgram.Param cin = k.params().get(2);
        assertEquals(AddressSpace.CONSTANT, cin.space());
        assertEquals(Validator.ACCESS_READ, cin.access());
        ClProgram.Param s = k.params().get(4);
        assertEquals(2, s.param());
        assertEquals(Types.F32, s.type());
        assertEquals(4, s.size());
        assertEquals(-1, s.space());
        assertEquals(0, s.access());
        for (ClProgram.Param p : k.params()) {
            assertTrue(c.source().contains(" " + p.name()), p.name());
        }
    }

    @Test
    void moduleWithoutKernelsIsAnError() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32);
        b.endFunction(b.block(b.returnStmt(b.param(0))));
        Result<ClProgram> r = OpenClEmitter.program(b.build(), EmitOptions.RELEASE);
        assertFalse(r.isOk());
        assertEquals("CG-A01", r.diagnostics().get(0).code());
    }
}
