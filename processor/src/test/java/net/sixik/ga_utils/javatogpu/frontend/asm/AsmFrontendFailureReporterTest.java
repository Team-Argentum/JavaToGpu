package net.sixik.ga_utils.javatogpu.frontend.asm;

import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        ClassNode classNode = new ClassNode();
        new ClassReader(classBytesWithMixedMethods()).accept(classNode, 0);

        AsmFrontendFailureReport report = reporter.reportClass(classNode);

        assertFalse(report.successful());
        assertEquals(1, report.failureCount());
        assertEquals("arrayLength", report.failures().get(0).family());
        assertEquals(OWNER, report.failures().get(0).ownerInternalName());
        assertEquals("arrayLengthKernel", report.failures().get(0).methodName());
    }

    @Test
    void reportsWholeClassFromByteArray() {
        AsmFrontendFailureReport report = reporter.reportClass(classBytesWithMixedMethods());

        assertFalse(report.successful());
        assertEquals(1, report.failureCount());
        assertEquals("arrayLength", report.failures().get(0).family());
        assertEquals("arrayLengthKernel", report.failures().get(0).methodName());
    }

    @Test
    void reportsWholeClassFromClassFile() throws IOException {
        Path classFile = Files.createTempFile("javatogpu-asm-report", ".class");
        try {
            Files.write(classFile, classBytesWithMixedMethods());

            AsmFrontendFailureReport report = reporter.reportClassFile(classFile);

            assertFalse(report.successful());
            assertEquals(1, report.failureCount());
            assertEquals("arrayLength", report.failures().get(0).family());
        } finally {
            Files.deleteIfExists(classFile);
        }
    }

    @Test
    void reportsWholeClassDirectoryInStablePathOrder() throws IOException {
        Path classDirectory = Files.createTempDirectory("javatogpu-asm-report-dir");
        Path packageDirectory = Files.createDirectories(classDirectory.resolve("sample"));
        try {
            Files.write(packageDirectory.resolve("BReportDemo.class"), classBytesWithThrowMethod("sample/BReportDemo"));
            Files.write(packageDirectory.resolve("AReportDemo.class"), classBytesWithMixedMethods());

            AsmFrontendFailureReport report = reporter.reportClassDirectory(classDirectory);

            assertFalse(report.successful());
            assertEquals(2, report.failureCount());
            assertEquals("arrayLength", report.failures().get(0).family());
            assertEquals("exceptionControlFlow", report.failures().get(1).family());
            assertEquals(1L, report.familyCounts().get("arrayLength"));
            assertEquals(1L, report.familyCounts().get("exceptionControlFlow"));
        } finally {
            Files.deleteIfExists(packageDirectory.resolve("AReportDemo.class"));
            Files.deleteIfExists(packageDirectory.resolve("BReportDemo.class"));
            Files.deleteIfExists(packageDirectory);
            Files.deleteIfExists(classDirectory);
        }
    }

    @Test
    void reportsWholeJarInStableEntryOrder() throws IOException {
        Path jarFile = Files.createTempFile("javatogpu-asm-report", ".jar");
        try {
            writeJar(jarFile, List.of(
                    jarClass("sample/BReportDemo.class", classBytesWithThrowMethod("sample/BReportDemo")),
                    jarClass("sample/AReportDemo.class", classBytesWithMixedMethods())
            ));

            AsmFrontendFailureReport report = reporter.reportJar(jarFile);

            assertFalse(report.successful());
            assertEquals(2, report.failureCount());
            assertEquals("arrayLength", report.failures().get(0).family());
            assertEquals("exceptionControlFlow", report.failures().get(1).family());
            assertEquals("sample/ReportDemo", report.failures().get(0).ownerInternalName());
            assertEquals("sample/BReportDemo", report.failures().get(1).ownerInternalName());
        } finally {
            Files.deleteIfExists(jarFile);
        }
    }

    @Test
    void reportsArtifactByDetectingClassDirectoryAndJarInputs() throws IOException {
        Path classFile = Files.createTempFile("javatogpu-asm-report-artifact", ".class");
        Path classDirectory = Files.createTempDirectory("javatogpu-asm-report-artifact-dir");
        Path jarFile = Files.createTempFile("javatogpu-asm-report-artifact", ".jar");
        try {
            Files.write(classFile, classBytesWithMixedMethods());
            Files.write(classDirectory.resolve("ReportDemo.class"), classBytesWithMixedMethods());
            writeJar(jarFile, List.of(jarClass("sample/ReportDemo.class", classBytesWithMixedMethods())));

            assertEquals("arrayLength", reporter.reportArtifact(classFile).failures().get(0).family());
            assertEquals("arrayLength", reporter.reportArtifact(classDirectory).failures().get(0).family());
            assertEquals("arrayLength", reporter.reportArtifact(jarFile).failures().get(0).family());
        } finally {
            Files.deleteIfExists(classFile);
            Files.deleteIfExists(classDirectory.resolve("ReportDemo.class"));
            Files.deleteIfExists(classDirectory);
            Files.deleteIfExists(jarFile);
        }
    }

    @Test
    void rejectsUnsupportedArtifactPathTypes() throws IOException {
        Path unsupportedFile = Files.createTempFile("javatogpu-asm-report-artifact", ".txt");
        try {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> reporter.reportArtifact(unsupportedFile)
            );

            assertTrue(exception.getMessage().contains(".class file, .jar file, or class directory"));
        } finally {
            Files.deleteIfExists(unsupportedFile);
        }
    }

    @Test
    void writesReportAsPropertiesArtifact() throws IOException {
        Path reportFile = Files.createTempFile("javatogpu-asm-report", ".properties");
        try {
            AsmFrontendFailureReport report = reporter.reportClass(classBytesWithMixedMethods());

            AsmFrontendFailureReportIO.write(reportFile, report);
            Properties properties = AsmFrontendFailureReportIO.readIfExists(reportFile).orElseThrow();

            assertEquals("false", properties.getProperty("asmReport.successful"));
            assertEquals("1", properties.getProperty("asmReport.failureCount"));
            assertEquals("arrayLength", properties.getProperty("asmReport.failure.0.family"));
            assertTrue(properties.getProperty("asmReport.summary").contains("failures=1"));
        } finally {
            Files.deleteIfExists(reportFile);
        }
    }

    @Test
    void convertsReportToPropertiesWithCustomPrefix() {
        AsmFrontendFailureReport report = reporter.reportClass(classBytesWithMixedMethods());

        Properties properties = AsmFrontendFailureReportIO.toProperties(report, "customAsm");

        assertEquals("false", properties.getProperty("customAsm.successful"));
        assertEquals("arrayLength", properties.getProperty("customAsm.failure.0.family"));
    }

    @Test
    void requireSuccessfulReturnsSuccessfulReport() {
        MethodNode method = methodNode("kernel", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });
        AsmFrontendFailureReport report = reporter.report(OWNER, method);

        assertEquals(report, report.requireSuccessful());
    }

    @Test
    void requireSuccessfulThrowsWithFirstFailureMetadata() {
        AsmFrontendFailureReport report = reporter.reportClass(classBytesWithMixedMethods());

        AsmFrontendException exception = assertThrows(AsmFrontendException.class, report::requireSuccessful);

        assertTrue(exception.getMessage().contains("asmFailureReport failed failures=1"));
        assertTrue(exception.getMessage().contains("ARRAYLENGTH"));
        assertEquals("arrayLength", exception.metadata().orElseThrow().family());
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

    private byte[] classBytesWithMixedMethods() {
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
        return writer.toByteArray();
    }

    private byte[] classBytesWithThrowMethod(String ownerInternalName) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, ownerInternalName, null, "java/lang/Object", null);
        writeMethod(writer, "throwKernel", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitInsn(Opcodes.ATHROW);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });
        writer.visitEnd();
        return writer.toByteArray();
    }

    private void writeJar(Path jarFile, List<JarClass> classes) throws IOException {
        try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(jarFile))) {
            for (JarClass jarClass : classes) {
                outputStream.putNextEntry(new JarEntry(jarClass.entryName()));
                outputStream.write(jarClass.classBytes());
                outputStream.closeEntry();
            }
        }
    }

    private JarClass jarClass(String entryName, byte[] classBytes) {
        return new JarClass(entryName, classBytes);
    }

    private record JarClass(String entryName, byte[] classBytes) {
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
