package net.sixik.ga_utils.javatogpu.frontend.diagnostics;

import net.sixik.ga_utils.javatogpu.frontend.asm.AsmFrontendException;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmFrontendFailureMetadata;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsmFrontendDiagnosticAdapterTest {

    private static final String OWNER = "sample/DiagnosticsDemo";

    private final AsmFrontendDiagnosticAdapter adapter = new AsmFrontendDiagnosticAdapter();

    @Test
    void rendersAsmFrontendFailureWithMethodSourceSpanAndHelp() {
        String source = "public static void kernel(float[] input, float[] output) {\n"
                + "    output[0] = input.length;\n"
                + "}";
        ParsedGpuMethod parsedMethod = new GpuMethodParser().parseMethod(source, "DiagnosticsDemo", "sample.DiagnosticsDemo");
        AsmGpuMethod method = new AsmGpuMethod(
                OWNER,
                parsedMethod,
                methodNode("kernel", "([F[F)V", mv -> {
                    mv.visitCode();
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                })
        );

        String rendered = adapter.render(
                method,
                new AsmFrontendException("Runtime array length reads are not supported by ASM GPU frontend: ARRAYLENGTH; pass required lengths or bounds as explicit kernel/helper parameters"),
                "DiagnosticsDemo.java",
                source.lines().toList()
        ).orElseThrow();

        assertTrue(rendered.contains("error[JTG-ASM-001]: ASM frontend cannot lower this method to GPU-safe IR"));
        assertTrue(rendered.contains("--> DiagnosticsDemo.java:1:1"));
        assertTrue(rendered.contains("1 | public static void kernel(float[] input, float[] output) {"));
        assertTrue(rendered.contains("method contains bytecode outside the supported GPU subset"));
        assertTrue(rendered.contains("= help: Runtime array length reads are not supported"));
    }

    @Test
    void returnsEmptyDiagnosticWhenParsedMethodHasNoSourceRange() {
        AsmGpuMethod method = new AsmGpuMethod(
                OWNER,
                parsedMethodWithoutSource(),
                methodNode("kernel", "()V", mv -> {
                    mv.visitCode();
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                })
        );

        assertTrue(adapter.toDiagnostic(
                method,
                new AsmFrontendException("Unsupported bytecode opcode for ASM GPU frontend: DUP_X1"),
                "DiagnosticsDemo.java"
        ).isEmpty());
    }

    @Test
    void usesOwnerQualifiedNameWhenSourceNameIsBlank() {
        ParsedGpuMethod parsedMethod = new GpuMethodParser().parseMethod(
                "public static void kernel() { }",
                "DiagnosticsDemo",
                "sample.DiagnosticsDemo"
        );
        AsmGpuMethod method = new AsmGpuMethod(
                OWNER,
                parsedMethod,
                methodNode("kernel", "()V", mv -> {
                    mv.visitCode();
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                })
        );

        GpuSourceDiagnostic diagnostic = adapter.toDiagnostic(
                method,
                new AsmFrontendException("Unsupported bytecode opcode for ASM GPU frontend: DUP_X1"),
                ""
        ).orElseThrow();

        assertEquals("sample/DiagnosticsDemo.java:1:1", diagnostic.primarySpan().location());
    }

    @Test
    void exportsAsmFailureMetadataAsArtifactFields() {
        ParsedGpuMethod parsedMethod = new GpuMethodParser().parseMethod(
                "public static void kernel(float[] input) { }",
                "DiagnosticsDemo",
                "sample.DiagnosticsDemo"
        );
        AsmGpuMethod method = new AsmGpuMethod(
                OWNER,
                parsedMethod,
                methodNode("kernel", "([F)V", mv -> {
                    mv.visitCode();
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                })
        );
        AsmFrontendFailureMetadata metadata = new AsmFrontendFailureMetadata(
                "arrayLength",
                OWNER,
                "kernel",
                "([F)V",
                2,
                7,
                "ARRAYLENGTH",
                "Runtime array length reads are not supported by ASM GPU frontend: ARRAYLENGTH"
        );

        GpuSourceDiagnostic diagnostic = adapter.toDiagnostic(
                method,
                new AsmFrontendException("Runtime array length reads are not supported", metadata),
                "DiagnosticsDemo.java"
        ).orElseThrow();

        assertEquals("arrayLength", diagnostic.artifactFields().get("asmFailure.family"));
        assertEquals(OWNER, diagnostic.artifactFields().get("asmFailure.owner"));
        assertEquals("kernel", diagnostic.artifactFields().get("asmFailure.method"));
        assertEquals("([F)V", diagnostic.artifactFields().get("asmFailure.descriptor"));
        assertEquals(OWNER + ".kernel([F)V", diagnostic.artifactFields().get("asmFailure.methodKey"));
        assertEquals(
                "arrayLength sample/DiagnosticsDemo.kernel([F)V instruction=2 line=7 opcode=ARRAYLENGTH",
                diagnostic.artifactFields().get("asmFailure.summary")
        );
        assertEquals("2", diagnostic.artifactFields().get("asmFailure.instructionIndex"));
        assertEquals("7", diagnostic.artifactFields().get("asmFailure.lineNumber"));
        assertEquals("ARRAYLENGTH", diagnostic.artifactFields().get("asmFailure.opcode"));
    }

    private ParsedGpuMethod parsedMethodWithoutSource() {
        return new ParsedGpuMethod(
                "DiagnosticsDemo",
                "sample.DiagnosticsDemo",
                "kernel",
                "void",
                List.of(new ParsedGpuParameter("value", "int", GpuAddressSpace.PRIVATE, false, List.of())),
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

    private MethodNode methodNode(String methodName, String descriptor, MethodBodyWriter bodyWriter) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, OWNER, null, "java/lang/Object", null);
        MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodName, descriptor, null, null);
        bodyWriter.write(methodVisitor);
        writer.visitEnd();

        ClassNode classNode = new ClassNode();
        new ClassReader(writer.toByteArray()).accept(classNode, 0);
        return classNode.methods.stream()
                .filter(method -> method.name.equals(methodName) && method.desc.equals(descriptor))
                .findFirst()
                .orElseThrow();
    }

    @FunctionalInterface
    private interface MethodBodyWriter {
        void write(MethodVisitor methodVisitor);
    }
}
