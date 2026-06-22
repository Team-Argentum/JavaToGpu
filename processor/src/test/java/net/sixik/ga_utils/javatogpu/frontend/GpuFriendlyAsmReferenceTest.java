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

import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuFriendlyAsmReferenceTest {

    private static final String DEMO_OWNER = "sample/ReferenceDemo";
    private static final String HELPERS_OWNER = "sample/ReferenceHelpers";
    private static final String GPU_OWNER = Type.getInternalName(net.sixik.ga_utils.javatogpu.api.GPU.class);

    @Test
    void compilesLiteralAndBinaryLoweringShape() {
        MethodNode kernelMethodNode = methodNode(DEMO_OWNER, "kernel", "([F[F)V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, GPU_OWNER, "get_global_id", "(I)I", false);
            mv.visitVarInsn(Opcodes.ISTORE, 2);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.FALOAD);
            mv.visitVarInsn(Opcodes.FSTORE, 3);
            mv.visitVarInsn(Opcodes.FLOAD, 3);
            mv.visitLdcInsn(2.0f);
            mv.visitInsn(Opcodes.FADD);
            mv.visitVarInsn(Opcodes.FSTORE, 4);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitVarInsn(Opcodes.FLOAD, 4);
            mv.visitInsn(Opcodes.FASTORE);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();
        String kernel = compiler.compileStructuredAsm(
                asmKernel("kernel", List.of(
                        globalArrayParameter("input", "float[]"),
                        globalArrayParameter("output", "float[]")
                ), kernelMethodNode),
                List.of()
        );

        assertTrue(kernel.contains("tmp4 = (tmp3 + 2.0f);"));
        assertTrue(kernel.contains("arg1[tmp2] = tmp4;"));
    }

    @Test
    void compilesIfElseLoweringShape() {
        MethodNode kernelMethodNode = methodNode(DEMO_OWNER, "kernel", "([I[I)V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label elseLabel = new org.objectweb.asm.Label();
            org.objectweb.asm.Label endLabel = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, GPU_OWNER, "get_global_id", "(I)I", false);
            mv.visitVarInsn(Opcodes.ISTORE, 2);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.IALOAD);
            mv.visitJumpInsn(Opcodes.IFNE, elseLabel);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitInsn(Opcodes.IASTORE);
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
            mv.visitLabel(elseLabel);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.ICONST_2);
            mv.visitInsn(Opcodes.IASTORE);
            mv.visitLabel(endLabel);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();
        String kernel = compiler.compileStructuredAsm(
                asmKernel("kernel", List.of(
                        globalArrayParameter("input", "int[]"),
                        globalArrayParameter("output", "int[]")
                ), kernelMethodNode),
                List.of()
        );

        assertTrue(kernel.contains("if ((!arg0[tmp2])) {"));
        assertTrue(kernel.contains("arg1[tmp2] = 1;"));
        assertTrue(kernel.contains("arg1[tmp2] = 2;"));
    }

    @Test
    void compilesWhileAndSwitchLoweringShape() {
        MethodNode kernelMethodNode = methodNode(DEMO_OWNER, "kernel", "([I[I)V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label loopCheck = new org.objectweb.asm.Label();
            org.objectweb.asm.Label loopEnd = new org.objectweb.asm.Label();
            org.objectweb.asm.Label caseZero = new org.objectweb.asm.Label();
            org.objectweb.asm.Label defaultCase = new org.objectweb.asm.Label();
            org.objectweb.asm.Label switchMerge = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 2);
            mv.visitLabel(loopCheck);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.ICONST_4);
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitTableSwitchInsn(0, 0, defaultCase, caseZero);
            mv.visitLabel(caseZero);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitIntInsn(Opcodes.BIPUSH, 7);
            mv.visitInsn(Opcodes.IASTORE);
            mv.visitJumpInsn(Opcodes.GOTO, loopEnd);
            mv.visitLabel(defaultCase);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.ICONST_3);
            mv.visitInsn(Opcodes.IASTORE);
            mv.visitJumpInsn(Opcodes.GOTO, switchMerge);
            mv.visitLabel(switchMerge);
            mv.visitIincInsn(2, 1);
            mv.visitJumpInsn(Opcodes.GOTO, loopCheck);
            mv.visitLabel(loopEnd);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();
        String kernel = compiler.compileStructuredAsm(
                asmKernel("kernel", List.of(
                        globalArrayParameter("input", "int[]"),
                        globalArrayParameter("output", "int[]")
                ), kernelMethodNode),
                List.of()
        );

        assertTrue(kernel.contains("while ((tmp2 < 4)) {"));
        assertTrue(kernel.contains("switch (tmp2) {"));
        assertTrue(kernel.contains("goto __jtg_loop_break_0;"));
    }

    @Test
    void compilesHelperCallLoweringShape() {
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
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.FALOAD);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPERS_OWNER, "square", "(F)F", false);
            mv.visitVarInsn(Opcodes.FSTORE, 3);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitVarInsn(Opcodes.FLOAD, 3);
            mv.visitInsn(Opcodes.FASTORE);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();
        String kernel = compiler.compileStructuredAsm(
                asmKernel("kernel", List.of(
                        globalArrayParameter("input", "float[]"),
                        globalArrayParameter("output", "float[]")
                ), kernelMethodNode),
                List.of(new AsmGpuMethod(
                        HELPERS_OWNER,
                        parsedMethod("ReferenceHelpers", "sample.ReferenceHelpers", "square", "float", List.of(
                                parameter("value", "float")
                        )),
                        helperMethodNode
                ))
        );

        assertTrue(kernel.contains("float jtg_fn_ReferenceHelpers_square_float(float value);"));
        assertTrue(kernel.contains("tmp3 = jtg_fn_ReferenceHelpers_square_float(arg0[tmp2]);"));
    }

    private AsmGpuMethod asmKernel(String name, List<ParsedGpuParameter> parameters, MethodNode methodNode) {
        return new AsmGpuMethod(
                DEMO_OWNER,
                parsedMethod("ReferenceDemo", "sample.ReferenceDemo", name, "void", parameters),
                methodNode
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
