package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.codegen.opencl.ClProgram;
import com.sixik.gpu.codegen.opencl.EmitOptions;
import com.sixik.gpu.codegen.opencl.OpenClEmitter;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Пример из {@code docs/AGENTS.md} («Фронтенд байткода: полный пример»): генератор строит {@link ClassNode} без LVT,
 * привязывает флаг и таблицу, задаёт атрибуты ядра через {@link AsmJit.Entry}, получает IR и {@link ClProgram}.
 * Тест держит пример документации рабочим: меняя одно, меняйте и другое.
 */
class AgentGuideExampleTest {

    /**
     * Сгенерированный класс {@code gen/Router}, эквивалент на Java:
     * <pre>{@code
     * static float twice(float v) { return v * 2f; }
     * static void k(float[] out, float[] in) {
     *     int i = Gpu.getGlobalId(0);
     *     float x = in[i];
     *     if (Flags.SCALE) { x = x * Tables.W[i & 3]; }
     *     out[i] = twice(x);
     * }
     * }</pre>
     * Строки - идентификаторы узлов генератора (100, 101, ...), {@code SourceFile} - {@code router/demo}.
     */
    static ClassNode generate() {
        ClassNode cn = new ClassNode();
        cn.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "gen/Router", null, "java/lang/Object", null);
        cn.visitSource("router/demo", null);

        MethodVisitor tw = cn.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "twice", "(F)F", null, null);
        tw.visitCode();
        tw.visitVarInsn(Opcodes.FLOAD, 0);
        tw.visitInsn(Opcodes.FCONST_2);
        tw.visitInsn(Opcodes.FMUL);
        tw.visitInsn(Opcodes.FRETURN);
        tw.visitMaxs(2, 1);
        tw.visitEnd();

        MethodVisitor mv = cn.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "k", "([F[F)V", null, null);
        mv.visitCode();
        line(mv, 100);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/sixik/gpu/api/Gpu", "getGlobalId", "(I)I", false);
        mv.visitVarInsn(Opcodes.ISTORE, 2);                          // int i
        line(mv, 101);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitInsn(Opcodes.FALOAD);
        mv.visitVarInsn(Opcodes.FSTORE, 3);                          // float x
        line(mv, 102);
        Label skip = new Label();
        mv.visitFieldInsn(Opcodes.GETSTATIC, "gen/Flags", "SCALE", "Z");   // привязанный флаг
        mv.visitJumpInsn(Opcodes.IFEQ, skip);
        mv.visitVarInsn(Opcodes.FLOAD, 3);
        mv.visitFieldInsn(Opcodes.GETSTATIC, "gen/Tables", "W", "[F");     // привязанная таблица
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitInsn(Opcodes.ICONST_3);
        mv.visitInsn(Opcodes.IAND);
        mv.visitInsn(Opcodes.FALOAD);
        mv.visitInsn(Opcodes.FMUL);
        mv.visitVarInsn(Opcodes.FSTORE, 3);
        mv.visitLabel(skip);
        line(mv, 103);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitVarInsn(Opcodes.FLOAD, 3);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "gen/Router", "twice", "(F)F", false);
        mv.visitInsn(Opcodes.FASTORE);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(4, 4);
        mv.visitEnd();
        cn.visitEnd();
        return cn;
    }

    private static void line(MethodVisitor mv, int node) {
        Label l = new Label();
        mv.visitLabel(l);
        mv.visitLineNumber(node, l);
    }

    static AsmJit.Unit unit(Bindings bindings) {
        AsmJit.Entry entry = new AsmJit.Entry("gen/Router", "k", "([F[F)V",
                Map.of("compilationProperties", "opt=2"), Map.of("x", 64));
        return new AsmJit.Unit(ClassSource.of(AgentGuideExampleTest.class.getClassLoader()),
                Map.of("gen/Router", generate()), List.of(entry), bindings, Links.none());
    }

    static final AsmJit.Options OPTIONS = AsmJit.Options.DEFAULT.synthesizeLvt(true);

    @Test
    void exampleCompilesAndRuns() {
        Bindings bindings = Bindings.none()
                .constant("gen/Flags", "SCALE", true)
                .table("gen/Tables", "W", new float[]{0.5f, 1f, 2f, 4f});
        AsmJit.Unit unit = unit(bindings);
        AsmJit.Result jit = AsmJit.compile(unit, OPTIONS);
        assertTrue(jit.ok(), jit.diagnostics().toString());

        int n = 64;                                   // кратно reqd_work_group_size(64), иначе IN-L01 (на устройстве RT-L01)
        float[] in = new float[n];
        for (int i = 0; i < n; i++) {
            in[i] = i + 1;
        }
        float[] out = new float[n];
        var run = Interpreter.run(jit.kernels().get(0).module(), NdRange.of1D(n), out, in);
        assertTrue(run.isOk(), run.diagnostics().toString());
        float[] w = {0.5f, 1f, 2f, 4f};
        float[] expected = new float[n];
        for (int i = 0; i < n; i++) {
            expected[i] = in[i] * w[i & 3] * 2f;
        }
        assertArrayEquals(expected, out);

        Result<ClProgram> cl = OpenClEmitter.program(jit.module(), EmitOptions.RELEASE);
        assertTrue(cl.isOk(), cl.diagnostics().toString());
        assertTrue(cl.value().source().contains("__constant float tbl0[4]"), cl.value().source());
        assertEquals(1, cl.value().kernels().size());
    }

    /** Непривязанный флаг: {@code GPU0115} с позицией узла генератора. */
    @Test
    void unboundFieldPointsAtTheGeneratorNode() {
        AsmJit.Unit unit = unit(Bindings.none().table("gen/Tables", "W", new float[]{1f}));
        AsmJit.Result jit = AsmJit.compile(unit, OPTIONS);
        assertEquals("GPU0115", jit.diagnostics().get(0).code(), jit.diagnostics().toString());
        assertEquals("router/demo:102", AsmJit.position(jit.diagnostics().get(0), unit));
    }
}
