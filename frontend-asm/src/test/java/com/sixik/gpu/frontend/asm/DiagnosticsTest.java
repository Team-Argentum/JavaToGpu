package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.frontend.common.Diag;
import com.sixik.gpu.ir.interpreter.samples.IndexKernels;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Диагностики, которые есть только у фронтенда байткода (группа {@code GPU03xx} таблицы {@code diagnostics.def}):
 * каждая воспроизводится class-файлом, и тест следит, чтобы в таблице не было кодов группы без проверки.
 */
class DiagnosticsTest {

    private static final Set<String> covered = new TreeSet<>();

    private static List<AsmDiagnostic> compile(Map<String, byte[]> classes, String name) {
        AsmFrontend.Result r = AsmFrontend.compile(ClassSource.of(classes), List.of(name), AsmFrontend.Options.DEFAULT);
        r.diagnostics().forEach(d -> covered.add(d.code()));
        return r.diagnostics();
    }

    private static Set<String> codes(List<AsmDiagnostic> diags) {
        return diags.stream().map(AsmDiagnostic::code).collect(Collectors.toSet());
    }

    private static byte[] corpusClass(Class<?> c) throws IOException {
        try (InputStream in = c.getClassLoader().getResourceAsStream(c.getName().replace('.', '/') + ".class")) {
            assertTrue(in != null, c.getName());
            return in.readAllBytes();
        }
    }

    @Test
    void classWithoutLocalVariableTableIsRejected() throws IOException {
        ClassWriter cw = new ClassWriter(0);
        new ClassReader(corpusClass(IndexKernels.class)).accept(cw, ClassReader.SKIP_DEBUG);
        String internal = IndexKernels.class.getName().replace('.', '/');
        List<AsmDiagnostic> d = compile(Map.of(internal, cw.toByteArray()), IndexKernels.class.getName());
        assertTrue(codes(d).contains("GPU0301"), d.toString());
        assertTrue(d.stream().filter(x -> x.code().equals("GPU0301")).allMatch(AsmDiagnostic::error), d.toString());
    }

