package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.asm.AsmGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                null,
                false,
                List.of(),
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

    @FunctionalInterface
    private interface MethodBodyWriter {
        void write(MethodVisitor methodVisitor);
    }
}
