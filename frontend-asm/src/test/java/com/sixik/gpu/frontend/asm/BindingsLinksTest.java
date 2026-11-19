package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.optimizer.OptimizeOptions;
import com.sixik.gpu.ir.optimizer.OptimizeResult;
import com.sixik.gpu.ir.optimizer.Optimizer;
import com.sixik.gpu.tools.IrDump;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Шаг B3 (FRONTEND_ASM_RUNTIME.md, 18.7): привязки полей ({@link Bindings}, 18.2-S3) и связи заглушек
 * {@code @Extern} ({@link Links}, 18.2-S2). Библиотека - class-файлы из {@link ClassSource}, роутер - сгенерированный
 * класс без аннотаций.
 */
class BindingsLinksTest {

    private static final String FLAGS = """
            package ga2;

            public class Flags {
                public static boolean AQUIFERS = false;
                public static int SCALE = 1;
                public static String NAME = "overworld";
            }
            """;

    private static final String LINKS = """
            package ga2;

            import com.sixik.gpu.api.annotations.DeviceFunction;
            import com.sixik.gpu.api.annotations.Extern;

            public class RouterLinks {
                @DeviceFunction
                @Extern
                public static int erosion(int x) {
                    throw new IllegalStateException("linked at runtime");
                }
            }
            """;

    private static final String LIB = """
            package ga2;

            import com.sixik.gpu.api.annotations.DeviceFunction;

            public class Lib {
                @DeviceFunction
                public static int twice(int x) {
                    return x * 2;
                }

                /** Библиотека вызывает сгенерированный код через заглушку. */
                @DeviceFunction
                public static int aquifer(int x) {
                    return RouterLinks.erosion(x) + 1;
                }
            }
            """;

    /** Сгенерированный роутер: без аннотаций, вызывает библиотеку и читает флаги. */
    private static final String ROUTER = """
            package gen;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.Global;
            import ga2.Flags;
            import ga2.Lib;

            public class Router {
                public static int ap_erosion(int x) {
                    return x * 10;
                }

                public static int ap_depth(int x, int y) {
                    return x - y;
                }

                public static void k(@Global int[] a) {
                    int i = Gpu.getGlobalId(0);
                    int v = a[i];
                    if (Flags.AQUIFERS) {
                        v = Lib.aquifer(v);
                    }
                    a[i] = Lib.twice(v) * Flags.SCALE;
                }
            }
            """;

    private static Map<String, byte[]> library;
    private static ClassNode router;

    private static synchronized void init() {
        if (library != null) {
            return;
        }
        JavacHarness.Outcome o = JavacHarness.compile(false, null, FLAGS, LINKS, LIB, ROUTER);
        assertTrue(o.success(), o.log());
        library = new HashMap<>(o.classes());
        byte[] r = library.remove("gen/Router");
        router = new ClassNode();
        new ClassReader(r).accept(router, 0);
    }

    private static final Links ERO = Links.none().redirect("ga2/RouterLinks", "erosion", "(I)I",
            "gen/Router", "ap_erosion", "(I)I");

    private static AsmJit.Result jit(Bindings b, Links l) {
        init();
        ClassSource src = ClassSource.chain(ClassSource.of(library), ClassSource.of(BindingsLinksTest.class.getClassLoader()));
        return AsmJit.compile(new AsmJit.Unit(src, Map.of("gen/Router", router),
                List.of(AsmJit.Entry.of("gen/Router", "k", "([I)V")), b, l), AsmJit.Options.DEFAULT);
    }

    private static Bindings flags(boolean aquifers, int scale) {
        return Bindings.none().constant("ga2/Flags", "AQUIFERS", aquifers).constant("ga2/Flags", "SCALE", scale);
    }

    private static int run(IrModule m, int x) {
        int[] a = {x};
        assertEquals("ok", CorpusTest.outcome(m, NdRange.of1D(1), new Object[]{a}));
        return a[0];
    }

    private static IrModule module(AsmJit.Result r) {
        assertTrue(r.ok(), r.diagnostics().toString());
        return r.kernels().get(0).module();
    }

    private static String optimized(IrModule m) {
        OptimizeResult r = Optimizer.optimize(m, OptimizeOptions.DEFAULT);
        assertTrue(r.ok(), r.failures().toString());
        return IrDump.dump(r.module(), false);
    }

    private static List<String> codes(AsmJit.Result r) {
        return r.diagnostics().stream().map(AsmDiagnostic::code).toList();
    }

    /** Флаг - константа IR: ветка исполняется по значению привязки, а после оптимизатора выключенной ветки нет. */
    @Test
    void boundFlagFoldsTheBranch() {
        IrModule off = module(jit(flags(false, 3), ERO));
        IrModule on = module(jit(flags(true, 3), ERO));
        assertEquals(30, run(off, 5));            // twice(5) * 3
        assertEquals(306, run(on, 5));            // twice(erosion(5) + 1) * 3 = twice(51) * 3
        assertNotEquals(IrDump.dump(off, false), IrDump.dump(on, false), "the binding is part of the IR");
        String o = optimized(off);
        assertFalse(o.contains(" if "), o);
        assertTrue(IrDump.dump(off, false).contains(" if "), "before the optimizer the branch is there");
        assertEquals(30, run(Optimizer.optimize(off, OptimizeOptions.DEFAULT).module(), 5));
    }

