package net.sixik.ga_utils.javatogpu.frontend.asm;

import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsmFrontendFailureReporterTest {

    private static final String OWNER = "sample/ReportDemo";

    private final AsmFrontendFailureReporter reporter = new AsmFrontendFailureReporter();

    @Test
    void returnsSuccessfulReportForSupportedMethod() {
        MethodNode method = methodNode("kernel", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmFrontendFailureReport report = reporter.report(OWNER, method);

        assertTrue(report.successful());
        assertEquals(0, report.failureCount());
        assertEquals("asmFailureReport successful failures=0", report.summaryLine());
        assertEquals("true", report.artifactFields("asmReport").get("asmReport.successful"));
    }

    @Test
    void collectsFailuresAcrossMultipleMethodsWithoutThrowing() {
        MethodNode arrayLengthMethod = methodNode("arrayLengthKernel", "([F)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });
        MethodNode throwMethod = methodNode("throwKernel", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitInsn(Opcodes.ATHROW);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmFrontendFailureReport report = reporter.reportAll(List.of(
                asmMethod("arrayLengthKernel", "int", arrayLengthMethod),
                asmMethod("throwKernel", "void", throwMethod)
        ));

        assertFalse(report.successful());
        assertEquals(2, report.failureCount());
        assertEquals(1L, report.familyCounts().get("arrayLength"));
        assertEquals(1L, report.familyCounts().get("exceptionControlFlow"));
        assertEquals(
                "arrayLength sample/ReportDemo.arrayLengthKernel([F)I instruction=2 opcode=ARRAYLENGTH",
                report.summaries().get(0)
        );
        assertTrue(report.summaryLine().contains("asmFailureReport failed failures=2"));
        assertEquals("false", report.artifactFields("asmReport").get("asmReport.successful"));
        assertEquals("2", report.artifactFields("asmReport").get("asmReport.failureCount"));
        assertTrue(report.artifactFields("asmReport").get("asmReport.summaries").contains("ATHROW"));
        assertEquals("arrayLength", report.artifactFields("asmReport").get("asmReport.failure.0.family"));
    }

    @Test
    void scannerCollectsMultipleFailuresInsideOneMethod() {
        MethodNode method = methodNode("mixedUnsupportedKernel", "([F)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitInsn(Opcodes.ATHROW);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmFrontendFailureReport report = reporter.report(OWNER, method);

        assertFalse(report.successful());
        assertEquals(2, report.failureCount());
        assertEquals("arrayLength", report.failures().get(0).family());
        assertEquals("exceptionControlFlow", report.failures().get(1).family());
        assertEquals("ARRAYLENGTH", report.failures().get(0).opcodeName());
        assertEquals("ATHROW", report.failures().get(1).opcodeName());
        assertTrue(report.artifactFields("asmReport").get("asmReport.summaries").contains("ARRAYLENGTH"));
        assertTrue(report.artifactFields("asmReport").get("asmReport.summaries").contains("ATHROW"));
    }

    @Test
    void scannerCollectsStaticOwnerFieldAndDescriptorIssues() {
        MethodNode method = methodNode("ownerAndDescriptorKernel", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "sample/ExternalHelper", "badArg", "(Ljava/lang/String;)V", false);
            mv.visitFieldInsn(Opcodes.GETSTATIC, "sample/ExternalState", "value", "Ljava/lang/String;");
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmFrontendFailureReport report = reporter.report(OWNER, method);

        assertFalse(report.successful());
        assertEquals(4, report.failureCount());
        assertEquals(1L, report.familyCounts().get("methodDescriptor"));
        assertEquals(1L, report.familyCounts().get("methodInvocation"));
        assertEquals(2L, report.familyCounts().get("fieldAccess"));
        assertTrue(report.artifactFields("asmReport").get("asmReport.summaries").contains("INVOKESTATIC"));
        assertTrue(report.artifactFields("asmReport").get("asmReport.summaries").contains("GETSTATIC"));
    }

    @Test
    void scannerHonorsWhitelistedHelperOwnerInConfig() {
        MethodNode method = methodNode("allowedHelperKernel", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "sample/ExternalHelper", "ok", "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmFrontendFailureReport report = reporter.report(
                OWNER,
                method,
                AsmValidationConfig.defaultConfig().withHelperOwner("sample/ExternalHelper")
        );

        assertTrue(report.successful());
    }

    @Test
    void reportsWholeClassWithoutManualAsmGpuMethodWrapping() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, OWNER, null, "java/lang/Object", null);
        writeMethod(writer, "arrayLengthKernel", "([F)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });
        writeMethod(writer, "okKernel", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });
        writer.visitEnd();

        ClassNode classNode = new ClassNode();
        new ClassReader(writer.toByteArray()).accept(classNode, 0);

        AsmFrontendFailureReport report = reporter.reportClass(classNode);

        assertFalse(report.successful());
        assertEquals(1, report.failureCount());
        assertEquals("arrayLength", report.failures().get(0).family());
        assertEquals(OWNER, report.failures().get(0).ownerInternalName());
        assertEquals("arrayLengthKernel", report.failures().get(0).methodName());
    }

    private AsmGpuMethod asmMethod(String name, String returnType, MethodNode methodNode) {
        return new AsmGpuMethod(
                OWNER,
                new ParsedGpuMethod(
                        "ReportDemo",
                        "sample.ReportDemo",
                        name,
                        returnType,
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        false,
                        List.of(),
                        "",
                        "",
                        "",
                        false
                ),
                methodNode
        );
    }

    private MethodNode methodNode(String methodName, String descriptor, int access, MethodBodyWriter bodyWriter) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, OWNER, null, "java/lang/Object", null);
        writeMethod(writer, methodName, descriptor, access, bodyWriter);
        writer.visitEnd();

        ClassNode classNode = new ClassNode();
        new ClassReader(writer.toByteArray()).accept(classNode, 0);
        return classNode.methods.stream()
                .filter(method -> method.name.equals(methodName) && method.desc.equals(descriptor))
                .findFirst()
                .orElseThrow();
    }

    private void writeMethod(
            ClassWriter writer,
            String methodName,
            String descriptor,
            int access,
            MethodBodyWriter bodyWriter
    ) {
        MethodVisitor methodVisitor = writer.visitMethod(access, methodName, descriptor, null, null);
        bodyWriter.write(methodVisitor);
    }

    @FunctionalInterface
    private interface MethodBodyWriter {
        void write(MethodVisitor methodVisitor);
    }
}
