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

    @Test
    void everyBytecodeOnlyCodeIsCovered() throws IOException {
        classWithoutLocalVariableTableIsRejected();
        subroutinesAreUnsupportedInstructions();
        unreadableClassFilesAreReported();
        Set<String> table = Diag.codes().stream().filter(c -> c.startsWith("GPU03"))
                .collect(Collectors.toCollection(TreeSet::new));
        assertFalse(table.isEmpty());
        Set<String> seen = covered.stream().filter(c -> c.startsWith("GPU03"))
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(table, seen, "GPU03xx codes of diagnostics.def and the codes this test reproduces");
    }
}
