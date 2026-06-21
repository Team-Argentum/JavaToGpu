package net.sixik.ga_utils.javatogpu.frontend.asm;

import net.sixik.ga_utils.javatogpu.api.FloatPtr;
import net.sixik.ga_utils.javatogpu.api.GPU;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsmSubsetValidatorTest {

    private static final String KERNEL_OWNER = "sample/Kernel";
    private static final String HELPER_OWNER = "sample/Helpers";
    private static final String STRUCT_OWNER = "sample/Point";
    private static final String GPU_OWNER = Type.getInternalName(GPU.class);
    private static final String FLOAT_PTR_OWNER = Type.getInternalName(FloatPtr.class);

    private final AsmSubsetValidator validator = new AsmSubsetValidator();

    @Test
    void acceptsKernelLikeMethodWithGpuCallsAndPrimitiveArrays() {
        MethodNode method = methodNode(KERNEL_OWNER, "kernel", "([F[F)V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, GPU_OWNER, "get_global_id", "(I)I", false);
            mv.visitVarInsn(Opcodes.ISTORE, 2);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.FALOAD);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, GPU_OWNER, "sin", "(F)F", false);
            mv.visitInsn(Opcodes.FASTORE);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        assertDoesNotThrow(() -> validator.validate(KERNEL_OWNER, method));
    }

    @Test
    void acceptsWhitelistedHelperOwners() {
        MethodNode method = methodNode(KERNEL_OWNER, "helperCaller", "(F)F", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitVarInsn(Opcodes.FLOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER_OWNER, "square", "(F)F", false);
            mv.visitInsn(Opcodes.FRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmValidationConfig config = AsmValidationConfig.defaultConfig().withHelperOwner(HELPER_OWNER);

        assertDoesNotThrow(() -> validator.validate(KERNEL_OWNER, method, config));
    }

    @Test
    void acceptsPointerConstructionAndFieldMutation() {
        MethodNode method = methodNode(KERNEL_OWNER, "pointerFlow", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitTypeInsn(Opcodes.NEW, FLOAT_PTR_OWNER);
            mv.visitInsn(Opcodes.DUP);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, FLOAT_PTR_OWNER, "<init>", "()V", false);
            mv.visitVarInsn(Opcodes.ASTORE, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitLdcInsn(2.0f);
            mv.visitFieldInsn(Opcodes.PUTFIELD, FLOAT_PTR_OWNER, "value", "F");
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, FLOAT_PTR_OWNER, "value", "F");
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        assertDoesNotThrow(() -> validator.validate(KERNEL_OWNER, method));
    }

    @Test
    void acceptsWhitelistedStructOwners() {
        MethodNode method = methodNode(KERNEL_OWNER, "readPoint", "(Lsample/Point;)F", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, STRUCT_OWNER, "x", "F");
            mv.visitInsn(Opcodes.FRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmValidationConfig config = AsmValidationConfig.defaultConfig().withStructOwner(STRUCT_OWNER);

        assertDoesNotThrow(() -> validator.validate(KERNEL_OWNER, method, config));
    }

    @Test
    void acceptsGpuStaticConstantsAndBarrierCalls() {
        MethodNode method = methodNode(KERNEL_OWNER, "barrierFlow", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitFieldInsn(Opcodes.GETSTATIC, GPU_OWNER, "CLK_LOCAL_MEM_FENCE", "I");
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, GPU_OWNER, "barrier", "(I)V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        assertDoesNotThrow(() -> validator.validate(KERNEL_OWNER, method));
    }

    @Test
    void acceptsCanonicalTableSwitch() {
        MethodNode method = methodNode(KERNEL_OWNER, "switcher", "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            Label caseZero = new Label();
            Label caseOne = new Label();
            Label defaultCase = new Label();
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitTableSwitchInsn(0, 1, defaultCase, caseZero, caseOne);
            mv.visitLabel(caseZero);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitLabel(caseOne);
            mv.visitInsn(Opcodes.ICONST_2);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitLabel(defaultCase);
            mv.visitInsn(Opcodes.ICONST_3);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        assertDoesNotThrow(() -> validator.validate(KERNEL_OWNER, method));
    }

    @Test
    void rejectsNonStaticMethods() {
        MethodNode method = methodNode(KERNEL_OWNER, "instanceKernel", "()V", Opcodes.ACC_PUBLIC, mv -> {
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmFrontendException exception = assertThrows(
                AsmFrontendException.class,
                () -> validator.validate(KERNEL_OWNER, method)
        );

        assertTrue(exception.getMessage().contains("only supports static methods"));
    }

    @Test
    void rejectsInvokeVirtualEvenForSupportedValueTypes() {
        MethodNode method = methodNode(KERNEL_OWNER, "badVirtualCall", "(L" + FLOAT_PTR_OWNER + ";)V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, FLOAT_PTR_OWNER, "hashCode", "()I", false);
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmFrontendException exception = assertThrows(
                AsmFrontendException.class,
                () -> validator.validate(KERNEL_OWNER, method)
        );

        assertTrue(exception.getMessage().contains("Unsupported method invocation kind"));
        assertTrue(exception.getMessage().contains("INVOKEVIRTUAL"));
    }

    @Test
    void rejectsUnknownStaticCallOwners() {
        MethodNode method = methodNode(KERNEL_OWNER, "unknownStaticCall", "(F)F", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitVarInsn(Opcodes.FLOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "sample/UnknownMath", "square", "(F)F", false);
            mv.visitInsn(Opcodes.FRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmFrontendException exception = assertThrows(
                AsmFrontendException.class,
                () -> validator.validate(KERNEL_OWNER, method)
        );

        assertTrue(exception.getMessage().contains("Unsupported static call owner"));
    }

    @Test
    void rejectsExceptionHandlers() {
        MethodNode method = methodNode(KERNEL_OWNER, "tryCatchKernel", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            Label start = new Label();
            Label end = new Label();
            Label handler = new Label();
            mv.visitTryCatchBlock(start, end, handler, "java/lang/RuntimeException");
            mv.visitCode();
            mv.visitLabel(start);
            mv.visitInsn(Opcodes.NOP);
            mv.visitLabel(end);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitLabel(handler);
            mv.visitInsn(Opcodes.ATHROW);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmFrontendException exception = assertThrows(
                AsmFrontendException.class,
                () -> validator.validate(KERNEL_OWNER, method)
        );

        assertTrue(exception.getMessage().contains("Exception handlers are not supported"));
    }

    @Test
    void rejectsForbiddenStackManipulationPatterns() {
        MethodNode method = methodNode(KERNEL_OWNER, "dupX1Kernel", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitInsn(Opcodes.ICONST_2);
            mv.visitInsn(Opcodes.DUP_X1);
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmFrontendException exception = assertThrows(
                AsmFrontendException.class,
                () -> validator.validate(KERNEL_OWNER, method)
        );

        assertTrue(exception.getMessage().contains("Unsupported bytecode opcode"));
        assertTrue(exception.getMessage().contains("DUP_X1"));
    }

    @Test
    void rejectsObjectArrayCreation() {
        MethodNode method = methodNode(KERNEL_OWNER, "objectArrayKernel", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
            mv.visitCode();
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        });

        AsmFrontendException exception = assertThrows(
                AsmFrontendException.class,
                () -> validator.validate(KERNEL_OWNER, method)
        );

        assertTrue(exception.getMessage().contains("Object arrays are not supported"));
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
        List<MethodNode> methods = classNode.methods;
        return methods.stream()
                .filter(method -> method.name.equals(methodName) && method.desc.equals(descriptor))
                .findFirst()
                .orElseThrow();
    }

    @FunctionalInterface
    private interface MethodBodyWriter {
        void write(MethodVisitor methodVisitor);
    }
}
