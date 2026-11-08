package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.Features;
import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.samples.ControlKernels;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Поведение {@link OpenClEmitter#emit}: состав текста, режимы, ошибки значениями. */
class EmitterTest {

    private static Artifact emit(IrModule m, EmitOptions o) {
        Result<Artifact> r = OpenClEmitter.emit(m, m.functionCount() - 1, Target.GENERIC, o);
        assertTrue(r.isOk(), String.valueOf(r.diagnostics()));
        return r.value();
    }

    private static String code(Result<Artifact> r) {
        assertFalse(r.isOk(), "ожидалась ошибка");
        assertEquals(1, r.diagnostics().stream().map(Diagnostic::code).distinct().count(), r.diagnostics().toString());
        return r.diagnostics().get(0).code();
    }

    // ---- режимы и детерминизм ----------------------------------------------------------------

    @Test
    void releaseTextHasNoNamesAndNoLineDirectives() {
        String src = emit(Fixtures.computeSine(), EmitOptions.RELEASE).source();
        assertFalse(src.contains("computeSine"));
        assertFalse(src.contains("input"));
        assertFalse(src.contains("#line"));
        assertTrue(src.contains("__kernel void k0("));
        assertTrue(src.contains("p0_len"));
    }

    @Test
    void debugTextCarriesNamesAndLines() {
        String src = emit(Fixtures.computeSine(), EmitOptions.DEBUG).source();
        assertTrue(src.contains("__kernel void computeSine("));
        assertTrue(src.contains("__global float* input"));
        assertTrue(src.contains("input_len"));
        assertTrue(src.contains("#line 3\n"));
    }

    @Test
    void sourceNameGoesIntoLineDirectives() {
        String src = emit(Fixtures.computeSine(), EmitOptions.DEBUG.withSourceName("Sine.java")).source();
        assertTrue(src.contains("#line 3 \"Sine.java\"\n"));
        assertFalse(emit(Fixtures.computeSine(), EmitOptions.RELEASE.withSourceName("Sine.java")).source()
                .contains("Sine.java"), "в release имён нет");
    }

    @Test
    void releaseDoesNotDependOnDebugInfo() {
        assertEquals(emit(Fixtures.computeSine(), EmitOptions.RELEASE).source(),
                emit(Fixtures.computeSineWithoutDebug(), EmitOptions.RELEASE).source());
    }

    @Test
    void emitIsDeterministic() {
        for (EmitOptions o : new EmitOptions[]{EmitOptions.RELEASE, EmitOptions.DEBUG}) {
            for (SampleKernels.Kernel k : SampleKernels.all()) {
                assertEquals(emit(k.module(), o).source(), emit(k.module(), o).source(), k.label());
            }
        }
    }

    @Test
    void textDoesNotDependOnTargetInV1() {
        IrModule m = Fixtures.computeSine();
        Result<Artifact> a = OpenClEmitter.emit(m, Target.GENERIC);
        Result<Artifact> b = OpenClEmitter.emit(m, m.functionCount() - 1,
                new Target("opencl", "amd", "gpu", 3, 64, 20), EmitOptions.RELEASE);
        assertEquals(a.value().source(), b.value().source());
        assertEquals("amd", b.value().target().vendor());
    }

    @Test
    void defaultOverloadEmitsLastFunctionInRelease() {
        IrModule m = Fixtures.computeSine();
        Artifact a = OpenClEmitter.emit(m, Target.GENERIC).value();
        assertEquals(emit(m, EmitOptions.RELEASE).source(), a.source());
        assertEquals(EmitOptions.RELEASE, a.options());
        assertEquals("k0", a.entry());
    }

    @Test
    void sourceHeaderIsAscii() {
        for (SampleKernels.Kernel k : SampleKernels.all()) {
            for (char c : emit(k.module(), EmitOptions.DEBUG).source().toCharArray()) {
                assertTrue(c < 128, k.label() + ": не ASCII символ " + (int) c);
            }
        }
    }

    // ---- математика float ---------------------------------------------------------------------

    @Test
    void strictFloatByDefault() {
        Artifact a = emit(Fixtures.computeSine(), EmitOptions.RELEASE);
        assertTrue(a.source().contains("#pragma OPENCL FP_CONTRACT OFF"));
        assertEquals("", a.buildOptions());
    }

    @Test
    void fastMathDropsContractPragmaAndAddsBuildOption() {
        Artifact a = emit(Fixtures.computeSine(), EmitOptions.RELEASE.withFastMath(true));
        assertFalse(a.source().contains("FP_CONTRACT"));
        assertEquals("-cl-fast-relaxed-math", a.buildOptions());
        assertTrue(a.options().fastMath());
    }

    // ---- возможности устройства ---------------------------------------------------------------

    @Test
    void doubleKernelEnablesFp64() {
        Artifact a = emit(HandModules.ops(Types.F64), EmitOptions.RELEASE);
        assertTrue(a.source().contains("#pragma OPENCL EXTENSION cl_khr_fp64 : enable"));
        assertEquals(Features.FP64, a.requiredFeatures() & Features.FP64);
    }

    @Test
    void floatOnlyKernelNeedsNoExtensions() {
        Artifact a = emit(HandModules.ops(Types.F32), EmitOptions.RELEASE);
        assertFalse(a.source().contains("EXTENSION"));
        assertEquals(0, a.requiredFeatures());
    }

    @Test
    void int64AtomicsEnableExtension() {
        Artifact a = emit(HandModules.atomics(), EmitOptions.RELEASE);
        assertTrue(a.source().contains("cl_khr_int64_base_atomics"));
        assertTrue((a.requiredFeatures() & Features.INT64_ATOMICS) != 0);
        assertTrue(a.source().contains("atom_add("));
        assertTrue(a.source().contains("atomic_add("));
    }

    // ---- сигнатура ядра -----------------------------------------------------------------------

    @Test
    void arrayParametersExpandToPointerAndLength() {
        Artifact a = emit(Fixtures.computeSine(), EmitOptions.DEBUG);
        List<KernelArg> args = a.args();
        assertEquals(4, args.size());
        assertEquals(KernelArg.ARRAY_PTR, args.get(0).kind());
        assertEquals(0, args.get(0).param());
        assertEquals(Types.F32, args.get(0).type());
        assertEquals(AddressSpace.GLOBAL, args.get(0).space());
        assertEquals("input", args.get(0).name());
        assertEquals(KernelArg.ARRAY_LEN, args.get(1).kind());
        assertEquals(0, args.get(1).param());
        assertEquals(Types.I32, args.get(1).type());
        assertEquals(-1, args.get(1).space());
        assertEquals("input_len", args.get(1).name());
        assertEquals("output", args.get(2).name());
        assertEquals(1, args.get(3).param());
    }

    @Test
    void scalarParametersStayScalars() {
        SampleKernels.Kernel k = SampleKernels.kernel(ControlKernels.class, "parameters");
        Artifact a = emit(k.module(), EmitOptions.DEBUG);
        List<KernelArg> args = a.args();
        assertEquals(6, args.size(), "int, long, float, double и массив с длиной");
        assertEquals(KernelArg.SCALAR, args.get(0).kind());
        assertEquals(Types.I32, args.get(0).type());
        assertEquals(Types.I64, args.get(1).type());
        assertEquals(Types.F32, args.get(2).type());
        assertEquals(Types.F64, args.get(3).type());
        assertEquals(KernelArg.ARRAY_PTR, args.get(4).kind());
        assertEquals(KernelArg.ARRAY_LEN, args.get(5).kind());
        for (KernelArg arg : args) {
            assertTrue(a.source().contains(arg.name()), arg.name());
        }
    }

    @Test
    void constantArrayKeepsItsAddressSpace() {
        Artifact a = emit(HandModules.deviceFunctions(), EmitOptions.DEBUG);
        assertEquals(AddressSpace.CONSTANT, a.args().get(2).space());
        assertTrue(a.source().contains("__constant float* table"));
    }

    @Test
    void requiredWorkGroupBecomesAttribute() {
        assertTrue(emit(Fixtures.computeSine(), EmitOptions.RELEASE).source()
                .contains("__attribute__((reqd_work_group_size(256, 1, 1)))"));
        assertTrue(emit(HandModules.workGroupAttribute(), EmitOptions.RELEASE).source()
                .contains("reqd_work_group_size(64, 2, 1)"));
        assertFalse(emit(HandModules.ops(Types.I32), EmitOptions.RELEASE).source().contains("reqd_work_group_size"));
    }

    // ---- состав функций -----------------------------------------------------------------------

    @Test
    void onlyFunctionsReachableFromTheKernelAreEmitted() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32);
        b.nameFunction("unused");
        b.endFunction(b.block(b.returnStmt(b.param(0))));
        int used = b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32);
        b.nameFunction("helper");
        b.endFunction(b.block(b.returnStmt(b.binary(Op.ADD, b.param(0), b.constI32(1)))));
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.nameFunction("main");
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.call(used, b.constI32(1)))));
        Artifact a = emit(b.build(), EmitOptions.DEBUG);
        assertTrue(a.source().contains("int helper("));
        assertFalse(a.source().contains("unused"));
        assertTrue(a.source().indexOf("int helper(") < a.source().indexOf("__kernel void main("),
                "вызываемая функция печатается раньше вызывающей");
    }

    @Test
    void anyKernelOfTheModuleCanBeSelected() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        int first = b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.nameFunction("first");
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constI32(1))));
        int second = b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.nameFunction("second");
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constI32(2))));
        IrModule m = b.build();
        Artifact a = OpenClEmitter.emit(m, first, Target.GENERIC, EmitOptions.DEBUG).value();
        assertEquals("first", a.entry());
        assertFalse(a.source().contains("second"));
        assertEquals("second", OpenClEmitter.emit(m, second, Target.GENERIC, EmitOptions.DEBUG).value().entry());
    }

    // ---- имена --------------------------------------------------------------------------------

    @Test
    void awkwardDebugNamesAreMangled() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, Types.I32);
        b.nameFunction("get_work");
        b.nameParams("int", "sin");
        int l1 = b.declareLocal(Types.I32);
        int l2 = b.declareLocal(Types.I32);
        int l3 = b.declareLocal(Types.I32);
        b.nameLocal(l1, "x");
        b.nameLocal(l2, "x");
        b.nameLocal(l3, "float4");
        b.endFunction(b.block(
                b.localDecl(l1, b.param(1)), b.localDecl(l2, b.localGet(l1)), b.localDecl(l3, b.localGet(l2)),
                b.store(b.elemPtr(b.param(0), b.constI32(0)), b.localGet(l3))));
        String src = emit(b.build(), EmitOptions.DEBUG).source();
        assertTrue(src.contains("void u_get_work("), src);
        assertTrue(src.contains("u_int"), src);
        assertTrue(src.contains("int u_sin"), src);
        assertTrue(src.contains("int x = u_sin;"), src);
        assertTrue(src.contains("int x_1 = x;"), src);
        assertTrue(src.contains("int u_float4 = x_1;"), src);
    }

    // ---- форма текста -------------------------------------------------------------------------

    @Test
    void lazySelectOfBooleansPrintsAsShortCircuitOperators() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int x = b.declareLocal(Types.I32);
        b.nameLocal(x, "x");
        int cx = b.binary(Op.GT, b.localGet(x), b.constI32(0));
        int cy = b.binary(Op.LT, b.localGet(x), b.constI32(9));
        int and = b.select(cx, cy, b.constBool(false));
        int or = b.select(b.binary(Op.EQ, b.localGet(x), b.constI32(5)), b.constBool(true),
                b.binary(Op.EQ, b.localGet(x), b.constI32(6)));
        b.endFunction(b.block(b.localDecl(x, b.constI32(3)),
                b.store(b.elemPtr(b.param(0), b.constI32(0)), b.select(and, b.constI32(1), b.constI32(0))),
                b.store(b.elemPtr(b.param(0), b.constI32(1)), b.select(or, b.constI32(1), b.constI32(0)))));
        String src = emit(b.build(), EmitOptions.DEBUG).source();
        assertTrue(src.contains("((x > 0) && (x < 9)) ? 1 : 0"), src);
        assertTrue(src.contains("((x == 5) || (x == 6)) ? 1 : 0"), src);
    }

    @Test
    void complexLoopStepUsesLabelInsteadOfContinue() {
        String src = emit(HandModules.control(), EmitOptions.RELEASE).source();
        assertTrue(src.contains("goto cont_1;"));
        assertTrue(src.contains("cont_1:;"));
        assertTrue(src.contains("do {") && src.contains("} while ("), "do-while остаётся do-while");
        assertTrue(src.contains("} else if ("), "цепочка else if");
    }

    @Test
    void simpleLoopStepStaysInForHeader() {
        String src = emit(HandModules.deviceFunctions(), EmitOptions.DEBUG).source();
        assertTrue(src.contains("for (; i < a_len; i = (int)((uint)i + 1u)) {"), src);
        assertFalse(src.contains("goto"));
    }

    @Test
    void signedArithmeticIsDoneInUnsignedType() {
        String src = emit(HandModules.ops(Types.I32), EmitOptions.RELEASE).source();
        assertTrue(src.contains("p1[0] = (int)((uint)v0 + (uint)v1);"), src);
        assertTrue(src.contains("p1[39] = (int)(((uint)v0 + (uint)v1) * ((uint)v0 - (uint)v1));"), src);
        String wide = emit(HandModules.ops(Types.I64), EmitOptions.RELEASE).source();
        assertTrue(wide.contains("(long)((ulong)v0 + (ulong)v1)"), wide);
    }

    @Test
    void shiftCountsAreMasked() {
        String src = emit(HandModules.ops(Types.I32), EmitOptions.RELEASE).source();
        assertTrue(src.contains("(v1 & 31)"), src);
        assertTrue(src.contains("(uint)v0 << 0;") || src.contains("<< 0)"), "константа 32 -> 0");
        String wide = emit(HandModules.ops(Types.I64), EmitOptions.RELEASE).source();
        assertTrue(wide.contains("(v1 & 63)"), wide);
    }

    @Test
    void floatToIntUsesSaturatingHelpersAndNarrowsThroughInt() {
        String src = emit(HandModules.conversions(Types.F32), EmitOptions.RELEASE).source();
        assertTrue(src.contains("convert_int_sat"));
        assertTrue(src.contains("convert_ulong_sat"));
        assertTrue(src.contains("isnan(x) ? 0 :"));
        assertTrue(src.contains("(char)gpu_f32_to_i32("), "float -> byte: насыщение до int, затем сужение");
        assertTrue(src.contains("(ushort)gpu_f32_to_i32("));
    }

    @Test
    void floatMaxMinKeepJavaSemantics() {
        String src = emit(HandModules.ops(Types.F32), EmitOptions.RELEASE).source();
        assertTrue(src.contains("gpu_max_f32(v0, v1)"));
        assertFalse(src.contains("fmax"));
        assertTrue(src.contains("signbit(a)"));
        String ints = emit(HandModules.ops(Types.I32), EmitOptions.RELEASE).source();
        assertTrue(ints.contains("max(v0, v1)") && !ints.contains("gpu_max"));
    }

    @Test
    void helpersAreAbsentWhenUnused() {
        String src = emit(Fixtures.computeSine(), EmitOptions.RELEASE).source();
        assertFalse(src.contains("gpu_"));
        assertFalse(src.contains("static inline"));
    }

    @Test
    void privateArraysAreZeroInitialisedAtTheDeclaration() {
        String src = emit(HandModules.deviceFunctions(), EmitOptions.DEBUG).source();
        assertTrue(src.contains("int scratch[5] = {0};"), src);
        assertTrue(src.contains("fill(scratch, 5, 7);"), src);
    }

    // ---- ошибки значениями --------------------------------------------------------------------

    @Test
    void nullArgumentsAreProgrammingErrors() {
        IrModule m = Fixtures.computeSine();
        assertThrows(IllegalArgumentException.class, () -> OpenClEmitter.emit(null, Target.GENERIC));
        assertThrows(IllegalArgumentException.class, () -> OpenClEmitter.emit(m, null));
        assertThrows(IllegalArgumentException.class, () -> OpenClEmitter.emit(m, 0, Target.GENERIC, null));
    }

    @Test
    void missingFunctionIsReported() {
        IrModule m = Fixtures.computeSine();
        assertEquals("CG-A00", code(OpenClEmitter.emit(m, -1, Target.GENERIC, EmitOptions.RELEASE)));
        assertEquals("CG-A00", code(OpenClEmitter.emit(m, 5, Target.GENERIC, EmitOptions.RELEASE)));
    }

    @Test
    void deviceFunctionIsNotAKernel() {
        IrModule m = HandModules.deviceFunctions();
        assertEquals("CG-A01", code(OpenClEmitter.emit(m, 0, Target.GENERIC, EmitOptions.RELEASE)));
    }

    @Test
    void invalidModuleReturnsValidatorDiagnostics() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.I32); // ядро обязано быть void: IR-C02
        b.endFunction(b.block(b.returnStmt(b.constI32(1))));
        Result<Artifact> r = OpenClEmitter.emit(b.build(), Target.GENERIC);
        assertFalse(r.isOk());
        assertTrue(r.diagnostics().stream().allMatch(d -> d.code().startsWith("IR-")), r.diagnostics().toString());
    }

    @Test
    void unsupportedConstructsAreReportedBeforeAnyTextIsProduced() {
        IrModule rich = Fixtures.rich();
        Result<Artifact> r = OpenClEmitter.emit(rich, Target.GENERIC);
        assertFalse(r.isOk());
        assertTrue(r.diagnostics().stream().allMatch(d -> d.code().equals("CG-U01")), r.diagnostics().toString());
        assertTrue(r.diagnostics().size() > 3, "все нарушения за один проход");
    }

    @Test
    void barrierAndLocalMemoryAreMilestoneM10() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int la = b.declareLocal(b.array(AddressSpace.LOCAL, Types.I32));
        b.endFunction(b.block(b.localArrayDecl(la, b.constI32(8)), b.barrier(1)));
        Result<Artifact> r = OpenClEmitter.emit(b.build(), Target.GENERIC);
        assertEquals("CG-U01", code(r));
        String all = r.diagnostics().toString();
        assertTrue(all.contains("M10"), all);
    }

    @Test
    void vectorsAreMilestoneM8() {
        IrBuilder b = new IrBuilder();
        int v4 = b.vec(Types.F32, 4);
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int v = b.declareLocal(v4);
        b.endFunction(b.block(b.localDecl(v, b.splat(v4, b.constF32(1f))),
                b.store(b.elemPtr(b.param(0), b.constI32(0)), b.extract(b.localGet(v), 0))));
        Result<Artifact> r = OpenClEmitter.emit(b.build(), Target.GENERIC);
        assertEquals("CG-U01", code(r));
        assertTrue(r.diagnostics().toString().contains("M8"), r.diagnostics().toString());
    }

    @Test
    void localArrayParameterOfKernelIsMilestoneM10() {
        IrBuilder b = new IrBuilder();
        int la = b.array(AddressSpace.LOCAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, la);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constF32(1f))));
        Result<Artifact> r = OpenClEmitter.emit(b.build(), Target.GENERIC);
        assertEquals("CG-U01", code(r));
        assertTrue(r.diagnostics().toString().contains("M10"), r.diagnostics().toString());
    }

    @Test
    void specConstIsNotSupported() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.specConst(0, Types.I32))));
        assertEquals("CG-U01", code(OpenClEmitter.emit(b.build(), Target.GENERIC)));
    }

    @Test
    void unsupportedCodeInUnreachableFunctionIsIgnored() {
        IrBuilder b = new IrBuilder();
        int v4 = b.vec(Types.F32, 4);
        b.beginFunction(IrModule.DEVICE, v4);
        b.endFunction(b.block(b.returnStmt(b.splat(v4, b.constF32(1f)))));
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constI32(1))));
        assertNotNull(emit(b.build(), EmitOptions.RELEASE));
    }

    @Test
    void everyBuiltinIsEmitted() {
        IrModule m = HandModules.builtins();
        String src = emit(m, EmitOptions.RELEASE).source();
        for (String name : new String[]{"get_global_id", "get_local_id", "get_group_id", "get_global_size",
                "get_local_size", "get_num_groups", "get_work_dim", "sin(", "cos(", "sqrt(", "fma(", "max(", "min("}) {
            assertTrue(src.contains(name), name);
        }
        assertTrue(Builtins.count() >= 15);
    }
}