    /** Связь в обе стороны: роутер вызывает библиотеку, библиотека - роутер через {@code @Extern}. */
    @Test
    void libraryAndGeneratedCodeCallEachOther() {

        IrModule m = module(jit(flags(true, 1), ERO));
        assertEquals(2 * (7 * 10 + 1), run(m, 7));
    }

    /** Непривязанный {@code getstatic} - по-прежнему {@code GPU0115}. */
    @Test
    void unboundStaticFieldIsRejected() {
        AsmJit.Result r = jit(Bindings.none().constant("ga2/Flags", "SCALE", 2), ERO);
        assertEquals(List.of("GPU0115"), codes(r), r.diagnostics().toString());
        assertTrue(r.diagnostics().get(0).message().contains("AQUIFERS"), r.diagnostics().toString());
    }

    /** {@code GPU0308}: значение не того типа и поле не примитивного типа. */
    @Test
    void bindingOfAnotherTypeIsRejected() {
        AsmJit.Result r = jit(flags(false, 1).constant("ga2/Flags", "SCALE", 2.5f), ERO);
        assertEquals(List.of("GPU0308"), codes(r), r.diagnostics().toString());
        assertTrue(r.diagnostics().get(0).message().contains("int, the value is Float"), r.diagnostics().toString());
        assertEquals("only fields of primitive types and their arrays are bound, the field is java.lang.String",
                Bindings.problem("Ljava/lang/String;", "x"));
        assertEquals("the value 300 is out of range of byte", Bindings.problem("B", 300));
    }

    /** {@code GPU0309}: заглушка без связи и связь с другим дескриптором. */
    @Test
    void missingOrMismatchedLinksAreRejected() {
        AsmJit.Result none = jit(flags(true, 1), Links.none());
        assertEquals(List.of("GPU0309"), codes(none), none.diagnostics().toString());
        assertTrue(none.diagnostics().get(0).message().contains("no link"), none.diagnostics().toString());

        AsmJit.Result other = jit(flags(true, 1), Links.none().redirect("ga2/RouterLinks", "erosion", "(I)I",
                "gen/Router", "ap_depth", "(II)I"));
        assertEquals(List.of("GPU0309"), codes(other), other.diagnostics().toString());
        assertTrue(other.diagnostics().get(0).message().contains("another descriptor"), other.diagnostics().toString());
    }

    /** Ветка, выключенная привязкой, всё равно проверяется: связь нужна и при {@code AQUIFERS = false}. */
    @Test
    void disabledBranchIsStillChecked() {
        AsmJit.Result r = jit(flags(false, 1), Links.none());
        assertEquals(List.of("GPU0309"), codes(r), r.diagnostics().toString());
    }

    /** Байткод {@code ClassWriter} без LVT: {@code getstatic} привязанной константы в сгенерированном ядре. */
    @Test
    void classWriterKernelReadsABinding() {
        init();
        ClassNode cn = new ClassNode();
        cn.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "gen/Cw", null, "java/lang/Object", null);
        MethodVisitor mv = cn.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "k", "([I)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitFieldInsn(Opcodes.GETSTATIC, "ga2/Flags", "SCALE", "I");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "ga2/Lib", "twice", "(I)I", false);
        mv.visitInsn(Opcodes.IASTORE);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(3, 1);
        mv.visitEnd();
        cn.visitEnd();
        ClassSource src = ClassSource.chain(ClassSource.of(library), ClassSource.of(BindingsLinksTest.class.getClassLoader()));
        AsmJit.Result r = AsmJit.compile(new AsmJit.Unit(src, Map.of("gen/Cw", cn),
                List.of(AsmJit.Entry.of("gen/Cw", "k", "([I)V")), flags(false, 21), null), AsmJit.Options.DEFAULT);
        assertEquals(42, run(module(r), 0));
    }

    /**
     * {@code frontend-java}: тело заглушки {@code @Extern} не проверяется (иначе {@code throw} дал бы
     * {@code GPU0101}), а вызов заглушки из ядра - {@code GPU0309}: этот фронтенд не связывает.
     */
    @Test
    void frontendJavaSkipsStubsAndRejectsTheirCalls() {
        JavacHarness.Outcome stub = JavacHarness.compile(true, null, LINKS);
        assertTrue(stub.success(), stub.log());
        assertEquals(List.of(), stub.gpuErrors());
        JavacHarness.Outcome call = JavacHarness.compile(true, null, LINKS, """
                package k;

                import com.sixik.gpu.api.Gpu;
                import com.sixik.gpu.api.annotations.Global;
                import com.sixik.gpu.api.annotations.Kernel;
                import ga2.RouterLinks;

                public class UsesStub {
                    @Kernel
                    public static void k(@Global int[] a) {
                        a[Gpu.getGlobalId(0)] = RouterLinks.erosion(1);
                    }
                }
                """);
        assertEquals(List.of("GPU0309"), call.gpuErrors(), call.log());
    }
}
