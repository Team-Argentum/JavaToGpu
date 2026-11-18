package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Шаг B4 (FRONTEND_ASM_RUNTIME.md, 18.7): модуль с несколькими ядрами печатается одной программой
 * ({@link OpenClEmitter#emitProgram}).
 */
class ProgramTest {

    static final Target NV = new Target("opencl", "nvidia", "gpu", 1, 32, 48);

    /**
     * Модуль из трёх ядер над {@code int[] a} с общей функцией {@code sq(x) = x * x} ({@code inline = NEVER}):
     * {@code k0: a[i] = sq(a[i])}, {@code k1: a[i] = sq(a[i]) + 1}, {@code k2: a[i] = sq(i)}.
     *
     * @param fp флаги {@code fp} ядер по порядку
     */
    static IrModule threeKernels(int... fp) {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        int sq = b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32);
        b.inlinePolicy(IrModule.INLINE_NEVER);
        b.nameFunction("sq");
        b.endFunction(b.block(b.returnStmt(b.binary(Op.MUL, b.param(0), b.param(0)))));
        for (int k = 0; k < 3; k++) {
            b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
            b.nameFunction("k" + k);
            if (fp.length > k) {
                b.fpMode(fp[k]);
            }
            int id = b.declareLocal(Types.I32);
            int decl = b.localDecl(id, b.intrinsic(Fixtures.GET_GLOBAL_ID, b.constI32(0)));
            int arg = k == 2 ? b.localGet(id) : b.load(b.elemPtr(b.param(0), b.localGet(id)));
            int v = b.call(sq, arg);
            if (k == 1) {
                v = b.binary(Op.ADD, v, b.constI32(1));
            }
            int st = b.store(b.elemPtr(b.param(0), b.localGet(id)), v);
            b.endFunction(b.block(decl, st));
        }
        return b.build();
    }

    private static int count(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** Критерий B4: одна копия общей функции, {@code noinline}, все ядра - {@code __kernel}. */
    @Test
    void sharedFunctionIsPrintedOnce() {
        IrModule m = threeKernels();
        Result<Program> r = OpenClEmitter.emitProgram(m, NV, EmitOptions.RELEASE);
        assertTrue(r.isOk(), r.diagnostics().toString());
        Program p = r.value();
        String src = p.source();
        assertEquals(1, count(src, "^int f0\\("), src);
        assertEquals(1, count(src, "^__attribute__\\(\\(noinline\\)\\)$"), src);
        assertEquals(3, count(src, "^__kernel void "), src);
        assertEquals(List.of(1, 2, 3), p.kernels().stream().map(Program.Kernel::function).toList());
        assertEquals(List.of("k1", "k2", "k3"), p.kernels().stream().map(Program.Kernel::entry).toList());
        for (Program.Kernel k : p.kernels()) {
            assertEquals(2, k.args().size(), "array: pointer and length");
        }
    }

    /** Одиночный {@code emit} ядра программы печатает только его замыкание, тем же текстом функций. */
    @Test
    void singleKernelEmitIsTheClosure() {
        IrModule m = threeKernels();
        Result<Artifact> one = OpenClEmitter.emit(m, 2, NV, EmitOptions.RELEASE);
        assertTrue(one.isOk(), one.diagnostics().toString());
        assertEquals(1, count(one.value().source(), "^__kernel void "), one.value().source());
        assertEquals("k2", one.value().entry());
        String program = OpenClEmitter.emitProgram(m, NV, EmitOptions.RELEASE).value().source();
        String f0 = one.value().source().substring(one.value().source().indexOf("__attribute__((noinline))"));
        f0 = f0.substring(0, f0.indexOf("}\n") + 2);
        assertTrue(program.contains(f0), program);
    }

    /** Модуль с одним ядром: программа - тот же текст, что {@code emit}. */
    @Test
    void oneKernelProgramEqualsEmit() {
        IrModule m = Fixtures.computeSine();
        Program p = OpenClEmitter.emitProgram(m, NV, EmitOptions.RELEASE).value();
        Artifact a = OpenClEmitter.emit(m, NV).value();
        assertEquals(a.source(), p.source());
        assertEquals(a.args(), p.kernels().get(0).args());
        assertEquals(a.buildOptions(), p.buildOptions());
    }

    /**
     * Флаги {@code fp} общей функции - пересечение флагов ядер, которые её достигают: {@code max} у float печатается
     * {@code fmax} только при {@code finite+nsz}, и строгое ядро это запрещает для всей программы.
     */
    @Test
    void sharedFunctionGetsTheIntersectionOfFpFlags() {
        int relaxed = IrModule.FP_FINITE | IrModule.FP_NO_SIGNED_ZERO;
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        int mx = b.beginFunction(IrModule.DEVICE, Types.F32, Types.F32, Types.F32);
        b.endFunction(b.block(b.returnStmt(b.intrinsic(com.sixik.gpu.ir.Builtins.idOf("max"), b.param(0), b.param(1)))));
        for (int k = 0; k < 2; k++) {
            b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
            b.fpMode(k == 0 ? relaxed : 0);
            int p = b.elemPtr(b.param(0), b.constI32(0));
            b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)),
                    b.call(mx, b.load(p), b.constF32(1f)))));
        }
        IrModule m = b.build();
        Result<Artifact> relaxedOnly = OpenClEmitter.emit(m, 1, NV, EmitOptions.RELEASE);
        assertTrue(relaxedOnly.isOk(), relaxedOnly.diagnostics().toString());
        assertTrue(relaxedOnly.value().source().contains("fmax("), relaxedOnly.value().source());
        Result<Program> both = OpenClEmitter.emitProgram(m, NV, EmitOptions.RELEASE);
        assertTrue(both.isOk(), both.diagnostics().toString());
        assertFalse(both.value().source().contains("fmax("), both.value().source());
    }

    /** Модуль без ядер - {@code CG-A01}. */
    @Test
    void moduleWithoutKernelsIsRejected() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32);
        b.endFunction(b.block(b.returnStmt(b.param(0))));
        Result<Program> r = OpenClEmitter.emitProgram(b.build(), NV, EmitOptions.RELEASE);
        assertFalse(r.isOk());
        assertEquals("CG-A01", r.diagnostics().get(0).code());
    }
}
