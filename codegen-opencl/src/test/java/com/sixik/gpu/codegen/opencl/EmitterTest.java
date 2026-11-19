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
        assertFalse(r.isOk(), "an error was expected");
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
                .contains("Sine.java"), "no names in release");
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
                assertTrue(c < 128, k.label() + ": non-ASCII character " + (int) c);
            }
        }
    }

    // ---- математика float ---------------------------------------------------------------------

    @Test
    void floatIsAlwaysStrictForTheDriver() {
        // даже ядро с fp=relaxed: неточные преобразования делает оптимизатор явным кодом, драйверу их не доверяем
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.fpMode(IrModule.FP_ALL);
        int p = b.elemPtr(b.param(0), b.constI32(0));
        b.endFunction(b.block(b.store(p, b.binary(Op.ADD, b.binary(Op.MUL, b.load(p), b.load(p)), b.constF32(1f)))));
        for (IrModule m : new IrModule[]{Fixtures.computeSine(), b.build()}) {
            for (EmitOptions o : new EmitOptions[]{EmitOptions.RELEASE, EmitOptions.DEBUG}) {
                Artifact a = emit(m, o);
                assertTrue(a.source().contains("#pragma OPENCL FP_CONTRACT OFF"), a.source());
                assertEquals("", a.buildOptions());
            }
        }
    }

    /** Ядро {@code f[0] = sin(f[0]) + cos(f[1]) + sqrt(f[2]); d[0] = sin(d[0])} с маской {@code fp}. */
    private static IrModule mathKernel(int fp) {
        IrBuilder b = new IrBuilder();
        int fa = b.array(AddressSpace.GLOBAL, Types.F32);
        int da = b.array(AddressSpace.GLOBAL, Types.F64);
        b.beginFunction(IrModule.KERNEL, Types.VOID, fa, da);
        b.fpMode(fp);
        int[] x = new int[3];
        for (int k = 0; k < 3; k++) {
            x[k] = b.load(b.elemPtr(b.param(0), b.constI32(k)));
        }
        int f = b.binary(Op.ADD, b.binary(Op.ADD, b.intrinsic(com.sixik.gpu.ir.Builtins.idOf("sin"), x[0]),
                b.intrinsic(com.sixik.gpu.ir.Builtins.idOf("cos"), x[1])),
                b.intrinsic(com.sixik.gpu.ir.Builtins.idOf("sqrt"), x[2]));
        int d = b.intrinsic(com.sixik.gpu.ir.Builtins.idOf("sin"), b.load(b.elemPtr(b.param(1), b.constI32(0))));
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), f),
                b.store(b.elemPtr(b.param(1), b.constI32(0)), d)));
        return b.build();
    }

    private static String emitMath(int fp, int nativeMath) {
        IrModule m = mathKernel(fp);
        return OpenClEmitter.emit(m, m.functionCount() - 1, Target.GENERIC.withNativeMath(nativeMath),
                EmitOptions.RELEASE).value().source();
    }

    @Test
    void nativeFunctionsNeedTheKernelFlagAndTheTarget() {
        String all = emitMath(IrModule.FP_NATIVE, Target.NATIVE_ALL);
        assertTrue(all.contains("native_sin(") && all.contains("native_cos(") && all.contains("native_sqrt("), all);
        assertTrue(all.contains("= sin("), "double keeps the exact sin: " + all);

        String partial = emitMath(IrModule.FP_NATIVE, Target.NATIVE_COS);
        assertTrue(partial.contains("native_cos("), partial);
        assertFalse(partial.contains("native_sin(") || partial.contains("native_sqrt("), partial);

        assertFalse(emitMath(IrModule.FP_ALL & ~IrModule.FP_NATIVE, Target.NATIVE_ALL).contains("native_"),
                "a kernel without fp=native stays exact");
        assertFalse(emitMath(IrModule.FP_NATIVE, 0).contains("native_"), "nothing calibrated on the target");
    }

    /** Ядро {@code a[0] = a[0] / a[1]} над элементами {@code elem} с маской {@code fp}. */
    private static IrModule divKernel(int elem, int fp) {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, elem);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.fpMode(fp);
        int x = b.load(b.elemPtr(b.param(0), b.constI32(0)));
        int y = b.load(b.elemPtr(b.param(0), b.constI32(1)));
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.binary(Op.DIV, x, y))));
        return b.build();
    }

    private static String options(IrModule m, boolean exact) {
        return OpenClEmitter.emit(m, m.functionCount() - 1, Target.GENERIC.withExactDivideSqrt(exact),
                EmitOptions.RELEASE).value().buildOptions();
    }

    @Test
    void strictFloatDivisionIsCorrectlyRoundedWhereTheDeviceCan() {
        assertEquals(Target.EXACT_DIVIDE_SQRT_OPTION, options(divKernel(Types.F32, 0), true));
        assertEquals(Target.EXACT_DIVIDE_SQRT_OPTION, options(divKernel(Types.F32, IrModule.FP_CONTRACT), true),
                "contract does not allow an approximate division");
        assertEquals(Target.EXACT_DIVIDE_SQRT_OPTION, options(mathKernel(0), true), "sqrt(float)");
        assertEquals("", options(divKernel(Types.F32, 0), false), "the device cannot");
        assertEquals("", options(divKernel(Types.F32, IrModule.FP_RECIPROCAL), true), "fp=reciprocal");
        assertEquals("", options(divKernel(Types.F32, IrModule.FP_NATIVE), true), "fp=native");
        assertEquals("", options(divKernel(Types.I32, 0), true), "integer division");
        assertEquals("", options(divKernel(Types.F64, 0), true), "double division is always correctly rounded");
        assertEquals("", options(Fixtures.computeSine(), true), "no division or sqrt");
    }

    /** Ядро {@code a[0] = max(a[0], a[1]) + min(a[0], a[1]) + 1/sqrt(a[2])} с маской {@code fp}. */
    private static String fastKernel(int elem, int fp) {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, elem);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.fpMode(fp);
        int[] l = new int[5];
        for (int k = 0; k < l.length; k++) {
            l[k] = b.load(b.elemPtr(b.param(0), b.constI32(k)));
        }
        int one = elem == Types.F32 ? b.constF32(1f) : b.constF64(1d);
        int v = b.binary(Op.ADD, b.binary(Op.ADD, b.intrinsic(Builtins.idOf("max"), l[0], l[1]),
                b.intrinsic(Builtins.idOf("min"), l[2], l[3])),
                b.binary(Op.DIV, one, b.intrinsic(Builtins.idOf("sqrt"), l[4])));
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), v)));
        IrModule m = b.build();
        Result<Artifact> r = OpenClEmitter.emit(m, Target.GENERIC);
        assertTrue(r.isOk(), r.diagnostics().toString());
        return r.value().source();
    }

    @Test
    void finiteAndNszUseHardwareMinMax() {
        int both = IrModule.FP_FINITE | IrModule.FP_NO_SIGNED_ZERO;
        for (int elem : new int[]{Types.F32, Types.F64}) {
            String fast = fastKernel(elem, both);
            assertTrue(fast.contains("fmax(") && fast.contains("fmin("), fast);
            assertFalse(fast.contains("gpu_max_") || fast.contains("gpu_min_"), fast);
            for (int fp : new int[]{0, IrModule.FP_FINITE, IrModule.FP_NO_SIGNED_ZERO}) {
                String strict = fastKernel(elem, fp);
                assertTrue(strict.contains("gpu_max_") && !strict.contains("fmax("),
                        "NaN or -0.0 still matter with fp=" + fp + ": " + strict);
            }
        }
    }

    @Test
    void reciprocalUsesRsqrt() {
        for (int elem : new int[]{Types.F32, Types.F64}) {
            assertTrue(fastKernel(elem, IrModule.FP_RECIPROCAL).contains("rsqrt("));
            assertFalse(fastKernel(elem, IrModule.FP_ALL & ~IrModule.FP_RECIPROCAL).contains("rsqrt("));
        }
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
        assertEquals(6, args.size(), "int, long, float, double and an array with length");
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
                "the called function is printed before the caller");
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
        assertTrue(src.contains("do {") && src.contains("} while ("), "do-while stays do-while");
        assertTrue(src.contains("} else if ("), "else if chain");
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
        assertTrue(src.contains("(uint)v0 << 0;") || src.contains("<< 0)"), "constant 32 -> 0");
        String wide = emit(HandModules.ops(Types.I64), EmitOptions.RELEASE).source();
        assertTrue(wide.contains("(v1 & 63)"), wide);
    }

    @Test
    void floatToIntUsesSaturatingHelpersAndNarrowsThroughInt() {
        String src = emit(HandModules.conversions(Types.F32), EmitOptions.RELEASE).source();
        assertTrue(src.contains("convert_int_sat"));
        assertTrue(src.contains("convert_ulong_sat"));
        assertTrue(src.contains("isnan(x) ? 0 :"));
        assertTrue(src.contains("(char)gpu_f32_to_i32("), "float -> byte: saturation to int, then narrowing");
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

    /** Ядро с тремя вещами вне v1: указатель-параметр, {@code __local}-параметр и {@code spec_const}. */
    private static IrModule outsideV1() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID, b.array(AddressSpace.GLOBAL, Types.I32),
                b.ptr(AddressSpace.GLOBAL, Types.I32), b.array(AddressSpace.LOCAL, Types.I32));
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.specConst(0, Types.I32))));
        return b.build();
    }

    @Test
    void unsupportedConstructsAreReportedBeforeAnyTextIsProduced() {
        Result<Artifact> r = OpenClEmitter.emit(outsideV1(), Target.GENERIC);
        assertFalse(r.isOk());
        assertTrue(r.diagnostics().stream().allMatch(d -> d.code().equals("CG-U01")), r.diagnostics().toString());
        assertTrue(r.diagnostics().size() >= 3, "all violations in one pass: " + r.diagnostics());
        assertTrue(r.diagnostics().toString().contains("outside v1"), r.diagnostics().toString());
    }

    @Test
    void richModuleWithLocalArrayBarrierAndPointersIsEmitted() {
        Result<Artifact> r = OpenClEmitter.emit(Fixtures.rich(), Target.GENERIC);
        assertTrue(r.isOk(), r.diagnostics().toString());
        assertTrue(r.value().source().contains("__local "), r.value().source());
    }

    @Test
    void localArrayAndBarrierAreEmittedAtKernelLevel() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.requireWorkGroup(8, 1, 1);
        int la = b.declareLocal(b.array(AddressSpace.LOCAL, Types.I32));
        b.nameLocal(la, "tile");
        b.endFunction(b.block(b.localArrayDecl(la, b.constI32(8)), b.barrier(1), b.barrier(2), b.barrier(3),
                b.store(b.elemPtr(b.localGet(la), b.constI32(0)), b.constI32(1)),
                b.store(b.elemPtr(b.param(0), b.constI32(0)), b.arrayLen(b.localGet(la)))));
        Result<Artifact> r = OpenClEmitter.emit(b.build(), 0, Target.GENERIC, EmitOptions.DEBUG);
        assertTrue(r.isOk(), r.diagnostics().toString());
        String src = r.value().source();
        assertTrue(src.contains("    __local int tile[8];"), src);
        assertTrue(src.contains("barrier(CLK_LOCAL_MEM_FENCE);"), src);
        assertTrue(src.contains("barrier(CLK_GLOBAL_MEM_FENCE);"), src);
        assertTrue(src.contains("barrier(CLK_LOCAL_MEM_FENCE | CLK_GLOBAL_MEM_FENCE);"), src);
        assertTrue(src.contains("[0] = 8;"), "local array length is a constant: " + src);
    }

    @Test
    void atomicFamilyUsesAtomicPrefixFor32BitAndAtomForWiderTypes() {
        IrBuilder b = new IrBuilder();
        int i32 = b.array(AddressSpace.GLOBAL, Types.I32);
        int i64 = b.array(AddressSpace.GLOBAL, Types.I64);
        b.beginFunction(IrModule.KERNEL, Types.VOID, i32, i64);
        String[] ops = {"atomicAdd", "atomicSub", "atomicMin", "atomicMax", "atomicAnd", "atomicOr", "atomicXor", "atomicXchg"};
        int[] st = new int[ops.length * 2];
        for (int i = 0; i < ops.length; i++) {
            st[2 * i] = b.exprStmt(b.intrinsic(com.sixik.gpu.ir.Builtins.idOf(ops[i]), b.elemPtr(b.param(0), b.constI32(0)), b.constI32(1)));
            st[2 * i + 1] = b.exprStmt(b.intrinsic(com.sixik.gpu.ir.Builtins.idOf(ops[i]), b.elemPtr(b.param(1), b.constI32(0)), b.constI64(1)));
        }
        b.endFunction(b.block(st));
        Result<Artifact> r = OpenClEmitter.emit(b.build(), Target.GENERIC);
        assertTrue(r.isOk(), r.diagnostics().toString());
        String src = r.value().source();
        for (String op : new String[]{"add", "sub", "min", "max", "and", "or", "xor", "xchg"}) {
            assertTrue(src.contains("atomic_" + op + "(&"), op + "\n" + src);
            assertTrue(src.contains("atom_" + op + "(&"), op + "\n" + src);
        }
        assertTrue(src.contains("#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable"), src);
        assertTrue(src.contains("#pragma OPENCL EXTENSION cl_khr_int64_extended_atomics : enable"), src);
    }

    /** {@code atomicCmpxchg} - три аргумента; 64-битный - из базового набора, расширенные атомики не нужны. */
    @Test
    void compareExchangeTakesThreeArguments() {
        IrBuilder b = new IrBuilder();
        int i32 = b.array(AddressSpace.GLOBAL, Types.I32);
        int i64 = b.array(AddressSpace.GLOBAL, Types.I64);
        b.beginFunction(IrModule.KERNEL, Types.VOID, i32, i64);
        int cas = com.sixik.gpu.ir.Builtins.idOf("atomicCmpxchg");
        b.endFunction(b.block(
                b.exprStmt(b.intrinsic(cas, b.elemPtr(b.param(0), b.constI32(0)), b.constI32(0), b.constI32(1))),
                b.exprStmt(b.intrinsic(cas, b.elemPtr(b.param(1), b.constI32(0)), b.constI64(2), b.constI64(3)))));
        Result<Artifact> r = OpenClEmitter.emit(b.build(), Target.GENERIC);
        assertTrue(r.isOk(), r.diagnostics().toString());
        String src = r.value().source();
        assertTrue(src.contains("atomic_cmpxchg(&"), src);
        assertTrue(src.contains("atom_cmpxchg(&"), src);
        assertTrue(src.contains(", 0, 1)"), src);
        assertTrue(src.contains("cl_khr_int64_base_atomics"), src);
        assertFalse(src.contains("cl_khr_int64_extended_atomics"), src);
    }

    @Test
    void baseAtomicsOf64BitsDoNotRequireTheExtendedExtension() {
        IrBuilder b = new IrBuilder();
        int i64 = b.array(AddressSpace.GLOBAL, Types.I64);
        b.beginFunction(IrModule.KERNEL, Types.VOID, i64);
        b.endFunction(b.block(b.exprStmt(b.intrinsic(com.sixik.gpu.ir.Builtins.idOf("atomicAdd"),
                b.elemPtr(b.param(0), b.constI32(0)), b.constI64(1)))));
        Result<Artifact> r = OpenClEmitter.emit(b.build(), Target.GENERIC);
        assertTrue(r.isOk(), r.diagnostics().toString());
        assertTrue(r.value().source().contains("cl_khr_int64_base_atomics"));
        assertFalse(r.value().source().contains("cl_khr_int64_extended_atomics"));
    }

    @Test
    void pointerToLocalIsAddressOfAndAccessThroughItNamesTheLocalDirectly() {
        IrBuilder b = new IrBuilder();
        int pf = b.ptr(AddressSpace.PRIVATE, Types.F32);
        int inc = b.beginFunction(IrModule.DEVICE, Types.VOID, pf);
        b.nameFunction("inc");
        b.nameParams("p");
        b.endFunction(b.block(b.store(b.param(0), b.binary(Op.ADD, b.load(b.param(0)), b.constF32(1)))));
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int l = b.declareLocal(Types.F32);
        b.nameLocal(l, "x");
        b.endFunction(b.block(b.localDecl(l, b.constF32(1f)), b.exprStmt(b.call(inc, b.localAddr(l))),
                b.store(b.elemPtr(b.param(0), b.constI32(0)), b.load(b.localAddr(l)))));
        Result<Artifact> r = OpenClEmitter.emit(b.build(), 1, Target.GENERIC, EmitOptions.DEBUG);
        assertTrue(r.isOk(), r.diagnostics().toString());
        String src = r.value().source();
        assertTrue(src.contains("void inc(__private float* p)"), src);
        assertTrue(src.contains("*p = *p + 1.0f;"), src);
        assertTrue(src.contains("inc(&x);"), src);
        assertTrue(src.contains("[0] = x;"), "load(local_addr(x)) is printed as x, without '*&': " + src);
    }

    @Test
    void fieldPointerOfDereferencedPointerIsParenthesized() {
        IrBuilder b = new IrBuilder();
        int s = b.struct(new int[]{Types.F32, Types.I32}, 0);
        b.nameStruct(s, "Cell", "w", "tag");
        int ps = b.ptr(AddressSpace.GLOBAL, s);
        int set = b.beginFunction(IrModule.DEVICE, Types.VOID, ps);
        b.nameFunction("set");
        b.nameParams("c");
        b.endFunction(b.block(b.store(b.fieldPtr(b.param(0), 1), b.constI32(5))));
        int arr = b.array(AddressSpace.GLOBAL, s);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.endFunction(b.block(b.exprStmt(b.call(set, b.elemPtr(b.param(0), b.constI32(2)))),
                b.store(b.fieldPtr(b.elemPtr(b.param(0), b.constI32(1)), 0), b.constF32(2))));
        Result<Artifact> r = OpenClEmitter.emit(b.build(), 1, Target.GENERIC, EmitOptions.DEBUG);
        assertTrue(r.isOk(), r.diagnostics().toString());
        String src = r.value().source();
        assertTrue(src.contains("(*c).tag = 5;"), "*c.tag would mean *(c.tag): " + src);
        assertTrue(src.contains("[1].w = 2.0f;"), src);
    }

    @Test
    void pointerInKernelParametersIsNotSupported() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID, b.ptr(AddressSpace.GLOBAL, Types.F32));
        b.endFunction(b.block());
        Result<Artifact> r = OpenClEmitter.emit(b.build(), Target.GENERIC);
        assertEquals("CG-U01", code(r));
        assertTrue(r.diagnostics().toString().contains("use an array"), r.diagnostics().toString());
    }

    @Test
    void vectorsOfSmallElementsAreNotSupported() {
        IrBuilder b = new IrBuilder();
        int v = b.vec(Types.I8, 4);
        int arr = b.array(AddressSpace.GLOBAL, Types.I8);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int l = b.declareLocal(v);
        b.endFunction(b.block(b.localDecl(l, b.splat(v, b.constInt(Types.I8, 1)))));
        assertEquals("CG-U01", code(OpenClEmitter.emit(b.build(), Target.GENERIC)));
    }

    @Test
    void vectorsAndStructsAreEmittedAsOpenClTypes() {
        String vec = emit(HandModules.vectorsF32(), EmitOptions.RELEASE).source();
        assertTrue(vec.contains("float4 v0 = (float4)(p0[0], p0[1], p0[2], p0[3]);"), vec);
        assertTrue(vec.contains(".wzyx"), "swizzle with letters for float4");
        assertTrue(vec.contains(".s00112233"), "s-notation for an 8-lane result");
        assertTrue(vec.contains("gpu_set_float4_1("), "lane replacement is a function");
        assertTrue(vec.contains("gpu_max_float4("), "max/min of float vectors with Java semantics");
        assertTrue(vec.contains("gpu_conv_float4_int4("), "float -> int on a vector is saturating, with NaN -> 0");
        assertTrue(vec.contains("as_int4("), "vector bitcast");

        String ints = emit(HandModules.vectorsI32(), EmitOptions.RELEASE).source();
        assertTrue(ints.contains("as_int4(as_uint4("), "signed vector arithmetic in an unsigned type: " + ints);
        assertTrue(ints.contains("gpu_div_int4(") && ints.contains("gpu_rem_long2("), "division is lane-wise via a function");

        String structs = emit(HandModules.structs(), EmitOptions.DEBUG).source();
        assertTrue(structs.contains("typedef struct {\n    float x;\n    float y;\n    float4 v;\n    int id;\n} Particle;"), structs);
        assertTrue(structs.indexOf("} Particle;") < structs.indexOf("} Body;"), "the nested type is defined earlier");
        assertTrue(structs.contains("Particle ps[3];"), "array of structs is declared without {0}");
        assertTrue(structs.contains("gpu_zero_Particle()"), structs);
    }

    @Test
    void aggregateKernelArgumentsKeepTheirIrType() {
        IrBuilder b = new IrBuilder();
        int v4 = b.vec(Types.F32, 4);
        int arr = b.array(AddressSpace.GLOBAL, v4);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, v4);
        b.nameParams("items", "scale");
        int g = b.declareLocal(Types.I32);
        b.endFunction(b.block(
                b.localDecl(g, b.intrinsic(Builtins.idOf("getGlobalId"), b.constI32(0))),
                b.store(b.elemPtr(b.param(0), b.localGet(g)), b.binary(Op.MUL, b.load(b.elemPtr(b.param(0), b.localGet(g))), b.param(1)))));
        Artifact a = emit(b.build(), EmitOptions.DEBUG);
        assertEquals(KernelArg.ARRAY_PTR, a.args().get(0).kind());
        assertEquals(v4, a.args().get(0).type(), "array element is a vector");
        assertEquals(KernelArg.SCALAR, a.args().get(2).kind());
        assertEquals(v4, a.args().get(2).type(), "vector by value");
        assertTrue(a.source().contains("__global float4* items, int items_len, float4 scale"), a.source());
    }

    @Test
    void localArrayParameterOfKernelIsOutsideV1() {
        IrBuilder b = new IrBuilder();
        int la = b.array(AddressSpace.LOCAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, la);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constF32(1f))));
        Result<Artifact> r = OpenClEmitter.emit(b.build(), Target.GENERIC);
        assertEquals("CG-U01", code(r));
        assertTrue(r.diagnostics().toString().contains("outside v1"), r.diagnostics().toString());
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

    @Test
    void inlineAndOptLevelAreInformativeAttributesOnly() {
        // оптимизатора нет: политика inline и уровень оптимизации хранятся в IR (и меняют хэш), но не влияют на текст OpenCL C
        java.util.function.IntFunction<IrModule> build = variant -> {
            IrBuilder b = new IrBuilder();
            int arr = b.array(AddressSpace.GLOBAL, Types.I32);
            int helper = b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32);
            if (variant == 1) {
                b.inlinePolicy(IrModule.INLINE_ALWAYS);
                b.optLevel(3);
            }
            b.endFunction(b.block(b.returnStmt(b.binary(Op.ADD, b.param(0), b.constI32(1)))));
            b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
            if (variant == 1) {
                b.inlinePolicy(IrModule.INLINE_NEVER);
                b.optLevel(2);
            }
            b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.call(helper, b.constI32(1)))));
            return b.build();
        };
        IrModule plain = build.apply(0);
        IrModule tuned = build.apply(1);
        assertEquals(OpenClEmitter.emit(plain, Target.GENERIC).value().source(),
                OpenClEmitter.emit(tuned, Target.GENERIC).value().source());
        assertFalse(java.util.Arrays.equals(com.sixik.gpu.ir.IrBinary.write(plain), com.sixik.gpu.ir.IrBinary.write(tuned)),
                "attributes stay in the IR and are part of the hash");
        assertEquals(IrModule.INLINE_ALWAYS, tuned.fInline(0));
        assertEquals(3, tuned.fOpt(0));
    }

    @Test
    void codegenVersionIsPartOfThePublicContract() {
        assertFalse(OpenClEmitter.VERSION.isBlank());
    }

    // ---- глубокие выражения ------------------------------------------------------------------

    /** Наибольшая вложенность скобок {@code ( [ {} в тексте. */
    private static int bracketDepth(String src) {
        int depth = 0;
        int max = 0;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                max = Math.max(max, ++depth);
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            }
        }
        return max;
    }

    @Test
    void deepExpressionsStayWithinTheCompilerBracketLimit() {
        for (EmitOptions o : new EmitOptions[]{EmitOptions.RELEASE, EmitOptions.DEBUG}) {
            String src = emit(HandModules.deep(400), o).source();
            assertTrue(bracketDepth(src) < 128, "bracket depth " + bracketDepth(src) + " in\n" + src);
        }
    }

    @Test
    void shallowExpressionsAreNotSplit() {
        String src = emit(HandModules.deep(3), EmitOptions.RELEASE).source();
        assertFalse(src.contains(" e1"), src);
        assertTrue(src.contains("for (;"), "a loop with a shallow condition keeps its form:\n" + src);
    }

    @Test
    void deepLazyBranchesBecomeIfElseAndDeepConditionsMoveIntoTheLoop() {
        String src = emit(HandModules.deep(100), EmitOptions.RELEASE).source();
        // ветка select с выносами вычисляется только под своим условием
        assertTrue(java.util.regex.Pattern.compile("(?m)^\\s+if \\(.*< p0_len\\) \\{$").matcher(src).find(), src);
        assertTrue(java.util.regex.Pattern.compile("(?m)^\\s+e\\d+ = ").matcher(src).find(), src);
        // условия циклов проверяются на каждой итерации
        assertTrue(src.contains("while (1) {"), src);
        assertTrue(src.contains("} while (1);"), src);
        assertTrue(java.util.regex.Pattern.compile("if \\(!\\(.*\\)\\) break;").matcher(src).find(), src);
        // глубокое второе условие не поднимается в else if
        assertFalse(src.contains("} else if ("), src);
    }
}