    @Test
    void subroutinesAreUnsupportedInstructions() {
        // jsr/ret javac не порождает с Java 6: class-файл версии 49 собирается вручную
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC, "t/Jsr", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "k", "()V", null, null);
        mv.visitAnnotation(Annotations.KERNEL, false).visitEnd();
        mv.visitCode();
        Label sub = new Label();
        mv.visitJumpInsn(Opcodes.JSR, sub);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitLabel(sub);
        mv.visitVarInsn(Opcodes.ASTORE, 0);
        mv.visitVarInsn(Opcodes.RET, 0);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        List<AsmDiagnostic> d = compile(Map.of("t/Jsr", cw.toByteArray()), "t.Jsr");
        assertTrue(codes(d).contains("GPU0302"), d.toString());
        assertTrue(d.stream().anyMatch(x -> x.code().equals("GPU0302") && x.message().contains("jsr")), d.toString());
    }

    @Test
    void unreadableClassFilesAreReported() {
        List<AsmDiagnostic> garbage = compile(Map.of("t/Bad", new byte[]{1, 2, 3, 4, 5}), "t.Bad");
        assertEquals(List.of("GPU0306"), garbage.stream().map(AsmDiagnostic::code).toList(), garbage.toString());
        List<AsmDiagnostic> missing = compile(Map.of(), "t.Missing");
        assertEquals(List.of("GPU0306"), missing.stream().map(AsmDiagnostic::code).toList(), missing.toString());
        assertTrue(missing.get(0).message().contains("t.Missing"), missing.toString());
    }

    /** Ядро {@code static void k(int n)} с телом, которое пишет {@code body}; локалей сверх параметра нет. */
    private static byte[] kernel(String name, java.util.function.Consumer<MethodVisitor> body) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "k", "(I)V", null, null);
        mv.visitAnnotation(Annotations.KERNEL, false).visitEnd();
        mv.visitCode();
        body.accept(mv);
        mv.visitMaxs(3, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void irreducibleControlFlowIsRejected() {
        // в цикл A..B входят двумя путями: проходом в A и переходом в B - javac так не строит
        byte[] c = kernel("t/Irreducible", mv -> {
            Label a = new Label();
            Label b = new Label();
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IFEQ, b);
            mv.visitLabel(a);
            mv.visitIincInsn(0, 1);
            mv.visitLabel(b);
            mv.visitIincInsn(0, 2);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitIntInsn(Opcodes.BIPUSH, 100);
            mv.visitJumpInsn(Opcodes.IF_ICMPLT, a);
            mv.visitInsn(Opcodes.RETURN);
        });
        List<AsmDiagnostic> d = compile(Map.of("t/Irreducible", c), "t.Irreducible");
        assertEquals(List.of("GPU0304"), d.stream().map(AsmDiagnostic::code).toList(), d.toString());
    }

    @Test
    void valueOnTheStackAcrossALoopIsRejected() {
        // значение, лежащее на стеке через весь цикл, - не условное выражение
        byte[] c = kernel("t/StackLoop", mv -> {
            Label h = new Label();
            mv.visitInsn(Opcodes.ICONST_5);
            mv.visitLabel(h);
            mv.visitIincInsn(0, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitIntInsn(Opcodes.BIPUSH, 10);
            mv.visitJumpInsn(Opcodes.IF_ICMPLT, h);
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.RETURN);
        });
        List<AsmDiagnostic> d = compile(Map.of("t/StackLoop", c), "t.StackLoop");
        assertEquals(List.of("GPU0305"), d.stream().map(AsmDiagnostic::code).toList(), d.toString());
    }

    @Test
    void calleeClassMissingFromTheClasspathIsReported() {
        byte[] c = kernel("t/CallsMissing", mv -> {
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "t/Missing", "f", "(I)I", false);
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.RETURN);
        });
        List<AsmDiagnostic> d = compile(Map.of("t/CallsMissing", c), "t.CallsMissing");
        assertEquals(List.of("GPU0303"), d.stream().map(AsmDiagnostic::code).toList(), d.toString());
        assertTrue(d.get(0).message().contains("t.Missing") && d.get(0).message().contains("Missing.f"), d.toString());
    }

    /** {@code GPU0307}: синтез LVT ({@code AsmJit}, режим {@code SYNTHESIZE}) - слот читается до записи. */
    @Test
    void unsynthesizableLocalsAreReported() {
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        cn.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "t/NoLvt", null, "java/lang/Object", null);
        MethodVisitor mv = cn.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "k", "([I)V", null, null);
        mv.visitAnnotation(Annotations.KERNEL, false).visitEnd();
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitInsn(Opcodes.IASTORE);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(3, 2);
        mv.visitEnd();
        cn.visitEnd();
        AsmJit.Result r = AsmJit.compile(new AsmJit.Unit(null, Map.of("t/NoLvt", cn),
                List.of(AsmJit.Entry.of("t/NoLvt", "k", "([I)V"))), AsmJit.Options.DEFAULT.synthesizeLvt(true));
        r.diagnostics().forEach(d -> covered.add(d.code()));
        assertEquals(List.of("GPU0307"), r.diagnostics().stream().map(AsmDiagnostic::code).toList(),
                r.diagnostics().toString());
    }

    /** {@code GPU0311}: точка входа {@link AsmJit} без метода и с атрибутами, заданными дважды. */
    @Test
    void badAsmJitEntriesAreReported() throws IOException {
        String internal = IndexKernels.class.getName().replace('.', '/');
        AsmJit.Unit unit = new AsmJit.Unit(ClassSource.of(Map.of(internal, corpusClass(IndexKernels.class))), null,
                List.of(AsmJit.Entry.of(internal, "noSuchKernel", "()V"),
                        new AsmJit.Entry(internal, "sine", "([F[F)V", null, Map.of("x", 64))));
        AsmJit.Result r = AsmJit.compile(unit, AsmJit.Options.DEFAULT);
        r.diagnostics().forEach(d -> covered.add(d.code()));
        assertEquals(List.of("GPU0311", "GPU0311"), r.diagnostics().stream().map(AsmDiagnostic::code).toList(),
                r.diagnostics().toString());
        assertTrue(r.diagnostics().get(1).message().contains("both"), r.diagnostics().toString());
    }

    @Test
    void everyBytecodeOnlyCodeIsCovered() throws IOException {
        classWithoutLocalVariableTableIsRejected();
        subroutinesAreUnsupportedInstructions();
        unreadableClassFilesAreReported();
        irreducibleControlFlowIsRejected();
        valueOnTheStackAcrossALoopIsRejected();
        calleeClassMissingFromTheClasspathIsReported();
        badAsmJitEntriesAreReported();
        unsynthesizableLocalsAreReported();
        Set<String> table = Diag.codes().stream().filter(c -> c.startsWith("GPU03"))
                .collect(Collectors.toCollection(TreeSet::new));
        assertFalse(table.isEmpty());
        Set<String> seen = covered.stream().filter(c -> c.startsWith("GPU03"))
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(table, seen, "GPU03xx codes of diagnostics.def and the codes this test reproduces");
    }
}
