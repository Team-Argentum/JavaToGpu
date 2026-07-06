package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.asm.AsmGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmFrontendException;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmFrontendFailureReport;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuProgramCompilerTest {

    private static final String DEMO_OWNER = "sample/Demo";
    private static final String HELPERS_OWNER = "sample/Helpers";
    private static final String GPU_OWNER = Type.getInternalName(net.sixik.ga_utils.javatogpu.api.GPU.class);

    @Test
    void compilesSourceProgramThroughUnifiedFacade() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = square(input[id]);
                }
                """;
        String helperSource = """
                @CCode(inline = true)
                float square(float value) {
                    return value * value;
                }
                """;

        GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();
        String kernel = compiler.compileSource(methodSource, List.of(helperSource));

        assertTrue(kernel.contains("inline float jtg_fn_square_float(float value);"));
        assertTrue(kernel.contains("output[id] = jtg_fn_square_float(input[id]);"));
    }

    @Test
    void compilesStructuredAsmProgramThroughUnifiedFacade() {
        MethodNode helperMethodNode = methodNode(HELPERS_OWNER, "square", "(F)F", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitVarInsn(Opcodes.FLOAD, 0);
            mv.visitVarInsn(Opcodes.FLOAD, 0);
            mv.visitInsn(Opcodes.FMUL);
            mv.visitInsn(Opcodes.FRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        MethodNode kernelMethodNode = methodNode(DEMO_OWNER, "kernel", "([F[F)V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, GPU_OWNER, "get_global_id", "(I)I", false);
            mv.visitVarInsn(Opcodes.ISTORE, 2);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.FALOAD);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_OWNER, "square", "(F)F", false);
            mv.visitInsn(Opcodes.FASTORE);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmGpuMethod helperMethod = new AsmGpuMethod(
                HELPERS_OWNER,
                parsedMethod("Helpers", "sample.Helpers", "square", "float", List.of(parameter("value", "float"))),
                helperMethodNode
        );
        AsmGpuMethod kernelMethod = new AsmGpuMethod(
                DEMO_OWNER,
                parsedMethod("Demo", "sample.Demo", "kernel", "void", List.of(
                        globalArrayParameter("input", "float[]"),
                        globalArrayParameter("output", "float[]")
                )),
                kernelMethodNode
        );

        GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();
        String kernel = compiler.compileStructuredAsm(kernelMethod, List.of(helperMethod));

        assertTrue(kernel.contains("float jtg_fn_Helpers_square_float(float value);"));
        assertTrue(kernel.contains("arg1[tmp2] = jtg_fn_Helpers_square_float(arg0[tmp2]);"));
    }

    @Test
    void lowersStructuredAsmThroughUnifiedFacade() {
        MethodNode methodNode = methodNode(DEMO_OWNER, "kernel", "([F[F)V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, GPU_OWNER, "get_global_id", "(I)I", false);
            mv.visitVarInsn(Opcodes.ISTORE, 2);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.FCONST_1);
            mv.visitInsn(Opcodes.FASTORE);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();
        assertEquals(
                "kernel",
                compiler.liftStructuredAsm(new AsmGpuMethod(
                        DEMO_OWNER,
                        parsedMethod("Demo", "sample.Demo", "kernel", "void", List.of(
                                globalArrayParameter("input", "float[]"),
                                globalArrayParameter("output", "float[]")
                        )),
                        methodNode
                )).name()
        );
    }

    @Test
    void reportsRustLikeDiagnosticForStructuredAsmFailureThroughFacade() {
        String source = "public static void kernel(float[] input, float[] output) {\n"
                + "    output[0] = input.length;\n"
                + "}";
        ParsedGpuMethod parsedMethod = new GpuMethodParser().parseMethod(source, "Demo", "sample.Demo");
        MethodNode methodNode = methodNode(DEMO_OWNER, "kernel", "([F[F)V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });
        AsmGpuMethod method = new AsmGpuMethod(DEMO_OWNER, parsedMethod, methodNode);
        List<String> diagnostics = new java.util.ArrayList<>();

        GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();
        AsmFrontendException exception = assertThrows(
                AsmFrontendException.class,
                () -> compiler.compileStructuredAsm(
                        method,
                        List.of(),
                        "Demo.java",
                        source.lines().toList(),
                        diagnostics::add
                )
        );

        assertTrue(exception.getMessage().contains("Runtime array length reads are not supported"));
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).contains("error[JTG-ASM-001]: ASM frontend cannot lower this method to GPU-safe IR"));
        assertTrue(diagnostics.get(0).contains("--> Demo.java:1:1"));
        assertTrue(diagnostics.get(0).contains("1 | public static void kernel(float[] input, float[] output) {"));
        assertTrue(diagnostics.get(0).contains("= help: Runtime array length reads are not supported"));
    }

    @Test
    void reportsStructuredAsmFailuresThroughUnifiedFacadeWithoutCompiling() {
        MethodNode arrayLengthMethod = methodNode(DEMO_OWNER, "arrayLengthKernel", "([F)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });
        MethodNode throwMethod = methodNode(DEMO_OWNER, "throwKernel", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitInsn(Opcodes.ATHROW);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();
        AsmFrontendFailureReport report = compiler.reportStructuredAsm(List.of(
                new AsmGpuMethod(
                        DEMO_OWNER,
                        parsedMethod("Demo", "sample.Demo", "arrayLengthKernel", "int", List.of(
                                globalArrayParameter("input", "float[]")
                        )),
                        arrayLengthMethod
                ),
                new AsmGpuMethod(
                        DEMO_OWNER,
                        parsedMethod("Demo", "sample.Demo", "throwKernel", "void", List.of()),
                        throwMethod
                )
        ));

        assertEquals(2, report.failureCount());
        assertEquals(1L, report.familyCounts().get("arrayLength"));
        assertEquals(1L, report.familyCounts().get("exceptionControlFlow"));
        assertTrue(report.summaryLine().contains("asmFailureReport failed failures=2"));
        assertTrue(report.artifactFields("asmReport").get("asmReport.summaries").contains("ATHROW"));
    }

    @Test
    void reportsStructuredAsmClassFailuresThroughUnifiedFacade() {
        ClassNode classNode = new ClassNode();
        new ClassReader(classBytesWithArrayLengthMethod()).accept(classNode, 0);

        AsmFrontendFailureReport report = GpuProgramCompiler.createDefault().reportStructuredAsmClass(classNode);

        assertEquals(1, report.failureCount());
        assertEquals("arrayLength", report.failures().get(0).family());
        assertEquals("arrayLengthKernel", report.failures().get(0).methodName());
    }

    @Test
    void reportsStructuredAsmClassFailuresFromByteArrayThroughFacade() {
        AsmFrontendFailureReport report = GpuProgramCompiler.createDefault()
                .reportStructuredAsmClass(classBytesWithArrayLengthMethod());

        assertEquals(1, report.failureCount());
        assertEquals("arrayLength", report.failures().get(0).family());
    }

    @Test
    void reportsStructuredAsmClassFailuresFromFileThroughFacade() throws IOException {
        Path classFile = Files.createTempFile("javatogpu-compiler-asm-report", ".class");
        try {
            Files.write(classFile, classBytesWithArrayLengthMethod());

            AsmFrontendFailureReport report = GpuProgramCompiler.createDefault()
                    .reportStructuredAsmClassFile(classFile);

            assertEquals(1, report.failureCount());
            assertEquals("arrayLength", report.failures().get(0).family());
        } finally {
            Files.deleteIfExists(classFile);
        }
    }

    @Test
    void reportsStructuredAsmClassFailuresFromDirectoryThroughFacade() throws IOException {
        Path classDirectory = Files.createTempDirectory("javatogpu-compiler-asm-report-dir");
        try {
            Files.write(classDirectory.resolve("Demo.class"), classBytesWithArrayLengthMethod());

            AsmFrontendFailureReport report = GpuProgramCompiler.createDefault()
                    .reportStructuredAsmClassDirectory(classDirectory);

            assertEquals(1, report.failureCount());
            assertEquals("arrayLength", report.failures().get(0).family());
        } finally {
            Files.deleteIfExists(classDirectory.resolve("Demo.class"));
            Files.deleteIfExists(classDirectory);
        }
    }

    @Test
    void reportsStructuredAsmClassFailuresFromJarThroughFacade() throws IOException {
        Path jarFile = Files.createTempFile("javatogpu-compiler-asm-report", ".jar");
        try {
            try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(jarFile))) {
                outputStream.putNextEntry(new JarEntry("sample/Demo.class"));
                outputStream.write(classBytesWithArrayLengthMethod());
                outputStream.closeEntry();
            }

            AsmFrontendFailureReport report = GpuProgramCompiler.createDefault()
                    .reportStructuredAsmJar(jarFile);

            assertEquals(1, report.failureCount());
            assertEquals("arrayLength", report.failures().get(0).family());
            assertEquals("arrayLengthKernel", report.failures().get(0).methodName());
        } finally {
            Files.deleteIfExists(jarFile);
        }
    }

    @Test
    void reportsStructuredAsmArtifactFailuresByDetectingPathTypeThroughFacade() throws IOException {
        Path classFile = Files.createTempFile("javatogpu-compiler-asm-report-artifact", ".class");
        Path classDirectory = Files.createTempDirectory("javatogpu-compiler-asm-report-artifact-dir");
        Path jarFile = Files.createTempFile("javatogpu-compiler-asm-report-artifact", ".jar");
        try {
            Files.write(classFile, classBytesWithArrayLengthMethod());
            Files.write(classDirectory.resolve("Demo.class"), classBytesWithArrayLengthMethod());
            try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(jarFile))) {
                outputStream.putNextEntry(new JarEntry("sample/Demo.class"));
                outputStream.write(classBytesWithArrayLengthMethod());
                outputStream.closeEntry();
            }

            GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();

            assertEquals("arrayLength", compiler.reportStructuredAsmArtifact(classFile).failures().get(0).family());
            assertEquals("arrayLength", compiler.reportStructuredAsmArtifact(classDirectory).failures().get(0).family());
            assertEquals("arrayLength", compiler.reportStructuredAsmArtifact(jarFile).failures().get(0).family());
        } finally {
            Files.deleteIfExists(classFile);
            Files.deleteIfExists(classDirectory.resolve("Demo.class"));
            Files.deleteIfExists(classDirectory);
            Files.deleteIfExists(jarFile);
        }
    }

    @Test
    void writesStructuredAsmArtifactReportThroughFacade() throws IOException {
        Path classFile = Files.createTempFile("javatogpu-compiler-asm-report-artifact", ".class");
        Path reportFile = Files.createTempFile("javatogpu-compiler-asm-report", ".properties");
        try {
            Files.write(classFile, classBytesWithArrayLengthMethod());

            AsmFrontendFailureReport report = GpuProgramCompiler.createDefault()
                    .writeStructuredAsmArtifactReport(classFile, reportFile);
            Properties properties = new Properties();
            try (java.io.InputStream inputStream = Files.newInputStream(reportFile)) {
                properties.load(inputStream);
            }

            assertEquals(1, report.failureCount());
            assertEquals("false", properties.getProperty("asmReport.successful"));
            assertEquals("arrayLength", properties.getProperty("asmReport.failure.0.family"));
        } finally {
            Files.deleteIfExists(classFile);
            Files.deleteIfExists(reportFile);
        }
    }

    @Test
    void requireStructuredAsmArtifactFailsOnUnsupportedBytecodeThroughFacade() throws IOException {
        Path classFile = Files.createTempFile("javatogpu-compiler-asm-require", ".class");
        try {
            Files.write(classFile, classBytesWithArrayLengthMethod());

            AsmFrontendException exception = assertThrows(
                    AsmFrontendException.class,
                    () -> GpuProgramCompiler.createDefault().requireStructuredAsmArtifact(classFile)
            );

            assertTrue(exception.getMessage().contains("asmFailureReport failed failures=1"));
            assertEquals("arrayLength", exception.metadata().orElseThrow().family());
        } finally {
            Files.deleteIfExists(classFile);
        }
    }

    @Test
    void writeAndRequireStructuredAsmArtifactReportWritesBeforeFailing() throws IOException {
        Path classFile = Files.createTempFile("javatogpu-compiler-asm-require", ".class");
        Path reportFile = Files.createTempFile("javatogpu-compiler-asm-require", ".properties");
        try {
            Files.write(classFile, classBytesWithArrayLengthMethod());

            assertThrows(
                    AsmFrontendException.class,
                    () -> GpuProgramCompiler.createDefault().writeAndRequireStructuredAsmArtifactReport(classFile, reportFile)
            );
            Properties properties = new Properties();
            try (java.io.InputStream inputStream = Files.newInputStream(reportFile)) {
                properties.load(inputStream);
            }

            assertEquals("false", properties.getProperty("asmReport.successful"));
            assertEquals("arrayLength", properties.getProperty("asmReport.failure.0.family"));
        } finally {
            Files.deleteIfExists(classFile);
            Files.deleteIfExists(reportFile);
        }
    }

    private ParsedGpuMethod parsedMethod(
            String ownerSimpleName,
            String ownerQualifiedName,
            String name,
            String returnType,
            List<ParsedGpuParameter> parameters
    ) {
        return new ParsedGpuMethod(
                ownerSimpleName,
                ownerQualifiedName,
                name,
                returnType,
                parameters,
                List.of(),
                List.of(),
                null,
                false,
                List.of(),
                "",
                "",
                "",
                false
        );
    }

    private ParsedGpuParameter parameter(String name, String javaType) {
        return new ParsedGpuParameter(name, javaType, GpuAddressSpace.PRIVATE, false, List.of());
    }

    private ParsedGpuParameter globalArrayParameter(String name, String javaType) {
        return new ParsedGpuParameter(name, javaType, GpuAddressSpace.GLOBAL, false, List.of());
    }

    private MethodNode methodNode(
            String ownerInternalName,
            String methodName,
            String descriptor,
            int access,
            MethodBodyWriter bodyWriter
    ) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, ownerInternalName, null, "java/lang/Object", null);
        MethodVisitor methodVisitor = writer.visitMethod(access, methodName, descriptor, null, null);
        bodyWriter.write(methodVisitor);
        writer.visitEnd();

        ClassNode classNode = new ClassNode();
        new ClassReader(writer.toByteArray()).accept(classNode, 0);
        return classNode.methods.stream()
                .filter(method -> method.name.equals(methodName) && method.desc.equals(descriptor))
                .findFirst()
                .orElseThrow();
    }

    private byte[] classBytesWithArrayLengthMethod() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, DEMO_OWNER, null, "java/lang/Object", null);
        MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "arrayLengthKernel", "([F)I", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitInsn(Opcodes.ARRAYLENGTH);
        methodVisitor.visitInsn(Opcodes.IRETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    @FunctionalInterface
    private interface MethodBodyWriter {
        void write(MethodVisitor methodVisitor);
    }
}
