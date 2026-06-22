package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.asm.AsmFrontendException;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsmFrontendServiceTest {

    private static final String DEMO_OWNER = "sample/Demo";
    private static final String HELPERS_OWNER = "sample/Helpers";
    private static final String GPU_OWNER = Type.getInternalName(net.sixik.ga_utils.javatogpu.api.GPU.class);

    @Test
    void validatesAndLiftsStructuredAsmKernel() {
        MethodNode method = methodNode(DEMO_OWNER, "kernel", "([F[F)V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            org.objectweb.asm.Label elseLabel = new org.objectweb.asm.Label();
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, GPU_OWNER, "get_global_id", "(I)I", false);
            mv.visitVarInsn(Opcodes.ISTORE, 2);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitJumpInsn(Opcodes.IF_ICMPLE, elseLabel);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.FCONST_1);
            mv.visitInsn(Opcodes.FASTORE);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitLabel(elseLabel);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.FCONST_2);
            mv.visitInsn(Opcodes.FASTORE);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmFrontendService service = AsmFrontendService.createDefault();
        GpuIrMethod irMethod = service.validateAndLiftStructured(new AsmGpuMethod(
                DEMO_OWNER,
                parsedMethod("Demo", "sample.Demo", "kernel", "void", List.of(
                        globalArrayParameter("input", "float[]"),
                        globalArrayParameter("output", "float[]")
                )),
                method
        ));

        assertEquals("kernel", irMethod.name());
        assertEquals(2, irMethod.statements().size());
        assertInstanceOf(net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration.class, irMethod.statements().get(0));
        assertInstanceOf(GpuIrIf.class, irMethod.statements().get(1));
    }

    @Test
    void validatesLowersAndEmitsAsmKernelWithAsmHelper() {
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
                parsedMethod("Helpers", "sample.Helpers", "square", "float", List.of(
                        parameter("value", "float")
                )),
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

        AsmFrontendService service = AsmFrontendService.createDefault();
        String kernel = service.validateLowerAndEmitStructured(kernelMethod, List.of(helperMethod));

        assertTrue(kernel.contains("float jtg_fn_Helpers_square_float(float value);"));
        assertTrue(kernel.contains("return (arg0 * arg0);"));
        assertTrue(kernel.contains("arg1[tmp2] = jtg_fn_Helpers_square_float(arg0[tmp2]);"));
    }

    @Test
    void rejectsParsedAndAsmSignatureMismatch() {
        MethodNode method = methodNode(DEMO_OWNER, "kernel", "([F[F)V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmFrontendService service = AsmFrontendService.createDefault();

        AsmFrontendException exception = assertThrows(
                AsmFrontendException.class,
                () -> service.validateAndLiftStructured(new AsmGpuMethod(
                        DEMO_OWNER,
                        parsedMethod("Demo", "sample.Demo", "kernel", "void", List.of(
                                globalArrayParameter("input", "float[]")
                        )),
                        method
                ))
        );

        assertTrue(exception.getMessage().contains("parameter count"));
    }

    @Test
    void emitsGotoForOuterLoopBreakInsideSwitchCase() {
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
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitInsn(Opcodes.IASTORE);
            mv.visitJumpInsn(Opcodes.GOTO, loopEnd);
            mv.visitLabel(defaultCase);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.ICONST_2);
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

        AsmGpuMethod kernelMethod = new AsmGpuMethod(
                DEMO_OWNER,
                parsedMethod("Demo", "sample.Demo", "kernel", "void", List.of(
                        globalArrayParameter("input", "int[]"),
                        globalArrayParameter("output", "int[]")
                )),
                kernelMethodNode
        );

        AsmFrontendService service = AsmFrontendService.createDefault();
        String kernel = service.validateLowerAndEmitStructured(kernelMethod, List.of());

        assertTrue(kernel.contains("goto __jtg_loop_break_0;"));
        assertTrue(kernel.contains("__jtg_loop_break_0: ;"));
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
